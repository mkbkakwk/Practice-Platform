import CodeMirror, { EditorView } from "@uiw/react-codemirror";
import { cpp } from "@codemirror/lang-cpp";
import { java } from "@codemirror/lang-java";
import { javascript } from "@codemirror/lang-javascript";
import { python } from "@codemirror/lang-python";

interface CodeEditorProps {
  value: string;
  language: string;
  onChange: (value: string) => void;
  ariaLabel: string;
  height?: string;
  readOnly?: boolean;
  appearance?: "light" | "graphite";
}

// Contest and practice workspaces opt in; behavior and language extensions stay unchanged.
const graphiteEditorTheme = EditorView.theme({
  "&": { backgroundColor: "hsl(var(--surface-elevated))", color: "hsl(var(--foreground))" },
  ".cm-content": { caretColor: "hsl(var(--brand))", fontFamily: "ui-monospace, SFMono-Regular, Consolas, monospace", fontSize: "13px" },
  ".cm-gutters": { backgroundColor: "hsl(var(--surface-elevated))", color: "hsl(var(--muted-foreground))", borderRight: "none" },
  ".cm-activeLine, .cm-activeLineGutter": { backgroundColor: "hsl(var(--secondary))" },
  "&.cm-focused .cm-selectionBackground, .cm-selectionBackground, ::selection": { backgroundColor: "hsl(var(--brand) / .2)" },
  "&.cm-focused": { outline: "2px solid hsl(var(--ring))", outlineOffset: "-2px" },
}, { dark: true });

export function CodeEditor({
  value,
  language,
  onChange,
  ariaLabel,
  height = "360px",
  readOnly = false,
  appearance = "light",
}: CodeEditorProps) {
  return (
    <div className={appearance === "graphite" ? "overflow-hidden rounded-md" : "overflow-hidden rounded-md border"} data-testid="code-editor">
      <CodeMirror
        aria-label={ariaLabel}
        value={value}
        height={height}
        theme={appearance === "graphite" ? graphiteEditorTheme : "light"}
        extensions={languageExtensions(language)}
        onChange={onChange}
        readOnly={readOnly}
        basicSetup={{
          lineNumbers: true,
          highlightActiveLine: true,
          foldGutter: false,
        }}
      />
    </div>
  );
}

export function languageExtensions(language: string) {
  switch (language) {
    case "python":
      return [python()];
    case "javascript":
      return [javascript()];
    case "cpp":
    case "cpp17":
    case "c":
      return [cpp()];
    case "java":
      return [java()];
    default:
      return [];
  }
}
