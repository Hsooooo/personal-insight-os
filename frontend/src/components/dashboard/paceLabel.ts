export function paceLabel(pace?: string | null): string {
  switch (pace) {
    case 'AHEAD':
      return '앞서감';
    case 'ON_TRACK':
      return '순항';
    case 'BEHIND':
      return '뒤처짐';
    case 'INSUFFICIENT_DATA':
      return '데이터 부족';
    default:
      return pace || '-';
  }
}
