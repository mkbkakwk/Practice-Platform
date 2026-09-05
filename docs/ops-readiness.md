# 发布拓扑与健康检查语义

本文记录 Stage 9A 引入的运维设计：直接以运行服务的状态为依据，不另建一套计分、消息队列或监控系统。该设计本身不会部署服务、备份或恢复数据。

## 正式发布拓扑

不可变发布 Compose 包含 Frontend、Backend、PostgreSQL、RabbitMQ、Runner、Worker 六个服务。PostgreSQL、RabbitMQ、DOCX 存储卷及生产网络继续使用既有外部持久资源。

正式 Compose 中，Worker 只能以 `remote` 模式评测。Compose 插值时就要求提供可信 Runner 地址和令牌，因此 Runner 配置缺失时不会静默回退到本地执行。Runner 是唯一能访问 Docker socket 的应用服务。不可信学生代码只在已有的一次性沙箱容器内执行；学生容器不会获得控制服务使用的 socket。

## 接口约定

| 服务 | 存活检查（Liveness） | 就绪检查（Readiness） | 含义 |
| --- | --- | --- | --- |
| Backend | `GET /api/health` | `GET /api/readiness` | 存活接口公开，只返回 `status`。就绪状态要求 PostgreSQL 可用且 Flyway 已初始化。RabbitMQ 有意不作为后端就绪的硬依赖，因为消息服务故障时事务型 Outbox 仍能安全保存任务。 |
| Worker | `GET /api/health` | `GET /api/readiness` | 要求 PostgreSQL 可用、RabbitMQ 监听器处于活动状态，且 Runner 已就绪。 |
| Runner | `GET /api/liveness` | `GET /api/readiness` | 复用已有 `sandboxAvailable` 信号，涵盖 Docker Engine 和所需沙箱镜像的可用性。 |

普通依赖故障不应因存活检查而演变成重启循环。当服务暂时无法安全接收相应工作时，就绪接口返回 `503`，但进程保持运行，依赖恢复后可自行恢复。公开或容器健康响应仅包含 `status`，不泄露主机名、队列深度、存储路径、凭据或令牌。

依赖探测在 HTTP 请求线程之外运行，时间预算上限为 750 毫秒。依赖卡住时可快速返回 `503`，不会长期占用请求线程等待连接池或网络默认超时。

Worker 就绪检查同时要求 Rabbit 监听器生命周期处于活动状态，并执行有时间上限、不修改状态的 AMQP 连接／通道探测。即使 Spring 监听器注册表仍显示运行中，也能发现消息服务中断。探测不发布消息，不修改队列、交换机或绑定关系。

## 版本证据

Backend 从不可变发布元数据读取 `APP_GIT_SHA`、`APP_VERSION`、`APP_BUILD_TIME`。`APP_GIT_SHA` 始终是完整的 40 位源码提交 SHA，与预发布环境使用的短镜像标签分开记录。构建时间是制品自身固定的 UTC ISO-8601 时间戳，不是容器启动时间。

`GET /api/admin/version` 仅供管理员访问，返回这三个字段及当前 Flyway 版本。公开健康接口不返回版本或数据库结构信息。

前后端发布镜像由同一个已记录的发布提交构建，并携带相同的 OCI revision 标签。预发布前端角标只在预发布构建中启用。

## 发布预检

`scripts/release-preflight.sh` 保持只读：验证固定本地镜像 ID 和 OCI 标签、所需 PostgreSQL／RabbitMQ／JWT／Runner 变量、Runner 镜像与容器、外部持久资源、解析后的 Compose 配置、Flyway 版本和存活状态。输出仅包含字段是否存在及验证结果，不输出秘密值。

备份、保留策略、仪表板、Prometheus 指标、结构化日志和恢复演练不属于最初 Stage 9A 的范围。当前完整操作流程见[日常运维手册](OPERATIONS.md)。
