// Week math and the week view (DESIGN 5.9), ported from MealWeek. Pure, so it is tested with a fixed today and week
// start.
import { NameKey } from '../pantry/rules';
import { addDays, compareStrings, daysBetween, epochDay, nullsLast, sortedWith, type LocalDate } from '../time';
import { MEAL_SLOTS, type MealSlot, type PlannedMeal } from './models';

/** 1 is Monday, 7 is Sunday (java.time.DayOfWeek). */
export type DayOfWeek = 1 | 2 | 3 | 4 | 5 | 6 | 7;

/** The day of the week of [date]; 1970-01-01 was a Thursday. */
export const dayOfWeek = (date: LocalDate): DayOfWeek => (((((epochDay(date) + 3) % 7) + 7) % 7) + 1) as DayOfWeek;

/** Regions whose weeks start on Sunday (CLDR), for browsers without Intl week info. */
const SUNDAY_REGIONS = new Set(
  'AG AS BD BR BS BT BW BZ CA CN CO DM DO ET GT GU HK HN ID IL IN JM JP KE KH KR LA MH MM MO MT MX MZ NI NP PA PE PH PK PR PT PY SA SG SV TH TT TW UM US VE VI WS YE ZA ZW'.split(
    ' ',
  ),
);

/** The first day of the week in the phone's region (WeekFields.of(Locale.getDefault()) in the app). */
export function firstDayOfWeek(locale: string): DayOfWeek {
  try {
    const l = new Intl.Locale(locale) as Intl.Locale & {
      getWeekInfo?: () => { firstDay: number };
      weekInfo?: { firstDay: number };
    };
    const info = l.getWeekInfo?.() ?? l.weekInfo;
    if (info && info.firstDay >= 1 && info.firstDay <= 7) return info.firstDay as DayOfWeek;
    const region = l.maximize().region;
    return region && SUNDAY_REGIONS.has(region) ? 7 : 1;
  } catch {
    return 1;
  }
}

/** One day of the plan: its meals by slot, in order. */
export type PlanDay = {
  date: LocalDate;
  isToday: boolean;
  isPast: boolean;
  slots: Record<MealSlot, PlannedMeal[]>;
};

export const PlanDays = {
  isEmpty: (day: PlanDay) => MEAL_SLOTS.every((s) => day.slots[s].length === 0),
  /** Every meal of the day, breakfast first (Home's Today card). */
  meals: (day: PlanDay): PlannedMeal[] => MEAL_SLOTS.flatMap((s) => day.slots[s]),
};

export const MealWeek = {
  DAYS: 7,

  /** The first day of the week containing [date], for weeks starting on [firstDay]. */
  start: (date: LocalDate, firstDay: DayOfWeek): LocalDate => addDays(date, -((dayOfWeek(date) - firstDay + 7) % 7)),

  end: (start: LocalDate): LocalDate => addDays(start, MealWeek.DAYS - 1),

  days: (start: LocalDate): LocalDate[] => Array.from({ length: MealWeek.DAYS }, (_, i) => addDays(start, i)),

  /** The week starting [start]: every day with its three slots, meals ordered by position, then creation. */
  build(start: LocalDate, meals: PlannedMeal[], today: LocalDate): PlanDay[] {
    return MealWeek.days(start).map((date) => {
      const dayMeals = meals.filter((m) => m.date === date);
      const slots = Object.fromEntries(
        MEAL_SLOTS.map((slot) => [
          slot,
          sortedWith(
            dayMeals.filter((m) => m.slot === slot),
            (a, b) => a.position - b.position,
            (a, b) => nullsLast<number>((x, y) => x - y)(a.createdAt, b.createdAt),
          ),
        ]),
      ) as Record<MealSlot, PlannedMeal[]>;
      return { date, isToday: date === today, isPast: date < today, slots };
    });
  },

  /** Where a new meal goes in a slot: after the last one. */
  nextPosition: (slotMeals: PlannedMeal[]): number => Math.max(-1, ...slotMeals.map((m) => m.position)) + 1,

  /**
   * Copy last week: [previous] meals moved by whole weeks from [from] to [to], keeping recipe, title, servings, cooks
   * and note but not Cooked. A meal already in [existing] on the same day and slot with the same name is skipped, so
   * copying twice adds nothing. Copies go after the meals already in their slot, in their old order. Ids are left
   * empty for the service to fill.
   */
  copy(previous: PlannedMeal[], from: LocalDate, to: LocalDate, existing: PlannedMeal[]): PlannedMeal[] {
    const shift = daysBetween(from, to);
    const key = (date: LocalDate, slot: MealSlot, title: string) => `${date}|${slot}|${NameKey.of(title)}`;
    const taken = new Set(existing.map((m) => key(m.date, m.slot, m.title)));
    const next = new Map<string, number>();
    for (const m of existing) {
      const k = `${m.date}|${m.slot}`;
      next.set(k, Math.max(next.get(k) ?? 0, m.position + 1));
    }
    const ordered = sortedWith(
      previous,
      (a, b) => compareStrings(a.date, b.date),
      (a, b) => MEAL_SLOTS.indexOf(a.slot) - MEAL_SLOTS.indexOf(b.slot),
      (a, b) => a.position - b.position,
    );
    const copies: PlannedMeal[] = [];
    for (const meal of ordered) {
      const date = addDays(meal.date, shift);
      const k = key(date, meal.slot, meal.title);
      if (taken.has(k)) continue;
      taken.add(k);
      const slotKey = `${date}|${meal.slot}`;
      const position = next.get(slotKey) ?? 0;
      next.set(slotKey, position + 1);
      copies.push({
        ...meal,
        id: '',
        date,
        position,
        cookedAt: null,
        cookedBy: null,
        createdAt: null,
        createdBy: null,
        pendingSync: false,
      });
    }
    return copies;
  },
};
