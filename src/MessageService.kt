package org.aprsdroid.app

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.MessagePacket
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import org.aprsdroid.app.service.AprsMessageEvent
import org.aprsdroid.app.service.ServiceEventBus

internal enum class PendingMessageAction {
    SEND,
    WAIT,
    ABORT,
}

internal fun pendingMessageAction(retryCount: Int, maxRetries: Int, delayMillis: Long): PendingMessageAction =
    when {
        delayMillis > 0 -> PendingMessageAction.WAIT
        retryCount >= maxRetries -> PendingMessageAction.ABORT
        else -> PendingMessageAction.SEND
    }

class MessageService(val s: AprsService) {

    private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aprs-msg-io").apply { isDaemon = true }
    }

    private val replyAckState = ReplyAckState()

    companion object {
        const val TAG = "APRSdroid.MsgService"
        const val NUM_OF_RETRIES = 7

        fun calculateRetryDelayMs(retrycnt: Int): Long = 30000L * (1 shl max(min(retrycnt - 1, 6), 0))
    }

    val pendingSender = Runnable { sendPendingMessages() }

    fun createMessageNotifier() = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            sendPendingMessages()
        }
    }

    fun storeNotifyMessage(ts: Long, srccall: String, msg: MessagePacket) {
        val isNew = s.db.addMessage(ts, srccall, msg)
        if (isNew) {
            ServiceNotifier.instance.notifyMessage(s, srccall, msg.messageBody)
        }

        ServiceEventBus.postMessageEvent(
            AprsMessageEvent(
                destination = msg.targetCallsign,
                sender = srccall,
                body = msg.messageBody,
                isOutgoing = false,
                timestamp = ts,
            )
        )

        s.sendBroadcast(
            AprsService.privateIntent(s, AprsService.MESSAGE)
                .putExtra(AprsService.SOURCE, srccall)
                .putExtra(AprsService.DEST, msg.targetCallsign)
                .putExtra(AprsService.BODY, msg.messageBody)
        )
    }

    fun handleMessage(ts: Long, ap: APRSPacket, parsedMsg: MessagePacket) {
        val parsed = AprsMessageParser.reparseIncoming(parsedMsg)
        val msg = parsed.packet
        val callssid = s.prefs.getCallSsid()
        if (AprsPacket.sameMessageCallsign(msg.targetCallsign, callssid)) {
            if (msg.isAck || msg.isRej) {
                val newType = if (msg.isAck) {
                    StorageDatabase.Companion.Message.TYPE_OUT_ACKED
                } else {
                    StorageDatabase.Companion.Message.TYPE_OUT_REJECTED
                }
                s.db.updateMessageAcked(ap.sourceCall, msg.messageNumber, newType)
                ServiceEventBus.postMessageEvent(
                    AprsMessageEvent(
                        destination = ap.sourceCall,
                        sender = callssid,
                        body = if (msg.isAck) "ack" else "rej",
                        isOutgoing = false,
                        timestamp = ts,
                    )
                )
                s.sendBroadcast(AprsService.privateIntent(s, AprsService.MESSAGE))
            } else {
                parsed.replyAck?.let { replyAck ->
                    if (replyAck.isNotEmpty()) {
                        s.db.updateMessageAcked(
                            ap.sourceCall,
                            replyAck,
                            StorageDatabase.Companion.Message.TYPE_OUT_ACKED,
                        )
                        ServiceEventBus.postMessageEvent(
                            AprsMessageEvent(
                                destination = ap.sourceCall,
                                sender = callssid,
                                body = "ack",
                                isOutgoing = false,
                                timestamp = ts,
                            )
                        )
                        s.sendBroadcast(AprsService.privateIntent(s, AprsService.MESSAGE))
                    }
                }

                if (parsed.replyAckCapable && msg.messageNumber.isNotEmpty()) {
                    replyAckState.rememberIncoming(ap.sourceCall, msg.messageNumber)
                }

                storeNotifyMessage(ts, ap.sourceCall, msg)
                if (msg.messageNumber.isNotEmpty()) {
                    val ack = s.newPacket(
                        MessagePacket(ap.sourceCall, "ack", parsed.wireMessageNumber),
                    )
                    s.sendPacket(ack)
                }
                checkAndReplyAprsQuery(ap.sourceCall, msg.messageBody)
            }
        } else if (msg.targetCallsign.split("-")[0].equals(s.prefs.getCallsign(), ignoreCase = true) && !msg.isAck && !msg.isRej) {
            if (AprsPacket.sameMessageCallsign(ap.sourceCall, callssid)) return
            Log.d(TAG, "incoming message for " + msg.targetCallsign)
            storeNotifyMessage(ts, ap.sourceCall, msg)
        }
    }

    fun getRetryDelayMS(retrycnt: Int): Long = calculateRetryDelayMs(retrycnt)

    fun scheduleNextSend(delay: Long) {
        Log.d(TAG, "scheduling TX in " + (delay + 999) / 1000 + "s")
        s.handler.postDelayed(pendingSender, (delay + 999) / 1000 * 1000)
    }

    fun stop() {
        s.handler.removeCallbacks(pendingSender)
        ioExecutor.shutdown()
    }

    fun sendPendingMessages() {
        s.handler.removeCallbacks(pendingSender)
        ioExecutor.execute {
            var nextRun = Long.MAX_VALUE

            val c = s.db.getPendingMessages(NUM_OF_RETRIES)
            c.moveToFirst()
            while (!c.isAfterLast) {
                val id = c.getLong(0)
                val ts = c.getLong(StorageDatabase.Companion.Message.COLUMN_TS)
                val retrycnt = c.getInt(StorageDatabase.Companion.Message.COLUMN_RETRYCNT)
                val call = c.getString(StorageDatabase.Companion.Message.COLUMN_CALL)
                val msgid = c.getString(StorageDatabase.Companion.Message.COLUMN_MSGID)
                val text = c.getString(StorageDatabase.Companion.Message.COLUMN_TEXT)
                val tSend = ts + getRetryDelayMS(retrycnt) - System.currentTimeMillis()

                Log.d(TAG, String.format(Locale.US, "pending message: %d/%d (%ds) ->%s '%s'", retrycnt, NUM_OF_RETRIES, tSend / 1000, call, text))
                when (pendingMessageAction(retrycnt, NUM_OF_RETRIES, tSend)) {
                    PendingMessageAction.ABORT -> {
                        s.db.updateMessageType(id, StorageDatabase.Companion.Message.TYPE_OUT_ABORTED)
                        val callSsid = s.prefs.getCallSsid()
                        s.handler.post {
                            ServiceEventBus.postMessageEvent(
                                AprsMessageEvent(
                                    destination = call,
                                    sender = callSsid,
                                    body = text,
                                    isOutgoing = true,
                                    timestamp = System.currentTimeMillis(),
                                )
                            )
                            s.sendBroadcast(AprsService.privateIntent(s, AprsService.MESSAGE))
                        }
                    }
                    PendingMessageAction.SEND -> {
                        val wireMsgId = replyAckState.decorateOutgoing(call, msgid)
                        val msg = s.newPacket(MessagePacket(call, text, wireMsgId))
                        s.sendPacket(msg)
                        val cv = ContentValues().apply {
                            put(StorageDatabase.Companion.Message.RETRYCNT, retrycnt + 1)
                            put(StorageDatabase.Companion.Message.TS, System.currentTimeMillis())
                        }
                        s.db.updateMessage(id, cv)
                        val callSsid = s.prefs.getCallSsid()
                        s.handler.post {
                            ServiceEventBus.postMessageEvent(
                                AprsMessageEvent(
                                    destination = call,
                                    sender = callSsid,
                                    body = text,
                                    isOutgoing = true,
                                    timestamp = System.currentTimeMillis(),
                                )
                            )
                            s.sendBroadcast(AprsService.privateIntent(s, AprsService.MESSAGE))
                        }
                        nextRun = min(nextRun, getRetryDelayMS(retrycnt + 1))
                    }
                    PendingMessageAction.WAIT -> {
                        // Includes the final post-send timeout. Queue rescans must not
                        // strand retrycnt == NUM_OF_RETRIES without a wake-up.
                        nextRun = min(nextRun, tSend)
                    }
                }
                c.moveToNext()
            }
            c.close()

            if (nextRun != Long.MAX_VALUE) {
                val delay = nextRun
                s.handler.post {
                    scheduleNextSend(delay)
                }
            }
        }
    }

    private fun checkAndReplyAprsQuery(targetCall: String, body: String) {
        val query = body.trim().uppercase(Locale.US)
        val replyText = when {
            query == "?VER?" || query == "?VER" -> s.prefs.getVersion()
            query == "?PING?" || query == "?PING" -> "PONG"
            query == "?APRST" || query == "?APRST?" -> "APRSdroid Mod OK"
            else -> null
        }
        if (replyText != null) {
            val replyMsg = s.newPacket(
                MessagePacket(targetCall, replyText, "")
            )
            s.sendPacket(replyMsg)
        }
    }
}
