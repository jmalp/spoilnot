import type { ButtonHTMLAttributes } from 'react';
import { Icon, type IconName } from './Icon';
import { ripple } from './ripple';

type Props = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> & {
  label: string;
  selected: boolean;
  /** Shown when not selected; a selected chip shows a check, so the choice never depends on color. */
  icon?: IconName;
  /** One of a group where only one can be chosen: read as a radio button, in a role="radiogroup" parent. */
  radio?: boolean;
};

/** A FilterChip that toggles; selected ones have a check (DESIGN 6, never color alone). */
export function FilterChip({ label, selected, icon, radio = false, className, ...rest }: Props) {
  const leading = selected ? 'round/check' : icon;
  return (
    <button
      type="button"
      className={`m3-chip m3-state ${leading ? 'm3-chip--with-icon' : ''} ${className ?? ''}`}
      role={radio ? 'radio' : undefined}
      aria-checked={radio ? selected : undefined}
      aria-pressed={radio ? undefined : selected}
      onPointerDown={ripple}
      {...rest}
    >
      {leading && <Icon name={leading} />}
      {label}
    </button>
  );
}
