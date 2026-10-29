import { useState, type ReactNode } from 'react';
import { str } from '../core/i18n';
import { IconButton, TextField } from './kit';
import './forms.css';

/** A vertical form with the app's spacing (FormColumn): 12 px between fields. */
export function FormColumn({ children, onSubmit }: { children: ReactNode; onSubmit?: () => void }) {
  return (
    <form
      className="sn-form"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit?.();
      }}
    >
      {children}
    </form>
  );
}

/** A password field with Show and Hide (PasswordField). */
export function PasswordField({
  value,
  onValueChange,
  label = str('field_password'),
  supportingText,
  error,
  autoComplete = 'current-password',
}: {
  value: string;
  onValueChange: (value: string) => void;
  label?: string;
  supportingText?: string;
  error?: string;
  autoComplete?: 'current-password' | 'new-password';
}) {
  const [visible, setVisible] = useState(false);
  return (
    <TextField
      label={label}
      value={value}
      onValueChange={onValueChange}
      type={visible ? 'text' : 'password'}
      autoComplete={autoComplete}
      supportingText={supportingText}
      error={error}
      trailing={
        <IconButton
          icon={visible ? 'round/visibility_off' : 'round/visibility'}
          label={str(visible ? 'hide_password' : 'show_password')}
          onClick={() => setVisible(!visible)}
        />
      }
    />
  );
}
