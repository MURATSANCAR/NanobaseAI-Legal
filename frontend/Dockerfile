FROM node:22.17.0-alpine AS build
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
ARG NEXT_PUBLIC_OIDC_ISSUER=http://localhost:8081/realms/specai
ARG NEXT_PUBLIC_OIDC_CLIENT_ID=specai-portal
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
ENV NEXT_PUBLIC_OIDC_ISSUER=${NEXT_PUBLIC_OIDC_ISSUER}
ENV NEXT_PUBLIC_OIDC_CLIENT_ID=${NEXT_PUBLIC_OIDC_CLIENT_ID}
RUN pnpm run build

FROM node:22.17.0-alpine
RUN corepack enable && addgroup -S portal && adduser -S -G portal -u 10001 portal
WORKDIR /app
COPY --from=build --chown=portal:portal /app /app
USER 10001
EXPOSE 3000
CMD ["pnpm", "run", "start", "--", "--host", "0.0.0.0"]
