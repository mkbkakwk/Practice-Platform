import { HashRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "@/lib/auth";
import { Navbar } from "@/components/Navbar";
import { ErrorBoundary } from "@/components/ErrorBoundary";
import ProblemList from "@/pages/ProblemList";
import ProblemDetail from "@/pages/ProblemDetail";
import Login from "@/pages/Login";
import Register from "@/pages/Register";
import Submissions from "@/pages/Submissions";
import Leaderboard from "@/pages/Leaderboard";

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
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </HashRouter>
      </AuthProvider>
    </ErrorBoundary>
  );
}
