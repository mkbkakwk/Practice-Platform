# 学校部署指南

本指南面向校园 IT 管理员、计算机教师和机房管理员。Practice-Platform 用于编程练习、比赛、教学分析与 Office 文档实践。已验收基线为 **v0.9.1 / `f273f3be519019841f050275d2b04bc2f4406797` / Flyway V9**，已通过真实生产环境的小范围试点。

常规课堂／校园使用已完成试点验收；**大规模同时比赛容量尚未认证**。本指南不承诺某个在线人数或每秒评测量。

## 1. 先分清三种环境

| 用途 | 配置 | 数据与操作边界 |
| --- | --- | --- |
| 个人体验／开发 | [docker-compose.yml](../docker-compose.yml) | 可构建，含开发默认值；只能在隔离开发机使用 |
| 集成分支 / 预发布环境 | [docker-compose.staging.yml](../docker-compose.staging.yml) | 验证变更，独立账号、卷、网络与端口 |
| 学校正式服务 | [docker-compose.release.yml](../docker-compose.release.yml) | 无 `build:`，使用固定本地镜像、外部持久卷和正式密钥 |

开发 Compose 的固定容器名可能与正式环境冲突，不能仅用不同项目名 `-p` 就认为已经隔离。不要在学校正在使用的服务器运行 README 的开发启动命令。

## 2. 服务器起点建议，不是容量认证

| 项目 | 起步建议 | 较大课堂可考虑 |
| --- | --- | --- |
| 操作系统 | Ubuntu Server 24.04 LTS，64 位 | 先在同版本隔离环境演练 |
| CPU | 8 核 | 12–16 核 |
| 内存 | 16 GB | 32 GB |
| 存储 | SSD 200 GB 以上 | 按文档、日志、镜像和备份增长预留空间 |
| 网络 | 固定校园 LAN IP | 内部 DNS、稳定链路、受限管理通道 |
| 软件 | Docker Engine、Compose 插件、Git、Bash、curl、Python 3、常规 GNU 工具 | 记录并验证实际安装版本 |

使用 [Docker 官方 Ubuntu 安装指南](https://docs.docker.com/engine/install/ubuntu/) 安装 Engine、Compose 和 Buildx，并安排补丁维护。不要把测试用的一键安装脚本当作正式升级策略。Docker 管理权限接近宿主机管理权限，只授予可信操作员。

评测容量主要受同时提交数、语言编译成本、用例数量与运行时间、Runner 并发／CPU／内存／PID 限制影响。在线浏览人数不等于同时评测能力。大型校级比赛前须用代表性题目单独压测，并记录延迟、队列峰值、失败率和资源余量。

## 3. 校园网络与六服务拓扑

```text
教师／学生浏览器
      │ 学校 LAN（或经批准的互联网入口）
      ▼
校内 DNS 或服务器 IP : 80 / 443
      │
宿主机 Nginx / Caddy
      │ http://127.0.0.1:3000
      ▼
Frontend ──► Backend ──► PostgreSQL
                │
             RabbitMQ ──► Worker ──► Runner ──► 临时评测沙箱
                Backend ──► Office 持久卷
```

| Compose 服务名 | 职责 | 校园网直接暴露 |
| --- | --- | --- |
| `db`（PostgreSQL） | 账号、题目、提交、比赛、分析与 Office 引用 | 否 |
| `rabbitmq` | 评测消息 | 否；管理界面默认仅 `127.0.0.1:15672` |
| `backend` | 业务 API、权限、Flyway、Office | 否，由 Frontend 代理 API |
| `worker` | 消费评测任务，调用远程 Runner | 否 |
| `runner` | 管理 Docker 评测沙箱 | 否 |
| `frontend` | 网页与 API 转发 | 仅绑定宿主机 `127.0.0.1:3000`，由外层代理开放 |

**只有 Runner 挂载 `/var/run/docker.sock`**。Worker 使用 `JUDGE_EXECUTION_MODE=remote`、`RUNNER_BASE_URL=http://runner:8080`，不允许为了方便给 Backend/Worker/Frontend 加 Docker socket。

### 访问模式

- **仅校园 LAN**：例如 `http://10.20.30.40`，或内部 DNS。该地址仅为占位示例。即便是 LAN，真实账号也推荐 HTTPS；HTTP 不加密密码和会话，须评估并接受相应风险。
- **校园域名**：例如 `https://oj.school.example`，配置校内 DNS 和客户端信任的 TLS 证书。`.example` 是占位域，必须替换。
- **互联网访问**：必须 HTTPS、反向代理、防火墙与最小端口开放；仅开放需要的 Web 端口，SSH 限管理来源。不要向学生网或互联网开放 DB、AMQP、Runner、Worker。

RabbitMQ 管理访问优先经 SSH 隧道。只有明确的安全运维需求才改变绑定，并同步做网络访问控制。Docker 发布端口可能绕过简单的 UFW 规则，必须核查实际绑定及宿主机规则，参见 [Docker 防火墙说明](https://docs.docker.com/engine/install/ubuntu/#firewall-limitations)。

## 4. 固定版本与外部配置

以下命令在 **Ubuntu Bash、仓库根目录**执行，属于操作手册示例，不是无条件一键脚本。任一校验或命令失败立即停止，不继续下一阶段。先取得学校授权与验收过的发布包。已有学校环境应跳至升级章节，不重复初始化。

```bash
set -euo pipefail
set +x
git checkout v0.9.1
test "$(git rev-parse 'v0.9.1^{}')" = f273f3be519019841f050275d2b04bc2f4406797
test "$(git rev-parse HEAD)" = f273f3be519019841f050275d2b04bc2f4406797
test -z "$(git status --porcelain)"
```

不要部署任意 `main` HEAD 或集成分支的同步合并 SHA。保留完整所需 Git 历史，以便验证工具和被备份运行版本的 Git 提交。

### 密钥文件

以 [.env.production.example](../.env.production.example) 为字段参考，在仓库外创建学校自己的受限文件，例如 `/etc/practice-platform/production.env`。父目录仅操作员可访问，文件权限 `0600`；Windows 使用仅操作员与 SYSTEM 的 ACL。不要提交运行时 `.env`，不要把示例占位密码直接上线。

| 类别 | 实际变量／要求 |
| --- | --- |
| PostgreSQL | `POSTGRES_USER`、`POSTGRES_PASSWORD`；数据库名 `POSTGRES_DB` 放发布元数据 |
| RabbitMQ | `RABBITMQ_USER`、`RABBITMQ_PASSWORD` |
| 登录 | `JWT_SECRET` 高熵随机且至少 32 字符；`JWT_EXPIRES_IN` 按学校策略 |
| 校园来源 | `CORS_ORIGIN=https://oj.school.example`，匹配实际协议、主机和端口，不使用 `*` 或遗漏为 localhost |
| Runner | `RUNNER_TOKEN` 随机独立；`DOCKER_SOCKET_GID` 用 `stat -c %g /var/run/docker.sock` 获取，不猜测 |
| 沙箱镜像 | `RUNNER_DOCKER_PYTHON_IMAGE`、`RUNNER_DOCKER_JAVASCRIPT_IMAGE`、`RUNNER_DOCKER_C_IMAGE`、`RUNNER_DOCKER_CPP_IMAGE`、`RUNNER_DOCKER_JAVA_IMAGE`；镜像须已在本机验收 |
| 运维身份 | `OPS_ADMIN_USERNAME`、`OPS_ADMIN_PASSWORD`：已存在的本校管理员凭据；示例文件未包含这两项，需在外部文件安全补充 |
| 安全初始化 | `PROMOTE_FIRST_ADMIN=false`；正式 Compose 还会强制关闭，见首次管理员章节 |

每个变量名只定义一次，不让密钥与发布文件互相覆盖。使用干净的操作员 shell，避免已导出的开发变量覆盖 Compose 的 `--env-file` 值。使用纯 `KEY=value`、UTF-8 无 BOM、无多行值；现有发布辅助脚本按行读取，不是通用的环境变量文件解析器。自动生成密钥宜用高熵十六进制字符串等无需引号／插值的字符集。已有凭据含 `$`、空格或引号时，须在隔离环境核对 Compose、Bash 与应用实际收到的值；不要改密码来掩盖解析问题。不要执行不可信的环境变量文件，不要使用 `set -x` 或输出完整 `docker compose config`。

### 发布元数据，不含密码

单独保存 `/etc/practice-platform/releases/v0.9.1.env`。从学校自己的验收清单填写，不复制其他服务器的卷 ID 或备份路径。[不可变发布合同](immutable-release-workflow.md) 列出完整字段。

发布字段的核心值为 `RELEASE_VERSION=v0.9.1`、`RELEASE_TAG=v0.9.1`、`RELEASE_GIT_SHA` / `RELEASE_MAIN_SHA` 均为上面的完整发布 SHA，`RELEASE_FLYWAY_VERSION=9`、`EXPECTED_OCI_VERSION=v0.9.1`。`RELEASE_BUILD_TIME` 必须是发布镜像的真实固定 UTC 构建时间，不能每次重启重新生成。

应用使用 `BACKEND_IMAGE` / `WORKER_IMAGE` / `RUNNER_IMAGE` / `FRONTEND_IMAGE`，例如本地固定标签 `oj-backend:v0.9.1`；并填写四个 `EXPECTED_*_IMAGE_ID`。`POSTGRES_IMAGE` / `RABBITMQ_IMAGE` 使用验收过的内容摘要（digest）引用。Compose 不拉取也不构建，必须提前导入学校收到的已验收镜像（以及沙箱镜像），核对 ID、架构和 OCI SHA／版本。不能把其他机器重建得到的镜像自动视为原已接受制品。

显式指定这两个外部文件，避免发布辅助脚本回落到历史 v0.4.0 默认路径：

```bash
export FORMAL_ENV_FILE=/etc/practice-platform/production.env
export RELEASE_ENV_FILE=/etc/practice-platform/releases/v0.9.1.env
export COMPOSE_DISABLE_ENV_FILE=1
formal_compose() {
  docker compose --env-file "$FORMAL_ENV_FILE" --env-file "$RELEASE_ENV_FILE" \
    -p oj -f docker-compose.release.yml "$@"
}
formal_compose config --quiet
formal_compose config --services
```

`formal_compose` 是本指南明确给出的 Bash 函数，不是仓库自带脚本。服务输出应恰为 `db rabbitmq backend runner worker frontend`。只验证配置，不会创建容器。全量配置可能含密钥，不要粘贴到终端记录或工单。

## 5. 持久资源：首次创建与既有资源不能混淆

| 数据 | 元数据变量 | 保护要求 |
| --- | --- | --- |
| PostgreSQL | `FORMAL_POSTGRES_VOLUME` | 业务权威数据库 |
| RabbitMQ | `FORMAL_RABBITMQ_VOLUME` | 消息持久状态，不用清空队列掩盖错误 |
| Office DOCX | `FORMAL_DOCS_VOLUME` | 必须与数据库配对备份／恢复 |
| 服务网络 | `FORMAL_NETWORK` | `external: true`，使用审批过的网络 |

正式 Compose 要求以上资源预先存在，缺失即失败，避免误建空白替代卷。**已有部署只核对并复用真实名称**，包括已有匿名卷的完整 ID；不要执行删卷、`down -v` 或 `volume prune`。

仅对确认全新的空白学校安装，操作员可先创建命名资源，例如：

```bash
docker volume create school-oj-postgres
docker volume create school-oj-rabbitmq
docker volume create school-oj-office
docker network create school-oj-network
```

这四个名字仅用于新安装示例，必须原样对应本校元数据；不能用于替换既有生产环境存储。服务器还需备份到另一受控介质或主机，不能让在线卷与唯一备份同时丢失。

## 6. 校园反向代理

在发布元数据固定：

```dotenv
FORMAL_FRONTEND_BIND=127.0.0.1
FORMAL_FRONTEND_PORT=3000
RABBITMQ_MANAGEMENT_BIND=127.0.0.1
RABBITMQ_MANAGEMENT_PORT=15672
```

以下为**宿主机 Nginx** TLS 示例，放入经学校管理的 Nginx 配置中；证书路径、域名都是占位符。首次初始化完成前不开放入口。Nginx 若在容器内，`127.0.0.1` 并非宿主机，本例不能直接照搬。

```nginx
server {
    listen 80;
    server_name oj.school.example;
    return 301 https://oj.school.example$request_uri;
}
server {
    listen 443 ssl;
    server_name oj.school.example;
    ssl_certificate /etc/nginx/tls/oj.school.example/fullchain.pem;
    ssl_certificate_key /etc/nginx/tls/oj.school.example/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
    }
}
```

校内 HTTP 模式可使用单个 `listen 80` server 配置块和相同的 location 代理配置，不配置 TLS 跳转；同步改成实际 HTTP `CORS_ORIGIN`。Caddy 可承担同样入口，但证书签发／内部 CA 信任应由学校管理。配置通过 `nginx -t` 后才按学校变更流程加载。[Nginx 代理参数参考](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)。

上传大小取外层代理、内置 Frontend Nginx、Backend 三者最低限制。当前 [Frontend 配置](../frontend/nginx.conf) 未覆盖 Nginx 默认 `client_max_body_size`（[默认 1m](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_max_body_size)）；仅提高外层限制不能保证较大 Office 文件可上传。遇到 413 应记录并走配置／版本验收流程，不直接修改已接受镜像。

## 7. 新校首次安装与管理员初始化

这是**尚未向师生开放、数据库完全为空**的新安装流程，不是现有生产环境的密码恢复流程。

1. 完成固定源码／镜像、密钥、卷、网络、存储空间核验，外层校园入口保持关闭。先 `formal_compose up -d db rabbitmq`，等待健康。
2. 源码 [AuthService](../backend-spring/src/main/java/com/oj/service/AuthService.java) 只在 `promoteFirstAdmin=true` 且**用户总数为 0**时提升首次注册者。正式 Compose 强制 `PROMOTE_FIRST_ADMIN=false`，只改环境变量文件不会启用；没有默认管理员密码，也不能靠 `OPS_ADMIN_*` 自动创建账号。
3. 在 Git 外准备**一次性、审批过的初始化覆盖配置**，仅含下方设置。保持入口关闭且只有操作员通过 localhost／SSH 隧道访问；已有任何用户或状态不明，停止并交给账号恢复流程，禁止删用户重新初始化。

```yaml
services:
  backend:
    environment:
      PROMOTE_FIRST_ADMIN: "true"
```

例如保存为 `/etc/practice-platform/bootstrap-admin.yml` 后（正常发布不得带此覆盖配置）：

```bash
docker compose --env-file "$FORMAL_ENV_FILE" --env-file "$RELEASE_ENV_FILE" \
  -p oj -f docker-compose.release.yml -f /etc/practice-platform/bootstrap-admin.yml \
  up -d --no-deps backend
formal_compose up -d --no-deps runner frontend
```

4. Backend 会在空数据库初始化至 V9。通过正常注册界面创建唯一首位管理员，使用学校批准的随机密码，验证角色为 ADMIN。不使用开发演示数据，不直接改业务表，不让学生抢先注册。
5. 立即以**不带覆盖配置的正式 Compose**重建 Backend 容器，使 `PROMOTE_FIRST_ADMIN=false` 恢复生效；不是重建镜像：

```bash
formal_compose up -d --no-deps --force-recreate backend
formal_compose up -d --no-deps worker
formal_compose ps
```

6. 等六服务全部健康，核查 Backend 实际初始化开关已关闭，再验证管理员登录。移除一次性覆盖配置的运行引用并妥善收管。不要在正常生产环境留下开启设置。
7. 将该本校管理员的运维凭据安全配置为外部文件中的 `OPS_ADMIN_USERNAME` / `OPS_ADMIN_PASSWORD`，验证登录和管理员只读权限。不要打印凭据／JWT。先建立初始配对备份、恢复演练和完整运维就绪证据，再开放校园入口。

完整 `release-preflight.sh` 还要求前序运行身份与多类恢复文件，**不是空白安装向导**。新安装不存在“前一生产环境 SHA”，不能用历史 V4 身份或空文件伪造它；如尚无这些证据，应明确记录为首次安装验收范围，建立首个运行与恢复基线，再使用适用的完整发布审计。不要声称该脚本在空白系统上通过。

## 8. 师生入门

管理员通过产品内用户管理功能分配教师角色；教师创建题目、测试用例、Office 练习和比赛；学生正常注册／登录（`USER`）、参与活动并查看结果。不得在 DB 手工改角色或判定来让验收通过。

先用最少的真实试点账号演练 AC／WA、比赛榜单、教学分析、Office 提交与授权下载，核对最终消息／Outbox 清空、文档引用完整。保留试点记录，不为了测试结束而删除正式数据。

## 9. 已有学校环境升级：受控分阶段启动

正常变更链：**Issue → 功能／修复分支 → Docker 测试 → PR → 集成分支／预发布环境 → 已接受固定发布 → 全新 T1 → 受控部署 → 观察**。这里不是自动发布脚本，必须有针对本校当前版本、入口和恢复方法的审批及演练。

| 阶段 | 必须完成 | 不应做 |
| --- | --- | --- |
| 维护前 | 当前身份／健康、Office、无遗留任务、队列、OPS 登录就绪、备份封装程序、恢复包 | 未备好凭据就进入维护 |
| 维护与 T1 | 独立入口写入屏障生效；停 Frontend/Worker；确认无消费者／在途工作；全新配对备份完整验证；备份退出后再次确认 Worker 停止 | 复用已恢复营业前的历史 T1；清空队列 |
| 提交点前 | 仅 Backend + Runner；预期 Flyway；身份／就绪／沙箱／Office／队列／Outbox；Worker/Frontend 未运行 | 完整 ops-check、上传、提交、比赛写操作 |
| 提交顺序 | 启 Worker → Worker→Runner／零消息 → 启 Frontend → 路由与六服务验收 → 记录提交点 → 开放业务入口 | 把容器启动等同于重新开放写入 |
| 提交点后 | 管理员／权限、比赛／教学分析、Office 只读验收、完整 ops-check、运行观察 | 自动回退旧库或盲恢复 T1 |

**写入屏障必须独立于 Frontend 容器。** 本项目没有单一全局只读开关；可在已演练的外层反向代理／防火墙限制正常用户访问，仅允许操作员验收。覆盖所有正常入口，保持到提交点之后。仅停旧 Frontend 不足以在新 Frontend 启动后继续关闭业务访问。

针对当前已运行、已核验名称的环境，选择性命令形态为：

```bash
# 每行都有上述人工审批／验证前置条件，不能整段无条件执行。
formal_compose stop frontend worker
# 此处：静默边界、全新 T1、验证与备份 cleanup 后重新 stop/确认 worker。
formal_compose up -d --no-deps backend runner
# 此处：仅 Backend/Runner 的全部提交前验收。
formal_compose up -d --no-deps worker
# 此处：Worker 就绪、Runner 联通、队列与 Outbox 检查。
formal_compose up -d --no-deps frontend
# 此处：六服务验收、记录提交点，再经入口控制开放业务。
```

升级时不要运行无服务选择的全体 `up -d`；不要无必要重建 DB/RabbitMQ。`--no-deps` 不会替你证明依赖健康，因此每阶段必须查实际容器状态。若执行失败，按 [运维手册](OPERATIONS.md) 的提交前／提交后边界处置。

## 10. 上线后

按 [运维手册](OPERATIONS.md) 安排巡检、配对备份、异机副本、保留策略和定期隔离恢复演练。课堂规模试点通过不等于大型比赛认证；后续工作由真实反馈、缺陷、容量需求和明确新需求驱动，不自动启动新开发阶段。
