package com.parcha.spoilnot.feature.recipes.ui

import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.recipes.domain.ExtractionMethod
import com.parcha.spoilnot.feature.recipes.domain.ImportResult
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.RecipeImportService
import com.parcha.spoilnot.feature.recipes.domain.RecipeService
import com.parcha.spoilnot.feature.recipes.domain.RecipeStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Paste link and the share sheet: fetch, extract and preview (DESIGN 11.2). */
@HiltViewModel(assistedFactory = RecipeImportViewModel.Factory::class)
class RecipeImportViewModel @AssistedInject constructor(
    @Assisted private val url: String?,
    session: SessionManager,
    store: RecipeStore,
    service: RecipeService,
    private val importer: RecipeImportService,
) : SavingRecipeViewModel(session, store, service) {
    @AssistedFactory
    interface Factory {
        fun create(url: String?): RecipeImportViewModel
    }

    sealed interface UiState {
        data object Loading : UiState

        data class Preview(val content: RecipeContent, val method: ExtractionMethod) : UiState

        /** Nothing usable in the fetched page: the screen moves on to the built-in browser. */
        data class TryBrowser(val url: String, val prefill: RecipeContent) : UiState

        data object NoLink : UiState

        data object Offline : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Keeps the shared listener running so the duplicate check has the box.
        store.box.launchIn(viewModelScope)
        load()
    }

    fun retry() = load()

    private fun load() {
        if (url == null) {
            _state.value = UiState.NoLink
            return
        }
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = importer.fromUrl(url)) {
                is ImportResult.Found -> UiState.Preview(result.content, result.method)
                is ImportResult.TryBrowser -> UiState.TryBrowser(result.url, result.prefill)
                ImportResult.InvalidUrl -> UiState.NoLink
                ImportResult.Offline -> UiState.Offline
            }
        }
    }
}
