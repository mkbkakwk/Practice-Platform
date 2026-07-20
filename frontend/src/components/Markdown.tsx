import ReactMarkdown from "react-markdown";
import remarkMath from "remark-math";
import remarkGfm from "remark-gfm";
import rehypeKatex from "rehype-katex";
import "katex/dist/katex.min.css";

/**
 * Markdown 渲染器：支持 GFM（表格/删除线/任务列表）+ LaTeX 数学公式。
 *  - 行内公式：$E=mc^2$
 *  - 块级公式：$$\sum_{i=1}^n i = \frac{n(n+1)}{2}$$
 *
 * 样式用 Tailwind 任意值选择器精准控制各元素，无需 typography 插件。
 * 代码块深色背景，行内代码浅色，表格带边框，公式块可横向滚动。
 */
export function Markdown({ children, className = "" }: { children: string; className?: string }) {
  return (
    <div
      className={
        "max-w-none " +
        // 标题
        "[&_h1]:mt-0 [&_h1]:mb-3 [&_h1]:text-xl [&_h1]:font-bold [&_h1]:text-zinc-900 " +
        "[&_h2]:mt-5 [&_h2]:mb-2 [&_h2]:text-lg [&_h2]:font-semibold [&_h2]:text-zinc-900 " +
        "[&_h3]:mt-4 [&_h3]:mb-1.5 [&_h3]:text-base [&_h3]:font-semibold [&_h3]:text-zinc-800 " +
        // 段落
        "[&_p]:my-2.5 [&_p]:leading-7 [&_p]:text-zinc-700 " +
        // 列表
        "[&_ul]:my-2.5 [&_ul]:list-disc [&_ul]:pl-5 [&_ul]:text-zinc-700 " +
        "[&_ol]:my-2.5 [&_ol]:list-decimal [&_ol]:pl-5 [&_ol]:text-zinc-700 " +
        "[&_li]:my-1 " +
        // 行内代码
        "[&_code]:rounded [&_code]:bg-zinc-100 [&_code]:px-1.5 [&_code]:py-0.5 [&_code]:text-[13px] [&_code]:font-mono [&_code]:text-pink-600 " +
        // 代码块
        "[&_pre]:my-3 [&_pre]:rounded-md [&_pre]:bg-zinc-900 [&_pre]:p-3 [&_pre]:overflow-x-auto " +
        "[&_pre_code]:bg-transparent [&_pre_code]:p-0 [&_pre_code]:text-[13px] [&_pre_code]:text-zinc-100 [&_pre_code]:leading-6 " +
        // 引用
        "[&_blockquote]:my-3 [&_blockquote]:border-l-4 [&_blockquote]:border-zinc-300 [&_blockquote]:bg-zinc-50 [&_blockquote]:py-1 [&_blockquote]:pl-3 [&_blockquote]:text-zinc-600 " +
        // 链接
        "[&_a]:text-blue-600 [&_a]:underline [&_a]:underline-offset-2 " +
        // 表格
        "[&_table]:my-3 [&_table]:w-full [&_table]:border-collapse [&_table]:text-sm " +
        "[&_th]:border [&_th]:border-zinc-300 [&_th]:px-2.5 [&_th]:py-1.5 [&_th]:bg-zinc-50 [&_th]:text-left [&_th]:font-semibold [&_th]:text-zinc-800 " +
        "[&_td]:border [&_td]:border-zinc-300 [&_td]:px-2.5 [&_td]:py-1.5 [&_td]:text-zinc-700 " +
        // 分隔线
        "[&_hr]:my-4 [&_hr]:border-zinc-200 " +
        // 图片
        "[&_img]:max-w-full [&_img]:rounded " +
        // KaTeX 公式块：横向滚动防溢出
        "[_.katex-display]:my-3 [&_.katex-display]:overflow-x-auto [&_.katex-display]:overflow-y-hidden " +
        className
      }
    >
      <ReactMarkdown
        remarkPlugins={[remarkMath, remarkGfm]}
        rehypePlugins={[rehypeKatex]}
      >
        {children}
      </ReactMarkdown>
    </div>
  );
}
