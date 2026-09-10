import { useEffect } from "react";
import type { ReactNode } from "react";
import { ThemeProvider as NextThemesProvider, useTheme } from "next-themes";

const themeStorageKey = "practice-platform-theme";

function ResolvedThemeSynchronizer() {
  const { resolvedTheme } = useTheme();

  useEffect(() => {
    const root = document.documentElement;
    const resolved = resolvedTheme === "light" ? "light" : "dark";
    root.classList.toggle("dark", resolved === "dark");
    root.dataset.theme = resolved;
    root.style.colorScheme = resolved;
  }, [resolvedTheme]);

  return null;
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  return (
    <NextThemesProvider attribute="data-theme" defaultTheme="dark" enableSystem storageKey={themeStorageKey} disableTransitionOnChange>
      <ResolvedThemeSynchronizer />
      {children}
    </NextThemesProvider>
  );
}
