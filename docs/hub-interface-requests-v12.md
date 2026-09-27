# Hub v1.2 接口请求与完成项

## BLOCKED：Group Endpoint attestation 的签名原文不一致

**固定版本**

- Hub commit：`41beaf0fa57e8279ad993fa4ce070a33515851ba`
- 协议包 SHA-256：`e42cdca3d9f2b8719179476e2e7e87a2a9793c8c5b2a8352331928f882d18d5d`
- catalog SHA-256：`613084ee67f75d27762ddaf59ec2e5b33ebea7383cbfcf455a50b472e756c66a`
- Hub 镜像 ID：`sha256:528dc6817a35a37c1c028dce85243afb3a7e42b4b04ed9410bd68046ef7d67e8`

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
tar -xOzf ../CICADA/.cicada-data/contracts/client-hub-e42cdca3d9f2b8719179476e2e7e87a2a9793c8c5b2a8352331928f882d18d5d.tar.gz docs/client-hub-wire-v1.md \
  | nl -ba | sed -n '148,163p'

git -C ../CICADA show 41beaf0fa57e8279ad993fa4ce070a33515851ba:cicada-go/internal/e2ee/endpoint_attestation.go \
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
`41beaf0...` 镜像和公开 `/v2/client/rpc/recover`，分别让真实 Android 已登记
设备遇到下列三个结果。入口不能要求 Android 读取 Hub 数据库、保存管理 bearer，
也不能让生产 Hub 暴露控制故障的 API。

| 场景 | 预期可观察结果 |
|---|---|
| 原始请求已持久接受、业务仍执行 | 原包 `/recover` 返回 HTTP 409 `STILL_PROCESSING`；Client 保留原包、operation ID 和序号，不新建任务。 |
| 原始请求已接受、Hub 在最终响应密封前重启 | 原包 `/recover` 返回原预留响应序号的签名密文 `OUTCOME_UNCERTAIN`；Client 验签后保留业务不确定标记并读取权威状态。 |
| 旧 schema 无预留响应序号的未确定请求 | 原包 `/recover` 返回 HTTP 409 `RECOVERY_UNAVAILABLE`；Client 不猜响应序号，不重新执行。 |

**当前验证与缺口：**新固定镜像已用 Android 模拟器验证登记 201 响应丢失与
RPC 200 响应丢失。三种 409/不确定结果仅有核心 Go 测试所覆盖的合成故障路径；
本次未发现可在隔离运行镜像上安全、确定、可复跑地触发它们的入口。Client
不读取或直接修改 Hub 数据库，也不要求在生产 RPC 中加入故障开关。因此上表
三项固定镜像 Android 实测均为 **NOT_RUN**。请交付隔离测试驱动、准备与清理
命令、预期 HTTP/密文证据，以及对应干净 Hub commit、协议包摘要和完整镜像 ID。

## 已通过：远端 Node/Codex 原生审批链路

`41beaf0` 固定镜像上，Android 模拟器经加密 `intent.submit` 创建 Goal；真实
Node Agent 的 `gpt-5.6-luna` 原生 Codex turn 发出审批请求；Android 经加密
`approvals.list/decide` 接受；同一 Worker 的 attempt 1 完成，Node 返回的原生
Thread ID 摘要与 Android 所见审批请求的 Thread ID 摘要相同。Android 再用
`intent.status` 与 `goal.result(intent_id)` 读取终态。脱敏证据与早期测试代理
故障的修正过程见[当前验收](client-hub-v1.2-41beaf0-validation.md)。这项通过
不替代 Android 真机或公网 HTTPS 验收。
