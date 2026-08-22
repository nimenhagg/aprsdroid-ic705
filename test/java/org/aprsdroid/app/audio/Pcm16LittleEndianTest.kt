package org.aprsdroid.app.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Pcm16LittleEndianTest {
    @Test
    fun decodesSignedBoundaryValues() {
        val bytes = byteArrayOf(
            0x00, 0x00,
            0xff.toByte(), 0x7f,
            0x00, 0x80.toByte(),
            0xff.toByte(), 0xff.toByte(),
        )

        assertArrayEquals(
            shortArrayOf(0, Short.MAX_VALUE, Short.MIN_VALUE, -1),
            Pcm16LittleEndian.decode(bytes),
        )
    }

    @Test
    fun honorsOffsetAndEffectiveLength() {
        val bytes = byteArrayOf(
            0x55,
            0x34, 0x12,
            0x00, 0x80.toByte(),
            0x66,
        )

        assertArrayEquals(
            shortArrayOf(0x1234, Short.MIN_VALUE),
            Pcm16LittleEndian.decode(bytes, offset = 1, length = 4),
        )
    }

    @Test
    fun normalizesOnlyRequestedSamples() {
        val destination = FloatArray(4) { 42.0f }

        val count = Pcm16LittleEndian.normalizeToFloats(
            samples = shortArrayOf(111, Short.MIN_VALUE, 0, Short.MAX_VALUE, 222),
            offset = 1,
            length = 3,
            destination = destination,
        )

        assertEquals(3, count)
        assertEquals(-1.0f, destination[0], 0.0f)
        assertEquals(0.0f, destination[1], 0.0f)
        assertEquals(Short.MAX_VALUE.toFloat() / 32768.0f, destination[2], 0.0f)
        assertEquals(42.0f, destination[3], 0.0f)
    }

    @Test
    fun decodesOnlyEffectiveByteLengthToFloats() {
        val destination = FloatArray(3) { 42.0f }

        val count = Pcm16LittleEndian.decodeToFloats(
            bytes = byteArrayOf(0x55, 0x00, 0x80.toByte(), 0x00, 0x00, 0x66),
            offset = 1,
            length = 4,
            destination = destination,
        )

        assertEquals(2, count)
        assertEquals(-1.0f, destination[0], 0.0f)
        assertEquals(0.0f, destination[1], 0.0f)
        assertEquals(42.0f, destination[2], 0.0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPartialPcm16Sample() {
        Pcm16LittleEndian.decode(byteArrayOf(0x01), length = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRangePastEndOfBuffer() {
        Pcm16LittleEndian.decode(byteArrayOf(0x00, 0x00), offset = 1, length = 2)
    }
}
