# docs/business — 业务域提示词索引

本目录是 **AI 编码提示词**，不是需求文档。每个文件描述一个业务域的**代码实际落地情况**：涉及模块、真实接口、核心类、数据表、状态取值、编码约束与已知坑。

## ⚠️ 接口清单是主线摘录，不是全集

各业务域 `*.md` 中的接口清单**只覆盖主线链路**，不等于模块的全部对外接口。实测（2026-08-25）：**代码侧共 420 条唯一路径**（含双别名 454 条），Apifox 项目内仅 105 条，本目录提示词记录的更少。

因此：
- 判断「某接口是否存在」**MUST** 直接看 Controller 上的 `@RequestMapping` / `@PostMapping` 注解，**NEVER** 以提示词或 Apifox 未收录为依据断言不存在。
- 提示词里没写的接口不代表不该用；发现遗漏 **MUST** 补进对应 `*.md`。
- 同一路径可能在多模块重复实现，定位实现 **MUST** 以「模块 + URL」为准。

## 使用顺序

1. 从下表定位业务域，读取对应 `*.md` 提示词；
2. 按提示词中「参考原始文档」一节读取 `docs/接口规范文档/`、`docs/业务需求文档/`、`docs/技术规范文档/` 下的规格原文；
3. 读取提示词列出的核心类源码，确认状态机与字段；
4. 再开始编码。

## 业务域清单

| 业务域 | 提示词 | 主要模块 |
|--------|--------|----------|
| APP 统一接入层 | [app-gateway.md](app-gateway.md) | fep-app-server |
| 账户开户 / 支付通道 / 电子员工卡 | [account-employee-card.md](account-employee-card.md) | account-server, fep-acc-server |
| 乘车码与过闸票卡状态 | [ride-code.md](ride-code.md) | ticket-server, fep-dev-server, industry-data-server |
| 闸机出站扣费（后付费） | [gate-txn-pay.md](gate-txn-pay.md) | gate-txn-pay-server |
| 支付签约 / 解约 / 免密扣款 | [pay-sign.md](pay-sign.md) | pay-sign-server |
| 支付宝渠道（碰一下 / 出行） | [alipay-channel.md](alipay-channel.md) | fep-alipay-server, alipay-pay-sign-server, alipay-account-server |
| TVM / BOM 非现金购票、充值、退款 | [tvm-bom-pay.md](tvm-bom-pay.md) | collect-pay-server |
| APP 扫码取票（单程票） | [app-ticket-collect.md](app-ticket-collect.md) | collect-ticket-server |
| 日票 / 多日计次票 | [daily-ticket.md](daily-ticket.md) | daily-ticket-server |
| 公共能力（黑名单 / 密钥 / 参数） | [common-services.md](common-services.md) | blacklist-server, key-server, para-server |
| 安全服务与加密机 | [security-hsm.md](security-hsm.md) | acc-security-server, acc-secure-server |
| ACC 编码设备与文件传输 | [acc-es-file.md](acc-es-file.md) | acc-es-server |
| 综合管理后台 | [admin-web.md](admin-web.md) | web-server, web |

## 全局事实（与 AGENTS.md 一致，勿重复推断）

- 服务间调用统一走 `rpc` 模块的 `ProxyWebClient`，地址取 `service.*.url`，无 Feign / Dubbo / 注册中心。
- **项目不使用 Redis 与消息队列**。异步补偿统一为「落库状态 + `@Scheduled` 扫表重试」；并发控制统一为「唯一索引 + `DuplicateKeyException` 兜底 + `MERGE INTO` + 状态终态短路」。`resource/micro/rabbitmq-adaptor` 是历史遗留构件，业务模块中未引用，**NEVER** 新增引用。
- 多数模块用 String 字面量而非枚举表达状态，改动状态值时 **MUST** 全局 grep 确认调用方。
- 部署与配置相关（生产环境清单、敏感配置外置方案）见 [`../ops/README.md`](../ops/README.md)。
- 架构总览（真实端口、调用拓扑、部署链路）见 [`../architecture/overview.md`](../architecture/overview.md)；公共构件见 [`../architecture/common-components.md`](../architecture/common-components.md)。

## 尚未落地的需求（`docs/` 有规格、代码无实现）

- **数字人民币硬钱包（IF1A-05 ~ IF1A-12）**：仅 collect-pay-server 的支付方式枚举里有 e-CNY 字样，无业务实现、无表。
- **ACC 对账文件（IF8A 逻辑卡号 / 对账文件）**：无对账代码，`acc-es-server` 的 FTP 只做编码设备任务与报告文件。
- **技术规范第 4 部分 0x2001~0x9004 系统级报文**：无实现。仓库中唯一的二进制报文服务是 acc-es-server Netty 的 `7000~7005`。
- **IF5A HCE 相关**：仅有 `requestCardDataAnalyse` / `requestUpdateCardData` / `notiUpdateHceData` 三个入口，票卡分析链路不完整。

接到上述需求时 **MUST** 先向用户确认是否为新建功能，**NEVER** 假设已有代码可复用。
