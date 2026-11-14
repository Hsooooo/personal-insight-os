import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { AlertTriangle, Key, Trash2 } from 'lucide-react';
import { api, getErrorMessage } from '@/lib/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { QueryError } from '@/components/common/QueryError';
import type { ApiKey } from '@/types';

interface ApiKeySectionProps {
  selectedKeyId: number | null;
  justIssuedKeyId: number | null;
  onSelect: (id: number) => void;
  onCreated: (key: ApiKey) => void;
  onDeleted: (id: number) => void;
}

export function ApiKeySection({ selectedKeyId, justIssuedKeyId, onSelect, onCreated, onDeleted }: ApiKeySectionProps) {
  const queryClient = useQueryClient();
  const [newKeyName, setNewKeyName] = useState('');
  const [pendingDelete, setPendingDelete] = useState<ApiKey | null>(null);

  const keysQuery = useQuery({ queryKey: ['apiKeys'], queryFn: api.apiKeys.list });

  const createMutation = useMutation({
    mutationFn: api.apiKeys.create,
    onSuccess: (data) => {
      if (data.key) onCreated(data);
      setNewKeyName('');
      void queryClient.invalidateQueries({ queryKey: ['apiKeys'] });
      toast.success('API 키가 발행되었습니다');
    },
    onError: (err) => toast.error(getErrorMessage(err, 'API 키 발행에 실패했습니다')),
  });

  const deleteMutation = useMutation({
    mutationFn: api.apiKeys.delete,
    onSuccess: (_, id) => {
      onDeleted(id);
      setPendingDelete(null);
      void queryClient.invalidateQueries({ queryKey: ['apiKeys'] });
      toast.success('API 키가 삭제되었습니다');
    },
    onError: (err) => toast.error(getErrorMessage(err, 'API 키 삭제에 실패했습니다')),
  });

  const handleCreate = () => {
    const name = newKeyName.trim();
    if (!name) return;
    createMutation.mutate(name);
  };

  const keys = keysQuery.data ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Key className="h-4 w-4" aria-hidden="true" />
          API 키 (헤드리스 클라이언트용)
        </CardTitle>
        <CardDescription>
          브라우저 로그인(OAuth)을 할 수 없는 서버·스크립트 환경에서 사용할 키를 발행합니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            handleCreate();
          }}
        >
          <Label htmlFor="new-api-key-name" className="sr-only">
            API 키 이름
          </Label>
          <Input
            id="new-api-key-name"
            placeholder="에이전트 이름 (예: 홈서버 cron)"
            value={newKeyName}
            onChange={(e) => setNewKeyName(e.target.value)}
          />
          <Button type="submit" disabled={createMutation.isPending || !newKeyName.trim()}>
            {createMutation.isPending ? '발행 중...' : '발행'}
          </Button>
        </form>

        {keysQuery.isPending ? (
          <div className="h-20 animate-pulse rounded-lg border bg-muted" aria-busy="true" />
        ) : keysQuery.isError ? (
          <QueryError compact error={keysQuery.error} onRetry={() => void keysQuery.refetch()} />
        ) : keys.length > 0 ? (
          <div className="space-y-2">
            <p className="text-sm font-medium" id="api-key-list-label">
              발행된 키 목록
            </p>
            <div className="space-y-1" role="radiogroup" aria-labelledby="api-key-list-label">
              {keys.map((key) => {
                const inputId = `api-key-${key.id}`;
                return (
                  <div
                    key={key.id}
                    className={`flex items-center justify-between rounded-md border px-3 py-2 text-sm ${
                      selectedKeyId === key.id ? 'border-accent bg-accent' : ''
                    }`}
                  >
                    <label htmlFor={inputId} className="flex flex-1 cursor-pointer items-center gap-2">
                      <input
                        id={inputId}
                        type="radio"
                        name="selectedKey"
                        checked={selectedKeyId === key.id}
                        onChange={() => onSelect(key.id)}
                        className="h-3.5 w-3.5"
                      />
                      <span className="font-medium">{key.name}</span>
                      {justIssuedKeyId === key.id && (
                        <Badge variant="secondary" className="text-xs">
                          방금 발급
                        </Badge>
                      )}
                      <Badge variant="outline" className="text-xs">
                        {new Date(key.createdAt).toLocaleDateString('ko-KR')}
                      </Badge>
                    </label>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7 text-destructive hover:text-destructive"
                      onClick={() => setPendingDelete(key)}
                      aria-label={`API 키 ${key.name} 삭제`}
                    >
                      <Trash2 className="h-3 w-3" aria-hidden="true" />
                    </Button>
                  </div>
                );
              })}
            </div>
            <p className="text-xs text-muted-foreground">
              <AlertTriangle className="mr-1 inline h-3 w-3" aria-hidden="true" />
              보안을 위해 키 원문은 발급 직후에만 표시되며 브라우저에 저장되지 않습니다. 키를 잃어버렸다면 삭제 후
              재발급하세요.
            </p>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">발행된 API 키가 없습니다.</p>
        )}
      </CardContent>

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => {
          if (!open) setPendingDelete(null);
        }}
        title="API 키를 삭제할까요?"
        description={
          pendingDelete
            ? `"${pendingDelete.name}" 키를 사용하는 클라이언트는 즉시 PIOS에 접근할 수 없게 됩니다.`
            : undefined
        }
        confirmLabel="삭제"
        destructive
        pending={deleteMutation.isPending}
        onConfirm={() => {
          if (pendingDelete) deleteMutation.mutate(pendingDelete.id);
        }}
      />
    </Card>
  );
}
