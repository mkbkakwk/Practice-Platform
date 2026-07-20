<div align="center">

# 🧑‍💻 Practice Platform

### 一个开箱即用的在线练习平台 · 算法评测 · 消息队列异步评测 · Docker 一键部署

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=white)](https://react.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.13-FF6600?logo=rabbitmq&logoColor=white)](https://www.rabbitmq.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](https://docs.docker.com/compose/)

**在线算法练习 · 异步评测 · 排行榜** · 为教学与练习场景设计，支持水平扩展扛并发，未来将拓展 Office 操作等更多练习模块。

</div>

---

## 📖 项目简介

Practice Platform 是一套面向教学和自学场景的在线练习系统。当前已实现 **算法评测（Online Judge）** 模块，采用**消息队列 + Worker 池**的异步评测架构，支持高并发提交。架构上预留了多模块扩展能力，未来可加入 Office 操作练习、SQL 练习等更多题型。

### 为什么选择它

- 🚀 **一键部署** —— 一条 `docker compose up -d --build` 启动数据库、消息队列、后端、评测 Worker、前端
- 🛡️ **安全的评测沙箱** —— Java `ProcessBuilder` 起子进程 + ulimit 资源限制（CPU / 内存 / 时间 / 输出），超时强制清理进程组
- 🌐 **多语言支持** —— Python 3、JavaScript (Node)、C、C++17、Java 开箱即用
- 📝 **现代化编辑器** —— 内置 CodeMirror，语法高亮、多语言模板切换
- ⚡ **异步评测 + 高并发** —— 提交经 RabbitMQ 入队，Worker 池水平扩展，轻松扛 300+ 并发
- 📦 **预置题库** —— 自带 6 道经典算法题，部署后立即可用
- 🔐 **权限分级** —— 首位注册用户自动成为管理员
- 🐛 **错误可观测** —— 前端 ErrorBoundary 错误页 + 统一日志，便于排查问题

---

## ✨ 功能特性

| 模块 | 功能 |
| :--- | :--- |
| 👤 用户 | 注册 / 登录（JWT）/ 个人中心 / 首位用户自动管理员 |
| 📚 题库 | 题目列表（分页、难度筛选）、Markdown 题面、样例 |
| 💻 评测 | 多语言代码编辑器、一键提交、异步评测、轮询结果 |
| 🏷️ 判定 | AC / WA / TLE / RE / CE / SE |
| 📊 记录 | 全站提交动态、个人提交历史 |
| 🏆 排行榜 | 按通过题数排名 |
| 🛡️ 错误处理 | 前端 ErrorBoundary 友好错误页 + 统一日志 |
| ⚙️ 管理 | 管理员通过 API 增删题目 |

---

## 🏗️ 系统架构

```
┌──────────────┐    POST /submissions    ┌──────────────┐    publish     ┌──────────────┐
│   Frontend   │ ──────────────────────▶ │   Backend    │ ────────────▶ │   RabbitMQ   │
│  React SPA   │     (返回 submissionId)  │ Spring Boot  │               │    队列      │
│  + 轮询结果  │ ◀────────────────────── │              │               └──────┬───────┘
└──────────────┘    GET /submissions/:id  └──────┬───────┘                      │ consume
                                                │ JDBC                         ▼
                                                ▼                    ┌──────────────────┐
                                        ┌──────────────┐             │  Worker 池       │
                                        │  PostgreSQL  │ ◀──写入结果── │  (ProcessBuilder │
                                        │              │             │   评测沙箱)      │
                                        └──────────────┘             │  可水平扩展      │
                                                                     └──────────────────┘
```

**异步评测流程**：
1. 学生提交代码 → 后端写入 `PENDING` 记录 → 发送到 RabbitMQ → 立即返回 `submissionId`
2. Worker 从队列消费 → 用 `ProcessBuilder` 起子进程运行代码 → ulimit 限制资源 → 超时 `destroyForcibly`
3. 评测完成 → Worker 写回结果到 PostgreSQL → 更新排行榜
4. 前端轮询 `GET /submissions/:id` → 拿到非 PENDING 结果即展示

### 技术栈

| 层 | 技术 | 说明 |
| :--- | :--- | :--- |
| **前端** | React 19 · Vite · TypeScript · Tailwind CSS · shadcn/ui · CodeMirror · marked | SPA + ErrorBoundary 错误页 + 统一日志 |
| **后端** | Spring Boot 3.3 · Java 21 · MyBatis-Plus · JWT · Spring AMQP | REST API + 异步提交调度 |
| **评测 Worker** | Spring Boot · ProcessBuilder · Spring AMQP 消费者 | 独立服务，可 `--scale worker=N` 水平扩展 |
| **数据库** | PostgreSQL 16 | 用户、题目、提交记录 |
| **消息队列** | RabbitMQ 3.13 | 提交任务解耦与削峰 |
| **部署** | Docker · docker-compose | 多阶段构建，五容器编排 |

### 评测安全机制

用户提交的代码在 Worker 容器内以**受限子进程**方式执行：

- 🧱 **独立进程组** —— 超时可通过 `destroyForcibly` 清理整个进程树
- ⏱️ **时间限制** —— 墙钟超时 + `ulimit -t` CPU 时间双重限制
- 💾 **内存限制** —— `ulimit -v` 虚拟内存上限
- 📄 **输出限制** —— `ulimit -f` 防止输出爆炸
- 🔒 **容器隔离** —— Worker 容器本身提供网络与文件系统隔离，最小化环境变量

> ⚠️ **生产环境建议**：当前沙箱适合教学/练习场景。若要公开到互联网，建议额外使用 `nsjail`、`firejail` 或 Docker-in-Docker 提供更强隔离。

---

## 🚀 快速开始

### 前置要求

只需安装 [Docker](https://www.docker.com/) 与 Docker Compose（Docker Desktop 已包含）。

### 一键部署

```bash
git clone https://github.com/mkbkakwk/Practice-Platform.git
cd Practice-Platform
docker compose up -d --build
```

首次构建约 5–10 分钟（需拉取镜像、编译 Java 应用、安装语言运行时）。之后启动只需几秒。

启动完成后访问 👉 **http://localhost:8080**

> 💡 **第一个注册的账号会自动成为管理员**，拥有添加题目的权限。

### 自定义配置（可选）

复制 `.env.example` 为 `.env` 并按需修改：

```bash
cp .env.example .env
# 编辑 .env（端口、数据库密码、JWT 密钥、Worker 数量等）
docker compose up -d --build
```

### 常用命令

```bash
docker compose up -d --build          # 构建并后台启动
docker compose logs -f backend        # 查看后端日志
docker compose logs -f worker         # 查看 Worker 评测日志
docker compose ps                     # 查看容器状态
docker compose up -d --scale worker=3 # 水平扩展到 3 个 Worker（扛并发）
docker compose down                   # 停止并移除容器
docker compose down -v                # 停止并清空数据（含数据库）
```

### 水平扩展（扛高并发）

评测是 CPU 密集型任务，单 Worker 串行评测。通过 `--scale` 起多个 Worker 并行消费队列：

```bash
# 3 个 Worker，每个最多 2 并发 = 6 路并行评测
docker compose up -d --scale worker=3
```

300 人同时提交时，设置 `WORKER_REPLICAS=5` 左右即可轻松应对。

---

## 🧑‍💻 使用指南

### 学生使用流程

1. 打开 http://localhost:8080，点击 **注册** 创建账号
2. 在「题库」选择一道题目
3. 在右侧编辑器编写代码，选择语言，点击 **提交评测**
4. 按钮显示「评测中(N)」表示正在轮询结果（N 为轮询次数）
5. 查看评测结果：
   - ✅ **AC** —— 通过全部测试点
   - ❌ **WA** —— 第 N 个测试点答案错误
   - ⏱️ **TLE** —— 运行超时
   - 💥 **RE** —— 运行错误（如除零、段错误）
   - 🔧 **CE** —— 编译错误，显示编译器报错信息
6. 在「提交记录」查看历史，在「排行榜」查看排名

### 管理员添加题目

管理员可通过 REST API 创建题目（前端管理后台规划中）：

```bash
# 1. 登录获取 token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"你的用户名","password":"你的密码"}'

# 2. 创建题目（用上一步返回的 token）
curl -X POST http://localhost:8080/api/problems \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{
    "slug": "hello-world",
    "title": "Hello World",
    "description": "# Hello World\n\n输出 Hello, World!",
    "difficulty": "EASY",
    "timeLimit": 1000,
    "memoryLimit": 256,
    "tags": ["入门"],
    "samples": "[{\"input\":\"\",\"output\":\"Hello, World!\"}]",
    "testCases": "[{\"input\":\"\",\"output\":\"Hello, World!\"}]"
  }'
```

---

## 📂 目录结构

```
practice-platform/
├── docker-compose.yml          # 五服务编排：db + rabbitmq + backend + worker + frontend
├── .env.example                # 环境变量示例
├── README.md
│
├── backend-spring/             # Spring Boot 后端
│   ├── Dockerfile              # 多阶段构建（Maven 编译 + JRE 运行）
│   ├── pom.xml
│   ├── src/main/resources/
│   │   ├── application.yml     # 配置
│   │   ├── schema.sql          # 建表脚本（启动自动执行）
│   │   └── data.sql            # 种子题目（启动自动导入）
│   └── src/main/java/com/oj/
│       ├── config/             # WebConfig / RabbitConfig / MybatisPlusConfig / AppProperties
│       ├── common/             # JwtUtil / JwtInterceptor / CurrentUser / 异常处理
│       ├── entity/             # MyBatis-Plus 实体
│       ├── mapper/             # Mapper 接口
│       ├── dto/                # 请求/响应 DTO
│       ├── service/            # AuthService / ProblemService / SubmissionService
│       ├── controller/         # REST 控制器
│       └── judge/              # LanguageDef 语言配置
│
├── worker/                     # Java 评测 Worker（独立服务）
│   ├── Dockerfile              # 含 python3/g++/jdk 运行时
│   ├── pom.xml
│   └── src/main/java/com/oj/
│       ├── config/             # RabbitConfig / JudgeConsumer 消费者
│       ├── judge/              # Runner(ProcessBuilder沙箱) / JudgeService / LanguageDef
│       ├── entity/             # 实体
│       └── mapper/             # Mapper
│
└── frontend/                   # React 前端
    ├── Dockerfile              # 多阶段构建（build + nginx）
    ├── nginx.conf              # SPA 托管 + /api 反代
    └── src/
        ├── lib/                # api.ts / auth.ts / logger.ts 日志工具
        ├── components/         # Navbar / ErrorBoundary 错误边界
        ├── pages/              # 题库 / 详情 / 登录 / 记录 / 排行榜
        └── App.tsx             # 路由（ErrorBoundary 双重包裹）
```

---

## 📦 预置题库

部署后自带 6 道经典算法题，覆盖入门到进阶：

| # | 题目 | 难度 | 考点 |
|---|------|------|------|
| 1 | A + B 问题 | 简单 | 输入输出基础 |
| 2 | 求 1 到 N 的和 | 简单 | 数学、64 位整型 |
| 3 | 斐波那契数列取模 | 中等 | 递推、取模 |
| 4 | 判断质数 | 简单 | 数学、试除法 |
| 5 | 最大子数组和 | 中等 | 动态规划（Kadane） |
| 6 | 逆序对计数 | 困难 | 分治、归并排序 |

---

## 🔌 API 概览

| 方法 | 路径 | 鉴权 | 说明 |
| :--- | :--- | :--- | :--- |
| POST | `/api/auth/register` | - | 注册 |
| POST | `/api/auth/login` | - | 登录 |
| GET | `/api/auth/me` | ✅ | 当前用户信息 |
| GET | `/api/problems` | - | 题目列表（分页、筛选） |
| GET | `/api/problems/:slug` | - | 题目详情 |
| POST | `/api/problems` | 🔒 管理员 | 创建题目 |
| PUT | `/api/problems/:slug` | 🔒 管理员 | 更新题目 |
| GET | `/api/submissions/meta/languages` | - | 支持语言及模板 |
| POST | `/api/submissions` | ✅ | 提交评测（异步，返回 submissionId） |
| GET | `/api/submissions/:id` | ✅ | 查询提交结果（前端轮询） |
| GET | `/api/submissions` | - | 全站提交记录 |
| GET | `/api/users/me/submissions` | ✅ | 个人提交记录 |
| GET | `/api/users/leaderboard` | - | 排行榜 |

---

## ⚙️ 环境变量

| 变量 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `PORT` | `8080` | 前端对外端口 |
| `POSTGRES_USER` | `oj` | 数据库用户 |
| `POSTGRES_PASSWORD` | `oj` | 数据库密码 |
| `POSTGRES_DB` | `oj` | 数据库名 |
| `RABBITMQ_USER` | `oj` | RabbitMQ 用户 |
| `RABBITMQ_PASSWORD` | `oj` | RabbitMQ 密码 |
| `WORKER_REPLICAS` | `1` | Worker 容器数量（水平扩展） |
| `WORKER_CONCURRENCY` | `1` | 单 Worker 并发消费数 |
| `JWT_SECRET` | `please-change-...` | JWT 签名密钥（**生产必改**） |
| `JWT_EXPIRES_IN` | `7d` | Token 有效期 |
| `CORS_ORIGIN` | `*` | 跨域来源 |
| `PROMOTE_FIRST_ADMIN` | `true` | 首位注册用户是否成为管理员 |

---

## 🛠️ 本地开发

### 后端（Spring Boot）

需本地 PostgreSQL 与 RabbitMQ，或用 Docker 仅起依赖：

```bash
# 仅起 db 和 rabbitmq
docker compose up -d db rabbitmq

# 后端本地运行
cd backend-spring
# 配置 application.yml 或环境变量指向本地依赖
./mvnw spring-boot:run     # http://localhost:4000
```

### Worker

```bash
cd worker
./mvnw spring-boot:run      # 消费 RabbitMQ 队列
```

### 前端

```bash
cd frontend
npm install
VITE_API_BASE=http://localhost:4000/api npm run dev   # http://localhost:5173
```

---

## 🗺️ 路线图

- [x] 算法评测（Python / JS / C / C++ / Java）
- [x] 消息队列 + Worker 池异步评测架构
- [x] 前端 ErrorBoundary 错误页 + 统一日志
- [x] Docker 一键部署、Worker 水平扩展
- [ ] 前端管理后台（可视化添加题目）
- [ ] **Office 操作练习模块**（Word / Excel / PPT，Java + Apache POI）
- [ ] SQL 练习模块
- [ ] SSE 实时推送评测结果（替代轮询）
- [ ] 比赛模式（限时、计分板）
- [ ] 题目数据导入 / 导出
- [ ] 代码防作弊相似度检测

---

## 🤝 贡献

欢迎提交 Issue 与 Pull Request。

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/your-feature`
3. 提交更改：`git commit -m 'feat: add some feature'`
4. 推送分支：`git push origin feature/your-feature`
5. 提交 Pull Request

> 提交信息建议遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范。

---

## 📄 License

[MIT](./LICENSE) © 2026 Practice Platform

---

<div align="center">

如果这个项目对你有帮助，欢迎 ⭐ Star 支持！

</div>
