package com.motodash.spotify

import com.google.gson.annotations.SerializedName

data class SpotifyTokenResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("token_type") val tokenType: String? = null,
    @SerializedName("expires_in") val expiresIn: Long = 3600L,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("scope") val scope: String? = null
)

data class SpotifyPlaylistsResponse(
    @SerializedName("items") val items: List<SpotifyPlaylistDto?>? = null,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("next") val next: String? = null
)

data class SpotifyPlaylistDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("items") val itemsSummary: SpotifyItemsSummaryDto? = null,
    @SerializedName("tracks") val tracksSummary: SpotifyItemsSummaryDto? = null
) {
    fun resolvedTrackCount(): Int = itemsSummary?.total ?: tracksSummary?.total ?: 0
}

data class SpotifyItemsSummaryDto(
    @SerializedName("total") val total: Int = 0
)

/**
 * 2026 Spotify Web API response for GET /v1/playlists/{playlist_id}/items.
 * Each array element places the track payload under the `item` field (not legacy `track`).
 */
data class SpotifyPlaylistItemsResponse(
    @SerializedName("items") val items: List<SpotifyPlaylistItemWrapper?>? = null,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("next") val next: String? = null
)

data class SpotifyPlaylistItemWrapper(
    @SerializedName("added_at") val addedAt: String? = null,
    @SerializedName("is_local") val isLocal: Boolean? = null,
    @SerializedName("item") val item: SpotifyTrackItemDto? = null
)

/**
 * Spotify Web API response for GET /v1/me/tracks (Liked Songs).
 */
data class SpotifyLikedTracksResponse(
    @SerializedName("items") val items: List<SpotifySavedTrackWrapper?>? = null,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("next") val next: String? = null
)

data class SpotifySavedTrackWrapper(
    @SerializedName("added_at") val addedAt: String? = null,
    @SerializedName("track") val track: SpotifyTrackItemDto? = null,
    @SerializedName("item") val item: SpotifyTrackItemDto? = null
) {
    fun resolvedTrack(): SpotifyTrackItemDto? = track ?: item
}

data class SpotifyTrackItemDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("uri") val uri: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("artists") val artists: List<SpotifyArtistDto?>? = null,
    @SerializedName("duration_ms") val durationMs: Long? = null,
    @SerializedName("is_local") val isLocal: Boolean? = null,
    @SerializedName("type") val type: String? = null
)

data class SpotifyArtistDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null
)

data class SpotifyPlayRequest(
    @SerializedName("uris") val uris: List<String>? = null
)

data class SpotifyPlayerStateResponse(
    @SerializedName("is_playing") val isPlaying: Boolean = false,
    @SerializedName("item") val item: SpotifyTrackItemDto? = null,
    @SerializedName("device") val device: SpotifyDeviceDto? = null
)

data class SpotifyDevicesResponse(
    @SerializedName("devices") val devices: List<SpotifyDeviceDto?>? = null
)

data class SpotifyDeviceDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("is_active") val isActive: Boolean = false
)

data class SpotifyPlaylistDomain(
    val id: String,
    val name: String,
    val trackCount: Int,
    val isLikedSongs: Boolean = false
)

data class SpotifyTrackDomain(
    val playlistId: String,
    val trackId: String,
    val trackUri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val position: Int
)

object SpotifyResponseMapper {
    fun mapPlaylists(response: SpotifyPlaylistsResponse?): List<SpotifyPlaylistDomain> {
        val rawItems = response?.items ?: return emptyList()
        return rawItems.mapNotNull { dto ->
            val id = dto?.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = dto.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled Playlist"
            SpotifyPlaylistDomain(
                id = id,
                name = name,
                trackCount = dto.resolvedTrackCount().coerceAtLeast(0),
                isLikedSongs = false
            )
        }
    }

    /**
     * Maps 2026 `/v1/playlists/{id}/items` response using `items[i].item` with full null defense.
     */
    fun mapPlaylistItems(
        playlistId: String,
        response: SpotifyPlaylistItemsResponse?,
        positionOffset: Int = 0
    ): List<SpotifyTrackDomain> {
        val wrappers = response?.items ?: return emptyList()
        val result = mutableListOf<SpotifyTrackDomain>()
        var nextPos = positionOffset
        for (wrapper in wrappers) {
            if (wrapper == null) continue
            if (wrapper.isLocal == true) continue
            val dto = wrapper.item ?: continue
            val domain = mapTrackDto(playlistId, dto, nextPos) ?: continue
            result.add(domain)
            nextPos++
        }
        return result
    }

    /**
     * Maps `/v1/me/tracks` response for Liked Songs with full null defense.
     */
    fun mapLikedTracks(
        response: SpotifyLikedTracksResponse?,
        playlistId: String = SpotifyConfig.LIKED_SONGS_PLAYLIST_ID,
        positionOffset: Int = 0
    ): List<SpotifyTrackDomain> {
        val wrappers = response?.items ?: return emptyList()
        val result = mutableListOf<SpotifyTrackDomain>()
        var nextPos = positionOffset
        for (wrapper in wrappers) {
            val dto = wrapper?.resolvedTrack() ?: continue
            val domain = mapTrackDto(playlistId, dto, nextPos) ?: continue
            result.add(domain)
            nextPos++
        }
        return result
    }

    private fun mapTrackDto(
        playlistId: String,
        dto: SpotifyTrackItemDto,
        position: Int
    ): SpotifyTrackDomain? {
        if (dto.isLocal == true) return null
        val uri = dto.uri?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val title = dto.name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val artistNames = dto.artists
            ?.mapNotNull { it?.name?.trim()?.takeIf { name -> name.isNotEmpty() } }
            ?.joinToString(", ")
            ?.takeIf { it.isNotEmpty() }
            ?: "Unknown Artist"
        val id = dto.id?.trim()?.takeIf { it.isNotEmpty() } ?: uri
        return SpotifyTrackDomain(
            playlistId = playlistId,
            trackId = id,
            trackUri = uri,
            title = title,
            artist = artistNames,
            durationMs = (dto.durationMs ?: 0L).coerceAtLeast(0L),
            position = position
        )
    }
}
