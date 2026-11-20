package com.motodash.telemetry

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class TripOdometer(
    private val maxJumpMeters: Double = DEFAULT_MAX_JUMP_METERS
) {
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastTimestampMs: Long? = null
    private var lastSpeedKmh = 0f

    @Volatile
    var distanceMeters: Double = 0.0
        private set

    val distanceKm: Double
        get() = distanceMeters / 1000.0

    @Synchronized
    fun addLocation(lat: Double, lon: Double, accuracyMeters: Float, speedKmh: Float, timestampMs: Long? = null) {
        if (!isValidCoordinate(lat, lon)) return
        if (!accuracyMeters.isFinite() || accuracyMeters <= 0.0f || accuracyMeters > MAX_ACCURACY_METERS) return
        if (!speedKmh.isFinite() || speedKmh < MIN_MOVING_SPEED_KMH) return

        val prevLat = lastLat
        val prevLon = lastLon
        if (prevLat == null || prevLon == null) {
            lastLat = lat
            lastLon = lon
            lastTimestampMs = timestampMs
            lastSpeedKmh = speedKmh
            return
        }

        val segmentMeters = haversineMeters(prevLat, prevLon, lat, lon)
        if (!segmentMeters.isFinite()) return

        val previousTime = lastTimestampMs
        val elapsed = if (timestampMs != null && previousTime != null) timestampMs - previousTime else null
        if (elapsed != null && elapsed <= 0L) return
        val plausibleDistance = if (elapsed != null) {
            // Allow measurement noise and acceleration, but never bridge a missing GPS interval.
            maxOf(speedKmh, lastSpeedKmh) / 3.6 * (elapsed / 1000.0) * 1.8 + 2 * accuracyMeters
        } else maxJumpMeters
        if ((elapsed == null || elapsed <= TripRecorder.MAX_GAP_MS) &&
            segmentMeters <= minOf(maxJumpMeters, plausibleDistance)) {
            distanceMeters += segmentMeters
        }
        // Re-anchor cleanly on valid moving fix (skips inflating odometer on unrealistic jumps)
        lastLat = lat
        lastLon = lon
        lastTimestampMs = timestampMs
        lastSpeedKmh = speedKmh
    }

    @Synchronized
    fun breakSegment() {
        lastLat = null
        lastLon = null
        lastTimestampMs = null
        lastSpeedKmh = 0f
    }

    @Synchronized
    fun reset() {
        distanceMeters = 0.0
        breakSegment()
    }

    fun formatTripText(): String = String.format(Locale.US, "Trip %.1f km", distanceKm)

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
    }

    companion object {
        const val MAX_ACCURACY_METERS = 20.0f
        const val MIN_MOVING_SPEED_KMH = 2.5f
        const val DEFAULT_MAX_JUMP_METERS = 10_000.0
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            if (lat1 == lat2 && lon1 == lon2) return 0.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val radLat1 = Math.toRadians(lat1)
            val radLat2 = Math.toRadians(lat2)
            val a = sin(dLat / 2.0).pow(2) +
                cos(radLat1) * cos(radLat2) * sin(dLon / 2.0).pow(2)
            val c = 2.0 * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
            return EARTH_RADIUS_METERS * c
        }
    }
}
