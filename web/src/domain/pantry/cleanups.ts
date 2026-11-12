// Which empty batches this page is cleaning up or may not clean up (DESIGN 8.5, after v1, review REL-11;
// EmptyBatchCleanups in the app): one whose clean-up is waiting for the server isn't written again, and one the server
// refused isn't tried again until the app is reopened. Another phone, or a fixed batch (its quantity back above 0),
// still cleans it up normally.
export class EmptyBatchCleanups {
  private readonly waiting = new Set<string>();
  private readonly refused = new Set<string>();

  /** The batch IDs of [ids] to clean up now, marked as waiting. */
  toTry(ids: readonly string[]): string[] {
    const now = ids.filter((id) => !this.waiting.has(id) && !this.refused.has(id));
    for (const id of now) this.waiting.add(id);
    return now;
  }

  /** The server answered the clean-up of [ids]; [refused] remembers them for this page. */
  answered(ids: readonly string[], refused: boolean) {
    for (const id of ids) {
      this.waiting.delete(id);
      if (refused) this.refused.add(id);
    }
  }
}
