package com.parcha.spoilnot.feature.plan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.ui.UiMessage
import com.parcha.spoilnot.core.ui.messageRes
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.core.write.Undo
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryList
import com.parcha.spoilnot.feature.grocery.domain.GroceryRepository
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.pantry.domain.NaturalOrder
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import com.parcha.spoilnot.feature.pantry.ui.todayFlow
import com.parcha.spoilnot.feature.plan.domain.MealGroceries
import com.parcha.spoilnot.feature.plan.domain.MealPlanGrocery
import com.parcha.spoilnot.feature.plan.domain.MealPlanRepository
import com.parcha.spoilnot.feature.plan.domain.MealPlanService
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.domain.MealWeek
import com.parcha.spoilnot.feature.plan.domain.PlanDay
import com.parcha.spoilnot.feature.plan.domain.PlannedMeal
import com.parcha.spoilnot.feature.recipes.domain.CookLine
import com.parcha.spoilnot.feature.recipes.domain.Cooking
import com.parcha.spoilnot.feature.recipes.domain.GroceryCandidate
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeService
import com.parcha.spoilnot.feature.recipes.domain.RecipeStore
import com.parcha.spoilnot.feature.recipes.domain.UseItUp
import com.parcha.spoilnot.feature.recipes.domain.UseItUpSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Plan tab (DESIGN 5.9): one week of meals, who cooks them, and adding, changing and removing meals; the week's
 * ingredients to a grocery list, Cooked, and Use it up in the picker. The pantry and grocery lists are listened to
 * only while a sheet that needs them is open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanViewModel @Inject constructor(
    session: SessionManager,
    private val repository: MealPlanRepository,
    households: HouseholdRepository,
    recipes: RecipeStore,
    pantry: PantryRepository,
    groceries: GroceryRepository,
    writer: HouseholdWriter,
    private val service: MealPlanService,
    private val recipeService: RecipeService,
    clock: Clock,
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val weekStart: LocalDate = LocalDate.MIN,
        val isThisWeek: Boolean = true,
        val days: List<PlanDay> = emptyList(),
        val members: List<Member> = emptyList(),
        val recipes: List<Recipe> = emptyList(),
        val offline: Boolean = false,
        val myUid: String = "",
        /** Recipes in this week still to cook, so "Add to grocery list" has something to add. */
        val canShop: Boolean = false,
    ) {
        fun meal(id: String): PlannedMeal? = days.firstNotNullOfOrNull { d ->
            d.slots.values.flatten().find {
                it.id ==
                    id
            }
        }
    }

    /** The phone's region decides the first day of the week (Sunday in the US, Monday in most places). */
    private val firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    /** How many weeks from this one are shown (0 = this week). */
    private val weekOffset = MutableStateFlow(0L)

    private val me = session.session.filterIsInstance<Session.InHousehold>()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<UiState> = combine(me, weekOffset, todayFlow(clock)) { me, offset, today ->
        Triple(me, MealWeek.start(today, firstDay).plusWeeks(offset), today)
    }.flatMapLatest { (me, start, today) ->
        if (me == null) return@flatMapLatest flowOf(UiState())
        combine(
            repository.observe(me.householdId, start, MealWeek.end(start)).catch { },
            households.observeMembers(me.householdId).onStart { emit(emptyList()) }.catch { emit(emptyList()) },
            recipes.box.map { box -> box?.recipes.orEmpty().sortedWith(compareBy(NaturalOrder) { it.title }) },
        ) { plan, members, box ->
            UiState(
                loading = false,
                weekStart = start,
                isThisWeek = start == MealWeek.start(today, firstDay),
                days = MealWeek.build(start, plan.meals, today),
                members = members.sortedWith(compareBy(NaturalOrder) { it.displayName }),
                recipes = box,
                offline = plan.fromCache,
                myUid = me.user.uid,
            ).let { it.copy(canShop = MealPlanGrocery.toShop(it.days, it.recipes).isNotEmpty()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /** One pantry listener shared by the sheets that need it, running only while one of them is open. */
    private val pantryData: Flow<PantryData> = me
        .map { it?.householdId }
        .distinctUntilChanged()
        // On an error the sheets still open, without pantry hints.
        .flatMapLatest { hid -> if (hid == null) emptyFlow() else pantry.observe(hid).catch { emit(EMPTY_PANTRY) } }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /**
     * "Use it up" at the top of the recipe picker (DESIGN 5.9): saved recipes using food that expires soon. Null until
     * the pantry has loaded, so the sheet opens at its full height.
     */
    val useItUp: StateFlow<List<UseItUpSuggestion>?> =
        combine(pantryData, recipes.box, todayFlow(clock)) { p, box, today ->
            UseItUp.suggestions(box?.recipes.orEmpty(), p, today)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The Cooked sheet for a recipe meal: the pantry products it uses, at the meal's servings. */
    data class CookState(val meal: PlannedMeal, val recipe: Recipe, val lines: List<CookLine>, val pantry: PantryData)

    private val cookingId = MutableStateFlow<String?>(null)

    val cook: StateFlow<CookState?> = cookingId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            combine(state, pantryData) { s, p ->
                val meal = s.meal(id)
                val recipe = meal?.recipeId?.let { r -> s.recipes.firstOrNull { it.id == r } }
                if (meal == null || recipe == null) {
                    null
                } else {
                    CookState(meal, recipe, Cooking.lines(recipe, p, MealPlanGrocery.factor(meal, recipe)), p)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The week's ingredients for a grocery list (DESIGN 5.9), like Add to grocery list on a recipe (5.5). */
    data class WeekGroceryState(
        val lists: List<GroceryList> = emptyList(),
        val listId: String? = null,
        val sections: List<MealGroceries> = emptyList(),
        val pantry: PantryData = PantryData(emptyList(), emptyList(), fromCache = true),
        val loading: Boolean = true,
    )

    private val chosenList = MutableStateFlow<String?>(null)

    val weekGrocery: StateFlow<WeekGroceryState> = me.map { it?.householdId }.distinctUntilChanged()
        .flatMapLatest { hid ->
            if (hid == null) return@flatMapLatest flowOf(WeekGroceryState())
            val lists = groceries.observeLists(hid).catch { emit(emptyList()) }
            val listId = combine(lists, chosenList) { all, chosen ->
                all.firstOrNull { it.id == chosen }?.id ?: all.firstOrNull { it.isDefault }?.id ?: all.firstOrNull()?.id
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
            combine(state, lists, listId, items, pantryData) { s, all, id, listItems, p ->
                WeekGroceryState(
                    lists = all,
                    listId = id,
                    sections = MealPlanGrocery.sections(MealPlanGrocery.toShop(s.days, s.recipes), p, listItems),
                    pantry = p,
                    loading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekGroceryState())

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    init {
        writer.errors.onEach { _messages.send(UiMessage(it.messageRes())) }.launchIn(viewModelScope)
    }

    fun previousWeek() = weekOffset.update { it - 1 }

    fun nextWeek() = weekOffset.update { it + 1 }

    fun thisWeek() = weekOffset.update { 0 }

    fun addRecipe(date: LocalDate, slot: MealSlot, recipe: Recipe, cooks: List<String>) = act { hid, actor ->
        service.addRecipe(hid, actor, date, slot, recipe, cooks, slotMeals(date, slot))
    }

    fun addOther(date: LocalDate, slot: MealSlot, title: String, cooks: List<String>) = act { hid, actor ->
        service.addOther(hid, actor, date, slot, title, cooks, slotMeals(date, slot))
    }

    fun update(meal: PlannedMeal, servings: Int?, cooks: List<String>, note: String?) = act { hid, actor ->
        service.update(hid, actor, meal, servings, cooks, note)
    }

    fun remove(meal: PlannedMeal) = act { hid, actor ->
        _messages.send(UiMessage(R.string.plan_removed, meal.title, service.remove(hid, actor, meal)))
    }

    fun undo(undo: Undo) = act { hid, actor -> service.undo(hid, actor, undo) }

    /**
     * Cooked: a recipe meal opens the sheet of what it used; a meal without a saved recipe is marked cooked at
     * once, with Undo.
     */
    fun cook(meal: PlannedMeal) {
        val recipe = meal.recipeId?.let { id -> state.value.recipes.firstOrNull { it.id == id } }
        if (recipe != null) {
            cookingId.value = meal.id
        } else {
            act { hid, actor ->
                val undo = service.cooked(hid, actor, meal, null, emptyMap(), EMPTY_PANTRY)
                _messages.send(UiMessage(R.string.plan_cooked_done, meal.title, undo))
            }
        }
    }

    fun closeCookSheet() {
        cookingId.value = null
    }

    /** From the Cooked sheet: takes [amounts] off the pantry, marks the meal cooked and logs it, with Undo. */
    fun cooked(amounts: Map<String, Double>) {
        val c = cook.value ?: return
        cookingId.value = null
        act { hid, actor ->
            val undo = service.cooked(hid, actor, c.meal, c.recipe, amounts, c.pantry)
            _messages.send(UiMessage(R.string.plan_cooked_done, c.meal.title, undo))
        }
    }

    fun notCooked(meal: PlannedMeal) = act { hid, actor -> service.notCooked(hid, actor, meal) }

    /** Moves [meal] to another day or slot of its week, with Undo. */
    fun move(meal: PlannedMeal, date: LocalDate, slot: MealSlot) = act { hid, actor ->
        val undo = service.move(hid, actor, meal, date, slot, slotMeals(date, slot)) ?: return@act
        _messages.send(UiMessage(R.string.plan_moved, meal.title, undo))
    }

    /**
     * Copy last week into the week shown. Last week is read from the server when it answers within a few seconds,
     * otherwise from the phone's copy.
     */
    fun copyLastWeek() {
        val shown = state.value
        if (shown.loading) return
        act { hid, actor ->
            val from = shown.weekStart.minusWeeks(1)
            val week = repository.observe(hid, from, MealWeek.end(from))
            val previous = (withTimeoutOrNull(COPY_WAIT_MS) { week.first { !it.fromCache } } ?: week.first()).meals
            val existing = shown.days.flatMap { it.meals }
            val copied = service.copyWeek(hid, actor, previous, from, shown.weekStart, existing)
            if (copied == null) {
                _messages.send(
                    UiMessage(if (previous.isEmpty()) R.string.plan_copy_none else R.string.plan_copy_already),
                )
            } else {
                _messages.send(UiMessage(R.string.plan_copied, listOf(copied.first.toString()), copied.second))
            }
        }
    }

    /** Removes every meal of the week shown, with one Undo. */
    fun clearWeek() = act { hid, actor ->
        val undo = service.clear(hid, actor, state.value.days.flatMap { it.meals }) ?: return@act
        _messages.send(UiMessage(R.string.plan_cleared, null, undo))
    }

    fun chooseList(listId: String) {
        chosenList.value = listId
    }

    /** Adds the chosen lines with the source Meal plan; [from] names the plan in Recent activity. */
    fun addWeekToGrocery(chosen: List<GroceryCandidate>, from: String) {
        val g = weekGrocery.value
        val list = g.lists.firstOrNull { it.id == g.listId } ?: return
        act { hid, actor ->
            val count = recipeService.addItems(
                hid,
                actor,
                list.id,
                chosen,
                g.pantry,
                list.name,
                ItemSource.MealPlan,
                from,
            )
            if (count > 0) _messages.send(UiMessage(R.string.recipe_added_to_list, list.name))
        }
    }

    private fun slotMeals(date: LocalDate, slot: MealSlot): List<PlannedMeal> =
        state.value.days.firstOrNull { it.date == date }?.slots?.get(slot).orEmpty()

    private fun act(block: suspend (String, Actor) -> Unit) {
        val me = me.value ?: return
        viewModelScope.launch {
            try {
                block(me.householdId, Actor(me.user.uid, me.displayName))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(UiMessage(e.messageRes()))
            }
        }
    }
}

/** How long Copy last week waits for the server before using the phone's copy. */
private const val COPY_WAIT_MS = 5_000L

private val EMPTY_PANTRY = PantryData(emptyList(), emptyList(), fromCache = false)
