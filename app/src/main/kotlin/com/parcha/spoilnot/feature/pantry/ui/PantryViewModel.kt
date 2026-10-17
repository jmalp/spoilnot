package com.parcha.spoilnot.feature.pantry.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.ui.messageRes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.pantry.domain.Actor
import com.parcha.spoilnot.feature.pantry.domain.BatchEdit
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFilter
import com.parcha.spoilnot.feature.pantry.domain.PantryGroup
import com.parcha.spoilnot.feature.pantry.domain.PantryGrouping
import com.parcha.spoilnot.feature.pantry.domain.PantryList
import com.parcha.spoilnot.feature.pantry.domain.PantryQuery
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import com.parcha.spoilnot.feature.pantry.domain.PantryService
import com.parcha.spoilnot.feature.pantry.domain.PantrySort
import com.parcha.spoilnot.feature.pantry.domain.PantryWriteErrors
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import com.parcha.spoilnot.feature.pantry.domain.Undo
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A message for the snackbar, with Undo when the action can be undone. */
data class PantryMessage(@param:StringRes val text: Int, val arg: String? = null, val undo: Undo? = null)

/** "Today" in the user's calendar, re-checked every minute so statuses roll over at midnight. */
fun todayFlow(clock: Clock): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now(clock))
        delay(60_000)
    }
}.distinctUntilChanged()

/** The pantry tab (DESIGN 5.2). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PantryViewModel @Inject constructor(
    session: SessionManager,
    repository: PantryRepository,
    writeErrors: PantryWriteErrors,
    private val service: PantryService,
    clock: Clock,
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val groups: List<PantryGroup> = emptyList(),
        /** Nothing in the pantry at all (not just filtered out). */
        val pantryEmpty: Boolean = false,
        val offline: Boolean = false,
        val query: PantryQuery = PantryQuery(),
        val today: LocalDate = LocalDate.MIN,
        val data: PantryData = PantryData(emptyList(), emptyList(), fromCache = true),
    )

    private val me = session.session.filterIsInstance<Session.InHousehold>()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val query = MutableStateFlow(PantryQuery())

    private val data: StateFlow<PantryData?> = me
        .map { it?.householdId }
        .distinctUntilChanged()
        .flatMapLatest { hid -> if (hid == null) flow { } else repository.observe(hid) }
        .catch { }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<UiState> = combine(data, query, todayFlow(clock)) { data, query, today ->
        if (data == null) {
            UiState(query = query, today = today)
        } else {
            UiState(
                loading = false,
                groups = PantryList.build(data.products, data.batches, query, today),
                pantryEmpty = PantryList.rows(data.products, data.batches, today).isEmpty(),
                offline = data.fromCache,
                query = query,
                today = today,
                data = data,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private val _messages = Channel<PantryMessage>(Channel.BUFFERED)
    val messages: Flow<PantryMessage> = _messages.receiveAsFlow()

    init {
        // Invariant 1: delete batches that reached 0, whoever notices first.
        data.onEach { d ->
            if (d !=
                null
            ) {
                context()?.let { (hid, actor) -> runCatching { service.deleteEmptyBatches(hid, actor, d) } }
            }
        }
            .launchIn(viewModelScope)
        writeErrors.errors.onEach { _messages.send(PantryMessage(it.writeMessage())) }.launchIn(viewModelScope)
    }

    fun setFilter(filter: PantryFilter) = query.update { it.copy(filter = filter) }

    fun setSort(sort: PantrySort) = query.update { it.copy(sort = sort) }

    fun setGrouping(grouping: PantryGrouping) = query.update { it.copy(grouping = grouping) }

    fun setSearch(text: String) = query.update { it.copy(search = text) }

    /** Saves the Add item sheet; returns false when it could not be saved. */
    fun add(draft: StockDraft, onDone: () -> Unit) = act { hid, actor ->
        val result = service.add(hid, actor, draft, currentData())
        onDone()
        val text = if (result.merged) R.string.added_merged_message else R.string.added_message
        _messages.send(PantryMessage(text, draft.name.trim()))
    }

    fun adjust(batch: StockBatch, delta: Double) = act { hid, actor ->
        service.adjust(hid, actor, batch, delta)?.let {
            _messages.send(PantryMessage(R.string.undo_used_up, batch.productName, it))
        }
    }

    fun usedUp(batch: StockBatch) = act { hid, actor ->
        _messages.send(PantryMessage(R.string.undo_used_up, batch.productName, service.usedUp(hid, actor, batch)))
    }

    fun throwAway(batch: StockBatch, reason: DiscardReason) = act { hid, actor ->
        _messages.send(
            PantryMessage(R.string.undo_thrown_away, batch.productName, service.throwAway(hid, actor, batch, reason)),
        )
    }

    fun edit(batch: StockBatch, edit: BatchEdit) = act { hid, actor ->
        service.edit(hid, actor, batch, edit)?.let {
            _messages.send(PantryMessage(R.string.undo_used_up, batch.productName, it))
        }
    }

    fun move(batch: StockBatch, location: Location) = act { hid, actor -> service.move(hid, actor, batch, location) }

    fun undo(undo: Undo) = act { hid, actor -> service.undo(hid, actor, undo) }

    private fun currentData(): PantryData = data.value ?: throw AppError.Offline()

    private fun context(): Pair<String, Actor>? = me.value?.let { it.householdId to Actor(it.user.uid, it.displayName) }

    private fun act(block: suspend (String, Actor) -> Unit) {
        val (hid, actor) = context() ?: return
        viewModelScope.launch {
            try {
                block(hid, actor)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(PantryMessage(e.messageRes()))
            }
        }
    }
}

/** Server rejections of pantry writes; a vanished item means someone else deleted it (DESIGN 9.4). */
@StringRes
internal fun AppError.writeMessage(): Int = messageRes()
