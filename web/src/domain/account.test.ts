// Ported from AccountValidationTest and InviteCodesTest.
import { describe, expect, it } from 'vitest';
import { AccountValidation } from './account';
import { HouseholdRules, InviteCodes } from './household';

describe('AccountValidation', () => {
  it('display names are 1 to 40 characters after trimming', () => {
    expect(AccountValidation.displayNameProblem('  ')).toBe('blank');
    expect(AccountValidation.displayNameProblem(` ${'a'.repeat(40)} `)).toBeNull();
    expect(AccountValidation.displayNameProblem('a'.repeat(41))).toBe('tooLong');
  });

  it('passwords need at least 8 characters', () => {
    expect(AccountValidation.isPasswordLongEnough('1234567')).toBe(false);
    expect(AccountValidation.isPasswordLongEnough('12345678')).toBe(true);
  });

  it('email check accepts normal addresses and rejects obvious mistakes', () => {
    expect(AccountValidation.isEmailValid(' sam@example.com ')).toBe(true);
    for (const bad of ['sam', 'sam@', 'sam@example', '@example.com', 'sam @example.com']) {
      expect(AccountValidation.isEmailValid(bad)).toBe(false);
    }
  });

  it('fallback display name comes from the email', () => {
    expect(AccountValidation.fallbackDisplayName('sam@example.com')).toBe('sam');
    expect(AccountValidation.fallbackDisplayName(null)).toBe('Me');
    expect(AccountValidation.fallbackDisplayName('@example.com')).toBe('Me');
  });

  it('household names are 1 to 60 characters', () => {
    expect(HouseholdRules.isNameValid(' ')).toBe(false);
    expect(HouseholdRules.isNameValid('a'.repeat(60))).toBe(true);
    expect(HouseholdRules.isNameValid('a'.repeat(61))).toBe(false);
  });
});

describe('InviteCodes', () => {
  it('generated codes are 10 characters from the safe alphabet', () => {
    for (let i = 0; i < 100; i++) expect(InviteCodes.isWellFormed(InviteCodes.generate())).toBe(true);
  });

  it('the alphabet has no look-alike characters', () => {
    for (const c of 'IO01') expect(InviteCodes.ALPHABET).not.toContain(c);
    expect(InviteCodes.ALPHABET).toHaveLength(32);
  });

  it('generation uses the given random source', () => {
    expect(InviteCodes.generate(() => 0)).toBe('AAAAAAAAAA');
    expect(InviteCodes.generate((bound) => bound - 1)).toBe('9999999999');
  });

  it('codes cover the whole alphabet', () => {
    const seen = new Set(Array.from({ length: 200 }, () => InviteCodes.generate()).join(''));
    expect(seen.size).toBe(32);
  });

  it('normalize uppercases and drops separators and extra characters', () => {
    expect(InviteCodes.normalize('abcde-23456')).toBe('ABCDE23456');
    expect(InviteCodes.normalize(' ab cd e2345 6789')).toBe('ABCDE23456');
  });

  it('well-formed means exact length and only safe characters', () => {
    expect(InviteCodes.isWellFormed('ABCDE23456')).toBe(true);
    expect(InviteCodes.isWellFormed('ABCDE2345')).toBe(false);
    expect(InviteCodes.isWellFormed('ABCDE2345O')).toBe(false);
  });

  it('format adds a hyphen after five characters', () => {
    expect(InviteCodes.format('ABCDE23456')).toBe('ABCDE-23456');
    expect(InviteCodes.format('ABC')).toBe('ABC');
  });
});
