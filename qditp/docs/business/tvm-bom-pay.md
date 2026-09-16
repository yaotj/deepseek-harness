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
