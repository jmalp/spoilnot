package com.parcha.spoilnot.core.navigation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.parcha.spoilnot.EmulatorAdmin
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.MainActivity
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import com.parcha.spoilnot.feature.pantry.domain.PantryService
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * A change the server refuses later is reported whichever tab is open (after v1, review REL-13): refused writes are
 * collected once, in the shell. Before, only Pantry, Plan and Groceries showed them, while they were open.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WriteRefusalUiTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val signedOut = object : ExternalResource() {
        override fun before() {
            EmulatorSupport.requireEmulatorAndNetworkAccess()
            EmulatorSupport.skipAppTour()
            FirebaseAuth.getInstance().signOut()
        }
    }

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var sessionManager: SessionManager

    @Inject lateinit var households: HouseholdRepository

    @Inject lateinit var firestore: FirestoreProvider

    @Inject lateinit var pantry: PantryService

    @Inject lateinit var pantryRepository: PantryRepository

    @Before
    fun inject() = hilt.inject()

    @After
    fun online() {
        runBlocking { runCatching { firestore.current.enableNetwork().await() } }
    }

    private fun str(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun tab(id: Int) = compose.onNode(hasText(str(id)) and isSelectable())

    @Test
    fun aRefusedChangeIsReportedOnAnotherTab() {
        compose.waitUntilAtLeastOneExists(hasTestTag("screen_welcome"), 20_000)
        val (householdId, actor) = runBlocking {
            sessionManager.signUp("Rae", uniqueEmail("rae"), PASSWORD)
            withTimeout(20_000) { sessionManager.session.first { it is Session.NoHousehold } }
            val uid = checkNotNull(FirebaseAuth.getInstance().currentUser).uid
            val hid = households.createHousehold(uid, "Rae", "Refusals", InviteCodes.generate())
            withTimeout(20_000) { sessionManager.session.first { it is Session.InHousehold } }
            hid to Actor(uid, "Rae")
        }
        val draft = StockDraft(
            "Cheese", 1.0, QuantityUnit.Pieces, LocalDate.now().plusDays(4), DateType.UseBy, Location.Fridge,
            Category.Other, null, null, false, null,
        )
        val cheese = runBlocking {
            pantry.add(householdId, actor, draft, pantryRepository.observe(householdId).first())
            firestore.current.waitForPendingWrites().await()
            withTimeout(15_000) {
                pantryRepository.observe(householdId).first { d -> d.batches.any { it.productName == "Cheese" } }
            }.batches.single { it.productName == "Cheese" }
        }

        // Offline, +1 on the cheese (as from Pantry), then the person moves to Recipes.
        compose.waitUntilAtLeastOneExists(hasTestTag("screen_home"), 20_000)
        runBlocking {
            firestore.current.disableNetwork().await()
            pantry.adjust(householdId, actor, cheese, 1.0)
        }
        tab(R.string.tab_recipes).performClick()
        compose.onNodeWithTag("screen_recipes").assertIsDisplayed()

        // Meanwhile the batch was deleted outright on the server, so the +1 is refused when the phone is back.
        EmulatorAdmin.deleteDocument("households/$householdId/stock/${cheese.id}")
        runBlocking { firestore.current.enableNetwork().await() }

        // The rules see no document to update, so the server refuses it.
        compose.waitUntilAtLeastOneExists(hasText(str(R.string.error_permission)), 30_000)
        compose.onNodeWithTag("screen_recipes").assertIsDisplayed()
    }
}
