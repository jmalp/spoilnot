package com.parcha.spoilnot.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parcha.spoilnot.R
import com.parcha.spoilnot.core.designsystem.component.DetailScaffold
import com.parcha.spoilnot.core.designsystem.component.ErrorText
import com.parcha.spoilnot.core.designsystem.component.FormColumn
import com.parcha.spoilnot.core.designsystem.component.FormTextField
import com.parcha.spoilnot.core.designsystem.component.MaxContentWidth
import com.parcha.spoilnot.core.designsystem.component.PasswordField
import com.parcha.spoilnot.core.designsystem.component.PrimaryButton
import com.parcha.spoilnot.core.designsystem.theme.Fredoka
import com.parcha.spoilnot.feature.auth.domain.AccountValidation
import com.parcha.spoilnot.feature.auth.domain.AccountValidation.NameProblem

@Composable
fun WelcomeScreen(onCreateAccount: () -> Unit, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    // A Surface, not just a background: it also sets the text color, which was black in dark mode (beta feedback).
    Surface(modifier.fillMaxSize().testTag("screen_welcome")) {
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = MaxContentWidth).verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier.size(112.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_launcher_monochrome),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(112.dp),
                    )
                }
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = Fredoka),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.welcome_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(16.dp))
                PrimaryButton(stringResource(R.string.action_create_account), onClick = onCreateAccount)
                OutlinedButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_sign_in))
                }
            }
        }
    }
}

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

    AuthForm(stringResource(R.string.sign_in_title), onBack, modifier.testTag("screen_sign_in")) {
        EmailField(email, { email = it }, if (emailError) stringResource(R.string.error_email_invalid) else null)
        PasswordField(value = password, onValueChange = { password = it }, onImeAction = submit)
        ErrorText(state.error?.let { stringResource(it) })
        PrimaryButton(stringResource(R.string.action_sign_in), onClick = submit, busy = state.busy)
        TextButton(onClick = { onForgotPassword(email) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_forgot_password))
        }
    }
}

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
            if (submitted &&
                !emailValid
            ) {
                stringResource(R.string.error_email_invalid)
            } else {
                null
            },
        )
        PasswordField(
            value = password,
            onValueChange = { password = it },
            helper = stringResource(R.string.field_password_help),
            error = if (submitted && !passwordValid) stringResource(R.string.error_password_short) else null,
            onImeAction = submit,
        )
        ErrorText(state.error?.let { stringResource(it) })
        PrimaryButton(stringResource(R.string.action_create_account), onClick = submit, busy = state.busy)
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

    AuthForm(stringResource(R.string.reset_title), onBack, modifier.testTag("screen_reset_password")) {
        Text(stringResource(R.string.reset_body), style = MaterialTheme.typography.bodyLarge)
        EmailField(
            email,
            { email = it },
            if (submitted && !emailValid) stringResource(R.string.error_email_invalid) else null,
            imeAction = ImeAction.Done,
            onDone = submit,
        )
        state.resetSentTo?.let {
            Text(stringResource(R.string.reset_sent, it), style = MaterialTheme.typography.bodyMedium)
        }
        ErrorText(state.error?.let { stringResource(it) })
        PrimaryButton(stringResource(R.string.action_send_link), onClick = submit, busy = state.busy)
    }
}

@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
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
    )
}

@Composable
private fun AuthForm(title: String, onBack: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    DetailScaffold(title = title, onBack = onBack, modifier = modifier) { padding: PaddingValues ->
        FormColumn(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) { content() }
    }
}
