import { plural, str } from '../../core/i18n';
import type { ActivityEvent } from '../../domain/home/models';
import { reasonLabel } from '../pantry/labels';

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const WEEK = 7 * DAY;

/** Calendar days between two instants in the phone's time zone. */
function calendarDays(from: number, to: number): number {
  const day = (t: number) => {
    const d = new Date(t);
    return Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / DAY;
  };
  return day(to) - day(from);
}

/**
 * "Just now", "5 minutes ago", "2 hours ago", "Yesterday", "3 days ago", then "Oct 12, 2026": the wording of
 * DateUtils.getRelativeTimeSpanString with minute resolution, which Recent activity uses in the app.
 */
export function relativeTime(at: number, now: number, locale?: string): string {
  const ago = now - at;
  if (ago < MINUTE) return str('activity_just_now');
  const relative = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
  const capitalized = (text: string) => text.charAt(0).toLocaleUpperCase(locale) + text.slice(1);
  if (ago < HOUR) return capitalized(relative.format(-Math.floor(ago / MINUTE), 'minute'));
  if (ago < DAY) return capitalized(relative.format(-Math.floor(ago / HOUR), 'hour'));
  if (ago < WEEK) return capitalized(relative.format(-calendarDays(at, now), 'day'));
  return new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric', year: 'numeric' }).format(at);
}

/** "Sam added Eggs to Groceries" (DESIGN 5.6); the signed-in member's own events read "You". */
export function activityText(event: ActivityEvent, myUid: string): string {
  const who = event.actorUid === myUid ? str('activity_you') : event.actorName;
  const name = event.name ?? '';
  const list = event.listName ?? str('activity_the_list');
  switch (event.type) {
    case 'stock_added':
      return str('activity_stock_added', who, name);
    case 'grocery_added':
      return event.count !== null
        ? plural('activity_grocery_added_from_recipe', event.count, who, event.count, name, list)
        : str('activity_grocery_added', who, name, list);
    case 'grocery_checked':
      return str('activity_grocery_checked', who, name);
    case 'recipe_saved':
      return str('activity_recipe_saved', who, name);
    case 'recipe_cooked':
      return str('activity_recipe_cooked', who, name);
    case 'member_joined':
      return str('activity_member_joined', who);
    case 'household_reset':
      return str('activity_household_reset', who);
    case 'discarded':
      return event.reason
        ? str('activity_discarded_reason', who, name, reasonLabel(event.reason).toLowerCase())
        : str('activity_discarded', who, name);
  }
}
