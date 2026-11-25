import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Icon, type IconName } from './Icon';
import { Progress } from './Progress';
import { ripple } from './ripple';

export type ButtonVariant = 'filled' | 'tonal' | 'outlined' | 'text';

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  icon?: IconName;
  /** An icon after the label, such as the arrow on Next. */
  trailingIcon?: IconName;
  /** Full width, like PrimaryButton in the app. */
  full?: boolean;
  /** A 48 px button (stretched to the touch target), for buttons in cards and secondary form buttons. */
  large?: boolean;
  /**
   * A screen's or sheet's main button (DESIGN 19.9; PrimaryButton in the app): full width, filled, 56 px, ButtonLarge.
   * Implies [full] and [large].
   */
  primary?: boolean;
  /** Error colors, for destructive text buttons such as Remove from plan. */
  danger?: boolean;
  /** Shows a small spinner instead of the label and ignores taps (PrimaryButton's busy). */
  busy?: boolean;
  children: ReactNode;
};

/** Material 3 buttons: Button, FilledTonalButton, OutlinedButton and TextButton. */
export function Button({
  variant = 'filled',
  icon,
  trailingIcon,
  full,
  large,
  primary,
  danger,
  busy,
  className,
  children,
  disabled,
  ...rest
}: Props) {
  const classes = [
    'm3-button',
    'm3-state',
    `m3-button--${variant}`,
    icon && 'm3-button--with-icon',
    trailingIcon && 'm3-button--with-trailing-icon',
    (full || primary) && 'm3-button--full',
    (large || primary) && 'm3-button--large',
    primary && 'm3-button--primary',
    danger && 'm3-button--error',
    className,
  ];
  return (
    <button
      type="button"
      className={classes.filter(Boolean).join(' ')}
      onPointerDown={ripple}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      {...rest}
    >
      {busy ? (
        <Progress small />
      ) : (
        <>
          {icon && <Icon name={icon} />}
          {children}
          {trailingIcon && <Icon name={trailingIcon} />}
        </>
      )}
    </button>
  );
}
