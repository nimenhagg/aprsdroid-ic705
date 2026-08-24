package org.aprsdroid.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AboutDialog(private val context: Context) {
    fun show() {
        MaterialAlertDialogBuilder(context)
            .setTitle("APRSdroid IC-705")
            .setMessage(
                "APRSdroid IC-705 Edition\n" +
                "${context.getString(R.string.build_revision)}\n\n" +
                "专为 ICOM IC-705 与现代无线电爱好者深度定制。\n\n" +
                "• IC-705 Wi-Fi 原生直连通信协议栈\n" +
                "• Jetpack Compose Material 3 现代化界面\n" +
                "• 多源矢量与瓦片地图支持\n" +
                "• 挂号信短消息机制与送达回执追踪\n\n" +
                "遵循 GNU GPLv2 开源协议。\n" +
                "致谢所有为 APRS 生态做出贡献的 HAM 朋友！"
            )
            .setPositiveButton("好的", null)
            .setNeutralButton("开源主页") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/nimenhagg/aprsdroid-ic705"))
                context.startActivity(intent)
            }
            .show()
    }
}
