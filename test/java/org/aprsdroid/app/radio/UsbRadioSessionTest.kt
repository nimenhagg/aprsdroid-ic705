package org.aprsdroid.app.radio

import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import net.ab0oo.aprs.parser.Parser
import org.aprsdroid.app.audio.DrainablePcmSink
import org.aprsdroid.app.audio.PcmEncoding
import org.aprsdroid.app.audio.PcmFormat
import org.aprsdroid.app.audio.PcmSink
import org.aprsdroid.app.audio.PcmSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sivantoledo.ax25.Packet

class UsbRadioSessionTest {

    @Test
    fun startTransitionToRunningAndSuccessfulTransmit() {
        val catStream = FakeByteStream()
        val audioSource = FakePcmSource()
        val audioSink = FakeDrainablePcmSink()
        val decoder = FakePcmDecoder()
        val fakeRadio = FakeRadio()

        val states = mutableListOf<UsbRadioSession.State>()

        val session = UsbRadioSession(
            profile = RadioProfile.IC705_USB,
            catStream = catStream,
            audioSource = audioSource,
            audioSink = audioSink,
            decoder = decoder,
            radioControlFactory = { transport ->
                assertTrue(transport.endpoint?.startsWith("127.0.0.1:") == true)
                fakeRadio
            },
            onStateChanged = { state, _ -> states.add(state) },
        )

        assertEquals(UsbRadioSession.State.IDLE, session.state)

        session.start()

        assertEquals(UsbRadioSession.State.RUNNING, session.state)
        assertTrue(session.isRunning)
        assertTrue(fakeRadio.isOpen)

        val rawAx25 = Packet(
            "APDR16",
            "N0CALL",
            arrayOf("WIDE1-1"),
            Packet.AX25_CONTROL_APRS,
            Packet.AX25_PROTOCOL_NO_LAYER_3,
            "=3955.00N/11623.00E#Test Beacon".toByteArray(Charsets.ISO_8859_1),
        )
        val aprsPacket = Parser.parseAX25(rawAx25.bytesWithoutCRC())

        val result = session.transmit(aprsPacket)

        assertEquals(RadioPttSequence.Result.Completed, result)
        assertEquals(listOf(true, false), fakeRadio.commands)
        assertFalse(fakeRadio.pttState)
        assertTrue(audioSink.writtenSamples.size > 500)
        assertTrue(audioSink.drained)

        session.close()

        assertEquals(UsbRadioSession.State.STOPPED, session.state)
        assertTrue(session.isClosed)
        assertTrue(fakeRadio.closed)
        assertTrue(catStream.closed)
        assertTrue(audioSource.closed)
        assertTrue(audioSink.closed)
        assertTrue(decoder.closed)
    }

    @Test
    fun usbDetachedTriggersSafeTeardown() {
        val catStream = FakeByteStream()
        val audioSource = FakePcmSource()
        val audioSink = FakeDrainablePcmSink()
        val decoder = FakePcmDecoder()
        val fakeRadio = FakeRadio()

        val session = UsbRadioSession(
            profile = RadioProfile.IC705_USB,
            catStream = catStream,
            audioSource = audioSource,
            audioSink = audioSink,
            decoder = decoder,
            radioControlFactory = { fakeRadio },
        )

        session.start()
        assertTrue(session.isRunning)

        session.onUsbDetached()

        assertEquals(UsbRadioSession.State.STOPPED, session.state)
        assertTrue(session.isClosed)
        assertFalse(fakeRadio.pttState)
        assertTrue(fakeRadio.closed)
        assertTrue(catStream.closed)
    }

    @Test
    fun rxAudioSamplesAreRoutedToPacketConsumer() {
        val catStream = FakeByteStream()
        val testData = ShortArray(128) { it.toShort() }
        val audioSource = FakePcmSource(testData)
        val audioSink = FakeDrainablePcmSink()
        val decoder = FakePcmDecoder()
        val fakeRadio = FakeRadio()

        val session = UsbRadioSession(
            profile = RadioProfile.IC705_USB,
            catStream = catStream,
            audioSource = audioSource,
            audioSink = audioSink,
            decoder = decoder,
            radioControlFactory = { fakeRadio },
        )

        session.start()

        val deadline = System.currentTimeMillis() + 1000
        while (decoder.receivedSamples.size < 128 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }

        assertTrue(decoder.receivedSamples.size >= 128)
        assertEquals(testData[0], decoder.receivedSamples[0])

        session.close()
    }

    private class FakeByteStream : RadioByteStream {
        private val outStream = PipedOutputStream()
        override val inputStream: InputStream = PipedInputStream(outStream)
        override val outputStream: OutputStream = outStream
        var closed = false

        override fun close() {
            closed = true
            runCatching { inputStream.close() }
            runCatching { outputStream.close() }
        }
    }

    private class FakeRadio(
        private val readback: Boolean = true,
    ) : RadioControl {
        override val radio = RadioDescriptor(3085, "Icom", "IC-705")
        override val capabilities = RadioCapabilities(canGetPtt = true, canSetPtt = true)
        override var isOpen: Boolean = false
        var pttState: Boolean = false
        var closed: Boolean = false
        val commands = mutableListOf<Boolean>()

        override fun open() {
            isOpen = true
        }

        override fun closePort() {
            isOpen = false
        }

        override fun isPttOn(): Boolean = pttState && readback

        override fun setPtt(on: Boolean) {
            commands += on
            pttState = on
        }

        override fun close() {
            closed = true
            isOpen = false
        }
    }

    private class FakeDrainablePcmSink : DrainablePcmSink {
        override val format: PcmFormat = PcmFormat(16000, 1, PcmEncoding.PCM_16_LE)
        val writtenSamples = mutableListOf<Short>()
        var drained = false
        var closed = false

        override fun write(buffer: ShortArray, offset: Int, length: Int) {
            for (i in offset until offset + length) {
                writtenSamples.add(buffer[i])
            }
        }

        override fun drain(timeoutMs: Long) {
            drained = true
        }

        override fun close() {
            closed = true
        }
    }

    private class FakePcmSource(
        private val data: ShortArray = ShortArray(0),
    ) : PcmSource {
        override val format: PcmFormat = PcmFormat(16000, 1, PcmEncoding.PCM_16_LE)
        var pos = 0
        var closed = false

        override fun read(buffer: ShortArray, offset: Int, length: Int): Int {
            if (closed) return -1
            if (pos >= data.size) {
                Thread.sleep(10)
                return if (closed) -1 else 0
            }
            val toCopy = minOf(length, data.size - pos)
            System.arraycopy(data, pos, buffer, offset, toCopy)
            pos += toCopy
            return toCopy
        }

        override fun close() {
            closed = true
        }
    }

    private class FakePcmDecoder : PcmSink {
        override val format: PcmFormat = PcmFormat(16000, 1, PcmEncoding.PCM_16_LE)
        val receivedSamples = mutableListOf<Short>()
        var closed = false

        @Synchronized
        override fun write(buffer: ShortArray, offset: Int, length: Int) {
            for (i in offset until offset + length) {
                receivedSamples.add(buffer[i])
            }
        }

        override fun close() {
            closed = true
        }
    }
}
