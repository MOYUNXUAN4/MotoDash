package com.motodash.unit

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.motodash.telemetry.TripRecorder
import com.motodash.telemetry.TripStorage
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripStorageTest {
    @get:Rule val folder = TemporaryFolder()
    private fun context(): Context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
        override fun getFilesDir() = folder.root
    }

    @Test
    fun `interrupted trip recovers checkpoint without adding relaunch downtime`() {
        val app = context()
        val recorder = TripRecorder()
        recorder.start(1000L)
        recorder.addSample(2000L, 36f, 37.0, -122.0, 5f)
        recorder.addSample(3000L, 36f, 37.00009, -122.0, 5f)
        val checkpoint = recorder.snapshot(4000L)
        assertTrue(recorder.isRecording)
        TripStorage(app).save(checkpoint)
        val recovered = TripStorage(app).read()!!
        assertEquals(3000L, recovered.durationMs)
        assertEquals(checkpoint.distanceKm, recovered.distanceKm, 0.000001)
        assertEquals(checkpoint.chart, recovered.chart)
    }

    @Test
    fun `newer finished summary replaces checkpoint`() {
        val app = context()
        val recorder = TripRecorder()
        recorder.start(0L)
        val storage = TripStorage(app)
        storage.save(recorder.snapshot(1000L))
        storage.save(recorder.stop(6000L))
        assertEquals(6000L, storage.read()!!.durationMs)
    }
}
