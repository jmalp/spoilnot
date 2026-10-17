package com.parcha.spoilnot.feature.recipes.domain

import com.parcha.spoilnot.core.coroutines.ApplicationScope
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One listener on the recipe box shared by every recipe screen. It starts on the first visit to the Recipes tab
 * (DESIGN 9.1) and stops a few seconds after the last recipe screen goes away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RecipeStore @Inject constructor(
    session: SessionManager,
    repository: RecipeRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    val box: StateFlow<RecipeBoxData?> = session.session
        .map { (it as? Session.InHousehold)?.householdId }
        .distinctUntilChanged()
        .flatMapLatest { hid -> if (hid == null) flowOf(null) else repository.observe(hid).catch { } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
}
