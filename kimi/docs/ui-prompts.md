# 15. v0 UI 생성 프롬프트 초안

## 15.1 전체 대시보드 프롬프트

```text
Create a modern personal insight dashboard web app for a service called Personal Insight OS.

The app analyzes personal Garmin health and activity data, stores domain nodes in a personal graph, and allows users to ask questions to their own data using LLM-based RAG.

Use a clean SaaS dashboard layout with a left sidebar.

Sidebar items:
- Dashboard
- Data Sources
- Activities
- Health Timeline
- Personal Graph
- Ask My Data
- Insights
- Goals
- Settings

Dashboard main sections:
- Today Condition card
- Sleep and Recovery summary
- Recent Activities
- Health Metrics trend
- Latest Insights
- Recommended Questions

Use shadcn/ui, Tailwind CSS, responsive layout, cards, charts, and tables.
```

## 15.2 Ask My Data 프롬프트

```text
Create an Ask My Data page for a personal health and activity insight app.

The page should allow users to ask natural language questions about their Garmin health, sleep, activity, and recovery data.

Layout:
- Large question input area
- Suggested question chips
- Answer card with conclusion
- Evidence section
- Related graph nodes section
- Confidence indicator
- Follow-up question suggestions
- Feedback buttons: Correct, Unclear, Wrong, Important

Use a clean analytical interface with shadcn/ui and Tailwind CSS.
```

## 15.3 Personal Graph 프롬프트

```text
Create a Personal Graph Explorer page for a personal insight web app.

The graph shows relationships between:
- Person
- Date
- Activity
- Sleep
- HealthMetric
- Goal
- Insight
- Question
- DataSource

Layout:
- Main graph canvas
- Left filter panel with node type, relationship type, date range, confidence
- Right detail panel for selected node or relationship
- Bottom related insights panel

Use React Flow style graph visualization, shadcn/ui, and a modern dark-friendly dashboard design.
```
