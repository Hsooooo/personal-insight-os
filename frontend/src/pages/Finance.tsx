import { useMemo, useState } from 'react';
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
import { ArrowDownUp, FileSpreadsheet, Link2, Plus, ReceiptText, RefreshCw, Trash2, WalletCards } from 'lucide-react';
import type { FinanceAccount, FinanceImportPreviewResponse, FinanceImportRow, FinanceTransaction, RecurringBillItem } from '@/types';

function money(value: number | null | undefined) {
  if (value == null) return '-';
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW', maximumFractionDigits: 0 }).format(value);
}

function shortDateTime(value: string | null | undefined) {
  if (!value) return '-';
  return value.slice(0, 16).replace('T', ' ');
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
  });

  const { data: cycles, isLoading: cyclesLoading } = useQuery({
    queryKey: ['financeCycles'],
    queryFn: api.finance.cycles,
  });

  const activeCycleId = selectedCycleId || cycles?.[0]?.id;

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
      setNewAccount({ name: '', accountType: 'BANK_ACCOUNT', role: 'OTHER', institution: '', aliases: '', memo: '' });
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

  const totals = useMemo(() => {
    const list = transactions || [];
    return {
      cashflowOut: list.filter((t) => t.cashflowIncluded && t.flowType !== '수입').reduce((sum, t) => sum + Number(t.amount), 0),
      spending: list.filter((t) => t.spendingIncluded && t.flowType !== '수입').reduce((sum, t) => sum + Number(t.amount), 0),
      income: list.filter((t) => t.flowType === '수입').reduce((sum, t) => sum + Number(t.amount), 0),
      count: list.length,
    };
  }, [transactions]);

  const categoryTotals = useMemo(() => {
    const map = new Map<string, number>();
    (transactions || []).forEach((t) => {
      if (!t.spendingIncluded || t.flowType === '수입') return;
      const key = t.category || 'Uncategorized';
      map.set(key, (map.get(key) || 0) + Number(t.amount));
    });
    return Array.from(map.entries()).sort((a, b) => b[1] - a[1]).slice(0, 8);
  }, [transactions]);

  const accountTotals = useMemo(() => {
    const map = new Map<string, { income: number; cashOut: number; net: number }>();
    (transactions || []).forEach((tx) => {
      const key = tx.accountName || tx.asset || 'Unmapped';
      const current = map.get(key) || { income: 0, cashOut: 0, net: 0 };
      if (tx.flowType === '수입') current.income += Number(tx.amount);
      if (tx.flowType !== '수입' && tx.cashflowIncluded) current.cashOut += Number(tx.amount);
      current.net = current.income - current.cashOut;
      map.set(key, current);
    });
    return Array.from(map.entries()).sort((a, b) => Math.abs(b[1].net) - Math.abs(a[1].net)).slice(0, 8);
  }, [transactions]);

  const unmappedAssets = useMemo(() => {
    const map = new Map<string, { count: number; cashOut: number; income: number }>();
    (transactions || []).forEach((tx) => {
      if (tx.accountId || !tx.asset) return;
      const current = map.get(tx.asset) || { count: 0, cashOut: 0, income: 0 };
      current.count += 1;
      if (tx.flowType === '수입') current.income += Number(tx.amount);
      if (tx.flowType !== '수입' && tx.cashflowIncluded) current.cashOut += Number(tx.amount);
      map.set(tx.asset, current);
    });
    return Array.from(map.entries()).sort((a, b) => b[1].count - a[1].count);
  }, [transactions]);

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
    });
  };

  const handleCreateAccount = () => {
    createAccountMutation.mutate({
      ...newAccount,
      aliases: newAccount.aliases.split(',').map((v) => v.trim()).filter(Boolean),
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

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Finance</h2>
          <p className="text-muted-foreground">Salary-cycle spending, cashflow, and recurring bill profiles</p>
        </div>
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

      <Tabs defaultValue="overview" className="space-y-4">
        <TabsList className="flex h-auto flex-wrap justify-start">
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="transactions">Transactions</TabsTrigger>
          <TabsTrigger value="accounts">Accounts</TabsTrigger>
          <TabsTrigger value="import">Import</TabsTrigger>
          <TabsTrigger value="recurring">Recurring</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="space-y-4">
          <div className="grid gap-4 md:grid-cols-4">
            <MetricCard title="Income" value={money(totals.income)} icon={<WalletCards className="h-4 w-4" />} />
            <MetricCard title="Cash Out" value={money(totals.cashflowOut)} icon={<ArrowDownUp className="h-4 w-4" />} />
            <MetricCard title="Spending" value={money(totals.spending)} icon={<ReceiptText className="h-4 w-4" />} />
            <MetricCard title="Rows" value={String(totals.count)} icon={<FileSpreadsheet className="h-4 w-4" />} />
          </div>
          <Card>
            <CardHeader>
              <CardTitle>Spending Categories</CardTitle>
            </CardHeader>
            <CardContent>
              {transactionsLoading ? <Skeleton className="h-32" /> : (
                <div className="space-y-3">
                  {categoryTotals.length === 0 && <p className="text-sm text-muted-foreground">No spending rows in this cycle yet.</p>}
                  {categoryTotals.map(([category, amount]) => (
                    <div key={category} className="flex items-center justify-between rounded-md border px-3 py-2">
                      <span className="text-sm font-medium">{category}</span>
                      <span className="text-sm tabular-nums">{money(amount)}</span>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Account Flow</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-3">
                {accountTotals.length === 0 && <p className="text-sm text-muted-foreground">No account flow in this cycle yet.</p>}
                {accountTotals.map(([account, flow]) => (
                  <div key={account} className="grid gap-2 rounded-md border px-3 py-2 text-sm sm:grid-cols-[1fr_120px_120px_120px]">
                    <span className="font-medium">{account}</span>
                    <span className="text-muted-foreground">In {money(flow.income)}</span>
                    <span className="text-muted-foreground">Out {money(flow.cashOut)}</span>
                    <span className="text-right font-medium tabular-nums">{money(flow.net)}</span>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="transactions">
          <Card>
            <CardHeader>
              <CardTitle>Transactions</CardTitle>
            </CardHeader>
            <CardContent>
              {transactionsLoading ? <Skeleton className="h-48" /> : (
                <FinanceTransactionsTable transactions={transactions || []} />
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
                  <div className="mt-3 grid grid-cols-3 gap-2 text-xs">
                    <span>In<br /><strong>{money(account.cycleIncome)}</strong></span>
                    <span>Out<br /><strong>{money(account.cycleCashOut)}</strong></span>
                    <span>Net<br /><strong>{money(account.cycleNetFlow)}</strong></span>
                  </div>
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

function FinanceTransactionsTable({ transactions }: { transactions: FinanceTransaction[] }) {
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
              <TableCell className="whitespace-nowrap text-xs">{shortDateTime(tx.transactionAt)}</TableCell>
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
              <TableCell className="whitespace-nowrap text-right tabular-nums">{money(tx.amount)}</TableCell>
              <TableCell>
                <div className="flex flex-wrap gap-1">
                  {tx.cashflowIncluded && <Badge variant="outline">Cash</Badge>}
                  {tx.spendingIncluded && <Badge variant="outline">Spend</Badge>}
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
