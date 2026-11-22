// How a food's name is saved (DESIGN 8.1; owner, 2026-11-21), ported from Android's NameCase.
import { NameKey } from './rules';

const LETTER = /\p{L}/u;
const DIGIT = /\p{Nd}/u;

/**
 * Every word of a food's name starts with a capital letter and keeps the rest as typed, so "black beans" and "Black
 * beans" are both saved as "Black Beans", and "BBQ sauce" as "BBQ Sauce". Used for pantry items and grocery items,
 * whoever adds them; not for recipe titles, lists or people.
 */
export const NameCase = {
  /**
   * [name], already cleaned (NameKey.clean), with each word's first letter in capitals. A word starts after a space;
   * punctuation before its first letter keeps it open ("(organic)" becomes "(Organic)"), a digit closes it ("2kg"
   * stays). A letter with no single capital (ß) is kept, as on Android.
   */
  title(name: string): string {
    let out = '';
    let wordStart = true;
    for (const c of name) {
      if (c === ' ') {
        wordStart = true;
      } else if (wordStart && LETTER.test(c)) {
        const upper = c.toUpperCase();
        out += upper.length === c.length ? upper : c;
        wordStart = false;
        continue;
      } else if (wordStart && DIGIT.test(c)) {
        wordStart = false;
      }
      out += c;
    }
    return out;
  },

  /** A typed name as it is saved: cleaned, then title. */
  of(name: string): string {
    return NameCase.title(NameKey.clean(name));
  },
};
