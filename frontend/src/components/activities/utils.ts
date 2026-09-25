export const TAG_PRESETS = ['5K / 레이스', '10K / 레이스', '하프 / 레이스', '풀 / 레이스'];

export const ACTIVITY_TYPES = [
  { value: '', label: '모든 타입' },
  { value: 'running', label: '러닝' },
  { value: 'cycling', label: '사이클' },
  { value: 'swimming', label: '수영' },
  { value: 'WEIGHT_TRAINING', label: '웨이트' },
  { value: 'other', label: '기타' },
];

export const BODY_PARTS = [
  { value: 'CHEST', label: '가슴' },
  { value: 'BACK', label: '등' },
  { value: 'LEGS', label: '하체' },
  { value: 'SHOULDER', label: '어깨' },
  { value: 'ARMS', label: '팔' },
  { value: 'CORE', label: '코어' },
];

export const TAG_OPTIONS = [
  { value: '', label: '모든 태그' },
  { value: '__none__', label: '태그 없음' },
  ...TAG_PRESETS.map((t) => ({ value: t, label: t })),
];

export const SORT_OPTIONS = [
  { value: 'startTime,desc', label: '최신순' },
  { value: 'startTime,asc', label: '오래된순' },
  { value: 'distance,desc', label: '거리 ↓' },
  { value: 'distance,asc', label: '거리 ↑' },
  { value: 'duration,desc', label: '시간 ↓' },
  { value: 'calories,desc', label: '칼로리 ↓' },
];

export function getTagColor(tag: string | null): string {
  if (!tag) return '';
  if (tag.includes('5K')) return 'bg-blue-100 text-blue-700 hover:bg-blue-100';
  if (tag.includes('10K')) return 'bg-indigo-100 text-indigo-700 hover:bg-indigo-100';
  if (tag.includes('하프')) return 'bg-purple-100 text-purple-700 hover:bg-purple-100';
  if (tag.includes('풀')) return 'bg-orange-100 text-orange-700 hover:bg-orange-100';
  return 'bg-slate-100 text-slate-700 hover:bg-slate-100';
}

export function isRunningType(type: string | null): boolean {
  if (!type) return false;
  const t = type.toLowerCase();
  return t === 'running' || t.includes('run') || t.includes('treadmill') || t.includes('track');
}

export interface ExerciseForm {
  name: string;
  sets: { reps: string; weightKg: string; durationSeconds: string }[];
}
