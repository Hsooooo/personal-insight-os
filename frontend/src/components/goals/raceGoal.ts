import type { Goal } from '@/types';
import { todayKst } from '@/lib/utils';

export const GOAL_TEMPLATES = [
  { value: 'RACE', label: '레이스', unit: 's', placeholder: '1:35:00' },
  { value: 'WEEKLY_RUN_DISTANCE', label: '주간 러닝 거리', unit: 'km', placeholder: '40' },
  { value: 'WEEKLY_ACTIVITY_COUNT', label: '주간 운동 횟수', unit: '회', placeholder: '4' },
  { value: 'SLEEP_HOURS', label: '평균 수면 시간', unit: 'h', placeholder: '7.5' },
  { value: 'WEIGHT_KG', label: '체중', unit: 'kg', placeholder: '70' },
] as const;

export const RACE_DISTANCES = [
  { value: '5', label: '5K' },
  { value: '10', label: '10K' },
  { value: '21.0975', label: '하프 (21.1km)' },
  { value: '42.195', label: '풀코스 (42.195km)' },
] as const;

/** "1:35" → 5700, "1:35:30" → 5730 (시:분 또는 시:분:초). 잘못된 형식은 null */
export function parseRaceTime(text: string): number | null {
  const parts = text.trim().split(':');
  if (parts.length < 2 || parts.length > 3 || parts.some((p) => !/^\d+$/.test(p))) return null;
  const [h, m, s = 0] = parts.map(Number);
  if (m >= 60 || s >= 60) return null;
  const total = h * 3600 + m * 60 + s;
  return total > 0 ? total : null;
}

/** 5730 → "1:35:30", 1350 → "22:30" */
export function formatRaceTime(seconds: number | null | undefined): string {
  if (seconds == null || !Number.isFinite(Number(seconds))) return '-';
  const total = Math.round(Number(seconds));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const mm = String(m).padStart(2, '0');
  const ss = String(s).padStart(2, '0');
  return h > 0 ? `${h}:${mm}:${ss}` : `${m}:${ss}`;
}

export function raceDistanceLabel(km: number | null | undefined): string {
  if (km == null) return '거리 미설정';
  const preset = RACE_DISTANCES.find((d) => Number(d.value) === Number(km));
  return preset ? preset.label.split(' ')[0] : `${Number(km)}km`;
}

/** 대회일까지 남은 일수 (KST). 지난 대회는 음수 */
export function daysUntil(ymd: string | null | undefined, today: string = todayKst()): number | null {
  if (!ymd) return null;
  const diff = Date.parse(`${ymd}T00:00:00Z`) - Date.parse(`${today}T00:00:00Z`);
  return Number.isFinite(diff) ? Math.round(diff / 86_400_000) : null;
}

export function dDayLabel(days: number | null): string | null {
  if (days == null) return null;
  if (days === 0) return 'D-Day';
  return days > 0 ? `D-${days}` : `D+${-days}`;
}

/** 카드에 쓰는 "현재 / 목표" 문구. RACE는 예상 기록 / 목표 기록 */
export function goalProgressText(goal: Goal): string {
  if (goal.goalType === 'RACE') {
    return `예상 ${formatRaceTime(goal.currentValue)} / 목표 ${formatRaceTime(goal.targetValue)}`;
  }
  const unit = goal.targetUnit ? ` ${goal.targetUnit}` : '';
  return `${goal.currentValue ?? '-'}${unit} / ${goal.targetValue}${unit}`;
}
