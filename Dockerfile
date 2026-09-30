# Single image serving the API and the web GUI (DEP-1).

FROM node:22-alpine AS web
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS server
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY shared shared
COPY server server
RUN ./gradlew --no-daemon :server:buildFatJar

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home calorie \
    && mkdir /data && chown calorie /data
WORKDIR /app
COPY --from=server /src/server/build/libs/calorie-companion-server.jar app.jar
COPY --from=web /web/dist web
USER calorie
ENV CC_PORT=8080 \
    CC_DATA_DIR=/data \
    CC_WEB_DIR=/app/web
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
