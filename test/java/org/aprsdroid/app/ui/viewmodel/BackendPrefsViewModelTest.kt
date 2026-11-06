package org.aprsdroid.app.ui.viewmodel

import org.aprsdroid.app.data.preferences.AppPreferenceStore
import org.aprsdroid.app.radio.RadioProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackendPrefsViewModelTest {

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
    private lateinit var viewModel: BackendPrefsViewModel

    @Before
    fun setUp() {
        store = FakePreferenceStore()
        viewModel = BackendPrefsViewModel(store)
    }

    @Test
    fun initialStateLoadsSensibleDefaults() {
        val state = viewModel.uiState.value
        assertEquals("aprsis", state.proto)
        assertEquals("tcp", state.aprsisLink)
        assertEquals("IC-705", state.ic705Model)
        assertEquals("A4", state.ic705CivAddress)
        assertNull(state.activeDialog)
    }

    @Test
    fun selectProtocolUpdatesPreferenceAndClosesDialog() {
        viewModel.dispatch(BackendPrefsIntent.OpenDialog(BackendDialog.Protocol))
        assertEquals(BackendDialog.Protocol, viewModel.uiState.value.activeDialog)

        viewModel.dispatch(BackendPrefsIntent.SelectProtocol("ic705"))
        assertEquals("ic705", viewModel.uiState.value.proto)
        assertEquals("ic705", store.getString("proto", ""))
        assertNull(viewModel.uiState.value.activeDialog)
    }

    @Test
    fun selectWlanModelUpdatesModelAndCivAddress() {
        viewModel.dispatch(BackendPrefsIntent.SelectWlanModel("IC-9700"))
        val state = viewModel.uiState.value
        assertEquals("IC-9700", state.ic705Model)
        assertEquals("A2", state.ic705CivAddress)
        assertEquals("IC-9700", store.getString("ic705.model", ""))
        assertEquals("A2", store.getString("ic705.civ_address", ""))
    }

    @Test
    fun selectRadioModelUpdatesHamlibProfileSettings() {
        val profile = RadioProfile.IC705_USB
        viewModel.dispatch(BackendPrefsIntent.SelectRadioModel(profile))

        val state = viewModel.uiState.value
        assertEquals(profile.hamlibModelId, state.radioModelId)
        assertEquals(profile.defaultBaudRate.toString(), state.radioBaudRate)
        assertEquals("A4", state.radioCivAddress)
    }

    @Test
    fun saveEditValueSanitizesHexCivAddress() {
        viewModel.dispatch(BackendPrefsIntent.SaveEditValue("ic705.civ_address", "0xa4h"))
        assertEquals("A4", viewModel.uiState.value.ic705CivAddress)
        assertEquals("A4", store.getString("ic705.civ_address", ""))
    }

    @Test
    fun toggleBooleanUpdatesStateAndStore() {
        assertFalse(viewModel.uiState.value.ic705SyncFreq)

        viewModel.dispatch(BackendPrefsIntent.ToggleBoolean("ic705.sync_freq", true))
        assertTrue(viewModel.uiState.value.ic705SyncFreq)
        assertTrue(store.getBoolean("ic705.sync_freq", false))
    }

    @Test
    fun savePasscodeUpdatesCredentials() {
        viewModel.dispatch(BackendPrefsIntent.SavePasscode("BG7XXX", "12345"))
        assertEquals("BG7XXX", store.getString("callsign", ""))
        assertEquals("12345", store.getString("passcode", ""))
        assertFalse(store.getBoolean("firstrun", true))
    }

    @Test
    fun dialogOpenAndDismissalCycle() {
        assertNull(viewModel.uiState.value.activeDialog)

        viewModel.dispatch(BackendPrefsIntent.OpenDialog(BackendDialog.TncLink))
        assertEquals(BackendDialog.TncLink, viewModel.uiState.value.activeDialog)

        viewModel.dispatch(BackendPrefsIntent.DismissDialog)
        assertNull(viewModel.uiState.value.activeDialog)
    }
}
