import { describe, expect, it } from 'vitest';
import {
  applyExerciseMappings,
  fuzzyMatchHevyName,
  getUnmappedExercises,
  parseHevyText,
  removeHevyMapping,
  setHevyMapping,
  toWeightTrainingRequest,
} from './hevyParser';

const SAMPLE = `저녁 상체 운동
화요일, 5월 26, 2026 9:04오후

벤치 프레스 (바벨)
세트 1: 60 kg x 10
세트 2: 62.5 kg x 8

플랭크
세트 1: 45s

턱걸이
세트 1: 6 회

@hevyapp
https://hevy.com/workout/abc`;

describe('parseHevyText', () => {
  it('parses name, PM start time and exercises with all set types', () => {
    const w = parseHevyText(SAMPLE);
    expect(w).not.toBeNull();
    expect(w?.activityName).toBe('저녁 상체 운동');
    expect(w?.startTime).toBe('2026-05-26T21:04:00');
    expect(w?.exercises.map((e) => e.hevyName)).toEqual(['벤치 프레스 (바벨)', '플랭크', '턱걸이']);
    expect(w?.exercises[0].sets).toEqual([
      { weightKg: 60, reps: 10 },
      { weightKg: 62.5, reps: 8 },
    ]);
    expect(w?.exercises[1].sets).toEqual([{ durationSeconds: 45 }]);
    expect(w?.exercises[2].sets).toEqual([{ reps: 6 }]);
  });

  it('handles 12 AM/PM edge cases', () => {
    expect(parseHevyText('A\n월요일, 1월 5, 2026 12:30오전\n스쿼트\n세트 1: 6 회')?.startTime).toBe('2026-01-05T00:30:00');
    expect(parseHevyText('A\n월요일, 1월 5, 2026 12:30오후\n스쿼트\n세트 1: 6 회')?.startTime).toBe('2026-01-05T12:30:00');
  });

  it('returns null for too-short or exercise-less input', () => {
    expect(parseHevyText('only one line')).toBeNull();
    expect(parseHevyText('a\nb\n@hevyapp')).toBeNull();
  });
});

describe('fuzzyMatchHevyName / mappings', () => {
  const names = ['벤치프레스', '스쿼트', '데드리프트'];

  it('matches after normalization (spaces, parentheses)', () => {
    expect(fuzzyMatchHevyName('벤치 프레스', names)).toEqual({ bestMatch: '벤치프레스', score: 1 });
    expect(fuzzyMatchHevyName('벤치 프레스 (바벨)', names).bestMatch).toBe('벤치프레스');
  });

  it('returns null when nothing is similar enough', () => {
    expect(fuzzyMatchHevyName('수영', names)).toEqual({ bestMatch: null, score: 0 });
  });

  it('prefers stored mappings and can remove them', () => {
    setHevyMapping('Bench Press (Barbell)', '벤치프레스');
    expect(fuzzyMatchHevyName('Bench Press (Barbell)', names)).toEqual({ bestMatch: '벤치프레스', score: 1 });
    removeHevyMapping('Bench Press (Barbell)');
    expect(fuzzyMatchHevyName('Bench Press (Barbell)', names).bestMatch).toBeNull();
  });

  it('applyExerciseMappings + getUnmappedExercises', () => {
    const w = parseHevyText(SAMPLE);
    if (!w) throw new Error('parse failed');
    setHevyMapping('플랭크', '코어 플랭크');
    const mapped = applyExerciseMappings(w, names);
    expect(mapped.exercises.map((e) => e.mappedName)).toEqual(['벤치프레스', '코어 플랭크', '턱걸이']);
    expect(getUnmappedExercises(w).map((e) => e.hevyName)).toEqual(['벤치 프레스 (바벨)', '턱걸이']);
  });
});

describe('toWeightTrainingRequest', () => {
  it('sums set durations and passes options', () => {
    const w = parseHevyText(SAMPLE);
    if (!w) throw new Error('parse failed');
    const req = toWeightTrainingRequest(w, { bodyPart: '상체', avgHr: 120, calories: 300 });
    expect(req).toMatchObject({
      activityName: '저녁 상체 운동',
      startTime: '2026-05-26T21:04:00',
      durationSeconds: 45,
      bodyPart: '상체',
      averageHeartRate: 120,
      calories: 300,
    });
    expect(req.exercises).toHaveLength(3);
  });

  it('leaves duration undefined when no timed sets', () => {
    const w = parseHevyText('A\n월요일, 1월 5, 2026 9:00오전\n스쿼트\n세트 1: 100 kg x 5');
    if (!w) throw new Error('parse failed');
    expect(toWeightTrainingRequest(w).durationSeconds).toBeUndefined();
  });
});
