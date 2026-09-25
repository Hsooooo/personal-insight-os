import type { FinanceAccount, FinanceTransaction, RecurringBillItem } from '@/types';

export const CATEGORY_COLORS = ['#10b981', '#0ea5e9', '#f59e0b', '#ef4444', '#8b5cf6', '#14b8a6', '#f97316', '#64748b'];

export function money(value: number | null | undefined) {
  if (value == null) return '-';
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW', maximumFractionDigits: 0 }).format(value);
}

export function percent(value: number, total: number) {
  if (total <= 0) return '0%';
  return `${Math.round((value / total) * 100)}%`;
}

export function cashflowAmount(tx: FinanceTransaction) {
  return Number(tx.cashflowAmount ?? (tx.cashflowIncluded ? tx.amount : 0));
}

export function spendingAmount(tx: FinanceTransaction) {
  return Number(tx.spendingAmount ?? (tx.spendingIncluded ? tx.amount : 0));
}

export function deferredSpendingAmount(tx: FinanceTransaction) {
  if (tx.flowType === '수입' || tx.cashflowIncluded) return 0;
  return spendingAmount(tx);
}

export function isLiabilityTransaction(tx: FinanceTransaction) {
  return tx.accountType === 'MOBILE_PAYMENT'
    || tx.accountRole === 'PAYMENT_METHOD'
    || tx.asset === '소액결제'
    || tx.paymentMethod === '소액결제';
}

export function isLiabilityAccount(account: FinanceAccount) {
  return account.accountType === 'MOBILE_PAYMENT' || account.role === 'PAYMENT_METHOD';
}

export function externalCashOutAmount(tx: FinanceTransaction) {
  if (tx.flowType === '수입' || tx.flowType === '이체지출') return 0;
  return cashflowAmount(tx);
}

export function parseMoneyInput(value: string) {
  const parsed = Number(value.replace(/,/g, '').trim() || 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

export function shortDateTime(value: string | null | undefined) {
  if (!value) return '-';
  const parts = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(new Date(value));
  const get = (type: string) => parts.find((part) => part.type === type)?.value || '';
  return `${get('year')}-${get('month')}-${get('day')} ${get('hour')}:${get('minute')}`;
}

export function seoulTime(value: string | null | undefined) {
  if (!value) return '';
  return new Date(value).toLocaleTimeString('en-GB', {
    timeZone: 'Asia/Seoul',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

export function seoulDate(value: string | null | undefined) {
  if (!value) return '';
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value;
  return new Date(value).toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' });
}

export function parseDateOnly(value: string) {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}

export function formatDateOnly(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function addDays(date: Date, days: number) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

export function maxDate(a: string, b: string) {
  return a > b ? a : b;
}

export function minDate(a: string, b: string) {
  return a < b ? a : b;
}

export function cyclePeriod(cycle: { startsAt: string; endsAt: string | null } | undefined, transactions: FinanceTransaction[]) {
  const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' });
  const cycleStart = seoulDate(cycle?.startsAt) || transactions[0]?.transactionDate || today;
  const lastTransactionDate = transactions[transactions.length - 1]?.transactionDate;
  const cycleEnd = seoulDate(cycle?.endsAt) || lastTransactionDate || today;
  return { start: cycleStart, end: cycleEnd };
}

export function buildFinanceWeekOptions(cycle: { startsAt: string; endsAt: string | null } | undefined, transactions: FinanceTransaction[]) {
  const period = cyclePeriod(cycle, transactions);
  const options = [{ key: 'ALL', label: 'All cycle', start: period.start, end: period.end }];
  if (!period.start || !period.end || period.start > period.end) return options;

  let cursor = parseDateOnly(period.start);
  let weekNumber = 1;
  while (formatDateOnly(cursor) <= period.end) {
    const daysSinceMonday = (cursor.getDay() + 6) % 7;
    const calendarWeekStart = formatDateOnly(addDays(cursor, -daysSinceMonday));
    const calendarWeekEnd = formatDateOnly(addDays(parseDateOnly(calendarWeekStart), 6));
    const start = maxDate(period.start, calendarWeekStart);
    const end = minDate(period.end, calendarWeekEnd);
    options.push({
      key: `${start}_${end}`,
      label: `Week ${weekNumber} · ${start} ~ ${end}`,
      start,
      end,
    });
    cursor = addDays(parseDateOnly(end), 1);
    weekNumber += 1;
  }
  return options;
}

export function filterTransactionsByPeriod(transactions: FinanceTransaction[], period: { start: string; end: string }) {
  return transactions.filter((tx) => tx.transactionDate >= period.start && tx.transactionDate <= period.end);
}

export function accountAliases(account: FinanceAccount) {
  return new Set([account.name, ...(account.aliases || [])].filter(Boolean));
}

export function resolveAccount(accounts: FinanceAccount[], tx: FinanceTransaction) {
  if (tx.accountId) {
    const byId = accounts.find((account) => account.id === tx.accountId);
    if (byId) return byId;
  }
  const candidates = [tx.accountName, tx.asset].filter(Boolean);
  return accounts.find((account) => {
    const aliases = accountAliases(account);
    return candidates.some((candidate) => aliases.has(candidate || ''));
  });
}

export function resolveAccountByName(accounts: FinanceAccount[], name: string | null | undefined) {
  if (!name) return undefined;
  return accounts.find((account) => accountAliases(account).has(name));
}

export function flowForAccount(transactions: FinanceTransaction[], accounts: FinanceAccount[]) {
  const map = new Map<number, { income: number; out: number }>();
  accounts.forEach((account) => {
    if (!isLiabilityAccount(account)) map.set(account.id, { income: 0, out: 0 });
  });

  transactions.forEach((tx) => {
    if (isLiabilityTransaction(tx)) return;
    const source = resolveAccount(accounts, tx);
    if (source && !isLiabilityAccount(source)) {
      const current = map.get(source.id) || { income: 0, out: 0 };
      if (tx.flowType === '수입') current.income += Number(tx.amount);
      if (tx.flowType !== '수입' && tx.cashflowIncluded) current.out += cashflowAmount(tx);
      map.set(source.id, current);
    }
    if (tx.flowType === '이체지출') {
      const destination = resolveAccountByName(accounts, tx.category);
      if (destination && !isLiabilityAccount(destination)) {
        const current = map.get(destination.id) || { income: 0, out: 0 };
        current.income += Number(tx.amount);
        map.set(destination.id, current);
      }
    }
  });

  return map;
}

export function accountFlowSummaries(
  accounts: FinanceAccount[],
  periodTransactions: FinanceTransaction[],
  prePeriodTransactions: FinanceTransaction[]
) {
  const periodFlow = flowForAccount(periodTransactions, accounts);
  const prePeriodFlow = flowForAccount(prePeriodTransactions, accounts);
  return accounts
    .map((account) => {
      const cycleOpening = Number(account.periodOpeningBalance ?? account.openingBalance ?? 0);
      if (isLiabilityAccount(account)) {
        return {
          ...account,
          periodOpeningBalance: cycleOpening,
          cycleIncome: 0,
          cycleCashOut: 0,
          cycleNetFlow: 0,
          estimatedBalance: cycleOpening,
        };
      }
      const period = periodFlow.get(account.id) || { income: 0, out: 0 };
      const prePeriod = prePeriodFlow.get(account.id) || { income: 0, out: 0 };
      const periodOpeningBalance = cycleOpening + (prePeriod.income - prePeriod.out);
      const cycleNetFlow = period.income - period.out;
      return {
        ...account,
        periodOpeningBalance,
        cycleIncome: period.income,
        cycleCashOut: period.out,
        cycleNetFlow,
        estimatedBalance: periodOpeningBalance + cycleNetFlow,
      };
    });
}

export function summaryTitle(periodKind: 'cycle' | 'week') {
  return periodKind === 'week' ? 'Finance Weekly Summary' : 'Finance Cycle Summary';
}

export function formatSummaryRange(range: { start: string; end: string }, kind: 'cycle' | 'week') {
  if (kind === 'cycle') return `All cycle · ${range.start} ~ ${range.end}`;
  return `${range.start} ~ ${range.end}`;
}

export function formatFinanceWeeklySummary(
  transactions: FinanceTransaction[],
  range: { start: string; end: string },
  cycleLabel: string | undefined,
  periodKind: 'cycle' | 'week' = 'week',
  accountFlowRows: Array<{
    name: string;
    periodOpeningBalance: number;
    cycleIncome: number;
    cycleCashOut: number;
    cycleNetFlow: number;
    estimatedBalance: number;
  }> = []
) {
  const rows = transactions.filter((tx) => tx.transactionDate >= range.start && tx.transactionDate <= range.end);
  const income = rows.filter((tx) => tx.flowType === '수입').reduce((sum, tx) => sum + Number(tx.amount), 0);
  const cashOut = rows.reduce((sum, tx) => sum + externalCashOutAmount(tx), 0);
  const deferredSpending = rows.reduce((sum, tx) => sum + deferredSpendingAmount(tx), 0);
  const spending = rows.reduce((sum, tx) => sum + spendingAmount(tx), 0);
  const net = income - cashOut;

  const byCategory = new Map<string, number>();
  const byLiability = new Map<string, { used: number; settled: number }>();
  rows.forEach((tx) => {
    const spend = spendingAmount(tx);
    if (tx.spendingIncluded && tx.flowType !== '수입' && spend > 0) {
      byCategory.set(tx.category || 'Uncategorized', (byCategory.get(tx.category || 'Uncategorized') || 0) + spend);
    }
    if (isLiabilityTransaction(tx)) {
      const liability = tx.accountName || tx.asset || tx.paymentMethod || 'Liability';
      const current = byLiability.get(liability) || { used: 0, settled: 0 };
      current.used += spend;
      current.settled += cashflowAmount(tx);
      byLiability.set(liability, current);
    }
  });

  const topCategories = Array.from(byCategory.entries()).sort((a, b) => b[1] - a[1]);
  const liabilityRows = Array.from(byLiability.entries())
    .sort((a, b) => Math.abs(b[1].settled - b[1].used) - Math.abs(a[1].settled - a[1].used))
    .slice(0, 8);

  let md = `# ${summaryTitle(periodKind)}: ${range.start} ~ ${range.end}\n\n`;
  if (cycleLabel) md += `Cycle: ${cycleLabel}\n\n`;
  md += `## Totals\n`;
  md += `| Income | External Cash Out | Deferred Spending | Actual Spending | Net External Cashflow | Transactions |\n`;
  md += `|--------|-------------------|-------------------|-----------------|-----------------------|--------------|\n`;
  md += `| ${money(income)} | ${money(cashOut)} | ${money(deferredSpending)} | ${money(spending)} | ${money(net)} | ${rows.length} |\n\n`;

  md += `## Spending Categories\n`;
  if (topCategories.length === 0) {
    md += `_No spending rows in this period._\n\n`;
  } else {
    md += `| Category | Amount |\n|----------|--------|\n`;
    topCategories.forEach(([category, amount]) => {
      md += `| ${category} | ${money(amount)} |\n`;
    });
    md += `\n`;
  }

  md += `## Account Flow\n`;
  if (accountFlowRows.length === 0) {
    md += `_No account flow in this period._\n\n`;
  } else {
    md += `| Account | Opening | In | Out | Net | Closing |\n|---------|--------:|---:|----:|----:|--------:|\n`;
    accountFlowRows.forEach((summary) => {
      md += `| ${summary.name} | ${money(summary.periodOpeningBalance)} | ${money(summary.cycleIncome)} | ${money(summary.cycleCashOut)} | ${money(summary.cycleNetFlow)} | ${money(summary.estimatedBalance)} |\n`;
    });
    md += `\n`;
  }

  md += `## Liability Flow\n`;
  if (liabilityRows.length === 0) {
    md += `_No liability flow in this period._\n\n`;
  } else {
    md += `| Account | Used | Settled | Net Liability |\n|---------|------|---------|---------------|\n`;
    liabilityRows.forEach(([account, summary]) => {
      md += `| ${account} | ${money(summary.used)} | ${money(summary.settled)} | ${money(summary.settled - summary.used)} |\n`;
    });
    md += `\n`;
  }

  md += `## Transactions\n`;
  if (rows.length === 0) {
    md += `_No transactions in this period._\n`;
  } else {
    md += `| Date | Account | Category | Description | Flow | Amount |\n`;
    md += `|------|---------|----------|-------------|------|--------|\n`;
    rows.forEach((tx) => {
      md += `| ${tx.transactionDate} | ${tx.accountName || tx.asset || '-'} | ${tx.category || '-'} | ${tx.description || '-'} | ${tx.flowType} | ${money(tx.amount)} |\n`;
    });
  }

  return md;
}

export const ktDefaultItems: RecurringBillItem[] = [
  { itemName: '월정액', amount: 50000, itemType: 'BASE', sortOrder: 0 },
  { itemName: '듀얼번호', amount: 2900, itemType: 'ADD_ON', sortOrder: 1 },
  { itemName: '캐치콜서비스', amount: 400, itemType: 'ADD_ON', sortOrder: 2 },
  { itemName: 'KT365폰케어', amount: 2730, itemType: 'ADD_ON', sortOrder: 3 },
  { itemName: 'KT365폰케어 일반 플러스서비스', amount: 2519, itemType: 'ADD_ON', sortOrder: 4 },
  { itemName: '유튜브 프리미엄(요금제)', amount: 4046, itemType: 'CONTENT', sortOrder: 5 },
  { itemName: '모바일 월정액 요금 할인', amount: -2730, itemType: 'DISCOUNT', sortOrder: 6 },
  { itemName: '부가가치세', amount: 5986, itemType: 'TAX', sortOrder: 7 },
  { itemName: '10원 절삭', amount: -9, itemType: 'ADJUSTMENT', sortOrder: 8 },
];

export const accountTypeOptions = ['BANK_ACCOUNT', 'MOBILE_PAYMENT', 'SAVINGS_GOAL', 'DEBT', 'INTERNAL', 'OTHER'];
export const accountRoleOptions = ['SALARY', 'LIVING', 'SUBSCRIPTION', 'SINKING_FUND', 'DEBT_REPAYMENT', 'PAYMENT_METHOD', 'OTHER'];

export function suggestAccount(asset: string) {
  if (asset === '소액결제') return { accountType: 'MOBILE_PAYMENT', role: 'PAYMENT_METHOD' };
  if (asset === '회생') return { accountType: 'DEBT', role: 'DEBT_REPAYMENT' };
  if (asset === '마이핏') return { accountType: 'SAVINGS_GOAL', role: 'SINKING_FUND' };
  if (asset === '하나은행(구독)') return { accountType: 'BANK_ACCOUNT', role: 'SUBSCRIPTION' };
  if (asset.includes('은행')) return { accountType: 'BANK_ACCOUNT', role: 'OTHER' };
  return { accountType: 'OTHER', role: 'OTHER' };
}
