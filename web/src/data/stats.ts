// Insights counts (feature/insights/data in the app): one document per month, written with increments.
import {
  collection,
  doc,
  documentId,
  increment,
  query,
  where,
  type DocumentSnapshot,
  type Firestore,
  type WriteBatch,
} from 'firebase/firestore';
import { distinctUntilChanged, map, switchMap, type Observable } from 'rxjs';
import { monthStats, type MonthStats, type StatsRepository } from '../domain/insights/insights';
import type { StatsChange } from '../domain/insights/stats';
import { DISCARD_REASONS, type DiscardReason } from '../domain/pantry/models';
import type { YearMonth } from '../domain/time';
import type { Actor, HouseholdChange } from '../domain/write';
import { Stamps } from './delta-sync';
import type { FirebaseSdk } from './firebase';
import { querySnapshots, retryAfterOwnWrites } from './firestore-rx';
import type { ChangeMapper } from './writer';

export const statsPath = (hid: string) => `households/${hid}/stats`;

/** Counters are never negative on screen, even if a race left one below zero. */
const count = (value: unknown): number => (typeof value === 'number' ? Math.max(0, Math.trunc(value)) : 0);

function toMonthStats(s: DocumentSnapshot): MonthStats | null {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(s.id)) return null;
  const byDay: Record<number, number> = {};
  for (const [day, n] of Object.entries((s.get('thrownByDay') as Record<string, unknown> | null) ?? {})) {
    const d = Number(day);
    if (Number.isInteger(d) && d >= 1 && d <= 31 && count(n) > 0) byDay[d] = count(n);
  }
  const thrown: Partial<Record<DiscardReason, number>> = {};
  for (const reason of DISCARD_REASONS) if (count(s.get(reason)) > 0) thrown[reason] = count(s.get(reason));
  return monthStats(s.id, count(s.get('used')), thrown, byDay);
}

export class FirestoreStatsRepository implements StatsRepository {
  constructor(private readonly sdk: FirebaseSdk) {}

  // Month ids sort as text, so a document-id range reads only the months asked for.
  observeMonths(householdId: string, from: YearMonth): Observable<MonthStats[]> {
    return this.sdk.instance$.pipe(
      switchMap((db) =>
        querySnapshots(query(collection(db, statsPath(householdId)), where(documentId(), '>=', from))).pipe(
          retryAfterOwnWrites(db),
        ),
      ),
      map((snapshot) =>
        snapshot.docs
          .map(toMonthStats)
          .filter((m): m is MonthStats => m !== null)
          .sort((a, b) => (a.month < b.month ? -1 : 1)),
      ),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
    );
  }
}

const isStatsChange = (change: HouseholdChange): change is StatsChange => change.type.startsWith('stats.');

export const statsMapper: ChangeMapper = {
  write(batch: WriteBatch, db: Firestore, hid: string, actor: Actor, change: HouseholdChange): boolean {
    if (!isStatsChange(change)) return false;
    const fields =
      change.type === 'stats.countUsedUp'
        ? { used: increment(change.delta) }
        : { [change.reason]: increment(change.delta), thrownByDay: { [String(change.day)]: increment(change.delta) } };
    batch.set(doc(db, statsPath(hid), change.month), { ...fields, ...Stamps.updated(actor) }, { merge: true });
    return true;
  },
};
