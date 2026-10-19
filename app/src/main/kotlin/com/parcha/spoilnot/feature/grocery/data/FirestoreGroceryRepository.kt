package com.parcha.spoilnot.feature.grocery.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.SyncLog
import com.parcha.spoilnot.core.firebase.Tombstones.isTombstone
import com.parcha.spoilnot.core.firebase.awaitMapped
import com.parcha.spoilnot.core.firebase.liveDocuments
import com.parcha.spoilnot.core.firebase.retryAfterOwnWrites
import com.parcha.spoilnot.core.firebase.snapshotFlow
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryList
import com.parcha.spoilnot.feature.grocery.domain.GroceryRepository
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreGroceryRepository @Inject constructor(
    private val firestore: FirestoreProvider,
    private val syncLog: SyncLog,
) : GroceryRepository {
    override fun observeLists(householdId: String): Flow<List<GroceryList>> = firestore.instance
        .flatMapLatest { db -> db.collection(lists(householdId)).snapshotFlow().retryAfterOwnWrites(db) }
        .map { snapshot ->
            snapshot.documents
                .map { GroceryList(it.id, it.getString("name").orEmpty(), it.getBoolean("isDefault") == true) }
                .sortedWith(compareByDescending<GroceryList> { it.isDefault }.thenBy { it.name.lowercase() })
        }
        .distinctUntilChanged()

    override fun observeItems(householdId: String, listId: String): Flow<List<GroceryItem>> = firestore.instance
        // Changes-only listeners (DESIGN 9.8); each item keeps its pending-sync flag from the cache.
        .flatMapLatest { db -> db.collection(items(householdId, listId)).liveDocuments(db, syncLog) }
        .map { live -> live.documents.map { it.toItem() } }
        .distinctUntilChanged()

    override suspend fun isCached(householdId: String, listId: String, itemId: String): Boolean = try {
        firestore.current.document("${items(householdId, listId)}/$itemId").get(Source.CACHE).awaitMapped()
            .let { it.exists() && !it.isTombstone() }
    } catch (e: Exception) {
        // Not in the cache at all.
        false
    }

    override suspend fun itemIds(householdId: String, listId: String): List<String> =
        firestore.current.collection(items(householdId, listId)).get(Source.SERVER).awaitMapped().documents.map {
            it.id
        }

    internal companion object {
        fun lists(hid: String) = "households/$hid/groceryLists"

        fun items(hid: String, listId: String) = "households/$hid/groceryLists/$listId/items"

        // New fields are optional on read with defaults (DESIGN 8.1); `checked` absent means false (DESIGN 8.2).
        fun DocumentSnapshot.toItem() = GroceryItem(
            id = id,
            name = getString("name").orEmpty(),
            nameKey = getString("nameKey").orEmpty(),
            quantity = getDouble("quantity"),
            unit = getString("unit")?.let { QuantityUnit.fromWire(it) },
            note = getString("note"),
            category = getString("category")?.let { Category.fromWire(it) },
            checked = getBoolean("checked") == true,
            checkedBy = getString("checkedBy"),
            source = ItemSource.fromWire(getString("source")),
            productId = getString("productId"),
            recipeId = getString("recipeId"),
            createdBy = getString("createdBy"),
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant(),
            pendingSync = metadata.hasPendingWrites(),
            aliasOf = getString("aliasOf"),
        )
    }
}
