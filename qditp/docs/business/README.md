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
| APP 交易查询（IF8A-05 / 34 / 41） | [ride-code.md](ride-code.md) | **trans-query-server (9113)** — 独立服务，三条 URL 与 `ticket-server` 的 `TicketTransController` **逐字一致、并存未替换**；**已接线并在承接真实流量**（`fep-alipay` 的 `/channel/findTravelList` → `trans-query-*-svc:30035`），`fep-app-server` 的 `service.ticket.url` 仍指向 ticket-server（APP 域那三条尚未切过来） |
| 闸机出站扣费（后付费） | [gate-txn-pay.md](gate-txn-pay.md) | gate-txn-pay-server |
| 支付签约 / 解约 / 免密扣款 | [pay-sign.md](pay-sign.md) | pay-sign-server |
| 支付宝渠道（碰一下 / 出行） | [alipay-channel.md](alipay-channel.md) | fep-alipay-server, alipay-pay-sign-server, alipay-account-server |
| TVM / BOM 非现金购票、充值、退款 | [tvm-bom-pay.md](tvm-bom-pay.md) | **face-pay-server (58101)** = 本域现行主实现；**2026-09-16 17:31 起已承接设备域 + APP 域全部入向流量**（`fep-app-vr` 的 `/itptvm/`、`/itpbom/` 指向 `face-pay-server-svc:30025`，`fep-app` 的 env `service.collectPay.url` 已改指 30025，旧表数据已全量迁入 `F2F_*`，见 ADR-D116 / D117）。**NEVER 回退成「VS 仍指向 collect-pay / 设备流量还在旧应用」**——那是 ADR-D112 的当日口径、已被 D117 取代。判断流量归属 **MUST 现查 VS + `fep-app` env，NEVER 引用本行** |
| APP 扫码取票（单程票） | [app-ticket-collect.md](app-ticket-collect.md) | collect-ticket-server |
| 日票 / 多日计次票 | [daily-ticket.md](daily-ticket.md) | daily-ticket-server |
| 逻辑卡号池（批次申请 / ACC 文件导入 / 卡号预占） | [card-pool.md](card-pool.md) | card-pool-server |
| 日终对账（分片抽取 / 流式聚合 / FTP 投递） | [recon.md](recon.md) | recon-server + **四个源服务**：gate-txn-pay-server, collect-pay-server, daily-ticket-server, **face-pay-server**（2026-09-16 新增的第 4 个源，与 collect-pay 是「新表 / 旧表」并列关系、**NEVER 二选一**；**本处此前写「三个源」已过期、NEVER 回退**，期望清单 MUST 看 `recon-server` 的 `recon.orchestration.sources[*]`）。ticket-server 的对账代码保留、**不在期望清单** |
| 公共能力（黑名单 / 密钥 / 参数） | [common-services.md](common-services.md) | blacklist-server, key-server, para-server |
| 安全服务与加密机 | [security-hsm.md](security-hsm.md) | acc-security-server, acc-secure-server |
| ACC 编码设备与文件传输 | [acc-es-file.md](acc-es-file.md) | acc-es-server |
| 综合管理后台 | [admin-web.md](admin-web.md) | web-server, web |
| **超时处理（超时费 / 超时扣次 / 超时补收）** | [overtime-handling.md](overtime-handling.md) ⚠️ **本篇是待实现契约、不是现状** | ticket-server, daily-ticket-server, gate-txn-pay-server, para-server, pay-sign-server |

## 全局事实（与 AGENTS.md 一致，勿重复推断）

- 服务间调用统一走 `rpc` 模块的 `ProxyWebClient`，地址取 `service.*.url`，无 Feign / Dubbo / 注册中心。
- **项目不使用 Redis 与消息队列**。并发控制统一为「唯一索引 + `DuplicateKeyException` 兜底 + `MERGE INTO` + 状态终态短路」；异步补偿在形态上仍是「落库状态 + 扫表重试」，但**调度落点多数已不在模块内**：模块内 `@Scheduled` 在跑的只剩 **`face-pay-server`（7 个、分布在 6 个类）与 `collect-pay-server`（`SingleTicketRefundTask`，4 处）**；**`gate-txn-pay-server` 与 `recon-server` 现在一个都没有**（原有任务已迁到 web-admin `sys_job`，入口是各自的 `/internal/**` 端点），`pay-sign-server` 也一个都没有（其 10 个 `/internal/**` 补偿端点与日终对账全部由 web-admin `sys_job` 触发）。**本条此前把 `gate-txn-pay-server` 列进「在跑的」是错的，NEVER 回退。** **排查「某个补偿有没有跑」MUST 查 `SYS_JOB_LOG`，NEVER 只在模块里找 `@Scheduled`**，完整清单见 `AGENTS.md` §2.2.1。`resource/micro/rabbitmq-adaptor` 是历史遗留构件，业务模块中未引用，**NEVER** 新增引用。
- 多数模块用 String 字面量而非枚举表达状态，改动状态值时 **MUST** 全局 grep 确认调用方。
- 部署与配置相关（生产环境清单）见 [`../ops/README.md`](../ops/README.md)。
- 架构总览（真实端口、调用拓扑、部署链路）见 [`../architecture/overview.md`](../architecture/overview.md)；公共构件见 [`../architecture/common-components.md`](../architecture/common-components.md)。

## 尚未落地的需求（`docs/` 有规格、代码无实现）

- **数字人民币硬钱包（IF1A-06 ~ IF1A-12，共 7 个；规格原文里没有 IF1A-05）**：仅 collect-pay-server 的支付方式枚举里有 e-CNY 字样，无业务实现、无表。已实现的 IF1A 只有 01/02/03/04（闸机检票、密钥同步、设备心跳、票卡状态查询），与硬钱包无关。这 7 个**不在同一个模块**，按 URL 前缀分两组，**估算工作量或设计扩展点前 MUST 先按这条分组**：
  - **只有 2 个进 `fep-dev-server`**（AGM 前缀 `/itpagm/ci/agm/**`）：IF1A-06 `requestEncyWalletStatus`（钱包状态获取）、IF1A-07 `ecnyWalletTranUpload`（行程上传）。
  - **其余 5 个是 BOM 前缀 `/itpbom/ci/bom/**`，真实归属 `face-pay-server`**（`controller/ci/bom/BomOrderController.java:41`）：IF1A-08 `requestEcnyArrears`、IF1A-09 `requestEcnyNoCashOrder`、IF1A-10 `requestPayment`、IF1A-11 `ecnyCashNotice`、IF1A-12 `requestEcnyRetry`。**NEVER 因为编号是 IF1A 就认为它们进闸机前置。**
  - **IF1A-10 的 URL `requestPayment` 与已在跑的 IF8A-05 完全撞名**（同模块同前缀）。照规格直接实现会**抢占一个在跑的端点**，**MUST 先向甲方澄清**是复用还是另起 URL。
  - 另有 IF2A-01 / IF2A-09 需加 `payType`（`0` 其他 / `1` 数字人民币 app），落 TVM 域，不在上面两组内。
  - 规格原文 `docs/接口规范文档/数字人民币钱包无电无网乘车接口-V2.docx` 自身版本号不自洽（封面 V1.9、变更记录只到 1.8、文件名 V2），引用条款前 **MUST** 确认手上是最新版。详见 ADR-D75。
- **ACC 对账文件**：`ITP.EXP` / `ITP.PAY` / `ITP.BUS` / `ITP.DETAIL` 四类文件的抽取、聚合与 FTP 投递链路**已落地**（recon-server + gate-txn-pay / collect-pay / daily-ticket / **face-pay** **四个源服务**，见 [recon.md](recon.md)），**行格式已按甲方规格原文 `docs/接口规范文档/ACC与ITP之间的文件.docx` §一 落地，2026-09-11 完成返工**（**「甲方规格缺失、格式是自定义 v1」是已被纠正的错误结论，NEVER 再这样写**）。未闭合项：**端到端已于 2026-09-11 在测试环境实跑通过**（recon-server 1.0.10 + web-admin 1.1.17：`sys_job` **225「给ACC上传扣费交易」**（2026-09-21 由 109「日终对账」改号改名）→ `POST /internal/recon/daily/run` → 批次 `RECON20260909` 收口 `SUCCESS`，`RECON_BATCH_FILE` 四行全 `UPLOADED`），**但仍无单元测试**；四张 `RECON_*` 表与 `IDX_DAILY_TICKET_ORDER_RECON` **已在唯一目标库 `AFCITPDB` 建好并回查验证**（`IDX_TRAVEL_TICKET_ORDER_RECON` 已废弃、不再需要）——**本条此前写「测试库已建、生产库仍未执行」是把同一个库当成了两个，NEVER 回退，见 AGENTS.md §8「唯一目标库」**；共享存储（ReadWriteMany PVC）与单副本约束未落地；`X-Recon-Token` 鉴权已按 2026-09-11 要求整段删除，属**有意为之的临时降级，上线前 MUST 恢复**；甲方文档自身多处歧义待澄清、数据侧多处字段恒 0（**旅游票按子单 `DAILY_TICKET_ORDER` 统计已落地；主单 `TRAVEL_TICKET_ORDER` 是聚合壳、`PAY_STATUS` 永不回写，属当前设计非缺陷 —— 本条此前把它列为 P0 已作废，NEVER 回退**）。**逻辑卡号文件对账仍无实现**；`acc-es-server` 的 FTP 只做编码设备任务与报告文件。
- **技术规范第 4 部分 0x2001~0x9004 系统级报文**：无实现。仓库中唯一的二进制报文服务是 acc-es-server Netty 的 `7000~7005`。
- **IF5A HCE 相关**：仅有 `requestCardDataAnalyse` / `requestUpdateCardData` / `notiUpdateHceData` 三个入口，票卡分析链路不完整。

接到上述需求时 **MUST** 先向用户确认是否为新建功能，**NEVER** 假设已有代码可复用。
