import CodeMirror from "@uiw/react-codemirror";
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
}

export function CodeEditor({
  value,
  language,
  onChange,
  ariaLabel,
  height = "360px",
  readOnly = false,
}: CodeEditorProps) {
  return (
    <div className="overflow-hidden rounded-md border" data-testid="code-editor">
      <CodeMirror
        aria-label={ariaLabel}
        value={value}
        height={height}
        theme="light"
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
