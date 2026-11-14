import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { HeartPulse, Scale } from 'lucide-react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
} from 'recharts';
import { formatDate } from '@/lib/utils';
import { QueryError } from '@/components/common/QueryError';
import { ChartDataTable } from '@/components/common/ChartDataTable';

export default function Health() {
  const [days] = useState(14);
  // 조회 범위는 화면 진입 시점 기준으로 고정한다
  const [{ start, end }] = useState(() => {
    const now = Date.now();
    return {
      end: new Date(now).toISOString().split('T')[0],
      start: new Date(now - days * 86400000).toISOString().split('T')[0],
    };
  });

  const metricsQuery = useQuery({
    queryKey: ['health-metrics', start, end],
    queryFn: ({ signal }) => api.health.metrics(start, end, { signal }),
  });
  const metrics = metricsQuery.data;
  const metricsLoading = metricsQuery.isPending;

  const sleepQuery = useQuery({
    queryKey: ['health-sleep', start, end],
    queryFn: ({ signal }) => api.health.sleep(start, end, { signal }),
  });
  const sleep = sleepQuery.data;
  const sleepLoading = sleepQuery.isPending;

  const metricsError = metricsQuery.isError ? (
    <QueryError compact error={metricsQuery.error} onRetry={() => void metricsQuery.refetch()} className="h-64" />
  ) : null;
  const sleepError = sleepQuery.isError ? (
    <QueryError compact error={sleepQuery.error} onRetry={() => void sleepQuery.refetch()} className="h-64" />
  ) : null;

  const rhrData = metrics?.slice().reverse().map((m) => ({
    date: formatDate(m.metricDate),
    rhr: m.restingHeartRate,
  })) || [];

  const sleepData = sleep?.slice().reverse().map((s) => ({
    date: formatDate(s.sleepDate),
    total: s.totalSleepSeconds ? Math.round(s.totalSleepSeconds / 3600 * 10) / 10 : 0,
    deep: s.deepSleepSeconds ? Math.round(s.deepSleepSeconds / 3600 * 10) / 10 : 0,
    light: s.lightSleepSeconds ? Math.round(s.lightSleepSeconds / 3600 * 10) / 10 : 0,
    rem: s.remSleepSeconds ? Math.round(s.remSleepSeconds / 3600 * 10) / 10 : 0,
    awake: s.awakeSeconds ? Math.round(s.awakeSeconds / 3600 * 10) / 10 : 0,
    nap: s.napSeconds ? Math.round(s.napSeconds / 3600 * 10) / 10 : 0,
    score: s.sleepScore,
  })) || [];

  const weightData = metrics?.slice().reverse().map((m) => ({
    date: formatDate(m.metricDate),
    weight: m.weightKg,
  })) || [];

  const stepsData = metrics?.slice().reverse().map((m) => ({
    date: formatDate(m.metricDate),
    steps: m.steps,
  })) || [];

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">Health Timeline</h2>
        <p className="text-muted-foreground">Track your health metrics over time</p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader className="flex flex-row items-center gap-2">
            <HeartPulse className="h-5 w-5 text-rose-500" />
            <div>
              <CardTitle>Resting Heart Rate</CardTitle>
              <CardDescription>Last {days} days</CardDescription>
            </div>
          </CardHeader>
          <CardContent>
            {metricsLoading ? (
              <Skeleton className="h-64" />
            ) : metricsError ? (
              metricsError
            ) : (
              <>
                <ResponsiveContainer width="100%" height={250}>
                  <LineChart data={rhrData}>
                    <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                    <XAxis dataKey="date" fontSize={12} tickLine={false} axisLine={false} />
                    <YAxis fontSize={12} tickLine={false} axisLine={false} domain={['auto', 'auto']} />
                    <Tooltip contentStyle={{ borderRadius: '8px' }} />
                    <Line type="monotone" dataKey="rhr" stroke="#f43f5e" strokeWidth={2} dot={false} />
                  </LineChart>
                </ResponsiveContainer>
                <ChartDataTable caption="안정시 심박" rows={rhrData} columns={[{ key: 'date', label: '날짜' }, { key: 'rhr', label: '안정시 심박 (bpm)' }]} />
              </>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Sleep Duration</CardTitle>
            <CardDescription>Last {days} days</CardDescription>
          </CardHeader>
          <CardContent>
            {sleepLoading ? (
              <Skeleton className="h-64" />
            ) : sleepError ? (
              sleepError
            ) : (
              <>
                <ResponsiveContainer width="100%" height={250}>
                  <BarChart data={sleepData}>
                    <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                    <XAxis dataKey="date" fontSize={12} tickLine={false} axisLine={false} />
                    <YAxis fontSize={12} tickLine={false} axisLine={false} label={{ value: 'h', angle: -90, position: 'insideLeft', fontSize: 10 }} />
                    <Tooltip
                      contentStyle={{ borderRadius: '8px' }}
                      formatter={(value, name) => [`${value ?? '-'}h`, name]}
                    />
                    <Bar dataKey="deep" stackId="sleep" fill="#4f46e5" radius={[2, 2, 0, 0]} name="Deep" />
                    <Bar dataKey="light" stackId="sleep" fill="#818cf8" radius={[2, 2, 0, 0]} name="Light" />
                    <Bar dataKey="rem" stackId="sleep" fill="#a78bfa" radius={[2, 2, 0, 0]} name="REM" />
                    <Bar dataKey="awake" stackId="sleep" fill="#fbbf24" radius={[2, 2, 0, 0]} name="Awake" />
                    <Bar dataKey="nap" fill="#2dd4bf" radius={[2, 2, 0, 0]} name="Nap" />
                  </BarChart>
                </ResponsiveContainer>
                <ChartDataTable caption="수면 시간" rows={sleepData} columns={[{ key: 'date', label: '날짜' }, { key: 'deep', label: 'Deep (h)' }, { key: 'light', label: 'Light (h)' }, { key: 'rem', label: 'REM (h)' }, { key: 'awake', label: 'Awake (h)' }, { key: 'nap', label: 'Nap (h)' }]} />
              </>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Sleep Score</CardTitle>
            <CardDescription>Quality tracking</CardDescription>
          </CardHeader>
          <CardContent>
            {sleepLoading ? (
              <Skeleton className="h-64" />
            ) : sleepError ? (
              sleepError
            ) : (
              <>
                <ResponsiveContainer width="100%" height={250}>
                  <LineChart data={sleepData}>
                    <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                    <XAxis dataKey="date" fontSize={12} tickLine={false} axisLine={false} />
                    <YAxis fontSize={12} tickLine={false} axisLine={false} domain={[0, 100]} />
                    <Tooltip contentStyle={{ borderRadius: '8px' }} />
                    <Line type="monotone" dataKey="score" stroke="#10b981" strokeWidth={2} dot={false} />
                  </LineChart>
                </ResponsiveContainer>
                <ChartDataTable caption="수면 점수" rows={sleepData} columns={[{ key: 'date', label: '날짜' }, { key: 'score', label: '수면 점수' }]} />
              </>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Steps</CardTitle>
            <CardDescription>Daily step count</CardDescription>
          </CardHeader>
          <CardContent>
            {metricsLoading ? (
              <Skeleton className="h-64" />
            ) : metricsError ? (
              metricsError
            ) : (
              <>
                <ResponsiveContainer width="100%" height={250}>
                  <BarChart data={stepsData}>
                    <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                    <XAxis dataKey="date" fontSize={12} tickLine={false} axisLine={false} />
                    <YAxis fontSize={12} tickLine={false} axisLine={false} />
                    <Tooltip contentStyle={{ borderRadius: '8px' }} />
                    <Bar dataKey="steps" fill="#f59e0b" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
                <ChartDataTable caption="걸음 수" rows={stepsData} columns={[{ key: 'date', label: '날짜' }, { key: 'steps', label: '걸음 수' }]} />
              </>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center gap-2">
            <Scale className="h-5 w-5 text-sky-500" />
            <div>
              <CardTitle>Weight</CardTitle>
              <CardDescription>Daily weight change</CardDescription>
            </div>
          </CardHeader>
          <CardContent>
            {metricsLoading ? (
              <Skeleton className="h-64" />
            ) : metricsError ? (
              metricsError
            ) : (
              <>
                <ResponsiveContainer width="100%" height={250}>
                  <LineChart data={weightData}>
                    <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                    <XAxis dataKey="date" fontSize={12} tickLine={false} axisLine={false} />
                    <YAxis fontSize={12} tickLine={false} axisLine={false} domain={['auto', 'auto']} />
                    <Tooltip contentStyle={{ borderRadius: '8px' }} formatter={(value) => [`${value ?? '-'} kg`, 'Weight']} />
                    <Line type="monotone" dataKey="weight" stroke="#0ea5e9" strokeWidth={2} dot={false} connectNulls />
                  </LineChart>
                </ResponsiveContainer>
                <ChartDataTable caption="체중" rows={weightData} columns={[{ key: 'date', label: '날짜' }, { key: 'weight', label: '체중 (kg)' }]} />
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
