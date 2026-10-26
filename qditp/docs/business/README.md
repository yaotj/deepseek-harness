# docs/business — 业务域提示词索引

本目录是 **AI 编码提示词**，不是需求文档。每个文件描述一个业务域的**代码实际落地情况**：涉及模块、真实接口、核心类、数据表、状态取值、编码约束与已知坑。

## ⚠️ 接口清单是主线摘录，不是全集

各业务域 `*.md` 中的接口清单**只覆盖主线链路**，不等于模块的全部对外接口。实测（2026-08-25）：**代码侧共 420 条唯一路径**（含双别名 454 条），Apifox 项目内仅 105 条，本目录提示词记录的更少。

因此：
- 判断「某接口是否存在」**MUST** 直接看 Controller 上的 `@RequestMapping` / `@PostMapping` 注解，**NEVER** 以提示词或 Apifox 未收录为依据断言不存在。
- 提示词里没写的接口不代表不该用；发现遗漏 **MUST** 补进对应 `*.md`。
- 同一路径可能在多模块重复实现，定位实现 **MUST** 以「模块 + URL」为准。

## AI 执行约束

> ⚠️ 涉及业务逻辑时，**NEVER** 仅凭 `docs/business/` 外的简要描述下结论或改动。**MUST** 按以下顺序执行：
> 1. 读取 `docs/business/<业务域>.md` 业务提示词；
> 2. 读取提示词中「参考原始文档」列出的需求 / 接口规范原文；
> 3. 读取提示词点名的核心类源码，确认状态机、关键字段、幂等设计与异常码；
> 4. 再动手处理。

## 业务域清单

| 业务域 | 提示词 | 主要模块 |
|--------|--------|----------|
| **尚未落地的需求**（docs 有规格、代码无实现） | [未落地需求.md](未落地需求.md) | 数字人民币硬钱包 IF1A-06~12、`0x2001~0x9004` 系统级报文、IF5A HCE 补全、IF8B §3.64、超时处理。**接这类需求 MUST 先读它，NEVER 声称可复用现有实现** |
| APP 统一接入层 | [app-gateway.md](app-gateway.md) | fep-app-server |
| **用户分流透明网关（SCG）** | [route-sentinel.md](route-sentinel.md) | `route-sentinel`（上级目录独立项目）：设备域 + APP 订单域按 `thirdUserId` 分流到旧 ITP / 新系统 |
| 账户开户 / 支付通道 / 电子员工卡 | [account-employee-card.md](account-employee-card.md) | account-server, fep-acc-server |
| 乘车码与过闸票卡状态 | [ride-code.md](ride-code.md) | ticket-server, fep-dev-server, industry-data-server |
| APP 交易查询（IF8A-05 / 34 / 41） | [ride-code.md](ride-code.md) | **trans-query-server (9113)** — 独立服务，三条 URL 与 `ticket-server` 的 `TicketTransController` **逐字一致、并存未替换已接线并在承接真实流量**（`fep-alipay` 的 `/channel/findTravelList` → `trans-query-*-svc:30035`），`fep-app-server` 的 `service.ticket.url` 仍指向 ticket-server（APP 域那三条尚未切过来） |
| 闸机出站扣费（后付费） | [gate-txn-pay.md](gate-txn-pay.md) | gate-txn-pay-server |
| 支付签约 / 解约 / 免密扣款 | [pay-sign.md](pay-sign.md) | pay-sign-server |
| 支付宝渠道（碰一下 / 出行） | [alipay-channel.md](alipay-channel.md) | fep-alipay-server, alipay-pay-sign-server, alipay-account-server |
| TVM / BOM 非现金购票、充值、退款 | [tvm-bom-pay.md](tvm-bom-pay.md) | **face-pay-server (58101)** = 本域现行主实现；**2026-09-16 17:31 起已承接设备域 + APP 域全部入向流量**（`fep-app-vr` 的 `/itptvm/`、`/itpbom/` 指向 `face-pay-server-svc:30025`，`fep-app` 的 env `service.collectPay.url` 已改指 30025，旧表数据已全量迁入 `F2F_*`，见 ADR-D116 / D117）。**NEVER 回退成「VS 仍指向 collect-pay / 设备流量还在旧应用」**——那是 ADR-D112 的当日口径、已被 D117 取代。判断流量归属 **MUST 现查 VS + `fep-app` env，NEVER 引用本行** |
| APP 扫码取票（单程票） | [app-ticket-collect.md](app-ticket-collect.md) | collect-ticket-server |
| 日票 / 多日计次票 | [daily-ticket.md](daily-ticket.md) | daily-ticket-server |
| 逻辑卡号池（批次申请 / ACC 文件导入 / 卡号预占） | [card-pool.md](card-pool.md) | card-pool-server |
| 日终对账（分片抽取 / 流式聚合 / FTP 投递） | [recon.md](recon.md) | recon-server + **三个源服务**：gate-txn-pay-server, daily-ticket-server, **face-pay-server**（collect-pay 旧表源 2026-10-13 随该模块下线移除；期望清单 MUST 现查 `recon-server` 的 `recon.orchestration.sources[*]`）。ticket-server 的对账代码保留、**不在期望清单** |
| 琴岛通对账（四方对账工作簿，**未立项**） | [recon-qdt.md](recon-qdt.md) | **代码零实现**。琴岛通《对账.xls》四表（消费 / 充值 / 互联互通 / 互联网支付）对齐评估：表 4 大部分可对齐、表 2 部分、表 1/3 当前不可对齐；阻塞在产品口径与琴岛通反馈通道。关联 IT-013 |
| 公共能力（黑名单 / 密钥 / 参数） | [common-services.md](common-services.md) | blacklist-server, key-server, para-server |
| 安全服务与加密机 | [security-hsm.md](security-hsm.md) | acc-security-server, acc-secure-server |
| ACC 编码设备与文件传输 | [acc-es-file.md](acc-es-file.md) | acc-es-server |
| 码值字典（全仓盘点 + 排查速查）| [dictionary-inventory.md](dictionary-inventory.md) / [dictionary-detail.md](dictionary-detail.md) | 跨模块（model / pay-sign / collect-pay / gate-txn / face-pay / account / recon / acc-es 等）——inventory 是索引（散点分布+痛点），detail 是全量码值↔中文↔落库列速查手册；**码值全集以枚举类 / SQL 为准，本文件是镜像，改码值后 MUST 同步** |
| 第三方渠道对账（TXNDIFF，与 ACC 对账并列）| [recon-thirdparty.md](recon-thirdparty.md) | recon-server 消费面；源在 gate-txn-pay / daily-ticket / face-pay 三类第三方账单 |
| 综合管理后台 | [admin-web.md](admin-web.md) | web-server, web |
| **超时处理（超时费 / 超时扣次 / 超时补收）** | [overtime-handling.md](overtime-handling.md) ⚠️ **本篇是待实现契约、不是现状** | ticket-server, daily-ticket-server, gate-txn-pay-server, para-server, pay-sign-server |

## 全局事实

> 本节只留**跨域元规则**与**排障入口判据**。凡「哪个域的事实」一律去承接文档读，NEVER 在本文件复述：

| 事实簇 | 唯一权威文档 |
|---|---|
| 码值/状态取值速查 | [dictionary-detail.md §十「排查速查索引」](dictionary-detail.md#十、排查速查索引-按载体列反查)（全量码值↔中文，wiki 内可直读） |
| 码值/状态取值速查 | [dictionary-detail.md §十「排查速查索引」](dictionary-detail.md#十、排查速查索引-按载体列反查)（全量码值↔中文，wiki 内可直读） |
| `sys_job` 清单 / cron / 任务编号、`gate-txn-pay` 7 个内部端点、模块内 `@Scheduled` 现状 | [`docs/architecture/web-server.md`](docs/architecture/web-server.md) §七（含 §7.0 端点表、§7.0.1 `@Scheduled` 实测表） |
| tracing 开关「三行成组」、已打开的模块名单、「MUST 同时查 Deployment env」判据 | [`docs/architecture/common-components.md`](docs/architecture/common-components.md) §3.1.2 |
| 用户报 bug 时如何用 traceId / VictoriaLogs 排查入口服务链路、管理范围边界、常见无 traceId 原因 | [`docs/ops/入口服务链路排查规范.md`](docs/ops/入口服务链路排查规范.md) |
| 开户数量由 `ticketLimit` 控制、唯一卡查重条件、`ISSUE_ORG_CODE` vs `CARD_ISSUE_CODE` | [`docs/business/account-employee-card.md`](docs/business/account-employee-card.md) §IF8A-01 当前开户规则（ADR-D122） |
| 黑名单自动解除（`sys_job` 230）三条判据与 `skipped` 覆盖缺口 | [`docs/business/common-services.md`](docs/business/common-services.md) |
| 用户主动重试扣费、`GATE_RETRY_QUEUE`、`sys_job` 400 | `docs/domain/decisions.md` ADR-D169；队列消费细节见 web-server.md §7.0 |

**版本号 / 端口 / 任务编号 / 流量指向 MUST 现查**（`docs/提示词/工程与文档规范.md` §五），NEVER 引用上表文档里的数字当现状。

- 服务间调用统一走 `rpc` 模块的 `ProxyWebClient`，地址取 `service.*.url`，无 Feign / Dubbo / 注册中心。
- **项目不使用 Redis 与消息队列**。并发控制统一为「唯一索引 + `DuplicateKeyException` 兜底 + `MERGE INTO` + 状态终态短路」；异步补偿在形态上仍是「落库状态 + 扫表重试」，但**调度落点多数已不在模块内**：代码里带模块内 `@Scheduled` 的业务模块只剩 **`face-pay-server`（7 个、分布在 6 个类，在跑）与 `collect-pay-server`（`SingleTicketRefundTask` 4 处，但该模块 2026-10-13 已下线 0 pod、这 4 个都不在跑）**，`gate-txn-pay-server` 与 `recon-server` 代码里没有业务 `@Scheduled`（recon 那 1 个第三方对账默认关闭），`pay-sign-server` 也一个都没有（原有任务已迁到 web-admin `sys_job`，入口是各自的 `/internal/**` 端点）。 **排查「某个补偿有没有跑」MUST 查 `SYS_JOB_LOG` + 集群 Deployment 实况（是否 0 副本），NEVER 只在模块里找 `@Scheduled`、也 NEVER 只凭仓库有代码就当线上在跑**，完整清单见 `docs/architecture/web-server.md` §7.0.1。`resource/micro/rabbitmq-adaptor` 是历史遗留构件，业务模块中未引用，**NEVER** 新增引用。
- 多数模块用 String 字面量而非枚举表达状态，改动状态值时 **MUST** 全局 grep 确认调用方。
- 部署与配置相关（生产环境清单）见 [`../ops/README.md`](../ops/README.md)。
- 架构总览（真实端口、调用拓扑、部署链路）见 [`../architecture/overview.md`](../architecture/overview.md)；公共构件见 [`../architecture/common-components.md`](../architecture/common-components.md)。

## 尚未落地的需求（`docs/` 有规格、代码无实现）

详见 [未落地需求.md](未落地需求.md)（数字人民币硬钱包 IF1A-06~12 / 0x2001~0x9004 系统级报文 / IF5A HCE 补全 / IF8B §3.64 / 超时处理 / ACC 对账文件 / 琴岛通对账）。**接这类需求 MUST 先读它，NEVER 声称可复用现有实现**。

接到上述需求时 **MUST** 先向用户确认是否为新建功能，**NEVER** 假设已有代码可复用。
