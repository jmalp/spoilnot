package com.parcha.spoilnot.feature.recipes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parcha.spoilnot.core.ui.UiMessage
import com.parcha.spoilnot.core.ui.messageRes
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The household and the signed-in member for recipe screens, and their snackbar messages. */
abstract class RecipeScopeViewModel(session: SessionManager) : ViewModel() {
    private val me = session.session.filterIsInstance<Session.InHousehold>()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    protected suspend fun show(message: UiMessage) = _messages.send(message)

    protected fun context(): Pair<String, Actor>? = me.value?.let {
        it.householdId to Actor(it.user.uid, it.displayName)
    }

    /** Runs a write for the current household; failures become a snackbar. */
    protected fun act(block: suspend (String, Actor) -> Unit) {
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

/** Messages that outlive the screen that caused them, such as Undo after deleting from the detail screen. */
@Singleton
class RecipeEvents @Inject constructor() {
    private val channel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = channel.receiveAsFlow()

    suspend fun send(message: UiMessage) = channel.send(message)
}
