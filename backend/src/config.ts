import dotenv from "dotenv";

dotenv.config();

function env(key: string, fallback: string): string {
  const v = process.env[key];
  return v && v.length > 0 ? v : fallback;
}

export const config = {
  port: parseInt(env("PORT", "4000"), 10),
  jwtSecret: env("JWT_SECRET", "change-me-in-production-please"),
  jwtExpiresIn: env("JWT_EXPIRES_IN", "7d"),
  corsOrigin: env("CORS_ORIGIN", "*"),
  databaseUrl: env("DATABASE_URL", "postgresql://oj:oj@localhost:5432/oj?schema=public"),
  // Directory used by the judge to run untrusted code.
  judgeWorkspace: env("JUDGE_WORKSPACE", "/tmp/oj-judge"),
  // When set (e.g. "1"), the first registered user is promoted to ADMIN.
  promoteFirstAdmin: env("PROMOTE_FIRST_ADMIN", "1") === "1",
};
