import { HelpCircle, Save, ThumbsDown, ThumbsUp } from 'lucide-react';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';

const FEEDBACK_ACTIONS = [
  { status: 'CORRECT', icon: ThumbsUp, label: '정확해요' },
  { status: 'UNCLEAR', icon: HelpCircle, label: '애매해요' },
  { status: 'WRONG', icon: ThumbsDown, label: '틀렸어요' },
] as const;

export function InsightActions({ insightId }: { insightId: number }) {
  const handleSave = async () => {
    try {
      await api.insights.save(insightId);
      toast.success('인사이트가 저장되었습니다.');
    } catch {
      toast.error('저장에 실패했습니다.');
    }
  };

  const handleFeedback = async (status: string) => {
    try {
      await api.insights.feedback(insightId, status);
      toast.success('피드백이 반영되었습니다.');
    } catch {
      toast.error('피드백 전송에 실패했습니다.');
    }
  };

  return (
    <div className="flex items-center gap-1">
      <Button
        variant="ghost"
        size="icon"
        className="h-7 w-7"
        onClick={() => void handleSave()}
        aria-label="인사이트 저장"
        title="인사이트 저장"
      >
        <Save className="h-3.5 w-3.5" aria-hidden="true" />
      </Button>
      {FEEDBACK_ACTIONS.map(({ status, icon: Icon, label }) => (
        <Button
          key={status}
          variant="ghost"
          size="icon"
          className="h-7 w-7"
          onClick={() => void handleFeedback(status)}
          aria-label={`피드백: ${label}`}
          title={label}
        >
          <Icon className="h-3.5 w-3.5" aria-hidden="true" />
        </Button>
      ))}
    </div>
  );
}
