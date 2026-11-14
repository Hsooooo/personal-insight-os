import { useMemo, useState } from 'react';
import { Table2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import type { GraphData } from '@/types';

const MAX_ROWS = 200;

/** 캔버스 그래프의 텍스트 대안: 요약 + 노드/관계 표 토글 */
export function GraphDataTable({ data, summary }: { data: GraphData; summary: string }) {
  const [open, setOpen] = useState(false);
  const labelById = useMemo(() => new Map(data.nodes.map((n) => [n.id, n.label])), [data.nodes]);

  return (
    <div className="mt-3 space-y-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs text-muted-foreground" aria-live="polite">
          {summary}
        </p>
        <Button
          variant="ghost"
          size="sm"
          className="h-7 gap-1 text-xs"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          aria-controls="graph-data-table"
        >
          <Table2 className="h-3.5 w-3.5" aria-hidden="true" />
          {open ? '표 숨기기' : '표로 보기'}
        </Button>
      </div>
      {open && (
        <div id="graph-data-table" className="grid max-h-80 gap-4 overflow-auto md:grid-cols-2">
          <table className="w-full border-collapse text-xs">
            <caption className="mb-1 text-left font-medium">노드 ({data.nodes.length})</caption>
            <thead>
              <tr className="bg-muted">
                <th scope="col" className="border px-2 py-1 text-left">유형</th>
                <th scope="col" className="border px-2 py-1 text-left">이름</th>
              </tr>
            </thead>
            <tbody>
              {data.nodes.slice(0, MAX_ROWS).map((n) => (
                <tr key={n.id}>
                  <td className="border px-2 py-1">{n.type}</td>
                  <td className="border px-2 py-1">{n.label}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <table className="w-full border-collapse text-xs">
            <caption className="mb-1 text-left font-medium">관계 ({data.relationships.length})</caption>
            <thead>
              <tr className="bg-muted">
                <th scope="col" className="border px-2 py-1 text-left">출발</th>
                <th scope="col" className="border px-2 py-1 text-left">관계</th>
                <th scope="col" className="border px-2 py-1 text-left">도착</th>
              </tr>
            </thead>
            <tbody>
              {data.relationships.slice(0, MAX_ROWS).map((r) => (
                <tr key={r.id}>
                  <td className="border px-2 py-1">{labelById.get(r.sourceId) ?? r.sourceId}</td>
                  <td className="border px-2 py-1">{r.type}</td>
                  <td className="border px-2 py-1">{labelById.get(r.targetId) ?? r.targetId}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {(data.nodes.length > MAX_ROWS || data.relationships.length > MAX_ROWS) && (
            <p className="text-xs text-muted-foreground md:col-span-2">처음 {MAX_ROWS}개 항목만 표시합니다.</p>
          )}
        </div>
      )}
    </div>
  );
}
