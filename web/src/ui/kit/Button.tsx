import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Icon, type IconName } from './Icon';
import { ripple } from './ripple';

export type ButtonVariant = 'filled' | 'tonal' | 'outlined' | 'text';

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  icon?: IconName;
  /** Full width, like PrimaryButton in the app. */
  full?: boolean;
  /** The 56 px PrimaryButton the app uses at the bottom of forms. */
  large?: boolean;
  /** Error colors, for destructive text buttons such as Remove from plan. */
  danger?: boolean;
  children: ReactNode;
};

/** Material 3 buttons: Button, FilledTonalButton, OutlinedButton and TextButton. */
export function Button({ variant = 'filled', icon, full, large, danger, className, children, ...rest }: Props) {
  const classes = [
    'm3-button',
    'm3-state',
    `m3-button--${variant}`,
    icon && 'm3-button--with-icon',
    full && 'm3-button--full',
    large && 'm3-button--large',
    danger && 'm3-button--error',
    className,
  ];
  return (
    <button type="button" className={classes.filter(Boolean).join(' ')} onPointerDown={ripple} {...rest}>
      {icon && <Icon name={icon} />}
      {children}
    </button>
  );
}
