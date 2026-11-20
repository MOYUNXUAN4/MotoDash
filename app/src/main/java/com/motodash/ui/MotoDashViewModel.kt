package com.motodash.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.motodash.BuildConfig
import com.motodash.data.SpotifyNetworkModule
import com.motodash.data.SpotifyRepository
import com.motodash.data.db.MotoDashDatabase
import com.motodash.location.DashboardMath
import com.motodash.spotify.PlaybackController
import com.motodash.spotify.PlaybackException
import com.motodash.spotify.SpotifyWebPlaybackController
import com.motodash.spotify.SharedPrefsTokenStore
import com.motodash.spotify.SpotifyAuthManager
import com.motodash.spotify.SpotifyConfig
import com.motodash.spotify.SpotifyRemoteController
import com.motodash.spotify.SpotifyTrackDomain
import com.motodash.telemetry.SystemTelemetryProvider
import com.motodash.ui.components.formatTripDuration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "MotoDashVM"

@OptIn(ExperimentalCoroutinesApi::class)
class MotoDashViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val tripSession = com.motodash.telemetry.TripSession.get(application)
    private val locationTracker = tripSession.locationTracker
    private val telemetryProvider = SystemTelemetryProvider(application)
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val tokenStore = SharedPrefsTokenStore(application)
    private val authApi = SpotifyNetworkModule.createSpotifyAuthApi()
    private val webApi = SpotifyNetworkModule.createSpotifyWebApi()
    private val authManager = SpotifyAuthManager(
        tokenStore = tokenStore,
        authApi = authApi,
        defaultClientId = BuildConfig.SPOTIFY_CLIENT_ID
    )
    private val database = MotoDashDatabase.getInstance(application)
    private val repository = SpotifyRepository(
        webApi = webApi,
        dao = database.spotifyDao(),
        authManager = authManager
    )
    private val playbackController: PlaybackController = SpotifyRemoteController(application)

    private val tripUiFlow = MutableStateFlow(TripUi(TripPhase.IDLE, "Trip 0.0 km", "00:00"))
    private val tripSummaryFlow = tripSession.summary
    private val hasLocationPermissionFlow = MutableStateFlow(locationTracker.hasLocationPermission())

    private val selectedPlaylistIdFlow = MutableStateFlow<String?>(null)
    private val isRefreshingFlow = MutableStateFlow(false)
    private val isAuthenticatedFlow = MutableStateFlow(authManager.hasValidSession())
    private val clientIdFlow = MutableStateFlow(authManager.getConfiguredClientId())
    private val statusMessageFlow = MutableStateFlow<String?>(null)
    private val webPlayback = SpotifyWebPlaybackController(webApi, authManager::getValidAccessToken)
    private val webPlaybackStateFlow = webPlayback.state
    private var webPlaybackPollJob: Job? = null

    private val _uiState = MutableStateFlow(MotoDashUiState())
    val uiState: StateFlow<MotoDashUiState> = _uiState.asStateFlow()

    init {
        observeLocationForTrip()
        observeDashboardState()
        observeMusicState()
        viewModelScope.launch {
            tripSummaryFlow.collect { summary ->
                _uiState.update { it.copy(tripSummary = summary) }
            }
        }
    }

    private fun observeLocationForTrip() {
        viewModelScope.launch {
            tripSession.state.collect { trip ->
                tripUiFlow.value = TripUi(
                    if (trip.recording) TripPhase.RECORDING else TripPhase.IDLE,
                    trip.tripText,
                    formatTripDuration(trip.elapsedMs)
                )
            }
        }
    }

    fun toggleTrip() {
        if (!tripSession.toggle()) {
            android.widget.Toast.makeText(getApplication(), "Unable to start recording. Check location permission and retry.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun dismissTripSummary() { tripSession.dismissSummary() }

    private data class TripUi(val phase: TripPhase, val tripText: String, val elapsedText: String)

    private fun observeDashboardState() {
        viewModelScope.launch {
            combine(
                locationTracker.speedState,
                locationTracker.latestReading,
                telemetryProvider.telemetryState,
                tripUiFlow,
                hasLocationPermissionFlow
            ) { speedState, reading, telemetry, tripUi, hasPerm ->
                DashboardUiState(
                    speedText = DashboardMath.formatSpeedText(speedState),
                    displaySpeedKmh = speedState.displaySpeedKmh,
                    speedColorHex = DashboardMath.getSpeedColorHex(speedState.displaySpeedKmh),
                    isTimedOut = speedState.isTimedOut,
                    gpsStatus = speedState.status,
                    accuracyMeters = speedState.accuracyMeters,
                    latitude = reading?.latitude,
                    longitude = reading?.longitude,
                    bearingDegrees = speedState.effectiveBearingDegrees,
                    dynamicZoom = speedState.dynamicZoom,
                    sunlightMode = true,
                    maskOpacity = DashboardMath.getMaskOpacity(true),
                    tripText = tripUi.tripText,
                    tripPhase = tripUi.phase,
                    tripElapsedText = tripUi.elapsedText,
                    formattedTime = telemetry.formattedTime,
                    batteryPercent = telemetry.batteryPercent,
                    formattedBattery = telemetry.formattedBattery,
                    isCharging = telemetry.isCharging,
                    hasLocationPermission = hasPerm
                )
            }.collect { dashboardState ->
                _uiState.update { it.copy(dashboard = dashboardState) }
            }
        }
    }

    private fun observeMusicState() {
        val playlistsFlow = repository.observePlaylists()
        val tracksFlow = selectedPlaylistIdFlow.flatMapLatest { playlistId ->
            if (playlistId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeTracksForPlaylist(playlistId)
            }
        }

        // Auto-select first playlist once playlists arrive from Room cache or network
        viewModelScope.launch {
            playlistsFlow.collect { playlists ->
                val currentSelection = selectedPlaylistIdFlow.value
                if (playlists.isNotEmpty() &&
                    (currentSelection == null || playlists.none { it.id == currentSelection })
                ) {
                    val defaultId = playlists.first().id
                    selectedPlaylistIdFlow.value = defaultId
                    loadTracksIfNeeded(defaultId, forceRefresh = false)
                }
            }
        }

        val effectivePlaybackFlow = combine(
            playbackController.playbackState,
            webPlaybackStateFlow
        ) { remote, web ->
            if (remote.isConnected) remote else web.copy(
                isConnected = web.isConnected || remote.isConnected,
                isConnecting = remote.isConnecting
            )
        }

        viewModelScope.launch {
            combine(
                combine(playlistsFlow, selectedPlaylistIdFlow, tracksFlow) { p, sel, t ->
                    Triple(p, sel, t)
                },
                effectivePlaybackFlow,
                combine(
                    isAuthenticatedFlow,
                    clientIdFlow,
                    isRefreshingFlow,
                    statusMessageFlow
                ) { auth, cid, refreshing, msg ->
                    Quadruple(auth, cid, refreshing, msg)
                }
            ) { (playlists, selectedId, tracks), playback, (isAuth, clientId, isRefreshing, statusMsg) ->
                MusicUiState(
                    isAuthenticated = isAuth,
                    hasClientIdConfigured = clientId.isNotBlank(),
                    configuredClientId = clientId,
                    isRemoteConnected = playback.isConnected,
                    isRemoteConnecting = playback.isConnecting,
                    isPlaying = playback.isPlaying,
                    currentTrackUri = playback.currentTrackUri,
                    currentTrackTitle = playback.currentTrackTitle,
                    currentTrackArtist = playback.currentTrackArtist,
                    playlists = playlists,
                    selectedPlaylistId = selectedId,
                    tracks = tracks,
                    isRefreshing = isRefreshing,
                    statusMessage = statusMsg ?: playback.errorMessage
                )
            }.collect { musicState ->
                _uiState.update { it.copy(music = musicState) }
            }
        }
    }

    fun onStartForeground(hostContext: Context? = null) {
        telemetryProvider.start(viewModelScope)
        val hasPerm = locationTracker.hasLocationPermission()
        hasLocationPermissionFlow.value = hasPerm
        tripSession.setDashboardVisible(true)

        val clientId = authManager.getConfiguredClientId()
        clientIdFlow.value = clientId
        isAuthenticatedFlow.value = authManager.hasValidSession()

        if (clientId.isNotBlank() && isAuthenticatedFlow.value) {
            playbackController.connect(clientId, hostContext)
            refreshLibraryIfEmpty()
            startWebPlaybackPolling()
        }
    }

    fun onStopForeground() {
        telemetryProvider.stop()
        tripSession.setDashboardVisible(false)
        playbackController.disconnect()
        stopWebPlaybackPolling()
        webPlayback.disconnect()
    }

    private fun startWebPlaybackPolling() {
        stopWebPlaybackPolling()
        webPlaybackPollJob = viewModelScope.launch {
            while (isActive) {
                if (!playbackController.playbackState.value.isConnected && authManager.hasValidSession()) {
                    syncWebPlaybackState()
                }
                delay(4_000L)
            }
        }
    }

    private fun stopWebPlaybackPolling() {
        webPlaybackPollJob?.cancel()
        webPlaybackPollJob = null
    }

    private suspend fun syncWebPlaybackState() {
        webPlayback.sync().onFailure { error ->
            Log.d(TAG, "Playback sync unavailable: ${error.message}")
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        val hasPerm = granted || locationTracker.hasLocationPermission()
        hasLocationPermissionFlow.value = hasPerm
        tripSession.setDashboardVisible(true)
    }

    /** Long-press on the trip button: discard the idle trip counter (ignored while recording). */
    fun resetTrip() {
        tripSession.reset()
    }

    fun updateSpotifyClientId(newClientId: String) {
        val trimmed = newClientId.trim()
        authManager.saveClientId(trimmed)
        clientIdFlow.value = trimmed
        statusMessageFlow.value = if (trimmed.isNotBlank()) {
            "Client ID saved. Tap Connect Spotify."
        } else {
            "Spotify Client ID cleared."
        }
    }

    fun startSpotifyLogin(context: Context) {
        val clientId = authManager.getConfiguredClientId()
        if (clientId.isBlank()) {
            statusMessageFlow.value = "Set Spotify Client ID first"
            return
        }
        val result = authManager.startAuthorization(context)
        result.onFailure { err ->
            statusMessageFlow.value = err.message ?: "Unable to launch Spotify login"
        }
    }

    fun handleAuthCallback(uri: Uri?, hostContext: Context? = null) {
        if (uri == null) return
        if (uri.scheme != SpotifyConfig.CALLBACK_SCHEME || uri.host != SpotifyConfig.CALLBACK_HOST) {
            return
        }
        viewModelScope.launch {
            isRefreshingFlow.value = true
            statusMessageFlow.value = "Connecting to Spotify..."
            val result = authManager.handleAuthRedirect(uri)
            result.onSuccess {
                isAuthenticatedFlow.value = true
                statusMessageFlow.value = null
                val clientId = authManager.getConfiguredClientId()
                if (clientId.isNotBlank()) {
                    playbackController.connect(clientId, hostContext)
                }
                refreshLibrary()
                startWebPlaybackPolling()
            }.onFailure { err ->
                isRefreshingFlow.value = false
                statusMessageFlow.value = err.message ?: "Spotify sign-in failed"
            }
        }
    }

    fun selectPlaylist(playlistId: String) {
        if (selectedPlaylistIdFlow.value == playlistId) return
        selectedPlaylistIdFlow.value = playlistId
        loadTracksIfNeeded(playlistId, forceRefresh = false)
    }

    fun refreshLibrary() {
        if (!authManager.hasValidSession()) {
            statusMessageFlow.value = "Connect Spotify to load playlists"
            return
        }
        viewModelScope.launch {
            isRefreshingFlow.value = true
            statusMessageFlow.value = null
            val playlistsResult = repository.refreshPlaylists()
            playlistsResult.onSuccess { playlists ->
                val targetId = selectedPlaylistIdFlow.value
                    ?.takeIf { id -> playlists.any { it.id == id } }
                    ?: playlists.firstOrNull()?.id
                if (targetId != null) {
                    selectedPlaylistIdFlow.value = targetId
                    repository.refreshTracksForPlaylist(targetId).onFailure { err ->
                        statusMessageFlow.value = err.message ?: "Failed to load tracks"
                    }
                }
            }.onFailure { err ->
                statusMessageFlow.value = err.message ?: "Failed to refresh playlists"
            }
            isRefreshingFlow.value = false
        }
    }

    private fun refreshLibraryIfEmpty() {
        viewModelScope.launch {
            val currentPlaylists = _uiState.value.music.playlists
            if (currentPlaylists.isEmpty()) {
                refreshLibrary()
            }
        }
    }

    private fun loadTracksIfNeeded(playlistId: String, forceRefresh: Boolean) {
        viewModelScope.launch {
            if (!forceRefresh && repository.hasCachedTracks(playlistId)) {
                return@launch
            }
            if (!authManager.hasValidSession()) return@launch
            isRefreshingFlow.value = true
            repository.refreshTracksForPlaylist(playlistId).onFailure { err ->
                statusMessageFlow.value = err.message ?: "Failed to fetch tracks"
            }
            isRefreshingFlow.value = false
        }
    }

    fun playTrack(track: SpotifyTrackDomain, hostContext: Context? = null) {
        if (playbackController.playbackState.value.isConnected) {
            playbackController.playTrack(track.trackUri, hostContext)
            return
        }
        viewModelScope.launch {
            statusMessageFlow.value = "Starting playback..."
            val tracks = _uiState.value.music.tracks
            val index = tracks.indexOfFirst { it.position == track.position && it.trackUri == track.trackUri }
            val queue = if (index >= 0) tracks.drop(index).take(50).map { it.trackUri } else listOf(track.trackUri)
            webPlayback.play(track, queue).onSuccess {
                statusMessageFlow.value = null
            }.onFailure { error ->
                if (error is PlaybackException &&
                    (error.statusCode == 404 || error.message == "Spotify Premium required")) {
                    statusMessageFlow.value = "Opening Spotify to play this track..."
                    openTrackInSpotifyApp(hostContext ?: getApplication(), track.trackUri)
                } else {
                    statusMessageFlow.value = error.message ?: "Unable to play. Check your connection."
                }
            }
        }
    }

    private fun openTrackInSpotifyApp(context: Context, trackUri: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trackUri)).apply {
                setPackage("com.spotify.music")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (error: Exception) {
            statusMessageFlow.value = "Unable to open Spotify. Check that it is installed."
            Log.w(TAG, "Unable to open Spotify", error)
        }
    }

    fun togglePlayPause(hostContext: Context? = null) {
        if (playbackController.playbackState.value.isConnected) {
            playbackController.togglePlayPause(hostContext)
            return
        }
        viewModelScope.launch {
            webPlayback.toggle().onSuccess {
                statusMessageFlow.value = null
            }.onFailure { error ->
                statusMessageFlow.value = error.message ?: "Playback unavailable; sending media button"
                // A media key has no confirmation, so keep the last confirmed UI state.
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
        }
    }

    fun skipNext() = skipTrack(next = true)
    fun skipPrevious() = skipTrack(next = false)

    private fun skipTrack(next: Boolean) {
        if (playbackController.playbackState.value.isConnected) {
            if (next) playbackController.skipNext() else playbackController.skipPrevious()
            return
        }
        viewModelScope.launch {
            webPlayback.skip(next).onSuccess {
                statusMessageFlow.value = null
                delay(600L)
                syncWebPlaybackState()
            }.onFailure { error ->
                statusMessageFlow.value = error.message ?: "Playback unavailable; sending media button"
                dispatchMediaKey(if (next) KeyEvent.KEYCODE_MEDIA_NEXT else KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val am = audioManager ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    override fun onCleared() {
        super.onCleared()
        onStopForeground()
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
