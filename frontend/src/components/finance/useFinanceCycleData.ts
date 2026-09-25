import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import {
  accountFlowSummaries,
  buildFinanceWeekOptions,
  cashflowAmount,
  cyclePeriod,
  deferredSpendingAmount,
  externalCashOutAmount,
  filterTransactionsByPeriod,
  isLiabilityAccount,
  isLiabilityTransaction,
  spendingAmount,
} from '@/components/finance/utils';

/**
 * Owns the cross-cutting finance data (cycles, transactions, accounts) and every
 * derived value that is shared across multiple tabs of the Finance page.
 */
export function useFinanceCycleData() {
  const [selectedCycleId, setSelectedCycleId] = useState<number | undefined>();
  const [selectedWeekKey, setSelectedWeekKey] = useState('ALL');

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

  const { data: accounts } = useQuery({
    queryKey: ['financeAccounts', activeCycleId],
    queryFn: () => api.finance.accounts(activeCycleId),
  });

  useEffect(() => {
    setSelectedWeekKey('ALL');
  }, [activeCycleId]);

  const weekOptions = useMemo(
    () => buildFinanceWeekOptions(activeCycle, transactions || []),
    [activeCycle, transactions]
  );

  const selectedWeek = weekOptions.find((option) => option.key === selectedWeekKey) || weekOptions[0];
  const selectedPeriod = selectedWeek || cyclePeriod(activeCycle, transactions || []);
  const selectedPeriodKind: 'cycle' | 'week' = selectedWeekKey === 'ALL' ? 'cycle' : 'week';

  const selectedPeriodTransactions = useMemo(
    () => filterTransactionsByPeriod(transactions || [], selectedPeriod),
    [selectedPeriod, transactions]
  );

  const prePeriodTransactions = useMemo(() => {
    const cycleStart = cyclePeriod(activeCycle, transactions || []).start;
    return (transactions || []).filter(
      (tx) => tx.transactionDate >= cycleStart && tx.transactionDate < selectedPeriod.start
    );
  }, [activeCycle, selectedPeriod.start, transactions]);

  const totals = useMemo(() => {
    const list = selectedPeriodTransactions;
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
  }, [selectedPeriodTransactions]);

  const categoryTotals = useMemo(() => {
    const map = new Map<string, number>();
    selectedPeriodTransactions.forEach((t) => {
      if (!t.spendingIncluded || t.flowType === '수입') return;
      const amount = spendingAmount(t);
      if (amount <= 0) return;
      const key = t.category || 'Uncategorized';
      map.set(key, (map.get(key) || 0) + amount);
    });
    return Array.from(map.entries()).sort((a, b) => b[1] - a[1]);
  }, [selectedPeriodTransactions]);

  const categoryChartData = useMemo(
    () => categoryTotals.map(([name, value]) => ({ name, value })),
    [categoryTotals]
  );

  const categoryChartTotal = useMemo(
    () => categoryChartData.reduce((sum, item) => sum + item.value, 0),
    [categoryChartData]
  );

  const accountTotals = useMemo(() => {
    return accountFlowSummaries(accounts || [], selectedPeriodTransactions, prePeriodTransactions)
      .filter((account) => !isLiabilityAccount(account))
      .sort((a, b) => Math.abs(Number(b.cycleNetFlow)) - Math.abs(Number(a.cycleNetFlow)))
      .slice(0, 8);
  }, [accounts, prePeriodTransactions, selectedPeriodTransactions]);

  const liabilityTotals = useMemo(() => {
    const map = new Map<string, { used: number; settled: number }>();
    selectedPeriodTransactions.forEach((tx) => {
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
  }, [selectedPeriodTransactions]);

  const accountCardSummaries = useMemo(
    () => accountFlowSummaries(accounts || [], selectedPeriodTransactions, prePeriodTransactions),
    [accounts, prePeriodTransactions, selectedPeriodTransactions]
  );

  const unmappedAssets = useMemo(() => {
    const map = new Map<string, { count: number; cashOut: number; income: number }>();
    const mappedAliases = new Set<string>();
    (accounts || []).forEach((account) => {
      mappedAliases.add(account.name);
      (account.aliases || []).forEach((alias) => mappedAliases.add(alias));
    });
    selectedPeriodTransactions.forEach((tx) => {
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
  }, [accounts, selectedPeriodTransactions]);

  return {
    cycles,
    cyclesLoading,
    selectedCycleId,
    setSelectedCycleId,
    activeCycleId,
    activeCycle,
    transactions,
    transactionsLoading,
    accounts,
    selectedWeekKey,
    setSelectedWeekKey,
    weekOptions,
    selectedPeriod,
    selectedPeriodKind,
    selectedPeriodTransactions,
    prePeriodTransactions,
    totals,
    categoryTotals,
    categoryChartData,
    categoryChartTotal,
    accountTotals,
    liabilityTotals,
    accountCardSummaries,
    unmappedAssets,
  };
}
