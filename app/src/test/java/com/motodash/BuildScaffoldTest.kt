package com.motodash

import com.spotify.android.appremote.api.SpotifyAppRemote
import org.junit.Assert.assertNotNull
import org.junit.Test

class BuildScaffoldTest {
    @Test
    fun testScaffoldSetup() {
        val app = MotoDashApplication()
        assertNotNull(app)
    }

    @Test
    fun testSpotifyAppRemoteClassResolvable() {
        // Verifies the :spotify-app-remote AAR artifact is successfully resolved and linked
        val clazz = SpotifyAppRemote::class.java
        assertNotNull(clazz)
    }
}
