package com.parcha.spoilnot.feature.recipes.domain

/** The Create manually and Edit form as typed (DESIGN 5.5): one ingredient and one step per line. */
data class RecipeForm(
    val title: String = "",
    val imageUrl: String = "",
    val servings: String = "",
    val prepMinutes: String = "",
    val cookMinutes: String = "",
    val totalMinutes: String = "",
    val ingredients: String = "",
    val steps: String = "",
    val notes: String = "",
    val tags: String = "",
) {
    enum class Problem { TitleMissing, TitleTooLong, ImageUrl, Number }

    fun problems(): Set<Problem> = buildSet {
        val t = title.trim()
        if (t.isEmpty()) add(Problem.TitleMissing)
        if (t.length > RecipeLimits.TITLE) add(Problem.TitleTooLong)
        if (imageUrl.isNotBlank() && !SourceUrl.isWebUrl(imageUrl.trim())) add(Problem.ImageUrl)
        if (listOf(servings, prepMinutes, cookMinutes, totalMinutes).any { it.isNotBlank() && number(it) == null }) {
            add(Problem.Number)
        }
    }

    /** The content to save; [base] keeps what the form does not show (link, source, description, import method). */
    fun toContent(base: RecipeContent?): RecipeContent {
        val prep = number(prepMinutes)
        val cook = number(cookMinutes)
        val total = number(totalMinutes) ?: if (prep != null || cook != null) (prep ?: 0) + (cook ?: 0) else null
        val servingsValue = number(servings)
        val keepServingsText = base?.servings == servingsValue
        return (base ?: RecipeContent(title = "")).copy(
            title = title.trim(),
            imageUrl = imageUrl.trim().ifEmpty { null },
            servings = servingsValue,
            servingsText = base?.servingsText?.takeIf { keepServingsText },
            prepMinutes = prep,
            cookMinutes = cook,
            totalMinutes = total,
            ingredients = RecipeLines.ingredients(ingredients),
            steps = RecipeLines.steps(steps),
            notes = notes.trim().ifEmpty { null },
            tags = RecipeLines.tags(tags),
        )
    }

    companion object {
        fun from(c: RecipeContent) = RecipeForm(
            title = c.title,
            imageUrl = c.imageUrl.orEmpty(),
            servings = c.servings?.toString().orEmpty(),
            prepMinutes = c.prepMinutes?.toString().orEmpty(),
            cookMinutes = c.cookMinutes?.toString().orEmpty(),
            totalMinutes = c.totalMinutes?.toString().orEmpty(),
            ingredients = RecipeLines.ingredientsToText(c.ingredients),
            steps = RecipeLines.stepsToText(c.steps),
            notes = c.notes.orEmpty(),
            tags = c.tags.joinToString(", "),
        )

        private fun number(text: String): Int? = text.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()?.takeIf {
            it in
                1..10_000
        }
    }
}
