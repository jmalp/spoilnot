package com.parcha.spoilnot.feature.recipes.ui

import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.core.write.Undo
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeBox
import com.parcha.spoilnot.feature.recipes.domain.RecipeFilter
import com.parcha.spoilnot.feature.recipes.domain.RecipeQuery
import com.parcha.spoilnot.feature.recipes.domain.RecipeService
import com.parcha.spoilnot.feature.recipes.domain.RecipeSort
import com.parcha.spoilnot.feature.recipes.domain.RecipeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** The recipe box (DESIGN 5.5). */
@HiltViewModel
class RecipesViewModel @Inject constructor(
    session: SessionManager,
    store: RecipeStore,
    private val service: RecipeService,
    events: RecipeEvents,
) : RecipeScopeViewModel(session) {
    data class UiState(
        val loading: Boolean = true,
        val recipes: List<Recipe> = emptyList(),
        val tags: List<String> = emptyList(),
        /** Nothing saved at all (not just filtered out). */
        val boxEmpty: Boolean = false,
        val offline: Boolean = false,
        val query: RecipeQuery = RecipeQuery(),
    )

    private val query = MutableStateFlow(RecipeQuery())

    val state: StateFlow<UiState> = combine(store.box, query) { box, query ->
        if (box == null) {
            UiState(query = query)
        } else {
            val tags = RecipeBox.tags(box.recipes)
            // A tag chip whose last recipe was deleted falls back to All.
            val filter = query.filter
            val stale = filter is RecipeFilter.Tag && tags.none { NameKey.of(it) == NameKey.of(filter.tag) }
            val q = if (stale) {
                query.copy(filter = RecipeFilter.All)
            } else {
                query
            }
            UiState(
                loading = false,
                recipes = RecipeBox.list(box.recipes, q),
                tags = tags,
                boxEmpty = box.recipes.isEmpty(),
                offline = box.fromCache,
                query = q,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        events.messages.onEach { show(it) }.launchIn(viewModelScope)
    }

    fun setSearch(text: String) = query.update { it.copy(search = text) }

    fun setFilter(filter: RecipeFilter) = query.update { it.copy(filter = filter) }

    fun setSort(sort: RecipeSort) = query.update { it.copy(sort = sort) }

    fun toggleFavorite(recipe: Recipe) = act { hid, actor -> service.setFavorite(hid, actor, recipe, !recipe.favorite) }

    fun undo(undo: Undo) = act { hid, actor -> service.undo(hid, actor, undo) }
}
