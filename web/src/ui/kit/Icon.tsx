import { ICONS, type IconName } from '../icons.generated';

export type { IconName };

/**
 * A Material icon (DESIGN 18.2). Decorative by default; with [label] it is announced like a contentDescription.
 * The markup is generated from the icon package at build time, never from data.
 */
export function Icon({ name, size = 24, label }: { name: IconName; size?: number; label?: string }) {
  return (
    <svg
      className="m3-icon"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      dangerouslySetInnerHTML={{ __html: ICONS[name] }}
    />
  );
}
