import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { ClipboardPaste, Plus, RefreshCw, X } from 'lucide-react';
import type { Activity as ActivityType, ActivityFilter } from '@/types';
import HevyImportDialog from '@/components/HevyImportDialog';
import ActivityWeeklyOverview from '@/components/ActivityWeeklyOverview';
import { ActivityListSection } from '@/components/activities/ActivityListSection';
import { MobileActionMenu } from '@/components/activities/MobileActionMenu';
import { RunningDetailDialog } from '@/components/activities/RunningDetailDialog';
import { WeightTrainingForm } from '@/components/activities/WeightTrainingForm';
import { useTodaySync } from '@/components/activities/useTodaySync';
import { isRunningType } from '@/components/activities/utils';

export default function Activities() {
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState<ActivityFilter>({});
  const [draft, setDraft] = useState<ActivityFilter>({});
  const [showForm, setShowForm] = useState(false);
  const [showHevyDialog, setShowHevyDialog] = useState(false);
  const [editActivity, setEditActivity] = useState<ActivityType | undefined>();
  const [runningModalOpen, setRunningModalOpen] = useState(false);
  const [selectedRunningId, setSelectedRunningId] = useState<number | null>(null);
  const [copied, setCopied] = useState(false);

  const { triggerSync, isPending: todaySyncPending, isPolling: todaySyncPolling } = useTodaySync();

  const { data, isLoading } = useQuery({
    queryKey: ['activities', page, filter],
    queryFn: () => api.activities.list(page, 20, filter),
  });

  const { data: exerciseNames } = useQuery({
    queryKey: ['exerciseNames'],
    queryFn: api.activities.getExerciseNames,
  });

  const applyFilter = () => {
    setFilter(draft);
    setPage(0);
  };

  const resetFilter = () => {
    setDraft({});
    setFilter({});
    setPage(0);
  };

  const hasActiveFilter = Object.values(filter).some((v) => v !== undefined && v !== '');

  const handleSortChange = (sortValue: string) => {
    const [sortBy, sortDir] = sortValue.split(',');
    const next = { ...draft, sortBy, sortDir };
    setDraft(next);
  };

  const currentSortValue = draft.sortBy ? `${draft.sortBy},${draft.sortDir || 'desc'}` : 'startTime,desc';

  const handleRowClick = (activity: ActivityType) => {
    if (isRunningType(activity.activityType)) {
      setSelectedRunningId(activity.id);
      setRunningModalOpen(true);
      return;
    }
    if (activity.sourceType === 'MANUAL') {
      setEditActivity(activity);
      setShowForm(true);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const handleCloseForm = () => {
    setShowForm(false);
    setEditActivity(undefined);
  };

  const handleCloseRunningModal = () => {
    setRunningModalOpen(false);
    setSelectedRunningId(null);
    setCopied(false);
  };

  const { data: runningDetail, isLoading: runningDetailLoading } = useQuery({
    queryKey: ['activity', selectedRunningId],
    queryFn: () => api.activities.get(selectedRunningId!),
    enabled: !!selectedRunningId,
  });

  const { data: runningLaps, isLoading: runningLapsLoading } = useQuery({
    queryKey: ['activity-laps', selectedRunningId],
    queryFn: () => api.activities.getLaps(selectedRunningId!),
    enabled: !!selectedRunningId,
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Activities</h2>
          <p className="text-muted-foreground">Your workout and activity history</p>
        </div>
        <div className="flex items-center gap-2">
          {/* Desktop action buttons */}
          <div className="hidden md:flex items-center gap-2">
            <Button
              size="sm"
              variant="outline"
              className="h-9 gap-1"
              onClick={() => triggerSync()}
              disabled={todaySyncPending || todaySyncPolling}
            >
              <RefreshCw className={`h-4 w-4 ${todaySyncPolling ? 'animate-spin' : ''}`} />
              오늘 동기화
            </Button>
            <Button
              size="sm"
              variant="outline"
              className="h-9 gap-1"
              onClick={() => setShowHevyDialog(true)}
            >
              <ClipboardPaste className="h-4 w-4" />
              Hevy 불러오기
            </Button>
            <Button
              size="sm"
              className="h-9 gap-1"
              onClick={() => {
                setEditActivity(undefined);
                setShowForm((s) => !s);
              }}
            >
              {showForm ? <X className="h-4 w-4" /> : <Plus className="h-4 w-4" />}
              {showForm ? '닫기' : '웨이트 기록'}
            </Button>
          </div>
          {/* Mobile action menu */}
          <MobileActionMenu
            onHevy={() => setShowHevyDialog(true)}
            onWeight={() => {
              setEditActivity(undefined);
              setShowForm((s) => !s);
            }}
            onSyncToday={() => triggerSync()}
            syncRunning={todaySyncPending || todaySyncPolling}
            showForm={showForm}
          />
        </div>
      </div>

      {showForm && <WeightTrainingForm onClose={handleCloseForm} editActivity={editActivity} />}

      <HevyImportDialog
        open={showHevyDialog}
        onOpenChange={setShowHevyDialog}
        exerciseNames={exerciseNames || []}
      />

      <Tabs defaultValue="overview" className="space-y-4">
        <TabsList className="flex h-auto flex-wrap justify-start">
          <TabsTrigger value="overview">Weekly Overview</TabsTrigger>
          <TabsTrigger value="list">Activity List</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="space-y-4">
          <ActivityWeeklyOverview onActivityClick={handleRowClick} />
        </TabsContent>

        <TabsContent value="list">
          <ActivityListSection
            data={data}
            isLoading={isLoading}
            draft={draft}
            setDraft={setDraft}
            applyFilter={applyFilter}
            resetFilter={resetFilter}
            hasActiveFilter={hasActiveFilter}
            currentSortValue={currentSortValue}
            handleSortChange={handleSortChange}
            page={page}
            setPage={setPage}
            handleRowClick={handleRowClick}
          />
        </TabsContent>
      </Tabs>

      {/* Running Detail Modal */}
      <RunningDetailDialog
        open={runningModalOpen}
        onOpenChange={setRunningModalOpen}
        runningDetail={runningDetail}
        runningDetailLoading={runningDetailLoading}
        runningLaps={runningLaps}
        runningLapsLoading={runningLapsLoading}
        copied={copied}
        setCopied={setCopied}
        onClose={handleCloseRunningModal}
      />
    </div>
  );
}
