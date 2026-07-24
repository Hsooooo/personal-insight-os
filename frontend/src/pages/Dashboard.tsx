import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';
import { useNavigate } from 'react-router-dom';
import {
  Activity,
  HeartPulse,
  Moon,
  Scale,
  Lightbulb,
  MessageSquare,
  Check,
  FileText,
  Newspaper,
  Target,
  RefreshCw,
} from 'lucide-react';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import { formatDate, formatDistance, formatWeeklyReport, isRunningType } from '@/lib/utils';

function paceLabel(pace?: string | null): string {
  switch (pace) {
    case 'AHEAD':
      return '앞서감';
    case 'ON_TRACK':
      return '순항';
    case 'BEHIND':
      return '뒤처짐';
    case 'INSUFFICIENT_DATA':
      return '데이터 부족';
    default:
      return pace || '-';
  }
}

export default function Dashboard() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [copied, setCopied] = useState(false);
  const [copying, setCopying] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ['dashboard'],
    queryFn: api.dashboard.summary,
  });

  const generateBriefing = useMutation({
    mutationFn: () => api.briefings.generate(true),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
  });

  const handleCopyWeeklyReport = async () => {
    if (!data || copying) return;
    setCopying(true);
    try {
      const kstDate = (date: Date) =>
        date.toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' });
      const end = new Date();
      const start = new Date();
      start.setDate(end.getDate() - 6);
      const startStr = kstDate(start);
      const endStr = kstDate(end);

      const [sleepRes, activitiesRes] = await Promise.all([
        api.health.sleep(startStr, endStr),
        api.activities.list(0, 100, {
          startTimeFrom: startStr,
          startTimeTo: endStr,
          sortBy: 'startTime',
          sortDir: 'desc',
        }),
      ]);

      const activities = activitiesRes.content || [];
      const runningActivities = activities.filter((a) => isRunningType(a.activityType));
      const lapsResults = await Promise.all(
        runningActivities.map((a) => api.activities.getLaps(a.id).catch(() => []))
      );
      const lapsMap = new Map<number, typeof lapsResults[0]>();
      runningActivities.forEach((a, i) => lapsMap.set(a.id, lapsResults[i]));

      const activitiesWithLaps = activities.map((a) => ({
        ...a,
        laps: lapsMap.get(a.id),
      }));

      const report = formatWeeklyReport(
        data.last7DaysHealth || [],
        sleepRes,
        activitiesWithLaps,
        startStr,
        endStr
      );

      await navigator.clipboard.writeText(report);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy weekly report', err);
    } finally {
      setCopying(false);
    }
  };

  if (isLoading) {
    return <DashboardSkeleton />;
  }

  const healthChartData = data?.last7DaysHealth?.slice().reverse().map((h) => ({
    date: formatDate(h.metricDate),
    rhr: h.restingHeartRate,
    stress: h.stressAvg,
    weight: h.weightKg,
  })) || [];

  const briefingPreview = data?.latestBriefing?.summary
    ? data.latestBriefing.summary.split('\n').filter(Boolean).slice(0, 4).join('\n')
    : null;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Dashboard</h2>
          <p className="text-muted-foreground">Overview of your health and activity data</p>
        </div>
        <Button
          size="sm"
          variant="outline"
          className="gap-1"
          onClick={handleCopyWeeklyReport}
          disabled={copying || isLoading}
        >
          {copied ? <Check className="h-4 w-4" /> : <FileText className="h-4 w-4" />}
          {copied ? 'Copied!' : 'Copy Weekly Report'}
        </Button>
      </div>

      <Card className="border-indigo-200/60 bg-gradient-to-br from-indigo-50/80 to-background">
        <CardHeader className="flex flex-row items-start justify-between space-y-0 pb-3">
          <div>
            <CardTitle className="flex items-center gap-2 text-base">
              <Newspaper className="h-4 w-4 text-indigo-600" />
              {data?.latestBriefing?.title || 'Weekly Briefing'}
            </CardTitle>
            <CardDescription className="mt-1">
              지난주 데이터 기반 자동 브리핑 · 목표·패턴 포함
            </CardDescription>
          </div>
          <Button
            size="sm"
            variant="outline"
            className="gap-1 shrink-0"
            disabled={generateBriefing.isPending}
            onClick={() => generateBriefing.mutate()}
          >
            <RefreshCw className={`h-3.5 w-3.5 ${generateBriefing.isPending ? 'animate-spin' : ''}`} />
            {data?.latestBriefing ? 'Regenerate' : 'Generate'}
          </Button>
        </CardHeader>
        <CardContent>
          {briefingPreview ? (
            <div className="space-y-3">
              <pre className="whitespace-pre-wrap font-sans text-sm leading-relaxed text-foreground/90">
                {briefingPreview}
              </pre>
              <div className="flex items-center gap-2">
                <Badge variant="secondary">WEEKLY_BRIEFING</Badge>
                {data?.latestBriefing?.confidence != null && (
                  <span className="text-xs text-muted-foreground">
                    Confidence: {(Number(data.latestBriefing.confidence) * 100).toFixed(0)}%
                  </span>
                )}
                <Button
                  variant="link"
                  className="h-auto p-0 text-xs"
                  onClick={() => navigate('/insights')}
                >
                  View full briefing
                </Button>
              </div>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">
              아직 주간 브리핑이 없습니다. Generate를 눌러 지난주 요약을 만들어 보세요.
            </p>
          )}
        </CardContent>
      </Card>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium">Resting Heart Rate</CardTitle>
            <HeartPulse className="h-4 w-4 text-rose-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {data?.latestHealth?.restingHeartRate || '-'}{' '}
              <span className="text-sm font-normal text-muted-foreground">bpm</span>
            </div>
            <p className="text-xs text-muted-foreground">Latest reading</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium">Sleep Score</CardTitle>
            <Moon className="h-4 w-4 text-indigo-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{data?.latestSleep?.sleepScore || '-'}</div>
            <p className="text-xs text-muted-foreground">Last night</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium">Latest Activity</CardTitle>
            <Activity className="h-4 w-4 text-emerald-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {data?.latestActivity ? formatDistance(data.latestActivity.distanceMeters) : '-'}
            </div>
            <p className="text-xs text-muted-foreground">
              {data?.latestActivity?.activityType || 'No activity'}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium">Weight</CardTitle>
            <Scale className="h-4 w-4 text-sky-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {data?.latestHealth?.weightKg ?? '-'}{' '}
              <span className="text-sm font-normal text-muted-foreground">kg</span>
            </div>
            <p className="text-xs text-muted-foreground">Latest reading</p>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-7">
        <Card className="lg:col-span-4">
          <CardHeader>
            <CardTitle>7-Day Trends</CardTitle>
            <CardDescription>Resting heart rate, stress level, and weight</CardDescription>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={250}>
              <AreaChart data={healthChartData}>
                <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                <XAxis dataKey="date" fontSize={12} tickLine={false} axisLine={false} />
                <YAxis fontSize={12} tickLine={false} axisLine={false} />
                <Tooltip
                  contentStyle={{ borderRadius: '8px', border: '1px solid hsl(var(--border))' }}
                />
                <Area
                  type="monotone"
                  dataKey="rhr"
                  stroke="#f43f5e"
                  fill="#f43f5e"
                  fillOpacity={0.1}
                  strokeWidth={2}
                  name="RHR"
                />
                <Area
                  type="monotone"
                  dataKey="stress"
                  stroke="#6366f1"
                  fill="#6366f1"
                  fillOpacity={0.1}
                  strokeWidth={2}
                  name="Stress"
                />
                <Area
                  type="monotone"
                  dataKey="weight"
                  stroke="#0ea5e9"
                  fill="#0ea5e9"
                  fillOpacity={0.1}
                  strokeWidth={2}
                  name="Weight"
                />
              </AreaChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        <div className="space-y-4 lg:col-span-3">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0">
              <CardTitle className="flex items-center gap-2">
                <Target className="h-4 w-4 text-indigo-500" />
                Goal Progress
              </CardTitle>
              <Button variant="link" className="h-auto p-0 text-xs" onClick={() => navigate('/goals')}>
                Manage
              </Button>
            </CardHeader>
            <CardContent className="space-y-3">
              {data?.activeGoals?.length ? (
                data.activeGoals.slice(0, 3).map((goal) => {
                  const percent = Math.min(Math.max(Number(goal.progressPercent ?? 0), 0), 100);
                  return (
                    <div key={goal.id} className="space-y-1.5">
                      <div className="flex items-center justify-between gap-2 text-sm">
                        <span className="truncate font-medium">{goal.title}</span>
                        <Badge variant="outline" className="shrink-0 text-[10px]">
                          {paceLabel(goal.paceStatus)}
                        </Badge>
                      </div>
                      {goal.progressSupported ? (
                        <>
                          <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
                            <div
                              className="h-full rounded-full bg-indigo-500"
                              style={{ width: `${percent}%` }}
                            />
                          </div>
                          <p className="text-xs text-muted-foreground">
                            {goal.currentValue ?? '-'}
                            {goal.targetUnit ? ` ${goal.targetUnit}` : ''}
                            {' / '}
                            {goal.targetValue}
                            {goal.targetUnit ? ` ${goal.targetUnit}` : ''}
                            {goal.progressPercent != null
                              ? ` · ${Number(goal.progressPercent).toFixed(0)}%`
                              : ''}
                          </p>
                        </>
                      ) : (
                        <p className="text-xs text-muted-foreground">자동 추적 미지원</p>
                      )}
                    </div>
                  );
                })
              ) : (
                <p className="text-sm text-muted-foreground">
                  목표가 없습니다. Goals에서 주간 러닝·수면 목표를 추가해 보세요.
                </p>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Lightbulb className="h-4 w-4 text-amber-500" />
                Recent Insights
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {data?.recentInsights?.length ? (
                data.recentInsights.slice(0, 3).map((insight) => (
                  <div
                    key={insight.id}
                    className="rounded-lg border p-3 hover:bg-muted/50 cursor-pointer transition-colors"
                    onClick={() => navigate('/insights')}
                  >
                    <p className="text-sm font-medium line-clamp-2">{insight.summary}</p>
                    <div className="mt-2 flex items-center gap-2">
                      <Badge
                        variant={insight.category === 'ANOMALY_ALERT' ? 'destructive' : 'secondary'}
                        className="text-xs"
                      >
                        {insight.category || 'Insight'}
                      </Badge>
                      <span className="text-xs text-muted-foreground">
                        Confidence:{' '}
                        {insight.confidence ? `${(insight.confidence * 100).toFixed(0)}%` : '-'}
                      </span>
                    </div>
                  </div>
                ))
              ) : (
                <p className="text-sm text-muted-foreground">No insights yet. Ask your data a question!</p>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <MessageSquare className="h-4 w-4 text-indigo-500" />
                Quick Questions
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {data?.suggestedQuestions?.map((q, i) => (
                <Button
                  key={i}
                  variant="outline"
                  className="w-full justify-start text-left h-auto py-2 px-3"
                  onClick={() => navigate('/ask', { state: { question: q } })}
                >
                  <span className="text-sm line-clamp-2">{q}</span>
                </Button>
              ))}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6">
      <Skeleton className="h-10 w-48" />
      <Skeleton className="h-40 w-full" />
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-28" />
        ))}
      </div>
      <div className="grid gap-4 lg:grid-cols-7">
        <Skeleton className="h-80 lg:col-span-4" />
        <Skeleton className="h-80 lg:col-span-3" />
      </div>
    </div>
  );
}
