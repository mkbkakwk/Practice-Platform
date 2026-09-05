# 回退操作手册

> 当前校园运行与恢复边界见[运维手册](OPERATIONS.md)。本文在相同数据库结构下进行的预发布回退演练，不授权生产环境 V9→V4 恢复。生产发布提交点前跨结构回退，必须使用本次全新 T1 的配对数据库和 Office 备份，满足零消息条件，并采用已批准的清洁目标恢复流程；不能用通用 `pg_restore --clean` 替代。`restore.sh` 仅允许隔离空目标。越过发布提交点后不得自动回退 V4，必须按具体事故评估。

应用回退（application rollback）是将应用容器切换到此前已验证的源码版本，**不等于数据库回退**。不能假设旧应用兼容新数据库结构。每次涉及迁移的发布前，都须判断迁移是否向后兼容，还是会阻止直接应用回退。Stage 9D 演练的前后版本均为 Flyway V9，因此不包含数据库结构回退。

## 允许回退的条件

在已获批的适用流程中，新应用的健康／就绪、发布元数据、关键冒烟测试、安全／日志，或持续队列／Outbox 检查失败，且已确认上一可用 SHA 与当前数据库兼容时，才可考虑应用回退。

数据兼容性不明、可能需要破坏性数据操作，或无法独立核验回退 SHA 时，必须停止并升级处理。

## 预发布环境回退顺序

```bash
ROLLBACK_SHA=<previous-known-good-40-char-sha>
./scripts/staging-deploy-sha.sh --sha "$ROLLBACK_SHA"
./scripts/staging-smoke.sh
```

执行前把占位符替换为已验证的完整 SHA。该过程保留预发布 PostgreSQL、RabbitMQ、Office 卷和外部备份，不使用 `down -v`、删卷、清空队列或数据库结构回退。

验证管理员版本接口、OCI revision、健康／就绪、`sandboxAvailable`、队列／Outbox，以及一次算法 AC。对照回退前快照，比较用户、题目、比赛、提交、Outbox、Office 记录和文件的脱敏计数。

RabbitMQ 消息不是权威备份状态；PostgreSQL 与 Office 文件才是权威恢复数据集。持久异步任务的恢复依赖事务型 Outbox，而不是恢复 RabbitMQ 消息卷。

## 再次部署目标版本

回退证据通过验收后，用 `staging-deploy-sha.sh --sha "$DEPLOY_SHA"` 重新部署原定的精确目标版本。重新验证版本、健康、数据保留、评测、队列和 Outbox。目标 SHA 尚未重新部署并验收前，不得宣称正向恢复完成。
