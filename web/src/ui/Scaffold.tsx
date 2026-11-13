import { createContext, useContext, useEffect, useState, type ReactNode, type UIEvent } from 'react';
import { str } from '../core/i18n';
import { Button, Icon, IconButton, TopAppBar, useOptionalSnackbars, useSnackbars, type IconName } from './kit';
import './scaffold.css';

const ScrolledContext = createContext<(scrolled: boolean) => void>(() => undefined);

/**
 * For a list that scrolls on its own below fixed content (innerScroll): tells the top app bar when content is under
 * it, as the pinned bar's scroll behavior does in the app.
 */
export function useOnInnerScroll(): (event: UIEvent<HTMLElement>) => void {
  const setScrolled = useContext(ScrolledContext);
  return (event) => setScrolled(event.currentTarget.scrollTop > 0);
}

/**
 * A tab's screen (TabScaffold in the app): the pinned top app bar with a headlineSmall title, the screen's
 * actions and the Settings avatar, content centered at most 640 px wide, and an optional extended FAB. With
 * [innerScroll] the content fills the screen and scrolls itself (a fixed search and filters above a list).
 */
export function TabScaffold({
  title,
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
  onOpenSettings: () => void;
  actions?: ReactNode;
  fab?: ReactNode;
  joinRequests?: number;
  innerScroll?: boolean;
  testId?: string;
  children: ReactNode;
}) {
  const [scrolled, setScrolled] = useState(false);
  const snackbars = useSnackbars();
  // Snackbars show above the FAB, as Scaffold places them.
  useEffect(() => {
    if (!fab) return;
    snackbars.setBottomInset(56 + 16);
    return () => snackbars.setBottomInset(0);
  }, [fab, snackbars]);
  return (
    <div
      className={`sn-screen ${innerScroll ? 'sn-screen--inner-scroll' : ''}`}
      data-testid={testId}
      onScroll={(e) => setScrolled(e.currentTarget.scrollTop > 0)}
    >
      <TopAppBar
        title={title}
        titleStyle="headline"
        scrolled={scrolled}
        actions={
          <>
            {actions}
            <IconButton
              icon="round/account_circle"
              label={joinRequests > 0 ? `${str('open_settings')} (${joinRequests})` : str('open_settings')}
              badge={joinRequests}
              onClick={onOpenSettings}
            />
          </>
        }
      />
      <ScrolledContext.Provider value={setScrolled}>
        <main className="sn-screen__content">{children}</main>
      </ScrolledContext.Provider>
      {fab && <div className="sn-screen__fab">{fab}</div>}
    </div>
  );
}

/**
 * A pushed screen (DetailScaffold): a titleLarge title with a back button, and an optional extended FAB or a bar under
 * the content (after v1, UX-12: a bar never covers what scrolls above it).
 */
export function DetailScaffold({
  title,
  onBack,
  actions,
  fab,
  bottomBar,
  testId,
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
  children: ReactNode;
}) {
  const [scrolled, setScrolled] = useState(false);
  // Snackbars show above the FAB or the bar; screens before sign-in have neither.
  const snackbars = useOptionalSnackbars();
  const inset = bottomBar ? BOTTOM_BAR_PX : fab ? 56 + 16 : 0;
  useEffect(() => {
    if (!inset || !snackbars) return;
    snackbars.setBottomInset(inset);
    return () => snackbars.setBottomInset(0);
  }, [inset, snackbars]);
  const screen = (
    <div className="sn-screen" data-testid={testId} onScroll={(e) => setScrolled(e.currentTarget.scrollTop > 0)}>
      <TopAppBar
        title={title}
        scrolled={scrolled}
        navigation={<IconButton icon="round/arrow_back" label={str('back')} onClick={onBack} />}
        actions={actions}
      />
      <main className="sn-screen__content">{children}</main>
    </div>
  );
  if (bottomBar) {
    return (
      <div className="sn-screen-frame sn-screen-frame--bar">
        <div className="sn-screen-frame__main">{screen}</div>
        <div className="sn-bottom-bar">{bottomBar}</div>
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
  return (
    <div className="sn-load-error" data-testid="load_error" role="alert">
      <Icon name="round/error_outline" size={20} />
      <span className="t-body-medium sn-load-error__text">{str('load_error')}</span>
      <Button variant="text" data-testid="load_error_retry" onClick={onRetry}>
        {str('action_retry')}
      </Button>
    </div>
  );
}

/** Shown while data comes from the phone only (OfflineBanner, DESIGN 9.5). */
export function OfflineBanner() {
  return (
    <div className="sn-offline" data-testid="offline_banner" role="status">
      <Icon name="round/cloud_off" size={16} />
      <span className="t-body-medium">{str('offline_banner')}</span>
    </div>
  );
}
