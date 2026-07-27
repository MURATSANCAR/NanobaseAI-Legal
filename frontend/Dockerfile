FROM node:22.17.0-alpine AS build
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm run build

FROM node:22.17.0-alpine
RUN corepack enable && addgroup -S portal && adduser -S -G portal -u 10001 portal
WORKDIR /app
COPY --from=build --chown=portal:portal /app /app
USER 10001
EXPOSE 3000
CMD ["pnpm", "run", "start", "--", "--host", "0.0.0.0"]
