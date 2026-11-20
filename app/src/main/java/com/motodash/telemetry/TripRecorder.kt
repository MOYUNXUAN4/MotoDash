package com.motodash.telemetry

data class TripSample(val offsetMs: Long, val speedKmh: Float)

data class TripSummary(
    val durationMs: Long,
    val movingTimeMs: Long,
    val distanceKm: Double,
    val maxSpeedKmh: Float,
    val maxSpeedAtMs: Long,
    val avgSpeedKmh: Float,
    val avgMovingSpeedKmh: Float,
    val bestZeroTo60Ms: Long?,
    val stopCount: Int,
    val chart: List<TripSample>
)

/**
 * Records a manually started/stopped trip: distance, speed curve, max / average speed,
 * moving time, stop count and best 0→60 km/h sprint.
 */
class TripRecorder(
    private val maxStoredSamples: Int = 20_000,
    private val chartPoints: Int = 240
) {
    private val odometer = TripOdometer()
    private var samples = ArrayList<TripSample>()

    @Volatile
    var isRecording: Boolean = false
        private set

    private var startMs = 0L
    private var lastTimeMs = 0L
    private var movingTimeMs = 0L
    private var maxSpeed = 0f
    private var maxSpeedAtMs = 0L
    private var wasMoving = false
    private var stopCount = 0
    private var restSinceMs: Long? = null
    private var bestZeroTo60Ms: Long? = null

    val distanceKm: Double get() = odometer.distanceKm

    fun formatTripText(): String = odometer.formatTripText()

    @Synchronized
    fun elapsedMs(nowMs: Long): Long = if (isRecording) (nowMs - startMs).coerceAtLeast(0L) else 0L

    @Synchronized
    fun start(nowMs: Long) {
        resetInternal()
        isRecording = true
        startMs = nowMs
        lastTimeMs = nowMs
        restSinceMs = nowMs
    }

    @Synchronized
    fun addSample(nowMs: Long, speedKmh: Float, lat: Double, lon: Double, accuracyMeters: Float) {
        if (!isRecording) return
        if (nowMs < lastTimeMs) return
        if (!accuracyMeters.isFinite() || accuracyMeters <= 0f || accuracyMeters > TripOdometer.MAX_ACCURACY_METERS) {
            odometer.breakSegment()
            restSinceMs = null
            wasMoving = false
            lastTimeMs = nowMs
            return
        }
        val speed = if (speedKmh.isFinite()) speedKmh.coerceAtLeast(0f) else 0f
        val dt = nowMs - lastTimeMs
        if (dt > MAX_GAP_MS) {
            odometer.breakSegment()
            restSinceMs = null
            wasMoving = false
        }
        val moving = speed >= MOVING_THRESHOLD_KMH

        if (dt in 1..MAX_GAP_MS && moving) movingTimeMs += dt
        if (wasMoving && !moving) stopCount++
        wasMoving = moving

        odometer.addLocation(lat, lon, accuracyMeters, speed, nowMs)

        // Best 0 → 60 km/h sprint (last moment at rest until crossing 60)
        if (speed < REST_THRESHOLD_KMH) {
            restSinceMs = nowMs
        } else if (speed >= SPRINT_TARGET_KMH) {
            val from = restSinceMs
            if (from != null) {
                val elapsed = nowMs - from
                if (elapsed in 1..MAX_SPRINT_MS) {
                    bestZeroTo60Ms = minOf(bestZeroTo60Ms ?: Long.MAX_VALUE, elapsed)
                }
                restSinceMs = null
            }
        }

        val offset = (nowMs - startMs).coerceAtLeast(0L)
        if (speed > maxSpeed) {
            maxSpeed = speed
            maxSpeedAtMs = offset
        }
        samples.add(TripSample(offset, speed))
        if (samples.size > maxStoredSamples) {
            samples = ArrayList(samples.filterIndexed { index, _ -> index % 2 == 0 })
        }
        lastTimeMs = nowMs
    }

    @Synchronized
    fun stop(nowMs: Long): TripSummary {
        val result = snapshot(nowMs)
        isRecording = false
        return result
    }

    /** A non-destructive checkpoint; recovery ends at this timestamp, never at relaunch time. */
    @Synchronized
    fun snapshot(nowMs: Long): TripSummary {
        val duration = (maxOf(nowMs, lastTimeMs) - startMs).coerceAtLeast(0L)
        val hours = duration / 3_600_000.0
        val movingHours = movingTimeMs / 3_600_000.0
        val distance = odometer.distanceKm
        return TripSummary(
            durationMs = duration,
            movingTimeMs = movingTimeMs,
            distanceKm = distance,
            maxSpeedKmh = maxSpeed,
            maxSpeedAtMs = maxSpeedAtMs,
            avgSpeedKmh = if (hours > 0.0) (distance / hours).toFloat() else 0f,
            avgMovingSpeedKmh = if (movingHours > 0.0) (distance / movingHours).toFloat() else 0f,
            bestZeroTo60Ms = bestZeroTo60Ms,
            stopCount = stopCount,
            chart = downsample(samples)
        )
    }

    @Synchronized
    fun reset() {
        isRecording = false
        resetInternal()
    }

    private fun resetInternal() {
        odometer.reset()
        samples = ArrayList()
        movingTimeMs = 0L
        maxSpeed = 0f
        maxSpeedAtMs = 0L
        wasMoving = false
        stopCount = 0
        restSinceMs = null
        bestZeroTo60Ms = null
    }

    private fun downsample(source: List<TripSample>): List<TripSample> {
        if (source.size <= chartPoints) return source.toList()
        val bucketSize = source.size.toDouble() / chartPoints
        val result = ArrayList<TripSample>(chartPoints)
        for (i in 0 until chartPoints) {
            val from = (i * bucketSize).toInt()
            val to = (((i + 1) * bucketSize).toInt()).coerceAtMost(source.size).coerceAtLeast(from + 1)
            val bucket = source.subList(from, to)
            val peak = bucket.maxByOrNull { it.speedKmh } ?: continue
            result.add(peak)
        }
        return result
    }

    companion object {
        const val MOVING_THRESHOLD_KMH = 2.5f
        const val REST_THRESHOLD_KMH = 3f
        const val SPRINT_TARGET_KMH = 60f
        const val MAX_SPRINT_MS = 30_000L
        const val MAX_GAP_MS = 5_000L
    }
}
