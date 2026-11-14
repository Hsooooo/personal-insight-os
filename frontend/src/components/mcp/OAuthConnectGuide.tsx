import { Globe } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { CopyableBlock } from './CopyableBlock';
import { oauthSnippets } from './snippets';

interface OAuthConnectGuideProps {
  mcpUrl: string;
  copied: string | null;
  onCopy: (text: string, id: string) => void;
}

export function OAuthConnectGuide({ mcpUrl, copied, onCopy }: OAuthConnectGuideProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Globe className="h-4 w-4" aria-hidden="true" />
          연결 방법 1 — OAuth (권장)
        </CardTitle>
        <CardDescription>
          MCP 클라이언트에 아래 URL만 추가하세요. 처음 연결할 때 브라우저가 열리면 PIOS에 로그인하고 권한을
          승인하면 됩니다. 키를 복사하거나 저장할 필요가 없습니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <CopyableBlock id="oauth-url" label="MCP 서버 URL" text={mcpUrl} copied={copied} onCopy={onCopy} />

        <Tabs defaultValue="claude-code">
          <TabsList className="h-auto flex-wrap">
            <TabsTrigger value="claude-code">Claude Code</TabsTrigger>
            <TabsTrigger value="claude-desktop">Claude Desktop / claude.ai</TabsTrigger>
            <TabsTrigger value="cursor">Cursor</TabsTrigger>
            <TabsTrigger value="codex">Codex</TabsTrigger>
            <TabsTrigger value="kimi">Kimi</TabsTrigger>
          </TabsList>

          <TabsContent value="claude-code" className="mt-4">
            <CopyableBlock
              id="oauth-claude-code"
              label="Claude Code"
              text={oauthSnippets.claudeCode(mcpUrl)}
              copied={copied}
              onCopy={onCopy}
              hint={
                <>
                  Claude Code 세션에서 <code>/mcp</code> → <code>pios-coach</code> → <strong>Authenticate</strong>를
                  선택하면 브라우저가 열립니다.
                </>
              }
            />
          </TabsContent>

          <TabsContent value="claude-desktop" className="mt-4 space-y-2 text-sm">
            <ol className="list-decimal space-y-1 pl-5 text-muted-foreground">
              <li>
                <strong>Settings → Connectors → Add custom connector</strong>를 엽니다.
              </li>
              <li>
                이름 <code>PIOS Coach</code>, URL <code className="break-all">{mcpUrl}</code> 을 입력합니다.
              </li>
              <li>
                <strong>Connect</strong>를 누르면 열리는 PIOS 승인 화면에서 권한을 확인하고 승인합니다.
              </li>
            </ol>
            <p className="text-xs text-muted-foreground">
              claude.ai 커넥터는 Anthropic 서버에서 접속하므로 공개 HTTPS 주소가 필요합니다.
            </p>
          </TabsContent>

          <TabsContent value="cursor" className="mt-4">
            <CopyableBlock
              id="oauth-cursor"
              label="~/.cursor/mcp.json"
              text={oauthSnippets.cursor(mcpUrl)}
              copied={copied}
              onCopy={onCopy}
              hint="저장 후 Cursor 설정의 MCP 목록에서 pios-coach의 Login/Connect를 눌러 인증합니다."
            />
          </TabsContent>

          <TabsContent value="codex" className="mt-4 space-y-3">
            <CopyableBlock
              id="oauth-codex-toml"
              label="1. ~/.codex/config.toml"
              text={oauthSnippets.codexToml(mcpUrl)}
              copied={copied}
              onCopy={onCopy}
            />
            <CopyableBlock
              id="oauth-codex-login"
              label="2. 브라우저 로그인"
              text={oauthSnippets.codexLogin()}
              copied={copied}
              onCopy={onCopy}
              hint={
                <>
                  Codex 버전에 따라 원격 MCP OAuth 사용 시 <code>experimental_use_rmcp_client = true</code> 설정이 필요할 수
                  있습니다.
                </>
              }
            />
          </TabsContent>

          <TabsContent value="kimi" className="mt-4">
            <CopyableBlock
              id="oauth-kimi"
              label="Kimi Code CLI"
              text={oauthSnippets.kimi(mcpUrl)}
              copied={copied}
              onCopy={onCopy}
              hint={
                <>
                  CLI 버전별 옵션은 <code>kimi mcp add --help</code>로 확인하세요.
                </>
              }
            />
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  );
}
