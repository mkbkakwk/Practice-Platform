# ---------- build stage ----------
FROM node:20-bookworm-slim AS build
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

COPY prisma ./prisma
RUN npx prisma generate

COPY tsconfig.json ./
COPY src ./src
RUN npx tsc -p tsconfig.json

# ---------- runtime stage ----------
FROM node:20-bookworm-slim AS runtime
WORKDIR /app

# Language runtimes required by the judge: Python 3, C/C++ (g++), Java (JDK).
# git is needed by some npm post-install steps; ca-certificates for HTTPS.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        python3 \
        g++ \
        default-jdk-headless \
        ca-certificates \
        bash \
        coreutils \
    && rm -rf /var/lib/apt/lists/*

# Bring the built artefacts and full node_modules (includes prisma CLI used at
# startup to apply the schema).
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
COPY --from=build /app/prisma ./prisma
COPY package.json ./

ENV NODE_ENV=production
ENV PORT=4000
ENV JUDGE_WORKSPACE=/tmp/oj-judge
ENV DATABASE_URL=postgresql://oj:oj@db:5432/oj?schema=public

EXPOSE 4000

# Apply the schema (idempotent) then start the server.
CMD ["sh", "-c", "npx prisma db push --accept-data-loss --skip-generate && node dist/index.js"]
