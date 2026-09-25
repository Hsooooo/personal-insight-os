import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Search, SlidersHorizontal } from 'lucide-react';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet';
import type { ActivityFilter } from '@/types';
import { FilterSelect } from './FilterSelect';
import { ACTIVITY_TYPES, SORT_OPTIONS, TAG_OPTIONS } from './utils';

export function MobileFilterBar({
  draft,
  setDraft,
  applyFilter,
  resetFilter,
  hasActiveFilter,
  currentSortValue,
}: {
  draft: ActivityFilter;
  setDraft: (f: ActivityFilter) => void;
  applyFilter: () => void;
  resetFilter: () => void;
  hasActiveFilter: boolean;
  currentSortValue: string;
}) {
  const [showSearch, setShowSearch] = useState(false);

  const handleSortChange = (sortValue: string) => {
    const [sortBy, sortDir] = sortValue.split(',');
    setDraft({ ...draft, sortBy, sortDir });
  };

  return (
    <div className="space-y-2 md:hidden">
      <div className="flex flex-wrap items-center gap-2">
        <FilterSelect
          value={draft.activityType || ''}
          onChange={(v) => setDraft({ ...draft, activityType: v || undefined })}
          options={ACTIVITY_TYPES}
        />
        <FilterSelect
          value={draft.userTag === '' && draft.userTag !== undefined ? '__none__' : draft.userTag || ''}
          onChange={(v) => setDraft({ ...draft, userTag: v === '__none__' ? '' : v || undefined })}
          options={TAG_OPTIONS}
        />
        <FilterSelect
          value={currentSortValue}
          onChange={handleSortChange}
          options={SORT_OPTIONS}
        />
        <Button
          size="sm"
          variant={showSearch ? 'default' : 'outline'}
          className="h-8 px-2"
          onClick={() => setShowSearch((s) => !s)}
        >
          <Search className="h-3.5 w-3.5" />
        </Button>
        <Sheet>
          <SheetTrigger asChild>
            <Button size="sm" variant="outline" className="h-8 gap-1 px-2">
              <SlidersHorizontal className="h-3.5 w-3.5" />
              필터
            </Button>
          </SheetTrigger>
          <SheetContent side="bottom" className="h-auto max-h-[80vh]">
            <SheetHeader>
              <SheetTitle>고급 필터</SheetTitle>
            </SheetHeader>
            <div className="mt-4 space-y-4">
              <div className="space-y-2">
                <Label className="text-xs text-muted-foreground">기간</Label>
                <div className="flex items-center gap-2">
                  <Input
                    type="date"
                    value={draft.startTimeFrom || ''}
                    onChange={(e) => setDraft({ ...draft, startTimeFrom: e.target.value || undefined })}
                    className="h-9 text-xs"
                  />
                  <span className="text-xs text-muted-foreground">~</span>
                  <Input
                    type="date"
                    value={draft.startTimeTo || ''}
                    onChange={(e) => setDraft({ ...draft, startTimeTo: e.target.value || undefined })}
                    className="h-9 text-xs"
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label className="text-xs text-muted-foreground">거리 (m)</Label>
                <div className="flex items-center gap-2">
                  <Input
                    type="number"
                    placeholder="최소"
                    value={draft.minDistance || ''}
                    onChange={(e) => setDraft({ ...draft, minDistance: e.target.value || undefined })}
                    className="h-9 text-xs"
                  />
                  <span className="text-xs text-muted-foreground">~</span>
                  <Input
                    type="number"
                    placeholder="최대"
                    value={draft.maxDistance || ''}
                    onChange={(e) => setDraft({ ...draft, maxDistance: e.target.value || undefined })}
                    className="h-9 text-xs"
                  />
                </div>
              </div>
              <div className="flex gap-2 pt-2">
                <Button className="flex-1" size="sm" onClick={applyFilter}>
                  적용
                </Button>
                <Button variant="ghost" size="sm" onClick={resetFilter}>
                  초기화
                </Button>
              </div>
            </div>
          </SheetContent>
        </Sheet>
      </div>
      {showSearch && (
        <div className="flex items-center gap-2">
          <div className="relative flex-1">
            <Search className="absolute left-2 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="이름 검색"
              value={draft.activityName || ''}
              onChange={(e) => setDraft({ ...draft, activityName: e.target.value || undefined })}
              className="h-8 pl-7 text-xs"
              onKeyDown={(e) => e.key === 'Enter' && applyFilter()}
            />
          </div>
          <Button size="sm" variant="secondary" className="h-8 text-xs" onClick={applyFilter}>
            검색
          </Button>
        </div>
      )}
      <div className="flex items-center gap-2">
        <Button size="sm" variant="secondary" className="h-8 text-xs" onClick={applyFilter}>
          적용
        </Button>
        <Button size="sm" variant="ghost" className="h-8 text-xs" onClick={resetFilter}>
          초기화
        </Button>
        {hasActiveFilter && (
          <Badge variant="outline" className="text-xs">
            필터 적용중
          </Badge>
        )}
      </div>
    </div>
  );
}
