// Ported from TabNavigatorTest.
import { describe, expect, it } from 'vitest';
import { depth, goBack, initialNav, navigate, selectTab, showRoot, top, type NavState } from './navigation';

const recipe = (id: string) => ({ name: 'recipe', id }) as const;
const settings = { name: 'settings' } as const;
const householdSettings = { name: 'householdSettings' } as const;

describe('navigation', () => {
  it('starts on Home showing only its root', () => {
    const s = initialNav();
    expect(s.tab).toBe('home');
    expect(top(s)).toEqual({ name: 'root' });
    expect(depth(s)).toBe(0);
  });

  it('another tab is shown on top of Home', () => {
    const s = selectTab(initialNav(), 'pantry');
    expect(s.tab).toBe('pantry');
    expect(depth(s)).toBe(1);
  });

  it('each tab keeps its own back stack across tab switches', () => {
    let s = navigate(selectTab(initialNav(), 'pantry'), recipe('r1'));
    s = selectTab(s, 'groceries');
    expect(top(s)).toEqual({ name: 'root' });
    s = selectTab(s, 'pantry');
    expect(top(s)).toEqual(recipe('r1'));
  });

  it('reselecting the current tab pops it to its root', () => {
    let s = navigate(navigate(selectTab(initialNav(), 'pantry'), recipe('r1')), recipe('r2'));
    s = selectTab(s, 'pantry');
    expect(s.stacks.pantry).toEqual([{ name: 'root' }]);
  });

  it('back pops within a tab, then returns to Home, then leaves the app', () => {
    let s: NavState | null = navigate(selectTab(initialNav(), 'pantry'), recipe('r1'));
    s = goBack(s)!;
    expect([s.tab, top(s)]).toEqual(['pantry', { name: 'root' }]);
    s = goBack(s)!;
    expect(s.tab).toBe('home');
    expect(goBack(s)).toBeNull();
  });

  it('showRoot switches to a tab and pops it to its root', () => {
    let s = navigate(selectTab(initialNav(), 'pantry'), recipe('r1'));
    s = selectTab(s, 'home');
    s = showRoot(s, 'pantry');
    expect(s.tab).toBe('pantry');
    expect(s.stacks.pantry).toEqual([{ name: 'root' }]);
  });

  it('leaving a tab closes Settings on it but keeps other screens', () => {
    let s = selectTab(initialNav(), 'pantry');
    s = navigate(navigate(navigate(s, recipe('r1')), settings), householdSettings);
    s = navigate(selectTab(s, 'groceries'), settings);
    s = selectTab(s, 'pantry');
    expect(s.stacks.pantry).toEqual([{ name: 'root' }, recipe('r1')]);
    expect(s.stacks.groceries).toEqual([{ name: 'root' }]);
    // Leaving through Back to Home closes it as well.
    s = navigate(s, settings);
    s = goBack(goBack(goBack(s)!)!)!;
    expect(s.tab).toBe('home');
    s = showRoot(navigate(s, settings), 'pantry');
    expect(s.stacks.home).toEqual([{ name: 'root' }]);
  });
});
