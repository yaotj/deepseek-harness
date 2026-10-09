---
业务域: 日票 / 多日计次票
模块: daily-ticket-server
---

# 提示词：日票 / 多日计次票

## 何时读本文件
日票与多日计次票的下单、支付、退款、取消、激活、进站校验、出站扣次、ACC 通知相关改动。

## 模块定位
`daily-ticket-server`，端口 **9108**，`spring.application.name=daily-ticket-server`
启动类 `daily-ticket-server/.../DailyTicketServer.java`（`@EnableDefaultMybatisAutoConfig`），Oracle 走自研 `other.sql.*`

## 接口清单

`controller/DailyTicketController.java`（前缀 `/ci/daily-ticket`，由 fep-app-server 的 `AppDailyTicketController` 转发）
- IF8A-60 `/requestCountingOrder` — 下单
- IF8A-70 `/requestTravelOrder` — 旅游票下单（聚合单，主单落 `TRAVEL_TICKET_ORDER`，内含日票按张落 `DAILY_TICKET_ORDER` 子单）
- IF8A-61 `/payment/requestPay`
- IF8A-62 `/payment/requestPayResult`
- IF8A-64 `/payment/requestRefundTicket`
- IF8A-65 `/ticket/cancelOrder` —— **2026-09-22 起（1.0.65，ADR-D156）覆盖「日票 + 旅游票 / 未激活即可取消 / 已收款则自动退款」三件事**，详见下面「取消订单与取消后自动退款」那节。**NEVER 回退成「只认 `CREATED`、只查子单表」** —— 那版旅游票主单（`0T` 前缀）永远返「订单不存在」。
- IF8A-67 `/ticket/updateTicket`
- IF8A-71 `/ticket/updateAndNotice`
- 无编号内部接口：`/payment/receivePayResult`（支付回调）、`/queryDailyTicketInfo`、`/queryDailyTicketPayInfo`、`/entry/check`（进站校验）、`/ticket/markUsed`（出站扣次，由 ticket-server `GateTicketHandler` 调用）、`/ticket/rideAvailability`（拉码前置可用性查询）
  - `POST /ci/daily-ticket/ticket/rideAvailability`（2026-09-17 新增，1.0.29，ADR-D126）：**拉码前置的只读可用性查询**，调用方是 `fep-app-server` 的 IF8A-03 `requestIndustryData`（`IndustryDataServiceImpl`，经 `DailyTicketClient.checkRideAvailability`）。
    入参只有 `cardNum`，应答复用 `DailyTicketBaseResult`：`retCode=0000` 可发码，其余不可发、原因在 `retMsg`。
    判据与 `validateEntryCheck` 同源（`selectForEntryCheck` 白名单 + 退款占用 + 有效期 + `ACTUAL_TIMES`），但**只读、不推进任何状态**，且**异常一律吞成 retCode、绝不抛出**。详见下面「拉码前置可用性校验」那节。
  - `/queryDailyTicketPayInfo`（2026-09-15 新增，ADR-D82）：按 `ticketCode` 回溯购票订单，给 IF8A-34 交易详情填
    `payTradeOrderNo` / `payOrderNoDate` / `payChannelCode`（日票过闸免扣费、没有 `PAY_TXN_DETAIL`，这三个字段原先恒空串）。
    链路 `TICKET_CODE` → `DAILY_TICKET_INSTANCE.ORDER_NO` → `DAILY_TICKET_ORDER` 的 `TRADE_NO` / `PAY_DATE` / `PAY_CHANNEL_CODE`。
    调用方是 `trans-query-server` 与 `ticket-server` 两份 `TransDetailQueryHandler.enrichDailyTicketPayInfo`（**逐字段一致，改一处 MUST 同批改两处**）。
    **NEVER 把它合并进 `/queryDailyTicketInfo`** —— 那条是闸机检票热路径，合并等于每次进站都多 join 一次订单表。
    查不到时返 **`0000` + 三个字段 null，NEVER 返失败码**（调用方是交易详情主链路，返失败会把整条 IF8A-34 打挂）。
    `payOrderNoDate` 是**购票付款时刻**、不是过闸时刻，长周期票会显示很早的时间，**属有意为之，NEVER 改成过闸时间**。

`controller/DailyTicketRefundController.java`（前缀 `/page/daily-ticket/refund`，运营页面，强制 `orderType=1`）
- `GET /orders`、`POST /request`、`/pay-query`、`/query`、`/retry`、`/resubmit`、`GET /records`

`controller/internal/BatchRefundInternalController.java`（前缀 `/internal/daily-ticket/batch-refund`，**2026-09-20 新增，甲方需求 16/17，ADR-D151**）
- `POST /daily`（`sys_job` **245**，cron `0 0 20 * * ?`）、`POST /monthly`（`sys_job` **250**，cron **`0 0 20 L * ?`** 月末最后一天）—— 两条 job_id 均于 2026-09-21 由 135 / 136 改号，**NEVER 回退**
- 两条只差回溯窗口（7 天 / 60 天），**候选判据完全相同**：`ORDER_STATUS='PAID'` + `PAY_STATUS='PAID'` + **`DAILY_TICKET_INSTANCE` 无同 `ORDER_NO` 行**（= 未激活，**订单表没有激活列，NEVER 去那里找**）+ `PAY_DATE` 早于 `waitDays`（默认 3 天）
- **独立日票谓词 MUST 带 `PARENT_ORDER_NO IS NULL`，NEVER 去掉** —— 否则旅游票子单会被当独立日票单独退掉、主单进半退状态
- 旅游票走**主单整单退**（`orderType="2"` → `requestTravelRefund`，子单校验已在那里，批量侧 NEVER 重写）
- `DailyTicketBatchRefundService` **只扫表 + 逐笔复用 `requestRefundTicket`**，**刻意不带 `@Transactional`（每笔调支付网关，NEVER 加）**；两个独立 `AtomicBoolean`，busy 返 `9998`（**属限流不是失败**，web-admin 侧只打 WARN）
- 可配键：`daily.batchRefund.limit:200`（**两类各取一次，单轮 scanned 上限 2×limit**）/ `waitDays:3` / `daily.lookbackDays:7` / `monthly.lookbackDays:60`
- **无鉴权**（沿用本模块 internal 端点现状，与 AGENTS.md §5.2 冲突）

> 注意：日票的运营端后台接口在**本模块**，不在 web-server。

### 取消订单与取消后自动退款（IF8A-65，1.0.65 / 2026-09-22，ADR-D156）

甲方需求原话：「日票/旅游票 非已激活状态收到取消订单请求需要后台进行取消订单，如果取消状态订单收到支付结果通知需要自动发起退款」。

**两个入口、同一套处置**（`CanceledOrderRefundService`，退款包内）：
- `DailyTicketOrderCreationService.cancelOrder` —— 取消时订单已是 `PAID`；
- `DailyTicketPaymentService.receivePayResult` —— **已取消的单随后收到支付成功通知**（取消与支付赛跑，钱晚到）。

**取消侧口径**（改任一条 MUST 同批改单测 `DailyTicketCancelOrderTest`）：
- 前置白名单 `CREATED` / `PAYING` / `PAY_FAILED` / `PAID`；`CANCELED` 幂等返 `0000`；`REFUNDING` / `REFUNDED` 拒。**NEVER 写成「非终态即可取消」**。
- 「已激活」判据 = `DAILY_TICKET_INSTANCE` 有同 `ORDER_NO` 行（旅游票 = 任一子单有行），与退款侧 `refundType` 判据同源。**订单表没有激活列，NEVER 去那里找**。
- `PAYING` 先 `queryAndRefreshPayResult` / `queryAndRefreshTravelPayResult` 主动查一次再决策 —— 不查就会把已到账的单按未付取消、漏掉退款。
- 状态推进全走 CAS（`cancelIfPending` / `cancelIfPaid` / `cancelSubOrdersByParent`），**NEVER 退回无条件 `updateOrderStatus`**：与支付回调赛跑时那样写会把已落 `PAID` 的单改成 `CANCELED` 而支付事实仍在。
- 旅游票 = **主单 CAS 成功后**再 `cancelSubOrdersByParent` 批量取消子单，顺序 NEVER 颠倒（反过来写、主单 CAS 失败时会留下「主单还在、子单全没」）。
- 海之巴士单（`ORDER_SOURCE=4`）**一律拒绝取消**（下单即已收款且不走我方网关，退款只能由该渠道发起）；小程序单（`6`）允许取消、退款单落库后等对方同步；免费票（`FREE-` 前缀）允许取消、不出网退款。

**回调侧口径**：`CANCELED` 单走新 CAS `updatePayResultIfCanceled`（`where ORDER_STATUS='CANCELED' and PAY_STATUS <> 'PAID'`），**只补支付事实、`ORDER_STATUS` 仍留 `CANCELED`**，再交给退款链路推 `REFUNDING → REFUNDED`。**NEVER 在这里把订单改回 `PAID`** —— 那等于把已取消的未激活票又变成可激活状态。CAS 落 0 行（重复回调）时**仍调一次退款**，退款服务自身按「已有退款单」幂等短路，这是首次回调落库成功但退款炸了时的唯一补偿出口。

**自动退款口径**：`refundType` 恒 `00` 直退（未激活没有可核验的行程，**NEVER 写 `01`**）、`REFUND_REASON='订单取消自动退款'`、旅游票 `REFUND_SCOPE=TRAVEL_FULL` 按主单整单退（钱按主单那一笔收的）；收口复用 `DailyTicketRefundSettlementService`，因此 IF8B-04 退款结果通知自动走它的 outbox。**出网失败 / 网关未返成功时退款单与订单都留 `REFUNDING`、NEVER 回写 `PAID`**，交运营页 `/pay-query` + `/retry` 人工收口（业主裁决：不加定时任务）。

**已知缺口**：`DailyTicketRefundSettlementService.markRefundFailed` 会把订单置回 `PAID`，因此取消单若走**运营页回查链路**判定退款失败，会回到 `PAID`（未激活 + 已支付 ⇒ 可激活）。该方法被全部手工退款共用（重退要求 `PAID`），本次刻意没动；只有自动退款自己的失败分支不置 `PAID`。

### `POST /resubmit`（1.0.24 新增，退款重提交）

判据只有一个：**`DAILY_TICKET_REFUND.PLATFORM_REFUND_NO IS NULL`**，即支付平台从未受理过这张退款单，此时沿用原 `REFUND_ORDER_NO` 重发是安全的（对端按 `refundOrderNo` 外部幂等）。与 `/retry` **互斥、NEVER 混用**：`/retry` 处理「对端已受理、结果未回」，`/resubmit` 处理「对端从未受理」。
- 前置状态白名单：`REFUNDING` / `FAILED` / `WAIT_VERIFY`，其余状态直接返回已有退款结果、不调支付平台。
- `WAIT_VERIFY` 还要求观察期 `VERIFY_AFTER_TIME` 已过；这是 `REFUND_TYPE='01'`（核验退款）目前**唯一的出口**——`WAIT_VERIFY` 只被写入、全模块无任何代码读取，无补偿任务驱动。
- 落 `DAILY_TICKET_PAY_LOG` 的 `BIZ_TYPE='REFUND_RESUBMIT'`，`DailyTicketPayLogMapper.xml` 的回查语句已把它并入 `BIZ_TYPE in ('REFUND','REFUND_RETRY','REFUND_RESUBMIT')`，否则后续 `/query` 恢复不出 `PLATFORM_REFUND_NO`。
- ⚠️ **无鉴权、无幂等键**，与 §5.2「新增状态变更型接口 MUST 有鉴权」冲突，属测试期临时降级，**上线前 MUST 补验签或收敛为内部 rpc 调用**。
- 修这个「未受理即永久卡死」缺陷的判据可**原样复制到 `face-pay-server`**（同机制，尚未修）。

## 核心流程
下单 `CREATED` → `requestPay` 经 `client/DailyTicketPayGatewayClient.java`（银商网关，**RSA2** 签名，配置 `daily-ticket.pay.*`）
→ 回调或主动查询置 `PAID` → 激活生成票实例 → 进站校验 / 出站扣次
→ 退款：未激活直退 `refundType=00`；已激活需核验退 `refundType=01`

核心类（god class 拆分后的形态，1.0.51~1.0.56）：

- 入口契约 `service/DailyTicketService.java`（25 个方法）。
- `service/impl/DailyTicketServiceImpl.java` —— **纯委派门面，200 行**：25 个 `@Override` 每个只有一行
  `return xxxService.yyy(...)`，注入 8 个协作者（`refundInitiationService` / `refundCallbackService` /
  `travelSubRefundService` / `refundProgressService` / `lifecycleService` / `orderSyncService` /
  `paymentService` / `orderCreationService`），**不注入任何 mapper、没有 logger、没有一行业务逻辑**。
  **NEVER 在它里面找实现**，也 NEVER 往它里面加逻辑 —— 按方法名去下面对应的服务类。
- 本轮拆出的五个服务：
  - `service/lifecycle/DailyTicketInstanceLifecycleService` —— 票实例生命周期：激活 `updateTicket`（IF8A-67）、
    首次使用通知 `updateAndNotice`（IF8A-71）、进站校验 `validateEntryCheck`、拉码可用性 `checkRideAvailability`、
    出站扣次 `markUsed`、使用流水 `queryUsageLog`、票实例查询 `queryDailyTicketInfo`
  - `service/sync/DailyTicketOrderSyncService` —— 小程序（CXUH）订单状态同步 `syncOrder`（IF8A-72），日票 / 旅游票两支
  - `service/payment/DailyTicketPaymentService` —— 支付执行与回调：`requestPay` / `requestPayResult` /
    `queryPayTicket` / `receivePayResult` / `queryDailyTicketPayInfo`，外加 **public** 的
    `queryAndRefreshPayResult` / `queryAndRefreshTravelPayResult`（退款侧要用它们回填 `PAYMENT_ORDER_NO`，
    **是有意放开的跨包入口、NEVER 改回 private**）
  - `service/refund/DailyTicketRefundInitiationService` —— 退款发起：`requestRefundTicket`（IF8A-64）、
    旅游票 `requestTravelRefund`
  - `service/order/DailyTicketOrderCreationService` —— 下单与取消：`requestCountingOrder`（IF8A-60）、
    `requestTravelOrder`（IF8A-70）、`requestOrderFree`（IF8A-73）、`cancelOrder`（IF8A-65）
- 拆分前就存在、本轮未新建的协作类：`service/refund/` 下的 `DailyTicketRefundSettlementService`、
  `DailyTicketRefundCallbackService`、`RefundProgressService`、`RefundGatewayRequests`、`TravelSubRefundService`、
  `DailyTicketTicketLockWriter`、`DailyTicketRefundMessages`；另有 `service/travel/TravelParentSummaryWriter`、
  `service/DailyTicketBatchRefundService`、`service/DailyTicketRefundQueryService`、`service/ReconExportService`、
  `service/support/DailyTicketOrderSupport`、`service/support/DailyTicketInstanceStatus`、
  `service/paylog/DailyTicketPayLogWriter`。

> **拆出来的服务全部零 `@Transactional`，这是刻意的、NEVER 加**：它们的链路里都有支付网关调用，
> 按 AGENTS.md §5.2「`@Transactional` 方法内 NEVER 发起任何 RPC / 网络调用」，加上注解等于把行锁的持有时长
> 绑到对端响应时长上。本模块至今**全模块 `@Transactional` 为 0**，一致性靠落库顺序与状态可判定性。

⚠️ **安全**：`daily-ticket-server` 配置中含商户私钥明文。触碰配置或签名逻辑 **MUST** 提示人工复核，**NEVER** 输出私钥值。

## 数据表
`DAILY_TICKET_ORDER`、`DAILY_TICKET_INSTANCE`、`DAILY_TICKET_PAY_LOG`、`DAILY_TICKET_REFUND`、`TRAVEL_TICKET_ORDER`
DDL：`daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql`

### 旅游票（IF8A-70）落库形态
- 主单 `TRAVEL_TICKET_ORDER`（单号前缀 `0T`）只存聚合信息与支付状态；每张日票是一条 `DAILY_TICKET_ORDER`（单号前缀 `0E`、`ORDER_TYPE='1'`、`PARENT_ORDER_NO` 指向主单）。
- **拆子单是被唯一索引逼出来的**：`UK_DAILY_TICKET_INSTANCE_ORDER ON DAILY_TICKET_INSTANCE(ORDER_NO)` 限定一个订单号只能挂一张票实例，一单挂多票在现有表上无法表达。
- 落库顺序**先子单后主单**：中途失败只留孤儿子单，APP 拿不到 `orderNo` 因而无法支付；反序会留下「可支付但子单缺张」的主单。本模块全局无 `@Transactional`，一致性靠顺序而非回滚。
- `totalAmount` 只做校验、不采信：服务端按 `ticketPrice * ticketCount` 重算，不一致直接拒单；`ticketCount` 上限 `MAX_TRAVEL_TICKET_COUNT=20`（待业务确认）。
- **【契约】主单就是聚合支付的实际承载 —— 主单支付一次、子单永不支付**（2026-09-22 起）。支付 / 退款 / 激活三条链路**早已适配聚合单**，全部以主单为对象：
  - 分支开关是 `orderType`（**不是单号前缀**）：`"1"` = 日票（独立日票与旅游票子单同值）、`"2"` = 旅游票主单，见 `service/support/DailyTicketOrderSupport.java` 的 `ORDER_TYPE_DAILY_TICKET` / `ORDER_TYPE_TRAVEL_TICKET`。`validateOrderNo(orderNo, orderType)` 只校验 `orderType ∈ {"1","2"}` 且 `orderNo` 非空，**NEVER 据前缀判类型**。
  - 支付：`DailyTicketPaymentService.requestPay` 见 `orderType="2"` 即转 `requestTravelPay`，用 `travelOrderMapper.selectByOrderNo(主单号)`、`updatePayRequest` 把主单置 `PAYING`/`PAYING`，送网关的**商户单号是主单号**、金额是**主单 `TOTAL_AMOUNT`**（独立日票才送子单号 + 单张 `TICKET_PRICE`）。回调 `receivePayResult` 先查 `DAILY_TICKET_ORDER`，命中 0 行即转 `travelOrderMapper` → `markTravelPaySuccess` / `markTravelPaySuccessOnCanceled`。
  - 激活：`DailyTicketInstanceLifecycleService.canActivate` 对**子单**（`PARENT_ORDER_NO` 非空）**回看主单**，要求主单 `ORDER_STATUS` 与 `PAY_STATUS` 双 `PAID` 才放行；子单自身恒 `CREATED`/`INIT` 不影响激活。
  - 退款：`DailyTicketRefundInitiationService.requestTravelRefund` 按 `TRAVEL_FULL` 整单退主单 `TOTAL_AMOUNT`。
  - **子单恒 `CREATED`/`INIT`、`PAY_DATE` 恒空是设计，不是故障**（2026-09-23 库实测：`PARENT_ORDER_NO` 非空的 6 行全部 `CREATED/INIT`、`PAY_DATE` 全 null，`PAY_STATUS='PAID'` 的子单 0 行）。**NEVER 拿「子单没支付」判 P0。**
- ⚠️ **本处此前写「支付 / 退款 / 激活链路尚未适配聚合单（本轮只做下单落库）」「聚合支付方案待定」，已过期**（那是 2026-09-16 阶段的中间态）。**NEVER 回退成「主单是聚合壳」「旅游票没有聚合支付」的口径。**

## 状态取值（**无枚举类**；`TICKET_STATUS` 除 `INIT` 外的取值收在常量类 `service/support/DailyTicketInstanceStatus`，其余状态列仍是各服务里的字符串字面量）
- `ORDER_STATUS`：`CREATED` / `PAYING` / `PAID` / `PAY_FAILED` / `CANCELED` / `REFUNDING` / `REFUNDED`
- `PAY_STATUS`：`INIT` / `PAYING` / `PAID` / `FAIL`
- `TICKET_STATUS`：`INIT` / `ACTIVATED` / `USED` / `EXPIRED` / `REFUND_LOCKED` / `REFUNDED`（六态；常量类 `DailyTicketInstanceStatus` 只收了后五个，**`INIT` 没有常量、仍是字面量**。完整语义与终态判定见 §一）
- `ACC_NOTICE_STATUS`：`INIT` / `SUCCESS`
- `REFUND_STATUS`：`REFUNDING` / `WAIT_VERIFY` / `REFUNDED` / `FAILED`

唯一复用的枚举是 `model` 模块的 `CardTypeCodeEnum.QR_POSTPAID`。

改动状态 **MUST** 全局 grep 字面量确认所有比较点；**NEVER** 只改一处赋值。

## 票实例落库与「过期」口径（2026-09-22 补）

### `DAILY_TICKET_INSTANCE` 只有一个插入点
- **【契约】全模块唯一的票实例落库点是 IF8A-67 激活接口**：`DailyTicketInstanceLifecycleService.updateTicket` 里那一次 `instanceMapper.upsert(ticket)`（`MERGE INTO DAILY_TICKET_INSTANCE ... ON (T.ORDER_NO = S.ORDER_NO)`，`resources/mapper/DailyTicketInstanceMapper.xml`）。**支付回调 `receivePayResult` 从不建票实例**，它只回写订单表（`DAILY_TICKET_ORDER` / `TRAVEL_TICKET_ORDER`）的支付事实。`DAILY_TICKET_INSTANCE.ORDER_NO` 与订单一一对应（唯一索引 `UK_DAILY_TICKET_INSTANCE_ORDER`）。**NEVER 在支付链路上「顺手」插一张实例** —— 那会绕过 `canActivate` 的支付校验，让未付单也能激活。
- 佐证一：支付回调入参 `DailyTicketPayCallbackReqDTO` **只有 10 个字段**（`orderNo` / `tradeNo` / `paymentOrderNo` / `payResult` / `payAmount` / `payDate` / `payChannel` / `cashAmount` / `couponAmount` / `rawBody`）—— **没有 `cardNum`、没有 `ticketCode`、没有 `countingEnd`**，物理上组不出 `DAILY_TICKET_INSTANCE` 的必填列。**NEVER 往回调 DTO 里加卡号 / 票号「以便建实例」。**
- 佐证二：激活入参 `DailyTicketActivateReqDTO`（17 个字段）**没有 `countingEnd`** —— 有效期在激活请求里根本不存在（见下）。

### 「过期」不是状态机，是「查询时动态比较」+ 事后收敛
- **本模块没有「过期」状态机**：`DailyTicketInstanceLifecycleService.validateEntryCheck` 与 `checkRideAvailability` 拿 `System.currentTimeMillis()` 与 `COUNTING_END` **现算现比**（`日票已过期` 文案在这两处，**改一处 MUST 同批改另一处**，与拉码校验逐字一致）。
- **`EXPIRED` 原先只有一个写入点**：出站扣次 `markUsed` 里 `remainTimes == 0`（计次票扣完最后一次）。**2026-09-22 新增第二个写入点**：`DailyTicketExpireService.convergeExpired`（定时收敛，见下节）。两者共用同一状态值，**属有意**（业主未要求区分，下游处置一致：不可过闸、不可退款）；要区分就得新增状态值并同批改 `selectForEntryCheck` 白名单与退款侧判断。
- **NEVER 因为「状态还是 `ACTIVATED`」就认为票没过期**；也 **NEVER 因为新增了收敛任务就删掉那两处动态比较** —— 收敛按小时跑、有延迟，动态比较是过闸拦截的最后一道闸（`DailyTicketExpireService` 类注释原文：「只做状态收敛，**NEVER 顺手去掉那两处动态判断**」）。
- **`COUNTING_END` 在激活时不写**：`updateTicket` 只写 `PERIOD`（`setPeriod(request.getPeriod())`）与 `COUNTING_START`，**没有 `setCountingEnd`**；`COUNTING_END` 只由 APP 的 IF8A-33 `updateAndNotice`（首次使用）写入，闸机出站 `markUsed` 传 null 不覆盖。因此「激活但从未乘车」的票 `COUNTING_END` **恒为空**。
- **`PERIOD`（有效期天数）此前落库后零读取**：写入方只有 `updateTicket`（`request.getPeriod()`），全模块无任何比较点；「过期」判定一律走 `COUNTING_END`。**2026-09-22 起收敛任务的 `fallbackByPeriod` 回退支成为它的第一个读取处**（见下节），这是唯一例外。

## 定时任务（Quartz 入口在 web-admin，**本模块零 `@Scheduled`**）

本模块**没有任何 `@Scheduled`（全模块 grep 为 0，刻意如此）**；所有定时补偿都由 `web-server/web-admin` 的 Quartz 任务经 rpc 打本模块的 `/internal/**` 端点。任务类在 `web-server/web-admin/src/main/java/com/chinasofti/huateng/quartz/task/`。**JobStore 是内存态**（改 `sys_job` 表后 MUST 重启 web-admin、或在界面上改存一次才生效，只改库不生效）。**排查「定时任务有没有跑」MUST 查 `SYS_JOB` / `SYS_JOB_LOG` + 本模块日志，NEVER 在本模块里找 `@Scheduled`。**

| job_id | 任务名 | 触发目标（quartz/task） | cron | 初始 status | 打到本模块的端点 |
|---|---|---|---|---|---|
| 225 | 给ACC上传扣费交易 | `reconQuartzTask.runDailyBatch()` | `0 0 2 * * ?` | 0 启用 | 对账抽取 `/internal/recon/...` |
| 245 | 多日票批量退款(当日) | `dailyTicketBatchRefundQuartzTask.refundDaily()` | `0 0 20 * * ?` | 0 启用 | `POST /internal/daily-ticket/batch-refund/daily` |
| 250 | 多日票批量退款(月度) | `dailyTicketBatchRefundQuartzTask.refundMonthly()` | `0 0 20 L * ?` | 0 启用 | `POST /internal/daily-ticket/batch-refund/monthly` |
| 350 | 日票过期状态收敛 | `dailyTicketQuartzTask.convergeExpiredTickets()` | `0 5 * * * ?` | **1 暂停** | `POST /internal/daily-ticket/expire/converge` |

- **`sys_job` 350「日票过期状态收敛」**（2026-09-22 新增；脚本 `web-server/web-quartz/src/main/resources/sql/web-quartz-daily-ticket-expire-job-migration.sql`，**注意在 `web-quartz` 模块、不是 `web-admin`**）：每小时第 5 分扫一轮 `DAILY_TICKET_INSTANCE`，把「有效期已过、状态还停在 `ACTIVATED` / `USED`」的票**逐条 CAS** 推进成 `EXPIRED`。
  - **初始 `status=1`（暂停）**，脚本 remark 原文：「初始 status=1 暂停：收敛成 EXPIRED 后该票不能再退款，属行为变更，须经业主确认后置 0。」**NEVER 未确认就置 0 上线。**
  - 候选两支（`DailyTicketInstanceMapper.selectExpiredCandidates`，白名单 `TICKET_STATUS in ('ACTIVATED','USED')`）：① `COUNTING_END` 非空 → `COUNTING_END < nowMillis`；② `COUNTING_END` 为空 → 回退 `ACTIVATE_TIME + NUMTODSINTERVAL(PERIOD,'DAY') < now`（`fallbackByPeriod` 默认 true，配键 `daily-ticket.expire.fallback-by-period:true`；关掉后「激活但从未乘车」的票永不被收敛）。`REFUND_LOCKED` / `REFUNDED` 天然被白名单排除。
  - 逐条 CAS 走 `updateStatusIfCurrent(id, expectStatus, EXPIRED, now)`，`expectStatus` 取自扫描时状态；**CAS 影响 0 行计入 `skipped`（多为并发被退款锁票，属正常竞态、不是失败）**，单张异常计入 `failed` 不中断整批。CAS 语句只改 `TICKET_STATUS` 与 `UPDATE_TIME`，**NEVER 在这里补 `COUNTING_END` / `FIRST_USE_TIME`**。
  - `batchLimit` 默认 500（`daily-ticket.expire.batch-limit:500`），单轮上限，防一次把全库历史票拉进内存。候选按 `UPDATE_TIME` 升序、`fetch first N rows only`。
  - 端点 `ExpireInternalController` 带 `AtomicBoolean` 限流，busy 返 **`9998`（限流不是失败）**，web-admin 侧 `convergeExpiredTickets` 只记 WARN **不抛**，**不能复用 `assertSuccess`**（否则前台调度日志每轮记红）。
  - 收敛后按子单状态重算旅游票主单汇总（`TravelParentSummaryWriter.refreshBySubOrder`），失败只记日志不回滚。
  - **⚠️「待裁决」——「过期票仍可退」旧口子关闭属行为变更**：`DailyTicketRefundInitiationService` 对非 `ACTIVATED` 一律返「车票已使用，不允许退款」，因此被收敛成 `EXPIRED` 的票**从此不能再发起退款**。这关闭了一个此前事实上存在的口子（`EXPIRED` 票被当「未激活」走 `refundType='00'` 全额退，见 §一 · 退款前置条件那段）。**该变更属行为变更、MUST 经业主确认后才允许启用任务；确认前收敛任务保持 `status=1`，文档口径 MUST 写成「待裁决」而不是既定规则。**
  - **⚠️ CAS 推进那一支（按日期过期）尚未被真实数据验证**：会话期内库中候选恒为 0 行（没有 `COUNTING_END` 已过、或 `ACTIVATE_TIME + PERIOD` 已过的 `ACTIVATED`/`USED` 行），**「能收敛」只在代码层验证过、未在真实数据上跑通**。**启用前 MUST 先造数演练**（造一张 `COUNTING_END` 已过的 `USED` 票，跑一次 `/converge`，确认 `expired=1`）。
- **无鉴权**：`/internal/daily-ticket/expire/converge` 与 `/internal/daily-ticket/batch-refund/{daily,monthly}` 都是**裸暴露**（沿用本模块 internal 端点现状），与 AGENTS.md §5.2 冲突，上线前 MUST 随那批端点统一补齐。

## 拉码前置可用性校验（2026-09-17 新增，daily-ticket-server 1.0.29 + fep-app 2.0.87，ADR-D126）

`POST /ci/daily-ticket/ticket/rideAvailability` → `DailyTicketInstanceLifecycleService.checkRideAvailability`（门面 `DailyTicketServiceImpl.checkRideAvailability` 只是一行委派）。
起因：IF8A-03 拉码链路原先**一行都不碰日票**，次票用完的用户照样能拉到可用乘车码，只在闸机侧被拦。这条把拒绝提前到拉码时。**以下五条 NEVER 改**：

1. **这是拉码时的提前反馈，不是护栏。** 闸机侧 `GateDailyTicketCoordinator.checkEntryAllowed` 那道权威校验**保持原样**，**NEVER 因为有了这条就撤掉** —— 拉码到进站之间可能隔很久，APP 还可能缓存旧码，只有进站那一刻的校验才是权威的。
2. **`fep-app-server` 只在日票族卡种才调它。** 判定是 `CardTypeMapping.toIssueCardType(userInfo.getCardType())` 归一（APP 上送 `12~15` → `0445~0448`）后过 `CardTypeCodeEnum.isDailyTicket`；后付费（`0441`）链路**一行未动**。这样收窄是为了把热路径上多出来的这一跳 RPC 代价**限制在日票用户**，**NEVER 扩到非日票卡种**。
3. **daily-ticket 不可达时降级放行。** `RpcOutcome.Unreachable` 分支只打 WARN、继续往下走、不拒发。理由两条：闸机侧还有一道权威校验；反过来「宁拒不放」会让 daily-ticket 一抖就**误拦全部日票用户**。**NEVER 改成拒发。**
4. **NEVER 改成复用 `queryDailyTicketInfo`。** 那个接口查不到实例时返 `0000` + 字段全 null（本文件已记的静默分支，是为了不打挂 IF8A-34 主链路而有意为之），于是「没有日票」与「服务抖动」在应答上**分不开** —— 接进拉码链路只能二选一地误拦或形同虚设。这正是新开一个端点、并用 `retCode` 明确表达「不可用」的原因。
5. **拒发时 `fep-app-server` 对 APP 返 `8004` + daily-ticket 的原始 `retMsg`。** 注意 **`8004` 在这条链路上已经是第三个语义**（account 的「没有账号卡片数据」、ticket 的「未注册用户」，现在再加上日票不可用），**排查时只能靠 `retMsg` 区分，NEVER 只看 retCode 下结论**。

拒绝文案与 `validateEntryCheck` **逐字一致**（`日票尚未激活` / `日票已过期` / `日票次数已用完` / `车票已申请退款，不允许使用` / `无可用日票`），因为它会经 fep-app 原样透传给 APP；改一处 **MUST** 同批看齐另一处。`ACTUAL_TIMES` 的判据是 `== 0`，**NEVER 写成 `<= 0`** —— 负数（如 `-99`）是「不限次」哨兵。

## 跨模块联动（改动必查）
- **出站扣次**：ticket-server `GateTicketHandler` 在日票出站时调 `/ticket/markUsed`，改签名或返回结构 **MUST** 同步 ticket-server
- **闸机扣费跳过**：gate-txn-pay-server 对 `CardTypeCodeEnum.isDailyTicket` 直接置 SUCCESS 不扣款，日票卡类型判定改动 **MUST** 同步核对该分支
- **APP 转发**：fep-app-server `AppDailyTicketController`。**APP 实际调的是《ITP与APP接口规范R6》给定的扁平路径，不是 `/app/dailyTicket/**`**，四条扁平别名 **NEVER** 删：
  - if8a_60 `/app/requestCountingOrder`
  - if8a_61 `/app/payment/requestPay`
  - if8a_62 `/app/payment/requestPayResult`
  - 支付回调 `/app/payment/receivePayResult`（由 daily-ticket-server 的 `daily-ticket.pay.notify-url` 上报给网关，改一边 **MUST** 同步另一边）
  > 2026-09-09 事故：`/app/payment/requestPay` 曾被 `PaySignController` 占为 IF8A-19 通用请求支付，日票支付被透传到 pay-sign，因缺 `amount` 被 `validateRequestPay` 拦为 `retCode=8001 amount不能为空`，APP 显示「创建支付渠道订单失败」；`requestPayResult` 与 `receivePayResult` 两条当时**根本未注册**。已归位到本 Controller，`PaySignController` 只保留 `/ci/app/requestPay`。

## 幂等
依赖订单状态终态短路 + 表主键。无 Redis 锁、无 MQ。新增写路径 **MUST** 补状态判断。

## 一卡多实例：`DAILY_TICKET_INSTANCE` 里 `CARD_NUM` **不唯一**（2026-09-17 修复，1.0.27，ADR-D121）
同一张卡可以有多行实例（重复激活 + 历史已过期）。实测样例：卡 `0426090947000056` 有 3 行
（09-16 15:20 与 15:22 各一张 `ACTIVATED` 计次票 + 09-11 一张 `EXPIRED`）。因此：
- `selectByCardNum` 是 `selectOne` 语义，**MUST 保留 `order by CREATE_TIME desc` + `fetch first 1 rows only`**
  （与 `selectForEntryCheck` / `selectByTicketCode` 同口径）。**NEVER 去掉那两行** —— 多行结果会抛
  `TooManyResultsException`，`markUsed` 与 `queryDailyTicketInfo` 一起返 500，而 ticket-server 侧按设计吞异常
  放行出站（日志「已放行出站，次数未扣减需人工核对」）、闸机仍返 `0000`，**乘客白坐一次且无自愈路径**。
- `markUsed` 与 `decreaseActualTimes` 的 WHERE **MUST 是 `ID = #{id}`，NEVER 回退成 `CARD_NUM`**。
  按卡号更新时，一卡两张可用计次票会**各减 1 次（资损）**，且另一张的状态 / `COUNTING_END` / `FIRST_USE_TIME`
  被一起污染。`decreaseActualTimes` 的签名就是 `(id, updateTime)`，调用点传 `selectByCardNum` 取到的那张实例的 id。
- **判据（通用）**：`selectOne` + 「业务上并非唯一」的列做 WHERE 是一对孪生缺陷 —— 读侧报
  `TooManyResultsException`、写侧**静默多行更新**；读侧那个报错反而挡着写侧的资损，**只修读侧比不修更危险**。
- 「为什么允许一张卡有两张同时可用的计次票」（重复激活未拦）**属业务口径未定，激活侧没有唯一约束**，
  当前只保证「一次出站只扣一张」。

## 参考原始文档
- `docs/业务需求文档/青岛地铁虚拟电子多日计次票、离线码、实时客流上传功能方案V1.docx`
- `docs/接口规范文档/青岛地铁日票-ITP与ACC交互文档.docx`（ACC 通知部分）

## 附：daily-ticket-server 源码注释知识抽取（2026-09-16，阶段一）

本节是 `daily-ticket-server` 模块内 `src/main/java/**/*.java`（32 个文件）与
`src/main/resources/mapper/*.xml`（7 个文件）**注释原文**的归档，只做归类与转录，不改代码、不改本文件上文。

> **行号会漂**。抽取当天 `DailyTicketServiceImpl.java` 在同一次会话内从 1440 行变成 1462 行，
> 同一条注释的行号出现过 1332 / 1336 两个值。因此**引用本节任何 `文件:行号` 前 MUST 先 grep 定位关键字**
> （例如 `grep -n 'NEVER 置' <file>`），NEVER 直接按本节行号跳转或据行号判断注释是否还在。
> **更彻底的一次漂移已经发生**：1.0.51~1.0.56 把 `DailyTicketServiceImpl` 从 2455 行拆成 200 行的纯委派门面，
> 本节原先所有 `DailyTicketServiceImpl.java:8xx~14xx` 形式的锚点**对应的行已不存在**，
> 现已按「类名 + 方法名」重新指向拆出来的服务类（归属见正文「核心类」）。**NEVER 再去门面里找这些注释。**
> 路径均相对 `daily-ticket-server/src/main/`。
> 注释原文中的 MUST / NEVER 逐条保留、未合并；本节不含 ADR 编号 —— 抽取范围内的注释**一处都没有引用 `ADR-D*`**，
> 它们只写日期（`2026-09-10` / `2026-09-11` / `2026-09-15`）与规格章节号（网关 §3.1 / §3.3、甲方文件 §一）。

### 一、日票与计次票（状态机与进站）

**契约与判据**

- `daily-ticket-server` / `DailyTicketInstanceStatus`（常量类与类注释，`java/com/chinasofti/huateng/dailyticket/service/support/DailyTicketInstanceStatus.java`）
  —— `DAILY_TICKET_INSTANCE.TICKET_STATUS` 六态：`INIT` 已下单未激活 / `ACTIVATED` 已激活未开始使用（可进站）/
  `USED` 已开始使用（可继续进站，一日票有效期内不限次、计次票凭剩余次数）/ `EXPIRED` 已过期或次数用尽（终态）/
  `REFUND_LOCKED` 退票锁定中（终态，锁定期不可过闸）/ `REFUNDED` 已退票（终态）。
  原文：「**`USED` 不是终态**——它表示「已开始使用」，不是「已用完」」。
- `daily-ticket-server` / `DailyTicketInstanceMapper.selectForEntryCheck`（`resources/mapper/DailyTicketInstanceMapper.xml:149~163`）
  —— 进站校验**状态白名单**是 `ACTIVATED`（已激活未使用）+ `USED`（已开始使用），排除
  `INIT` / `EXPIRED` / `REFUND_LOCKED` / `REFUNDED`；收口条件是 `COUNTING_END` 与 `ACTUAL_TIMES`。
  取最新一行（`order by CREATE_TIME desc` + `fetch first 1 rows only`）。
- `daily-ticket-server` / `DailyTicketInstanceLifecycleService.validateEntryCheck`（`.../service/lifecycle/DailyTicketInstanceLifecycleService.java`）
  —— 「计次票次数检查（仅校验，不扣减；扣减在出站时执行）」；
  「`ACTUAL_TIMES` 负数是「不限次」哨兵值（APP 上送 -99，见 `DailyTicketActivateReqDTO#actualTimes`），
  一日票 / 多日票走有效期而非次数，**NEVER 用 `<= 0` 判断用完**——那会把不限次票判成已用完」。
  实际判据是 `actualTimes != null && actualTimes == 0`。
- `daily-ticket-server` / `DailyTicketInstanceMapper.decreaseActualTimes`（`resources/mapper/DailyTicketInstanceMapper.xml:165~172`）
  —— 「计次票扣减可用次数（atomic update，防止并发超扣）」，`where ... and ACTUAL_TIMES > 0`。

**决策理由**

- 同上 `DailyTicketInstanceStatus` 类注释 —— 「收口条件应是有效期（`COUNTING_END`）与次数，不是 `USED` 这个状态本身」。

**陷阱**

- `daily-ticket-server` / `DailyTicketInstanceStatus`（`.../service/support/DailyTicketInstanceStatus.java` 类注释）与
  `DailyTicketInstanceMapper.selectForEntryCheck`（`resources/mapper/DailyTicketInstanceMapper.xml:153~154`）
  —— 「2026-09-10 线上事故：进站后 APP 的 `updateAndNotice` 把状态推到 `USED`，
  而 `selectForEntryCheck` 用 `TICKET_STATUS != 'USED'` 过滤，导致一日票刷一次就再也进不了站」。
  XML 侧原文：「**NEVER 写成 `TICKET_STATUS != 'USED'`**」。
- `daily-ticket-server` / `DailyTicketInstanceLifecycleService.validateEntryCheck`（同上文件，`ACTUAL_TIMES` 判据处）
  —— 「（2026-09-10 线上：-99 被判「计次票次数已用完」，日票进不了站）」。

### 二、旅游票（IF8A-70 聚合单）

**契约与判据**

- `daily-ticket-server` / `DailyTicketOrderCreationService.requestTravelOrder`（`.../service/order/DailyTicketOrderCreationService.java`）
  —— 「旅游票是聚合单：主单落 `TRAVEL_TICKET_ORDER`，内含的每张日票落一条 `DAILY_TICKET_ORDER` 子单
  （`ORDER_TYPE='1'`、`PARENT_ORDER_NO` 指向主单）」。
- 同方法 —— 「**金额一律服务端重算**：`totalAmount` 只用于与 `ticketPrice * ticketCount` 比对，
  比对不过直接拒单，**NEVER** 直接采信 APP 上送值落库」；校验实现在
  `DailyTicketOrderCreationService.validateTravelOrderRequest`（`.../service/order/DailyTicketOrderCreationService.java`，
  不一致时返「totalAmount与ticketPrice*ticketCount不一致」）。
- `daily-ticket-server` / `DailyTicketOrderCreationService.MAX_TRAVEL_TICKET_COUNT`（`.../service/order/DailyTicketOrderCreationService.java`）
  —— 「旅游票单次购买张数上限。旅游票下单按张数循环 INSERT，不设上限等于把 for 循环次数交给外部输入。
  上限值待业务确认，暂按 20 张」。
- `daily-ticket-server` / `DailyTicketOrderCreationService.nextTravelOrderNo`（`.../service/order/DailyTicketOrderCreationService.java`）
  —— 「旅游票主单号，前缀 `0T` 与日票子单的 `0E` 区分，便于日志与运营侧一眼分辨聚合单」。
- ⚠️ **本处此前摘录的两条（`ReconExportMapper.selectTravelTicketPaySummary` 处的「旅游票**没有聚合支付**」「APP 只能拿子单号逐张付，拿主单号 `0T...` 调 `requestPay` 命中 0 行、返回订单不存在」「`validateOrderNo` 只校验 `orderType='1'`」）已过期**（那是 2026-09-16 的中间态，源码注释已重写）。**正确口径见 §数据表「旅游票（IF8A-70）落库形态」：主单是聚合支付承载，`orderType="2"` 走 `requestTravelPay`、送主单号；子单恒 `CREATED`/`INIT`。NEVER 回退。**
- `daily-ticket-server` / `ReconExportMapper.selectTravelTicketPaySummary`（`java/com/chinasofti/huateng/dailyticket/mapper/ReconExportMapper.java`，现 javadoc 起于 `:30`）
  —— 「口径取**主单 `TRAVEL_TICKET_ORDER`**：旅游票是「主单聚合支付一次」，支付终态只回写主单，子单（`DAILY_TICKET_ORDER` 里 `PARENT_ORDER_NO` 非空那些）恒为 `CREATED`/`INIT`、`PAY_DATE` 恒空。**NEVER 改回按子单统计** —— 2026-09-22 实测：改回去这条查询恒返 0 行，旅游票在 `ITP.PAY` 里就一直没有数据（该缺陷自上线起存在，当日修复）。张数取 `SUM(TICKET_COUNT)`（一张主单含 N 张票，**NEVER 用 `COUNT(*)`** —— 那数的是订单数）」。

**决策理由**

- `daily-ticket-server` / `DailyTicketOrderCreationService.requestTravelOrder`（落库顺序那段注释，`.../service/order/DailyTicketOrderCreationService.java`）
  —— 「拆子单不是设计取舍——`UK_DAILY_TICKET_INSTANCE_ORDER` 限定一个订单号只能挂一张票实例，
  一单挂多票在现有表上无法表达」。
- 同方法 —— 「**落库顺序是先子单、后主单**：中途失败时只留下父单不存在的孤儿子单，
  APP 拿不到 `orderNo` 也就无法发起支付，不会出现「能付款但票数不足」的单。反序则会留下可支付但子单缺张的主单。
  本模块没有任何 `@Transactional`（全模块 grep 为 0），因此不靠事务回滚保证一致性，靠顺序与状态可判定性」。
- ⚠️ **本处此前摘录的整段（`ReconExportMapper` `:84~94` 与 `ReconExportMapper.xml:90~96` 处的「主单是聚合壳」「全仓库对 `travelTicketOrderMapper` 只有一次 `insert`、**没有任何 UPDATE**，主单状态永不回写」「支付实际是**每张子单各走一次**：`requestPay` 查 `DAILY_TICKET_ORDER`、商户单号是子单号、`PAY_STATUS='PAID'` 落在子单上」，以及 `:118~121` 处「主单 `PAY_STATUS` 恒为 `INIT`」）已过期**。**正确口径**：主单承载聚合支付 —— `orderType="2"` → `DailyTicketPaymentService.requestTravelPay` 送**主单号**、金额取主单 `TOTAL_AMOUNT`，支付终态只回写主单；`TravelTicketOrderMapper` 现有 `updatePayRequest` / `updatePayResultIfPaying` / `updatePaySuccessByExternalSync` / `updatePaymentOrderNo` / `cancelIfPending` / `cancelIfPaid` / `updatePayResultIfCanceled` 七个写方法。**NEVER 回退，也 NEVER 再据「主单 `PAY_STATUS` 恒为 `INIT`」判 P0。**
- 仍成立的部分（**不是缺陷，是当前设计**）：子单恒 `CREATED`/`INIT`、`PAY_DATE` 恒空 —— 钱是按主单那一笔收的。2026-09-23 库实测一致（`PARENT_ORDER_NO` 非空的 6 行全 `CREATED/INIT`、`PAY_DATE` 全 null）。
- `daily-ticket-server` / `TravelTicketOrder`（`java/com/chinasofti/huateng/dailyticket/model/TravelTicketOrder.java:5~10`）
  —— 「主单只承载聚合信息与支付状态，内含的每张日票落在 `DAILY_TICKET_ORDER`，
  通过 `PARENT_ORDER_NO` 回指本主单」；`ORDER_STATUS` / `PAY_STATUS` 取值与日票一致（`:58`、`:63`）。

### 三、激活与出站扣次

**契约与判据**

- `daily-ticket-server` / `DailyTicketInstanceLifecycleService.updateTicket`（`.../service/lifecycle/DailyTicketInstanceLifecycleService.java`）
  —— 激活（IF8A-32）前置：订单必须 `PAID`（否则「订单未支付，不能激活」）；
  已存在实例且状态不是 `ACTIVATED` 时**短路返成功**（重复激活幂等）；
  已存在实例且卡号与首次不一致时返失败「卡号与已激活车票不一致」。
  落库走 `instanceMapper.upsert`（`MERGE INTO ... ON (T.ORDER_NO = S.ORDER_NO)`）。
- 同方法 —— 「**`CARD_NUM` 直接取 APP 上送的 `cardNum`，NEVER 在此处向 card-pool-server 再预占卡号。**
  该卡号是开户（`businessType=ACCOUNT_OPEN`）时预占并下发给 APP 的那张，APP 取码与闸机上送用的都是它」。
- `daily-ticket-server` / `DailyTicketInstanceLifecycleService.updateAndNotice`（`.../service/lifecycle/DailyTicketInstanceLifecycleService.java`）
  —— 「APP 在首次进站后上送 `countingEnd`（一日票 = 首次使用 + 24h），语义是**有效期截止**，不是「票已用完」。
  因此这里只把状态推到 `USED`（已开始使用），**NEVER 置 `EXPIRED` 或任何终态**，票在有效期内仍要能继续进出站。
  `FIRST_USE_TIME` 只在首次写入，重复通知不覆盖」。
- `daily-ticket-server` / `DailyTicketInstanceLifecycleService.markUsed`（`.../service/lifecycle/DailyTicketInstanceLifecycleService.java`）
  —— 出站状态推进三分支：不限次票（`ACTUAL_TIMES < 0`，如一日票）保持 `USED`、靠 `COUNTING_END` 过期收口；
  计次票扣完最后一次（扣后为 0）推进到 `EXPIRED` 终态；计次票仍有剩余次数保持 `USED`、下次仍可进站。
- 同方法 —— 「**`countingEnd` 为 null 时 NEVER 覆盖库里已有的有效期**——闸机出站不带有效期
  （`GateTicketHandler:188` 传的就是 null），有效期由 APP 的 `updateAndNotice` 写入。`FIRST_USE_TIME` 同理只在首次写入」。
- `daily-ticket-server` / `DailyTicketInstanceLifecycleService.insertUsageLog`（`.../service/lifecycle/DailyTicketInstanceLifecycleService.java`）
  与 `DailyTicketUsageLog`（`java/com/chinasofti/huateng/dailyticket/model/DailyTicketUsageLog.java:5~8`）
  —— 「INSERT 扣次明细（`UK_DTUL_ORDER` 做幂等，重推不多扣）」；「每次出站扣次 INSERT 一行，
  `ORDER_NO` 唯一索引做幂等」。
- `daily-ticket-server` / `DailyTicketController.markUsed`（`java/com/chinasofti/huateng/dailyticket/controller/DailyTicketController.java:154~160`）
  —— 「扩展参数 `orderNo` / `inStation` / `outStation` 用于记录扣次明细，老调用方（不传这三个字段）仍兼容」。
  `orderNo` 关联 `GATE_TXN_PAY.ORDER_NO`（`DailyTicketService.markUsed` 的 javadoc）。
- `daily-ticket-server` / `DailyTicketInstance`（`java/com/chinasofti/huateng/dailyticket/model/DailyTicketInstance.java:95`、`:100`）
  —— `COUNTING_START` / `COUNTING_END` 是**毫秒时间戳**（`jdbcType=BIGINT`），不是日期列；
  `DailyTicketService.markUsed` 的 `countingEnd` 参数同样是毫秒时间戳（两个重载的 `@param` 都写明）。

**决策理由**

- `daily-ticket-server` / `DailyTicketServer`（`java/com/chinasofti/huateng/DailyTicketServer.java:13~19`）
  —— 「**本服务不依赖 card-pool-server，NEVER 加 `@EnableRpcCardPool`**：……激活时再向卡池预占一张，
  会因 `UK_LOGIC_CARD_POOL_BUSINESS` 唯一约束必然拿到另一个卡号，与 APP 侧持有并上送闸机的开户卡号不一致，
  导致 `selectForEntryCheck` / `markUsed` 按 `CARD_NUM` 精确匹配恒命中 0 行（2026-09-10 线上事故）。
  若后续 ACC 要求日票持独立卡号，MUST 先设计「开户卡号 ↔ 日票卡号」映射表并同步改 APP 取码链路」。
- `daily-ticket-server` / `DailyTicketInstanceLifecycleService.insertUsageLog`（同上文件）
  —— 「明细落库失败不中断出站流程，只记 ERROR 留证据」。

**陷阱**

- `daily-ticket-server` / `DailyTicketInstanceLifecycleService.markUsed` / `insertUsageLog` / `isIntegrityViolation`
  （`.../service/lifecycle/DailyTicketInstanceLifecycleService.java`）
  —— 「daily-ticket-server 已开 tracing，`DuplicateKeyException` 可能被切面包一层 `RuntimeException`，
  因此用 cause 链判定而非直接 catch `DuplicateKeyException`」；工具方法注释为
  「沿 cause 链判定是否为唯一索引冲突（兼容 tracing 切面包装）」。
- `daily-ticket-server` / `DailyTicketInstanceLifecycleService.updateTicket`（同上文件）
  —— 「2026-09-10 线上进站被拒即此原因」（激活处另占卡号 ⇒ 后续按 `CARD_NUM` 精确匹配恒 0 行）。

### 四、支付与退款

**契约与判据**

- `daily-ticket-server` / `DailyTicketPaymentService.requestPay`（`.../service/payment/DailyTicketPaymentService.java`）
  —— 支付前置状态白名单只有 `CREATED` 与 `PAYING`（其余返「订单状态不允许支付」）；
  已 `PAYING` 时先 `queryAndRefreshPayResult` 再按最新状态答（`PAID` 返成功、`PAY_FAILED`+`FAIL` 返「支付已失败，请重新下单」、
  其余返「支付处理中，请查询支付结果」）。
- `daily-ticket-server` / `DailyTicketOrderMapper.updatePayRequest`（`resources/mapper/DailyTicketOrderMapper.xml:109~121`）
  —— 置 `PAYING` 的 CAS 条件是 `(ORDER_STATUS='CREATED' and PAY_STATUS='INIT') or (ORDER_STATUS='PAYING' and PAY_STATUS='PAYING')`，
  影响 0 行时服务层返「订单状态已变更，请查询支付结果」。
- `daily-ticket-server` / `DailyTicketOrderMapper.updatePayResultIfPaying`（`resources/mapper/DailyTicketOrderMapper.xml:123~135`）
  —— 支付结果回写只在 `ORDER_STATUS='PAYING' and PAY_STATUS='PAYING'` 时生效。
- `daily-ticket-server` / `DailyTicketOrderMapper.updatePaymentOrderNo`（`resources/mapper/DailyTicketOrderMapper.xml:137~143`）
  —— `where ... and (PAYMENT_ORDER_NO is null or PAYMENT_ORDER_NO = #{paymentOrderNo})`，
  对应 `DailyTicketPaymentService.updatePayTerminalIfPaying`（`.../service/payment/DailyTicketPaymentService.java`）的注释
  「A duplicate success callback may carry the platform order number that was missing from the first terminal update. Keep it for refunds.」
- `daily-ticket-server` / `DailyTicketRefundInitiationService.requestRefundTicket`（`.../service/refund/DailyTicketRefundInitiationService.java`）
  —— 「同一日票订单只生成一笔退款单；重复请求直接返回已有处理结果」；
  订单必须 `PAID`+`PAID`、`PAYMENT_ORDER_NO` 必须有值；票实例 `USED` 直接拒退（「车票已使用，不允许退款」）；
  「未激活票可直退；已激活但尚未使用的票进入后续人工/定时核验流程」——
  `refundType` = 实例为空或非 `ACTIVATED` 时 `00`，否则 `01`（核验退款，订单置 `REFUNDING`，
  提示「已激活车票进入5天核验退款流程」）。
- `daily-ticket-server` / `RefundProgressService.queryRefundTicket`（`.../service/refund/RefundProgressService.java`）
  —— 「两个定位字段必须同时传入。历史数据先从原退款网关响应补齐平台退款单号」，
  查询报文同时送 `refundOrderNo`（平台退款单号）与 `merchantRefundNo`（我方 `REFUND_ORDER_NO`）；
  恢复不到时返「支付平台退款单号缺失，无法执行双字段退款查询」。
- `daily-ticket-server` / `RefundProgressService.resubmitRefundTicket`（`.../service/refund/RefundProgressService.java`）
  —— 「白名单：只有这三种前置状态才可能出现「支付平台从未受理」，其余状态一律按已有结果返回」
  （`REFUNDING` / `FAILED` / `WAIT_VERIFY`）；「核验退款只在观察期满后才允许转支付平台，观察期内仍按原设计等核销」
  （`VERIFY_AFTER_TIME` 未到即拒）；「本方法的唯一判据：对端从未受理。先按历史网关响应尝试恢复，
  恢复到了说明对端建过退款单，应走重试而不是重提交」；「沿用原退款单号重发，支付平台按 `refundOrderNo`
  保证外部幂等，不会重复退款」。
- `daily-ticket-server` / `DailyTicketService.resubmitRefundTicket`（接口 javadoc，`java/com/chinasofti/huateng/dailyticket/service/DailyTicketService.java`）
  —— 「与 `retryRefundTicket` 的分工是**互斥的，不要混用**：`retryRefundTicket` 先查再重发，
  前提是支付平台已建过退款单、查得到结论；而对端从未受理时 `refundQuery` 永远查不到东西，
  那条链路会一直卡在「支付平台退款单号缺失，无法执行双字段退款查询」，退款能力永久丧失。
  本方法就是补这个分支：**对端没受理过 ⇒ 重发是安全的，不会重复退款**」；
  且「它也是 `REFUND_TYPE='01'`（核验退款）唯一的出口 —— `WAIT_VERIFY` 在本模块内**只被写入、从无任何代码读取**，
  核销观察期满后没有任何驱动方」。
- `daily-ticket-server` / `DailyTicketRefundMessages.isGatewaySuccess`（`.../service/refund/DailyTicketRefundMessages.java`；`DailyTicketRefundInitiationService` 与 `DailyTicketPaymentService` 各有一个同名私有转调）
  —— 网关成功判定：`success=true` 或 `code=0` 或 `code=200`；
  `isGatewayExplicitFailure`（`:1310~1318`）才是「明确失败」，只有它成立时才 `markPayFailed`。
- `daily-ticket-server` / `RefundProgressService.isRefundSuccessStatus` / `isRefundFailedStatus`
  （`.../service/refund/RefundProgressService.java`）—— 「支付平台退款查询的成功状态兼容不同渠道返回值」；
  「仅将平台明确的失败终态回写为 FAILED；未知状态继续保持处理中」。
- `daily-ticket-server` / `DailyTicketRefundInitiationService.buildDailyTicketRefundRequest`（`.../service/refund/DailyTicketRefundInitiationService.java`，实际组报文在 `RefundGatewayRequests`）
  与 `DailyTicketPayProperties.refundNotifyUrl`（`java/com/chinasofti/huateng/dailyticket/config/DailyTicketPayProperties.java:54~60`）
  —— 「支付中心网关 §3.1 的 `notifyUrl` 是必填项。缺它对端不回调，退款单只能靠人工点「退款结果查询」收口，
  且日志一片绿、没有任何报错。用 `putIfText` 而不是 `put`：配置为空时不送空串，让网关按缺参报错、
  别把空地址当成有效回调地址」；配置侧原文「缺它对端不会回调、退款单只能靠人工点查询收口，
  因此 `buildDailyTicketRefundRequest` MUST 带上本值」。
- `daily-ticket-server` / `DailyTicketRefundNotifyService.buildBizData`（`java/com/chinasofti/huateng/dailyticket/service/DailyTicketRefundNotifyService.java:148~157`）
  —— 「六个字段逐字对齐 `collect-pay-server` 的 `NoticeAppRefundDTO`，额外多一个 `orderType="1"` 标明日票。
  **NEVER 改字段名**——那是对外契约」；`refundResult` 由 `REFUND_STATUS` 反推，
  「通知只在 `markRefunded` / `markRefundFailed` 两个**终态**收口点入队，因此这里只会出现 `REFUNDED` / `FAILED` 两种，
  与支付中心回调上送的 `SUCCESS` / `FAIL` 一一对应、不丢信息；其余状态保守报 `PROCESSING`」。
  `orderType` 的 `1` 代表日票，是「用户明确指定」（`:50`）。
- `daily-ticket-server` / `DailyTicketAppNotifyClient`（`java/com/chinasofti/huateng/dailyticket/client/DailyTicketAppNotifyClient.java:30~36`）
  —— 「**报文形态是「x-www-form-urlencoded 的 ITP 信封 + bizData 装业务 JSON」，NEVER 退回裸 JSON。**
  该网关只认信封（裸 JSON 实测返 `7004`），八个信封字段一个都不能少」；
  投递成功判定：「HTTP 非 2xx 一律失败；2xx 时能解析出 `retCode` / `code` 就按它判，
  **成功码只认 `0000` / `code=0`**；解析不出来（空体 / 非 JSON）按 2xx 记为已投递但打 WARN」；
  `timestamp` 每次投递按当前时刻重取（重试时也换新值），格式 `yyyyMMddHHmmss`（`:98`）。
- `daily-ticket-server` / `DailyTicketRefundMapper.updateNotifyStatus`（`java/com/chinasofti/huateng/dailyticket/mapper/DailyTicketRefundMapper.java:24~30`）
  —— 通知状态三取值：「PENDING 待发 / SUCCESS 已受理 / GIVEUP 放弃重投」；
  `notifyTime` 置待发时传 null、`notifyMsg` 成功或置待发时传 null。
  `DailyTicketRefund.notifyStatus`（`java/com/chinasofti/huateng/dailyticket/model/DailyTicketRefund.java:31`）
  补一句「为空表示无需通知」。
- `daily-ticket-server` / `DailyTicketRefundController`（`java/com/chinasofti/huateng/dailyticket/controller/DailyTicketRefundController.java:39`、`:51`、`:62`、`:114`）
  —— 运营页面契约：「日期条件作用于订单创建时间，避免页面传入倒置区间导致全量误查」；
  「页面只允许处理日票，订单类型不信任前端传值」；「页面不透传订单类型，避免被误用于非日票订单查询」；
  「页面操作统一校验订单号并固定为日票订单，避免接口参数被篡改」；退款记录查询的日期条件作用于**退款申请创建时间**（`:103`）。

**决策理由**

- `daily-ticket-server` / `DailyTicketRefundInitiationService.requestRefundTicket`（出网异常分支，`.../service/refund/DailyTicketRefundInitiationService.java`）
  —— 「出网异常（超时 / 连接断开 / 应答解析失败）时对端可能已经受理了这笔退款，
  因此 NEVER 返成功（会让 APP 以为钱已到账）、也 NEVER 返失败（会让 APP 以为没扣、引导用户重发）。
  顺序上先 `insertPayLog` 留证据、再把订单置 `REFUNDING` 等回调或人工回查收口：
  反过来写时后面这行 UPDATE 一旦再抛，就连一条「发过退款请求」的痕迹都不剩。
  退款单本身在 `buildRefund` 里已是 `REFUNDING`，不用再动」。
- 同方法 `:410` —— 「网关仅确认受理时不能直接标为完成，保存平台退款单号后等待结果查询」。
- `daily-ticket-server` / `DailyTicketRefundSettlementService.markRefundNotifyPending`（`.../service/refund/DailyTicketRefundSettlementService.java`）
  —— 「**只落库、不出网**：出网由 `DailyTicketRefundNotifyService` 的扫表端点驱动，回调链路另有一次快速路径调用。
  这里若直接发 HTTP，退款收口的响应时长就等于 APP 网关的响应时长，上游对同一笔的重推会全部堆在同一行上」；
  「次数一并重置为 0：终态是新的一轮通知，NEVER 复用上一轮的计数（否则上一轮攒到 4 次的单子这轮只剩 1 次机会
  就被判 `GIVEUP`）」。
- `daily-ticket-server` / `DailyTicketService.receiveRefundResult`（接口 javadoc，`.../service/DailyTicketService.java`；实现在 `service/refund/DailyTicketRefundCallbackService`）
  —— 「只落库：白名单推进退款单状态、回填 `PLATFORM_REFUND_NO`、把 IF8B-04 通知置 `PENDING`；
  出网通知交给 `DailyTicketRefundNotifyService`。**NEVER 在本方法里同步发 APP 通知**——收口响应时长会等于 APP 网关响应时长」。
- `daily-ticket-server` / `DailyTicketRefundNotifyService`（`.../service/DailyTicketRefundNotifyService.java:22~31`、`:97~104`）
  —— 「形态是本项目统一的「落库状态 + 扫表补偿」」；「**本模块 NEVER 加 `@Scheduled`**（daily-ticket-server
  现在一个都没有，刻意如此）：补偿由 web-admin 的 Quartz 任务打 `POST /internal/daily-ticket/refund/notify` 触发。
  `deliverOne(String)` 只是回调收口后的**快速路径**，不能替代扫表 —— 进程崩溃时那次快速路径就丢了，
  状态仍留在 `PENDING` 等下一轮扫表」；「**每条独立 try/catch**：一条失败不能中断整批，
  否则队列头部一条坏数据能把后面全部堵死」；快速路径「**MUST 在落库之后调用**——本模块没有任何 `@Transactional`，
  每条 SQL 自动提交，因此「置 PENDING 的 UPDATE 返回后」即已提交，此处不会出现「通知已发但状态回滚」」。
- `daily-ticket-server` / `DailyTicketAppNotifyResult`（`java/com/chinasofti/huateng/dailyticket/client/DailyTicketAppNotifyResult.java:4~8`）
  —— 「只有两种状态、**没有「不确定」**：通知是幂等可重投的（APP 侧按 orderNo 去重），拿不准时按失败重试比按成功丢弃安全。
  这与支付 / 退款链路相反 —— 那边的「对端未答」绝不能当失败，因为钱可能已经动了」；
  工厂方法命名为 `ok` 而不是 `delivered` 的理由见 `:18~21`（record 已为组件生成同名访问器，静态方法重名编译不过）。
- `daily-ticket-server` / `DailyTicketAppNotifyClient`（`.../client/DailyTicketAppNotifyClient.java:25~29`）
  —— 「**NEVER 抛异常、NEVER 返回 null。**所有失败收敛成 `DailyTicketAppNotifyResult#failed`，
  由调用方决定重试还是放弃；通知链路上一个未捕获异常会让整批扫表停摆」；
  「用 JDK `HttpClient` 而不是 `rpc` 模块的 `ProxyWebClient`：APP 网关是**外部系统**，
  不走 `service.*.url` 那套内部服务地址」。
- `daily-ticket-server` / `DailyTicketPayGatewayClient`（`java/com/chinasofti/huateng/dailyticket/client/DailyTicketPayGatewayClient.java:26~28`）
  —— 「该客户端绕开 pay-sign-server 的签约卡支付封装，避免影响原有通用支付接口」；
  退款结果查询的「签名、Base64 编码和公共报文结构与支付、退款请求保持一致」（`:55~58`）。
- `daily-ticket-server` / `RefundProgressService.retryRefundTicket`（`.../service/refund/RefundProgressService.java`）
  —— 「重试前先查询，避免上一笔请求已经在支付平台成功但本地尚未更新」；
  「使用已有退款单号重发，支付平台可按 `refundOrderNo` 保证外部幂等」；
  「与首次退款一致，网关同步成功时直接落终态；其余场景保持处理中等待查询」。
- `daily-ticket-server` / `DailyTicketRefundSettlementService.markRefundFailed` / `restorePlatformRefundNoFromPayLog` /
  `persistPlatformRefundNoIfChanged`（`.../service/refund/DailyTicketRefundSettlementService.java`）
  —— 「明确失败时保留原退款单，后续重试必须继续使用该退款单号」；
  「对旧退款数据从最近一笔退款网关响应中恢复平台退款单号。恢复失败时不降级为单字段查询，防止错误关联到其他退款单」；
  「仅在响应实际带回新平台退款单号时更新，避免查询处理中覆盖退款完成时间」。
- `daily-ticket-server` / `DailyTicketRefundMessages.buildExistingRefundResult`（`.../service/refund/DailyTicketRefundMessages.java`；`DailyTicketRefundInitiationService` 有一个同名私有转调）
  —— 「处理中和待核验以退款单为最终处理依据，禁止创建新的退款单」。

**陷阱**

- `daily-ticket-server` / `DailyTicketRefundNotifyService.NOTIFY_MSG_MAX`（`.../service/DailyTicketRefundNotifyService.java:53`）
  —— 「`NOTIFY_MSG` 列长 500，超长会直接 ORA-12899，落库前 MUST 截断」。
- `daily-ticket-server` / `DailyTicketAppNotifyClient`（`.../client/DailyTicketAppNotifyClient.java:36`）
  —— 「**NEVER 把 `7004` 加进成功码**——那等于把「对端根本没受理」永久记成投递完成」。
- `daily-ticket-server` / `DailyTicketAppNotifyClient.notify`（`.../client/DailyTicketAppNotifyClient.java:68`）
  —— 「`url` 为空时直接返回失败（配置缺失也要留痕，NEVER 静默跳过）」。
- `daily-ticket-server` / `DailyTicketController.receiveRefundResult`（`.../controller/DailyTicketController.java:113~118`）
  —— 「目标地址由 `daily-ticket.pay.refund-notify-url` 上报给网关，改那个键 MUST 同步 fep-app-server 的
  `AppDailyTicketController` 别名，否则回调落到静态资源处理器、响应退化成全局异常处理器的 UUID retCode」。
- `daily-ticket-server` / `DailyTicketAppNotifyProperties`（`.../config/DailyTicketAppNotifyProperties.java:12~17`）
  —— 两条硬性约定：「**只配完整 URL，NEVER 用 base + path 拼接**——拼接漏段时对端返的是业务码而不是 404，极难定位」；
  「**`refundResultUrl` 默认值为空**，由 K8s Deployment env 注入；旧 collect-pay-server 同族三条通知地址硬编码了
  `testngbackV2` 测试域名，本模块 NEVER 继承」。
- 同文件 `:8~10` —— 「Bean 由启动类 `DailyTicketServer` 上的 `@ConfigurationPropertiesScan` 自动注册，
  与本包已有的 `DailyTicketPayProperties` 走同一条装配路径，**NEVER 另加 `@Component` 或单独的
  `@EnableConfigurationProperties`**（会重复注册）」。
- 同文件 `:47~52` —— 「出向通知当前不加签，与 pay-sign / ticket / collect-pay 三条同族链路现状一致。
  与 AGENTS.md §5.2 的鉴权要求冲突、属测试期敞口，上线前 MUST 补；**NEVER 在本模块自造签名逻辑**，
  要加 MUST 对齐现有验签实现」（`signType` 默认 `00` 免签）。
- `daily-ticket-server` / `DailyTicketRefundController.resubmit`（`.../controller/DailyTicketRefundController.java:86~89`）
  —— 「⚠️ 与本控制器其余端点一致，**当前无鉴权**，与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权与归属校验」冲突，
  属测试期临时降级；上线前 MUST 补验签或限定只能由内网 web-admin 经 rpc 调用。也**不做幂等**：
  连调两次会向支付平台发两次请求（对端按 `refundOrderNo` 幂等，不会重复退款，但会多两条 `DAILY_TICKET_PAY_LOG`）」。
- `daily-ticket-server` / `DailyTicketRefundSettlementService.updatePlatformRefundNo`（`.../service/refund/DailyTicketRefundSettlementService.java`；发起侧 `DailyTicketRefundInitiationService` 有一个同名私有方法）
  —— 「支付平台版本的字段名存在 `refundOrderNo`/`refundNo` 两种实现，优先读取文档字段，兼容旧实现」。
- `daily-ticket-server` / `DailyTicketPaymentService.queryDailyTicketPayInfo`（`.../service/payment/DailyTicketPaymentService.java`）
  —— 「`payOrderNoDate` 是「购票付款时刻」而不是本次过闸时刻，长周期票会显示成很早的时间，属有意为之」，
  格式为 `new SimpleDateFormat("yyyyMMddHHmmss")`。
- `daily-ticket-server` / `DailyTicketInstanceLifecycleService.validateEntryCheck`（无实例分支，`.../service/lifecycle/DailyTicketInstanceLifecycleService.java`）
  —— 「无有效日票实例，返回失败但允许闸机走常规流程」（该分支返失败码，不是 `0000`）。

### 五、对账导出（DETAIL / PAY）

**契约与判据**

- `daily-ticket-server` / `ReconExportService.SOURCE`（`java/com/chinasofti/huateng/dailyticket/service/ReconExportService.java:56~57`）
  —— 「本源在对账体系里的来源标识，与 recon-server 注册的源名一致，NEVER 改」（值 `daily-ticket`，负责 DETAIL 与 PAY 两类文件）。
- `daily-ticket-server` / `ReconExportMapper`（类注释，`java/com/chinasofti/huateng/dailyticket/mapper/ReconExportMapper.java:20~33`）
  与 `ReconExportService.exportDetail` / `exportPaySummary`（`.../service/ReconExportService.java:165~173`、`:239~250`）
  —— DETAIL 7 段：`运营日期 | 交易类型 | 逻辑卡号 | 交易日期时间 | 交易金额 | 当前车站名称 | 设备编码`；
  PAY 21 段 = 前 5 段键 + 后 16 段度量，本模块**只填「旅游票张数(发售) / 旅游票金额」这一组（0 基下标 9 / 10）**，
  其余 14 段度量一律写字面 `0`。
- `daily-ticket-server` / `ReconExportService.TXN_TYPE_SALE`（`.../service/ReconExportService.java:59~66`）
  —— DETAIL 第 2 段固定中文「发售」，「甲方原文即中文「发售」」，「分片按 UTF-8 写出（见 `ReconPartSink`），
  **NEVER 改成拼音或英文缩写** —— 这是甲方文件内容契约的一部分，改了对方解析不出」。
- 同类 `exportDetail`（`.../service/ReconExportService.java:171~173`）—— 「第 2 段固定写中文串 `TXN_TYPE_SALE`「发售」，
  第 6、7 段固定写空串 —— 甲方原文「车票购买的交易在对账文件中交易类型为发售，当前车站类型和设备编码传空」。
  **NEVER 拿订单来源或渠道去填第 6、7 段**：甲方要的是空，填了反而对不上」。
- `daily-ticket-server` / `ReconExportMapper`（口径小节，`.../mapper/ReconExportMapper.java:35~44`）
  —— 「口径（跨服务契约，改动 MUST 同步 recon-server 与另外三个源服务）」：
  DETAIL 只导 `PAY_STATUS = 'PAID'`、窗口按 `PAY_DATE`（支付完成时点，TIMESTAMP）切，**不是** `CREATE_TIME`；
  PAY 同样取 `PAY_STATUS='PAID'` + `PAY_DATE` 窗口，额外加 `PARENT_ORDER_NO IS NOT NULL` 收窄到旅游票子单，
  并按 `PAY_CHANNEL_CODE` 拆到支付方式维度；「窗口一律左闭右开，跨两个自然日，故两类文件的日期段都由时间列现算，
  NEVER 用指令里的 `businessDate` 顶替」。
- `daily-ticket-server` / `ReconExportMapper.selectDetailPage`（`.../mapper/ReconExportMapper.java:54~63`）
  —— Keyset 游标是 `(PAY_DATE, ORDER_NO)`；「**NEVER 改成大页码 OFFSET** —— Oracle 的 `OFFSET n ROWS` 仍要扫掉前 n 行，
  数据量上来后单页耗时随页码线性上涨，而抽取跑在阻塞 DB 调用上，慢 SQL 会 pin 住虚拟线程的载体线程。Keyset 每页代价恒定」。
- `daily-ticket-server` / `ReconExportMapper.selectTravelTicketPaySummary`（`.../mapper/ReconExportMapper.java:96~116`）
  —— 「**张数用 `COUNT(*)`，NEVER 换回 `SUM(TICKET_COUNT)`**」；金额取 `NVL(SUM(NVL(PAY_AMOUNT, TICKET_PRICE)), 0)`，
  「内层 `NVL` 只兜历史脏数据；外层 `NVL` 兜「无行时 `SUM` 返回 NULL」」；
  「`PARENT_ORDER_NO IS NOT NULL` 是识别旅游票子单的**唯一判据**：独立日票该列为空。
  NEVER 改用 `ORDER_TYPE`（旅游票子单与独立日票同为 `'1'`）或 `CARD_TYPE`（原样落 APP 入参、混着三套编码空间）」；
  「「支付方式」段（PAY 第 5 段键）现在填子单 `PAY_CHANNEL_CODE`……因此分组键含该列，一天可能多行」。
  汇总「**MUST 在库内做**，NEVER 拉全量明细回 JVM 再 group」（`:81~82`）。
- `daily-ticket-server` / `ReconExportService.exportPaySummary`（`.../service/ReconExportService.java:261~267`）
  —— 「线路 / 车站 / 设备编号三段仍恒为空串：本模块 5 张表都没有对应列（`DAILY_TICKET_ORDER`、
  `DAILY_TICKET_INSTANCE`、`DAILY_TICKET_PAY_LOG`、`DAILY_TICKET_REFUND`、`TRAVEL_TICKET_ORDER` 已全量核对）。
  空串参与键的组成，因此这三段 NEVER 改成 null 或占位符，否则与其他源的键空间对不上」。
- 同方法 `:269~275` —— 「**线路段（第 2 段）自 2026-09-16 起由 recon-server 统一补齐**：它在聚合完成、写文件之前
  按**车站段**反查 `STATION_INFO`，且**无条件覆盖**本模块送来的值（见 recon-server 的 `ReconStationMapper` 与
  `recon.line-backfill.*`）。因此本模块这一段的语义变了：不再是「本模块没有线路列所以空」，而是「四个源一律不出线路」。
  由于本模块**连车站段也没有**，recon-server 反查不到、补出来仍是空串，**文件内容与收口前逐字节一致**」。
- `daily-ticket-server` / `ReconExportService.submit`（`.../service/ReconExportService.java:106~111`）
  —— 「受理抽取指令：校验后排入抽取线程池，**立即返回**」，返回 `false` 表示「同批次仍在途，本次丢弃（限流语义）」。

**决策理由**

- `daily-ticket-server` / `ReconExportService`（类注释，`.../service/ReconExportService.java:33~36`）
  与 `ReconExportMapper`（`.../mapper/ReconExportMapper.java:24~27`）
  —— 「**本模块只产出 DETAIL 的「车票购买（发售）」行。**甲方规格里 DETAIL 还有另一类行：
  「超时的行程在对账文件中类型为出站，费用为线网最高票价或者 1」。那部分数据在闸机出站扣费链路上，
  **由 gate-txn-pay-server 负责产出，本模块 NEVER 产出** —— 本模块 5 张表既没有进出站信息也没有超时费用，
  硬凑只能凭空造数。两个源各写各的行，由 recon-server 拼成同一个 ITP.DETAIL 文件」。
- `daily-ticket-server` / `ReconExportService.exportDetail`（`.../service/ReconExportService.java:177~189`）
  与 `ReconExportMapper.xml:31~46` —— **票种范围不收窄是有意降级、不是遗漏**，三个候选判据逐一核对后都不可靠：
  ① `DAILY_TICKET_INSTANCE.CODE_TICKET_TYPE` DDL 默认 `'0441'`（`sql/daily-ticket-server-schema.sql:60`），
  激活时代码又无条件写 `CardTypeCodeEnum.QR_POSTPAID.getCode()` 即 `'0441'`，全表同值、零区分度
  （XML 侧补一句「该列名不副实，`'0441'` 是二维码后付费单程票，多日计次票应为 `'0448'`」）；
  ② `DAILY_TICKET_ORDER.CARD_TYPE` 原样落 APP 入参，入口只校验非空、**没有**调
  `CardTypeMapping.isSupportedAppCardType`，库里可能混着三套编码空间（APP 口径含聚合桶 `'05'`，
  一个值覆盖 `0445`~`0448`、本身分不出计次票；ACC 两位口径 `'48'`；发卡口径 `'0448'`）；
  ③ `SHOW_TYPE` 同样原样落入参，全仓库无比较点、语义未定义。
  结论：「**宁可多导并标注，NEVER 凭猜写码值** —— 猜错的筛选条件会静默命中 0 行或漏掉整类票」。
- `daily-ticket-server` / `ReconExportService.exportDetail`（`.../service/ReconExportService.java:191~199`）
  与 `ReconExportMapper.xml:48~55` —— 「**旅游票子单也在本明细里，这是已知事实、不是遗漏。**……
  PAY 是按日期与支付方式的汇总、DETAIL 是逐笔明细，**两者并存不构成重复计账**。未决点：甲方是否要求 DETAIL
  只含独立日票（不含旅游票子单），规格原文里**没有依据**。若日后甲方明确要求排除，改法是在 `selectDetailPage`
  的 WHERE 追加 `AND O.PARENT_ORDER_NO IS NULL`；**当前无依据、不加**，NEVER 凭推测加这个条件 ——
  加错等于让整类已售票在 ACC 侧凭空消失」。
- `daily-ticket-server` / `ReconExportMapper.selectDetailPage`（`resources/mapper/ReconExportMapper.xml:78~85`）
  —— 关于 `LEFT JOIN DAILY_TICKET_INSTANCE`：该表 `ORDER_NO` 上有唯一索引 `UK_DAILY_TICKET_INSTANCE_ORDER`
  （`daily-ticket-server-schema.sql` 第 82 行），与订单一对一，JOIN 不会放大行数，Keyset 游标仍唯一；
  「用 LEFT 而非 INNER：已付但尚未激活、未生成票实例的订单也必须出现在发售明细里，改成 INNER 会静默少账
  （此时逻辑卡号段为空串）」。
- `daily-ticket-server` / `ReconExportMapper.selectTravelTicketPaySummary`（`.../mapper/ReconExportMapper.java:123~138`）
  —— 「**索引：不新增，现有两条够用**」：
  ① `IDX_DAILY_TICKET_ORDER_RECON (PAY_STATUS, PAY_DATE, ORDER_NO)` —— 前导列 `PAY_STATUS` 等值、`PAY_DATE` 范围，
  「正是 B-tree 能用的「等值 + 范围」组合，一次 range scan 即定位到窗口内的已付单」；
  ② `IDX_DAILY_TICKET_ORDER_PARENT (PARENT_ORDER_NO)` —— 「Oracle 的 B-tree **不存储全 NULL 键**，
  所以这条单列索引里只有旅游票子单，对 `IS NOT NULL` 反而是可用的访问路径」；
  两条都要回表、都不是覆盖索引，「想做成覆盖索引就得把 4 列都塞进键……那是为一天跑一次的对账 SQL
  给高频写入的订单表加一条宽索引，**不划算，故不加**」；
  「原 `IDX_TRAVEL_TICKET_ORDER_RECON ON TRAVEL_TICKET_ORDER (PAY_STATUS, UPDATE_TIME)` 已随本次口径变更
  从脚本中**删除** —— 不再查主单，那条索引无用」。
- `daily-ticket-server` / `ReconExportService`（类注释，`.../service/ReconExportService.java:38~51`）
  —— 「受理与抽取严格分离：`submit(ReconExportReqDTO)` 只做校验 + 入队，抽取跑在本类自己的**固定大小平台线程池**上」；
  「**NEVER 在请求线程上跑抽取**：全服务 `spring.threads.virtual.enabled=true`……抽取是「几百万行 × 阻塞 JDBC」，
  必须放在平台线程上，因此这里用 `Executors.newFixedThreadPool` 显式建平台线程，
  NEVER 换成 `newVirtualThreadPerTaskExecutor` 或 `@Async` 默认执行器」；
  「并发控制不引入任何中间件（本项目不用 Redis / MQ）：同一 batchId 在途时用 `ConcurrentHashMap#newKeySet`
  标记并直接回绝重复指令，收尾在 finally 里移除。recon-server 侧本身会超时补偿重推，回绝属限流、不是失败」。
- `daily-ticket-server` / `ReconExportService.runExport`（`.../service/ReconExportService.java:134~144`）
  —— 「方法上**故意不加 `@Transactional`**：抽取是长循环的只读扫描，包在事务里会让一个数据库事务横跨整段导出（分钟级），
  Druid 的 `remove-abandoned-timeout` 到点会强杀连接，抽取整体失败；且事务内还夹着向 recon-server 上送分片的 HTTP 调用，
  正好触碰「`@Transactional` 内 NEVER 发起 RPC」这条红线。这里每条 SQL 自动提交，读到哪算哪」；
  「某一类文件失败只影响该类：catch 住记 `log.error` 后继续下一类。未 `commit()` 就 `close()` 时 `ReconPartSink`
  会自动向 recon-server 声明该类失败，因此 NEVER 自己吞掉异常又不留痕——那会让 recon-server 永远停在 EXPORTING 等不到收齐」。
- `daily-ticket-server` / `ReconExportController`（`java/com/chinasofti/huateng/dailyticket/controller/internal/ReconExportController.java:26~28`）
  —— 「本接口只受理、不干活：抽取由 `ReconExportService` 丢到自己的平台线程池执行，这里**必须立即返回**。
  若在请求线程上同步跑抽取，阻塞的 ojdbc8 调用会 pin 住虚拟线程的载体线程，严重时全 JVM 虚拟线程停止调度」。
- `daily-ticket-server` / `ReconExportService.shutdown`（`.../service/ReconExportService.java:100~104`）
  —— 「关闭抽取线程池，避免容器停机时留下悬挂线程」（`@PreDestroy`）。

**陷阱**

- `daily-ticket-server` / `ReconExportMapper.xml`（文件头注释，`resources/mapper/ReconExportMapper.xml:9~11`）
  —— 「SQL 正文里一律不写注释：Druid WallFilter 的 `commentAllow` 默认为 false，SQL 带注释会被判定成注入并抛异常，
  该语句静默失效（只在 Oracle 生产暴露，达梦环境关了 WallFilter 看不出来）。所有说明只能留在这里的 XML 注释或 Java 注释中」。
  同款告示在 `DailyTicketInstanceMapper.xml:58`：「NEVER 在下面的 SQL 正文里加注释（Druid WallFilter 会判定为注入并让该语句静默失效）」。
- 同文件 `:13~15` —— 「时间条件直接绑 `java.sql.Timestamp`，NEVER 对窗口列套 `TO_CHAR` / `TRUNC` 之类函数做比较，
  否则该列上的索引失效，退化成全表扫。SELECT 列表与 GROUP BY 里的 `TO_CHAR` 只作用于输出分组，不影响 WHERE 的索引使用」。
  日期段的实际产出是 `TO_CHAR(O.PAY_DATE,'YYYYMMDD')`（运营日期 / `TXN_DATE`）与
  `TO_CHAR(O.PAY_DATE,'YYYYMMDDHH24MISS')`（交易日期时间）。
- `daily-ticket-server` / `ReconExportMapper`（`.../mapper/ReconExportMapper.java:14~18`）
  —— 「**NEVER 按本项目自己的口径重排字段** —— 分片是纯文本、无 schema，错位不报错，只会静默出错账」；
  「本 Mapper 只读三张表、不写任何表，因此不参与事务。**NEVER 在这里加写方法** —— 抽取链路一旦带上写操作，
  长循环期间就会持有行锁，与虚拟线程 pin 叠加会放大成全 JVM 停止调度的事故」。
- `daily-ticket-server` / `ReconExportService.exportPaySummary`（`.../service/ReconExportService.java:247~250`）
  —— 「**NEVER 把不负责的度量写成空串**：空串在 `ReconRecord#metric(String[], int)` 里虽然按 0 处理，
  但段数与其他源不一致时会整行错位」。
- `daily-ticket-server` / `ReconExportService.pick`（`.../service/ReconExportService.java:305~310`）
  —— 「MyBatis 的 `resultType=map` 用驱动返回的列标签做 key，Oracle 下是大写；但 `mapUnderscoreToCamelCase`
  之类的全局配置在不同基础构件版本上表现过差异，这里同时兜住大写列名与驼峰名，避免「取不到值→整列写空串」这种静默错账」。
- `daily-ticket-server` / `ReconExportService.toTimestamp`（`.../service/ReconExportService.java:362~371`）
  —— 「ojdbc8 对 `TIMESTAMP` 列的返回类型并不唯一：多数配置下是 `Timestamp`，也可能是驱动自有的 `oracle.sql.TIMESTAMP`
  （它不是 `Date` 的子类，直接强转会 `ClassCastException`）。这里按类型逐级兜底，最后一级用反射调 `timestampValue()`」；
  「返回 null 表示无法识别，调用方会立刻抛异常终止本类文件的抽取 —— **NEVER 静默把游标置回 null**，
  那会让 Keyset 从头再翻一遍，形成死循环并重复上送分片」。对应 `exportDetail` 的
  `IllegalStateException("对账明细游标缺失，拒绝继续翻页以免漏行或死循环 ...")`（`:227~230`）。
- `daily-ticket-server` / `ReconExportService.toLong`（`.../service/ReconExportService.java:341~344`）
  —— 「金额与笔数一律按 long 取。Oracle 的 `NUMBER` 经 ojdbc8 回来通常是 `BigDecimal`，
  少数聚合列可能是 `Long` / `Integer`，都在这里收口」。
- `daily-ticket-server` / `ReconExportController`（`.../controller/internal/ReconExportController.java:16~24`）
  —— 「**【开发测试阶段：本接口当前无鉴权，上线前 MUST 恢复】**用户 2026-09-11 明确要求「删除令牌要求，不用令牌了，
  当前处于开发测试阶段」，故原先的 `X-Recon-Token` 共享令牌校验已整段删除。本模块没有 spring-security、
  也没有全局验签拦截器，因此现状等于允许任何网络可达方触发全量导出，与 AGENTS.md §5.2……相冲突，
  属**有意为之的临时降级**」；「恢复方式：重新引入 `recon.internal-token` 配置与请求头 `X-Recon-Token` 的逐字节比对，
  **MUST 用 `java.security.MessageDigest#isEqual` 而不是 `equals`**，避免按字符短路带来的时序侧信道」。

### 六、持久层与 mapper

**契约与判据**

- `daily-ticket-server` / `DailyTicketInstanceMapper.selectByTicketCode`（`java/com/chinasofti/huateng/dailyticket/mapper/DailyTicketInstanceMapper.java:18~20`）
  —— 「**入参用票号而不是卡号**：一张卡可以先后买过多张日票，按卡号查会命中历史多单（`selectByCardNum` 就有这个性质），
  票号才唯一对应「某一趟行程用的那张票」」。
- `daily-ticket-server` / `DailyTicketRefundMapper.selectPendingNotify`（`.../mapper/DailyTicketRefundMapper.java:39~42`）
  —— 「捞待投递的退款通知（扫表补偿入口）」，`maxTimes` 为投递次数上限、达到即不再捞出；
  XML 侧补「取件顺序按 `UPDATE_TIME` 升序（先到先投），条数由调用方限流，避免一批扫太多把出网拖长」
  （`resources/mapper/DailyTicketRefundMapper.xml:112`）。
- `daily-ticket-server` / `DailyTicketOrderMapper.selectRefundOrders`（`resources/mapper/DailyTicketOrderMapper.xml:68`）
  与 `DailyTicketRefundMapper.selectRefunds`（`resources/mapper/DailyTicketRefundMapper.xml:31`）
  —— 「订单、票实例和退款单一并返回，页面无需逐条查询详情」；
  「退款表为主表；关联订单取得支付平台订单号，关联票实例辅助运营判断」。
- `daily-ticket-server` / `DailyTicketPayLogMapper.selectLatestRefundResponseBody`（`resources/mapper/DailyTicketPayLogMapper.xml:16`）
  —— 「历史退款单创建时未单独存平台退款单号，查询前从原退款响应中恢复该字段」，
  `BIZ_TYPE in ('REFUND', 'REFUND_RETRY', 'REFUND_RESUBMIT')`。
- `daily-ticket-server` / `DailyTicketUsageLogMapper.selectByCardNum`（`resources/mapper/DailyTicketUsageLogMapper.xml:40`）
  —— 「按卡号查扣次明细，时间倒序」。
- `daily-ticket-server` / `DailyTicketRefundQueryService.pageRefundOrders` / `pageRefundRecords`
  （`.../service/DailyTicketRefundQueryService.java`）—— 「统一收敛分页参数，避免无效页码和超大页造成数据库压力」；
  「退款记录与订单检索分开分页，页面可独立追踪核验退款的后续状态」。

**陷阱**

- `daily-ticket-server` / `DailyTicketInstanceMapper.selectByTicketCode`
  （`.../mapper/DailyTicketInstanceMapper.java:21~27` 与 `resources/mapper/DailyTicketInstanceMapper.xml:54~58`）
  —— 「2026-09-15 实测 `DAILY_TICKET_INSTANCE` 共 12 行、`TICKET_CODE` 12 个不重复、无空值，
  但**库里没有唯一索引保证它唯一**，因此 SQL 侧显式取最新一行、**NEVER 去掉那个 `FETCH FIRST 1 ROWS ONLY`** ——
  真出现重复票号时 MyBatis 会抛 `TooManyResultsException`，而本查询只是详情页的富化步骤，
  不该因为它把整个 IF8A-34 打挂」。
- `daily-ticket-server` / `DailyTicketRefundMapper.selectPendingNotify`（`resources/mapper/DailyTicketRefundMapper.xml:109~112`）
  —— 「NVL 包 `NOTIFY_TIMES` 是必要的：回调收口时只写 `NOTIFY_STATUS` 与 `NOTIFY_TIMES=0`，但历史行这两列都是 NULL，
  而 Oracle 里 NULL 参与比较恒为 UNKNOWN，不包 NVL 时那些行永远捞不出来」。
- `daily-ticket-server` / `DailyTicketRefundMapper.updateNotifyStatus`（`resources/mapper/DailyTicketRefundMapper.xml:95~98`）
  —— 「IF8B-04 通知投递状态回写。四列一起写，NEVER 拆成多条语句：失败分支要同时推进 `NOTIFY_TIMES` 与写失败原因，
  拆开后中途异常会留下「次数没加但原因写了」的行，扫表就会永久重投同一条。`UPDATE_TIME` 一起刷新，
  让 `selectPendingNotify` 的排序自然轮转，不至于同一条失败行反复占住批次头部」。
- `daily-ticket-server` / `daily-ticket-refund-page-migration.sql`（`resources/sql/daily-ticket-refund-page-migration.sql:1~3`）
  —— 「在已创建 `DAILY_TICKET_ORDER`、`DAILY_TICKET_REFUND` 表的环境中执行一次」，
  「执行前请确认 `DAILY_TICKET_REFUND.ORDER_NO` 不存在重复记录」。
  本模块 `resources/sql/` 下现有 5 个脚本：`daily-ticket-server-schema.sql`（只服务新建库）+
  `daily-ticket-recon-export-index.sql` / `daily-ticket-refund-notify-migration.sql` /
  `daily-ticket-refund-page-migration.sql` / `daily-ticket-usage-log-migration.sql` 四个增量脚本。
  **注意：AGENTS.md §8 那条「`*-schema.sql` 只服务新建库、新增列/索引 MUST 出独立 `*-migration.sql` 并执行 + 回查」
  在本模块源码注释里只有上面那一条脚本头注释是相关的，其余四个脚本没有任何头注释**，
  因此「这些脚本是否已在 `AFCITPDB` 执行过」在代码注释里查不到，MUST 现查库。

### 墓碑注释清单（建议转为断言测试）

不进上面正文，只在此列出「禁止某个具体改法」的注释。行号同样会漂，引用前 MUST 先 grep。

| 序 | 文件:行号 | 禁止的事 | 能否断言化 |
|---|---|---|---|
| 1 | `java/com/chinasofti/huateng/DailyTicketServer.java:13` | NEVER 加 `@EnableRpcCardPool` | 能。扫启动类注解集合，断言不含 `EnableRpcCardPool` |
| 2 | `.../service/lifecycle/DailyTicketInstanceLifecycleService.java` `updateTicket` | NEVER 在激活处再向 card-pool-server 预占卡号 | 能。单测断言 `updateTicket` 落库的 `CARD_NUM` 恒等于入参 `cardNum` |
| 3 | `.../service/lifecycle/DailyTicketInstanceLifecycleService.java` `updateAndNotice` | `updateAndNotice` NEVER 置 `EXPIRED` 或任何终态 | 能。调用后断言 `TICKET_STATUS == USED` |
| 4 | `.../service/lifecycle/DailyTicketInstanceLifecycleService.java` `markUsed` | `countingEnd` 为 null 时 NEVER 覆盖库里已有的有效期 | 能。传 null 后断言 `COUNTING_END` 不变 |
| 5 | `.../service/lifecycle/DailyTicketInstanceLifecycleService.java` `validateEntryCheck` | NEVER 用 `<= 0` 判断计次票次数用完 | 能。`actualTimes=-99` 进站断言放行、`=0` 断言拒绝 |
| 6 | `resources/mapper/DailyTicketInstanceMapper.xml:153` | NEVER 写成 `TICKET_STATUS != 'USED'` | 能。XML 文本断言 + `USED` 行可被 `selectForEntryCheck` 命中的集成用例 |
| 7 | `resources/mapper/DailyTicketInstanceMapper.xml:56` / `.../mapper/DailyTicketInstanceMapper.java:23` | NEVER 去掉 `FETCH FIRST 1 ROWS ONLY` | 部分。需造重复 `TICKET_CODE` 的集成用例；退化方案是 XML 文本断言 |
| 8 | `.../service/order/DailyTicketOrderCreationService.java` `validateTravelOrderRequest` | NEVER 直接采信 APP 上送的 `totalAmount` 落库 | 能。`totalAmount != ticketPrice*ticketCount` 断言拒单 |
| 9 | `.../service/DailyTicketService.java` / `.../controller/DailyTicketController.java` | NEVER 把 `queryDailyTicketPayInfo` 合并进 `queryDailyTicketInfo` | 弱。只能反射断言两个方法/两条 URL 并存 |
| 10 | `.../service/DailyTicketService.java` / `.../service/payment/DailyTicketPaymentService.java` `queryDailyTicketPayInfo` | 查不到实例或订单时 NEVER 返失败码（MUST 返 `0000` + 三字段空） | 能。无实例 / 无订单两个分支断言 `retCode=0000` |
| 11 | `.../service/ReconExportService.java:275` | 将来补上车站段后，NEVER 在本模块加 `STATION_INFO` 的 join 自己算线路 | 能。断言本模块 mapper XML 全文不含 `STATION_INFO` |
| 12 | `.../service/ReconExportService.java:199` / `resources/mapper/ReconExportMapper.xml:55` | NEVER 凭推测给 DETAIL 加 `AND O.PARENT_ORDER_NO IS NULL` | 能。XML 文本断言 `selectDetailPage` 不含该谓词 |
| 13 | `.../mapper/ReconExportMapper.java:30` / `resources/mapper/ReconExportMapper.xml:29` | 张数 NEVER 用 `COUNT(*)`（MUST `SUM(NVL(T.TICKET_COUNT,0))`；PAY 按**主单**统计） | 能。XML 文本断言含 `SUM(NVL(T.TICKET_COUNT, 0))`、不含 `COUNT(*)` |
| 14 | `.../mapper/ReconExportMapper.java:30` | PAY 取数对象 NEVER 回退成子单（MUST 主单 `TRAVEL_TICKET_ORDER`） | 能。XML 文本断言 `selectTravelTicketPaySummary` 的 `FROM` 是 `TRAVEL_TICKET_ORDER`、不含 `PARENT_ORDER_NO` |
| 15 | `.../mapper/ReconExportMapper.java:17` | NEVER 在 `ReconExportMapper` 里加写方法 | 能。反射断言接口方法名全部以 `select` 开头 |
| 16 | `.../mapper/ReconExportMapper.java:14` | NEVER 按本项目口径重排对账字段 | 能。对 `ReconRecord.line(...)` 出参做逐段快照测试（DETAIL 7 段 / PAY 21 段） |
| 17 | `.../service/ReconExportService.java:47` | NEVER 换成 `newVirtualThreadPerTaskExecutor` 或 `@Async` 默认执行器 | 能。断言抽取线程名前缀 `recon-export` 且非虚拟线程 |
| 18 | `.../service/ReconExportService.java:63` | 「发售」NEVER 改成拼音或英文缩写 | 能。常量值断言 |
| 19 | `.../service/ReconExportService.java:250` | 不负责的度量段 NEVER 写成空串 | 能。断言 PAY 行 21 段中 14 段为字面 `0` |
| 20 | `.../service/ReconExportService.java:266` | 线路 / 车站 / 设备编号三段 NEVER 改成 null 或占位符 | 能。断言这三段为空串 |
| 21 | `.../service/ReconExportService.java:371` | NEVER 静默把 Keyset 游标置回 null | 能。喂不可识别时间类型，断言抛异常而非继续翻页 |
| 22 | `resources/mapper/ReconExportMapper.xml:13` | NEVER 对窗口列套 `TO_CHAR` / `TRUNC` 做比较 | 能。XML 文本断言 WHERE 段不含这两个函数 |
| 23 | `resources/mapper/ReconExportMapper.xml:9` / `resources/mapper/DailyTicketInstanceMapper.xml:58` | NEVER 在 SQL 正文里写注释（Druid WallFilter 静默拒绝） | 能。扫全部 mapper XML 的 SQL 正文（剔除 XML 注释）断言无行注释符与块注释 |
| 24 | `resources/mapper/DailyTicketRefundMapper.xml:95` | 通知四列 NEVER 拆成多条语句写 | 弱。只能 XML 文本断言单条 `update` 含四列 |
| 25 | `.../client/DailyTicketAppNotifyClient.java:25` | NEVER 抛异常、NEVER 返回 null | 能。传坏 URL / 超时场景断言返回 `failed` 而非抛出 |
| 26 | `.../client/DailyTicketAppNotifyClient.java:30` | NEVER 退回裸 JSON（MUST 用 form 信封 + bizData） | 能。断言 `Content-Type` 为 `x-www-form-urlencoded` 且体含 8 个信封字段 |
| 27 | `.../client/DailyTicketAppNotifyClient.java:36` | NEVER 把 `7004` 加进成功码 | 能。喂 `retCode=7004` 断言判为未投递 |
| 28 | `.../config/DailyTicketAppNotifyProperties.java:9` | NEVER 另加 `@Component` 或单独的 `@EnableConfigurationProperties` | 能。断言容器内该类型 Bean 只有一个 |
| 29 | `.../config/DailyTicketAppNotifyProperties.java:14` | NEVER 用 base + path 拼接通知地址 | 弱。属配置约定，只能断言 properties 里该键是完整 URL 或空 |
| 30 | `.../config/DailyTicketAppNotifyProperties.java:16` | 本模块 NEVER 继承 collect-pay 的 `testngbackV2` 地址 | 能。扫 `application.properties` 断言不含该字符串 |
| 31 | `.../config/DailyTicketAppNotifyProperties.java:52` | NEVER 在本模块自造签名逻辑 | 弱。只能扫本模块无自建签名工具类 |
| 32 | `.../service/DailyTicketRefundNotifyService.java:26` | 本模块 NEVER 加 `@Scheduled` | 能。全模块源码扫描断言 `@Scheduled` 出现次数为 0（需剔除注释） |
| 33 | `.../service/DailyTicketRefundNotifyService.java:152` | IF8B-04 的 bizData NEVER 改字段名 | 能。对 `buildBizData` 出参做键名快照测试（六字段 + `orderType`） |
| 34 | `.../mapper/ReconExportMapper.java:43` | 日期段 NEVER 用指令里的 `businessDate` 顶替 | 能。断言两条 SQL 的日期段来自 `PAY_DATE` 现算 |
| 35 | `.../service/DailyTicketRefundNotifyService.java:100` | 快速路径 NEVER 在落库之前调用 | 弱。调用顺序约束，需以 mock 校验调用序 |

### 抽取范围与未归档说明

已丢弃：复述方法名 / 参数名的普通 Javadoc（`model/**`、`page/**` 的字段注释、`DailyTicketPayProperties`
前 13 个键的一句话说明、`DailyTicketController` 的 `IF8A-6x 日票xx` 单行注释、`DailyTicketService`
的 `@param` / `@return` 行）、`DailyTicketPayGatewayRequest` / `DailyTicketPayGatewayResponse` 的类名复述；
本次抽取范围内**没有** `{@inheritDoc}`、也没有空 Javadoc。

没把握归类、需人工裁一次的 5 条：

1. `.../client/DailyTicketAppNotifyResult.java:18~21`（工厂方法叫 `ok` 而不是 `delivered`，
   因为 record 已为组件生成同名访问器、静态方法重名编译不过）—— 是 Java 语言约束而非业务判据，
   暂归「决策理由」，也可视为「陷阱」。
2. `.../service/payment/DailyTicketPaymentService.java` `updatePayTerminalIfPaying`（`A duplicate success callback may carry the
   platform order number that was missing from the first terminal update. Keep it for refunds.`）——
   全模块唯一一处英文注释，既是 `updatePaymentOrderNo` 的 WHERE 契约又是「重复回调别丢单号」的陷阱，
   暂归「契约与判据」。
3. `.../service/lifecycle/DailyTicketInstanceLifecycleService.java`（`validateEntryCheck` 无实例时「返回失败但允许闸机走常规流程」）
   与 §四那条 `queryDailyTicketPayInfo`「查不到 MUST 返 `0000`」方向相反 —— 两个查询的语义确实不同
   （前者是闸机判据、后者是详情富化），但并列在一起容易被误当成前后矛盾，暂各归各节并在此点明。
4. AGENTS.md §5.1 的另两条 Druid / XML 规则（`where 1 = 1` 打头 + 全可选 `<if>`；mapper XML 注释里连续减号）
   **在本模块注释里没有任何对应文字**。代码事实上已经避开了第一条（`DailyTicketOrderMapper.xml:92`
   与 `DailyTicketRefundMapper.xml:49` 用的是 `<where>` 标签），但那是代码本身、不是注释，
   因此未进正文；本阶段只归档注释，未把这两条补写进代码。
5. 「日期字段格式踩坑」在本模块注释里**没有独立成条**，只有三处弱相关并已分别归入各节：
   `COUNTING_START` / `COUNTING_END` 是毫秒时间戳（`BIGINT`，第三节）、
   窗口列不得套 `TO_CHAR` / `TRUNC` 否则索引失效（第五节）、
   ojdbc8 的 `TIMESTAMP` 返回类型不唯一（第五节）。是否还有别的日期格式坑，注释里查不到。

## 附：daily-ticket-server 注释知识迁移（2026-09-16，阶段二·完整）

阶段一是**摘录**（560 行、按业务主题归类）；本阶段是**迁移前的完整清点**：逐文件盘一遍注释承载的知识，
凡「多行叙述 / MUST-NEVER / 事故史 / 墓碑」一律落进本节，随后从代码删除，代码只留标准 Javadoc。
**保留的一行式护栏见 §覆盖率自评 末尾清单。**

规模口径（2026-09-16 实测）：`src/main` 注释 1332 行、无 `src/test`（本模块**一个单测都没有**）、
SQL 5 个文件共 **106 条 `COMMENT ON`**（`daily-ticket-server-schema.sql` 92 条含 5 条表注释、
`daily-ticket-usage-log-migration.sql` 11 条、`daily-ticket-refund-notify-migration.sql` 4 条、
`daily-ticket-refund-page-migration.sql` 1 条 + 3 行文件头行注释、`daily-ticket-recon-export-index.sql` 0 条）。
**`COMMENT ON` 是 DDL 语句、不是注释，本次不删**；它承载的取值域汇总在 §六。

文件别名：〔impl〕`daily-ticket-server/src/main/java/com/chinasofti/huateng/dailyticket/service/impl/DailyTicketServiceImpl.java`
（**纯委派门面，200 行、无业务逻辑**，下面各条已不再引用它）、
〔lifecycle〕`.../service/lifecycle/DailyTicketInstanceLifecycleService.java`、
〔order〕`.../service/order/DailyTicketOrderCreationService.java`、
〔pay〕`.../service/payment/DailyTicketPaymentService.java`、
〔refundInit〕`.../service/refund/DailyTicketRefundInitiationService.java`、
〔refundCb〕`.../service/refund/DailyTicketRefundCallbackService.java`、
〔refundSettle〕`.../service/refund/DailyTicketRefundSettlementService.java`、
〔refundProgress〕`.../service/refund/RefundProgressService.java`、
〔refundGwReq〕`.../service/refund/RefundGatewayRequests.java`、
〔status〕`.../service/support/DailyTicketInstanceStatus.java`、〔sync〕`.../service/sync/DailyTicketOrderSyncService.java`、
〔svc〕`.../service/DailyTicketService.java`、〔notifySvc〕`.../service/DailyTicketRefundNotifyService.java`、
〔reconSvc〕`.../service/ReconExportService.java`、〔ctl〕`.../controller/DailyTicketController.java`、
〔refundCtl〕`.../controller/DailyTicketRefundController.java`、
〔reconCtl〕`.../controller/internal/ReconExportController.java`、
〔notifyCtl〕`.../controller/internal/RefundNotifyInternalController.java`、
〔instMapper〕`.../mapper/DailyTicketInstanceMapper.java`、〔instXml〕`.../resources/mapper/DailyTicketInstanceMapper.xml`、
〔refundMapper〕`.../mapper/DailyTicketRefundMapper.java`、〔refundXml〕`.../resources/mapper/DailyTicketRefundMapper.xml`、
〔reconMapper〕`.../mapper/ReconExportMapper.java`、〔reconXml〕`.../resources/mapper/ReconExportMapper.xml`、
〔notifyClient〕`.../client/DailyTicketAppNotifyClient.java`、〔notifyResult〕`.../client/DailyTicketAppNotifyResult.java`、
〔payClient〕`.../client/DailyTicketPayGatewayClient.java`、〔notifyProp〕`.../config/DailyTicketAppNotifyProperties.java`、
〔payProp〕`.../config/DailyTicketPayProperties.java`、〔prop〕`daily-ticket-server/src/main/resources/application.properties`、
〔boot〕`.../DailyTicketServer.java`、〔travelEntity〕`.../model/TravelTicketOrder.java`、
〔ddl〕`.../resources/sql/daily-ticket-server-schema.sql`。

### 一、日票 / 多日计次票状态机与取值

**票实例状态机**（`DAILY_TICKET_INSTANCE.TICKET_STATUS`，〔status〕是权威定义、〔ddl〕:104 是列注释）：

| 取值 | 含义 | 是否终态 | 能否进站 | 写入方 |
|---|---|---|---|---|
| `INIT` | 已下单未激活 | 否 | 否 | **无写入方**（票实例首次落库即 `ACTIVATED`） |
| `ACTIVATED` | 已激活未开始使用 | 否 | **能** | 激活 IF8A-67；退款失败回退 |
| `USED` | **已开始使用**（不是「已用完」） | **否** | **能继续**（一日票有效期内不限次、计次票凭剩余次数） | IF8A-71 首用通知；出站扣次未清零 |
| `EXPIRED` | 已过期 / 次数用尽 | 是 | 否 | 出站扣次 `remainTimes == 0`；**2026-09-22 起新增** `DailyTicketExpireService.convergeExpired`（按日期过期，见正文「票实例落库与「过期」口径」） |
| `REFUND_LOCKED` | **核验退款观察期锁定**（不可过闸、不可放款前流转） | 否（可回 `ACTIVATED`） | 否 | 发起核验退款（`refundType='01'`） |
| `REFUNDED` | 已退票 | 是 | 否 | 放款成功（`markRefunded`） |

- **`REFUND_LOCKED` / `REFUNDED` 是 ADR-D124 才补上的，此前这三个值（含 `INIT`）在代码里零写入方**，于是
  「已激活票申请退款 → 观察期内照常乘坐 5 天 → 运营台放款」全程无人拦，实测存量里已有中招数据。
  **四处成组、改一处 MUST 看齐其余三处**：①`requestRefundTicket` 的 `refundType='01'` 分支先
  CAS `ACTIVATED -> REFUND_LOCKED`，抢不到即拒退款；②`resubmitRefundTicket`（**唯一的放款入口**，
  `retryRefundTicket` 与 `queryRefundTicket` 对 type 01 都走不通）放款前复查票必须仍是 `REFUND_LOCKED`；
  ③`markRefunded` CAS 推 `REFUNDED`、`markRefundFailed` CAS 回退 `ACTIVATED`；④`markUsed` 与
  `updateAndNotice` 两个「置已使用」入口都用 `isTicketLockedForRefund` 挡住。
  **进站白名单不需要改** —— `selectForEntryCheck` 只放 `ACTIVATED` + `USED`，两个新值天然被排除，
  **NEVER 为了「让锁定票也能查到」把它们加进那个白名单**。
- **退款前置条件已收窄为「票不存在或票是 `ACTIVATED`」**（ADR-D124）。此前是「票不是 `USED` 就能退」，
  于是 `EXPIRED`（次数用尽 / 过期）票被当成「未激活」走 `refundType='00'` 直连网关**全额退款**，
  实测存量里有这种行（如 `0E202609161745400007`：`REFUND_TYPE='00'` 却有 `TICKET_STATUS='EXPIRED'` 的实例）。
  **NEVER 退回 `"USED".equals(...)` 那种黑名单写法。**

- **`USED` 不是终态 —— 2026-09-10 线上事故**：进站后 APP 的 `updateAndNotice` 把状态推到 `USED`，而
  `selectForEntryCheck` 曾用 `TICKET_STATUS != 'USED'` 过滤，**一日票刷一次就再也进不了站**
  （〔status〕类注释、〔instXml〕:149~155）。**进站白名单只有 `ACTIVATED` + `USED`**，收口条件是有效期
  `COUNTING_END` 与次数 `ACTUAL_TIMES`，**NEVER 写成 `TICKET_STATUS != 'USED'`**。
- **`REFUND_LOCKED` 与 `REFUNDED` 都不在 `selectForEntryCheck` 白名单里，这是「已申请退款的票不能再乘坐」的唯一落点，
  NEVER 把它们加进那个白名单**。该不变量的原文现在写在〔status〕的**类注释**里。
- **`ACTUAL_TIMES` 负数是「不限次」哨兵**（APP 上送 `-99`，见 `DailyTicketActivateReqDTO#actualTimes`；
  〔ddl〕:99 列注释「-99表示不限次」；〔lifecycle〕`validateEntryCheck`）。**NEVER 用 `<= 0` 判次数用完** —— 2026-09-10 线上
  实测 `-99` 被判成「计次票次数已用完」，日票进不了站。一日票 / 多日票走有效期、不走次数。
- 订单状态（`DAILY_TICKET_ORDER.ORDER_STATUS`，〔ddl〕:45）：`CREATED` / `PAYING` / `PAID` / `CANCELED` /
  `REFUNDING` / `REFUNDED` 等；支付状态（`PAY_STATUS`，〔ddl〕:46）：`INIT` / `PAYING` / `PAID` / `FAIL`。
  旅游票主单取值与日票一致（〔travelEntity〕:57~63、〔ddl〕:217~218，主单多一个 `PAY_FAILED`）。
- 退款状态（`DAILY_TICKET_REFUND.REFUND_STATUS`，〔ddl〕:170）：`REFUNDING` / `REFUNDED` / `FAILED` /
  `WAIT_VERIFY`；退款类型（`REFUND_TYPE`，〔ddl〕:171）：`00` 直接退款、`01` 激活后核验退款。
- ACC 通知状态（`ACC_NOTICE_STATUS`，〔ddl〕:109）：`INIT` / `SUCCESS` / `FAIL`。
  **`ACC_NOTICE_*` 四列的唯一写入方是 `updateAccNoticeStatus`（ACC 发售通知链路），NEVER 让任何别的语句写它们**
  （daily-ticket-server 1.0.63 修，ADR-D155）。此前 `markUsed`（出站扣次）与 `updateAndNotice`（APP 首次使用通知）
  都顺手写 `SUCCESS` + 通知时间，而补偿扫表 `selectPendingAccNotice` 只捞 `PENDING` / `FAIL` / `INIT`
  ⇒ **一条从未被 ACC 受理的上报被洗成终态、永久出队，不报错不告警**。
  「乘客出站了」与「ACC 受理了发售」是两件事，**NEVER 用前者断言后者**；
  防回退用 `DailyTicketInstanceAccNoticeIsolationTest`（`ArgumentCaptor` 断言 `markUsed` 不改那两列）。
- **本模块无枚举类**：`TICKET_STATUS` 除 `INIT` 外的取值收在常量类〔status〕，其余状态列仍是各服务里的字符串字面量（正文「状态取值」小节已记）：改任何一个取值 MUST 全局 grep 比较点。
### 二、激活（IF8A-67 / IF8A-32）与出站扣次

- **`CARD_NUM` 直接取 APP 上送的 `cardNum`，NEVER 在激活时再向 card-pool-server 预占卡号**
  （〔lifecycle〕`updateTicket`、〔boot〕:13~19）。该卡号是开户（`businessType=ACCOUNT_OPEN`）时预占并下发给 APP 的那张，
  APP 取码与闸机上送用的都是它。若激活时另占一张，因 `UK_LOGIC_CARD_POOL_BUSINESS` 唯一约束**必然是不同卡号**，
  后续 `selectForEntryCheck` / `markUsed` / `queryDailyTicketInfo` 按 `CARD_NUM` 精确匹配**恒命中 0 行**
  —— 2026-09-10 线上进站被拒即此原因。**本服务 NEVER 加 `@EnableRpcCardPool`**；若后续 ACC 要求日票持独立卡号，
  MUST 先设计「开户卡号 ↔ 日票卡号」映射表并同步改 APP 取码链路。
- **激活时 `CODE_TICKET_TYPE` 无条件写 `'0441'`**（`CardTypeCodeEnum.QR_POSTPAID`，〔lifecycle〕`updateTicket` 内；
  〔ddl〕:93 列注释「码体车票类型，日票固定为0441」）。该列**名不副实**：`0441` 是二维码后付费单程票，
  多日计次票本应是 `0448` —— 这是 §五 票种无法收窄的根因之一。
- **激活只写 `PERIOD` 与 `COUNTING_START`，NEVER 写 `COUNTING_END`**（〔lifecycle〕`updateTicket`，**没有**
  `setCountingEnd`）：有效期截止只由 APP 的 IF8A-33 `updateAndNotice` 在首次使用时写入。因此「激活但从未乘车」
  的票 `COUNTING_END` **恒为空** —— 这是今天 `selectExpiredCandidates` 需要 `fallbackByPeriod`（回退 `ACTIVATE_TIME + PERIOD` 天）
  回退支的唯一原因。`DailyTicketActivateReqDTO` 里也没有 `countingEnd` 字段（有效期压根不在激活入参里）。
- **首次使用通知（IF8A-33）只推 `USED`，NEVER 置 `EXPIRED` 或任何终态**（〔lifecycle〕`updateAndNotice`）：APP 上送的
  `countingEnd` 语义是**有效期截止**（一日票 = 首次使用 + 24h），不是「票已用完」。`FIRST_USE_TIME` 只在首次写入、
  重复通知不覆盖。
- **出站扣次规则**（〔lifecycle〕`markUsed`）：不限次票（`ACTUAL_TIMES < 0`）保持 `USED`、靠 `COUNTING_END` 过期收口；
  计次票扣完最后一次（扣后为 0）推进 `EXPIRED`；仍有剩余次数保持 `USED`。
  扣减是 atomic update（`ACTUAL_TIMES - 1`、下限 0，〔instXml〕:165、〔instMapper〕:36~37），防并发超扣。
- **`countingEnd` 为 null 时 NEVER 覆盖库里已有有效期**（〔lifecycle〕`markUsed`）：闸机出站不带有效期
  （`GateTicketHandler:188` 传的就是 null），有效期只由 APP 的 `updateAndNotice` 写入。
- **扣次明细 `DAILY_TICKET_USAGE_LOG` 靠 `UK_DTUL_ORDER` 幂等**（〔lifecycle〕`insertUsageLog`），`ORDER_NO` 关联
  `GATE_TXN_PAY.ORDER_NO`（〔ddl-usage〕:23 列注释）。**本模块已开 tracing，`DuplicateKeyException` 可能被切面
  包一层 `RuntimeException`，因此 MUST 沿 cause 链判定**（〔lifecycle〕`isIntegrityViolation` 私有方法），
  与 card-pool 的 `isIntegrityViolation` 同源（ADR-D53）。明细落库失败**不中断出站流程**，只记 ERROR 留证据。
- 进站校验查不到实例时**返回失败但允许闸机走常规流程**（〔lifecycle〕`validateEntryCheck`）—— 与 §四那条「详情富化查不到 MUST 返
  `0000`」方向相反，两者语义不同（前者是闸机判据、后者是页面富化），**NEVER 互相套用**。
- **按票号查实例 MUST 保留 `FETCH FIRST 1 ROWS ONLY`**（〔instXml〕:54~58、〔instMapper〕:15~25）：
  2026-09-15 实测 `DAILY_TICKET_INSTANCE` 共 12 行、`TICKET_CODE` 12 个不重复且无空值，
  **但库里没有唯一索引兜着**；真出现重复票号时 MyBatis 会抛 `TooManyResultsException`，而调用方只是
  IF8A-34 详情页的富化步骤，不该因此让整条详情失败。**入参用票号而不是卡号** —— 一张卡可先后买过多张日票。

### 三、旅游票（IF8A-70）：主单聚合支付，子单只承载票实例

- **拆子单不是设计取舍，是表结构约束**（〔order〕`requestTravelOrder`）：`UK_DAILY_TICKET_INSTANCE_ORDER` 限定
  一个订单号只能挂一张票实例，一单挂多票在现有表上无法表达。主单落 `TRAVEL_TICKET_ORDER`（单号前缀 `0T`，
  〔order〕`nextTravelOrderNo`、〔ddl〕:209），每张日票落一条 `DAILY_TICKET_ORDER` 子单（`ORDER_TYPE='1'`、
  `PARENT_ORDER_NO` 指向主单，〔ddl〕:185 列注释「独立日票为空」，日票子单前缀 `0E`）。
- **落库顺序 MUST 先子单、后主单**（〔order〕`requestTravelOrder`）：中途失败只留「父单不存在的孤儿子单」，APP 拿不到
  `orderNo` 也就无法发起支付；反序会留下「可支付但子单缺张」的主单。**本模块没有任何 `@Transactional`
  （全模块 grep 为 0）**，一致性不靠回滚、靠顺序与状态可判定性。
- **金额一律服务端重算**（〔order〕`validateTravelOrderRequest`、〔ddl〕:215）：`totalAmount` 只用于与 `ticketPrice * ticketCount`
  比对，比对不过直接拒单，**NEVER 直接采信 APP 上送值落库**。
- **单次购买张数上限 20（待业务确认）**（〔order〕`MAX_TRAVEL_TICKET_COUNT`）：按张数循环 INSERT，不设上限等于把 for 循环次数
  交给外部输入。
- ⚠️ **本处此前写「主单 `PAY_STATUS` 永不回写，属当前设计、不是缺陷」（含「〔order〕对 `travelTicketOrderMapper` **只有一次 `insert`、没有任何 UPDATE**」）与「**旅游票没有聚合支付**……拿主单号 `0T...` 调 `requestPay` 命中 0 行」两条，已过期**（2026-09-16 的中间态）。**现口径**：
  - **主单就是聚合支付的实际承载**（〔pay〕`requestPay` → `requestTravelPay`）：`orderType="2"` 用 `travelOrderMapper.selectByOrderNo(主单号)`、`updatePayRequest` 置主单 `PAYING`/`PAYING`，送网关**商户单号 = 主单号**、金额 = 主单 `TOTAL_AMOUNT`；回调 `receivePayResult` 查子单命中 0 行即转主单表 → `markTravelPaySuccess` / `markTravelPaySuccessOnCanceled`。`TravelTicketOrderMapper` 现有 `updatePayRequest` / `updatePayResultIfPaying` / `updatePaySuccessByExternalSync` / `updatePaymentOrderNo` / `cancelIfPending` / `cancelIfPaid` / `updatePayResultIfCanceled` 七个写方法。
  - **子单恒 `CREATED`/`INIT`、`PAY_DATE` 恒空仍是设计**（钱按主单收），2026-09-23 库实测一致；激活时 `canActivate` 对子单**回看主单**双 `PAID`。
  - `validateOrderNo` 只校验 `orderType ∈ {"1","2"}` 与非空，**不认单号前缀**；旧文「只校验 `orderType='1'`」「传主单号会被拒」均错。
  - **NEVER 回退成「主单是聚合壳」「旅游票没有聚合支付」，也 NEVER 再据「主单 `PAY_STATUS` 恒为 `INIT`」判 P0。**
### 四、支付、退款与 IF8B-04 通知

- **退款回调（网关 §3.3）三道闸口，顺序不可换**（〔refundCb〕`receiveRefundResult`）：①**幂等** —— 已 `REFUNDED` / `FAILED`
  直接返成功（支付中心会重推，重复推进会把 `REFUND_DATE` 覆盖成第二次回调时间并重置通知次数）；
  ②**白名单** —— 只允许 `REFUNDING` / `WAIT_VERIFY` 推进，**NEVER 写成「非终态即可处理」**；
  ③**结果分派** —— `SUCCESS` / `FAIL` 才推终态，`PROCESSING` 只回填平台退款单号（同方法内）。
- **回调方法只落库，出网交给通知服务**（〔refundCb〕`receiveRefundResult`、〔svc〕`receiveRefundResult`）：收尾那次 `deliverOne` 是
  **快速路径**、排在全部落库语句之后（本模块无事务、每条 SQL 自动提交），且内部 catch 全部异常。
  **NEVER 把它挪到落库之前**，也 **NEVER 在回调里同步发 APP 通知** —— 否则收口响应时长等于 APP 网关响应时长。
  另一条同源不变量：**四个终态入口在 `markRefundNotifyPending` 之后那行 `deliverOne` NEVER 删、也 NEVER 把首次投递
  只挂在回调路径上** —— 同步退款链路比支付中心的异步回调早约 100ms 到，回调必然落进「已是 `REFUNDED`」的幂等分支、
  不会再投递（2026-09-20 生产实测：连续四笔 `NOTIFY_STATUS` 全停在 `PENDING`、`NOTIFY_TIMES=0`）。
  该不变量的原文现在写在〔refundSettle〕的**类注释**里。
- **`PLATFORM_REFUND_NO` 只能写平台单号**（〔refundCb〕组 `refundData` 处）：回调里的 `refundNo` 才是**平台**退款单号，
  `outRefundNo` 是我方商户退款单号。**NEVER 把 `outRefundNo` 放进 `refundOrderNo` 键** —— 否则
  `PLATFORM_REFUND_NO` 被写成我方单号，退款查询永久对不上。字段名两种实现兼容见〔refundSettle〕`updatePlatformRefundNo`。
- **`/resubmit` 与 `/retry` 互斥、NEVER 混用**（〔refundCtl〕:78~89、〔svc〕`resubmitRefundTicket` javadoc）：`retry` 先查再重发，
  前提是支付平台已建过退款单；对端从未受理（`PLATFORM_REFUND_NO IS NULL`）时 `refundQuery` 永远查不到，
  链路会一直卡在「支付平台退款单号缺失，无法执行双字段退款查询」、**退款能力永久丧失**。`resubmit` 是那个死角的
  唯一出口，也是 `WAIT_VERIFY`（核验退款 `REFUND_TYPE='01'`）唯一的出口 —— **该状态在本模块内只被写入、
  从无任何读取方**，观察期（`VERIFY_AFTER_TIME`，〔ddl〕:173「通常为申请后5天」）满后没有任何驱动方，
  因此对已过观察期的核验单放行重提交、观察期内仍拒绝（〔refundProgress〕`resubmitRefundTicket` 白名单三态）。
- **`resubmit` 当前无鉴权、也不做幂等**（〔refundCtl〕:86~89）：连调两次会向支付平台发两次请求（对端按
  `refundOrderNo` 幂等、不会重复退款，但会多两条 `DAILY_TICKET_PAY_LOG`）。与 AGENTS.md §5.2 冲突，
  属测试期临时降级，**上线前 MUST 补验签或限定只能由内网 web-admin 经 rpc 调用**。
- **退款出网异常时既不能返成功也不能返失败**（〔refundInit〕`requestRefundTicket`）：对端可能已受理。顺序 MUST 是
  先 `insertPayLog` 留证据、再把订单置 `REFUNDING` 等回调或人工回查 —— 反过来写时后面那行 UPDATE 一旦再抛，
  **连一条「发过退款请求」的痕迹都不剩**。
- **`notifyUrl` 是网关 §3.1 的必填项**（〔payProp〕:54~59、〔refundGwReq〕、〔pay〕组支付报文处、〔prop〕:46~49）：缺它对端不回调、
  退款单只能靠人工点「退款结果查询」收口，**且日志一片绿、没有任何报错**。组报文用 `putIfText` 而不是 `put`：
  配置为空时不送空串，让网关按缺参报错、别把空地址当有效回调地址。目标路径 MUST 在 `fep-app-server` 的
  `AppDailyTicketController` 注册（扁平那条 `/app/payment/receiveRefundResult`），**改一边 MUST 同步另一边**，
  否则回调落到静态资源处理器、响应退化成全局异常处理器的 UUID `retCode`。
- **IF8B-04 退款结果通知 = 落库状态 + 扫表补偿**（〔notifySvc〕:19~31、〔notifyCtl〕:12~26）：终态收口时只把
  `NOTIFY_STATUS` 置 `PENDING`（〔refundSettle〕`markRefundNotifyPending`，**次数一并重置为 0** —— 终态是新一轮通知，
  NEVER 复用上一轮计数，否则上一轮攒到 4 次的单子这轮只剩 1 次机会就被判 `GIVEUP`）；真正 HTTP 由
  `POST /internal/daily-ticket/refund/notify` 驱动。**本模块 NEVER 加 `@Scheduled`（现在一个都没有，刻意如此）**，
  由 web-admin Quartz 定时打；**排查「退款通知有没有发」MUST 查 `SYS_JOB_LOG` + 本服务日志，
  NEVER 在本模块里找 `@Scheduled`**。每条独立 try/catch —— 一条失败不能中断整批。
- **通知回写四列 MUST 一条语句**（〔refundXml〕:95~98）：失败分支要同时推进 `NOTIFY_TIMES` 与写失败原因，
  拆开后中途异常会留下「次数没加但原因写了」的行、**扫表永久重投同一条**；`UPDATE_TIME` 一起刷新，
  让 `selectPendingNotify` 的排序自然轮转。
- **扫待发 SQL 的 `NVL(NOTIFY_TIMES, 0)` 是必要的**（〔refundXml〕:109~112）：回调收口只写 `NOTIFY_STATUS`
  与 `NOTIFY_TIMES=0`，但历史行两列都是 NULL，而 Oracle 里 NULL 参与比较恒为 UNKNOWN，
  **不包 NVL 那些行永远捞不出来**。取件按 `UPDATE_TIME` 升序、条数由调用方限流（〔notifyCtl〕:25~26）。
- **通知报文形态：x-www-form-urlencoded 的 ITP 信封 + `bizData` 装业务 JSON，NEVER 退回裸 JSON**
  （〔notifyClient〕:30~36、〔prop〕:65~67）：该 APP 网关裸 JSON 实测返 `7004`（ADR-D89），
  **八个信封字段一个都不能少**（缺字段时对端返的还是那个笼统的 `7004`、看不出少了哪个）。
  成功码**只认 `0000` / `code=0`**；解析不出（空体 / 非 JSON）按 2xx 记已投递但打 WARN；
  **NEVER 把 `7004` 加进成功码** —— 那等于把「对端根本没受理」永久记成投递完成。
- **通知结果只有两态、没有「不确定」**（〔notifyResult〕:3~8）：通知幂等可重投（APP 侧按 `orderNo` 去重），
  拿不准按失败重试比按成功丢弃安全。**这与支付 / 退款链路相反** —— 那边的「对端未答」绝不能当失败，因为钱可能已动。
  工厂方法叫 `ok` 而不是 `delivered`（record 已为组件生成同名访问器，重名会编译失败）。
- **`refundResult` 由 `REFUND_STATUS` 反推**（〔notifySvc〕:154~157）：通知只在 `markRefunded` /
  `markRefundFailed` 两个终态入队，因此只会出现 `REFUNDED` / `FAILED`，与回调上送的 `SUCCESS` / `FAIL`
  一一对应；其余状态保守报 `PROCESSING`。六个 bizData 字段逐字对齐 collect-pay 的 `NoticeAppRefundDTO`
  + 多一个 `orderType="1"` 标明日票，**NEVER 改字段名**（对外契约）。
- 出向通知 `signType=00` 免签（〔notifyProp〕:47~52）：与 pay-sign / ticket / collect-pay 三条同族链路现状一致，
  与 AGENTS.md §5.2 冲突、属测试期敞口，**上线前 MUST 补；NEVER 在本模块自造签名逻辑**。
- 通知地址默认值**刻意留空**、由 K8s env `DAILY_TICKET_NOTIFY_APP_REFUND_RESULT_URL` 注入（〔prop〕:60~63、
  〔notifyProp〕:14~16）：旧 collect-pay 同族三条通知地址硬编码了 `testngbackV2` 测试域名，**本模块 NEVER 继承**；
  地址未配置时通知任务记失败并退避重试、不会静默丢弃（已落库，配好即自动补发）。
  **只配完整 URL，NEVER 用 base + path 拼接** —— 拼接漏段时对端返业务码而不是 404、极难定位。
- `NOTIFY_MSG` 列长 500，超长直接 `ORA-12899`，落库前 MUST 截断（〔notifySvc〕:53）。
- `subject` / `body` 这类中文配置 **MUST 写 `\uXXXX` 转义、NEVER 直接写中文**（〔prop〕:53~56）：
  Boot 的 `OriginTrackedPropertiesLoader` 按 ISO-8859-1 读 `.properties`，UTF-8 中文会被解成 mojibake 并原样送到支付中心。
- 日票支付走**本模块专用**网关客户端、绕开 pay-sign-server 的签约卡封装（〔payClient〕:25~28）。
### 五、对账导出（DETAIL / PAY）与索引、窗口

- **来源标识 `daily-ticket`，负责 DETAIL 与 PAY 两类文件**（〔reconSvc〕:25~31、〔reconMapper〕:10~15）；
  段序与取值口径来自甲方 `docs/接口规范文档/ACC与ITP之间的文件.docx` §一：DETAIL = 「（4）虚拟电子多日计次票文件」、
  PAY = 「（2）统计汇总文件」。**NEVER 自行重排字段或改段数** —— 分片是纯文本、无 schema，错位不报错、静默出错账。
- **DETAIL 7 段**：`运营日期|交易类型|逻辑卡号|交易日期时间|交易金额|当前车站名称|设备编码`。本模块**只产出
  「车票购买（发售）」行**，第 2 段固定中文串「发售」（〔reconSvc〕:59~64，**NEVER 改成拼音或英文缩写**），
  第 6、7 段固定空串（甲方明确要求传空，**NEVER 拿订单来源或渠道去填**）。
  另一类「超时行程 → 出站」行**由 gate-txn-pay-server 产出，本模块 NEVER 产出** —— 本模块 5 张表既没有进出站信息
  也没有超时费用，硬凑只能凭空造数（〔reconSvc〕:33~36、〔reconMapper〕:24~27）。
- **PAY 21 段 = 5 段键 + 16 段度量**，本模块**只填「旅游票张数(发售) / 旅游票金额」（0 基下标 9 / 10）**，
  其余 14 段度量写字面 `0`；**NEVER 把不负责的度量写成空串** —— 空串虽按 0 处理，但段数与其他源不一致会整行错位
  （〔reconSvc〕:247~250）。线路 / 车站 / 设备编号三段恒空串（本模块 5 张表已全量核对、确实没有这三列），
  **NEVER 改成 null 或占位符**，否则与其他源的键空间对不上。
  **线路段自 2026-09-16 起由 recon-server 按车站段反查 `STATION_INFO` 并无条件覆盖**，本模块连车站段都没有、
  补出来仍是空串、文件逐字节一致；**将来本模块若补上车站段，NEVER 在本模块 join `STATION_INFO` 自己算线路**
  （〔reconSvc〕:269~275）。
- ⚠️ **本处此前整条（「PAY 取数对象是旅游票子单、不是主单（2026-09-11 改口径）」，含「张数用 `COUNT(*)`，NEVER 换回 `SUM(TICKET_COUNT)`」「金额 `NVL(SUM(NVL(PAY_AMOUNT, TICKET_PRICE)), 0)`」「`PARENT_ORDER_NO IS NOT NULL` 是识别子单的唯一判据」）已过期**。2026-09-22 又改回**按主单统计** —— 子单口径下这条查询恒返 0 行的缺陷自上线起存在，当日修复。
- **PAY 取数对象是旅游票主单、不是子单**（〔reconMapper〕:30~45、〔reconXml〕:29~41）：`TRAVEL_TICKET_ORDER` + `PAY_STATUS='PAID'` + `PAY_DATE` 窗口，
  按（日期, 主单 `PAY_CHANNEL_CODE`）在**库内 GROUP BY**（**NEVER 拉全量明细回 JVM 再 group**）。
  - **张数用 `NVL(SUM(NVL(T.TICKET_COUNT, 0)), 0)`，NEVER 用 `COUNT(*)`** —— 一张主单含 N 张票，`COUNT(*)` 数的是**订单数**、不是张数。
  - **金额用 `NVL(SUM(NVL(T.PAY_AMOUNT, T.TOTAL_AMOUNT)), 0)`**：内层优先 `PAY_AMOUNT`、回退 `TOTAL_AMOUNT`（主单未回写 `PAY_AMOUNT` 时的兜底），外层兜「无行时 `SUM` 返 NULL」，**NEVER 去掉外层 NVL**。
  - **NEVER 改回按子单统计** —— 子单恒 `CREATED`/`INIT`、`PAY_DATE` 恒空，按子单查恒返 0 行（2026-09-22 实测）。
  - 旧文那句「旅游票是每张子单各走一次支付」是前提错误（见 §三）；`PARENT_ORDER_NO IS NOT NULL` 现在 PAY 侧已不使用。
- **票种范围待甲方确认，当前导出全部已付日票（有意降级、不是遗漏）**（〔reconSvc〕:177~189、〔reconXml〕:31~46）：
  三个候选判据逐一核对都不可靠 —— ①`CODE_TICKET_TYPE` 全表恒 `'0441'`、零区分度（〔ddl〕:60 默认值 +
  〔lifecycle〕`updateTicket` 无条件写入）；②`DAILY_TICKET_ORDER.CARD_TYPE` 原样落 APP 入参（〔order〕`requestCountingOrder` 下单、`requestTravelOrder` 旅游票子单，
  入口 `validateOrderRequest` 只校验非空、**没有**调 `CardTypeMapping.isSupportedAppCardType`），库里可能混着
  APP 口径（含日票聚合桶 `'05'`，一个值覆盖 `0445`~`0448`、本身分不出计次票）、ACC 两位口径（`'48'`）
  与发卡口径（`'0448'`）；③`SHOW_TYPE` 同样原样落入参、全仓库无比较点、语义未定义。
  **宁可多导并标注，NEVER 凭猜写码值** —— 猜错会静默命中 0 行或漏掉整类票。
- ⚠️ **本处此前写「旅游票子单也在 DETAIL 里，是已知事实」，该前提已不成立**（〔reconSvc〕`exportDetail`、〔reconXml〕:7~26）：DETAIL 只查 `DAILY_TICKET_ORDER` 且过滤 `PAY_STATUS='PAID'`；而子单恒 `CREATED`/`INIT`、`PAY_DATE` 恒空，**因此旅游票子单一条都进不了 DETAIL**（2026-09-23 库实测：子单 6 行全 `CREATED/INIT`）。旅游票的账只在 PAY 汇总里出现（按主单）。
  **「待复核」**：甲方是否要求 DETAIL 覆盖旅游票购买行，规格原文里**没有依据**；当前实现等价于「DETAIL 只含独立日票」（已付子单实际为 0 行）。**NEVER 凭推测给 `selectDetailPage` 加 `AND O.PARENT_ORDER_NO IS NULL` 或 `IS NOT NULL`** —— 任一方向改错都会让整类票在 ACC 侧凭空消失或重复。**MUST 先与甲方确认口径再动。**
- **窗口一律左闭右开、跨两个自然日**，两类文件的日期段都由时间列现算，**NEVER 用指令里的 `businessDate` 顶替**
  （〔reconMapper〕:42~43、〔reconXml〕:117）。窗口列是 `PAY_DATE`（支付完成时点，TIMESTAMP）、**不是 `CREATE_TIME`**。
  **NEVER 对窗口列套 `TO_CHAR` / `TRUNC` 做比较**（索引失效、退化全表扫）；`SELECT` 列表与 `GROUP BY` 里的
  `TO_CHAR` 只作用于输出分组，不影响 WHERE 用索引（〔reconXml〕:13~15）。
- ⚠️ **本处此前写「索引：不新增，现有两条够用」（`IDX_DAILY_TICKET_ORDER_RECON` 等值+范围、`IDX_DAILY_TICKET_ORDER_PARENT` 走 `IS NOT NULL`）与「原 `IDX_TRAVEL_TICKET_ORDER_RECON ... 已随口径变更删除 —— 不再查主单，那条索引无用」，已过期**（那是按子单统计的口径）。现口径查**主单 `TRAVEL_TICKET_ORDER`**、过滤 `PAY_STATUS='PAID'` + `PAY_DATE` 窗口。
  **索引现状（2026-09-23 核对）**：`sql/daily-ticket-recon-export-index.sql` 只有 `IDX_DAILY_TICKET_ORDER_RECON (PAY_STATUS, PAY_DATE, ORDER_NO)`（**子单表**）；主单表现存索引为 `IDX_TRAVEL_TICKET_ORDER_USER / _STATUS / _CREATE / _PAYMENT / _BATCH_REFUND`（`daily-ticket-server-schema.sql:264~268`），**没有一条以 `PAY_STATUS` 为前导列**（`_STATUS` 与 `_BATCH_REFUND` 都以 `ORDER_STATUS` 打头）。**「待复核」**：是否重建 `IDX_TRAVEL_TICKET_ORDER_RECON` 或确认由 `_BATCH_REFUND` 兜底。**NEVER 照抄旧文「那条索引无用」。**
- **DETAIL 用 Keyset 游标 `(PAY_DATE, ORDER_NO)` 翻页，NEVER 改成大页码 `OFFSET`**（〔reconMapper〕:54~59）：
  Oracle 的 `OFFSET n ROWS` 仍要扫掉前 n 行，单页耗时随页码线性上涨，而抽取跑在阻塞 DB 调用上、慢 SQL 会 pin 载体线程。
  `LEFT JOIN DAILY_TICKET_INSTANCE` 取 `CARD_NUM`：`UK_DAILY_TICKET_INSTANCE_ORDER` 保证一对一、不放大行数，
  游标仍唯一；**用 LEFT 而非 INNER** —— 已付但未激活、未生成票实例的订单也必须出现在发售明细里，
  改 INNER 会静默少账（此时逻辑卡号段为空串，〔reconXml〕:78~85）。
- **抽取跑在固定大小平台线程池上，受理与抽取严格分离**（〔reconSvc〕:38~51、〔reconCtl〕:26~28）：
  `submit` 只校验 + 入队后**立即返回**；**NEVER 在请求线程上跑抽取**、**NEVER 换成
  `newVirtualThreadPerTaskExecutor` 或 `@Async` 默认执行器**（2026-08-26 生产事故形态：虚拟线程在
  `synchronized` 内阻塞 pin 住载体线程，全 JVM 停止调度）。同 batchId 在途用 `ConcurrentHashMap.newKeySet()`
  标记并**直接回绝**（限流语义、不是失败），收尾在 finally 移除；**不引入任何中间件**。
- **抽取方法故意不加 `@Transactional`**（〔reconSvc〕:134~144）：长循环只读扫描包进事务会让一个 DB 事务横跨
  分钟级导出，Druid `remove-abandoned-timeout` 到点强杀连接；且事务内还夹着向 recon-server 上送分片的 HTTP 调用，
  正好触碰「事务内 NEVER 发起 RPC」红线。某一类文件失败只影响该类；未 `commit()` 就 `close()` 时
  `ReconPartSink` 会自动向 recon-server 声明该类失败，**NEVER 自己吞异常又不留痕**（会让 recon-server 永停 `EXPORTING`）。
- **`ReconExportMapper` 只读三张表、NEVER 在这里加写方法**（〔reconMapper〕:17~18）：抽取链路一旦带写操作，
  长循环期间就持有行锁，与虚拟线程 pin 叠加会放大成全 JVM 停止调度。
- **结果 Map 取列同时兜大写与驼峰**（〔reconSvc〕:305~320）：MyBatis `resultType=map` 用驱动列标签做 key（Oracle 下大写），
  而 `mapUnderscoreToCamelCase` 在不同基础构件版本上表现过差异，**不兜住会「取不到值 → 整列写空串」静默错账**。
- **ojdbc8 的 `TIMESTAMP` 返回类型不唯一**（〔reconSvc〕:362~371）：多数是 `java.sql.Timestamp`，也可能是
  `oracle.sql.TIMESTAMP`（不是 `Date` 子类，直接强转 `ClassCastException`）；逐级兜底、最后一级反射调
  `timestampValue()`。**返回 null 时调用方立刻抛异常终止本类抽取，NEVER 静默把游标置回 null** ——
  那会让 Keyset 从头再翻一遍、形成死循环并重复上送分片。
- **对账内部端点当前无鉴权**（〔reconCtl〕:16~24）：`X-Recon-Token` 已按用户 2026-09-11 要求整段删除
  （原话「删除令牌要求，不用令牌了，当前处于开发测试阶段」），`recon.internal-token` 键**已无读取方、不需要注入**，
  **NEVER 再写「空值时一律拒绝」**（已作废）。恢复时 **MUST 用 `MessageDigest.isEqual` 而不是 `equals`**
  （避免按字符短路的时序侧信道）。
- `InternalMicroHttp` 会把整个请求头 Map 打进 INFO 日志（含令牌明文），因此把该 logger 压到 WARN
  （〔prop〕:98~99）；公共构件 `resource/micro` 不改。
### 六、106 条 `COMMENT ON` 承载的取值域（DDL 事实，本次不删）

分布：〔ddl〕`daily-ticket-server-schema.sql` **92 条**（5 条表注释 + 87 条列注释，含 :185 的
`PARENT_ORDER_NO` 补充列）、`daily-ticket-usage-log-migration.sql` **11 条**（1 表 + 10 列）、
`daily-ticket-refund-notify-migration.sql` **4 条**（与 schema 里那 4 条**逐字重复**，改一处 MUST 同步另一处）、
`daily-ticket-refund-page-migration.sql` **1 条**（`PLATFORM_REFUND_NO`）。带取值域 / 单位 / 语义约束的如下：

| 列 | 注释里的取值域 / 约束 | 位置 |
|---|---|---|
| `DAILY_TICKET_ORDER.ORDER_TYPE` | 日票固定为 `1`（旅游票子单与独立日票同值，**因此不能拿它区分**） | 〔ddl〕:33 |
| `DAILY_TICKET_ORDER.CARD_TYPE` | 「APP侧卡类型，日票生码时码体票种仍固定为0441」 | 〔ddl〕:37 |
| `DAILY_TICKET_ORDER.CHANNEL_TYPE` | `1` 转 app、`2` 转 wap | 〔ddl〕:43 |
| `DAILY_TICKET_ORDER.ORDER_STATUS` | `CREATED`/`PAYING`/`PAID`/`CANCELED`/`REFUNDING`/`REFUNDED` 等 | 〔ddl〕:45 |
| `DAILY_TICKET_ORDER.PAY_STATUS` | `INIT`/`PAYING`/`PAID`/`FAIL` 等 | 〔ddl〕:46 |
| `DAILY_TICKET_ORDER.PARENT_ORDER_NO` | 子单指向 `TRAVEL_TICKET_ORDER.ORDER_NO`，**独立日票为空** | 〔ddl〕:185 |
| `TICKET_PRICE` / `PAY_AMOUNT` | 单位**分** | 〔ddl〕:40~41 |
| `DAILY_TICKET_INSTANCE.CODE_TICKET_TYPE` | 「码体车票类型，日票固定为0441」（默认值同为 `0441`，:60） | 〔ddl〕:93 |
| `DAILY_TICKET_INSTANCE.ACTUAL_TIMES` | 「实际可用次数，**-99 表示不限次**」 | 〔ddl〕:99 |
| `DAILY_TICKET_INSTANCE.TICKET_STATUS` | `INIT`/`ACTIVATED`/`USED`/`EXPIRED`/`REFUND_LOCKED`/`REFUNDED` 等 | 〔ddl〕:104 |
| `COUNTING_START` / `COUNTING_END` | 「计次/计时开始（结束）时间，**毫秒时间戳**」 | 〔ddl〕:105~106 |
| `ACC_NOTICE_STATUS` | `INIT`/`SUCCESS`/`FAIL` 等 | 〔ddl〕:109 |
| `TRANS_AMOUNT` / `DISCOUNT_AMOUNT` | 单位**分** | 〔ddl〕:101~102 |
| `DAILY_TICKET_PAY_LOG.BIZ_TYPE` | `PAY`/`QUERY`/`REFUND`/`CALLBACK` | 〔ddl〕:131 |
| `DAILY_TICKET_REFUND.REFUND_STATUS` | `REFUNDING`/`REFUNDED`/`FAILED`/`WAIT_VERIFY` 等 | 〔ddl〕:170 |
| `DAILY_TICKET_REFUND.REFUND_TYPE` | `00` 直接退款、`01` 激活后核验退款 | 〔ddl〕:171 |
| `DAILY_TICKET_REFUND.VERIFY_AFTER_TIME` | 「激活后退款核验时间，**通常为申请后 5 天**」 | 〔ddl〕:173 |
| `DAILY_TICKET_REFUND.NOTIFY_STATUS` | `PENDING` 待发 / `SUCCESS` 已受理 / `GIVEUP` 放弃重投，**空表示无需通知** | 〔ddl〕:176 |
| `DAILY_TICKET_REFUND.NOTIFY_TIMES` | 达到 `daily-ticket.notify.app.max-notify-times` 即置 `GIVEUP` | 〔ddl〕:177 |
| `TRAVEL_TICKET_ORDER.ORDER_NO` | 「旅游票单号，**前缀 0T**」 | 〔ddl〕:209 |
| `TRAVEL_TICKET_ORDER.TOTAL_AMOUNT` | 「单位分，**服务端按 TICKET_PRICE 乘 TICKET_COUNT 重算**」 | 〔ddl〕:215 |
| `TRAVEL_TICKET_ORDER.ORDER_STATUS` | `CREATED`/`PAYING`/`PAID`/`PAY_FAILED`/`CANCELED`/`REFUNDING`/`REFUNDED` | 〔ddl〕:217 |
| `TRAVEL_TICKET_ORDER.PAY_STATUS` | `INIT`/`PAYING`/`PAID`/`FAIL`（**主单承载聚合支付，实测已回写 `PAYING`/`PAID`**；此处此前写「实际永停 `INIT`」已过期，见 §三） | 〔ddl〕:218 |
| `DAILY_TICKET_USAGE_LOG.ID` | 主键取 `SEQ_DAILY_TICKET_USAGE_LOG.NEXTVAL` | usage:21 |
| `DAILY_TICKET_USAGE_LOG.ORDER_NO` | 关联 `GATE_TXN_PAY.ORDER_NO`，**唯一索引做幂等** | usage:23 |
| `DAILY_TICKET_USAGE_LOG.TXN_DATE` | `YYYYMMDD` | usage:24 |
| `TIMES_BEFORE` / `TIMES_AFTER` / `TICKET_STATUS` | 扣前 / 扣后剩余次数、扣后票状态 | usage:27~29 |

`daily-ticket-refund-page-migration.sql` 头 3 行是**文件级行注释**（用途、幂等提示、执行前确认
`DAILY_TICKET_REFUND.ORDER_NO` 无重复），属「一次性执行说明」、**保留**。

### 七、可观测性（1.0.22 起已开 tracing）

- 〔prop〕:11~25 是与 card-pool 同源的**三行成组**配置（`management.tracing.enabled=true` +
  `sampling.probability=0` + `spring.autoconfigure.exclude=...OtlpAutoConfiguration`），三条理由逐字同
  card-pool（见 `docs/business/card-pool.md` §八），**样板在 `card-pool-server/src/main/resources/application.properties:9~24`**。
- 打开的直接动机：对账链路是 **web-admin → recon-server → 本模块**，`recon-server` 下发
  `/internal/recon/export` 时带 W3C `traceparent`，不开这三行则 `%X{traceId}` 恒空、
  **按 `sys_job_log`（job 225「给ACC上传扣费交易」，2026-09-21 由 109「日终对账」改号改名）的 traceId 检索本模块抽取日志会 0 条**、链路断在这一环。
- 本模块**没有自带 log4j2 配置、走公共 `log4j2-linux.xml`**，其 pattern 已含 `%X{traceId}`，只差这个开关。
- 连带：本模块已开 tracing ⇒ 幂等兜底 catch **MUST 沿 cause 链判定**（见 §二 扣次明细那条）。

### 八、逐文件覆盖清单（本阶段口径）

| 文件 | 注释行 | 本阶段处置 | 迁入位置 |
|---|---|---|---|
| 〔impl〕 | 173 | 状态机表、事故史、三闸口、行内 MUST/NEVER 全删，留一句式 Javadoc | §一~§四 |
| —— 上一行是**拆分前**（2455 行 god class）的口径；1.0.51~1.0.56 拆分后这些注释已分散到〔lifecycle〕〔order〕〔pay〕〔refundInit〕〔sync〕〔status〕等类，门面只剩一句 Javadoc | —— | 仅作历史记录，NEVER 据它去门面里找注释 | —— |
| 〔svc〕 | 112 | `resubmit` vs `retry` 分工、查不到返 `0000` 等叙述删除 | §二、§四 |
| 〔reconSvc〕 | 161 | 段序、票种降级、平台线程池、Keyset 等叙述删除 | §五 |
| 〔reconMapper〕 | 126 | 类注释两张段序表、索引论证、口径清单删除 | §五 |
| 〔reconXml〕 | 82 | DETAIL/PAY 两大段说明删除；**保留**「SQL 正文禁写注释」一行 | §五 |
| 〔notifySvc〕 | 49 | 落库+扫表形态、NEVER 加 `@Scheduled` 等删除 | §四 |
| 〔notifyClient〕/〔notifyResult〕 | 33 / 16 | 三条约定、成功码口径、两态语义删除 | §四 |
| 〔notifyProp〕/〔payProp〕 | 48 / 55 | 两条硬性约定、`notifyUrl` 必填说明删除；字段级 Javadoc 保留 | §四 |
| 〔ctl〕/〔refundCtl〕/〔reconCtl〕/〔notifyCtl〕 | 62/23/23/22 | 鉴权降级、受理语义、行内说明删除，留接口编号一行 | §四、§五 |
| 〔instMapper〕/〔instXml〕/〔refundMapper〕/〔refundXml〕 | 18/13/16/9 | 事故史与 SQL 设计理由删除；**保留** XML 内「SQL 正文禁写注释」 | §二、§四 |
| 〔boot〕 | 14 | NEVER 加 `@EnableRpcCardPool` 那段删除 | §二 |
| 〔prop〕 | 37 | **tracing 三行说明整段保留**（口径同 card-pool）；其余长说明删除 | §四、§七 |
| 实体 5 个 + page 4 个 | 230 | 字段级一句式 Javadoc **全部保留** | §六 |
| SQL 5 个文件 | 106 条 `COMMENT ON` | **DDL 语句，不删** | §六 |

### 矛盾与待裁决

1. **「查不到返 `0000`」与「查不到返失败」并存**：`queryDailyTicketPayInfo` MUST 返 `0000` + 三字段留空
   （〔svc〕`queryDailyTicketPayInfo`、〔pay〕`queryDailyTicketPayInfo`，调用方是详情主链路），而 `validateEntryCheck` 无实例时**返失败**
   但允许闸机走常规流程（〔lifecycle〕`validateEntryCheck`）。**两者语义确实不同、不是矛盾**，但并列易被误判，**NEVER 互相套用**。
2. **`CODE_TICKET_TYPE` 列名与实际值不符**：列名说「码体车票类型」、DDL 默认与代码都写 `0441`（二维码后付费单程票），
   而多日计次票本应 `0448`（〔reconXml〕:37）。**待甲方确认票种码值口径**；在此之前对账无法按票种收窄（§五）。
3. **`DAILY_TICKET_ORDER.CARD_TYPE` 混着三套编码空间**（APP 口径含聚合桶 `05` / ACC 两位 / 发卡四位），
   入口不做白名单校验。**待裁决：是否改成落规范化后的发卡卡类型**（那是对账收窄的另一条前提）。
4. **`WAIT_VERIFY` 在本模块内只被写入、无任何读取方**（〔svc〕:75~77）：核销观察期满后没有自动驱动方，
   唯一出口是人工点 `/resubmit`。**待业务确认是否要补一条 web-admin 定时任务。**
5. **`resubmit` / `retry` / 对账 / 通知四类内部端点当前全部无鉴权**（〔refundCtl〕:86、〔reconCtl〕:16、〔notifyCtl〕:20），
   与 AGENTS.md §5.2 冲突，属测试期临时降级，**上线前 MUST 恢复**。
6. **旅游票张数上限 20 是暂定值**（〔order〕`MAX_TRAVEL_TICKET_COUNT`），**待业务确认**。
7. **`daily-ticket-refund-notify-migration.sql` 的 4 条 `COMMENT ON` 与 schema 里逐字重复**：
   两处都改才不会漂移，**当前无机制保证**。
8. **本模块 0 个单测**：§一~§五 的结论多来自线上事故与实测，**没有任何断言锁住**；墓碑清单里标「可断言」的几条
   建议优先补测（本阶段未补，属未闭合项）。
### 墓碑清单（本阶段从代码删除、只在文档留证）

| # | 原位置 | 墓碑内容（短语可 grep 本文档） | 为什么不能重犯 | 可否断言 |
|---|---|---|---|---|
| 1 | 〔status〕类注释 / 〔instXml〕:151~154 | 「NEVER 写成 `TICKET_STATUS != 'USED'`」（2026-09-10 事故） | 一日票刷一次就再也进不了站 | 可断言：进站白名单含 `USED` |
| 2 | 〔lifecycle〕`validateEntryCheck` | 「`-99` 被判『计次票次数已用完』」（2026-09-10 事故） | 不限次票整类进不了站 | 可断言：`ACTUAL_TIMES=-99` 放行 |
| 3 | 〔lifecycle〕`updateTicket` / 〔boot〕:13~19 | 「NEVER 在激活时再向卡池预占，`UK_LOGIC_CARD_POOL_BUSINESS` 必然给出另一个卡号」 | `CARD_NUM` 精确匹配恒 0 行、进站被拒 | 可断言：源码扫描无 `@EnableRpcCardPool` |
| 4 | 〔order〕`requestTravelOrder` | 「落库顺序 MUST 先子单后主单」 | 反序留下「可支付但缺张」的主单 | 可断言：顺序断言 |
| 5 | 〔reconMapper〕:30~45 / 〔reconSvc〕:165~166 | 「改按子单统计后恒返 0 行（子单恒 `CREATED`/`INIT`、`PAY_DATE` 恒空），PAY MUST 按**主单** `TRAVEL_TICKET_ORDER`」 | PAY 第 10/11 段整段丢账 | 可断言：SQL 的 `FROM` 是 `TRAVEL_TICKET_ORDER`、不含 `PARENT_ORDER_NO IS NOT NULL` |
| 6 | 〔reconMapper〕:30~45 | 「张数 NEVER 用 `COUNT(*)`，MUST `SUM(NVL(TICKET_COUNT,0))`」（主单一张含 N 票） | `COUNT(*)` 数的是**订单数**、静默少账 | 可断言：SQL 含 `SUM(NVL(T.TICKET_COUNT, 0))` |
| 7 | 〔reconSvc〕:177~189 / 〔reconXml〕:31~46 | 「三个候选票种判据逐一核对都不可靠，NEVER 凭猜写码值」 | 猜错即静默 0 行或漏整类票 | 弱断言：SQL 无票种谓词 |
| 8 | 〔reconSvc〕:191~199 | 「NEVER 凭推测加 `AND O.PARENT_ORDER_NO IS NULL`」 | 整类已售票在 ACC 侧凭空消失 | 可断言：SQL 不含该谓词 |
| 9 | 〔reconMapper〕:54~59 | 「NEVER 改成大页码 `OFFSET`」 | 单页耗时随页码线性上涨、慢 SQL pin 载体线程 | 弱断言：SQL 含 Keyset 谓词 |
| 10 | 〔reconXml〕:83~84 | 「用 LEFT 而非 INNER，改 INNER 会静默少账」 | 已付未激活单从发售明细消失 | 可断言：SQL 含 `LEFT JOIN` |
| 11 | 〔refundInit〕`requestRefundTicket` | 「出网异常 NEVER 返成功也 NEVER 返失败；先 `insertPayLog` 再置 `REFUNDING`」 | 反序时连「发过退款」的痕迹都不剩 | 可断言：调用顺序 |
| 12 | 〔refundCb〕`receiveRefundResult` | 「NEVER 把 `outRefundNo` 放进 `refundOrderNo` 键」 | `PLATFORM_REFUND_NO` 写成我方单号、退款查询永久对不上 | 可断言 |
| 13 | 〔svc〕:69~73 / 〔refundCtl〕:81~84 | 「对端从未受理时 `refundQuery` 永远查不到，退款能力永久丧失」 | `/retry` 与 `/resubmit` 混用即卡死 | 可断言：`PLATFORM_REFUND_NO IS NULL` 分支 |
| 14 | 〔refundXml〕:95~98 | 「四列一起写，NEVER 拆成多条语句」 | 「次数没加但原因写了」⇒ 永久重投 | 可断言：mapper XML 单语句 |
| 15 | 〔refundXml〕:109~112 | 「不包 `NVL(NOTIFY_TIMES,0)` 时历史 NULL 行永远捞不出来」 | 老退款单的通知永不补发 | 可断言：SQL 含 `NVL` |
| 16 | 〔refundSettle〕`markRefundNotifyPending` | 「次数一并重置为 0，NEVER 复用上一轮计数」 | 上一轮攒到 4 次的单子这轮只剩 1 次机会 | 可断言 |
| 17 | 〔notifyClient〕:30~36 | 「裸 JSON 实测返 `7004`；NEVER 把 `7004` 加进成功码」（ADR-D89） | 把「对端没受理」永久记成投递完成 | 可断言：成功码集合 |
| 18 | 〔refundGwReq〕 / 〔payProp〕:56~59 | 「`notifyUrl` 必填，缺它对端不回调且日志一片绿」 | 退款单只能靠人工收口、无任何报错 | 可断言：报文含该键 |
| 19 | 〔prop〕:53~56 | 「`subject`/`body` MUST 写 `\uXXXX`，NEVER 直接写中文」 | ISO-8859-1 读取 ⇒ mojibake 原样送支付中心 | 可断言：properties 无非 ASCII |
| 20 | 〔prop〕:19~25 / 〔reconXml〕:9~11 | tracing 三行成组理由、Druid `commentAllow=false` 说明 | 只加第一行 ⇒ OTLP exporter 被 env 激活；SQL 带注释 ⇒ 语句静默失效 | 可断言：配置三行齐全 |
| 21 | 〔lifecycle〕`insertUsageLog` / `isIntegrityViolation` | 「已开 tracing，`DuplicateKeyException` 可能被切面包一层」（ADR-D53） | 按类型 catch 的幂等兜底全部落空 | 可断言：cause 链判定 |
| 22 | 〔pay〕`updatePayTerminalIfPaying`（英文注释） | 「重复成功回调可能带来首次终态更新时缺失的平台单号，留着给退款用」 | 丢单号后退款查询无法定位 | 可断言 |
| 23 | 〔instMapper〕:21~25 / 〔instXml〕:54~57 | 「12 行 / 12 个不重复但库里没有唯一索引，NEVER 去掉 `FETCH FIRST 1 ROWS ONLY`」 | 重复票号 ⇒ `TooManyResultsException` 打挂 IF8A-34 | 可断言：SQL 含该子句 |
| 24 | 〔reconSvc〕:370~371 | 「NEVER 静默把游标置回 null」 | Keyset 从头再翻、死循环并重复上送分片 | 可断言 |
| 25 | 〔reconSvc〕:269~275 | 「线路段由 recon-server 无条件覆盖，本模块 NEVER 自己 join `STATION_INFO`」 | 两处各算一次、口径漂移 | 弱断言 |

### 覆盖率自评

- **注释侧覆盖**：`src/main` 1332 行中，「叙述 / MUST-NEVER / 事故史 / 墓碑」类**已 100% 落进本节或阶段一**
  （逐文件核对见 §八）。其余约 560 行是实体 / page / config 的字段级一句式 Javadoc 与 `@param` 段，
  **属标准 Javadoc、不在删除范围**。
- **任务点名的 8 项**：状态机与取值 §一 ✅；激活与出站扣次 §二 ✅；106 条列注释取值域 §六 ✅；
  旅游票**按主单聚合支付 + PAY 按主单统计**（子单恒 `CREATED`/`INIT` 属设计）§三 / §五 ✅；
  `IDX_DAILY_TICKET_ORDER_RECON` 与对账窗口 §五 ✅；退款相关表 §四 / §六 ✅；已开 tracing（1.0.22）§七 ✅。
- **「退款相关表与分区」中的「分区」据实说明**：本模块 5 张表 + `DAILY_TICKET_USAGE_LOG` **都不是分区表**，
  `daily-ticket-server-schema.sql` 与三个 migration 里**没有任何 `PARTITION` 子句**（月分区表是
  `gate-txn-pay-server` 的形态，见 `docs/business/gate-txn-pay.md`）。因此本节只覆盖「退款相关表」：
  `DAILY_TICKET_REFUND`（含 4 列通知 outbox）、`DAILY_TICKET_PAY_LOG`（退款交互留证）、
  `DAILY_TICKET_ORDER`（`REFUNDING` / `REFUNDED` 回写）。**NEVER 据任务措辞推断本模块有分区表。**
- **未覆盖 / 降级说明**：①本模块 0 单测，所有结论无断言锁定（矛盾 8）；②票种收窄、`CARD_TYPE` 编码归一、
  `WAIT_VERIFY` 驱动方、张数上限四项待业务/甲方裁决（矛盾 2~4、6）；③四类内部端点无鉴权（矛盾 5）；
  ④`AGENTS.md` §5.1 的 `where 1 = 1` 与「XML 注释连续减号」两条规则在本模块注释里无对应文字，
  代码事实上已避开（`DailyTicketOrderMapper.xml:92`、`DailyTicketRefundMapper.xml:49` 用 `<where>`）。
- **代码侧保留的一行式护栏（删除阶段后逐条复核过）**：
  1. 〔prop〕tracing「三行成组、NEVER 只加第一行」整段 —— 与 card-pool 同源，删掉会导致别的模块照抄错；
  2. 7 个 mapper XML 中 `ReconExportMapper.xml` / `DailyTicketInstanceMapper.xml` 的「SQL 正文禁写注释」一行；
  3. `daily-ticket-refund-page-migration.sql` 头 3 行执行前提说明（一次性脚本的执行说明，不是叙述型注释）。










