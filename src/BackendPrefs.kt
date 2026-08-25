package org.aprsdroid.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.ic705.diagnostic.Ic705RxDiagnosticActivity
import org.aprsdroid.app.ui.component.PreferenceCategoryHeader
import org.aprsdroid.app.ui.component.PreferenceEditDialog
import org.aprsdroid.app.ui.component.PreferenceGroupCard
import org.aprsdroid.app.ui.component.PreferenceItem
import org.aprsdroid.app.ui.component.PreferenceSelectDialog
import org.aprsdroid.app.ui.component.PreferenceSwitchItem
import org.aprsdroid.app.ui.component.PreferenceValueItem
import org.aprsdroid.app.ui.theme.AprsTheme

class BackendPrefs : ComponentActivity(), PermissionHelper {

    companion object {
        const val BACKEND_PERMISSION = 1000
        const val REQUEST_GPS = 1010
    }

    val prefs: PrefsWrapper by lazy { PrefsWrapper(this) }
    override var pendingPermissionAction: Int? = null
    override var pendingPermissions: Set<String> = emptySet()
    override val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> handlePermissionResult(grants) }

    private val protoState = mutableStateOf("aprsis")
    private val aprsisLinkState = mutableStateOf("tcp")
    private val tncLinkState = mutableStateOf("bluetooth")

    // APRS-IS TCP
    private val tcpServerState = mutableStateOf("china.aprs2.net")
    private val tcpPortState = mutableStateOf("14580")
    private val tcpFilterState = mutableStateOf("")
    private val tcpFilterDistState = mutableStateOf("50")

    // IC-705
    private val ic705AddressState = mutableStateOf("192.168.59.1")
    private val ic705PortState = mutableStateOf("50001")
    private val ic705UsernameState = mutableStateOf("ic705")
    private val ic705PasswordState = mutableStateOf("")

    // AFSK
    private val afskBtScoState = mutableStateOf(false)

    // TCP TNC
    private val tcptncHostState = mutableStateOf("127.0.0.1")
    private val tcptncPortState = mutableStateOf("8001")

    // Dialog state
    private val editDialogKey = mutableStateOf<String?>(null)
    private val showProtoDialog = mutableStateOf(false)
    private val showAprsIsLinkDialog = mutableStateOf(false)
    private val showTncLinkDialog = mutableStateOf(false)

    private fun refreshState() {
        protoState.value = prefs.getString("proto", "aprsis")
        aprsisLinkState.value = prefs.getString("aprsis", "tcp")
        tncLinkState.value = prefs.getString("link", "bluetooth")

        tcpServerState.value = prefs.getString("tcp.server", "china.aprs2.net")
        tcpPortState.value = prefs.getString("tcp.port", "14580")
        tcpFilterState.value = prefs.getString("tcp.filter", "")
        tcpFilterDistState.value = prefs.getString("tcp.filterdist", "50")

        ic705AddressState.value = prefs.getString("ic705.address", "192.168.59.1")
        ic705PortState.value = prefs.getString("ic705.control_port", "50001")
        ic705UsernameState.value = prefs.getString("ic705.username", "ic705")
        ic705PasswordState.value = prefs.getString("ic705.password", "")

        afskBtScoState.value = prefs.getBoolean("afsk.btsco", false)

        tcptncHostState.value = prefs.getString("tcptnc.server", "127.0.0.1")
        tcptncPortState.value = prefs.getString("tcptnc.port", "8001")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restorePermissionState(savedInstanceState)
        refreshState()

        setContent {
            AprsTheme {
                val protoOptions = remember {
                    listOf(
                        "aprsis" to "互联网 APRS-IS",
                        "ic705" to "IC-705 Wi-Fi 直连",
                        "afsk" to "音频调制解调 (AFSK)",
                        "kiss" to "KISS TNC (蓝牙 / TCP / USB)",
                        "kenwood" to "Kenwood / 健伍对讲机",
                        "tnc2" to "TNC2 文本终端"
                    )
                }

                val aprsIsLinkOptions = remember {
                    listOf(
                        "tcp" to "TCP 实时长连接 (双向)",
                        "udp" to "UDP 数据报 (单向发送)",
                        "http" to "HTTP Web POST"
                    )
                }

                val tncLinkOptions = remember {
                    listOf(
                        "bluetooth" to "蓝牙 SPP 连接",
                        "tcpip" to "TCP/IP 网络 KISS TNC",
                        "usb" to "USB OTG 串口"
                    )
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.p__connection),
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(android.R.string.cancel)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // 1. 连接协议选择
                        PreferenceCategoryHeader(title = stringResource(R.string.p_conntype))
                        PreferenceGroupCard {
                            val protoTitle = protoOptions.find { it.first == protoState.value }?.second ?: protoState.value
                            PreferenceValueItem(
                                title = stringResource(R.string.p_conntype),
                                value = protoTitle,
                                summary = stringResource(R.string.p_conntype_entry),
                                icon = Icons.Default.CellTower,
                                onClick = { showProtoDialog.value = true }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 2. 针对各协议的专属配置
                        when (protoState.value) {
                            "aprsis" -> {
                                PreferenceCategoryHeader(title = "APRS-IS 连接参数")
                                PreferenceGroupCard {
                                    val linkTitle = aprsIsLinkOptions.find { it.first == aprsisLinkState.value }?.second ?: aprsisLinkState.value
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_link),
                                        value = linkTitle,
                                        summary = stringResource(R.string.p_link_entry),
                                        icon = Icons.Default.SettingsEthernet,
                                        onClick = { showAprsIsLinkDialog.value = true }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_host),
                                        value = tcpServerState.value,
                                        summary = stringResource(R.string.p_host_summary),
                                        icon = Icons.Default.Router,
                                        onClick = { editDialogKey.value = "tcp.server" }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_port),
                                        value = tcpPortState.value,
                                        summary = "APRS-IS 服务器端口 (默认 14580)",
                                        icon = Icons.Default.Router,
                                        onClick = { editDialogKey.value = "tcp.port" }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceItem(
                                        title = stringResource(R.string.p_passcode),
                                        summary = if (prefs.getPasscode().isEmpty()) "未配置 (以只读访客连接)" else "已配置校验码",
                                        icon = Icons.Default.Password,
                                        onClick = { PasscodeDialog(this@BackendPrefs, false).show() }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_filter),
                                        value = tcpFilterState.value.ifEmpty { "无自定义过滤" },
                                        summary = stringResource(R.string.p_filter_summary),
                                        icon = Icons.Default.FilterAlt,
                                        onClick = { editDialogKey.value = "tcp.filter" }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_filterdist),
                                        value = "${tcpFilterDistState.value} km",
                                        summary = stringResource(R.string.p_filterdist_summary),
                                        icon = Icons.Default.FilterAlt,
                                        onClick = { editDialogKey.value = "tcp.filterdist" }
                                    )
                                }
                            }
                            "ic705" -> {
                                PreferenceCategoryHeader(title = "IC-705 Wi-Fi 参数与控制")
                                PreferenceGroupCard {
                                    PreferenceValueItem(
                                        title = stringResource(R.string.ic705_rx_address),
                                        value = ic705AddressState.value,
                                        summary = "IC-705 电台机身局域网 IP 地址",
                                        icon = Icons.Default.Wifi,
                                        onClick = { editDialogKey.value = "ic705.address" }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.ic705_rx_port),
                                        value = ic705PortState.value,
                                        summary = "控制 UDP 端口 (默认 50001)",
                                        icon = Icons.Default.Router,
                                        onClick = { editDialogKey.value = "ic705.control_port" }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.ic705_rx_username),
                                        value = ic705UsernameState.value.ifEmpty { "未设置" },
                                        summary = "电台网络设置中的用户名",
                                        icon = Icons.Default.Radio,
                                        onClick = { editDialogKey.value = "ic705.username" }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.ic705_rx_password),
                                        value = if (ic705PasswordState.value.isEmpty()) "未设置" else "••••••",
                                        summary = "电台网络设置中的连接密码",
                                        icon = Icons.Default.Password,
                                        onClick = { editDialogKey.value = "ic705.password" }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceItem(
                                        title = "启动 IC-705 Wi-Fi 实时诊断与监控",
                                        summary = "监控实时 UDP 握手、PCM 采样流与 AX.25 帧解码",
                                        icon = Icons.Default.Wifi,
                                        onClick = {
                                            startActivity(Intent(this@BackendPrefs, Ic705RxDiagnosticActivity::class.java))
                                        }
                                    )
                                }
                            }
                            "afsk" -> {
                                PreferenceCategoryHeader(title = "音频 AFSK 参数")
                                PreferenceGroupCard {
                                    PreferenceSwitchItem(
                                        title = "蓝牙 SCO 音频路由",
                                        summary = "使用蓝牙免提耳麦的麦克风和扬声器进行 AFSK 调制解调",
                                        icon = Icons.Default.Bluetooth,
                                        checked = afskBtScoState.value,
                                        onCheckedChange = { checked ->
                                            afskBtScoState.value = checked
                                            prefs.set("afsk.btsco", checked)
                                        }
                                    )
                                }
                            }
                            "kiss", "kenwood", "tnc2" -> {
                                PreferenceCategoryHeader(title = "TNC 链路连接")
                                PreferenceGroupCard {
                                    val tncTitle = tncLinkOptions.find { it.first == tncLinkState.value }?.second ?: tncLinkState.value
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_link),
                                        value = tncTitle,
                                        summary = stringResource(R.string.p_link_entry),
                                        icon = when (tncLinkState.value) {
                                            "bluetooth" -> Icons.Default.Bluetooth
                                            "usb" -> Icons.Default.Usb
                                            else -> Icons.Default.SettingsEthernet
                                        },
                                        onClick = { showTncLinkDialog.value = true }
                                    )
                                    if (tncLinkState.value == "tcpip") {
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                        PreferenceValueItem(
                                            title = stringResource(R.string.p_host),
                                            value = tcptncHostState.value,
                                            summary = "KISS TCP 服务器 IP 地址",
                                            icon = Icons.Default.Router,
                                            onClick = { editDialogKey.value = "tcptnc.server" }
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                        PreferenceValueItem(
                                            title = stringResource(R.string.p_port),
                                            value = tcptncPortState.value,
                                            summary = "KISS TCP 服务器端口",
                                            icon = Icons.Default.Router,
                                            onClick = { editDialogKey.value = "tcptnc.port" }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }

                    // Dialogs
                    if (showProtoDialog.value) {
                        PreferenceSelectDialog(
                            title = stringResource(R.string.p_conntype),
                            options = protoOptions,
                            selected = protoState.value,
                            onDismiss = { showProtoDialog.value = false },
                            onSelect = { selected ->
                                protoState.value = selected
                                prefs.set("proto", selected)
                                val perms = AprsBackend.defaultBackendPermissions(prefs)
                                if (perms.isNotEmpty()) {
                                    checkPermissions(perms.toTypedArray(), BACKEND_PERMISSION)
                                }
                            }
                        )
                    }

                    if (showAprsIsLinkDialog.value) {
                        PreferenceSelectDialog(
                            title = stringResource(R.string.p_link),
                            options = aprsIsLinkOptions,
                            selected = aprsisLinkState.value,
                            onDismiss = { showAprsIsLinkDialog.value = false },
                            onSelect = { selected ->
                                aprsisLinkState.value = selected
                                prefs.set("aprsis", selected)
                            }
                        )
                    }

                    if (showTncLinkDialog.value) {
                        PreferenceSelectDialog(
                            title = stringResource(R.string.p_link),
                            options = tncLinkOptions,
                            selected = tncLinkState.value,
                            onDismiss = { showTncLinkDialog.value = false },
                            onSelect = { selected ->
                                tncLinkState.value = selected
                                prefs.set("link", selected)
                                val perms = AprsBackend.defaultBackendPermissions(prefs)
                                if (perms.isNotEmpty()) {
                                    checkPermissions(perms.toTypedArray(), BACKEND_PERMISSION)
                                }
                            }
                        )
                    }

                    editDialogKey.value?.let { key ->
                        val currentVal = prefs.getString(key, "")
                        PreferenceEditDialog(
                            title = "修改参数",
                            initialValue = currentVal,
                            onDismiss = { editDialogKey.value = null },
                            onSave = { newValue ->
                                prefs.set(key, newValue)
                                refreshState()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun getActionName(action: Int): Int = R.string.p__connection
    override fun onAllPermissionsGranted(action: Int) {}
    override fun onPermissionsFailedCancel(action: Int) {}
}
