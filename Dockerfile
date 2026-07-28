FROM eclipse-temurin:17-jre-noble

WORKDIR /app

COPY --chown=10001:10001 build/libs/grimoire.jar app.jar

USER 10001:10001

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
