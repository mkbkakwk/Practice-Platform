// API client. Base URL resolves to `/api` in production (proxied by nginx to
// the backend container) and can be overridden via VITE_API_BASE for dev.
import { log, TAGS } from "./logger";

const API_BASE = (import.meta.env.VITE_API_BASE as string | undefined) || "/api";

const TOKEN_KEY = "oj_token";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string> | undefined),
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const method = (options.method as string) || "GET";
  const url = `${API_BASE}${path}`;
  log.debug(TAGS.api, `${method} ${path}`);

  let res: Response;
  try {
    res = await fetch(url, { ...options, headers });
  } catch (networkErr) {
    log.error(TAGS.api, `网络请求失败 ${method} ${path}`, networkErr);
    throw new ApiError(0, `网络请求失败：${(networkErr as Error).message}`);
  }

  const text = await res.text();
  let data: unknown = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    log.error(TAGS.api, `响应非 JSON ${method} ${path} -> ${res.status}`, text.slice(0, 200));
  }

  if (!res.ok) {
    const msg = (data && typeof data === "object" && "error" in (data as object)
      ? (data as { error: string }).error
      : null) || `请求失败 (${res.status})`;
    log.error(TAGS.api, `${method} ${path} -> ${res.status}`, msg, data);
    if (res.status === 401) {
      setToken(null);
    }
    throw new ApiError(res.status, msg);
  }
  log.debug(TAGS.api, `${method} ${path} -> ${res.status} OK`);
  return data as T;
}

export interface PublicUser {
  id: number;
  username: string;
  role: "USER" | "ADMIN";
  solvedCount?: number;
}

export interface ProblemListItem {
  id: number;
  slug: string;
  title: string;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  tags: string[];
  timeLimit: number;
  memoryLimit: number;
  visible?: boolean;
}

export interface Sample {
  input: string;
  output: string;
}

export interface ProblemDetail {
  id: number;
  slug: string;
  title: string;
  description: string;
  inputFmt: string;
  outputFmt: string;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  tags: string[];
  timeLimit: number;
  memoryLimit: number;
  samples: Sample[];
  /** Only present when fetched by an admin (for editing). */
  testCases?: Sample[];
}

/** Payload for creating/updating a problem (admin only). */
export interface ProblemUpsert {
  slug: string;
  title: string;
  description: string;
  inputFmt?: string;
  outputFmt?: string;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  timeLimit: number;
  memoryLimit: number;
  tags: string[];
  samples: Sample[];
  testCases: Sample[];
  visible: boolean;
}

export interface LanguageDef {
  id: string;
  name: string;
  ext: string;
  template: string;
}

export type Verdict = "PENDING" | "AC" | "WA" | "TLE" | "RE" | "CE" | "SE";

export interface Submission {
  id: number;
  verdict: Verdict;
  timeMs: number;
  memoryKb: number;
  message?: string;
  passed: number;
  total: number;
  language: string;
  code: string;
  createdAt: string;
  problem?: { id: number; slug: string; title: string; difficulty: string };
  user?: { id: number; username: string };
}

export const api = {
  register: (username: string, password: string) =>
    request<{ token: string; user: PublicUser }>("/auth/register", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),
  login: (username: string, password: string) =>
    request<{ token: string; user: PublicUser }>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),
  me: () => request<{ user: PublicUser }>("/auth/me"),
  listProblems: (params: { page?: number; pageSize?: number; difficulty?: string } = {}) => {
    const q = new URLSearchParams();
    if (params.page) q.set("page", String(params.page));
    if (params.pageSize) q.set("pageSize", String(params.pageSize));
    if (params.difficulty) q.set("difficulty", params.difficulty);
    return request<{ total: number; page: number; pageSize: number; problems: ProblemListItem[] }>(
      `/problems?${q.toString()}`,
    );
  },
  getProblem: (slug: string) =>
    request<{ problem: ProblemDetail }>(`/problems/${slug}`),
  createProblem: (payload: ProblemUpsert) =>
    request<{ problem: ProblemDetail }>("/problems", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  updateProblem: (slug: string, payload: ProblemUpsert) =>
    request<{ problem: ProblemDetail }>(`/problems/${slug}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),
  getLanguages: () =>
    request<{ languages: LanguageDef[] }>("/submissions/meta/languages"),
  submit: (problemId: number, language: string, code: string) =>
    request<{ submissionId: number; status: Verdict; message?: string }>(
      "/submissions",
      { method: "POST", body: JSON.stringify({ problemId, language, code }) },
    ),
  getSubmission: (id: number) =>
    request<{ submission: Submission }>(`/submissions/${id}`),
  listSubmissions: (params: { page?: number; pageSize?: number; problemId?: number } = {}) => {
    const q = new URLSearchParams();
    if (params.page) q.set("page", String(params.page));
    if (params.pageSize) q.set("pageSize", String(params.pageSize));
    if (params.problemId) q.set("problemId", String(params.problemId));
    return request<{ total: number; page: number; pageSize: number; submissions: Submission[] }>(
      `/submissions?${q.toString()}`,
    );
  },
  mySubmissions: (params: { page?: number; pageSize?: number } = {}) => {
    const q = new URLSearchParams();
    if (params.page) q.set("page", String(params.page));
    if (params.pageSize) q.set("pageSize", String(params.pageSize));
    return request<{ total: number; page: number; pageSize: number; submissions: Submission[] }>(
      `/users/me/submissions?${q.toString()}`,
    );
  },
  leaderboard: (limit = 20) =>
    request<{ leaderboard: (PublicUser & { rank: number; createdAt: string })[] }>(
      `/users/leaderboard?limit=${limit}`,
    ),

  /**
   * Poll a submission until its verdict is no longer PENDING.
   * Returns the final submission. Calls onTick(pollCount, latest) each poll.
   * Resolves with the settled submission.
   */
  pollSubmission: async (
    id: number,
    opts: { intervalMs?: number; timeoutMs?: number; onTick?: (poll: number, s: Submission | null) => void } = {},
  ): Promise<Submission> => {
    const interval = opts.intervalMs ?? 1500;
    const timeout = opts.timeoutMs ?? 30000;
    const deadline = Date.now() + timeout;
    let poll = 0;
    log.info(TAGS.poll, `开始轮询提交 #${id}，间隔 ${interval}ms，超时 ${timeout}ms`);
    while (Date.now() < deadline) {
      poll++;
      const { submission } = await api.getSubmission(id);
      log.debug(TAGS.poll, `#${id} 第 ${poll} 次轮询 verdict=${submission.verdict}`);
      opts.onTick?.(poll, submission);
      if (submission.verdict !== "PENDING") {
        log.info(TAGS.poll, `#${id} 评测完成 verdict=${submission.verdict} passed=${submission.passed}/${submission.total} timeMs=${submission.timeMs}`);
        return submission;
      }
      await new Promise((r) => setTimeout(r, interval));
    }
    log.warn(TAGS.poll, `#${id} 轮询超时（${timeout}ms），仍为 PENDING`);
    const { submission } = await api.getSubmission(id);
    return submission;
  },
};

