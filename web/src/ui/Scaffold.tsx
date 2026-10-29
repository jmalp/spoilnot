import { useState, type ReactNode } from 'react';
import { str } from '../core/i18n';
import { Icon, IconButton, TopAppBar, type IconName } from './kit';
import './scaffold.css';

/**
 * A tab's screen (TabScaffold in the app): the pinned top app bar with a headlineSmall title, the screen's
 * actions and the Settings avatar, content centered at most 640 px wide, and an optional extended FAB.
 */
export function TabScaffold({
  title,
  onOpenSettings,
  actions,
  fab,
  joinRequests = 0,
  testId,
  children,
}: {
  title: string;
  onOpenSettings: () => void;
  actions?: ReactNode;
  fab?: ReactNode;
  joinRequests?: number;
  testId?: string;
  children: ReactNode;
}) {
  const [scrolled, setScrolled] = useState(false);
  return (
    <div className="sn-screen" data-testid={testId} onScroll={(e) => setScrolled(e.currentTarget.scrollTop > 0)}>
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
      <main className="sn-screen__content">{children}</main>
      {fab && <div className="sn-screen__fab">{fab}</div>}
    </div>
  );
}

/** A pushed screen (DetailScaffold): a titleLarge title with a back button. */
export function DetailScaffold({
  title,
  onBack,
  actions,
  testId,
  children,
}: {
  title: string;
  onBack: () => void;
  actions?: ReactNode;
  testId?: string;
  children: ReactNode;
}) {
  const [scrolled, setScrolled] = useState(false);
  return (
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
