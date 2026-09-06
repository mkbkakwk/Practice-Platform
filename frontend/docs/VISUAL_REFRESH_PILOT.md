# Graphite 视觉 Pilot 人工验收

本轮仅涉及前端展示。未访问 Production；未改 API、权限、计分、数据库、Compose 或发布镜像。分支来自 Integration，必须经人工 UI Review 后再决定是否合并，不自动扩展到其他页面。

## 视觉与范围

- 参考 HTML 只提取视觉语言，未复制 HTML、CDN、DOM 脚本或模拟业务。
- `src/graphite.css` 定义唯一 graphite 色阶，映射现有 shadcn CSS variables；Tailwind 使用 surface、elevated、subtle、brand、success 等语义角色。
- Navbar、ContestDetail、ContestStandings 显式启用 `graphite-theme dark`。其他页面保留原明亮背景，避免只换全局底色而破坏尚未改造的页面。
- Shared Button/Badge/Card、Markdown、SubmissionResultCard、OfficeJudgeResult 仅做兼容明暗主题的展示调整；CodeMirror 石墨主题为可选参数，原页面默认仍为 light。
- 品牌色只用于主要操作、焦点、选中题目和当前用户细小标记；前三名仅小尺寸 rank badge。
- UI 使用本地 system sans stack，数字和代码使用 system monospace / tabular-nums。无 Google Fonts、Material Symbols、Tailwind CDN 或新依赖。
- 常规过渡 150ms、淡入 180ms；RUNNING 小圆点 2.4s 轻微透明度变化。reduced-motion 取消非必要动画和过渡。没有摇晃、光晕、粒子或整页滑动。

## 保留的数据合同

- ContestDetail 原有 phase 刷新、服务端时间显示、参赛判断、草稿、语言选择、提交/下载/轮询与取消逻辑保留。
- 倒计时来自现有 `startAt` / `endAt`；不决定服务端是否允许提交。
- Standings 使用服务端给出的 entry 顺序、rank、score、solved、penalty；不重新排序或计算。当前用户按 user ID 匹配，不按用户名。
- ICPC 未通过的尝试仍显示“次数”，不推断为 WA；SCORE 的部分得分不推断为 AC。封榜和管理员榜单提示保留。
- 所有 Verdict 有可读文字，所有 phase 有文字；不只靠颜色表达状态。
- Reference-only 的 Rating、通知/搜索、公告、First Solve、假在线人数、假倒计时、语言运行环境统计、Worker telemetry、导出和本地 IDE 均未实现。

## 验证与隔离边界

前端 lint、Vitest 和 production build 使用现有 Docker test target，不依赖宿主机项目构建：

```bash
COMPOSE_DISABLE_ENV_FILE=1 docker compose -p practice-platform-visual-pilot-test -f docker-compose.test.yml build frontend-test
COMPOSE_DISABLE_ENV_FILE=1 docker compose -p practice-platform-visual-pilot-test -f docker-compose.test.yml run --rm --no-deps frontend-test
```

完整仓库测试沿用 PR 的 Docker CI：`.github/workflows/ci.yml` → `scripts/test-docker.sh`，未修改测试门禁。

`src/pages/ContestVisualPilot.test.tsx` 补充 ID 匹配、服务端排名/分数保留、匿名视图、loading/empty/error 和非纯色状态信息的回归断言。既有 auth、SCORE/ICPC、封榜、Office redaction、提交轮询测试保持。

`test/visual-pilot.cjs` 是隔离浏览器检查，不属于应用 bundle。它需要环境中已有 Playwright + Chrome（可用 `PILOT_BROWSER_CHANNEL` 选择已安装 channel），不为运行它新增应用依赖。

先在独立本地容器中构建和预览：

```bash
docker run --detach --rm --name practice-platform-visual-pilot-preview --label purpose=frontend-visual-pilot --publish 127.0.0.1:18443:4173 practice-platform-visual-pilot-test-frontend-test sh -c 'npm run build && npm run preview -- --host 0.0.0.0 --port 4173'
node frontend/test/visual-pilot.cjs
```

这不是 Production 启动命令。浏览器仅允许 `127.0.0.1:18443`；全部 API 由测试拦截为明确标记的隔离 fixture，未知 API、外部网络请求和非 GET API 均拒绝。不会调用 Production，不创建真实业务数据。fixture 只用于布局和状态测试，不代表真实参赛人数或业务验收数据。

脚本输出 375、768、1280（Admin 导航）、1440px 截图和结构化结果到系统临时目录 `practice-platform-visual-pilot-evidence`，也可通过 `PILOT_EVIDENCE_DIR` 指定 Git 外的位置。检查页面 overflow、表格独立滚动、焦点、reduced-motion、Radix portal、当前用户和浏览器异常；覆盖 ICPC、SCORE、封榜、空、加载、权限错误视图。

完成检查后只停止本次专用预览容器，不使用全局 prune：

```bash
docker stop practice-platform-visual-pilot-preview
```

## 人工 Review（尚待确认）

- 石墨黑是否克制，层级和分隔线是否清晰？
- 中文字体、题意阅读、代码编辑和数字是否舒服？
- 375px 导航与按钮是否易用；排名表横向滚动是否直观？
- 当前用户是否容易找到，但没有大面积紫色？
- AC、未解决次数、分数、封榜信息是否保持明确？
- 是否仍像原来的 Practice-Platform，而不是新产品或游戏 HUD？

通过本轮小范围人工验收后，其他页面的推广需另行决定。回退本轮 UI 可通过撤销此独立前端提交完成；不涉及数据库或 Production 回滚。
