package com.parcha.spoilnot.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.parcha.spoilnot.R

private fun manrope(weight: FontWeight) = Font(
    resId = R.font.manrope,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Manrope, bundled as one variable font (no runtime downloads). */
val Manrope =
    FontFamily(
        manrope(FontWeight.Normal),
        manrope(FontWeight.Medium),
        manrope(FontWeight.SemiBold),
        manrope(FontWeight.Bold),
    )

private val Default = Typography()

private fun TextStyle.manrope() = copy(fontFamily = Manrope)

internal val SpoilNotTypography =
    Typography(
        displayLarge = Default.displayLarge.manrope(),
        displayMedium = Default.displayMedium.manrope(),
        displaySmall = Default.displaySmall.manrope(),
        headlineLarge = Default.headlineLarge.manrope(),
        headlineMedium = Default.headlineMedium.manrope(),
        headlineSmall = Default.headlineSmall.manrope(),
        titleLarge = Default.titleLarge.manrope(),
        titleMedium = Default.titleMedium.manrope(),
        titleSmall = Default.titleSmall.manrope(),
        bodyLarge = Default.bodyLarge.manrope(),
        bodyMedium = Default.bodyMedium.manrope(),
        bodySmall = Default.bodySmall.manrope(),
        labelLarge = Default.labelLarge.manrope(),
        labelMedium = Default.labelMedium.manrope(),
        labelSmall = Default.labelSmall.manrope(),
    )
