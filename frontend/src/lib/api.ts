// API client. Base URL resolves to `/api` in production (proxied by nginx to
// the backend container) and can be overridden via VITE_API_BASE for dev.
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

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;

  if (!res.ok) {
    const msg = (data && data.error) || `请求失败 (${res.status})`;
    if (res.status === 401) {
      // token invalid/expired — clear it
      setToken(null);
    }
    throw new ApiError(res.status, msg);
  }
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
  getLanguages: () =>
    request<{ languages: LanguageDef[] }>("/submissions/meta/languages"),
  submit: (problemId: number, language: string, code: string) =>
    request<{ submission: Submission; detail?: { failedCase?: number; input?: string; expected?: string; actual?: string } }>(
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
};
