import { Calendar } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { confidenceColor, formatDateRange, type ChatMessage } from './askFormat';
import { EvidenceCard } from './EvidenceCard';
import { InsightActions } from './InsightActions';
import { MarkdownAnswer } from './MarkdownAnswer';

interface SystemMessageProps {
  message: ChatMessage;
  onFollowUp: (question: string) => void;
}

export function SystemMessage({ message: msg, onFollowUp }: SystemMessageProps) {
  return (
    <>
      {msg.period && (
        <div className="mb-3 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          <Badge variant="outline" className="font-normal">
            <Calendar className="mr-1 h-3 w-3" aria-hidden="true" />
            분석: {formatDateRange(msg.period.start, msg.period.end)}
          </Badge>
          <Badge variant="outline" className="font-normal">
            기준선: {formatDateRange(msg.period.baselineStart, msg.period.baselineEnd)}
          </Badge>
        </div>
      )}

      <MarkdownAnswer content={msg.content} />

      {msg.confidence && (
        <div className="mt-4 rounded-md border bg-muted/30 p-3">
          <div className="mb-1 flex items-center gap-2">
            <Badge className={`${confidenceColor(msg.confidence.level)} border`}>
              신뢰도 {msg.confidence.level} ({(msg.confidence.score * 100).toFixed(0)}%)
            </Badge>
          </div>
          <ul className="space-y-0.5 text-xs text-muted-foreground">
            {msg.confidence.reasons.map((reason, i) => (
              <li key={i}>• {reason}</li>
            ))}
          </ul>
        </div>
      )}

      {msg.evidences && msg.evidences.length > 0 && (
        <div className="mt-4 space-y-2">
          <p className="text-xs font-medium text-muted-foreground">근거</p>
          <div className="grid gap-2">
            {msg.evidences.map((evidence, i) => (
              <EvidenceCard key={i} evidence={evidence} />
            ))}
          </div>
        </div>
      )}

      <div className="mt-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          {msg.intent && (
            <Badge variant="secondary" className="text-xs">
              {msg.intent}
            </Badge>
          )}
        </div>
        {msg.insightId ? <InsightActions insightId={msg.insightId} /> : null}
      </div>

      {msg.followUpQuestions && msg.followUpQuestions.length > 0 && (
        <>
          <Separator className="my-3" />
          <div className="space-y-1">
            <p className="text-xs text-muted-foreground">추가 질문</p>
            <div className="flex flex-wrap gap-2">
              {msg.followUpQuestions.map((q, i) => (
                <Button
                  key={i}
                  variant="outline"
                  size="sm"
                  className="h-auto px-2 py-1 text-xs"
                  onClick={() => onFollowUp(q)}
                >
                  {q}
                </Button>
              ))}
            </div>
          </div>
        </>
      )}
    </>
  );
}
