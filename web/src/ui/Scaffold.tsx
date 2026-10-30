import { createContext, useContext, useEffect, useState, type ReactNode, type UIEvent } from 'react';
import { str } from '../core/i18n';
import { Icon, IconButton, TopAppBar, useOptionalSnackbars, useSnackbars, type IconName } from './kit';
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
              data-tour="tour_settings"
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

/** A pushed screen (DetailScaffold): a titleLarge title with a back button, and an optional extended FAB. */
export function DetailScaffold({
  title,
  onBack,
  actions,
  fab,
  testId,
  children,
}: {
  title: string;
  onBack: () => void;
  actions?: ReactNode;
  /** Stays in place while the content scrolls under it. */
  fab?: ReactNode;
  testId?: string;
  children: ReactNode;
}) {
  const [scrolled, setScrolled] = useState(false);
  // Snackbars show above the FAB; screens before sign-in have neither.
  const snackbars = useOptionalSnackbars();
  useEffect(() => {
    if (!fab || !snackbars) return;
    snackbars.setBottomInset(56 + 16);
    return () => snackbars.setBottomInset(0);
  }, [fab, snackbars]);
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
  if (!fab) return screen;
  return (
    <div className="sn-screen-frame">
      {screen}
      <div className="sn-screen__fab">{fab}</div>
    </div>
  );
}

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

/** Shown while data comes from the phone only (OfflineBanner, DESIGN 9.5). */
export function OfflineBanner() {
  return (
    <div className="sn-offline" data-testid="offline_banner" role="status">
      <Icon name="round/cloud_off" size={16} />
      <span className="t-body-medium">{str('offline_banner')}</span>
    </div>
  );
}
