import { useCallback, useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';

/** 클립보드 복사 + 최근 복사한 항목 표시(2초) */
export function useCopyToClipboard() {
  const [copied, setCopied] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    []
  );

  const copy = useCallback(async (text: string, id: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(id);
      toast.success('복사되었습니다');
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => setCopied(null), 2000);
    } catch {
      toast.error('클립보드에 복사하지 못했습니다');
    }
  }, []);

  return { copied, copy };
}
