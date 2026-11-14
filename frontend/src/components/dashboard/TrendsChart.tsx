import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ChartDataTable } from '@/components/common/ChartDataTable';
import { EmptyState } from '@/components/common/EmptyState';
import { formatDate } from '@/lib/utils';
import type { HealthMetric } from '@/types';

interface TrendRow {
  date: string;
  rhr: number | null;
  stress: number | null;
  weight: number | null;
}

export function TrendsChart({ metrics }: { metrics: HealthMetric[] | undefined }) {
  const rows: TrendRow[] =
    metrics
      ?.slice()
      .reverse()
      .map((h) => ({
        date: formatDate(h.metricDate),
        rhr: h.restingHeartRate,
        stress: h.stressAvg,
        weight: h.weightKg,
      })) ?? [];

  return (
    <Card className="lg:col-span-4">
      <CardHeader>
        <CardTitle>7-Day Trends</CardTitle>
        <CardDescription>Resting heart rate, stress level, and weight</CardDescription>
      </CardHeader>
      <CardContent>
        {rows.length === 0 ? (
          <EmptyState message="최근 7일 건강 데이터가 없습니다." className="h-[250px]" />
        ) : (
          <>
            <div role="img" aria-label={`최근 ${rows.length}일 안정시 심박·스트레스·체중 추이 차트. 아래 데이터 표에서 수치를 확인할 수 있습니다.`}>
              <ResponsiveContainer width="100%" height={250}>
                <AreaChart data={rows}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                  <XAxis dataKey="date" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis fontSize={12} tickLine={false} axisLine={false} />
                  <Tooltip contentStyle={{ borderRadius: '8px', border: '1px solid hsl(var(--border))' }} />
                  <Area type="monotone" dataKey="rhr" stroke="#f43f5e" fill="#f43f5e" fillOpacity={0.1} strokeWidth={2} name="RHR" />
                  <Area type="monotone" dataKey="stress" stroke="#6366f1" fill="#6366f1" fillOpacity={0.1} strokeWidth={2} name="Stress" />
                  <Area type="monotone" dataKey="weight" stroke="#0ea5e9" fill="#0ea5e9" fillOpacity={0.1} strokeWidth={2} name="Weight" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
            <ChartDataTable<TrendRow>
              caption="최근 7일 건강 추이"
              rows={rows}
              columns={[
                { key: 'date', label: '날짜' },
                { key: 'rhr', label: '안정시 심박 (bpm)' },
                { key: 'stress', label: '스트레스' },
                { key: 'weight', label: '체중 (kg)' },
              ]}
            />
          </>
        )}
      </CardContent>
    </Card>
  );
}
