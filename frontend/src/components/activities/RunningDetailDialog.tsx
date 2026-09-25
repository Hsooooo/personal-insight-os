import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Copy, Footprints } from 'lucide-react';
import { formatDateTime, formatDistance, formatDuration, formatLapCopyText, formatPace } from '@/lib/utils';
import type { Activity as ActivityType, GarminActivityLap } from '@/types';

export function RunningDetailDialog({
  open,
  onOpenChange,
  runningDetail,
  runningDetailLoading,
  runningLaps,
  runningLapsLoading,
  copied,
  setCopied,
  onClose,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  runningDetail: ActivityType | undefined;
  runningDetailLoading: boolean;
  runningLaps: GarminActivityLap[] | undefined;
  runningLapsLoading: boolean;
  copied: boolean;
  setCopied: (copied: boolean) => void;
  onClose: () => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Footprints className="h-5 w-5 text-emerald-500" />
            {runningDetail?.activityName || 'Running Detail'}
          </DialogTitle>
          <DialogDescription>
            {runningDetail ? formatDateTime(runningDetail.startTime) : ''}
          </DialogDescription>
        </DialogHeader>

        {runningDetailLoading || runningLapsLoading ? (
          <div className="space-y-3 py-4">
            <Skeleton className="h-20" />
            <Skeleton className="h-32" />
          </div>
        ) : runningDetail ? (
          <div className="space-y-5 py-2">
            {/* Summary */}
            <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
              <div className="rounded-lg border bg-muted/30 p-3">
                <div className="text-xs text-muted-foreground">Distance</div>
                <div className="text-sm font-semibold">{formatDistance(runningDetail.distanceMeters)}</div>
              </div>
              <div className="rounded-lg border bg-muted/30 p-3">
                <div className="text-xs text-muted-foreground">Duration</div>
                <div className="text-sm font-semibold">{formatDuration(runningDetail.durationSeconds)}</div>
              </div>
              <div className="rounded-lg border bg-muted/30 p-3">
                <div className="text-xs text-muted-foreground">Pace</div>
                <div className="text-sm font-semibold">{formatPace(runningDetail.averagePaceSeconds)}</div>
              </div>
              <div className="rounded-lg border bg-muted/30 p-3">
                <div className="text-xs text-muted-foreground">Avg HR</div>
                <div className="text-sm font-semibold">{runningDetail.averageHeartRate || '-'} bpm</div>
              </div>
              <div className="rounded-lg border bg-muted/30 p-3">
                <div className="text-xs text-muted-foreground">Max HR</div>
                <div className="text-sm font-semibold">{runningDetail.maxHeartRate || '-'} bpm</div>
              </div>
              <div className="rounded-lg border bg-muted/30 p-3">
                <div className="text-xs text-muted-foreground">Calories</div>
                <div className="text-sm font-semibold">{runningDetail.calories || '-'} kcal</div>
              </div>
            </div>

            {runningDetail.weatherCondition && (
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <span>🌤️ {runningDetail.weatherCondition}</span>
                {runningDetail.weatherTemperature !== null && (
                  <span>{runningDetail.weatherTemperature}°C</span>
                )}
                {runningDetail.weatherHumidity !== null && (
                  <span>· 습도 {runningDetail.weatherHumidity}%</span>
                )}
                {runningDetail.weatherWindSpeed !== null && (
                  <span>· 바람 {runningDetail.weatherWindSpeed}km/h</span>
                )}
              </div>
            )}

            {/* Laps */}
            {runningLaps && runningLaps.length > 0 && (
              <div>
                <div className="text-sm font-semibold mb-2">Splits ({runningLaps.length})</div>
                <div className="rounded-md border overflow-hidden">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead className="w-12">#</TableHead>
                        <TableHead>Distance</TableHead>
                        <TableHead>Time</TableHead>
                        <TableHead>Pace</TableHead>
                        <TableHead>Avg HR</TableHead>
                        <TableHead>Max HR</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {runningLaps.map((lap: GarminActivityLap) => (
                        <TableRow key={lap.id}>
                          <TableCell className="font-medium">{lap.lapIndex}</TableCell>
                          <TableCell>{formatDistance(lap.distanceMeters)}</TableCell>
                          <TableCell>{formatDuration(lap.durationSeconds)}</TableCell>
                          <TableCell>{formatPace(lap.averagePaceSeconds)}</TableCell>
                          <TableCell>{lap.averageHeartRate || '-'} bpm</TableCell>
                          <TableCell>{lap.maxHeartRate || '-'} bpm</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="py-8 text-center text-muted-foreground text-sm">Activity not found.</div>
        )}

        <DialogFooter className="gap-2">
          {runningDetail && runningLaps && runningLaps.length > 0 && (
            <Button
              size="sm"
              variant="outline"
              className="gap-1"
              onClick={() => {
                const text = formatLapCopyText(runningDetail, runningLaps);
                navigator.clipboard.writeText(text).then(() => {
                  setCopied(true);
                  setTimeout(() => setCopied(false), 2000);
                });
              }}
            >
              {copied ? (
                <>Copied!</>
              ) : (
                <>
                  <Copy className="h-4 w-4" />
                  Copy as Text
                </>
              )}
            </Button>
          )}
          <Button size="sm" onClick={onClose}>
            Close
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
