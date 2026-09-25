import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api } from '@/lib/api';

// 운동 직후 오늘 하루치만 즉시 동기화 (최소 증분 단위)
export function useTodaySync() {
  const queryClient = useQueryClient();
  const [todaySyncLogId, setTodaySyncLogId] = useState<number | null>(null);

  const todaySyncMutation = useMutation({
    mutationFn: () => {
      const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' });
      return api.dataSources.syncGarmin('INCREMENTAL', today, today);
    },
    onSuccess: (log) => {
      toast.success('오늘 데이터 동기화를 시작했습니다');
      setTodaySyncLogId(log.id);
    },
    onError: (err: Error) => toast.error(err.message || '동기화 요청 실패'),
  });

  useEffect(() => {
    if (todaySyncLogId == null) return;
    let cancelled = false;
    let attempts = 0;
    const poll = async () => {
      try {
        const log = await api.dataSources.getSyncLog(todaySyncLogId);
        if (cancelled) return;
        if (log.status === 'RUNNING') {
          attempts++;
          if (attempts < 60) {
            setTimeout(poll, 2000);
          } else {
            toast.info('동기화 확인 시간 초과. 잠시 후 새로고침하세요.');
            setTodaySyncLogId(null);
          }
          return;
        }
        setTodaySyncLogId(null);
        if (log.status === 'COMPLETED') {
          toast.success(`오늘 동기화 완료: 활동 ${log.activitiesCount}건, 건강 ${log.healthMetricsCount}건, 수면 ${log.sleepCount}건`);
          queryClient.invalidateQueries({ queryKey: ['activities'] });
          queryClient.invalidateQueries({ queryKey: ['dashboard'] });
        } else {
          toast.error(`동기화 ${log.status}${log.errorMessage ? ': ' + log.errorMessage : ''}`);
        }
      } catch {
        if (!cancelled) setTodaySyncLogId(null);
      }
    };
    const timer = setTimeout(poll, 2000);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [todaySyncLogId, queryClient]);

  return {
    triggerSync: () => todaySyncMutation.mutate(),
    isPending: todaySyncMutation.isPending,
    isPolling: todaySyncLogId != null,
  };
}
