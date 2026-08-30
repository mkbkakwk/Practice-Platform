import { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2, Loader2, RefreshCw, XCircle } from "lucide-react";
import { api, getApiErrorMessage, type SystemStatus as Status } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

const names: Record<string, string> = {
  backend: "后端", postgresql: "PostgreSQL", rabbitmq: "RabbitMQ", worker: "Worker", runner: "Runner",
};

function State({ value }: { value: string }) {
  const style = value === "UP" ? "text-emerald-700" : value === "DOWN" ? "text-red-700" : "text-amber-700";
  const Icon = value === "UP" ? CheckCircle2 : value === "DOWN" ? XCircle : AlertTriangle;
  return <span className={`inline-flex items-center gap-1 text-sm font-medium ${style}`}><Icon className="h-4 w-4" />{value}</span>;
}

export default function SystemStatus() {
  const [status, setStatus] = useState<Status | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    let live = true; const controller = new AbortController();
    setLoading(true);
    api.getSystemStatus(controller.signal).then((value) => {
      if (live) { setStatus(value); setError(""); }
    }).catch((reason) => {
      if (live) setError(getApiErrorMessage(reason, "系统状态加载失败"));
    }).finally(() => { if (live) setLoading(false); });
    return () => { live = false; controller.abort(); };
  }, [refresh]);

  if (loading && !status) return <div className="py-20 text-center"><Loader2 className="mx-auto h-6 w-6 animate-spin text-zinc-400" /></div>;
  return <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6">
    <div className="mb-6 flex flex-wrap items-start justify-between gap-3"><div><h1 className="text-2xl font-bold">系统状态</h1><p className="mt-1 text-sm text-zinc-500">只读运行证据；不会执行重启、清理或队列变更。</p></div><Button variant="outline" onClick={() => setRefresh((value) => value + 1)} disabled={loading}><RefreshCw className={`mr-2 h-4 w-4 ${loading ? "animate-spin" : ""}`} />刷新</Button></div>
    {error && <p role="alert" className="mb-4 rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    {!status ? null : <>
      <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {Object.entries(status.components).map(([key, component]) => <Card key={key} className="p-4"><p className="text-sm text-zinc-500">{names[key] || key}</p><div className="mt-2 flex items-center justify-between"><State value={component.status} /><span className="text-xs text-zinc-500">{component.latencyMs} ms</span></div></Card>)}
      </section>
      <section className="mt-6 grid gap-6 lg:grid-cols-2"><Card className="p-5"><h2 className="font-semibold">发布证据</h2><dl className="mt-3 grid gap-2 text-sm"><div><dt className="text-zinc-500">Git SHA</dt><dd className="break-all font-mono text-xs">{status.version.gitSha}</dd></div><div><dt className="text-zinc-500">构建时间</dt><dd>{status.version.buildTime}</dd></div><div><dt className="text-zinc-500">Flyway</dt><dd>V{status.version.flywayVersion}</dd></div></dl></Card>
      <Card className="p-5"><h2 className="font-semibold">异步工作</h2><dl className="mt-3 grid grid-cols-2 gap-3 text-sm"><div><dt className="text-zinc-500">主队列</dt><dd>{status.queues.main}</dd></div><div><dt className="text-zinc-500">重试队列</dt><dd>{status.queues.retry}</dd></div><div><dt className="text-zinc-500">DLQ</dt><dd>{status.queues.dlq}</dd></div><div><dt className="text-zinc-500">Outbox 非终态</dt><dd>{status.outbox.nonterminal ?? "UNKNOWN"}</dd></div><div><dt className="text-zinc-500">发布器</dt><dd>{status.outbox.publisherRunning ? "轮询中" : "空闲"}</dd></div><div><dt className="text-zinc-500">最近失败</dt><dd>{status.outbox.lastFailure}</dd></div></dl></Card></section>
      <section className="mt-6"><Card className="p-5"><h2 className="font-semibold">小型运行指标</h2><div className="mt-3 grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">{Object.entries(status.metrics).map(([key, value]) => <div key={key}><p className="text-xs text-zinc-500">{key}</p><p className="font-medium">{value}</p></div>)}</div><p className="mt-4 text-xs text-zinc-500">检查时间：{new Date(status.checkedAt).toLocaleString()}</p></Card></section>
    </>}
  </main>;
}
