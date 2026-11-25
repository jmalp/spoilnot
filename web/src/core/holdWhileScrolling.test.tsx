import { act, cleanup, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SCROLL_IDLE_MS, useHeldWhileScrolling } from './holdWhileScrolling';

function List({ value }: { value: number }) {
  const [scroller, setScroller] = useState<HTMLDivElement | null>(null);
  const shown = useHeldWhileScrolling(value, scroller);
  return (
    <div ref={setScroller} data-testid="list">
      shown {shown}
    </div>
  );
}

describe('useHeldWhileScrolling', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('shows a change at once while the list is still', () => {
    const { rerender } = render(<List value={1} />);
    rerender(<List value={2} />);
    expect(screen.getByTestId('list').textContent).toBe('shown 2');
  });

  it('holds a change while the list scrolls and shows it once the scroll stops', () => {
    const { rerender } = render(<List value={1} />);
    const list = screen.getByTestId('list');
    act(() => {
      list.dispatchEvent(new Event('scroll'));
    });
    rerender(<List value={2} />);
    expect(list.textContent).toBe('shown 1');
    // Still scrolling: each scroll event pushes the end back.
    act(() => {
      vi.advanceTimersByTime(SCROLL_IDLE_MS - 10);
      list.dispatchEvent(new Event('scroll'));
      vi.advanceTimersByTime(SCROLL_IDLE_MS - 10);
    });
    expect(list.textContent).toBe('shown 1');
    act(() => {
      vi.advanceTimersByTime(20);
    });
    expect(list.textContent).toBe('shown 2');
  });

  it('shows the change at scrollend, where the browser sends one', () => {
    const { rerender } = render(<List value={1} />);
    const list = screen.getByTestId('list');
    act(() => {
      list.dispatchEvent(new Event('scroll'));
    });
    rerender(<List value={3} />);
    expect(list.textContent).toBe('shown 1');
    act(() => {
      list.dispatchEvent(new Event('scrollend'));
    });
    expect(list.textContent).toBe('shown 3');
  });
});
