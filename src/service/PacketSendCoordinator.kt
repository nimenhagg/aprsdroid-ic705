package org.aprsdroid.app.service

import net.ab0oo.aprs.parser.APRSPacket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns the single-threaded packet-send worker used by AprsService.
 *
 * Persistence callbacks intentionally run on the worker thread, matching the
 * legacy AprsService behavior. Only the final completion callback is marshalled
 * through postToMain.
 */
internal class PacketSendCoordinator(
    private val updateBackend: (APRSPacket) -> String?,
    private val onTxPost: (status: String, packetText: String) -> Unit,
    private val onErrorPost: (errorText: String) -> Unit,
    private val postToMain: ((() -> Unit) -> Unit),
    private val onFinished: (String) -> Unit,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) {
    fun send(packet: APRSPacket, statusPostfix: String = "") {
        executor.submit {
            val status = executePacketSend(
                updateBackend = { updateBackend(packet) },
                packetText = { packet.toString() },
                statusPostfix = statusPostfix,
                onTxPost = onTxPost,
                onErrorPost = onErrorPost,
            )
            postToMain { onFinished(status) }
        }
    }

    fun shutdownNow() {
        executor.shutdownNow()
    }
}

/**
 * Executes the non-threading send policy so result/error semantics can be host-JVM tested.
 */
internal fun executePacketSend(
    updateBackend: () -> String?,
    packetText: () -> String,
    statusPostfix: String,
    onTxPost: (status: String, packetText: String) -> Unit,
    onErrorPost: (errorText: String) -> Unit,
    reportException: (Exception) -> Unit = { it.printStackTrace() },
): String {
    return try {
        val status = (updateBackend() ?: NO_BACKEND_STATUS) + statusPostfix
        onTxPost(status, packetText())
        status
    } catch (e: Exception) {
        val errorText = e.toString()
        onErrorPost(errorText)
        reportException(e)
        errorText
    }
}

private const val NO_BACKEND_STATUS = "No poster"
