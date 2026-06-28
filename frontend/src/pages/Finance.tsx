import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { toast } from 'sonner';
import { ArrowDownUp, CalendarDays, Check, Copy, FileSpreadsheet, FilterX, Link2, Plus, ReceiptText, RefreshCw, Search, Trash2, WalletCards } from 'lucide-react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import type { FinanceAccount, FinanceImportPreviewResponse, FinanceImportRow, FinanceTransaction, RecurringBillItem } from '@/types';

const CATEGORY_COLORS = ['#10b981', '#0ea5e9', '#f59e0b', '#ef4444', '#8b5cf6', '#14b8a6', '#f97316', '#64748b'];

function money(value: number | null | undefined) {
  if (value == null) return '-';
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW', maximumFractionDigits: 0 }).format(value);
}

function percent(value: number, total: number) {
  if (total <= 0) return '0%';
  return `${Math.round((value / total) * 100)}%`;
}

function cashflowAmount(tx: FinanceTransaction) {
  return Number(tx.cashflowAmount ?? (tx.cashflowIncluded ? tx.amount : 0));
}

function spendingAmount(tx: FinanceTransaction) {
  return Number(tx.spendingAmount ?? (tx.spendingIncluded ? tx.amount : 0));
}

function deferredSpendingAmount(tx: FinanceTransaction) {
  if (tx.flowType === '수입' || tx.cashflowIncluded) return 0;
  return spendingAmount(tx);
}

function isLiabilityTransaction(tx: FinanceTransaction) {
  return tx.accountType === 'MOBILE_PAYMENT'
    || tx.accountRole === 'PAYMENT_METHOD'
    || tx.asset === '소액결제'
    || tx.paymentMethod === '소액결제';
}

function isLiabilityAccount(account: FinanceAccount) {
  return account.accountType === 'MOBILE_PAYMENT' || account.role === 'PAYMENT_METHOD';
}

function externalCashOutAmount(tx: FinanceTransaction) {
  if (tx.flowType === '수입' || tx.flowType === '이체지출') return 0;
  return cashflowAmount(tx);
}

function parseMoneyInput(value: string) {
  const parsed = Number(value.replace(/,/g, '').trim() || 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

function shortDateTime(value: string | null | undefined) {
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

function seoulTime(value: string | null | undefined) {
  if (!value) return '';
  return new Date(value).toLocaleTimeString('en-GB', {
    timeZone: 'Asia/Seoul',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

function seoulDate(value: string | null | undefined) {
  if (!value) return '';
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value;
  return new Date(value).toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' });
}

function parseDateOnly(value: string) {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}

function formatDateOnly(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function addDays(date: Date, days: number) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

function maxDate(a: string, b: string) {
  return a > b ? a : b;
}

function minDate(a: string, b: string) {
  return a < b ? a : b;
}

function getFinanceWeekRange(cycle: { startsAt: string; endsAt: string | null } | undefined, transactions: FinanceTransaction[]) {
  const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' });
  const cycleStart = seoulDate(cycle?.startsAt) || transactions[0]?.transactionDate || today;
  const cycleEnd = seoulDate(cycle?.endsAt) || transactions[transactions.length - 1]?.transactionDate || today;
  const reference = today >= cycleStart && today <= cycleEnd
    ? today
    : today > cycleEnd ? cycleEnd : cycleStart;
  const refDate = parseDateOnly(reference);
  const daysSinceMonday = (refDate.getDay() + 6) % 7;
  const weekStart = formatDateOnly(addDays(refDate, -daysSinceMonday));
  const weekEnd = formatDateOnly(addDays(parseDateOnly(weekStart), 6));
  return {
    start: maxDate(cycleStart, weekStart),
    end: minDate(cycleEnd, weekEnd),
    weekEnd,
  };
}

function formatFinanceWeeklySummary(
  transactions: FinanceTransaction[],
  range: { start: string; end: string },
  cycleLabel: string | undefined
) {
  const rows = transactions.filter((tx) => tx.transactionDate >= range.start && tx.transactionDate <= range.end);
  const income = rows.filter((tx) => tx.flowType === '수입').reduce((sum, tx) => sum + Number(tx.amount), 0);
  const cashOut = rows.reduce((sum, tx) => sum + externalCashOutAmount(tx), 0);
  const deferredSpending = rows.reduce((sum, tx) => sum + deferredSpendingAmount(tx), 0);
  const spending = rows.reduce((sum, tx) => sum + spendingAmount(tx), 0);
  const net = income - cashOut;

  const byCategory = new Map<string, number>();
  const byAccount = new Map<string, { income: number; out: number }>();
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
      return;
    }
    const account = tx.accountName || tx.asset || 'Unmapped';
    const current = byAccount.get(account) || { income: 0, out: 0 };
    if (tx.flowType === '수입') current.income += Number(tx.amount);
    if (tx.cashflowIncluded && tx.flowType !== '수입') current.out += cashflowAmount(tx);
    byAccount.set(account, current);
    if (tx.flowType === '이체지출' && tx.category) {
      const destination = byAccount.get(tx.category) || { income: 0, out: 0 };
      destination.income += Number(tx.amount);
      byAccount.set(tx.category, destination);
    }
  });

  const topCategories = Array.from(byCategory.entries()).sort((a, b) => b[1] - a[1]).slice(0, 8);
  const accountRows = Array.from(byAccount.entries())
    .sort((a, b) => Math.abs(b[1].income - b[1].out) - Math.abs(a[1].income - a[1].out))
    .slice(0, 8);
  const liabilityRows = Array.from(byLiability.entries())
    .sort((a, b) => Math.abs(b[1].settled - b[1].used) - Math.abs(a[1].settled - a[1].used))
    .slice(0, 8);

  let md = `# Finance Weekly Summary: ${range.start} ~ ${range.end}\n\n`;
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
  if (accountRows.length === 0) {
    md += `_No account flow in this period._\n\n`;
  } else {
    md += `| Account | In | Out | Net |\n|---------|----|-----|-----|\n`;
    accountRows.forEach(([account, summary]) => {
      md += `| ${account} | ${money(summary.income)} | ${money(summary.out)} | ${money(summary.income - summary.out)} |\n`;
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

function statusBadge(status: FinanceImportRow['status']) {
  if (status === 'NEW') return <Badge className="bg-emerald-100 text-emerald-700 hover:bg-emerald-100">New</Badge>;
  if (status === 'NEEDS_REVIEW') return <Badge className="bg-amber-100 text-amber-700 hover:bg-amber-100">Review</Badge>;
  return <Badge variant="secondary">Duplicate</Badge>;
}

const ktDefaultItems: RecurringBillItem[] = [
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

const accountTypeOptions = ['BANK_ACCOUNT', 'MOBILE_PAYMENT', 'SAVINGS_GOAL', 'DEBT', 'INTERNAL', 'OTHER'];
const accountRoleOptions = ['SALARY', 'LIVING', 'SUBSCRIPTION', 'SINKING_FUND', 'DEBT_REPAYMENT', 'PAYMENT_METHOD', 'OTHER'];

function suggestAccount(asset: string) {
  if (asset === '소액결제') return { accountType: 'MOBILE_PAYMENT', role: 'PAYMENT_METHOD' };
  if (asset === '회생') return { accountType: 'DEBT', role: 'DEBT_REPAYMENT' };
  if (asset === '마이핏') return { accountType: 'SAVINGS_GOAL', role: 'SINKING_FUND' };
  if (asset === '하나은행(구독)') return { accountType: 'BANK_ACCOUNT', role: 'SUBSCRIPTION' };
  if (asset.includes('은행')) return { accountType: 'BANK_ACCOUNT', role: 'OTHER' };
  return { accountType: 'OTHER', role: 'OTHER' };
}

export default function Finance() {
  const queryClient = useQueryClient();
  const [selectedCycleId, setSelectedCycleId] = useState<number | undefined>();
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<FinanceImportPreviewResponse | null>(null);
  const [reviewActions, setReviewActions] = useState<Record<string, 'create' | 'skip'>>({});
  const [copiedWeeklyFinance, setCopiedWeeklyFinance] = useState(false);
  const [transactionFilters, setTransactionFilters] = useState({
    search: '',
    flowType: 'ALL',
    account: 'ALL',
    category: 'ALL',
    inclusion: 'ALL',
    from: '',
    to: '',
  });
  const [newBill, setNewBill] = useState({ name: 'KT 통신비', provider: 'KT', category: '주거/통신', memo: '' });
  const [versionTemplateId, setVersionTemplateId] = useState<number | ''>('');
  const [versionCycleId, setVersionCycleId] = useState<number | ''>('');
  const [versionItems, setVersionItems] = useState<RecurringBillItem[]>(ktDefaultItems);
  const [newAccount, setNewAccount] = useState({
    name: '',
    accountType: 'BANK_ACCOUNT',
    role: 'OTHER',
    institution: '',
    aliases: '',
    memo: '',
    openingBalance: '',
    openingBalanceDate: '',
    openingBalanceMemo: '',
  });

  const { data: cycles, isLoading: cyclesLoading } = useQuery({
    queryKey: ['financeCycles'],
    queryFn: api.finance.cycles,
  });

  const activeCycleId = selectedCycleId || cycles?.[0]?.id;
  const activeCycle = cycles?.find((cycle) => cycle.id === activeCycleId);

  const { data: transactions, isLoading: transactionsLoading } = useQuery({
    queryKey: ['financeTransactions', activeCycleId],
    queryFn: () => api.finance.transactions(activeCycleId),
    enabled: !!activeCycleId,
  });

  const { data: recurringBills } = useQuery({
    queryKey: ['financeRecurringBills'],
    queryFn: api.finance.recurringBills,
  });

  const { data: accounts } = useQuery({
    queryKey: ['financeAccounts', activeCycleId],
    queryFn: () => api.finance.accounts(activeCycleId),
  });

  const previewMutation = useMutation({
    mutationFn: api.finance.previewImport,
    onSuccess: (data) => {
      setPreview(data);
      const actions: Record<string, 'create' | 'skip'> = {};
      data.rows.forEach((row) => {
        if (row.status === 'NEEDS_REVIEW') actions[row.rowId] = 'skip';
      });
      setReviewActions(actions);
      toast.success(`${data.totalRows}개 행을 확인했습니다`);
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : 'Import preview failed'),
  });

  const confirmMutation = useMutation({
    mutationFn: api.finance.confirmImport,
    onSuccess: (data) => {
      toast.success(`${data.created}개 거래를 저장했습니다`);
      setPreview(null);
      setFile(null);
      queryClient.invalidateQueries({ queryKey: ['financeCycles'] });
      queryClient.invalidateQueries({ queryKey: ['financeTransactions'] });
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : 'Import failed'),
  });

  const createBillMutation = useMutation({
    mutationFn: api.finance.createRecurringBill,
    onSuccess: () => {
      toast.success('반복 청구 템플릿을 만들었습니다');
      queryClient.invalidateQueries({ queryKey: ['financeRecurringBills'] });
    },
  });

  const createVersionMutation = useMutation({
    mutationFn: () => {
      if (!versionTemplateId) throw new Error('템플릿을 선택해주세요');
      const expectedAmount = versionItems.reduce((sum, item) => sum + Number(item.amount || 0), 0);
      return api.finance.createRecurringBillVersion(Number(versionTemplateId), {
        effectiveCycleId: versionCycleId ? Number(versionCycleId) : null,
        expectedAmount,
        items: versionItems,
      });
    },
    onSuccess: () => {
      toast.success('새 템플릿 버전을 만들었습니다');
      queryClient.invalidateQueries({ queryKey: ['financeRecurringBills'] });
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : 'Version creation failed'),
  });

  const deleteBillMutation = useMutation({
    mutationFn: api.finance.deleteRecurringBill,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['financeRecurringBills'] }),
  });

  const createAccountMutation = useMutation({
    mutationFn: api.finance.createAccount,
    onSuccess: () => {
      toast.success('계좌를 만들고 거래를 매핑했습니다');
      setNewAccount({
        name: '',
        accountType: 'BANK_ACCOUNT',
        role: 'OTHER',
        institution: '',
        aliases: '',
        memo: '',
        openingBalance: '',
        openingBalanceDate: '',
        openingBalanceMemo: '',
      });
      queryClient.invalidateQueries({ queryKey: ['financeAccounts'] });
      queryClient.invalidateQueries({ queryKey: ['financeTransactions'] });
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : 'Account creation failed'),
  });

  const updateAccountMutation = useMutation({
    mutationFn: ({ id, account }: { id: number; account: Partial<FinanceAccount> }) => api.finance.updateAccount(id, account),
    onSuccess: () => {
      toast.success('계좌 매핑을 갱신했습니다');
      queryClient.invalidateQueries({ queryKey: ['financeAccounts'] });
      queryClient.invalidateQueries({ queryKey: ['financeTransactions'] });
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : 'Account update failed'),
  });

  const deleteAccountMutation = useMutation({
    mutationFn: api.finance.deleteAccount,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['financeAccounts'] });
      queryClient.invalidateQueries({ queryKey: ['financeTransactions'] });
    },
  });

  const autoMapMutation = useMutation({
    mutationFn: api.finance.autoMapAccounts,
    onSuccess: (data) => {
      toast.success(`${data.updatedTransactions}개 거래를 다시 매핑했습니다`);
      queryClient.invalidateQueries({ queryKey: ['financeAccounts'] });
      queryClient.invalidateQueries({ queryKey: ['financeTransactions'] });
    },
  });

  const updateTransactionTimeMutation = useMutation({
    mutationFn: ({ id, time }: { id: number; time: string }) => api.finance.updateTransactionTime(id, { time }),
    onSuccess: () => {
      toast.success('거래 시간을 보정했습니다');
      queryClient.invalidateQueries({ queryKey: ['financeTransactions'] });
      queryClient.invalidateQueries({ queryKey: ['financeAccounts'] });
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : 'Transaction time update failed'),
  });

  const totals = useMemo(() => {
    const list = transactions || [];
    const cashflowOut = list.reduce((sum, t) => sum + externalCashOutAmount(t), 0);
    const income = list.filter((t) => t.flowType === '수입').reduce((sum, t) => sum + Number(t.amount), 0);
    return {
      cashflowOut,
      deferredSpending: list.reduce((sum, t) => sum + deferredSpendingAmount(t), 0),
      spending: list.reduce((sum, t) => sum + spendingAmount(t), 0),
      income,
      netExternalCashflow: income - cashflowOut,
      count: list.length,
    };
  }, [transactions]);

  const categoryTotals = useMemo(() => {
    const map = new Map<string, number>();
    (transactions || []).forEach((t) => {
      if (!t.spendingIncluded || t.flowType === '수입') return;
      const amount = spendingAmount(t);
      if (amount <= 0) return;
      const key = t.category || 'Uncategorized';
      map.set(key, (map.get(key) || 0) + amount);
    });
    return Array.from(map.entries()).sort((a, b) => b[1] - a[1]).slice(0, 8);
  }, [transactions]);

  const categoryChartData = useMemo(
    () => categoryTotals.map(([name, value]) => ({ name, value })),
    [categoryTotals]
  );

  const categoryChartTotal = useMemo(
    () => categoryChartData.reduce((sum, item) => sum + item.value, 0),
    [categoryChartData]
  );

  const accountTotals = useMemo(() => {
    return [...(accounts || [])]
      .filter((account) => !isLiabilityAccount(account))
      .sort((a, b) => Math.abs(Number(b.cycleNetFlow)) - Math.abs(Number(a.cycleNetFlow)))
      .slice(0, 8);
  }, [accounts]);

  const liabilityTotals = useMemo(() => {
    const map = new Map<string, { used: number; settled: number }>();
    (transactions || []).forEach((tx) => {
      if (!isLiabilityTransaction(tx)) return;
      const key = tx.accountName || tx.asset || tx.paymentMethod || 'Liability';
      const current = map.get(key) || { used: 0, settled: 0 };
      current.used += spendingAmount(tx);
      current.settled += cashflowAmount(tx);
      map.set(key, current);
    });
    return Array.from(map.entries())
      .map(([name, summary]) => ({ name, ...summary, net: summary.settled - summary.used }))
      .sort((a, b) => Math.abs(b.net) - Math.abs(a.net))
      .slice(0, 8);
  }, [transactions]);

  const financeWeekRange = useMemo(
    () => getFinanceWeekRange(activeCycle, transactions || []),
    [activeCycle, transactions]
  );

  const transactionFilterOptions = useMemo(() => {
    const list = transactions || [];
    return {
      accounts: Array.from(new Set(list.map((tx) => tx.accountName || tx.asset || 'Unmapped'))).sort(),
      categories: Array.from(new Set(list.map((tx) => tx.category || 'Uncategorized'))).sort(),
      flowTypes: Array.from(new Set(list.map((tx) => tx.flowType).filter(Boolean))).sort(),
    };
  }, [transactions]);

  const filteredTransactions = useMemo(() => {
    const search = transactionFilters.search.trim().toLowerCase();
    return (transactions || []).filter((tx) => {
      const account = tx.accountName || tx.asset || 'Unmapped';
      const category = tx.category || 'Uncategorized';
      const searchable = [
        tx.transactionDate,
        account,
        category,
        tx.subcategory,
        tx.description,
        tx.asset,
        tx.memo,
        tx.flowType,
        tx.paymentMethod,
      ].filter(Boolean).join(' ').toLowerCase();

      if (search && !searchable.includes(search)) return false;
      if (transactionFilters.flowType !== 'ALL' && tx.flowType !== transactionFilters.flowType) return false;
      if (transactionFilters.account !== 'ALL' && account !== transactionFilters.account) return false;
      if (transactionFilters.category !== 'ALL' && category !== transactionFilters.category) return false;
      if (transactionFilters.inclusion === 'CASH' && !tx.cashflowIncluded) return false;
      if (transactionFilters.inclusion === 'SPEND' && (!tx.spendingIncluded || spendingAmount(tx) <= 0)) return false;
      if (transactionFilters.inclusion === 'ADJUSTED' && !tx.timeAdjusted) return false;
      if (transactionFilters.inclusion === 'UNMAPPED' && tx.accountId) return false;
      if (transactionFilters.from && tx.transactionDate < transactionFilters.from) return false;
      if (transactionFilters.to && tx.transactionDate > transactionFilters.to) return false;
      return true;
    });
  }, [transactionFilters, transactions]);

  const filteredTransactionTotals = useMemo(() => ({
    cashflowOut: filteredTransactions.reduce((sum, t) => sum + externalCashOutAmount(t), 0),
    deferredSpending: filteredTransactions.reduce((sum, t) => sum + deferredSpendingAmount(t), 0),
    spending: filteredTransactions.reduce((sum, t) => sum + spendingAmount(t), 0),
    income: filteredTransactions.filter((t) => t.flowType === '수입').reduce((sum, t) => sum + Number(t.amount), 0),
  }), [filteredTransactions]);

  const unmappedAssets = useMemo(() => {
    const map = new Map<string, { count: number; cashOut: number; income: number }>();
    const mappedAliases = new Set<string>();
    (accounts || []).forEach((account) => {
      mappedAliases.add(account.name);
      (account.aliases || []).forEach((alias) => mappedAliases.add(alias));
    });
    (transactions || []).forEach((tx) => {
      if (!tx.accountId && tx.asset) {
        const current = map.get(tx.asset) || { count: 0, cashOut: 0, income: 0 };
        current.count += 1;
        if (tx.flowType === '수입') current.income += Number(tx.amount);
        if (tx.flowType !== '수입' && tx.cashflowIncluded) current.cashOut += Number(tx.amount);
        map.set(tx.asset, current);
      }
      if (tx.flowType === '이체지출' && tx.category && !mappedAliases.has(tx.category)) {
        const current = map.get(tx.category) || { count: 0, cashOut: 0, income: 0 };
        current.count += 1;
        current.income += Number(tx.amount);
        map.set(tx.category, current);
      }
    });
    return Array.from(map.entries()).sort((a, b) => b[1].count - a[1].count);
  }, [accounts, transactions]);

  const handlePreview = () => {
    if (!file) {
      toast.error('엑셀 파일을 선택해주세요');
      return;
    }
    previewMutation.mutate(file);
  };

  const handleConfirm = () => {
    if (!preview) return;
    confirmMutation.mutate({
      importSessionId: preview.importSessionId,
      decisions: preview.rows.map((row) => ({
        row,
        action: row.status === 'NEW' ? 'create' : row.status === 'NEEDS_REVIEW' ? reviewActions[row.rowId] || 'skip' : 'skip',
      })),
    });
  };

  const updateVersionItem = (idx: number, field: keyof RecurringBillItem, value: string) => {
    setVersionItems((items) =>
      items.map((item, i) => i === idx ? { ...item, [field]: field === 'amount' || field === 'sortOrder' ? Number(value) : value } : item)
    );
  };

  const fillAccountFromAsset = (asset: string) => {
    const suggested = suggestAccount(asset);
    setNewAccount({
      name: asset,
      accountType: suggested.accountType,
      role: suggested.role,
      institution: asset.includes('은행') ? asset.replace(/\(.+\)/, '') : '',
      aliases: asset,
      memo: '',
      openingBalance: '',
      openingBalanceDate: '',
      openingBalanceMemo: '',
    });
  };

  const handleCreateAccount = () => {
    createAccountMutation.mutate({
      ...newAccount,
      aliases: newAccount.aliases.split(',').map((v) => v.trim()).filter(Boolean),
      openingBalance: parseMoneyInput(newAccount.openingBalance),
      openingBalanceDate: newAccount.openingBalanceDate || null,
      openingBalanceMemo: newAccount.openingBalanceMemo || null,
      active: true,
    });
  };

  const mapAssetToExisting = (asset: string, accountId: number) => {
    const account = accounts?.find((item) => item.id === accountId);
    if (!account) return;
    updateAccountMutation.mutate({
      id: account.id,
      account: {
        ...account,
        aliases: Array.from(new Set([...(account.aliases || []), account.name, asset])),
      },
    });
  };

  const handleCopyFinanceWeeklySummary = async () => {
    const report = formatFinanceWeeklySummary(transactions || [], financeWeekRange, activeCycle?.label);
    await navigator.clipboard.writeText(report);
    setCopiedWeeklyFinance(true);
    toast.success('Finance weekly summary copied');
    setTimeout(() => setCopiedWeeklyFinance(false), 2000);
  };

  const resetTransactionFilters = () => setTransactionFilters({
    search: '',
    flowType: 'ALL',
    account: 'ALL',
    category: 'ALL',
    inclusion: 'ALL',
    from: '',
    to: '',
  });

  return (
    <div className="space-y-6">
      <div className="rounded-md border bg-muted/20 p-4">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-3xl font-bold tracking-tight">Finance</h2>
              {activeCycle && <Badge variant="outline">{activeCycle.status}</Badge>}
            </div>
            <p className="text-muted-foreground">Salary-cycle spending, cashflow, and recurring bill profiles</p>
            <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
              <span className="inline-flex items-center gap-1 rounded-md border bg-background px-2 py-1">
                <CalendarDays className="h-3.5 w-3.5" />
                Cycle {activeCycle ? `${seoulDate(activeCycle.startsAt)} ~ ${seoulDate(activeCycle.endsAt) || 'open'}` : '-'}
              </span>
              <span className="inline-flex items-center gap-1 rounded-md border bg-background px-2 py-1">
                <ReceiptText className="h-3.5 w-3.5" />
                Weekly copy {financeWeekRange.start} ~ {financeWeekRange.end}
              </span>
            </div>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            <Button
              size="sm"
              variant="outline"
              className="gap-2"
              onClick={handleCopyFinanceWeeklySummary}
              disabled={transactionsLoading || !activeCycleId}
            >
              {copiedWeeklyFinance ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
              {copiedWeeklyFinance ? 'Copied!' : 'Copy Weekly Summary'}
            </Button>
            <select
              value={activeCycleId || ''}
              onChange={(e) => setSelectedCycleId(e.target.value ? Number(e.target.value) : undefined)}
              className="h-10 rounded-md border border-input bg-background px-3 text-sm"
            >
              {cyclesLoading && <option>Loading cycles...</option>}
              {cycles?.map((cycle) => (
                <option key={cycle.id} value={cycle.id}>{cycle.label}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      <Tabs defaultValue="overview" className="space-y-4">
        <TabsList className="flex h-auto flex-wrap justify-start">
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="transactions">Transactions</TabsTrigger>
          <TabsTrigger value="accounts">Accounts</TabsTrigger>
          <TabsTrigger value="import">Import</TabsTrigger>
          <TabsTrigger value="recurring">Recurring</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="space-y-4">
          <div className="grid gap-4 md:grid-cols-3 xl:grid-cols-6">
            <MetricCard title="Income" value={money(totals.income)} icon={<WalletCards className="h-4 w-4" />} />
            <MetricCard title="External Cash Out" value={money(totals.cashflowOut)} icon={<ArrowDownUp className="h-4 w-4" />} />
            <MetricCard title="Deferred Spending" value={money(totals.deferredSpending)} icon={<ReceiptText className="h-4 w-4" />} />
            <MetricCard title="Actual Spending" value={money(totals.spending)} icon={<ReceiptText className="h-4 w-4" />} />
            <MetricCard title="Net External" value={money(totals.netExternalCashflow)} icon={<WalletCards className="h-4 w-4" />} />
            <MetricCard title="Rows" value={String(totals.count)} icon={<FileSpreadsheet className="h-4 w-4" />} />
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Spending Categories</CardTitle>
              </CardHeader>
              <CardContent>
                {transactionsLoading ? <Skeleton className="h-32" /> : (
                  <div className="space-y-2">
                    {categoryTotals.length === 0 && <p className="text-sm text-muted-foreground">No spending rows in this cycle yet.</p>}
                    {categoryTotals.map(([category, amount], index) => {
                      const max = categoryTotals[0]?.[1] || 1;
                      return (
                        <div key={category} className="rounded-md border bg-background px-3 py-2">
                          <div className="flex items-center justify-between gap-3">
                            <span className="text-sm font-medium">{category}</span>
                            <span className="text-sm tabular-nums">{money(amount)}</span>
                          </div>
                          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-muted">
                            <div
                              className={index % 2 === 0 ? 'h-full rounded-full bg-emerald-500' : 'h-full rounded-full bg-sky-500'}
                              style={{ width: `${Math.max(6, Math.round((amount / max) * 100))}%` }}
                            />
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>Category Mix</CardTitle>
              </CardHeader>
              <CardContent>
                {transactionsLoading ? <Skeleton className="h-64" /> : categoryChartData.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No spending mix in this cycle yet.</p>
                ) : (
                  <div className="grid gap-4 md:grid-cols-[minmax(220px,0.9fr)_1fr] md:items-center">
                    <div className="relative h-64 min-w-0">
                      <ResponsiveContainer width="100%" height="100%">
                        <PieChart>
                          <Pie
                            data={categoryChartData}
                            dataKey="value"
                            nameKey="name"
                            innerRadius="58%"
                            outerRadius="82%"
                            paddingAngle={2}
                            strokeWidth={2}
                          >
                            {categoryChartData.map((entry, index) => (
                              <Cell key={entry.name} fill={CATEGORY_COLORS[index % CATEGORY_COLORS.length]} />
                            ))}
                          </Pie>
                          <Tooltip formatter={(value: number) => money(value)} />
                        </PieChart>
                      </ResponsiveContainer>
                      <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center text-center">
                        <span className="text-xs text-muted-foreground">Actual</span>
                        <span className="text-lg font-semibold tabular-nums">{money(categoryChartTotal)}</span>
                      </div>
                    </div>
                    <div className="space-y-2">
                      {categoryChartData.map((item, index) => (
                        <div key={item.name} className="grid grid-cols-[12px_1fr_auto] items-center gap-2 text-sm">
                          <span className="h-3 w-3 rounded-full" style={{ backgroundColor: CATEGORY_COLORS[index % CATEGORY_COLORS.length] }} />
                          <span className="min-w-0 truncate text-muted-foreground">{item.name}</span>
                          <span className="whitespace-nowrap text-right tabular-nums">{percent(item.value, categoryChartTotal)}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Account Flow</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="overflow-x-auto">
                <div className="min-w-[760px] space-y-2">
                  <div className="grid grid-cols-[1.4fr_130px_130px_150px_150px] gap-3 px-3 text-xs font-medium text-muted-foreground">
                    <span>Account</span>
                    <span className="text-right">In</span>
                    <span className="text-right">Out</span>
                    <span className="text-right">Net</span>
                    <span className="text-right">Estimated</span>
                  </div>
                  {accountTotals.length === 0 && <p className="px-3 py-4 text-sm text-muted-foreground">No account flow in this cycle yet.</p>}
                  {accountTotals.map((account) => (
                    <div key={account.id} className="grid grid-cols-[1.4fr_130px_130px_150px_150px] items-center gap-3 rounded-md border bg-background px-3 py-2 text-sm">
                      <span className="min-w-0 truncate font-medium">{account.name}</span>
                      <span className="whitespace-nowrap text-right text-muted-foreground tabular-nums">{money(account.cycleIncome)}</span>
                      <span className="whitespace-nowrap text-right text-muted-foreground tabular-nums">{money(account.cycleCashOut)}</span>
                      <span className="whitespace-nowrap text-right font-medium tabular-nums">{money(account.cycleNetFlow)}</span>
                      <span className="whitespace-nowrap text-right font-medium tabular-nums">{money(account.estimatedBalance)}</span>
                    </div>
                  ))}
                </div>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Liability Flow</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="overflow-x-auto">
                <div className="min-w-[680px] space-y-2">
                  <div className="grid grid-cols-[1.4fr_150px_150px_170px] gap-3 px-3 text-xs font-medium text-muted-foreground">
                    <span>Account</span>
                    <span className="text-right">Used</span>
                    <span className="text-right">Settled</span>
                    <span className="text-right">Net Liability</span>
                  </div>
                  {liabilityTotals.length === 0 && <p className="px-3 py-4 text-sm text-muted-foreground">No liability flow in this cycle yet.</p>}
                  {liabilityTotals.map((liability) => (
                    <div key={liability.name} className="grid grid-cols-[1.4fr_150px_150px_170px] items-center gap-3 rounded-md border bg-background px-3 py-2 text-sm">
                      <span className="min-w-0 truncate font-medium">{liability.name}</span>
                      <span className="whitespace-nowrap text-right text-muted-foreground tabular-nums">{money(liability.used)}</span>
                      <span className="whitespace-nowrap text-right text-muted-foreground tabular-nums">{money(liability.settled)}</span>
                      <span className="whitespace-nowrap text-right font-medium tabular-nums">{money(liability.net)}</span>
                    </div>
                  ))}
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="transactions" className="space-y-4">
          <Card>
            <CardHeader className="space-y-4">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <CardTitle>Transactions</CardTitle>
                  <p className="text-sm text-muted-foreground">
                    {filteredTransactions.length} of {(transactions || []).length} rows · income {money(filteredTransactionTotals.income)} · external out {money(filteredTransactionTotals.cashflowOut)} · deferred {money(filteredTransactionTotals.deferredSpending)} · actual spend {money(filteredTransactionTotals.spending)}
                  </p>
                </div>
                <Button size="sm" variant="outline" className="gap-2" onClick={resetTransactionFilters}>
                  <FilterX className="h-4 w-4" />
                  Reset Filters
                </Button>
              </div>
              <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-[1.4fr_0.8fr_0.9fr_0.9fr_0.9fr_0.8fr_0.8fr]">
                <div className="relative">
                  <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
                  <Input
                    value={transactionFilters.search}
                    onChange={(e) => setTransactionFilters({ ...transactionFilters, search: e.target.value })}
                    className="pl-9"
                    placeholder="Search account, memo, category"
                  />
                </div>
                <select value={transactionFilters.flowType} onChange={(e) => setTransactionFilters({ ...transactionFilters, flowType: e.target.value })} className="h-10 rounded-md border border-input bg-background px-3 text-sm">
                  <option value="ALL">All flows</option>
                  {transactionFilterOptions.flowTypes.map((flow) => <option key={flow} value={flow}>{flow}</option>)}
                </select>
                <select value={transactionFilters.account} onChange={(e) => setTransactionFilters({ ...transactionFilters, account: e.target.value })} className="h-10 rounded-md border border-input bg-background px-3 text-sm">
                  <option value="ALL">All accounts</option>
                  {transactionFilterOptions.accounts.map((account) => <option key={account} value={account}>{account}</option>)}
                </select>
                <select value={transactionFilters.category} onChange={(e) => setTransactionFilters({ ...transactionFilters, category: e.target.value })} className="h-10 rounded-md border border-input bg-background px-3 text-sm">
                  <option value="ALL">All categories</option>
                  {transactionFilterOptions.categories.map((category) => <option key={category} value={category}>{category}</option>)}
                </select>
                <select value={transactionFilters.inclusion} onChange={(e) => setTransactionFilters({ ...transactionFilters, inclusion: e.target.value })} className="h-10 rounded-md border border-input bg-background px-3 text-sm">
                  <option value="ALL">All flags</option>
                  <option value="CASH">Cashflow</option>
                  <option value="SPEND">Spending</option>
                  <option value="ADJUSTED">Adjusted</option>
                  <option value="UNMAPPED">Unmapped</option>
                </select>
                <Input type="date" value={transactionFilters.from} onChange={(e) => setTransactionFilters({ ...transactionFilters, from: e.target.value })} />
                <Input type="date" value={transactionFilters.to} onChange={(e) => setTransactionFilters({ ...transactionFilters, to: e.target.value })} />
              </div>
            </CardHeader>
            <CardContent>
              {transactionsLoading ? <Skeleton className="h-48" /> : (
                <FinanceTransactionsTable
                  transactions={filteredTransactions}
                  updatingTransactionId={updateTransactionTimeMutation.variables?.id}
                  isUpdatingTime={updateTransactionTimeMutation.isPending}
                  onUpdateTime={(id, time) => updateTransactionTimeMutation.mutate({ id, time })}
                />
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="accounts" className="space-y-4">
          <div className="grid gap-4 lg:grid-cols-[360px_1fr]">
            <Card>
              <CardHeader>
                <CardTitle>Create Account</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <Field label="Name" value={newAccount.name} onChange={(v) => setNewAccount({ ...newAccount, name: v })} />
                <div className="grid gap-3 sm:grid-cols-2">
                  <SelectField label="Type" value={newAccount.accountType} options={accountTypeOptions} onChange={(v) => setNewAccount({ ...newAccount, accountType: v })} />
                  <SelectField label="Role" value={newAccount.role} options={accountRoleOptions} onChange={(v) => setNewAccount({ ...newAccount, role: v })} />
                </div>
                <Field label="Institution" value={newAccount.institution} onChange={(v) => setNewAccount({ ...newAccount, institution: v })} />
                <Field label="Aliases" value={newAccount.aliases} onChange={(v) => setNewAccount({ ...newAccount, aliases: v })} />
                <div className="grid gap-3 sm:grid-cols-2">
                  <Field label="Opening Balance" value={newAccount.openingBalance} onChange={(v) => setNewAccount({ ...newAccount, openingBalance: v })} />
                  <Field label="Opening Date" value={newAccount.openingBalanceDate} onChange={(v) => setNewAccount({ ...newAccount, openingBalanceDate: v })} />
                </div>
                <Field label="Opening Memo" value={newAccount.openingBalanceMemo} onChange={(v) => setNewAccount({ ...newAccount, openingBalanceMemo: v })} />
                <Button className="w-full" onClick={handleCreateAccount} disabled={!newAccount.name || createAccountMutation.isPending}>
                  <Plus className="mr-2 h-4 w-4" />
                  Add Account
                </Button>
                <Button variant="outline" className="w-full" onClick={() => autoMapMutation.mutate()} disabled={autoMapMutation.isPending}>
                  <Link2 className="mr-2 h-4 w-4" />
                  Re-map Aliases
                </Button>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Unmapped Assets</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {unmappedAssets.length === 0 && <p className="text-sm text-muted-foreground">All visible assets are mapped.</p>}
                {unmappedAssets.map(([asset, summary]) => {
                  const suggested = suggestAccount(asset);
                  return (
                    <div key={asset} className="rounded-md border p-3">
                      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                          <p className="font-medium">{asset}</p>
                          <p className="text-xs text-muted-foreground">
                            {summary.count} rows · in {money(summary.income)} · out {money(summary.cashOut)}
                          </p>
                          <div className="mt-2 flex gap-1">
                            <Badge variant="outline">{suggested.accountType}</Badge>
                            <Badge variant="outline">{suggested.role}</Badge>
                          </div>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          <Button size="sm" variant="outline" onClick={() => fillAccountFromAsset(asset)}>Use Suggestion</Button>
                          <select
                            defaultValue=""
                            onChange={(e) => e.target.value && mapAssetToExisting(asset, Number(e.target.value))}
                            className="h-9 rounded-md border border-input bg-background px-2 text-xs"
                          >
                            <option value="">Map to existing</option>
                            {accounts?.map((account) => (
                              <option key={account.id} value={account.id}>{account.name}</option>
                            ))}
                          </select>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Accounts</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3 md:grid-cols-2">
              {accounts?.length ? accounts.map((account) => (
                <div key={account.id} className="rounded-md border p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-medium">{account.name}</p>
                      <p className="text-sm text-muted-foreground">{account.institution || '-'} · {account.accountType} · {account.role}</p>
                    </div>
                    <Button variant="ghost" size="icon" onClick={() => deleteAccountMutation.mutate(account.id)}>
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                  <div className="mt-3 grid grid-cols-4 gap-2 text-xs">
                    <span>Opening<br /><strong>{money(account.openingBalance)}</strong></span>
                    <span>In<br /><strong>{money(account.cycleIncome)}</strong></span>
                    <span>Out<br /><strong>{money(account.cycleCashOut)}</strong></span>
                    <span>Estimated<br /><strong>{money(account.estimatedBalance)}</strong></span>
                  </div>
                  <AccountOpeningBalanceEditor
                    account={account}
                    isSaving={updateAccountMutation.isPending}
                    onSave={(payload) => updateAccountMutation.mutate({ id: account.id, account: { ...account, ...payload } })}
                  />
                  <div className="mt-3 flex flex-wrap gap-1">
                    {account.aliases.map((alias) => <Badge key={alias} variant="secondary">{alias}</Badge>)}
                  </div>
                </div>
              )) : <p className="text-sm text-muted-foreground">No accounts yet.</p>}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="import" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Excel Import</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
                <div className="space-y-2">
                  <Label>Export file</Label>
                  <Input type="file" accept=".xlsx" onChange={(e) => setFile(e.target.files?.[0] || null)} />
                </div>
                <Button onClick={handlePreview} disabled={previewMutation.isPending}>
                  {previewMutation.isPending ? <RefreshCw className="mr-2 h-4 w-4 animate-spin" /> : <FileSpreadsheet className="mr-2 h-4 w-4" />}
                  Preview
                </Button>
              </div>

              {preview && (
                <div className="space-y-3">
                  <div className="flex flex-wrap gap-2">
                    <Badge variant="outline">Total {preview.totalRows}</Badge>
                    <Badge className="bg-emerald-100 text-emerald-700 hover:bg-emerald-100">New {preview.newRows}</Badge>
                    <Badge className="bg-amber-100 text-amber-700 hover:bg-amber-100">Review {preview.reviewRows}</Badge>
                    <Badge variant="secondary">Duplicate {preview.duplicateRows}</Badge>
                  </div>
                  <div className="overflow-x-auto rounded-md border">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Status</TableHead>
                          <TableHead>Date</TableHead>
                          <TableHead>Category</TableHead>
                          <TableHead>Description</TableHead>
                          <TableHead>Amount</TableHead>
                          <TableHead>Cycle</TableHead>
                          <TableHead>Decision</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {preview.rows.map((row) => (
                          <TableRow key={row.rowId}>
                            <TableCell>{statusBadge(row.status)}</TableCell>
                            <TableCell className="whitespace-nowrap text-xs">{shortDateTime(row.transactionAt)}</TableCell>
                            <TableCell className="min-w-36 text-xs">{row.category} / {row.subcategory || '-'}</TableCell>
                            <TableCell className="min-w-48 text-sm">{row.description || '-'}</TableCell>
                            <TableCell className="whitespace-nowrap text-right tabular-nums">{money(row.amount)}</TableCell>
                            <TableCell className="min-w-36 text-xs">{row.cycleLabel}</TableCell>
                            <TableCell>
                              {row.status === 'NEEDS_REVIEW' ? (
                                <select
                                  value={reviewActions[row.rowId] || 'skip'}
                                  onChange={(e) => setReviewActions({ ...reviewActions, [row.rowId]: e.target.value as 'create' | 'skip' })}
                                  className="h-8 rounded-md border border-input bg-background px-2 text-xs"
                                >
                                  <option value="skip">Skip</option>
                                  <option value="create">Create</option>
                                </select>
                              ) : row.status === 'NEW' ? (
                                <span className="text-xs text-emerald-700">Create</span>
                              ) : (
                                <span className="text-xs text-muted-foreground">Skip</span>
                              )}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                  <Button onClick={handleConfirm} disabled={confirmMutation.isPending}>
                    Confirm Import
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="recurring" className="space-y-4">
          <div className="grid gap-4 lg:grid-cols-[360px_1fr]">
            <Card>
              <CardHeader>
                <CardTitle>Create Template</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <Field label="Name" value={newBill.name} onChange={(v) => setNewBill({ ...newBill, name: v })} />
                <Field label="Provider" value={newBill.provider} onChange={(v) => setNewBill({ ...newBill, provider: v })} />
                <Field label="Category" value={newBill.category} onChange={(v) => setNewBill({ ...newBill, category: v })} />
                <Button className="w-full" onClick={() => createBillMutation.mutate(newBill)} disabled={!newBill.name}>
                  <Plus className="mr-2 h-4 w-4" />
                  Add Template
                </Button>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Create Version</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="space-y-2">
                    <Label>Template</Label>
                    <select value={versionTemplateId} onChange={(e) => setVersionTemplateId(e.target.value ? Number(e.target.value) : '')} className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm">
                      <option value="">Select template</option>
                      {recurringBills?.map((bill) => <option key={bill.id} value={bill.id}>{bill.name}</option>)}
                    </select>
                  </div>
                  <div className="space-y-2">
                    <Label>Effective Cycle</Label>
                    <select value={versionCycleId} onChange={(e) => setVersionCycleId(e.target.value ? Number(e.target.value) : '')} className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm">
                      <option value="">No cycle selected</option>
                      {cycles?.map((cycle) => <option key={cycle.id} value={cycle.id}>{cycle.label}</option>)}
                    </select>
                  </div>
                </div>
                <div className="space-y-2">
                  {versionItems.map((item, idx) => (
                    <div key={idx} className="grid grid-cols-[1fr_120px_120px] gap-2">
                      <Input value={item.itemName} onChange={(e) => updateVersionItem(idx, 'itemName', e.target.value)} />
                      <Input type="number" value={item.amount} onChange={(e) => updateVersionItem(idx, 'amount', e.target.value)} />
                      <Input value={item.itemType} onChange={(e) => updateVersionItem(idx, 'itemType', e.target.value)} />
                    </div>
                  ))}
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-muted-foreground">Expected {money(versionItems.reduce((s, i) => s + Number(i.amount || 0), 0))}</span>
                  <Button onClick={() => createVersionMutation.mutate()} disabled={!versionTemplateId}>
                    Add Version
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Recurring Bills</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {recurringBills?.length ? recurringBills.map((bill) => (
                <div key={bill.id} className="rounded-md border p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-medium">{bill.name}</p>
                      <p className="text-sm text-muted-foreground">{bill.provider} · {bill.category}</p>
                    </div>
                    <Button variant="ghost" size="icon" onClick={() => deleteBillMutation.mutate(bill.id)}>
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                  <div className="mt-3 space-y-2">
                    {bill.versions.map((version) => (
                      <div key={version.id} className="rounded-md bg-muted px-3 py-2 text-sm">
                        v{version.version} · expected {money(version.expectedAmount)} · cycle {version.effectiveCycleId || '-'}
                      </div>
                    ))}
                  </div>
                </div>
              )) : <p className="text-sm text-muted-foreground">No recurring bill templates yet.</p>}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}

function MetricCard({ title, value, icon }: { title: string; value: string; icon: ReactNode }) {
  return (
    <Card>
      <CardContent className="pt-6">
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted-foreground">{title}</span>
          <span className="text-muted-foreground">{icon}</span>
        </div>
        <p className="mt-2 text-2xl font-semibold tracking-tight">{value}</p>
      </CardContent>
    </Card>
  );
}

function AccountOpeningBalanceEditor({
  account,
  isSaving,
  onSave,
}: {
  account: FinanceAccount;
  isSaving: boolean;
  onSave: (payload: Partial<FinanceAccount>) => void;
}) {
  const [openingBalance, setOpeningBalance] = useState(String(account.openingBalance ?? 0));
  const [openingBalanceDate, setOpeningBalanceDate] = useState(account.openingBalanceDate || '');
  const [openingBalanceMemo, setOpeningBalanceMemo] = useState(account.openingBalanceMemo || '');

  return (
    <div className="mt-3 grid gap-2 rounded-md bg-muted/30 p-3">
      <div className="grid gap-2 sm:grid-cols-[1fr_140px_auto]">
        <Input
          value={openingBalance}
          inputMode="numeric"
          onChange={(e) => setOpeningBalance(e.target.value)}
          placeholder="Opening balance"
        />
        <Input
          value={openingBalanceDate}
          type="date"
          onChange={(e) => setOpeningBalanceDate(e.target.value)}
        />
        <Button
          size="sm"
          variant="outline"
          onClick={() => onSave({
            openingBalance: parseMoneyInput(openingBalance),
            openingBalanceDate: openingBalanceDate || null,
            openingBalanceMemo: openingBalanceMemo || null,
          })}
          disabled={isSaving}
        >
          Save
        </Button>
      </div>
      <Input
        value={openingBalanceMemo}
        onChange={(e) => setOpeningBalanceMemo(e.target.value)}
        placeholder="Opening balance memo"
      />
    </div>
  );
}

function Field({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      <Input value={value} onChange={(e) => onChange(e.target.value)} />
    </div>
  );
}

function SelectField({ label, value, options, onChange }: { label: string; value: string; options: string[]; onChange: (value: string) => void }) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      <select value={value} onChange={(e) => onChange(e.target.value)} className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm">
        {options.map((option) => <option key={option} value={option}>{option}</option>)}
      </select>
    </div>
  );
}

function FinanceTransactionsTable({
  transactions,
  updatingTransactionId,
  isUpdatingTime,
  onUpdateTime,
}: {
  transactions: FinanceTransaction[];
  updatingTransactionId?: number;
  isUpdatingTime: boolean;
  onUpdateTime: (id: number, time: string) => void;
}) {
  if (transactions.length === 0) {
    return <p className="text-sm text-muted-foreground">No finance transactions yet. Import an export file to begin.</p>;
  }
  return (
    <div className="overflow-x-auto rounded-md border">
      <Table>
        <TableHeader>
            <TableRow>
              <TableHead>Date</TableHead>
              <TableHead>Account</TableHead>
              <TableHead>Category</TableHead>
            <TableHead>Description</TableHead>
            <TableHead>Flow</TableHead>
            <TableHead>Amount</TableHead>
            <TableHead>Flags</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {transactions.map((tx) => (
            <TableRow key={tx.id}>
              <TableCell className="min-w-40 whitespace-nowrap text-xs">
                <TransactionTimeEditor
                  transaction={tx}
                  isSaving={isUpdatingTime && updatingTransactionId === tx.id}
                  onSave={onUpdateTime}
                />
              </TableCell>
              <TableCell className="min-w-36">
                <p className="text-sm font-medium">{tx.accountName || tx.asset || '-'}</p>
                <p className="text-xs text-muted-foreground">{tx.accountRole || (tx.accountName ? tx.accountType : 'Unmapped')}</p>
              </TableCell>
              <TableCell className="min-w-40 text-xs">{tx.category} / {tx.subcategory || '-'}</TableCell>
              <TableCell className="min-w-52">
                <p className="text-sm font-medium">{tx.description || '-'}</p>
                <p className="text-xs text-muted-foreground">{tx.asset}{tx.memo ? ` · ${tx.memo}` : ''}</p>
              </TableCell>
              <TableCell><Badge variant={tx.flowType === '수입' ? 'default' : 'secondary'}>{tx.flowType}</Badge></TableCell>
              <TableCell className="whitespace-nowrap text-right tabular-nums">
                <p>{money(tx.amount)}</p>
                {tx.spendingIncluded && spendingAmount(tx) !== Number(tx.amount) && (
                  <p className="text-xs text-muted-foreground">Spend {money(spendingAmount(tx))}</p>
                )}
              </TableCell>
              <TableCell>
                <div className="flex flex-wrap gap-1">
                  {tx.cashflowIncluded && <Badge variant="outline">Cash</Badge>}
                  {tx.spendingIncluded && spendingAmount(tx) > 0 && <Badge variant="outline">Spend</Badge>}
                  {tx.paymentMethod === '소액결제' && <Badge className="bg-sky-100 text-sky-700 hover:bg-sky-100">소액</Badge>}
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

function TransactionTimeEditor({
  transaction,
  isSaving,
  onSave,
}: {
  transaction: FinanceTransaction;
  isSaving: boolean;
  onSave: (id: number, time: string) => void;
}) {
  const [time, setTime] = useState(seoulTime(transaction.transactionAt));
  const currentTime = seoulTime(transaction.transactionAt);
  const changed = time !== currentTime;

  useEffect(() => {
    setTime(currentTime);
  }, [currentTime]);

  return (
    <div className="space-y-1">
      <p className="font-medium">{transaction.transactionDate}</p>
      <div className="flex items-center gap-2">
        <Input
          type="time"
          value={time}
          onChange={(e) => setTime(e.target.value)}
          className="h-8 w-24 text-xs"
          step={60}
        />
        <Button
          size="sm"
          variant="outline"
          className="h-8 px-2 text-xs"
          onClick={() => onSave(transaction.id, time)}
          disabled={!changed || !time || isSaving}
        >
          Save
        </Button>
      </div>
      {transaction.timeAdjusted && <Badge variant="secondary">Adjusted</Badge>}
    </div>
  );
}
