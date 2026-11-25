// The Edit window while someone else changes or removes its item (after v1, DESIGN 9.2 and 9.4), ported from
// ItemEditWatch in the app.
import { NameKey } from './rules';
import type { PantryData, Product, StockBatch } from './models';
import type { ItemEdit } from './service';

/**
 * The purchase and product as the Edit window opened with them. Saving compares the form with this and writes only
 * what the person changed; while the window is open, the live copy is compared with it.
 */
export type ItemEditBase = { batch: StockBatch; product: Product | null };

/** What the Edit window shows for a purchase and its product, as the form would hand it back unchanged. */
export function itemEditOf({ batch, product }: ItemEditBase): ItemEdit {
  const autoAdd = product?.autoAddToGrocery === true;
  return {
    name: product?.name ?? batch.productName,
    quantity: batch.quantity,
    unit: product?.unit ?? batch.unit,
    category: product?.category ?? batch.category,
    expiryDate: batch.expiryDate,
    location: batch.location,
    autoAddToGrocery: autoAdd,
    minQuantity: autoAdd ? (product?.minQuantity ?? null) : null,
    buyQuantity: autoAdd ? (product?.buyQuantity ?? null) : null,
  };
}

/**
 * Unchanged: nothing the window shows changed, or only this person changed it. Changed: someone else did ("Sam
 * changed this item. Review"); `live` is how it is now. Removed: deleted, or used up to 0 (`byUid` is known then).
 */
export type EditWatch =
  | { kind: 'unchanged' }
  | { kind: 'changed'; byUid: string | null; live: ItemEditBase }
  | { kind: 'removed'; byUid: string | null };

const sameEdit = (a: ItemEdit, b: ItemEdit) => JSON.stringify(a) === JSON.stringify(b);

/** Compares the live `data` with `base`. Changes by `myUid` don't count as someone else's. */
export function checkEdit(base: ItemEditBase, data: PantryData, myUid: string): EditWatch {
  const batch = data.batches.find((b) => b.id === base.batch.id);
  if (!batch) return { kind: 'removed', byUid: null };
  // Batches at 0 or below are hidden and about to be removed (DESIGN 8.5 invariant 1).
  if (batch.quantity <= 0) return { kind: 'removed', byUid: batch.updatedBy ?? null };
  const product = data.products.find((p) => p.id === batch.productId) ?? null;
  const live = { batch, product };
  const now = itemEditOf(live);
  const then = itemEditOf(base);
  if (sameEdit(now, then)) return { kind: 'unchanged' };
  const batchChanged =
    now.quantity !== then.quantity || now.expiryDate !== then.expiryDate || now.location !== then.location;
  const by = (batchChanged ? batch.updatedBy : (product?.updatedBy ?? batch.updatedBy)) ?? null;
  return by !== null && by === myUid ? { kind: 'unchanged' } : { kind: 'changed', byUid: by, live };
}

/**
 * Review: the form shows the item as it is now, with this person's own changes kept. A field they didn't touch takes
 * the `live` value; a changed quantity keeps their difference. Keep at least, Buy quantity and the switch go together.
 */
export function rebaseEdit(draft: ItemEdit, base: ItemEdit, live: ItemEdit): ItemEdit {
  const keepTouched =
    draft.autoAddToGrocery !== base.autoAddToGrocery ||
    draft.minQuantity !== base.minQuantity ||
    draft.buyQuantity !== base.buyQuantity;
  return {
    name: NameKey.clean(draft.name) === base.name ? live.name : draft.name,
    quantity: Math.max(0, live.quantity + draft.quantity - base.quantity),
    unit: draft.unit === base.unit ? live.unit : draft.unit,
    category: draft.category === base.category ? live.category : draft.category,
    expiryDate: draft.expiryDate === base.expiryDate ? live.expiryDate : draft.expiryDate,
    location: draft.location === base.location ? live.location : draft.location,
    autoAddToGrocery: keepTouched ? draft.autoAddToGrocery : live.autoAddToGrocery,
    minQuantity: keepTouched ? draft.minQuantity : live.minQuantity,
    buyQuantity: keepTouched ? draft.buyQuantity : live.buyQuantity,
  };
}
