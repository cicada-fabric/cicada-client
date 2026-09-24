# CICADA Android Client

本仓库负责 CICADA 手机 Client。**当前优先交付 Android**，以后还要支持 iOS，并迁移成**原生鸿蒙应用**。当前主工程采用 TypeScript + React Native；鸿蒙版届时使用 ArkUI/ArkTS 重写原生页面，沿用明确的产品流程与数据契约。选型和迁移边界见 [技术选型](docs/stack-decision.md)。Client 是用户随身查看、输入和管理 CICADA 的入口；Control、权威管理状态与面板服务通常运行在 Hub，Worker 的原生 Thread 运行在 Node。App 不替 Control 规划，也不直接向 Worker 的原生 Session 注入输入。

当前阶段已在原有 UI 和端侧语音转文字基础上，接入核心仓库提供的 **Client ↔ Hub v2 加密契约**。目标架构以相邻核心仓库 [`../CICADA/CICADA.md`](../CICADA/CICADA.md)（Architecture v2.1）为准，实际接口和完成度以核心 `docs/architecture-v2-status.md`、服务端代码及测试为准。本仓库只开发 Client；核心仓库用于读取规格、启动开发 Hub 和本地测试 Owner 公钥登记。

第一版 Android 目标：

1. 随时查看每个已授权 Node、Worker、Goal 和 Group 的状态、更新时间与来源；区分运行、等待、暂停、失败、完成、未知，以及消息投递和业务结果。
2. 提供尽量便捷的语音入口和同等可用的文字入口。用户可下载、安装、切换多个手机端侧模型，允许 GB 级模型；第一版只启用实测过的语音转文字能力，未来用途另行设计。没有本地模型时仍可打字；联网转写仅作为后续明确告知并经用户选择的可选方案。转写结果先供用户检查，再发给 Control。
3. 将用户的自然语言请求交给 Hub 上的 Control。Control 负责查询 Worker/Group、选择 Node/Workspace/Agent、创建或启动 Goal 等实际管理工作；手机呈现持久请求、澄清、审批、结果与失败状态。
4. 在手机上展示和操作 Hub 的管理面板。每次变更都提交到 Hub 的权威服务，按服务端版本、权限和结果更新画面；断网或冲突后重新对账。
5. 支持用户发起、审阅和撤销与其他人的 Thread 的限定范围对接；跨用户连线必须双方真实同意，不能由手机画一条线就生效。

**手机 Client ↔ Hub 上的 Control 的业务 RPC 使用 ML-KEM-768、ML-DSA-65 和 AES-256-GCM 应用层加密与认证。** 设备私钥由 Android Keystore 包裹；Hub ID 和完整 Control 公钥必须由用户通过独立可信渠道输入并固定，设备须取得 Owner 独立签发的一次性 Grant。此链路的明文终点是手机与 Control；Control 处理指令需要读取正文。普通 peer 消息的“Hub 不可读”仍是另一项未完成目标。发布版只接受 HTTPS；开发 HTTP 仅允许 debug 模拟器访问 `10.0.2.2:8787` 或 `:8788`。本 App 不使用旧 `/v1` bearer API。

开发与测试在 Docker 中进行。已核实 Docker 数据根目录为 `/gpu1-share/data/docker-root`；本项目缓存和测试文件使用 `/gpu1-share/data/cicada-client`，与现有 `/gpu1-share/data/cicada` 分开。开发前先阅读 [AGENTS.md](AGENTS.md)、[产品与交互](docs/product.md)、[真实接口与缺口](docs/backend-contract.md)、[开发顺序](docs/development-plan.md) 和 [稳定版本核对](docs/stack-audit-2026-09-24.md)。

## 当前可运行内容与边界

- React Native 共享 UI：概览、节点、工作、管理、我的。登录后以加密 `session.capabilities` 的实际 allowlist 开启操作；`status.snapshot/changes` 读取真实状态并显示来源/观察时间，断线后保留待恢复密文。超过五分钟未成功读取快照、同步失败或存在待恢复密文时，页面标为上次快照。未登录显示空状态，不展示开发 fixture。
- 状态大列表按批渲染，Node/Goal 页复用 Worker 索引；拓扑和 Node 管理列表进入管理页后读取。工作页有请求历史快捷入口，Intent 原文只在认证后按需读取并保留于当前内存。服务端 `intent.list` 尚未分页，历史特别多时首次读取量仍受核心契约限制。
- Android Kotlin 原生安全适配：独立固定 Hub 公钥、生成设备 PQ 身份、验证 OwnerDeviceGrant、设备登记、加密 `/v2/client/rpc`、精确密文重试和撤权隔离。当前 `client-hub-v1.1` 与 catalog 摘要会在公开候选和加密 `session.capabilities` 中核对；旧缓存授权随版本升级失效。登记结果丢失或 RPC HTTP 409 时保留本机证据并暂停可能重复执行的操作，安全恢复协议仍待核心补齐。文字或本地语音转写经用户确认后通过 `intent.submit/status` 交给 Control；工作页可按需找回本人 Intent。管理界面接 `topology.*`、`nodes.*`、`approvals.*`，设置页接本 Owner 的 `devices.list/revoke`；远端未领取 Worker 的 Goal 可使用有限的排队暂停/恢复入口。跨用户 Link 提案页按会话授权提供 `link.list` 与一次性邀请、预览、接受和版本化撤销。提案不表示普通 peer 消息可用。旧镜像的互操作记录见 [前一阶段记录](docs/client-hub-interop-results.md)；当前版本的证据另见 [v1.1 验收](docs/client-hub-v1.1-validation.md)。
- 首页底部可一键打开语音输入；未安装模型时可直接文字输入。用户可在「设置」中分别安装、选择和删除 Vosk 中文/英文、Paraformer 中文小模型和 SenseVoice Small INT8。权重均按需下载，不内置于 APK；录音和识别留在设备上，文字是可编辑草稿。运行时依赖 sherpa-onnx AAR 在构建时单独下载并核验。模型选型与候选见 [模型选择](docs/model-selection.md)。
- 开发 Hub 当前声明 `status_events=false` 和 `external_thread_links=false`：App 隐藏实时推送和跨用户 Thread 消息入口。`status.changes` 只是部分增量；跨用户邀请/密钥同意不代表 peer 消息可用，Key Grant 签署入口尚未启用。公网 HTTPS、真机 STT 性能、iOS 与原生鸿蒙仍未验收。

在仓库根目录构建（Docker 必须可用，数据根目录必须位于 `/gpu1-share/data`）：

```bash
./scripts/docker-build.sh
```

APK 输出到 `/gpu1-share/data/cicada-client/build-output/cicada-client-debug.apk`。离线语音模型的真实推理结果和重现命令见 [实测记录](docs/stt-validation.md)。模型权重、测试音频、AAR、Gradle 缓存和 APK 不加入 Git。

真实后端契约及本地验证步骤见 [Client ↔ Hub 接口清单](docs/backend-contract.md)、[v1.1 验收](docs/client-hub-v1.1-validation.md)、[核心接口缺口](docs/hub-interface-requests.md)和[核心开发交接](docs/cicada-core-handoff.md)。开发 Hub 由核心仓库 `./scripts/run-client-hub-dev.sh` 启动，Owner 审批公钥由核心提供的本地 `owner-key register` 命令登记；App 内没有自行批准设备的捷径。

已登记的开发模拟器可运行 `./scripts/interop/run-activity-recovery.sh`，验证断网留下原始密文、重启 App 后精确恢复与权威快照对账。脚本会核对目标 AVD 名称，并在退出时移除临时网络阻断规则；它不创建生产身份。

构建测试 APK 后，可运行 `CICADA_EMULATOR_VECTOR_CHECK=1 ./scripts/docker-emulator-check.sh`，在全新模拟器上执行五页 UI smoke 与公开 Kotlin 加密向量。针对当前 `:8788` 开发 Hub 的候选身份、错误公钥固定和无效 Grant 拒绝路径，可运行 `CICADA_EMULATOR_SECURITY_CHECK=1 CICADA_EMULATOR_HUB_BASE_URL=http://10.0.2.2:8788 ./scripts/docker-emulator-check.sh`；不会提交设备登记。

本开发机的模拟器如需下载模型，可让 Docker 使用物理机 7890 代理：

```bash
CICADA_EMULATOR_PROXY=http://127.0.0.1:7890 ./scripts/docker-emulator-check.sh
```

脚本同时设置模拟器内的 `10.0.2.2:7890` 系统代理；此设置仅用于本地测试，发布版没有代理地址。脚本会将五页 UI 和输入面板的截图保存在 `/gpu1-share/data/cicada-client/build-output/`。
