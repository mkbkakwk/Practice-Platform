/* Isolated Docker-built preview only, following visual-rollout.cjs.
 * All API calls are intercepted. Unknown origins, endpoints and writes fail closed.
 * Output is outside Git; no real accounts, services or credentials are used. */
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const origin = 'http://127.0.0.1:18443';
const output = path.join(os.tmpdir(), 'practice-platform-admin-visual-evidence');
const selectedWidths = process.env.VISUAL_WIDTHS ? process.env.VISUAL_WIDTHS.split(',').map(Number).filter(Number.isFinite) : [375, 768, 1280, 1440];
const selectedPages = process.env.VISUAL_PAGES ? new Set(process.env.VISUAL_PAGES.split(',').filter(Boolean)) : null;
const screenshotFullPage = process.env.VISUAL_VIEWPORT_ONLY !== '1';
const createdAt = '2026-09-01T08:30:00Z';
const user = { id: 9, username: '隔离测试管理员', role: 'ADMIN', solvedCount: 0 };
const problem = { id: 101, slug: 'fixture-sum', title: '两数之和 · 隔离视觉测试', difficulty: 'EASY', tags: ['基础'], timeLimit: 1000, memoryLimit: 256, contentVisibility: 'PUBLIC', createdBy: 9, creatorUsername: user.username, createdAt, visible: true, submissionCount: 2,
  description: '## 题目描述\n计算两个整数的和。\n\n使用标准输入输出。', inputFmt: '两个整数。', outputFmt: '一个整数。', samples: [{ input: '12 30', output: '42' }], testCases: [{ input: '1 2', output: '3' }] };
const problems = ['两数之和', '区间查询', '路径选择', '字符串匹配'].map((title, i) => ({ ...problem, id: 101 + i, title, slug: i ? `fixture-${i}` : problem.slug, difficulty: ['EASY', 'MEDIUM', 'HARD'][i % 3], visible: i !== 3 }));
const question = { id: 201, appType: 'WORD', category: '文字编辑', difficulty: 'EASY', questionType: 'SINGLE_CHOICE', content: '怎样保存当前正在编辑的文档？', options: ['使用保存命令', '关闭计算机'], answer: '0', explanation: '使用保存命令保留修改。', visible: true, contentVisibility: 'PUBLIC', createdBy: 9, creatorUsername: user.username, createdAt, submissionCount: 2 };
const exercise = { id: 301, title: '校园通知排版 · 隔离视觉测试', difficulty: 'EASY', description: '## 排版要求\n将标题居中，正文使用统一字体。', teacherDocName: 'fixture-reference.docx', starterDocName: 'fixture-starter.docx', hasTeacherDoc: true, hasStarterDoc: true, visible: true, contentVisibility: 'PUBLIC', createdBy: 9, creatorUsername: user.username, createdAt, submissionCount: 2 };
const submission = { id: 401, exerciseId: 301, userId: 12, studentDocName: 'fixture-answer.docx', status: 'NEEDS_REVIEW', score: 80, teacherComment: '', createdAt,
  compareResult: JSON.stringify([{ index: 0, studentText: '校园通知', teacherText: '校园通知', match: false, diffs: [{ label: '对齐方式', student: 'left', teacher: 'center', match: false }] }, { index: 1, studentText: '请按时参加活动。', teacherText: '请按时参加活动。', match: true, diffs: [] }]) };
const contest = { id: 7, title: '课堂练习赛 · 隔离视觉测试', description: '配置题目与参赛者，发布前核对时间。', status: 'DRAFT', phase: 'DRAFT', accessType: 'INVITE_ONLY', scoringMode: 'SCORE', ownerId: 9, ownerUsername: user.username, startAt: '2026-12-01T01:00:00Z', endAt: '2026-12-01T03:00:00Z', freezeAt: null, participant: false, createdAt, updatedAt: createdAt };
const contestProblems = problems.slice(0, 2).map((p, i) => ({ contestProblemId: 71 + i, problemType: 'ALGORITHM', problemId: p.id, displayOrder: i + 1, label: String.fromCharCode(65 + i), title: p.title, difficulty: p.difficulty, slug: p.slug, content: p }));
const analytics = { contestId: 7, title: contest.title, scoringMode: 'SCORE', phase: 'ENDED', generatedAt: createdAt,
  overview: { participantCount: 2, activeParticipantCount: 1, inactiveParticipantCount: 1, totalSubmissionCount: 3, algorithmSubmissionCount: 2, choiceSubmissionCount: 1, docxSubmissionCount: 0, averageTotalScore: 50, maxTotalScore: 100, fullScoreParticipantCount: 0, firstSubmissionAt: createdAt, lastSubmissionAt: createdAt },
  problems: [{ ...contestProblems[0], submissionCount: 3, uniqueSubmitterCount: 1, participationRate: 0.5, successParticipantCount: 1, successRate: 0.5, submissionAcceptanceRate: 0.333, infrastructureFailureCount: 0 }],
  timeline: [1, 2, 0].map((submissionCount, index) => ({ startAt: `2026-12-01T0${index + 1}:00:00Z`, endAt: `2026-12-01T0${index + 2}:00:00Z`, submissionCount })), distribution: [{ label: '0%', participantCount: 1 }, { label: '50%', participantCount: 1 }] };
const users = [user, { id: 10, username: '隔离测试教师', role: 'TEACHER', solvedCount: 1 }, { id: 12, username: '隔离测试学生', role: 'USER', solvedCount: 2 }];
const routes = [
  ['problems', '/admin/problems', '算法题管理', '两数之和'],
  ['problem-create', '/admin/problems/new', '新建题目'],
  ['problem-edit', '/admin/problems/fixture-sum/edit', `编辑：${problem.title}`],
  ['users', '/admin/users', '用户与角色', '隔离测试学生'],
  ['contest', '/admin/contests/7', contest.title, '参赛者（1）'],
  ['contest-create', '/admin/contests/new', '创建比赛'],
  ['analytics', '/admin/contests/7/analytics', contest.title, '隔离测试学生'],
  ['office-questions', '/admin/office', 'Office 选择题管理', question.content],
  ['office-question-form', '/admin/office/201/edit', '编辑 Office 题目'],
  ['office-documents', '/admin/office-doc', 'Office 排版练习', exercise.title],
  ['office-document-form', '/admin/office-doc/301/edit', '编辑排版练习', '发布就绪'],
  ['office-reviews', '/admin/office-doc/review-list', '文档提交复核', submission.studentDocName],
  ['office-review', '/admin/office-doc/review/401', '文档复核', '人工复核打分'],
  ['system-status', '/admin/system-status', '系统状态', '异步工作'],
];

async function main() {
  await fs.mkdir(output, { recursive: true });
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const results = [];
  try {
    for (const width of selectedWidths) {
      const context = await browser.newContext({ viewport: { width, height: 960 }, locale: 'zh-CN', reducedMotion: 'reduce', serviceWorkers: 'block' });
      await context.addInitScript(() => localStorage.setItem('oj_token', 'isolated-preview-not-a-real-token'));
      const unexpected = [], errors = [];
      await context.route('**/*', async (route) => {
        const req = route.request(), url = new URL(req.url());
        if (url.origin !== origin) { unexpected.push(url.origin); return route.abort(); }
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (req.method() !== 'GET') { unexpected.push(`${req.method()} ${url.pathname}`); return route.abort(); }
        const respond = (data) => route.fulfill({ contentType: 'application/json', body: JSON.stringify(data) });
        const paged = (key, rows) => ({ [key]: rows, page: 1, pageSize: 50, total: rows.length });
        switch (url.pathname) {
          case '/api/auth/me': return respond({ user });
          case '/api/problems/manage': return respond(paged('problems', problems));
          case '/api/problems/fixture-sum': return respond({ problem });
          case '/api/users': return respond(paged('users', users));
          case '/api/office/questions/manage': return respond(paged('questions', [question]));
          case '/api/office/questions/201': return respond({ question });
          case '/api/office/docs/exercises/manage': return respond(paged('exercises', [exercise]));
          case '/api/office/docs/exercises/301': return respond({ exercise });
          case '/api/office/docs/submissions': return respond(paged('submissions', [submission]));
          case '/api/office/docs/submissions/401/review-detail': return respond({ submission });
          case '/api/contests/7': return respond({ detail: { contest, problems: contestProblems } });
          case '/api/contests/7/participants': return respond(paged('participants', [{ id: 1, contestId: 7, userId: 12, username: users[2].username, joinedAt: createdAt }]));
          case '/api/contests/students': return respond(paged('students', [users[2]]));
          case '/api/contests/7/analytics': return respond({ analytics });
          case '/api/contests/7/analytics/participants': return respond({ participants: paged('participants', [{ userId: 12, username: users[2].username, rank: 1, totalSubmissionCount: 3, submittedProblemCount: 1, successfulProblemCount: 1, lastSubmissionAt: createdAt, totalScore: 100 }]) });
          case '/api/admin/system-status': return respond({ checkedAt: createdAt, version: { gitSha: 'isolated-fixture', buildTime: createdAt, flywayVersion: '9' }, components: Object.fromEntries(['backend', 'postgresql', 'rabbitmq', 'worker', 'runner'].map((key) => [key, { status: 'UP', latencyMs: 2 }])), queues: { main: 0, retry: 0, dlq: 0 }, outbox: { nonterminal: 0, publisherRunning: false, lastFailure: 'NONE' }, metrics: { httpRequests: 3 } });
          default: unexpected.push(url.pathname); return route.abort();
        }
      });
      const page = await context.newPage();
      page.on('pageerror', (error) => errors.push(error.message));
      for (const [name, route, heading, readyText] of routes.filter(([name]) => !selectedPages || selectedPages.has(name))) {
        await page.goto(`${origin}/#${route}`);
        await page.getByRole('heading', { name: heading, exact: true }).waitFor();
        if (readyText) await page.getByText(readyText, { exact: true }).first().waitFor();
        if (name === 'contest') await page.getByRole('button', { name: '已加入', exact: true }).waitFor();
        assert.equal(await page.locator('.admin-theme').first().evaluate((el) => getComputedStyle(el).backgroundColor), 'rgb(10, 12, 16)');
        const dimensions = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
        assert(dimensions.document <= dimensions.viewport, `${name}/${width} overflow ${JSON.stringify(dimensions)}`);
        const focus = page.locator('.admin-page button:enabled, .admin-page a').first();
        await page.keyboard.press('Tab'); await focus.focus();
        assert.equal(await focus.evaluate((el) => getComputedStyle(el).outlineStyle), 'solid', `${name} focus`);
        assert.equal(await focus.evaluate((el) => getComputedStyle(el).transitionDuration), '1e-05s');
        await focus.evaluate((el) => el.blur());
        if (name === 'contest') {
          await page.getByRole('button', { name: '取消比赛', exact: true }).click();
          const dialog = page.getByRole('alertdialog');
          await dialog.waitFor();
          assert(await dialog.getByText(/不可恢复/).isVisible());
          assert.equal(await dialog.evaluate((el) => getComputedStyle(el).backgroundColor), 'rgb(10, 12, 16)');
          await page.screenshot({ path: path.join(output, `confirmation-${width}.png`) });
          await dialog.getByRole('button', { name: '返回检查' }).click();
        }
        for (const region of await page.locator('.admin-table-scroll').all()) {
          assert.equal(await region.getAttribute('tabindex'), '0');
          if (width === 375) {
            await region.focus(); await page.keyboard.press('ArrowRight');
            await page.waitForFunction((el) => el.scrollLeft > 0, await region.elementHandle());
            await region.evaluate((el) => { el.scrollLeft = 0; el.blur(); });
          }
        }
        await page.evaluate(() => scrollTo({ top: 0, behavior: 'instant' }));
        await page.screenshot({ path: path.join(output, `${name}-${width}.png`), fullPage: screenshotFullPage });
        results.push({ name, width, overflow: 'PASS', focus: 'PASS', reducedMotion: 'PASS' });
      }
      assert.deepEqual(unexpected, [], 'No external request or API write');
      assert.deepEqual(errors, [], 'No browser exception');
      await context.close();
    }
    await fs.writeFile(path.join(output, 'summary.json'), JSON.stringify({ source: 'ISOLATED FIXTURES ONLY', results, unexpectedRequests: 0, browserErrors: 0 }, null, 2));
    console.log(JSON.stringify({ evidence: output, checkedViews: results.length, result: 'PASS' }));
  } finally { await browser.close(); }
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
