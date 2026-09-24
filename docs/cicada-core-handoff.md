# 给 CICADA 核心开发者的 Client v1.1 交接

**基线：**核心提交 `fb0f07a2330084b9402eb72878388bd1866bee10`，`client-hub-v1.1`，catalog SHA-256 `f6f05783ddc00e51b92ebe050b5d8e6b9b185fae80d8fc6f04bc3143b6782374`。Client 只修改 `~/CICADA_CLIENT`，不读取 Hub 数据库，不在手机保存 Hub/Node bearer，也不使用旧 `/v1` 管理接口。实际镜像、APK、命令和结果见 [验收记录](client-hub-v1.1-validation.md)。

## 已可对接

- Android Kotlin 验证公开的双向加密、ML-DSA 签名、AAD、篡改拒绝向量；候选 Hub 身份仍须用户独立固定。设备 Grant 在本机验签，再向真实 Hub 登记。
- 加密 `session.capabilities` 同时校验 Owner、角色、合同修订和 catalog 摘要；UI 和原生 RPC 以 allowlist 开放 `status.snapshot/changes`、`intent.*`、`topology.*`、`nodes.*`、`approvals.*`、Owner 自己的 `devices.*` 和仅限提案的 `link.*`。实际调用仍受 Hub Guard 控制。
- 原始加密 RPC 包和序号在网络发送前写入设备私有存储；普通断网后按原字节恢复。Hub HTTP 409 后持久冻结原包并停止新 RPC；设备登记 201 丢失时冻结原设备身份和原 Grant 摘要。两者都需要核心提供恢复协议，不能凭客户端猜测终态。
- `external_thread_links=false` 时仅展示 Link 提案，隐藏普通跨用户 Thread 消息；`status_events=false` 时只用快照和部分 changes，不宣称实时完整事件。Group key 的 Client 签署入口关闭，直至取得完整 Endpoint proof 并独立验签。

## 核心需答复和实现

请以 [接口缺口与复现](hub-interface-requests.md)为具体请求。优先顺序建议：

1. **N4 设备登记响应丢失。** 提供与原 Grant、设备公钥、Hub、nonce 绑定的权威查询/恢复；区分已提交、未提交、已撤销、过期、Owner key 失效。当前重发同一登记可能得到 403，Client 无法证明 201 是否已提交。
2. **N4 RPC 409/UNCERTAIN。** 定义设备认证的原包对账；响应需绑定 epoch、sequence、operation ID 和包摘要，区分缓存完成、仍处理、确定未执行、UNCERTAIN、撤权。明确何时可安全退休 pending，尤其是 Hub 重启后。Client 当前冻结，避免重复执行。
3. **Group key 取证。** `group.key_manifest` 经 Client 加密 RPC 返回完整 Endpoint self-attestation 和绑定证据，或提供不要求 Node bearer 的等效公开证明。仅 digest 不足以在手机独立验签。
4. **N5 真实闭环。** 给出隔离的真实 Node/Agent 启动和可稳定产生 Approval 的任务，使 Android Intent→Hub→真实 Worker→审批→结果可复跑。现有 Go `httptest`/人工 claim/result 仅证明协议模拟，不证明真实 Codex 执行。

请先修订 `docs/client-hub-wire-v1.md`、OpenAPI、catalog 和公开向量，再提供针对丢包、409、Hub 重启、撤权和重放的服务端测试。Client 在新合同及证明材料可独立验证后再启用恢复和 Group key。核心若修改 catalog 摘要，Client 的固定摘要和测试包必须同步升级。

## 可直接发送给核心开发 AI 的提示词

> 你只负责 `~/CICADA` 核心仓库。先核对当前 Git 分支、未提交修改、`CICADA.md`、`docs/client-hub-development.md`、`docs/client-hub-handoff.md`、`docs/client-hub-wire-v1.md`、OpenAPI、路由和测试。Client 基线是 `client-hub-v1.1` / `fb0f07a`，现有 Android 适配已按公开合同验证；不要直接修改 `~/CICADA_CLIENT`，也不要让手机读取数据库或持有 Hub/Node bearer。请优先设计并实现安全 N4：设备登记 201 响应丢失时的受认证恢复，以及加密 `/v2/client/rpc` HTTP 409/`UNCERTAIN` 的原包对账与安全退休；保留单调序号和至多一次效果，写出跨重启、撤权、重放、并发故障注入测试。给 Client 明确的请求/响应、认证绑定、终态和重试规则，更新 wire、OpenAPI、catalog、公开向量与协议包。然后为 Group key 的 Client manifest 提供完整可验签 Endpoint proof/绑定证据，不要求 Node bearer；提供隔离的真实 Node Worker→审批→结果测试流程。详见 `~/CICADA_CLIENT/docs/hub-interface-requests.md` 与 `docs/client-hub-v1.1-validation.md`。请逐项回报新核心提交、镜像 ID、合同摘要、测试命令/退出码与尚未解决的风险，不能把 Go mock 视作真实 Codex 或真机/公网 HTTPS 验收。
