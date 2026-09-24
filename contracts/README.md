# Client/Hub 固定协议包

当前 Android Client 固定使用 `client-hub-v1.2/`，来自 CICADA commit
`01d51ece186a7ec53dc2a83b77e05085f939bd28`。原始 tar SHA-256：
`628910647ecca9cf1b6d72a2872e22f6b0158b421a92b6349331a1bfdb640151`；
catalog SHA-256：`613084ee67f75d27762ddaf59ec2e5b33ebea7383cbfcf455a50b472e756c66a`。
导入前使用核心 `client-contract.py verify` 核对原始包；本仓库的
`python3 scripts/check-client-contract.py` 独立核对 manifest、完整源提交、
`source_dirty=false`、每个文件及 catalog 原始字节。镜像 ID 和运行时加密
`session.capabilities` 须另外验证，不能由协议包或可变 tag 推断。

`client-hub-v1.1/` 保留为历史验收证据，当前代码不接受其 revision/catalog。
包内向量含**公开合成**私钥，仅用于密码学互操作测试，不能用作真实设备、
Owner 或 Hub 身份。完整 Endpoint 自签证明的签名字节在固定 v1.2 文档与实现
之间有冲突；在核心修正并重新固定前，Client 不开放 `group.key_grant`，见
[接口请求](../docs/hub-interface-requests-v12.md)。
