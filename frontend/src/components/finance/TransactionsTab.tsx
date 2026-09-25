import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { FilterX, Search, Trash2 } from 'lucide-react';
import type { FinanceTransaction } from '@/types';
import { externalCashOutAmount, deferredSpendingAmount, money, seoulTime, spendingAmount } from '@/components/finance/utils';

const initialFilters = {
  search: '',
  flowType: 'ALL',
  account: 'ALL',
  category: 'ALL',
  inclusion: 'ALL',
  from: '',
  to: '',
};

export function TransactionsTab({
  selectedPeriodTransactions,
  transactionsLoading,
}: {
  selectedPeriodTransactions: FinanceTransaction[];
  transactionsLoading: boolean;
}) {
  const queryClient = useQueryClient();
  const [transactionFilters, setTransactionFilters] = useState(initialFilters);
  const [selectedTransactionIds, setSelectedTransactionIds] = useState<number[]>([]);

  const updateTransactionTimeMutation = useMutation({
    mutationFn: ({ id, time }: { id: number; time: string }) => api.finance.updateTransactionTime(id, { time }),
    onSuccess: () => {
      toast.success('거래 시간을 보정했습니다');
      queryClient.invalidateQueries({ queryKey: ['financeTransactions'] });
      queryClient.invalidateQueries({ queryKey: ['financeAccounts'] });
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : 'Transaction time update failed'),
  });

  const deleteTransactionsMutation = useMutation({
    mutationFn: api.finance.deleteTransactions,
    onSuccess: (data) => {
      toast.success(`${data.deleted}개 거래를 삭제했습니다`);
      setSelectedTransactionIds([]);
      queryClient.invalidateQueries({ queryKey: ['financeTransactions'] });
      queryClient.invalidateQueries({ queryKey: ['financeAccounts'] });
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : 'Transaction delete failed'),
  });

  const transactionFilterOptions = useMemo(() => {
    const list = selectedPeriodTransactions;
    return {
      accounts: Array.from(new Set(list.map((tx) => tx.accountName || tx.asset || 'Unmapped'))).sort(),
      categories: Array.from(new Set(list.map((tx) => tx.category || 'Uncategorized'))).sort(),
      flowTypes: Array.from(new Set(list.map((tx) => tx.flowType).filter(Boolean))).sort(),
    };
  }, [selectedPeriodTransactions]);

  const filteredTransactions = useMemo(() => {
    const search = transactionFilters.search.trim().toLowerCase();
    return selectedPeriodTransactions.filter((tx) => {
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
  }, [selectedPeriodTransactions, transactionFilters]);

  const filteredTransactionTotals = useMemo(() => ({
    cashflowOut: filteredTransactions.reduce((sum, t) => sum + externalCashOutAmount(t), 0),
    deferredSpending: filteredTransactions.reduce((sum, t) => sum + deferredSpendingAmount(t), 0),
    spending: filteredTransactions.reduce((sum, t) => sum + spendingAmount(t), 0),
    income: filteredTransactions.filter((t) => t.flowType === '수입').reduce((sum, t) => sum + Number(t.amount), 0),
  }), [filteredTransactions]);

  useEffect(() => {
    const visibleIds = new Set(filteredTransactions.map((tx) => tx.id));
    setSelectedTransactionIds((ids) => ids.filter((id) => visibleIds.has(id)));
  }, [filteredTransactions]);

  const resetTransactionFilters = () => setTransactionFilters(initialFilters);

  const handleToggleTransactionSelection = (id: number, checked: boolean) => {
    setSelectedTransactionIds((ids) => {
      if (checked) return ids.includes(id) ? ids : [...ids, id];
      return ids.filter((selectedId) => selectedId !== id);
    });
  };

  const handleToggleAllVisibleTransactions = (checked: boolean) => {
    setSelectedTransactionIds(checked ? filteredTransactions.map((tx) => tx.id) : []);
  };

  const handleDeleteSelectedTransactions = () => {
    if (selectedTransactionIds.length === 0 || deleteTransactionsMutation.isPending) return;
    deleteTransactionsMutation.mutate(selectedTransactionIds);
  };

  return (
    <Card>
      <CardHeader className="space-y-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <CardTitle>Transactions</CardTitle>
            <p className="text-sm text-muted-foreground">
              {filteredTransactions.length} of {selectedPeriodTransactions.length} rows · income {money(filteredTransactionTotals.income)} · external out {money(filteredTransactionTotals.cashflowOut)} · deferred {money(filteredTransactionTotals.deferredSpending)} · actual spend {money(filteredTransactionTotals.spending)}
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
          <div className="space-y-3">
            <div className="flex flex-col gap-2 rounded-md border bg-muted/30 p-3 text-sm sm:flex-row sm:items-center sm:justify-between">
              <span className="text-muted-foreground">
                {selectedTransactionIds.length > 0
                  ? `${selectedTransactionIds.length} selected`
                  : 'Select rows to delete transactions'}
              </span>
              <Button
                size="sm"
                variant="destructive"
                className="gap-2"
                disabled={selectedTransactionIds.length === 0 || deleteTransactionsMutation.isPending}
                onClick={handleDeleteSelectedTransactions}
              >
                <Trash2 className="h-4 w-4" />
                {deleteTransactionsMutation.isPending ? 'Deleting...' : 'Delete Selected'}
              </Button>
            </div>
            <FinanceTransactionsTable
              transactions={filteredTransactions}
              selectedIds={selectedTransactionIds}
              updatingTransactionId={updateTransactionTimeMutation.variables?.id}
              isUpdatingTime={updateTransactionTimeMutation.isPending}
              onToggleTransaction={handleToggleTransactionSelection}
              onToggleAll={handleToggleAllVisibleTransactions}
              onUpdateTime={(id, time) => updateTransactionTimeMutation.mutate({ id, time })}
            />
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function FinanceTransactionsTable({
  transactions,
  selectedIds,
  updatingTransactionId,
  isUpdatingTime,
  onToggleTransaction,
  onToggleAll,
  onUpdateTime,
}: {
  transactions: FinanceTransaction[];
  selectedIds: number[];
  updatingTransactionId?: number;
  isUpdatingTime: boolean;
  onToggleTransaction: (id: number, checked: boolean) => void;
  onToggleAll: (checked: boolean) => void;
  onUpdateTime: (id: number, time: string) => void;
}) {
  if (transactions.length === 0) {
    return <p className="text-sm text-muted-foreground">No finance transactions yet. Import an export file to begin.</p>;
  }
  const selectedSet = new Set(selectedIds);
  const allVisibleSelected = transactions.length > 0 && transactions.every((tx) => selectedSet.has(tx.id));
  return (
    <div className="overflow-x-auto rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-10">
              <input
                type="checkbox"
                checked={allVisibleSelected}
                onChange={(event) => onToggleAll(event.target.checked)}
                aria-label="Select all visible transactions"
                className="h-4 w-4 rounded border-input"
              />
            </TableHead>
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
              <TableCell>
                <input
                  type="checkbox"
                  checked={selectedSet.has(tx.id)}
                  onChange={(event) => onToggleTransaction(tx.id, event.target.checked)}
                  aria-label={`Select transaction ${tx.id}`}
                  className="h-4 w-4 rounded border-input"
                />
              </TableCell>
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
