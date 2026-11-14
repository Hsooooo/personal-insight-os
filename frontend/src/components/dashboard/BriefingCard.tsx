import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Newspaper, RefreshCw } from 'lucide-react';
import { api } from '@/lib/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import type { Insight } from '@/types';

export function BriefingCard({ briefing }: { briefing: Insight | null | undefined }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const generateBriefing = useMutation({
    mutationFn: () => api.briefings.generate(true),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
  });

  const preview = briefing?.summary ? briefing.summary.split('\n').filter(Boolean).slice(0, 4).join('\n') : null;

  return (
    <Card className="border-indigo-200/60 bg-gradient-to-br from-indigo-50/80 to-background">
      <CardHeader className="flex flex-row items-start justify-between space-y-0 pb-3">
        <div>
          <CardTitle className="flex items-center gap-2 text-base">
            <Newspaper className="h-4 w-4 text-indigo-600" aria-hidden="true" />
            {briefing?.title || 'Weekly Briefing'}
          </CardTitle>
          <CardDescription className="mt-1">지난주 데이터 기반 자동 브리핑 · 목표·패턴 포함</CardDescription>
        </div>
        <Button
          size="sm"
          variant="outline"
          className="shrink-0 gap-1"
          disabled={generateBriefing.isPending}
          onClick={() => generateBriefing.mutate()}
        >
          <RefreshCw
            className={`h-3.5 w-3.5 ${generateBriefing.isPending ? 'animate-spin' : ''}`}
            aria-hidden="true"
          />
          {briefing ? 'Regenerate' : 'Generate'}
        </Button>
      </CardHeader>
      <CardContent>
        {preview ? (
          <div className="space-y-3">
            <pre className="whitespace-pre-wrap font-sans text-sm leading-relaxed text-foreground/90">{preview}</pre>
            <div className="flex items-center gap-2">
              <Badge variant="secondary">WEEKLY_BRIEFING</Badge>
              {briefing?.confidence != null && (
                <span className="text-xs text-muted-foreground">
                  Confidence: {(Number(briefing.confidence) * 100).toFixed(0)}%
                </span>
              )}
              <Button variant="link" className="h-auto p-0 text-xs" onClick={() => navigate('/insights')}>
                View full briefing
              </Button>
            </div>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">
            아직 주간 브리핑이 없습니다. Generate를 눌러 지난주 요약을 만들어 보세요.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
