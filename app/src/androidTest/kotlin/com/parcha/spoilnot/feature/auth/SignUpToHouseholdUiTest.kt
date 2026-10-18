package com.parcha.spoilnot.feature.auth

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.parcha.spoilnot.EmulatorSupport
import com.parcha.spoilnot.EmulatorSupport.PASSWORD
import com.parcha.spoilnot.EmulatorSupport.uniqueEmail
import com.parcha.spoilnot.MainActivity
import com.parcha.spoilnot.R
import com.parcha.spoilnot.feature.household.domain.InviteCodes
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/** The real app against the emulators: sign up, create a household, see the invite code, sign out. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SignUpToHouseholdUiTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    /** Before the activity starts: emulator access granted (no permission prompt) and nobody signed in. */
    @get:Rule(order = 1)
    val signedOut = object : ExternalResource() {
        override fun before() {
            EmulatorSupport.requireEmulatorAndNetworkAccess()
            FirebaseAuth.getInstance().signOut()
        }
    }

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun str(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun field(label: Int) = compose.onNode(hasSetTextAction() and hasText(str(label)))

    private fun button(label: Int) = compose.onNode(hasText(str(label)) and hasClickAction())

    private fun awaitTag(tag: String, timeoutMs: Long = 20_000): SemanticsNodeInteraction {
        compose.waitUntilAtLeastOneExists(hasTestTag(tag), timeoutMs)
        return compose.onNodeWithTag(tag)
    }

    @Test
    fun signUpCreateHouseholdShowCodeAndSignOut() {
        awaitTag("screen_welcome")
        button(R.string.action_create_account).performClick()

        awaitTag("screen_sign_up")
        field(R.string.field_display_name).performTextInput("Casey")
        field(R.string.field_email).performTextInput(uniqueEmail("casey"))
        field(R.string.field_password).performTextInput(PASSWORD)
        button(R.string.action_create_account).performClick()

        awaitTag("screen_gate")
        field(R.string.field_household_name).performTextInput("Casey home")
        button(R.string.action_create).performClick()

        awaitTag("screen_home")
        compose.onNodeWithContentDescription(str(R.string.open_settings)).performClick()
        awaitTag("screen_settings")
        compose.onNodeWithTag("row_household").performClick()
        awaitTag("screen_household")
        compose.waitUntilAtLeastOneExists(hasTestTag("invite_code"), 10_000)
        val code = compose.onNodeWithTag("invite_code").fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString("") { it.text }
        assertTrue("Shown as XXXXX-XXXXX: $code", InviteCodes.isWellFormed(code.replace("-", "")) && code[5] == '-')

        compose.onNodeWithContentDescription(str(R.string.back)).performClick()
        awaitTag("screen_settings")
        button(R.string.action_sign_out).performScrollTo().performClick()
        awaitTag("screen_welcome")
    }

    @Test
    fun wrongPasswordShowsAFriendlyError() {
        awaitTag("screen_welcome")
        button(R.string.action_sign_in).performClick()

        awaitTag("screen_sign_in")
        field(R.string.field_email).performTextInput(uniqueEmail("nobody"))
        field(R.string.field_password).performTextInput("not-the-password")
        button(R.string.action_sign_in).performClick()

        compose.waitUntilAtLeastOneExists(
            SemanticsMatcher.expectValue(
                SemanticsProperties.Text,
                listOf(AnnotatedString(str(R.string.error_invalid_credentials))),
            ),
            15_000,
        )
    }
}
