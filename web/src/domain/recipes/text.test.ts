// Ported from RecipeTextTest and SourceUrlTest (the parts the web uses; imports stay on Android).
import { describe, expect, it } from 'vitest';
import { RecipeText, SourceUrl } from './text';

describe('RecipeText', () => {
  it('clean strips tags, decodes entities and collapses whitespace', () => {
    expect(RecipeText.clean('  <b>Mac</b> &amp;   Cheese ', 200)).toBe('Mac & Cheese');
    expect(RecipeText.clean('Fish &amp;amp; Chips', 200)).toBe('Fish & Chips');
    expect(RecipeText.clean('abcdef', 3)).toBe('abc');
    expect(RecipeText.clean('  <br> ', 10)).toBeNull();
    expect(RecipeText.clean(null, 10)).toBeNull();
    // Not a tag: kept as typed.
    expect(RecipeText.clean('Ready in < 5 min', 100)).toBe('Ready in < 5 min');
  });

  it('tags are split, de-duplicated keeping the first spelling, and capped at eight', () => {
    expect(RecipeText.tags(['Dinner, quick', 'dinner', 'Quick'])).toEqual(['Dinner', 'quick']);
    expect(RecipeText.tags(Array.from({ length: 12 }, (_, i) => `tag${i + 1}`))).toHaveLength(8);
  });
});

describe('SourceUrl', () => {
  it('canonical drops tracking parameters, the fragment and default ports', () => {
    expect(SourceUrl.canonical('HTTPS://Example.COM:443/pie/?utm_source=x&id=3&utm_medium=y&fbclid=z#comments')).toBe(
      'https://example.com/pie/?id=3',
    );
    expect(SourceUrl.canonical('https://example.com')).toBe('https://example.com/');
    expect(SourceUrl.canonical('http://example.com:8080/a')).toBe('http://example.com:8080/a');
  });

  it('only http and https', () => {
    for (const url of [
      'javascript:alert(1)',
      'file:///etc/hosts',
      'content://media/1',
      'intent://x#Intent;end',
      'example.com/pie',
      'not a url',
    ]) {
      expect(SourceUrl.canonical(url)).toBeNull();
    }
  });

  it('the site name for the source chip', () => {
    expect(SourceUrl.siteName('https://www.SeriousEats.com/pie')).toBe('seriouseats.com');
    expect(SourceUrl.siteName(null)).toBeNull();
  });
});
