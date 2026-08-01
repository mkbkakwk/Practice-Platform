import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AUTH_EXPIRED_EVENT, api, getToken, setToken } from "./api";
import { AuthProvider, useAuth } from "./auth";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function AuthProbe() {
  const { user, loading, login } = useAuth();
  return (
    <div>
      <span data-testid="loading">{loading ? "loading" : "ready"}</span>
      <span data-testid="username">{user?.username ?? "anonymous"}</span>
      <span data-testid="role">{user?.role ?? "NONE"}</span>
      <button type="button" onClick={() => void login("alice", "new-password")}>
        sign-in
      </button>
    </div>
  );
}

describe("AuthProvider", () => {
  it("restores the current user from an initial token", async () => {
    setToken("restored-token");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(200, {
        user: { id: 1, username: "alice", role: "USER", solvedCount: 2 },
      }),
    );

    render(<AuthProvider><AuthProbe /></AuthProvider>);

    await waitFor(() => {
      expect(screen.getByTestId("username")).toHaveTextContent("alice");
    });
    expect(screen.getByTestId("role")).toHaveTextContent("USER");
    expect(screen.getByTestId("loading")).toHaveTextContent("ready");
    expect(getToken()).toBe("restored-token");
    const options = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(options.headers).toMatchObject({ Authorization: "Bearer restored-token" });
  });

  it("stores the token and current user after successful login", async () => {
    const user = userEvent.setup();
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(200, {
        token: "new-login-token",
        user: {
          id: 2,
          username: "alice",
          role: "TEACHER",
          solvedCount: 3,
          tokenVersion: 99,
          internalSecret: "must-not-render",
        },
      }),
    );

    render(<AuthProvider><AuthProbe /></AuthProvider>);
    await user.click(screen.getByRole("button", { name: "sign-in" }));

    await waitFor(() => {
      expect(screen.getByTestId("username")).toHaveTextContent("alice");
    });
    expect(screen.getByTestId("role")).toHaveTextContent("TEACHER");
    expect(getToken()).toBe("new-login-token");
    expect(document.body).not.toHaveTextContent("new-login-token");
    expect(document.body).not.toHaveTextContent("must-not-render");
    expect(document.body).not.toHaveTextContent("tokenVersion");
  });

  it("clears token and user state and enters the login flow on expiry", async () => {
    setToken("expired-context-token");
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(200, {
        user: { id: 3, username: "teacher", role: "TEACHER", solvedCount: 0 },
      }),
    );
    render(<AuthProvider><AuthProbe /></AuthProvider>);
    await waitFor(() => {
      expect(screen.getByTestId("username")).toHaveTextContent("teacher");
    });

    act(() => {
      window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT, {
        detail: { message: "Session expired" },
      }));
    });

    expect(getToken()).toBeNull();
    expect(screen.getByTestId("username")).toHaveTextContent("anonymous");
    expect(screen.getByTestId("role")).toHaveTextContent("NONE");
    expect(window.location.hash).toBe("#/login");
    expect(sessionStorage.getItem("oj_auth_notice")).toBe("Session expired");
  });

  it("drops the old role after a 401 and accepts the new role after login", async () => {
    setToken("old-role-token");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      jsonResponse(200, {
        user: { id: 4, username: "role-user", role: "TEACHER", solvedCount: 0 },
      }),
    );
    const user = userEvent.setup();
    render(<AuthProvider><AuthProbe /></AuthProvider>);
    await waitFor(() => {
      expect(screen.getByTestId("role")).toHaveTextContent("TEACHER");
    });

    fetchMock.mockResolvedValueOnce(jsonResponse(401, { error: "Session expired" }));
    await act(async () => {
      await api.listManageProblems().catch(() => undefined);
    });

    expect(screen.getByTestId("username")).toHaveTextContent("anonymous");
    expect(screen.getByTestId("role")).toHaveTextContent("NONE");
    expect(getToken()).toBeNull();
    expect(window.location.hash).toBe("#/login");

    fetchMock.mockResolvedValueOnce(jsonResponse(200, {
      token: "new-role-token",
      user: { id: 4, username: "role-user", role: "ADMIN", solvedCount: 0 },
    }));
    await user.click(screen.getByRole("button", { name: "sign-in" }));

    await waitFor(() => {
      expect(screen.getByTestId("role")).toHaveTextContent("ADMIN");
    });
    expect(screen.getByTestId("role")).not.toHaveTextContent("TEACHER");
    expect(getToken()).toBe("new-role-token");
  });

  it("processes concurrent 401 responses as one logout flow", async () => {
    setToken("concurrent-context-token");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      jsonResponse(200, {
        user: { id: 5, username: "parallel-user", role: "USER", solvedCount: 0 },
      }),
    );
    render(<AuthProvider><AuthProbe /></AuthProvider>);
    await waitFor(() => {
      expect(screen.getByTestId("username")).toHaveTextContent("parallel-user");
    });

    let release: (() => void) | undefined;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    fetchMock.mockImplementation(async () => {
      await gate;
      return jsonResponse(401, { error: "Session expired" });
    });
    const expired = vi.fn();
    window.addEventListener(AUTH_EXPIRED_EVENT, expired);

    await act(async () => {
      const first = api.me();
      const second = api.listUsers();
      release?.();
      await Promise.allSettled([first, second]);
    });

    expect(expired).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("username")).toHaveTextContent("anonymous");
    expect(getToken()).toBeNull();
    window.removeEventListener(AUTH_EXPIRED_EVENT, expired);
  });

  it("keeps token and user state after 403", async () => {
    setToken("forbidden-context-token");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      jsonResponse(200, {
        user: { id: 6, username: "student", role: "USER", solvedCount: 0 },
      }),
    );
    render(<AuthProvider><AuthProbe /></AuthProvider>);
    await waitFor(() => {
      expect(screen.getByTestId("username")).toHaveTextContent("student");
    });

    fetchMock.mockResolvedValueOnce(jsonResponse(403, { error: "Forbidden" }));
    await act(async () => {
      await api.listUsers().catch(() => undefined);
    });

    expect(screen.getByTestId("username")).toHaveTextContent("student");
    expect(screen.getByTestId("role")).toHaveTextContent("USER");
    expect(getToken()).toBe("forbidden-context-token");
    expect(window.location.hash).toBe("");
  });
});
