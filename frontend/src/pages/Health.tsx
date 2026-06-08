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

export default function Health() {
  const [days] = useState(14);
  const end = new Date().toISOString().split('T')[0];
  const start = new Date(Date.now() - days * 86400000).toISOString().split('T')[0];

  const { data: metrics, isLoading: metricsLoading } = useQuery({
    queryKey: ['health-metrics', start, end],
    queryFn: () => api.health.metrics(start, end),
  });

  const { data: sleep, isLoading: sleepLoading } = useQuery({
    queryKey: ['health-sleep', start, end],
    queryFn: () => api.health.sleep(start, end),
  });

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
    score: s.sleepScore,
  })) || [];

  const weightData = metrics?.slice().reverse().map((m) => ({
    date: formatDate(m.metricDate),
    weight: m.weightKg,
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
            ) : (
              <ResponsiveContainer width="100%" height={250}>
                <LineChart data={rhrData}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                  <XAxis dataKey="date" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis fontSize={12} tickLine={false} axisLine={false} domain={['auto', 'auto']} />
                  <Tooltip contentStyle={{ borderRadius: '8px' }} />
                  <Line type="monotone" dataKey="rhr" stroke="#f43f5e" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
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
            ) : (
              <ResponsiveContainer width="100%" height={250}>
                <BarChart data={sleepData}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                  <XAxis dataKey="date" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis fontSize={12} tickLine={false} axisLine={false} label={{ value: 'h', angle: -90, position: 'insideLeft', fontSize: 10 }} />
                  <Tooltip
                    contentStyle={{ borderRadius: '8px' }}
                    formatter={(value: number, name: string) => [`${value}h`, name]}
                  />
                  <Bar dataKey="deep" stackId="sleep" fill="#4f46e5" radius={[2, 2, 0, 0]} name="Deep" />
                  <Bar dataKey="light" stackId="sleep" fill="#818cf8" radius={[2, 2, 0, 0]} name="Light" />
                  <Bar dataKey="rem" stackId="sleep" fill="#a78bfa" radius={[2, 2, 0, 0]} name="REM" />
                  <Bar dataKey="awake" stackId="sleep" fill="#fbbf24" radius={[2, 2, 0, 0]} name="Awake" />
                </BarChart>
              </ResponsiveContainer>
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
            ) : (
              <ResponsiveContainer width="100%" height={250}>
                <LineChart data={sleepData}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                  <XAxis dataKey="date" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis fontSize={12} tickLine={false} axisLine={false} domain={[0, 100]} />
                  <Tooltip contentStyle={{ borderRadius: '8px' }} />
                  <Line type="monotone" dataKey="score" stroke="#10b981" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
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
            ) : (
              <ResponsiveContainer width="100%" height={250}>
                <BarChart data={metrics?.slice().reverse().map((m) => ({
                  date: formatDate(m.metricDate),
                  steps: m.steps,
                })) || []}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                  <XAxis dataKey="date" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis fontSize={12} tickLine={false} axisLine={false} />
                  <Tooltip contentStyle={{ borderRadius: '8px' }} />
                  <Bar dataKey="steps" fill="#f59e0b" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
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
            ) : (
              <ResponsiveContainer width="100%" height={250}>
                <LineChart data={weightData}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                  <XAxis dataKey="date" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis fontSize={12} tickLine={false} axisLine={false} domain={['auto', 'auto']} />
                  <Tooltip contentStyle={{ borderRadius: '8px' }} formatter={(value: number) => [`${value} kg`, 'Weight']} />
                  <Line type="monotone" dataKey="weight" stroke="#0ea5e9" strokeWidth={2} dot={false} connectNulls />
                </LineChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
