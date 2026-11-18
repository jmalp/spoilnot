package com.parcha.spoilnot.perf

import android.util.Log
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.parcha.spoilnot.EmulatorAdmin
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.MainActivity
import com.parcha.spoilnot.PerfTest
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.feature.grocery.domain.GroceryChange
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryRepository
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.household.domain.HouseholdRepository
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import com.parcha.spoilnot.feature.household.domain.Session
import com.parcha.spoilnot.feature.household.domain.SessionManager
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.PantryRepository
import com.parcha.spoilnot.feature.pantry.domain.PantryService
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import com.parcha.spoilnot.feature.plan.domain.MealPlanChange
import com.parcha.spoilnot.feature.plan.domain.MealPlanRepository
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.domain.PlannedMeal
import com.parcha.spoilnot.feature.recipes.domain.Ingredient
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeChange
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.RecipeRepository
import com.parcha.spoilnot.feature.recipes.domain.Step
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DESIGN 13.1 budget: smooth scrolling with 500 batches. Seeds 500 batches (250 products, two each), flings the
 * Pantry list and logs Android's frame statistics. Opt in with `-e perf true` ([PerfTest]); the numbers are a debug build's, so
 * a release build does better.
 *
 * It also seeds what `JankBenchmark` needs for the other screens (after v1, 2026-11-17, review 08's B23 and B24), as
 * review 08 had them: 150 items on the grocery list (30 in the cart), 40 recipes (without photos), three meals a day
 * from a week ago to the end of next week, and six months of counts for Your kitchen. Each is added only where it is
 * missing, also to a household seeded before them.
 */
@OptIn(ExperimentalTestApi::class)
@PerfTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PantryScrollPerfTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var session: SessionManager

    @Inject lateinit var households: HouseholdRepository

    @Inject lateinit var pantry: PantryService

    @Inject lateinit var pantryRepository: PantryRepository

    @Inject lateinit var writer: HouseholdWriter

    @Inject lateinit var groceries: GroceryRepository

    @Inject lateinit var recipes: RecipeRepository

    @Inject lateinit var mealPlan: MealPlanRepository

    private var scenario: ActivityScenario<MainActivity>? = null
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun seed500Batches() = runBlocking {
        EmulatorSupport.requireEmulatorAndNetworkAccess()
        hilt.inject()
        FirebaseAuth.getInstance().signOut()
        // A fixed emulator account, so the release-like benchmark build can sign in to the same data.
        if (runCatching { session.signIn(PERF_EMAIL, PASSWORD) }.isSuccess) {
            val signedIn = withTimeout(15_000) {
                session.session.first { it is Session.InHousehold || it is Session.NoHousehold }
            }
            // The account outlives a cleared Firestore emulator (the rules tests clear it); seed again then.
            if (signedIn is Session.InHousehold) {
                seedExtras(signedIn.householdId, signedIn.user.uid)
                return@runBlocking
            }
        } else {
            session.signUp("Pat", PERF_EMAIL, PASSWORD)
            withTimeout(15_000) { session.session.first { it is Session.NoHousehold } }
        }
        val uid = checkNotNull(FirebaseAuth.getInstance().currentUser).uid
        val hid = households.createHousehold(uid, "Pat", "Perf home", InviteCodes.generate())
        withTimeout(15_000) { session.session.first { it is Session.InHousehold } }
        var data = withTimeout(10_000) { pantryRepository.observe(hid).first() }
        val changes = mutableListOf<HouseholdChange>()
        val today = LocalDate.now()
        repeat(500) { i ->
            val draft = StockDraft(
                "Item ${i / 2}", 1.0 + i % 3, QuantityUnit.Pieces, today.plusDays((i % 40).toLong() - 5),
                DateType.UseBy, Location.entries[i % 4], Category.entries[i % 9], null, null, false, null,
            )
            val plan = pantry.planAdd(draft, data)
            changes += plan.changes
            data = plan.after
        }
        writer.apply(hid, Actor(uid, "Pat"), changes)
        withTimeout(60_000) { pantryRepository.observe(hid).first { !it.fromCache && it.batches.size == 500 } }
        seedExtras(hid, uid)
    }

    /** What `JankBenchmark` scrolls and opens on the other tabs (the class comment), where it is missing. */
    private suspend fun seedExtras(hid: String, uid: String) {
        val actor = Actor(uid, "Pat")
        val list = withTimeout(15_000) { groceries.observeLists(hid).first { lists -> lists.any { it.isDefault } } }
            .first { it.isDefault }
        val items = withTimeout(15_000) { groceries.observeItems(hid, list.id).first() }
        if (items.size < GROCERY_ITEMS) {
            writer.apply(hid, actor, (items.size until GROCERY_ITEMS).map { groceryItem(list.id, it, uid) })
            withTimeout(60_000) { groceries.observeItems(hid, list.id).first { it.size >= GROCERY_ITEMS } }
        }

        val recipeBox = withTimeout(30_000) { recipes.observe(hid).first { !it.fromCache } }.recipes
        if (recipeBox.size < RECIPES) {
            writer.apply(hid, actor, (recipeBox.size until RECIPES).map { RecipeChange.Create(recipe(it, uid)) })
        }
        val box = withTimeout(60_000) { recipes.observe(hid).first { !it.fromCache && it.recipes.size >= RECIPES } }
            .recipes.sortedBy { it.title }

        val today = LocalDate.now()
        val from = today.minusDays(7)
        val to = today.plusDays(13)
        val planned = withTimeout(30_000) { mealPlan.observe(hid, from, to).first { !it.fromCache } }.meals
            .map { it.date to it.slot }
            .toSet()
        val meals = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
            .flatMap { date -> MealSlot.entries.map { date to it } }
            .filter { it !in planned }
            .mapIndexed { i, (date, slot) ->
                val recipe = box[(i * 3) % box.size]
                MealPlanChange.Create(
                    PlannedMeal(
                        id = writer.newId(),
                        date = date,
                        slot = slot,
                        position = 0,
                        recipeId = recipe.id,
                        title = recipe.title,
                        servings = 4,
                        cooks = listOf(uid),
                        note = if (i % 5 == 0) "Keep a plate for later" else null,
                    ),
                )
            }
            .toList()
        if (meals.isNotEmpty()) writer.apply(hid, actor, meals)

        // Counts are counters the app increments as batches finish; written past the rules, as half a year of use
        // would leave them (DESIGN 8.2).
        val thisMonth = YearMonth.now()
        withContext(Dispatchers.IO) {
            for (back in 0L..5L) {
                val path = "households/$hid/stats/${thisMonth.minusMonths(back)}"
                if (EmulatorAdmin.documentExists(path)) continue
                val expired = 1 + (back % 3).toInt()
                val spoiled = (back % 2).toInt()
                EmulatorAdmin.putDocument(
                    path,
                    mapOf(
                        "used" to 18 + (back * 5 % 11).toInt(),
                        DiscardReason.Expired.wire to expired,
                        DiscardReason.Spoiled.wire to spoiled,
                        "thrownByDay" to mapOf("1" to expired + spoiled),
                        "updatedAt" to Instant.now(),
                        "updatedBy" to uid,
                    ),
                )
            }
        }
    }

    /** Review 08's grocery list: every aisle, every fifth item in the cart, a note on every seventh. */
    private fun groceryItem(listId: String, i: Int, uid: String): GroceryChange.CreateItem {
        val round = i / FOODS.size
        val name = FOODS[i % FOODS.size] + if (round > 0) " ${round + 1}" else ""
        val inCart = i % 5 == 4
        return GroceryChange.CreateItem(
            listId,
            GroceryItem(
                id = writer.newId(),
                name = name,
                nameKey = NameKey.of(name),
                quantity = if (i % 3 == 0) null else 1.0 + i % 4,
                unit = if (i % 3 == 0) null else QuantityUnit.Pieces,
                note = if (i % 7 == 0) "The big one, not the small one" else null,
                category = Category.entries[i % Category.entries.size],
                checked = inCart,
                checkedBy = if (inCart) uid else null,
                source = ItemSource.Manual,
                productId = null,
                recipeId = null,
                createdBy = uid,
                createdAt = null,
            ),
        )
    }

    private fun recipe(i: Int, uid: String): Recipe {
        val round = i / RECIPE_TITLES.size
        val title = RECIPE_TITLES[i % RECIPE_TITLES.size] + if (round > 0) " ${round + 1}" else ""
        return Recipe(
            id = writer.newId(),
            content = RecipeContent(
                title = title,
                servings = 4,
                servingsText = "4 servings",
                totalMinutes = 15 + (i * 7) % 80,
                ingredients = listOf(
                    "2 eggs",
                    "200 g flour",
                    "1 onion, diced",
                    "2 cloves garlic",
                    "1 can (400 g) tomatoes",
                    "1 tsp salt",
                ).map { Ingredient(it) },
                steps = listOf(Step("Get everything ready."), Step("Cook the ${title.lowercase()}."), Step("Serve.")),
                tags = if (i % 3 == 0) listOf("breakfast", "quick") else listOf("dinner"),
            ),
            favorite = i % 4 == 0,
            createdBy = uid,
        )
    }

    @After
    fun tearDown() {
        scenario?.close()
        FirebaseAuth.getInstance().signOut()
    }

    companion object {
        /** Emulator-only account holding the 500-batch household. */
        const val PERF_EMAIL = "perf-500@example.com"

        /** Items on the perf household's grocery list, for the Groceries benchmarks. */
        const val GROCERY_ITEMS = 150

        /** Recipes in the perf household's box. */
        const val RECIPES = 40

        private val FOODS = listOf(
            "Apples", "Sourdough bread", "Whole milk", "Chicken thighs", "Frozen peas", "Basmati rice",
            "Sparkling water", "Tortilla chips", "Dish soap", "Bananas", "Bagels", "Greek yogurt", "Salmon fillets",
            "Ice cream", "Canned tomatoes", "Orange juice", "Dark chocolate", "Paper towels", "Spinach", "Croissants",
            "Cheddar", "Ground beef", "Frozen berries", "Red lentils", "Coffee beans", "Pretzels", "Batteries AA",
        )

        private val RECIPE_TITLES = listOf(
            "Spinach and feta omelette", "Creamy tomato pasta", "Banana bread", "Chicken tacos", "Greek yogurt parfait",
            "Vegetable stir-fry", "Red lentil soup", "Fluffy pancakes", "Caesar salad", "Beef chili",
        )
    }

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).let { fd ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }

    @Test
    fun pantryScrollsSmoothlyWith500Batches() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val context = instrumentation.targetContext
        compose.waitUntilAtLeastOneExists(hasText(context.getString(R.string.tab_pantry)) and isSelectable(), 20_000)
        compose.onNode(hasText(context.getString(R.string.tab_pantry)) and isSelectable()).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("pantry_list"), 20_000)
        compose.waitForIdle()

        val pkg = context.packageName
        shell("dumpsys gfxinfo $pkg reset")
        repeat(6) {
            repeat(4) { compose.onNodeWithTag("pantry_list").performTouchInput { swipeUp(durationMillis = 150) } }
            repeat(4) { compose.onNodeWithTag("pantry_list").performTouchInput { swipeDown(durationMillis = 150) } }
        }
        compose.waitForIdle()
        val stats = shell("dumpsys gfxinfo $pkg")
        val lines = stats.lines().filter {
            it.contains("Total frames rendered") || it.contains("Janky frames") || it.contains("percentile")
        }.take(8)
        lines.forEach { Log.i("PantryScrollPerf", it.trim()) }
    }
}
