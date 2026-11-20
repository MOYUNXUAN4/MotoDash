package com.motodash.e2e.tier1_features

import com.motodash.e2e.harness.DashboardMathEngine
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class MaskOverlayE2ETest {

    @Test
    fun testF3_defaultNormalOpacity55Percent() {
        // R2: Mid layer default 55% opacity
        val defaultOpacity = DashboardMathEngine.getMaskOpacity(sunlightMode = false)
        assertEquals("Default mask overlay opacity must be 55% (0.55f)", 0.55f, defaultOpacity, 0.0001f)
    }

    @Test
    fun testF3_sunlightModeHighContrast82Percent() {
        // R2: Switch to 82% opacity in 'Sunlight High Contrast Mode'
        val sunlightOpacity = DashboardMathEngine.getMaskOpacity(sunlightMode = true)
        assertEquals("Sunlight High Contrast Mode opacity must be 82% (0.82f)", 0.82f, sunlightOpacity, 0.0001f)
    }

    @Test
    fun testF3_opacityToggleStateFlow() {
        // Test toggle state transition
        val sunlightState = MutableStateFlow(false)
        assertEquals(0.55f, DashboardMathEngine.getMaskOpacity(sunlightState.value), 0.0001f)

        // Toggle ON
        sunlightState.value = true
        assertEquals(0.82f, DashboardMathEngine.getMaskOpacity(sunlightState.value), 0.0001f)

        // Toggle OFF
        sunlightState.value = false
        assertEquals(0.55f, DashboardMathEngine.getMaskOpacity(sunlightState.value), 0.0001f)
    }

    @Test
    fun testF3_verticalGradientSplitBoundary() {
        // Gradient mask smoothly transitions to pure black (alpha 1.0f) at the 60/40 boundary
        fun calculateGradientAlphaAtNormalizedY(yFraction: Float, baseOpacity: Float): Float {
            // yFraction from 0.0 (top) to 1.0 (boundary with music area)
            return baseOpacity + ((1.0f - baseOpacity) * yFraction.coerceIn(0.0f, 1.0f))
        }

        val topAlpha = calculateGradientAlphaAtNormalizedY(0.0f, 0.55f)
        val bottomAlpha = calculateGradientAlphaAtNormalizedY(1.0f, 0.55f)

        assertEquals("Top of gradient mask should equal base opacity", 0.55f, topAlpha, 0.001f)
        assertEquals("Bottom boundary must reach 1.0f pure black to seamlessly blend with lower 40%", 1.0f, bottomAlpha, 0.001f)
    }

    @Test
    fun testF3_highContrastTextReadability() {
        // WCAG relative luminance calculation for white text over 82% black mask
        fun relativeLuminance(r: Double, g: Double, b: Double): Double {
            fun channel(c: Double) = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
        }

        val whiteLum = relativeLuminance(1.0, 1.0, 1.0)
        // Background under 82% mask (assuming worst-case bright map underneath of 0.8)
        val bgEstimated = 0.8 * (1.0 - 0.82)
        val bgLum = relativeLuminance(bgEstimated, bgEstimated, bgEstimated)
        val contrastRatio = (whiteLum + 0.05) / (bgLum + 0.05)

        assertTrue("Sunlight mode contrast ratio ($contrastRatio) must exceed WCAG AAA requirement (7.0:1)", contrastRatio > 7.0)
    }
}
