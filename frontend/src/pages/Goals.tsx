import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Target, Plus, Trash2, AlertTriangle } from 'lucide-react';
import type { Goal } from '@/types';

const GOAL_TEMPLATES = [
  { value: 'WEEKLY_RUN_DISTANCE', label: '주간 러닝 거리', unit: 'km', placeholder: '40' },
  { value: 'WEEKLY_ACTIVITY_COUNT', label: '주간 운동 횟수', unit: '회', placeholder: '4' },
  { value: 'SLEEP_HOURS', label: '평균 수면 시간', unit: 'h', placeholder: '7.5' },
  { value: 'WEIGHT_KG', label: '체중', unit: 'kg', placeholder: '70' },
] as const;

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
  const [newGoal, setNewGoal] = useState({
    title: '',
    goalType: 'WEEKLY_RUN_DISTANCE',
    description: '',
    targetValue: '',
    startDate: '',
    targetDate: '',
  });

  const { data, isLoading } = useQuery({
    queryKey: ['goals'],
    queryFn: api.goals.list,
  });

  const createMutation = useMutation({
    mutationFn: api.goals.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['goals'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      setIsAdding(false);
      setNewGoal({
        title: '',
        goalType: 'WEEKLY_RUN_DISTANCE',
        description: '',
        targetValue: '',
        startDate: '',
        targetDate: '',
      });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.goals.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['goals'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });

  const selectedTemplate = GOAL_TEMPLATES.find((t) => t.value === newGoal.goalType);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Goals</h2>
          <p className="text-muted-foreground">실제 데이터와 연결된 목표 진행률을 추적합니다</p>
        </div>
        <Button onClick={() => setIsAdding(true)}>
          <Plus className="mr-2 h-4 w-4" />
          Add Goal
        </Button>
      </div>

      {isAdding && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">New Goal</CardTitle>
          </CardHeader>
          <CardContent>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                createMutation.mutate({
                  title: newGoal.title,
                  goalType: newGoal.goalType,
                  description: newGoal.description || undefined,
                  targetValue: newGoal.targetValue ? Number(newGoal.targetValue) : undefined,
                  targetUnit: selectedTemplate?.unit,
                  startDate: newGoal.startDate || undefined,
                  targetDate: newGoal.targetDate || undefined,
                });
              }}
              className="space-y-3"
            >
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label>Title</Label>
                  <Input
                    value={newGoal.title}
                    onChange={(e) => setNewGoal({ ...newGoal, title: e.target.value })}
                    placeholder="e.g., 주간 러닝 40km"
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label>Type</Label>
                  <Select
                    value={newGoal.goalType}
                    onValueChange={(value) => setNewGoal({ ...newGoal, goalType: value })}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="목표 유형" />
                    </SelectTrigger>
                    <SelectContent>
                      {GOAL_TEMPLATES.map((t) => (
                        <SelectItem key={t.value} value={t.value}>
                          {t.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
              <div className="grid gap-3 sm:grid-cols-3">
                <div className="space-y-2">
                  <Label>Target ({selectedTemplate?.unit || 'value'})</Label>
                  <Input
                    type="number"
                    step="0.1"
                    value={newGoal.targetValue}
                    onChange={(e) => setNewGoal({ ...newGoal, targetValue: e.target.value })}
                    placeholder={selectedTemplate?.placeholder}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label>Start date</Label>
                  <Input
                    type="date"
                    value={newGoal.startDate}
                    onChange={(e) => setNewGoal({ ...newGoal, startDate: e.target.value })}
                  />
                </div>
                <div className="space-y-2">
                  <Label>Target date</Label>
                  <Input
                    type="date"
                    value={newGoal.targetDate}
                    onChange={(e) => setNewGoal({ ...newGoal, targetDate: e.target.value })}
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label>Description</Label>
                <Input
                  value={newGoal.description}
                  onChange={(e) => setNewGoal({ ...newGoal, description: e.target.value })}
                  placeholder="Describe your goal"
                />
              </div>
              <div className="flex gap-2">
                <Button type="submit" disabled={createMutation.isPending}>
                  Create Goal
                </Button>
                <Button variant="outline" type="button" onClick={() => setIsAdding(false)}>
                  Cancel
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {isLoading ? (
        <div className="space-y-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-24" />
          ))}
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {data?.length ? (
            data.map((goal) => <GoalCard key={goal.id} goal={goal} onDelete={(id) => deleteMutation.mutate(id)} />)
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

function GoalCard({ goal, onDelete }: { goal: Goal; onDelete: (id: number) => void }) {
  const percent = Math.min(Math.max(goal.progressPercent ?? 0, 0), 100);
  const warn = warningLabel(goal.warning);
  const template = GOAL_TEMPLATES.find((t) => t.value === goal.goalType);

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between pb-2">
        <div className="flex items-center gap-2">
          <Target className="h-5 w-5 text-indigo-500" />
          <CardTitle className="text-base">{goal.title}</CardTitle>
        </div>
        <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => onDelete(goal.id)}>
          <Trash2 className="h-3.5 w-3.5" />
        </Button>
      </CardHeader>
      <CardContent className="space-y-3">
        {goal.description && <p className="text-sm text-muted-foreground">{goal.description}</p>}
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="secondary">{template?.label || goal.goalType}</Badge>
          <Badge variant={goal.status === 'ACTIVE' ? 'default' : 'secondary'}>{goal.status}</Badge>
          {goal.progressSupported && (
            <Badge variant={paceBadgeVariant(goal.paceStatus)}>{paceLabel(goal.paceStatus)}</Badge>
          )}
        </div>

        {goal.progressSupported && goal.targetValue != null ? (
          <div className="space-y-2">
            <div className="flex items-baseline justify-between text-sm">
              <span className="text-muted-foreground">
                {goal.currentValue ?? '-'}
                {goal.targetUnit ? ` ${goal.targetUnit}` : ''}
                {' / '}
                {goal.targetValue}
                {goal.targetUnit ? ` ${goal.targetUnit}` : ''}
              </span>
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
