package com.motodash.location

import kotlin.math.roundToInt

/**
 * Filters raw GPS speed samples for motorcycle HUD display:
 * - Converts m/s -> km/h (`* 3.6f`).
 * - Rejects fixes with invalid (`<= 0f` or non-finite) or poor (`> maxAccuracyMeters`) accuracy.
 * - Debounces stationary jitter (`< stopThresholdKmh`) to `0 km/h` and freezes map bearing.
 * - Applies exponential moving average (`alpha`) with direct initialization on first valid moving sample
 *   and resets EMA state (`hasFirstSample = false`, `smoothedSpeedKmh = 0f`) on rejection, stop debounce,
 *   timeout, or [reset].
 * - Flags signal timeout when `elapsed > signalTimeoutMs` or before any sample has been received.
 */
class GpsSpeedFilter(
    private val alpha: Float = 0.35f,
    private val stopThresholdKmh: Float = 2.5f,
    private val maxAccuracyMeters: Float = 20.0f,
    private val signalTimeoutMs: Long = 3000L,
    /** EMA weight applied when speed is falling; higher = display drops faster when braking. */
    private val decelAlpha: Float = alpha
) {
    private var smoothedSpeedKmh: Float = 0f
    private var hasFirstSample: Boolean = false
    private var hasReceivedAnySample: Boolean = false
    private var lastUpdateTimestampMs: Long = 0L
    private var lastAccuracyMeters: Float = Float.POSITIVE_INFINITY
    private var lastEffectiveBearingDegrees: Float = 0f
    private var lastState: FilteredSpeedState? = null

    fun processSample(sample: LocationReading): FilteredSpeedState {
        // Ignore stale out-of-order samples so cached fixes neither overwrite newer speed/bearing
        // nor regress lastUpdateTimestampMs and cause false timeouts.
        if (hasReceivedAnySample && sample.timestampMs < lastUpdateTimestampMs) {
            return lastState ?: createTimedOutState(lastUpdateTimestampMs = lastUpdateTimestampMs)
        }

        hasReceivedAnySample = true
        lastUpdateTimestampMs = sample.timestampMs
        lastAccuracyMeters = sample.accuracyMeters

        val rawSpeedKmh = if (sample.speedMps.isFinite() && sample.speedMps >= 0f) {
            val converted = sample.speedMps * 3.6f
            if (converted.isFinite()) {
                if (converted == 0f) 0f else converted
            } else {
                0f
            }
        } else {
            0f
        }

        val status = evaluateStatus(sample.accuracyMeters, isTimedOut = false)

        // 1. Accuracy rejection (> 20.0m, <= 0m, or non-finite): speed 0, reset EMA, freeze bearing
        if (!sample.accuracyMeters.isFinite() ||
            sample.accuracyMeters <= 0f ||
            sample.accuracyMeters > maxAccuracyMeters
        ) {
            resetEma()
            val rejectedState = FilteredSpeedState(
                displaySpeedKmh = 0f,
                roundedSpeedKmh = 0,
                rawSpeedKmh = rawSpeedKmh,
                accuracyMeters = sample.accuracyMeters,
                status = status,
                isTimedOut = false,
                isZeroDebounced = false,
                isAccuracyRejected = true,
                effectiveBearingDegrees = lastEffectiveBearingDegrees,
                dynamicZoom = DashboardMath.calculateDynamicZoom(0f),
                lastUpdateTimestampMs = sample.timestampMs
            )
            lastState = rejectedState
            return rejectedState
        }

        // 2. Stop debounce (< 2.5 km/h): speed 0, reset EMA, freeze bearing
        if (rawSpeedKmh < stopThresholdKmh) {
            resetEma()
            val debouncedState = FilteredSpeedState(
                displaySpeedKmh = 0f,
                roundedSpeedKmh = 0,
                rawSpeedKmh = rawSpeedKmh,
                accuracyMeters = sample.accuracyMeters,
                status = status,
                isTimedOut = false,
                isZeroDebounced = true,
                isAccuracyRejected = false,
                effectiveBearingDegrees = lastEffectiveBearingDegrees,
                dynamicZoom = DashboardMath.calculateDynamicZoom(0f),
                lastUpdateTimestampMs = sample.timestampMs
            )
            lastState = debouncedState
            return debouncedState
        }

        // 3. EMA smoothing (direct initialization on first valid moving sample)
        val nextSmoothed = if (!hasFirstSample) {
            hasFirstSample = true
            rawSpeedKmh
        } else {
            val weight = if (rawSpeedKmh < smoothedSpeedKmh) decelAlpha else alpha
            (weight * rawSpeedKmh) + ((1.0f - weight) * smoothedSpeedKmh)
        }
        smoothedSpeedKmh = if (nextSmoothed.isFinite()) {
            if (nextSmoothed == 0f) 0f else nextSmoothed
        } else {
            hasFirstSample = false
            0f
        }

        // 4. Bearing update (freeze if !hasBearing or non-finite)
        if (sample.hasBearing && sample.bearingDegrees.isFinite()) {
            lastEffectiveBearingDegrees = DashboardMath.normalizeBearing(sample.bearingDegrees)
        }

        val activeState = FilteredSpeedState(
            displaySpeedKmh = smoothedSpeedKmh,
            roundedSpeedKmh = smoothedSpeedKmh.roundToInt(),
            rawSpeedKmh = rawSpeedKmh,
            accuracyMeters = sample.accuracyMeters,
            status = status,
            isTimedOut = false,
            isZeroDebounced = false,
            isAccuracyRejected = false,
            effectiveBearingDegrees = lastEffectiveBearingDegrees,
            dynamicZoom = DashboardMath.calculateDynamicZoom(smoothedSpeedKmh),
            lastUpdateTimestampMs = sample.timestampMs
        )
        lastState = activeState
        return activeState
    }

    fun checkTimeout(currentTimestampMs: Long): FilteredSpeedState {
        if (!hasReceivedAnySample) {
            return createTimedOutState(lastUpdateTimestampMs = 0L)
        }
        val effectiveTimeoutMs = signalTimeoutMs.coerceAtLeast(0L)
        val timeoutThresholdMs = if (lastUpdateTimestampMs > Long.MAX_VALUE - effectiveTimeoutMs) {
            Long.MAX_VALUE
        } else {
            lastUpdateTimestampMs + effectiveTimeoutMs
        }
        if (currentTimestampMs > timeoutThresholdMs) {
            resetEma()
            val timedOutState = createTimedOutState(lastUpdateTimestampMs = lastUpdateTimestampMs)
            lastState = timedOutState
            return timedOutState
        }
        return lastState ?: createTimedOutState(lastUpdateTimestampMs = lastUpdateTimestampMs)
    }

    fun reset() {
        resetEma()
        hasReceivedAnySample = false
        lastUpdateTimestampMs = 0L
        lastAccuracyMeters = Float.POSITIVE_INFINITY
        lastEffectiveBearingDegrees = 0f
        lastState = null
    }

    private fun resetEma() {
        smoothedSpeedKmh = 0f
        hasFirstSample = false
    }

    private fun createTimedOutState(lastUpdateTimestampMs: Long): FilteredSpeedState {
        return FilteredSpeedState(
            displaySpeedKmh = 0f,
            roundedSpeedKmh = 0,
            rawSpeedKmh = 0f,
            accuracyMeters = lastAccuracyMeters,
            status = GpsSignalStatus.RED,
            isTimedOut = true,
            isZeroDebounced = false,
            isAccuracyRejected = false,
            effectiveBearingDegrees = lastEffectiveBearingDegrees,
            dynamicZoom = DashboardMath.calculateDynamicZoom(0f),
            lastUpdateTimestampMs = lastUpdateTimestampMs
        )
    }

    private fun evaluateStatus(accuracyMeters: Float, isTimedOut: Boolean): GpsSignalStatus {
        return when {
            isTimedOut -> GpsSignalStatus.RED
            !accuracyMeters.isFinite() || accuracyMeters <= 0f -> GpsSignalStatus.RED
            accuracyMeters <= 15.0f -> GpsSignalStatus.GREEN
            accuracyMeters <= 50.0f -> GpsSignalStatus.YELLOW
            else -> GpsSignalStatus.RED
        }
    }
}
