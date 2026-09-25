package org.aprsdroid.app.hamlib

/**
 * Result codes exchanged across the Hamlib JNI boundary.
 *
 * Hamlib functions return `RIG_OK` (0) or the negative of an
 * `enum rig_errcode_e` value, so `-RIG_ETIMEOUT` is reported as `-5` here.
 * The codes below `-4096` are produced by the thin JNI layer itself and can
 * never collide with a Hamlib return value.
 */
object HamlibError {
    const val OK = 0

    /** No handle with this id is (or is still) registered. */
    const val UNKNOWN_HANDLE = -4096

    /** The fixed handle registry is full; a handle leaked somewhere. */
    const val TOO_MANY_HANDLES = -4097

    /** Hamlib backends could not be loaded, so no rig can be created. */
    const val BACKENDS_UNAVAILABLE = -4098

    /** A JNI string could not be converted. */
    const val STRING_CONVERSION_FAILED = -4099

    /** A caller-supplied output array was too small. */
    const val OUTPUT_ARRAY_TOO_SMALL = -4100

    private val hamlibNames = arrayOf(
        "success",                     // 0
        "invalid parameter",           // 1  RIG_EINVAL
        "invalid configuration",       // 2  RIG_ECONF
        "memory shortage",             // 3  RIG_ENOMEM
        "function not implemented",    // 4  RIG_ENIMPL
        "communication timed out",     // 5  RIG_ETIMEOUT
        "I/O error",                   // 6  RIG_EIO
        "internal Hamlib error",       // 7  RIG_EINTERNAL
        "protocol error",              // 8  RIG_EPROTO
        "command rejected by the rig", // 9  RIG_ERJCTED
        "argument truncated",          // 10 RIG_ETRUNC
        "function not available",      // 11 RIG_ENAVAIL
        "VFO not targetable",          // 12 RIG_ENTARGET
        "bus error",                   // 13 RIG_BUSERROR
        "bus busy",                    // 14 RIG_BUSBUSY
        "invalid argument pointer",    // 15 RIG_EARG
        "invalid VFO",                 // 16 RIG_EVFO
        "argument out of domain",      // 17 RIG_EDOM
        "function deprecated",         // 18 RIG_EDEPRECATED
        "security error",              // 19 RIG_ESECURITY
        "rig not powered on",          // 20 RIG_EPOWER
        "limit exceeded",              // 21 RIG_ELIMIT
        "access denied",               // 22 RIG_EACCESS
    )

    private val jniNames = mapOf(
        UNKNOWN_HANDLE to "unknown or closed Hamlib handle",
        TOO_MANY_HANDLES to "too many Hamlib handles open",
        BACKENDS_UNAVAILABLE to "Hamlib backends could not be loaded",
        STRING_CONVERSION_FAILED to "JNI string conversion failed",
        OUTPUT_ARRAY_TOO_SMALL to "JNI output array too small",
    )

    fun isOk(code: Int): Boolean = code == OK

    fun isHandleError(code: Int): Boolean = code == UNKNOWN_HANDLE

    /** Human readable name for a return code, independent of native text. */
    fun name(code: Int): String {
        jniNames[code]?.let { return it }
        val magnitude = if (code < 0) -code else code
        if (magnitude in hamlibNames.indices) {
            return hamlibNames[magnitude]
        }
        return "unrecognised Hamlib code $code"
    }

    /**
     * Builds the message used by [HamlibException]. The native text produced by
     * `rigerror()` is only appended when it adds information.
     */
    fun describe(code: Int, nativeText: String? = null): String {
        val name = name(code)
        val text = nativeText?.trim().orEmpty()
        return when {
            text.isEmpty() -> "$name ($code)"
            text.equals(name, ignoreCase = true) -> "$name ($code)"
            else -> "$name ($code): $text"
        }
    }

    /** Throws unless [code] is [OK]; used by every JNI wrapper call. */
    fun require(code: Int, operation: String, nativeText: String? = null) {
        if (!isOk(code)) {
            throw HamlibException(code, "$operation failed - ${describe(code, nativeText)}")
        }
    }
}

/** Raised for every non-OK Hamlib or JNI return code. */
class HamlibException(
    val code: Int,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Raised when Kotlin-side code uses a handle after [HamlibHandle.close]. */
class HamlibClosedException(message: String) : IllegalStateException(message)

/** Raised when the native Hamlib JNI library cannot be loaded. */
class HamlibUnavailableException(message: String, cause: Throwable?) :
    IllegalStateException(message, cause)
