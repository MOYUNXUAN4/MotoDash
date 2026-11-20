package com.motodash.unit

import android.Manifest
import android.app.Application
import android.location.Location
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import com.motodash.location.GpsSignalStatus
import com.motodash.location.GpsSpeedFilter
import com.motodash.location.LocationReading
import com.motodash.location.LocationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class LocationTrackerTest {

    private class FakeFusedLocationClient(
        private val onRequestLocationUpdates: (() -> Unit)? = null
    ) {
        val requestUpdatesCallsAtomic = AtomicInteger(0)
        val removeUpdatesCallsAtomic = AtomicInteger(0)
        var requestUpdatesCalls: Int
            get() = requestUpdatesCallsAtomic.get()
            set(value) { requestUpdatesCallsAtomic.set(value) }
        var removeUpdatesCalls: Int
            get() = removeUpdatesCallsAtomic.get()
            set(value) { removeUpdatesCallsAtomic.set(value) }
        @Volatile var lastRequest: LocationRequest? = null
        @Volatile var lastCallback: LocationCallback? = null

        val proxy: FusedLocationProviderClient = Proxy.newProxyInstance(
            FusedLocationProviderClient::class.java.classLoader,
            arrayOf(FusedLocationProviderClient::class.java)
        ) { _, method, args ->
            when (method.name) {
                "requestLocationUpdates" -> {
                    onRequestLocationUpdates?.invoke()
                    requestUpdatesCallsAtomic.incrementAndGet()
                    args?.forEach { arg ->
                        when (arg) {
                            is LocationRequest -> lastRequest = arg
                            is LocationCallback -> lastCallback = arg
                        }
                    }
                    Tasks.forResult<Void>(null)
                }
                "removeLocationUpdates" -> {
                    removeUpdatesCallsAtomic.incrementAndGet()
                    Tasks.forResult<Void>(null)
                }
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                "toString" -> "FakeFusedLocationClient"
                else -> Tasks.forResult<Void>(null)
            }
        } as FusedLocationProviderClient
    }

    @Test
    fun `startTracking returns false and never calls requestLocationUpdates when permission denied`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val fakeClient = FakeFusedLocationClient()
        val tracker = LocationTracker(
            context = app,
            fusedLocationClient = fakeClient.proxy,
            timeProvider = { 1000L }
        )

        assertFalse(tracker.hasLocationPermission())
        assertFalse(tracker.startTracking())
        assertFalse(tracker.isTracking)
        assertEquals(0, fakeClient.requestUpdatesCalls)
    }

    @Test
    fun `startTracking requests high accuracy 1000ms updates and stopTracking removes updates`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val fakeClient = FakeFusedLocationClient()
        val dispatcher = StandardTestDispatcher()

        val tracker = LocationTracker(
            context = app,
            fusedLocationClient = fakeClient.proxy,
            dispatcher = dispatcher,
            timeProvider = { 1000L }
        )

        assertTrue(tracker.hasLocationPermission())
        assertTrue(tracker.startTracking())
        assertTrue(tracker.isTracking)
        assertEquals(1, fakeClient.requestUpdatesCalls)
        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, fakeClient.lastRequest?.priority)
        assertEquals(1000L, fakeClient.lastRequest?.intervalMillis)
        assertNotNull(fakeClient.lastCallback)

        // Duplicate startTracking is idempotent
        assertTrue(tracker.startTracking())
        assertEquals(1, fakeClient.requestUpdatesCalls)

        tracker.stopTracking()
        assertFalse(tracker.isTracking)
        assertEquals(1, fakeClient.removeUpdatesCalls)
    }

    @Test
    fun `onLocationReceived maps missing accuracy and missing speed safely`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val fakeClient = FakeFusedLocationClient()
        var now = 2000L
        val tracker = LocationTracker(
            context = app,
            fusedLocationClient = fakeClient.proxy,
            timeProvider = { now }
        )

        val noAccuracyLoc = Location("gps").apply {
            latitude = 37.77
            longitude = -122.41
            speed = 15.0f
            // accuracy deliberately unset -> hasAccuracy() == false
        }
        tracker.onLocationReceived(noAccuracyLoc)

        assertEquals(Float.POSITIVE_INFINITY, tracker.latestReading.value?.accuracyMeters)
        assertEquals(GpsSignalStatus.RED, tracker.speedState.value.status)
        assertTrue(tracker.speedState.value.isAccuracyRejected)
        assertEquals(0.0f, tracker.speedState.value.displaySpeedKmh, 0.001f)

        now = 3000L
        val validNoSpeedLoc = Location("gps").apply {
            latitude = 37.78
            longitude = -122.42
            accuracy = 8.0f
            bearing = 180.0f
            // speed deliberately unset -> hasSpeed() == false -> 0f
        }
        tracker.onLocationReceived(validNoSpeedLoc)

        assertEquals(0.0f, tracker.latestReading.value?.speedMps ?: -1f, 0.001f)
        assertEquals(GpsSignalStatus.GREEN, tracker.speedState.value.status)
        assertTrue(tracker.speedState.value.isZeroDebounced)
        assertEquals(0.0f, tracker.speedState.value.displaySpeedKmh, 0.001f)

        now = 4000L
        val movingLoc = Location("gps").apply {
            latitude = 37.79
            longitude = -122.43
            accuracy = 6.0f
            speed = 20.0f
            bearing = 270.0f
        }
        tracker.onLocationReceived(movingLoc)
        assertEquals(72.0f, tracker.speedState.value.displaySpeedKmh, 0.001f)
        assertEquals(270.0f, tracker.speedState.value.effectiveBearingDegrees, 0.001f)
    }

    @Test
    fun `watchdog coroutine marks speedState timed out after 3000ms`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val fakeClient = FakeFusedLocationClient()
        val dispatcher = StandardTestDispatcher(testScheduler)

        var currentTimeMs = 1000L
        val tracker = LocationTracker(
            context = app,
            fusedLocationClient = fakeClient.proxy,
            speedFilter = GpsSpeedFilter(signalTimeoutMs = 3000L),
            dispatcher = dispatcher,
            timeProvider = { currentTimeMs },
            watchdogCheckIntervalMs = 500L
        )

        assertTrue(tracker.speedState.value.isTimedOut)
        assertEquals(GpsSignalStatus.RED, tracker.speedState.value.status)

        assertTrue(tracker.startTracking())
        tracker.onLocationReading(
            LocationReading(
                speedMps = 15.0f,
                accuracyMeters = 8.0f,
                bearingDegrees = 90.0f,
                hasBearing = true,
                latitude = 37.77,
                longitude = -122.41,
                timestampMs = currentTimeMs
            )
        )
        assertFalse(tracker.speedState.value.isTimedOut)
        assertEquals(GpsSignalStatus.GREEN, tracker.speedState.value.status)

        // Advance 2500ms -> t = 3500 (elapsed 2500 <= 3000) -> still active
        currentTimeMs = 3500L
        advanceTimeBy(2500L)
        runCurrent()
        assertFalse(tracker.speedState.value.isTimedOut)
        assertEquals(54.0f, tracker.speedState.value.displaySpeedKmh, 0.001f)

        // Advance 1000ms -> t = 4500 (elapsed 3500 > 3000) -> timed out RED
        currentTimeMs = 4500L
        advanceTimeBy(1000L)
        runCurrent()
        assertTrue(tracker.speedState.value.isTimedOut)
        assertEquals(GpsSignalStatus.RED, tracker.speedState.value.status)
        assertEquals(0.0f, tracker.speedState.value.displaySpeedKmh, 0.001f)

        tracker.stopTracking()
    }

    @Test
    fun `reset clears latestReading and restores timed out RED state`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val fakeClient = FakeFusedLocationClient()
        val tracker = LocationTracker(
            context = app,
            fusedLocationClient = fakeClient.proxy,
            timeProvider = { 5000L }
        )

        tracker.onLocationReading(
            LocationReading(
                speedMps = 10.0f,
                accuracyMeters = 5.0f,
                bearingDegrees = 45.0f,
                hasBearing = true,
                latitude = 37.77,
                longitude = -122.41,
                timestampMs = 5000L
            )
        )
        assertNotNull(tracker.latestReading.value)
        assertFalse(tracker.speedState.value.isTimedOut)

        tracker.reset()
        assertNull(tracker.latestReading.value)
        assertTrue(tracker.speedState.value.isTimedOut)
        assertEquals(GpsSignalStatus.RED, tracker.speedState.value.status)
    }

    @Test
    fun `concurrent startTracking calls register updates once and stopTracking leaks zero watchdogs`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val executor = Executors.newFixedThreadPool(4)
        try {
            repeat(10) {
                val timeCalls = AtomicInteger(0)
                val fakeClient = FakeFusedLocationClient(
                    onRequestLocationUpdates = { Thread.sleep(10) }
                )
                val tracker = LocationTracker(
                    context = app,
                    fusedLocationClient = fakeClient.proxy,
                    dispatcher = Dispatchers.Default,
                    timeProvider = {
                        timeCalls.incrementAndGet()
                        1000L
                    },
                    watchdogCheckIntervalMs = 15L
                )

                val startGate = CountDownLatch(1)
                val doneGate = CountDownLatch(4)
                val results = java.util.Collections.synchronizedList(mutableListOf<Boolean>())

                repeat(4) {
                    executor.submit {
                        startGate.await(5, TimeUnit.SECONDS)
                        results.add(tracker.startTracking())
                        doneGate.countDown()
                    }
                }
                startGate.countDown()
                assertTrue(doneGate.await(5, TimeUnit.SECONDS))

                assertEquals(listOf(true, true, true, true), results)
                assertEquals(1, fakeClient.requestUpdatesCalls)
                assertTrue(tracker.isTracking)

                tracker.stopTracking()
                assertFalse(tracker.isTracking)
                assertEquals(1, fakeClient.removeUpdatesCalls)

                Thread.sleep(40)
                val settledCalls = timeCalls.get()
                Thread.sleep(60)
                assertEquals(settledCalls, timeCalls.get())
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `stopTracking called while startTracking is in flight prevents dangling tracking and watchdogs`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val enteredRequest = CountDownLatch(1)
            val releaseRequest = CountDownLatch(1)
            val timeCalls = AtomicInteger(0)
            val fakeClient = FakeFusedLocationClient(
                onRequestLocationUpdates = {
                    enteredRequest.countDown()
                    releaseRequest.await(5, TimeUnit.SECONDS)
                }
            )
            val tracker = LocationTracker(
                context = app,
                fusedLocationClient = fakeClient.proxy,
                dispatcher = Dispatchers.Default,
                timeProvider = {
                    timeCalls.incrementAndGet()
                    1000L
                },
                watchdogCheckIntervalMs = 15L
            )

            val startFuture = executor.submit<Boolean> { tracker.startTracking() }
            assertTrue(enteredRequest.await(5, TimeUnit.SECONDS))

            val stopFuture = executor.submit { tracker.stopTracking() }
            Thread.sleep(30)
            releaseRequest.countDown()

            assertTrue(startFuture.get(5, TimeUnit.SECONDS))
            stopFuture.get(5, TimeUnit.SECONDS)

            assertFalse(tracker.isTracking)
            assertEquals(1, fakeClient.requestUpdatesCalls)
            assertEquals(1, fakeClient.removeUpdatesCalls)

            Thread.sleep(40)
            val settledCalls = timeCalls.get()
            Thread.sleep(60)
            assertEquals(settledCalls, timeCalls.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `default timeProvider uses monotonic SystemClock elapsedRealtime`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val fakeClient = FakeFusedLocationClient()
        val beforeMs = SystemClock.elapsedRealtime()
        val tracker = LocationTracker(
            context = app,
            fusedLocationClient = fakeClient.proxy
        )
        val loc = Location("gps").apply {
            latitude = 37.77
            longitude = -122.41
            accuracy = 5.0f
            speed = 10.0f
        }
        tracker.onLocationReceived(loc)
        val afterMs = SystemClock.elapsedRealtime()

        val recordedTs = tracker.latestReading.value?.timestampMs ?: -1L
        assertTrue(
            "Expected elapsedRealtime timestamp in [$beforeMs, $afterMs], got $recordedTs",
            recordedTs in beforeMs..afterMs
        )
    }
}
