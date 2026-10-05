package org.aprsdroid.app

import org.aprsdroid.app.aprs.AprsWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AprsWeatherTest {

    @Test
    fun testParseStandardWeatherReport() {
        val payload = "_10090556c220s004g005t077r000p000P000h50b09900"
        val weather = AprsWeather.parse(payload)
        assertNotNull(weather)
        assertEquals(220, weather!!.windDirDeg)
        assertEquals(4, weather.windSpeedMph)
        assertEquals(5, weather.windGustMph)
        assertEquals(77, weather.tempF)
        assertEquals(25.0, weather.tempC!!, 0.1)
        assertEquals(50, weather.humidityPercent)
        assertEquals(990.0, weather.pressureHpa!!, 0.1)
    }

    @Test
    fun testParseNegativeTemperature() {
        val payload = "c045s010t-04h85b10213"
        val weather = AprsWeather.parse(payload)
        assertNotNull(weather)
        assertEquals(-4, weather!!.tempF)
        assertEquals(-20.0, weather.tempC!!, 0.1)
        assertEquals(85, weather.humidityPercent)
        assertEquals(1021.3, weather.pressureHpa!!, 0.1)
    }
}
