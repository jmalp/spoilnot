// The recipe box (feature/recipes/data in the app): a changes-only listener and the writes of RecipeChange.
import {
  collection,
  doc,
  serverTimestamp,
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
import { capTo, ReadCaps } from '../domain/caps';
import { int, mapEach, millis, num, records, str as text, strings } from './fields';

export const recipes = (hid: string) => `households/${hid}/recipes`;

// New fields are optional on read with defaults (DESIGN 8.1).
export function toRecipe(s: DocumentSnapshot): Recipe {
  return {
    id: s.id,
    content: {
      title: capTo(text(s.get('title')) ?? '', ReadCaps.RECIPE_TITLE),
      sourceUrl: text(s.get('sourceUrl')),
      sourceName: text(s.get('sourceName')),
      imageUrl: text(s.get('imageUrl')),
      description: text(s.get('description')),
      servings: int(s.get('servings')),
      servingsText: text(s.get('servingsText')),
      prepMinutes: int(s.get('prepMinutes')),
      cookMinutes: int(s.get('cookMinutes')),
      totalMinutes: int(s.get('totalMinutes')),
      ingredients: records(s.get('ingredients')).flatMap((m): Ingredient[] => {
        const raw = text(m.raw);
        return raw === null
          ? []
          : [{ raw, qty: num(m.qty), unit: text(m.unit), name: text(m.name), group: text(m.group) }];
      }),
      steps: records(s.get('steps')).flatMap((m): Step[] => {
        const t = text(m.text);
        return t === null ? [] : [{ text: t, group: text(m.group) }];
      }),
      notes: text(s.get('notes')),
      tags: strings(s.get('tags')),
      importMethod: importMethodOf(s.get('importMethod')),
    },
    favorite: s.get('favorite') === true,
    rating: int(s.get('rating')),
    createdAt: millis(s, 'createdAt'),
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
      map((live) => ({ recipes: mapEach(live.documents, toRecipe), fromCache: live.fromCache })),
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
      // Undo removes the tombstone and nothing else (DESIGN 8.6).
      case 'recipe.restore':
        batch.set(recipe(change.recipe.id), Tombstones.restore(actor), { merge: true });
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
