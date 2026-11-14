import type { GraphData } from '@/types';

export interface TypeCount {
  type: string;
  count: number;
}

function countBy(items: { type: string }[]): TypeCount[] {
  const counts = new Map<string, number>();
  for (const item of items) counts.set(item.type, (counts.get(item.type) ?? 0) + 1);
  return Array.from(counts, ([type, count]) => ({ type, count })).sort((a, b) => b.count - a.count);
}

/** 스크린리더/텍스트 대안용 그래프 요약 */
export function summarizeGraph(data: GraphData): { nodes: TypeCount[]; edges: TypeCount[]; text: string } {
  const nodes = countBy(data.nodes);
  const edges = countBy(data.relationships);
  const nodeText = nodes.map((n) => `${n.type} ${n.count}개`).join(', ');
  const edgeText = edges.map((e) => `${e.type} ${e.count}개`).join(', ');
  const text =
    data.nodes.length === 0
      ? '표시할 노드가 없습니다.'
      : `노드 ${data.nodes.length}개(${nodeText}), 관계 ${data.relationships.length}개${edgeText ? `(${edgeText})` : ''}.`;
  return { nodes, edges, text };
}
