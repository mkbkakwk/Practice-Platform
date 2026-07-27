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
   '[{"input":"1 2\n","output":"3\n"},{"input":"0 0\n","output":"0\n"}]',
   '[{"input":"1 2\n","output":"3\n"},{"input":"0 0\n","output":"0\n"},{"input":"-5 5\n","output":"0\n"},{"input":"1000000000 1000000000\n","output":"2000000000\n"},{"input":"-1000000000 -1000000000\n","output":"-2000000000\n"},{"input":"42 58\n","output":"100\n"},{"input":"-7 10\n","output":"3\n"}]')
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
   '[{"input":"10\n","output":"55\n"},{"input":"1\n","output":"1\n"}]',
   '[{"input":"10\n","output":"55\n"},{"input":"1\n","output":"1\n"},{"input":"100\n","output":"5050\n"},{"input":"1000\n","output":"500500\n"},{"input":"1000000000\n","output":"500000000500000000\n"},{"input":"2\n","output":"3\n"}]')
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
   '[{"input":"1\n","output":"1\n"},{"input":"10\n","output":"55\n"}]',
   '[{"input":"1\n","output":"1\n"},{"input":"2\n","output":"1\n"},{"input":"3\n","output":"2\n"},{"input":"10\n","output":"55\n"},{"input":"20\n","output":"6765\n"},{"input":"100\n","output":"687995182\n"},{"input":"1000000\n","output":"918091266\n"}]')
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
   '[{"input":"3\n2\n9\n17\n","output":"Yes\nNo\nYes\n"}]',
   '[{"input":"1\n1\n","output":"No\n"},{"input":"1\n2\n","output":"Yes\n"},{"input":"1\n3\n","output":"Yes\n"},{"input":"1\n4\n","output":"No\n"},{"input":"3\n2\n9\n17\n","output":"Yes\nNo\nYes\n"},{"input":"2\n999999999989\n1000000000000\n","output":"Yes\nNo\n"}]')
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
   '[{"input":"5\n1 -2 3 4 -1\n","output":"7\n"},{"input":"3\n-1 -2 -3\n","output":"-1\n"}]',
   '[{"input":"5\n1 -2 3 4 -1\n","output":"7\n"},{"input":"3\n-1 -2 -3\n","output":"-1\n"},{"input":"1\n5\n","output":"5\n"},{"input":"6\n-2 1 -3 4 -1 2 1 -5 4\n","output":"6\n"},{"input":"4\n-1 -2 -3 -4\n","output":"-1\n"},{"input":"8\n2 3 -8 7 -1 2 3 -1\n","output":"11\n"}]')
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
   '[{"input":"5\n3 1 4 1 5\n","output":"3\n"},{"input":"4\n4 3 2 1\n","output":"6\n"}]',
   '[{"input":"5\n3 1 4 1 5\n","output":"3\n"},{"input":"4\n4 3 2 1\n","output":"6\n"},{"input":"1\n1\n","output":"0\n"},{"input":"3\n1 2 3\n","output":"0\n"},{"input":"3\n3 2 1\n","output":"3\n"},{"input":"6\n5 4 3 2 1 0\n","output":"15\n"}]')
) AS t(slug, title, description, input_fmt, output_fmt, difficulty, time_limit, memory_limit, tags, samples, test_cases)
WHERE NOT EXISTS (SELECT 1 FROM "Problem" WHERE slug = 'count-inversions');

-- ============ Office 操作练习种子题目 ============
-- 每条独立 INSERT...SELECT WHERE NOT EXISTS，幂等可重跑。
-- options/answer/selected 统一存 TEXT(JSON 字符串)，避免 jsonb 类型映射问题。

INSERT INTO "OfficeQuestion" (app_type, category, difficulty, question_type, content, options, answer, explanation)
SELECT 'WORD', '文字排版', 'EASY', 'SINGLE_CHOICE',
       '在 Word 中，要将选中文本的格式快速复制到其他文本，应使用的工具是？',
       '["格式刷","粘贴板","样式窗格","替换"]',
       '0',
       '格式刷可复制格式到其他文本，双击格式刷可连续多次刷取格式，再次单击或按 Esc 退出。'
WHERE NOT EXISTS (SELECT 1 FROM "OfficeQuestion" WHERE content = '在 Word 中，要将选中文本的格式快速复制到其他文本，应使用的工具是？');

INSERT INTO "OfficeQuestion" (app_type, category, difficulty, question_type, content, options, answer, explanation)
SELECT 'WORD', '快捷键', 'EASY', 'SINGLE_CHOICE',
       'Word 中“保存”文档的默认快捷键是？',
       '["Ctrl+S","Ctrl+P","Ctrl+C","Ctrl+V"]',
       '0',
       'Ctrl+S 保存，Ctrl+P 打印，Ctrl+C 复制，Ctrl+V 粘贴。'
WHERE NOT EXISTS (SELECT 1 FROM "OfficeQuestion" WHERE content = 'Word 中“保存”文档的默认快捷键是？');

INSERT INTO "OfficeQuestion" (app_type, category, difficulty, question_type, content, options, answer, explanation)
SELECT 'WORD', '页面布局', 'MEDIUM', 'MULTI_CHOICE',
       '下列属于 Word“页面布局”选项卡中可设置的项目有？',
       '["页边距","纸张方向","分栏","行号","字体颜色"]',
       '0,1,2,3',
       '页边距、纸张方向、分栏、行号均在“页面布局”选项卡；字体颜色属于“开始”选项卡。'
WHERE NOT EXISTS (SELECT 1 FROM "OfficeQuestion" WHERE content = '下列属于 Word“页面布局”选项卡中可设置的项目有？');

INSERT INTO "OfficeQuestion" (app_type, category, difficulty, question_type, content, options, answer, explanation)
SELECT 'WORD', '邮件合并', 'HARD', 'TRUE_FALSE',
       'Word 的“邮件合并”功能可以批量生成录取通知书、成绩单等基于模板的个性化文档。',
       '["正确","错误"]',
       'T',
       '邮件合并可将主文档与数据源(如 Excel 表格)结合，批量生成个性化文档。'
WHERE NOT EXISTS (SELECT 1 FROM "OfficeQuestion" WHERE content = 'Word 的“邮件合并”功能可以批量生成录取通知书、成绩单等基于模板的个性化文档。');

INSERT INTO "OfficeQuestion" (app_type, category, difficulty, question_type, content, options, answer, explanation)
SELECT 'EXCEL', '公式函数', 'EASY', 'SINGLE_CHOICE',
       '在 Excel 中，求 B1:B10 单元格区域数值之和的公式是？',
       '["=SUM(B1:B10)","=ADD(B1:B10)","=TOTAL(B1:B10)","=B1+B10"]',
       '0',
       'SUM 是求和函数；Excel 没有 ADD/TOTAL 函数；=B1+B10 只能加首尾两个单元格。'
WHERE NOT EXISTS (SELECT 1 FROM "OfficeQuestion" WHERE content = '在 Excel 中，求 B1:B10 单元格区域数值之和的公式是？');

INSERT INTO "OfficeQuestion" (app_type, category, difficulty, question_type, content, options, answer, explanation)
SELECT 'EXCEL', '单元格引用', 'MEDIUM', 'SINGLE_CHOICE',
       'Excel 公式中，绝对引用 A1 单元格的正确写法是？',
       '["$A$1","A$1","$A1","A1"]',
       '0',
       '$A$1 是绝对引用(行列均锁定)；A$1 锁行不锁列；$A1 锁列不锁行；A1 是相对引用。'
WHERE NOT EXISTS (SELECT 1 FROM "OfficeQuestion" WHERE content = 'Excel 公式中，绝对引用 A1 单元格的正确写法是？');

INSERT INTO "OfficeQuestion" (app_type, category, difficulty, question_type, content, options, answer, explanation)
SELECT 'EXCEL', '常用函数', 'MEDIUM', 'MULTI_CHOICE',
       '下列 Excel 函数中，属于统计类函数的有？',
       '["AVERAGE","COUNT","VLOOKUP","MAX","MIN"]',
       '0,1,3,4',
       'AVERAGE/COUNT/MAX/MIN 是统计函数；VLOOKUP 是查找与引用函数。'
WHERE NOT EXISTS (SELECT 1 FROM "OfficeQuestion" WHERE content = '下列 Excel 函数中，属于统计类函数的有？');

INSERT INTO "OfficeQuestion" (app_type, category, difficulty, question_type, content, options, answer, explanation)
SELECT 'EXCEL', '数据透视表', 'HARD', 'TRUE_FALSE',
       'Excel 数据透视表修改了源数据后，会自动实时刷新结果，无需手动更新。',
       '["正确","错误"]',
       'F',
       '数据透视表不会自动刷新，需右键“刷新”或“数据”选项卡中手动更新。'
WHERE NOT EXISTS (SELECT 1 FROM "OfficeQuestion" WHERE content = 'Excel 数据透视表修改了源数据后，会自动实时刷新结果，无需手动更新。');

INSERT INTO "OfficeQuestion" (app_type, category, difficulty, question_type, content, options, answer, explanation)
SELECT 'PPT', '基础操作', 'EASY', 'SINGLE_CHOICE',
       'PowerPoint 中，从当前幻灯片开始放映的快捷键是？',
       '["F5","Shift+F5","Esc","F12"]',
       '1',
       'F5 从头放映；Shift+F5 从当前页开始放映；Esc 退出放映；F12 另存为。'
WHERE NOT EXISTS (SELECT 1 FROM "OfficeQuestion" WHERE content = 'PowerPoint 中，从当前幻灯片开始放映的快捷键是？');

INSERT INTO "OfficeQuestion" (app_type, category, difficulty, question_type, content, options, answer, explanation)
SELECT 'PPT', '动画', 'MEDIUM', 'MULTI_CHOICE',
       'PowerPoint 中动画类型包括下列哪些？',
       '["进入动画","强调动画","退出动画","动作路径","超链接"]',
       '0,1,2,3',
       '四大动画类型：进入、强调、退出、动作路径；超链接属于交互，不属于动画。'
WHERE NOT EXISTS (SELECT 1 FROM "OfficeQuestion" WHERE content = 'PowerPoint 中动画类型包括下列哪些？');

INSERT INTO "OfficeQuestion" (app_type, category, difficulty, question_type, content, options, answer, explanation)
SELECT 'PPT', '幻灯片母版', 'HARD', 'TRUE_FALSE',
       '在 PowerPoint 中修改幻灯片母版，会影响所有基于该母版的幻灯片样式。',
       '["正确","错误"]',
       'T',
       '母版是模板的基础，修改母版会统一应用到所有使用该母版的幻灯片，用于统一样式。'
WHERE NOT EXISTS (SELECT 1 FROM "OfficeQuestion" WHERE content = '在 PowerPoint 中修改幻灯片母版，会影响所有基于该母版的幻灯片样式。');
