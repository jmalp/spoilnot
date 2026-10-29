// The Home tab (DESIGN 5.6), ported from feature/home/domain: Recent activity and what the cards show.
import type { Observable } from 'rxjs';
import { Expiry } from '../pantry/rules';
import { PantryRows, type PantryRow } from '../pantry/rows';
import type { DiscardReason, PantryData } from '../pantry/models';
import { compareStrings, nullsLast, sortedWith, type LocalDate } from '../time';

/** households/{hid}/activity types (DESIGN 8.2). */
export const ACTIVITY_TYPES = [
  'stock_added',
  'grocery_added',
  'grocery_checked',
  'recipe_saved',
  'member_joined',
  'discarded',
  /** After v1: someone used "I cooked this" on a recipe. */
  'recipe_cooked',
  /** After v1: the owner cleared the household's data (DESIGN 5.1); older events are not shown. */
  'household_reset',
] as const;
export type ActivityType = (typeof ACTIVITY_TYPES)[number];

export const activityTypeOf = (value: unknown): ActivityType | null =>
  (ACTIVITY_TYPES as readonly unknown[]).includes(value) ? (value as ActivityType) : null;

/** One line of Recent activity, such as "Sam added Eggs to Groceries" (DESIGN 5.6). */
export type ActivityEvent = {
  id: string;
  type: ActivityType;
  actorUid: string;
  actorName: string;
  name: string | null;
  quantity: number | null;
  reason: DiscardReason | null;
  listName: string | null;
  /** Items added at once, from the recipe called [name]. */
  count: number | null;
  /** Epoch milliseconds; estimated while the server timestamp is pending. */
  createdAt: number | null;
};

/** Recent activity rules (after v1). */
export const ActivityFeed = {
  /**
   * [events] (newest first) up to and including the newest household_reset: events from before a reset can still sit
   * in a phone's cache (changes-only reads, DESIGN 9.8) but describe data that is gone.
   */
  sinceReset(events: ActivityEvent[]): ActivityEvent[] {
    const reset = events.findIndex((e) => e.type === 'household_reset');
    return reset < 0 ? events : events.slice(0, reset + 1);
  },
};

export interface ActivityRepository {
  /** The newest [limit] events, newest first. Unknown types (from newer app versions) are skipped. */
  observeRecent(householdId: string, limit: number): Observable<ActivityEvent[]>;
}

/** What the Home tab shows (DESIGN 5.6). The grocery count joins with the grocery list (W3). */
export type HomeSummary = {
  /** Expired and expiring within 3 days, soonest first. */
  attention: PantryRow[];
  expiredCount: number;
  /** Staples below Keep at least, out of stock first. */
  lowStock: PantryRow[];
  recent: ActivityEvent[];
};

export const HomeDashboard = {
  summary(pantry: PantryData, recent: ActivityEvent[], today: LocalDate): HomeSummary {
    const rows = PantryRows.rows(pantry.products, pantry.batches, today);
    const attention = sortedWith(
      rows.filter((r) => r.batches.some((b) => Expiry.needsAttention(Expiry.of(b.expiryDate, today)))),
      (a, b) => nullsLast(compareStrings)(a.soonest?.expiryDate, b.soonest?.expiryDate),
    );
    return {
      attention,
      expiredCount: attention.filter((r) => r.status === 'expired').length,
      lowStock: sortedWith(
        rows.filter((r) => r.isLow || r.isOut),
        (a, b) => Number(b.isOut) - Number(a.isOut),
        (a, b) => compareStrings(a.name.toLowerCase(), b.name.toLowerCase()),
      ),
      recent,
    };
  },
};
