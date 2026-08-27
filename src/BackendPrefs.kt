package org.aprsdroid.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Usb
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import org.aprsdroid.app.ic705.diagnostic.Ic705RxDiagnosticActivity
import org.aprsdroid.app.ui.component.PasscodeDialogCompose
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

    private val tcpServerState = mutableStateOf("china.aprs2.net")
    private val tcpFilterState = mutableStateOf("")
    private val tcpFilterDistState = mutableStateOf("50")
    private val tcpSoTimeoutState = mutableStateOf("120")
    private val udpServerState = mutableStateOf("srvr.aprs-is.net")
    private val httpServerState = mutableStateOf("srvr.aprs-is.net")

    private val ic705AddressState = mutableStateOf("192.168.59.1")
    private val ic705PortState = mutableStateOf("50001")
    private val ic705UsernameState = mutableStateOf("ic705")
    private val ic705PasswordState = mutableStateOf("")

    private val afskBtScoState = mutableStateOf(false)
    private val afskOutputState = mutableStateOf("0")
    private val afskPrefixState = mutableStateOf("200")

    private val btClientState = mutableStateOf(true)
    private val btMacState = mutableStateOf("")
    private val btChannelState = mutableStateOf("-1")
    private val baudRateState = mutableStateOf("115200")

    private val kissInitState = mutableStateOf("")
    private val kissDelayState = mutableStateOf("300")
    private val kenwoodGpsState = mutableStateOf(false)
    private val kenwoodGpsDebugState = mutableStateOf(false)

    private val editDialogKey = mutableStateOf<String?>(null)
    private val showProtoDialog = mutableStateOf(false)
    private val showAprsIsLinkDialog = mutableStateOf(false)
    private val showTncLinkDialog = mutableStateOf(false)
    private val showBluetoothDeviceDialog = mutableStateOf(false)
    private val showBaudRateDialog = mutableStateOf(false)
    private val showAfskOutputDialog = mutableStateOf(false)
    private val showPasscodeDialog = mutableStateOf(false)

    private fun refreshState() {
        protoState.value = prefs.getString("proto", "aprsis")
        aprsisLinkState.value = prefs.getString("aprsis", "tcp")
        tncLinkState.value = prefs.getString("link", "bluetooth")

        tcpServerState.value = prefs.getString("tcp.server", "china.aprs2.net")
        tcpFilterState.value = prefs.getString("tcp.filter", "")
        tcpFilterDistState.value = prefs.getString("tcp.filterdist", "50")
        tcpSoTimeoutState.value = prefs.getString("tcp.sotimeout", "120")
        udpServerState.value = prefs.getString("udp.server", "srvr.aprs-is.net")
        httpServerState.value = prefs.getString("http.server", "srvr.aprs-is.net")

        ic705AddressState.value = prefs.getString("ic705.address", "192.168.59.1")
        ic705PortState.value = prefs.getString("ic705.control_port", "50001")
        ic705UsernameState.value = prefs.getString("ic705.username", "ic705")
        ic705PasswordState.value = prefs.getString("ic705.password", "")

        afskBtScoState.value = prefs.getBoolean("afsk.btsco", false)
        afskOutputState.value = prefs.getString("afsk.output", "0")
        afskPrefixState.value = prefs.getString("afsk.prefix", "200")

        btClientState.value = prefs.getBoolean("bt.client", true)
        btMacState.value = prefs.getString("bt.mac", "")
        btChannelState.value = prefs.getString("bt.channel", "-1")
        baudRateState.value = prefs.getString("baudrate", "115200")

        kissInitState.value = prefs.getString("kiss.init", "")
        kissDelayState.value = prefs.getString("kiss.delay", "300")
        kenwoodGpsState.value = prefs.getBoolean("kenwood.gps", false)
        kenwoodGpsDebugState.value = prefs.getBoolean("kenwood.gps_debug", false)
    }

    private fun hasBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, AprsBackend.BLUETOOTH_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun bondedBluetoothDevices(): List<Pair<String, String>> {
        if (!hasBluetoothPermission()) return emptyList()
        return try {
            val manager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
            val devices = manager?.adapter?.bondedDevices.orEmpty()
            devices
                .sortedWith(compareBy({ it.name?.lowercase().orEmpty() }, { it.address }))
                .map { device ->
                    val address = device.address.orEmpty()
                    val label = device.name?.takeIf { it.isNotBlank() }
                        ?.let { "$it · $address" }
                        ?: address
                    address to label
                }
                .filter { it.first.isNotBlank() }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun requestCurrentBackendPermissions() {
        val permissions = AprsBackend.defaultBackendPermissions(prefs)
        if (permissions.isNotEmpty()) {
            checkPermissions(permissions.toTypedArray(), BACKEND_PERMISSION)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restorePermissionState(savedInstanceState)
        refreshState()

        setContent {
            AprsTheme {
                val protoOptions = listOf(
                    "aprsis" to stringResource(R.string.setting_proto_aprsis),
                    "ic705" to stringResource(R.string.setting_proto_ic705),
                    "afsk" to stringResource(R.string.setting_proto_afsk),
                    "kiss" to stringResource(R.string.setting_proto_kiss),
                    "kenwood" to stringResource(R.string.setting_proto_kenwood),
                    "tnc2" to stringResource(R.string.setting_proto_tnc2),
                )
                val aprsIsLinkOptions = listOf(
                    "tcp" to stringResource(R.string.setting_aprsis_tcp),
                    "udp" to stringResource(R.string.setting_aprsis_udp),
                    "http" to stringResource(R.string.setting_aprsis_http),
                )
                val tncLinkOptions = listOf(
                    "bluetooth" to stringResource(R.string.setting_tnc_bluetooth),
                    "tcpip" to stringResource(R.string.setting_tnc_tcp),
                    "usb" to stringResource(R.string.setting_tnc_usb),
                )
                val baudRates = stringArrayResource(R.array.p_serial_baudrates)
                val baudRateOptions = baudRates.map { it to it }
                val afskOutputValues = stringArrayResource(R.array.p_afsk_out_ev)
                val afskOutputLabels = stringArrayResource(R.array.p_afsk_out_e)
                val afskOutputOptions = afskOutputValues.zip(afskOutputLabels)
                val bluetoothDevices = bondedBluetoothDevices()

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.p__connection),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(android.R.string.cancel),
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    },
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        PreferenceCategoryHeader(title = stringResource(R.string.setting_connection_mode))
                        PreferenceGroupCard {
                            val protoTitle = protoOptions.firstOrNull { it.first == protoState.value }
                                ?.second ?: protoState.value
                            PreferenceValueItem(
                                title = stringResource(R.string.setting_connection_mode),
                                value = protoTitle,
                                summary = stringResource(R.string.setting_connection_mode_summary),
                                icon = Icons.Default.CellTower,
                                onClick = { showProtoDialog.value = true },
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        when (protoState.value) {
                            "aprsis" -> {
                                PreferenceCategoryHeader(title = stringResource(R.string.setting_aprsis_category))
                                PreferenceGroupCard {
                                    val linkTitle = aprsIsLinkOptions
                                        .firstOrNull { it.first == aprsisLinkState.value }
                                        ?.second ?: aprsisLinkState.value
                                    PreferenceValueItem(
                                        title = stringResource(R.string.setting_aprsis_transport),
                                        value = linkTitle,
                                        summary = stringResource(R.string.setting_aprsis_transport_summary),
                                        icon = Icons.Default.SettingsEthernet,
                                        onClick = { showAprsIsLinkDialog.value = true },
                                    )

                                    when (aprsisLinkState.value) {
                                        "tcp" -> {
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                            PreferenceValueItem(
                                                title = stringResource(R.string.setting_server),
                                                value = tcpServerState.value,
                                                summary = stringResource(R.string.p_tcp_server_summary),
                                                icon = Icons.Default.Router,
                                                onClick = { editDialogKey.value = "tcp.server" },
                                            )
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                            PreferenceValueItem(
                                                title = stringResource(R.string.p_sotimeout),
                                                value = "${tcpSoTimeoutState.value} s",
                                                summary = stringResource(R.string.p_sotimeout_summary),
                                                icon = Icons.Default.Router,
                                                onClick = { editDialogKey.value = "tcp.sotimeout" },
                                            )
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                            PreferenceValueItem(
                                                title = stringResource(R.string.setting_filter),
                                                value = tcpFilterState.value.ifEmpty {
                                                    stringResource(R.string.setting_filter_none)
                                                },
                                                summary = stringResource(R.string.setting_filter_summary),
                                                icon = Icons.Default.FilterAlt,
                                                onClick = { editDialogKey.value = "tcp.filter" },
                                            )
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                            PreferenceValueItem(
                                                title = stringResource(R.string.setting_filter_radius),
                                                value = "${tcpFilterDistState.value} km",
                                                summary = stringResource(R.string.setting_filter_radius_summary),
                                                icon = Icons.Default.FilterAlt,
                                                onClick = { editDialogKey.value = "tcp.filterdist" },
                                            )
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                            PreferenceItem(
                                                title = stringResource(R.string.p_filterhelp),
                                                summary = stringResource(R.string.p_filterhelp_summary),
                                                icon = Icons.Default.FilterAlt,
                                                onClick = {
                                                    UrlOpener.open(this@BackendPrefs, "https://www.aprs-is.net/javAPRSFilter.aspx")
                                                },
                                            )
                                        }

                                        "udp" -> {
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                            PreferenceValueItem(
                                                title = stringResource(R.string.setting_server),
                                                value = udpServerState.value,
                                                summary = stringResource(R.string.p_host_summary),
                                                icon = Icons.Default.Router,
                                                onClick = { editDialogKey.value = "udp.server" },
                                            )
                                        }

                                        "http" -> {
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                            PreferenceValueItem(
                                                title = stringResource(R.string.setting_server),
                                                value = httpServerState.value,
                                                summary = stringResource(R.string.p_host_summary),
                                                icon = Icons.Default.Router,
                                                onClick = { editDialogKey.value = "http.server" },
                                            )
                                        }
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceItem(
                                        title = stringResource(R.string.setting_passcode),
                                        summary = if (prefs.getString("passcode", "").isEmpty()) {
                                            stringResource(R.string.setting_passcode_missing)
                                        } else {
                                            stringResource(R.string.setting_passcode_configured)
                                        },
                                        icon = Icons.Default.Password,
                                        onClick = { showPasscodeDialog.value = true },
                                    )
                                }
                            }

                            "ic705" -> {
                                PreferenceCategoryHeader(title = stringResource(R.string.setting_ic705_category))
                                PreferenceGroupCard {
                                    PreferenceValueItem(
                                        title = stringResource(R.string.ic705_rx_address),
                                        value = ic705AddressState.value,
                                        summary = stringResource(R.string.setting_ic705_address_summary),
                                        icon = Icons.Default.Wifi,
                                        onClick = { editDialogKey.value = "ic705.address" },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.ic705_rx_port),
                                        value = ic705PortState.value,
                                        summary = stringResource(R.string.setting_ic705_port_summary),
                                        icon = Icons.Default.Router,
                                        onClick = { editDialogKey.value = "ic705.control_port" },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.ic705_rx_username),
                                        value = ic705UsernameState.value.ifEmpty {
                                            stringResource(R.string.setting_not_set)
                                        },
                                        summary = stringResource(R.string.setting_ic705_username_summary),
                                        icon = Icons.Default.Radio,
                                        onClick = { editDialogKey.value = "ic705.username" },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.ic705_rx_password),
                                        value = if (ic705PasswordState.value.isEmpty()) {
                                            stringResource(R.string.setting_not_set)
                                        } else {
                                            "••••••"
                                        },
                                        summary = stringResource(R.string.setting_ic705_password_summary),
                                        icon = Icons.Default.Password,
                                        onClick = { editDialogKey.value = "ic705.password" },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceItem(
                                        title = stringResource(R.string.setting_ic705_diagnostics),
                                        summary = stringResource(R.string.setting_ic705_diagnostics_summary),
                                        icon = Icons.Default.Wifi,
                                        onClick = {
                                            startActivity(
                                                Intent(
                                                    this@BackendPrefs,
                                                    Ic705RxDiagnosticActivity::class.java,
                                                ),
                                            )
                                        },
                                    )
                                }
                            }

                            "afsk" -> {
                                PreferenceCategoryHeader(title = stringResource(R.string.p_conn_afsk))
                                PreferenceGroupCard {
                                    PreferenceSwitchItem(
                                        title = stringResource(R.string.p_afsk_btsco),
                                        summary = stringResource(R.string.p_afsk_btsco_summary),
                                        icon = Icons.Default.Bluetooth,
                                        checked = afskBtScoState.value,
                                        onCheckedChange = { checked ->
                                            afskBtScoState.value = checked
                                            prefs.set("afsk.btsco", checked)
                                        },
                                    )
                                    if (!afskBtScoState.value) {
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                        PreferenceValueItem(
                                            title = stringResource(R.string.p_afsk_output),
                                            value = afskOutputOptions
                                                .firstOrNull { it.first == afskOutputState.value }
                                                ?.second ?: afskOutputState.value,
                                            icon = Icons.Default.Radio,
                                            onClick = { showAfskOutputDialog.value = true },
                                        )
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_afsk_prefix),
                                        value = "${afskPrefixState.value} ms",
                                        summary = stringResource(R.string.p_afsk_prefix_summary),
                                        icon = Icons.Default.Radio,
                                        onClick = { editDialogKey.value = "afsk.prefix" },
                                    )
                                }
                            }

                            "kiss", "kenwood", "tnc2" -> {
                                if (protoState.value == "kiss") {
                                    PreferenceCategoryHeader(title = stringResource(R.string.p_conn_kiss))
                                    PreferenceGroupCard {
                                        PreferenceValueItem(
                                            title = stringResource(R.string.p_tnc_init),
                                            value = kissInitState.value.ifEmpty { stringResource(R.string.setting_not_set) },
                                            summary = stringResource(R.string.p_tnc_init_summary),
                                            icon = Icons.Default.Radio,
                                            onClick = { editDialogKey.value = "kiss.init" },
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                        PreferenceValueItem(
                                            title = stringResource(R.string.p_tnc_delay),
                                            value = "${kissDelayState.value} ms",
                                            summary = stringResource(R.string.p_tnc_delay_summary),
                                            icon = Icons.Default.Radio,
                                            onClick = { editDialogKey.value = "kiss.delay" },
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                if (protoState.value == "kenwood") {
                                    PreferenceCategoryHeader(title = stringResource(R.string.p_conn_kwd))
                                    PreferenceGroupCard {
                                        PreferenceItem(
                                            title = stringResource(R.string.p_conn_kwd_info),
                                            icon = Icons.Default.Radio,
                                            onClick = {
                                                UrlOpener.open(this@BackendPrefs, getString(R.string.kwd_help_url))
                                            },
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                        PreferenceSwitchItem(
                                            title = stringResource(R.string.p_conn_kwd_gps),
                                            summary = stringResource(R.string.p_conn_kwd_gps_summary),
                                            icon = Icons.Default.Radio,
                                            checked = kenwoodGpsState.value,
                                            onCheckedChange = { checked ->
                                                kenwoodGpsState.value = checked
                                                prefs.set("kenwood.gps", checked)
                                                if (checked) requestCurrentBackendPermissions()
                                            },
                                        )
                                        if (kenwoodGpsState.value) {
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                            PreferenceSwitchItem(
                                                title = stringResource(R.string.p_conn_kwd_gps_debug),
                                                summary = stringResource(R.string.p_conn_kwd_gps_debug_summary),
                                                icon = Icons.Default.Radio,
                                                checked = kenwoodGpsDebugState.value,
                                                onCheckedChange = { checked ->
                                                    kenwoodGpsDebugState.value = checked
                                                    prefs.set("kenwood.gps_debug", checked)
                                                },
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                PreferenceCategoryHeader(title = stringResource(R.string.setting_tnc_category))
                                PreferenceGroupCard {
                                    val tncTitle = tncLinkOptions.firstOrNull { it.first == tncLinkState.value }
                                        ?.second ?: tncLinkState.value
                                    PreferenceValueItem(
                                        title = stringResource(R.string.setting_tnc_transport),
                                        value = tncTitle,
                                        summary = stringResource(R.string.setting_tnc_transport_summary),
                                        icon = when (tncLinkState.value) {
                                            "bluetooth" -> Icons.Default.Bluetooth
                                            "usb" -> Icons.Default.Usb
                                            else -> Icons.Default.SettingsEthernet
                                        },
                                        onClick = { showTncLinkDialog.value = true },
                                    )

                                    when (tncLinkState.value) {
                                        "tcpip" -> {
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                            PreferenceValueItem(
                                                title = stringResource(R.string.setting_server),
                                                value = tcpServerState.value,
                                                summary = stringResource(R.string.p_tcptnc_server_summary),
                                                icon = Icons.Default.Router,
                                                onClick = { editDialogKey.value = "tcp.server" },
                                            )
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                            PreferenceValueItem(
                                                title = stringResource(R.string.p_sotimeout),
                                                value = "${tcpSoTimeoutState.value} s",
                                                summary = stringResource(R.string.p_sotimeout_summary),
                                                icon = Icons.Default.Router,
                                                onClick = { editDialogKey.value = "tcp.sotimeout" },
                                            )
                                        }

                                        "bluetooth" -> {
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                            PreferenceSwitchItem(
                                                title = stringResource(R.string.p_bt_client),
                                                summary = stringResource(R.string.p_bt_client_summary),
                                                icon = Icons.Default.Bluetooth,
                                                checked = btClientState.value,
                                                onCheckedChange = { checked ->
                                                    btClientState.value = checked
                                                    prefs.set("bt.client", checked)
                                                },
                                            )
                                            if (btClientState.value) {
                                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                                PreferenceValueItem(
                                                    title = stringResource(R.string.p_bt_tnc_device),
                                                    value = bluetoothDevices
                                                        .firstOrNull { it.first == btMacState.value }
                                                        ?.second
                                                        ?: btMacState.value.ifEmpty {
                                                            stringResource(R.string.setting_not_set)
                                                        },
                                                    summary = stringResource(R.string.p_bt_tnc_device_summary),
                                                    icon = Icons.Default.Bluetooth,
                                                    onClick = {
                                                        when {
                                                            !hasBluetoothPermission() -> {
                                                                checkPermissions(
                                                                    arrayOf(AprsBackend.BLUETOOTH_PERMISSION),
                                                                    BACKEND_PERMISSION,
                                                                )
                                                            }
                                                            bluetoothDevices.isNotEmpty() -> {
                                                                showBluetoothDeviceDialog.value = true
                                                            }
                                                            else -> {
                                                                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                                            }
                                                        }
                                                    },
                                                )
                                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                                PreferenceValueItem(
                                                    title = stringResource(R.string.p_bt_channel),
                                                    value = btChannelState.value,
                                                    summary = stringResource(R.string.p_bt_channel_summary),
                                                    icon = Icons.Default.Bluetooth,
                                                    onClick = { editDialogKey.value = "bt.channel" },
                                                )
                                            }
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                            PreferenceItem(
                                                title = stringResource(R.string.p_bt_prefs),
                                                summary = stringResource(R.string.p_bt_prefs_summary),
                                                icon = Icons.Default.Bluetooth,
                                                onClick = {
                                                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                                },
                                            )
                                        }

                                        "usb" -> {
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                            PreferenceValueItem(
                                                title = stringResource(R.string.p_serial_baudrate),
                                                value = baudRateState.value,
                                                summary = stringResource(R.string.p_serial_baudrate_summary),
                                                icon = Icons.Default.Usb,
                                                onClick = { showBaudRateDialog.value = true },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }

                    if (showProtoDialog.value) {
                        PreferenceSelectDialog(
                            title = stringResource(R.string.setting_connection_mode),
                            options = protoOptions,
                            selected = protoState.value,
                            onDismiss = { showProtoDialog.value = false },
                            onSelect = { selected ->
                                protoState.value = selected
                                prefs.set("proto", selected)
                                requestCurrentBackendPermissions()
                            },
                        )
                    }

                    if (showAprsIsLinkDialog.value) {
                        PreferenceSelectDialog(
                            title = stringResource(R.string.setting_aprsis_transport),
                            options = aprsIsLinkOptions,
                            selected = aprsisLinkState.value,
                            onDismiss = { showAprsIsLinkDialog.value = false },
                            onSelect = { selected ->
                                aprsisLinkState.value = selected
                                prefs.set("aprsis", selected)
                                refreshState()
                            },
                        )
                    }

                    if (showTncLinkDialog.value) {
                        PreferenceSelectDialog(
                            title = stringResource(R.string.setting_tnc_transport),
                            options = tncLinkOptions,
                            selected = tncLinkState.value,
                            onDismiss = { showTncLinkDialog.value = false },
                            onSelect = { selected ->
                                tncLinkState.value = selected
                                prefs.set("link", selected)
                                requestCurrentBackendPermissions()
                            },
                        )
                    }

                    if (showBluetoothDeviceDialog.value) {
                        PreferenceSelectDialog(
                            title = stringResource(R.string.p_bt_tnc_device_entry),
                            options = bluetoothDevices,
                            selected = btMacState.value,
                            onDismiss = { showBluetoothDeviceDialog.value = false },
                            onSelect = { address ->
                                btMacState.value = address
                                prefs.set("bt.mac", address)
                            },
                        )
                    }

                    if (showBaudRateDialog.value) {
                        PreferenceSelectDialog(
                            title = stringResource(R.string.p_serial_baudrate),
                            options = baudRateOptions,
                            selected = baudRateState.value,
                            onDismiss = { showBaudRateDialog.value = false },
                            onSelect = { baud ->
                                baudRateState.value = baud
                                prefs.set("baudrate", baud)
                            },
                        )
                    }

                    if (showAfskOutputDialog.value) {
                        PreferenceSelectDialog(
                            title = stringResource(R.string.p_afsk_output),
                            options = afskOutputOptions,
                            selected = afskOutputState.value,
                            onDismiss = { showAfskOutputDialog.value = false },
                            onSelect = { output ->
                                afskOutputState.value = output
                                prefs.set("afsk.output", output)
                            },
                        )
                    }

                    if (showPasscodeDialog.value) {
                        PasscodeDialogCompose(
                            initialCallsign = prefs.getCallsign(),
                            initialPasscode = prefs.getString("passcode", ""),
                            firstRun = false,
                            onDismiss = { showPasscodeDialog.value = false },
                            onSave = { call, pass ->
                                prefs.prefs.edit {
                                    putString("callsign", call)
                                    putString("passcode", pass)
                                    putBoolean("firstrun", false)
                                }
                                showPasscodeDialog.value = false
                                refreshState()
                            },
                        )
                    }

                    editDialogKey.value?.let { key ->
                        PreferenceEditDialog(
                            title = stringResource(R.string.setting_edit_value),
                            initialValue = prefs.getString(key, ""),
                            onDismiss = { editDialogKey.value = null },
                            onSave = { newValue ->
                                prefs.set(key, newValue)
                                refreshState()
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    override fun getActionName(action: Int): Int = R.string.p__connection
    override fun onAllPermissionsGranted(action: Int) {
        refreshState()
    }
    override fun onPermissionsFailedCancel(action: Int) {}
}
