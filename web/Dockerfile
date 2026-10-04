# syntax=docker/dockerfile:1
# The awardtrace/web image: Caddy with the web build and its configuration (docs 08 and 10). Build it from the
# repository root, since the build generates its API client from backend/openapi.json:
#   docker build -f web/Dockerfile .

# Static files are the same on every CPU, so they build on the build machine's own platform, even for an arm64 image.
FROM --platform=$BUILDPLATFORM node:24.21.0-alpine AS build
WORKDIR /src/web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY backend/openapi.json /src/backend/openapi.json
COPY web/ ./
RUN npm run build

FROM caddy:2.11.6-alpine
COPY infra/caddy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /src/web/dist /srv
