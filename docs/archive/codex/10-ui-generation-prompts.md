# UI 생성 프롬프트

## 문서 목적

v0 또는 유사한 UI 생성 도구에 사용할 수 있는 프롬프트를 정리합니다. MVP 첫 화면 구현은 실제 사용 가능한 대시보드 경험을 우선해야 합니다.

## 전체 대시보드 프롬프트

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

## Ask My Data 프롬프트

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

## Personal Graph 프롬프트

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

## 추가 UI 생성 지침

```text
Do not create a marketing landing page as the first screen.
The first screen should be the actual dashboard experience.
Use dense but readable SaaS dashboard composition.
Show realistic empty, loading, connected, disconnected, and error states.
Health data should be presented as evidence, not diagnosis.
Ask My Data answers should always show supporting evidence.
Graph relationships should expose confidence and source.
```

