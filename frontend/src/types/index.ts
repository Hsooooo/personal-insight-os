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

export interface FinanceCycle {
  id: number;
  label: string;
  salaryDate: string;
  startsAt: string;
  endsAt: string | null;
  status: string;
}

export interface FinanceTransaction {
  id: number;
  cycleId: number | null;
  linkedTemplateVersionId: number | null;
  accountId: number | null;
  accountName: string | null;
  accountType: string | null;
  accountRole: string | null;
  transactionAt: string;
  transactionDate: string;
  asset: string;
  category: string;
  subcategory: string;
  description: string;
  amount: number;
  flowType: string;
  memo: string;
  currency: string;
  sourceFingerprint: string;
  sourceRow: Record<string, unknown>;
  cashflowIncluded: boolean;
  spendingIncluded: boolean;
  cashflowAmount: number;
  spendingAmount: number;
  paymentMethod: string;
  timeAdjusted: boolean;
  timeAdjustedAt: string | null;
}

export interface FinanceAccount {
  id: number;
  name: string;
  accountType: string;
  role: string;
  institution: string | null;
  memo: string | null;
  active: boolean;
  openingBalance: number;
  openingBalanceDate: string | null;
  openingBalanceMemo: string | null;
  aliases: string[];
  cycleIncome: number;
  cycleCashOut: number;
  cycleNetFlow: number;
  estimatedBalance: number;
}

export interface FinanceAccountAutoMapResponse {
  updatedTransactions: number;
}

export interface FinanceImportRow {
  rowId: string;
  rowNumber: number;
  status: 'NEW' | 'DUPLICATE' | 'NEEDS_REVIEW';
  matchedTransactionId: number | null;
  cycleId: number | null;
  cycleLabel: string;
  cycleStartsAt: string;
  cycleSalaryDate: string;
  transactionAt: string;
  transactionDate: string;
  asset: string;
  category: string;
  subcategory: string;
  description: string;
  amount: number;
  flowType: string;
  memo: string;
  currency: string;
  sourceFingerprint: string;
  cashflowIncluded: boolean;
  spendingIncluded: boolean;
  cashflowAmount: number;
  spendingAmount: number;
  paymentMethod: string;
  sourceRow: Record<string, unknown>;
}

export interface FinanceImportPreviewResponse {
  importSessionId: string;
  totalRows: number;
  newRows: number;
  duplicateRows: number;
  reviewRows: number;
  rows: FinanceImportRow[];
}

export interface FinanceImportConfirmRequest {
  importSessionId: string;
  decisions: {
    action: 'create' | 'skip';
    row: FinanceImportRow;
  }[];
}

export interface FinanceImportConfirmResponse {
  created: number;
  skipped: number;
  transactions: FinanceTransaction[];
}

export interface RecurringBill {
  id: number;
  name: string;
  provider: string;
  category: string;
  memo: string;
  active: boolean;
  versions: RecurringBillVersion[];
}

export interface RecurringBillVersion {
  id: number;
  templateId: number;
  effectiveCycleId: number | null;
  version: number;
  expectedAmount: number;
  active: boolean;
  items: RecurringBillItem[];
}

export interface RecurringBillItem {
  id?: number;
  itemName: string;
  amount: number;
  itemType: string;
  sortOrder: number;
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
  weatherTemperature: number | null;
  weatherHumidity: number | null;
  weatherWindSpeed: number | null;
  weatherCondition: string | null;
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
  weightKg: number | null;
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

export interface EvidenceData {
  metric: string;
  currentValue: number | null;
  baselineValue: number | null;
  changeRate: number | null;
  unit: string;
  date: string | null;
  route: string | null;
}

export interface Evidence {
  id: number;
  evidenceType: string;
  sourceTable: string;
  sourceId: number;
  evidenceSummary: string;
  weight: number;
  evidenceData?: EvidenceData;
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
  weightsCount: number;
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

export interface AskPeriod {
  start: string;
  end: string;
  baselineStart: string;
  baselineEnd: string;
}

export interface AskConfidence {
  score: number;
  level: 'LOW' | 'MEDIUM' | 'HIGH';
  reasons: string[];
}

export interface AskEvidence {
  type: 'HEALTH_METRIC' | 'SLEEP' | 'ACTIVITY';
  label: string;
  observation: string;
  comparison: string;
  currentValue: number | null;
  baselineValue: number | null;
  changeRate: number | null;
  unit: string;
  sourceId: number | null;
  sourceDate: string | null;
  route: string | null;
}

export interface AskRequest {
  question: string;
}

export interface AskResponse {
  questionId: number;
  insightId: number;
  answer: string;
  intent: string;
  period: AskPeriod;
  confidence: AskConfidence;
  evidences: AskEvidence[];
  followUpQuestions: string[];
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

export interface GarminActivityLap {
  id: number;
  lapIndex: number;
  startTime: string;
  durationSeconds: number;
  distanceMeters: number;
  averagePaceSeconds: number | null;
  averageHeartRate: number | null;
  maxHeartRate: number | null;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
