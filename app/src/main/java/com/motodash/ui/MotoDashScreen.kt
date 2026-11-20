package com.motodash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motodash.spotify.SpotifyTrackDomain
import com.motodash.ui.components.LowerMusicSection
import com.motodash.ui.components.TripSummaryDialog
import com.motodash.ui.components.UpperDashboardSection
import com.motodash.ui.theme.MotoDashColors

@Composable
fun MotoDashScreen(
    uiState: MotoDashUiState,
    onTripClick: () -> Unit,
    onTripLongPress: () -> Unit,
    onDismissTripSummary: () -> Unit,
    onLaunchWaze: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onRefreshLibrary: () -> Unit,
    onPlayTrack: (SpotifyTrackDomain) -> Unit,
    onConnectSpotify: () -> Unit,
    onSaveClientId: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showClientIdDialog by remember { mutableStateOf(false) }

    // Strict 60% / 40% vertical split on pure black background
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MotoDashColors.PureBlack)
            .testTag("motodash_root")
    ) {
        UpperDashboardSection(
            state = uiState.dashboard,
            onTripClick = onTripClick,
            onTripLongPress = onTripLongPress,
            onLaunchWaze = onLaunchWaze,
            onRequestLocationPermission = onRequestLocationPermission,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f)
        )

        HorizontalDivider(
            thickness = 1.dp,
            color = MotoDashColors.HairlineDivider
        )

        LowerMusicSection(
            state = uiState.music,
            onPrevious = onPrevious,
            onTogglePlayPause = onTogglePlayPause,
            onNext = onNext,
            onSelectPlaylist = onSelectPlaylist,
            onRefresh = onRefreshLibrary,
            onPlayTrack = onPlayTrack,
            onConnectSpotify = onConnectSpotify,
            onOpenClientIdDialog = { showClientIdDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
        )
    }

    uiState.tripSummary?.let { summary ->
        TripSummaryDialog(summary = summary, onDismiss = onDismissTripSummary)
    }

    if (showClientIdDialog) {
        SpotifyClientIdDialog(
            initialClientId = uiState.music.configuredClientId,
            onDismiss = { showClientIdDialog = false },
            onSave = { newId ->
                onSaveClientId(newId)
                showClientIdDialog = false
            }
        )
    }
}

@Composable
private fun SpotifyClientIdDialog(
    initialClientId: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draftId by remember(initialClientId) { mutableStateOf(initialClientId) }
    val trimmed = draftId.trim()
    val looksLikePackageName = trimmed.contains(".") || trimmed.equals("com.motodash", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MotoDashColors.CardSurface,
        title = {
            Text(
                text = "Spotify Client ID",
                color = MotoDashColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Paste your 32-character Spotify App Client ID (not com.motodash).",
                    color = MotoDashColors.TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draftId,
                    onValueChange = { draftId = it },
                    singleLine = true,
                    isError = looksLikePackageName,
                    placeholder = { Text("e.g. 9f3c2a1b4d5e...") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (looksLikePackageName) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "That looks like a package name. Copy the 32-char Client ID from Spotify Dashboard.",
                        color = MotoDashColors.SpeedAmber,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !looksLikePackageName,
                onClick = { onSave(trimmed) }
            ) {
                Text(
                    text = "Save",
                    color = if (looksLikePackageName) MotoDashColors.TextDimmed else MotoDashColors.SpotifyGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = MotoDashColors.TextSecondary
                )
            }
        }
    )
}
