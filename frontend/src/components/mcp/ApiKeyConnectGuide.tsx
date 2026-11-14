import { Lock, Terminal } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { CopyableBlock } from './CopyableBlock';
import { apiKeySnippets } from './snippets';

interface ApiKeyConnectGuideProps {
  mcpUrl: string;
  /** 이번 세션에서 방금 발급한 키 원문 (없으면 가이드 잠금) */
  apiKey: string | undefined;
  copied: string | null;
  onCopy: (text: string, id: string) => void;
}

export function ApiKeyConnectGuide({ mcpUrl, apiKey, copied, onCopy }: ApiKeyConnectGuideProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Terminal className="h-4 w-4" aria-hidden="true" />
          연결 방법 2 — API 키 (헤드리스 / OAuth 미지원 클라이언트)
        </CardTitle>
        <CardDescription>
          키를 <code>Authorization: Bearer pios_...</code> 헤더로 보냅니다 (권장). 구버전 설정의{' '}
          <code>X-API-Key: pios_...</code> 헤더도 호환을 위해 계속 동작하지만(legacy) 새 설정에는 Bearer 방식을 쓰세요. 키는
          계정 전체 권한(읽기·쓰기)을 가지므로 안전한 곳에 보관하세요.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {!apiKey ? (
          <div className="space-y-3 rounded-md border border-destructive/20 bg-destructive/10 p-6 text-center">
            <Lock className="mx-auto h-8 w-8 text-destructive" aria-hidden="true" />
            <p className="text-sm font-medium text-destructive">API 키가 필요합니다</p>
            <p className="mx-auto max-w-sm text-xs text-muted-foreground">
              위에서 API 키를 발행하면 키가 채워진 설정 예시가 표시됩니다.
            </p>
          </div>
        ) : (
          <Tabs defaultValue="claude-code">
            <TabsList className="h-auto flex-wrap">
              <TabsTrigger value="claude-code">Claude Code</TabsTrigger>
              <TabsTrigger value="cursor">Cursor</TabsTrigger>
              <TabsTrigger value="claude-desktop">Claude Desktop</TabsTrigger>
              <TabsTrigger value="codex">Codex</TabsTrigger>
              <TabsTrigger value="kimi">Kimi</TabsTrigger>
            </TabsList>

            <TabsContent value="claude-code" className="mt-4 space-y-3">
              <CopyableBlock
                id="key-claude-code"
                label="Claude Code"
                text={apiKeySnippets.claudeCode(mcpUrl, apiKey)}
                copied={copied}
                onCopy={onCopy}
              />
            </TabsContent>

            <TabsContent value="cursor" className="mt-4 space-y-3">
              <CopyableBlock
                id="key-cursor"
                label="~/.cursor/mcp.json"
                text={apiKeySnippets.cursor(mcpUrl, apiKey)}
                copied={copied}
                onCopy={onCopy}
              />
            </TabsContent>

            <TabsContent value="claude-desktop" className="mt-4 space-y-3">
              <CopyableBlock
                id="key-claude-desktop"
                label="claude_desktop_config.json (mcp-remote 브리지)"
                text={apiKeySnippets.claudeDesktopMcpRemote(mcpUrl, apiKey)}
                copied={copied}
                onCopy={onCopy}
                hint={
                  <>
                    Claude Desktop 설정 파일은 로컬(stdio) 서버만 직접 지원하므로 <code>mcp-remote</code> 브리지를 사용합니다.
                    위치: <code>~/Library/Application Support/Claude/claude_desktop_config.json</code> (macOS)
                  </>
                }
              />
            </TabsContent>

            <TabsContent value="codex" className="mt-4 space-y-3">
              <CopyableBlock
                id="key-codex-env"
                label="1. 환경변수 설정"
                text={apiKeySnippets.codexEnv(apiKey)}
                copied={copied}
                onCopy={onCopy}
              />
              <CopyableBlock
                id="key-codex-toml"
                label="2. ~/.codex/config.toml"
                text={apiKeySnippets.codexToml(mcpUrl)}
                copied={copied}
                onCopy={onCopy}
                hint="Codex가 PIOS_API_KEY 값을 Bearer 토큰으로 전송합니다."
              />
            </TabsContent>

            <TabsContent value="kimi" className="mt-4 space-y-3">
              <CopyableBlock
                id="key-kimi"
                label="Kimi Code CLI"
                text={apiKeySnippets.kimi(mcpUrl, apiKey)}
                copied={copied}
                onCopy={onCopy}
                hint={
                  <>
                    등록 후 <code>kimi mcp list</code>로 확인
                  </>
                }
              />
            </TabsContent>
          </Tabs>
        )}
      </CardContent>
    </Card>
  );
}
