import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Plus, Trash2 } from 'lucide-react';
import type { FinanceCycle, RecurringBill, RecurringBillItem } from '@/types';
import { Field } from '@/components/finance/FormFields';
import { ktDefaultItems, money } from '@/components/finance/utils';

export function RecurringTab({
  cycles,
  recurringBills,
}: {
  cycles: FinanceCycle[] | undefined;
  recurringBills: RecurringBill[] | undefined;
}) {
  const queryClient = useQueryClient();
  const [newBill, setNewBill] = useState({ name: 'KT 통신비', provider: 'KT', category: '주거/통신', memo: '' });
  const [versionTemplateId, setVersionTemplateId] = useState<number | ''>('');
  const [versionCycleId, setVersionCycleId] = useState<number | ''>('');
  const [versionItems, setVersionItems] = useState<RecurringBillItem[]>(ktDefaultItems);

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

  const updateVersionItem = (idx: number, field: keyof RecurringBillItem, value: string) => {
    setVersionItems((items) =>
      items.map((item, i) => i === idx ? { ...item, [field]: field === 'amount' || field === 'sortOrder' ? Number(value) : value } : item)
    );
  };

  return (
    <div className="space-y-4">
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
    </div>
  );
}
