# 给 CICADA 核心开发者的 Client v1.2 交接

**固定基线：**Hub commit `01d51ece186a7ec53dc2a83b77e05085f939bd28`，协议包 SHA-256 `628910647ecca9cf1b6d72a2872e22f6b0158b421a92b6349331a1bfdb640151`，catalog SHA-256 `613084ee67f75d27762ddaf59ec2e5b33ebea7383cbfcf455a50b472e756c66a`，完整镜像 ID `sha256:e31b4c5dc6fceb27932fbc4e5a43afac425b6ef0647a3c7f25788ff52b31585b`。Client 只修改 `~/CICADA_CLIENT`，不读取 Hub 数据库，不在手机保存 Hub/Node bearer，也不使用旧 `/v1` 管理接口。实测与未运行项见 [v1.2 验收](client-hub-v1.2-validation.md)。

## Client 已对接

- Android Kotlin 独立验证公开双向密码学向量；候选 Hub 身份仍须用户独立固定，Grant 本机验签。加密 `session.capabilities` 校验 owner、角色、合同和 catalog 后才开放相关操作。
- 登记 201 丢失后，原 Grant 请求跨重建原样重发。加密 RPC 在网络发送前持久保存原签名密文、operation ID、请求序号和预期响应序号；丢失 200 后交 `/v2/client/rpc/recover`，已完成时消费原密文。`STILL_PROCESSING` 保留 pending，`RECOVERY_UNAVAILABLE` 保留阻断，`RECOVERY_REJECTED` 只有用户明确选择才原包重试；签名 `OUTCOME_UNCERTAIN` 退休传输 pending，但保留业务不确定标记并要求权威状态对账。固定镜像的后三种恢复故障注入还未通过 Android 实测。
- 文本与本地语音草稿经确认调用 `intent.submit/status`；manager 在授权下以 `intent_id` 查询 `goal.result`，分别显示 Intent、Goal、Worker 和有限 Artifact 引用。`approvals.list/decide` 已接入，真实远端 Codex Worker 审批闭环仍阻塞。
- `external_thread_links=false` 时只显示连接提案，`status_events=false` 时只用快照和部分 changes。Group Key Grant 签署保持关闭。

## 核心需处理

1. **修正 Endpoint attestation 签名原文冲突。**固定 wire 明确签名 JSON 排除 `signature`，固定 Go 实际签入 `"signature":null`。请选定唯一原文字节，让 Hub/Node/Client 合同与实现一致；提供完整公开合成向量及新干净提交、协议包、catalog、镜像 ID。否则 Client 无法独立验证 `candidate_attestation`、binding/manifest digest，不会签 `group.key_grant`。请求和复现见 [v1.2 接口请求](hub-interface-requests-v12.md)。
2. **提供隔离固定镜像恢复故障入口。**固定 Go Store/HTTP 测试覆盖 `STILL_PROCESSING`、重启后 `OUTCOME_UNCERTAIN`、旧请求 `RECOVERY_UNAVAILABLE`；现有运行镜像没有可让 Android 合法触发的确定性入口。请给隔离数据和完整命令，不要求 Client 读数据库或持有管理 bearer。Client 需要逐项验证原包、密文、签名、预留序号、撤权与禁止重复执行。
3. **完成远端 Node Codex Worker 审批桥。**需要同一 Goal/Worker/attempt 的 Node 认证审批请求、Client `approvals.decide`、旧 attempt fencing、Worker 继续原任务并回报。现有人工 Node HTTP result 与合成 Approval 不能替代真实原生回调。

## 可直接交给核心开发 AI 的提示词

> 你只负责 `~/CICADA`，先检查工作树和固定 `01d51ece186a7ec53dc2a83b77e05085f939bd28` 合同、Go 路由、测试；不要修改 `~/CICADA_CLIENT`。Client 已固定 v1.2 协议包 SHA-256 `628910647ecca9cf1b6d72a2872e22f6b0158b421a92b6349331a1bfdb640151`、catalog `613084ee67f75d27762ddaf59ec2e5b33ebea7383cbfcf455a50b472e756c66a` 和完整镜像 ID `sha256:e31b4c5dc6fceb27932fbc4e5a43afac425b6ef0647a3c7f25788ff52b31585b`。请先解决 `EndpointKeyAttestation` 签名原文冲突：wire 排除 `signature`，Go `json.Marshal` 实际包含 `"signature":null`；统一 Hub、Node、wire、OpenAPI 和公开完整向量，提供可独立验证的原始证明、完整公钥、ID/epoch、revision、binding 和 manifest digest。再提供隔离固定镜像可复跑的 Android 故障注入方案，分别触发 `/v2/client/rpc/recover` 的 `STILL_PROCESSING`、重启后的签名密文 `OUTCOME_UNCERTAIN` 和旧请求 `RECOVERY_UNAVAILABLE`，不让 Client 读 Hub 数据库或持有 bearer。最后完成真实远端 Node Codex Worker→审批→继续原任务→`goal.result` 桥，含 Node 认证和旧 attempt fencing；不要用人工 Approval 或合成 Node 结果代替。每项交付干净 commit、协议包/catalog SHA-256、完整镜像 ID、测试命令/退出码和脱敏证据；详见 `~/CICADA_CLIENT/docs/hub-interface-requests-v12.md` 与 `docs/client-hub-v1.2-validation.md`。
