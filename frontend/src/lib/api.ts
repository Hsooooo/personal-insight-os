import { fetchApi, type RequestOptions } from '@/lib/apiClient';
import type {
  Activity,
  ActivityFilter,
  ApiKey,
  AskRequest,
  AskResponse,
  AuthResponse,
  DashboardSummary,
  GarminActivityLap,
  Goal,
  GraphData,
  HealthMetric,
  Insight,
  LlmProvider,
  Page,
  ProviderConnection,
  Sleep,
  SyncLog,
  User,
  WeightTrainingRequest,
  FinanceCycle,
  FinanceAccount,
  FinanceAccountAutoMapResponse,
  FinanceImportConfirmRequest,
  FinanceImportConfirmResponse,
  FinanceImportPreviewResponse,
  FinanceTransactionDeleteResponse,
  FinanceTransaction,
  RecurringBill,
  RecurringBillVersion,
  OAuthAuthorizationRequest,
  OAuthDecisionResponse,
  OAuthGrant,
} from '@/types';

export { ApiError, isApiError, getErrorMessage, setUnauthorizedHandler, refreshSession } from '@/lib/apiClient';
export type { ApiErrorCode, RequestOptions } from '@/lib/apiClient';


export const api = {
  auth: {
    login: (email: string, password: string): Promise<AuthResponse> =>
      fetchApi('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }), skipAuthRefresh: true }),
    register: (email: string, password: string, displayName?: string): Promise<AuthResponse> =>
      fetchApi('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({ email, password, displayName }),
        skipAuthRefresh: true,
      }),
    me: (opts?: RequestOptions): Promise<User> =>
      fetchApi('/api/auth/me', { signal: opts?.signal }),
    logout: (): Promise<void> => fetchApi('/api/auth/logout', { method: 'POST', skipAuthRefresh: true }),
  },
  dashboard: {
    summary: (opts?: RequestOptions): Promise<DashboardSummary> =>
      fetchApi('/api/dashboard/summary', { signal: opts?.signal }),
  },
  dataSources: {
    list: (opts?: RequestOptions): Promise<ProviderConnection[]> =>
      fetchApi('/api/data-sources', { signal: opts?.signal }),
    connectGarmin: (email: string, password: string): Promise<ProviderConnection> =>
      fetchApi('/api/data-sources/garmin/connect', { method: 'POST', body: JSON.stringify({ email, password }) }),
    syncGarmin: (syncType?: string, dateFrom?: string, dateTo?: string): Promise<SyncLog> =>
      fetchApi('/api/data-sources/garmin/sync', {
        method: 'POST',
        body: JSON.stringify({ syncType, dateFrom, dateTo }),
      }),
    getSyncLogs: (opts?: RequestOptions): Promise<SyncLog[]> =>
      fetchApi('/api/data-sources/garmin/sync-logs', { signal: opts?.signal }),
    getSyncLog: (id: number, opts?: RequestOptions): Promise<SyncLog> =>
      fetchApi(`/api/data-sources/garmin/sync-logs/${id}`, { signal: opts?.signal }),
    generateMockData: (): Promise<ProviderConnection> =>
      fetchApi('/api/data-sources/garmin/mock', { method: 'POST' }),
    disconnectGarmin: (): Promise<void> => fetchApi('/api/data-sources/garmin', { method: 'DELETE' }),
  },
  activities: {
    list: (page = 0, size = 20, filter?: ActivityFilter, opts?: RequestOptions): Promise<Page<Activity>> => {
      const params = new URLSearchParams({ page: String(page), size: String(size) });
      if (filter?.activityType) params.append('activityType', filter.activityType);
      if (filter?.userTag !== undefined) params.append('userTag', filter.userTag);
      if (filter?.activityName) params.append('activityName', filter.activityName);
      if (filter?.startTimeFrom) params.append('startTimeFrom', filter.startTimeFrom);
      if (filter?.startTimeTo) params.append('startTimeTo', filter.startTimeTo);
      if (filter?.minDistance) params.append('minDistance', filter.minDistance);
      if (filter?.maxDistance) params.append('maxDistance', filter.maxDistance);
      if (filter?.sortBy) params.append('sortBy', filter.sortBy);
      if (filter?.sortDir) params.append('sortDir', filter.sortDir);
      return fetchApi(`/api/activities?${params.toString()}`, { signal: opts?.signal });
    },
    get: (id: number, opts?: RequestOptions): Promise<Activity> =>
      fetchApi(`/api/activities/${id}`, { signal: opts?.signal }),
    getLaps: (id: number, opts?: RequestOptions): Promise<GarminActivityLap[]> =>
      fetchApi(`/api/activities/${id}/laps`, { signal: opts?.signal }),
    updateTag: (id: number, userTag: string): Promise<Activity> =>
      fetchApi(`/api/activities/${id}/tag`, { method: 'PATCH', body: JSON.stringify({ userTag }) }),
    createWeightTraining: (data: WeightTrainingRequest): Promise<Activity> =>
      fetchApi('/api/activities/weight', { method: 'POST', body: JSON.stringify(data) }),
    updateWeightTraining: (id: number, data: WeightTrainingRequest): Promise<Activity> =>
      fetchApi(`/api/activities/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
    deleteWeightTraining: (id: number): Promise<void> =>
      fetchApi(`/api/activities/${id}`, { method: 'DELETE' }),
    getExerciseNames: (opts?: RequestOptions): Promise<string[]> =>
      fetchApi('/api/activities/exercises', { signal: opts?.signal }),
  },
  health: {
    metrics: (start: string, end: string, opts?: RequestOptions): Promise<HealthMetric[]> =>
      fetchApi(`/api/health/metrics?start=${start}&end=${end}`, { signal: opts?.signal }),
    sleep: (start: string, end: string, opts?: RequestOptions): Promise<Sleep[]> =>
      fetchApi(`/api/health/sleep?start=${start}&end=${end}`, { signal: opts?.signal }),
  },
  graph: {
    get: (days?: number, view?: string, raceCategory?: string, opts?: RequestOptions): Promise<GraphData> => {
      const params = new URLSearchParams();
      if (days !== undefined) params.append('days', String(days));
      if (view) params.append('view', view);
      if (raceCategory) params.append('raceCategory', raceCategory);
      const qs = params.toString();
      return fetchApi(`/api/graph${qs ? '?' + qs : ''}`, { signal: opts?.signal });
    },
  },
  ask: {
    ask: (question: string): Promise<AskResponse> =>
      fetchApi('/api/ask', { method: 'POST', body: JSON.stringify({ question } satisfies AskRequest) }),
  },
  insights: {
    list: (category?: string, feedbackStatus?: string, opts?: RequestOptions): Promise<Insight[]> => {
      const params = new URLSearchParams();
      if (category) params.append('category', category);
      if (feedbackStatus) params.append('feedbackStatus', feedbackStatus);
      return fetchApi(`/api/insights?${params.toString()}`, { signal: opts?.signal });
    },
    saved: (opts?: RequestOptions): Promise<Insight[]> =>
      fetchApi('/api/insights/saved', { signal: opts?.signal }),
    get: (id: number, opts?: RequestOptions): Promise<Insight> =>
      fetchApi(`/api/insights/${id}`, { signal: opts?.signal }),
    save: (id: number): Promise<Insight> => fetchApi(`/api/insights/${id}/save`, { method: 'POST' }),
    feedback: (id: number, feedbackStatus: string): Promise<Insight> =>
      fetchApi(`/api/insights/${id}/feedback`, { method: 'POST', body: JSON.stringify({ feedbackStatus }) }),
    delete: (id: number): Promise<void> => fetchApi(`/api/insights/${id}`, { method: 'DELETE' }),
    detectAnomalyAlerts: (): Promise<Insight[]> =>
      fetchApi('/api/insights/anomaly-alerts/detect', { method: 'POST' }),
  },
  goals: {
    list: (opts?: RequestOptions): Promise<Goal[]> =>
      fetchApi('/api/goals', { signal: opts?.signal }),
    create: (goal: Partial<Goal>): Promise<Goal> =>
      fetchApi('/api/goals', { method: 'POST', body: JSON.stringify(goal) }),
    get: (id: number, opts?: RequestOptions): Promise<Goal> =>
      fetchApi(`/api/goals/${id}`, { signal: opts?.signal }),
    update: (id: number, goal: Partial<Goal>): Promise<Goal> =>
      fetchApi(`/api/goals/${id}`, { method: 'PATCH', body: JSON.stringify(goal) }),
    delete: (id: number): Promise<void> => fetchApi(`/api/goals/${id}`, { method: 'DELETE' }),
  },
  briefings: {
    latest: (opts?: RequestOptions): Promise<Insight | null> =>
      fetchApi('/api/briefings/latest', { signal: opts?.signal }),
    generate: (force = false): Promise<Insight> =>
      fetchApi('/api/briefings/generate', { method: 'POST', body: JSON.stringify({ force }) }),
  },
  llmProviders: {
    list: (opts?: RequestOptions): Promise<LlmProvider[]> =>
      fetchApi('/api/settings/llm-providers', { signal: opts?.signal }),
    create: (provider: Partial<LlmProvider>): Promise<LlmProvider> =>
      fetchApi('/api/settings/llm-providers', { method: 'POST', body: JSON.stringify(provider) }),
    update: (id: number, provider: Partial<LlmProvider>): Promise<LlmProvider> =>
      fetchApi(`/api/settings/llm-providers/${id}`, { method: 'PATCH', body: JSON.stringify(provider) }),
    delete: (id: number): Promise<void> => fetchApi(`/api/settings/llm-providers/${id}`, { method: 'DELETE' }),
  },
  apiKeys: {
    list: (opts?: RequestOptions): Promise<ApiKey[]> =>
      fetchApi('/api/auth/api-keys', { signal: opts?.signal }),
    create: (name: string): Promise<ApiKey> =>
      fetchApi('/api/auth/api-keys', { method: 'POST', body: JSON.stringify({ name }) }),
    delete: (id: number): Promise<void> => fetchApi(`/api/auth/api-keys/${id}`, { method: 'DELETE' }),
  },
  oauth: {
    getRequest: (requestId: string, opts?: RequestOptions): Promise<OAuthAuthorizationRequest> =>
      fetchApi(`/api/oauth/requests/${encodeURIComponent(requestId)}`, { signal: opts?.signal }),
    decide: (requestId: string, approve: boolean): Promise<OAuthDecisionResponse> =>
      fetchApi(`/api/oauth/requests/${encodeURIComponent(requestId)}/decision`, {
        method: 'POST',
        body: JSON.stringify({ approve }),
      }),
    grants: (opts?: RequestOptions): Promise<OAuthGrant[]> => fetchApi('/api/oauth/grants', { signal: opts?.signal }),
    revokeGrant: (id: number): Promise<void> => fetchApi(`/api/oauth/grants/${id}`, { method: 'DELETE' }),
  },
  finance: {
    cycles: (opts?: RequestOptions): Promise<FinanceCycle[]> =>
      fetchApi('/api/finance/cycles', { signal: opts?.signal }),
    accounts: (cycleId?: number, opts?: RequestOptions): Promise<FinanceAccount[]> => {
      const params = new URLSearchParams();
      if (cycleId) params.append('cycleId', String(cycleId));
      const qs = params.toString();
      return fetchApi(`/api/finance/accounts${qs ? '?' + qs : ''}`, { signal: opts?.signal });
    },
    createAccount: (account: Partial<FinanceAccount>): Promise<FinanceAccount> =>
      fetchApi('/api/finance/accounts', { method: 'POST', body: JSON.stringify(account) }),
    updateAccount: (id: number, account: Partial<FinanceAccount>): Promise<FinanceAccount> =>
      fetchApi(`/api/finance/accounts/${id}`, { method: 'PATCH', body: JSON.stringify(account) }),
    deleteAccount: (id: number): Promise<void> =>
      fetchApi(`/api/finance/accounts/${id}`, { method: 'DELETE' }),
    autoMapAccounts: (): Promise<FinanceAccountAutoMapResponse> =>
      fetchApi('/api/finance/accounts/auto-map', { method: 'POST' }),
    transactions: (cycleId?: number, opts?: RequestOptions): Promise<FinanceTransaction[]> => {
      const params = new URLSearchParams();
      if (cycleId) params.append('cycleId', String(cycleId));
      const qs = params.toString();
      return fetchApi(`/api/finance/transactions${qs ? '?' + qs : ''}`, { signal: opts?.signal });
    },
    updateTransactionTime: (id: number, request: { time: string }): Promise<FinanceTransaction> =>
      fetchApi(`/api/finance/transactions/${id}/time`, { method: 'PATCH', body: JSON.stringify(request) }),
    deleteTransactions: (transactionIds: number[]): Promise<FinanceTransactionDeleteResponse> =>
      fetchApi('/api/finance/transactions/delete', { method: 'POST', body: JSON.stringify({ transactionIds }) }),
    previewImport: (file: File): Promise<FinanceImportPreviewResponse> => {
      const body = new FormData();
      body.append('file', file);
      return fetchApi('/api/finance/import/preview', { method: 'POST', body });
    },
    confirmImport: (request: FinanceImportConfirmRequest): Promise<FinanceImportConfirmResponse> =>
      fetchApi('/api/finance/import/confirm', { method: 'POST', body: JSON.stringify(request) }),
    recurringBills: (opts?: RequestOptions): Promise<RecurringBill[]> =>
      fetchApi('/api/finance/recurring-bills', { signal: opts?.signal }),
    createRecurringBill: (bill: Partial<RecurringBill>): Promise<RecurringBill> =>
      fetchApi('/api/finance/recurring-bills', { method: 'POST', body: JSON.stringify(bill) }),
    createRecurringBillVersion: (templateId: number, version: Partial<RecurringBillVersion>): Promise<RecurringBillVersion> =>
      fetchApi(`/api/finance/recurring-bills/${templateId}/versions`, { method: 'POST', body: JSON.stringify(version) }),
    deleteRecurringBill: (templateId: number): Promise<void> =>
      fetchApi(`/api/finance/recurring-bills/${templateId}`, { method: 'DELETE' }),
  },
};
