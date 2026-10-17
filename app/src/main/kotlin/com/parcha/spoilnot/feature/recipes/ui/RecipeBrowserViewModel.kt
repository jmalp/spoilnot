package com.parcha.spoilnot.feature.recipes.ui

import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.recipes.domain.BrowserScript
import com.parcha.spoilnot.feature.recipes.domain.Extraction
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.RecipeExtractor
import com.parcha.spoilnot.feature.recipes.domain.RecipeService
import com.parcha.spoilnot.feature.recipes.domain.RecipeStore
import com.parcha.spoilnot.feature.recipes.domain.SourceUrl
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The built-in browser (DESIGN 5.5, 11.2): checks every loaded page for a recipe. */
@HiltViewModel(assistedFactory = RecipeBrowserViewModel.Factory::class)
class RecipeBrowserViewModel @AssistedInject constructor(
    @Assisted startUrl: String?,
    @Assisted private val fallback: Boolean,
    @Assisted private val prefill: RecipeContent,
    session: SessionManager,
    store: RecipeStore,
    service: RecipeService,
) : SavingRecipeViewModel(session, store, service) {
    @AssistedFactory
    interface Factory {
        fun create(startUrl: String?, fallback: Boolean, prefill: RecipeContent): RecipeBrowserViewModel
    }

    data class UiState(
        /** The page the web view shows; survives leaving and returning to the screen. */
        val url: String? = null,
        val found: Extraction.Found? = null,
        /** Opened because a fetch found nothing, and the live page has nothing either: offer Create manually. */
        val notFound: RecipeContent? = null,
        val fallback: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState(url = startUrl?.let { SourceUrl.canonical(it) }, fallback = fallback))
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Checks of the current page; a new page cancels them. */
    private val extractions = mutableListOf<Job>()

    init {
        store.box.launchIn(viewModelScope)
    }

    /** A new page started loading: whatever was found belongs to the old one. */
    fun onNavigate(url: String) {
        extractions.forEach { it.cancel() }
        extractions.clear()
        _state.update { it.copy(url = url, found = null, notFound = null) }
    }

    /**
     * The script's result for the current page. [final] is the last check after the page settled; only then may a
     * page without a recipe count as not found.
     */
    fun onPage(result: String?, final: Boolean) {
        extractions += viewModelScope.launch {
            val page = BrowserScript.parse(result) ?: return@launch
            val extracted = withContext(Dispatchers.Default) { RecipeExtractor.fromPage(page) }
            _state.update { s ->
                when {
                    extracted is Extraction.Found -> s.copy(found = extracted, notFound = null)
                    s.found != null -> s
                    final && fallback && extracted is Extraction.NotFound -> s.copy(notFound = merge(extracted.prefill))
                    else -> s
                }
            }
        }
    }

    fun saveFound() {
        _state.value.found?.let { save(it.content) }
    }

    /** What the fetched page offered, filled in from the live page where it had more. */
    private fun merge(live: RecipeContent) = live.copy(
        title = prefill.title.ifEmpty { live.title },
        imageUrl = prefill.imageUrl ?: live.imageUrl,
        sourceUrl = live.sourceUrl ?: prefill.sourceUrl,
    )
}
