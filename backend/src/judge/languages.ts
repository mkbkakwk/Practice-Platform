import { config } from "../config.js";

export interface LanguageDef {
  id: string;            // canonical id, e.g. "python"
  name: string;          // display name
  ext: string;           // file extension without dot
  /** Produce a single command (string) that compiles the source. Returns null if interpreted. */
  compile: (srcPath: string, outPath: string) => string | null;
  /** Produce the command that runs the compiled/interpreted program. */
  run: (srcPath: string, outPath: string) => string;
  /** Default code template shown in the editor. */
  template: string;
}

export const LANGUAGES: Record<string, LanguageDef> = {
  python: {
    id: "python",
    name: "Python 3",
    ext: "py",
    compile: () => null,
    run: (srcPath) => `python3 ${srcPath}`,
    template: `import sys

def main():
    data = sys.stdin.read().split()
    a, b = int(data[0]), int(data[1])
    print(a + b)

if __name__ == "__main__":
    main()
`,
  },
  javascript: {
    id: "javascript",
    name: "JavaScript (Node)",
    ext: "js",
    compile: () => null,
    run: (srcPath) => `node ${srcPath}`,
    template: `const main = () => {
  const data = require("fs").readFileSync(0, "utf8").toString().trim().split(/\\s+/);
  const a = parseInt(data[0], 10);
  const b = parseInt(data[1], 10);
  console.log(a + b);
};

main();
`,
  },
  cpp: {
    id: "cpp",
    name: "C++ 17 (g++)",
    ext: "cpp",
    compile: (srcPath, outPath) =>
      `g++ -std=c++17 -O2 -o ${outPath} ${srcPath}`,
    run: (_srcPath, outPath) => outPath,
    template: `#include <bits/stdc++.h>
using namespace std;

int main() {
    int a, b;
    cin >> a >> b;
    cout << a + b << endl;
    return 0;
}
`,
  },
  c: {
    id: "c",
    name: "C (gcc)",
    ext: "c",
    compile: (srcPath, outPath) => `gcc -std=c11 -O2 -o ${outPath} ${srcPath} -lm`,
    run: (_srcPath, outPath) => outPath,
    template: `#include <stdio.h>

int main(void) {
    int a, b;
    scanf("%d %d", &a, &b);
    printf("%d\\n", a + b);
    return 0;
}
`,
  },
  java: {
    id: "java",
    name: "Java (OpenJDK)",
    ext: "java",
    // Class is always named Main so the binary path is predictable.
    compile: (srcPath, _outPath) => `javac ${srcPath}`,
    run: (srcPath, _outPath) => {
      // Run from the directory containing the .class file, class name Main.
      const dir = srcPath.replace(/\/Main\.java$/, "");
      return `cd ${dir} && java Main`;
    },
    template: `import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int a = sc.nextInt();
        int b = sc.nextInt();
        System.out.println(a + b);
    }
}
`,
  },
};

export function getLanguage(id: string): LanguageDef | undefined {
  return LANGUAGES[id];
}

/** Build the workspace directory for a single run. */
export function runDir(id: string): string {
  return `${config.judgeWorkspace}/${id}`;
}
