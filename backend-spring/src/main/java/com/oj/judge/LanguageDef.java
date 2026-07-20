package com.oj.judge;

import java.util.List;

/**
 * Supported submission languages. Keep in sync with the Worker's copy.
 * The template is shown in the frontend editor as a starting point.
 */
public record LanguageDef(String id, String name, String ext, String template) {

    public static final List<LanguageDef> ALL = List.of(
        new LanguageDef("python",
            "Python 3", "py",
            "import sys\n\ndef main():\n    data = sys.stdin.read().split()\n    a, b = int(data[0]), int(data[1])\n    print(a + b)\n\nif __name__ == \"__main__\":\n    main()\n"),
        new LanguageDef("javascript",
            "JavaScript (Node)", "js",
            "const main = () => {\n  const data = require('fs').readFileSync(0, 'utf8').toString().trim().split(/\\s+/);\n  const a = parseInt(data[0], 10);\n  const b = parseInt(data[1], 10);\n  console.log(a + b);\n};\n\nmain();\n"),
        new LanguageDef("cpp",
            "C++ 17 (g++)", "cpp",
            "#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n    int a, b;\n    cin >> a >> b;\n    cout << a + b << endl;\n    return 0;\n}\n"),
        new LanguageDef("c",
            "C (gcc)", "c",
            "#include <stdio.h>\n\nint main(void) {\n    int a, b;\n    scanf(\"%d %d\", &a, &b);\n    printf(\"%d\\n\", a + b);\n    return 0;\n}\n"),
        new LanguageDef("java",
            "Java (OpenJDK)", "java",
            "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int a = sc.nextInt();\n        int b = sc.nextInt();\n        System.out.println(a + b);\n    }\n}\n")
    );

    public static boolean isSupported(String id) {
        return ALL.stream().anyMatch(l -> l.id().equals(id));
    }
}
