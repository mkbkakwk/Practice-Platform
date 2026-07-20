import { Router } from "express";
import { z } from "zod";
import type { Prisma, Difficulty } from "@prisma/client";
import { prisma } from "../prisma.js";
import { adminRequired, authRequired } from "../middleware/auth.js";

export const problemsRouter = Router();

// Public list (only visible problems, without hidden test cases)
problemsRouter.get("/", async (req, res) => {
  const page = Math.max(1, parseInt(req.query.page as string) || 1);
  const pageSize = Math.min(50, Math.max(1, parseInt(req.query.pageSize as string) || 20));
  const difficulty = req.query.difficulty as string | undefined;

  const where: Prisma.ProblemWhereInput = { visible: true };
  if (difficulty && ["EASY", "MEDIUM", "HARD"].includes(difficulty)) {
    where.difficulty = difficulty as Difficulty;
  }

  const [total, problems] = await Promise.all([
    prisma.problem.count({ where }),
    prisma.problem.findMany({
      where,
      select: {
        id: true,
        slug: true,
        title: true,
        difficulty: true,
        tags: true,
        timeLimit: true,
        memoryLimit: true,
      },
      orderBy: { id: "asc" },
      skip: (page - 1) * pageSize,
      take: pageSize,
    }),
  ]);

  res.json({ total, page, pageSize, problems });
});

// Public detail (no hidden test cases)
problemsRouter.get("/:slug", async (req, res) => {
  const problem = await prisma.problem.findUnique({
    where: { slug: req.params.slug },
    select: {
      id: true,
      slug: true,
      title: true,
      description: true,
      inputFmt: true,
      outputFmt: true,
      difficulty: true,
      tags: true,
      timeLimit: true,
      memoryLimit: true,
      samples: true,
      visible: true,
    },
  });
  if (!problem || !problem.visible) {
    res.status(404).json({ error: "题目不存在" });
    return;
  }
  res.json({ problem });
});

const createSchema = z.object({
  slug: z.string().min(2).max(60).regex(/^[a-z0-9-]+$/),
  title: z.string().min(1).max(120),
  description: z.string().min(1),
  inputFmt: z.string().optional().default(""),
  outputFmt: z.string().optional().default(""),
  difficulty: z.enum(["EASY", "MEDIUM", "HARD"]).default("EASY"),
  timeLimit: z.number().int().min(100).max(30000).default(1000),
  memoryLimit: z.number().int().min(32).max(1024).default(256),
  tags: z.array(z.string()).default([]),
  samples: z.array(z.object({ input: z.string(), output: z.string() })).default([]),
  testCases: z.array(z.object({ input: z.string(), output: z.string() })).default([]),
  visible: z.boolean().default(true),
});

// Admin: create problem
problemsRouter.post("/", authRequired, adminRequired, async (req, res) => {
  const parsed = createSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.errors[0].message });
    return;
  }
  const data = parsed.data;
  const exists = await prisma.problem.findUnique({ where: { slug: data.slug } });
  if (exists) {
    res.status(409).json({ error: "slug 已存在" });
    return;
  }
  const problem = await prisma.problem.create({ data });
  res.status(201).json({ problem });
});

// Admin: update problem
problemsRouter.put("/:slug", authRequired, adminRequired, async (req, res) => {
  const existing = await prisma.problem.findUnique({ where: { slug: req.params.slug } });
  if (!existing) {
    res.status(404).json({ error: "题目不存在" });
    return;
  }
  const parsed = createSchema.partial().safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.errors[0].message });
    return;
  }
  const problem = await prisma.problem.update({
    where: { id: existing.id },
    data: parsed.data,
  });
  res.json({ problem });
});
