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

    private val tcpServerState = mutableStateOf("china.aprs2.net")
    private val tcpPortState = mutableStateOf("14580")
    private val tcpFilterState = mutableStateOf("")
    private val tcpFilterDistState = mutableStateOf("50")

    private val ic705AddressState = mutableStateOf("192.168.59.1")
    private val ic705PortState = mutableStateOf("50001")
    private val ic705UsernameState = mutableStateOf("ic705")
    private val ic705PasswordState = mutableStateOf("")

    private val afskBtScoState = mutableStateOf(false)

    private val tcptncHostState = mutableStateOf("127.0.0.1")
    private val tcptncPortState = mutableStateOf("8001")

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
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.setting_server),
                                        value = tcpServerState.value,
                                        summary = stringResource(R.string.setting_aprsis_server_summary),
                                        icon = Icons.Default.Router,
                                        onClick = { editDialogKey.value = "tcp.server" },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.setting_server_port),
                                        value = tcpPortState.value,
                                        summary = stringResource(R.string.setting_aprsis_port_summary),
                                        icon = Icons.Default.Router,
                                        onClick = { editDialogKey.value = "tcp.port" },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceItem(
                                        title = stringResource(R.string.setting_passcode),
                                        summary = if (prefs.getPasscode().isEmpty()) {
                                            stringResource(R.string.setting_passcode_missing)
                                        } else {
                                            stringResource(R.string.setting_passcode_configured)
                                        },
                                        icon = Icons.Default.Password,
                                        onClick = { PasscodeDialog(this@BackendPrefs, false).show() },
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
                                PreferenceCategoryHeader(title = stringResource(R.string.setting_afsk_category))
                                PreferenceGroupCard {
                                    PreferenceSwitchItem(
                                        title = stringResource(R.string.setting_afsk_bluetooth_sco),
                                        summary = stringResource(R.string.setting_afsk_bluetooth_sco_summary),
                                        icon = Icons.Default.Bluetooth,
                                        checked = afskBtScoState.value,
                                        onCheckedChange = { checked ->
                                            afskBtScoState.value = checked
                                            prefs.set("afsk.btsco", checked)
                                        },
                                    )
                                }
                            }

                            "kiss", "kenwood", "tnc2" -> {
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
                                    if (tncLinkState.value == "tcpip") {
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                        PreferenceValueItem(
                                            title = stringResource(R.string.setting_server),
                                            value = tcptncHostState.value,
                                            summary = stringResource(R.string.setting_tnc_host_summary),
                                            icon = Icons.Default.Router,
                                            onClick = { editDialogKey.value = "tcptnc.server" },
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                        PreferenceValueItem(
                                            title = stringResource(R.string.setting_server_port),
                                            value = tcptncPortState.value,
                                            summary = stringResource(R.string.setting_tnc_port_summary),
                                            icon = Icons.Default.Router,
                                            onClick = { editDialogKey.value = "tcptnc.port" },
                                        )
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
                                val perms = AprsBackend.defaultBackendPermissions(prefs)
                                if (perms.isNotEmpty()) {
                                    checkPermissions(perms.toTypedArray(), BACKEND_PERMISSION)
                                }
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
                                val perms = AprsBackend.defaultBackendPermissions(prefs)
                                if (perms.isNotEmpty()) {
                                    checkPermissions(perms.toTypedArray(), BACKEND_PERMISSION)
                                }
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

    override fun getActionName(action: Int): Int = R.string.p__connection
    override fun onAllPermissionsGranted(action: Int) {}
    override fun onPermissionsFailedCancel(action: Int) {}
}
