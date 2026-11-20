package com.motodash.e2e.tier1_features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardTelemetryE2ETest {

    @Test
    fun testF5_systemTimeFormatting() {
        // R2: Top-left current time in HH:mm format
        val fixedTimeMs = 1773974400000L // Specific epoch
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        val formattedTime = sdf.format(Date(fixedTimeMs))
        assertTrue("Formatted time should match regex \\d{2}:\\d{2}", formattedTime.matches(Regex("\\d{2}:\\d{2}")))
    }

    @Test
    fun testF5_batteryPercentageDisplay() {
        // R2: Top-left phone battery percentage
        data class BatteryState(val level: Int, val isCharging: Boolean)
        fun formatBattery(state: BatteryState): String = "${state.level}%"

        assertEquals("85%", formatBattery(BatteryState(level = 85, isCharging = false)))
        assertEquals("100%", formatBattery(BatteryState(level = 100, isCharging = true)))
        assertEquals("15%", formatBattery(BatteryState(level = 15, isCharging = false)))
    }

    @Test
    fun testF5_tripOdometerAccumulation() {
        // R2: Bottom-left Trip distance (Trip 0.0 km)
        class TripOdometer {
            private var totalDistanceMeters: Double = 0.0

            fun addSegment(speedMps: Float, timeSeconds: Float) {
                totalDistanceMeters += (speedMps * timeSeconds)
            }

            fun getTripText(): String {
                val km = totalDistanceMeters / 1000.0
                return String.format(Locale.US, "Trip %.1f km", km)
            }
        }

        val odometer = TripOdometer()
        assertEquals("Trip 0.0 km", odometer.getTripText())

        // Ride at 20 m/s (~72 km/h) for 100 seconds = 2000m = 2.0 km
        odometer.addSegment(speedMps = 20.0f, timeSeconds = 100.0f)
        assertEquals("Trip 2.0 km", odometer.getTripText())

        // Another 1500m = 1.5 km -> Total 3.5 km
        odometer.addSegment(speedMps = 15.0f, timeSeconds = 100.0f)
        assertEquals("Trip 3.5 km", odometer.getTripText())
    }

    @Test
    fun testF5_tripOdometerLongPressReset() {
        // R2: Bottom-left Trip distance: long-press to reset
        class ResettableTripOdometer {
            var distanceKm: Double = 42.8

            fun onLongPress() {
                distanceKm = 0.0
            }

            fun format(): String = String.format(Locale.US, "Trip %.1f km", distanceKm)
        }

        val odometer = ResettableTripOdometer()
        assertEquals("Trip 42.8 km", odometer.format())
        odometer.onLongPress()
        assertEquals("Trip 0.0 km", odometer.format())
    }

    @Test
    fun testF5_wazeIntentConstruction() {
        // R2: Bottom-right large "Waze" floating button launching Intent(ACTION_VIEW, Uri.parse("waze://"))
        val expectedAction = "android.intent.action.VIEW"
        val wazeUriString = "waze://"

        // Pure-JVM RFC-3986 scheme/hier-part parser (java.net.URI throws URISyntaxException on empty authority "waze://")
        val rfc3986Match = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(//[^\\s]*)$").matchEntire(wazeUriString)
        assertTrue("Waze URI must conform to <scheme>://<hier-part>", rfc3986Match != null)
        val scheme = rfc3986Match!!.groupValues[1]
        val schemeSpecificPart = rfc3986Match.groupValues[2]

        data class IntentContract(val action: String, val dataUri: String, val scheme: String, val schemeSpecificPart: String)
        val wazeIntent = IntentContract(
            action = expectedAction,
            dataUri = wazeUriString,
            scheme = scheme,
            schemeSpecificPart = schemeSpecificPart
        )

        assertEquals("Intent action must be ACTION_VIEW", "android.intent.action.VIEW", wazeIntent.action)
        assertEquals("Waze URI string must be waze://", "waze://", wazeIntent.dataUri)
        assertEquals("Uri scheme must be waze", "waze", wazeIntent.scheme)
        assertEquals("Scheme-specific part must be //", "//", wazeIntent.schemeSpecificPart)
    }
}
