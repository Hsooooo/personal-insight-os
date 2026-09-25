FROM node:22-alpine AS builder
WORKDIR /app
# lock 파일 기준으로 설치해 빌드마다 의존성 버전이 달라지지 않게 한다
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
