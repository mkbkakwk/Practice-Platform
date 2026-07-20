import { Router } from "express";
import { z } from "zod";
import { prisma } from "../prisma.js";
import { authRequired } from "../middleware/auth.js";
import { judge } from "../judge/judge.service.js";
import { LANGUAGES } from "../judge/languages.js";

export const submissionsRouter = Router();

// Language list + templates (declared before /:id to avoid param capture)
submissionsRouter.get("/meta/languages", async (_req, res) => {
  const langs = Object.values(LANGUAGES).map((l) => ({
    id: l.id,
    name: l.name,
    ext: l.ext,
    template: l.template,
  }));
  res.json({ languages: langs });
});

const submitSchema = z.object({
  problemId: z.number().int().positive(),
  language: z.string().min(1).max(20),
  code: z.string().min(1).max(50000),
});

// Submit code for judging
submissionsRouter.post("/", authRequired, async (req, res) => {
  const parsed = submitSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.errors[0].message });
    return;
  }
  const { problemId, language, code } = parsed.data;

  if (!LANGUAGES[language]) {
    res.status(400).json({ error: `不支持的语言: ${language}` });
    return;
  }

  const problem = await prisma.problem.findUnique({
    where: { id: problemId },
  });
  if (!problem || !problem.visible) {
    res.status(404).json({ error: "题目不存在" });
    return;
  }

  // Read test cases (stored as JSON array of {input,output})
  const testCases = (problem.testCases as { input: string; output: string }[]) || [];

  const result = await judge({
    language,
    code,
    timeLimitMs: problem.timeLimit,
    memoryLimitKb: problem.memoryLimit * 1024,
    testCases,
  });

  const submission = await prisma.submission.create({
    data: {
      userId: req.user!.id,
      problemId,
      language,
      code,
      verdict: result.verdict,
      timeMs: result.timeMs,
      memoryKb: result.memoryKb,
      message: result.message,
      passed: result.passed,
      total: result.total,
    },
  });

  // On first AC for this user/problem, bump solvedCount.
  if (result.verdict === "AC") {
    const alreadySolved = await prisma.submission.findFirst({
      where: {
        userId: req.user!.id,
        problemId,
        verdict: "AC",
        id: { not: submission.id },
      },
      select: { id: true },
    });
    if (!alreadySolved) {
      await prisma.user.update({
        where: { id: req.user!.id },
        data: { solvedCount: { increment: 1 } },
      });
    }
  }

  res.status(201).json({
    submission: {
      id: submission.id,
      verdict: submission.verdict,
      timeMs: submission.timeMs,
      memoryKb: submission.memoryKb,
      message: submission.message,
      passed: submission.passed,
      total: submission.total,
      createdAt: submission.createdAt,
    },
    detail:
      result.verdict === "AC"
        ? undefined
        : {
            failedCase: result.failedCase,
            input: result.failedInput,
            expected: result.failedExpected,
            actual: result.failedActual,
          },
  });
});

// Get a single submission (own, or admin can see any)
submissionsRouter.get("/:id", authRequired, async (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (Number.isNaN(id)) {
    res.status(400).json({ error: "无效的提交 ID" });
    return;
  }
  const submission = await prisma.submission.findUnique({
    where: { id },
    include: { problem: { select: { slug: true, title: true } } },
  });
  if (!submission) {
    res.status(404).json({ error: "提交不存在" });
    return;
  }
  if (submission.userId !== req.user!.id && req.user!.role !== "ADMIN") {
    res.status(403).json({ error: "无权查看该提交" });
    return;
  }
  res.json({ submission });
});

// Recent submissions feed (public)
submissionsRouter.get("/", async (req, res) => {
  const page = Math.max(1, parseInt(req.query.page as string) || 1);
  const pageSize = Math.min(50, Math.max(1, parseInt(req.query.pageSize as string) || 20));
  const problemId = req.query.problemId ? parseInt(req.query.problemId as string) : undefined;

  const where = problemId ? { problemId } : {};
  const [total, submissions] = await Promise.all([
    prisma.submission.count({ where }),
    prisma.submission.findMany({
      where,
      orderBy: { createdAt: "desc" },
      skip: (page - 1) * pageSize,
      take: pageSize,
      include: {
        problem: { select: { id: true, slug: true, title: true, difficulty: true } },
        user: { select: { id: true, username: true } },
      },
    }),
  ]);

  res.json({ total, page, pageSize, submissions });
});

