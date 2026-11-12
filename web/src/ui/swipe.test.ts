import { describe, expect, it } from 'vitest';
import { isSafariEdge, SAFARI_EDGE_PX } from './swipe';

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
