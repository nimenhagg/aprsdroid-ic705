package org.aprsdroid.app.ui.screen

import android.media.AudioManager
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
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.R
import org.aprsdroid.app.radio.RadioAudioDevice
import org.aprsdroid.app.radio.RadioProfile
import org.aprsdroid.app.radio.WlanRadioModel
import org.aprsdroid.app.ui.component.PasscodeDialogCompose
import org.aprsdroid.app.ui.component.PreferenceCategoryHeader
import org.aprsdroid.app.ui.component.PreferenceEditDialog
import org.aprsdroid.app.ui.component.PreferenceGroupCard
import org.aprsdroid.app.ui.component.PreferenceItem
import org.aprsdroid.app.ui.component.PreferenceSelectDialog
import org.aprsdroid.app.ui.component.PreferenceSwitchItem
import org.aprsdroid.app.ui.component.PreferenceValueItem
import org.aprsdroid.app.ui.component.RadioSelectDialog
import org.aprsdroid.app.ui.viewmodel.BackendDialog
import org.aprsdroid.app.ui.viewmodel.BackendPrefsIntent
import org.aprsdroid.app.ui.viewmodel.BackendPrefsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackendPrefsScreen(
    state: BackendPrefsUiState,
    onIntent: (BackendPrefsIntent) -> Unit,
    onNavigateBack: () -> Unit,
    bondedBluetoothDevices: List<Pair<String, String>>,
    hasBluetoothPermission: Boolean,
    onRequestBluetoothPermission: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenDiagnostic: () -> Unit,
    onOpenHelpUrl: (String) -> Unit,
    audioManager: AudioManager?,
    initialCallsign: String,
    initialPasscode: String,
) {
    val protoOptions = listOf(
        "aprsis" to stringResource(R.string.setting_proto_aprsis),
        "ic705" to stringResource(R.string.setting_proto_ic705),
        "usbradio" to stringResource(R.string.setting_proto_usbradio),
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
                    IconButton(onClick = onNavigateBack) {
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
                val protoTitle = protoOptions.firstOrNull { it.first == state.proto }
                    ?.second ?: state.proto
                PreferenceValueItem(
                    title = stringResource(R.string.setting_connection_mode),
                    value = protoTitle,
                    summary = stringResource(R.string.setting_connection_mode_summary),
                    icon = Icons.Default.CellTower,
                    onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.Protocol)) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (state.proto) {
                "aprsis" -> {
                    PreferenceCategoryHeader(title = stringResource(R.string.setting_aprsis_category))
                    PreferenceGroupCard {
                        val linkTitle = aprsIsLinkOptions
                            .firstOrNull { it.first == state.aprsisLink }
                            ?.second ?: state.aprsisLink
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_aprsis_transport),
                            value = linkTitle,
                            summary = stringResource(R.string.setting_aprsis_transport_summary),
                            icon = Icons.Default.SettingsEthernet,
                            onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.AprsIsLink)) },
                        )

                        when (state.aprsisLink) {
                            "tcp" -> {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                PreferenceValueItem(
                                    title = stringResource(R.string.setting_server),
                                    value = state.tcpServer,
                                    summary = stringResource(R.string.p_tcp_server_summary),
                                    icon = Icons.Default.Router,
                                    onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("tcp.server", state.tcpServer))) },
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                PreferenceValueItem(
                                    title = stringResource(R.string.p_sotimeout),
                                    value = "${state.tcpSoTimeout} s",
                                    summary = stringResource(R.string.p_sotimeout_summary),
                                    icon = Icons.Default.Router,
                                    onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("tcp.sotimeout", state.tcpSoTimeout))) },
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                PreferenceValueItem(
                                    title = stringResource(R.string.setting_filter),
                                    value = state.tcpFilter.ifEmpty {
                                        stringResource(R.string.setting_filter_none)
                                    },
                                    summary = stringResource(R.string.setting_filter_summary),
                                    icon = Icons.Default.FilterAlt,
                                    onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("tcp.filter", state.tcpFilter))) },
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                PreferenceValueItem(
                                    title = stringResource(R.string.setting_filter_radius),
                                    value = "${state.tcpFilterDist} km",
                                    summary = stringResource(R.string.setting_filter_radius_summary),
                                    icon = Icons.Default.FilterAlt,
                                    onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("tcp.filterdist", state.tcpFilterDist))) },
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                PreferenceItem(
                                    title = stringResource(R.string.p_filterhelp),
                                    summary = stringResource(R.string.p_filterhelp_summary),
                                    icon = Icons.Default.FilterAlt,
                                    onClick = {
                                        onOpenHelpUrl("https://www.aprs-is.net/javAPRSFilter.aspx")
                                    },
                                )
                            }

                            "udp" -> {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                PreferenceValueItem(
                                    title = stringResource(R.string.setting_server),
                                    value = state.udpServer,
                                    summary = stringResource(R.string.p_host_summary),
                                    icon = Icons.Default.Router,
                                    onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("udp.server", state.udpServer))) },
                                )
                            }

                            "http" -> {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                PreferenceValueItem(
                                    title = stringResource(R.string.setting_server),
                                    value = state.httpServer,
                                    summary = stringResource(R.string.p_host_summary),
                                    icon = Icons.Default.Router,
                                    onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("http.server", state.httpServer))) },
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceItem(
                            title = stringResource(R.string.setting_passcode),
                            summary = if (initialPasscode.isEmpty()) {
                                stringResource(R.string.setting_passcode_missing)
                            } else {
                                stringResource(R.string.setting_passcode_configured)
                            },
                            icon = Icons.Default.Password,
                            onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.Passcode)) },
                        )
                    }
                }

                "ic705" -> {
                    PreferenceCategoryHeader(title = stringResource(R.string.setting_ic705_category))
                    PreferenceGroupCard {
                        val selectedModel = WlanRadioModel.findById(state.ic705Model)
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_wlan_radio_model),
                            value = selectedModel.modelName,
                            summary = stringResource(R.string.setting_wlan_radio_model_summary),
                            icon = Icons.Default.Radio,
                            onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.WlanModel)) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        val displayCiv = state.ic705CivAddress.ifBlank {
                            selectedModel.defaultCivHex
                        }
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_wlan_civ_address),
                            value = "${displayCiv}h",
                            summary = stringResource(R.string.setting_wlan_civ_address_summary),
                            icon = Icons.Default.Router,
                            onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("ic705.civ_address", state.ic705CivAddress))) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceValueItem(
                            title = stringResource(R.string.ic705_rx_address),
                            value = state.ic705Address,
                            summary = stringResource(R.string.setting_ic705_address_summary),
                            icon = Icons.Default.Wifi,
                            onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("ic705.address", state.ic705Address))) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceValueItem(
                            title = stringResource(R.string.ic705_rx_port),
                            value = state.ic705Port,
                            summary = stringResource(R.string.setting_ic705_port_summary),
                            icon = Icons.Default.Router,
                            onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("ic705.control_port", state.ic705Port))) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceValueItem(
                            title = stringResource(R.string.ic705_rx_username),
                            value = state.ic705Username.ifEmpty {
                                stringResource(R.string.setting_not_set)
                            },
                            summary = stringResource(R.string.setting_ic705_username_summary),
                            icon = Icons.Default.Radio,
                            onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("ic705.username", state.ic705Username))) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceValueItem(
                            title = stringResource(R.string.ic705_rx_password),
                            value = if (state.ic705Password.isEmpty()) {
                                stringResource(R.string.setting_not_set)
                            } else {
                                "••••••"
                            },
                            summary = stringResource(R.string.setting_ic705_password_summary),
                            icon = Icons.Default.Password,
                            onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("ic705.password", state.ic705Password))) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceSwitchItem(
                            title = stringResource(R.string.setting_ic705_sync_freq),
                            summary = stringResource(R.string.setting_ic705_sync_freq_summary),
                            icon = Icons.Default.Radio,
                            checked = state.ic705SyncFreq,
                            onCheckedChange = { checked ->
                                onIntent(BackendPrefsIntent.ToggleBoolean("ic705.sync_freq", checked))
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceSwitchItem(
                            title = stringResource(R.string.setting_ic705_use_gps),
                            summary = stringResource(R.string.setting_ic705_use_gps_summary),
                            icon = Icons.Default.LocationOn,
                            checked = state.ic705UseGps,
                            onCheckedChange = { checked ->
                                onIntent(BackendPrefsIntent.ToggleBoolean("ic705.use_gps", checked))
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceItem(
                            title = stringResource(R.string.setting_ic705_diagnostics),
                            summary = stringResource(R.string.setting_ic705_diagnostics_summary),
                            icon = Icons.Default.Wifi,
                            onClick = onOpenDiagnostic,
                        )
                    }
                }

                "usbradio" -> {
                    PreferenceCategoryHeader(title = stringResource(R.string.setting_usbradio_category))
                    PreferenceGroupCard {
                        val currentProfile = RadioProfile.findByModelId(state.radioModelId) ?: RadioProfile.IC705_USB
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_usbradio_model),
                            value = currentProfile.name,
                            summary = stringResource(R.string.setting_usbradio_model_summary),
                            icon = Icons.Default.Radio,
                            onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.RadioModel)) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_usbradio_baudrate),
                            value = "${state.radioBaudRate} bd",
                            summary = stringResource(R.string.setting_usbradio_baudrate_summary),
                            icon = Icons.Default.Usb,
                            onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.RadioBaudRate)) },
                        )
                        if (currentProfile.isIcom) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            val displayCiv = state.radioCivAddress.ifBlank {
                                currentProfile.defaultCivAddress?.let { Integer.toHexString(it).uppercase() } ?: "A4"
                            }
                            PreferenceValueItem(
                                title = stringResource(R.string.setting_usbradio_civ_address),
                                value = "${displayCiv}h",
                                summary = stringResource(R.string.setting_usbradio_civ_summary),
                                icon = Icons.Default.Router,
                                onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("radio.civ_address", state.radioCivAddress))) },
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        val usbOutputs = audioManager?.let { RadioAudioDevice.listUsbDevices(it, isInput = false) }.orEmpty()
                        val selectedOutId = state.radioAudioDeviceOut.toIntOrNull() ?: -1
                        val audioDeviceDisplay = if (selectedOutId == -1) {
                            if (usbOutputs.isNotEmpty()) {
                                "${stringResource(R.string.setting_usbradio_audio_auto)} (${usbOutputs.first().productName.ifEmpty { "USB Audio" }})"
                            } else {
                                "${stringResource(R.string.setting_usbradio_audio_auto)} (${stringResource(R.string.setting_usbradio_audio_none)})"
                            }
                        } else {
                            usbOutputs.firstOrNull { it.id == selectedOutId }?.productName?.ifEmpty { "USB Audio (ID: $selectedOutId)" }
                                ?: "ID: $selectedOutId"
                        }
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_usbradio_audio_device),
                            value = audioDeviceDisplay,
                            summary = stringResource(R.string.setting_usbradio_audio_device_summary),
                            icon = Icons.Default.Headphones,
                            onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.RadioAudioDevice)) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_usbradio_tx_volume),
                            value = "${state.radioTxVolume}%",
                            summary = stringResource(R.string.setting_usbradio_tx_volume_summary),
                            icon = Icons.Default.VolumeUp,
                            onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.RadioTxVolume)) },
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    PreferenceCategoryHeader(title = stringResource(R.string.p_conn_afsk))
                    PreferenceGroupCard {
                        PreferenceValueItem(
                            title = stringResource(R.string.p_afsk_prefix),
                            value = "${state.afskPrefix} ms",
                            summary = stringResource(R.string.p_afsk_prefix_summary),
                            icon = Icons.Default.Radio,
                            onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("afsk.prefix", state.afskPrefix))) },
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
                            checked = state.afskBtSco,
                            onCheckedChange = { checked ->
                                onIntent(BackendPrefsIntent.ToggleBoolean("afsk.btsco", checked))
                            },
                        )
                        if (!state.afskBtSco) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            val afskOutTitle = afskOutputOptions.firstOrNull { it.first == state.afskOutput }
                                ?.second ?: state.afskOutput
                            PreferenceValueItem(
                                title = stringResource(R.string.p_afsk_output),
                                value = afskOutTitle,
                                icon = Icons.Default.Radio,
                                onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.AfskOutput)) },
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PreferenceValueItem(
                            title = stringResource(R.string.p_afsk_prefix),
                            value = "${state.afskPrefix} ms",
                            summary = stringResource(R.string.p_afsk_prefix_summary),
                            icon = Icons.Default.Radio,
                            onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("afsk.prefix", state.afskPrefix))) },
                        )
                    }
                }

                "kiss", "kenwood", "tnc2" -> {
                    if (state.proto == "kiss") {
                        PreferenceCategoryHeader(title = stringResource(R.string.p_conn_kiss))
                        PreferenceGroupCard {
                            PreferenceValueItem(
                                title = stringResource(R.string.p_tnc_init),
                                value = state.kissInit.ifEmpty { stringResource(R.string.setting_not_set) },
                                summary = stringResource(R.string.p_tnc_init_summary),
                                icon = Icons.Default.Radio,
                                onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("kiss.init", state.kissInit))) },
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            PreferenceValueItem(
                                title = stringResource(R.string.p_tnc_delay),
                                value = "${state.kissDelay} ms",
                                summary = stringResource(R.string.p_tnc_delay_summary),
                                icon = Icons.Default.Radio,
                                onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("kiss.delay", state.kissDelay))) },
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (state.proto == "kenwood") {
                        PreferenceCategoryHeader(title = stringResource(R.string.p_conn_kwd))
                        PreferenceGroupCard {
                            PreferenceItem(
                                title = stringResource(R.string.p_conn_kwd_info),
                                icon = Icons.Default.Radio,
                                onClick = {
                                    onOpenHelpUrl("https://aprsdroid.org/kenwood/")
                                },
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            PreferenceSwitchItem(
                                title = stringResource(R.string.p_conn_kwd_gps),
                                summary = stringResource(R.string.p_conn_kwd_gps_summary),
                                icon = Icons.Default.Radio,
                                checked = state.kenwoodGps,
                                onCheckedChange = { checked ->
                                    onIntent(BackendPrefsIntent.ToggleBoolean("kenwood.gps", checked))
                                },
                            )
                            if (state.kenwoodGps) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                PreferenceSwitchItem(
                                    title = stringResource(R.string.p_conn_kwd_gps_debug),
                                    summary = stringResource(R.string.p_conn_kwd_gps_debug_summary),
                                    icon = Icons.Default.Radio,
                                    checked = state.kenwoodGpsDebug,
                                    onCheckedChange = { checked ->
                                        onIntent(BackendPrefsIntent.ToggleBoolean("kenwood.gps_debug", checked))
                                    },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    PreferenceCategoryHeader(title = stringResource(R.string.setting_tnc_category))
                    PreferenceGroupCard {
                        val tncTitle = tncLinkOptions.firstOrNull { it.first == state.tncLink }
                            ?.second ?: state.tncLink
                        PreferenceValueItem(
                            title = stringResource(R.string.setting_tnc_transport),
                            value = tncTitle,
                            summary = stringResource(R.string.setting_tnc_transport_summary),
                            icon = when (state.tncLink) {
                                "bluetooth" -> Icons.Default.Bluetooth
                                "usb" -> Icons.Default.Usb
                                else -> Icons.Default.SettingsEthernet
                            },
                            onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.TncLink)) },
                        )

                        when (state.tncLink) {
                            "tcpip" -> {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                PreferenceValueItem(
                                    title = stringResource(R.string.setting_server),
                                    value = state.tcpServer,
                                    summary = stringResource(R.string.p_tcptnc_server_summary),
                                    icon = Icons.Default.Router,
                                    onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("tcp.server", state.tcpServer))) },
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                PreferenceValueItem(
                                    title = stringResource(R.string.p_sotimeout),
                                    value = "${state.tcpSoTimeout} s",
                                    summary = stringResource(R.string.p_sotimeout_summary),
                                    icon = Icons.Default.Router,
                                    onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("tcp.sotimeout", state.tcpSoTimeout))) },
                                )
                            }

                            "bluetooth" -> {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                PreferenceSwitchItem(
                                    title = stringResource(R.string.p_bt_client),
                                    summary = stringResource(R.string.p_bt_client_summary),
                                    icon = Icons.Default.Bluetooth,
                                    checked = state.btClient,
                                    onCheckedChange = { checked ->
                                        onIntent(BackendPrefsIntent.ToggleBoolean("bt.client", checked))
                                    },
                                )
                                if (state.btClient) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_bt_tnc_device),
                                        value = bondedBluetoothDevices
                                            .firstOrNull { it.first == state.btMac }
                                            ?.second
                                            ?: state.btMac.ifEmpty {
                                                stringResource(R.string.setting_not_set)
                                            },
                                        summary = stringResource(R.string.p_bt_tnc_device_summary),
                                        icon = Icons.Default.Bluetooth,
                                        onClick = {
                                            when {
                                                !hasBluetoothPermission -> {
                                                    onRequestBluetoothPermission()
                                                }
                                                bondedBluetoothDevices.isNotEmpty() -> {
                                                    onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.BluetoothDevice))
                                                }
                                                else -> {
                                                    onOpenBluetoothSettings()
                                                }
                                            }
                                        },
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    PreferenceValueItem(
                                        title = stringResource(R.string.p_bt_channel),
                                        value = state.btChannel,
                                        summary = stringResource(R.string.p_bt_channel_summary),
                                        icon = Icons.Default.Bluetooth,
                                        onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.EditValue("bt.channel", state.btChannel))) },
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                PreferenceItem(
                                    title = stringResource(R.string.p_bt_prefs),
                                    summary = stringResource(R.string.p_bt_prefs_summary),
                                    icon = Icons.Default.Bluetooth,
                                    onClick = onOpenBluetoothSettings,
                                )
                            }

                            "usb" -> {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                PreferenceValueItem(
                                    title = stringResource(R.string.p_serial_baudrate),
                                    value = state.baudRate,
                                    summary = stringResource(R.string.p_serial_baudrate_summary),
                                    icon = Icons.Default.Usb,
                                    onClick = { onIntent(BackendPrefsIntent.OpenDialog(BackendDialog.BaudRate)) },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        when (val dialog = state.activeDialog) {
            BackendDialog.Protocol -> {
                PreferenceSelectDialog(
                    title = stringResource(R.string.setting_connection_mode),
                    options = protoOptions,
                    selected = state.proto,
                    onDismiss = { onIntent(BackendPrefsIntent.DismissDialog) },
                    onSelect = { selected -> onIntent(BackendPrefsIntent.SelectProtocol(selected)) },
                )
            }

            BackendDialog.AprsIsLink -> {
                PreferenceSelectDialog(
                    title = stringResource(R.string.setting_aprsis_transport),
                    options = aprsIsLinkOptions,
                    selected = state.aprsisLink,
                    onDismiss = { onIntent(BackendPrefsIntent.DismissDialog) },
                    onSelect = { selected -> onIntent(BackendPrefsIntent.SelectAprsIsLink(selected)) },
                )
            }

            BackendDialog.TncLink -> {
                PreferenceSelectDialog(
                    title = stringResource(R.string.setting_tnc_transport),
                    options = tncLinkOptions,
                    selected = state.tncLink,
                    onDismiss = { onIntent(BackendPrefsIntent.DismissDialog) },
                    onSelect = { selected -> onIntent(BackendPrefsIntent.SelectTncLink(selected)) },
                )
            }

            BackendDialog.BluetoothDevice -> {
                PreferenceSelectDialog(
                    title = stringResource(R.string.p_bt_tnc_device_entry),
                    options = bondedBluetoothDevices,
                    selected = state.btMac,
                    onDismiss = { onIntent(BackendPrefsIntent.DismissDialog) },
                    onSelect = { address -> onIntent(BackendPrefsIntent.SelectBluetoothDevice(address)) },
                )
            }

            BackendDialog.BaudRate -> {
                PreferenceSelectDialog(
                    title = stringResource(R.string.p_serial_baudrate),
                    options = baudRateOptions,
                    selected = state.baudRate,
                    onDismiss = { onIntent(BackendPrefsIntent.DismissDialog) },
                    onSelect = { baud -> onIntent(BackendPrefsIntent.SelectBaudRate(baud)) },
                )
            }

            BackendDialog.AfskOutput -> {
                PreferenceSelectDialog(
                    title = stringResource(R.string.p_afsk_output),
                    options = afskOutputOptions,
                    selected = state.afskOutput,
                    onDismiss = { onIntent(BackendPrefsIntent.DismissDialog) },
                    onSelect = { output -> onIntent(BackendPrefsIntent.SelectAfskOutput(output)) },
                )
            }

            BackendDialog.Passcode -> {
                PasscodeDialogCompose(
                    initialCallsign = initialCallsign,
                    initialPasscode = initialPasscode,
                    firstRun = false,
                    onDismiss = { onIntent(BackendPrefsIntent.DismissDialog) },
                    onSave = { call, pass ->
                        onIntent(BackendPrefsIntent.SavePasscode(call, pass))
                    },
                )
            }

            BackendDialog.RadioModel -> {
                RadioSelectDialog(
                    selectedModelId = state.radioModelId,
                    onDismiss = { onIntent(BackendPrefsIntent.DismissDialog) },
                    onSelect = { profile ->
                        onIntent(BackendPrefsIntent.SelectRadioModel(profile))
                    },
                )
            }

            BackendDialog.RadioBaudRate -> {
                PreferenceSelectDialog(
                    title = stringResource(R.string.setting_usbradio_baudrate),
                    options = baudRateOptions,
                    selected = state.radioBaudRate,
                    onDismiss = { onIntent(BackendPrefsIntent.DismissDialog) },
                    onSelect = { baud -> onIntent(BackendPrefsIntent.SelectRadioBaudRate(baud)) },
                )
            }

            BackendDialog.RadioAudioDevice -> {
                val usbOutputs = audioManager?.let { RadioAudioDevice.listUsbDevices(it, isInput = false) }.orEmpty()
                val audioOptions = buildList {
                    add("-1" to stringResource(R.string.setting_usbradio_audio_auto))
                    usbOutputs.forEach { dev ->
                        add(dev.id.toString() to (dev.productName.ifEmpty { "USB Audio" } + " (ID: ${dev.id})"))
                    }
                }
                PreferenceSelectDialog(
                    title = stringResource(R.string.setting_usbradio_audio_device),
                    options = audioOptions,
                    selected = state.radioAudioDeviceOut,
                    onDismiss = { onIntent(BackendPrefsIntent.DismissDialog) },
                    onSelect = { devId -> onIntent(BackendPrefsIntent.SelectRadioAudioDevice(devId)) },
                )
            }

            BackendDialog.RadioTxVolume -> {
                val volumeOptions = listOf(
                    "100" to "100%",
                    "90" to "90%",
                    "80" to "80%",
                    "70" to "70%",
                    "60" to "60%",
                    "50" to "50%",
                )
                PreferenceSelectDialog(
                    title = stringResource(R.string.setting_usbradio_tx_volume),
                    options = volumeOptions,
                    selected = state.radioTxVolume,
                    onDismiss = { onIntent(BackendPrefsIntent.DismissDialog) },
                    onSelect = { vol -> onIntent(BackendPrefsIntent.SelectRadioTxVolume(vol)) },
                )
            }

            BackendDialog.WlanModel -> {
                val modelOptions = WlanRadioModel.ALL.map { model ->
                    model.id to "${model.modelName} (CI-V: ${model.defaultCivHex}h)"
                }
                PreferenceSelectDialog(
                    title = stringResource(R.string.setting_wlan_radio_model),
                    options = modelOptions,
                    selected = state.ic705Model,
                    onDismiss = { onIntent(BackendPrefsIntent.DismissDialog) },
                    onSelect = { selectedId -> onIntent(BackendPrefsIntent.SelectWlanModel(selectedId)) },
                )
            }

            is BackendDialog.EditValue -> {
                PreferenceEditDialog(
                    title = stringResource(R.string.setting_edit_value),
                    initialValue = dialog.initialValue,
                    onDismiss = { onIntent(BackendPrefsIntent.DismissDialog) },
                    onSave = { newValue -> onIntent(BackendPrefsIntent.SaveEditValue(dialog.key, newValue)) },
                )
            }

            null -> {}
        }
    }
}
