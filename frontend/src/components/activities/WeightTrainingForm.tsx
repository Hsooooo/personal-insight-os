import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import { Dumbbell, Plus, Trash2, X } from 'lucide-react';
import type { Activity as ActivityType, WeightTrainingRequest } from '@/types';
import { BODY_PARTS, type ExerciseForm } from './utils';

export function WeightTrainingForm({
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
