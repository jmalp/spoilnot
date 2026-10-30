// Ported from IngredientParserTest and ScalingTest.
import { describe, expect, it } from 'vitest';
import { ingredient } from './models';
import { IngredientParser, Scaling } from './parser';

const parsed = (raw: string) => IngredientParser.parse(raw);

describe('IngredientParser', () => {
  it('integers, decimals and units', () => {
    expect(parsed('2 cups flour')).toEqual(ingredient('2 cups flour', { qty: 2, unit: 'cup', name: 'flour' }));
    expect(parsed('1.5 kg potatoes')).toEqual(
      ingredient('1.5 kg potatoes', { qty: 1.5, unit: 'kg', name: 'potatoes' }),
    );
    expect(parsed('.5 tsp salt')).toEqual(ingredient('.5 tsp salt', { qty: 0.5, unit: 'tsp', name: 'salt' }));
  });

  it('unicode fractions and mixed numbers', () => {
    expect(parsed('½ cup sugar').qty).toBeCloseTo(0.5);
    expect(parsed('1½ cups milk').qty).toBeCloseTo(1.5);
    expect(parsed('1 ½ cups milk').qty).toBeCloseTo(1.5);
    expect(parsed('1 1/2 cups milk').qty).toBeCloseTo(1.5);
    expect(parsed('3/4 tsp vanilla').qty).toBeCloseTo(0.75);
    expect(parsed('1⁄2 tsp salt').qty).toBeCloseTo(0.5);
    expect(parsed('1 1/2 cups milk').unit).toBe('cup');
  });

  it('ranges use the lower value', () => {
    expect(parsed('2-3 cloves garlic').qty).toBe(2);
    expect(parsed('2 – 3 cloves garlic').qty).toBe(2);
    expect(parsed('2 to 3 tbsp oil').qty).toBe(2);
    expect(parsed('2-3 cloves garlic').unit).toBe('clove');
    expect(IngredientParser.quantity('2-3 cloves garlic')!.max).toBe(3);
  });

  it('plurals, abbreviations and units glued to the number', () => {
    expect(parsed('2 Tbsp. olive oil').unit).toBe('tbsp');
    expect(parsed('2 Tbsp. olive oil').name).toBe('olive oil');
    expect(parsed('200g flour').unit).toBe('g');
    expect(parsed('2 lbs chicken').unit).toBe('lb');
    expect(parsed('8 oz. cream cheese').unit).toBe('oz');
    expect(parsed('1 package yeast').unit).toBe('package');
    expect(parsed('1 pinch nutmeg').unit).toBe('pinch');
    expect(parsed('1 l water').unit).toBe('l');
    expect(parsed('250 ml milk').unit).toBe('ml');
  });

  it('a word that starts like a unit is part of the name', () => {
    expect(parsed('2 garlic cloves')).toEqual(ingredient('2 garlic cloves', { qty: 2, name: 'garlic cloves' }));
    expect(parsed('3 large eggs')).toEqual(ingredient('3 large eggs', { qty: 3, name: 'large eggs' }));
    expect(parsed('2 carrots')).toEqual(ingredient('2 carrots', { qty: 2, name: 'carrots' }));
    expect(parsed('1 tomato')).toEqual(ingredient('1 tomato', { qty: 1, name: 'tomato' }));
  });

  it('the name drops text after the first comma and anything in parentheses', () => {
    expect(parsed('1 onion, finely chopped').name).toBe('onion');
    expect(parsed('2 tbsp butter (softened)').name).toBe('butter');
    expect(parsed('1 cup of flour').name).toBe('flour');
    // The can size sits between the quantity and the unit.
    const tomatoes = parsed('1 (14 ounce) can diced tomatoes, drained');
    expect(tomatoes).toMatchObject({ qty: 1, unit: 'can', name: 'diced tomatoes' });
  });

  it('lines without a leading quantity keep only raw', () => {
    expect(parsed('Salt and pepper to taste')).toEqual(ingredient('Salt and pepper to taste'));
    expect(parsed('A handful of basil')).toEqual(ingredient('A handful of basil'));
    expect(parsed('0 eggs').qty).toBeNull();
  });

  it('the group is kept', () => {
    expect(IngredientParser.parse('1 cup stock', 'Sauce').group).toBe('Sauce');
  });
});

describe('Scaling', () => {
  it('scales only the quantity and keeps the rest of the line', () => {
    expect(Scaling.line('1 ½ cups flour, sifted', 2)).toBe('3 cups flour, sifted');
    expect(Scaling.line('200g flour', 2)).toBe('400g flour');
    expect(Scaling.line('2-3 cloves garlic', 0.5)).toBe('1–1½ cloves garlic');
    expect(Scaling.line('Salt to taste', 3)).toBe('Salt to taste');
    expect(Scaling.line('2 cups milk', 1)).toBe('2 cups milk');
  });

  it('factor comes from the original and target servings', () => {
    expect(Scaling.factor(4, 6)).toBeCloseTo(1.5);
    expect(Scaling.factor(null, 6)).toBe(1);
    expect(Scaling.factor(4, null)).toBe(1);
  });

  it('quantities are shown as kitchen fractions', () => {
    expect(Scaling.formatQuantity(0.5)).toBe('½');
    expect(Scaling.formatQuantity(1 / 3)).toBe('⅓');
    expect(Scaling.formatQuantity(1.75)).toBe('1¾');
    expect(Scaling.formatQuantity(1.99)).toBe('2');
    expect(Scaling.formatQuantity(12.4)).toBe('12');
    expect(Scaling.formatQuantity(1.1)).toBe('1.1');
    expect(Scaling.formatQuantity(0.125)).toBe('⅛');
  });
});
