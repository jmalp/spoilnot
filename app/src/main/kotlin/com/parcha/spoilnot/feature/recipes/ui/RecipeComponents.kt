package com.parcha.spoilnot.feature.recipes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.parcha.spoilnot.R
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.recipes.domain.Recipe

/** A remote image cached on the device; missing or failed images show a placeholder (DESIGN 11.6). */
@Composable
fun RecipeImage(url: String?, modifier: Modifier = Modifier, title: String? = null) {
    // Without a photo (after v1): the recipe's first letter on one of the theme's container colors, chosen by the
    // title so each card keeps its color; the grey book on every card looked unfinished.
    val initial = title?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
    val tones = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
    )
    val (background, foreground) = if (initial == null) {
        MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        tones[Math.floorMod(NameKey.of(title).hashCode(), tones.size)]
    }
    val placeholder = @Composable {
        Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
            if (initial != null) {
                Text(initial.toString(), style = MaterialTheme.typography.displayMedium, color = foreground)
            } else {
                Icon(
                    Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = foreground,
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

/** A card in the recipe box: image, title, total time and a favorite star (DESIGN 5.5). */
@Composable
fun RecipeCard(recipe: Recipe, onOpen: () -> Unit, onToggleFavorite: () -> Unit, modifier: Modifier = Modifier) {
    val time = durationText(recipe.content.totalMinutes)
    Card(onClick = onOpen, modifier = modifier.testTag("recipe_card")) {
        Box {
            RecipeImage(recipe.content.imageUrl, Modifier.fillMaxWidth().aspectRatio(4f / 3f), recipe.title)
            IconToggleButton(
                checked = recipe.favorite,
                onCheckedChange = { onToggleFavorite() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), MaterialTheme.shapes.extraLarge),
            ) {
                Icon(
                    if (recipe.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = stringResource(
                        if (recipe.favorite) R.string.recipe_unfavorite else R.string.recipe_favorite,
                    ),
                    tint = if (recipe.favorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                recipe.title,
                style = MaterialTheme.typography.titleMedium,
                // Two lines on every card, and the time row's space kept, so cards side by side match (after v1).
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (time == null) {
                Text(" ", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            } else {
                val description = stringResource(R.string.recipe_total_time, time)
                Row(
                    Modifier.padding(top = 4.dp).clearAndSetSemantics { contentDescription = description },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        time,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun servingsLabel(servings: Int?, text: String?): String? = when {
    servings != null -> pluralStringResource(R.plurals.recipe_servings, servings, servings)
    text != null -> text
    else -> null
}
