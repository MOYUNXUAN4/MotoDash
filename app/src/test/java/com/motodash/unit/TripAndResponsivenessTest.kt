package com.motodash.unit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.motodash.location.GpsSpeedFilter
import com.motodash.location.LocationReading
import com.motodash.telemetry.TripRecorder
import com.motodash.ui.components.TripSummaryDialog
import com.motodash.ui.components.formatTripDuration
import com.motodash.ui.components.riderTitle
import com.motodash.ui.theme.MotoDashTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class TripAndResponsivenessTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun reading(speedKmh: Float, t: Long) = LocationReading(
        speedMps = speedKmh / 3.6f,
        accuracyMeters = 5f,
        bearingDegrees = 0f,
        hasBearing = true,
        latitude = 25.0,
        longitude = 121.0,
        timestampMs = t
    )

    @Test
    fun `fast deceleration alpha drops displayed speed much quicker than default smoothing`() {
        val defaultFilter = GpsSpeedFilter()
        val snappyFilter = GpsSpeedFilter(decelAlpha = 0.85f)
        listOf(defaultFilter, snappyFilter).forEach { f ->
            f.processSample(reading(80f, 0))
            f.processSample(reading(80f, 500))
        }
        val slow = defaultFilter.processSample(reading(20f, 1000)).displaySpeedKmh
        val fast = snappyFilter.processSample(reading(20f, 1000)).displaySpeedKmh
        assertTrue("snappy=$fast should be closer to 20 than default=$slow", fast < slow - 20f)

        // Full stop still zeroes instantly through the debounce
        assertEquals(0f, snappyFilter.processSample(reading(0f, 1500)).displaySpeedKmh, 0f)
    }

    @Test
    fun `acceleration keeps the standard 0_35 smoothing even with fast deceleration enabled`() {
        val default = GpsSpeedFilter()
        val snappy = GpsSpeedFilter(decelAlpha = 0.85f)
        listOf(default, snappy).forEach { it.processSample(reading(40f, 0)) }
        val a = default.processSample(reading(80f, 500)).displaySpeedKmh
        val b = snappy.processSample(reading(80f, 500)).displaySpeedKmh
        assertEquals(a, b, 0.0001f)
        assertEquals(54f, b, 0.01f)
    }

    @Test
    fun `duration formatter and rider titles`() {
        assertEquals("01:05", formatTripDuration(65_000))
        assertEquals("1:02:03", formatTripDuration(3_723_000))

        val recorder = TripRecorder()
        recorder.start(0)
        recorder.addSample(1000, 135f, 25.0, 121.0, 5f)
        val summary = recorder.stop(2000)
        assertEquals("SPEED DEMON", riderTitle(summary).second)
    }

    @Test
    fun `trip summary dialog shows top speed rider title and curve`() {
        val recorder = TripRecorder()
        recorder.start(0)
        val speeds = listOf(0f, 25f, 60f, 95f, 128f, 90f, 40f, 0f)
        speeds.forEachIndexed { i, s ->
            recorder.addSample((i + 1) * 1000L, s, 25.0 + i * 0.0004, 121.0, 5f)
        }
        val summary = recorder.stop(9000)

        composeRule.setContent {
            MotoDashTheme { TripSummaryDialog(summary = summary, onDismiss = {}) }
        }

        composeRule.onNodeWithTag("trip_summary_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("summary_max_speed").assertIsDisplayed()
        composeRule.onNodeWithText("128").assertIsDisplayed()
        composeRule.onNodeWithText("HIGHWAY HAWK").assertIsDisplayed()
        composeRule.onNodeWithTag("speed_curve_chart").assertIsDisplayed()
    }
}
