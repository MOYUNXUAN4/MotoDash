package com.motodash.ui

import com.motodash.location.DashboardMath
import com.motodash.location.GpsSignalStatus
import com.motodash.spotify.SpotifyPlaylistDomain
import com.motodash.spotify.SpotifyTrackDomain
import com.motodash.telemetry.TripSummary

enum class TripPhase { IDLE, RECORDING }

data class DashboardUiState(
    val speedText: String = "--",
    val displaySpeedKmh: Float = 0f,
    val speedColorHex: Long = DashboardMath.COLOR_NORMAL_WHITE,
    val isTimedOut: Boolean = true,
    val gpsStatus: GpsSignalStatus = GpsSignalStatus.RED,
    val accuracyMeters: Float = Float.POSITIVE_INFINITY,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val bearingDegrees: Float = 0f,
    val dynamicZoom: Double = 16.5,
    val sunlightMode: Boolean = true,
    val maskOpacity: Float = DashboardMath.MASK_OPACITY_SUNLIGHT,
    val tripText: String = "Trip 0.0 km",
    val tripPhase: TripPhase = TripPhase.IDLE,
    val tripElapsedText: String = "00:00",
    val formattedTime: String = "--:--",
    val batteryPercent: Int? = null,
    val formattedBattery: String = "--%",
    val isCharging: Boolean = false,
    val hasLocationPermission: Boolean = false
)

data class MusicUiState(
    val isAuthenticated: Boolean = false,
    val hasClientIdConfigured: Boolean = false,
    val configuredClientId: String = "",
    val isRemoteConnected: Boolean = false,
    val isRemoteConnecting: Boolean = false,
    val isPlaying: Boolean = false,
    val currentTrackUri: String? = null,
    val currentTrackTitle: String? = null,
    val currentTrackArtist: String? = null,
    val playlists: List<SpotifyPlaylistDomain> = emptyList(),
    val selectedPlaylistId: String? = null,
    val tracks: List<SpotifyTrackDomain> = emptyList(),
    val isRefreshing: Boolean = false,
    val statusMessage: String? = null
)

data class MotoDashUiState(
    val dashboard: DashboardUiState = DashboardUiState(),
    val music: MusicUiState = MusicUiState(),
    val tripSummary: TripSummary? = null
)
