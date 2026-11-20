package com.motodash.unit

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.motodash.data.SpotifyNetworkModule
import com.motodash.data.SpotifyRepository
import com.motodash.data.db.MotoDashDatabase
import com.motodash.spotify.PkceAuthHelper
import com.motodash.spotify.SpotifyAuthManager
import com.motodash.spotify.SpotifyConfig
import com.motodash.spotify.SpotifyTokens
import com.motodash.spotify.TokenStore
import com.motodash.spotify.SpotifyWebApi
import com.motodash.data.db.PlaylistEntity
import com.motodash.data.db.TrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpotifyAuthAndCacheTest {

    private lateinit var server: MockWebServer
    private lateinit var database: MotoDashDatabase

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MotoDashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
    }

    @Test
    fun `PKCE helper generates RFC7636 verifier challenge and authorization URL with required scopes`() {
        val verifier = PkceAuthHelper.generateCodeVerifier()
        val challenge = PkceAuthHelper.generateCodeChallenge(verifier)
        assertTrue(verifier.length in 43..128)
        assertTrue(challenge.isNotBlank())
        assertTrue(!challenge.contains("="))

        val url = PkceAuthHelper.buildAuthorizationUrl(
            clientId = "demo_client_id",
            codeChallenge = challenge,
            state = "state_xyz"
        )
        assertTrue(url.contains("client_id=demo_client_id"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("motodash%3A%2F%2Fcallback"))
        assertTrue(url.contains("playlist-read-private"))
        assertTrue(url.contains("playlist-read-collaborative"))
        assertTrue(url.contains("user-library-read"))
        assertTrue(url.contains("app-remote-control"))
    }

    @Test
    fun `SpotifyAuthManager exchanges code and refreshes expired access token automatically`() = runTest {
        val fakeStore = InMemoryTokenStore().apply {
            saveClientId("client_abc")
            savePendingPkce("verifier_123", "state_999")
        }
        var nowMs = 1_000_000L

        val authApi = SpotifyNetworkModule.createSpotifyAuthApi(baseUrl = server.url("/").toString())
        val authManager = SpotifyAuthManager(
            tokenStore = fakeStore,
            authApi = authApi,
            defaultClientId = "client_abc",
            clockMs = { nowMs }
        )

        // 1. Exchange authorization code
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "access_token": "acc_initial",
                  "token_type": "Bearer",
                  "expires_in": 3600,
                  "refresh_token": "ref_initial"
                }
                """.trimIndent()
            )
        )
        val callbackUri = Uri.parse("motodash://callback?code=auth_code_1&state=state_999")
        val exchangeResult = authManager.handleAuthRedirect(callbackUri)
        assertTrue(exchangeResult.isSuccess)
        assertEquals("acc_initial", authManager.getValidAccessToken())

        // 2. Advance clock past expiry and verify automatic refresh
        nowMs += 3_600_000L
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "access_token": "acc_refreshed",
                  "token_type": "Bearer",
                  "expires_in": 3600,
                  "refresh_token": "ref_rotated"
                }
                """.trimIndent()
            )
        )
        val refreshedToken = authManager.getValidAccessToken()
        assertEquals("acc_refreshed", refreshedToken)
        assertEquals("ref_rotated", fakeStore.getTokens()?.refreshToken)
    }

    @Test
    fun `SpotifyRepository fetches playlists and 2026 items and caches them in Room database`() = runTest {
        val fakeStore = InMemoryTokenStore().apply {
            saveClientId("client_abc")
            saveTokens(
                SpotifyTokens(
                    accessToken = "valid_token",
                    refreshToken = "refresh_tok",
                    expiresAtMs = System.currentTimeMillis() + 3_600_000L
                )
            )
        }
        val baseUrl = server.url("/").toString()
        val authManager = SpotifyAuthManager(
            tokenStore = fakeStore,
            authApi = SpotifyNetworkModule.createSpotifyAuthApi(baseUrl = baseUrl),
            defaultClientId = "client_abc"
        )
        val webApi = SpotifyNetworkModule.createSpotifyWebApi(baseUrl = baseUrl)
        val repo = SpotifyRepository(
            webApi = webApi,
            dao = database.spotifyDao(),
            authManager = authManager
        )

        // Enqueue /me/playlists
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "total": 1,
                  "items": [
                    {
                      "id": "pl_canyon",
                      "name": "Canyon Run",
                      "items": { "total": 2 }
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val playlistsResult = repo.refreshPlaylists()
        assertTrue(playlistsResult.isSuccess)
        val cachedPlaylists = repo.observePlaylists().first()
        assertEquals(2, cachedPlaylists.size)
        assertEquals(SpotifyConfig.LIKED_SONGS_PLAYLIST_ID, cachedPlaylists[0].id)
        assertTrue(cachedPlaylists[0].isLikedSongs)
        assertEquals("pl_canyon", cachedPlaylists[1].id)
        assertEquals("Canyon Run", cachedPlaylists[1].name)

        // Enqueue /playlists/pl_canyon/items
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "total": 2,
                  "items": [
                    {
                      "item": {
                        "id": "t1",
                        "uri": "spotify:track:t1",
                        "name": "Kickstart My Heart",
                        "duration_ms": 284000,
                        "artists": [{ "name": "Motley Crue" }]
                      }
                    },
                    {
                      "item": {
                        "id": "t2",
                        "uri": "spotify:track:t2",
                        "name": "Free Bird",
                        "duration_ms": 548000,
                        "artists": [{ "name": "Lynyrd Skynyrd" }]
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val tracksResult = repo.refreshTracksForPlaylist("pl_canyon")
        assertTrue(tracksResult.isSuccess)
        val cachedTracks = repo.observeTracksForPlaylist("pl_canyon").first()
        assertEquals(2, cachedTracks.size)
        assertEquals("Kickstart My Heart", cachedTracks[0].title)
        assertEquals("Free Bird", cachedTracks[1].title)

        // Verify network paths hit
        val req1 = server.takeRequest()
        val req2 = server.takeRequest()
        assertNotNull(req1.path)
        assertTrue(req1.path!!.startsWith("/me/playlists"))
        assertTrue(req2.path!!.startsWith("/playlists/pl_canyon/items"))
    }

    @Test
    fun `playlist pagination advances by raw items and preserves liked songs first`() = runTest {
        server.enqueue(MockResponse().setBody("""{"total":3,"next":"page2","items":[null,{"id":"a","name":"A"}]}"""))
        server.enqueue(MockResponse().setBody("""{"total":3,"items":[{"id":"b","name":"B"}]}"""))
        val result = repository().refreshPlaylists().getOrThrow()
        assertEquals(listOf(SpotifyConfig.LIKED_SONGS_PLAYLIST_ID, "a", "b"), result.map { it.id })
        assertEquals("0", server.takeRequest().requestUrl?.queryParameter("offset"))
        assertEquals("2", server.takeRequest().requestUrl?.queryParameter("offset"))
        assertEquals(3, database.spotifyDao().getAllPlaylists().size)
    }

    @Test
    fun `playlist item pagination skips unavailable entries without reusing positions`() = runTest {
        database.spotifyDao().upsertPlaylists(listOf(PlaylistEntity("p", "Playlist", 4)))
        server.enqueue(MockResponse().setBody("""{"total":4,"next":"page2","items":[null,${trackItem("one")},{"is_local":true}]}"""))
        server.enqueue(MockResponse().setBody("""{"total":4,"items":[${trackItem("two")}]}"""))
        val result = repository().refreshTracksForPlaylist("p").getOrThrow()
        assertEquals(listOf("one", "two"), result.map { it.trackId })
        assertEquals(listOf(0, 1), database.spotifyDao().getTracksForPlaylist("p").map { it.position })
        assertTrue(server.takeRequest().path!!.startsWith("/playlists/p/items"))
        assertEquals("3", server.takeRequest().requestUrl?.queryParameter("offset"))
    }

    @Test
    fun `a playlist with more than fifty tracks is fully cached`() = runTest {
        database.spotifyDao().upsertPlaylists(listOf(PlaylistEntity("p", "Playlist", 51)))
        val firstPage = (0 until 50).joinToString(",") { trackItem("t$it") }
        server.enqueue(MockResponse().setBody("""{"total":51,"next":"page2","items":[$firstPage]}"""))
        server.enqueue(MockResponse().setBody("""{"total":51,"items":[${trackItem("t50")}]}"""))
        val result = repository().refreshTracksForPlaylist("p").getOrThrow()
        assertEquals(51, result.size)
        assertEquals((0..50).toList(), database.spotifyDao().getTracksForPlaylist("p").map { it.position })
        server.takeRequest()
        assertEquals("50", server.takeRequest().requestUrl?.queryParameter("offset"))
    }

    @Test
    fun `liked songs load multiple pages with unique cached positions`() = runTest {
        val liked = SpotifyConfig.LIKED_SONGS_PLAYLIST_ID
        database.spotifyDao().upsertPlaylists(listOf(PlaylistEntity(liked, "Liked Songs", 3, true)))
        server.enqueue(MockResponse().setBody("""{"total":3,"next":"page2","items":[${trackItem("one", "track")},null]}"""))
        server.enqueue(MockResponse().setBody("""{"total":3,"items":[${trackItem("two", "track")}]}"""))
        val result = repository().refreshTracksForPlaylist(liked).getOrThrow()
        assertEquals(listOf("one", "two"), result.map { it.trackId })
        assertEquals(listOf(0, 1), database.spotifyDao().getTracksForPlaylist(liked).map { it.position })
        assertTrue(server.takeRequest().path!!.startsWith("/me/tracks"))
        assertEquals("2", server.takeRequest().requestUrl?.queryParameter("offset"))
    }

    @Test
    fun `refreshing playlist metadata preserves cached tracks even if subsequent track refresh fails`() = runTest {
        seedCachedTrack()
        server.enqueue(MockResponse().setBody("""{"total":1,"items":[{"id":"p","name":"Renamed playlist","items":{"total":2}}]}"""))
        val repo = repository()
        repo.refreshPlaylists().getOrThrow()
        val playlist = database.spotifyDao().getAllPlaylists().single { it.id == "p" }
        assertEquals("Renamed playlist", playlist.name)
        assertEquals(2, playlist.trackCount)
        assertEquals("old", database.spotifyDao().getTracksForPlaylist("p").single().trackId)

        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(repo.refreshTracksForPlaylist("p").isFailure)
        assertEquals("old", database.spotifyDao().getTracksForPlaylist("p").single().trackId)
    }

    @Test
    fun `a failed later page leaves the previous track cache intact`() = runTest {
        seedCachedTrack()
        server.enqueue(MockResponse().setBody("""{"total":2,"next":"page2","items":[${trackItem("replacement")}]}"""))
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(repository().refreshTracksForPlaylist("p").isFailure)
        assertEquals(listOf("old"), database.spotifyDao().getTracksForPlaylist("p").map { it.trackId })
    }

    @Test
    fun `an empty unfinished page fails safely instead of looping or erasing cache`() = runTest {
        seedCachedTrack()
        server.enqueue(MockResponse().setBody("""{"total":10,"next":"page2","items":[]}"""))
        assertTrue(repository().refreshTracksForPlaylist("p").isFailure)
        assertEquals(1, server.requestCount)
        assertEquals("old", database.spotifyDao().getTracksForPlaylist("p").single().trackId)
    }

    @Test
    fun `refresh propagates cancellation instead of converting it to an error result`() = runTest {
        val cancelledApi = java.lang.reflect.Proxy.newProxyInstance(
            SpotifyWebApi::class.java.classLoader, arrayOf(SpotifyWebApi::class.java)
        ) { _, _, _ -> throw CancellationException("Cancelled fetch") } as SpotifyWebApi
        try {
            repository(cancelledApi).refreshPlaylists()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertTrue(database.spotifyDao().getAllPlaylists().isEmpty())
        }
    }

    private fun repository(api: SpotifyWebApi? = null): SpotifyRepository {
        val baseUrl = server.url("/").toString()
        val store = InMemoryTokenStore().apply {
            saveTokens(SpotifyTokens("valid", "refresh", System.currentTimeMillis() + 3_600_000L))
        }
        return SpotifyRepository(
            api ?: SpotifyNetworkModule.createSpotifyWebApi(baseUrl), database.spotifyDao(),
            SpotifyAuthManager(store, SpotifyNetworkModule.createSpotifyAuthApi(baseUrl), "client")
        )
    }

    private suspend fun seedCachedTrack() {
        database.spotifyDao().upsertPlaylists(listOf(PlaylistEntity("p", "Playlist", 1)))
        database.spotifyDao().replaceTracksForPlaylist("p", listOf(
            TrackEntity("p", "old", "spotify:track:old", "Old track", "Artist", 1000L, 0)
        ))
    }

    private fun trackItem(id: String, payload: String = "item"): String =
        """{"$payload":{"id":"$id","uri":"spotify:track:$id","name":"$id","artists":[{"name":"Artist"}]}}"""

    private class InMemoryTokenStore : TokenStore {
        private var tokens: SpotifyTokens? = null
        private var verifier: String? = null
        private var state: String? = null
        private var clientId: String = ""

        override fun saveTokens(tokens: SpotifyTokens) {
            this.tokens = tokens
        }

        override fun getTokens(): SpotifyTokens? = tokens

        override fun clearTokens() {
            tokens = null
        }

        override fun savePendingPkce(codeVerifier: String, state: String) {
            this.verifier = codeVerifier
            this.state = state
        }

        override fun getPendingCodeVerifier(): String? = verifier
        override fun getPendingState(): String? = state
        override fun clearPendingPkce() {
            verifier = null
            state = null
        }

        override fun saveClientId(clientId: String) {
            this.clientId = clientId
        }

        override fun getClientId(defaultClientId: String): String =
            clientId.ifBlank { defaultClientId }
    }
}
