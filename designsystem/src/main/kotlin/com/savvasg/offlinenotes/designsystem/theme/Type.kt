package com.savvasg.offlinenotes.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

internal val OfflineNotesTypography = Typography(
    headlineLarge = Default.headlineLarge.tightened(FontWeight.SemiBold, (-0.5).sp),
    headlineMedium = Default.headlineMedium.tightened(FontWeight.SemiBold, (-0.4).sp),
    headlineSmall = Default.headlineSmall.tightened(FontWeight.SemiBold, (-0.3).sp),
    titleLarge = Default.titleLarge.tightened(FontWeight.SemiBold, (-0.2).sp),
    titleMedium = Default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Default.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = Default.bodyMedium.copy(lineHeight = 22.sp),
    labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    labelMedium = Default.labelMedium.copy(letterSpacing = 0.4.sp),
    labelSmall = Default.labelSmall.copy(letterSpacing = 0.5.sp),
)

private fun TextStyle.tightened(weight: FontWeight, spacing: androidx.compose.ui.unit.TextUnit) =
    copy(fontFamily = FontFamily.Default, fontWeight = weight, letterSpacing = spacing)
