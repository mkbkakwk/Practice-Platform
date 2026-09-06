/* Isolated browser acceptance: Docker-built preview only. All API calls are
 * intercepted; unknown API calls, external origins and writes fail closed.
 * Screenshots remain outside Git. No Production services or credentials. */
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const origin = 'http://127.0.0.1:18443';
const output = process.env.ROLLOUT_EVIDENCE_DIR || path.join(os.tmpdir(), 'practice-platform-visual-rollout-evidence');
const createdAt = '2026-09-01T08:30:00Z';
const user = { id: 12, username: '隔离视觉测试学生', role: 'USER' };
const problem = { id: 101, slug: 'fixture-sum', title: '两数之和 · 隔离视觉测试', difficulty: 'EASY', tags: ['基础', '标准输入输出'], timeLimit: 1000, memoryLimit: 256, contentVisibility: 'PUBLIC', createdBy: null, creatorUsername: null, createdAt, visible: true, submissionCount: 0,
  description: '## 题目描述\n给定两个整数 **a** 和 **b**，计算它们的和。\n\n### 输入格式\n一行包含两个整数，以空格分隔。\n\n### 输出格式\n输出这两个整数的和。', samples: [{ input: '12 30', output: '42' }] };
const problems = ['两数之和', '区间查询', '路径选择', '字符串匹配', '顺序统计'].map((title, i) => ({ ...problem, id: 101 + i, title: `${title} · 隔离视觉测试`, difficulty: ['EASY', 'MEDIUM', 'HARD'][i % 3] }));
const questions = [{ id: 201, appType: 'WORD', category: '文字编辑', difficulty: 'EASY', questionType: 'SINGLE_CHOICE', content: '怎样保存当前正在编辑的文档？', options: ['使用保存命令', '直接关闭计算机', '删除文档内容'], contentVisibility: 'PUBLIC', createdBy: null, creatorUsername: null, createdAt }];
const exercise = { id: 301, title: '校园通知排版 · 隔离视觉测试', difficulty: 'EASY', description: '## 排版要求\n将标题居中，正文使用统一字体与段落间距。\n\n保留原有文字内容，完成后上传 DOCX 文档。', teacherDocName: 'fixture-reference.docx', starterDocName: 'fixture-starter.docx', hasTeacherDoc: true, hasStarterDoc: true, visible: true, contentVisibility: 'PUBLIC', createdBy: null, creatorUsername: null, createdAt };
const docSubmission = { id: 401, exerciseId: 301, userId: 12, studentDocName: 'fixture-answer.docx', status: 'REVIEWED', score: 100, teacherComment: '格式清晰，符合要求。', resultDetail: null, errorCategory: null, createdAt };
const submissions = ['AC', 'WA', 'TLE', 'RE', 'CE', 'PENDING', 'JUDGING', 'SE'].map((verdict, i) => ({ id: 501 + i, verdict, problem, user, language: 'python', timeMs: 24, memoryKb: 1024, passed: verdict === 'AC' ? 2 : 0, total: 2, createdAt }));
const routes = [
  ['problems', '/', '题库'], ['problem', '/problem/fixture-sum', problem.title],
  ['submissions', '/submissions', '提交记录'], ['office-list', '/office', 'Office 操作练习'],
  ['office-practice', '/office/201', null], ['office-docs', '/office/docs', '排版练习（文档上传）'],
  ['office-document', '/office/docs/301', exercise.title], ['login', '/login', '登录'], ['register', '/register', '注册'],
];

async function main() {
  await fs.mkdir(output, { recursive: true });
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const results = [];
  try {
    for (const width of [375, 768, 1280, 1440]) {
      const context = await browser.newContext({ viewport: { width, height: 960 }, locale: 'zh-CN', reducedMotion: 'reduce', serviceWorkers: 'block' });
      await context.addInitScript(() => {
        if (['#/login', '#/register'].includes(location.hash)) localStorage.removeItem('oj_token');
        else localStorage.setItem('oj_token', 'isolated-visual-fixture-not-a-real-token');
      });
      const unexpected = [], errors = [];
      let empty = false;
      await context.route('**/*', async (route) => {
        const req = route.request(), url = new URL(req.url());
        if (url.origin !== origin) { unexpected.push(url.origin); return route.abort(); }
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (req.method() !== 'GET') { unexpected.push(`${req.method()} ${url.pathname}`); return route.abort(); }
        const respond = (data) => route.fulfill({ contentType: 'application/json', body: JSON.stringify(data) });
        const page = Number(url.searchParams.get('page') || 1), pageSize = 20;
        switch (url.pathname) {
          case '/api/auth/me': return respond({ user });
          case '/api/problems': return respond({ problems: empty ? [] : problems.filter((p) => !url.searchParams.get('difficulty') || p.difficulty === url.searchParams.get('difficulty')), total: empty ? 0 : 25, page, pageSize });
          case '/api/problems/fixture-sum': return respond({ problem });
          case '/api/submissions/meta/languages': return respond({ languages: [{ id: 'python', name: 'Python', template: 'a, b = map(int, input().split())\nprint(a + b)\n' }] });
          case '/api/users/me/submissions': return respond({ submissions: empty ? [] : submissions, total: empty ? 0 : 8, page, pageSize });
          case '/api/office/questions': return respond({ questions: empty ? [] : questions, total: empty ? 0 : 1, page, pageSize });
          case '/api/office/questions/201': return respond({ question: questions[0] });
          case '/api/office/docs/exercises': return respond({ exercises: empty ? [] : [exercise], total: empty ? 0 : 1, page, pageSize });
          case '/api/office/docs/exercises/301': return respond({ exercise });
          case '/api/office/docs/submissions': return respond({ submissions: [{ id: docSubmission.id }], total: 1, page, pageSize });
          case '/api/office/docs/submissions/401': return respond({ submission: docSubmission });
          default: unexpected.push(url.pathname); return route.abort();
        }
      });
      const page = await context.newPage();
      page.on('pageerror', (e) => errors.push(e.message));
      for (const [name, hash, heading] of routes) {
        const authPage = name === 'login' || name === 'register';
        if (authPage) await page.evaluate(() => localStorage.removeItem('oj_token'));
        await page.goto(`${origin}/#${hash}`);
        if (authPage) await page.reload();
        if (heading) await page.getByRole('heading', { name: heading, exact: true }).waitFor();
        if (name === 'problems') await page.getByRole('link', { name: problems[0].title, exact: true }).waitFor();
        if (name === 'submissions') await page.getByTitle('AC', { exact: true }).waitFor();
        if (name === 'office-list') await page.getByRole('link', { name: questions[0].content }).waitFor();
        if (name === 'office-practice') {
          const option = page.getByRole('button', { name: /使用保存命令/ });
          await option.waitFor(); await option.click();
          assert.equal(await option.getAttribute('aria-pressed'), 'true');
        }
        if (name === 'office-docs') await page.getByRole('link', { name: exercise.title }).waitFor();
        if (name === 'problem') {
          await page.locator('.cm-editor').waitFor();
          await page.getByRole('combobox').click();
          await page.getByRole('listbox').waitFor();
          assert.equal(await page.locator('[data-slot="select-content"]').evaluate((el) => getComputedStyle(el).backgroundColor), 'rgb(22, 27, 36)');
          await page.keyboard.press('Escape');
          assert(await page.getByRole('button', { name: '运行样例' }).isDisabled(), 'No fake sample execution enabled');
        }
        const shell = page.locator('.graphite-theme').first();
        assert.equal(await shell.evaluate((el) => getComputedStyle(el).backgroundColor), 'rgb(10, 12, 16)');
        const dimensions = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
        assert(dimensions.document <= dimensions.viewport, `${name}/${width} overflow ${JSON.stringify(dimensions)}`);
        const focus = name === 'login' || name === 'register' ? page.getByLabel('用户名', { exact: true }) : page.locator('button:enabled').first();
        await page.keyboard.press('Tab'); await focus.focus();
        assert.equal(await focus.evaluate((el) => getComputedStyle(el).outlineStyle), 'solid', `${name} keyboard focus`);
        assert.equal(await focus.evaluate((el) => getComputedStyle(el).transitionDuration), '1e-05s');
        await focus.evaluate((el) => el.blur());
        await page.locator(':focus').evaluateAll((elements) => elements.forEach((el) => el.blur()));
        await page.evaluate(() => scrollTo({ top: 0, behavior: 'instant' }));
        await page.screenshot({ path: path.join(output, `${name}-${width}.png`), fullPage: true });
        results.push({ page: name, width, overflow: 'PASS', focus: 'PASS', reducedMotion: 'PASS' });
      }
      if (width === 1440) {
        empty = true;
        for (const [hash, label] of [['/', '暂无题目'], ['/submissions', '暂无提交记录'], ['/office', '暂无题目'], ['/office/docs', '暂无排版练习']]) {
          await page.goto(`${origin}/#${hash}`);
          await page.getByText(label, { exact: true }).waitFor();
        }
      }
      assert.deepEqual(unexpected, [], 'No external/API write/unrecognized request');
      assert.deepEqual(errors, [], 'No browser exceptions');
      await context.close();
    }
    await fs.writeFile(path.join(output, 'summary.json'), JSON.stringify({ source: 'ISOLATED FIXTURES ONLY', results, unexpectedRequests: 0, browserErrors: 0 }, null, 2));
    console.log(JSON.stringify({ evidence: output, checkedViews: results.length, result: 'PASS' }));
  } finally { await browser.close(); }
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
