import { Activity, Heart, Moon, Scale } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { QueryError } from '@/components/common/QueryError';
import type { SyncLog } from '@/types';
import { SyncStatusIcon } from './SyncStatus';
import { getSyncStatusLabel } from './syncStatusLabel';

interface SyncHistoryCardProps {
  logs: SyncLog[] | undefined;
  isPending: boolean;
  error: unknown;
  onRetry: () => void;
}

export function SyncHistoryCard({ logs, isPending, error, onRetry }: SyncHistoryCardProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Sync History</CardTitle>
        <CardDescription>Recent Garmin synchronization logs</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="max-h-[400px] space-y-3 overflow-y-auto">
          {isPending ? (
            Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-20" />)
          ) : error ? (
            <QueryError compact error={error} onRetry={onRetry} />
          ) : logs && logs.length > 0 ? (
            <ul className="space-y-3">
              {logs.slice(0, 20).map((log) => (
                <li key={log.id} className="space-y-2 rounded-lg border p-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <SyncStatusIcon status={log.status} />
                      <span className="text-sm font-medium">{getSyncStatusLabel(log.status)}</span>
                    </div>
                    <span className="text-xs text-muted-foreground">
                      {new Date(log.startedAt).toLocaleString('ko-KR')}
                    </span>
                  </div>
                  <div className="flex flex-wrap gap-3 text-xs text-muted-foreground">
                    <span className="flex items-center gap-1" title="Activities">
                      <Activity className="h-3 w-3" aria-hidden="true" />
                      <span className="sr-only">Activities</span>
                      {log.activitiesCount || 0}
                    </span>
                    <span className="flex items-center gap-1" title="Health metrics">
                      <Heart className="h-3 w-3" aria-hidden="true" />
                      <span className="sr-only">Health metrics</span>
                      {log.healthMetricsCount || 0}
                    </span>
                    <span className="flex items-center gap-1" title="Sleep">
                      <Moon className="h-3 w-3" aria-hidden="true" />
                      <span className="sr-only">Sleep</span>
                      {log.sleepCount || 0}
                    </span>
                    <span className="flex items-center gap-1" title="Weights">
                      <Scale className="h-3 w-3" aria-hidden="true" />
                      <span className="sr-only">Weights</span>
                      {log.weightsCount || 0}
                    </span>
                  </div>
                  {log.errorMessage && <p className="text-xs text-red-500">{log.errorMessage}</p>}
                </li>
              ))}
            </ul>
          ) : (
            <p className="py-8 text-center text-sm text-muted-foreground">No sync history yet</p>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
