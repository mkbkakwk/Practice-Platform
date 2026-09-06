# 备份与恢复操作手册

> 学校操作员请先读[运维手册](OPERATIONS.md)。下文预发布示例用于技术演练。正式备份须使用已验证的外部封装程序，将正式配置和发布元数据两个环境变量文件显式转发给每次内部 Compose 调用。非 daily 备份退出时会尝试启动 Backend／Worker，发布窗口内须随后重新确认 Worker 停止。不得将隔离恢复命令指向生产环境。

本手册补充[备份与恢复设计](backup-restore.md)。权威恢复数据是 PostgreSQL 与 Office 持久文件，RabbitMQ 消息存储不是权威备份。

## 备份

使用 Git、PostgreSQL 卷和 Office 卷之外的宿主机目录。每日备份在线执行，数据库与 Office 可能存在轻微时间差；发布和恢复演练要求先安排 Backend／Worker 停写，再生成逻辑一致的配对备份。

```bash
./scripts/backup.sh --environment staging --mode consistent \
  --backup-root <external-backup-root> \
  --project practice-platform-staging \
  --db-container practice-platform-staging-postgres \
  --db-name "$STAGING_POSTGRES_DB" --db-user "$STAGING_POSTGRES_USER" \
  --office-volume practice-platform-staging-docs \
  --compose-file docker-compose.yml --compose-file docker-compose.staging.yml
```

尖括号内容均须先替换为实际值。脚本在发布备份前检查剩余空间，生成 PostgreSQL 自定义格式导出与 Office 归档，分别记录备份工具 Git SHA、被备份运行版本 Git SHA、Flyway 和 UTC 元数据；验证 SHA-256 与归档安全性后，以原子方式发布带 `.complete` 的备份，并通过 EXIT 处理逻辑重新启动 Backend／Worker。

新文件采用 `umask 077`：Linux 目录权限为 `700`、文件为 `600`；Windows 应使用兼容的受限 ACL，绝不能允许所有用户写入。

正式生产备份还必须提供 `--production-runtime-git-sha <full-sha>`。发布 T1 预检会验证该值等于实际观察到的上一生产运行版本，不会用备份工具的 checkout SHA 代替。

认定备份可用前，先运行 `./scripts/backup-verify.sh <backup-dir>`。默认保留 7 份 daily、4 份 weekly、3 份 monthly；首先只运行 `backup-retention.sh --dry-run` 查看计划。损坏或不完整的目录保留调查，不自动删除。

## 隔离恢复

不得向在线预发布或生产环境恢复。先准备全新的 PostgreSQL、Office 卷和隔离网络，项目名使用 `practice-platform-stage9d-drill-*`，再执行：

```bash
./scripts/restore.sh --backup <verified-backup-dir> --target isolated --confirm-isolated \
  --project practice-platform-stage9d-drill-example --db-container <fresh-db-container> \
  --db-name <fresh-db-name> --db-user <fresh-db-user> --office-volume <fresh-office-volume>
```

保护逻辑拒绝在线／非隔离目标名、非空目标、不完整或校验失败的备份，以及含路径穿越或链接的归档。对本手册的 V9 演练，验证 Flyway V9、失败迁移数为 0、备份时的数据库计数、Office 文件数及代表性 SHA-256，并检查数据库到 Office 的引用。

只有包括 RabbitMQ 在内的全部依赖都已隔离，才能启动演练应用。记录备份年龄和恢复耗时，作为恢复点目标（RPO）与恢复时间目标（RTO）的证据；结束后仅按批准范围移除本次演练资源。
