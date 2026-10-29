package com.parcha.spoilnot.feature.tutorial.domain

/** The bottom-bar tabs, as the tour knows them (the shell maps them to its destinations). */
enum class TourTab { Home, Pantry, Plan, Recipes, Groceries }

/** What a tour step points at; the UI marks each with a coach mark key of the same name. */
enum class TourTarget(val key: String) {
    HomeCards("tour_home_cards"),
    PantryTab("tour_tab_pantry"),
    AddItem("tour_add_item"),
    PantryList("tour_pantry_list"),
    PlanTab("tour_tab_plan"),
    RecipesTab("tour_tab_recipes"),
    AddRecipe("tour_add_recipe"),
    GroceriesTab("tour_tab_groceries"),
    QuickAdd("tour_quick_add"),
    Settings("tour_settings"),
}

/**
 * The follow-along tour for new users (after v1, DESIGN 5.8). Each step points at [target] on [tab] (the shell
 * shows that tab when the step starts with Next). A step with [advanceOnTab] also moves on when the user opens that
 * tab, one with [advanceOnTap] when the user taps the target itself: the tour follows along instead of only telling.
 */
enum class TourStep(
    val tab: TourTab?,
    val target: TourTarget?,
    val advanceOnTab: TourTab? = null,
    val advanceOnTap: Boolean = false,
) {
    Welcome(null, null),
    Home(TourTab.Home, TourTarget.HomeCards),
    OpenPantry(TourTab.Home, TourTarget.PantryTab, advanceOnTab = TourTab.Pantry),
    AddItem(TourTab.Pantry, TourTarget.AddItem, advanceOnTap = true),
    PantryRows(TourTab.Pantry, TourTarget.PantryList),

    /** After v1: the meal planner (DESIGN 5.9). */
    OpenPlan(TourTab.Pantry, TourTarget.PlanTab, advanceOnTab = TourTab.Plan),
    OpenRecipes(TourTab.Plan, TourTarget.RecipesTab, advanceOnTab = TourTab.Recipes),
    AddRecipe(TourTab.Recipes, TourTarget.AddRecipe, advanceOnTap = true),
    OpenGroceries(TourTab.Recipes, TourTarget.GroceriesTab, advanceOnTab = TourTab.Groceries),
    QuickAdd(TourTab.Groceries, TourTarget.QuickAdd),
    Invite(TourTab.Groceries, TourTarget.Settings),
}

sealed interface TourEvent {
    data object Next : TourEvent

    data object Skip : TourEvent

    data class TabOpened(val tab: TourTab) : TourEvent

    data class TargetTapped(val target: TourTarget) : TourEvent
}

/** The tour's state machine: pure, so every path is unit tested. */
object Tour {
    val first: TourStep = TourStep.Welcome

    /** Steps the user can see as "n of total": the welcome card is not counted. */
    val countedSteps: Int = TourStep.entries.size - 1

    /** "3 of 9" for [step]; null for the welcome card. */
    fun position(step: TourStep): Int? = if (step == TourStep.Welcome) null else step.ordinal

    /** The step after [event] on [step], or null when the tour is over (finished or skipped). */
    fun next(step: TourStep, event: TourEvent): TourStep? = when (event) {
        TourEvent.Skip -> null
        TourEvent.Next -> after(step)
        is TourEvent.TabOpened -> if (step.advanceOnTab == event.tab) after(step) else step
        is TourEvent.TargetTapped -> if (step.advanceOnTap && step.target == event.target) after(step) else step
    }

    private fun after(step: TourStep): TourStep? = TourStep.entries.getOrNull(step.ordinal + 1)
}
