package com.motodash.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.motodash.telemetry.TripSummary
import com.motodash.ui.theme.MotoDashColors
import com.motodash.ui.theme.MotoDashFonts
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

fun formatTripDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

/** Fun rider archetype based on how the ride went. */
fun riderTitle(summary: TripSummary): Pair<String, String> = when {
    summary.maxSpeedKmh >= 130f -> "🔥" to "SPEED DEMON"
    summary.maxSpeedKmh >= 100f -> "🦅" to "HIGHWAY HAWK"
    summary.maxSpeedKmh >= 70f -> "🏁" to "ROAD RUNNER"
    summary.maxSpeedKmh >= 40f -> "🏙️" to "CITY CRUISER"
    summary.distanceKm < 0.05 -> "🅿️" to "PARKING LOT HERO"
    else -> "🌿" to "CHILL RIDER"
}

private fun speedLineColor(speedKmh: Float): Color = when {
    speedKmh >= 130f -> MotoDashColors.SpeedRed
    speedKmh >= 110f -> MotoDashColors.SpeedAmber
    else -> lerp(MotoDashColors.WazeCyan, Color.White, (speedKmh / 110f).coerceIn(0f, 1f) * 0.6f)
}

@Composable
fun TripSummaryDialog(
    summary: TripSummary,
    onDismiss: () -> Unit
) {
    val (emoji, title) = riderTitle(summary)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF0B0F14),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, Color(0xFF2B333E)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 24.dp)
                .testTag("trip_summary_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = emoji, fontSize = 40.sp)
                    Column {
                        Text(
                            text = "RIDE COMPLETE",
                            color = MotoDashColors.TextSecondary,
                            fontSize = 14.sp,
                            fontFamily = MotoDashFonts.Gauge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = title,
                            color = MotoDashColors.SpotifyGreen,
                            fontSize = 30.sp,
                            fontFamily = MotoDashFonts.SpeedDigits,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }

                // Hero stat: top speed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF121820))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "TOP SPEED",
                            color = MotoDashColors.TextSecondary,
                            fontSize = 13.sp,
                            fontFamily = MotoDashFonts.Gauge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = summary.maxSpeedKmh.roundToInt().toString(),
                                color = speedLineColor(summary.maxSpeedKmh),
                                fontSize = 76.sp,
                                fontFamily = MotoDashFonts.SpeedDigits,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                modifier = Modifier.testTag("summary_max_speed")
                            )
                            Text(
                                text = " km/h",
                                color = MotoDashColors.TextSecondary,
                                fontSize = 20.sp,
                                fontFamily = MotoDashFonts.Gauge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                    Text(
                        text = "at ${formatTripDuration(summary.maxSpeedAtMs)}",
                        color = MotoDashColors.TextSecondary,
                        fontSize = 16.sp,
                        fontFamily = MotoDashFonts.Gauge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = "DISTANCE",
                        value = String.format(Locale.US, "%.1f", summary.distanceKm),
                        unit = "km",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "DURATION",
                        value = formatTripDuration(summary.durationMs),
                        unit = "",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = "AVG SPEED",
                        value = summary.avgSpeedKmh.roundToInt().toString(),
                        unit = "km/h",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "MOVING AVG",
                        value = summary.avgMovingSpeedKmh.roundToInt().toString(),
                        unit = "km/h",
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "SPEED CURVE",
                    color = MotoDashColors.TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = MotoDashFonts.Gauge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                SpeedCurveChart(
                    summary = summary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F141B))
                        .testTag("speed_curve_chart")
                )

                // Fun facts
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    summary.bestZeroTo60Ms?.let { ms ->
                        FunFact("⚡", String.format(Locale.US, "Best 0→60 km/h sprint: %.1f s", ms / 1000.0))
                    }
                    FunFact("🚦", "Stops along the way: ${summary.stopCount}")
                    FunFact("⏱️", "Rolling for ${formatTripDuration(summary.movingTimeMs)} · parked ${formatTripDuration((summary.durationMs - summary.movingTimeMs).coerceAtLeast(0L))}")
                }

                Surface(
                    color = MotoDashColors.SpotifyGreen,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .clickable(onClick = onDismiss)
                        .testTag("trip_summary_done")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "DONE",
                            color = MotoDashColors.PureBlack,
                            fontSize = 22.sp,
                            fontFamily = MotoDashFonts.Gauge,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF121820))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = MotoDashColors.TextSecondary,
            fontSize = 12.sp,
            fontFamily = MotoDashFonts.Gauge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = MotoDashColors.TextPrimary,
                fontSize = 34.sp,
                fontFamily = MotoDashFonts.SpeedDigits,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = " $unit",
                    color = MotoDashColors.TextSecondary,
                    fontSize = 15.sp,
                    fontFamily = MotoDashFonts.Gauge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun FunFact(emoji: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Text(
            text = text,
            color = MotoDashColors.TextPrimary,
            fontSize = 16.sp,
            fontFamily = MotoDashFonts.Gauge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SpeedCurveChart(
    summary: TripSummary,
    modifier: Modifier = Modifier
) {
    val chart = summary.chart
    if (chart.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Ride a little longer to draw your speed curve",
                color = MotoDashColors.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(color = MotoDashColors.TextSecondary, fontSize = 11.sp)
    val peakStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

    Canvas(modifier = modifier) {
        val d = density
        val left = 36f * d
        val right = 12f * d
        val top = 22f * d
        val bottom = 22f * d
        val w = size.width - left - right
        val h = size.height - top - bottom
        val yMax = max(summary.maxSpeedKmh, 20f) * 1.12f
        val totalMs = max(chart.last().offsetMs, 1L).toFloat()

        fun px(offsetMs: Long) = left + w * (offsetMs / totalMs)
        fun py(speed: Float) = top + h * (1f - (speed / yMax).coerceIn(0f, 1f))

        // Grid + Y labels
        for (i in 0..3) {
            val value = yMax * i / 3f
            val y = py(value)
            drawLine(
                color = Color(0xFF232C38),
                start = Offset(left, y),
                end = Offset(left + w, y),
                strokeWidth = 1f * d
            )
            drawText(
                textMeasurer = measurer,
                text = value.roundToInt().toString(),
                topLeft = Offset(4f * d, y - 7f * d),
                style = axisStyle
            )
        }

        // X labels
        drawText(measurer, "0:00", Offset(left, size.height - 18f * d), axisStyle)
        val endLabel = formatTripDuration(chart.last().offsetMs)
        val endWidth = measurer.measure(endLabel, axisStyle).size.width
        drawText(measurer, endLabel, Offset(left + w - endWidth, size.height - 18f * d), axisStyle)

        // Area fill under the curve
        val fill = Path().apply {
            moveTo(px(chart.first().offsetMs), py(0f))
            chart.forEach { lineTo(px(it.offsetMs), py(it.speedKmh)) }
            lineTo(px(chart.last().offsetMs), py(0f))
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(MotoDashColors.WazeCyan.copy(alpha = 0.32f), Color.Transparent),
                startY = top,
                endY = top + h
            )
        )

        // Average speed dashed line
        if (summary.avgMovingSpeedKmh > 0f) {
            val ay = py(summary.avgMovingSpeedKmh)
            drawLine(
                color = MotoDashColors.SpotifyGreen.copy(alpha = 0.9f),
                start = Offset(left, ay),
                end = Offset(left + w, ay),
                strokeWidth = 1.6f * d,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f * d, 8f * d))
            )
            drawText(
                measurer,
                "AVG ${summary.avgMovingSpeedKmh.roundToInt()}",
                Offset(left + 6f * d, ay - 16f * d),
                TextStyle(color = MotoDashColors.SpotifyGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
        }

        // Speed-coloured curve
        for (i in 0 until chart.size - 1) {
            val a = chart[i]
            val b = chart[i + 1]
            drawLine(
                color = speedLineColor((a.speedKmh + b.speedKmh) / 2f),
                start = Offset(px(a.offsetMs), py(a.speedKmh)),
                end = Offset(px(b.offsetMs), py(b.speedKmh)),
                strokeWidth = 3f * d,
                cap = StrokeCap.Round
            )
        }

        // Peak marker
        val peak = chart.maxByOrNull { it.speedKmh } ?: return@Canvas
        val peakCenter = Offset(px(peak.offsetMs), py(peak.speedKmh))
        drawCircle(Color.Black.copy(alpha = 0.6f), radius = 8f * d, center = peakCenter)
        drawCircle(speedLineColor(peak.speedKmh), radius = 5.5f * d, center = peakCenter)
        drawCircle(Color.White, radius = 5.5f * d, center = peakCenter, style = Stroke(width = 1.6f * d))
        val peakLabel = "MAX ${summary.maxSpeedKmh.roundToInt()}"
        val peakWidth = measurer.measure(peakLabel, peakStyle).size.width
        val labelX = (peakCenter.x - peakWidth / 2f).coerceIn(left, left + w - peakWidth)
        drawText(measurer, peakLabel, Offset(labelX, (peakCenter.y - 22f * d).coerceAtLeast(2f * d)), peakStyle)
    }
}
