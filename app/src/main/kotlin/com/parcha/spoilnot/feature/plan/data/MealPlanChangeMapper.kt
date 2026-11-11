package com.parcha.spoilnot.feature.plan.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.firebase.FirestoreChangeMapper
import com.parcha.spoilnot.core.firebase.Stamps
import com.parcha.spoilnot.core.firebase.Tombstones
import com.parcha.spoilnot.core.firebase.awaitMapped
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.feature.plan.data.FirestoreMealPlanRepository.Companion.meals
import com.parcha.spoilnot.feature.plan.domain.MealPlanChange
import com.parcha.spoilnot.feature.plan.domain.PlannedMeal
import javax.inject.Inject

/** Firestore writes for [MealPlanChange]s. Every field is written explicitly, nulls included (DESIGN 8.1). */
class MealPlanChangeMapper @Inject constructor() : FirestoreChangeMapper {
    override fun write(
        batch: WriteBatch,
        db: FirebaseFirestore,
        householdId: String,
        actor: Actor,
        change: HouseholdChange,
    ): Boolean {
        if (change !is MealPlanChange) return false
        fun doc(id: String) = db.document("${meals(householdId)}/$id")
        val stamp = Stamps.updated(actor)
        when (change) {
            is MealPlanChange.Create -> batch.set(
                doc(change.meal.id),
                fields(change.meal) + Stamps.created(actor) + stamp,
            )

            // Undo removes the tombstone and nothing else (DESIGN 8.6).
            is MealPlanChange.Restore -> batch.set(doc(change.meal.id), Tombstones.restore(actor), SetOptions.merge())

            is MealPlanChange.Update -> batch.update(
                doc(change.mealId),
                mapOf("servings" to change.servings, "cooks" to change.cooks, "note" to change.note) + stamp,
            )

            is MealPlanChange.Delete -> batch.set(doc(change.mealId), Tombstones.fields(actor), SetOptions.merge())

            is MealPlanChange.Move -> batch.update(
                doc(change.mealId),
                mapOf("date" to change.date.toString(), "slot" to change.slot.wire, "position" to change.position) +
                    stamp,
            )

            is MealPlanChange.SetCooked -> batch.update(
                doc(change.mealId),
                mapOf(
                    "cookedAt" to if (change.cooked) FieldValue.serverTimestamp() else null,
                    "cookedBy" to if (change.cooked) actor.uid else null,
                ) + stamp,
            )
        }
        return true
    }

    /** The rules mark a meal cooked once (DESIGN 8.5): a refused Cooked lost to another phone's when it is cooked now. */
    override suspend fun whyRefused(
        db: FirebaseFirestore,
        householdId: String,
        chunk: List<HouseholdChange>,
    ): AppError? {
        val cooked = chunk.filterIsInstance<MealPlanChange.SetCooked>().firstOrNull { it.cooked } ?: return null
        val doc = db.document("${meals(householdId)}/${cooked.mealId}").get(Source.SERVER).awaitMapped()
        if (doc.get("cookedAt") == null) return null
        return AppError.LostRace(AppError.LostRace.Race.Cooked, doc.getString("cookedBy"))
    }

    private fun fields(meal: PlannedMeal): Map<String, Any?> = mapOf(
        "date" to meal.date.toString(),
        "slot" to meal.slot.wire,
        "position" to meal.position,
        "recipeId" to meal.recipeId,
        "title" to meal.title,
        "servings" to meal.servings,
        "cooks" to meal.cooks,
        "note" to meal.note,
        "cookedAt" to meal.cookedAt?.let { Timestamp(it) },
        "cookedBy" to meal.cookedBy,
    )
}
