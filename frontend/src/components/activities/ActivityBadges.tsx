import { Badge } from '@/components/ui/badge';
import { Dumbbell } from 'lucide-react';

export function getTypeBadge(type: string | null, sourceType: string) {
  const isManual = sourceType === 'MANUAL';
  const base = type || 'UNKNOWN';
  if (base === 'WEIGHT_TRAINING') {
    return (
      <Badge variant="outline" className="border-amber-300 text-amber-700 bg-amber-50">
        <Dumbbell className="mr-1 h-3 w-3" />
        웨이트
      </Badge>
    );
  }
  return (
    <Badge variant={isManual ? 'outline' : 'secondary'}>
      {isManual && <span className="mr-1 text-[10px]">수동</span>}
      {base}
    </Badge>
  );
}
