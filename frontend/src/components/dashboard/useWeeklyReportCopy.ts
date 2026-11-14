import { useState } from 'react';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import { formatWeeklyReport, isRunningType } from '@/lib/utils';
import type { DashboardSummary, GarminActivityLap } from '@/types';

const kstDate = (date: Date) => date.toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' });

/** 최근 7일 주간 리포트를 만들어 클립보드에 복사한다 */
export function useWeeklyReportCopy(summary: DashboardSummary | undefined) {
  const [copied, setCopied] = useState(false);
  const [copying, setCopying] = useState(false);

  const copy = async () => {
    if (!summary || copying) return;
    setCopying(true);
    try {
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
        runningActivities.map((a) => api.activities.getLaps(a.id).catch((): GarminActivityLap[] => []))
      );
      const lapsMap = new Map<number, GarminActivityLap[]>();
      runningActivities.forEach((a, i) => lapsMap.set(a.id, lapsResults[i] ?? []));

      const activitiesWithLaps = activities.map((a) => ({ ...a, laps: lapsMap.get(a.id) }));
      const report = formatWeeklyReport(summary.last7DaysHealth || [], sleepRes, activitiesWithLaps, startStr, endStr);

      await navigator.clipboard.writeText(report);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy weekly report', err);
      toast.error('주간 리포트를 복사하지 못했습니다');
    } finally {
      setCopying(false);
    }
  };

  return { copy, copied, copying };
}
