// The cases both apps must agree on (after v1, B21). Android's ParityCasesTest reads the same file; a case that
// fails here or there is a difference between the two apps.
import { describe, expect, it } from 'vitest';
import { guessAisle } from './grocery/aisles';
import { parseQuickAdd } from './grocery/service';
import { Amounts } from './pantry/amounts';
import { product } from './pantry/fixtures';
import { NameKey } from './pantry/rules';
import { TEST_WORDS } from './recipes/fixtures';
import { IngredientParser, Scaling } from './recipes/parser';
import { ProductLink } from './recipes/shopping';
import cases from './parity-cases.json';

const differ = <T>(rows: T[], wrong: (row: T) => string | null) => rows.map(wrong).filter((w) => w !== null);

describe('parity with Android', () => {
  it('name keys and clean names', () => {
    expect(
      differ(cases.nameKey, (c) => {
        const got = [NameKey.of(c.name), NameKey.clean(c.name)];
        return got[0] === c.key && got[1] === c.clean ? null : `${JSON.stringify(c.name)} -> ${JSON.stringify(got)}`;
      }),
    ).toEqual([]);
  });

  it('quick add', () => {
    expect(
      differ(cases.quickAdd, (c) => {
        const p = parseQuickAdd(c.text);
        const same = p.name === c.name && p.quantity === c.quantity && p.unit === c.unit;
        return same ? null : `${JSON.stringify(c.text)} -> ${JSON.stringify(p)}`;
      }),
    ).toEqual([]);
  });

  it('ingredient lines', () => {
    expect(
      differ(cases.ingredient, (c) => {
        const p = IngredientParser.parse(c.raw);
        const same = p.qty === c.qty && p.unit === c.unit && p.name === c.name;
        return same ? null : `${JSON.stringify(c.raw)} -> ${p.qty} ${p.unit} ${p.name}`;
      }),
    ).toEqual([]);
  });

  it('scaled lines', () => {
    expect(
      differ(cases.scale, (c) => {
        const got = Scaling.line(c.raw, c.factor, TEST_WORDS.pinch);
        return got === c.line ? null : `${JSON.stringify(c.raw)} × ${c.factor} -> ${JSON.stringify(got)}`;
      }),
    ).toEqual([]);
  });

  it('ingredient links', () => {
    expect(
      differ(cases.link, (c) => {
        const linked = ProductLink.find(c.line, [product('p', { name: c.product })]) !== null;
        return linked === c.linked ? null : `${c.line} / ${c.product} -> ${linked}`;
      }),
    ).toEqual([]);
  });

  it('aisle guesses', () => {
    expect(
      differ(cases.aisle, (c) => {
        const got = guessAisle(c.name);
        return got === c.aisle ? null : `${c.name} -> ${got}`;
      }),
    ).toEqual([]);
  });

  it('typed amounts', () => {
    expect(
      differ(cases.amountParse, (c) => {
        const got = Amounts.parse(c.text);
        return got === c.value ? null : `${JSON.stringify(c.text)} -> ${got}`;
      }),
    ).toEqual([]);
  });

  it('shown amounts', () => {
    expect(
      differ(cases.amountFormat, (c) => {
        const got = Amounts.format(c.value);
        return got === c.text ? null : `${c.value} -> ${got}`;
      }),
    ).toEqual([]);
  });
});
