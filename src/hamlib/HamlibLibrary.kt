package org.aprsdroid.app.hamlib

/**
 * Loads `libaprs_hamlib.so`.
 *
 * Android builds ship the library inside the APK, so it is loaded by name. JVM
 * tests use an explicit host build instead and point at it with
 * `-Daprsdroid.hamlib.native.path=` or `APRSDROID_HAMLIB_NATIVE=`.
 *
 * Loading never falls back silently: a missing or broken library surfaces as
 * [HamlibUnavailableException] and stays cached, so callers cannot end up with
 * a half-initialised Hamlib layer.
 */
object HamlibLibrary {
    const val NATIVE_LIBRARY_NAME = "aprs_hamlib"
    const val NATIVE_PATH_PROPERTY = "aprsdroid.hamlib.native.path"
    const val NATIVE_PATH_ENV = "APRSDROID_HAMLIB_NATIVE"

    @Volatile
    private var loaded = false

    @Volatile
    private var failure: HamlibUnavailableException? = null

    val isLoaded: Boolean
        get() = loaded

    /** Loads the library on first use and caches the outcome. */
    @Synchronized
    fun load() {
        if (loaded) return
        failure?.let { throw it }
        try {
            val explicit = System.getProperty(NATIVE_PATH_PROPERTY)?.takeIf { it.isNotBlank() }
                ?: System.getenv(NATIVE_PATH_ENV)?.takeIf { it.isNotBlank() }
            if (explicit != null) {
                System.load(explicit)
            } else {
                System.loadLibrary(NATIVE_LIBRARY_NAME)
            }
            loaded = true
        } catch (error: Throwable) {
            val unavailable = HamlibUnavailableException(
                "Hamlib native library '$NATIVE_LIBRARY_NAME' is not available " +
                    "(set $NATIVE_PATH_PROPERTY for host tests): ${error.message}",
                error,
            )
            failure = unavailable
            throw unavailable
        }
    }

    /** Test-friendly probe that reports availability instead of throwing. */
    fun isAvailable(): Boolean = try {
        load()
        true
    } catch (_: HamlibUnavailableException) {
        false
    }

    /** Path or library name the loader would use; diagnostics only. */
    fun describeSource(): String =
        System.getProperty(NATIVE_PATH_PROPERTY)?.takeIf { it.isNotBlank() }
            ?: System.getenv(NATIVE_PATH_ENV)?.takeIf { it.isNotBlank() }
            ?: "lib$NATIVE_LIBRARY_NAME.so (linked into the APK)"
}
