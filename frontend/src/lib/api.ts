// API client. Base URL resolves to `/api` in production (proxied by nginx to
// the backend container) and can be overridden via VITE_API_BASE for dev.
import { log, TAGS } from "./logger";

const API_BASE = (import.meta.env.VITE_API_BASE as string | undefined) || "/api";

const TOKEN_KEY = "oj_token";
export const AUTH_EXPIRED_EVENT = "oj:auth-expired";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

function invalidateSession(requestToken: string | null, message: string) {
  if (!requestToken || getToken() !== requestToken) return;
  setToken(null);
  window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT, { detail: { message } }));
}

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

export function getApiErrorMessage(error: unknown, fallback = "操作失败") {
  return error instanceof ApiError ? error.message : fallback;
}

export function isAbortError(error: unknown) {
  return error instanceof Error && error.name === "AbortError";
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  withAuthentication = true,
): Promise<T> {
  const token = withAuthentication ? getToken() : null;
  // For FormData (file uploads) we must NOT set Content-Type — the browser sets
  // the correct multipart/form-data; charset boundary automatically. Setting it
  // manually to application/json breaks multipart parsing on the server.
  const isFormData = options.body instanceof FormData;
  const headers: Record<string, string> = {
    ...(isFormData ? {} : { "Content-Type": "application/json" }),
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
    if (isAbortError(networkErr)) throw networkErr;
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
    if (res.status === 401) invalidateSession(token, msg);
    throw new ApiError(res.status, msg);
  }
  log.debug(TAGS.api, `${method} ${path} -> ${res.status} OK`);
  return data as T;
}

async function downloadFile(path: string, filename: string): Promise<void> {
  const token = getToken();
  const headers: Record<string, string> = {};
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`${API_BASE}${path}`, { headers });
  if (!res.ok) {
    let message = `下载失败 (${res.status})`;
    try {
      const data = await res.json() as { error?: string };
      if (data.error) message = data.error;
    } catch {
      // Keep the controlled fallback message for non-JSON responses.
    }
    if (res.status === 401) invalidateSession(token, message);
    throw new ApiError(res.status, message);
  }

  const objectUrl = URL.createObjectURL(await res.blob());
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(objectUrl);
}
export interface PublicUser {
  id: number;
  username: string;
  role: "USER" | "TEACHER" | "ADMIN";
  solvedCount?: number;
}

export interface SystemStatusComponent {
  status: "UP" | "DOWN" | "UNKNOWN";
  latencyMs: number;
}

export interface SystemStatus {
  checkedAt: string;
  version: { gitSha: string; version: string; buildTime: string; flywayVersion: string };
  components: Record<string, SystemStatusComponent>;
  queues: { main: number | "UNKNOWN"; retry: number | "UNKNOWN"; dlq: number | "UNKNOWN" };
  outbox: { status: string; latencyMs: number; nonterminal?: number; publisherRunning: boolean; lastFailure: string };
  metrics: Record<string, number>;
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
  contentVisibility: "PUBLIC" | "CONTEST_ONLY";
  createdBy: number | null;
  creatorUsername: string | null;
  submissionCount: number;
  createdAt: string;
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
  /** Only present for an authorized content manager (for editing). */
  testCases?: Sample[];
  visible: boolean;
  contentVisibility: "PUBLIC" | "CONTEST_ONLY";
  createdBy: number | null;
  creatorUsername: string | null;
  createdAt: string;
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
  contentVisibility: "PUBLIC" | "CONTEST_ONLY";
}

export interface LanguageDef {
  id: string;
  name: string;
  ext: string;
  template: string;
}

export type Verdict = "PENDING" | "JUDGING" | "AC" | "WA" | "TLE" | "MLE" | "OLE" | "RE" | "CE" | "SE" | "JUDGE_FAILED";

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
  contestProblemId?: number | null;
  problem?: { id: number; slug: string; title: string; difficulty: string };
  user?: { id: number; username: string };
}

// ---- Office operation practice ----
export type OfficeAppType = "WORD" | "EXCEL" | "PPT";
export type OfficeQuestionType = "SINGLE_CHOICE" | "MULTI_CHOICE" | "TRUE_FALSE";

export interface OfficeQuestionListItem {
  id: number;
  appType: OfficeAppType;
  category: string;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  questionType: OfficeQuestionType;
  content: string;
  visible?: boolean;
  contentVisibility: "PUBLIC" | "CONTEST_ONLY";
  createdBy: number | null;
  creatorUsername: string | null;
  submissionCount: number;
  createdAt: string;
}

export interface OfficeQuestionDetail {
  id: number;
  appType: OfficeAppType;
  category: string;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  questionType: OfficeQuestionType;
  content: string;
  options: string[];
  /** Only present when fetched by an admin (for editing). */
  answer?: string;
  explanation?: string;
  visible?: boolean;
  contentVisibility: "PUBLIC" | "CONTEST_ONLY";
  createdBy: number | null;
  creatorUsername: string | null;
  createdAt: string;
}

export interface OfficeQuestionUpsert {
  appType: OfficeAppType;
  category: string;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  questionType: OfficeQuestionType;
  content: string;
  options: string[];
  answer: string;
  explanation?: string;
  visible: boolean;
  contentVisibility: "PUBLIC" | "CONTEST_ONLY";
}

export interface OfficeSubmitResult {
  correct: boolean;
  correctAnswer: string;
  explanation: string;
}

export interface OfficeStats {
  totalAnswered: number;
  correctCount: number;
  accuracy: number;
  wordAnswered: number;
  wordCorrect: number;
  excelAnswered: number;
  excelCorrect: number;
  pptAnswered: number;
  pptCorrect: number;
}

// ---- Office document exercises (排版练习) ----
export interface DocExerciseListItem {
  id: number;
  title: string;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  visible: boolean;
  contentVisibility: "PUBLIC" | "CONTEST_ONLY";
  hasTeacherDoc: boolean;
  hasStarterDoc: boolean;
  starterDocName: string | null;
  createdBy: number | null;
  creatorUsername: string | null;
  submissionCount: number;
  createdAt: string;
}

export interface DocExerciseDetail {
  id: number;
  title: string;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  description: string;
  teacherDocName: string | null;
  starterDocName: string | null;
  visible: boolean;
  contentVisibility: "PUBLIC" | "CONTEST_ONLY";
  createdBy: number | null;
  creatorUsername: string | null;
  createdAt: string;
}

export interface DocParaInfo {
  index: number;
  text: string;
  fontFamily: string;
  fontSizePt: number;
  bold: boolean;
  italic: boolean;
  underline: boolean;
  align: string;
  firstLineIndentChars: number;
  lineSpacing: number;
  color: string;
}

export interface DocCompareDiff {
    ruleId?: string;
    prop?: string;
  label: string;
  student: unknown;
  teacher: unknown;
  match: boolean;
}

export interface DocCompareRow {
  index: number;
  studentText: string;
  teacherText: string;
  diffs: DocCompareDiff[];
  match: boolean;
}

export type DocSubmissionStatus =
  "PENDING" | "JUDGING" | "COMPLETED" | "FAILED" | "AUTO_CHECKED" | "NEEDS_REVIEW" | "REVIEWED";

export interface StudentDocSubmission {
  id: number;
  userId: number;
  exerciseId: number;
  studentDocName: string;
  status: DocSubmissionStatus;
  score: number | null;
  teacherComment: string | null;
  judgeVersion: string;
  resultDetail: OfficeJudgeResultDetail;
  errorCategory: string | null;
  judgedAt: string | null;
  createdAt: string;
  contestProblemId?: number | null;
}

export interface ReviewerDocSubmission {
  id: number;
  userId: number;
  exerciseId: number;
  studentDocName: string;
  autoResult: string;
  compareResult: string;
  status: DocSubmissionStatus;
  score: number | null;
  teacherComment: string | null;
  judgeVersion: string;
  resultDetail: OfficeJudgeResultDetail;
  errorCategory: string | null;
  judgedAt: string | null;
  createdAt: string;
  contestProblemId?: number | null;
}

// ---- Contest core ----
export type ContestPhase = "DRAFT" | "UPCOMING" | "RUNNING" | "ENDED" | "CANCELLED";
export type ContestAccessType = "OPEN" | "INVITE_ONLY";
export type ContestScoringMode = "SCORE" | "ICPC";

export interface ContestSummary {
  id: number;
  title: string;
  description: string;
  status: "DRAFT" | "PUBLISHED" | "CANCELLED";
  phase: ContestPhase;
  accessType: ContestAccessType;
  ownerId: number;
  ownerUsername: string | null;
  scoringMode: ContestScoringMode;
  startAt: string;
  endAt: string;
  freezeAt: string | null;
  participant: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ContestProblemItem {
  contestProblemId: number;
  problemType: "ALGORITHM" | "OFFICE_CHOICE" | "OFFICE_DOCX";
  problemId: number;
  displayOrder: number;
  label: string;
  title: string;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  slug: string | null;
  content: Record<string, unknown> | null;
}

export interface ContestDetail {
  contest: ContestSummary;
  problems: ContestProblemItem[];
}

export interface ContestParticipant {
  id: number;
  userId: number;
  username: string;
  addedBy: number;
  joinedAt: string;
}

export interface ContestStudentOption {
  id: number;
  username: string;
  role: "USER";
}

export interface ContestUpsert {
  title: string;
  description: string;
  startAt: string;
  endAt: string;
  accessType: ContestAccessType;
  scoringMode: ContestScoringMode;
  freezeAt: string | null;
}

export interface ContestChoiceSubmission {
  recordId: number;
  contestProblemId: number;
  selected: string[];
  correct: boolean;
  createdAt: string;
}

export interface ContestStandingProblem {
  contestProblemId: number;
  label: string;
  score: number | null;
  solved: boolean;
  attempts: number;
  penaltyMinutes: number | null;
}

export interface ContestStandingEntry {
  rank: number;
  userId: number;
  username: string;
  totalScore: number;
  solved: number;
  penaltyMinutes: number;
  problems: ContestStandingProblem[];
}

export interface ContestStanding {
  contestId: number;
  scoringMode: ContestScoringMode;
  phase: ContestPhase;
  frozen: boolean;
  managerView: boolean;
  freezeAt: string | null;
  generatedAt: string;
  entries: ContestStandingEntry[];
}

export interface ContestAnalyticsProblem {
  contestProblemId: number; label: string; displayOrder: number; title: string; problemType: ContestProblemItem["problemType"];
  submissionCount: number; uniqueSubmitterCount: number; participationRate: number; successParticipantCount: number; successRate: number; infrastructureFailureCount: number;
  validJudgedSubmissionCount: number | null; acceptedSubmissionCount: number | null; submissionAcceptanceRate: number | null;
  correctSubmissionCount: number | null; validSubmissionCount: number | null; correctSubmissionRate: number | null;
  scoredParticipantCount: number | null; averageBestScore: number | null; medianBestScore: number | null; perfectScoreParticipantCount: number | null; perfectScoreRate: number | null; needsReviewSubmissionCount: number | null;
}
export interface ContestAnalytics { contestId: number; title: string; scoringMode: ContestScoringMode; phase: ContestPhase; generatedAt: string; overview: { participantCount:number; activeParticipantCount:number; inactiveParticipantCount:number; totalSubmissionCount:number; algorithmSubmissionCount:number; choiceSubmissionCount:number; docxSubmissionCount:number; firstSubmissionAt:string|null; lastSubmissionAt:string|null; averageTotalScore:number|null; maxTotalScore:number|null; minTotalScore:number|null; fullScoreParticipantCount:number|null; averageSolved:number|null; maxSolved:number|null; averagePenaltyAmongSolvedParticipants:number|null; }; problems: ContestAnalyticsProblem[]; timeline: {startAt:string;endAt:string;submissionCount:number;algorithmCount:number;choiceCount:number;docxCount:number;successCount:number}[]; distribution:{label:string;participantCount:number}[]; }
export interface ContestAnalyticsParticipant { userId:number; username:string; rank:number|null; totalSubmissionCount:number; submittedProblemCount:number; successfulProblemCount:number; lastSubmissionAt:string|null; totalScore:number|null; solved:number|null; penaltyMinutes:number|null; }

export interface RejudgeBatchItem {
  id: number;
  submissionId: number;
  judgeGeneration: number;
  status: "QUEUED" | "COMPLETED" | "FAILED" | "STALE";
  createdAt: string;
  completedAt: string | null;
}

export interface RejudgeBatch {
  id: number;
  contestId: number;
  contestProblemId: number | null;
  requestedSubmissionId: number | null;
  requestedBy: number;
  status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
  totalCount: number;
  queuedCount: number;
  completedCount: number;
  failedCount: number;
  createdAt: string;
  completedAt: string | null;
}

export interface RejudgeBatchDetail {
  batch: RejudgeBatch;
  items: RejudgeBatchItem[];
}

export interface RejudgeableSubmission {
  id: number;
  contestProblemId: number;
  problemLabel: string;
  userId: number;
  username: string;
  verdict: string;
  judgeGeneration: number;
  createdAt: string;
}

export interface OfficeJudgeResultDetail {
  judgeVersion: string;
  totalScore: number;
  earnedScore: number;
  passed: boolean;
  items: Array<{
    ruleId: string;
    target: string;
    expected: string;
    actual: string;
    score: number;
    earned: number;
    passed: boolean;
    message: string;
  }>;
  totalErrorCount: number;
  truncated: boolean;
}

export interface DocSubmissionListItem {
  id: number;
  exerciseId: number;
  userId: number;
  studentDocName: string;
  status: DocSubmissionStatus;
  score: number | null;
  createdAt: string;
}

export interface UserListItem {
  id: number;
  username: string;
  role: "USER" | "TEACHER" | "ADMIN";
  solvedCount: number;
  createdAt: string;
}

export const api = {
  getSystemStatus: (signal?: AbortSignal) => request<SystemStatus>("/admin/system-status", { signal }),
  register: (username: string, password: string) =>
    request<{ token: string; user: PublicUser }>("/auth/register", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }, false),
  login: (username: string, password: string) =>
    request<{ token: string; user: PublicUser }>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }, false),
  me: () => request<{ user: PublicUser }>("/auth/me"),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<{ message: string }>("/auth/password", {
      method: "PUT",
      body: JSON.stringify({ currentPassword, newPassword }),
    }),
  listProblems: (params: { page?: number; pageSize?: number; difficulty?: string } = {}) => {
    const q = new URLSearchParams();
    if (params.page) q.set("page", String(params.page));
    if (params.pageSize) q.set("pageSize", String(params.pageSize));
    if (params.difficulty) q.set("difficulty", params.difficulty);
    return request<{ total: number; page: number; pageSize: number; problems: ProblemListItem[] }>(
      `/problems?${q.toString()}`,
    );
  },
  listManageProblems: (params: { page?: number; pageSize?: number; difficulty?: string } = {}) => {
    const q = new URLSearchParams();
    if (params.page) q.set("page", String(params.page));
    if (params.pageSize) q.set("pageSize", String(params.pageSize));
    if (params.difficulty) q.set("difficulty", params.difficulty);
    return request<{ total: number; page: number; pageSize: number; problems: ProblemListItem[] }>(
      `/problems/manage?${q.toString()}`,
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
  setProblemVisibility: (slug: string, visible: boolean) =>
    request<{ problem: ProblemDetail }>(`/problems/${slug}/visibility`, {
      method: "PUT",
      body: JSON.stringify({ visible }),
    }),
  deleteProblem: (slug: string) => request<{ deleted: boolean; deletedSubmissions: number; affectedUsers: number }>(
    `/problems/${slug}`,
    { method: "DELETE" },
  ),
  getLanguages: () =>
    request<{ languages: LanguageDef[] }>("/submissions/meta/languages"),
  submit: (problemId: number, language: string, code: string) =>
    request<{ submissionId: number; status: Verdict; message?: string }>(
      "/submissions",
      { method: "POST", body: JSON.stringify({ problemId, language, code }) },
    ),
  getSubmission: (id: number, signal?: AbortSignal) =>
    request<{ submission: Submission }>(`/submissions/${id}`, { signal }),
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

  // ---- Office practice ----
  listOfficeQuestions: (params: { page?: number; pageSize?: number; appType?: string; difficulty?: string } = {}) => {
    const q = new URLSearchParams();
    if (params.page) q.set("page", String(params.page));
    if (params.pageSize) q.set("pageSize", String(params.pageSize));
    if (params.appType) q.set("appType", params.appType);
    if (params.difficulty) q.set("difficulty", params.difficulty);
    return request<{ total: number; page: number; pageSize: number; questions: OfficeQuestionListItem[] }>(
      `/office/questions?${q.toString()}`,
    );
  },
  listManageOfficeQuestions: (params: { page?: number; pageSize?: number; appType?: string; difficulty?: string } = {}) => {
    const q = new URLSearchParams();
    if (params.page) q.set("page", String(params.page));
    if (params.pageSize) q.set("pageSize", String(params.pageSize));
    if (params.appType) q.set("appType", params.appType);
    if (params.difficulty) q.set("difficulty", params.difficulty);
    return request<{ total: number; page: number; pageSize: number; questions: OfficeQuestionListItem[] }>(
      `/office/questions/manage?${q.toString()}`,
    );
  },
  getOfficeQuestion: (id: number) =>
    request<{ question: OfficeQuestionDetail }>(`/office/questions/${id}`),
  createOfficeQuestion: (payload: OfficeQuestionUpsert) =>
    request<{ question: OfficeQuestionDetail }>(`/office/questions`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  updateOfficeQuestion: (id: number, payload: OfficeQuestionUpsert) =>
    request<{ question: OfficeQuestionDetail }>(`/office/questions/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),
  setOfficeQuestionVisibility: (id: number, visible: boolean) =>
    request<{ question: OfficeQuestionDetail }>(`/office/questions/${id}/visibility`, {
      method: "PUT",
      body: JSON.stringify({ visible }),
    }),
  deleteOfficeQuestion: (id: number) => request<{ deleted: boolean; deletedRecords: number; affectedUsers: number }>(
    `/office/questions/${id}`,
    { method: "DELETE" },
  ),
  submitOfficeAnswer: (questionId: number, selected: string[]) =>
    request<{ result: OfficeSubmitResult }>(`/office/submit`, {
      method: "POST",
      body: JSON.stringify({ questionId, selected }),
    }),
  officeStats: () =>
    request<{ stats: OfficeStats }>(`/office/stats`),

  // ---- Office document exercises (排版练习: upload .docx, auto-compare) ----
  listDocExercises: (params: { page?: number; pageSize?: number } = {}) => {
    const q = new URLSearchParams();
    if (params.page) q.set("page", String(params.page));
    if (params.pageSize) q.set("pageSize", String(params.pageSize));
    return request<{ total: number; page: number; pageSize: number; exercises: DocExerciseListItem[] }>(
      `/office/docs/exercises?${q.toString()}`,
    );
  },
  listManageDocExercises: (params: { page?: number; pageSize?: number } = {}) => {
    const q = new URLSearchParams();
    if (params.page) q.set("page", String(params.page));
    if (params.pageSize) q.set("pageSize", String(params.pageSize));
    return request<{ total: number; page: number; pageSize: number; exercises: DocExerciseListItem[] }>(
      `/office/docs/exercises/manage?${q.toString()}`,
    );
  },
  getDocExercise: (id: number) =>
    request<{ exercise: DocExerciseDetail }>(`/office/docs/exercises/${id}`),
  createDocExercise: (payload: { title: string; difficulty: string; description: string; visible?: boolean; contentVisibility: "PUBLIC" | "CONTEST_ONLY" }) =>
    request<{ exercise: DocExerciseDetail }>(`/office/docs/exercises`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  updateDocExercise: (id: number, payload: { title: string; difficulty: string; description: string; visible?: boolean; contentVisibility: "PUBLIC" | "CONTEST_ONLY" }) =>
    request<{ exercise: DocExerciseDetail }>(`/office/docs/exercises/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),
  setDocExerciseVisibility: (id: number, visible: boolean) =>
    request<{ exercise: DocExerciseDetail }>(`/office/docs/exercises/${id}/visibility`, {
      method: "PUT",
      body: JSON.stringify({ visible }),
    }),
  deleteDocExercise: (id: number) => request<{ deleted: boolean; deletedSubmissions: number; deletedFiles: number; affectedUsers: number }>(
    `/office/docs/exercises/${id}`,
    { method: "DELETE" },
  ),
  uploadTeacherDoc: (exerciseId: number, file: File) => {
    const fd = new FormData();
    fd.append("file", file);
    return request<{ teacherDocName: string }>(`/office/docs/exercises/${exerciseId}/teacher-doc`, {
      method: "POST",
      body: fd,
    });
  },
  downloadTeacherDoc: (exerciseId: number, filename: string) =>
    downloadFile(`/office/docs/exercises/${exerciseId}/teacher-doc`, filename),
  uploadStarterDoc: (exerciseId: number, file: File) => {
    const fd = new FormData();
    fd.append("file", file);
    return request<{ starterDocName: string }>(`/office/docs/exercises/${exerciseId}/starter`, {
      method: "POST",
      body: fd,
    });
  },
  downloadStarterDoc: (exerciseId: number, filename: string) =>
    downloadFile(`/office/docs/exercises/${exerciseId}/starter`, filename),
  submitDocExercise: (exerciseId: number, file: File) => {
    const fd = new FormData();
    fd.append("file", file);
    return request<{ submission: StudentDocSubmission }>(`/office/docs/exercises/${exerciseId}/submit`, {
      method: "POST",
      body: fd,
    });
  },
  getDocSubmission: (id: number) =>
    request<{ submission: StudentDocSubmission }>(`/office/docs/submissions/${id}`),
  getDocSubmissionForReview: (id: number) =>
    request<{ submission: ReviewerDocSubmission }>(`/office/docs/submissions/${id}/review-detail`),
  listDocSubmissions: (params: { exerciseId?: number; page?: number; pageSize?: number } = {}) => {
    const q = new URLSearchParams();
    if (params.exerciseId) q.set("exerciseId", String(params.exerciseId));
    if (params.page) q.set("page", String(params.page));
    if (params.pageSize) q.set("pageSize", String(params.pageSize));
    return request<{ total: number; page: number; pageSize: number; submissions: DocSubmissionListItem[] }>(
      `/office/docs/submissions?${q.toString()}`,
    );
  },
  downloadStudentDoc: (submissionId: number, filename: string) =>
    downloadFile(`/office/docs/submissions/${submissionId}/download`, filename),
  reviewDocSubmission: (id: number, score: number, comment: string) =>
    request<{ submission: ReviewerDocSubmission }>(`/office/docs/submissions/${id}/review`, {
      method: "PUT",
      body: JSON.stringify({ score, comment }),
    }),

  // ---- Contest core ----
  listContests: (params: { page?: number; pageSize?: number } = {}) => {
    const q = new URLSearchParams();
    if (params.page) q.set("page", String(params.page));
    if (params.pageSize) q.set("pageSize", String(params.pageSize));
    return request<{ total: number; page: number; pageSize: number; contests: ContestSummary[] }>(
      `/contests?${q.toString()}`,
    );
  },
  getContest: (id: number) => request<{ detail: ContestDetail }>(`/contests/${id}`),
  getContestStandings: (id: number) => request<{ standings: ContestStanding }>(`/contests/${id}/standings`),
  getContestAnalytics: (id: number, signal?: AbortSignal) =>
    request<{ analytics: ContestAnalytics }>(`/contests/${id}/analytics`, { signal }),
  getContestAnalyticsParticipants: (
    id: number,
    params: { page?: number; pageSize?: number; query?: string } = {},
    signal?: AbortSignal,
  ) => {
    const q = new URLSearchParams();
    if (params.page) q.set("page", String(params.page));
    if (params.pageSize) q.set("pageSize", String(params.pageSize));
    if (params.query) q.set("query", params.query);
    return request<{ participants: { page: number; pageSize: number; total: number; participants: ContestAnalyticsParticipant[] } }>(
      `/contests/${id}/analytics/participants?${q}`,
      { signal },
    );
  },
  searchContestStudents: (params: { query?: string; page?: number; pageSize?: number } = {}) => {
    const q = new URLSearchParams();
    if (params.query) q.set("query", params.query);
    if (params.page) q.set("page", String(params.page));
    if (params.pageSize) q.set("pageSize", String(params.pageSize));
    return request<{ total: number; page: number; pageSize: number; students: ContestStudentOption[] }>(
      `/contests/students?${q.toString()}`,
    );
  },
  createContest: (payload: ContestUpsert) => request<{ detail: ContestDetail }>("/contests", {
    method: "POST",
    body: JSON.stringify(payload),
  }),
  updateContest: (id: number, payload: ContestUpsert) => request<{ detail: ContestDetail }>(`/contests/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  }),
  publishContest: (id: number) => request<{ detail: ContestDetail }>(`/contests/${id}/publish`, { method: "POST" }),
  cancelContest: (id: number) => request<{ detail: ContestDetail }>(`/contests/${id}/cancel`, { method: "POST" }),
  deleteContest: (id: number) => request<{ deleted: boolean }>(`/contests/${id}`, { method: "DELETE" }),
  joinContest: (id: number) => request<{ participant: ContestParticipant }>(`/contests/${id}/join`, { method: "POST" }),
  listContestParticipants: (id: number, params: { page?: number; pageSize?: number } = {}) => {
    const q = new URLSearchParams();
    if (params.page) q.set("page", String(params.page));
    if (params.pageSize) q.set("pageSize", String(params.pageSize));
    return request<{ total: number; page: number; pageSize: number; participants: ContestParticipant[] }>(
      `/contests/${id}/participants?${q.toString()}`,
    );
  },
  addContestParticipant: (id: number, userId: number) => request<{ participant: ContestParticipant }>(
    `/contests/${id}/participants`, { method: "POST", body: JSON.stringify({ userId }) },
  ),
  removeContestParticipant: (id: number, userId: number) => request<{ removed: boolean }>(
    `/contests/${id}/participants/${userId}`, { method: "DELETE" },
  ),
  addContestProblem: (id: number, problemType: ContestProblemItem["problemType"], problemId: number, label?: string) =>
    request<{ contestProblem: ContestProblemItem }>(`/contests/${id}/problems`, {
      method: "POST",
      body: JSON.stringify({ problemType, problemId, label }),
    }),
  reorderContestProblems: (id: number, contestProblemIds: number[]) =>
    request<{ problems: ContestProblemItem[] }>(`/contests/${id}/problems/order`, {
      method: "PUT",
      body: JSON.stringify({ contestProblemIds }),
    }),
  removeContestProblem: (id: number, contestProblemId: number) => request<{ removed: boolean }>(
    `/contests/${id}/problems/${contestProblemId}`, { method: "DELETE" },
  ),
  submitContestAlgorithm: (contestId: number, contestProblemId: number, language: string, code: string) =>
    request<{ submissionId: number; status: Verdict; message: string }>(
      `/contests/${contestId}/problems/${contestProblemId}/submissions`, {
        method: "POST",
        body: JSON.stringify({ language, code }),
      },
    ),
  submitContestOffice: (contestId: number, contestProblemId: number, file: File) => {
    const fd = new FormData();
    fd.append("file", file);
    return request<{ submission: StudentDocSubmission }>(
      `/contests/${contestId}/problems/${contestProblemId}/office-submissions`,
      { method: "POST", body: fd },
    );
  },
  submitContestChoice: (contestId: number, contestProblemId: number, selected: string[]) =>
    request<{ submission: ContestChoiceSubmission }>(
      `/contests/${contestId}/problems/${contestProblemId}/choice-submissions`,
      { method: "POST", body: JSON.stringify({ selected }) },
    ),
  rejudgeContestSubmission: (contestId: number, submissionId: number) =>
    request<{ batch: RejudgeBatchDetail }>(`/contests/${contestId}/rejudge/submissions/${submissionId}`, { method: "POST" }),
  rejudgeContestProblem: (contestId: number, contestProblemId: number) =>
    request<{ batch: RejudgeBatchDetail }>(`/contests/${contestId}/problems/${contestProblemId}/rejudge`, { method: "POST" }),
  rejudgeContest: (contestId: number) =>
    request<{ batch: RejudgeBatchDetail }>(`/contests/${contestId}/rejudge`, { method: "POST" }),
  listRejudgeableContestSubmissions: (contestId: number, params: { page?: number; pageSize?: number } = {}) => {
    const q = new URLSearchParams();
    if (params.page) q.set("page", String(params.page));
    if (params.pageSize) q.set("pageSize", String(params.pageSize));
    return request<{ total: number; page: number; pageSize: number; submissions: RejudgeableSubmission[] }>(
      `/contests/${contestId}/rejudge/submissions${q.size ? `?${q}` : ""}`,
    );
  },
  getRejudgeBatch: (contestId: number, batchId: number) =>
    request<{ batch: RejudgeBatchDetail }>(`/contests/${contestId}/rejudge/batches/${batchId}`),
  downloadContestStarter: (contestId: number, contestProblemId: number, filename: string) =>
    downloadFile(`/contests/${contestId}/problems/${contestProblemId}/starter`, filename),

  // ---- admin user management ----
  listUsers: (params: { page?: number; pageSize?: number } = {}) => {
    const q = new URLSearchParams();
    if (params.page) q.set("page", String(params.page));
    if (params.pageSize) q.set("pageSize", String(params.pageSize));
    return request<{ total: number; page: number; pageSize: number; users: UserListItem[] }>(
      `/users?${q.toString()}`,
    );
  },
  updateUserRole: (id: number, role: "USER" | "TEACHER" | "ADMIN") =>
    request<{ user: UserListItem }>(`/users/${id}/role`, {
      method: "PUT",
      body: JSON.stringify({ role }),
    }),


  /**
   * Poll a submission until its verdict is no longer PENDING.
   * Returns the final submission. Calls onTick(pollCount, latest) each poll.
   * Resolves with the settled submission.
   */
  pollSubmission: async (
    id: number,
    opts: {
      intervalMs?: number;
      timeoutMs?: number;
      onTick?: (poll: number, submission: Submission | null) => void;
      signal?: AbortSignal;
    } = {},
  ): Promise<Submission> => {
    const interval = opts.intervalMs ?? 1500;
    const timeout = opts.timeoutMs ?? 30000;
    const deadline = Date.now() + timeout;
    let poll = 0;
    log.info(TAGS.poll, `开始轮询提交 #${id}，间隔 ${interval}ms，超时 ${timeout}ms`);
    while (Date.now() < deadline) {
      throwIfAborted(opts.signal);
      poll++;
      const { submission } = await api.getSubmission(id, opts.signal);
      log.debug(TAGS.poll, `#${id} 第 ${poll} 次轮询 verdict=${submission.verdict}`);
      opts.onTick?.(poll, submission);
      if (submission.verdict !== "PENDING" && submission.verdict !== "JUDGING") {
        log.info(TAGS.poll, `#${id} 评测完成 verdict=${submission.verdict} passed=${submission.passed}/${submission.total} timeMs=${submission.timeMs}`);
        return submission;
      }
      await abortableDelay(interval, opts.signal);
    }
    log.warn(TAGS.poll, `#${id} 轮询超时（${timeout}ms），仍为 PENDING`);
    throwIfAborted(opts.signal);
    const { submission } = await api.getSubmission(id, opts.signal);
    return submission;
  },
};

function throwIfAborted(signal?: AbortSignal) {
  if (signal?.aborted) throw new DOMException("Polling aborted", "AbortError");
}

function abortableDelay(milliseconds: number, signal?: AbortSignal) {
  if (!signal) return new Promise<void>((resolve) => setTimeout(resolve, milliseconds));
  throwIfAborted(signal);
  return new Promise<void>((resolve, reject) => {
    const timeout = window.setTimeout(() => {
      signal.removeEventListener("abort", onAbort);
      resolve();
    }, milliseconds);
    const onAbort = () => {
      window.clearTimeout(timeout);
      signal.removeEventListener("abort", onAbort);
      reject(new DOMException("Polling aborted", "AbortError"));
    };
    signal.addEventListener("abort", onAbort, { once: true });
  });
}
