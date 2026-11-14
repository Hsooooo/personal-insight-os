import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface EmptyStateProps {
  icon?: LucideIcon;
  message: ReactNode;
  action?: ReactNode;
  className?: string;
}

export function EmptyState({ icon: Icon, message, action, className }: EmptyStateProps) {
  return (
    <div className={cn('flex flex-col items-center justify-center gap-3 py-10 text-center', className)}>
      {Icon && <Icon className="h-8 w-8 text-muted-foreground" aria-hidden="true" />}
      <p className="text-sm text-muted-foreground">{message}</p>
      {action}
    </div>
  );
}
