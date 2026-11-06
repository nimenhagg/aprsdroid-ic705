package org.aprsdroid.app.ui.viewmodel

import org.aprsdroid.app.data.preferences.AppPreferenceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocationPrefsViewModelTest {

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
        }
    }

    private lateinit var store: FakePreferenceStore
    private lateinit var viewModel: LocationPrefsViewModel

    @Before
    fun setUp() {
        store = FakePreferenceStore()
        viewModel = LocationPrefsViewModel(store)
    }

    @Test
    fun initialStateLoadsSensibleDefaults() {
        val state = viewModel.uiState.value
        assertEquals("smartbeaconing", state.locSource)
        assertEquals("100", state.sbFastSpeed)
        assertEquals("60", state.sbFastRate)
        assertEquals("5", state.sbSlowSpeed)
        assertEquals("1200", state.sbSlowRate)
        assertEquals("15", state.sbTurnTime)
        assertEquals("10", state.sbTurnMin)
        assertEquals("240", state.sbTurnSlope)
        assertEquals("10", state.interval)
        assertEquals("0.000", state.manualLat)
        assertEquals("0.000", state.manualLon)
        assertTrue(state.periodicPosition)
        assertEquals("0", state.ambiguity)
        assertTrue(state.showSpdBear)
        assertTrue(state.showAltitude)
        assertNull(state.activeDialog)
    }

    @Test
    fun setLocationSourceUpdatesStoreAndState() {
        viewModel.onIntent(LocationPrefsIntent.SetLocationSource("manual"))
        assertEquals("manual", viewModel.uiState.value.locSource)
        assertEquals("manual", store.getString("loc_source", ""))
    }

    @Test
    fun setAmbiguityUpdatesStoreAndState() {
        viewModel.onIntent(LocationPrefsIntent.SetAmbiguity("2"))
        assertEquals("2", viewModel.uiState.value.ambiguity)
        assertEquals("2", store.getString("priv_ambiguity", ""))
    }

    @Test
    fun saveFieldValueUpdatesTargetPreference() {
        viewModel.onIntent(LocationPrefsIntent.SaveFieldValue("sb.fastspeed", "120"))
        assertEquals("120", viewModel.uiState.value.sbFastSpeed)
        assertEquals("120", store.getString("sb.fastspeed", ""))

        viewModel.onIntent(LocationPrefsIntent.SaveFieldValue("interval", "5"))
        assertEquals("5", viewModel.uiState.value.interval)
        assertEquals("5", store.getString("interval", ""))
    }

    @Test
    fun updateManualCoordinatesFormatsToFiveDecimals() {
        viewModel.onIntent(LocationPrefsIntent.UpdateManualCoordinates(39.9042, 116.4074))
        assertEquals("39.90420", viewModel.uiState.value.manualLat)
        assertEquals("116.40740", viewModel.uiState.value.manualLon)
        assertEquals("39.90420", store.getString("manual_lat", ""))
        assertEquals("116.40740", store.getString("manual_lon", ""))
    }

    @Test
    fun toggleSwitchesUpdatesStateAndStore() {
        viewModel.onIntent(LocationPrefsIntent.SetPeriodicPosition(false))
        assertFalse(viewModel.uiState.value.periodicPosition)
        assertFalse(store.getBoolean("periodicposition", true))

        viewModel.onIntent(LocationPrefsIntent.SetShowSpdBear(false))
        assertFalse(viewModel.uiState.value.showSpdBear)
        assertFalse(store.getBoolean("priv_spdbear", true))

        viewModel.onIntent(LocationPrefsIntent.SetShowAltitude(false))
        assertFalse(viewModel.uiState.value.showAltitude)
        assertFalse(store.getBoolean("priv_altitude", true))
    }

    @Test
    fun dialogLifecycleWorksCorrectly() {
        viewModel.onIntent(LocationPrefsIntent.OpenLocationSourceDialog)
        assertEquals(LocationDialog.LocationSource, viewModel.uiState.value.activeDialog)

        viewModel.onIntent(LocationPrefsIntent.DismissDialog)
        assertNull(viewModel.uiState.value.activeDialog)

        viewModel.onIntent(LocationPrefsIntent.OpenAmbiguityDialog)
        assertEquals(LocationDialog.Ambiguity, viewModel.uiState.value.activeDialog)

        viewModel.onIntent(LocationPrefsIntent.DismissDialog)
        assertNull(viewModel.uiState.value.activeDialog)

        viewModel.onIntent(LocationPrefsIntent.OpenEditDialog("sb.slowspeed"))
        val active = viewModel.uiState.value.activeDialog
        assertNotNull(active)
        assertTrue(active is LocationDialog.EditField)
        assertEquals("sb.slowspeed", (active as LocationDialog.EditField).key)
    }
}
