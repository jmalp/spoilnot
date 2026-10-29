// One row per product (PantryList.rows in the app): what Home's cards list. The Pantry tab itself shows one row per
// purchase (items.ts).
import { compareStrings, nullsLast, type LocalDate } from '../time';
import { isStaple, type Category, type Location, type Product, type QuantityUnit, type StockBatch } from './models';
import { Expiry, type ExpiryStatus } from './rules';

export type PantryRow = {
  productId: string;
  name: string;
  unit: QuantityUnit;
  category: Category;
  product: Product | null;
  /** Soonest expiry first, undated last. */
  batches: StockBatch[];
  total: number;
  /** Status of the soonest-expiring batch, or noDate for an empty staple. */
  status: ExpiryStatus;
  soonest: StockBatch | null;
  location: Location;
  /** A staple (Keep at least set) with nothing left: shown with an Out badge. */
  isOut: boolean;
  /** A staple below its Keep at least. */
  isLow: boolean;
  pendingSync: boolean;
};

const byExpiry = nullsLast(compareStrings);

export const PantryRows = {
  /** Products with stock, plus staples at zero; empty batches are left out. */
  rows(products: Product[], batches: StockBatch[], today: LocalDate): PantryRow[] {
    const byProduct = new Map<string, StockBatch[]>();
    for (const b of batches) {
      if (b.quantity > 0) byProduct.set(b.productId, [...(byProduct.get(b.productId) ?? []), b]);
    }
    const byId = new Map(products.map((p) => [p.id, p]));
    const ids = [...new Set([...byProduct.keys(), ...products.filter(isStaple).map((p) => p.id)])];
    return ids.map((id) => row(byId.get(id) ?? null, id, byProduct.get(id) ?? [], today));
  },
};

function row(product: Product | null, productId: string, batches: StockBatch[], today: LocalDate): PantryRow {
  const sorted = [...batches].sort((a, b) => byExpiry(a.expiryDate, b.expiryDate));
  const soonest = sorted[0] ?? null;
  const total = batches.reduce((sum, b) => sum + b.quantity, 0);
  const first = batches[0];
  return {
    productId,
    name: product?.name ?? first?.productName ?? '',
    unit: product?.unit ?? first?.unit ?? 'pcs',
    category: product?.category ?? first?.category ?? 'other',
    product,
    batches: sorted,
    total,
    status: Expiry.of(soonest?.expiryDate ?? null, today),
    soonest,
    location: soonest?.location ?? product?.defaultLocation ?? 'other',
    isOut: product !== null && isStaple(product) && total <= 0,
    isLow: product?.minQuantity != null && total < product.minQuantity,
    pendingSync: batches.some((b) => b.pendingSync),
  };
}
