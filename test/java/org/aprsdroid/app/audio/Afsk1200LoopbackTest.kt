package org.aprsdroid.app.audio

import java.util.concurrent.atomic.AtomicReference
import net.ab0oo.aprs.parser.Parser
import org.aprsdroid.app.ic705.protocol.Ic705AudioPacketCodec
import org.aprsdroid.app.ic705.session.Ic705TxAudioPacketizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import sivantoledo.ax25.Packet

class Afsk1200LoopbackTest {

    @Test
    fun aprsPacketToAx25ModulationDemodulationRoundTrip() {
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

        // Parse initial packet using APRS parser to get APRSPacket instance
        val aprsPacket = Parser.parseAX25(initialAx25.bytesWithoutCRC())
        assertEquals(testSource, aprsPacket.sourceCall)
        assertEquals(testDest, aprsPacket.destinationCall)
        assertEquals(2, aprsPacket.digipeaters.size)

        // 1. Encode APRSPacket back into AX.25 Packet
        val ax25Packet = Ax25PacketEncoder.encode(aprsPacket)
        assertEquals(testSource, ax25Packet.source)
        assertEquals(testDest, ax25Packet.destination)
        assertEquals(2, ax25Packet.path.size)
        assertEquals("WIDE1-1", ax25Packet.path[0])
        assertEquals("WIDE2-1", ax25Packet.path[1])

        // 2. Modulate AX.25 Packet to 12 kHz PCM samples
        val generator = Afsk1200PcmGenerator(sampleRateHz = 12_000, txDelayMs = 250, txTail = 2)
        val pcmSamples = generator.generateSamples(ax25Packet)
        assert(pcmSamples.isNotEmpty())

        // 3. Packetize into IC-705 UDP datagrams
        val packetizer = Ic705TxAudioPacketizer(
            senderId = 0x12345678,
            receiverId = 0x76543210,
            initialOuterSequence = 10,
            initialAudioSequence = 100,
        )
        val datagrams = packetizer.packetize(pcmSamples, primeSilenceMs = 40, trailingSilenceMs = 40)
        assert(datagrams.isNotEmpty())

        // 4. Feed UDP datagrams into FeedableAfskDecoder through PCM unpacking
        val decodedFrame = AtomicReference<ByteArray>()
        val decoder = FeedableAfskDecoder(
            format = PcmFormat(sampleRateHz = 12_000),
            onPacket = { packet -> decodedFrame.set(packet) },
        )

        for (datagram in datagrams) {
            val audioPacket = Ic705AudioPacketCodec.decode(datagram)
            decoder.writePcm16Le(audioPacket.pcmPayload)
        }

        // 5. Verify the received AX.25 packet matches the original
        val receivedRaw = decodedFrame.get()
        assertNotNull("Decoder must have produced a raw AX.25 frame", receivedRaw)

        val parsedReceived = Parser.parseAX25(receivedRaw)
        assertEquals(testSource, parsedReceived.sourceCall)
        assertEquals(testDest, parsedReceived.destinationCall)
        assertEquals(testPayload, parsedReceived.aprsInformation.toString())
    }

    @Test
    fun rawAx25BytesDirectModulationRoundTrip() {
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
        val decoder = FeedableAfskDecoder(
            format = PcmFormat(sampleRateHz = 12_000),
            onPacket = { received.set(it) },
        )

        decoder.write(pcmSamples)

        val raw = received.get()
        assertNotNull("Decoder must receive direct packet", raw)
        val parsed = Parser.parseAX25(raw)
        assertEquals("N0CALL", parsed.sourceCall)
        assertEquals("APRS", parsed.destinationCall)
        assertEquals(">Direct Packet Test", parsed.aprsInformation.toString())
    }
}
