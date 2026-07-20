import { Component, type ErrorInfo, type ReactNode } from "react";
import { AlertTriangle, RefreshCw, ChevronDown, ChevronUp, Home } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useState } from "react";

interface Props {
  children: ReactNode;
  /** 自定义错误标签，用于区分是哪个区域崩溃，例如"题目详情" */
  label?: string;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

/**
 * 捕获子组件树渲染期间的 JavaScript 错误，展示一个明确的错误页
 * （而不是空白页）。错误信息、组件栈、调用栈都可展开查看，方便排查。
 *
 * 注意：ErrorBoundary 只能捕获渲染、生命周期、事件处理之外的错误；
 * 异步错误（如 fetch 失败）需要在调用处 try/catch 并用日志打印。
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, error: null, errorInfo: null };

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    this.setState({ errorInfo });
    // 同时打到控制台，便于开发者工具查看完整栈
    console.error("[ErrorBoundary]", this.props.label ?? "render", error, errorInfo);
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null, errorInfo: null });
  };

  handleReload = () => {
    window.location.reload();
  };

  render() {
    if (!this.state.hasError) return this.props.children;
    return (
      <ErrorView
        label={this.props.label}
        error={this.state.error}
        errorInfo={this.state.errorInfo}
        onRetry={this.handleReset}
        onReload={this.handleReload}
      />
    );
  }
}

function ErrorView({
  label,
  error,
  errorInfo,
  onRetry,
  onReload,
}: {
  label?: string;
  error: Error | null;
  errorInfo: ErrorInfo | null;
  onRetry: () => void;
  onReload: () => void;
}) {
  const [showStack, setShowStack] = useState(false);
  const message = error?.message ?? "未知错误";
  const stack = error?.stack ?? "";
  const componentStack = errorInfo?.componentStack ?? "";

  return (
    <div className="flex min-h-[60vh] items-center justify-center px-4 py-10">
      <div className="w-full max-w-2xl rounded-lg border border-red-200 bg-red-50 p-6">
        <div className="flex items-start gap-3">
          <AlertTriangle className="mt-0.5 h-6 w-6 shrink-0 text-red-600" />
          <div className="min-w-0 flex-1">
            <h2 className="text-lg font-semibold text-red-900">
              {label ? `${label} 出错了` : "页面出错了"}
            </h2>
            <p className="mt-1 break-words text-sm text-red-700">{message}</p>

            <div className="mt-4 flex flex-wrap gap-2">
              <Button size="sm" variant="default" onClick={onRetry} className="gap-1.5">
                <RefreshCw className="h-3.5 w-3.5" /> 重试
              </Button>
              <Button size="sm" variant="outline" onClick={onReload} className="gap-1.5">
                <RefreshCw className="h-3.5 w-3.5" /> 刷新页面
              </Button>
              <a href="#/">
                <Button size="sm" variant="ghost" className="gap-1.5">
                  <Home className="h-3.5 w-3.5" /> 回首页
                </Button>
              </a>
              <Button
                size="sm"
                variant="ghost"
                onClick={() => setShowStack((s) => !s)}
                className="gap-1.5"
              >
                {showStack ? (
                  <>
                    <ChevronUp className="h-3.5 w-3.5" /> 收起详情
                  </>
                ) : (
                  <>
                    <ChevronDown className="h-3.5 w-3.5" /> 查看详情
                  </>
                )}
              </Button>
            </div>

            {showStack && (
              <div className="mt-4 space-y-3">
                {stack && (
                  <div>
                    <p className="mb-1 text-xs font-medium text-red-800">错误堆栈</p>
                    <pre className="max-h-48 overflow-auto rounded bg-white p-3 text-xs text-red-900">
                      {stack}
                    </pre>
                  </div>
                )}
                {componentStack && (
                  <div>
                    <p className="mb-1 text-xs font-medium text-red-800">组件树</p>
                    <pre className="max-h-48 overflow-auto rounded bg-white p-3 text-xs text-red-900">
                      {componentStack}
                    </pre>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
