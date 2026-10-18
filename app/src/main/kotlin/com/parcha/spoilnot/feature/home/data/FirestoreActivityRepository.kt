package com.parcha.spoilnot.feature.home.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.retryAfterOwnWrites
import com.parcha.spoilnot.core.firebase.snapshotFlow
import com.parcha.spoilnot.feature.home.domain.ActivityEvent
import com.parcha.spoilnot.feature.home.domain.ActivityRepository
import com.parcha.spoilnot.feature.home.domain.ActivityType
import com.parcha.spoilnot.feature.pantry.domain.DiscardReason
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreActivityRepository @Inject constructor(private val firestore: FirestoreProvider) : ActivityRepository {
    // A single-field order uses the automatic index (DESIGN 8.4).
    override fun observeRecent(householdId: String, limit: Int): Flow<List<ActivityEvent>> = firestore.instance
        .flatMapLatest { db ->
            db.collection("households/$householdId/activity")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .snapshotFlow()
                .retryAfterOwnWrites(db)
        }
        .map { snapshot -> snapshot.documents.mapNotNull { it.toEvent() } }
        .distinctUntilChanged()

    private fun DocumentSnapshot.toEvent(): ActivityEvent? {
        val type = ActivityType.fromWire(getString("type")) ?: return null
        val params = get("params") as? Map<*, *> ?: emptyMap<String, Any>()
        return ActivityEvent(
            id = id,
            type = type,
            actorUid = getString("actorUid").orEmpty(),
            actorName = getString("actorName").orEmpty(),
            name = params["name"] as? String,
            quantity = (params["quantity"] as? Number)?.toDouble(),
            reason = DiscardReason.fromWire(params["reason"] as? String),
            listName = params["listName"] as? String,
            count = (params["count"] as? Number)?.toInt(),
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant(),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface HomeDataModule {
    @Binds
    fun activityRepository(impl: FirestoreActivityRepository): ActivityRepository
}
