package com.motodash.location

enum class GpsSignalStatus {
    GREEN,  // Valid accuracy <= 15.0m
    YELLOW, // Valid accuracy > 15.0m and <= 50.0m
    RED     // Accuracy > 50.0m, invalid accuracy (<= 0f / non-finite), or timeout > 3000ms
}

data class LocationReading(
    val speedMps: Float,
    val accuracyMeters: Float,
    val bearingDegrees: Float,
    val hasBearing: Boolean,
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long
)

data class FilteredSpeedState(
    val displaySpeedKmh: Float,
    val roundedSpeedKmh: Int,
    val rawSpeedKmh: Float,
    val accuracyMeters: Float,
    val status: GpsSignalStatus,
    val isTimedOut: Boolean,
    val isZeroDebounced: Boolean,
    val isAccuracyRejected: Boolean,
    val effectiveBearingDegrees: Float,
    val dynamicZoom: Double,
    val lastUpdateTimestampMs: Long
)
