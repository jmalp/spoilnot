package com.parcha.spoilnot.feature.plan.data

import com.google.firebase.firestore.DocumentSnapshot
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.SyncLog
import com.parcha.spoilnot.core.firebase.liveDocuments
import com.parcha.spoilnot.feature.plan.domain.MealPlanData
import com.parcha.spoilnot.feature.plan.domain.MealPlanRepository
import com.parcha.spoilnot.feature.plan.domain.MealSlot
import com.parcha.spoilnot.feature.plan.domain.PlannedMeal
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreMealPlanRepository @Inject constructor(
    private val firestore: FirestoreProvider,
    private val syncLog: SyncLog,
) : MealPlanRepository {
    // Changes-only, limited to the dates on screen (DESIGN 9.8): a week seen for the first time is read in full,
    // then only what changed. Dates are 'YYYY-MM-DD' strings, so a string range is a date range.
    override fun observe(householdId: String, from: LocalDate, to: LocalDate): Flow<MealPlanData> = firestore.instance
        .flatMapLatest { db ->
            val meals = db.collection(meals(householdId))
            val window = meals.whereGreaterThanOrEqualTo("date", from.toString())
                .whereLessThanOrEqualTo("date", to.toString())
            meals.liveDocuments(db, syncLog, scope = window, key = "${meals.path}?date=$from..$to")
        }
        .map { live -> MealPlanData(live.documents.mapNotNull { it.toMeal() }, live.fromCache) }
        .distinctUntilChanged()

    internal companion object {
        fun meals(hid: String) = "households/$hid/meals"

        // New fields are optional on read with defaults (DESIGN 8.1); a meal with no valid date or slot is skipped.
        fun DocumentSnapshot.toMeal(): PlannedMeal? {
            val date = getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            val slot = MealSlot.fromWire(getString("slot")) ?: return null
            return PlannedMeal(
                id = id,
                date = date,
                slot = slot,
                position = getLong("position") ?: 0L,
                recipeId = getString("recipeId"),
                title = getString("title").orEmpty(),
                servings = getLong("servings")?.toInt(),
                cooks = (get("cooks") as? List<*>)?.filterIsInstance<String>().orEmpty(),
                note = getString("note"),
                cookedAt = getTimestamp("cookedAt")?.toDate()?.toInstant(),
                cookedBy = getString("cookedBy"),
                createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant(),
                createdBy = getString("createdBy"),
                pendingSync = metadata.hasPendingWrites(),
            )
        }
    }
}
