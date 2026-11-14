// Ported from AmountsTest.
import { describe, expect, it } from 'vitest';
import { Amounts } from './amounts';

describe('Amounts (after v1, LOGIC-12)', () => {
  it('amounts are written in ASCII digits and read back', () => {
    expect(Amounts.format(1.5)).toBe('1.5');
    expect(Amounts.parse(Amounts.format(1.5))).toBe(1.5);
    // The stepper: + on "1" twice.
    expect(Amounts.format(Amounts.parse(Amounts.format(Amounts.parse('1')! + 1))! + 1)).toBe('3');
  });

  it('typed native digits and separators are read', () => {
    expect(Amounts.parse('١٫٥')).toBe(1.5);
    expect(Amounts.parse('۲٫۲۵')).toBe(2.25);
    expect(Amounts.parse('1,5')).toBe(1.5);
    expect(Amounts.parse('1..5')).toBeNull();
  });
});
