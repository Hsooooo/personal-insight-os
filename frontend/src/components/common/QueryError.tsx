import { AlertTriangle, RotateCcw } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { getErrorMessage } from '@/lib/api';
import { cn } from '@/lib/utils';

interface QueryErrorProps {
  error: unknown;
  onRetry?: () => void;
  title?: string;
  className?: string;
  /** 카드 안에 들어가는 작은 형태 */
  compact?: boolean;
}

export function QueryError({ error, onRetry, title = '데이터를 불러오지 못했습니다', className, compact }: QueryErrorProps) {
  return (
    <div
      role="alert"
      className={cn(
        'flex flex-col items-center justify-center gap-2 rounded-lg border border-destructive/30 bg-destructive/5 text-center',
        compact ? 'p-4' : 'p-8',
        className
      )}
    >
      <AlertTriangle className={cn('text-destructive', compact ? 'h-5 w-5' : 'h-8 w-8')} aria-hidden="true" />
      <p className="text-sm font-medium">{title}</p>
      <p className="text-xs text-muted-foreground">{getErrorMessage(error)}</p>
      {onRetry && (
        <Button variant="outline" size="sm" className="mt-1 gap-1" onClick={onRetry}>
          <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
          다시 시도
        </Button>
      )}
    </div>
  );
}
