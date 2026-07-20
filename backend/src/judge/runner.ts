import { spawn } from "child_process";

/**
 * Build the environment for the untrusted process. In the (Linux) container we
 * use a restricted PATH so only system tools are reachable — a security
 * measure. On other platforms (local dev) we inherit the user environment so
 * language runtimes on the user's PATH remain discoverable.
 */
function buildEnv(cwd: string): NodeJS.ProcessEnv {
  const common = {
    HOME: cwd,
    LANG: "C.UTF-8",
    LC_ALL: "C.UTF-8",
  };
  if (process.platform === "win32") {
    return { ...process.env, ...common };
  }
  return {
    PATH: "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
    ...common,
  };
}

export interface RunOptions {
  /** Shell command to execute. */
  command: string;
  /** Standard input fed to the process. */
  stdin: string;
  /** Wall-clock timeout in milliseconds. */
  timeLimitMs: number;
  /** Virtual-memory cap in KB (best-effort, Linux only). Set 0 to disable. */
  memoryLimitKb: number;
  /** Working directory. */
  cwd: string;
}

export interface RunResult {
  ok: boolean;
  stdout: string;
  stderr: string;
  exitCode: number | null;
  signal: NodeJS.Signals | null;
  timedOut: boolean;
  memoryError: boolean;
  elapsedMs: number;
}

/**
 * Execute a command in a sandboxed fashion:
 *  - runs as a new process group so we can kill the whole tree on timeout
 *  - applies a best-effort virtual-memory cap via `ulimit -v` (Linux)
 *  - applies a CPU-time cap via `ulimit -t` so runaway loops are reaped even
 *    if wall-clock detection races
 *  - captures stdout/stderr and exit status
 *
 * NOTE: This is designed to run inside the backend Docker container where the
 * filesystem and network are already isolated by the container boundary.
 */
export function run(opts: RunOptions): Promise<RunResult> {
  return new Promise((resolve) => {
    const start = Date.now();

    let prelude = "";
    if (opts.memoryLimitKb > 0) {
      prelude += `ulimit -v ${opts.memoryLimitKb} 2>/dev/null; `;
    }
    // CPU seconds = ceil(timeLimitMs/1000) + 1s grace
    const cpuSec = Math.ceil(opts.timeLimitMs / 1000) + 1;
    prelude += `ulimit -t ${cpuSec} 2>/dev/null; `;
    // Limit file writes (output explosion protection). 256MB in 1KB blocks.
    prelude += `ulimit -f 262144 2>/dev/null; `;

    const wrapped = `${prelude}exec ${opts.command}`;

    const child = spawn("bash", ["-c", wrapped], {
      cwd: opts.cwd,
      detached: true,
      stdio: ["pipe", "pipe", "pipe"],
      env: buildEnv(opts.cwd),
    });

    let stdout = "";
    let stderr = "";
    let timedOut = false;
    let memoryError = false;
    let settled = false;

    const cap = 16 * 1024 * 1024; // 16MB guard per stream
    child.stdout.on("data", (d: Buffer) => {
      if (stdout.length < cap) stdout += d.toString("utf8");
    });
    child.stderr.on("data", (d: Buffer) => {
      if (stderr.length < cap) stderr += d.toString("utf8");
    });

    const timer = setTimeout(() => {
      timedOut = true;
      try {
        if (child.pid) process.kill(-child.pid, "SIGKILL");
      } catch {
        /* group already gone */
      }
    }, opts.timeLimitMs);

    const finish = (result: Omit<RunResult, "elapsedMs">) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      resolve({ ...result, elapsedMs: Date.now() - start });
    };

    child.on("close", (code, signal) => {
      // Heuristic: an OOM usually surfaces as non-zero exit with memory text.
      const memHint = /Cannot allocate memory|MemoryError|out of memory|bad allocation|std::bad_alloc/i.test(stderr);
      memoryError = !timedOut && memHint;
      finish({
        ok: code === 0 && !timedOut,
        stdout,
        stderr,
        exitCode: code,
        signal,
        timedOut,
        memoryError,
      });
    });

    child.on("error", () => {
      finish({
        ok: false,
        stdout,
        stderr,
        exitCode: null,
        signal: null,
        timedOut,
        memoryError: false,
      });
    });

    // Feed stdin. Ignore write errors (process may have exited early).
    try {
      child.stdin.end(opts.stdin);
    } catch {
      /* ignore */
    }
  });
}
