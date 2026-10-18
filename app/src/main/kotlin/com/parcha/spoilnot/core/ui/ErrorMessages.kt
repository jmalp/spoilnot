package com.parcha.spoilnot.core.ui

import androidx.annotation.StringRes
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.error.AppError

/** What to tell the user about a failure: what happened and what to do (DESIGN 6.9). */
@StringRes
fun Throwable.messageRes(): Int = when (this) {
    is AppError.InvalidCredentials -> R.string.error_invalid_credentials
    is AppError.EmailInUse -> R.string.error_email_in_use
    is AppError.InvalidEmail -> R.string.error_email_invalid
    is AppError.WeakPassword -> R.string.error_weak_password
    is AppError.TooManyAttempts -> R.string.error_too_many_attempts
    is AppError.RecentLoginRequired -> R.string.error_recent_login
    is AppError.Offline -> R.string.error_offline
    is AppError.PermissionDenied -> R.string.error_permission
    is AppError.QuotaExceeded -> R.string.error_quota
    is AppError.NotFound -> R.string.error_not_found
    is AppError.Conflict -> R.string.error_conflict
    is AppError.NameTaken -> R.string.error_name_taken
    is AppError.InviteCodeInactive -> R.string.error_code_inactive
    is AppError.OwnerMustTransferFirst -> R.string.error_owner_must_transfer
    else -> R.string.error_unknown
}
