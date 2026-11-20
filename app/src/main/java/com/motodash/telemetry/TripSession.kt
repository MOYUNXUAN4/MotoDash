package com.motodash.telemetry

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.motodash.location.GpsSpeedFilter
import com.motodash.location.LocationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TripSessionState(val recording: Boolean = false, val tripText: String = "Trip 0.0 km", val elapsedMs: Long = 0L)

/** One application-scoped tracker shared by the dashboard and foreground recording service. */
class TripSession private constructor(context: Context) {
    private val app = context.applicationContext
    val locationTracker = LocationTracker(app, speedFilter = GpsSpeedFilter(decelAlpha = 0.85f), preferNativeGps = true)
    private val recorder = TripRecorder()
    private val storage = TripStorage(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val checkpoints = Channel<TripSummary>(Channel.CONFLATED)
    private val _state = MutableStateFlow(TripSessionState())
    val state = _state.asStateFlow()
    private val _summary = MutableStateFlow(storage.read())
    val summary = _summary.asStateFlow()
    private var dashboardVisible = false
    private var serviceRequested = false

    init {
        scope.launch(Dispatchers.IO) { for (snapshot in checkpoints) storage.save(snapshot) }
        scope.launch {
            locationTracker.latestReading.collect { reading ->
                if (reading != null && recorder.isRecording) {
                    // Statistics use each GPS sample's own speed, independent of display smoothing.
                    recorder.addSample(reading.timestampMs, reading.speedMps * 3.6f,
                        reading.latitude, reading.longitude, reading.accuracyMeters)
                    publish()
                }
            }
        }
        scope.launch {
            var ticks = 0
            while (true) {
                delay(1_000L)
                if (recorder.isRecording) {
                    if (!locationTracker.hasLocationPermission()) {
                        finish()
                        app.stopService(Intent(app, TripRecordingService::class.java))
                        continue
                    }
                    publish()
                    if (++ticks % 5 == 0) checkpoints.trySend(recorder.snapshot(SystemClock.elapsedRealtime()))
                }
            }
        }
    }

    fun setDashboardVisible(visible: Boolean) {
        dashboardVisible = visible
        updateTracking()
    }

    fun toggle(): Boolean {
        if (recorder.isRecording) {
            finish()
            app.stopService(Intent(app, TripRecordingService::class.java))
            return true
        }
        if (serviceRequested || !dashboardVisible || !locationTracker.hasLocationPermission()) return false
        return try {
            serviceRequested = true
            ContextCompat.startForegroundService(app, Intent(app, TripRecordingService::class.java))
            true
        } catch (error: RuntimeException) {
            serviceRequested = false
            android.util.Log.w("TripSession", "Recording service could not start", error)
            false
        }
    }

    /** Called only once the service has successfully entered the foreground. */
    fun beginFromService() {
        serviceRequested = false
        if (recorder.isRecording) return
        _summary.value = null
        recorder.start(SystemClock.elapsedRealtime())
        checkpoints.trySend(recorder.snapshot(SystemClock.elapsedRealtime()))
        updateTracking()
        publish()
    }

    fun finish() {
        serviceRequested = false
        if (recorder.isRecording) {
            val summary = recorder.stop(SystemClock.elapsedRealtime())
            _summary.value = summary
            checkpoints.trySend(summary)
        }
        updateTracking()
        publish()
    }

    fun serviceStopped() { serviceRequested = false; finish() }
    fun dismissSummary() { _summary.value = null }
    fun reset() { if (!recorder.isRecording) { recorder.reset(); publish() } }

    private fun updateTracking() {
        if ((dashboardVisible || recorder.isRecording) && locationTracker.hasLocationPermission()) {
            locationTracker.startTracking()
        } else locationTracker.stopTracking()
    }

    private fun publish() {
        _state.value = TripSessionState(recorder.isRecording, recorder.formatTripText(), recorder.elapsedMs(SystemClock.elapsedRealtime()))
    }

    companion object {
        @Volatile private var instance: TripSession? = null
        fun get(context: Context): TripSession = instance ?: synchronized(this) {
            instance ?: TripSession(context).also { instance = it }
        }
    }
}
