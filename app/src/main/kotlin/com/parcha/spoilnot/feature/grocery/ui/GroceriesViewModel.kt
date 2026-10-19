package com.parcha.spoilnot.feature.grocery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.ui.UiMessage
import com.parcha.spoilnot.core.ui.messageRes
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.core.write.Undo
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryLayout
import com.parcha.spoilnot.feature.grocery.domain.GroceryList
import com.parcha.spoilnot.feature.grocery.domain.GroceryRepository
import com.parcha.spoilnot.feature.grocery.domain.GroceryService
import com.parcha.spoilnot.feature.grocery.domain.GroceryView
import com.parcha.spoilnot.feature.grocery.domain.PantryEntry
import com.parcha.spoilnot.feature.grocery.domain.QuickAddResult
import com.parcha.spoilnot.feature.grocery.domain.RestockEngine
import com.parcha.spoilnot.feature.grocery.domain.ShoppingTrip
import com.parcha.spoilnot.feature.grocery.domain.StoreOrder
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.ui.todayFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Groceries tab (DESIGN 5.3). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroceriesViewModel @Inject constructor(
    session: SessionManager,
    grocery: GroceryRepository,
    pantry: PantryRepository,
    households: HouseholdRepository,
    writer: HouseholdWriter,
    private val service: GroceryService,
    clock: Clock,
) : ViewModel() {
    /** This phone's current shopping trip, for learning the store order. */
    private val trip = ShoppingTrip(clock)

    data class UiState(
        val loading: Boolean = true,
        val lists: List<GroceryList> = emptyList(),
        val list: GroceryList? = null,
        val items: List<GroceryItem> = emptyList(),
        val view: GroceryView = GroceryView(emptyList(), emptyList()),
        val backInStock: Set<String> = emptySet(),
        val memberNames: Map<String, String> = emptyMap(),
        val pantry: PantryData = PantryData(emptyList(), emptyList(), fromCache = true),
        val today: LocalDate = LocalDate.MIN,
        val myUid: String = "",
    )

    private val me = session.session.filterIsInstance<Session.InHousehold>()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val hid = me.map { it?.householdId }.distinctUntilChanged()
    private val selectedListId = MutableStateFlow<String?>(null)

    private val lists = hid.flatMapLatest {
        if (it ==
            null
        ) {
            flowOf(emptyList())
        } else {
            grocery.observeLists(it)
        }
    }.catch { }
    private val currentList = combine(lists, selectedListId) { all, selected ->
        all.firstOrNull { it.id == selected } ?: all.firstOrNull { it.isDefault } ?: all.firstOrNull()
    }.distinctUntilChanged()
    private val items: Flow<List<GroceryItem>?> = combine(
        hid,
        currentList.map {
            it?.id
        }.distinctUntilChanged(),
    ) { h, l ->
        h to
            l
    }
        .flatMapLatest { (h, l) -> if (h == null || l == null) flowOf(null) else grocery.observeItems(h, l) }
        .catch { }
    private val pantryData = hid.flatMapLatest { if (it == null) emptyFlow() else pantry.observe(it) }.catch { }
    private val members = hid.flatMapLatest { if (it == null) flowOf(emptyList()) else households.observeMembers(it) }
        .map { list -> list.associate { it.uid to it.displayName } }
        .catch { emit(emptyMap()) }

    val state: StateFlow<UiState> = combine(
        combine(lists, currentList, items) { l, c, i -> Triple(l, c, i) },
        pantryData,
        members,
        todayFlow(clock),
    ) { (allLists, list, listItems), data, names, today ->
        val itemsNow = listItems.orEmpty()
        UiState(
            loading = listItems == null,
            lists = allLists,
            list = list,
            items = itemsNow,
            view = StoreOrder.sort(GroceryLayout.of(itemsNow), list?.aisleOrder.orEmpty()),
            backInStock = itemsNow.filter { RestockEngine.isBackInStock(it, data) }.map { it.id }.toSet(),
            memberNames = names,
            pantry = data,
            today = today,
            myUid = me.value?.user?.uid.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(LIST_LINGER_MS), UiState())

    /** "Already on the list" waiting for an answer, and the text that was typed. */
    val duplicate = MutableStateFlow<QuickAddResult.AlreadyOnList?>(null)
    private var duplicateText = ""

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    init {
        writer.errors.onEach { _messages.send(UiMessage(it.messageRes())) }.launchIn(viewModelScope)
        announceOthersAdditions()
    }

    fun selectList(list: GroceryList) = selectedListId.update { list.id }

    /** Returns through [onAdded] so the field is cleared only when the item was added. */
    fun quickAdd(text: String, addAnyway: Boolean = false, onAdded: () -> Unit) = act { hid, actor ->
        val list = state.value.list ?: return@act
        when (
            val result = service.quickAdd(
                hid,
                actor,
                list.id,
                text,
                state.value.items,
                state.value.pantry,
                addAnyway,
                list.name,
            )
        ) {
            QuickAddResult.Added -> onAdded()

            is QuickAddResult.AlreadyOnList -> {
                duplicateText = text
                duplicate.value = result
            }
        }
    }

    fun increaseDuplicate(onDone: () -> Unit) = act { hid, actor ->
        val pending = duplicate.value ?: return@act
        val list = state.value.list ?: return@act
        service.increase(hid, actor, list.id, pending.existing, pending.request)
        duplicate.value = null
        onDone()
    }

    fun addDuplicateAnyway(onDone: () -> Unit) {
        if (duplicate.value == null) return
        duplicate.value = null
        quickAdd(duplicateText, addAnyway = true, onAdded = onDone)
    }

    fun dismissDuplicate() {
        duplicate.value = null
    }

    fun toggle(item: GroceryItem) = act { hid, actor ->
        service.setChecked(hid, actor, listId(), item, !item.checked, state.value.list?.name)
        // Learn the store's aisle order from the order things go into the cart (DESIGN 5.3).
        val list = state.value.list ?: return@act
        if (!item.checked) {
            val aisle = item.category ?: Category.Other
            val before = trip.checked(list.id, aisle)
            StoreOrder.learn(list.aisleOrder, before, aisle)?.let { service.setAisleOrder(hid, actor, list, it) }
        }
    }

    fun delete(item: GroceryItem) = act { hid, actor ->
        _messages.send(UiMessage(R.string.grocery_deleted, item.name, service.delete(hid, actor, listId(), item)))
    }

    fun clearChecked() = act { hid, actor ->
        service.clearChecked(hid, actor, listId(), state.value.items)?.let {
            _messages.send(UiMessage(R.string.grocery_cleared, null, it))
        }
    }

    fun edit(
        item: GroceryItem,
        quantity: Double?,
        unit: QuantityUnit?,
        note: String?,
        category: Category?,
        name: String? = null,
    ) = act { hid, actor ->
        service.edit(hid, actor, listId(), item, quantity, unit, note, category, name, state.value.pantry.products)
    }

    fun createList(name: String) = act { hid, actor -> selectedListId.value = service.createList(hid, actor, name) }

    fun renameList(name: String) = act { hid, actor ->
        state.value.list?.let { service.renameList(hid, actor, it, name) }
    }

    fun deleteList() = act { hid, actor ->
        val list = state.value.list ?: return@act
        service.deleteList(hid, actor, list)
        selectedListId.value = null
    }

    fun addToPantry(entries: List<PantryEntry>, skipped: List<GroceryItem>, onDone: () -> Unit) = act { hid, actor ->
        service.addToPantry(hid, actor, listId(), entries, skipped, state.value.pantry)
        onDone()
        if (entries.isNotEmpty()) _messages.send(UiMessage(R.string.grocery_added_to_pantry))
    }

    fun undo(undo: Undo) = act { hid, actor -> service.undo(hid, actor, undo) }

    /** "Sam added Eggs" when someone else adds to the list being viewed (DESIGN 9.2). */
    private fun announceOthersAdditions() {
        var known: Set<String>? = null
        var knownList: String? = null
        state.onEach { s ->
            if (s.loading) return@onEach
            val ids = s.items.map { it.id }.toSet()
            val previous = known
            if (previous != null && knownList == s.list?.id) {
                s.items.filter {
                    it.id !in previous && it.createdBy != null && it.createdBy != s.myUid
                }.forEach { item ->
                    val who = s.memberNames[item.createdBy] ?: return@forEach
                    _messages.send(UiMessage(R.string.grocery_someone_added, listOf(who, item.name)))
                }
            }
            known = ids
            knownList = s.list?.id
        }.launchIn(viewModelScope)
    }

    private fun listId(): String = checkNotNull(state.value.list).id

    private fun act(block: suspend (String, Actor) -> Unit) {
        val s = me.value ?: return
        viewModelScope.launch {
            try {
                block(s.householdId, Actor(s.user.uid, s.displayName))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(UiMessage(e.messageRes()))
            }
        }
    }

    private companion object {
        /** DESIGN 9.1: item listeners stay while visible, plus 30 s. */
        const val LIST_LINGER_MS = 30_000L
    }
}
