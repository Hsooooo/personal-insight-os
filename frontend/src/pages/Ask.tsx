import { useState, useRef, useEffect } from 'react';
import { useMutation } from '@tanstack/react-query';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Link, useLocation } from 'react-router-dom';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import {
  Loader2,
  Send,
  Lightbulb,
  Save,
  ThumbsUp,
  ThumbsDown,
  HelpCircle,
  RotateCcw,
  Calendar,
  TrendingUp,
  TrendingDown,
  Minus,
  ExternalLink,
} from 'lucide-react';
import type { AskEvidence, AskConfidence, AskPeriod } from '@/types';

interface ChatMessage {
  id: number;
  type: 'user' | 'system';
  content: string;
  insightId?: number;
  intent?: string;
  period?: AskPeriod;
  confidence?: AskConfidence;
  evidences?: AskEvidence[];
  followUpQuestions?: string[];
}

function formatDateRange(start: string, end: string) {
  return `${start} ~ ${end}`;
}

function formatNumber(value: number | null, unit: string = '') {
  if (value === null || value === undefined) return '-';
  const formatted = Number.isInteger(value) ? value.toString() : value.toFixed(1);
  return `${formatted}${unit}`;
}

export default function Ask() {
  const location = useLocation();
  const initialQuestion = (location.state as any)?.question || '';
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [failedQuestion, setFailedQuestion] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const askMutation = useMutation({
    mutationFn: api.ask.ask,
    onSuccess: (response) => {
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now(),
          type: 'system',
          content: response.answer,
          insightId: response.insightId,
          intent: response.intent,
          period: response.period,
          confidence: response.confidence,
          evidences: response.evidences,
          followUpQuestions: response.followUpQuestions,
        },
      ]);
      setFailedQuestion(null);
    },
    onError: () => {
      toast.error('질문 처리 중 오류가 발생했습니다. 다시 시도해주세요.');
    },
  });

  useEffect(() => {
    if (initialQuestion) {
      setQuestion(initialQuestion);
      handleAsk(initialQuestion);
    }
  }, [initialQuestion]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleAsk = (q: string) => {
    if (!q.trim()) return;
    setMessages((prev) => [...prev, { id: Date.now(), type: 'user', content: q }]);
    askMutation.mutate(q);
    setQuestion('');
    setFailedQuestion(q);
  };

  const handleRetry = () => {
    if (failedQuestion) {
      handleAsk(failedQuestion);
    }
  };

  const handleSave = async (insightId: number) => {
    try {
      await api.insights.save(insightId);
      toast.success('인사이트가 저장되었습니다.');
    } catch (e) {
      toast.error('저장에 실패했습니다.');
    }
  };

  const handleFeedback = async (insightId: number, status: string) => {
    try {
      await api.insights.feedback(insightId, status);
      toast.success('피드백이 반영되었습니다.');
    } catch (e) {
      toast.error('피드백 전송에 실패했습니다.');
    }
  };

  const sampleQuestions = [
    '최근 컨디션이 떨어진 이유가 뭐야?',
    '러닝 기록이 좋았던 날들의 공통점은 뭐야?',
    '수면이 부족하면 내 울동 성과가 얼마나 떨어져?',
    '이번 주 훈련 강도는 적절했어?',
  ];

  const confidenceColor = (level: string) => {
    switch (level) {
      case 'HIGH':
        return 'bg-green-100 text-green-700 border-green-200';
      case 'MEDIUM':
        return 'bg-amber-100 text-amber-700 border-amber-200';
      default:
        return 'bg-red-100 text-red-700 border-red-200';
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">Ask My Data</h2>
        <p className="text-muted-foreground">Ask natural language questions about your health data</p>
      </div>

      <div className="mx-auto max-w-3xl">
        {messages.length === 0 && (
          <div className="mb-6 grid gap-2 sm:grid-cols-2">
            {sampleQuestions.map((q, i) => (
              <Button
                key={i}
                variant="outline"
                className="h-auto justify-start py-3 px-4 text-left text-sm"
                onClick={() => handleAsk(q)}
              >
                <Lightbulb className="mr-2 h-4 w-4 shrink-0 text-amber-500" />
                {q}
              </Button>
            ))}
          </div>
        )}

        <div className="space-y-4 mb-6 max-h-[600px] overflow-y-auto">
          {messages.map((msg) => (
            <div key={msg.id} className={`flex ${msg.type === 'user' ? 'justify-end' : 'justify-start'}`}>
              <Card className={`max-w-[80%] ${msg.type === 'user' ? 'bg-indigo-50 border-indigo-100' : ''}`}>
                <CardContent className="p-4">
                  {msg.type === 'user' ? (
                    <div className="text-sm">{msg.content}</div>
                  ) : (
                    <>
                      {msg.period && (
                        <div className="mb-3 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                          <Badge variant="outline" className="font-normal">
                            <Calendar className="mr-1 h-3 w-3" />
                            분석: {formatDateRange(msg.period.start, msg.period.end)}
                          </Badge>
                          <Badge variant="outline" className="font-normal">
                            기준선: {formatDateRange(msg.period.baselineStart, msg.period.baselineEnd)}
                          </Badge>
                        </div>
                      )}

                      <div className="text-sm leading-relaxed">
                        <ReactMarkdown
                          remarkPlugins={[remarkGfm]}
                          components={{
                            table: ({ children }) => (
                              <table className="w-full text-sm border-collapse my-2">{children}</table>
                            ),
                            thead: ({ children }) => <thead className="bg-muted">{children}</thead>,
                            th: ({ children }) => (
                              <th className="border px-2 py-1 text-left font-medium">{children}</th>
                            ),
                            td: ({ children }) => <td className="border px-2 py-1">{children}</td>,
                            h3: ({ children }) => <h3 className="text-base font-semibold mt-3 mb-1">{children}</h3>,
                            ul: ({ children }) => <ul className="list-disc pl-4 my-1">{children}</ul>,
                            ol: ({ children }) => <ol className="list-decimal pl-4 my-1">{children}</ol>,
                            p: ({ children }) => <p className="my-1">{children}</p>,
                          }}
                        >
                          {msg.content}
                        </ReactMarkdown>
                      </div>

                      {msg.confidence && (
                        <div className="mt-4 rounded-md border bg-muted/30 p-3">
                          <div className="flex items-center gap-2 mb-1">
                            <Badge className={`${confidenceColor(msg.confidence.level)} border`}>
                              신뢰도 {msg.confidence.level} ({(msg.confidence.score * 100).toFixed(0)}%)
                            </Badge>
                          </div>
                          <ul className="text-xs text-muted-foreground space-y-0.5">
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
                        {msg.insightId && (
                          <div className="flex items-center gap-1">
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-7 w-7"
                              onClick={() => handleSave(msg.insightId!)}
                            >
                              <Save className="h-3.5 w-3.5" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-7 w-7"
                              onClick={() => handleFeedback(msg.insightId!, 'CORRECT')}
                            >
                              <ThumbsUp className="h-3.5 w-3.5" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-7 w-7"
                              onClick={() => handleFeedback(msg.insightId!, 'UNCLEAR')}
                            >
                              <HelpCircle className="h-3.5 w-3.5" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-7 w-7"
                              onClick={() => handleFeedback(msg.insightId!, 'WRONG')}
                            >
                              <ThumbsDown className="h-3.5 w-3.5" />
                            </Button>
                          </div>
                        )}
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
                                  className="h-auto py-1 px-2 text-xs"
                                  onClick={() => handleAsk(q)}
                                >
                                  {q}
                                </Button>
                              ))}
                            </div>
                          </div>
                        </>
                      )}
                    </>
                  )}
                </CardContent>
              </Card>
            </div>
          ))}

          {askMutation.isError && failedQuestion && (
            <div className="flex justify-center">
              <Button variant="outline" size="sm" onClick={handleRetry} className="gap-1">
                <RotateCcw className="h-3.5 w-3.5" />
                &quot;{failedQuestion}&quot; 다시 시도
              </Button>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleAsk(question);
          }}
          className="flex gap-2"
        >
          <Input
            placeholder="Ask about your health data..."
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            className="flex-1"
            disabled={askMutation.isPending}
          />
          <Button type="submit" disabled={askMutation.isPending || !question.trim()}>
            {askMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
          </Button>
        </form>
      </div>
    </div>
  );
}

function EvidenceCard({ evidence }: { evidence: AskEvidence }) {
  const changePositive = evidence.changeRate !== null && evidence.changeRate > 0;
  const changeNegative = evidence.changeRate !== null && evidence.changeRate < 0;
  const ChangeIcon = changePositive ? TrendingUp : changeNegative ? TrendingDown : Minus;
  const changeColor = 'text-muted-foreground';

  const linkTo =
    evidence.route ||
    (evidence.sourceId && evidence.type === 'ACTIVITY'
      ? `/activities/${evidence.sourceId}`
      : evidence.sourceDate
        ? `/health?date=${evidence.sourceDate}`
        : null);

  return (
    <div className="rounded-md border p-3 text-sm hover:bg-muted/20 transition-colors">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="font-medium">{evidence.label}</p>
          <p className="text-muted-foreground text-xs">{evidence.observation}</p>
        </div>
        {linkTo && (
          <Link
            to={linkTo}
            className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-accent-foreground"
          >
            <ExternalLink className="h-3.5 w-3.5" />
          </Link>
        )}
      </div>
      <div className="mt-2 flex items-center gap-3 text-xs">
        <span className="text-muted-foreground">{evidence.comparison}</span>
        {evidence.changeRate !== null && (
          <span className={`flex items-center gap-0.5 ${changeColor}`}>
            <ChangeIcon className="h-3 w-3" />
            {Math.abs(evidence.changeRate).toFixed(1)}%
          </span>
        )}
      </div>
      <div className="mt-2 grid grid-cols-2 gap-2 text-xs">
        <div className="rounded bg-muted/40 px-2 py-1">
          <span className="text-muted-foreground">현재</span>
          <div className="font-medium">{formatNumber(evidence.currentValue, evidence.unit)}</div>
        </div>
        <div className="rounded bg-muted/40 px-2 py-1">
          <span className="text-muted-foreground">기준선</span>
          <div className="font-medium">{formatNumber(evidence.baselineValue, evidence.unit)}</div>
        </div>
      </div>
    </div>
  );
}
