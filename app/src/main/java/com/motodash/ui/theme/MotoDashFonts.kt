package com.motodash.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.motodash.R

/** Racing-dashboard typography: condensed italic digits for speed, condensed semibold for clock/stats. */
object MotoDashFonts {
    val SpeedDigits = FontFamily(
        Font(R.font.barlow_condensed_bold_italic, FontWeight.Bold, FontStyle.Italic),
        Font(R.font.barlow_condensed_bold_italic, FontWeight.Black, FontStyle.Italic)
    )
    val Gauge = FontFamily(
        Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_condensed_semibold, FontWeight.Bold),
        Font(R.font.barlow_condensed_semibold, FontWeight.ExtraBold),
        Font(R.font.barlow_condensed_semibold, FontWeight.Normal)
    )
}
