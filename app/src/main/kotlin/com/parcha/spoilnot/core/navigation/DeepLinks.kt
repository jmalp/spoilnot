package com.parcha.spoilnot.core.navigation

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where a notification tap should land (DESIGN 10.2). */
sealed interface DeepLink {
    /** `/pantry?filter=expiring`: the Pantry tab filtered to Expiring soon. */
    data object PantryExpiring : DeepLink

    companion object {
        const val EXTRA_ROUTE = "route"
        const val PANTRY_EXPIRING = "/pantry?filter=expiring"

        fun parse(route: String?): DeepLink? = when (route) {
            PANTRY_EXPIRING -> PantryExpiring
            else -> null
        }
    }
}

/** Holds a deep link until the screen it targets has applied it. */
@Singleton
class DeepLinkRouter @Inject constructor() {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()

    fun open(link: DeepLink?) {
        if (link != null) _pending.value = link
    }

    fun consume(link: DeepLink) {
        _pending.compareAndSet(link, null)
    }
}
