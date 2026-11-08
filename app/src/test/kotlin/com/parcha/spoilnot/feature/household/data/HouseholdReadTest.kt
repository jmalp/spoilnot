package com.parcha.spoilnot.feature.household.data

import com.parcha.spoilnot.core.firebase.fakeSnapshot
import com.parcha.spoilnot.feature.household.data.FirestoreHouseholdRepository.Companion.toHousehold
import com.parcha.spoilnot.feature.household.data.FirestoreHouseholdRepository.Companion.toInviteCode
import com.parcha.spoilnot.feature.household.data.FirestoreHouseholdRepository.Companion.toJoinRequest
import com.parcha.spoilnot.feature.household.data.FirestoreHouseholdRepository.Companion.toMember
import org.junit.Assert.assertEquals
import org.junit.Test

class HouseholdReadTest {
    private val long = "N".repeat(10_000)

    @Test
    fun `people's names are cut to 40 characters and household names to 60`() {
        assertEquals(
            "N".repeat(40),
            fakeSnapshot("u1", mapOf("displayName" to long, "role" to "member")).toMember().displayName,
        )
        assertEquals(
            "N".repeat(40),
            fakeSnapshot("u2", mapOf("displayName" to long, "status" to "pending")).toJoinRequest()?.displayName,
        )
        assertEquals("N".repeat(60), fakeSnapshot("h1", mapOf("name" to long)).toHousehold().name)
        assertEquals(
            "N".repeat(60),
            fakeSnapshot("ABCDE23456", mapOf("householdName" to long)).toInviteCode().householdName,
        )
    }
}
