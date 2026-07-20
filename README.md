<div align="center">

# 🧑‍💻 Practice Platform

### 一个开箱即用的在线练习平台 · 算法评测 · 多语言支持 · Docker 一键部署

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Node](https://img.shields.io/badge/Node-20%2B-339933?logo=node.js&logoColor=white)](https://nodejs.org/)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=white)](https://react.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](https://docs.docker.com/compose/)

**在线算法练习 · 自动评测 · 排行榜** · 为教学与练习场景设计，未来将拓展 Office 操作等更多练习模块。

</div>

---

## 📖 项目简介

Practice Platform 是一套面向教学和自学场景的在线练习系统。当前已实现 **算法评测（Online Judge）** 模块，支持学生提交代码、自动评测、查看结果与排行。架构上预留了多模块扩展能力，未来可加入 Office 操作练习、SQL 练习等更多题型。

### 为什么选择它

- 🚀 **真正的一键部署** —— 一条 `docker compose up -d --build` 搞定数据库、后端、前端，无需任何额外配置
- 🛡️ **安全的评测沙箱** —— 进程隔离 + 资源限制（CPU / 内存 / 时间 / 输出大小），超时强制清理进程组
- 🌐 **多语言支持** —— Python 3、JavaScript (Node)、C、C++17、Java 开箱即用
- 📝 **现代化编辑器** —— 内置 CodeMirror，语法高亮、多语言模板切换
- 🎯 **完整功能闭环** —— 注册登录 → 题库 → 提交评测 → 结果反馈 → 提交记录 → 排行榜
- 📦 **预置题库** —— 自带 6 道经典算法题，部署后立即可用
- 🔐 **权限分级** —— 首位注册用户自动成为管理员，可管理题目

---

## ✨ 功能特性

| 模块 | 功能 |
| :--- | :--- |
| 👤 用户 | 注册 / 登录（JWT）/ 个人中心 / 首位用户自动管理员 |
| 📚 题库 | 题目列表（分页、难度筛选）、Markdown 题面、样例 |
| 💻 评测 | 多语言代码编辑器、一键提交、实时评测结果 |
| 🏷️ 判定 | AC（通过）/ WA（答案错误）/ TLE（超时）/ RE（运行错误）/ CE（编译错误） |
| 📊 记录 | 全站提交动态、个人提交历史 |
| 🏆 排行榜 | 按通过题数排名 |
| ⚙️ 管理 | 管理员通过 API 增删题目 |

---

## 🚀 快速开始

### 前置要求

只需安装 [Docker](https://www.docker.com/) 与 Docker Compose（Docker Desktop 已包含）。

### 一键部署

```bash
git clone https://github.com/<你的用户名>/<仓库名>.git
cd <仓库名>
docker compose up -d --build
```

首次构建约 3–8 分钟（需拉取镜像并安装语言运行时）。之后启动只需几秒。

启动完成后访问 👉 **http://localhost:8080**

> 💡 **第一个注册的账号会自动成为管理员**，拥有添加题目的权限。

### 自定义配置（可选）

复制 `.env.example` 为 `.env` 并按需修改：

```bash
cp .env.example .env
# 编辑 .env（端口、数据库密码、JWT 密钥等）
docker compose up -d --build
```

### 常用命令

```bash
docker compose up -d --build   # 构建并后台启动
docker compose logs -f backend  # 查看后端日志
docker compose ps               # 查看容器状态
docker compose down             # 停止并移除容器
docker compose down -v          # 停止并清空数据（含数据库）
```

---

## 🧑‍💻 使用指南

### 学生使用流程

1. 打开 http://localhost:8080，点击 **注册** 创建账号
2. 在「题库」选择一道题目
3. 在右侧编辑器编写代码，选择语言，点击 **提交评测**
4. 查看评测结果：
   - ✅ **AC** —— 通过全部测试点
   - ❌ **WA** —— 显示失败测试点的输入、期望输出与你的输出
   - ⏱️ **TLE** —— 运行超时，提示第几个测试点超时
   - 💥 **RE** —— 运行错误（如除零、段错误）
   - 🔧 **CE** —— 编译错误，显示编译器报错信息
5. 在「提交记录」查看历史，在「排行榜」查看排名

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
    "samples": [{"input": "", "output": "Hello, World!"}],
    "testCases": [{"input": "", "output": "Hello, World!"}]
  }'
```

---

## 🏗️ 系统架构

```
┌─────────────────┐     /api/*       ┌─────────────────┐        ┌──────────────────┐
│    Frontend     │ ───────────────▶ │    Backend      │ ─────▶ │   PostgreSQL     │
│  (nginx + SPA)  │   reverse proxy │ (Express + 沙箱) │        │   (持久化存储)    │
│   React 静态站  │                  │  评测引擎 + API  │        └──────────────────┘
└─────────────────┘                  └─────────────────┘
                                            │
                                            ▼
                                     ┌─────────────────┐
                                     │  评测沙箱        │
                                     │  python3/g++/   │
                                     │  node/jdk       │
                                     └─────────────────┘
```

### 技术栈

| 层 | 技术 | 说明 |
| :--- | :--- | :--- |
| **前端** | React 19 · Vite · TypeScript · Tailwind CSS · shadcn/ui · CodeMirror · marked | 静态 SPA，nginx 托管与 API 反代 |
| **后端** | Node.js · Express · TypeScript · Prisma ORM · JWT · bcrypt | REST API + 评测引擎 |
| **数据库** | PostgreSQL 16 | 用户、题目、提交记录 |
| **评测** | 子进程隔离 · ulimit · 进程组管理 | 时间/内存/CPU/输出限制，超时 SIGKILL |
| **部署** | Docker · docker-compose | 多阶段构建，三容器编排 |

### 评测安全机制

用户提交的代码在后端容器内以**受限子进程**方式执行：

- 🧱 **独立进程组** —— 超时可通过 `kill(-pgid, SIGKILL)` 清理整个进程树
- ⏱️ **三重时间限制** —— 墙钟超时 + CPU 时间 ulimit + 容器级保护
- 💾 **内存限制** —— `ulimit -v` 虚拟内存上限（Java 例外，依赖容器限制）
- 📄 **输出限制** —— `ulimit -f` 防止输出爆炸撑爆磁盘
- 🔒 **容器隔离** —— 后端容器本身提供网络与文件系统隔离

> ⚠️ **生产环境建议**：当前沙箱适合教学/练习场景。若要公开到互联网，建议额外使用 `nsjail`、`firejail` 或 Docker-in-Docker 提供更强隔离。

---

## 📂 目录结构

```
practice-platform/
├── docker-compose.yml          # 一键编排：db + backend + frontend
├── .env.example                # 环境变量示例
├── README.md
│
├── backend/                    # 后端服务
│   ├── Dockerfile              # 多阶段构建（含语言运行时）
│   ├── prisma/
│   │   └── schema.prisma       # 数据库模型定义
│   ├── src/
│   │   ├── auth/               # 注册 / 登录 / JWT
│   │   ├── problems/           # 题目 CRUD
│   │   ├── submissions/        # 提交与评测入口
│   │   ├── judge/              # 评测沙箱核心
│   │   │   ├── runner.ts       # 子进程执行器
│   │   │   ├── judge.service.ts # 评测流程编排
│   │   │   └── languages.ts    # 多语言配置
│   │   ├── users/              # 排行榜 / 个人记录
│   │   ├── middleware/         # 鉴权中间件
│   │   ├── seed.ts             # 预置题目数据
│   │   ├── app.ts              # Express 应用
│   │   └── index.ts            # 入口
│   └── package.json
│
└── frontend/                   # 前端应用
    ├── Dockerfile              # 多阶段构建（build + nginx）
    ├── nginx.conf              # SPA 托管 + /api 反代
    ├── src/
    │   ├── lib/                # API 客户端 / Auth 上下文 / 样式工具
    │   ├── components/         # 通用组件（Navbar 等）
    │   ├── pages/              # 题库 / 详情 / 登录 / 记录 / 排行榜
    │   └── App.tsx             # 路由
    └── package.json
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
| POST | `/api/submissions` | ✅ | 提交评测 |
| GET | `/api/submissions` | - | 全站提交记录 |
| GET | `/api/submissions/:id` | ✅ | 提交详情（仅本人或管理员） |
| GET | `/api/users/me/submissions` | ✅ | 个人提交记录 |
| GET | `/api/users/leaderboard` | - | 排行榜 |

---

## 🛠️ 本地开发

无需 Docker 也能本地开发（需本地 PostgreSQL）：

```bash
# 后端
cd backend
npm install
# 配置 .env 指向本地 PostgreSQL：DATABASE_URL=postgresql://user:pass@localhost:5432/oj
npx prisma db push
npm run seed          # 导入预置题目
npm run dev           # http://localhost:4000

# 前端（另开终端）
cd frontend
npm install
VITE_API_BASE=http://localhost:4000/api npm run dev   # http://localhost:5173
```

### 添加评测语言

编辑 `backend/src/judge/languages.ts`，按 `LanguageDef` 接口添加新语言配置（compile / run / template），后端重启即生效。

---

## ⚙️ 环境变量

| 变量 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `PORT` | `8080` | 前端对外端口 |
| `POSTGRES_USER` | `oj` | 数据库用户 |
| `POSTGRES_PASSWORD` | `oj` | 数据库密码 |
| `POSTGRES_DB` | `oj` | 数据库名 |
| `JWT_SECRET` | `please-change-...` | JWT 签名密钥（**生产必改**） |
| `JWT_EXPIRES_IN` | `7d` | Token 有效期 |
| `CORS_ORIGIN` | `*` | 跨域来源 |
| `PROMOTE_FIRST_ADMIN` | `1` | 首位注册用户是否成为管理员 |

---

## 🗺️ 路线图

- [x] 算法评测（Python / JS / C / C++ / Java）
- [x] 题库、提交、记录、排行榜
- [x] Docker 一键部署
- [ ] 前端管理后台（可视化添加题目）
- [ ] **Office 操作练习模块**（Word / Excel / PPT）
- [ ] SQL 练习模块
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
