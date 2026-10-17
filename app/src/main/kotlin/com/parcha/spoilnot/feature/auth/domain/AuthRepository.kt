package com.parcha.spoilnot.feature.auth.domain

import kotlinx.coroutines.flow.Flow

/** Email and password accounts (DESIGN 5.1). Implementations throw [com.parcha.spoilnot.core.error.AppError]. */
interface AuthRepository {
    /** The signed-in user, or null. Emits on sign-in and sign-out. */
    val authUser: Flow<AuthUser?>

    val currentUser: AuthUser?

    /** Creates the account, sets its display name and sends a verification email. Leaves the user signed in. */
    suspend fun signUp(displayName: String, email: String, password: String)

    suspend fun signIn(email: String, password: String)

    /** Sends a reset link. Succeeds whether or not the address has an account (no email enumeration). */
    suspend fun sendPasswordReset(email: String)

    suspend fun sendEmailVerification()

    /** Refreshes [currentUser], for example to pick up a verified email. */
    suspend fun reload(): AuthUser?

    /**
     * Checks the password without changing the current sign-in, so a wrong password stops account deletion
     * before anything is deleted. Throws InvalidCredentials when it does not match.
     */
    suspend fun verifyPassword(password: String)

    /** Refreshes the sign-in when deleting it requires a recent login. */
    suspend fun reauthenticate(password: String)

    fun signOut()

    /** Deletes the sign-in itself. The profile must be deleted first, while the user can still write it. */
    suspend fun deleteAccount()
}

/** users/{uid}. Implementations throw [com.parcha.spoilnot.core.error.AppError]. */
interface UserRepository {
    /** The profile, or null once the server confirms it does not exist. */
    fun observeProfile(uid: String): Flow<UserProfile?>

    /** Creates the profile unless it already exists. */
    suspend fun createProfileIfMissing(uid: String, displayName: String)

    /** Clears householdId after the user lost their membership. */
    suspend fun clearHousehold(uid: String)

    /** Deletes the profile and its device tokens. */
    suspend fun deleteProfile(uid: String)

    suspend fun setUnits(uid: String, units: Units)
}
