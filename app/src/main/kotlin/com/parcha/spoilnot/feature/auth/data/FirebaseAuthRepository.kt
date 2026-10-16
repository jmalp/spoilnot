package com.parcha.spoilnot.feature.auth.data

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.parcha.spoilnot.BuildConfig
import com.parcha.spoilnot.core.error.AppError
import com.parcha.spoilnot.core.firebase.FirebaseEmulators
import com.parcha.spoilnot.feature.auth.domain.AuthRepository
import com.parcha.spoilnot.feature.auth.domain.AuthUser
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository @Inject constructor(private val auth: FirebaseAuth) : AuthRepository {
    override val authUser: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAuthUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override val currentUser: AuthUser? get() = auth.currentUser?.toAuthUser()

    override suspend fun signUp(displayName: String, email: String, password: String) = mapped {
        val user = checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user)
        user.updateProfile(userProfileChangeRequest { this.displayName = displayName }).await()
        // Verification is encouraged, not required (DESIGN 5.1); a failed send must not fail sign-up.
        runCatching { user.sendEmailVerification().await() }
        Unit
    }

    override suspend fun signIn(email: String, password: String) = mapped {
        auth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    override suspend fun sendPasswordReset(email: String) = mapped {
        try {
            auth.sendPasswordResetEmail(email).await()
        } catch (e: FirebaseAuthInvalidUserException) {
            // Same outcome as success, so the screen never reveals whether an account exists.
        }
        Unit
    }

    override suspend fun sendEmailVerification() = mapped {
        auth.currentUser?.sendEmailVerification()?.await()
        Unit
    }

    override suspend fun reload(): AuthUser? = mapped {
        auth.currentUser?.reload()?.await()
        currentUser
    }

    override suspend fun verifyPassword(password: String) = mapped {
        val email = auth.currentUser?.email ?: throw AppError.InvalidCredentials()
        // A separate app instance, so the check never touches the signed-in session that Firestore uses.
        val name = "${auth.app.name}-password-check"
        val context = auth.app.applicationContext
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == name }
            ?: FirebaseApp.initializeApp(context, auth.app.options, name)
        val checker = FirebaseAuth.getInstance(app).also {
            FirebaseEmulators.configureAuth(it, BuildConfig.FIREBASE_EMULATOR_HOST)
        }
        try {
            checker.signInWithEmailAndPassword(email, password).await()
        } finally {
            checker.signOut()
        }
        Unit
    }

    override suspend fun reauthenticate(password: String) = mapped {
        val user = auth.currentUser ?: throw AppError.InvalidCredentials()
        val email = user.email ?: throw AppError.InvalidCredentials()
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        Unit
    }

    override fun signOut() = auth.signOut()

    override suspend fun deleteAccount() = mapped {
        auth.currentUser?.delete()?.await()
        Unit
    }

    private suspend fun <T> mapped(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AppError) {
        throw e
    } catch (e: Exception) {
        throw e.toAuthError()
    }

    private fun Exception.toAuthError(): AppError = when (this) {
        is FirebaseAuthWeakPasswordException -> AppError.WeakPassword(this)

        is FirebaseAuthUserCollisionException -> AppError.EmailInUse(this)

        is FirebaseAuthRecentLoginRequiredException -> AppError.RecentLoginRequired(this)

        is FirebaseAuthInvalidCredentialsException ->
            if (errorCode == "ERROR_INVALID_EMAIL") AppError.InvalidEmail(this) else AppError.InvalidCredentials(this)

        is FirebaseAuthInvalidUserException -> AppError.InvalidCredentials(this)

        is FirebaseTooManyRequestsException -> AppError.TooManyAttempts(this)

        is FirebaseNetworkException -> AppError.Offline(this)

        else -> AppError.Unknown(this)
    }
}

private fun FirebaseUser.toAuthUser() = AuthUser(uid, email, displayName, isEmailVerified)
