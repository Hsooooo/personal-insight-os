import { Eye, EyeOff, Filter, MousePointerClick } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import type { GraphNode, GraphRelationship } from '@/types';
import type { FilterState } from './GraphCanvas';
import {
  DEFAULT_EDGE_COLOR,
  DEFAULT_NODE_COLOR,
  EDGE_COLORS,
  EDGE_TYPE_LABELS,
  NODE_STYLES,
  NODE_TYPE_LABELS,
} from './graphStyles';
import { TypeFilterCheckbox } from './TypeFilterCheckbox';

interface TypeFilterCardProps {
  title: string;
  types: string[];
  filters: FilterState;
  onChange: (type: string, checked: boolean) => void;
  variant: 'node' | 'edge';
  emptyText: string;
}

function TypeFilterCard({ title, types, filters, onChange, variant, emptyText }: TypeFilterCardProps) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-sm">
          <Filter className="h-4 w-4" aria-hidden="true" />
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        <fieldset className="space-y-2">
          <legend className="sr-only">{title} 필터</legend>
          {types.map((type) => (
            <TypeFilterCheckbox
              key={type}
              variant={variant}
              label={(variant === 'node' ? NODE_TYPE_LABELS[type] : EDGE_TYPE_LABELS[type]) || type}
              color={
                variant === 'node'
                  ? NODE_STYLES[type]?.color || DEFAULT_NODE_COLOR
                  : EDGE_COLORS[type] || DEFAULT_EDGE_COLOR
              }
              checked={filters[type] !== false}
              onChange={(checked) => onChange(type, checked)}
            />
          ))}
        </fieldset>
        {types.length === 0 && <p className="text-xs text-muted-foreground">{emptyText}</p>}
      </CardContent>
    </Card>
  );
}

function NodeDetail({ node }: { node: GraphNode }) {
  const color = NODE_STYLES[node.type]?.color || DEFAULT_NODE_COLOR;
  const properties = Object.entries(node.properties || {});
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-sm">
          <MousePointerClick className="h-4 w-4" aria-hidden="true" />
          Node Detail
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        <div>
          <span className="text-xs text-muted-foreground">Label</span>
          <p className="text-sm font-medium">{node.label}</p>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs text-muted-foreground">Type</span>
          <Badge variant="outline" style={{ borderColor: color, color }}>
            {node.type}
          </Badge>
        </div>
        <div>
          <span className="text-xs text-muted-foreground">ID</span>
          <p className="font-mono text-xs">{node.id}</p>
        </div>
        {properties.length > 0 && (
          <div>
            <span className="text-xs text-muted-foreground">Properties</span>
            <div className="mt-1 space-y-1">
              {properties.map(([k, v]) => (
                <div key={k} className="text-xs">
                  <span className="text-muted-foreground">{k}:</span>{' '}
                  <span className="font-mono">{String(v).slice(0, 50)}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function EdgeDetail({ edge }: { edge: GraphRelationship }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-sm">
          <MousePointerClick className="h-4 w-4" aria-hidden="true" />
          Edge Detail
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        <div>
          <span className="text-xs text-muted-foreground">Type</span>
          <p className="text-sm font-medium">{edge.type}</p>
        </div>
        <div>
          <span className="text-xs text-muted-foreground">Source</span>
          <p className="font-mono text-xs">{edge.sourceId}</p>
        </div>
        <div>
          <span className="text-xs text-muted-foreground">Target</span>
          <p className="font-mono text-xs">{edge.targetId}</p>
        </div>
        {edge.confidence !== undefined && (
          <div>
            <span className="text-xs text-muted-foreground">Confidence</span>
            <p className="text-xs">{edge.confidence}</p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function UsageHint() {
  return (
    <Card className="bg-muted/50">
      <CardContent className="space-y-2 pt-4">
        <p className="flex items-center gap-1 text-xs text-muted-foreground">
          <MousePointerClick className="h-3 w-3" aria-hidden="true" />
          노드를 클릭하면 연결된 관계가 하이라이트됩니다
        </p>
        <p className="flex items-center gap-1 text-xs text-muted-foreground">
          <Eye className="h-3 w-3" aria-hidden="true" />
          체크박스로 노드/관계 타입을 ON/OFF 할 수 있습니다
        </p>
        <p className="flex items-center gap-1 text-xs text-muted-foreground">
          <EyeOff className="h-3 w-3" aria-hidden="true" />
          빈 공간을 클릭하면 선택이 해제됩니다
        </p>
      </CardContent>
    </Card>
  );
}

interface GraphSidePanelProps {
  nodeTypes: string[];
  edgeTypes: string[];
  nodeFilters: FilterState;
  edgeFilters: FilterState;
  onNodeFilterChange: (type: string, checked: boolean) => void;
  onEdgeFilterChange: (type: string, checked: boolean) => void;
  selectedNode: GraphNode | null;
  selectedEdge: GraphRelationship | null;
}

export function GraphSidePanel({
  nodeTypes,
  edgeTypes,
  nodeFilters,
  edgeFilters,
  onNodeFilterChange,
  onEdgeFilterChange,
  selectedNode,
  selectedEdge,
}: GraphSidePanelProps) {
  return (
    <div className="space-y-4">
      <TypeFilterCard
        title="Node Types"
        types={nodeTypes}
        filters={nodeFilters}
        onChange={onNodeFilterChange}
        variant="node"
        emptyText="No nodes found"
      />
      <TypeFilterCard
        title="Relationships"
        types={edgeTypes}
        filters={edgeFilters}
        onChange={onEdgeFilterChange}
        variant="edge"
        emptyText="No edges found"
      />
      <div aria-live="polite">
        {selectedNode && <NodeDetail node={selectedNode} />}
        {selectedEdge && <EdgeDetail edge={selectedEdge} />}
      </div>
      {!selectedNode && !selectedEdge && <UsageHint />}
    </div>
  );
}
