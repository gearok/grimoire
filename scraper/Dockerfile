# syntax=docker/dockerfile:1

# --- Build stage: compile the scraper shadow jar with the bundled Gradle wrapper ---
FROM eclipse-temurin:26-jdk-noble AS build

WORKDIR /workspace

# Copy the Gradle wrapper and build configuration first so dependency
# resolution can be cached independently of source changes.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY scraper/build.gradle.kts ./scraper/

RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true

# Copy the actual sources and build the scraper jar. The scraper depends on the
# shared model in the root module, so both source trees are required.
COPY src ./src
COPY scraper/src ./scraper/src

RUN ./gradlew --no-daemon :scraper:shadowJar

# --- Runtime stage: run the jar on a slim JRE ---
FROM eclipse-temurin:26-jre-noble

WORKDIR /app

COPY --chown=10001:10001 --from=build /workspace/scraper/build/libs/grimoire-scraper.jar scraper.jar

USER 10001:10001

ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m"

ENTRYPOINT ["java", "-jar", "/app/scraper.jar"]
