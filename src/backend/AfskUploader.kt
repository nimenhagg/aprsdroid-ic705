package org.aprsdroid.app

import android.content.Intent
import android.media.AudioTrack
import android.util.Log
import com.nogy.afu.soundmodem.APRSFrame
import com.nogy.afu.soundmodem.Afsk
import com.nogy.afu.soundmodem.Message
import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.Digipeater

class AfskUploader(
    val service: AprsService,
    prefs: PrefsWrapper,
) : AprsBackend(prefs) {

    companion object {
        const val TAG = "APRSdroid.Afsk"
    }

    var frameLength: Int = prefs.getStringInt("afsk.prefix", 200) * 1200 / 8 / 1000
    var digis: String = prefs.getString("digi_path", "WIDE1-1")
    val useBt: Boolean = prefs.getAfskBluetooth()
    val samplerate: Int = if (useBt) 16000 else 22050
    val outType: Int = prefs.getAfskOutput()
    val inType: Int = if (useBt) 1 else 1
    val output: Afsk = Afsk(outType, samplerate)
    val aw: AfskInWrapper = AfskInWrapper(this, inType, samplerate / 2)
    private val ax25PacketConsumer = Ax25PacketConsumer(
        Ax25SubmitSink { text -> service.postSubmit(text) },
        TAG,
    )
    private val bluetoothAudioRouter: AfskBluetoothAudioRouter by lazy {
        AfskBluetoothAudioRouter(service) {
            log(service.getString(R.string.afsk_info_sco_est))
            aw.start()
            service.postPosterStarted()
        }
    }

    init {
        @Suppress("DEPRECATION")
        output.setVolume(AudioTrack.getMaxVolume())
    }

    fun isCallsignAX25Valid(): Boolean {
        return if (prefs.getCallsign().length > 6) {
            service.postAbort(service.getString(R.string.e_toolong_callsign))
            false
        } else {
            true
        }
    }

    override fun start(): Boolean {
        if (!isCallsignAX25Valid()) return false
        return if (useBt) {
            log(service.getString(R.string.afsk_info_sco_req))
            bluetoothAudioRouter.start()
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
            bluetoothAudioRouter.stop()
        }
    }

    /** Receives a raw FCS-stripped AX.25 frame from Graywolf. */
    fun handlePacket(data: ByteArray) {
        ax25PacketConsumer.accept(data)
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
