import { useAuthStore } from '@/stores/authStore';
import type {
  Activity,
  ActivityFilter,
  ApiResponse,
  AskRequest,
  AskResponse,
  AuthResponse,
  DashboardSummary,
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
} from '@/types';

const API_BASE = import.meta.env.VITE_API_URL || '';

async function fetchApi<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = useAuthStore.getState().token;
  const url = `${API_BASE}${path}`;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options.headers as Record<string, string>) || {}),
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    useAuthStore.getState().logout();
    window.location.href = '/login';
    throw new Error('Unauthorized');
  }

  const data = (await response.json()) as ApiResponse<T>;

  if (!response.ok || !data.success) {
    throw new Error(data.message || `HTTP ${response.status}`);
  }

  return data.data;
}

export const api = {
  auth: {
    login: (email: string, password: string): Promise<AuthResponse> =>
      fetchApi('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
    register: (email: string, password: string, displayName?: string): Promise<AuthResponse> =>
      fetchApi('/api/auth/register', { method: 'POST', body: JSON.stringify({ email, password, displayName }) }),
    me: (): Promise<User> => fetchApi('/api/auth/me'),
  },
  dashboard: {
    summary: (): Promise<DashboardSummary> => fetchApi('/api/dashboard/summary'),
  },
  dataSources: {
    list: (): Promise<ProviderConnection[]> => fetchApi('/api/data-sources'),
    connectGarmin: (email: string, password: string): Promise<ProviderConnection> =>
      fetchApi('/api/data-sources/garmin/connect', { method: 'POST', body: JSON.stringify({ email, password }) }),
    syncGarmin: (syncType?: string, dateFrom?: string, dateTo?: string): Promise<ProviderConnection> =>
      fetchApi('/api/data-sources/garmin/sync', {
        method: 'POST',
        body: JSON.stringify({ syncType, dateFrom, dateTo }),
      }),
    getSyncLogs: (): Promise<SyncLog[]> => fetchApi('/api/data-sources/garmin/sync-logs'),
    generateMockData: (): Promise<ProviderConnection> =>
      fetchApi('/api/data-sources/garmin/mock', { method: 'POST' }),
    disconnectGarmin: (): Promise<void> => fetchApi('/api/data-sources/garmin', { method: 'DELETE' }),
  },
  activities: {
    list: (page = 0, size = 20, filter?: ActivityFilter): Promise<Page<Activity>> => {
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
      return fetchApi(`/api/activities?${params.toString()}`);
    },
    get: (id: number): Promise<Activity> => fetchApi(`/api/activities/${id}`),
    updateTag: (id: number, userTag: string): Promise<Activity> =>
      fetchApi(`/api/activities/${id}/tag`, { method: 'PATCH', body: JSON.stringify({ userTag }) }),
    createWeightTraining: (data: WeightTrainingRequest): Promise<Activity> =>
      fetchApi('/api/activities/weight', { method: 'POST', body: JSON.stringify(data) }),
    updateWeightTraining: (id: number, data: WeightTrainingRequest): Promise<Activity> =>
      fetchApi(`/api/activities/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
    deleteWeightTraining: (id: number): Promise<void> =>
      fetchApi(`/api/activities/${id}`, { method: 'DELETE' }),
    getExerciseNames: (): Promise<string[]> =>
      fetchApi('/api/activities/exercises'),
  },
  health: {
    metrics: (start: string, end: string): Promise<HealthMetric[]> =>
      fetchApi(`/api/health/metrics?start=${start}&end=${end}`),
    sleep: (start: string, end: string): Promise<Sleep[]> =>
      fetchApi(`/api/health/sleep?start=${start}&end=${end}`),
  },
  graph: {
    get: (days?: number, view?: string, raceCategory?: string): Promise<GraphData> => {
      const params = new URLSearchParams();
      if (days !== undefined) params.append('days', String(days));
      if (view) params.append('view', view);
      if (raceCategory) params.append('raceCategory', raceCategory);
      const qs = params.toString();
      return fetchApi(`/api/graph${qs ? '?' + qs : ''}`);
    },
  },
  ask: {
    ask: (question: string): Promise<AskResponse> =>
      fetchApi('/api/ask', { method: 'POST', body: JSON.stringify({ question } satisfies AskRequest) }),
  },
  insights: {
    list: (category?: string, feedbackStatus?: string): Promise<Insight[]> => {
      const params = new URLSearchParams();
      if (category) params.append('category', category);
      if (feedbackStatus) params.append('feedbackStatus', feedbackStatus);
      return fetchApi(`/api/insights?${params.toString()}`);
    },
    saved: (): Promise<Insight[]> => fetchApi('/api/insights/saved'),
    get: (id: number): Promise<Insight> => fetchApi(`/api/insights/${id}`),
    save: (id: number): Promise<Insight> => fetchApi(`/api/insights/${id}/save`, { method: 'POST' }),
    feedback: (id: number, feedbackStatus: string): Promise<Insight> =>
      fetchApi(`/api/insights/${id}/feedback`, { method: 'POST', body: JSON.stringify({ feedbackStatus }) }),
    delete: (id: number): Promise<void> => fetchApi(`/api/insights/${id}`, { method: 'DELETE' }),
  },
  goals: {
    list: (): Promise<Goal[]> => fetchApi('/api/goals'),
    create: (goal: Partial<Goal>): Promise<Goal> =>
      fetchApi('/api/goals', { method: 'POST', body: JSON.stringify(goal) }),
    get: (id: number): Promise<Goal> => fetchApi(`/api/goals/${id}`),
    update: (id: number, goal: Partial<Goal>): Promise<Goal> =>
      fetchApi(`/api/goals/${id}`, { method: 'PATCH', body: JSON.stringify(goal) }),
    delete: (id: number): Promise<void> => fetchApi(`/api/goals/${id}`, { method: 'DELETE' }),
  },
  llmProviders: {
    list: (): Promise<LlmProvider[]> => fetchApi('/api/settings/llm-providers'),
    create: (provider: Partial<LlmProvider>): Promise<LlmProvider> =>
      fetchApi('/api/settings/llm-providers', { method: 'POST', body: JSON.stringify(provider) }),
    update: (id: number, provider: Partial<LlmProvider>): Promise<LlmProvider> =>
      fetchApi(`/api/settings/llm-providers/${id}`, { method: 'PATCH', body: JSON.stringify(provider) }),
    delete: (id: number): Promise<void> => fetchApi(`/api/settings/llm-providers/${id}`, { method: 'DELETE' }),
  },
};
