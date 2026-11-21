import { describe, expect, it } from 'vitest';
import { isSafariEdge, SAFARI_EDGE_PX, swipeDirection } from './swipe';

describe('isSafariEdge (WEB-15)', () => {
  it("leaves swipes from the left edge to Safari's Back in a Safari tab", () => {
    expect(isSafariEdge(0, false)).toBe(true);
    expect(isSafariEdge(SAFARI_EDGE_PX - 1, false)).toBe(true);
    expect(isSafariEdge(SAFARI_EDGE_PX, false)).toBe(false);
    expect(isSafariEdge(200, false)).toBe(false);
  });

  it('keeps every swipe for the row in the Home Screen app', () => {
    expect(isSafariEdge(0, true)).toBe(false);
    expect(isSafariEdge(10, true)).toBe(false);
  });
});

describe('swipeDirection', () => {
  it('acts past 40% of the row, either way', () => {
    expect(swipeDirection(161, 400)).toBe('right');
    expect(swipeDirection(-161, 400)).toBe('left');
  });

  it('springs back at 40% or less', () => {
    expect(swipeDirection(160, 400)).toBeNull();
    expect(swipeDirection(-160, 400)).toBeNull();
    expect(swipeDirection(0, 400)).toBeNull();
  });
});
