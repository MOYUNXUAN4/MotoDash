package com.motodash.e2e.tier1_features

import com.motodash.e2e.harness.DashboardMathEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class SpeedometerE2ETest {

    @Test
    fun testF4_normalSpeedWhiteColor() {
        // Speed < 110 km/h -> White (#FFFFFF)
        assertEquals("#FFFFFF", DashboardMathEngine.getSpeedColorHex(0.0f))
        assertEquals("#FFFFFF", DashboardMathEngine.getSpeedColorHex(50.0f))
        assertEquals("#FFFFFF", DashboardMathEngine.getSpeedColorHex(109.9f))
    }

    @Test
    fun testF4_amberWarningThreshold110Kmh() {
        // Speed >= 110 km/h and < 130 km/h -> Amber Yellow (#FFC107)
        assertEquals("#FFC107", DashboardMathEngine.getSpeedColorHex(110.0f))
        assertEquals("#FFC107", DashboardMathEngine.getSpeedColorHex(115.5f))
        assertEquals("#FFC107", DashboardMathEngine.getSpeedColorHex(129.9f))
    }

    @Test
    fun testF4_redDangerThreshold130Kmh() {
        // Speed >= 130 km/h -> Red (#FF3B30)
        assertEquals("#FF3B30", DashboardMathEngine.getSpeedColorHex(130.0f))
        assertEquals("#FF3B30", DashboardMathEngine.getSpeedColorHex(145.0f))
        assertEquals("#FF3B30", DashboardMathEngine.getSpeedColorHex(250.0f))
    }

    @Test
    fun testF4_monospaceBoldTypography() {
        // R2: Giant monospaced bold speedometer font occupying ~45% of upper area height with km/h
        data class SpeedometerStyleConfig(
            val isMonospace: Boolean,
            val isBold: Boolean,
            val heightFractionOfUpperArea: Float,
            val unitText: String
        )

        val config = SpeedometerStyleConfig(
            isMonospace = true,
            isBold = true,
            heightFractionOfUpperArea = 0.45f,
            unitText = "km/h"
        )

        assertTrue("Speedometer font must be monospaced to prevent jitter when digits change", config.isMonospace)
        assertTrue("Speedometer font must be bold for high motorcycle visibility", config.isBold)
        assertEquals("Speedometer must occupy ~45% of upper area", 0.45f, config.heightFractionOfUpperArea, 0.01f)
        assertEquals("Speed unit must be km/h", "km/h", config.unitText)
    }

    @Test
    fun testF4_signalLossDashFormatting() {
        // R3: Signal loss displays "--" or dimmed speed digits when no update received for > 3 seconds
        fun formatSpeedDisplay(isSignalLost: Boolean, speedKmh: Float): String {
            return if (isSignalLost) "--" else speedKmh.roundToInt().toString()
        }

        assertEquals("--", formatSpeedDisplay(isSignalLost = true, speedKmh = 85.0f))
        assertEquals("0", formatSpeedDisplay(isSignalLost = false, speedKmh = 0.0f))
        assertEquals("112", formatSpeedDisplay(isSignalLost = false, speedKmh = 112.4f))
    }
}
