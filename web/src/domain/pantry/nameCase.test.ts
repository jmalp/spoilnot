import { describe, expect, it } from 'vitest';
import { NameCase } from './nameCase';
import { NameKey } from './rules';

// A food's name is saved with every word capitalized (DESIGN 8.1; owner, 2026-11-21), as Android's NameCaseTest.
describe('NameCase', () => {
  it('starts every word with a capital, whatever was typed', () => {
    expect(NameCase.of('black beans')).toBe('Black Beans');
    expect(NameCase.of('Black beans')).toBe('Black Beans');
    expect(NameCase.of('super Pickled pickles of Doom')).toBe('Super Pickled Pickles Of Doom');
  });

  it('keeps the rest of each word as typed', () => {
    expect(NameCase.of('BBQ sauce')).toBe('BBQ Sauce');
    expect(NameCase.of('m&Ms')).toBe('M&Ms');
    expect(NameCase.of("o'brien potatoes")).toBe("O'brien Potatoes");
  });

  it('cleans the name first', () => {
    expect(NameCase.of('  greek   yogurt ')).toBe('Greek Yogurt');
  });

  it('keeps a word open after punctuation, not after a digit', () => {
    expect(NameCase.of('eggs (organic)')).toBe('Eggs (Organic)');
    expect(NameCase.of('"fancy" ham')).toBe('"Fancy" Ham');
    expect(NameCase.of('2% milk')).toBe('2% Milk');
    expect(NameCase.of('7up')).toBe('7up');
    expect(NameCase.of('rice 2kg')).toBe('Rice 2kg');
  });

  it('handles letters beyond English, and keeps one with no single capital', () => {
    expect(NameCase.of('élan crème fraîche')).toBe('Élan Crème Fraîche');
    expect(NameCase.of('ñame')).toBe('Ñame');
    expect(NameCase.of('ßtraße')).toBe('ßtraße');
  });

  it("doesn't change matching", () => {
    expect(NameKey.of(NameCase.of('black beans'))).toBe(NameKey.of('black beans'));
  });
});
