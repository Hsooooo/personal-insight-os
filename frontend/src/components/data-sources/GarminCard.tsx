import { useState } from 'react';
import { CheckCircle2, Database, Loader2, RefreshCw, XCircle } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import type { ProviderConnection } from '@/types';
import { isSyncRange, SYNC_RANGE_OPTIONS, type SyncRange } from './syncRange';

interface GarminCardProps {
  connection: ProviderConnection | undefined;
  isSyncing: boolean;
  cooldownRemaining: number;
  syncRange: SyncRange;
  onSyncRangeChange: (range: SyncRange) => void;
  onSync: () => void;
  onDisconnect: () => void;
  onConnect: (email: string, password: string) => void;
  connectPending: boolean;
  onGenerateMock: () => void;
  mockPending: boolean;
}

export function GarminCard({
  connection,
  isSyncing,
  cooldownRemaining,
  syncRange,
  onSyncRangeChange,
  onSync,
  onDisconnect,
  onConnect,
  connectPending,
  onGenerateMock,
  mockPending,
}: GarminCardProps) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const connected = connection?.connectionStatus === 'CONNECTED';

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Database className="h-5 w-5 text-indigo-500" aria-hidden="true" />
            <CardTitle>Garmin Connect</CardTitle>
          </div>
          {connected ? (
            <Badge variant="default" className="bg-emerald-100 text-emerald-700 hover:bg-emerald-100">
              <CheckCircle2 className="mr-1 h-3 w-3" aria-hidden="true" /> Connected
            </Badge>
          ) : (
            <Badge variant="secondary">
              <XCircle className="mr-1 h-3 w-3" aria-hidden="true" /> Disconnected
            </Badge>
          )}
        </div>
        <CardDescription>Sync your Garmin health and activity data</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {connected && connection ? (
          <>
            <div className="space-y-2 rounded-lg bg-muted p-4">
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">Last synced</span>
                <span className="font-medium">
                  {connection.lastSyncedAt ? new Date(connection.lastSyncedAt).toLocaleString('ko-KR') : 'Never'}
                </span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">Activities</span>
                <span className="font-medium">{connection.dataCount || 0}</span>
              </div>
              {isSyncing && (
                <div className="flex items-center gap-2 text-sm text-amber-600" role="status">
                  <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" />
                  <span>Sync in progress...</span>
                </div>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="sync-range">Sync Range</Label>
              <select
                id="sync-range"
                value={syncRange}
                onChange={(e) => {
                  if (isSyncRange(e.target.value)) onSyncRangeChange(e.target.value);
                }}
                className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              >
                {SYNC_RANGE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex flex-col gap-2 sm:flex-row">
              <Button onClick={onSync} disabled={cooldownRemaining > 0 || isSyncing} className="flex-1">
                {isSyncing && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />}
                <RefreshCw className="mr-2 h-4 w-4" aria-hidden="true" />
                {cooldownRemaining > 0 ? `Wait ${cooldownRemaining}s` : isSyncing ? 'Syncing...' : 'Sync Now'}
              </Button>
              <Button variant="outline" onClick={onDisconnect}>
                Disconnect
              </Button>
            </div>

            {import.meta.env.DEV && (
              <div className="flex gap-2">
                <Button variant="ghost" size="sm" onClick={onGenerateMock} disabled={mockPending}>
                  {mockPending && <Loader2 className="mr-2 h-3 w-3 animate-spin" aria-hidden="true" />}
                  Generate Mock Data
                </Button>
              </div>
            )}
          </>
        ) : (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              onConnect(email, password);
            }}
            className="space-y-3"
          >
            <div className="space-y-2">
              <Label htmlFor="garmin-email">Garmin Email</Label>
              <Input
                id="garmin-email"
                type="email"
                autoComplete="username"
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
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            <Button type="submit" className="w-full" disabled={connectPending}>
              {connectPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />}
              Connect Garmin
            </Button>
            <p className="text-xs text-muted-foreground">
              Connects to Garmin Connect via garminconnect library. Your credentials are used only for sync and stored
              securely.
            </p>
          </form>
        )}
      </CardContent>
    </Card>
  );
}
