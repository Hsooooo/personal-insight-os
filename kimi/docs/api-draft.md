# 12. API 초안

## 12.1 Data Sources API

```text
GET    /api/data-sources
POST   /api/data-sources/garmin/connect
POST   /api/data-sources/garmin/sync
GET    /api/data-sources/garmin/sync-logs
DELETE /api/data-sources/garmin
```

## 12.2 Activities API

```text
GET /api/activities
GET /api/activities/{activityId}
GET /api/activities/{activityId}/insights
POST /api/activities/{activityId}/analyze
```

## 12.3 Health API

```text
GET /api/health/timeline
GET /api/health/sleep
GET /api/health/metrics
GET /api/health/summary
```

## 12.4 Graph API

```text
GET /api/graph
GET /api/graph/nodes/{nodeId}
GET /api/graph/nodes/{nodeId}/neighbors
GET /api/graph/relationships/{relationshipId}
```

## 12.5 Ask API

```text
POST /api/ask
GET  /api/questions
GET  /api/questions/{questionId}
```

## 12.6 Insights API

```text
GET   /api/insights
GET   /api/insights/{insightId}
POST  /api/insights/{insightId}/save
POST  /api/insights/{insightId}/feedback
DELETE /api/insights/{insightId}
```

## 12.7 Goals API

```text
GET    /api/goals
POST   /api/goals
GET    /api/goals/{goalId}
PATCH  /api/goals/{goalId}
DELETE /api/goals/{goalId}
GET    /api/goals/{goalId}/insights
```

## 12.8 LLM Provider API

```text
GET    /api/settings/llm-providers
POST   /api/settings/llm-providers
PATCH  /api/settings/llm-providers/{providerId}
DELETE /api/settings/llm-providers/{providerId}
POST   /api/settings/llm-providers/{providerId}/test
```
