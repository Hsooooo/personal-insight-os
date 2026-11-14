import { useNavigate } from 'react-router-dom';
import { Lightbulb } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import type { Insight } from '@/types';

export function RecentInsightsCard({ insights }: { insights: Insight[] | undefined }) {
  const navigate = useNavigate();
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Lightbulb className="h-4 w-4 text-amber-500" aria-hidden="true" />
          Recent Insights
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {insights?.length ? (
          insights.slice(0, 3).map((insight) => (
            <button
              type="button"
              key={insight.id}
              className="block w-full cursor-pointer rounded-lg border p-3 text-left transition-colors hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              onClick={() => navigate('/insights')}
            >
              <p className="line-clamp-2 text-sm font-medium">{insight.summary}</p>
              <div className="mt-2 flex items-center gap-2">
                <Badge variant={insight.category === 'ANOMALY_ALERT' ? 'destructive' : 'secondary'} className="text-xs">
                  {insight.category || 'Insight'}
                </Badge>
                <span className="text-xs text-muted-foreground">
                  Confidence: {insight.confidence ? `${(insight.confidence * 100).toFixed(0)}%` : '-'}
                </span>
              </div>
            </button>
          ))
        ) : (
          <p className="text-sm text-muted-foreground">No insights yet. Ask your data a question!</p>
        )}
      </CardContent>
    </Card>
  );
}
