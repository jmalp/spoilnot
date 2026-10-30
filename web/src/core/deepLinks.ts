// Where a notification tap lands (DESIGN 10.2), ported from DeepLink.parse: the digest's routes and nothing else.
import type { PantryFilter } from '../domain/pantry/items';

export type DeepLink = { tab: 'pantry'; filter: PantryFilter } | { tab: 'plan' };

export const DeepLinks = {
  /** A digest about food: the Pantry tab filtered to Expiring soon. */
  PANTRY_EXPIRING: '/pantry?filter=expiring',
  /** A digest with only today's meals: the Plan tab. */
  PLAN: '/plan',

  parse(route: string | null | undefined): DeepLink | null {
    if (route === DeepLinks.PANTRY_EXPIRING) return { tab: 'pantry', filter: 'expiringSoon' };
    if (route === DeepLinks.PLAN) return { tab: 'plan' };
    return null;
  },
};

/** The message the service worker sends an open window when a notification is tapped. */
export const OPEN_ROUTE_MESSAGE = 'spoilnot.openRoute';
