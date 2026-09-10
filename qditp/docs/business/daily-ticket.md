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
- IF8A-65 `/ticket/cancelOrder`
- IF8A-67 `/ticket/updateTicket`
- IF8A-71 `/ticket/updateAndNotice`
- 无编号内部接口：`/payment/receivePayResult`（支付回调）、`/queryDailyTicketInfo`、`/entry/check`（进站校验）、`/ticket/markUsed`（出站扣次，由 ticket-server `GateTicketHandler` 调用）

`controller/DailyTicketRefundController.java`（前缀 `/page/daily-ticket/refund`，运营页面，强制 `orderType=1`）
- `GET /orders`、`POST /request`、`/pay-query`、`/query`、`/retry`、`GET /records`

> 注意：日票的运营端后台接口在**本模块**，不在 web-server。

## 核心流程
下单 `CREATED` → `requestPay` 经 `client/DailyTicketPayGatewayClient.java`（银商网关，**RSA2** 签名，配置 `daily-ticket.pay.*`）
→ 回调或主动查询置 `PAID` → 激活生成票实例 → 进站校验 / 出站扣次
→ 退款：未激活直退 `refundType=00`；已激活需核验退 `refundType=01`

核心类：`service/DailyTicketService.java`、`service/impl/DailyTicketServiceImpl.java`

⚠️ **安全**：`daily-ticket-server` 配置中含商户私钥明文。触碰配置或签名逻辑 **MUST** 提示人工复核，**NEVER** 输出私钥值。

## 数据表
`DAILY_TICKET_ORDER`、`DAILY_TICKET_INSTANCE`、`DAILY_TICKET_PAY_LOG`、`DAILY_TICKET_REFUND`、`TRAVEL_TICKET_ORDER`
DDL：`daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql`

### 旅游票（IF8A-70）落库形态
- 主单 `TRAVEL_TICKET_ORDER`（单号前缀 `0T`）只存聚合信息与支付状态；每张日票是一条 `DAILY_TICKET_ORDER`（单号前缀 `0E`、`ORDER_TYPE='1'`、`PARENT_ORDER_NO` 指向主单）。
- **拆子单是被唯一索引逼出来的**：`UK_DAILY_TICKET_INSTANCE_ORDER ON DAILY_TICKET_INSTANCE(ORDER_NO)` 限定一个订单号只能挂一张票实例，一单挂多票在现有表上无法表达。
- 落库顺序**先子单后主单**：中途失败只留孤儿子单，APP 拿不到 `orderNo` 因而无法支付；反序会留下「可支付但子单缺张」的主单。本模块全局无 `@Transactional`，一致性靠顺序而非回滚。
- `totalAmount` 只做校验、不采信：服务端按 `ticketPrice * ticketCount` 重算，不一致直接拒单；`ticketCount` 上限 `MAX_TRAVEL_TICKET_COUNT=20`（待业务确认）。
- **支付 / 退款 / 激活链路尚未适配聚合单**（本轮只做下单落库）：`requestPay` 等接口的 `validateOrderNo` 强制 `orderType=1`，只认日票子单号，传主单号会被拒。聚合支付方案待定。

## 状态取值（**无枚举类，全是 `DailyTicketServiceImpl` 中的字符串字面量**）
- `ORDER_STATUS`：`CREATED` / `PAYING` / `PAID` / `PAY_FAILED` / `CANCELED` / `REFUNDING` / `REFUNDED`
- `PAY_STATUS`：`INIT` / `PAYING` / `PAID` / `FAIL`
- `TICKET_STATUS`：`ACTIVATED` / `USED`
- `ACC_NOTICE_STATUS`：`INIT` / `SUCCESS`
- `REFUND_STATUS`：`REFUNDING` / `WAIT_VERIFY` / `REFUNDED` / `FAILED`

唯一复用的枚举是 `model` 模块的 `CardTypeCodeEnum.QR_POSTPAID`。

改动状态 **MUST** 全局 grep 字面量确认所有比较点；**NEVER** 只改一处赋值。

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

## 参考原始文档
- `docs/业务需求文档/青岛地铁虚拟电子多日计次票、离线码、实时客流上传功能方案V1.docx`
- `docs/接口规范文档/青岛地铁日票-ITP与ACC交互文档.docx`（ACC 通知部分）
