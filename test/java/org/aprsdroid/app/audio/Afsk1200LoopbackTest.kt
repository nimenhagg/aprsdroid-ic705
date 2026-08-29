package org.aprsdroid.app.audio

import java.util.concurrent.atomic.AtomicReference
import net.ab0oo.aprs.parser.Parser
import org.aprsdroid.app.ic705.protocol.Ic705AudioPacketCodec
import org.aprsdroid.app.ic705.session.Ic705TxAudioPacketizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import sivantoledo.ax25.Afsk1200Demodulator
import sivantoledo.ax25.Packet
import sivantoledo.ax25.PacketHandler

/**
 * Host-JVM sanity tests for the Java AFSK modulator / legacy microphone modem.
 *
 * These tests intentionally do not exercise the IC-705 production decoder:
 * Graywolf is mandatory there and is covered by test_graywolf_loopback.sh.
 */
class Afsk1200LoopbackTest {

    @Test
    fun aprsPacketToAx25ModulationLegacyDemodulationRoundTrip() {
        val testSource = "BD3QID-5"
        val testDest = "APDR16"
        val testDigis = arrayOf("WIDE1-1", "WIDE2-1")
        val testPayload = "=3955.00N/11623.00E#Test APRS Position via IC-705 Wi-Fi"

        val initialAx25 = Packet(
            testDest,
            testSource,
            testDigis,
            Packet.AX25_CONTROL_APRS,
            Packet.AX25_PROTOCOL_NO_LAYER_3,
            testPayload.toByteArray(Charsets.ISO_8859_1),
        )

        val aprsPacket = Parser.parseAX25(initialAx25.bytesWithoutCRC())
        assertEquals(testSource, aprsPacket.sourceCall)
        assertEquals(testDest, aprsPacket.destinationCall)
        assertEquals(2, aprsPacket.digipeaters.size)

        val ax25Packet = Ax25PacketEncoder.encode(aprsPacket)
        assertEquals(testSource, ax25Packet.source)
        assertEquals(testDest, ax25Packet.destination)
        assertEquals(2, ax25Packet.path.size)
        assertEquals("WIDE1-1", ax25Packet.path[0])
        assertEquals("WIDE2-1", ax25Packet.path[1])

        val generator = Afsk1200PcmGenerator(sampleRateHz = 12_000, txDelayMs = 250, txTail = 2)
        val pcmSamples = generator.generateSamples(ax25Packet)
        assert(pcmSamples.isNotEmpty())

        val packetizer = Ic705TxAudioPacketizer(
            senderId = 0x12345678,
            receiverId = 0x76543210,
            initialOuterSequence = 10,
            initialAudioSequence = 100,
        )
        val datagrams = packetizer.packetize(pcmSamples, primeSilenceMs = 40, trailingSilenceMs = 40)
        assert(datagrams.isNotEmpty())

        val decodedFrame = AtomicReference<ByteArray>()
        val demodulator = legacyDecoder { packet -> decodedFrame.set(packet) }

        for (datagram in datagrams) {
            val audioPacket = Ic705AudioPacketCodec.decode(datagram)
            val floats = FloatArray(audioPacket.pcmPayload.size / 2)
            val count = Pcm16LittleEndian.decodeToFloats(
                bytes = audioPacket.pcmPayload,
                offset = 0,
                length = audioPacket.pcmPayload.size,
                destination = floats,
            )
            demodulator.addSamples(floats, count)
        }

        val receivedRaw = decodedFrame.get()
        assertNotNull("Legacy sanity decoder must have produced a raw AX.25 frame", receivedRaw)

        val parsedReceived = Parser.parseAX25(receivedRaw)
        assertEquals(testSource, parsedReceived.sourceCall)
        assertEquals(testDest, parsedReceived.destinationCall)
        assertEquals(testPayload, parsedReceived.aprsInformation.toString())
    }

    @Test
    fun rawAx25BytesDirectModulationLegacyRoundTrip() {
        val testPacket = Packet(
            "APRS",
            "N0CALL",
            arrayOf("WIDE1-1"),
            Packet.AX25_CONTROL_APRS,
            Packet.AX25_PROTOCOL_NO_LAYER_3,
            ">Direct Packet Test".toByteArray(Charsets.ISO_8859_1),
        )

        val generator = Afsk1200PcmGenerator(sampleRateHz = 12_000, txDelayMs = 200)
        val pcmSamples = generator.generateSamples(testPacket)

        val received = AtomicReference<ByteArray>()
        val demodulator = legacyDecoder { packet -> received.set(packet) }
        val floats = FloatArray(pcmSamples.size) { index -> pcmSamples[index].toFloat() / 32768.0f }
        demodulator.addSamples(floats, floats.size)

        val raw = received.get()
        assertNotNull("Legacy sanity decoder must receive direct packet", raw)
        val parsed = Parser.parseAX25(raw)
        assertEquals("N0CALL", parsed.sourceCall)
        assertEquals("APRS", parsed.destinationCall)
        assertEquals(">Direct Packet Test", parsed.aprsInformation.toString())
    }

    private fun legacyDecoder(onPacket: (ByteArray) -> Unit): Afsk1200Demodulator =
        Afsk1200Demodulator(
            12_000,
            1,
            6,
            object : PacketHandler {
                override fun handlePacket(packet: ByteArray) {
                    onPacket(packet.copyOf())
                }
            },
        )
}
