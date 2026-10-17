package com.parcha.spoilnot.core.navigation

import com.parcha.spoilnot.feature.pantry.domain.PantryFilter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where a notification tap or a share from another app should land (DESIGN 10.2, 11.1). */
sealed interface DeepLink {
    /** The Pantry tab with a filter: a digest opens Expiring soon (`/pantry?filter=expiring`), Home cards others. */
    data class Pantry(val filter: PantryFilter) : DeepLink

    /** A link shared from another app; null when the shared text had none. */
    data class ImportRecipe(val url: String?) : DeepLink

    companion object {
        const val EXTRA_ROUTE = "route"
        const val PANTRY_EXPIRING = "/pantry?filter=expiring"

        fun parse(route: String?): DeepLink? = when (route) {
            PANTRY_EXPIRING -> Pantry(PantryFilter.ExpiringSoon)
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
