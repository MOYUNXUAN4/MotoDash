package com.motodash.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocationTracker(
    context: Context,
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context),
    private val speedFilter: GpsSpeedFilter = GpsSpeedFilter(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val timeProvider: () -> Long = { SystemClock.elapsedRealtime() },
    private val updateIntervalMs: Long = DEFAULT_UPDATE_INTERVAL_MS,
    private val watchdogCheckIntervalMs: Long = DEFAULT_WATCHDOG_INTERVAL_MS,
    /** When true, prefer raw GNSS (Doppler speed, 2 Hz) over the smoothed fused provider for snappier speed. */
    private val preferNativeGps: Boolean = false,
    private val nativeGpsIntervalMs: Long = NATIVE_GPS_INTERVAL_MS
) {
    private val appContext = context.applicationContext ?: context
    private val locationManager: LocationManager? =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val filterLock = Any()
    private val lifecycleLock = Any()
    @Volatile
    private var nativeGpsActive = false

    private val nativeListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onLocationReceived(location)
        }

        override fun onLocationChanged(locations: MutableList<Location>) {
            for (location in locations) onLocationReceived(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private val _speedState = MutableStateFlow(
        synchronized(filterLock) {
            speedFilter.checkTimeout(timeProvider())
        }
    )
    val speedState: StateFlow<FilteredSpeedState> = _speedState.asStateFlow()

    private val _latestReading = MutableStateFlow<LocationReading?>(null)
    val latestReading: StateFlow<LocationReading?> = _latestReading.asStateFlow()

    private var trackingScope: CoroutineScope? = null
    private var watchdogJob: Job? = null

    @Volatile
    var isTracking: Boolean = false
        private set

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) {
                onLocationReceived(location)
            }
        }
    }

    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    @SuppressLint("MissingPermission")
    fun startTracking(): Boolean {
        synchronized(lifecycleLock) {
            if (!hasLocationPermission()) {
                return false
            }
            if (isTracking) {
                return true
            }

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, updateIntervalMs)
                .setMinUpdateIntervalMillis((updateIntervalMs / 2L).coerceAtLeast(100L))
                .setWaitForAccurateLocation(false)
                .build()

            return try {
                nativeGpsActive = preferNativeGps && tryStartNativeGps()
                if (!nativeGpsActive) {
                    fusedLocationClient.requestLocationUpdates(
                        request,
                        locationCallback,
                        Looper.getMainLooper()
                    )
                }
                // Seed map position quickly from cached lastLocation if no live fix yet
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { cached ->
                        if (cached != null && _latestReading.value == null) {
                            _latestReading.value = LocationReading(
                                speedMps = 0f,
                                accuracyMeters = if (cached.hasAccuracy() && cached.accuracy > 0f) {
                                    cached.accuracy
                                } else {
                                    Float.POSITIVE_INFINITY
                                },
                                bearingDegrees = if (cached.hasBearing()) cached.bearing else 0f,
                                hasBearing = cached.hasBearing(),
                                latitude = cached.latitude,
                                longitude = cached.longitude,
                                timestampMs = cached.elapsedRealtimeNanos.takeIf { it > 0L }?.div(1_000_000L) ?: timeProvider()
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Ignore in mocked/test environments where lastLocation task may be null
                }
                isTracking = true
                startWatchdog()
                true
            } catch (_: SecurityException) {
                stopWatchdog()
                isTracking = false
                false
            }
        }
    }

    fun stopTracking() {
        synchronized(lifecycleLock) {
            stopWatchdog()
            if (nativeGpsActive) {
                nativeGpsActive = false
                try {
                    locationManager?.removeUpdates(nativeListener)
                } catch (_: SecurityException) {
                    // Ignore if permission was revoked while stopping
                }
            }
            if (isTracking) {
                isTracking = false
                try {
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                } catch (_: SecurityException) {
                    // Ignore if permission was revoked while stopping
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryStartNativeGps(): Boolean {
        val lm = locationManager ?: return false
        return try {
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return false
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                nativeGpsIntervalMs,
                0f,
                nativeListener,
                Looper.getMainLooper()
            )
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun onLocationReceived(location: Location) {
        val reading = LocationReading(
            speedMps = if (location.hasSpeed()) location.speed else 0f,
            accuracyMeters = if (location.hasAccuracy() && location.accuracy > 0f) {
                location.accuracy
            } else {
                Float.POSITIVE_INFINITY
            },
            bearingDegrees = if (location.hasBearing()) location.bearing else 0f,
            hasBearing = location.hasBearing(),
            latitude = location.latitude,
            longitude = location.longitude,
            timestampMs = location.elapsedRealtimeNanos.takeIf { it > 0L }?.div(1_000_000L) ?: timeProvider()
        )
        onLocationReading(reading)
    }

    fun onLocationReading(reading: LocationReading): FilteredSpeedState {
        return synchronized(filterLock) {
            val state = speedFilter.processSample(reading)
            if (reading.timestampMs < state.lastUpdateTimestampMs) return@synchronized state
            _speedState.value = state
            _latestReading.value = reading
            state
        }
    }

    fun checkTimeoutNow(): FilteredSpeedState {
        return synchronized(filterLock) {
            val state = speedFilter.checkTimeout(timeProvider())
            _speedState.value = state
            state
        }
    }

    fun reset() {
        synchronized(filterLock) {
            speedFilter.reset()
            _latestReading.value = null
            _speedState.value = speedFilter.checkTimeout(timeProvider())
        }
    }

    private fun startWatchdog() {
        synchronized(lifecycleLock) {
            stopWatchdog()
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            trackingScope = scope
            watchdogJob = scope.launch {
                while (isActive) {
                    delay(watchdogCheckIntervalMs)
                    checkTimeoutNow()
                }
            }
        }
    }

    private fun stopWatchdog() {
        synchronized(lifecycleLock) {
            watchdogJob?.cancel()
            watchdogJob = null
            trackingScope?.cancel()
            trackingScope = null
        }
    }

    companion object {
        const val DEFAULT_UPDATE_INTERVAL_MS = 1000L
        const val DEFAULT_WATCHDOG_INTERVAL_MS = 500L
        const val NATIVE_GPS_INTERVAL_MS = 500L
    }
}
