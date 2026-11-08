// Names from another client are cut to DESIGN 8.1's lengths on read (PantryReadTest and the others in the app).
import type { DocumentSnapshot } from 'firebase/firestore';
import { describe, expect, it } from 'vitest';
import { toEvent } from './activity';
import { toItem, toList } from './grocery';
import { toHousehold, toInviteCode, toJoinRequest, toMember } from './households';
import { toBatch, toProduct } from './pantry';
import { toMeal } from './plan';
import { toRecipe } from './recipes';

/** A stored document with [fields], read through the getters the mappers use. */
const snapshot = (id: string, fields: Record<string, unknown>) =>
  ({
    id,
    get: (field: string) => fields[field] ?? null,
    exists: () => true,
    metadata: { hasPendingWrites: false, fromCache: false },
  }) as unknown as DocumentSnapshot;

const long = (c: string) => c.repeat(100_000);

describe('reading names', () => {
  it('cuts products, batches and grocery items to 80 characters and lists to 60', () => {
    expect(toProduct(snapshot('p1', { name: long('M') })).name).toBe('M'.repeat(80));
    expect(toBatch(snapshot('s1', { productName: long('M') })).productName).toBe('M'.repeat(80));
    expect(toItem(snapshot('i1', { name: long('E') })).name).toBe('E'.repeat(80));
    expect(toList(snapshot('g1', { name: long('L') })).name).toBe('L'.repeat(60));
    expect(toProduct(snapshot('p2', { name: 'Milk' })).name).toBe('Milk');
  });

  it('cuts people to 40 characters and households to 60', () => {
    expect(toMember(snapshot('u1', { displayName: long('N'), role: 'member' })).displayName).toBe('N'.repeat(40));
    expect(toJoinRequest(snapshot('u2', { displayName: long('N'), status: 'pending' }))?.displayName).toBe(
      'N'.repeat(40),
    );
    expect(toHousehold(snapshot('h1', { name: long('N') })).name).toBe('N'.repeat(60));
    expect(toInviteCode(snapshot('ABCDE23456', { householdName: long('N') })).householdName).toBe('N'.repeat(60));
  });

  it('cuts recipe titles to 200, meal titles to 120 and an event to its lengths', () => {
    expect(toRecipe(snapshot('r1', { title: long('t') })).content.title).toBe('t'.repeat(200));
    expect(toMeal(snapshot('m1', { date: '2026-11-08', slot: 'dinner', title: long('t') }))?.title).toBe(
      't'.repeat(120),
    );
    const event = toEvent(
      snapshot('a1', {
        type: 'grocery_added',
        actorUid: 'u1',
        actorName: long('A'),
        params: { name: long('x'), listName: long('l') },
      }),
    );
    expect(event?.actorName).toBe('A'.repeat(40));
    expect(event?.name).toBe('x'.repeat(200));
    expect(event?.listName).toBe('l'.repeat(60));
  });
});
