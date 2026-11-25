package com.parcha.spoilnot.feature.grocery.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.parcha.spoilnot.core.coroutines.ApplicationScope
import com.parcha.spoilnot.core.coroutines.SharedByKey
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.SyncLog
import com.parcha.spoilnot.core.firebase.Tombstones.isTombstone
import com.parcha.spoilnot.core.firebase.awaitMapped
import com.parcha.spoilnot.core.firebase.boolean
import com.parcha.spoilnot.core.firebase.cachedLiveDocuments
import com.parcha.spoilnot.core.firebase.double
import com.parcha.spoilnot.core.firebase.instant
import com.parcha.spoilnot.core.firebase.liveDocuments
import com.parcha.spoilnot.core.firebase.mapEach
import com.parcha.spoilnot.core.firebase.retryAfterOwnWrites
import com.parcha.spoilnot.core.firebase.snapshotFlow
import com.parcha.spoilnot.core.firebase.string
import com.parcha.spoilnot.core.write.ReadCaps
import com.parcha.spoilnot.core.write.capTo
import com.parcha.spoilnot.feature.grocery.domain.GroceryItem
import com.parcha.spoilnot.feature.grocery.domain.GroceryList
import com.parcha.spoilnot.feature.grocery.domain.GroceryRepository
import com.parcha.spoilnot.feature.grocery.domain.ItemSource
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreGroceryRepository(
    private val firestore: FirestoreProvider,
    private val syncLog: SyncLog,
    scope: CoroutineScope,
    /** How long the shared listeners stay after the last collector leaves; two-phone tests use 0 to "close the app". */
    stopTimeoutMs: Long,
) : GroceryRepository {
    @Inject constructor(firestore: FirestoreProvider, syncLog: SyncLog, @ApplicationScope scope: CoroutineScope) :
        this(firestore, syncLog, scope, SharedByKey.STOP_TIMEOUT_MS)

    /** One pair of item listeners per list and phone, shared by every screen (after v1, DESIGN 9.1). */
    private val sharedItems = SharedByKey<String, List<GroceryItem>>(scope, stopTimeoutMs)

    override fun observeLists(householdId: String): Flow<List<GroceryList>> = firestore.instance
        .flatMapLatest { db -> db.collection(lists(householdId)).snapshotFlow().retryAfterOwnWrites(db) }
        .map { snapshot -> snapshot.documents.toLists() }
        .distinctUntilChanged()

    override fun observeCachedLists(householdId: String): Flow<List<GroceryList>> = firestore.instance
        .flatMapLatest { db -> db.collection(lists(householdId)).cachedLiveDocuments() }
        .map { it.toLists() }
        .distinctUntilChanged()

    override fun observeItems(householdId: String, listId: String): Flow<List<GroceryItem>> =
        sharedItems.get(items(householdId, listId)) {
            firestore.instance
                // Changes-only listeners (DESIGN 9.8); each item keeps its pending-sync flag from the cache.
                .flatMapLatest { db -> db.collection(items(householdId, listId)).liveDocuments(db, syncLog) }
                .map { live -> live.documents.mapEach { it.toItem() } }
                .distinctUntilChanged()
        }

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

        fun List<DocumentSnapshot>.toLists() = mapEach { it.toList() }
            .sortedWith(compareByDescending<GroceryList> { it.isDefault }.thenBy { it.name.lowercase() })

        fun DocumentSnapshot.toList() = GroceryList(
            id,
            string("name").orEmpty().capTo(ReadCaps.LIST_NAME),
            boolean("isDefault") == true,
            (get("aisleOrder") as? List<*>).orEmpty().mapNotNull { wire ->
                (wire as? String)?.let { w -> Category.entries.firstOrNull { c -> c.wire == w } }
            },
        )

        // New fields are optional on read with defaults (DESIGN 8.1); `checked` absent means false (DESIGN 8.2).
        fun DocumentSnapshot.toItem() = GroceryItem(
            id = id,
            name = string("name").orEmpty().capTo(ReadCaps.NAME),
            // Keys written before one whitespace definition (after v1, WEB-06) are normalised here, not migrated.
            nameKey = NameKey.of(string("nameKey").orEmpty()),
            quantity = double("quantity"),
            unit = string("unit")?.let { QuantityUnit.fromWire(it) },
            note = string("note"),
            category = string("category")?.let { Category.fromWire(it) },
            checked = boolean("checked") == true,
            checkedBy = string("checkedBy"),
            source = ItemSource.fromWire(string("source")),
            productId = string("productId"),
            recipeId = string("recipeId"),
            createdBy = string("createdBy"),
            createdAt = instant("createdAt"),
            pendingSync = metadata.hasPendingWrites(),
            aliasOf = string("aliasOf"),
        )
    }
}
