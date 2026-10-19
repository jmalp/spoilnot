package com.parcha.spoilnot.feature.insights.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.parcha.spoilnot.core.firebase.FirestoreChangeMapper
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.Stamps
import com.parcha.spoilnot.core.firebase.retryAfterOwnWrites
import com.parcha.spoilnot.core.firebase.snapshotFlow
import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.feature.insights.data.FirestoreStatsRepository.Companion.FIELD_BY_DAY
import com.parcha.spoilnot.feature.insights.data.FirestoreStatsRepository.Companion.FIELD_USED
import com.parcha.spoilnot.feature.insights.domain.MonthStats
import com.parcha.spoilnot.feature.insights.domain.StatsChange
import com.parcha.spoilnot.feature.insights.domain.StatsRepository
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** households/{hid}/stats/{yyyy-MM} (DESIGN 8.2): one document per month with counters. */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreStatsRepository @Inject constructor(private val firestore: FirestoreProvider) : StatsRepository {
    override fun observeMonths(householdId: String, from: YearMonth): Flow<List<MonthStats>> = firestore.instance
        .flatMapLatest { db ->
            // Month ids sort as text, so a document-id range reads only the months asked for.
            db.collection(stats(householdId))
                .whereGreaterThanOrEqualTo(FieldPath.documentId(), from.toString())
                .snapshotFlow()
                .retryAfterOwnWrites(db)
        }
        .map { snapshot -> snapshot.documents.mapNotNull { it.toMonthStats() }.sortedBy { it.month } }
        .distinctUntilChanged()

    private fun DocumentSnapshot.toMonthStats(): MonthStats? {
        val month = runCatching { YearMonth.parse(id) }.getOrNull() ?: return null
        val byDay = (get(FIELD_BY_DAY) as? Map<*, *>).orEmpty()
            .mapNotNull { (day, count) -> (day as? String)?.toIntOrNull()?.let { it to count.asCount() } }
            .filter { (day, count) -> day in 1..31 && count > 0 }
            .toMap()
        return MonthStats(
            month = month,
            used = get(FIELD_USED).asCount(),
            thrown = DiscardReason.entries.associateWith { get(it.wire).asCount() }.filterValues { it > 0 },
            thrownByDay = byDay,
        )
    }

    /** Counters are never negative on screen, even if a race left one below zero. */
    private fun Any?.asCount(): Int = ((this as? Number)?.toInt() ?: 0).coerceAtLeast(0)

    companion object {
        const val FIELD_USED = "used"
        const val FIELD_BY_DAY = "thrownByDay"

        fun stats(hid: String) = "households/$hid/stats"
    }
}

/** Firestore writes for [StatsChange]s: merged increments, so two phones counting at once both count. */
class StatsChangeMapper @Inject constructor() : FirestoreChangeMapper {
    override fun write(
        batch: WriteBatch,
        db: FirebaseFirestore,
        householdId: String,
        actor: Actor,
        change: HouseholdChange,
    ): Boolean {
        if (change !is StatsChange) return false
        val fields: Map<String, Any> = when (change) {
            is StatsChange.CountUsedUp -> mapOf(FIELD_USED to FieldValue.increment(change.delta.toLong()))

            is StatsChange.CountThrownAway -> {
                val by = FieldValue.increment(change.delta.toLong())
                mapOf(change.reason.wire to by, FIELD_BY_DAY to mapOf(change.day.toString() to by))
            }
        }
        batch.set(
            db.document("${FirestoreStatsRepository.stats(householdId)}/${change.month}"),
            fields + Stamps.updated(actor),
            SetOptions.merge(),
        )
        return true
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface InsightsDataModule {
    @Binds
    fun statsRepository(impl: FirestoreStatsRepository): StatsRepository

    @Binds
    @IntoSet
    fun statsChangeMapper(impl: StatsChangeMapper): FirestoreChangeMapper
}
