import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { AppWindow, Unplug } from 'lucide-react';
import { api, getErrorMessage } from '@/lib/api';
import { describeScope } from '@/lib/oauth';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { QueryError } from '@/components/common/QueryError';
import type { OAuthGrant } from '@/types';

function formatDateTime(value: string | null): string {
  if (!value) return '사용 기록 없음';
  return new Date(value).toLocaleString('ko-KR');
}

export function OAuthGrantsSection() {
  const queryClient = useQueryClient();
  const [pendingRevoke, setPendingRevoke] = useState<OAuthGrant | null>(null);

  const grantsQuery = useQuery({ queryKey: ['oauthGrants'], queryFn: api.oauth.grants });

  const revokeMutation = useMutation({
    mutationFn: (id: number) => api.oauth.revokeGrant(id),
    onSuccess: () => {
      setPendingRevoke(null);
      void queryClient.invalidateQueries({ queryKey: ['oauthGrants'] });
      toast.success('앱 연결이 해제되었습니다');
    },
    onError: (err) => toast.error(getErrorMessage(err, '연결 해제에 실패했습니다')),
  });

  const grants = grantsQuery.data ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <AppWindow className="h-4 w-4" aria-hidden="true" />
          연결된 앱 (OAuth)
        </CardTitle>
        <CardDescription>브라우저 승인으로 연결된 AI 클라이언트입니다. 연결을 해제하면 즉시 접근이 차단됩니다.</CardDescription>
      </CardHeader>
      <CardContent>
        {grantsQuery.isPending ? (
          <div className="space-y-2" aria-busy="true">
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
          </div>
        ) : grantsQuery.isError ? (
          <QueryError compact error={grantsQuery.error} onRetry={() => void grantsQuery.refetch()} />
        ) : grants.length === 0 ? (
          <p className="text-sm text-muted-foreground">연결된 앱이 없습니다. 아래 가이드에 따라 MCP 클라이언트를 연결해 보세요.</p>
        ) : (
          <ul className="space-y-2">
            {grants.map((grant) => {
              const name = grant.clientName?.trim() || grant.clientId;
              return (
                <li key={grant.id} className="flex items-start justify-between gap-3 rounded-md border p-3">
                  <div className="min-w-0 space-y-1">
                    <p className="truncate text-sm font-medium">{name}</p>
                    <p className="truncate font-mono text-xs text-muted-foreground">{grant.clientId}</p>
                    <div className="flex flex-wrap gap-1">
                      {grant.scopes.map((scope) => (
                        <Badge key={scope} variant="outline" className="text-[10px]" title={scope}>
                          {describeScope(scope).label}
                        </Badge>
                      ))}
                    </div>
                    <p className="text-xs text-muted-foreground">
                      연결: {formatDateTime(grant.createdAt)} · 마지막 사용: {formatDateTime(grant.lastUsedAt)}
                    </p>
                  </div>
                  <Button
                    variant="outline"
                    size="sm"
                    className="shrink-0 gap-1 text-destructive hover:text-destructive"
                    onClick={() => setPendingRevoke(grant)}
                    aria-label={`${name} 연결 해제`}
                  >
                    <Unplug className="h-3.5 w-3.5" aria-hidden="true" />
                    해제
                  </Button>
                </li>
              );
            })}
          </ul>
        )}
      </CardContent>

      <ConfirmDialog
        open={pendingRevoke !== null}
        onOpenChange={(open) => {
          if (!open) setPendingRevoke(null);
        }}
        title="앱 연결을 해제할까요?"
        description={
          pendingRevoke
            ? `"${pendingRevoke.clientName?.trim() || pendingRevoke.clientId}"의 액세스·리프레시 토큰이 모두 폐기됩니다. 다시 사용하려면 앱에서 재연결해야 합니다.`
            : undefined
        }
        confirmLabel="연결 해제"
        destructive
        pending={revokeMutation.isPending}
        onConfirm={() => {
          if (pendingRevoke) revokeMutation.mutate(pendingRevoke.id);
        }}
      />
    </Card>
  );
}
