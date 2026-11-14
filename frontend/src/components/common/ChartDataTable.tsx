export interface ChartColumn<Row> {
  key: keyof Row & string;
  label: string;
  format?: (value: Row[keyof Row & string], row: Row) => string;
}

interface ChartDataTableProps<Row> {
  caption: string;
  columns: ChartColumn<Row>[];
  rows: Row[];
}

function defaultFormat(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

/** 차트의 텍스트 대안: 펼쳐 볼 수 있는 데이터 표 (키보드/스크린리더 접근 가능) */
export function ChartDataTable<Row>({ caption, columns, rows }: ChartDataTableProps<Row>) {
  if (rows.length === 0) return null;
  return (
    <details className="mt-2 text-xs">
      <summary className="cursor-pointer select-none rounded-sm text-muted-foreground hover:text-foreground">
        데이터 표로 보기
      </summary>
      <div className="mt-2 max-h-64 overflow-auto">
        <table className="w-full border-collapse">
          <caption className="sr-only">{caption}</caption>
          <thead>
            <tr className="bg-muted">
              {columns.map((c) => (
                <th key={c.key} scope="col" className="border px-2 py-1 text-left font-medium">
                  {c.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, i) => (
              <tr key={i}>
                {columns.map((c) => (
                  <td key={c.key} className="border px-2 py-1">
                    {c.format ? c.format(row[c.key], row) : defaultFormat(row[c.key])}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}
