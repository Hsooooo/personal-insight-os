import type { WeightTrainingRequest } from '@/types';

const LS_MAPPINGS_KEY = 'hevy-exercise-mappings';

export interface ParsedHevySet {
  reps?: number;
  weightKg?: number;
  durationSeconds?: number;
}

export interface ParsedHevyExercise {
  hevyName: string;
  mappedName: string;
  sets: ParsedHevySet[];
}

export interface ParsedHevyWorkout {
  activityName: string;
  startTime: string;
  exercises: ParsedHevyExercise[];
}

/* ─── 정규화 ─── */
function normalize(str: string): string {
  return str
    .toLowerCase()
    .replace(/[\s\(\)\[\]\-\/\.]/g, '')
    .replace(/(을|를|이|가|은|는)$/g, '');
}

function ngrams(str: string, n: number): string[] {
  const grams: string[] = [];
  for (let i = 0; i <= str.length - n; i++) {
    grams.push(str.slice(i, i + n));
  }
  return grams;
}

function jaccard(a: string, b: string): number {
  const ga = new Set(ngrams(a, 2));
  const gb = new Set(ngrams(b, 2));
  const inter = new Set([...ga].filter((x) => gb.has(x)));
  return inter.size / (ga.size + gb.size - inter.size);
}

/* ─── localStorage 매핑 ─── */
function getMappings(): Record<string, string> {
  try {
    const raw = localStorage.getItem(LS_MAPPINGS_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

function saveMappings(mappings: Record<string, string>) {
  localStorage.setItem(LS_MAPPINGS_KEY, JSON.stringify(mappings));
}

export function setHevyMapping(hevyName: string, mappedName: string) {
  const m = getMappings();
  m[hevyName] = mappedName;
  saveMappings(m);
}

export function removeHevyMapping(hevyName: string) {
  const m = getMappings();
  delete m[hevyName];
  saveMappings(m);
}

/* ─── Fuzzy 매칭 ─── */
export function fuzzyMatchHevyName(
  hevyName: string,
  exerciseNames: string[]
): { bestMatch: string | null; score: number } {
  const mappings = getMappings();
  if (mappings[hevyName]) {
    return { bestMatch: mappings[hevyName], score: 1 };
  }

  const normHevy = normalize(hevyName);
  let best: string | null = null;
  let bestScore = 0;

  for (const ex of exerciseNames) {
    const normEx = normalize(ex);
    if (!normEx) continue;

    // 정확 일치
    if (normHevy === normEx) return { bestMatch: ex, score: 1 };

    // 포함 일치
    if (normHevy.includes(normEx) || normEx.includes(normHevy)) {
      const score = 0.8;
      if (score > bestScore) {
        bestScore = score;
        best = ex;
      }
      continue;
    }

    // Jaccard
    const sim = jaccard(normHevy, normEx);
    if (sim > bestScore) {
      bestScore = sim;
      best = ex;
    }
  }

  return bestScore >= 0.4 ? { bestMatch: best, score: bestScore } : { bestMatch: null, score: 0 };
}

/* ─── 날짜 파싱 ─── */
function parseHevyDateTime(line: string): string | null {
  // 예: "화요일, 5월 26, 2026 9:04오후"
  const match = line.match(
    /(\d{1,2})월\s+(\d{1,2}),\s+(\d{4})\s+(\d{1,2}):(\d{2})(오전|오후)/
  );
  if (!match) return null;
  const [, month, day, year, hour, min, ampm] = match;
  let h = parseInt(hour, 10);
  if (ampm === '오후' && h !== 12) h += 12;
  if (ampm === '오전' && h === 12) h = 0;
  const m = parseInt(min, 10);
  const mon = parseInt(month, 10).toString().padStart(2, '0');
  const d = parseInt(day, 10).toString().padStart(2, '0');
  const hs = h.toString().padStart(2, '0');
  const ms = m.toString().padStart(2, '0');
  return `${year}-${mon}-${d}T${hs}:${ms}:00`;
}

/* ─── 세트 파싱 ─── */
function parseSetLine(line: string): ParsedHevySet | null {
  const trimmed = line.trim();
  if (!trimmed.startsWith('세트')) return null;

  // 패턴 1: 10 kg x 15
  const kgMatch = trimmed.match(/kg\s*x\s*(\d+)/i);
  if (kgMatch) {
    const weightMatch = trimmed.match(/(\d+(?:\.\d+)?)\s*kg/i);
    return {
      weightKg: weightMatch ? parseFloat(weightMatch[1]) : undefined,
      reps: parseInt(kgMatch[1], 10),
    };
  }

  // 패턴 2: 45s / 45초
  const secMatch = trimmed.match(/(\d+)\s*(?:s|초)/i);
  if (secMatch) {
    return { durationSeconds: parseInt(secMatch[1], 10) };
  }

  // 패턴 3: 6 회
  const repMatch = trimmed.match(/(\d+)\s*회/);
  if (repMatch) {
    return { reps: parseInt(repMatch[1], 10) };
  }

  return null;
}

/* ─── 메인 파서 ─── */
export function parseHevyText(text: string): ParsedHevyWorkout | null {
  const lines = text.split('\n').map((l) => l.trim());
  if (lines.length < 3) return null;

  const activityName = lines[0];
  const startTime = parseHevyDateTime(lines[1]);

  const exercises: ParsedHevyExercise[] = [];
  let currentExercise: { name: string; sets: ParsedHevySet[] } | null = null;

  for (let i = 2; i < lines.length; i++) {
    const line = lines[i];

    // @hevyapp 이하 무시
    if (line.startsWith('@hevyapp') || line.startsWith('https://')) break;
    if (!line) {
      // 빈 줄이면 현재 운 종료
      if (currentExercise) {
        exercises.push({
          hevyName: currentExercise.name,
          mappedName: currentExercise.name,
          sets: currentExercise.sets,
        });
        currentExercise = null;
      }
      continue;
    }

    const setResult = parseSetLine(line);
    if (setResult) {
      if (currentExercise) {
        currentExercise.sets.push(setResult);
      }
    } else {
      // 세트 패턴이 아니면 운 이름
      if (currentExercise) {
        exercises.push({
          hevyName: currentExercise.name,
          mappedName: currentExercise.name,
          sets: currentExercise.sets,
        });
      }
      currentExercise = { name: line, sets: [] };
    }
  }

  // 마지막 운 처리
  if (currentExercise) {
    exercises.push({
      hevyName: currentExercise.name,
      mappedName: currentExercise.name,
      sets: currentExercise.sets,
    });
  }

  if (exercises.length === 0) return null;

  return {
    activityName,
    startTime: startTime || new Date().toISOString().slice(0, 19),
    exercises,
  };
}

/* ─── 매핑 적용 ─── */
export function applyExerciseMappings(
  workout: ParsedHevyWorkout,
  exerciseNames: string[]
): ParsedHevyWorkout {
  const mappings = getMappings();
  return {
    ...workout,
    exercises: workout.exercises.map((ex) => {
      const cached = mappings[ex.hevyName];
      if (cached) return { ...ex, mappedName: cached };

      const { bestMatch } = fuzzyMatchHevyName(ex.hevyName, exerciseNames);
      return { ...ex, mappedName: bestMatch || ex.hevyName };
    }),
  };
}

/* ─── WeightTrainingRequest 변환 ─── */
export function toWeightTrainingRequest(
  workout: ParsedHevyWorkout,
  options?: { bodyPart?: string; avgHr?: number; calories?: number }
): WeightTrainingRequest {
  const totalDuration = workout.exercises.reduce((sum, ex) => {
    return sum + ex.sets.reduce((s, set) => s + (set.durationSeconds || 0), 0);
  }, 0);

  return {
    activityName: workout.activityName,
    startTime: workout.startTime,
    durationSeconds: totalDuration || undefined,
    averageHeartRate: options?.avgHr,
    calories: options?.calories,
    bodyPart: options?.bodyPart,
    exercises: workout.exercises.map((ex) => ({
      name: ex.mappedName,
      sets: ex.sets,
    })),
  };
}

/* ─── 매핑 안 된 운 목록 ─── */
export function getUnmappedExercises(workout: ParsedHevyWorkout): ParsedHevyExercise[] {
  const mappings = getMappings();
  return workout.exercises.filter((ex) => !mappings[ex.hevyName]);
}
