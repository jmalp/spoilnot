// The Home tab (DESIGN 5.6), ported from feature/home/domain: Recent activity and what the cards show.
import type { Observable } from 'rxjs';
import type { GroceryItem } from '../grocery/models';
import { NeedsAttention, PantryItems, type NeedsAttentionSet } from '../pantry/items';
import { PantryRows, type PantryRow } from '../pantry/rows';
import { isStaple, type DiscardReason, type PantryData } from '../pantry/models';
import { compareStrings, sortedWith, type LocalDate } from '../time';

/** households/{hid}/activity types (DESIGN 8.2). */
export const ACTIVITY_TYPES = [
  'stock_added',
  'grocery_added',
  /** Written by builds before R15 (after v1); still shown while such events are among the newest. */
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
  /**
   * [events] with each author's name from the member list ([names], uid to display name), so a line always names who
   * wrote it (the rules check `actorUid`, not the stored `actorName`). Someone who has left keeps the stored name.
   */
  named(events: ActivityEvent[], names: ReadonlyMap<string, string>): ActivityEvent[] {
    return events.map((event) => {
      const name = names.get(event.actorUid);
      return name === undefined || name === event.actorName ? event : { ...event, actorName: name };
    });
  },
};

export interface ActivityRepository {
  /** The newest [limit] events, newest first. Unknown types (from newer app versions) are skipped. */
  observeRecent(householdId: string, limit: number): Observable<ActivityEvent[]>;
}

/** What the Home tab shows (DESIGN 5.6). */
export type HomeSummary = {
  /** The one Needs attention set (after v1, R8): Home lists its usable part and folds the expired. */
  attention: NeedsAttentionSet;
  /** Products below Keep at least, out of stock first; shown in the Grocery list card (after v1, R1). */
  lowStock: PantryRow[];
  /** Items still to buy on the default list. */
  groceryCount: number;
  /** No product has Keep some in stock on yet, so the Grocery list card says how to start (after v1, UX-07). */
  noStaples: boolean;
  /** Nothing is in the pantry yet, so Needs attention says how to start instead of "all clear" (after v1, UX-07). */
  pantryEmpty: boolean;
  recent: ActivityEvent[];
};

export const HomeDashboard = {
  summary(pantry: PantryData, groceryItems: GroceryItem[], recent: ActivityEvent[], today: LocalDate): HomeSummary {
    const items = PantryItems.all(pantry.products, pantry.batches, today);
    const rows = PantryRows.rows(pantry.products, pantry.batches, today);
    return {
      attention: NeedsAttention.of(items),
      lowStock: sortedWith(
        rows.filter((r) => r.isLow || r.isOut),
        (a, b) => Number(b.isOut) - Number(a.isOut),
        (a, b) => compareStrings(a.name.toLowerCase(), b.name.toLowerCase()),
      ),
      groceryCount: groceryItems.filter((i) => !i.checked).length,
      noStaples: !pantry.products.some(isStaple),
      pantryEmpty: !items.some((i) => i.kind === 'purchase'),
      recent,
    };
  },
};
