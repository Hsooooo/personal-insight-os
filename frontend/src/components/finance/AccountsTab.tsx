import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Link2, Plus, Trash2 } from 'lucide-react';
import type { FinanceAccount } from '@/types';
import { Field, SelectField } from '@/components/finance/FormFields';
import { accountRoleOptions, accountTypeOptions, money, parseMoneyInput, suggestAccount } from '@/components/finance/utils';

const initialNewAccount = {
  name: '',
  accountType: 'BANK_ACCOUNT',
  role: 'OTHER',
  institution: '',
  aliases: '',
  memo: '',
  openingBalance: '',
  openingBalanceDate: '',
  openingBalanceMemo: '',
};

export function AccountsTab({
  accounts,
  accountCardSummaries,
  unmappedAssets,
}: {
  accounts: FinanceAccount[] | undefined;
  accountCardSummaries: FinanceAccount[];
  unmappedAssets: Array<[string, { count: number; cashOut: number; income: number }]>;
}) {
  const queryClient = useQueryClient();
  const [newAccount, setNewAccount] = useState(initialNewAccount);

  const createAccountMutation = useMutation({
    mutationFn: api.finance.createAccount,
    onSuccess: () => {
      toast.success('계좌를 만들고 거래를 매핑했습니다');
      setNewAccount(initialNewAccount);
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

  return (
    <div className="space-y-4">
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
          {accountCardSummaries.length ? accountCardSummaries.map((account) => (
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
              <div className="mt-3 grid grid-cols-2 gap-2 text-xs sm:grid-cols-6">
                <span>Seed<br /><strong>{money(account.openingBalance)}</strong></span>
                <span>Opening<br /><strong>{money(account.periodOpeningBalance)}</strong></span>
                <span>In<br /><strong>{money(account.cycleIncome)}</strong></span>
                <span>Out<br /><strong>{money(account.cycleCashOut)}</strong></span>
                <span>Net<br /><strong>{money(account.cycleNetFlow)}</strong></span>
                <span>Closing<br /><strong>{money(account.estimatedBalance)}</strong></span>
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
    </div>
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
          placeholder="Seed opening balance"
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
