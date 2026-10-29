// The app's words (DESIGN 18.2), generated from the Android strings.xml. Formats follow Android's: %1$s and %2$d
// are positional, %s and %d take the arguments in order.
import { PLURALS, STRINGS } from '../strings.generated';

export type StringId = keyof typeof STRINGS;
export type PluralId = keyof typeof PLURALS;
type Arg = string | number;

const rules = new Intl.PluralRules('en');

export function format(template: string, args: readonly Arg[]): string {
  let next = 0;
  return template.replace(/%(?:(\d+)\$)?([sd%])/g, (match, position: string | undefined, kind: string) => {
    if (kind === '%') return '%';
    const value = args[position ? Number(position) - 1 : next++];
    if (value === undefined) return match;
    return kind === 'd' ? String(Math.trunc(Number(value))) : String(value);
  });
}

/** `getString(R.string.id, args)`. */
export function str(id: StringId, ...args: Arg[]): string {
  return format(STRINGS[id], args);
}

/** `getQuantityString(R.plurals.id, count, args)`: [count] picks the form; the arguments fill it. */
export function plural(id: PluralId, count: number, ...args: Arg[]): string {
  const forms: Partial<Record<Intl.LDMLPluralRule, string>> = PLURALS[id];
  const template = forms[rules.select(count)] ?? forms.other ?? '';
  return format(template, args);
}
