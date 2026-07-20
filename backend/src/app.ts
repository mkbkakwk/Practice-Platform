import express from "express";
import cors from "cors";
import { config } from "./config.js";
import { authRouter } from "./auth/auth.routes.js";
import { problemsRouter } from "./problems/problems.routes.js";
import { submissionsRouter } from "./submissions/submissions.routes.js";
import { usersRouter } from "./users/users.routes.js";

export function createApp() {
  const app = express();

  app.use(cors({ origin: config.corsOrigin === "*" ? true : config.corsOrigin.split(",") }));
  app.use(express.json({ limit: "1mb" }));

  app.get("/api/health", (_req, res) => res.json({ ok: true, ts: Date.now() }));

  app.use("/api/auth", authRouter);
  app.use("/api/problems", problemsRouter);
  app.use("/api/submissions", submissionsRouter);
  app.use("/api/users", usersRouter);

  // 404
  app.use((req, res) => {
    res.status(404).json({ error: `路由不存在: ${req.method} ${req.path}` });
  });

  // Error handler
  app.use((err: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
    console.error("[error]", err);
    res.status(500).json({ error: "服务器内部错误" });
  });

  return app;
}
