package com.parcha.spoilnot.feature.recipes.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.parcha.spoilnot.core.firebase.FirestoreChangeMapper
import com.parcha.spoilnot.core.firebase.Stamps
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.feature.recipes.data.FirestoreRecipeRepository.Companion.contentFields
import com.parcha.spoilnot.feature.recipes.data.FirestoreRecipeRepository.Companion.recipes
import com.parcha.spoilnot.feature.recipes.domain.RecipeChange
import javax.inject.Inject

/** Firestore writes for [RecipeChange]s. */
class RecipeChangeMapper @Inject constructor() : FirestoreChangeMapper {
    override fun write(
        batch: WriteBatch,
        db: FirebaseFirestore,
        householdId: String,
        actor: Actor,
        change: HouseholdChange,
    ): Boolean {
        if (change !is RecipeChange) return false
        val stamp = Stamps.updated(actor)
        fun doc(id: String) = db.document("${recipes(householdId)}/$id")
        when (change) {
            is RecipeChange.Create -> batch.set(
                doc(change.recipe.id),
                contentFields(change.recipe.content) + mapOf("favorite" to change.recipe.favorite, "rating" to null) +
                    Stamps.created(actor) + stamp,
            )

            is RecipeChange.Restore -> batch.set(
                doc(change.recipe.id),
                contentFields(change.recipe.content) + stamp + mapOf(
                    "favorite" to change.recipe.favorite,
                    "rating" to change.recipe.rating,
                    "createdAt" to (change.recipe.createdAt?.let { Timestamp(it) } ?: FieldValue.serverTimestamp()),
                    "createdBy" to (change.recipe.createdBy ?: actor.uid),
                ),
            )

            is RecipeChange.Update -> batch.update(doc(change.recipeId), contentFields(change.content) + stamp)

            is RecipeChange.SetFavorite -> batch.update(
                doc(change.recipeId),
                mapOf("favorite" to change.favorite) + stamp,
            )

            is RecipeChange.Delete -> batch.delete(doc(change.recipeId))

            is RecipeChange.LogSaved -> batch.set(
                db.document("households/$householdId/activity/${change.eventId}"),
                mapOf(
                    "type" to TYPE_SAVED,
                    "actorUid" to actor.uid,
                    "actorName" to actor.name,
                    "recipeId" to change.recipeId,
                    "params" to mapOf("name" to change.title),
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
        }
        return true
    }

    private companion object {
        const val TYPE_SAVED = "recipe_saved"
    }
}
