import { useMemo } from 'react';
import { map, of } from 'rxjs';
import { useObservable } from '../core/hooks';
import { str, type StringId } from '../core/i18n';
import { useNav } from '../core/NavProvider';
import { useHouseholdSession, useServices } from '../core/services';
import { HouseholdSettingsScreen } from './household/HouseholdSettingsScreen';
import { SettingsScreen } from './settings/SettingsScreen';
import { top, type Route, type Tab } from '../core/navigation';
import { NavigationBar, SnackbarProvider, type IconName, type NavigationItem } from './kit';
import { EmptyState, TabScaffold } from './Scaffold';
import './shell.css';

/** The bottom-bar tabs in display order, with their icons (TopLevelDestination in the app). */
export const TAB_ITEMS: NavigationItem<Tab>[] = (
  [
    ['home', 'tab_home', 'home'],
    ['pantry', 'tab_pantry', 'kitchen'],
    ['plan', 'tab_plan', 'calendar_month'],
    ['recipes', 'tab_recipes', 'menu_book'],
    ['groceries', 'tab_groceries', 'shopping_cart'],
  ] as const
).map(([id, label, icon]) => ({
  id,
  label: str(label as StringId),
  icon: `outlined/${icon}` as IconName,
  selectedIcon: `round/${icon}` as IconName,
}));

/** The signed-in app (DESIGN 6.2): five tabs in a bottom navigation bar, each with its own back stack. */
export function AppShell() {
  const nav = useNav();
  const route = top(nav.state);
  return (
    <div className="sn-shell">
      <div className="sn-shell__main">
        <SnackbarProvider>
          <Screen tab={nav.state.tab} route={route} />
        </SnackbarProvider>
      </div>
      <NavigationBar items={TAB_ITEMS} selected={nav.state.tab} onSelect={nav.selectTab} />
    </div>
  );
}

function Screen({ tab, route }: { tab: Tab; route: Route }) {
  const nav = useNav();
  const me = useHouseholdSession();
  const { households } = useServices();
  // The owner's avatar carries a badge while someone waits for approval (DESIGN 5.1).
  const joinRequests = useObservable(
    useMemo(
      () =>
        me.role === 'owner'
          ? households
              .observeJoinRequests(me.householdId)
              .pipe(map((all) => all.filter((r) => r.status === 'pending').length))
          : of(0),
      [households, me.householdId, me.role],
    ),
    0,
  );
  const openSettings = () => nav.navigate({ name: 'settings' });
  if (route.name === 'settings') {
    return <SettingsScreen onBack={nav.back} onOpenHousehold={() => nav.navigate({ name: 'householdSettings' })} />;
  }
  if (route.name === 'householdSettings') return <HouseholdSettingsScreen onBack={nav.back} />;
  const item = TAB_ITEMS.find((t) => t.id === tab)!;
  return (
    <TabScaffold title={item.label} onOpenSettings={openSettings} joinRequests={joinRequests} testId={`screen_${tab}`}>
      <EmptyState icon={item.selectedIcon} message={str('web_coming_soon')} />
    </TabScaffold>
  );
}
