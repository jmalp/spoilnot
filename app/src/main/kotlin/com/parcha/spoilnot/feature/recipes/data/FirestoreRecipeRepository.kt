package com.parcha.spoilnot.feature.recipes.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.retryAfterOwnWrites
import com.parcha.spoilnot.core.firebase.snapshotFlow
import com.parcha.spoilnot.feature.recipes.domain.ImportMethod
import com.parcha.spoilnot.feature.recipes.domain.Ingredient
import com.parcha.spoilnot.feature.recipes.domain.Recipe
import com.parcha.spoilnot.feature.recipes.domain.RecipeBoxData
import com.parcha.spoilnot.feature.recipes.domain.RecipeContent
import com.parcha.spoilnot.feature.recipes.domain.RecipeRepository
import com.parcha.spoilnot.feature.recipes.domain.Step
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreRecipeRepository @Inject constructor(private val firestore: FirestoreProvider) : RecipeRepository {
    override fun observe(householdId: String): Flow<RecipeBoxData> = firestore.instance
        // Metadata changes too, so fromCache turns false once the server confirms (offline banner).
        .flatMapLatest { db ->
            db.collection(recipes(householdId)).snapshotFlow(MetadataChanges.INCLUDE).retryAfterOwnWrites(db)
        }
        .map { snapshot -> RecipeBoxData(snapshot.documents.map { it.toRecipe() }, snapshot.metadata.isFromCache) }
        .distinctUntilChanged()

    internal companion object {
        fun recipes(hid: String) = "households/$hid/recipes"

        /** Every content field written explicitly, nulls included (DESIGN 8.1). */
        fun contentFields(c: RecipeContent): Map<String, Any?> = mapOf(
            "title" to c.title,
            "sourceUrl" to c.sourceUrl,
            "sourceName" to c.sourceName,
            "imageUrl" to c.imageUrl,
            "description" to c.description,
            "servings" to c.servings,
            "servingsText" to c.servingsText,
            "prepMinutes" to c.prepMinutes,
            "cookMinutes" to c.cookMinutes,
            "totalMinutes" to c.totalMinutes,
            "ingredients" to c.ingredients.map {
                mapOf("raw" to it.raw, "qty" to it.qty, "unit" to it.unit, "name" to it.name, "group" to it.group)
            },
            "steps" to c.steps.map { mapOf("text" to it.text, "group" to it.group) },
            "notes" to c.notes,
            "tags" to c.tags,
            "importMethod" to c.importMethod.wire,
        )

        // New fields are optional on read with defaults (DESIGN 8.1).
        fun DocumentSnapshot.toRecipe() = Recipe(
            id = id,
            content = RecipeContent(
                title = getString("title").orEmpty(),
                sourceUrl = getString("sourceUrl"),
                sourceName = getString("sourceName"),
                imageUrl = getString("imageUrl"),
                description = getString("description"),
                servings = getLong("servings")?.toInt(),
                servingsText = getString("servingsText"),
                prepMinutes = getLong("prepMinutes")?.toInt(),
                cookMinutes = getLong("cookMinutes")?.toInt(),
                totalMinutes = getLong("totalMinutes")?.toInt(),
                ingredients = maps("ingredients").mapNotNull { m ->
                    (m["raw"] as? String)?.let { raw ->
                        Ingredient(
                            raw = raw,
                            qty = (m["qty"] as? Number)?.toDouble(),
                            unit = m["unit"] as? String,
                            name = m["name"] as? String,
                            group = m["group"] as? String,
                        )
                    }
                },
                steps = maps("steps").mapNotNull { m ->
                    (m["text"] as? String)?.let { Step(it, m["group"] as? String) }
                },
                notes = getString("notes"),
                tags = (get("tags") as? List<*>)?.filterIsInstance<String>().orEmpty(),
                importMethod = ImportMethod.fromWire(getString("importMethod")),
            ),
            favorite = getBoolean("favorite") == true,
            rating = getLong("rating")?.toInt(),
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant(),
            createdBy = getString("createdBy"),
        )

        private fun DocumentSnapshot.maps(field: String): List<Map<*, *>> =
            (get(field) as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
    }
}
