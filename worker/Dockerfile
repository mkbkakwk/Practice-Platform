# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre-jammy AS runtime

# The worker executes untrusted user code: install language runtimes.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        python3 \
        g++ \
        default-jdk-headless \
        bash \
        coreutils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/target/oj-worker.jar /app/app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENV JUDGE_WORKSPACE=/tmp/oj-judge
EXPOSE 0
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
