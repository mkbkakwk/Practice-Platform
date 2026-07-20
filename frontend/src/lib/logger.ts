/**
 * 前端统一日志工具。
 * 用统一前缀 [OJ] 输出，方便在浏览器控制台的日志海里筛选定位。
 * 生产环境可关闭 debug 级别（这里默认全开，便于排查）。
 */

const PREFIX = "[OJ]";
const ENABLED = import.meta.env.DEV || true; // 生产也开，方便排查

function fmt(tag: string, msg: string): string {
  return `${PREFIX} ${tag} ${msg}`;
}

export const log = {
  debug(tag: string, msg: string, ...args: unknown[]) {
    if (!ENABLED) return;
    console.debug(fmt(tag, msg), ...args);
  },
  info(tag: string, msg: string, ...args: unknown[]) {
    if (!ENABLED) return;
    console.info(fmt(tag, msg), ...args);
  },
  warn(tag: string, msg: string, ...args: unknown[]) {
    console.warn(fmt(tag, msg), ...args);
  },
  /** 错误日志：始终输出，带红色前缀便于一眼定位 */
  error(tag: string, msg: string, ...args: unknown[]) {
    console.error(fmt(tag, msg), ...args);
  },
};

/** 常用 tag，避免散落字符串 */
export const TAGS = {
  api: "API",
  auth: "Auth",
  judge: "Judge",
  poll: "Poll",
  render: "Render",
  app: "App",
};
