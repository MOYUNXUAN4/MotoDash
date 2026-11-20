package com.motodash.e2e.tier1_features

import com.motodash.e2e.harness.GpsSpeedFilterEngine
import com.motodash.e2e.harness.GpsStatus
import com.motodash.e2e.harness.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GpsSignalStatusE2ETest {

    private lateinit var filter: GpsSpeedFilterEngine

    @Before
    fun setUp() {
        filter = GpsSpeedFilterEngine(alpha = 0.35f, timeoutThresholdMs = 3000L)
    }

    @Test
    fun testF7_threeSecondSignalTimeoutTrigger() {
        // R3: Signal loss: Display -- or dimmed speed digits when no update received for > 3 seconds
        val s1 = LocationSample(speedMps = 15.0f, accuracyMeters = 5.0f, timestampMs = 1000L)
        filter.processSample(s1)

        // Elapsed = 3001ms (> 3000ms threshold)
        val timeoutResult = filter.checkTimeout(currentTimestampMs = 4001L)
        assertTrue("Timeout must be flagged when elapsed > 3000ms", timeoutResult.isTimedOut)
        assertEquals(GpsStatus.RED, timeoutResult.status)
        assertEquals(0.0f, timeoutResult.displaySpeedKmh, 0.001f)
    }

    @Test
    fun testF7_withinThreeSecondsNoTimeout() {
        // Elapsed = 2500ms (<= 3000ms)
        val s1 = LocationSample(speedMps = 15.0f, accuracyMeters = 5.0f, timestampMs = 1000L)
        filter.processSample(s1)

        val activeResult = filter.checkTimeout(currentTimestampMs = 3500L)
        assertFalse("Timeout must not be flagged when elapsed <= 3000ms", activeResult.isTimedOut)
        assertEquals(54.0f, activeResult.displaySpeedKmh, 0.001f)
    }

    @Test
    fun testF7_gpsStatusGreenHighAccuracy() {
        // R2: GPS status indicator (green <= 15m accuracy)
        val sample = LocationSample(speedMps = 10.0f, accuracyMeters = 12.0f, timestampMs = 1000L)
        val result = filter.processSample(sample)
        assertEquals(GpsStatus.GREEN, result.status)
    }

    @Test
    fun testF7_gpsStatusYellowModerateAccuracy() {
        // R2: yellow = weak signal (> 15m)
        val sample = LocationSample(speedMps = 10.0f, accuracyMeters = 18.5f, timestampMs = 1000L)
        val result = filter.processSample(sample)
        assertEquals(GpsStatus.YELLOW, result.status)
    }

    @Test
    fun testF7_gpsStatusRedTimeoutOrNoFix() {
        // R2: red = no fix
        val sample = LocationSample(speedMps = 10.0f, accuracyMeters = 65.0f, timestampMs = 1000L)
        val result = filter.processSample(sample)
        assertEquals(GpsStatus.RED, result.status)
    }
}
