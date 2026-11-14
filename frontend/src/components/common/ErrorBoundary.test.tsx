import { describe, expect, it, vi } from 'vitest';
import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ErrorBoundary } from './ErrorBoundary';
import { QueryError } from './QueryError';
import { ApiError } from '@/lib/apiClient';

function Boom({ explode }: { explode: boolean }) {
  if (explode) throw new Error('kaboom');
  return <p>fine</p>;
}

describe('ErrorBoundary', () => {
  it('renders a fallback and recovers on reset', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    function Harness() {
      const [explode, setExplode] = useState(true);
      return (
        <ErrorBoundary onReset={() => setExplode(false)}>
          <Boom explode={explode} />
        </ErrorBoundary>
      );
    }
    render(<Harness />);
    expect(screen.getByRole('alert')).toHaveTextContent('kaboom');
    await userEvent.click(screen.getByRole('button', { name: /다시 시도/ }));
    expect(screen.getByText('fine')).toBeInTheDocument();
  });
});

describe('QueryError', () => {
  it('shows a friendly network message and calls onRetry', async () => {
    const onRetry = vi.fn();
    render(<QueryError error={new ApiError('x', 0, 'NETWORK_ERROR')} onRetry={onRetry} />);
    expect(screen.getByRole('alert')).toHaveTextContent('서버에 연결할 수 없습니다');
    await userEvent.click(screen.getByRole('button', { name: /다시 시도/ }));
    expect(onRetry).toHaveBeenCalledOnce();
  });
});
