import { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertTriangle, RotateCcw } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface ErrorBoundaryProps {
  children: ReactNode;
  /** 값이 바뀌면 오류 상태를 자동 초기화한다 (예: 라우트 경로) */
  resetKey?: unknown;
  onReset?: () => void;
  fallback?: (props: { error: Error; reset: () => void }) => ReactNode;
}

interface ErrorBoundaryState {
  error: Error | null;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled UI error', error, info.componentStack);
  }

  componentDidUpdate(prevProps: ErrorBoundaryProps) {
    if (this.state.error && prevProps.resetKey !== this.props.resetKey) {
      this.setState({ error: null });
    }
  }

  reset = () => {
    this.props.onReset?.();
    this.setState({ error: null });
  };

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;
    if (this.props.fallback) return this.props.fallback({ error, reset: this.reset });
    return <DefaultErrorFallback error={error} reset={this.reset} />;
  }
}

function DefaultErrorFallback({ error, reset }: { error: Error; reset: () => void }) {
  return (
    <div role="alert" className="flex min-h-[50vh] flex-col items-center justify-center gap-3 p-6 text-center">
      <AlertTriangle className="h-10 w-10 text-destructive" aria-hidden="true" />
      <h2 className="text-lg font-semibold">화면을 표시하는 중 문제가 발생했습니다</h2>
      <p className="max-w-md text-sm text-muted-foreground">{error.message || '알 수 없는 오류'}</p>
      <div className="flex gap-2">
        <Button variant="outline" onClick={reset} className="gap-1">
          <RotateCcw className="h-4 w-4" aria-hidden="true" />
          다시 시도
        </Button>
        <Button variant="ghost" onClick={() => window.location.reload()}>
          새로고침
        </Button>
      </div>
    </div>
  );
}
