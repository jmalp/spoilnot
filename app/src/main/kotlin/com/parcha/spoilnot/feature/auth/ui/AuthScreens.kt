package com.parcha.spoilnot.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.ErrorText
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.LeafBadge
import com.parcha.spoilnot.core.designsystem.component.MaxContentWidth
import com.parcha.spoilnot.core.designsystem.component.PasswordField
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.component.PushedScaffold
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotSurfaces
import com.parcha.spoilnot.core.designsystem.theme.SpoilNotText
import com.parcha.spoilnot.feature.auth.domain.AccountValidation
import com.parcha.spoilnot.feature.auth.domain.AccountValidation.NameProblem

/**
 * Welcome (DESIGN 5.1, 19.12) in the 2026-11 look: the app's jar in a large leaf, its name in Fredoka and the tagline in
 * the middle of the page, Create account and Sign in at the bottom as 60 dp buttons (19.9). The page scrolls when the
 * text is large.
 */
@Composable
fun WelcomeScreen(onCreateAccount: () -> Unit, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    // A Surface, not just a background: it also sets the text color, which was black in dark mode (beta feedback).
    Surface(modifier.fillMaxSize().testTag("screen_welcome"), color = SpoilNotSurfaces.current.page) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            // At least the screen's height, so the words sit in the middle and the buttons at the bottom; taller with
            // large text, and then it scrolls.
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .widthIn(max = MaxContentWidth)
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
                ) {
                    LeafBadge(
                        size = 208.dp,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_launcher_monochrome),
                            contentDescription = null,
                            modifier = Modifier.size(208.dp),
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.app_name),
                            style = SpoilNotText.Wordmark,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            stringResource(R.string.welcome_tagline),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Column(
                    Modifier
                        .widthIn(max = MaxContentWidth)
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PrimaryButton(
                        stringResource(R.string.action_create_account),
                        onClick = onCreateAccount,
                        modifier = Modifier.heightIn(min = WelcomeButtonHeight),
                    )
                    FilledTonalButton(
                        onClick = onSignIn,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().heightIn(min = WelcomeButtonHeight),
                    ) { Text(stringResource(R.string.action_sign_in), style = SpoilNotText.ButtonLarge) }
                }
            }
        }
    }
}

/** Welcome's and Sign in's main buttons are 60 dp, a step up from the 56 dp elsewhere (DESIGN 19.9). */
private val WelcomeButtonHeight = 60.dp

/**
 * Sign in (DESIGN 5.1, 19.12): Email and Password, Sign in and Forgot password?. A wrong email or password outlines the
 * password field and says so under it with an icon, and moves the focus there, so TalkBack reads the field with its
 * error; an email the server refuses does the same on Email. Any other failure (offline, too many tries) shows under
 * the fields.
 */
@Composable
fun SignInScreen(
    state: AuthViewModel.UiState,
    onSignIn: (email: String, password: String) -> Unit,
    onForgotPassword: (email: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val emailError = submitted && !AccountValidation.isEmailValid(email)
    val submit = {
        submitted = true
        if (AccountValidation.isEmailValid(email) && password.isNotEmpty()) onSignIn(email, password)
    }
    val error = state.error
    val passwordFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    LaunchedEffect(error) {
        when (error) {
            R.string.error_invalid_credentials -> passwordFocus.tryFocus()
            R.string.error_email_invalid -> emailFocus.tryFocus()
        }
    }

    AuthForm(stringResource(R.string.sign_in_title), onBack, modifier.testTag("screen_sign_in")) {
        EmailField(
            email,
            { email = it },
            when {
                emailError || error == R.string.error_email_invalid -> stringResource(R.string.error_email_invalid)
                else -> null
            },
            modifier = Modifier.focusRequester(emailFocus),
        )
        PasswordField(
            value = password,
            onValueChange = { password = it },
            error = error?.takeIf { it == R.string.error_invalid_credentials }?.let { stringResource(it) },
            onImeAction = submit,
            modifier = Modifier.focusRequester(passwordFocus),
        )
        ErrorText(error?.takeIf { it !in FieldErrors }?.let { stringResource(it) })
        PrimaryButton(
            stringResource(R.string.action_sign_in),
            onClick = submit,
            busy = state.busy,
            modifier = Modifier.padding(top = 6.dp).heightIn(min = WelcomeButtonHeight),
        )
        TextButton(
            onClick = { onForgotPassword(email) },
            modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.action_forgot_password), style = SpoilNotText.ButtonMedium) }
    }
}

/** Server errors that belong to a field, shown on it rather than under the form. */
private val FieldErrors = setOf(
    R.string.error_invalid_credentials,
    R.string.error_email_invalid,
    R.string.error_email_in_use,
    R.string.error_weak_password,
)

/**
 * Moves the focus to a field once it is laid out (a frame later, when the error came with the screen); a field that
 * isn't there is not worth crashing for.
 */
private suspend fun FocusRequester.tryFocus() {
    withFrameNanos { }
    runCatching { requestFocus() }
}

/**
 * Create account (DESIGN 5.1): name, email and password with their helpers. An email the server refuses or already
 * has an account, or a password it calls weak, is shown on that field (DESIGN 19.12), the focus moved there.
 */
@Composable
fun SignUpScreen(
    state: AuthViewModel.UiState,
    onSignUp: (name: String, email: String, password: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val nameProblem = AccountValidation.displayNameProblem(name)
    val emailValid = AccountValidation.isEmailValid(email)
    val passwordValid = AccountValidation.isPasswordLongEnough(password)
    val submit = {
        submitted = true
        if (nameProblem == null && emailValid && passwordValid) onSignUp(name, email, password)
    }
    val error = state.error
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(error) {
        when (error) {
            R.string.error_email_in_use, R.string.error_email_invalid -> emailFocus.tryFocus()
            R.string.error_weak_password -> passwordFocus.tryFocus()
        }
    }

    AuthForm(stringResource(R.string.sign_up_title), onBack, modifier.testTag("screen_sign_up")) {
        FormTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.field_display_name),
            helper = stringResource(R.string.field_display_name_help),
            error = when (nameProblem.takeIf { submitted }) {
                NameProblem.Blank -> stringResource(R.string.error_name_blank)
                NameProblem.TooLong -> stringResource(R.string.error_name_too_long)
                null -> null
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )
        EmailField(
            email,
            { email = it },
            when {
                submitted && !emailValid -> stringResource(R.string.error_email_invalid)
                error == R.string.error_email_in_use || error == R.string.error_email_invalid -> stringResource(error)
                else -> null
            },
            modifier = Modifier.focusRequester(emailFocus),
        )
        PasswordField(
            value = password,
            onValueChange = { password = it },
            helper = stringResource(R.string.field_password_help),
            error = when {
                submitted && !passwordValid -> stringResource(R.string.error_password_short)
                error == R.string.error_weak_password -> stringResource(error)
                else -> null
            },
            onImeAction = submit,
            modifier = Modifier.focusRequester(passwordFocus),
        )
        ErrorText(error?.takeIf { it !in FieldErrors }?.let { stringResource(it) })
        PrimaryButton(
            stringResource(R.string.action_create_account),
            onClick = submit,
            busy = state.busy,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun ResetPasswordScreen(
    initialEmail: String,
    state: AuthViewModel.UiState,
    onSend: (email: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val emailValid = AccountValidation.isEmailValid(email)
    val submit = {
        submitted = true
        if (emailValid) onSend(email)
    }
    val error = state.error

    AuthForm(stringResource(R.string.reset_title), onBack, modifier.testTag("screen_reset_password")) {
        Text(
            stringResource(R.string.reset_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EmailField(
            email,
            { email = it },
            when {
                submitted && !emailValid -> stringResource(R.string.error_email_invalid)
                error == R.string.error_email_invalid -> stringResource(error)
                else -> null
            },
            imeAction = ImeAction.Done,
            onDone = submit,
        )
        state.resetSentTo?.let {
            Text(
                stringResource(R.string.reset_sent, it),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        ErrorText(error?.takeIf { it !in FieldErrors }?.let { stringResource(it) })
        PrimaryButton(
            stringResource(R.string.action_send_link),
            onClick = submit,
            busy = state.busy,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    FormTextField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(R.string.field_email),
        error = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier,
    )
}

/**
 * The signed-out forms as pushed screens of the 2026-11 look (DESIGN 19.7): the round Back over the title, the fields
 * 18 dp apart (19.11), scrolling above the keyboard.
 */
@Composable
private fun AuthForm(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    PushedScaffold(title = title, onBack = onBack, modifier = modifier) { padding: PaddingValues ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}
