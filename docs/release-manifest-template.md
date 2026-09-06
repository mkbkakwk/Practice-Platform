# Practice-Platform 发布记录模板

本模板只记录版本标识和验收结果。不得填写密码、JWT 密钥、令牌、用户数据或原始环境变量文件内容。尖括号内容均为占位说明，填写时应替换为本次发布的真实、非秘密信息。

括号中的英文字段名沿用已有发布约定，供工具校验和历史记录对照；按其前面的中文说明填写即可。

## 发布身份

| 字段 | 值 |
| --- | --- |
| 发布版本（Release Version） | `<版本号>` |
| 发布 Git SHA（Release Git SHA） | `<构建所用的完整提交 SHA>` |
| main 合并提交（Main Merge Commit） | `<完整 main 合并提交 SHA>` |
| 发布标签 | `<附注标签>` |
| 上一生产运行版本 Git SHA（Previous Production Git SHA） | `<上一运行版本的完整 SHA>` |
| 备份工具 Git SHA（Backup Tool Git SHA） | `<备份工具所用源码的完整 SHA>` |
| T1 生产运行版本 Git SHA（T1 Production Runtime Git SHA） | `<T1 备份记录的完整运行版本 SHA>` |
| CI 运行记录 | `<GitHub Actions 运行链接或 ID>` |
| 发布 PR（Release PR） | `<PR 链接或编号>` |

## 不可变本地镜像

| 组件 | 本地镜像引用 | 本地镜像 ID（Local Image ID） | OCI revision（源码版本） | OCI version（发布版本） |
| --- | --- | --- | --- | --- |
| Backend（后端） | `oj-backend:<release>` | `<sha256:...>` | `<完整 SHA>` | `<发布标签>` |
| Worker（评测任务处理器） | `oj-worker:<release>` | `<sha256:...>` | `<完整 SHA>` | `<发布标签>` |
| Runner（沙箱执行服务） | `oj-runner:<release>` | `<sha256:...>` | `<完整 SHA>` | `<发布标签>` |
| Frontend（前端） | `oj-frontend:<release>` | `<sha256:...>` | `<完整 SHA>` | `<发布标签>` |

## 运行环境与持久存储

| 字段 | 值 |
| --- | --- |
| Flyway 版本（Flyway Version） | `<版本号>` |
| 部署时间 | `<ISO-8601 时间戳>` |
| 数据库卷（Database Volume） | `<外部卷名称>` |
| RabbitMQ 卷（RabbitMQ Volume） | `<外部卷名称>` |
| DOCX 卷（DOCX Volume） | `<外部卷名称>` |
| 网络 | `<外部网络名称>` |
| Frontend 端口 | `<宿主机绑定地址及端口>` |
| 健康检查地址 | `<URL>` |

## 恢复材料

| 字段 | 值 |
| --- | --- |
| 备份 ID（Backup ID） | `<备份集标识>` |
| T1 备份工具 Git SHA | `<清单中的 backupToolGitSha>` |
| T1 生产运行版本 Git SHA | `<清单中的 productionRuntimeGitSha>` |
| PostgreSQL 备份 | `<路径或引用，以及 SHA-256>` |
| RabbitMQ definitions 配置备份 | `<路径或引用，以及 SHA-256>` |
| DOCX 备份 | `<路径或引用，以及 SHA-256>` |
| 回退后端镜像（Rollback Backend Image） | `<不可变镜像 ID 或 digest>` |
| 回退任务处理器镜像（Rollback Worker Image） | `<不可变镜像 ID 或 digest>` |
| 回退 Runner 镜像 | `<不可变镜像 ID 或 digest；旧拓扑没有此服务时注明不存在>` |
| 回退前端镜像（Rollback Frontend Image） | `<不可变镜像 ID 或 digest>` |

## 验证清单

- [ ] 标签指向发布 Git SHA。
- [ ] OCI revision 和 version 标签与本记录一致。
- [ ] 本地镜像标签解析出的镜像 ID 与记录一致。
- [ ] Flyway 为预期版本，没有意外迁移。
- [ ] 所有外部持久卷及生产网络均存在。
- [ ] 在完整拓扑验收阶段，PostgreSQL、RabbitMQ、Backend、Worker、Runner、Frontend 健康检查均通过。
- [ ] Worker 使用远程 Runner；只有 Runner 挂载 Docker socket。
- [ ] 维护前已验证 OPS 凭据就绪；完整 ops-check 在发布提交点后执行。
- [ ] 回退镜像及备份可访问。
- [ ] 已记录部署前后的生产数据计数及健康状态。
