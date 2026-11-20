package com.motodash.location

object DashboardMath {
    private const val ZOOM_LOW_SPEED = 16.5
    private const val ZOOM_HIGH_SPEED = 14.5
    private const val SPEED_ZOOM_MIN_KMH = 20.0f
    private const val SPEED_ZOOM_MAX_KMH = 100.0f

    private const val SPEED_AMBER_THRESHOLD_KMH = 110.0f
    private const val SPEED_RED_THRESHOLD_KMH = 130.0f

    const val COLOR_NORMAL_WHITE: Long = 0xFFFFFFFFL
    const val COLOR_WARNING_AMBER: Long = 0xFFFFC107L
    const val COLOR_DANGER_RED: Long = 0xFFFF3B30L

    const val MASK_OPACITY_NORMAL: Float = 0.55f
    const val MASK_OPACITY_SUNLIGHT: Float = 0.82f

    fun calculateDynamicZoom(speedKmh: Float): Double {
        if (speedKmh.isNaN() || speedKmh <= SPEED_ZOOM_MIN_KMH) return ZOOM_LOW_SPEED
        if (speedKmh >= SPEED_ZOOM_MAX_KMH) return ZOOM_HIGH_SPEED
        val fraction = (speedKmh - SPEED_ZOOM_MIN_KMH) / (SPEED_ZOOM_MAX_KMH - SPEED_ZOOM_MIN_KMH)
        return ZOOM_LOW_SPEED - (fraction * (ZOOM_LOW_SPEED - ZOOM_HIGH_SPEED))
    }

    fun getSpeedColorHex(speedKmh: Float): Long {
        if (!speedKmh.isFinite()) return COLOR_NORMAL_WHITE
        return when {
            speedKmh >= SPEED_RED_THRESHOLD_KMH -> COLOR_DANGER_RED
            speedKmh >= SPEED_AMBER_THRESHOLD_KMH -> COLOR_WARNING_AMBER
            else -> COLOR_NORMAL_WHITE
        }
    }

    fun getMaskOpacity(sunlightMode: Boolean): Float {
        return if (sunlightMode) MASK_OPACITY_SUNLIGHT else MASK_OPACITY_NORMAL
    }

    fun formatSpeedText(state: FilteredSpeedState): String {
        return if (state.isTimedOut) "--" else state.roundedSpeedKmh.coerceAtLeast(0).toString()
    }

    fun normalizeAngleDelta(fromDeg: Float, toDeg: Float): Float {
        if (!fromDeg.isFinite() || !toDeg.isFinite()) return 0f
        var delta = normalizeBearing(toDeg) - normalizeBearing(fromDeg)
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return if (delta == -0.0f) 0f else delta
    }

    fun normalizeBearing(degrees: Float): Float {
        if (!degrees.isFinite()) return 0f
        val normalized = ((degrees % 360f) + 360f) % 360f
        return if (normalized >= 360f || normalized == -0.0f) 0f else normalized
    }
}
