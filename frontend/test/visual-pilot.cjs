/* Isolated browser QA only. Requires an operator-supplied Playwright installation.
 * Every API request is intercepted; fixture data is never shipped in the app.
 * Start a Docker-built Vite preview on 127.0.0.1:18443 before running this file.
 */
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');

const origin = 'http://127.0.0.1:18443';
const output = process.env.PILOT_EVIDENCE_DIR || path.join(os.tmpdir(), 'practice-platform-visual-pilot-evidence');
const user = { id: 12, username: '视觉测试学生', role: 'USER' };
const now = Date.now();
const iso = (offset) => new Date(now + offset).toISOString();
const contest = {
  id: 7, title: '校园算法挑战赛 · 视觉测试', description: '这是一组隔离的 UI 测试数据，不会写入 Production。\n\n请阅读题意，使用标准输入输出完成作答。',
  phase: 'RUNNING', status: 'PUBLISHED', accessType: 'OPEN', scoringMode: 'ICPC',
  ownerId: 1, ownerUsername: null, startAt: iso(-1800000), endAt: iso(5400000),
  freezeAt: null, participant: true, createdAt: iso(-86400000), updatedAt: iso(-86400000),
};
const problems = ['两数之和', '区间查询', '路径选择'].map((title, i) => ({
  contestProblemId: 71 + i, problemId: 1 + i, problemType: 'ALGORITHM', displayOrder: i,
  label: String.fromCharCode(65 + i), title, difficulty: 'EASY', slug: null,
  content: { description: '给定两个整数 **a** 和 **b**，计算它们的和。', inputFmt: '一行包含两个整数，以空格分隔。', outputFmt: '输出它们的和。', samples: [{ input: '12 30', output: '42' }] },
}));
const standing = {
  contestId: 7, phase: 'RUNNING', scoringMode: 'ICPC', frozen: false, managerView: false,
  freezeAt: null, generatedAt: iso(0),
  entries: Array.from({ length: 6 }, (_, i) => ({
    rank: i + 1, userId: 10 + i, username: i === 2 ? user.username : `测试参赛者 ${i + 1}`,
    totalScore: 0, solved: Math.max(0, 3 - i), penaltyMinutes: i < 3 ? 42 + 17 * i : 0,
    problems: problems.map((p, j) => ({ contestProblemId: p.contestProblemId, label: p.label,
      score: null, solved: j < 3 - i, attempts: j < 3 - i ? 1 : j === 0 ? 2 : 0,
      penaltyMinutes: j < 3 - i ? 14 + 17 * i : null })),
  })),
};

async function main() {
  await fs.mkdir(output, { recursive: true });
  const browser = await chromium.launch({ channel: process.env.PILOT_BROWSER_CHANNEL || 'chrome', headless: true });
  const results = [];
  try {
    for (const width of [375, 768, 1280, 1440]) {
      const context = await browser.newContext({ viewport: { width, height: 960 }, locale: 'zh-CN', reducedMotion: 'reduce', serviceWorkers: 'block' });
      await context.addInitScript(() => localStorage.setItem('oj_token', 'isolated-ui-fixture-not-a-real-token'));
      let mode = 'normal';
      const unexpected = [];
      const errors = [];
      await context.route('**/*', async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        if (url.origin !== origin) { unexpected.push(url.origin); return route.abort(); }
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (request.method() !== 'GET') { unexpected.push(`${request.method()} ${url.pathname}`); return route.abort(); }
        const respond = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) });
        if (url.pathname === '/api/auth/me') return respond({ user: width === 1280 ? { ...user, role: 'ADMIN' } : user });
        if (url.pathname === '/api/submissions/meta/languages') return respond({ languages: [{ id: 'python', name: 'Python', ext: '.py', template: 'a, b = map(int, input().split())\nprint(a + b)\n' }] });
        if (mode === 'error') return respond({ error: '无权查看比赛排名' }, 403);
        if (mode === 'loading') { await new Promise((resolve) => setTimeout(resolve, 1500)); }
        if (url.pathname === '/api/contests/7') return respond({ detail: { contest, problems } });
        if (url.pathname === '/api/contests/7/standings') {
          const data = structuredClone(standing);
          if (mode === 'empty') data.entries = [];
          if (mode === 'frozen') data.frozen = true;
          if (mode === 'score') { data.scoringMode = 'SCORE'; data.entries.forEach((e) => { e.totalScore = 37.5; e.problems.forEach((p) => { p.score = 12.5; }); }); }
          return respond({ standings: data });
        }
        unexpected.push(url.pathname);
        return respond({ error: 'Unrecognized isolated fixture request' }, 404);
      });
      const page = await context.newPage();
      page.on('pageerror', (error) => errors.push(error.message));
      async function snap(name) {
        const dimensions = await page.evaluate(() => ({ width: innerWidth, document: document.documentElement.scrollWidth }));
        assert(dimensions.document <= dimensions.width, `${name}: unexpected document overflow ${JSON.stringify(dimensions)}`);
        await page.screenshot({ path: path.join(output, `${name}-${width}.png`), fullPage: name !== 'navigation' });
      }
      await page.goto(`${origin}/#/contests/7`);
      await page.getByRole('heading', { name: contest.title }).waitFor();
      await page.locator('.cm-editor').waitFor();
      assert.equal(await page.locator('.graphite-theme').first().evaluate((el) => getComputedStyle(el).backgroundColor), 'rgb(10, 12, 16)');
      assert.equal(await page.locator('.pilot-running-dot').evaluate((el) => getComputedStyle(el).animationDuration), '1e-05s');
      const contrast = await page.locator('.graphite-theme').first().evaluate((root) => {
        const probe = document.createElement('span');
        // Read final token colors synchronously, without interpolating even .01ms.
        probe.style.setProperty('transition', 'none', 'important');
        root.append(probe);
        const rgb = (variable) => {
          probe.style.color = `hsl(var(--${variable}))`;
          return getComputedStyle(probe).color.match(/[\d.]+/g).slice(0, 3).map(Number);
        };
        const luminance = (value) => value.map((c) => { c /= 255; return c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4; }).reduce((sum, c, i) => sum + c * [.2126, .7152, .0722][i], 0);
        const ratio = (a, b) => (Math.max(luminance(a), luminance(b)) + .05) / (Math.min(luminance(a), luminance(b)) + .05);
        const card = rgb('card');
        const result = {};
        for (const token of ['foreground', 'text-secondary', 'muted-foreground', 'success', 'danger', 'warning', 'info', 'violet', 'rose', 'rank-gold', 'rank-silver', 'rank-bronze']) {
          const fg = rgb(token);
          const background = token.endsWith('foreground') || token === 'text-secondary' ? card : fg.map((channel, i) => channel * .1 + card[i] * .9);
          result[token] = ratio(fg, background);
        }
        for (const token of ['primary', 'destructive']) result[token] = ratio(rgb(`${token}-foreground`), rgb(token));
        probe.remove();
        return result;
      });
      for (const [token, value] of Object.entries(contrast)) assert(value >= 4.5, `${token}: text contrast ${value.toFixed(2)} is below 4.5`);
      await snap('contest');
      await page.getByRole('combobox').click();
      await page.getByRole('listbox').waitFor();
      assert.equal(await page.locator('[data-slot="select-content"]').evaluate((el) => getComputedStyle(el).backgroundColor), 'rgb(22, 27, 36)');
      await page.keyboard.press('Escape');
      if (width < 1280) {
        await page.getByRole('button', { name: '打开导航菜单' }).click();
        await page.getByRole('navigation', { name: '移动端导航' }).waitFor();
        await snap('navigation');
        await page.keyboard.press('Escape');
      }
      await page.getByRole('link', { name: /查看排名/ }).click();
      await page.getByRole('table').waitFor();
      assert.equal(await page.locator('[data-current-user="true"]').count(), 1);
      const region = page.getByRole('region', { name: '比赛排名表格' });
      await page.keyboard.press('Tab');
      await region.focus();
      assert.equal(await region.evaluate((el) => getComputedStyle(el).outlineStyle), 'solid');
      await region.evaluate((el) => el.blur());
      await snap('standings');
      if (width === 375) {
        assert(await region.evaluate((el) => el.scrollWidth > el.clientWidth));
        await page.keyboard.press('ArrowRight');
      }
      if (width === 1440) {
        await page.emulateMedia({ reducedMotion: 'no-preference' });
        assert.equal(await page.locator('.pilot-running-dot').evaluate((el) => getComputedStyle(el).animationDuration), '2.4s');
        await page.emulateMedia({ reducedMotion: 'reduce' });
        for (const state of ['frozen', 'score', 'empty', 'error', 'loading']) {
          mode = state;
          await page.reload();
          if (state === 'error') await page.getByRole('alert').waitFor();
          else if (state === 'loading') await page.getByRole('status', { name: '加载比赛排名' }).waitFor();
          else await page.getByRole('table').waitFor();
          await snap(`standings-${state}`);
        }
      }
      assert.deepEqual(unexpected, [], 'All network requests must be local, supported GET fixtures');
      assert.deepEqual(errors, [], 'No browser runtime exceptions');
      results.push({ width, overflow: 'PASS', focus: 'PASS', reducedMotion: 'PASS', portals: 'PASS', currentUser: 'PASS', minimumTokenContrast: Number(Math.min(...Object.values(contrast)).toFixed(2)), pageErrors: 0, unexpectedRequests: 0 });
      await context.close();
    }
    await fs.writeFile(path.join(output, 'summary.json'), JSON.stringify({ source: 'ISOLATED UI FIXTURES — NOT PRODUCTION', results }, null, 2));
    console.log(JSON.stringify({ results, evidence: output }, null, 2));
  } finally { await browser.close(); }
}
main().catch((error) => { console.error(error.message); process.exitCode = 1; });
