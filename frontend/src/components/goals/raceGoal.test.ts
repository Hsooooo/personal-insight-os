import { describe, expect, it } from 'vitest';
import { daysUntil, dDayLabel, formatRaceTime, goalProgressText, parseRaceTime, raceDistanceLabel } from './raceGoal';
import type { Goal } from '@/types';

describe('raceGoal', () => {
  it('parses H:MM and H:MM:SS', () => {
    expect(parseRaceTime('1:35')).toBe(5700);
    expect(parseRaceTime('3:30:00')).toBe(12600);
    expect(parseRaceTime('0:22:30')).toBe(1350);
    expect(parseRaceTime('1:75')).toBeNull();
    expect(parseRaceTime('abc')).toBeNull();
    expect(parseRaceTime('95')).toBeNull();
  });

  it('formats seconds', () => {
    expect(formatRaceTime(5730)).toBe('1:35:30');
    expect(formatRaceTime(1350)).toBe('22:30');
    expect(formatRaceTime(null)).toBe('-');
  });

  it('labels distances and D-day', () => {
    expect(raceDistanceLabel(21.0975)).toBe('하프');
    expect(raceDistanceLabel(30)).toBe('30km');
    expect(daysUntil('2027-03-21', '2026-11-15')).toBe(126);
    expect(dDayLabel(126)).toBe('D-126');
    expect(dDayLabel(0)).toBe('D-Day');
    expect(dDayLabel(-2)).toBe('D+2');
  });

  it('describes race progress with finish times', () => {
    const goal = { goalType: 'RACE', currentValue: 5559, targetValue: 5700 } as Goal;
    expect(goalProgressText(goal)).toBe('예상 1:32:39 / 목표 1:35:00');
  });
});
