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
`DAILY_TICKET_ORDER`、`DAILY_TICKET_INSTANCE`、`DAILY_TICKET_PAY_LOG`、`DAILY_TICKET_REFUND`
DDL：`daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql`

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
- **APP 转发**：fep-app-server `AppDailyTicketController`，另有 `/app/requestCountingOrder` 旧别名需保留

## 幂等
依赖订单状态终态短路 + 表主键。无 Redis 锁、无 MQ。新增写路径 **MUST** 补状态判断。

## 参考原始文档
- `docs/业务需求文档/虚拟电子多日计次票接口清单.md`
- `docs/业务需求文档/青岛地铁虚拟电子多日计次票、离线码、实时客流上传功能方案V1.docx`
- `docs/接口规范文档/青岛地铁日票-ITP与ACC交互文档.docx`（ACC 通知部分）
