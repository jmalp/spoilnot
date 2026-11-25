import { useEffect, useRef, useState } from 'react';
import { useAction } from '../../core/hooks';
import { str, type StringId } from '../../core/i18n';
import { useServices } from '../../core/services';
import { AccountValidation } from '../../domain/account';
import { LAUNCHER_MONOCHROME } from '../icons.generated';
import { Button, ErrorText, TextField } from '../kit';
import { FormColumn, PasswordField } from '../forms';
import { DetailScaffold } from '../Scaffold';
import { WelcomeInstallLine } from '../install/InstallHelp';
import './auth.css';

type Screen = { name: 'welcome' } | { name: 'signIn' } | { name: 'signUp' } | { name: 'reset'; email: string };

/** Signed-out screens (AuthFlow): welcome, sign in, create account and password reset. */
export function AuthFlow() {
  const [stack, setStack] = useState<Screen[]>([{ name: 'welcome' }]);
  const go = (screen: Screen) => setStack((s) => [...s, screen]);
  const back = () => setStack((s) => (s.length > 1 ? s.slice(0, -1) : s));
  const screen = stack[stack.length - 1]!;
  switch (screen.name) {
    case 'welcome':
      return <WelcomeScreen onCreateAccount={() => go({ name: 'signUp' })} onSignIn={() => go({ name: 'signIn' })} />;
    case 'signIn':
      return <SignInScreen onForgotPassword={(email) => go({ name: 'reset', email })} onBack={back} />;
    case 'signUp':
      return <SignUpScreen onBack={back} />;
    case 'reset':
      return <ResetPasswordScreen initialEmail={screen.email} onBack={back} />;
  }
}

/**
 * Welcome (WelcomeScreen in the app; DESIGN 19.12, step 8): the jar in a large leaf, the name in Fredoka and the
 * tagline in the middle of the page, Create account and Sign in as 60 px buttons at the bottom, then the line about
 * adding the app to the Home Screen. The page scrolls when the text is large.
 */
function WelcomeScreen({ onCreateAccount, onSignIn }: { onCreateAccount: () => void; onSignIn: () => void }) {
  return (
    <main className="sn-welcome" data-testid="screen_welcome">
      <div className="sn-welcome__middle">
        <div className="sn-welcome__leaf" aria-hidden>
          <svg viewBox="0 0 108 108" width="208" height="208">
            {LAUNCHER_MONOCHROME.map((path) => (
              <path key={path.d} d={path.d} fill="currentColor" fillRule={path.evenOdd ? 'evenodd' : undefined} />
            ))}
          </svg>
        </div>
        <div className="sn-welcome__words">
          <h1 className="sn-welcome__title t-wordmark">{str('web_app_name')}</h1>
          <p className="sn-welcome__tagline c-on-surface-variant">{str('welcome_tagline')}</p>
        </div>
      </div>
      <div className="sn-welcome__actions">
        <Button primary className="sn-auth__tall" onClick={onCreateAccount}>
          {str('action_create_account')}
        </Button>
        <Button variant="tonal" full large className="sn-auth__tall t-button-large" onClick={onSignIn}>
          {str('action_sign_in')}
        </Button>
        <WelcomeInstallLine />
      </div>
    </main>
  );
}

/** Server errors that belong to a field, shown on it rather than under the form (DESIGN 19.12). */
const FIELD_ERRORS: readonly StringId[] = [
  'error_invalid_credentials',
  'error_email_invalid',
  'error_email_in_use',
  'error_weak_password',
];

/** The error under the form: anything but a field's own. */
const formError = (error: StringId | null) => (error && !FIELD_ERRORS.includes(error) ? str(error) : null);

/**
 * Sign in (SignInScreen in the app; DESIGN 19.12): a wrong email or password outlines Password and says so under it
 * with an icon, an email the server refuses does the same on Email, and the focus moves there; any other failure
 * (offline, too many tries) shows under the fields.
 */
function SignInScreen({ onForgotPassword, onBack }: { onForgotPassword: (email: string) => void; onBack: () => void }) {
  const { session } = useServices();
  const action = useAction();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const emailRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const error = action.error;
  // A field's own error moves the focus to it, so VoiceOver reads the field with its error (its description).
  useEffect(() => {
    if (error === 'error_invalid_credentials') passwordRef.current?.focus();
    else if (error === 'error_email_invalid') emailRef.current?.focus();
  }, [error]);
  const emailError =
    (submitted && !AccountValidation.isEmailValid(email)) || error === 'error_email_invalid'
      ? str('error_email_invalid')
      : undefined;
  const submit = () => {
    setSubmitted(true);
    if (AccountValidation.isEmailValid(email) && password !== '')
      void action.run(() => session.signIn(email, password));
  };
  return (
    <DetailScaffold title={str('sign_in_title')} onBack={onBack} header="pushed" testId="screen_sign_in">
      <FormColumn onSubmit={submit}>
        <TextField
          label={str('field_email')}
          value={email}
          onValueChange={setEmail}
          type="email"
          autoComplete="email"
          inputMode="email"
          error={emailError}
          inputRef={emailRef}
        />
        <PasswordField
          value={password}
          onValueChange={setPassword}
          error={error === 'error_invalid_credentials' ? str(error) : undefined}
          inputRef={passwordRef}
        />
        <ErrorText text={formError(error)} />
        <Button type="submit" primary busy={action.busy} className="sn-auth__tall sn-auth__main">
          {str('action_sign_in')}
        </Button>
        <Button variant="text" className="sn-auth__secondary t-button-medium" onClick={() => onForgotPassword(email)}>
          {str('action_forgot_password')}
        </Button>
      </FormColumn>
    </DetailScaffold>
  );
}

/**
 * Create account (SignUpScreen in the app): an email the server refuses or already has an account, or a password it
 * calls weak, is shown on that field (DESIGN 19.12), the focus moved there.
 */
function SignUpScreen({ onBack }: { onBack: () => void }) {
  const { session } = useServices();
  const action = useAction();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const emailRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const error = action.error;
  // A field's own error moves the focus to it, so VoiceOver reads the field with its error (its description).
  useEffect(() => {
    if (error === 'error_email_in_use' || error === 'error_email_invalid') emailRef.current?.focus();
    else if (error === 'error_weak_password') passwordRef.current?.focus();
  }, [error]);
  const nameProblem = AccountValidation.displayNameProblem(name);
  const emailValid = AccountValidation.isEmailValid(email);
  const passwordValid = AccountValidation.isPasswordLongEnough(password);
  const submit = () => {
    setSubmitted(true);
    if (nameProblem === null && emailValid && passwordValid)
      void action.run(() => session.signUp(name, email, password));
  };
  return (
    <DetailScaffold title={str('sign_up_title')} onBack={onBack} header="pushed" testId="screen_sign_up">
      <FormColumn onSubmit={submit}>
        <TextField
          label={str('field_display_name')}
          value={name}
          onValueChange={setName}
          autoComplete="nickname"
          autoCapitalize="words"
          supportingText={str('field_display_name_help')}
          error={
            submitted && nameProblem
              ? str(nameProblem === 'blank' ? 'error_name_blank' : 'error_name_too_long')
              : undefined
          }
        />
        <TextField
          label={str('field_email')}
          value={email}
          onValueChange={setEmail}
          type="email"
          autoComplete="email"
          inputMode="email"
          error={
            submitted && !emailValid
              ? str('error_email_invalid')
              : error === 'error_email_in_use' || error === 'error_email_invalid'
                ? str(error)
                : undefined
          }
          inputRef={emailRef}
        />
        <PasswordField
          value={password}
          onValueChange={setPassword}
          autoComplete="new-password"
          supportingText={str('field_password_help')}
          error={
            submitted && !passwordValid
              ? str('error_password_short')
              : error === 'error_weak_password'
                ? str(error)
                : undefined
          }
          inputRef={passwordRef}
        />
        <ErrorText text={formError(error)} />
        <Button type="submit" primary busy={action.busy} className="sn-auth__main">
          {str('action_create_account')}
        </Button>
      </FormColumn>
    </DetailScaffold>
  );
}

function ResetPasswordScreen({ initialEmail, onBack }: { initialEmail: string; onBack: () => void }) {
  const { auth } = useServices();
  const action = useAction();
  const [email, setEmail] = useState(initialEmail);
  const [submitted, setSubmitted] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const emailValid = AccountValidation.isEmailValid(email);
  const error = action.error;
  const submit = () => {
    setSubmitted(true);
    if (!emailValid) return;
    void action.run(async () => {
      await auth.sendPasswordReset(email.trim());
      setSentTo(email.trim());
    });
  };
  return (
    <DetailScaffold title={str('reset_title')} onBack={onBack} header="pushed" testId="screen_reset_password">
      <FormColumn onSubmit={submit}>
        <p className="t-body-large c-on-surface-variant sn-auth__text">{str('reset_body')}</p>
        <TextField
          label={str('field_email')}
          value={email}
          onValueChange={setEmail}
          type="email"
          autoComplete="email"
          inputMode="email"
          error={
            submitted && !emailValid
              ? str('error_email_invalid')
              : error === 'error_email_invalid'
                ? str(error)
                : undefined
          }
        />
        {sentTo && (
          <p className="t-body-large sn-auth__text sn-auth__sent" role="status">
            {str('reset_sent', sentTo)}
          </p>
        )}
        <ErrorText text={formError(error)} />
        <Button type="submit" primary busy={action.busy} className="sn-auth__main">
          {str('action_send_link')}
        </Button>
      </FormColumn>
    </DetailScaffold>
  );
}
