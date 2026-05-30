import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatDuration(seconds: number | null): string {
  if (!seconds) return '-';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m ${s}s`;
}

export function formatDistance(meters: number | null): string {
  if (!meters) return '-';
  if (meters >= 1000) return `${(meters / 1000).toFixed(2)} km`;
  return `${meters} m`;
}

export function formatDate(dateStr: string | null): string {
  if (!dateStr) return '-';
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    month: 'short',
    day: 'numeric',
  });
}

export function formatDateTime(dateStr: string | null): string {
  if (!dateStr) return '-';
  // LocalDateTime string: "2026-05-02T09:14:31" — avoid Date() timezone conversion
  const clean = dateStr.slice(0, 16).replace('T', ' ');
  const [ymd, hm] = clean.split(' ');
  if (!ymd || !hm) return '-';
  return `${ymd} ${hm}`;
}

export function formatPace(secondsPerKm: number | null): string {
  if (!secondsPerKm) return '-';
  const m = Math.floor(secondsPerKm / 60);
  const s = Math.floor(secondsPerKm % 60);
  return `${m}:${s.toString().padStart(2, '0')} /km`;
}

export function formatLapCopyText(
  activity: {
    activityName: string;
    startTime: string;
    distanceMeters: number | null;
    durationSeconds: number;
    averagePaceSeconds: number | null;
    averageHeartRate: number | null;
    calories: number | null;
  },
  laps: Array<{
    lapIndex: number;
    distanceMeters: number;
    durationSeconds: number;
    averagePaceSeconds: number | null;
    averageHeartRate: number | null;
    maxHeartRate: number | null;
  }>
): string {
  const date = formatDateTime(activity.startTime);
  const dist = formatDistance(activity.distanceMeters);
  const dur = formatDuration(activity.durationSeconds);
  const pace = formatPace(activity.averagePaceSeconds);
  const hr = activity.averageHeartRate ? `${activity.averageHeartRate} bpm` : '-';
  const cal = activity.calories ? `${activity.calories} kcal` : '-';

  let text = `🏃 ${activity.activityName} — ${date}\n`;
  text += `📍 Total: ${dist} | ${dur} | ${pace} | Avg HR ${hr} | ${cal}\n\n`;
  text += `Splits:\n`;

  for (const lap of laps) {
    const lDist = formatDistance(lap.distanceMeters);
    const lDur = formatDuration(lap.durationSeconds);
    const lPace = formatPace(lap.averagePaceSeconds);
    const lAvgHr = lap.averageHeartRate ? `${lap.averageHeartRate}bpm` : '-';
    const lMaxHr = lap.maxHeartRate ? `${lap.maxHeartRate}bpm` : '-';
    text += `#${lap.lapIndex}  ${lDist}  ${lDur}  ${lPace}  Avg ${lAvgHr}  Max ${lMaxHr}\n`;
  }

  return text;
}
