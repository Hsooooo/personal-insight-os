export type SyncRange = 'incremental' | 'full-3' | 'full-6' | 'full-12' | 'full-all';

export const SYNC_RANGE_OPTIONS: { value: SyncRange; label: string }[] = [
  { value: 'incremental', label: 'Incremental (last 7 days)' },
  { value: 'full-3', label: 'Full - Last 3 months' },
  { value: 'full-6', label: 'Full - Last 6 months' },
  { value: 'full-12', label: 'Full - Last 1 year' },
  { value: 'full-all', label: 'Full - All data' },
];

export function isSyncRange(value: string): value is SyncRange {
  return SYNC_RANGE_OPTIONS.some((o) => o.value === value);
}

/** KST(Asia/Seoul) 기준 YYYY-MM-DD */
export function kstDate(date: Date): string {
  return date.toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' });
}

export interface SyncRequestParams {
  syncType: 'FULL' | 'INCREMENTAL';
  dateFrom: string | undefined;
  dateTo: string;
}

/** 동기화 범위 선택값 → API 파라미터 (KST 기준) */
export function buildSyncRequest(range: SyncRange, now: Date = new Date()): SyncRequestParams {
  const dateTo = kstDate(now);
  if (range === 'full-all') {
    return { syncType: 'FULL', dateFrom: undefined, dateTo };
  }
  if (range === 'incremental') {
    const d = new Date(now);
    d.setDate(d.getDate() - 7);
    return { syncType: 'INCREMENTAL', dateFrom: kstDate(d), dateTo };
  }
  const months = parseInt(range.replace('full-', ''), 10);
  const d = new Date(now);
  d.setMonth(d.getMonth() - months);
  return { syncType: 'FULL', dateFrom: kstDate(d), dateTo };
}
