package com.parcha.spoilnot.feature.auth.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.parcha.spoilnot.R
import com.parcha.spoilnot.feature.update.domain.AppConfig
import com.parcha.spoilnot.feature.update.ui.UpdateRequiredScreen
import com.parcha.spoilnot.testing.Screenshots
import com.parcha.spoilnot.testing.setThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The signed-out screens and Update required, light and dark at 1.0× and 2.0× text (after v1, review CODE-07). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AuthScreenshotTest(private val dark: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} font={1}")
        fun params() = Screenshots.params()
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String) =
        compose.onRoot().captureRoboImage(Screenshots.screenshotName(name, dark, fontScale))

    @Test
    fun signIn() {
        compose.setThemed(dark, fontScale) {
            SignInScreen(AuthViewModel.UiState(error = R.string.error_invalid_credentials), { _, _ -> }, {}, {})
        }
        capture("auth_sign_in")
    }

    @Test
    fun signUp() {
        compose.setThemed(dark, fontScale) { SignUpScreen(AuthViewModel.UiState(), { _, _, _ -> }, {}) }
        capture("auth_sign_up")
    }

    @Test
    fun resetPassword() {
        compose.setThemed(dark, fontScale) {
            ResetPasswordScreen("sam@example.test", AuthViewModel.UiState(), {}, {})
        }
        capture("auth_reset_password")
    }

    @Test
    fun updateRequired() {
        compose.setThemed(dark, fontScale) {
            UpdateRequiredScreen(AppConfig(minSupportedBuild = 99, message = null, updateUrl = "https://example.test"))
        }
        capture("update_required")
    }
}
