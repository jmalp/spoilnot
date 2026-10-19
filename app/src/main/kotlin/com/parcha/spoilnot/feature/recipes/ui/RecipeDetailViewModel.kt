package com.parcha.spoilnot.feature.recipes.ui

import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.ui.UiMessage
import com.parcha.spoilnot.core.write.Undo
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryList
import com.parcha.spoilnot.feature.grocery.domain.GroceryRepository
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import com.parcha.spoilnot.feature.pantry.domain.PantryService
import com.parcha.spoilnot.feature.recipes.domain.CookLine
import com.parcha.spoilnot.feature.recipes.domain.Cooking
import com.parcha.spoilnot.feature.recipes.domain.GroceryCandidate
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeGrocery
import com.parcha.spoilnot.feature.recipes.domain.RecipeService
import com.parcha.spoilnot.feature.recipes.domain.RecipeStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Recipe detail (DESIGN 5.5) and its Add to grocery list sheet. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = RecipeDetailViewModel.Factory::class)
class RecipeDetailViewModel @AssistedInject constructor(
    @Assisted private val recipeId: String,
    session: SessionManager,
    store: RecipeStore,
    pantry: PantryRepository,
    groceries: GroceryRepository,
    private val service: RecipeService,
    private val pantryService: PantryService,
    private val events: RecipeEvents,
) : RecipeScopeViewModel(session) {
    @AssistedFactory
    interface Factory {
        fun create(recipeId: String): RecipeDetailViewModel
    }

    data class UiState(val loading: Boolean = true, val recipe: Recipe? = null)

    val state: StateFlow<UiState> = store.box
        .map { box -> if (box == null) UiState() else UiState(false, box.recipes.firstOrNull { it.id == recipeId }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    data class GroceryState(
        val lists: List<GroceryList> = emptyList(),
        val listId: String? = null,
        val candidates: List<GroceryCandidate> = emptyList(),
        val pantry: PantryData = PantryData(emptyList(), emptyList(), fromCache = true),
        val loading: Boolean = true,
    )

    /** Listeners for the sheet run only while it is open. */
    private val sheetOpen = MutableStateFlow(false)
    private val chosenList = MutableStateFlow<String?>(null)

    private val householdId = session.session.map { (it as? Session.InHousehold)?.householdId }.distinctUntilChanged()

    val grocery: StateFlow<GroceryState> = combine(householdId, sheetOpen) { hid, open -> hid.takeIf { open } }
        .flatMapLatest { hid ->
            if (hid == null) {
                flowOf(GroceryState())
            } else {
                val lists = groceries.observeLists(hid).catch { emit(emptyList()) }
                val listId = combine(lists, chosenList) { all, chosen ->
                    all.firstOrNull { it.id == chosen }?.id ?: all.firstOrNull { it.isDefault }?.id
                        ?: all.firstOrNull()?.id
                }.distinctUntilChanged()
                val items = listId.flatMapLatest { id ->
                    if (id ==
                        null
                    ) {
                        flowOf(emptyList())
                    } else {
                        groceries.observeItems(hid, id).catch { emit(emptyList<GroceryItem>()) }
                    }
                }
                combine(state, lists, listId, items, pantry.observe(hid).catch { }) { s, all, id, listItems, p ->
                    GroceryState(
                        lists = all,
                        listId = id,
                        candidates = s.recipe?.let { RecipeGrocery.candidates(it, p, listItems) }.orEmpty(),
                        pantry = p,
                        loading = false,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroceryState())

    /** The "I cooked this" sheet (DESIGN 5.5): the pantry products this recipe uses, at the chosen servings. */
    data class CookState(
        val lines: List<CookLine> = emptyList(),
        val pantry: PantryData = PantryData(emptyList(), emptyList(), fromCache = true),
        val loading: Boolean = true,
    )

    private val cookFactor = MutableStateFlow<Double?>(null)

    val cook: StateFlow<CookState> = combine(householdId, cookFactor) { hid, factor -> factor?.let { hid to it } }
        .flatMapLatest { open ->
            val (hid, factor) = open ?: return@flatMapLatest flowOf(CookState())
            if (hid == null) return@flatMapLatest flowOf(CookState())
            combine(state, pantry.observe(hid).catch { }) { s, p ->
                CookState(s.recipe?.let { Cooking.lines(it, p, factor) }.orEmpty(), p, loading = false)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CookState())

    /** Opens the sheet at [factor] times the recipe's servings, or closes it with null. */
    fun openCookSheet(factor: Double?) {
        cookFactor.value = factor
    }

    /** Takes [amounts] (product id to amount) off the pantry and logs "cooked", with Undo for all of it. */
    fun cooked(amounts: Map<String, Double>) {
        val recipe = state.value.recipe ?: return
        val pantryData = cook.value.pantry
        act { hid, actor ->
            val (event, removeEvent) = service.cookedEvent(recipe)
            val undo = pantryService.use(hid, actor, amounts, pantryData, listOf(event), listOf(removeEvent))
            cookFactor.value = null
            show(UiMessage(R.string.recipe_cooked_done, recipe.title, undo))
        }
    }

    fun undo(undo: Undo) = act { hid, actor -> pantryService.undo(hid, actor, undo) }

    fun openGrocerySheet(open: Boolean) {
        sheetOpen.value = open
    }

    fun chooseList(listId: String) {
        chosenList.value = listId
    }

    fun toggleFavorite() {
        val recipe = state.value.recipe ?: return
        act { hid, actor -> service.setFavorite(hid, actor, recipe, !recipe.favorite) }
    }

    /** Deletes and leaves the screen; the recipe box offers Undo (DESIGN 5.5). */
    fun delete(onDeleted: () -> Unit) {
        val recipe = state.value.recipe ?: return
        act { hid, actor ->
            val undo = service.delete(hid, actor, recipe)
            onDeleted()
            events.send(UiMessage(R.string.recipe_deleted, recipe.title, undo))
        }
    }

    fun addToGrocery(chosen: List<GroceryCandidate>) {
        val recipe = state.value.recipe ?: return
        val g = grocery.value
        val list = g.lists.firstOrNull { it.id == g.listId } ?: return
        act { hid, actor ->
            val count = service.addToGrocery(hid, actor, list.id, recipe, chosen, g.pantry, list.name)
            sheetOpen.value = false
            if (count > 0) show(UiMessage(R.string.recipe_added_to_list, list.name))
        }
    }
}
