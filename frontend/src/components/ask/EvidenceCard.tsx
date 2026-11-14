import { Link } from 'react-router-dom';
import { ExternalLink, Minus, TrendingDown, TrendingUp } from 'lucide-react';
import type { AskEvidence } from '@/types';
import { evidenceLink, formatNumber } from './askFormat';

export function EvidenceCard({ evidence }: { evidence: AskEvidence }) {
  const changePositive = evidence.changeRate !== null && evidence.changeRate > 0;
  const changeNegative = evidence.changeRate !== null && evidence.changeRate < 0;
  const ChangeIcon = changePositive ? TrendingUp : changeNegative ? TrendingDown : Minus;
  const changeLabel = changePositive ? '증가' : changeNegative ? '감소' : '변화 없음';
  const linkTo = evidenceLink(evidence);

  return (
    <div className="rounded-md border p-3 text-sm transition-colors hover:bg-muted/20">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="font-medium">{evidence.label}</p>
          <p className="text-xs text-muted-foreground">{evidence.observation}</p>
        </div>
        {linkTo && (
          <Link
            to={linkTo}
            aria-label={`${evidence.label} 원본 데이터 보기`}
            className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
          </Link>
        )}
      </div>
      <div className="mt-2 flex items-center gap-3 text-xs">
        <span className="text-muted-foreground">{evidence.comparison}</span>
        {evidence.changeRate !== null && (
          <span className="flex items-center gap-0.5 text-muted-foreground">
            <ChangeIcon className="h-3 w-3" aria-hidden="true" />
            <span className="sr-only">{changeLabel}</span>
            {Math.abs(evidence.changeRate).toFixed(1)}%
          </span>
        )}
      </div>
      <div className="mt-2 grid grid-cols-2 gap-2 text-xs">
        <div className="rounded bg-muted/40 px-2 py-1">
          <span className="text-muted-foreground">현재</span>
          <div className="font-medium">{formatNumber(evidence.currentValue, evidence.unit)}</div>
        </div>
        <div className="rounded bg-muted/40 px-2 py-1">
          <span className="text-muted-foreground">기준선</span>
          <div className="font-medium">{formatNumber(evidence.baselineValue, evidence.unit)}</div>
        </div>
      </div>
    </div>
  );
}
