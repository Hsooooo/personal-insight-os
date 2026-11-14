import { Check, Copy } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

interface NewKeyBannerProps {
  rawKey: string;
  copied: boolean;
  onCopy: () => void;
  onClose: () => void;
}

export function NewKeyBanner({ rawKey, copied, onCopy, onClose }: NewKeyBannerProps) {
  return (
    <Card className="border-green-500/30 bg-green-500/10" role="status">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Check className="h-4 w-4 text-green-600" aria-hidden="true" />
          새 API 키가 발행되었습니다
        </CardTitle>
        <CardDescription className="text-yellow-700 dark:text-yellow-400">
          이 키는 지금만 표시됩니다. 반드시 저장하세요!
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="flex items-center gap-2">
          <code className="flex-1 break-all rounded-md bg-background p-3 text-sm">{rawKey}</code>
          <Button variant="outline" size="icon" onClick={onCopy} aria-label="새 API 키 복사">
            {copied ? <Check className="h-4 w-4" aria-hidden="true" /> : <Copy className="h-4 w-4" aria-hidden="true" />}
          </Button>
        </div>
        <Button variant="ghost" className="mt-2" onClick={onClose}>
          닫기
        </Button>
      </CardContent>
    </Card>
  );
}
