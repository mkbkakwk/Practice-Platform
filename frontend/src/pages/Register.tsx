import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { ApiError } from "@/lib/api";
import { Loader2 } from "lucide-react";

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<"USER" | "TEACHER">("USER");
  const [err, setErr] = useState("");
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErr("");
    setLoading(true);
    try {
      await register(username.trim(), password, role);
      navigate("/");
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "注册失败");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-[calc(100vh-3.5rem)] items-center justify-center bg-zinc-50 px-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>注册</CardTitle>
          <CardDescription>创建账号开始练习算法题</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="username">用户名</Label>
              <Input
                id="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="3-20 位字母数字下划线"
                autoComplete="username"
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">密码</Label>
              <Input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="至少 6 位"
                autoComplete="new-password"
                required
              />
            </div>
            <div className="space-y-2">
              <Label>注册身份</Label>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => setRole("USER")}
                  className={`rounded-md border p-3 text-left text-sm transition-colors ${
                    role === "USER" ? "border-zinc-900 bg-zinc-50" : "border-zinc-200 hover:bg-zinc-50"
                  }`}
                >
                  <div className="font-medium">学生</div>
                  <div className="text-xs text-zinc-500">做题 + 上传文档练习</div>
                </button>
                <button
                  type="button"
                  onClick={() => setRole("TEACHER")}
                  className={`rounded-md border p-3 text-left text-sm transition-colors ${
                    role === "TEACHER" ? "border-zinc-900 bg-zinc-50" : "border-zinc-200 hover:bg-zinc-50"
                  }`}
                >
                  <div className="font-medium">老师</div>
                  <div className="text-xs text-zinc-500">出题 + 复核学生提交</div>
                </button>
              </div>
            </div>
            {err && <p className="text-sm text-red-600">{err}</p>}
            <Button type="submit" className="w-full" disabled={loading}>
              {loading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              注册
            </Button>
            <p className="text-center text-sm text-zinc-500">
              已有账号？{" "}
              <Link to="/login" className="font-medium text-zinc-900 underline">
                登录
              </Link>
            </p>
            <p className="text-center text-xs text-zinc-400">
              首位注册的用户将自动成为管理员
            </p>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
