package com.parcha.spoilnot.feature.settings.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.GeoPoint
import com.parcha.spoilnot.BuildConfig
import com.parcha.spoilnot.core.firebase.FirestoreProvider
import com.parcha.spoilnot.core.firebase.awaitMapped
import com.parcha.spoilnot.feature.settings.domain.ExportData
import com.parcha.spoilnot.feature.settings.domain.ExportDoc
import com.parcha.spoilnot.feature.settings.domain.ExportFormat
import com.parcha.spoilnot.feature.settings.domain.HouseholdExporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the household's collections once (about 130 reads for a typical household, DESIGN 9.7). */
class FirestoreHouseholdExporter @Inject constructor(
    private val firestore: FirestoreProvider,
    private val clock: Clock,
) : HouseholdExporter {
    override suspend fun export(householdId: String): String {
        val db = firestore.current
        val base = "households/$householdId"
        suspend fun all(path: String) = db.collection(path).get().awaitMapped().documents.map { it.toExport() }
        val lists = db.collection("$base/groceryLists").get().awaitMapped().documents
        val data = ExportData(
            household = db.document(base).get().awaitMapped().takeIf { it.exists() }?.toExport(),
            members = all("$base/members"),
            products = all("$base/products"),
            stock = all("$base/stock"),
            groceryLists = lists.map { list -> list.toExport() to all("$base/groceryLists/${list.id}/items") },
            recipes = all("$base/recipes"),
        )
        return withContext(Dispatchers.Default) {
            ExportFormat.write(data, clock.instant(), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }
    }

    private fun DocumentSnapshot.toExport() = ExportDoc(id, data.orEmpty().mapValues { plain(it.value) })

    /** Firestore types to plain values. */
    private fun plain(v: Any?): Any? = when (v) {
        is Timestamp -> v.toInstant()
        is GeoPoint -> mapOf("latitude" to v.latitude, "longitude" to v.longitude)
        is Map<*, *> -> v.mapValues { plain(it.value) }
        is List<*> -> v.map(::plain)
        else -> v
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface SettingsDataModule {
    @Binds
    fun householdExporter(impl: FirestoreHouseholdExporter): HouseholdExporter
}
