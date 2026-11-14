/** 외부 URL로의 전체 페이지 이동 (테스트에서 모킹하기 위해 분리) */
export function redirectTo(url: string): void {
  window.location.assign(url);
}
