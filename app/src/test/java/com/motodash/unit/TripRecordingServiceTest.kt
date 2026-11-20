package com.motodash.unit

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.motodash.telemetry.TripRecordingService
import com.motodash.telemetry.TripSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripRecordingServiceTest {
    @Test
    fun `service owns recording after dashboard leaves and releases tracker on finish`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val session = TripSession.get(app)
        session.setDashboardVisible(true)
        val tracker = session.locationTracker
        val service = Robolectric.buildService(TripRecordingService::class.java).create().startCommand(0, 1)
        assertTrue(session.state.value.recording)
        session.setDashboardVisible(false)
        assertTrue(session.state.value.recording)
        assertTrue(tracker.isTracking)
        assertSame(tracker, TripSession.get(app).locationTracker)
        service.destroy()
        assertFalse(session.state.value.recording)
        assertFalse(tracker.isTracking)
        assertNotNull(session.summary.value)
    }
}
