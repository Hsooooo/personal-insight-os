import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import type { SyncLog } from '@/types';

const ACTIVE_POLL_INTERVAL_MS = 2000; // 2 seconds
const ACTIVE_POLL_MAX_MS = 5 * 60 * 1000; // 5 minutes

/** 방금 시작한 동기화 로그를 빠르게 폴링하고, 끝나면 관련 캐시를 무효화한다 */
export function useActiveSyncPolling(activeLogId: number | null, onDone: () => void) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!activeLogId) return;

    let cancelled = false;
    let attempts = 0;
    let timer: ReturnType<typeof setTimeout> | null = null;
    const controller = new AbortController();
    const maxAttempts = ACTIVE_POLL_MAX_MS / ACTIVE_POLL_INTERVAL_MS;

    const poll = async () => {
      try {
        const log = await api.dataSources.getSyncLog(activeLogId, { signal: controller.signal });
        if (cancelled) return;

        queryClient.setQueryData<SyncLog[]>(['syncLogs'], (old) => {
          if (!old) return [log];
          return [log, ...old.filter((l) => l.id !== log.id)];
        });

        if (log.status === 'COMPLETED') {
          toast.success(`Sync completed: ${log.activitiesCount} activities, ${log.healthMetricsCount} health metrics`);
          onDone();
          void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
          void queryClient.invalidateQueries({ queryKey: ['activities'] });
          void queryClient.invalidateQueries({ queryKey: ['health'] });
          return;
        }

        if (log.status === 'FAILED' || log.status === 'PARTIAL') {
          toast.error(`Sync ${log.status.toLowerCase()}${log.errorMessage ? ': ' + log.errorMessage : ''}`);
          onDone();
          return;
        }

        attempts++;
        if (attempts >= maxAttempts) {
          toast.info('Sync status polling timed out. Check sync history for results.');
          onDone();
          return;
        }

        timer = setTimeout(poll, ACTIVE_POLL_INTERVAL_MS);
      } catch {
        if (!cancelled) {
          toast.error('Failed to check sync status');
          onDone();
        }
      }
    };

    void poll();

    return () => {
      cancelled = true;
      controller.abort();
      if (timer) clearTimeout(timer);
    };
  }, [activeLogId, queryClient, onDone]);
}
