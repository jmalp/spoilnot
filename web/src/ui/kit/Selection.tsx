import type { InputHTMLAttributes } from 'react';

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'onChange'> & {
  onCheckedChange?: (checked: boolean) => void;
};

/** A Checkbox in a 48 px target. Label it with a surrounding <label> or aria-label. */
export function Checkbox({ onCheckedChange, ...rest }: Props) {
  return (
    <input
      type="checkbox"
      className="m3-checkbox m3-state"
      onChange={(e) => onCheckedChange?.(e.target.checked)}
      {...rest}
    />
  );
}

/** A RadioButton in a 48 px target. */
export function Radio({ onCheckedChange, ...rest }: Props) {
  return (
    <input type="radio" className="m3-radio m3-state" onChange={(e) => onCheckedChange?.(e.target.checked)} {...rest} />
  );
}

/** A Switch, announced as a switch. */
export function Switch({ onCheckedChange, ...rest }: Props) {
  return (
    <input
      type="checkbox"
      role="switch"
      className="m3-switch"
      onChange={(e) => onCheckedChange?.(e.target.checked)}
      {...rest}
    />
  );
}
