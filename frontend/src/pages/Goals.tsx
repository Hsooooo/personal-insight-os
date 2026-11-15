import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Target, Plus, Trash2, Pencil, AlertTriangle, Flag } from 'lucide-react';
import type { Goal } from '@/types';
import { QueryError } from '@/components/common/QueryError';
import { GoalForm } from '@/components/goals/GoalForm';
import { GOAL_TEMPLATES, dDayLabel, daysUntil, goalProgressText, raceDistanceLabel } from '@/components/goals/raceGoal';

function paceBadgeVariant(pace?: string | null): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (pace) {
    case 'AHEAD':
      return 'default';
    case 'ON_TRACK':
      return 'secondary';
    case 'BEHIND':
      return 'destructive';
    default:
      return 'outline';
  }
}

function paceLabel(pace?: string | null): string {
  switch (pace) {
    case 'AHEAD':
      return '앞서감';
    case 'ON_TRACK':
      return '순항';
    case 'BEHIND':
      return '뒤처짐';
    case 'INSUFFICIENT_DATA':
      return '데이터 부족';
    case 'UNSUPPORTED':
      return '자동 추적 미지원';
    default:
      return pace || '-';
  }
}

function warningLabel(warning?: string | null): string | null {
  if (warning === 'OVERTRAINING_HINT') return '목표 대비 운동량이 과도합니다';
  if (warning === 'UNDERTRAINING_HINT') return '목표 대비 진행이 더딥니다';
  return null;
}

export default function Goals() {
  const queryClient = useQueryClient();
  const [isAdding, setIsAdding] = useState(false);
  const [editing, setEditing] = useState<Goal | null>(null);

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['goals'],
    queryFn: api.goals.list,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['goals'] });
    queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  };

  const createMutation = useMutation({
    mutationFn: api.goals.create,
    onSuccess: () => {
      invalidate();
      setIsAdding(false);
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, goal }: { id: number; goal: Partial<Goal> }) => api.goals.update(id, goal),
    onSuccess: () => {
      invalidate();
      setEditing(null);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.goals.delete(id),
    onSuccess: invalidate,
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Goals</h2>
          <p className="text-muted-foreground">실제 데이터와 연결된 목표 진행률을 추적합니다</p>
        </div>
        <Button
          onClick={() => {
            setEditing(null);
            setIsAdding(true);
          }}
        >
          <Plus className="mr-2 h-4 w-4" />
          Add Goal
        </Button>
      </div>

      {isAdding && (
        <GoalForm
          heading="New Goal"
          submitLabel="Create Goal"
          pending={createMutation.isPending}
          error={createMutation.error?.message}
          onSubmit={(goal) => createMutation.mutate(goal)}
          onCancel={() => setIsAdding(false)}
        />
      )}

      {editing && (
        <GoalForm
          key={editing.id}
          heading={`Edit: ${editing.title}`}
          submitLabel="Save"
          initial={editing}
          pending={updateMutation.isPending}
          error={updateMutation.error?.message}
          onSubmit={(goal) => updateMutation.mutate({ id: editing.id, goal })}
          onCancel={() => setEditing(null)}
        />
      )}

      {isLoading ? (
        <div className="space-y-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-24" />
          ))}
        </div>
      ) : isError ? (
        <QueryError error={error} onRetry={() => void refetch()} title="목표를 불러오지 못했습니다" />
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {data?.length ? (
            data.map((goal) => (
              <GoalCard
                key={goal.id}
                goal={goal}
                onEdit={(g) => {
                  setIsAdding(false);
                  setEditing(g);
                  window.scrollTo({ top: 0, behavior: 'smooth' });
                }}
                onDelete={(id) => deleteMutation.mutate(id)}
              />
            ))
          ) : (
            <Card className="md:col-span-2">
              <CardContent className="py-12 text-center">
                <Target className="mx-auto h-8 w-8 text-muted-foreground mb-3" />
                <p className="text-muted-foreground">No goals yet. Add your first goal!</p>
              </CardContent>
            </Card>
          )}
        </div>
      )}
    </div>
  );
}

function GoalCard({
  goal,
  onEdit,
  onDelete,
}: {
  goal: Goal;
  onEdit: (goal: Goal) => void;
  onDelete: (id: number) => void;
}) {
  const percent = Math.min(Math.max(Number(goal.progressPercent ?? 0), 0), 100);
  const warn = warningLabel(goal.warning);
  const template = GOAL_TEMPLATES.find((t) => t.value === goal.goalType);
  const isRace = goal.goalType === 'RACE';
  const dDay = isRace ? dDayLabel(daysUntil(goal.targetDate)) : null;
  const raceIncomplete = isRace && (goal.raceDistanceKm == null || goal.targetValue == null || !goal.targetDate);

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between pb-2">
        <div className="flex items-center gap-2">
          {isRace ? (
            <Flag className="h-5 w-5 text-amber-500" aria-hidden="true" />
          ) : (
            <Target className="h-5 w-5 text-indigo-500" aria-hidden="true" />
          )}
          <CardTitle className="text-base">{goal.title}</CardTitle>
        </div>
        <div className="flex shrink-0 items-center">
          <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => onEdit(goal)} aria-label={`목표 ${goal.title} 수정`}>
            <Pencil className="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
          <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => onDelete(goal.id)} aria-label={`목표 ${goal.title} 삭제`}>
            <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {goal.description && <p className="text-sm text-muted-foreground">{goal.description}</p>}
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="secondary">{template?.label || goal.goalType}</Badge>
          {isRace && goal.raceDistanceKm != null && <Badge variant="outline">{raceDistanceLabel(goal.raceDistanceKm)}</Badge>}
          {dDay && (
            <Badge variant="outline">
              {dDay} · {goal.targetDate}
            </Badge>
          )}
          <Badge variant={goal.status === 'ACTIVE' ? 'default' : 'secondary'}>{goal.status}</Badge>
          {goal.progressSupported && (
            <Badge variant={paceBadgeVariant(goal.paceStatus)}>{paceLabel(goal.paceStatus)}</Badge>
          )}
        </div>

        {raceIncomplete ? (
          <p className="text-xs text-muted-foreground">
            대회 거리·목표 기록·대회일을 입력하면 예상 기록을 계산합니다.{' '}
            <button type="button" className="underline underline-offset-2" onClick={() => onEdit(goal)}>
              지금 입력
            </button>
          </p>
        ) : goal.progressSupported && goal.targetValue != null ? (
          <div className="space-y-2">
            <div className="flex items-baseline justify-between text-sm">
              <span className="text-muted-foreground">{goalProgressText(goal)}</span>
              <span className="font-medium">
                {goal.progressPercent != null ? `${Number(goal.progressPercent).toFixed(0)}%` : '-'}
              </span>
            </div>
            <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
              <div
                className="h-full rounded-full bg-indigo-500 transition-all"
                style={{ width: `${percent}%` }}
              />
            </div>
            {isRace && goal.currentValue == null && (
              <p className="text-xs text-muted-foreground">최근 6주 러닝 기록이 부족해 예상 기록을 계산하지 못했습니다.</p>
            )}
            {goal.projectedDate && (
              <p className="text-xs text-muted-foreground">예상 달성일: {goal.projectedDate}</p>
            )}
          </div>
        ) : (
          <p className="text-xs text-muted-foreground">이 유형은 자동 진행률 추적이 아직 지원되지 않습니다.</p>
        )}

        {goal.blockers && goal.blockers.length > 0 && (
          <div className="space-y-1 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-800">
            <p className="font-medium">이번 주 방해 요인</p>
            <ul className="list-disc space-y-0.5 pl-4">
              {goal.blockers.map((b) => (
                <li key={b}>{b}</li>
              ))}
            </ul>
          </div>
        )}

        {warn && (
          <div className="flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
            <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
            <span>{warn}</span>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
