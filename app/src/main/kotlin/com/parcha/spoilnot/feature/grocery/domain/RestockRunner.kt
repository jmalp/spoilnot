package com.parcha.spoilnot.feature.grocery.domain

import com.parcha.spoilnot.core.coroutines.ApplicationScope
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Runs the [RestockEngine] on this phone while the user is in a household (DESIGN 9.6): on every change of the
 * products and stock (cache snapshots included, so it works offline), debounced by 300 ms.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Singleton
class RestockRunner @Inject constructor(
    private val session: SessionManager,
    private val pantry: PantryRepository,
    private val grocery: GroceryRepository,
    private val writer: HouseholdWriter,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val clock: java.time.Clock,
) {
    private var job: Job? = null
    private var lastPlan: Any? = null
    private var lastPlanAt = 0L

    private data class Input(
        val householdId: String,
        val actor: Actor,
        val data: PantryData,
        val defaultListId: String?,
    )

    fun start() {
        if (job != null) return
        job = scope.launch { inputs(session).debounce(DEBOUNCE_MS).collectLatest(::evaluate) }
    }

    private fun inputs(session: SessionManager): Flow<Input> = session.session
        .map { s -> (s as? Session.InHousehold)?.let { it.householdId to Actor(it.user.uid, it.displayName) } }
        .distinctUntilChanged()
        .flatMapLatest { context ->
            if (context == null) {
                emptyFlow()
            } else {
                val (hid, actor) = context
                val defaultList = grocery.observeLists(hid).map { lists ->
                    lists.firstOrNull { it.isDefault }?.id
                }.distinctUntilChanged()
                combine(pantry.observe(hid), defaultList) { data, listId -> Input(hid, actor, data, listId) }
                    .catch { }
            }
        }

    private suspend fun evaluate(input: Input) {
        try {
            val draft = RestockEngine.plan(input.data, input.defaultListId, cachedLowStockIds = emptySet())
            if (draft.isEmpty()) return
            // createdAt/createdBy are written only for items not already in the local cache (DESIGN 9.6).
            val cached = draft.filterIsInstance<GroceryChange.UpsertLowStock>()
                .map { GroceryRules.lowStockItemId(it.productId) }
                .filter { grocery.isCached(input.householdId, input.defaultListId!!, it) }
                .toSet()
            val plan = RestockEngine.plan(input.data, input.defaultListId, cached)
            // A plan the server rejected comes back after the local rollback; do not resend it in a tight loop.
            val now = clock.millis()
            if (plan == lastPlan && now - lastPlanAt < REPEAT_GUARD_MS) return
            lastPlan = plan
            lastPlanAt = now
            writer.apply(input.householdId, input.actor, plan)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The next snapshot triggers another evaluation.
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val REPEAT_GUARD_MS = 30_000L
    }
}
