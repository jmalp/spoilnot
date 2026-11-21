import { useEffect, useLayoutEffect, useState, type CSSProperties, type ReactNode } from 'react';
import { useTrueAfter } from '../core/hooks';
import { plural, str } from '../core/i18n';
import {
  Button,
  Icon,
  IconButton,
  RoundIconButton,
  TopAppBar,
  useOptionalSnackbars,
  useSnackbars,
  type IconName,
} from './kit';
import { ripple } from './kit/ripple';
import './scaffold.css';

/** The floating button at 100% text (64 px) and its 16 px margin: snackbars show above it, as Scaffold places them. */
const FAB_INSET_PX = 64 + 16;

/**
 * A tab's screen in the 2026-11 look (TabScaffold in the app, DESIGN 19.7): the tinted page, a pinned header with the
 * [eyebrow] line over the title, the screen's actions and the Settings button, content centered at most 640 px wide,
 * and an optional extended FAB. The header is the old top app bar's 64 px at 100% text and keeps its color while the
 * content scrolls. With [innerScroll] the content fills the screen and scrolls itself (a fixed search and filters
 * above a list).
 */
export function TabScaffold({
  title,
  eyebrow,
  onOpenSettings,
  actions,
  fab,
  joinRequests = 0,
  innerScroll = false,
  testId,
  children,
}: {
  /** The title, or a control such as the grocery list switcher. */
  title: ReactNode;
  /** The line above the title: the date, or how many items (DESIGN 19.7). */
  eyebrow?: string;
  onOpenSettings: () => void;
  actions?: ReactNode;
  fab?: ReactNode;
  joinRequests?: number;
  innerScroll?: boolean;
  testId?: string;
  children: ReactNode;
}) {
  const snackbars = useSnackbars();
  const [fabRef, fabSpace] = useFabSpace(Boolean(fab));
  useEffect(() => {
    if (!fab) return;
    snackbars.setBottomInset(FAB_INSET_PX);
    return () => snackbars.setBottomInset(0);
  }, [fab, snackbars]);
  return (
    <div
      className={`sn-screen sn-screen--page ${innerScroll ? 'sn-screen--inner-scroll' : ''}`}
      data-testid={testId}
      style={fabSpace}
    >
      <header className="sn-screen-header">
        <div className="sn-screen-header__titles">
          {eyebrow && (
            <p className="t-screen-eyebrow c-on-surface-variant sn-screen-header__eyebrow" data-testid="screen_eyebrow">
              {eyebrow}
            </p>
          )}
          <h1 className="t-screen-title sn-screen-header__title">{title}</h1>
        </div>
        <div className="sn-screen-header__actions">
          {actions}
          <SettingsButton joinRequests={joinRequests} onClick={onOpenSettings} />
        </div>
      </header>
      <main className="sn-screen__content">{children}</main>
      {fab && (
        <div className="sn-screen__fab" ref={fabRef}>
          {fab}
        </div>
      )}
    </div>
  );
}

/**
 * The button that opens Settings (DESIGN 6.2, 19.7): a 48 px tonal circle with a person, and the owner's waiting join
 * requests as a badge. "Open settings, 1 join request waiting", as the app says it; the badge's number isn't read
 * again.
 */
function SettingsButton({ joinRequests, onClick }: { joinRequests: number; onClick: () => void }) {
  const label =
    joinRequests > 0 ? plural('open_settings_with_requests', joinRequests, joinRequests) : str('open_settings');
  return (
    <button
      type="button"
      className="sn-settings-button m3-state"
      aria-label={label}
      title={label}
      onClick={onClick}
      onPointerDown={ripple}
    >
      <Icon name="round/person" />
      {joinRequests > 0 && (
        <span className="m3-badge" aria-hidden>
          {joinRequests}
        </span>
      )}
    </button>
  );
}

/** The FAB's 16 px margin below it and 16 px of air above it. */
const FAB_MARGINS_PX = 32;

/**
 * The room a tab's list leaves under its last row for the floating button, as measured (listBottomPadding() in the
 * app, after v1, review UX-14): --sn-fab-space on the screen, which grows with the button at large text sizes. Lists
 * use at least 96 px (the button at 100 %).
 */
function useFabSpace(hasFab: boolean): [(el: HTMLDivElement | null) => void, CSSProperties | undefined] {
  const [height, setHeight] = useState(0);
  const [element, setElement] = useState<HTMLDivElement | null>(null);
  useLayoutEffect(() => {
    if (!element) return;
    const measure = () => setHeight(element.getBoundingClientRect().height);
    measure();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [element]);
  const style =
    hasFab && height > 0 ? ({ '--sn-fab-space': `${height + FAB_MARGINS_PX}px` } as CSSProperties) : undefined;
  return [setElement, style];
}

/**
 * A pushed screen (DetailScaffold): a titleLarge title with a back button, and an optional extended FAB or a bar under
 * the content (after v1, UX-12: a bar never covers what scrolls above it). On the page's color, the bar too, until the
 * screen's own step of the new look (DESIGN 19.7).
 *
 * [header] picks the 2026-11 look's tops: 'pushed' is the round Back on its own line with the [actions] at its end, the
 * [title] under it (PushedScaffold in the app); 'overlay' puts the round Back and the actions over the content, which
 * runs under the status bar (a recipe's hero). Both take the look's bar for the main button ([bottomBar]).
 */
export function DetailScaffold({
  title,
  onBack,
  actions,
  fab,
  bottomBar,
  testId,
  header = 'bar',
  eyebrow,
  children,
}: {
  title: string;
  onBack: () => void;
  actions?: ReactNode;
  /** Stays in place while the content scrolls under it. */
  fab?: ReactNode;
  /** Across the bottom, under the content, which scrolls clear of it. */
  bottomBar?: ReactNode;
  testId?: string;
  header?: 'bar' | 'pushed' | 'overlay';
  /** With header 'pushed', a line above the title (PushedScaffold's eyebrow in the app): Your kitchen's month. */
  eyebrow?: string;
  children: ReactNode;
}) {
  // Snackbars show above the FAB or the bar; screens before sign-in have neither.
  const snackbars = useOptionalSnackbars();
  const look = header !== 'bar';
  const inset = bottomBar ? (look ? BOTTOM_BAR_LOOK_PX : BOTTOM_BAR_PX) : fab ? FAB_INSET_PX : 0;
  useEffect(() => {
    if (!inset || !snackbars) return;
    snackbars.setBottomInset(inset);
    return () => snackbars.setBottomInset(0);
  }, [inset, snackbars]);
  const back = <RoundIconButton icon="round/arrow_back" label={str('back')} onClick={onBack} />;
  const top =
    header === 'pushed' ? (
      <header className="sn-pushed-header">
        <div className="sn-pushed-header__row">
          {back}
          <span className="sn-pushed-header__spacer" />
          {actions}
        </div>
        {eyebrow && (
          <p className="t-screen-eyebrow c-on-surface-variant sn-pushed-header__eyebrow" data-testid="screen_eyebrow">
            {eyebrow}
          </p>
        )}
        {title && (
          <h1 className={`t-screen-title sn-pushed-header__title ${eyebrow ? 'sn-pushed-header__title--under' : ''}`}>
            {title}
          </h1>
        )}
      </header>
    ) : header === 'overlay' ? (
      // Pinned with no height of its own, so the content starts at the very top and scrolls under the buttons.
      <header className="sn-overlay-header">
        <div className="sn-pushed-header__row sn-overlay-header__row">
          {back}
          <span className="sn-pushed-header__spacer" />
          {actions}
        </div>
      </header>
    ) : (
      <TopAppBar
        title={title}
        page
        navigation={<IconButton icon="round/arrow_back" label={str('back')} onClick={onBack} />}
        actions={actions}
      />
    );
  const screen = (
    <div className="sn-screen sn-screen--page" data-testid={testId}>
      {top}
      <main className="sn-screen__content">{children}</main>
    </div>
  );
  if (bottomBar) {
    return (
      <div className="sn-screen-frame sn-screen-frame--bar">
        <div className="sn-screen-frame__main">{screen}</div>
        <div className={`sn-bottom-bar ${look ? 'sn-bottom-bar--look' : ''}`}>{bottomBar}</div>
      </div>
    );
  }
  if (!fab) return screen;
  return (
    <div className="sn-screen-frame">
      {screen}
      <div className="sn-screen__fab">{fab}</div>
    </div>
  );
}

/** A bottom bar's height: a 48 px button with 12 px above and below. */
const BOTTOM_BAR_PX = 72;

/** The 2026-11 look's bar: the 56 px main button with 12 px above and below (BottomActionBar in the app). */
const BOTTOM_BAR_LOOK_PX = 80;

/** A one-line explanation with an icon, and ways to start under it (EmptyState, DESIGN 6.7). */
export function EmptyState({ icon, message, children }: { icon: IconName; message: string; children?: ReactNode }) {
  return (
    <div className="sn-empty">
      <span className="c-primary">
        <Icon name={icon} size={48} />
      </span>
      <p className="t-body-large c-on-surface-variant">{message}</p>
      {children && <div className="sn-empty__actions">{children}</div>}
    </div>
  );
}

/**
 * DESIGN 6.7: the free daily quota was reached; it resets at midnight Pacific time. Shown above every top-level screen
 * (App, after v1 WEB-09), so it takes the status bar's space itself.
 */
export function QuotaBanner() {
  return (
    <div className="sn-quota" data-testid="quota_banner" role="status">
      <Icon name="round/cloud_off" size={24} />
      <span className="t-body-medium">{str('error_quota')}</span>
    </div>
  );
}

/**
 * "Couldn't load." with Retry (DESIGN 6.7, after v1; LoadError in the app): shown while one of the screen's listeners
 * is failing, where the Offline banner goes, above what the screen still shows. The icon and the words carry the
 * status, not the color alone.
 */
export function LoadError({ onRetry }: { onRetry: () => void }) {
  // The 2026-11 look (DESIGN 19.11): a rounded banner, first in the screen's content.
  return (
    <div className="sn-load-error" data-testid="load_error" role="alert">
      <Icon name="round/error_outline" size={22} />
      <span className="t-body-large sn-load-error__text">{str('load_error')}</span>
      <Button variant="text" className="t-button-large" data-testid="load_error_retry" onClick={onRetry}>
        {str('action_retry')}
      </Button>
    </div>
  );
}

/**
 * How long a screen's data must keep coming from the phone before the Offline banner shows (OFFLINE_BANNER_DELAY_MS
 * in the app; DESIGN 9.5, after v1, 2026-11-21). A screen that opens or starts a new query (Plan's next week) reads
 * the phone first and the server a moment later; without the wait the banner showed for that moment and moved
 * everything under it twice.
 */
export const OFFLINE_BANNER_DELAY_MS = 2_000;

/** Shown once data has come from the phone only for OFFLINE_BANNER_DELAY_MS (OfflineBanner, DESIGN 9.5). */
export function OfflineBanner({ offline }: { offline: boolean }) {
  const shown = useTrueAfter(offline, OFFLINE_BANNER_DELAY_MS);
  if (!offline) return null;
  // While it waits, a hidden mark says the screen is still on the phone's data: the browser tests' sign that the
  // server hasn't answered yet, which the missing banner no longer is.
  if (!shown) return <span hidden data-testid="offline_waiting" />;
  return (
    <div className="sn-offline" data-testid="offline_banner" role="status">
      <Icon name="round/cloud_off" size={20} />
      <span className="t-label-large">{str('offline_banner')}</span>
    </div>
  );
}
