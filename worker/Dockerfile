ARG NODE_VERSION=22.22.3
FROM node:${NODE_VERSION}-bookworm-slim AS node-runtime

# ---------- dependency cache ----------
FROM maven:3.9-eclipse-temurin-21 AS dependencies
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# ---------- test stage ----------
FROM dependencies AS test
COPY --from=node-runtime /usr/local/bin/node /usr/local/bin/node
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        python3 \
        gcc \
        g++ \
        bash \
        coreutils \
    && rm -rf /var/lib/apt/lists/* \
    && ln -sf /opt/java/openjdk/bin/java /usr/local/bin/java \
    && ln -sf /opt/java/openjdk/bin/javac /usr/local/bin/javac
RUN node --version
COPY src ./src
COPY --from=fixtures . /contracts/runner
CMD ["mvn", "-B", "test"]

# ---------- build stage ----------
FROM dependencies AS build
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- runtime stage ----------
# The production Worker is a trusted queue consumer and judge coordinator. It
# never executes student programs; language runtimes live only in the disposable
# sandbox images controlled by the Runner.
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
COPY --from=build /app/target/oj-worker.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENV JUDGE_WORKSPACE=/tmp/oj-judge
EXPOSE 0
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
