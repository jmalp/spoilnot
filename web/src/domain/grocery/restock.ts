// The restock engine (DESIGN 9.6), ported from RestockEngine and RestockRunner.
import {
  combineLatest,
  debounceTime,
  distinctUntilChanged,
  EMPTY,
  map,
  retry,
  switchMap,
  timer,
  type Subscription,
} from 'rxjs';
import type { PantryChange, PantryData, Product } from '../pantry/models';
import type { PantryRepository } from '../pantry/repository';
import type { SessionManager } from '../session';
import type { Clock } from '../time';
import type { Actor, HouseholdWriter } from '../write';
import { GroceryRules, type GroceryChange, type GroceryItem, type GroceryRepository } from './models';

/**
 * A pure function from the current products and stock to the writes to make. It runs on every phone; the plan is
 * idempotent, so two phones evaluating at once write identical data.
 * - Below Keep at least while armed: add `low_{productId}` to the default list once and disarm.
 * - Back at Keep at least or more while disarmed: re-arm.
 * Deleting the item therefore does not bring it back until stock recovers and drops again.
 */
export const RestockEngine = {
  plan(
    data: PantryData,
    defaultListId: string | null,
    cachedLowStockIds: Set<string>,
  ): (GroceryChange | PantryChange)[] {
    const changes: (GroceryChange | PantryChange)[] = [];
    for (const product of data.products) {
      const min = product.minQuantity;
      if (min === null || !product.autoAddToGrocery) continue;
      const total = RestockEngine.totalOf(product, data);
      if (total >= min) {
        if (!product.restockArmed) changes.push({ type: 'pantry.setRestockArmed', productId: product.id, armed: true });
      } else if (product.restockArmed && defaultListId !== null) {
        changes.push({
          type: 'grocery.upsertLowStock',
          listId: defaultListId,
          productId: product.id,
          name: product.name,
          nameKey: product.nameKey,
          unit: product.unit,
          category: product.category,
          quantity: RestockEngine.buyQuantity(product, total),
          writeCreated: !cachedLowStockIds.has(GroceryRules.lowStockItemId(product.id)),
        });
        changes.push({ type: 'pantry.setRestockArmed', productId: product.id, armed: false });
      }
    }
    return changes;
  },

  /** Buy quantity, or Keep at least minus what is left, at least 1 (DESIGN 5.3). */
  buyQuantity: (product: Product, total: number): number =>
    product.buyQuantity ?? Math.max(1, (product.minQuantity ?? 0) - total),

  /** Total stock; batches at 0 or below do not count (DESIGN 8.5, invariant 1). */
  totalOf: (product: Product, data: PantryData): number =>
    data.batches.filter((b) => b.productId === product.id && b.quantity > 0).reduce((sum, b) => sum + b.quantity, 0),

  /** A low-stock item whose product is back at Keep at least shows "Back in stock" with a one-tap remove. */
  isBackInStock(item: GroceryItem, data: PantryData): boolean {
    if (item.source !== 'low_stock' || item.checked) return false;
    const product = data.products.find((p) => p.id === item.productId);
    if (!product || product.minQuantity === null) return false;
    return RestockEngine.totalOf(product, data) >= product.minQuantity;
  },
};

/** How long a plan the server rejected is not sent again, so a rollback does not loop. */
const REPEAT_GUARD_MS = 30_000;
const DEBOUNCE_MS = 300;
const RESUBSCRIBE_MS = 5_000;

/**
 * Runs the [RestockEngine] while the user is in a household: on every change of the products and stock (cache
 * snapshots included, so it works offline), debounced by 300 ms.
 */
export class RestockRunner {
  private subscription: Subscription | null = null;
  private lastPlan = '';
  private lastPlanAt = 0;

  constructor(
    private readonly session: SessionManager,
    private readonly pantry: PantryRepository,
    private readonly grocery: GroceryRepository,
    private readonly writer: HouseholdWriter,
    private readonly clock: Clock,
  ) {}

  start(): void {
    if (this.subscription) return;
    this.subscription = this.session.session$
      .pipe(
        map((s) =>
          s.kind === 'inHousehold'
            ? { hid: s.householdId, actor: { uid: s.user.uid, name: s.displayName } as Actor }
            : null,
        ),
        distinctUntilChanged((a, b) => a?.hid === b?.hid && a?.actor.uid === b?.actor.uid),
        switchMap((context) => {
          if (!context) return EMPTY;
          const defaultList = this.grocery.observeLists(context.hid).pipe(
            map((lists) => lists.find((l) => l.isDefault)?.id ?? null),
            distinctUntilChanged(),
          );
          return combineLatest([this.pantry.observe(context.hid), defaultList]).pipe(
            map(([data, listId]) => ({ ...context, data, listId })),
            // A refused or failed listener (such as right after creating a household, before the server has the
            // membership) listens again a little later instead of stopping the engine for the session.
            retry({ delay: () => timer(RESUBSCRIBE_MS) }),
          );
        }),
        debounceTime(DEBOUNCE_MS),
      )
      .subscribe({ next: (input) => void this.evaluate(input.hid, input.actor, input.data, input.listId) });
  }

  stop(): void {
    this.subscription?.unsubscribe();
    this.subscription = null;
  }

  private async evaluate(hid: string, actor: Actor, data: PantryData, listId: string | null): Promise<void> {
    try {
      const draft = RestockEngine.plan(data, listId, new Set());
      if (draft.length === 0) return;
      // createdAt and createdBy are written only for items not already in the local cache (DESIGN 9.6).
      const cached = new Set<string>();
      for (const change of draft) {
        if (change.type !== 'grocery.upsertLowStock' || listId === null) continue;
        const id = GroceryRules.lowStockItemId(change.productId);
        if (await this.grocery.isCached(hid, listId, id)) cached.add(id);
      }
      const plan = RestockEngine.plan(data, listId, cached);
      const key = JSON.stringify(plan);
      const now = this.clock.now();
      if (key === this.lastPlan && now - this.lastPlanAt < REPEAT_GUARD_MS) return;
      this.lastPlan = key;
      this.lastPlanAt = now;
      await this.writer.apply(hid, actor, plan);
    } catch {
      // The next snapshot triggers another evaluation.
    }
  }
}
