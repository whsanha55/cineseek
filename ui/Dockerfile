# 빌드 단계
FROM node:22-alpine AS build
WORKDIR /workspace
COPY package.json pnpm-lock.yaml ./
# minimum-release-age=0 — lockfile의 transitive dep(electron-to-chromium 등)가 당일 배포라서 공급망 정책에 걸리는 것을 허용
RUN corepack enable && npm_config_minimum_release_age=0 pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# 실행 단계 — nginx 정적 서빙 + /api 프록시
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
