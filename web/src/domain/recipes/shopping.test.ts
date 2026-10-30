// Ported from RecipeShoppingTest.
import { describe, expect, it } from 'vitest';
import { product } from '../pantry/fixtures';
import { IngredientParser } from './parser';
import { ProductLink, ShoppingName } from './shopping';

const shop = (line: string) => {
  const parsed = IngredientParser.parse(line);
  return ShoppingName.of(parsed.name ?? parsed.raw);
};

describe('ShoppingName', () => {
  it('size, preparation and freshness words are dropped', () => {
    expect(shop('1 extra large onion, diced')).toBe('Onion');
    expect(shop('2 extra-large onions, finely chopped')).toBe('Onions');
    expect(shop('3 large eggs, beaten')).toBe('Eggs');
    expect(shop('4 ripe tomatoes')).toBe('Tomatoes');
    expect(shop('1 onion finely chopped')).toBe('Onion');
    expect(shop('2 boneless skinless chicken breasts')).toBe('Chicken breasts');
    expect(shop('3 cloves fresh garlic, minced')).toBe('Garlic');
    expect(shop('1 medium onion, peeled and diced')).toBe('Onion');
  });

  it('measures, notes in parentheses and serving phrases are dropped', () => {
    expect(shop('½ tbsp soy sauce')).toBe('Soy sauce');
    expect(shop('Salt to taste')).toBe('Salt');
    expect(shop('Fresh parsley, for garnish')).toBe('Parsley');
    expect(shop('2 tbsp butter or margarine')).toBe('Butter');
    expect(shop('2 tbsp olive oil, plus more for the pan')).toBe('Olive oil');
    expect(shop('1 (14 ounce) can diced tomatoes')).toBe('Tomatoes');
  });

  it('words that change what is bought stay', () => {
    expect(shop('1 tsp freshly ground black pepper')).toBe('Ground black pepper');
    expect(shop('1 cup whole milk')).toBe('Whole milk');
    expect(shop('1 cup frozen peas')).toBe('Frozen peas');
    expect(shop('2 cups baby spinach')).toBe('Baby spinach');
    expect(shop('1 tsp hot sauce')).toBe('Hot sauce');
    expect(shop('Salt and pepper')).toBe('Salt and pepper');
  });

  it('a name made only of dropped words is kept as written', () => {
    expect(ShoppingName.of('large')).toBe('Large');
  });
});

describe('ProductLink', () => {
  it('an ingredient links to the product it means, in stock or not', () => {
    const onion = product('onion');
    const pepper = product('pepper', { name: 'Black pepper' });
    const products = [onion, pepper, product('milk', { name: 'Whole milk' })];
    expect(ProductLink.find('Onion', products)).toBe(onion);
    expect(ProductLink.find('Onions', products)).toBe(onion);
    expect(ProductLink.find('Ground black pepper', products)).toBe(pepper);
    expect(ProductLink.find('Milk', products)).toBeNull(); // a product with more words is something else
  });

  it('no link when it would be a guess', () => {
    const products = [product('pepper', { name: 'Pepper' }), product('green', { name: 'Green onions' })];
    expect(ProductLink.find('Red bell pepper', products)).toBeNull(); // two more words is another product
    expect(ProductLink.find('Onion', products)).toBeNull(); // green onions are not onions
    expect(ProductLink.find('Soy sauce', products)).toBeNull();
  });

  it('the product with the most shared words wins', () => {
    const pepper = product('pepper', { name: 'Pepper' });
    const bell = product('bell', { name: 'Bell pepper' });
    expect(ProductLink.find('Green bell pepper', [pepper, bell])).toBe(bell);
  });

  it('a name the household taught a product wins', () => {
    const greenOnions = { ...product('green', { name: 'Green onions' }), aliases: ['scallion'] };
    const products = [product('onion', { name: 'Onion' }), greenOnions];
    expect(ProductLink.find('Scallions', products)).toBe(greenOnions);
    expect(ProductLink.find('scallion', products)).toBe(greenOnions);
  });
});
