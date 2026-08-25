---
业务域: APP 扫码取票（单程票）
模块: collect-ticket-server
---

# 提示词：APP 扫码取票

## 何时读本文件
APP 端单程票购买张数限制、票价试算、下单、请求支付、取票订单查询、TVM 取票通知、取消订单、支付回调相关改动。

## 模块定位
`collect-ticket-server`，端口 **9098**，`spring.application.name=collect-ticket`
启动类 `collect-ticket-server/.../CollectTicketServer.java`

⚠️ 两个部署风险，改动时 **MUST** 提醒用户：
1. 端口 **9098 与 account-server 冲突**。
2. `application.properties` **未配置 `other.sql.*` 数据源**，但模块内存在 MyBatis mapper XML —— 数据源依赖外部环境注入，仓库内无其他配置文件。本地跑通前 **MUST** 先确认数据源来源。
   该文件实际含有的键：`service.token.url`、`collect.ticket.*`、`other.web.*`、`knife4j.production`、`service.route.mapping.default`，以及写死个人目录的 `server.tomcat.basedir` 与 `logging.file.path`（`/Users/zhoucong/logs`，容器内不可用，另见 `../ops/生产环境清单.md` §六 P1）。

## 接口清单
唯一 controller：`collect-ticket-server/.../controller/ci/app/TicketCollectController.java`（前缀 `/ci/app`）
- IF8A-09 `requestBuySinlgeTicketMaxNum`
- IF8A-10 `requestTicketPriceByStation`
- IF8A-11 `requestPaymentInfo`
- IF8A-20 `requestOrder`
- IF2A-02 `queryTicketCollectOrder`
- IF2A-03 `ticketCollectNotify`
- IF2A-05 `cancelTicketCollectOrder`
- `ticketCollectPayNotify`（支付结果回调，对应 collect-pay 的 `pay.center.callback-url`）

⚠️ IF8A-09/10 同时被 `para-server` 与 `fep-app-server/AppParaController` 实现，IF8A-11/20 同时被 `collect-pay-server/TvmAppOrderController` 实现。定位实现 **MUST** 确认调用方实际路由到哪个服务，**NEVER** 假设编号唯一对应一处代码。

## 核心类与流程
- `service/impl/TicketCollectServiceImpl.java`（下单→支付→取票主流程集中在此）
- `client/CollectPayClient.java`（调支付）
- `util/PaySignUtils.java`（**SHA256WithRSA** 签名，本域唯一符合 AGENTS.md 签名描述的地方）

流程：创建 `Ticket_Collect_Info`（未支付）+ `Ticket_Collect_Logs` → 请求支付 → 支付回调置支付结果 → 取票通知更新 `collectStatus` → 取消置 99 → 回执 `SUCCESS` / `FAIL`

## 状态取值（常量在 `TicketCollectServiceImpl` 顶部，无枚举）
- 订单：`ORDER_STATUS_UNPAID=0`、`ORDER_STATUS_PAID=100`、取消置 `99`
- 取票：`COLLECT_STATUS_NOT_COLLECTED=0`、`COLLECT_STATUS_SUCCESS=100`
- 支付结果：`PAY_RESULT_UNPAID=0`、`PAY_RESULT_PROCESSING=1`、`PAY_RESULT_SUCCESS=100`
- 二维码有效期 `QRCODE_EXPIRE_SECONDS=300`，过期错误码 `2101`
- 错误码枚举：`constant/CollectTicketErrorCodeEnum.java`

## 幂等（本仓最薄的一处，改动重点）
无 Redis 锁、无防重表、无可确认的唯一索引，仅靠"先查 `orderStatus`/`collectStatus` 再更新"+ 订单号唯一性。
在此模块新增支付/取票写入路径 **MUST**：
1. 先查当前状态并对终态短路返回；
2. 提示用户为关键表补唯一索引；
3. **NEVER** 引入 Redis 锁作为替代（与全项目约定不符）。

## 数据表
`Ticket_Collect_Info`、`Ticket_Collect_Logs`、`Ticket_Collect_Log_Detail`（mapper 在 `collect-ticket-server/src/main/resources/mapper/`）

## 参考原始文档
- `docs/接口规范文档/ITP与APP接口规范R6_接口清单.md`（IF8A-09/10/11/20）
- `docs/技术规范文档/城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.docx`（IF2A-02/03/05）
