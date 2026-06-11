import { useState, useEffect, useRef, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';

import {
  Loader2,
  Database,
  CheckCircle2,
  XCircle,
  RefreshCw,
  Clock,
  Activity,
  Moon,
  Heart,
  AlertTriangle,
} from 'lucide-react';
import type { SyncLog } from '@/types';

const SYNC_COOLDOWN_SECONDS = 30;
const POLL_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

function useCooldown() {
  const [cooldownRemaining, setCooldownRemaining] = useState(0);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const startCooldown = useCallback(() => {
    setCooldownRemaining(SYNC_COOLDOWN_SECONDS);
    if (intervalRef.current) clearInterval(intervalRef.current);
    intervalRef.current = setInterval(() => {
      setCooldownRemaining((prev) => {
        if (prev <= 1) {
          if (intervalRef.current) clearInterval(intervalRef.current);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
  }, []);

  useEffect(() => {
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, []);

  return { cooldownRemaining, startCooldown };
}

function getStatusIcon(status: string) {
  switch (status) {
    case 'COMPLETED':
      return <CheckCircle2 className="h-4 w-4 text-emerald-500" />;
    case 'RUNNING':
      return <Loader2 className="h-4 w-4 animate-spin text-amber-500" />;
    case 'FAILED':
      return <XCircle className="h-4 w-4 text-red-500" />;
    case 'PARTIAL':
      return <AlertTriangle className="h-4 w-4 text-amber-500" />;
    default:
      return <Clock className="h-4 w-4 text-muted-foreground" />;
  }
}

function getStatusLabel(status: string) {
  switch (status) {
    case 'COMPLETED':
      return 'Completed';
    case 'RUNNING':
      return 'Running';
    case 'FAILED':
      return 'Failed';
    case 'PARTIAL':
      return 'Partial';
    default:
      return status;
  }
}

export default function DataSources() {
  const queryClient = useQueryClient();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [syncRange, setSyncRange] = useState('incremental');
  const { cooldownRemaining, startCooldown } = useCooldown();

  const { data: connections, isLoading } = useQuery({
    queryKey: ['dataSources'],
    queryFn: api.dataSources.list,
  });

  const { data: syncLogs } = useQuery({
    queryKey: ['syncLogs'],
    queryFn: api.dataSources.getSyncLogs,
    refetchInterval: (query) => {
      const data = query.state.data as SyncLog[] | undefined;
      // Poll every 5 min if any sync is running; otherwise disable auto-poll
      const hasRunning = data?.some((l) => l.status === 'RUNNING');
      return hasRunning ? POLL_INTERVAL_MS : false;
    },
  });

  // Smart polling: only poll when tab is visible
  useEffect(() => {
    const handleVisibility = () => {
      if (document.visibilityState === 'visible') {
        queryClient.invalidateQueries({ queryKey: ['syncLogs'] });
      }
    };
    document.addEventListener('visibilitychange', handleVisibility);
    return () => document.removeEventListener('visibilitychange', handleVisibility);
  }, [queryClient]);

  const connectMutation = useMutation({
    mutationFn: () => api.dataSources.connectGarmin(email, password),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dataSources'] });
      queryClient.invalidateQueries({ queryKey: ['syncLogs'] });
    },
  });

  const syncMutation = useMutation({
    mutationFn: () => {
      // 동기화 범위는 KST(Asia/Seoul) 기준으로 계산
      const kstDate = (date: Date) =>
        date.toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' });

      const today = kstDate(new Date());
      let dateFrom: string | undefined;
      let dateTo = today;
      let syncType = 'INCREMENTAL';

      if (syncRange === 'full-all') {
        syncType = 'FULL';
        dateFrom = undefined;
      } else if (syncRange.startsWith('full-')) {
        syncType = 'FULL';
        const months = parseInt(syncRange.replace('full-', ''), 10);
        const d = new Date();
        d.setMonth(d.getMonth() - months);
        dateFrom = kstDate(d);
      } else {
        // incremental: last 7 days
        const d = new Date();
        d.setDate(d.getDate() - 7);
        dateFrom = kstDate(d);
      }

      return api.dataSources.syncGarmin(syncType, dateFrom, dateTo);
    },
    onSuccess: () => {
      startCooldown();
      queryClient.invalidateQueries({ queryKey: ['dataSources'] });
      queryClient.invalidateQueries({ queryKey: ['syncLogs'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });

  const mockMutation = useMutation({
    mutationFn: api.dataSources.generateMockData,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dataSources'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });

  const disconnectMutation = useMutation({
    mutationFn: api.dataSources.disconnectGarmin,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['dataSources'] }),
  });

  const garminConn = connections?.find((c) => c.providerType === 'GARMIN');
  const isSyncing = syncLogs?.some((l) => l.status === 'RUNNING');

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">Data Sources</h2>
        <p className="text-muted-foreground">Connect and manage your data providers</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {/* Garmin Card */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Database className="h-5 w-5 text-indigo-500" />
                <CardTitle>Garmin Connect</CardTitle>
              </div>
              {garminConn?.connectionStatus === 'CONNECTED' ? (
                <Badge variant="default" className="bg-emerald-100 text-emerald-700 hover:bg-emerald-100">
                  <CheckCircle2 className="mr-1 h-3 w-3" /> Connected
                </Badge>
              ) : (
                <Badge variant="secondary">
                  <XCircle className="mr-1 h-3 w-3" /> Disconnected
                </Badge>
              )}
            </div>
            <CardDescription>Sync your Garmin health and activity data</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {garminConn?.connectionStatus === 'CONNECTED' ? (
              <>
                <div className="rounded-lg bg-muted p-4 space-y-2">
                  <div className="flex justify-between text-sm">
                    <span className="text-muted-foreground">Last synced</span>
                    <span className="font-medium">
                      {garminConn.lastSyncedAt
                        ? new Date(garminConn.lastSyncedAt).toLocaleString('ko-KR')
                        : 'Never'}
                    </span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-muted-foreground">Activities</span>
                    <span className="font-medium">{garminConn.dataCount || 0}</span>
                  </div>
                  {isSyncing && (
                    <div className="flex items-center gap-2 text-sm text-amber-600">
                      <Loader2 className="h-3 w-3 animate-spin" />
                      <span>Sync in progress...</span>
                    </div>
                  )}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="sync-range">Sync Range</Label>
                  <select
                    id="sync-range"
                    value={syncRange}
                    onChange={(e) => setSyncRange(e.target.value)}
                    className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                  >
                    <option value="incremental">Incremental (last 7 days)</option>
                    <option value="full-3">Full - Last 3 months</option>
                    <option value="full-6">Full - Last 6 months</option>
                    <option value="full-12">Full - Last 1 year</option>
                    <option value="full-all">Full - All data</option>
                  </select>
                </div>

                <div className="flex gap-2">
                  <Button
                    onClick={() => syncMutation.mutate()}
                    disabled={syncMutation.isPending || cooldownRemaining > 0 || isSyncing}
                    className="flex-1"
                  >
                    {syncMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    <RefreshCw className="mr-2 h-4 w-4" />
                    {cooldownRemaining > 0
                      ? `Wait ${cooldownRemaining}s`
                      : isSyncing
                        ? 'Syncing...'
                        : 'Sync Now'}
                  </Button>
                  <Button variant="outline" onClick={() => disconnectMutation.mutate()}>
                    Disconnect
                  </Button>
                </div>

                <div className="flex gap-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => mockMutation.mutate()}
                    disabled={mockMutation.isPending}
                  >
                    {mockMutation.isPending && <Loader2 className="mr-2 h-3 w-3 animate-spin" />}
                    Generate Mock Data
                  </Button>
                </div>
              </>
            ) : (
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  connectMutation.mutate();
                }}
                className="space-y-3"
              >
                <div className="space-y-2">
                  <Label htmlFor="garmin-email">Garmin Email</Label>
                  <Input
                    id="garmin-email"
                    type="email"
                    placeholder="your@email.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="garmin-password">Password</Label>
                  <Input
                    id="garmin-password"
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                  />
                </div>
                <Button type="submit" className="w-full" disabled={connectMutation.isPending}>
                  {connectMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  Connect Garmin
                </Button>
                <p className="text-xs text-muted-foreground">
                  Connects to Garmin Connect via garminconnect library. Your credentials are used
                  only for sync and stored securely.
                </p>
              </form>
            )}
          </CardContent>
        </Card>

        {/* Sync History */}
        <Card>
          <CardHeader>
            <CardTitle>Sync History</CardTitle>
            <CardDescription>Recent Garmin synchronization logs</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-3 max-h-[400px] overflow-y-auto">
              {syncLogs && syncLogs.length > 0 ? (
                syncLogs.slice(0, 20).map((log) => (
                  <div
                    key={log.id}
                    className="rounded-lg border p-3 space-y-2"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        {getStatusIcon(log.status)}
                        <span className="text-sm font-medium">{getStatusLabel(log.status)}</span>
                      </div>
                      <span className="text-xs text-muted-foreground">
                        {new Date(log.startedAt).toLocaleString('ko-KR')}
                      </span>
                    </div>
                    <div className="flex gap-3 text-xs text-muted-foreground">
                      <span className="flex items-center gap-1">
                        <Activity className="h-3 w-3" />
                        {log.activitiesCount || 0}
                      </span>
                      <span className="flex items-center gap-1">
                        <Heart className="h-3 w-3" />
                        {log.healthMetricsCount || 0}
                      </span>
                      <span className="flex items-center gap-1">
                        <Moon className="h-3 w-3" />
                        {log.sleepCount || 0}
                      </span>
                    </div>
                    {log.errorMessage && (
                      <p className="text-xs text-red-500">{log.errorMessage}</p>
                    )}
                  </div>
                ))
              ) : (
                <p className="text-sm text-muted-foreground text-center py-8">
                  No sync history yet
                </p>
              )}
            </div>
          </CardContent>
        </Card>

        {/* Future Providers */}
        <Card className="md:col-span-2">
          <CardHeader>
            <CardTitle>Coming Soon</CardTitle>
            <CardDescription>More data sources on the roadmap</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
              {['Strava', 'Apple Health', 'Oura Ring', 'Withings', 'Fitbit'].map((name) => (
                <div key={name} className="flex items-center justify-between rounded-lg border p-3">
                  <span className="text-sm font-medium">{name}</span>
                  <Badge variant="outline">Soon</Badge>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
