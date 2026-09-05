# 评测消息可靠性

## 交付保证与权威状态

评测链路采用的是**至少一次投递（at-least-once）**，不是严格一次投递。PostgreSQL 是提交状态的权威来源；RabbitMQ 消息是持久化的任务触发信号，重复投递属于正常且受支持的情况。

可靠性流程如下：

```text
提交记录 + 事务型 Outbox
        → 发布确认（publisher confirm）
        → RabbitMQ 持久消息触发（至少一次）
        → 数据库条件更新／租约认领（幂等）
        → Runner 执行
        → 结果事务提交
        → 手动 ACK 确认
```

不使用 PostgreSQL／RabbitMQ 的 XA 分布式事务。Outbox 防止消息丢失，数据库认领和评测令牌防止重复消息产生第二份有效业务结果。

## 提交与 Outbox 事务

提交接口在同一个 PostgreSQL 事务内写入新的 `Submission` 和一条类型为 `JUDGE_REQUESTED` 的 `judge_outbox` 事件。消息负载只携带稳定的 `eventId`、`submissionId`、协议结构版本和投递尝试次数等调度信息。Worker 从 PostgreSQL 重新读取源代码、题目限制及测试用例，不信任消息中可能过期的业务数据。后续加入的重判代次约定见[比赛计分](contest-scoring.md)。

HTTP 成功表示平台已持久化接收提交，不表示 RabbitMQ 在该瞬间一定可用。RabbitMQ 中断时，提交保持 `PENDING`，待服务恢复后由 Outbox 转发器发布。

转发器用 `FOR UPDATE SKIP LOCKED` 批量认领事件，在等待 RabbitMQ 之前释放数据库事务。认领行具有有期限的 `PUBLISHING` 租约和随机发布者令牌。只有当前令牌能将其标记为 `PUBLISHED` 或安排重试；转发器崩溃后，过期认领可被恢复。

只有与本次发布关联的肯定确认，才能把事件标记为 `PUBLISHED`。NACK、退回消息、超时或连接失败会让事件回到 `PENDING`，并记录指数退避时间和脱敏失败类别。消息服务已接收、数据库尚未更新时若进程崩溃，同一个稳定 `eventId` 可能再次发布；Worker 已按此情况设计。

已发布记录默认保留七天，之后分批限量清理。待处理、发布中和仍可重试的记录不因保留策略而删除。

## Worker 任务归属与崩溃恢复

持久评测队列采用手动确认，预取数为 `1`。Worker 首先通过数据库条件更新认领状态：

```text
PENDING -> JUDGING + judge_token + judge_lease_until
```

过期的 `JUDGING` 租约可用新令牌重新认领；尚未到期的租约不能被另一个 Worker 执行，对应触发消息经重试队列延迟处理。完成更新必须同时满足 `JUDGING` 状态和当前令牌，迟到或恢复运行的旧 Worker 不能覆盖较新的结果。

结果事务提交后才确认 RabbitMQ 消息。如果 ACK 丢失，重新投递时会读到数据库终态，按幂等成功处理并确认，不再次调用 Runner。Worker 在认领后退出时，消息会重新投递，其他 Worker 可在租约过期后重新认领。配置的租约必须长于平台编译、执行、网络和清理耗时的硬上限之和。

`AC`、`WA`、`CE`、`RE`、`TLE`、`MLE`、`OLE` 等由学生代码产生的终态是正常评测结果，应提交并确认，不作为基础设施重试处理。

## 重试与死信拓扑

交换机、队列均持久化，消息也持久化；评测队列不使用独占或自动删除模式：

```text
oj.judge / oj.judge.queue
        -> oj.judge.retry / oj.judge.retry.queue (TTL)
        -> oj.judge / oj.judge.queue

基础设施最终失败
        -> oj.judge.dlx / oj.judge.dlq
```

延迟由消息服务实现，Worker 不会持有已投递消息并通过睡眠等待。默认最多执行三次尝试。临时 Runner／Docker／HTTP 故障会释放数据库认领，再发布投递次数递增的触发消息。最后一次失败会发布脱敏死信记录，将提交设为 `JUDGE_FAILED`，并确认原消息。

死信记录保留 `eventId`、`submissionId`、原始路由键、尝试次数、失败类别和时间戳，不包含学生源代码、Runner 令牌、Docker socket 细节或服务凭据。

## 运维与可观测性

可使用以下安全日志字段关联事件：`eventId`、`submissionId`、`judgeToken`、投递次数、Worker 实例和 Runner 请求 ID。日志覆盖 Outbox 认领／确认／重试、接收消息、任务认领、忽略重复、Runner 完成、重试、死信、数据库提交及 ACK 等边界，不输出源代码或秘密。

后端运维状态提供未发布 Outbox 数量、转发器状态和最近一次脱敏失败；公开健康接口的最小响应约定见[健康检查语义](ops-readiness.md)。操作员还应观察 RabbitMQ 连接，以及主队列、重试队列、死信队列的待投递和未确认数量。Outbox 待处理数持续上升可能意味着发布者或 RabbitMQ 异常；死信数增长需要调查并作出明确操作决定。

主要配置如下，变量名和默认值保持与工具约定一致：

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `JUDGE_MAX_RETRIES` | `3` | 基础设施执行尝试总次数 |
| `JUDGE_RETRY_DELAY_MS` | `5000` | 重试队列 TTL（毫秒） |
| `JUDGE_LEASE` | `30m` | Worker 任务归属租约 |
| `JUDGE_PUBLISH_CONFIRM_TIMEOUT` | `5s` | Worker 重试／死信发布确认超时 |
| `OUTBOX_BATCH_SIZE` | `20` | 转发器每轮认领事件数 |
| `OUTBOX_LEASE` | `30s` | 可恢复的发布认领租约 |
| `OUTBOX_CONFIRM_TIMEOUT` | `5s` | Outbox 的 RabbitMQ 发布确认超时 |
| `OUTBOX_INITIAL_RETRY_DELAY` | `1s` | 初始发布退避时间 |
| `OUTBOX_MAX_RETRY_DELAY` | `1m` | 最大发布退避时间 |
| `OUTBOX_RETENTION` | `7d` | 已发布事件保留时间 |

`scripts/test-judge-reliability.sh` 是使用临时资源的故障注入测试。它验证 RabbitMQ 中断／恢复、三个 Worker 下的稳定重复事件、终止认领 Worker 后的租约恢复、结果幂等性、临时 Runner 故障恢复、有界重试、死信及 `JUDGE_FAILED`。脚本只移除具名测试资源，不执行 Docker 全局 prune。
