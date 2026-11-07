package org.aprsdroid.app.test

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.internal.MainDispatcherFactory
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

@OptIn(InternalCoroutinesApi::class)
class TestMainDispatcherFactory : MainDispatcherFactory {
    override val loadPriority: Int
        get() = 1000

    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "test-main-dispatcher").apply { isDaemon = true }
    }
    private val delegate = executor.asCoroutineDispatcher()

    private val mainDispatcher = object : MainCoroutineDispatcher() {
        override val immediate: MainCoroutineDispatcher
            get() = this

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            delegate.dispatch(context, block)
        }
    }

    override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher {
        return mainDispatcher
    }
}
