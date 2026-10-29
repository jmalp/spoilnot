import { describe, expect, it } from 'vitest';
import { applyTextScale, scaledSp } from './textScale';

describe('text scale (FontScaleConverterFactory)', () => {
  it('is linear at and just above the default size', () => {
    expect(scaledSp(16, 1)).toBe(16);
    expect(scaledSp(16, 0.85)).toBeCloseTo(13.6);
    expect(scaledSp(16, 1.1)).toBeCloseTo(17.6);
  });

  it("follows Android's tables at their scales", () => {
    expect(scaledSp(14, 2)).toBe(26);
    expect(scaledSp(24, 2)).toBe(36);
    expect(scaledSp(16, 2)).toBe(28); // between 14 and 18
    expect(scaledSp(28, 2)).toBeCloseTo(37.33, 2); // headlineMedium grows by a third, not double
    expect(scaledSp(14, 1.5)).toBe(22);
    expect(scaledSp(12, 1.3)).toBeCloseTo(15.6);
  });

  it('mixes the two nearest tables between their scales, and grows past 200 %', () => {
    expect(scaledSp(14, 1.9)).toBeCloseTo((24.4 + 26) / 2);
    expect(scaledSp(14, 3)).toBeCloseTo(39);
    expect(scaledSp(57, 2)).toBeGreaterThan(57);
  });

  it('sets a variable for each size', () => {
    applyTextScale(2);
    const root = document.documentElement.style;
    expect(root.getPropertyValue('--sp-14')).toBe('26px');
    expect(root.getPropertyValue('--sp-28')).toBe('37.33px');
    expect(root.fontSize).toBe('32px');
  });
});
