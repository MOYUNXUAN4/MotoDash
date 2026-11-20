package com.motodash.e2e.tier1_features

import com.motodash.e2e.harness.GpsSpeedFilterEngine
import com.motodash.e2e.harness.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GpsSpeedFilterE2ETest {

    private lateinit var filter: GpsSpeedFilterEngine

    @Before
    fun setUp() {
        filter = GpsSpeedFilterEngine(alpha = 0.35f)
    }

    @Test
    fun testF6_rawSpeedMpsToKmhConversion() {
        // R3: Speed calculation: location.speed * 3.6f (km/h)
        val sample = LocationSample(speedMps = 10.0f, accuracyMeters = 5.0f, timestampMs = 1000L)
        val result = filter.processSample(sample)
        // 10.0 m/s * 3.6 = 36.0 km/h (first sample direct)
        assertEquals(36.0f, result.displaySpeedKmh, 0.001f)
        assertFalse(result.isZeroDebounced)
        assertFalse(result.isAccuracyRejected)
    }

    @Test
    fun testF6_stopDebounceUnder2_5Kmh() {
        // R3: Stop debounce: Force speed to 0 when speed < 2.5f km/h
        val sample = LocationSample(speedMps = 0.5f, accuracyMeters = 5.0f, timestampMs = 2000L)
        // 0.5 * 3.6 = 1.8 km/h < 2.5 km/h
        val result = filter.processSample(sample)
        assertEquals(0.0f, result.displaySpeedKmh, 0.0001f)
        assertTrue(result.isZeroDebounced)
        assertFalse(result.isAccuracyRejected)
    }

    @Test
    fun testF6_accuracyCutoffAbove20m() {
        // R3: Stop debounce: Force speed to 0 when location.accuracy > 20f
        val sample = LocationSample(speedMps = 15.0f, accuracyMeters = 22.5f, timestampMs = 3000L)
        // Even though speed is 54 km/h, accuracy 22.5m > 20m forces 0
        val result = filter.processSample(sample)
        assertEquals(0.0f, result.displaySpeedKmh, 0.0001f)
        assertFalse(result.isZeroDebounced)
        assertTrue(result.isAccuracyRejected)
    }

    @Test
    fun testF6_exponentialMovingAverageSmoothing() {
        // R3: Smoothing: Exponential moving average (alpha = 0.35f)
        val s1 = LocationSample(speedMps = 10.0f, accuracyMeters = 5.0f, timestampMs = 1000L) // 36 km/h
        val r1 = filter.processSample(s1)
        assertEquals(36.0f, r1.displaySpeedKmh, 0.001f)

        val s2 = LocationSample(speedMps = 15.0f, accuracyMeters = 5.0f, timestampMs = 2000L) // 54 km/h
        val r2 = filter.processSample(s2)
        // EMA: 0.35 * 54.0 + (1 - 0.35) * 36.0 = 18.9 + 23.4 = 42.3 km/h
        val expectedEma = (0.35f * 54.0f) + (0.65f * 36.0f)
        assertEquals(expectedEma, r2.displaySpeedKmh, 0.001f)
    }

    @Test
    fun testF6_firstSampleInitialization() {
        // When filter is freshly initialized, first valid sample takes raw speed directly without smoothing from 0
        val freshFilter = GpsSpeedFilterEngine(alpha = 0.35f)
        val initialSample = LocationSample(speedMps = 20.0f, accuracyMeters = 8.0f, timestampMs = 5000L) // 72 km/h
        val result = freshFilter.processSample(initialSample)
        assertEquals(72.0f, result.displaySpeedKmh, 0.001f)
    }
}
