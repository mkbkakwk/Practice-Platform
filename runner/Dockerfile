# ---------- dependency cache ----------
FROM maven:3.9-eclipse-temurin-21 AS dependencies
WORKDIR /app
COPY pom.xml ./
RUN mvn -B dependency:go-offline

# ---------- test stage ----------
FROM dependencies AS test
COPY src ./src
CMD ["mvn", "-B", "test"]

# ---------- build stage ----------
FROM dependencies AS build
COPY src ./src
RUN mvn -B package -DskipTests

# ---------- service stage ----------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl grep \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 runner \
    && useradd --uid 10001 --gid runner --no-create-home --shell /usr/sbin/nologin runner
COPY --from=build --chown=runner:runner /app/target/oj-sandbox-runner.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENV SERVER_PORT=8080
EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
