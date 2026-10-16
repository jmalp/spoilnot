package com.parcha.spoilnot.feature.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

@Serializable
private data object WelcomeRoute : NavKey

@Serializable
private data object SignInRoute : NavKey

@Serializable
private data object SignUpRoute : NavKey

@Serializable
private data class ResetPasswordRoute(val email: String) : NavKey

/** Signed-out screens: welcome, sign in, create account and password reset. */
@Composable
fun AuthFlow(modifier: Modifier = Modifier, viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(WelcomeRoute)
    fun go(key: NavKey) {
        viewModel.resetMessages()
        backStack.add(key)
    }
    fun back() {
        viewModel.resetMessages()
        backStack.removeLastOrNull()
    }

    NavDisplay(
        backStack = backStack,
        onBack = ::back,
        modifier = modifier,
        entryProvider = entryProvider {
            entry<WelcomeRoute> { WelcomeScreen(onCreateAccount = { go(SignUpRoute) }, onSignIn = { go(SignInRoute) }) }
            entry<SignInRoute> {
                SignInScreen(
                    state = state,
                    onSignIn = viewModel::signIn,
                    onForgotPassword = { go(ResetPasswordRoute(it)) },
                    onBack = ::back,
                )
            }
            entry<SignUpRoute> { SignUpScreen(state = state, onSignUp = viewModel::signUp, onBack = ::back) }
            entry<ResetPasswordRoute> { key ->
                ResetPasswordScreen(
                    initialEmail = key.email,
                    state = state,
                    onSend = viewModel::sendPasswordReset,
                    onBack = ::back,
                )
            }
        },
    )
}
