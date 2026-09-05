# 不可变本地发布与元数据合同

学校操作员先读 [学校部署](SCHOOL_DEPLOYMENT.md) 和 [运维手册](OPERATIONS.md)。本文面向准备制品、审核身份与发布清单的维护者；不提供自动生产环境部署脚本。

```text
已接受源码 + CI → 附注标签 → 固定本地 OCI 镜像
    → 镜像 ID／发布清单 → 本校配置与恢复就绪 → 受控部署 → 观察
```

当前已接受基线：`v0.9.1`，源码 `f273f3be519019841f050275d2b04bc2f4406797`，Flyway V9。生产环境使用四个已验收应用镜像，不使用任意移动分支 HEAD、隐含 latest 或临时重建制品。正式 [Compose](../docker-compose.release.yml) 无 `build:`、`pull_policy: never`，不依赖项目镜像远程发布。

## 外部文件与完整字段

[.env.production.example](../.env.production.example) 只提供配置类别与占位符。实际秘密在 Git 外的 `FORMAL_ENV_FILE`；非秘密发布元数据在外部 `RELEASE_ENV_FILE`。必须将两个绝对路径显式导出为环境变量；[release-common.sh](../scripts/release-common.sh) 的未覆盖默认值仍指向历史 v0.4.0-foundation 文件，不能用于新校部署。

[v0.4.0-foundation.env.example](../deploy/releases/v0.4.0-foundation.env.example) 是**历史恢复合同样本**，不是当前学校安装配置：其中镜像 ID、卷 ID、路径、网络绑定不能复制到新校。保留它用于追溯，不移动旧标签，不覆盖旧镜像证据。

下表列出 [发布预检脚本](../scripts/release-preflight.sh) 的元数据合同，值从实际已验收制品／本校资源／恢复材料取得。不要把尖括号占位值或空文件写进去假装通过。

| 类别 | 字段 | 填写依据 |
| --- | --- | --- |
| 发布身份 | `RELEASE_VERSION`, `RELEASE_TAG`, `RELEASE_GIT_SHA`, `RELEASE_MAIN_SHA` | v0.9.1 的两个 SHA 均为上面的完整发布提交 |
| 数据库结构／构建 | `RELEASE_FLYWAY_VERSION`, `RELEASE_BUILD_TIME`, `EXPECTED_OCI_VERSION` | `9`、真实固定 UTC `YYYY-MM-DDTHH:MM:SSZ`、`v0.9.1` |
| T1 来源 | `PREVIOUS_PRODUCTION_GIT_SHA`, `PREVIOUS_PRODUCTION_FLYWAY_VERSION`, `BACKUP_TOOL_GIT_SHA`, `T1_PRODUCTION_RUNTIME_GIT_SHA` | 前版本来自实际运行；工具 SHA 为本次发布 SHA；T1 运行版本等于前版本 SHA |
| 基础镜像 | `POSTGRES_IMAGE`, `RABBITMQ_IMAGE` | 已验收且本机存在的 `@sha256:...` 引用 |
| 应用镜像 | `BACKEND_IMAGE`, `WORKER_IMAGE`, `RUNNER_IMAGE`, `FRONTEND_IMAGE` | 本地固定标签，如 `oj-backend:v0.9.1`，不是仓库路径或隐含 latest |
| 镜像证据 | `EXPECTED_BACKEND_IMAGE_ID`, `EXPECTED_WORKER_IMAGE_ID`, `EXPECTED_RUNNER_IMAGE_ID`, `EXPECTED_FRONTEND_IMAGE_ID` | 本机实际不可变 `sha256:...`，与已接受清单一致 |
| 数据库名 | `POSTGRES_DB` | 本校实际数据库，不在更新时换库 |
| 外部资源 | `FORMAL_POSTGRES_VOLUME`, `FORMAL_RABBITMQ_VOLUME`, `FORMAL_DOCS_VOLUME`, `FORMAL_NETWORK` | 实际保留的三个卷和网络 |
| 容器名 | `FORMAL_POSTGRES_CONTAINER`, `FORMAL_RABBITMQ_CONTAINER`, `FORMAL_BACKEND_CONTAINER`, `FORMAL_WORKER_CONTAINER`, `FORMAL_RUNNER_CONTAINER`, `FORMAL_FRONTEND_CONTAINER` | 常用 `oj-db`, `oj-rabbitmq`, `oj-backend`, `oj-worker-1`, `oj-runner`, `oj-frontend`；以现场为准 |
| Web | `FORMAL_FRONTEND_PORT` | 学校建议 `3000` |
| 恢复文件 | `POSTGRES_LOGICAL_BACKUP`, `POSTGRES_GLOBALS_BACKUP`, `POSTGRES_BASE_BACKUP`, `RABBITMQ_DEFINITIONS_BACKUP`, `DOCS_BACKUP` | 对应已存在的受限恢复文件；不能是占位符 |

学校网络额外设置 `FORMAL_FRONTEND_BIND=127.0.0.1`、`RABBITMQ_MANAGEMENT_BIND=127.0.0.1`、`RABBITMQ_MANAGEMENT_PORT=15672`。Runner 配置和安全字段见学校指南；`OPS_ADMIN_*` 是额外运维凭据输入，不包含于发布预检的秘密变量检查列表，仍须独立验证。

`backup.sh` 生成的是 PostgreSQL 逻辑导出 + Office 配对包，**不会同时生成全局对象、物理基础备份或 RabbitMQ definitions（拓扑定义）**。完整发布预检仍要求表中全部恢复路径存在；这些材料由学校批准的独立恢复流程准备。存在性不等于完整性验证，不得以同一个空文件填满合同。首次空白安装不存在历史运行/T1，按学校指南单独记录首次初始化验收，不伪造前版本。

发布报告采用 [发布记录模板](release-manifest-template.md)，只记录非秘密身份、ID、计数、校验结果与时间。

## 已接受镜像的校验与传递

有已接受制品时**导入并验证，不重建**。镜像传输包及其校验和由学校批准的安全渠道提供，例如在确认外部文件后使用 `docker load --input /srv/release-package/accepted-images.tar`。同时准备五种 Runner 沙箱镜像与备份辅助镜像 `postgres:16-alpine`。镜像支持的 CPU 架构必须匹配服务器。

固定标签可被重新指向，所以还必须比对镜像 ID 和 OCI 标签。例如下面只读检查 Backend，其他三个应用也要逐一相同核对：

```bash
docker image inspect oj-backend:v0.9.1 --format \
  '{{.Id}} {{index .Config.Labels "org.opencontainers.image.revision"}} {{index .Config.Labels "org.opencontainers.image.version"}}'
```

不允许在已有 v0.9.1 标签下静默换代码或将新构建 ID 冒充历史验收 ID。基础镜像、安全修复与构建工具升级也要另走验收。

## 维护者：准备未来本地制品

以下展示**真实 Dockerfile 的构建形态**，不是操作员重新构建已接受 v0.9.1 的指令。仅在未来发布准备阶段、固定版本且工作树干净的源码、CI 已通过且审批允许构建时使用；不在生产环境维护窗口执行。`release_version` / `release_sha` / `release_build_time` 由该次发布提供，不能用任意 HEAD 或每条命令重新取时间。

```bash
: "${release_version:?set approved release tag}"
: "${release_sha:?set approved full source SHA}"
: "${release_build_time:?set one immutable UTC build timestamp}"
test "$(git rev-parse HEAD)" = "$release_sha"
test "$(git rev-parse "$release_version^{}")" = "$release_sha"
test -z "$(git status --porcelain)"
build_release_image() {
  local component="$1" context="$2"
  shift 2
  docker build --target runtime \
    --label "org.opencontainers.image.revision=$release_sha" \
    --label "org.opencontainers.image.created=$release_build_time" \
    --label org.opencontainers.image.source=mkbkakwk/Practice-Platform \
    --label "org.opencontainers.image.version=$release_version" \
    --tag "oj-$component:$release_version" "$@" "$context"
}
build_release_image backend backend-spring
build_release_image worker worker --build-context fixtures=./test/fixtures/runner
build_release_image runner runner
build_release_image frontend frontend \
  --build-arg VITE_DEPLOY_ENV=production --build-arg "VITE_BUILD_SHA=$release_sha"
```

Dockerfile 的 runtime 目标构建不等同于测试（Java 打包时跳过测试）；先完成 [test-docker.sh](../scripts/test-docker.sh) 和 CI 合同再验收制品。Worker 命名构建上下文来自现有 [测试配置](../docker-compose.test.yml)。Frontend 元数据在构建时固化，不靠运行时环境变量注入预发布角标。

沙箱 Dockerfile 保留在 [sandbox-images](../sandbox-images/)，也须单独准备、记录并测试；不能靠 Worker 本地执行兜底。完整镜像 ID 与 OCI revision/version 应填入外部清单后再部署。

## 门禁按实际能力分阶段

| 工具／验证 | 何时使用 | 不应误解为 |
| --- | --- | --- |
| Compose `config --quiet`、image inspect、外部资源核对 | 维护前，无运行变更 | 全部业务验收 |
| [T1 来源预检脚本](../scripts/release-t1-preflight.sh) | 旧生产环境仍在运行，T1 前；预期／观察／声明来源相等，工具 SHA 可不同 | 自动创建备份或停写 |
| 内部 Backend/Runner 就绪、Flyway、沙箱、Office、队列／Outbox | 新版 Backend+Runner，Worker/Frontend 保持停止 | 完整 ops-check |
| [发布预检脚本](../scripts/release-preflight.sh) | **目标六服务已经运行**且恢复合同齐全时的完整只读审计 | 空白安装向导、旧 V4 对目标 V9 的上线前一次性检查 |
| [ops-check.sh](../scripts/ops-check.sh) | 正常六服务／提交点后，有 OPS 登录和备份目录 | Worker 保持停止时必须通过的门禁 |
| [release-status.sh](../scripts/release-status.sh) | 只读状态证据 | 自动修复或充分的单一成功信号 |

`release-preflight.sh` 检查标签类型与指向、SHA 祖先关系、实际应用 ID/OCI、资源／恢复文件存在性、Compose 插值、六容器健康、目标 Flyway 与 Frontend 路由。不修改资源，但在目标服务未启动时一定不能完整通过。

学校 [分阶段启动升级流程](SCHOOL_DEPLOYMENT.md) 必须使用选择性 `up -d --no-deps backend runner`，然后分别单独启动 Worker、Frontend，保留独立业务入口屏障至提交点。没有“一个宽泛 up 命令”可以替代该流程。

## 版本与恢复边界

当前生产环境日常运行版本是 v0.9.1 / V9；历史 v0.4.0-foundation / V4、v0.9.0（尚未部署到生产环境就被修正版替代）仍保留追溯材料。它们不是当前部署默认值。三类权威持久状态不得在日常更新中删除或替换。

任何跨数据库结构回退先证明兼容性；需要数据恢复时遵守 [运维手册](OPERATIONS.md) 的全新 T1 配对／零消息／清洁目标与提交前边界。提交后出现新业务写入时，禁止自动旧版本恢复。
