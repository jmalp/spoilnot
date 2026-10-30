import { useId, useState, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react';

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange' | 'value'> & {
  label: string;
  value: string;
  onValueChange: (value: string) => void;
  supportingText?: string;
  error?: string;
  leading?: ReactNode;
  trailing?: ReactNode;
};

/** An OutlinedTextField with a floating label, supporting text and an error line (FormTextField in the app). */
export function TextField({
  label,
  value,
  onValueChange,
  supportingText,
  error,
  leading,
  trailing,
  className,
  ...rest
}: Props) {
  const id = useId();
  const [focused, setFocused] = useState(false);
  const floating = focused || value.length > 0;
  const describedBy = error || supportingText ? `${id}-supporting` : undefined;
  const classes = ['m3-text-field', floating && 'm3-text-field--floating', error && 'm3-text-field--error', className];
  return (
    <div className={classes.filter(Boolean).join(' ')}>
      <div className="m3-text-field__box">
        {leading}
        <input
          id={id}
          className="m3-text-field__input"
          value={value}
          onChange={(event) => onValueChange(event.target.value)}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          {...rest}
        />
        {label && (
          <label className="m3-text-field__label" htmlFor={id}>
            {label}
          </label>
        )}
        {trailing && <div className="m3-text-field__trailing">{trailing}</div>}
      </div>
      {(error || supportingText) && (
        <div id={describedBy} className="m3-text-field__supporting">
          {error ?? supportingText}
        </div>
      )}
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
