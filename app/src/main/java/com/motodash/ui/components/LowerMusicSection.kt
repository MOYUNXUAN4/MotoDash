package com.motodash.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motodash.spotify.SpotifyPlaylistDomain
import com.motodash.spotify.SpotifyTrackDomain
import com.motodash.ui.MusicUiState
import com.motodash.ui.theme.MotoDashColors

@Composable
fun LowerMusicSection(
    state: MusicUiState,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onRefresh: () -> Unit,
    onPlayTrack: (SpotifyTrackDomain) -> Unit,
    onConnectSpotify: () -> Unit,
    onOpenClientIdDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MotoDashColors.PureBlack)
            .navigationBarsPadding()
            .testTag("lower_music_section")
    ) {
        // 1. Fixed Top Playback Bar (strictly 64dp height, >= 56dp x 56dp buttons)
        PlaybackControlBar(
            trackTitle = state.currentTrackTitle,
            trackArtist = state.currentTrackArtist,
            isPlaying = state.isPlaying,
            isRemoteConnected = state.isRemoteConnected,
            isAuthenticated = state.isAuthenticated,
            onPrevious = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPrevious()
            },
            onTogglePlayPause = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onTogglePlayPause()
            },
            onNext = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onNext()
            },
            onHeaderClick = {
                if (!state.isAuthenticated) {
                    onConnectSpotify()
                }
            }
        )

        HorizontalDivider(thickness = 1.dp, color = MotoDashColors.HairlineDivider)

        // 2. Middle Playlist Pill Bar (44dp tall pills + rightmost Refresh button)
        PlaylistTagBar(
            playlists = state.playlists,
            selectedPlaylistId = state.selectedPlaylistId,
            isRefreshing = state.isRefreshing,
            showSettingsButton = !state.hasClientIdConfigured,
            onSelectPlaylist = { id ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectPlaylist(id)
            },
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRefresh()
            },
            onOpenClientIdDialog = onOpenClientIdDialog
        )

        HorizontalDivider(thickness = 1.dp, color = Color(0xFF141920))

        if (state.isAuthenticated && !state.statusMessage.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F1608))
                    .clickable(onClick = {
                        if (state.statusMessage.contains("re-authorize", ignoreCase = true)) {
                            onConnectSpotify()
                        } else {
                            onTogglePlayPause()
                        }
                    })
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = state.statusMessage,
                    color = MotoDashColors.SpeedAmber,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. Bottom Glove-Friendly Track List (strictly 62dp row height)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                !state.isAuthenticated && state.tracks.isEmpty() -> {
                    SpotifyConnectEmptyState(
                        hasClientId = state.hasClientIdConfigured,
                        statusMessage = state.statusMessage,
                        onConnectSpotify = onConnectSpotify,
                        onOpenClientIdDialog = onOpenClientIdDialog
                    )
                }

                state.tracks.isEmpty() -> {
                    EmptyPlaylistState(
                        isRefreshing = state.isRefreshing,
                        statusMessage = state.statusMessage,
                        onRefresh = onRefresh
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("track_list"),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(
                            items = state.tracks,
                            key = { "${it.playlistId}_${it.position}_${it.trackUri}" }
                        ) { track ->
                            val isActive = state.currentTrackUri != null &&
                                state.currentTrackUri == track.trackUri
                            TrackRowItem(
                                track = track,
                                isPlayingThisTrack = isActive,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onPlayTrack(track)
                                }
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = Color(0xFF151B22)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackControlBar(
    trackTitle: String?,
    trackArtist: String?,
    isPlaying: Boolean,
    isRemoteConnected: Boolean,
    isAuthenticated: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onHeaderClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MotoDashColors.DeckSurface)
            .padding(start = 14.dp, end = 6.dp)
            .testTag("playback_control_bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Now playing title + artist
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = !isAuthenticated, onClick = onHeaderClick)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isPlaying -> MotoDashColors.SpotifyGreen
                            isRemoteConnected -> MotoDashColors.TextSecondary
                            else -> MotoDashColors.TextDimmed
                        }
                    )
            )
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = trackTitle?.takeIf { it.isNotBlank() }
                        ?: if (isAuthenticated) "Ready to Ride" else "Spotify",
                    color = MotoDashColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("now_playing_title")
                )
                Text(
                    text = trackArtist?.takeIf { it.isNotBlank() }
                        ?: if (isAuthenticated) "Select a track below" else "Tap Connect below",
                    color = MotoDashColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("now_playing_artist")
                )
            }
        }

        // Three glove-friendly buttons (>= 56dp x 56dp each)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPrevious)
                    .semantics { contentDescription = "Previous Track" }
                    .testTag("btn_previous"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = null,
                    tint = MotoDashColors.TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onTogglePlayPause)
                    .semantics {
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    }
                    .testTag("btn_play_pause"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MotoDashColors.SpotifyGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MotoDashColors.PureBlack,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNext)
                    .semantics { contentDescription = "Next Track" }
                    .testTag("btn_next"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = MotoDashColors.TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistTagBar(
    playlists: List<SpotifyPlaylistDomain>,
    selectedPlaylistId: String?,
    isRefreshing: Boolean,
    showSettingsButton: Boolean,
    onSelectPlaylist: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenClientIdDialog: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MotoDashColors.PureBlack)
            .padding(vertical = 6.dp)
            .testTag("playlist_tag_bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (playlists.isEmpty()) {
                item(key = "placeholder_liked") {
                    PlaylistPill(
                        name = "Liked Songs",
                        isLikedSongs = true,
                        isSelected = true,
                        onClick = onRefresh
                    )
                }
            } else {
                items(
                    items = playlists,
                    key = { it.id }
                ) { playlist ->
                    PlaylistPill(
                        name = playlist.name,
                        isLikedSongs = playlist.isLikedSongs,
                        isSelected = playlist.id == selectedPlaylistId,
                        onClick = { onSelectPlaylist(playlist.id) }
                    )
                }
            }
        }

        if (showSettingsButton) {
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MotoDashColors.PillSurface)
                    .border(1.dp, MotoDashColors.PillBorder, CircleShape)
                    .clickable(onClick = onOpenClientIdDialog)
                    .semantics { contentDescription = "Configure Spotify Client ID" }
                    .testTag("btn_spotify_settings"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = MotoDashColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Rightmost Refresh button (glove-friendly 44dp height)
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(width = 48.dp, height = 44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MotoDashColors.PillSurface)
                .border(1.dp, MotoDashColors.PillBorder, RoundedCornerShape(22.dp))
                .clickable(enabled = !isRefreshing, onClick = onRefresh)
                .semantics { contentDescription = "Refresh Playlists" }
                .testTag("btn_refresh_playlists"),
            contentAlignment = Alignment.Center
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    color = MotoDashColors.SpotifyGreen,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = MotoDashColors.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistPill(
    name: String,
    isLikedSongs: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) MotoDashColors.SpotifyGreen else MotoDashColors.PillSurface
    val textColor = if (isSelected) MotoDashColors.PureBlack else MotoDashColors.TextPrimary
    val borderColor = if (isSelected) MotoDashColors.SpotifyGreen else MotoDashColors.PillBorder

    Row(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .testTag("playlist_pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isLikedSongs) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = if (isSelected) MotoDashColors.PureBlack else MotoDashColors.SpotifyGreen,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = name,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TrackRowItem(
    track: SpotifyTrackDomain,
    isPlayingThisTrack: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(
                if (isPlayingThisTrack) MotoDashColors.SpotifyGreenSubtle else MotoDashColors.PureBlack
            )
            .clickable(onClick = onClick)
            .testTag("track_row"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Spotify Green (#1DB954) active highlight bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                .background(
                    if (isPlayingThisTrack) MotoDashColors.SpotifyGreen else Color.Transparent
                )
                .testTag(if (isPlayingThisTrack) "active_track_indicator" else "inactive_track_indicator")
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.title,
                color = if (isPlayingThisTrack) MotoDashColors.SpotifyGreen else MotoDashColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                color = MotoDashColors.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SpotifyConnectEmptyState(
    hasClientId: Boolean,
    statusMessage: String?,
    onConnectSpotify: () -> Unit,
    onOpenClientIdDialog: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MotoDashColors.SpotifyGreen,
            modifier = Modifier.size(30.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = statusMessage ?: if (hasClientId) {
                "Sign in to load your Spotify playlists"
            } else {
                "Enter your Spotify Client ID to connect"
            },
            color = MotoDashColors.TextSecondary,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            color = MotoDashColors.SpotifyGreen,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(26.dp))
                .clickable(onClick = if (hasClientId) onConnectSpotify else onOpenClientIdDialog)
                .testTag("btn_connect_spotify")
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (hasClientId) "Connect Spotify" else "Set Client ID",
                    color = MotoDashColors.PureBlack,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun EmptyPlaylistState(
    isRefreshing: Boolean,
    statusMessage: String?,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                color = MotoDashColors.SpotifyGreen,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Syncing playlist tracks...",
                color = MotoDashColors.TextSecondary,
                fontSize = 14.sp
            )
        } else {
            Text(
                text = statusMessage ?: "No tracks cached for this playlist",
                color = MotoDashColors.TextSecondary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = MotoDashColors.PillSurface,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MotoDashColors.PillBorder),
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(onClick = onRefresh)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Refresh Now",
                        color = MotoDashColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
