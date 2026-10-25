@file:OptIn(ExperimentalTextApi::class)

package uk.co.aaronburt.rtsp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import uk.co.aaronburt.rtsp.R

private val weights = listOf(
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
)

val InterTight = FontFamily(
    weights.map { weight ->
        Font(
            resId = R.font.inter_tight,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

val JetBrainsMono = FontFamily(
    weights.map { weight ->
        Font(
            resId = R.font.jetbrains_mono,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

private val base = Typography()

val RtspTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = InterTight),
    displayMedium = base.displayMedium.copy(fontFamily = InterTight),
    displaySmall = base.displaySmall.copy(fontFamily = InterTight),
    headlineLarge = base.headlineLarge.copy(fontFamily = InterTight),
    headlineMedium = TextStyle(
        fontFamily = InterTight,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.02).em,
    ),
    headlineSmall = base.headlineSmall.copy(fontFamily = InterTight),
    titleLarge = TextStyle(
        fontFamily = InterTight,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.01).em,
    ),
    titleMedium = TextStyle(
        fontFamily = InterTight,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = InterTight,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterTight,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterTight,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InterTight,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = InterTight,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = InterTight,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterTight,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

val MonoSmall = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

val MonoTiny = MonoSmall.copy(fontSize = 10.sp, lineHeight = 14.sp)

val MonoBody = MonoSmall.copy(fontSize = 14.sp, lineHeight = 20.sp)
