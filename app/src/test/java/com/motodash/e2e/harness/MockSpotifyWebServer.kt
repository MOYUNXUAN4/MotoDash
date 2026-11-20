package com.motodash.e2e.harness

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.CopyOnWriteArrayList

class MockSpotifyWebServer {

    private val server = MockWebServer()

    val recordedPaths: MutableList<String> = CopyOnWriteArrayList()
    @Volatile
    var shouldSimulateTokenExpired: Boolean = false
    @Volatile
    var shouldSimulateNetworkDrop: Boolean = false
    @Volatile
    var shouldSimulateRateLimit: Boolean = false
    @Volatile
    var customItemsResponse: String? = null

    fun start(): String {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                recordedPaths.add(path)

                if (shouldSimulateNetworkDrop) {
                    return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                }

                if (shouldSimulateTokenExpired && !path.contains("/api/token")) {
                    return MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"error":{"status":401,"message":"The access token expired"}}""")
                }

                if (shouldSimulateRateLimit) {
                    return MockResponse()
                        .setResponseCode(429)
                        .setHeader("Retry-After", "30")
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"error":{"status":429,"message":"API rate limit exceeded"}}""")
                }

                return when {
                    path.contains("/v1/me/playlists") -> {
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(SAMPLE_PLAYLISTS_JSON)
                    }
                    path.contains("/items") -> {
                        val body = customItemsResponse ?: SAMPLE_2026_PLAYLIST_ITEMS_JSON
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(body)
                    }
                    path.contains("/v1/me/tracks") -> {
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(SAMPLE_LIKED_SONGS_JSON)
                    }
                    path.contains("/api/token") -> {
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(SAMPLE_TOKEN_JSON)
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        return server.url("/").toString()
    }

    fun shutdown() {
        server.shutdown()
    }

    companion object {
        val SAMPLE_2026_PLAYLIST_ITEMS_JSON = """
        {
          "items": [
            {
              "added_at": "2026-01-01T00:00:00Z",
              "is_local": false,
              "item": {
                "id": "track_001",
                "name": "Highway to Hell",
                "uri": "spotify:track:highway_001",
                "duration_ms": 208000,
                "type": "track",
                "artists": [{"name": "AC/DC", "uri": "spotify:artist:acdc"}],
                "album": {"name": "Highway to Hell", "images": [{"url": "https://img.spotify.com/1.jpg"}]}
              }
            },
            {
              "added_at": "2026-01-02T00:00:00Z",
              "is_local": false,
              "item": null
            },
            {
              "added_at": "2026-01-03T00:00:00Z",
              "is_local": false,
              "item": {
                "id": "episode_001",
                "name": "Moto Podcast #42",
                "uri": "spotify:episode:pod_001",
                "type": "episode"
              }
            },
            {
              "added_at": "2026-01-04T00:00:00Z",
              "is_local": false,
              "item": {
                "id": "track_002",
                "name": "Born to Be Wild",
                "uri": "spotify:track:wild_002",
                "duration_ms": 210000,
                "type": "track",
                "artists": [{"name": "Steppenwolf", "uri": "spotify:artist:steppenwolf"}],
                "album": {"name": "Steppenwolf", "images": [{"url": "https://img.spotify.com/2.jpg"}]}
              }
            }
          ],
          "total": 4
        }
        """.trimIndent()

        val SAMPLE_PLAYLISTS_JSON = """
        {
          "items": [
            {
              "id": "pl_rock",
              "name": "Rock Riding Anthems",
              "uri": "spotify:playlist:pl_rock",
              "tracks": {"total": 45},
              "images": [{"url": "https://img.spotify.com/rock.jpg"}]
            },
            {
              "id": "pl_ambient",
              "name": "Night Cruise Ambient",
              "uri": "spotify:playlist:pl_ambient",
              "tracks": {"total": 28},
              "images": [{"url": "https://img.spotify.com/night.jpg"}]
            }
          ],
          "total": 2
        }
        """.trimIndent()

        val SAMPLE_LIKED_SONGS_JSON = """
        {
          "items": [
            {
              "added_at": "2026-01-05T00:00:00Z",
              "track": {
                "id": "liked_001",
                "name": "Thunderstruck",
                "uri": "spotify:track:thunder_001",
                "duration_ms": 292000,
                "artists": [{"name": "AC/DC"}],
                "album": {"name": "The Razors Edge", "images": [{"url": "https://img.spotify.com/thunder.jpg"}]}
              }
            }
          ],
          "total": 1
        }
        """.trimIndent()

        val SAMPLE_TOKEN_JSON = """
        {
          "access_token": "mock_access_token_2026",
          "token_type": "Bearer",
          "expires_in": 3600,
          "refresh_token": "mock_refresh_token_2026",
          "scope": "playlist-read-private playlist-read-collaborative user-library-read app-remote-control"
        }
        """.trimIndent()
    }
}
