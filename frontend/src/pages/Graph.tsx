import { useCallback, useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { GitGraph, Filter, MousePointerClick, Eye, EyeOff } from 'lucide-react';
import type { GraphNode, GraphRelationship } from '@/types';

// Cytoscape type declarations
type Cy = import('cytoscape').Core;

const NODE_STYLES: Record<string, { color: string; size: number }> = {
  Person:       { color: '#6366f1', size: 52 },
  Activity:     { color: '#10b981', size: 40 },
  Sleep:        { color: '#8b5cf6', size: 38 },
  HealthMetric: { color: '#f43f5e', size: 36 },
  Race:         { color: '#f59e0b', size: 32 },
  Goal:         { color: '#ec4899', size: 38 },
  Insight:      { color: '#06b6d4', size: 38 },
};

const EDGE_COLORS: Record<string, string> = {
  PERFORMED: 'rgba(99, 102, 241, 0.5)',
  HAS_SLEEP: 'rgba(139, 92, 246, 0.5)',
  HAS_METRIC: 'rgba(244, 63, 94, 0.5)',
  TAGGED_AS: 'rgba(245, 158, 11, 0.5)',
  OCCURRED_ON: 'rgba(148, 163, 184, 0.5)',
  DERIVED_FROM: 'rgba(6, 182, 212, 0.5)',
  ANSWERED_BY: 'rgba(236, 72, 153, 0.5)',
  SUPPORTED_BY: 'rgba(16, 185, 129, 0.5)',
};

const NODE_TYPE_LABELS: Record<string, string> = {
  Person: 'Person',
  Activity: 'Activity',
  Sleep: 'Sleep',
  HealthMetric: 'HealthMetric',
  Race: 'Race',
  Goal: 'Goal',
  Insight: 'Insight',
};

const EDGE_TYPE_LABELS: Record<string, string> = {
  PERFORMED: 'PERFORMED',
  HAS_SLEEP: 'HAS_SLEEP',
  HAS_METRIC: 'HAS_METRIC',
  TAGGED_AS: 'TAGGED_AS',
  OCCURRED_ON: 'OCCURRED_ON',
  DERIVED_FROM: 'DERIVED_FROM',
  ANSWERED_BY: 'ANSWERED_BY',
  SUPPORTED_BY: 'SUPPORTED_BY',
};

interface FilterState {
  [key: string]: boolean;
}

function hexToRgba(hex: string, alpha: number): string {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

function buildStylesheet(): any[] {
  const base: any[] = [
    {
      selector: 'node',
      style: {
        'background-fill': 'solid',
        'label': 'data(label)',
        'shape': 'ellipse',
        'font-family': '"Pretendard", "Inter", -apple-system, BlinkMacSystemFont, sans-serif',
        'font-size': '12px',
        'font-weight': 500,
        'text-valign': 'bottom',
        'text-halign': 'center',
        'text-margin-y': 4,
        'color': '#1e293b',
        'text-background-color': '#f8fafc',
        'text-background-opacity': 0.85,
        'text-background-padding': '2px 5px',
        'text-background-shape': 'roundrectangle',
        'border-width': 1.5,
        'shadow-blur': 6,
        'shadow-color': 'rgba(0, 0, 0, 0.08)',
        'shadow-opacity': 1,
        'shadow-offset-y': 2,
        'transition-property': 'background-color, border-color, shadow-blur, border-width',
        'transition-duration': '0.15s',
      },
    },
  ];

  // Type-specific node styles (solid color + subtle border)
  const typeStyles = Object.entries(NODE_STYLES).map(([type, style]) => ({
    selector: `node[type = "${type}"]`,
    style: {
      'background-color': style.color,
      'width': style.size,
      'height': style.size,
      'border-color': hexToRgba(style.color, 0.25),
    },
  }));

  // Type-specific selected styles
  const selectedTypeStyles = Object.entries(NODE_STYLES).map(([type, style]) => ({
    selector: `node[type = "${type}"]:selected`,
    style: {
      'border-width': 2,
      'border-color': style.color,
      'border-opacity': 1,
      'shadow-blur': 12,
      'shadow-color': hexToRgba(style.color, 0.3),
      'shadow-offset-y': 0,
    },
  }));

  // Type-specific hover styles
  const hoverTypeStyles = Object.entries(NODE_STYLES).map(([type, style]) => ({
    selector: `node[type = "${type}"].hover`,
    style: {
      'border-width': 2,
      'border-color': style.color,
      'border-opacity': 0.8,
      'shadow-blur': 10,
      'shadow-color': hexToRgba(style.color, 0.2),
    },
  }));

  const edgeBase = {
    selector: 'edge',
    style: {
      'width': 1.2,
      'curve-style': 'bezier',
      'target-arrow-shape': 'vee',
      'arrow-scale': 0.7,
      'line-color': 'rgba(148, 163, 184, 0.4)',
      'target-arrow-color': 'rgba(148, 163, 184, 0.4)',
      'label': 'data(type)',
      'font-family': '"Pretendard", "Inter", -apple-system, BlinkMacSystemFont, sans-serif',
      'font-size': '9px',
      'font-weight': 500,
      'color': 'rgba(100, 116, 139, 0.6)',
      'text-background-color': '#f8fafc',
      'text-background-opacity': 0.85,
      'text-background-padding': '2px 5px',
      'text-background-shape': 'roundrectangle',
      'transition-property': 'line-color, target-arrow-color',
      'transition-duration': '0.15s',
    },
  };

  const edgeTypeStyles = Object.entries(EDGE_COLORS).map(([type, color]) => ({
    selector: `edge[type = "${type}"]`,
    style: {
      'line-color': color,
      'target-arrow-color': color,
      'color': color,
    },
  }));

  const taggedEdge = {
    selector: 'edge[type = "TAGGED_AS"]',
    style: {
      'line-style': 'dashed',
      'width': 1,
    },
  };

  const dimmed = {
    selector: '.dimmed',
    style: {
      'opacity': 0.08,
    },
  };

  return [...base, ...typeStyles, ...selectedTypeStyles, ...hoverTypeStyles, edgeBase, ...edgeTypeStyles, taggedEdge, dimmed];
}

function FilterCheckbox({
  label,
  color,
  checked,
  onChange,
}: {
  label: string;
  color: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <label className="flex items-center gap-2 cursor-pointer select-none group">
      <input
        type="checkbox"
        className="peer h-4 w-4 rounded border border-input accent-primary cursor-pointer"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      <div className="h-3 w-3 rounded-full shrink-0" style={{ backgroundColor: color }} />
      <span className="text-sm text-foreground group-hover:text-primary transition-colors">{label}</span>
    </label>
  );
}

function EdgeFilterCheckbox({
  label,
  color,
  checked,
  onChange,
}: {
  label: string;
  color: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <label className="flex items-center gap-2 cursor-pointer select-none group">
      <input
        type="checkbox"
        className="peer h-4 w-4 rounded border border-input accent-primary cursor-pointer"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      <div className="h-0.5 w-4 shrink-0" style={{ backgroundColor: color }} />
      <span className="text-sm text-foreground group-hover:text-primary transition-colors">{label}</span>
    </label>
  );
}

export default function Graph() {
  const { data, isLoading } = useQuery({
    queryKey: ['graph'],
    queryFn: api.graph.get,
  });

  const containerRef = useRef<HTMLDivElement>(null);
  const cyRef = useRef<Cy | null>(null);
  const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null);
  const [selectedEdge, setSelectedEdge] = useState<GraphRelationship | null>(null);

  const allNodeTypes = Array.from(new Set(data?.nodes.map((n) => n.type) || []));
  const allEdgeTypes = Array.from(new Set(data?.relationships.map((r) => r.type) || []));

  const [nodeFilters, setNodeFilters] = useState<FilterState>({});
  const [edgeFilters, setEdgeFilters] = useState<FilterState>({});

  useEffect(() => {
    if (data) {
      const nTypes = Array.from(new Set(data.nodes.map((n) => n.type)));
      const eTypes = Array.from(new Set(data.relationships.map((r) => r.type)));
      setNodeFilters(Object.fromEntries(nTypes.map((t) => [t, true])));
      setEdgeFilters(Object.fromEntries(eTypes.map((t) => [t, true])));
    }
  }, [data]);

  // Apply filters to Cytoscape
  useEffect(() => {
    const cy = cyRef.current;
    if (!cy || !data) return;

    cy.batch(() => {
      cy.nodes().forEach((n) => {
        const type = n.data('type') as string;
        const visible = nodeFilters[type] !== false;
        n.style('display', visible ? 'element' : 'none');
      });

      cy.edges().forEach((e) => {
        const type = e.data('type') as string;
        const typeVisible = edgeFilters[type] !== false;
        const sourceVisible = e.source().style('display') !== 'none';
        const targetVisible = e.target().style('display') !== 'none';
        e.style('display', typeVisible && sourceVisible && targetVisible ? 'element' : 'none');
      });
    });
  }, [nodeFilters, edgeFilters, data]);

  // Initialize Cytoscape
  useEffect(() => {
    if (!containerRef.current || !data || cyRef.current) return;

    let destroyed = false;

    const init = async () => {
      const cytoscape = (await import('cytoscape')).default;
      const fcose = (await import('cytoscape-fcose')).default;
      cytoscape.use(fcose);

      if (destroyed) return;

      const nodeIdMap = new Map(data.nodes.map((n) => [n.id, n]));
      const validRelationships = data.relationships.filter(
        (r) => nodeIdMap.has(r.sourceId) && nodeIdMap.has(r.targetId)
      );

      const elements: import('cytoscape').ElementDefinition[] = [
        ...data.nodes.map((n) => ({
          data: {
            id: n.id,
            label: n.label,
            type: n.type,
            ...n.properties,
          },
        })),
        ...validRelationships.map((r) => ({
          data: {
            id: r.id,
            source: r.sourceId,
            target: r.targetId,
            type: r.type,
            ...r.properties,
          },
        })),
      ];

      const cy = cytoscape({
        container: containerRef.current,
        elements,
        style: buildStylesheet() as import('cytoscape').StylesheetStyle[],
        layout: {
          name: 'fcose',
          padding: 30,
          nodeRepulsion: 4500,
          idealEdgeLength: 100,
          edgeElasticity: 0.45,
          nestingFactor: 0.1,
          gravity: 0.25,
          numIter: 2500,
          tile: true,
          tilingPaddingVertical: 10,
          tilingPaddingHorizontal: 10,
          animate: true,
          animationDuration: 600,
          fit: true,
        } as any,
        minZoom: 0.2,
        maxZoom: 3,
        wheelSensitivity: 0.3,
      });

      cyRef.current = cy;

      // Node click
      cy.on('tap', 'node', (evt) => {
        const node = evt.target;
        const nodeData = data.nodes.find((n) => n.id === node.id()) || null;
        setSelectedNode(nodeData);
        setSelectedEdge(null);

        cy.nodes().removeClass('dimmed');
        cy.edges().removeClass('dimmed');
        const neighborhood = node.neighborhood().add(node);
        cy.elements().not(neighborhood).addClass('dimmed');
      });

      // Edge click
      cy.on('tap', 'edge', (evt) => {
        const edge = evt.target;
        const edgeData = data.relationships.find((r) => r.id === edge.id()) || null;
        setSelectedEdge(edgeData);
        setSelectedNode(null);
      });

      // Background click
      cy.on('tap', (evt) => {
        if (evt.target === cy) {
          setSelectedNode(null);
          setSelectedEdge(null);
          cy.nodes().removeClass('dimmed');
          cy.edges().removeClass('dimmed');
        }
      });

      // Hover
      cy.on('mouseover', 'node', (evt) => {
        evt.target.addClass('hover');
      });
      cy.on('mouseout', 'node', (evt) => {
        evt.target.removeClass('hover');
      });
    };

    init();

    return () => {
      destroyed = true;
      if (cyRef.current) {
        cyRef.current.destroy();
        cyRef.current = null;
      }
    };
  }, [data]);

  const handleNodeFilterChange = useCallback((type: string, checked: boolean) => {
    setNodeFilters((prev) => ({ ...prev, [type]: checked }));
  }, []);

  const handleEdgeFilterChange = useCallback((type: string, checked: boolean) => {
    setEdgeFilters((prev) => ({ ...prev, [type]: checked }));
  }, []);

  const fitGraph = useCallback(() => {
    (cyRef.current as any)?.fit(30);
  }, []);

  const relayout = useCallback(() => {
    if (!cyRef.current) return;
    cyRef.current.layout({
      name: 'fcose',
      padding: 30,
      nodeRepulsion: 4500,
      idealEdgeLength: 100,
      edgeElasticity: 0.45,
      nestingFactor: 0.1,
      gravity: 0.25,
      numIter: 2500,
      tile: true,
      tilingPaddingVertical: 10,
      tilingPaddingHorizontal: 10,
      animate: true,
      animationDuration: 600,
      fit: true,
    } as any).run();
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">Personal Graph</h2>
        <p className="text-muted-foreground">Explore connections in your health data</p>
      </div>

      <div className="grid gap-4 lg:grid-cols-4">
        <Card className="lg:col-span-3">
          <CardHeader className="flex flex-row items-center gap-2 pb-2">
            <GitGraph className="h-5 w-5 text-indigo-500" />
            <CardTitle>Knowledge Graph</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Skeleton className="h-[500px]" />
            ) : (
              <div className="h-[500px] rounded-xl border border-[rgba(116,119,126,0.1)] bg-[#f8fafc] relative overflow-hidden">
                <div className="absolute inset-0" ref={containerRef} />
                {/* Zoom controls */}
                <div className="absolute top-[10px] right-[12px] flex gap-1 z-10">
                  <button
                    onClick={fitGraph}
                    className="w-7 h-7 rounded-md border border-[rgba(116,119,126,0.1)] bg-white/90 text-[#43474d] text-sm flex items-center justify-center hover:bg-white transition-colors"
                    title="Fit to screen"
                  >
                    ⊙
                  </button>
                  <button
                    onClick={relayout}
                    className="w-7 h-7 rounded-md border border-[rgba(116,119,126,0.1)] bg-white/90 text-[#43474d] text-sm flex items-center justify-center hover:bg-white transition-colors"
                    title="Re-layout"
                  >
                    ⟳
                  </button>
                </div>
                {/* Legend */}
                <div className="absolute bottom-3 left-3.5 flex flex-wrap gap-x-3.5 gap-y-1 items-center z-10 text-[0.78rem] text-[#43474d] bg-white/85 px-2.5 py-1 rounded-lg border border-[rgba(116,119,126,0.1)]">
                  {Object.entries(NODE_STYLES).map(([type, style]) => (
                    <span key={type} className="inline-flex items-center gap-1.5">
                      <span className="inline-block w-2.5 h-2.5 rounded-full" style={{ backgroundColor: style.color }} />
                      {NODE_TYPE_LABELS[type] || type}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        <div className="space-y-4">
          {/* Node Legend & Filter */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="flex items-center gap-2 text-sm">
                <Filter className="h-4 w-4" />
                Node Types
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {allNodeTypes.map((type) => {
                const style = NODE_STYLES[type];
                return (
                  <FilterCheckbox
                    key={type}
                    label={NODE_TYPE_LABELS[type] || type}
                    color={style?.color || '#64748b'}
                    checked={nodeFilters[type] !== false}
                    onChange={(checked) => handleNodeFilterChange(type, checked)}
                  />
                );
              })}
              {allNodeTypes.length === 0 && (
                <p className="text-xs text-muted-foreground">No nodes found</p>
              )}
            </CardContent>
          </Card>

          {/* Edge Legend & Filter */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="flex items-center gap-2 text-sm">
                <Filter className="h-4 w-4" />
                Relationships
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {allEdgeTypes.map((type) => (
                <EdgeFilterCheckbox
                  key={type}
                  label={EDGE_TYPE_LABELS[type] || type}
                  color={EDGE_COLORS[type] || '#94a3b8'}
                  checked={edgeFilters[type] !== false}
                  onChange={(checked) => handleEdgeFilterChange(type, checked)}
                />
              ))}
              {allEdgeTypes.length === 0 && (
                <p className="text-xs text-muted-foreground">No edges found</p>
              )}
            </CardContent>
          </Card>

          {/* Selected Node Detail */}
          {selectedNode && (
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="flex items-center gap-2 text-sm">
                  <MousePointerClick className="h-4 w-4" />
                  Node Detail
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-2">
                <div>
                  <span className="text-xs text-muted-foreground">Label</span>
                  <p className="text-sm font-medium">{selectedNode.label}</p>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-muted-foreground">Type</span>
                  <Badge
                    variant="outline"
                    style={{
                      borderColor: NODE_STYLES[selectedNode.type]?.color || '#64748b',
                      color: NODE_STYLES[selectedNode.type]?.color || '#64748b',
                    }}
                  >
                    {selectedNode.type}
                  </Badge>
                </div>
                <div>
                  <span className="text-xs text-muted-foreground">ID</span>
                  <p className="text-xs font-mono">{selectedNode.id}</p>
                </div>
                {Object.entries(selectedNode.properties || {}).length > 0 && (
                  <div>
                    <span className="text-xs text-muted-foreground">Properties</span>
                    <div className="mt-1 space-y-1">
                      {Object.entries(selectedNode.properties || {}).map(([k, v]) => (
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
          )}

          {/* Selected Edge Detail */}
          {selectedEdge && (
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="flex items-center gap-2 text-sm">
                  <MousePointerClick className="h-4 w-4" />
                  Edge Detail
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-2">
                <div>
                  <span className="text-xs text-muted-foreground">Type</span>
                  <p className="text-sm font-medium">{selectedEdge.type}</p>
                </div>
                <div>
                  <span className="text-xs text-muted-foreground">Source</span>
                  <p className="text-xs font-mono">{selectedEdge.sourceId}</p>
                </div>
                <div>
                  <span className="text-xs text-muted-foreground">Target</span>
                  <p className="text-xs font-mono">{selectedEdge.targetId}</p>
                </div>
                {selectedEdge.confidence !== undefined && (
                  <div>
                    <span className="text-xs text-muted-foreground">Confidence</span>
                    <p className="text-xs">{selectedEdge.confidence}</p>
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {/* Usage hint */}
          {!selectedNode && !selectedEdge && (
            <Card className="bg-muted/50">
              <CardContent className="pt-4 space-y-2">
                <p className="text-xs text-muted-foreground flex items-center gap-1">
                  <MousePointerClick className="h-3 w-3" />
                  노드를 클릭하면 연결된 관계가 하이라이트됩니다
                </p>
                <p className="text-xs text-muted-foreground flex items-center gap-1">
                  <Eye className="h-3 w-3" />
                  체크박스로 노드/관계 타입을 ON/OFF 할 수 있습니다
                </p>
                <p className="text-xs text-muted-foreground flex items-center gap-1">
                  <EyeOff className="h-3 w-3" />
                  빈 공간을 클릭하면 선택이 해제됩니다
                </p>
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
