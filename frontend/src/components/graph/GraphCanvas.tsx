import { useCallback, useEffect, useRef } from 'react';
import type { Core, ElementDefinition } from 'cytoscape';
import type { GraphData, GraphNode, GraphRelationship } from '@/types';
import { buildStylesheet, FCOSE_LAYOUT, NODE_STYLES, NODE_TYPE_LABELS } from './graphStyles';

export type FilterState = Record<string, boolean>;

export interface GraphSelection {
  node: GraphNode | null;
  edge: GraphRelationship | null;
}

interface GraphCanvasProps {
  data: GraphData;
  nodeFilters: FilterState;
  edgeFilters: FilterState;
  onSelect: (selection: GraphSelection) => void;
  /** 스크린리더용 요약 (캔버스 자체는 접근 불가하므로) */
  summary: string;
}

const CONTROL_BUTTON_CLASS =
  'w-7 h-7 rounded-md border border-[rgba(116,119,126,0.1)] bg-white/90 text-[#43474d] text-sm flex items-center justify-center hover:bg-white transition-colors';

export function GraphCanvas({ data, nodeFilters, edgeFilters, onSelect, summary }: GraphCanvasProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const cyRef = useRef<Core | null>(null);
  const onSelectRef = useRef(onSelect);

  useEffect(() => {
    onSelectRef.current = onSelect;
  }, [onSelect]);

  // Apply filters to Cytoscape
  useEffect(() => {
    const cy = cyRef.current;
    if (!cy) return;

    cy.batch(() => {
      cy.nodes().forEach((n) => {
        const type = n.data('type') as string;
        n.style('display', nodeFilters[type] !== false ? 'element' : 'none');
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
    const container = containerRef.current;
    if (!container || cyRef.current) return;

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

      const elements: ElementDefinition[] = [
        ...data.nodes.map((n) => ({
          data: { id: n.id, label: n.label, type: n.type, ...n.properties },
        })),
        ...validRelationships.map((r) => ({
          data: { id: r.id, source: r.sourceId, target: r.targetId, type: r.type, ...r.properties },
        })),
      ];

      const cy = cytoscape({
        container,
        elements,
        style: buildStylesheet(),
        layout: FCOSE_LAYOUT,
        minZoom: 0.2,
        maxZoom: 3,
        wheelSensitivity: 0.3,
      });

      cyRef.current = cy;

      cy.on('tap', 'node', (evt) => {
        const node = evt.target;
        onSelectRef.current({ node: nodeIdMap.get(node.id()) ?? null, edge: null });
        cy.nodes().removeClass('dimmed');
        cy.edges().removeClass('dimmed');
        const neighborhood = node.neighborhood().add(node);
        cy.elements().not(neighborhood).addClass('dimmed');
      });

      cy.on('tap', 'edge', (evt) => {
        const edge = evt.target;
        onSelectRef.current({ node: null, edge: data.relationships.find((r) => r.id === edge.id()) ?? null });
      });

      cy.on('tap', (evt) => {
        if (evt.target === cy) {
          onSelectRef.current({ node: null, edge: null });
          cy.nodes().removeClass('dimmed');
          cy.edges().removeClass('dimmed');
        }
      });

      cy.on('mouseover', 'node', (evt) => {
        evt.target.addClass('hover');
      });
      cy.on('mouseout', 'node', (evt) => {
        evt.target.removeClass('hover');
      });
    };

    void init();

    return () => {
      destroyed = true;
      if (cyRef.current) {
        cyRef.current.destroy();
        cyRef.current = null;
      }
    };
  }, [data]);

  const fitGraph = useCallback(() => {
    cyRef.current?.fit(undefined, 30);
  }, []);

  const relayout = useCallback(() => {
    cyRef.current?.layout(FCOSE_LAYOUT).run();
  }, []);

  return (
    <div className="relative h-[500px] overflow-hidden rounded-xl border border-[rgba(116,119,126,0.1)] bg-[#f8fafc]">
      <div className="absolute inset-0" ref={containerRef} role="img" aria-label={`지식 그래프 시각화. ${summary}`} />
      {/* Zoom controls */}
      <div className="absolute right-[12px] top-[10px] z-10 flex gap-1">
        <button type="button" onClick={fitGraph} className={CONTROL_BUTTON_CLASS} title="Fit to screen" aria-label="화면에 맞추기">
          <span aria-hidden="true">⊙</span>
        </button>
        <button type="button" onClick={relayout} className={CONTROL_BUTTON_CLASS} title="Re-layout" aria-label="다시 배치">
          <span aria-hidden="true">⟳</span>
        </button>
      </div>
      {/* Legend */}
      <div
        className="absolute bottom-3 left-3.5 z-10 flex flex-wrap items-center gap-x-3.5 gap-y-1 rounded-lg border border-[rgba(116,119,126,0.1)] bg-white/85 px-2.5 py-1 text-[0.78rem] text-[#43474d]"
        aria-hidden="true"
      >
        {Object.entries(NODE_STYLES).map(([type, style]) => (
          <span key={type} className="inline-flex items-center gap-1.5">
            <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: style.color }} />
            {NODE_TYPE_LABELS[type] || type}
          </span>
        ))}
      </div>
    </div>
  );
}
