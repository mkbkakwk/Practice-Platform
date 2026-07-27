<div align="center">

# 🧑‍💻 Practice Platform

### 算法评测 · Office 操作练习 · 文档排版练习 · 三角色权限 · Docker 一键部署

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=white)](https://react.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.13-FF6600?logo=rabbitmq&logoColor=white)](https://www.rabbitmq.com/)
[![Apache POI](https://img.shields.io/badge/Apache%20POI-5.3-AA0000?logo=apache&logoColor=white)](https://poi.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](https://docs.docker.com/compose/)

**在线算法练习 · 异步评测 · Office 选择题 · 文档排版自动比对 · 三角色权限** · 为教学与练习场景设计，支持水平扩展扛并发。

</div>

---

## 📖 项目简介

Practice Platform 是一套面向教学和自学场景的在线练习系统。目前已实现 **三大模块**：

1. **算法评测（Online Judge）** —— 消息队列 + Worker 池异步评测，支持 Python/JS/C/C++/Java
2. **Office 选择题练习** —— Word/Excel/PPT 操作题，单选/多选/判断，即时判分
3. **文档排版练习** —— 学生上传 .docx，系统用 Apache POI 自动解析格式并与老师参考文档逐段比对，有差异的老师人工复核

采用 **管理员 / 老师 / 学生** 三角色权限体系，各司其职。

### 为什么选择它

- 🚀 **一键部署** —— 一条 `docker compose up -d --build` 启动数据库、消息队列、后端、评测 Worker、前端
- 🛡️ **安全的评测沙箱** —— Java `ProcessBuilder` 起子进程 + ulimit 资源限制，超时强制清理进程组
- 🌐 **多语言支持** —— Python 3、JavaScript (Node)、C、C++17、Java 开箱即用
- 📝 **现代化编辑器** —— 内置 CodeMirror，语法高亮、多语言模板切换
- ⚡ **异步评测 + 高并发** —— 提交经 RabbitMQ 入队，Worker 池水平扩展，轻松扛 300+ 并发
- 📦 **预置题库** —— 自带 6 道算法题 + 11 道 Office 选择题，部署后立即可用
- 📄 **文档排版自动比对** —— Apache POI 解析 .docx 格式，逐段比对字体/字号/加粗/对齐/缩进/行距
- 👨‍🏫 **三角色权限** —— 管理员管理用户、老师出题+复核、学生做题+上传文档
- 🔐 **注册选身份** —— 注册时选择学生或老师，首位注册用户自动成为管理员
- 🐛 **错误可观测** —— 前端 ErrorBoundary 错误页 + 统一日志

---

## ✨ 功能特性

| 模块 | 功能 |
| :--- | :--- |
| 👤 用户 | 注册（选身份：学生/老师）/ 登录（JWT）/ 首位用户自动管理员 |
| 📚 算法题库 | 题目列表（分页、难度筛选）、Markdown 题面（支持 LaTeX）、样例 |
| 💻 算法评测 | 多语言代码编辑器、一键提交、异步评测、轮询结果 |
| 🏷️ 判定 | AC / WA / TLE / RE / CE / SE |
| 📊 记录 | 全站提交动态、个人提交历史 |
| 🏆 排行榜 | 按通过题数排名 |
| 📝 Office 选择题 | Word/Excel/PPT 分类、单选/多选/判断、即时判分+解析、答题统计 |
| 📄 文档排版练习 | 学生上传 .docx → POI 解析格式 → 和老师文档逐段比对 → 老师复核打分 |
| ⚙️ 管理后台 | 算法题可视化表单（Markdown+LaTeX预览）、Office 题库管理、用户角色管理 |
| 🛡️ 错误处理 | 前端 ErrorBoundary 友好错误页 + 统一日志 |

### 三角色权限

| 功能 | 学生 | 老师 | 管理员 |
| :--- | :---: | :---: | :---: |
| 做算法题 / Office 选择题 | ✅ | ✅ | ✅ |
| 上传排版文档 | ✅ | ✅ | ✅ |
| 查看自己的提交记录 | ✅ | ✅ | ✅ |
| 创建排版练习 + 上传参考文档 | ❌ | ✅ | ✅ |
| 复核学生提交（打分+评语） | ❌ | ✅ | ✅ |
| 算法题管理（增删改） | ❌ | ❌ | ✅ |
| Office 选择题管理 | ❌ | ❌ | ✅ |
| 用户角色管理 | ❌ | ❌ | ✅ |

---

## 🏗️ 系统架构

```
┌──────────────┐    POST /submissions    ┌──────────────┐    publish     ┌──────────────┐
│   Frontend   │ ──────────────────────▶ │   Backend    │ ────────────▶ │   RabbitMQ   │
│  React SPA   │     (返回 submissionId)  │ Spring Boot  │               │    队列      │
│  + 轮询结果  │ ◀────────────────────── │              │               └──────┬───────┘
└──────────────┘    GET /submissions/:id  └──────┬───────┘                      │ consume
      │                                          │ JDBC                         ▼
      │ 上传 .docx                               ▼                    ┌──────────────────┐
      │ POST /office/docs/.../submit     ┌──────────────┐             │  Worker 池       │
      └─────────────────────────────────▶│  PostgreSQL  │ ◀──写入结果──│  (ProcessBuilder │
                                         │              │             │   评测沙箱)      │
              ┌──────────────┐           │ · User       │             │  可水平扩展      │
              │ Apache POI   │           │ · Problem    │             └──────────────────┘
              │ .docx 解析   │           │ · Submission │
              │ 格式提取     │           │ · OfficeQ    │
              └──────────────┘           │ · OfficeEx   │
                     │                   │ · OfficeDoc  │
                     ▼                   │   Submission │
              逐段比对判分                └──────────────┘
```

**异步评测流程**：
1. 学生提交代码 → 后端写入 `PENDING` 记录 → 发送到 RabbitMQ → 立即返回 `submissionId`
2. Worker 从队列消费 → 用 `ProcessBuilder` 起子进程运行代码 → ulimit 限制资源 → 超时 `destroyForcibly`
3. 评测完成 → Worker 写回结果到 PostgreSQL → 更新排行榜
4. 前端轮询 `GET /submissions/:id` → 拿到非 PENDING 结果即展示

**文档排版比对流程**：
1. 老师创建排版练习 → 写 Markdown 排版要求 → 上传参考 .docx
2. 学生下载要求 → 在 Word 中排版 → 上传 .docx
3. 后端用 Apache POI 解析学生文档（字体/字号/加粗/对齐/缩进/行距）→ 与老师文档逐段比对
4. 全部匹配 → 自动通过；有差异 → 标记待复核 → 老师人工打分+评语

### 技术栈

| 层 | 技术 | 说明 |
| :--- | :--- | :--- |
| **前端** | React 19 · Vite · TypeScript · Tailwind CSS · shadcn/ui · CodeMirror · marked · KaTeX | SPA + ErrorBoundary + 统一日志 |
| **后端** | Spring Boot 3.3 · Java 21 · MyBatis-Plus · JWT · Spring AMQP · Apache POI | REST API + 异步提交 + .docx 解析 |
| **评测 Worker** | Spring Boot · ProcessBuilder · Spring AMQP 消费者 | 独立服务，可 `--scale worker=N` 水平扩展 |
| **数据库** | PostgreSQL 16 | 用户、题目、提交、Office 题库与记录 |
| **消息队列** | RabbitMQ 3.13 | 提交任务解耦与削峰 |
| **部署** | Docker · docker-compose | 多阶段构建，五容器编排 |

### 评测安全机制

用户提交的代码在 Worker 容器内以**受限子进程**方式执行：

- 🧱 **独立进程组** —— 超时可通过 `destroyForcibly` 清理整个进程树
- ⏱️ **时间限制** —— 墙钟超时 + `ulimit -t` CPU 时间双重限制
- 💾 **内存限制** —— `ulimit -v` 虚拟内存上限
- 📄 **输出限制** —— `ulimit -f` 防止输出爆炸
- 🔒 **容器隔离** —— Worker 容器本身提供网络与文件系统隔离

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

启动完成后访问 👉 **http://localhost:3000**

> 💡 **第一个注册的账号会自动成为管理员**。注册时可选「学生」或「老师」身份。

> ⚠️ **Windows 端口说明**：Windows 会动态保留部分 TCP 端口（含 8080/4000），项目 `.env` 已默认设 `PORT=3000`。如遇端口冲突，修改 `.env` 中的 `PORT` 即可。

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

---

## 🧑‍💻 使用指南

### 学生

1. 打开 http://localhost:3000，注册时选择「学生」身份
2. **算法练习**：在「题库」选题 → 编辑器写代码 → 提交评测 → 查看结果
3. **Office 选择题**：点「Office」→ 选 Word/Excel/PPT → 答题 → 即时判分+解析
4. **文档排版练习**：点「Office」→「排版练习（文档上传）」→ 下载老师参考文档 → 按要求在 Word 中排版 → 上传 .docx → 查看自动比对结果

### 老师

1. 注册时选择「老师」身份（或由管理员在「用户」管理页提升角色）
2. 导航栏出现「复核」入口
3. **创建排版练习**：排版练习列表 →「新建练习」→ 填标题+Markdown要求 → 上传老师参考 .docx
4. **复核学生提交**：点「复核」→ 查看比对详情（逐段格式差异）→ 下载学生文档 → 打分+评语

### 管理员

- 老师的全部功能 +
- **算法题管理**：导航栏「管理」→ 可视化表单创建题目（Markdown+LaTeX 实时预览、样例/测试点动态增删）
- **Office 选择题管理**：管理 Office 题库（创建/编辑单选/多选/判断题）
- **用户角色管理**：导航栏「用户」→ 修改任意用户的角色（学生/老师/管理员）

---

## 📂 目录结构

```
practice-platform/
├── docker-compose.yml          # 五服务编排：db + rabbitmq + backend + worker + frontend
├── .env                        # 本地配置（端口等）
├── README.md
│
├── backend-spring/             # Spring Boot 后端
│   ├── Dockerfile              # 多阶段构建（Maven 编译 + JRE 运行）
│   ├── pom.xml                 # 含 Apache POI 依赖
│   ├── src/main/resources/
│   │   ├── application.yml     # 配置（含 multipart 文件上传）
│   │   ├── schema.sql          # 建表（含 Office 题库/练习/提交表）
│   │   └── data.sql            # 种子数据（6 算法题 + 11 Office 选择题）
│   └── src/main/java/com/oj/
│       ├── config/             # WebConfig / RabbitConfig / AppProperties
│       ├── common/             # JwtUtil / CurrentUser(三角色) / DocxParser / DocComparator
│       ├── entity/             # 实体（含 OfficeQuestion/Exercise/Submission）
│       ├── mapper/             # Mapper 接口
│       ├── dto/                # 请求/响应 DTO
│       ├── service/            # Auth / Problem / Submission / Office / OfficeDoc
│       └── controller/         # REST 控制器
│
├── worker/                     # Java 评测 Worker（独立服务）
│   ├── Dockerfile              # 含 python3/g++/jdk 运行时
│   └── src/main/java/com/oj/   # Runner(ProcessBuilder沙箱) / JudgeService
│
└── frontend/                   # React 前端
    ├── Dockerfile              # 多阶段构建（build + nginx）
    ├── nginx.conf              # SPA 托管 + /api 反代
    └── src/
        ├── lib/                # api.ts / auth.ts / verdict.tsx / logger.ts
        ├── components/         # Navbar / ErrorBoundary / AdminGuard+TeacherGuard
        └── pages/              # 题库/详情/登录/记录/排行榜 + Office练习/排版练习/复核/用户管理
```

---

## 📦 预置题库

### 算法题（6 道）

| # | 题目 | 难度 | 考点 |
|---|------|------|------|
| 1 | A + B 问题 | 简单 | 输入输出基础 |
| 2 | 求 1 到 N 的和 | 简单 | 数学、64 位整型 |
| 3 | 斐波那契数列取模 | 中等 | 递推、取模 |
| 4 | 判断质数 | 简单 | 数学、试除法 |
| 5 | 最大子数组和 | 中等 | 动态规划（Kadane） |
| 6 | 逆序对计数 | 困难 | 分治、归并排序 |

### Office 选择题（11 道）

| 应用 | 数量 | 题型 | 考点示例 |
|------|------|------|----------|
| Word | 4 | 单选/多选/判断 | 格式刷、快捷键、页面布局、邮件合并 |
| Excel | 4 | 单选/多选/判断 | SUM 函数、绝对引用、统计函数、数据透视表 |
| PPT | 3 | 单选/多选/判断 | 放映快捷键、动画类型、幻灯片母版 |

---

## 🔌 API 概览

| 方法 | 路径 | 鉴权 | 说明 |
| :--- | :--- | :--- | :--- |
| POST | `/api/auth/register` | - | 注册（可选 role: USER/TEACHER） |
| POST | `/api/auth/login` | - | 登录 |
| GET | `/api/auth/me` | ✅ | 当前用户信息 |
| GET | `/api/problems` | - | 算法题列表 |
| GET | `/api/problems/:slug` | - | 算法题详情 |
| POST | `/api/problems` | 🔒 管理员 | 创建算法题 |
| PUT | `/api/problems/:slug` | 🔒 管理员 | 更新算法题 |
| POST | `/api/submissions` | ✅ | 提交评测（异步） |
| GET | `/api/submissions/:id` | ✅ | 查询评测结果 |
| GET | `/api/office/questions` | - | Office 选择题列表 |
| GET | `/api/office/questions/:id` | - | Office 选择题详情 |
| POST | `/api/office/submit` | ✅ | 提交选择题答案 |
| GET | `/api/office/stats` | ✅ | 答题统计 |
| POST | `/api/office/questions` | 🔒 管理员 | 创建选择题 |
| GET | `/api/office/docs/exercises` | - | 排版练习列表 |
| GET | `/api/office/docs/exercises/:id` | - | 排版练习详情 |
| POST | `/api/office/docs/exercises` | 🔒 老师/管理员 | 创建排版练习 |
| POST | `/api/office/docs/exercises/:id/teacher-doc` | 🔒 老师/管理员 | 上传老师参考文档 |
| POST | `/api/office/docs/exercises/:id/submit` | ✅ | 学生上传 .docx |
| GET | `/api/office/docs/submissions` | ✅ | 提交列表（老师/管理员看全部） |
| PUT | `/api/office/docs/submissions/:id/review` | 🔒 老师/管理员 | 复核打分 |
| GET | `/api/users` | 🔒 管理员 | 用户列表 |
| PUT | `/api/users/:id/role` | 🔒 管理员 | 修改用户角色 |
| GET | `/api/users/leaderboard` | - | 排行榜 |

---

## ⚙️ 环境变量

| 变量 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `PORT` | `3000` | 前端对外端口 |
| `POSTGRES_USER` | `oj` | 数据库用户 |
| `POSTGRES_PASSWORD` | `oj` | 数据库密码 |
| `POSTGRES_DB` | `oj` | 数据库名 |
| `RABBITMQ_USER` | `oj` | RabbitMQ 用户 |
| `RABBITMQ_PASSWORD` | `oj` | RabbitMQ 密码 |
| `WORKER_REPLICAS` | `1` | Worker 容器数量 |
| `WORKER_CONCURRENCY` | `1` | 单 Worker 并发消费数 |
| `JWT_SECRET` | `please-change-...` | JWT 签名密钥（**生产必改**） |
| `JWT_EXPIRES_IN` | `7d` | Token 有效期 |
| `CORS_ORIGIN` | `*` | 跨域来源 |
| `PROMOTE_FIRST_ADMIN` | `true` | 首位注册用户是否成为管理员 |
| `DOC_STORAGE` | `/app/oj-docs` | 文档上传存储路径 |

---

## 🛠️ 本地开发

### 后端（Spring Boot）

```bash
# 仅起 db 和 rabbitmq
docker compose up -d db rabbitmq

# 后端本地运行
cd backend-spring
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
- [x] 前端管理后台（可视化添加算法题，Markdown+LaTeX 预览）
- [x] **Office 选择题练习模块**（Word/Excel/PPT，单选/多选/判断）
- [x] **文档排版练习模块**（上传 .docx，Apache POI 解析+自动比对+老师复核）
- [x] **三角色权限体系**（管理员/老师/学生）
- [x] 用户角色管理（管理员可改任意用户角色）
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

---

## 📄 License

[MIT](./LICENSE) © 2026 Practice Platform

---

<div align="center">

如果这个项目对你有帮助，欢迎 ⭐ Star 支持！

</div>
