import { afterEach, describe, expect, it, vi } from "vitest";
import { AUTH_EXPIRED_EVENT, api, getToken, setToken } from "./api";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function observeExpired() {
  const listener = vi.fn();
  window.addEventListener(AUTH_EXPIRED_EVENT, listener);
  return {
    listener,
    stop: () => window.removeEventListener(AUTH_EXPIRED_EVENT, listener),
  };
}

function createDeferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("authenticated API requests", () => {
  it("adds the bearer token to protected requests", async () => {
    setToken("protected-token");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(200, { total: 0, page: 1, pageSize: 100, users: [] }),
    );

    await api.listUsers({ pageSize: 100 });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const options = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(options.headers).toMatchObject({
      Authorization: "Bearer protected-token",
    });
  });

  it("clears the token and emits one event for a protected 401 without retrying", async () => {
    setToken("expired-token");
    const expired = observeExpired();
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(401, { error: "Session expired" }),
    );

    await expect(api.listUsers()).rejects.toMatchObject({ status: 401 });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(getToken()).toBeNull();
    expect(expired.listener).toHaveBeenCalledTimes(1);
    expired.stop();
  });

  it("deduplicates concurrent 401 responses for the same token", async () => {
    setToken("shared-expired-token");
    const expired = observeExpired();
    let release: (() => void) | undefined;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async () => {
      await gate;
      return jsonResponse(401, { error: "Session expired" });
    });

    const first = api.me();
    const second = api.listUsers();
    release?.();
    const results = await Promise.allSettled([first, second]);

    expect(results.every((result) => result.status === "rejected")).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(expired.listener).toHaveBeenCalledTimes(1);
    expect(getToken()).toBeNull();
    expired.stop();
  });

  it("does not clear a newly issued token when an older request returns 401", async () => {
    setToken("token-old");
    const expired = observeExpired();
    const deferred = createDeferred<Response>();
    const fetchMock = vi.spyOn(globalThis, "fetch").mockReturnValueOnce(deferred.promise);

    const pendingRequest = api.listUsers();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const options = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(options.headers).toMatchObject({
      Authorization: "Bearer token-old",
    });

    setToken("token-new");
    deferred.resolve(jsonResponse(401, { error: "Session expired" }));

    await expect(pendingRequest).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(getToken()).toBe("token-new");
    expect(expired.listener).not.toHaveBeenCalled();
    expect(window.location.hash).toBe("");
    expect(sessionStorage.getItem("oj_auth_notice")).toBeNull();
    expired.stop();
  });

  it("does not attach an old token or emit session expiry when login returns 401", async () => {
    setToken("existing-token");
    const expired = observeExpired();
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(401, { error: "Invalid credentials" }),
    );

    await expect(api.login("alice", "wrong-password")).rejects.toMatchObject({ status: 401 });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const options = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(options.headers).not.toHaveProperty("Authorization");
    expect(expired.listener).not.toHaveBeenCalled();
    expect(getToken()).toBe("existing-token");
    expect(window.location.hash).toBe("");
    expired.stop();
  });

  it("does not create a session-expiry loop for an unauthenticated public request", async () => {
    const expired = observeExpired();
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(401, { error: "Unauthorized" }),
    );

    await expect(api.getLanguages()).rejects.toMatchObject({ status: 401 });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(expired.listener).not.toHaveBeenCalled();
    expect(getToken()).toBeNull();
    expired.stop();
  });

  it("preserves the token and emits no expiry event for 403", async () => {
    setToken("allowed-token");
    const expired = observeExpired();
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(403, { error: "Forbidden" }),
    );

    await expect(api.listUsers()).rejects.toMatchObject({ status: 403 });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(getToken()).toBe("allowed-token");
    expect(expired.listener).not.toHaveBeenCalled();
    expired.stop();
  });
});

describe("protected document downloads", () => {
  it("uses fetch with Authorization and completes the Blob download", async () => {
    setToken("download-token");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("docx", { status: 200 }),
    );
    const createObjectURL = vi.fn(() => "blob:practice-platform-test");
    const revokeObjectURL = vi.fn();
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      value: createObjectURL,
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      value: revokeObjectURL,
    });
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);

    await api.downloadTeacherDoc(42, "reference.docx");

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/office/docs/exercises/42/teacher-doc");
    const options = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(options.headers).toMatchObject({
      Authorization: "Bearer download-token",
    });
    expect(createObjectURL).toHaveBeenCalledTimes(1);
    expect(click).toHaveBeenCalledTimes(1);
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:practice-platform-test");
    expect(document.querySelector("a")).toBeNull();
  });

  it("uses the unified expiry flow when a protected download returns 401", async () => {
    setToken("expired-download-token");
    const expired = observeExpired();
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(401, { error: "Download unauthorized" }),
    );

    await expect(api.downloadStudentDoc(9, "student.docx")).rejects.toMatchObject({ status: 401 });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(getToken()).toBeNull();
    expect(expired.listener).toHaveBeenCalledTimes(1);
    expired.stop();
  });

  it("keeps the session when a protected download returns 403", async () => {
    setToken("download-forbidden-token");
    const expired = observeExpired();
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(403, { error: "Forbidden" }),
    );

    await expect(api.downloadStudentDoc(10, "student.docx")).rejects.toMatchObject({ status: 403 });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(getToken()).toBe("download-forbidden-token");
    expect(expired.listener).not.toHaveBeenCalled();
    expired.stop();
  });
});

describe("submission polling lifecycle", () => {
  it("stops without another request when its signal is already aborted", async () => {
    const controller = new AbortController();
    controller.abort();
    const fetchMock = vi.spyOn(globalThis, "fetch");

    await expect(api.pollSubmission(42, { signal: controller.signal })).rejects.toMatchObject({ name: "AbortError" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("cancels an in-flight polling delay when the page is left", async () => {
    const controller = new AbortController();
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(200, {
      submission: { id: 42, verdict: "PENDING", timeMs: 0, memoryKb: 0, passed: 0, total: 0, language: "python", code: "", createdAt: "2026-08-01T00:00:00Z" },
    }));

    const polling = api.pollSubmission(42, { intervalMs: 60_000, timeoutMs: 120_000, signal: controller.signal });
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    controller.abort();

    await expect(polling).rejects.toMatchObject({ name: "AbortError" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
