package com.parcha.spoilnot.core.firebase

import com.google.firebase.Timestamp
import com.parcha.spoilnot.feature.auth.data.toProfile
import com.parcha.spoilnot.feature.grocery.data.FirestoreGroceryRepository.Companion.toItem
import com.parcha.spoilnot.feature.grocery.data.FirestoreGroceryRepository.Companion.toList
import com.parcha.spoilnot.feature.grocery.data.FirestoreGroceryRepository.Companion.toLists
import com.parcha.spoilnot.feature.home.data.FirestoreActivityRepository.Companion.toEvent
import com.parcha.spoilnot.feature.household.data.FirestoreHouseholdRepository.Companion.toHousehold
import com.parcha.spoilnot.feature.household.data.FirestoreHouseholdRepository.Companion.toInviteCode
import com.parcha.spoilnot.feature.household.data.FirestoreHouseholdRepository.Companion.toJoinRequest
import com.parcha.spoilnot.feature.household.data.FirestoreHouseholdRepository.Companion.toMember
import com.parcha.spoilnot.feature.household.domain.Role
import com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository.Companion.toBatch
import com.parcha.spoilnot.feature.pantry.data.FirestorePantryRepository.Companion.toProduct
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.plan.data.FirestoreMealPlanRepository.Companion.toMeal
import com.parcha.spoilnot.feature.recipes.data.FirestoreRecipeRepository.Companion.toRecipe
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A field of another type reads as missing, so one document written by a buggy client, a console edit or a restore
 * doesn't blank a list on every phone (after v1, review REL-04). [fakeSnapshot]'s typed getters throw like the SDK's,
 * so these fail if a mapper goes back to them. The first cases are review 05's round-1 documents.
 */
class WrongTypeReadTest {
    private val time = Timestamp(Instant.parse("2026-11-01T10:00:00Z"))

    @Test
    fun `a batch with a text quantity reads as 0 and keeps the rest`() {
        val batch = fakeSnapshot(
            "bad-qty",
            mapOf("productId" to "p-b0", "productName" to "Milk", "quantity" to "2", "expiryDate" to "2026-11-20"),
        ).toBatch()
        assertEquals(0.0, batch.quantity, 0.0)
        assertEquals("Milk", batch.productName)
        assertEquals("2026-11-20", batch.expiryDate.toString())
    }

    @Test
    fun `a grocery item checked yes reads as unchecked`() {
        val item = fakeSnapshot("bad-checked", mapOf("name" to "Bad checked", "checked" to "yes")).toItem()
        assertFalse(item.checked)
        assertEquals("Bad checked", item.name)
    }

    @Test
    fun `a recipe with text servings reads without servings`() {
        val recipe = fakeSnapshot("bad-servings", mapOf("title" to "Bad servings", "servings" to "4")).toRecipe()
        assertNull(recipe.content.servings)
        assertEquals("Bad servings", recipe.content.title)
    }

    @Test
    fun `a meal with a text position reads at position 0`() {
        val meal = fakeSnapshot(
            "bad-position",
            mapOf("date" to "2026-11-11", "slot" to "lunch", "position" to "1", "title" to "Bad position"),
        ).toMeal()
        assertEquals(0L, meal?.position)
        assertEquals("Bad position", meal?.title)
    }

    @Test
    fun `an event with a numeric author name reads without a name`() {
        val event = fakeSnapshot(
            "bad-created",
            mapOf("type" to "stock_added", "actorUid" to "u1", "actorName" to 42, "params" to mapOf("name" to "Eggs")),
        ).toEvent()
        assertEquals("", event?.actorName)
        assertEquals("Eggs", event?.name)
    }

    @Test
    fun `list fields holding text read as empty lists`() {
        val product = fakeSnapshot(
            "p1",
            mapOf("name" to "Milk", "recentShelfLifeDays" to "7", "aliases" to "whole milk"),
        ).toProduct()
        assertEquals(emptyList<Int>(), product.recentShelfLifeDays)
        assertEquals(emptyList<String>(), product.aliases)

        val recipe = fakeSnapshot(
            "r1",
            mapOf("title" to "Soup", "ingredients" to "one string", "steps" to "Cook it.", "tags" to "x"),
        ).toRecipe()
        assertEquals(emptyList<Any>(), recipe.content.ingredients)
        assertEquals(emptyList<Any>(), recipe.content.steps)
        assertEquals(emptyList<String>(), recipe.content.tags)

        val meal = fakeSnapshot("m1", mapOf("date" to "2026-11-11", "slot" to "dinner", "cooks" to "u1")).toMeal()
        assertEquals(emptyList<String>(), meal?.cooks)

        val list = fakeSnapshot("g1", mapOf("name" to "Groceries", "aisleOrder" to "dairy")).toList()
        assertEquals(emptyList<Category>(), list.aisleOrder)
    }

    @Test
    fun `list entries of another type are left out`() {
        val product = fakeSnapshot(
            "p1",
            mapOf(
                "recentShelfLifeDays" to listOf(7L, "8", 9.0),
                "aliases" to listOf("a", 3),
            ),
        )
            .toProduct()
        assertEquals(listOf(7, 9), product.recentShelfLifeDays)
        assertEquals(listOf("a"), product.aliases)
        val recipe = fakeSnapshot(
            "r1",
            mapOf(
                "tags" to listOf("x", 3),
                "ingredients" to listOf("2 eggs", mapOf("raw" to "1 onion")),
            ),
        )
            .toRecipe()
        assertEquals(listOf("x"), recipe.content.tags)
        assertEquals(listOf("1 onion"), recipe.content.ingredients.map { it.raw })
    }

    @Test
    fun `times of another type read as missing, and real times still read`() {
        val batch = fakeSnapshot("s1", mapOf("createdAt" to "2026-12-24T18:00:00Z", "quantity" to 1L)).toBatch()
        assertNull(batch.createdAt)
        val item = fakeSnapshot("i1", mapOf("name" to "Eggs", "createdAt" to time)).toItem()
        assertEquals(time.toInstant(), item.createdAt)
        val meal = fakeSnapshot("m1", mapOf("date" to "2026-11-11", "slot" to "dinner", "cookedAt" to 5L)).toMeal()
        assertNull(meal?.cookedAt)
        val member = fakeSnapshot(
            "u1",
            mapOf("displayName" to 42, "role" to true, "joinedAt" to "yesterday"),
        ).toMember()
        assertEquals("", member.displayName)
        assertEquals(Role.Member, member.role)
        assertNull(member.joinedAt)
    }

    @Test
    fun `numbers of another kind still read`() {
        // A double where the app writes an integer (servings 2.5 from review 05's round 2) is cut, as getLong did.
        val recipe = fakeSnapshot("r1", mapOf("servings" to 2.5, "totalMinutes" to 30L)).toRecipe()
        assertEquals(2, recipe.content.servings)
        assertEquals(30, recipe.content.totalMinutes)
        // An integer where the app writes a double.
        assertEquals(2.0, fakeSnapshot("s1", mapOf("quantity" to 2L)).toBatch().quantity, 0.0)
    }

    @Test
    fun `household, invite code, join request and profile read with wrong types`() {
        val household = fakeSnapshot(
            "h1",
            mapOf("name" to 7, "ownerUid" to "u1", "inviteCode" to listOf("A")),
        ).toHousehold()
        assertEquals("", household.name)
        assertEquals("u1", household.ownerUid)
        assertEquals("", household.inviteCode)

        val code = fakeSnapshot("ABC", mapOf("householdId" to "h1", "active" to "true")).toInviteCode()
        assertFalse(code.active)

        val request = fakeSnapshot(
            "u2",
            mapOf("status" to "pending", "displayName" to 3, "joinCode" to 1234L, "requestedAt" to "now"),
        ).toJoinRequest()
        assertEquals("", request?.displayName)
        assertEquals("", request?.code)
        assertNull(request?.requestedAt)

        val profile = fakeSnapshot("u1", mapOf("displayName" to "Sam", "householdId" to 5L, "units" to 1)).toProfile()
        assertEquals("Sam", profile.displayName)
        assertNull(profile.householdId)
    }

    @Test
    fun `an unknown meal slot is left out, not an error`() {
        assertNull(fakeSnapshot("m1", mapOf("date" to "2026-11-11", "slot" to "snack")).toMeal())
        assertNull(fakeSnapshot("m2", mapOf("date" to 20261111L, "slot" to "lunch")).toMeal())
    }

    @Test
    fun `a document that fails to map is skipped and the others still show`() {
        val docs = listOf(
            fakeSnapshot("g1", mapOf("name" to "Groceries", "isDefault" to true)),
            fakeSnapshot("g2", mapOf("name" to "Party")),
        )
        val mapped = docs.mapEach { if (it.id == "g2") error("boom") else it.toList() }
        assertEquals(listOf("g1"), mapped.map { it.id })
        assertTrue(docs.toLists().size == 2)
    }

    @Test
    fun `the newest change a server snapshot brought ignores a time of another type`() {
        val doc = fakeSnapshot("s1", mapOf("updatedAt" to "2026-11-01T10:00:00Z"))
        assertNull(doc.instant("updatedAt", estimate = false))
        assertEquals(time.toInstant(), fakeSnapshot("s2", mapOf("updatedAt" to time)).instant("updatedAt"))
    }
}
