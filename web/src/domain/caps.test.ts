// Ported from ReadCapsTest.
import { describe, expect, it } from 'vitest';
import { capOrNull, capTo, ReadCaps } from './caps';

describe('capTo', () => {
  it('keeps text within the cap as it is', () => {
    expect(capTo('Milk', ReadCaps.NAME)).toBe('Milk');
    expect(capTo('x'.repeat(80), ReadCaps.NAME)).toBe('x'.repeat(80));
    expect(capOrNull(null, ReadCaps.NAME)).toBeNull();
  });

  it('cuts longer text to the cap', () => {
    expect(capTo('x'.repeat(500_000), ReadCaps.NAME)).toBe('x'.repeat(80));
    expect(capTo('Samantha', 3)).toBe('Sam');
  });

  it('does not split a character made of two units', () => {
    expect(capTo('ab🥛', 3)).toBe('ab');
    expect(capTo('ab🥛c', 4)).toBe('ab🥛');
  });
});
