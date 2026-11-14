import { describe, expect, it } from 'vitest';
import type { FinanceTransaction } from '@/types';
import {
  addDays,
  cashflowAmount,
  deferredSpendingAmount,
  externalCashOutAmount,
  filterTransactionsByPeriod,
  formatDateOnly,
  maxDate,
  minDate,
  money,
  parseDateOnly,
  parseMoneyInput,
  percent,
  seoulDate,
  seoulTime,
  spendingAmount,
} from './utils';

function tx(partial: Partial<FinanceTransaction>): FinanceTransaction {
  return { id: 1, amount: 1000, flowType: '지출', transactionDate: '2026-11-10', ...partial } as FinanceTransaction;
}

describe('finance formatting', () => {
  it('money formats KRW and handles null', () => {
    expect(money(null)).toBe('-');
    expect(money(undefined)).toBe('-');
    expect(money(12345)).toMatch(/12,345/);
  });

  it('percent', () => {
    expect(percent(1, 0)).toBe('0%');
    expect(percent(1, 3)).toBe('33%');
  });

  it('parseMoneyInput strips commas and rejects garbage', () => {
    expect(parseMoneyInput('1,234,000')).toBe(1234000);
    expect(parseMoneyInput('  ')).toBe(0);
    expect(parseMoneyInput('abc')).toBe(0);
  });

  it('seoulDate / seoulTime convert to Asia/Seoul', () => {
    expect(seoulDate('2026-11-10')).toBe('2026-11-10');
    expect(seoulDate('2026-11-10T16:00:00Z')).toBe('2026-11-11');
    expect(seoulTime('2026-11-10T16:05:00Z')).toBe('01:05');
    expect(seoulDate(null)).toBe('');
    expect(seoulTime(undefined)).toBe('');
  });

  it('date-only helpers round trip', () => {
    const d = parseDateOnly('2026-02-28');
    expect(formatDateOnly(addDays(d, 1))).toBe('2026-03-01');
    expect(maxDate('2026-01-02', '2026-01-01')).toBe('2026-01-02');
    expect(minDate('2026-01-02', '2026-01-01')).toBe('2026-01-01');
  });
});

describe('finance amounts', () => {
  it('cashflow / spending fall back to inclusion flags', () => {
    expect(cashflowAmount(tx({ cashflowIncluded: true }))).toBe(1000);
    expect(cashflowAmount(tx({ cashflowIncluded: false }))).toBe(0);
    expect(cashflowAmount(tx({ cashflowAmount: 500 }))).toBe(500);
    expect(spendingAmount(tx({ spendingIncluded: true }))).toBe(1000);
  });

  it('deferred spending excludes income and cashflow-included rows', () => {
    expect(deferredSpendingAmount(tx({ flowType: '수입', spendingIncluded: true }))).toBe(0);
    expect(deferredSpendingAmount(tx({ cashflowIncluded: true, spendingIncluded: true }))).toBe(0);
    expect(deferredSpendingAmount(tx({ cashflowIncluded: false, spendingIncluded: true }))).toBe(1000);
  });

  it('external cash out ignores income and transfers', () => {
    expect(externalCashOutAmount(tx({ flowType: '수입', cashflowIncluded: true }))).toBe(0);
    expect(externalCashOutAmount(tx({ flowType: '이체지출', cashflowIncluded: true }))).toBe(0);
    expect(externalCashOutAmount(tx({ cashflowIncluded: true }))).toBe(1000);
  });

  it('filterTransactionsByPeriod is inclusive', () => {
    const list = [tx({ id: 1, transactionDate: '2026-11-01' }), tx({ id: 2, transactionDate: '2026-11-07' }), tx({ id: 3, transactionDate: '2026-11-08' })];
    expect(filterTransactionsByPeriod(list, { start: '2026-11-01', end: '2026-11-07' }).map((t) => t.id)).toEqual([1, 2]);
  });
});
