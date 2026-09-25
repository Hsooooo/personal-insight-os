import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { FinanceHeader } from '@/components/finance/FinanceHeader';
import { OverviewTab } from '@/components/finance/OverviewTab';
import { TransactionsTab } from '@/components/finance/TransactionsTab';
import { AccountsTab } from '@/components/finance/AccountsTab';
import { ImportTab } from '@/components/finance/ImportTab';
import { RecurringTab } from '@/components/finance/RecurringTab';
import { useFinanceCycleData } from '@/components/finance/useFinanceCycleData';

export default function Finance() {
  const {
    cycles,
    cyclesLoading,
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
    totals,
    categoryTotals,
    categoryChartData,
    categoryChartTotal,
    accountTotals,
    liabilityTotals,
    accountCardSummaries,
    unmappedAssets,
  } = useFinanceCycleData();

  const { data: recurringBills } = useQuery({
    queryKey: ['financeRecurringBills'],
    queryFn: api.finance.recurringBills,
  });

  const handleCycleChange = (cycleId: number | undefined) => {
    setSelectedCycleId(cycleId);
    setSelectedWeekKey('ALL');
  };

  return (
    <div className="space-y-6">
      <FinanceHeader
        activeCycle={activeCycle}
        activeCycleId={activeCycleId}
        cycles={cycles}
        cyclesLoading={cyclesLoading}
        transactionsLoading={transactionsLoading}
        selectedWeekKey={selectedWeekKey}
        weekOptions={weekOptions}
        selectedPeriod={selectedPeriod}
        selectedPeriodKind={selectedPeriodKind}
        transactions={transactions}
        accountTotals={accountTotals}
        onCycleChange={handleCycleChange}
        onWeekChange={setSelectedWeekKey}
      />

      <Tabs defaultValue="overview" className="space-y-4">
        <TabsList className="flex h-auto flex-wrap justify-start">
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="transactions">Transactions</TabsTrigger>
          <TabsTrigger value="accounts">Accounts</TabsTrigger>
          <TabsTrigger value="import">Import</TabsTrigger>
          <TabsTrigger value="recurring">Recurring</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="space-y-4">
          <OverviewTab
            totals={totals}
            transactionsLoading={transactionsLoading}
            categoryTotals={categoryTotals}
            categoryChartData={categoryChartData}
            categoryChartTotal={categoryChartTotal}
            accountTotals={accountTotals}
            liabilityTotals={liabilityTotals}
          />
        </TabsContent>

        {/* 탭별 입력 state(필터·선택·import 미리보기·폼)가 탭 전환 시 초기화되지 않도록 항상 마운트 */}
        <TabsContent value="transactions" forceMount className="space-y-4 data-[state=inactive]:hidden">
          <TransactionsTab
            selectedPeriodTransactions={selectedPeriodTransactions}
            transactionsLoading={transactionsLoading}
          />
        </TabsContent>

        <TabsContent value="accounts" forceMount className="space-y-4 data-[state=inactive]:hidden">
          <AccountsTab
            accounts={accounts}
            accountCardSummaries={accountCardSummaries}
            unmappedAssets={unmappedAssets}
          />
        </TabsContent>

        <TabsContent value="import" forceMount className="space-y-4 data-[state=inactive]:hidden">
          <ImportTab />
        </TabsContent>

        <TabsContent value="recurring" forceMount className="space-y-4 data-[state=inactive]:hidden">
          <RecurringTab cycles={cycles} recurringBills={recurringBills} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
