package com.parcha.spoilnot.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.CoachMarks
import com.parcha.spoilnot.core.designsystem.component.LocalCoachMarks
import com.parcha.spoilnot.core.designsystem.component.SpoilNotSnackbarHost
import com.parcha.spoilnot.core.designsystem.component.coachMark
import com.parcha.spoilnot.core.designsystem.component.rememberScreenReaderOn
import com.parcha.spoilnot.core.designsystem.motion.Motion
import com.parcha.spoilnot.core.designsystem.motion.rememberReduceMotion
import com.parcha.spoilnot.core.ui.MessageEffect
import com.parcha.spoilnot.feature.grocery.ui.GroceriesScreen
import com.parcha.spoilnot.feature.home.ui.HomeNavigation
import com.parcha.spoilnot.feature.home.ui.HomeScreen
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.ui.HouseholdSettingsScreen
import com.parcha.spoilnot.feature.insights.ui.InsightsScreen
import com.parcha.spoilnot.feature.notifications.ui.NotificationExplainer
import com.parcha.spoilnot.feature.notifications.ui.NotificationSettingsScreen
import com.parcha.spoilnot.feature.notifications.ui.RemindersViewModel
import com.parcha.spoilnot.feature.notifications.ui.rememberTurnOnNotifications
import com.parcha.spoilnot.feature.pantry.ui.LocalUnits
import com.parcha.spoilnot.feature.pantry.ui.PantryScreen
import com.parcha.spoilnot.feature.plan.ui.PlanNavigation
import com.parcha.spoilnot.feature.plan.ui.PlanScreen
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.ui.CookModeScreen
import com.parcha.spoilnot.feature.recipes.ui.RecipeBrowserScreen
import com.parcha.spoilnot.feature.recipes.ui.RecipeDetailScreen
import com.parcha.spoilnot.feature.recipes.ui.RecipeEditScreen
import com.parcha.spoilnot.feature.recipes.ui.RecipeImportScreen
import com.parcha.spoilnot.feature.recipes.ui.RecipesNavigation
import com.parcha.spoilnot.feature.recipes.ui.RecipesScreen
import com.parcha.spoilnot.feature.settings.ui.AboutScreen
import com.parcha.spoilnot.feature.settings.ui.SettingsScreen
import com.parcha.spoilnot.feature.tutorial.domain.TourEvent
import com.parcha.spoilnot.feature.tutorial.domain.TourTab
import com.parcha.spoilnot.feature.tutorial.domain.TourTarget
import com.parcha.spoilnot.feature.tutorial.ui.TourOverlay
import com.parcha.spoilnot.feature.tutorial.ui.TourViewModel

/** Waiting join requests, shown as a badge on the Settings avatar for the owner (DESIGN 5.1). */
val LocalJoinRequestCount = compositionLocalOf { 0 }

/** The signed-in app: five tabs in a bottom NavigationBar, each with its own back stack (DESIGN 6.2). */
@Composable
fun AppShell(
    session: Session.InHousehold,
    modifier: Modifier = Modifier,
    shellViewModel: ShellViewModel = hiltViewModel(),
    tourViewModel: TourViewModel = hiltViewModel(),
    remindersViewModel: RemindersViewModel = hiltViewModel(),
) {
    val tourStep by tourViewModel.step.collectAsStateWithLifecycle()
    // Reminders on this phone (after v1, review UX-02): asked on any tab, and Home says when they are off.
    val reminders by remindersViewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        remindersViewModel.refresh()
        onPauseOrDispose { }
    }
    val askSystem = rememberTurnOnNotifications(onFinished = remindersViewModel::turnOnFinished)
    val turnOnReminders = {
        remindersViewModel.turnOnStarted()
        askSystem()
    }
    // A tap on a marked element can move a follow-along step on (DESIGN 5.8).
    val coachMarks = remember {
        CoachMarks { key ->
            TourTarget.entries.firstOrNull { it.key == key }?.let { tourViewModel.on(TourEvent.TargetTapped(it)) }
        }
    }
    val pendingJoinRequests by shellViewModel.pendingJoinRequests.collectAsStateWithLifecycle()
    val shellSnackbar = remember { SnackbarHostState() }
    MessageEffect(shellViewModel.messages, shellSnackbar, onUndo = {})
    val deepLink by shellViewModel.deepLink.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable { mutableStateOf(TabNavigator.START) }
    val backStacks = TopLevelDestination.entries.associateWith { tab -> key(tab) { rememberNavBackStack(tab.root) } }
    val navigator = remember(backStacks) {
        TabNavigator(backStacks, { currentTab }, { currentTab = it }, closesOnLeave = ::isSettingsScreen)
    }

    val openSettings = { navigator.navigate(SettingsRoute(navigator.currentTab)) }
    // Each tour step starts on its tab, at its root (Next may move on to another one): taken from Settings, the
    // Home step must close Settings on the Home tab too, or it points at nothing.
    LaunchedEffect(tourStep) {
        tourStep?.tab?.let { tab -> navigator.showRoot(tab.destination()) }
    }
    // A tapped digest or a Home card opens the Pantry tab at its root; the Pantry screen applies the filter.
    LaunchedEffect(deepLink) {
        when (val link = deepLink) {
            is DeepLink.Pantry -> {
                navigator.showRoot(TopLevelDestination.Pantry)
                shellViewModel.navigated(link)
            }

            is DeepLink.Plan -> {
                navigator.showRoot(TopLevelDestination.Plan)
                shellViewModel.navigated(link)
                shellViewModel.consume(link)
            }

            // A link shared from another app opens its preview on the Recipes tab (DESIGN 11.1).
            is DeepLink.ImportRecipe -> {
                navigator.showRoot(TopLevelDestination.Recipes)
                navigator.navigate(RecipeImportRoute(link.url, TopLevelDestination.Recipes))
                shellViewModel.navigated(link)
                shellViewModel.consume(link)
            }

            null -> Unit
        }
    }
    val openRecipe = { id: String -> navigator.navigate(RecipeDetailRoute(id, navigator.currentTab)) }
    val provider = entryProvider<NavKey> {
        entry<HomeRoute>(metadata = onTab(TopLevelDestination.Home)) {
            HomeScreen(
                navigation = HomeNavigation(
                    openSettings = openSettings,
                    openPantry = { filter -> shellViewModel.open(DeepLink.Pantry(filter)) },
                    openGroceries = { navigator.showRoot(TopLevelDestination.Groceries) },
                    reviewJoinRequests = { navigator.navigate(HouseholdSettingsRoute(navigator.currentTab)) },
                    openInsights = { navigator.navigate(InsightsRoute(navigator.currentTab)) },
                    openRecipe = openRecipe,
                    openPlan = { navigator.showRoot(TopLevelDestination.Plan) },
                    turnOnReminders = turnOnReminders,
                ),
                pendingJoinRequests = if (session.role == Role.Owner) pendingJoinRequests else 0,
                remindersOff = reminders.offOnThisPhone,
            )
        }
        entry<PantryRoute>(metadata = onTab(TopLevelDestination.Pantry)) { PantryScreen(onOpenSettings = openSettings) }
        entry<PlanRoute>(metadata = onTab(TopLevelDestination.Plan)) {
            PlanScreen(PlanNavigation(openSettings = openSettings, openRecipe = openRecipe))
        }
        entry<RecipesRoute>(metadata = onTab(TopLevelDestination.Recipes)) {
            RecipesScreen(
                RecipesNavigation(
                    openSettings = openSettings,
                    openRecipe = openRecipe,
                    importLink = { navigator.navigate(RecipeImportRoute(it, navigator.currentTab)) },
                    openBrowser = { navigator.navigate(RecipeBrowserRoute(null, navigator.currentTab)) },
                    createManually = { navigator.navigate(RecipeEditRoute(null, navigator.currentTab)) },
                ),
            )
        }
        entry<RecipeDetailRoute>(metadata = ::onItsTab) { key ->
            RecipeDetailScreen(
                recipeId = key.recipeId,
                onBack = { navigator.goBack() },
                onEdit = { navigator.navigate(RecipeEditRoute(key.recipeId, key.tab)) },
                onCook = { servings -> navigator.navigate(CookModeRoute(key.recipeId, servings, key.tab)) },
            )
        }
        entry<RecipeEditRoute>(metadata = ::onItsTab) { key ->
            RecipeEditScreen(
                recipeId = key.recipeId,
                prefill = key.prefill(),
                onBack = { navigator.goBack() },
                onSaved = { id ->
                    if (key.recipeId == null) navigator.replace(RecipeDetailRoute(id, key.tab)) else navigator.goBack()
                },
            )
        }
        entry<RecipeImportRoute>(metadata = ::onItsTab) { key ->
            RecipeImportScreen(
                url = key.url,
                onBack = { navigator.goBack() },
                onSaved = { navigator.replace(RecipeDetailRoute(it, key.tab)) },
                onOpenExisting = { navigator.replace(RecipeDetailRoute(it, key.tab)) },
                onTryBrowser = { url, prefill ->
                    navigator.replace(
                        RecipeBrowserRoute(
                            url,
                            key.tab,
                            fallback = true,
                            prefill.title.ifEmpty {
                                null
                            },
                            prefill.imageUrl,
                        ),
                    )
                },
            )
        }
        entry<RecipeBrowserRoute>(metadata = ::onItsTab) { key ->
            RecipeBrowserScreen(
                url = key.url,
                fallback = key.fallback,
                prefill = RecipeContent(
                    title = key.prefillTitle.orEmpty(),
                    imageUrl = key.prefillImageUrl,
                    sourceUrl = key.url,
                ),
                onBack = { navigator.goBack() },
                onOpenRecipe = openRecipe,
                onCreateManually = { p ->
                    navigator.navigate(
                        RecipeEditRoute(
                            null,
                            key.tab,
                            p.title.ifEmpty {
                                null
                            },
                            p.imageUrl,
                            p.sourceUrl,
                        ),
                    )
                },
            )
        }
        entry<CookModeRoute>(metadata = ::onItsTab) { key ->
            CookModeScreen(recipeId = key.recipeId, servings = key.servings, onBack = { navigator.goBack() })
        }
        entry<GroceriesRoute>(metadata = onTab(TopLevelDestination.Groceries)) {
            GroceriesScreen(onOpenSettings = openSettings)
        }
        entry<SettingsRoute>(metadata = ::onItsTab) {
            SettingsScreen(
                onBack = { navigator.goBack() },
                onOpenHousehold = { navigator.navigate(HouseholdSettingsRoute(navigator.currentTab)) },
                onOpenNotifications = { navigator.navigate(NotificationSettingsRoute(navigator.currentTab)) },
                onOpenAbout = { navigator.navigate(AboutRoute(navigator.currentTab)) },
            )
        }
        entry<AboutRoute>(metadata = ::onItsTab) { AboutScreen(onBack = { navigator.goBack() }) }
        entry<InsightsRoute>(metadata = ::onItsTab) { InsightsScreen(onBack = { navigator.goBack() }) }
        entry<NotificationSettingsRoute>(metadata = ::onItsTab) {
            NotificationSettingsScreen(onBack = { navigator.goBack() })
        }
        entry<HouseholdSettingsRoute>(metadata = ::onItsTab) {
            HouseholdSettingsScreen(onBack = { navigator.goBack() })
        }
    }

    // Decorate every tab's stack on every composition so saved state and ViewModels of hidden tabs survive.
    val entriesByTab: Map<TopLevelDestination, List<NavEntry<NavKey>>> =
        TopLevelDestination.entries.associateWith { tab ->
            key(tab) {
                rememberDecoratedNavEntries(
                    backStack = backStacks.getValue(tab),
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = provider,
                )
            }
        }
    val visibleEntries = navigator.visible(entriesByTab::getValue)

    CompositionLocalProvider(
        LocalJoinRequestCount provides pendingJoinRequests,
        LocalUnits provides session.units,
        LocalCoachMarks provides coachMarks,
    ) {
        val reduceMotion = rememberReduceMotion()
        // TalkBack stays on the tour's card while it shows (review A11Y-10). Only with a screen reader on: a finger
        // still follows the tour by tapping the highlighted tab, and UI tests do the same through the tree.
        val screenReader = rememberScreenReaderOn()
        ShellLayout(
            modifier = modifier,
            hideBehindOverlay = tourStep != null && screenReader,
            bar = { barModifier ->
                TabBar(currentTab, barModifier) { tab ->
                    navigator.selectTab(tab)
                    tourViewModel.on(TourEvent.TabOpened(tab.tourTab()))
                }
            },
            overlay = { tourStep?.let { TourOverlay(it, coachMarks, tourViewModel::on) } },
        ) {
            // The quota banner is above the root (SpoilNotRoot), so it shows on every screen (after v1, REL-02).
            NavDisplay(
                entries = visibleEntries,
                onBack = { navigator.goBack() },
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { screenChange(forward = true, newOnTop = true, reduceMotion) },
                popTransitionSpec = { screenChange(forward = false, newOnTop = false, reduceMotion) },
                predictivePopTransitionSpec = { screenChange(forward = false, newOnTop = false, reduceMotion) },
            )
            // Refused writes from any screen (after v1, REL-13), above the navigation bar.
            SpoilNotSnackbarHost(shellSnackbar, Modifier.align(Alignment.BottomCenter))
        }
        if (reminders.ask && tourStep == null) {
            NotificationExplainer(reminders.hour, onTurnOn = turnOnReminders, onLater = remindersViewModel::later)
        }
    }
}

/**
 * The five tabs (DESIGN 6.2), each label on one line (review UX-14). In the 2026-11 look (DESIGN 19.11) the bar is
 * `surfaceContainer` and the current tab's indicator `primaryContainer`.
 */
@Composable
internal fun TabBar(
    currentTab: TopLevelDestination,
    modifier: Modifier = Modifier,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NavigationBar(modifier, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        TopLevelDestination.entries.forEach { tab ->
            val selected = tab == currentTab
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.coachMark(tab.tourTarget()?.key ?: "tab_${tab.name}"),
                icon = { Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, contentDescription = null) },
                label = { TabLabel(stringResource(tab.label)) },
                colors = colors,
            )
        }
    }
}

private fun TourTab.destination(): TopLevelDestination = when (this) {
    TourTab.Home -> TopLevelDestination.Home
    TourTab.Pantry -> TopLevelDestination.Pantry
    TourTab.Plan -> TopLevelDestination.Plan
    TourTab.Recipes -> TopLevelDestination.Recipes
    TourTab.Groceries -> TopLevelDestination.Groceries
}

private fun TopLevelDestination.tourTab(): TourTab = TourTab.entries.first { it.destination() == this }

private fun TopLevelDestination.tourTarget(): TourTarget? = when (this) {
    TopLevelDestination.Home -> null
    TopLevelDestination.Pantry -> TourTarget.PantryTab
    TopLevelDestination.Plan -> TourTarget.PlanTab
    TopLevelDestination.Recipes -> TourTarget.RecipesTab
    TopLevelDestination.Groceries -> TourTarget.GroceriesTab
}

/** A failed import's title, image and link for Create manually (DESIGN 11.2). */
private fun RecipeEditRoute.prefill(): RecipeContent? =
    if (prefillTitle == null && prefillImageUrl == null && prefillSourceUrl == null) {
        null
    } else {
        RecipeContent(title = prefillTitle.orEmpty(), imageUrl = prefillImageUrl, sourceUrl = prefillSourceUrl)
    }

/**
 * How the shell moves between two screens (DESIGN 6.4): Material's shared axis within a tab, a single fade between
 * tabs (after v1, review PERF-09). [newOnTop] is false for a pop, which the navigation draws under the old screen.
 */
private fun AnimatedContentTransitionScope<Scene<NavKey>>.screenChange(
    forward: Boolean,
    newOnTop: Boolean,
    reduceMotion: Boolean,
): ContentTransform = if (initialState.tab() != targetState.tab()) {
    Motion.tabSwitch(newOnTop, reduceMotion)
} else {
    Motion.sharedAxis(forward, reduceMotion)
}

/** The tab a scene's screen belongs to, from its entry's [TAB_METADATA]. */
private fun Scene<NavKey>.tab(): Any? = entries.lastOrNull()?.metadata?.get(TAB_METADATA)

/** An entry's metadata key for its tab, so a transition can tell a tab switch from a push. */
private const val TAB_METADATA = "spoilnot.tab"

/** The metadata of a tab's root on [tab]. */
private fun onTab(tab: TopLevelDestination): Map<String, Any> = mapOf(TAB_METADATA to tab)

/** The metadata of a pushed screen, on the tab it was opened on. */
private fun onItsTab(key: PushedRoute): Map<String, Any> = onTab(key.tab)

/** Settings and its pages close when their tab is left. */
private fun isSettingsScreen(key: NavKey) =
    key is SettingsRoute || key is HouseholdSettingsRoute || key is NotificationSettingsRoute || key is AboutRoute
