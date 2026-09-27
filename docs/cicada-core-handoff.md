# CICADA 核心团队交接：Client ↔ 固定 Hub v1.2

**交接日期：**2026-09-25

**Client 实现提交：**`3c2d8e14457d10aaf9b88bbe56215e2c10ff856a`（`dev/react-native`）

**验收依据：**[固定镜像联合验收](client-hub-v1.2-41beaf0-validation.md)

## 固定基线

| 项目 | 固定值 |
|---|---|
| Hub 源提交 | `41beaf0fa57e8279ad993fa4ce070a33515851ba`；`source_dirty=false` |
| 协议包 SHA-256 | `e42cdca3d9f2b8719179476e2e7e87a2a9793c8c5b2a8352331928f882d18d5d` |
| catalog SHA-256 | `613084ee67f75d27762ddaf59ec2e5b33ebea7383cbfcf455a50b472e756c66a` |
| 完整 Hub 镜像 ID | `sha256:528dc6817a35a37c1c028dce85243afb3a7e42b4b04ed9410bd68046ef7d67e8` |
| Client debug APK / AndroidTest APK SHA-256 | `65eff9c51f4c0b9416b36b1d96fac458ac6980f06fd9798e93350c8b60251d19` / `f6fd23ef9a64a054fdf6b19cced0de4b6a202e1eea5312e5a283f95b66b2b847` |

Client 只修改 `~/CICADA_CLIENT`，没有在手机保存 Hub/Node bearer、读取或直接修改 Hub 数据库，也没有使用旧 `/v1` 管理接口。Hub 用完整 image ID 和一次性隔离状态启动；管理 Owner 测试公钥仅经正式 CLI 登记。原始测试材料、Node 凭据及模型凭据均位于 Git 外权限受限目录。

## 已完成的 Client 联调

- 独立 Kotlin 公开双向向量 `OK (5 tests)`，固定包、镜像标签和 Android 加密 `session.capabilities` 的合同、catalog、manager 角色相互一致。
- 固定镜像上登记 201 响应丢失后原 Grant 重发，以及加密 RPC 200 响应丢失后原包 `/v2/client/rpc/recover`，各 `OK (1 test)`；未创建替代 operation 或推测响应序号。
- Android 模拟器通过加密 `nodes.preview/confirm` 绑定真实 Node Agent；加密 `intent.submit` 启动有界 Goal；`gpt-5.6-luna` 的真实原生 Codex turn 请求审批；Android 以 `approvals.list/decide` 明确接受；同一 Worker attempt 1 完成。Android 所见审批 `threadId` 与 Node 认证结果 `thread_id` 的 SHA-256 一致；Android 经 `intent.status` 与 `goal.result(intent_id)` 读取 Intent `resolved`、Goal/Worker `completed`。脱敏证据见当前验收报告。
- `group.key_manifest/grant/status` 与 `link.key_*` 仍在 Android 原生 allowlist 之外。`status_events=false` 和 `external_thread_links=false` 时继续分别使用部分变化加快照，以及仅展示跨用户提案。

## 需要核心继续交付

1. **Endpoint attestation 签名字节合同（BLOCKED）。**固定 wire 文档要求签名原文省略 `signature`，固定 Go 实现签入 `"signature":null`。请统一 Hub、Node、wire、OpenAPI 与公开完整合成向量，交付原始 attestation 字节、SHA-256、ML-DSA-65 签名及完整公钥、所有 Endpoint/Principal/Node/SessionBinding ID 与 epoch、revision、binding 和 manifest digest。Client 完整独立验签前不会签 `group.key_grant`。请求、复现及门槛见[接口请求](hub-interface-requests-v12.md)。
2. **运行镜像的确定性恢复故障驱动（NOT_RUN）。**请分别提供在一次性隔离 Hub 上安全、可复跑地触发原包 `/recover` 的 409 `STILL_PROCESSING`、重启后的签名密文 `OUTCOME_UNCERTAIN`、旧请求 409 `RECOVERY_UNAVAILABLE` 的命令、准备状态、清理步骤、预期 HTTP/密文证据及新固定镜像 ID。不得要求 Android 读取或直接改 Hub 数据库，也不要在生产 RPC 增加故障开关。Client 将核对 pending 原包、计数、阻断和权威状态对账。
3. **独立设备与公网部署验收（NOT_RUN）。**本轮使用 Android 35 模拟器及回环 Hub；物理 Android 设备、HTTPS 证书/网络与真机密钥边界未验收。需要后续环境与独立记录，不可沿用模拟器结论。

## 可交给 CICADA 核心开发者的提示词

> 你只负责 `~/CICADA`。先检查工作树与固定提交
> `41beaf0fa57e8279ad993fa4ce070a33515851ba` 的协议包、路由和镜像；
> 不修改 `~/CICADA_CLIENT`。
>
> Client 实现提交为 `3c2d8e14457d10aaf9b88bbe56215e2c10ff856a`。
> 固定 Hub image ID 为
> `sha256:528dc6817a35a37c1c028dce85243afb3a7e42b4b04ed9410bd68046ef7d67e8`。
> Android 模拟器已通过加密 Hub 与真实 Node Agent、`gpt-5.6-luna`
> 原生 Codex 完成审批闭环；同一 Worker attempt/Thread 继续并经 `goal.result`
> 读取完成。登记 201 与 RPC 200 响应丢失亦已通过。请保留这些证据边界。
>
> 现在请完成两项交付：
>
> 1. 统一 `EndpointKeyAttestation` 的精确 ML-DSA-65 签名原文。
>    当前 wire 排除 `signature`，固定 Go 实现签入 `"signature":null`。
>    提供完整公开合成向量、原始 attestation 字节、签名和公钥，以及所有
>    ID、epoch、revision、binding 与 manifest digest 的可独立验证证据。
> 2. 提供只在一次性隔离镜像使用的安全故障驱动，确定性触发
>    `/v2/client/rpc/recover` 的 `STILL_PROCESSING`、Hub 重启后的签名密文
>    `OUTCOME_UNCERTAIN` 和旧请求 `RECOVERY_UNAVAILABLE`。附准备、执行、
>    清理命令及预期 HTTP/密文证据。
>
> 不要让 Client 读取或直接修改 Hub 数据库、持有 Hub/Node bearer，或调用
> 旧 `/v1`；不要在生产 API 添加故障开关。每项交付干净 commit、协议包及
> catalog SHA-256、完整镜像 ID、`source_dirty`、测试命令/退出码和脱敏证据。
> 参考 `~/CICADA_CLIENT/docs/client-hub-v1.2-41beaf0-validation.md` 与
> `~/CICADA_CLIENT/docs/hub-interface-requests-v12.md`。
