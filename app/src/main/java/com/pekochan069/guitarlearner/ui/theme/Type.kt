package com.pekochan069.guitarlearner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Baseline = Typography()

internal val AppTypography = Typography(
    displayLarge = Baseline.displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    displayMedium = Baseline.displayMedium.copy(fontWeight = FontWeight.SemiBold),
    displaySmall = Baseline.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = Baseline.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Baseline.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Baseline.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
)
