package com.motodash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motodash.telemetry.WazeLauncher
import com.motodash.ui.MotoDashScreen
import com.motodash.ui.MotoDashViewModel
import com.motodash.ui.theme.MotoDashTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MotoDashViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Recording can still run if notifications are declined; the dashboard can finish it.
        viewModel.toggleTrip()
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureImmersiveKeepScreenOn()

        // Handle OAuth redirect if launched via motodash://callback
        intent?.data?.let { uri ->
            viewModel.handleAuthCallback(uri, this)
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            MotoDashTheme {
                MotoDashScreen(
                    uiState = uiState,
                    onTripClick = ::toggleTrip,
                    onTripLongPress = viewModel::resetTrip,
                    onDismissTripSummary = viewModel::dismissTripSummary,
                    onLaunchWaze = {
                        val launched = WazeLauncher.launchWaze(this)
                        if (!launched) {
                            Toast.makeText(
                                this,
                                "Waze is not installed on this device",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onRequestLocationPermission = ::requestLocationPermissions,
                    onPrevious = viewModel::skipPrevious,
                    onTogglePlayPause = { viewModel.togglePlayPause(this) },
                    onNext = viewModel::skipNext,
                    onSelectPlaylist = viewModel::selectPlaylist,
                    onRefreshLibrary = viewModel::refreshLibrary,
                    onPlayTrack = { track -> viewModel.playTrack(track, this) },
                    onConnectSpotify = { viewModel.startSpotifyLogin(this) },
                    onSaveClientId = viewModel::updateSpotifyClientId
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onStartForeground(this)
        if (!viewModel.uiState.value.dashboard.hasLocationPermission) {
            requestLocationPermissions()
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.onStopForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            viewModel.handleAuthCallback(uri, this)
        }
    }

    private fun requestLocationPermissions() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun toggleTrip() {
        if (viewModel.uiState.value.dashboard.tripPhase == com.motodash.ui.TripPhase.IDLE &&
            viewModel.uiState.value.dashboard.hasLocationPermission &&
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else viewModel.toggleTrip()
    }

    private fun configureImmersiveKeepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.navigationBars())
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
    }
}
