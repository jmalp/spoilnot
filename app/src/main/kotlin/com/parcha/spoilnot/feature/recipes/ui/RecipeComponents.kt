package com.parcha.spoilnot.feature.recipes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.theme.LeafShape
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotCorners
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.recipes.domain.Recipe

/**
 * How big a recipe's letter is drawn without a photo: its leaf's size, the letter in it, and how far above the
 * block's bottom it sits ([bottom]; centered when null).
 */
enum class RecipeLetterSize(val leaf: Dp, val letter: Int, val bottom: Dp?) {
    /** A card in the recipe box. */
    Card(68.dp, 30, null),

    /** The block at the top of a recipe: low, clear of the round buttons and above the page that overlaps it. */
    Header(96.dp, 44, 44.dp),
}

/**
 * A remote image cached on the device; missing or failed images show a placeholder (DESIGN 11.6). Without a photo
 * (DESIGN 19.12) the placeholder is a block in one of the theme's container colors with the recipe's first letter in
 * a leaf of the matching strong color, chosen by the title so each recipe keeps its color.
 */
@Composable
fun RecipeImage(
    url: String?,
    modifier: Modifier = Modifier,
    title: String? = null,
    letterSize: RecipeLetterSize = RecipeLetterSize.Card,
) {
    val initial = title?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
    val colors = MaterialTheme.colorScheme
    // Block, leaf, letter.
    val tones = listOf(
        Triple(colors.primaryContainer, colors.primary, colors.onPrimary),
        Triple(colors.secondaryContainer, colors.secondary, colors.onSecondary),
        Triple(colors.tertiaryContainer, colors.tertiary, colors.onTertiary),
    )
    val (block, leaf, letter) = if (initial == null) {
        Triple(colors.surfaceContainerHigh, Color.Transparent, colors.onSurfaceVariant)
    } else {
        tones[Math.floorMod(NameKey.of(title).hashCode(), tones.size)]
    }
    val placeholder = @Composable {
        Box(
            Modifier.fillMaxSize().background(block).padding(bottom = letterSize.bottom ?: 0.dp),
            contentAlignment = if (letterSize.bottom != null) Alignment.BottomCenter else Alignment.Center,
        ) {
            if (initial != null) {
                // A picture, not a word: TalkBack read "S, Spinach omelette" (review A11Y-07).
                Box(
                    Modifier.size(letterSize.leaf).background(leaf, LeafShape).clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        initial.toString(),
                        style = SpoilNotText.ScreenTitle,
                        fontSize = letterSize.letter.sp,
                        lineHeight = letterSize.letter.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = letter,
                    )
                }
            } else {
                Icon(
                    Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = letter,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
    if (url == null) {
        Box(modifier) { placeholder() }
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = { placeholder() },
            error = { placeholder() },
            modifier = modifier,
        )
    }
}

/** "1 hr 15 min", or null when the time is unknown. */
@Composable
fun durationText(minutes: Int?): String? {
    if (minutes == null) return null
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> stringResource(R.string.recipe_minutes, m)
        m == 0 -> stringResource(R.string.recipe_hours, h)
        else -> stringResource(R.string.recipe_hours_minutes, h, m)
    }
}

/** The height of a card's picture or letter block (DESIGN 19.12). */
private val CardImageHeight = 120.dp

/**
 * A card in the recipe box (DESIGN 5.5, 19.8): a plain card with the photo or the letter in a leaf, the title and the
 * total time, and the favorite star in a round button over the picture.
 */
@Composable
fun RecipeCard(recipe: Recipe, onOpen: () -> Unit, onToggleFavorite: () -> Unit, modifier: Modifier = Modifier) {
    val time = durationText(recipe.content.totalMinutes)
    Surface(
        onClick = onOpen,
        modifier = modifier.testTag("recipe_card"),
        shape = RoundedCornerShape(SpoilNotCorners.Card),
        color = SpoilNotSurfaces.current.row,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column {
            Box {
                RecipeImage(
                    recipe.content.imageUrl,
                    Modifier.fillMaxWidth().height(CardImageHeight),
                    recipe.title,
                )
                FavoriteToggle(
                    favorite = recipe.favorite,
                    onToggle = onToggleFavorite,
                    round = true,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
            Column(
                Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    recipe.title,
                    style = SpoilNotText.RowTitle,
                    // Two lines on every card, and the time row's space kept, so cards side by side match (after v1).
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (time == null) {
                    Text(" ", style = SpoilNotText.RowSupporting, modifier = Modifier.clearAndSetSemantics { })
                } else {
                    val description = stringResource(R.string.recipe_total_time, time)
                    Row(
                        Modifier.clearAndSetSemantics { contentDescription = description },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            time,
                            style = SpoilNotText.RowSupporting,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The favorite star, on recipe cards and recipe detail (review A11Y-08): one name, "Favorite", with its on or off
 * state, so TalkBack says "Favorite, checked" instead of a label that flips with the state and the state again.
 * [round] puts it in a 48 dp circle of the row color, over a picture (DESIGN 19.7).
 */
@Composable
fun FavoriteToggle(favorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, round: Boolean = false) {
    IconToggleButton(
        checked = favorite,
        onCheckedChange = { onToggle() },
        modifier = if (round) {
            modifier.size(48.dp).background(SpoilNotSurfaces.current.row, CircleShape)
        } else {
            modifier
        },
    ) {
        Icon(
            if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = stringResource(R.string.recipe_favorite_toggle),
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun servingsLabel(servings: Int?, text: String?): String? = when {
    servings != null -> pluralStringResource(R.plurals.recipe_servings, servings, servings)
    text != null -> text
    else -> null
}

/**
 * A fact about a recipe as a pill on the row color (DESIGN 19.12): the total time, the servings, the site it came
 * from. With [onClick] it is a button (the site opens the original).
 */
@Composable
fun InfoPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: (() -> Unit)? = null) {
    val content = @Composable {
        Row(
            Modifier.heightIn(min = 32.dp).padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = SpoilNotText.ButtonMedium)
        }
    }
    val color = SpoilNotSurfaces.current.row
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    if (onClick != null) {
        // Material's clickable surface keeps a 48 dp touch target around the 32 dp pill.
        Surface(onClick = onClick, shape = CircleShape, color = color, contentColor = contentColor) { content() }
    } else {
        Surface(shape = CircleShape, color = color, contentColor = contentColor) { content() }
    }
}
