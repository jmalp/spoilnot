package com.parcha.spoilnot.feature.recipes.data

import com.parcha.spoilnot.core.firebase.fakeSnapshot
import com.parcha.spoilnot.feature.recipes.data.FirestoreRecipeRepository.Companion.toRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the app takes from a stored recipe that another client wrote (DESIGN 8.1, 11.3). */
class RecipeReadTest {
    private fun read(vararg fields: Pair<String, Any?>) =
        fakeSnapshot("r1", mapOf("title" to "Pancakes", *fields)).toRecipe()

    @Test
    fun `links and photos other than http and https are dropped`() {
        for (link in listOf("tel:+15555550100", "sms:+15555550100", "intent://x#Intent;end", "javascript:alert(1)")) {
            assertNull(link, read("sourceUrl" to link).content.sourceUrl)
        }
        for (photo in listOf(
            "data:image/png;base64,AAAA",
            "file:///sdcard/x.png",
            "content://x/y",
            "//cdn.example.com/p.jpg",
        )) {
            assertNull(photo, read("imageUrl" to photo).content.imageUrl)
        }
        assertNull(read("sourceUrl" to "https://example.com/${"x".repeat(2_000)}").content.sourceUrl)
    }

    @Test
    fun `web links and photos are kept`() {
        val recipe = read("sourceUrl" to "https://example.com/pancakes", "imageUrl" to "http://example.com/p.jpg")
        assertEquals("https://example.com/pancakes", recipe.content.sourceUrl)
        assertEquals("http://example.com/p.jpg", recipe.content.imageUrl)
    }

    @Test
    fun `a title is cut to 200 characters`() {
        assertEquals("t".repeat(200), read("title" to "t".repeat(100_000)).content.title)
    }
}
