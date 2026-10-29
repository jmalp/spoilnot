package com.parcha.spoilnot.feature.settings.domain

import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Settings > Data > Export (DESIGN 13.4): products, stock, lists, recipes, Insights counts and meals as JSON. */
interface HouseholdExporter {
    /** The household's data as a JSON document. Reads the server when online, the local copy otherwise. */
    suspend fun export(householdId: String): String
}

/** A document read for the export: its id and plain values (maps, lists, strings, numbers, Instants). */
data class ExportDoc(val id: String, val fields: Map<String, Any?>)

/** Everything the export contains. */
data class ExportData(
    val household: ExportDoc?,
    val members: List<ExportDoc>,
    val products: List<ExportDoc>,
    val stock: List<ExportDoc>,
    /** Each list with its items. */
    val groceryLists: List<Pair<ExportDoc, List<ExportDoc>>>,
    val recipes: List<ExportDoc>,
    /** Insights counts, one document per month (after v1; older files have none). */
    val stats: List<ExportDoc> = emptyList(),
    /** Planned meals (after v1, DESIGN 5.9; older files have none). */
    val meals: List<ExportDoc> = emptyList(),
)

object ExportFormat {
    const val VERSION = 1
    private val json = Json { prettyPrint = true }

    fun fileName(today: LocalDate) = "spoilnot-export-$today.json"

    fun write(data: ExportData, exportedAt: Instant, appVersion: String): String {
        val root = JsonObject(
            mapOf(
                "format" to JsonPrimitive("spoilnot-export"),
                "formatVersion" to JsonPrimitive(VERSION),
                "appVersion" to JsonPrimitive(appVersion),
                "exportedAt" to JsonPrimitive(exportedAt.toString()),
                "household" to (data.household?.let(::doc) ?: JsonNull),
                "members" to JsonArray(data.members.map(::doc)),
                "products" to JsonArray(data.products.map(::doc)),
                "stock" to JsonArray(data.stock.map(::doc)),
                "groceryLists" to JsonArray(
                    data.groceryLists.map { (list, items) ->
                        JsonObject(doc(list) + ("items" to JsonArray(items.map(::doc))))
                    },
                ),
                "recipes" to JsonArray(data.recipes.map(::doc)),
                "stats" to JsonArray(data.stats.map(::doc)),
                "meals" to JsonArray(data.meals.map(::doc)),
            ),
        )
        return json.encodeToString(JsonElement.serializer(), root)
    }

    private fun doc(d: ExportDoc) = JsonObject(
        mapOf("id" to JsonPrimitive(d.id)) + d.fields.toSortedMap().mapValues {
            value(it.value)
        },
    )

    /** Plain values to JSON; times become ISO 8601 strings. */
    fun value(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is Instant -> JsonPrimitive(v.toString())
        is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to value(x) }.toSortedMap())
        is List<*> -> JsonArray(v.map(::value))
        else -> JsonPrimitive(v.toString())
    }
}
