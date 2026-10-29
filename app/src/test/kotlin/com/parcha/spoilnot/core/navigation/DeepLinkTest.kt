package com.parcha.spoilnot.core.navigation

import com.parcha.spoilnot.feature.pantry.domain.PantryFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    @Test
    fun `digest routes open Expiring soon or, with only meals, the Plan tab`() {
        assertEquals(DeepLink.Pantry(PantryFilter.ExpiringSoon), DeepLink.parse("/pantry?filter=expiring"))
        assertEquals(DeepLink.Plan, DeepLink.parse("/plan"))
        assertNull(DeepLink.parse("/somewhere-else"))
        assertNull(DeepLink.parse(null))
    }
}
