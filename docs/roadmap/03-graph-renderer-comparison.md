# 구상: 관계망 그래프 뷰 렌더링 라이브러리 변경 검토

> 현재: `@xyflow/react` (React Flow) → 대안 비교

---

## 1. 현재 상태

| 항목 | 내용 |
|------|------|
| **현재 라이브러리** | `@xyflow/react` (React Flow v12) |
| **장점** | React 네이티브, 인터랙티브(드래그/줌), MiniMap/Controls 내장 |
| **단점** | 무료 버전은 기본 기능만, 대규모 그래프(1000+ 노드) 성능 이슈, 레이아웃 자동 계산 없음 |

---

## 2. 대안 라이브러리 비교

### A. 유지: React Flow + dagre 레이아웃

```
라이브러리: @xyflow/react + dagre
장점: 추가 학습 없음, dagre로 자동 레이아웃 가능
단점: 여전히 대규모 성능 이슈
적합: 지금 상태 유지, 단순 그래프
```

```typescript
// dagre 자동 레이아웃 예시
import Dagre from '@dagrejs/dagre';

function getLayoutedElements(nodes, edges) {
    const g = new Dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));
    g.setGraph({ rankdir: 'TB' });
    nodes.forEach((n) => g.setNode(n.id, { width: 150, height: 40 }));
    edges.forEach((e) => g.setEdge(e.source, e.target));
    Dagre.layout(g);
    // g.node(v).x, .y 로 위치 설정
}
```

### B. Cytoscape.js

```
라이브러리: cytoscape + react-cytoscapejs
장점: 대규모 그래프(만 개+)도 가능, 다양한 레이아웃 알고리즘 내장,
      성능이 React Flow보다 월등히 좋음, 무료
단점: React 친화적이지 않음 (ref 기반), 커스텀 노드 UI 어려움,
      학습 곡선 있음
적합: 데이터 많아질 때, 복잡한 그래프 분석
```

```javascript
// Cytoscape 기본 사용
var cy = cytoscape({
  container: document.getElementById('cy'),
  elements: [
    { data: { id: 'a', label: 'Person' } },
    { data: { id: 'b', label: 'Activity' } },
    { data: { id: 'ab', source: 'a', target: 'b' } }
  ],
  layout: { name: 'cose' }  // Compound Spring Embedder
});
```

### C. D3.js + Force Simulation

```
라이브러리: d3
장점: 완전한 자유도, force simulation이 자연스러움,
      대규모 가능, 커스텀 시각화 무한대
단점: 학습 곡선 매우 가파름, 보일러플레이트 많음,
      React와 통합 번거로움
적합: 시각화 전문가, 완전 커스텀 UI 필요 시
```

### D. vis-network / vis.js

```
라이브러리: vis-network
장점: 사용이 매우 쉬움, 물리 엔진 내장, 대규모 가능,
      클러스터링/필터링 내장
단점: React 통합 패키지가 불안정, 커스텀 노드 한정적,
      최신 업데이트 느림
적합: 빠르게 대규모 그래프를 띄우고 싶을 때
```

### E. Sigma.js

```
라이브러리: sigma.js
장점: WebGL 기반으로 성능 최강, 10만 노드도 60fps,
      React 통합(sigma-react) 있음
단점: 노드 커스텀 UI 제한적 (Canvas/WebGL), 상호작용 설정 복잡,
      학습 곡선 있음
적합: 성능이 최우선, 노드 수가 매우 많을 때
```

---

## 3. 비교표

| 기준 | React Flow | Cytoscape.js | D3.js | vis-network | Sigma.js |
|------|:----------:|:------------:|:-----:|:-----------:|:--------:|
| **React 친화도** | ⭐⭐⭐ | ⭐⭐ | ⭐ | ⭐⭐ | ⭐⭐ |
| **대규모 성능** | ⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **커스텀 노드 UI** | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐ |
| **자동 레이아웃** | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| **학습 곡선** | ⭐⭐⭐ (낮음) | ⭐⭐ (중간) | ⭐ (높음) | ⭐⭐⭐ (낮음) | ⭐⭐ (중간) |
| **무료** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **현재 적합도** | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ |

---

## 4. 추천 전략

### 단기 (지금 ~ 3개월)

**React Flow 유지 + dagre 레이아웃 추가**

```
이유:
- 이미 구현되어 있음 (마이그레이션 비용 0)
- MVP 단계에서는 노드 수가 수백 개 내외
- dagre 하나만 추가하면 레이아웃 문제 해결
```

**적용 방법:**
```bash
npm install @dagrejs/dagre
```

```typescript
// Graph.tsx에 추가
import Dagre from '@dagrejs/dagre';

// API 응답 받은 후 dagre로 레이아웃 계산
const layouted = getLayoutedElements(data.nodes, data.relationships);
setNodes(layouted.nodes);
setEdges(layouted.edges);
```

### 중기 (3~6개월 후)

**Cytoscape.js 마이그레이션 검토**

```
트리거:
- 사용자당 노드 수가 500개 이상으로 증가
- React Flow에서 뚝뚝 끊기는 느낌
- 더 복잡한 그래프 분석 (커뮤니티 탐지, 최단 경로 등) 필요
```

### 장기 (6개월+)

**Sigma.js 고려**

```
트리거:
- 사용자당 노드 수가 10,000개 이상
- 실시간 그래프 업데이트 필요
- WebGL 기반 성능이 필수
```

---

## 5. 당장 할 일 (30분)

| 작업 | 시간 |
|------|------|
| dagre 설치 | 1분 |
| `getLayoutedElements` 유틸 함수 작성 | 10분 |
| `Graph.tsx`에 적용 | 15분 |
| 테스트 | 5분 |

**예상 결과:**
```
Before: 노드들이 랜덤하게 흩어져 있음
After:  계층 구조로 자동 정렬됨
         Person
           ├── Activity
           ├── Sleep
           └── HealthMetric
```

---

## 6. 결론

| 시점 | 결정 |
|------|------|
| **지금** | React Flow 유지 + dagre 자동 레이아웃 추가 (30분) |
| **노드 500개+** | Cytoscape.js 마이그레이션 검토 |
| **노드 10,000개+** | Sigma.js 검토 |

> **핵심 인사이트:** 지금 라이브러리를 바꾸는 것은 오버엔지니어링. dagre 레이아웃 하나 추가로 80% 만족도 상승.
