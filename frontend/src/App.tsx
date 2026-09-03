import { HashRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "@/lib/auth";
import { Navbar } from "@/components/Navbar";
import { ErrorBoundary } from "@/components/ErrorBoundary";
import { AdminGuard, TeacherGuard } from "@/components/AdminGuard";
import SystemStatus from "@/pages/SystemStatus";
import ProblemList from "@/pages/ProblemList";
import ProblemDetail from "@/pages/ProblemDetail";
import Login from "@/pages/Login";
import Register from "@/pages/Register";
import Submissions from "@/pages/Submissions";
import Leaderboard from "@/pages/Leaderboard";
import AdminProblemList from "@/pages/AdminProblemList";
import ProblemForm from "@/pages/ProblemForm";
import ProblemEditPage from "@/pages/ProblemEditPage";
import OfficeList from "@/pages/OfficeList";
import OfficePractice from "@/pages/OfficePractice";
import OfficeAdminList from "@/pages/OfficeAdminList";
import OfficeQuestionForm from "@/pages/OfficeQuestionForm";
import OfficeDocList from "@/pages/OfficeDocList";
import OfficeDocExerciseDetail from "@/pages/OfficeDocExerciseDetail";
import OfficeDocForm from "@/pages/OfficeDocForm";
import OfficeDocManageList from "@/pages/OfficeDocManageList";
import OfficeDocReview from "@/pages/OfficeDocReview";
import OfficeDocReviewList from "@/pages/OfficeDocReviewList";
import AdminUserList from "@/pages/AdminUserList";
import ContestList from "@/pages/ContestList";
import ContestDetail from "@/pages/ContestDetail";
import ContestManage from "@/pages/ContestManage";
import ContestStandings from "@/pages/ContestStandings";
import ContestAnalytics from "@/pages/ContestAnalytics";
import { Toaster } from "sonner";

function Layout({ label, children }: { label?: string; children: React.ReactNode }) {
  return <div className="min-h-screen bg-white text-zinc-900"><Navbar /><ErrorBoundary label={label}>{children}</ErrorBoundary></div>;
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
            <Route path="/contests" element={<Layout label="比赛"><ContestList /></Layout>} />
            <Route path="/contests/:id" element={<Layout label="比赛详情"><ContestDetail /></Layout>} />
            <Route path="/contests/:id/standings" element={<Layout label="比赛排名"><ContestStandings /></Layout>} />
            <Route path="/office" element={<Layout label="Office 练习"><OfficeList /></Layout>} />
            <Route path="/office/:id" element={<Layout label="Office 练习"><OfficePractice /></Layout>} />
            <Route path="/office/docs" element={<Layout label="排版练习"><OfficeDocList /></Layout>} />
            <Route path="/office/docs/:id" element={<Layout label="排版练习"><OfficeDocExerciseDetail /></Layout>} />

            <Route path="/admin/problems" element={<Layout label="算法题管理"><TeacherGuard><AdminProblemList /></TeacherGuard></Layout>} />
            <Route path="/admin/problems/new" element={<Layout label="新建题目"><TeacherGuard><ProblemForm mode="create" /></TeacherGuard></Layout>} />
            <Route path="/admin/problems/:slug/edit" element={<Layout label="编辑题目"><TeacherGuard><ProblemEditPage /></TeacherGuard></Layout>} />
            <Route path="/admin/office" element={<Layout label="Office 题库管理"><TeacherGuard><OfficeAdminList /></TeacherGuard></Layout>} />
            <Route path="/admin/office/new" element={<Layout label="新建 Office 题目"><TeacherGuard><OfficeQuestionForm mode="create" /></TeacherGuard></Layout>} />
            <Route path="/admin/office/:id/edit" element={<Layout label="编辑 Office 题目"><TeacherGuard><OfficeQuestionForm mode="edit" /></TeacherGuard></Layout>} />
            <Route path="/admin/office-doc" element={<Layout label="排版练习管理"><TeacherGuard><OfficeDocManageList /></TeacherGuard></Layout>} />
            <Route path="/admin/office-doc/new" element={<Layout label="新建排版练习"><TeacherGuard><OfficeDocForm mode="create" /></TeacherGuard></Layout>} />
            <Route path="/admin/office-doc/:id/edit" element={<Layout label="编辑排版练习"><TeacherGuard><OfficeDocForm mode="edit" /></TeacherGuard></Layout>} />
            <Route path="/admin/office-doc/review-list" element={<Layout label="文档复核"><TeacherGuard><OfficeDocReviewList /></TeacherGuard></Layout>} />
            <Route path="/admin/office-doc/review/:id" element={<Layout label="文档复核"><TeacherGuard><OfficeDocReview /></TeacherGuard></Layout>} />
            <Route path="/admin/users" element={<Layout label="用户管理"><AdminGuard><AdminUserList /></AdminGuard></Layout>} />
            <Route path="/admin/system-status" element={<Layout label="系统状态"><AdminGuard><SystemStatus /></AdminGuard></Layout>} />
            <Route path="/admin/contests/new" element={<Layout label="创建比赛"><TeacherGuard><ContestManage /></TeacherGuard></Layout>} />
            <Route path="/admin/contests/:id" element={<Layout label="管理比赛"><TeacherGuard><ContestManage /></TeacherGuard></Layout>} />
            <Route path="/admin/contests/:id/analytics" element={<Layout label="比赛数据分析"><TeacherGuard><ContestAnalytics /></TeacherGuard></Layout>} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
          <Toaster richColors position="top-center" />
        </HashRouter>
      </AuthProvider>
    </ErrorBoundary>
  );
}
