import { describe, expect, it } from 'vitest';
import type { Clock } from './time';
import { nextPrompt, UPDATE_CHECK_INTERVAL_MS, UpdateChecks, updateRequired } from './updates';

class FakeClock implements Clock {
  constructor(public time = Date.parse('2026-11-08T12:00:00Z')) {}
  now = () => this.time;
  advance(ms: number) {
    this.time += ms;
  }
}

const idle = { required: false, waiting: false };

describe('updateRequired', () => {
  it('requires an update only when this build is older than minWebBuild', () => {
    expect(updateRequired({ minWebBuild: 3 }, 2)).toBe(true);
    expect(updateRequired({ minWebBuild: 3 }, 3)).toBe(false);
    expect(updateRequired({ minWebBuild: 3 }, 4)).toBe(false);
  });

  it('never requires one without the document or the field', () => {
    expect(updateRequired(null, 1)).toBe(false);
    expect(updateRequired({ minWebBuild: 0 }, 1)).toBe(false);
  });
});

describe('UpdateChecks', () => {
  it('looks when the app first becomes visible without a registration on record', () => {
    expect(new UpdateChecks(new FakeClock()).shouldCheck(idle)).toBe(true);
  });

  it('looks at most once an hour after the browser looked', () => {
    const clock = new FakeClock();
    const checks = new UpdateChecks(clock);
    checks.checked();
    clock.advance(UPDATE_CHECK_INTERVAL_MS - 1);
    expect(checks.shouldCheck(idle)).toBe(false);
    clock.advance(1);
    expect(checks.shouldCheck(idle)).toBe(true);
    // That look counts: the next is an hour later.
    clock.advance(UPDATE_CHECK_INTERVAL_MS / 2);
    expect(checks.shouldCheck(idle)).toBe(false);
    clock.advance(UPDATE_CHECK_INTERVAL_MS / 2);
    expect(checks.shouldCheck(idle)).toBe(true);
  });

  it('a "no" does not move the hour along', () => {
    const clock = new FakeClock();
    const checks = new UpdateChecks(clock);
    checks.checked();
    for (let i = 0; i < 59; i++) {
      clock.advance(60_000);
      expect(checks.shouldCheck(idle)).toBe(false);
    }
    clock.advance(60_000);
    expect(checks.shouldCheck(idle)).toBe(true);
  });

  it('looks every time while an update is required and none is waiting', () => {
    const clock = new FakeClock();
    const checks = new UpdateChecks(clock);
    checks.checked();
    expect(checks.shouldCheck({ required: true, waiting: false })).toBe(true);
    clock.advance(1_000);
    expect(checks.shouldCheck({ required: true, waiting: false })).toBe(true);
  });

  it('does not look while a new version is already waiting', () => {
    const clock = new FakeClock();
    const checks = new UpdateChecks(clock);
    clock.advance(UPDATE_CHECK_INTERVAL_MS * 2);
    expect(checks.shouldCheck({ required: false, waiting: true })).toBe(false);
    expect(checks.shouldCheck({ required: true, waiting: true })).toBe(false);
  });

  it('treats a clock set back as an hour gone by', () => {
    const clock = new FakeClock();
    const checks = new UpdateChecks(clock);
    checks.checked();
    clock.advance(-60_000);
    expect(checks.shouldCheck(idle)).toBe(true);
  });
});

describe('nextPrompt', () => {
  it('shows nothing until a new version is waiting', () => {
    expect(nextPrompt(false, false, null)).toBeNull();
    expect(nextPrompt(false, true, null)).toBeNull();
  });

  it('offers a waiting version once', () => {
    expect(nextPrompt(true, false, null)).toBe('offer');
    expect(nextPrompt(true, false, 'offer')).toBeNull();
  });

  it('requires it when config/app asks, also after the offer, and only once', () => {
    expect(nextPrompt(true, true, null)).toBe('require');
    expect(nextPrompt(true, true, 'offer')).toBe('require');
    expect(nextPrompt(true, true, 'require')).toBeNull();
  });

  it('does not go back to an offer once required', () => {
    expect(nextPrompt(true, false, 'require')).toBeNull();
  });
});
