// A field of another type reads as missing, so one document written by a buggy client, a console edit or a restore
// doesn't keep a list spinning on this phone (after v1, review WEB-10; WrongTypeReadTest in the app). The first cases
// are review 05's round-1 documents, then the review's list fields that threw.
import type { DocumentSnapshot } from 'firebase/firestore';
import { describe, expect, it, vi } from 'vitest';
import { toEvent } from './activity';
import { mapEach, millis, timeOf } from './fields';
import { toItem, toList } from './grocery';
import { toHousehold, toInviteCode, toJoinRequest, toMember } from './households';
import { toBatch, toProduct } from './pantry';
import { toMeal } from './plan';
import { toRecipe } from './recipes';

/** A stored document with [fields]; a timestamp is { toMillis }, as the SDK's Timestamp. */
const snapshot = (id: string, fields: Record<string, unknown>) =>
  ({
    id,
    get: (field: string) => fields[field] ?? null,
    exists: () => true,
    metadata: { hasPendingWrites: false, fromCache: false },
  }) as unknown as DocumentSnapshot;
const time = (ms: number) => ({ toMillis: () => ms });

describe('reading wrong types', () => {
  it("reads review 05's round 1 without throwing", () => {
    const batch = toBatch(snapshot('bad-qty', { productName: 'Milk', quantity: '2', expiryDate: '2026-11-20' }));
    expect([batch.quantity, batch.productName, batch.expiryDate]).toEqual([0, 'Milk', '2026-11-20']);
    expect(toItem(snapshot('bad-checked', { name: 'Bad checked', checked: 'yes' })).checked).toBe(false);
    expect(toRecipe(snapshot('bad-servings', { title: 'Bad servings', servings: '4' })).content.servings).toBeNull();
    expect(toMeal(snapshot('bad-position', { date: '2026-11-11', slot: 'lunch', position: '1' }))?.position).toBe(0);
    const event = toEvent(snapshot('bad-name', { type: 'stock_added', actorName: 42, params: { name: 'Eggs' } }));
    expect([event?.actorName, event?.name]).toEqual(['', 'Eggs']);
  });

  it('reads list fields holding text as empty lists (they threw before)', () => {
    const product = toProduct(snapshot('p1', { name: 'Milk', recentShelfLifeDays: '7', aliases: 'whole milk' }));
    expect([product.recentShelfLifeDays, product.aliases]).toEqual([[], []]);
    const recipe = toRecipe(snapshot('r1', { title: 'Soup', ingredients: 'one string', steps: 'Cook.', tags: 'x' }));
    expect([recipe.content.ingredients, recipe.content.steps, recipe.content.tags]).toEqual([[], [], []]);
    expect(toMeal(snapshot('m1', { date: '2026-11-11', slot: 'dinner', cooks: 'u1' }))?.cooks).toEqual([]);
    expect(toList(snapshot('g1', { name: 'Groceries', aisleOrder: 'dairy' })).aisleOrder).toEqual([]);
  });

  it('leaves out list entries of another type', () => {
    const product = toProduct(snapshot('p1', { recentShelfLifeDays: [7, '8', 9], aliases: ['a', 3] }));
    expect([product.recentShelfLifeDays, product.aliases]).toEqual([[7, 9], ['a']]);
    const recipe = toRecipe(snapshot('r1', { tags: ['x', 3], ingredients: ['2 eggs', { raw: '1 onion' }, [1]] }));
    expect(recipe.content.tags).toEqual(['x']);
    expect(recipe.content.ingredients.map((i) => i.raw)).toEqual(['1 onion']);
    expect(toEvent(snapshot('a1', { type: 'stock_added', params: 'x' }))?.name).toBeNull();
  });

  it('reads times of another type as missing, and real times still', () => {
    expect(toBatch(snapshot('s1', { createdAt: '2026-12-24T18:00:00Z' })).createdAt).toBeNull();
    expect(toItem(snapshot('i1', { createdAt: time(5) })).createdAt).toBe(5);
    expect(toMeal(snapshot('m1', { date: '2026-11-11', slot: 'dinner', cookedAt: 5 }))?.cookedAt).toBeNull();
    expect(toRecipe(snapshot('r1', { createdAt: { toMillis: 'no' } })).createdAt).toBeNull();
    expect(millis(snapshot('x', { updatedAt: '2026-11-01' }), 'updatedAt', false)).toBeNull();
    expect(timeOf(time(7))).toBe(7);
  });

  it('reads people, households and invite codes with wrong types', () => {
    const member = toMember(snapshot('u1', { displayName: 42, role: true, joinedAt: 'yesterday' }));
    expect([member.displayName, member.role, member.joinedAt]).toEqual(['', 'member', null]);
    expect(toHousehold(snapshot('h1', { name: 7, ownerUid: 'u1', inviteCode: ['A'] }))).toMatchObject({
      name: '',
      ownerUid: 'u1',
      inviteCode: '',
    });
    expect(toInviteCode(snapshot('ABC', { householdId: 'h1', householdName: 3 }))).toEqual({
      code: 'ABC',
      householdId: 'h1',
      householdName: '',
    });
    const request = toJoinRequest(snapshot('u2', { status: 'pending', displayName: 3, joinCode: 1234 }));
    expect([request?.displayName, request?.code]).toEqual(['', '']);
  });

  it('skips a document that fails to map and keeps the others', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const docs = [snapshot('g1', { name: 'Groceries' }), snapshot('g2', { name: 'Party' })];
    const lists = mapEach(docs, (d) => {
      if (d.id === 'g2') throw new Error('boom');
      return toList(d);
    });
    expect(lists.map((l) => l.id)).toEqual(['g1']);
    expect(warn).toHaveBeenCalledOnce();
    warn.mockRestore();
    // A mapper's null leaves a document out on purpose.
    expect(mapEach(docs, (d) => (d.id === 'g1' ? null : d.id))).toEqual(['g2']);
  });
});
