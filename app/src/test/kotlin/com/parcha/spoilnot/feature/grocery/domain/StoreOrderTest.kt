package com.parcha.spoilnot.feature.grocery.domain

import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.Category.Bakery
import com.parcha.spoilnot.feature.pantry.domain.Category.Dairy
import com.parcha.spoilnot.feature.pantry.domain.Category.Frozen
import com.parcha.spoilnot.feature.pantry.domain.Category.MeatFish
import com.parcha.spoilnot.feature.pantry.domain.Category.Produce
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoreOrderTest {
    @Test
    fun `an aisle visited after one the order puts later moves just after it`() {
        // Default order: Produce, Dairy, MeatFish, Bakery, Frozen, ...
        val learned = StoreOrder.learn(emptyList(), visited = listOf(Bakery), checked = Dairy)!!
        assertEquals(listOf(Produce, MeatFish, Bakery, Dairy, Frozen), learned.take(5))
    }

    @Test
    fun `an order that already agrees, the first aisle of a trip and a revisited aisle change nothing`() {
        assertNull(StoreOrder.learn(emptyList(), visited = listOf(Produce), checked = Bakery))
        assertNull(StoreOrder.learn(emptyList(), visited = emptyList(), checked = Frozen))
        assertNull(StoreOrder.learn(emptyList(), visited = listOf(Dairy, Bakery), checked = Dairy))
    }

    @Test
    fun `a few trips settle the store's order`() {
        var order = emptyList<Category>()
        repeat(2) {
            val trip = listOf(Bakery, Produce, Frozen, Dairy)
            trip.forEachIndexed { i, aisle -> StoreOrder.learn(order, trip.take(i), aisle)?.let { order = it } }
        }
        assertEquals(
            listOf(Bakery, Produce, Frozen, Dairy),
            StoreOrder.effective(order).filter {
                it in
                    setOf(Bakery, Produce, Frozen, Dairy)
            },
        )
    }

    @Test
    fun `aisles are shown in the saved order, unknown ones in the default order after`() {
        val view = GroceryView(listOf(Produce to emptyList(), Dairy to emptyList(), Frozen to emptyList()), emptyList())
        val sorted = StoreOrder.sort(view, listOf(Frozen, Produce))
        assertEquals(listOf(Frozen, Produce, Dairy), sorted.aisles.map { it.first })
    }

    @Test
    fun `a trip ends after two quiet hours or on another list`() {
        var now = Instant.parse("2026-10-19T10:00:00Z")
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId?): Clock = this
            override fun instant(): Instant = now
        }
        val trip = ShoppingTrip(clock)
        assertEquals(emptyList<Category>(), trip.checked("g1", Produce))
        assertEquals(listOf(Produce), trip.checked("g1", Dairy))
        assertEquals(listOf(Produce, Dairy), trip.checked("g1", Produce))
        assertEquals(emptyList<Category>(), trip.checked("g2", Bakery))
        now = now.plus(Duration.ofHours(3))
        assertEquals(emptyList<Category>(), trip.checked("g2", Frozen))
    }
}
