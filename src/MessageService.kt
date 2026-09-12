package org.aprsdroid.app

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.MessagePacket
import java.util.Locale
import kotlin.math.min

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
    companion object {
        const val TAG = "APRSdroid.MsgService"
        const val NUM_OF_RETRIES = 7
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

        s.sendBroadcast(
            AprsService.privateIntent(s, AprsService.MESSAGE)
                .putExtra(AprsService.SOURCE, srccall)
                .putExtra(AprsService.DEST, msg.targetCallsign)
                .putExtra(AprsService.BODY, msg.messageBody)
        )
    }

    fun handleMessage(ts: Long, ap: APRSPacket, parsedMsg: MessagePacket) {
        val msg = AprsMessageParser.reparseIncoming(parsedMsg)
        val callssid = s.prefs.getCallSsid()
        if (AprsPacket.sameMessageCallsign(msg.targetCallsign, callssid)) {
            if (msg.isAck || msg.isRej) {
                val newType = if (msg.isAck) {
                    StorageDatabase.Companion.Message.TYPE_OUT_ACKED
                } else {
                    StorageDatabase.Companion.Message.TYPE_OUT_REJECTED
                }
                s.db.updateMessageAcked(ap.sourceCall, msg.messageNumber, newType)
                s.sendBroadcast(AprsService.privateIntent(s, AprsService.MESSAGE))
            } else {
                storeNotifyMessage(ts, ap.sourceCall, msg)
                if (msg.messageNumber.isNotEmpty()) {
                    val ack = s.newPacket(MessagePacket(ap.sourceCall, "ack", msg.messageNumber))
                    s.sendPacket(ack)
                }
            }
        } else if (msg.targetCallsign.split("-")[0].equals(s.prefs.getCallsign(), ignoreCase = true) && !msg.isAck && !msg.isRej) {
            if (AprsPacket.sameMessageCallsign(ap.sourceCall, callssid)) return
            Log.d(TAG, "incoming message for " + msg.targetCallsign)
            storeNotifyMessage(ts, ap.sourceCall, msg)
        }
    }

    fun getRetryDelayMS(retrycnt: Int): Long = 30000L * (1 shl min(retrycnt - 1, 6))

    fun scheduleNextSend(delay: Long) {
        Log.d(TAG, "scheduling TX in " + (delay + 999) / 1000 + "s")
        s.handler.postDelayed(pendingSender, (delay + 999) / 1000 * 1000)
    }

    fun stop() {
        s.handler.removeCallbacks(pendingSender)
    }

    fun sendPendingMessages() {
        s.handler.removeCallbacks(pendingSender)
        var nextRun = Long.MAX_VALUE

        val c = s.db.getPendingMessages(NUM_OF_RETRIES)
        c.moveToFirst()
        while (!c.isAfterLast) {
            val ts = c.getLong(StorageDatabase.Companion.Message.COLUMN_TS)
            val retrycnt = c.getInt(StorageDatabase.Companion.Message.COLUMN_RETRYCNT)
            val call = c.getString(StorageDatabase.Companion.Message.COLUMN_CALL)
            val msgid = c.getString(StorageDatabase.Companion.Message.COLUMN_MSGID)
            val text = c.getString(StorageDatabase.Companion.Message.COLUMN_TEXT)
            val tSend = ts + getRetryDelayMS(retrycnt) - System.currentTimeMillis()

            Log.d(TAG, String.format(Locale.US, "pending message: %d/%d (%ds) ->%s '%s'", retrycnt, NUM_OF_RETRIES, tSend / 1000, call, text))
            when (pendingMessageAction(retrycnt, NUM_OF_RETRIES, tSend)) {
                PendingMessageAction.ABORT -> {
                    s.db.updateMessageType(c.getLong(0), StorageDatabase.Companion.Message.TYPE_OUT_ABORTED)
                    s.sendBroadcast(AprsService.privateIntent(s, AprsService.MESSAGE))
                }
                PendingMessageAction.SEND -> {
                    val msg = s.newPacket(MessagePacket(call, text, msgid))
                    s.sendPacket(msg)
                    val cv = ContentValues().apply {
                        put(StorageDatabase.Companion.Message.RETRYCNT, retrycnt + 1)
                        put(StorageDatabase.Companion.Message.TS, System.currentTimeMillis())
                    }
                    s.db.updateMessage(c.getLong(0), cv)
                    s.sendBroadcast(AprsService.privateIntent(s, AprsService.MESSAGE))
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
            scheduleNextSend(nextRun)
        }
    }
}
