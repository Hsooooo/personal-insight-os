import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { getErrorMessage, isApiError } from '@/lib/api';

/** 세션 만료/취소는 별도 처리되므로 전역 토스트에서 제외한다 */
function shouldToast(error: unknown): boolean {
  if (isApiError(error) && (error.code === 'ABORTED' || error.code === 'UNAUTHORIZED')) return false;
  return true;
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    queryCache: new QueryCache({
      onError: (error, query) => {
        // 최초 로딩 실패는 각 화면의 <QueryError>가 보여준다.
        // 이미 데이터가 있는 상태에서 백그라운드 재조회가 실패한 경우만 토스트로 알린다.
        if (query.state.data === undefined || !shouldToast(error)) return;
        toast.error(`데이터를 새로고침하지 못했습니다: ${getErrorMessage(error)}`, { id: `query-${query.queryHash}` });
      },
    }),
    mutationCache: new MutationCache({
      onError: (error, _variables, _context, mutation) => {
        // 개별 onError가 있는 mutation은 자체적으로 메시지를 보여준다
        if (mutation.options.onError || !shouldToast(error)) return;
        toast.error(getErrorMessage(error));
      },
    }),
    defaultOptions: {
      queries: {
        retry: (failureCount, error) => {
          if (isApiError(error) && error.status >= 400 && error.status < 500) return false;
          return failureCount < 1;
        },
        refetchOnWindowFocus: false,
      },
    },
  });
}
