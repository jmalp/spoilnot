import { str, type StringId } from '../../core/i18n';
import type { Member } from '../../domain/household';
import type { MealSlot } from '../../domain/plan/models';
import { firstDayOfWeek } from '../../domain/plan/week';
import type { LocalDate } from '../../domain/time';
import { Icon } from '../kit';
import './plan.css';

/** The phone's region decides the first day of the week (Sunday in the US, Monday in most places). */
export const phoneFirstDay = () => firstDayOfWeek(typeof navigator === 'undefined' ? 'en-US' : navigator.language);

const utc = (date: LocalDate) => Date.parse(`${date}T12:00:00Z`);

/** "Thursday, Oct 29", in the phone's language. */
export function dayTitle(date: LocalDate): string {
  const weekday = new Intl.DateTimeFormat(undefined, { weekday: 'long', timeZone: 'UTC' }).format(utc(date));
  return `${weekday}, ${shortDate(date)}`;
}

/** "Oct 29", in the phone's language. */
export const shortDate = (date: LocalDate): string =>
  new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' }).format(utc(date));

/** "Thu 29", for the Move chips. */
export function shortDay(date: LocalDate): string {
  const weekday = new Intl.DateTimeFormat(undefined, { weekday: 'short', timeZone: 'UTC' }).format(utc(date));
  return `${weekday} ${Number(date.slice(8, 10))}`;
}

export const slotLabel = (slot: MealSlot): string => str(`plan_slot_${slot}` as StringId);

/** Cooks' names in household order; members who left are not shown, and the user is "You". */
export const cookNames = (cooks: string[], members: Member[], myUid: string): string[] =>
  members.filter((m) => cooks.includes(m.uid)).map((m) => (m.uid === myUid ? str('plan_you') : m.displayName));

/** A cook's name with a person icon, so who cooks never depends on color. */
export function CookChip({ name }: { name: string }) {
  return (
    <span className="sn-cook-chip t-label-medium">
      <Icon name="round/person" size={14} />
      {name}
    </span>
  );
}

/** "Cooked" with a check: an icon and a word, never color alone. */
export function CookedLabel() {
  return (
    <span className="sn-cooked-label t-label-medium">
      <Icon name="round/check_circle" size={16} />
      {str('plan_cooked')}
    </span>
  );
}
