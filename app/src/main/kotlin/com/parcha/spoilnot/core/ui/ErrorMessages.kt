package com.parcha.spoilnot.core.ui

import androidx.annotation.StringRes
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.error.AppError.LostRace.Race

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
    is AppError.LostRace -> race.unnamed()
    else -> R.string.error_unknown
}

/**
 * [messageRes] as a snackbar message. A [AppError.LostRace] names the member whose change won (DESIGN 8.5), from
 * [nameOf]; it says it without a name when that is unknown or [myUid] (the same account on another phone).
 */
fun Throwable.toUiMessage(nameOf: (String) -> String? = { null }, myUid: String? = null): UiMessage {
    if (this !is AppError.LostRace) return UiMessage(messageRes())
    val name = byUid?.takeIf { it != myUid }?.let(nameOf)
    val named = race.named()
    return if (name != null && named != null) UiMessage(named, listOf(name)) else UiMessage(race.unnamed())
}

@StringRes
private fun Race.unnamed(): Int = when (this) {
    Race.AddedToPantry -> R.string.race_pantry
    Race.Removed -> R.string.race_removed
    Race.Cooked -> R.string.race_cooked
    Race.ListDeleted -> R.string.race_list_deleted
}

@StringRes
private fun Race.named(): Int? = when (this) {
    Race.AddedToPantry -> R.string.race_pantry_by
    Race.Removed -> R.string.race_removed_by
    Race.Cooked -> R.string.race_cooked_by
    Race.ListDeleted -> null
}
