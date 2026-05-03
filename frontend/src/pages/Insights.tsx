import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Lightbulb, Save, Bookmark, Trash2 } from 'lucide-react';

const categories = ['', '운동', '수면', '회복', '스트레스', '목표', '패턴'];
const feedbackStatuses = ['', 'CORRECT', 'UNCLEAR', 'WRONG', 'IMPORTANT'];

export default function Insights() {
  const queryClient = useQueryClient();
  const [category, setCategory] = useState('');
  const [feedbackStatus, setFeedbackStatus] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['insights', category, feedbackStatus],
    queryFn: () => api.insights.list(category || undefined, feedbackStatus || undefined),
  });

  const saveMutation = useMutation({
    mutationFn: (id: number) => api.insights.save(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['insights'] }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.insights.delete(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['insights'] }),
  });

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">Insights</h2>
        <p className="text-muted-foreground">AI-generated insights from your data</p>
      </div>

      <div className="flex flex-wrap gap-2">
        <select
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          className="rounded-md border border-input bg-background px-3 py-2 text-sm"
        >
          <option value="">All Categories</option>
          {categories.filter(Boolean).map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
        <select
          value={feedbackStatus}
          onChange={(e) => setFeedbackStatus(e.target.value)}
          className="rounded-md border border-input bg-background px-3 py-2 text-sm"
        >
          <option value="">All Feedback</option>
          {feedbackStatuses.filter(Boolean).map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
      </div>

      {isLoading ? (
        <div className="space-y-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-32" />
          ))}
        </div>
      ) : (
        <div className="space-y-4">
          {data?.length ? (
            data.map((insight) => (
              <Card key={insight.id}>
                <CardHeader className="pb-3">
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-2">
                      <Lightbulb className="h-4 w-4 text-amber-500" />
                      <CardTitle className="text-base">{insight.title}</CardTitle>
                    </div>
                    <div className="flex items-center gap-1">
                      {insight.isSaved && <Bookmark className="h-4 w-4 text-indigo-500" />}
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-7 w-7"
                        onClick={() => saveMutation.mutate(insight.id)}
                      >
                        <Save className="h-3.5 w-3.5" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-7 w-7"
                        onClick={() => deleteMutation.mutate(insight.id)}
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    </div>
                  </div>
                  <CardDescription>
                    {insight.modelProvider} · {insight.modelName} ·{' '}
                    {new Date(insight.createdAt).toLocaleDateString('ko-KR')}
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <p className="text-sm leading-relaxed text-muted-foreground">{insight.summary}</p>
                  <div className="mt-3 flex items-center gap-2">
                    {insight.category && <Badge variant="secondary">{insight.category}</Badge>}
                    {insight.confidence && (
                      <Badge variant="outline">
                        Confidence: {(insight.confidence * 100).toFixed(0)}%
                      </Badge>
                    )}
                    {insight.feedbackStatus && (
                      <Badge>{insight.feedbackStatus}</Badge>
                    )}
                  </div>
                </CardContent>
              </Card>
            ))
          ) : (
            <Card>
              <CardContent className="py-12 text-center">
                <Lightbulb className="mx-auto h-8 w-8 text-muted-foreground mb-3" />
                <p className="text-muted-foreground">No insights yet. Ask your data a question!</p>
              </CardContent>
            </Card>
          )}
        </div>
      )}
    </div>
  );
}
