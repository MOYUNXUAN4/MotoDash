package com.motodash.unit

import com.motodash.location.DashboardMath
import com.motodash.location.FilteredSpeedState
import com.motodash.location.GpsSignalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardMathTest {

    @Test
    fun `calculateDynamicZoom clamps at 16_5 below 20 and 14_5 above 100 with linear interpolation`() {
        assertEquals(16.5, DashboardMath.calculateDynamicZoom(-5f), 0.0001)
        assertEquals(16.5, DashboardMath.calculateDynamicZoom(0f), 0.0001)
        assertEquals(16.5, DashboardMath.calculateDynamicZoom(20f), 0.0001)
        assertEquals(16.0, DashboardMath.calculateDynamicZoom(40f), 0.0001)
        assertEquals(15.5, DashboardMath.calculateDynamicZoom(60f), 0.0001)
        assertEquals(15.0, DashboardMath.calculateDynamicZoom(80f), 0.0001)
        assertEquals(14.5, DashboardMath.calculateDynamicZoom(100f), 0.0001)
        assertEquals(14.5, DashboardMath.calculateDynamicZoom(180f), 0.0001)
        assertEquals(16.5, DashboardMath.calculateDynamicZoom(Float.NaN), 0.0001)
        assertEquals(14.5, DashboardMath.calculateDynamicZoom(Float.POSITIVE_INFINITY), 0.0001)
        assertEquals(16.5, DashboardMath.calculateDynamicZoom(Float.NEGATIVE_INFINITY), 0.0001)
    }

    @Test
    fun `getSpeedColorHex follows white amber red thresholds`() {
        assertEquals(DashboardMath.COLOR_NORMAL_WHITE, DashboardMath.getSpeedColorHex(0f))
        assertEquals(DashboardMath.COLOR_NORMAL_WHITE, DashboardMath.getSpeedColorHex(109.9f))
        assertEquals(DashboardMath.COLOR_WARNING_AMBER, DashboardMath.getSpeedColorHex(110.0f))
        assertEquals(DashboardMath.COLOR_WARNING_AMBER, DashboardMath.getSpeedColorHex(129.9f))
        assertEquals(DashboardMath.COLOR_DANGER_RED, DashboardMath.getSpeedColorHex(130.0f))
        assertEquals(DashboardMath.COLOR_DANGER_RED, DashboardMath.getSpeedColorHex(220.0f))
        assertEquals(DashboardMath.COLOR_NORMAL_WHITE, DashboardMath.getSpeedColorHex(Float.NaN))
    }

    @Test
    fun `getMaskOpacity returns 0_55 normal and 0_82 sunlight`() {
        assertEquals(0.55f, DashboardMath.getMaskOpacity(false), 0.0001f)
        assertEquals(0.82f, DashboardMath.getMaskOpacity(true), 0.0001f)
    }

    @Test
    fun `formatSpeedText shows dashes on timeout and rounded integer otherwise`() {
        val timedOut = FilteredSpeedState(
            displaySpeedKmh = 88.4f,
            roundedSpeedKmh = 88,
            rawSpeedKmh = 88.4f,
            accuracyMeters = 10f,
            status = GpsSignalStatus.RED,
            isTimedOut = true,
            isZeroDebounced = false,
            isAccuracyRejected = false,
            effectiveBearingDegrees = 0f,
            dynamicZoom = 15.0,
            lastUpdateTimestampMs = 1000L
        )
        assertEquals("--", DashboardMath.formatSpeedText(timedOut))

        val active = timedOut.copy(isTimedOut = false, status = GpsSignalStatus.GREEN, roundedSpeedKmh = 112)
        assertEquals("112", DashboardMath.formatSpeedText(active))

        val zero = active.copy(roundedSpeedKmh = 0)
        assertEquals("0", DashboardMath.formatSpeedText(zero))
    }

    @Test
    fun `normalizeAngleDelta computes shortest signed path across 0 and 360`() {
        assertEquals(90f, DashboardMath.normalizeAngleDelta(0f, 90f), 0.001f)
        assertEquals(-90f, DashboardMath.normalizeAngleDelta(90f, 0f), 0.001f)
        assertEquals(20f, DashboardMath.normalizeAngleDelta(350f, 10f), 0.001f)
        assertEquals(-20f, DashboardMath.normalizeAngleDelta(10f, 350f), 0.001f)
        assertEquals(180f, DashboardMath.normalizeAngleDelta(0f, 180f), 0.001f)
        assertEquals(20f, DashboardMath.normalizeAngleDelta(-10f, 370f), 0.001f)
        assertEquals(0f, DashboardMath.normalizeAngleDelta(Float.NaN, 10f), 0.001f)
        assertEquals(0f, DashboardMath.normalizeAngleDelta(10f, Float.POSITIVE_INFINITY), 0.001f)
    }

    @Test
    fun `normalizeAngleDelta stays finite within -180 to 180 on extreme finite float bounds`() {
        val pairs = listOf(
            -Float.MAX_VALUE to Float.MAX_VALUE,
            Float.MAX_VALUE to -Float.MAX_VALUE,
            -2e38f to 2e38f,
            180f to 180f
        )
        for ((fromDeg, toDeg) in pairs) {
            val delta = DashboardMath.normalizeAngleDelta(fromDeg, toDeg)
            assertTrue("Expected finite delta for ($fromDeg, $toDeg), got $delta", delta.isFinite())
            assertTrue("Expected delta in [-180, 180], got $delta", delta in -180f..180f)
        }
        assertEquals(0, java.lang.Float.floatToRawIntBits(DashboardMath.normalizeAngleDelta(-0.0f, 0.0f)))
    }
}
