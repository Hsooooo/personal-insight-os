import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { TableCell } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import type { Activity as ActivityType } from '@/types';
import { TAG_PRESETS, getTagColor } from './utils';

export function ActivityTagCell({ activity }: { activity: ActivityType }) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [customInput, setCustomInput] = useState(false);
  const [customValue, setCustomValue] = useState('');

  const updateTagMutation = useMutation({
    mutationFn: ({ id, tag }: { id: number; tag: string }) => api.activities.updateTag(id, tag),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['activities'] });
      setEditing(false);
      setCustomInput(false);
    },
  });

  if (editing) {
    if (customInput) {
      return (
        <TableCell>
          <div className="flex gap-1">
            <input
              autoFocus
              className="h-7 w-28 rounded border border-input px-2 text-xs"
              placeholder="태그 입력"
              value={customValue}
              onChange={(e) => setCustomValue(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && customValue.trim()) {
                  updateTagMutation.mutate({ id: activity.id, tag: customValue.trim() });
                }
                if (e.key === 'Escape') {
                  setCustomInput(false);
                }
              }}
            />
            <button
              className="h-7 rounded bg-primary px-2 text-xs text-primary-foreground"
              onClick={() => customValue.trim() && updateTagMutation.mutate({ id: activity.id, tag: customValue.trim() })}
            >
              저장
            </button>
            <button
              className="h-7 rounded border px-2 text-xs"
              onClick={() => setCustomInput(false)}
            >
              취소
            </button>
          </div>
        </TableCell>
      );
    }

    return (
      <TableCell>
        <select
          autoFocus
          className="h-7 w-32 rounded border border-input px-1 text-xs"
          onChange={(e) => {
            const value = e.target.value;
            if (value === '__custom__') {
              setCustomInput(true);
            } else if (value === '__remove__') {
              updateTagMutation.mutate({ id: activity.id, tag: '' });
            } else if (value) {
              updateTagMutation.mutate({ id: activity.id, tag: value });
            }
          }}
          onBlur={() => setEditing(false)}
          defaultValue=""
        >
          <option value="" disabled>태그 선택</option>
          {TAG_PRESETS.map((t) => (
            <option key={t} value={t}>{t}</option>
          ))}
          <option value="__custom__">직접 입력...</option>
          {activity.userTag && <option value="__remove__">태그 제거</option>}
        </select>
      </TableCell>
    );
  }

  return (
    <TableCell
      className="cursor-pointer"
      onClick={() => setEditing(true)}
      title="클릭하여 태그 변경"
    >
      {activity.userTag ? (
        <Badge className={getTagColor(activity.userTag)}>{activity.userTag}</Badge>
      ) : (
        <span className="text-xs text-muted-foreground hover:text-foreground">+ 태그</span>
      )}
    </TableCell>
  );
}
