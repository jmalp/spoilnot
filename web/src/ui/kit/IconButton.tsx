import type { ButtonHTMLAttributes } from 'react';
import { Icon, type IconName } from './Icon';
import { ripple } from './ripple';

type Props = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> & {
  icon: IconName;
  /** What TalkBack and VoiceOver read (the contentDescription). */
  label: string;
  /** A number badge, such as waiting join requests on the Settings avatar. */
  badge?: number;
};

/** An IconButton: a 40 px state layer in a 48 px touch target. */
export function IconButton({ icon, label, badge, ...rest }: Props) {
  return (
    <button type="button" className="m3-icon-button" aria-label={label} title={label} onPointerDown={ripple} {...rest}>
      <span className="m3-icon-button__layer m3-state">
        <Icon name={icon} />
      </span>
      {badge ? (
        <span className="m3-badge" aria-hidden>
          {badge}
        </span>
      ) : null}
    </button>
  );
}
