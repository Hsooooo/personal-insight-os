/**
 * MCP 클라이언트 연동 설정 문자열 생성 (순수 함수 — 테스트 대상).
 * 내용은 docs/mcp-integration.md "클라이언트 연동" 섹션과 일치시킨다.
 */

export const SERVER_NAME = 'pios-coach';

export function getMcpUrl(origin: string = window.location.origin): string {
  // MCP 서버는 Caddy(/mcp)를 통해서만 외부에 노출된다
  return `${origin.replace(/\/$/, '')}/mcp`;
}

// ── OAuth (권장: URL만 등록 → 브라우저 로그인/승인) ───────────

export const oauthSnippets = {
  claudeCode: (url: string) => `claude mcp add --transport http ${SERVER_NAME} ${url}`,
  cursor: (url: string) => JSON.stringify({ mcpServers: { [SERVER_NAME]: { url } } }, null, 2),
  codexToml: (url: string) => `[mcp_servers.${SERVER_NAME}]\nurl = "${url}"`,
  codexLogin: () => `codex mcp login ${SERVER_NAME}`,
  kimi: (url: string) =>
    `kimi mcp add --transport http --auth oauth ${SERVER_NAME} ${url}\nkimi mcp auth ${SERVER_NAME}`,
};

// ── API 키 (헤드리스/CI/OAuth 미지원 클라이언트) ───────────────

export function bearerHeader(apiKey: string): string {
  return `Bearer ${apiKey}`;
}

export const apiKeySnippets = {
  claudeCode: (url: string, apiKey: string) =>
    `claude mcp add --transport http ${SERVER_NAME} ${url} \\\n  --header "Authorization: ${bearerHeader(apiKey)}"`,
  kimi: (url: string, apiKey: string) =>
    `kimi mcp add --transport http ${SERVER_NAME} ${url} \\\n  --header "Authorization: ${bearerHeader(apiKey)}"`,
  cursor: (url: string, apiKey: string) =>
    JSON.stringify({ mcpServers: { [SERVER_NAME]: { url, headers: { Authorization: bearerHeader(apiKey) } } } }, null, 2),
  codexEnv: (apiKey: string) => `export PIOS_API_KEY=${apiKey}`,
  codexToml: (url: string) => `[mcp_servers.${SERVER_NAME}]\nurl = "${url}"\nbearer_token_env_var = "PIOS_API_KEY"`,
  /** Claude Desktop: stdio만 직접 지원하므로 mcp-remote 브리지를 사용 */
  claudeDesktopMcpRemote: (url: string, apiKey: string) =>
    JSON.stringify(
      {
        mcpServers: {
          [SERVER_NAME]: {
            command: 'npx',
            args: ['-y', 'mcp-remote', url, '--header', 'Authorization:Bearer ${PIOS_API_KEY}'],
            env: { PIOS_API_KEY: apiKey },
          },
        },
      },
      null,
      2
    ),
};
