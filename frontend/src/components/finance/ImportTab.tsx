import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { FileSpreadsheet, RefreshCw } from 'lucide-react';
import type { FinanceImportPreviewResponse, FinanceImportRow } from '@/types';
import { money, shortDateTime } from '@/components/finance/utils';

function statusBadge(status: FinanceImportRow['status']) {
  if (status === 'NEW') return <Badge className="bg-emerald-100 text-emerald-700 hover:bg-emerald-100">New</Badge>;
  if (status === 'NEEDS_REVIEW') return <Badge className="bg-amber-100 text-amber-700 hover:bg-amber-100">Review</Badge>;
  return <Badge variant="secondary">Duplicate</Badge>;
}

export function ImportTab() {
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<FinanceImportPreviewResponse | null>(null);
  const [reviewActions, setReviewActions] = useState<Record<string, 'create' | 'skip'>>({});

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

  return (
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
  );
}
