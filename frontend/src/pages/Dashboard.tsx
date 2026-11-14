import { useQuery } from '@tanstack/react-query';
import { Check, FileText } from 'lucide-react';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { QueryError } from '@/components/common/QueryError';
import { BriefingCard } from '@/components/dashboard/BriefingCard';
import { DashboardSkeleton } from '@/components/dashboard/DashboardSkeleton';
import { GoalProgressCard } from '@/components/dashboard/GoalProgressCard';
import { QuickQuestionsCard } from '@/components/dashboard/QuickQuestionsCard';
import { RecentInsightsCard } from '@/components/dashboard/RecentInsightsCard';
import { StatCards } from '@/components/dashboard/StatCards';
import { TrendsChart } from '@/components/dashboard/TrendsChart';
import { useWeeklyReportCopy } from '@/components/dashboard/useWeeklyReportCopy';

export default function Dashboard() {
  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['dashboard'],
    queryFn: api.dashboard.summary,
  });
  const weeklyReport = useWeeklyReportCopy(data);

  if (isPending) {
    return <DashboardSkeleton />;
  }

  if (isError) {
    return (
      <div className="space-y-6">
        <h2 className="text-3xl font-bold tracking-tight">Dashboard</h2>
        <QueryError error={error} onRetry={() => void refetch()} title="대시보드를 불러오지 못했습니다" />
      </div>
    );
  }

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
          onClick={() => void weeklyReport.copy()}
          disabled={weeklyReport.copying}
        >
          {weeklyReport.copied ? (
            <Check className="h-4 w-4" aria-hidden="true" />
          ) : (
            <FileText className="h-4 w-4" aria-hidden="true" />
          )}
          {weeklyReport.copied ? 'Copied!' : 'Copy Weekly Report'}
        </Button>
      </div>

      <BriefingCard briefing={data.latestBriefing} />
      <StatCards data={data} />

      <div className="grid gap-4 lg:grid-cols-7">
        <TrendsChart metrics={data.last7DaysHealth} />
        <div className="space-y-4 lg:col-span-3">
          <GoalProgressCard goals={data.activeGoals} />
          <RecentInsightsCard insights={data.recentInsights} />
          <QuickQuestionsCard questions={data.suggestedQuestions} />
        </div>
      </div>
    </div>
  );
}
