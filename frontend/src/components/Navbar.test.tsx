import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Navbar } from "./Navbar";

const auth = vi.hoisted(() => ({
  user: { id: 2, username: "teacher", role: "TEACHER" as const } as { id: number; username: string; role: "USER" | "TEACHER" | "ADMIN" } | null,
  logout: vi.fn(),
}));

vi.mock("@/lib/auth", () => ({
  useAuth: () => ({ user: auth.user, logout: auth.logout, loading: false }),
}));

describe("responsive navbar", () => {
  beforeEach(() => {
    auth.user = { id: 2, username: "teacher", role: "TEACHER" };
    auth.logout.mockReset();
  });

  it("offers an accessible mobile trigger and teacher navigation", async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><Navbar /></MemoryRouter>);
    await user.click(screen.getByRole("button", { name: "打开导航菜单" }));
    const mobile = await screen.findByRole("navigation", { name: "移动端导航" });
    expect(within(mobile).getByRole("link", { name: /题库/ })).toBeInTheDocument();
    expect(within(mobile).getByRole("link", { name: /内容管理/ })).toBeInTheDocument();
    expect(within(mobile).getByRole("link", { name: /复核/ })).toBeInTheDocument();
    expect(within(mobile).queryByRole("link", { name: /用户/ })).not.toBeInTheDocument();
  });

  it("keeps tablet widths on the compact navigation until the extra-large breakpoint", () => {
    render(<MemoryRouter><Navbar /></MemoryRouter>);

    const desktop = screen.getByRole("navigation", { name: "主导航" });
    const trigger = screen.getByRole("button", { name: "打开导航菜单" });

    expect(desktop).toHaveClass("hidden", "xl:flex");
    expect(desktop).not.toHaveClass("md:flex");
    expect(trigger).toHaveClass("xl:hidden");
    expect(trigger).not.toHaveClass("md:hidden");
  });

  it("offers the complete student navigation without management links", async () => {
    auth.user = { id: 10, username: "student", role: "USER" };
    const user = userEvent.setup();
    render(<MemoryRouter><Navbar /></MemoryRouter>);
    await user.click(screen.getByRole("button", { name: "打开导航菜单" }));
    const mobile = await screen.findByRole("navigation", { name: "移动端导航" });
    for (const label of ["题库", "Office", "比赛", "提交记录", "排行榜"]) {
      expect(within(mobile).getByRole("link", { name: new RegExp(label) })).toBeInTheDocument();
    }
    expect(within(mobile).queryByRole("link", { name: /内容管理/ })).not.toBeInTheDocument();
    expect(within(mobile).queryByRole("link", { name: /用户管理/ })).not.toBeInTheDocument();
  });

  it("adds user management for an admin and keeps logout in the menu footer", async () => {
    auth.user = { id: 1, username: "admin", role: "ADMIN" };
    const user = userEvent.setup();
    render(<MemoryRouter><Navbar /></MemoryRouter>);
    await user.click(screen.getByRole("button", { name: "打开导航菜单" }));
    const mobile = await screen.findByRole("navigation", { name: "移动端导航" });
    expect(within(mobile).getByRole("link", { name: /用户/ })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "退出" }));
    expect(auth.logout).toHaveBeenCalledTimes(1);
  });
});
