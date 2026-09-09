import { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2, Loader2, RefreshCw, XCircle } from "lucide-react";
import { api, getApiErrorMessage, type SystemStatus as Status } from "@/lib/api";
import { Button } from "@/components/ui/button";

const names: Record<string, string> = {
  backend: "后端", postgresql: "PostgreSQL", rabbitmq: "RabbitMQ", worker: "Worker", runner: "Runner",
};

function State({ value }: { value: string }) {
  const style = value === "UP" ? "text-success" : value === "DOWN" ? "text-danger" : "text-warning";
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

  if (loading && !status) return <div className="py-20 text-center"><Loader2 className="mx-auto h-6 w-6 animate-spin text-muted-foreground" /></div>;
  return <main className="admin-page">
    <header className="management-hero"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="management-kicker">平台健康 · Read-only operations</p><h1 className="management-title">系统状态</h1><p className="management-summary">只读运行证据；不会执行重启、清理或队列变更。</p></div><Button variant="outline" onClick={() => setRefresh((value) => value + 1)} disabled={loading}><RefreshCw className={`mr-2 h-4 w-4 ${loading ? "animate-spin" : ""}`} />刷新</Button></div>
      {status && <div className="management-deck"><div className="management-deck-item"><p className="management-deck-label">总体状态</p><p className="management-deck-value">{Object.values(status.components).every((item) => item.status === "UP") ? "HEALTHY" : "ATTENTION"}</p></div><div className="management-deck-item"><p className="management-deck-label">已检查</p><p className="management-deck-value">{new Date(status.checkedAt).toLocaleString()}</p></div><div className="management-deck-item"><p className="management-deck-label">Flyway</p><p className="management-deck-value">V{status.version.flywayVersion}</p></div></div>}
    </header>
    {error && <p role="alert" className="mb-4 rounded border border-danger/25 bg-danger/5 p-3 text-sm text-danger">{error}</p>}
    {!status ? null : <>
      <section className="management-panel mt-6"><div className="management-panel-header"><div><h2 className="management-panel-title">服务健康</h2><p className="management-panel-description">组件状态和已有延迟读数。</p></div></div><div className="grid gap-px bg-border sm:grid-cols-2 lg:grid-cols-5">
        {Object.entries(status.components).map(([key, component]) => <div key={key} className="bg-card p-4"><p className="text-sm text-muted-foreground">{names[key] || key}</p><div className="mt-2 flex items-center justify-between"><State value={component.status} /><span className="font-mono text-xs text-muted-foreground">{component.latencyMs} ms</span></div></div>)}
      </div></section>
      <section className="management-layout"><div className="management-main"><section className="management-panel"><div className="management-panel-header"><div><h2 className="management-panel-title">异步工作</h2><p className="management-panel-description">队列与 Outbox 的现有只读快照。</p></div></div><dl className="grid grid-cols-2 gap-px bg-border sm:grid-cols-3">{[["主队列",status.queues.main],["重试队列",status.queues.retry],["DLQ",status.queues.dlq],["Outbox 非终态",status.outbox.nonterminal ?? "UNKNOWN"],["发布器",status.outbox.publisherRunning ? "轮询中" : "空闲"],["最近失败",status.outbox.lastFailure]].map(([label,value]) => <div key={String(label)} className="bg-card p-4"><dt className="text-xs text-muted-foreground">{label}</dt><dd className="mt-1 break-words font-mono text-sm">{value}</dd></div>)}</dl></section></div><aside className="management-rail"><section className="management-panel"><div className="management-panel-header"><div><h2 className="management-panel-title">发布证据</h2></div></div><dl className="divide-y divide-border text-sm"><div className="p-4"><dt className="text-muted-foreground">Git SHA</dt><dd className="mt-1 break-all font-mono text-xs">{status.version.gitSha}</dd></div><div className="p-4"><dt className="text-muted-foreground">构建时间</dt><dd className="mt-1">{status.version.buildTime}</dd></div></dl></section></aside></section>
      <section className="management-panel mt-6"><div className="management-panel-header"><div><h2 className="management-panel-title">小型运行指标</h2><p className="management-panel-description">服务端已经提供的指标；不推断额外监控数据。</p></div></div><div className="grid grid-cols-2 gap-px bg-border sm:grid-cols-3">{Object.entries(status.metrics).map(([key, value]) => <div key={key} className="bg-card p-4"><p className="text-xs text-muted-foreground">{key}</p><p className="mt-1 font-mono text-sm font-medium">{value}</p></div>)}</div></section>
    </>}
  </main>;
}
