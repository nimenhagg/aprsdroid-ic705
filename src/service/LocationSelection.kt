package org.aprsdroid.app.service

/**
 * Returns the first candidate with the greatest timestamp.
 *
 * Keeping this decision independent from Android Location objects makes the
 * cached-location selection rule directly testable on the host JVM.
 */
internal fun <T : Any> newestByTimestamp(
    candidates: Iterable<T>,
    timestampOf: (T) -> Long,
): T? {
    var newest: T? = null
    var newestTimestamp = Long.MIN_VALUE
    for (candidate in candidates) {
        val timestamp = timestampOf(candidate)
        if (newest == null || timestamp > newestTimestamp) {
            newest = candidate
            newestTimestamp = timestamp
        }
    }
    return newest
}
