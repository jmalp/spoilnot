import { describe, expect, it } from 'vitest';
import { format, plural, str } from './i18n';

describe('i18n', () => {
  it('formats like Android: positional and sequential arguments', () => {
    expect(format('%1$s – %2$s', ['Oct 26', 'Nov 1'])).toBe('Oct 26 – Nov 1');
    expect(format('%s of %d', ['3', 10.7])).toBe('3 of 10');
    expect(format('100%%', [])).toBe('100%');
  });

  it('uses the same words as the Android app', () => {
    expect(str('tab_plan')).toBe('Plan');
    expect(str('share_message', 'Home', 'ABCDE-FGHIJ')).toBe(
      'Join our household "Home" on Spoil Not with the code ABCDE-FGHIJ',
    );
  });

  it('picks the plural form by count', () => {
    expect(plural('plan_servings', 1, 1)).toBe('1 serving');
    expect(plural('plan_servings', 4, 4)).toBe('4 servings');
  });
});
