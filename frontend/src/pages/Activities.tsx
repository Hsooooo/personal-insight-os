import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';
import { Activity, Dumbbell, FilterX, Plus, Search, Trash2, X, ClipboardPaste, Copy, Footprints, SlidersHorizontal, MoreHorizontal } from 'lucide-react';
import { formatDateTime, formatDistance, formatDuration, formatPace, formatLapCopyText } from '@/lib/utils';
import type { Activity as ActivityType, ActivityFilter, WeightTrainingRequest, GarminActivityLap } from '@/types';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet';
import HevyImportDialog from '@/components/HevyImportDialog';

const TAG_PRESETS = ['5K / 레이스', '10K / 레이스', '하프 / 레이스', '풀 / 레이스'];

const ACTIVITY_TYPES = [
  { value: '', label: '모든 타입' },
  { value: 'running', label: '러닝' },
  { value: 'cycling', label: '사이클' },
  { value: 'swimming', label: '수영' },
  { value: 'WEIGHT_TRAINING', label: '웨이트' },
  { value: 'other', label: '기타' },
];

const BODY_PARTS = [
  { value: 'CHEST', label: '가슴' },
  { value: 'BACK', label: '등' },
  { value: 'LEGS', label: '하체' },
  { value: 'SHOULDER', label: '어깨' },
  { value: 'ARMS', label: '팔' },
  { value: 'CORE', label: '코어' },
];

const TAG_OPTIONS = [
  { value: '', label: '모든 태그' },
  { value: '__none__', label: '태그 없음' },
  ...TAG_PRESETS.map((t) => ({ value: t, label: t })),
];

const SORT_OPTIONS = [
  { value: 'startTime,desc', label: '최신순' },
  { value: 'startTime,asc', label: '오래된순' },
  { value: 'distance,desc', label: '거리 ↓' },
  { value: 'distance,asc', label: '거리 ↑' },
  { value: 'duration,desc', label: '시간 ↓' },
  { value: 'calories,desc', label: '칼로리 ↓' },
];

function getTagColor(tag: string | null): string {
  if (!tag) return '';
  if (tag.includes('5K')) return 'bg-blue-100 text-blue-700 hover:bg-blue-100';
  if (tag.includes('10K')) return 'bg-indigo-100 text-indigo-700 hover:bg-indigo-100';
  if (tag.includes('하프')) return 'bg-purple-100 text-purple-700 hover:bg-purple-100';
  if (tag.includes('풀')) return 'bg-orange-100 text-orange-700 hover:bg-orange-100';
  return 'bg-slate-100 text-slate-700 hover:bg-slate-100';
}

function isRunningType(type: string | null): boolean {
  if (!type) return false;
  const t = type.toLowerCase();
  return t === 'running' || t.includes('run') || t.includes('treadmill') || t.includes('track');
}

function getTypeBadge(type: string | null, sourceType: string) {
  const isManual = sourceType === 'MANUAL';
  const base = type || 'UNKNOWN';
  if (base === 'WEIGHT_TRAINING') {
    return (
      <Badge variant="outline" className="border-amber-300 text-amber-700 bg-amber-50">
        <Dumbbell className="mr-1 h-3 w-3" />
        웨이트
      </Badge>
    );
  }
  return (
    <Badge variant={isManual ? 'outline' : 'secondary'}>
      {isManual && <span className="mr-1 text-[10px]">수동</span>}
      {base}
    </Badge>
  );
}

function ActivityTagCell({ activity }: { activity: ActivityType }) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [customInput, setCustomInput] = useState(false);
  const [customValue, setCustomValue] = useState('');

  const updateTagMutation = useMutation({
    mutationFn: ({ id, tag }: { id: number; tag: string }) => api.activities.updateTag(id, tag),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['activities'] });
      setEditing(false);
      setCustomInput(false);
    },
  });

  if (editing) {
    if (customInput) {
      return (
        <TableCell>
          <div className="flex gap-1">
            <input
              autoFocus
              className="h-7 w-28 rounded border border-input px-2 text-xs"
              placeholder="태그 입력"
              value={customValue}
              onChange={(e) => setCustomValue(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && customValue.trim()) {
                  updateTagMutation.mutate({ id: activity.id, tag: customValue.trim() });
                }
                if (e.key === 'Escape') {
                  setCustomInput(false);
                }
              }}
            />
            <button
              className="h-7 rounded bg-primary px-2 text-xs text-primary-foreground"
              onClick={() => customValue.trim() && updateTagMutation.mutate({ id: activity.id, tag: customValue.trim() })}
            >
              저장
            </button>
            <button
              className="h-7 rounded border px-2 text-xs"
              onClick={() => setCustomInput(false)}
            >
              취소
            </button>
          </div>
        </TableCell>
      );
    }

    return (
      <TableCell>
        <select
          autoFocus
          className="h-7 w-32 rounded border border-input px-1 text-xs"
          onChange={(e) => {
            const value = e.target.value;
            if (value === '__custom__') {
              setCustomInput(true);
            } else if (value === '__remove__') {
              updateTagMutation.mutate({ id: activity.id, tag: '' });
            } else if (value) {
              updateTagMutation.mutate({ id: activity.id, tag: value });
            }
          }}
          onBlur={() => setEditing(false)}
          defaultValue=""
        >
          <option value="" disabled>태그 선택</option>
          {TAG_PRESETS.map((t) => (
            <option key={t} value={t}>{t}</option>
          ))}
          <option value="__custom__">직접 입력...</option>
          {activity.userTag && <option value="__remove__">태그 제거</option>}
        </select>
      </TableCell>
    );
  }

  return (
    <TableCell
      className="cursor-pointer"
      onClick={() => setEditing(true)}
      title="클릭하여 태그 변경"
    >
      {activity.userTag ? (
        <Badge className={getTagColor(activity.userTag)}>{activity.userTag}</Badge>
      ) : (
        <span className="text-xs text-muted-foreground hover:text-foreground">+ 태그</span>
      )}
    </TableCell>
  );
}

function FilterSelect({
  value,
  onChange,
  options,
  placeholder,
}: {
  value: string;
  onChange: (v: string) => void;
  options: { value: string; label: string }[];
  placeholder?: string;
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="h-8 rounded-md border border-input bg-background px-2 text-xs focus:outline-none focus:ring-1 focus:ring-ring"
    >
      {placeholder && <option value="" disabled>{placeholder}</option>}
      {options.map((o) => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </select>
  );
}

function MobileActivityCard({
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

function MobileFilterBar({
  draft,
  setDraft,
  applyFilter,
  resetFilter,
  hasActiveFilter,
  currentSortValue,
}: {
  draft: ActivityFilter;
  setDraft: (f: ActivityFilter) => void;
  applyFilter: () => void;
  resetFilter: () => void;
  hasActiveFilter: boolean;
  currentSortValue: string;
}) {
  const [showSearch, setShowSearch] = useState(false);

  const handleSortChange = (sortValue: string) => {
    const [sortBy, sortDir] = sortValue.split(',');
    setDraft({ ...draft, sortBy, sortDir });
  };

  return (
    <div className="space-y-2 md:hidden">
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
        <Button
          size="sm"
          variant={showSearch ? 'default' : 'outline'}
          className="h-8 px-2"
          onClick={() => setShowSearch((s) => !s)}
        >
          <Search className="h-3.5 w-3.5" />
        </Button>
        <Sheet>
          <SheetTrigger asChild>
            <Button size="sm" variant="outline" className="h-8 gap-1 px-2">
              <SlidersHorizontal className="h-3.5 w-3.5" />
              필터
            </Button>
          </SheetTrigger>
          <SheetContent side="bottom" className="h-auto max-h-[80vh]">
            <SheetHeader>
              <SheetTitle>고급 필터</SheetTitle>
            </SheetHeader>
            <div className="mt-4 space-y-4">
              <div className="space-y-2">
                <Label className="text-xs text-muted-foreground">기간</Label>
                <div className="flex items-center gap-2">
                  <Input
                    type="date"
                    value={draft.startTimeFrom || ''}
                    onChange={(e) => setDraft({ ...draft, startTimeFrom: e.target.value || undefined })}
                    className="h-9 text-xs"
                  />
                  <span className="text-xs text-muted-foreground">~</span>
                  <Input
                    type="date"
                    value={draft.startTimeTo || ''}
                    onChange={(e) => setDraft({ ...draft, startTimeTo: e.target.value || undefined })}
                    className="h-9 text-xs"
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label className="text-xs text-muted-foreground">거리 (m)</Label>
                <div className="flex items-center gap-2">
                  <Input
                    type="number"
                    placeholder="최소"
                    value={draft.minDistance || ''}
                    onChange={(e) => setDraft({ ...draft, minDistance: e.target.value || undefined })}
                    className="h-9 text-xs"
                  />
                  <span className="text-xs text-muted-foreground">~</span>
                  <Input
                    type="number"
                    placeholder="최대"
                    value={draft.maxDistance || ''}
                    onChange={(e) => setDraft({ ...draft, maxDistance: e.target.value || undefined })}
                    className="h-9 text-xs"
                  />
                </div>
              </div>
              <div className="flex gap-2 pt-2">
                <Button className="flex-1" size="sm" onClick={applyFilter}>
                  적용
                </Button>
                <Button variant="ghost" size="sm" onClick={resetFilter}>
                  초기화
                </Button>
              </div>
            </div>
          </SheetContent>
        </Sheet>
      </div>
      {showSearch && (
        <div className="flex items-center gap-2">
          <div className="relative flex-1">
            <Search className="absolute left-2 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="이름 검색"
              value={draft.activityName || ''}
              onChange={(e) => setDraft({ ...draft, activityName: e.target.value || undefined })}
              className="h-8 pl-7 text-xs"
              onKeyDown={(e) => e.key === 'Enter' && applyFilter()}
            />
          </div>
          <Button size="sm" variant="secondary" className="h-8 text-xs" onClick={applyFilter}>
            검색
          </Button>
        </div>
      )}
      <div className="flex items-center gap-2">
        <Button size="sm" variant="secondary" className="h-8 text-xs" onClick={applyFilter}>
          적용
        </Button>
        <Button size="sm" variant="ghost" className="h-8 text-xs" onClick={resetFilter}>
          초기화
        </Button>
        {hasActiveFilter && (
          <Badge variant="outline" className="text-xs">
            필터 적용중
          </Badge>
        )}
      </div>
    </div>
  );
}

function MobileActionMenu({
  onHevy,
  onWeight,
  showForm,
}: {
  onHevy: () => void;
  onWeight: () => void;
  showForm: boolean;
}) {
  const [open, setOpen] = useState(false);
  return (
    <div className="md:hidden">
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetTrigger asChild>
          <Button size="sm" className="h-9 gap-1">
            <Plus className="h-4 w-4" />
            기록
          </Button>
        </SheetTrigger>
        <SheetContent side="bottom" className="h-auto">
          <SheetHeader>
            <SheetTitle>새 기록</SheetTitle>
          </SheetHeader>
          <div className="mt-4 space-y-2">
            <Button
              variant="outline"
              className="w-full justify-start gap-2"
              onClick={() => {
                onHevy();
                setOpen(false);
              }}
            >
              <ClipboardPaste className="h-4 w-4" />
              Hevy 불러오기
            </Button>
            <Button
              variant="outline"
              className="w-full justify-start gap-2"
              onClick={() => {
                onWeight();
                setOpen(false);
              }}
            >
              {showForm ? <X className="h-4 w-4" /> : <Plus className="h-4 w-4" />}
              {showForm ? '기록 닫기' : '수동 웨이트 기록'}
            </Button>
          </div>
        </SheetContent>
      </Sheet>
    </div>
  );
}

interface ExerciseForm {
  name: string;
  sets: { reps: string; weightKg: string; durationSeconds: string }[];
}

function WeightTrainingForm({
  onClose,
  editActivity,
}: {
  onClose: () => void;
  editActivity?: ActivityType;
}) {
  const queryClient = useQueryClient();
  const isEdit = !!editActivity;

  const detail = editActivity?.weightTrainingDetail;
  const [name, setName] = useState(editActivity?.activityName || '');
  const [startTime, setStartTime] = useState(
    editActivity?.startTime ? editActivity.startTime.slice(0, 16) : ''
  );
  const [durationMin, setDurationMin] = useState(
    editActivity?.durationSeconds ? String(Math.round(editActivity.durationSeconds / 60)) : ''
  );
  const [avgHr, setAvgHr] = useState(editActivity?.averageHeartRate ? String(editActivity.averageHeartRate) : '');
  const [calories, setCalories] = useState(editActivity?.calories ? String(editActivity.calories) : '');
  const [bodyPart, setBodyPart] = useState(detail?.bodyPart || 'CHEST');

  const { data: exerciseNames } = useQuery({
    queryKey: ['exercise-names'],
    queryFn: api.activities.getExerciseNames,
    staleTime: 1000 * 60 * 5,
  });

  const [customNames, setCustomNames] = useState<Record<number, boolean>>({});

  const [exercises, setExercises] = useState<ExerciseForm[]>(
    detail?.exercises?.map((e) => ({
      name: e.name,
      sets: e.sets.map((s) => ({ reps: String(s.reps || ''), weightKg: String(s.weightKg || ''), durationSeconds: String(s.durationSeconds || '') })),
    })) || [{ name: '', sets: [{ reps: '', weightKg: '', durationSeconds: '' }] }]
  );

  const addExercise = () => {
    setExercises([...exercises, { name: '', sets: [{ reps: '', weightKg: '', durationSeconds: '' }] }]);
  };

  const removeExercise = (idx: number) => {
    setExercises(exercises.filter((_, i) => i !== idx));
  };

  const addSet = (exIdx: number) => {
    const next = [...exercises];
    next[exIdx].sets.push({ reps: '', weightKg: '', durationSeconds: '' });
    setExercises(next);
  };

  const removeSet = (exIdx: number, setIdx: number) => {
    const next = [...exercises];
    next[exIdx].sets = next[exIdx].sets.filter((_, i) => i !== setIdx);
    setExercises(next);
  };

  const updateExerciseName = (idx: number, value: string) => {
    const next = [...exercises];
    next[idx].name = value;
    setExercises(next);
  };

  const toggleCustomName = (idx: number) => {
    setCustomNames((prev) => ({ ...prev, [idx]: !prev[idx] }));
  };

  const updateSet = (exIdx: number, setIdx: number, field: 'reps' | 'weightKg' | 'durationSeconds', value: string) => {
    const next = [...exercises];
    next[exIdx].sets[setIdx][field] = value;
    setExercises(next);
  };

  const createMutation = useMutation({
    mutationFn: api.activities.createWeightTraining,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['activities'] });
      onClose();
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: WeightTrainingRequest) =>
      api.activities.updateWeightTraining(editActivity!.id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['activities'] });
      onClose();
    },
  });

  const deleteMutation = useMutation({
    mutationFn: api.activities.deleteWeightTraining,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['activities'] });
      onClose();
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const durationSeconds = durationMin ? parseInt(durationMin) * 60 : 0;
    const payload: WeightTrainingRequest = {
      activityName: name || 'Weight Training',
      startTime: startTime ? startTime + ":00" : new Date().toISOString(),
      durationSeconds,
      averageHeartRate: avgHr ? parseInt(avgHr) : undefined,
      calories: calories ? parseInt(calories) : undefined,
      bodyPart,
      exercises: exercises
        .filter((ex) => ex.name.trim())
        .map((ex) => ({
          name: ex.name.trim(),
          sets: ex.sets
            .filter((s) => s.reps || s.durationSeconds)
            .map((s) => ({
              reps: s.reps ? parseInt(s.reps) : undefined,
              weightKg: s.weightKg ? parseFloat(s.weightKg) : undefined,
              durationSeconds: s.durationSeconds ? parseInt(s.durationSeconds) : undefined,
            })),
        })),
    };

    if (isEdit) {
      updateMutation.mutate(payload);
    } else {
      createMutation.mutate(payload);
    }
  };

  return (
    <Card className="mb-6 border-amber-200 bg-amber-50/30">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base flex items-center gap-2">
            <Dumbbell className="h-4 w-4 text-amber-600" />
            {isEdit ? '웨이트 기록 수정' : '웨이트 기록 입력'}
          </CardTitle>
          <div className="flex items-center gap-2">
            {isEdit && (
              <Button
                type="button"
                size="sm"
                variant="destructive"
                className="h-7 text-xs"
                onClick={() => deleteMutation.mutate(editActivity.id)}
                disabled={deleteMutation.isPending}
              >
                <Trash2 className="mr-1 h-3 w-3" />
                삭제
              </Button>
            )}
            <Button type="button" size="sm" variant="ghost" className="h-7 text-xs" onClick={onClose}>
              <X className="mr-1 h-3 w-3" />
              취소
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <div className="space-y-1">
              <Label className="text-xs">운동 이름</Label>
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="예: 가슴 운동"
                className="h-8 text-xs"
                required
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">시작 시간</Label>
              <Input
                type="datetime-local"
                value={startTime}
                onChange={(e) => setStartTime(e.target.value)}
                className="h-8 text-xs"
                required
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">소요 시간 (분)</Label>
              <Input
                type="number"
                value={durationMin}
                onChange={(e) => setDurationMin(e.target.value)}
                placeholder="60"
                className="h-8 text-xs"
                required
                min={1}
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">운동 부위</Label>
              <select
                value={bodyPart}
                onChange={(e) => setBodyPart(e.target.value)}
                className="h-8 w-full rounded-md border border-input bg-background px-2 text-xs"
              >
                {BODY_PARTS.map((b) => (
                  <option key={b.value} value={b.value}>{b.label}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label className="text-xs">평균 심박 (bpm)</Label>
              <Input
                type="number"
                value={avgHr}
                onChange={(e) => setAvgHr(e.target.value)}
                placeholder="120"
                className="h-8 text-xs"
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">칼로리 (kcal)</Label>
              <Input
                type="number"
                value={calories}
                onChange={(e) => setCalories(e.target.value)}
                placeholder="350"
                className="h-8 text-xs"
              />
            </div>
          </div>

          <Separator className="my-2" />

          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <Label className="text-xs font-semibold">종목 및 세트</Label>
              <Button type="button" size="sm" variant="outline" className="h-7 text-xs" onClick={addExercise}>
                <Plus className="mr-1 h-3 w-3" />
                종목 추가
              </Button>
            </div>

            {exercises.map((ex, exIdx) => (
              <div key={exIdx} className="rounded-md border bg-background p-3 space-y-2">
                <div className="flex items-center gap-2">
                  {customNames[exIdx] ? (
                    <>
                      <Input
                        value={ex.name}
                        onChange={(e) => updateExerciseName(exIdx, e.target.value)}
                        placeholder="새 종목명 입력"
                        className="h-8 text-xs flex-1"
                      />
                      <Button
                        type="button"
                        size="sm"
                        variant="ghost"
                        className="h-8 text-xs px-2"
                        onClick={() => toggleCustomName(exIdx)}
                      >
                        목록에서 선택
                      </Button>
                    </>
                  ) : (
                    <>
                      <select
                        value={ex.name}
                        onChange={(e) => {
                          if (e.target.value === '__custom__') {
                            toggleCustomName(exIdx);
                            updateExerciseName(exIdx, '');
                          } else {
                            updateExerciseName(exIdx, e.target.value);
                          }
                        }}
                        className="h-8 flex-1 rounded-md border border-input bg-background px-2 text-xs"
                      >
                        <option value="">종목 선택</option>
                        {(exerciseNames || []).map((n) => (
                          <option key={n} value={n}>{n}</option>
                        ))}
                        <option value="__custom__">➕ 새 종목 입력</option>
                      </select>
                      <Button
                        type="button"
                        size="sm"
                        variant="ghost"
                        className="h-8 text-xs px-2"
                        onClick={() => toggleCustomName(exIdx)}
                      >
                        직접 입력
                      </Button>
                    </>
                  )}
                  {exercises.length > 1 && (
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      className="h-8 w-8 p-0 text-destructive"
                      onClick={() => removeExercise(exIdx)}
                    >
                      <X className="h-4 w-4" />
                    </Button>
                  )}
                </div>

                <div className="space-y-1">
                  {ex.sets.map((set, setIdx) => (
                    <div key={setIdx} className="flex items-center gap-2">
                      <span className="text-xs text-muted-foreground w-8">{setIdx + 1}세트</span>
                      <Input
                        type="number"
                        value={set.reps}
                        onChange={(e) => updateSet(exIdx, setIdx, 'reps', e.target.value)}
                        placeholder="반복"
                        className="h-7 w-20 text-xs"
                      />
                      <span className="text-xs text-muted-foreground">회</span>
                      <Input
                        type="number"
                        step="0.5"
                        value={set.weightKg}
                        onChange={(e) => updateSet(exIdx, setIdx, 'weightKg', e.target.value)}
                        placeholder="무게"
                        className="h-7 w-20 text-xs"
                      />
                      <span className="text-xs text-muted-foreground">kg</span>
                      <Input
                        type="number"
                        value={set.durationSeconds}
                        onChange={(e) => updateSet(exIdx, setIdx, 'durationSeconds', e.target.value)}
                        placeholder="초"
                        className="h-7 w-20 text-xs"
                      />
                      <span className="text-xs text-muted-foreground">초</span>
                      {ex.sets.length > 1 && (
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          className="h-7 w-7 p-0 text-destructive"
                          onClick={() => removeSet(exIdx, setIdx)}
                        >
                          <X className="h-3 w-3" />
                        </Button>
                      )}
                    </div>
                  ))}
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    className="h-7 text-xs"
                    onClick={() => addSet(exIdx)}
                  >
                    <Plus className="mr-1 h-3 w-3" />
                    세트 추가
                  </Button>
                </div>
              </div>
            ))}
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button type="submit" size="sm" className="h-8 text-xs" disabled={createMutation.isPending || updateMutation.isPending}>
              {isEdit ? '수정 저장' : '기록 저장'}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

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

      {/* Running Detail Modal */}
      <Dialog open={runningModalOpen} onOpenChange={setRunningModalOpen}>
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
            <Button size="sm" onClick={handleCloseRunningModal}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
