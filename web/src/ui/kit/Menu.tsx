import { useEffect, useRef, type ReactNode } from 'react';
import { ripple } from './ripple';

/**
 * A DropdownMenu below its anchor: put it next to the button that opens it, inside a `position: relative`
 * element. A tap outside or Escape closes it.
 */
export function Menu({ open, onDismiss, children }: { open: boolean; onDismiss: () => void; children: ReactNode }) {
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
    ref.current?.querySelector<HTMLElement>('button:not(:disabled)')?.focus();
    return () => {
      document.removeEventListener('pointerdown', outside, true);
      document.removeEventListener('keydown', escape);
    };
  }, [open, onDismiss]);
  if (!open) return null;
  return (
    <div ref={ref} className="m3-menu" role="menu" style={{ top: '100%', right: 0 }}>
      {children}
    </div>
  );
}

export function MenuItem({
  label,
  onClick,
  disabled,
  testId,
}: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
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
      {label}
    </button>
  );
}
