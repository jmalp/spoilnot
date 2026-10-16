package com.parcha.spoilnot.feature.auth.domain

/** Form checks for sign-up and sign-in (DESIGN 5.1, 8.2). Pure functions; the UI maps results to messages. */
object AccountValidation {
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_DISPLAY_NAME_LENGTH = 40

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    enum class NameProblem { Blank, TooLong }

    fun displayNameProblem(name: String): NameProblem? = when {
        name.isBlank() -> NameProblem.Blank
        name.trim().length > MAX_DISPLAY_NAME_LENGTH -> NameProblem.TooLong
        else -> null
    }

    fun isEmailValid(email: String): Boolean = EMAIL.matches(email.trim())

    fun isPasswordLongEnough(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

    /** A display name for a profile that has none, such as "sam" for sam@example.com. */
    fun fallbackDisplayName(email: String?): String =
        email?.substringBefore('@')?.take(MAX_DISPLAY_NAME_LENGTH)?.ifBlank { null } ?: "Me"
}
