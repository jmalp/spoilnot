import { useEffect, useRef, type ReactNode } from 'react';
import { ripple } from './ripple';

/**
 * A DropdownMenu below its anchor: put it next to the button that opens it, inside a `position: relative`
 * element. A tap outside or Escape closes it.
 */
export function Menu({
  open,
  onDismiss,
  children,
  align = 'end',
  focusFirst = true,
}: {
  open: boolean;
  onDismiss: () => void;
  children: ReactNode;
  /** The edge of the anchor the menu lines up with. */
  align?: 'start' | 'end';
  /**
   * Moves focus to the first item, as a menu opened from a button does. Suggestions under a text field
   * (ExposedDropdownMenu) leave focus in the field, so typing goes on.
   */
  focusFirst?: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const outside = (event: Event) => {
      if (!ref.current?.contains(event.target as Node)) onDismiss();
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onDismiss();
    };
    document.addEventListener('pointerdown', outside, true);
    document.addEventListener('keydown', escape);
    if (focusFirst) ref.current?.querySelector<HTMLElement>('button:not(:disabled)')?.focus();
    return () => {
      document.removeEventListener('pointerdown', outside, true);
      document.removeEventListener('keydown', escape);
    };
  }, [open, onDismiss, focusFirst]);
  if (!open) return null;
  return (
    <div
      ref={ref}
      className="m3-menu"
      role="menu"
      style={align === 'end' ? { top: '100%', right: 0 } : { top: '100%', left: 0 }}
    >
      {children}
    </div>
  );
}

export function MenuItem({
  label,
  onClick,
  disabled,
  leading,
  testId,
}: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
  /** A leading icon, such as the check on the chosen sort. */
  leading?: React.ReactNode;
  testId?: string;
}) {
  return (
    <button
      type="button"
      role="menuitem"
      className="m3-menu__item m3-state"
      disabled={disabled}
      data-testid={testId}
      onClick={onClick}
      onPointerDown={ripple}
    >
      {leading}
      {label}
    </button>
  );
}

/** A divider between groups of menu items. */
export function MenuDivider() {
  return <hr className="m3-menu__divider" />;
}
