// Ported from MealWeekTest.
import { describe, expect, it } from 'vitest';
import { addDays } from '../time';
import { meal, THURSDAY } from './fixtures';
import { MEAL_SLOTS } from './models';
import { dayOfWeek, firstDayOfWeek, MealWeek, PlanDays } from './week';

describe('MealWeek', () => {
  it("the week starts on the phone's first day", () => {
    expect(dayOfWeek(THURSDAY)).toBe(4);
    expect(MealWeek.start(THURSDAY, 1)).toBe('2026-10-26');
    expect(MealWeek.start(THURSDAY, 7)).toBe('2026-10-25');
    // A week start day is its own week's start.
    expect(MealWeek.start(THURSDAY, 4)).toBe(THURSDAY);
    expect(MealWeek.end('2026-10-26')).toBe('2026-11-01');
  });

  it('the first day of the week follows the region', () => {
    expect(firstDayOfWeek('en-US')).toBe(7);
    expect(firstDayOfWeek('en-GB')).toBe(1);
    expect(firstDayOfWeek('es-ES')).toBe(1);
    expect(firstDayOfWeek('not a locale!')).toBe(1);
  });

  it('seven days, each with three slots, meals ordered by position then creation', () => {
    const start = '2026-10-26';
    const meals = [
      meal('late', { position: 1 }),
      meal('first', { position: 0, createdSecond: 5 }),
      meal('tie', { position: 0, createdSecond: 9 }),
      meal('lunch', { slot: 'lunch' }),
      meal('next week', { date: '2026-11-02' }),
    ];
    const days = MealWeek.build(start, meals, THURSDAY);
    expect(days.map((d) => d.date)).toEqual(Array.from({ length: 7 }, (_, i) => addDays(start, i)));
    const day = days.find((d) => d.date === THURSDAY)!;
    expect(day.slots.dinner.map((m) => m.id)).toEqual(['first', 'tie', 'late']);
    expect(day.slots.lunch.map((m) => m.id)).toEqual(['lunch']);
    expect(day.slots.breakfast).toEqual([]);
    expect(day.isToday).toBe(true);
    expect(days[0]!.isPast).toBe(true);
    expect(days[6]!.isPast).toBe(false);
    expect(PlanDays.isEmpty(days[6]!)).toBe(true);
    expect(Object.keys(day.slots)).toEqual([...MEAL_SLOTS]);
  });

  it('a new meal goes after the last one in its slot', () => {
    expect(MealWeek.nextPosition([])).toBe(0);
    expect(MealWeek.nextPosition([meal('a', { position: 3 }), meal('b', { position: 1 })])).toBe(4);
  });

  it("a day's meals run from breakfast to dinner", () => {
    const day = MealWeek.build(
      THURSDAY,
      [meal('d', { slot: 'dinner' }), meal('b', { slot: 'breakfast' }), meal('l', { slot: 'lunch' })],
      THURSDAY,
    )[0]!;
    expect(PlanDays.meals(day).map((m) => m.id)).toEqual(['b', 'l', 'd']);
  });

  it('copying a week shifts the days, keeps the plan, forgets Cooked and skips what is already there', () => {
    const lastStart = '2026-10-19';
    const thisStart = '2026-10-26';
    const previous = [
      {
        ...meal('a', { date: addDays(lastStart, 3), position: 1 }),
        title: 'Tacos',
        recipeId: 'r1',
        servings: 4,
        cooks: ['u1'],
        note: 'Spicy',
        cookedAt: 0,
        cookedBy: 'u1',
      },
      { ...meal('b', { date: addDays(lastStart, 3), position: 0 }), title: 'Soup' },
      { ...meal('c', { date: lastStart, slot: 'lunch' }), title: 'Leftovers' },
    ];
    // This week already has Soup on Thursday dinner and something else in that slot.
    const existing = [
      { ...meal('x', { position: 0 }), title: 'soup' },
      { ...meal('y', { position: 5 }), title: 'Pizza' },
    ];
    const copies = MealWeek.copy(previous, lastStart, thisStart, existing);
    expect(copies.map((m) => m.title)).toEqual(['Leftovers', 'Tacos']);
    const tacos = copies[1]!;
    expect([tacos.date, tacos.position]).toEqual([THURSDAY, 6]);
    expect([tacos.recipeId, tacos.servings, tacos.cooks, tacos.note]).toEqual(['r1', 4, ['u1'], 'Spicy']);
    expect([tacos.cookedAt, tacos.cookedBy]).toEqual([null, null]);
    expect(copies[0]!.date).toBe(thisStart);
    // Copying again adds nothing.
    expect(MealWeek.copy(previous, lastStart, thisStart, [...existing, ...copies])).toEqual([]);
  });
});
