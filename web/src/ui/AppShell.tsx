import { useMemo, useState } from 'react';
import { map, of } from 'rxjs';
import { useObservable } from '../core/hooks';
import { str, type StringId } from '../core/i18n';
import { useNav } from '../core/NavProvider';
import { useHouseholdSession, useServices } from '../core/services';
import { DEFAULT_QUERY, type PantryQuery } from '../domain/pantry/items';
import { DEFAULT_RECIPE_QUERY, type RecipeQuery } from '../domain/recipes/box';
import { GroceriesScreen } from './grocery/GroceriesScreen';
import { HomeScreen } from './home/HomeScreen';
import { HouseholdSettingsScreen } from './household/HouseholdSettingsScreen';
import { InsightsScreen } from './insights/InsightsScreen';
import { CookModeScreen } from './recipes/CookModeScreen';
import { RecipeDetailScreen } from './recipes/RecipeDetailScreen';
import { RecipeEditScreen } from './recipes/RecipeEditScreen';
import { RecipesScreen } from './recipes/RecipesScreen';
import { PantryScreen } from './pantry/PantryScreen';
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
  // The Pantry tab's search and filter live here, as its view model outlives the screen in the app.
  const [pantryQuery, setPantryQuery] = useState<PantryQuery>(DEFAULT_QUERY);
  const [groceryListId, setGroceryListId] = useState<string | null>(null);
  const [recipeQuery, setRecipeQuery] = useState<RecipeQuery>(DEFAULT_RECIPE_QUERY);
  // A recipe opens on the tab it was tapped in (RecipeDetailRoute(id, currentTab) in the app).
  const openRecipe = (id: string) => nav.navigate({ name: 'recipe', id });
  const openSettings = () => nav.navigate({ name: 'settings' });
  if (route.name === 'settings') {
    return <SettingsScreen onBack={nav.back} onOpenHousehold={() => nav.navigate({ name: 'householdSettings' })} />;
  }
  if (route.name === 'householdSettings') return <HouseholdSettingsScreen onBack={nav.back} />;
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
        navigation={{
          openSettings,
          // A Home card opens the Pantry tab at its root with its filter (DeepLink.Pantry in the app).
          openPantry: (filter) => {
            setPantryQuery((q) => ({ ...q, filter }));
            nav.showRoot('pantry');
          },
          openInsights: () => nav.navigate({ name: 'insights' }),
          openGroceries: () => nav.showRoot('groceries'),
          openRecipe,
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
