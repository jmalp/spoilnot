import { act, render, screen } from '@testing-library/react';
import { BehaviorSubject, defer, finalize, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RELISTEN_MS, useObservable } from './hooks';

let attempts = 0;
// Each subscription starts its own listener, as the repositories' observables do.
const source = defer(() => (attempts++ === 0 ? throwError(() => new Error('permission denied')) : of('loaded')));

function Screen() {
  return <p>{useObservable(source, 'loading')}</p>;
}

describe('useObservable', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    attempts = 0;
  });
  afterEach(() => vi.useRealTimers());

  it('keeps the last value after an error and listens again a little later', async () => {
    render(<Screen />);
    expect(screen.getByText('loading')).toBeTruthy();
    await act(() => vi.advanceTimersByTimeAsync(RELISTEN_MS));
    expect(screen.getByText('loaded')).toBeTruthy();
  });

  it('lets go of the source while the page is hidden and listens again when it comes back (DESIGN 9.1)', async () => {
    let state: DocumentVisibilityState = 'visible';
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state });
    const setVisibility = (next: DocumentVisibilityState) => {
      state = next;
      document.dispatchEvent(new Event('visibilitychange'));
    };
    let listening = 0;
    const value = new BehaviorSubject('first');
    const listener = defer(() => {
      listening++;
      return value.pipe(finalize(() => listening--));
    });
    function Live() {
      return <p>{useObservable(listener, 'loading')}</p>;
    }
    try {
      render(<Live />);
      expect(screen.getByText('first')).toBeTruthy();
      expect(listening).toBe(1);
      act(() => setVisibility('hidden'));
      expect(listening).toBe(0);
      // The screen keeps what it showed.
      expect(screen.getByText('first')).toBeTruthy();
      value.next('changed while away');
      act(() => setVisibility('visible'));
      expect(listening).toBe(1);
      expect(screen.getByText('changed while away')).toBeTruthy();
    } finally {
      state = 'visible';
      Reflect.deleteProperty(document, 'visibilityState');
    }
  });
});
