package com.parcha.spoilnot.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * A screen pushed on a tab's stack. It keeps the tab it was opened on, so the same screen on two tabs has two keys
 * (and two saved states), and the shell can tell a push within a tab from a tab switch (DESIGN 6.4).
 */
interface PushedRoute : NavKey {
    val tab: TopLevelDestination
}

@Serializable
data object HomeRoute : NavKey

@Serializable
data object PantryRoute : NavKey

/** The meal planner (after v1, DESIGN 5.9). */
@Serializable
data object PlanRoute : NavKey

@Serializable
data object RecipesRoute : NavKey

@Serializable
data object GroceriesRoute : NavKey

/**
 * Settings opens from the avatar button on any tab, on top of that tab's back stack (DESIGN 6.2). The tab is part
 * of the key so two tabs can each have Settings open without sharing saved state.
 */
@Serializable
data class SettingsRoute(override val tab: TopLevelDestination) : PushedRoute

/** Settings > Household, pushed on the same tab as Settings. */
@Serializable
data class HouseholdSettingsRoute(override val tab: TopLevelDestination) : PushedRoute

/** Settings > Notifications, pushed on the same tab as Settings. */
@Serializable
data class NotificationSettingsRoute(override val tab: TopLevelDestination) : PushedRoute

/** A saved recipe (DESIGN 5.5), pushed on the tab that opened it. */
@Serializable
data class RecipeDetailRoute(val recipeId: String, override val tab: TopLevelDestination) : PushedRoute

/**
 * Create manually ([recipeId] null) or edit. A failed import prefills the title, image and link (DESIGN 11.2).
 */
@Serializable
data class RecipeEditRoute(
    val recipeId: String?,
    override val tab: TopLevelDestination,
    val prefillTitle: String? = null,
    val prefillImageUrl: String? = null,
    val prefillSourceUrl: String? = null,
) : PushedRoute

/** The preview of a recipe fetched from a link (Paste link and the share sheet). */
@Serializable
data class RecipeImportRoute(val url: String?, override val tab: TopLevelDestination) : PushedRoute

/**
 * The built-in browser (DESIGN 5.5). [fallback] is true when a fetched page had no recipe, so a page without one
 * offers Create manually with [prefillTitle] and [prefillImageUrl].
 */
@Serializable
data class RecipeBrowserRoute(
    val url: String?,
    override val tab: TopLevelDestination,
    val fallback: Boolean = false,
    val prefillTitle: String? = null,
    val prefillImageUrl: String? = null,
) : PushedRoute

/** Cook mode at [servings] (the detail screen's stepper). */
@Serializable
data class CookModeRoute(val recipeId: String, val servings: Int?, override val tab: TopLevelDestination) : PushedRoute

/** Insights (DESIGN 5.7), opened from Home's "Your kitchen this month" card. */
@Serializable
data class InsightsRoute(override val tab: TopLevelDestination) : PushedRoute

/** Settings > About, pushed on the same tab as Settings. */
@Serializable
data class AboutRoute(override val tab: TopLevelDestination) : PushedRoute
