import { prisma } from "./prisma.js";

interface Sample {
  input: string;
  output: string;
}

interface SeedProblem {
  slug: string;
  title: string;
  description: string;
  inputFmt: string;
  outputFmt: string;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  timeLimit: number;
  memoryLimit: number;
  tags: string[];
  samples: Sample[];
  testCases: Sample[];
}

const problems: SeedProblem[] = [
  {
    slug: "a-plus-b",
    title: "A + B 问题",
    description: `# A + B 问题

这是一道经典的入门题目，帮助你熟悉评测系统。

## 题目描述

给定两个整数 $a$ 和 $b$，请输出它们的和 $a + b$。

## 输入格式

一行，包含两个整数 $a$ 和 $b$，用空格分隔。

## 输出格式

一行，包含一个整数，表示 $a + b$ 的值。

## 数据范围

$-10^9 \\le a, b \\le 10^9$`,
    inputFmt: "两个整数 $a$ 和 $b$，以空格分隔。",
    outputFmt: "一个整数 $a+b$。",
    difficulty: "EASY",
    timeLimit: 1000,
    memoryLimit: 256,
    tags: ["入门", "模拟"],
    samples: [
      { input: "1 2\n", output: "3\n" },
      { input: "0 0\n", output: "0\n" },
    ],
    testCases: [
      { input: "1 2\n", output: "3\n" },
      { input: "0 0\n", output: "0\n" },
      { input: "-5 5\n", output: "0\n" },
      { input: "1000000000 1000000000\n", output: "2000000000\n" },
      { input: "-1000000000 -1000000000\n", output: "-2000000000\n" },
      { input: "42 58\n", output: "100\n" },
      { input: "-7 10\n", output: "3\n" },
    ],
  },
  {
    slug: "sum-1-to-n",
    title: "求 1 到 N 的和",
    description: `# 求 1 到 N 的和

## 题目描述

给定一个正整数 $n$，求 $1 + 2 + 3 + \\dots + n$ 的值。

## 输入格式

一行，一个正整数 $n$。

## 输出格式

一行，一个整数表示求和结果。

## 数据范围

$1 \\le n \\le 10^9$

## 提示

注意结果可能超过 32 位整型范围，请使用 64 位整型。`,
    inputFmt: "一个正整数 $n$。",
    outputFmt: "求和结果。",
    difficulty: "EASY",
    timeLimit: 1000,
    memoryLimit: 256,
    tags: ["入门", "数学"],
    samples: [
      { input: "10\n", output: "55\n" },
      { input: "1\n", output: "1\n" },
    ],
    testCases: [
      { input: "10\n", output: "55\n" },
      { input: "1\n", output: "1\n" },
      { input: "100\n", output: "5050\n" },
      { input: "1000\n", output: "500500\n" },
      { input: "1000000000\n", output: "500000000500000000\n" },
      { input: "2\n", output: "3\n" },
    ],
  },
  {
    slug: "fibonacci-mod",
    title: "斐波那契数列取模",
    description: `# 斐波那契数列取模

## 题目描述

定义斐波那契数列：$f_1 = 1, f_2 = 1, f_n = f_{n-1} + f_{n-2} \\ (n \\ge 3)$。

给定 $n$，求 $f_n \\bmod 1000000007$ 的值。

## 输入格式

一行，一个正整数 $n$。

## 输出格式

一行，一个整数表示 $f_n \\bmod 10^9+7$。

## 数据范围

$1 \\le n \\le 10^6$

## 提示

$n$ 较大时，请使用迭代而非递归以避免超时。`,
    inputFmt: "一个正整数 $n$。",
    outputFmt: "$f_n \\bmod 10^9+7$。",
    difficulty: "MEDIUM",
    timeLimit: 1000,
    memoryLimit: 256,
    tags: ["递推", "动态规划"],
    samples: [
      { input: "1\n", output: "1\n" },
      { input: "10\n", output: "55\n" },
    ],
    testCases: [
      { input: "1\n", output: "1\n" },
      { input: "2\n", output: "1\n" },
      { input: "3\n", output: "2\n" },
      { input: "10\n", output: "55\n" },
      { input: "20\n", output: "6765\n" },
      { input: "100\n", output: "687995182\n" },
      { input: "1000000\n", output: "918091266\n" },
    ],
  },
  {
    slug: "is-prime",
    title: "判断质数",
    description: `# 判断质数

## 题目描述

给定 $n$ 个整数，依次判断每个数是否为质数。

## 输入格式

第一行一个整数 $n$。
接下来 $n$ 行，每行一个正整数 $x$。

## 输出格式

共 $n$ 行，每行输出 \`Yes\` 或 \`No\` 表示该数是否为质数。

## 数据范围

$1 \\le n \\le 10^5$，$1 \\le x \\le 10^{12}$`,
    inputFmt: "第一行 $n$，接下来 $n$ 行各一个 $x$。",
    outputFmt: "$n$ 行 Yes/No。",
    difficulty: "EASY",
    timeLimit: 2000,
    memoryLimit: 256,
    tags: ["数学", "质数"],
    samples: [
      { input: "3\n2\n9\n17\n", output: "Yes\nNo\nYes\n" },
    ],
    testCases: [
      { input: "1\n1\n", output: "No\n" },
      { input: "1\n2\n", output: "Yes\n" },
      { input: "1\n3\n", output: "Yes\n" },
      { input: "1\n4\n", output: "No\n" },
      { input: "3\n2\n9\n17\n", output: "Yes\nNo\nYes\n" },
      { input: "2\n999999999989\n1000000000000\n", output: "Yes\nNo\n" },
    ],
  },
  {
    slug: "max-subarray-sum",
    title: "最大子数组和",
    description: `# 最大子数组和

## 题目描述

给定一个长度为 $n$ 的整数序列 $a_1, a_2, \\dots, a_n$，求一个连续子数组使得其元素之和最大。输出这个最大和。

## 输入格式

第一行一个整数 $n$。
第二行 $n$ 个整数 $a_1, a_2, \\dots, a_n$，以空格分隔。

## 输出格式

一行，一个整数表示最大子数组和。

## 数据范围

$1 \\le n \\le 10^5$，$-10^4 \\le a_i \\le 10^4$

## 提示

可使用 Kadane 算法在 $O(n)$ 时间内完成。`,
    inputFmt: "第一行 $n$，第二行 $n$ 个整数。",
    outputFmt: "最大子数组和。",
    difficulty: "MEDIUM",
    timeLimit: 1000,
    memoryLimit: 256,
    tags: ["动态规划", "分治"],
    samples: [
      { input: "5\n1 -2 3 4 -1\n", output: "7\n" },
      { input: "3\n-1 -2 -3\n", output: "-1\n" },
    ],
    testCases: [
      { input: "5\n1 -2 3 4 -1\n", output: "7\n" },
      { input: "3\n-1 -2 -3\n", output: "-1\n" },
      { input: "1\n5\n", output: "5\n" },
      { input: "6\n-2 1 -3 4 -1 2 1 -5 4\n", output: "6\n" },
      { input: "4\n-1 -2 -3 -4\n", output: "-1\n" },
      { input: "8\n2 3 -8 7 -1 2 3 -1\n", output: "11\n" },
    ],
  },
  {
    slug: "count-inversions",
    title: "逆序对计数",
    description: `# 逆序对计数

## 题目描述

给定一个长度为 $n$ 的整数序列 $a_1, a_2, \\dots, a_n$，统计其中逆序对的个数。

若 $i < j$ 且 $a_i > a_j$，则称 $(i, j)$ 为一个逆序对。

## 输入格式

第一行一个整数 $n$。
第二行 $n$ 个整数，以空格分隔。

## 输出格式

一行，一个整数表示逆序对的数量。

## 数据范围

$1 \\le n \\le 10^5$，$|a_i| \\le 10^9$

## 提示

使用归并排序可在 $O(n \\log n)$ 时间内完成。结果可能很大，使用 64 位整型。`,
    inputFmt: "第一行 $n$，第二行 $n$ 个整数。",
    outputFmt: "逆序对数量。",
    difficulty: "HARD",
    timeLimit: 1500,
    memoryLimit: 256,
    tags: ["分治", "归并排序"],
    samples: [
      { input: "5\n3 1 4 1 5\n", output: "3\n" },
      { input: "4\n4 3 2 1\n", output: "6\n" },
    ],
    testCases: [
      { input: "5\n3 1 4 1 5\n", output: "3\n" },
      { input: "4\n4 3 2 1\n", output: "6\n" },
      { input: "1\n1\n", output: "0\n" },
      { input: "3\n1 2 3\n", output: "0\n" },
      { input: "3\n3 2 1\n", output: "3\n" },
      { input: "6\n5 4 3 2 1 0\n", output: "15\n" },
    ],
  },
];

export async function seedIfEmpty(): Promise<void> {
  const count = await prisma.problem.count();
  if (count > 0) {
    console.log(`[seed] problems table already has ${count} rows, skipping seed.`);
    return;
  }
  console.log(`[seed] seeding ${problems.length} sample problems...`);
  for (const p of problems) {
    await prisma.problem.create({
      data: {
        slug: p.slug,
        title: p.title,
        description: p.description,
        inputFmt: p.inputFmt,
        outputFmt: p.outputFmt,
        difficulty: p.difficulty,
        timeLimit: p.timeLimit,
        memoryLimit: p.memoryLimit,
        tags: p.tags,
        samples: p.samples as unknown as object,
        testCases: p.testCases as unknown as object,
      },
    });
  }
  console.log("[seed] done.");
}

// Allow running directly: `npm run seed`
if (import.meta.url === `file://${process.argv[1]}`) {
  seedIfEmpty()
    .catch((e) => {
      console.error(e);
      process.exit(1);
    })
    .finally(() => prisma.$disconnect());
}
