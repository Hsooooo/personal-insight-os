import type { ReactNode } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { ArrowDownUp, FileSpreadsheet, ReceiptText, WalletCards } from 'lucide-react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { CATEGORY_COLORS, money, percent } from '@/components/finance/utils';

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

export function OverviewTab({
  totals,
  transactionsLoading,
  categoryTotals,
  categoryChartData,
  categoryChartTotal,
  accountTotals,
  liabilityTotals,
}: {
  totals: {
    income: number;
    cashflowOut: number;
    deferredSpending: number;
    spending: number;
    netExternalCashflow: number;
    count: number;
  };
  transactionsLoading: boolean;
  categoryTotals: Array<[string, number]>;
  categoryChartData: Array<{ name: string; value: number }>;
  categoryChartTotal: number;
  accountTotals: Array<{
    id: number;
    name: string;
    periodOpeningBalance: number;
    cycleIncome: number;
    cycleCashOut: number;
    cycleNetFlow: number;
    estimatedBalance: number;
  }>;
  liabilityTotals: Array<{ name: string; used: number; settled: number; net: number }>;
}) {
  return (
    <div className="space-y-4">
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
            <div className="min-w-[920px] space-y-2">
              <div className="grid grid-cols-[1.4fr_130px_130px_130px_130px_140px] gap-3 px-3 text-xs font-medium text-muted-foreground">
                <span>Account</span>
                <span className="text-right">Opening</span>
                <span className="text-right">In</span>
                <span className="text-right">Out</span>
                <span className="text-right">Net</span>
                <span className="text-right">Closing</span>
              </div>
              {accountTotals.length === 0 && <p className="px-3 py-4 text-sm text-muted-foreground">No account flow in this cycle yet.</p>}
              {accountTotals.map((account) => (
                <div key={account.id} className="grid grid-cols-[1.4fr_130px_130px_130px_130px_140px] items-center gap-3 rounded-md border bg-background px-3 py-2 text-sm">
                  <span className="min-w-0 truncate font-medium">{account.name}</span>
                  <span className="whitespace-nowrap text-right text-muted-foreground tabular-nums">{money(account.periodOpeningBalance)}</span>
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
    </div>
  );
}
