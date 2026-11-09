package com.parcha.spoilnot.feature.grocery.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.feature.grocery.domain.GroceryChange
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The restock engine's write of a Low stock item (DESIGN 9.6). */
class GroceryChangeMapperTest {
    private val paths = mutableListOf<String>()
    private val db = mockk<FirebaseFirestore> {
        every { document(any()) } answers {
            paths += firstArg<String>()
            mockk<DocumentReference>()
        }
    }
    private val fields = slot<Map<String, Any?>>()
    private val batch = mockk<WriteBatch> {
        every { set(any(), capture(fields), any<SetOptions>()) } answers { self as WriteBatch }
    }

    private fun upsert(writeCreated: Boolean) = GroceryChange.UpsertLowStock(
        listId = "g1",
        productId = "milk",
        name = "Milk",
        nameKey = "milk",
        unit = QuantityUnit.Pieces,
        category = Category.Dairy,
        quantity = 1.0,
        writeCreated = writeCreated,
    )

    private fun write(change: GroceryChange): Map<String, Any?> {
        assertTrue(GroceryChangeMapper().write(batch, db, "h1", Actor("u1", "Sam"), change))
        verify(exactly = 1) { batch.set(any(), any(), any<SetOptions>()) }
        return fields.captured
    }

    @Test
    fun `a new item, or a tombstone coming back, starts unchecked with no one who checked it`() {
        // LOGIC-01: Add to pantry leaves a checked tombstone; the next dip must not bring it back in the cart.
        val written = write(upsert(writeCreated = true))
        assertEquals(listOf("households/h1/groceryLists/g1/items/low_milk"), paths)
        assertEquals(false, written["checked"])
        for (field in listOf("checkedBy", "checkedAt", "aliasOf", "note", "recipeId")) {
            assertTrue("$field written", written.containsKey(field))
            assertNull(field, written[field])
        }
        assertEquals("u1", written["createdBy"])
        assertTrue("brought back from a tombstone", written.containsKey("deletedAt"))
    }

    @Test
    fun `an item already live in the cache keeps its checked state and creator`() {
        val written = write(upsert(writeCreated = false))
        for (field in listOf("checked", "checkedBy", "checkedAt", "createdBy", "createdAt")) {
            assertFalse("$field written", written.containsKey(field))
        }
        assertEquals(1.0, written["quantity"])
        assertEquals("u1", written["updatedBy"])
    }
}
