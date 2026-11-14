export interface ScopeInfo {
  scope: string;
  label: string;
  description: string;
  /** 쓰기 권한 여부 (강조 표시용) */
  write: boolean;
}

const SCOPE_INFO: Record<string, Omit<ScopeInfo, 'scope'>> = {
  'mcp:read': {
    label: '데이터 읽기',
    description: '활동·건강 지표·수면·목표·인사이트·그래프를 조회하고 내 데이터에 대해 질문할 수 있습니다.',
    write: false,
  },
  'mcp:write': {
    label: '목표 수정·동기화·브리핑 생성 등',
    description: '목표를 만들거나 수정하고, 인사이트 저장/피드백, Garmin 동기화 실행, 주간 브리핑 생성을 할 수 있습니다.',
    write: true,
  },
};

export function describeScope(scope: string): ScopeInfo {
  const known = SCOPE_INFO[scope];
  if (known) return { scope, ...known };
  return { scope, label: scope, description: '알 수 없는 권한입니다. 요청한 앱을 신뢰하는 경우에만 승인하세요.', write: true };
}

/** redirectUri는 전체 URL 또는 host만 올 수 있다 */
export function getRedirectHost(redirectUri: string): string {
  // "localhost:8080" 같은 host만 있는 값은 URL로 파싱하면 scheme으로 오인되므로 '://' 가 있을 때만 파싱한다
  if (!redirectUri.includes('://')) return redirectUri;
  try {
    return new URL(redirectUri).host || redirectUri;
  } catch {
    return redirectUri;
  }
}

const LOOPBACK_HOSTNAMES = new Set(['localhost', '127.0.0.1', '[::1]', '::1']);

/** 루프백(로컬 앱) 또는 이 서비스 자신의 도메인이면 신뢰 가능한 것으로 본다 */
export function isTrustedRedirectHost(host: string, currentHostname = window.location.hostname): boolean {
  const hostname = host.replace(/:\d+$/, '').toLowerCase();
  return LOOPBACK_HOSTNAMES.has(hostname) || hostname === currentHostname.toLowerCase();
}

export function isExpired(expiresAt: string, now: number = Date.now()): boolean {
  const ts = Date.parse(expiresAt);
  return Number.isFinite(ts) && ts <= now;
}

/** 브라우저 이동 대상으로 절대 허용하지 않는 scheme */
const BLOCKED_SCHEMES = new Set(['javascript', 'data', 'vbscript', 'file', 'blob', 'about']);

/** URI의 scheme (소문자, ':' 제외). scheme이 없으면 null */
export function getUriScheme(uri: string): string | null {
  const match = /^([a-z][a-z0-9+.-]*):/i.exec(uri.trim());
  return match ? match[1].toLowerCase() : null;
}

/** RFC 8252 private-use scheme(cursor://, vscode:// 등) 이면 scheme 반환, http(s)면 null */
export function getCustomScheme(uri: string): string | null {
  const scheme = getUriScheme(uri);
  return scheme && scheme !== 'http' && scheme !== 'https' ? scheme : null;
}

/**
 * 승인/거부 후 이동할 redirectUrl 검증.
 * - 서버가 GET /api/oauth/requests/{id}에서 돌려준 등록 redirect_uri로 정확히 시작하고
 *   그 뒤가 끝이거나 '?'(등록 URI에 이미 query가 있으면 '&')로 이어져야 한다.
 * - scheme이 위험 목록(javascript/data/vbscript/file/blob/about)이 아니어야 한다.
 */
export function isAllowedRedirect(redirectUrl: string, registeredUri: string): boolean {
  if (!registeredUri || !redirectUrl.startsWith(registeredUri)) return false;
  const rest = redirectUrl.slice(registeredUri.length);
  const separatorOk = rest === '' || rest.startsWith('?') || (registeredUri.includes('?') && rest.startsWith('&'));
  if (!separatorOk) return false;
  const scheme = getUriScheme(redirectUrl);
  return scheme !== null && !BLOCKED_SCHEMES.has(scheme);
}
