import { useNavigate } from 'react-router-dom';
import { MessageSquare } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

export function QuickQuestionsCard({ questions }: { questions: string[] | undefined }) {
  const navigate = useNavigate();
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <MessageSquare className="h-4 w-4 text-indigo-500" aria-hidden="true" />
          Quick Questions
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {questions?.map((q, i) => (
          <Button
            key={i}
            variant="outline"
            className="h-auto w-full justify-start px-3 py-2 text-left"
            onClick={() => navigate('/ask', { state: { question: q } })}
          >
            <span className="line-clamp-2 text-sm">{q}</span>
          </Button>
        ))}
      </CardContent>
    </Card>
  );
}
