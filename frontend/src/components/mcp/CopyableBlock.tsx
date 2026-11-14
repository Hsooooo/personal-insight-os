import type { ReactNode } from 'react';
import { Check, Copy } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface CopyableBlockProps {
  id: string;
  label: string;
  text: string;
  copied: string | null;
  onCopy: (text: string, id: string) => void;
  hint?: ReactNode;
}

export function CopyableBlock({ id, label, text, copied, onCopy, hint }: CopyableBlockProps) {
  const isCopied = copied === id;
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-medium">{label}</p>
        <Button variant="outline" size="sm" onClick={() => onCopy(text, id)} aria-label={`${label} 복사`}>
          {isCopied ? <Check className="mr-1 h-3 w-3" aria-hidden="true" /> : <Copy className="mr-1 h-3 w-3" aria-hidden="true" />}
          {isCopied ? '복사됨' : '복사'}
        </Button>
      </div>
      <pre className="overflow-auto whitespace-pre-wrap break-all rounded-md bg-muted p-4 text-xs">{text}</pre>
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}
