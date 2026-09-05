# 发布操作手册

> 学校正式部署请从[学校部署指南](SCHOOL_DEPLOYMENT.md)开始，日常操作见[运维手册](OPERATIONS.md)。下文的预发布环境（Staging）／Stage 9D 命令用于隔离演练，不是生产环境的自动操作指令。正式升级应先仅启动 Backend 和 Runner，再依次单独启动 Worker、Frontend。OPS 凭据须在维护前就绪，完整 ops-check 在发布提交点后执行；不得把完整拓扑下的冒烟测试用于 Worker／Frontend 尚未启动的阶段。

本手册适用于受控发布演练，或另行获批的生产维护窗口；文档本身不构成生产变更授权。所有命令都必须绑定到 `DEPLOY_SHA` 指定的完整 40 位 Git 提交，不能用 `HEAD`、`main`、`latest` 或分支名代替。

## 必须停止的情况

工作树不干净、请求的 SHA 不是 CI 已测试版本、Flyway 版本不符、缺少全新且已验证的备份、磁盘空间低于配置阈值、队列／死信队列或 Outbox 异常、未明确已知可用的回退 SHA 时，均须在部署前停止。不得通过 prune、清空队列、删卷或回退数据库结构来掩盖这些问题。

## 预发布环境的发布前检查

```bash
DEPLOY_SHA=<40-char-sha>
ROLLBACK_SHA=<previous-known-good-40-char-sha>
git status --short
git rev-parse "$DEPLOY_SHA"
./scripts/ops-check.sh --environment staging --expected-sha "$ROLLBACK_SHA" --backup-root <external-backup-root>
```

上面的尖括号内容是占位符，执行前须替换为实际值。确认 `DEPLOY_SHA` 的 Docker CI 已通过，Flyway 为 V9，存在带有效清单、校验和、`.complete` 标记的最新一致性备份，并已完成隔离恢复演练。记录 OCI revision、备份 ID、操作员、开始时间（UTC）和回退 SHA。

## 预发布环境部署顺序

`staging-deploy-sha.sh` 创建临时、分离 HEAD 的工作树，并从该精确源码启动既有预发布拓扑。脚本只操作 `practice-platform-staging`，保留 PostgreSQL、RabbitMQ、Office 和外部备份数据。

```bash
./scripts/staging-deploy-sha.sh --sha "$DEPLOY_SHA"
./scripts/staging-smoke.sh
```

使用不会泄露令牌的一次性客户端，确认 `/api/admin/version` 返回相同的完整 SHA、有效且固定的 UTC 构建时间及 Flyway 9。核对全部健康／就绪状态、`sandboxAvailable`、Backend／Worker／Frontend 的 OCI revision、队列和 Outbox。隔离环境中执行一次算法 AC 测试，以及小范围比赛、分析和 Office 冒烟测试。

## 发布后观察与签收

针对已部署 SHA 运行 `ops-check.sh`。记录队列深度、死信数、非终态 Outbox 数、重启情况、备份年龄、剩余磁盘空间、评测结果和警告。只有精确部署 SHA、健康状态和冒烟测试证据全部一致时才能签收。生产环境还须遵循[不可变发布流程](immutable-release-workflow.md)中已获批准的维护窗口方案。
