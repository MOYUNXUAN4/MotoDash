package com.motodash.unit

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.motodash.telemetry.WazeLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WazeLauncherTest {

    @Test
    fun `buildWazeIntent targets ACTION_VIEW and waze scheme uri`() {
        val intent = WazeLauncher.buildWazeIntent()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse("waze://"), intent.data)
        assertEquals("waze", intent.data?.scheme)
    }

    @Test
    fun `launchWaze from Application context adds FLAG_ACTIVITY_NEW_TASK and returns true`() {
        val appContext = ApplicationProvider.getApplicationContext<android.app.Application>()
        val launched = WazeLauncher.launchWaze(appContext)

        assertTrue(launched)
        val startedIntent = shadowOf(appContext).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, startedIntent.action)
        assertEquals(Uri.parse("waze://"), startedIntent.data)
        assertTrue((startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun `launchWaze from Activity context omits FLAG_ACTIVITY_NEW_TASK and returns true`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val launched = WazeLauncher.launchWaze(activity)

        assertTrue(launched)
        val startedIntent = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, startedIntent.action)
        assertEquals(Uri.parse("waze://"), startedIntent.data)
        assertEquals(0, startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun `launchWaze returns false safely when ActivityNotFoundException is thrown`() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val missingAppContext = object : ContextWrapper(baseContext) {
            override fun startActivity(intent: Intent?) {
                throw ActivityNotFoundException("Waze not installed")
            }
        }

        val launched = WazeLauncher.launchWaze(missingAppContext)
        assertFalse(launched)
    }
}
