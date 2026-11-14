import { Skeleton } from '@/components/ui/skeleton';

/** 라우트 지연 로딩(Suspense) 중 표시되는 기본 스켈레톤 */
export function PageSkeleton() {
  return (
    <div className="space-y-6" aria-busy="true" aria-label="페이지 불러오는 중">
      <div className="space-y-2">
        <Skeleton className="h-9 w-56" />
        <Skeleton className="h-4 w-80 max-w-full" />
      </div>
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-28" />
        ))}
      </div>
      <Skeleton className="h-72 w-full" />
    </div>
  );
}
