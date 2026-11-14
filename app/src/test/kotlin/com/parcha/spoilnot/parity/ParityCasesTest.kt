package com.parcha.spoilnot.parity

import com.parcha.spoilnot.feature.grocery.domain.AisleGuess
import com.parcha.spoilnot.feature.grocery.domain.QuickAddParser
import com.parcha.spoilnot.feature.pantry.domain.Amounts
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures
import com.parcha.spoilnot.feature.recipes.domain.IngredientParser
import com.parcha.spoilnot.feature.recipes.domain.ProductLink
import com.parcha.spoilnot.feature.recipes.domain.Scaling
import com.parcha.spoilnot.feature.recipes.domain.TEST_WORDS
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The cases both apps must agree on (after v1, B21), read from `web/src/domain/parity-cases.json`; the web's
 * `parity.test.ts` reads the same file. A case that fails here or there is a difference between the two apps.
 */
class ParityCasesTest {
    private val cases: JsonObject =
        Json.parseToJsonElement(File("../web/src/domain/parity-cases.json").readText()).jsonObject

    private fun section(name: String) = cases.getValue(name).jsonArray.map { it.jsonObject }

    private fun JsonObject.str(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it != JsonNull }?.content

    private fun JsonObject.num(key: String): Double? = (get(key) as? JsonPrimitive)?.doubleOrNull

    private fun check(section: String, wrong: List<String>) =
        assertEquals("$section cases that differ", emptyList<String>(), wrong)

    @Test
    fun `name keys and clean names`() = check(
        "nameKey",
        section("nameKey").mapNotNull { c ->
            val name = c.str("name")!!
            val got = NameKey.of(name) to NameKey.clean(name)
            if (got == c.str("key") to c.str("clean")) null else "${c["name"]} -> $got"
        },
    )

    @Test
    fun `quick add`() = check(
        "quickAdd",
        section("quickAdd").mapNotNull { c ->
            val p = QuickAddParser.parse(c.str("text")!!)
            val got = Triple(p.name, p.quantity, p.unit?.wire)
            if (got == Triple(c.str("name"), c.num("quantity"), c.str("unit"))) null else "${c["text"]} -> $got"
        },
    )

    @Test
    fun `ingredient lines`() = check(
        "ingredient",
        section("ingredient").mapNotNull { c ->
            val p = IngredientParser.parse(c.str("raw")!!)
            val got = Triple(p.qty, p.unit, p.name)
            if (got == Triple(c.num("qty"), c.str("unit"), c.str("name"))) null else "${c["raw"]} -> $got"
        },
    )

    @Test
    fun `scaled lines`() = check(
        "scale",
        section("scale").mapNotNull { c ->
            val got = Scaling.line(c.str("raw")!!, c.num("factor")!!, TEST_WORDS.pinch)
            if (got == c.str("line")) null else "${c["raw"]} × ${c["factor"]} -> $got"
        },
    )

    @Test
    fun `ingredient links`() = check(
        "link",
        section("link").mapNotNull { c ->
            val product = PantryFixtures.product("p", name = c.str("product")!!)
            val linked = ProductLink.find(c.str("line")!!, listOf(product)) != null
            if (linked ==
                c["linked"]!!.jsonPrimitive.booleanOrNull
            ) {
                null
            } else {
                "${c["line"]} / ${c["product"]} -> $linked"
            }
        },
    )

    @Test
    fun `aisle guesses`() = check(
        "aisle",
        section("aisle").mapNotNull { c ->
            val got = AisleGuess.of(c.str("name")!!)?.wire
            if (got == c.str("aisle")) null else "${c["name"]} -> $got"
        },
    )

    @Test
    fun `typed amounts`() = check(
        "amountParse",
        section("amountParse").mapNotNull { c ->
            val got = Amounts.parse(c.str("text")!!)
            if (got == c.num("value")) null else "${c["text"]} -> $got"
        },
    )

    @Test
    fun `shown amounts`() = check(
        "amountFormat",
        section("amountFormat").mapNotNull { c ->
            val got = Amounts.format(c.num("value")!!)
            if (got == c.str("text")) null else "${c["value"]} -> $got"
        },
    )
}
