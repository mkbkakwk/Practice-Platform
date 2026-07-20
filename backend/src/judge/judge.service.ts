import { mkdir, rm, writeFile } from "fs/promises";
import { join } from "path";
import { randomUUID } from "crypto";
import { getLanguage } from "./languages.js";
import { run } from "./runner.js";

export interface TestCase {
  input: string;
  output: string;
}

export interface JudgeInput {
  language: string;
  code: string;
  timeLimitMs: number;
  memoryLimitKb: number;
  testCases: TestCase[];
}

export type Verdict = "AC" | "WA" | "TLE" | "RE" | "CE";

export interface JudgeResult {
  verdict: Verdict;
  passed: number;
  total: number;
  timeMs: number;
  memoryKb: number;
  message?: string;
  // First failing case details (for student-facing feedback). 1-indexed.
  failedCase?: number;
  failedInput?: string;
  failedExpected?: string;
  failedActual?: string;
}

/** Normalize output for comparison: trim trailing spaces per line, drop trailing blank lines. */
function normalize(s: string): string {
  return s
    .replace(/\r\n/g, "\n")
    .replace(/\r/g, "\n")
    .split("\n")
    .map((l) => l.replace(/\s+$/g, ""))
    .join("\n")
    .replace(/\n+$/g, "")
    .trimStart();
  // trimStart keeps leading content but drops a leading blank line which is usually noise.
}

export async function judge(input: JudgeInput): Promise<JudgeResult> {
  const lang = getLanguage(input.language);
  if (!lang) {
    return {
      verdict: "CE",
      passed: 0,
      total: input.testCases.length,
      timeMs: 0,
      memoryKb: 0,
      message: `不支持的语言: ${input.language}`,
    };
  }

  const runId = randomUUID();
  // Java requires the file be named Main.java and placed in its own directory.
  const dir = join(process.env.JUDGE_WORKSPACE || "/tmp/oj-judge", runId);

  await mkdir(dir, { recursive: true });

  // Java memory cap via ulimit is unreliable (JVM reserves huge virtual space),
  // so we disable the hard cap for it and rely on container + CPU limits.
  const memCap = input.language === "java" ? 0 : Math.round(input.memoryLimitKb * 2);

  try {
    const srcPath = join(dir, `Main.${lang.ext}`);
    await writeFile(srcPath, input.code, "utf8");

    const outPath = join(dir, "main_out");

    // 1) Compile (if needed)
    const compileCmd = lang.compile(srcPath, outPath);
    if (compileCmd) {
      const compileResult = await run({
        command: compileCmd,
        stdin: "",
        timeLimitMs: 10000,
        memoryLimitKb: 0, // compiler needs room
        cwd: dir,
      });
      if (compileResult.timedOut || compileResult.exitCode !== 0) {
        return {
          verdict: "CE",
          passed: 0,
          total: input.testCases.length,
          timeMs: 0,
          memoryKb: 0,
          message: compileResult.stderr.trim() || "编译失败",
        };
      }
    }

    // 2) Run each test case sequentially
    let passed = 0;
    let maxTime = 0;
    let lastVerdict: Verdict = "AC";

    for (let i = 0; i < input.testCases.length; i++) {
      const tc = input.testCases[i];
      const result = await run({
        command: lang.run(srcPath, outPath),
        stdin: tc.input,
        timeLimitMs: input.timeLimitMs,
        memoryLimitKb: memCap,
        cwd: dir,
      });

      if (result.timedOut) {
        return {
          verdict: "TLE",
          passed,
          total: input.testCases.length,
          timeMs: Math.max(maxTime, input.timeLimitMs),
          memoryKb: 0,
          message: `第 ${i + 1} 个测试点运行超时`,
          failedCase: i + 1,
        };
      }
      if (result.memoryError) {
        return {
          verdict: "RE",
          passed,
          total: input.testCases.length,
          timeMs: maxTime,
          memoryKb: input.memoryLimitKb,
          message: `第 ${i + 1} 个测试点内存超限`,
          failedCase: i + 1,
        };
      }
      if (result.exitCode !== 0) {
        return {
          verdict: "RE",
          passed,
          total: input.testCases.length,
          timeMs: maxTime,
          memoryKb: 0,
          message: `第 ${i + 1} 个测试点运行错误 (退出码 ${result.exitCode})`,
          failedCase: i + 1,
        };
      }

      maxTime = Math.max(maxTime, result.elapsedMs);

      const expected = normalize(tc.output);
      const actual = normalize(result.stdout);
      if (expected !== actual) {
        return {
          verdict: "WA",
          passed,
          total: input.testCases.length,
          timeMs: maxTime,
          memoryKb: 0,
          message: `第 ${i + 1} 个测试点答案错误`,
          failedCase: i + 1,
          failedInput: tc.input,
          failedExpected: tc.output,
          failedActual: result.stdout,
        };
      }

      passed++;
      lastVerdict = "AC";
    }

    void lastVerdict;
    return {
      verdict: "AC",
      passed,
      total: input.testCases.length,
      timeMs: maxTime,
      memoryKb: 0,
      message: `通过全部 ${input.testCases.length} 个测试点`,
    };
  } catch (err) {
    return {
      verdict: "RE",
      passed: 0,
      total: input.testCases.length,
      timeMs: 0,
      memoryKb: 0,
      message: `评测器异常: ${(err as Error).message}`,
    };
  } finally {
    // Best-effort cleanup
    try {
      await rm(dir, { recursive: true, force: true });
    } catch {
      /* ignore */
    }
  }
}
