import { useCallback, useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api, getErrorMessage } from '@/lib/api';
import { Skeleton } from '@/components/ui/skeleton';
import { QueryError } from '@/components/common/QueryError';
import { ComingSoonCard } from '@/components/data-sources/ComingSoonCard';
import { GarminCard } from '@/components/data-sources/GarminCard';
import { SyncHistoryCard } from '@/components/data-sources/SyncHistoryCard';
import { buildSyncRequest, type SyncRange } from '@/components/data-sources/syncRange';
import { useActiveSyncPolling } from '@/components/data-sources/useActiveSyncPolling';
import { useCooldown } from '@/components/data-sources/useCooldown';

const SYNC_COOLDOWN_SECONDS = 30;
const POLL_INTERVAL_MS = 30 * 1000; // 30 seconds

export default function DataSources() {
  const queryClient = useQueryClient();
  const [syncRange, setSyncRange] = useState<SyncRange>('incremental');
  const [activeLogId, setActiveLogId] = useState<number | null>(null);
  const { cooldownRemaining, startCooldown } = useCooldown(SYNC_COOLDOWN_SECONDS);

  const connectionsQuery = useQuery({
    queryKey: ['dataSources'],
    queryFn: api.dataSources.list,
  });

  const syncLogsQuery = useQuery({
    queryKey: ['syncLogs'],
    queryFn: api.dataSources.getSyncLogs,
    refetchInterval: (query) => {
      const hasRunning = query.state.data?.some((l) => l.status === 'RUNNING');
      return hasRunning ? POLL_INTERVAL_MS : false;
    },
  });

  // Smart polling: only poll when tab is visible
  useEffect(() => {
    const handleVisibility = () => {
      if (document.visibilityState === 'visible') {
        void queryClient.invalidateQueries({ queryKey: ['syncLogs'] });
      }
    };
    document.addEventListener('visibilitychange', handleVisibility);
    return () => document.removeEventListener('visibilitychange', handleVisibility);
  }, [queryClient]);

  const clearActiveLog = useCallback(() => setActiveLogId(null), []);
  useActiveSyncPolling(activeLogId, clearActiveLog);

  const connectMutation = useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      api.dataSources.connectGarmin(email, password),
    onSuccess: () => {
      toast.success('Garmin connected successfully');
      void queryClient.invalidateQueries({ queryKey: ['dataSources'] });
      void queryClient.invalidateQueries({ queryKey: ['syncLogs'] });
    },
    onError: (err) => toast.error(getErrorMessage(err, 'Failed to connect Garmin')),
  });

  const syncMutation = useMutation({
    mutationFn: () => {
      // 동기화 범위는 KST(Asia/Seoul) 기준으로 계산
      const { syncType, dateFrom, dateTo } = buildSyncRequest(syncRange);
      return api.dataSources.syncGarmin(syncType, dateFrom, dateTo);
    },
    onSuccess: (log) => {
      startCooldown();
      setActiveLogId(log.id);
      toast.info(`Sync started (${log.syncType}, ${log.dateFrom} ~ ${log.dateTo})`);
      void queryClient.invalidateQueries({ queryKey: ['syncLogs'] });
    },
    onError: (err) => toast.error(getErrorMessage(err, 'Failed to start sync')),
  });

  const mockMutation = useMutation({
    mutationFn: api.dataSources.generateMockData,
    onSuccess: () => {
      toast.success('Mock data generated');
      void queryClient.invalidateQueries({ queryKey: ['dataSources'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
    onError: (err) => toast.error(getErrorMessage(err, 'Failed to generate mock data')),
  });

  const disconnectMutation = useMutation({
    mutationFn: api.dataSources.disconnectGarmin,
    onSuccess: () => {
      toast.success('Garmin disconnected');
      void queryClient.invalidateQueries({ queryKey: ['dataSources'] });
    },
    onError: (err) => toast.error(getErrorMessage(err, 'Failed to disconnect Garmin')),
  });

  const garminConn = connectionsQuery.data?.find((c) => c.providerType === 'GARMIN');
  const isSyncing = Boolean(syncLogsQuery.data?.some((l) => l.status === 'RUNNING')) || activeLogId != null;

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">Data Sources</h2>
        <p className="text-muted-foreground">Connect and manage your data providers</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {connectionsQuery.isPending ? (
          <Skeleton className="h-80" />
        ) : connectionsQuery.isError ? (
          <QueryError error={connectionsQuery.error} onRetry={() => void connectionsQuery.refetch()} />
        ) : (
          <GarminCard
            connection={garminConn}
            isSyncing={isSyncing}
            cooldownRemaining={cooldownRemaining}
            syncRange={syncRange}
            onSyncRangeChange={setSyncRange}
            onSync={() => syncMutation.mutate()}
            onDisconnect={() => disconnectMutation.mutate()}
            onConnect={(email, password) => connectMutation.mutate({ email, password })}
            connectPending={connectMutation.isPending}
            onGenerateMock={() => mockMutation.mutate()}
            mockPending={mockMutation.isPending}
          />
        )}

        <SyncHistoryCard
          logs={syncLogsQuery.data}
          isPending={syncLogsQuery.isPending}
          error={syncLogsQuery.isError ? syncLogsQuery.error : null}
          onRetry={() => void syncLogsQuery.refetch()}
        />

        <ComingSoonCard />
      </div>
    </div>
  );
}
