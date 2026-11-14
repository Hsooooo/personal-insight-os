import { Eye, PencilLine } from 'lucide-react';
import { describeScope } from '@/lib/oauth';

export function ScopeList({ scopes }: { scopes: string[] }) {
  if (scopes.length === 0) {
    return <p className="text-sm text-muted-foreground">요청한 권한이 없습니다.</p>;
  }
  return (
    <ul className="space-y-2" aria-label="요청 권한">
      {scopes.map((scope) => {
        const info = describeScope(scope);
        const Icon = info.write ? PencilLine : Eye;
        return (
          <li key={scope} className="flex gap-3 rounded-md border p-3">
            <Icon
              className={info.write ? 'mt-0.5 h-4 w-4 shrink-0 text-amber-600' : 'mt-0.5 h-4 w-4 shrink-0 text-indigo-600'}
              aria-hidden="true"
            />
            <div className="space-y-0.5">
              <p className="text-sm font-medium">
                {info.label} <code className="ml-1 text-xs text-muted-foreground">{scope}</code>
              </p>
              <p className="text-xs text-muted-foreground">{info.description}</p>
            </div>
          </li>
        );
      })}
    </ul>
  );
}
