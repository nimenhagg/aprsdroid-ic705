package org.aprsdroid.app.radio

import org.aprsdroid.app.hamlib.HamlibRig
import org.aprsdroid.app.hamlib.HamlibRigCatalog

/**
 * Configuration and capability profile for a radio model supported via Hamlib / USB.
 *
 * Defines model identification, default CI-V/CAT parameters, supported baud rates,
 * and audio defaults.
 */
data class RadioProfile(
    val name: String,
    val hamlibModelId: Int,
    val defaultBaudRate: Int = DEFAULT_BAUD_RATE,
    val supportedBaudRates: List<Int> = DEFAULT_BAUD_RATES,
    val defaultCivAddress: Int? = null,
    val defaultAudioSampleRateHz: Int = DEFAULT_AUDIO_SAMPLE_RATE_HZ,
    val isExperimental: Boolean = false,
    val manufacturer: String = defaultManufacturer(name),
) {
    val isIcom: Boolean
        get() = defaultCivAddress != null || manufacturer.equals("Icom", ignoreCase = true)

    companion object {
        const val DEFAULT_BAUD_RATE = 19200
        val DEFAULT_BAUD_RATES = listOf(9600, 19200, 38400, 57600, 115200)
        const val DEFAULT_AUDIO_SAMPLE_RATE_HZ = 16000

        fun defaultManufacturer(name: String): String = when {
            name.startsWith("Icom", ignoreCase = true) || name.startsWith("IC-", ignoreCase = true) -> "Icom"
            name.startsWith("Yaesu", ignoreCase = true) || name.startsWith("FT", ignoreCase = true) -> "Yaesu"
            name.startsWith("Kenwood", ignoreCase = true) || name.startsWith("TS", ignoreCase = true) -> "Kenwood"
            name.startsWith("Elecraft", ignoreCase = true) || name.startsWith("KX", ignoreCase = true) -> "Elecraft"
            name.startsWith("Hamlib", ignoreCase = true) || name.startsWith("Dummy", ignoreCase = true) -> "Hamlib"
            else -> "Other"
        }

        /**
         * Icom IC-705 connected via USB OTG.
         * Primary target hardware: features built-in CP2105 dual UART + USB Audio codec.
         */
        val IC705_USB = RadioProfile(
            name = "Icom IC-705 (USB)",
            hamlibModelId = 3085,
            defaultBaudRate = 19200,
            supportedBaudRates = listOf(9600, 19200, 38400, 115200),
            defaultCivAddress = 0xA4,
            defaultAudioSampleRateHz = 16000,
            isExperimental = false,
            manufacturer = "Icom",
        )

        /**
         * Icom IC-7100 connected via USB.
         */
        val IC7100_USB = RadioProfile(
            name = "Icom IC-7100 (USB)",
            hamlibModelId = 3070,
            defaultBaudRate = 19200,
            supportedBaudRates = listOf(9600, 19200, 38400, 115200),
            defaultCivAddress = 0x88,
            defaultAudioSampleRateHz = 16000,
            isExperimental = true,
            manufacturer = "Icom",
        )

        /**
         * Icom IC-7300 connected via USB.
         */
        val IC7300_USB = RadioProfile(
            name = "Icom IC-7300 (USB)",
            hamlibModelId = 3073,
            defaultBaudRate = 19200,
            supportedBaudRates = listOf(9600, 19200, 38400, 115200),
            defaultCivAddress = 0x94,
            defaultAudioSampleRateHz = 16000,
            isExperimental = true,
            manufacturer = "Icom",
        )

        /**
         * Icom IC-7610 connected via USB.
         */
        val IC7610_USB = RadioProfile(
            name = "Icom IC-7610 (USB)",
            hamlibModelId = 3078,
            defaultBaudRate = 19200,
            supportedBaudRates = listOf(9600, 19200, 38400, 115200),
            defaultCivAddress = 0x98,
            defaultAudioSampleRateHz = 16000,
            isExperimental = true,
            manufacturer = "Icom",
        )

        /**
         * Icom IC-9700 connected via USB.
         */
        val IC9700_USB = RadioProfile(
            name = "Icom IC-9700 (USB)",
            hamlibModelId = 3081,
            defaultBaudRate = 19200,
            supportedBaudRates = listOf(9600, 19200, 38400, 115200),
            defaultCivAddress = 0xA2,
            defaultAudioSampleRateHz = 16000,
            isExperimental = true,
            manufacturer = "Icom",
        )

        /**
         * Yaesu FT-891 connected via USB.
         */
        val FT891_USB = RadioProfile(
            name = "Yaesu FT-891 (USB)",
            hamlibModelId = 1036,
            defaultBaudRate = 38400,
            supportedBaudRates = listOf(9600, 19200, 38400),
            defaultCivAddress = null,
            defaultAudioSampleRateHz = 16000,
            isExperimental = true,
            manufacturer = "Yaesu",
        )

        /**
         * Yaesu FT-991 / FT-991A connected via USB.
         */
        val FT991A_USB = RadioProfile(
            name = "Yaesu FT-991/A (USB)",
            hamlibModelId = 1035,
            defaultBaudRate = 38400,
            supportedBaudRates = listOf(9600, 19200, 38400),
            defaultCivAddress = null,
            defaultAudioSampleRateHz = 16000,
            isExperimental = true,
            manufacturer = "Yaesu",
        )

        /**
         * Yaesu FTDX10 connected via USB.
         */
        val FTDX10_USB = RadioProfile(
            name = "Yaesu FTDX10 (USB)",
            hamlibModelId = 1042,
            defaultBaudRate = 38400,
            supportedBaudRates = listOf(9600, 19200, 38400),
            defaultCivAddress = null,
            defaultAudioSampleRateHz = 16000,
            isExperimental = true,
            manufacturer = "Yaesu",
        )

        /**
         * Yaesu FT-710 connected via USB.
         */
        val FT710_USB = RadioProfile(
            name = "Yaesu FT-710 (USB)",
            hamlibModelId = 1045,
            defaultBaudRate = 38400,
            supportedBaudRates = listOf(9600, 19200, 38400),
            defaultCivAddress = null,
            defaultAudioSampleRateHz = 16000,
            isExperimental = true,
            manufacturer = "Yaesu",
        )

        /**
         * Kenwood TS-590SG connected via USB.
         */
        val TS590SG_USB = RadioProfile(
            name = "Kenwood TS-590SG (USB)",
            hamlibModelId = 2031,
            defaultBaudRate = 115200,
            supportedBaudRates = listOf(9600, 19200, 38400, 57600, 115200),
            defaultCivAddress = null,
            defaultAudioSampleRateHz = 16000,
            isExperimental = true,
            manufacturer = "Kenwood",
        )

        /**
         * Kenwood TS-890S connected via USB.
         */
        val TS890S_USB = RadioProfile(
            name = "Kenwood TS-890S (USB)",
            hamlibModelId = 2036,
            defaultBaudRate = 115200,
            supportedBaudRates = listOf(9600, 19200, 38400, 57600, 115200),
            defaultCivAddress = null,
            defaultAudioSampleRateHz = 16000,
            isExperimental = true,
            manufacturer = "Kenwood",
        )

        /**
         * Hamlib Dummy Rig for testing and verification without physical radio.
         */
        val HAMLIB_DUMMY = RadioProfile(
            name = "Hamlib Dummy Rig",
            hamlibModelId = 1,
            defaultBaudRate = 19200,
            supportedBaudRates = listOf(19200),
            defaultCivAddress = null,
            defaultAudioSampleRateHz = 16000,
            isExperimental = false,
            manufacturer = "Hamlib",
        )

        val PRESETS: List<RadioProfile> = listOf(
            IC705_USB,
            IC7100_USB,
            IC7300_USB,
            IC7610_USB,
            IC9700_USB,
            FT891_USB,
            FT991A_USB,
            FTDX10_USB,
            FT710_USB,
            TS590SG_USB,
            TS890S_USB,
            HAMLIB_DUMMY,
        )

        val RECOMMENDED: List<RadioProfile> = listOf(
            IC705_USB,
            IC7100_USB,
            IC7300_USB,
            HAMLIB_DUMMY,
        )

        val MANUFACTURERS: List<String> = listOf(
            "All",
            "Recommended",
            "Icom",
            "Yaesu",
            "Kenwood",
            "Hamlib",
            "Other",
        )

        fun fromHamlibRig(rig: HamlibRig): RadioProfile {
            PRESETS.firstOrNull { it.hamlibModelId == rig.modelId }?.let { return it }

            val mfr = rig.manufacturer.trim().ifEmpty { "Other" }
            val model = rig.model.trim()
            val fullName = if (model.startsWith(mfr, ignoreCase = true)) model else "$mfr $model"

            val baud = when {
                mfr.equals("Icom", ignoreCase = true) -> 19200
                mfr.equals("Yaesu", ignoreCase = true) -> 38400
                mfr.equals("Kenwood", ignoreCase = true) -> 115200
                else -> DEFAULT_BAUD_RATE
            }

            return RadioProfile(
                name = fullName,
                hamlibModelId = rig.modelId,
                defaultBaudRate = baud,
                supportedBaudRates = DEFAULT_BAUD_RATES,
                defaultCivAddress = null,
                defaultAudioSampleRateHz = DEFAULT_AUDIO_SAMPLE_RATE_HZ,
                isExperimental = !rig.isStable,
                manufacturer = mfr,
            )
        }

        fun allProfiles(): List<RadioProfile> {
            val catalogRigs = HamlibRigCatalog.list()
            val allList = if (catalogRigs.isEmpty()) {
                PRESETS
            } else {
                val catalogProfiles = catalogRigs.map { fromHamlibRig(it) }
                val presetIds = PRESETS.map { it.hamlibModelId }.toSet()
                val nonPresetCatalog = catalogProfiles.filter { it.hamlibModelId !in presetIds }
                PRESETS + nonPresetCatalog
            }
            return allList.distinctBy { it.hamlibModelId }.sortedWith(
                compareBy<RadioProfile> { it.manufacturer.lowercase() }
                    .thenBy { it.name.lowercase() }
            )
        }

        fun findByModelId(modelId: Int): RadioProfile? {
            PRESETS.firstOrNull { it.hamlibModelId == modelId }?.let { return it }
            return HamlibRigCatalog.findByModelId(modelId)?.let { fromHamlibRig(it) }
        }

        fun findByName(name: String): RadioProfile? =
            allProfiles().firstOrNull { it.name.equals(name, ignoreCase = true) }

        fun createCustom(
            name: String,
            modelId: Int,
            baudRate: Int = DEFAULT_BAUD_RATE,
            civAddress: Int? = null,
            audioSampleRateHz: Int = DEFAULT_AUDIO_SAMPLE_RATE_HZ,
            manufacturer: String = defaultManufacturer(name),
        ): RadioProfile = RadioProfile(
            name = name,
            hamlibModelId = modelId,
            defaultBaudRate = baudRate,
            supportedBaudRates = listOf(baudRate),
            defaultCivAddress = civAddress,
            defaultAudioSampleRateHz = audioSampleRateHz,
            isExperimental = true,
            manufacturer = manufacturer,
        )
    }
}
