import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import { toast } from 'sonner';
import { Copy, Check, Trash2, Key, AlertTriangle, Lock, Bot } from 'lucide-react';
import type { ApiKey } from '@/types';

const LS_KEY_PREFIX = 'pios_mcp_key_';

function getStoredKey(keyId: number): string | null {
  try {
    return localStorage.getItem(LS_KEY_PREFIX + keyId) || null;
  } catch {
    return null;
  }
}

function storeKey(keyId: number, rawKey: string) {
  try {
    localStorage.setItem(LS_KEY_PREFIX + keyId, rawKey);
  } catch {
    // ignore
  }
}

function removeStoredKey(keyId: number) {
  try {
    localStorage.removeItem(LS_KEY_PREFIX + keyId);
  } catch {
    // ignore
  }
}

function maskKey(rawKey: string | null | undefined): string {
  if (!rawKey || !rawKey.startsWith('pios_')) return '••••••••';
  return rawKey.slice(0, 6) + '••••' + rawKey.slice(-4);
}

export default function McpSettings() {
  const queryClient = useQueryClient();
  const [newKeyName, setNewKeyName] = useState('');
  const [newKey, setNewKey] = useState<ApiKey | null>(null);
  const [loading, setLoading] = useState(false);
  const [selectedKeyId, setSelectedKeyId] = useState<number | null>(null);
  const [copied, setCopied] = useState<string | null>(null);
  const [agent, setAgent] = useState('kimi');

  const apiBaseUrl = `${window.location.origin}/api`;
  const mcpUrl = `${window.location.protocol}//${window.location.hostname}:8001/mcp`;

  const { data: keys, isLoading } = useQuery({
    queryKey: ['apiKeys'],
    queryFn: api.apiKeys.list,
  });

  const keysWithLocal = (keys || []).map((k) => {
    const raw = getStoredKey(k.id);
    return { ...k, _localKey: raw };
  });

  useEffect(() => {
    if (newKey) {
      setSelectedKeyId(newKey.id);
    }
  }, [newKey]);

  const createMutation = useMutation({
    mutationFn: api.apiKeys.create,
    onSuccess: (data) => {
      if (data.key) {
        storeKey(data.id, data.key);
        setNewKey(data);
      }
      setNewKeyName('');
      queryClient.invalidateQueries({ queryKey: ['apiKeys'] });
      toast.success('API 키가 발행되었습니다');
    },
    onError: () => {
      toast.error('API 키 발행에 실패했습니다');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: api.apiKeys.delete,
    onSuccess: (_, id) => {
      if (selectedKeyId === id) setSelectedKeyId(null);
      removeStoredKey(id);
      queryClient.invalidateQueries({ queryKey: ['apiKeys'] });
      toast.success('API 키가 삭제되었습니다');
    },
  });

  const handleCreate = () => {
    if (!newKeyName.trim()) return;
    createMutation.mutate(newKeyName.trim());
  };

  const copyToClipboard = (text: string, label: string) => {
    navigator.clipboard.writeText(text);
    setCopied(label);
    toast.success('복사되었습니다');
    setTimeout(() => setCopied(null), 2000);
  };

  const selectedKey = keysWithLocal.find((k) => k.id === selectedKeyId);
  const apiKey = newKey?.key || selectedKey?._localKey || 'YOUR_API_KEY_HERE';
  const apiKeyReady = !!(newKey?.key || selectedKey?._localKey);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">🤖 MCP 연동 설정</h1>
        <p className="text-muted-foreground">AI 에이전트와 Personal Insight OS를 연동하세요</p>
      </div>

      {newKey && (
        <Card className="border-green-500/30 bg-green-500/10">
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <Check className="h-4 w-4 text-green-400" />
              새 API 키가 발행되었습니다
            </CardTitle>
            <CardDescription className="text-yellow-600 dark:text-yellow-400">
              이 키는 지금만 표시됩니다. 반드시 저장하세요!
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2">
              <code className="flex-1 rounded-md bg-background p-3 text-sm break-all">{newKey.key}</code>
              <Button variant="outline" size="icon" onClick={() => copyToClipboard(newKey.key!, 'newKey')}>
                {copied === 'newKey' ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
              </Button>
            </div>
            <Button variant="ghost" className="mt-2" onClick={() => setNewKey(null)}>닫기</Button>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2">
            <Key className="h-4 w-4" />
            API 키 발행
          </CardTitle>
          <CardDescription>AI 에이전트가 PIOS API에 접근할 수 있는 키를 발행하세요</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex gap-2">
            <Input
              placeholder="에이전트 이름 (예: 내 노트북 Kimi)"
              value={newKeyName}
              onChange={(e) => setNewKeyName(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter' && newKeyName.trim()) handleCreate(); }}
            />
            <Button onClick={handleCreate} disabled={createMutation.isPending || !newKeyName.trim()}>
              {createMutation.isPending ? '발행 중...' : '발행'}
            </Button>
          </div>

          {isLoading ? (
            <div className="h-20 rounded-lg border bg-muted animate-pulse" />
          ) : keysWithLocal.length > 0 ? (
            <div className="space-y-2">
              <p className="text-sm font-medium">발행된 키 목록</p>
              <div className="space-y-1">
                {keysWithLocal.map((key) => (
                  <div
                    key={key.id}
                    className={`flex items-center justify-between rounded-md border px-3 py-2 text-sm cursor-pointer ${selectedKeyId === key.id ? 'bg-accent border-accent' : ''}`}
                    onClick={() => setSelectedKeyId(key.id)}
                  >
                    <div className="flex items-center gap-2">
                      <input
                        type="radio"
                        name="selectedKey"
                        checked={selectedKeyId === key.id}
                        onChange={() => setSelectedKeyId(key.id)}
                        className="h-3.5 w-3.5"
                      />
                      <span className="font-medium">{key.name}</span>
                      {key._localKey ? (
                        <Badge variant="secondary" className="text-xs font-mono">{maskKey(key._localKey)}</Badge>
                      ) : (
                        <Badge variant="outline" className="text-xs text-muted-foreground">키 보관 안 됨</Badge>
                      )}
                      <Badge variant="outline" className="text-xs">{new Date(key.createdAt).toLocaleDateString('ko-KR')}</Badge>
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="text-destructive hover:text-destructive h-7 w-7"
                      onClick={(e) => { e.stopPropagation(); deleteMutation.mutate(key.id); }}
                    >
                      <Trash2 className="h-3 w-3" />
                    </Button>
                  </div>
                ))}
              </div>
              {keysWithLocal.some((k) => !k._localKey) && (
                <p className="text-xs text-muted-foreground">
                  <AlertTriangle className="inline h-3 w-3 mr-1" />
                  "키 보관 안 됨" 표시는 이 브라우저에서 발급 직후 저장하지 않았거나 다른 기기에서 발급한 키입니다. 해당 키를 사용하려면 삭제 후 재발급하세요.
                </p>
              )}
            </div>
          ) : null}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">🌐 MCP 연동 가이드</CardTitle>
          <CardDescription>사용 중인 AI 에이전트를 선택하여 등록하세요</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {!apiKeyReady ? (
            <div className="rounded-md bg-destructive/10 border border-destructive/20 p-6 text-center space-y-3">
              <Lock className="h-8 w-8 mx-auto text-destructive" />
              <div className="text-sm font-medium text-destructive">API 키가 필요합니다</div>
              <p className="text-xs text-muted-foreground max-w-sm mx-auto">
                MCP 연동을 위해서는 위에서 API 키를 발행하고 선택해야 합니다.<br />
                키를 발급하면 아래 가이드가 활성화됩니다.
              </p>
            </div>
          ) : (
            <Tabs value={agent} onValueChange={setAgent}>
              <TabsList className="flex-wrap h-auto">
                <TabsTrigger value="kimi">Kimi</TabsTrigger>
                <TabsTrigger value="claude-code">Claude Code</TabsTrigger>
                <TabsTrigger value="claude-desktop">Claude Desktop</TabsTrigger>
                <TabsTrigger value="cursor">Cursor</TabsTrigger>
                <TabsTrigger value="codex">Codex</TabsTrigger>
              </TabsList>

              <TabsContent value="kimi" className="space-y-3 mt-4">
                <div className="flex items-center justify-between">
                  <Label className="text-sm font-medium">Kimi Code CLI</Label>
                  <Button variant="outline" size="sm" onClick={() => copyToClipboard(`kimi mcp add --transport http pios-coach ${mcpUrl} --header "X-API-Key: ${apiKey}"`, 'http-kimi')}>
                    {copied === 'http-kimi' ? <Check className="h-3 w-3 mr-1" /> : <Copy className="h-3 w-3 mr-1" />}
                    복사
                  </Button>
                </div>
                <pre className="rounded-md bg-muted p-4 text-xs overflow-auto whitespace-pre-wrap">{`kimi mcp add --transport http pios-coach \\
  ${mcpUrl} \\
  --header "X-API-Key: ${apiKey}"`}</pre>
                <p className="text-xs text-muted-foreground">
                  등록 후 <code>kimi mcp list</code>로 확인
                </p>
              </TabsContent>

              <TabsContent value="claude-code" className="space-y-3 mt-4">
                <div className="flex items-center justify-between">
                  <Label className="text-sm font-medium">Claude Code</Label>
                  <Button variant="outline" size="sm" onClick={() => copyToClipboard(`claude mcp add --transport http pios-coach ${mcpUrl} --header "X-API-Key: ${apiKey}"`, 'http-claude-code')}>
                    {copied === 'http-claude-code' ? <Check className="h-3 w-3 mr-1" /> : <Copy className="h-3 w-3 mr-1" />}
                    복사
                  </Button>
                </div>
                <pre className="rounded-md bg-muted p-4 text-xs overflow-auto whitespace-pre-wrap">{`claude mcp add --transport http pios-coach \\
  ${mcpUrl} \\
  --header "X-API-Key: ${apiKey}"`}</pre>
                <p className="text-xs text-muted-foreground">
                  CLI에 <code>--header</code> 미지원 시 아래 파일 직접 수정
                </p>
                <Separator />
                <div className="flex items-center justify-between">
                  <Label className="text-sm font-medium">또는 설정 파일 직접 수정</Label>
                  <Button variant="outline" size="sm" onClick={() => copyToClipboard(JSON.stringify({
                    mcpServers: {
                      'pios-coach': {
                        command: 'npx',
                        args: ['-y', '@anthropic-ai/mcp-remote', mcpUrl, `--header=X-API-Key:${apiKey}`],
                      },
                    },
                  }, null, 2), 'claude-code-file')}>
                    {copied === 'claude-code-file' ? <Check className="h-3 w-3 mr-1" /> : <Copy className="h-3 w-3 mr-1" />}
                    복사
                  </Button>
                </div>
                <pre className="rounded-md bg-muted p-4 text-xs overflow-auto whitespace-pre-wrap">{JSON.stringify({
                  mcpServers: {
                    'pios-coach': {
                      command: 'npx',
                      args: ['-y', '@anthropic-ai/mcp-remote', mcpUrl, `--header=X-API-Key:${apiKey}`],
                    },
                  },
                }, null, 2)}</pre>
                <p className="text-xs text-muted-foreground">
                  파일 위치: <code>~/.claude/settings.json</code> (또는 프로젝트 루트 <code>.claude/settings.json</code>)
                </p>
              </TabsContent>

              <TabsContent value="claude-desktop" className="space-y-3 mt-4">
                <div className="flex items-center justify-between">
                  <Label className="text-sm font-medium">Claude Desktop</Label>
                  <Button variant="outline" size="sm" onClick={() => copyToClipboard(JSON.stringify({
                    mcpServers: {
                      'pios-coach': {
                        transport: 'http',
                        url: mcpUrl,
                        headers: {
                          'X-API-Key': apiKey,
                        },
                      },
                    },
                  }, null, 2), 'http-claude-desktop')}>
                    {copied === 'http-claude-desktop' ? <Check className="h-3 w-3 mr-1" /> : <Copy className="h-3 w-3 mr-1" />}
                    복사
                  </Button>
                </div>
                <pre className="rounded-md bg-muted p-4 text-xs overflow-auto whitespace-pre-wrap">{JSON.stringify({
                  mcpServers: {
                    'pios-coach': {
                      transport: 'http',
                      url: mcpUrl,
                      headers: {
                        'X-API-Key': apiKey,
                      },
                    },
                  },
                }, null, 2)}</pre>
                <p className="text-xs text-muted-foreground">
                  파일 위치: <code>~/Library/Application Support/Claude/claude_desktop_config.json</code> (macOS)
                </p>
              </TabsContent>

              <TabsContent value="cursor" className="space-y-3 mt-4">
                <div className="flex items-center justify-between">
                  <Label className="text-sm font-medium">Cursor</Label>
                  <Button variant="outline" size="sm" onClick={() => copyToClipboard(JSON.stringify({
                    mcpServers: {
                      'pios-coach': {
                        transport: 'http',
                        url: mcpUrl,
                        headers: {
                          'X-API-Key': apiKey,
                        },
                      },
                    },
                  }, null, 2), 'http-cursor')}>
                    {copied === 'http-cursor' ? <Check className="h-3 w-3 mr-1" /> : <Copy className="h-3 w-3 mr-1" />}
                    복사
                  </Button>
                </div>
                <pre className="rounded-md bg-muted p-4 text-xs overflow-auto whitespace-pre-wrap">{JSON.stringify({
                  mcpServers: {
                    'pios-coach': {
                      transport: 'http',
                      url: mcpUrl,
                      headers: {
                        'X-API-Key': apiKey,
                      },
                    },
                  },
                }, null, 2)}</pre>
                <p className="text-xs text-muted-foreground">
                  파일 위치: <code>~/.cursor/mcp.json</code> — 설정 저장 후 Cursor 재시작
                </p>
              </TabsContent>

              <TabsContent value="codex" className="space-y-3 mt-4">
                <div className="rounded-md bg-blue-500/10 border border-blue-500/20 p-3 text-xs text-blue-600 dark:text-blue-400 space-y-1">
                  <p className="font-medium">환경변수 설정 (필수)</p>
                  <p>아래 명령어를 터미널에서 실행한 후 <code>codex</code>를 실행하세요：</p>
                  <div className="flex items-center gap-2 mt-1">
                    <code className="flex-1 rounded bg-background p-2 text-xs font-mono break-all">export PIOS_API_KEY={apiKey}</code>
                    <Button variant="outline" size="icon" className="h-7 w-7 shrink-0" onClick={() => copyToClipboard(`export PIOS_API_KEY=${apiKey}`, 'codex-env')}>
                      {copied === 'codex-env' ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
                    </Button>
                  </div>
                </div>

                <div className="flex items-center justify-between">
                  <Label className="text-sm font-medium">OpenAI Codex CLI 설정 파일</Label>
                  <Button variant="outline" size="sm" onClick={() => copyToClipboard(`[mcp_servers.pios-coach]\nurl = "${mcpUrl}"\n\n[mcp_servers.pios-coach.env_http_headers]\n"X-API-Key" = "PIOS_API_KEY"`, 'http-codex')}>
                    {copied === 'http-codex' ? <Check className="h-3 w-3 mr-1" /> : <Copy className="h-3 w-3 mr-1" />}
                    복사
                  </Button>
                </div>
                <pre className="rounded-md bg-muted p-4 text-xs overflow-auto whitespace-pre-wrap">{`[mcp_servers.pios-coach]
url = "${mcpUrl}"

[mcp_servers.pios-coach.env_http_headers]
"X-API-Key" = "PIOS_API_KEY"`}</pre>
                <p className="text-xs text-muted-foreground">
                  파일 위치：<code>~/.codex/config.toml</code>
                </p>

                <Separator />

                <div className="flex items-center justify-between">
                  <Label className="text-sm font-medium">또는 직접 값 넣기 (환경변수 불필요)</Label>
                  <Button variant="outline" size="sm" onClick={() => copyToClipboard(`[mcp_servers.pios-coach]\nurl = "${mcpUrl}"\n\n[mcp_servers.pios-coach.headers]\n"X-API-Key" = "${apiKey}"`, 'http-codex-direct')}>
                    {copied === 'http-codex-direct' ? <Check className="h-3 w-3 mr-1" /> : <Copy className="h-3 w-3 mr-1" />}
                    복사
                  </Button>
                </div>
                <pre className="rounded-md bg-muted p-4 text-xs overflow-auto whitespace-pre-wrap">{`[mcp_servers.pios-coach]
url = "${mcpUrl}"

[mcp_servers.pios-coach.headers]
"X-API-Key" = "${apiKey}"`}</pre>
                <p className="text-xs text-muted-foreground">
                  <code>headers</code> 섹션 지원 여부는 Codex 버전에 따라 다를 수 있습니다.
                </p>

                <Separator />
                <div className="flex items-center justify-between">
                  <Label className="text-sm font-medium">또는 CLI로 등록</Label>
                  <Button variant="outline" size="sm" onClick={() => copyToClipboard(`codex mcp add pios-coach --url ${mcpUrl}`, 'http-codex-cli')}>
                    {copied === 'http-codex-cli' ? <Check className="h-3 w-3 mr-1" /> : <Copy className="h-3 w-3 mr-1" />}
                    복사
                  </Button>
                </div>
                <pre className="rounded-md bg-muted p-4 text-xs overflow-auto whitespace-pre-wrap">{`codex mcp add pios-coach --url ${mcpUrl}`}</pre>
                <p className="text-xs text-muted-foreground">
                  CLI로 등록 후 <code>~/.codex/config.toml</code>에 헤더를 수동으로 추가해야 합니다.
                </p>
              </TabsContent>
            </Tabs>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
