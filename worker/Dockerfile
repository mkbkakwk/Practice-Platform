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
CMD ["mvn", "-B", "test"]

# ---------- build stage ----------
FROM dependencies AS build
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jdk-jammy AS runtime

COPY --from=node-runtime /usr/local/bin/node /usr/local/bin/node

# The worker executes untrusted user code: install language runtimes.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        python3 \
        g++ \
        gcc \
        bash \
        coreutils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN node --version
COPY --from=build /app/target/oj-worker.jar /app/app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENV JUDGE_WORKSPACE=/tmp/oj-judge
EXPOSE 0
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
