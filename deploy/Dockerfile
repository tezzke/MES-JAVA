# Linux amd64 离线交付的两个最终 target：app-runtime 与 nginx-runtime。
# 目标机只 docker load，不会执行本 Dockerfile。
FROM node:20.19.5-alpine3.22@sha256:6178e78b972f79c335df281f4b7674a2d85071aae2af020ffa39f0a770265435 AS frontend-build
WORKDIR /app
COPY frontend/package*.json ./
COPY frontend/scripts/verify-lock.mjs ./scripts/verify-lock.mjs
RUN npm run verify:lock && npm ci
COPY frontend/ ./
RUN npm run test && npm run build

FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS backend-build
WORKDIR /src
COPY backend/pom.xml ./
COPY backend/mes-core/pom.xml mes-core/
COPY backend/mes-acquisition/pom.xml mes-acquisition/
COPY backend/mes-infrastructure/pom.xml mes-infrastructure/
COPY backend/mes-system/pom.xml mes-system/
COPY backend/mes-production/pom.xml mes-production/
COPY backend/mes-api/pom.xml mes-api/
RUN mvn -B dependency:go-offline
COPY backend/ ./
COPY --from=frontend-build /app/dist ./mes-api/src/main/resources/static
RUN mvn -B package

FROM eclipse-temurin:21.0.8_9-jre-jammy@sha256:db1689535962d757a5adabf57387584ed543d38c0b9d1fe870123ea362ad73b0 AS app-runtime
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl sqlite3 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 mes \
    && useradd --system --uid 10001 --gid mes --home-dir /app --shell /usr/sbin/nologin mes \
    && mkdir -p /app/data /app/config /app/backups \
    && chown -R mes:mes /app
COPY --from=backend-build /src/mes-api/target/mes-api.jar ./mes-api.jar
RUN chown -R mes:mes /app
USER mes
EXPOSE 5100 6001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "mes-api.jar"]

FROM nginx:1.28.0-alpine@sha256:30f1c0d78e0ad60901648be663a710bdadf19e4c10ac6782c235200619158284 AS nginx-runtime
COPY deploy/nginx.offline.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-build /app/dist /usr/share/nginx/html
EXPOSE 80
