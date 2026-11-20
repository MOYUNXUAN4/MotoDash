package com.motodash.unit

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.motodash.MotoDashApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MotoDashConfigTest {

    @Test
    fun `MotoDashApplication boots cleanly under Robolectric without UnsatisfiedLinkError`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(app is MotoDashApplication)
    }

    @Test
    fun `MainActivity manifest metadata specifies singleTask portrait keepScreenOn and callback deep link`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pm = app.packageManager

        @Suppress("DEPRECATION")
        val pkgInfo = pm.getPackageInfo(
            app.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_PERMISSIONS
        )

        val activities = pkgInfo.activities.orEmpty()
        val appActivities = activities.filter { it.name.startsWith("com.motodash.") }
        assertEquals(1, appActivities.size)
        val mainActivity = appActivities.first()
        assertEquals("com.motodash.MainActivity", mainActivity.name)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, mainActivity.launchMode)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, mainActivity.screenOrientation)
        assertTrue(mainActivity.exported)

        val permissions = pkgInfo.requestedPermissions.orEmpty().toSet()
        assertTrue(permissions.contains(android.Manifest.permission.INTERNET))
        assertTrue(permissions.contains(android.Manifest.permission.ACCESS_NETWORK_STATE))
        assertTrue(permissions.contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(permissions.contains(android.Manifest.permission.ACCESS_COARSE_LOCATION))

        val callbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("motodash://callback?code=abc"))
        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(callbackIntent, PackageManager.MATCH_DEFAULT_ONLY)
        assertTrue(resolved.any { it.activityInfo.name == "com.motodash.MainActivity" })
    }

    @Test
    fun `manifest xml declares keepScreenOn and queries for Spotify Waze and CustomTabs`() {
        val manifestFile = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).first { it.exists() }

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
        doc.documentElement.normalize()

        val activities = doc.getElementsByTagName("activity")
        assertEquals(1, activities.length)
        val attrs = activities.item(0).attributes
        assertEquals("true", attrs.getNamedItem("android:keepScreenOn")?.nodeValue)
        assertEquals("singleTask", attrs.getNamedItem("android:launchMode")?.nodeValue)
        assertEquals("portrait", attrs.getNamedItem("android:screenOrientation")?.nodeValue)

        val queriedPackages = mutableSetOf<String>()
        val packageNodes = doc.getElementsByTagName("package")
        for (i in 0 until packageNodes.length) {
            packageNodes.item(i).attributes.getNamedItem("android:name")?.nodeValue?.let {
                queriedPackages.add(it)
            }
        }
        assertTrue(queriedPackages.contains("com.spotify.music"))
        assertTrue(queriedPackages.contains("com.waze"))

        val xmlText = manifestFile.readText()
        assertTrue(xmlText.contains("android:scheme=\"waze\""))
        assertTrue(xmlText.contains("CustomTabsService"))
    }

    @Test
    fun `Theme_MotoDash resolves pure black window status and navigation bar colors`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val themeResId = app.resources.getIdentifier("Theme.MotoDash", "style", app.packageName)
        assertTrue("Theme.MotoDash style must exist", themeResId != 0)

        val theme = app.resources.newTheme()
        theme.applyStyle(themeResId, true)

        val typedArray = theme.obtainStyledAttributes(
            intArrayOf(
                android.R.attr.windowBackground,
                android.R.attr.colorBackground,
                android.R.attr.statusBarColor,
                android.R.attr.navigationBarColor
            )
        )
        try {
            assertEquals(Color.BLACK, typedArray.getColor(0, -1))
            assertEquals(Color.BLACK, typedArray.getColor(1, -1))
            assertEquals(Color.BLACK, typedArray.getColor(2, -1))
            assertEquals(Color.BLACK, typedArray.getColor(3, -1))
        } finally {
            typedArray.recycle()
        }

        val appName = app.getString(
            app.resources.getIdentifier("app_name", "string", app.packageName).also {
                assertNotNull(it)
                assertTrue(it != 0)
            }
        )
        assertEquals("MotoDash", appName)
    }
}
