package com.parcha.spoilnot.feature.settings.domain

import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFormatTest {
    private val created = Instant.parse("2026-10-17T12:00:00Z")

    @Test
    fun `products, stock, lists with their items and recipes, with times as ISO strings`() {
        val data = ExportData(
            household = ExportDoc("h1", mapOf("name" to "Home", "createdAt" to created)),
            members = listOf(ExportDoc("u1", mapOf("displayName" to "Sam", "role" to "owner"))),
            products = listOf(
                ExportDoc("p1", mapOf("name" to "Milk", "minQuantity" to 2.0, "recentShelfLifeDays" to listOf(5L, 7L))),
            ),
            stock = listOf(
                ExportDoc(
                    "s1",
                    mapOf(
                        "productId" to "p1",
                        "quantity" to 1.5,
                        "expiryDate" to "2026-10-20",
                        "note" to null,
                    ),
                ),
            ),
            groceryLists = listOf(
                ExportDoc("g1", mapOf("name" to "Groceries")) to
                    listOf(ExportDoc("i1", mapOf("name" to "Eggs", "checked" to false))),
            ),
            recipes = listOf(
                ExportDoc(
                    "r1",
                    mapOf(
                        "title" to "Pie",
                        "ingredients" to listOf(mapOf("raw" to "1 cup flour", "qty" to 1.0)),
                    ),
                ),
            ),
        )
        val json = Json.parseToJsonElement(ExportFormat.write(data, created, "1.0.0 (5)")).jsonObject
        assertEquals("spoilnot-export", json["format"]!!.jsonPrimitive.content)
        assertEquals("2026-10-17T12:00:00Z", json["exportedAt"]!!.jsonPrimitive.content)
        assertEquals("2026-10-17T12:00:00Z", json["household"]!!.jsonObject["createdAt"]!!.jsonPrimitive.content)
        assertEquals("Milk", json["products"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("1.5", json["stock"]!!.jsonArray[0].jsonObject["quantity"]!!.jsonPrimitive.content)
        assertEquals("null", json["stock"]!!.jsonArray[0].jsonObject["note"].toString())
        val list = json["groceryLists"]!!.jsonArray[0].jsonObject
        assertEquals("Eggs", list["items"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
        val ingredient = json["recipes"]!!.jsonArray[0].jsonObject["ingredients"]!!.jsonArray[0].jsonObject
        assertEquals("1 cup flour", ingredient["raw"]!!.jsonPrimitive.content)
    }

    @Test
    fun `file name carries the date`() {
        assertEquals("spoilnot-export-2026-10-17.json", ExportFormat.fileName(LocalDate.of(2026, 10, 17)))
    }
}
