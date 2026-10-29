import type { ButtonHTMLAttributes } from 'react';
import { Icon, type IconName } from './Icon';
import { ripple } from './ripple';

type Props = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> & { icon: IconName; label: string };

/** An ExtendedFloatingActionButton; it stays extended while scrolling (DECISIONS 2026-10-19). */
export function Fab({ icon, label, className, ...rest }: Props) {
  return (
    <button type="button" className={`m3-fab m3-state ${className ?? ''}`} onPointerDown={ripple} {...rest}>
      <Icon name={icon} />
      {label}
    </button>
  );
}
