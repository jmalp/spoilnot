package com.parcha.spoilnot.feature.pantry.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.navigation.DeepLink
import com.parcha.spoilnot.core.navigation.DeepLinkRouter
import com.parcha.spoilnot.core.prefs.AppPreferences
import com.parcha.spoilnot.core.ui.UiMessage
import com.parcha.spoilnot.core.ui.messageRes
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.core.write.Undo
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.EditWatch
import com.parcha.spoilnot.feature.pantry.domain.ItemEdit
import com.parcha.spoilnot.feature.pantry.domain.ItemEditBase
import com.parcha.spoilnot.feature.pantry.domain.ItemEditWatch
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFilter
import com.parcha.spoilnot.feature.pantry.domain.PantryGrouping
import com.parcha.spoilnot.feature.pantry.domain.PantryItemGroup
import com.parcha.spoilnot.feature.pantry.domain.PantryItems
import com.parcha.spoilnot.feature.pantry.domain.PantryQuery
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import com.parcha.spoilnot.feature.pantry.domain.PantryService
import com.parcha.spoilnot.feature.pantry.domain.PantrySort
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    private val repository: PantryRepository,
    private val households: HouseholdRepository,
    writer: HouseholdWriter,
    private val service: PantryService,
    clock: Clock,
    private val deepLinks: DeepLinkRouter,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    /**
     * True once the third dated item is saved and the notification explanation has not been shown (DESIGN 5.4): by
     * then reminders have something to remind about (after v1; it was the first).
     */
    val askForNotifications = MutableStateFlow(false)

    data class UiState(
        val loading: Boolean = true,
        /** One row per purchase (beta feedback). */
        val groups: List<PantryItemGroup> = emptyList(),
        /** Nothing in the pantry at all (not just filtered out). */
        val pantryEmpty: Boolean = false,
        val offline: Boolean = false,
        val query: PantryQuery = PantryQuery(),
        val today: LocalDate = LocalDate.MIN,
        val data: PantryData = PantryData(emptyList(), emptyList(), fromCache = true),
        /** The open Edit window, if any (after v1, DESIGN 9.2). */
        val edit: EditWindow? = null,
        /** Members by uid, while the Edit window is open. */
        val memberNames: Map<String, String> = emptyMap(),
    )

    /**
     * The Edit window: [base] is what it compares with (what it opened with, or the item as it was at the last
     * Review), [changed] the item as it is now when someone else changed it, and [changedBy] their name, if known.
     */
    data class EditWindow(
        val base: ItemEditBase,
        val canChangeUnit: Boolean,
        val changed: ItemEditBase? = null,
        val changedBy: String? = null,
        /** The item is gone; the window closes and says who removed it. Never shown. */
        val removedBy: EditWatch.Removed? = null,
    )

    private val me = session.session.filterIsInstance<Session.InHousehold>()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val query = MutableStateFlow(PantryQuery())

    /** What the open Edit window compares with; null while it is closed. */
    private val editing = MutableStateFlow<ItemEditBase?>(null)

    // Members' names, only while the Edit window is open (it names who changed its item), so Pantry itself doesn't
    // hold another listener.
    private val memberNames: Flow<Map<String, String>> =
        combine(me.map { it?.householdId }, editing.map { it != null }) { hid, open -> hid.takeIf { open } }
            .distinctUntilChanged()
            .flatMapLatest { hid -> if (hid == null) flowOf(emptyList()) else households.observeMembers(hid) }
            .map { list -> list.associate { it.uid to it.displayName } }
            .catch { emit(emptyMap()) }

    @Volatile private var cleaning: Job? = null

    // The household's shared pantry flow (DESIGN 9.1). Everything here runs only while the screen collects [state],
    // so leaving the tab lets go of the listeners (after v1, EFF-01), and off the main thread (PERF-02). A StateFlow,
    // so the state build below always takes the newest snapshot and skips any it had no time for.
    private val data: StateFlow<PantryData?> = me
        .map { it?.householdId }
        .distinctUntilChanged()
        .flatMapLatest { hid -> if (hid == null) flow { } else repository.observe(hid) }
        .onEach(::deleteEmptyBatches)
        .catch { }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<UiState> = combine(data, query, todayFlow(clock), editing, memberNames) {
            data,
            query,
            today,
            editing,
            names,
        ->
        if (data == null) {
            UiState(query = query, today = today)
        } else {
            val all = PantryItems.all(data.products, data.batches, today)
            UiState(
                loading = false,
                groups = PantryItems.build(all, query),
                pantryEmpty = all.isEmpty(),
                offline = data.fromCache,
                query = query,
                today = today,
                data = data,
                edit = editing?.let { editWindow(it, data, names) },
                memberNames = names,
            )
        }
    }.onEach { s -> s.edit?.removedBy?.let { closeRemoved(s.edit.base, it, s.memberNames) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /** The Edit window against the live [data]: someone else's change, or the item gone (DESIGN 9.2, 9.4). */
    private fun editWindow(base: ItemEditBase, data: PantryData, names: Map<String, String>): EditWindow {
        val batch = base.batch
        val others = data.batches.any { it.productId == batch.productId && it.id != batch.id && it.quantity > 0 }
        val window = EditWindow(base, canChangeUnit = !others)
        return when (val watch = ItemEditWatch.check(base, data, myUid())) {
            EditWatch.Unchanged -> window
            is EditWatch.Changed -> window.copy(changed = watch.live, changedBy = watch.byUid?.let(names::get))
            is EditWatch.Removed -> window.copy(removedBy = watch)
        }
    }

    /** Closes the Edit window whose item is gone, saying who removed it unless it was this person. */
    private fun closeRemoved(base: ItemEditBase, removed: EditWatch.Removed, names: Map<String, String>) {
        if (!editing.compareAndSet(base, null)) return
        val hid = me.value?.householdId ?: return
        viewModelScope.launch {
            val who = removed.byUid ?: repository.removedBy(hid, base.batch.id)
            if (who == myUid()) return@launch
            // The member list starts with the window, so a removal right after opening it may come first: wait a
            // moment.
            val name = who?.let { uid ->
                names[uid] ?: withTimeoutOrNull(MEMBER_NAME_WAIT_MS) {
                    runCatching { households.observeMembers(hid).first().firstOrNull { it.uid == uid } }
                        .getOrNull()?.displayName
                }
            }
            _messages.send(
                if (name != null) {
                    UiMessage(R.string.edit_removed_by, name)
                } else {
                    UiMessage(R.string.edit_removed)
                },
            )
        }
    }

    private fun myUid(): String = me.value?.user?.uid.orEmpty()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    init {
        writer.errors.onEach { _messages.send(UiMessage(it.writeMessage())) }.launchIn(viewModelScope)
        // A tapped digest lands here filtered to Expiring soon (DESIGN 5.4); Home cards bring their own filter.
        deepLinks.pending.onEach { link ->
            if (link is DeepLink.Pantry) {
                setFilter(link.filter)
                deepLinks.consume(link)
            }
        }.launchIn(viewModelScope)
    }

    fun setFilter(filter: PantryFilter) = query.update { it.copy(filter = filter) }

    fun setSort(sort: PantrySort) = query.update { it.copy(sort = sort) }

    fun setGrouping(grouping: PantryGrouping) = query.update { it.copy(grouping = grouping) }

    fun setSearch(text: String) = query.update { it.copy(search = text) }

    /** Saves the Add item sheet; returns false when it could not be saved. */
    fun add(draft: StockDraft, onDone: () -> Unit) = act { hid, actor ->
        val before = currentData()
        val result = service.add(hid, actor, draft, before)
        onDone()
        if (draft.expiryDate != null &&
            before.batches.count { it.expiryDate != null } + 1 >= NOTIFICATION_PROMPT_DATED &&
            !appPreferences.hasShownNotificationExplainer()
        ) {
            askForNotifications.value = true
        }
        val text = if (result.merged) R.string.added_merged_message else R.string.added_message
        _messages.send(UiMessage(text, draft.name.trim()))
    }

    fun adjust(batch: StockBatch, delta: Double) = act { hid, actor ->
        service.adjust(hid, actor, batch, delta)?.let {
            _messages.send(UiMessage(R.string.undo_used_up, batch.productName, it))
        }
    }

    fun usedUp(batch: StockBatch) = act { hid, actor ->
        _messages.send(UiMessage(R.string.undo_used_up, batch.productName, service.usedUp(hid, actor, batch)))
    }

    /** Mark opened (DESIGN 5.2): [count] of [batch], to be used within [days]; with Undo. */
    fun markOpened(batch: StockBatch, count: Double, days: Int, product: Product?) = act { hid, actor ->
        val undo = service.markOpened(hid, actor, batch, count, days, product)
        _messages.send(UiMessage(R.string.undo_opened, batch.productName, undo))
    }

    fun throwAway(batch: StockBatch, reason: DiscardReason) = act { hid, actor ->
        _messages.send(
            UiMessage(R.string.undo_thrown_away, batch.productName, service.throwAway(hid, actor, batch, reason)),
        )
    }

    /** Opens the Edit window on [batch] and its product as this phone has them now. */
    fun openEdit(batch: StockBatch) {
        editing.value = ItemEditBase(batch, data.value?.products?.firstOrNull { it.id == batch.productId })
    }

    fun closeEdit() {
        editing.value = null
    }

    /** Review: the window now compares with the item as it is ([live]); the sheet has rebased its draft on it. */
    fun reviewEdit(live: ItemEditBase) {
        editing.value = live
    }

    /**
     * Saves the Edit window: only what the person changed from what it compares with (after v1, DESIGN 9.4). It
     * closes once saved.
     */
    fun saveEdit(edit: ItemEdit) = act { hid, actor ->
        val base = editing.value ?: return@act
        val undo = service.editItem(hid, actor, base, edit, currentData())
        editing.compareAndSet(base, null)
        undo?.let { _messages.send(UiMessage(R.string.undo_used_up, base.batch.productName, it)) }
    }

    fun move(batch: StockBatch, location: Location) = act { hid, actor -> service.move(hid, actor, batch, location) }

    /** The explanation was answered (or not needed): never ask again from here. */
    fun notificationExplainerDone() {
        askForNotifications.value = false
        viewModelScope.launch { appPreferences.markNotificationExplainerShown() }
    }

    fun undo(undo: Undo) = act { hid, actor -> service.undo(hid, actor, undo) }

    /** Invariant 1: delete batches that reached 0, whoever notices first; one clean-up at a time. */
    private fun deleteEmptyBatches(d: PantryData) {
        if (cleaning?.isActive == true || d.batches.none { it.quantity <= 0 && !it.pendingSync }) return
        val (hid, actor) = context() ?: return
        cleaning = viewModelScope.launch { runCatching { service.deleteEmptyBatches(hid, actor, d) } }
    }

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
                _messages.send(UiMessage(e.messageRes()))
            }
        }
    }
}

/** How long the Edit window waits for the member list to name who removed its item. */
internal const val MEMBER_NAME_WAIT_MS = 3_000L

/** Dated items in the pantry before the app explains notifications and asks for permission (after v1). */
internal const val NOTIFICATION_PROMPT_DATED = 3

/** Server rejections of pantry writes; a vanished item means someone else deleted it (DESIGN 9.4). */
@StringRes
internal fun AppError.writeMessage(): Int = messageRes()
