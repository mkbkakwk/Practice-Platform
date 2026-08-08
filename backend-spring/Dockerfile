# ---------- dependency cache ----------
FROM maven:3.9-eclipse-temurin-21 AS dependencies
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# ---------- test stage ----------
FROM dependencies AS test
COPY src ./src
CMD ["mvn", "-B", "test"]

# ---------- build stage ----------
FROM dependencies AS build
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/oj-backend.jar /app/app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENV SERVER_PORT=4000
EXPOSE 4000
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
