import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { toast } from 'sonner';
import { Check, CalendarDays, Copy, ReceiptText } from 'lucide-react';
import type { FinanceCycle, FinanceTransaction } from '@/types';
import { formatFinanceWeeklySummary, formatSummaryRange, seoulDate } from '@/components/finance/utils';

export function FinanceHeader({
  activeCycle,
  activeCycleId,
  cycles,
  cyclesLoading,
  transactionsLoading,
  selectedWeekKey,
  weekOptions,
  selectedPeriod,
  selectedPeriodKind,
  transactions,
  accountTotals,
  onCycleChange,
  onWeekChange,
}: {
  activeCycle: FinanceCycle | undefined;
  activeCycleId: number | undefined;
  cycles: FinanceCycle[] | undefined;
  cyclesLoading: boolean;
  transactionsLoading: boolean;
  selectedWeekKey: string;
  weekOptions: Array<{ key: string; label: string; start: string; end: string }>;
  selectedPeriod: { start: string; end: string };
  selectedPeriodKind: 'cycle' | 'week';
  transactions: FinanceTransaction[] | undefined;
  accountTotals: Array<{
    name: string;
    periodOpeningBalance: number;
    cycleIncome: number;
    cycleCashOut: number;
    cycleNetFlow: number;
    estimatedBalance: number;
  }>;
  onCycleChange: (cycleId: number | undefined) => void;
  onWeekChange: (weekKey: string) => void;
}) {
  const [copiedWeeklyFinance, setCopiedWeeklyFinance] = useState(false);

  const handleCopyFinanceWeeklySummary = async () => {
    const report = formatFinanceWeeklySummary(
      transactions || [],
      selectedPeriod,
      activeCycle?.label,
      selectedPeriodKind,
      accountTotals
    );
    await navigator.clipboard.writeText(report);
    setCopiedWeeklyFinance(true);
    toast.success(selectedPeriodKind === 'week' ? 'Finance weekly summary copied' : 'Finance cycle summary copied');
    setTimeout(() => setCopiedWeeklyFinance(false), 2000);
  };

  return (
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
              Summary range {formatSummaryRange(selectedPeriod, selectedPeriodKind)}
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
            {copiedWeeklyFinance ? 'Copied!' : selectedPeriodKind === 'week' ? 'Copy Weekly Summary' : 'Copy Cycle Summary'}
          </Button>
          <select
            value={activeCycleId || ''}
            onChange={(e) => onCycleChange(e.target.value ? Number(e.target.value) : undefined)}
            className="h-10 rounded-md border border-input bg-background px-3 text-sm"
          >
            {cyclesLoading && <option>Loading cycles...</option>}
            {cycles?.map((cycle) => (
              <option key={cycle.id} value={cycle.id}>{cycle.label}</option>
            ))}
          </select>
          <select
            value={selectedWeekKey}
            onChange={(e) => onWeekChange(e.target.value)}
            className="h-10 rounded-md border border-input bg-background px-3 text-sm"
            disabled={!activeCycleId || transactionsLoading}
          >
            {weekOptions.map((option) => (
              <option key={option.key} value={option.key}>{option.label}</option>
            ))}
          </select>
        </div>
      </div>
    </div>
  );
}
