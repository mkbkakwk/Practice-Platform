# 比赛核心设计

本文记录 Stage 6／6.6 建立的比赛业务边界。该阶段没有引入排行榜或第二套评测流程；后续计分扩展见文末链接。PostgreSQL 是业务状态的权威来源，注入的 UTC `Clock` 是时间判断的权威来源。

## 数据模型

- `Contest` 保存标题、描述、创建者、状态 `DRAFT|PUBLISHED|CANCELLED`、参与方式 `OPEN|INVITE_ONLY`，以及 `TIMESTAMPTZ` 类型的开始／结束时间。
- `ContestParticipant` 关联一名学生和一场比赛，记录添加人及添加时间。`(contest_id, user_id)` 唯一。
- `ContestProblem` 必须且只能关联一种题型和一个目标：`ALGORITHM` 对应 `Problem`，`OFFICE_CHOICE` 对应 `OfficeQuestion`，`OFFICE_DOCX` 对应 `OfficeExercise`。数据库 `CHECK`、外键、部分唯一索引及确定性的 `display_order` 防止关联含糊或重复。
- 算法 `Submission`、DOCX `OfficeDocSubmission`、选择题 `OfficeRecord` 均可携带不可变的 `contest_problem_id`；普通练习提交的该字段保持 `NULL`。

比赛核心始于 Flyway V7。V8 增加两种 Office 比赛题型，将历史 `OFFICE` 比赛题映射为 `OFFICE_DOCX`，并保留既有 DOCX 参考文档路径。历史题目继续为 `PUBLIC`；已有 DOCX 练习不会自动把参考答案文档当作学生操作素材。

## 生命周期与时间

数据库只保存草稿 `DRAFT`、已发布 `PUBLISHED`、已取消 `CANCELLED`。API 按以下规则计算展示阶段：

| 保存的状态与时间条件 | API 阶段 |
| --- | --- |
| `DRAFT` | `DRAFT`（草稿） |
| `PUBLISHED` 且 `now < startAt` | `UPCOMING`（未开始） |
| `PUBLISHED` 且 `startAt <= now < endAt` | `RUNNING`（进行中） |
| `PUBLISHED` 且 `now >= endAt` | `ENDED`（已结束） |
| `CANCELLED` | `CANCELLED`（已取消） |

允许提交的区间是 `[startAt, endAt)`：恰好在开始时刻提交会被接受，恰好在结束时刻提交会被拒绝。该检查与提交创建紧邻执行，并锁定对应 Contest 行。提交及其比赛上下文一旦提交事务，Worker 就不再检查比赛时间，因此评测可以在比赛结束后完成。

草稿和未开始比赛的核心配置可编辑；进行中、已结束、已取消的比赛配置冻结。只有无提交的草稿比赛可以物理删除。取消比赛仍保留题目、参赛人和历史记录。

## 访问权限与参赛人

公开注册产生现有 `USER` 角色，即学生角色。只有学生可以成为参赛人。

- `OPEN`：已登录学生只能在比赛已发布且尚未开始时自行加入。数据库唯一约束保证并发加入操作具有幂等性。
- `INVITE_ONLY`：拒绝自行加入。创建者或管理员只能在草稿或未开始阶段维护名单。

教师只能管理自己创建的比赛，以及自己原本就有管理权限的内容。管理员可以管理全部比赛和内容；学生不能调用管理操作。

学生比赛列表包含已发布的公开比赛，以及本人已加入的已发布邀请赛；不相关的邀请赛和草稿对其隐藏。

## 题目可见性

算法题、Office 选择题和 DOCX 练习使用以下可见性：

- `PUBLIC`：仍可在普通练习区访问。加入比赛不会自动隐藏题目，教师不能据此认为题目已保密。
- `CONTEST_ONLY`：不出现在普通题目列表，直接详情接口也受保护。学生只有参加包含该题的已发布比赛，且比赛已经开始后才能阅读。参赛人在结束后仍保留访问权，不相关学生始终无权访问。

比赛 DTO 不暴露算法测试用例、DOCX 参考答案路径／文件、选择题答案／解析或用户认证字段。比赛未开始时，不返回隐藏的比赛专用题目正文。进行中的选择题提交只返回正确／错误，不返回正确答案或解析。

## 提交流程集成

比赛接口从 URL 和数据库关联中确定上下文：

```text
POST /api/contests/{contestId}/problems/{contestProblemId}/submissions
POST /api/contests/{contestId}/problems/{contestProblemId}/choice-submissions
POST /api/contests/{contestId}/problems/{contestProblemId}/office-submissions
GET  /api/contests/{contestId}/problems/{contestProblemId}/starter
```

服务端检查比赛、阶段、参赛人、题目关联、底层题型及内容启用状态。客户端不能通过普通练习提交接口注入比赛上下文。

算法比赛复用原有 `Submission + judge_outbox` 原子事务，后续仍经过 RabbitMQ、幂等 Worker、Runner 及每次提交独立的 Docker 沙箱。选择题比赛复用单选／多选／判断题评测器，同时保存比赛上下文。DOCX 比赛复用 Stage 5 的文件验证、存储生命周期、标准化比较、结构化评分和脱敏错误处理。

每个可用的 DOCX 练习需要两个独立且已验证的文件：操作素材（starter）供学生下载编辑，参考答案（reference）仅供服务端评测及获授权的复核使用。`PUBLIC` 素材通过普通练习接口提供；`CONTEST_ONLY` 素材只在比赛开始后通过比赛接口向参赛人提供，结束后仍可读取。参考答案绝不向学生开放。

## Stage 7 扩展

Stage 7 增加 SCORE／ICPC 计分、由提交数据计算的排行榜、服务端授权的封榜视图，以及具有评测代次保护的算法重判。完整语义及暂不支持 Office 历史重判的原因见[比赛计分](contest-scoring.md)。虚拟参赛、团队、公告和赛后公开题目仍不在该设计范围内。
