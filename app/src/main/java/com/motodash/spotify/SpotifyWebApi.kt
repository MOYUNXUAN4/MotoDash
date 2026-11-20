package com.motodash.spotify

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface SpotifyWebApi {
    @GET("me/playlists")
    suspend fun getUserPlaylists(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): SpotifyPlaylistsResponse

    /**
     * 2026 Spotify Web API compliant endpoint for playlist items.
     * Legacy /playlists/{id}/tracks is strictly forbidden.
     */
    @GET("playlists/{playlist_id}/items")
    suspend fun getPlaylistItems(
        @Header("Authorization") authorization: String,
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): SpotifyPlaylistItemsResponse

    @GET("me/tracks")
    suspend fun getLikedTracks(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): SpotifyLikedTracksResponse

    @GET("me/player")
    suspend fun getPlaybackState(
        @Header("Authorization") authorization: String
    ): Response<SpotifyPlayerStateResponse>

    @GET("me/player/devices")
    suspend fun getAvailableDevices(
        @Header("Authorization") authorization: String
    ): SpotifyDevicesResponse

    @PUT("me/player/play")
    suspend fun startPlayback(
        @Header("Authorization") authorization: String,
        @Query("device_id") deviceId: String? = null,
        @Body body: SpotifyPlayRequest? = null
    ): Response<Unit>

    @PUT("me/player/pause")
    suspend fun pausePlayback(
        @Header("Authorization") authorization: String,
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    @POST("me/player/next")
    suspend fun skipToNext(
        @Header("Authorization") authorization: String,
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>

    @POST("me/player/previous")
    suspend fun skipToPrevious(
        @Header("Authorization") authorization: String,
        @Query("device_id") deviceId: String? = null
    ): Response<Unit>
}
