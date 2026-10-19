package com.parcha.spoilnot.core.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.motion.rememberReduceMotion
import com.parcha.spoilnot.core.error.QuotaSignal
import com.parcha.spoilnot.feature.grocery.ui.GroceriesScreen
import com.parcha.spoilnot.feature.home.ui.HomeNavigation
import com.parcha.spoilnot.feature.home.ui.HomeScreen
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.ui.HouseholdSettingsScreen
import com.parcha.spoilnot.feature.insights.ui.InsightsScreen
import com.parcha.spoilnot.feature.notifications.ui.NotificationSettingsScreen
import com.parcha.spoilnot.feature.pantry.ui.LocalUnits
import com.parcha.spoilnot.feature.pantry.ui.PantryScreen
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

/** Waiting join requests, shown as a badge on the Settings avatar for the owner (DESIGN 5.1). */
val LocalJoinRequestCount = compositionLocalOf { 0 }

/** The signed-in app: four tabs in a bottom NavigationBar, each with its own back stack (DESIGN 6.2). */
@Composable
fun AppShell(
    session: Session.InHousehold,
    modifier: Modifier = Modifier,
    shellViewModel: ShellViewModel = hiltViewModel(),
) {
    val pendingJoinRequests by shellViewModel.pendingJoinRequests.collectAsStateWithLifecycle()
    val deepLink by shellViewModel.deepLink.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable { mutableStateOf(TabNavigator.START) }
    val backStacks = TopLevelDestination.entries.associateWith { tab -> key(tab) { rememberNavBackStack(tab.root) } }
    val navigator = remember(backStacks) {
        TabNavigator(backStacks, { currentTab }, { currentTab = it }, closesOnLeave = ::isSettingsScreen)
    }

    val openSettings = { navigator.navigate(SettingsRoute(navigator.currentTab)) }
    // A tapped digest or a Home card opens the Pantry tab at its root; the Pantry screen applies the filter.
    LaunchedEffect(deepLink) {
        when (val link = deepLink) {
            is DeepLink.Pantry -> {
                navigator.showRoot(TopLevelDestination.Pantry)
                shellViewModel.navigated(link)
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
        entry<HomeRoute> {
            HomeScreen(
                navigation = HomeNavigation(
                    openSettings = openSettings,
                    openPantry = { filter -> shellViewModel.open(DeepLink.Pantry(filter)) },
                    openGroceries = { navigator.showRoot(TopLevelDestination.Groceries) },
                    reviewJoinRequests = { navigator.navigate(HouseholdSettingsRoute(navigator.currentTab)) },
                    openInsights = { navigator.navigate(InsightsRoute(navigator.currentTab)) },
                ),
                pendingJoinRequests = if (session.role == Role.Owner) pendingJoinRequests else 0,
            )
        }
        entry<PantryRoute> { PantryScreen(onOpenSettings = openSettings) }
        entry<RecipesRoute> {
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
        entry<RecipeDetailRoute> { key ->
            RecipeDetailScreen(
                recipeId = key.recipeId,
                onBack = { navigator.goBack() },
                onEdit = { navigator.navigate(RecipeEditRoute(key.recipeId, key.tab)) },
                onCook = { servings -> navigator.navigate(CookModeRoute(key.recipeId, servings, key.tab)) },
            )
        }
        entry<RecipeEditRoute> { key ->
            RecipeEditScreen(
                recipeId = key.recipeId,
                prefill = key.prefill(),
                onBack = { navigator.goBack() },
                onSaved = { id ->
                    if (key.recipeId == null) navigator.replace(RecipeDetailRoute(id, key.tab)) else navigator.goBack()
                },
            )
        }
        entry<RecipeImportRoute> { key ->
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
        entry<RecipeBrowserRoute> { key ->
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
        entry<CookModeRoute> { key ->
            CookModeScreen(recipeId = key.recipeId, servings = key.servings, onBack = { navigator.goBack() })
        }
        entry<GroceriesRoute> { GroceriesScreen(onOpenSettings = openSettings) }
        entry<SettingsRoute> {
            SettingsScreen(
                onBack = { navigator.goBack() },
                onOpenHousehold = { navigator.navigate(HouseholdSettingsRoute(navigator.currentTab)) },
                onOpenNotifications = { navigator.navigate(NotificationSettingsRoute(navigator.currentTab)) },
                onOpenAbout = { navigator.navigate(AboutRoute(navigator.currentTab)) },
            )
        }
        entry<AboutRoute> { AboutScreen(onBack = { navigator.goBack() }) }
        entry<InsightsRoute> { InsightsScreen(onBack = { navigator.goBack() }) }
        entry<NotificationSettingsRoute> { NotificationSettingsScreen(onBack = { navigator.goBack() }) }
        entry<HouseholdSettingsRoute> { HouseholdSettingsScreen(onBack = { navigator.goBack() }) }
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
    val visibleEntries = if (currentTab == TabNavigator.START) {
        entriesByTab.getValue(TabNavigator.START)
    } else {
        entriesByTab.getValue(TabNavigator.START) + entriesByTab.getValue(currentTab)
    }

    CompositionLocalProvider(LocalJoinRequestCount provides pendingJoinRequests, LocalUnits provides session.units) {
        Scaffold(
            modifier = modifier,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                NavigationBar {
                    TopLevelDestination.entries.forEach { tab ->
                        val selected = tab == currentTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigator.selectTab(tab) },
                            icon = {
                                Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, contentDescription = null)
                            },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            },
        ) { padding ->
            val quotaDay by QuotaSignal.exhaustedOn.collectAsStateWithLifecycle()
            val reduceMotion = rememberReduceMotion()
            Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
                // DESIGN 6.7: the free daily quota was reached; it resets at midnight Pacific time.
                if (quotaDay != null && QuotaSignal.isExhausted()) QuotaBanner()
                NavDisplay(
                    entries = visibleEntries,
                    onBack = { navigator.goBack() },
                    modifier = Modifier.weight(1f),
                    transitionSpec = { sharedAxis(forward = true, reduceMotion) },
                    popTransitionSpec = { sharedAxis(forward = false, reduceMotion) },
                    predictivePopTransitionSpec = { sharedAxis(forward = false, reduceMotion) },
                )
            }
        }
    }
}

/** A failed import's title, image and link for Create manually (DESIGN 11.2). */
private fun RecipeEditRoute.prefill(): RecipeContent? =
    if (prefillTitle == null && prefillImageUrl == null && prefillSourceUrl == null) {
        null
    } else {
        RecipeContent(title = prefillTitle.orEmpty(), imageUrl = prefillImageUrl, sourceUrl = prefillSourceUrl)
    }

@Composable
private fun QuotaBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("quota_banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Text(
            stringResource(R.string.error_quota),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * Material shared axis X (DESIGN 6.4): a short slide with a fade, 250 ms; only a fade when the system's Remove
 * animations setting is on.
 */
private fun sharedAxis(forward: Boolean, reduceMotion: Boolean): ContentTransform {
    if (reduceMotion) return fadeIn(tween(120)) togetherWith fadeOut(tween(120))
    val sign = if (forward) 1 else -1
    return (slideInHorizontally(tween(250)) { sign * it / 8 } + fadeIn(tween(250, delayMillis = 50))) togetherWith
        (slideOutHorizontally(tween(250)) { -sign * it / 8 } + fadeOut(tween(150)))
}

/** Settings and its pages close when their tab is left. */
private fun isSettingsScreen(key: NavKey) =
    key is SettingsRoute || key is HouseholdSettingsRoute || key is NotificationSettingsRoute || key is AboutRoute
