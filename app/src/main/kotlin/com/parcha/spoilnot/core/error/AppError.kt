package com.parcha.spoilnot.core.error

/**
 * Typed errors thrown by repositories (DESIGN 7.5). The UI maps them to messages; nothing outside data/ sees
 * Firebase exception types.
 */
sealed class AppError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    class PermissionDenied(cause: Throwable? = null) : AppError("Permission denied", cause)

    class QuotaExceeded(cause: Throwable? = null) : AppError("Daily free quota reached", cause)

    class Offline(cause: Throwable? = null) : AppError("Offline", cause)

    class NotFound(cause: Throwable? = null) : AppError("Not found", cause)

    class Conflict(cause: Throwable? = null) : AppError("Conflict", cause)

    /** Another pantry product already has this name. */
    class NameTaken : AppError("Name taken")

    // Accounts
    class InvalidCredentials(cause: Throwable? = null) : AppError("Invalid credentials", cause)

    class EmailInUse(cause: Throwable? = null) : AppError("Email already in use", cause)

    class InvalidEmail(cause: Throwable? = null) : AppError("Invalid email", cause)

    class WeakPassword(cause: Throwable? = null) : AppError("Weak password", cause)

    class TooManyAttempts(cause: Throwable? = null) : AppError("Too many attempts", cause)

    class RecentLoginRequired(cause: Throwable? = null) : AppError("Recent sign-in required", cause)

    // Households
    class InviteCodeInactive : AppError("Invite code is not active")

    class OwnerMustTransferFirst : AppError("Owner must transfer ownership or delete the household")

    class Unknown(cause: Throwable? = null) : AppError(cause?.message, cause)
}
