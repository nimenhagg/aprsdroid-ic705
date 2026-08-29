package org.aprsdroid.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class PacketSendCoordinatorTest {
    @Test
    fun successAppendsPostfixAndPostsTxRecord() {
        val events = mutableListOf<String>()

        val result = executePacketSend(
            updateBackend = { "Sent" },
            packetText = { "CALL>APRS:test" },
            statusPostfix = " (±5m)",
            onTxPost = { status, packet -> events += "tx:$status:$packet" },
            onErrorPost = { error -> events += "error:$error" },
            reportException = {},
        )

        assertEquals("Sent (±5m)", result)
        assertEquals(listOf("tx:Sent (±5m):CALL>APRS:test"), events)
    }

    @Test
    fun missingBackendPreservesNoPosterFallback() {
        val events = mutableListOf<String>()

        val result = executePacketSend(
            updateBackend = { null },
            packetText = { "packet" },
            statusPostfix = "",
            onTxPost = { status, packet -> events += "tx:$status:$packet" },
            onErrorPost = { error -> events += "error:$error" },
            reportException = {},
        )

        assertEquals("No poster", result)
        assertEquals(listOf("tx:No poster:packet"), events)
    }

    @Test
    fun backendExceptionBecomesErrorPostAndFinalStatus() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("boom")

        val result = executePacketSend(
            updateBackend = { throw failure },
            packetText = { "unused" },
            statusPostfix = "",
            onTxPost = { status, packet -> events += "tx:$status:$packet" },
            onErrorPost = { error -> events += "error:$error" },
            reportException = {},
        )

        assertEquals(failure.toString(), result)
        assertEquals(listOf("error:${failure}"), events)
    }

    @Test
    fun txPersistenceExceptionUsesSameErrorPath() {
        val events = mutableListOf<String>()
        val failure = IllegalArgumentException("db")

        val result = executePacketSend(
            updateBackend = { "Sent" },
            packetText = { "packet" },
            statusPostfix = "",
            onTxPost = { _, _ -> throw failure },
            onErrorPost = { error -> events += "error:$error" },
            reportException = {},
        )

        assertEquals(failure.toString(), result)
        assertEquals(listOf("error:${failure}"), events)
    }
}
