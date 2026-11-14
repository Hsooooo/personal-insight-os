import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Activity, HeartPulse, Moon, Scale } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDistance } from '@/lib/utils';
import type { DashboardSummary } from '@/types';

function StatCard({
  title,
  icon: Icon,
  iconClassName,
  value,
  caption,
}: {
  title: string;
  icon: LucideIcon;
  iconClassName: string;
  value: ReactNode;
  caption: string;
}) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-sm font-medium">{title}</CardTitle>
        <Icon className={`h-4 w-4 ${iconClassName}`} aria-hidden="true" />
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold">{value}</div>
        <p className="text-xs text-muted-foreground">{caption}</p>
      </CardContent>
    </Card>
  );
}

export function StatCards({ data }: { data: DashboardSummary }) {
  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
      <StatCard
        title="Resting Heart Rate"
        icon={HeartPulse}
        iconClassName="text-rose-500"
        value={
          <>
            {data.latestHealth?.restingHeartRate || '-'}{' '}
            <span className="text-sm font-normal text-muted-foreground">bpm</span>
          </>
        }
        caption="Latest reading"
      />
      <StatCard
        title="Sleep Score"
        icon={Moon}
        iconClassName="text-indigo-500"
        value={data.latestSleep?.sleepScore || '-'}
        caption="Last night"
      />
      <StatCard
        title="Latest Activity"
        icon={Activity}
        iconClassName="text-emerald-500"
        value={data.latestActivity ? formatDistance(data.latestActivity.distanceMeters) : '-'}
        caption={data.latestActivity?.activityType || 'No activity'}
      />
      <StatCard
        title="Weight"
        icon={Scale}
        iconClassName="text-sky-500"
        value={
          <>
            {data.latestHealth?.weightKg ?? '-'}{' '}
            <span className="text-sm font-normal text-muted-foreground">kg</span>
          </>
        }
        caption="Latest reading"
      />
    </div>
  );
}
