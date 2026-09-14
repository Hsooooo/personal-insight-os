import { useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import {
  Activity as ActivityIcon,
  CalendarDays,
  Check,
  ChevronLeft,
  ChevronRight,
  Copy,
  Dumbbell,
  Flame,
  Footprints,
  Timer,
} from 'lucide-react';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import {
  ACTIVITY_CATEGORIES,
  activityCategoryMeta,
  addDaysYmd,
  formatActivityWeeklySummary,
  formatDistance,
  formatDuration,
  formatPace,
  formatSignedDelta,
  isRunningType,
  mondayOf,
  summarizeActivityWeek,
  todayKst,
  weekdayLabel,
} from '@/lib/utils';
import type { Activity } from '@/types';

const WEEK_OPTION_COUNT = 12;

function fetchWeekActivities(weekStart: string) {
  return api.activities
    .list(0, 200, {
      startTimeFrom: weekStart,
      startTimeTo: addDaysYmd(weekStart, 6),
      sortBy: 'startTime',
      sortDir: 'asc',
    })
    .then((page) => page.content || []);
}

function weekLabel(start: string, currentWeekStart: string) {
  const base = `${start} ~ ${addDaysYmd(start, 6).slice(5)}`;
  if (start === currentWeekStart) return `${base} · 이번 주`;
  if (start === addDaysYmd(currentWeekStart, -7)) return `${base} · 지난 주`;
  return base;
}

function delta(current: number, prev: number | undefined, format: (v: number) => string) {
  return prev === undefined ? null : formatSignedDelta(current - prev, format);
}

function StatCard({
  title,
  value,
  detail,
  delta,
  icon,
}: {
  title: string;
  value: string;
  detail?: string;
  delta?: string | null;
  icon: ReactNode;
}) {
  return (
    <Card>
      <CardContent className="pt-6">
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted-foreground">{title}</span>
          <span className="text-muted-foreground">{icon}</span>
        </div>
        <p className="mt-2 text-2xl font-semibold tracking-tight tabular-nums">{value}</p>
        {detail && <p className="mt-1 text-xs text-muted-foreground">{detail}</p>}
        {delta && <p className="mt-1 text-xs text-muted-foreground">지난주 대비 {delta}</p>}
      </CardContent>
    </Card>
  );
}

export default function ActivityWeeklyOverview({
  onActivityClick,
}: {
  onActivityClick: (activity: Activity) => void;
}) {
  const currentWeekStart = useMemo(() => mondayOf(todayKst()), []);
  const [weekStart, setWeekStart] = useState(currentWeekStart);
  const [copying, setCopying] = useState(false);
  const [copied, setCopied] = useState(false);
  const weekEnd = addDaysYmd(weekStart, 6);
  const prevWeekStart = addDaysYmd(weekStart, -7);

  // 'activities' 접두 queryKey → 동기화/웨이트 저장/태그 변경 시 기존 invalidate로 함께 갱신
  const { data: weekActivities, isLoading } = useQuery({
    queryKey: ['activities', 'week', weekStart],
    queryFn: () => fetchWeekActivities(weekStart),
  });
  const { data: prevWeekActivities } = useQuery({
    queryKey: ['activities', 'week', prevWeekStart],
    queryFn: () => fetchWeekActivities(prevWeekStart),
  });

  const summary = useMemo(
    () => summarizeActivityWeek(weekActivities || [], weekStart),
    [weekActivities, weekStart]
  );
  const prevSummary = useMemo(
    () => (prevWeekActivities ? summarizeActivityWeek(prevWeekActivities, prevWeekStart) : undefined),
    [prevWeekActivities, prevWeekStart]
  );

  const weekOptions = useMemo(() => {
    const starts = Array.from({ length: WEEK_OPTION_COUNT }, (_, i) => addDaysYmd(currentWeekStart, -7 * i));
    if (!starts.includes(weekStart)) starts.push(weekStart);
    return starts.map((start) => ({ start, label: weekLabel(start, currentWeekStart) }));
  }, [currentWeekStart, weekStart]);

  const presentCategories = ACTIVITY_CATEGORIES.filter((c) => summary.byCategory[c.key].count > 0);
  const categoriesBySeconds = [...presentCategories].sort(
    (a, b) => summary.byCategory[b.key].seconds - summary.byCategory[a.key].seconds
  );
  const maxCategorySeconds = Math.max(1, ...presentCategories.map((c) => summary.byCategory[c.key].seconds));
  const chartData = summary.byDay.map((day) => ({
    label: `${day.weekday} ${day.date.slice(8)}`,
    ...Object.fromEntries(ACTIVITY_CATEGORIES.map((c) => [c.key, Math.round(day.secondsByCategory[c.key] / 60)])),
  }));

  const handleCopyWeeklySummary = async () => {
    if (!weekActivities || copying) return;
    setCopying(true);
    try {
      const [health, sleep] = await Promise.all([
        api.health.metrics(weekStart, weekEnd),
        api.health.sleep(weekStart, weekEnd),
      ]);
      const runs = weekActivities.filter((a) => isRunningType(a.activityType));
      const lapsResults = await Promise.all(runs.map((a) => api.activities.getLaps(a.id).catch(() => [])));
      const lapsMap = new Map(runs.map((a, i) => [a.id, lapsResults[i]]));
      const activitiesWithLaps = weekActivities.map((a) => ({ ...a, laps: lapsMap.get(a.id) }));

      const report = formatActivityWeeklySummary(summary, prevSummary, health, sleep, activitiesWithLaps);
      await navigator.clipboard.writeText(report);
      setCopied(true);
      toast.success('Activity weekly summary copied');
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '주간 요약 복사 실패');
    } finally {
      setCopying(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 rounded-md border bg-muted/20 p-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            className="h-9 w-9 shrink-0 p-0"
            onClick={() => setWeekStart(addDaysYmd(weekStart, -7))}
            aria-label="이전 주"
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <select
            value={weekStart}
            onChange={(e) => setWeekStart(e.target.value)}
            className="h-9 min-w-0 flex-1 rounded-md border border-input bg-background px-3 text-sm sm:flex-none"
          >
            {weekOptions.map((option) => (
              <option key={option.start} value={option.start}>{option.label}</option>
            ))}
          </select>
          <Button
            size="sm"
            variant="outline"
            className="h-9 w-9 shrink-0 p-0"
            onClick={() => setWeekStart(addDaysYmd(weekStart, 7))}
            disabled={weekStart >= currentWeekStart}
            aria-label="다음 주"
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
        <Button
          size="sm"
          variant="outline"
          className="gap-2"
          onClick={handleCopyWeeklySummary}
          disabled={copying || isLoading}
        >
          {copied ? <Check className="h-4 w-4" /> : <Copy className={`h-4 w-4 ${copying ? 'animate-pulse' : ''}`} />}
          {copied ? 'Copied!' : 'Copy Weekly Summary'}
        </Button>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-6">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-28" />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-6">
          <StatCard
            title="활동"
            value={`${summary.count}회`}
            delta={delta(summary.count, prevSummary?.count, String)}
            icon={<ActivityIcon className="h-4 w-4" />}
          />
          <StatCard
            title="총 시간"
            value={formatDuration(summary.seconds)}
            delta={delta(summary.seconds, prevSummary?.seconds, formatDuration)}
            icon={<Timer className="h-4 w-4" />}
          />
          <StatCard
            title="러닝 거리"
            value={formatDistance(Math.round(summary.runDistanceMeters))}
            detail={summary.runCount ? `${summary.runCount}회 · ${formatPace(summary.avgRunPaceSeconds)}` : undefined}
            delta={delta(summary.runDistanceMeters, prevSummary?.runDistanceMeters, formatDistance)}
            icon={<Footprints className="h-4 w-4" />}
          />
          <StatCard
            title="칼로리"
            value={`${Math.round(summary.calories).toLocaleString()} kcal`}
            delta={delta(summary.calories, prevSummary?.calories, (v) => `${v.toLocaleString()} kcal`)}
            icon={<Flame className="h-4 w-4" />}
          />
          <StatCard
            title="웨이트"
            value={`${summary.weightSessions}회`}
            detail={summary.weightVolumeKg ? `볼륨 ${Math.round(summary.weightVolumeKg).toLocaleString()} kg` : undefined}
            delta={delta(summary.weightSessions, prevSummary?.weightSessions, String)}
            icon={<Dumbbell className="h-4 w-4" />}
          />
          <StatCard
            title="활동일"
            value={`${summary.activeDays}/7`}
            delta={delta(summary.activeDays, prevSummary?.activeDays, String)}
            icon={<CalendarDays className="h-4 w-4" />}
          />
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-[1.4fr_1fr]">
        <Card>
          <CardHeader>
            <CardTitle>Daily</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading ? <Skeleton className="h-64" /> : summary.count === 0 ? (
              <p className="text-sm text-muted-foreground">이 주에 기록된 활동이 없습니다.</p>
            ) : (
              <>
                <div className="h-64 min-w-0">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartData} margin={{ top: 8, right: 8, left: -12, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} />
                      <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                      <YAxis tick={{ fontSize: 12 }} unit="분" allowDecimals={false} />
                      <Tooltip formatter={(value: number) => `${value}분`} />
                      {presentCategories.map((c) => (
                        <Bar key={c.key} dataKey={c.key} name={c.label} stackId="minutes" fill={c.color} />
                      ))}
                    </BarChart>
                  </ResponsiveContainer>
                </div>
                <div className="mt-3 flex flex-wrap gap-3 text-xs text-muted-foreground">
                  {presentCategories.map((c) => (
                    <span key={c.key} className="inline-flex items-center gap-1.5">
                      <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: c.color }} />
                      {c.label}
                    </span>
                  ))}
                </div>
              </>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>By Type</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading ? <Skeleton className="h-40" /> : categoriesBySeconds.length === 0 ? (
              <p className="text-sm text-muted-foreground">이 주에 기록된 활동이 없습니다.</p>
            ) : (
              <div className="space-y-2">
                {categoriesBySeconds.map((c) => {
                  const totals = summary.byCategory[c.key];
                  return (
                    <div key={c.key} className="rounded-md border bg-background px-3 py-2">
                      <div className="flex items-center justify-between gap-3">
                        <span className="flex items-center gap-2 text-sm font-medium">
                          <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: c.color }} />
                          {c.label}
                        </span>
                        <span className="text-sm tabular-nums">
                          {totals.count}회 · {formatDuration(totals.seconds)}
                        </span>
                      </div>
                      {totals.distanceMeters > 0 && (
                        <div className="mt-1 text-xs text-muted-foreground">
                          {formatDistance(Math.round(totals.distanceMeters))}
                        </div>
                      )}
                      <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-muted">
                        <div
                          className="h-full rounded-full"
                          style={{
                            width: `${Math.max(6, Math.round((totals.seconds / maxCategorySeconds) * 100))}%`,
                            backgroundColor: c.color,
                          }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Activities · {summary.count}</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="space-y-2">
              {Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-12" />
              ))}
            </div>
          ) : !weekActivities?.length ? (
            <p className="text-sm text-muted-foreground">이 주에 기록된 활동이 없습니다.</p>
          ) : (
            <div className="space-y-2">
              {weekActivities.map((activity) => {
                const meta = activityCategoryMeta(activity.activityType);
                const running = isRunningType(activity.activityType);
                const clickable = running || activity.sourceType === 'MANUAL';
                const date = activity.startTime.slice(0, 10);
                const metrics = [
                  formatDistance(activity.distanceMeters),
                  formatDuration(activity.durationSeconds),
                  running ? formatPace(activity.averagePaceSeconds) : '-',
                  activity.averageHeartRate ? `${activity.averageHeartRate} bpm` : '-',
                ].filter((v) => v !== '-');
                return (
                  <div
                    key={activity.id}
                    onClick={clickable ? () => onActivityClick(activity) : undefined}
                    className={`flex flex-col gap-1 rounded-md border bg-background px-3 py-2 sm:flex-row sm:items-center sm:justify-between ${
                      clickable ? 'cursor-pointer hover:bg-muted/40' : ''
                    }`}
                  >
                    <div className="flex min-w-0 items-center gap-2">
                      <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: meta.color }} />
                      <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
                        {date.slice(5)} ({weekdayLabel(date)}) {activity.startTime.slice(11, 16)}
                      </span>
                      <span className="truncate text-sm font-medium">{activity.activityName}</span>
                    </div>
                    <span className="text-xs text-muted-foreground tabular-nums sm:text-right">{metrics.join(' · ')}</span>
                  </div>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
