# ---------- dependency cache ----------
FROM maven:3.9-eclipse-temurin-21 AS dependencies
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# ---------- test stage ----------
FROM dependencies AS test
COPY src ./src
COPY --from=fixtures . /contracts/runner
CMD ["mvn", "-B", "test"]

# ---------- build stage ----------
FROM dependencies AS build
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- service stage ----------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 runner \
    && useradd --uid 10001 --gid runner --no-create-home --shell /usr/sbin/nologin runner
COPY --from=build --chown=runner:runner /app/target/oj-sandbox-runner.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENV SERVER_PORT=8080
EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ---------- pinned nsjail build for the Linux sandbox image ----------
FROM ubuntu:24.04 AS nsjail-build
ARG NSJAIL_REVISION=898e7e042e1bb3a10e54817d71c9eabdcc8e7089
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bison ca-certificates flex g++ gcc git make pkg-config \
        libnl-route-3-dev libprotobuf-dev protobuf-compiler \
    && rm -rf /var/lib/apt/lists/*
RUN git clone https://github.com/google/nsjail.git /src/nsjail \
    && cd /src/nsjail \
    && git checkout --detach "$NSJAIL_REVISION" \
    && test "$(git rev-parse HEAD)" = "$NSJAIL_REVISION" \
    && git submodule update --init --recursive \
    && make -j2

# Node 22 is copied from its official image into the otherwise Ubuntu 24.04
# sandbox runtime root. Student commands never select this path themselves.
FROM node:22-bookworm-slim AS node-runtime

# ---------- self-contained student runtime rootfs ----------
FROM ubuntu:24.04 AS sandbox-rootfs
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates gcc g++ openjdk-21-jdk-headless python3 \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /dev /proc /tmp /workspace /usr/local/bin
COPY --from=node-runtime /usr/local/bin/node /usr/local/bin/node
RUN ln -s /usr/local/bin/node /usr/bin/node \
    && /usr/bin/python3 --version \
    && /usr/bin/node --version \
    && /usr/bin/gcc --version \
    && /usr/bin/g++ --version \
    && /usr/bin/java -version \
    && /usr/bin/javac -version

# ---------- common Dockerized Linux sandbox runtime ----------
FROM ubuntu:24.04 AS linux-runtime-base
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates curl openjdk-21-jre-headless \
        libnl-route-3-200 libprotobuf32t64 \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app /home/runner /run/oj-sandbox-runner/jobs /run/oj-sandbox-runner/cgroup \
        /etc/oj-sandbox-runner /opt/oj-sandbox-runner/bin \
        /srv/oj-sandbox-runner/rootfs \
    && chown -R 1000:1000 /app /home/runner /run/oj-sandbox-runner
COPY --from=nsjail-build /src/nsjail/nsjail /opt/oj-sandbox-runner/bin/nsjail
COPY --from=sandbox-rootfs / /srv/oj-sandbox-runner/rootfs
COPY security/nsjail-seccomp.policy /etc/oj-sandbox-runner/nsjail-seccomp.policy
RUN chmod 0755 /opt/oj-sandbox-runner/bin/nsjail \
    && chmod 0644 /etc/oj-sandbox-runner/nsjail-seccomp.policy
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENV SERVER_PORT=8080
EXPOSE 8080

# ---------- Dockerized Runner service ----------
FROM linux-runtime-base AS linux-runtime
COPY --from=build --chown=1000:1000 /app/target/oj-sandbox-runner.jar /app/app.jar
WORKDIR /app
USER 1000:1000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# ---------- Dockerized Linux security acceptance ----------
FROM linux-runtime-base AS linux-security-test
RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --chown=1000:1000 pom.xml ./
COPY --chown=1000:1000 src ./src
COPY --from=fixtures --chown=1000:1000 . /contracts/runner
ENV RUNNER_CONTRACT_FIXTURE_DIR=/contracts/runner
USER 1000:1000
CMD ["mvn", "-B", "-Plinux-security", "verify"]
