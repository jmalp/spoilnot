import type { HTMLAttributes, ReactNode } from 'react';
import { Icon, type IconName } from './Icon';
import './look.css';

// The shared pieces of the 2026-11 look (DESIGN 19; core/designsystem in the app). The web adds each piece with the
// first screen that uses it: these are the ones the shell and Home use.

/**
 * A group of rows (DESIGN 19.5; ListSegment in the app): rows 2 px apart, 20 px outside corners and 6 px where two
 * meet, on the row surface; [nested] for rows inside a highlight card (18 px, the nested row surface). The corners
 * come from each row's place in the group, so give each row the class sn-segment.
 */
export function ListGroup({
  nested = false,
  className,
  children,
  ...rest
}: HTMLAttributes<HTMLDivElement> & { nested?: boolean; children: ReactNode }) {
  return (
    <div className={`sn-group ${nested ? 'sn-group--nested' : ''} ${className ?? ''}`} {...rest}>
      {children}
    </div>
  );
}

/**
 * The leading tile of a row (DESIGN 19.6; StatusTile in the app): the status icon in its fixed color on that color at
 * 12% over the row, a neutral tile for no date. Decoration: the row says the status in words.
 */
export function StatusTile({ tone, icon, nested = false }: { tone: string; icon: IconName; nested?: boolean }) {
  return (
    <span className={`sn-status-tile sn-status-tile--${tone} ${nested ? 'sn-status-tile--nested' : ''}`} aria-hidden>
      <Icon name={icon} size={nested ? 22 : 24} />
    </span>
  );
}

/**
 * The one card a screen wants read first (DESIGN 19.8; HighlightCard in the app): primaryContainer, 28 px corners.
 */
export function HighlightCard({ className, children, ...rest }: HTMLAttributes<HTMLElement> & { children: ReactNode }) {
  return (
    <section className={`sn-highlight-card ${className ?? ''}`} {...rest}>
      {children}
    </section>
  );
}

/** A card that suggests something (DESIGN 19.8; AccentCard in the app): tertiaryContainer, 24 px corners. */
export function AccentCard({ className, children, ...rest }: HTMLAttributes<HTMLElement> & { children: ReactNode }) {
  return (
    <section className={`sn-accent-card ${className ?? ''}`} {...rest}>
      {children}
    </section>
  );
}

/** A plain card (DESIGN 19.8): the row surface with 24 px corners, no outline. */
export function PlainCard({ className, children, ...rest }: HTMLAttributes<HTMLElement> & { children: ReactNode }) {
  return (
    <section className={`sn-plain-card ${className ?? ''}`} {...rest}>
      {children}
    </section>
  );
}

/** The leaf from the app icon holding an icon (DESIGN 19.3; LeafBadge in the app), in the accent card's colors. */
export function LeafBadge({ icon }: { icon: IconName }) {
  return (
    <span className="sn-leaf-badge" aria-hidden>
      <Icon name={icon} />
    </span>
  );
}

/**
 * A small label on a row (DESIGN 19.10; LabelChip in the app): where an item came from, or who (a person). A label,
 * not a control.
 */
export function LabelChip({
  text,
  icon,
  kind = 'source',
}: {
  text: string;
  icon?: IconName;
  kind?: 'source' | 'person';
}) {
  return (
    <span className={`sn-label-chip sn-label-chip--${kind} t-chip-label`}>
      {icon && <Icon name={icon} size={14} />}
      {text}
    </span>
  );
}
