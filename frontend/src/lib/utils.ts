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

export function formatSleepDurationShort(seconds: number | null): string {
  if (!seconds) return '-';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h > 0 && m > 0) return `${h}h${m}m`;
  if (h > 0) return `${h}h`;
  return `${m}m`;
}

export function formatWeeklyReport(
  healthMetrics: Array<{
    metricDate: string;
    restingHeartRate: number;
    stressAvg: number;
    steps: number;
    caloriesTotal: number;
  }>,
  sleepSessions: Array<{
    sleepDate: string;
    totalSleepSeconds: number;
    deepSleepSeconds: number;
    lightSleepSeconds: number;
    remSleepSeconds: number;
    awakeSeconds: number;
    sleepScore: number;
  }>,
  activities: Array<{
    activityType: string;
    activityName: string;
    startTime: string;
    distanceMeters: number | null;
    durationSeconds: number;
    averagePaceSeconds: number | null;
    averageHeartRate: number | null;
    calories: number | null;
    weightTrainingDetail?: {
      bodyPart?: string;
      exercises?: Array<{
        name: string;
        sets: Array<{
          reps?: number;
          weightKg?: number;
          durationSeconds?: number;
        }>;
      }>;
    } | null;
  }>,
  startDate: string,
  endDate: string
): string {
  const fmtDate = (d: string) => d.slice(5); // MM-DD

  // Merge health and sleep by date
  const dateMap = new Map<string, { health?: typeof healthMetrics[0]; sleep?: typeof sleepSessions[0] }>();
  for (const h of healthMetrics) dateMap.set(h.metricDate, { ...dateMap.get(h.metricDate), health: h });
  for (const s of sleepSessions) dateMap.set(s.sleepDate, { ...dateMap.get(s.sleepDate), sleep: s });
  const sortedDates = Array.from(dateMap.keys()).sort();

  let md = `# Weekly Report: ${startDate} ~ ${endDate}\n\n`;

  md += `## Daily Health & Sleep\n`;
  md += `| Date | RHR | Stress | Steps | Calories | Sleep | Deep | Light | REM | Awake | Score |\n`;
  md += `|------|-----|--------|-------|----------|-------|-------|-------|-----|-------|-------|\n`;

  for (const date of sortedDates) {
    const { health, sleep } = dateMap.get(date)!;
    const rhr = health?.restingHeartRate ?? '-';
    const stress = health?.stressAvg ?? '-';
    const steps = health?.steps ?? '-';
    const cal = health?.caloriesTotal ?? '-';
    const total = sleep ? formatSleepDurationShort(sleep.totalSleepSeconds) : '-';
    const deep = sleep ? formatSleepDurationShort(sleep.deepSleepSeconds) : '-';
    const light = sleep ? formatSleepDurationShort(sleep.lightSleepSeconds) : '-';
    const rem = sleep ? formatSleepDurationShort(sleep.remSleepSeconds) : '-';
    const awake = sleep ? formatSleepDurationShort(sleep.awakeSeconds) : '-';
    const score = sleep?.sleepScore ?? '-';
    md += `| ${fmtDate(date)} | ${rhr} | ${stress} | ${steps} | ${cal} | ${total} | ${deep} | ${light} | ${rem} | ${awake} | ${score} |\n`;
  }

  md += `\n## Activities (${activities.length})\n`;
  if (activities.length === 0) {
    md += `_No activities this week._\n`;
  } else {
    md += `| Date | Type | Name | Distance | Duration | Pace | Avg HR | Calories |\n`;
    md += `|------|------|------|----------|----------|------|--------|----------|\n`;
    for (const a of activities) {
      const d = fmtDate(a.startTime);
      const type = a.activityType;
      const name = a.activityName;
      const dist = formatDistance(a.distanceMeters);
      const dur = formatDuration(a.durationSeconds);
      const pace = formatPace(a.averagePaceSeconds);
      const hr = a.averageHeartRate ? `${a.averageHeartRate}` : '-';
      const cal = a.calories ?? '-';
      md += `| ${d} | ${type} | ${name} | ${dist} | ${dur} | ${pace} | ${hr} | ${cal} |\n`;
    }

    // Weight training detail
    const weightActivities = activities.filter((a) => a.activityType === 'WEIGHT_TRAINING' && a.weightTrainingDetail);
    if (weightActivities.length > 0) {
      md += `\n## Weight Training Detail\n`;
      for (const a of weightActivities) {
        const d = fmtDate(a.startTime);
        const detail = a.weightTrainingDetail!;
        const bodyPart = detail.bodyPart ? `(${detail.bodyPart})` : '';
        md += `### ${d} ${a.activityName} ${bodyPart}\n`;
        if (detail.exercises) {
          for (const ex of detail.exercises) {
            const sets = ex.sets.map((s, i) => {
              if (s.weightKg && s.reps) return `${i + 1}set: ${s.reps}reps @ ${s.weightKg}kg`;
              if (s.reps) return `${i + 1}set: ${s.reps}reps`;
              if (s.durationSeconds) return `${i + 1}set: ${formatDuration(s.durationSeconds)}`;
              return `${i + 1}set`;
            }).join(' / ');
            md += `- ${ex.name}: ${sets}\n`;
          }
        }
      }
    }
  }

  return md;
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
