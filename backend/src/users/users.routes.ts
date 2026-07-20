import { Router } from "express";
import { prisma } from "../prisma.js";
import { authRequired } from "../middleware/auth.js";

export const usersRouter = Router();

// Leaderboard: top users by solvedCount
usersRouter.get("/leaderboard", async (req, res) => {
  const limit = Math.min(100, Math.max(1, parseInt(req.query.limit as string) || 20));
  const users = await prisma.user.findMany({
    orderBy: [{ solvedCount: "desc" }, { createdAt: "asc" }],
    take: limit,
    select: {
      id: true,
      username: true,
      solvedCount: true,
      createdAt: true,
    },
  });
  const ranked = users.map((u, i) => ({ rank: i + 1, ...u }));
  res.json({ leaderboard: ranked });
});

// Personal submission history
usersRouter.get("/me/submissions", authRequired, async (req, res) => {
  const page = Math.max(1, parseInt(req.query.page as string) || 1);
  const pageSize = Math.min(50, Math.max(1, parseInt(req.query.pageSize as string) || 20));

  const [total, submissions] = await Promise.all([
    prisma.submission.count({ where: { userId: req.user!.id } }),
    prisma.submission.findMany({
      where: { userId: req.user!.id },
      orderBy: { createdAt: "desc" },
      skip: (page - 1) * pageSize,
      take: pageSize,
      include: {
        problem: { select: { id: true, slug: true, title: true, difficulty: true } },
      },
    }),
  ]);

  res.json({ total, page, pageSize, submissions });
});
