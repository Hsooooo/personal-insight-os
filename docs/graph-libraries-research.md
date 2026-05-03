# 묻 그래프 시각화 라이브러리 조사

> 작성일: 2026-05-02
> 목적: PIOS 프로젝트의 그래프 뷰 라이브러리 교체/개선 검토를 위한 묻 라이브러리 샘플 페이지 모음

---

## 1. Cytoscape.js (현재 사용 중)

> Canvas 기반, force-directed 레이아웃, 가장 성숙한 네트워크 전용 라이브러리

- 공식 홈: https://js.cytoscape.org/
- 데모 갤러리: https://js.cytoscape.org/demos
- fcose 레이아웃 데모: https://ivis-at-bilkent.github.io/cytoscape.js-fcose/demo.html
- GitHub: https://github.com/cytoscape/cytoscape.js
- 라이선스: MIT

**장점**: 성숙, 확장성, 성능, 다양한 레이아웃 플러그인 (fcose, cola, dagre 등)
**단점**: DOM 스타일링 어려움 (Canvas 기반), React 통합 시 래퍼 필요

---

## 2. React Flow (@xyflow/react)

> DOM 기반, React 컴포넌트 노드, dagre/col 레이아웃 가능

- 공식 홈: https://reactflow.dev/
- 예시 갤러리: https://reactflow.dev/examples
- Custom Nodes 예시: https://reactflow.dev/examples/nodes/custom-node
- GitHub: https://github.com/xyflow/xyflow
- 라이선스: MIT (Pro 플랜 있음, 핵심 기능은 묻)

**장점**: shadcn/ui와 완벽 통합, React 네이티브, 커스텀 노드/엣지 용이
**단점**: 대규모 노드(1000+) 시 성능 저하, 레이아웃 엔진 별도 설치 필요

---

## 3. Sigma.js (v2)

> WebGL 렌더링, 10K+ 노드 60fps, React 지원

- 공식 홈: https://www.sigmajs.org/
- 인터랙티브 데모: https://www.sigmajs.org/demo/
- GitHub 예시: https://github.com/jacomyal/sigma.js/tree/main/packages/demo
- GitHub: https://github.com/jacomyal/sigma.js
- 라이선스: MIT

**장점**: WebGL로 대규모 그래프 고성능, React 공식 지원, 시각적으로 깔끔
**단점**: 커스텀 노드 모양 제한적, Cytoscape보다 생태계 작음

---

## 4. D3.js (d3-force)

> SVG/Canvas 직접 제어, 완전한 자유도, trend-frame-reader-web에서 사용

- 공식 홈: https://d3js.org/
- Force Simulation 예시: https://observablehq.com/collection/@d3/d3-force
- 기본 force-directed: https://observablehq.com/@d3/force-directed-graph
- GitHub: https://github.com/d3/d3
- 라이선스: ISC

**장점**: 완전한 자유도, 모든 시각적 요소 직접 제어, 대규모 생태계
**단점**: 러닝 커브 가파름, 보일러플레이트 많음, 직접 렌더링 코드 작성 필요

---

## 5. vis-network (vis.js)

> Canvas 기반, 물리 엔진 내장, 사용법 매우 간단

- 공식 홈: https://visjs.github.io/vis-network/
- 예시 갤러리: https://visjs.github.io/vis-network/examples/
- 기본 네트워크: https://visjs.github.io/vis-network/examples/network/basicUsage.html
- GitHub: https://github.com/visjs/vis-network
- 라이선스: Apache-2.0 / MIT

**장점**: 사용법 매우 간단, 내장 물리 엔진, 빠른 프로토타이핑
**단점**: 커스텀 제한적, 다소 구식 UI, React 통합 불편

---

## 6. G6 (AntV)

> Alibaba出品, Canvas/SVG, 풍부한 내장 레이아웃

- 공식 홈: https://g6.antv.antgroup.com/
- 예시 갤러리: https://g6.antv.antgroup.com/examples
- Force-directed: https://g6.antv.antgroup.com/examples#layout-force
- GitHub: https://github.com/antvis/G6
- 라이선스: MIT

**장점**: 10+ 내장 레이아웃, 기능 풍부, 중국 대기업 메인테인
**단점**: 영문 문서 부족, API 복잡함, React 통합 시 추가 작업

---

## 7. react-force-graph (2D/3D/VR)

> Three.js/WebGL 기반, 3D force-directed 지원

- 공식 홈: https://github.com/vasturiano/react-force-graph
- 2D 데모: https://vasturiano.github.io/react-force-graph/example/large-graph/
- 3D 데모: https://vasturiano.github.io/react-force-graph/example/3d-directional-particles/
- GitHub: https://github.com/vasturiano/react-force-graph
- 라이선스: MIT

**장점**: 3D/VR 지원, React 네이티브, 시각적 임팩트 큼
**단점**: 번들 사이즈 큼 (Three.js 의존), 2D는 과할 수 있음

---

## 8. Apache ECharts (graph)

> Canvas 기반, 대규모 데이터에 강함, 다양한 내장 레이아웃

- 공식 홈: https://echarts.apache.org/
- Graph 예시: https://echarts.apache.org/examples/en/index.html#chart-type-graph
- Force Layout: https://echarts.apache.org/examples/en/editor.html?c=graph-force
- GitHub: https://github.com/apache/echarts
- 라이선스: Apache-2.0

**장점**: 대규모 데이터 최적화, 다양한 차트 타입, 중국/글로벌 모두 인기
**단점**: 그래프는 부가 기능, 네트워크 전용 기능은 Cytoscape/Sigma에 비해 약함

---

## 9. VivaGraphJS

> WebGL/SVG 렌더링, 다양한 레이아웃 엔진, 가벼움

- GitHub: https://github.com/anvaka/VivaGraphJS
- 데모: https://anvaka.github.io/VivaGraphJS/demos/
- WebGL 기본: https://anvaka.github.io/VivaGraphJS/demos/tutorial_svg/01.basic.html
- 라이선스: BSD-3-Clause

**장점**: 가벼움, 빠름, 다양한 렌더링 엔진
**단점**: 최근 업데이트 적음, React 통합 불편

---

## 10. Reagraph (React + WebGL)

> React 전용, Three.js 기반, 3D/2D 모두 지원

- GitHub: https://github.com/reaviz/reagraph
- 스토리북 데모: https://reagraph.dev/
- 기본 그래프: https://reagraph.dev/?path=/story/demos-basic--simple
- 라이선스: Apache-2.0

**장점**: React 네이티브, WebGL 성능, Three.js 기반
**단점**: 상대적으로 신생 라이브러리, 생태계 작음

---

## 11. Unovis

> React/D3 기반, 다양한 차트 + 그래프, 가벼움

- 공식 홈: https://unovis.dev/
- 갤러리: https://unovis.dev/gallery
- GitHub: https://github.com/f5/unovis
- 라이선스: Apache-2.0

**장점**: 가벼움, React 지원, 차트+그래프 통합
**단점**: 그래프는 부가 기능, 네트워크 전용 기능 제한적

---

## 12. ngraph + ngraph.pixel

> force layout 엔진 + WebGL 렌더링, 대규모 그래프용

- GitHub: https://github.com/anvaka/ngraph
- 패키지 매니저 의존성 그래프: https://anvaka.github.io/npmgraph.an/
- 3D 예시: https://anvaka.github.io/ngraph.pixel/demo/
- 라이선스: MIT

**장점**: 모듈화, 다양한 렌더링 옵션, npm 의존성 그래프 등 실전 사례 많음
**단점**: 조합형 라이브러리, 초기 설정 복잡

---

## 13. ccNetViz (WebGL)

> WebGL 기반, 대규모 네트워크 시각화

- GitHub: https://github.com/HelikarLab/ccNetViz
- 예시: https://helikarlab.github.io/ccNetViz/
- 라이선스: GPL-3.0

**장점**: WebGL 고성능, 대규모 네트워크 특화
**단점**: 메인테인드 활발하지 않음, React 통합 불편

---

## 핵심 비교표

| 라이브러리 | 렌더링 | React 지원 | 레이아웃 | 강점 | 단점 |
|-----------|--------|-----------|---------|------|------|
| **Cytoscape.js** | Canvas | 별도 래퍼 | fcose, cola, dagre 등 | 성숙, 확장성, 성능 | DOM 스타일링 어려움 |
| **React Flow** | DOM | 네이티브 | dagre, elk 외부 필요 | shadcn 완벽 통합 | 대규모 노드 시 성능↓ |
| **Sigma.js** | WebGL | 네이티브 | forceAtlas2 | 10K+ 노드 60fps | 커스텀 노드 제한적 |
| **D3.js** | SVG/Canvas | useRef 직접 | d3-force 완전 제어 | 완전한 자유도 | 러닝커브, 보일러플레이트 |
| **vis-network** | Canvas | 별도 래퍼 | 내장 물리 엔진 | 사용법 매우 간단 | 커스텀 제한적, 구식 |
| **G6** | Canvas/SVG | 네이티브 | 10+ 내장 레이아웃 | 기능 풍부 | 영문 문서 부족 |
| **react-force-graph** | Three.js/WebGL | 네이티브 | d3-force | 3D/VR 지원 | 번들 사이즈 큼 |
| **VivaGraphJS** | WebGL/SVG | 별도 래퍼 | 다양한 엔진 | 가벼움, 빠름 | 업데이트 적음 |

---

## 검토 결정사항

### 현재 상태 (2026-05-02)
- **사용 중**: Cytoscape.js + fcose 레이아웃
- **최근 변경**: 노드 원형 통일, 단색 fill, subtle 그림자, 컨테이너 내 오버레이 UI

### 고려 중인 옵션
1. **Cytoscape.js 유지 + 디자인 개선** (현재 진행 중)
2. **Sigma.js 교체** — WebGL 성능 + React 네이티브 지원
3. **D3.js 직접 구현** — trend-frame-reader-web 스타일 완벽 재현

### 다음 단계
- [ ] Sigma.js 데모로 대규모 데이터 성능 확인
- [ ] React Flow로 shadcn 통합 디자인 프로토타입 (시간 여유 시)
- [ ] D3.js 마이그레이션 비용 산정
