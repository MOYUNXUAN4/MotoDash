package com.motodash.unit

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.motodash.location.DashboardMath
import com.motodash.location.GpsSignalStatus
import com.motodash.spotify.SpotifyConfig
import com.motodash.spotify.SpotifyPlaylistDomain
import com.motodash.spotify.SpotifyTrackDomain
import com.motodash.ui.DashboardUiState
import com.motodash.ui.MotoDashScreen
import com.motodash.ui.MotoDashUiState
import com.motodash.ui.MusicUiState
import com.motodash.ui.theme.MotoDashTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class MotoDashUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders 60_40 dashboard and glove-friendly Spotify deck with all required dimensions and actions`() {
        var tripToggled = false
        var tripReset = false
        var wazeLaunched = false
        var playPauseClicked = false
        var playedTrackUri: String? = null
        var selectedPlaylistId: String? = null

        val sampleTracks = listOf(
            SpotifyTrackDomain(
                playlistId = "pl_1",
                trackId = "t1",
                trackUri = "spotify:track:t1",
                title = "Highway Star",
                artist = "Deep Purple",
                durationMs = 365000L,
                position = 0
            ),
            SpotifyTrackDomain(
                playlistId = "pl_1",
                trackId = "t2",
                trackUri = "spotify:track:t2",
                title = "Nightcall",
                artist = "Kavinsky",
                durationMs = 258000L,
                position = 1
            )
        )

        val state = MotoDashUiState(
            dashboard = DashboardUiState(
                speedText = "118",
                displaySpeedKmh = 118f,
                speedColorHex = DashboardMath.getSpeedColorHex(118f),
                isTimedOut = false,
                gpsStatus = GpsSignalStatus.GREEN,
                accuracyMeters = 6f,
                latitude = 37.7749,
                longitude = -122.4194,
                bearingDegrees = 90f,
                dynamicZoom = DashboardMath.calculateDynamicZoom(118f),
                sunlightMode = false,
                maskOpacity = DashboardMath.MASK_OPACITY_NORMAL,
                tripText = "Trip 14.2 km",
                formattedTime = "18:45",
                batteryPercent = 84,
                formattedBattery = "84%",
                hasLocationPermission = true
            ),
            music = MusicUiState(
                isAuthenticated = true,
                hasClientIdConfigured = true,
                isRemoteConnected = true,
                isPlaying = true,
                currentTrackUri = "spotify:track:t1",
                currentTrackTitle = "Highway Star",
                currentTrackArtist = "Deep Purple",
                playlists = listOf(
                    SpotifyPlaylistDomain(
                        id = SpotifyConfig.LIKED_SONGS_PLAYLIST_ID,
                        name = "Liked Songs",
                        trackCount = 10,
                        isLikedSongs = true
                    ),
                    SpotifyPlaylistDomain(
                        id = "pl_1",
                        name = "Night Ride",
                        trackCount = 2,
                        isLikedSongs = false
                    )
                ),
                selectedPlaylistId = "pl_1",
                tracks = sampleTracks
            )
        )

        composeRule.setContent {
            MotoDashTheme {
                MotoDashScreen(
                    uiState = state,
                    onTripClick = { tripToggled = true },
                    onTripLongPress = { tripReset = true },
                    onDismissTripSummary = {},
                    onLaunchWaze = { wazeLaunched = true },
                    onRequestLocationPermission = {},
                    onPrevious = {},
                    onTogglePlayPause = { playPauseClicked = true },
                    onNext = {},
                    onSelectPlaylist = { selectedPlaylistId = it },
                    onRefreshLibrary = {},
                    onPlayTrack = { playedTrackUri = it.trackUri },
                    onConnectSpotify = {},
                    onSaveClientId = {}
                )
            }
        }

        // Verify HUD elements
        composeRule.onNodeWithTag("speedometer_value").assertIsDisplayed()
        composeRule.onNodeWithText("118").assertIsDisplayed()
        composeRule.onNodeWithText("km/h").assertIsDisplayed()
        composeRule.onNodeWithText("18:45").assertIsDisplayed()
        composeRule.onNodeWithText("84%").assertIsDisplayed()
        composeRule.onNodeWithText("Trip 14.2 km").assertIsDisplayed()
        composeRule.onNodeWithTag("gps_status_indicator").assertIsDisplayed()

        // Verify strict 60% / 40% vertical split ratio
        val upperHeight = composeRule.onNodeWithTag("upper_dashboard_section")
            .getUnclippedBoundsInRoot().height.value
        val lowerHeight = composeRule.onNodeWithTag("lower_music_section")
            .getUnclippedBoundsInRoot().height.value
        val ratio = upperHeight / (upperHeight + lowerHeight)
        assertEquals(0.6f, ratio, 0.01f)

        // Verify strict glove-friendly dimensions
        composeRule.onNodeWithTag("playback_control_bar").assertHeightIsEqualTo(64.dp)
        composeRule.onNodeWithTag("btn_previous")
            .assertHeightIsEqualTo(56.dp)
            .assertWidthIsAtLeast(56.dp)
        composeRule.onNodeWithTag("btn_play_pause")
            .assertHeightIsEqualTo(56.dp)
            .assertWidthIsAtLeast(56.dp)
        composeRule.onNodeWithTag("btn_next")
            .assertHeightIsEqualTo(56.dp)
            .assertWidthIsAtLeast(56.dp)

        composeRule.onAllNodesWithTag("playlist_pill").onFirst().assertHeightIsEqualTo(44.dp)
        composeRule.onAllNodesWithTag("track_row").onFirst().assertHeightIsEqualTo(62.dp)
        composeRule.onNodeWithTag("active_track_indicator", useUnmergedTree = true).assertIsDisplayed()

        // Verify interactive callbacks
        // Sunlight toggle button was removed: sunlight high-contrast is now the permanent default look
        composeRule.onAllNodesWithTag("sunlight_mode_toggle").assertCountEquals(0)

        composeRule.onNodeWithTag("waze_launch_button").performClick()
        assertTrue(wazeLaunched)

        composeRule.onNodeWithTag("trip_odometer").performClick()
        assertTrue(tripToggled)

        composeRule.onNodeWithTag("trip_odometer").performTouchInput { longClick() }
        assertTrue(tripReset)

        composeRule.onNodeWithTag("btn_play_pause").performClick()
        assertTrue(playPauseClicked)

        composeRule.onNodeWithText("Liked Songs").performClick()
        assertEquals(SpotifyConfig.LIKED_SONGS_PLAYLIST_ID, selectedPlaylistId)

        composeRule.onNodeWithText("Nightcall").performClick()
        assertEquals("spotify:track:t2", playedTrackUri)
    }
}
