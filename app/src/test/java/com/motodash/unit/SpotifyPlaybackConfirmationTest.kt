package com.motodash.unit

import com.motodash.data.SpotifyNetworkModule
import com.motodash.spotify.SpotifyTrackDomain
import com.motodash.spotify.SpotifyWebPlaybackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpotifyPlaybackConfirmationTest {
    private lateinit var server: MockWebServer
    private lateinit var controller: SpotifyWebPlaybackController
    private val track = SpotifyTrackDomain("playlist", "t1", "spotify:track:t1", "Track", "Artist", 1000, 0)

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        controller = SpotifyWebPlaybackController(
            SpotifyNetworkModule.createSpotifyWebApi(server.url("/").toString())
        ) { "token" }
    }

    @After fun teardown() { server.shutdown() }

    @Test fun `failed play never claims requested track is playing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("{}"))
        assertTrue(controller.play(track, listOf(track.trackUri)).isFailure)
        assertFalse(controller.state.value.isPlaying)
        assertFalse(controller.state.value.isConnected)
        assertNull(controller.state.value.currentTrackUri)
    }

    @Test fun `missing token sends no request and does not change playing state`() = runTest {
        val signedOut = SpotifyWebPlaybackController(
            SpotifyNetworkModule.createSpotifyWebApi(server.url("/").toString())
        ) { null }
        assertTrue(signedOut.play(track, emptyList()).isFailure)
        assertFalse(signedOut.state.value.isPlaying)
        assertEquals(0, server.requestCount)
    }

    @Test fun `successful play publishes confirmation and failed pause retains it`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(controller.play(track, listOf(track.trackUri)).isSuccess)
        assertTrue(controller.state.value.isPlaying)
        assertEquals(track.trackUri, controller.state.value.currentTrackUri)
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(controller.toggle().isFailure)
        assertTrue(controller.state.value.isPlaying)
    }

    @Test fun `no active device clears stale title and connected status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        controller.play(track, listOf(track.trackUri))
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(controller.sync().isSuccess)
        assertFalse(controller.state.value.isPlaying)
        assertFalse(controller.state.value.isConnected)
        assertNull(controller.state.value.currentTrackTitle)
    }

    @Test fun `expired session polling clears playing indicator`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        controller.play(track, emptyList())
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(controller.sync().isFailure)
        assertFalse(controller.state.value.isPlaying)
        assertFalse(controller.state.value.isConnected)
    }

    @Test fun `inactive device retry only confirms playback after successful retry`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody("""{"devices":[{"id":"phone","type":"Smartphone","is_active":false}]}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(controller.play(track, listOf(track.trackUri)).isSuccess)
        assertTrue(controller.state.value.isPlaying)
        server.takeRequest()
        server.takeRequest()
        assertEquals("phone", server.takeRequest().requestUrl?.queryParameter("device_id"))
    }

    @Test fun `cancellation is propagated without inventing a playback failure`() = runTest {
        val cancelled = SpotifyWebPlaybackController(
            SpotifyNetworkModule.createSpotifyWebApi(server.url("/").toString())
        ) { throw CancellationException("cancelled") }
        try {
            cancelled.play(track, emptyList())
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertFalse(cancelled.state.value.isPlaying)
        }
    }
}
