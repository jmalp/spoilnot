import { useEffect, useMemo, useRef, useState } from 'react';
import { catchError, concatMap, firstValueFrom, map, of, timeout } from 'rxjs';
import { errorText } from '../core/errors';
import { useObservable } from '../core/hooks';
import { str, type StringId } from '../core/i18n';
import { useNav } from '../core/NavProvider';
import { useHouseholdSession, useServices } from '../core/services';
import { DeepLinks, OPEN_ROUTE_MESSAGE } from '../core/deepLinks';
import { onUpdateReady, setUpdateRequired } from '../core/updates';
import { WEB_BUILD } from '../core/version';
import { DEFAULT_QUERY, type PantryQuery } from '../domain/pantry/items';
import { DEFAULT_RECIPE_QUERY, type RecipeQuery } from '../domain/recipes/box';
import { nextPrompt, updateRequired, type UpdatePrompt as Prompt } from '../domain/updates';
import { GroceriesScreen } from './grocery/GroceriesScreen';
import { HomeScreen } from './home/HomeScreen';
import { HouseholdSettingsScreen } from './household/HouseholdSettingsScreen';
import { InsightsScreen } from './insights/InsightsScreen';
import { PlanScreen } from './plan/PlanScreen';
import { CookModeScreen } from './recipes/CookModeScreen';
import { RecipeDetailScreen } from './recipes/RecipeDetailScreen';
import { RecipeEditScreen } from './recipes/RecipeEditScreen';
import { RecipesScreen } from './recipes/RecipesScreen';
import { PantryScreen } from './pantry/PantryScreen';
import { NotificationExplainer } from './notifications/NotificationExplainer';
import { NotificationSettingsScreen } from './notifications/NotificationSettingsScreen';
import { useReminders, useTurnOnReminders } from './notifications/reminders';
import { AboutScreen } from './settings/AboutScreen';
import { SettingsScreen } from './settings/SettingsScreen';
import { top, type Route, type Tab } from '../core/navigation';
import {
  INDEFINITE,
  LONG_MS,
  NavigationBar,
  SnackbarProvider,
  useSnackbars,
  type IconName,
  type NavigationItem,
} from './kit';
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
  const { restock } = useServices();
  // The restock engine runs on every phone while the household is on screen (DESIGN 9.6): it starts with the
  // session's shell, not at page load, and reads the cache only (after v1).
  useEffect(() => {
    restock.start();
    return () => restock.stop();
  }, [restock]);
  // Reminders are explained on whichever tab is open (after v1, review UX-02), and Home says when they are off.
  const reminders = useReminders();
  const turnOn = useTurnOnReminders();
  // When the system no longer asks, Settings > Notifications says where to turn them on.
  const turnOnReminders = () => void turnOn(() => nav.navigate({ name: 'notifications' }));
  return (
    <div className="sn-shell">
      <div className="sn-shell__main">
        <SnackbarProvider>
          <Screen
            tab={nav.state.tab}
            route={route}
            reminders={{ off: reminders.offOnThisPhone, turnOn: turnOnReminders }}
          />
          {reminders.ask && (
            <NotificationExplainer hour={reminders.hour} onTurnOn={turnOnReminders} onLater={reminders.later} />
          )}
          <UpdatePrompt />
          <WriteRefusals />
        </SnackbarProvider>
      </div>
      <NavigationBar items={TAB_ITEMS} selected={nav.state.tab} onSelect={nav.selectTab} />
    </div>
  );
}

/**
 * Writes the server refused after the action returned, from any screen (after v1, review REL-13): reported once here,
 * whichever tab or screen is open, instead of by Pantry, Plan, Groceries and Recipes when they happened to be open. A
 * refusal that lost to another phone names who won (DESIGN 8.5), from one read of the member list.
 */
function WriteRefusals() {
  const snackbars = useSnackbars();
  const me = useHouseholdSession();
  const { writer, households } = useServices();
  useEffect(() => {
    const subscription = writer.errors$
      .pipe(
        concatMap(async (error) => {
          const winner = error.byUid !== null && error.byUid !== me.user.uid ? error.byUid : null;
          const name =
            winner === null
              ? undefined
              : await firstValueFrom(
                  households.observeMembers(me.householdId).pipe(
                    map((all) => all.find((m) => m.uid === winner)?.displayName),
                    timeout(MEMBER_NAME_WAIT_MS),
                    catchError(() => of(undefined)),
                  ),
                );
          return errorText(error, () => name, me.user.uid);
        }),
      )
      .subscribe((text) => snackbars.show(text));
    return () => subscription.unsubscribe();
  }, [writer, households, snackbars, me.householdId, me.user.uid]);
  return null;
}

/** How long a refusal's message waits for the member list to name who won. */
const MEMBER_NAME_WAIT_MS = 3_000;

/**
 * A new version is ready (DESIGN 18.5): Update reloads into it; otherwise it starts with the next launch. When
 * config/app.minWebBuild is newer than this build, the prompt stays until Update is tapped.
 */
function UpdatePrompt() {
  const snackbars = useSnackbars();
  const { appConfig } = useServices();
  const required = useObservable(
    useMemo(() => appConfig.observe().pipe(map((config) => updateRequired(config, WEB_BUILD))), [appConfig]),
    false,
  );
  const [apply, setApply] = useState<{ run: () => void } | null>(null);
  // What was shown for the waiting version (a new one starts again).
  const shown = useRef<{ run: () => void; prompt: Prompt } | null>(null);
  useEffect(() => onUpdateReady((run) => setApply({ run })), []);
  useEffect(() => {
    setUpdateRequired(required);
    return () => setUpdateRequired(false);
  }, [required]);
  useEffect(() => {
    const before = apply && shown.current?.run === apply.run ? shown.current.prompt : null;
    const prompt = nextPrompt(apply !== null, required, before);
    if (!apply || !prompt) return;
    shown.current = { run: apply.run, prompt };
    snackbars.showAction(
      str('web_update_ready'),
      str('web_update_reload'),
      apply.run,
      prompt === 'require' ? INDEFINITE : LONG_MS,
    );
  }, [apply, required, snackbars]);
  return null;
}

function Screen({
  tab,
  route,
  reminders,
}: {
  tab: Tab;
  route: Route;
  reminders: { off: boolean; turnOn: () => void };
}) {
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
  // The Pantry tab's search and filter live here, as its view model outlives the screen in the app.
  const [pantryQuery, setPantryQuery] = useState<PantryQuery>(() => {
    // A tapped digest opened the app at its route (DeepLink in the app).
    const link = DeepLinks.parse(location.pathname + location.search);
    return link?.tab === 'pantry' ? { ...DEFAULT_QUERY, filter: link.filter } : DEFAULT_QUERY;
  });
  // A digest tapped while the app is open: the service worker passes its route on (DESIGN 18.4).
  useEffect(() => {
    // The address keeps no query, so a reload does not apply the link again.
    if (location.search) history.replaceState(history.state, '', location.pathname);
    const worker = navigator.serviceWorker;
    if (!worker) return;
    const onMessage = (event: MessageEvent) => {
      const message = event.data as { type?: unknown; route?: unknown } | null;
      if (message?.type !== OPEN_ROUTE_MESSAGE) return;
      const link = DeepLinks.parse(typeof message.route === 'string' ? message.route : null);
      if (!link) return;
      if (link.tab === 'pantry') setPantryQuery((q) => ({ ...q, filter: link.filter }));
      nav.showRoot(link.tab);
    };
    worker.addEventListener('message', onMessage);
    return () => worker.removeEventListener('message', onMessage);
  }, [nav]);
  const [groceryListId, setGroceryListId] = useState<string | null>(null);
  const [recipeQuery, setRecipeQuery] = useState<RecipeQuery>(DEFAULT_RECIPE_QUERY);
  const [planWeek, setPlanWeek] = useState(0);
  // A recipe opens on the tab it was tapped in (RecipeDetailRoute(id, currentTab) in the app).
  const openRecipe = (id: string) => nav.navigate({ name: 'recipe', id });
  const openSettings = () => nav.navigate({ name: 'settings' });
  if (route.name === 'settings') {
    return (
      <SettingsScreen
        onBack={nav.back}
        onOpenHousehold={() => nav.navigate({ name: 'householdSettings' })}
        onOpenNotifications={() => nav.navigate({ name: 'notifications' })}
        onOpenAbout={() => nav.navigate({ name: 'about' })}
      />
    );
  }
  if (route.name === 'notifications') return <NotificationSettingsScreen onBack={nav.back} />;
  if (route.name === 'householdSettings') return <HouseholdSettingsScreen onBack={nav.back} />;
  if (route.name === 'about') return <AboutScreen onBack={nav.back} />;
  if (route.name === 'insights') return <InsightsScreen onBack={nav.back} />;
  if (route.name === 'recipe') {
    return (
      <RecipeDetailScreen
        key={route.id}
        recipeId={route.id}
        onBack={nav.back}
        onEdit={() => nav.navigate({ name: 'recipeEdit', id: route.id })}
        onCook={(servings) => nav.navigate({ name: 'cookMode', id: route.id, servings })}
      />
    );
  }
  if (route.name === 'recipeEdit') return <RecipeEditScreen key={route.id} recipeId={route.id} onBack={nav.back} />;
  if (route.name === 'cookMode') {
    return <CookModeScreen key={route.id} recipeId={route.id} servings={route.servings} onBack={nav.back} />;
  }
  if (tab === 'home') {
    return (
      <HomeScreen
        joinRequests={joinRequests}
        remindersOff={reminders.off}
        navigation={{
          openSettings,
          turnOnReminders: reminders.turnOn,
          // A Home card opens the Pantry tab at its root with its filter (DeepLink.Pantry in the app).
          openPantry: (filter) => {
            setPantryQuery((q) => ({ ...q, filter }));
            nav.showRoot('pantry');
          },
          openInsights: () => nav.navigate({ name: 'insights' }),
          openGroceries: () => nav.showRoot('groceries'),
          openRecipe,
          openPlan: () => nav.showRoot('plan'),
          reviewJoinRequests: () => nav.navigate({ name: 'householdSettings' }),
        }}
      />
    );
  }
  if (tab === 'pantry') {
    return (
      <PantryScreen
        onOpenSettings={openSettings}
        joinRequests={joinRequests}
        query={pantryQuery}
        onQueryChange={setPantryQuery}
      />
    );
  }
  if (tab === 'plan') {
    return (
      <PlanScreen
        onOpenSettings={openSettings}
        joinRequests={joinRequests}
        weekOffset={planWeek}
        onWeekOffset={setPlanWeek}
        onOpenRecipe={openRecipe}
      />
    );
  }
  if (tab === 'recipes') {
    return (
      <RecipesScreen
        onOpenSettings={openSettings}
        joinRequests={joinRequests}
        query={recipeQuery}
        onQueryChange={setRecipeQuery}
        onOpenRecipe={openRecipe}
      />
    );
  }
  if (tab === 'groceries') {
    return (
      <GroceriesScreen
        onOpenSettings={openSettings}
        joinRequests={joinRequests}
        listId={groceryListId}
        onSelectList={setGroceryListId}
      />
    );
  }
  const item = TAB_ITEMS.find((t) => t.id === tab)!;
  return (
    <TabScaffold title={item.label} onOpenSettings={openSettings} joinRequests={joinRequests} testId={`screen_${tab}`}>
      <EmptyState icon={item.selectedIcon} message={str('web_coming_soon')} />
    </TabScaffold>
  );
}
