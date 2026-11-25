package com.parcha.spoilnot.feature.auth.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.theme.ColorTheme
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Where a signed-out form's server errors show (DESIGN 19.12, step 8): a wrong email or password on the password field,
 * an email already in use or a weak password on its field, the focus moved there so TalkBack reads it; anything else
 * under the fields, with its icon.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AuthErrorsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun fieldWithError(label: Int, error: Int) =
        hasSetTextAction() and hasText(str(label), substring = true) and
            SemanticsMatcher.expectValue(SemanticsProperties.Error, str(error))

    @Test
    fun wrongPasswordIsOnThePasswordFieldWhichTakesTheFocus() {
        compose.setContent {
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) {
                SignInScreen(AuthViewModel.UiState(error = R.string.error_invalid_credentials), { _, _ -> }, {}, {})
            }
        }
        compose.onNode(fieldWithError(R.string.field_password, R.string.error_invalid_credentials))
            .assertExists()
            .assertIsFocused()
        // Said once, on the field: not again under the form.
        assertEquals(
            1,
            compose.onAllNodesWithText(str(R.string.error_invalid_credentials), substring = true)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun beingOfflineShowsUnderTheFormNotOnAField() {
        compose.setContent {
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) {
                SignInScreen(AuthViewModel.UiState(error = R.string.error_offline), { _, _ -> }, {}, {})
            }
        }
        compose.onNodeWithText(str(R.string.error_offline)).assertExists()
        compose.onNode(hasSetTextAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
            .assertDoesNotExist()
    }

    @Test
    fun anEmailInUseIsOnTheEmailField() {
        compose.setContent {
            SpoilNotTheme(colorTheme = ColorTheme.SpoilNot) {
                SignUpScreen(AuthViewModel.UiState(error = R.string.error_email_in_use), { _, _, _ -> }, {})
            }
        }
        compose.onNode(fieldWithError(R.string.field_email, R.string.error_email_in_use))
            .assertExists()
            .assertIsFocused()
    }
}
