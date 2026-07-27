import { type ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "@/lib/auth";
import { Loader2 } from "lucide-react";

/**
 * 路由守卫：仅 ADMIN 角色可访问子内容。
 * 未登录或非管理员会被重定向到首页。
 */
export function AdminGuard({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-zinc-400" />
      </div>
    );
  }

  if (!user || user.role !== "ADMIN") {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
}

/**
 * 路由守卫：TEACHER 或 ADMIN 可访问（出题、复核等）。
 */
export function TeacherGuard({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-zinc-400" />
      </div>
    );
  }

  if (!user || (user.role !== "TEACHER" && user.role !== "ADMIN")) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
}
