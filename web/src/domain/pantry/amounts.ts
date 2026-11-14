// Amounts as people read and type them (after v1, LOGIC-12), ported from Amounts. Always written with ASCII digits
// and a point; typed amounts may use the phone's own digits and a comma.
import { NameKey } from './rules';

const NUMBER = /^\d*\.?\d+$|^\d+\.$/;

/**
 * Where each set of ten decimal digits starts in the Basic Multilingual Plane besides ASCII (Unicode's Nd): the same
 * list as Android's, so both apps read the same typed digits.
 */
const DIGIT_ZEROS = [
  0x0660, 0x06f0, 0x07c0, 0x0966, 0x09e6, 0x0a66, 0x0ae6, 0x0b66, 0x0be6, 0x0c66, 0x0ce6, 0x0d66, 0x0de6, 0x0e50,
  0x0ed0, 0x0f20, 0x1040, 0x1090, 0x17e0, 0x1810, 0x1946, 0x19d0, 0x1a80, 0x1a90, 0x1b50, 0x1bb0, 0x1c40, 0x1c50,
  0xa620, 0xa8d0, 0xa900, 0xa9d0, 0xa9f0, 0xaa50, 0xabf0, 0xff10,
];
const SEPARATORS = new Set([',', '٫', '،']);

function ascii(c: string): string {
  const code = c.charCodeAt(0);
  const zero = DIGIT_ZEROS.find((z) => code >= z && code <= z + 9);
  if (zero !== undefined) return String(code - zero);
  return SEPARATORS.has(c) ? '.' : c;
}

export const Amounts = {
  /** 2, 1.5 or 0.25: no trailing zeros, at most two decimals, halves rounded up (DecimalFormat HALF_UP). */
  format: (value: number): string => String(Number(value.toFixed(2))),

  /** A typed amount, or null when it is not a number: "1.5", "1,5", "١٫٥" and "۱.۵" are all 1.5. */
  parse(text: string): number | null {
    const clean = NameKey.clean(text);
    let value = '';
    // By UTF-16 code unit, as Android reads a String's chars.
    for (let i = 0; i < clean.length; i++) value += ascii(clean[i]!);
    return NUMBER.test(value) ? Number(value) : null;
  },
};
