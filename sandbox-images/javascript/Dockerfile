FROM gcc:13-bookworm AS helper
COPY sandbox-images/common/sandbox-exec.c /src/sandbox-exec.c
RUN gcc -static -Os -s -o /sandbox-exec /src/sandbox-exec.c

FROM node:22-bookworm-slim

RUN groupadd --gid 10001 sandbox \
    && useradd --uid 10001 --gid sandbox --no-create-home --shell /usr/sbin/nologin sandbox \
    && mkdir -p /workspace \
    && chown 10001:10001 /workspace

WORKDIR /workspace
COPY --from=helper --chown=10001:10001 --chmod=0555 /sandbox-exec /usr/local/bin/sandbox-exec
USER 10001:10001
CMD ["/usr/bin/tail", "-f", "/dev/null"]
