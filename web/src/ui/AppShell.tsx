import { str, type StringId } from '../core/i18n';
import { useNav } from '../core/NavProvider';
import { top, type Route, type Tab } from '../core/navigation';
import { NavigationBar, SnackbarProvider, type IconName, type NavigationItem } from './kit';
import { DetailScaffold, EmptyState, TabScaffold } from './Scaffold';
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
  const openSettings = () => nav.navigate({ name: 'settings' });
  if (route.name === 'settings') {
    return (
      <DetailScaffold title={str('settings')} onBack={nav.back} testId="screen_settings">
        <EmptyState icon="round/account_circle" message={str('web_coming_soon')} />
      </DetailScaffold>
    );
  }
  const item = TAB_ITEMS.find((t) => t.id === tab)!;
  return (
    <TabScaffold title={item.label} onOpenSettings={openSettings} testId={`screen_${tab}`}>
      <EmptyState icon={item.selectedIcon} message={str('web_coming_soon')} />
    </TabScaffold>
  );
}
