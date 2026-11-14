import { describe, expect, it } from 'vitest';
import { buildSyncRequest, isSyncRange, kstDate } from './syncRange';

// 2026-11-14 15:30 UTC = 2026-11-15 00:30 KST
const NOW = new Date('2026-11-14T15:30:00Z');

describe('syncRange', () => {
  it('kstDate uses Asia/Seoul', () => {
    expect(kstDate(NOW)).toBe('2026-11-15');
  });

  it('full-all has no start date', () => {
    expect(buildSyncRequest('full-all', NOW)).toEqual({ syncType: 'FULL', dateFrom: undefined, dateTo: '2026-11-15' });
  });

  it('incremental syncs the last 7 days', () => {
    const r = buildSyncRequest('incremental', NOW);
    expect(r.syncType).toBe('INCREMENTAL');
    expect(r.dateTo).toBe('2026-11-15');
    expect(r.dateFrom).toBe('2026-11-08');
  });

  it('full-N goes back N months', () => {
    expect(buildSyncRequest('full-3', NOW).dateFrom).toBe('2026-08-15');
    expect(buildSyncRequest('full-12', NOW).dateFrom).toBe('2025-11-15');
  });

  it('isSyncRange guards select values', () => {
    expect(isSyncRange('full-6')).toBe(true);
    expect(isSyncRange('full-24')).toBe(false);
  });
});
