// The pantry list (DESIGN 5.2), ported from PantryItems: one row per purchase; a staple with nothing left keeps an
// Out row. Pure, so it is tested with a fixed today.
import { compareStrings, nullsLast, sortedWith, type LocalDate } from '../time';
import { CATEGORIES, isStaple, LOCATIONS, type Category, type Location, type Product, type StockBatch } from './models';
import { Expiry, NameKey, naturalOrder, type ExpiryStatus } from './rules';

export type PantryFilter = 'all' | 'fridge' | 'freezer' | 'pantry' | 'other' | 'expiringSoon' | 'expired' | 'lowStock';
export type PantrySort = 'soonestExpiry' | 'name' | 'recentlyAdded';
export type PantryGrouping = 'none' | 'location' | 'category';
export type PantryQuery = { filter: PantryFilter; sort: PantrySort; grouping: PantryGrouping; search: string };
export const DEFAULT_QUERY: PantryQuery = { filter: 'all', sort: 'soonestExpiry', grouping: 'none', search: '' };

type Common = {
  /** Stable for the list: the batch id, or out-<product id>. */
  key: string;
  productId: string;
  name: string;
  product: Product | null;
  category: Category;
  location: Location;
  /** The product's whole stock is below Keep at least. */
  isLow: boolean;
};

export type PurchaseItem = Common & { kind: 'purchase'; batch: StockBatch; status: ExpiryStatus };
/** A staple with no stock: shown with an Out badge; tapping it adds stock. */
export type OutItem = Common & { kind: 'out'; product: Product };
export type PantryItem = PurchaseItem | OutItem;

/** A section of the list; [location] or [category] is set when grouping. */
export type PantryItemGroup = { location: Location | null; category: Category | null; items: PantryItem[] };

export const PantryItems = {
  all(products: Product[], batches: StockBatch[], today: LocalDate): PantryItem[] {
    const live = batches.filter((b) => b.quantity > 0);
    const totals = new Map<string, number>();
    for (const b of live) totals.set(b.productId, (totals.get(b.productId) ?? 0) + b.quantity);
    const byId = new Map(products.map((p) => [p.id, p]));
    const low = (product: Product | null, total: number) => product?.minQuantity != null && total < product.minQuantity;
    const purchases = live.map((batch): PantryItem => {
      const product = byId.get(batch.productId) ?? null;
      return {
        kind: 'purchase',
        key: batch.id,
        productId: batch.productId,
        name: product?.name ?? batch.productName,
        product,
        category: product?.category ?? batch.category,
        location: batch.location,
        isLow: low(product, totals.get(batch.productId) ?? 0),
        batch,
        status: Expiry.of(batch.expiryDate, today),
      };
    });
    const out = products
      .filter((p) => isStaple(p) && (totals.get(p.id) ?? 0) <= 0)
      .map((product): PantryItem => ({
        kind: 'out',
        key: `out-${product.id}`,
        productId: product.id,
        name: product.name,
        product,
        category: product.category,
        location: product.defaultLocation,
        isLow: true,
      }));
    return [...purchases, ...out];
  },

  build(products: Product[], batches: StockBatch[], query: PantryQuery, today: LocalDate): PantryItemGroup[] {
    const search = NameKey.of(query.search);
    const items = this.all(products, batches, today)
      .filter((it) => search === '' || NameKey.of(it.name).includes(search))
      .filter((it) => matches(it, query.filter));
    const sorted = sort(items, query.sort);
    switch (query.grouping) {
      case 'none':
        return sorted.length === 0 ? [] : [{ location: null, category: null, items: sorted }];
      case 'location':
        return LOCATIONS.map((location) => ({
          location,
          category: null,
          items: sorted.filter((i) => i.location === location),
        })).filter((g) => g.items.length > 0);
      case 'category':
        return CATEGORIES.map((category) => ({
          location: null,
          category,
          items: sorted.filter((i) => i.category === category),
        })).filter((g) => g.items.length > 0);
    }
  },
};

function matches(item: PantryItem, filter: PantryFilter): boolean {
  switch (filter) {
    case 'all':
      return true;
    case 'fridge':
    case 'freezer':
    case 'pantry':
    case 'other':
      return item.location === filter;
    case 'expiringSoon':
      return item.kind === 'purchase' && Expiry.needsAttention(item.status) && item.status !== 'expired';
    case 'expired':
      return item.kind === 'purchase' && item.status === 'expired';
    case 'lowStock':
      return item.isLow;
  }
}

const byName = (a: PantryItem, b: PantryItem) => naturalOrder(a.name, b.name);
const expiryOf = (i: PantryItem) => (i.kind === 'purchase' ? i.batch.expiryDate : null);
const byExpiry = (a: PantryItem, b: PantryItem) => nullsLast(compareStrings)(expiryOf(a), expiryOf(b));
/** Out rows have nothing to use up, so they go last. */
const outLast = (a: PantryItem, b: PantryItem) => Number(a.kind === 'out') - Number(b.kind === 'out');
/** A purchase still waiting for its server timestamp is the newest of all. */
const added = (i: PantryItem) => (i.kind === 'purchase' ? (i.batch.createdAt ?? Infinity) : -Infinity);

function sort(items: PantryItem[], order: PantryQuery['sort']): PantryItem[] {
  switch (order) {
    case 'soonestExpiry':
      return sortedWith(items, outLast, byExpiry, byName);
    case 'name':
      return sortedWith(items, byName, byExpiry);
    case 'recentlyAdded':
      return sortedWith(items, outLast, (a, b) => (added(b) > added(a) ? 1 : added(b) < added(a) ? -1 : 0), byName);
  }
}

/**
 * Expired purchases gathered into one header at the top of the list, so a backlog of old food does not push
 * everything else off the screen: only when browsing (no search, any filter but Expired) and with at least [MIN].
 */
export const ExpiredFold = {
  MIN: 3,
  of(groups: PantryItemGroup[], query: PantryQuery): { expired: PurchaseItem[]; rest: PantryItemGroup[] } | null {
    if (query.search.trim() !== '' || query.filter === 'expired') return null;
    const expired = groups
      .flatMap((g) => g.items)
      .filter((i): i is PurchaseItem => i.kind === 'purchase' && i.status === 'expired');
    if (expired.length < this.MIN) return null;
    const ids = new Set(expired.map((i) => i.key));
    const rest = groups
      .map((g) => ({ ...g, items: g.items.filter((i) => !ids.has(i.key)) }))
      .filter((g) => g.items.length > 0);
    return { expired, rest };
  },
};
