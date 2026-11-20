package com.motodash.spotify

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.lang.ref.WeakReference

private const val TAG = "MotoDashRemote"

data class RemotePlaybackState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isPlaying: Boolean = false,
    val currentTrackUri: String? = null,
    val currentTrackTitle: String? = null,
    val currentTrackArtist: String? = null,
    val errorMessage: String? = null
)

interface PlaybackController {
    val playbackState: StateFlow<RemotePlaybackState>
    fun connect(clientId: String, hostContext: Context? = null)
    fun disconnect()
    fun playTrack(trackUri: String, hostContext: Context? = null)
    fun togglePlayPause(hostContext: Context? = null)
    fun skipNext()
    fun skipPrevious()
}

class SpotifyRemoteController(
    context: Context
) : PlaybackController {
    private val appContext = context.applicationContext ?: context
    private var hostContextRef: WeakReference<Context>? = null

    private val _playbackState = MutableStateFlow(RemotePlaybackState())
    override val playbackState: StateFlow<RemotePlaybackState> = _playbackState.asStateFlow()

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var playerStateSubscription: Subscription<PlayerState>? = null
    private var lastClientId: String? = null
    private var pendingTrackUri: String? = null

    override fun connect(clientId: String, hostContext: Context?) {
        if (hostContext != null) {
            hostContextRef = WeakReference(hostContext)
        }
        val trimmedId = clientId.trim()
        if (trimmedId.isEmpty()) {
            _playbackState.update {
                it.copy(
                    isConnected = false,
                    isConnecting = false,
                    errorMessage = "Configure Spotify Client ID to connect"
                )
            }
            return
        }

        if (spotifyAppRemote?.isConnected == true) {
            return
        }

        lastClientId = trimmedId
        val connectContext = hostContext ?: hostContextRef?.get() ?: appContext
        Log.i(TAG, "Connecting SpotifyAppRemote with clientId=${trimmedId.take(6)}... context=${connectContext.javaClass.simpleName}")
        _playbackState.update { it.copy(isConnecting = true, errorMessage = null) }

        val connectionParams = ConnectionParams.Builder(trimmedId)
            .setRedirectUri(SpotifyConfig.REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(
            connectContext,
            connectionParams,
            object : Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    Log.i(TAG, "SpotifyAppRemote connected successfully!")
                    spotifyAppRemote = appRemote
                    _playbackState.update {
                        it.copy(
                            isConnected = true,
                            isConnecting = false,
                            errorMessage = null
                        )
                    }
                    subscribeToPlayerState(appRemote)

                    val queuedUri = pendingTrackUri
                    pendingTrackUri = null
                    if (!queuedUri.isNullOrBlank()) {
                        Log.i(TAG, "Playing queued track after connect: $queuedUri")
                        executePlay(appRemote, queuedUri)
                    }
                }

                override fun onFailure(throwable: Throwable) {
                    Log.e(TAG, "SpotifyAppRemote connection failed: ${throwable.javaClass.simpleName}: ${throwable.message}", throwable)
                    spotifyAppRemote = null
                    val friendlyMsg = buildFriendlyError(throwable)
                    _playbackState.update {
                        it.copy(
                            isConnected = false,
                            isConnecting = false,
                            errorMessage = friendlyMsg
                        )
                    }
                }
            }
        )
    }

    private fun buildFriendlyError(throwable: Throwable): String {
        val simpleName = throwable.javaClass.simpleName
        val raw = throwable.message?.trim().orEmpty()
        return when {
            simpleName.contains("CouldNotFindSpotifyApp", ignoreCase = true) ->
                "Spotify app not installed"
            simpleName.contains("NotLoggedIn", ignoreCase = true) ->
                "Please sign in to Spotify app first"
            simpleName.contains("AuthenticationFailed", ignoreCase = true) ||
                simpleName.contains("UserNotAuthorized", ignoreCase = true) ->
                "Spotify auth failed ($simpleName): check SHA-1 & com.motodash in Dashboard"
            raw.isNotEmpty() -> "$simpleName: $raw"
            else -> "Spotify Remote error: $simpleName"
        }
    }

    private fun subscribeToPlayerState(appRemote: SpotifyAppRemote) {
        playerStateSubscription?.cancel()
        val subscription = appRemote.playerApi.subscribeToPlayerState()
        subscription
            .setEventCallback { state ->
                val track = state.track
                val artistsText = track?.artists
                    ?.mapNotNull { it?.name?.takeIf { name -> name.isNotBlank() } }
                    ?.joinToString(", ")
                    ?.takeIf { it.isNotBlank() }
                    ?: track?.artist?.name

                _playbackState.update {
                    it.copy(
                        isConnected = true,
                        isPlaying = !state.isPaused,
                        currentTrackUri = track?.uri,
                        currentTrackTitle = track?.name,
                        currentTrackArtist = artistsText,
                        errorMessage = null
                    )
                }
            }
            .setErrorCallback { err ->
                Log.e(TAG, "PlayerState subscription error: ${err.message}", err)
                _playbackState.update {
                    it.copy(errorMessage = err.message ?: "Player state error")
                }
            }
        playerStateSubscription = subscription
    }

    override fun disconnect() {
        playerStateSubscription?.cancel()
        playerStateSubscription = null
        spotifyAppRemote?.let { remote ->
            SpotifyAppRemote.disconnect(remote)
        }
        spotifyAppRemote = null
        _playbackState.update {
            it.copy(isConnected = false, isConnecting = false)
        }
    }

    override fun playTrack(trackUri: String, hostContext: Context?) {
        if (hostContext != null) {
            hostContextRef = WeakReference(hostContext)
        }
        val remote = spotifyAppRemote
        if (remote == null || !remote.isConnected) {
            Log.w(TAG, "Remote not connected yet; queuing $trackUri and reconnecting...")
            pendingTrackUri = trackUri
            lastClientId?.let { connect(it, hostContext) }
            return
        }
        executePlay(remote, trackUri)
    }

    private fun executePlay(remote: SpotifyAppRemote, trackUri: String) {
        Log.i(TAG, "Calling playerApi.play($trackUri)")
        remote.playerApi.play(trackUri)
            .setResultCallback {
                Log.i(TAG, "playerApi.play succeeded for $trackUri")
            }
            .setErrorCallback { err ->
                Log.e(TAG, "playerApi.play failed for $trackUri: ${err.message}", err)
                _playbackState.update {
                    it.copy(errorMessage = "Play failed: ${err.message ?: err.javaClass.simpleName}")
                }
            }
    }

    override fun togglePlayPause(hostContext: Context?) {
        if (hostContext != null) {
            hostContextRef = WeakReference(hostContext)
        }
        val remote = spotifyAppRemote
        if (remote == null || !remote.isConnected) {
            lastClientId?.let { connect(it, hostContext) }
            return
        }
        if (_playbackState.value.isPlaying) {
            remote.playerApi.pause()
                .setErrorCallback { err ->
                    Log.e(TAG, "pause failed: ${err.message}", err)
                }
        } else {
            remote.playerApi.resume()
                .setErrorCallback { err ->
                    Log.e(TAG, "resume failed: ${err.message}", err)
                }
        }
    }

    override fun skipNext() {
        val remote = spotifyAppRemote ?: return
        if (remote.isConnected) {
            remote.playerApi.skipNext()
                .setErrorCallback { err -> Log.e(TAG, "skipNext failed: ${err.message}", err) }
        }
    }

    override fun skipPrevious() {
        val remote = spotifyAppRemote ?: return
        if (remote.isConnected) {
            remote.playerApi.skipPrevious()
                .setErrorCallback { err -> Log.e(TAG, "skipPrevious failed: ${err.message}", err) }
        }
    }
}
