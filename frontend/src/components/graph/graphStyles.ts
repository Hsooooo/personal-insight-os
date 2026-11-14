import type { BaseLayoutOptions, StylesheetStyle } from 'cytoscape';

export const NODE_STYLES: Record<string, { color: string; size: number }> = {
  Person:       { color: '#6366f1', size: 52 },
  Activity:     { color: '#10b981', size: 40 },
  Sleep:        { color: '#8b5cf6', size: 38 },
  HealthMetric: { color: '#f43f5e', size: 36 },
  Race:         { color: '#f59e0b', size: 32 },
  Goal:         { color: '#ec4899', size: 38 },
  Insight:      { color: '#06b6d4', size: 38 },
  Question:     { color: '#64748b', size: 34 },
};

export const EDGE_COLORS: Record<string, string> = {
  PERFORMED: 'rgba(99, 102, 241, 0.5)',
  HAS_SLEEP: 'rgba(139, 92, 246, 0.5)',
  HAS_METRIC: 'rgba(244, 63, 94, 0.5)',
  TAGGED_AS: 'rgba(245, 158, 11, 0.5)',
  OCCURRED_ON: 'rgba(148, 163, 184, 0.5)',
  DERIVED_FROM: 'rgba(6, 182, 212, 0.5)',
  ANSWERED_BY: 'rgba(236, 72, 153, 0.5)',
  SUPPORTED_BY: 'rgba(16, 185, 129, 0.5)',
  HAS_GOAL: 'rgba(236, 72, 153, 0.45)',
  ASKED: 'rgba(100, 116, 139, 0.5)',
  HAS_INSIGHT: 'rgba(6, 182, 212, 0.45)',
};

export const NODE_TYPE_LABELS: Record<string, string> = {
  Person: 'Person',
  Activity: 'Activity',
  Sleep: 'Sleep',
  HealthMetric: 'HealthMetric',
  Race: 'Race',
  Goal: 'Goal',
  Insight: 'Insight',
  Question: 'Question',
};

export const EDGE_TYPE_LABELS: Record<string, string> = {
  PERFORMED: 'PERFORMED',
  HAS_SLEEP: 'HAS_SLEEP',
  HAS_METRIC: 'HAS_METRIC',
  TAGGED_AS: 'TAGGED_AS',
  OCCURRED_ON: 'OCCURRED_ON',
  DERIVED_FROM: 'DERIVED_FROM',
  ANSWERED_BY: 'ANSWERED_BY',
  SUPPORTED_BY: 'SUPPORTED_BY',
  HAS_GOAL: 'HAS_GOAL',
  ASKED: 'ASKED',
  HAS_INSIGHT: 'HAS_INSIGHT',
};

export const DEFAULT_NODE_COLOR = '#64748b';
export const DEFAULT_EDGE_COLOR = '#94a3b8';

/** cytoscape-fcose 레이아웃 옵션 (타입 정의가 없어 필요한 항목만 선언) */
export interface FcoseLayoutOptions extends BaseLayoutOptions {
  name: 'fcose';
  padding: number;
  nodeRepulsion: number;
  idealEdgeLength: number;
  edgeElasticity: number;
  nestingFactor: number;
  gravity: number;
  numIter: number;
  tile: boolean;
  tilingPaddingVertical: number;
  tilingPaddingHorizontal: number;
  animate: boolean;
  animationDuration: number;
  fit: boolean;
}

export const FCOSE_LAYOUT: FcoseLayoutOptions = {
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
};

export function hexToRgba(hex: string, alpha: number): string {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

// 참고: cytoscape 3.x는 shadow-* 노드 스타일을 지원하지 않아(무시되고 콘솔 경고만 발생) 제거했다.
export function buildStylesheet(): StylesheetStyle[] {
  const base: StylesheetStyle[] = [
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
        'transition-property': 'background-color, border-color, border-width',
        'transition-duration': 150, // ms (= 0.15s)
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
    },
  }));

  // Type-specific hover styles
  const hoverTypeStyles = Object.entries(NODE_STYLES).map(([type, style]) => ({
    selector: `node[type = "${type}"].hover`,
    style: {
      'border-width': 2,
      'border-color': style.color,
      'border-opacity': 0.8,
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
      'transition-duration': 150, // ms (= 0.15s)
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
