import { AlertTriangle, CheckCircle2, Clock, Loader2, XCircle } from 'lucide-react';

export function SyncStatusIcon({ status }: { status: string }) {
  switch (status) {
    case 'COMPLETED':
      return <CheckCircle2 className="h-4 w-4 text-emerald-500" aria-hidden="true" />;
    case 'RUNNING':
      return <Loader2 className="h-4 w-4 animate-spin text-amber-500" aria-hidden="true" />;
    case 'FAILED':
      return <XCircle className="h-4 w-4 text-red-500" aria-hidden="true" />;
    case 'PARTIAL':
      return <AlertTriangle className="h-4 w-4 text-amber-500" aria-hidden="true" />;
    default:
      return <Clock className="h-4 w-4 text-muted-foreground" aria-hidden="true" />;
  }
}
