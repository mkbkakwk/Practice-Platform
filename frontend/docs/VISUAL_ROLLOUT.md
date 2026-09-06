# Graphite 用户页推广 · 人工 Review

基于 Integration `5760bb7566c3c4656e6dc024d6f45df0f25da9bd`（Docker CI PASS），沿用 PR #47 已接受的 palette、surface、按钮、Verdict 和 reduced-motion；不另建配色体系。

## 范围

- 训练题库：保留难度过滤、分页、现有标签与资源限制；使用语义难度 badge、安静的表格分隔和可键盘聚焦的横向滚动区域。
- 普通题目：保持原请求、草稿、提交和轮询；复用 Graphite CodeMirror 外观，下拉 portal 同主题，样例不再独立套卡片。
- 提交记录：保留个人/管理员视图和服务端 verdict；不重新判断结果或重算分数。
- Office 用户页：选择题列表/练习、文档列表/练习；保留所有上传、下载、作答、复核入口及权限条件。
- 登录/注册：同一产品外观，保留表单字段、autocomplete、验证、认证与导航行为。

Navbar 和 Contest Pilot 不重新设计。Admin、排行榜、比赛列表等未列入本轮的页面不主动转成深色。共享难度 token 在原有浅色页面仍有对应语义值。

## 业务边界

没有新增 API、业务数据或功能。保留现有禁用的“运行样例”按钮，不实现虚构的本地执行。Office 中 Word / Excel / PPT 是已有选择题分类，不是新增文档判题引擎。没有 rating、推荐题、连续训练、遥测、SSO 或 MFA。

## 验证与隔离预览

沿用仓库 Docker `frontend-test` 执行 lint、Vitest 和 production build；GitHub 正常 Docker CI 执行完整仓库测试。新增回归测试保留所有既有断言，覆盖题库过滤/分页、提交结果、Office 选项/提交锁定、注册失败可访问反馈。

`frontend/test/visual-rollout.cjs` 使用 Docker-built Vite preview（仅 `127.0.0.1:18443`）及 operator 提供的 Playwright。运行 `node frontend/test/visual-rollout.cjs` 前需启动隔离 preview。脚本阻断所有外部 origin、未知 API 和非 GET API，只响应明确的隔离 fixture；不会请求 Production。它生成 375 / 768 / 1280 / 1440px 的 9 类页面截图，检查溢出、焦点、reduced-motion、portal，并复核空态。

截图及 JSON 摘要默认保存在操作系统临时目录 `practice-platform-visual-rollout-evidence`，不提交 Git；可通过 `ROLLOUT_EVIDENCE_DIR` 指定独立证据目录。

## 人工验收重点

- 中文正文长时间阅读是否舒适；普通训练是否比 Contest 更安静。
- 表格是否易于扫读；手机横向滚动是否可控。
- 编辑器、样例和 Office 文档操作是否属于同一产品。
- 登录表单、选中态、错误态和键盘焦点是否清楚。

仅供人工 UI Review。不要自动合并、发布或开始 Admin 视觉改造。
