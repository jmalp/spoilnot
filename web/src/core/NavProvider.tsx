import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import * as nav from './navigation';
import type { NavState, Route, Tab } from './navigation';

type Navigator = {
  state: NavState;
  selectTab: (tab: Tab) => void;
  showRoot: (tab: Tab) => void;
  navigate: (route: Route) => void;
  replace: (route: Route) => void;
  back: () => void;
};

const Context = createContext<Navigator | null>(null);

export function useNav(): Navigator {
  const value = useContext(Context);
  if (!value) throw new Error('useNav outside NavProvider');
  return value;
}

/** The tab a URL path names (`/pantry`), for links from notifications; Home otherwise. */
export function tabFromPath(path: string): Tab {
  const name = path.split('/')[1] ?? '';
  return (nav.TABS as readonly string[]).includes(name) ? (name as Tab) : nav.START;
}

/**
 * Keeps the navigation state and the browser history in step: one history entry per Back press the app would
 * handle (navigation.depth), so the browser's and Android's Back buttons and the iPhone's edge swipe pop screens,
 * and the URL names the current tab.
 */
export function NavProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState(() => nav.initialNav(tabFromPath(location.pathname)));
  const historyDepth = useRef(0);
  const ignorePops = useRef(0);
  const currentTab = useRef(state.tab);

  useEffect(() => {
    const path = `/${state.tab}${location.search}`;
    const target = nav.depth(state);
    if (target > historyDepth.current) {
      for (let d = historyDepth.current + 1; d <= target; d++) history.pushState({ depth: d }, '', path);
    } else if (target < historyDepth.current) {
      ignorePops.current++;
      history.go(target - historyDepth.current);
    } else {
      history.replaceState({ depth: target }, '', path);
    }
    historyDepth.current = target;
    currentTab.current = state.tab;
  }, [state]);

  useEffect(() => {
    const onPop = () => {
      if (ignorePops.current > 0) {
        ignorePops.current--;
        history.replaceState({ depth: historyDepth.current }, '', `/${currentTab.current}`);
        return;
      }
      historyDepth.current = Math.max(0, historyDepth.current - 1);
      setState((current) => nav.goBack(current) ?? current);
    };
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, []);

  const back = useCallback(() => setState((current) => nav.goBack(current) ?? current), []);
  const value = useMemo<Navigator>(
    () => ({
      state,
      selectTab: (tab) => setState((s) => nav.selectTab(s, tab)),
      showRoot: (tab) => setState((s) => nav.showRoot(s, tab)),
      navigate: (route) => setState((s) => nav.navigate(s, route)),
      replace: (route) => setState((s) => nav.replace(s, route)),
      back,
    }),
    [state, back],
  );
  return <Context.Provider value={value}>{children}</Context.Provider>;
}
