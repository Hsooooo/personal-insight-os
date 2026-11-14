import { describe, expect, it } from 'vitest';
import { apiKeySnippets, getMcpUrl, oauthSnippets } from './snippets';

const URL_ = 'https://pios.example.com/mcp';
const KEY = 'pios_test123';

describe('MCP snippets', () => {
  it('getMcpUrl appends /mcp and trims trailing slash', () => {
    expect(getMcpUrl('https://pios.example.com')).toBe(URL_);
    expect(getMcpUrl('https://pios.example.com/')).toBe(URL_);
  });

  it('OAuth snippets contain only the URL (no secrets)', () => {
    expect(oauthSnippets.claudeCode(URL_)).toBe(`claude mcp add --transport http pios-coach ${URL_}`);
    expect(JSON.parse(oauthSnippets.cursor(URL_))).toEqual({ mcpServers: { 'pios-coach': { url: URL_ } } });
    expect(oauthSnippets.codexToml(URL_)).toContain(`url = "${URL_}"`);
    expect(oauthSnippets.kimi(URL_)).toContain('--auth oauth');
  });

  it('API key snippets use Authorization: Bearer (not legacy X-API-Key)', () => {
    const all = [
      apiKeySnippets.claudeCode(URL_, KEY),
      apiKeySnippets.kimi(URL_, KEY),
      apiKeySnippets.cursor(URL_, KEY),
      apiKeySnippets.claudeDesktopMcpRemote(URL_, KEY),
    ];
    for (const s of all) {
      expect(s).toContain('Bearer');
      expect(s).not.toContain('X-API-Key');
    }
    expect(JSON.parse(apiKeySnippets.cursor(URL_, KEY)).mcpServers['pios-coach'].headers).toEqual({
      Authorization: `Bearer ${KEY}`,
    });
    expect(apiKeySnippets.codexToml(URL_)).toContain('bearer_token_env_var = "PIOS_API_KEY"');
    expect(apiKeySnippets.codexToml(URL_)).not.toContain(KEY);
  });
});
