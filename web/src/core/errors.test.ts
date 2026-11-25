// Ported from ErrorMessagesTest: a refused write that lost to another phone says why (DESIGN 8.5).
import { describe, expect, it } from 'vitest';
import { AppError, errorText, lostRace, messageId } from './errors';
import { str } from './i18n';

const names = new Map([
  ['sam', 'Sam'],
  ['me', 'Alex'],
]);
const text = (error: unknown) => errorText(error, (uid) => names.get(uid), 'me');

describe('errorText', () => {
  it('a lost Add to pantry, removal or Cooked names the member whose change won', () => {
    expect(text(lostRace('addedToPantry', 'sam'))).toBe(str('race_pantry_by', 'Sam'));
    expect(text(lostRace('removed', 'sam'))).toBe(str('race_removed_by', 'Sam'));
    expect(text(lostRace('cooked', 'sam'))).toBe(str('race_cooked_by', 'Sam'));
    expect(text(lostRace('addedToPantry', 'sam'))).toBe(
      'Sam already took these off the list. Nothing was added to the pantry.',
    );
  });

  it('without a known name, or when it was this account on another phone, it says it without one', () => {
    expect(text(lostRace('addedToPantry', 'me'))).toBe(str('race_pantry'));
    expect(text(lostRace('cooked', 'left-the-household'))).toBe(str('race_cooked'));
    expect(text(lostRace('removed'))).toBe(str('race_removed'));
    expect(errorText(lostRace('addedToPantry', 'sam'))).toBe(str('race_pantry'));
  });

  it('a deleted list is never anyone’s name, and other errors keep their message', () => {
    expect(text(lostRace('listDeleted', 'sam'))).toBe(str('race_list_deleted'));
    expect(messageId(lostRace('listDeleted'))).toBe('race_list_deleted');
    expect(text(new AppError('permissionDenied'))).toBe(str('error_permission'));
    expect(text(new Error('boom'))).toBe(str('error_unknown'));
  });
});
