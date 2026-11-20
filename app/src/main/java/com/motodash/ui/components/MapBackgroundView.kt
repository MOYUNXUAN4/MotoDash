package com.motodash.ui.components

import android.graphics.Color as AndroidColor
import android.os.Build
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.motodash.location.DashboardMath
import com.motodash.spotify.SpotifyConfig
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer

private const val TAG = "MotoDashMap"
private const val FALLBACK_DARK_STYLE_URL =
    "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
private val DEFAULT_FALLBACK_LAT_LNG = LatLng(37.7749, -122.4194)

/**
 * The map camera is padded from the top so the rider's position sits below the speed digits.
 * Padding 40% of the height puts the camera centre (the rider) at 40% + 60%/2 = 70% of the height.
 */
object MapCursorLayout {
    const val PADDING_TOP_FRACTION = 0.40f
    const val CURSOR_Y_FRACTION = 0.70f
}

@Composable
fun MapBackgroundView(
    latitude: Double?,
    longitude: Double?,
    bearingDegrees: Float,
    zoomLevel: Double,
    modifier: Modifier = Modifier,
    styleUrl: String = SpotifyConfig.MAP_STYLE_URL
) {
    val isPreviewOrRobolectric = LocalInspectionMode.current ||
        Build.FINGERPRINT.isNullOrBlank() ||
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

    if (isPreviewOrRobolectric) {
        FallbackDarkMapCanvas(modifier = modifier)
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Use TextureView mode (textureMode = true) so MapLibre composites cleanly inside
    // Jetpack Compose AndroidView beneath translucent overlays on Android 14/15.
    val mapView = remember {
        val options = MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true)
        MapView(context, options).apply {
            onCreate(null)
        }
    }

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var lastRenderedBearing by remember { mutableFloatStateOf(0f) }
    var hasMovedToFix by remember { mutableStateOf(false) }
    var usedFallbackStyle by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = {
            mapView.apply {
                addOnDidFailLoadingMapListener { errorMessage ->
                    Log.w(TAG, "Map load failed ($styleUrl): $errorMessage")
                    if (!usedFallbackStyle) {
                        usedFallbackStyle = true
                        mapLibreMap?.setStyle(FALLBACK_DARK_STYLE_URL) { fallbackStyle ->
                            enhanceDarkMapRoadVisibility(fallbackStyle)
                        }
                    }
                }
                getMapAsync { map ->
                    mapLibreMap = map
                    map.uiSettings.apply {
                        isScrollGesturesEnabled = false
                        isRotateGesturesEnabled = false
                        isTiltGesturesEnabled = false
                        isDoubleTapGesturesEnabled = false
                        isQuickZoomGesturesEnabled = false
                        isZoomGesturesEnabled = true
                        isCompassEnabled = false
                        isLogoEnabled = false
                        isAttributionEnabled = false
                    }
                    val initialTarget = if (latitude != null && longitude != null) {
                        LatLng(latitude, longitude)
                    } else {
                        DEFAULT_FALLBACK_LAT_LNG
                    }
                    map.cameraPosition = CameraPosition.Builder()
                        .target(initialTarget)
                        .zoom(zoomLevel)
                        .bearing(DashboardMath.normalizeBearing(bearingDegrees).toDouble())
                        .tilt(20.0)
                        .build()

                    map.setStyle(styleUrl) { style ->
                        enhanceDarkMapRoadVisibility(style)
                        Log.i(TAG, "Loaded and enhanced MapLibre style: $styleUrl")
                    }
                }
            }
        },
        modifier = modifier
    )

    LaunchedEffect(mapLibreMap, latitude, longitude, bearingDegrees, zoomLevel) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val viewHeight = mapView.height
        if (viewHeight > 0) {
            map.setPadding(0, (viewHeight * MapCursorLayout.PADDING_TOP_FRACTION).toInt(), 0, 0)
        }
        val target = if (latitude != null && longitude != null) {
            LatLng(latitude, longitude)
        } else {
            return@LaunchedEffect
        }

        // Smooth heading-up bearing via shortest angular delta
        val normalizedTargetBearing = DashboardMath.normalizeBearing(bearingDegrees)
        val delta = DashboardMath.normalizeAngleDelta(lastRenderedBearing, normalizedTargetBearing)
        val smoothBearing = DashboardMath.normalizeBearing(lastRenderedBearing + delta)
        lastRenderedBearing = smoothBearing

        val cameraPosition = CameraPosition.Builder()
            .target(target)
            .zoom(zoomLevel)
            .bearing(smoothBearing.toDouble())
            .tilt(20.0)
            .build()

        if (!hasMovedToFix) {
            hasMovedToFix = true
            map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        } else {
            map.easeCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 750)
        }
    }
}

/**
 * Boosts OpenFreeMap dark theme road, highway, building, and label contrast so streets
 * remain unmistakably legible behind the translucent motorcycle speedometer HUD.
 */
private fun enhanceDarkMapRoadVisibility(style: Style) {
    try {
        for (layer in style.layers) {
            val id = layer.id.lowercase()
            when (layer) {
                is BackgroundLayer -> {
                    layer.setProperties(
                        PropertyFactory.backgroundColor(AndroidColor.parseColor("#0E141D"))
                    )
                }

                is FillLayer -> {
                    when {
                        id.contains("water") -> layer.setProperties(
                            PropertyFactory.fillColor(AndroidColor.parseColor("#102944"))
                        )
                        id.contains("building") -> layer.setProperties(
                            PropertyFactory.fillColor(AndroidColor.parseColor("#1D2838")),
                            PropertyFactory.fillOutlineColor(AndroidColor.parseColor("#2B3A4E")),
                            PropertyFactory.fillOpacity(0.85f)
                        )
                        id.contains("park") || id.contains("grass") || id.contains("wood") -> layer.setProperties(
                            PropertyFactory.fillColor(AndroidColor.parseColor("#122620"))
                        )
                    }
                }

                is LineLayer -> {
                    when {
                        id.contains("motorway_inner") -> layer.setProperties(
                            PropertyFactory.lineColor(AndroidColor.parseColor("#54B8FF")),
                            PropertyFactory.lineOpacity(1.0f)
                        )
                        id.contains("motorway_casing") -> layer.setProperties(
                            PropertyFactory.lineColor(AndroidColor.parseColor("#1D4E7A")),
                            PropertyFactory.lineOpacity(0.95f)
                        )
                        id.contains("motorway") -> layer.setProperties(
                            PropertyFactory.lineColor(AndroidColor.parseColor("#47A3E6")),
                            PropertyFactory.lineOpacity(0.95f)
                        )
                        id.contains("major_inner") || id.contains("trunk") || id.contains("primary") -> layer.setProperties(
                            PropertyFactory.lineColor(AndroidColor.parseColor("#8FAAC6")),
                            PropertyFactory.lineOpacity(0.95f)
                        )
                        id.contains("major_casing") -> layer.setProperties(
                            PropertyFactory.lineColor(AndroidColor.parseColor("#2B3C50")),
                            PropertyFactory.lineOpacity(0.9f)
                        )
                        id.contains("major") || id.contains("secondary") || id.contains("tertiary") -> layer.setProperties(
                            PropertyFactory.lineColor(AndroidColor.parseColor("#738FA9")),
                            PropertyFactory.lineOpacity(0.9f)
                        )
                        id.contains("minor") || id.contains("street") || id.contains("road") -> layer.setProperties(
                            PropertyFactory.lineColor(AndroidColor.parseColor("#5C748E")),
                            PropertyFactory.lineOpacity(0.88f)
                        )
                        id.contains("path") -> layer.setProperties(
                            PropertyFactory.lineColor(AndroidColor.parseColor("#425569")),
                            PropertyFactory.lineOpacity(0.75f)
                        )
                        id.contains("waterway") -> layer.setProperties(
                            PropertyFactory.lineColor(AndroidColor.parseColor("#1B436E"))
                        )
                    }
                }

                is SymbolLayer -> {
                    if (id.contains("highway_name") || id.contains("road") || id.contains("place")) {
                        layer.setProperties(
                            PropertyFactory.textColor(AndroidColor.parseColor("#DCE6F2")),
                            PropertyFactory.textHaloColor(AndroidColor.parseColor("#0B1018")),
                            PropertyFactory.textHaloWidth(1.5f)
                        )
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to enhance map style layers: ${e.message}")
    }
}

@Composable
private fun FallbackDarkMapCanvas(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E141D))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridStep = 64f
            val lineColor = Color(0xFF233245)
            var x = 0f
            while (x <= size.width) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5f)
                x += gridStep
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                y += gridStep
            }
        }
    }
}
