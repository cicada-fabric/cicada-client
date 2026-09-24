# Android Client 开发与验收计划（2026-09-24）

本仓库只开发手机 Client，当前只交付 Android。核心 Architecture v2.1 与实际 Go 路由为契约依据；[真实接口清单](backend-contract.md)、[前一阶段实测](client-hub-interop-results.md)及[当前 v1.1 验收](client-hub-v1.1-validation.md)分别记录不同镜像的证据。前一阶段结果不能认证当前 `fb0f07a` Hub。所有远程操作必须先通过独立固定的 Hub 公钥、OwnerDeviceGrant、加密 `session.capabilities` 与服务端 Guard；不使用旧 `/v1` bearer。

## 已完成的基线

| 范围 | 状态与证据 |
|---|---|
| 五页 Android UI、便捷语音与文字入口 | 已实现；Docker Android 35 模拟器打开五页和输入面板。未登录显示未知/空状态，不注入开发 fixture。 |
| 可选本地 STT | Vosk 中英、Paraformer 中文、SenseVoice Small INT8 可分别下载安装、选择和删除；Docker 公开音频推理、Android 模拟器四款模型的下载/选择或 JNI 实测见 [STT 记录](stt-validation.md)。本轮修正 SenseVoice `tokens.txt` 的截断 SHA-256，并在模拟器验证了真实下载、安装、选择与中文推理；Vosk 英文也通过真实下载和英文推理。真机麦克风、内存、耗电未测。 |
| Client→Control PQ 安全会话 | Android Kotlin 设备 ML-KEM/ML-DSA 身份、Keystore 包裹、可信 Hub 公钥固定、Owner Grant 登记和加密 `/v2/client/rpc` 已实现；真实 Docker Hub 测过重放、篡改、撤权、断线和精确密文恢复。 |
| 权威状态与基础管理 | `session.capabilities` 授权下读取 `status.snapshot/changes`，提交并查询当前 `intent`，操作 `topology.*`、`nodes.*`、`approvals.*`；真实 Hub 通过成功与部分业务拒绝路径。 |

## 按依赖执行

当前优先项是 [核心接口缺口](hub-interface-requests.md)中的 N4 安全恢复与 N5 真实 Worker/审批闭环。Client v1.1 已固定协议包和 catalog 摘要，公开 Kotlin 向量先于新 Hub Android 联调；Group Endpoint Key Grant 在完整 proof 取证与本地验签前维持关闭。

1. **P0：契约与任务书对账（本轮完成）。** 已修正旧“Hub 未接入”叙述，逐项核对当前核心提交、能力标志、OpenAPI、实际路由和测试；记录已完成、部分与后端未就绪。核心源码和数据库未由 Client 仓库改写。
2. **P1：请求恢复与状态可信度（部分完成）。** 工作页已使用加密 `intent.list/status` 按需找回本人请求与业务结果；真实 Hub 和 Android 模拟器已验证重建会话后可取回 `intent.list/get/status`。已登录模拟器还实测了文字草稿经单独确认提交、Hub 持久 Intent ID、加密业务进度和 `DONE` 与 Goal 状态的区分。`status.changes` 逐页读取，已验签业务拒绝会重新读取能力与快照。超过 5 分钟未成功读取快照、同步失败或存在待恢复密文时，页面标明“上次快照，待对账”；可重复运行的 Android 断网→重启 App→恢复原始密文→重读快照脚本已通过。全新模拟器也实测候选 Hub 不会自动成为 trust pin、错误 Hub/Control 公钥拒绝登记和无效 Grant 本机拒绝。对外层 HTTP 409/`UNCERTAIN` 继续保持 fail closed；仍需核心明确可安全退休待处理包、再发新只读对账请求的契约，以及真机前后台断线验收。
3. **P2：补齐已存在的管理 API（部分完成）。** 设置页可按需调用 `devices.list`，对其他 ACTIVE 设备按版本请求 `devices.revoke`；工作页仅对远端未领取 Worker 提供 `goal.lifecycle` 排队暂停/恢复入口。真实 Docker Hub 已验证设备列表、自撤销拒绝、过期版本拒绝、可丢弃设备撤销成功及撤权后 403。Android Kotlin 加密 RPC 在真实远端排队 Goal 上验证暂停、过期版本拒绝、恢复与再次暂停，Node jobs 不可领取；手机 UI 的恢复排队与暂停排队确认也已手动点击并对账。管理列表现在进入管理页后读取，大列表分批渲染；写操作将 Hub 接受与后续对账失败分开提示。每类写操作仍需继续验收授权、版本冲突、撤权、断网/精确重试和权威状态重读；真实待审批记录需要核心运行时产生。
4. **P2b：跨用户提案（主路径已验收，继续补边界）。** 核心已提供加密 `link.list` 的 owner-scoped 分页，以及 `link.invite_create/preview/accept` 的一次性提案流程；`topology.apply` 可按版本撤销本人参与的 Link。Client 仅在 `external_link_invites=true` 且加密会话授权时开放提案管理，原始 token 只在当前内存展示一次，接受前显示 Hub 受限预览并由用户明确确认；写入后重读 `link.list`。两台独立 Android 35 模拟器与隔离 Docker Hub 已验证双方 Owner/Node/Endpoint 登记、邀请、受限预览、接受、双方列表、一次性重放拒绝、旧版本冲突及版本化撤销。来源端还通过 Hub 已处理但 relay 丢失响应的原始密文恢复；这不等于离线写入或 Hub 进程重启。真实过期 token、错误目标和实际授权密钥签署未验收。`link.key_*` 与 `group.key_*` 仍只作为 Hub 广告能力记录，Android 原生 RPC allowlist 禁止调用，待完整合同、双方 Endpoint attestation 和当前绑定独立验证后再启用。`external_thread_links=false` 时持续隐藏普通 peer 聊天和生效连线；PROPOSED 不能发消息。
5. **P3：手机体验与设备验收。** 真机测麦克风、中文准确率、加载/推理延迟、峰值内存和电量；GB 级模型先做空间预检、续传/失败恢复再开放。复核 Android Keystore 能力、HTTPS/证书、公网延迟和手机前后台恢复。探索安全的快捷入口，但不启用后台常听或自动上传音频。
6. **P4：依赖后端的完整能力。** 完整状态推送、跨用户 Thread 的真实双端授权及普通 peer 盲 Hub 加密、生产设备引导/换机与密钥轮换，都必须等核心契约与端到端验收；能力为 `false` 时保持入口关闭。iOS 与原生鸿蒙作为后续独立平台阶段，复用契约与样例，不虚报构建。

## 每阶段工作规则

- Docker 数据根目录须为 `/gpu1-share/data/docker-root`；Client 缓存、模型、APK、测试密钥与输出只放 `/gpu1-share/data/cicada-client`，不入 Git。只在本仓库编辑 App；核心仓库用于读规格、启动开发 Hub 和必要的开发测试公钥登记。
- 固定依赖版本；按需加载大量列表和详情。HTTP 200、入队、Intent `DONE`、Node 在线与 Worker/Goal 完成是不同事实，必须显示来源与观察时间。
- 先做有意义的真实 Hub 与 Android 验证，再在 `dev/` 分支阶段性提交。不自动推送、合入 `main`、发布、部署生产或轮换真实密钥。每次交付记录命令、退出码、未运行项和安全边界。
