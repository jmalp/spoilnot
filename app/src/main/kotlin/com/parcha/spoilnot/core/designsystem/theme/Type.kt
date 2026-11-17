package com.parcha.spoilnot.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
        manrope(FontWeight.ExtraBold),
    )

private fun text(weight: FontWeight, size: Int, lineHeight: Int, letterSpacing: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

/**
 * Text styles of the 2026-11 look (DESIGN 19.4), beside Material's scale: heavier titles and tighter numbers than
 * the scale has. Sizes are sp, so they follow the phone's text size like every other style.
 */
object SpoilNotText {
    /** A tab's or pushed screen's title. */
    val ScreenTitle = text(FontWeight.ExtraBold, size = 30, lineHeight = 40, letterSpacing = -0.5)

    /** The line above a screen's title: the date, or how many items. */
    val ScreenEyebrow = text(FontWeight.SemiBold, size = 14, lineHeight = 20)

    /** The heading of a group of rows. */
    val GroupHeading = text(FontWeight.ExtraBold, size = 14, lineHeight = 20)

    /** A row's name. */
    val RowTitle = text(FontWeight.Bold, size = 16, lineHeight = 22)

    /** A row's second line. */
    val RowSupporting = text(FontWeight.SemiBold, size = 13, lineHeight = 18)

    /** The status words in a row's second line. */
    val RowStatus = text(FontWeight.ExtraBold, size = 13, lineHeight = 18)

    /** The heading of a section on the page or of a plain card, such as Home's Today. */
    val SectionTitle = text(FontWeight.ExtraBold, size = 18, lineHeight = 24)

    /** The title of a highlight card. */
    val CardTitle = text(FontWeight.ExtraBold, size = 22, lineHeight = 28, letterSpacing = -0.2)

    /** The one large number of a card, such as the month's percentage. */
    val BigNumber = text(FontWeight.ExtraBold, size = 48, lineHeight = 54, letterSpacing = -1.5)

    /** The month's percentage on Your kitchen, the screen it is the point of. */
    val HeroNumber = text(FontWeight.ExtraBold, size = 64, lineHeight = 70, letterSpacing = -2.0)

    /** The number of an accent card, such as the streak's days. */
    val AccentNumber = text(FontWeight.ExtraBold, size = 26, lineHeight = 32, letterSpacing = -0.3)

    /** The label of a large filled button and of the floating button. */
    val ButtonLarge = text(FontWeight.ExtraBold, size = 16, lineHeight = 24)

    /** The label of a tonal button and of a connected button. */
    val ButtonMedium = text(FontWeight.Bold, size = 14, lineHeight = 20)

    /** The text of a label chip. */
    val ChipLabel = text(FontWeight.Bold, size = 12, lineHeight = 16)

    /** A sheet's title, such as Add item. */
    val SheetTitle = text(FontWeight.ExtraBold, size = 26, lineHeight = 34, letterSpacing = -0.3)

    /** A dialog's title. */
    val DialogTitle = text(FontWeight.ExtraBold, size = 24, lineHeight = 32, letterSpacing = -0.3)

    /** The label above a text field or a picker on a form. */
    val FieldLabel = text(FontWeight.Bold, size = 13, lineHeight = 18)

    /** What is typed in a text field, and a picker's value. */
    val FieldText = text(FontWeight.SemiBold, size = 17, lineHeight = 24)
}

/**
 * Fredoka (after v1), a rounded display face bundled as one variable font, for the app's name on the welcome
 * screen only; everything else stays Manrope. Licence in assets/licenses/fredoka-OFL.txt.
 */
val Fredoka =
    FontFamily(
        Font(
            resId = R.font.fredoka,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(FontWeight.SemiBold.weight)),
        ),
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
