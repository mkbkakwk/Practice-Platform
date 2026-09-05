# 校园日常运维手册

适用基线：**v0.9.1 / `f273f3be519019841f050275d2b04bc2f4406797` / Flyway V9**。首次安装和网络设置见 [学校部署指南](SCHOOL_DEPLOYMENT.md)。本手册的命令示例在 Ubuntu Bash、固定源码目录执行；检查不等于授权恢复，写操作必须有明确的操作窗口和责任人。

## 1. 正常运行合同

- 正式拓扑为 `db`、`rabbitmq`、`backend`、`worker`、`runner`、`frontend` 六服务；只用 `docker-compose.release.yml` 和已验收的本地镜像。
- 只有 Runner 挂载 Docker socket；Worker 远程调用 Runner。数据库、队列与 Office 持久卷不得作为临时资源删除。
- 密钥在 Git 外受限存储，日志不含密码、JWT、Runner token。完整环境或 `docker inspect` 输出可能含密钥，不对外提供。
- RabbitMQ purge **不是日常恢复**。已开放业务后的旧 V4 回退不是通用恢复办法；数据库／Office／消息必须按事故一起评估。
- 小范围试点已经通过；大规模并发尚未认证。Worker 架构支持竞争消费，但正式 Compose 的固定 `container_name` 不支持直接 `--scale worker=N`；扩容是单独验收的容量变更。

按学校部署指南设置 `FORMAL_ENV_FILE`、`RELEASE_ENV_FILE`、`COMPOSE_DISABLE_ENV_FILE=1` 和 `formal_compose` 函数。两个文件必须来自批准的安全来源；每次操作确认项目、容器、卷及当前 SHA，不能凭旧终端变量猜测。

## 2. 每日／课前只读巡检

```bash
formal_compose ps
bash scripts/release-status.sh
curl --fail --silent --show-error http://127.0.0.1:3000/ --output /dev/null
curl --fail --silent --show-error http://127.0.0.1:3000/api/health
curl --fail --silent --show-error http://127.0.0.1:3000/api/readiness
df -h /var/lib/docker /srv/practice-platform-backups
docker system df
```

路径按学校实际 Docker data-root 和备份盘替换。不要从磁盘检查直接升级成 `prune`。六个容器都应健康；Frontend 端口为 `127.0.0.1:3000 -> 80`。`release-status.sh` 的输出要逐项判断，不能只看退出码。

Runner/Worker 就绪通过 Compose 的容器内健康检查和 Admin 系统状态验证，无需临时公开它们的端口。Runner 应 `sandboxAvailable=true`，Worker→Runner 应正常。记录各服务重启计数与时间范围；计数是容器生命周期累计值，计划重启与崩溃循环要分开解释。

### 队列与数据库聚合

优先使用 Admin 的系统状态页面：Main、Retry、DLQ、Worker、Runner、Outbox。以下是额外只读命令，不读消息正文，不发布／消费消息：

```bash
formal_compose exec -T rabbitmq rabbitmqctl list_queues -p / \
  name messages_ready messages_unacknowledged consumers
```

使用返回的实际队列名识别 Main/Retry/DLQ；不要把 `messages_ready=0` 当作没有在途任务，还要看未确认消息、Worker 和数据库 lease。下列 SQL **仅适用于已核验的 V9**，不会输出源代码、用户身份或消息 payload：

```bash
formal_compose exec -T -e PGOPTIONS='-c default_transaction_read_only=on' db \
  sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT verdict, count(*) FROM "Submission"
  WHERE verdict IN ('PENDING', 'JUDGING', 'JUDGE_FAILED') GROUP BY verdict;
SELECT count(*) AS expired_judging_leases FROM "Submission"
  WHERE verdict = 'JUDGING' AND judge_lease_until < CURRENT_TIMESTAMP;
SELECT status, count(*), min(created_at) AS oldest_created_at
  FROM judge_outbox WHERE status <> 'PUBLISHED' GROUP BY status;
SELECT count(*) AS expired_publisher_leases FROM judge_outbox
  WHERE status = 'PUBLISHING' AND lease_until < CURRENT_TIMESTAMP;
SQL
```

正常业务处理中允许短暂积压，空闲后应排空；DLQ、长时间不前进的 PENDING、过期 lease、持续重试需要调查。把年龄与已配置任务超时、业务负载结合判断，不能仅因状态 PENDING 就手工改终态。保存聚合数、UTC 时间、必要的受影响 ID；不 UPDATE、不 purge、不手工构造消息。

## 3. OPS 检查与权限

[ops-check.sh](../scripts/ops-check.sh) 会用 `OPS_ADMIN_USERNAME` / `OPS_ADMIN_PASSWORD` 调用 `/api/auth/login`，再读取 `/api/admin/version` 与 `/api/admin/system-status`。它需要 Worker UP、Runner/sandbox、Flyway V9、正常 HTTP 入口及备份根目录；检查备份新鲜度、磁盘、队列／DLQ、Outbox、重启计数。

**凭据就绪是维护前条件；完整 ops-check 是六服务正常运行／发布提交点后的检查，不是 Backend+Runner-only holdback 门禁。** 运行前通过受控秘密加载器将这两个 key 导入进程；仅导出 `FORMAL_ENV_FILE` 路径不会自动加载 OPS 凭据。不要把明文放命令参数、截图或工单里。

对于已经审查为 Bash 兼容、操作员自有的受限 env 文件，可在独立子 shell 加载；不要对来源不明或未经转义验证的文件使用 `source`：

```bash
(
  set +x
  set -a
  source "$FORMAL_ENV_FILE"
  set +a
  bash scripts/ops-check.sh --environment release \
    --expected-sha f273f3be519019841f050275d2b04bc2f4406797 \
    --backup-root /srv/practice-platform-backups \
    --base-url http://127.0.0.1:3000 --project oj
)
```

默认最大备份年龄 93600 秒（26 小时），最低可用空间 1 GiB；这是脚本告警阈值，不是建议只留这么少空间。可通过实际支持的 `--max-backup-age-seconds`、`--minimum-free-bytes` 明确设置学校政策，不能为了让检查变绿临时放宽。

失败先区分凭据缺失、权限、组件故障、积压、备份过期等，保存脱敏结果。没有已有测试数据时，不为了只读巡检创建比赛或提交。登录／Admin 请求均不得打印 token；非 Admin 应被 Admin-only 接口拒绝。

## 4. Office 引用与文件检查

V9 的真实引用来源是：

- `"OfficeExercise".teacher_doc_path`
- `"OfficeExercise".starter_doc_path`
- `"OfficeDocSubmission".student_doc_path`

SQL 标识符必须保留双引号和大小写。历史 V4 没有 `starter_doc_path`，V8 才添加它；不要把 V9 SQL直接用于 V4，也不要使用不存在的 `office_exercise`。

只读审计按 [OfficeFileReconciler](../backend-spring/src/main/java/com/oj/office/OfficeFileReconciler.java) 和 [OfficeStorageService](../backend-spring/src/main/java/com/oj/office/OfficeStorageService.java) 复现语义：

1. 解析实际 `DOC_STORAGE` 根目录（正式容器内 `/app/oj-docs`）和挂载卷，确认可读。
2. 引用可为 storage ID，也可为该文件在 Backend 内的规范绝对路径，二者都要匹配。不能只比较 basename 而忽略路径是否合法。
3. 候选只取根目录**直接子级**、普通文件、非 symlink；名字完全匹配 `[0-9a-f-]{36}\.docx`（UUID-like，不是严格 UUID 解析）。mtime 严格早于实际清理阈值，默认 `OFFICE_ORPHAN_MIN_AGE=1h`。Windows 还需注意 reparse point；不能跟随链接。
4. 逐一核对 DB 引用是否能解析为根目录内的可读普通文件。对无法安全解析的文件标记 UNKNOWN，不判“可删除”。候选集合减去上述全部引用，才是可能的 orphan。
5. 记录“引用数／可解析引用数、候选数、未引用候选数”，不公开文档内容或不必要的文件名。相同文件可能有多个引用，引用行数和独立文件数分开记。

应用自身有定时 reconciler，会删除满足条件的旧未引用托管文件；巡检**不要手动调用清理方法**。出现未知或异常 orphan，保留证据并调查，不执行删除。迁移／发布前要重新核对；备份恢复后同样要对配对数据做引用验证。13/13 等试点计数只是当时基线，正常新增数据后不能永远强制同一数字。

## 5. 配对备份：先确定一致性要求

真实脚本为 [backup.sh](../scripts/backup.sh)、[backup-verify.sh](../scripts/backup-verify.sh)、[backup-retention.sh](../scripts/backup-retention.sh)、[restore.sh](../scripts/restore.sh)。详细格式与威胁防护见 [备份设计](backup-restore.md)。

| mode | 行为 | 用途／限制 |
| --- | --- | --- |
| `daily` | 在线 pg_dump + Office 归档，不停业务 | 可能存在 DB／Office 时间差，不可假称发布前一致性 T1 |
| `consistent` | 脚本停止 Backend/Worker 后备份 | 受控升级 T1；还需操作员先关闭入口、排空并验证在途任务 |
| `weekly` / `monthly` | 与 consistent 一样会停 Backend/Worker | 需要计划停写窗口，不是无感任务 |

备份目录在 Git、数据库卷和 Office 卷之外，具备足够空间、受限 ACL、无异常链接；另存经验证的异机副本，访问备份等同访问敏感业务数据。默认 1 GiB 的脚本空间检查不能替代实际容量估算。

### 来源身份

新格式 `formatVersion=2` 明确区分 `backupToolGitSha`（执行工具的 checkout）与 `productionRuntimeGitSha`（正在备份的应用运行版本），Flyway 独立从源 DB 读取。正式 `--environment release` 必须显式提供完整、可解析的运行 commit，不能默认使用工具 HEAD。

例如当前 v0.9.1 日常备份两者可能相同；从旧版升级时两者通常不同。操作员必须先比对实际 Backend OCI、预期旧 SHA 与提供的 runtime SHA 相等。升级 T1 可运行 [release-t1-preflight.sh](../scripts/release-t1-preflight.sh)，其元数据必须描述**本次当前运行版本**；历史 V4 T1 不可冒充已恢复营业后的新 T1。

### 内部 Compose 必须显式收到 env 文件

`backup.sh` 接受 `--compose-file`，但**没有 `--env-file` 参数**，内部会再次运行 Compose。仅设置 `FORMAL_ENV_FILE` / `RELEASE_ENV_FILE` 并不会让 Docker 读取它们。

使用本校已隔离演练的外部 env-forwarding wrapper；下面给出可审查的 **Bash 转发形态**，不是仓库新增脚本。必须在同操作系统／shell／路径／操作员环境，用隔离 DB+Office 验证成功和故意失败的证据后才用于正式备份。Windows 使用 Git Bash 时为脚本提供其支持的路径格式；不要直接把 Bash 语法粘贴到 PowerShell，不能未经演练重写参数链。

```bash
# 放在外部操作员 wrapper 的子 shell 内；两个 env 文件路径已 export。
set -euo pipefail
set +x
: "${FORMAL_ENV_FILE:?set approved external formal env path}"
: "${RELEASE_ENV_FILE:?set approved external release env path}"
export REAL_DOCKER="$(type -P docker)"
test -n "$REAL_DOCKER"
docker() {
  if [[ "${1:-}" == compose ]]; then
    shift
    "$REAL_DOCKER" compose --env-file "$FORMAL_ENV_FILE" \
      --env-file "$RELEASE_ENV_FILE" "$@"
  else
    "$REAL_DOCKER" "$@"
  fi
}
export -f docker
```

该函数会被 Bash 子进程继承，转发每个 `docker compose` 调用，非 Compose 调用保持原始参数。内部的 `-p`、`-f` 仍由 backup.sh 设置。禁止启用 shell trace、打印 env 或直接存未经审核的 Docker stderr。

以下是 consistent 的命令形态，**只能在已批准停写、Frontend/Worker held、队列／在途／非终态 Outbox 为零之后执行**。变量通过已审查发布清单／安全加载器读取，下面会对缺值 fail closed；不要把示例视为自动维护脚本：

```bash
: "${BACKUP_ROOT:?set external backup destination}"
: "${FORMAL_POSTGRES_CONTAINER:?read actual container from release metadata}"
: "${POSTGRES_DB:?read actual database name}"
: "${POSTGRES_USER:?load approved database user without printing}"
: "${FORMAL_DOCS_VOLUME:?read actual Office volume}"
: "${PRODUCTION_RUNTIME_GIT_SHA:?supply observed full runtime SHA}"
bash scripts/backup.sh --environment release --mode consistent \
  --backup-root "$BACKUP_ROOT" --project oj \
  --db-container "$FORMAL_POSTGRES_CONTAINER" --db-name "$POSTGRES_DB" \
  --db-user "$POSTGRES_USER" --office-volume "$FORMAL_DOCS_VOLUME" \
  --production-runtime-git-sha "$PRODUCTION_RUNTIME_GIT_SHA" \
  --compose-file "$PWD/docker-compose.release.yml"
```

上面的非秘密资源变量不会因为只 export 两个文件路径而自动出现；可使用 [release-common.sh](../scripts/release-common.sh) 的 `release_env_value` 按学校字段提取，或经过验证的安全加载器。路径始终加引号。脚本归档 helper 使用 `postgres:16-alpine`，离线学校须提前准备并验收此工具镜像；它不替代正式 DB 的 digest 固定。

### 退出陷阱与持久化证据

**非 daily 备份的 EXIT trap 会尝试 `start backend worker`，即使操作员事先已经停过 Worker。** Frontend 不由此脚本管理。发布过程中，无论备份成功失败，必须检查真实容器状态并重新 hold Worker；验证消息、在途任务、非终态 Outbox 为零。不能把脚本退出 0 当作“仍处于静默边界”。

外部 wrapper 必须在 Git 外以受限权限记录：开始／结束 UTC、固定阶段名、最后完成阶段、进程退出码、最终 backup ID 或 NONE。可将已审核的固定进度行映射为阶段，不记录原始密码／路径报错内容；禁止把原始 stdout/stderr 未脱敏地 `tee` 到普通日志。

脚本阶段可映射为：参数／身份／源校验 → 临时目录 → 停 Backend/Worker → Flyway → dump → Office archive → manifest/checksum/verify → atomic publish → cleanup/restart。现有脚本并非每一步都有独立 marker，外部证据应如实写“最后已知阶段／范围”，不能编造精确错误。学校采用的 wrapper 应在隔离失败演练中验证诊断覆盖；若现有 marker 不够定位，停止并完善经审核的外部诊断，不盲试 Production。

失败时临时目录通常被自动移除，空 `consistent` 目录不等于任何成功备份。备份失败不自动重试或继续部署；保持用户入口受控，核验后恢复旧正常服务，记录失败证据。

## 6. 备份验证与保留

成功发布的目录包含 `database.dump`、`office.tar.gz`、`manifest.json`、`SHA256SUMS`、`.complete`。只见文件不算成功：脚本退出 0、最终唯一 ID、完整验证都需要。

```bash
: "${BACKUP_DIR:?set exact completed backup directory}"
: "${BACKUP_TOOL_GIT_SHA:?set actual tool checkout SHA}"
: "${PRODUCTION_RUNTIME_GIT_SHA:?set actual backed-up runtime SHA}"
bash scripts/backup-verify.sh "$BACKUP_DIR" \
  --expect-backup-tool-git-sha "$BACKUP_TOOL_GIT_SHA" \
  --expect-production-runtime-git-sha "$PRODUCTION_RUNTIME_GIT_SHA"
```

该脚本验证 manifest、普通文件／非链接、三个权威文件的 SHA-256、归档完整性与安全性、completion marker 及所声明来源。manifest 格式 1 保留读取／验证兼容性，但无法满足格式 2 的双 SHA 验证，不要给旧备份虚构来源。

**这不是完整恢复证明**：还需隔离恢复检查 pg_dump schema/data、实际 Flyway、关键业务计数和 DB→Office 引用。校验和证明字节一致，不能证明业务关系正确。备份数据库全库与 Office 原件都敏感，报告只保留聚合数与必要哈希。

```bash
: "${BACKUP_ROOT:?set existing external backup root}"
bash scripts/backup-retention.sh --backup-root "$BACKUP_ROOT" --dry-run
```

默认保留 7 daily / 4 weekly / 3 monthly；`consistent` 不在该清理列表。脚本不会自动调度备份，`backup.sh` 也不会自动执行 retention。`--apply` 是删除操作，必须另行审核计划、保护仍需保留的发布 T1 和历史证据、确认异机副本，再决定是否执行。损坏／不完整目录保留调查，不能为了整洁先删。运维排程可由学校批准的调度系统执行已验收 wrapper；周／月一致性任务会中断写入，必须安排窗口。

## 7. 隔离恢复演练与事故恢复

[restore.sh](../scripts/restore.sh) **只允许隔离、空目标**，不支持直接恢复 Production。它只接受项目名前缀 `practice-platform-stage9b-test-*` 或 `practice-platform-stage9d-drill-*`；这是脚本现有安全白名单，不是要求学校重做历史开发阶段。

先在隔离主机／项目创建全新数据库、全新 Office 卷与网络，核对 DB 容器的 Compose project 标签及目标 Office 卷确属该演练。不能只改 `--project` 名称而指向真实卷。目标 public 不得有表、Office 不得有文件。按 [详细恢复演练](backup-restore-runbook.md) 准备后执行：

```bash
: "${BACKUP_DIR:?set verified source backup}"
: "${DRILL_PROJECT:?set approved isolated project name}"
: "${DRILL_DB_CONTAINER:?set isolated database container}"
: "${DRILL_DB_NAME:?set isolated database name}"
: "${DRILL_DB_USER:?set isolated database user}"
: "${DRILL_OFFICE_VOLUME:?set new isolated Office volume}"
bash scripts/restore.sh --backup "$BACKUP_DIR" --target isolated --confirm-isolated \
  --project "$DRILL_PROJECT" --db-container "$DRILL_DB_CONTAINER" \
  --db-name "$DRILL_DB_NAME" --db-user "$DRILL_DB_USER" --office-volume "$DRILL_OFFICE_VOLUME"
```

核对恢复后的 Flyway 等于**备份时版本**（不是一律 V9）、业务计数／必要判定、Office 引用和文件哈希，记录耗时与数据时点。若启动应用验收，必须连隔离 RabbitMQ、隔离 Runner/Worker，绝不接入正式依赖。演练数据仍是敏感副本；结束后仅按批准的精确清单处理隔离资源，保留报告。

### 发布提交点前与之后

- **提交点前**：在已演练且获批的方案中，保持写入关闭、Worker 停止；记录并要求零消息／零非终态 Outbox；停变更版本应用服务。跨不兼容 schema 的恢复必须是匹配 fresh T1 的 PostgreSQL + Office + 旧应用。
- 已验证的 V9→V4 提交前恢复采用**清洁目标 public schema → 恢复完整已验证 V4 dump → 匹配 Office → 验证 V4 与引用 → 旧应用验收 → 再开放**。这是破坏性事故／发布操作，需要单独审批、停止 DB 使用者、精确目标核对和隔离演练；本手册不提供可误粘贴执行的删 schema 命令。不要用通用 `pg_restore --clean` 替代它，也不要声称 `restore.sh` 可指向 live。
- **提交点后／写入已开放**：新数据可能已出现，禁止盲恢复 T1 或自动 V4 回退。先按影响必要性关闭入口／停止消费，收集队列、Outbox、DB、Office 与运行证据，再制定 incident-specific 方案。即使消息为零，也不代表可以抹去提交后数据。

旧 T1、旧镜像、旧发布材料要保留，但不意味着它们是下一次维护的 fresh T1。完整策略见 [事故手册](incident-runbook.md) 和 [回退边界](rollback-runbook.md)。

## 8. 有界日志与单服务重启

```bash
formal_compose logs --since 30m --tail 200 --no-color backend worker runner frontend
formal_compose logs --since 30m --tail 100 --no-color db rabbitmq
```

仅在可信管理终端查看；落盘前审查脱敏，不把全量日志贴到公开 Issue。记录错误类别、次数、时间窗口和必要 ID，不输出源代码、文档内容或凭据。查 ERROR／5xx 突增、MQ 重连循环、过期 lease、Runner/sandbox、Office 文件错误；预期 401/403、单个无害 warning 不等于服务故障。内置 local 日志轮转为 10m × 3，长期审计需学校另有安全采集与保留策略，不能宣称被轮转的时间段“零错误”。

单服务重启是小范围运维写操作，**不是发布、不是自动恢复**。先保存健康／重启数／消息与日志证据，核对镜像和配置未变，评估在途任务影响。仅在已有批准且故障原因允许时对指定服务操作，例如：

```bash
# 示例仅用于获批的 Frontend 单服务故障；不重建、不启动依赖。
formal_compose restart --no-deps frontend
formal_compose ps
curl --fail --silent --show-error http://127.0.0.1:3000/api/health
```

Worker/Runner 重启会影响正在评测的任务，要结合 lease／重试机制观察，不循环重启来“等它变绿”。Backend 重启可能触发迁移校验及计划任务；未核实身份不得贸然重启。数据库／RabbitMQ 故障、数据完整性或安全事故升级处理，不套用应用重启示例。任何必要配置／镜像变更回到正式变更流程。

## 9. 操作记录与后续工作

Git 外保存：版本与四个镜像 ID、Flyway、当前卷／网络、备份 ID 与验证结果、恢复演练、维护／提交／开放时间、运行计数、故障证据与审批。密码只在密钥存储，不混入非秘密发布报告。

新问题按 Issue → 分支 → Docker 测试 → PR → Integration/Staging → 固定发布 → fresh 备份 → 受控部署 → 观察处理。大规模并发／容量仍需单独认证；不要自动开启新的编号开发阶段，也不要为清理验收而删除真实试点业务数据。
