import { describe, expect, it } from 'vitest';
import { summarizeGraph } from './graphSummary';
import type { GraphData } from '@/types';

describe('summarizeGraph', () => {
  it('counts nodes and relationships by type', () => {
    const data = {
      nodes: [
        { id: 'p', label: 'Me', type: 'Person', properties: {} },
        { id: 'a1', label: 'Run', type: 'Activity', properties: {} },
        { id: 'a2', label: 'Run 2', type: 'Activity', properties: {} },
      ],
      relationships: [
        { id: 'r1', sourceId: 'p', targetId: 'a1', type: 'PERFORMED', properties: {} },
        { id: 'r2', sourceId: 'p', targetId: 'a2', type: 'PERFORMED', properties: {} },
      ],
    } as unknown as GraphData;
    const s = summarizeGraph(data);
    expect(s.nodes).toEqual([
      { type: 'Activity', count: 2 },
      { type: 'Person', count: 1 },
    ]);
    expect(s.text).toBe('노드 3개(Activity 2개, Person 1개), 관계 2개(PERFORMED 2개).');
  });

  it('handles empty graphs', () => {
    expect(summarizeGraph({ nodes: [], relationships: [] } as unknown as GraphData).text).toBe('표시할 노드가 없습니다.');
  });
});
