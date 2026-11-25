package com.parcha.spoilnot.feature.pantry.domain

import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one Needs attention set (after v1, R8): Home, Pantry's filter and the digest (`digest.test.ts`) agree. */
class NeedsAttentionTest {
    private val products = listOf(
        product("milk"),
        product("yogurt"),
        product("chips", "Tortilla chips"),
        product("dill", "Fresh dill"),
        product("rice"),
        product("oil", "Olive oil", min = 1.0),
    )
    private val batches = listOf(
        batch("m1", "milk", inDays = 0),
        batch("m2", "milk", inDays = 3),
        batch("y1", "yogurt", inDays = 1),
        batch("c1", "chips", inDays = -40),
        batch("d1", "dill", inDays = -1),
        batch("r1", "rice", inDays = 4),
        batch("u1", "rice", inDays = null),
        batch("gone", "yogurt", quantity = 0.0, inDays = 1),
    )

    @Test
    fun `usable first, soonest first, expired apart, and nothing past 3 days, undated, used up or out`() {
        val set = NeedsAttention.of(products, batches, TODAY)
        assertEquals(listOf("m1", "y1", "m2"), set.usable.map { it.key })
        assertEquals(listOf("c1", "d1"), set.expired.map { it.key })
        assertEquals(5, set.size)
    }

    @Test
    fun `the 3-day edge follows the injected today`() {
        val set = NeedsAttention.of(products, batches, TODAY.plusDays(1))
        // A day later: Milk today is expired, and Rice comes within 3 days.
        assertEquals(listOf("y1", "m2", "r1"), set.usable.map { it.key })
        assertEquals(listOf("c1", "d1", "m1"), set.expired.map { it.key })
    }

    @Test
    fun `Pantry's Needs attention filter lists exactly the set, so Home's See all count matches`() {
        val set = NeedsAttention.of(products, batches, TODAY)
        val filtered = PantryItems.build(products, batches, PantryQuery(filter = PantryFilter.NeedsAttention), TODAY)
            .flatMap { it.items }.map { it.key }
        assertEquals((set.usable + set.expired).map { it.key }.toSet(), filtered.toSet())
        assertEquals(set.size, filtered.size)
    }

    @Test
    fun `an empty pantry needs nothing`() {
        assertTrue(NeedsAttention.of(products, emptyList(), TODAY).isEmpty())
    }
}
