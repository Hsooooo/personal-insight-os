import type { AskConfidence, AskEvidence, AskPeriod } from '@/types';

export interface ChatMessage {
  id: number;
  type: 'user' | 'system';
  content: string;
  insightId?: number;
  intent?: string;
  period?: AskPeriod;
  confidence?: AskConfidence;
  evidences?: AskEvidence[];
  followUpQuestions?: string[];
}

export function formatDateRange(start: string, end: string): string {
  return `${start} ~ ${end}`;
}

export function formatNumber(value: number | null | undefined, unit: string = ''): string {
  if (value === null || value === undefined) return '-';
  const formatted = Number.isInteger(value) ? value.toString() : value.toFixed(1);
  return `${formatted}${unit}`;
}

export function confidenceColor(level: string): string {
  switch (level) {
    case 'HIGH':
      return 'bg-green-100 text-green-700 border-green-200';
    case 'MEDIUM':
      return 'bg-amber-100 text-amber-700 border-amber-200';
    default:
      return 'bg-red-100 text-red-700 border-red-200';
  }
}

/** 근거 카드에서 이동할 내부 경로 */
export function evidenceLink(evidence: AskEvidence): string | null {
  if (evidence.route) return evidence.route;
  if (evidence.sourceId && evidence.type === 'ACTIVITY') return `/activities/${evidence.sourceId}`;
  if (evidence.sourceDate) return `/health?date=${evidence.sourceDate}`;
  return null;
}

/** location.state에서 초기 질문을 안전하게 꺼낸다 */
export function getInitialQuestion(state: unknown): string {
  if (typeof state === 'object' && state !== null && 'question' in state) {
    const q = (state as { question: unknown }).question;
    return typeof q === 'string' ? q : '';
  }
  return '';
}

export const SAMPLE_QUESTIONS = [
  '최근 컨디션이 떨어진 이유가 뭐야?',
  '러닝 기록이 좋았던 날들의 공통점은 뭐야?',
  '수면이 부족하면 내 울동 성과가 얼마나 떨어져?',
  '이번 주 훈련 강도는 적절했어?',
];
