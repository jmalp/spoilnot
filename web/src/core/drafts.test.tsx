import { act, render } from '@testing-library/react';
import { StrictMode } from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { DraftStore, useSessionDraft } from './drafts';

const asText = (raw: unknown) => (typeof raw === 'string' ? raw : null);

describe('DraftStore', () => {
  beforeEach(() => sessionStorage.clear());

  it('keeps JSON by name and removes it with null', () => {
    const store = new DraftStore(sessionStorage);
    store.write('a', { cooks: ['u1'] });
    expect(sessionStorage.getItem('spoilnot.draft.a')).toBe('{"cooks":["u1"]}');
    expect(new DraftStore(sessionStorage).read('a')).toEqual({ cooks: ['u1'] });
    store.write('a', null);
    expect(store.read('a')).toBeNull();
  });

  it('reads damaged JSON as nothing, and works without storage', () => {
    sessionStorage.setItem('spoilnot.draft.bad', '{');
    expect(new DraftStore(sessionStorage).read('bad')).toBeNull();
    const none = new DraftStore(null);
    none.write('a', 1);
    expect(none.read('a')).toBeNull();
    none.clear();
  });

  it('clear removes only drafts', () => {
    sessionStorage.setItem('spoilnot.other', 'kept');
    const store = new DraftStore(sessionStorage);
    store.write('a', 1);
    store.write('b', 2);
    store.clear();
    expect(sessionStorage.length).toBe(1);
    expect(sessionStorage.getItem('spoilnot.other')).toBe('kept');
  });
});

describe('useSessionDraft', () => {
  beforeEach(() => sessionStorage.clear());

  let set: (value: string | null) => void = () => undefined;
  function Field({ store }: { store: DraftStore }) {
    const [value, setValue] = useSessionDraft('field', asText, null, store);
    set = setValue;
    return <span data-testid="value">{value ?? 'none'}</span>;
  }

  it('starts from a saved draft, saves changes and forgets the draft when it leaves (StrictMode too)', () => {
    const store = new DraftStore(sessionStorage);
    store.write('field', 'Pizza night');
    const page = render(
      <StrictMode>
        <Field store={store} />
      </StrictMode>,
    );
    expect(page.getByTestId('value').textContent).toBe('Pizza night');
    expect(store.read('field')).toBe('Pizza night');
    act(() => set('Tacos'));
    expect(store.read('field')).toBe('Tacos');
    act(() => set(null));
    expect(store.read('field')).toBeNull();
    act(() => set('Soup'));
    page.unmount();
    expect(store.read('field')).toBeNull();
  });

  it('ignores a draft its parser refuses', () => {
    const store = new DraftStore(sessionStorage);
    store.write('field', 42);
    const page = render(<Field store={store} />);
    expect(page.getByTestId('value').textContent).toBe('none');
    expect(store.read('field')).toBeNull();
  });
});
