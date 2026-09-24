# Android Client ↔ Hub v1.2：已实现的服务端契约（2026-09-24）

来源：核心仓库干净提交 `01d51ece186a7ec53dc2a83b77e05085f939bd28` 导出的 [固定协议包](../contracts/README.md)，并核对实际路由和测试。核心服务端是权威定义；本仓库不链接 Go 核心，也不调用旧 `/v1` 管理 bearer API。固定镜像 ID 为 `sha256:e31b4c5dc6fceb27932fbc4e5a43afac425b6ef0647a3c7f25788ff52b31585b`；本地隔离 Hub 在 `127.0.0.1:8789`，模拟器使用 `10.0.2.2:8789`；生产仅允许 HTTPS 和独立核验的 Hub 身份。历史镜像和旧验收不能证明 v1.2。

| 步骤 | 真实接口 | Android 用法与边界 |
|---|---|---|
| 公共能力 | `GET /v2/client/capabilities` | 核对 `contract_revision=client-hub-v1.2` 和 catalog `613084ee67f75d27762ddaf59ec2e5b33ebea7383cbfcf455a50b472e756c66a`；这只表示 Hub 实现能力，不授予设备权限。`status_events=false`、`external_thread_links=false` 时隐藏推送和跨用户消息入口。 |
| 固定身份 | `GET /v2/client/identity` | 用户从独立可信渠道提供完整 `hub_id` 与 Control 公钥；App 校验公钥 ID 并逐字节比较，绝不自动信任首次 HTTP 返回。 |
| 设备登记 | `POST /v2/client/devices/enroll` | 手机生成 ML-KEM-768/ML-DSA-65 密钥；owner 独立签署绑定 Hub/设备/密钥/时限/nonce 的 `OwnerDeviceGrant`。App 在首次 POST 前持久保存完整原始请求；201 响应丢失时，仅原样重发该请求，并核对返回的 owner、device、epoch 和 key version。无手机端自我批准或 bearer 捷径。 |
| 敏感读写 | `POST /v2/client/rpc`、`POST /v2/client/rpc/recover` | 所有业务请求使用精确 Client-Control v1 route/AAD、ML-KEM、ML-DSA、HKDF、AES-GCM。持久保存序号、预期响应序号和原始签名密文包；响应丢失先用 `/recover` 查原包。`COMPLETED` 返回同一密文；`STILL_PROCESSING` 保留 pending；`OUTCOME_UNCERTAIN` 验签后退休传输 pending 但保留不确定业务证据，并要求权威快照对账；`RECOVERY_UNAVAILABLE` 不猜序号或重发；`RECOVERY_REJECTED` 仅允许用户明确选择原包重试。HTTP 200 内仍可能是加密业务拒绝。 |
| 会话授权 | 加密 `session.capabilities` | 核对当前 owner、角色、`contract_revision`、`catalog_sha256`，再按 `available_rpc_operations` 限定设备操作；旧版本缓存授权失效。 |
| 状态 | 加密 `status.snapshot`、`status.changes` | 快照含 Node/Endpoint/Worker/Goal/Group/Task 及来源/新鲜度；变化流是 `completeness=partial` 的持久游标，不是 push。定期快照对账，列表只缓存于内存。 |
| 自然语言 | 加密 `intent.submit`、`intent.status`、`goal.result` | 文本或本地 STT 草稿经用户确认后提交；`intent.status` 的 `DONE` 只说明 Intent 分发完毕。Manager 会话在授权后用 `intent_id` 查询有界 `goal.result`，独立显示 Intent、Goal、Worker 终态及 Artifact 引用。 |
| 请求历史 | 加密 `intent.list`、`intent.get`、`intent.status` | 服务端已有 owner 范围的持久 Intent 列表与详情；Android 工作页用 `intent.list` 按需读取历史，用 `intent.status` 逐条读取业务与分发进度。`intent.get` 已在 Android 互操作中验证，但 UI 无需重复调用。重新创建会话后的查询已在真实开发 Hub 测过。返回含原文/结果，只能在认证后按需读取，不写入持久缓存。当前 `intent.list` 的 Store 查询没有分页或服务端数量上限；Client 仅能按需调用并限制本机显示数，大量历史需要后端增加分页契约。 |
| 设备管理 | 加密 `devices.list`、`devices.revoke` | 同 owner Client 设备清单和版本化撤销已接手机设置页；当前手机的撤销按钮隐藏，服务端也拒绝自撤销。真实 Docker Hub 已验证列表、业务拒绝、可丢弃设备撤销及撤权后 403；不能把 Node 绑定撤销当作 Client 设备撤销。 |
| Goal 生命周期 | 加密 `goal.lifecycle` | 工作页仅在已知、非陈旧的远端 queued/paused Goal 且其 Worker 均已知为 queued 时提供 `pause/resume`；提交前重读权威快照并带 `expected_version`，失败后不自动重试。Android Kotlin 加密 RPC 已在真实 Docker Hub 的远端排队 Goal 上通过暂停、过期版本拒绝、恢复和 Node 不可领取验证；手机 UI 的恢复/暂停排队确认亦已实测。服务端不停止正在运行的原生 Worker。 |
| 拓扑 | 加密 `topology.snapshot`、`topology.apply` | `group.create`、`group.set_parent`、`endpoint.join_group/leave_group`、`membership.bind_role`、同 owner `link.propose/revoke`；使用对象版本/绑定 epoch，失败重新读取权威状态。 |
| Node 绑定 | 加密 `nodes.preview/confirm/list/revoke` | Node 本地保管 bearer，手机只输入短码；先预览核对再批准；撤销带 `expected_version`。 |
| 审批 | 加密 `approvals.list/decide` | manager owner 才能列举和决定；`accept/decline` 只对 pending 生效。 |
| 跨 owner 提案与密钥同意 | 加密 `link.list`、`link.invite_create/preview/accept`；Hub 另广告 `link.key_*` | Android 管理页按 `external_link_invites` 与加密会话授权展示 owner-scoped 分页列表、一次性邀请、受限预览、明确接受和版本化撤销；所有状态均标为提案。隔离真实 Docker Hub 与两台新 Android 模拟器已通过双 Owner 的邀请→预览→接受→双方列表→版本冲突拒绝→撤销主路径；非法 token、跨 Owner source 和消费后重放亦被拒绝。`external_thread_links=false`，只展示提案。`link.key_*` 和 `group.key_*` 在 Android 原生 RPC allowlist 中禁用，直到 Client 独立校验完整合同、manifest 和 Endpoint attestation。 |
| Group Endpoint 密钥 | 加密 `group.key_manifest/grant/status` | Hub v1.2 返回完整 `candidate_attestation`，但固定文档与 Go 签名字节不一致；Client 的可调用 allowlist 仍不包含这三项，owner 签署入口关闭。见[接口请求](hub-interface-requests-v12.md)。 |

**身份区分：** Hub 文档中的 `control_public_identity.id` 是 Client→Control 加密接收方，不是 Manager Owner ID。实际开发 Hub 的 resident Owner 由 Control 自身身份决定；测试时若把接收方公钥 ID 当成 `owner_id`，加密通道仍可建立，但 `session.capabilities.role` 正确返回 `external`。界面必须只信任加密的会话能力。

**版本与证据边界（2026-09-24）：** 固定提交、镜像和包的实际验证见 [v1.2 验收](client-hub-v1.2-validation.md)。核心仓库工作树可能继续前进，不能由其当前 HEAD 推断本次固定镜像。真实 Codex、真机、公网 HTTPS 结果需分别验收；本仓库不修改核心状态声明。

## 目前不开放为可用功能

- `status_events=false`：没有完整推送，使用 `status.changes` 轮询并周期性快照对账。
- `external_thread_links=false`：邀请和密钥同意只是提案；普通 peer 消息仍有 Hub 可见明文路径，不能展示为可聊天的跨用户连接。
- 跨 owner 邀请 token 是一次性 bearer，最多一小时；Hub 预览不返回源 owner/Node/Endpoint/Group ID。App 只在当前内存展示创建结果，切后台清除；用户需自行通过可信渠道交给目标用户，当前没有指定目标 owner 的邀请绑定。
- 外部 owner 会话不能调用本 Hub 的 Intent、Goal、Approval 管理操作；真实权限以加密 `session.capabilities` 为准。
- 公网 TLS 部署、设备换机/恢复及真机麦克风性能仍需独立验收。开发 HTTP 只允许模拟器到宿主机回环映射，敏感正文仍在应用层加密。

## 开发顺序与验证门槛

1. 独立实现 Android PQ 密钥、系统 Keystore 包装、手动可信 Hub 公钥固定、Grant 验证和登记；使用非 Go 客户端对真实 Docker Hub 通过 `session.capabilities`，核对响应签名/密文。
2. 持久化请求序号及精确密文包；实测原包重试得到相同响应、改包重放被拒绝、设备撤销后被拒绝、断线恢复与未确定请求的权威对账。
3. 接状态/Intent/管理 UI；成功、业务拒绝、版本冲突、撤权和断线均显示来源和恢复动作；不保留敏感请求正文到日志或长期缓存。
4. 保持现有本地 STT 和 UI；没有会话时不发送管理数据，不用 `/v1` 降级。
