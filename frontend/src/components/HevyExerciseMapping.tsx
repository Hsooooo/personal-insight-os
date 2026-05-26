import { useState } from 'react';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { setHevyMapping } from '@/lib/hevyParser';

interface HevyExerciseMappingProps {
  hevyName: string;
  currentMapping: string;
  exerciseNames: string[];
  onChange: (mappedName: string) => void;
}

export default function HevyExerciseMapping({
  hevyName,
  currentMapping,
  exerciseNames,
  onChange,
}: HevyExerciseMappingProps) {
  const [value, setValue] = useState(currentMapping);

  const handleChange = (newValue: string) => {
    setValue(newValue);
    setHevyMapping(hevyName, newValue);
    onChange(newValue);
  };

  const isNew = !exerciseNames.includes(value) && value === hevyName;

  return (
    <div className="flex items-center gap-3 py-2">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium truncate">{hevyName}</span>
          {isNew && (
            <Badge variant="outline" className="text-xs shrink-0">
              새 운
            </Badge>
          )}
        </div>
      </div>
      <div className="w-48 shrink-0">
        <Select value={value} onValueChange={handleChange}>
          <SelectTrigger className="h-8 text-xs">
            <SelectValue placeholder="운 선택" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={hevyName}>🆕 "{hevyName}" 새로 등록</SelectItem>
            {exerciseNames
              .filter((n) => n !== hevyName)
              .sort()
              .map((name) => (
                <SelectItem key={name} value={name}>
                  {name}
                </SelectItem>
              ))}
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}
