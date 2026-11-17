# syntax=docker/dockerfile:1

FROM eclipse-temurin:26-jre-noble

WORKDIR /app

# The application is built before Docker runs, either locally or by GitHub Actions.
COPY --chown=10001:10001 build/libs/grimoire.jar app.jar

USER 10001:10001

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
