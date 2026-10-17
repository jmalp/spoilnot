package com.parcha.spoilnot.feature.pantry.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.ui.UiMessage
import com.parcha.spoilnot.core.ui.messageRes
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.Undo
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.pantry.domain.BatchEdit
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryEvent
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import com.parcha.spoilnot.feature.pantry.domain.PantryService
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.ProductEdit
import com.parcha.spoilnot.feature.pantry.domain.StockBatch
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Product detail (DESIGN 5.2): totals, batches, settings, rename, shelf life and the last 20 events. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ProductDetailViewModel.Factory::class)
class ProductDetailViewModel @AssistedInject constructor(
    @Assisted private val productId: String,
    session: SessionManager,
    repository: PantryRepository,
    private val service: PantryService,
    clock: Clock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(productId: String): ProductDetailViewModel
    }

    data class UiState(
        val loading: Boolean = true,
        val product: Product? = null,
        /** The batch's own copy of the name when the product is not loaded yet. */
        val name: String = "",
        val batches: List<StockBatch> = emptyList(),
        val events: List<PantryEvent> = emptyList(),
        val today: LocalDate = LocalDate.MIN,
        val data: PantryData = PantryData(emptyList(), emptyList(), fromCache = true),
    ) {
        val total: Double get() = batches.sumOf { it.quantity }
        val hasStock: Boolean get() = batches.isNotEmpty()
    }

    private val me = session.session.filterIsInstance<Session.InHousehold>()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<UiState> = me.map { it?.householdId }.distinctUntilChanged().flatMapLatest { hid ->
        if (hid == null) {
            flowOf(UiState())
        } else {
            combine(repository.observe(hid), repository.observeEvents(hid, productId, EVENT_LIMIT), todayFlow(clock)) {
                    data,
                    events,
                    today,
                ->
                val batches = data.batches.filter { it.productId == productId && it.quantity > 0 }
                    .sortedWith(compareBy(nullsLast()) { it.expiryDate })
                val product = data.products.firstOrNull { it.id == productId }
                UiState(
                    false,
                    product,
                    product?.name ?: batches.firstOrNull()?.productName.orEmpty(),
                    batches,
                    events,
                    today,
                    data,
                )
            }
        }
    }.catch { }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    fun save(edit: ProductEdit) = act { hid, actor ->
        val product = state.value.product ?: return@act
        service.updateProduct(hid, actor, product, edit, state.value.data)
        _messages.send(UiMessage(R.string.saved))
    }

    fun adjust(batch: StockBatch, delta: Double) = act { hid, actor ->
        service.adjust(hid, actor, batch, delta)?.let {
            _messages.send(UiMessage(R.string.undo_used_up, batch.productName, it))
        }
    }

    fun usedUp(batch: StockBatch) = act { hid, actor ->
        _messages.send(UiMessage(R.string.undo_used_up, batch.productName, service.usedUp(hid, actor, batch)))
    }

    fun throwAway(batch: StockBatch, reason: DiscardReason) = act { hid, actor ->
        _messages.send(
            UiMessage(R.string.undo_thrown_away, batch.productName, service.throwAway(hid, actor, batch, reason)),
        )
    }

    fun edit(batch: StockBatch, edit: BatchEdit) = act { hid, actor ->
        service.edit(hid, actor, batch, edit)?.let {
            _messages.send(UiMessage(R.string.undo_used_up, batch.productName, it))
        }
    }

    fun move(batch: StockBatch, location: Location) = act { hid, actor -> service.move(hid, actor, batch, location) }

    fun undo(undo: Undo) = act { hid, actor -> service.undo(hid, actor, undo) }

    private fun act(block: suspend (String, Actor) -> Unit) {
        val s = me.value ?: return
        viewModelScope.launch {
            try {
                block(s.householdId, Actor(s.user.uid, s.displayName))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppError.Conflict) {
                _messages.send(UiMessage(R.string.error_product_exists))
            } catch (e: Exception) {
                _messages.send(UiMessage(e.messageRes()))
            }
        }
    }

    private companion object {
        const val EVENT_LIMIT = 20
    }
}
