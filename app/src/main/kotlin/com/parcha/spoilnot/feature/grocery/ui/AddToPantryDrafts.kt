package com.parcha.spoilnot.feature.grocery.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import com.parcha.spoilnot.feature.pantry.domain.Location
import com.parcha.spoilnot.feature.pantry.domain.QuantityUnit
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the person set on one line of the Add to pantry sheet (after v1, REL-08). */
data class EntryEdit(
    val quantity: String,
    val unit: QuantityUnit,
    val expiry: LocalDate?,
    val location: Location,
    val skip: Boolean,
)

/**
 * The Add to pantry sheet's edits, by item id (after v1, REL-08). They live in the screen's [SavedStateHandle], so
 * they stay through rotation and process death, and apart from the list of items: when someone else checks off or
 * unchecks an item, the edited lines keep what was typed, new lines start from their defaults, and lines that left the
 * cart aren't shown or added. Lines nobody touched follow their item and product. Cleared when the sheet closes.
 */
class AddToPantryDrafts(private val handle: SavedStateHandle = SavedStateHandle()) {
    private val edits = mutableStateMapOf<String, EntryEdit>()
    private val expanded = mutableStateOf(emptySet<String>())

    init {
        handle.get<String>(KEY)?.let { saved ->
            runCatching { Json.decodeFromString<Saved>(saved) }.getOrNull()?.let { s ->
                s.edits.forEach { (id, e) -> e.toEdit()?.let { edits[id] = it } }
                expanded.value = s.expanded.toSet()
            }
        }
    }

    fun edit(itemId: String): EntryEdit? = edits[itemId]

    fun isExpanded(itemId: String): Boolean = itemId in expanded.value

    fun set(itemId: String, edit: EntryEdit) {
        edits[itemId] = edit
        save()
    }

    fun setExpanded(itemId: String, open: Boolean) {
        expanded.value = if (open) expanded.value + itemId else expanded.value - itemId
        save()
    }

    fun clear() {
        edits.clear()
        expanded.value = emptySet()
        handle.remove<String>(KEY)
    }

    private fun save() {
        handle[KEY] = Json.encodeToString(
            Saved.serializer(),
            Saved(edits.mapValues { (_, e) -> SavedEdit.of(e) }, expanded.value.toList()),
        )
    }

    @Serializable
    private data class Saved(val edits: Map<String, SavedEdit>, val expanded: List<String>)

    @Serializable
    private data class SavedEdit(
        val quantity: String,
        val unit: String,
        val expiry: String?,
        val location: String,
        val skip: Boolean,
    ) {
        fun toEdit(): EntryEdit? = runCatching {
            EntryEdit(
                quantity,
                QuantityUnit.fromWire(unit),
                expiry?.let(LocalDate::parse),
                Location.fromWire(location),
                skip,
            )
        }.getOrNull()

        companion object {
            fun of(e: EntryEdit) = SavedEdit(e.quantity, e.unit.wire, e.expiry?.toString(), e.location.wire, e.skip)
        }
    }

    private companion object {
        const val KEY = "addToPantryDrafts"
    }
}
