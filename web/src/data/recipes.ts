// The recipe box (feature/recipes/data in the app): a changes-only listener and the writes of RecipeChange.
import {
  collection,
  doc,
  serverTimestamp,
  Timestamp,
  type DocumentSnapshot,
  type Firestore,
  type WriteBatch,
} from 'firebase/firestore';
import { distinctUntilChanged, map, switchMap, type Observable } from 'rxjs';
import {
  importMethodOf,
  isRecipeChange,
  type Ingredient,
  type Recipe,
  type RecipeBoxData,
  type RecipeContent,
  type RecipeRepository,
  type Step,
} from '../domain/recipes/models';
import type { Actor, HouseholdChange } from '../domain/write';
import { liveDocuments, Stamps, Tombstones, type SyncLog } from './delta-sync';
import type { FirebaseSdk } from './firebase';
import { activity } from './pantry';
import type { ChangeMapper } from './writer';

export const recipes = (hid: string) => `households/${hid}/recipes`;

const text = (value: unknown): string | null => (typeof value === 'string' ? value : null);
const num = (value: unknown): number | null => (typeof value === 'number' ? value : null);
const int = (value: unknown): number | null => (typeof value === 'number' ? Math.trunc(value) : null);
const maps = (value: unknown): Record<string, unknown>[] =>
  Array.isArray(value) ? value.filter((v): v is Record<string, unknown> => typeof v === 'object' && v !== null) : [];

// New fields are optional on read with defaults (DESIGN 8.1).
export function toRecipe(s: DocumentSnapshot): Recipe {
  return {
    id: s.id,
    content: {
      title: text(s.get('title')) ?? '',
      sourceUrl: text(s.get('sourceUrl')),
      sourceName: text(s.get('sourceName')),
      imageUrl: text(s.get('imageUrl')),
      description: text(s.get('description')),
      servings: int(s.get('servings')),
      servingsText: text(s.get('servingsText')),
      prepMinutes: int(s.get('prepMinutes')),
      cookMinutes: int(s.get('cookMinutes')),
      totalMinutes: int(s.get('totalMinutes')),
      ingredients: maps(s.get('ingredients')).flatMap((m): Ingredient[] => {
        const raw = text(m.raw);
        return raw === null
          ? []
          : [{ raw, qty: num(m.qty), unit: text(m.unit), name: text(m.name), group: text(m.group) }];
      }),
      steps: maps(s.get('steps')).flatMap((m): Step[] => {
        const t = text(m.text);
        return t === null ? [] : [{ text: t, group: text(m.group) }];
      }),
      notes: text(s.get('notes')),
      tags: ((s.get('tags') as unknown[] | null) ?? []).filter((t): t is string => typeof t === 'string'),
      importMethod: importMethodOf(s.get('importMethod')),
    },
    favorite: s.get('favorite') === true,
    rating: int(s.get('rating')),
    createdAt: (s.get('createdAt', { serverTimestamps: 'estimate' }) as Timestamp | null)?.toMillis?.() ?? null,
    createdBy: text(s.get('createdBy')),
  };
}

export class FirestoreRecipeRepository implements RecipeRepository {
  constructor(
    private readonly sdk: FirebaseSdk,
    private readonly log: SyncLog,
  ) {}

  // Changes-only listeners (DESIGN 9.8); fromCache turns false once the server confirms (offline banner).
  observe(householdId: string): Observable<RecipeBoxData> {
    return this.sdk.instance$.pipe(
      switchMap((db) => liveDocuments(db, collection(db, recipes(householdId)), this.log)),
      map((live) => ({ recipes: live.documents.map(toRecipe), fromCache: live.fromCache })),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
    );
  }
}

/** Every content field written explicitly, nulls included (DESIGN 8.1). */
const contentFields = (c: RecipeContent) => ({
  title: c.title,
  sourceUrl: c.sourceUrl,
  sourceName: c.sourceName,
  imageUrl: c.imageUrl,
  description: c.description,
  servings: c.servings,
  servingsText: c.servingsText,
  prepMinutes: c.prepMinutes,
  cookMinutes: c.cookMinutes,
  totalMinutes: c.totalMinutes,
  ingredients: c.ingredients.map((i) => ({ raw: i.raw, qty: i.qty, unit: i.unit, name: i.name, group: i.group })),
  steps: c.steps.map((s) => ({ text: s.text, group: s.group })),
  notes: c.notes,
  tags: c.tags,
  importMethod: c.importMethod,
});

/** Firestore writes for RecipeChange (RecipeChangeMapper). */
export const recipeMapper: ChangeMapper = {
  write(batch: WriteBatch, db: Firestore, hid: string, actor: Actor, change: HouseholdChange): boolean {
    if (!isRecipeChange(change)) return false;
    const recipe = (id: string) => doc(db, recipes(hid), id);
    const stamp = Stamps.updated(actor);
    switch (change.type) {
      case 'recipe.restore':
        batch.set(recipe(change.recipe.id), {
          ...contentFields(change.recipe.content),
          ...stamp,
          favorite: change.recipe.favorite,
          rating: change.recipe.rating,
          createdAt:
            change.recipe.createdAt !== null ? Timestamp.fromMillis(change.recipe.createdAt) : serverTimestamp(),
          createdBy: change.recipe.createdBy ?? actor.uid,
        });
        break;
      case 'recipe.update':
        batch.update(recipe(change.recipeId), { ...contentFields(change.content), ...stamp });
        break;
      case 'recipe.setFavorite':
        batch.update(recipe(change.recipeId), { favorite: change.favorite, ...stamp });
        break;
      // A tombstone, so other phones' change listeners see the deletion (DESIGN 9.8).
      case 'recipe.delete':
        batch.set(recipe(change.recipeId), Tombstones.fields(actor), { merge: true });
        break;
      case 'recipe.logCooked':
        batch.set(doc(db, activity(hid), change.eventId), {
          type: 'recipe_cooked',
          actorUid: actor.uid,
          actorName: actor.name,
          recipeId: change.recipeId,
          params: { name: change.title },
          createdAt: serverTimestamp(),
        });
        break;
    }
    return true;
  },
};
