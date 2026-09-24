# Hub v1.2 接口阻塞项

## BLOCKED：Group Endpoint attestation 的签名原文不一致

**固定版本**

- Hub commit：`01d51ece186a7ec53dc2a83b77e05085f939bd28`
- 协议包 SHA-256：`628910647ecca9cf1b6d72a2872e22f6b0158b421a92b6349331a1bfdb640151`
- catalog SHA-256：`613084ee67f75d27762ddaf59ec2e5b33ebea7383cbfcf455a50b472e756c66a`
- Hub 镜像 ID：`sha256:e31b4c5dc6fceb27932fbc4e5a43afac425b6ef0647a3c7f25788ff52b31585b`

**问题**

协议包 `docs/client-hub-wire-v1.md` 的 “Complete Group Endpoint key evidence” 节说明，
`EndpointKeyAttestation` 的 ML-DSA-65 签名输入是按给定字段顺序生成的
`unsigned_claims`，并且**排除 `signature` 字段**。

同一固定 Hub commit 的
`cicada-go/internal/e2ee/endpoint_attestation.go` 定义 `Signature` 为 `[]byte`，
JSON tag 是 `json:"signature"` 且没有 `omitempty`。`endpointAttestationSignedBytes` 把
`Signature` 设为 `nil` 后直接调用 `json.Marshal(attestation)`。Go 的
`encoding/json` 会把 nil `[]byte` 序列化为 `null`，所以源码实际签名原文包含
末尾的 `"signature":null`，而不是省略这个字段。Go 签发端和校验端都复用该函数。

因此，严格按固定协议包实现的 Client 会拒绝当前 Go 实现生成的 Endpoint
attestation。若 Client 改按源码签名，则会偏离固定合同。当前不允许据此为
`group.key_grant` 生成 owner 签名；Hub 返回的 candidate/manifest 摘要不能消除此冲突。

**请 CICADA 核心维护者确认并修正**

请明确选择唯一的签名原文，并使 Hub 签发、Hub 校验、Node 校验、协议文档和独立
Kotlin 测试向量完全一致：

1. 若合同定义正确，请修改 Go 签发与校验，使签名原文省略 `signature` 字段；
   检查并更新所有 Endpoint attestation 的调用方与测试。
2. 若 Go 当前行为定义正确，请修订合同，明确 unsigned JSON 中
   `signature:null` 的位置和字节形式。

无论选择哪项，请提供一个仅使用合成密钥的公开向量，至少包含完整
`candidate_attestation` 原始字节、预期 SHA-256、签名原文的精确 UTF-8 字节
（或十六进制）、完整 ML-DSA-65 公钥及独立验证结果。随后以干净 Hub commit
重新导出协议包并提供匹配的 commit、catalog SHA-256 和完整镜像 ID；manifest
须保持 `source_dirty=false`。

**复现步骤**

以下命令仅读取 CICADA 中固定 commit 的源码和提供的协议包：

```bash
tar -xOzf /home/zyf/CICADA/.cicada-data/contracts/client-hub-628910647ecca9cf1b6d72a2872e22f6b0158b421a92b6349331a1bfdb640151.tar.gz docs/client-hub-wire-v1.md \
  | nl -ba | sed -n '148,163p'

git -C /home/zyf/CICADA show 01d51ece186a7ec53dc2a83b77e05085f939bd28:cicada-go/internal/e2ee/endpoint_attestation.go \
  | nl -ba | sed -n '23,52p'
```

最小 Go 行为复现：

```go
package main

import (
	"encoding/json"
	"fmt"
)

type Attestation struct {
	Signature []byte `json:"signature"`
}

func main() {
	encoded, _ := json.Marshal(Attestation{Signature: nil})
	fmt.Println(string(encoded))
}
```

输出为 `{"signature":null}`。固定 commit 中 `endpointAttestationSignedBytes`
执行的正是将 `Signature` 设为 nil 后对完整结构体进行 `json.Marshal`。

**验收门槛**

收到修正后的固定交付和合成公开向量前，Client 不会将
`group.key_manifest` 标记为已验证，也不会启用 `group.key_grant` owner 签署。

## 需要固定镜像上的可复现恢复故障入口

**请求：**请核心团队提供只在隔离测试环境启用的确定性入口或测试驱动，使用同一
`01d51ece...` 镜像和公开 `/v2/client/rpc/recover`，分别让真实 Android 已登记
设备遇到下列三个结果。入口不能要求 Android 读取 Hub 数据库、保存管理 bearer，
也不能让生产 Hub 暴露控制故障的 API。

| 场景 | 预期可观察结果 |
|---|---|
| 原始请求已持久接受、业务仍执行 | 原包 `/recover` 返回 HTTP 409 `STILL_PROCESSING`；Client 保留原包、operation ID 和序号，不新建任务。 |
| 原始请求已接受、Hub 在最终响应密封前重启 | 原包 `/recover` 返回原预留响应序号的签名密文 `OUTCOME_UNCERTAIN`；Client 验签后保留业务不确定标记并读取权威状态。 |
| 旧 schema 无预留响应序号的未确定请求 | 原包 `/recover` 返回 HTTP 409 `RECOVERY_UNAVAILABLE`；Client 不猜响应序号，不重新执行。 |

**现有复现与缺口：**在固定提交的 Git 归档中运行
`go test -count=1 ./internal/store -run 'TestClientRecovery|TestGroupEndpointKeyGrant'`
和
`go test -count=1 ./internal/server -run 'TestClientRPCRecovery|TestClientIntentQueuesWorkForOwnerBoundMachineAgent|TestClientGroupEndpointKeyGrant'`
均 exit 0，证明 Go Store/HTTP 合成故障行为。隔离固定 Docker 镜像已用 Android
验证登记 201 丢失、RPC 200 丢失、未到达 Hub 的原包显式重试、错包与撤权；
该运行镜像没有可由 Client 合法调用的暂停/崩溃注入入口。因此上表三个场景的
**固定镜像 Android 实测为 NOT_RUN**。请返回可复跑命令、隔离数据条件、预期
HTTP/密文证据和完整新镜像 ID；若合同或实现修改，请同时提供新协议包与 catalog。

## BLOCKED：远端 Codex Worker 审批桥

**请求：**请提供真实 Node Codex Worker 在执行原任务期间提出审批、等待同一
Goal/Worker/attempt 的 `approvals.decide`，再继续执行并回报结果的后端通道及
隔离验收步骤。必须由 Node 身份认证并 fence 旧 attempt；App 只通过加密
`approvals.list/decide` 交互，不持有 Node bearer。

**当前复现：**固定提交的 `TestClientIntentQueuesWorkForOwnerBoundMachineAgent`
和本仓库的 Android→Hub→Node HTTP fixture→`goal.result` 路径使用合成 Node
claim/result，不产生真实 Codex 审批回调。预期在真实 Node Worker 发起待审批
动作时，Android `approvals.list` 能看到同一 attempt 的 pending 项，明确决议
后该原生任务继续。当前桥接尚未完成，不能用人工 `CreateApproval` 行代替。
