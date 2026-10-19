package com.parcha.spoilnot.feature.insights.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.insights.domain.Insights
import com.parcha.spoilnot.feature.insights.domain.InsightsSummary
import com.parcha.spoilnot.feature.insights.domain.StatsRepository
import com.parcha.spoilnot.feature.pantry.ui.todayFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The household's [InsightsSummary] from [from] on, recomputed every day. Home asks for the current month only (one
 * document per open); the Insights screen for the last six months.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun insightsFlow(
    session: SessionManager,
    stats: StatsRepository,
    clock: Clock,
    from: (LocalDate) -> YearMonth,
): Flow<InsightsSummary?> = session.session
    .map { (it as? Session.InHousehold)?.householdId }
    .distinctUntilChanged()
    .flatMapLatest { hid ->
        if (hid == null) {
            flowOf(null)
        } else {
            val today = todayFlow(clock)
            today.map(from).distinctUntilChanged()
                .flatMapLatest { first -> stats.observeMonths(hid, first) }
                .combine(today) { months, day -> Insights.summary(months, day) }
                .catch { emit(Insights.summary(emptyList(), LocalDate.now(clock))) }
        }
    }

/** The Insights screen (DESIGN 5.7). */
@HiltViewModel
class InsightsViewModel @Inject constructor(session: SessionManager, stats: StatsRepository, clock: Clock) :
    ViewModel() {
    /** Null while loading. */
    val summary: StateFlow<InsightsSummary?> = insightsFlow(session, stats, clock, Insights::firstMonth)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
