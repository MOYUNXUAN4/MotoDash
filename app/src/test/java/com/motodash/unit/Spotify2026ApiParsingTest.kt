package com.motodash.unit

import com.motodash.data.SpotifyNetworkModule
import com.motodash.spotify.SpotifyLikedTracksResponse
import com.motodash.spotify.SpotifyPlaylistItemsResponse
import com.motodash.spotify.SpotifyResponseMapper
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Spotify2026ApiParsingTest {

    private lateinit var server: MockWebServer
    private val gson = SpotifyNetworkModule.createGson()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses 2026 playlist items response reading items_i_item with null defenses`() {
        val json = """
            {
              "total": 6,
              "items": [
                {
                  "added_at": "2026-03-01T10:00:00Z",
                  "is_local": false,
                  "item": {
                    "id": "trk_1",
                    "uri": "spotify:track:trk_1",
                    "name": "Highway Star",
                    "duration_ms": 365000,
                    "artists": [
                      { "id": "a1", "name": "Deep Purple" }
                    ]
                  }
                },
                null,
                {
                  "added_at": "2026-03-01T10:01:00Z",
                  "is_local": false,
                  "item": null
                },
                {
                  "added_at": "2026-03-01T10:02:00Z",
                  "is_local": true,
                  "item": {
                    "id": "local_1",
                    "uri": "spotify:local:test",
                    "name": "Local MP3",
                    "artists": [{ "name": "Local Artist" }]
                  }
                },
                {
                  "added_at": "2026-03-01T10:03:00Z",
                  "is_local": false,
                  "item": {
                    "id": "trk_2",
                    "uri": "spotify:track:trk_2",
                    "name": "Nightcall",
                    "duration_ms": 258000,
                    "artists": [
                      { "name": "Kavinsky" },
                      null,
                      { "name": "Lovefoxxx" }
                    ]
                  }
                },
                {
                  "added_at": "2026-03-01T10:04:00Z",
                  "item": {
                    "id": "trk_3",
                    "uri": "spotify:track:trk_3",
                    "name": "Mystery Ride",
                    "artists": null
                  }
                }
              ]
            }
        """.trimIndent()

        val response = gson.fromJson(json, SpotifyPlaylistItemsResponse::class.java)
        val mapped = SpotifyResponseMapper.mapPlaylistItems("pl_ride", response)

        assertEquals(3, mapped.size)

        assertEquals("spotify:track:trk_1", mapped[0].trackUri)
        assertEquals("Highway Star", mapped[0].title)
        assertEquals("Deep Purple", mapped[0].artist)
        assertEquals(0, mapped[0].position)

        assertEquals("spotify:track:trk_2", mapped[1].trackUri)
        assertEquals("Nightcall", mapped[1].title)
        assertEquals("Kavinsky, Lovefoxxx", mapped[1].artist)
        assertEquals(1, mapped[1].position)

        assertEquals("spotify:track:trk_3", mapped[2].trackUri)
        assertEquals("Mystery Ride", mapped[2].title)
        assertEquals("Unknown Artist", mapped[2].artist)
        assertEquals(2, mapped[2].position)
    }

    @Test
    fun `legacy track field inside playlist items wrapper is not treated as 2026 item`() {
        val legacyJson = """
            {
              "items": [
                {
                  "track": {
                    "id": "old_1",
                    "uri": "spotify:track:old_1",
                    "name": "Legacy Track",
                    "artists": [{ "name": "Legacy Artist" }]
                  }
                }
              ]
            }
        """.trimIndent()

        val response = gson.fromJson(legacyJson, SpotifyPlaylistItemsResponse::class.java)
        val mapped = SpotifyResponseMapper.mapPlaylistItems("pl_legacy", response)
        assertTrue("2026 mapper must read 'item', not legacy 'track'", mapped.isEmpty())
    }

    @Test
    fun `parses liked songs from me_tracks cleanly with null defenses`() {
        val likedJson = """
            {
              "total": 2,
              "items": [
                {
                  "added_at": "2026-04-01T12:00:00Z",
                  "track": {
                    "id": "liked_1",
                    "uri": "spotify:track:liked_1",
                    "name": "Born To Be Wild",
                    "duration_ms": 208000,
                    "artists": [{ "name": "Steppenwolf" }]
                  }
                },
                {
                  "track": null
                }
              ]
            }
        """.trimIndent()

        val response = gson.fromJson(likedJson, SpotifyLikedTracksResponse::class.java)
        val mapped = SpotifyResponseMapper.mapLikedTracks(response)

        assertEquals(1, mapped.size)
        assertEquals("Born To Be Wild", mapped[0].title)
        assertEquals("Steppenwolf", mapped[0].artist)
        assertEquals("spotify:track:liked_1", mapped[0].trackUri)
    }

    @Test
    fun `SpotifyWebApi targets 2026 playlists_id_items endpoint and never legacy tracks`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "total": 1,
                      "items": [
                        {
                          "item": {
                            "id": "t99",
                            "uri": "spotify:track:t99",
                            "name": "Turbo Killer",
                            "artists": [{ "name": "Carpenter Brut" }]
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val api = SpotifyNetworkModule.createSpotifyWebApi(baseUrl = server.url("/").toString())
        val response = api.getPlaylistItems(
            authorization = "Bearer test_token",
            playlistId = "pl_12345"
        )

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("Bearer test_token", request.getHeader("Authorization"))
        assertTrue(
            "Must call /playlists/{id}/items, actual: ${request.path}",
            request.path?.startsWith("/playlists/pl_12345/items") == true
        )
        assertFalse(
            "Must NEVER call deprecated /playlists/{id}/tracks",
            request.path?.contains("/playlists/pl_12345/tracks") == true
        )

        val tracks = SpotifyResponseMapper.mapPlaylistItems("pl_12345", response)
        assertEquals(1, tracks.size)
        assertEquals("Turbo Killer", tracks[0].title)
        assertEquals("Carpenter Brut", tracks[0].artist)
    }
}
