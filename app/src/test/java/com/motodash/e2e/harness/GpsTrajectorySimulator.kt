package com.motodash.e2e.harness

class GpsTrajectorySimulator(private var currentTimestampMs: Long = 1000L) {

    fun createSample(
        speedKmh: Float,
        accuracyMeters: Float = 5.0f,
        advanceMs: Long = 1000L
    ): LocationSample {
        currentTimestampMs += advanceMs
        return LocationSample(
            speedMps = speedKmh / 3.6f,
            accuracyMeters = accuracyMeters,
            timestampMs = currentTimestampMs
        )
    }

    fun simulateStationary(seconds: Int, accuracyMeters: Float = 5.0f): List<LocationSample> {
        require(seconds > 0) { "seconds must be > 0" }
        return (1..seconds).map {
            createSample(speedKmh = 0.0f, accuracyMeters = accuracyMeters)
        }
    }

    fun simulateStopDebounce(
        seconds: Int,
        speedKmh: Float = 1.8f,
        accuracyMeters: Float = 5.0f
    ): List<LocationSample> {
        require(seconds > 0) { "seconds must be > 0" }
        return (1..seconds).map {
            createSample(speedKmh = speedKmh, accuracyMeters = accuracyMeters)
        }
    }

    fun simulateAcceleration(
        fromKmh: Float,
        toKmh: Float,
        seconds: Int,
        accuracyMeters: Float = 5.0f
    ): List<LocationSample> {
        require(seconds > 0) { "seconds must be > 0" }
        val step = (toKmh - fromKmh) / seconds
        return (1..seconds).map { i ->
            createSample(speedKmh = fromKmh + (step * i), accuracyMeters = accuracyMeters)
        }
    }

    fun simulateDeceleration(
        fromKmh: Float,
        toKmh: Float,
        seconds: Int,
        accuracyMeters: Float = 5.0f
    ): List<LocationSample> {
        require(seconds > 0) { "seconds must be > 0" }
        val step = (fromKmh - toKmh) / seconds
        return (1..seconds).map { i ->
            createSample(speedKmh = fromKmh - (step * i), accuracyMeters = accuracyMeters)
        }
    }

    fun simulateCruising(speedKmh: Float, seconds: Int, accuracyMeters: Float = 5.0f): List<LocationSample> {
        require(seconds > 0) { "seconds must be > 0" }
        return (1..seconds).map {
            createSample(speedKmh = speedKmh, accuracyMeters = accuracyMeters)
        }
    }

    fun advanceTimeWithoutLocation(millis: Long): Long {
        currentTimestampMs += millis
        return currentTimestampMs
    }

    fun getCurrentTimestamp(): Long = currentTimestampMs
}
