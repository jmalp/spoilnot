import type { DocumentSnapshot } from 'firebase/firestore';
import { describe, expect, it } from 'vitest';
import { toAppConfig } from './config';

const snapshot = (data: Record<string, unknown> | null) =>
  ({ exists: () => data !== null, get: (field: string) => data?.[field] }) as unknown as DocumentSnapshot;

describe('toAppConfig', () => {
  it('reads minWebBuild', () => {
    expect(toAppConfig(snapshot({ minSupportedBuild: 10, minWebBuild: 2 }))).toEqual({ minWebBuild: 2 });
  });

  it('is null without the document', () => {
    expect(toAppConfig(snapshot(null))).toBeNull();
  });

  it('reads a missing field or one that is not a whole number as not set', () => {
    expect(toAppConfig(snapshot({ minSupportedBuild: 10 }))).toEqual({ minWebBuild: 0 });
    expect(toAppConfig(snapshot({ minWebBuild: '2' }))).toEqual({ minWebBuild: 0 });
    expect(toAppConfig(snapshot({ minWebBuild: 1.5 }))).toEqual({ minWebBuild: 0 });
  });
});
