// The app's navigation (DESIGN 6.2), ported from TabNavigator: five tabs, each with its own back stack. Back pops
// within a tab, then returns to Home, then leaves the app. Pure functions over an immutable state, for React.

export const TABS = ['home', 'pantry', 'plan', 'recipes', 'groceries'] as const;
export type Tab = (typeof TABS)[number];
export const START: Tab = 'home';

/** A screen. Each tab's stack starts with its root; pushed screens are added as milestones need them. */
export type Route =
  | { name: 'root' }
  | { name: 'settings' }
  | { name: 'householdSettings' }
  | { name: 'about' }
  | { name: 'insights' }
  | { name: 'recipe'; id: string }
  | { name: 'recipeEdit'; id: string }
  | { name: 'cookMode'; id: string; servings: number | null };

export type NavState = { tab: Tab; stacks: Record<Tab, Route[]> };

/** Screens that close when their tab is left, like Settings and its screens in the app. */
export const closesOnLeave = (route: Route) =>
  route.name === 'settings' || route.name === 'householdSettings' || route.name === 'about';

export function initialNav(tab: Tab = START): NavState {
  const stacks = Object.fromEntries(TABS.map((t) => [t, [{ name: 'root' }]])) as Record<Tab, Route[]>;
  return { tab, stacks };
}

/** The current screen. */
export function top(state: NavState): Route {
  const stack = state.stacks[state.tab];
  return stack[stack.length - 1]!;
}

/** How many Back presses lead out of the app: the stack above the root, plus one for a tab other than Home. */
export function depth(state: NavState): number {
  return state.stacks[state.tab].length - 1 + (state.tab === START ? 0 : 1);
}

function withStack(state: NavState, tab: Tab, stack: Route[]): NavState {
  return { ...state, stacks: { ...state.stacks, [tab]: stack } };
}

function leave(state: NavState, tab: Tab): NavState {
  const stack = [...state.stacks[tab]];
  while (stack.length > 1 && closesOnLeave(stack[stack.length - 1]!)) stack.pop();
  return withStack(state, tab, stack);
}

const rootOnly = (state: NavState, tab: Tab) => withStack(state, tab, state.stacks[tab].slice(0, 1));

/** Selecting the current tab again pops it to its root; selecting another tab restores its stack. */
export function selectTab(state: NavState, tab: Tab): NavState {
  if (tab === state.tab) return rootOnly(state, tab);
  return { ...leave(state, state.tab), tab };
}

/** Shows [tab] at its root, for example when a notification opens the filtered Pantry list. */
export function showRoot(state: NavState, tab: Tab): NavState {
  const popped = rootOnly(state, tab);
  return tab === state.tab ? popped : { ...leave(popped, state.tab), tab };
}

export function navigate(state: NavState, route: Route): NavState {
  return withStack(state, state.tab, [...state.stacks[state.tab], route]);
}

/** Replaces the current screen. */
export function replace(state: NavState, route: Route): NavState {
  const stack = state.stacks[state.tab];
  return withStack(state, state.tab, [...(stack.length > 1 ? stack.slice(0, -1) : stack), route]);
}

/** Null when there is nothing left to pop, so the browser should handle Back. */
export function goBack(state: NavState): NavState | null {
  const stack = state.stacks[state.tab];
  if (stack.length > 1) return withStack(state, state.tab, stack.slice(0, -1));
  if (state.tab !== START) return { ...leave(state, state.tab), tab: START };
  return null;
}
