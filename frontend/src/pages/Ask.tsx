import { useState, useRef, useEffect } from 'react';
import { useMutation } from '@tanstack/react-query';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import { Loader2, Send, Lightbulb, Save, ThumbsUp, ThumbsDown, HelpCircle } from 'lucide-react';
import { useLocation } from 'react-router-dom';

interface ChatMessage {
  id: number;
  type: 'user' | 'system';
  content: string;
  insightId?: number;
  evidences?: string[];
  confidence?: string;
  followUp?: string;
}

export default function Ask() {
  const location = useLocation();
  const initialQuestion = (location.state as any)?.question || '';
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const askMutation = useMutation({
    mutationFn: api.ask.ask,
    onSuccess: (response) => {
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now(),
          type: 'system',
          content: response.conclusion,
          insightId: response.insightId,
          evidences: response.evidenceSummary,
          confidence: response.confidence,
          followUp: response.followUpQuestion,
        },
      ]);
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
  };

  const handleSave = async (insightId: number) => {
    try {
      await api.insights.save(insightId);
    } catch (e) {
      // ignore
    }
  };

  const handleFeedback = async (insightId: number, status: string) => {
    try {
      await api.insights.feedback(insightId, status);
    } catch (e) {
      // ignore
    }
  };

  const sampleQuestions = [
    '최근 컨디션이 떨어진 이유가 뭐야?',
    '러닝 기록이 좋았던 날들의 공통점은 뭐야?',
    '수면이 부족하면 내 운동 성과가 얼마나 떨어져?',
    '이번 주 훈련 강도는 적절했어?',
  ];

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
                  <div className="text-sm leading-relaxed">
                    <ReactMarkdown
                      remarkPlugins={[remarkGfm]}
                      components={{
                        table: ({ children }) => (
                          <table className="w-full text-sm border-collapse my-2">{children}</table>
                        ),
                        thead: ({ children }) => (
                          <thead className="bg-muted">{children}</thead>
                        ),
                        th: ({ children }) => (
                          <th className="border px-2 py-1 text-left font-medium">{children}</th>
                        ),
                        td: ({ children }) => (
                          <td className="border px-2 py-1">{children}</td>
                        ),
                        h3: ({ children }) => (
                          <h3 className="text-base font-semibold mt-3 mb-1">{children}</h3>
                        ),
                        ul: ({ children }) => (
                          <ul className="list-disc pl-4 my-1">{children}</ul>
                        ),
                        ol: ({ children }) => (
                          <ol className="list-decimal pl-4 my-1">{children}</ol>
                        ),
                        p: ({ children }) => (
                          <p className="my-1">{children}</p>
                        ),
                      }}
                    >
                      {msg.content}
                    </ReactMarkdown>
                  </div>

                  {msg.type === 'system' && (
                    <>
                      {msg.evidences && msg.evidences.length > 0 && (
                        <div className="mt-3">
                          <p className="text-xs font-medium text-muted-foreground mb-1">Evidence</p>
                          <ul className="space-y-1">
                            {msg.evidences.map((e, i) => (
                              <li key={i} className="text-xs text-muted-foreground flex items-start gap-1">
                                <span className="mt-1 h-1 w-1 rounded-full bg-indigo-400 shrink-0" />
                                {e}
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}

                      <div className="mt-3 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <Badge variant="outline" className="text-xs">
                            Confidence: {msg.confidence}
                          </Badge>
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

                      {msg.followUp && (
                        <>
                          <Separator className="my-3" />
                          <p className="text-xs text-muted-foreground italic">{msg.followUp}</p>
                        </>
                      )}
                    </>
                  )}
                </CardContent>
              </Card>
            </div>
          ))}
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
</div>
  );
}
