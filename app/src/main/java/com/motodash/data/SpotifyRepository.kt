package com.motodash.data

import com.motodash.data.db.PlaylistEntity
import com.motodash.data.db.SpotifyDao
import com.motodash.data.db.TrackEntity
import com.motodash.spotify.SpotifyAuthManager
import com.motodash.spotify.SpotifyConfig
import com.motodash.spotify.SpotifyPlaylistDomain
import com.motodash.spotify.SpotifyResponseMapper
import com.motodash.spotify.SpotifyTrackDomain
import com.motodash.spotify.SpotifyWebApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class SpotifyRepository(
    private val webApi: SpotifyWebApi,
    private val dao: SpotifyDao,
    private val authManager: SpotifyAuthManager
) {
    fun observePlaylists(): Flow<List<SpotifyPlaylistDomain>> =
        dao.observePlaylists().map { list ->
            list.map { entity ->
                SpotifyPlaylistDomain(
                    id = entity.id,
                    name = entity.name,
                    trackCount = entity.trackCount,
                    isLikedSongs = entity.isLikedSongs
                )
            }
        }

    fun observeTracksForPlaylist(playlistId: String): Flow<List<SpotifyTrackDomain>> =
        dao.observeTracksForPlaylist(playlistId).map { list ->
            list.map { entity ->
                SpotifyTrackDomain(
                    playlistId = entity.playlistId,
                    trackId = entity.trackId,
                    trackUri = entity.trackUri,
                    title = entity.title,
                    artist = entity.artist,
                    durationMs = entity.durationMs,
                    position = entity.position
                )
            }
        }

    suspend fun refreshPlaylists(): Result<List<SpotifyPlaylistDomain>> = refreshResult {
        val token = authManager.getValidAccessToken()
            ?: error("Not signed in to Spotify")
        val bearer = "Bearer $token"

        val userPlaylists = collectPages { offset, _ ->
            val response = webApi.getUserPlaylists(authorization = bearer, limit = PAGE_SIZE, offset = offset)
            Page(SpotifyResponseMapper.mapPlaylists(response), response.items.orEmpty().size,
                response.total, response.next)
        }.distinctBy { it.id }.filterNot { it.id == SpotifyConfig.LIKED_SONGS_PLAYLIST_ID }

        val now = System.currentTimeMillis()
        val entities = buildList {
            // Pin "Liked Songs" first for one-tap motorcycle access
            add(
                PlaylistEntity(
                    id = SpotifyConfig.LIKED_SONGS_PLAYLIST_ID,
                    name = SpotifyConfig.LIKED_SONGS_TITLE,
                    trackCount = 0,
                    isLikedSongs = true,
                    displayOrder = 0,
                    updatedAtMs = now
                )
            )
            userPlaylists.forEachIndexed { index, playlist ->
                add(
                    PlaylistEntity(
                        id = playlist.id,
                        name = playlist.name,
                        trackCount = playlist.trackCount,
                        isLikedSongs = false,
                        displayOrder = index + 1,
                        updatedAtMs = now
                    )
                )
            }
        }
        dao.replacePlaylists(entities)
        entities.map {
            SpotifyPlaylistDomain(
                id = it.id,
                name = it.name,
                trackCount = it.trackCount,
                isLikedSongs = it.isLikedSongs
            )
        }
    }

    suspend fun refreshTracksForPlaylist(playlistId: String): Result<List<SpotifyTrackDomain>> = refreshResult {
        val token = authManager.getValidAccessToken()
            ?: error("Not signed in to Spotify")
        val bearer = "Bearer $token"

        val domainTracks = collectPages { offset, storedCount ->
            if (playlistId == SpotifyConfig.LIKED_SONGS_PLAYLIST_ID) {
                val response = webApi.getLikedTracks(authorization = bearer, limit = PAGE_SIZE, offset = offset)
                Page(SpotifyResponseMapper.mapLikedTracks(response, playlistId, storedCount),
                    response.items.orEmpty().size, response.total, response.next)
            } else {
                // Keep the existing playlist items endpoint and its items[i].item parsing.
                val response = webApi.getPlaylistItems(
                    authorization = bearer, playlistId = playlistId, limit = PAGE_SIZE, offset = offset
                )
                Page(SpotifyResponseMapper.mapPlaylistItems(playlistId, response, storedCount),
                    response.items.orEmpty().size, response.total, response.next)
            }
        }

        val trackEntities = domainTracks.map { track ->
            TrackEntity(
                playlistId = playlistId,
                trackId = track.trackId,
                trackUri = track.trackUri,
                title = track.title,
                artist = track.artist,
                durationMs = track.durationMs,
                position = track.position
            )
        }
        dao.replaceTracksForPlaylist(playlistId, trackEntities)
        domainTracks
    }

    suspend fun hasCachedTracks(playlistId: String): Boolean {
        return dao.getTracksForPlaylist(playlistId).isNotEmpty()
    }

    private data class Page<T>(val values: List<T>, val rawCount: Int, val total: Int, val next: String?)

    /** Fetch everything before replacing Room's cache, so a later page failure preserves it. */
    private suspend fun <T> collectPages(fetch: suspend (Int, Int) -> Page<T>): List<T> {
        val values = mutableListOf<T>()
        var offset = 0
        repeat(MAX_PAGES) {
            currentCoroutineContext().ensureActive()
            val page = fetch(offset, values.size)
            values.addAll(page.values)
            val nextOffset = offset.toLong() + page.rawCount
            val hasMore = !page.next.isNullOrBlank() || nextOffset < page.total
            if (!hasMore) return values
            check(page.rawCount > 0 && nextOffset <= Int.MAX_VALUE) {
                "Spotify pagination did not advance; cached library retained"
            }
            offset = nextOffset.toInt()
        }
        error("Spotify pagination exceeded its safety limit; cached library retained")
    }

    private suspend fun <T> refreshResult(block: suspend () -> T): Result<T> = try {
        currentCoroutineContext().ensureActive()
        val value = block()
        currentCoroutineContext().ensureActive()
        Result.success(value)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        currentCoroutineContext().ensureActive()
        Result.failure(error)
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val MAX_PAGES = 10_000
    }
}
