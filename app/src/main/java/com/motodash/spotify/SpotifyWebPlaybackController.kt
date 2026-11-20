package com.motodash.spotify

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response

/** Serializes playback commands and publishes only states confirmed by Spotify. */
class SpotifyWebPlaybackController(
    private val api: SpotifyWebApi,
    private val accessToken: suspend () -> String?
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(RemotePlaybackState())
    val state = mutableState.asStateFlow()

    suspend fun sync(): Result<Unit> = request {
        val response = api.getPlaybackState(it)
        when (response.code()) {
            200 -> {
                val body = response.body() ?: error("Empty playback response")
                val track = body.item
                mutableState.value = RemotePlaybackState(
                    isConnected = true,
                    isPlaying = body.isPlaying,
                    currentTrackUri = track?.uri,
                    currentTrackTitle = track?.name,
                    currentTrackArtist = track?.artists.orEmpty()
                        .mapNotNull { artist -> artist?.name?.trim()?.takeIf(String::isNotEmpty) }
                        .joinToString(", ").takeIf(String::isNotEmpty)
                )
            }
            204 -> mutableState.value = RemotePlaybackState()
            else -> throw playbackError(response)
        }
    }.onFailure {
        // A lost session/network must not leave a permanently green, playing indicator.
        mutableState.value = mutableState.value.copy(isConnected = false, isPlaying = false)
    }

    suspend fun play(track: SpotifyTrackDomain, followingUris: List<String>): Result<Unit> = request { bearer ->
        val queue = followingUris.takeIf { it.firstOrNull() == track.trackUri }
            ?.take(50) ?: listOf(track.trackUri)
        val body = SpotifyPlayRequest(uris = queue)
        var response = api.startPlayback(bearer, body = body)
        if (response.code() == 404) {
            val devices = api.getAvailableDevices(bearer).devices.orEmpty().filterNotNull()
            val device = devices.firstOrNull { it.isActive }
                ?: devices.firstOrNull { it.type.equals("Smartphone", ignoreCase = true) }
                ?: devices.firstOrNull()
            if (device?.id != null) {
                response = api.startPlayback(bearer, deviceId = device.id, body = body)
            }
        }
        if (!response.isSuccessful) throw playbackError(response)
        mutableState.value = RemotePlaybackState(
            isConnected = true, isPlaying = true, currentTrackUri = track.trackUri,
            currentTrackTitle = track.title, currentTrackArtist = track.artist
        )
    }

    suspend fun toggle(): Result<Unit> = request { bearer ->
        val wasPlaying = mutableState.value.isPlaying
        val response = if (wasPlaying) api.pausePlayback(bearer) else api.startPlayback(bearer)
        if (!response.isSuccessful) throw playbackError(response)
        mutableState.value = mutableState.value.copy(isConnected = true, isPlaying = !wasPlaying)
    }

    suspend fun skip(next: Boolean): Result<Unit> = request { bearer ->
        val response = if (next) api.skipToNext(bearer) else api.skipToPrevious(bearer)
        if (!response.isSuccessful) throw playbackError(response)
        // The next track is unknown until the subsequent sync.
        mutableState.value = mutableState.value.copy(
            currentTrackUri = null, currentTrackTitle = null, currentTrackArtist = null
        )
    }

    fun disconnect() {
        mutableState.value = RemotePlaybackState()
    }

    private suspend fun request(block: suspend (String) -> Unit): Result<Unit> = mutex.withLock {
        try {
            val token = accessToken() ?: throw PlaybackException(401, "Sign in to Spotify to play")
            block("Bearer $token")
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun playbackError(response: Response<*>): PlaybackException {
        val detail = response.errorBody()?.string().orEmpty()
        val message = when {
            response.code() == 404 -> "No active Spotify device"
            detail.contains("PREMIUM_REQUIRED", ignoreCase = true) -> "Spotify Premium required"
            response.code() == 401 || response.code() == 403 -> "Tap here to re-authorize Spotify playback"
            response.code() == 429 -> "Spotify is busy. Try again shortly."
            else -> "Playback error (${response.code()})"
        }
        return PlaybackException(response.code(), message)
    }
}

class PlaybackException(val statusCode: Int, message: String) : Exception(message)
