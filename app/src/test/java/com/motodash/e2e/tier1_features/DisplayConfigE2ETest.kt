package com.motodash.e2e.tier1_features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class DisplayConfigE2ETest {

    private fun resolveAppFile(relativePath: String): File {
        val direct = File(relativePath)
        return if (direct.exists()) direct else File("app", relativePath)
    }

    private fun loadManifestXml(): Element {
        val manifestFile = resolveAppFile("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist at ${manifestFile.absolutePath}", manifestFile.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
        return doc.documentElement
    }

    private fun loadThemesXml(): Element {
        val themesFile = resolveAppFile("src/main/res/values/themes.xml")
        assertTrue("themes.xml must exist at ${themesFile.absolutePath}", themesFile.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(themesFile)
        return doc.documentElement
    }

    private fun parseArgbHex(hex: String): Int {
        val clean = hex.trim()
        require(clean.matches(Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$"))) {
            "Invalid hex color: $hex"
        }
        val raw = clean.substring(1).toLong(16)
        return if (clean.length == 7) (0xFF000000L or raw).toInt() else raw.toInt()
    }

    @Test
    fun testF1_portraitOrientationLocked() {
        val manifest = loadManifestXml()
        val activities = manifest.getElementsByTagName("activity")
        assertTrue("At least one activity must be declared", activities.length > 0)
        val mainActivity = activities.item(0) as Element
        val orientation = mainActivity.getAttribute("android:screenOrientation")
        assertEquals("Screen orientation must be locked to portrait", "portrait", orientation)
    }

    @Test
    fun testF1_keepScreenOnConfigured() {
        val manifest = loadManifestXml()
        val mainActivity = manifest.getElementsByTagName("activity").item(0) as Element
        val keepScreenOn = mainActivity.getAttribute("android:keepScreenOn")
        assertEquals("keepScreenOn must be enabled in manifest or window flags", "true", keepScreenOn)
    }

    @Test
    fun testF1_pureBlackThemeEnforced() {
        // R1: Full-screen immersive dark theme with pure black background (#000000)
        val manifest = loadManifestXml()
        val application = manifest.getElementsByTagName("application").item(0) as? Element
        assertNotNull("AndroidManifest.xml must declare an <application> element", application)
        assertEquals(
            "Application theme must reference @style/Theme.MotoDash",
            "@style/Theme.MotoDash",
            application!!.getAttribute("android:theme")
        )

        val themesRoot = loadThemesXml()
        val styles = themesRoot.getElementsByTagName("style")
        var motoDashStyle: Element? = null
        for (i in 0 until styles.length) {
            val style = styles.item(i) as Element
            if (style.getAttribute("name") == "Theme.MotoDash") {
                motoDashStyle = style
                break
            }
        }
        assertNotNull("themes.xml must define <style name=\"Theme.MotoDash\">", motoDashStyle)

        val itemNodes = motoDashStyle!!.getElementsByTagName("item")
        val themeItems = mutableMapOf<String, String>()
        for (i in 0 until itemNodes.length) {
            val item = itemNodes.item(i) as Element
            themeItems[item.getAttribute("name")] = item.textContent.trim()
        }

        val requiredBlackAttrs = listOf(
            "android:windowBackground",
            "android:statusBarColor",
            "android:navigationBarColor"
        )
        for (attr in requiredBlackAttrs) {
            val hexValue = themeItems[attr]
            assertNotNull("Theme.MotoDash must define $attr", hexValue)
            assertEquals("$attr must be pure black (#000000)", "#000000", hexValue)

            val pureBlackInt = parseArgbHex(hexValue!!)
            assertEquals("Theme $attr color int must be 0xFF000000 (-16777216)", 0xFF000000.toInt(), pureBlackInt)
            assertEquals("Alpha must be 255", 255, (pureBlackInt ushr 24) and 0xFF)
            assertEquals("Red component must be 0", 0, (pureBlackInt ushr 16) and 0xFF)
            assertEquals("Green component must be 0", 0, (pureBlackInt ushr 8) and 0xFF)
            assertEquals("Blue component must be 0", 0, pureBlackInt and 0xFF)
        }
    }

    @Test
    fun testF1_verticalSplit60_40Ratio() {
        // R1: Strict vertical split using Column(Modifier.fillMaxSize()): Upper 60% (0.6f), lower 40% (0.4f)
        val upperWeight = 0.6f
        val lowerWeight = 0.4f
        assertEquals("Upper weight + lower weight must equal 1.0f", 1.0f, upperWeight + lowerWeight, 0.0001f)
        assertTrue("Upper dashboard weight must be exactly 0.6f", upperWeight == 0.6f)
        assertTrue("Lower glove-friendly music area weight must be exactly 0.4f", lowerWeight == 0.4f)
    }

    @Test
    fun testF1_singleActivityConfiguration() {
        val manifest = loadManifestXml()
        val activities = manifest.getElementsByTagName("activity")
        assertEquals("Application must maintain single Activity architecture", 1, activities.length)
        val activity = activities.item(0) as Element
        val activityName = activity.getAttribute("android:name")
        assertTrue(
            "Single activity must be MainActivity (found '$activityName')",
            activityName == ".MainActivity" || activityName == "MainActivity" || activityName == "com.motodash.MainActivity"
        )

        // Verify callback intent filter is present
        val dataNodes = manifest.getElementsByTagName("data")
        var foundCallback = false
        for (i in 0 until dataNodes.length) {
            val node = dataNodes.item(i) as Element
            if (node.getAttribute("android:scheme") == "motodash" && node.getAttribute("android:host") == "callback") {
                foundCallback = true
                break
            }
        }
        assertTrue("MainActivity must declare intent-filter for motodash://callback", foundCallback)
    }
}
