package com.parcha.spoilnot.core.ui

import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.error.AppError.LostRace.Race
import org.junit.Assert.assertEquals
import org.junit.Test

/** A refused write that lost to another phone says why, naming who won when it can (DESIGN 8.5). */
class ErrorMessagesTest {
    private val names = mapOf("sam" to "Sam", "me" to "Alex")

    private fun message(error: Throwable) = error.toUiMessage({ names[it] }, myUid = "me")

    @Test
    fun `a lost Add to pantry names the member who took the items off the list`() {
        assertEquals(
            UiMessage(R.string.race_pantry_by, listOf("Sam")),
            message(AppError.LostRace(Race.AddedToPantry, "sam")),
        )
        assertEquals(
            UiMessage(R.string.race_removed_by, listOf("Sam")),
            message(AppError.LostRace(Race.Removed, "sam")),
        )
        assertEquals(UiMessage(R.string.race_cooked_by, listOf("Sam")), message(AppError.LostRace(Race.Cooked, "sam")))
    }

    @Test
    fun `without a known name, or when it was this account on another phone, it says it without one`() {
        assertEquals(UiMessage(R.string.race_pantry), message(AppError.LostRace(Race.AddedToPantry, "me")))
        assertEquals(UiMessage(R.string.race_cooked), message(AppError.LostRace(Race.Cooked, "left-the-household")))
        assertEquals(UiMessage(R.string.race_removed), message(AppError.LostRace(Race.Removed, null)))
        assertEquals(UiMessage(R.string.race_pantry), AppError.LostRace(Race.AddedToPantry, "sam").toUiMessage())
    }

    @Test
    fun `a deleted list is never anyone's name, and other errors keep their message`() {
        assertEquals(UiMessage(R.string.race_list_deleted), message(AppError.LostRace(Race.ListDeleted, "sam")))
        assertEquals(R.string.race_list_deleted, AppError.LostRace(Race.ListDeleted).messageRes())
        assertEquals(UiMessage(R.string.error_permission), message(AppError.PermissionDenied()))
        assertEquals(UiMessage(R.string.error_unknown), message(IllegalStateException()))
    }
}
