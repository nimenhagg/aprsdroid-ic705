package org.aprsdroid.app.hamlib

/** Per-rig capability flags decoded from the JNI enumeration. */
data class HamlibRigCapabilities(
    val canGetFrequency: Boolean,
    val canSetFrequency: Boolean,
    val canGetMode: Boolean,
    val canSetMode: Boolean,
    val canGetPtt: Boolean,
    val canSetPtt: Boolean,
    val supportedModesMask: Long,
    val portType: Int,
) {
    val canControlFrequency: Boolean get() = canGetFrequency && canSetFrequency
    val canControlMode: Boolean get() = canGetMode && canSetMode
    val canControlPtt: Boolean get() = canGetPtt && canSetPtt

    companion object {
        const val FLAG_GET_FREQUENCY = 1 shl 0
        const val FLAG_SET_FREQUENCY = 1 shl 1
        const val FLAG_GET_MODE = 1 shl 2
        const val FLAG_SET_MODE = 1 shl 3
        const val FLAG_GET_PTT = 1 shl 4
        const val FLAG_SET_PTT = 1 shl 5

        fun fromFlags(flags: Int, modesMask: Long, portType: Int) = HamlibRigCapabilities(
            canGetFrequency = flags and FLAG_GET_FREQUENCY != 0,
            canSetFrequency = flags and FLAG_SET_FREQUENCY != 0,
            canGetMode = flags and FLAG_GET_MODE != 0,
            canSetMode = flags and FLAG_SET_MODE != 0,
            canGetPtt = flags and FLAG_GET_PTT != 0,
            canSetPtt = flags and FLAG_SET_PTT != 0,
            supportedModesMask = modesMask,
            portType = portType,
        )
    }
}

/**
 * One Hamlib rig model as reported by `rig_list_foreach`.
 *
 * The JNI layer sends tab separated ASCII lines; [decode] is the single place
 * that knows that encoding, which keeps the native side free of JNI objects
 * for every field.
 */
data class HamlibRig(
    val modelId: Int,
    val manufacturer: String,
    val model: String,
    val driverVersion: String,
    val driverStatus: Int,
    val capabilities: HamlibRigCapabilities,
) {
    val displayName: String
        get() = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ")

    val isStable: Boolean get() = driverStatus == DRIVER_STATUS_STABLE

    companion object {
        const val DRIVER_STATUS_ALPHA = 0
        const val DRIVER_STATUS_UNTESTED = 1
        const val DRIVER_STATUS_BETA = 2
        const val DRIVER_STATUS_STABLE = 3
        const val DRIVER_STATUS_BUGGY = 4

        /**
         * Decodes one enumeration line: `model, manufacturer, model name,
         * driver version, driver status, capability flags, mode mask, port
         * type`. Returns `null` for malformed input instead of throwing, so one
         * unexpected backend cannot break the whole catalog.
         */
        fun decode(line: String): HamlibRig? {
            val parts = line.split('\t')
            if (parts.size != 8) return null
            val modelId = parts[0].toIntOrNull() ?: return null
            val status = parts[4].toIntOrNull() ?: return null
            val flags = parts[5].toIntOrNull() ?: return null
            val modesMask = parts[6].toLongOrNull() ?: return null
            val portType = parts[7].toIntOrNull() ?: return null
            return HamlibRig(
                modelId = modelId,
                manufacturer = parts[1],
                model = parts[2],
                driverVersion = parts[3],
                driverStatus = status,
                capabilities = HamlibRigCapabilities.fromFlags(flags, modesMask, portType),
            )
        }

        fun decodeOrNull(line: String): HamlibRig? = try {
            decode(line)
        } catch (_: Throwable) {
            null
        }
    }
}

/** Read-only view of the rig models the pinned Hamlib build knows about. */
object HamlibRigCatalog {
    /** Hamlib's built-in dummy backend, used for hardware-free verification. */
    const val DUMMY_MODEL_ID = 1

    /** Model number of the dummy backend variant without VFO support. */
    const val DUMMY_NOVFO_MODEL_ID = 6

    @Volatile
    private var cachedList: List<HamlibRig>? = null

    fun list(): List<HamlibRig> {
        cachedList?.let { return it }
        val result = try {
            HamlibNative.listRigs()
                .mapNotNull(HamlibRig::decodeOrNull)
                .sortedWith(compareBy({ it.manufacturer.lowercase() }, { it.model.lowercase() }))
        } catch (_: Throwable) {
            emptyList()
        }
        if (result.isNotEmpty()) {
            cachedList = result
        }
        return result
    }

    fun count(): Int {
        cachedList?.let { return it.size }
        return try {
            HamlibNative.rigCount()
        } catch (_: Throwable) {
            0
        }
    }

    fun findByModelId(modelId: Int): HamlibRig? = list().firstOrNull { it.modelId == modelId }

    fun version(): String = try { HamlibNative.version() } catch (_: Throwable) { "" }

    fun backendRevision(): String = try { HamlibNative.backendRevision() } catch (_: Throwable) { "" }
}
