# Practice-Platform 校园编程与 Office 实践平台

面向学校机房、计算机课堂和校园竞赛的练习平台：教师组织题目与比赛，学生提交程序或 Office 文档，管理员维护账号、权限和运行环境。

## 当前状态

| 项目 | 已接受基线 |
| --- | --- |
| 稳定版本 | **v0.9.1** |
| 发布源码 | `f273f3be519019841f050275d2b04bc2f4406797` |
| 数据库 | Flyway V9 |
| 小范围 Production Pilot | 已通过 |
| 常规课堂／校园使用 | 已完成受控试点验收，可开展日常使用 |
| 大规模并发与负载容量 | **尚未认证**；大型同时比赛前必须另做压力测试 |

这是已验收的版本基线，不代表任意配置的新服务器自动获得同样验收结果。

## 核心功能

- **编程练习与评测**：题目、测试用例、提交记录；Worker 调度远程 Runner，在 Docker 沙箱中执行程序并持久化判定结果。
- **比赛**：比赛配置、题目关联、参与、成绩与榜单。
- **教学分析**：基于真实比赛和提交数据的 Analytics，受角色与资源权限约束。
- **Office 实践**：教师布置 DOCX 练习，学生提交文档，完成评阅、结果查看和授权下载。
- **运维与安全**：角色权限、健康／就绪检查、队列与 Outbox 观测、数据库与 Office 配对备份。

## 架构概览

```text
校园浏览器 → 校园入口反向代理 → Frontend → Backend → PostgreSQL
                                             │
                                          RabbitMQ
                                             │
                                           Worker → Runner → 临时评测沙箱
```

正式部署包含 PostgreSQL、RabbitMQ、Backend、Worker、Runner、Frontend 六个服务。**只有 Runner 可以挂载 Docker socket**；学生不直接访问数据库、消息队列或评测服务。Office 文档使用独立持久卷，不能仅备份数据库。

## 谁来使用

| 角色 | 主要工作 |
| --- | --- |
| Admin | 管理账号与角色、查看系统状态、组织运维 |
| Teacher | 创建题目、Office 练习和比赛，查看教学结果 |
| Student（源码角色 `USER`） | 注册／登录、参与练习和比赛、查看自己的提交 |

## 快速本地体验：仅限开发／评估

准备 Git、Docker 和 Docker Compose 插件。在**没有 Production 的独立开发电脑或虚拟机**运行：

```bash
git clone https://github.com/mkbkakwk/Practice-Platform.git
cd Practice-Platform
cp .env.example .env
# 编辑 .env：替换 JWT_SECRET、RUNNER_TOKEN，并确认本机 Docker socket GID。
docker compose -f docker-compose.yml --profile sandbox-images build \
  sandbox-python-image sandbox-javascript-image sandbox-c-image sandbox-cpp-image sandbox-java-image
docker compose -f docker-compose.yml up -d --build
docker compose -f docker-compose.yml ps
```

按 `.env` 中 `PORT` 访问（示例为 `http://localhost:3000`）。仅对全新、未开放的开发数据库，可在首次启动前临时设置 `.env` 的 `PROMOTE_FIRST_ADMIN=1`，注册首位管理员后改回 `0` 并重新应用 Backend 配置。已有用户时此开关不会补建管理员；正式环境的不同初始化方式见学校指南。

**这些是开发命令，不是学校正式发布命令。** 开发 Compose 含默认凭据、构建指令和固定容器名；仅更换 `-p` 不能避免与 Production 的容器名冲突。不要把开发默认配置开放给校园用户。

## 学校正式部署

请从 [学校部署指南](docs/SCHOOL_DEPLOYMENT.md) 开始。正式环境使用 [docker-compose.release.yml](docker-compose.release.yml)，消费已核验的固定版本本地镜像和既有外部持久卷；不在维护窗口临时构建，不从移动分支 HEAD 随意发布。

日常巡检、备份和故障处置见 [运维手册](docs/OPERATIONS.md)。首次安装、受控升级和事故恢复是不同流程，不能互相替代。

## 文档索引

| 读者／问题 | 文档 |
| --- | --- |
| 校园服务器、网络、HTTPS、首次管理员、上线 | [学校部署](docs/SCHOOL_DEPLOYMENT.md) |
| 巡检、日志、备份验证、恢复演练 | [日常运维](docs/OPERATIONS.md) |
| 维护者：固定源码、镜像与发布元数据 | [不可变本地发布](docs/immutable-release-workflow.md)、[发布清单模板](docs/release-manifest-template.md) |
| 备份格式与技术演练 | [备份设计](docs/backup-restore.md)、[备份恢复详细手册](docs/backup-restore-runbook.md) |
| 隔离环境发布／回退演练 | [发布演练](docs/release-runbook.md)、[回退演练](docs/rollback-runbook.md) |
| 运行观测与事故 | [运维就绪设计](docs/ops-readiness.md)、[事故手册](docs/incident-runbook.md) |
| 评测隔离与消息可靠性 | [Runner 架构](docs/sandbox-runner-architecture.md)、[消息可靠性](docs/judge-message-reliability.md) |
| 比赛与分析 | [Contest](docs/contest-core.md)、[计分](docs/contest-scoring.md)、[Analytics](docs/contest-analytics.md) |
| Office 判题与文档安全 | [Office 技术说明](docs/office-judging.md) |

深层文档中的历史演练背景用于解释设计与证据，不是让学校操作员重新执行开发阶段。

## 安全

Production 密钥只能保存在 Git 外的受限文件或学校批准的密钥系统中；[.env.production.example](.env.production.example) 只是变量示例，不能原样使用。不要在 Issue、日志或截图中公开密码、JWT、Runner token 或完整环境配置。

校园访问通过反向代理；互联网访问必须使用 HTTPS、防火墙与最小暴露端口。`PROMOTE_FIRST_ADMIN` 在日常 Production 必须关闭。RabbitMQ purge 不是日常恢复手段；提交点之后不允许自动回退旧 V4 数据库。威胁模型与漏洞报告方式见 [SECURITY.md](SECURITY.md)，不要公开可利用细节或用户数据。

## 容量声明

当前版本已通过小范围受控 Production Pilot，适用于日常课堂／校园使用；**尚未认证大规模同时比赛容量**。评测架构支持多个 Worker 竞争消费，但当前正式 Compose 固定单个 Worker 容器名，扩容需要单独规划和演练，不能直接套用开发环境的 `--scale` 命令。

实际并发容量取决于 CPU、内存、语言、测试用例、提交峰值与 Runner 资源限制。数百人同时提交评测之前，应进行贴近真实课程负载的容量与压力测试，不作未经测量的数字承诺。

## 开发与贡献

以真实反馈、缺陷、容量需求或明确功能请求提出 Issue，再经功能／修复分支、Docker 测试、PR、Integration/Staging 验收和正式发布流程推进。不要自动开启另一个编号开发阶段。

测试入口见 [scripts](scripts/) 和 [Docker 测试拓扑](docker-compose.test.yml)，CI 合同见 [ci.yml](.github/workflows/ci.yml)。测试与 Staging 必须使用隔离数据，不能指向 Production。文档和代码变更均通过 PR 审查，不在已标记发布上静默修改。

## 许可证

[MIT License](LICENSE)。
