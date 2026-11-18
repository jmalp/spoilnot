import { Icon } from './Icon';
import type { ReactNode } from 'react';
import { ripple } from './ripple';

/**
 * A ListItem: headline, optional supporting text, leading and trailing content. With [onClick] the whole row is a
 * button, like Modifier.clickable on the Compose one.
 */
export function ListItem({
  headline,
  supporting,
  leading,
  trailing,
  onClick,
  testId,
}: {
  headline: ReactNode;
  supporting?: ReactNode;
  leading?: ReactNode;
  trailing?: ReactNode;
  onClick?: () => void;
  testId?: string;
}) {
  const body = (
    <>
      {leading && <span className="m3-list-item__leading">{leading}</span>}
      <span className="m3-list-item__text">
        <span className="m3-list-item__headline">{headline}</span>
        {supporting && <span className="m3-list-item__supporting">{supporting}</span>}
      </span>
      {trailing && <span className="m3-list-item__trailing">{trailing}</span>}
    </>
  );
  const classes = `m3-list-item ${supporting ? 'm3-list-item--two-line' : ''}`;
  return onClick ? (
    <button
      type="button"
      className={`${classes} m3-state`}
      onClick={onClick}
      onPointerDown={ripple}
      data-testid={testId}
    >
      {body}
    </button>
  ) : (
    <div className={classes} data-testid={testId}>
      {body}
    </div>
  );
}

/** An error line announced by screen readers when it appears (ErrorText); nothing when [text] is null. */
export function ErrorText({ text }: { text: string | null | undefined }) {
  // The 2026-11 look (DESIGN 19.12, step 8): an icon beside the message, as a field's error has, so an error is never
  // told by its color alone. The icon is decoration; the words are read.
  return (
    <p className="sn-error-text" role="status" aria-live="polite" hidden={!text}>
      {text && <Icon name="round/error_outline" size={18} />}
      <span>{text}</span>
    </p>
  );
}
