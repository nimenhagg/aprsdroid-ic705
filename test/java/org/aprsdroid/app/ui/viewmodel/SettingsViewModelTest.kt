package org.aprsdroid.app.ui.viewmodel

import org.aprsdroid.app.data.preferences.AppPreferenceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {

    private class FakePreferenceStore : AppPreferenceStore {
        private val stringMap = mutableMapOf<String, String>()
        private val boolMap = mutableMapOf<String, Boolean>()
        private val setMap = mutableMapOf<String, Set<String>>()

        override fun getString(key: String, defValue: String?): String {
            return stringMap[key] ?: (defValue ?: "")
        }

        override fun getBoolean(key: String, defValue: Boolean): Boolean {
            return boolMap[key] ?: defValue
        }

        override fun getStringInt(key: String, defValue: Int): Int {
            return stringMap[key]?.toIntOrNull() ?: defValue
        }

        override fun getStringSet(key: String, defValue: Set<String>): Set<String> {
            return setMap[key] ?: defValue
        }

        override fun set(name: String, newVal: String): String {
            stringMap[name] = newVal
            return newVal
        }

        override fun set(name: String, newVal: Boolean): Boolean {
            boolMap[name] = newVal
            return newVal
        }

        override fun setStringSet(name: String, values: Set<String>) {
            setMap[name] = values
        }

        override fun saveCredentials(callsign: String, passcode: String) {
            stringMap["callsign"] = callsign
            stringMap["passcode"] = passcode
            boolMap["firstrun"] = false
        }
    }

    private lateinit var store: FakePreferenceStore
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        store = FakePreferenceStore()
        viewModel = SettingsViewModel(store)
    }

    @Test
    fun initialStateLoadsSensibleDefaults() {
        val state = viewModel.uiState.value
        assertEquals("", state.callsign)
        assertEquals("10", state.ssid)
        assertEquals("WIDE1-1", state.digiPath)
        assertEquals("/$", state.symbol)
        assertEquals("APRSdroid Mod", state.status)
        assertTrue(state.showObjects)
        assertFalse(state.sendBatteryInfo)
        assertTrue(state.autoCheckUpdates)
        assertFalse(state.phgEnabled)
        assertNull(state.activeDialog)
    }

    @Test
    fun saveCredentialsUpdatesCallsignAndDismissesDialog() {
        viewModel.onIntent(SettingsIntent.OpenIdentityDialog)
        assertEquals(SettingsDialog.Identity, viewModel.uiState.value.activeDialog)

        viewModel.onIntent(SettingsIntent.SaveCredentials("bg1abc", "12345"))
        assertEquals("BG1ABC", viewModel.uiState.value.callsign)
        assertEquals("BG1ABC", store.getString("callsign", ""))
        assertEquals("12345", store.getString("passcode", ""))
        assertNull(viewModel.uiState.value.activeDialog)
    }

    @Test
    fun saveStatusTruncatesTo42Characters() {
        val longStatus = "This is a very long APRS status comment that exceeds forty two characters easily"
        viewModel.onIntent(SettingsIntent.SaveStatus(longStatus))

        val saved = viewModel.uiState.value.status
        assertEquals(42, saved.length)
        assertEquals(longStatus.take(42), saved)
        assertEquals(longStatus.take(42), store.getString("status", ""))
    }

    @Test
    fun digiPresetsAddAndDeleteCorrectly() {
        viewModel.onIntent(SettingsIntent.AddDigiPreset("WIDE2-2"))
        assertTrue(viewModel.uiState.value.userDigiPresets.contains("WIDE2-2"))
        assertTrue(store.getStringSet("digi_path_user_presets").contains("WIDE2-2"))

        viewModel.onIntent(SettingsIntent.DeleteDigiPreset("WIDE2-2"))
        assertFalse(viewModel.uiState.value.userDigiPresets.contains("WIDE2-2"))
        assertFalse(store.getStringSet("digi_path_user_presets").contains("WIDE2-2"))
    }

    @Test
    fun savePhgUpdatesAllSixFields() {
        viewModel.onIntent(
            SettingsIntent.SavePhg(
                enabled = true,
                power = "25",
                height = "20",
                unit = "m",
                gain = "6",
                dir = "4",
            )
        )

        val state = viewModel.uiState.value
        assertTrue(state.phgEnabled)
        assertEquals("25", state.phgPower)
        assertEquals("20", state.phgHeight)
        assertEquals("m", state.phgHeightUnit)
        assertEquals("6", state.phgGain)
        assertEquals("4", state.phgDir)

        assertTrue(store.getBoolean("phg_enabled", false))
        assertEquals("25", store.getString("phg_power", ""))
        assertEquals("20", store.getString("phg_height", ""))
    }

    @Test
    fun toggleSwitchesUpdatesStateAndStore() {
        viewModel.onIntent(SettingsIntent.ToggleShowObjects(false))
        assertFalse(viewModel.uiState.value.showObjects)
        assertFalse(store.getBoolean("show_objects", true))

        viewModel.onIntent(SettingsIntent.ToggleSendBatteryInfo(true))
        assertTrue(viewModel.uiState.value.sendBatteryInfo)
        assertTrue(store.getBoolean("send_battery_aprsis", false))

        viewModel.onIntent(SettingsIntent.ToggleAutoCheckUpdates(false))
        assertFalse(viewModel.uiState.value.autoCheckUpdates)
        assertFalse(store.getBoolean("check_updates_automatically", true))
    }

    @Test
    fun dialogLifecycleWorksCorrectly() {
        viewModel.onIntent(SettingsIntent.OpenAboutDialog)
        assertEquals(SettingsDialog.About, viewModel.uiState.value.activeDialog)

        viewModel.onIntent(SettingsIntent.DismissDialog)
        assertNull(viewModel.uiState.value.activeDialog)
    }
}
