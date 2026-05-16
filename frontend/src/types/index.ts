export interface User {
  id: number;
  email: string;
  displayName: string | null;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string | null;
  data: T;
}

export interface Activity {
  id: number;
  externalActivityId: string | null;
  sourceType: string;
  activityType: string;
  activityName: string;
  startTime: string;
  durationSeconds: number;
  distanceMeters: number | null;
  averagePaceSeconds: number | null;
  averageHeartRate: number | null;
  maxHeartRate: number | null;
  calories: number | null;
  elevationGainMeters: number | null;
  userTag: string | null;
  weightTrainingDetail: WeightTrainingDetail | null;
}

export interface WeightTrainingDetail {
  bodyPart: string;
  exercises: ExerciseDetail[];
  totalVolumeKg: number;
  totalSets: number;
  totalReps: number;
  notes?: string;
}

export interface ExerciseDetail {
  name: string;
  sets: SetDetail[];
}

export interface SetDetail {
  reps?: number;
  weightKg?: number;
  durationSeconds?: number;
}

export interface WeightTrainingRequest {
  activityName?: string;
  startTime?: string;
  durationSeconds?: number;
  averageHeartRate?: number;
  calories?: number;
  bodyPart?: string;
  exercises?: {
    name: string;
    sets: {
      reps?: number;
      weightKg?: number;
  durationSeconds?: number;
    }[];
  }[];
}

export interface HealthMetric {
  id: number;
  metricDate: string;
  restingHeartRate: number;
  hrvAvg: number;
  stressAvg: number;
  bodyBatteryMin: number;
  bodyBatteryMax: number;
  steps: number;
  caloriesTotal: number;
}

export interface Sleep {
  id: number;
  sleepDate: string;
  startTime: string;
  endTime: string;
  totalSleepSeconds: number;
  deepSleepSeconds: number;
  lightSleepSeconds: number;
  remSleepSeconds: number;
  awakeSeconds: number;
  sleepScore: number;
}

export interface Insight {
  id: number;
  category: string;
  title: string;
  summary: string;
  confidence: number;
  modelProvider: string;
  modelName: string;
  feedbackStatus: string;
  isSaved: boolean;
  createdAt: string;
  evidences: Evidence[];
}

export interface Evidence {
  id: number;
  evidenceType: string;
  sourceTable: string;
  sourceId: number;
  evidenceSummary: string;
  weight: number;
}

export interface Goal {
  id: number;
  title: string;
  goalType: string;
  description: string;
  targetValue: number;
  targetUnit: string;
  startDate: string;
  targetDate: string;
  status: string;
}

export interface ProviderConnection {
  id: number;
  providerType: string;
  connectionStatus: string;
  lastSyncedAt: string;
  syncConfig: Record<string, unknown> | null;
  dataCount: number;
}

export interface SyncLog {
  id: number;
  providerType: string;
  syncType: string;
  status: string;
  dateFrom: string | null;
  dateTo: string | null;
  activitiesCount: number;
  healthMetricsCount: number;
  sleepCount: number;
  errorMessage: string | null;
  startedAt: string;
  completedAt: string | null;
  createdAt: string;
}

export interface LlmProvider {
  id: number;
  providerName: string;
  defaultChatModel: string;
  embeddingModel: string;
  enabled: boolean;
  monthlyBudgetLimit: number;
}

export interface DashboardSummary {
  latestHealth: HealthMetric | null;
  latestSleep: Sleep | null;
  latestActivity: Activity | null;
  totalActivities: number;
  last7DaysHealth: HealthMetric[];
  last7DaysActivities: Activity[];
  recentInsights: Insight[];
  suggestedQuestions: string[];
}

export interface AskRequest {
  question: string;
}

export interface AskResponse {
  questionId: number;
  insightId: number;
  conclusion: string;
  evidenceSummary: string[];
  relatedData: string[];
  confidence: string;
  followUpQuestion: string;
}

export interface GraphNode {
  id: string;
  type: string;
  label: string;
  properties: Record<string, unknown>;
}

export interface GraphRelationship {
  id: string;
  type: string;
  sourceId: string;
  targetId: string;
  confidence: number;
  sourceType: string;
  properties: Record<string, unknown>;
}

export interface GraphData {
  nodes: GraphNode[];
  relationships: GraphRelationship[];
}

export interface ActivityFilter {
  activityType?: string;
  userTag?: string;
  activityName?: string;
  startTimeFrom?: string;
  startTimeTo?: string;
  minDistance?: string;
  maxDistance?: string;
  sortBy?: string;
  sortDir?: string;
}

export interface ApiKey {
  id: number;
  name: string;
  key?: string;
  lastUsedAt: string | null;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
