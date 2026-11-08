// The longest text the screens take from Firestore (ReadCaps in the app, DESIGN 8.1). The rules limit what is
// written, but data written before they did can be longer, and a very long name would slow every list showing it.
export const ReadCaps = {
  /** Products, batches and grocery items. */
  NAME: 80,
  /** Display names of people. */
  PERSON: 40,
  /** Households and grocery lists. */
  LIST_NAME: 60,
  RECIPE_TITLE: 200,
  MEAL_TITLE: 120,
} as const;

/** [text] cut to [max] characters (UTF-16 units), without splitting a character made of two units. */
export function capTo(text: string, max: number): string {
  if (text.length <= max) return text;
  const code = max > 0 ? text.charCodeAt(max - 1) : 0;
  const end = code >= 0xd800 && code <= 0xdbff ? max - 1 : max;
  return text.slice(0, end);
}

/** The same for an optional text. */
export const capOrNull = (text: string | null, max: number): string | null => (text === null ? null : capTo(text, max));
