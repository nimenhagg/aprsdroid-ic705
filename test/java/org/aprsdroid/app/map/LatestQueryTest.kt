package org.aprsdroid.app.map

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.aprsdroid.app.data.repository.LatestQuery
import org.junit.Assert.*
import org.junit.Test

class LatestQueryTest {
    @Test fun clearingOwnerCancelsQueryAndNeverPublishes() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        var delivered = false
        var reportedCancellationAsFailure = false
        val queue = LatestQuery<Unit, Unit>(scope, Unit, query = {
            started.complete(Unit)
            try { awaitCancellation() } finally { stopped.complete(Unit) }
        }, onResult = { _, _ -> delivered = true },
            onFailure = { reportedCancellationAsFailure = true }, intervalMs = 0)
        try {
            queue.refresh(Unit)
            withTimeout(2000) { started.await() }
            scope.cancel()
            withTimeout(2000) { stopped.await() }
            queue.refresh(Unit)
            yield()
            assertFalse(delivered)
            assertFalse(reportedCancellationAsFailure)
        } finally { scope.cancel() }
    }

    @Test fun burstDuringSlowQueryCreatesOnlyOneFollowup() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()
        var queries = 0
        var results = 0
        try {
            val queue = LatestQuery(scope, Unit, query = {
                queries++
                if (queries == 1) { started.complete(Unit); release.await() }
                queries
            }, onResult = { _, _ -> if (++results == 2) complete.complete(Unit) },
                onFailure = { throw AssertionError(it) }, intervalMs = 0)
            queue.refresh(Unit)
            withTimeout(2000) { started.await() }
            repeat(1000) { queue.refresh(Unit) }
            assertEquals(1, queries)
            release.complete(Unit)
            withTimeout(2000) { complete.await() }
            yield()
            assertEquals(2, queries)
            assertEquals(2, results)
        } finally { scope.cancel() }
    }

    @Test fun filterChangeRejectsOldResultAndQueriesLatestFilter() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()
        val results = mutableListOf<Boolean>()
        try {
            val queue = LatestQuery(scope, true, query = { filter ->
                if (filter) { started.complete(Unit); release.await() }
                filter
            }, onResult = { result, _ -> results.add(result); complete.complete(Unit) },
                onFailure = { throw AssertionError(it) }, intervalMs = 0)
            queue.refresh(true)
            withTimeout(2000) { started.await() }
            queue.refresh(false)
            release.complete(Unit)
            withTimeout(2000) { complete.await() }
            assertEquals(listOf(false), results)
        } finally { scope.cancel() }
    }

    @Test fun queryFailureDoesNotKillSubsequentRefresh() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val failed = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Int>()
        try {
            val queue = LatestQuery(scope, 0, query = { request ->
                if (request == 0) error("test query failure")
                request
            }, onResult = { result, _ -> complete.complete(result) },
                onFailure = { failed.complete(Unit) }, intervalMs = 0)
            queue.refresh(0)
            withTimeout(2000) { failed.await() }
            queue.refresh(1)
            assertEquals(1, withTimeout(2000) { complete.await() })
        } finally { scope.cancel() }
    }
}
