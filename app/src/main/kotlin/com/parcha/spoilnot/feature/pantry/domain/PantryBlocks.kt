package com.parcha.spoilnot.feature.pantry.domain

/** The heading over a block of the pantry list (DESIGN 19.5, 19.12). */
sealed interface PantryHeading {
    /** The default view's first group: the Needs attention set (5.4). */
    data object UseFirst : PantryHeading

    /** The default view's second group: everything not in Use first. */
    data object EverythingElse : PantryHeading

    data class At(val location: Location) : PantryHeading

    data class Kind(val category: Category) : PantryHeading
}

/** One row of a block: the line that folds the expired backlog, or an item. */
sealed interface PantryLine {
    /** Unique within its block. */
    val key: String

    /** "65 expired items" with Show or Hide (after v1, DESIGN 5.2). */
    data class Folded(val count: Int) : PantryLine {
        override val key: String get() = "expired-fold"
    }

    data class Item(val item: PantryItem) : PantryLine {
        override val key: String get() = item.key
    }
}

/** Rows drawn as one rounded group (DESIGN 19.5), under [heading] when it has one. [key] is unique in the list. */
data class PantryBlock(val key: String, val heading: PantryHeading?, val lines: List<PantryLine>)

/**
 * The pantry list as the screen draws it, built from [PantryItems.build]'s groups. Pure, so it is tested without a
 * screen.
 *
 * In the default view ([PantryQuery.isDefaultView]) the list splits into Use first (every purchase that has expired or
 * expires within [NeedsAttention.DAYS] days) and Everything else; when nothing needs attention it is one block without
 * a heading. A backlog of [ExpiredFold.MIN] or more expired purchases folds into one line, the first row of Use first,
 * and its rows follow that line while [showExpired]. Any other view keeps its own order and grouping, with the folded
 * line, when there is one, as a block of its own at the top.
 */
object PantryBlocks {
    const val USE_FIRST = "use-first"
    const val EVERYTHING_ELSE = "everything-else"
    const val ALL = "all"
    const val EXPIRED = "expired"

    fun of(groups: List<PantryItemGroup>, query: PantryQuery, showExpired: Boolean): List<PantryBlock> {
        val fold = ExpiredFold.of(groups, query)
        return if (query.isDefaultView) {
            sections(groups.flatMap { it.items }, fold, showExpired)
        } else {
            buildList {
                fold?.let { add(PantryBlock(EXPIRED, null, foldLines(it, showExpired))) }
                (fold?.rest ?: groups).forEach { group -> add(block(group)) }
            }
        }
    }

    private fun sections(items: List<PantryItem>, fold: ExpiredFold?, showExpired: Boolean): List<PantryBlock> {
        if (items.isEmpty()) return emptyList()
        val (first, rest) = items.partition(NeedsAttention::includes)
        if (first.isEmpty()) return listOf(PantryBlock(ALL, null, items.map(PantryLine::Item)))
        val firstLines = if (fold == null) {
            first.map(PantryLine::Item)
        } else {
            val folded = fold.expired.mapTo(HashSet()) { it.key }
            foldLines(fold, showExpired) + first.filterNot { it.key in folded }.map(PantryLine::Item)
        }
        return listOfNotNull(
            PantryBlock(USE_FIRST, PantryHeading.UseFirst, firstLines),
            rest.takeIf { it.isNotEmpty() }
                ?.let { PantryBlock(EVERYTHING_ELSE, PantryHeading.EverythingElse, it.map(PantryLine::Item)) },
        )
    }

    private fun foldLines(fold: ExpiredFold, showExpired: Boolean): List<PantryLine> =
        listOf(PantryLine.Folded(fold.expired.size)) +
            if (showExpired) fold.expired.map(PantryLine::Item) else emptyList()

    private fun block(group: PantryItemGroup): PantryBlock {
        val heading = group.location?.let(PantryHeading::At) ?: group.category?.let(PantryHeading::Kind)
        val key = group.location?.let { "location-${it.name}" } ?: group.category?.let { "category-${it.name}" } ?: ALL
        return PantryBlock(key, heading, group.items.map(PantryLine::Item))
    }
}
