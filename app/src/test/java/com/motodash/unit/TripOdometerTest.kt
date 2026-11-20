package com.motodash.unit

import com.motodash.telemetry.TripOdometer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class TripOdometerTest {

    private lateinit var odometer: TripOdometer

    @Before
    fun setUp() {
        odometer = TripOdometer()
    }

    @Test
    fun `timed fixes reject plausible accuracy teleport and do not bridge signal gap`() {
        odometer.addLocation(37.0, -122.0, 5f, 36f, 1000)
        odometer.addLocation(37.00009, -122.0, 5f, 36f, 2000)
        val beforeJump = odometer.distanceMeters
        assertTrue(beforeJump in 9.0..11.0)
        odometer.addLocation(37.01, -122.0, 5f, 36f, 2500)
        assertEquals(beforeJump, odometer.distanceMeters, 0.001)
        odometer.addLocation(37.02, -122.0, 5f, 36f, 20_000)
        assertEquals(beforeJump, odometer.distanceMeters, 0.001)
        odometer.addLocation(37.02009, -122.0, 5f, 36f, 21_000)
        assertTrue(odometer.distanceMeters in 19.0..21.0)
    }

    @Test
    fun `out of order timed fix cannot change anchor or distance`() {
        odometer.addLocation(37.0, -122.0, 5f, 36f, 1000)
        odometer.addLocation(37.00009, -122.0, 5f, 36f, 2000)
        val distance = odometer.distanceMeters
        odometer.addLocation(37.5, -122.0, 5f, 36f, 1500)
        odometer.addLocation(37.00018, -122.0, 5f, 36f, 3000)
        assertEquals(distance * 2, odometer.distanceMeters, 0.01)
    }

    @Test
    fun `initial state is zero meters and formats Trip 0_0 km`() {
        assertEquals(0.0, odometer.distanceMeters, 0.0001)
        assertEquals(0.0, odometer.distanceKm, 0.0001)
        assertEquals("Trip 0.0 km", odometer.formatTripText())
    }

    @Test
    fun `first valid fix sets baseline and subsequent fixes accumulate Haversine distance`() {
        odometer.addLocation(lat = 37.0, lon = -122.0, accuracyMeters = 10f, speedKmh = 30f)
        assertEquals(0.0, odometer.distanceMeters, 0.0001)

        // 0.01 deg latitude ~ 1111.95 m
        odometer.addLocation(lat = 37.01, lon = -122.0, accuracyMeters = 10f, speedKmh = 40f)
        assertTrue(odometer.distanceMeters in 1100.0..1125.0)
        assertEquals("Trip 1.1 km", odometer.formatTripText())

        odometer.addLocation(lat = 37.02, lon = -122.0, accuracyMeters = 10f, speedKmh = 40f)
        assertTrue(odometer.distanceMeters in 2200.0..2250.0)
        assertEquals("Trip 2.2 km", odometer.formatTripText())
    }

    @Test
    fun `boundary acceptance and rejection for accuracy and speed`() {
        // Boundary valid: accuracy == 20.0f and speed == 2.5f
        odometer.addLocation(lat = 37.0, lon = -122.0, accuracyMeters = 20.0f, speedKmh = 2.5f)
        // Rejected: accuracy > 20.0f
        odometer.addLocation(lat = 37.01, lon = -122.0, accuracyMeters = 20.1f, speedKmh = 50f)
        // Rejected: negative accuracy
        odometer.addLocation(lat = 37.02, lon = -122.0, accuracyMeters = -1.0f, speedKmh = 50f)
        // Rejected: speed < 2.5f
        odometer.addLocation(lat = 37.03, lon = -122.0, accuracyMeters = 8.0f, speedKmh = 2.49f)

        assertEquals(0.0, odometer.distanceMeters, 0.0001)

        // Next valid fix should measure from original baseline (37.0 -> 37.01), proving anchor was not corrupted
        odometer.addLocation(lat = 37.01, lon = -122.0, accuracyMeters = 15.0f, speedKmh = 10.0f)
        assertTrue(odometer.distanceMeters in 1100.0..1125.0)
    }

    @Test
    fun `invalid coordinates NaN Infinity and out of bounds are ignored safely`() {
        odometer.addLocation(lat = 37.0, lon = -122.0, accuracyMeters = 8f, speedKmh = 30f)

        odometer.addLocation(lat = Double.NaN, lon = -122.0, accuracyMeters = 8f, speedKmh = 30f)
        odometer.addLocation(lat = 37.01, lon = Double.POSITIVE_INFINITY, accuracyMeters = 8f, speedKmh = 30f)
        odometer.addLocation(lat = 91.0, lon = -122.0, accuracyMeters = 8f, speedKmh = 30f)
        odometer.addLocation(lat = 37.01, lon = -181.0, accuracyMeters = 8f, speedKmh = 30f)

        assertEquals(0.0, odometer.distanceMeters, 0.0001)
    }

    @Test
    fun `teleport jump above maxJumpMeters reanchors without inflating distance`() {
        odometer.addLocation(lat = 37.0, lon = -122.0, accuracyMeters = 8f, speedKmh = 50f)
        // ~111 km jump (> 10 km maxJumpMeters)
        odometer.addLocation(lat = 38.0, lon = -122.0, accuracyMeters = 8f, speedKmh = 50f)
        assertEquals(0.0, odometer.distanceMeters, 0.0001)

        // Subsequent 0.01 deg step from new anchor (38.0 -> 38.01) accumulates normally
        odometer.addLocation(lat = 38.01, lon = -122.0, accuracyMeters = 8f, speedKmh = 50f)
        assertTrue(odometer.distanceMeters in 1100.0..1125.0)
    }

    @Test
    fun `reset clears accumulated distance and baseline anchor`() {
        odometer.addLocation(lat = 37.0, lon = -122.0, accuracyMeters = 8f, speedKmh = 50f)
        odometer.addLocation(lat = 37.01, lon = -122.0, accuracyMeters = 8f, speedKmh = 50f)
        assertTrue(odometer.distanceMeters > 1000.0)

        odometer.reset()
        assertEquals(0.0, odometer.distanceMeters, 0.0001)
        assertEquals("Trip 0.0 km", odometer.formatTripText())

        // First point after reset is new baseline, does not jump from 37.01
        odometer.addLocation(lat = 40.0, lon = -74.0, accuracyMeters = 8f, speedKmh = 50f)
        assertEquals(0.0, odometer.distanceMeters, 0.0001)
    }

    @Test
    fun `formatTripText always uses US decimal dot regardless of default Locale`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            odometer.addLocation(lat = 37.0, lon = -122.0, accuracyMeters = 8f, speedKmh = 50f)
            odometer.addLocation(lat = 37.01, lon = -122.0, accuracyMeters = 8f, speedKmh = 50f)
            assertEquals("Trip 1.1 km", odometer.formatTripText())
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
