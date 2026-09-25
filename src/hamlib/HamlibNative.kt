package org.aprsdroid.app.hamlib

/**
 * Raw JNI surface for the thin Hamlib boundary (AGENT.md section 16.3).
 *
 * This object is intentionally internal: callers use [HamlibRigCatalog] and
 * [HamlibHandle] so handle lifetime, error mapping and thread serialization
 * stay in one place. Every function returns a Hamlib/JNI result code, and
 * getters write their value into a caller-provided array so the code is never
 * lost.
 */
internal object HamlibNative {

    private fun requireLibrary() = HamlibLibrary.load()

    fun version(): String {
        requireLibrary()
        return nativeVersion()
    }

    fun backendRevision(): String {
        requireLibrary()
        return nativeBackendRevision()
    }

    fun rigCount(): Int {
        requireLibrary()
        return nativeRigCount()
    }

    fun listRigs(): List<String> {
        requireLibrary()
        return nativeListRigs()?.toList().orEmpty()
    }

    fun create(modelId: Int): Long {
        requireLibrary()
        return nativeCreate(modelId)
    }

    fun open(handle: Long, pathname: String?): Int = nativeOpen(handle, pathname)

    fun closeRig(handle: Long): Int = nativeClose(handle)

    fun destroy(handle: Long): Int = nativeDestroy(handle)

    fun isOpen(handle: Long): Int = nativeIsOpen(handle)

    fun getFrequency(handle: Long): Pair<Int, Double> {
        val out = DoubleArray(1)
        val code = nativeGetFreq(handle, out)
        return code to out[0]
    }

    fun setFrequency(handle: Long, hz: Double): Int = nativeSetFreq(handle, hz)

    fun getMode(handle: Long): Triple<Int, Long, Long> {
        val out = LongArray(2)
        val code = nativeGetMode(handle, out)
        return Triple(code, out[0], out[1])
    }

    fun setMode(handle: Long, mode: Long, passbandHz: Long): Int =
        nativeSetMode(handle, mode, passbandHz)

    fun getPtt(handle: Long): Pair<Int, Int> {
        val out = IntArray(1)
        val code = nativeGetPtt(handle, out)
        return code to out[0]
    }

    fun setPtt(handle: Long, on: Boolean): Int = nativeSetPtt(handle, on)

    fun errorText(code: Int): String = try {
        nativeErrorText(code)
    } catch (_: Throwable) {
        ""
    }

    fun modeName(mode: Long): String = try {
        nativeModeName(mode)
    } catch (_: Throwable) {
        ""
    }

    private external fun nativeVersion(): String
    private external fun nativeBackendRevision(): String
    private external fun nativeRigCount(): Int
    private external fun nativeListRigs(): Array<String>?
    private external fun nativeCreate(modelId: Int): Long
    private external fun nativeOpen(handle: Long, pathname: String?): Int
    private external fun nativeClose(handle: Long): Int
    private external fun nativeDestroy(handle: Long): Int
    private external fun nativeIsOpen(handle: Long): Int
    private external fun nativeGetFreq(handle: Long, out: DoubleArray): Int
    private external fun nativeSetFreq(handle: Long, hz: Double): Int
    private external fun nativeGetMode(handle: Long, out: LongArray): Int
    private external fun nativeSetMode(handle: Long, mode: Long, width: Long): Int
    private external fun nativeGetPtt(handle: Long, out: IntArray): Int
    private external fun nativeSetPtt(handle: Long, on: Boolean): Int
    private external fun nativeErrorText(code: Int): String
    private external fun nativeModeName(mode: Long): String
}
