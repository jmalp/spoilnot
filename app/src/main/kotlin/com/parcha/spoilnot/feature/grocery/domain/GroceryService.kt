package com.parcha.spoilnot.feature.grocery.domain

import com.parcha.spoilnot.core.write.Actor
import com.parcha.spoilnot.core.write.HouseholdChange
import com.parcha.spoilnot.core.write.HouseholdWriter
import com.parcha.spoilnot.core.write.Undo
import com.parcha.spoilnot.feature.pantry.domain.AddPlan
import com.parcha.spoilnot.feature.pantry.domain.Category
import com.parcha.spoilnot.feature.pantry.domain.DateType
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.NameKey
import com.parcha.spoilnot.feature.pantry.domain.PantryChange
import com.parcha.spoilnot.feature.pantry.domain.PantryData
import com.parcha.spoilnot.feature.pantry.domain.PantryLimits
import com.parcha.spoilnot.feature.pantry.domain.PantryService
import com.parcha.spoilnot.feature.pantry.domain.Product
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import com.parcha.spoilnot.feature.pantry.domain.StockDraft
import java.time.LocalDate
import javax.inject.Inject

/** "2 kg rice" in the quick-add field: an optional leading quantity and unit, then the name. */
data class QuickAdd(val name: String, val quantity: Double?, val unit: QuantityUnit?)

object QuickAddParser {
    private val NUMBER = Regex("""^(\d+(?:[.,]\d+)?)\s+(.*)$""")

    fun parse(text: String): QuickAdd {
        val clean = NameKey.clean(text)
        val match = NUMBER.matchEntire(clean) ?: return QuickAdd(clean, null, null)
        val quantity = match.groupValues[1].replace(',', '.').toDouble()
        val rest = match.groupValues[2]
        val unit = QuantityUnit.entries
            .sortedByDescending { it.wire.length }
            .firstOrNull { u -> rest.lowercase().startsWith(u.wire.replace('_', ' ') + " ") }
        val name = if (unit == null) rest else rest.drop(unit.wire.length).trim()
        return if (name.isEmpty()) QuickAdd(clean, null, null) else QuickAdd(name, quantity, unit)
    }
}

/** Outcome of the quick-add field. */
sealed interface QuickAddResult {
    data object Added : QuickAddResult

    /** DESIGN 5.3: the name is already on the list unchecked; ask to increase it or add anyway. */
    data class AlreadyOnList(val existing: GroceryItem, val request: QuickAdd) : QuickAddResult
}

/** One checked item in the Add to pantry sheet. */
data class PantryEntry(
    val item: GroceryItem,
    val quantity: Double,
    val unit: QuantityUnit,
    val expiryDate: LocalDate?,
    val location: Location,
    val dateType: DateType = DateType.UseBy,
)

/** Groups for the list screen: unchecked items by category (aisle), then the In cart section (DESIGN 5.3). */
data class GroceryView(val aisles: List<Pair<Category, List<GroceryItem>>>, val inCart: List<GroceryItem>)

object GroceryLayout {
    fun of(items: List<GroceryItem>): GroceryView {
        val (cart, toBuy) = items.partition { it.checked }
        val byTime = compareBy<GroceryItem, java.time.Instant?>(nullsLast()) {
            it.createdAt
        }.thenBy { it.name.lowercase() }
        val aisles = toBuy.groupBy { it.category ?: Category.Other }.toSortedMap()
            .map { (category, list) -> category to list.sortedWith(byTime) }
        return GroceryView(aisles, cart.sortedBy { it.name.lowercase() })
    }

    /** Pantry products and names already used on the list, for the quick-add suggestions. */
    fun suggestions(query: String, items: List<GroceryItem>, pantry: PantryData, limit: Int = 5): List<String> {
        val key = NameKey.of(query)
        if (key.isEmpty()) return emptyList()
        val names = (pantry.products.map { it.name } + items.map { it.name }).distinctBy { NameKey.of(it) }
        return names.filter { NameKey.of(it).contains(key) && NameKey.of(it) != key }
            .sortedBy { !NameKey.of(it).startsWith(key) }
            .take(limit)
    }
}

/** Grocery list rules (DESIGN 5.3). Plans go to the HouseholdWriter, together with pantry changes when needed. */
class GroceryService @Inject constructor(
    private val writer: HouseholdWriter,
    private val repository: GroceryRepository,
    private val pantry: PantryService,
) {
    /** The quick-add field. Links a matching pantry product, so the item gets its category (aisle). */
    suspend fun quickAdd(
        householdId: String,
        actor: Actor,
        listId: String,
        text: String,
        items: List<GroceryItem>,
        pantryData: PantryData,
        addAnyway: Boolean = false,
        listName: String? = null,
    ): QuickAddResult {
        val request = QuickAddParser.parse(text)
        require(request.name.isNotEmpty() && request.name.length <= GroceryRules.MAX_NAME_LENGTH) { "Invalid name" }
        val key = NameKey.of(request.name)
        if (!addAnyway) {
            items.firstOrNull {
                !it.checked && it.nameKey == key
            }?.let { return QuickAddResult.AlreadyOnList(it, request) }
        }
        val product = pantryData.products.firstOrNull { it.nameKey == key }
        val item = GroceryItem(
            id = writer.newId(),
            name = product?.name ?: request.name,
            nameKey = key,
            quantity = request.quantity,
            unit = request.unit ?: product?.unit?.takeIf { request.quantity != null },
            note = null,
            // Typed names get a guessed aisle, so they sort into the store order (DESIGN 5.3).
            category = product?.category ?: AisleGuess.of(request.name, pantryData.products),
            checked = false,
            checkedBy = null,
            source = ItemSource.Manual,
            productId = product?.id,
            recipeId = null,
            createdBy = actor.uid,
            createdAt = null,
        )
        writer.apply(
            householdId,
            actor,
            listOf(
                GroceryChange.CreateItem(listId, item),
                GroceryChange.LogEvent(writer.newId(), GroceryEventType.Added, item.name, listName),
            ),
        )
        return QuickAddResult.Added
    }

    /** "Increase quantity" from the Already on the list prompt. */
    suspend fun increase(householdId: String, actor: Actor, listId: String, existing: GroceryItem, request: QuickAdd) {
        val delta = request.quantity ?: 1.0
        val change = if (existing.quantity == null) {
            GroceryChange.UpdateItem(
                listId,
                existing.id,
                1.0 + delta,
                existing.unit ?: request.unit,
                existing.note,
                existing.category,
            )
        } else {
            GroceryChange.AddToItem(listId, existing.id, delta)
        }
        writer.apply(householdId, actor, listOf(change))
    }

    /** Saves [list]'s learned store order (DESIGN 5.3). */
    suspend fun setAisleOrder(householdId: String, actor: Actor, list: GroceryList, order: List<Category>) =
        writer.apply(householdId, actor, listOf(GroceryChange.SetAisleOrder(list.id, order)))

    /** Checking off is logged (DESIGN 5.6); unchecking is not. */
    suspend fun setChecked(
        householdId: String,
        actor: Actor,
        listId: String,
        item: GroceryItem,
        checked: Boolean,
        listName: String? = null,
    ) = writer.apply(
        householdId,
        actor,
        listOfNotNull(
            GroceryChange.SetChecked(listId, item.id, checked),
            if (checked) {
                GroceryChange.LogEvent(
                    writer.newId(),
                    GroceryEventType.Checked,
                    item.name,
                    listName,
                )
            } else {
                null
            },
        ),
    )

    suspend fun edit(
        householdId: String,
        actor: Actor,
        listId: String,
        item: GroceryItem,
        quantity: Double?,
        unit: QuantityUnit?,
        note: String?,
        category: Category?,
        name: String? = null,
        products: List<Product> = emptyList(),
    ) {
        val changes = mutableListOf<HouseholdChange>(
            GroceryChange.UpdateItem(listId, item.id, quantity, unit, note?.trim()?.ifEmpty { null }, category),
        )
        // Renaming (after v1, DESIGN 5.3) relinks the item to the product of its new name.
        val clean = name?.let(NameKey::clean)?.take(GroceryRules.MAX_NAME_LENGTH)?.trim()
        if (!clean.isNullOrEmpty() && clean != item.name) {
            val key = NameKey.of(clean)
            val product = products.firstOrNull { it.nameKey == key }
            changes += GroceryChange.RenameItem(listId, item.id, product?.name ?: clean, key, product?.id)
        }
        writer.apply(householdId, actor, changes)
    }

    suspend fun delete(householdId: String, actor: Actor, listId: String, item: GroceryItem): Undo {
        writer.apply(householdId, actor, listOf(GroceryChange.DeleteItem(listId, item.id)))
        return Undo(listOf(GroceryChange.RestoreItem(listId, item)))
    }

    /** Clear checked removes everything in the cart. */
    suspend fun clearChecked(householdId: String, actor: Actor, listId: String, items: List<GroceryItem>): Undo? {
        val checked = items.filter { it.checked }
        if (checked.isEmpty()) return null
        writer.apply(householdId, actor, checked.map { GroceryChange.DeleteItem(listId, it.id) })
        return Undo(checked.map { GroceryChange.RestoreItem(listId, it) })
    }

    suspend fun createList(householdId: String, actor: Actor, name: String): String {
        val id = writer.newId()
        writer.apply(householdId, actor, listOf(GroceryChange.CreateList(GroceryList(id, cleanListName(name), false))))
        return id
    }

    suspend fun renameList(householdId: String, actor: Actor, list: GroceryList, name: String) =
        writer.apply(householdId, actor, listOf(GroceryChange.RenameList(list.id, cleanListName(name))))

    /** Deletes a list and its items (no cascade in Firestore). The default list cannot be deleted. */
    suspend fun deleteList(householdId: String, actor: Actor, list: GroceryList) {
        require(!list.isDefault) { "The default list cannot be deleted" }
        val items = repository.itemIds(householdId, list.id)
        writer.apply(
            householdId,
            actor,
            items.map { GroceryChange.DeleteItem(list.id, it, tombstone = false) } + GroceryChange.DeleteList(list.id),
        )
    }

    /**
     * Done shopping (DESIGN 5.3): creates the stock batches (and any new products) for [entries], removes those
     * items and the [skipped] ones from the list, all in one batched write.
     */
    suspend fun addToPantry(
        householdId: String,
        actor: Actor,
        listId: String,
        entries: List<PantryEntry>,
        skipped: List<GroceryItem>,
        pantryData: PantryData,
    ) {
        var data = pantryData
        val changes = mutableListOf<HouseholdChange>()
        for (entry in entries) {
            val plan = pantry.planAdd(
                StockDraft(
                    name = entry.item.name,
                    quantity = entry.quantity,
                    unit = entry.unit,
                    expiryDate = entry.expiryDate,
                    dateType = entry.dateType,
                    location = entry.location,
                    category = entry.item.category ?: Category.Other,
                    note = null,
                    minQuantity = null,
                    autoAddToGrocery = false,
                    buyQuantity = null,
                ),
                data,
            )
            val learned = learnAlias(entry.item.aliasOf, plan)
            changes += learned.changes
            changes += GroceryChange.DeleteItem(listId, entry.item.id)
            data = learned.after
        }
        changes += skipped.map { GroceryChange.DeleteItem(listId, it.id) }
        if (changes.isNotEmpty()) writer.apply(householdId, actor, changes)
    }

    suspend fun undo(householdId: String, actor: Actor, undo: Undo) = writer.apply(householdId, actor, undo.changes)

    /**
     * A renamed recipe item teaches its product the recipe's name (DESIGN 5.5): a new product is created with it, an
     * existing one gets it added, unless it already has it, it is the product's own name, or the product is full.
     */
    private fun learnAlias(alias: String?, plan: AddPlan): AddPlan {
        if (alias.isNullOrEmpty()) return plan
        val created = plan.changes.filterIsInstance<PantryChange.CreateProduct>().firstOrNull()
        val product = plan.after.products.firstOrNull { it.id == plan.result.productId } ?: return plan
        if (alias == product.nameKey || alias in product.aliases || product.aliases.size >= PantryLimits.MAX_ALIASES) {
            return plan
        }
        val taught = product.copy(aliases = product.aliases + alias)
        val changes = if (created != null) {
            plan.changes.map { if (it == created) PantryChange.CreateProduct(taught) else it }
        } else {
            plan.changes + PantryChange.AddAlias(product.id, alias)
        }
        val after = plan.after.copy(products = plan.after.products.map { if (it.id == product.id) taught else it })
        return plan.copy(changes = changes, after = after)
    }

    private fun cleanListName(name: String): String {
        val clean = NameKey.clean(name)
        require(clean.isNotEmpty() && clean.length <= GroceryRules.MAX_LIST_NAME_LENGTH) { "Invalid list name" }
        return clean
    }
}
