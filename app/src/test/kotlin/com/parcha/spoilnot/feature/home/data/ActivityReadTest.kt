package com.parcha.spoilnot.feature.home.data

import com.parcha.spoilnot.core.firebase.fakeSnapshot
import com.parcha.spoilnot.feature.home.data.FirestoreActivityRepository.Companion.toEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityReadTest {
    @Test
    fun `an event's author, name and list are cut to their lengths`() {
        val event = fakeSnapshot(
            "a1",
            mapOf(
                "type" to "grocery_added",
                "actorUid" to "u1",
                "actorName" to "A".repeat(1_000),
                "params" to mapOf("name" to "x".repeat(100_000), "listName" to "l".repeat(1_000)),
            ),
        ).toEvent()!!
        assertEquals("A".repeat(40), event.actorName)
        assertEquals("x".repeat(200), event.name)
        assertEquals("l".repeat(60), event.listName)
    }
}
