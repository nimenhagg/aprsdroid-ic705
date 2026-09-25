package org.aprsdroid.app.radio

import java.io.InputStream
import java.io.OutputStream
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

class RadioAudioBackendTest {

    @Test
    fun transmitAprsPacketExecutesFullPttAudioDrainSequence() {
        val radio = FakeRadio()
        val source = FakePcmSource()
        val sink = FakeDrainablePcmSink()
        val decoder = FakePcmDecoder()

        val backend = RadioAudioBackend(
            radioControl = radio,
            audioSource = source,
            audioSink = sink,
            decoder = decoder,
            txSampleRateHz = 16000,
        )

        val rawAx25 = Packet(
            "APDR16",
            "N0CALL",
            arrayOf("WIDE1-1"),
            Packet.AX25_CONTROL_APRS,
            Packet.AX25_PROTOCOL_NO_LAYER_3,
            "=3955.00N/11623.00E#Test Beacon".toByteArray(Charsets.ISO_8859_1),
        )
        val aprsPacket = Parser.parseAX25(rawAx25.bytesWithoutCRC())

        val result = backend.transmit(aprsPacket)

        assertEquals(RadioPttSequence.Result.Completed, result)
        assertEquals(listOf(true, false), radio.commands)
        assertFalse(radio.pttState)
        assertTrue(sink.writtenSamples.size > 500)
        assertTrue(sink.drained)

        backend.close()
    }

    @Test
    fun transmitFailsSafelyWhenDrainThrows() {
        val radio = FakeRadio()
        val source = FakePcmSource()
        val sink = FakeDrainablePcmSink(throwOnDrain = true)
        val decoder = FakePcmDecoder()

        val backend = RadioAudioBackend(
            radioControl = radio,
            audioSource = source,
            audioSink = sink,
            decoder = decoder,
        )

        val packet = Packet(
            "APDR16",
            "N0CALL",
            emptyArray(),
            Packet.AX25_CONTROL_APRS,
            Packet.AX25_PROTOCOL_NO_LAYER_3,
            "test".toByteArray(),
        )

        val result = backend.transmit(packet)

        assertEquals(RadioPttSequence.Result.AudioFailed, result)
        assertEquals(listOf(true, false), radio.commands)
        assertFalse(radio.pttState)

        backend.close()
    }

    @Test
    fun transmitFailsSafelyWhenWriteThrows() {
        val radio = FakeRadio()
        val source = FakePcmSource()
        val sink = FakeDrainablePcmSink(throwOnWrite = true)
        val decoder = FakePcmDecoder()

        val backend = RadioAudioBackend(
            radioControl = radio,
            audioSource = source,
            audioSink = sink,
            decoder = decoder,
        )

        val packet = Packet(
            "APDR16",
            "N0CALL",
            emptyArray(),
            Packet.AX25_CONTROL_APRS,
            Packet.AX25_PROTOCOL_NO_LAYER_3,
            "test".toByteArray(),
        )

        val result = backend.transmit(packet)

        assertEquals(RadioPttSequence.Result.AudioFailed, result)
        assertEquals(listOf(true, false), radio.commands)
        assertFalse(radio.pttState)

        backend.close()
    }

    @Test
    fun transmitAbortsWhenPttOnNotConfirmed() {
        val radio = FakeRadio(readback = false)
        val source = FakePcmSource()
        val sink = FakeDrainablePcmSink()
        val decoder = FakePcmDecoder()

        val backend = RadioAudioBackend(
            radioControl = radio,
            audioSource = source,
            audioSink = sink,
            decoder = decoder,
        )

        val packet = Packet(
            "APDR16",
            "N0CALL",
            emptyArray(),
            Packet.AX25_CONTROL_APRS,
            Packet.AX25_PROTOCOL_NO_LAYER_3,
            "test".toByteArray(),
        )

        val result = backend.transmit(packet)

        assertEquals(RadioPttSequence.Result.PttOnNotConfirmed, result)
        assertTrue(sink.writtenSamples.isEmpty())
        assertEquals(listOf(true, false), radio.commands)
        assertFalse(radio.pttState)

        backend.close()
    }

    @Test
    fun rxLoopReadsSourceAndFeedsDecoder() {
        val radio = FakeRadio()
        val testData = ShortArray(256) { (it * 10).toShort() }
        val source = FakePcmSource(testData)
        val sink = FakeDrainablePcmSink()
        val decoder = FakePcmDecoder()

        val backend = RadioAudioBackend(
            radioControl = radio,
            audioSource = source,
            audioSink = sink,
            decoder = decoder,
        )

        backend.start()

        val deadline = System.currentTimeMillis() + 1000
        while (decoder.receivedSamples.size < 256 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }

        assertTrue(decoder.receivedSamples.size >= 256)
        assertEquals(testData[0], decoder.receivedSamples[0])
        assertEquals(testData[255], decoder.receivedSamples[255])

        backend.close()
    }

    @Test
    fun closeReleasesAllUnderlyingResources() {
        val radio = FakeRadio()
        val source = FakePcmSource()
        val sink = FakeDrainablePcmSink()
        val decoder = FakePcmDecoder()

        val backend = RadioAudioBackend(
            radioControl = radio,
            audioSource = source,
            audioSink = sink,
            decoder = decoder,
        )

        backend.start()
        assertFalse(backend.isClosed)

        backend.close()

        assertTrue(backend.isClosed)
        assertTrue(radio.closed)
        assertTrue(source.closed)
        assertTrue(sink.closed)
        assertTrue(decoder.closed)
    }

    @Test
    fun radioAudioDeviceProperties() {
        val usbDevice = RadioAudioDevice(
            id = 42,
            productName = "USB Audio Codec",
            type = 11, // TYPE_USB_DEVICE
            isSource = true,
            isSink = true,
        )
        assertTrue(usbDevice.isUsb)
        assertEquals(42, usbDevice.id)
        assertEquals("USB Audio Codec", usbDevice.productName)

        val speakerDevice = RadioAudioDevice(
            id = 1,
            productName = "Built-in Speaker",
            type = 2, // TYPE_BUILTIN_SPEAKER
            isSource = false,
            isSink = true,
        )
        assertFalse(speakerDevice.isUsb)
    }

    private class FakeRadio(
        private val readback: Boolean = true,
    ) : RadioControl {
        override val radio = RadioDescriptor(1, "Test", "Fake")
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

    private class FakeDrainablePcmSink(
        private val throwOnWrite: Boolean = false,
        private val throwOnDrain: Boolean = false,
    ) : DrainablePcmSink {
        override val format: PcmFormat = PcmFormat(16000, 1, PcmEncoding.PCM_16_LE)
        val writtenSamples = mutableListOf<Short>()
        var drained = false
        var closed = false

        override fun write(buffer: ShortArray, offset: Int, length: Int) {
            if (throwOnWrite) throw IllegalStateException("Synthetic write failure")
            for (i in offset until offset + length) {
                writtenSamples.add(buffer[i])
            }
        }

        override fun drain(timeoutMs: Long) {
            if (throwOnDrain) throw IllegalStateException("Synthetic drain failure")
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
