package com.parcha.spoilnot.core.write

import com.parcha.spoilnot.feature.auth.domain.AccountValidation
import com.parcha.spoilnot.feature.grocery.domain.GroceryRules
import com.parcha.spoilnot.feature.household.domain.HouseholdRules
import com.parcha.spoilnot.feature.pantry.domain.PantryLimits
import com.parcha.spoilnot.feature.plan.domain.MealPlanLimits
import com.parcha.spoilnot.feature.recipes.domain.RecipeGrocery
import com.parcha.spoilnot.feature.recipes.domain.RecipeLimits
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's field limits against `firebase/firestore.rules`, which decide what the server accepts (DESIGN 8.1; after
 * v1, review B27, CODE-04). A limit is kept next to the feature that uses it; this test reads each field's limit from
 * the rules and fails when the two disagree, so a change on one side can't leave the other behind. Where the app
 * writes less than the rules accept (notes, a recipe's servings), it checks the app stays within them. The web's
 * `field-limits.test.ts` checks its constants the same way.
 */
class FieldLimitsTest {
    private val rules = File("../firebase/firestore.rules").readText()

    @Test
    fun peopleAndHouseholds() {
        same(AccountValidation.MAX_DISPLAY_NAME_LENGTH, text("users", "displayName"))
        same(AccountValidation.MAX_DISPLAY_NAME_LENGTH, text("joinRequests", "displayName"))
        same(ReadCaps.PERSON, text("members", "displayName"))
        same(ReadCaps.PERSON, text("activity", "actorName"))
        same(HouseholdRules.MAX_NAME_LENGTH, text("households", "name"))
        same(HouseholdRules.MAX_NAME_LENGTH, text("inviteCodes", "householdName"))
        same(ReadCaps.LIST_NAME, text("households", "name"))
    }

    @Test
    fun pantry() {
        same(PantryLimits.MAX_NAME_LENGTH, text("products", "name"))
        same(PantryLimits.MAX_NAME_LENGTH, text("stock", "productName"))
        same(ReadCaps.NAME, text("products", "name"))
        same(PantryLimits.MAX_ALIASES, list("products", "aliases").count)
        same(PantryLimits.MAX_ALIASES * PantryLimits.MAX_NAME_LENGTH, list("products", "aliases").total!!)
        same(PantryLimits.MAX_OPENED_DAYS, number("products", "openedShelfLifeDays"))
        within(PantryLimits.MAX_NOTE_LENGTH, text("stock", "note"))
    }

    @Test
    fun groceries() {
        same(GroceryRules.MAX_LIST_NAME_LENGTH, text("groceryLists", "name"))
        same(ReadCaps.LIST_NAME, text("groceryLists", "name"))
        same(GroceryRules.MAX_LIST_NAME_LENGTH, text("activity", "listName"))
        same(GroceryRules.MAX_NAME_LENGTH, text("items", "name"))
        same(PantryLimits.MAX_NAME_LENGTH, text("items", "aliasOf"))
        within(RecipeGrocery.MAX_NOTE_LENGTH, text("items", "note"))
    }

    @Test
    fun recipes() {
        same(RecipeLimits.TITLE, text("recipes", "title"))
        same(ReadCaps.RECIPE_TITLE, text("recipes", "title"))
        // An event's name is at most a recipe's title (recipe_saved, recipe_cooked).
        same(RecipeLimits.TITLE, text("activity", "name"))
        same(RecipeLimits.SOURCE_NAME, text("recipes", "sourceName"))
        same(RecipeLimits.DESCRIPTION, text("recipes", "description"))
        same(RecipeLimits.SERVINGS_TEXT, text("recipes", "servingsText"))
        same(RecipeLimits.NOTES, text("recipes", "notes"))
        same(RecipeLimits.MAX_INGREDIENTS, list("recipes", "ingredients").count)
        same(RecipeLimits.MAX_STEPS, list("recipes", "steps").count)
        same(RecipeLimits.MAX_TAGS, list("recipes", "tags").count)
        same(RecipeLimits.MAX_TAGS * RecipeLimits.TAG, list("recipes", "tags").total!!)
        same(RecipeLimits.URL, webUrl())
        within(RecipeLimits.MAX_SERVINGS, number("recipes", "servings"))
    }

    @Test
    fun meals() {
        same(MealPlanLimits.TITLE, text("meals", "title"))
        same(ReadCaps.MEAL_TITLE, text("meals", "title"))
        same(MealPlanLimits.NOTE, text("meals", "note"))
        same(MealPlanLimits.MAX_COOKS, list("meals", "cooks").count)
        same(MealPlanLimits.MAX_SERVINGS, number("meals", "servings"))
    }

    /** The parser finds what it looks for, so a renamed rule fails here rather than passing unchecked. */
    @Test
    fun readsTheRules() {
        assertEquals(80, text("products", "name"))
        assertEquals(Limit(30, 30 * 80), list("products", "aliases"))
        assertEquals(1000, number("meals", "servings"))
    }

    private fun same(app: Int, rule: Int) = assertEquals("the app's limit and the rules' differ", rule, app)

    private fun within(app: Int, rule: Int) = assertTrue("the app writes $app, the rules accept $rule", app <= rule)

    /** The rules of `match /<path>/{…}`, up to the next `match` (a nested collection's rules come after its parent's). */
    private fun block(path: String): String {
        val start = rules.indexOf("match /$path/{")
        check(start >= 0) { "no match /$path in the rules" }
        val end = rules.indexOf("match /", start + 1).takeIf { it > 0 } ?: rules.length
        return rules.substring(start, end)
    }

    /** The longest string the rules accept for [field]: `s(d.field, lo, hi)` or `optS(d, 'field', lo, hi)`. */
    private fun text(path: String, field: String): Int {
        val f = Regex.escape(field)
        return single(
            path,
            field,
            Regex("""\bs\([\w.]+\.$f, \d+, (\d+)\)"""),
            Regex("""optS\([\w.]+, '$f', \d+, (\d+)\)"""),
        )
    }

    /** The largest number the rules accept for [field]: `optInt(d, 'field', lo, hi)` or `x.field <= hi`. */
    private fun number(path: String, field: String): Int {
        val f = Regex.escape(field)
        return single(path, field, Regex("""optInt\([\w.]+, '$f', \d+, (\d+)\)"""), Regex("""\.$f <= (\d+)"""))
    }

    /** How many entries the rules accept for [field], and in total characters for a list of strings. */
    private fun list(path: String, field: String): Limit {
        val f = Regex.escape(field)
        val block = block(path)
        Regex("""optStrList\([\w.]+, '$f', (\d+), ([\d *]+)\)""").find(block)?.let { m ->
            return Limit(m.groupValues[1].toInt(), product(m.groupValues[2]))
        }
        return Limit(
            single(
                path,
                field,
                Regex("""\.get\('$f', \[]\)\.size\(\) <= (\d+)"""),
                Regex("""\.$f\.size\(\) <= (\d+)"""),
            ),
            null,
        )
    }

    /** A recipe link's or photo's longest accepted length (`optWebUrl`). */
    private fun webUrl(): Int {
        val function = rules.substring(rules.indexOf("function optWebUrl")).substringBefore("}")
        return Regex("""\bs\(d\[f], \d+, (\d+)\)""").find(function)!!.groupValues[1].toInt()
    }

    /** Every match of [patterns] in [path]'s rules: at least one, and all the same. */
    private fun single(path: String, field: String, vararg patterns: Regex): Int {
        val block = block(path)
        val found = patterns.flatMap { p -> p.findAll(block).map { it.groupValues[1].toInt() } }.toSet()
        check(found.size == 1) { "expected one limit for $path.$field in the rules, found $found" }
        return found.single()
    }

    private fun product(expression: String) = expression.split('*').map { it.trim().toInt() }.reduce(Int::times)

    private data class Limit(val count: Int, val total: Int?)
}
