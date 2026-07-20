import { HashRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "@/lib/auth";
import { Navbar } from "@/components/Navbar";
import { ErrorBoundary } from "@/components/ErrorBoundary";
import { AdminGuard } from "@/components/AdminGuard";
import ProblemList from "@/pages/ProblemList";
import ProblemDetail from "@/pages/ProblemDetail";
import Login from "@/pages/Login";
import Register from "@/pages/Register";
import Submissions from "@/pages/Submissions";
import Leaderboard from "@/pages/Leaderboard";
import AdminProblemList from "@/pages/AdminProblemList";
import ProblemForm from "@/pages/ProblemForm";
import ProblemEditPage from "@/pages/ProblemEditPage";

function Layout({ label, children }: { label?: string; children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-white text-zinc-900">
      <Navbar />
      <ErrorBoundary label={label}>{children}</ErrorBoundary>
    </div>
  );
}

export default function App() {
  return (
    <ErrorBoundary label="应用">
      <AuthProvider>
        <HashRouter>
          <Routes>
            <Route path="/login" element={<Layout label="登录"><Login /></Layout>} />
            <Route path="/register" element={<Layout label="注册"><Register /></Layout>} />
            <Route path="/" element={<Layout label="题库"><ProblemList /></Layout>} />
            <Route path="/problem/:slug" element={<Layout label="题目详情"><ProblemDetail /></Layout>} />
            <Route path="/submissions" element={<Layout label="提交记录"><Submissions /></Layout>} />
            <Route path="/leaderboard" element={<Layout label="排行榜"><Leaderboard /></Layout>} />

            {/* Admin-only routes */}
            <Route path="/admin/problems" element={<Layout label="题目管理"><AdminGuard><AdminProblemList /></AdminGuard></Layout>} />
            <Route path="/admin/problems/new" element={<Layout label="新建题目"><AdminGuard><ProblemForm mode="create" /></AdminGuard></Layout>} />
            <Route path="/admin/problems/:slug/edit" element={<Layout label="编辑题目"><AdminGuard><ProblemEditPage /></AdminGuard></Layout>} />

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </HashRouter>
      </AuthProvider>
    </ErrorBoundary>
  );
}
