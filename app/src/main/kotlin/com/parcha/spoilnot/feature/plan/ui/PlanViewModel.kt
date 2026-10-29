package com.parcha.spoilnot.feature.plan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.ui.UiMessage
import com.parcha.spoilnot.core.ui.messageRes
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.core.write.Undo
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.pantry.domain.NaturalOrder
import com.parcha.spoilnot.feature.pantry.ui.todayFlow
import com.parcha.spoilnot.feature.plan.domain.MealPlanRepository
import com.parcha.spoilnot.feature.plan.domain.MealPlanService
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.domain.MealWeek
import com.parcha.spoilnot.feature.plan.domain.PlanDay
import com.parcha.spoilnot.feature.plan.domain.PlannedMeal
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeStore
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
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Plan tab (DESIGN 5.9): one week of meals, who cooks them, and adding, changing and removing meals. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanViewModel @Inject constructor(
    session: SessionManager,
    repository: MealPlanRepository,
    households: HouseholdRepository,
    recipes: RecipeStore,
    writer: HouseholdWriter,
    private val service: MealPlanService,
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
    )

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
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

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
