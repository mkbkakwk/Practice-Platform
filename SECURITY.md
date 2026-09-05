# 安全政策

## 如何报告安全漏洞

如果漏洞可能泄露凭据、学生数据、宿主机访问权限或沙箱逃逸细节，请勿提交公开 Issue。请通过私密渠道联系仓库所有者，提供受影响的提交版本、复现步骤、影响范围及建议的缓解措施。未经明确授权，不得在生产环境（Production）或预发布环境（Staging）进行安全测试。

## 沙箱威胁模型

学生源代码和测试用例输入均属于不可信数据。正式部署中，这些数据由 Worker 转交给经过身份验证的 Runner。Worker 承担可信的评测业务逻辑，不挂载 Docker socket，也不安装学生程序所需的语言工具链。Runner 是可信的 Docker 控制服务，也是唯一能够访问 Docker socket 的服务。

Docker socket 具有很高的权限：Runner 一旦被攻破，攻击者可能进一步控制 Docker 守护进程及宿主机。因此，Runner 必须部署在内部网络，使用随机生成的 Bearer 令牌进行认证，不对外发布端口，也不得把 socket 挂载到 Worker 或学生程序容器。

学生程序运行在一次性容器内。容器使用非 root 用户、只读根文件系统、无网络模式，移除全部 Linux capabilities，启用 `no-new-privileges`，并保留 Docker 默认的 seccomp 策略。CPU、内存与交换空间、进程数、输出量、实际运行时间、工作目录及 `/tmp` 均有上限。语言镜像和命令参数由封闭的枚举选项决定；提交内容不能指定 shell 命令或 Docker 参数。

Runner 必须遵循“无法确认安全就拒绝执行”的原则（fail closed）。Runner 不可用、协议响应无效、资源清理失败或并发已满时，都不能回退为在 Worker 本地执行学生代码。

## 运维要求

- `RUNNER_TOKEN`、数据库密码和 JWT 密钥不得进入 Git 或日志。
- 不得使用 `privileged`、宿主机 PID／网络命名空间或 `seccomp=unconfined`；不得向 Worker 或学生程序容器挂载 Docker socket。
- 沙箱镜像须经过审查并固定版本；正式发布不得使用 `latest`。
- 部署前运行 `./scripts/test-docker.sh`。安全验收要求失败数、错误数和跳过数均为零。
- 清理操作只能针对具有确切 Runner／测试标签的资源。应用清理不得执行全局 prune。
- 生产环境项目名为 `oj`，预发布环境项目名为 `practice-platform-staging`。两者相互独立；获得一个环境的测试授权，不代表可以修改另一个环境。

历史 nsjail Stage 3B-2 验收材料保留在 Git 引用 `archive/nsjail-stage3b2-15of15` 中，仅用于追溯，不是当前正式部署方案。
