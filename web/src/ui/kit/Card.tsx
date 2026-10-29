import type { HTMLAttributes, ReactNode } from 'react';
import { ripple } from './ripple';

type Props = HTMLAttributes<HTMLElement> & {
  variant?: 'outlined' | 'filled' | 'elevated';
  /** A clickable card is a button, so it is announced and focusable like the Compose one. */
  onClick?: () => void;
  children: ReactNode;
};

/** OutlinedCard, Card and ElevatedCard with 16 px corners (DESIGN 6.4). */
export function Card({ variant = 'outlined', onClick, className, children, ...rest }: Props) {
  const classes = `m3-card m3-card--${variant} ${onClick ? 'm3-state' : ''} ${className ?? ''}`;
  if (onClick) {
    return (
      <button type="button" className={classes} onClick={onClick} onPointerDown={ripple} {...rest}>
        {children}
      </button>
    );
  }
  return (
    <section className={classes} {...rest}>
      {children}
    </section>
  );
}
