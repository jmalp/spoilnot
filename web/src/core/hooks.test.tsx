import { act, render, screen } from '@testing-library/react';
import { defer, of, throwError } from 'rxjs';
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
});
