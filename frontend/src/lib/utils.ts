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

export function isRunningType(type: string | null): boolean {
  if (!type) return false;
  const t = type.toLowerCase();
  return t === 'running' || t.includes('run') || t.includes('treadmill') || t.includes('track');
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
    weightKg: number | null;
    averageSpo2?: number | null;
    avgWakingRespiration?: number | null;
    bodyBatteryCharged?: number | null;
    vigorousIntensityMinutes?: number | null;
  }>,
  sleepSessions: Array<{
    sleepDate: string;
    totalSleepSeconds: number;
    deepSleepSeconds: number;
    lightSleepSeconds: number;
    remSleepSeconds: number;
    awakeSeconds: number;
    sleepScore: number;
    napSeconds?: number | null;
    sleepNeedMinutes?: number | null;
    hrvStatus?: string | null;
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
    laps?: Array<{
      lapIndex: number;
      distanceMeters: number;
      durationSeconds: number;
      averagePaceSeconds: number | null;
      averageHeartRate: number | null;
      maxHeartRate: number | null;
    }>;
    weatherTemperature?: number | null;
    weatherHumidity?: number | null;
    weatherWindSpeed?: number | null;
    weatherCondition?: string | null;
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
  md += `| Date | RHR | Stress | Steps | Calories | Weight | SpO2 | Resp | BB+ | Vigorous | Sleep | Deep | Light | REM | Awake | Nap | Need | HRV | Score |\n`;
  md += `|------|-----|--------|-------|----------|--------|------|------|-----|----------|-------|------|-------|-----|-------|-----|------|-----|-------|\n`;

  for (const date of sortedDates) {
    const { health, sleep } = dateMap.get(date)!;
    const rhr = health?.restingHeartRate ?? '-';
    const stress = health?.stressAvg ?? '-';
    const steps = health?.steps ?? '-';
    const cal = health?.caloriesTotal ?? '-';
    const weight = health?.weightKg ?? '-';
    const spo2 = health?.averageSpo2 ?? '-';
    const resp = health?.avgWakingRespiration ?? '-';
    const bbCharged = health?.bodyBatteryCharged ?? '-';
    const vigorous = health?.vigorousIntensityMinutes ?? '-';
    const total = sleep ? formatSleepDurationShort(sleep.totalSleepSeconds) : '-';
    const deep = sleep ? formatSleepDurationShort(sleep.deepSleepSeconds) : '-';
    const light = sleep ? formatSleepDurationShort(sleep.lightSleepSeconds) : '-';
    const rem = sleep ? formatSleepDurationShort(sleep.remSleepSeconds) : '-';
    const awake = sleep ? formatSleepDurationShort(sleep.awakeSeconds) : '-';
    const nap = sleep?.napSeconds ? formatSleepDurationShort(sleep.napSeconds) : '-';
    const need = sleep?.sleepNeedMinutes ? `${sleep.sleepNeedMinutes}m` : '-';
    const hrv = sleep?.hrvStatus ?? '-';
    const score = sleep?.sleepScore ?? '-';
    md += `| ${fmtDate(date)} | ${rhr} | ${stress} | ${steps} | ${cal} | ${weight} | ${spo2} | ${resp} | ${bbCharged} | ${vigorous} | ${total} | ${deep} | ${light} | ${rem} | ${awake} | ${nap} | ${need} | ${hrv} | ${score} |\n`;
  }

  md += `\n## Activities (${activities.length})\n`;
  if (activities.length === 0) {
    md += `_No activities this week._\n`;
  } else {
    for (const a of activities) {
      const d = formatDateTime(a.startTime);
      const dist = formatDistance(a.distanceMeters);
      const dur = formatDuration(a.durationSeconds);
      const pace = formatPace(a.averagePaceSeconds);
      const hr = a.averageHeartRate ? `${a.averageHeartRate} bpm` : '-';
      const cal = a.calories ? `${a.calories} kcal` : '-';

      const typeLower = a.activityType.toLowerCase();
      let emoji = '🏃';
      if (typeLower.includes('weight') || typeLower.includes('웨이트')) emoji = '💪';
      else if (typeLower.includes('cycle') || typeLower.includes('bike') || typeLower.includes('사이클')) emoji = '🚴';
      else if (typeLower.includes('swim') || typeLower.includes('수영')) emoji = '🏊';
      else if (typeLower.includes('walk') || typeLower.includes('걷기')) emoji = '🚶';
      else if (typeLower.includes('hik') || typeLower.includes('등산')) emoji = '🥾';
      else if (typeLower.includes('yoga') || typeLower.includes('요가')) emoji = '🧘';

      md += `\n${emoji} ${a.activityName} — ${d}\n`;
      md += `📍 Total: ${dist} | ${dur}${pace !== '-' ? ` | ${pace}` : ''}${hr !== '-' ? ` | Avg HR ${hr}` : ''}${cal !== '-' ? ` | ${cal}` : ''}\n`;

      if (a.weatherCondition) {
        const w = [];
        if (a.weatherTemperature !== null) w.push(`${a.weatherTemperature}°C`);
        if (a.weatherHumidity !== null) w.push(`습도 ${a.weatherHumidity}%`);
        if (a.weatherWindSpeed !== null) w.push(`바람 ${a.weatherWindSpeed}km/h`);
        md += `🌤️ ${a.weatherCondition}${w.length > 0 ? ' · ' + w.join(' · ') : ''}\n`;
      }

      // Running laps
      if (a.laps && a.laps.length > 0) {
        md += `\nSplits:\n`;
        for (const lap of a.laps) {
          const lDist = formatDistance(lap.distanceMeters);
          const lDur = formatDuration(lap.durationSeconds);
          const lPace = formatPace(lap.averagePaceSeconds);
          const lAvgHr = lap.averageHeartRate ? `${lap.averageHeartRate}bpm` : '-';
          const lMaxHr = lap.maxHeartRate ? `${lap.maxHeartRate}bpm` : '-';
          md += `#${lap.lapIndex}  ${lDist}  ${lDur}  ${lPace}  Avg ${lAvgHr}  Max ${lMaxHr}\n`;
        }
      }

      // Weight training detail
      if (a.weightTrainingDetail) {
        const detail = a.weightTrainingDetail;
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
    weatherTemperature?: number | null;
    weatherHumidity?: number | null;
    weatherWindSpeed?: number | null;
    weatherCondition?: string | null;
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
  text += `📍 Total: ${dist} | ${dur} | ${pace} | Avg HR ${hr} | ${cal}\n`;

  if (activity.weatherCondition) {
    const w = [];
    if (activity.weatherTemperature !== null && activity.weatherTemperature !== undefined) w.push(`${activity.weatherTemperature}°C`);
    if (activity.weatherHumidity !== null && activity.weatherHumidity !== undefined) w.push(`습도 ${activity.weatherHumidity}%`);
    if (activity.weatherWindSpeed !== null && activity.weatherWindSpeed !== undefined) w.push(`바람 ${activity.weatherWindSpeed}km/h`);
    text += `🌤️ ${activity.weatherCondition}${w.length > 0 ? ' · ' + w.join(' · ') : ''}\n`;
  }

  text += `\nSplits:\n`;

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

export function todayKst(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' });
}

function parseYmd(ymd: string): Date {
  const [year, month, day] = ymd.split('-').map(Number);
  return new Date(year, month - 1, day);
}

function toYmd(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function addDaysYmd(ymd: string, days: number): string {
  const date = parseYmd(ymd);
  date.setDate(date.getDate() + days);
  return toYmd(date);
}

const WEEKDAY_LABELS = ['월', '화', '수', '목', '금', '토', '일'];

// 0 = 월요일 ... 6 = 일요일
function weekdayIndex(ymd: string): number {
  return (parseYmd(ymd).getDay() + 6) % 7;
}

export function weekdayLabel(ymd: string): string {
  return WEEKDAY_LABELS[weekdayIndex(ymd)];
}

export function mondayOf(ymd: string): string {
  return addDaysYmd(ymd, -weekdayIndex(ymd));
}

export function formatSignedDelta(value: number, format: (v: number) => string): string {
  const rounded = Math.round(value);
  if (rounded === 0) return '±0';
  return `${rounded > 0 ? '+' : '-'}${format(Math.abs(rounded))}`;
}

export type ActivityCategory = 'running' | 'weight' | 'cycling' | 'swimming' | 'other';

export const ACTIVITY_CATEGORIES: Array<{ key: ActivityCategory; label: string; color: string }> = [
  { key: 'running', label: '러닝', color: '#10b981' },
  { key: 'weight', label: '웨이트', color: '#f59e0b' },
  { key: 'cycling', label: '사이클', color: '#0ea5e9' },
  { key: 'swimming', label: '수영', color: '#8b5cf6' },
  { key: 'other', label: '기타', color: '#64748b' },
];

export function activityCategory(type: string | null): ActivityCategory {
  if (!type) return 'other';
  const t = type.toLowerCase();
  if (t.includes('weight') || t.includes('strength') || t.includes('웨이트')) return 'weight';
  if (isRunningType(type)) return 'running';
  if (t.includes('cycl') || t.includes('bik') || t.includes('사이클')) return 'cycling';
  if (t.includes('swim') || t.includes('수영')) return 'swimming';
  return 'other';
}

export function activityCategoryMeta(type: string | null) {
  const key = activityCategory(type);
  return ACTIVITY_CATEGORIES.find((c) => c.key === key) || ACTIVITY_CATEGORIES[ACTIVITY_CATEGORIES.length - 1];
}

interface WeekActivityInput {
  activityType: string | null;
  startTime: string;
  durationSeconds: number;
  distanceMeters: number | null;
  calories: number | null;
  weightTrainingDetail?: { totalVolumeKg?: number | null } | null;
}

export interface ActivityWeekTotals {
  count: number;
  seconds: number;
  distanceMeters: number;
  calories: number;
}

export interface ActivityWeekSummary extends ActivityWeekTotals {
  start: string;
  end: string;
  activeDays: number;
  runCount: number;
  runDistanceMeters: number;
  avgRunPaceSeconds: number | null;
  weightSessions: number;
  weightVolumeKg: number;
  byCategory: Record<ActivityCategory, ActivityWeekTotals>;
  byDay: Array<ActivityWeekTotals & { date: string; weekday: string; secondsByCategory: Record<ActivityCategory, number> }>;
}

function emptyTotals(): ActivityWeekTotals {
  return { count: 0, seconds: 0, distanceMeters: 0, calories: 0 };
}

function addToTotals(totals: ActivityWeekTotals, activity: WeekActivityInput) {
  totals.count += 1;
  totals.seconds += activity.durationSeconds || 0;
  totals.distanceMeters += Number(activity.distanceMeters || 0);
  totals.calories += activity.calories || 0;
}

// 월~일 한 주 집계. startTime은 KST LocalDateTime 문자열이므로 앞 10자리로 날짜를 판단한다.
export function summarizeActivityWeek(activities: WeekActivityInput[], weekStart: string): ActivityWeekSummary {
  const end = addDaysYmd(weekStart, 6);
  const byCategory = Object.fromEntries(
    ACTIVITY_CATEGORIES.map((c) => [c.key, emptyTotals()])
  ) as Record<ActivityCategory, ActivityWeekTotals>;
  const byDay = Array.from({ length: 7 }, (_, i) => ({
    date: addDaysYmd(weekStart, i),
    weekday: WEEKDAY_LABELS[i],
    ...emptyTotals(),
    secondsByCategory: Object.fromEntries(ACTIVITY_CATEGORIES.map((c) => [c.key, 0])) as Record<ActivityCategory, number>,
  }));
  const summary: ActivityWeekSummary = {
    start: weekStart,
    end,
    ...emptyTotals(),
    activeDays: 0,
    runCount: 0,
    runDistanceMeters: 0,
    avgRunPaceSeconds: null,
    weightSessions: 0,
    weightVolumeKg: 0,
    byCategory,
    byDay,
  };

  let pacedRunSeconds = 0;
  for (const a of activities) {
    const day = byDay.find((d) => d.date === a.startTime.slice(0, 10));
    if (!day) continue;
    const category = activityCategory(a.activityType);
    addToTotals(summary, a);
    addToTotals(byCategory[category], a);
    addToTotals(day, a);
    day.secondsByCategory[category] += a.durationSeconds || 0;
    if (category === 'running') {
      summary.runCount += 1;
      if (a.distanceMeters) {
        summary.runDistanceMeters += Number(a.distanceMeters);
        pacedRunSeconds += a.durationSeconds || 0;
      }
    }
    if (category === 'weight') {
      summary.weightSessions += 1;
      summary.weightVolumeKg += Number(a.weightTrainingDetail?.totalVolumeKg || 0);
    }
  }

  summary.activeDays = byDay.filter((d) => d.count > 0).length;
  summary.avgRunPaceSeconds = summary.runDistanceMeters > 0
    ? Math.round(pacedRunSeconds / (summary.runDistanceMeters / 1000))
    : null;
  return summary;
}

type WeeklyReportArgs = Parameters<typeof formatWeeklyReport>;

export function formatActivityWeeklySummary(
  summary: ActivityWeekSummary,
  prev: ActivityWeekSummary | undefined,
  healthMetrics: WeeklyReportArgs[0],
  sleepSessions: WeeklyReportArgs[1],
  activities: WeeklyReportArgs[2]
): string {
  const count = (v: number) => String(v);
  const kcal = (v: number) => `${Math.round(v).toLocaleString()} kcal`;
  const kg = (v: number) => `${Math.round(v).toLocaleString()} kg`;
  const distance = (v: number) => formatDistance(Math.round(v));
  const totalRows: Array<[string, number, number | undefined, (v: number) => string]> = [
    ['Activities', summary.count, prev?.count, count],
    ['Active Days (of 7)', summary.activeDays, prev?.activeDays, count],
    ['Total Time', summary.seconds, prev?.seconds, formatDuration],
    ['Calories', summary.calories, prev?.calories, kcal],
    ['Runs', summary.runCount, prev?.runCount, count],
    ['Run Distance', summary.runDistanceMeters, prev?.runDistanceMeters, distance],
    ['Weight Sessions', summary.weightSessions, prev?.weightSessions, count],
    ['Weight Volume', summary.weightVolumeKg, prev?.weightVolumeKg, kg],
  ];

  let md = `# Activity Weekly Summary: ${summary.start} ~ ${summary.end}\n\n`;

  md += `## Totals\n`;
  md += `| Metric | This Week | Prev Week | Δ |\n`;
  md += `|--------|-----------|-----------|---|\n`;
  for (const [label, value, prevValue, format] of totalRows) {
    const delta = prevValue === undefined ? '-' : formatSignedDelta(value - prevValue, format);
    md += `| ${label} | ${format(value)} | ${prevValue === undefined ? '-' : format(prevValue)} | ${delta} |\n`;
  }
  md += `| Avg Run Pace | ${formatPace(summary.avgRunPaceSeconds)} | ${formatPace(prev?.avgRunPaceSeconds ?? null)} | - |\n`;

  md += `\n## By Type\n`;
  const presentTypes = ACTIVITY_CATEGORIES.filter((c) => summary.byCategory[c.key].count > 0);
  if (presentTypes.length === 0) {
    md += `_No activities this week._\n`;
  } else {
    md += `| Type | Count | Time | Distance | Calories |\n`;
    md += `|------|-------|------|----------|----------|\n`;
    for (const c of presentTypes) {
      const t = summary.byCategory[c.key];
      md += `| ${c.label} | ${t.count} | ${formatDuration(t.seconds)} | ${distance(t.distanceMeters)} | ${kcal(t.calories)} |\n`;
    }
  }

  md += `\n## Daily\n`;
  md += `| Date | Day | Activities | Time | Distance | Calories | Types |\n`;
  md += `|------|-----|------------|------|----------|----------|-------|\n`;
  for (const d of summary.byDay) {
    const types = ACTIVITY_CATEGORIES.filter((c) => d.secondsByCategory[c.key] > 0).map((c) => c.label).join(', ') || '-';
    md += `| ${d.date.slice(5)} | ${d.weekday} | ${d.count} | ${formatDuration(d.seconds)} | ${distance(d.distanceMeters)} | ${d.calories ? kcal(d.calories) : '-'} | ${types} |\n`;
  }

  md += `\n`;
  md += formatWeeklyReport(healthMetrics, sleepSessions, activities, summary.start, summary.end).replace(/^# .*\n\n/, '');
  return md;
}
