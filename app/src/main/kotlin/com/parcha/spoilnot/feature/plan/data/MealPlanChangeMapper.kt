package com.parcha.spoilnot.feature.plan.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.parcha.spoilnot.core.firebase.FirestoreChangeMapper
import com.parcha.spoilnot.core.firebase.Stamps
import com.parcha.spoilnot.core.firebase.Tombstones
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

            is MealPlanChange.Restore -> batch.set(
                doc(change.meal.id),
                fields(change.meal) + stamp + mapOf(
                    "createdAt" to (change.meal.createdAt?.let { Timestamp(it) } ?: FieldValue.serverTimestamp()),
                    "createdBy" to (change.meal.createdBy ?: actor.uid),
                ),
            )

            is MealPlanChange.Update -> batch.update(
                doc(change.mealId),
                mapOf("servings" to change.servings, "cooks" to change.cooks, "note" to change.note) + stamp,
            )

            is MealPlanChange.Delete -> batch.set(doc(change.mealId), Tombstones.fields(actor), SetOptions.merge())
        }
        return true
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
