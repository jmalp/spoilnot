package com.parcha.spoilnot.feature.tutorial.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.CoachMarkOverlay
import com.parcha.spoilnot.core.designsystem.component.CoachMarks
import com.parcha.spoilnot.feature.tutorial.domain.Tour
import com.parcha.spoilnot.feature.tutorial.domain.TourEvent
import com.parcha.spoilnot.feature.tutorial.domain.TourStep

/** One tour step over the app: the hole around its target (if it is on screen), its text and the buttons. */
@Composable
fun TourOverlay(step: TourStep, marks: CoachMarks, onEvent: (TourEvent) -> Unit, modifier: Modifier = Modifier) {
    val last = step == TourStep.entries.last()
    CoachMarkOverlay(
        target = step.target?.let { marks.boundsOf(it.key) },
        title = if (step == TourStep.Welcome) stringResource(R.string.tour_welcome_title) else null,
        text = stringResource(step.text),
        progress = Tour.position(step)?.let { stringResource(R.string.tour_progress, it, Tour.countedSteps) },
        nextLabel = stringResource(
            when {
                step == TourStep.Welcome -> R.string.tour_start
                last -> R.string.tour_done
                else -> R.string.tour_next
            },
        ),
        onNext = { onEvent(TourEvent.Next) },
        skipLabel = if (last) null else stringResource(R.string.tour_skip),
        onSkip = { onEvent(TourEvent.Skip) },
        modifier = modifier,
    )
}

@get:StringRes
private val TourStep.text: Int
    get() = when (this) {
        TourStep.Welcome -> R.string.tour_welcome
        TourStep.Home -> R.string.tour_home
        TourStep.OpenPantry -> R.string.tour_open_pantry
        TourStep.AddItem -> R.string.tour_add_item
        TourStep.PantryRows -> R.string.tour_pantry_rows
        TourStep.OpenRecipes -> R.string.tour_open_recipes
        TourStep.AddRecipe -> R.string.tour_add_recipe
        TourStep.OpenGroceries -> R.string.tour_open_groceries
        TourStep.QuickAdd -> R.string.tour_quick_add
        TourStep.Invite -> R.string.tour_invite
    }
