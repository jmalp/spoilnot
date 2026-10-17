package com.parcha.spoilnot.feature.recipes.ui

import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.RecipeForm
import com.parcha.spoilnot.feature.recipes.domain.RecipeService
import com.parcha.spoilnot.feature.recipes.domain.RecipeStore
import com.parcha.spoilnot.feature.recipes.domain.SaveResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Create manually ([recipeId] null, optionally [prefill]ed from a failed import) or Edit (DESIGN 5.5). */
@HiltViewModel(assistedFactory = RecipeEditViewModel.Factory::class)
class RecipeEditViewModel @AssistedInject constructor(
    @Assisted private val recipeId: String?,
    @Assisted private val prefill: RecipeContent?,
    session: SessionManager,
    private val store: RecipeStore,
    private val service: RecipeService,
) : RecipeScopeViewModel(session) {
    @AssistedFactory
    interface Factory {
        fun create(recipeId: String?, prefill: RecipeContent?): RecipeEditViewModel
    }

    data class UiState(
        val loading: Boolean = true,
        /** The form's starting values; null while the recipe loads. */
        val initial: RecipeForm? = null,
        val saving: Boolean = false,
    )

    private val saving = MutableStateFlow(false)

    val state: StateFlow<UiState> = combine(store.box, saving) { box, busy ->
        when {
            recipeId == null -> UiState(false, prefill?.let { RecipeForm.from(it) } ?: RecipeForm(), busy)

            box == null -> UiState(saving = busy)

            else -> UiState(
                false,
                box.recipes.firstOrNull {
                    it.id == recipeId
                }?.let { RecipeForm.from(it.content) },
                busy,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private fun existing(): Recipe? = store.box.value?.recipes?.firstOrNull { it.id == recipeId }

    /** Saves and calls [onSaved] with the recipe's id. A new recipe is not checked for duplicates (typed in). */
    fun save(form: RecipeForm, onSaved: (String) -> Unit) {
        if (form.problems().isNotEmpty() || saving.value) return
        saving.value = true
        act { hid, actor ->
            try {
                if (recipeId == null) {
                    val result = service.save(hid, actor, form.toContent(prefill), emptyList(), saveCopy = true)
                    onSaved((result as SaveResult.Saved).recipeId)
                } else {
                    service.update(hid, actor, recipeId, form.toContent(existing()?.content))
                    onSaved(recipeId)
                }
            } finally {
                saving.value = false
            }
        }
    }
}
