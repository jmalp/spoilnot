package com.parcha.spoilnot.core.navigation

import com.parcha.spoilnot.feature.pantry.domain.PantryFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    @Test
    fun `digest routes open Needs attention or, with only meals, the Plan tab`() {
        // The path keeps its old name, which released builds still read as Expiring soon (after v1, R8).
        assertEquals(DeepLink.Pantry(PantryFilter.NeedsAttention), DeepLink.parse("/pantry?filter=expiring"))
        assertEquals(DeepLink.Plan, DeepLink.parse("/plan"))
        assertNull(DeepLink.parse("/somewhere-else"))
        assertNull(DeepLink.parse(null))
    }
}
