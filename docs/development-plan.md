# CICADA Client 开发与验收计划（2026-09-24）

本仓库只开发手机 Client，当前只交付 Android。核心 Architecture v2.1 与固定 Go 路由为契约依据；[真实接口清单](backend-contract.md)和[当前 v1.2 验收](client-hub-v1.2-validation.md)记录本轮证据。[v1.1 验收](client-hub-v1.1-validation.md)仅是历史记录，不能认证 `01d51ece` Hub。所有远程操作必须先通过独立固定的 Hub 公钥、OwnerDeviceGrant、加密 `session.capabilities` 与服务端 Guard；不使用旧 `/v1` bearer。

## 已完成的基线

| 范围 | 状态与证据 |
|---|---|
| 五页 Android UI、便捷语音与文字入口 | 已实现；Docker Android 35 模拟器打开五页和输入面板。未登录显示未知/空状态，不注入开发 fixture。 |
| 可选本地 STT | Vosk 中英、Paraformer 中文、SenseVoice Small INT8 可分别下载安装、选择和删除；Docker 公开音频推理、Android 模拟器四款模型的下载/选择或 JNI 实测见 [STT 记录](stt-validation.md)。本轮修正 SenseVoice `tokens.txt` 的截断 SHA-256，并在模拟器验证了真实下载、安装、选择与中文推理；Vosk 英文也通过真实下载和英文推理。真机麦克风、内存、耗电未测。 |
| Client→Control PQ 安全会话 | Android Kotlin 设备 ML-KEM/ML-DSA 身份、Keystore 包裹、可信 Hub 公钥固定、Owner Grant 登记和加密 `/v2/client/rpc` 已实现；真实 Docker Hub 测过重放、篡改、撤权、断线和精确密文恢复。 |
| 权威状态与基础管理 | `session.capabilities` 授权下读取 `status.snapshot/changes`，提交并查询当前 `intent`，操作 `topology.*`、`nodes.*`、`approvals.*`；真实 Hub 通过成功与部分业务拒绝路径。 |

## 当前依赖与阶段顺序

当前优先项是 [v1.2 接口请求](hub-interface-requests-v12.md)中的 Endpoint attestation 签名字节冲突、固定镜像的确定性恢复故障入口和真实远端 Worker 审批桥。Client v1.2 已固定协议包、catalog、完整镜像 ID，并重跑公开 Kotlin 向量与运行镜像 Android 联调；Group Endpoint Key Grant 在签名字节合同一致且完整 proof 本地验签前维持关闭。

### v1.2 后续步骤

1. 核心明确 Endpoint attestation 的唯一签名原文，提供修订协议包、干净 commit、完整镜像 ID 和合成向量；Client 才实现原始 proof SHA-256、ML-DSA-65、公钥与所有 binding/revision/manifest digest 独立校验，再开放 owner 签署。
2. 核心提供隔离固定镜像的可复现故障入口；Android 分别验证 `STILL_PROCESSING`、签名密文 `OUTCOME_UNCERTAIN`、旧请求 `RECOVERY_UNAVAILABLE`，并校验跨重启证据与无重复操作。
3. 核心完成真实 Node Codex Worker 审批桥；Client 验证同一 Goal/Worker/attempt 的审批、继续执行和结果。随后单独验收真机和公网 HTTPS。阶段性通过后再考虑合入 main。

4. 在真实设备上测麦克风、中文准确率、加载和推理耗时、峰值内存、电量以及前后台恢复；另测公网 HTTPS、证书和延迟。GB 级模型经空间预检和中断恢复验证后再开放。
5. 完整状态推送、跨用户 Thread 的真实双端授权、普通 peer 盲 Hub 加密、设备换机与密钥轮换依赖核心合同和端到端验收；能力为 `false` 时保持入口关闭。iOS 和原生鸿蒙作为独立平台阶段，复用产品行为与协议测试向量。

## 每阶段工作规则

- Docker 数据根目录须为 `/gpu1-share/data/docker-root`；Client 缓存、模型、APK、测试密钥与输出只放 `/gpu1-share/data/cicada-client`，不入 Git。只在本仓库编辑 App；核心仓库用于读规格、启动开发 Hub 和必要的开发测试公钥登记。
- 固定依赖版本；按需加载大量列表和详情。HTTP 200、入队、Intent `DONE`、Node 在线与 Worker/Goal 完成是不同事实，必须显示来源与观察时间。
- 先做有意义的真实 Hub 与 Android 验证，再在 `dev/` 分支阶段性提交。不自动推送、合入 `main`、发布、部署生产或轮换真实密钥。每次交付记录命令、退出码、未运行项和安全边界。
