package org.aprsdroid.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import androidx.core.content.ContextCompat
import com.jazzido.PacketDroid.PacketCallback
import com.nogy.afu.soundmodem.APRSFrame
import com.nogy.afu.soundmodem.Afsk
import com.nogy.afu.soundmodem.Message
import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.Digipeater
import sivantoledo.ax25.PacketHandler

class AfskUploader(
    val service: AprsService,
    prefs: PrefsWrapper
) : AprsBackend(prefs), PacketHandler, PacketCallback {

    companion object {
        const val TAG = "APRSdroid.Afsk"
    }

    var frameLength: Int = prefs.getStringInt("afsk.prefix", 200) * 1200 / 8 / 1000
    var digis: String = prefs.getString("digi_path", "WIDE1-1")
    val useHq: Boolean = prefs.getAfskHQ()
    val useBt: Boolean = prefs.getAfskBluetooth()
    val samplerate: Int = if (useBt) 16000 else 22050
    val outType: Int = prefs.getAfskOutput()
    val inType: Int = if (useBt) 1 else 1
    val output: Afsk = Afsk(outType, samplerate)
    val aw: AfskInWrapper = AfskInWrapper(useHq, this, inType, samplerate / 2)
    private val ax25PacketConsumer = Ax25PacketConsumer(
        Ax25SubmitSink { text -> service.postSubmit(text) }, TAG
    )

    init {
        @Suppress("DEPRECATION")
        output.setVolume(AudioTrack.getMaxVolume())
    }

    private val btScoReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            val state = i.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
            Log.d(TAG, "AudioManager SCO event: $state")
            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                log(service.getString(R.string.afsk_info_sco_est))
                aw.start()
                try { service.unregisterReceiver(this) } catch (_: Exception) {}
                service.postPosterStarted()
            }
        }
    }

    fun isCallsignAX25Valid(): Boolean {
        return if (prefs.getCallsign().length > 6) {
            service.postAbort(service.getString(R.string.e_toolong_callsign))
            false
        } else {
            true
        }
    }

    @SuppressLint("WrongConstant")
    override fun start(): Boolean {
        if (!isCallsignAX25Valid()) return false
        return if (useBt) {
            log(service.getString(R.string.afsk_info_sco_req))
            val am = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            am.startBluetoothSco()
            ContextCompat.registerReceiver(
                service, btScoReceiver,
                IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
            false
        } else {
            aw.start()
            true
        }
    }

    fun sendMessage(msg: Message): Boolean {
        return output.sendMessage(msg)
    }

    override fun update(packet: APRSPacket): String {
        packet.setDigipeaters(Digipeater.parseList(digis, true))
        val from = packet.sourceCall
        val to = packet.destinationCall
        val data = packet.aprsInformation.toString()
        val msg = APRSFrame(from, to, digis, data, frameLength).message
        Log.d(TAG, "update(): From: $from To: $to Via: $digis telling $data")
        return if (sendMessage(msg)) "AFSK OK" else "AFSK busy"
    }

    override fun stop() {
        aw.close()
        if (useBt) {
            val am = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            am.stopBluetoothSco()
            try { service.unregisterReceiver(btScoReceiver) } catch (_: RuntimeException) {}
        }
    }

    override fun handlePacket(data: ByteArray) {
        ax25PacketConsumer.accept(data)
    }

    override fun received(data: ByteArray) {
        handlePacket(data)
    }

    override fun peak(peakValue: Short) {
        notifyMicLevel(peakValue / 330)
    }

    fun notifyMicLevel(level: Int) {
        val i = Intent(AprsService.MICLEVEL)
        i.setPackage(service.packageName)
        i.putExtra("level", level)
        service.sendBroadcast(i)
    }

    fun log(s: String) {
        Log.i(TAG, s)
        service.postAddPost(StorageDatabase.Companion.Post.TYPE_INFO, R.string.post_info, s)
    }

    fun postAbort(s: String) {
        service.postAbort(s)
    }
}
