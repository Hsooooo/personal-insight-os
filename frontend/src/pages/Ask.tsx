import { useCallback, useEffect, useRef, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useLocation } from 'react-router-dom';
import { toast } from 'sonner';
import { Lightbulb, Loader2, RotateCcw, Send } from 'lucide-react';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { getInitialQuestion, SAMPLE_QUESTIONS, type ChatMessage } from '@/components/ask/askFormat';
import { SystemMessage } from '@/components/ask/SystemMessage';

export default function Ask() {
  const location = useLocation();
  const initialQuestion = getInitialQuestion(location.state);
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [failedQuestion, setFailedQuestion] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const askedInitialRef = useRef<string | null>(null);

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

  const { mutate: ask } = askMutation;
  const handleAsk = useCallback(
    (q: string) => {
      if (!q.trim()) return;
      setMessages((prev) => [...prev, { id: Date.now(), type: 'user', content: q }]);
      ask(q);
      setQuestion('');
      setFailedQuestion(q);
    },
    [ask]
  );

  // 대시보드의 빠른 질문으로 진입한 경우 한 번만 자동 질문한다 (StrictMode 이중 실행 방지)
  useEffect(() => {
    if (initialQuestion && askedInitialRef.current !== initialQuestion) {
      askedInitialRef.current = initialQuestion;
      handleAsk(initialQuestion);
    }
  }, [initialQuestion, handleAsk]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView?.({ behavior: 'smooth' });
  }, [messages]);

  const handleRetry = () => {
    if (failedQuestion) handleAsk(failedQuestion);
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
            {SAMPLE_QUESTIONS.map((q, i) => (
              <Button
                key={i}
                variant="outline"
                className="h-auto justify-start px-4 py-3 text-left text-sm"
                onClick={() => handleAsk(q)}
              >
                <Lightbulb className="mr-2 h-4 w-4 shrink-0 text-amber-500" aria-hidden="true" />
                {q}
              </Button>
            ))}
          </div>
        )}

        <div className="mb-6 max-h-[600px] space-y-4 overflow-y-auto" role="log" aria-live="polite" aria-label="대화 내역">
          {messages.map((msg) => (
            <div key={msg.id} className={`flex ${msg.type === 'user' ? 'justify-end' : 'justify-start'}`}>
              <Card className={`max-w-[80%] ${msg.type === 'user' ? 'border-indigo-100 bg-indigo-50' : ''}`}>
                <CardContent className="p-4">
                  {msg.type === 'user' ? (
                    <div className="text-sm">{msg.content}</div>
                  ) : (
                    <SystemMessage message={msg} onFollowUp={handleAsk} />
                  )}
                </CardContent>
              </Card>
            </div>
          ))}

          {askMutation.isPending && (
            <p className="flex items-center gap-2 text-sm text-muted-foreground" role="status">
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
              답변을 생성하는 중...
            </p>
          )}

          {askMutation.isError && failedQuestion && (
            <div className="flex justify-center">
              <Button variant="outline" size="sm" onClick={handleRetry} className="gap-1">
                <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
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
          <label htmlFor="ask-question" className="sr-only">
            질문 입력
          </label>
          <Input
            id="ask-question"
            placeholder="Ask about your health data..."
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            className="flex-1"
            disabled={askMutation.isPending}
          />
          <Button type="submit" disabled={askMutation.isPending || !question.trim()} aria-label="질문 보내기">
            {askMutation.isPending ? (
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            ) : (
              <Send className="h-4 w-4" aria-hidden="true" />
            )}
          </Button>
        </form>
      </div>
    </div>
  );
}
