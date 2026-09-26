package org.aprsdroid.app.radio

import org.aprsdroid.app.ic705.protocol.Ic705CivCommands

/**
 * Radio models supported via WLAN / Wi-Fi / Icom LAN.
 *
 * Each model defines its default display name, default CI-V address, default control port,
 * and description.
 */
enum class WlanRadioModel(
    val id: String,
    val modelName: String,
    val defaultCivAddress: Int,
    val defaultPort: Int = 50001,
    val description: String = "",
) {
    IC705(
        id = "IC-705",
        modelName = "Icom IC-705",
        defaultCivAddress = Ic705CivCommands.DEFAULT_RADIO_ADDRESS, // 0xA4
        defaultPort = 50001,
        description = "Wi-Fi 直连 / 接入点全模式便携电台",
    ),
    IC9700(
        id = "IC-9700",
        modelName = "Icom IC-9700",
        defaultCivAddress = 0xA2,
        defaultPort = 50001,
        description = "以太网 LAN / Wi-Fi 全模式 VHF/UHF 基站",
    ),
    IC7610(
        id = "IC-7610",
        modelName = "Icom IC-7610",
        defaultCivAddress = 0x98,
        defaultPort = 50001,
        description = "以太网 LAN / Wi-Fi HF/50MHz 双接收基站",
    ),
    IC905(
        id = "IC-905",
        modelName = "Icom IC-905",
        defaultCivAddress = 0xAC,
        defaultPort = 50001,
        description = "以太网 LAN / Wi-Fi 微波全模式电台",
    ),
    CUSTOM(
        id = "CUSTOM",
        modelName = "自定义 / 其他 (Custom)",
        defaultCivAddress = Ic705CivCommands.DEFAULT_RADIO_ADDRESS, // 0xA4
        defaultPort = 50001,
        description = "自定义 CI-V 地址与网络端点",
    );

    val defaultCivHex: String
        get() = Integer.toHexString(defaultCivAddress).uppercase()

    companion object {
        val ALL: List<WlanRadioModel> = listOf(IC705, IC9700, IC7610, IC905, CUSTOM)

        fun findById(id: String?): WlanRadioModel {
            if (id.isNullOrBlank()) return IC705
            return ALL.firstOrNull {
                it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true)
            } ?: IC705
        }

        fun parseCivAddress(hexString: String?, defaultAddress: Int = Ic705CivCommands.DEFAULT_RADIO_ADDRESS): Int {
            if (hexString.isNullOrBlank()) return defaultAddress
            val clean = hexString.trim()
                .removePrefix("0x")
                .removePrefix("0X")
                .removeSuffix("h")
                .removeSuffix("H")
                .trim()
            val parsed = clean.toIntOrNull(16) ?: return defaultAddress
            return if (parsed in 1..0xEF) parsed else defaultAddress
        }

        fun formatCivAddress(address: Int): String {
            return Integer.toHexString(address and 0xFF).uppercase()
        }
    }
}
