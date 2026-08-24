package org.aprsdroid.app


import android.Manifest
import android.os.Build
import net.ab0oo.aprs.parser.APRSPacket
import org.aprsdroid.app.backend.Ic705WifiBackend
import java.io.InputStream
import java.io.OutputStream

abstract class AprsBackend(@JvmField val prefs: PrefsWrapper) {
    @JvmField
    val login: String = prefs.getLoginString()

    abstract fun start(): Boolean
    abstract fun update(packet: APRSPacket): String
    abstract fun stop()

    companion object {
        const val DEFAULT_CONNTYPE = "tcp"
        const val DEFAULT_LINK = "tcpip"
        const val DEFAULT_PROTO = "aprsis"

        const val PASSCODE_NONE = 0
        const val PASSCODE_OPTIONAL = 1
        const val PASSCODE_REQUIRED = 2

        private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

        const val CAN_RECEIVE = 1
        const val CAN_XMIT = 2
        const val CAN_DUPLEX = 3

        @JvmField
        val BLUETOOTH_PERMISSION: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            @Suppress("DEPRECATION")
            Manifest.permission.BLUETOOTH_ADMIN
        }

        class BackendInfo(
            @JvmField val create: (AprsService, PrefsWrapper) -> AprsBackend,
            @JvmField val prefxml: Int,
            @JvmField val permissions: Set<String>,
            @JvmField val duplex: Int,
            @JvmField val need_passcode: Int
        )

        class ProtoInfo(
            @JvmField val create: ((AprsService, InputStream, OutputStream) -> TncProto)?,
            @JvmField val prefxml: Int,
            @JvmField val link: String?
        ) {
            fun link(): String? = link
        }

        @JvmField
        val backend_upgrade = mapOf(
            "tcp" to "aprsis-tcpip-tcp",
            "udp" to "aprsis-tcpip-udp",
            "http" to "aprsis-tcpip-http",
            "afsk" to "afsk-bluetooth-tcp",
            "bluetooth" to "kiss-bluetooth-tcp",
            "kenwood" to "kenwood-bluetooth-tcp",
            "tcptnc" to "kiss-tcpip-tcp",
            "usb" to "kiss-usb-tcp"
        )

        @JvmField
        val backend_collection = mapOf(
            "udp" to BackendInfo({ _, p -> UdpUploader(p) }, R.xml.backend_udp, emptySet(), CAN_XMIT, PASSCODE_REQUIRED),
            "http" to BackendInfo({ _, p -> HttpPostUploader(p) }, R.xml.backend_http, emptySet(), CAN_XMIT, PASSCODE_REQUIRED),
            "afsk" to BackendInfo({ s, p -> AfskUploader(s, p) }, 0, setOf(Manifest.permission.RECORD_AUDIO), CAN_DUPLEX, PASSCODE_NONE),
            "ic705" to BackendInfo({ s, p -> Ic705WifiBackend(s, p) }, 0, emptySet(), CAN_DUPLEX, PASSCODE_NONE),
            "tcp" to BackendInfo({ s, p -> TcpUploader(s, p) }, R.xml.backend_tcp, emptySet(), CAN_DUPLEX, PASSCODE_OPTIONAL),
            "bluetooth" to BackendInfo({ s, p -> BluetoothTnc(s, p) }, R.xml.backend_bluetooth, setOf(BLUETOOTH_PERMISSION), CAN_DUPLEX, PASSCODE_NONE),
            "tcpip" to BackendInfo({ s, p -> TcpUploader(s, p) }, R.xml.backend_tcptnc, emptySet(), CAN_DUPLEX, PASSCODE_NONE),
            "usb" to BackendInfo({ s, p -> UsbTnc(s, p) }, R.xml.backend_usb, emptySet(), CAN_DUPLEX, PASSCODE_NONE)
        )

        @JvmField
        val proto_collection = mapOf(
            "aprsis" to ProtoInfo({ s, isStream, osStream -> AprsIsProto(s, isStream, osStream) }, R.xml.proto_aprsis, "aprsis"),
            "afsk" to ProtoInfo(null, R.xml.proto_afsk, null),
            "ic705" to ProtoInfo(null, R.xml.proto_ic705, null),
            "kiss" to ProtoInfo({ s, isStream, osStream -> KissProto(s, isStream, osStream) }, R.xml.proto_kiss, "link"),
            "tnc2" to ProtoInfo({ _, isStream, osStream -> Tnc2Proto(isStream, osStream) }, R.xml.proto_tnc2, "link"),
            "kenwood" to ProtoInfo({ s, isStream, osStream -> KenwoodProto(s, isStream, osStream) }, R.xml.proto_kenwood, "link")
        )

        @JvmStatic
        fun DEFAULT_CONNTYPE(): String = DEFAULT_CONNTYPE

        @JvmStatic
        fun DEFAULT_LINK(): String = DEFAULT_LINK

        @JvmStatic
        fun DEFAULT_PROTO(): String = DEFAULT_PROTO

        @JvmStatic
        fun defaultProtoInfo(p: String): ProtoInfo {
            return proto_collection[p] ?: proto_collection["aprsis"]!!
        }

        @JvmStatic
        fun defaultProtoInfo(prefs: PrefsWrapper): ProtoInfo = defaultProtoInfo(prefs.getProto())

        @JvmStatic
        fun defaultBackendInfo(prefs: PrefsWrapper): BackendInfo {
            val pi = defaultProtoInfo(prefs)
            return backend_collection[defaultBackendKey(prefs, pi)] ?: backend_collection[DEFAULT_CONNTYPE]!!
        }

        @JvmStatic
        fun defaultBackendPermissions(prefs: PrefsWrapper): Set<String> {
            val perms = mutableSetOf<String>()
            perms.addAll(defaultBackendInfo(prefs).permissions)
            val backendKey = defaultBackendKey(prefs, defaultProtoInfo(prefs))
            if (requiresLocalNetworkPermission(backendKey, Build.VERSION.SDK_INT)) {
                perms.add(LOCAL_NETWORK_PERMISSION)
            }
            if (prefs.getProto() == "kenwood" && prefs.getBoolean("kenwood.gps", false)) {
                perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
                perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            return perms
        }

        private fun defaultBackendKey(prefs: PrefsWrapper, protoInfo: ProtoInfo): String {
            return if (protoInfo.link != null) {
                prefs.getString(protoInfo.link, DEFAULT_LINK)
            } else {
                prefs.getProto()
            }
        }

        internal fun requiresLocalNetworkPermission(backendKey: String, sdkInt: Int): Boolean {
            return sdkInt >= Build.VERSION_CODES.CINNAMON_BUN &&
                (backendKey == "ic705" || backendKey == "tcpip")
        }

        @JvmStatic
        fun instanciateUploader(service: AprsService, prefs: PrefsWrapper): AprsBackend {
            return defaultBackendInfo(prefs).create(service, prefs)
        }

        @JvmStatic
        fun instanciateProto(service: AprsService, isStream: InputStream, osStream: OutputStream): TncProto {
            val creator = defaultProtoInfo(service.prefs).create
                ?: throw IllegalArgumentException("No protocol handler for " + service.prefs.getProto())
            return creator(service, isStream, osStream)
        }

        @JvmStatic
        fun prefxml_proto(prefs: PrefsWrapper): Int = defaultProtoInfo(prefs).prefxml

        @JvmStatic
        fun prefxml_backend(prefs: PrefsWrapper): Int = defaultBackendInfo(prefs).prefxml
    }
}
