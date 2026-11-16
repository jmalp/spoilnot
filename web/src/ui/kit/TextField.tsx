import { useId, useState, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react';
import { Icon } from './Icon';

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange' | 'value'> & {
  label: string;
  value: string;
  onValueChange: (value: string) => void;
  supportingText?: string;
  error?: string;
  leading?: ReactNode;
  trailing?: ReactNode;
  /**
   * Material's outlined field with a floating label, as the app keeps it for the fields its steps haven't reached yet
   * (Groceries' quick add, Recipes' search and the recipe sheets).
   */
  outlined?: boolean;
};

/**
 * A one-line text field (FormTextField in the app). In the 2026-11 look (DESIGN 19.10, step 3) its label sits above a
 * 56 px box on the field color with 16 px corners, with a 2 px primary border while focused and a 2 px error border and
 * an icon by the message when wrong; [outlined] keeps Material's outlined field with its floating label.
 */
export function TextField({
  label,
  value,
  onValueChange,
  supportingText,
  error,
  leading,
  trailing,
  outlined = false,
  className,
  ...rest
}: Props) {
  const id = useId();
  const [focused, setFocused] = useState(false);
  const floating = focused || value.length > 0;
  const describedBy = error || supportingText ? `${id}-supporting` : undefined;
  const look = outlined ? (floating ? 'm3-text-field--floating' : '') : 'm3-text-field--look';
  const classes = ['m3-text-field', look, error && 'm3-text-field--error', className];
  const labelClass = outlined ? 'm3-text-field__label' : 'm3-text-field__label-above t-field-label';
  const labelElement = label ? (
    <label className={labelClass} htmlFor={id}>
      {label}
    </label>
  ) : null;
  return (
    <div className={classes.filter(Boolean).join(' ')}>
      {!outlined && labelElement}
      <div className="m3-text-field__box">
        {leading}
        <input
          id={id}
          className={outlined ? 'm3-text-field__input' : 'm3-text-field__input t-field-text'}
          value={value}
          onChange={(event) => onValueChange(event.target.value)}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          {...rest}
        />
        {outlined && labelElement}
        {trailing && <div className="m3-text-field__trailing">{trailing}</div>}
      </div>
      <FieldMessage id={describedBy} error={error} helper={supportingText} />
    </div>
  );
}

/** The line under a field: the error with an icon, so it never relies on color, or else the helper. */
export function FieldMessage({ id, error, helper }: { id?: string; error?: string; helper?: string }) {
  if (!error && !helper) return null;
  return (
    <div id={id} className="m3-text-field__supporting">
      {error && <Icon name="round/error_outline" size={16} />}
      <span>{error ?? helper}</span>
    </div>
  );
}

/** A multi-line OutlinedTextField (minLines in the app): the label rests on the first line until it floats. */
export function TextArea({
  label,
  value,
  onValueChange,
  supportingText,
  rows = 4,
  ...rest
}: Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'onChange' | 'value'> & {
  label: string;
  value: string;
  onValueChange: (value: string) => void;
  supportingText?: string;
}) {
  const id = useId();
  const [focused, setFocused] = useState(false);
  const floating = focused || value.length > 0;
  return (
    <div className={`m3-text-field m3-text-field--multiline ${floating ? 'm3-text-field--floating' : ''}`}>
      <div className="m3-text-field__box">
        <textarea
          id={id}
          className="m3-text-field__input"
          value={value}
          rows={rows}
          onChange={(event) => onValueChange(event.target.value)}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          aria-describedby={supportingText ? `${id}-supporting` : undefined}
          {...rest}
        />
        <label className="m3-text-field__label" htmlFor={id}>
          {label}
        </label>
      </div>
      {supportingText && (
        <div id={`${id}-supporting`} className="m3-text-field__supporting">
          {supportingText}
        </div>
      )}
    </div>
  );
}
