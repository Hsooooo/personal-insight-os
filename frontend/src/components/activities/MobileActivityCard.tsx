import { Badge } from '@/components/ui/badge';
import { formatDateTime, formatDistance, formatDuration, formatPace } from '@/lib/utils';
import type { Activity as ActivityType } from '@/types';
import { getTypeBadge } from './ActivityBadges';
import { getTagColor, isRunningType } from './utils';

export function MobileActivityCard({
  activity,
  onClick,
}: {
  activity: ActivityType;
  onClick: () => void;
}) {
  const running = isRunningType(activity.activityType);
  return (
    <div
      onClick={onClick}
      className="rounded-lg border bg-card p-4 active:bg-muted/60 cursor-pointer transition-colors"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <span>{formatDateTime(activity.startTime)}</span>
            {getTypeBadge(activity.activityType, activity.sourceType)}
          </div>
          <h3 className="mt-1 truncate text-sm font-semibold">{activity.activityName}</h3>
        </div>
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
        {activity.distanceMeters != null && (
          <span className="text-muted-foreground">{formatDistance(activity.distanceMeters)}</span>
        )}
        {activity.durationSeconds > 0 && (
          <span className="text-muted-foreground">{formatDuration(activity.durationSeconds)}</span>
        )}
        {running && activity.averagePaceSeconds != null && (
          <span className="text-muted-foreground">{formatPace(activity.averagePaceSeconds)}</span>
        )}
      </div>
      {activity.userTag && (
        <div className="mt-2">
          <Badge className={getTagColor(activity.userTag)}>{activity.userTag}</Badge>
        </div>
      )}
    </div>
  );
}
