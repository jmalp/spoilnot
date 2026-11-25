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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * Runs the [RestockEngine] on this phone while the user is in a household (DESIGN 9.6): on every change of the
 * products and stock, debounced by 300 ms, once both have been read from the server ([PantryData.serverRead]; a
 * warm cache counts, so it works offline). A change made on this phone is acted on at once; one from another phone
 * after [RestockEngine.OTHER_PHONE_DELAY_MS], so usually only one phone writes. A failed listener listens again with
 * a backoff.
 *
 * After v1 it reads the local cache only ([PantryRepository.observeCached], [GroceryRepository.observeCachedLists]):
 * it reacts to what the screens' server listeners and this phone's own writes put there, and holds no server
 * listener of its own, so nothing is read again when the app comes back after a long time (EFF-01). It is started
 * when an activity starts, not when the process does ([start]).
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

    @Volatile private var lastOwnChangeAt: Long? = null

    private data class Input(
        val householdId: String,
        val actor: Actor,
        val data: PantryData,
        val defaultListId: String?,
    )

    /** Starts the engine once; later calls do nothing. */
    fun start() {
        if (job != null) return
        job = scope.launch {
            launch {
                writer.localPlans.collect { if (RestockEngine.movesStock(it)) lastOwnChangeAt = clock.millis() }
            }
            inputs(session).debounce(DEBOUNCE_MS).collectLatest(::evaluate)
        }
    }

    private fun inputs(session: SessionManager): Flow<Input> = session.session
        .map { s -> (s as? Session.InHousehold)?.let { it.householdId to Actor(it.user.uid, it.displayName) } }
        .distinctUntilChanged()
        .flatMapLatest { context ->
            if (context == null) {
                emptyFlow()
            } else {
                val (hid, actor) = context
                val defaultList = grocery.observeCachedLists(hid).map { lists ->
                    lists.firstOrNull { it.isDefault }?.id
                }.distinctUntilChanged()
                combine(pantry.observeCached(hid), defaultList) { data, listId -> Input(hid, actor, data, listId) }
                    // A refused or failed listener (quota, a membership the server does not have yet) listens again
                    // later instead of stopping the engine until the app restarts.
                    .retryWhen { cause, attempt ->
                        if (cause is CancellationException) return@retryWhen false
                        delay(backoffMillis(attempt))
                        true
                    }
            }
        }

    private suspend fun evaluate(input: Input) {
        try {
            // A first sync can deliver the products before the stock: every staple would look empty (DESIGN 9.6).
            if (!input.data.serverRead) return
            val draft = RestockEngine.plan(input.data, input.defaultListId, cachedLowStockIds = emptySet())
            if (draft.isEmpty()) return
            // Collected by collectLatest: a newer snapshot during the wait (another phone's write) cancels this one.
            delay(RestockEngine.delayMillis(lastOwnChangeAt, clock.millis()))
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

    /** 1, 2, 4 … 32 seconds, as SessionManager retries its listeners. */
    private fun backoffMillis(attempt: Long): Long = 1_000L shl attempt.coerceAtMost(5).toInt()

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val REPEAT_GUARD_MS = 30_000L
    }
}
