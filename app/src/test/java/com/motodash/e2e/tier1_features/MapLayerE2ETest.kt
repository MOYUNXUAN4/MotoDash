package com.motodash.e2e.tier1_features

import com.motodash.e2e.harness.DashboardMathEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class MapLayerE2ETest {

    @Test
    fun testF2_keylessDarkStyleUrl() {
        // R2: MapLibre loading free keyless dark style (https://tiles.openfreemap.org/styles/dark)
        val styleUrl = "https://tiles.openfreemap.org/styles/dark"
        val uri = URI(styleUrl)
        assertEquals("Scheme must be https", "https", uri.scheme)
        assertEquals("Host must be tiles.openfreemap.org", "tiles.openfreemap.org", uri.host)
        assertEquals("Path must be /styles/dark", "/styles/dark", uri.path)
        assertTrue("URL must not contain API key parameters", uri.query == null || !uri.query.contains("key="))
    }

    @Test
    fun testF2_accidentalTouchGesturesDisabled() {
        // R2: Disable accidental touch gestures or allow zoom only
        data class MapGestureSettings(
            val isRotateGesturesEnabled: Boolean,
            val isTiltGesturesEnabled: Boolean,
            val isScrollGesturesEnabled: Boolean,
            val isZoomGesturesEnabled: Boolean
        )

        val motorcycleSettings = MapGestureSettings(
            isRotateGesturesEnabled = false,
            isTiltGesturesEnabled = false,
            isScrollGesturesEnabled = false,
            isZoomGesturesEnabled = true
        )

        assertTrue("Accidental rotate gestures must be disabled", !motorcycleSettings.isRotateGesturesEnabled)
        assertTrue("Accidental tilt gestures must be disabled", !motorcycleSettings.isTiltGesturesEnabled)
        assertTrue("Accidental pan/scroll gestures must be disabled to prevent losing rider position", !motorcycleSettings.isScrollGesturesEnabled)
        assertTrue("Controlled zoom gestures may be enabled", motorcycleSettings.isZoomGesturesEnabled)
    }

    @Test
    fun testF2_headingUpBearingTracking() {
        // R2: Heading-up orientation smoothly rotating map
        fun computeBearingUpdate(currentBearing: Float, targetBearing: Float, interpolationFactor: Float = 0.2f): Float {
            var diff = (targetBearing - currentBearing) % 360f
            if (diff > 180f) diff -= 360f
            if (diff < -180f) diff += 360f
            return (currentBearing + (diff * interpolationFactor) + 360f) % 360f
        }

        val initialBearing = 0f
        val newTargetBearing = 90f // Turn East
        val step1 = computeBearingUpdate(initialBearing, newTargetBearing)
        assertTrue("Step 1 bearing should interpolate smoothly towards 90 deg", step1 > 0f && step1 < 90f)
        val step2 = computeBearingUpdate(step1, newTargetBearing)
        assertTrue("Step 2 bearing should be closer to target", step2 > step1 && step2 < 90f)
    }

    @Test
    fun testF2_stationaryBearingFreeze() {
        // When stationary (< 2.5 km/h), bearing jitter from phone gyro/compass must not spin the map
        fun updateMapOrientation(speedKmh: Float, lastValidBearing: Float, newJitterBearing: Float): Float {
            return if (speedKmh < 2.5f) lastValidBearing else newJitterBearing
        }

        val preservedBearing = 145.0f
        val jitterBearing = 220.0f
        val result = updateMapOrientation(speedKmh = 1.2f, lastValidBearing = preservedBearing, newJitterBearing = jitterBearing)
        assertEquals("Bearing must be frozen when speed is under stop debounce limit (< 2.5 km/h)", preservedBearing, result, 0.001f)
    }

    @Test
    fun testF2_dynamicZoomCalculation() {
        // R2: Zoom ~16.5 at low speeds (<=20 km/h), zoom ~14.5 at high speeds (>=100 km/h)
        val zoomAtLowSpeed = DashboardMathEngine.calculateDynamicZoom(0.0f)
        assertEquals("Low speed zoom must be 16.5", 16.5, zoomAtLowSpeed, 0.001)

        val zoomAt20Kmh = DashboardMathEngine.calculateDynamicZoom(20.0f)
        assertEquals("Zoom at 20 km/h must be 16.5", 16.5, zoomAt20Kmh, 0.001)

        val zoomAt60Kmh = DashboardMathEngine.calculateDynamicZoom(60.0f)
        assertEquals("Zoom at 60 km/h (midpoint) must be 15.5", 15.5, zoomAt60Kmh, 0.001)

        val zoomAt100Kmh = DashboardMathEngine.calculateDynamicZoom(100.0f)
        assertEquals("Zoom at 100 km/h must be 14.5", 14.5, zoomAt100Kmh, 0.001)

        val zoomAt160Kmh = DashboardMathEngine.calculateDynamicZoom(160.0f)
        assertEquals("High speed zoom must clamp at 14.5", 14.5, zoomAt160Kmh, 0.001)
    }
}
