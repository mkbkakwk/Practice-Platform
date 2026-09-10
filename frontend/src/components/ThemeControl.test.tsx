import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ThemeControl } from "./ThemeControl";
import { ThemeProvider } from "./ThemeProvider";

let systemIsDark = true;
const listeners = new Set<(event: MediaQueryListEvent) => void>();

function emitSystemTheme(isDark: boolean) {
  systemIsDark = isDark;
  listeners.forEach((listener) => listener({ matches: isDark } as MediaQueryListEvent));
}

function renderControl() {
  return render(<ThemeProvider><ThemeControl /></ThemeProvider>);
}

describe("theme preference", () => {
  beforeEach(() => {
    systemIsDark = true;
    listeners.clear();
    localStorage.clear();
    document.documentElement.removeAttribute("data-theme");
    document.documentElement.classList.remove("dark");
    vi.stubGlobal("matchMedia", vi.fn().mockImplementation(() => ({
      get matches() { return systemIsDark; },
      addEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => listeners.add(listener),
      removeEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => listeners.delete(listener),
      addListener: (listener: (event: MediaQueryListEvent) => void) => listeners.add(listener),
      removeListener: (listener: (event: MediaQueryListEvent) => void) => listeners.delete(listener),
      media: "(prefers-color-scheme: dark)",
      onchange: null,
      dispatchEvent: () => true,
    })));
  });

  afterEach(() => vi.unstubAllGlobals());

  it("defaults to dark without persisting a preference", async () => {
    renderControl();
    await waitFor(() => expect(document.documentElement.dataset.theme).toBe("dark"));
    expect(localStorage.getItem("practice-platform-theme")).toBeNull();
    expect(screen.getByRole("button", { name: "切换主题，当前深色" })).toBeInTheDocument();
  });

  it("persists explicit light and dark selections through the accessible control", async () => {
    const user = userEvent.setup();
    renderControl();
    await user.click(screen.getByRole("button", { name: /切换主题/ }));
    await user.click(screen.getByRole("menuitemradio", { name: "浅色" }));
    await waitFor(() => expect(document.documentElement.dataset.theme).toBe("light"));
    expect(localStorage.getItem("practice-platform-theme")).toBe("light");

    await user.click(screen.getByRole("button", { name: /切换主题/ }));
    await user.click(screen.getByRole("menuitemradio", { name: "深色" }));
    await waitFor(() => expect(document.documentElement.dataset.theme).toBe("dark"));
    expect(localStorage.getItem("practice-platform-theme")).toBe("dark");
  });

  it("keeps the system preference while reacting to OS theme changes", async () => {
    systemIsDark = false;
    localStorage.setItem("practice-platform-theme", "system");
    renderControl();
    await waitFor(() => expect(document.documentElement.dataset.theme).toBe("light"));
    expect(localStorage.getItem("practice-platform-theme")).toBe("system");

    emitSystemTheme(true);
    await waitFor(() => expect(document.documentElement.dataset.theme).toBe("dark"));
    expect(localStorage.getItem("practice-platform-theme")).toBe("system");
  });
});
