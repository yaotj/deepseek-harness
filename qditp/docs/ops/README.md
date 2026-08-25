# docs/ops — 部署与运维交付文档

面向**部署、配置、上线**的交付物。与其他目录的分工：

- `docs/business/` — 业务域编码提示词（写代码前读）
- `docs/architecture/` — 架构与模块技术设计（理解结构时读）
- `docs/ops/` — 部署与配置清单（**上线、配环境、排查环境问题时读**）
- `docs/reviews/` — 代码审查记录（按链路 + 日期归档）
- `docs/接口规范文档/`、`docs/业务需求文档/`、`docs/技术规范文档/` — 甲方提供的原始规格，只读，勿改

## 文档清单

| 文档 | 用途 | 何时读 |
|---|---|---|
| [生产环境清单.md](生产环境清单.md) | 服务部署清单、外部依赖、环境变量、网络开通、部署顺序、上线核对清单 | 上线前、扩环境、排查连不通 |
| [敏感配置外置方案.md](敏感配置外置方案.md) | 明文密钥清单（只记键名与位置）、外置写法、Secret 划分、轮换流程 | 改配置、加密钥、安全整改 |

## AI 使用约束

- 涉及**部署配置、环境变量、外部系统地址、密钥**的改动，**MUST** 先读上表对应文档。
- 修改 `application.properties|yml` 中的地址或凭据 **MUST** 先与用户确认目标环境，**NEVER** 擅自改动线上路由。
- 新增敏感配置项 **MUST** 写成 `${ENV_VAR:}`（空默认值），**NEVER** 写默认真值。
- **NEVER** 在对话、日志、提交信息或文档中回显密钥值，引用时只写键名与位置。
- 发现新的环境类问题（错配地址、测试残留、本地路径、单副本约束）**MUST** 追加到 `生产环境清单.md` 的问题章节，而不是只在对话里说。

## 当前待办（截至 2026-08-25）

`生产环境清单.md` §一 记录了 2026-08-25 实测的**生产集群** NodePort 映射（`kubernetes.b` @ `172.20.211.23`，用户已确认为生产地址，22 个 ITP 服务 + 1 个无关的 httpbin 镜像，均 1 副本），并逐项比对出 **4 处生产在跑的地址错配**：`service.fepDev.url` 指向自身、`service.alipayAccount.url` 指向 key-server、`service.gateTxnPay.url` 用容器端口、`service.dailyTicket.url` 仍是 `127.0.0.1`；另有 `fep-alipay-server`(30023) 为已弃用的旧部署仍在运行（现只用 `fep-alipay` 30020），需停止并删除。这些是**线上正在发生的故障**，修正等同变更线上路由，**MUST** 先与用户确认。

§六 记录了 P0 与 P1，均**未修复**：测试地址残留（含 `gate-txn-pay-server` 的 `wallet.app-gateway-url`）、`ticket-server` 配置行损坏、无效占位地址、商户归属切换日临近；P1 侧新增两类，**`service.alipay-pay-sign.url` 在 `fep-alipay-server` / `blacklist-server` 指向 9096（pay-sign-server）**，以及**三处依赖存在但 URL 键完全缺失**（`ticket-server` 缺 paySign、`fep-app-server` 缺 collectPay、`pay-sign-server` 缺 gateTxnPay，落到集群内无法解析的默认服务名）。上线核对 **MUST** 把「键缺失」当成独立一类检查。

`database`(30031) 已确认是 **BYOK database MCP Server**，不是 Oracle 实例，与业务数据源无关。
