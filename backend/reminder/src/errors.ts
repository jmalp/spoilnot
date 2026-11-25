// Errors in public workflow logs (DESIGN 13.4). A Firestore error can hold the client, and with it the service
// account key, and its message can name a user or a full household path. So the entry points never print an error
// object or message: one fixed line with the error's code, which docs/RUNBOOK.md explains.
import { writeSync } from 'node:fs';

/** An error the jobs raise themselves; its code says what went wrong. */
export class JobError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'JobError';
    this.code = code;
  }
}

/** gRPC status names by number, as Firestore reports them. */
const GRPC = [
  'OK', 'CANCELLED', 'UNKNOWN', 'INVALID_ARGUMENT', 'DEADLINE_EXCEEDED', 'NOT_FOUND', 'ALREADY_EXISTS',
  'PERMISSION_DENIED', 'RESOURCE_EXHAUSTED', 'FAILED_PRECONDITION', 'ABORTED', 'OUT_OF_RANGE', 'UNIMPLEMENTED',
  'INTERNAL', 'UNAVAILABLE', 'DATA_LOSS', 'UNAUTHENTICATED',
];

/** Shapes of codes: Firebase's and the jobs' ('messaging/invalid-argument', 'wrong-project'), Node's, HTTP's. */
const CODE = /^(?:[a-z]+(?:-[a-z]+)*\/)?[a-z]+(?:-[a-z]+)*$|^[A-Z][A-Z0-9_]*$|^\d{1,4}$/;

/** The error's code (a gRPC status name, a Firebase, Node or job code), or "no code" for anything else. */
export function errorCode(error: unknown): string {
  const code = (error as { code?: unknown } | null | undefined)?.code;
  if (typeof code === 'number') return GRPC[code] ?? String(code);
  if (typeof code === 'string' && code.length <= 64 && CODE.test(code)) return code;
  return 'no code';
}

export function failureLine(job: string, error: unknown): string {
  return `${job} failed: ${errorCode(error)}. Details are not printed in public logs; see docs/RUNBOOK.md.`;
}

/**
 * Runs an entry point. Any failure, including one nobody handled, prints only failureLine and exits non-zero:
 * Node's own report of an unhandled error would print the whole object.
 */
export function runMain(job: string, main: () => Promise<void>): void {
  // Written synchronously, so the line is out before an exit.
  const fail = (error: unknown) => {
    writeSync(2, `${failureLine(job, error)}\n`);
    process.exitCode = 1;
  };
  const crash = (error: unknown) => {
    fail(error);
    process.exit(1);
  };
  process.on('uncaughtException', crash);
  process.on('unhandledRejection', crash);
  main().catch(fail);
}
