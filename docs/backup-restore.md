# 备份与恢复设计（Stage 9B）

## 权威数据与恢复目标

权威恢复数据集由 PostgreSQL 和 Office 持久存储卷组成。PostgreSQL 保存用户、题目、比赛、提交、Outbox 状态、重判状态、Office 元数据，以及计算派生数据所需的全部输入。Office 卷保存学生操作素材、教师参考答案和学生 Office 提交文件。Backend 将该卷挂载到 `/app/oj-docs`，Worker 和 Runner 不挂载或管理 Office 存储。

RabbitMQ 消息不是权威备份输入。恢复后，应用重新声明消息拓扑，事务型 Outbox 重新发布持久化的待处理任务。恢复出的 Outbox 可能重复发布待处理事件，因此原有幂等性和评测代次保护仍然必要。

## 备份模式

在仓库外准备仅操作员可访问的宿主机目录，例如 `/var/backups/practice-platform`。运行时必须明确指定环境、Compose 项目、数据库容器／库名／用户和 Office 卷。

```bash
BACKUP_MIN_FREE_BYTES=1073741824 \
  ./scripts/backup.sh --environment staging --mode daily \
  --backup-root /var/backups/practice-platform \
  --project practice-platform-staging \
  --db-container practice-platform-staging-postgres \
  --db-name "$STAGING_POSTGRES_DB" --db-user "$STAGING_POSTGRES_USER" \
  --office-volume practice-platform-staging-docs
```

`daily` 模式在线生成 PostgreSQL 16 兼容的自定义格式 `pg_dump`，以及 Office 的 `tar.gz` 归档。正常恢复点目标为 24 小时。它不是跨资源原子快照，数据库元数据与 Office 归档之间可能存在少量时间差。

每周、每月、发布前、高风险维护前或重要比赛检查点，使用 `--mode weekly`、`--mode monthly` 或 `--mode consistent`，并指定准确的 Compose 文件。这些非 daily 模式会临时停止 Backend 和 Worker 的写入路径，完成数据库导出和归档；即使某一步失败，EXIT 处理逻辑仍会尝试重新启动它们。它提供的是计划内停写边界，不是文件系统事务快照。

完成后的备份以原子方式发布到对应分类目录。以下示意 daily／consistent 目录；尖括号表示应替换的内容，运行版本 SHA 为完整 40 位值：

```text
<备份根目录>/<daily|consistent>/<UTC时间>_<生产运行版本完整SHA>/
  database.dump
  office.tar.gz
  manifest.json
  SHA256SUMS
  .complete
```

版本 2 清单包含 UTC 时间、模式、`backupToolGitSha`、`productionRuntimeGitSha`、Flyway 版本和制品名称。发布过程中这两个 SHA 有意分开：备份工具可以比正在备份的生产应用更新。带 `gitSha` 的历史版本 1 清单仍可作为历史备份验证。清单不包含运行凭据。

`SHA256SUMS` 覆盖数据库导出、Office 归档和清单。未完成的临时内容不会发布；只有校验和有效且带 `.complete` 的目录才算完整备份。备份使用 `umask 077`，根目录不得放在 Git、PostgreSQL 卷或 Office 卷中。

Windows Git Bash 应使用仓库之外、Docker Desktop 可共享的本地目录。脚本为宿主机工具保留 `/c/...` 路径，仅将 Docker 绑定挂载来源转换为 `C:/...`；支持空格和反斜杠输入。驱动器根目录及 UNC／网络共享路径会被拒绝，避免 Docker 挂载语义含糊。

可用空间不足 `BACKUP_MIN_FREE_BYTES` 时，脚本会在开始备份工作前失败。它不会删除既有备份或权威数据来腾出空间。

## 验证、保留与恢复

```bash
./scripts/backup-verify.sh /var/backups/practice-platform/daily/<backup-id>
./scripts/backup-retention.sh --backup-root /var/backups/practice-platform --dry-run
./scripts/backup-retention.sh --backup-root /var/backups/practice-platform --apply
```

执行前替换路径占位符。`--apply` 会删除文件，须先审查 `--dry-run` 结果并另行获得批准，不能把这三行无条件连续执行。

保留策略按清单 `createdAt` 排序，保存最新 7 份 daily、4 份 weekly、3 份 monthly 已验证备份。预演不修改文件。损坏或不完整目录会被报告，不会静默删除；删除范围仅限配置根目录下已验证的直接子目录。

恢复默认拒绝执行。脚本只接受明确确认的隔离测试目标；Stage 9B 使用 `practice-platform-stage9b-test-*`，目标 PostgreSQL 和 Office 卷都必须为空：

```bash
./scripts/restore.sh --backup /safe/path/<backup-id> \
  --target isolated --confirm-isolated \
  --project practice-platform-stage9b-test-example \
  --db-container practice-platform-stage9b-test-example-db \
  --db-name restore_db --db-user restore_user \
  --office-volume practice-platform-stage9b-test-example-office
```

恢复前要求清单版本受支持、存在 `.complete` 和全部制品、校验和及来源 SHA 有效，并通过归档路径安全检查。包含绝对路径、`..` 或链接的归档会被拒绝。脚本不会自动执行新的 Flyway 迁移。

启动恢复后的应用之前，必须检查恢复出的 Flyway 历史与备份时版本一致（本阶段演练为 V9）、数据库内容、Office 文件校验和及数据库到文件的引用。不得把“当前发布为 V9”理解为所有历史备份恢复后都必须是 V9。

## 恢复目标

- 正常每日备份的恢复点目标（RPO）为 24 小时，即期望最多损失该时间范围内的数据。
- 发布和重要比赛前额外创建一致性备份。
- 恢复时间目标（RTO）为 2 小时。这是运维目标，不是代码提供的服务等级承诺。
- 灾难恢复流程应先准备干净的 PostgreSQL 和 Office 存储，恢复已验证备份，检查 Flyway 与文件引用，再启动服务，检查就绪、Outbox／队列及应用冒烟测试。

这些默认脚本不能直接恢复在线预发布或生产环境。当前正式操作边界见[运维手册](OPERATIONS.md)。
