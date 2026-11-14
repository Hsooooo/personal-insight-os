const DAY_OPTIONS = [
  { value: 7, label: '7일' },
  { value: 14, label: '14일' },
  { value: 30, label: '30일' },
  { value: 0, label: '전체' },
];

const VIEW_OPTIONS = [
  { key: 'activities', label: '활동' },
  { key: 'condition', label: '컨디션' },
  { key: 'all', label: '통합' },
];

function segmentClass(active: boolean) {
  return `px-3 py-1 text-sm rounded-md transition-colors ${
    active ? 'bg-background shadow-sm text-foreground font-medium' : 'text-muted-foreground hover:text-foreground'
  }`;
}

interface GraphFilterBarProps {
  days: number;
  onDaysChange: (days: number) => void;
  view: string;
  onViewChange: (view: string) => void;
  raceCategory: string;
  onRaceCategoryChange: (value: string) => void;
}

export function GraphFilterBar({
  days,
  onDaysChange,
  view,
  onViewChange,
  raceCategory,
  onRaceCategoryChange,
}: GraphFilterBarProps) {
  return (
    <div className="flex flex-wrap items-center gap-3">
      <div className="flex items-center gap-1 rounded-lg bg-muted/50 p-1" role="group" aria-label="기간">
        {DAY_OPTIONS.map((d) => (
          <button
            key={d.value}
            type="button"
            onClick={() => onDaysChange(d.value)}
            aria-pressed={days === d.value}
            className={segmentClass(days === d.value)}
          >
            {d.label}
          </button>
        ))}
      </div>

      <div className="flex items-center gap-1 rounded-lg bg-muted/50 p-1" role="group" aria-label="보기">
        {VIEW_OPTIONS.map((v) => (
          <button
            key={v.key}
            type="button"
            onClick={() => onViewChange(v.key)}
            aria-pressed={view === v.key}
            className={segmentClass(view === v.key)}
          >
            {v.label}
          </button>
        ))}
      </div>

      {view !== 'condition' && (
        <select
          value={raceCategory}
          onChange={(e) => onRaceCategoryChange(e.target.value)}
          aria-label="레이스 종류"
          className="h-8 rounded-md border border-input bg-background px-2 text-sm"
        >
          <option value="">모든 레이스</option>
          <option value="5K">5K</option>
          <option value="10K">10K</option>
          <option value="HALF">하프</option>
          <option value="FULL">풀</option>
          <option value="CUSTOM">커스텀</option>
        </select>
      )}
    </div>
  );
}
