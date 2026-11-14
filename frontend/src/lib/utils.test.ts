import { describe, expect, it } from 'vitest';
import {
  addDaysYmd,
  cn,
  formatDateTime,
  formatDistance,
  formatDuration,
  formatPace,
  formatSignedDelta,
  formatSleepDurationShort,
  isRunningType,
  mondayOf,
  weekdayLabel,
} from './utils';

describe('formatters', () => {
  it('formatDuration', () => {
    expect(formatDuration(null)).toBe('-');
    expect(formatDuration(0)).toBe('-');
    expect(formatDuration(59)).toBe('0m 59s');
    expect(formatDuration(125)).toBe('2m 5s');
    expect(formatDuration(3725)).toBe('1h 2m');
  });

  it('formatDistance', () => {
    expect(formatDistance(null)).toBe('-');
    expect(formatDistance(850)).toBe('850 m');
    expect(formatDistance(10000)).toBe('10.00 km');
    expect(formatDistance(5234)).toBe('5.23 km');
  });

  it('formatPace', () => {
    expect(formatPace(null)).toBe('-');
    expect(formatPace(305)).toBe('5:05 /km');
    expect(formatPace(299.9)).toBe('4:59 /km');
  });

  it('formatDateTime keeps local time without timezone conversion', () => {
    expect(formatDateTime('2026-05-02T09:14:31')).toBe('2026-05-02 09:14');
    expect(formatDateTime(null)).toBe('-');
    expect(formatDateTime('2026-05-02')).toBe('-');
  });

  it('formatSleepDurationShort', () => {
    expect(formatSleepDurationShort(null)).toBe('-');
    expect(formatSleepDurationShort(7 * 3600)).toBe('7h');
    expect(formatSleepDurationShort(7 * 3600 + 30 * 60)).toBe('7h30m');
    expect(formatSleepDurationShort(45 * 60)).toBe('45m');
  });

  it('formatSignedDelta', () => {
    const f = (v: number) => `${v}km`;
    expect(formatSignedDelta(3.4, f)).toBe('+3km');
    expect(formatSignedDelta(-2.6, f)).toBe('-3km');
    expect(formatSignedDelta(0.2, f)).toBe('±0');
  });
});

describe('date helpers', () => {
  it('addDaysYmd crosses month/year boundaries', () => {
    expect(addDaysYmd('2026-01-31', 1)).toBe('2026-02-01');
    expect(addDaysYmd('2026-01-01', -1)).toBe('2025-12-31');
  });

  it('weekdayLabel', () => {
    expect(weekdayLabel('2026-11-14')).toBe('토');
  });

  it('mondayOf returns the Monday of the week', () => {
    expect(mondayOf('2026-11-14')).toBe('2026-11-09'); // Saturday
    expect(mondayOf('2026-11-09')).toBe('2026-11-09'); // Monday
    expect(mondayOf('2026-11-15')).toBe('2026-11-09'); // Sunday
  });
});

describe('misc', () => {
  it('isRunningType', () => {
    expect(isRunningType('running')).toBe(true);
    expect(isRunningType('TREADMILL_RUNNING')).toBe(true);
    expect(isRunningType('track_running')).toBe(true);
    expect(isRunningType('cycling')).toBe(false);
    expect(isRunningType(null)).toBe(false);
  });

  it('cn merges tailwind classes', () => {
    const hidden = false;
    expect(cn('px-2', 'px-4', hidden && 'hidden')).toBe('px-4');
  });
});
