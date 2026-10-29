// Insights counts (feature/insights/data in the app): one document per month, written with increments.
import { doc, increment, type Firestore, type WriteBatch } from 'firebase/firestore';
import type { StatsChange } from '../domain/insights/stats';
import type { Actor, HouseholdChange } from '../domain/write';
import { Stamps } from './delta-sync';
import type { ChangeMapper } from './writer';

export const statsPath = (hid: string) => `households/${hid}/stats`;

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
