import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { ClipboardPaste, Plus, RefreshCw, X } from 'lucide-react';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet';

export function MobileActionMenu({
  onHevy,
  onWeight,
  onSyncToday,
  syncRunning,
  showForm,
}: {
  onHevy: () => void;
  onWeight: () => void;
  onSyncToday: () => void;
  syncRunning: boolean;
  showForm: boolean;
}) {
  const [open, setOpen] = useState(false);
  return (
    <div className="md:hidden">
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetTrigger asChild>
          <Button size="sm" className="h-9 gap-1">
            <Plus className="h-4 w-4" />
            기록
          </Button>
        </SheetTrigger>
        <SheetContent side="bottom" className="h-auto">
          <SheetHeader>
            <SheetTitle>새 기록</SheetTitle>
          </SheetHeader>
          <div className="mt-4 space-y-2">
            <Button
              variant="outline"
              className="w-full justify-start gap-2"
              onClick={() => {
                onSyncToday();
                setOpen(false);
              }}
              disabled={syncRunning}
            >
              <RefreshCw className={`h-4 w-4 ${syncRunning ? 'animate-spin' : ''}`} />
              오늘 동기화
            </Button>
            <Button
              variant="outline"
              className="w-full justify-start gap-2"
              onClick={() => {
                onHevy();
                setOpen(false);
              }}
            >
              <ClipboardPaste className="h-4 w-4" />
              Hevy 불러오기
            </Button>
            <Button
              variant="outline"
              className="w-full justify-start gap-2"
              onClick={() => {
                onWeight();
                setOpen(false);
              }}
            >
              {showForm ? <X className="h-4 w-4" /> : <Plus className="h-4 w-4" />}
              {showForm ? '기록 닫기' : '수동 웨이트 기록'}
            </Button>
          </div>
        </SheetContent>
      </Sheet>
    </div>
  );
}
