import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { api, AUTH_EXPIRED_EVENT, getToken, setToken, type PublicUser } from "./api";

interface AuthState {
  user: PublicUser | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, password: string) => Promise<void>;
  logout: () => void;
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<PublicUser | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = async () => {
    const token = getToken();
    if (!token) {
      setUser(null);
      setLoading(false);
      return;
    }
    try {
      const { user } = await api.me();
      setUser(user);
    } catch {
      setToken(null);
      setUser(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const handleExpired = (event: Event) => {
      const message = (event as CustomEvent<{ message?: string }>).detail?.message
        || "登录状态已失效，请重新登录";
      setUser(null);
      setLoading(false);
      sessionStorage.setItem("oj_auth_notice", message);
      if (window.location.hash !== "#/login") {
        window.location.hash = "#/login";
      }
    };
    window.addEventListener(AUTH_EXPIRED_EVENT, handleExpired);
    refresh();
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, handleExpired);
  }, []);

  const login = async (username: string, password: string) => {
    const { token, user } = await api.login(username, password);
    setToken(token);
    setUser(user);
  };

  const register = async (username: string, password: string) => {
    const { token, user } = await api.register(username, password);
    setToken(token);
    setUser(user);
  };

  const logout = () => {
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
