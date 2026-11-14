/** 로그인 후 돌아갈 내부 경로를 location.state에서 안전하게 꺼낸다. */
export interface RedirectState {
  from?: { pathname?: string; search?: string; hash?: string };
}

export function getRedirectTarget(state: unknown, fallback = '/'): string {
  if (typeof state !== 'object' || state === null || !('from' in state)) return fallback;
  const from = (state as RedirectState).from;
  const pathname = from?.pathname;
  // 외부 URL / 프로토콜 상대 경로(//evil.com) 차단
  if (!pathname || !pathname.startsWith('/') || pathname.startsWith('//') || pathname === '/login') {
    return fallback;
  }
  return `${pathname}${from?.search ?? ''}${from?.hash ?? ''}`;
}
