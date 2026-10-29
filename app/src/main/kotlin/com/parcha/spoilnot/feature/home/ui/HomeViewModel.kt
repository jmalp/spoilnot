package com.parcha.spoilnot.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.ui.UiMessage
import com.parcha.spoilnot.core.ui.messageRes
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.Undo
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryRepository
import com.parcha.spoilnot.feature.home.domain.ActivityEvent
import com.parcha.spoilnot.feature.home.domain.ActivityRepository
import com.parcha.spoilnot.feature.home.domain.HomeDashboard
import com.parcha.spoilnot.feature.home.domain.HomeSummary
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.Member
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.insights.domain.InsightsSummary
import com.parcha.spoilnot.feature.insights.domain.StatsRepository
import com.parcha.spoilnot.feature.insights.ui.insightsFlow
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import com.parcha.spoilnot.feature.pantry.domain.PantryService
import com.parcha.spoilnot.feature.pantry.ui.todayFlow
import com.parcha.spoilnot.feature.plan.domain.MealPlanRepository
import com.parcha.spoilnot.feature.plan.domain.MealWeek
import com.parcha.spoilnot.feature.plan.domain.PlannedMeal
import com.parcha.spoilnot.feature.recipes.domain.RecipeStore
import com.parcha.spoilnot.feature.recipes.domain.UseItUp
import com.parcha.spoilnot.feature.recipes.domain.UseItUpSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Home tab (DESIGN 5.6): what needs attention, today's meals (5.9), low stock, the grocery list, this month's
 * kitchen (5.7) and recent activity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    session: SessionManager,
    pantry: PantryRepository,
    grocery: GroceryRepository,
    activity: ActivityRepository,
    stats: StatsRepository,
    recipes: RecipeStore,
    mealPlan: MealPlanRepository,
    households: HouseholdRepository,
    clock: Clock,
) : ViewModel() {
    data class UiState(
        /** Null until the pantry has loaded once: the skeleton shows (DESIGN 6.7). */
        val summary: HomeSummary? = null,
        val today: LocalDate = LocalDate.MIN,
        val offline: Boolean = false,
        /** The signed-in member, so their own events read "You". */
        val myUid: String = "",
        /** This month's counts (DESIGN 5.7); null until loaded. */
        val insights: InsightsSummary? = null,
        /** Saved recipes that use food expiring soon (DESIGN 5.6). */
        val useItUp: List<UseItUpSuggestion> = emptyList(),
        /** Today's planned meals, breakfast first (DESIGN 5.9); the card shows only when there are some. */
        val todayMeals: List<PlannedMeal> = emptyList(),
        val members: List<Member> = emptyList(),
    )

    /** The same week window as the Plan tab, so both share one full read (DESIGN 9.8). */
    private val firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    /** Only the current month: one document per open (DESIGN 9.7). */
    private val insights = insightsFlow(session, stats, clock, YearMonth::from).onStart { emit(null) }

    val state: StateFlow<UiState> = session.session
        .map { (it as? Session.InHousehold)?.let { s -> s.householdId to s.user.uid } }
        .distinctUntilChanged()
        .flatMapLatest { me ->
            if (me == null) {
                flowOf(UiState())
            } else {
                val (hid, uid) = me
                val items: Flow<List<GroceryItem>> = grocery.observeLists(hid)
                    .map { lists -> lists.firstOrNull { it.isDefault }?.id }
                    .distinctUntilChanged()
                    .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else grocery.observeItems(hid, id) }
                    .catch { emit(emptyList()) }
                    .onStart { emit(emptyList()) }
                val recent: Flow<List<ActivityEvent>> = activity.observeRecent(hid, RECENT)
                    .catch { emit(emptyList()) }
                    .onStart { emit(emptyList()) }
                val box = recipes.box.map { it?.recipes.orEmpty() }
                val home =
                    combine(
                        pantry.observe(hid).catch {
                        },
                        items,
                        recent,
                        todayFlow(clock),
                        insights,
                    ) { p, i, r, today, s ->
                        UiState(HomeDashboard.summary(p, i, r, today), today, p.fromCache, uid, s) to p
                    }
                val today = todayFlow(clock).flatMapLatest { day ->
                    val start = MealWeek.start(day, firstDay)
                    mealPlan.observe(hid, start, MealWeek.end(start))
                        .map { plan -> MealWeek.build(start, plan.meals, day).first { it.isToday }.meals }
                        .catch { emit(emptyList()) }
                        .onStart { emit(emptyList()) }
                }
                val members = households.observeMembers(hid).catch { emit(emptyList()) }.onStart { emit(emptyList()) }
                combine(home, box, today, members) { (state, p), saved, meals, people ->
                    state.copy(
                        useItUp = UseItUp.suggestions(saved, p, state.today),
                        todayMeals = meals,
                        members = people,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private companion object {
        /** DESIGN 5.6: the last 10 events. */
        const val RECENT = 10
    }
}
