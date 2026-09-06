# 沙箱 Runner 架构

## 当前架构

学生提交只经过一条“无法确认安全就停止”的执行路径：

```text
RabbitMQ
  → Worker 处理池（评测业务规则；无 Docker socket 和语言工具链）
  → RemoteSandboxClient
  → POST /api/v1/jobs
  → Runner（可信 Docker 控制服务）
  → 一次性编译／运行容器
```

Worker 比较实际输出和预期输出，负责判定 AC／WA；Runner 只返回执行状态和限量输出。远程执行失败不会触发 Worker 本地执行兜底。基础执行层的系统错误与上层重试、最终 `JUDGE_FAILED` 的关系，见[评测消息可靠性](judge-message-reliability.md)。

此前 nsjail Stage 3B-2 实现及 Ubuntu 15/15 验收证据保留在 Git 引用 `archive/nsjail-stage3b2-15of15`。Docker 化 nsjail 的实验代码仍保留在相应功能分支中，供追溯参考。正式方案使用每次提交独立的 Docker 容器。

## 信任边界

Runner 是可信控制服务。它能访问 Docker socket，意味着能够控制 Docker 守护进程，因此必须与不可信客户端隔离，并使用随机 `RUNNER_TOKEN` 保护。Worker 和学生程序容器都不能获得 socket。

每个学生程序容器使用固定、经过审查的 `HostConfig`：

- `Privileged=false`，不使用宿主机 PID／IPC／网络命名空间，网络模式为 `none`。
- 移除全部 Linux capabilities，启用 `no-new-privileges`。
- 保留 Docker 默认 seccomp 策略。
- 使用非 root UID／GID `10001:10001`，根文件系统只读。
- 限制内存与交换空间、CPU、进程数、工作目录及 `/tmp`。
- 只挂载本次提交的制品卷和当前测试用例输入。
- 语言镜像及命令参数由封闭的语言枚举选择，不能由提交任意指定。

学生源代码、标准输入、预期输出、编译器路径、shell 命令和 Docker 参数都不能改变控制服务的执行命令。辅助程序从使用 `O_NOFOLLOW` 打开的固定路径读取标准输入，再调用 `execv`，不经过 shell 插值。

## 执行生命周期

每次提交分配唯一的 Runner 所属标签和制品卷。C、C++、Java 在一次性编译容器中只编译一次；Python 和 JavaScript 经过相同验证边界，但不产生本地编译制品。每个测试用例随后在全新容器中运行，因此不同用例的 `/workspace`、`/tmp`、进程和输出相互隔离。Runner 流式读取限量的标准输出／错误输出，并通过终止一次性容器执行实际运行时间上限。

无论成功、CE、RE、TLE、MLE、OLE 还是内部故障，都必须清理。Runner 删除测试／编译容器和提交卷，对清理操作进行有限次数重试，并验证没有残留匹配资源。无法证明清理完成时返回 `SYSTEM_ERROR`。启动时的遗留资源清理仅限携带当前配置中精确 Runner 实例标签的资源。

## 资源与状态处理

Runner 执行状态与平台业务判定相互独立：

| 状态值 | 含义 |
| --- | --- |
| `OK` | 执行成功 |
| `COMPILE_ERROR` | 编译失败 |
| `RUNTIME_ERROR` | 运行错误 |
| `TIME_LIMIT_EXCEEDED` | 超过运行时间上限 |
| `MEMORY_LIMIT_EXCEEDED` | 超过内存上限 |
| `OUTPUT_LIMIT_EXCEEDED` | 超过输出上限 |
| `SYSTEM_ERROR` | 系统错误 |

TLE 和 OLE 由强制终止机制确定。MLE 根据 Docker OOM 状态及退出后的内存统计判断，不能仅凭退出码 137 认定内存超限。普通非零退出仍归为 RE。结果说明属于限量元数据，不占用学生标准输出／错误输出的额度。

公平信号量限制 Runner 同时接收的任务数，正式 Compose 默认为四个。并发满时返回 HTTP 429，由调用方等待／重试，不创建无限线程，也不绕过隔离。多个 Worker 是同一 RabbitMQ 队列的竞争消费者，预取数为 1，使用手动确认。

## 协议与认证

版本化接口为 `POST /api/v1/jobs`，认证头为 `Authorization: Bearer <RUNNER_TOKEN>`。请求包含 UUID 类型的 `requestId`、封闭的语言枚举、源代码、资源限制和有序测试用例输入，不包含自定义命令、shell、argv、可执行文件路径或预期答案。Runner 与 Worker 共用 `test/fixtures/runner/` 下的 JSON 协议测试样本。

两端均验证请求 ID、字段完整性、顺序、大小限制、超时、已知状态和响应上限。令牌、源代码、标准输入、标准输出和错误输出不会写入普通日志。

## 验证

完整 Docker 测试入口为：

```bash
./scripts/test-docker.sh
```

测试涵盖常规 Backend／Worker／Runner／Frontend 测试、发布／配置检查、真实 Docker 安全测试和三个 Worker 的竞争消费测试。安全测试覆盖五种语言、CE／RE／TLE／MLE／OLE、fork／PID 限制、网络和原始套接字隔离、capabilities、命名空间及 `/proc` 隔离、只读文件系统、受限 `/tmp`、秘密隔离、并发和清理。测试 Compose 项目使用一次性资源及精确标签，不执行全局 prune。

## 开发环境启动示例

下列命令使用开发 Compose，**只用于隔离开发机，不是学校生产部署命令**。启动应用前先构建五种固定沙箱镜像：

```bash
docker compose --profile sandbox-images build \
  sandbox-python-image sandbox-javascript-image sandbox-c-image \
  sandbox-cpp-image sandbox-java-image
docker compose up -d --build --scale worker=3
```

在不纳入 Git 的本地环境变量文件中设置 `RUNNER_TOKEN` 和 `DOCKER_SOCKET_GID`。只有 Runner 接入 Docker 控制服务；学生容器使用 `none` 网络模式，因此允许 Worker→Runner HTTP 请求，但不允许学生容器访问 Runner、Backend、数据库、RabbitMQ 或互联网。

学校正式部署使用固定镜像和 `docker-compose.release.yml`，具体步骤见[学校部署指南](SCHOOL_DEPLOYMENT.md)。
