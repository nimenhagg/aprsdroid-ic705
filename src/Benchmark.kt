package org.aprsdroid.app

import android.util.Log

object Benchmark {
    inline fun <T> measure(tag: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        return try {
            block()
        } finally {
            val exectime = System.currentTimeMillis() - start
            Log.d(tag, String.format(null, "execution time: %.3f s", exectime / 1000.0))
        }
    }

    inline operator fun <T> invoke(tag: String, block: () -> T): T = measure(tag, block)
}
