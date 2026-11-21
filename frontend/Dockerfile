FROM node:26.13.1-alpine AS builder
WORKDIR /app
# lock 파일 기준으로 설치해 빌드마다 의존성 버전이 달라지지 않게 한다
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# 비root nginx (포트 8080)
FROM nginxinc/nginx-unprivileged:1.31.7-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
