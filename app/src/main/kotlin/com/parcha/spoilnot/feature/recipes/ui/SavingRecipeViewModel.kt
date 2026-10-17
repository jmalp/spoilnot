package com.parcha.spoilnot.feature.recipes.ui

import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.RecipeService
import com.parcha.spoilnot.feature.recipes.domain.RecipeStore
import com.parcha.spoilnot.feature.recipes.domain.SaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Saving an imported recipe, with "Already saved" when the link is in the box (DESIGN 5.5). */
abstract class SavingRecipeViewModel(
    session: SessionManager,
    private val store: RecipeStore,
    private val service: RecipeService,
) : RecipeScopeViewModel(session) {
    sealed interface SaveState {
        data object Idle : SaveState

        data object Saving : SaveState

        data class AlreadySaved(val existing: Recipe, val content: RecipeContent) : SaveState

        data class Saved(val recipeId: String) : SaveState
    }

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun save(content: RecipeContent, saveCopy: Boolean = false) {
        if (_saveState.value == SaveState.Saving) return
        _saveState.value = SaveState.Saving
        act { hid, actor ->
            try {
                val existing = store.box.value?.recipes.orEmpty()
                _saveState.value = when (val result = service.save(hid, actor, content, existing, saveCopy)) {
                    is SaveResult.Saved -> SaveState.Saved(result.recipeId)
                    is SaveResult.AlreadySaved -> SaveState.AlreadySaved(result.existing, content)
                }
            } finally {
                if (_saveState.value == SaveState.Saving) _saveState.value = SaveState.Idle
            }
        }
    }

    fun dismissSaveState() {
        _saveState.value = SaveState.Idle
    }
}
