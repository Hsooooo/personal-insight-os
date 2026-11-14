import { describe, expect, it } from 'vitest';
import { describeScope, getCustomScheme, getRedirectHost, isAllowedRedirect, isExpired, isTrustedRedirectHost } from './oauth';

describe('oauth helpers', () => {
  it('describes known scopes in Korean', () => {
    expect(describeScope('mcp:read').label).toBe('데이터 읽기');
    expect(describeScope('mcp:read').write).toBe(false);
    expect(describeScope('mcp:write').label).toContain('목표 수정');
    expect(describeScope('mcp:write').write).toBe(true);
    expect(describeScope('admin').label).toBe('admin');
  });

  it('getRedirectHost accepts full URIs or bare hosts', () => {
    expect(getRedirectHost('http://127.0.0.1:33418/callback')).toBe('127.0.0.1:33418');
    expect(getRedirectHost('https://claude.ai/api/mcp/auth_callback')).toBe('claude.ai');
    expect(getRedirectHost('localhost:8080')).toBe('localhost:8080');
  });

  it('trusts loopback and same-site hosts only', () => {
    expect(isTrustedRedirectHost('localhost:3000', 'pios.example.com')).toBe(true);
    expect(isTrustedRedirectHost('127.0.0.1:1234', 'pios.example.com')).toBe(true);
    expect(isTrustedRedirectHost('[::1]:1234', 'pios.example.com')).toBe(true);
    expect(isTrustedRedirectHost('pios.example.com', 'pios.example.com')).toBe(true);
    expect(isTrustedRedirectHost('evil.example.net', 'pios.example.com')).toBe(false);
    expect(isTrustedRedirectHost('localhost.evil.net', 'pios.example.com')).toBe(false);
  });

  it('isExpired', () => {
    const now = Date.parse('2026-11-14T00:00:00Z');
    expect(isExpired('2026-11-13T23:59:59Z', now)).toBe(true);
    expect(isExpired('2026-11-14T00:10:00Z', now)).toBe(false);
    expect(isExpired('not a date', now)).toBe(false);
  });

  it('getCustomScheme detects RFC 8252 private-use schemes', () => {
    expect(getCustomScheme('cursor://anysphere.cursor-retrieval/oauth/cb')).toBe('cursor');
    expect(getCustomScheme('vscode://vscode.github-authentication/did-authenticate')).toBe('vscode');
    expect(getCustomScheme('https://claude.ai/cb')).toBeNull();
    expect(getCustomScheme('http://127.0.0.1:1234/cb')).toBeNull();
  });

  it('isAllowedRedirect requires the registered URI prefix + ? or end, and a safe scheme', () => {
    const reg = 'cursor://anysphere.cursor-retrieval/oauth/user-pios-coach/callback';
    expect(isAllowedRedirect(`${reg}?code=a&state=b`, reg)).toBe(true);
    expect(isAllowedRedirect(reg, reg)).toBe(true);
    expect(isAllowedRedirect(`${reg}-evil?code=a`, reg)).toBe(false);
    expect(isAllowedRedirect(`${reg}/x?code=a`, reg)).toBe(false);
    expect(isAllowedRedirect('https://other.example/cb?code=a', reg)).toBe(false);
    expect(isAllowedRedirect('http://127.0.0.1:33418/cb?code=a', 'http://127.0.0.1:33418/cb')).toBe(true);
    expect(isAllowedRedirect('https://app.example/cb?x=1&code=a', 'https://app.example/cb?x=1')).toBe(true);
    for (const bad of ['javascript:alert(1)', 'data:text/html,hi', 'vbscript:x', 'file:///etc/passwd', 'blob:https://x/1', 'about:blank']) {
      expect(isAllowedRedirect(bad, bad)).toBe(false);
      expect(isAllowedRedirect(`${bad}?code=a`, bad)).toBe(false);
    }
    expect(isAllowedRedirect('JavaScript:alert(1)', 'JavaScript:alert(1)')).toBe(false);
    expect(isAllowedRedirect('anything', '')).toBe(false);
  });
});
