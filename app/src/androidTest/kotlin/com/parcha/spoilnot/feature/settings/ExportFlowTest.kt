package com.parcha.spoilnot.feature.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.TestPhone
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import com.parcha.spoilnot.feature.settings.data.FirestoreHouseholdExporter
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings > Export (DESIGN 13.4) against the emulators: it reads from the server, so offline it says "You're offline"
 * instead of saving the phone's copy, and it includes the person's own profile and the activity log (after v1,
 * 2026-11-11).
 */
@RunWith(AndroidJUnit4::class)
class ExportFlowTest {
    private lateinit var phone: TestPhone
    private lateinit var householdId: String
    private lateinit var exporter: FirestoreHouseholdExporter

    @Before
    fun setUp() = runBlocking {
        EmulatorSupport.requireEmulatorAndNetworkAccess()
        phone = TestPhone("export")
        phone.sessionManager.signUp("Olive", uniqueEmail("olive"), PASSWORD)
        phone.awaitSession { it is Session.NoHousehold }
        householdId = phone.households.createHousehold(phone.uid, "Olive", "Export test", InviteCodes.generate())
        phone.awaitSession { it is Session.InHousehold }
        exporter = FirestoreHouseholdExporter(phone.firestore, Clock.systemUTC())
    }

    @After
    fun tearDown() {
        if (::phone.isInitialized) {
            runBlocking { phone.firestore.current.enableNetwork().await() }
            phone.close()
        }
    }

    @Test
    fun exportReadsTheServerAndIncludesTheProfileAndActivity() = runBlocking {
        val draft = StockDraft(
            "Cheese", 1.0, QuantityUnit.Pieces, LocalDate.now().plusDays(4), DateType.UseBy, Location.Fridge,
            Category.Other, null, null, false, null,
        )
        val pantry = withTimeout(10_000) { phone.pantryRepository.observe(householdId).first { !it.fromCache } }
        phone.pantry.add(householdId, phone.actor, draft, pantry)
        phone.firestore.current.waitForPendingWrites().await()

        val json = Json.parseToJsonElement(exporter.export(householdId, phone.uid)).jsonObject
        assertEquals("Export test", json["household"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("Cheese"),
            json["products"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content },
        )
        val profile = json["profile"]!!.jsonObject
        assertEquals(phone.uid, profile["id"]!!.jsonPrimitive.content)
        assertEquals("Olive", profile["displayName"]!!.jsonPrimitive.content)
        val types = json["activity"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertTrue("activity: $types", "stock_added" in types)

        // Offline: no file from the phone's copy, though the cache has everything.
        phone.firestore.current.disableNetwork().await()
        try {
            exporter.export(householdId, phone.uid)
            fail("an offline export must not save the phone's copy")
        } catch (e: AppError.Offline) {
            // "You're offline" (error_offline).
        }
    }
}
