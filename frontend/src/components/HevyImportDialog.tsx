import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { toast } from 'sonner';
import {
  parseHevyText,
  applyExerciseMappings,
  getUnmappedExercises,
  toWeightTrainingRequest,
} from '@/lib/hevyParser';
import HevyExerciseMapping from '@/components/HevyExerciseMapping';
import type { ParsedHevyWorkout } from '@/lib/hevyParser';

interface HevyImportDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  exerciseNames: string[];
}

export default function HevyImportDialog({ open, onOpenChange, exerciseNames }: HevyImportDialogProps) {
  const queryClient = useQueryClient();
  const [text, setText] = useState('');
  const [parsed, setParsed] = useState<ParsedHevyWorkout | null>(null);
  const [bodyPart, setBodyPart] = useState('UPPER_BODY');

  const createMutation = useMutation({
    mutationFn: api.activities.createWeightTraining,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['activities'] });
      toast.success('울동 기록이 저장되었습니다');
      handleClose();
    },
    onError: () => {
      toast.error('저장에 실패했습니다');
    },
  });

  const handleParse = () => {
    const raw = parseHevyText(text);
    if (!raw) {
      toast.error('텍스트를 파싱할 수 없습니다. 형식을 확인해주세요.');
      return;
    }
    const mapped = applyExerciseMappings(raw, exerciseNames);
    setParsed(mapped);
  };

  const handleSave = () => {
    if (!parsed) return;
    const req = toWeightTrainingRequest(parsed, { bodyPart });
    createMutation.mutate(req);
  };

  const handleClose = () => {
    setText('');
    setParsed(null);
    onOpenChange(false);
  };

  const handleMappingChange = (idx: number, newName: string) => {
    if (!parsed) return;
    const next = { ...parsed };
    next.exercises = [...next.exercises];
    next.exercises[idx] = { ...next.exercises[idx], mappedName: newName };
    setParsed(next);
  };

  const unmapped = parsed ? getUnmappedExercises(parsed) : [];

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-lg max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <span>📋 Hevy 텍스트로 불러오기</span>
          </DialogTitle>
        </DialogHeader>

        {!parsed ? (
          <div className="space-y-4">
            <div className="space-y-2">
              <Label className="text-xs">Hevy 공유 텍스트를 붙여넣으세요</Label>
              <textarea
                className="w-full h-40 rounded-md border border-input bg-background px-3 py-2 text-sm resize-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                value={text}
                onChange={(e) => setText(e.target.value)}
                placeholder={`울동이름\n화요일, 5월 26, 2026 9:04오후\n\n풀 업\n세트 1: 6 회\n...\n\n@hevyapp`}
              />
            </div>
            <div className="space-y-2">
              <Label className="text-xs">부위</Label>
              <select
                value={bodyPart}
                onChange={(e) => setBodyPart(e.target.value)}
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              >
                <option value="UPPER_BODY">상체</option>
                <option value="LOWER_BODY">하체</option>
                <option value="PUSH">푸시</option>
                <option value="PULL">풀</option>
                <option value="LEG">레그</option>
                <option value="FULL_BODY">전신</option>
                <option value="CORE">코어</option>
                <option value="ARM">팔</option>
                <option value="BACK">등</option>
                <option value="CHEST">가슴</option>
                <option value="SHOULDER">어깨</option>
              </select>
            </div>
            <Button onClick={handleParse} disabled={!text.trim()} className="w-full">
              파싱
            </Button>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="rounded-md bg-muted p-3 space-y-1">
              <p className="text-sm font-medium">{parsed.activityName}</p>
              <p className="text-xs text-muted-foreground">{parsed.startTime.replace('T', ' ')}</p>
              <p className="text-xs text-muted-foreground">{parsed.exercises.length}개 운 · {parsed.exercises.reduce((s, e) => s + e.sets.length, 0)}세트</p>
            </div>

            {unmapped.length > 0 && (
              <div className="rounded-md border border-amber-200 bg-amber-50/50 p-3 space-y-2">
                <div className="flex items-center gap-2">
                  <Badge variant="outline" className="text-xs text-amber-700 border-amber-300">
                    매핑 필요
                  </Badge>
                  <span className="text-xs text-muted-foreground">
                    Hevy 이름을 기존 운과 연결하거나 새로 등록하세요
                  </span>
                </div>
                {parsed.exercises.map((ex, idx) => (
                  <HevyExerciseMapping
                    key={idx}
                    hevyName={ex.hevyName}
                    currentMapping={ex.mappedName}
                    exerciseNames={exerciseNames}
                    onChange={(name) => handleMappingChange(idx, name)}
                  />
                ))}
              </div>
            )}

            {unmapped.length === 0 && (
              <div className="space-y-2">
                {parsed.exercises.map((ex, idx) => (
                  <div key={idx} className="flex items-center justify-between py-1 text-sm">
                    <span className="font-medium">{ex.mappedName}</span>
                    <span className="text-xs text-muted-foreground">
                      {ex.sets.length}세트
                      {ex.sets.some((s) => s.weightKg) && ' · 무게'}
                      {ex.sets.some((s) => s.durationSeconds) && ' · 시간'}
                    </span>
                  </div>
                ))}
              </div>
            )}

            <div className="flex gap-2 pt-2">
              <Button variant="outline" onClick={() => setParsed(null)} className="flex-1">
                다시 입력
              </Button>
              <Button
                onClick={handleSave}
                disabled={createMutation.isPending}
                className="flex-1"
              >
                {createMutation.isPending ? '저장 중...' : '기록 저장'}
              </Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
