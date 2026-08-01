import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { Navbar } from "@/components/Navbar";
import { api, AUTH_EXPIRED_EVENT, setToken } from "@/lib/api";
import { AuthProvider } from "@/lib/auth";
import AdminUserList from "./AdminUserList";
import Login from "./Login";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("authentication UI behavior", () => {
  it("shows login failure without triggering the global expiry loop", async () => {
    const user = userEvent.setup();
    const expired = vi.fn();
    window.addEventListener(AUTH_EXPIRED_EVENT, expired);
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse(401, { error: "Invalid username or password" }),
    );

    render(
      <MemoryRouter initialEntries={["/login"]}>
        <AuthProvider>
          <Login />
        </AuthProvider>
      </MemoryRouter>,
    );

    await user.type(screen.getByLabelText("\u7528\u6237\u540d"), "alice");
    await user.type(screen.getByLabelText("\u5bc6\u7801"), "wrong-password");
    await user.click(screen.getByRole("button", { name: "\u767b\u5f55" }));

    expect(await screen.findByText("Invalid username or password")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(expired).not.toHaveBeenCalled();
    expect(window.location.hash).toBe("");
    window.removeEventListener(AUTH_EXPIRED_EVENT, expired);
  });

  it("does not render tokenVersion, tokens, or extra response fields in user UI", async () => {
    setToken("ui-secret-token");
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith("/api/auth/me")) {
        return jsonResponse(200, {
          user: {
            id: 7,
            username: "visible-admin",
            role: "ADMIN",
            solvedCount: 4,
            tokenVersion: 123456789,
            internalSecret: "profile-internal-secret",
          },
        });
      }
      if (url.includes("/api/users?")) {
        return jsonResponse(200, {
          total: 1,
          page: 1,
          pageSize: 100,
          users: [{
            id: 8,
            username: "listed-student",
            role: "USER",
            solvedCount: 1,
            createdAt: "2026-08-01T00:00:00Z",
            tokenVersion: 987654321,
            token: "listed-user-secret-token",
          }],
        });
      }
      throw new Error("Unexpected URL: " + url);
    });

    render(
      <MemoryRouter>
        <AuthProvider>
          <Navbar />
          <AdminUserList />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByText("visible-admin")).toBeInTheDocument();
    expect(await screen.findByText("listed-student")).toBeInTheDocument();
    expect(document.body).not.toHaveTextContent("ui-secret-token");
    expect(document.body).not.toHaveTextContent("listed-user-secret-token");
    expect(document.body).not.toHaveTextContent("profile-internal-secret");
    expect(document.body).not.toHaveTextContent("tokenVersion");
    expect(document.body).not.toHaveTextContent("123456789");
    expect(document.body).not.toHaveTextContent("987654321");
  });

  it("keeps the authenticated UI when an API call returns 403", async () => {
    setToken("ui-forbidden-token");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      jsonResponse(200, {
        user: { id: 9, username: "visible-user", role: "USER", solvedCount: 0 },
      }),
    );
    render(
      <MemoryRouter>
        <AuthProvider>
          <Navbar />
        </AuthProvider>
      </MemoryRouter>,
    );
    expect(await screen.findByText("visible-user")).toBeInTheDocument();

    fetchMock.mockResolvedValueOnce(jsonResponse(403, { error: "Forbidden" }));
    await api.listUsers().catch(() => undefined);

    expect(screen.getByText("visible-user")).toBeInTheDocument();
    expect(localStorage.getItem("oj_token")).toBe("ui-forbidden-token");
    expect(window.location.hash).toBe("");
  });
});
