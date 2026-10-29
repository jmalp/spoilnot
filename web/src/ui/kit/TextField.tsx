import { useId, useState, type InputHTMLAttributes, type ReactNode } from 'react';

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange' | 'value'> & {
  label: string;
  value: string;
  onValueChange: (value: string) => void;
  supportingText?: string;
  error?: string;
  trailing?: ReactNode;
};

/** An OutlinedTextField with a floating label, supporting text and an error line (FormTextField in the app). */
export function TextField({ label, value, onValueChange, supportingText, error, trailing, className, ...rest }: Props) {
  const id = useId();
  const [focused, setFocused] = useState(false);
  const floating = focused || value.length > 0;
  const describedBy = error || supportingText ? `${id}-supporting` : undefined;
  const classes = ['m3-text-field', floating && 'm3-text-field--floating', error && 'm3-text-field--error', className];
  return (
    <div className={classes.filter(Boolean).join(' ')}>
      <div className="m3-text-field__box">
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
        <label className="m3-text-field__label" htmlFor={id}>
          {label}
        </label>
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
