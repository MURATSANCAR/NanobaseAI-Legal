FROM node:22.17.0-alpine AS build
WORKDIR /app
# Pin pnpm 9 — pnpm 10/11 refuse ignored native builds by default (ERR_PNPM_IGNORED_BUILDS).
RUN npm install -g pnpm@9.15.9
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
# Workspace file is for monorepo tooling locally; breaks single-app Docker builds.
RUN rm -f pnpm-workspace.yaml
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
ARG NEXT_PUBLIC_BASE_PATH=
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
ENV NEXT_PUBLIC_BASE_PATH=${NEXT_PUBLIC_BASE_PATH}
RUN pnpm run build

FROM node:22.17.0-alpine
RUN npm install -g pnpm@9.15.9 \
  && addgroup -S portal && adduser -S -G portal -u 10001 portal
WORKDIR /app
COPY --from=build --chown=portal:portal /app /app
USER 10001
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD ["node", "-e", "fetch('http://127.0.0.1:3000/legal/').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>fetch('http://127.0.0.1:3000/').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1)))"]
CMD ["pnpm", "run", "start", "--", "--host", "0.0.0.0"]
