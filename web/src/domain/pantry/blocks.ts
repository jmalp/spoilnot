// The pantry list as the screen draws it (DESIGN 19.5, 19.12); PantryBlocks in the app.
import type { Category, Location } from './models';
import {
  ExpiredFold,
  NeedsAttention,
  type PantryItem,
  type PantryItemGroup,
  type PantryQuery,
  type PurchaseItem,
} from './items';

/** The heading over a block of the list. */
export type PantryHeading =
  | { kind: 'useFirst' }
  | { kind: 'everythingElse' }
  | { kind: 'location'; location: Location }
  | { kind: 'category'; category: Category };

/** One row of a block: the line that folds the expired backlog, or an item. [key] is unique within its block. */
export type PantryLine =
  { kind: 'folded'; key: 'expired-fold'; count: number } | { kind: 'item'; key: string; item: PantryItem };

/** Rows drawn as one rounded group, under [heading] when it has one. [key] is unique in the list. */
export type PantryBlock = { key: string; heading: PantryHeading | null; lines: PantryLine[] };

/**
 * The list as it opens: soonest expiry first, not grouped, no search and no filter. Only this view splits into Use
 * first and Everything else.
 */
export const isDefaultView = (query: PantryQuery): boolean =>
  query.filter === 'all' && query.sort === 'soonestExpiry' && query.grouping === 'none' && query.search.trim() === '';

const itemLine = (item: PantryItem): PantryLine => ({ kind: 'item', key: item.key, item });

function foldLines(expired: PurchaseItem[], showExpired: boolean): PantryLine[] {
  return [
    { kind: 'folded', key: 'expired-fold', count: expired.length },
    ...(showExpired ? expired.map(itemLine) : []),
  ];
}

function block(group: PantryItemGroup): PantryBlock {
  const heading: PantryHeading | null = group.location
    ? { kind: 'location', location: group.location }
    : group.category
      ? { kind: 'category', category: group.category }
      : null;
  const key = group.location ? `location-${group.location}` : group.category ? `category-${group.category}` : 'all';
  return { key, heading, lines: group.items.map(itemLine) };
}

/**
 * Builds the blocks from PantryItems.build's groups. In the default view the list splits into Use first (every
 * purchase that has expired or expires within NeedsAttention.DAYS days) and Everything else; when nothing needs
 * attention it is one block without a heading. A backlog of ExpiredFold.MIN or more expired purchases folds into one
 * line, the first row of Use first, and its rows follow that line while [showExpired]. Any other view keeps its own
 * order and grouping, with the folded line, when there is one, as a block of its own at the top.
 */
export function pantryBlocks(groups: PantryItemGroup[], query: PantryQuery, showExpired: boolean): PantryBlock[] {
  const fold = ExpiredFold.of(groups, query);
  if (!isDefaultView(query)) {
    return [
      ...(fold ? [{ key: 'expired', heading: null, lines: foldLines(fold.expired, showExpired) }] : []),
      ...(fold?.rest ?? groups).map(block),
    ];
  }
  const items = groups.flatMap((g) => g.items);
  if (items.length === 0) return [];
  const first = items.filter(NeedsAttention.includesItem);
  const rest = items.filter((i) => !NeedsAttention.includesItem(i));
  if (first.length === 0) return [{ key: 'all', heading: null, lines: items.map(itemLine) }];
  const folded = new Set(fold?.expired.map((i) => i.key));
  const firstLines = fold
    ? [...foldLines(fold.expired, showExpired), ...first.filter((i) => !folded.has(i.key)).map(itemLine)]
    : first.map(itemLine);
  return [
    { key: 'use-first', heading: { kind: 'useFirst' }, lines: firstLines },
    ...(rest.length > 0
      ? [{ key: 'everything-else', heading: { kind: 'everythingElse' } as const, lines: rest.map(itemLine) }]
      : []),
  ];
}
