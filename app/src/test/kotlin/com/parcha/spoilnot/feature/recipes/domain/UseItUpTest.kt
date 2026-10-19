package com.parcha.spoilnot.feature.recipes.domain

import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.TODAY
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.batch
import com.parcha.spoilnot.feature.pantry.domain.PantryFixtures.product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UseItUpTest {
    private val spinach = product("spinach")
    private val yogurt = product("yogurt")
    private val rice = product("rice")
    private val pantry = PantryData(
        listOf(spinach, yogurt, rice),
        listOf(
            batch("s1", "spinach", inDays = 1),
            batch("s2", "spinach", inDays = 6),
            batch("y1", "yogurt", inDays = 3),
            batch("r1", "rice", inDays = 90),
        ),
        fromCache = false,
    )

    @Test
    fun `recipes using the most expiring food come first, with the batch that expires first`() {
        val curry =
            recipe("curry", "Green curry", ingredients = listOf("2 cups fresh spinach", "1 cup yogurt", "1 cup rice"))
        val salad =
            recipe("salad", "Spinach salad", ingredients = listOf("4 cups baby spinach leaves", "1 cup spinach"))
        val pilaf = recipe("pilaf", "Pilaf", ingredients = listOf("2 cups rice"))
        val result = UseItUp.suggestions(listOf(pilaf, salad, curry), pantry, TODAY)
        assertEquals(listOf("curry", "salad"), result.map { it.recipe.id })
        assertEquals(listOf("spinach", "yogurt"), result[0].uses.map { it.product.id })
        assertEquals("s1", result[0].uses[0].batch.id)
        assertEquals(1, result[1].uses.size) // "baby spinach leaves" is not Spinach; "spinach" is, once
    }

    @Test
    fun `expired food and food with time left are not suggestions`() {
        val old =
            PantryData(
                listOf(spinach),
                listOf(batch("s1", "spinach", inDays = -1), batch("s2", "spinach", inDays = 9)),
                false,
            )
        assertTrue(UseItUp.suggestions(listOf(recipe("a", ingredients = listOf("spinach"))), old, TODAY).isEmpty())
    }

    @Test
    fun `at most three, favorites first among equals`() {
        val recipes = (1..5).map {
            recipe("r$it", "Recipe $it", favorite = it == 4, ingredients = listOf("1 cup yogurt"))
        }
        val result = UseItUp.suggestions(recipes, pantry, TODAY)
        assertEquals(listOf("r4", "r1", "r2"), result.map { it.recipe.id })
    }
}
