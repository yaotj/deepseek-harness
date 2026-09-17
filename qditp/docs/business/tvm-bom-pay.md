---
业务域: TVM / BOM 非现金购票、充值、退款
模块: face-pay-server（本域现行主实现；**流量是否已切 MUST 现查 VS，2026-09-16 实测仍指向 collect-pay，见下文 ⚠️ 现状块与 ADR-D112**）; collect-pay-server（仍在收真实设备流量 + 内部端点 + 旧单退款）
---

# 提示词：TVM / BOM 非现金支付

## 何时读本文件
TVM 单程票下单出票、TVM 充值、BOM 非现金收款、当面付订单与退款、单程票退款、支付中心（bestonepay）对接、APP 取票下单通知重试相关改动。

## 模块定位

**设备（TVM / BOM）与 APP 的当面付流量已于 2026-09-15 全部切到 `face-pay-server`**（端口 58101，Service `face-pay-server-svc:30025`，`F2F_*` 表）。`collect-pay-server`（端口 58101，`spring.application.name=itpagm`，`application.yml` 而非 properties）**进程保留未停**，只承担三类残留职责：

- `/internal/recon/export` —— 日终对账源（`recon-server` 的 `sources[1]` 直连 `collect-pay-c23ku-svc:30024`）
- `/internal/app-order/{register,close-unpaid,pay-result}` —— gate-txn-pay 补款链路（`SupplementCollectPayGateway`）
- 旧单退款入口 `/itptvm/ci/tvm/requestRefund` 与运营后台 `/{orderNo}/refund`，外加 `NoticeAppTask` 那 4 个由 web-admin Quartz 驱动的旧表通知端点

⚠️ **安全**：`collect-pay-server/src/main/resources/application.yml` 中明文内置支付中心商户私钥。触碰该文件 **MUST** 提示人工复核安全合规，**NEVER** 把私钥值输出到对话或日志。

## ⚠️ 切换已完成（2026-09-15），切换机制不是改 Service selector

`face-pay-server` 是本域的**新模块重写版**：全新表（`F2F_*`）、全新服务，**不动旧表**。设备侧 URL、响应键名、四套 retCode 族与中文提示语**全部保持不变**。

**真实切换点是两处路由配置，NEVER 再按「改 K8s Service selector」执行**（本文件与 `face-pay-refactor.md` §22.10 此前都这么写，已作废——`collect-pay-c23ku-svc` 的 targetPort 是 8080、face-pay 听 58101，翻 selector 等于全量打到无人监听的端口）。**操作步骤、现成 patch 命令（带 `test` op）、三重验证与回滚命令见 [`../ops/流量切换.md`](../ops/流量切换.md) —— 切之前 MUST 先读那份 Runbook，本节只说机制**：

- **设备域**：`kubectl edit vs fep-app-vr -n itp`，`/itptvm/` 与 `/itpbom/` 两条 route 的 destination 从 `collect-pay-c23ku-svc:30024` 改为 `face-pay-server-svc:30025`。公网入口 `58.56.166.170:48000` → 节点 `172.20.211.200`（`k8s02-gateway-866a2`）→ `itp-gateway`（ns `itp-gateway`，istio 网关）→ 该 VirtualService。
- **APP 域**：`fep-app` Deployment 的 env **`service.collectPay.url`**（键名带点、不是 `SERVICE_COLLECT_PAY_URL`）改为 `http://face-pay-server-svc.itp.svc:30025`。

**这个切法让三条内部链路零影响**：recon / gate-txn-pay / web-admin 都是**直连** `collect-pay-c23ku-svc:30024`、不经网关，所以对账、补款、旧表通知任务都还在旧服务上跑，不需要先把 `/internal/**` 迁到新模块。

> ⚠️ **现状（2026-09-16 实测，ADR-D112）：设备域流量当时并不在 face-pay 上。** `kubectl get vs fep-app-vr -n itp` 读回显示 `/itptvm/` 与 `/itpbom/` 两条 route 的 `destination.host` **都还是 `collect-pay-c23ku-svc:30024`**，旧应用日志里能看到当天 15:40 的真实设备交易（下单 → 支付 → `payNotice` → 出票上报，全部经 envoy 进来）。也就是说 **AGENTS.md §2.2 与本文件开头「已于 2026-09-15 切至 face-pay（ADR-D85）」与集群实况不符** —— 可能是切过又切回、也可能那次只改了 APP 域。**判断「现在流量在哪个应用」MUST 每次现查 VS，NEVER 引用任何文档里的那句话**（含本段）。附带的好处：旧应用日志中的 `BodyCacheFilter` 行**请求参数与响应体成对**，是做新旧契约基线核对最好用的回放语料，取法与对比矩阵见 ADR-D112。

- 下面的接口清单对**两个模块都成立**（同一批 URL），改接口 **MUST 同时评估两边**；新逻辑只落 `face-pay-server`，`collect-pay-server` 只做上面三类残留职责的维护。
- 新模块的表结构、状态机、幂等索引、已修掉的旧缺陷清单与未完成项，见 **`docs/architecture/face-pay-refactor.md`**（§二十一 是按 URL 的进度台账）。
- **旧库只读旁路已于 2026-09-13 按用户裁决整体删除，本条此前写「已落地」已作废、NEVER 回退**（原话「后续要停掉旧服务，删除旧库的，不需要做兼容层」，判据见 `F2fTvmOrderService.loadOrderForQuery` 的 Javadoc；2026-09-16 复核：`facepay/` 下**没有 `legacy` 包**、`src/main/resources` 里**搜不到 `f2f.legacy.enabled`**）。连带后果**已实测**（ADR-D112 续）：拿旧单的真实语料打新服务，`requestOrderResult` 返 `8999 没有查找到出票信息`，而旧应用同一份报文返 `0000` + 完整交易数据；`requestPayResult` 同理返 `2002`。**这是有意的，NEVER 因为「历史单查不到」把旁路加回来**，但**切流量时 MUST 与业务确认「切流前的历史单在设备侧查不到」可接受**。另注意 face-pay Deployment 上还留着 **`F2F_LEGACY_READ_ENABLED=true` 这个已无读取方的死 env**（同 `RECON_INTERNAL_TOKEN` 那类残留），**NEVER 据它判断旁路还在**。
- **切换后仍未闭合**：**支付主链路（拉码 → 真实扫码付 → `payNotice` → 出票上报）与退款链路已于 2026-09-15 在新服务上真实跑通**（两笔真实出款各 1 分，见 ADR-D88；本行此前写「下单 / 支付 / 退款零笔」已作废、**NEVER 回退**）。设备心跳（`notiDeviceHeard`，落 `F2F_DEVICE_STATUS`）一直有量；**`F2F_*` 各表行数 MUST 现查、NEVER 引用具体数字** —— 本行初稿写「4 台设备」，同日复核已是 7 行，而 **2026-09-15 15:03:41 起七张 `F2F_*` 表被清空（TRUNCATE 形态，非本次改动所为），`F2F_ORDER` 现为 0 行**；**NEVER 因为表是空的就判定链路没跑过**。§十二 场景 4 比对、旧表 `TBL_TICKET_REFUND_RECORD` 补唯一索引、双写影子比对仍未做。
- **四套 retCode 族是既有契约，NEVER 统一**：TVM `0000/2999/2001-2008`、TVM-`requestRefund` `9999`、BOM `0000/8999/8001-8007`、APP `0000/8001/8003/8999/9999`。
- **字段名里的既有拼写错误是契约的一部分，NEVER 修正**：`singelTicketNum`、`takeTickeDate`、`transAount`、`tranDate`、`lastTransAmout`、`lastTikcetTransSeq`、`operaterId`。

## 接口清单

**TVM** `controller/ci/tvm/TvmOrderController.java`（前缀 `/itptvm/ci/tvm`）
- `notiDeviceHeard`、IF2A-01 `requestGenSjtOrder`、IF2A-11 `requestPayment`、IF2A-03 `requestPayResult`
- IF2A-04 `notiTakeTicketResult`、IF2A-05 `notiTakeTicketFailResult`、`requestRefund`
- IF8A-15 `requestActiveTicket`、IF2A-08 `requestTakeTicketAuth`
- IF2A-09 `requestTopup`、IF2A-06 `topupCardResultNoti`、IF2A-07 `topupCardFailNoti`
- `requestPayOrderDetail`、`payNotice`

**BOM** `controller/ci/bom/BomOrderController.java`（前缀 `/itpbom/ci/bom`）
- `notiDeviceHeard`、IF8A-04 `requestGenNoCashOrder`、IF8A-05 `requestPayment`、IF8A-06 `requestGetPayResult`
- IF2A-08 `notiBusResult`、IF2A-09 `notiTopupResult`
- IF5A-01 `requestCardDataAnalyse`、IF5A-03 `requestUpdateCardData`、IF5A-09 `notiUpdateHceData`
- `requestOrderResult`、`requestTicketRefund`
- **出票上报别名（face-pay 1.0.42 起，只在新模块有）**：IF2A-05 `notiTakeTicketResult`、IF2A-06 `notiTakeTicketFailResult` —— 与 TVM 前缀那两条是**同一个 `F2fTicketIssueService`、同一个 2xxx 响应族、同一张 `F2F_RESULT_REPORT`**（幂等仍靠 `UK_F2F_REPORT_IDEM`，同一笔打两个前缀也只落一行），差别只有渠道兜底值：本前缀兜底 `BOM`、TVM 那条兜底 `TVM`。加它的原因是现场设备把出票上报打到了 BOM 前缀，而该 URL 在 face-pay 与旧 collect-pay 里**都不存在**，落到静态资源解析后被全局异常处理器兜成 **HTTP 200 + UUID `retCode`**、订单永久卡 `PAID`（2026-09-16 实测，见 ADR-D97）。**排查这类「上报返 UUID」MUST 先在日志里找 `No static resource <path>.`**
- **出票成功上报「订单不存在」的 retCode 按 `providerId` 分两个码：`03` → `2999`，其余（含缺失）→ `-1`**（2026-09-16 / 1.0.54 修回，ADR-D112）。旧实现是在 `TvmOrderController:163-176` 按 `providerId=="03"` 分流到 `BomOrderServiceImpl:1137` 才落 2999，新实现的 `channelOf()` 只折算渠道码、不再分流，于是 1.0.53 及之前**三种 providerId 一律 `-1`** —— 而现场设备发的正是 `providerId=03`，属**只在 BOM 那一支上踩的契约回归**。判据 **MUST 用 `providerId` 本身，NEVER 换成 `channelOf(...)` 的渠道码**（BOM 前缀那两条别名在 providerId 缺失时兜底 BOM，用渠道码判会把「BOM 前缀 + 无 providerId」也判成 2999，而旧实现那一支不存在、没有基线）。**兄弟接口 `notiTakeTicketFailResult` 两侧三种 providerId 本来就全是 `2999`，不需要分叉、NEVER 顺手改**

⚠️ BOM 的 IF8A-04/05/06 与 APP 域的 IF8A-04（自助补站）/IF8A-05（交易记录）/IF8A-06（解约）**编号冲突**，且 IF2A-08/09 在 TVM 与 BOM 下含义不同。定位接口 **MUST** 以「模块 + URL」为准，**NEVER** 只凭编号。

**APP 取票侧** `controller/ci/app/TvmAppOrderController.java`（前缀 `/ci/app`）
- IF8A-20 `requestOrder`、IF8A-11 `requestPaymentInfo`、IF8A-18 `requestPayResult`
- `requestPreActiveOrderList`、`requestRefundTicket`、`requestRefundTicketResult`、`receiveRefundResult`

**运营端** `controller/page/FacePayOrderPageController.java`（`/page/face-pay/orders`）：分页、`POST {orderNo}/refund` **退剩余可退金额**（`ORDER_AMOUNT - REFUND_AMOUNT`，不接受调用方指定金额；2026-09-15 / ADR-D88 起由「全额退款」改为此语义，可退为 0 即拒绝）

**运营端** `controller/page/AppOrderPageController.java`（`/page/app/orders`，2026-09-14 / 1.1.80 新增）：`POST {orderNo}/refund` 体 `{"refundAmount":<分>}`，**按指定金额**退 APP 取票订单，用于补退已部分退款的剩余额度。存在原因是 `/ci/app/requestRefundTicket` 取 `PAY_AMOUNT` **全额**、对部分退款单必被支付中心拒；**那条是 APP 对外契约（APP 只传 `orderNo`），NEVER 给它加金额字段**。服务层 `AppOrderServiceImpl.refundByAmount` 带**超退闸门**：可退余额 = `PAY_AMOUNT` − `AppRefundOrderMapper.sumSuccessRefundAmount`（只累计 `REFUND_STATUS='1'`），超额即拒且**不调支付中心、不落退款单**。⚠️ **无鉴权**（与本模块其它入口一致，但它能指定金额、更危险，**上线前 MUST 补验签或收进内网**）、**无幂等**（连调两次退两次）。2026-09-14 实测：可退 400 时申请 500 被拒、申请 400 成功。

**内部端点** `controller/internal/AppPayOrderInternalController.java`（前缀 `/internal/app-order`，2026-09-14 / 1.1.83 新增，唯一调用方是 `gate-txn-pay-server` 的补款单链路）
- `POST register` — 建补款用 APP 订单行 + 前置单（两张表一个事务），按 `ORDER_NO` 幂等重入
- `POST close-unpaid` — `PAY_STATUS` `'0'→'2'` 白名单关单，命中 0 行按成功处理（已是终态）
- `POST pay-result` — 回查支付结果，未建单返 `found=false` 而**不是**报错
⚠️ **三个端点无鉴权**（与 `/internal/recon` 同款临时降级，2026-09-14 用户裁决），**上线前 MUST 补**。三条资损口径全部落在 `AppPayOrderInternalServiceImpl` 里，改动 MUST 先读 ADR-D64。

**出向通知（face-pay-server，IF8B 族）** —— 载体是 `F2F_NOTIFY_TASK` + `F2fNotifyJob` 扫表投递，**本项目无 MQ**；地址来自 `f2f.notify.app.*`（env `NOTIFY_APP_*`），**判断线上实际值 MUST 查 Deployment env**
- IF8B-06 出票成功 / IF8B-07 出票故障 —— `NOTIFY_TYPE='TAKE_TICKET_OK'` / `'TAKE_TICKET_FAIL'`，生产者 `F2fTicketIssueService.enqueueAppNotify`，**只推 APP 来源单**（`TRANS_TYPE='03'`）
  - ⚠️ **IF8B-07 的 payload 首位带 `userId`（取 `THIRD_USER_ID`），IF8B-06 不带 —— 这个不对称是实测结论，NEVER 抹平**（2026-09-16 / 1.0.54，ADR-D112）。同一订单同一信封只改 `bizData`：`receiveTakeTicketFaultResult` 原 5 键 → `7004`，补 `userId` → `7005 当前数据已经在处理中`（已受理）；`receiveTakeTicketResult` 原 4 键不带 `userId` → **`0000 成功`**。**NEVER 顺手给 IF8B-06 也加 `userId`**（把已验证通过的形态推回未知），也 NEVER 把两个 payload 合并成一份
- IF8B-04 退款结果 —— `'REFUND_RESULT'`，生产者 `F2fAppOrderService`
- **IF8B-05 支付结果 —— `'PAY_RESULT'`，2026-09-15 / 1.0.36 新增（ADR-D89）**。生产者收口在 `F2fPayCenterFlow.enqueuePayResultNotify`，三个支付成功点全覆盖（`markPaidAndReport` 重载 / `F2fTvmOrderService` 过期收口发现已支付 / `receivePayNotice` 回调成功）。**与出票、退款那几条不同：本条不做 `TRANS_TYPE` 过滤、设备单也推**（用户裁决）。8 键报文与三处「待甲方澄清」见 ADR-D89
  - ⚠️ **这条通知线上 100% 未被受理**（2026-09-16 实测，ADR-D102）：`F2F_NOTIFY_TASK` 里 `PAY_RESULT` 全部 `GIVEUP`、`LAST_ERROR` 一律 `retCode=7004`。**成因在对端**：七组探针（信封 / 裸 JSON、`deviceId`/`sign` 四种组合、3 键 / 7 键 / 只带 `orderNo`）全返 7004，而同批打 `receiveTakeTicketResult` 返 7001；同一 `orderNo` 重发返 `7005 当前数据已经在处理中`（= 它解析到了订单号）。**NEVER 再去改这条的报文形态或补字段，MUST 找 APP 侧核对 `receivePaymentResult`**；**NEVER 把 7004 加进成功码**
  - ⚠️ **上一条的「100%」与「成因在对端」已被同日实测收窄，NEVER 再按原文照搬**（2026-09-16 / 1.0.54，ADR-D112）：`F2F_NOTIFY_TASK` 20 条里**有 1 条 `PAY_RESULT` 是 `SUCCESS`** —— 14:06:25 那条 `CHANNEL=01` 的 APP 单、payload 带 `userId=00522955`；18 条无 `userId` 的全 7004，另 1 条有 `userId` 但属设备单（`THIRD_USER_ID` 人工塞、`tradeNo` 假造）也 7004。因此**「对端处理异常」只对设备单成立，带真实 `userId` 的 APP 单已能通**。据此改动：**设备单（`THIRD_USER_ID` 为空）的重试上限压到 1 次**（`F2fNotifyService.enqueue` 的 `maxRetryTimes` 重载），**仍全推、NEVER 加 `TRANS_TYPE` 过滤**（ADR-D89 那条裁决不变）—— 默认 5 次只是把注定 7004 的报文推 5 遍、再刷一条 ERROR 级 GIVEUP 把真正要人工看的失败埋掉
- ⚠️ 这几条**都不带验签**，与 §5.2 冲突，上线前 MUST 补
- **投递彻底放弃（`GIVEUP`）现在打 ERROR**（1.0.47 / ADR-D104）：`F2fNotifyService.markFailure` 在 `retried + 1 >= maxRetry` 时改打 `ERROR`「通知投递已放弃（GIVEUP），需人工介入」。此前最后一次失败与前几次同为 `WARN`、毫无区分度。**NEVER 把 7004 加进成功码来消掉这些告警。**

**出票上报的后续动作补偿（`F2F_RESULT_REPORT.PROCESSED`，1.0.47 新增，ADR-D104）** —— `F2fReportRecoveryJob` + `F2fReportRecovery` + `F2fTicketIssueService.resumeFromReport`，face-pay 的第 7 个 `@Scheduled`（`f2f.report.*`，120s 一轮、静默期 300s）
- 补的是**此前完全没有的自愈路径**：接收链路同步做完「落票 → 推进订单 → 入队通知」就返回，**从不置 `PROCESSED='1'`**，进程在三步中途被杀那笔单就永久卡 `PAID`、APP 收不到通知、少出的票不退差额。2026-09-16 实测该列出票上报两类 **4 条全是 `'0'`、一条 `'1'` 都没有**
- 只重放 `TAKE_TICKET_OK` / `TAKE_TICKET_FAIL` 两类；三步各自幂等（订单 CAS 白名单只认 `PAID`、退款按 `UK_F2F_REFUND_IDEM`、通知按 `UK_F2F_NOTIFY_IDEM`），因此重放**不会二次出款**
- **`f2f.report.staleSeconds` NEVER 调到小于设备上报请求的最长耗时**（链路里含支付中心退款调用），否则补偿会与首报并发重放同一行、在日志里制造两份看似矛盾的退款记录

**已废弃**：`controller/ci/app/CollectPayController.java` 整类被注释（IF8A-09/10/12/13 未生效）。**NEVER** 在此类中新增代码，如需恢复 **MUST** 先与用户确认。

**`face-pay-server` 侧的同名对应物**（URL 完全相同，包名 `com.chinasofti.huateng.facepay.controller`）：`ci/tvm/TvmOrderController`（14 条）、`ci/bom/BomOrderController`（11 条）、`ci/app/AppOrderController`（7 条）、`page/FacePayOrderPageController`（2 条）。新模块**没有**被注释的死代码。

⚠️ **`face-pay-server` 的 IF8A-11 `/ci/app/requestPaymentInfo` 按订单号前缀分流两类单**（2026-09-16 / 1.0.43，ADR-D100）：`SP` 前缀走 `SupplementOrderServiceImpl.requestPayInfo`（读 `SUPPLEMENT_ORDER`，`PROCESSING` 回放已有 `PAYMENT_INFO`、`INIT` 补一次支付中心预下单），其余走 `F2fAppOrderService.requestPayInfo`（读 `F2F_ORDER`）。**分流常量只有一处** `SupplementOrderService.ORDER_NO_PREFIX`，落单侧 `generateOrderNo` 用的是同一个，**NEVER 各写一份字面量**。这条分支不是设计洁癖：补款单（IF8A-26）落在 `SUPPLEMENT_ORDER`、取票单落在 `F2F_ORDER`，缺分流时补款单在这里恒返 `9999 订单号错误`、**APP 侧显示「生成订单失败」**（2026-09-16 实测：10:52:18 建单返 `0000`、20ms 后取支付信息即失败，当天 7 笔全踩）。**NEVER 让补款分支在 `PROCESSING` 时按 APP 传入的通道重新预下单** —— 支付中心已挂待支付单，再下一次等于同一笔欠费有两份可付参数；通道不一致 MUST 拒绝并让乘客重新走 IF8A-26 建新单。

⚠️ **补款单的支付结果回调走自己的 `notifyUrl` 与自己的端点**（2026-09-16 / 1.0.46，ADR-D103）：`SupplementPayCenterFlow.preOrder` 取 `PayCenterProperties.effectiveSupplementNoticeUrl()`（配置键 `pay.center.supplement-notice-url` / env `PAY_CENTER_SUPPLEMENT_NOTICE_URL`，**留空即回落到通用 `pay-notice-url`**），调 `PayCenterMessageFactory.buildPayRequest(command, notifyUrl)` 这个**两参重载**；接收端是 `SupplementPayNoticeController`，**一个方法挂两条全路径** —— 原 `/ci/facePay/paycenter/payNotice` 与对外别名 **`/itpbom/ci/bom/supplementPayNotice`**（复用 `fep-app-vr` 已有的 `/itpbom/` 前缀、`rewrite.uri` 路径原样保留 ⇒ **不需要改网关**）。**为什么必须分开**：通用 `pay-notice-url` 线上指向 `/itptvm/ci/tvm/payNotice`，那里只查 `F2F_ORDER`，补款回调恒返 `2001 订单不存在`、被支付中心反复重推，状态只能靠 `converge()` 每 5 分钟兜（实测延迟约 3 分钟）。**改这条链路 MUST 注意两点**：`buildPayRequest` 两个重载的 bizData **键序与 `notifyUrl` 的位置必须逐字一致**（键序进待签串），**NEVER 挪位置**；`effectiveSupplementNoticeUrl()` **NEVER 改成直接返回 `supplementNoticeUrl`** —— env 未注入时会送空串，而网关对空串的行为没实测过。

## 核心 service
`TvmOrderServiceImpl`、`TvmTopupServiceImpl`、`TvmTakeTicketServiceImpl`、`TvmOrderPreServiceImpl`、`BomOrderServiceImpl`、`AppOrderServiceImpl`、`PayCenterServiceImpl`、`TvmCommonServiceImpl`、`CollectPayServiceImpl`

TVM 主链路（见 `service/TvmOrderService.java` 方法注释）：
IF2A-01 下单 → IF2A-11 扫码支付 → IF2A-03 查支付结果 → IF2A-04/05 出票结果/故障通知 → `requestRefund` → `payNotice` → `sendNoticeAppTakeTicketRecord` / `sendNoticeAppTakeTicketFailureRecord`
异步补偿：`doTime.noticeTakeTicketTask`、`doTime.noticeRefundTask` 定时扫描，`app.retryTimes=5`

## 数据表
`collect-pay-server/sql.txt` 只有 **20 张 `CREATE TABLE`**。下列表名在 mapper XML 中被引用但 **sql.txt 里没有 DDL**，改动前 **MUST** 确认库中实际结构：`TBL_TVM_Topup_Notiy`（`TvmTopupOrderMapper.xml`）、`TBL_BOM_TOPUP_RESULT`（`BomTopupResultMapper.xml`）、`TBL_TICKET_REFUND_RECORD`（`BomNoCashOrderMapper.xml`）。
- TVM：`TBL_TVM_ORDER_PAY`、`TBL_TVM_ORDER_PAY_PRE`、`TBL_TVM_ORDER_TOPUP`、`TBL_TVM_Topup_Notiy`、`TBL_TVM_ORDER_REFUND`、`TBL_TVM_TAKE_TICKET_ORDER`、`TBL_TVM_MAIN_TICKET`、`TBL_TVM_SUB_TICKET`
- APP：`TBL_TVM_APP_ORDER`、`TBL_APP_ORDER_REFUND`、`APP_PAY_LOGS`
  - ⚠️ **这两张表的唯一写入方就是本模块**（2026-09-14 收回 owner）。2026-09-11~14 期间 `gate-txn-pay-server` 曾用自带的 `AppPayOrderMapper` 跨域直插两张表，**现已删除**；补款单（IF8A-26）改由本模块新增的三个内部端点 `POST /internal/app-order/{register,close-unpaid,pay-result}`（`AppPayOrderInternalServiceImpl`）代写，仍借 IF8A-11 / `payNotice` / IF8A-18 收款。**NEVER 让任何其它模块再直写这两张表**；改动 `AppOrderServiceImpl` / `TvmOrderPreServiceImpl` 的分派 / `TvmAppOrderMapper.xml` 或这两张表结构前 **MUST** 先看 `docs/business/gate-txn-pay.md` §补款单的收款通道 与 ADR-D64。补款行的特征是 `TICKET_TYPE='SP'` / `RSV2='SP'`；三条资损口径（前置单 `TRANS_TYPE='03'` 必须一起插、`RSV2` MUST 非空、`ACTIVATE_FLAG='0'`）现在只在本模块，**NEVER 改动其中任何一条**。**`refundAppNotTakeTickets` 的「已支付 + 无票 + `RSV2 IS NULL` 即自动退款」规则 NEVER 放宽成不看 `RSV2`**，否则补款单的钱会被次日退掉、造成资损。
  - ⚠️ **`/internal/app-order/*` 三个端点当前无鉴权**（与 `/internal/recon` 同款临时降级，2026-09-14 用户裁决），与 AGENTS.md §5.2 冲突，**上线前 MUST 补**。
  - ⚠️ **`/itptvm/ci/tvm/payNotice` 没有任何验签**（`validateRequestOrder` 只判字段非空，`bizData` 是明文 JSON），任何网络可达方都能伪造支付成功回调把订单改成已支付。2026-09-11 端到端测试正是靠这一点模拟的回调。与 AGENTS.md §5.2「状态变更型接口 MUST 有鉴权」冲突，**上线前 MUST 补齐**。
- BOM：`TBL_BOM_ORDER_PAY`、`TBL_BOM_ORDER_REFUND`、`TBL_BOM_SALE_INFO`、`TBL_BOM_MAIN_TICKET`、`TBL_BOM_SUB_TICKET`、`TBL_BOM_BUS_RESULT`、`TBL_BOM_TOPUP_RESULT`、`TBL_BOM_TICKET_REFUND`
- 通用：`TBL_TICKET_REFUND_RECORD`
- 通知重试表：`tbl_notice_app_taketicket_record`、`tbl_notice_app_refund_record`、`TBL_NOTICE_APP_FAILURE_RECORD`（后者建表脚本 `scripts/create_tbl_notice_app_failure_record.sql`）
- 订单号来源 `OrderSeqMapper`（Oracle `ORDER_NO_SEQ.NEXTVAL`），**MUST** 沿用序列，**NEVER** 自造订单号规则

## 状态定义（无枚举类，散落常量 —— 本域最大技术债）
- `CollectPayServiceImpl`：`PAY_TYPE_PAY=0`、`PAY_TYPE_REFUND=1`、`PAY_RESULT_SUCCESS=100`、`PAY_RESULT_FAIL=1`、`DEFAULT_ORDER_TIMEOUT=60`
- 业务操作结果初始状态 `"0"`（已通知待处理），见 `BomOrderServiceImpl`
- 返回码：`model/response/app/AppOrderResult.java`（0000/9999）、`model/response/bom/BomOrderResult.java`（0000/8999）

改状态值 **MUST** 全局 grep 字面量；新增状态 **SHOULD** 就近抽枚举，但不得改变已落库的取值。

### face-pay 侧：退款与主状态**正交**（2026-09-15 / ADR-D88，改动前后语义完全不同）

`F2F_ORDER.ORDER_STATUS` **完全不参与退款**，退款事实由三列独立承载：
`REFUND_STATUS`（`NONE` / `PARTIAL` / `SUCCESS`，值域类 `domain/F2fOrderRefundStatus`）、
`REFUND_AMOUNT`（分）、`LAST_REFUND_TMS`。参考实现是 `PAY_TXN_DETAIL`。

- 唯一写入方 `F2fOrderMapper.updateRefundSummary(origOrderNo)`：**`SUM` 一遍 `F2F_REFUND` 里
  `REFUND_STATUS='SUCCESS'` 的行再整体覆盖三列**，重算而非累加 ⇒ 天然幂等。
  **NEVER 改成累加**（并发少算 / 重跑多算，多算后会变成假的超退拒绝、把单子永久挡死）。
- 主状态上**已经没有 `REFUNDING` 这条边**，六处 CAS 已删。**NEVER 加回** —— 必然 0 行 + 假告警。
  但 `STATUS_REFUNDING` 常量仍被 `PAID_LIKE` 与 `reportOnly` 守卫引用，**NEVER 顺手删常量**。
- **整单退款四道闸门，缺一不可**：①同来源查重 `UK_F2F_REFUND_IDEM`
  ②跨来源在途拦截 `countUnsettledRefunds`（`INIT` / `PROCESSING` / `MANUAL` **都算在途**）
  ③主状态在 `REFUNDABLE` 白名单 ④可退金额 `ORDER_AMOUNT - REFUND_AMOUNT > 0`。
  三个入口都已前置：运营端、TVM 设备、APP `requestRefund`。
- **对外契约的唯一影响**：TVM `requestPayOrderDetail` 的 `orderStatus=7` 判据由主状态改为
  `REFUND_STATUS`，且 **`PARTIAL` 与 `SUCCESS` 都答 7**（ADR-D106 回归旧 collect-pay 口径，
  旧判据是 `TBL_TVM_ORDER_PAY.RSV2` 非空、部分退与全额退都写那列）；实现是
  `F2fOrderRefundStatus.refunded(...)`，购票与充值两支共用。**NEVER 退回只认 `SUCCESS`**
  （ADR-D88 当时写的「且 `REFUND_STATUS=SUCCESS`」已被 D106 取代）；内部三档保持不变。
  `PAID_LIKE` **仍保留 `REFUNDING` / `REFUNDED`** 以兼容切换前的历史行，**NEVER 删**。
- 退款收口靠 `F2fRefundReconcileJob` 回查（`f2f.refund.scanIntervalMs` 60s +
  `f2f.refund.firstQueryDelaySeconds` 60s），**提交后约 2 分钟才落终态，测试时 NEVER 立刻断言失败**。

## 幂等
- 主键约束 `ORDER_NO` / `ID`
- 状态终态短路：支付/退款/通知重入均"先查库再决定"（`TvmOrderServiceImpl`、`TvmTopupServiceImpl`、`BomOrderServiceImpl`、`AppOrderServiceImpl` 多处）
- 通知重试靠上述三张防重表
- 无 Redis 锁。新增支付/退款写入 **MUST** 补状态短路判断

## 参考原始文档
- `docs/业务需求文档/非现金购票单程票退款功能3.docx`
- `docs/技术规范文档/城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.docx`（IF2A 域）
- `docs/接口规范文档/青岛地铁-ITP与APP接口规范R6.docx`（IF8A-11/15/18/20）

## 附：face-pay-server 源码注释知识抽取（2026-09-16，阶段一）

本节把 `face-pay-server` 源码注释里的知识按「契约与判据 / 决策理由 / 陷阱」三类搬运归档，墓碑类注释单列文末。本阶段只读代码、只写本文档，未改动任何 `.java` / `.xml` / `.yml` / `.properties`。

定位串约定（为控制行长，下文省略两个固定前缀）：
- Java 类前缀 = `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/`（启动类 `FacePayServer` 在 `face-pay-server/src/main/java/com/chinasofti/huateng/`）
- mapper XML 前缀 = `face-pay-server/src/main/resources/mapper/`

### 一、TVM 设备域

#### 1.1 契约与判据

- **响应体的每个 key 都是既有契约，NEVER 改名、NEVER 增删**；形态是「外层 `retCode` / `retMsg` + 业务字段平铺在同一层」，不是嵌套 data；旧实现用 `successData(JSONObject)` 把 `retCode` 直接 put 进业务体，本类保持一致。`TvmResponses`（类注释）— `api/device/tvm/TvmResponses.java:9`
- **2026-09-16 起，凡是「成功响应带业务字段」的接口，其失败分支 MUST 也回同一套键、值全为 JSON null**（用户明确要求：「参考 requestTakeTicketAuth，错误也要有全量字段，保证设备端能正常编译」）。设备侧按固定结构体解析，键缺失会导致解析/编译失败 —— 只回 `retCode/retMsg` 的裸错误体在那一侧是不可解析的报文。落地形态是每类响应各抽一个私有 `*Body(...)`，成功与失败共用，**NEVER 在失败方法里再抄一份 put 列表**。`TvmResponses` — `api/device/tvm/TvmResponses.java:18`
- **`fail(DeviceRetCode, String)` / `fail(DeviceRetCode)` 这两个只有两键的通用失败构造 NEVER 删、NEVER 加业务字段**：它们服务于成功侧本来就只有 `retCode/retMsg` 的接口（设备心跳、出票上报、充值结果通知、激活取票），那些接口加字段反而是契约变更。`TvmResponses` — `api/device/tvm/TvmResponses.java:24`
- **IF2A-01 拉码下单失败带与成功响应相同的 `orderNo` / `payUrl` 两键、值为 JSON null**；`retCode` 由调用方给定、逐个失败分支保持原码（参数校验 `2002`、支付中心被拒或状态不明 `2999`），**NEVER 借这次改动统一错误码**。同一条 URL 上 `providerId=03` 走 BOM 售票、成功体只有 `orderNo`，键集是本方法的子集；校验发生在 controller 分流**之前**、拿不到 provider，故统一回本方法 —— 多出来的 `payUrl:null` 对 BOM 是冗余键、不影响结构体取值，而少键才会让设备解析失败。`TvmResponses.genSjtOrderFail` — `api/device/tvm/TvmResponses.java:41`
- **查询支付结果：外层 `retCode` 随 `paymentResult` 变，NEVER 写死 0000。** 旧实现成功走 `successData` 给 `0000/成功`，支付失败与未支付走 `failData` 给 `2999/失败`（`TvmOrderServiceImpl:302~310`、`TvmPayCodeEnum.FAIL`）。2026-09-13 用两笔真实历史失败单（`00202609111620275127` 拉码、`08202609111700165143` 充值）重放实测：旧回 `2999/失败`、新回 `0000/成功`，内层 `paymentResult:FAILED` 两边一致。**TVM 设备若按外层码判成败，这条差异会把失败单读成成功**，据此改为按结果取码。`ORDERED`（支付中）仍是 `0000` —— 2026-09-11 的 34 例 A/B 里 `TVM_payResult_unpaid` 两侧本就一致，**NEVER 顺手把它也改成 2999**。`TvmResponses.payResult` — `api/device/tvm/TvmResponses.java:69`
- **IF2A-03 查询支付结果失败（参数缺失 / 订单不存在）带与成功响应相同的 3 个业务键、值为 JSON null；业务值 MUST 全 null，NEVER 顺手填成 `FAILED`** —— 这一支的语义是「这笔查不到」，不是「这笔支付失败了」。设备若把它读成支付失败，会对一笔可能已扣款的单子做错误处置。码仍保持 `2002`（参数校验族），与查得到但失败时的 `2999` 区分开。`TvmResponses.payResultFail` — `api/device/tvm/TvmResponses.java:90`
- **取票鉴权成功 8 个业务字段 + retCode/retMsg；`singelTicketNum` 的拼写错误是既有契约**（旧 `DeviceResponse` 即如此）。旧实现用 `String.valueOf(getTicketNum())` 在票数为 null 时产出字面量 `"null"`，设备侧可能误解析成 1；本实现票数为 null 时直接给 JSON null。`TvmResponses.takeTicketAuthSuccess` — `api/device/tvm/TvmResponses.java:126`
- **取票鉴权「无激活的订单」：`retCode=2003` + 与成功响应完全相同的 8 个业务键、值全为 JSON null**（用户 2026-09-16 明确要求）；**码与文案仍是 `2003 无激活的订单`，NEVER 改**。**值 MUST 是 JSON null，NEVER 是字符串 `"null"` 或空串** —— 旧 collect-pay 在「查到行但未激活」那支回的是 `failData(空 TvmAppOrder)`，其 `String.valueOf(null)` 产出字面量 `"null"`，设备侧可能把 `singelTicketNum` 误解析成 1 张票而多出一张票。本方法只恢复「键齐全」，不恢复那个字面量缺陷，也不恢复旧的 `2999` 码。键名与键序 MUST 与 `takeTicketAuthSuccess` 一致，故两者共用 `takeTicketAuthBody`。`TvmResponses.takeTicketAuthNoActiveOrder` — `api/device/tvm/TvmResponses.java:142`
- **取票鉴权通用失败：键集同成功、业务值全 JSON null，码由调用方给定、NEVER 在本方法里写死**；controller 入参校验分支用 `2002`。`TvmResponses.takeTicketAuthFail` — `api/device/tvm/TvmResponses.java:161`
- **支付中心查询 ITP 订单详情 13 个 key 逐字照搬旧 `getPayCenterPayOrderDetailResult`；两处怪异之处 NEVER「修正」**：站名与站码取同一个值（旧实现留着「todo 需改为中文名」）；`orderStatus` 在「支付失败 / 未支付」时是**空字符串**而不是某个码。`TvmResponses.payOrderDetail` — `api/device/tvm/TvmResponses.java:189`
- **`totalTicketPrice` MUST 输出字符串**：旧 `TvmPayOrder.totalPrice` 是 String 字段，响应里是 `"totalTicketPrice":"200"`；新表 `ORDER_AMOUNT` 是 NUMBER，直接 put 会序列化成数字。对端是支付中心收银台，按 String 取值时数字会解析失败。2026-09-10 双跑重放对比实测发现，**NEVER 改回数字**。`TvmResponses.payOrderDetail`（行内注释）— `api/device/tvm/TvmResponses.java:216`
- **充值单订单详情键集与购票单不同**：没有 `singleGetoffStationName / Code` 两个键（充值无出站）；多一个 `singleTicketPrice`、与 `totalTicketPrice` 同值；`singleTicketNum` 是**字符串 "1"** 而购票单那边是数字 —— 这个类型差异是旧实现既有形态、收银台已按此解析，**NEVER 统一成数字**；上车站码取 `deviceId` 前 4 位。`TvmResponses.topupOrderDetail` — `api/device/tvm/TvmResponses.java:232`
- **IF2A 设备退款成功 `retCode=0000` + 3 个业务键 `refundResult / refundResultDesc / refundNo`**，键名与键序逐字照搬旧 collect-pay 的 `RequestRefundRespDTO`。`TvmResponses.refundSuccess` — `api/device/tvm/TvmResponses.java:274`
- **退款接口专用失败 `retCode=9999`**，与 TVM 的 2xxx、BOM 的 8999 都不同 —— 同一个服务里三套错误码族并存，既有契约、设备侧已按此解析，**NEVER 统一成 2999**。业务键与 `refundSuccess` 同一套、值全 JSON null；**NEVER 把 `refundResult` 填成 `FAILED`**：参数非法 / 订单不存在 / 状态不可退这些分支表达的是「请求没被受理」，不是「退款做了但失败了」，后者只能由 `F2F_REFUND.REFUND_STATUS` 收口后经查询或通知给出。`TvmResponses.refundFail` — `api/device/tvm/TvmResponses.java:297`
- **设备退款「订单不存在」是 `retCode=2002`，与同一接口其它失败分支的 9999 不同码**：旧 `requestRefund` 查不到单时走参数校验族的 `2002`，只有业务失败才用 `9999`。2026-09-11 新旧双打实测：同一报文旧返 `2002`、新返 `9999`，retMsg 一致，据此改回。**NEVER 把本方法并回 `refundFail(String)`。** `TvmResponses.refundOrderNotFound` — `api/device/tvm/TvmResponses.java:328`
- **出票成功上报的「订单不存在」按 `providerId` 分两个码：`providerId=03` 回 `2999`，其余（含缺失）回 `-1`**。旧 `TvmOrderServiceImpl.notiTakeTicketResult:371` 写的是 `fail("-1", ...)`，而紧邻的 `notiTakeTicketFailResult:579` 写的是 `failMessage(...)` 落到 2999 —— 同一对上报接口的两个码不一样，TVM 已按此解析，**NEVER 把两者统一，也 NEVER 改成 `DeviceRetCode.ORDER_NO_ERROR`（2006）**。判据 **MUST 用 `providerId` 本身，NEVER 换成 `channelOf(...)` 算出来的渠道码**：`BomOrderController.channelOf` 在 `providerId` 缺失时兜底 BOM，用渠道码判会把「BOM 前缀 + 无 providerId」也判成 2999，而旧实现那一支根本不存在、没有基线。（ADR-D105，2026-09-16 新旧双打实测矩阵：旧 `03`→`2999`、旧 `02`/`01`→`-1`；新修复前三者一律 `-1`；兄弟接口 `notiTakeTicketFailResult` 两侧三种 `providerId` 全是 `2999`、本来就一致、不需要分叉。）`TvmResponses.takeTicketOrderNotFound` — `api/device/tvm/TvmResponses.java:343`
- **出票失败上报的「订单不存在」是 `retCode=2999`**（旧 `TvmOrderResult.failMessage(String)` 的默认码 `TvmPayCodeEnum.FAIL`），**不是 2006**。`TvmResponses.takeTicketFailOrderNotFound` — `api/device/tvm/TvmResponses.java:376`
- **`retCode/retMsg` MUST 在业务键之后 put**：旧实现即「业务体在前、码在后」，本类全部构造保持同一键序。`TvmResponses.withCode` — `api/device/tvm/TvmResponses.java:406`
- **`retCode` 表示的是「请求是否被受理」，不是「支付是否成功」**：查询支付结果接口即使查到支付失败，`retCode` 仍是 `0000`，业务结果只体现在响应体的 `paymentResult` 里。这个反直觉的形态是既有契约，**NEVER 在重写里「修正」**。`DeviceRetCode`（类注释）— `api/device/DeviceRetCode.java:6`
- **`paymentResult` 与内部状态的映射（NEVER 直接把内部状态吐给设备）**：`F2F_ORDER.ORDER_STATUS` 为 PAID / FULFILLED 等已收款态 → `SUCCESS`；PAY_FAILED / EXPIRED / CANCELED → `FAILED`；TVM `requestPayResult` 的「尚未支付」→ `ORDERED`（文案「已下单」）；扫码支付进行中、以及**支付中心没答上来（UNKNOWN）** → `PROCESSING`（文案「处理中」），让设备继续轮询，**NEVER 报 FAILED（钱可能已经扣了）**。`PaymentResult`（类注释）— `api/device/PaymentResult.java:7`
- **`ORDERED` 与 `PROCESSING` 不能互换**：旧实现里 TVM 的 `requestPayResult` 用 `ORDERED/已下单`，而 BOM 的 `requestGetPayResult` 与两侧的 `requestPayment` 用 `PROCESSING/处理中`（`BomOrderServiceImpl:424`、`TvmOrderServiceImpl:245`）。2026-09-11 新旧双打实测：同一笔未支付的 BOM 单，旧返 `PROCESSING`、新曾返 `ORDERED`（`paymentResultDesc` 两边都是「处理中」，只有码不一样），据此拆成两个值。**NEVER 为了「统一枚举」再合并回去。** `PaymentResult` — `api/device/PaymentResult.java:16`
- **`singelTicketNum` 少一个 `l`（不是 `singleTicketNum`），而同一份报文里的 `singleTicketType` 拼写正常**。这是设备实际发送的名字，**NEVER 顺手改正——改了就收不到值**。`RequestGenSjtOrderReqDTO` — `api/device/tvm/RequestGenSjtOrderReqDTO.java:9`
- **IF2A-01 报文没有 `orderNo` 字段**：订单号由 ITP 生成后返给设备、不是设备传入。这条否掉了「靠 `UK_F2F_ORDER_NO` 兜设备重发」的设想。金额单位是**分**、但类型是 `String`（旧契约如此）。`RequestGenSjtOrderReqDTO` — `api/device/tvm/RequestGenSjtOrderReqDTO.java:13`
- **`takeTickeDate` 的拼写错误（少一个 t）是既有契约，NEVER 改正** —— 设备侧按这个 key 发报文，改名等于收不到值。`NotiTakeTicketResultReqDTO` — `api/device/tvm/NotiTakeTicketResultReqDTO.java:12`
- **IF2A-02 付款码支付（主扫）的错误码族与其余 TVM 接口不同**：旧实现的校验失败走 `BomOrderResult.failMessage(...)`，`retCode` 是 **8999** 而不是 2002/2999（TVM 与 BOM 共用同一段实现），重写照搬。`RequestPaymentReqDTO` — `api/device/tvm/RequestPaymentReqDTO.java:9`
- **TVM 主动退款又是一套独立错误码**：旧实现校验失败返回 `retCode=9999`「订单号和退款金额不能为空」，与 2xxx / 8999 都不同。既有契约、照搬。`RequestRefundReqDTO` — `api/device/tvm/RequestRefundReqDTO.java:9`
- **IF2A-08 取票鉴权没有 `orderNo`**：设备扫到二维码后只有 `(deviceId, qrcodeGenDate, randomFact)`，靠这三要素反查订单，命中 `IDX_F2F_ORDER_QRCODE`。`RequestTakeTicketAuthReqDTO` — `api/device/tvm/RequestTakeTicketAuthReqDTO.java:9`
- **IF2A-11 写卡充值失败上报：`topupStatus` 只有 `01`（失败）才退款**，`02`（存疑）与 `03`（取消）不退、留人工处理 —— 存疑意味着卡可能已写成功，盲退会造成「卡里有钱、钱也退了」。这是旧实现的口径，照搬。`TopupCardFailNotiReqDTO` — `api/device/tvm/TopupCardFailNotiReqDTO.java:9`
- **IF2A-09 `payType=0` 走本地聚合码（不调支付中心），其余走支付中心预下单**，与购票链路口径一致。`RequestTopupReqDTO` — `api/device/tvm/RequestTopupReqDTO.java:13`
- **`beforeAmount` 的 `0` 是合法值**：新卡 / 已刷空的卡余额就是 0，此时必须能充值。本方法曾与 `transAmountInFen()` 共用「必须为正」的解析，导致 `beforeAmount="0"` 被判成非法数字、整笔充值被 `2002` 拒绝，而旧实现返回 `0000` + payUrl（2026-09-11 新旧双打实测）。两个字段的合法区间不同，**NEVER 再合并成同一个 parse**。`RequestTopupReqDTO.beforeAmountInFen` — `api/device/tvm/RequestTopupReqDTO.java:48`
- **IF2A-04 设备心跳恒回 `0000`**，即使 `deviceId` 缺失也不报错 —— 心跳接口回失败会让设备侧告警刷屏，而设备号缺失是对端报文问题。`TvmOrderController.notiDeviceHeard` — `controller/ci/tvm/TvmOrderController.java:90`；同款口径在 `F2fDeviceHeartbeatService.recordHeartbeat` — `service/F2fDeviceHeartbeatService.java:40`
- **TVM 侧 URL 与旧服务一字不改**：类级 `/itptvm/ci/tvm`，端口 58101、无 context-path。入参是 `@ModelAttribute` 表单绑定 + `bizData` 二次反序列化，**NEVER 改成 `@RequestBody`**；**本链路无验签**，加鉴权属契约变更、需独立评审。`TvmOrderController`（类注释）— `controller/ci/tvm/TvmOrderController.java:42`
- **`requestPayOrderDetail` 这条不走 bizData**：调用方是支付中心收银台，`orderNo` 直接放在表单里，绑定后即用、不做二次反序列化。照搬旧实现的入参形态，**NEVER 改成 unwrap**。`TvmOrderController.requestPayOrderDetail` — `controller/ci/tvm/TvmOrderController.java:265`
- **`payNotice` 的 JSON 入口是主路径**：支付中心用 `application/json` 发请求（网关文档第 26 行）。另有 form 兜底入口，`x-www-form-urlencoded` 与不带 `Content-Type` 的请求都落到那里。`bizData` MUST 走 `PayCenterCallbackRequest.bizDataJson()` 拿明文，**NEVER 直接 parse**（网关的 `bizData` 是 Base64 后的 JSON）。`TvmOrderController.payNotice` / `payNoticeForm` / `handlePayNotice` — `controller/ci/tvm/TvmOrderController.java:280` / `:296` / `:311`
- **IF2A-03 `retCode` 恒为 0000**，业务结果在 `paymentResult`；本地已是终态则短路返回，只有 `CREATED` / `PAYING` 才去问支付中心。`F2fTvmOrderService.requestPayResult` — `service/F2fTvmOrderService.java:321`
- **BOM 售票下单（同一条 URL、`providerId=03` 分流）只落订单、不碰支付中心、不出二维码，返回体只有 `orderNo`**；**返回体是 BOM 族（`0000` / `8999`），但入参校验失败仍回 TVM 的 `2002`** —— 校验发生在 controller 分流之前、两个渠道共用一套文案，**NEVER 为 BOM 单独改成 8003**。`F2fTvmOrderService.requestBomSaleOrder` — `service/F2fTvmOrderService.java:200`
- **收银台 `orderStatus` 口径逐字照搬旧实现**：`1` 支付中、`2` 支付成功、`7` 已退款、**其余是空字符串**。空串看着像 bug，但收银台已按此解析，**NEVER 改成某个码**。`F2fTvmOrderService.requestPayOrderDetail` — `service/F2fTvmOrderService.java:490`
- **`7`（已退款）判据是 `REFUND_STATUS`、不是 `ORDER_STATUS`**（ADR-D88）；**`PARTIAL` 与 `SUCCESS` 都答 7**，回归旧 collect-pay 口径（ADR-D106，用户裁决），旧判据是 `TBL_TVM_ORDER_PAY.RSV2`（退款单号）非空、而部分退与全额退都会写那一列 —— 收银台侧 `7` 的语义一直是「这单发生过退款」、不是「已全额退完」。**NEVER 退回只认 `SUCCESS`**：那样一笔部分退款单在旧系统答 7、在本模块答 2，切换即静默改变对外行为。我方内部仍**保留 `NONE` / `PARTIAL` / `SUCCESS` 三档**，只在这一个出口上把后两档投影成同一个码，**NEVER 因为对外合并就把内部也并成两值**。`F2fTvmOrderService.toCashierStatus` — `service/F2fTvmOrderService.java:524`
- **支付结果回调用 `merchantOrderNo` 定位本地订单**（`orderNo` 是支付中心侧订单号）；应答语义：只有明确处理完才回 `code=0`，**状态不明一律回失败让对端重推，NEVER 回成功** —— 那等于永久丢掉一笔支付结果；已是终态的重复回调直接回成功（幂等）。`F2fTvmOrderService.receivePayNotice` — `service/F2fTvmOrderService.java:439`
- **`PAID_LIKE`（对设备口径为「已收款」的内部状态白名单）里仍留着 `REFUNDING` / `REFUNDED`**：主状态自 ADR-D88 起不再推进到这两个状态，但**存量行还在**，去掉会让老单答成空串。`F2fTvmOrderService.PAID_LIKE` — `service/F2fTvmOrderService.java:82`
- **BOM 售票单的失效时长不能复用 `qrcodeExpireSeconds`（180 秒）**：BOM 售票没有二维码，下单后由售票员扫乘客付款码再调 `requestPayment`，中间是人工操作、180 秒内收不了口；**但也 NEVER 留 null** —— APP 单已经踩过这个坑：`EXPIRE_TMS` 为空的订单永远不被 `F2fOrderExpireJob` 扫到、未付款的单会永久停在 `CREATED`。折中给 30 分钟。两个渠道的 `expireSeconds` **都 MUST 有值**。`F2fTvmOrderService.bomSaleExpireSeconds` / `insertOrder` — `service/F2fTvmOrderService.java:128` / `:259`

#### 1.2 决策理由

- **三条不可违反的编排约束**：①整个类不带 `@Transactional`（链路里有支付中心调用，事务包住网络调用会把行锁持有时长拉长到对端响应时长，AGENTS.md §5.2 的 2026-08-26 生产事故），顺序固定「INSERT 订单 → 事务外调支付中心 → UPDATE 结果」；②**对端没答上来时 NEVER 把订单写成 PAY_FAILED**（钱可能已经扣了），订单留在 `CREATED`、由 180 秒过期扫表或后续查询接口收口，旧实现在这里直接写 FAILED、是本次重写要修掉的行为；③状态判断用白名单，只有明确列举的状态才短路。`F2fTvmOrderService`（类注释）— `service/F2fTvmOrderService.java:41`
- **`CHANNEL_BOM` 常量单列的理由**：BOM 卖单程票走的是 **TVM 的 URL**（`/itptvm/ci/tvm/requestGenSjtOrder`）、靠 `providerId=03` 分流，不是 `/itpbom` 下的接口（2026-09-10 双跑抓包实测确认）。旧实现把这笔单写进三张表且**没有任何一列存 `'03'`**，渠道信息只存在于路由分支里、落库后无从分辨；新表用 `CHANNEL` 记住它。`F2fTvmOrderService.CHANNEL_BOM` — `service/F2fTvmOrderService.java:61`
- **`TicketSpec.invalid` 用一个字段带回文案而不是直接抛异常**：为了让 TVM 与 BOM 两条分支**共用同一套校验但各自组装自己族的响应**，同时不改动既有文案。`F2fTvmOrderService.TicketSpec` — `service/F2fTvmOrderService.java:232`
- **两种 PENDING 的 `channelCode` 取值不同，这是重构前就有的区别、不是笔误**：对端没答上来时报文里没有 `paymentVendor`、只能回落到本地订单的渠道；答上来但状态仍在处理中时，以对端给的 `paymentVendor` 为准。`F2fTvmOrderService.requestPayResult`（行内注释）— `service/F2fTvmOrderService.java:357`
- **二维码超时收口的顺序是「先问支付中心，再决定是否置 EXPIRED」**：不能盲目按时间置过期 —— `PAYING` 意味着码已经给出去了，乘客可能刚付完而回调还没到。`SUCCESS` → `markPaid`（相当于补救一次丢失的回调）；`FAILED` / `UNPAID` → 置 `EXPIRED`；**没答上来或状态不明 → 什么都不做**，留给下一轮扫表，**NEVER 在这里置终态**。`F2fTvmOrderService.settleExpiredOrder` — `service/F2fTvmOrderService.java:366`
- **「对端答上来了、业务码非 0（实测 9999 未找到数据）」按 EXPIRED 收口**：支付中心没有这笔单，等价于「用户从未支付」。这正是 `PayCenterResult` 类注释里「业务失败：code != 0，对端明确拒绝，可判 FAILED」那一条。**NEVER 退回「当作没拿到结果、下轮再试」** —— 2026-09-10 实测该分支让订单 `F200202609100914540082` 每 30 秒外呼一次、持续 40 分钟没有出口。`F2fTvmOrderService.settleExpiredOrder`（行内注释）— `service/F2fTvmOrderService.java:388`
- **`markPaymentSuccess` 存在的理由**：没有这一步时 `F2F_PAYMENT.PAY_CHANNEL_CODE` 永远为空、查询接口回吐的 `paymentChannelCode` 恒为 null，而旧实现是写库并回吐的。`markSuccess` 返回 0 属正常（该尝试已是 SUCCESS、回调先到了），只记日志不报错；并发下第二条 SUCCESS 由 `UK_F2F_PAY_SUCCESS` 抛 `DuplicateKeyException`，同样按「已有成功记录」幂等吞掉。**NEVER 让这里的异常打断订单状态推进** —— 支付确实成功了。`F2fTvmOrderService.markPaymentSuccess` — `service/F2fTvmOrderService.java:566`
- **`requestPayOrderDetail` MUST 按 `BIZ_TYPE` 分流、NEVER 用一套键集覆盖两种业务**：2026-09-11 重放对比实测，合成一套会让充值单的站码、张数、单价三项全部退化成 null、收银台页面渲染不出充值信息。`F2fTvmOrderService.requestPayOrderDetail` — `service/F2fTvmOrderService.java:496`
- **`payOrderDetail` 的失败分支刻意没有做「全量 null 键」改造**（与本类其它带业务字段的响应不同）：调用方是**支付中心收银台、不是设备**，不存在「按固定结构体解析导致编译失败」的问题；且购票单 13 键与充值单 12 键的**键集本就不同**，订单查不到时无法判断该回哪一套。要改 MUST 先与支付中心确认按哪套键，**NEVER 自行挑一套补上**。`TvmResponses.payOrderDetail` — `api/device/tvm/TvmResponses.java:197`；同一判据的行内注释在 `F2fTvmOrderService.requestPayOrderDetail` — `service/F2fTvmOrderService.java:507`
- **`requestGenSjtOrder` 的校验提到了分流之前**：两个渠道的必填项完全相同，旧实现只在 TVM 分支校验，BOM 分支缺参时会在 `new BigDecimal(null)` 处抛 NPE、退化成 UUID retCode。`TvmOrderController.requestGenSjtOrder` — `controller/ci/tvm/TvmOrderController.java:145`
- **补一条 `payType` 显式校验是有意的行为修正**：旧实现没有校验 `payType`，而下游 `request.getPayType().equals("0")` 会在 payType 为空时抛 NPE、退化成 UUID retCode。失败结果不变，但错误码从 500 变成 2002。`TvmOrderController`（校验方法）— `controller/ci/tvm/TvmOrderController.java:341`；DTO 侧同款说明在 `RequestTopupReqDTO` — `api/device/tvm/RequestTopupReqDTO.java:9`
- **保留 `payNoticeForm` 兜底入口的理由不是文档、而是无法排除**：旧应用的 `payNotice` 用 `@ModelAttribute`（只能收 form），从上线到 2026-09-11 一次都没被真实调用过（当天翻遍旧应用日志，`/itptvm/ci/tvm/payNotice` 入站记录为 0），所以「支付中心到底发 JSON 还是 form」在我方没有实证样本。两种都收下比赌一种更稳。`TvmOrderController.payNoticeForm` — `controller/ci/tvm/TvmOrderController.java:296`
- **`channelOf` 兜底到 TVM 的理由**：出票结果上报本来就同时服务 TVM 与 BOM（旧实现在同一个 URL 里按 `providerId=03` 分流到 BOM 的一套代码，两边做同一件事）；新表统一后不再分流，只需把渠道码记对。`providerId` 缺失或非法时归到 TVM —— 这条 URL 挂在 `/itptvm` 下，默认归属 TVM 比归到 null 更符合事实（旧实现在这里是 `request.getProviderId().equals("03")`、缺失直接 NPE）。`TvmOrderController.channelOf` — `controller/ci/tvm/TvmOrderController.java:370`
- **`requestActiveTicket` 的「已被其他设备激活」由旧的 2999「激活失败」改成 2008「订单已锁定」**：设备侧靠码值区分「该重扫」还是「换一台机器」，2999 无法区分。其余文案逐字照搬。`F2fTakeTicketService.requestActiveTicket` — `service/F2fTakeTicketService.java:63`
- **扫码取票两处修掉的旧缺陷**：①激活改成条件更新抢锁（旧实现是无条件 `UPDATE ... WHERE ORDER_NO=?`，两台设备同时扫同一个码会双双成功、订单被后写的那台覆盖，前一台随后取票鉴权查不到；现在 WHERE 带 `ACTIVATE_DEVICE_ID IS NULL`、返回 0 即已被占用回 2008）；②鉴权查询不再依赖「多查一条就报失败」（旧 `selectByDeviceAndQrcode` 返回 List、命中多条时回「查询到的订单数量过多，失败」，三要素本应唯一，现在 `selectByQrcode` 直接取一行）。`F2fTakeTicketService`（类注释）— `service/F2fTakeTicketService.java:25`
- **`F2fTakeTicketService` 无网络调用、也不带 `@Transactional`**：两个方法各自只有一条写 SQL，靠条件更新本身保证原子性，加事务没有收益。`service/F2fTakeTicketService.java:39`
- **充值与购票共用 `F2F_ORDER`、靠 `BIZ_TYPE`（01 购票 / 02 充值）区分**：旧实现是 `TVM_TOPUP_ORDER` + `TVM_PAY_PRE_ORDER` 两张表，查支付结果时还要先查前置表拿 `transType` 再分流（`TvmOrderPreServiceImpl`）。表合一后这一层路由消失，`requestPayResult` 一个实现同时覆盖两种业务。`F2fTopupService`（类注释）— `service/F2fTopupService.java:39`
- **充值失败必须退款**：钱已收但卡没充上，`topupCardFailNoti` 里 `topupStatus=01` 即触发全额退款；来源 `TOPUP_FAIL`，幂等键「原订单号 + #WHOLE# + TOPUP_FAIL」，同一笔重复通知只会产生一张退款单。`F2fTopupService` — `service/F2fTopupService.java:45`
- **IF2A-06 补上了旧实现缺的状态推进**：旧实现只 INSERT 通知记录、**订单状态一直停在支付成功**，于是「已收款未完成业务」的每日批量退款任务无法区分「真的没充上」和「充好了没记账」。`F2fTopupService.topupCardResultNoti` — `service/F2fTopupService.java:178`；DTO 侧同款说明（`AFTER_AMOUNT` 恒空、`UpdateDbMap.getTopupNotiUpdateSuccessDb` 全仓库无调用点）在 `TopupCardResultNotiReqDTO` — `api/device/tvm/TopupCardResultNotiReqDTO.java:9`
- **订单未支付时只留上报、不推状态（`reportOnly`）**：旧实现除「订单不存在」外**完全不看状态**，一律 INSERT 通知并回 `0000`（`TvmTopupServiceImpl:281-297`）。曾改成「非可上报状态回 2005」，但支付中心 payNotice 迟到时 TVM 可能已经把卡充好并上报，拒收等于把这条唯一的现场证据丢掉、且设备不会再补。因此保留旧的「收下」语义，只把**状态推进**锁在 `PAID` 上 —— 两者是独立的两件事，**NEVER 因为「白名单优先」把上报也一起拒掉**。2026-09-11 新旧双打实测：旧 `0000`、新 `2005`。`F2fTopupService.topupCardResultNoti` — `service/F2fTopupService.java:182`；`topupCardFailNoti` 同款 — `service/F2fTopupService.java:219`
- **`getPayCenterOrderNo` 取真值**：旧实现这里硬编码空串（`BomOrderServiceImpl:640` 留着「根据实际情况填写」的注释），支付中心只能靠商户订单号定位。`F2fTopupService` — `service/F2fTopupService.java:437`；`F2fBomOrderService` 同款 — `service/F2fBomOrderService.java:397`
- **订单号格式 20 位、与旧实现完全一致，NEVER 加版本标识前缀**（用户 2026-09-10 裁决「按照旧的来」）。此前曾用 22 位 `F2 + 业务码 + 时间 + 序列`，但设备与支付中心收银台对该字段是否有长度限制未核实，**长度变化是会直接打挂链路的 A 类契约差异**。**格式只在本类里定义一处，NEVER 在别处散落字符串拼接。** `F2fOrderNo`（类注释）— `support/F2fOrderNo.java:7`
- **蓝绿并行期区分新旧服务产生的单不靠订单号、靠表**：新服务只写 `F2F_*`、旧服务只写 `TBL_TVM_*` / `TBL_BOM_*`，两边零交集。两套服务用的是不同序列（`F2F_ORDER_NO_SEQ` vs `ORDER_NO_SEQ`），但**同一秒内两边各取到相同序列值时会生成相同订单号** —— 切换期是「停旧起新」而非同时收流量，因此不构成问题；**若将来真要双写收流量，MUST 先解决这个碰撞**。`F2fOrderNo` — `support/F2fOrderNo.java:21`
- **退款单号与订单号共用同一格式、同一序列，只靠业务码区分**：好处是运维看号段即知单据类型、也不必再维护第二个序列。旧实现的退款单号由 `OrderCommonUtils` 另起一套规则、两者不兼容，但**退款单号不出现在设备契约里**（`requestRefund` 的成功响应只回 `retCode/retMsg`），因此换格式对设备无影响。`F2fOrderNo.BIZ_REFUND` — `support/F2fOrderNo.java:40`
- **`F2fSequenceMapper` 与旧服务的 `ORDER_NO_SEQ` 不共用**：蓝绿并行期两套服务各自发号，旧服务回滚也不会因新服务消耗过序列而出现空洞。SQL 写在 XML 而不是 MyBatis 注解（旧 `OrderSeqMapper` 用 `@Select` 把 SQL 写在注解里，本模块不沿用）。`F2fSequenceMapper` — `mapper/F2fSequenceMapper.java:6` / `:17`；XML 侧 — `F2fSequenceMapper.xml:10`
- **`F2fChannel` 写成常量而不是枚举**，与本项目「多数模块用 String 字面量表达状态」的既有做法一致 —— 枚举在 mapper 参数与 JSON 报文之间来回转换只会增加转换点。**`providerId` 与 `CHANNEL` 恰好同码（`03` 都表示 BOM）、但两者语义不同**，抽成一个方法后一旦哪天不再同码，改这一处即可、不必全局 grep 字面量。`F2fChannel` / `F2fChannel.channelOf` — `support/F2fChannel.java:7` / `:26`

#### 1.3 陷阱

- **`deviceId` 只在表单值非空非空白时才覆盖**：部分 TVM 报文把 `deviceId` 放在 bizData 里，无条件覆盖会把它擦成 null。`DeviceRequests.unwrap` — `api/device/DeviceRequests.java:8`
- **旧 `RequestActiveTicketReqDTO` 用 Lombok 在子类重复声明了 `deviceId`，生成的 getter 覆盖父类**，导致父类字段恒空、表单上的 `deviceId` 永远读不到。本类**不重复声明**，报文层面无差异。`RequestActiveTicketReqDTO` — `api/device/tvm/RequestActiveTicketReqDTO.java:9`
- **`actualNum()` 解析失败时 MUST 拒绝而不是当 0 处理 —— 当 0 会退全款**：出票失败上报会触发差额退款，订单买了 N 张、实际只出了 M 张、需要退 `(N-M) × 单价`。`NotiTakeTicketFailResultReqDTO.actualNum` — `api/device/tvm/NotiTakeTicketFailResultReqDTO.java:12`
- **旧实现 `Integer.parseInt(...)` 裸调用**，非数字或为空即抛异常并退化成全局异常处理器的 UUID retCode、设备会一直重传；这里返回 null 让调用方按 2002 拒绝。`NotiTakeTicketResultReqDTO.actualNum` — `api/device/tvm/NotiTakeTicketResultReqDTO.java:30`
- **旧 `Integer.valueOf(refundAmt)` 无保护且不校验上限，可以退出比原订单更多的钱**；本实现用 `amountInFen()` 返回 null，并在 service 里对「超过原订单金额」直接拒绝。`RequestRefundReqDTO` — `api/device/tvm/RequestRefundReqDTO.java:12`
- **`transAmount` 非数字返回 null、上层按 2002 拒绝是有意收紧**：旧实现对 `transAmount="abc"` 直接返回 `0000` 并建单（2026-09-11 新旧双打实测），金额根本没落地却告诉设备成功，**NEVER 退回旧行为**。负数与 0 能解析出来，由 `F2fTopupService.requestTopup` 的 `transAmount <= 0` 分支单独回「transAmount必须为正数」、与「不是合法数字」区分开。`RequestTopupReqDTO.transAmountInFen` — `api/device/tvm/RequestTopupReqDTO.java:34`
- **旧实现校验里没有 `payType`，随后却直接 `request.getPayType().equals("0")`，缺字段即 NPE**；本实现把 `payType` 纳入必填校验，且判断写成 `"0".equals(payType)` 的顺序。`RequestTopupReqDTO` — `api/device/tvm/RequestTopupReqDTO.java:9`
- **旧库只读旁路已于 2026-09-13 按用户裁决整体删除**（原话「后续要停掉旧服务，删除旧库的，不需要做兼容层」）。连带后果：**切流前由 `collect-pay-server` 建的历史单，在本服务上一律「订单不存在」**（`2002 / 没有找到匹配的订单`）。这是有意的，**NEVER 因为「历史单查不到」再把旁路加回来**。`F2fTvmOrderService.loadOrder` — `service/F2fTvmOrderService.java:160`
- **旧实现没校验充值金额格式**，`Integer.parseInt` 在非数字时抛异常、退化成全局异常处理器的 UUID retCode；这里显式挡在前面回 2002。`F2fTopupService.requestTopup` — `service/F2fTopupService.java:118`
- **序列段定长由数据库保证**（`F2F_ORDER_NO_SEQ` 建成 `MAXVALUE 9999 CYCLE`），Java 侧再做一次取模兜底 —— 旧实现只 `leftPad` 不取模，序列一旦超过 9999 就会吐出 21 位订单号，属潜在缺陷、这里不继承。`F2fOrderNo` — `support/F2fOrderNo.java:27`
- **STT 渠道尚未接入且编码口径未定**：`DeviceTypeEnum` 记 14、`BaseRequestDTO` 注释记 07，两者矛盾。确认后才能加常量并放开 CHECK 约束。`F2fChannel` — `support/F2fChannel.java:10`

### 二、BOM 设备域

#### 2.1 契约与判据

- **BOM 错误码族与 TVM 不同：成功 `0000`、失败 `8999`（不是 TVM 的 2999）。** 这一族同时被 TVM 的 `requestPayment` 使用 —— 旧实现里 TVM 付款码支付复用了 BOM 的这段实现、校验失败也回 8999。看着像 bug，但设备侧已按此解析，属既有契约，**NEVER 统一**。`BomResponses`（类注释）— `api/device/bom/BomResponses.java:9`
- **2026-09-16 起「成功响应带业务字段」的接口，其失败分支 MUST 也回同一套键、值全为 JSON null**（落地口径与 `TvmResponses` 一致）；BOM 侧按固定结构体解析、键缺失即解析失败。形态是每类响应各抽一个私有 `*Body(...)`、成功与失败共用，**NEVER 在失败方法里再抄一份 put 列表**。`BomResponses` — `api/device/bom/BomResponses.java:13`
- **`fail()` / `fail(String,String)` / `failMessage(String)` / `orderNotFound()` 这四个两键构造 NEVER 删、NEVER 加业务字段**：成功侧本来就只有 `retCode/retMsg` 的接口（设备心跳、业务操作结果通知、充值结果通知、HCE 更新结果通知）仍在用它们，给那些接口补业务字段反而是契约变更。`BomResponses` — `api/device/bom/BomResponses.java:19`
- **IF8A-04 下单失败带与 `successOrderNo` 相同的 `orderNo` 键、值为 JSON null**；`retCode` 由调用方给定：入参校验 `8003`、业务失败 `8999`，**NEVER 借这次改动统一**。`BomResponses.orderFail` — `api/device/bom/BomResponses.java:53`
- **支付结果响应的 `paymentResultDesc` 与 `msg` 取同一个字符串**，逐字照搬旧 `BomOrderResult.successPaymentResultWithMsg(paymentResult, desc, msg)` 的调用形态（旧实现两个入参传的是同一个值）。**NEVER 改成 `result.getMsg()`**：那会把 `paymentResultDesc` 变成枚举里的「失败 / 成功」，与旧服务的「支付失败 / 支付成功」不一致（2026-09-11 双跑对比实测到）。`BomResponses.paymentResult` — `api/device/bom/BomResponses.java:65`
- **IF8A-05 / IF8A-06 的入参校验、订单不存在、订单状态不允许支付这几支：业务值 MUST 全 null，NEVER 填成 `FAILED`** —— 语义是「这笔请求不成立」、不是「这笔支付失败了」。真正的支付失败仍走 `paymentResult(FAILED, "支付失败")` 且 `retCode` 是 `0000`，那是 BOM 已按此解析的既有形态，**两者 NEVER 混用**。`BomResponses.paymentResultFail` — `api/device/bom/BomResponses.java:78`
- **BOM 族码值清单**（旧 `BomPayCodeEnum`）：`8001` 非法设备、`8002` 订单未支付、`8003` 非法参数、`8004` 无激活的订单、`8005` 充值金额超限、`8006` 订单号错误、`8007` 订单已退款；旧实现只实际用了 `8003` 与 `8006`。`BomResponses.fail` — `api/device/bom/BomResponses.java:106`
- **「订单不存在」专用响应的 `retCode` 是 8006 而不是 8999**：2026-09-11 用 BOM 自检报文（`orderNo` 二十个 0）双跑实测，旧应用 `requestPayment` / `requestGetPayResult` / `notiBusResult` 三处都回 `8006`，新实现原先一律回 `failMessage`（8999），而**设备侧按 8006 分支处理「订单号打错了」、收到 8999 会当成通用失败**。**唯一例外是 `requestTicketRefund`：旧实现在那里回 `9999`（BOM 域仅此一处），NEVER 把它也改成 8006。** `BomResponses.orderNotFound` — `api/device/bom/BomResponses.java:121`
- **BOM 单程票退款成功 `retCode=0000` + 3 个业务键（2026-09-16 新增）**，键名与键序与 TVM 侧 `TvmResponses.refundSuccess` 完全一致（`refundResult / refundResultDesc / refundNo`）—— **不是巧合**：旧 collect-pay 的 `BomOrderServiceImpl.requestTicketTRefund` import 的就是 TVM 那个 `model.response.tvm.RequestRefundRespDTO`、两侧共用同一份 DTO。**旧实现的运行行为只有 2 键**（成功回 `success()`、失败回 `fail()` / `failMessage()`，那份 DTO 的 3 键 `success(...)` 是死代码、零调用方）；本方法按 DTO 公布的结构补齐，**NEVER 因为「旧实现只回 2 键」把这 3 个键删回去**。注意与 TVM 侧的**错误码族仍然不同**（本类是 BOM 族），加字段不改码，**NEVER 借这次改动把两族码统一**。`BomResponses.refundSuccess` — `api/device/bom/BomResponses.java:137`
- **BOM 单程票退款失败：带相同 3 个业务键、值为 JSON null；`retCode` 由调用方给定、逐个失败分支保持原码** —— 参数与状态类走 `8999`（`failMessage` 的码），**「订单不存在」那一支仍是 `9999`**（BOM 域仅此一处），**NEVER 归一成 8006 或 8999**。**NEVER 把 `refundResult` 填成 `FAILED`**：这些分支表达的是「请求没被受理」。`BomResponses.refundFail` — `api/device/bom/BomResponses.java:158`
- **IF5A-01 票卡分析结果 14 个业务 key 逐字照搬旧 `RequestCardDataAnalyseRespDTO`；`lastTransAmout` 与 `lastTikcetTransSeq` 两个拼写错误是既有契约**（少一个 n、Ticket 写成 Tikcet），BOM 侧已按此解析，**NEVER 更正**。`adviceOpt` 在下游是 `List<String>`、原样放入由 Fastjson 序列化成数组。`BomResponses.cardDataAnalyse` — `api/device/bom/BomResponses.java:181`
- **票卡分析失败带相同的 13 个业务键、值为 JSON null；`adviceOpt` 给的是 JSON null、不是空数组** —— 成功侧那个键在下游是 `List<String>`、序列化成数组，失败侧若擅自给 `[]` 等于告诉 BOM「分析成功、没有建议操作」，与「分析失败」不是一回事。**NEVER 改成空数组或空串。** `BomResponses.cardDataAnalyseFail` — `api/device/bom/BomResponses.java:199`
- **单程票交易查询的 `paymentResult` 值域是 `SUCCESS / FAILED / UNPAID`**（来自支付中心状态枚举），**与 `paymentResult(...)` 的 `SUCCESS / FAILED / ORDERED` 不同** —— 同一 controller 两套值域是既有契约。`BomResponses.orderResult` — `api/device/bom/BomResponses.java:248`；service 侧同款说明在 `F2fBomOrderService.requestOrderResult` — `service/F2fBomOrderService.java:212`
- **`transAmount` MUST 序列化成字符串**：旧实现是 `result.put("transAmount", orderResult.getString("ticketPrice"))`（`BomOrderServiceImpl:1314,1328`），BOM 侧一直收到的是 `"200"` 而不是 `200`。这里入参保持 `Long`（内部都是分）、只在出参处转字符串，**NEVER 直接 put 数值** —— 2026-09-11 双跑对比实测到该类型漂移。失败支的 `transAmount` 同样是 null（不是字符串 `"0"`）—— 给 0 会被 BOM 读成「查到了、金额为零」。`BomResponses.orderResult` / `orderResultFail` — `api/device/bom/BomResponses.java:254` / `:267`
- **IF2A-08 `optResult` 只有 `SUCCESS` / `FAILED` 两种有效取值，`FAILED` 触发原单全额退款**；**`tranDate` 少一个 s**（不是 transDate）、既有契约、**NEVER 更正**；旧实现拿到这个字段后完全没用过，本实现把它落到 `F2F_RESULT_REPORT.REPORT_TMS`。`NotiBusResultReqDTO` — `api/device/bom/NotiBusResultReqDTO.java:8` / `:11`
- **IF2A-09 BOM 充值结果通知的 `topupStatus` 取值方向与直觉相反**：`00` 成功、`01` 失败并退款；与 TVM 的 `topupCardFailNoti` 一致，属既有契约。`NotiTopupResultReqDTO` — `api/device/bom/NotiTopupResultReqDTO.java:8`
- **IF5A-09 的 `hceData` 是票卡数据密文**，落库到 `F2F_RESULT_REPORT.RAW_BODY` 供审计；**NEVER 打进业务日志**。`NotiUpdateHceDataReqDTO` — `api/device/bom/NotiUpdateHceDataReqDTO.java:8`
- **IF8A-04 的 `transAount` 拼写错误是既有契约**（少一个 m），设备侧按此拼装 bizData、**NEVER 更正**。`bomOptSeq` 是 BOM 侧操作流水，落到 `F2F_ORDER.DEVICE_SEQ`，由 `UK_F2F_ORDER_DEV_SEQ`（CHANNEL + DEVICE_ID + DEVICE_SEQ）保证同一台 BOM 的同一笔操作只会生成一张订单；旧实现只入库不判重、重复请求会重复开单。`RequestGenNoCashOrderReqDTO` — `api/device/bom/RequestGenNoCashOrderReqDTO.java:8` / `:11`
- **单程票交易查询两要素定位一张票：逻辑卡号 + 交易日期**，对应 `UK_F2F_TICKET_LOGIC`。`RequestOrderResultReqDTO` — `api/device/bom/RequestOrderResultReqDTO.java:8`
- **BOM 侧 URL 与旧服务一字不改**（类级 `/itpbom/ci/bom`）；错误码族是 `0000 / 8999 / 80xx`，校验失败统一用 `8003 非法参数`，但旧实现 `fail(8003,...)` 与 `failMessage(8999,...)` 混用、本实现照搬各端点的实际码值；**本链路无验签**。`BomOrderController`（类注释）— `controller/ci/bom/BomOrderController.java:36`
- **IF8A-05 只校验 `orderNo` 与 `paymentVendor`，不校验 `paymentCode`**：旧实现对缺 `paymentCode` 的报文照样往下走、真去调支付中心，最后回 `0000 + paymentResult=FAILED/支付失败`（2026-09-11 新旧双打实测：旧返 FAILED、新曾返 `8003 paymentCode不能为空`）。缺渠道码时支付中心必然拒付、不会动钱，因此照搬旧口径。与 `requestUpdateCardData` 同一条裁决（用户 2026-09-11「不校验」），**NEVER 再以「补齐校验」为理由加回来**。`BomOrderController.requestPayment` — `controller/ci/bom/BomOrderController.java:133`
- **BOM 这两个字段是错位的既有形态：`paymentCode` 实际是渠道码，`paymentVendor` 实际是付款码。NEVER「修正」字段名。** `BomOrderController.requestPayment` — `controller/ci/bom/BomOrderController.java:143`；落库与外发口径见 `F2fScanPayService`（下方陷阱条目）
- **IF5A-03 校验项与旧实现逐条对齐**（2026-09-11 新旧双打逐字段实测确认）：旧服务校验 `cardId` / `adviceOpt` / `updateStationCode` / `optDate`（四条都回 `8003 xxx不能为空`），但**不校验 `updateType`、也不校验 `optDate` 的格式** —— 这两种情况旧服务直接把请求透传给 ticket-server、返回下游码（实测 `8004 未注册用户`）。曾按「补齐校验」加过这两条，用户 2026-09-11 裁决「不校验」、已移除，**NEVER 再以「旧实现漏校验」为理由加回来**。`BomOrderController.requestUpdateCardData` — `controller/ci/bom/BomOrderController.java:253`
- **BOM 前缀的出票上报别名与 TVM 前缀那条同一实现、同一响应族（2xxx）**；渠道归属默认 `BOM`（本 URL 挂在 `/itpbom` 下）、`providerId` 合法时以它为准 —— 与 `TvmOrderController.channelOf` 只差兜底值，**NEVER 把兜底值改成 TVM**。`BomOrderController.notiTakeTicketResult` — `controller/ci/bom/BomOrderController.java:297` / `:308`
- **BOM 单程票退款按票退，幂等键是「原订单号 + 逻辑卡号 + BOM_ORIGINAL」**；成功与失败都回 `refundResult / refundResultDesc / refundNo` 三个业务键、失败支值全 JSON null；**「订单不存在」那一支的 `9999` 是 BOM 域独一份的既有码，NEVER 归一成 8999 / 8006**。`F2fBomOrderService.requestTicketRefund` — `service/F2fBomOrderService.java:244` / `:249`
- **只有终态 `SUCCESS` 才回 `SUCCESS`**：支付中心受理（`PROCESSING`）不等于钱已到账，收口要等 `F2fRefundService.reconcileRefund` 回查；`INIT` / `MANUAL` 一并按 `PROCESSING` 上报 —— 对 BOM 来说都是「还没有结论」，**NEVER 映射成 FAILED**（会让设备把在途退款当成失败）。`F2fBomOrderService`（行内注释）— `service/F2fBomOrderService.java:311`
- **`TICKET_STATUS_REFUNDING` 是 `F2F_TICKET.TICKET_STATUS`、不是订单状态** —— 拼写与 `F2fOrderStatus.REFUNDING` 撞车纯属巧合，两者是**不同的取值域**。因此 **NEVER 改成 `F2fOrderStatus.REFUNDING.name()`**：编译器两边都是 String、发现不了，但一旦订单侧改了拼写，票状态会被同步改坏。前缀 `TICKET_` 也是 `F2fOrderStatusArchTest` 区分取值域的唯一依据。`F2fBomOrderService.TICKET_STATUS_REFUNDING` — `service/F2fBomOrderService.java:82`
- **IF5A 三个接口都是透传**（分析与更新逻辑在 ticket-server、HCE 数据回写在 account-server）；**下游失败时 MUST 原样透传 ticket-server 的 retCode / retMsg，NEVER 包成 8999**：2026-09-11 新旧双打实测，同一 cardId 旧服务回 `8004 未注册用户`、新服务当时回 `8999 票卡分析失败[8004]:未注册用户`，设备按 8004 做的分支在新服务上永远匹配不到、已改回透传。（「下游异常 / 无应答」两个分支仍回 8999 —— 那是我方兜底、下游根本没给码。）`F2fHceService.requestCardDataAnalyse` — `service/F2fHceService.java:68`；`requestUpdateCardData` 同款 — `service/F2fHceService.java:109`
- **IF5A-09 回写失败仍回成功**：通知已经落库、回失败只会让 BOM 无意义重推；但审计里的 `OPT_RESULT` 会记成 FAILED、运营端能查出来。`F2fHceService.notiUpdateHceData` — `service/F2fHceService.java:148`

#### 2.2 决策理由

- **BOM 域四处修掉的旧缺陷**：①下单幂等（`bomOptSeq` 落 `DEVICE_SEQ`、由 `UK_F2F_ORDER_DEV_SEQ` 挡重复开单，撞索引时查出已有订单原样返回；旧实现只入库不判重）；②业务结果通知幂等（`UK_F2F_REPORT_IDEM` 挡重复通知，重复的 `FAILED` 不会二次退款；旧实现每次都生成新退款单）；③退款结果不再被忽略（旧 `doRefund` 返回值只打日志、退款失败照样回 `0000`；这里按 `RefundOutcome` 显式区分「拒绝 / 已存在 / 已受理」）；④交易查询不再 NPE（旧 `getBomOrderInfo` 对订单不判空就 `.getRsv2()`）。整个类不带 `@Transactional`：退款链路内有支付中心调用。`F2fBomOrderService`（类注释）— `service/F2fBomOrderService.java:34` / `:46`
- **IF2A-08「订单不在 `REPORTABLE` 里（钱没收到）时只落上报、原样回 `0000`，不动状态、NEVER 退款」的两条实测依据**：①BOM 在一笔支付失败后会固定补发一次 `optResult=FAILED` 作为调用收尾，这是正常话务不是异常，2026-09-11 并跑期间在真实流量里命中 3 次（16:35 / 18:15 / 18:17）；②同日 BOM 全量双打，未支付（`CREATED`）的单子旧返 `0000`、新返 `8999 订单状态不允许上报业务结果`。旧实现除「订单不存在」外根本不看状态，所以**判据是「取反 `REPORTABLE`」而不是枚举「哪些算支付未成功」** —— 曾按 `PAY_FAILED / EXPIRED` 两个状态放行、漏了 `CREATED`。放行上报但 NEVER 走退款分支：钱没收到，退款单会被支付中心拒（`错误的订单号`）并留下 stranded `INIT` 记录，靠 `F2fRefundReconcileJob` 无限重试也收不了口。`F2fBomOrderService.notiBusResult` — `service/F2fBomOrderService.java:167` / `:175`
- **退款提交失败也回成功**：上报与退款单都已落库，`F2fRefundReconcileJob` 会重试；回失败只会让 BOM 无意义重推。`F2fBomOrderService.notiBusResult` — `service/F2fBomOrderService.java:163`
- **单程票退款合成一条链路**：旧实现先无条件写一条退款请求记录（`insertTicketRefundRecord`，该表没有唯一索引），再按 `transType` 分流到三套几乎相同的退款代码，退款成功后把退款单号写回子票表；这里合成「校验 → 提交退款 → 回写票的 `REFUND_NO` 与状态」。`F2fBomOrderService.requestTicketRefund` — `service/F2fBomOrderService.java:246`
- **`transType` 在新表合一后不再用于分流**，仅落库供对账归类（旧实现用它做「购票 / BOM 收款 / 扫码取票」三分流）。`RequestTicketRefundReqDTO` — `api/device/bom/RequestTicketRefundReqDTO.java:8`
- **IF5A-03 补齐了 `updateType` 校验**（票卡分析那条校验了、这条旧实现没校验）—— 更新类型缺失时下游行为不确定、属状态变更型操作、不该放行。（注：controller 层按用户裁决未加该校验，见上方契约条目。）`RequestCardDataUpdateReqDTO` — `api/device/bom/RequestCardDataUpdateReqDTO.java:8`
- **IF5A-01 是纯透传，ITP 只做参数校验与审计留痕**；`providerId` 不在子类重复声明 —— 旧 DTO 在子类又声明了一遍，导致 `TransforUtils.copyBaseParams` 回填公共参数时用表单里的（往往为空的）`providerId` 覆盖掉 bizData 里已解析的值。`RequestCardDataAnalyseReqDTO` — `api/device/bom/RequestCardDataAnalyseReqDTO.java:6` / `:9`
- **HCE 链路两处与旧实现的差异**：①远端错误码不再原样透给 BOM 的那条已被上面「MUST 透传」的实测结论取代（当前只有「下游异常 / 无应答」两支收敛成 8999 + 远端文案）；②状态变更型操作补审计 —— `requestUpdateCardData` 与 `notiUpdateHceData` 都会改票卡数据，旧实现前者完全不落库、后者落一条 `STATUS` 列被静默丢弃的记录，这里统一落到 `F2F_RESULT_REPORT`、`ORDER_NO` 位放 `cardId`（本链路没有订单号）。`F2fHceService`（类注释）— `service/F2fHceService.java:28`
- **HCE 审计的 `ORDER_NO` 位放 `cardId`**，而 `UK_F2F_REPORT_IDEM` 是 (REPORT_TYPE, ORDER_NO)，因此同一张卡的重复通知会被挡住。`F2fHceService.saveReport` — `service/F2fHceService.java:180`
- **BOM 域设备心跳与旧实现的差别是「现在真的落库了」**：旧实现在 controller 里直接 return success、从不记录心跳。`BomOrderController.notiDeviceHeard` — `controller/ci/bom/BomOrderController.java:91`；补上的缺口说明见 `F2fDeviceHeartbeatService` — `service/F2fDeviceHeartbeatService.java:13`
- **IF8A-06 改成 BOM 自己轮询、服务端不再阻塞**：旧实现的 `requestPayment` 在服务端 `Thread.sleep` 轮询本方法最长 180 秒（AGENTS.md §5.2）。`BomOrderController.requestGetPayResult` — `controller/ci/bom/BomOrderController.java:160`
- **付款码支付三处与旧实现的有意差异**：①不再轮询（旧 `BomOrderServiceImpl.getBomPayResult` 在请求线程上 `Thread.sleep` 轮询到 `bom.payTimeOut`，违反「NEVER 在请求线程上做长时间阻塞」；本实现只发一次支付、必要时补一次查询就返回，剩下由设备自己调 `requestPayResult` 收口）；②**对端没答上来时回 `ORDERED` 而不是失败**（付款码已经扫过、钱可能已经扣了，回 FAILED 会让设备当场提示失败并可能重复收款）；③订单从统一的 `F2F_ORDER` 查（旧实现这条 URL 挂在 `/itptvm` 下却去查 `BOM_NO_CASH_ORDER`，TVM 自己下的单在那张表里根本不存在 —— 即 TVM 付款码支付一直查不到订单，新表合一后这个缺陷自然消失）。错误码族是 8999 而不是 2999、**NEVER 统一**；整个类不带 `@Transactional`。`F2fScanPayService`（类注释）— `service/F2fScanPayService.java:30` / `:44` / `:46`
- **IF8A-06 与 TVM 的 `requestPayResult` 是同一件事、只有响应壳不同**（TVM 回 `paymentResult/paymentResultDesc/paymentChannelCode`，BOM 回 `paymentResult/paymentResultDesc/msg`），业务判定完全共用 `resolve`，因此不存在两套状态映射。**问不到时回 `ORDERED` 让 BOM 继续轮询，NEVER 回 FAILED。** `F2fScanPayService.requestGetPayResult` — `service/F2fScanPayService.java:161` / `:168` / 行内 `:202`
- **同步就收到钱时「先落成功的支付流水、再推订单 PAID」，顺序 MUST 保持**；被拒与「返回支付失败」对设备是同一句话、差别只在日志。`F2fScanPayService`（行内注释）— `service/F2fScanPayService.java:142` / `:149`

#### 2.3 陷阱

- **设备侧 `paymentCode` / `paymentVendor` 的命名与实际语义是反的，落库与外发都 MUST 按语义放、NEVER 按名字放。** 设备上送的 `paymentCode` 其实是**支付渠道码**（实测 `03`），`paymentVendor` 其实是**用户付款码**（实测 18 位数字）。旧实现在 `BomOrderServiceImpl:272` → `PayCenterCommon:96,103` 完成这次交叉：设备 `paymentCode` → 网关 `paymentVendor`，设备 `paymentVendor` → 网关 `authCode`。2026-09-10 BOM 售票重放实测：本方法原先照名字直接对应，把 18 位付款码写进 `PAYMENT_VENDOR VARCHAR2(8)`，当场 `ORA-12899`、响应退化成 UUID retCode；即便列宽够，外发报文也会把付款码当渠道码、把 `03` 当付款码，支付中心必然拒付。`F2fScanPayService`（字段交叉说明）— `service/F2fScanPayService.java:246`
- **`markSuccess` 返回 0 或撞唯一键都属正常**（回调先到、或并发第二次扣款尝试），一律幂等吞掉，**NEVER 让这里的异常打断订单状态推进** —— 钱确实收到了。撞键判定走 `F2fDuplicateKey.isConflict(Throwable)` 的 cause 链，**NEVER 退回 `catch (DuplicateKeyException)`**：本模块一旦打开 tracing，观测切面会把异常包一层、按类型 catch 当场失效（AGENTS.md §5.2）。`F2fScanPayService.markPaymentSuccess` — `service/F2fScanPayService.java:214` / `:218`
- **票状态 CAS 返 0 行是重复退款的唯一信号，NEVER 降级成 info**：退款已提交给支付中心（`refundService` 在前面），说明这张票在本次校验之后被别人推走了，最常见是 BOM 对同一张票并发发退款。订单主状态不再参与退款（ADR-D88），票状态是现在仅存的那道 CAS。`F2fBomOrderService`（行内注释）— `service/F2fBomOrderService.java:302`
- **`warnIfConflict` 只告警、不改应答、不拒绝请求**，改一处 MUST 看齐其余三处（TVM / APP / 充值）。`F2fBomOrderService.warnIfConflict` — `service/F2fBomOrderService.java:406`

### 三、APP 域

#### 3.1 契约与判据

- **APP 侧是第四套错误码族**：成功 `0000`，失败按端点分别用 `8001`（下单 / 请求支付信息的参数校验）、`8003`（查询类的参数校验）、`8999`（支付中）、`9999`（service 层通用失败）。**旧实现在同一条链路上 controller 回 8001、service 回 8003，本实现照搬这种不一致** —— APP 侧已按此解析，统一码值属契约变更。`AppResponses`（类注释）— `api/device/app/AppResponses.java:9`
- **IF8A-11 响应的 `signType` 固定 `00`、`sign` 固定空串 —— 即本响应实际不签名**。这是既有形态，改成真签名属契约变更、需与 APP 侧同步。`AppResponses.payInfo` — `api/device/app/AppResponses.java:48`
- **IF8A-18 的 `payDate` 格式随分支不同**：本地已终态时吐 `TBL_TVM_APP_ORDER.PAY_TIME` 列原值（失败单该列多为 **null**；2026-09-13 用真实历史单 `SP20260911185631455135717` 重放实测旧回 null、新曾兜成 `""`）；向支付中心查到失败时才显式给 `""`（`AppOrderServiceImpl:277`）。格式也随分支不同：终态吐 `yyyy-MM-dd HH:mm:ss`、查支付中心吐 `yyyyMMddHHmmss`，由调用方各自传入。`AppResponses.payResult` — `api/device/app/AppResponses.java:63`
- **退款受理 / 退款结果两个接口同一形态**：`refundType` 固定 `00`；`notifyUrl` 只在退款受理响应里出现、传 null 表示不下发该 key。**`refundAmount` 必须序列化成字符串** —— 旧实现整条响应是 `Map<String,String>`、APP 侧拿到的一直是 `"400"` 而不是 `400`；这里入参保持 `Long`（内部都是分）、只在出参处转字符串，**NEVER 直接 put 数值**。`AppResponses.refund` — `api/device/app/AppResponses.java:92` / `:97`
- **`userId` 目前只做非空校验、不做归属校验**（旧实现同样如此），因此任何网络可达方凭 `orderNo` 就能对他人订单发起退款。补归属校验属行为变更（会拒绝掉线上现有的部分请求），**MUST 独立评审**。`RequestAppPayResultReqDTO` — `api/device/app/RequestAppPayResultReqDTO.java:12`
- **IF8A-20 的 `singelTicketNum` 拼写错误是既有契约**（少一个 n），与 TVM 的 `RequestGenSjtOrderReqDTO` 同名同错，**NEVER 更正**。`RequestOrderReqDTO` — `api/device/app/RequestOrderReqDTO.java:8`
- **IF8A-11 的 `payChannelCode` 同时作为支付中心报文的 `paymentVendor`**，旧实现把它在两个位置各传一次、本实现保持一致。`RequestPayInfoReqDTO` — `api/device/app/RequestPayInfoReqDTO.java:8`
- **`appType` 只认 `01`（青岛地铁）**，其余值旧实现回 `9999 appType有误，请输入正确的值`，本实现照搬。`RequestQueryActiveOrderReqDTO` — `api/device/app/RequestQueryActiveOrderReqDTO.java:8`
- **APP 链路无验签、无归属校验**（与旧服务一致）：`userId` 只判非空、不校验订单是否属于该用户。加鉴权属契约变更、需独立评审。`AppOrderController`（类注释）— `controller/ci/app/AppOrderController.java:33`
- **IF8A-11 的 URL 是 `/requestPaymentInfo`** —— 旧实现的 Javadoc 写成 `/ci/app/requestPayInfo`，是注释错误，**以注解为准**。`AppOrderController.requestPaymentInfo` — `controller/ci/app/AppOrderController.java:84`
- **`SP` 前缀的补款单走补款分支**：IF8A-26 建的单在 `SUPPLEMENT_ORDER`、取票单在 `F2F_ORDER`，两张表没有交集。缺这条分流时补款单恒返 `9999 订单号错误`、APP 侧显示「生成订单失败」（2026-09-16 实测，同一笔在 10:52:18 建单成功、20ms 后取支付信息即失败）。**按订单号前缀分流是因为 APP 侧无法改动**：它对两类单调的是同一个 URL、同一份报文。`AppOrderController.requestPaymentInfo` — `controller/ci/app/AppOrderController.java:87`
- **请求退款只校验 `orderNo`、不校验 `userId`**：旧实现这里连 request 判空都没有、也不校验 `userId`，缺 `userId` 时照样回 `0000` 带 `refundResult`（2026-09-11 新旧双打实测：旧返退款报文体、新曾返 `8003 非法参数,userId不能为空`）。曾以「`userId` 要落到退款单 `OPERATOR_ID` 供对账追溯」为由补上该校验，与 `requestUpdateCardData` / BOM `paymentCode` 同属一类，按用户 2026-09-11「不校验」的裁决移除。缺 `userId` 时退款单的 `OPERATOR_ID` 为空、属既有形态，**NEVER 再加回校验**。`AppOrderController.requestRefundTicket` — `controller/ci/app/AppOrderController.java:139`
- **退款结果回调入参是支付中心信封、不是设备信封**；**定位退款单用 `refundNo`（我方退款单号）、不是 `orderNo`** —— 旧实现校验的是 `orderNo` 非空、实际查库却用 `refundNo`，只传 `orderNo` 时能过校验但必然查不到；本实现两者都校验。**不验签**，与旧实现一致。`AppOrderController.receiveRefundResult` — `controller/ci/app/AppOrderController.java:174` / `:176` / `:179`；DTO 侧同款 — `api/device/app/AppRefundNotiResultReqDTO.java:11` / `:15`
- **IF8A-18 `payResult` 只有 `SUCCESS` / `FAIL` 两种取值；问不到结果时回 `8999 支付中`（不是 FAIL），照搬旧口径。** 本地已是终态则短路，只有 `CREATED` / `PAYING` 才去问支付中心。`F2fAppOrderService.requestPayResult` — `service/F2fAppOrderService.java:238` / `:241`；行内注释 `:276` / `:279`
- **IF8A-18 本地终态分支的 `payDate`：有值按 `yyyy-MM-dd HH:mm:ss` 输出、无值返回 null 而不是空串** —— 旧实现原样吐 `PAY_TIME` 列值，失败单该列多为 null。两个 formatter **不是同一个，NEVER 合并**：旧实现在「DB 已是终态」分支里原样返回 `TBL_TVM_APP_ORDER.PAY_TIME` 列值，而该列真实存的就是带分隔符的 `yyyy-MM-dd HH:mm:ss`（2026-09-11 抽查库内四条历史真实单，形如 `2026-09-11 18:52:40`）；只有「向支付中心查到结果」那条分支才用紧凑形态（`AppOrderServiceImpl:263`）。双打实测同一笔已付单旧回 `2026-09-11 20:46:50`、新回 `20260911204649`，据此拆开。`F2fAppOrderService.PAY_DATE_FORMATTER` / `formatPayDate` — `service/F2fAppOrderService.java:108` / `:121`
- **获取已激活取票订单列表：`ticketPrice` 必须序列化成字符串** —— 旧实现的载体是 `AppActiveOrderModel`，该字段声明为 `String`（`AppActiveOrderModel:12`），APP 侧一直收到 `"200"` 而不是 `200`，**NEVER 直接 put 数值**（2026-09-11 双跑对比实测到该类型漂移）。`singelTicketNum` 同理，拼写少一个 t 也是既有契约。`F2fAppOrderService.requestPreActiveOrderList` — `service/F2fAppOrderService.java:312`
- **退款受理响应里的 `notifyUrl` 回吐我方配置的退款结果通知地址**，照搬旧实现（把内部配置回显给 APP，看着奇怪但是既有契约）；取值来自 `f2f.notify.app.refund-notice-url`（旧键 `pay.center.app-refund-notice-url`，集群 env 实测值指向 fep-app 的 `/ci/app/receiveRefundResult`）。**NEVER 复用 `f2f.notify.app.refund-result-url`** —— 那是 `F2fNotifyJob` 出向推送退款结果的地址（旧键 `pay.center.notice-app-refundresult-url`），两个键在旧实现里是不同的值、合并会改变响应内容。`F2fAppOrderService.requestRefundTicket` — `service/F2fAppOrderService.java:345`；配置侧同款 — `channel/app/AppNotifyProperties.java:35` / `:39`
- **状态不允许退款时回 `0000` + `refundResult=FAIL`，不是 `9999`**：旧 `AppOrderServiceImpl.requestRefundTicket:517` 只校验订单存在、**完全不看状态**，直接把未支付单也送去支付中心，被拒后照样回 `0000` 带 `refundResult=FAIL / refundResultDesc=退款失败`（2026-09-11 新旧双打实测）。APP 端解析的是报文体里的 `refundResult`，退化成 `9999` 会让它拿不到结构化结果。因此**响应形态照搬旧实现，但 NEVER 照搬「未支付也真去发起退款」** —— 那会在 `F2F_REFUND` 里留下必然失败的 `INIT` 记录、`F2fRefundReconcileJob` 无限重试也收不了口（与 TVM / BOM 侧同一条裁决）。`F2fAppOrderService.requestRefundTicket` — `service/F2fAppOrderService.java:354`
- **退款结果查询：先按退款单号查、未命中再按原订单号查，两条都要留、NEVER 只保一条。** 入参字段名叫 `orderNo`，但旧实现（`AppOrderServiceImpl:811` 把它赋给局部变量 `refundRrderNo` 后 `selectByRefundNo`）拿它当**退款单号**用。2026-09-11 双打实测证实两侧当时完全互斥：同一笔已付单，旧服务传订单号回「没有找到对应退款记录」、传退款单号回 `0000 FAIL`；新服务反过来。而 `requestRefundTicket` 的响应体里**并不回退款单号**，APP 只能从 `receiveRefundResult` 回调的 `refundNo` 里拿，因此现网 APP 传的一定是退款单号 —— **只按订单号查等于把这个接口对现网打死**。`F2fAppOrderService.requestRefundTicketResult` — `service/F2fAppOrderService.java:410` / `:412`
- **退款结果查询响应里的 `orderNo` 回请求传入的原值**（旧实现即如此，传退款单号就回退款单号），**NEVER 改成回订单号**；**`refundDate` 是 8 位日期（`yyyyMMdd`）** —— 旧实现这里给的是日期不是时间戳，2026-09-11 实测旧回 `20260911`、新曾回 14 位 `20260911204733`，已改回 8 位。`F2fAppOrderService.requestRefundTicketResult` — `service/F2fAppOrderService.java:420`
- **`TRANS_TYPE_APP` 与 `F2fTicketIssueService` 的判断值 MUST 保持一致**（后者用它判断「出票结果要不要通知 APP」）。`F2fAppOrderService.TRANS_TYPE_APP` — `service/F2fAppOrderService.java:73`
- **IF8A-11（补款单分支）的应答形态与取票单逐字一致**（`payChannelCode / paymentInfo / signType / sign`），**APP 侧无需改动**；补款单号前缀常量落单侧与分流侧 **MUST 同源**。`SupplementOrderService.ORDER_NO_PREFIX` / `requestPayInfo` — `service/supplement/SupplementOrderService.java:11` / `:19`
- **运营端 `/page/**` 返回 `ResultVO`（`code/msg/data` 形态），不是设备的 retCode 契约**；**必须先有检索范围**（订单号、支付中心订单号，或完整的下单时间范围）—— `F2F_ORDER` 是按月分区的核心交易表、无条件全扫会直接影响设备链路。`FacePayOrderPageController`（类注释）— `controller/page/FacePayOrderPageController.java:29` / `:32`
- **`payCenterChannelOrderNo`（渠道订单号）也算一种检索范围**：旧 `/page/face-pay/orders` 就支持这一维，2026-09-11 新旧双打实测旧服务在无参时回「请填写订单号、支付中心订单号、渠道订单号，或完整的下单时间范围」，而新服务连这个 `@RequestParam` 都没有、运营后台按渠道订单号查不到单。**NEVER 再把它从入参里去掉。** `FacePayOrderPageController.page` — `controller/page/FacePayOrderPageController.java:68`
- **运营端退款金额只从订单总额算、不信任页面输入**（照搬旧实现的约束，页面只能填退款原因）。`FacePayOrderPageController.requestRefund` — `controller/page/FacePayOrderPageController.java:128`；DTO 侧同款（故意不含退款金额）— `api/page/FacePayRefundRequest.java:6`
- **`AppPartialRefundRequest.refundAmount` 单位是分**，与 `F2F_ORDER.ORDER_AMOUNT` 同单位，**NEVER 改成元** —— 全链路（设备报文、支付中心、`F2F_*` 三张表）都是分。`api/page/AppPartialRefundRequest.java:12`
- **按指定金额退款：`refundAmount` NEVER 退化成退全额** —— 本端点的语义就是「按运营指定的金额退」，把缺失的金额猜成全额是资损路径。`AppOrderPageController.refund` — `controller/page/AppOrderPageController.java:53`
- **运营端订单视图字段直接对齐 `F2F_ORDER` 域模型、不再做旧口径的状态映射或格式变换**：`orderStatus` 存真实枚举值（CREATED / PAYING / PAID / FULFILLED / REFUNDED 等），不再有旧 `status="1"` 这种 ItpStatusEnum 投影；金额为 `Long` 单位分，不再输出旧 `TO_CHAR` 字符串；`singleTicketType` 直接投影 `F2F_ORDER.SINGLE_TICKET_TYPE`；已退款状态由 `refundStatus`（NONE / PARTIAL / SUCCESS）独立承载、与 `orderStatus` 正交（ADR-D88）。**前端消费侧 MUST 同步更新映射逻辑。** `FacePayOrderPageVO`（类注释）— `api/page/FacePayOrderPageVO.java:6`；`orderStatus` 值域见 `:30`、`refundStatus` 见 `:82`

#### 3.2 决策理由

- **APP 域四处修掉的旧缺陷**：①`requestPayResult` 的失败判定字面量写错（旧实现拿支付中心的 `status` 与 `"3"` 比，而支付中心返回的是 `"FAILED"` —— 等于「支付失败」这一支永远不成立、订单永久停在支付中；这里走 `PayCenterStatus` 枚举）；②退款结果查询不再把订单号当退款单号用（旧实现变量名叫 `refundRrderNo`、值是 `orderNo`，却去 `selectByRefundNo` 查，只有恰好两个号相同才查得到）；③退款通知改成落库 + 定时投递（旧实现在业务线程池里同步 push APP、失败才落库交给重试任务）；④退款受理不再无条件返回成功（旧 `doRefund` 整段 catch 后回 `9999 请求异常`，但退款单可能已经落库、支付中心可能已经受理，APP 侧无法区分；这里按 `RefundOutcome` 区分拒绝 / 已存在 / 已受理）。整个类不带 `@Transactional`：链路里有支付中心调用。`F2fAppOrderService`（类注释）— `service/F2fAppOrderService.java:45` / `:62`
- **IF8A-20 下单只落库、不调支付中心** —— APP 随后再调 `requestPayInfo` 换取支付信息。`F2fAppOrderService.requestOrder` — `service/F2fAppOrderService.java:166`
- **IF8A-11 必须在事务外**（中间那次 `execute` 是网络调用）；只有 `CREATED` 才允许换支付信息（白名单）—— 旧实现的判定是 `PAY_STATUS != "0"` 即拒绝、等价，但这里写成显式白名单。**`rejectTransition` 传 null：APP 被拒后订单 MUST 留在 `CREATED`，乘客可换支付通道重来** —— 这与 TVM / BOM 一次被拒即置 `PAY_FAILED` 是**有意的差别**，重构前它只体现为「这里少两行」。`F2fAppOrderService.requestPayInfo` — `service/F2fAppOrderService.java:191` / `:193` / 行内 `:220`
- **`warnIfConflict` 刻意只告警、不改应答**（返 0 行的常见含义是并发回调已把订单推成 PAID，而调用方仍会对 APP 回 FAIL —— 这个口径分歧原本没有任何痕迹），与 `F2fTvmOrderService.warnIfConflict` 同一口径，改一处 MUST 看齐另外两处。**「已收款但订单没推到 PAID」的 ERROR 上报已收口到 `F2fPayCenterFlow.markPaidAndReport`、本类不再自留副本**；但 `warnIfConflict` **保留** —— 它服务的是**退款域**的 `REFUNDING` / `REFUNDED` 推进、与支付中心答复无关，**NEVER 一起挪进 `F2fPayCenterFlow`**（那会把两个取值域的冲突口径混成一个）。`F2fAppOrderService.warnIfConflict` — `service/F2fAppOrderService.java:285` / `:288` / `:301`
- **`INIT` / `PROCESSING` 时不再同步问支付中心**：`F2fRefundReconcileJob` 已经在扫表收口，查询接口再发一次网络调用只会让 APP 侧的响应时间随支付中心抖动，且两处并发更新同一行。`F2fAppOrderService.requestRefundTicketResult` — `service/F2fAppOrderService.java:429`
- **`MANUAL` 允许被回调救回**：转人工只表示我方放弃了自动收口，回调带来的是支付中心的权威结论，此时 MUST 接受并推进到 SUCCESS / FAILED。因此前置状态白名单里带上 MANUAL，而终态短路只认 SUCCESS / FAILED。`F2fAppOrderService.receiveRefundResult` — `service/F2fAppOrderService.java:471`
- **退款结果通知入队失败只记日志**：退款状态已落库，通知丢了可以由运营端补发，但退款结果不能因为通知失败而回滚。报文体 6 个 key 逐字照搬旧 `NoticeAppRefundDTO`。`F2fAppOrderService.enqueueRefundNotify` — `service/F2fAppOrderService.java:514` / `:516`
- **`markPaymentSuccess` 返回 0 或撞 `UK_F2F_PAY_SUCCESS` 都按幂等吞掉，NEVER 打断订单状态推进。** `F2fAppOrderService` — `service/F2fAppOrderService.java:535`
- **旧 `CollectPayController`（`/ci/app/requestPay` 等四条）整个文件被注释掉、运行时并不存在，因此本服务不实现它们。** `AppOrderController`（类注释）— `controller/ci/app/AppOrderController.java:26`
- **`FacePayRefundRequest` 放在 `api.page` 而不是 `controller.page`**：本模块的入向契约一律归 `api/<渠道>`（`api/device/{tvm,bom,app}`、`api/paycenter`），运营后台是第四个渠道；`controller` 包只放 `@RestController`。`api/page/FacePayRefundRequest.java:9`
- **`AppPartialRefundRequest` 同样只在业务模块内、不在 `model` 模块** —— 它是运营后台与本服务之间的内部契约、不经 `parseBizData`、不属于对外契约。与旧类的差异：旧类只有 `refundAmount`，本类补 `refundReason` 与 `operatorId`（退款单要落 `F2F_REFUND.OPERATOR_ID` 才能查「谁点的」，旧实现没有这个列可落）。`api/page/AppPartialRefundRequest.java:4` / `:8`
- **为什么不沿用 collect-pay 的 `FacePayOrderPageView`**：那个类是旧表的单行结构投影（旧单表把支付中心单号、退款单号都塞在一行里），拆成 `F2F_ORDER + F2F_PAYMENT + F2F_REFUND` 之后字段来源和口径都变了，硬套旧 View 只会让两套命名在前端打架。`api/page/FacePayOrderPageVO.java:19`
- **`/page/app/orders/{orderNo}/refund` 与 `/page/face-pay/orders/{orderNo}/refund` 的关系是「按指定金额」与「退剩余全额」两种入口，共用同一套闸门与同一张 `F2F_REFUND`**、不是两条独立链路，差异只在金额来源。存在原因：APP 取票订单已随 2026-09-15 切流落到 `F2F_ORDER`（`BIZ_TYPE='03'`），旧端点操作的 `TBL_TVM_APP_ORDER` / `TBL_APP_ORDER_REFUND` 不再有新数据，运营后台照旧调这个 URL 时旧模块只会答「未找到订单」—— 不是没退成，是根本查不到那张单。`AppOrderPageController`（类注释）— `controller/page/AppOrderPageController.java:19` / `:24`
- **请求体可空校验放 controller、金额有效性校验放 service**：前者是「报文有没有」，后者是「业务允不允许」；**NEVER 把可退余额判断挪到 controller** —— 那需要读订单与已退汇总、属业务逻辑（AGENTS.md §3.3）。`AppOrderPageController.refund` — `controller/page/AppOrderPageController.java:48`

#### 3.3 陷阱

- **`/page/**` 与 `/page/app/orders/**` 都没有鉴权**（与旧实现一致），靠网络隔离保护；`/page/face-pay/orders/**` 能发起真实退款，**上线前 MUST 确认该路径未对外暴露**。`FacePayOrderPageController` — `controller/page/FacePayOrderPageController.java:40`
- **`/page/app/orders/{orderNo}/refund` 比查询类端点敏感得多 —— 它出钱**：任何网络可达方按订单号 POST 一次即可发起退款；旧实现的类注释也自记了同一条（原文「上线前 MUST 补鉴权」），**上线前 MUST 一并补**。`AppOrderPageController` — `controller/page/AppOrderPageController.java:29`
- **`orderDate` 的 `yyyyMMddHHmmss`、null 进空串出**：旧实现的 `orderDate` 是把 `yyyy-MM-dd HH:mm:ss` 里的 `-`、空格、`:` 逐个替换掉得到的，结果与本格式一致。`F2fAppOrderService`（格式化方法）— `service/F2fAppOrderService.java:606`
- **APP 预下单没开同步支付状态判定，构造上不可能收到「同步已收款」分支**（充值拉码同款，见 `F2fTopupService` 行内 `service/F2fTopupService.java:167`）。`F2fAppOrderService`（行内注释）— `service/F2fAppOrderService.java:232`
- **退款汇总三列重算：NEVER 改回「把订单推成 REFUNDED」**（ADR-D88）—— 退款与支付/履约主状态正交，主状态推成 REFUNDED 会丢掉「退款前出过票」这个事实，部分退更是表达不了。重算式幂等，失败分支也要算（金额可能因别的来源已变）。`F2fAppOrderService.receiveRefundResult`（行内注释）— `service/F2fAppOrderService.java:502`

### 四、补款（IF8A-26 / `SUPPLEMENT_ORDER`）

#### 4.1 契约与判据

- **`SUPPLEMENT_ORDER.payStatus` 取值**：`INIT` 已下单待支付 / `PROCESSING` 已发起支付 / `SUCCESS` 支付成功 / `FAIL` 支付失败 / `CLOSED` 已关闭。**与 `GATE_TXN_PAY.DEBIT_STATUS` 是两套独立状态**：本状态描述补款单自身，原订单的结清与否**始终以 `GATE_TXN_PAY.DEBIT_STATUS` 为权威口径**。`SupplementOrder` — `entity/SupplementOrder.java:10`
- **`SupplementOrderItem.settleStatus` 取值**：`PENDING` 待结清 / `SETTLED` 原订单已收敛为 SUCCESS / `FAILED` 原订单收敛失败需人工核对。这只是补款侧的对齐结果，**NEVER 用它替代 `GATE_TXN_PAY.DEBIT_STATUS` 判断一笔乘车订单是否已结清**。`entity/SupplementOrderItem.java:8`
- **`activeOrigOrderNo` 不是业务字段**，唯一用途是让「同一笔欠费被两张补款单同时覆盖」在 DB 层就 INSERT 失败（由 `UK_SUPPLEMENT_ITEM_ACTIVE` 承载），置 NULL 即释放独占（Oracle 唯一索引不约束全 NULL 行）。`entity/SupplementOrderItem.java:12`
- **补款单状态推进只允许从 `INIT` / `PROCESSING` 更新为目标状态**：白名单而非黑名单 —— `SUCCESS` / `FAIL` / `CLOSED` 已是终态，重复回调 **NEVER 改写**。`SupplementOrderMapper.updateStatus` — `mapper/SupplementOrderMapper.java:27`
- **回写预下单结果的 WHERE 只认 `INIT`**，同一笔重复调预下单时第二次返回 0 行；**上层收到 0 行 MUST 改为回放库里已有的 `PAYMENT_INFO`，NEVER 再向支付中心预下单一次**。`SupplementOrderMapper.updatePrepayResult` — `mapper/SupplementOrderMapper.java:48`
- **已作废（CLOSED）或已判失败（FAIL）却又收到钱时的强制销账，白名单只放这两个状态**：作废 / 关单 / 判失败与乘客付款之间存在毫秒级竞态，钱既然收了就 MUST 用来清欠费。**三个渠道凭据参数 MUST 一起传** —— 分两步写会留下「状态已 SUCCESS 但商户单号为空」的行。`SupplementOrderMapper`（强制销账语句）— `mapper/SupplementOrderMapper.java:66`
- **收敛任务扫描会把 `closedLookbackHours` 小时内的 `CLOSED` / `FAIL` 单一起捞回来**，专为「作废/关单/判失败后乘客仍完成了支付」兜底。`SupplementOrderMapper`（扫描语句）— `mapper/SupplementOrderMapper.java:77`；XML 侧 — `SupplementOrderMapper.xml:178`
- **收敛原过闸订单只能走 RPC**：`GateTxnPayClient.convergeDebitStatusForSupplement` → `POST /internal/gate-txn-pay/debit/converge`，对端语句名 `convergeDebitStatusForSupplement`、白名单含 `FAIL`、行为与已删掉那条本地 UPDATE 逐笔一致。`SupplementPayCenterFlow` — `service/supplement/SupplementPayCenterFlow.java:41`
- **收敛的三支判据**（一次 RPC 拿全）：`converged=true` → 本次把原订单推进到 SUCCESS、明细标 SETTLED；`converged=false` 且原订单已 `SUCCESS` → 已被先到的补款单结清，钱已实收而行程早已平账，本单是**重复支付待退款**、明细标 FAILED 留人工/对账闭环，**NEVER 当幂等成功标 SETTLED**（那等于把一笔该退的钱藏起来）；其余（含对端返非 0000）→ 业务拒绝、重试无用、明细标 FAILED 留人工核对。`SupplementPayCenterFlow.converge*` — `service/supplement/SupplementPayCenterFlow.java:202`
- **异常（网络不可达）一律往外抛、不改本地状态**：明细留在未结清态，由 `SupplementOrderCloseProcessor.converge`（cron `0 */5 * * * ?`）下一轮重入。**NEVER 在这里 catch 后把明细标成 FAILED** —— 那会把「可重试」写成终态。`SupplementPayCenterFlow` — `service/supplement/SupplementPayCenterFlow.java:213`
- **补款回调地址 MUST 用补款专用的**：通用 `payNoticeUrl` 指向 `/itptvm/ci/tvm/payNotice`、那里只查 `F2F_ORDER`，补款回调恒返 `2001 订单不存在`、被支付中心反复重推，状态只能靠 `converge` 兜（延迟 5 分钟）。见 ADR-D103。`SupplementPayCenterFlow`（行内注释）— `service/supplement/SupplementPayCenterFlow.java:72`
- **补款回调 Controller 的类级 `@RequestMapping` 已刻意去掉**：两条路径不共享前缀、MUST 各写全路径 —— `/ci/facePay/paycenter/payNotice`（原有内网路径，**NEVER 删**）与 `/itpbom/ci/bom/supplementPayNotice`（对外别名，复用 `fep-app-vr` 已有的 `/itpbom/` 前缀、路径原样保留，因此**不需要改网关**；支付中心的 `notifyUrl` 送的就是它）。**两条走同一个方法体，NEVER 复制一份实现。** `SupplementPayNoticeController`（类注释）— `controller/paycenter/SupplementPayNoticeController.java:15`
- **`effectiveSupplementNoticeUrl()`：配了就用补款专用的、没配回落到通用的；NEVER 改成直接返回 `supplementNoticeUrl`** —— 回落分支存在的意义是「env 未注入时行为与改动前完全一致」，而网关文档该字段是「否 / 不传取默认」、送空串的行为未实测。`PayCenterProperties.effectiveSupplementNoticeUrl` / `supplementNoticeUrl` — `channel/paycenter/PayCenterProperties.java:197` / `:57`
- **`buildPayRequest` 两参重载的键序与单参重载完全一致、`notifyUrl` 仍是最后一个** —— 键序进待签串，这里只换值不换位置、签名口径未变。**NEVER 把这个 put 挪位置或提前。** `PayCenterMessageFactory.buildPayRequest`（两参重载）— `channel/paycenter/PayCenterMessageFactory.java:38` / `:44`
- **`GATE_TXN_PAY` 只读实体只保留补款链路所需的 9 个字段**（gate-txn-pay 版本有 46 列）；若未来新增其它使用场景，可按需扩列，但 **MUST 先评估是否应该新建专用实体而不是继续复用**。`entity/GateTxnPay.java:6`
- **`GateTxnPayMapper.selectByOrderNos` 不带 `TXN_DATE`、走不到分区裁剪**，只命中 `UK_GATE_TXN_PAY_ORDER_NO` 的前缀列；**调用方 MUST 限制列表长度**（Oracle IN 列表上限 1000）。`mapper/GateTxnPayMapper.java:28`；XML 侧 — `GateTxnPayMapper.xml:33`

#### 4.2 决策理由

- **`@EnableRpcGateTxnPay` 是补款收敛所需**（2026-09-16 新增）：`SupplementPayCenterFlow` 注入 `GateTxnPayClient` 调 `POST /internal/gate-txn-pay/debit/converge` 收敛原过闸订单 —— 那张表的 owner 是 gate-txn-pay-server，本模块此前是直写、已改 RPC。同样 **MUST 配 `service.gateTxnPay.url`**，缺键会落到默认服务名、解析不到，表现为**补款支付成功但明细永远结不清**。`FacePayServer`（类注释）— `face-pay-server/src/main/java/com/chinasofti/huateng/FacePayServer.java:28`
- **删掉 `GateTxnPayMapper.convergeDebitStatus` 的理由**：`GATE_TXN_PAY` 的 owner 是 gate-txn-pay-server，跨域直写它的热路径表违反「热路径写入定 owner」判据 —— 同一张表两个模块各持一份 UPDATE，白名单一旦漂移就是资金账不平，而编译与单测都发现不了。**NEVER 在本接口 / 本 XML 里加回任何 `GATE_TXN_PAY` 的写方法。** 剩下那个 select 仍是**跨域读**（共享 Oracle schema），属有意保留的现状；若未来拆库，它也 MUST 改 RPC。`mapper/GateTxnPayMapper.java:14` / `:22`；XML 侧 — `GateTxnPayMapper.xml:5`
- **PayCenter 直连架构下的字段与方法精简**：CollectPay 链路的 outbox 四列（`saleSyncStatus` / `saleSyncRetryCount` / `saleSyncTime` / `saleSyncResult`）已删除 —— PayCenter 预下单在单次 HTTP 请求内完成、不需要「先落本地、再补偿投递」的 outbox；预下单直接写 `PAYMENT_INFO` / `MERCHANT_ORDER_NO` / `PAY_CHANNEL_CODE` 三列，失败则回 `INIT` 并记录 `REMARK`、后续可重试。mapper 侧同批移除 `markSaleSyncSuccess/Failed/Rejected`、`selectPendingSaleSync`。`entity/SupplementOrder.java:15`；`mapper/SupplementOrderMapper.java:13`；XML 侧 — `SupplementOrderMapper.xml:5`
- **本地落单无独占设计**：同一行程单允许同时挂在多张补款单下、先到先得 —— 谁先支付成功谁就收敛行程单，后到的重复支付由 `settleSuccess` 标 FAILED 记「重复支付待退款」。明细的 `ACTIVE_ORIG_ORDER_NO` 已废弃（恒写 NULL），`UK_SUPPLEMENT_ITEM_ACTIVE` 因此永不触发，撞键只可能来自主表 `ORDER_NO` 唯一索引（重试幂等）。`SupplementOrderLocalWriter.write` — `service/supplement/SupplementOrderLocalWriter.java:40`
- **通道由 IF8A-26 按原订单的 `PAYMENT_VENDOR` 定、支付参数也是按它向支付中心换的；换通道 MUST 重新走 IF8A-26 建新单，NEVER 在 `requestPayInfo` 里按 APP 传入的通道重下** —— `PROCESSING` 时支付中心已挂待支付单，再下一次等于同一笔欠费有两份可付参数。`SupplementOrderServiceImpl`（行内注释）— `service/supplement/SupplementOrderServiceImpl.java:200`
- **`INIT` 分支补一次预下单的理由**：落到那里说明下单那次支付中心不可达（IF8A-26 的 `Unreachable` 分支回 `0000` + `INIT`）；`updatePrepayResult` 的 WHERE 只认 `INIT`、天然挡住重复预下单。`SupplementOrderServiceImpl`（行内注释）— `service/supplement/SupplementOrderServiceImpl.java:212`
- **`SUPPLEMENT_ORDER.TXN_DATE` 是 NOT NULL、取补款发生日（`yyyyMMdd`）。** `SupplementOrderServiceImpl`（行内注释）— `service/supplement/SupplementOrderServiceImpl.java:141`

#### 4.3 陷阱

- **`ROWNUM` 截断 MUST 写在外层内联视图上**：Oracle 的 `ROWNUM` 在 `ORDER BY` 之前求值，同层写就变成「随便取 N 行再排序」。`SupplementOrderMapper.xml:178`
- **无独占后同一行程单可能被多张补款单各付成功一次**：钱已实收但行程早已结清，本单属重复扣款、明细标 FAILED 并记「重复支付待退款」，由对账/人工退款闭环。`SupplementPayCenterFlow`（行内注释）— `service/supplement/SupplementPayCenterFlow.java:233`
### 五、退款

#### 5.1 契约与判据

- **本模块所有退款入口都必须走 `F2fRefundService`，共 7 个来源**（见 `CK_F2F_REFUND_SOURCE`）：`TAKE_TICKET_FAIL` 出票故障自动退、`BOM_ORIGINAL` 单程票原路退、`APP_REQUEST` 用户主动退、`DAILY_BATCH` 每日批量退未取票、`TOPUP_FAIL` 充值失败退、`PAGE_MANUAL` 运营端手工退、`TVM_REQUEST` 设备侧 `requestRefund` 发起。`F2fRefundService`（类注释）— `service/F2fRefundService.java:23`；来源清单见 `entity/F2fRefund.java:38`
- **四条不可违反的约束**：①防重靠唯一函数索引 `UK_F2F_REFUND_IDEM (ORIG_ORDER_NO, NVL(TICKET_LOGIC_NUM,'#WHOLE#'), REFUND_SOURCE)`、**不靠先查后插**（并发下两个线程都查不到就都插进去、等于重复退款 —— 这是旧实现的最高优先级缺陷，`TvmCommonServiceImpl.doRefund` 无任何幂等键）；②**对端没答上来 NEVER 置 FAILED**（退款请求发出去了、钱可能已经退了，此时置 FAILED 会让补偿任务再退一次），留在 `INIT` 由 `reconcileRefund` 查询收口 —— 旧实现的 `doRefund` 反过来、**不管对端答什么都 `return true`**，调用方据此写「已退款」标记、退款失败也被记成已退；③整个类不带 `@Transactional`，顺序固定「INSERT 退款单 → 事务外调支付中心 → UPDATE 结果」；④状态推进走白名单、`fromStatuses` 一律显式传。`service/F2fRefundService.java:26`
- **退款状态机**：`INIT --受理成功--> PROCESSING --查询确认--> SUCCESS`；`PROCESSING --查询确认失败--> FAILED`；`PROCESSING --次数用尽或超时间窗仍查不到--> MANUAL`；`INIT --对端未答--> 留 INIT`，扫表按指数退避重查（不置 FAILED）。`service/F2fRefundService.java:40`
- **`MANUAL` NEVER 当成「退款失败」**：钱到底退没退是未知的，这个状态的含义是「本系统已放弃自动判定，需要人工查支付中心或走对账」。`F2fRefundService.STATUS_MANUAL` — `service/F2fRefundService.java:96`
- **返回值 MUST 检查**：`RefundOutcome.alreadyExisted()` 为 true 表示这笔「原订单 + 票 + 来源」已经退过，调用方 **NEVER 再重复触发下游动作**（如再改一次票状态）。旧实现的 `doRefund` 返回 boolean 且恒为 true、调用方无从区分，结果是重复退款与「失败也标记成已退」同时存在。`F2fRefundService.refund` — `service/F2fRefundService.java:154`；`RefundOutcome`（类注释）— `service/RefundOutcome.java:6`
- **`RefundOutcome` 的「已受理」不等于「退款成功」**：钱到账要等 `reconcileRefund` 查询确认；对设备回「已受理」是既有契约（旧实现连受理与否都不看、一律回成功）。`service/RefundOutcome.java:32`
- **`reconcileRefund` 只发 refundQuery、NEVER 重发退款**：重发是资金动作、必须另有严格限次的入口。`INIT` 的单也要查 —— 「对端未答」不等于「没受理」，可能请求已到达。`F2fRefundService.reconcileRefund` — `service/F2fRefundService.java:223` / `:225`
- **退避与放弃**：间隔从 `backoffBaseSeconds`（默认 300 秒）起翻倍、封顶 `backoffMaxSeconds`（默认 3600 秒），即 5 / 10 / 20 / 40 分钟后转为每小时一次；放弃条件两个、**谁先到算谁** —— 查询次数达到 `maxQueryTimes`（默认 30），或 `REQUEST_TMS` 距今超过 `giveUpAfterHours`（默认 24 小时 = 一个支付中心对账周期）。按该退避序列约 27 次覆盖满 24 小时，因此正常情况下时间窗先到、次数上限是兜底。移位次数钳到 20 以内，避免 `long` 溢出成负数。`service/F2fRefundService.java:49`；`nextQueryTms` — `service/F2fRefundService.java:337`
- **设备侧退款响应契约照搬旧实现**：成功只回 `retCode=0000 / retMsg=成功`（**不回退款单号**），失败回 `retCode=9999`，**但「订单不存在」是 `2002`**（旧实现在这一支走参数校验族）。**「已受理」不等于「已退成功」** —— 钱到账要等 `reconcileRefund` 收口，但设备契约就是这样、**NEVER 改**。`F2fDeviceRefundService`（类注释）— `service/F2fDeviceRefundService.java:38`
- **只有终态 `SUCCESS` 才回 `SUCCESS`**；其余非终态（`INIT` / `MANUAL`）一律按 `PROCESSING` 上报 —— 对设备来说都是「还没有结论」，**NEVER 把它们映射成 `FAILED`**（那会让设备把一笔仍在处理中的退款当成失败）。`F2fDeviceRefundService.toRefundResult` — `service/F2fDeviceRefundService.java:121` / `:123`
- **运营端整单退款四道闸门，缺一不可**：①同来源查重（`findPageRefund`，最终由 `UK_F2F_REFUND_IDEM` 兜底）；②**跨来源在途拦截**（`countUnsettledRefunds`）—— 唯一索引只挡**同来源**，设备退款 / 批量退款 / APP 退款各是独立的一行；原实现靠「订单已是 `REFUNDING` 就不在 `REFUNDABLE` 里」顺带挡住了跨来源重复退，主状态不再变化后**那道防线消失了**，MUST 由这一步接替；`INIT` / `PROCESSING` / `MANUAL` 都算未收口，**`MANUAL` 尤其 NEVER 放行**（语义是「退没退未知，等人工查」）；③主状态在 `REFUNDABLE` 白名单（`PAID` / `FULFILLED` / `FULFILL_FAILED`）；④可退金额 = `ORDER_AMOUNT - REFUND_AMOUNT`，退的是**剩余金额**而非订单总额（原实现恒退总额，在已有部分退成功的订单上会超额退款；已退满则直接拒绝）。`F2fPageRefundService.refundWholeOrder` — `service/F2fPageRefundService.java:78`
- **运营端四条对外表现逐字对齐旧 `FacePayOrderPageController.requestRefund`**（2026-09-11 双跑对比后返工）：①成功时 `data` 是 `{retCode:"0000", retMsg:"成功"}` —— 旧实现把 `tvmOrderPreService.requestRefund` 的原始 JSON 直接 `ResultMapper.ok` 出去、运营后台前端按 `data.retCode` 判成功，**NEVER 改成业务字段对象**（否则前端拿到 `undefined` 会判失败），`refundNo` 等只进日志；②状态不可退的文案是「仅支付成功的订单可以退款」、**不带内部状态名**；③已退过的文案是「该订单已发起退款，退款单号：xxx」；④**查重仍排在状态校验之前** —— 退款不再推进 `ORDER_STATUS`（ADR-D88），第二次调用时订单仍是「支付成功」、两种顺序都能落到查重分支，但顺序保持不变才能让「已发起退款」这条文案优先于状态文案。`F2fPageRefundService.refundWholeOrder` — `service/F2fPageRefundService.java:64`
- **按指定金额退款与整单退款完全共用四道闸门，唯一差别是金额来自入参**；**同一订单第二次调用会被判「已发起退款」而拒绝，即使金额不同** —— 唯一索引挡住的正是「同一页面对同一单退两次」这类资损。**NEVER 为了「支持多次部分退」偷偷去掉这道查重**（那等于把旧实现的资损缺陷搬过来）；确有分次退款需求时 MUST 先定「按什么键幂等」，再改索引 + 迁移脚本。金额上界按剩余可退金额校验、超额直接拒绝。`F2fPageRefundService.refundByAmount` — `service/F2fPageRefundService.java:100` / `:104`
- **`F2fOrderRefundStatus` 与 `F2fOrderStatus` 正交**（ADR-D88）：退款 **NEVER 改 `ORDER_STATUS`**，只改 `REFUND_STATUS` / `REFUND_AMOUNT` / `LAST_REFUND_TMS` 这三列。参考实现是 `PAY_TXN_DETAIL`（`PAY_STATUS` 与 `REFUND_STATUS` 并列、退款从不覆盖支付主状态）。**本枚举不是权威值域，DDL 的 `CK_F2F_ORDER_REFUND_STATUS` 才是**（`f2f-schema.sql` 与 `f2f-order-refund-summary-migration.sql`），加取值 MUST 同时改那条约束，只改枚举会在写入时报 ORA-02290。`domain/F2fOrderRefundStatus.java:4` / `:14`
- **`F2fOrderRefundStatus` 没有「退款中」这一档**：本枚举描述的是**已收口的结果**，由 `F2fOrderMapper.updateRefundSummary` 从 `F2F_REFUND` 里 `REFUND_STATUS='SUCCESS'` 的行重算而来。在途退款的存在性 MUST 查 `countUnsettledRefunds`，**NEVER 在这里加 `REFUNDING`** —— 那会把「在途」和「已退成」混进同一个字段、退回被本次改造废弃的旧形态。`domain/F2fOrderRefundStatus.java:18`
- **`F2F_ORDER` 上退款汇总三列是 `F2F_REFUND` 的投影，唯一写入方是 `F2fOrderMapper.updateRefundSummary`（重算式），NEVER 在别处单独 UPDATE。** `entity/F2fOrder.java:45`
- **`F2F_REFUND` 防重复退款的核心是唯一函数索引 `UK_F2F_REFUND_IDEM`**：整单退时 `ticketLogicNum` 为空、索引用 `#WHOLE#` 占位，避免多笔整单退绕过约束。因此写入 MUST 直接 INSERT、**NEVER 先查后插**。`entity/F2fRefund.java:11`
- **`NEXT_QUERY_TMS` 是扫表谓词（`IDX_F2F_REFUND_SCAN`）；扫表谓词里 NEVER 再加 `RETRY_TIMES` 上限** —— 次数上限在 application 层判定并显式置 `MANUAL`，写进 SQL 会让次数用尽的单直接从扫描结果消失、停在非终态无人管。`entity/F2fRefund.java:81`；mapper 侧同款 — `mapper/F2fRefundMapper.java:27`、`F2fRefundMapper.xml:127`
- **`RefundCommand` 用 record 是因为它构造后即不可变** —— 退款金额与幂等三要素在链路中途被改写是最难查的一类缺陷。`ticketLogicNum` 为空即整单退、对应唯一索引里的 `#WHOLE#` 占位；`payCenterOrderNo` 要求调用方从 `F2F_PAYMENT.PAY_CENTER_ORDER_NO` 取真值（旧实现 `BomOrderServiceImpl:640` 硬编码空串、留着「根据实际情况填写」的注释）。`service/RefundCommand.java:9` / `:14` / `:24`

#### 5.2 决策理由

- **次数上限 MUST 在 application 层判定、NEVER 写进扫表谓词**：写进 SQL 的 `RETRY_TIMES < N` 一旦用尽，那笔单直接从扫描结果里消失、状态停在 INIT / PROCESSING、无告警无人工入口（原实现「固定 60 秒 × 20 次」就是这样在 20 分钟内静默丢单的）；放在 application 层则会显式置 `MANUAL` 并打 ERROR。`REQUEST_TMS` 为空时只按次数判、不因缺时间戳就永不放弃。`F2fRefundService`（退避与放弃说明 / `giveUpIfExhausted`）— `service/F2fRefundService.java:57` / `:303` / `:310`
- **`submitToPayCenter` 三个分支**：受理成功置 `PROCESSING` 等收口；对端未答或业务失败**留在 INIT** 并累加重试次数 —— **NEVER 在这里置 FAILED**。`F2fRefundService.submitToPayCenter` — `service/F2fRefundService.java:196` / `:198`
- **退款汇总重算式、不是累加式**：`updateRefundSummary` 直接从 `F2F_REFUND` 里 `REFUND_STATUS='SUCCESS'` 的行 `SUM` 出金额，因此执行 N 次结果相同、天然幂等，不需要 CAS、也不需要前置状态白名单。累加写法（`REFUND_AMOUNT = REFUND_AMOUNT + ?`）在并发或补跑下会算出偏小 / 虚高的值，虚高之后真实退款会被误判成超额 —— 那个坑记在 `PayTxnDetailMapper.xml` 的注释里，**NEVER 改回累加**。返回值只进日志、**NEVER 拿它判断退款成败**（0 行只说明订单号对不上，而退款单的终态已经落库了）。`F2fRefundService.refreshOrderRefundSummary` — `service/F2fRefundService.java:279` / `:287`；mapper 侧 — `mapper/F2fOrderMapper.java:201` / `:205`、`F2fOrderMapper.xml:390`
- **`findExistingRefund` 按 `(ticketLogicNum, refundSource)` 在同一原订单的退款单里定位** —— 这两列加上原订单号正是 `UK_F2F_REFUND_IDEM` 的三要素；`ticketLogicNum` 为空表示整单退、索引里用 `#WHOLE#` 占位，这里用 `Objects.equals` 对齐 null 语义。`F2fRefundService.findExistingRefund` — `service/F2fRefundService.java:359`
- **`RefundCommand` 的参数自检在落库前拦住非法值，而不是等 Oracle 的 CHECK 约束抛异常** —— 约束抛出来的是 `DataIntegrityViolationException`，与「已退过」的 `DuplicateKeyException` 混在一起后无法区分处理。`service/RefundCommand.java:49`
- **设备侧退款相对旧实现补上的三道校验**：①订单必须已支付（白名单 `PAID / FULFILLED / FULFILL_FAILED`）—— 旧实现只在 TVM 购票分支里校验了 `STATUS='1'`、充值分支同样校验，但 **BOM（transType=04）分支根本不存在**、直接回「交易类型不明确」；②退款金额不得超过订单金额（旧实现 `Integer.valueOf(refundAmt)` 裸转换、不校验上限，设备传多少就退多少）；③金额非数字时按 9999 拒绝，而不是抛异常退化成 UUID retCode。`F2fDeviceRefundService`（类注释）— `service/F2fDeviceRefundService.java:21`
- **2026-09-15 起 `F2fDeviceRefundService` 不再推进 `ORDER_STATUS`**（ADR-D88）：收口只写 `F2F_REFUND` + 订单上的退款汇总三列。原先靠「CAS 到 `REFUNDING` 返 0 行」顺带识别重复退款，那条信号消失后由两处接替 —— **跨来源在途退款**用 `countUnsettledRefunds` 显式拦（同来源仍由 `UK_F2F_REFUND_IDEM` 兜底），**超额退款**用「订单金额 − 已退金额」判据拦。**NEVER 把 CAS 加回来** —— 主状态不再有 `REFUNDING` 这条边，加回去只会恒返 0 行、刷满假告警。`service/F2fDeviceRefundService.java:31`
- **运营端退款与旧实现的差异**：①「已退过」的判定不再看 `RSV2` 是否非空（旧实现用订单表的 `RSV2` 复用字段存退款单号来判重，这依赖退款成功时一定回写成功；现在直接查 `F2F_REFUND`、并最终由 `UK_F2F_REFUND_IDEM` 兜底）；②退款结果不再靠 `retCode` 字符串判断（`RefundOutcome` 显式区分「被拒绝」「已存在」「已受理」，运营端能看出是重复点击还是真的拒绝）。不带 `@Transactional`：`F2fRefundService.refund` 内有支付中心调用。`F2fPageRefundService`（类注释）— `service/F2fPageRefundService.java:23` / `:33`
- **`F2fOrderRefundStatus` 为什么必须与主状态正交**：把退款塞进主状态机后，一笔订单的「钱收了没」「票出了没」「退了多少」三件事被压成一个字段，于是 `FULFILLED` 的订单一退款就丢掉了「已出票」这个事实，运营端与设备侧再也分不清「退款前出过票」和「从未出票」；部分退更是根本表达不了。`domain/F2fOrderRefundStatus.java:9`
- **`countUnsettledRefunds` 接替了原先「状态是 REFUNDING 就拒绝」的作用**：唯一函数索引只挡「同一订单 + 同一 `REFUND_SOURCE`」、挡不住「运营端退过、BOM 设备再退」，发起任何新退款前 **MUST** 先查它；`MANUAL` 也算未收口 —— 它的语义是「本系统已放弃自动判定，钱退没退未知」，放行等于允许在一笔可能已成功的退款之上再退一次。`mapper/F2fOrderMapper.java:214` / `:221`；XML 侧 — `F2fOrderMapper.xml:431`

#### 5.3 陷阱

- **`updateRefundSummary` 返回 1 只表示订单行存在，NEVER 拿返回值判断「退款成功没成功」** —— 那要看 `F2F_REFUND`。`mapper/F2fOrderMapper.java:207`
- **`updateStatus` 里 `PAY_CENTER_REFUND_NO` 与 `FINISH_TMS` 传 null 时用 NVL 保留原值**，避免回调补号后被后续状态推进擦掉。`F2fRefundMapper.xml:108`
- **「累加重试次数 + 记失败原因 + 排下次查询时刻」三件事在同一条 UPDATE 里**：拆成两条会出现「次数加了但没排下次」的中间态、那笔单下一轮立刻被再查一次、退避形同虚设。不动 `REFUND_STATUS`：状态推进只走 `updateStatus`。`mapper/F2fRefundMapper.java:102`；XML 侧 — `F2fRefundMapper.xml:149`
- **`F2F_REFUND` 一个原订单可能有多条退款单**（按票退 + 不同来源），调用方 MUST 自行按业务口径筛选。`mapper/F2fRefundMapper.java:57`
### 六、通知与定时任务

#### 6.1 契约与判据

- **出向通知报文形态是「ITP 信封 + `bizData`」，NEVER 退回裸 JSON**：2026-09-15（ADR-D89）用同一份业务字段对同一个 URL 做 A/B 实测，结论是**该网关只认信封** —— 裸 JSON body（`Content-Type: application/json`）→ `retCode=7004 处理过程出现错误!`；`x-www-form-urlencoded` 信封 + `bizData` 装同一份 JSON → `retCode=7001 找不到对应的数据`（= 已解析出订单号、只是对端库里没这单）。7001 与 7004 的差别就是「解析到了」与「没解析到」。改本类报文形态前 MUST 重跑这个 A/B，**NEVER 只凭「HTTP 200 且返了 retCode」就认为形态没问题**（两种形态都是 200）。pay-sign / ticket / collect-pay 三条同族链路一直发的都是信封，**face-pay 此前是四条里唯一发裸 JSON 的，因此 IF8B-04/05/06/07 全都从未被对端受理过**。`AppNotifyClient`（类注释）— `channel/app/AppNotifyClient.java:31`
- **「7004 = 我方形态不对」这条判断对 IF8B-05 `receivePaymentResult` 已被实测推翻**（2026-09-16，ADR-D102）：该端点上跑过 7 组探针（信封 / 裸 JSON、`deviceId` 与 `sign` 空与非空的四种组合、3 键 / 7 键 / 只带 `orderNo` 三种 `bizData`），**全部返 `7004`**；而同一域名、同一批次、同一信封打 `receiveTakeTicketResult` 返的是 `7001`。更关键的一条：**拿同一个 `orderNo` 重发时它返 `7005 当前数据已经在处理中`** —— 说明它**解析到了订单号并落了记录**、随后处理必然出错。因此 IF8B-05 的 7004 是**对端该端点自身的处理异常**，不是我方报文形态或字段缺失。**NEVER 再为这条通知改报文形态或补字段**；同样 **NEVER 把 7004 加进成功码把它藏起来**。`AppNotifyClient` — `channel/app/AppNotifyClient.java:45`
- **上一条 NEVER 只覆盖 IF8B-05，NEVER 外推到同族其它通知**（2026-09-16，ADR-D105）：同日又拿一条已 `GIVEUP` 的真实订单在**同一个域名**上做 A/B，结论相反 —— `receiveTakeTicketFaultResult`（IF8B-07）原 5 键 → `7004`，`bizData` 首位补 `userId` → `7005 当前数据已经在处理中`（已受理）；`receiveTakeTicketResult`（IF8B-06）原 4 键、**不带** `userId` → `0000 成功`；`receivePaymentResult`（IF8B-05）带真实 `userId` 的 APP 单 → 2026-09-16 14:06:25 实测 **SUCCESS**，同批不带 `userId` 的设备单仍全 `7004`。因此该网关的 `7004` **至少有两种成因**：①我方 `bizData` 缺该端点必需的键（IF8B-07 的 `userId`）；②对端该端点自身处理异常（IF8B-05 的 7 组探针）。**判据只能靠逐端点 A/B 实测，NEVER 拿某一个端点的结论套到别的端点上。** `AppNotifyClient` — `channel/app/AppNotifyClient.java:58`
- **「投递成功」的判定口径**：HTTP 非 2xx 一律判失败；2xx 时再看应答体 —— 能解析出 `retCode` 或 `code` → **按它判**，只有明确成功码才算投递成功；解析不出来（空体、非 JSON、没有这两个键）→ **按 2xx 算投递成功但打 WARN**。后一条是有意的折中：若改成「解析不出就判失败」，一个只回 `OK` 纯文本的网关会让每条通知都重试到 `GIVEUP`；若改成「2xx 就无条件成功」，网关回 `retCode=9999` 也会被当成投递完成、通知永久丢失。`AppNotifyClient` — `channel/app/AppNotifyClient.java:73`
- **成功码只认 `0000` / `code=0`，NEVER 顺手把 `7004` 加进来**：`pay-sign-server` 的 `app.notify.success-ret-codes=0000,7004` 是**那条链路**按「7004 = 已处理/重复通知」拿到确认后配的（`docs/business/pay-sign.md:234`），而 ticket-server 侧同一个码实测是「对无效卡号的处理失败」、被判成失败（ADR-D61 明确要求两条链路口径 **NEVER 统一**）。本链路的 7004 是**对端处理阶段出错**，加成功码等于把「对端根本没受理」永久记成投递完成。`AppNotifyClient` — `channel/app/AppNotifyClient.java:82`
- **ITP 信封表单体八个字段一个都不能少** —— 对端缺字段时返的还是那个笼统的 `7004`、看不出少了哪个；`timestamp` 每次投递按当前时刻重取（重试时也会换新值），格式与设备链路一致 `yyyyMMddHHmmss`。`AppNotifyClient.buildForm` — `channel/app/AppNotifyClient.java:145`
- **`AppNotifyClient` 的两条约定**：①**NEVER 抛异常、NEVER 返回 null** —— 所有失败都收敛成 `AppNotifyResult.failed`，由调用方决定重试还是放弃（通知链路上一个未捕获异常会让整批扫表任务停摆）；②用 JDK `HttpClient` 而不是 `rpc` 模块的 `ProxyWebClient`：APP 网关是**外部系统**，不走 `service.*.url` 那套内部服务发现。`channel/app/AppNotifyClient.java:22`
- **通知地址只配完整 URL，NEVER 用 base + path 拼接**：2026-08-26 事故 —— `pay-sign-server` 5 条路径漏了 `/v1`，网关返回 `code=600 操作失败`（不是 404），免密扣款自上线起零条成功却没人发现。**默认值一律为空**（`${ENV:}` 形态由 K8s 注入）；旧 `collect-pay-server/application.yml:36-39` 三条通知地址硬编码了 `dtcustomer.bestonepay.com/testngbackV2/...`，**`testngbackV2` 是测试环境残留**、属未修复 P0，**本模块 NEVER 继承这些值**。URL 未配置时 `F2fNotifyJob` 把该任务记为失败并退避重试、**不会静默丢弃**（通知任务已落库，配好地址后自动补发）。`AppNotifyProperties`（类注释）— `channel/app/AppNotifyProperties.java:8` / `:19`
- **`refundNoticeUrl` 与 `refundResultUrl` 是两个不同的值，NEVER 合并**：前者只出现在退款受理响应体里、我方从不请求它；后者是 `F2fNotifyJob` 真正 POST 的出向地址。旧实现两个键各配各的，合并会改变响应内容。`AppNotifyProperties.refundNoticeUrl` — `channel/app/AppNotifyProperties.java:35` / `:39`
- **信封那六个字段 NEVER 删**（2026-09-15 实测 ADR-D89）；取值逐字对齐旧 `collect-pay-server/application.yml:40~44` 的 `app.*`（`providerId=06` / `charset=UTF-8` / `format=json` / `signType=00`），`sign` 与 `deviceId` 旧实现都是空串。但「信封就能被受理」只对出票 / 退款那几条成立 —— `receivePaymentResult` 七组全返 `7004`，**NEVER 为了那条通知去动这六个字段的取值**，成因在对端。`AppNotifyProperties`（`providerId` 字段注释）— `channel/app/AppNotifyProperties.java:62` / `:72`
- **出向通知当前不加签**（`signType=00` + 空 `sign`），与 pay-sign / ticket / collect-pay 三条链路现状一致。与 AGENTS.md §5.2 的鉴权要求冲突、属测试期敞口，上线前 MUST 补；**NEVER 在本模块自造签名逻辑**，要加 MUST 对齐现有验签实现。`AppNotifyProperties.signType` — `channel/app/AppNotifyProperties.java:87`
- **`F2F_NOTIFY_TASK` 幂等靠函数唯一索引 `UK_F2F_NOTIFY_IDEM (NOTIFY_TYPE, ORDER_NO, NVL(REFUND_NO,'#NONE#'))`**：同类型同订单同退款单只投递一次；落库 MUST 直接 INSERT、**NEVER 先查后插**，重复由 `DuplicateKeyException` 兜底。退避策略由应用算好 `nextRetryTms` 后写入、**SQL 内不做任何时间计算**；扫表只按 `IDX_F2F_NOTIFY_SCAN (NOTIFY_STATUS, NEXT_RETRY_TMS)` 取。`notifyStatus` 取值 `PENDING / SUCCESS / FAILED / GIVEUP`，其中 `GIVEUP` 表示超过最大重试次数放弃、需人工介入、不再被扫表捞出。`F2fNotifyTask`（类注释）— `entity/F2fNotifyTask.java:11`
- **通知目标当前仅 APP**：旧实现三张表均为 `tbl_notice_app_*`、无向 BOM / TVM 的出向通知；**新增目标 MUST 先确认对端有接收接口，NEVER 先放开 CHECK 再找场景**。`entity/F2fNotifyTask.java:34`
- **`selectByIdemKey` 的 SQL 内 `REFUND_NO` 用 `NVL(..., '#NONE#')` 与索引表达式保持一致**：`refundNo` 传 null 即表示「整单类通知」，**NEVER 改写成 `REFUND_NO = #{refundNo}`** —— 那样 null 永远比不中、且用不上函数索引。主要用途是 INSERT 撞唯一索引后回查已有任务做幂等返回，**NEVER 用它做「先查后插」的前置判断**。`mapper/F2fNotifyTaskMapper.java:49` / `:55`；XML 侧 — `F2fNotifyTaskMapper.xml:71`
- **`selectDueTasks`：`NOTIFY_STATUS='PENDING'` 且 `NEXT_RETRY_TMS` 已到，按 `NEXT_RETRY_TMS` 升序、先到期先发**；`NEXT_RETRY_TMS` 为空视为「立即可发」，首次落库不写该列时也能被捞出。`SUCCESS` / `FAILED` / `GIVEUP` 都不会被捞出：`FAILED` 由 `markFailure` 重置回 `PENDING` 才重新进入扫描，`GIVEUP` 需人工介入。**显式 ORDER BY 是 FETCH FIRST 的前提**：无排序时取哪 limit 条不确定、早到期的任务可能长期被饿死。`mapper/F2fNotifyTaskMapper.java:65`；XML 侧 — `F2fNotifyTaskMapper.xml:84`
- **`selectDueTasksByType` 与 `selectDueTasks` 完全同义、只多一条 `NOTIFY_TYPE` 谓词**；存在的唯一理由是旧模块 `/pay/noticeAppTask/**` 那三个外部触发端点**按业务类型分开**（取票成功 / 取票失败 / 退款结果各一个 URL，原本对应三张 `TBL_NOTICE_APP_*` 表）。本模块把三张表合成一张，但**对上游的触发粒度 MUST 保持不变** —— 否则运维点「重投退款通知」会连带把取票通知也发一遍。**NEVER 用它替代 `selectDueTasks` 做常规扫表**（那条走全类型，少一次索引前缀不匹配）。`mapper/F2fNotifyTaskMapper.java:80` / `:87`；XML 侧 — `F2fNotifyTaskMapper.xml:103`
- **`markSuccess` 前置状态白名单只允许 `PENDING` / `FAILED`，`SUCCESS` 与 `GIVEUP` 不再翻转**；返回值 **调用方 MUST 据此判断，NEVER 忽略**。`mapper/F2fNotifyTaskMapper.java:99` / `:103`；XML 侧 — `F2fNotifyTaskMapper.xml:120`
- **通知任务的四条约束**（改 mapper 前 MUST 先读）：落库只 INSERT 不先查后插；扫表只按两列取、SQL 内 NEVER 做时间计算；状态推进走白名单、**NEVER 改成「非终态即可更新」**；`GIVEUP` 是人工介入终态。`mapper/F2fNotifyTaskMapper.java:13`
- **`F2fNotifyService.enqueue(..., maxRetryTimes)` 重载唯一在用的场景是 IF8B-05 推设备单**（`F2fPayCenterFlow.enqueuePayResultNotify`）：设备单的 `THIRD_USER_ID` 为空、报文里 `userId` 只能上送 null，而 APP 侧按 `userId` 定位用户，2026-09-16 实测这类通知**必然**返 `7004`（`F2F_NOTIFY_TASK` 里 18 条无 `userId` 的 `PAY_RESULT` 无一例外，唯一成功那条是带真实 `userId` 的 APP 单）。默认 5 次重试对它没有任何意义 —— 只是把同一条注定失败的报文推 5 遍、再刷一条 ERROR 级 GIVEUP 日志，把真正需要人工介入的失败埋在噪音里。传 1 表示**一次即终态**。**NEVER 借这个重载去跳过设备单的入队**（「设备单也推」是用户裁决，见 ADR-D89）：本重载只压缩重试次数、不改推送范围。`F2fNotifyService.enqueue` — `service/F2fNotifyService.java:79` / `:89`
- **IF8B-05 `payDate` 的格式**：规格原文没给格式，此处对齐同族 IF8B-02 的 `terminationTime`（`YYYYMMDDHH24mmss`），**待甲方确认**。改这个常量等于改对外契约，MUST 先和 APP 侧对齐。`F2fPayCenterFlow.PAY_DATE_PATTERN` — `service/F2fPayCenterFlow.java:68`
- **IF8B-05 的三处「待甲方澄清」**（行内注释）：①`payResult` 规格有 SUCCESS / FAIL 两个取值，但当前只在支付成功收口处推、没有推失败的业务需求，要加 MUST 单独定触发点、**NEVER 在本方法里加参数分叉**；②规格未写金额单位，此处按分、与同族 IF8B-04 的 `refundAmount`「单位分」对齐；③`voucher` 恒为空串 —— IF8B-05 在「支付成功时刻」触发，而 face-pay 的取票凭证（票逻辑卡号）要等出票结果上报才存在、`F2F_ORDER` 上没有任何可用作 voucher 的列，且规格原文对 voucher 的说明自相矛盾（既写「整段用于生成二维码」又写「预留字段」）、未给必填性；④`orderType` 固定 `"0"`（全推），而充值单在规格的 `orderType` 枚举（0 单程票 / 1 普通日票 / 2 全城通日票）里没有对应取值。`F2fPayCenterFlow.enqueuePayResultNotify` — `service/F2fPayCenterFlow.java:374` / `:377` / `:380` / `:386`
- **IF8B-05 的 `payDate` 参数 NEVER 改成读回查出来的 `order.getPaidTms()`**：三个调用点传进来的都是各自 `markPaid` 用的同一个 `LocalDateTime`，而回查实体在并发/幂等重入下可能还是旧值或 null，两者对不上就等于给 APP 报了一个错的支付时间。`F2fPayCenterFlow.enqueuePayResultNotify` — `service/F2fPayCenterFlow.java:358`
- **IF8B-05 推送范围 = 所有订单，故意不做 `TRANS_TYPE='03'` 过滤**（用户裁决）；这与同模块 `F2fTicketIssueService.enqueueAppNotify`「只推 APP 单」的口径**故意不同**，**NEVER 顺手加上 `TRANS_TYPE` 过滤**。但**设备单的重试上限压到 1 次**（2026-09-16 用户裁决，ADR-D105），**这只压缩重试次数、不改推送范围** —— 任务照样落库，**NEVER 借它退化成「设备单不推」**。`F2fPayCenterFlow.enqueuePayResultNotify` — `service/F2fPayCenterFlow.java:345` / `:349`
- **IF8B-06 / IF8B-07 的 payload 4 个 key 逐字照搬旧 `noticeAppTakeTicketResult`**（`TvmOrderServiceImpl:446-450`）：`orderNo / orderTicketNum / actualTakeTicketNum / takeTickeDate` —— 最后一个**拼写少一个 t 是既有契约**、APP 侧按这个 key 取值、**NEVER 改名**；退款场景额外带 `refundNo`。**IF8B-07（`TAKE_TICKET_FAIL`）额外带 `userId`，IF8B-06 不带** —— 2026-09-16 A/B 实测确立（ADR-D105，同一订单 `00202609161406170219`、同一套信封、只改 `bizData`）。因此 **`userId` 不是这一族通知的通用必填项、只有 IF8B-07 那个端点要**：**NEVER 顺手给 IF8B-06 也加**（它无 `userId` 已实测 `0000`，改形态等于把一条已验证通过的链路推回未知），也 **NEVER 因为「两条通知长得像」就把两个 payload 合并成一份**。`userId` 取 `THIRD_USER_ID`，本方法只对 `TRANS_TYPE='03'` 的 APP 单入队、那些单该列必然非空。`F2fTicketIssueService.enqueueAppNotify` — `service/F2fTicketIssueService.java:300` / `:302` / `:307` / `:315` / `:322`
- **`/pay/noticeAppTask/**` 四条端点无鉴权**（与 collect-pay 的 `NoticeAppTask`、本模块 `/page/face-pay/orders/**` 一致，靠网络隔离）。这四条**不改业务数据、只重发通知**，最坏后果是 APP 侧收到重复通知（**APP MUST 按 orderNo 幂等**，同 `F2fNotifyJob` 的多副本约束），因此风险低于 `/internal/app-order/**` 那类建单端点；**上线前 MUST 与其余裸端点一并补鉴权**。`NoticeAppTaskController` — `controller/notice/NoticeAppTaskController.java:36`
- **三个投递端点恒返 `0000`**：本端点的语义是「触发一轮投递」，单笔通知投递失败属正常业务分支（已按退避排下次重试），不该让调用方以为触发本身失败、从而立刻重试整轮 —— 那只会把同一批任务重复捞出。真实结果看 `retMsg` 的计数与服务端日志。`NoticeAppTaskController`（公共体）— `controller/notice/NoticeAppTaskController.java:93` / `:95`
- **连通性探针端点不碰任何表、恒返 `0000`**：运维用它确认「这台服务的通知重投入口通不通」，与「有没有待投递任务」解耦 —— 投递端点返 0 条时分不清是没任务还是没连上。`NoticeAppTaskController` — `controller/notice/NoticeAppTaskController.java:63`
- **`F2fNotifyDeliverer` 的三条约束（与原 `F2fNotifyJob` 一致，NEVER 放宽）**：①不带 `@Transactional`（每笔都要发 HTTP，AGENTS.md §5.2）；②逐笔 try/catch（一笔异常不能让整批停摆）；③**URL 未配置也要记失败，NEVER 静默跳过** —— 否则配置漏了没人知道。扫表本身失败（DB 不可用等）时返回空计数并记 ERROR、不抛出。未知类型的 `urlOf` 返回 null 时 `AppNotifyClient.post` 会记「通知地址未配置」并判失败、走退避重试，**未知类型不静默丢弃**，最终 GIVEUP 时人工能从 `LAST_ERROR` 看出原因。`F2fNotifyDeliverer` — `service/F2fNotifyDeliverer.java:27` / `:65` / `:127`
- **`F2fReportRecovery` 只重放 `TAKE_TICKET_OK` / `TAKE_TICKET_FAIL` 两类**：`BOM_BIZ_RESULT` / `TOPUP_OK` 等类型的后续动作在各自链路里内联完成，**NEVER 往这个列表里加它们** —— 那等于让本任务去重放它没有实现的语义。`F2fReportRecovery.REPLAY_TYPES` — `service/F2fReportRecovery.java:43`
- **`F2fReportRecovery` 三条不变量**（改本类前逐条对）：①**先重放、成功了才 `markProcessed`** —— 顺序颠倒会把「标记成功但动作没做」变成新的静默丢失，而这正是本任务要修的那类缺陷；②**`markProcessed` 返回 0 就当别人已处理，NEVER 记 ERROR**（WHERE 带 `PROCESSED='0'`，0 行是并发保护生效、不是故障）；③单条异常只计数、不中断整批，异常行保持 `'0'`、下轮自然重来。`service/F2fReportRecovery.java:25`
- **`resumeFromReport` 三步都幂等，所以重放安全**：状态推进是带 `PAID` 白名单的 CAS（重复即 0 行）；差额退款撞 `UK_F2F_REFUND_IDEM`（出票故障退款是整单粒度 + 固定 source，同一单重放不会二次出款）；通知入队撞 `UK_F2F_NOTIFY_IDEM`。**NEVER 在这里补「先查有没有做过」的判断** —— 那等于把幂等从唯一索引挪到应用层。返回 false 表示订单查不到或类型不属本任务，**留着 `'0'` 等人工**。`F2fTicketIssueService.resumeFromReport` — `service/F2fTicketIssueService.java:374` / `:381`
- **`f2f.report.staleSeconds` 默认 300 秒，NEVER 调到小于设备上报请求的最长耗时**：出票上报链路里含支付中心退款调用（正常几百毫秒、超时可到十几秒），静默期太短会让本任务与首报**并发**重放同一行 —— 三步虽都幂等、不会二次出款，但会白发一次支付中心请求并在日志里制造两份看起来矛盾的记录、排查时极易误判成「重复退款」。`F2fReportRecoveryJob` — `scheduler/F2fReportRecoveryJob.java:18`；mapper 侧同款（`staleBefore` / `reportTypes` **都不是可选的**）— `mapper/F2fResultReportMapper.java:64` / `:68`、`F2fResultReportMapper.xml:104`
- **设备离线判定窗口 = `offlineTimeoutSeconds`，默认 300 秒（按 1 分钟心跳周期的 5 倍容忍）；这个倍数不能压太紧** —— TVM 在出票高峰期心跳可能延迟，误判离线会污染运营看板。`F2fDeviceOfflineJob` — `scheduler/F2fDeviceOfflineJob.java:16`
- **`F2fOrderExpireJob` 三条约束**：①**不带 `@Transactional`**（每笔收口都要调支付中心，事务包住网络调用是 2026-08-26 生产事故的成因）；②**逐笔 try/catch**（一笔异常不能让整批停摆，否则一条脏数据会永久堵住收口）；③**重试 MUST 有出口** —— 扫表带放弃窗口下界（`f2f.order.expireGiveUpHours`），超窗的单不再被扫、不再外呼，只由 `countStaleExpiredOrders` 计数告警。**NEVER 让「状态不明就下轮再试」成为无上限循环**：2026-09-10 实测订单 `F200202609100914540082` 每 30 秒查一次支付中心、回 `9999 未找到数据`，持续 40 分钟没有出口，与 `F2fRefundReconcileJob` 的 `RETRY_TIMES < 20` 形成的对比正是这个缺陷被发现的方式。`scheduler/F2fOrderExpireJob.java:17`
- **`F2fRefundReconcileJob` 三条约束**：不带 `@Transactional`；逐笔 try/catch；**只捞 `NEXT_QUERY_TMS` 已到点的单**，退避没到点的本轮不动 —— 既避免同一笔卡住的单被每分钟反复查，也避免刚落库的单立刻被扫走（此时支付中心那边可能还没记上）。**停止条件不在本类**：什么时候放弃自动收口由 `F2fRefundService` 按 `REQUEST_TMS` 的时间窗判定并置 MANUAL；本类 **NEVER 再传 maxRetryTimes** —— 按次数截断会让单静默掉出扫描范围、停在非终态无人管。`scheduler/F2fRefundReconcileJob.java:23` / `:32`
- **`F2F_RESULT_REPORT` 统一承载六个设备上报接口、收敛为五个 `REPORT_TYPE`**：`TAKE_TICKET_OK`、`TAKE_TICKET_FAIL`、`TOPUP_OK`、`TOPUP_FAIL`、`BOM_BIZ_RESULT`（见 `CK_F2F_REPORT_TYPE`）。幂等靠 `UK_F2F_REPORT_IDEM (REPORT_TYPE, ORDER_NO)`，写入只 INSERT、重复由唯一索引抛 `DuplicateKeyException`、application 层捕获后当作「已收到过」返回成功。`PROCESSED` 把「接收」与「后续动作」解耦（退款、状态推进由扫表驱动，命中 `IDX_F2F_REPORT_PENDING`）。`REPORT_TMS` 是 `VARCHAR2(14)` 的 `YYYYMMDDHHMMSS` 字符串、不是时间类型，只有 `RECEIVE_TMS` / `CREATE_TMS` 是 `TIMESTAMP(6)`。`entity/F2fResultReport.java:11` / `:16`
- **`selectByOrderNo` 的 `orderNo` 在 `ERROR_CODE=2101` 时是取票二维码的 `randomFact`。** `mapper/F2fResultReportMapper.java:46`
- **`markProcessed` 的 WHERE 带 `PROCESSED='0'` 做并发保护，返回 0 表示该条已被其他线程处理，调用方 MUST 据此跳过**而不是重复执行后续动作。`mapper/F2fResultReportMapper.java:82`；XML 侧 — `F2fResultReportMapper.xml:128`
- **`updateOptResultByIdem` 用幂等键而不是自增 id 定位**：`insert` 不回填主键，而 `(REPORT_TYPE, ORDER_NO)` 本身就是 `UK_F2F_REPORT_IDEM`、唯一；该语句不带 `PROCESSED` 条件 —— 它改的是业务结论、不是处理进度。`mapper/F2fResultReportMapper.java:89` / `:92`；XML 侧 — `F2fResultReportMapper.xml:139`
- **`F2F_DEVICE_STATUS` 一台设备只有一行**，主键是复合主键 `(CHANNEL, DEVICE_ID)`、没有自增 ID，因此实体里也没有 `id` 字段；**只存最新状态不存历史**，心跳更新走 `MERGE INTO` 单语句 upsert、**NEVER 先 SELECT 判存在再 insert/update**。`entity/F2fDeviceStatus.java:11`
- **心跳 MERGE 命中已有行时**：`LAST_HEARTBEAT_TMS` 更新为本次心跳时间、`HEARTBEAT_COUNT` 自增 1、`ONLINE_FLAG` 置 `'1'`（超时置离线后重新上报即自动恢复在线）；未命中时按 `HEARTBEAT_COUNT = 1`、`ONLINE_FLAG = '1'` 插入首行。`STATION_CODE` 用 `NVL` 保护：本次心跳未带车站时保留已有值、不被 NULL 覆盖。`ON` 子句里的 `CHANNEL` / `DEVICE_ID` 是主键、Oracle 不允许在 `UPDATE SET` 中出现，故未列入。`mapper/F2fDeviceStatusMapper.java:30`；XML 侧 — `F2fDeviceStatusMapper.xml:28`
- **离线判定不在心跳链路里做**，由 `selectHeartbeatTimeout` + `markOfflineByDeadline` 的扫表任务完成（命中 `IDX_F2F_DEVICE_HB`）；`deadline` 由调用方按「心跳周期 × 容忍倍数」算出、**本语句不内置时间窗**。批量置离线的 WHERE 复用与扫表相同的两个条件，因此扫表与置离线之间若设备恰好补上心跳、该行不会被误置离线。`mapper/F2fDeviceStatusMapper.java:22` / `:56` / `:77`；XML 侧 — `F2fDeviceStatusMapper.xml:69` / `:93`

#### 6.2 决策理由

- **face-pay-server 的 `@Scheduled` 实测共 7 个、分布在 6 个类里**（通知投递 / 退款回查 / 订单过期 / 设备离线 / 补款 converge / 补款 close / 出票上报补偿）。**仓库文档里「4 个」那个数字是补款那两个（ADR-D103）加进来之前写的，已过期。** `F2fReportRecoveryJob`（类注释）— `scheduler/F2fReportRecoveryJob.java:11`
- **多副本无锁是本模块统一取舍，face-pay-server MUST 单副本**：两个副本可能同时捞到同一条并各发一次，因此 **APP 侧 MUST 按 orderNo 幂等**；`markSuccess` 的前置状态白名单保证只有一个副本能把它标成功、另一个拿到 0 行。`F2fOrderExpireJob` 侧的代价是对支付中心的重复查询，因此**副本数变化时 MUST 复核扫表间隔与批量**。`scheduler/F2fNotifyJob.java:26`；`scheduler/F2fOrderExpireJob.java:30`；`scheduler/F2fRefundReconcileJob.java:36`；`service/F2fReportRecovery.java:35`；`scheduler/F2fReportRecoveryJob.java:23`
- **`F2fNotifyJob` 是 `F2F_NOTIFY_TASK` 的常规出口** —— 没有它，`F2fNotifyService.enqueue` 落的任务只会堆在表里。投递逻辑已外提到 `F2fNotifyDeliverer`，因为 `/pay/noticeAppTask/**` 那三个外部触发端点要复用同一份实现；本类只剩「多久扫一次、单轮多少条」两个决策。**外部 HTTP 触发只是快速路径，NEVER 因为有了它就把本任务停掉。** `scheduler/F2fNotifyJob.java:9` / `:12` / `:24`
- **相对旧实现的结构性差异**：旧实现在设备请求线程里同步发 HTTP、失败置 `NOTICE_FAIL` 就结束，而重推方法 `sendNoticeAppTakeTicketRecord` **没有任何 `@Scheduled` 绑定**、要靠外部 web-server 的 Quartz 去调。两个后果：设备白等一个 HTTP 超时；一旦 Quartz 没配，通知就永久躺在表里。`scheduler/F2fNotifyJob.java:16`；`service/F2fNotifyService.java:19`
- **为什么单独一个 `F2fNotifyDeliverer` 而不是让 Controller 调 Job**：`@Scheduled` 方法是给调度器的入口，被 HTTP 线程直接调用会让「本轮是谁触发的」在日志里分不清，也会让 Job 从「只被调度器碰」变成「随时可能被并发进入」。把执行体外提后两个入口各自记自己的日志、Job 仍只被调度器碰一次。`service/F2fNotifyDeliverer.java:22`
- **为什么本模块也要有 `/pay/noticeAppTask/**` 那四条**：本模块的通知投递已有 `F2fNotifyJob`（fixedDelay 30 秒）常规兜底、比旧实现强，但**旧模块的这四个 URL 是 web-admin Quartz 与运维手工重投的既有入口**；设备与 APP 流量切到本模块后，旧模块的这四条打进来只会去扫三张空的 `TBL_NOTICE_APP_*` 旧表、每轮返 0 条，看着像「通知都发完了」。因此本模块补齐同名端点、落到 `F2F_NOTIFY_TASK`。**NEVER 因为有了这四条就停掉 `F2fNotifyJob`** —— 外部触发是快速路径，常规收敛仍靠模块内扫表（Quartz 一旦没配，通知会永久躺在表里，旧实现正是这个坑）。`NoticeAppTaskController` — `controller/notice/NoticeAppTaskController.java:17` / `:41`
- **与旧实现的三处有意差异**：①旧的三个投递方法返回 `void`（HTTP 200 空体）、调用方无法知道发了几条，本实现四条统一返 `CommonResult`、`retMsg` 带「到期/已投递/待重试/异常」四个计数；②旧实现每类通知一张表，本模块是一张表 + `NOTIFY_TYPE` 列，但**触发粒度保持按类型分开**；③旧实现的重试次数在应用侧 +1 后回写，本模块由 mapper 单条 UPDATE 用 `CASE WHEN` 判定是否 `GIVEUP`，因此**同一批被并发触发两次也不会突破 `MAX_RETRY_TIMES`**。`controller/notice/NoticeAppTaskController.java:24`
- **`markFailure` 把「是否放弃」放进同一条 UPDATE 的三条理由**：①次数与状态是同一个决策的两个面，拆两条 SQL 之间存在窗口、期间任务仍是 `PENDING` 且 `NEXT_RETRY_TMS` 已到、会被下一轮扫表重复捞出，实际重试次数可能超过 `MAX_RETRY_TIMES`；②`MAX_RETRY_TIMES` 是行上的列而非常量（DDL 默认 5、允许按任务调整），比较交给数据库读到的是当前行真值，应用侧先读再算会引入读到即过期的问题；③本项目不使用分布式锁，跨行原子性只能靠单语句。`nextRetryTms` 由调用方按退避策略算好后传入、**SQL 内 NEVER 做时间计算**；命中 `GIVEUP` 分支时该值仍会写入，但 `GIVEUP` 不被扫表捞出、不影响行为，且保留了「原本打算何时重试」的现场信息。`mapper/F2fNotifyTaskMapper.java:109`；XML 侧 — `F2fNotifyTaskMapper.xml:131`
- **退避策略在应用侧算**：按已重试次数指数退避 30s → 60s → 120s → 240s → 480s、上限 10 分钟；是否 `GIVEUP` 由 mapper 的同一条 UPDATE 用 `CASE WHEN` 判定，避免「加次数」与「置 GIVEUP」拆成两条 SQL 后被重复捞出。`F2fNotifyService.markFailure` — `service/F2fNotifyService.java:149` / `:151`
- **`enqueue` 只 INSERT、不做前置判重**；`NEXT_RETRY_TMS` 留空即「立即可发」，下一轮扫表（默认 30 秒内）就会取到。**这里不做同步推送** —— 接收链路 MUST 毫秒级返回设备。`service/F2fNotifyService.java:65` / `:67`
- **`F2fPayCenterFlow` 是「与支付中心交互并收口本地状态」这条骨架的唯一实现**（模板方法 + 结果策略）。重构前，同一条骨架在 4 个 service 里各抄一遍预下单、3 个 service 里各抄一遍查询收口，判定序列逐行相同、**差别只在「用哪个响应壳回话」**。**本类只管状态推进、绝不组装响应**：四个渠道的 retCode 族互不相同（TVM `0000/2002/2999`、BOM `0000/8003/8006/8999`、APP `0000/9999`），统一它们是明确禁止的；因此本类返回 `Submitted` / `Settled` 这类**判定结果**，由调用方 `switch` 模式匹配后各自组壳 —— 少写一个分支直接编译失败。`service/F2fPayCenterFlow.java:25` / `:27` / `:33`
- **`F2fPayCenterFlow` 不是「统一 CAS 入口」**（`docs/domain/state-machines.md` §二③ 明令禁止）：它只承担「支付中心答复所蕴含的那几步推进」（受理→`PAYING`、收款→`PAID`、被拒→`PAY_FAILED`、未支付→`EXPIRED`）；退款域（`REFUNDING` / `REFUNDED`）与履约域（`FULFILL_FAILED`）的 CAS **仍留在各自 service，NEVER 挪进来** —— 那些转移的副作用与冲突口径按链路不同，合并即失去分辨力。本类**不带 `@Transactional` 且 MUST 保持如此**。`service/F2fPayCenterFlow.java:38` / `:44`
- **判定顺序不可调换**：传输失败 → 业务码非 0 → （可选）同步支付状态 → （可选）`data` 为空 → 受理成功；**调用方 MUST 自己先 INSERT 一行 `INIT` 的支付流水**（各渠道列不同），本方法只负责把它推向终态。`F2fPayCenterFlow.submit` — `service/F2fPayCenterFlow.java:164` / `:166` / `:169`
- **`rejectTransition` 的 `null` 是有意的取值**：APP 侧被拒后订单要留在 `CREATED`、乘客可以换个支付通道再来一次；TVM / BOM 侧一次被拒即置 `PAY_FAILED`。**重构前这个差异只体现为「某个类里少了两行」**，现在必须显式写出来。`service/F2fPayCenterFlow.java:106`
- **`Unknown` 结局：支付流水置 `UNKNOWN`、订单一律不动**，留给收口任务；两种成因合并在这里，调用方要区分就看 `result.isTransportFailed()`（①对端没答上来；②答了 `code=0` 但 `data` 是空的）。`SyncPaid` 分支**刻意什么都没写** —— 支付流水的成功行各渠道列不同，由调用方写完再调 `markPaidAndReport`。`service/F2fPayCenterFlow.java:146` / `:155`
- **`NoConclusion`：本地一律不动状态**，调用方 MUST 回「支付中 / 处理中」让对方继续轮询，**NEVER 回失败** —— 钱可能已经收了。`service/F2fPayCenterFlow.java:244`
- **`markPaidAndReport` 只告警、不改对上游的应答**：`markPaid` 的 WHERE 是 `ORDER_STATUS IN ('CREATED','PAYING')`，返 0 行意味着订单已被别人推走（最常见是被 `EXPIRED` 收口任务抢先），而钱已经在支付中心收掉了。设备侧拿到失败会重新收款、那才是真的二次扣款。关键字 **`F2F CAS 冲突` 供日志告警检索，NEVER 改措辞、NEVER 降级成 warn**。`service/F2fPayCenterFlow.java:302` / `:308`
- **加 `markPaidAndReport(String, ...)` 重载而不是改原签名**：原签名有 `F2fScanPayService` 与本类 `settle` 两个调用方、外加 3 处注释引用，那两条链路拿不到 `tradeNo`。`service/F2fPayCenterFlow.java:316`
- **`enqueuePayResultNotify` 整个方法体被 try/catch 包住且只记日志**：本方法挂在支付成功收口之后，抛出去会让支付回调对支付中心报错、引来重推 —— 而钱已经收了、订单已经 PAID，重推除了放大流量没有任何用。通知漏了可由人工补，**NEVER 让它打断支付主链路应答**。`service/F2fPayCenterFlow.java:341`
- **`warnIfConflict` 刻意只告警、不改应答**：升级成「按库里真实状态应答」是对外行为变更，**MUST 先有 WARN 频次数据**（对齐过闸链路的观察期口径）。`service/F2fPayCenterFlow.java:403` / `:406`；TVM 侧同款说明 — `service/F2fTvmOrderService.java:606` / `:608`
- **`F2fTvmOrderService` 的「二维码超时收口」里的 `markPaid` 仍自己接返回值并据此分流、不走 `markPaidAndReport`；改那处时 MUST 确认仍然接住了返回值。** `service/F2fTvmOrderService.java:612`
- **两处 IF8B-05 入队点 NEVER 替换成 `markPaidAndReport`**：该分支与 `markPaidAndReport` 的语义/日志都不同，只能在其后追加入队；`tradeNo` 取支付中心订单号（与上一行 `markPaymentSuccess` / `markSuccess` 落 `PAY_CENTER_ORDER_NO` 用的是同一个值），时刻复用同一个 `now`。`F2fTvmOrderService`（行内注释）— `service/F2fTvmOrderService.java:406` / `:470`
- **`F2fReportRecovery` 是 `F2F_RESULT_REPORT.PROCESSED` 的唯一消费方** —— 没有它，那一列恒为 `'0'`、而「落上报之后失败」的单子永远不会有人补。接收链路是「落票 → **落上报（幂等锚点）** → 推进订单 → 差额退款 → 入队通知」，上报行一旦提交、设备重传就会撞 `UK_F2F_REPORT_IDEM` 并**直接回 `0000`**，因此后三步中任何一步失败都不会再被重做：订单永久卡 `PAID`、出票故障的差额退款永不发起、APP 永不收到通知，且线上只留一行日志。表设计早就把 `PROCESSED` 留成了出口（列注释写着「后续动作由扫表驱动」），但 **1.0.45 之前 `selectPendingReports` / `markProcessed` 零调用方**。`service/F2fReportRecovery.java:13` / `:16`
- **`F2fOrderExpireJob` 是方向 1 防重下单的兜底**：重复下单产生的多余订单靠它从 `CREATED` / `PAYING` 收敛掉，没有这个任务，设计文档 §十六 的风险边界不成立。`countStaleExpiredOrders` 告警方法自身的异常吞掉 —— 告警失败 **NEVER 影响收口主流程**。`scheduler/F2fOrderExpireJob.java:14` / `:84`
- **`F2fRefundReconcileJob` 为什么必须主动查而不能只等回调**：与解约链路同理（AGENTS.md §8 末条）—— 退款回调只是快速路径，回调丢失、回调地址配错、我方短暂不可用都会让状态永久悬空；唯一可靠的收口方式是主动查支付中心的退款查询接口。没有它，`F2fRefundService` 的「对端未答留 INIT」就是个死状态。`scheduler/F2fRefundReconcileJob.java:15` / `:18`
- **`F2fDeviceOfflineJob` 是纯本地 UPDATE、没有网络调用，也不需要逐条处理**：`markOfflineByDeadline` 一条 SQL 批量搞定，且 WHERE 带 `ONLINE_FLAG='1'`，多副本重复执行只是第二次命中 0 行。心跳只负责「我还在」，判定「谁不在了」必须靠扫表 —— 设备掉线时不会发一条「我下线了」的报文。`scheduler/F2fDeviceOfflineJob.java:13` / `:19`
- **心跳为什么必须用 `MERGE INTO`**：主键是 `(CHANNEL, DEVICE_ID)`、一台设备一行；心跳是高频并发写（每设备约 1 分钟一次、全线设备数百台），「先 SELECT 判断存在再决定 insert / update」在同一设备重连或多线程重投时会撞主键。单条 MERGE 由数据库保证原子性，这也是本项目的既有约定（AGENTS.md §2.2.1）。不带 `@Transactional`：单条 SQL、自动提交即可。`F2fDeviceHeartbeatService` — `service/F2fDeviceHeartbeatService.java:19` / `:24`
- **`AppNotifyResult` 只有两种状态、没有「不确定」**：通知是幂等可重投的（APP 侧按 orderNo 去重），因此拿不准时按失败重试比按成功丢弃安全。**这与支付链路相反** —— 支付的「对端未答」绝不能当失败，因为钱可能已经扣了。工厂方法叫 `ok` 而不是 `delivered`：record 会为组件 `delivered` 自动生成同名访问器，静态方法重名会被编译器判为「记录中的存取方法无效」。`channel/app/AppNotifyResult.java:6` / `:20`
- **`AppNotifyChannelConfig` 只负责把 `AppNotifyProperties` 注册成 Bean**：`AppNotifyClient` 自带 `@Component`，因为它没有「必须能脱离 Spring 单测」的要求（不像 `PayCenterSigner` 要逐字比对待签串）。`channel/app/AppNotifyChannelConfig.java:7`
- **`payResultUrl` 看着像死配置、其实不是：NEVER 删。** `F2fNotifyJob.urlOf()` 的 `case TYPE_PAY_RESULT` 在读它（`F2fNotifyJob.java:107`），删字段直接编译失败。2026-09-15 曾据一次**大小写敏感**的 grep（只搜 `payResultUrl`、漏掉 `getPayResultUrl`）误判成零读取方而删除、构建即报「找不到符号」。**判断某属性有无读取方 MUST 用不区分大小写的 grep 或直接搜 `get<Name>`。** 生产者已于 2026-09-15（1.0.36 / ADR-D89）补齐（`F2fPayCenterFlow.enqueuePayResultNotify`，覆盖三个支付成功收口点），集群 env `NOTIFY_APP_PAY_RESULT_URL` 也已配上真实地址。`channel/app/AppNotifyProperties.java:46` / `:48` / `:54`
- **`PAYABLE_FROM_CREATED` 拆成常量不是为了好看**：那条 CAS **必须与 `// CAS-DISCARD:` 标记同行**，否则 `F2fOrderStatusArchTest.casReturnValueNeverDropped` 只看得见调用行、看不见下一行的标记，直接判成「返回值被丢弃」（2026-09-14 实测被门禁拦下）。行内标记原文：`PAYING` 是「已发起支付」记账标记，写不上不改变任何后续判断。`service/F2fPayCenterFlow.java:56` / `:58` / `:213`
- **设备单 IF8B-05 重试上限常量的理由与「NEVER 借它跳过入队」的边界写在 `F2fNotifyService.enqueue(..., int maxRetryTimes)` 的方法注释里，改这里 MUST 一起看。** `F2fPayCenterFlow.DEVICE_PAY_RESULT_MAX_RETRY` — `service/F2fPayCenterFlow.java:77`

#### 6.3 陷阱

- **`F2fNotifyDeliverer.DeliverStat` 的 `due` 是本轮捞到的条数，`delivered + retried + errored` 应等于它**；`F2fReportRecovery` 同款（`resumed + skipped + errored` 应等于 `due`）。`service/F2fNotifyDeliverer.java:49`；`service/F2fReportRecovery.java:64`
- **`F2fNotifyTaskMapper.insert` 的四个字段（`RETRY_TIMES` / `MAX_RETRY_TIMES` / `CREATE_TMS` / `UPDATE_TMS`）虽有 DEFAULT，但显式列出即按传入值写、显式传 null 会覆盖 DEFAULT，因此调用方 MUST 给这四个字段赋值。** `F2fNotifyTaskMapper.xml:35`
- **`F2fResultReportMapper.insert` 的 `PROCESSED` 传 null 覆盖不了默认值（显式列出即按传入值写），调用方 MUST 传 `'0'`。** `F2fResultReportMapper.xml:43`
- **`selectPendingReports` 的三个谓词都是必填、没有可选分支，因此不会踩 Druid WallFilter 那条「恒真条件成为唯一谓词」的规则**（AGENTS.md 5.1）。`F2fResultReportMapper.xml:104`
- **`markFailure` 不返回是否已放弃**，需要时由调用方随后 `selectById` 读取 `NOTIFY_STATUS`。`mapper/F2fNotifyTaskMapper.java:131`

### 七、持久层与 mapper

> 本节定位串：Java 侧路径前缀 `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/`，XML 侧前缀 `face-pay-server/src/main/resources/mapper/`。

#### 7.1 契约与判据

- **全库 SQL 正文 NEVER 出现 `- -` 行注释或 `/* */` 块注释**：Druid WallFilter 的 `commentAllow=false` 会把带注释的语句判定为注入并**静默失效**（只在 Oracle 生产暴露，达梦环境关了 WallFilter 测不出来），说明一律写在 XML 注释里，见 AGENTS.md §5.1。同一段禁令逐字出现在 8 个 mapper XML 的文件头 — `F2fOrderMapper.xml:6`、`F2fPaymentMapper.xml:6`、`F2fTicketMapper.xml:6`、`F2fRefundMapper.xml:6`、`F2fDeviceStatusMapper.xml:6`、`F2fSequenceMapper.xml:4`、`F2fReconExportMapper.xml:6`。
- **XML 注释内也 NEVER 出现两个连续的半角减号**，否则 SAX 直接报「注释中不允许出现字符串」、整个服务启动即挂。`F2fSequenceMapper.xml:6`；同一条约束在对账 mapper 里写成「本文件的 XML 注释内也不出现连续两个半角减号」— `F2fReconExportMapper.xml:8`
- **`F2F_ORDER` 三条本表约束（改 `F2fOrderMapper` 前 MUST 先读）**：① **下单只 INSERT，不先查后插** —— 并发下「先 SELECT 判存在再 INSERT」无效，且旧实现正是这个形态；重复下单靠 `UK_F2F_ORDER_NO` 抛 `DuplicateKeyException`，由 application 层捕获后查已有订单幂等返回。② **状态推进走白名单** —— `updateStatus` 的 `fromStatuses` 必填，**NEVER 写成「非终态即可更新」**，中间态往往尚未通过前置校验。③ **取票激活用条件更新抢锁** —— `activateForDevice` 的 WHERE 带 `ACTIVATE_DEVICE_ID IS NULL`，靠返回行数判断是否抢到，返回 0 表示已被其他设备激活、对外返回 `2008`。`F2fOrderMapper`（类注释）— `mapper/F2fOrderMapper.java:14`、`:16`、`:19`、`:21`；XML 侧「返回 0 → 2008（规格 §7.5 异常流程）、前置状态要求 `PAID`（未支付不允许激活）」— `F2fOrderMapper.xml:212`
- **`activateForDevice` MUST 一并写入取票二维码三要素**：IF2A-08 `requestTakeTicketAuth` 正是按 `ACTIVATE_DEVICE_ID + QRCODE_GEN_DATE + RANDOM_FACT` 回查这一行，**这三列不在激活时落库，取票鉴权就永远查不到订单**。`F2fOrderMapper.activateForDevice` — `mapper/F2fOrderMapper.java:105`；`F2fOrderMapper.xml:217`
- **`selectByQrcode` 匹配 `ACTIVATE_DEVICE_ID` 而不是 `DEVICE_ID`**：`DEVICE_ID` 是下单设备（APP 单里不是 TVM），二维码三要素是激活时由 TVM 写入的。**旧实现把 TVM 的 `deviceId` 覆盖写进下单设备字段，丢掉了下单来源。** `F2fOrderMapper.xml:126`；命中 `IDX_F2F_ORDER_QRCODE` — `mapper/F2fOrderMapper.java:50`
- **`updateStatus` 推进到 `FULFILLED` 时顺带落 `FULFILL_TMS`，条件写成幂等的 `NVL`**：重复上报被幂等挡住前若已推进过，不覆盖首次履约时间。`F2fOrderMapper.xml:179`、`:184`
- **`markPaid` 前置状态白名单只允许 `CREATED` / `PAYING`。** `F2fOrderMapper.xml:201`
- **`selectExpiredCandidates` 的 `earliestExpireTms` 是放弃窗口下界，MUST 传、NEVER 去掉。** 没有下界时，一笔支付中心永远查不到的订单会被每轮扫到、每轮外呼一次且永不收敛 —— 2026-09-10 实测：订单 `F200202609100914540082` 每 30 秒一次 `payQuery`、回 `9999 未找到数据`，持续 40 分钟没有出口。超过下界的单不再被扫，改由 `countStaleExpired` 计数告警 + 人工判定。`F2fOrderMapper.selectExpiredCandidates` — `mapper/F2fOrderMapper.java:121`；`F2fOrderMapper.xml:237`
- **`countStaleExpired` 计数的这批单「不会再自动收口」**，计数只为让问题可见 —— 持续大于 0 说明有一批单需要人工判定。`mapper/F2fOrderMapper.java:134`；`F2fOrderMapper.xml:251`
- **运营端分页 `selectPage` 的调用方 MUST 先保证有检索范围**（订单号 / 支付中心订单号 / 完整时间范围之一），否则这条 SQL 会全表扫 `F2F_ORDER`；该表按月分区且是核心交易表，无条件全扫会拖垮设备链路，controller 层已挡在前面。`mapper/F2fOrderMapper.java:149`
- **`Page_Where_Clause` 被三个 select 共用，因此三个方法的入参 MUST 一致**，缺一个 MyBatis 会报 `Parameter not found`。渠道订单号（`payCenterChannelOrderNo`）是旧 page 就有的检索维度，2026-09-11 新旧双打实测旧服务支持、新服务漏了这一维，据此补齐。`F2fOrderMapper.xml:277`
- **`updateRefundSummary`：退款只动 `REFUND_STATUS` / `REFUND_AMOUNT` / `LAST_REFUND_TMS` 三列，NEVER 改支付 / 履约主状态（ADR-D88）**，口径与 pay-sign 的 `PayTxnDetailMapper.updateRefundSummary` 一致。**重算、不累加**，执行 1 次与 N 次落库值相同，因此**没有前置状态白名单、也不需要 CAS**；返回 1 只表示订单行存在，**NEVER 拿返回值判断「退款成功没成功」**。已退总额为 0 时写 `NONE`。可退口径用 `ORDER_AMOUNT`，与 `F2fPageRefundService` 取整单金额的口径一致。`mapper/F2fOrderMapper.java:201`、`:205`；`F2fOrderMapper.xml:391`、`:401`
- **`countUnsettledRefunds` 是跨来源重复退款的防线，发起任何新退款前 MUST 先查它**：唯一函数索引 `UK_F2F_REFUND_IDEM` 只挡「同一订单 + 同一 `REFUND_SOURCE`」，挡不住「运营端退过、BOM 设备再退」。主状态不再走 `REFUNDING` 之后，这条查询接替了原先「状态是 REFUNDING 就拒绝」的作用。**`INIT` / `PROCESSING` / `MANUAL` 三者都 MUST 拦** —— `MANUAL` 的语义是「本系统已放弃自动判定、钱退没退未知」，放行等于允许在一笔可能已成功的退款之上再退一次。`mapper/F2fOrderMapper.java:216`、`:221`；`F2fOrderMapper.xml:432`、`:436`
- **`F2F_PAYMENT` 三条约束（一行一次尝试）**：① 插入一次尝试只 INSERT，`ATTEMPT_NO` 由 `UK_F2F_PAY_ATTEMPT (ORDER_NO, ATTEMPT_NO)` 保证唯一，并发下重复序号抛 `DuplicateKeyException`，由 application 层重取序号或幂等返回；② **标记成功靠 `UK_F2F_PAY_SUCCESS` 兜底**（函数索引 `CASE WHEN PAY_STATUS='SUCCESS' THEN ORDER_NO ELSE NULL END`，一笔订单最多一条 SUCCESS），因此 `markSuccess` 直接条件更新、**NEVER 先查有没有成功记录再更新**，第二条 SUCCESS 被唯一索引挡住抛 `DuplicateKeyException`，**调用方 MUST 把该异常按「已有成功记录」处理**；③ `markSuccess` / `markFinalStatus` 前置状态必填，**NEVER 写成「非终态即可更新」**。`mapper/F2fPaymentMapper.java:14`~`:25`；`F2fPaymentMapper.xml:116`、`:119`
- **`PAY_STATUS=UNKNOWN` 表示对端未明确应答、需靠查询接口收口，不得直接判失败**；因此 UNKNOWN 在 `markSuccess` / `markFinalStatus` 的前置白名单里是允许状态，由收口任务据查询结果推进到 SUCCESS 或 FAILED。**NEVER 把 UNKNOWN 直接改成 FAILED** —— MUST 先经支付中心查询接口确认。`mapper/F2fPaymentMapper.java:28`、`:96`；`F2fPaymentMapper.xml:117`
- **`markSuccess` 的 `payChannelCode` 传 null 时 SQL 用 `NVL` 保留原值，NEVER 因为一次没带就把已有渠道码抹掉**；查询接口回吐的 `paymentChannelCode` 取自本列。**`COST_MS` 不在此处改写**：耗时归属发起请求那一刻，回调不覆盖。`mapper/F2fPaymentMapper.java:78`；`F2fPaymentMapper.xml:120`、`:121`
- **`selectMaxAttemptNo` 的返回值只是建议值**：该订单无任何尝试时 `MAX` 返回 NULL、映射为 Java null，调用方从 1 开始；真正的唯一性由 `UK_F2F_PAY_ATTEMPT` 保证。`mapper/F2fPaymentMapper.java:116`；`F2fPaymentMapper.xml:163`
- **`selectByPayCenterOrderNo` 查不到时调用方 MUST 据此拒绝未知回调，而非新建记录。** `mapper/F2fPaymentMapper.java:65`
- **`F2F_TICKET` 四条约束**：① 出票上报一次带多张票走 `batchInsert`，XML 用 Oracle 的 `INSERT ALL ... SELECT * FROM DUAL`，**NEVER 改成 MySQL 的多值 VALUES 语法**（Oracle 不支持）；② 只 INSERT 不先查后插，重复上报靠 `UK_F2F_TICKET_LOGIC (TICKET_LOGIC_NUM, TRANS_DATE)` 抛 `DuplicateKeyException`；③ `selectLatestByLogicNum` 必须带 `ORDER BY TRANS_DATE DESC` + `FETCH FIRST 1 ROWS ONLY`（命中 `IDX_F2F_TICKET_RECENT`），**NEVER 去掉** —— 旧实现同类查询既无排序也无取一行，多笔命中直接抛 `TooManyResultsException`，这是已记录的缺陷；④ `updateStatus` 的 `fromStatuses` 必填。`mapper/F2fTicketMapper.java:12`~`:25`、`:69`；`F2fTicketMapper.xml:61`、`:114`
- **`markRefunded` 前置状态白名单为 `REFUNDING` / `ISSUED` / `FAULT`**，已是 `REFUNDED` 的票命中 0 行，调用方据此拒绝重复退款。`mapper/F2fTicketMapper.java:89`；`F2fTicketMapper.xml:143`
- **`F2F_REFUND` 最关键的约束：退款防重靠唯一函数索引，NEVER 靠先查后插。** `UK_F2F_REFUND_IDEM (ORIG_ORDER_NO, NVL(TICKET_LOGIC_NUM,'#WHOLE#'), REFUND_SOURCE)` 表达「同一原订单 + 同一票 + 同一退款来源只能有一条退款单」；整单退时 `TICKET_LOGIC_NUM` 为空、索引用 `#WHOLE#` 占位，避免多笔整单退绕过约束。并发下「先 SELECT 判断是否已退过再 INSERT」无效 —— 两个线程都查不到就都插进去，等于重复退款，**这是旧实现的最高优先级缺陷**；重复由唯一索引抛 `DuplicateKeyException`，**NEVER 在 SQL 里做判重**。`mapper/F2fRefundMapper.java:13`~`:21`、`:43`；`F2fRefundMapper.xml:48`~`:54`
- **`selectRetryCandidates` 谓词里 NEVER 再加 `RETRY_TIMES` 上限**：次数上限确实存在，但它在 application 层判定并显式置 `MANUAL`；搬进 SQL 就变成「次数一到直接从扫描结果里消失」，状态停在 `INIT` / `PROCESSING`、无告警无人工入口，**那正是原实现的缺陷**。WHERE 与 ORDER BY MUST 保持 `IDX_F2F_REFUND_SCAN (REFUND_STATUS, NEXT_QUERY_TMS)` 的列序，显式 `ORDER BY NEXT_QUERY_TMS` 是 `FETCH FIRST` 的前提。`mapper/F2fRefundMapper.java:27`~`:31`、`:89`；`F2fRefundMapper.xml:128`、`:133`
- **`updateStatus` 的 `PAY_CENTER_REFUND_NO` 与 `FINISH_TMS` 传 null 时用 `NVL` 保留原值**，避免回调补号后被后续状态推进擦掉；目标状态为 `SUCCESS` / `FAILED` 时同时回填 `FINISH_TMS`；返回 0 行时**调用方 MUST 据此判断并拒绝、NEVER 忽略返回值**。`mapper/F2fRefundMapper.java:73`、`:79`；`F2fRefundMapper.xml:111`
- **`markRetry` 的三件事（累加 `RETRY_TIMES`、记失败原因、排下次查询时刻）MUST 在同一条 UPDATE 里**：分成两条会出现「次数加了但没排下次」的中间态，那笔单下一轮立刻被再查一次、退避形同虚设。**不动 `REFUND_STATUS`**：状态推进只走 `updateStatus`，避免重试计数顺带把状态改成非法值。`mapper/F2fRefundMapper.java:103`、`:106`；`F2fRefundMapper.xml:150`、`:152`
- **`selectByOrigOrderNo` 一个订单可能有多条退款单（按票退 + 不同来源），调用方 MUST 自行按业务口径筛选。** `mapper/F2fRefundMapper.java:58`；`F2fRefundMapper.xml:93`
- **`F2F_RESULT_REPORT` 三条约束**：① 写入只 INSERT、**NEVER 先查后插** —— 规格要求设备断网后重传，并发下先查后插无效；重复上报由 `UK_F2F_REPORT_IDEM (REPORT_TYPE, ORDER_NO)` 抛 `DuplicateKeyException`，application 层捕获后当作「已收到过」返回成功，**这正是断网重传要的效果**；② 接收与后续动作解耦（退款、订单状态推进不在接收链路里做，由 `selectPendingReports` 扫表驱动）；③ `markProcessed` 的 WHERE 带 `PROCESSED = '0'`，返回 0 表示该条已被其他线程处理，**调用方 MUST 据此跳过**。`mapper/F2fResultReportMapper.java:14`~`:22`
- **`selectPendingReports` 的静默期 `staleBefore` 与 `reportTypes` 都不是可选的**：接收链路是「落票 → 落上报 → 推进订单 → 差额退款 → 入队通知」，落上报之后那几步还在同一个请求里跑，不排除刚落库的行会让补偿任务与首报**并发**做同一件事（三步虽都幂等，但会白发一遍支付中心退款查询）；本表还承载 `BOM_BIZ_RESULT` / `TOPUP_OK` 等类型，它们的后续动作在各自链路里内联完成、不该被本任务重放，不过滤会让那些行被反复扫到又什么都做不了。`mapper/F2fResultReportMapper.java:64`、`:68`
- **`updateOptResult` 用幂等键而不是自增 id 定位**：`insert` 不回填主键，而 `(REPORT_TYPE, ORDER_NO)` 本身就是 `UK_F2F_REPORT_IDEM`、唯一。`mapper/F2fResultReportMapper.java:92`
- **`selectByOrderNo` 的 `orderNo` 在 `ERROR_CODE=2101` 时是取票二维码的 `randomFact`。** `mapper/F2fResultReportMapper.java:47`
- **`F2F_DEVICE_STATUS` 三条约束**：① **心跳更新只能用单条 `MERGE INTO`，不是 insert / update 两步** —— 主键是复合主键 `(CHANNEL, DEVICE_ID)`、一台设备只有一行，先 SELECT 判断存在再决定 insert 还是 update，在每 1 分钟并发心跳下会撞主键（同一设备重连、多线程重投都会命中同一行）；本项目的约定是「唯一索引 / 主键 + MERGE INTO」而不是应用层判断，见 AGENTS.md §2.2.1。② **只存最新状态不存历史**，MERGE 命中已有行时覆盖车站与心跳时间、`HEARTBEAT_COUNT` 自增，**NEVER 追加新行**。③ 离线判定不在心跳链路里做，由 `selectHeartbeatTimeout` + `markOfflineByDeadline` 的扫表任务完成、命中 `IDX_F2F_DEVICE_HB`。`mapper/F2fDeviceStatusMapper.java:13`~`:23`；`F2fDeviceStatusMapper.xml:29`~`:31`
- **心跳 MERGE 的三个细节**：`STATION_CODE` 用 `NVL` 保护（本次心跳未带车站时保留已有值、不被 NULL 覆盖）；`ONLINE_FLAG` 置 `'1'`（超时被置离线的设备重新上报即自动恢复在线）；`ON` 子句里的 `CHANNEL` / `DEVICE_ID` 是主键、**Oracle 不允许在 UPDATE SET 中出现**，故未列入。目标表不起别名，因此 UPDATE SET 右侧可直接写 `F2F_DEVICE_STATUS.HEARTBEAT_COUNT + 1`。`F2fDeviceStatusMapper.xml:32`~`:35`；`mapper/F2fDeviceStatusMapper.java:32`
- **`F2F_ORDER_NO_SEQ` 建为 `MAXVALUE 9999 + CYCLE`，返回值恒在 1~9999，订单号序列段定长 4 位**；SQL 写在 XML 里、不用 MyBatis 注解（AGENTS.md §5.1：统一走自研 mybatis-adaptor，SQL 不进 Java 注解），旧实现 `OrderSeqMapper` 用 `@Select` 把 SQL 写在注解里、本模块不沿用。`mapper/F2fSequenceMapper.java:6`、`:15`；`F2fSequenceMapper.xml:11`
- **`GateTxnPayMapper` 现在只剩只读查询，NEVER 在本接口 / 本文件里加回任何 `GATE_TXN_PAY` 的写方法 / 写语句**：`GATE_TXN_PAY` 的 owner 是 gate-txn-pay-server。剩下的 `selectByOrderNos` 仍是**跨域读**（共享 Oracle schema），属有意保留的现状；**若未来拆库，它也 MUST 改 RPC**。该查询不带 `TXN_DATE` 因此走不到分区裁剪、只命中 `UK_GATE_TXN_PAY_ORDER_NO` 的前缀列，**调用方 MUST 限制列表长度（Oracle IN 列表上限 1000）**。`mapper/GateTxnPayMapper.java:20`、`:22`、`:30`；`GateTxnPayMapper.xml:15`、`:17`、`:36`
- **补款单状态推进用白名单而非黑名单**：`SUCCESS` / `FAIL` / `CLOSED` 已是终态，**重复回调 NEVER 改写**；`updatePreOrderResult` 的 WHERE 只认 `INIT`，同一笔重复调预下单时第二次返回 0 行，**上层收到 0 行 MUST 改为回放库里已有的 `PAYMENT_INFO`、NEVER 再向支付中心预下单一次**。`mapper/SupplementOrderMapper.java:29`、`:50`
- **强制销账的三个渠道凭据参数 MUST 一起传**：分两步写会留下「状态已 SUCCESS 但商户单号为空」的行；白名单只放 `CLOSED` / `FAIL` 两个状态 —— 作废 / 关单 / 判失败与乘客付款之间存在毫秒级竞态，**钱既然收了就 MUST 用来清欠费**。`mapper/SupplementOrderMapper.java:66`~`:68`
- **对账抽取只产出 `ITP.PAY` 与 `ITP.BUS` 两类汇总**，全部方法都是聚合查询、没有明细分页；`ITP.EXP` 与 `ITP.DETAIL` 与当面付无关，由 gate-txn-pay 与 daily-ticket 负责。`mapper/F2fReconExportMapper.java:17`；`F2fReconExportMapper.xml:10`
- **对账九条共同约定**（`F2fReconExportMapper.xml:6`~`:83`）：② 时间窗口 `O.PAID_TMS >= windowStart AND < windowEnd` **左闭右开**，`PAID_TMS` 是真 `TIMESTAMP(6)`、绑定用 `jdbcType=TIMESTAMP`、参数由 Java 侧传 `java.sql.Timestamp`，**NEVER 把窗口参数写成 `'yyyy-MM-dd'` 字符串直接比 TIMESTAMP 列**（Oracle 走隐式转换，格式不匹配即 `ORA-01843`，account-server 的注册量统计已因同型问题炸过一次），**NEVER 对 `PAID_TMS` 套 `TO_CHAR` / `TRUNC` 放进 WHERE**（索引失效）— `:12`~`:17`；③ **窗口列与日期分组列 MUST 同源、都取 `O.PAID_TMS`** — `:19`；④ 成功状态白名单 `P.PAY_STATUS = 'SUCCESS'`，**NEVER 写成「非 FAILED 即成功」之类的黑名单**（中间态被当成已收款会让本期虚增收入且下期重复入账），`UK_F2F_PAY_SUCCESS` 保证一单最多一条成功支付、因此 INNER JOIN 后不会放大笔数 — `:24`~`:29`；⑤ `TXN_DATE` 段 `TO_CHAR(O.PAID_TMS,'YYYYMMDD')` 只出现在 SELECT / GROUP BY / ORDER BY、不进 WHERE — `:31`；⑥ 金额列 `NUMBER(12)` 单位分、`SUM` 外只包 `NVL`，**NEVER 套 `TO_NUMBER`**（那是 collect-pay 那个源为字符串金额列准备的），**金额取 `P.PAY_AMOUNT`（实付）、NEVER 取 `O.ORDER_AMOUNT`** —— 后者是下单金额，与实际到账可能不等（部分退款不改它、聚合码场景也不改它）— `:34`~`:38`；⑦ 车站段统一取 `NVL(O.STATION_CODE, O.ENTRY_STATION_CODE)`，两者都空时该段留空、**账仍在** — `:40`~`:44`；⑧ 两张表的别名固定 `O` / `P`、全部列名都带前缀，不依赖「两表无同名列」这一偶然事实（它们都有 `ORDER_NO` / `CREATE_TMS`），**下次若引入第三张表 MUST 同样给它别名并给所有列加前缀** — `:69`~`:72`。
- **`BIZ_TYPE` 到 `ITP.PAY` 度量段的映射（0 基下标）**：`01` + `CHANNEL IN ('02','03')` → idx 5/6 BOM/TVM 发售；`01` + `CHANNEL='01'` → idx 13/14 APP 购票；`02` → idx 7/8 BOM/TVM 充值；`04` + `TRANS_TYPE='42'` → idx 15/16 BOM 行政处理；`04` + `TRANS_TYPE` 非 42 → idx 19/20 BOM 处理。**`BIZ_TYPE='03'`（取票）不产出任何度量** —— 取票是 APP 购票单的履约动作，钱在购票那一单已经计过，再计一次就是重复入账。恒 0 的三组是旅游票（9/10，归 daily-ticket）、过闸（11/12，归 gate-txn-pay）、单边交易（17/18，属 `ITP.EXP` 口径、本模块无判据）。`F2fReconExportMapper.xml:74`~`:83`；`mapper/F2fReconExportMapper.java:50`、`:65`、`:73`、`:79`、`:93`
- **`04` 的两组构成完全二分，改任一条的谓词 MUST 同时改另一条**，否则 BOM 非现金收款会漏账或被两组重复计入。`mapper/F2fReconExportMapper.java:97`；`F2fReconExportMapper.xml:157`
- **`ITP.BUS` 键只有日期一段，NEVER 再带车站 / 设备 / 支付方式**（多带一段会让 recon-server 按 1 段键聚合时把维度信息当成度量列错位读取）；口径 MUST 与 `ITP.PAY` 五组的并集一致（`BIZ_TYPE IN ('01','02','04')`），**两类文件取自同一批成功支付，口径分叉会让 ACC 侧两份文件对不上**。优惠金额本模块恒 0（两表都没有优惠 / 折扣金额列），**NEVER 拿 `ORDER_AMOUNT` 与 `PAY_AMOUNT` 之差去推算** —— 那个差额可能来自部分退款、聚合码场景或脏数据，算出来的不是优惠。`mapper/F2fReconExportMapper.java:106`；`F2fReconExportMapper.xml:206`~`:213`
- **退款不参与对账扣减**：`F2F_REFUND` 一行都不读，与 collect-pay 源同口径（甲方 PAY / BUS 规格里没有退款段）；**NEVER 顺手减 `REFUND_AMOUNT`** —— 那会让同一笔在退款当日的账与购买当日的账互相抵扣、两天都对不上。`mapper/F2fReconExportMapper.java:42`
- **两个漏账探针 NEVER 删**：探针一 `countUncoveredPaidOrders`（窗口内有成功支付但 `BIZ_TYPE` 不在白名单 `('01','02','04')` 的单数）—— 白名单谓词导致新增业务类型或取票单意外挂上成功支付时那部分钱**不进任何一段度量、也不报错**，纯文本对账文件没有 schema、下游读不出异常，**这是本项目反复踩过的「静默漏账」类缺陷的唯一出口**；探针二（支付已成功按 `P.FINISH_TMS` 落窗、但订单 `PAID_TMS` 为空的单数）—— **这是唯一能发现这种漏账的地方**。`mapper/F2fReconExportMapper.java:115`、`:120`、`:126`；`F2fReconExportMapper.xml:232`、`:248`
- **`COUNT` / `SUM` 的结果在 `Map<String,Object>` 里是 `BigDecimal`，取值 MUST 经调用方的转换方法，直接强转 `(Long)` 会 `ClassCastException`。** `mapper/F2fReconExportMapper.java:39`

#### 7.2 决策理由

- **`selectStatusForUpdate`（只取状态一列）与 `selectByOrderNo` 并存是有意的**：这条只在 CAS 返 0 行后的冲突分支被调到，形态对齐 pay-sign-server 的 `selectSignStatusBySeq`；**NEVER 改成用 `selectByOrderNo` 再取字段** —— 那是为了一列去读整行。「只在冲突分支调」是三件套规范的一部分：CAS 命中就不回查；订单不存在时返回 null，调用方按 `CONFLICT` 处理。`mapper/F2fOrderMapper.java:40`、`:43`；`F2fOrderMapper.xml:114`~`:116`
- **`FULFILL_TMS` 的回填放在 mapper 而不是各 service**：履约时间是对账与「支付→出票时长」统计的唯一来源，2026-09-10 BOM 售票重放实测发现它恒为 null（只改了状态没回填时间戳）；推进到 `FULFILLED` 的调用点有四处（`F2fTicketIssueService`、`F2fTopupService` 两处、`F2fBomOrderService`），**逐个加必然漏**。`F2fOrderMapper.xml:180`~`:183`
- **运营端分页用 `EXISTS` 子查询而不是 JOIN**：支付中心订单号与渠道订单号都在 `F2F_PAYMENT` 上，一笔订单可能有多次支付尝试，**JOIN 会把同一订单重复成多行、分页计数随之出错**。`F2fOrderMapper.xml:275`~`:276`
- **强类型分页 `selectPageView` 与旧 `selectLegacyPage` 的三条核心差异**：① 不再用裸 Map + `LEGACY_KEYS` 手动补齐，直接映射到 `FacePayOrderPageVO`；② 不再输出旧 `ItpStatusEnum` 的 `0/1/2/3` 状态码和 msg 文案 —— 前端直接消费域模型真实枚举值（`CREATED` / `PAID` / `FULFILLED` / `REFUNDED` 等），`orderAmount` / `ticketPrice` / `refundAmount` 为 `Long` 单位分，退款状态由独立列承载与主状态正交；③ 支付 / 退款相关列改为外层子查询分页 + `LEFT JOIN`，避免 6 个标量子查询，`Page_Where_Clause` 仍嵌在内层、保持 `EXISTS` 子查询的过滤作用且不影响外层 JOIN。`mapper/F2fOrderMapper.java:181`~`:184`；`F2fOrderMapper.xml:336`~`:341`
- **`LEFT JOIN` 里用 `(SELECT MAX(ATTEMPT_NO) ...)` 定位最后一次支付尝试的理由**：同一订单可能有多次支付尝试，直接 JOIN 会把单行订单膨胀成多行、分页计数随之出错；用标量子查询先定出最后一次 `ATTEMPT_NO` 再 `LEFT JOIN`，单行对单行、不影响外层分页。`F2F_REFUND` 和 `STATION_INFO` 同理。**NEVER 改成 `INNER JOIN` 或过滤 NULL** —— BOM 柜台售票不写 payment 行、也可能无进出站，这些行 `LEFT JOIN` 后对应列自然为 null，运营页靠 `isStationName || isStationCode` 退化显示。`F2fOrderMapper.xml:343`~`:349`
- **`updateRefundSummary` 为什么是重算而不是累加**：结果只取决于 `F2F_REFUND` 里 `REFUND_STATUS='SUCCESS'` 的行，执行 1 次和 N 次落库值相同，也不依赖调用方传入的旧值快照。**累加写法在两笔退款并发时各自读到偏小的已退总额，双方都写偏小值，账面虚低且无法自愈**；对端已受理但本地回滚后补跑一次又会把同一笔算两遍、账面虚高，随后真实退款被误判超额并拒绝。**明细表 `F2F_REFUND` 才是唯一账本，本表三列只是它的投影。** 已退总额为 0 时写 `NONE`（说明还没有任何一笔退款成功，可能都停在 `INIT` / `MANUAL`，此时既不该写 `PARTIAL` 也不该写 `SUCCESS`）。**NEVER 加前置状态白名单**：本语句幂等，加了反而会在重入时命中 0 行、汇总永远停在旧值。`F2fOrderMapper.xml:395`~`:405`
- **`convergeDebitStatus` 于 2026-09-16 整段删除并迁到 owner 侧**：跨域直写别人域的热路径表违反「热路径写入定 owner」判据 —— 同一张表两个模块各持一份 UPDATE，白名单一旦漂移就是资金账不平，**而编译、单测都发现不了**。收敛现走 RPC：`GateTxnPayClient.convergeDebitStatusForSupplement` → `POST /internal/gate-txn-pay/debit/converge`，对端语句名 `convergeDebitStatusForSupplement`、白名单含 `FAIL`、行为与删掉那条逐笔一致。`mapper/GateTxnPayMapper.java:14`~`:19`；`GateTxnPayMapper.xml:9`~`:15`
- **`SupplementOrderMapper` 是 PayCenter 直连架构下的精简版**：与原 gate-txn-pay 版本的差异 —— ① 去掉 `SALE_SYNC_*` 四列与对应 `insert` / `selectPendingSaleSync` / `markSaleSync*` 三条方法（CollectPay 链路的 outbox 四列读写在直连架构下不需要）；② `insert` 不写 `SALE_SYNC_*` 列（DB 中仍存在但留 NULL，SQL 里显式列清单列名不变）。`mapper/SupplementOrderMapper.java:13`~`:15`；`SupplementOrderMapper.xml:6`~`:8`
- **`selectUnsettled` 还会把 `closedLookbackHours` 小时内的 `CLOSED` / `FAIL` 单一起捞回来**，专为「作废 / 关单 / 判失败后乘客仍完成了支付」兜底。`mapper/SupplementOrderMapper.java:77`~`:79`；`SupplementOrderMapper.xml:182`
- **对账抽取刻意独立成一个 mapper、现有 mapper / XML 一行都没改**：对账抽取是批处理口径（扫时间窗口、只取成功支付、库内 `GROUP BY`），与联机查询的口径和索引策略完全不同，**混进现有 mapper 后任何一方调过滤条件都会误伤另一方**。返回类型统一 `Map<String,Object>`：聚合只产出少数几列，映射成 30 余列的实体等于每行多背一堆 null 字段。`mapper/F2fReconExportMapper.java:13`、`:38`
- **与 collect-pay 那个源的两处关键差异（改动时 MUST 一并看齐）**：① 时间列是真 `TIMESTAMP(6)` 不是 `VARCHAR2` —— collect-pay 的四张旧表把时间存成 `VARCHAR2(100)` 的 `yyyy-MM-dd HH:mm:ss`、因此直接做字符串比较；② 金额列是 `NUMBER(12)` 单位分、**NEVER 套 `TO_NUMBER`** —— collect-pay 那边套是因为旧表金额列是字符串。`mapper/F2fReconExportMapper.java:20`~`:29`
- **窗口列与分组列同源导致分区裁剪失效，这是有意的取舍**：`F2F_ORDER` 按 `CREATE_TMS` 做 `INTERVAL` 月分区，按 `PAID_TMS` 过滤扫全表；**正确的账期口径优先于扫描量**，且对账是每日一次的批处理。`F2fReconExportMapper.xml:21`~`:22`
- **「BOM 行政处理」这一组在 collect-pay 源里恒 0、本源能算出来**：旧表 `TBL_BOM_ORDER_PAY` 的 `TRANS_TYPE` 取值在仓库内三处证据互相矛盾（`BomNoCashOrder` 的注释清单里没有「发售」、`BomBusinessCodeEnum` 的 `01` 与 `22` 描述都写「充值」、`BomOrderServiceImpl:950` 又把发售硬编码成 `01`），所以那边只能整表归「发售」组；而 `F2F_ORDER` 用 `BIZ_TYPE` 表达业务类型、`TRANS_TYPE` 只表达 BOM 交易类型，两者正交，`42 行政处理` 的语义在列注释里是唯一的（细分由 `ADMIN_TRANS_TYPE` 的 `01~0A` 承载）。**NEVER 因为「collect-pay 那边是 0」就把本组也写成 0。** `mapper/F2fReconExportMapper.java:81`~`:87`；`F2fReconExportMapper.xml:153`~`:156`
- **线路段（`ITP.PAY` 第 2 段）本模块一律不出，2026-09-16 起由 recon-server 在聚合完成、写文件之前按车站码统一补齐**（见该模块的 `ReconStationMapper` 与 `recon.line-backfill.*` 配置），本文件因此删掉了原先五条 PAY 汇总里各自一份的 `LEFT JOIN STATION_INFO S ON S.STATION_CODE = NVL(O.STATION_CODE, O.ENTRY_STATION_CODE)` 与 SELECT / GROUP BY / ORDER BY 里的 `S.LINE_CODE`。收口判据（**这段替换了本文件此前那段「有意破例」的说明，该说明已作废、NEVER 回退**）：`STATION_INFO` 属车站 / 参数域、owner 不是 face-pay，原先四个源各写一遍这个 join，等于「线路怎么取」有四份副本、四份各自的 join 列名、四次维表结构变更的暴露面；而线路是车站的函数（一个车站码只对应一条线路），完全可以在聚合完成后由消费端一次补齐。实际动到 gate-txn-pay / collect-pay / face-pay 三个源 + recon-server 四个模块（daily-ticket 本来就没有这个 join；ticket-server 那处 join 在明细 EXP 里、明细走字节流式拼接不解析行内容、recon-server 补不了，而且 ticket 已不在期望清单，故不动）。`F2fReconExportMapper.xml:46`~`:60`
- **`ITP.PAY` 充值组不按 `CHANNEL` 拆**：甲方段名本身就是「BOM/TVM 充值」合并一段；充值单没有入站车站，车站段实际取到的是 `STATION_CODE`（设备所在车站）。`F2fReconExportMapper.xml:130`~`:131`
- **APP 购票组的 `DEVICE_ID` 通常为空，仍照常放进分组键、为空时该段留空**（APP 单没有受理设备）。`mapper/F2fReconExportMapper.java:67`；`F2fReconExportMapper.xml:106`
- **`selectPendingReports` 的三个谓词都是必填、没有可选分支**，因此不会踩 Druid WallFilter 那条「恒真条件成为唯一谓词」的规则（AGENTS.md §5.1）。`F2fResultReportMapper.xml:104`
- **`insert` 语句显式传 `CREATE_TMS` / `UPDATE_TMS`（列上有 `DEFAULT SYSTIMESTAMP`）是为了单测可控**；`ID` 由 `GENERATED BY DEFAULT AS IDENTITY` 生成、不在插入列里。四张表同款说明：`F2fOrderMapper.xml:63`~`:64`、`F2fPaymentMapper.xml:45`~`:46`、`F2fTicketMapper.xml:34`~`:35`、`F2fRefundMapper.xml:44`~`:46`（`F2F_REFUND` 另有 `RETRY_TIMES` 有 `DEFAULT 0`、为空时传 0 保持 NOT NULL）。

#### 7.3 陷阱

- **`fromStatuses` 传空集合时 `foreach` 会生成 `IN ()`、Oracle 直接报语法错误，这是有意的** —— 比静默放行所有状态安全；调用方 MUST 传非空集合。四处同款：`F2fOrderMapper.xml:176`~`:177`、`F2fPaymentMapper.xml:141`~`:142`、`F2fTicketMapper.xml:127`~`:128`、`F2fRefundMapper.xml:109`~`:110`（`selectRetryCandidates` 的 `statuses` 同理 — `F2fRefundMapper.xml:131`）；Java 侧 — `mapper/F2fPaymentMapper.java:95`
- **`batchInsert` 的 `tickets` 为空时 `foreach` 不产出任何 `INTO` 子句、语句非法并直接报错，这是有意的** —— 比静默插入 0 行更容易发现调用方传空集合。`F2fTicketMapper.xml:66`~`:67`
- **`FETCH FIRST` 必须配显式 `ORDER BY`，否则每批取到的行集不确定**：`F2fDeviceStatusMapper.xml:72`（无排序时早到期的行可能长期被饿死 — `F2fNotifyTaskMapper` 同款）；`FETCH FIRST` 需 Oracle 12c+ — `F2fOrderMapper.xml:162`、`F2fPaymentMapper.xml:84`、`F2fTicketMapper.xml:116`。
- **旧实现同类查询既无排序也无取一行，多笔命中直接抛 `TooManyResultsException`**（§14.6 第 2 条已记录的缺陷）：`selectLatestByLogicNum`（订单）— `mapper/F2fOrderMapper.java:67`、`F2fOrderMapper.xml:161`；`selectLatestByLogicNum`（票）— `mapper/F2fTicketMapper.java:22`、`F2fTicketMapper.xml:115`；`selectLastAttempt` — `mapper/F2fPaymentMapper.java:48`、`F2fPaymentMapper.xml:83`。
- **支付中心订单号无唯一约束（重试可能复用同号）**，因此 `selectByPayCenterOrderNo` 显式排序取最新一行，避免多笔命中抛 `TooManyResultsException`。`F2fPaymentMapper.xml:104`
- **`selectByDeviceSeq` 的 `deviceSeq` 为 null 时索引不生效，此处不允许传 null**（索引三列都参与匹配）。`mapper/F2fOrderMapper.java:77`
- **`(O.TRANS_TYPE IS NULL OR O.TRANS_TYPE <> '42')` MUST 这么写**：`TRANS_TYPE` 可空，而 **Oracle 里 `NULL <> '42'` 结果是 UNKNOWN、不是 TRUE**，只写不等号会把所有 `TRANS_TYPE` 为空的 BOM 单静默漏掉、二分随之破裂。`F2fReconExportMapper.xml:181`~`:183`
- **`ROWNUM` 截断 MUST 写在外层内联视图上**：Oracle 的 `ROWNUM` **在 `ORDER BY` 之前求值**，同层写就变成「随便取 N 行再排序」。`SupplementOrderMapper.xml:180`~`:181`
- **`SUM` 会跳过 NULL、全组皆 NULL 时返回 NULL**，所以外面只包 `NVL` 兜 NULL。`F2fReconExportMapper.xml:34`
- **`PAID_TMS` 可能为空却支付已成功**：`PAID_TMS` 由 `markPaid` 写入、其 WHERE 是 `ORDER_STATUS IN ('CREATED','PAYING')` 的状态白名单，若支付成功回调到达时订单状态已不在白名单内，则该行 `PAID_TMS` 一直为空、被五条汇总 select **静默丢掉**。`mapper/F2fReconExportMapper.java:129`~`:133`；`F2fReconExportMapper.xml:244`~`:248`
- **`markProcessed` / `markOffline` / `updateStatus` 系列返回 0 行有多种成因，不能一律当失败**：`markOffline` 返回 0 表示「不存在或已离线」— `F2fDeviceStatusMapper.xml:83`；扫表与置离线之间若设备恰好补上心跳（`LAST_HEARTBEAT_TMS` 被 MERGE 推后），该行不会被误置离线 — `mapper/F2fDeviceStatusMapper.java:80`、`F2fDeviceStatusMapper.xml:94`~`:95`。
- **从未上报心跳的设备 `selectByDeviceId` 返回空**（按复合主键取唯一一行）。`F2fDeviceStatusMapper.xml:61`
- **`insert` 时若 `PAY_STATUS` 直接给 `SUCCESS`，还会额外受 `UK_F2F_PAY_SUCCESS` 约束（一单一条 SUCCESS）**；`markFinalStatus` 的 `toStatus` 取值受 `CK_F2F_PAY_STATUS` 约束、`toStatus=SUCCESS` 同样额外受 `UK_F2F_PAY_SUCCESS` 约束。`F2fPaymentMapper.xml:48`、`:143`
- **`F2F_ORDER_NO_SEQ` 与旧服务的 `ORDER_NO_SEQ` 不共用是刻意的**：蓝绿并行期两套服务各自发号，旧服务回滚也不会因新服务消耗过序列而出现空洞。`mapper/F2fSequenceMapper.java:17`~`:18`；`F2fSequenceMapper.xml:12`


## 墓碑注释清单（建议转为断言测试）

> 以下注释形态是「本类不再持有 X / 本方法已迁走 / NEVER 在这里加回 Y」，按用户要求不进正文，单独列出。路径前缀同上：Java `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/`，mapper XML `face-pay-server/src/main/resources/mapper/`。每条给出它想禁止的具体事情。

1. `mapper/GateTxnPayMapper.java:14`~`:20` —— 禁止在 `GateTxnPayMapper` 接口里加回任何 `GATE_TXN_PAY` 的**写方法**（`convergeDebitStatus` 已于 2026-09-16 删除，收敛改走 RPC `POST /internal/gate-txn-pay/debit/converge`）。断言点：该接口不出现除 `selectByOrderNos` 以外的方法、不出现 `update` / `insert` 前缀方法。
2. `GateTxnPayMapper.xml:9`~`:15` —— 禁止在该 XML 里加回任何 `GATE_TXN_PAY` 的写语句。断言点：文件内不存在 `<update>` / `<insert>` / `<delete>` 节点。
3. `service/supplement/SupplementPayCenterFlow.java:43`~`:46` —— 禁止在本类里再注入 face-pay 的 `GateTxnPayMapper` 做写操作（收敛原过闸订单只能走 RPC）。断言点：本类字段中无 `GateTxnPayMapper`。
4. `mapper/SupplementOrderMapper.java:13`~`:15` —— 禁止加回 CollectPay 链路的 outbox 四列读写方法（`markSaleSyncSuccess` / `markSaleSyncFailed` / `markSaleSyncRejected` / `selectPendingSaleSync`）。断言点：接口内不存在这四个方法名。
5. `entity/SupplementOrder.java:15`~`:19` —— 禁止把 outbox 四列（`saleSyncStatus` / `saleSyncRetryCount` / `saleSyncTime` / `saleSyncResult`）加回实体（DB 列仍在但恒 NULL）。断言点：实体无这四个字段。
6. `SupplementOrderMapper.xml:6`~`:8` —— 禁止在 `insert` 的列清单里写 `SALE_SYNC_*` 列。断言点：`insert` 语句正文不含 `SALE_SYNC`。
7. `service/supplement/SupplementOrderLocalWriter.java:44`~`:45` —— 明细的 `ACTIVE_ORIG_ORDER_NO` 已废弃、恒写 NULL，`UK_SUPPLEMENT_ITEM_ACTIVE` 因此永不触发；禁止再给该列赋非空值（否则「同一行程单可挂多张补款单」的无独占设计被破坏）。断言点：落单后该列为 NULL。
8. `service/F2fTvmOrderService.java:162`~`:166` —— 旧库只读旁路 `LegacyOrderReader` 已于 2026-09-13 整体删除；禁止因为「切流前 collect-pay-server 建的历史单在本服务一律 `2002 没有找到匹配的订单`」而把旁路加回来。断言点：仓库内无 `LegacyOrderReader` 引用；历史单查询返回 `2002`。
9. `service/ReconExportService.java:269`~`:272` —— 线路段已整体收口到 recon-server；禁止在 `line(...)` 组装方法里塞线路值、禁止把 `STATION_INFO` 的 join 加回 mapper、更禁止用车站码前 2 位推线路。断言点：`ITP.PAY` 第 2 段恒为空串。
10. `F2fReconExportMapper.xml:46`~`:67` —— 同上收口的 mapper 侧墓碑：禁止把 `LEFT JOIN STATION_INFO S ON S.STATION_CODE = NVL(O.STATION_CODE, O.ENTRY_STATION_CODE)` 与 `S.LINE_CODE` 加回五条 PAY 汇总；且此前那段「有意破例」的说明已作废、禁止回退。断言点：文件内无 `STATION_INFO`、无别名 `S`。
11. `mapper/F2fOrderMapper.java:181` / `controller/page/FacePayOrderPageController.java:38` / `api/page/FacePayOrderPageVO.java:6`~`:16` —— `selectLegacyPage`（裸 Map + `LEGACY_KEYS` + 旧 `ItpStatusEnum` 的 `0/1/2/3` 状态码与 msg 文案）已废弃；禁止让运营端分页再输出 `status="1"` 这类旧投影、禁止把金额改回 `TO_CHAR` 字符串、禁止把 `singleTicketType` 改回旧 `ticketType`。断言点：分页响应 `orderStatus` 为真实枚举值、金额为 `Long` 分。
12. `api/page/FacePayOrderPageVO.java:15`~`:16`、`:82`~`:83`（ADR-D88）—— 退款状态由 `refundStatus`（`NONE` / `PARTIAL` / `SUCCESS`）独立承载、与 `orderStatus` 正交；禁止让退款改写主状态。断言点：退款成功后 `orderStatus` 不变为 `REFUNDED`。
13. `service/F2fDeviceRefundService.java:31`、`:35`~`:36`（ADR-D88）—— 2026-09-15 起本类不再推进 `ORDER_STATUS`；禁止把「推 `REFUNDING` 的 CAS」加回来（主状态不再有 `REFUNDING` 这条边，加回去只会恒返 0 行、刷满假告警），超额退款改用「订单金额 - 已退金额」判据拦。断言点：本类无 `updateStatus(... REFUNDING ...)` 调用。
14. `mapper/F2fOrderMapper.java:218` / `F2fOrderMapper.xml:435` —— 主状态不再走 `REFUNDING`，「状态是 REFUNDING 就拒绝」的旧防线由 `countUnsettledRefunds` 接替；禁止把状态判定当作重复退款防线。断言点：发起退款前调用 `countUnsettledRefunds`。
15. `F2fOrderMapper.xml:405` —— 禁止给 `updateRefundSummary` 加前置状态白名单（本语句幂等，加了会在重入时命中 0 行、汇总永远停在旧值）。断言点：该 `<update>` 的 WHERE 只有 `ORDER_NO`。
16. `mapper/F2fRefundMapper.java:29`~`:31` / `F2fRefundMapper.xml:133`~`:135` / `entity/F2fRefund.java:84` / `scheduler/F2fRefundReconcileJob.java:33`~`:34` —— 禁止把 `RETRY_TIMES` 上限加进 `selectRetryCandidates` 的谓词、禁止再给本任务传 `maxRetryTimes`（按次数截断会让单静默掉出扫描范围、停在非终态无人管；停止条件在 `F2fRefundService` 按 `REQUEST_TMS` 时间窗判定并置 `MANUAL`）。断言点：SQL 无 `RETRY_TIMES` 谓词、Job 无 `maxRetryTimes` 参数。
17. `service/F2fAppOrderService.java:301`~`:302` —— 「已收款但订单没推到 PAID」的 ERROR 上报已收口到 `F2fPayCenterFlow.markPaidAndReport(String)`，本类不再自留副本；禁止在本类重新实现该上报。断言点：本类无该 ERROR 上报代码。
18. `service/F2fTvmOrderService.java:606` —— 冲突上报统一走 `F2fPayCenterFlow.warnIfConflict`，本类不再自留副本；禁止在本类重复实现冲突告警。断言点：本类无自建 `warnIfConflict` 逻辑。
19. `api/device/tvm/TvmResponses.java:27`~`:28` —— 设备退款 `IF2A requestRefund` 已于 2026-09-16 移出「纯错误体」名单（现回 3 个业务键，见 `refundSuccess`）；此前把它列在该名单的那行已作废、禁止回退。断言点：`requestRefund` 失败分支回同一套 3 键、值全 null。
20. `api/device/bom/BomResponses.java:23`~`:24` —— BOM 单程票退款 `requestTicketRefund` 同上，已于 2026-09-16 移出该名单、禁止回退。断言点同 19。
21. `service/F2fTakeTicketService.java:35`~`:37`、`:97` —— 「查不到取票订单」的两条 `2003` 分支已由「纯错误体」改成「与成功响应同形、8 个业务键值全为 JSON null」；禁止回退成旧 collect-pay 的 `failData(空订单)`（那是 `2999` 码）。断言点：`2003` 分支响应含 8 个业务键、值全 null。
22. `service/F2fTakeTicketService.java:30`~`:32` —— 鉴权查询不再依赖「多查一条就报失败」（旧 `selectByDeviceAndQrcode` 返回 List、命中多条回「查询到的订单数量过多，失败」）；禁止把 List 版查询加回来，三要素本应唯一、`selectByQrcode` 直接取一行。断言点：mapper 无返回 List 的二维码查询。
23. `service/F2fTakeTicketService.java:27`~`:29` —— 激活已改成条件更新抢锁；禁止回退成无条件 `UPDATE ... WHERE ORDER_NO=?`（两台设备同时扫同一个码会双双成功、订单被后写的那台覆盖）。断言点：`activateForDevice` 的 WHERE 含 `ACTIVATE_DEVICE_ID IS NULL`。
24. `controller/ci/app/AppOrderController.java:144`~`:147` —— APP 退款只校验 `orderNo`、不校验 `userId`（曾以「`userId` 要落到 `OPERATOR_ID` 供对账追溯」为由补上，按用户 2026-09-11「不校验」裁决移除）；禁止再加回该校验。断言点：缺 `userId` 时不返 `8003`。
25. `controller/ci/bom/BomOrderController.java:136`~`:141` —— BOM 支付只校验 `orderNo` 与 `paymentVendor`、不校验 `paymentCode`；禁止以「补齐校验」为理由加回来。断言点：缺 `paymentCode` 时不返 `8003`，而是走到支付中心后回 `0000 + paymentResult=FAILED`。
26. `controller/ci/bom/BomOrderController.java:255`~`:261` —— `requestUpdateCardData` 不校验 `updateType`、也不校验 `optDate` 的格式（曾按「补齐校验」加过这两条，用户 2026-09-11 裁决「不校验」已移除）；禁止以「旧实现漏校验」为理由加回来。断言点：这两种情况透传给 ticket-server、返回下游码。
27. `controller/page/FacePayOrderPageController.java:68`~`:72` —— `payCenterChannelOrderNo`（渠道订单号）是旧端点就支持的检索维度、也算一种检索范围；禁止再把它从入参里去掉。断言点：controller 存在该 `@RequestParam`，且三个 select 入参一致。
28. `controller/page/AppOrderPageController.java:20`~`:22`、`:50` —— 旧端点操作的 `TBL_TVM_APP_ORDER` / `TBL_APP_ORDER_REFUND` 不再有新数据（APP 取票订单已随 2026-09-15 切流落到 `F2F_ORDER` 的 `BIZ_TYPE='03'`），因此本模块必须自带同 URL 端点；另**禁止把可退余额判断挪到 controller**（那需要读订单与已退汇总，属业务逻辑，AGENTS.md §3.3）。断言点：URL `POST /page/app/orders/{orderNo}/refund` 存在；controller 内无余额计算。
29. `channel/app/AppNotifyProperties.java:50`~`:57`（ADR-D89）—— `payResultUrl` 的生产者已于 2026-09-15（1.0.36）补齐（`F2fPayCenterFlow.enqueuePayResultNotify`，覆盖三个支付成功收口点，集群 env `NOTIFY_APP_PAY_RESULT_URL` 也已配真实地址）；此前那段「没有任何生产者、env 已删除、分支走不到」已作废、禁止回退，更禁止据此删除该属性（2026-09-15 曾因大小写敏感 grep 漏掉 `getPayResultUrl` 而误删、构建即报「找不到符号」）。断言点：该属性存在且有读取方。
30. `channel/app/AppNotifyClient.java:45`、`:54`（ADR-D102）—— IF8B-05 `receivePaymentResult` 的 `7004` 已实测为对端该端点自身处理异常；禁止再为这条通知改报文形态或补字段、禁止把 `7004` 加进成功码把它藏起来。断言点：成功码集合只含 `0000` / `code=0`。
31. `service/F2fTicketIssueService.java:374`~`:378` —— 三步（状态推进 CAS / 差额退款撞 `UK_F2F_REFUND_IDEM` / 通知入队撞 `UK_F2F_NOTIFY_IDEM`）都幂等，因此重放安全；禁止在这里补「先查有没有做过」的判断（那等于把幂等从唯一索引挪到应用层，AGENTS.md §5.1 末条）。断言点：该方法无前置存在性 SELECT。
32. `mapper/F2fReconExportMapper.java:13`~`:15` —— 对账抽取刻意独立成一个 mapper、现有 mapper / XML 一行都没改；禁止把这些聚合查询合并进联机链路的 mapper（任一方调过滤条件都会误伤另一方）。断言点：`F2fReconExportMapper` 只被 `ReconExportService` 使用、且不出现在联机 service 中。
33. `api/device/tvm/RequestTopupReqDTO.java:54` —— 两个字段的合法区间不同，禁止再合并成同一个 parse。断言点：存在两个独立的解析方法/校验区间。
34. `service/F2fTopupService.java:282`~`:284` —— 旧实现的 `afterAmount` 直接复制 `transAmount`、数据是错的，本实现不再写该列；禁止把该列的赋值加回来。断言点：充值落库语句不含 `AFTER_AMOUNT` 赋值。

## 附：collect-pay-server 源码注释知识抽取（2026-09-16，阶段一）

> 本节与上一节（face-pay-server 的抽取）**并列、互不覆盖**，抽取源是 `collect-pay-server/src/main` 的 Java 注释、`resources/mapper/*.xml` 的 XML 注释与 `resources/application.yml` 的 `##` 注释（该模块是全项目唯一用 yml 的业务模块）。
>
> **行号会漂：引用任何一条前 MUST 先 grep 关键字确认位置。** 本次抽取当场撞到一例已漂的引用：`ReconExportMapper.java` 的 javadoc 与 `ReconExportMapper.xml` 的 XML 注释都写「`BomOrderServiceImpl.java:950` 把发售硬编码成 `setTransType("01")`」「`BomOrderServiceImpl.java:931` 的 `TRANS_TYPE` 取自请求体」，2026-09-16 实测这两处分别在 **`:962`** 与 **`:943`**。因此本节所有 `文件:行号` 只是入口坐标，不是断言。
>
> 定位串前缀：Java 侧 `collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/`，mapper XML 侧 `collect-pay-server/src/main/resources/mapper/`，配置 `collect-pay-server/src/main/resources/`。

### 附.1 契约与判据

#### 附.1.1 TVM 设备域

- **TVM 族错误码是 `2xxx`（`TvmPayCodeEnum`）**：`0000` 成功、`2999` 失败、`2001` 非法设备、`2002` 非法参数、`2003` 无激活的订单、`2004` 充值金额超限、`2005` 订单未支付、`2006` 订单号错误、`2007` 订单已退款、`2008` 订单已锁定。枚举里另有一行被注释掉的 `FAIL("2999","失败")` 副本，现行 `FAIL` 仍是 `2999`。`collect-pay-server` `TvmPayCodeEnum` — `constant/TvmPayCodeEnum.java:7`~`:19`
- **「无激活订单」的答复有两种形态，且与改动前逐字节一致**：三要素查不到行时回 `2003`；查到行但未激活时回**带空订单对象的失败结构**（`TvmOrderResult.failData(DeviceResponse.getQuerySuccessResult(new TvmAppOrder()))`）。原实现把「查不到行」那支的 `2003` 注释掉了，**NEVER 顺手改回去**。`collect-pay-server` `TvmTakeTicketServiceImpl.notActiveResult` — `service/impl/TvmTakeTicketServiceImpl.java:180`~`:193`
- **IF2A-04 出票结果通知的口径**：出票张数与订单张数一致才发送取票通知，此情况下不发生退款。`collect-pay-server` `TvmOrderController.notiTakeTicketResult` — `controller/ci/tvm/TvmOrderController.java:160`
- **IF2A-11 扫码支付的入参校验被注释掉了两条**：`orderNo` 为空、`paymentVendor` 为空的两条 `return RequestPaymentRespDTO.fail("8999", ...)` 现在都在注释里（注意 `8999` 是 BOM 族的失败码、不是 TVM 族的 `2999`）。`collect-pay-server` `TvmOrderController.requestPayment` — `controller/ci/tvm/TvmOrderController.java:130`、`:134`
- **`/ci/tvm/requestRefund` 是「自己用」的入口**：按订单号发起退款、退款金额取订单总金额。`collect-pay-server` `TvmOrderController` — `controller/ci/tvm/TvmOrderController.java:198`~`:199`
- **TVM 前置单的 `TRANS_TYPE` 决定支付中心回调的分派**（`BusinessTypeEnum`：`01` 扫码购票 / `02` 扫码充值 / `03` 扫码取票 / `04` bom 支付 / `05` app 退款）：`payNotice` 先用支付中心送来的 `merchantOrderNo`（即 ITP 订单号）查前置单，前置单不存在或 `TRANS_TYPE` 为空即回 `-1「没有找到匹配的订单，请确认订单号是否正确」`。`collect-pay-server` `TvmOrderPreServiceImpl.payNotice` — `service/impl/TvmOrderPreServiceImpl.java:115`~`:135`；`constant/BusinessTypeEnum.java:8`~`:12`

#### 附.1.2 BOM 设备域

- **BOM 族错误码是 `8xxx`（`BomPayCodeEnum`）**：`0000` 成功、`0001` 乘客取消订单、`8999` 失败、`8001` 非法设备、`8002` 订单未支付、`8003` 非法参数、`8004` 无激活的订单、`8005` 充值金额超限、`8006` 订单号错误、`8007` 订单已退款。与 TVM 族**同义不同码**（例：非法参数 TVM 是 `2002`、BOM 是 `8003`），改任一族 MUST 确认目标链路属哪族。`collect-pay-server` `BomPayCodeEnum` — `constant/BomPayCodeEnum.java:4`~`:13`
- **`TBL_BOM_NOCASH_ORDER.TRANS_TYPE` 的注释清单只有 9 项，且清单里没有「购票 / 发售」**：`02` 超时更新、`03` 超程更新 / 一卡通余额不足更新、`04` 未出站更新处理、`05` 无入站更新处理、`06` 储值票即时退卡 / 单程票退票、`22` 充值、`2A` 黑名单卡锁定、`2B` 卡锁定解除、`42` 行政处理；`ADMIN_TRANS_TYPE` 只在 `transType=42` 时使用。`collect-pay-server` `BomNoCashOrder.transType` — `entity/BomNoCashOrder.java:19`~`:31`（`adminTransType` — `:34`~`:36`）
- **`BomBusinessCodeEnum` 只有两项、且两项中文描述都写「充值」**：`SALE("01","充值")` 与 `TOPUP("22","充值")`。`collect-pay-server` — `constant/BomBusinessCodeEnum.java:4`~`:5`
- **BOM 建单落库的 `TRANS_TYPE` 有两个来源**：非现金收款建单取自请求体 `request.getTransType()`；发售建单**硬编码 `"01"`**。这与上面那份不含「发售」的注释清单直接冲突，是对账两组恒 0 的根因（见 附.1.6）。`collect-pay-server` `BomOrderServiceImpl.buildBomNoCashOrder` / `buildBomSaleOrder` — `service/impl/BomOrderServiceImpl.java:943`、`:962`
- **BOM 建单的初始状态是 `ItpStatusEnum.PAYING`（`'0'` 支付中），`MSG` 同步写该枚举的中文描述。** `collect-pay-server` `BomOrderServiceImpl.buildBomNoCashOrder` — `service/impl/BomOrderServiceImpl.java:950`~`:951`
- **`IF8A-04` 请求非现金收款下单的入参校验里有一条业务规则**：行政处理类型需要填写行政交易类型代码。`collect-pay-server` `BomOrderController.validateGenNoCashOrder` — `controller/ci/bom/BomOrderController.java:99`
- **BOM 侧的接口编号在本模块的真实含义**：`IF8A-04` 请求非现金收款下单、`IF8A-05` 扫码支付、`IF8A-06` 查询支付结果、`IF2A-08` 业务操作结果通知、`IF2A-09` 充值结果通知（注释原文带甲方章节号「7.3.3.5」）、`IF5A-01` 请求票卡分析（后付费二维码票分析）、`IF5A-03` 请求票卡更新、`IF5A-09` HCE 票卡更新结果通知。`collect-pay-server` `BomOrderController` — `controller/ci/bom/BomOrderController.java:50`、`:107`、`:134`、`:158`、`:185`、`:212`、`:239`、`:322`
- **BOM 充值结果通知的三态口径**：`topupStatus=01` 更新通知状态为处理成功 + 原订单置成功；`topupStatus=02` 更新通知状态为处理失败 + 原订单置失败。`collect-pay-server` `BomOrderServiceImpl.notiTopupResult` — `service/impl/BomOrderServiceImpl.java:680`~`:682`

#### 附.1.3 APP 域

- **APP 域接口编号**：`IF8A-20` 请求下单、`IF8A-11` 请求支付信息（ITP 按支付通道编码建支付订单 → 请求该通道预下单 → 把预下单返回的支付信息**签名后**返回）、`IF8A-18` 支付结果查询（**先查库，库里已有成功 / 失败即直接返回**，否则再走 `tvmOrderPreService.requestPayResult()`）。`collect-pay-server` `AppOrderService` — `service/AppOrderService.java:18`、`:27`~`:29`、`:37`~`:40`；`controller/ci/app/TvmAppOrderController.java:34`、`:90`~`:93`、`:118`~`:122`
- **`IF8B-05` 支付结果通知在本模块是注释掉的**：`AppOrderService` 里 `receivePaymentResult(JSONObject)` 连声明带 javadoc 整段被注释，现存的是下面那个「支付结果通知」方法。`collect-pay-server` `AppOrderService` — `service/AppOrderService.java:56`~`:65`
- **APP 单程票订单号是 20 位定长**：`ProductType(2) + yyyyMMddHHmmss(14) + 序列(4)`，前缀 `ProductType.ordinaryTicket = "00"`。2026-09-11 由「`00` + 时间 + UUID 前 8 位」的 24 位改成 20 位，与 BOM（`BomOrderServiceImpl.generateOrderNo`）和 face-pay 同口径，历史 24 位订单不受影响。长度由 `ORDER_NO_SEQ` 保证（实测 `MAX_VALUE=9999` + `CYCLE=Y`），**NEVER 把该序列改成不循环或放大上限**，否则订单号会超过 20 位。`collect-pay-server` `AppOrderServiceImpl.generateOrderNo` — `service/impl/AppOrderServiceImpl.java:388`~`:396`；序列与 BOM / TVM 共用、保证跨渠道不重号 — `:59`
- **`ProductType` 是订单号前缀的取值域**：`00` 一票通_单程票、`01` 计程票、`02` 计次票、`03` 计期票、`07` 员工票、`08` 琴岛通卡、`09` 青岛银行金融 IC 卡、`0A` 交通部互联互通卡、`0B` 补充票款、`0C` 招行一网通_垫资补缴、`0D`（描述与 `0C` 重复，原文即如此）、`0E` 虚拟电子票。`collect-pay-server` — `constant/ProductType.java:10`~`:21`
- **`/internal/app-order/**` 的三条资损口径（从 gate-txn-pay 侧 mapper 注释原样搬来，改任一条前 MUST 逐条复核）**：① `requestPayInfo` 按 `TICKET_PRICE × TICKET_NUM` 算送去支付中心的金额，因此登记时 `TICKET_NUM` 固定 `1`、`TICKET_PRICE` 写全额，**NEVER 只写 `TOTALPRICE`**；② `RSV2` **MUST 非空**，否则次日被 `refundAppNotTakeTickets` 当成「购票未取票」**全额退款**（补款单永远不会有票）；③ 前置单 `TRANS_TYPE` 只能是 `03`——只有它进 `appOrderService.payNotice`（只改订单行、不出票），缺这行前置单时支付中心异步回调对该单**完全失效**、状态永远停在 `'0'`。`collect-pay-server` `AppPayOrderInternalService`（类注释）— `service/AppPayOrderInternalService.java:24`~`:36`；入口处的两条硬校验 — `service/impl/AppPayOrderInternalServiceImpl.java:147`~`:153`
- **登记接口按 `orderNo` 幂等，已登记过直接返成功**：调用方是「本地先落 PENDING、提交后再同步、失败留给扫表补偿」的 outbox 模式，同一笔会被重放，**NEVER 改成「重复即报错」**（对方的补偿永远收不了口）。两张表（`TBL_TVM_APP_ORDER` / `TBL_TVM_ORDER_PAY_PRE`）在**同一个本地事务**里写、MUST 同生同死——只落其中一张的后果分别是「乘客看不到单」与「支付中心回调无处分派」。`collect-pay-server` `AppPayOrderInternalService.register` — `service/AppPayOrderInternalService.java:48`~`:53`
- **三列都写全额是刻意的**：`requestPayInfo` 只读 `TICKET_PRICE` 与 `TICKET_NUM`，`payNotice` 与对账读 `PAY_AMOUNT`，页面展示读 `totalPrice`。`collect-pay-server` `AppPayOrderInternalServiceImpl` — `service/impl/AppPayOrderInternalServiceImpl.java:163`~`:164`
- **关单接口影响 0 行也算成功**：白名单是 `PAY_STATUS='0'`，0 行的成因是「行不存在」或「已支付 / 已失败」；关单的语义是「保证乘客付不了」，目标已达成即返成功，**NEVER 把 0 行当失败返回**（调用方会当成可重试、白重推）。`collect-pay-server` `AppPayOrderInternalServiceImpl.closePending` — `service/impl/AppPayOrderInternalServiceImpl.java:98`~`:99`；`service/AppPayOrderInternalService.java:58`
- **回查接口查不到时返 `found=false` 而不是抛异常**：那是真实的业务事实，调用方据此落 ERROR 或补登记；抛异常只留给「本模块自己出错」。`collect-pay-server` `AppPayOrderInternalService.queryPayResult` — `service/AppPayOrderInternalService.java:65`~`:66`
- **`/ci/app/**` 的 `CollectPayController` 整个文件是注释掉的**（`IF8A-09` requestPay / `IF8A-10` payQuery / `IF8A-12` requestRefund / `IF8A-13` refundQuery 四条 URL 连类声明一起被注释）。判断这四条端点是否存在 MUST 以此为准。`collect-pay-server` — `controller/ci/app/CollectPayController.java:1`~`:67`

#### 附.1.4 退款

- **设备侧退款响应 `RequestRefundRespDTO` 恒定 5 个字段**：`retCode` 返回码、`retMsg` 返回消息、`refundResult` 退款结果（取值 `SUCCESS` 退款成功 / `FAILED` 退款失败 / `PROCESSING` 处理中）、`refundResultDesc` 退款结果描述、`refundNo` 退款单号。`collect-pay-server` `RequestRefundRespDTO` — `model/response/tvm/RequestRefundRespDTO.java:10`~`:35`
- **支付中心退款状态与本地退款状态是两个取值域，NEVER 混用**：支付中心侧 `PayCenterRefundStatusEnum` 是 `PROCESSING` 退款中 / `SUCCESS` 退款成功 / `FAIL` 退款失败；本地 `ItpStatusEnum` 的退款三态复用支付三态的字符值——`REFUND_ING("0")` / `REFUND_SUCCESS("1")` / `REFUNDING_FAIL("2")`。`collect-pay-server` — `constant/PayCenterRefundStatusEnum.java:9`~`:11`；`constant/ItpStatusEnum.java:15`~`:17`
- **`refundByAmount`（指定金额退款）自带超退闸门**：可退余额 = `TBL_TVM_APP_ORDER.PAY_AMOUNT` − `AppRefundOrderMapper.sumSuccessRefundAmount`，入参超过余额**直接拒、不发起支付中心调用、不落退款单**。这一层是该方法存在的主要价值，**NEVER 为了「让运营能强退」把它去掉**。`collect-pay-server` `AppOrderService.refundByAmount` — `service/AppOrderService.java:79`~`:82`
- **三道校验全部在调支付中心之前完成**：订单存在且 `PAY_STATUS='1'`（**白名单，NEVER 写成「非失败即可退」**）、金额为正整数、金额不超过可退余额；被拒时不落退款单、不发网络请求。金额单位是**分**。`collect-pay-server` `AppOrderServiceImpl.refundByAmount` — `service/impl/AppOrderServiceImpl.java:534`~`:541`；`controller/page/AppPartialRefundRequest.java:12`
- **`sumSuccessRefundAmount` 只统计 `REFUND_STATUS='1'`**：失败（`2`）与退款中（`0`）都不占额度；唯一用途就是算可退余额。`collect-pay-server` `AppRefundOrderMapper.sumSuccessRefundAmount` — `mapper/AppRefundOrderMapper.java:49`~`:52`；`AppRefundOrderMapper.xml:56`~`:57`
- **两个退款入口的金额来源不同，且旧入口 NEVER 改**：`/ci/app/requestRefundTicket` 取 `TBL_TVM_APP_ORDER.PAY_AMOUNT` **全额**，对已部分退款的订单必然超额被支付中心拒（2026-09-14 实测订单 `00202609111609085124` 付 600 已退 200，走旧接口会按 600 提交）；**旧接口按原样保留、NEVER 改它的金额来源**——它是 APP 对外契约的一部分，APP 端只传 `orderNo`，加字段等于改契约。`collect-pay-server` `AppOrderService.refundByAmount`（对比说明）— `service/AppOrderService.java:71`~`:77`；`controller/page/AppOrderPageController.java:18`~`:20`
- **两个退款入口都不做幂等**：与本模块其它退款入口一致，同一订单连调两次会退两次（退款操作手册铁律 2）。调用方 MUST 自己控制只调一次，中断后 MUST 先查 `TBL_APP_ORDER_REFUND` 再续跑、**NEVER 重放原列表**。`collect-pay-server` `AppOrderService.refundByAmount` — `service/AppOrderService.java:84`~`:85`；`controller/page/AppOrderPageController.java:50`~`:51`
- **运营端当面付退款的金额只从订单总额计算、不信任页面输入**；请求体只允许填退款原因，未填时用默认原因。`collect-pay-server` `FacePayOrderPageController.refund` — `controller/page/FacePayOrderPageController.java:85`~`:87`；`controller/page/FacePayRefundRequest.java:3`~`:5`
- **APP 侧「指定金额退款」的请求体只有金额一个字段**：退款原因不开放填写，因为 `AppOrderServiceImpl.doRefund` 把 `REFUND_REASON` 硬编码为「业务操作失败」，接收一个会被丢掉的原因字段属于骗调用方；要支持自定义原因得改 `doRefund` 签名（旧链路、本次不动）。`collect-pay-server` `AppPartialRefundRequest` — `controller/page/AppPartialRefundRequest.java:4`~`:8`
- **退款发起成功不修改退款状态**：状态在支付中心发起退款通知且退款成功时由 `receiveRefundResult` 接口修改。`collect-pay-server` `AppOrderServiceImpl` — `service/impl/AppOrderServiceImpl.java:663`
- **`app.center.refundReason` 的默认值是「出票数量不足」**（yml 里的 `pay.center.refundReason`）。`collect-pay-server` — `application.yml:26`

#### 附.1.5 通知与定时任务

- **`notice-app-*-url` 三条通知契约**：出票结果 `receiveTakeTicketResult`、出票故障结果 `receiveTakeTicketFaultResult`、退款结果 `receiveRefundResult`，三条都在 `pay.center.notice-app-*-url` 下、注释原文分别是「itp通知app出票结果」与「itp通知app退款结果」。`collect-pay-server` — `application.yml:35`~`:39`
- **通知重试上限由 `app.retryTimes` 控制，默认 5**（注释原文「通知app取票结果和退款的上限次数」），`NoticeAppTask` 扫表时把它作为条件之一传进 `selectSendFailRefundLs`。`collect-pay-server` — `application.yml:45`~`:46`；`task/NoticeAppTask.java:107`
- **`doTime` 两条 cron 是给通知任务预留的配置键**：`noticeTakeTicketTask: "*/5 * * * * ?"`、`noticeRefundTask: "*/2 * * * * ?"`；而 `NoticeAppTask` 的四个方法是 `@PostMapping` 端点（`/pay/noticeAppTask/**`），**由 web-server Quartz 定时任务发起调用**（`testtbNoticeAppTask` 的日志原文即「收到由 web-server Quartz 定时任务发起的调用」）。`collect-pay-server` — `application.yml:48`~`:50`；`task/NoticeAppTask.java:29`、`:43`~`:45`
- **重试报文 MUST 与首次报文逐字段一致，这有两条落库口径**：① 退款结果列 MUST 存**给 app 的取值域**（`SUCCESS` / `FAIL`），存 `ItpStatusEnum` 的 `"1"` / `"2"` 会让重试报文与首次报文取值不一致；同时 **NEVER 写死成功**——`dealAppRefundResult` 在退款失败分支同样返回 `true`。② 退款时间列 MUST 存与首次通知报文完全一致的 `yyyyMMddHHmmss`，存 `yyyyMMdd` 会让重试报文的 `refundDate` 变成 8 位当天日期、APP 侧解析失败。`collect-pay-server` `AppOrderServiceImpl` — `service/impl/AppOrderServiceImpl.java:694`~`:701`
- **首次推送的 `retryTimes` 写死 `1`**（两处注释原文：「这里retryTimes写死为1，因为明确这里是第一次发送」「此处是第一次推送，所以写死为1」）。`collect-pay-server` `AppOrderServiceImpl` — `service/impl/AppOrderServiceImpl.java:655`、`:758`
- **APP 主动退款的 `refundType` 与 `doRefund` 给 app 的应答保持一致（`00`），TVM 故障退款仍为 `01`。** `collect-pay-server` `AppOrderServiceImpl` — `service/impl/AppOrderServiceImpl.java:651`
- **通知报文与落库都用给 app 的取值域（`SUCCESS` / `FAIL`），不要用 `ItpStatusEnum` 的 `1` / `2`。** `collect-pay-server` `AppOrderServiceImpl` — `service/impl/AppOrderServiceImpl.java:647`
- **退款终态查到后 MUST 通知一次 app，顺序是「先落通知记录（`status=0`）→ 同步 push → 成功置 `1` / 失败置 `2` 交给 `NoticeAppTask` 重试」。** `collect-pay-server` `AppOrderServiceImpl` — `service/impl/AppOrderServiceImpl.java:639`~`:640`
- **`SingleTicketRefundTask` 是本模块唯一在跑的 `@Scheduled`，共 4 处**：APP 在线购票未取票退款（cron `0 0 20 * * ?`）、TVM 充值退款（cron `0 0 20 * * ?`）、BOM 售票未取票退款（cron `0 1 9,15,21 * * ?`）、BOM 充值退款（cron `0 0 9,15,21 * * ?`）。三个方法上方各留有一行被注释掉的 `@PostMapping`（`/refundAppNotTakeTickets`、`/refundBomSaleNotTakeTickets`、`/refundBomTopupNotTakeTickets`），即这三条 URL **当前不存在**。`collect-pay-server` `SingleTicketRefundTask` — `task/SingleTicketRefundTask.java:33`~`:34`、`:50`~`:51`、`:68`~`:69`、`:86`~`:87`
- **`refundAppNotTakeTickets` 的扫描口径**（决定了 `RSV2` 必须非空那条资损防线）：`PAY_STATUS='1'` + `RSV2 IS NULL` + `CREATE_TIME` 在给定窗口内 + 左连 `TBL_TVM_MAIN_TICKET` 后主票表无行。`collect-pay-server` `TvmAppOrderMapper.selectByCondition` — `TvmAppOrderMapper.xml:144`~`:154`

#### 附.1.6 对账导出

- **本模块只产出 `ITP.PAY` 与 `ITP.BUS` 两类，全部是库内 `GROUP BY`、没有明细分页**；`ITP.DETAIL` 与 `ITP.EXP` 收到指令时只记 warn 并跳过。DETAIL 按甲方规格（《ACC与ITP之间的文件》第一章第 4 节）是「虚拟电子多日计次票的明细和总金额，多日计次票正常交易不对账、只对车票购买与超时费」，与 TVM / BOM 非现金收款毫无关系，已改由 gate-txn-pay 与 daily-ticket 负责。**NEVER 拿本模块的四张收款表去凑 DETAIL 行**——凑出来的是错账文件，而纯文本分片没有 schema、下游读不出异常。`collect-pay-server` `ReconExportService`（类注释）— `service/ReconExportService.java:25`~`:32`；`mapper/ReconExportMapper.java:16`~`:18`；`ReconExportMapper.xml:10`~`:13`
- **本源标识固定 `collect-pay`，是跨服务契约的第一段字段值，NEVER 改。** `collect-pay-server` `ReconExportService.SOURCE` — `service/ReconExportService.java:58`~`:59`
- **`ITP.PAY` 每行 21 段（前 5 段键 + 后 16 段度量），字段顺序来自甲方规格、NEVER 调整**：键 `0` 日期 / `1` 线路 / `2` 车站 / `3` 设备编号 / `4` 支付方式；度量 `5-6` BOM/TVM 发售、`7-8` BOM/TVM 充值、`9-10` 旅游票、`11-12` 过闸、`13-14` APP 购票、`15-16` BOM 行政处理、`17-18` 单边交易、`19-20` BOM 处理。本模块只填三组，其余 13 段写字面 `0`。`collect-pay-server` `ReconExportService.exportPay` — `service/ReconExportService.java:170`~`:188`；常量 — `:61`~`:74`；XML 侧同表 — `ReconExportMapper.xml:76`~`:84`
- **四张源表与各自的成功状态列 / 金额列**：`TBL_TVM_ORDER_PAY`（TVM 扫码购票，`STATUS`，金额 `TOTAL_PRICE`）、`TBL_TVM_ORDER_TOPUP`（TVM 扫码充值，`STATUS`，金额 `TRANS_AMOUNT`）、`TBL_TVM_APP_ORDER`（APP 在线购票，**状态列是 `PAY_STATUS` 不是 `STATUS`**，金额 `PAY_AMOUNT`）、`TBL_BOM_ORDER_PAY`（BOM 非现金收款，`STATUS`，金额 **`TRANS_AOUNT`**——DDL 原文就少一个 M，**NEVER 顺手改成 `TRANS_AMOUNT`**，改了就是 `ORA-00904`）。`collect-pay-server` `ReconExportService`（类注释）— `service/ReconExportService.java:34`~`:37`；`ReconExportMapper.xml:34`~`:36`、`:130`~`:131`
- **成功状态白名单只导 `'1'`**：`ItpStatusEnum` 取值 `0` 支付中 / 退款中、`1` 支付成功 / 退款成功、`2` 失败、`3` 未支付；**NEVER 写成「非 2」之类的黑名单**——中间态 `0` 会被误当成已收款，导致本期虚增收入且下期重复入账。`collect-pay-server` — `ReconExportMapper.xml:20`~`:24`；`constant/ItpStatusEnum.java:10`~`:13`
- **时间窗口 `CREATE_TIME >= windowStart AND CREATE_TIME < windowEnd` 左闭右开**，保证相邻两天既不漏也不重；四张表的时间列都是 `VARCHAR2(100)` 存 `yyyy-MM-dd HH:mm:ss`（写入值来自 `utils/DateUtils.getNowTime()`），因此直接做字符串比较（该格式下字典序等价于时间序）、绑定 `jdbcType=VARCHAR`，**NEVER 对时间列套 `TO_DATE` 之类的函数**（索引失效）。`collect-pay-server` — `ReconExportMapper.xml:15`~`:18`；`mapper/ReconExportMapper.java:20`~`:23`
- **`TXN_DATE` 段用 `REPLACE(SUBSTR(CREATE_TIME,1,10),'-','')`，只出现在 SELECT / GROUP BY / ORDER BY、不进 WHERE**，因此不影响索引。`collect-pay-server` — `ReconExportMapper.xml:26`~`:28`
- **金额列全是 `VARCHAR2(100)`，所以 `SUM` 一律套 `TO_NUMBER`、外面再包 `NVL`**（`SUM` 跳过 NULL，全组皆 NULL 时返回 NULL）；**NEVER 省掉 `TO_NUMBER` 靠隐式转换**——Oracle 逐行隐式转换口径不可控，遇脏数据直接 `ORA-01722`。`collect-pay-server` — `ReconExportMapper.xml:30`~`:33`；`mapper/ReconExportMapper.java:25`~`:26`
- **APP 购票组窗口过滤仍用 `CREATE_TIME` 而不是 `PAY_TIME`**：窗口列与分组列 MUST 同源，混用会让同一笔在相邻两天的窗口里重复或漏掉。`collect-pay-server` — `ReconExportMapper.xml:132`~`:133`
- **车站段取 `IN_STATION_CODE` 而不是 `OUT_STATION_CODE`**，两条理由都是实测：① 同一行不能自相矛盾——2026-09-11 测试库确有 4 笔 `IN=0622 / OUT=0122`，车站段取 OUT 会让 recon-server 补出线路 `01` 而不是 `06`；② `OUT_STATION_CODE` 大面积为 NULL——`TBL_TVM_ORDER_PAY` 共 111 行、其中 55 行 OUT 为 NULL（IN 一行不缺）。**NEVER 用车站码前 2 位推线路**：实测前 2 位恰好等于 `LINE_CODE` 是编码巧合、不是契约。`collect-pay-server` — `ReconExportMapper.xml:60`~`:68`；`mapper/ReconExportMapper.java:48`~`:53`
- **`TBL_TVM_ORDER_TOPUP` 与 `TBL_BOM_ORDER_PAY` 都没有车站号列**，车站段由 Java 补空串、线路段也无从关联，**NEVER 拿 `DEVICE_ID` 去猜车站或线路**（设备编号与车站码在本项目里没有可验证的换算关系）；待甲方补数据源（例如要求报文上送车站码）或明确接受空值，届时 MUST 同步改对应 select 与 `ReconExportService`。`collect-pay-server` — `ReconExportMapper.xml:105`~`:110`、`:167`~`:169`；`mapper/ReconExportMapper.java:65`~`:67`、`:115`~`:117`
- **`TBL_TVM_APP_ORDER` 的 DDL 里没有 `DEVICE_ID`**（resultMap 有、DDL 无，属仓库已知不一致），因此不引用该列、设备编号段由 Java 补空串；同表 DDL 也没有 `PAYCENTER_ORDERNO` / `PAYCENTER_CHANNELORDERNO`（`TBL_TVM_ORDER_TOPUP` 的 mapper XML 在读写它们，同属已知不一致），汇总用不到、不引用。`collect-pay-server` — `ReconExportMapper.xml:130`~`:131`、`:111`~`:112`；`mapper/ReconExportMapper.java:77`~`:80`
- **混合大小写列名（`totalPrice` / `merchantOrderNo` / `PAYCENTER_channelOrderNo`）一律回避不引用**：Oracle 里这类列必须带双引号才能命中，不带引号会 `ORA-00904`。`collect-pay-server` — `ReconExportMapper.xml:38`~`:39`；`mapper/ReconExportMapper.java:32`~`:34`
- **「BOM 行政处理」（idx 15/16）与「BOM 处理」（idx 19/20）两组恒 0 的成因**：`TBL_BOM_ORDER_PAY.TRANS_TYPE` 取值在仓库内**三处证据互相矛盾**——① `BomNoCashOrder` 的字段注释清单里**根本没有「购票 / 发售」**；② `BomBusinessCodeEnum` 的 `SALE("01","充值")` 与 `TOPUP("22","充值")` 两个描述都写「充值」，`01` 到底是发售还是充值无法判定；③ `BomOrderServiceImpl` 的 BOM 发售建单**硬编码 `setTransType("01")`**，与①直接冲突。加上 `TRANS_TYPE` 另有一条来源是**设备上送**（取自请求体），库里必然还有 `02/03/04/05/06/2A/2B/42` 这些既不是购票也不是充值的行。**凭猜写取值会把账挂到错误分组，宁可整表归「发售」一组并标注**——发售组金额偏大是可解释的口径问题，错拆是错账。甲方给出明确判据后再拆，届时 MUST 同步改本 select 与 `ReconExportService`。`collect-pay-server` `ReconExportMapper.selectBomPayPaySummary` — `mapper/ReconExportMapper.java:95`~`:112`；`ReconExportMapper.xml:152`~`:164`、`:82`~`:84`；`service/ReconExportService.java:190`~`:197`
- **旅游票（9/10）与过闸（11/12）本模块没有对应业务，单边交易（17/18）属 `ITP.EXP` 口径、也不在本模块。** `collect-pay-server` `ReconExportService.exportPay` — `service/ReconExportService.java:190`~`:192`
- **`ITP.BUS` 只有 4 段（`日期|对账金额|付款金额|优惠金额`），键只有日期一段，NEVER 再带车站 / 设备 / 票种**——多带一段会让 recon-server 按 1 段键聚合时把维度信息当成度量列错位读取。对账金额与付款金额都取该表金额列之和；**优惠金额恒 0**：四张表的 DDL（`collect-pay-server/sql.txt`）里**都没有任何优惠 / 折扣金额列**（已逐表核对），**NEVER 拿票价与实付之差去推算**——`TICKET_PRICE × TICKET_NUM` 与实付的差额可能来自分单、退款或脏数据，算出来的不是优惠。`collect-pay-server` — `ReconExportMapper.xml:186`~`:195`；`mapper/ReconExportMapper.java:127`~`:128`；`service/ReconExportService.java:276`~`:289`
- **四张表的同键分组可能重复出现（同一天同一设备同一通道，购票与充值各一行），这是允许的**：recon-server 按前 5 段键做二次聚合、对 16 个度量列逐列累加；BUS 侧同理按 1 段键二次聚合三个度量。`collect-pay-server` `ReconExportService` — `service/ReconExportService.java:216`~`:217`、`:288`~`:289`；`ReconExportMapper.xml:194`~`:195`
- **写行 MUST 是完整 21 段短行不行**：先把 16 段度量全填 `0L`，再按组下标覆盖两段；**NEVER 只拼「键 + 本组两段」**——段数不足时 recon-server 按下标取度量列会整体错位，而纯文本没有 schema、不会报错，只会静默出错账。`collect-pay-server` `ReconExportService.writePayRow` — `service/ReconExportService.java:245`~`:249`
- **抽取受理接口立即返回，不等抽取完成**：收齐判定由 recon-server 侧的批次状态负责，本响应只表示「已排入队列」；同批次上一轮仍在执行时返回 `rejected`，那是**限流、不是失败**。`collect-pay-server` `ReconExportController.export` — `controller/internal/ReconExportController.java:36`~`:40`；`service/ReconExportService.submit` — `service/ReconExportService.java:102`~`:107`
- **`recon.export.worker` 默认 1（串行），刻意不放大**：抽取是重 IO 的区间扫描，并发只会互相抢 Oracle 的 IO 与 Druid 连接，反而拖慢联机链路。`collect-pay-server` `ReconExportService`（构造器 javadoc）— `service/ReconExportService.java:86`~`:88`；`application.yml:113`

#### 附.1.7 持久层与 mapper

- **`updateStatusToFailedIfPending` 与 `updateByOrderNo` 的区别是「有条件 / 无条件」**：后者 WHERE 只有 `ORDER_NO`，会把已支付成功的行也一起改掉；关单场景 **MUST 用前者**、**NEVER 图省事换成 `updateByOrderNo`**——乘客付款与上游关单之间有毫秒级竞态，无条件更新会把「已收到的钱」抹成支付失败，钱收了却没人销账。影响 0 行是正常结果（该行已是终态），调用方 MUST 当成成功返回、**NEVER 当失败重试**。`collect-pay-server` `TvmAppOrderMapper` — `mapper/TvmAppOrderMapper.java:51`~`:62`；`TvmAppOrderMapper.xml:118`
- **`sumSuccessRefundAmount` 的 `REGEXP_LIKE` 过滤 NEVER 删**：`REFUND_AMOUNT` 是 `VARCHAR2`，库里存在非数字与量级写错的历史脏值（2026-09-14 实测 BOM 侧有 `'0'`、也有付 1 分却记 300 的行），`TO_NUMBER` 一旦撞到脏值就抛 `ORA-01722`，整个退款请求会连「留证据」的落库一起失败。`collect-pay-server` `AppRefundOrderMapper.sumSuccessRefundAmount` — `mapper/AppRefundOrderMapper.java:54`~`:57`；`AppRefundOrderMapper.xml:58`~`:59`
- **`updateByOrderNo` 系列用 Map 传参、支持动态更新字段，但参数 MUST 含 `orderNo` / `refundNo`。** `collect-pay-server` — `mapper/TvmAppOrderMapper.java:42`~`:47`；`mapper/AppRefundOrderMapper.java:40`~`:45`
- **对账抽取刻意独立成一个 mapper、现有 mapper / XML 一行都没改**：对账抽取是批处理口径（扫时间窗口、只取成功单、库内 `GROUP BY`），与联机查询的口径和索引策略完全不同，混进现有 mapper 后任何一方调过滤条件都会误伤另一方。返回类型统一 `Map<String,Object>`：聚合只产出少数几列，映射成 20 余列的实体等于每行多背一堆 null 字段。`collect-pay-server` `ReconExportMapper`（类注释）— `mapper/ReconExportMapper.java:12`~`:14`、`:28`~`:30`
- **`COUNT` / `TO_NUMBER` 的结果在 `Map<String,Object>` 里是 `BigDecimal`**，取值 MUST 经调用方的转换方法，直接强转 `(Long)` 会 `ClassCastException`；该转换方法只服务本类、是私有实现细节，不是新建公共工具类。`collect-pay-server` `ReconExportMapper` / `ReconExportService.toLong` — `mapper/ReconExportMapper.java:29`~`:30`；`service/ReconExportService.java:322`~`:324`

#### 附.1.8 配置（yml）

- **该模块的 `spring.application.name` 是 `itpagm`（不是 `collect-pay`）**，`log4j2-collectpay.xml` 的 `appName` 属性同为 `itpagm`，因此日志文件名与检索键都是 `itpagm`。`collect-pay-server` — `application.yml:73`~`:74`；`log4j2-collectpay.xml:29`
- **tracing 三行成组已在本模块落地**：`management.tracing.enabled=true` + `management.tracing.sampling.probability=0` + `spring.autoconfigure.exclude=...OtlpAutoConfiguration`。注释逐条给了理由：`probability=0` 只让本服务发起的 trace 不采样（采样器是 `parentBased(traceIdRatioBased(0))`，上游带 `sampled=1` 的 `traceparent` / `b3` 进来时 span 仍会被采样并进导出队列）；`micro/web` 的 `web.properties` 已把 `management.otlp.tracing.endpoint` 整行注释掉，那条 `exclude` 是**第二道保险**——K8s Deployment 只要注入 `MANAGEMENT_OTLP_TRACING_ENDPOINT` env 就会重新激活 exporter；Boot 3.2.6 **没有** `management.tracing.export.enabled` 这个开关，把 endpoint 置空也不行（`OtlpAutoConfiguration` 只判断键是否存在），只能排掉整个自动配置。**NEVER 删除那行 exclude。** `collect-pay-server` — `application.yml:75`~`:84`、`:91`~`:95`
- **打开 tracing 后能对上的两条链路**：① recon-server 下发 `/internal/recon/export` 带来的 W3C `traceparent`（源头是 `sys_job` 109 日终对账）；② APP / TVM / BOM 入向请求自身的 `traceparent`。只需要 MDC 里的 `traceId` / `spanId` 供 VictoriaLogs 检索，**span 上报一律不要**。`collect-pay-server` — `application.yml:85`~`:90`
- **`service.recon.url` 是 2026-09-11 按 `kubectl get svc -n itp` 实测回填的真实 Service 名**，注释同时点明「Service 端口等于 NodePort 号（30034），不等于容器 `server.port`（9112）」。`collect-pay-server` — `application.yml:103`~`:106`
- **`recon.internal-token` 默认必须为空、由 K8s Secret 注入 `RECON_INTERNAL_TOKEN`**（该键当前已无读取方，见 附.2 的鉴权降级条）。`collect-pay-server` — `application.yml:108`~`:109`
- **TVM 取票授权挂起等待的三个参数（2026-09-11 新增）**：`waitMillis: 10000` / `pollIntervalMillis: 500` / `maxWaiting: 500`。注释原文：TVM 每轮只发一次 `requestTakeTicketAuth`、且比 APP 扫码激活早 4~8 秒，厂商不改轮询逻辑，因此查不到订单时把这次请求挂住等激活事件；**`waitMillis` MUST < 15000**——istio 的 `fep-app-vr` `/itptvm/` 路由没配 timeout，走 Envoy 默认 15 秒，超过就变成网关 504 而不是我方响应。`collect-pay-server` — `application.yml:63`~`:71`；`service/impl/TvmTakeTicketServiceImpl.java:54`~`:57`
- **兜底回查间隔 NEVER 小于 500ms**（JDBC 阻塞会 pin 载体线程）。`collect-pay-server` `TvmTakeTicketServiceImpl` — `service/impl/TvmTakeTicketServiceImpl.java:62`
- **BOM 支付轮询窗口**：`bom.payTimeOut: 180`、`bom.payTimeInterval: 3`。`collect-pay-server` — `application.yml:60`~`:62`
- **APP 公共报文字段的固定值**：`app.providerId=06`、`charset=UTF-8`、`format=json`、`signType=00`（即免签）。`collect-pay-server` — `application.yml:41`~`:44`
- **日志走本模块自带的 `log4j2-collectpay.xml`，公共 `log4j2-linux.xml` 不再参与**（`CustomLoggingConfiguration` 见到 `logging.config` 非空即直接 return）。目的：用配置级 `RegexFilter` 丢掉设备心跳 `notiDeviceHeard` 的 INFO 日志——它占了日志量的 **79%**、把业务链路埋掉；`InternalMicroHttp` 的令牌明文压制也已搬进该文件的 Logger 段。`collect-pay-server` — `application.yml:114`~`:119`；`log4j2-collectpay.xml:2`~`:10`、`:129`~`:133`

### 附.2 决策理由

#### 附.2.1 为什么本模块不停、退款与通知留在这里

- **`/internal/app-order/**` 存在的原因是把跨域直写收回来**：`TBL_TVM_APP_ORDER` / `TBL_TVM_ORDER_PAY_PRE` 两张表的 owner 是本模块（`docs/business/tvm-bom-pay.md`），但 2026-09-11 起 gate-txn-pay-server 的 IF8A-26 补款单为了复用本模块的收银台链路，**直接在自己进程里 INSERT / UPDATE 这两张表**——那违反 `docs/domain/README.md` 的「热路径写入定 owner」判据：字段口径散落在两个模块，本模块改一列语义就可能静默打挂对方。2026-09-14 按用户要求收口成本接口。`collect-pay-server` `AppPayOrderInternalService`（类注释）— `service/AppPayOrderInternalService.java:11`~`:15`；`controller/internal/AppPayOrderInternalController.java:17`~`:18`
- **切换尚未发生、两条写入路径并存**：gate-txn-pay-server 的 `mapper/AppPayOrderMapper.java` 与 `resources/mapper/AppPayOrderMapper.xml` **仍在原地**，`SupplementOrderServiceImpl` 的私有 `registerAppPayOrder` 也仍在用它直写两张表，因此本接口这一条**暂时无人调用**。完成切换的那一笔 MUST 同时删掉对方那个 mapper 与 XML、并改掉它的三处调用点与单测桩，**NEVER 在两条路径并存的状态下上线**——那样同一笔单据可能被两种口径各写一次。`collect-pay-server` `AppPayOrderInternalService`（类注释）— `service/AppPayOrderInternalService.java:17`~`:22`
- **为什么另开 `/page/app/orders/{orderNo}/refund` 而不是改旧入口**：`/ci/app/requestRefundTicket` 的退款金额取 `PAY_AMOUNT` 全额，对已部分退款的订单必然超额被支付中心拒；那条是 APP 对外契约（APP 只传 `orderNo`），**NEVER 给它加金额字段**，因此另开本入口。放在 `/page/**` 而不是 `/ci/**`：这是运营 / 清退用的内部操作，不属于 APP 契约，与同目录的 `FacePayOrderPageController` 同类。`collect-pay-server` `AppOrderPageController`（类注释）— `controller/page/AppOrderPageController.java:16`~`:23`
- **运营端退款复用 `TvmOrderPreService`** 是为了沿用既有的支付中心退款与退款单号落库流程。`collect-pay-server` `FacePayOrderPageController.refund` — `controller/page/FacePayOrderPageController.java:86`~`:87`
- **`/internal/recon/export` 与 `/internal/app-order/**` 当前无鉴权，是有意为之的临时降级、上线前 MUST 恢复**。对账那条：用户 2026-09-11 明确要求「删除令牌要求，不用令牌了，当前处于开发测试阶段」，原 `X-Recon-Token` 共享令牌校验整段删除；本项目多数业务模块没有 spring-security、没有全局拦截器兜底，现状等于允许任何网络可达方触发区间全扫级别的批处理，与 AGENTS.md §5.2 冲突。补款那条：用户 2026-09-14 裁决「不加鉴权，照本模块现有 `/internal/recon` 的做法」，但它**比 `/internal/recon` 更敏感**——拿到任意订单号即可给他人建单、或关掉他人的待支付单，**NEVER 拿「`/internal/recon` 也没加」当长期理由**（那批是只读导出与内部编排，本批是按订单号改他人的支付状态，敏感度不同级）。`collect-pay-server` — `controller/internal/ReconExportController.java:14`~`:18`；`controller/internal/AppPayOrderInternalController.java:20`~`:25`；`service/AppPayOrderInternalService.java:38`~`:41`
- **恢复鉴权的方式已写死**：MUST 对齐现有验签实现（`AccountRequestVerifier` / `ItpRequestSignVerifier`），**NEVER 自造签名逻辑**；若走共享令牌，比较 MUST 用 `MessageDigest.isEqual` 做**定长时间比较**、**NEVER 用 `String.equals`**（后者短路返回，可被逐字节计时探测出令牌内容），令牌值由 K8s Secret 注入、**NEVER 在仓库里写默认真值**。`collect-pay-server` — `controller/internal/ReconExportController.java:20`~`:23`；`controller/internal/AppPayOrderInternalController.java:27`~`:29`
- **运营端 APP 退款端点没有鉴权这一点被单独标注过风险边界**：已知的唯一防线是服务层的可退余额闸门，**它挡的是「退多了」，挡不住「不该退的人来退」**；上线前 MUST 补鉴权，或改由 web-admin 经 rpc 调用并只在内网暴露。`collect-pay-server` `AppOrderPageController`（类注释）— `controller/page/AppOrderPageController.java:25`~`:31`

#### 附.2.2 旧表与 `F2F_*` 新表的分工

- **对账源只读四张旧收款主表**：`TBL_TVM_ORDER_PAY` / `TBL_TVM_ORDER_TOPUP` / `TBL_TVM_APP_ORDER` / `TBL_BOM_ORDER_PAY`，**不复用任何联机链路的 mapper**。这批表的两个形态特征与 face-pay 的 `F2F_*` 正好相反、也是本模块所有 SQL 写法的由来：时间列是 `VARCHAR2(100)` 的 `yyyy-MM-dd HH:mm:ss`（因此做字符串比较）、金额列是 `VARCHAR2(100)`（因此 `SUM` 套 `TO_NUMBER`）。`collect-pay-server` `ReconExportMapper`（类注释）— `mapper/ReconExportMapper.java:10`、`:20`~`:26`
- **APP 订单号 2026-09-11 改成 20 位就是为了与 BOM 和新服务 face-pay 完全同口径**，前缀仍是 `00`、历史 24 位订单不受影响。`collect-pay-server` `AppOrderServiceImpl.generateOrderNo` — `service/impl/AppOrderServiceImpl.java:390`~`:392`
- **`ORDER_NO_SEQ` 是本模块 APP / BOM / TVM 三个渠道共用的一条序列**，共用的目的是跨渠道不重号。（face-pay 侧另建 `F2F_ORDER_NO_SEQ` 与它不共用，见本文件 §7.3 那条。）`collect-pay-server` `AppOrderServiceImpl` — `service/impl/AppOrderServiceImpl.java:59`
- **线路段收口到 recon-server（2026-09-16）**：原先 TVM 购票与 APP 购票两条各写一遍 `LEFT JOIN STATION_INFO S ON S.STATION_CODE = T.IN_STATION_CODE` 并在 SELECT / GROUP BY / ORDER BY 带 `S.LINE_CODE`，现已全部删除。理由：线路是车站的函数（一个车站码只对应一条线路），四个源各写一遍等于「线路怎么取」有四份副本、四份各自的 join 列名、四次维表结构变更的暴露面；而 `STATION_INFO` 属车站 / 参数域、owner 不是本模块，少一处跨域直连就少一处破例。**NEVER 把 join 加回来**：recon-server 侧是无条件覆盖，加回来不改变产出、只让口径重新分叉。`collect-pay-server` — `ReconExportMapper.xml:41`~`:51`；`service/ReconExportService.java:199`~`:208`
- **收口顺带解决了本模块一个长期缺口，但结论不变**：TVM 充值与 BOM 两张表连车站码列都没有，本模块本来就无从关联；而 recon-server 是按「车站段」查维表补的，因此这两组的线路段**仍然是空**——空的成因从「本模块拿不到车站码」变成「这两组本身就没有车站段」。待甲方补数据源后，本模块只要把车站段填上，线路段就会由 recon-server 自动补齐，**届时不需要在本文件里加任何 join**。`collect-pay-server` — `ReconExportMapper.xml:53`~`:58`；`service/ReconExportService.java:210`~`:215`
- **四条 select 现在都是单表查询（没有任何 join），主表别名仍统一写 `T`、列名一律带前缀**：这不再是为了避免 `ORA-00918`，而是为了让下次任何一条 select 引入第二张表时不必回头改列名。`collect-pay-server` — `ReconExportMapper.xml:70`~`:72`
- **`writePayRow` 的 `lineCode` 形参刻意保留**：四组一律传 `null`，保留形参是为了让 21 段的段位在签名上仍然可见，**NEVER 在这里塞任何自己算出来的线路值**。`collect-pay-server` `ReconExportService.writePayRow` — `service/ReconExportService.java:252`~`:254`

#### 附.2.3 线程模型与事务边界

- **抽取绝不能跑在请求线程上**：全服务默认 `spring.threads.virtual.enabled=true`（`resource/micro/web/src/main/resources/web.properties:6`），Tomcat 处理线程是虚拟线程；JDK 21 未落地 JEP 491，虚拟线程在 `synchronized` 内阻塞会 **pin 住载体线程**，而 ojdbc8 的 `PhysicalConnection` / `OracleStatement` 大量方法是 `synchronized`——一条 60s 慢 SQL 就是 60s 的 pin。载体线程池 parallelism 默认等于容器可见 CPU 数，CPU limit 偏小时一两条慢 SQL 即可 pin 满，**全 JVM 虚拟线程停止调度**，连 WebClient 响应的续体都唤不醒。对账抽取是区间全扫级别的批处理，放在请求线程上等于必然触发该故障。因此本类把任务派给一个**固定大小的平台线程池**（`new Thread(...)` 造出的就是平台线程），控制器只负责受理与立即返回。`collect-pay-server` `ReconExportService`（类注释）— `service/ReconExportService.java:39`~`:47`；`controller/internal/ReconExportController.java:38`~`:40`
- **并发控制不依赖任何中间件**（本项目不用 Redis / MQ）：同一 `batchId` 的在途标记放在 `ConcurrentHashMap.newKeySet()` 里，`add` 成功才受理、`finally` 里移除；单副本内足够，多副本场景由 recon-server 侧按批次分发保证只下发一次。`collect-pay-server` `ReconExportService`（类注释）— `service/ReconExportService.java:49`~`:51`
- **`runExport` 及其调用链刻意不加 `@Transactional`**：每类一次查询、每条 SQL 自动提交。若把整轮抽取包进一个事务，事务时长等于抽取时长，期间还夹着 `sink.write` 触发的分片上送（HTTP 网络调用）——**事务内发起 RPC 是本项目明令禁止的**：连接被 Druid 的 `remove-abandoned-timeout` 判定为泄漏后强杀，`commit` 抛 `connection closed`，整轮白跑。抽取是纯只读，本来也不需要事务。`collect-pay-server` `ReconExportService.runExport` — `service/ReconExportService.java:139`~`:143`
- **每类文件独立 try-with-resources、一类失败不中断整轮**：某一类抛异常时 `ReconPartSink.close()` 会自动向 recon-server 声明该类失败，本方法 catch 住后继续跑下一类。**NEVER 让一类的失败中断整轮**——PAY 与 BUS 是两份独立的对账文件，一类挂掉不该拖累另一类，否则重跑成本翻倍。`collect-pay-server` `ReconExportService.runExport` — `service/ReconExportService.java:135`~`:137`
- **`notiTopupResult` 有意不带 `@Transactional`（2026-09-14 摘除，NEVER 加回）**：充值失败分支要调支付中心退款（`callPayCenter`），一旦被事务包住，`TBL_TVM_ORDER_TOPUP` 那一行的排他锁持有时长就等于支付中心的响应时长，BOM 对同一笔的重推会全部堆在同一行上串行等锁；等待超过 Druid `remove-abandoned-timeout` 后连接被强杀、`commit` 抛 `connection closed`，**连「充值结果通知已入库」那条 INSERT 一起回滚**——证据全丢、响应退化成全局异常处理器的 UUID `retCode`、BOM 继续重推，自我放大且没有出口。摘掉之后每条 SQL 自动提交：通知记录与「退款中」状态先落地，退款结果再各自回写，失败也留得下痕迹。`collect-pay-server` `BomOrderServiceImpl.notiTopupResult` — `service/impl/BomOrderServiceImpl.java:686`~`:698`
- **`register` 反过来可以安全地包事务**：它全程无 RPC，因此两张表能放在同一个本地事务里；**NEVER 在本方法内新增任何远端调用**。`collect-pay-server` `AppPayOrderInternalServiceImpl.register` — `service/impl/AppPayOrderInternalServiceImpl.java:50`~`:53`
- **取票挂起 NEVER 放进带 `@Transactional` 的方法**：行级锁会被持有整个等待时长，这正是 AGENTS.md §5.2 记录的 2026-08-26 生产事故形态。`collect-pay-server` `TakeTicketWaiter`（类注释）— `service/support/TakeTicketWaiter.java:24`~`:25`
- **取票挂起的唤醒是「内存事件 + 固定间隔回查数据库」双路**：唤醒走内存事件（本服务单副本），并由调用方按固定间隔回查数据库兜底，避免「激活落在别的副本」或「signal 与 await 之间存在窗口」导致漏唤醒。全局挂起上限（`maxWaiting`）超限时直接退回「立即返回」的原有行为，防止设备异常时把线程占满；等待键就是二维码三要素，与 `selectByDeviceAndQrcode` 的查询条件一一对应。`collect-pay-server` `TakeTicketWaiter`（类注释与字段）— `service/support/TakeTicketWaiter.java:21`~`:22`、`:35`、`:39`、`:44`、`:61`
- **推送与解析 MUST 兜住异常**：`noticeAppRefundResult` 在 `tvmexecutor` 线程里跑，抛出去没人接，通知记录会卡在 `status=0` 且 `retryTimes` 不递增，`NoticeAppTask` 会反复扫到同一条。`collect-pay-server` `AppOrderServiceImpl` — `service/impl/AppOrderServiceImpl.java:761`~`:762`
- **不先查后插也不重复报错，靠主键兜底**：`selectByOrderNo` + INSERT 是 check-then-act，并发重放会在主键上撞出来；撞了说明另一条并发请求已登记成功，按幂等返成功而不是让对方进补偿队列重推。判定 **MUST 沿 cause 链**——本模块开了 tracing，观测切面会把异常换类型。`collect-pay-server` `AppPayOrderInternalServiceImpl` — `service/impl/AppPayOrderInternalServiceImpl.java:78`~`:80`、`:190`~`:196`
- **入口就拒绝空 `RSV2`、NEVER 给个默认值放行**——默认值等于替调用方定资损口径。`collect-pay-server` `AppPayOrderInternalServiceImpl` — `service/impl/AppPayOrderInternalServiceImpl.java:147`~`:148`
- **日志改用本模块自带配置而不是改公共配置的三条理由**：一次心跳产生 4 行、其中 3 行来自公共构件（`FirstFilter` / `MoreInterceptor` / `BodyCacheFilter`），只删 Controller 里那句 `log.info` 只能去掉 1/4；`resource/micro/web` 的 `log4j2-linux.xml` 被 **21 个模块共用**，改一次全量生效；用 `logging.level` 压 `FirstFilter` / `BodyCacheFilter` 也不行——那三个 logger 是按类命名的（会连带压掉非心跳链路的同类日志）。`collect-pay-server` — `log4j2-collectpay.xml:3`~`:10`
- **配置级过滤器两级短路**：① WARN 及以上一律 ACCEPT——心跳链路真出错时（异常、超时）仍然看得到，不会被正则吞掉；② 其余事件命中 `notiDeviceHeard` 即 DENY，覆盖公共构件那 3 行与任何后续新增的心跳日志。`RegexFilter` 默认 `useRawMsg=false`，匹配的是**格式化后的整条消息**（URL 就在消息里）；`matches()` 是全串匹配，所以必须写 `.*`，`(?s)` 让 `.` 也匹配换行。`collect-pay-server` — `log4j2-collectpay.xml:37`~`:43`
- **VictoriaLogs appender 段与公共配置完全同口径、MUST 保留**：url 为空时自动禁用、不起 `vlogs-sender` 线程；collect-pay 已注入 `VLOGS_URL`，删掉这段本服务的日志上报就会消失。两级过滤：带 `x-vlogs-capture=1` 的链路全量上报，其余只上报 WARN 及以上。`collect-pay-server` — `log4j2-collectpay.xml:89`~`:93`
- **业务包 logger 的 level 与 appender 引用刻意与 Root 一致**，只为让 `x-vlogs-capture` 链路采集生效；**NEVER 给这里的 `VictoriaLogs` 引用加 `level` 属性**——`AppenderRef` 的 level 是引用层过滤器、先于 appender 自己的 `ThreadContextMapFilter` 求值，加了就会把 INFO 短路掉。`collect-pay-server` — `log4j2-collectpay.xml:145`~`:149`

### 附.3 陷阱

#### 附.3.1 Druid WallFilter 两条规则

- **SQL 正文 NEVER 出现行注释或块注释**：Druid WallFilter 的 `commentAllow=false` 会把带注释的语句判定为注入、**语句静默失效**。共同约定一律写在 XML 注释里。同一条禁令在本模块 mapper XML 里出现三处 — `ReconExportMapper.xml:6`~`:7`、`AppRefundOrderMapper.xml:60`、`TvmAppOrderMapper.xml:119`
- **mapper XML 的注释里也 NEVER 出现连续两个半角减号**，否则 XML 解析直接报错（进而 `sqlSessionFactory` 建不起来、服务启动即挂）。本模块把这条与上一条写成一组、成对出现 — `ReconExportMapper.xml:7`~`:8`
- **注**：本次抽取未在本模块 mapper XML 中见到「`where 1 = 1` 打头 + 全部谓词可选 `<if>`」那条 WallFilter 规则的显式告示（AGENTS.md §5.1 第二条）。该规则对本模块**仍然适用**，只是**代码注释里没有对应条目**，属抽取源缺失、不是已确认无风险。

#### 附.3.2 Oracle 与列名陷阱

- **`TRANS_AOUNT` 少一个 M 是 DDL 原文**（`collect-pay-server/sql.txt` 第 136 行），**NEVER 顺手改正**——改了就是 `ORA-00904`。`collect-pay-server` — `ReconExportMapper.xml:35`~`:36`；`mapper/ReconExportMapper.java:114`~`:115`
- **混合大小写列名不带双引号会 `ORA-00904`**，因此 `totalPrice` / `merchantOrderNo` / `PAYCENTER_channelOrderNo` 一律回避。`collect-pay-server` — `ReconExportMapper.xml:38`~`:39`
- **`SUM` 跳过 NULL、全组皆 NULL 时返回 NULL**，所以外面必须包 `NVL`。`collect-pay-server` — `ReconExportMapper.xml:30`~`:31`
- **`TO_NUMBER` 撞到 `VARCHAR2` 脏值抛 `ORA-01722`**，两处都被记为陷阱：对账侧靠「金额列不做隐式转换」控制口径；退款侧靠 `REGEXP_LIKE` 只取纯数字行，删了它整个退款请求会连「留证据」的落库一起失败。`collect-pay-server` — `ReconExportMapper.xml:32`~`:33`；`mapper/AppRefundOrderMapper.java:54`~`:57`
- **`resultMap` 有、DDL 无的列不能引用**：`TBL_TVM_APP_ORDER` 的 `DEVICE_ID`、`TBL_TVM_ORDER_TOPUP` 的 `PAYCENTER_ORDERNO` / `PAYCENTER_CHANNELORDERNO` 都属这类「仓库已知不一致」。`collect-pay-server` — `ReconExportMapper.xml:111`~`:112`、`:130`~`:131`；`mapper/ReconExportMapper.java:77`~`:79`

#### 附.3.3 yml 与 Deployment env 的覆盖关系

- **`server.port` 在仓库里是 `58101`，线上被 Deployment env 顶成 `8080`**，而 `collect-pay-c23ku-svc` 的 `targetPort` 就是 **8080**。判断本模块实际监听端口 MUST 查 Deployment env，NEVER 只看 yml。`collect-pay-server` — `application.yml:55`~`:56`（该覆盖关系的实测记录在 AGENTS.md §8「为什么不能改 Service selector」一条与 `docs/ops/流量切换.md`）
- **`service.recon.url` 是本模块唯一带 `${ENV:}` 包装的服务地址**（`${SERVICE_RECON_URL:...}`），另外两条 `service.ticket.url` / `service.account.url` 是**裸的 `http://127.0.0.1:9103` / `http://127.0.0.1:9098`**——在 K8s 里等于打到自己，线上只能靠 Deployment env 覆盖。`collect-pay-server` — `application.yml:96`~`:106`
- **`recon.export.*` 三个键都带 `${ENV:}`**（`RECON_EXPORT_TEMP_DIR` 默认 `/home/javaapp/app/recon-export`、`RECON_EXPORT_PAGE_SIZE` 默认 5000、`RECON_EXPORT_WORKER` 默认 1）。`collect-pay-server` — `application.yml:110`~`:113`
- **`Properties` 段的位置在 log4j2 里是硬约束**：**NEVER 把 `Properties` 之后的任何元素挪到它前面**。2026-09-11 实测把 `<Filters>` 写在 `<Properties>` 之前，整份配置的 `${logPath}` / `${appName}` / `${webLoggerLevel}` **全部不做替换**，日志文件被建成字面量路径 `/home/javaapp/soft/${logPath}/${appName}.log`，Root 级别失效、INFO 全丢（`status="off"` 时 log4j2 自身的报错也被吞掉，只能靠「文件名里带 `${}`」反推）。`collect-pay-server` — `log4j2-collectpay.xml:21`~`:26`
- **`%X{traceId}` 那一列在本模块有值的前提是上面那三行 tracing 配置生效**；`logPattern` 一直写着 `%X{traceId}`。`collect-pay-server` — `log4j2-collectpay.xml:34`；`application.yml:85`~`:87`
- **`InternalMicroHttp` 会把整个请求头 Map 打进 INFO 日志（含内部令牌明文）**，因此被压到 WARN；该压制原先写在 `application.yml` 的 `logging.level` 里，改用本文件后一并搬进 Logger 段——**排查这条时 NEVER 只看 yml**。`collect-pay-server` — `log4j2-collectpay.xml:129`~`:132`

#### 附.3.4 `testngbackV2` 测试地址残留（三条）与 `localhost` 回调、占位域名

- **`notice-app-*-url` 三条全是裸硬编码的 `testngbackV2` 测试地址**（`https://dtcustomer.bestonepay.com/testngbackV2/ci/app/v2/receiveTakeTicketResult` / `receiveTakeTicketFaultResult` / `receiveRefundResult`），无 `${ENV:}` 包装，线上只能靠 Spring relaxed binding 的 env 覆盖。`collect-pay-server` — `application.yml:36`、`:37`、`:39`
- **`pay.center.callback-url` 是 `http://localhost:9098/ci/app/ticketCollectPayNotify`**——`localhost` 在 K8s 里等于打到自己 Pod，且端口 `9098` 与本模块的 `server.port`（yml 58101 / 线上 8080）都不一致。`collect-pay-server` — `application.yml:21`
- **`pay.center.gateway-url` 是占位域名 `https://pay-gateway.example.com/api`**；同段下面五条真实支付中心 URL（`pay-center-pay-url` / `pay-center-query-url` / `pay-center-refund-url` / `pay-center-jhm-url` / `query-refund-url`）都是完整 `.../api/v1/...` 形态、与 `gateway-url` 无关。`collect-pay-server` — `application.yml:23`、`:28`~`:34`
- **`pay-notice` 与 `app-refund-notice-url` 两条指向同一个公网地址** `http://58.56.166.170:48000/itptvm/ci/tvm/payNotice`（即经 istio 网关绕回自己的 `/itptvm/` 前缀）。`collect-pay-server` — `application.yml:32`~`:33`
- **`pay.center.private-key` 与 `jhm.key` 在 yml 里是明文真值、没有 `${ENV:}` 包装**，与 AGENTS.md §5.2「敏感配置 MUST 写成 `${ENV_VAR:}` 并由 K8s Secret 注入」冲突。本条只记键名与位置、不回显值。`collect-pay-server` — `application.yml:25`、`:52`
- **数据源账号口令同为明文**（`other.sql.username` / `other.sql.password`），同上只记键名与位置。`collect-pay-server` — `application.yml:10`、`:12`

#### 附.3.5 其它运行期陷阱

- **`waitMillis` 超过 15000 会变成网关 504**：`fep-app-vr` 的 `/itptvm/` 路由没配 timeout、走 Envoy 默认 15 秒，TVM 收到的就不是我方响应。`collect-pay-server` — `application.yml:65`~`:66`；`service/impl/TvmTakeTicketServiceImpl.java:55`~`:57`
- **`refundTime.substring(0, 8)` NEVER 再用**：2026-08-27 生产事故——支付中心返回的退款时间是 ISO 形态 `2026-08-27T12:55:57`，截前 8 位得到 `"2026-08-"`，APP 侧解析 `refundDate` 直接失败（订单 `0020260827131329f68e95f4`）。现在的写法是剥掉所有非数字字符再取前 14 位，对 `yyyyMMddHHmmss` 与 `yyyy-MM-dd HH:mm:ss` 两种形态都成立。`collect-pay-server` `AppOrderServiceImpl.normalizeRefundTime` — `service/impl/AppOrderServiceImpl.java:713`~`:720`
- **NEVER 再用 `businessType` 把 APP 主动退款排除在通知之外**：2026-08-27 生产事故——APP 退款走 `BusinessTypeEnum.APP_REFUND`（`requestRefundTicket` 传入），而这里原来只放行 `TVM_SCAN_QR_TAKETICKET`，导致订单 `00202608271248044c519a98` 退款已成功、`TBL_APP_ORDER_REFUND.REFUND_STATUS=1`，但 `TBL_NOTICE_APP_REFUND_RECORD` **零条**、`NoticeAppTask` 每轮扫到 `size=0`，APP 永远停在「退款进行中」。`collect-pay-server` `AppOrderServiceImpl` — `service/impl/AppOrderServiceImpl.java:641`~`:645`
- **`dealAppRefundResult` 在退款失败分支同样返回 `true`**，因此落库时 **NEVER 写死成功**。`collect-pay-server` `AppOrderServiceImpl` — `service/impl/AppOrderServiceImpl.java:696`
- **`ORDER_NO_SEQ` 一旦改成不循环或放大上限，订单号会超过 20 位**（长度靠 `MAX_VALUE=9999` + `CYCLE=Y` 保证）。`collect-pay-server` `AppOrderServiceImpl.generateOrderNo` — `service/impl/AppOrderServiceImpl.java:394`~`:395`
- **`AppOrderServiceImpl` 里有两处 `// todo 通知app支付结果`**（支付成功与失败两个分支各一处），即支付结果通知在这两个分支上**没有实现**。`collect-pay-server` — `service/impl/AppOrderServiceImpl.java:457`、`:473`
- **`TvmTakeTicketServiceImpl` 顶部留有一条未决 todo**：「激活和激活订单查询的问题：1.要激活的订单在哪里，预设 tvm 表的订单，如果这样，是否要在这个表中加一个新字段（是否已激活字段)」。`collect-pay-server` — `service/impl/TvmTakeTicketServiceImpl.java:66`

### 附.4 墓碑注释清单（建议转为断言测试）

> 以下条目的注释形态是「已迁走 / NEVER 加回 / 本条路径暂无调用方 / 整段已删除或已注释」，按要求不进正文。路径前缀同上。每条给：`文件:行号` + 禁止的事 + 能否断言化。

1. `service/AppPayOrderInternalService.java:17`~`:22` —— 禁止在「两条写入路径并存」的状态下上线：gate-txn-pay-server 的 `mapper/AppPayOrderMapper.java` + `resources/mapper/AppPayOrderMapper.xml` 仍在原地直写 `TBL_TVM_APP_ORDER` / `TBL_TVM_ORDER_PAY_PRE`，本接口这一条**暂时无人调用**；完成切换的那一笔 MUST 同时删掉对方 mapper 与 XML、并改掉三处调用点与单测桩。**可断言**（跨模块）：gate-txn-pay-server 内不存在 `AppPayOrderMapper`，且 `SupplementOrderServiceImpl` 无私有 `registerAppPayOrder`。
2. `service/AppPayOrderInternalService.java:11`~`:15` / `controller/internal/AppPayOrderInternalController.java:17`~`:18` —— 禁止任何模块再跨域直写这两张表（owner 是本模块，写入 MUST 走 `/internal/app-order/**`）。**部分可断言**：全仓 grep 只有 collect-pay-server 出现这两张表的 INSERT / UPDATE。
3. `service/AppOrderService.java:71`~`:77` / `controller/page/AppOrderPageController.java:18`~`:20` —— 禁止给旧入口 `/ci/app/requestRefundTicket` 加金额字段、也禁止改它的金额来源（恒取 `PAY_AMOUNT` 全额）。**可断言**：`requestRefundTicket` 的入参只有 `orderNo`；对已部分退款订单调它会被支付中心拒。
4. `service/AppOrderService.java:79`~`:82` / `service/impl/AppOrderServiceImpl.java:538`~`:540` —— 禁止去掉 `refundByAmount` 的可退余额闸门（「让运营能强退」不是理由）。**可断言**：入参金额 > `PAY_AMOUNT` − 已成功退款额时不落 `TBL_APP_ORDER_REFUND`、不发支付中心请求。
5. `mapper/AppRefundOrderMapper.java:54`~`:57` / `AppRefundOrderMapper.xml:58`~`:59` —— 禁止删除 `sumSuccessRefundAmount` 里的 `REGEXP_LIKE` 纯数字过滤。**可断言**：库中存在非数字 `REFUND_AMOUNT` 时该查询不抛 `ORA-01722`。
6. `mapper/TvmAppOrderMapper.java:53`~`:59` / `TvmAppOrderMapper.xml:118` —— 关单禁止换用无条件的 `updateByOrderNo`；禁止把「影响 0 行」当失败重试。**可断言**：`updateStatusToFailedIfPending` 的 WHERE 含 `PAY_STATUS = '0'`；0 行时调用方返成功。
7. `service/impl/AppPayOrderInternalServiceImpl.java:98`~`:99` —— 同上的服务层墓碑：禁止把关单的 0 行当失败返回。**可断言**：对已支付订单调 `closePending` 返成功且不改状态。
8. `service/impl/BomOrderServiceImpl.java:686`~`:698` —— 禁止给 `notiTopupResult` 加回 `@Transactional`（2026-09-14 摘除）。**可断言**：该方法及其调用链无 `@Transactional`。
9. `service/ReconExportService.java:139`~`:143` —— 禁止给对账抽取链路加 `@Transactional`（链路内有 `sink.write` 的 HTTP 出网）。**可断言**：`runExport` / `exportPay` / `exportBus` 无 `@Transactional`。
10. `service/support/TakeTicketWaiter.java:24`~`:25` —— 禁止把取票挂起放进带 `@Transactional` 的方法。**可断言**：调用 `waitForActivation` 的方法链上无 `@Transactional`。
11. `service/impl/AppPayOrderInternalServiceImpl.java:50`~`:53` —— 禁止在 `register` 内新增任何远端调用（它是本模块少数**允许**带事务的方法，前提就是无 RPC）。**可断言**：该方法体内无 `*Client` / `RestTemplate` / `WebClient` 调用。
12. `ReconExportMapper.xml:41`~`:51` / `service/ReconExportService.java:199`~`:208`、`:252`~`:254` —— 禁止把 `LEFT JOIN STATION_INFO S ON S.STATION_CODE = T.IN_STATION_CODE` 与 `S.LINE_CODE` 加回四条 PAY 汇总；禁止在 `writePayRow` 的 `lineCode` 形参里塞自己算出来的线路值；禁止用车站码前 2 位推线路。**可断言**：本文件内无 `STATION_INFO`、无别名 `S`；`ITP.PAY` 第 2 段恒为空。
13. `mapper/ReconExportMapper.java:16`~`:18` / `ReconExportMapper.xml:10`~`:13` / `service/ReconExportService.java:27`~`:32` —— 原先给 `ITP.DETAIL` 用的**四条 keyset 明细 select 已整段删除**；禁止拿本模块四张收款表去凑 DETAIL 或 EXP 行。**可断言**：收到 `DETAIL` / `EXP` 指令时只记 warn 且不产出分片；本 mapper 内无明细分页 select。
14. `mapper/ReconExportMapper.java:12`~`:14` —— 禁止把对账聚合查询合并进联机链路的 mapper（现有 mapper / XML 一行都没改）。**可断言**：`ReconExportMapper` 只被 `ReconExportService` 引用。
15. `mapper/ReconExportMapper.java:95`~`:112` / `ReconExportMapper.xml:152`~`:164`、`:82`~`:84` / `service/ReconExportService.java:190`~`:197` —— 禁止在甲方给出判据前按 `TRANS_TYPE` 拆分 BOM 组（当前整表归「发售」，行政处理 15/16 与 BOM 处理 19/20 恒 0）；拆的那一笔 MUST 同时改 select 与 `ReconExportService`。**可断言**：BOM 汇总 SQL 的 WHERE 内无 `TRANS_TYPE` 谓词；idx 15/16/19/20 恒为 `0`。
16. `ReconExportMapper.xml:186`~`:195` / `service/ReconExportService.java:281`~`:286` —— 禁止给 `ITP.BUS` 的键加车站 / 设备 / 票种；禁止拿票价与实付之差推算优惠金额（四张表都没有优惠列）。**可断言**：BUS 行恒 4 段、第 4 段恒 `0`。
17. `service/ReconExportService.java:245`~`:249` —— 禁止写「键 + 本组两段」的短行（段数不足会让 recon-server 按下标错位读取，纯文本不报错）。**可断言**：PAY 每行恒 21 段。
18. `service/ReconExportService.java:58`~`:59`、`:61`~`:62`、`:170` —— 禁止改本源标识 `collect-pay`、禁止调整 21 段的字段顺序与段数。**可断言**：常量值与分片首字段不变。
19. `controller/internal/ReconExportController.java:14`~`:23` —— `X-Recon-Token` 校验已整段删除（用户 2026-09-11 要求），属临时降级：禁止把它当长期现状，上线前 MUST 恢复；恢复时禁止用 `String.equals` 比较令牌、禁止在仓库写默认真值。**可断言**（恢复后）：`recon.internal-token` 有读取方且比较走 `MessageDigest.isEqual`。
20. `controller/internal/AppPayOrderInternalController.java:20`~`:29` / `service/AppPayOrderInternalService.java:38`~`:41` —— 同上的补款侧墓碑，另加一条：**禁止拿「`/internal/recon` 也没加鉴权」当长期理由**（只读导出与「按订单号改他人支付状态」不同级）。**可断言**（恢复后）：`/internal/app-order/**` 无有效凭据时被拒。
21. `controller/page/AppOrderPageController.java:25`~`:31` —— 运营端 APP 退款端点当前无鉴权、可指定金额：禁止把服务层的余额闸门当成访问控制（它挡「退多了」，不挡「不该退的人来退」）。**可断言**（恢复后）：无凭据调用被拒。
22. `service/AppOrderService.java:84`~`:85` / `controller/page/AppOrderPageController.java:50`~`:51` —— 退款入口无幂等：禁止重放原列表、禁止假定「连调两次只退一次」；中断后 MUST 先查 `TBL_APP_ORDER_REFUND` 再续跑。**难以断言**（属操作规程，可做的是文档校验与调用方侧的一次性控制）。
23. `service/impl/AppOrderServiceImpl.java:641`~`:645` —— 禁止再用 `businessType` 把 APP 主动退款排除在通知之外（2026-08-27 生产事故）。**可断言**：`BusinessTypeEnum.APP_REFUND` 的退款成功后 `TBL_NOTICE_APP_REFUND_RECORD` 有行。
24. `service/impl/AppOrderServiceImpl.java:694`~`:701`、`:696` —— 禁止把退款结果列存成 `ItpStatusEnum` 的 `"1"`/`"2"`、禁止写死成功、禁止把退款时间存成 8 位日期。**可断言**：该列取值属 {`SUCCESS`,`FAIL`}，时间列长度 14。
25. `service/impl/AppOrderServiceImpl.java:713`~`:720` —— 禁止回退成 `refundTime.substring(0, 8)`（2026-08-27 生产事故）。**可断言**：输入 `2026-08-27T12:55:57` 时输出 `20260827125557`。
26. `service/impl/AppOrderServiceImpl.java:761`~`:762` —— 禁止让 `tvmexecutor` 线程里的推送与解析抛出异常（抛出后通知记录卡在 `status=0` 且 `retryTimes` 不递增）。**可断言**：推送异常时 `retryTimes` 仍递增或状态置 2。
27. `service/impl/AppPayOrderInternalServiceImpl.java:78`~`:80`、`:190`~`:196` —— 禁止只 `catch (DuplicateKeyException)`（本模块开了 tracing，观测切面会换异常类型），判定 MUST 沿 `getCause()` 链。**可断言**：包一层 `RuntimeException` 的完整性冲突仍被识别为幂等成功。
28. `service/impl/AppPayOrderInternalServiceImpl.java:147`~`:148` —— 禁止给空 `RSV2` 补默认值放行（等于替调用方定资损口径）。**可断言**：`RSV2` 为空时 `register` 直接拒。
29. `service/impl/TvmTakeTicketServiceImpl.java:180`~`:193` —— 原实现把「三要素查不到行」那支的 `2003` 注释掉了，禁止顺手改回去；两种「无激活订单」形态 MUST 与改动前逐字节一致。**可断言**：查不到行回 `2003`，查到未激活回带空订单对象的失败结构。
30. `service/impl/TvmTakeTicketServiceImpl.java:55`~`:57`、`:62` / `application.yml:65`~`:66` —— 禁止把 `waitMillis` 配到 15000 以上（Envoy 默认 15 秒会变成网关 504）、禁止把兜底回查间隔配到 500ms 以下。**可断言**：配置校验。
31. `service/impl/AppOrderServiceImpl.java:394`~`:395` —— 禁止把 `ORDER_NO_SEQ` 改成不循环或放大上限（订单号会超过 20 位）。**可断言**：新建 APP 订单号长度恒 20。
32. `service/AppOrderService.java:56`~`:65` —— `IF8B-05 receivePaymentResult` 的声明与 javadoc **整段被注释**；判断该端点是否存在 MUST 以注解为准、禁止据 javadoc 认为已实现。**可断言**：本模块无 `receivePaymentResult` 方法。
33. `controller/ci/app/CollectPayController.java:1`~`:67` —— 整个类（含 `IF8A-09` / `IF8A-10` / `IF8A-12` / `IF8A-13` 四条 URL）被注释，属**死代码**；`CollectPayService` / `CollectPayServiceImpl` 因此无 HTTP 入口。禁止据这四条 URL 认为端点存在。**可断言**：`/ci/app/requestPay`、`/payQuery`、`/requestRefund`、`/refundQuery` 返 404（或本项目的 UUID `retCode` 形态）。
34. `task/SingleTicketRefundTask.java:33`、`:68`、`:86` —— 三行 `@PostMapping`（`/refundAppNotTakeTickets` / `/refundBomSaleNotTakeTickets` / `/refundBomTopupNotTakeTickets`）被注释，即这三条 URL **当前不存在**、这三个补偿只能靠模块内 `@Scheduled` 触发。**可断言**：这三条 URL 无 handler。
35. `controller/ci/tvm/TvmOrderController.java:130`、`:134` —— IF2A-11 的两条入参校验（`orderNo` / `paymentVendor` 为空返 `8999`）被注释；禁止以「补齐校验」为理由加回来（会改变设备侧可见的失败码与形态）。**可断言**：缺这两个字段时不返 `8999`。
36. `constant/AppCodeEnum.java:9` / `constant/TvmPayCodeEnum.java:9` —— 两个枚举各有一行被注释的取值：`AppCodeEnum` 注释掉 `FAIL("2999","失败")`、**现行 `FAIL` 是 `9999`**；`TvmPayCodeEnum` 注释掉一个同值 `FAIL("2999")` 副本、现行仍是 `2999`。改错误码前 MUST 先确认现行行是哪一行。**可断言**：`AppCodeEnum.FAIL.getCode()` 为 `9999`、`TvmPayCodeEnum.FAIL.getCode()` 为 `2999`。（`DevicePayCodeEnum` 的 `ORDERED` / `SUCCESS` / `FAILED` 三项之间只有一个**空行**、没有注释掉的取值，2026-09-16 复核；**NEVER 据「行号不连续」推断那里有墓碑**。）
37. `service/impl/AppOrderServiceImpl.java:273`、`:290`、`:376` —— 三处被注释的实现残留（`updateParams.put("payDate", ...)`、一个 `@Override`、`order.setMerchantOrderNo("")`）。禁止据它们推断当前行为。**难以断言**（建议清理而非断言）。
38. `log4j2-collectpay.xml:21`~`:26` —— 禁止把 `Properties` 之后的任何元素挪到它前面（2026-09-11 实测会让全部 `${}` 不做替换、INFO 全丢且报错被吞）。**可断言**：配置内 `Properties` 是首个子元素；启动后日志文件名不含 `${`。
39. `log4j2-collectpay.xml:145`~`:149` —— 禁止给业务包 logger 的 `VictoriaLogs` 引用加 `level` 属性（会把 INFO 短路掉）。**可断言**：该 `AppenderRef` 无 `level`。
40. `log4j2-collectpay.xml:89`~`:93` —— 禁止删除 VictoriaLogs appender 段（collect-pay 已注入 `VLOGS_URL`，删掉日志上报会消失）。**可断言**：配置内存在该 appender 且 url 取自 `VLOGS_URL`。
41. `application.yml:75`~`:84` —— 禁止删除 `spring.autoconfigure.exclude` 那行 `OtlpAutoConfiguration`（Boot 3.2.6 没有 `management.tracing.export.enabled`，置空 endpoint 也无效）。**可断言**：注入 `MANAGEMENT_OTLP_TRACING_ENDPOINT` 后仍不起 OTLP exporter。
42. `ReconExportMapper.xml:35`~`:36` / `mapper/ReconExportMapper.java:114`~`:115` —— 禁止把 `TRANS_AOUNT` 改成 `TRANS_AMOUNT`（DDL 原文少一个 M，改了即 `ORA-00904`）。**可断言**：SQL 文本包含 `TRANS_AOUNT`。
43. `ReconExportMapper.xml:6`~`:8` / `AppRefundOrderMapper.xml:60` / `TvmAppOrderMapper.xml:119` —— 禁止在 SQL 正文写行注释或块注释；禁止在 XML 注释里出现连续两个半角减号。**可断言**：`xmllint --noout` 通过 + SQL 正文无注释符。
44. `ReconExportMapper.xml:105`~`:110`、`:167`~`:169` / `mapper/ReconExportMapper.java:65`~`:67`、`:115`~`:117` —— 禁止拿 `DEVICE_ID` 去猜车站或线路（设备编号与车站码没有可验证的换算关系）。**可断言**：这两条 select 的车站段恒空。

## 附：face-pay-server 源码注释知识抽取（2026-09-16，阶段二）

本轮只补阶段一漏掉的部分，**不改写、不删除阶段一任何条目**。抽取口径是机械穷举而不是抽样：把 `face-pay-server/src/main` 下 139 个文件的注释切成 **1074 个注释块**，逐块与阶段一附录里出现过的 `文件:行号` 定位串比对（容差 ±3 行），阶段一引用过的块整块跳过，全部由 `@param` / `@return` / `/** 构造方法 */` 之类样板行构成的块也跳过，剩下 **747 块 / 2246 行** 才是本轮的输入。本轮同样只读代码、只写本文档，未改动任何 `.java` / `.xml` / `.yml` / `.properties` / `.sql`。

定位串前缀约定沿用阶段一：Java 类前缀 = `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/`，mapper XML 前缀 = `face-pay-server/src/main/resources/mapper/`，`application.yml` / `sql/*.sql` 前缀 = `face-pay-server/src/main/resources/`。

### 八、设备公共报文层（`api/device` 根与字段级契约）

- **设备侧公共请求参数字段名与旧 `BaseRequestDTO` 逐字一致，因为「设备发的就是这些名字」**；接入形态**不可改**：`application/x-www-form-urlencoded` + `@ModelAttribute` 表单绑定，业务参数在 `bizData` 里是 JSON 字符串、需二次反序列化。**本链路没有验签**，`sign` / `signType` 只是被拷进 DTO，**新增鉴权属契约变更、NEVER 在重写里顺手加**。`BaseDeviceRequest`（类注释）— `api/device/BaseDeviceRequest.java:3`
- **`providerId`（商户编码）的完整值域只在这一处**：`01`-APP、`02`-TVM、`03`-BOM、`04`-AGM、`05`-ACC、`06`-ITP、`07`-STT。`F2fChannel` 只映射了前三个，**排查「设备送了 04/05/07 怎么办」MUST 看这条**。`BaseDeviceRequest.providerId` — `api/device/BaseDeviceRequest.java:14`
- **`signType` 值域 `00`-不签名 / `01`-sha1withrsa / `02`-MD5**；`timestamp` 格式 `YYYYMMDDHHMMSS`；`charset` 固定 UTF-8、`format` 固定 json。这些是设备侧信封的既有取值，**face-pay 一个都不校验**。`BaseDeviceRequest` — `api/device/BaseDeviceRequest.java:23` / `:29`
- **`bizData` 为空或非法 JSON 时 `unwrap` 返回 `null`，由调用方回 `2002`**（不是抛异常）。`DeviceRequests.unwrap` — `api/device/DeviceRequests.java:20`
- **`TicketInfo` 的三个字段就是 `F2F_TICKET` 的业务主键与金额来源**：`(ticketLogicNum, transDate)` 正是 `UK_F2F_TICKET_LOGIC` 的两列；`transDate` 设备侧可能是 `yyyyMMddHHmmss` 或 `yyyyMMdd`，**原样落库不做归一**。金额转 `Long` **失败不抛异常**——出票结果是既成事实，不能因为一个金额格式问题把整批票丢掉，`priceInFen()` 返 null 并留在 `F2F_RESULT_REPORT.RAW_BODY` 里备查。`TicketInfo`（类注释）— `api/device/TicketInfo.java:3`、`:24`
- **`NotiTakeTicketFailResultReqDTO.actualNum` 允许为 0（完全没出票），但解析失败 MUST 返 null 而不是当 0** —— 当 0 会退全款。同一 DTO 里 `faultSlipSeq` 是 TVM 打印的故障单号、乘客凭它到 BOM 处理，落 `F2F_RESULT_REPORT.FAULT_SLIP_SEQ`；`ticketList` **可能是空列表**（一张都没出）。`api/device/tvm/NotiTakeTicketFailResultReqDTO.java:20` / `:26` / `:35` / `:38`
- **`RequestPaymentReqDTO` 的两条错误码是 `8999`（不是 2002）**：`orderNo` 为空回「orderNo不能为空」、`paymentVendor` 为空回「paymentVendor不能为空」；`paymentCode`（乘客付款码）**旧实现不校验但原样送支付中心**，且 `toString()` **NEVER 打印 paymentCode** —— 付款码等同于支付凭证。`api/device/tvm/RequestPaymentReqDTO.java:16` / `:19` / `:22` / `:49`
- **IF2A-03 请求里的 `userId` 在旧实现的 TVM 查询链路上从未被使用，保留仅为报文兼容**；真正被读的只有 `orderNo`（为空回 `2002`）。`RequestPayResultReqDTO`（类注释）— `api/device/tvm/RequestPayResultReqDTO.java:5` / `:17`
- **`RequestGenSjtOrderReqDTO` 的条件必填关系**：`singleTicketType=0`（按站点购票）时起终点站码必填，`=1` 是按固定票价购票；`payType=0` 走本地聚合码分支、不调支付中心。**`singelTicketNum` 拼写少一个 t 是既有契约**（阶段一已记，此处补的是必填矩阵）。`api/device/tvm/RequestGenSjtOrderReqDTO.java:20` / `:29` / `:32` / `:35`
- **`TopupCardResultNotiReqDTO.ticketLogicNum` 会在 service 内与订单上的卡号比对，不一致即拒绝**；`afterAmount` 回写 `F2F_ORDER.CARD_AFTER_AMOUNT`，**解析失败时不回写该列**（返 null 而不是 0）。`api/device/tvm/TopupCardResultNotiReqDTO.java:20` / `:32` / `:40`
- **`TopupCardFailNotiReqDTO.needRefund()` 的唯一判据是 `topupStatus=01`**；`02` 存疑 / `03` 取消都不退。`api/device/tvm/TopupCardFailNotiReqDTO.java:27` / `:42`
- **BOM 侧三个拼写照搬点**：`RequestGenNoCashOrderReqDTO` 的 `operater`（操作员，少一个 t）与交易金额字段拼写错误、`RequestCardDataUpdateReqDTO` 的操作员号同样少一个 t、`NotiBusResultReqDTO` 的交易日期字段拼写照搬。**改任何一个等于收不到值。** `api/device/bom/RequestGenNoCashOrderReqDTO.java:23` / `:30`、`api/device/bom/RequestCardDataUpdateReqDTO.java:19`、`api/device/bom/NotiBusResultReqDTO.java:23`
- **BOM 交易类型 `transType` 的九个取值是 `02/03/04/05/06/22/2A/2B/42`，代码只对 `42` 做校验**（`42` 行政处理时 `adminTransType` 必填，取值 `01~0A`）；`bomOptSeq` 是幂等键。`api/device/bom/RequestGenNoCashOrderReqDTO.java:17` / `:20` / `:33`
- **`NotiBusResultReqDTO` 的退款判定口径是「只有字面量 `FAILED` 才退款，其它取值静默忽略」**（旧实现口径，照搬）。`api/device/bom/NotiBusResultReqDTO.java:16` / `:26`
- **`NotiTopupResultReqDTO`：`00` 充值成功、`01` 充值失败需退款**，只这两个取值。`api/device/bom/NotiTopupResultReqDTO.java:14`
- **HCE 数据只打长度不打内容**（`notiUpdateHceData` 的 `hceData`）。`api/device/bom/NotiUpdateHceDataReqDTO.java:43`

### 九、响应构造层补遗（`TvmResponses` / `BomResponses` / `AppResponses` / `PayCenterResponses`）

- **「成功与失败共用一个私有 `*Body(...)`」这条模式在 TVM 侧共 5 组**：下单类两键（拉码 / 充值四支共用，`TvmResponses.java:60`）、查询支付结果三键（`:101`）、取票鉴权八键（`:171`）、退款三键（`:315`）、出票上报「订单不存在」的同一句文案（`:324`）。**改任一组 MUST 同时看成功与失败两支**，否则键集会漂。
- **充值下单与拉码下单同形态（`orderNo` + `payUrl`）**：旧实现是两个方法但字段一致，重写后 IF2A-09 的失败键集直接复用 `genSjtOrderFail`。`TvmResponses.topupOrderSuccess` / `topupOrderFail` — `api/device/tvm/TvmResponses.java:110` / `:115` / `:120`
- **`payUrl` 是整串给设备显示成二维码、设备不解析**——这条决定了 `payType=0` 本地聚合码分支可以直接拼 URL 返回。`api/device/tvm/TvmResponses.java:35`
- **BOM 族错误码三个取值**：成功 `0000`（与 TVM 一致）、失败 **`8999`（不是 TVM 的 2999）**、订单号错误 / 订单不存在另有专用码。`BomResponses` — `api/device/bom/BomResponses.java:28` / `:31` / `:34`
- **BOM 单程票退款失败：参数与状态类走 `8999`，但「订单不存在」那一支是 `9999`（BOM 域仅此一处），NEVER 归一成 8006 或 8999**；业务值一律 JSON null，**NEVER 把 `refundResult` 填成 `FAILED`** —— 这些分支表达「请求没被受理」。`BomResponses.refundFail` — `api/device/bom/BomResponses.java:165`
- **IF5A-01 票卡分析：14 个业务 key 逐字照搬旧 `RequestCardDataAnalyseRespDTO`，其中 `lastTransAmout`（少一个 n）与 `lastTikcetTransSeq`（Ticket 写成 Tikcet）两个拼写错误是既有契约，NEVER 更正**；`adviceOpt` 在下游是 `List<String>`、原样放入由 Fastjson 序列化成数组。`BomResponses.cardDataAnalyse` — `api/device/bom/BomResponses.java:188`
- **票卡分析失败：13 个业务键值全 JSON null，`adviceOpt` 给的是 null 而不是空数组** —— 给 `[]` 等于告诉 BOM「分析成功、没有建议操作」，与「分析失败」不是一回事。**NEVER 改成空数组或空串。** `BomResponses.cardDataAnalyseFail` / `cardDataAnalyseBody` — `api/device/bom/BomResponses.java:206` / `:218`
- **单程票交易查询失败：6 个业务键全 null，`transAmount` 同样是 null 而不是字符串 `"0"`** —— 给 0 会被 BOM 读成「查到了、金额为零」。`BomResponses.orderResultFail` / `orderResultBody` — `api/device/bom/BomResponses.java:274` / `:284`
- **APP 族错误码分工**：`0000` 成功、`8001` 下单与请求支付信息的参数校验失败、`8003` 查询类接口的参数校验失败、`8999` 既是「支付中」也是 service 层通用失败（旧 `failMessage`）。**同一个 `8999` 承载两种语义是既有契约。** `AppResponses` — `api/device/app/AppResponses.java:17`~`:29`、`:118`、`:123`
- **回给支付中心的应答只有 `{code, msg}` 两键，且应答语义决定对端是否重推**：只有 `code=0` 表示我方已受理；**状态不明时 MUST 返回失败让对端重推，NEVER 为了「让日志干净」回成功** —— 那等于把一笔支付结果永久丢掉。另有专用的「订单不存在」码。`PayCenterResponses`（类注释）— `api/paycenter/PayCenterResponses.java:5`、`:21`、`:37`

### 十、Controller 层补遗（`controller/ci/**`、`controller/page`、`controller/notice`）

- **`TvmOrderController` 里 `providerId=03` 常量的唯一用途是「在 TVM 的 URL 上分流到 BOM 柜台售票」**，与旧实现一致。`controller/ci/tvm/TvmOrderController.java:58`
- **IF2A-04 心跳与旧实现的真实差别是「现在真的落库了」**：旧实现在 controller 里直接 return success、从不记录任何心跳。`controller/ci/tvm/TvmOrderController.java:107`
- **两个上报接口的重复上报由 `UK_F2F_REPORT_IDEM` 幂等挡住**（出票成功 IF2A-05 / 充值成功 IF2A-06），controller 注释即写明这一点。`controller/ci/tvm/TvmOrderController.java:123` / `:258`
- **IF8A-15 激活取票订单是「手机扫 TVM 二维码后调用」**，IF2A-08 取票鉴权**没有 orderNo**、靠二维码三要素定位。`controller/ci/tvm/TvmOrderController.java:218` / `:230`
- **BOM 侧 IF8A-06 查询支付结果由 BOM 自己轮询**：旧实现的 `requestPayment` 在**服务端 `Thread.sleep` 轮询最长 180 秒**，新实现让设备轮询、服务端不再阻塞（AGENTS.md §5.2）。`controller/ci/bom/BomOrderController.java:169`
- **IF5A-03 票卡更新的校验项逐条对齐旧实现，且「不校验」是裁决过的**：旧服务只校验 `cardId` / `adviceOpt` / `updateStationCode` / `optDate`（四条都回 `8003 xxx不能为空`），**不校验 `updateType`、也不校验 `optDate` 格式** —— 这两种情况旧服务直接透传 ticket-server 并返下游码（实测 `8004 未注册用户`）。曾按「补齐校验」加过这两条，用户 2026-09-11 裁决「不校验」已移除，**NEVER 再以「旧实现漏校验」为理由加回来**。`controller/ci/bom/BomOrderController.java:262`
- **BOM 前缀的两条出票上报别名（`notiTakeTicketResult` / `notiTakeTicketFailResult`）是为了兼容现场设备的实际 URL**：2026-09-16 实测订单 `00202609160953330199` 已 `PAID`、票已实际出，上报却落到静态资源解析、被全局异常处理器兜成 HTTP 200 + UUID `retCode`，订单永久卡 `PAID`、`F2F_TICKET` / `F2F_RESULT_REPORT` 零行（ADR-D97）。**两条别名与 TVM 前缀同一实现、同一响应族（2xxx）**；**渠道归属兜底值是 BOM（不是 TVM），NEVER 把兜底值改成 TVM** —— 与 `TvmOrderController.channelOf` 只差这一个默认值。`controller/ci/bom/BomOrderController.java:306` / `:332` / `:344`
- **BOM controller 注入的是七个业务服务**（BOM 非现金订单 / 心跳 / 扫码付 / 充值下单 / 充值结果 / HCE / 出票上报），其中出票上报服务是被两条 BOM 别名复用的。`controller/ci/bom/BomOrderController.java:73`
- **APP 域退款三条（申请 / 查询 / 支付中心回调）2026-09-16 从 `F2fAppOrderService` 拆出，URL 与响应形态一行未改**；退款回调的入参是**支付中心信封、不是设备信封**，**且不验签（与旧实现一致）**。旧实现「校验 `orderNo` 非空却用 `refundNo` 查库」——只传 `orderNo` 时过校验但必然查不到，本实现两者都校验。`controller/ci/app/AppOrderController.java:45` / `:182`
- **运营端时间入参解析刻意留在 Controller、不下沉 service**：格式错就直接回「时间格式必须是 yyyy-MM-dd HH:mm:ss」；分页归一化与检索范围校验才在 `F2fOrderQueryService`。`controller/page/FacePayOrderPageController.java:48`
- **运营端全额退款的金额只从订单总额算、不信任页面输入**（页面只能填退款原因），照搬旧实现的这条约束。`controller/page/FacePayOrderPageController.java:111`
- **三个通知重投端点按 `NOTIFY_TYPE` 分别只投一类**（`TAKE_TICKET_OK` / `TAKE_TICKET_FAIL` / `REFUND_RESULT`）：运维点「重投退款通知」时不该把取票通知也发一遍。`controller/notice/NoticeAppTaskController.java:74` / `:80` / `:86`
- **对账抽取端点的 `accepted=false` 是限流、不是失败**（同批次上一轮仍在执行）。`controller/internal/ReconExportController.java:42`

### 十一、支付中心通道（`channel/paycenter` + `api/paycenter`）

- **`PayCenterClient` 的四条硬性约束**（类注释）：① **NEVER 在 `@Transactional` 方法里调本类**（2026-08-26 生产事故）；② **本类不抛异常、也不返回 null**，传输层失败返回 `isTransportFailed()` 为真的结果、调用方按 `UNKNOWN` 落库；③ **NEVER 打印 `bizData` 明文 / `sign` / 私钥**，请求侧只打已脱敏的 `PayCenterRequest.toString()`；④ 响应原文**全量**返给调用方落 `F2F_PAYMENT.RESPONSE_BODY` 留证、出错时也留。传输用 **JDK 21 内置 `java.net.http.HttpClient`**：不引新依赖，且它在虚拟线程上阻塞不会 pin 载体线程（旧实现用的 commons-httpclient 3.x 已不维护）。`channel/paycenter/PayCenterClient.java:17`
- **URL MUST 是配置里的完整地址、NEVER 由 base+path 拼**（`pay.center.*-url`）。`PayCenterClient.post` — `channel/paycenter/PayCenterClient.java:53`；同款判据在 `PayCenterProperties`（类注释，含「漏 `/v1` 时对端返 `code=600 操作失败`、不是 404」）— `channel/paycenter/PayCenterProperties.java:5`
- **日志里 `status` 原样打出来的那一列 MUST 保留**：支付中心网关文档 §5.1 **没有列 `status` 值域**，实测取值只能靠日志反推；2026-09-11 已因此踩过一次（枚举缺 `FAIL`，支付失败回调收不了口）。`channel/paycenter/PayCenterClient.java:113`
- **`PayCenterSigner` / `PayCenterMessageFactory` 故意不加 `@Component`**：它们是纯 Java 类，报文与待签串的口径必须能在不起 Spring 的单测里逐字比对；装配集中在 `PayCenterChannelConfig`，配置项读取点只有那一处。`channel/paycenter/PayCenterChannelConfig.java:7`、`PayCenterMessageFactory.java:10`、`PayCenterSigner.java:16`
- **bizData 用 `LinkedHashMap` 而不是 POJO，是因为键序会进入待签串**：改用 POJO 时键序取决于 Fastjson 的字段排序策略，**升级依赖就可能静默改变签名内容**。三个场景的键序差异（`scan` 把 `authCode` 放在 `notifyUrl` 之前、且**不带 `payType`**）是旧实现既有形态、原样保留。`PayCenterMessageFactory`（类注释）— `channel/paycenter/PayCenterMessageFactory.java:10`
- **待签串口径（用户 2026-09-09 裁决：对齐 pay-sign-server）**：把 bizData 的原始 JSON 解成 `TreeMap`（键名字典序）→ 跳过 null 与空白值 → `key=value` 用 `&` 拼接；**信封的 `merchantNo` / `apiVersion` / `signType` / `charset` 四个字段不参与待签串**；嵌套对象/数组按键排序后序列化。来源 `PayGatewayClient.buildSignSource:111-124`。`PayCenterSigner`（类注释）+ `buildSignSource` / `signValue` — `channel/paycenter/PayCenterSigner.java:16` / `:77` / `:104`
- **两个模块打同样三条 URL 却曾用两套待签串，且其中至少一条的验签在网关侧实际未生效**：`collect-pay-server`（`SignUtils.buildSignData`）**签外层信封**（固定 5 键、不排序、不剔空，`bizData` 是整个 Base64 串），`pay-sign-server` **签 bizData 内部字段**。两套没有交集（一个签壳、一个签芯），而两条链路都在生产跑通过。裁决以 pay-sign-server 为准，**回退方式写在注释里**：把 `buildSignSource` 换回信封五键拼接、并把 `sign` 的第二个入参去掉。`channel/paycenter/PayCenterSigner.java:29`~`:45`
- **签名失败 MUST 抛异常，NEVER 返回空串蒙混过关**；`sign(request, bizDataJson)` 的第二个入参**是 Base64 之前的原始 JSON**，不能传 `request.getBizData()`（那已是 Base64 后的串）。旧 `SignUtils:33` 有一行 `log.info("privateKey is {}", privateKey)`，**本类不予保留**。`channel/paycenter/PayCenterSigner.java:64`、`:47`
- **聚合码 URL 的签名是 `md5("orderNo=" + orderNo + "&key=" + jhmKey)`**（照搬旧 `SignUtils.getJhmSign`），只有一个参数因此 TreeMap 排序无实际作用。`channel/paycenter/PayCenterSigner.java:111`
- **`PayCenterResult` 的三种结局必须区分**：业务成功（`code=0`，再看 `status()`）/ 业务失败（`code != 0`，对端明确拒绝、可判 FAILED）/ **没答上来（`isTransportFailed()`：超时、连不上、HTTP 非 2xx、响应体不是合法 JSON）—— 此时 NEVER 判 FAILED，MUST 落 `PAY_STATUS='UNKNOWN'`**。**这是旧实现最大的坑**：`PayCenterServiceImpl.callPayCenter` 异常后 `return null`，调用方一律按「支付中心返回结果为空」写 `status=FAILED`，把「不知道」当成了「没付成功」。`channel/paycenter/PayCenterResult.java:6`、`:58`、`:63`
- **`PayCenterStatus.FAIL` 是支付中心真实下发的失败取值，`FAILED` 从未被观测到**：旧 `PayCenterStatusEnum` 只有 `FAILED`，于是支付失败回调永远落进「状态不明确」分支回 `-1`、支付中心按退避**无限重推**（2026-09-11 并跑期间在新服务上复现为重推风暴）。同一份网关文档的退款回调（`refundResult`）明确写的是 `FAIL`，支付回调 §5.1 则没列值域，因此**两个都收；判失败 MUST 走 `isFailed()`，NEVER 写 `== FAILED`**；解析不出取值时 `fromCode` 返 `null`、调用方按 `UNKNOWN` 收口。`channel/paycenter/PayCenterStatus.java:3`、`:24`、`:26`、`:40`、`:45`
- **`PayScene` 三态来自旧实现三个报文组装方法、不是我方设计**：`QRCODE`（设备拉码、`paymentVendor` 旧实现**固定 `0C`**、不由入参决定）/ `SCAN`（设备扫用户付款码，BOM 与 TVM 扫码都走这条，**必须带 `authCode` 且旧实现不传 `payType`**）/ `APP`。**STT 未接入、落地时属于哪一种取决于「谁扫谁」，需求未定，因此枚举不预留 STT 值。** `channel/paycenter/PayScene.java:3`、`PayCenterPayCommand.java:3` / `:27` / `:48`
- **退款报文三个订单号都要传**：`refundOrderNo`（我方退款单号，幂等键）、`merchantOrderNo`（我方原支付订单号）、`orderNo`（**支付中心侧**原订单号）。后者为空时支付中心按前者定位，但旧实现始终传，此处保持一致。`PayCenterMessageFactory.refund` — `channel/paycenter/PayCenterMessageFactory.java:79`
- **支付结果查询的入参是我方订单号、报文键名却是 `merchantOrderNo`**，勿与响应里的 `orderNo`（支付中心订单号）混淆。`PayCenterMessageFactory.query` — `channel/paycenter/PayCenterMessageFactory.java:67`
- **`payType=0` 的本地分支不调支付中心、只拼聚合码收银台 URL**（照搬旧 `TvmOrderServiceImpl:96-103`）；**这条分支的 `F2F_PAYMENT` 该记什么状态在设计里仍是空的**（`docs/architecture/face-pay-refactor.md` §十六 P1），**实现落库前 MUST 先定口径**。`PayCenterMessageFactory.localAggregateUrl` — `channel/paycenter/PayCenterMessageFactory.java:107`
- **信封组装时原始 JSON 要单独留一份传给签名器**：待签串取自 bizData 的内部字段、信封里放的是它的 Base64，两者不能互相推导。`PayCenterMessageFactory.envelope` — `channel/paycenter/PayCenterMessageFactory.java:123`
- **`PayCenterRequest.bizData` 是 Base64(业务 JSON, UTF-8) 而不是裸 JSON**：旧实现三套客户端里**只有走 `PayCenterCommon` 的这一套是线上真实形态**，另两套（`CollectPayServiceImpl`、`TvmTopupServiceImpl` 的私有方法）发裸 JSON **且都是死代码，NEVER 参照它们**；`toString()` 里 `bizData` 与 `sign` 只出长度。`channel/paycenter/PayCenterRequest.java:3` / `:72`
- **入向回调的 `bizData` 同样是 Base64，取明文 MUST 走 `bizDataJson()`**，**NEVER 把 `getBizData()` 直接丢给 `JSON.parseObject`** —— 重写初版就是这么写的，真实回调必然解析失败（2026-09-11 定位）。判定「解出来以 `{` 开头」而不是「Base64 解码没抛异常」，因为**裸 JSON 串里只含 Base64 字母表字符时也能被解码成乱码而不报错**；兜裸 JSON 的理由是联调回放多为人手构造 + 旧死代码曾发裸 JSON。`api/paycenter/PayCenterCallbackRequest.java:6` / `:84` / `:109`
- **⚠️ 安全项（已知风险、有意保留）：支付结果回调带 `sign` 但旧实现从不验签，本次重写保持同契约、同样不验签** —— 意味着**任何网络可达方都能构造一条 `status=SUCCESS` 的回调把订单改成已支付**。加验签属契约变更 + 支付安全红线（AGENTS.md §5.2），**MUST 人工评审后单独实施，NEVER 在重写里顺手加**；已在交付说明里单列为风险项。`api/paycenter/PayCenterCallbackRequest.java:17`
- **回调业务报文里两个订单号极易搞混**：`orderNo` 是**支付中心侧**订单号，`merchantOrderNo` 才是**我方**订单号 —— 定位本地订单 MUST 用后者。旧实现 `payNotice` 查库用的是 `merchantOrderNo`，**但同一方法的日志打的是 `orderNo`，读日志时容易被误导**。`api/paycenter/PayNoticeReqDTO.java:3`
- **回调 `status` 值域 `SUCCESS / FAILED / ORDERED / UNPAID`，`payChannelCode` 回填 `F2F_PAYMENT.PAY_CHANNEL_CODE`，金额单位分**。`api/paycenter/PayNoticeReqDTO.java:24` / `:42`

### 十二、APP 出向通知通道（`channel/app`）

- **判定 APP 应答成功有两套码并存**：APP 侧成功码（与设备侧 `retCode` 同族）与**部分网关用 `code=0` 表示成功**（支付中心那套口径），两者都收。`channel/app/AppNotifyClient.java:92` / `:95` / `:172`
- **通知地址为空时直接返回失败、NEVER 静默跳过** —— 配置缺失也要留痕（最终 GIVEUP 时人工能从 `LAST_ERROR` 看出原因）。`AppNotifyClient.post` — `channel/app/AppNotifyClient.java:114`；同款判据在 `F2fNotifyDeliverer.urlOf` — `service/F2fNotifyDeliverer.java:124`
- **出向信封三个固定值**：`charset` / `format` 与设备链路一致，`sign` 免签时为**空串**，`deviceId` 出向通知无设备概念、**旧实现固定空串**。`channel/app/AppNotifyProperties.java:80` / `:83` / `:96` / `:99`
- **三个通知 URL 与旧键的对应关系**（迁移时按这个对，别按名字猜）：出票成功 = 旧 `pay.center.notice-app-taketicketresult-url`、出票失败 = 旧 `pay.center.notice-app-taketicketfailureresult-url`、退款结果 = 旧 `pay.center.notice-app-refundresult-url`。`channel/app/AppNotifyProperties.java:25` / `:28` / `:31`
- **通知的读超时可以比设备链路宽松**（通知是异步任务）。`channel/app/AppNotifyProperties.java:105`
- **`AppNotifyResult` 的工厂方法叫 `ok` 而不是 `delivered`**：record 会为组件 `delivered` 自动生成同名访问器，静态方法重名会被编译器判为「记录中的存取方法无效」。`channel/app/AppNotifyResult.java:17`

### 十三、domain 层：状态机三件套与幂等判定（`domain/**`）

- **`F2F_ORDER.ORDER_STATUS` 的值域权威是 DDL 的 CHECK 约束、不是枚举**：`sql/f2f-schema.sql:39` 的 `CK_F2F_ORDER_STATUS` 列了 11 个取值，枚举逐字对齐。**加状态 MUST 同时改那条约束（需 migration 脚本），只改枚举会在写入时报 `ORA-02290`。** `domain/F2fOrderStatus.java:8`
- **11 个状态的语义与副作用列**：`CREATED` 已下单未发起支付 / `PAYING` 已发起支付结果未定 / `PAID`（`markPaid` 的唯一目标状态，副作用列 `PAID_TMS`）/ `FULFILLED`（副作用列 `FULFILL_TMS`）/ `FULFILL_FAILED`（后续通常进 `REFUNDING`）/ `TOPUP_SUSPECT`（充值可疑需人工，**当前无写入方**）/ `PAY_FAILED`（终态）/ `EXPIRED`（终态）/ `REFUNDING` / `REFUNDED`（终态）/ `CANCELED`（终态，**当前无写入方**）。`domain/F2fOrderStatus.java:37`~`:57`
- **迁移白名单逐条来自 2026-09-14 对 9 个服务全部 40 余个写入点的实测，不是设计稿**；五个终态（`PAY_FAILED` / `EXPIRED` / `REFUNDED` / `CANCELED` / `TOPUP_SUSPECT`）没有出边，`TOPUP_SUSPECT` 连入边也没有 —— **这是照实记录、不是遗漏，哪天补上写入方 MUST 同时在这里补入边**。`domain/F2fOrderStatus.java:60`
- **并发保证不在枚举里、在 CAS 的 WHERE**（`F2fOrderMapper.xml` 的 `updateStatus` / `markPaid` / `activateForDevice`）。`canTransitTo` **只做快速失败与错误提示，NEVER 当作并发保证，也 NEVER 在 CAS 前加它做前置校验** —— 调用方手里的「当前状态」来自更早一次 select、随时可能过期，用过期值提前拦只会误拦，且比 CAS 返 0 行更难排查（ADR-D40 裁决）。`domain/F2fOrderStatus.java:16` / `:114`
- **枚举放模块内而不是 `model/domain/` 是有意偏离规范**：`F2F_ORDER` 是 face-pay 独占表，塞进 `model` 会让 21 个模块共享一个只有一处使用的类、还要背上「给 `model` 加东西 MUST 重建链路上所有模块镜像」的代价；先例是规范自己点名的 `recon-server/.../ReconBatchStatus`（也在模块内）。`domain/F2fOrderStatus.java:23`
- **宽松解析：库里脏值 / 新加但代码未识别的取值一律返 `null`、NEVER 抛异常** —— 拿到 null 只说明「本代码不认识这个状态」，不代表数据非法。`F2fOrderRefundStatus.of` 同口径。`domain/F2fOrderStatus.java:97`、`domain/F2fOrderRefundStatus.java:33`
- **四个读取集合是拆分后的唯一定义处，此前散落五处两名**：`PENDING`（未支付可继续支付）原先在 TVM / 充值 / APP / BOM 四个服务各有一份、在 `F2fScanPayService` 还叫 `PAYABLE`；`REFUNDABLE`（已收钱可退）原先 APP 用枚举拼、页面退款与设备退款各写一份裸字面量；`FAILED_LIKE`（失败态）四个服务各一份、其中三份带裸字面量 `"EXPIRED"` / `"CANCELED"`；`PAID_ONLY`（已支付待履约）是出票 / 充值四个写入点的前置。`domain/F2fOrderStatus.java:78` / `:85` / `:91` / `:94`
- **`F2fOrderStatusTransition` 把 CAS 影响行数翻译成 `DONE` / `IDEMPOTENT` / `CONFLICT` 三态 + 回查到的原始状态**；此前 9 个服务各写 `if (updated == 0) log.warn(...)`，**「已经是目标态」与「状态被别人推走了」两种情况混在一个分支里** —— 前者是正常重复上报、后者是真冲突，需要不同处置。形态照抄 `pay-sign-server/.../SignStatusTransition`。`domain/F2fOrderStatusTransition.java:5`
- **该类的三条 NEVER**：① **NEVER 在这里加 CAS 前的白名单校验**（权威白名单是三条 CAS 自己的 WHERE）；② **NEVER 扩成通用状态机引擎** —— 三条 CAS 各带不同副作用列（`FULFILL_TMS` / `PAID_TMS` / 激活四列），归一成 `transit(key, from, to)` 会把这些列丢掉（规范里那段伪代码正是这么写的，**已否决**）；③ **NEVER 让 `CONFLICT` 一律变成对上游报错** —— 调用方是 TVM / BOM / APP 三种设备，各自 retCode 族与容错口径不同，典型处置是「按库内真实状态继续走完应答 + 打 WARN」，与 pay-sign 返 409 不同。`domain/F2fOrderStatusTransition.java:15`
- **`observedStatus` 只在 CAS 返 0 行时才有值、且原样保留库里的字符串不做归一**（脏值本身就是排查线索）；**`currentStatusLoader` 只在 `updatedRows == 0` 时被调用**，「CAS 命中就不回查」是规则的一部分（改成无条件回查等于给每次成功迁移加一次多余 DB 往返）。**解析不出来（NULL / 脏值 / 大小写不符）一律按 `CONFLICT`，NEVER 兜底成目标态** —— 把「不认识的状态」当成「已经到位」会把真冲突伪装成幂等，是最难查的一类。`domain/F2fOrderStatusTransition.java:43` / `:59` / `:75`
- **退款汇总状态三档的判据是金额比较**：`NONE`（未发生过成功退款，列默认值）/ `PARTIAL`（`REFUND_AMOUNT < ORDER_AMOUNT`）/ `SUCCESS`（`REFUND_AMOUNT >= ORDER_AMOUNT`），值域见 `CK_F2F_ORDER_REFUND_STATUS`；**与 `ORDER_STATUS` 正交，退款 NEVER 改主状态**。`domain/F2fOrderRefundStatus.java:26`~`:47`、`api/page/FacePayOrderPageVO.java:81`
- **`F2fDuplicateKey` 三种异常类型都要认**（`DuplicateKeyException` / `DataIntegrityViolationException` / 被剥到底层的 `SQLIntegrityConstraintViolationException`），沿 `getCause()` 链走；**自环 cause（`cur.getCause() == cur`）要单独挡一下，否则死循环**；返 false 表示**与幂等无关、MUST 原样上抛**。破例建类的理由：本模块有 **12 处、分散在 9 个类**，照抄就是 9 份逐字相同的私有方法；**本类只有这一个方法、NEVER 往里塞第二个用途**（那才是 AGENTS.md 禁止的 `XxxUtils` 杂物袋）。`domain/F2fDuplicateKey.java:8` / `:32`

### 十四、2026-09-16 拆分（P1~P3）后的新宿主类

> 这一批类是同一天从三个大类里拆出来的，注释统一声明「**只搬代码、不改任何行为**，URL / retCode 族 / 响应键集 / 落库内容一行未改」。**排查「某个逻辑现在在哪」MUST 以这一节为索引，NEVER 按旧类名 grep。**

- **`F2fTvmPayResultService`（从 616 行的 `F2fTvmOrderService` 拆出）= TVM / BOM 单程票的支付结果侧**：设备查询、支付中心回调、收银台反查订单详情。原类同时承担「下单」「查结果」「过期收口」三件事、依赖 9 个，拆开后本类只依赖 4 个。三条编排约束与原类一致：**整个类不带 `@Transactional`**、**对端没答上来时 NEVER 写 PAY_FAILED**、**状态判断用白名单**。`service/F2fTvmPayResultService.java:29`
- **`markPaymentSuccess` 是 public，只为给 `F2fOrderExpireService` 复用**：过期收口查到「其实已支付」时要做的落库与这里逐字相同，那 15 行含 `UK_F2F_PAY_SUCCESS` 幂等与 cause 链判定，**抄第二份必然漂移**。**NEVER 把它挪进 `F2fPayCenterFlow`** —— flow 的 `SettleSpec` 已把「收款后做什么」定义成调用方传入的 `Consumer`，挪进去等于让 flow 反过来知道 TVM 链路的落库细节。`service/F2fTvmPayResultService.java:278`、`service/F2fOrderExpireService.java:41`
- **IF8B-05 支付结果通知在「回调」与「过期收口」两支上的实现刻意不同、NEVER 互相替换**：回调支的 `tradeNo` 取 `request.getOrderNo()`（支付中心侧订单号，与 `markSuccess` 用的同一个值）、时刻复用同一个 `now`（与刚写进 `PAID_TMS` 的值完全一致）；过期收口支自己 `markPaid` + 按支付中心订单号入队，日志与取值都不同。`service/F2fTvmPayResultService.java:183`、`service/F2fOrderExpireService.java:99`
- **`F2fOrderExpireService`（从 `F2fTvmOrderService` 拆出 `reconcileExpiredOrder` / `loadExpiredCandidates` / `countStaleExpiredOrders` 三个方法）服务的不只是 TVM**：`selectExpiredCandidates` 按 `EXPIRE_TMS` 扫全表，TVM 拉码单（180 秒）、BOM 柜台单（30 分钟）、APP 取票单都在内 —— **这正是把它挪出来的判据**，留在原类会让「TVM 下单」看着像它的宿主域。唯一调用方是 `F2fOrderExpireJob`；**本类不带 `@Transactional` 且 MUST 保持如此**。`service/F2fOrderExpireService.java:14`
- **过期收口的放弃窗口用「时间窗」表达而不是重试计数**（`expireGiveUpHours`）：超过它仍未收口的订单不再被扫、不再外呼，只计数告警；与 `F2fRefundReconcileJob` 的 `RETRY_TIMES < 20` 是同一类闸门，只是这里没有重试计数列。**扫表下界就是这个窗口，NEVER 去掉。** `service/F2fOrderExpireService.java:50` / `:111` / `:122`
- **P3 起过期收口的查询与分支判定收口进 `F2fPayCenterFlow.settle`，两处「只存在于过期收口」的语义改由 `SettleSpec` 两个可选字段显式表达、NEVER 丢**：① `expireOnUnpaid` 把「业务码非 0」与「明确支付失败」都判成 `EXPIRED`（**设备查询链路对这两种情况分别回「支付中」与 `PAY_FAILED`，与这里刻意不同**，因此 **NEVER 给设备查询链路传非 null**）；② `onPaidNotify` 保住「先 `markPaid` 再按支付中心订单号入队 IF8B-05」的写法（**入参里的收口时刻 MUST 同时用于 `PAID_TMS` 与通知的 `payDate`，两处同一个值**）。`service/F2fOrderExpireService.java:68`、`service/F2fPayCenterFlow.java:256`
- **过期收口落库的两句「未支付」原因刻意不合并**：「支付中心根本没有这笔单」意味着预下单就没成功，「明确未支付/失败」意味着码发出去了没人付；2026-09-10 那次「订单每 30 秒外呼、持续 40 分钟没有出口」的缺陷就是靠前者定位的，**NEVER 合并成一句**。`service/F2fPayCenterFlow.java:287`
- **`F2fPayCenterFlow` 的两组判定结果都要求调用方穷尽 `switch`**：预下单侧 `Accepted`（流水置 `PROCESSING`、订单已尝试推 `PAYING`）/ `Rejected`（流水置 `FAILED`、订单按 `RejectTransition` 处理）/ **`PaidSync`（同步已收款，只有付款码会走到 —— 本类刻意什么都不写，支付流水各渠道列不同、由调用方写完再调 `markPaidAndReport`）**；查询侧四个取值互斥且穷尽，**不写 default 也能编译**。`service/F2fPayCenterFlow.java:120` / `:135` / `:155` / `:236`
- **`SettleSpec.onPaid` 与 `markPaid` 的先后顺序 MUST 保持**：先执行调用方传入的支付流水回写（各渠道列不同、故为策略回调），本类随后才做 `markPaid` + 冲突上报。`service/F2fPayCenterFlow.java:256`
- **IF8B-05 报文里四处待甲方确认的取值，全部写在同一处注释**：① `payResult` 规格有 `SUCCESS` / `FAIL` 两值，当前**只在支付成功处推**，要加 MUST 单独定触发点、**NEVER 在本方法里加参数分叉**；② 规格未写金额单位，按分处理、与同族 IF8B-04 的 `refundAmount` 对齐；③ **`voucher` 恒为空串** —— IF8B-05 在「支付成功时刻」触发，而取票凭证（票逻辑卡号）要等出票上报才存在，`F2F_ORDER` 上没有任何可用作 voucher 的列；规格原文对它的说明**自相矛盾**（既写「整段用于生成二维码」又写「预留字段」）且未给必填性；④ `orderType` 固定 `"0"` 全推（购票单与充值单都推），而**充值单在规格的 `orderType` 枚举（0 单程票 / 1 普通日票 / 2 全城通日票）里没有对应取值**。`service/F2fPayCenterFlow.java:433`~`:446`
- **`warnIfConflict` 刻意只告警、不改应答、不拒绝请求**：返 0 行的常见含义是并发回调已把订单推成 `PAID`，而调用方仍会按原口径对上游回失败 —— **这个分歧原本没有任何痕迹**。升级成「按库里真实状态应答」是**对外行为变更，MUST 先有 WARN 频次数据**（对齐过闸链路的观察期口径）。**同款实现有四处（TVM / BOM 扫码 / APP / 充值结果），改一处 MUST 看齐其余几处。** `service/F2fPayCenterFlow.java:461`、`service/F2fTvmOrderService.java:273`、`service/F2fTopupResultService.java:311`
- **`F2fOrderQueryService`（运营端只读查询）抽出的原因是 Controller 里原先直接注入 `F2fOrderMapper` 并拼 11 个入参、算 offset、判检索范围，违反「Controller 只做参数校验与路由」**；它返回 `PageOutcome` 而不是 `ResultVO`（响应壳属 Controller 职责），好处是调用方 `switch` 少写一个分支直接编译失败。**「检索范围必填」这条规则 MUST 留在本类**：`F2F_ORDER` 是按月分区的核心交易表，无条件全扫会直接影响设备链路；规则本身与旧服务逐字一致 —— 订单号 / 支付中心订单号 / 渠道订单号三者任一，或完整的下单时间范围。`service/F2fOrderQueryService.java:12` / `:63` / `:88`
- **11 个检索维度逐个保留、NEVER 精简**；其中 `payCenterChannelOrderNo`（渠道订单号）是 2026-09-11 新旧双打**补回来的一维** —— 旧 `/page/face-pay/orders` 一直支持它，重写后的新服务连入参都没有，运营后台按渠道订单号查不到单。**NEVER 再把它去掉。** `service/F2fOrderQueryService.java:42`
- **`F2fRefundRetryPolicy`（从 `F2fRefundService` 构造里的 5 个 `@Value` 收成一个对象）只是搬家，判定语义与配置键名一行未改**（仍是 `f2f.refund.*`、默认值同旧 `@Value` 冒号后缀，**已有的 K8s env 覆盖照样生效**）：间隔 `backoffBaseSeconds * 2^n` 封顶 `backoffMaxSeconds`（默认 3600 秒，即 5/10/20/40 分钟后转为每小时一次），放弃是「次数达 `maxQueryTimes`」或「`REQUEST_TMS` 距今超 `giveUpAfterHours`（默认 24 小时 = 一个支付中心对账周期）」**谁先到算谁**；按默认退避序列约 27 次才覆盖满 24 小时，因此**正常情况下时间窗先到、次数上限是兜底**（防退避参数被改小后查询次数失控）。移位次数上限 20 是为了避免 `long << n` 溢出成负数。`service/F2fRefundRetryPolicy.java:7`、`:33`~`:56`
- **该策略类的两条 NEVER**：① **NEVER 把放弃判定搬进扫表谓词** —— 写成 `WHERE RETRY_TIMES < N` 的话，次数一到那笔单直接从扫描结果里消失、状态停在 `INIT` / `PROCESSING`、无告警无人工入口（**原实现「固定 60 秒 × 20 次」就是这样在 20 分钟内静默丢单的**）；② **NEVER 把两个判据合并成一个 boolean** —— ERROR 日志要分别打印「次数用尽」与「超时间窗」，那是人工介入时判断「钱到底可能退没退」的依据。`requestTms` 为空时**只按次数判、不因缺时间戳就永不放弃**。`service/F2fRefundRetryPolicy.java:17`、`:85`
- **`F2fTopupResultService`（从 459 行 / 8 依赖的 `F2fTopupService` 拆出）= 充值结果侧**：IF2A-06 成功通知、IF2A-07 失败通知（含全额退款）、IF2A-09 BOM 合体通知。三条照搬口径：**充值失败必须退款**（来源 `TOPUP_FAIL`，幂等键「原订单号 + `#WHOLE#` + TOPUP_FAIL」）、**订单未支付时只留上报不推状态也不退款**（`reportOnly`）、**整个类不带 `@Transactional`**。`service/F2fTopupResultService.java:25`
- **BOM 合体通知修掉旧实现三处问题**：①查的是 TVM 充值订单表而不是 BOM 自己的表（表合一后不再有此问题）；②不检查是否已退款，**重复 `01` 通知会重复退款**；③`afterAmount` 直接复制 `transAmount`、**数据是错的**，这里不再写该列。另：旧 `BomOrderServiceImpl.notiTopupResult:698-861` 记完通知就按 `topupStatus=01` **无条件退款、连状态都直接改成 `'2'` 支付失败** —— 「收下上报」照搬，「没收到钱也退款」**NEVER 照搬**。`service/F2fTopupResultService.java:166`
- **旧 BOM 那个方法的 `@Transactional` 已于 2026-09-14 摘除**（它把支付中心退款调用包在事务里，违反 §5.2），本类本来就不带事务 —— **NEVER 因为「旧的有事务」而给本类加 `@Transactional`**。`service/F2fTopupResultService.java:183`
- **充值失败退款的两份逐字相同私有方法（`submitRefund` / `submitTopupRefund`，唯一差别是 `deviceId` 取法）已合并成一个、`deviceId` 提成参数，落库内容与日志逐字不变；NEVER 拆回两份。** `service/F2fTopupResultService.java:223`
- **「退款提交失败不影响本接口回成功」的理由**：上报已落库、退款单也已落库（`INIT`），`F2fRefundReconcileJob` 会重试；回失败只会让设备无意义重推。**旧实现靠 `doRefund` 的返回值决定是否记退款单号，而那个方法恒返回 true（`TvmCommonServiceImpl`），等于没有判断。** `service/F2fTopupResultService.java:122`
- **`F2fAppRefundService`（从 614 行的 `F2fAppOrderService` 拆出）修掉三处旧缺陷**：①退款结果查询不再把订单号当退款单号用（旧变量名 `refundRrderNo` 值是 `orderNo`，却去 `selectByRefundNo` 查，只有两个号恰好相同才查得到）；②退款通知改成落库 + 定时投递（旧实现在业务线程池里同步 push、失败才落库）；③退款受理不再无条件返回成功（旧 `doRefund` 整段 catch 后回 `9999 请求异常`，而退款单可能已落库、支付中心可能已受理，APP 侧无法区分）。**整个类不带 `@Transactional`。** `service/F2fAppRefundService.java:25`
- **APP 退款响应里 `refundDate` 是 8 位日期（`yyyyMMdd`）**：2026-09-11 实测旧服务回 `20260911`、新服务曾回 14 位 `20260911204733`，已改回；`requestRefund` 与 `queryRefundResult` 两处同宽度、**NEVER 改成 14 位**。`service/F2fAppRefundService.java:55`
- **APP 退款响应回吐的 `notifyUrl` 取 `f2f.notify.app.refund-notice-url`（旧键 `pay.center.app-refund-notice-url`，集群 env 实测指向 fep-app 的 `/ci/app/receiveRefundResult`）**；**NEVER 复用 `f2f.notify.app.refund-result-url`** —— 那是 `F2fNotifyJob` 出向推送退款结果的地址（旧键 `pay.center.notice-app-refundresult-url`），两个键在旧实现里是不同的值，合并会改变响应内容。「把内部配置回显给 APP」看着奇怪但是既有契约。`service/F2fAppRefundService.java:89`
- **状态不允许退款时回 `0000` + `refundResult=FAIL`，不是 `9999`**（旧 `AppOrderServiceImpl.requestRefundTicket:517` 只校验订单存在、**完全不看状态**，把未支付单也送去支付中心，被拒后照样回 `0000` 带 `refundResult=FAIL`，2026-09-11 双打实测）。**响应形态照搬旧实现，但 NEVER 照搬「未支付也真去发起退款」** —— 那会在 `F2F_REFUND` 里留下必然失败的 `INIT` 记录，`F2fRefundReconcileJob` 无限重试也收不了口。`service/F2fAppRefundService.java:89`
- **退款结果查询「先按退款单号、未命中再按原订单号」两条都要留、NEVER 只保一条**：入参字段名叫 `orderNo` 但旧实现拿它当**退款单号**用；2026-09-11 双打实测两侧当时**完全互斥**；而 `requestRefund` 的响应体**并不回退款单号**，APP 只能从 `receiveRefundResult` 回调的 `refundNo` 里拿，**因此现网 APP 传的一定是退款单号，只按订单号查等于把这个接口对现网打死**。响应里的 `orderNo` 回**请求传入的原值**（传退款单号就回退款单号）。一笔订单可能有多张退款单，按订单号命中时取最近一张。`INIT` / `PROCESSING` 时**不再同步问支付中心**（扫表已在收口，再发网络调用只会让 APP 响应时间随支付中心抖动、且两处并发更新同一行）。`service/F2fAppRefundService.java:155`
- **退款回调允许把 `MANUAL` 救回**：转人工只表示我方放弃了自动收口，回调带来的是支付中心的权威结论，此时 MUST 接受并推进到 `SUCCESS` / `FAILED`；因此前置白名单里带 `MANUAL`、而终态短路只认 `SUCCESS` / `FAILED`。`service/F2fAppRefundService.java:207`
- **拆分时刻意保留的同形副本（`payCenterOrderNoOf`，6 行）**：`F2fAppOrderService` 里有一份逐字相同的，给 `queryPayResult` 回吐 `tradeNo` 用。**不抽公共工具类**是遵循 §5.1 与本项目「宁可留同形副本也不新建工具类」的惯例（`warnIfConflict` 三处并存即先例），但 **改这里 MUST 同步看齐那一份**。`service/F2fAppRefundService.java:277`

### 十五、退款 / 出票 / 扫码付 / 对账导出 / 订单号补遗

- **退款来源（`REFUND_SOURCE`）7 个取值各自的触发方**：`TAKE_TICKET_FAIL` 出票故障自动退（差额退）/ `BOM_ORIGINAL` BOM 单程票原路退 / `APP_REQUEST` APP 用户主动退 / `DAILY_BATCH` 每日批量退未取票 / `TOPUP_FAIL` 充值写卡失败退 / `PAGE_MANUAL` 运营端手工退 / `TVM_REQUEST` 设备侧 `requestRefund` 发起。`service/F2fRefundService.java:65`~`:83`（DDL 侧同一份枚举在 `sql/f2f-schema.sql:234`）
- **`requestRefund` 的返回值 MUST 检查**：`RefundOutcome.alreadyExisted()` 为 true 表示这笔「原订单 + 票 + 来源」已经退过，**调用方 NEVER 再重复触发下游动作**（如再改一次票状态）；`rejected(...)` 表示参数非法、**连退款单都没落库**。`service/F2fRefundService.java:140`、`service/RefundOutcome.java:21` / `:26`
- **送支付中心那步三个分支：受理成功置 `PROCESSING`、对端未答或业务失败一律留在 `INIT` 并累加重试次数 —— NEVER 在这里置 `FAILED`。** `service/F2fRefundService.java:180`
- **收口任务只发 `refundQuery`、NEVER 重发退款**（重发是资金动作，必须另有严格限次的入口）；`INIT` 的单也要查 —— **「对端未答」不等于「没受理」，请求可能已经到达**。查不到明确结果按指数退避排下一轮；一旦超时间窗置 `MANUAL` 并打 ERROR，**NEVER 靠次数用尽让它静默掉出扫描范围**。`service/F2fRefundService.java:207` / `:287`
- **退款汇总三列是重算式、不是累加式**：`updateRefundSummary` 直接从 `F2F_REFUND` 里 `REFUND_STATUS='SUCCESS'` 的行 `SUM` 出金额，因此执行 N 次结果相同、天然幂等，**不需要 CAS 也不需要前置状态白名单**。累加写法（`REFUND_AMOUNT = REFUND_AMOUNT + ?`）在并发或补跑下会算出偏小 / 虚高的值，**虚高之后真实退款会被误判成超额**（那个坑记在 `PayTxnDetailMapper.xml` 的注释里），**NEVER 改回累加**。**NEVER 动 `ORDER_STATUS`**（ADR-D88）；**返回值只进日志、NEVER 拿它判断退款成败** —— 0 行只说明订单号对不上，而退款单的终态已经落库了。`service/F2fRefundService.java:263`、`:109`
- **`FAIL_REASON` / `LAST_ERROR` 列宽都是 512、超长截断由应用负责**（mapper 文档已注明）。`service/F2fRefundService.java:349`、`service/F2fNotifyService.java:172`
- **出票上报的四条设计要点**（类注释）：① 幂等靠 `UK_F2F_REPORT_IDEM (REPORT_TYPE, ORDER_NO)` —— 规格要求设备断网后重传，**旧实现无任何判重、重传就重复插主票 + 子票**；② 接收与后续动作解耦，**NEVER 在设备请求线程里做 HTTP 推送**；③ **差额退款金额算不出来就不退** —— 旧实现在 `transType` 非 01/03 时 `ticketPrice` 为空串、`new BigDecimal("")` 直接抛异常，**更糟的是实际张数大于购买张数时算出负数仍会发起退款**；④ **只有 `PAID` 的订单能记出票结果** —— 未支付订单出票属于对账事故，必须拒绝而不是默默记账（旧实现不校验）。`service/F2fTicketIssueService.java:29`
- **落票 MUST 在落上报之前、NEVER 调换**：上报行是幂等闸门且单条自动提交；若先插上报后插票，插票一旦失败（2026-09-10 双跑重放实测踩到 `ORA-01400`），上报行已提交、设备重传被幂等挡住直接回 `0000`，**票永久丢失且设备侧看到的是成功**。反过来插票在前时，插票失败会抛到全局异常处理器、上报行不落库，设备重传能完整重跑一遍（票本身靠 `UK_F2F_TICKET_LOGIC` 幂等）。顺序固定「**落票 → 落上报（幂等锚点）** → 推进订单 → 退款 → 入队通知」。`service/F2fTicketIssueService.java:53`
- **批量插票撞唯一索引时降级为逐张插**：说明这批票里有已落库的，**批量整批回滚会让本来能落的票也丢掉**。`service/F2fTicketIssueService.java:242`
- **`CREATE_TMS` / `UPDATE_TMS` 是 NOT NULL 且表上没有默认值，漏赋值抛 `ORA-01400`、整条出票上报退化成 UUID retCode**（2026-09-10 双跑重放实测踩到）；**NEVER 依赖数据库默认值**。`service/F2fTicketIssueService.java:268`
- **两个上报接口的响应恒为 `0000`（除参数与订单不存在）；出票失败上报的退款成功与否不影响应答**（既有契约）。差额退款拒绝三种情况并只记日志：单价缺失、应退张数非正、原支付流水查不到支付中心订单号。`service/F2fTicketIssueService.java:107` / `:143` / `:183`
- **上报行落库时 `PROCESSED='0'`，让后续动作扫表任务能捞到**。`service/F2fTicketIssueService.java:348`
- **出票上报补偿的静默期 `staleSeconds` MUST 大于一次出票上报请求的最长耗时**，否则会与首报并发。`service/F2fReportRecovery.java:77`
- **付款码支付两条 NEVER**：查不到结论时 **NEVER 回 `FAILED`** —— 付款码已经扫过、钱可能已扣，回失败会让 BOM 重新收款；**只有两个状态允许发起扣款，其余一律短路（白名单，NEVER 改成黑名单）**。`attemptNo` 递增而不是固定 1（第一次码过期、第二次换码可重试）；收口时要落在**最近一次**支付尝试上、查不到按 1 处理。`service/F2fScanPayService.java:64` / `:118` / `:152` / `:202` / `:207` / `:239`
- **「被拒」与「返回支付失败」对设备是同一句话，差别只在日志（已在 flow 里分开打）** —— 排查时 MUST 看服务端日志而不是设备侧文案。`service/F2fScanPayService.java:149`
- **TVM 拉码 / 充值拉码「同步已收款」分支构造上不可能收到**（这两条链路没开同步支付状态判定）；**真收到说明 `spec` 被改错了，宁可炸也不能静默按失败回**。`service/F2fTvmOrderService.java:250`、`service/F2fTopupService.java:135`、`service/F2fAppOrderService.java:197`
- **`ITP.PAY` 每行段数固定 21（前 5 段键 + 后 16 段度量），五组度量的下标写死在常量里**（BOM/TVM 发售笔数 idx 5、充值 idx 7、APP 购票 idx 13、BOM 行政处理 idx 15、BOM 处理 idx 19，金额紧随其后）。**来自甲方规格、NEVER 调整。** `service/ReconExportService.java:62`~`:80`
- **`ITP.BUS` 只有 4 段** = 1 段键（日期）+ 3 段度量，行格式 `日期|对账金额|付款金额|优惠金额`；**优惠金额恒 0** —— `F2F_ORDER` / `F2F_PAYMENT` 都没有优惠列，**NEVER 拿 `ORDER_AMOUNT` 与 `PAY_AMOUNT` 之差去推算**（那个差额可能来自部分退款、聚合码场景或脏数据）。同一天出现多行是**允许的**，recon-server 按 1 段键二次聚合、三个度量逐列累加。`service/ReconExportService.java:292`
- **两个漏账探针只记日志、不影响抽取结果，NEVER 删、也 NEVER 改成抛异常中断抽取**：五条汇总 select 的 `BIZ_TYPE` 是白名单、窗口列是 `O.PAID_TMS`，因此有两类「有钱但不进任何一段度量」的行会被**静默丢掉**（纯文本对账文件没有 schema，下游读不出异常）。**漏几笔要人工核，但整份文件不出更严重。** `service/ReconExportService.java:174`
- **抽取线程数默认 1 即串行、刻意不放大**：抽取是重 IO 的区间扫描，并发只会互相抢 Oracle 的 IO 与 Druid 连接、反而拖慢联机链路。`service/ReconExportService.java:92`、`application.yml:131`
- **探针 `COUNT(*)` 理论上不会返 null 仍兜一层，NEVER 直接拆箱**。`service/ReconExportService.java:347`
- **订单号业务码只有三个：`00` 单程票（旧 `ProductType.ordinaryTicket`）、`08` 票卡充值（旧 `ProductType.tvmTopup`，旧号段 08）、`09` 退款单**；序列段 4 位、与 `F2F_ORDER_NO_SEQ` 的 `MAXVALUE 9999 CYCLE` 对应、**定长由数据库保证**，超出取模兜底。拼装是纯函数、时间由调用方传入便于单测。`support/F2fOrderNo.java:33` / `:36` / `:47` / `:49` / `:59`
- **`F2fOrderNoGenerator`：NEVER 在事务里调用本类之后又做 RPC** —— 取号是一条独立 SQL，下单链路的正确顺序是「取号 → INSERT 订单 → 事务外调支付中心」。`support/F2fOrderNoGenerator.java:8`
- **`F2fChannel` 只映射三个渠道（`01` APP / `02` TVM / `03` BOM）**，`channelOf` 在 `providerId` 为空时返 `null`、**调用方按非法参数拒绝**；设备侧 `providerId` 与本表 `CHANNEL` **恰好同码但语义不同**，抽成一个方法是为了「哪天不再同码时只改这一处」。`support/F2fChannel.java:16`~`:25`
- **BOM 售票单失效时长 30 分钟是折中值**：不能复用 `qrcodeExpireSeconds`（180 秒，BOM 售票没有二维码、中间是人工操作），但**也 NEVER 留 null** —— APP 单已经踩过：`EXPIRE_TMS` 为空的订单永远不被 `F2fOrderExpireJob` 扫到、未付款单会永久停在 `CREATED`。**两个渠道的 `expireSeconds` 都 MUST 有值。** `service/F2fTvmOrderService.java:94` / `:208`
- **补款单（`SUPPLEMENT_ORDER`）的三条口径**：单号形态 `SP + yyyyMMddHHmmssSSS + 卡号后 6 位`；`PAY_CHANNEL` 由 PayCenter 预下单时传入、**下单时为空**；预下单返回的支付串**原样回给 APP 唤起收银台、本端不解析**。捞超时未支付**仅限 `INIT`** —— `PROCESSING` 已在 PayCenter 挂待支付单、**不能单方面关单**；超时关单白名单也只放 `INIT`。`entity/SupplementOrder.java:23` / `:43` / `:47`、`mapper/SupplementOrderMapper.java:58` / `:62`
- **补款单必须自己实现 IF8A-11**：APP 拿到 IF8A-26 的 `orderNo` 后仍会调 `/ci/app/requestPaymentInfo`，而那条链路只查 `F2F_ORDER`、补款单落在 `SUPPLEMENT_ORDER` ⇒ **恒返 `9999 订单号错误`**（2026-09-16 实测，APP 侧表现为「生成订单失败」）；应答形态与取票单逐字一致，**APP 侧无需改动**。`service/supplement/SupplementOrderService.java:18`
- **补款专用的支付回调地址不能共用 `payNoticeUrl`**：那个键的实际值是 TVM 端点（集群 env `PAY_CENTER_PAY_NOTICE_URL` 指向 `/itptvm/ci/tvm/payNotice`），而该端点只查 `F2F_ORDER` ⇒ 回调恒返 `2001 订单不存在`、对端反复重推，支付成功只能靠 5 分钟一轮的收敛任务兜住（2026-09-16 实测延迟约 3 分钟，ADR-D103）。**留空即回落到 `payNoticeUrl`**（env 没注入时行为与改动前完全一致），**NEVER 让它送出空串** —— 网关文档该字段是「否 / 不传取默认」，送空串的行为未实测。`channel/paycenter/PayCenterProperties.java:56`
- **`APP` 侧 IF8A-11 被拒时 `rejectTransition` 传 null，订单 MUST 留在 `CREATED`**（乘客可换支付通道重来）；**这与 TVM / BOM 一次被拒即置 `PAY_FAILED` 是有意的差别**，重构前它只体现为「这里少两行」。`service/F2fAppOrderService.java:185`
- **IF8A-18 两个时间格式 NEVER 合并**：「DB 已是终态」分支原样返回带分隔符的 `yyyy-MM-dd HH:mm:ss`（2026-09-11 抽查库内四条历史真实单为该形态），只有「向支付中心查到结果」那条分支才用紧凑 14 位；双打实测同一笔已付单旧回 `2026-09-11 20:46:50`、新曾回 `20260911204649`。**无值时返 null 而不是空串。** `service/F2fAppOrderService.java:88` / `:101`
- **IF8A-18 问不到结果时回 `8999 支付中`（不是 FAIL）**，`payResult` 只有 `SUCCESS` / `FAIL` 两值。`service/F2fAppOrderService.java:202` / `:244`
- **激活订单列表里 `ticketPrice` 必须序列化成字符串**：旧载体 `AppActiveOrderModel` 该字段声明为 `String`，APP 侧一直收到 `"200"` 而不是 `200`（2026-09-11 双跑对比实测到类型漂移）；`singelTicketNum` 拼写同样是既有契约。`service/F2fAppOrderService.java:261`
- **BOM 下单只 INSERT、不先查后插**：并发下先查再插无效，重复请求由 `UK_F2F_ORDER_DEV_SEQ` 挡住后**回查已有订单幂等返回**。`service/F2fBomOrderService.java:118`
- **BOM 业务结果上报只在已收款后才有意义**，不在白名单内的一律 `report-only`。`service/F2fBomOrderService.java:73`
- **渠道码要等支付回调或查询结果才有值，可能为 null —— 旧实现同样回 null、不兜底填值**（三处同款注释：BOM / 取票鉴权 / TVM 支付结果）。`service/F2fBomOrderService.java:390`、`service/F2fTakeTicketService.java:119`、`service/F2fTvmPayResultService.java:314`
- **取票鉴权「查不到」与「查到但未激活」都回 `2003`，两支响应体都与成功响应同形（8 键齐全、值 JSON null）**；**NEVER 回退成旧 collect-pay 的 `failData(空订单)`** —— 那是 `2999` 码，且票数会变成字符串 `"null"`、设备侧可能误出 1 张票。`service/F2fTakeTicketService.java:91`
- **HCE 链路复用 BOM 业务结果通道做审计上报**（票卡更新审计）。`service/F2fHceService.java:49`

### 十六、调度任务（7 个 `@Scheduled` 的实测清单）

> 逐个类实测（2026-09-16）。**这 7 个都没有分布式锁，本模块 MUST 单副本**；`F2fOrderExpireJob` 的类注释里写着「多副本会重复扫同一批」的说明。

| 任务类 | 方法 | 触发配置（默认值） | 幂等前提 |
|---|---|---|---|
| `scheduler/F2fNotifyJob.java:43` | 通知投递（全类型，含 `PAY_RESULT`） | `fixedDelay = f2f.notify.scanIntervalMs`（30000） | 按 `NEXT_RETRY_TMS` 到点扫、`markSent` 返 false 即别的副本先做完 |
| `scheduler/F2fRefundReconcileJob.java:55` | 退款回查收口 | `fixedDelay = f2f.refund.scanIntervalMs`（60000） | 只发 `refundQuery`、终态短路 + 退避 + `MANUAL` 闸门 |
| `scheduler/F2fOrderExpireJob.java:54` | 过期订单收口 | `fixedDelay = f2f.order.expireScanIntervalMs`（30000） | 先问支付中心再置 `EXPIRED`；扫表下界 = 放弃窗口 |
| `scheduler/F2fDeviceOfflineJob.java:38` | 设备离线判定 | `fixedDelay = f2f.device.offlineScanIntervalMs`（60000） | `WHERE ONLINE_FLAG='1'`，重复执行不会重复改 |
| `scheduler/F2fReportRecoveryJob.java:42` | 出票上报后续动作补偿 | `fixedDelay = f2f.report.scanIntervalMs`（120000） | 只捞 `PROCESSED='0'` 且过静默期的行 |
| `scheduler/SupplementOrderCloseProcessor.java:20` | 补款成功后收敛原过闸订单 | `cron = supplement.scheduler.converge-cron`（`0 */5 * * * ?`） | 明细 `SETTLED` 幂等 + RPC 收敛 |
| `scheduler/SupplementOrderCloseProcessor.java:32` | 补款超时关单 | `cron = supplement.scheduler.close-cron`（`0 */10 * * * ?`） | 白名单只放 `INIT` |

- **`F2fNotifyJob` 里的重推方法 `sendNoticeAppTakeTicketRecord` 没有任何 `@Scheduled` 绑定**，要靠外部 web-server 的 Quartz 去调 —— **排查「重投为什么没跑」MUST 查 `SYS_JOB_LOG`，NEVER 在本模块找 cron**。`scheduler/F2fNotifyJob.java:18`、`service/F2fNotifyService.java:22`
- **`F2fNotifyDeliverer` 单独存在的理由**：`@Scheduled` 方法是给调度器的入口，HTTP 触发方（`/pay/noticeAppTask/**`）不该直接调 Job；**扫表本身失败（DB 不可用等）时返回空统计并记 ERROR、不抛出** —— 调度器与 HTTP 触发方都不该因为一次扫表失败而中断。传未知 `notifyType` 时扫不到行、返 `due=0`、**不报错**。`service/F2fNotifyDeliverer.java:17` / `:62` / `:79`
- **通知类型四个取值 `TAKE_TICKET_OK` / `TAKE_TICKET_FAIL` / `REFUND_RESULT` / `PAY_RESULT`（`CK_F2F_NOTIFY_TYPE`），目标只有 APP 一个（`CK_F2F_NOTIFY_TARGET` 也只允许这一个值）**；旧模块按业务类型分三个 URL（对应三张 `TBL_NOTICE_APP_*` 表），本模块表已合一但**触发粒度 MUST 保持不变**。`service/F2fNotifyService.java:37`~`:49`、`:129`
- **设备离线判定窗口由调用方按「心跳周期 × 容忍倍数」算好后传入，SQL 内不做时间计算**；`GIVEUP` 表示超过最大重试次数放弃、**需人工介入、不再扫表**。`service/F2fDeviceHeartbeatService.java:59`、`entity/F2fNotifyTask.java:45`
- **过期收口任务里「已过放弃窗口」的告警方法自身异常吞掉 —— 告警失败 NEVER 影响收口主流程。** `scheduler/F2fOrderExpireJob.java:87`

### 十七、entity 与表列语义补遗（`entity/**`、`api/page`）

- **`F2fOrder` 与旧实体的两处刻意差异**：金额用 `Long`（单位分，新表列 `NUMBER(12)`，**单位已由用户 2026-09-08 确认为分**），时间用 `LocalDateTime`（新表列 `TIMESTAMP(6)`）—— 旧表两者都是 `String`。**改字段 MUST 同步改 DDL 与 `docs/architecture/face-pay-refactor.md` §14.1 的映射表。** `entity/F2fOrder.java:5`
- **`F2F_ORDER` 关键列语义**：`BIZ_TYPE` = `01` 购票 / `02` 充值 / `03` 取票 / `04` 非现金收款；`ORDER_STATUS` 是**内部状态机、不等于对外 `paymentResult`**；`REFUND_AMOUNT` 由 `F2F_REFUND` 中 SUCCESS 的行**重算得出、NEVER 累加**；`DEVICE_TXN_SEQ` 只有 BOM 传（`bomOptSeq`）、TVM / APP 不传；`THIRD_USER_ID` 只有 APP 侧有值；`OPERATOR_ID` / `SHIFT_NO` BOM 侧有值；`ACTIVATE_DEVICE_ID` **非空即锁定，其他设备查询返 `2008`**；`CREATE_TMS` 是**分区键**；`REMARK` 承载旧表 `MSG` 列的内容。`entity/F2fOrder.java:26`~`:135`
- **`F2F_PAYMENT` 三条唯一性约束决定访问方式**：`UK_F2F_PAY_ATTEMPT (ORDER_NO, ATTEMPT_NO)` 同一订单内尝试序号唯一、从 1 递增；**`UK_F2F_PAY_SUCCESS` 是函数索引 `CASE WHEN PAY_STATUS='SUCCESS' THEN ORDER_NO ELSE NULL END`，即一笔订单最多一条 SUCCESS 记录 —— 标记成功靠该索引兜底，NEVER 先查有没有成功记录再更新（并发下无效）**；`IDX_F2F_PAY_CENTER_NO` 与 `IDX_F2F_PAY_ORDER (ORDER_NO, ATTEMPT_NO DESC)` 支撑回调查询与「最后一次尝试」查询。`entity/F2fPayment.java:5`
- **`F2F_PAYMENT.PAY_STATUS` 五个取值 `INIT / PROCESSING / SUCCESS / FAILED / UNKNOWN`，其中 `UNKNOWN` 表示对端未明确应答、需靠查询接口收口、不得直接判失败**；`PAY_SCENE` 三值 `qrcode` / `scan` / `app`；`PAY_TYPE=0` 表示本地拼聚合码 URL 不调支付中心；`PAY_CODE` 仅 `scan` 场景有值；`QR_CONTENT` 是**返给设备显示为二维码的完整字符串、设备不解析**；`COST_MS` 用于**排查虚拟线程 pin 与超时**；`CREATE_TMS` 是分区键。`entity/F2fPayment.java:32`~`:92`
- **`F2F_REFUND` 列语义**：`TICKET_LOGIC_NUM` 按票退时填、**整单退时为空，唯一索引用 `NVL` 占位避免多笔整单退绕过约束**；`REFUND_TICKET_NUM` 按票退为 1、出票故障场景为未出票张数；`REFUND_AMOUNT` 出票故障场景 = （订单张数 − 实际张数）× 单张票价；`ORIG_TRANS_DATE` 是 `YYYYMMDDHHMMSS`、**BOM 按「逻辑号 + 该字段」定位原票**；`REQUEST_TMS` 用于算「已经悬了多久」；`FINISH_TMS` 进 `SUCCESS` / `FAILED` / `MANUAL` 终态时回填。`entity/F2fRefund.java:34`~`:95`
- **`F2F_TICKET` 三处映射口径**：`TRANS_DATE` 是 `VARCHAR2(14)`（`yyyyMMddHHmmss`）**映射成 String、不是时间类型**（BOM 退款按「逻辑卡号 + 此字段」定位，**格式不得改写**）；金额用 `Long`（分）不退化成字符串；时间戳列用 `LocalDateTime`。出票上报按张写入、一次上报可能多张，**重复上报靠 `UK_F2F_TICKET_LOGIC (TICKET_LOGIC_NUM, TRANS_DATE)` 抛 `DuplicateKeyException` 兜底、NEVER 先查后插**。票状态四值 `ISSUED / FAULT / REFUNDING / REFUNDED`（`CK_F2F_TICKET_STATUS`）；`TICKET_LOGIC_NUM` **下单时未知、出票上报才有**。`entity/F2fTicket.java:5`、`:33`、`:42`
- **`F2F_RESULT_REPORT` 列语义**：`ORDER_NO` 一般是 ITP 订单号，**但 `ERROR_CODE=2101` 时规格要求填取票二维码的 `randomFact`，故不建外键**；`TOPUP_STATUS` = `00` 成功 / `01` 失败 / `02` 存疑 / `03` 取消；`ERROR_CODE=2101` 表示取票二维码超时需解锁订单；`OCCUR_TMS` 在 `2101` 时是二维码生成时间；`RECEIVE_TMS` 与 `PROCESSED` 组成扫表索引；`PROCESSED` = `0` 未处理 / `1` 已处理，**后续动作（退款、状态推进）由扫表驱动、与接收解耦**；`RAW_BODY` 留证是因为**规格允许出现接口未声明的字段**；`CREATE_TMS` 是分区键。`entity/F2fResultReport.java:32`~`:89`
- **`F2F_DEVICE_STATUS` 是复合主键 `(CHANNEL, DEVICE_ID)`**，`IDX_F2F_DEVICE_HB` 建在 `LAST_HEARTBEAT_TMS` 上支撑超时扫表；`HEARTBEAT_COUNT` 每次 MERGE 命中已有行自增 1；`ONLINE_FLAG` = `1` 在线 / `0` 离线、**由扫表按超时判定**。`entity/F2fDeviceStatus.java:21`~`:42`
- **`F2F_NOTIFY_TASK` 的幂等键是 `(NOTIFY_TYPE, ORDER_NO, REFUND_NO)`，退款类才有 `REFUND_NO`、非退款类在幂等键内用 `NVL` 占位为 `'#NONE#'`**；`PAYLOAD` **落库后原样投递、重试不再重新组装**；`NEXT_RETRY_TMS` 由应用算好后写入、扫表只按此字段取；`MAX_RETRY` DDL 默认 5、达到即 `GIVEUP`。`entity/F2fNotifyTask.java:30`~`:60`
- **`GATE_TXN_PAY`（本模块只读 / 收敛用）**：`DEBIT_STATUS` 六值 `INIT / PROCESSING / SUCCESS / RETRY / FAIL / CLOSED`；**交易日期列是 `VARCHAR2(16)` 存 `yyyyMMdd`、不是 DATE**；实付金额单位分。`entity/GateTxnPay.java:11`~`:18`；`SupplementPayCenterFlow` 侧「原过闸订单扣费终态取值与 gate-txn-pay 的 `DEBIT_STATUS` 口径一致」— `service/supplement/SupplementPayCenterFlow.java:34`
- **运营端列表 VO 的三个「取自最后一次支付尝试」字段**（支付中心订单号 / 渠道订单号 / 支付渠道编码，**BOM 柜台售票无支付方式值**）、**最近一笔退款单号取最新 ID**（整单退与部分退各可能有多行）、起终点站中文名按站码关联 `STATION_INFO`、三个时间列格式统一 `YYYY-MM-DD HH24:MI:SS`。`api/page/FacePayOrderPageVO.java:39`~`:96`
- **运营端部分退款入参：金额 MUST 大于 0 且不大于剩余可退金额**；`operatorId` 落 `F2F_REFUND.OPERATOR_ID` 供审计追溯（**旧实现没有这个字段**）；退款原因为空时落 `运营人工退款`。`api/page/AppPartialRefundRequest.java:17`、`api/page/FacePayRefundRequest.java:15` / `:18`

### 十八、mapper 层补遗（Java 接口 + XML）

- **「不做判重、撞唯一索引由 application 层兜」这条在 6 个 mapper 的方法注释里逐个写明**，**NEVER 改成先 SELECT 再 INSERT**：`F2fOrderMapper.insert`（`UK_F2F_ORDER_NO`）、`F2fPaymentMapper.insert`（`UK_F2F_PAY_ATTEMPT`，`ATTEMPT_NO` 由调用方基于 `selectMaxAttemptNo` 加 1）、`F2fRefundMapper.insert`（`UK_F2F_REFUND_IDEM` 与 `UK_F2F_REFUND_NO` 两条都会抛同一异常）、`F2fTicketMapper.insert` / `batchInsert`、`F2fResultReportMapper.insert`（`UK_F2F_REPORT_IDEM`）、`F2fNotifyTaskMapper.insert`（`UK_F2F_NOTIFY_IDEM`）。`mapper/F2fOrderMapper.java:29`、`mapper/F2fPaymentMapper.java:36`、`mapper/F2fRefundMapper.java:37`、`mapper/F2fTicketMapper.java:31`、`mapper/F2fResultReportMapper.java:28`、`mapper/F2fNotifyTaskMapper.java:33`
- **`batchInsert` 生成的是 Oracle `INSERT ALL ... SELECT * FROM DUAL`，NEVER 传空集合（空集合会生成非法 SQL）**。`mapper/F2fTicketMapper.java:39`
- **`selectRecentByLogicNum` 的 `ORDER BY TRANS_DATE DESC + FETCH FIRST 1 ROWS ONLY` NEVER 去掉** —— 多笔命中会抛 `TooManyResultsException`；该方法用于「设备只传逻辑卡号、未传交易日期」时的退款定位，命中 `IDX_F2F_TICKET_RECENT`。`mapper/F2fTicketMapper.java:65`
- **两个状态推进方法的 `fromStatuses` NEVER 传空集合**，返 0 行表示前置状态不满足、**调用方 MUST 据此拒绝**（订单侧 `F2fOrderMapper.updateStatus` / `markPaid`，票侧 `F2fTicketMapper.updateStatus`）。`mapper/F2fOrderMapper.java:83` / `:94`、`mapper/F2fTicketMapper.java:76`
- **退款扫表 SQL 的三个约束**：`WHERE` 用 `REFUND_STATUS + NEXT_QUERY_TMS` 命中 `IDX_F2F_REFUND_SCAN`；**显式 `ORDER BY NEXT_QUERY_TMS` 是 `FETCH FIRST` 的前提**（到点早的先查）；`statuses` 通常是 `INIT` / `PROCESSING`、**NEVER 传空集合**；只捞 `NEXT_QUERY_TMS` 不晚于此刻的单（**退避未到点的单本轮不动**）。`mapper/F2fRefundMapper.java:87`
- **设备离线扫表同款形态**：条件 `LAST_HEARTBEAT_TMS < deadline AND ONLINE_FLAG='1'`（已置离线的行不会重复返回），`deadline` 由调用方算出、**语句内不内置时间窗**，配显式 `ORDER BY LAST_HEARTBEAT_TMS` + limit；置离线**仅当当前为在线才更新、靠影响行数识别是否由本次调用改写**。`mapper/F2fDeviceStatusMapper.java:55` / `:68`
- **「每日批量退款未取票交易」的扫表是新增能力**（规格 §7.5，**旧实现无此任务，缺口 E3**），`deadline` 由调用方按业务规则算出、语句内不内置时间窗。`mapper/F2fOrderMapper.java:139`、`F2fOrderMapper.xml:260`
- **BOM 幂等回查那条 select 的三列与 `UK_F2F_ORDER_DEV_SEQ` 一致**（撞键后按同样三列回查已有订单）。`F2fOrderMapper.xml:138`
- **回调 / 反查各自命中的索引在 XML 注释里点名**：退款单号 → `UK_F2F_REFUND_NO`、支付中心退款单号 → `IDX_F2F_REFUND_CENTER`（**回调带来的单号在本地不存在时调用方 MUST 拒绝并留痕**）、上报幂等键 → `UK_F2F_REPORT_IDEM`、故障单号 → `IDX_F2F_REPORT_SLIP`、票按订单 → `IDX_F2F_TICKET_ORDER`、票按（逻辑卡号 + 交易日期）→ `UK_F2F_TICKET_LOGIC`、用户激活订单 → `IDX_F2F_ORDER_USER`。`F2fRefundMapper.xml:86` / `:101`、`F2fResultReportMapper.xml:80` / `:88` / `:96`、`F2fTicketMapper.xml:93` / `:101`、`F2fOrderMapper.xml:150`、`mapper/F2fRefundMapper.java:64`
- **两个 mapper XML 头部各有一段「本文件内所有 SQL 正文 NEVER 出现行注释或块注释」的告示**（Druid WallFilter `commentAllow=false` 会把带注释语句判成注入并**静默失效**，且**只在 Oracle 生产暴露、达梦环境关了 WallFilter 测不出来**）—— 这两段是本模块对 AGENTS.md §5.1 的现场提醒，**NEVER 删**。`F2fNotifyTaskMapper.xml:5`、`F2fResultReportMapper.xml:5`
- **`ITP.PAY` 的「BOM/TVM 充值」组（段 7 / 8）判据是 `BIZ_TYPE='02'`、不分渠道**。`mapper/F2fReconExportMapper.java:72`
- **补款单相关四个 mapper 方法的语义**：新增补款单靠 `ORDER_NO` 唯一索引兜底重复下单、明细结清标记单条更新、捞超时**仅限 `INIT`**、关单白名单**只放 `INIT`**。`mapper/SupplementOrderMapper.java:20`~`:62`

### 十九、配置（`application.yml`）补遗

- **`other.web.unSafeUrlChars` MUST 带引号**：YAML 裸 `null` 会被绑成**空串**，`FirstFilter.checkUrlSafe` 里 `"".split(" ")` 得到 `[""]`，而 `url.contains("")` **恒为 true** ⇒ **所有请求一律 403 `{"msg":"check url unSafe"}`**，且 warn 日志打的是 `unsafe char:`（空）。旧应用是 K8s env `other.web.unSafeUrlChars=null`（字符串 `"null"`），行为等价于关闭。`application.yml:14`
- **`service.gateTxnPay.url` 缺失的症状是「补款支付成功、补款单 SUCCESS，但明细永远不是 SETTLED、原行程还挂欠费」**：本模块此前直接 UPDATE `GATE_TXN_PAY`（owner 是 gate-txn-pay-server），2026-09-16 改成 RPC `POST /internal/gate-txn-pay/debit/converge`；**与 recon 同一个坑 —— Service 端口等于 NodePort 号（30019）、不等于容器 `server.port`（9106）**；键缺失时 rpc 落到默认服务名 `gate-txn-pay-service`、DNS 解析不到。`application.yml:118`
- **对账相关三条**：本服务是第 **4** 个源、**只出 `ITP.PAY` 与 `ITP.BUS`**；`internal-token` **目前无读取方**（`X-Recon-Token` 校验已按用户 2026-09-11 的要求在各源与 recon-server 侧整段删除，属测试期有意降级、**上线前 MUST 恢复**，保留键位是为了恢复时不用再找位置）；`temp-dir` 是分片落地临时目录、**分片一经上送即删除**，峰值占用只有「并发文件类型数 × 单片上限」。`application.yml:127`

### 矛盾与待裁决

> 本轮只记录、**未改任何代码**（用户明确要求）。每条按「注释说什么 / 代码或 DDL 实际是什么 / 证据 / 建议裁决」写。

1. **订单号是否含版本标识位 `F2`**。注释说：`F2F_ORDER.ORDER_NO` 是「ITP 订单号，**含版本标识位 F2**」（`entity/F2fOrder.java:23`）。实际是：订单号格式已按用户 2026-09-10 裁决「按照旧的来」定为 **20 位、与旧实现完全一致、NEVER 加版本标识前缀**，22 位 `F2 + 业务码 + 时间 + 序列` 那版已废弃（`support/F2fOrderNo.java:17`，阶段一亦已记录）；旁证是现场实测订单号 `00202609160953330199`（`controller/ci/bom/BomOrderController.java:313`）**以业务码 `00` 打头、无 `F2`**。**建议裁决**：`entity/F2fOrder.java:23` 那半句是废弃方案的残留，删掉「含版本标识位 F2」即可，**NEVER 反过来照它给订单号加前缀**。
2. **`EXPIRE_TMS` 是否恒为「创建时间 + 180 秒」**。注释说：「二维码失效时间，创建时间加 180 秒」（`entity/F2fOrder.java:120`）。实际是：**只有 TVM 拉码单是 180 秒**，BOM 售票单是 30 分钟（`bomSaleExpireSeconds`，`service/F2fTvmOrderService.java:94` / `:208`），APP 取票单另有取值。**建议裁决**：把列注释改成「按渠道不同，取值见 `F2fTvmOrderService.insertOrder` 的 `expireSeconds`」，**NEVER 让读者据这条注释推断 BOM 单 3 分钟就过期**。
3. **`CK_F2F_REFUND_STATUS` 到底几个取值**。注释说：「取值见 `CK_F2F_REFUND_STATUS`：INIT / PROCESSING / SUCCESS / FAILED」四个（`entity/F2fRefund.java:44`）。实际是：**DDL 是五个、含 `MANUAL`**（`sql/f2f-schema.sql:235`，列注释原文亦写明「MANUAL 超过自动收口时间窗仍查不到结果需人工介入（钱是否已退未知）」），而 `F2fRefundService` 的放弃分支正是置 `MANUAL`（`service/F2fRefundService.java:287`），同一实体的 `FINISH_TMS` 注释也写了「进入 SUCCESS / FAILED / **MANUAL** 终态时回填」（`entity/F2fRefund.java:89`）。**建议裁决**：补上 `MANUAL`；**NEVER 反过来据这条四值注释去删 DDL 里的 `MANUAL`** —— 那会让放弃分支写入即 `ORA-02290`。
4. **`TOPUP_SUSPECT` / `CANCELED` 有取值、无写入方**。注释说：这两个状态「**主代码没有任何地方把订单写成它们**，因此白名单里没有入边 —— 这是照实记录、不是遗漏」（`domain/F2fOrderStatus.java:29`）。但业务上「充值存疑」确实存在：`topupStatus=02` 存疑时**不退款、留人工处理**（`api/device/tvm/TopupCardFailNotiReqDTO.java:27`，阶段一已记），却没有任何状态承载它。**建议裁决**：二选一 —— 要么给 `02` 分支补 `TOPUP_SUSPECT` 写入方（同时补白名单入边），要么在文档与列注释里明确「存疑只留上报、订单状态不动」；**NEVER 让「有状态取值但没人写」的现状继续无归属**。
5. **支付结果回调不验签，与 AGENTS.md §5.2 直接冲突**。注释自述：「本回调带 `sign`，但旧实现从不验签，本次重写保持同契约、同样不验签 …… 意味着任何网络可达方都能构造一条 `status=SUCCESS` 的回调把订单改成已支付」，并声明「加验签属契约变更 + 支付安全红线，**MUST 人工评审后单独实施**」（`api/paycenter/PayCenterCallbackRequest.java:17`）；同一条现状在设备链路是「本链路没有验签」（`api/device/BaseDeviceRequest.java:9`）。**建议裁决**：作为**上线前必须闭合的安全项**单列，与对账那条「`X-Recon-Token` 已删、上线前 MUST 恢复」同批处理。
6. **两套待签串里至少有一条的网关验签实际未生效**。注释自述：`collect-pay-server` 签外层信封五键、`pay-sign-server` 签 bizData 内部字段，**两套没有交集（一个签壳、一个签芯）而两条链路都在生产跑通过**，因此「说明至少有一条的验签在网关侧实际未生效」；当前按用户裁决取 pay-sign 口径，**后续联调失败再回退**（`channel/paycenter/PayCenterSigner.java:29`）。**建议裁决**：向支付中心确认「到底校不校验签名、校的是哪一套」，拿到一次真实应答做证据（对齐 ADR-D92 的口径）；**在拿到结论前 NEVER 把两套之一当成「已验证正确」**。
7. **`payType=0` 本地聚合码分支的 `F2F_PAYMENT` 落库口径仍是空的**。注释自述：「这条分支没有任何支付中心交互，因此 `F2F_PAYMENT` 该记什么状态在设计里仍是空的（§十六 P1），**实现落库前 MUST 先定口径**」（`channel/paycenter/PayCenterMessageFactory.java:107`）。而该分支**已经在跑**（IF2A-01 / IF2A-09 的 `payType=0`）。**建议裁决**：先按「记一条 `PAY_SCENE=qrcode`、`PAY_TYPE=0`、`PAY_STATUS=INIT` 的尝试」补齐，还是明确「本分支不落 `F2F_PAYMENT`」—— 两种都要在对账口径上复核（`ITP.PAY` 五组度量取的是 `F2F_PAYMENT.PAY_AMOUNT`）。
8. **IF8B-05 通知有四处按我方推断填的值**（`payResult` 只推成功、金额按分、`voucher` 恒空串、`orderType` 固定 `"0"`），其中**规格原文对 `voucher` 的说明自相矛盾**（既写「整段用于生成二维码」又写「预留字段」）且未给必填性，**充值单在 `orderType` 枚举里根本没有对应取值**（`service/F2fPayCenterFlow.java:433`~`:446`）。**建议裁决**：四条一并向甲方澄清；**NEVER 在澄清前按猜测改任何一处**（改了就是静默改变对外通知内容）。
9. **APP 族 `8999` 同码两义**：既表示「支付中（`requestPayResult` 拿不到明确结果）」、又是 service 层通用失败（旧 `failMessage`）（`api/device/app/AppResponses.java:26` / `:29`）。**建议裁决**：属既有契约、**NEVER 拆码**；但排查 APP 侧问题时 **MUST 以报文体里的 `payResult` 而不是 `retCode` 判断支付结论**。
10. **`F2fRefundRetryPolicy` 的「约 27 次覆盖满 24 小时」是注释里的推算结论、不是配置值**（`service/F2fRefundRetryPolicy.java:45`）。**建议裁决**：判断线上真实退避 / 放弃行为 **MUST 现查 `f2f.refund.*` 的 yml 值与 Deployment env**（AGENTS.md §8 那条「判断线上真实值 MUST 查 env」同样适用），**NEVER 直接引用注释里的次数**。

### 墓碑清单（阶段二新增；已删除或已废弃，NEVER 恢复）

> 与阶段一文末那份「墓碑注释清单」不重复。判据同阶段一：**代码里删了就再也无处可查**，因此逐条留证据位置。

1. **旧库只读旁路 `LegacyOrderReader` 已于 2026-09-13 按用户裁决整体删除**（原话「后续要停掉旧服务，删除旧库的，不需要做兼容层」）。连带后果：**切流前由 collect-pay-server 建的历史单，在本服务上一律「订单不存在」（`2002 / 没有找到匹配的订单`）—— 这是有意的**。`service/F2fTvmPayResultService.java:93`（阶段一在 `F2fTvmOrderService.loadOrder` 也记过一次，本条是拆分后的新宿主）
2. **`F2fAppOrderService` 里的私有 `warnIfConflict` 已删**：2026-09-16 拆分时确认它**类内零调用、是死代码**；其注释声称服务「退款域的 `REFUNDING` / `REFUNDED` 推进」，而 ADR-D88 把退款正交化之后退款收口走的是 `refundMapper.updateStatus` + `orderMapper.updateRefundSummary`、**没有任何状态 CAS 需要它**。**NEVER 凭那条旧注释把它加回来**；真需要冲突告警时用 `F2fPayCenterFlow.warnIfConflict`（public）。`service/F2fAppOrderService.java:249`
3. **IF5A-03 曾补过的 `updateType` 与 `optDate` 格式两条校验已移除**（用户 2026-09-11 裁决「不校验」）。**NEVER 再以「旧实现漏校验」为理由加回来。** `controller/ci/bom/BomOrderController.java:262`
4. **22 位 `F2 + 业务码 + 时间 + 序列` 的订单号格式已废弃**（长度变化是会直接打挂链路的 A 类契约差异，设备与收银台对该字段是否有长度限制未核实）。`support/F2fOrderNo.java:17`
5. **「非可上报状态回 `2005`」这版实现已回退**：支付中心 payNotice 迟到时 TVM 可能已经把卡充好并上报，**拒收等于把唯一的现场证据丢掉且设备不会再补**；现在保留旧的「收下」语义、只把**状态推进**锁在 `PAID` 上。2026-09-11 双打实测：旧 `0000`、新 `2005`。`service/F2fTopupResultService.java:93`
6. **TVM 与 BOM 那两份逐字相同的充值退款私有方法（`submitRefund` / `submitTopupRefund`）已合并成一个**，`deviceId` 提成参数、落库内容与日志逐字不变。**NEVER 拆回两份**（两条链路的退款语义完全相同，分开只会漂移）。`service/F2fTopupResultService.java:223`
7. **`F2fRefundService` 构造里的 5 个 `@Value` 已收成 `F2fRefundRetryPolicy`**（配置键名与判定语义一行未改，已有 env 覆盖照样生效）。`service/F2fRefundService.java:122`
8. **旧 BOM `notiTopupResult` 的 `@Transactional` 已于 2026-09-14 摘除**（它把支付中心退款调用包在事务里，违反 §5.2）。**NEVER 因为「旧的有事务」而给新类加 `@Transactional`。** `service/F2fTopupResultService.java:183`
9. **原退款实现的「固定 60 秒 × 20 次」重试已废弃**：那种写法把放弃判定放在扫表谓词里，次数一到单子直接从扫描结果消失、状态停在 `INIT` / `PROCESSING`、**无告警无人工入口，20 分钟内静默丢单**。`service/F2fRefundRetryPolicy.java:19`
10. **服务端 `Thread.sleep` 轮询支付结果（最长 180 秒）已删除**，改由 BOM 自己轮询 IF8A-06。`controller/ci/bom/BomOrderController.java:169`
11. **`PayCenterServiceImpl.callPayCenter` 的「异常后 `return null`」语义已废弃**：调用方一律按「支付中心返回结果为空」写 `status=FAILED`，**把「不知道」当成了「没付成功」**；现在改为 `isTransportFailed()` 三分支 + `UNKNOWN`。`channel/paycenter/PayCenterResult.java:19`
12. **旧 `SignUtils:33` 那行 `log.info("privateKey is {}", privateKey)` 未被保留**（私钥与聚合码密钥 NEVER 进日志）。`channel/paycenter/PayCenterSigner.java:47`
13. **`F2fTvmOrderService` 自留的 `warnIfConflict` 副本已删、统一走 `F2fPayCenterFlow.warnIfConflict`**；但「二维码超时收口」那支的 `markPaid` **仍自己接返回值并据此分流**、不走 `markPaidAndReport`，**改它时 MUST 确认仍然接住了返回值**（那段代码 2026-09-16 起在 `F2fOrderExpireService.reconcileExpiredOrder`）。`service/F2fTvmOrderService.java:273`
14. **`payNotice` 的 form 兜底入口是「无法排除」而不是「有文档依据」**：旧应用的 `payNotice` 用 `@ModelAttribute`（只能收 form），**从上线到 2026-09-11 一次都没被真实调用过**（当天翻遍旧应用日志，入站记录为 0），所以「支付中心到底发 JSON 还是 form」我方**没有实证样本**。**NEVER 因为「看起来冗余」删掉其中一个入口**；等真实回调到达后按日志确认形态再决定是否收窄。`controller/ci/tvm/TvmOrderController.java:313`

### 本轮覆盖率自评

**分母（机械统计，`face-pay-server/src/main`，2026-09-16）**

- 文件 **139 个**（`.java` / `.xml` / `.yml` / `.properties` / `.sql`），总行 **19485**。
- 注释行 **5680**：与用户给的口径（Java 5138 + XML/properties 255 = 5393）差 **287 行**，差额来自两处 —— 本轮把 `src/main/resources/sql/*.sql` 也纳入扫描（`--` 行注释），以及多行块注释的续行按行计入。
- 注释块 **1074** 个。其中**判为纯样板、整块跳过**的（全部行都是 `@param` / `@return` / `@throws` / 孤立的 `*`、`/**`、`*/`、`/** 构造方法 */` 之类）合计 **1713 行**。

**分子（本轮实际抽取）**

- 阶段一已引用（按 `文件:行号` ±3 行容差比对）或整块样板的注释块被跳过后，**本轮输入 747 块 / 2246 行**。
- 本轮产出 **205 条**：知识条目 **181 条**（174 条正文 bullet + 7 行调度任务表）、**矛盾与待裁决 10 条**、**墓碑 14 条**；分 14 个小节（§八~§十九 + 矛盾 + 墓碑 + 本节）。
- **未抽取（判为零知识）的注释**：上面那 1713 行样板，外加一批「字段名中文直译」型行内注释（如 `/** 自增主键。 */`、`/** 创建时间。 */`、`/** 订单号。 */`）—— 这类在本轮里**只在能带出「值域 / 单位 / 索引归属 / 幂等语义」时才抽**，纯直译一律跳过。

**零知识注释的文件（4 个，扫描口径下整文件没有任何非样板注释块）**

- `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/ci/app/SupplementAppController.java`
- `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/scheduler/SupplementOrderCloseProcessor.java` —— **本模块唯一一个完全没有 Javadoc 的调度类**，而它带着两个 cron（见 §十六），**排查补款收敛时 MUST 直接看注解，别指望注释**。
- `face-pay-server/src/main/resources/sql/f2f-order-refund-summary-migration.sql`
- `face-pay-server/src/main/resources/sql/f2f-schema.sql` —— **这条是扫描口径的假阴性，NEVER 据它认为 schema 没有知识**：该文件的列语义与值域全部写在 **`COMMENT ON TABLE/COLUMN` 语句**里（不是 `--` 注释语法），本轮已从它取到 `CK_F2F_REFUND_STATUS` 五值、`REFUND_SOURCE` 七值等硬事实（见「矛盾与待裁决」第 3 条）。

**本轮自知的未覆盖面（留给后续轮次）**

1. **`COMMENT ON` 里的表 / 列语义没有系统性抽取**：`f2f-schema.sql` 有数百条 `COMMENT ON COLUMN`，本轮只在核对矛盾时点取了几条。若要把「表列语义」也变成 docs 唯一载体，MUST 单独一轮按表逐列搬。
2. **`src/test` 未纳入**（本轮分母只算 `src/main`）；单测里的断言注释可能承载「为什么这样断言」的判据。
3. **`pom.xml` / `log4j2-*.xml` / K8s 描述文件的注释未纳入**（`face-pay-server/pom.xml` 那段「jkube 刻意不 `activeByDefault`」的说明已在 AGENTS.md §7，未重复搬）。
4. **注释里点名但仓库内未核对的外部文档**：`docs/architecture/face-pay-refactor.md` §七 / §十四 / §十六 被多处注释引用，本轮未回读那份设计文档核对编号是否仍对得上。
5. **行内 `//` 注释里的一次性调试说明**未逐条抽（本轮只抽了带 NEVER / 事故日期 / 契约结论的那些）。






## 附：collect-pay-server 完整迁移 + face-pay 测试与 DDL 补漏（2026-09-16）

> **本节与前面三节（face-pay 阶段一、collect-pay 阶段一、face-pay 阶段二）并列、互不覆盖。** 抽取源有三块：①`collect-pay-server/src/main` 的**全部** 185 个文件（Java 164 / mapper XML 20 / `application.yml` 1，注释 4448 行 / 1818 块），阶段一只抽了约 251 行，本轮补齐剩余；②`face-pay-server/src/test` 的 13 个文件（注释 356 行 / 119 块，**此前两轮的分母都只算 `src/main`，测试从未纳入**）；③`face-pay-server/src/main/resources/sql/f2f-schema.sql` 的 **51 条 `COMMENT ON`**（阶段二把该文件误判成「零注释文件」，是扫描口径的**假阴性** —— 它的知识全写在 `COMMENT ON TABLE/COLUMN` 语句里、不是 SQL 行注释语法）。
>
> **face-pay 的 `src/main` 本轮一行未读、一行未删**（阶段一 + 阶段二已覆盖）。
>
> **行号会漂：引用任何一条前 MUST 先 grep 关键字确认位置。** 本节所有 `文件:行号` 只是入口坐标、不是断言。定位串前缀：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/`、`collect-pay-server/src/main/resources/`、`face-pay-server/src/test/java/com/chinasofti/huateng/facepay/`。

### 附二.0 本模块现在到底还在干什么（读代码前先看这条）

- **collect-pay-server 已退出设备域与 APP 域的入向流量**（2026-09-16 17:31 切至 face-pay，ADR-D117），但**进程仍在跑、NEVER 停掉它**。它现在承担四类职责：①**日终对账源** `POST /internal/recon/export`（本服务是四个源里的第 4 个，只出 `ITP.PAY` 与 `ITP.BUS`）；②**补款** `/internal/app-order/**`（`gate-txn-pay` 侧 `AppPayOrderMapper` 及其 XML 已删除，写入改走 `CollectPayClient.registerAppPayOrder`）；③`NoticeAppTask` 的 **4 个旧表通知端点**（对应三张 `TBL_NOTICE_APP_*` 与失败记录表）；④**旧单按票退款**入口（切流前由本服务建的历史单只在旧表里，face-pay 上一律「订单不存在」）。
- **本模块是全项目仅剩两个「自带 `@Scheduled`」的业务模块之一**：`task/SingleTicketRefundTask.java` **4 处**，cron 分别 `0 0 20 * * ?`（两条）、`0 1 9,15,21 * * ?`、`0 0 9,15,21 * * ?`。四者**无分布式锁 ⇒ collect-pay-server MUST 单副本**。**已知内部矛盾**：方法内日志写着「收到由 web-server Quartz 定时任务发起的调用」，而对应的三个 `@PostMapping` 入口**已被整段注释掉** —— 也就是说「外部触发方并不存在，真正在驱动的是本模块 `@Scheduled`」。**迁 Quartz 时 MUST 先摘 `@Scheduled` 再放开 HTTP 入口**，顺序反了同一批退款会跑两遍。`task/SingleTicketRefundTask.java:34` / `:51` / `:69` / `:87`
- **`TRANS_TYPE` 三处证据互相矛盾，后果是日终对账「BOM 行政处理」与「BOM 处理」两组度量恒 0**：①entity 注释的取值清单里**没有发售**；②`BomBusinessCodeEnum` 的 `01` 与 `22` 描述都写「充值」，而 `01` 不在 `transType` 取值表里；③建单处**硬编码 `setTransType("01")`**。物证还包括 `TvmOrderServiceImpl` 里被整段注释掉的 `getBomOrderSalePre`（**NEVER 删那段注释、也 NEVER 自行猜值补写**）。现状裁决是整表归发售组、两组度量留 0，**待甲方给判据**。
- **端口有两个数字，判断哪个生效 MUST 查 Deployment env**：仓库 `application.yml:56` 是 `port: 58101`，而集群 `collect-pay-c23ku-svc` 的 `targetPort` 是 **8080** —— 线上靠 Deployment env `server.port=8080` 顶掉 yml。**因此 NEVER 用 yml 里的 58101 去探活或配 Service**，也 NEVER 反过来改 yml「对齐线上」（那会打断本机与测试环境的既有用法）。
- **旧表与 `F2F_*` 的落点差异**：切流后新单只进 `F2F_ORDER` / `F2F_PAYMENT` / `F2F_REFUND` / `F2F_TICKET` / `F2F_RESULT_REPORT` / `F2F_NOTIFY_TASK` / `F2F_DEVICE_STATUS`；本模块的 18 张旧表**只读不再增长**（按 mapper 引用频次排序：`TBL_TVM_APP_ORDER`、`TBL_BOM_ORDER_PAY`、`TBL_TVM_ORDER_PAY`、`TBL_TVM_ORDER_TOPUP`、`TBL_TVM_SUB_TICKET`、`TBL_TVM_TAKE_TICKET_ORDER`、`TBL_TVM_ORDER_REFUND`、`TBL_BOM_SUB_TICKET`、`TBL_APP_ORDER_REFUND`、`TBL_BOM_ORDER_REFUND`、`TBL_BOM_BUS_RESULT`、`TBL_TVM_T`、`TBL_NOTICE_APP_FAILURE_RECORD`、`TBL_TVM_ORDER_PAY_PRE`、`TBL_BOM_TOPUP_RESULT`、`TBL_BOM_SALE_INFO`、`TBL_TICKET_REFUND_RECORD`、`TBL_BOM_TICKET_REFUND`）。**唯一例外是补款与对账**：补款单仍写旧表、对账仍从旧表抽取 —— 所以「旧表不再增长」这句**只对设备与 APP 下单成立**，NEVER 推广成「旧表已冻结」。


### 附二.1 controller / api / dto / entity / 常量（`collect-pay-server` 非 service 非 mapper 部分）

#### collect-pay-server 非 service / 非 mapper 层注释知识抽取（g1）

来源：`collect-pay-server/src/main` 下 controller / api / dto / vo / entity / constant / config / util / task 的注释块，共 264 块 1198 行。枚举取值、错误码取值已回源码核对（见坐标）。

#### 一、TVM 设备域（controller / api / dto）

- **TVM 侧接口全部挂在 `TvmOrderController`，URL 前缀是 `/ci/tvm/`，按注释分四段**：心跳检测、扫码购票、扫码取票、扫码支付（充值）。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/controller/ci/tvm/TvmOrderController.java:50,61,221,255`
- **TVM 真实接口与编号对应**：IF2A-01 `/ci/tvm/requestGenSjtOrder` 提交单程票订单、IF2A-11 `requestPayment` 扫码支付（TVM 主动扫用户付款码）、IF2A-03 `/ci/tvm/requestPayResult` 查询支付结果、IF2A-04 `/ci/tvm/notiTakeTicketResult` 出票结果通知、IF2A-05 `/ci/tvm/notiTakeTicketFailResult` 出票故障通知、IF8A-15 激活取票订单、IF2A-08 扫码取票订单查询、IF2A-09 请求充值下单、IF2A-06 充值结果通知、IF2A-07 充值失败通知。`.../controller/ci/tvm/TvmOrderController.java:63-66,113-119,142-145,159-162,178-181,223-228,239-244,257-262,274-279,290-295`
- **IF2A-04 出票结果通知的触发条件是「出票张数与订单张数一致」**，注释明确「此情况下不发生退款」；张数不一致走 IF2A-05 出票故障通知。`.../controller/ci/tvm/TvmOrderController.java:159-162`
- **IF2A-05 出票故障通知的错误代码 `2101` 语义是「取票二维码超时，解锁订单」**，且该码下「故障时间」字段 MUST 填取票二维码中的生成时间、不是当前时间；格式 `YYYYMMDDHH24mmss`。`.../model/request/tvm/NotiTakeTicketFailResultReqDTO.java:21-24,32-35`
- **IF2A-03 查询支付结果的应答取值域是三值 `ORDERED` / `SUCCESS` / `FAILED`**（`ORDERED` 表示「已经下单」，不是支付中）。`.../model/response/tvm/RequestPayResultRespDTO.java:22-25`
- **IF2A-11 扫码支付应答只有两值 `SUCCESS` / `FAILED`**，没有 `PROCESSING`；而退款应答有三值 `SUCCESS` / `FAILED` / `PROCESSING`。`.../model/response/tvm/RequestPaymentRespDTO.java:20-24`、`.../model/response/tvm/RequestRefundRespDTO.java:20-25`
- **IF2A-01 应答的二维码字段是「支付 URL」整串，TVM MUST 把整个字段直接渲染成二维码**，不要二次拼接或截断。`.../model/response/tvm/TvmOrderResult.java:25-27`
- **TVM 请求可能把 `deviceId` 放在 `bizData` 里而不是公共参数**：当公共参数 `deviceId` 缺失或为空白时 MUST NOT 擦除 `bizData` 内的值。`.../utils/TransforUtils.java:24-25`
- **`TvmOrderController` 里有一个「自己用」的退款接口**：按订单号发起退款、退款金额取订单总金额（全额），注释自述非甲方契约。`.../controller/ci/tvm/TvmOrderController.java:197-203`
- **TVM 充值订单表已有 `payType` 列，取值 `0` 其他支付方式 / `1` 数字人民币 app**。`.../entity/TvmTopupOrder.java:47-50`
- **TVM 侧错误码空间是 `2xxx`**：`0000` 成功、`2999` 失败、`2001` 非法设备、`2002` 非法参数、`2003` 无激活的订单、`2004` 充值金额超限、`2005` 订单未支付、`2006` 订单号错误、`2007` 订单已退款、`2008` 订单已锁定。`.../constant/TvmPayCodeEnum.java:8-18`

#### 二、BOM 设备域

- **BOM 侧接口全部挂在 `BomOrderController`，含心跳检测**，覆盖下单、支付、查询支付结果、业务操作结果通知、充值结果通知、票卡分析与更新。`.../controller/ci/bom/BomOrderController.java:20-23,39`
- **BOM 真实接口与编号对应**：IF8A-04 请求非现金收款下单（ITP 生成并返回订单号）、IF8A-05 扫码支付、IF8A-06 查询支付结果、IF2A-08 业务操作结果通知、IF2A-09 充值结果通知（规格 7.3.3.5）、IF5A-01 请求票卡分析（后付费二维码票分析）、IF5A-03 请求票卡更新、IF5A-09 HCE 票卡更新结果通知。`.../controller/ci/bom/BomOrderController.java:49-55,106-112,133-139,157-163,184-190,211-217,238-244,321-327`
- **IF8A-06 查询支付结果应答是三值 `SUCCESS` / `FAILED` / `PROCESSING`**，与 TVM 的 IF2A-03 三值（`ORDERED`/`SUCCESS`/`FAILED`）**不是同一个取值域**。`.../controller/ci/bom/BomOrderController.java:133-139`、`.../model/response/bom/BomOrderResult.java:92-99`
- **BOM 非现金收款 `transType` 取值域（9 个，含十六进制形态）**：`02` 超时更新、`03` 超程更新或一卡通余额不足更新、`04` 未出站更新处理、`05` 无入站更新处理、`06` 储值票即时退卡或单程票退票、`22` 充值、`2A` 黑名单卡锁定、`2B` 卡锁定解除、`42` 行政处理。**`2A` / `2B` 是字面量、不能当十进制处理**。`.../entity/BomNoCashOrder.java:19-30`、`.../model/request/bom/RequestGenNoCashOrderReqDTO.java:11-22`
- **`transType=42`（行政处理）时行政交易类型代码必填，取值 `01` 到 `0A` 共 10 个**：`01` 付费区内丢失车票或无票出闸或车票折损、`02` 闸门被误用、`03` 车票损坏不能出闸、`04` TVM 发售单程票卡票、`05` TVM 找零卡币或找零不够、`06` TVM/BOM 单程票无效不能进闸、`07` 储值卡已扣值但发售未完成、`08` 预发售单程票退票、`09` 特殊情况单程票退票、`0A` 其它情况。`.../model/request/bom/RequestGenNoCashOrderReqDTO.java:25-38`、`.../entity/BomNoCashOrder.java:33-35`
- **`TBL_BOM_BUS_RESULT.STATUS` 是四值，且 `2` 与 `3` 都表示「处理失败」、靠退款结果区分**：`0` 已通知待处理、`1` 处理成功、`2` 处理失败（退款成功）、`3` 处理失败（退款失败）。**退款订单号只在 `status=2` 时才有值**，排查退款单缺失 MUST 先看这一列。`.../entity/BomBusResult.java:31-37,40-43`
- **IF2A-08 业务操作结果的取值是 `SUCCESS` / `FAILED`**（字符串，不是数字码）。`.../entity/BomBusResult.java:24-28`、`.../model/request/bom/NotiBusResultReqDTO.java:17-21`
- **BOM 充值结果通知的状态用 `00` 成功 / `01` 失败**，与同域其它接口的 `SUCCESS`/`FAILED` 形态**完全不同**，改动 MUST 分别对待。`.../model/request/bom/NotiTopupResultReqDTO.java:16-20`
- **HCE 与票卡分析链路的「更新区域类型」是 `00` 非付费区 / `01` 付费区**，三个 DTO 上重复出现、取值一致。`.../model/request/bom/NotiUpdateHceDataReqDTO.java:11-15`、`.../model/request/bom/RequestCardDataAnalyseReqDTO.java:26-30`、`.../model/request/bom/RequestCardDataUpdateReqDTO.java:15-19`
- **「建议本次操作类型」是三值且编码不连续**：`018` 补进站（无法出站）、`006` 补出站（最低票价，无法进站）、`005` 20 分免费进站更新（无法进站）。`.../model/request/bom/NotiUpdateHceDataReqDTO.java:18-23`、`.../model/request/bom/RequestCardDataUpdateReqDTO.java:22-27`
- **BOM 侧错误码空间是 `8xxx`，且含一个业务语义码 `0001` 乘客取消订单**：`0000` 成功、`0001` 乘客取消订单、`8999` 失败、`8001` 非法设备、`8002` 订单未支付、`8003` 非法参数、`8004` 无激活的订单、`8005` 充值金额超限、`8006` 订单号错误、`8007` 订单已退款。`.../constant/BomPayCodeEnum.java:4-13`
- **BOM 响应工具类的默认失败码是 `8999`**（`BomOrderResult.fail()` 无参重载），成功恒为 `0000` + `retMsg=成功`。`.../model/response/bom/BomOrderResult.java:41-45,123-128`

#### 三、APP 域

- **APP 侧接口挂在 `TvmAppOrderController`，前缀 `/ci/app/`**：IF8A-20 `/ci/app/requestOrder` 请求下单（返回订单号）、IF8A-11 `/ci/app/requestPayInfo` 请求支付信息、IF8A-18 `/ci/app/requestPayResult` 支付结果查询、获取激活订单、请求退款、退款结果查询、退款结果通知。`.../controller/ci/app/TvmAppOrderController.java:33-40,89-97,117-126,148-152,173-177,193-197,217-221`
- **IF8A-18 的查询策略是「先查库、有终态直接返、无终态才请求支付中心」**，即数据库里已有成功或失败结果时不再出网。`.../controller/ci/app/TvmAppOrderController.java:117-126`
- **退款结果通知只有 APP 支付这一条链路有回调**，设备域（TVM/BOM）没有退款回调通知。`.../controller/ci/app/TvmAppOrderController.java:217-221`
- **IF8A-11 的支付通道编码取值域**：`10001` 支付宝 SDK、`10002` 微信支付 SDK、`10003` 联支付 SDK。ITP 按该编码创建支付订单、请求对应通道预下单，再把预下单返回的支付信息**签名后**返回。`.../model/request/app/RequestPayInfoReqDTO.java:6-9,18-23`、`.../controller/ci/app/TvmAppOrderController.java:89-97`
- **IF8A-20 的「购票类型」目前只有 `0`（有起点站和终点站）一个取值**，无其它形态。`.../model/request/app/RequestOrderReqDTO.java:38-41`
- **「行业类型」取值 `1` 地铁 / `2` 公交 / `3` 打车 / `4` 购物**。`.../model/request/RequestPayReqDTO.java:27-29`
- **公共参数「商户编码」取值域（规格 7.5.1.1）**：`01` 青岛地铁 APP、`02` TVM、`03` BOM、`04` AGM、`05` ACC、`06` ITP、`07` STT，其余预留。`.../model/request/BaseRequestDTO.java:7-10`
- **`signType` 取值 `00` 不签名 / `01` sha1withrsa / `02` MD5**，本模块 DTO 与订单表列上重复出现同一套取值；`00` 即免签。`.../model/request/BaseRequestDTO.java:33-36`、`.../entity/TvmAppOrder.java:103-106`
- **APP 侧错误码只有两个：`0000` 成功、`9999` 失败**，与 BOM 的 `8xxx`、TVM 的 `2xxx` **完全不同一套**。`.../constant/AppCodeEnum.java:8-10`
- **APP 状态字面量是 `SUCCESS` / `FAIL`（不是 `FAILED`），退款额外有 `PROCESSING`**：`PAY_SUCCESS=SUCCESS`、`PAY_FAIL=FAIL`、`REFUND_ING=PROCESSING`、`REFUND_SUCCESS=SUCCESS`、`REFUND_FAIL=FAIL`。`.../constant/AppStatusEnum.java:9-14`
- **运营端「按指定金额退款」是独立入口，存在原因是 `/ci/app/requestRefundTicket` 只能全额退**：后者退款金额取 `TBL_TVM_APP_ORDER.PAY_AMOUNT` 全额，对已部分退款的订单必然超额被支付中心拒。**那条是 APP 对外契约（APP 只传 `orderNo`），NEVER 给它加金额字段**，因此另开 `/page/**` 入口。`.../controller/page/AppOrderPageController.java:15-32`
- **该运营端退款接口无幂等：同一订单连调两次会退两次**（退款操作手册铁律 2）。调用方 MUST 自己控制；中断后 MUST 先查 `TBL_APP_ORDER_REFUND` 再续跑，**NEVER 重放原列表**。`.../controller/page/AppOrderPageController.java:44-52`
- **该接口金额单位是分**，可退余额与超退拦截在 `AppOrderService.refundByAmount` 内完成，controller 只做入参非空校验；超过可退余额会被服务层拒。`.../controller/page/AppOrderPageController.java:44-52`、`.../controller/page/AppPartialRefundRequest.java:12`
- **`/internal/app-order/**` 三个端点的语义与幂等口径**：登记订单行 + 支付前置单行按 `orderNo` 幂等；关闭待支付订单带 `PAY_STATUS='0'` 白名单且**影响 0 行也返成功**；回查支付结果查不到时返 `found=false`、**不抛异常**。`.../controller/internal/AppPayOrderInternalController.java:41,47,53`
- **`/internal/app-order/**` 是把 gate-txn-pay-server 的跨域直写收回到 owner 侧的结果（2026-09-14）**，写的是 `TBL_TVM_APP_ORDER` 与 `TBL_TVM_ORDER_PAY_PRE`，字段口径与三条资损防线在 `AppPayOrderInternalService` 接口注释里。`.../controller/internal/AppPayOrderInternalController.java:14-30`
- **`/internal/recon/export` 受理即返回、不等抽取完成**：抽取是分钟级批处理，同步等待会让请求线程长时间阻塞在 ojdbc8 的 `synchronized` 方法里、pin 住虚拟线程的载体线程；收齐判定由 recon-server 侧批次状态负责，本响应只表示「已排入队列」。`.../controller/internal/ReconExportController.java:35-44`
- **运营端 TVM 当面付分页查询强制要求「订单标识或完整下单时间范围」**，目的是保护数据库、避免全表扫。`.../controller/page/FacePayOrderPageController.java:41`
- **运营端 TVM 当面付退款金额只从订单总额计算、不信任页面输入**，且复用 `TvmOrderPreService` 以沿用既有支付中心退款与退款单号落库流程；请求体只允许填退款原因，未填用默认原因。`.../controller/page/FacePayOrderPageController.java:84-88`、`.../controller/page/FacePayRefundRequest.java:3,5`

- **collect-pay-server 全部入口当前都没有鉴权**：该模块无 spring-security、无全局拦截器兜底，`signType=00` 即免签。三处新增端点的注释都明确写着这是**有意为之的临时降级、与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权与归属校验」冲突、上线前 MUST 恢复**。`.../controller/internal/AppPayOrderInternalController.java:14-30`、`.../controller/internal/ReconExportController.java:11-24`、`.../controller/page/AppOrderPageController.java:15-32`
- **`/internal/app-order/**` 的敏感度高于 `/internal/recon`，NEVER 拿后者当长期免鉴权的理由**：`/internal/recon` 是只读导出与内部编排，而 `/internal/app-order` 是按订单号改他人支付状态（拿到任意订单号即可给他人建单、或关掉他人的待支付单）。用户 2026-09-14 裁决「不加鉴权，照本模块现有 `/internal/recon` 的做法」。`.../controller/internal/AppPayOrderInternalController.java:14-30`
- **恢复鉴权 MUST 对齐 `AccountRequestVerifier` / `ItpRequestSignVerifier`，NEVER 自造签名逻辑**；若走共享令牌，比较 **MUST 用 `MessageDigest.isEqual` 做定长时间比较、NEVER 用 `String.equals`**（后者短路返回，可被逐字节计时探测出令牌内容），令牌值由 K8s Secret 注入、**NEVER 在仓库里写默认真值**。`.../controller/internal/AppPayOrderInternalController.java:408-410`、`.../controller/internal/ReconExportController.java:428-431`
- **运营端按金额退款的唯一现存防线是服务层的可退余额闸门，它只挡「退多了」、挡不住「不该退的人来退」**；补救方案二选一：补验签，或改由 web-admin 经 rpc 调用并只在内网暴露。`.../controller/page/AppOrderPageController.java:15-32`

#### 四、错误码与响应构造

- **本模块并存四套互不兼容的错误码空间，改动任一处 MUST 先确认调用方属于哪一域**：APP `0000`/`9999`；BOM `0000`/`0001`/`8999`/`8001`~`8007`；TVM `0000`/`2999`/`2001`~`2008`；模块内部 `CollectPayErrorCodeEnum` `0000`/`9999`/`9001` 系统内部错误 + `8001`~`8009`。`.../constant/AppCodeEnum.java:8-10`、`.../constant/BomPayCodeEnum.java:4-13`、`.../constant/TvmPayCodeEnum.java:8-18`、`.../constant/CollectPayErrorCodeEnum.java:7-18`
- **`CollectPayErrorCodeEnum` 的 `8001`~`8009` 与 `BomPayCodeEnum` 的 `8001`~`8007` 数字重叠但含义不同**（前者 `8001` 无效的参数 / `8002` 订单不存在 / `8003` 订单状态异常 / `8004` 支付失败 / `8005` 支付超时 / `8006` 退款失败 / `8007` 支付中心返回错误 / `8008` 签名验证失败 / `8009` 服务提供商不可用；后者 `8001` 非法设备 / `8003` 非法参数）。**按数字反查含义 MUST 带上枚举类名。** `.../constant/CollectPayErrorCodeEnum.java:7-18`、`.../constant/BomPayCodeEnum.java:4-13`
- **支付中心（支付平台）响应码是另一套，且成功码是 `0`、失败是 `-1`**：`0` 成功、`-1` 失败或系统异常、`1001` 参数校验失败、`1002` 商户不存在、`1003` 签名验证失败、`2001` 订单不存在、`2002` 订单状态异常、`3001` 签约不存在、`3002` 签约状态异常、`4001` 退款单不存在、`4002` 退款金额超限。**注意 `2001`/`2002` 与 TVM 侧 `2001`/`2002` 数字撞车、含义无关。** `.../constant/PayCenterErrorCodeEnum.java:12-22`
- **支付中心公共请求参数固定 6 项**：`merchantNo`、`apiVersion`、`signType`、`charset`、`bizData`、`sign`；公共响应参数固定 4 项：`code`、`msg`、`data`、`success`。`.../model/request/PayCenterRequest.java:3-6`、`.../model/response/PayCenterResponse.java:5-8`
- **支付中心订单状态取值 `ORDERED` / `SUCCESS` / `FAILED` / `UNPAID`**（对应其返回的 `status` 字段）；设备侧透出的支付码枚举是 `ORDERED` / `SUCCESS` / `FAILED` 三值。`.../constant/PayCenterStatusEnum.java:8-11`、`.../constant/DevicePayCodeEnum.java:8-11`
- **支付中心退款状态是 `PROCESSING` / `SUCCESS` / `FAIL`**（失败是 `FAIL` 而非 `FAILED`，与其支付状态 `FAILED` 不一致）。`.../constant/PayCenterRefundStatusEnum.java:9-11`
- **响应构造工具类共 8 个重载，分别覆盖「无数据成功 / 自定义消息成功 / 带订单号成功 / 带支付结果成功 / 带支付结果与状态描述成功 / 指定码失败 / 默认失败 / 带支付结果失败」**；带订单号那个专供非现金收款下单。`.../model/response/bom/BomOrderResult.java:41-160`

#### 五、entity 与表列语义

- **`TBL_TVM_APP_ORDER.PAY_STATUS` 的取值语义是 `0` 未支付 / `1` 支付成功 / `2` 支付失败 / `3` 支付中**。这与同模块 `ItpStatusEnum` 的 `0` 支付中 / `3` 未支付**正好相反**，见矛盾清单第 1 条，读写该列 MUST 以实体注释为准。`.../entity/TvmAppOrder.java:49-52`
- **`TBL_BOM_NOCASH_ORDER` 与 `TvmTopupOrder` 的订单状态是 `0` 支付中 / `1` 支付成功 / `2` 支付失败 / `3` 未支付**，另有「状态描述」列存中文（如「支付成功」「支付失败」「处理中」）。`.../entity/BomNoCashOrder.java:63-69,72-75`、`.../entity/TvmTopupOrder.java:42-44`
- **三张退款表的退款状态取值一致：`0` 退款中 / `1` 退款成功 / `2` 退款失败**（`AppRefundOrder`、`BomRefundOrder`、`tbl_refund_order`）。`.../entity/AppRefundOrder.java:38-43`、`.../entity/BomRefundOrder.java:39-44`、`.../entity/RefundOrder.java:46-48`
- **`TBL_TVM_APP_ORDER.ACTIVATE_FLAG` 是 `0` 未激活 / `1` 已激活**，与 `ActivateFlagEnum` 一致。`.../entity/TvmAppOrder.java:87-90`、`.../constant/ActivateFlagEnum.java:10-11`
- **`tbl_refund_order.业务类型` 是 `1` 扫码购票 / `2` 扫码充值 / `3` 扫码取票（单字符）**，与 `BusinessTypeEnum` 的 `01`~`05`（两字符）**不是同一编码**，见矛盾清单第 2 条。`.../entity/RefundOrder.java:31-33`、`.../constant/BusinessTypeEnum.java:8-12`
- **出票主记录的「出票时间/故障时间」格式是 `YYYYMMDDHHMMSS`（14 位无分隔）**，与 BOM 通知类 DTO 的交易时间、更新时间格式一致；而订单表的创建时间/更新时间是 `yyyy-MM-dd HH:mm:ss`。**同一模块内两种时间格式并存，转换 MUST 按列区分。** `.../entity/TvmMainTicket.java:27-29`、`.../entity/BomMainTicket.java:25-27`、`.../model/request/bom/NotiBusResultReqDTO.java:29-32`、`.../model/request/bom/NotiUpdateHceDataReqDTO.java:41-44`、`.../entity/BomNoCashOrder.java:98-105`、`.../entity/BomBusResult.java:46-53`
- **出票明细表（`tbl_tvm_sub_ticket` / `tbl_bom_sub_ticket`）的「主表 ID」是外键、统一关联 `tbl_tvm_main_ticket`**，三个子票实体注释一致（BOM 子票也指向 TVM 主表）。`.../entity/SubTicket.java:15-17`、`.../entity/TvmSubTicket.java:15-17`、`.../entity/BomSubTicket.java:15-17`
- **`TvmPayOrder.计算总价()` 的返回单位是分**，与运营端退款金额单位一致。`.../entity/TvmPayOrder.java:248-251`
- **`App_Pay_Logs.渠道类型` 是 `1` APP 端（tradeType `03`）/ `2` ETC 端（已弃用，tradeType `06`）**，即渠道类型与 tradeType 是两套编码、需成对理解；`2` 已弃用。`.../entity/AppPayLogs.java:44-46`
- **运营端 TVM 当面付列表的起终点站中文名不是订单列，是查询时按 `IN_STATION_CODE` / `OUT_STATION_CODE` 关联 `STATION_INFO` 带出的**。`.../model/page/FacePayOrderPageView.java:19,21`
- **已发起退款的退款单号存在订单表的 `RSV2` 预留字段里，不是独立列**，运营端列表就是从该字段取值。`.../model/page/FacePayOrderPageView.java:28`
- **本模块涉及的表名清单（注释点名者）**：`TBL_TVM_APP_ORDER`、`TBL_TVM_ORDER_PAY_PRE`、`TBL_BOM_NOCASH_ORDER`、`TBL_BOM_BUS_RESULT`、`TBL_BOM_ORDER_REFUND`、`TBL_APP_ORDER_REFUND`、`BOM_TOPUP_RESULT`、`App_Pay_Logs`、`tbl_tvm_main_ticket`、`tbl_tvm_sub_ticket`、`tbl_bom_sub_ticket`、`tbl_refund_order`。`.../entity/*.java`（各类头注释）

#### 六、常量 / 枚举 / 配置类

- **扫码取票「通知 APP 状态」三值：`0` 初始化状态 / `1` 通知成功 / `2` 通知失败**，常量在 `ItpCommon.NOTICE_INIT` / `NOTICE_SUCCESS` / `NOTICE_FAIL`。`.../common/ItpCommon.java:5-8`
- **订单号规则固定 20 位：`ProductType.code`（2 位）+ `yyyyMMddHHmmss`（14 位）+ 序列号（4 位）**，序列值范围 `0`~`9999`；退款单号同样 20 位、走同一工具类。`.../utils/OrderNoUtils.java:9-14,19-25,31-36`
- **`ProductType` 共 12 项、决定订单号前 2 位**：`00` 一票通单程票、`01` 计程票、`02` 计次票、`03` 计期票、`07` 员工票、`08` 琴岛通卡、`09` 青岛银行金融 IC 卡、`0A` 交通部互联互通卡、`0B` 补充票款、`0C` 招行一网通垫资补缴、`0D`（`EcnyActPay`）、`0E` 虚拟电子票。**注意没有 `04`/`05`/`06`**，按序号推断编码会出错。`.../constant/ProductType.java:10-21`
- **`BusinessTypeEnum` 是两字符编码、共 5 项**：`01` 扫码购票、`02` 扫码充值、`03` 扫码取票、`04` bom 支付、`05` app 退款。`.../constant/BusinessTypeEnum.java:8-12`
- **`ItpStatusEnum` 把支付与退款两组状态塞进同一个枚举、code 相互重复**：支付 `0` 支付中 / `1` 支付成功 / `2` 支付失败 / `3` 未支付，退款 `0` 退款中 / `1` 退款成功 / `2` 退款失败。**按 code 反查枚举项会拿到错的那个，MUST 按业务场景显式取项、NEVER 写按 code 的通用反查。** `.../constant/ItpStatusEnum.java:10-17`
- **`AppStatusEnum` 同样把支付与退款塞进一个枚举**：`SUCCESS` 与 `FAIL` 各出现两次（支付成功/退款成功、支付失败/退款失败），同上不可按 code 反查。`.../constant/AppStatusEnum.java:9-14`
- **`BomBusinessCodeEnum` 只有两项且描述都是「充值」**：`SALE("01","充值")`、`TOPUP("22","充值")`。`01` 那项的枚举名是 `SALE`、描述却写「充值」，见矛盾清单第 7 条。`.../constant/BomBusinessCodeEnum.java:4-5`
- **`config/Executor.java` 与 `config/ResTemplateConfig.java` 只有 `Created by ... 2021/12/17` 与 `2022/3/15` 的作者注释、无任何契约说明**，属历史遗留配置类。`.../config/Executor.java:10-12`、`.../config/ResTemplateConfig.java:8-10`

#### 七、其它（util / 启动类 / 过滤器等）

- **`SingleTicketRefundTask` 有 4 个 `@Scheduled`，cron 各不相同且两两同点**：`refundAppNotTakeTickets` 与 `refundTvmTopupNotTakeTickets` 都是 `0 0 20 * * ?`（每天 20:00）；`refundBomSaleNotTakeTickets` 是 `0 1 9,15,21 * * ?`；`refundBomTopupNotTakeTickets` 是 `0 0 9,15,21 * * ?`（BOM 两个差 1 分钟、明显是刻意错峰）。`.../task/SingleTicketRefundTask.java:34,51,69,87`
- **该任务处理的对象是「APP 在线购票后未取票的订单」**，做定时查询与退款。`.../task/SingleTicketRefundTask.java:30-32`
- **APP 接口协议的出向报文是 `multipart/form-data`、业务字段以 JSON 字符串放进 `bizData`**（不是 `application/json`）。`.../utils/HttpUtils.java:74-76`
- **`DateUtils` 的两个偏移方法入参格式不同**：按天偏移入参是 `yyyy/MM/dd`，按分钟偏移入参是 `yyyy/MM/dd HH:mm`（精确到分钟）。**斜杠分隔、不是横线**，传错格式会解析失败。`.../utils/DateUtils.java:41-45,53-56`
- **`utils/BaseResult.java` 的类注释是未替换的模板**（`Class description goes here.`，`@version：2018/5/30`），无契约信息。`.../utils/BaseResult.java:5-10`


### 附二.2 service 层（上）：下单、支付、BOM、APP 与补款、退款、通知

#### 一、下单与支付（TVM）

- **TVM 域接口编号与真实方法的映射**：IF2A-01 提交单程票订单存在**两个变体**，一个返支付 URL、一个「仅下单」（TVM 主动扫用户付款码场景，先建单后调支付）；IF2A-11 扫码支付（TVM 主扫付款码后调支付中心）；IF2A-03 查询支付结果（TVM 轮询）；IF2A-04 出票结果通知；IF2A-05 出票故障通知。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/TvmOrderService.java:12-63`
- **TVM 充值三条接口**：IF2A-09 请求充值下单（返支付 URL）、IF2A-06 充值结果通知（成功）、IF2A-07 充值失败通知。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/TvmTopupService.java:11-38`
- **扫码取票是两段式**：IF8A-15 由手机侧激活取票订单（用户扫 TVM 二维码后调用），IF2A-08 由 TVM 轮询查询激活状态。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/TvmTakeTicketService.java:12-27`
- **同一个编号 IF2A-08 在两个域语义不同**：TVM 域是「扫码取票订单查询」，BOM 域是「业务操作结果通知」。定位实现 MUST 以「模块 + URL」为准，NEVER 按编号推断。`.../service/TvmTakeTicketService.java:21-27`、`.../service/BomOrderService.java:48-54`
- **IF8A-11 请求支付信息的链路**：按支付通道编码创建支付订单，请求对应通道预下单，把预下单返回的支付信息**签名后**返回（应答含支付通道编码、支付信息、签名类型、签名）。`.../service/AppOrderService.java:26-33`
- **IF8A-18 支付结果查询是「先库后远端」**：库里已有成功或失败的终态直接返回；只有既非成功也非失败时才调 `tvmOrderPreService.requestPayResult()` 向支付中心回查。应答含交易流水号、支付结果、支付金额、支付时间。`.../service/AppOrderService.java:36-44`
- **前置单 `TRANS_TYPE` 是支付回调的分派键**：`payNotice` 按前置单 `TRANS_TYPE` 决定走哪条处理分支（`TvmOrderPreServiceImpl:120`），缺前置单时支付中心异步回调对该单**完全失效**、状态永远停在 `'0'`。`.../service/AppPayOrderInternalService.java:85-87`
- **订单号序列跨渠道共用**：APP / BOM / TVM 三侧共用 Oracle 序列 `ORDER_NO_SEQ`，靠它保证跨渠道不重号。`.../service/impl/AppOrderServiceImpl.java:59`

#### 二、BOM 售票 / 充值

- **BOM 域接口编号映射**：IF8A-04 请求非现金收款下单（返订单号）、IF8A-05 扫码支付（扫用户付款码后调支付中心）、IF8A-06 查询支付结果（应答 `SUCCESS` / `FAILED` / `PROCESSING`）、IF2A-08 业务操作结果通知、IF2A-04 出票结果通知、IF5A-01 请求票卡分析（后付费二维码票）、IF5A-03 请求票卡更新、IF5A-09 HCE 票卡更新结果通知。`.../service/BomOrderService.java:16-116`
- **IF2A-08 业务操作结果通知的三步语义**：①先落通知记录（`TBL_BOM_BUS_RESULT`，`STATUS='0'` 已通知待处理）；②`optResult=SUCCESS` 时置通知处理成功并更新原订单；③`optResult=FAILED` 时发起退款，按退款结果回写通知状态，并把**原支付记录的 `RSV2` 改成退款订单号**。`.../service/impl/BomOrderServiceImpl.java:427-437`、`:471`、`:486`
- **BOM 退款只动 `RSV2`、不动原支付状态**：退款结束（无论成功或失败）后第 6 步只更新原支付订单的 `RSV2`（退款记录 ID），原支付状态保持不变。`.../service/impl/BomOrderServiceImpl.java:1567`、`:1570`
- **退款成功的唯一判据是支付中心响应 200**，其余一切响应都按退款失败处理。`.../service/impl/BomOrderServiceImpl.java:765`
- **票卡 `RSV2` 的落点按订单类型分叉**：BOM 订单改 BOM sub 表对应票卡记录的 `RSV2`，其余类型一律按 TVM 处理。`.../service/impl/BomOrderServiceImpl.java:1524`
- **BOM 发起的退款可能需要反向通知 APP**：原单若是 APP 下单、扫码取票出来的票，退款后 MUST 通知 APP 退款结果。`.../service/impl/BomOrderServiceImpl.java:1584`
- **出票数量少于购票数量即触发退款，且退款请求送的是支付中心的订单号**（不是我方订单号）。`.../service/impl/BomOrderServiceImpl.java:1241`
- **充值结果通知的状态取值**：`topupStatus='01'` 置通知处理成功 + 原订单成功；`'02'` 置通知处理失败 + 原订单失败。`.../service/impl/BomOrderServiceImpl.java:675-685`
- **充值结果通知（`notiTopupResult`）有意不带 `@Transactional`，2026-09-14 摘除、NEVER 加回**：失败分支要调支付中心退款（`callPayCenter`），一旦被事务包住，`TBL_TVM_ORDER_TOPUP` 那一行的排他锁持有时长等于支付中心响应时长，BOM 对同一笔的重推全部堆在同一行串行等锁；等待超过 Druid `remove-abandoned-timeout` 后连接被强杀、`commit` 抛 connection closed，**连「充值结果通知已入库」那条 INSERT 一起回滚** —— 证据全丢、响应退化成全局异常处理器的 UUID `retCode`、BOM 继续重推，自我放大且没有出口。摘掉后每条 SQL 自动提交，通知记录与「退款中」状态先落地，退款结果再各自回写。`.../service/impl/BomOrderServiceImpl.java:686-703`
- **充值退款的中间态是显式落库的**：先把 TVM 充值订单置「退款中」，再按支付中心结果分别回写成功 / 失败 / 异常，失败还回写回滚原因。`.../service/impl/BomOrderServiceImpl.java:733`、`:771`、`:798`、`:829`、`:843`
- **`TBL_BOM_ORDER_PAY.TRANS_TYPE` 的取值在仓库内三处证据互相矛盾**：`BomNoCashOrder` 注释清单里根本没有「发售」、`BomBusinessCodeEnum` 的 `01` 与 `22` 描述都写「充值」、而 `BomOrderServiceImpl:950` 又把发售硬编码成 `01`。后果是对账 ITP.PAY 的「BOM 行政处理」（15/16）与「BOM 处理」（19/20）两组**无法可靠识别、一律填 0**，整张 BOM 表都计入「发售」组、不按 `TRANS_TYPE` 拆分。这是**有意的降级：宁可标注也不凭猜写取值**。`.../service/ReconExportService.java:292-299`
- **BOM 侧有一层「BOM 误调 TVM 接口」的兼容包装**，改 BOM 分派逻辑前 MUST 先看这一层。`.../service/impl/BomOrderServiceImpl.java:603`
- **「该订单是否发起过退款」的查询是纯追溯用途、实际业务不依赖**，三处重复出现，删改前不必按业务分支推导。`.../service/impl/BomOrderServiceImpl.java:1356`、`:1384`、`:1423`

#### 三、APP 域与补款

- **`/internal/app-order/**` 存在的原因是把跨域直写收回来**：`TBL_TVM_APP_ORDER` / `TBL_TVM_ORDER_PAY_PRE` 两张表的 owner 是 collect-pay，但 2026-09-11 起 gate-txn-pay-server 的 IF8A-26 补款单为复用收银台链路，直接在自己进程里 INSERT / UPDATE 这两张表 —— 违反 `docs/domain/README.md` 的「热路径写入定 owner」判据（字段口径散落两个模块，改一列语义就可能静默打挂对方）。2026-09-14 按用户要求收口成本接口。`.../service/AppPayOrderInternalService.java:8-42`
- **三条资损口径（从 gate-txn-pay 侧 mapper 注释原样搬来，改任一条前 MUST 逐条复核）**：① `requestPayInfo` 按 `TICKET_PRICE × TICKET_NUM` 算送去支付中心的金额（`AppOrderServiceImpl:152`），因此登记时 `TICKET_NUM` 固定 `1`、`TICKET_PRICE` 写全额，**NEVER 只写 `TOTALPRICE`**；② `RSV2` **MUST 非空** —— `refundAppNotTakeTickets`（`TvmAppOrderMapper.xml` 的 `selectByCondition`）会把「`PAY_STATUS='1'` + `RSV2` 为空 + 昨天创建 + 主票表查不到票」当成购票未取票**全额退款**，补款单永远不会有票，`RSV2` 一空就是次日把收到的欠费退回去；③ 前置单 `TRANS_TYPE` 只能是 `03`，只有它进 `appOrderService.payNotice`（只改订单行、不出票），缺这行前置单支付中心回调对该单完全失效、状态永远停在 `'0'`。`.../service/AppPayOrderInternalService.java:76-88`
- **两条硬校验放在入口而不是给默认值**：`RSV2` 为空、`TRANS_TYPE` 为空一律在入口拒绝。**NEVER 在这里给默认值放行** —— 默认值等于替调用方定资损口径。`.../service/impl/AppPayOrderInternalServiceImpl.java:147-152`
- **登记时三列都写全额是刻意的**：`requestPayInfo` 只读 `TICKET_PRICE` 与 `TICKET_NUM`，`payNotice` 与对账读 `PAY_AMOUNT`，页面展示读 `totalPrice`，三个读取方口径不同。`.../service/impl/AppPayOrderInternalServiceImpl.java:163-164`
- **`ACTIVATE_FLAG` 写 `'0'` 是为了不污染乘客的单程票列表**：`requestPreActiveOrderList` 只捞 `ACTIVATE_FLAG='1'`。`.../service/impl/AppPayOrderInternalServiceImpl.java:37-38`
- **待支付状态取值来自 `ItpStatusEnum.PAYING`，`requestPayInfo` 只对这个状态发起预下单。** `.../service/impl/AppPayOrderInternalServiceImpl.java:31`
- **登记按 `orderNo` 幂等，已登记过直接返成功**：调用方是「本地先落 PENDING、提交后再同步、失败留给扫表补偿」的 outbox 模式，同一笔会被重放。**NEVER 改成「重复即报错」** —— 那会让对方的补偿永远收不了口。`.../service/AppPayOrderInternalService.java:45-54`
- **两张表 MUST 在同一个本地事务里写**：只落其中一张的后果分别是「乘客看不到单」与「支付中心回调无处分派」，都需要人工介入。该方法全程无 RPC，因此可以安全地包事务；**NEVER 在本方法内新增任何远端调用**。`.../service/AppPayOrderInternalService.java:103-104`、`.../service/impl/AppPayOrderInternalServiceImpl.java:50-53`
- **`selectByOrderNo` 是 check-then-act，并发重放靠主键撞出来**：撞了说明另一条并发请求已登记成功，按幂等返成功而不是让对方进补偿队列重推。**判定 MUST 沿 `getCause()` 链**（本模块开了 tracing，`resource/micro/web` 的观测切面历史上会把异常换类型，只 `catch (DuplicateKeyException)` 会静默落空，AGENTS.md §5.2 / ADR-D53）。`.../service/impl/AppPayOrderInternalServiceImpl.java:78-80`、`:190-196`
- **关单（`close-unpaid`）带 `PAY_STATUS='0'` 白名单，影响 0 行也算成功**：行不存在、已支付、已失败都是正常结果，关单语义是「保证乘客付不了」，目标已达成即返成功。**NEVER 把 0 行当失败返回** —— 调用方会当成可重试、白重推。`.../service/AppPayOrderInternalService.java:57-59`、`.../service/impl/AppPayOrderInternalServiceImpl.java:98-99`
- **支付结果回查查不到时返 `found=false`、不抛异常**：查不到是真实的业务事实，调用方据此落 ERROR 或补登记；抛异常只留给「本模块自己出错」。`.../service/AppPayOrderInternalService.java:62-67`
- **`/internal/app-order/**` 当前无鉴权，是有意为之的临时降级**（用户 2026-09-14 裁决，与本模块 `/internal/recon` 保持一致）。它是状态变更型接口，与 AGENTS.md §5.2 冲突：任何网络可达方都能按订单号给他人建单或关掉他人的待支付单。**上线前 MUST 补鉴权，方式对齐现有验签实现，NEVER 自造签名逻辑**。`.../service/AppPayOrderInternalService.java:90-93`
- **APP 单程票订单号是 20 位** = `ProductType(2)` + `yyyyMMddHHmmss(14)` + 序列(4)。2026-09-11 由「`00` + 时间 + UUID 前 8 位」的 24 位改成 20 位，与 BOM（`BomOrderServiceImpl.generateOrderNo`）和新服务 face-pay 完全同口径；前缀仍是 `ProductType.ordinaryTicket = "00"`，历史 24 位订单不受影响。`.../service/impl/AppOrderServiceImpl.java:387-396`
- **20 位长度由序列属性兜住**：`ORDER_NO_SEQ` 实测 `MAX_VALUE=9999` + `CYCLE=Y`，序列值永远不超过 4 位。**NEVER 把该序列改成不循环或放大上限**，否则订单号会超过 20 位。`.../service/impl/AppOrderServiceImpl.java:393-395`

#### 四、退款

- **`refundByAmount` 是 2026-09-14 新增的「按指定金额退款」入口**，用于补退「已部分退款的剩余部分」。与 `requestRefundTicket` 的**唯一区别是金额来源**：后者取 `TBL_TVM_APP_ORDER.PAY_AMOUNT` 全额，对已退过一部分的订单必然超额、被支付中心拒（2026-09-14 实测订单 `00202609111609085124` 付 600 已退 200，走旧接口会按 600 提交）。`.../service/AppOrderService.java:70-90`
- **旧接口 `requestRefundTicket` 按原样保留、NEVER 改它的金额来源**：它是 APP 对外契约的一部分，APP 端只传 `orderNo`，加字段等于改契约。`.../service/AppOrderService.java:44-45`
- **`refundByAmount` 自带超退闸门，这是它存在的主要价值**：可退余额 = `PAY_AMOUNT` 减 `AppRefundOrderMapper.sumSuccessRefundAmount`，入参超过余额直接拒、**不发起支付中心调用、不落退款单**。**NEVER 为了「让运营能强退」把它去掉**。`.../service/AppOrderService.java:47-50`
- **三道校验全部在调支付中心之前完成，被拒时不落退款单、不发网络请求**：订单存在且 `PAY_STATUS='1'`（**白名单，NEVER 写成「非失败即可退」**）、金额为正整数、金额不超过可退余额。`.../service/impl/AppOrderServiceImpl.java:534-541`
- **本模块所有退款入口都不做幂等**：同一订单连调两次会退两次（退款操作手册铁律 2）。调用方 MUST 自己控制只调一次，中断后 MUST 先查 `TBL_APP_ORDER_REFUND` 再续跑。`.../service/AppOrderService.java:52-53`
- **退款发起成功不修改退款状态**：状态要等支付中心的退款结果通知进 `receiveRefundResult` 时才改。`.../service/impl/AppOrderServiceImpl.java:663`
- **NEVER 用 `refundTime.substring(0, 8)` 取退款日期**（2026-08-27 生产事故）：支付中心返回的退款时间是 ISO 形态 `2026-08-27T12:55:57`，截前 8 位得到 `2026-08-`，APP 侧解析 `refundDate` 直接失败（订单 `0020260827131329f68e95f4`）。现行做法是**剥掉所有非数字字符再取前 14 位**，对 `yyyyMMddHHmmss` 与 `yyyy-MM-dd HH:mm:ss` 两种形态都成立。`.../service/impl/AppOrderServiceImpl.java:712-721`
- **`refundType` 按发起方分值域**：APP 主动退款用 `00`（与 `doRefund` 给 APP 的应答一致），TVM 故障退款仍为 `01`。`.../service/impl/AppOrderServiceImpl.java:651`
- **退款金额与状态的库内取值**：BOM 退款单状态 `'1'` 退款成功 / `'2'` 退款失败，通知记录状态 `'0'` 待处理。`.../service/impl/BomOrderServiceImpl.java:885-892`、`:427-437`

#### 五、通知与定时任务

- **查到退款终态后 MUST 通知一次 APP，顺序是「先落通知记录（`status=0`）再同步 push」**：push 成功置 `1`、失败置 `2` 交给 `NoticeAppTask` 重试。`.../service/impl/AppOrderServiceImpl.java:639-640`
- **NEVER 再用 `businessType` 把 APP 主动退款排除在通知之外**（2026-08-27 生产事故）：APP 退款走 `BusinessTypeEnum.APP_REFUND`（由 `requestRefundTicket` 传入），而原代码只放行 `TVM_SCAN_QR_TAKETICKET`，导致订单 `00202608271248044c519a98` 退款已成功、`TBL_APP_ORDER_REFUND.REFUND_STATUS=1`，但 `TBL_NOTICE_APP_REFUND_RECORD` 零条、`NoticeAppTask` 每轮扫到 `size=0`，**APP 永远停在「退款进行中」**。`.../service/impl/AppOrderServiceImpl.java:641-645`
- **通知报文与落库 MUST 用给 APP 的取值域（`SUCCESS` / `FAIL`），NEVER 用 `ItpStatusEnum` 的 `1` / `2`**：`NoticeAppTask` 重试时把库里的值**原样再推一次**，存 `1` / `2` 会让重试报文与首次报文取值不一致。同理 **NEVER 写死成功** —— `dealAppRefundResult` 在退款失败分支同样返回 `true`。`.../service/impl/AppOrderServiceImpl.java:647`、`:694-696`
- **退款时间列 MUST 存与首次通知报文完全一致的 `yyyyMMddHHmmss`**：`NoticeAppTask:110` 重试时直接把本列的值原样再推，存 `yyyyMMdd`（8 位）会让重试报文的 `refundDate` 变成当天日期、APP 侧解析失败。`.../service/impl/AppOrderServiceImpl.java:699-701`
- **首次通知的 `retryTimes` 写死 `1`**，因为该处明确是第一次发送。`.../service/impl/AppOrderServiceImpl.java:655`
- **推送与解析 MUST 兜住全部异常**：该方法在 `tvmexecutor` 线程里跑，抛出去没人接，通知记录会卡在 `status=0` 且 `retryTimes` 不递增，`NoticeAppTask` 反复扫到同一条。`.../service/impl/AppOrderServiceImpl.java:761-762`
- **BOM 退款结果查询走独立线程池异步轮询**（`executor.execute` + `tvmCommonService.getPayCenterRefundResult`），不占用请求线程。`.../service/impl/BomOrderServiceImpl.java:658`

#### 六、公共服务 / 事务与线程模型

- **collect-pay 在日终对账里的本源标识是 `collect-pay`，只负责 ITP.PAY 与 ITP.BUS 两类文件**；本源标识是跨服务契约的第一段字段值，**NEVER 改**。`.../service/ReconExportService.java:24-52`、`:58`
- **ITP.DETAIL 是虚拟电子多日计次票专用文件，本模块 NEVER 产出**：甲方规格《ACC与ITP之间的文件》第一章第 4 节明确 DETAIL 口径与 TVM / BOM 的非现金收款无关，已改由 gate-txn-pay 与 daily-ticket 负责。收到 `DETAIL` 或 `EXP` 指令只记 warn 并跳过，**NEVER 拿本模块的四张收款表去凑 DETAIL 行** —— 凑出来的是错账文件，而纯文本分片没有 schema、下游读不出异常。`.../service/ReconExportService.java:203-208`
- **对账覆盖四张收款主表，只导成功单**：`TBL_TVM_ORDER_PAY`（TVM 扫码购票）、`TBL_TVM_ORDER_TOPUP`（TVM 扫码充值）、`TBL_TVM_APP_ORDER`（APP 在线购票）、`TBL_BOM_ORDER_PAY`（BOM 非现金收款），四张表只导 `STATUS` / `PAY_STATUS` 为 `'1'` 的行（`ItpStatusEnum`）。`.../service/ReconExportService.java:210-213`
- **抽取绝不能跑在请求线程上，MUST 派给固定大小的平台线程池**：全服务默认 `spring.threads.virtual.enabled=true`（`resource/micro/web/src/main/resources/web.properties:6`），Tomcat 处理线程是虚拟线程；JDK 21 未落地 JEP 491，虚拟线程在 `synchronized` 内阻塞会 **pin 住载体线程**，而 ojdbc8 的 `PhysicalConnection` / `OracleStatement` 大量方法是 `synchronized` —— 一条 60s 慢 SQL 就是 60s 的 pin。载体线程池 parallelism 默认等于容器可见 CPU 数，CPU limit 偏小时一两条慢 SQL 即可 pin 满，**全 JVM 虚拟线程停止调度**，连 WebClient 响应的续体都唤不醒。对账抽取是区间全扫级别的批处理，放在请求线程上等于必然触发该故障；控制器只负责受理与立即返回（`new Thread(...)` 造出的就是平台线程）。`.../service/ReconExportService.java:215-223`、`:102-107`
- **并发控制不依赖任何中间件**（本项目不用 Redis / MQ）：同一 `batchId` 的在途标记放在 `ConcurrentHashMap.newKeySet()` 里，`add` 成功才受理，`finally` 里移除；单副本内足够，多副本由 recon-server 按批次分发保证只下发一次。受理返 `false` 表示同批次上一轮仍在执行，**是限流不是失败**。`.../service/ReconExportService.java:225-227`、`:102-107`
- **抽取线程数 `recon.export.worker` 默认 1（串行），刻意不放大**：抽取是重 IO 的区间扫描，并发只会互相抢 Oracle 的 IO 与 Druid 连接，反而拖慢联机链路。`.../service/ReconExportService.java:85-89`
- **一类文件的失败 NEVER 中断整轮**：每类文件独立 try-with-resources，某类抛异常时 `ReconPartSink.close()` 自动向 recon-server 声明该类失败，catch 住后继续跑下一类。PAY 与 BUS 是两份独立文件，一类挂掉不该拖累另一类，否则重跑成本翻倍。`.../service/ReconExportService.java:132-144`
- **抽取方法及其调用链刻意不加 `@Transactional`**：每类一次查询、每条 SQL 自动提交。若把整轮抽取包进事务，事务时长等于抽取时长，期间还夹着 `sink.write` 触发的分片上送（HTTP 网络调用），而事务内发起 RPC 是本项目明令禁止的 —— 连接被 Druid `remove-abandoned-timeout` 判定为泄漏后强杀、`commit` 抛 connection closed、整轮白跑。抽取是纯只读，本来也不需要事务。`.../service/ReconExportService.java:262-266`
- **ITP.PAY 每行固定 21 段 = 前 5 段键 + 后 16 段度量，字段顺序来自甲方规格、NEVER 调整**：键 0 日期 / 1 线路 / 2 车站 / 3 设备编号 / 4 支付方式；度量 5+6 BOM 与 TVM 发售笔数与金额、7+8 BOM 与 TVM 充值、9+10 旅游票、11+12 过闸、13+14 APP 购票、15+16 BOM 行政处理、17+18 单边交易、19+20 BOM 处理。`.../service/ReconExportService.java:167-218`、`:61`、`:64`、`:67`、`:70`、`:73`
- **本模块只填三组度量，每行只有一组非 0，其余 13 段写字面 `0`**：BOM 与 TVM 发售（5/6）← `TBL_TVM_ORDER_PAY` + `TBL_BOM_ORDER_PAY`；BOM 与 TVM 充值（7/8）← `TBL_TVM_ORDER_TOPUP`；APP 购票（13/14）← `TBL_TVM_APP_ORDER`。旅游票（9/10）与过闸（11/12）本模块没有对应业务，单边交易（17/18）属 ITP.EXP 口径。`.../service/ReconExportService.java:285-292`
- **NEVER 只拼「键 + 本组两段」这种短行**：段数不足时 recon-server 按下标取度量列会整体错位，而纯文本没有 schema、不会报错，只会静默出错账。正确做法是先把 16 段度量全部填 `0L`，再按组下标覆盖两段（金额下标固定为笔数下标 + 1）。`.../service/ReconExportService.java:244-259`
- **ITP.PAY 第 1 段线路本模块一律传 `null`**：2026-09-16 起线路段改由 recon-server 在聚合完成、写文件之前按车站码统一补齐（`ReconStationMapper` + `recon.line-backfill.*`），原先 `LEFT JOIN STATION_INFO S ON S.STATION_CODE = T.IN_STATION_CODE` 取 `LINE_CODE` 的写法已整段删除。收口理由：线路是车站的函数，四个源各写一遍等于「线路怎么取」有四份副本，且 `STATION_INFO` 的 owner 不是本模块。**NEVER 把 join 加回来**（recon-server 侧是无条件覆盖，加回来不改变产出、只让口径重新分叉）；**NEVER 用车站码前缀之类的规则去猜线路**（猜错等于把汇总账挂到错误线路上）。形参保留只为让 21 段的段位在签名上可见。`.../service/ReconExportService.java:301-310`、`:330-332`
- **TVM 充值与 BOM 两组的线路段仍为空，但成因已变**：recon-server 是按「车站段」反查维表补线路，而 `TBL_TVM_ORDER_TOPUP` / `TBL_BOM_ORDER_PAY` 连车站码列都没有、车站段本身就空。待甲方补数据源后，本模块只要把车站段填上线路段就会自动补齐，**届时不需要在本模块加任何 join**。`.../service/ReconExportService.java:312-317`
- **同键分组重复出行是允许的**：四张表可能对同一天同一设备同一通道各产出一行（购票与充值各一行），recon-server 按前 5 段键做二次聚合、对 16 个度量列逐列累加。BUS 同理，按 1 段键（日期）二次聚合、三个度量逐列累加。`.../service/ReconExportService.java:318-319`、`:352-353`
- **ITP.BUS 只有 4 段** = 1 段键（日期）+ 3 段度量，行格式 `日期|对账金额|付款金额|优惠金额`，字段顺序来自甲方规格、NEVER 调整。本模块对账金额与付款金额都取该表金额列之和，**优惠金额恒 0**。`.../service/ReconExportService.java:275-290`、`:313`
- **优惠段填 0 是核对过 DDL 的结论**：`collect-pay-server/sql.txt` 里那四张表**都没有任何优惠或折扣金额列**。**NEVER 拿票价与实付之差去推算优惠** —— `TICKET_PRICE * TICKET_NUM` 与实付的差额可能来自分单、退款或脏数据，算出来的不是优惠。`.../service/ReconExportService.java:345-350`
- **`resultType=java.util.Map` 下 `COUNT` 与 `TO_NUMBER` 的结果一律是 `BigDecimal`，直接强转 `(Long)` 会抛 ClassCastException**，因此有一个私有的金额转 long（单位分）方法；它只服务本类，不是新建公共工具类。`.../service/ReconExportService.java:319-325`
- **Map 取字符串时 `null` 保持 `null`**，由 `ReconRecord.line(Object...)` 统一转空串（表无该列时车站段 / 设备段也一律传 `null`）。`.../service/ReconExportService.java:343`、`:333-334`
- **完整性冲突判定 MUST 沿 `getCause()` 链，NEVER 只看最外层类名**：本模块开了 tracing，`resource/micro/web` 的观测切面历史上会把异常重新包一层，只 `catch (DuplicateKeyException)` 会静默落空（AGENTS.md §5.2 / ADR-D53）。`.../service/impl/AppPayOrderInternalServiceImpl.java:190-196`
- **时间格式在两个方向上不同**：给 APP 的通知与库内退款时间是 `yyyyMMddHHmmss`（无分隔符），而 `TvmCommonServiceImpl` 内部对非空时间统一转成 `yyyy-MM-dd HH:mm:ss`。跨这两层传时间 MUST 显式转换。`.../service/impl/TvmCommonServiceImpl.java:109`、`.../service/impl/AppOrderServiceImpl.java:712-721`


### 附二.3 service 层（下）：TVM 公共服务、取票挂起、退款与上报、死代码块里的判据

#### 一、TVM 公共服务与票务

- **取票授权查不到激活订单时 MUST 挂住这次请求、NEVER 立即返回**：TVM 每轮取票只发**一次** `requestTakeTicketAuth`（自己出码后约 1 秒，之后不再轮询），而 APP 扫码激活要等用户操作、实测滞后 4~8 秒，于是 TVM 那一次查询必然落在激活之前，拿到「无激活的订单」后直接终止、票永远打不出来；TVM 厂商不改轮询逻辑，因此在 ITP 侧挂起等待激活事件。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/support/TakeTicketWaiter.java:13-26`、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTakeTicketServiceImpl.java:54-58`。
- **挂起时长 NEVER 配到 15000 以上**：`tvm.takeTicket.waitMillis` 默认 10000，上限受 istio 路由超时约束 —— `fep-app-vr` 的 `/itptvm/` 路由没配 timeout、走 Envoy 默认 15 秒，超了 TVM 收到的是网关 504（不是我方报文）。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTakeTicketServiceImpl.java:54-60`。
- **兜底回查间隔 NEVER 小于 500ms**：`tvm.takeTicket.pollIntervalMillis` 默认 500；内存唤醒之外还要查库兜底，防止漏唤醒，但间隔过小会因 JDBC 阻塞 pin 住载体线程。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTakeTicketServiceImpl.java:62-64`。
- **挂起 NEVER 放进带 `@Transactional` 的方法**：行级锁会被持有整个等待时长，这正是 AGENTS.md §5.2 记录的 2026-08-26 生产事故形态。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/support/TakeTicketWaiter.java:24-25`。
- **唤醒走进程内存事件，成立前提是本服务单副本**：`signal` 只在激活成功（`updateAppOrder` 影响行数大于 0）后调用，没有等待者时什么都不做；「激活落在别的副本」或「signal 与 await 之间的窗口」由调用方按固定间隔回查数据库兜底。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/support/TakeTicketWaiter.java:21-22`、`:76`、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTakeTicketServiceImpl.java:91`。
- **等待键就是二维码三要素 `deviceId|qrcodeGenDate|randomFact`，与 `selectByDeviceAndQrcode` 的查询条件一一对应**：改查询条件 MUST 同步改 key，否则 signal 与 await 永远对不上。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/support/TakeTicketWaiter.java:39`。
- **挂起有全局额度，超限 MUST 退回「立即返回」的原有行为**：`tvm.takeTicket.maxWaiting` 默认 500，`tryAcquire` 返 false 即不再等待，防止设备异常时把线程占满；等待结束 MUST `discard(key)` 清理 map。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/support/TakeTicketWaiter.java:35`、`:44`、`:84`。
- **「无激活订单」有两种报文形态，NEVER 混用**：三要素查不到行时回 `2003`（`NO_ACTIVE_ORDER`）；查到行但未激活时回**带空订单对象**的失败结构（原实现把这里的 2003 注释掉了，**NEVER** 顺手改回去）。查到多行则回「查询到的订单数量过多，失败」。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTakeTicketServiceImpl.java:180-184`、`:154-159`。
- **激活取票的前置状态是白名单式的「支付成功」**：原支付订单 `payStatus` 不等于 `SUCCESS` 一律回「该订单非支付成功，不可激活」；激活成功即把 `activateFlag` 置 `ACTIVATE_ED` 并回写 `deviceId` / `qrcodeGenDate` / `randomFact` / `activeTime`。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTakeTicketServiceImpl.java:80-83`。
- **支付结果查询有三个终态短路，`UNPAID` 也是终态**：`SUCCESS` / `FAILED` / `UNPAID` 直接按库中状态返回，不再问支付中心；「未支付」的语义是「轮询结束后没有支付则认为未支付」。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:306`、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTopupServiceImpl.java:186`。
- **查不到支付结果或结果为空时，一律按「支付中 / 已下单」返回，NEVER 按失败返回**：购票与充值两条链路口径一致。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:359`、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTopupServiceImpl.java:241`。
- **BOM 下单转发给 TVM 时会把 BOM 的 `retCode` / `retMsg` 覆盖成 TVM 定义的键值**：排查「设备侧看到的码与 BOM 侧日志不一致」MUST 先看这一层转译，NEVER 认为是 BOM 返错。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:155`。
- **支付结果通知按前置单 `TRANS_TYPE` 分派，且 MUST 用支付中心传来的 `merchantOrderNo`（即 ITP 订单号）查前置单**：用支付中心自己的 `orderNo` 查必然查不到。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderPreServiceImpl.java:119-120`。
- **订单详情的 `orderStatus` 是派生值，`7`（已退款）的唯一判据是原支付订单 `RSV2` 非空**：映射为 `PAYING`=1、`SUCCESS`=2，`SUCCESS` 且 `RSV2` 非空则改判 7；前提是「只有支付成功后才可以发起退款」。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:833`、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTopupServiceImpl.java:630`。
- **出票主记录用 `notifyType` 区分两条上报链路**：`0` 出票结果通知、`1` 出票故障通知；故障单额外落 `faultSlipSeq` / `errorCode` / `errorMessage`，明细逐条写 `TvmSubTicket`。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:408`、`:613-632`。

#### 二、退款与出票上报

- **「退款结束」的判据是 `doRefund` 返 true / `refundNo` 非空，而不是「退款成功」**：注释原文即「退款结果可以是成功的也可以是失败的」，因此这个分支 NEVER 用来推断退款成功。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:700`、`:740`、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTopupServiceImpl.java:338`、`:495`、`:668`。
- **退款只回写原支付订单的 `RSV2`（退款记录 ID），NEVER 改原支付状态**：购票、充值、按票退款三处一致；下游据「`RSV2` 非空」判「已退款」，因此 `RSV2` 被别的用途占用即打挂订单详情。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:702`、`:743`、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTopupServiceImpl.java:340-344`、`:670-674`。
- **退款金额 = 票价 × (购票数 − 实际出票数)，用 `BigDecimal` 相乘、NEVER 用 double**：`handleRefund` 在 `buyNum` 不大于 `actualNum` 时直接返 false 不发起退款。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:636-637`、`:687-713`。
- **退款时送给支付中心的是支付中心的订单号（`payCenterOrderNo`），不是 ITP 订单号**。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:648`、`:697`。
- **出票故障通知按业务类型走两条不同的退款入口**：扫码取票（`TVM_SCAN_QR_TAKETICKET`）走 `appOrderService.doRefund`（APP 单口径），其余走 `tvmCommonService.doRefund` 包装的 `handleRefund`。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:650-658`。
- **APP 要的「取票时间」TVM 报文里没有，取的是故障时间 `faultOccurDate` 的前 8 位**：改这个字段 MUST 同步核对 APP 侧对该字段的解析长度。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:642-643`。
- **设备主动退款（`requestRefund`）的前置状态是白名单「支付成功」**：非 `SUCCESS` 一律返 `9999「订单状态不是支付成功,不能退款」`；订单号为空同样返 `9999`。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:721-737`、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTopupServiceImpl.java:660-664`。
- **充值失败通知里 `topupStatus='01'` 即触发全额退款**，退款金额取原单 `transAmount`，退款单号来自 `OrderCommonUtils.getRefundNo()`。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTopupServiceImpl.java:330-345`。
- **退款状态取值只有三类，「退款中 / 不明确」MUST 不做处理**：`REFUND_SUCCESS`（1）、`REFUNDING_FAIL`（2），其余状态既不回写也不停止轮询。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmCommonServiceImpl.java:246`、`:384`（注释形态）、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTopupServiceImpl.java:442-448`（注释形态）。
- **退款结果轮询借用 BOM 的超时参数、且在请求线程上 `Thread.sleep`**：`bom.payTimeOut` / `payTimeInterval`，注释原文「这里借用 bom 的时间设置」；虚拟线程下这属于「请求线程长时间阻塞」，扩大轮询窗口前 MUST 评估 pin 风险。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmCommonServiceImpl.java:219-221`、`:252`。

#### 三、通知与定时任务

- **通知记录三态 `NOTICE_INIT` / `NOTICE_SUCCESS` / `NOTICE_FAIL`，判成功的唯一依据是 APP 返回的 `retCode` 等于 `AppCodeEnum.SUCCESS`**：落库先写 `INIT` + `retryTimes=0`，发送后按 `orderNo` 回写状态与 `retryTimes`。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:456-465`、`:489-499`。
- **「首次发送」的 `retryTimes` 写死为 1，落库那行写的是 0，两者不是同一个值**：注释原文「这里很明确是第一次发送所以 retryTimes 设置为 1」；核对重试次数 MUST 分清「入库初值」与「本次发送标记」。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:530-531`、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmCommonServiceImpl.java:192`（注释形态）。
- **取票结果通知与出票故障通知各有独立的表与 mapper 方法**：`insertTakeNotice` / `updateTakeNoticeByOrderNo` 与 `insertTakeFailureNotice` / 对应回写；故障通知额外带 `takeTiketFaultReason` 与 `refundAmount`。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:461`、`:498`、`:526`、`:536-559`。
- **退款结果通知 APP 的整条链路（落库 + 发送 + 状态回写）在 TVM 公共服务里已整段注释、当前不发**：原设计是 `refundType='01'`、`refundDate` 取 `yyyyMMdd`、通知地址键 `pay.center.notice-app-refundresult-url`，且「通知写在退款逻辑里，是因为只有拿到准确退款结果后才能通知 APP」。判断「TVM 退款有没有通知 APP」MUST 看这段是否仍被注释，NEVER 据方法名存在就认为在跑。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmCommonServiceImpl.java:188-194`、`:262-322`。
- **退款结果通知的落库字段直接写终态成功值**：注释版 `saveNoticeAppRefundResultRecord` 无条件写 `REFUND_SUCCESS` 的 code 与 desc，与实际退款结果无关 —— 复活这段代码前 MUST 改成按真实结果填。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmCommonServiceImpl.java:267-268`。

#### 五、其它服务

- **`TransforUtils.getStringFromData` 是取支付中心 `data` 字段的统一入口，私有副本已被注释删除，NEVER 再抄一份**。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:786-788`、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:220-223`。
- **退款单号只有一套在用的生成规则**：`OrderCommonUtils.getRefundNo()` 与 `OrderNoUtils.generateRefundNo(seq)`（走 `orderSeqMapper.nextval()`）；旧的 `"RF" + 时间 + UUID 前 6 位` 已注释废弃，NEVER 复活（无序列、无唯一性保证）。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTopupServiceImpl.java:423`、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:682-685`。
- **支付中心下单 bizData 的骨架已收口到 `payCenterCommon.buildXxxRequest`**：注释版留下了当年的取值 `scene=TOPUP` / `paymentVendor=QR` / `industryType=1` / `amount` 为整数分 / `subject=票卡充值` / `orderTimeOut=DEFAULT_ORDER_TIMEOUT`，改出向报文 MUST 改 `payCenterCommon`，NEVER 在服务实现里重新拼一份。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTopupServiceImpl.java:389-408`。
- **订单详情返回的站点名当前填的是站码，是已知未闭合项**：`singlePickupStationName` / `singleGetoffStationName` 都取 `inStationCode` / `outStationCode`，源码带 todo「需改为中文名」。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:816-821`。
- **`regDate` / `payDate` 的格式是把 `yyyy-MM-dd HH:mm:ss` 去掉横线、冒号、空格后的 14 位串**，不是独立字段，改库中时间格式会直接改坏对外报文。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:824`、`:832`、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTopupServiceImpl.java:621`、`:629`。
- **`payNotice`（支付结果通知）对已支付成功 / 已支付失败的单直接返成功、不重复处理，对「不明确」的状态返 fail 让支付中心重推**：这是该链路的幂等形态，NEVER 改成无条件 UPDATE。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:856-892`。


### 附二.4 mapper（Java 接口 + XML）、对账导出 SQL 与 `application.yml`

#### 一、mapper Java 接口约束

- **退款超退闸门只累计「已成功」的退款行**：该汇总方法只统计 `REFUND_STATUS='1'`，失败（2）与退款中（0）都不占额度；唯一用途是算「可退余额 = 支付金额 减 本方法返回值」，给指定金额退款做超退闸门。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/mapper/AppRefundOrderMapper.java:48-61`
- **`REFUND_AMOUNT` 是 `VARCHAR2`，库里有历史脏值，SQL 内 `REGEXP_LIKE` 只取纯数字行、NEVER 删**：2026-09-14 实测 BOM 侧有 `'0'`、也有付 1 分却记 300 的行；`TO_NUMBER` 一旦撞到脏值就抛 `ORA-01722`，整个退款请求会连「留证据」的落库一起失败。`AppRefundOrderMapper.java:48-61`
- **关单 MUST 用带状态白名单的那个方法（WHERE 里有 `PAY_STATUS='0'`），NEVER 图省事换成 `updateByOrderNo`**：后者的 WHERE 只有 `ORDER_NO`，会把已支付成功的行一起改掉；乘客付款与上游关单之间有毫秒级竞态，无条件更新会把「已收到的钱」抹成支付失败，钱收了却没人销账。`TvmAppOrderMapper.java:50-62`
- **关单影响 0 行是正常结果，调用方 MUST 当成功返回、NEVER 当失败重试**：关单语义是「保证乘客付不了」，该行已是终态时目标已达成。`TvmAppOrderMapper.java:50-62`
- **动态更新一律 Map 传参，且参数里 MUST 含主键键名**：退款单是 `refundNo`（`AppRefundOrderMapper.java:39-45`、`BomRefundOrderMapper.java:39-45`），通知是 `notifyId`（`BomBusResultMapper.java:39-45`），BOM 订单是 `orderNo`（`BomNoCashOrderMapper.java:38-44`），TVM APP 订单是 `orderNo`（`TvmAppOrderMapper.java:41-47`）。
- **BOM 充值结果通知的入参键清单是固定 8 项**：`orderNo` / `ticketLogicNum` / `ticketPhysicsNum` / `transDate` / `transAmount` / `afterAmount` / `topupStatus` / `transType`。`BomTopupResultMapper.java:16-20`
- **BOM 子票查询刻意复用 `TvmSubTicket` 接收**：理由是 `TvmSubTicket` 与 `BomSubTicket` 字段一样。`TvmSubTicketMapper.java:31`
- **运营端分页查询 TVM 当面付订单的 `offset` / `limit` 由页面接口控制、不在 mapper 内兜默认值**。`TvmOrderMapper.java:33`
- **按订单号查询类方法的契约是「不存在返回 null」**（不是空对象、不抛异常），退款单、通知、订单三类 mapper 一致。`AppRefundOrderMapper.java:15-20`、`BomBusResultMapper.java:15-20`、`BomNoCashOrderMapper.java:20-25`、`TvmAppOrderMapper.java:18-23`

#### 二、mapper XML 与 SQL 约束

- **NEVER 在 SQL 正文里写行注释或块注释**：Druid WallFilter 的 `commentAllow=false`，SQL 正文出现注释会被判定为注入、该语句**静默失效**；注释 MUST 写在 mapper XML 的注释里。三处告示：`collect-pay-server/src/main/resources/mapper/AppRefundOrderMapper.xml:55-61`、`mapper/TvmAppOrderMapper.xml:119`、`mapper/ReconExportMapper.xml:5-73`
- **XML 注释文本内也 NEVER 出现连续两个半角减号**，否则 XML 解析直接报错（`ReconExportMapper.xml` 的共同约定里把这条与上一条并列写明）。`mapper/ReconExportMapper.xml:5-73`
- **关单 SQL 的白名单谓词写在 XML 里、逐条标注**：`WHERE` 带 `PAY_STATUS='0'`，NEVER 换成同文件上方那条无条件的 `updateByOrderNo`。`mapper/TvmAppOrderMapper.xml:118`
- **金额与时间列都是 `VARCHAR2`，Oracle 写法固定成组**：金额 `SUM` 一律套 `TO_NUMBER` 再包 `NVL`（`SUM` 跳过 `NULL`，全组皆 `NULL` 时返 `NULL`），NEVER 省掉 `TO_NUMBER` 靠隐式转换（逐行隐式转换口径不可控，遇脏值直接 `ORA-01722`）。`mapper/ReconExportMapper.xml:5-73` 第 5 条
- **混合大小写列名一律回避不引用**：`totalPrice` / `merchantOrderNo` / `PAYCENTER_channelOrderNo` 在 Oracle 里必须带双引号才能命中，不带引号会 `ORA-00904`。`mapper/ReconExportMapper.xml:5-73` 第 6 条、`mapper/ReconExportMapper.java:186-188`
- **列名一律带主表别名 `T`**：现在四条 select 都是单表查询，带前缀不再是为了避免 `ORA-00918`，而是为了下次引入第二张表时不必回头改列名。`mapper/ReconExportMapper.xml:5-73` 第 7 条末段

#### 三、对账导出 SQL

- **`ReconExportMapper` 刻意独立、不复用任何联机链路的 mapper**：对账抽取是批处理口径（扫时间窗口、只取成功单、库内 `GROUP BY`），与联机查询的口径和索引策略完全不同，混进现有 mapper 后任何一方调过滤条件都会误伤另一方；现有 mapper 与 XML 一行都没改。`mapper/ReconExportMapper.java:9-35`
- **本模块只产出 `ITP.PAY` 与 `ITP.BUS` 两类汇总，全部是聚合查询、没有明细分页**。`mapper/ReconExportMapper.java:9-35`、`mapper/ReconExportMapper.xml:5-73` 第 1 条
- **NEVER 改成把明细拉回 Java 再聚合**：聚合后只有几百到几千行，分页反而要把同一个聚合跑多遍，拉明细等于把全量搬进堆内存。`mapper/ReconExportMapper.java:39-58`
- **时间窗口左闭右开、直接做字符串比较、NEVER 套 `TO_DATE`**：四张表的时间列都是 `VARCHAR2(100)` 存 `yyyy-MM-dd HH:mm:ss`（写入值来自 `utils/DateUtils.getNowTime()`），该格式下字典序等价于时间序，绑定用 `jdbcType=VARCHAR`；套函数会让索引失效。参数取 `ReconExportReqDTO.getWindowStartDashed()` / `getWindowEndDashed()`。`mapper/ReconExportMapper.java:9-35`、`mapper/ReconExportMapper.xml:5-73` 第 2 条
- **成功状态是白名单判定，NEVER 写成「非 2」之类的黑名单**：`ItpStatusEnum` 取值 `0` 支付中或退款中、`1` 成功、`2` 失败、`3` 未支付，对账只导 `1`；放行中间态 `0` 会被误当成已收款，本期虚增收入且下期重复入账。`mapper/ReconExportMapper.xml:5-73` 第 3 条
- **状态列名不统一：`TBL_TVM_APP_ORDER` 叫 `PAY_STATUS`，其余三张表叫 `STATUS`**。`mapper/ReconExportMapper.xml:5-73` 第 3 条、`mapper/ReconExportMapper.java:74-88`
- **窗口列与分组列 MUST 同源**：`TBL_TVM_APP_ORDER` 的窗口过滤仍用 `CREATE_TIME` 而不是 `PAY_TIME`，混用会让同一笔在相邻两天的窗口里重复或漏掉。`mapper/ReconExportMapper.xml:129-134`
- **`TXN_DATE` 段固定写法 `REPLACE(SUBSTR(CREATE_TIME, 1, 10), '-', '')`**（`yyyy-MM-dd` 转 `yyyyMMdd`）；该表达式只出现在 SELECT 列表与 `GROUP BY` 里、不出现在 `WHERE` 里，因此不影响索引。`mapper/ReconExportMapper.xml:5-73` 第 4 条、`mapper/ReconExportMapper.java:39-58`
- **四张表的金额列各不相同**：`TBL_TVM_ORDER_PAY` 取 `TOTAL_PRICE`、`TBL_TVM_ORDER_TOPUP` 取 `TRANS_AMOUNT`（本次充值金额）、`TBL_TVM_APP_ORDER` 取 `PAY_AMOUNT`（实付）、`TBL_BOM_ORDER_PAY` 取 `TRANS_AOUNT`。`mapper/ReconExportMapper.xml:5-73` 第 5 条
- **`TRANS_AOUNT` 是 DDL 原文就少一个 M，NEVER 顺手改成 `TRANS_AMOUNT`**，改了就是 `ORA-00904`（证据坐标写在注释里：`collect-pay-server/sql.txt` 第 136 行）。`mapper/ReconExportMapper.java:92-120`、`mapper/ReconExportMapper.xml:152-170`
- **返回类型统一 `Map<String,Object>`，`COUNT` 与 `TO_NUMBER` 的结果在 Map 里是 `BigDecimal`**：取值 MUST 经调用方的转换方法，直接强转 `(Long)` 会 `ClassCastException`；映射成 20 余列的实体等于每行多背一堆 null 字段。`mapper/ReconExportMapper.java:9-35`
- **`ITP.PAY` 21 段里本模块只填 4 组度量**（0 基下标）：`idx 5/6` BOM 与 TVM 发售笔数与金额、`idx 7/8` BOM 与 TVM 充值、`idx 13/14` APP 购票；其余 12 段（旅游票、过闸、BOM 行政处理、单边交易、BOM 处理）本模块一律写字面 0，由 Java 侧填充。`mapper/ReconExportMapper.xml:75-85`
- **`TBL_BOM_ORDER_PAY` 整表计入「发售」组，刻意不按 `TRANS_TYPE` 拆分购票与充值**：这是有意的降级决策，三处仓库内证据互相矛盾（见 `frag_g3_contra.md`）；发售组金额偏大是可解释的口径问题，错拆是错账。甲方给出判据后再拆，届时 MUST 同步改本 select 与 `ReconExportService`。`mapper/ReconExportMapper.java:92-120`、`mapper/ReconExportMapper.xml:152-170`
- **BOM 行政处理（`idx 15/16`）与 BOM 处理（`idx 19/20`）当前恒 0**，成因同上，待甲方明确判据。`mapper/ReconExportMapper.xml:75-85`
- **线路段（`ITP.PAY` 第 2 段）本模块一律不出，2026-09-16 起改由 recon-server 在写文件前按车站码统一补齐**（该模块的 `ReconStationMapper` 与 `recon.line-backfill.*`）；原先两条 `LEFT JOIN STATION_INFO` 已全部删除，**NEVER 加回**（recon-server 侧是无条件覆盖，加回来不改变产出、只让口径重新分叉）。收口理由：线路是车站的函数，四个源各写一遍等于四份副本、四次维表变更暴露面，且 `STATION_INFO` 的 owner 不是本模块。`mapper/ReconExportMapper.xml:5-73` 第 7 条
- **车站段取 `IN_STATION_CODE`、NEVER 取 `OUT_STATION_CODE`**：a）同一行不能自相矛盾，2026-09-11 测试库实测确有 4 笔 `IN=0622` / `OUT=0122`，取 OUT 会让 recon-server 补出线路 01 而不是 06；b）`TBL_TVM_ORDER_PAY` 共 111 行、其中 55 行 `OUT_STATION_CODE` 为 `NULL`（`IN_STATION_CODE` 一行不缺）。`mapper/ReconExportMapper.xml:5-73` 第 7 条、`mapper/ReconExportMapper.java:39-58`
- **NEVER 用车站码前 2 位推线路**：实测前 2 位恰好等于 `LINE_CODE` 是编码巧合、不是契约；收口后这条约束的执行点搬到 recon-server，判据不变。`mapper/ReconExportMapper.xml:5-73` 第 7 条
- **`TBL_TVM_ORDER_TOPUP` 与 `TBL_BOM_ORDER_PAY` 无车站号列，车站段与线路段只能留空，NEVER 拿 `DEVICE_ID` 猜车站或线路**（设备编号与车站码在本项目里没有可验证的换算关系）；待甲方补数据源（例如要求报文上送车站码）或明确接受空值，届时 MUST 同步改 select 与 `ReconExportService`。`mapper/ReconExportMapper.xml:104-113`、`mapper/ReconExportMapper.xml:152-170`
- **`TBL_TVM_APP_ORDER` 的设备编号段由 Java 补空串**：DDL 里没有 `DEVICE_ID`（resultMap 有、DDL 无），因此不引用该列；支付方式取 `PAY_CHANNEL_CODE`。`mapper/ReconExportMapper.java:74-88`、`mapper/ReconExportMapper.xml:129-134`
- **`ITP.BUS` 只有 4 段（日期 + 对账金额 + 付款金额 + 优惠金额），键只有日期一段，NEVER 再带车站 / 设备 / 票种**：多带一段会让 recon-server 按 1 段键聚合时把维度信息当成度量列错位读取。`mapper/ReconExportMapper.xml:186-196`、`mapper/ReconExportMapper.java:124-131`
- **`ITP.BUS` 优惠金额本模块恒 0，NEVER 拿票价与实付之差去推算**：已逐表核对四张表 DDL 均无优惠或折扣金额列。`mapper/ReconExportMapper.xml:186-196`
- **BUS 的四条 select 结果依次写入同一个 sink，同一天出现多行是允许的**，recon-server 按 1 段键做二次聚合累加。`mapper/ReconExportMapper.xml:186-196`

#### 四、配置（application.yml）

- **TVM 取票授权的挂起等待 `waitMillis` MUST 小于 15000**：istio `fep-app-vr` 的 `/itptvm/` 路由没配 timeout、走 Envoy 默认 15 秒，超过就变成网关 504 而不是我方响应。挂起的成因是 TVM 每轮只发一次 `requestTakeTicketAuth`、且比 APP 扫码激活早 4 至 8 秒，厂商不改轮询逻辑，因此查不到订单时把这次请求挂住等激活事件。`collect-pay-server/src/main/resources/application.yml:63-66`
- **本模块打开了 tracing（`management.tracing.enabled=true`）**，目的只是让 MDC 里的 `traceId` / `spanId` 供 VictoriaLogs 检索、span 上报一律不要；打开后两条链路能对上：recon-server 下发 `/internal/recon/export` 带来的 W3C `traceparent`（源头是 `sys_job` 109 日终对账），以及 APP / TVM / BOM 入向请求自身的 `traceparent`。`application.yml:85-90`
- **`log4j2-collectpay.xml` 的 pattern 一直写着 `%X{traceId}`（该文件第 34 行），开关没打开前那一列恒为空**。`application.yml:86-87`
- **NEVER 删除 `spring.autoconfigure.exclude` 里对 `OtlpAutoConfiguration` 的排除**，三条理由：1）`sampling.probability=0` 只让本服务发起的 trace 不采样，采样器是 `parentBased(traceIdRatioBased(0))`，上游带 `sampled=1` 的 `traceparent` / `b3` 进来时仍会采；2）`micro/web` 的 `web.properties` 已把 `management.otlp.tracing.endpoint` 整行注释掉，本行是第二道保险，K8s Deployment 只要注入 `MANAGEMENT_OTLP_TRACING_ENDPOINT` 就会重新激活 exporter；3）Boot 3.2.6 没有 `management.tracing.export.enabled` 这个开关，把 endpoint 置空也不行（`OtlpAutoConfiguration` 只判断键是否存在），只能排掉整个自动配置。口径同 pay-sign-server 与 card-pool-server 的 properties。`application.yml:75-82`
- **`service.recon.url` 是 `kubectl get svc -n itp` 实测的真实 Service 名（recon-server 2026-09-11 已部署）**，且 **Service 端口等于 NodePort 号（30034），不等于容器 `server.port`（9112）**。`application.yml:103-104`
- **内部接口共享令牌默认必须为空、由 K8s Secret 注入 `RECON_INTERNAL_TOKEN`**（该注释与「鉴权已整段删除」的现状冲突，见 `frag_g3_contra.md`）。`application.yml:108`
- **日志走本模块自带的 `log4j2-collectpay.xml`，公共 `log4j2-linux.xml` 不再参与**：`CustomLoggingConfiguration` 见到 `logging.config` 非空即直接 return。目的是用配置级 `RegexFilter` 丢掉设备心跳 `notiDeviceHeard` 的 INFO 日志（占日志量 79%，3000 行里 1992 行），`InternalMicroHttp` 的令牌明文压制也一并搬进该文件的 Logger 段。`application.yml:114-117`、`log4j2-collectpay.xml:2-18`
- **一次心跳产生 4 行、其中 3 行来自公共构件（`FirstFilter` / `MoreInterceptor` / `BodyCacheFilter`）**，只删 Controller 里那句 `log.info` 只能去掉四分之一；不改公共 `log4j2-linux.xml` 是因为它被 21 个模块共用，不用 `logging.level` 压那三个 logger 是因为按类命名、压级别会连业务报文日志一起干掉（报文日志是重放与排障的唯一依据）。形态与 `log4j2-paysign.xml`、`log4j2-web-admin.xml` 一致。`log4j2-collectpay.xml:2-18`
- **该文件由 Boot 在启动早期加载，`webLoggerLevel` / `logPath` / `appName` 等系统属性尚未设置，全部 lookup MUST 自带默认值**；`appName` 固定写 `itpagm`（等于 `spring.application.name`）以保持日志文件名不变；容器内真实日志目录按 Pod 名分子目录。`log4j2-collectpay.xml:2-18`、`log4j2-collectpay.xml:30`
- **NEVER 把 `<Properties>` 之后的任何元素挪到它前面**：2026-09-11 实测把 `<Filters>` 写在 `<Properties>` 之前，整份配置的 `${logPath}` / `${appName}` / `${webLoggerLevel}` 全部不做替换，日志文件被建成字面量路径、Root 级别失效、INFO 全丢，且 `status="off"` 时 log4j2 自身报错也被吞掉，只能靠「文件名里带占位符」反推。`log4j2-collectpay.xml:21-26`
- **配置级 `RegexFilter` 两级短路**：WARN 及以上一律 ACCEPT（心跳链路真出错时仍看得到），其余命中 `notiDeviceHeard` 即 DENY，覆盖公共构件那 3 行与后续新增的心跳日志；`RegexFilter` 默认 `useRawMsg=false`，匹配的是格式化后的整条消息（URL 就在消息里），`matches()` 是全串匹配所以必须写 `.*`，`(?s)` 让点号也匹配换行。`log4j2-collectpay.xml:37-43`
- **VictoriaLogs appender 段 MUST 保留**：与公共配置同口径，`url` 为空时自动禁用、不起 `vlogs-sender` 线程，而 collect-pay 已注入 `VLOGS_URL`，删掉这段本服务日志上报会消失；两级过滤为带 `x-vlogs-capture=1` 的链路全量上报、其余只上报 WARN 及以上。`log4j2-collectpay.xml:89-93`
- **`InternalMicroHttp` 会把整个请求头 Map（含内部令牌明文）打进 INFO 日志，因此压到 WARN**。`log4j2-collectpay.xml:129-132`
- **NEVER 给业务包 logger 里的 VictoriaLogs `AppenderRef` 加 `level` 属性**：`AppenderRef` 的 level 是引用层过滤器，先于 appender 自己的 `ThreadContextMapFilter` 求值，加了就会把 INFO 短路掉；该 logger 的 level 与 appender 引用刻意与 Root 一致，只为让 `x-vlogs-capture` 链路采集生效。`log4j2-collectpay.xml:145-149`


### 附二.5 face-pay-server `src/test` 的断言判据（13 个文件 / 356 行注释，本轮首次纳入）

> 这些注释**不是用法说明，而是「这条断言在防什么」**。断言被放宽时构建照样绿，判据一旦删掉，后人只会看到一堆「看着可以合并 / 可以放宽」的用例。**删注释 MUST 保留每条的一行式说明。**

#### 一、对外契约测试（`api/device/tvm/TvmContractTest.java`）

- **报文字段名与响应 key 一律断言字面量、纯函数不起 Spring**：意义是把「一字不改」钉死 —— 字段名或 retCode 被人「顺手改正」时**立刻红**，而不是等设备联调才发现收不到值。`api/device/tvm/TvmContractTest.java:15`
- **失败响应的键集与键序 MUST 等于成功响应**：逐个断言 `keySet()` 相等而不是数个数（键名写错、键序漂移都要红）；取票鉴权返 `2003` 那支的业务键与成功响应**一模一样、值为 null**。设备侧若按顺序解析，**键序变化同样是契约变化**。`:109`、`:148`
- **null 键 MUST 熬过 JSON 序列化**：Controller 直接返回 fastjson2 `JSONObject`，但本工程**没有引入 fastjson2 的 spring 扩展、也没有自定义 `HttpMessageConverter`**，出站实际由 Spring Boot 默认的 Jackson 当 `Map` 写出。谁哪天加一个 `NON_NULL` 之类的全局配置，那 8 个键会**静默消失、设备侧毫无察觉** —— 这条断言就是那道防线。`:131`、`:232`
- **失败响应的业务值 MUST 是 JSON null，NEVER 填成 `FAILED` / `0` / 空串**：「查不到 / 参数缺失」与「这笔支付失败了」是两件事，后者仍走 `payResult(FAILED, ...)` 与 `paymentResult(FAILED, ...)`；混同会让设备对一笔**可能已扣款**的单子做错误处置。`:189`

#### 二、架构门禁（`arch/F2fGuardTest.java`、`arch/F2fOrderStatusArchTest.java`）

- **`F2fGuardTest` 把两条「只靠注释维持」的约束固化成会失败的构建**：①幂等兜底一律走 `F2fDuplicateKey.isConflict(Throwable)` 沿 cause 链判定（ADR-D84）—— 裸 `catch` 具体异常类型在**打开 tracing 的模块里会静默失效**，本模块尚未开 tracing，所以这条**现在不会在运行时暴露、一旦有人开了才炸**；②`@Scheduled` 的**数量与宿主类数被钉住**（当前 7 个分布在 6 个类，补款那个类里有两个），谁新增一个调度这个测试就红，提醒他一并复核单副本约束与文档口径。判定**在剥离注释后的代码上做**，因为那两处 NEVER 告示本身就写着被禁止的写法。`arch/F2fGuardTest.java:17`、`:37`、`:71`
- **`F2fOrderStatusArchTest` 的两条规则加进来时全绿**，目的是拦住「下一个人退回旧写法」；**规则失败时 NEVER 改规则去迁就代码**，先读 `docs/domain/state-machines.md` §二确认是新写法有理由还是又踩同一个坑。`arch/F2fOrderStatusArchTest.java:23`
- **四个取值域 NEVER 混谈**：该门禁只列**订单域** 11 个取值；票（`ISSUED` / `FAULT`）、支付（`INIT` / `SUCCESS` / `FAILED` / `PROCESSING`）、退款（另加 `MANUAL`）是**另外三个取值域**。允许保留裸字面量的**唯一形态**是常量名带取值域前缀（活样例 `F2fBomOrderService.TICKET_REFUNDING = "REFUNDING"`，它是 `F2F_TICKET.TICKET_STATUS`，与订单状态**拼写相同、取值域不同**）；**NEVER 靠加白名单文件名放行**，那等于把整个类豁免掉。`:44`、`:53`
- **三条 CAS 的返回行数不得被丢弃 —— 这是本组门禁里价值最高的一条**：2026-09-14 首次跑它时，**40 个调用点里有 12 个直接丢掉返回值**，其中 `F2fTvmOrderService` 查询到支付成功那处 `markPaid` 属于「钱已收、状态可能没落上」，是人工逐条 review 时漏掉的。CAS 的返回行数是它唯一的输出，丢掉等于把条件更新退化成无条件更新。认定「接住了」的四种形态：赋给变量，或交给三个解读入口之一（含 `F2fOrderStatusTransition.classify`）；唯一豁免形态是行内显式写 `// CAS-DISCARD: <理由>`，**NEVER 改成按文件名或方法名豁免、也 NEVER 只写标记不写理由**。`:63`、`:67`、`:74`、`:159`
- **为什么这两组门禁扫源码而不用 ArchUnit**：ArchUnit **看不见字符串常量**（`String` 常量池不在它的领域模型内），也**看不到「返回值有没有被使用」**（字节码里那是一条 POP 指令）。因此形态照 `account-server` 那种「离线解析工程文件」的做法，工作目录是模块根、不连库不起 Spring；判定窗口取**匹配行 + 上一行**，因为项目里有同行与跨行两种写法。`:104`、`:149`、`:159`
- **状态字面量收进枚举的收益不在洁癖**：11 个取值由 DDL 的 `CK_F2F_ORDER_STATUS` 授权，散写的字面量**拼错一个字母编译期完全无感**，运行时 CAS 静默返 0 行 —— 表现是「订单永远推不动」而不是报错。`:104`
- **CAS 决策 MUST 留在 `service` 包**：controller / 定时任务直接调等于把状态机决策散到接入层，回查与幂等短路必然被漏写。另注意**本模块服务类直接放在 `service` 包下、没有 `service.impl` 层**，NEVER 照抄 pay-sign-server 的包名。`:81`

#### 三、支付中心收口与补款的行为锁（`service/**`）

- **`F2fPayCenterFlowTest` 存在的理由**：重构前这条骨架**在 7 个 service 里各抄一遍**，而唯一覆盖过它的 `F2fTvmOrderServiceWriteTest` 是 `@Disabled`（要真库）—— 即「支付中心答复 → 本地状态推进」这一步在重构前**没有任何自动化验证**。每个用例对应一条**踩过或差点踩到的坑**，NEVER 删。`service/F2fPayCenterFlowTest.java:37`
- **传输失败时订单一个字段都不许动**（旧实现最大的坑：把「不知道」当成「没付成功」，而钱可能已经扣了）；**`code=0` 但 `data` 为空按 UNKNOWN 收口、不许推 `PAYING`**（推了等于对外宣称「码已经给出去了」，而二维码串根本没拿到）。`:98`、`:116`
- **被拒但 `rejectTransition == null` 时订单必须留在 `CREATED`**：这是 APP 侧既有语义（换个支付通道还能再来一次），重构前它只体现为「`F2fAppOrderService` 里少了两行」、任何人都可能顺手「补齐」；**本用例就是那两行不存在的证据，NEVER 删**。`:146`
- **查到已收款时顺序是「先回写支付流水、再 `markPaid`」**：先有成功的流水、再有 `PAID` 的订单，中途崩了也不会出现「订单说收了钱、流水里查不到那一笔」。**查询没问出结论则本地一律不动状态、也不调 `onPaid`**，调用方随后回「支付中 / 处理中」让对方继续轮询、**NEVER 回失败**；**查到未支付推 `EXPIRED` 而不是 `PAY_FAILED`**（两者是不同终态）。`:179`、`:201`、`:220`
- **付款码链路同步答 `SUCCESS` 时本类什么都不写**，落库让给调用方（各渠道的成功流水列不同）。`:162`
- **写库型测试的自证与还原**：`F2fTvmOrderServiceWriteTest` / `F2fOrderExpireReconcileTest` 靠 `DB_HOST` 环境变量开关（无库环境整类跳过）、只 INSERT 新行、用完按 `orderNo` 先删 `F2F_PAYMENT` 再删 `F2F_ORDER`；支付中心地址**故意指向 `127.0.0.1:1`（必然连不上）**，用来验证「对端没答上来时订单不得被写成失败」。`F2fOrderExpireReconcileTest` 用本机 stub HTTP 扮演支付中心，是**第一次覆盖到「支付中心正常应答」的成功路径**（真实凭据仍未拿到）。`service/F2fTvmOrderServiceWriteTest.java:32`、`service/F2fOrderExpireReconcileTest.java:33`
- **`F2fMapperSmokeTest` 验的是编译期与静态检查都看不到的三类错误**：接口方法名与 XML `statement id` 不一致、`resultMap` 的 `type` / `jdbcType` 非法（这两类启动即报），以及 **SQL 语法或列名错误 + Druid WallFilter 因注释判定注入**（只在真正执行时暴露，是本项目的高频事故形态）—— 所以它**逐条真的执行一次**、不是只启动上下文；全部只读零写入。`mapper/F2fMapperSmokeTest.java:16`
- **`PayCenterResults` 放在生产包内是有意的**：`PayCenterResult` 的两个工厂是**包级私有**（只允许 `PayCenterClient` 造它），**NEVER 为了测试把生产工厂改成 public**；也 **NEVER 改成 mock** —— 它是 `final` 类，本仓库当前的 Mockito mock maker 造不出替身，`when(...)` 会**直接调到真方法**并报 `UnfinishedStubbingException`（2026-09-14 实测）。同一限制导致 `SupplementOrderServiceImplTest` 用构造器注入而不是 `@InjectMocks`。`channel/paycenter/PayCenterResults.java:6`、`service/supplement/SupplementOrderServiceImplTest.java:38`
- **补款侧的语义锁**：本地落单是补款唯一带 `@Transactional` 的类，主表撞唯一索引返 `rejected`（**NEVER 抛异常**，由上层返 `8003` 让 APP 换单号重试）、不相关的 `RuntimeException` 一律向上抛；明细 `ACTIVE_ORIG_ORDER_NO` **恒为 NULL（无独占设计）**；**本地没落成时 NEVER 调 `preOrder`**。`preOrder` 是**同步**发 HTTP（与旧 CollectPay 链路的异步 outbox 不同），因此返回值可立即驱动状态推进、不需要补偿兜底：受理成功推 `PROCESSING`、`Unreachable` 留 `INIT` 且不回滚本地、`BizRejected` 回 `INIT` + `8003`、UPDATE 返 0 行视为幂等**不报错**。`service/supplement/SupplementOrderLocalWriterTest.java:29`、`:56`、`:71`、`:95`、`service/supplement/SupplementPayCenterFlowTest.java:43`、`:110`、`:125`、`:139`、`:151`
- **收敛链的三条判据**：回调 `SUCCESS` 走「推补款单 SUCCESS → RPC 收敛原订单 → 明细 `SETTLED`」；**`converged=false` 且原订单已 `SUCCESS`（被先到的补款单抢先结清）时，明细 MUST 标 `FAILED` + 「重复支付待退款」交对账或人工闭环，NEVER 标 `SETTLED` 蒙混过去**；回查传输失败时**既不推状态也不收敛**。判断分支 MUST 看 `converged` 与 `debitStatus` 两者，**NEVER 只按 retCode 断言**。`service/supplement/SupplementPayCenterFlowTest.java:79`、`:197`、`:234`、`:255`、`:271`
- **`isSupplementOrder` DB 异常降级为 `false`** 是保守选择：不要把非补款单误判成补款单。`service/supplement/SupplementOrderServiceImplTest.java:275`
- **`F2fOrderNoTest` 断言写死字面量的理由**：长度口径尚未与 ACC 对账侧确认，**一旦要回退到 20 位，这些用例就是「回退是否改干净」的判据**。`support/F2fOrderNoTest.java:10`


### 附二.6 `f2f-schema.sql` 的 `COMMENT ON` 列语义与取值域（51 条，阶段二假阴性的补漏）

> **NEVER 再据「文件里没有 SQL 行注释」判断这个 SQL 没有知识**：它的表/列语义与取值域全部写在 `COMMENT ON TABLE` / `COMMENT ON COLUMN` 语句里。下面逐列照 DDL 原文抄录取值域，**未做任何推断补全**；末尾单列「列注释与 `CHECK` 约束不一致」的条目 —— 那类是**改代码时最容易踩的**：注释宽于约束时按注释写入即 `ORA-02290`，注释窄于约束时会漏掉一个合法状态。

> 取值域一律照 `face-pay-server/src/main/resources/sql/f2f-schema.sql` 原文抄录，未在 DDL 中写明的不做推断；`CONSTRAINT CK_*` 的取值域已逐列核对，末尾另列不一致项。

#### F2F_ORDER（表注释：当面付收款单主表）

- `ORDER_NO`：ITP 订单号，含版本标识位 F2；取值域 DDL 未限定；`VARCHAR2(64 CHAR)` `NOT NULL`，唯一索引 `UK_F2F_ORDER_NO`（GLOBAL）。
- `CHANNEL`：受理渠道；取值域 `01` APP、`02` TVM、`03` BOM，`CK_F2F_ORDER_CHANNEL CHECK (CHANNEL IN ('01','02','03'))`；注释另注 STT 未接入且编码口径未定（`DeviceTypeEnum` 为 14、`BaseRequestDTO` 注释为 07），确认后再放开 CHECK 约束；与 `DEVICE_ID` + `DEVICE_SEQ` 共同构成防重下单键 `UK_F2F_ORDER_DEV_SEQ`。
- `BIZ_TYPE`：业务类型；取值域 `01` 购票、`02` 充值、`03` 取票、`04` 非现金收款，`CK_F2F_ORDER_BIZ CHECK (BIZ_TYPE IN ('01','02','03','04'))`。
- `TRANS_TYPE`：BOM 交易类型；取值域 `02` 超时更新、`03` 超程更新、`04` 未出站更新、`05` 无入站更新、`06` 退卡退票、`22` 充值、`2A` 黑名单锁定、`2B` 锁定解除、`42` 行政处理；DDL 内**无 CHECK 约束**。
- `ADMIN_TRANS_TYPE`：行政交易类型；取值域 `01` 至 `0A`；`TRANS_TYPE=42` 时必填；DDL 内无 CHECK 约束。
- `ORDER_STATUS`：内部状态机，注释明确「不等于对外 `paymentResult`」；取值域由 `CK_F2F_ORDER_STATUS` 给出 11 个：`CREATED` / `PAYING` / `PAID` / `FULFILLED` / `FULFILL_FAILED` / `TOPUP_SUSPECT` / `PAY_FAILED` / `EXPIRED` / `REFUNDING` / `REFUNDED` / `CANCELED`；`NOT NULL`，参与扫表索引 `IDX_F2F_ORDER_SCAN (ORDER_STATUS, EXPIRE_TMS)`。
- `ORDER_AMOUNT`：订单总金额；单位分；取值域 `CK_F2F_ORDER_AMOUNT CHECK (ORDER_AMOUNT >= 0)`；`NUMBER(12) NOT NULL`。
- `REFUND_STATUS`：退款汇总状态；取值域 `NONE` 未退、`PARTIAL` 部分退、`SUCCESS` 全额退，`CK_F2F_ORDER_REFUND_STATUS CHECK (REFUND_STATUS IN ('NONE','PARTIAL','SUCCESS'))`，`DEFAULT 'NONE' NOT NULL`；与 `ORDER_STATUS` **正交**，退款不改支付与履约主状态。
- `REFUND_AMOUNT`：已成功退款总额；单位分；`DEFAULT 0 NOT NULL`；由 `F2F_REFUND` 中 `REFUND_STATUS=SUCCESS` 的行**重算得出，NEVER 累加**。
- `LAST_REFUND_TMS`：最后一次退款汇总重算时刻；`TIMESTAMP(6)`。
- `DEVICE_SEQ`：终端设备流水号；取值域 DDL 未限定；BOM 传 `bomOptSeq`、TVM 不传；与 `CHANNEL` + `DEVICE_ID` 构成防重下单键，唯一索引 `UK_F2F_ORDER_DEV_SEQ` 用 `CASE WHEN DEVICE_SEQ IS NULL THEN NULL ELSE ... END` 让空流水号不参与约束。
- `TICKET_PRICE`：单张票价；单位分；`NUMBER(12)`。
- `SINGLE_TICKET_TYPE`：单程票购票方式；取值域 `0` 按站点购票、`1` 按固定票价购票；`VARCHAR2(1 CHAR)`，DDL 内无 CHECK 约束。
- `CARD_BEFORE_AMOUNT`：充值前卡内余额；单位分；用于充值限额校验。
- `SUSPECT_SLIP_NO`：TVM 充值存疑时打印的故障单号，BOM 凭此号查询；索引 `IDX_F2F_ORDER_SUSPECT`。
- `RANDOM_FACT`：取票二维码随机因子；长度 32；与 `DEVICE_ID` + `QRCODE_GEN_DATE` 组成查码索引 `IDX_F2F_ORDER_QRCODE`。
- `ACTIVATE_DEVICE_ID`：取票订单已被哪台设备激活；非空即锁定，其他设备查询返回 `2008`。
- `EXPIRE_TMS`：二维码失效时间；等于创建时间加 180 秒；与 `ORDER_STATUS` 组成扫表索引 `IDX_F2F_ORDER_SCAN`。
- 表级：按 `CREATE_TMS` 月分区（`INTERVAL NUMTOYMINTERVAL(1,'MONTH')`，初始分区 `P_F2F_ORDER_INIT` 上界 `2026-10-01`）；`IDX_F2F_ORDER_CARD (CARD_ID, CREATE_TMS DESC)` 与 `IDX_F2F_ORDER_USER (THIRD_USER_ID, ACTIVATE_FLAG, CREATE_TMS DESC)` 为 LOCAL 索引，两个唯一索引为 GLOBAL。
- `ACTIVATE_FLAG`（无列注释）：`VARCHAR2(1 CHAR) DEFAULT '0' NOT NULL`，取值域 DDL 未写明；参与 `IDX_F2F_ORDER_USER`。

#### F2F_PAYMENT（表注释：与支付中心的支付交互流水，一行一次尝试）

- `ATTEMPT_NO`：同一订单内的尝试序号；取值域从 1 递增；与 `ORDER_NO` 构成唯一索引 `UK_F2F_PAY_ATTEMPT`，另有 `IDX_F2F_PAY_ORDER (ORDER_NO, ATTEMPT_NO DESC)`。
- `PAY_SCENE`：支付场景；取值域 `qrcode` 设备拉码用户扫、`scan` 主动扫用户付款码、`app` APP 内支付，`CK_F2F_PAY_SCENE CHECK (PAY_SCENE IN ('qrcode','scan','app'))`。
- `PAY_STATUS`：支付状态；取值域 `CK_F2F_PAY_STATUS` 给出 5 个：`INIT` / `PROCESSING` / `SUCCESS` / `FAILED` / `UNKNOWN`；注释强调 `UNKNOWN` 表示对端未明确应答、需靠查询接口收口，**不得直接判失败**；`PAY_STATUS='SUCCESS'` 经函数唯一索引 `UK_F2F_PAY_SUCCESS`（`CASE WHEN PAY_STATUS='SUCCESS' THEN ORDER_NO ELSE NULL END`）保证一单只有一笔成功支付。
- `PAY_TYPE`：支付类型；取值域 `0` 本地拼聚合码 URL 不调支付中心、其他值调支付中心预下单；`VARCHAR2(1 CHAR)`，DDL 内无 CHECK 约束。
- `AUTH_CODE`：用户付款码；仅 `PAY_SCENE=scan` 时有值。
- `PAY_URL`：返回给设备显示为二维码的完整字符串，设备不解析；`VARCHAR2(512 CHAR)`。
- `COST_MS`：本次与支付中心交互耗时；单位毫秒；用于排查虚拟线程 pin 与超时。
- 表级：按 `CREATE_TMS` 月分区；`PAY_CENTER_ORDER_NO` 单列 LOCAL 索引 `IDX_F2F_PAY_CENTER_NO`；`REQUEST_BODY` / `RESPONSE_BODY` 为 `CLOB`。

#### F2F_RESULT_REPORT（表注释：设备业务结果上报，统一承载 IF2A-04/05/06/07 与 BOM 业务操作结果通知；规格要求断网重传，靠 `UK_F2F_REPORT_IDEM` 保证幂等；六个上报接口收敂为五个 `REPORT_TYPE`，TVM 的 `topupCardResultNoti` 与 BOM 的 `notiTopupResult` 同映射 `TOPUP_OK`，前提是同一订单只会被单一渠道上报，因此唯一索引不含 `CHANNEL`；若该前提被推翻，MUST 改为 `(CHANNEL,REPORT_TYPE,ORDER_NO)`）

- `REPORT_TYPE`（无列注释，取值域来自 CHECK）：`CK_F2F_REPORT_TYPE` 给出 5 个：`TAKE_TICKET_OK` / `TAKE_TICKET_FAIL` / `TOPUP_OK` / `TOPUP_FAIL` / `BOM_BIZ_RESULT`；与 `ORDER_NO` 构成幂等唯一索引 `UK_F2F_REPORT_IDEM`。
- `ORDER_NO`：一般为 ITP 订单号；`ERROR_CODE=2101` 时规格要求填取票二维码的 `randomFact`，**故不建外键**。
- `TOPUP_STATUS`：充值状态；取值域 `00` 成功、`01` 失败、`02` 存疑、`03` 取消，`CK_F2F_REPORT_TOPUP CHECK (TOPUP_STATUS IS NULL OR TOPUP_STATUS IN ('00','01','02','03'))`，即允许为空。
- `ERROR_CODE`：错误码；取值域 DDL 未穷举，注释只写明 `2101` 表示取票二维码超时需解锁订单。
- `REPORT_TMS`：设备侧发生时间；格式 `YYYYMMDDHHMMSS`（`VARCHAR2(14 CHAR)`）；`ERROR_CODE=2101` 时为二维码生成时间。
- `PROCESSED`：处理标记；取值域 `0` 未处理、`1` 已处理，`DEFAULT '0' NOT NULL`，DDL 内无 CHECK 约束；后续动作（退款、状态推进）由扫表驱动、与接收解耦，扫表索引 `IDX_F2F_REPORT_PENDING (PROCESSED, RECEIVE_TMS)`。
- `RAW_BODY`：原始报文留证（`CLOB`）；规格允许出现接口未声明的字段。
- 表级：按 `CREATE_TMS` 月分区；`FAULT_SLIP_SEQ` 单列 LOCAL 索引 `IDX_F2F_REPORT_SLIP`。

#### F2F_TICKET（表注释：单程票明细，出票结果上报时写入；退款按票粒度）

- `TICKET_LOGIC_NUM`：票卡逻辑号；下单时未知，出票上报才有；`NOT NULL`，与 `TRANS_DATE` 构成唯一索引 `UK_F2F_TICKET_LOGIC`，另有 `IDX_F2F_TICKET_RECENT (TICKET_LOGIC_NUM, TRANS_DATE DESC)`。
- `TRANS_DATE`：交易日期；格式 `YYYYMMDDHHMMSS`（`VARCHAR2(14 CHAR) NOT NULL`）；BOM 退款按逻辑号加此字段定位。
- `TICKET_STATUS`（无列注释，取值域来自 CHECK）：`CK_F2F_TICKET_STATUS` 给出 4 个：`ISSUED` / `FAULT` / `REFUNDING` / `REFUNDED`。
- `REFUND_NO`：已退款时回填退款单号。
- 表级：非分区表；`IDX_F2F_TICKET_ORDER (ORDER_NO)`。

#### F2F_REFUND（表注释：退款单，四类来源共用；`UK_F2F_REFUND_IDEM` 是防重复退款的根本手段）

- `REFUND_SOURCE`：退款来源；取值域 `CK_F2F_REFUND_SOURCE` 给出 7 个，与列注释逐一对应：`TAKE_TICKET_FAIL` 出票故障自动退、`BOM_ORIGINAL` 单程票原路退、`APP_REQUEST` 用户主动退、`DAILY_BATCH` 每日批量退未取票、`TOPUP_FAIL` 充值失败退、`PAGE_MANUAL` 运营端手工退、`TVM_REQUEST` 设备侧 `requestRefund` 发起；参与幂等唯一索引 `UK_F2F_REFUND_IDEM`。
- `REFUND_STATUS`：退款状态；取值域 `CK_F2F_REFUND_STATUS` 给出 5 个，语义为 `INIT` 已落库对端未答、`PROCESSING` 支付中心已受理、`SUCCESS` 退款成功、`FAILED` 支付中心明确答复失败、`MANUAL` 超过自动收口时间窗仍查不到结果需人工介入（钱是否已退未知）；与 `NEXT_QUERY_TMS` 组成扫表索引 `IDX_F2F_REFUND_SCAN`。
- `TICKET_LOGIC_NUM`：按票退时填、整单退时为空；唯一索引 `UK_F2F_REFUND_IDEM (ORIG_ORDER_NO, NVL(TICKET_LOGIC_NUM,'#WHOLE#'), REFUND_SOURCE)` 用 `NVL` 占位，避免多笔整单退绕过约束。
- `REFUND_AMOUNT`：退款金额；单位分；取值域 `CK_F2F_REFUND_AMOUNT CHECK (REFUND_AMOUNT > 0)`（不允许 0 元退款）；出票故障场景等于（订单张数减实际张数）乘单张票价。
- `RETRY_TIMES`：收口查询已失败次数；`NUMBER(2) DEFAULT 0 NOT NULL`；既算指数退避间隔也作放弃自动收口的上限判据；**上限判定在应用层做并显式置 `MANUAL`，NEVER 写进扫表谓词**。
- `NEXT_QUERY_TMS`：下次允许向支付中心发起退款查询的时刻；**扫表谓词就是它**，由指数退避计算；`DEFAULT SYSTIMESTAMP NOT NULL`。
- `REFUND_NO`（无列注释）：`NOT NULL`，唯一索引 `UK_F2F_REFUND_NO`；`ORIG_ORDER_NO` 有 `IDX_F2F_REFUND_ORIG`、`PAY_CENTER_REFUND_NO` 有 `IDX_F2F_REFUND_CENTER`。
- 表级：非分区表。

#### F2F_NOTIFY_TASK（表注释：出向通知任务，替代原三张 Notice 表；对应 IF8B-04/05/06/07）

- `NOTIFY_TYPE`（无列注释，取值域来自 CHECK）：`CK_F2F_NOTIFY_TYPE` 给出 4 个：`TAKE_TICKET_OK` / `TAKE_TICKET_FAIL` / `REFUND_RESULT` / `PAY_RESULT`；与 `ORDER_NO` + `NVL(REFUND_NO,'#NONE#')` 构成幂等唯一索引 `UK_F2F_NOTIFY_IDEM`。
- `TARGET`：通知目标；取值域 `CK_F2F_NOTIFY_TARGET CHECK (TARGET IN ('APP'))`，即当前仅 `APP`；注释说明旧实现三张表均为 `tbl_notice_app_*`、无向 BOM 与 TVM 的出向通知，**新增目标 MUST 先确认对端有接收接口，NEVER 先放开 CHECK 再找场景**。
- `NOTIFY_STATUS`：通知状态；取值域 `CK_F2F_NOTIFY_STATUS` 给出 4 个：`PENDING` / `SUCCESS` / `FAILED` / `GIVEUP`；`GIVEUP` 表示超过最大重试次数放弃、需人工介入、不再扫表；与 `NEXT_RETRY_TMS` 组成扫表索引 `IDX_F2F_NOTIFY_SCAN`。
- `NEXT_RETRY_TMS`：下次重试时间；退避策略由应用计算后写入，**扫表只按此字段取**。
- `RETRY_TIMES` / `MAX_RETRY_TIMES`（无列注释）：`NUMBER(2)`，默认分别为 0 与 **5**；`PAYLOAD` 为 `CLOB`。

#### F2F_DEVICE_STATUS（表注释：设备最新心跳状态，一设备一行，用 `MERGE INTO` 更新；规格要求每 1 分钟上报，ITP 确认设备正常；只存最新状态不存历史）

- `ONLINE_FLAG`：在线标记；取值域 `1` 在线、`0` 离线，`DEFAULT '1' NOT NULL`，DDL 内无 CHECK 约束；由扫表按 `LAST_HEARTBEAT_TMS` 超时判定（该判定列有单列索引 `IDX_F2F_DEVICE_HB`，`ONLINE_FLAG` 本身无索引）。
- 表级：主键是 `(CHANNEL, DEVICE_ID)` 复合键 `PK_F2F_DEVICE_STATUS`（本表无 `ID` 列）；`HEARTBEAT_COUNT NUMBER(12) DEFAULT 1 NOT NULL`；`LAST_HEARTBEAT_TMS NOT NULL`。
- 注：本表 `CHANNEL` 无列注释、**也无 CHECK 约束**，与 `F2F_ORDER.CHANNEL`（有 3 值 CHECK）不同源约束。

#### 序列

- `F2F_ORDER_NO_SEQ`：`MINVALUE 1` / `MAXVALUE 9999` / `START WITH 1` / `INCREMENT BY 1` / `CYCLE` / `CACHE 20`，即 4 位循环序号（DDL 未写明用途，不做推断）。

#### 与 CHECK 约束不一致的列

- **`F2F_REFUND` 表注释写「四类来源共用」，而 `CK_F2F_REFUND_SOURCE` 与列注释都是 7 个取值**（`TAKE_TICKET_FAIL` / `BOM_ORIGINAL` / `APP_REQUEST` / `DAILY_BATCH` / `TOPUP_FAIL` / `PAGE_MANUAL` / `TVM_REQUEST`）。表注释口径落后于约束，引用时以 CHECK 为准。
- **`F2F_ORDER.CHANNEL` 的列注释预告了第 4 个取值（STT），CHECK 只允许 3 个**；且注释自身记了两种互相冲突的编码口径（`DeviceTypeEnum` 为 14、`BaseRequestDTO` 注释为 07）。注释已明写「确认后再放开 CHECK」，属有意为之，但**注释取值域宽于 CHECK 取值域**。
- **有注释取值域、DDL 内却无 CHECK 兜底的枚举列**（写入非法值不会被数据库拦住）：`F2F_ORDER.TRANS_TYPE`（9 个取值）、`F2F_ORDER.ADMIN_TRANS_TYPE`（`01` 至 `0A`）、`F2F_ORDER.SINGLE_TICKET_TYPE`（`0` / `1`）、`F2F_PAYMENT.PAY_TYPE`（`0` 与「其他」，本身即开放取值）、`F2F_RESULT_REPORT.PROCESSED`（`0` / `1`）、`F2F_DEVICE_STATUS.ONLINE_FLAG`（`1` / `0`）。
- **既无列注释也无 CHECK 的单字符标记列**：`F2F_ORDER.ACTIVATE_FLAG`（`DEFAULT '0' NOT NULL`，参与 `IDX_F2F_ORDER_USER`），取值域在 DDL 内无任何声明。


### 矛盾与待裁决

> 本轮只记录、**未改任何代码**。先给三条**库侧权威结论**（这是本次把 `f2f-schema.sql` 的 `COMMENT ON` 逐列比对上一轮矛盾的目的），再列各来源新增的矛盾条目。

#### 三条取值域矛盾的库侧裁决（证据全部来自 `face-pay-server/src/main/resources/sql/f2f-schema.sql`）

1. **`F2F_REFUND.REFUND_STATUS` 到底几个取值 —— 库侧裁决：五个，含 `MANUAL`；实体那条「四值」注释是错的。** 库侧证据有两条且互相自洽：`CONSTRAINT CK_F2F_REFUND_STATUS CHECK (REFUND_STATUS IN ('INIT', 'PROCESSING', 'SUCCESS', 'FAILED', 'MANUAL'))`（`f2f-schema.sql:235`），以及列注释逐值给了语义 —— 「INIT 已落库对端未答，PROCESSING 支付中心已受理，SUCCESS 退款成功，FAILED 支付中心明确答复失败，MANUAL 超过自动收口时间窗仍查不到结果需人工介入（钱是否已退未知）」（`:253`）。**CHECK 约束是唯一有强制力的一方**：`F2fRefundService` 的放弃分支写入 `MANUAL`，若照四值注释去删 DDL 里的 `MANUAL`，那条写入**当场 `ORA-02290`**。裁决：**改实体注释补上 `MANUAL`，NEVER 动 DDL。**
   - **连带发现一条本轮新增的同名列陷阱**：`F2F_ORDER` 也有一列叫 `REFUND_STATUS`，取值域**完全不同** —— `CK_F2F_ORDER_REFUND_STATUS CHECK (REFUND_STATUS IN ('NONE', 'PARTIAL', 'SUCCESS'))`（`:45`），列注释「退款汇总状态：NONE 未退，PARTIAL 部分退，SUCCESS 全额退。与 ORDER_STATUS 正交，退款不改支付/履约主状态」（`:75`，即 ADR-D88）。**排查退款状态 MUST 先确认是哪张表的 `REFUND_STATUS`**，两者只有 `SUCCESS` 一个字面量重叠；按名字 grep 会同时命中两个取值域。
2. **`F2F_ORDER.ORDER_NO` 是否含版本标识位 `F2` —— 库侧裁决：库侧注释同样是废弃方案的残留，两处都要改，NEVER 拿它当权威。** DDL 原文是 `COMMENT ON COLUMN F2F_ORDER.ORDER_NO IS 'ITP订单号，含版本标识位F2'`（`:69`），与 `entity/F2fOrder.java:23` **逐字同源**（同一批人同一天写的两份副本），因此**它不构成第二个独立证据**。真正的权威有两个：`support/F2fOrderNo.java:17` 记着「22 位 `F2 + 业务码 + 时间 + 序列` 已废弃、按用户 2026-09-10 裁决回退成 20 位与旧实现一致」，以及现场实测订单号 `00202609160953330199`（**以业务码 `00` 打头、无 `F2`、正好 20 位**）。裁决：**实体注释与 DDL 列注释一起删掉「含版本标识位F2」半句**（DDL 改列注释不影响数据，出一条 `COMMENT ON` 迁移语句即可）；**NEVER 反过来照注释给订单号加前缀** —— 长度变化是会直接打挂设备链路的 A 类契约差异。
3. **`F2F_ORDER.EXPIRE_TMS` 是否恒为「创建时间 + 180 秒」 —— 库侧裁决：库侧注释与实体注释同源、同样只覆盖 TVM 拉码单，两处都不完整。** DDL 原文 `COMMENT ON COLUMN F2F_ORDER.EXPIRE_TMS IS '二维码失效时间，创建时间加180秒'`（`:86`）。反证有两条：**代码侧** BOM 售票单用 `bomSaleExpireSeconds`（30 分钟）、APP 取票单另有取值，`service/F2fTvmOrderService.java` 的 `insertOrder` 按渠道传 `expireSeconds`；**结构侧** `CREATE INDEX IDX_F2F_ORDER_SCAN ON F2F_ORDER (ORDER_STATUS, EXPIRE_TMS) LOCAL`（`:64`）说明该列是**过期扫描键**、值必须按单据类型变化，若真是「创建时间 + 固定 180 秒」，这个索引等价于按创建时间扫、根本不需要独立一列。裁决：两处注释都改成「失效时刻，按渠道/单据类型不同，取值见 `F2fTvmOrderService.insertOrder` 的 `expireSeconds`」；**NEVER 让读者据这条注释推断 BOM 单 3 分钟就过期**（会把仍在有效期的单误判成僵尸单去关）。

#### 本轮新增的矛盾条目（按来源分组）


**来源：非 service 侧（controller / api / dto / entity / 常量）**


#### collect-pay-server g1 矛盾 / 待裁决条目

来源同 `frag_g1.md`。每条按「注释说什么 / 实际是什么 / 证据坐标 / 建议裁决」。

#### 1. 订单支付状态 `0` 与 `3` 的含义在实体与枚举之间正好相反（P0，资损面）

- **注释说什么**：`TvmAppOrder.payStatus` 的 Javadoc 写「`0` 未支付，`1` 支付成功，`2` 支付失败，`3` 支付中」。
- **实际是什么**：同模块 `ItpStatusEnum` 是「`0` 支付中，`1` 支付成功，`2` 支付失败，`3` 未支付」，`BomNoCashOrder` / `TvmTopupOrder` 的订单状态也是后一套。也就是说 `TBL_TVM_APP_ORDER` 与其余订单表对 `0` / `3` 的解释互换。而 `/internal/app-order` 的「关闭仍待支付的订单」白名单写的是 `PAY_STATUS='0'`，按实体注释是「未支付」（正确），按 `ItpStatusEnum` 则是「支付中」（会关掉正在支付的单）。
- **证据坐标**：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/entity/TvmAppOrder.java:49-52`、`.../constant/ItpStatusEnum.java:10-13`、`.../entity/BomNoCashOrder.java:63-69`、`.../entity/TvmTopupOrder.java:42-44`、`.../controller/internal/AppPayOrderInternalController.java:47`
- **建议裁决**：先按 `TBL_TVM_APP_ORDER` 的实际数据分布回查（`SELECT PAY_STATUS, COUNT(*)` 并抽样比对 `PAY_TIME` 是否为空）确定真相，再选一侧为准并全局 grep 所有比较点。**MUST NOT 只改注释**；若确认实体注释为真，则 `ItpStatusEnum` **MUST NOT 再用于 APP 订单表**。

#### 2. 退款表的「业务类型」编码宽度与枚举不一致

- **注释说什么**：`RefundOrder.业务类型` 是「`1` 扫码购票 `2` 扫码充值 `3` 扫码取票」（单字符、只有 3 项）。
- **实际是什么**：`BusinessTypeEnum` 是两字符、共 5 项：`01` 扫码购票 / `02` 扫码充值 / `03` 扫码取票 / `04` bom 支付 / `05` app 退款。按枚举写入 `tbl_refund_order` 会产出 `01` 这种注释里没有的值；按注释写入则表达不了 `04` / `05`。
- **证据坐标**：`.../entity/RefundOrder.java:31-33`、`.../constant/BusinessTypeEnum.java:8-12`
- **建议裁决**：查 `tbl_refund_order` 的实际列长与现存 distinct 值定真相。若库里既有 `1` 也有 `01`，属两次写入口径不同的历史遗留，**MUST 先定归属再谈归一，NEVER 直接 UPDATE 归一**（退款记录是资金凭证）。

#### 3. 四个枚举的类级 Javadoc 是复制粘贴的错文案

- **注释说什么**：`ActivateFlagEnum`、`AppStatusEnum`、`BusinessTypeEnum`、`PayCenterRefundStatusEnum` 的类注释**逐字相同**，都是「ITP 支付订单状态枚举。`0` 支付中，`1` 支付成功，`2` 支付失败，`3` 未支付」。
- **实际是什么**：这四个类分别是「激活标志（`0`/`1`）」「APP 支付与退款状态（`SUCCESS`/`FAIL`/`PROCESSING`）」「业务类型（`01`~`05`）」「支付中心退款状态（`PROCESSING`/`SUCCESS`/`FAIL`）」，**没有一个是支付订单状态、也没有一个取值是 `0`~`3` 四值**（`ActivateFlagEnum` 只有 `0`/`1`）。真正符合该注释的只有 `ItpStatusEnum`。
- **证据坐标**：`.../constant/ActivateFlagEnum.java:5-8` vs `:10-11`、`.../constant/AppStatusEnum.java:3-6` vs `:9-14`、`.../constant/BusinessTypeEnum.java:3-6` vs `:8-12`、`.../constant/PayCenterRefundStatusEnum.java:3-6` vs `:9-11`、`.../constant/ItpStatusEnum.java:5-8`
- **建议裁决**：迁移 docs 时 **MUST 以枚举常量为准、丢弃这四处类注释**；**NEVER 把这四条当成「这些字段也有 `0`~`3` 状态」的证据**。仓库侧可另开一次注释修正（属纯注释改动、不动行为）。

#### 4. 三个错误码枚举共用同一句「对应文档表 70」的类注释，但码空间互不相同

- **注释说什么**：`AppCodeEnum`、`DevicePayCodeEnum`、`TvmPayCodeEnum` 的类注释**逐字相同**，都是「TVM 扫码购票业务错误码定义。对应文档表 70 错误代码列表」。
- **实际是什么**：`AppCodeEnum` 只有 `0000` / `9999`（APP 域）；`DevicePayCodeEnum` 根本不是错误码，是 `ORDERED` / `SUCCESS` / `FAILED` 三个**支付状态字面量**；只有 `TvmPayCodeEnum`（`0000` / `2999` / `2001`~`2008`）与「TVM 扫码购票错误码」相符。另外 `BomPayCodeEnum` 才是 BOM 域的 `8xxx`，它没有这句注释。
- **证据坐标**：`.../constant/AppCodeEnum.java:3-6` vs `:8-10`、`.../constant/DevicePayCodeEnum.java:3-6` vs `:8-11`、`.../constant/TvmPayCodeEnum.java:3-6` vs `:8-18`、`.../constant/BomPayCodeEnum.java:4-13`
- **建议裁决**：「文档表 70」这条溯源 **MUST 只挂在 `TvmPayCodeEnum` 上**；另两处按实际语义重写。查甲方错误码表时 **NEVER 按这三个类去反查同一张表**。

#### 5. `BomMainTicket` 的类名是 BOM、注释与表名却是 TVM 表

- **注释说什么**：`BomMainTicket` 的类注释是「TVM 出票主记录表实体（`tbl_tvm_main_ticket`）」。
- **实际是什么**：同目录另有 `TvmMainTicket`，类注释**逐字相同**（同样是 `tbl_tvm_main_ticket`）。即两个类映射同一张表，且 BOM 侧没有独立主票表。三个子票实体（`SubTicket` / `TvmSubTicket` / `BomSubTicket`）的「主表 ID」也都写「外键关联 `tbl_tvm_main_ticket`」，与之自洽。
- **证据坐标**：`.../entity/BomMainTicket.java:5-7`、`.../entity/TvmMainTicket.java:7-9`、`.../entity/SubTicket.java:15-17`、`.../entity/TvmSubTicket.java:15-17`、`.../entity/BomSubTicket.java:15-17`
- **建议裁决**：查库确认是否真的只有 `tbl_tvm_main_ticket` 一张主票表（有无 `tbl_bom_main_ticket`）。若确认只有一张，则「TVM/BOM 共用主票表」是设计事实、**MUST 写进 docs**，`BomMainTicket` 属冗余重复实体、可标记待清理；若库里另有 BOM 主票表，则 `BomMainTicket` 的注释是错的、**属映射错表的潜在缺陷**。

#### 6. `BomSaleOrder` 与 `BomNoCashOrder` 两个类都声明映射 `TBL_BOM_NOCASH_ORDER`

- **注释说什么**：两者类注释**逐字相同**：「BOM 非现金收款订单实体类。对应数据库表 `TBL_BOM_NOCASH_ORDER`，存储 BOM 非现金收款业务的订单信息」。
- **实际是什么**：类名一个是 `BomSaleOrder`（售票）、一个是 `BomNoCashOrder`（非现金），字段集不同（`BomNoCashOrder` 有 `transType` / 行政交易类型 / 订单状态 / 状态描述，`BomSaleOrder` 注释里只见到更新时间）。同一张表两个实体、或其中一个注释指错了表。
- **证据坐标**：`.../entity/BomSaleOrder.java:5-8`、`.../entity/BomNoCashOrder.java:3-6`
- **建议裁决**：按两个类的字段逐列比对 `TBL_BOM_NOCASH_ORDER` 的实际列（`USER_TAB_COLS`）判断谁是真 owner；另一个若是历史遗留 **MUST 标注废弃**。日终对账口径里的「BOM 处理 / BOM 行政处理两组恒 0」问题（AGENTS.md §2.2.2 已记）很可能与「读了错的那个实体 / 错的 `TRANS_TYPE` 取值口径」同源，**排查那两组恒 0 MUST 先闭合本条**。

#### 7. `BomBusinessCodeEnum` 两个常量描述都是「充值」

- **注释说什么**：`SALE("01", "充值")`、`TOPUP("22", "充值")`。
- **实际是什么**：`22` 在 `transType` 取值表里确实是「充值」；`01` 在 `transType` 表里**不存在**（该表是 `02`/`03`/`04`/`05`/`06`/`22`/`2A`/`2B`/`42`），而枚举名 `SALE` 指向「售票」。因此 `01` 的描述「充值」大概率是复制 `TOPUP` 那行时漏改。
- **证据坐标**：`.../constant/BomBusinessCodeEnum.java:4-5`、`.../entity/BomNoCashOrder.java:19-30`、`.../model/request/bom/RequestGenNoCashOrderReqDTO.java:11-22`
- **建议裁决**：向甲方或 BOM 侧确认 `01` 的真实业务含义（售票？），确认前 **NEVER 按描述把 `01` 当充值统计**；这直接影响对账里按业务类型分组的度量。

#### 8. `ProductType` 的 `0C` 与 `0D` 描述完全相同

- **注释说什么**：`CmbSUPPLEMENT("0C", "招行一网通_垫资补缴")`、`EcnyActPay("0D", "招行一网通_垫资补缴")`。
- **实际是什么**：枚举名 `EcnyActPay` 明示是数字人民币（e-CNY）相关，与「招行一网通」无关，`0D` 的描述是复制 `0C` 时漏改。**这是 AGENTS.md §2.2.2 所说「仅 collect-pay-server 支付方式枚举含 e-CNY 字样」的那一处**。
- **证据坐标**：`.../constant/ProductType.java:19-20`
- **建议裁决**：`0D` 的描述 MUST 按 e-CNY 语义修正。**但 NEVER 据此认为数字人民币硬钱包已有实现** —— 它只是订单号前缀枚举里的一个占位项，无业务实现、无表（AGENTS.md §2.2.2）。

#### 9. `SingleTicketRefundTask` 的日志自称「由 web-server Quartz 触发」，实际是本模块 `@Scheduled` 自驱

- **注释说什么**：四个方法体内的日志文案统一是「收到由 web-server Quartz 定时任务发起的调用 xxx 接口」，且方法上方留有被注释掉的 `@PostMapping`。
- **实际是什么**：四个方法上都挂着未注释的 `@Scheduled`（cron `0 0 20 * * ?` 两个、`0 1 9,15,21 * * ?`、`0 0 9,15,21 * * ?`），而三个 `@PostMapping` 被注释掉了 —— **web-admin 根本没有 HTTP 入口可调**，所以那句日志描述的触发方不存在。AGENTS.md §2.2.1 也把本模块列为「还有在跑的 `@Scheduled`」的两个模块之一（`SingleTicketRefundTask` 4 处），与代码一致、与日志文案不一致。
- **证据坐标**：`.../task/SingleTicketRefundTask.java:33,34,51,68,69,86,87`（`@Scheduled` 在 34/51/69/87，被注释的 `@PostMapping` 在 33/68/86），日志文案在 `:36,53,71,89`
- **建议裁决**：这四个任务**没有分布式锁**，因此 collect-pay-server **MUST 单副本**；若打算迁到 web-admin `sys_job` 统一调度，**MUST 先摘 `@Scheduled` 再放开 `@PostMapping`，NEVER 两者同时存在**（会同一批退款跑两遍，属资损）。在裁决前 **MUST 修正日志文案**，否则排查「退款到底谁触发的」会被误导到 `SYS_JOB_LOG` 去查一个不存在的任务。

#### 10. 支付失败字面量 `FAIL` 与 `FAILED` 在同模块内并存

- **注释说什么**：各 DTO 注释按各自域写：TVM 应答「失败：FAILED」、BOM 应答「SUCCESS/FAILED/PROCESSING」、APP 侧枚举「支付失败 FAIL」、支付中心退款「退款失败 FAIL」而支付失败 `FAILED`。
- **实际是什么**：`DevicePayCodeEnum` / `PayCenterStatusEnum` / TVM 与 BOM 应答 DTO 用 `FAILED`；`AppStatusEnum` / `PayCenterRefundStatusEnum` 用 `FAIL`。**同一个「失败」概念有两个字面量，且分界线不是「域」而是「支付 vs 退款」与「枚举 vs DTO」交叉**。
- **证据坐标**：`.../constant/DevicePayCodeEnum.java:11`、`.../constant/PayCenterStatusEnum.java:10`、`.../constant/PayCenterRefundStatusEnum.java:11`、`.../constant/AppStatusEnum.java:10,14`、`.../model/response/tvm/RequestPaymentRespDTO.java:20-24`、`.../model/response/tvm/RequestRefundRespDTO.java:20-25`
- **建议裁决**：**NEVER 做统一归一**（对外契约字面量一改就是设备侧解析失败）；MUST 在 docs 里按「哪条链路的哪个字段用哪个字面量」逐条列出，并在新写比较逻辑时用枚举常量而非硬编码字符串。

#### 11. 三处状态变更型端点无鉴权，与 AGENTS.md §5.2 直接冲突（注释自述为临时降级）

- **注释说什么**：`AppPayOrderInternalController`、`ReconExportController`、`AppOrderPageController` 三处类注释都写「开发测试阶段：本接口当前无鉴权，上线前 MUST 恢复」，并自认与 AGENTS.md §5.2 冲突、属**有意为之的临时降级**。
- **实际是什么**：当前代码里确实没有任何校验，且本模块无 spring-security、无全局拦截器兜底、`signType=00` 即免签。三者的暴露面依次是：按订单号给他人建单或关掉他人待支付单；触发区间全扫级别的批处理；按指定金额给任意订单退款。
- **证据坐标**：`.../controller/internal/AppPayOrderInternalController.java:14-30`、`.../controller/internal/ReconExportController.java:11-24`、`.../controller/page/AppOrderPageController.java:15-32`
- **建议裁决**：保持现状是已获用户裁决的（2026-09-14 / 2026-09-11 两次），但 **MUST 进上线核对清单**；恢复形态已写死在注释里（对齐 `AccountRequestVerifier` / `ItpRequestSignVerifier`，或共享令牌 + `MessageDigest.isEqual` + K8s Secret）。**NEVER 把「`/internal/recon` 也没加」当成 `/internal/app-order` 长期免鉴权的理由。**

#### 12. 同一批 `IF2A-08` / `IF2A-09` 编号在 TVM 与 BOM 两个 controller 里指不同接口

- **注释说什么**：`TvmOrderController` 里 IF2A-08 是「扫码取票订单查询」、IF2A-09 是「请求充值下单」；`BomOrderController` 里 IF2A-08 是「业务操作结果通知」、IF2A-09 是「充值结果通知（7.3.3.5）」。
- **实际是什么**：两组注释同时存在于代码里，四个方法都是实打实的端点。这与 AGENTS.md §2.2.1「接口编号不唯一映射到一处代码，定位实现 MUST 以模块 + URL 为准」一致，但**本模块内部就撞号**，比跨模块撞号更容易误判。
- **证据坐标**：`.../controller/ci/tvm/TvmOrderController.java:239-244,257-262`、`.../controller/ci/bom/BomOrderController.java:157-163,184-190`
- **建议裁决**：docs 里 **MUST 用「域 + 编号 + URL」三元组标注，NEVER 只写 IF2A-08**。按编号查实现时 MUST 同时 grep `controller/ci/tvm` 与 `controller/ci/bom` 两个包。

#### 13. `TvmTopupOrder` 已有 `payType` 列，而 AGENTS.md 记「IF2A-01 / IF2A-09 需加 payType」属未落地

- **注释说什么**：`TvmTopupOrder` 的字段注释是「`0`：其他支付方式 / `1`：数字人民币 app」，取值与 AGENTS.md §2.2.2 里「另有 IF2A-01 / IF2A-09 需加 `payType`（`0` 其他 / `1` 数字人民币 app）」逐字一致。
- **实际是什么**：实体（即库列）侧已有该字段，但 AGENTS.md 把它列在「尚未落地的需求」里。两者可能都对（列已建、接口 DTO 未加），也可能是 AGENTS 该条已过期。
- **证据坐标**：`.../entity/TvmTopupOrder.java:47-50`
- **建议裁决**：MUST 分两步核实 —— ①查 `USER_TAB_COLS` 确认 `PAY_TYPE` 列是否真在库里；②grep `RequestGenSjtOrderReqDTO` 与充值下单 req DTO 有没有 `payType` 字段。**在核实前 NEVER 声称「payType 已实现」，也 NEVER 声称「连列都没有」。**

#### 14. 「文档表 70」「7.5.1.1」「7.3.3.5」三处规格章节号在仓库内无法自证

- **注释说什么**：错误码注释引「文档表 70 错误代码列表」，公共参数基类引「7.5.1.1 请求公共参数」，BOM 充值结果通知引「7.3.3.5 IF2A-09」。
- **实际是什么**：这些章节号指向甲方 `.docx` 原文，注释里没写是哪一份文档；而 AGENTS.md §9 已记「《技术规范-第 4 部分-系统接口规范》在仓库内没有源文件」。因此这三处溯源目前**不可验证**。
- **证据坐标**：`.../constant/TvmPayCodeEnum.java:3-6`、`.../model/request/BaseRequestDTO.java:3-5`、`.../model/request/PayCenterBaseRequestDTO.java:5-7`、`.../controller/ci/bom/BomOrderController.java:184-190`
- **建议裁决**：迁移 docs 时 **MUST 把章节号连同「出自哪份 docx 待确认」一起记下**，NEVER 直接当成已核实的规格出处；补齐时优先在 `docs/接口规范文档/` 下按章节号反查并把文件名回填注释。


**来源：service 层（上）**


#### 矛盾 / 待裁决条目

**1. `AppPayOrderInternalService` 类注释里的「切换尚未发生、两条写入路径并存、本接口暂时无人调用」已过期**

- 注释说什么：gate-txn-pay-server 的 `mapper/AppPayOrderMapper.java` 与 `resources/mapper/AppPayOrderMapper.xml` **仍在原地**，`SupplementOrderServiceImpl` 的私有 `registerAppPayOrder` 也仍在用它直写 `TBL_TVM_APP_ORDER` / `TBL_TVM_ORDER_PAY_PRE`；因此**现在有两条写入路径并存，本接口这一条暂时无人调用**；完成切换的那一笔 MUST 同时删掉对方 mapper 与 XML、并改掉三处调用点与单测桩。
- 实际是什么：**切换已完成**。gate-txn-pay-server 的 mapper 目录只剩 `DiscountLevelMapper` / `GateTxnPayMapper` / `MetroTransferPushTaskMapper` / `ReconExportMapper`（Java 与 XML 均如此），没有 `AppPayOrderMapper`；写入改走 `rpc` 的 `CollectPayClient.registerAppPayOrder` → `POST /internal/app-order/register`，返回类型已是 `RpcOutcome`。因此本接口**有唯一调用方、不再是无人调用**。
- 证据坐标：`gate-txn-pay-server/src/main/java/com/chinasofti/huateng/gatetxnpay/mapper/`（目录清单）；`rpc/src/main/java/com/chinasofti/huateng/rpc/collectpay/CollectPayClient.java:86`、`:99`、`:102`；`docs/business/gate-txn-pay.md:149`（「`mapper/AppPayOrderMapper.java` 与 `.xml` 已删除，NEVER 加回」，ADR-D64）；过期表述所在：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/AppPayOrderInternalService.java:69-74`。
- 建议裁决：迁移到 docs 时**不要搬运这段表述**，改写为「切换已于 ADR-D64 完成，两张表的唯一写入方是本模块，唯一调用方是 gate-txn-pay 补款链路的 `CollectPayClient`」，并保留「NEVER 让任何其它模块再直写这两张表」这一条约束。同批 MUST 修正 `docs/business/tvm-bom-pay.md:786` 与 `:869` 里同源的过期副本（那两处已自带「可断言：gate-txn-pay-server 内不存在 `AppPayOrderMapper`」的反证，说明矛盾已被记录但未收口）。源码注释本身是否改动需另行确认（本次只做抽取）。

**2. `BomOrderServiceImpl.notiBusResult`（IF2A-08）带 `@Transactional` 且在事务内调支付中心退款**

- 注释说什么：同一个类的 `notiTopupResult` 有一整段注释解释「本方法有意不带 `@Transactional`（2026-09-14 摘除，NEVER 加回）」，理由是 AGENTS.md §5.2「`@Transactional` 方法内 NEVER 发起任何 RPC / 网络调用」，并把行锁堆积 → Druid 强杀连接 → `commit` 抛 connection closed → 证据全丢 → UUID `retCode` → 上游继续重推的完整链条写清楚了。
- 实际是什么：**同一个类里 `notiBusResult` 仍带 `@Transactional(rollbackFor = Exception.class)`，而它的 `optResult=FAILED` 分支正是 `this.doRefund(...)` 调支付中心**，随后还有 `updateBomBusResult` 回写 —— 与被摘除的那个方法是同型结构。同类的 `requestPayment`（IF8A-05 扫码支付）也带 `@Transactional` 且链路里有支付中心调用。
- 证据坐标：`.../service/impl/BomOrderServiceImpl.java:439`（`@Transactional` + `notiBusResult`）、`:486`~`:490` 区间的 `doRefund` 调用、`:249`（`@Transactional` + `requestPayment`）；对照样板 `.../service/impl/BomOrderServiceImpl.java:686-703`（已摘除的那段说明）；规则出处 AGENTS.md §5.2。
- 建议裁决：按 `notiTopupResult` 同款处理 —— 摘掉这两处 `@Transactional`，让通知记录与「退款中」状态先自动提交、退款结果各自回写。**这是改行为的动作，MUST 先向用户确认**；在确认前，docs 里 MUST 记为「已知未修复：BOM 域仍有两个事务内调支付中心的方法」，**NEVER 只搬 `notiTopupResult` 那段「已摘除」的注释就宣称该模块已合规**。

**3. `/internal/app-order/**` 三个端点无鉴权，与 AGENTS.md §5.2 直接冲突**

- 注释说什么：「本接口当前无鉴权（用户 2026-09-14 裁决：与本模块 `/internal/recon` 的临时降级保持一致）。它是状态变更型接口，与 AGENTS.md §5.2 冲突，属**有意为之的临时降级**：任何网络可达方都能按订单号给他人建单或关掉他人的待支付单。**上线前 MUST 补鉴权**。」
- 实际是什么：冲突真实存在且**尚未闭合**，`docs/ops/生产环境清单.md` 已把它列为 P0 与上线核对项；`docs/business/tvm-bom-pay.md:789` 另加一条判据「**NEVER 拿「`/internal/recon` 也没加」当长期理由**」（只读导出与「按订单号改他人支付状态」不同级）。
- 证据坐标：`.../service/AppPayOrderInternalService.java:90-93`；`docs/ops/生产环境清单.md:316`、`:318`、`:907`；`docs/business/tvm-bom-pay.md:789`。
- 建议裁决：保留为**显式登记的临时降级**，迁移时 MUST 同时搬运「NEVER 拿 recon 当长期理由」那条判据，**NEVER 把它写成「本模块内部端点按约定无需鉴权」**。是否本次恢复鉴权属安全红线，MUST 由用户决定。

**4. 单副本约束的表述不一致：注释暗示 recon-server 可多副本**

- 注释说什么：`ReconExportService` 的并发控制说明写「单副本内足够；**多副本场景由 recon-server 侧按批次分发保证只下发一次**」，读起来像是 recon-server 支持多副本、由它兜住幂等。
- 实际是什么：AGENTS.md §2.2.1 明确 `ReconOrchestrationService.runDailyBatch` 只有进程内 `AtomicBoolean` 拒绝并发、**没有数据库锁**，因此 **recon-server 与 web-admin 都 MUST 单副本**。也就是说「多副本场景由 recon-server 兜住」这个前提当前不成立。
- 证据坐标：`.../service/ReconExportService.java:225-227`；AGENTS.md §2.2.1「日终对账的调度也在 web-server」那条末句。
- 建议裁决：迁移时改写为「本源侧的在途标记只在**单副本内**有效；下发端 recon-server 自身也 MUST 单副本，**NEVER 据本条认为多副本已被兜住**」。属文档口径修正，不改代码。

**5. `BomOrderService` 里 IF2A-04 的注释写的是 TVM 语义**

- 注释说什么：`BomOrderService` 的 IF2A-04 出票结果通知，注释原文是「**TVM** 出票成功后通知 ITP 平台」。
- 实际是什么：该方法挂在 BOM 服务接口上；而 `TvmOrderService` 里另有一条同编号 IF2A-04、注释同样是「TVM 出票成功后通知 ITP 平台」。两处**同号同注释、分属两个渠道接口**，无法据注释判断 BOM 侧那条的真实语义（是 BOM 代 TVM 上报，还是 BOM 自身出票上报）。这与 AGENTS.md §2.2.1「接口编号不唯一映射到一处代码」同型。
- 证据坐标：`.../service/BomOrderService.java:99-105`；`.../service/TvmOrderService.java:48-54`。
- 建议裁决：迁移时**只写「模块 + URL」**，不搬「TVM 出票成功后通知」这句渠道断言；BOM 侧那条的真实语义 MUST 从 Controller 的 `@PostMapping` 与调用它的设备报文取证后再补。

**6. `BomOrderService` 有一条只有「充值业务但没发送充值通知」六个字的方法**

- 注释说什么：`/** 充值业务但没发送充值通知 @return */`，没有入参说明、没有状态取值、没有与 IF2A-06 / IF2A-07 / BOM 充值结果通知的关系。
- 实际是什么：无法从注释判断它是「补发通知的入口」还是「查询未通知充值单的入口」，也无法判断它是否属于对外契约。
- 证据坐标：`.../service/BomOrderService.java:59-62`。
- 建议裁决：**不迁移这条**（不构成硬事实）；需要时 MUST 先读实现与 Controller 取证再补写。

**7. 未完成的 TODO 直接影响退款查表正确性**

- 注释说什么：`// todo 判断当前订单是扫码购票还是扫码取票，然后查不同的表`。
- 实际是什么：该处当前**没有按订单类型分表查询**，即扫码购票与扫码取票走同一张表的查询路径；而本模块的退款链路对这两类订单的落表本来是不同的（购票单在 `TBL_TVM_ORDER_PAY` 一侧，取票单走 APP 订单一侧，`BomOrderServiceImpl:1524` 也确实按「BOM 订单改 BOM sub 表、其余按 TVM 处理」分叉）。
- 证据坐标：`.../service/impl/BomOrderServiceImpl.java:1294`；对照分叉点 `:1524`。
- 建议裁决：迁移为一条**已知坑**（「该处未按订单类型分表，排查 BOM 退款查不到原单时 MUST 先看这条 TODO」），**NEVER 当成已实现的分派逻辑**。是否修复需业务确认两类订单的落表口径。

**8. ITP.PAY 线路段与车站段的空缺待甲方补数据源**

- 注释说什么：TVM 充值与 BOM 两组的车站段本身就空（表里连车站码列都没有），因此 recon-server 也补不出线路段；「待甲方补数据源后，本模块只要把车站段填上，线路段就会由 recon-server 自动补齐」。
- 实际是什么：这是**已声明但未闭合的数据侧空缺**，与 AGENTS.md §2.2.2 记录的「collect-pay 的 `TRANS_TYPE` 三处矛盾致 BOM 行政处理与 BOM 处理两组恒 0」是同一批未闭合项；甲方规格对这些段位的必填性尚无澄清结论。
- 证据坐标：`.../service/ReconExportService.java:312-317`、`:292-299`；AGENTS.md §2.2.2 ACC 对账文件那条的「④甲方文档歧义」与「⑤数据侧 5 处空缺」。
- 建议裁决：迁移为**待甲方澄清清单**的一项，明确「当前产出这几段为空或恒 0 是有意标注、不是缺陷」，并保留「宁可标注也不凭猜写取值」这条判据。


**来源：service 层（下）**


# frag_g2b 矛盾与待裁决

#### 一、`TRANS_TYPE`：BOM 发售取值无写入方（与既有「三处矛盾致对账两组恒 0」同源）

- **注释说什么**：`TvmOrderServiceImpl` 里整段注释掉的 `getBomOrderSalePre` 明确要把前置单的 `transType` 写成 `BusinessTypeEnum.BOM_SCANED_SALE_PAY`，即「BOM 发售」独立成一个交易类型。
- **实际是什么**：该方法零调用方、整段注释。当前真实写入前置单 `TRANS_TYPE` 的只有四处，且**没有一处写 BOM 发售**：`TVM_SCAN_QR_BUYTICKET`（TVM 扫码购票）、`TVM_SCAN_QR_RECHARGE`（扫码充值）、`TVM_SCAN_QR_TAKETICKET`（APP 扫码取票）、`BOM_SCANED_PAY`（BOM 扫码支付，笼统一个值）。于是 BOM 侧「发售 / 充值 / 行政处理」在库里无法区分，对账导出只能整表计入「发售」组、不按 `TRANS_TYPE` 拆分，BOM 行政处理与 BOM 处理两组度量恒 0（与 AGENTS.md §2.2.2 ⑤ 记载的既有结论一致）。
- **证据坐标**：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:271-282`（注释版 BOM 发售前置单）、`:265`（TVM 购票实际取值）、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTopupServiceImpl.java:158`（充值）、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/AppOrderServiceImpl.java:118`（取票）、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/BomOrderServiceImpl.java:235`（BOM 唯一取值）、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/mapper/ReconExportMapper.java:95`、`:108`（整表计入发售、设备上送来源）、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/ReconExportService.java:192`、`:197`（一律填 0、有意降级）、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/AppPayOrderInternalServiceImpl.java:152-154`（`TRANS_TYPE` 为空时回调查不到前置单、状态永远停在待支付）。
- **建议裁决**：属**数据侧口径缺失、不是代码 bug**，MUST 先与甲方确认 BOM 侧「发售 / 充值 / 行政处理」在 `TBL_BOM_ORDER_PAY.TRANS_TYPE` 上的取值表，再回头决定是补写入（把 `BOM_SCANED_SALE_PAY` 之类的细分值落库）还是维持「整表发售 + 标注」的降级；**NEVER 在没有甲方取值表的情况下自行猜值写库**，也 NEVER 删 `:271-282` 那段注释（它是缺口的物证）。

#### 二、正常出票通知「是否可能退款」自相矛盾

- **注释说什么**：`:434` 原文「如果购票数量大于实际出票数量，发起退款 出票张数和订单张数一致才发送取票通知，所以不存在退款可能」—— 前半句说要退，后半句说不可能退，同一行里两个结论。
- **实际是什么**：正常出票通知（`notiTakeTicketResult`）里的 `handleRefund` 已整段注释，确实不退；而出票**故障**通知（`notiTakeTicketFailResult`）里 `refundNum = buyNum - actualNum` 的退款是活代码。也就是说「少出票」这件事只走故障通知一条路。
- **证据坐标**：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:434-437`（注释）、`:636-637`、`:648-658`（活代码）、`:687-713`（`handleRefund` 本体仍在、仅被故障链路调用）。
- **建议裁决**：改注释文字、保留行为。MUST 把 `:434` 改成单一结论「正常出票通知不退款，少出票一律由 TVM 走出票故障通知触发退款」，并写明依赖前提是**设备侧保证「张数不一致必发故障通知」**；这个前提 MUST 找 TVM 厂商书面确认 —— 若厂商在「少出票但无故障」场景下只发正常通知，则这笔钱既不退也无人知。

#### 三、扫码取票退款「不通知 APP」的依据是一句疑问

- **注释说什么**：`// app这里是不是经过票卡分析做的退款，所以不通知app` —— 疑问句，不是判据。
- **实际是什么**：`requestRefund` 分派到 `TVM_SCAN_QR_TAKETICKET` 时直接调 `appOrderService.doRefund(..., BusinessTypeEnum.APP_REFUND.getCode())` 并返回，链路里确实没有任何对 APP 的退款结果通知；而 TVM 公共服务里那套「退款成功后通知 APP」的实现是整段注释状态。
- **证据坐标**：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderPreServiceImpl.java:104-111`、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmCommonServiceImpl.java:188-194`（注释版通知）、`:262-322`。
- **建议裁决**：MUST 与 APP 侧确认「APP 主动发起的退款是否需要 ITP 反向通知结果」。若需要，则这是一个**功能缺口**（当前退款成功与失败对 APP 都不可见，只能靠 APP 自己轮询）；若不需要，把疑问句改成结论并注明「APP 自查退款结果」的接口名。

#### 四、注释版退款结果分支里 `APP_REFUND` 有一支永不可达

- **注释说什么**：第一支 `if (businessType == TVM_SCAN_QR_BUYTICKET || businessType == APP_REFUND)` 走 `dealRefundResult`，第二支 `else if (businessType == TVM_SCAN_QR_TAKETICKET || businessType == APP_REFUND)` 走 `dealAppRefundResult`。
- **实际是什么**：`APP_REFUND` 在第一支已被吃掉，第二支对它永不可达 —— 即「APP 主动退款」当年走的是 `dealRefundResult`（改 `REFUND_ORDER` + `TVM_PAY_ORDER`），而不是作者本意的 `dealAppRefundResult`（改 `APP_REFUND_ORDER` + `TVM_APP_ORDER`）。这是一个潜伏的**落错表**缺陷，随整段注释一起被冻结。
- **证据坐标**：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmCommonServiceImpl.java:181`、`:186`、`:356-390`（`dealRefundResult` 写 `refundOrderMapper` / `tvmOrderMapper`）、`:422-462`（`dealAppRefundResult` 写 `appRefundOrderMapper` / `tvmAppOrderMapper`）。
- **建议裁决**：这段若复活，MUST 先按 `businessType` 三值互斥重写分派（`APP_REFUND` 单独一支走 APP 表）；同时 MUST 回查历史数据：若线上曾跑过这套逻辑，APP 退款单可能被写进了 `REFUND_ORDER` 而不是 `APP_REFUND_ORDER`。

#### 五、对外返回的 `refundNo` 与实际落库的 `refundNo` 不是同一个

- **注释说什么**：`// 如果refundNo不为空，则证明退款结束` 这句被复制在五处活代码上方，暗示「`refundNo` 是本次退款的唯一标识」。
- **实际是什么**：出票故障 + 扫码取票那一支生成了**两个**退款单号 —— `generateRefundOrderNo()`（走 `orderSeqMapper.nextval()` + `OrderNoUtils`）只被 `result.put("refundNo", refundOrderNo)` 放进返回给设备的报文，真正传给 `appOrderService.doRefund` 并落库的是另一个 `OrderCommonUtils.getRefundNo()`。设备侧拿到的号在库里查不到。另外，注释版 `doRefund` 用 `!StringUtils.isEmpty(refundNo)` 当「退款结束」判据，而 `refundNo` 是本地生成、恒不为空，等于恒真。
- **证据坐标**：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:650-654`、`:682-685`、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTopupServiceImpl.java:495-502`（恒真判据，注释形态）、`:668`、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:700`、`:740`。
- **建议裁决**：MUST 统一成一个号（生成一次、既落库又返回），并把那五处注释改成「`doRefund` 返 true 只表示退款请求已收口，结果成败看 `REFUND_ORDER.REFUND_STATUS`」。改前 MUST 确认设备/支付中心有没有把返回的 `refundNo` 用于后续查询 —— 若在用，则这是一个对外契约缺陷、不只是内部不一致。

#### 六、挂起上限 15 秒的约束只写在注释里，没有代码防护

- **注释说什么**：`tvm.takeTicket.waitMillis` NEVER 配到 15000 以上，否则 TVM 收到网关 504；依据是 `fep-app-vr` 的 `/itptvm/` 路由没配 timeout、走 Envoy 默认 15 秒。
- **实际是什么**：该值是纯 `@Value` 注入、无上界校验，Deployment 一条 env 就能顶到 30000；而且真实耗时是「`waitMillis` + 每轮回查 SQL 的耗时」，默认 10000 并没有留出足够余量。约束在代码里完全不可见。
- **证据坐标**：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTakeTicketServiceImpl.java:54-60`、`:132-151`（deadline 计算）、`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/support/TakeTicketWaiter.java:35`。
- **建议裁决**：二选一并记 ADR —— (a) 在 VS 的 `/itptvm/` 路由上显式配 timeout 并把 `waitMillis` 的上界写成启动期校验；(b) 维持现状但把这条约束同时写进 `docs/ops/流量切换.md`（改那条 route 的人看不到 Java 注释）。**NEVER 只靠这行注释兜着。**


**来源：mapper / SQL / 配置**


#### 矛盾与待裁决条目

来源同 `/tmp/cpmig/g3.txt`。每条按「注释说什么 / 实际是什么 / 证据坐标 / 建议裁决」四行写。

**1. 线路段的 join：Java javadoc 与 XML 注释互相矛盾**

- 注释说什么：`ReconExportMapper` 的两处方法 javadoc 明写「线路段来自 `LEFT JOIN STATION_INFO S ON S.STATION_CODE = T.IN_STATION_CODE`」「MUST 保持 LEFT JOIN，INNER JOIN 会整组漏账」，且 `@return` 列表里带 `LINE_CODE`。
- 实际是什么：XML 的共同约定第 7 条写「2026-09-16 起线路段改由 recon-server 统一补齐，本模块四条 PAY 汇总 SQL 都不再出线路列，原先两条 join 现已全部删除，NEVER 加回」；同段还写明四条 select 现在都是单表查询。
- 证据坐标：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/mapper/ReconExportMapper.java:200-205`（方法 javadoc 块 `:39-58`）与 `:230-233`（方法 javadoc 块 `:74-88`）对 `collect-pay-server/src/main/resources/mapper/ReconExportMapper.xml:5-73` 第 7 条。
- 建议裁决：**以 XML 为准**（它带明确日期与收口理由，且描述的是当前 SQL 形态）。Java javadoc 的 join 段与 `@return` 里的 `LINE_CODE` 属过期文字，建议同步订正为「线路段由 recon-server 补齐、本方法不出该列」，但车站段取 `IN_STATION_CODE` 的两条理由（同行不自相矛盾、`OUT_STATION_CODE` 大面积为 NULL）在两处都成立、MUST 保留。本次只做抽取、未改仓库文件。

**2. `TBL_BOM_ORDER_PAY.TRANS_TYPE` 取值：仓库内三处证据互相矛盾**

- 注释说什么：`entity/BomNoCashOrder.java:19-31` 的字段注释清单是 `02` 超时更新、`03` 超程更新、`04` 未出站更新、`05` 无入站更新、`06` 退卡退票、`22` 充值、`2A` 黑名单锁定、`2B` 锁定解除、`42` 行政处理，**整份清单里没有「购票 / 发售」这一项**。
- 实际是什么：`constant/BomBusinessCodeEnum.java:4-5` 是 `SALE("01","充值")` 与 `TOPUP("22","充值")`，两个枚举的中文描述都写成「充值」，`01` 到底是发售还是充值无法判定；而 `service/impl/BomOrderServiceImpl.java:950` 的 BOM 发售建单硬编码 `order.setTransType("01")`，与那份不含发售的清单直接冲突；另有一条取值来源是设备上送（同类 `:931` 取自请求体），生产库里必然还存在 `02/03/04/05/06/2A/2B/42` 这些既非购票也非充值的行。
- 证据坐标：`mapper/ReconExportMapper.java:92-120`、`mapper/ReconExportMapper.xml:152-170`、`mapper/ReconExportMapper.xml:75-85`（注释内已逐条列出上述三处坐标）。
- 建议裁决：**维持现状「整表归 BOM/TVM 发售组并标注」**，发售组金额偏大是可解释的口径问题、错拆是错账；`ITP.PAY` 的 BOM 行政处理（`idx 15/16`）与 BOM 处理（`idx 19/20`）继续恒 0。向甲方索取「哪些 `TRANS_TYPE` 计发售、哪些计充值、哪些计行政处理」的明确判据后再拆，届时 MUST 同步改本 select 与 `ReconExportService`，并同步修正 `BomBusinessCodeEnum` 那两个都写「充值」的描述。

**3. resultMap 与 DDL 不一致的三个列**

- 注释说什么：`TBL_TVM_APP_ORDER` 的 `DEVICE_ID`「resultMap 有、DDL 无，属仓库已知不一致」；`TBL_TVM_ORDER_TOPUP` 的 `PAYCENTER_ORDERNO` / `PAYCENTER_CHANNELORDERNO`「DDL 里也没有，mapper XML 在读写它们，属仓库已知不一致」。
- 实际是什么：对账侧的处置是**回避不引用**（设备编号段由 Java 补空串，两个支付中心单号汇总用不到）；但注释同时承认**联机链路的 mapper XML 仍在读写那两个 `PAYCENTER_*` 列**，即联机路径上存在 `ORA-00904` 风险且未闭合。
- 证据坐标：`mapper/ReconExportMapper.xml:129-134`、`mapper/ReconExportMapper.xml:104-113`、`mapper/ReconExportMapper.java:74-88`。
- 建议裁决：对账侧维持「不引用」。联机侧 MUST 单独核对 `AFCITPDB` 里 `TBL_TVM_ORDER_TOPUP` 与 `TBL_TVM_APP_ORDER` 的真实列（`USER_TAB_COLS`），确认是「DDL 文件过期」还是「代码在读不存在的列」；两种成因的修法相反（前者补 DDL 文件、后者删读写），**NEVER 凭注释直接下结论**。

**4. `TRANS_AOUNT` 拼写：DDL 原文缺一个字母**

- 注释说什么：金额列名是 `TRANS_AOUNT`，「DDL 原文就少一个 M，NEVER 顺手改成 `TRANS_AMOUNT`」，改了就是 `ORA-00904`；证据坐标注释里写的是 `collect-pay-server/sql.txt` 第 136 行。
- 实际是什么：两处注释（Java javadoc 与 XML）口径一致，无内部冲突；风险在于**判据是仓库内的 `sql.txt`，不是库内实际列名**。
- 证据坐标：`mapper/ReconExportMapper.java:92-120`、`mapper/ReconExportMapper.xml:5-73` 第 5 条、`mapper/ReconExportMapper.xml:152-170`。
- 建议裁决：**保持现状拼写**，只在文档标注；若哪天要改列名，MUST 先在 `AFCITPDB` 用 `USER_TAB_COLS` 核实真实列名，再全仓 grep 两处引用一起改，NEVER 只改一处。

**5. `REFUND_AMOUNT` 的历史脏值：代码兜底与数据质量的取舍**

- 注释说什么：`REFUND_AMOUNT` 是 `VARCHAR2`，库里存在非数字与量级写错的历史脏值（2026-09-14 实测 BOM 侧有 `'0'`、也有付 1 分却记 300 的行），因此 SQL 内按 `REGEXP_LIKE` 只取纯数字行，**NEVER 去掉那个过滤**。
- 实际是什么：过滤只保证「查询不炸」，被过滤掉的脏行**不计入已退金额**，因此超退闸门在有脏行时会**高估可退余额**；量级写错的行（记 300 实付 1 分）本身仍会被计入。
- 证据坐标：`mapper/AppRefundOrderMapper.java:48-61`、`collect-pay-server/src/main/resources/mapper/AppRefundOrderMapper.xml:55-61`。
- 建议裁决：`REGEXP_LIKE` 过滤 MUST 保留；同时把脏值当**数据侧待办**处理，先按 `REGEXP_LIKE` 反向查出全部非数字行与「退款额大于原支付额」的行并给业务定归属，**NEVER 直接批量改数**（钱可能已退）。

**6. `recon.internal-token` 的注释与鉴权现状冲突**

- 注释说什么：`application.yml:108`「内部接口共享令牌，默认必须为空、由 K8s Secret 注入 `RECON_INTERNAL_TOKEN`」。
- 实际是什么：`X-Recon-Token` 鉴权已按用户 2026-09-11 的明确要求整段删除，四个源的 `ReconExportController` 与 `ReconInternalController` 都不再校验、`ReconClient` / `ReconExportClient` 也不再发送，该键**已无读取方、不需要注入**（属有意为之的临时降级，上线前 MUST 恢复）。
- 证据坐标：`collect-pay-server/src/main/resources/application.yml:108`（注释）对 `AGENTS.md` §2.2.2 该条现状记载。
- 建议裁决：该注释已过期，**NEVER 据它去 K8s 里注 Secret**；上线前恢复鉴权时再让注释与代码同时生效，恢复动作 MUST 四个源与 recon-server 一起做。

**7. 车站段与线路段的空缺：注释承认无数据源**

- 注释说什么：`TBL_TVM_ORDER_TOPUP` 与 `TBL_BOM_ORDER_PAY` 无车站号列，车站段与线路段只能留空，「待甲方补数据源（例如要求报文上送车站码）或明确接受空值」，且 NEVER 拿 `DEVICE_ID` 猜。
- 实际是什么：线路段收口到 recon-server 后，空的**成因变了**（从「本模块拿不到车站码」变成「这两组本身就没有车站段」），结论不变；`ITP.PAY` 这两组的车站段与线路段在产出文件里恒空。
- 证据坐标：`mapper/ReconExportMapper.xml:104-113`、`mapper/ReconExportMapper.xml:152-170`、`mapper/ReconExportMapper.xml:5-73` 第 7 条。
- 建议裁决：向甲方澄清是否接受空值；若要求补数据源，只需本模块把车站段填上、线路段会由 recon-server 自动补齐，**届时不需要在本文件里加任何 join**。

**8. `ITP.BUS` 优惠段恒 0 与甲方 4 段规格的落差**

- 注释说什么：BUS 规格 4 段为「日期 | 对账金额 | 付款金额 | 优惠金额」，而优惠金额本模块恒 0，NEVER 拿票价与实付之差推算。
- 实际是什么：四张表 DDL 均无优惠或折扣金额列（已逐表核对），因此优惠段无数据源可填。
- 证据坐标：`mapper/ReconExportMapper.xml:186-196`、`mapper/ReconExportMapper.java:124-131`。
- 建议裁决：作为**数据侧空缺**报给甲方，与 gate-txn-pay 的同型空缺一并澄清；在拿到数据源前维持恒 0，NEVER 用推算值填账。

**9. `testngbackV2` 残留在本次输入内不可见（覆盖面提示，非注释间的矛盾）**

- 注释说什么：本次筛出的 g3 注释块内**没有**任何 `testngbackV2` / `localhost` / 占位域名相关注释；`notice-app-*-url` 三条（`application.yml:36/37/39`）本身不带注释，因此未进入本次输入。
- 实际是什么：这三条 `testngbackV2` 残留确实存在于同一个 `application.yml`，属已知未修复 P0，判断线上实际值 MUST 查 Deployment env。
- 证据坐标：`collect-pay-server/src/main/resources/application.yml:36`、`:37`、`:39`（无注释行，g3 未收录）。
- 建议裁决：迁移 docs 时把这三条按「配置残留」写进运维清单口径，**NEVER 因为本次抽取里没有对应注释就认为已清干净**。


### 墓碑清单

> 判据同前几轮：**代码里删了就再也无处可查**，因此逐条留「删了什么 / 为什么 / NEVER 恢复的理由 / 证据坐标」。本轮的墓碑来源是 `collect-pay-server/src/main` 的全量注释与 `face-pay-server/src/test`；与前三节的墓碑清单**不重复**。
>
> **本模块的墓碑有一类特别形态：整段被注释掉的死代码块**（`g2b` 的 196 个注释块里约 170 块属于此类，集中在 `service/impl/TvmCommonServiceImpl.java` 与 `service/impl/TvmOrderServiceImpl.java`）。这类块里**有判据价值的只有少数几处**（例如 `getBomOrderSalePre` 那段是「`TRANS_TYPE` 缺 BOM 发售取值」的唯一物证），其余是历史实现的逐行副本。**删除时 MUST 逐块判断：带判据的转成一行式说明留在原处或搬进本清单，纯副本才整段删。**


**来源：非 service 侧**


#### collect-pay-server g1 墓碑条目（已删除 / 已废弃 / 已移除 / 不再使用）

来源同 `frag_g1.md`。每条记「删了什么、为什么、NEVER 恢复的理由」。

- **`controller/ci/app/CollectPayController` 整个类被注释掉（含 package / import / 类声明 / 4 个端点）**。删的是 APP 域四个旧端点：`requestPay`（请求支付）、`payQuery`（支付查询）、`requestRefund`（请求退款）、`refundQuery`（退款查询），以及它们依赖的 6 个 req DTO 与 4 个 resp DTO（`RequestPayReqDTO` / `PayQueryReqDTO` / `RequestRefundReqDTO` / `RefundQueryReqDTO` / `RequestPayRespDTO` / `PayQueryRespDTO` / `RequestRefundRespDTO` / `RefundQueryRespDTO`）与 `CollectPayService`。**为什么**：APP 域已由 `TvmAppOrderController` 的 IF8A-20 / IF8A-11 / IF8A-18 + 退款三接口取代，URL 前缀与报文骨架都换了。**NEVER 恢复的理由**：恢复会在同一模块产生两套 APP 支付/退款入口、且旧的一套用 `@RequestBody` JSON 而现行契约是 `form-data` + `bizData`，两者不可能共存于同一份甲方契约。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/controller/ci/app/CollectPayController.java:1-65`
- **`PayCenterCommon.buildNoticeAppRefundResultRequest(...)` 整段被注释掉**。删的是「ITP 向 APP 侧发退款结果通知」的支付中心报文构造：`merchantNo` / `apiVersion` / `signType` / `charset` 取配置，`bizData` 是 `LinkedHashMap` 按序放 `orderNo` / `refundType="01"` / `refundResult` / `refundResultDesc` / `refundDate` / `refundAmount`，再 Base64(JSON) 后 `signUtils.signRequest` 加签。**为什么**：退款结果通知改由 APP 域自身的「退款结果通知」端点（支付中心通知 ITP 方向）承载，出向组装不再走这里。**NEVER 恢复的理由**：其中 `refundType` 是硬编码 `"01"`、`bizData` 依赖 `LinkedHashMap` 的插入顺序做加签，一旦恢复会与现行加签口径产生第二套顺序约定；同时 `noticeAppRefundDTO.setSign(null)` 那行说明它曾靠「置空 sign 再重算」绕过验签，属不可复用写法。`.../common/PayCenterCommon.java:257-293`
- **`PayCenterCommon:178` 的 `bizDataMap.put("orderNo", order.getPayCenterOrderNo())` 被注释**。删的是「出向 `orderNo` 取支付中心订单号」这一取值口径，现行取的是我方订单号。**NEVER 恢复的理由**：`orderNo` 字段的归属方一旦切回支付中心订单号，我方按订单号做的幂等与回查（`/internal/app-order` 的 `orderNo` 幂等键）就对不上，属跨域主键混用。`.../common/PayCenterCommon.java:178`
- **`model/request/app/RequestPayResultReqDTO` 整个类被注释掉**（package / import / 类声明 / `userId` 与 `orderNo` 的 getter setter / `toString`）。**为什么**：IF8A-18 支付结果查询改用现行 DTO 与 `bizData` 解析路径。**NEVER 恢复的理由**：它 `extends BaseRequestDTO`（把公共参数与业务参数混在一个类里），与现行「公共参数走 `ItpCommonFormRequest`、业务参数走 `bizData` 单独 DTO」的分层相反，恢复即回退报文骨架收口。`.../model/request/app/RequestPayResultReqDTO.java:1-44`
- **`model/request/tvm/APPRefundNotiResultReqDTO` 的类声明被注释**（`public class APPRefundNotiResultReqDTO extends BaseRequestDTO`）。同上一条同源：APP 退款通知入参不再用 `extends BaseRequestDTO` 的形态。**NEVER 恢复的理由**：该类放在 `model/request/tvm/` 包下却承载 APP 域退款通知，包归属本身是错的，恢复会把 APP 域契约又混进 TVM 包。`.../model/request/tvm/APPRefundNotiResultReqDTO.java:13`
- **`RequestQueryRefundReqDTO.refundOrderNo` 字段被注释**。删的是「退款查询按退款流水号查」这一入参。**为什么**：本模块退款查询改为按订单号查。**NEVER 恢复的理由（重要且有反例）**：支付中心网关侧的退款查询恰恰**只认 `merchantRefundNo`**，只送 `refundOrderNo` 会被网关拒（AGENTS.md §8 记有该实测：返 `code=9999`「退款流水号或商户退款流水号必填」，表现为退款单永久空转而端点每轮返 `0000`）。因此这个字段名在**出向**方向是坑，恢复到入参上只会再引一次同名混淆。`.../model/request/RequestQueryRefundReqDTO.java:13`
- **`TvmOrderController` 里两条 `8999` 参数校验被注释掉**：`orderNo` 为空返 `RequestPaymentRespDTO.fail("8999","orderNo不能为空","FAILED","参数错误")`、`paymentVendor` 为空返同形失败。**为什么**：TVM 域的失败码空间是 `2xxx`（`2999` 失败 / `2002` 非法参数），`8999` 是 BOM 域的失败码，写在 TVM 应答里是串域错误。**NEVER 恢复的理由**：恢复等于在 TVM 应答里混进 BOM 错误码，设备侧按 `2xxx` 解析会落到未知码分支；要补校验 MUST 用 `TvmPayCodeEnum.INVALID_PARAM`（`2002`）。`.../controller/ci/tvm/TvmOrderController.java:130,134`
- **`SignUtils` 的 `private final String SIGN_ALGORITHM = "SHA256WithRSA"` 被注释**。**为什么**：本模块出向加签算法不再由这个常量固定，改由配置的 `signType` 决定（`00` 不签名 / `01` sha1withrsa / `02` MD5）。**NEVER 恢复的理由**：把算法写死成 `SHA256WithRSA` 会与 `signType` 配置产生两个真相源；且 AGENTS.md §4 已确立「签名算法不统一、`SHA256WithRSA` 仅用于渠道对接方向」，恢复即在设备域引入错误算法。改这里属安全红线，**MUST 人工复核**。`.../utils/SignUtils.java:27`
- **`SingleTicketRefundTask` 的三个 `@PostMapping` 被注释掉**（`/refundAppNotTakeTickets`、`/refundBomSaleNotTakeTickets`、`/refundBomTopupNotTakeTickets`），只留 `@Scheduled`。**为什么**：这些补偿改由本模块 `@Scheduled` 自驱，不再暴露 HTTP 入口。**NEVER 恢复的理由 / 注意**：这与方法内日志「收到由 web-server Quartz 定时任务发起的调用」**互相矛盾**（见 `frag_g1_contra.md` 第 9 条）；在裁决清楚「到底谁触发」之前恢复 HTTP 入口，会出现「Quartz 与本模块 `@Scheduled` 同时跑同一批退款」的双跑风险，而这四个任务**没有分布式锁**。`.../task/SingleTicketRefundTask.java:33,68,86`
- **`App_Pay_Logs` 的渠道类型 `2`（ETC 端，tradeType `06`）已弃用**，只有 `1`（APP 端，tradeType `03`）在用。**NEVER 恢复的理由**：注释直接标注「已弃用」，新写查询与统计 MUST NOT 把 `2` 当有效渠道纳入口径；若历史数据里存在 `2`，属遗留数据、不代表在跑的链路。`.../entity/AppPayLogs.java:44-46`
- **`X-Recon-Token` 共享令牌校验已整段删除**（`ReconExportController` 不再校验、`ReconExportClient` 不再发送）。**为什么**：用户 2026-09-11 明确要求「删除令牌要求，不用令牌了，当前处于开发测试阶段」。**NEVER 恢复的理由（有前提）**：这不是「永久不要」，而是**有意为之的临时降级，上线前 MUST 恢复**；恢复时的形态已在注释里写死（重新引入 `recon.internal-token` 配置 + 请求头比对 + `MessageDigest.isEqual` 定长比较 + K8s Secret 注入），**NEVER 用 `String.equals` 比对、NEVER 在仓库写默认真值**。`.../controller/internal/ReconExportController.java:11-24`
- **运营端「指定金额退款」请求体刻意不开放退款原因字段**。**为什么**：`AppOrderServiceImpl.doRefund` 把 `REFUND_REASON` 硬编码为「业务操作失败」，接收一个会被丢掉的原因字段等于骗调用方。**NEVER 恢复的理由**：要支持自定义原因得改 `doRefund` 的方法签名，那是旧链路、本次明确不动；只加 DTO 字段不改 `doRefund` 会得到「字段填了但库里还是硬编码原因」的静默不一致。`.../controller/page/AppPartialRefundRequest.java:3-9`
- **`DateUtils` 的 `main` 方法（含 `getTime(-1,"yyyy-MM-dd") + " 00:00:00"` 的调试拼接）被注释**。属调试残留，无业务语义。**NEVER 恢复的理由**：该拼接用的是横线格式 `yyyy-MM-dd`，而 `DateUtils` 两个偏移方法的入参约定是斜杠格式（`yyyy/MM/dd`、`yyyy/MM/dd HH:mm`），恢复这段会给后人一个错的格式样例。`.../utils/DateUtils.java:24-25`


**来源：service 层（上）**


#### 墓碑类条目（已删除 / 已废弃 / NEVER 恢复 / 不再使用）

- **`AppOrderService.receivePaymentResult(JSONObject)` 已整体注释掉、不再作为接口方法存在**。原语义是「第三方支付通道通知 ITP 平台支付结果，ITP 更新订单状态并通知 APP」。该职责现由 `payNotice` 一侧承担（按前置单 `TRANS_TYPE` 分派）。`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/AppOrderService.java:58`、`:63`
- **`BomOrderService.requestGetPayResult(RequestGetPayResultReqDTO)` 已注释掉**，BOM 查询支付结果的现行入口是 IF8A-06 那条（应答 `SUCCESS` / `FAILED` / `PROCESSING`）。`.../service/BomOrderService.java:45`
- **`TvmCommonService.getPayCenterRefundResult(payOrderNo, refundNo, refundAmount, businessType)` 四参签名已废弃**，接口与实现里那份四参轮询实现均整段注释。现行签名只收 `refundNo`（`tvmCommonService.getPayCenterRefundResult(refundNo)`，`BomOrderServiceImpl` 的异步查询在用）。**NEVER 恢复那份四参实现**：它内部同时包含 `refundTime.substring(0, 8)`（已确认为 2026-08-27 生产事故根因）和一段 `businessType` 分支，其中 `APP_REFUND` 同时出现在 `if` 与 `else if` 两个条件里、第二支永远不可达 —— 正是「APP 主动退款被排除在通知之外」那起事故的同型缺陷。`.../service/TvmCommonService.java:16`、`.../service/impl/TvmCommonServiceImpl.java:159-189`
- **`BomOrderServiceImpl.requestTicketTRefund(RequestTicketRefundReqDTO)` 已整段注释掉**（约 40 行，含 `generateRefundOrderNo` / `insertTicketRefund` / `doRefund` / `updateBomRefundOrder("2","退款失败")` 全套分支）。BOM 侧退款现由 `notiBusResult` 的 FAILED 分支与出票数量差额分支承担。**NEVER 直接解注释复活**：它把「退款失败」与「退款成功」都走同一个 `updateBomOrder` 回写，且没有超退闸门。`.../service/impl/BomOrderServiceImpl.java:1441-1479`
- **APP 订单号的 24 位旧格式（`00` + `yyyyMMddHHmmss` + UUID 前 8 位）已废弃**，2026-09-11 起统一为 20 位 `ProductType(2) + yyyyMMddHHmmss(14) + 序列(4)`，与 BOM 及 face-pay 同口径。历史 24 位订单不受影响、**NEVER 回改新单格式**。`.../service/impl/AppOrderServiceImpl.java:387-396`
- **对账 ITP.PAY 里「靠 `LEFT JOIN STATION_INFO` 取 `LINE_CODE`」的两条写法（TVM 购票、APP 购票）已整段删除，NEVER 加回**。2026-09-16 起线路段由 recon-server 在写文件前按车站码统一补齐（`ReconStationMapper` + `recon.line-backfill.*`），且 recon-server 侧是**无条件覆盖** —— 加回来不改变产出、只让「线路怎么取」重新分叉成四份副本；`STATION_INFO` 的 owner 也不是本模块。`.../service/ReconExportService.java:301-310`
- **`BomOrderServiceImpl` 的 `notiTopupResult` 上的 `@Transactional` 已于 2026-09-14 摘除，NEVER 加回**。理由是 AGENTS.md §5.2「`@Transactional` 方法内 NEVER 发起任何 RPC」：失败分支要调支付中心退款，被事务包住会让 `TBL_TVM_ORDER_TOPUP` 那行的排他锁持有时长等于支付中心响应时长，重推堆积、连接被 Druid `remove-abandoned-timeout` 强杀、`commit` 抛 connection closed，连「通知已入库」的 INSERT 一起回滚。`.../service/impl/BomOrderServiceImpl.java:686-703`
- **`refundTime.substring(0, 8)` 这种取退款日期的写法已废弃、NEVER 恢复**（2026-08-27 生产事故，订单 `0020260827131329f68e95f4`）。支付中心返 ISO 形态 `2026-08-27T12:55:57`，截前 8 位得到 `2026-08-`，APP 侧解析 `refundDate` 直接失败。现行做法是剥掉全部非数字字符再取前 14 位。`.../service/impl/AppOrderServiceImpl.java:712-721`
- **「用 `businessType` 把 APP 主动退款排除在退款通知之外」的判断已废弃、NEVER 回退**（2026-08-27 生产事故，订单 `00202608271248044c519a98`）。原代码只放行 `TVM_SCAN_QR_TAKETICKET`，而 APP 退款走 `BusinessTypeEnum.APP_REFUND`，结果退款成功、`REFUND_STATUS=1`，但 `TBL_NOTICE_APP_REFUND_RECORD` 零条、APP 永远停在「退款进行中」。`.../service/impl/AppOrderServiceImpl.java:641-645`
- **ITP.DETAIL 已从本模块的产出清单中移除，本模块 NEVER 产出该文件**（改由 gate-txn-pay 与 daily-ticket 负责）。收到 `DETAIL` 或 `EXP` 指令只记 warn 并跳过；**NEVER 拿本模块四张收款表去凑 DETAIL 行**，纯文本分片没有 schema、错账下游读不出来。`.../service/ReconExportService.java:203-208`
- **`BOM_SALE` / `BOM_PAY` 两个交易类型常量已注释掉、不再使用**（`BOM_SALE = DeviceTypeEnum.BOM.getCode()`、`BOM_PAY = "02"`），连带 `getTransAmountByTransType(transType)` 那个按 `BOM_SALE` 分金额的私有方法也整段注释。这与 §对账里记的「`TBL_BOM_ORDER_PAY.TRANS_TYPE` 三处证据互相矛盾」是同一个未收口问题，**NEVER 凭这些残留常量重建 `TRANS_TYPE` 语义**。`.../service/impl/BomOrderServiceImpl.java:63-64`、`:612-615`
- **`updateParams.put("payDate", DateUtils.getNowTime())` 与 `order.setMerchantOrderNo("")` 两处赋值已注释**，说明支付日期与商户订单号不在该分支回写；排查「`PAY_DATE` 为空」时 NEVER 以为这里会写。`.../service/impl/AppOrderServiceImpl.java:273`、`:376`
- **出票结果通知里「按数量差额直接 `handleRefund` 并打印 refundAmount」那段已注释**，现行实现改走独立的退款分支。`.../service/impl/BomOrderServiceImpl.java:1179-1181`
- **`BomOrderServiceImpl:311` 那条「直接返 `PROCESSING`」的短路应答已注释**，BOM 扫码支付不再无条件返处理中。`.../service/impl/BomOrderServiceImpl.java:311`


**来源：service 层（下）**


# frag_g2b 墓碑类条目

判据：g2b.txt 的 196 块注释里，**约 170 块是整段注释掉的死代码**（行首 `//` 包住的 Java 语句）。这些块的知识含量分两种：一种只是被删实现的逐行样板（无知识，仅登记块位置，防止后人误当「待启用功能」），另一种承载「为什么删 / 删之前长什么样」的判据 —— 后者已抽进 `frag_g2b.md`，此处只留墓碑坐标与理由。

#### 一、整段注释的功能块（NEVER 当成待启用功能）

- **TVM 退款成功后通知 APP 的整条链路**（轮询取退款结果 → `dealRefundResult` / `dealAppRefundResult` 回写 → 保存通知记录 → 发送 → 回写通知状态）。理由：现行实现只保留 `getPayCenterRefundResult` 这一个只查不写的轮询方法（`:217-260`），退款结果的落库与通知已挪到调用方（`appOrderService.doRefund` / `tvmCommonService.doRefund` + 各自的 `RSV2` 回写）；这段若原样复活会与现行链路重复回写退款单。坐标：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmCommonServiceImpl.java:150-215`（轮询主体）、`:262-322`（`saveNoticeAppRefundResultRecord` + `noticeAppRefundResult`）、`:326-353`（`getPayCenterRefundResult` 旧带写版本）、`:356-390`（`dealRefundResult`）、`:422-462`（`dealAppRefundResult`）。
- **出票结果通知（正常单）里的退款调用**。理由：注释原文给出的判据是「出票张数和订单张数一致才发送取票通知，所以不存在退款可能」，属**有意删除**，NEVER 顺手加回；同一模块的出票**故障**通知仍在退款。坐标：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:434-437`。
- **BOM 售票前置单构造 `getBomOrderSalePre`**（`transType` = `BOM_SCANED_SALE_PAY`）。理由：BOM 侧前置单现由 `BomOrderServiceImpl:235` 写入、取值是 `BOM_SCANED_PAY`，此方法零调用方；但它是「`TRANS_TYPE` 缺 BOM 发售取值」这条对账缺口的**物证**，已收进 `frag_g2b_contra.md`，**NEVER 删这段注释**。坐标：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:271-282`。
- **充值域自带的 `doRefund` 私有实现 + 支付中心下单请求组装 `buildTopupPayCenterRequest`**。理由：两者都已收口到 `payCenterCommon` / `tvmCommonService`，注释版里的 `refundOrder.setRefundReason("充值失败")`、`scene=TOPUP`、`paymentVendor=QR`、`industryType=1` 等取值有参考价值（已抽进 `frag_g2b.md` §五），实现本身 NEVER 复活。坐标：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTopupServiceImpl.java:389-408`（下单组装）、`:415-459`（旧 `doRefund`）。
- **私有 `getStringFromData`**。理由：全模块已统一走 `TransforUtils.getStringFromData`，注释行本身就是改造留痕（连方法签名都写成了 `private String TransforUtils.getStringFromData(...)`，不是合法 Java），NEVER 当代码读。坐标：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:786-788`。

#### 二、纯样板注释块（无知识，仅登记、不入 docs）

- **逐行 `log.info` / `Map.put` / setter 的注释复制**：`TvmCommonServiceImpl` 的 `:190-194`、`:207`、`:211`、`:264-275`、`:281-322`、`:329-353`、`:359-384`、`:425-462` 中的绝大多数单行，`TvmOrderServiceImpl:272-282`、`TvmTopupServiceImpl:390-459` 同类。理由：这些是被注释代码的字面组成部分，不构成独立事实；只在需要复原当年实现时回源码看，NEVER 逐行搬进 docs。
- **旧的失败描述占位赋值**：注释版 `noticeAppRefundResult` 里退款成功与失败两支都把 `refundResultDesc` 赋成字面量 `"refundResultDesc"`（占位符未替换）。理由：是缺陷而非契约，复活该方法 MUST 先修；不值得单列为知识条目。坐标：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmCommonServiceImpl.java:287`、`:290`。

#### 三、待清理但 NEVER 静默删除的 todo

- **激活订单的归属未定**：todo 原文追问「要激活的订单在哪里，预设 tvm 表的订单，如果这样，是否要在这个表中加一个新字段（是否已激活字段）」。理由：现行实现已在 `TvmAppOrder` 上用 `activateFlag` 落地了这个字段，todo 文字**已被实现覆盖但未删除**，容易让后人以为激活状态无处存放。坐标：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmTakeTicketServiceImpl.java:66`（对照实现 `:106`）。
- **站点名 todo**：`singlePickupStationName` / `singleGetoffStationName` 待改中文名，当前填站码。理由：属对外报文的已知偏差、需业务确认取名来源（`para-server` 站点参数），不是可以顺手删的注释。坐标：`collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/impl/TvmOrderServiceImpl.java:816`、`:819`。


**来源：mapper / SQL / 配置**


#### 墓碑条目（已删除 / 已废弃 / NEVER 恢复）

来源：`/tmp/cpmig/g3.txt`（collect-pay-server 的 `mapper/**`、`resources/mapper/*.xml`、`resources/application.yml`、`resources/log4j2-collectpay.xml` 注释块）。坐标一律相对仓库根。

- **`ITP.DETAIL` 的四条 keyset 明细 select 已整段删除，NEVER 在本模块恢复**。理由：`ITP.DETAIL` 按甲方规格（`docs/接口规范文档/ACC与ITP之间的文件.docx` 第一章第 4 节）是「虚拟电子多日计次票文件」，与 TVM / BOM 无关，已改由 gate-txn-pay 与 daily-ticket 负责；连带结论是本模块只剩 `ITP.PAY` 与 `ITP.BUS` 两类汇总、全部聚合、没有明细分页。坐标 `collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/mapper/ReconExportMapper.java:9-35`、`collect-pay-server/src/main/resources/mapper/ReconExportMapper.xml:5-73` 第 1 条。
- **两条 `LEFT JOIN STATION_INFO S ON S.STATION_CODE = T.IN_STATION_CODE` 及 SELECT / GROUP BY / ORDER BY 里的 `S.LINE_CODE` 已全部删除，NEVER 加回**。理由：2026-09-16 起线路段改由 recon-server 在聚合完成、写文件之前按车站码统一补齐（`ReconStationMapper` 与 `recon.line-backfill.*`）；recon-server 侧是**无条件覆盖**，加回来不改变产出、只让「线路怎么取」重新分叉成四份副本，且 `STATION_INFO` 属车站与参数域、owner 不是本模块。坐标 `collect-pay-server/src/main/resources/mapper/ReconExportMapper.xml:5-73` 第 7 条。
- **BOM 订单专用的单程票退款方法已弃用**。理由：注释原文「单程票退款 弃用 这个方法只限制了 bom 订单，改为全类型」，即退款入口已收口为全类型、不再按 BOM 订单收窄。坐标 `collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/mapper/BomNoCashOrderMapper.java:47`。
- **`RefundOrderMapper` 的 `updateRefundStatus(RefundOrder record)` 声明已整行注释掉**（注释形态即墓碑，方法不再存在）。理由：DDL 与注释未写明，仅可确认该签名已停用；同文件 `:28-30` 仍留着「更新退款状态」的 javadoc，对应的是保留下来的那个方法。坐标 `collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/mapper/RefundOrderMapper.java:31`。
- **公共 `log4j2-linux.xml` 对本模块不再参与**。理由：本模块设了 `logging.config` 指向自带的 `log4j2-collectpay.xml`，而 `CustomLoggingConfiguration` 见到 `logging.config` 非空即在第 38 行直接 return；因此本模块的日志行为一律以自带文件为准，改公共文件对 collect-pay 无效。坐标 `collect-pay-server/src/main/resources/application.yml:114-115`、`collect-pay-server/src/main/resources/log4j2-collectpay.xml:2-18`。
- **`application.yml` 里对 `InternalMicroHttp` 的 `logging.level` 压制已移除、搬进 `log4j2-collectpay.xml` 的 Logger 段**。理由：注释原文「原先写在 application.yml 的 logging.level 里，改用本文件后一并搬到这里」；该压制的目的是它会把整个请求头 Map（含内部令牌明文）打进 INFO。坐标 `collect-pay-server/src/main/resources/log4j2-collectpay.xml:129-132`、`application.yml:117`。
- **「只删 Controller 里那句 `log.info` 来降心跳日志量」这条路已被否决**。理由：一次心跳产生 4 行、其中 3 行来自公共构件（`FirstFilter` / `MoreInterceptor` / `BodyCacheFilter`），只删 Controller 那句只能去掉四分之一；改公共 `log4j2-linux.xml` 会对 21 个模块全量生效；用 `logging.level` 压那三个 logger 会连业务报文日志一起干掉，而报文日志是重放与排障的唯一依据。最终形态是「模块自带 `logging.config` + 配置级 `RegexFilter` 按 URL 丢弃」。坐标 `collect-pay-server/src/main/resources/log4j2-collectpay.xml:2-18`。
- **`<Filters>` 写在 `<Properties>` 之前的写法已被实测否决，NEVER 回退**。理由：2026-09-11 实测该顺序下 `${logPath}` / `${appName}` / `${webLoggerLevel}` 全部不做替换，日志文件被建成字面量路径、Root 级别失效、INFO 全丢，且 `status="off"` 时 log4j2 自身报错也被吞掉。坐标 `collect-pay-server/src/main/resources/log4j2-collectpay.xml:21-26`。
- **BUS 优惠金额「拿票价与实付之差推算」这条做法已被否决**。理由：已逐表核对 `TBL_TVM_ORDER_PAY` / `TBL_TVM_ORDER_TOPUP` / `TBL_TVM_APP_ORDER` / `TBL_BOM_ORDER_PAY` 四张表 DDL，均无任何优惠或折扣金额列，因此优惠段由 Java 侧写字面 0。坐标 `collect-pay-server/src/main/resources/mapper/ReconExportMapper.xml:186-196`。
- **「按 `TRANS_TYPE` 把 `TBL_BOM_ORDER_PAY` 拆成购票组与充值组」当前不做**（不是永久废弃，是待甲方判据）。理由与三处矛盾证据见 `/tmp/cpmig/frag_g3_contra.md` 第 2 条；现状为整表归「BOM/TVM 发售」组并标注，`ITP.PAY` 的 BOM 行政处理（`idx 15/16`）与 BOM 处理（`idx 19/20`）两组恒 0。坐标 `mapper/ReconExportMapper.java:92-120`、`mapper/ReconExportMapper.xml:75-85`、`mapper/ReconExportMapper.xml:152-170`。

### 覆盖率自评

**分母（机械统计，剥离器与阶段二同一把：Java 走词法剥离并保护字符串 / 字符 / 文本块，XML 剥 `<!-- -->`，yml 与 properties 剥 `#` 行，SQL 剥行注释与块注释）**

- `collect-pay-server/src/main`：**185 个文件**（Java **164** / mapper XML **20** / `application.yml` **1**），总行 **20004**，注释行 **4448**、注释块 **1818**。与用户给的口径（4531 行）差 **83 行**，差额在「块注释首尾行与空注释行的计入方式」，**两个数都对，引用时 MUST 说明口径**。
- `face-pay-server/src/test`：**13 个文件**、总行 **2563**、注释行 **356** / **119 块**（用户口径 351 行，差 5 行同因）。
- `face-pay-server/src/main/resources/sql/f2f-schema.sql`：**312 行，`COMMENT ON` 实测 51 条**（`grep -c 'COMMENT ON'`）。用户给的是 54 条，**差 3 条**；本轮按实测 51 条全量抽取，**判「有没有漏」MUST 现跑 `grep -c`，NEVER 引用任一方的数字**。

**分子（本轮实际产出，全部落在本节）**

- **docs 净增 893 行 / bullet 520 条**（不含本小节）。分布：
  - 契约与判据四组 **235 条**（附二.1 非 service 侧 77 / 附二.2 service 上 71 / 附二.3 service 下 36 / 附二.4 mapper 与配置 51）；
  - 模块定位（附二.0）**5 条**；
  - `src/test` 断言判据（附二.5）**23 条**；
  - DDL 列语义与取值域（附二.6）**62 条**，覆盖 51 条 `COMMENT ON` 全量；
  - **矛盾与待裁决**：三条取值域矛盾的**库侧裁决 3 条** + 各来源新增 **148 行四段式条目**（非 service 14 组 / service 上 8 组 / service 下 6 组 / mapper 与配置 9 组，共 **37 组**）；
  - **墓碑清单 46 条**（非 service 13 / service 上 14 / service 下 9 / mapper 与配置 10）。
- **过滤掉的分母**：**1078 块 / 2065 行**判为纯样板（全部行都是 `@param` / `@return` / `@throws` / 孤立的星号与斜杠、或「字段名中文直译」型单行），未逐条搬运。

**判为「零知识」或本轮刻意不搬的**

- `collect-pay-server` 的 20 个 mapper XML 里有 **多个整文件零注释**（如 `TvmTakeTicketOrderMapper.xml`、`TvmTopupOrderMapper.xml`），排查它们的 SQL 只能读语句本身。
- `service/impl/TvmCommonServiceImpl.java` 与 `service/impl/TvmOrderServiceImpl.java` 里**约 170 块是整段注释掉的死代码**，本轮只抽出其中带判据的少数几处（`getBomOrderSalePre` 是最重要的一处），其余按「历史实现逐行副本」处理。
- `face-pay-server/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` 是**无注释的开关文件**，但它决定了 mock maker 行为 —— 这就是附二.5 里「`final` 类造不出替身」那条的根因所在，**改它等于同时改掉一批测试的可行性**。

**本轮自知的未覆盖面（留给后续轮次）**

1. **`collect-pay-server` 的 `pom.xml` / `log4j2-*.xml` / K8s 描述文件注释未纳入**（与阶段二对 face-pay 的口径一致）。
2. **`face-pay-server/src/main` 本轮完全没读**（阶段一 + 阶段二已覆盖，其删除动作按用户要求**留给后续统一处理**，本轮一行未动）。
3. **`f2f-order-refund-summary-migration.sql` 等其它 SQL 是否也带 `COMMENT ON` 未系统核对** —— 阶段二的假阴性告诉我们「按行注释语法扫描」会漏掉这类，**核对 MUST 按 `COMMENT ON` 关键字重扫全部 `*.sql`**。
4. **三条库侧裁决只写进了 docs，DDL 与实体注释本身还没改**（订单号那半句、`EXPIRE_TMS` 那半句、`F2F_REFUND` 四值那条）；改 DDL 列注释需要一条独立的 `COMMENT ON` 迁移语句 + 当场回查，属下一轮动作。
5. **`TRANS_TYPE` 三处矛盾仍未闭合**，日终对账 BOM 两组度量恒 0 的现状不变，等甲方判据。

### 附二.7 代码侧已同步瘦身（读源码前先知道这条）

- **2026-09-16 本轮已把上面这些知识从代码里删掉了**：`collect-pay-server/src/main`（144 个文件，注释行 4165 至 2062）与 `face-pay-server/src/test`（13 个文件，356 至 115）**只保留标准 Javadoc**（首句摘要 + `@param` / `@return` / `@throws`），叙述段、`MUST` 与 `NEVER` 告示、事故史与墓碑说明**一律不在代码里了 —— 它们的唯一载体就是本节**。因此**在 collect-pay 的源码里搜不到某条约束时 NEVER 判断「这条约束不存在」，MUST 回来查本节**。
- **`face-pay-server/src/main` 本轮一行未动**（它的注释仍是重构期的完整形态，删除留给后续统一处理，避免与其它并行改动冲突）。
- **两类注释是刻意留下的**：①mapper XML 头部的**一行式**「SQL 正文 NEVER 出现行注释或块注释」告警（现存于 `ReconExportMapper.xml`、`AppRefundOrderMapper.xml`），删掉它等于把 AGENTS.md §5.1 那个静默失效的坑重新埋回去；②`service/impl/TvmCommonServiceImpl.java` 与 `service/impl/TvmOrderServiceImpl.java` 里**整段注释掉的死代码块**（含 `getBomOrderSalePre` 那段 `TRANS_TYPE` 物证）—— 它们不是叙述型注释，本轮**没有删**。
- **不变量自证方式（后续轮次照做）**：逐文件用词法剥离器去掉注释后，与 **`svn cat -r BASE`** 及删除前快照两路比对，**157 个文件的代码文本全部逐字一致**（Java 剥离时保护字符串、字符与文本块，XML 剥 `<!-- -->`，yml 剥 `#` 行）；mapper XML 另跑 `xmllint --noout` 全通过，且**没有任何 XML 注释里出现连续两个半角减号**。**NEVER 用 git 做这个基线**（AGENTS.md §7：`qd/` 上层那个旁挂 git 仓库会静默返回陈旧快照）。
