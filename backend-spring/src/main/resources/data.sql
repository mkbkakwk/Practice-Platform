-- Seed 6 classic algorithm problems. Only inserts when the table is empty.
-- Each statement is a single INSERT...SELECT guarded by NOT EXISTS so the
-- whole batch is idempotent and safe to re-run.

INSERT INTO "Problem" (slug, title, description, input_fmt, output_fmt, difficulty, time_limit, memory_limit, tags, samples, test_cases)
SELECT * FROM (VALUES
  ('a-plus-b', 'A + B 问题',
   '# A + B 问题

这是一道经典的入门题目，帮助你熟悉评测系统。

## 题目描述

给定两个整数 $a$ 和 $b$，请输出它们的和 $a + b$。

## 输入格式

一行，包含两个整数 $a$ 和 $b$，用空格分隔。

## 输出格式

一行，包含一个整数，表示 $a + b$ 的值。

## 数据范围

$-10^9 \le a, b \le 10^9$',
   '两个整数 $a$ 和 $b$，以空格分隔。', '一个整数 $a+b$。', 'EASY', 1000, 256,
   ARRAY['入门','模拟']::text[],
   '[{"input":"1 2\n","output":"3\n"},{"input":"0 0\n","output":"0\n"}]'::jsonb,
   '[{"input":"1 2\n","output":"3\n"},{"input":"0 0\n","output":"0\n"},{"input":"-5 5\n","output":"0\n"},{"input":"1000000000 1000000000\n","output":"2000000000\n"},{"input":"-1000000000 -1000000000\n","output":"-2000000000\n"},{"input":"42 58\n","output":"100\n"},{"input":"-7 10\n","output":"3\n"}]'::jsonb)
) AS t(slug, title, description, input_fmt, output_fmt, difficulty, time_limit, memory_limit, tags, samples, test_cases)
WHERE NOT EXISTS (SELECT 1 FROM "Problem");

INSERT INTO "Problem" (slug, title, description, input_fmt, output_fmt, difficulty, time_limit, memory_limit, tags, samples, test_cases)
SELECT * FROM (VALUES
  ('sum-1-to-n', '求 1 到 N 的和',
   '# 求 1 到 N 的 和

## 题目描述

给定一个正整数 $n$，求 $1 + 2 + 3 + \dots + n$ 的值。

## 输入格式

一行，一个正整数 $n$。

## 输出格式

一行，一个整数表示求和结果。

## 数据范围

$1 \le n \le 10^9$',
   '一个正整数 $n$。', '求和结果。', 'EASY', 1000, 256,
   ARRAY['入门','数学']::text[],
   '[{"input":"10\n","output":"55\n"},{"input":"1\n","output":"1\n"}]'::jsonb,
   '[{"input":"10\n","output":"55\n"},{"input":"1\n","output":"1\n"},{"input":"100\n","output":"5050\n"},{"input":"1000\n","output":"500500\n"},{"input":"1000000000\n","output":"500000000500000000\n"},{"input":"2\n","output":"3\n"}]'::jsonb)
) AS t(slug, title, description, input_fmt, output_fmt, difficulty, time_limit, memory_limit, tags, samples, test_cases)
WHERE NOT EXISTS (SELECT 1 FROM "Problem" WHERE slug = 'sum-1-to-n');

INSERT INTO "Problem" (slug, title, description, input_fmt, output_fmt, difficulty, time_limit, memory_limit, tags, samples, test_cases)
SELECT * FROM (VALUES
  ('fibonacci-mod', '斐波那契数列取模',
   '# 斐波那契数列取模

## 题目描述

定义斐波那契数列：$f_1 = 1, f_2 = 1, f_n = f_{n-1} + f_{n-2} \ (n \ge 3)$。

给定 $n$，求 $f_n \bmod 1000000007$ 的值。

## 数据范围

$1 \le n \le 10^6$',
   '一个正整数 $n$。', '$f_n \bmod 10^9+7$。', 'MEDIUM', 1000, 256,
   ARRAY['递推','动态规划']::text[],
   '[{"input":"1\n","output":"1\n"},{"input":"10\n","output":"55\n"}]'::jsonb,
   '[{"input":"1\n","output":"1\n"},{"input":"2\n","output":"1\n"},{"input":"3\n","output":"2\n"},{"input":"10\n","output":"55\n"},{"input":"20\n","output":"6765\n"},{"input":"100\n","output":"687995182\n"},{"input":"1000000\n","output":"918091266\n"}]'::jsonb)
) AS t(slug, title, description, input_fmt, output_fmt, difficulty, time_limit, memory_limit, tags, samples, test_cases)
WHERE NOT EXISTS (SELECT 1 FROM "Problem" WHERE slug = 'fibonacci-mod');

INSERT INTO "Problem" (slug, title, description, input_fmt, output_fmt, difficulty, time_limit, memory_limit, tags, samples, test_cases)
SELECT * FROM (VALUES
  ('is-prime', '判断质数',
   '# 判断质数

## 题目描述

给定 $n$ 个整数，依次判断每个数是否为质数。

## 数据范围

$1 \le n \le 10^5$，$1 \le x \le 10^{12}$',
   '第一行 $n$，接下来 $n$ 行各一个 $x$。', '$n$ 行 Yes/No。', 'EASY', 2000, 256,
   ARRAY['数学','质数']::text[],
   '[{"input":"3\n2\n9\n17\n","output":"Yes\nNo\nYes\n"}]'::jsonb,
   '[{"input":"1\n1\n","output":"No\n"},{"input":"1\n2\n","output":"Yes\n"},{"input":"1\n3\n","output":"Yes\n"},{"input":"1\n4\n","output":"No\n"},{"input":"3\n2\n9\n17\n","output":"Yes\nNo\nYes\n"},{"input":"2\n999999999989\n1000000000000\n","output":"Yes\nNo\n"}]'::jsonb)
) AS t(slug, title, description, input_fmt, output_fmt, difficulty, time_limit, memory_limit, tags, samples, test_cases)
WHERE NOT EXISTS (SELECT 1 FROM "Problem" WHERE slug = 'is-prime');

INSERT INTO "Problem" (slug, title, description, input_fmt, output_fmt, difficulty, time_limit, memory_limit, tags, samples, test_cases)
SELECT * FROM (VALUES
  ('max-subarray-sum', '最大子数组和',
   '# 最大子数组和

## 题目描述

给定一个长度为 $n$ 的整数序列，求一个连续子数组使得其元素之和最大。

## 数据范围

$1 \le n \le 10^5$，$-10^4 \le a_i \le 10^4$',
   '第一行 $n$，第二行 $n$ 个整数。', '最大子数组和。', 'MEDIUM', 1000, 256,
   ARRAY['动态规划','分治']::text[],
   '[{"input":"5\n1 -2 3 4 -1\n","output":"7\n"},{"input":"3\n-1 -2 -3\n","output":"-1\n"}]'::jsonb,
   '[{"input":"5\n1 -2 3 4 -1\n","output":"7\n"},{"input":"3\n-1 -2 -3\n","output":"-1\n"},{"input":"1\n5\n","output":"5\n"},{"input":"6\n-2 1 -3 4 -1 2 1 -5 4\n","output":"6\n"},{"input":"4\n-1 -2 -3 -4\n","output":"-1\n"},{"input":"8\n2 3 -8 7 -1 2 3 -1\n","output":"11\n"}]'::jsonb)
) AS t(slug, title, description, input_fmt, output_fmt, difficulty, time_limit, memory_limit, tags, samples, test_cases)
WHERE NOT EXISTS (SELECT 1 FROM "Problem" WHERE slug = 'max-subarray-sum');

INSERT INTO "Problem" (slug, title, description, input_fmt, output_fmt, difficulty, time_limit, memory_limit, tags, samples, test_cases)
SELECT * FROM (VALUES
  ('count-inversions', '逆序对计数',
   '# 逆序对计数

## 题目描述

统计整数序列中逆序对的个数。若 $i < j$ 且 $a_i > a_j$，则称 $(i, j)$ 为一个逆序对。

## 数据范围

$1 \le n \le 10^5$，$|a_i| \le 10^9$',
   '第一行 $n$，第二行 $n$ 个整数。', '逆序对数量。', 'HARD', 1500, 256,
   ARRAY['分治','归并排序']::text[],
   '[{"input":"5\n3 1 4 1 5\n","output":"3\n"},{"input":"4\n4 3 2 1\n","output":"6\n"}]'::jsonb,
   '[{"input":"5\n3 1 4 1 5\n","output":"3\n"},{"input":"4\n4 3 2 1\n","output":"6\n"},{"input":"1\n1\n","output":"0\n"},{"input":"3\n1 2 3\n","output":"0\n"},{"input":"3\n3 2 1\n","output":"3\n"},{"input":"6\n5 4 3 2 1 0\n","output":"15\n"}]'::jsonb)
) AS t(slug, title, description, input_fmt, output_fmt, difficulty, time_limit, memory_limit, tags, samples, test_cases)
WHERE NOT EXISTS (SELECT 1 FROM "Problem" WHERE slug = 'count-inversions');
