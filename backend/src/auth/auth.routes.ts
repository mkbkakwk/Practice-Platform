import { Router } from "express";
import bcrypt from "bcryptjs";
import { z } from "zod";
import { prisma } from "../prisma.js";
import { signToken } from "./jwt.js";
import { config } from "../config.js";
import { authRequired } from "../middleware/auth.js";

export const authRouter = Router();

const registerSchema = z.object({
  username: z.string().min(3).max(20).regex(/^[A-Za-z0-9_]+$/, "用户名只能包含字母、数字、下划线"),
  password: z.string().min(6).max(64),
});

authRouter.post("/register", async (req, res) => {
  const parsed = registerSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.errors[0].message });
    return;
  }
  const { username, password } = parsed.data;

  const exists = await prisma.user.findUnique({ where: { username } });
  if (exists) {
    res.status(409).json({ error: "用户名已被占用" });
    return;
  }

  const count = await prisma.user.count();
  const role = config.promoteFirstAdmin && count === 0 ? "ADMIN" : "USER";

  const hashed = await bcrypt.hash(password, 10);
  const user = await prisma.user.create({
    data: { username, password: hashed, role },
  });

  const token = signToken({ id: user.id, username: user.username, role: user.role });
  res.status(201).json({
    token,
    user: { id: user.id, username: user.username, role: user.role },
  });
});

authRouter.post("/login", async (req, res) => {
  const { username, password } = req.body as { username?: string; password?: string };
  if (!username || !password) {
    res.status(400).json({ error: "请输入用户名和密码" });
    return;
  }
  const user = await prisma.user.findUnique({ where: { username } });
  if (!user) {
    res.status(401).json({ error: "用户名或密码错误" });
    return;
  }
  const ok = await bcrypt.compare(password, user.password);
  if (!ok) {
    res.status(401).json({ error: "用户名或密码错误" });
    return;
  }
  const token = signToken({ id: user.id, username: user.username, role: user.role });
  res.json({
    token,
    user: { id: user.id, username: user.username, role: user.role, solvedCount: user.solvedCount },
  });
});

authRouter.get("/me", authRequired, async (req, res) => {
  const user = await prisma.user.findUnique({
    where: { id: req.user!.id },
    select: { id: true, username: true, role: true, solvedCount: true, createdAt: true },
  });
  if (!user) {
    res.status(404).json({ error: "用户不存在" });
    return;
  }
  res.json({ user });
});
