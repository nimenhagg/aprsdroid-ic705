package org.aprsdroid.app.hamlib

import java.util.concurrent.atomic.AtomicBoolean

/** A rig/PKT mode pair as reported by Hamlib. */
data class HamlibMode(val mode: Long, val passbandHz: Long) {
    fun matches(other: Long): Boolean = mode == other
    override fun toString(): String = "mode=0x${mode.toString(16)} passband=$passbandHz"
}

/**
 * Owning wrapper around one Hamlib rig handle.
 *
 * The native side already refuses unknown or destroyed handles, and this class
 * adds a Kotlin-side guard so a use-after-close fails fast with a clear error
 * instead of reaching C at all. Both layers are covered by tests.
 *
 * Instances are safe to share between threads: the native boundary serializes
 * every call per handle, and [closed] only ever flips once.
 */
class HamlibHandle private constructor(
    private val nativeId: Long,
    /** Hamlib model number this handle was created for. */
    val modelId: Int,
) : AutoCloseable {

    private val closed = AtomicBoolean(false)

    /** True while the rig port is open (`rig_open` succeeded). */
    val isOpen: Boolean
        get() {
            requireUsable()
            val result = HamlibNative.isOpen(nativeId)
            if (HamlibError.isHandleError(result)) return false
            return result == 1
        }

    val isClosed: Boolean get() = closed.get()

    /** Opens the rig port. A null/blank [pathname] uses the backend default. */
    fun open(pathname: String? = null) {
        requireUsable()
        val code = HamlibNative.open(nativeId, pathname)
        HamlibError.require(code, "rig_open", HamlibNative.errorText(code))
    }

    /** Closes the rig port but keeps the handle usable for a later [open]. */
    fun closePort() {
        requireUsable()
        val code = HamlibNative.closeRig(nativeId)
        HamlibError.require(code, "rig_close", HamlibNative.errorText(code))
    }

    fun frequencyHz(): Double {
        requireUsable()
        val (code, value) = HamlibNative.getFrequency(nativeId)
        HamlibError.require(code, "rig_get_freq", HamlibNative.errorText(code))
        return value
    }

    fun setFrequencyHz(hz: Double) {
        requireUsable()
        val code = HamlibNative.setFrequency(nativeId, hz)
        HamlibError.require(code, "rig_set_freq", HamlibNative.errorText(code))
    }

    fun mode(): HamlibMode {
        requireUsable()
        val (code, mode, passband) = HamlibNative.getMode(nativeId)
        HamlibError.require(code, "rig_get_mode", HamlibNative.errorText(code))
        return HamlibMode(mode, passband)
    }

    fun setMode(mode: Long, passbandHz: Long = 0L) {
        requireUsable()
        val code = HamlibNative.setMode(nativeId, mode, passbandHz)
        HamlibError.require(code, "rig_set_mode", HamlibNative.errorText(code))
    }

    fun isPttOn(): Boolean {
        requireUsable()
        val (code, ptt) = HamlibNative.getPtt(nativeId)
        HamlibError.require(code, "rig_get_ptt", HamlibNative.errorText(code))
        return ptt == PTT_ON
    }

    /**
     * Requests a PTT state.
     *
     * This is the raw Hamlib command only. APRSdroid's IC-705 Wi-Fi path keeps
     * its own ACK-based PTT state machine and must not be replaced by this call
     * (AGENT.md section 16.6).
     */
    fun setPtt(on: Boolean) {
        requireUsable()
        val code = HamlibNative.setPtt(nativeId, on)
        HamlibError.require(code, "rig_set_ptt", HamlibNative.errorText(code))
    }

    /** Releases the native handle. Idempotent. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val code = HamlibNative.destroy(nativeId)
        if (!HamlibError.isOk(code) && !HamlibError.isHandleError(code)) {
            throw HamlibException(code, "rig_cleanup failed - ${HamlibError.describe(code)}")
        }
    }

    private fun requireUsable() {
        if (closed.get()) {
            throw HamlibClosedException(
                "Hamlib handle for model $modelId was already closed",
            )
        }
    }

    companion object {
        const val PTT_OFF = 0
        const val PTT_ON = 1

        /**
         * Creates a handle for [modelId].
         *
         * @throws HamlibException when the model is unknown or the registry is
         *   full, so a leaked handle cannot be mistaken for a slow radio.
         */
        fun create(modelId: Int): HamlibHandle {
            val id = HamlibNative.create(modelId)
            if (id <= 0) {
                val code = if (id == 0L) HamlibError.UNKNOWN_HANDLE else id.toInt()
                val detail = if (code == 0) {
                    "unknown or unsupported model"
                } else {
                    HamlibError.describe(code)
                }
                throw HamlibException(code, "rig_init failed for model $modelId - $detail")
            }
            return HamlibHandle(id, modelId)
        }

        /** Convenience factory for the hardware-free dummy backend. */
        fun createDummy(): HamlibHandle = create(HamlibRigCatalog.DUMMY_MODEL_ID)
    }
}

/** Mode mask helpers for the RIG_MODE_* bit flags used by tests and callers. */
object HamlibModes {
    const val NONE = 0L
    const val AM = 1L shl 0
    const val CW = 1L shl 1
    const val USB = 1L shl 2
    const val LSB = 1L shl 3
    const val RTTY = 1L shl 4
    const val FM = 1L shl 5
    const val WFM = 1L shl 6
    const val PKTLSB = 1L shl 10
    const val PKTUSB = 1L shl 11
    const val PKTFM = 1L shl 12

    /** Mode name reported by Hamlib (`rig_strrmode`), for logs and diagnostics. */
    fun name(mode: Long): String = HamlibNative.modeName(mode)
}
