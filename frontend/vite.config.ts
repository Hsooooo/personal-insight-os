/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    // cytoscape(+fcose)는 Graph 화면 진입 시에만 지연 로드되는 단일 라이브러리라 경고 한도를 조정
    chunkSizeWarningLimit: 600,
    rolldownOptions: {
      output: {
        // 자주 바뀌지 않는 vendor를 묶어 캐시 효율을 높인다.
        // 라우트 지연 로딩과 함께 쓰이므로 차트/그래프 vendor는 해당 페이지 진입 시에만 로드된다.
        codeSplitting: {
          groups: [
            // 앱 셸과 차트가 함께 쓰는 작은 유틸(clsx 등)이 차트 청크로 끌려가 초기 로드에 포함되지 않도록 먼저 분리
            { name: 'vendor-ui', test: /[\\/]node_modules[\\/](clsx|tailwind-merge|class-variance-authority|sonner|zustand|lucide-react)[\\/]/, priority: 35 },
            { name: 'vendor-react', test: /[\\/]node_modules[\\/](react|react-dom|react-router|react-router-dom|scheduler)[\\/]/, priority: 30 },
            { name: 'vendor-query', test: /[\\/]node_modules[\\/]@tanstack[\\/]/, priority: 25 },
            { name: 'vendor-radix', test: /[\\/]node_modules[\\/](@radix-ui|@floating-ui|react-remove-scroll|react-remove-scroll-bar|react-style-singleton|use-callback-ref|use-sidecar|aria-hidden)[\\/]/, priority: 20 },
            { name: 'vendor-charts', test: /[\\/]node_modules[\\/](recharts|d3-[^\\/]+|victory-vendor|@reduxjs|redux|react-redux|reselect|immer|es-toolkit|decimal\.js-light)[\\/]/, priority: 20 },
            { name: 'vendor-cytoscape', test: /[\\/]node_modules[\\/](cytoscape|cytoscape-fcose|cose-base|layout-base)[\\/]/, priority: 20 },
            { name: 'vendor-markdown', test: /[\\/]node_modules[\\/](react-markdown|remark-[^\\/]+|micromark[^\\/]*|mdast-[^\\/]+|hast-[^\\/]+|unist-[^\\/]+|unified|vfile[^\\/]*|property-information|space-separated-tokens|comma-separated-tokens|html-url-attributes|decode-named-character-reference|character-entities[^\\/]*|devlop|bail|trough|is-plain-obj|ccount|escape-string-regexp|markdown-table|longest-streak|zwitch|trim-lines|estree-util-[^\\/]+|style-to-js|style-to-object|inline-style-parser)[\\/]/, priority: 20 },
          ],
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: false,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    restoreMocks: true,
  },
});
