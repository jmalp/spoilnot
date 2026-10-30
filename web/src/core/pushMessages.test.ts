import { describe, expect, it, vi } from 'vitest';
import { DeepLinks, OPEN_ROUTE_MESSAGE } from './deepLinks';
import { notificationFor, openRoute, safeRoute } from './pushMessages';

describe('push messages', () => {
  it("shows the digest from the job's message", () => {
    const { title, options } = notificationFor({
      notification: { title: '3 items need attention', body: 'Tomorrow: Milk.', tag: 'expiry_digest' },
      data: { type: 'expiry_digest', route: '/pantry?filter=expiring', date: '2026-10-30' },
      from: '1234',
    });
    expect(title).toBe('3 items need attention');
    expect(options).toEqual({
      body: 'Tomorrow: Milk.',
      tag: 'expiry_digest',
      icon: '/icons/icon-192.png',
      data: { route: '/pantry?filter=expiring' },
    });
  });

  it('a push without a notification still shows one (iOS requires it), opening the app', () => {
    expect(notificationFor(null)).toEqual({
      title: 'Spoil Not',
      options: { body: '', tag: 'expiry_digest', icon: '/icons/icon-192.png', data: { route: '/' } },
    });
  });

  it('routes stay on this site', () => {
    expect(safeRoute('/plan')).toBe('/plan');
    expect(safeRoute('https://example.com/')).toBe('/');
    expect(safeRoute('//example.com/')).toBe('/');
    expect(safeRoute('/\\example.com')).toBe('/');
    expect(safeRoute(42)).toBe('/');
  });

  it('a tap moves an open window to the route, or opens the app there', async () => {
    const window = { url: 'https://spoilnot-prod.web.app/home', postMessage: vi.fn(), focus: vi.fn(async () => true) };
    const other = { url: 'https://example.com/', postMessage: vi.fn(), focus: vi.fn(async () => true) };
    const clients = { matchAll: vi.fn(async () => [other, window]), openWindow: vi.fn(async () => null) };
    await openRoute(clients, '/plan', 'https://spoilnot-prod.web.app');
    expect(window.postMessage).toHaveBeenCalledWith({ type: OPEN_ROUTE_MESSAGE, route: '/plan' });
    expect(window.focus).toHaveBeenCalled();
    expect(other.postMessage).not.toHaveBeenCalled();
    expect(clients.openWindow).not.toHaveBeenCalled();

    const none = { matchAll: vi.fn(async () => []), openWindow: vi.fn(async () => null) };
    await openRoute(none, '/pantry?filter=expiring', 'https://spoilnot-prod.web.app');
    expect(none.openWindow).toHaveBeenCalledWith('/pantry?filter=expiring');
  });

  it("the digest's routes open the filtered Pantry or the Plan tab; nothing else is a link", () => {
    expect(DeepLinks.parse('/pantry?filter=expiring')).toEqual({ tab: 'pantry', filter: 'expiringSoon' });
    expect(DeepLinks.parse('/plan')).toEqual({ tab: 'plan' });
    expect(DeepLinks.parse('/pantry')).toBeNull();
    expect(DeepLinks.parse(null)).toBeNull();
  });
});
