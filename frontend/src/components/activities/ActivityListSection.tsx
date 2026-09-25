import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { Activity, FilterX, Search } from 'lucide-react';
import { formatDateTime, formatDistance, formatDuration, formatPace } from '@/lib/utils';
import type { Activity as ActivityType, ActivityFilter, Page } from '@/types';
import { getTypeBadge } from './ActivityBadges';
import { ActivityTagCell } from './ActivityTagCell';
import { FilterSelect } from './FilterSelect';
import { MobileActivityCard } from './MobileActivityCard';
import { MobileFilterBar } from './MobileFilterBar';
import { ACTIVITY_TYPES, SORT_OPTIONS, TAG_OPTIONS, isRunningType } from './utils';

export function ActivityListSection({
  data,
  isLoading,
  draft,
  setDraft,
  applyFilter,
  resetFilter,
  hasActiveFilter,
  currentSortValue,
  handleSortChange,
  page,
  setPage,
  handleRowClick,
}: {
  data: Page<ActivityType> | undefined;
  isLoading: boolean;
  draft: ActivityFilter;
  setDraft: (f: ActivityFilter) => void;
  applyFilter: () => void;
  resetFilter: () => void;
  hasActiveFilter: boolean;
  currentSortValue: string;
  handleSortChange: (sortValue: string) => void;
  page: number;
  setPage: (updater: (p: number) => number) => void;
  handleRowClick: (activity: ActivityType) => void;
}) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center gap-2 pb-3">
        <Activity className="h-5 w-5 text-emerald-500" />
        <CardTitle>Activity List</CardTitle>
        {hasActiveFilter && (
          <Badge variant="outline" className="ml-2 text-xs">
            <FilterX className="mr-1 h-3 w-3" />
            필터 적용중
          </Badge>
        )}
      </CardHeader>

      {/* Filter Bar - Desktop */}
      <div className="hidden md:block border-b bg-muted/30 px-6 py-3">
        <div className="flex flex-wrap items-center gap-2">
          <FilterSelect
            value={draft.activityType || ''}
            onChange={(v) => setDraft({ ...draft, activityType: v || undefined })}
            options={ACTIVITY_TYPES}
          />
          <FilterSelect
            value={draft.userTag === '' && draft.userTag !== undefined ? '__none__' : draft.userTag || ''}
            onChange={(v) => setDraft({ ...draft, userTag: v === '__none__' ? '' : v || undefined })}
            options={TAG_OPTIONS}
          />
          <FilterSelect
            value={currentSortValue}
            onChange={handleSortChange}
            options={SORT_OPTIONS}
          />
          <div className="relative">
            <Search className="absolute left-2 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="이름 검색"
              value={draft.activityName || ''}
              onChange={(e) => setDraft({ ...draft, activityName: e.target.value || undefined })}
              className="h-8 w-40 pl-7 text-xs"
              onKeyDown={(e) => e.key === 'Enter' && applyFilter()}
            />
          </div>
          <Button size="sm" variant="secondary" className="h-8 text-xs" onClick={applyFilter}>
            적용
          </Button>
          <Button size="sm" variant="ghost" className="h-8 text-xs" onClick={resetFilter}>
            초기화
          </Button>
        </div>

        <div className="mt-2 flex flex-wrap items-center gap-2">
          <span className="text-xs text-muted-foreground">기간:</span>
          <Input
            type="date"
            value={draft.startTimeFrom || ''}
            onChange={(e) => setDraft({ ...draft, startTimeFrom: e.target.value || undefined })}
            className="h-8 w-36 text-xs"
          />
          <span className="text-xs text-muted-foreground">~</span>
          <Input
            type="date"
            value={draft.startTimeTo || ''}
            onChange={(e) => setDraft({ ...draft, startTimeTo: e.target.value || undefined })}
            className="h-8 w-36 text-xs"
          />
          <span className="text-xs text-muted-foreground ml-2">거리(m):</span>
          <Input
            type="number"
            placeholder="최소"
            value={draft.minDistance || ''}
            onChange={(e) => setDraft({ ...draft, minDistance: e.target.value || undefined })}
            className="h-8 w-24 text-xs"
          />
          <span className="text-xs text-muted-foreground">~</span>
          <Input
            type="number"
            placeholder="최대"
            value={draft.maxDistance || ''}
            onChange={(e) => setDraft({ ...draft, maxDistance: e.target.value || undefined })}
            className="h-8 w-24 text-xs"
          />
        </div>
      </div>

      {/* Filter Bar - Mobile */}
      <div className="border-b bg-muted/30 px-4 py-3 md:hidden">
        <MobileFilterBar
          draft={draft}
          setDraft={setDraft}
          applyFilter={applyFilter}
          resetFilter={resetFilter}
          hasActiveFilter={hasActiveFilter}
          currentSortValue={currentSortValue}
        />
      </div>

      <CardContent className="pt-4 px-4 md:px-6">
        {isLoading ? (
          <>
            <div className="hidden md:block space-y-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-12" />
              ))}
            </div>
            <div className="space-y-3 md:hidden">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-24" />
              ))}
            </div>
          </>
        ) : (
          <>
            {/* Desktop Table */}
            <Table className="hidden md:table">
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Tag</TableHead>
                  <TableHead>Distance</TableHead>
                  <TableHead>Duration</TableHead>
                  <TableHead>Pace</TableHead>
                  <TableHead>Avg HR</TableHead>
                  <TableHead>Calories</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data?.content?.length ? (
                  data.content.map((activity) => (
                    <TableRow
                      key={activity.id}
                      className={
                        isRunningType(activity.activityType) || activity.sourceType === 'MANUAL'
                          ? 'cursor-pointer hover:bg-muted/40'
                          : ''
                      }
                      onClick={() => handleRowClick(activity)}
                    >
                      <TableCell className="font-medium">
                        {formatDateTime(activity.startTime)}
                      </TableCell>
                      <TableCell className="max-w-[200px] truncate">{activity.activityName}</TableCell>
                      <TableCell>
                        {getTypeBadge(activity.activityType, activity.sourceType)}
                      </TableCell>
                      <ActivityTagCell activity={activity} />
                      <TableCell>{formatDistance(activity.distanceMeters)}</TableCell>
                      <TableCell>{formatDuration(activity.durationSeconds)}</TableCell>
                      <TableCell>{formatPace(activity.averagePaceSeconds)}</TableCell>
                      <TableCell>{activity.averageHeartRate || '-'} bpm</TableCell>
                      <TableCell>{activity.calories || '-'} kcal</TableCell>
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={9} className="text-center text-muted-foreground py-8">
                      {hasActiveFilter
                        ? '조건에 맞는 활동이 없습니다. 필터를 조정해 보세요.'
                        : 'No activities found. Connect your Garmin to sync data or add a manual workout.'}
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>

            {/* Mobile Card List */}
            <div className="space-y-3 md:hidden">
              {data?.content?.length ? (
                data.content.map((activity) => (
                  <MobileActivityCard
                    key={activity.id}
                    activity={activity}
                    onClick={() => handleRowClick(activity)}
                  />
                ))
              ) : (
                <p className="text-center text-muted-foreground py-8">
                  {hasActiveFilter
                    ? '조건에 맞는 활동이 없습니다. 필터를 조정해 보세요.'
                    : 'No activities found. Connect your Garmin to sync data or add a manual workout.'}
                </p>
              )}
            </div>

            {/* Pagination */}
            {data && data.totalPages > 1 && (
              <div className="flex items-center justify-between pt-4 mt-2 border-t">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage((p) => p - 1)}
                >
                  이전
                </Button>
                <span className="text-sm text-muted-foreground">
                  {page + 1} / {data.totalPages} 페이지
                  <span className="ml-2 text-xs">(총 {data.totalElements}건)</span>
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= data.totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                >
                  다음
                </Button>
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
