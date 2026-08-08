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
- 🛡️ **受限评测进程** —— Java `ProcessBuilder` 起子进程 + ulimit 资源限制，`exec` 对准真实程序并在超时后强制终止
- 🌐 **多语言支持** —— Python 3、JavaScript (Node)、C、C++17、Java 开箱即用
- 📝 **现代化编辑器** —— 内置 CodeMirror，语法高亮、多语言模板切换
- ⚡ **异步评测 + 高并发** —— 提交经 RabbitMQ 入队，Worker 池水平扩展，轻松扛 300+ 并发
- 📦 **可选演示题库** —— 提供 6 道算法题 + 11 道 Office 选择题的手动开发 seed
- 📄 **文档排版自动比对** —— Apache POI 解析 .docx 格式，逐段比对字体/字号/加粗/对齐/缩进/行距
- 👨‍🏫 **三角色权限** —— 教师管理自建内容并复核自己的排版练习，管理员管理全部内容
- 🔐 **可靠身份权限** —— 公开注册默认统一为学生；角色或密码变化会立即使旧 JWT 失效
- 🐛 **错误可观测** —— 前端 ErrorBoundary 错误页 + 统一日志

---

## ✨ 功能特性

| 模块 | 功能 |
| :--- | :--- |
| 👤 用户 | 统一学生注册 / 登录（JWT）/ 管理员调整角色 / 受控首位管理员初始化 |
| 📚 算法题库 | 题目列表（分页、难度筛选）、Markdown 题面（支持 LaTeX）、样例 |
| 💻 算法评测 | 多语言代码编辑器、一键提交、异步评测、轮询结果 |
| 🏷️ 判定 | AC / WA / TLE / RE / CE / SE |
| 📊 记录 | 用户查看个人提交历史；管理员可查看全站提交 |
| 🏆 排行榜 | 按通过题数排名 |
| 📝 Office 选择题 | Word/Excel/PPT 分类、单选/多选/判断、即时判分+解析、答题统计 |
| 📄 文档排版练习 | 学生上传 .docx → POI 解析格式 → 和老师文档逐段比对 → 老师复核打分 |
| ⚙️ 管理后台 | 算法题可视化表单（Markdown+LaTeX预览）、Office 题库管理、用户角色管理 |
| 🛡️ 错误处理 | 前端 ErrorBoundary 友好错误页 + 统一日志 |

### 三角色权限

| 功能 | 学生 | 老师 | 管理员 |
| :--- | :---: | :---: | :---: |
| 查看已启用内容、做算法题 / Office 选择题 | ✅ | ✅ | ✅ |
| 下载排版素材、上传排版作业 | ✅ | ✅ | ✅ |
| 查看自己的提交与成绩 | ✅ | ✅ | ✅ |
| 创建三类内容 | ❌ | ✅ | ✅ |
| 编辑、启用、停用自建内容 | ❌ | ✅ | ✅ |
| 复核自建排版练习的学生提交 | ❌ | ✅ | ✅ |
| 管理其他教师或系统预置内容 | ❌ | ❌ | ✅ |
| 彻底删除无学生提交的自建内容 | ❌ | ✅ | ✅ |
| 彻底删除任意内容并清理关联数据 | ❌ | ❌ | ✅ |
| 用户角色管理 | ❌ | ❌ | ✅ |

系统预置内容的 `created_by` 为 `NULL`，仅管理员可以管理。三个内容模块复用 `visible` 作为启用状态：停用后学生无法继续查看或提交，但历史提交、答案、代码、文档、成绩和统计会保留，并可重新启用。彻底删除会清理该内容的真实关联提交与统计；教师只能彻底删除没有任何学生提交的自建内容。

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
Backend 启动时由 Flyway 创建或升级数据库结构；Worker 会等待 Backend
健康检查通过后才开始消费判题消息。全新数据库默认不插入演示题目或账号。

启动完成后访问 👉 **http://localhost:3000**

> 💡 生产环境默认不会自动提升首位注册用户；其余公开注册账号统一为学生，教师角色由管理员授予。

### 管理员安全初始化与会话失效

生产配置默认关闭 `PROMOTE_FIRST_ADMIN`。空库首次部署时，应在网站对外开放前临时、显式设为 `true`，创建唯一首位管理员后立即恢复为 `false`；事务级数据库锁保证并发注册最多产生一个首位管理员。公开注册在配置关闭时始终为学生账号。

JWT 包含 `userId`、`username`、`role` 和 `tokenVersion`。每个认证请求都会重新读取数据库用户并校验版本，最终权限以数据库当前角色为准；修改角色或密码会递增版本，使所有旧 Token 立即返回 401。系统拒绝降级或删除最后一个管理员。匿名访问采用明确白名单：注册、登录、健康检查、语言元数据、已启用算法题列表/详情和排行榜；其他后端接口默认要求登录。

> ⚠️ **Windows 端口说明**：Windows 会动态保留部分 TCP 端口（含 8080/4000），项目 `.env` 已默认设 `PORT=3000`。如遇端口冲突，修改 `.env` 中的 `PORT` 即可。

### 自定义配置（可选）

复制 `.env.example` 为 `.env` 并按需修改：

```bash
cp .env.example .env
# 编辑 .env（端口、数据库密码、JWT 密钥、Worker 数量等）
docker compose up -d --build
```

### 数据库迁移与演示数据

Flyway 是唯一的数据库结构来源，迁移文件位于
`backend-spring/src/main/resources/db/migration`：

1. `V1__baseline_schema.sql` 创建完整空库结构；
2. `V2__data_integrity_constraints.sql` 检查孤立数据并增加外键和 `CHECK`；
3. `V3__supporting_indexes.sql` 增加查询所需索引。

全新空库执行 V1–V3。由旧 `schema.sql` 创建的非空数据库会在版本 1
建立 baseline，然后仅执行 V2–V3；不会重复建表或自动插入演示数据。
Backend 是唯一迁移执行方，Worker 明确禁用 SQL 初始化。

可选演示题位于 `scripts/dev-seed.sql`，只允许在明确的开发数据库中
手动加载：

```bash
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < scripts/dev-seed.sql
```

不要在生产环境自动运行该脚本。测试数据由测试代码创建，不依赖演示 seed。

正式升级已有数据库前必须：

1. 进入维护模式或停止写入；
2. 完整备份 PostgreSQL；
3. 备份 Office 文档存储；
4. 在数据库副本上完整演练 migration；
5. 核对 9 类孤立数据预检查；
6. 记录升级前 schema 和 Flyway 状态；
7. 验证备份恢复流程后再安排正式迁移。

迁移发现孤立记录或非法既有值时会失败并保留数据，不会静默删除。
Flyway 不保证任意 PostgreSQL DDL 都能自动回滚，恢复仍依赖经过验证的备份。

### 常用命令

```bash
docker compose up -d --build          # 构建并后台启动
docker compose logs -f backend        # 查看后端日志
docker compose logs -f worker         # 查看 Worker 评测日志
docker compose ps                     # 查看容器状态
docker compose up -d --scale worker=3 # 水平扩展到 3 个 Worker（扛并发）
docker compose down                   # 停止并移除容器
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

1. 打开 http://localhost:3000 注册并登录（公开注册统一为学生）
2. **算法练习**：在「题库」选择已启用题目 → 编写代码 → 提交评测 → 查看自己的结果
3. **Office 选择题**：点「Office」→ 选择已启用题目 → 答题 → 查看即时判分与个人统计
4. **文档排版练习**：下载已启用练习的参考文档 → 在 Word 中排版 → 上传 .docx → 查看自己的自动比对与复核成绩

### 老师

1. 由管理员在「用户」管理页授予教师角色
2. 导航栏「内容管理」可以创建并管理自己创建的算法题、Office 选择题和排版练习
3. 可编辑、启用、停用自建内容；系统预置内容和其他教师内容没有管理按钮，后端也会拒绝请求
4. 可在「复核」中查看和批改自己创建的排版练习下的学生提交
5. 自建内容没有学生提交时可彻底删除；已有提交时只能停用

### 管理员

- 可以管理三类全部内容，包括系统预置内容和其他教师创建的内容
- 可以停用或重新启用任意内容；停用保留历史数据
- 可以彻底删除任意内容；有关联提交时会事务清理提交、文件和实际存在的统计字段
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
│   ├── pom.xml                 # 含 Apache POI 与 Flyway PostgreSQL 支持
│   ├── src/main/resources/
│   │   ├── application.yml     # 配置（含 multipart 文件上传）
│   │   └── db/migration/       # 唯一权威数据库结构：V1 / V2 / V3
│   ├── src/test/resources/
│   │   └── legacy-schema.sql   # 仅用于验证旧 schema 无损升级
│   └── src/main/java/com/oj/
│       ├── config/             # WebConfig / RabbitConfig / AppProperties
│       ├── common/             # JwtUtil / CurrentUser(三角色) / DocxParser / DocComparator
│       ├── entity/             # 实体（含 OfficeQuestion/Exercise/Submission）
│       ├── mapper/             # Mapper 接口
│       ├── dto/                # 请求/响应 DTO
│       ├── service/            # Auth / Problem / Submission / Office / OfficeDoc
│       └── controller/         # REST 控制器
│
├── scripts/dev-seed.sql        # 可选演示题；仅手动加载到开发数据库
│
├── worker/                     # Java 评测 Worker（独立服务）
│   ├── Dockerfile              # 含 Python/Node.js/GCC/G++/Temurin JDK 运行时
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

## 📦 可选演示题库

以下内容位于 `scripts/dev-seed.sql`，不会随生产或测试启动自动插入。
全新数据库默认为空，由管理员或教师创建内容。

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
| POST | `/api/auth/register` | - | 注册（统一创建 USER） |
| POST | `/api/auth/login` | - | 登录 |
| PUT | `/api/auth/password` | ✅ | 已登录用户修改自己的密码并使旧 Token 失效 |
| GET | `/api/auth/me` | ✅ | 当前用户信息 |
| GET | `/api/problems` | - | 算法题列表 |
| GET | `/api/problems/:slug` | - | 算法题详情 |
| GET | `/api/problems/manage` | 🔒 教师/管理员 | 可管理算法题列表（教师仅自建） |
| POST | `/api/problems` | 🔒 教师/管理员 | 创建算法题并记录 `created_by` |
| PUT | `/api/problems/:slug` | 🔒 所有者/管理员 | 更新算法题 |
| PUT | `/api/problems/:slug/visibility` | 🔒 所有者/管理员 | 启用或停用算法题 |
| DELETE | `/api/problems/:slug` | 🔒 所有者/管理员 | 彻底删除算法题 |
| POST | `/api/submissions` | ✅ | 提交评测（停用题禁止提交） |
| GET | `/api/submissions/:id` | ✅ | 查询评测结果 |
| GET | `/api/office/questions` | - | Office 选择题列表 |
| GET | `/api/office/questions/:id` | - | Office 选择题详情 |
| POST | `/api/office/submit` | ✅ | 提交选择题答案 |
| GET | `/api/office/stats` | ✅ | 答题统计 |
| GET | `/api/office/questions/manage` | 🔒 教师/管理员 | 可管理选择题列表 |
| POST | `/api/office/questions` | 🔒 教师/管理员 | 创建选择题 |
| PUT | `/api/office/questions/:id/visibility` | 🔒 所有者/管理员 | 启用或停用选择题 |
| DELETE | `/api/office/questions/:id` | 🔒 所有者/管理员 | 彻底删除选择题 |
| GET | `/api/office/docs/exercises` | - | 排版练习列表 |
| GET | `/api/office/docs/exercises/:id` | - | 排版练习详情 |
| GET | `/api/office/docs/exercises/manage` | 🔒 教师/管理员 | 可管理排版练习列表 |
| POST | `/api/office/docs/exercises` | 🔒 教师/管理员 | 创建排版练习 |
| PUT | `/api/office/docs/exercises/:id` | 🔒 所有者/管理员 | 编辑排版练习 |
| PUT | `/api/office/docs/exercises/:id/visibility` | 🔒 所有者/管理员 | 启用或停用排版练习 |
| DELETE | `/api/office/docs/exercises/:id` | 🔒 所有者/管理员 | 彻底删除排版练习 |
| POST | `/api/office/docs/exercises/:id/teacher-doc` | 🔒 老师/管理员 | 上传老师参考文档 |
| POST | `/api/office/docs/exercises/:id/submit` | ✅ | 学生上传 .docx |
| GET | `/api/office/docs/submissions` | ✅ | 学生看自己；教师看自建练习；管理员看全部 |
| PUT | `/api/office/docs/submissions/:id/review` | 🔒 所有者/管理员 | 复核打分 |
| GET | `/api/users` | 🔒 管理员 | 用户列表 |
| PUT | `/api/users/:id/role` | 🔒 管理员 | 修改用户角色 |
| DELETE | `/api/users/:id` | 🔒 管理员 | 删除无历史记录的用户（最后管理员受保护） |
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
| `PROMOTE_FIRST_ADMIN` | `false` | 仅用于对外开放前的受控首位管理员初始化 |
| `DOC_STORAGE` | `/app/oj-docs` | 文档上传存储路径 |

---

## Staging 测试网站

Staging 使用独立 Compose 项目 practice-platform-staging，并固定使用独立的 PostgreSQL、RabbitMQ、DOCX 卷和网络。它不会读取正式 .env、oj_oj-pgdata、oj_oj-docs 或 oj_oj-net。

首次在本机创建只属于 Staging 的随机凭据：

~~~bash
./scripts/staging-init-env.sh
~~~

该命令只创建被 Git 忽略的 .env.staging，不会输出密码或 JWT 密钥。占位示例位于 .env.staging.example。首位管理员自动提升始终默认为 false；Staging 公共注册只会创建 USER。

构建、启动并等待健康检查：

~~~bash
./scripts/staging-up.sh
~~~

默认访问地址：

- 页面：http://localhost:18080
- 健康检查：http://localhost:18080/api/health
- Backend、PostgreSQL 和 RabbitMQ 不暴露宿主机端口
- Navbar 中的 STAGING 标记会显示当前构建 Git SHA

运行独立 HTTP 冒烟测试：

~~~bash
./scripts/staging-smoke.sh
~~~

该测试只在 Staging 网络内创建临时测试用户，验证首页、健康检查、公开题目、匿名 401、注册、登录、鉴权、修改密码、旧 Token 失效和新密码登录；不会访问正式网站或正式数据。

查看状态和日志：

~~~bash
./scripts/staging-status.sh
./scripts/staging-logs.sh
./scripts/staging-logs.sh backend worker
~~~

安全停止并保留 Staging 数据：

~~~bash
./scripts/staging-down.sh
~~~

只有明确需要重建全新 Staging 数据时才使用以下命令；脚本会校验只删除名称包含 staging 的三个 Staging 卷：

~~~bash
./scripts/staging-down.sh --volumes
~~~

不要对默认或正式 Compose 项目执行 down，也不要将 .env.staging 提交到仓库。

---

## ✅ Docker 化测试与持续集成

宿主机只需要 Git、Docker 和 Docker Compose；不要在宿主机直接运行 Maven、Java、Node.js、npm、PostgreSQL 或 RabbitMQ。

统一测试入口：

```bash
./scripts/test-docker.sh
```

脚本默认使用独立 Compose 项目 `practice-platform-test`（CI 覆盖为 `practice-platform-ci`），依次构建测试镜像、启动临时 PostgreSQL/RabbitMQ、运行三个测试服务并汇总退出码；无论成功或失败都会执行 `down --remove-orphans` 清理测试容器和网络。

| 服务 | 作用 |
| :--- | :--- |
| `test-db` | PostgreSQL 16 测试库，数据目录使用 `tmpfs` |
| `test-rabbitmq` | RabbitMQ 3.13 测试实例，不映射宿主机端口 |
| `backend-test` | 容器内执行 Spring Boot/MockMvc/PostgreSQL/Flyway 回归测试 |
| `worker-test` | 容器内执行判题核心与消息消费回归测试 |
| `frontend-test` | 容器内执行 `npm ci`、`npm run lint`、`npm run test`、`npm run build` |

隔离保证：

- 不读取真实 `.env`，测试账号、数据库名和 JWT 仅用于临时测试环境；
- 不复用 `oj-db`、`oj-rabbitmq`、`oj-pgdata`、`oj-docs` 或当前部署网络；
- 测试消息使用 `oj.test.*` 队列、交换机和路由键；
- DOCX 固定资源位于 `backend-spring/src/test/resources/docx`，上传临时目录使用容器 `tmpfs`；
- 测试服务不发布端口、不启动长期运行的后端/Worker/前端。
- Flyway 测试在临时 PostgreSQL schema 中覆盖空库、重复迁移、旧结构升级、
  历史数据保留、孤立数据阻断以及外键/`CHECK` 执行；
- Worker 测试不维护独立核心 schema，使用 Backend 已迁移完成的同一隔离测试库。

GitHub Actions 在推送到 `chore/ci-baseline`、`codex/feature-foresight`，以及目标为 `codex/feature-foresight` 或 `main` 的 Pull Request 上运行同一 Docker 测试入口。工作流只验证，不部署。

前端认证回归测试使用 Vitest、React Testing Library 和 jsdom，在容器内模拟 API、路由、`localStorage` 和文件下载。当前覆盖登录状态恢复、登录成功、受保护请求 401、登录接口 401、并发 401 去重、403 保持登录、携带 Authorization 的文档下载，以及 Token/`tokenVersion` 不进入可见 DOM。该范围不依赖真实浏览器或当前部署，因此没有引入 Playwright。

测试全部通过后可只构建正式镜像进行验证：

```bash
docker compose build
```

该命令只构建镜像；不要用测试流程启动、替换或停止现有网站。

### 判题语言与运行时

前端语言下拉由后端元数据提供，后端允许列表与 Worker `LanguageDef` 保持以下五种语言一致：

| 语言 | 提交 ID | Worker 命令 |
| :--- | :--- | :--- |
| Python 3 | `python` | `python3` |
| JavaScript (Node.js 22 LTS) | `javascript` | `node` |
| C | `c` | `gcc` |
| C++17 | `cpp` | `g++ -std=c++17` |
| Java 21 | `java` | `javac` / `java` |

测试与正式 Worker 镜像共用固定的 Node.js `22.22.3` 运行时。编译和运行命令使用参数列表传递，工作目录由 `ProcessBuilder.directory(...)` 设置，bash 包装层只负责 ulimit 并通过 `exec "$@"` 切换到真实编译器或运行时。

算法题创建和更新都会在后端拒绝缺失、空或结构错误的测试点；Worker 对历史空测试点返回 `SE`（`No test cases configured`），不会判为 AC 或增加 `solved_count`。


### 已知测试基线边界

- DOCX 比较当前只读取第一个非空 Run，表格支持边界按现状记录；本阶段不重写 Word 评分算法。
- 前端构建仍提示主包体积较大和 Browserslist 数据陈旧，但不影响 lint 与构建成功。

---

## 🛠️ 本地开发

为保持开发、CI 与部署环境一致，本项目不要求在宿主机安装 Maven、Java、Node.js 或 npm。代码变更应先通过上述 Docker 测试入口；需要验证正式多阶段 Dockerfile 时只执行：

```bash
docker compose build
```

需要启动完整开发部署时参照“快速开始”中的 Compose 命令，并使用独立的开发配置；测试脚本不会操作该部署。

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
