package com.motodash.unit

import com.motodash.telemetry.TripRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRecorderTest {

    @Test
    fun `ignores samples before start and tracks max avg moving time and stops`() {
        val recorder = TripRecorder()
        recorder.addSample(500, 80f, 25.0, 121.0, 5f)
        assertFalse(recorder.isRecording)

        recorder.start(0)
        assertTrue(recorder.isRecording)

        // 1 Hz samples: rest -> accelerate to 90 -> stop
        val speeds = listOf(0f, 20f, 50f, 90f, 60f, 0f, 0f)
        var lat = 25.0
        speeds.forEachIndexed { i, s ->
            lat += if (s > 2.5f) 0.0003 else 0.0
            recorder.addSample((i + 1) * 1000L, s, lat, 121.0, 5f)
        }
        val summary = recorder.stop(8000)

        assertFalse(recorder.isRecording)
        assertEquals(90f, summary.maxSpeedKmh, 0.001f)
        assertEquals(4000L, summary.maxSpeedAtMs)
        assertEquals(8000L, summary.durationMs)
        assertEquals(4000L, summary.movingTimeMs)
        assertEquals(1, summary.stopCount)
        assertEquals(7, summary.chart.size)
        assertTrue(summary.distanceKm > 0.0)
        assertTrue(summary.avgMovingSpeedKmh > summary.avgSpeedKmh)
    }

    @Test
    fun `records best zero to sixty sprint and null when never reached`() {
        val slow = TripRecorder()
        slow.start(0)
        slow.addSample(1000, 0f, 25.0, 121.0, 5f)
        slow.addSample(2000, 30f, 25.0, 121.0, 5f)
        assertNull(slow.stop(3000).bestZeroTo60Ms)

        val fast = TripRecorder()
        fast.start(0)
        fast.addSample(1000, 0f, 25.0, 121.0, 5f)
        fast.addSample(3000, 40f, 25.0, 121.0, 5f)
        fast.addSample(5000, 65f, 25.0, 121.0, 5f)
        val summary = fast.stop(6000)
        assertNotNull(summary.bestZeroTo60Ms)
        assertEquals(4000L, summary.bestZeroTo60Ms)
    }

    @Test
    fun `downsamples long trips to chart size while preserving the peak`() {
        val recorder = TripRecorder(chartPoints = 50)
        recorder.start(0)
        for (i in 1..1000) {
            val speed = if (i == 437) 142f else 40f + (i % 10)
            recorder.addSample(i * 500L, speed, 25.0, 121.0, 5f)
        }
        val summary = recorder.stop(600_000)
        assertEquals(50, summary.chart.size)
        assertEquals(142f, summary.chart.maxOf { it.speedKmh }, 0.001f)
        assertEquals(142f, summary.maxSpeedKmh, 0.001f)
    }

    @Test
    fun `reset clears an unfinished recording`() {
        val recorder = TripRecorder()
        recorder.start(0)
        recorder.addSample(1000, 50f, 25.0, 121.0, 5f)
        recorder.reset()
        assertFalse(recorder.isRecording)
        assertEquals(0.0, recorder.distanceKm, 0.0)
        assertEquals("Trip 0.0 km", recorder.formatTripText())
    }
}
