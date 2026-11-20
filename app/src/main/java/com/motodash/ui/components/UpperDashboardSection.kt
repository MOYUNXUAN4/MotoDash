package com.motodash.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motodash.location.GpsSignalStatus
import com.motodash.ui.DashboardUiState
import com.motodash.ui.TripPhase
import com.motodash.ui.theme.MotoDashColors
import com.motodash.ui.theme.MotoDashFonts
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UpperDashboardSection(
    state: DashboardUiState,
    onTripClick: () -> Unit,
    onTripLongPress: () -> Unit,
    onLaunchWaze: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val animatedMaskAlpha by animateFloatAsState(
        targetValue = state.maskOpacity,
        animationSpec = tween(durationMillis = 250),
        label = "maskOpacity"
    )

    val baseSpeedColor = Color(state.speedColorHex)
    val targetSpeedColor = if (state.isTimedOut) MotoDashColors.TextDimmed else baseSpeedColor
    val animatedSpeedColor by animateColorAsState(
        targetValue = targetSpeedColor,
        animationSpec = tween(durationMillis = 180),
        label = "speedColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MotoDashColors.PureBlack)
            .testTag("upper_dashboard_section")
    ) {
        // Layer 1: MapLibre dark base map (heading-up, speed-adaptive zoom)
        MapBackgroundView(
            latitude = state.latitude,
            longitude = state.longitude,
            bearingDegrees = state.bearingDegrees,
            zoomLevel = state.dynamicZoom,
            modifier = Modifier
                .fillMaxSize()
                .testTag("map_background_layer")
        )

        // Layer 2: location belongs to the map, beneath the same contrast mask.
        PositionCursor(
            hasFix = state.latitude != null && !state.isTimedOut,
            modifier = Modifier
                .fillMaxSize()
                .testTag("position_cursor")
        )

        // Layer 3: shared mask softens both map and cursor while keeping the HUD crisp.
        val edgeAlpha = if (state.sunlightMode) animatedMaskAlpha else (animatedMaskAlpha * 0.68f)
        val midAlpha = if (state.sunlightMode) (animatedMaskAlpha * 0.72f) else (animatedMaskAlpha * 0.34f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = edgeAlpha),
                        0.22f to Color.Black.copy(alpha = midAlpha),
                        0.78f to Color.Black.copy(alpha = midAlpha),
                        1.0f to Color.Black.copy(alpha = edgeAlpha)
                    )
                )
                .testTag("mask_overlay_layer")
        )

        // Layer 4: cockpit HUD
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val speedFontSizeSp = (maxHeight.value * 0.54f).coerceIn(104f, 220f)

            // Top row: big clock + battery (left) | GPS status (right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelemetryPill(
                    formattedTime = state.formattedTime,
                    formattedBattery = state.formattedBattery,
                    batteryPercent = state.batteryPercent,
                    isCharging = state.isCharging
                )
                GpsStatusPill(
                    status = state.gpsStatus,
                    accuracyMeters = state.accuracyMeters,
                    isTimedOut = state.isTimedOut
                )
            }

            // Upper-center: giant gradient racing digits + km/h unit
            Column(
                modifier = Modifier.align(BiasAlignment(0f, -0.22f)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val (digitTop, digitBottom) = speedGradientColors(animatedSpeedColor, state.isTimedOut)
                    Text(
                        text = state.speedText,
                        style = TextStyle(
                            brush = Brush.verticalGradient(listOf(digitTop, digitBottom)),
                            fontSize = speedFontSizeSp.sp,
                            fontFamily = MotoDashFonts.SpeedDigits,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            lineHeight = (speedFontSizeSp * 0.92f).sp,
                            letterSpacing = (-2).sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.9f),
                                offset = Offset(0f, 8f),
                                blurRadius = 20f
                            )
                        ),
                        maxLines = 1,
                        modifier = Modifier.testTag("speedometer_value")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "km/h",
                        style = TextStyle(
                            color = if (state.isTimedOut) {
                                MotoDashColors.TextDimmed
                            } else {
                                animatedSpeedColor.copy(alpha = 0.9f)
                            },
                            fontSize = 26.sp,
                            fontFamily = MotoDashFonts.Gauge,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            letterSpacing = 1.sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.9f),
                                offset = Offset(0f, 3f),
                                blurRadius = 10f
                            )
                        ),
                        modifier = Modifier
                            .padding(bottom = (speedFontSizeSp * 0.12f).dp)
                            .testTag("speedometer_unit")
                    )
                }

                val showSpeedBar = !state.isTimedOut && state.displaySpeedKmh >= 110f
                if (showSpeedBar) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .width(120.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(animatedSpeedColor)
                    )
                }

                if (!state.hasLocationPermission) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xCC161B22),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, MotoDashColors.WazeCyan.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(onClick = onRequestLocationPermission)
                            .testTag("grant_gps_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GpsFixed,
                                contentDescription = null,
                                tint = MotoDashColors.WazeCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Enable GPS",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Bottom row: tap-to-record trip (left) | Waze (right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                TripButton(
                    tripText = state.tripText,
                    phase = state.tripPhase,
                    elapsedText = state.tripElapsedText,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTripClick()
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTripLongPress()
                    }
                )

                WazeFloatingButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLaunchWaze()
                    }
                )
            }
        }
    }
}

private fun speedGradientColors(base: Color, dimmed: Boolean): Pair<Color, Color> {
    if (dimmed) return base to base.copy(alpha = 0.7f)
    val isWhite = base.red > 0.97f && base.green > 0.97f && base.blue > 0.97f
    return if (isWhite) {
        Color.White to Color(0xFF9CCBFF)
    } else {
        lerp(base, Color.White, 0.45f) to base
    }
}

@Composable
private fun PositionCursor(hasFix: Boolean, modifier: Modifier = Modifier) {
    val accent by animateColorAsState(
        targetValue = if (hasFix) Color(0xFF7CA6B8) else Color(0xFF66717C),
        animationSpec = tween(durationMillis = 300),
        label = "cursorFixColor"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height * MapCursorLayout.CURSOR_Y_FRACTION)
        val d = density

        // A quiet, compact marker stays readable without pulsing over the speed display.
        val arrow = Path().apply {
            moveTo(center.x, center.y - 17f * d)
            lineTo(center.x + 11f * d, center.y + 12f * d)
            lineTo(center.x, center.y + 6f * d)
            lineTo(center.x - 11f * d, center.y + 12f * d)
            close()
        }
        drawPath(arrow, color = Color(0xFF0E141D).copy(alpha = 0.8f), style = Stroke(width = 3f * d, join = StrokeJoin.Round))
        drawPath(arrow, color = accent.copy(alpha = if (hasFix) 0.88f else 0.6f), style = Fill)
        drawPath(arrow, color = accent.copy(alpha = 0.65f), style = Stroke(width = 1f * d, join = StrokeJoin.Round))
    }
}

@Composable
private fun TelemetryPill(
    formattedTime: String,
    formattedBattery: String,
    batteryPercent: Int?,
    isCharging: Boolean
) {
    val batteryColor = when {
        isCharging -> MotoDashColors.GpsGreen
        batteryPercent != null && batteryPercent <= 15 -> MotoDashColors.SpeedRed
        batteryPercent != null && batteryPercent <= 25 -> MotoDashColors.SpeedAmber
        else -> MotoDashColors.TextPrimary
    }
    val batteryIcon = when {
        isCharging -> Icons.Rounded.BatteryChargingFull
        batteryPercent != null && batteryPercent <= 15 -> Icons.Rounded.BatteryAlert
        else -> Icons.Rounded.BatteryFull
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xB3000000))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("telemetry_pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = formattedTime,
            color = MotoDashColors.TextPrimary,
            fontSize = 48.sp,
            fontFamily = MotoDashFonts.Gauge,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                fontFeatureSettings = "tnum",
                shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 4f), 10f)
            ),
            modifier = Modifier.testTag("clock_text")
        )

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(Color(0x40FFFFFF))
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = batteryIcon,
                contentDescription = "Battery",
                tint = batteryColor,
                modifier = Modifier.size(21.dp)
            )
            Text(
                text = formattedBattery,
                color = batteryColor,
                fontSize = 22.sp,
                fontFamily = MotoDashFonts.Gauge,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.testTag("battery_text")
            )
        }
    }
}

@Composable
private fun GpsStatusPill(
    status: GpsSignalStatus,
    accuracyMeters: Float,
    isTimedOut: Boolean
) {
    val dotColor = when (status) {
        GpsSignalStatus.GREEN -> MotoDashColors.GpsGreen
        GpsSignalStatus.YELLOW -> MotoDashColors.GpsYellow
        GpsSignalStatus.RED -> MotoDashColors.GpsRed
    }
    val label = when {
        isTimedOut || status == GpsSignalStatus.RED -> "NO GPS"
        accuracyMeters.isFinite() -> "${accuracyMeters.roundToInt()}m"
        else -> "GPS"
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xB3000000))
            .border(1.dp, dotColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("gps_status_indicator"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        Text(
            text = label,
            color = MotoDashColors.TextPrimary,
            fontSize = 17.sp,
            fontFamily = MotoDashFonts.Gauge,
            fontWeight = FontWeight.Bold,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripButton(
    tripText: String,
    phase: TripPhase,
    elapsedText: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val recording = phase == TripPhase.RECORDING
    val transition = rememberInfiniteTransition(label = "recBlink")
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )
    val accent = if (recording) MotoDashColors.SpeedRed else MotoDashColors.SpotifyGreen

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xCC000000))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics {
                contentDescription = if (recording) "Stop trip recording" else "Start trip recording"
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("trip_odometer"),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (recording) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MotoDashColors.SpeedRed.copy(alpha = blink))
                )
            }
            Text(
                text = tripText,
                color = MotoDashColors.TextPrimary,
                fontSize = 22.sp,
                fontFamily = MotoDashFonts.Gauge,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
            if (recording) {
                Text(
                    text = elapsedText,
                    color = MotoDashColors.TextSecondary,
                    fontSize = 20.sp,
                    fontFamily = MotoDashFonts.Gauge,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }
        Text(
            text = if (recording) "TAP TO FINISH" else "TAP TO START",
            color = accent,
            fontSize = 12.sp,
            fontFamily = MotoDashFonts.Gauge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
    }
}

@Composable
private fun WazeFloatingButton(onClick: () -> Unit) {
    Surface(
        color = Color(0xE6172630),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MotoDashColors.WazeCyan.copy(alpha = 0.4f)),
        shadowElevation = 0.dp,
        modifier = Modifier
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Launch Waze Navigation" }
            .testTag("waze_launch_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Navigation,
                contentDescription = null,
                tint = Color(0xFF9CBECF),
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Waze",
                color = MotoDashColors.TextPrimary,
                fontSize = 22.sp,
                fontFamily = MotoDashFonts.Gauge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.4.sp
            )
        }
    }
}
