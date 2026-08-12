FROM eclipse-temurin:21-jdk-jammy

RUN groupadd --gid 10001 sandbox \
    && useradd --uid 10001 --gid sandbox --no-create-home --shell /usr/sbin/nologin sandbox \
    && mkdir -p /workspace \
    && chown 10001:10001 /workspace

WORKDIR /workspace
USER 10001:10001
CMD ["/usr/bin/tail", "-f", "/dev/null"]
