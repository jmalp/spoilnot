// What an open sheet holds, kept through a reload (after v1, REL-08 and REL-09 on the web; DESIGN 6.1, 18.3). iOS
// discards a page left in the background and loads it again when the person comes back, which on Android is the app
// being closed and its saved state restored. So the sheets that collect several choices (Add to pantry, Plan's Add
// meal and a meal's sheet) keep their draft in sessionStorage under `spoilnot.draft.<name>` while they are open:
// it belongs to this tab or Home Screen window only, goes when that closes, and is removed when the sheet closes,
// the screen leaves (a tab switch closes its sheets) or nobody is signed in.
import { useEffect, useState, type Dispatch, type SetStateAction } from 'react';

const PREFIX = 'spoilnot.draft.';

function defaultStorage(): Storage | null {
  try {
    return sessionStorage;
  } catch {
    return null;
  }
}

/** Drafts by name, as JSON; without storage (or when it is full) they last while the page does. */
export class DraftStore {
  constructor(private readonly storage: Storage | null = defaultStorage()) {}

  read(name: string): unknown {
    try {
      const raw = this.storage?.getItem(PREFIX + name);
      return raw == null ? null : (JSON.parse(raw) as unknown);
    } catch {
      return null;
    }
  }

  write(name: string, value: unknown): void {
    try {
      if (value === null) this.storage?.removeItem(PREFIX + name);
      else this.storage?.setItem(PREFIX + name, JSON.stringify(value));
    } catch {
      // Full or unavailable storage: the draft lasts while the page does.
    }
  }

  remove(name: string): void {
    this.write(name, null);
  }

  /** Every draft, when nobody is signed in any more. */
  clear(): void {
    try {
      const storage = this.storage;
      if (!storage) return;
      const names = Array.from({ length: storage.length }, (_, i) => storage.key(i)).filter(
        (k): k is string => k !== null && k.startsWith(PREFIX),
      );
      names.forEach((k) => storage.removeItem(k));
    } catch {
      // Nothing to clear.
    }
  }
}

export const drafts = new DraftStore();

/**
 * useState kept in [store] under [name] (rememberSaveable for a sheet): it starts from the saved draft when [parse]
 * accepts it, else from [initial]. null means nothing to keep (a closed sheet). The draft is removed when the
 * component leaves; a reload does not unmount, so it is still there for the page that comes back.
 */
export function useSessionDraft<T>(
  name: string,
  parse: (raw: unknown) => T | null,
  initial: T,
  store: DraftStore = drafts,
): [T, Dispatch<SetStateAction<T>>] {
  const [value, setValue] = useState<T>(() => parse(store.read(name)) ?? initial);
  useEffect(() => store.write(name, value), [store, name, value]);
  useEffect(() => () => store.remove(name), [store, name]);
  return [value, setValue];
}
