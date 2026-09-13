package org.aprsdroid.app.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Serial, conflated queries. Request/result callbacks share the caller's scope dispatcher. */
internal class LatestQuery<Request, Result>(
    scope: CoroutineScope,
    initialRequest: Request,
    private val query: suspend (Request) -> Result,
    private val onResult: (Result, Request) -> Unit,
    private val onFailure: (Exception) -> Unit,
    private val intervalMs: Long = 250L
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var latest = initialRequest

    init {
        scope.launch {
            for (request in requests) {
                // Fixed batching window: continuous RX must not starve the visible page.
                delay(intervalMs)
                while (requests.tryReceive().isSuccess) { /* fold the current burst */ }
                val current = latest
                try {
                    val result = query(current)
                    // A filter toggle during IO must not publish the previous filter's rows.
                    if (current == latest) onResult(result, current)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    onFailure(error)
                }
            }
        }
    }

    fun refresh(request: Request) {
        latest = request
        requests.trySend(Unit)
    }
}
