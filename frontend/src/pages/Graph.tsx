import { useCallback, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { GitGraph } from 'lucide-react';
import { api } from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { QueryError } from '@/components/common/QueryError';
import { EmptyState } from '@/components/common/EmptyState';
import { GraphFilterBar } from '@/components/graph/GraphFilterBar';
import { GraphCanvas, type FilterState, type GraphSelection } from '@/components/graph/GraphCanvas';
import { GraphDataTable } from '@/components/graph/GraphDataTable';
import { GraphSidePanel } from '@/components/graph/GraphSidePanel';
import { summarizeGraph } from '@/components/graph/graphSummary';

const NO_SELECTION: GraphSelection = { node: null, edge: null };

export default function Graph() {
  const [days, setDays] = useState<number>(30);
  const [view, setView] = useState<string>('all');
  const [raceCategory, setRaceCategory] = useState<string>('');
  const [selection, setSelection] = useState<GraphSelection>(NO_SELECTION);
  const [nodeFilters, setNodeFilters] = useState<FilterState>({});
  const [edgeFilters, setEdgeFilters] = useState<FilterState>({});

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['graph', days, view, raceCategory],
    queryFn: ({ signal }) => api.graph.get(days, view, raceCategory || undefined, { signal }),
  });

  const nodeTypes = useMemo(() => Array.from(new Set(data?.nodes.map((n) => n.type) ?? [])), [data]);
  const edgeTypes = useMemo(() => Array.from(new Set(data?.relationships.map((r) => r.type) ?? [])), [data]);
  const summary = useMemo(() => (data ? summarizeGraph(data).text : ''), [data]);

  // 데이터가 바뀌면 모든 타입을 다시 표시한다 (렌더 중 상태 조정 — effect 연쇄 렌더 방지).
  // 필터 값이 없으면(!== false) 표시로 간주하므로 빈 객체 = 전체 표시.
  const [filtersData, setFiltersData] = useState(data);
  if (filtersData !== data) {
    setFiltersData(data);
    setNodeFilters({});
    setEdgeFilters({});
  }

  const handleNodeFilterChange = useCallback((type: string, checked: boolean) => {
    setNodeFilters((prev) => ({ ...prev, [type]: checked }));
  }, []);

  const handleEdgeFilterChange = useCallback((type: string, checked: boolean) => {
    setEdgeFilters((prev) => ({ ...prev, [type]: checked }));
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">Personal Graph</h2>
        <p className="text-muted-foreground">Explore connections in your health data</p>
      </div>

      <GraphFilterBar
        days={days}
        onDaysChange={setDays}
        view={view}
        onViewChange={setView}
        raceCategory={raceCategory}
        onRaceCategoryChange={setRaceCategory}
      />

      <div className="grid gap-4 lg:grid-cols-4">
        <Card className="lg:col-span-3">
          <CardHeader className="flex flex-row items-center gap-2 pb-2">
            <GitGraph className="h-5 w-5 text-indigo-500" aria-hidden="true" />
            <CardTitle>Knowledge Graph</CardTitle>
          </CardHeader>
          <CardContent>
            {isPending ? (
              <Skeleton className="h-[500px]" />
            ) : isError ? (
              <QueryError error={error} onRetry={() => void refetch()} className="h-[500px]" />
            ) : data.nodes.length === 0 ? (
              <EmptyState
                icon={GitGraph}
                className="h-[500px]"
                message="선택한 기간에 표시할 그래프 데이터가 없습니다. 데이터를 동기화하거나 기간을 늘려 보세요."
              />
            ) : (
              <>
                <GraphCanvas
                  data={data}
                  nodeFilters={nodeFilters}
                  edgeFilters={edgeFilters}
                  onSelect={setSelection}
                  summary={summary}
                />
                <GraphDataTable data={data} summary={summary} />
              </>
            )}
          </CardContent>
        </Card>

        <GraphSidePanel
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          nodeFilters={nodeFilters}
          edgeFilters={edgeFilters}
          onNodeFilterChange={handleNodeFilterChange}
          onEdgeFilterChange={handleEdgeFilterChange}
          selectedNode={selection.node}
          selectedEdge={selection.edge}
        />
      </div>
    </div>
  );
}
