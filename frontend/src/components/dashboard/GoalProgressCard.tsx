import { useNavigate } from 'react-router-dom';
import { Target } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import type { Goal } from '@/types';
import { paceLabel } from './paceLabel';

export function GoalProgressCard({ goals }: { goals: Goal[] | undefined }) {
  const navigate = useNavigate();
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0">
        <CardTitle className="flex items-center gap-2">
          <Target className="h-4 w-4 text-indigo-500" aria-hidden="true" />
          Goal Progress
        </CardTitle>
        <Button variant="link" className="h-auto p-0 text-xs" onClick={() => navigate('/goals')}>
          Manage
        </Button>
      </CardHeader>
      <CardContent className="space-y-3">
        {goals?.length ? (
          goals.slice(0, 3).map((goal) => {
            const percent = Math.min(Math.max(Number(goal.progressPercent ?? 0), 0), 100);
            return (
              <div key={goal.id} className="space-y-1.5">
                <div className="flex items-center justify-between gap-2 text-sm">
                  <span className="truncate font-medium">{goal.title}</span>
                  <Badge variant="outline" className="shrink-0 text-[10px]">
                    {paceLabel(goal.paceStatus)}
                  </Badge>
                </div>
                {goal.progressSupported ? (
                  <>
                    <div
                      className="h-1.5 w-full overflow-hidden rounded-full bg-muted"
                      role="progressbar"
                      aria-label={`${goal.title} 진행률`}
                      aria-valuemin={0}
                      aria-valuemax={100}
                      aria-valuenow={Math.round(percent)}
                    >
                      <div className="h-full rounded-full bg-indigo-500" style={{ width: `${percent}%` }} />
                    </div>
                    <p className="text-xs text-muted-foreground">
                      {goal.currentValue ?? '-'}
                      {goal.targetUnit ? ` ${goal.targetUnit}` : ''}
                      {' / '}
                      {goal.targetValue}
                      {goal.targetUnit ? ` ${goal.targetUnit}` : ''}
                      {goal.progressPercent != null ? ` · ${Number(goal.progressPercent).toFixed(0)}%` : ''}
                    </p>
                  </>
                ) : (
                  <p className="text-xs text-muted-foreground">자동 추적 미지원</p>
                )}
              </div>
            );
          })
        ) : (
          <p className="text-sm text-muted-foreground">목표가 없습니다. Goals에서 주간 러닝·수면 목표를 추가해 보세요.</p>
        )}
      </CardContent>
    </Card>
  );
}
