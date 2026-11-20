package com.motodash.unit

import com.motodash.location.GpsSignalStatus
import com.motodash.location.GpsSpeedFilter
import com.motodash.location.LocationReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GpsSpeedFilterTest {

    private lateinit var filter: GpsSpeedFilter

    @Before
    fun setUp() {
        filter = GpsSpeedFilter(
            alpha = 0.35f,
            stopThresholdKmh = 2.5f,
            maxAccuracyMeters = 20.0f,
            signalTimeoutMs = 3000L
        )
    }

    private fun reading(
        speedMps: Float = 10.0f,
        accuracyMeters: Float = 8.0f,
        bearingDegrees: Float = 90.0f,
        hasBearing: Boolean = true,
        timestampMs: Long = 1000L
    ) = LocationReading(
        speedMps = speedMps,
        accuracyMeters = accuracyMeters,
        bearingDegrees = bearingDegrees,
        hasBearing = hasBearing,
        latitude = 37.7749,
        longitude = -122.4194,
        timestampMs = timestampMs
    )

    @Test
    fun `first valid sample converts mps to kmh and initializes EMA directly without ramping from zero`() {
        val state = filter.processSample(reading(speedMps = 20.0f, accuracyMeters = 10.0f, timestampMs = 1000L))

        assertEquals(72.0f, state.rawSpeedKmh, 0.001f)
        assertEquals(72.0f, state.displaySpeedKmh, 0.001f)
        assertEquals(72, state.roundedSpeedKmh)
        assertEquals(GpsSignalStatus.GREEN, state.status)
        assertFalse(state.isTimedOut)
        assertFalse(state.isZeroDebounced)
        assertFalse(state.isAccuracyRejected)
    }

    @Test
    fun `sequential samples apply EMA smoothing with alpha 0_35`() {
        val s1 = filter.processSample(reading(speedMps = 10.0f, timestampMs = 1000L)) // 36.0 km/h
        assertEquals(36.0f, s1.displaySpeedKmh, 0.001f)

        val s2 = filter.processSample(reading(speedMps = 15.0f, timestampMs = 2000L)) // 54.0 km/h
        // 0.35 * 54 + 0.65 * 36 = 18.9 + 23.4 = 42.3
        assertEquals(54.0f, s2.rawSpeedKmh, 0.001f)
        assertEquals(42.3f, s2.displaySpeedKmh, 0.001f)
        assertEquals(42, s2.roundedSpeedKmh)
    }

    @Test
    fun `stop debounce zeroes speeds strictly below 2_5 kmh and accepts 2_5 kmh`() {
        val below = filter.processSample(reading(speedMps = 2.49f / 3.6f, timestampMs = 1000L))
        assertEquals(0.0f, below.displaySpeedKmh, 0.001f)
        assertEquals(0, below.roundedSpeedKmh)
        assertTrue(below.isZeroDebounced)
        assertFalse(below.isAccuracyRejected)

        val atBoundary = filter.processSample(reading(speedMps = 2.50f / 3.6f, timestampMs = 2000L))
        assertEquals(2.50f, atBoundary.displaySpeedKmh, 0.01f)
        assertFalse(atBoundary.isZeroDebounced)
    }

    @Test
    fun `accuracy cutoff tiers and rejection boundaries`() {
        val greenBoundary = filter.processSample(reading(speedMps = 10.0f, accuracyMeters = 15.0f, timestampMs = 1000L))
        assertEquals(GpsSignalStatus.GREEN, greenBoundary.status)
        assertFalse(greenBoundary.isAccuracyRejected)

        val yellowAccepted = filter.processSample(reading(speedMps = 10.0f, accuracyMeters = 20.0f, timestampMs = 2000L))
        assertEquals(GpsSignalStatus.YELLOW, yellowAccepted.status)
        assertFalse(yellowAccepted.isAccuracyRejected)
        assertEquals(36.0f, yellowAccepted.displaySpeedKmh, 0.001f)

        val yellowRejected = filter.processSample(reading(speedMps = 10.0f, accuracyMeters = 20.1f, timestampMs = 3000L))
        assertEquals(GpsSignalStatus.YELLOW, yellowRejected.status)
        assertTrue(yellowRejected.isAccuracyRejected)
        assertEquals(0.0f, yellowRejected.displaySpeedKmh, 0.001f)

        val yellowUpper = filter.processSample(reading(speedMps = 10.0f, accuracyMeters = 50.0f, timestampMs = 4000L))
        assertEquals(GpsSignalStatus.YELLOW, yellowUpper.status)
        assertTrue(yellowUpper.isAccuracyRejected)

        val redPoorAccuracy = filter.processSample(reading(speedMps = 10.0f, accuracyMeters = 50.1f, timestampMs = 5000L))
        assertEquals(GpsSignalStatus.RED, redPoorAccuracy.status)
        assertTrue(redPoorAccuracy.isAccuracyRejected)
        assertEquals(0.0f, redPoorAccuracy.displaySpeedKmh, 0.001f)
    }

    @Test
    fun `invalid accuracy values zero negative NaN Infinity are rejected as RED`() {
        listOf(0.0f, -5.0f, Float.NaN, Float.POSITIVE_INFINITY).forEachIndexed { idx, badAcc ->
            val state = filter.processSample(
                reading(speedMps = 12.0f, accuracyMeters = badAcc, timestampMs = 1000L + idx * 500L)
            )
            assertEquals(GpsSignalStatus.RED, state.status)
            assertTrue(state.isAccuracyRejected)
            assertEquals(0.0f, state.displaySpeedKmh, 0.001f)
        }
    }

    @Test
    fun `recovery after stop debounce and accuracy rejection initializes directly without ramping from zero`() {
        filter.processSample(reading(speedMps = 15.0f, timestampMs = 1000L)) // 54 km/h
        val stopped = filter.processSample(reading(speedMps = 0.2f, timestampMs = 2000L))
        assertTrue(stopped.isZeroDebounced)

        val resumedAfterStop = filter.processSample(reading(speedMps = 15.0f, timestampMs = 3000L))
        assertEquals(54.0f, resumedAfterStop.displaySpeedKmh, 0.001f)

        val rejected = filter.processSample(reading(speedMps = 15.0f, accuracyMeters = 30.0f, timestampMs = 4000L))
        assertTrue(rejected.isAccuracyRejected)

        val resumedAfterReject = filter.processSample(reading(speedMps = 20.0f, accuracyMeters = 10.0f, timestampMs = 5000L))
        assertEquals(72.0f, resumedAfterReject.displaySpeedKmh, 0.001f)
    }

    @Test
    fun `non finite or negative speed is safely clamped to zero debounced`() {
        listOf(-3.0f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEachIndexed { idx, badSpeed ->
            val state = filter.processSample(
                reading(speedMps = badSpeed, accuracyMeters = 8.0f, timestampMs = 1000L + idx * 500L)
            )
            assertEquals(0.0f, state.rawSpeedKmh, 0.001f)
            assertEquals(0.0f, state.displaySpeedKmh, 0.001f)
            assertTrue(state.isZeroDebounced)
        }
    }

    @Test
    fun `bearing freezes when stationary rejected or missing and normalizes wraparound`() {
        val moving = filter.processSample(reading(speedMps = 10.0f, bearingDegrees = 370.0f, timestampMs = 1000L))
        assertEquals(10.0f, moving.effectiveBearingDegrees, 0.001f)

        val stationary = filter.processSample(reading(speedMps = 0.4f, bearingDegrees = 220.0f, timestampMs = 2000L))
        assertEquals(10.0f, stationary.effectiveBearingDegrees, 0.001f)

        val rejected = filter.processSample(
            reading(speedMps = 12.0f, accuracyMeters = 25.0f, bearingDegrees = 180.0f, timestampMs = 3000L)
        )
        assertEquals(10.0f, rejected.effectiveBearingDegrees, 0.001f)

        val noBearing = filter.processSample(
            reading(speedMps = 12.0f, accuracyMeters = 8.0f, bearingDegrees = 250.0f, hasBearing = false, timestampMs = 4000L)
        )
        assertEquals(10.0f, noBearing.effectiveBearingDegrees, 0.001f)

        val negativeBearing = filter.processSample(
            reading(speedMps = 12.0f, accuracyMeters = 8.0f, bearingDegrees = -10.0f, hasBearing = true, timestampMs = 5000L)
        )
        assertEquals(350.0f, negativeBearing.effectiveBearingDegrees, 0.001f)
    }

    @Test
    fun `timeout triggers strictly after 3000ms and before any sample arrives`() {
        val initialTimeout = filter.checkTimeout(1000L)
        assertTrue(initialTimeout.isTimedOut)
        assertEquals(GpsSignalStatus.RED, initialTimeout.status)
        assertEquals(0.0f, initialTimeout.displaySpeedKmh, 0.001f)

        filter.processSample(reading(speedMps = 15.0f, accuracyMeters = 18.0f, timestampMs = 2000L))

        val atBoundary = filter.checkTimeout(5000L) // elapsed == 3000L
        assertFalse(atBoundary.isTimedOut)
        assertEquals(GpsSignalStatus.YELLOW, atBoundary.status)
        assertEquals(54.0f, atBoundary.displaySpeedKmh, 0.001f)

        val afterTimeout = filter.checkTimeout(5001L) // elapsed == 3001L
        assertTrue(afterTimeout.isTimedOut)
        assertEquals(GpsSignalStatus.RED, afterTimeout.status)
        assertEquals(0.0f, afterTimeout.displaySpeedKmh, 0.001f)
        assertEquals(0, afterTimeout.roundedSpeedKmh)

        val recovered = filter.processSample(reading(speedMps = 20.0f, accuracyMeters = 10.0f, timestampMs = 6000L))
        assertFalse(recovered.isTimedOut)
        assertEquals(GpsSignalStatus.GREEN, recovered.status)
        assertEquals(72.0f, recovered.displaySpeedKmh, 0.001f)
    }

    @Test
    fun `reset clears prior state and EMA history`() {
        filter.processSample(reading(speedMps = 10.0f, bearingDegrees = 123.0f, timestampMs = 1000L))
        filter.reset()

        val afterResetTimeout = filter.checkTimeout(1500L)
        assertTrue(afterResetTimeout.isTimedOut)
        assertEquals(0.0f, afterResetTimeout.effectiveBearingDegrees, 0.001f)

        val freshSample = filter.processSample(reading(speedMps = 20.0f, bearingDegrees = 45.0f, timestampMs = 2000L))
        assertEquals(72.0f, freshSample.displaySpeedKmh, 0.001f)
        assertEquals(45.0f, freshSample.effectiveBearingDegrees, 0.001f)
    }

    @Test
    fun `extreme finite speed overflowing float times 3_6 does not poison EMA and recovers cleanly`() {
        val overflow = filter.processSample(reading(speedMps = Float.MAX_VALUE, accuracyMeters = 8.0f, timestampMs = 1000L))
        assertTrue(overflow.rawSpeedKmh.isFinite())
        assertTrue(overflow.displaySpeedKmh.isFinite())
        assertEquals(0.0f, overflow.rawSpeedKmh, 0.0f)
        assertEquals(0.0f, overflow.displaySpeedKmh, 0.0f)
        assertEquals(0, overflow.roundedSpeedKmh)
        assertTrue(overflow.isZeroDebounced)

        val recovered = filter.processSample(reading(speedMps = 10.0f, accuracyMeters = 8.0f, timestampMs = 2000L))
        assertEquals(36.0f, recovered.rawSpeedKmh, 0.001f)
        assertEquals(36.0f, recovered.displaySpeedKmh, 0.001f)
        assertEquals(36, recovered.roundedSpeedKmh)
        assertFalse(recovered.isZeroDebounced)
    }

    @Test
    fun `negative zero speedMps is canonicalized to positive zero`() {
        val state = filter.processSample(reading(speedMps = -0.0f, accuracyMeters = 8.0f, timestampMs = 1000L))
        assertEquals(0, java.lang.Float.floatToRawIntBits(state.rawSpeedKmh))
        assertEquals(0, java.lang.Float.floatToRawIntBits(state.displaySpeedKmh))
    }

    @Test
    fun `checkTimeout handles Long MIN_VALUE and MAX_VALUE boundaries without subtraction overflow`() {
        filter.processSample(reading(speedMps = 15.0f, accuracyMeters = 8.0f, timestampMs = Long.MIN_VALUE))
        val minTimedOut = filter.checkTimeout(1000L)
        assertTrue(minTimedOut.isTimedOut)
        assertEquals(GpsSignalStatus.RED, minTimedOut.status)

        filter.reset()
        filter.processSample(reading(speedMps = 15.0f, accuracyMeters = 8.0f, timestampMs = -5000L))
        val maxTimedOut = filter.checkTimeout(Long.MAX_VALUE)
        assertTrue(maxTimedOut.isTimedOut)
        assertEquals(GpsSignalStatus.RED, maxTimedOut.status)

        val maxTimeoutFilter = GpsSpeedFilter(signalTimeoutMs = Long.MAX_VALUE)
        maxTimeoutFilter.processSample(reading(speedMps = 15.0f, accuracyMeters = 8.0f, timestampMs = -5000L))
        assertTrue(maxTimeoutFilter.checkTimeout(Long.MAX_VALUE).isTimedOut)

        filter.reset()
        filter.processSample(reading(speedMps = 15.0f, accuracyMeters = 8.0f, timestampMs = Long.MAX_VALUE - 1000L))
        val saturatedThreshold = filter.checkTimeout(Long.MAX_VALUE)
        assertFalse(saturatedThreshold.isTimedOut)
        assertEquals(GpsSignalStatus.GREEN, saturatedThreshold.status)
    }

    @Test
    fun `out-of-order backward timestamp sample is ignored without corrupting speed or causing false timeout`() {
        val first = filter.processSample(reading(speedMps = 15.0f, accuracyMeters = 8.0f, timestampMs = 10_000L))
        assertEquals(54.0f, first.displaySpeedKmh, 0.001f)

        val stale = filter.processSample(reading(speedMps = 2.0f, accuracyMeters = 19.0f, timestampMs = 5_000L))
        assertEquals(54.0f, stale.displaySpeedKmh, 0.001f)
        assertEquals(10_000L, stale.lastUpdateTimestampMs)
        assertEquals(GpsSignalStatus.GREEN, stale.status)

        val timeoutCheck = filter.checkTimeout(10_500L)
        assertFalse(timeoutCheck.isTimedOut)
        assertEquals(GpsSignalStatus.GREEN, timeoutCheck.status)
        assertEquals(54.0f, timeoutCheck.displaySpeedKmh, 0.001f)
    }
}
