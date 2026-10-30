import { useState } from 'react';
import { useAction } from '../../core/hooks';
import { str } from '../../core/i18n';
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

function WelcomeScreen({ onCreateAccount, onSignIn }: { onCreateAccount: () => void; onSignIn: () => void }) {
  return (
    <main className="sn-welcome" data-testid="screen_welcome">
      <div className="sn-welcome__column">
        <div className="sn-welcome__icon" aria-hidden>
          <svg viewBox="0 0 108 108" width="112" height="112">
            {LAUNCHER_MONOCHROME.map((path) => (
              <path key={path.d} d={path.d} fill="currentColor" fillRule={path.evenOdd ? 'evenodd' : undefined} />
            ))}
          </svg>
        </div>
        <h1 className="sn-welcome__title t-display-medium">{str('web_app_name')}</h1>
        <p className="t-body-large c-on-surface-variant">{str('welcome_tagline')}</p>
        <div className="sn-welcome__space" />
        <Button full large onClick={onCreateAccount}>
          {str('action_create_account')}
        </Button>
        <Button variant="outlined" full large onClick={onSignIn}>
          {str('action_sign_in')}
        </Button>
        <WelcomeInstallLine />
      </div>
    </main>
  );
}

function SignInScreen({ onForgotPassword, onBack }: { onForgotPassword: (email: string) => void; onBack: () => void }) {
  const { session } = useServices();
  const action = useAction();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const emailError = submitted && !AccountValidation.isEmailValid(email);
  const submit = () => {
    setSubmitted(true);
    if (AccountValidation.isEmailValid(email) && password !== '')
      void action.run(() => session.signIn(email, password));
  };
  return (
    <DetailScaffold title={str('sign_in_title')} onBack={onBack} testId="screen_sign_in">
      <FormColumn onSubmit={submit}>
        <TextField
          label={str('field_email')}
          value={email}
          onValueChange={setEmail}
          type="email"
          autoComplete="email"
          inputMode="email"
          error={emailError ? str('error_email_invalid') : undefined}
        />
        <PasswordField value={password} onValueChange={setPassword} />
        <ErrorText text={action.error && str(action.error)} />
        <Button type="submit" full large busy={action.busy}>
          {str('action_sign_in')}
        </Button>
        <Button variant="text" className="sn-auth__secondary" onClick={() => onForgotPassword(email)}>
          {str('action_forgot_password')}
        </Button>
      </FormColumn>
    </DetailScaffold>
  );
}

function SignUpScreen({ onBack }: { onBack: () => void }) {
  const { session } = useServices();
  const action = useAction();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const nameProblem = AccountValidation.displayNameProblem(name);
  const emailValid = AccountValidation.isEmailValid(email);
  const passwordValid = AccountValidation.isPasswordLongEnough(password);
  const submit = () => {
    setSubmitted(true);
    if (nameProblem === null && emailValid && passwordValid)
      void action.run(() => session.signUp(name, email, password));
  };
  return (
    <DetailScaffold title={str('sign_up_title')} onBack={onBack} testId="screen_sign_up">
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
          error={submitted && !emailValid ? str('error_email_invalid') : undefined}
        />
        <PasswordField
          value={password}
          onValueChange={setPassword}
          autoComplete="new-password"
          supportingText={str('field_password_help')}
          error={submitted && !passwordValid ? str('error_password_short') : undefined}
        />
        <ErrorText text={action.error && str(action.error)} />
        <Button type="submit" full large busy={action.busy}>
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
  const submit = () => {
    setSubmitted(true);
    if (!emailValid) return;
    void action.run(async () => {
      await auth.sendPasswordReset(email.trim());
      setSentTo(email.trim());
    });
  };
  return (
    <DetailScaffold title={str('reset_title')} onBack={onBack} testId="screen_reset_password">
      <FormColumn onSubmit={submit}>
        <p className="t-body-large sn-auth__text">{str('reset_body')}</p>
        <TextField
          label={str('field_email')}
          value={email}
          onValueChange={setEmail}
          type="email"
          autoComplete="email"
          inputMode="email"
          error={submitted && !emailValid ? str('error_email_invalid') : undefined}
        />
        {sentTo && <p className="t-body-medium sn-auth__text">{str('reset_sent', sentTo)}</p>}
        <ErrorText text={action.error && str(action.error)} />
        <Button type="submit" full large busy={action.busy}>
          {str('action_send_link')}
        </Button>
      </FormColumn>
    </DetailScaffold>
  );
}
