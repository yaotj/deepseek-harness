# 日票 / 多日计次票 测试知识库

建库日期 2026-09-09。行号绑定 `fep-app-server` 2.0.75 / `daily-ticket-server` 1.0.10。

用例编号 `DailyTicket_Test_0xx` 是**本知识库自拟**——甲方手册未提供日票用例编号，不要当成手册编号引用。

## 链路与模块

APP → fep-app-server (9101, 镜像 `itp/fep-app`) → daily-ticket-server (9108, 镜像 `itp/daily-ticket-server`) → 银商支付网关。

- 支付网关不经 pay-sign-server，daily-ticket-server 自己直连：`DailyTicketPayGatewayClient.java:73`，实测 URL `http://dtcustomer.bestonepay.com/ngpayment-gateway/api/v1/payment/requestPay`，RSA2 签名，配置前缀 `daily-ticket.pay.*`
- 激活时向 card-pool-server 预占逻辑卡号：`DailyTicketServiceImpl.java:484` 起
- 出站扣次由 ticket-server `GateTicketHandler` 调 `/ticket/markUsed`
- 集群 Service：`fep-app-hr32k-svc`（9101:30010）、daily-ticket NodePort 30027

## 接口清单（真实映射，以注解为准）

fep-app 侧 `AppDailyTicketController.java`，每个接口都有 `/ci/app/dailyTicket/**` 与 `/app/dailyTicket/**` 两条，**APP 实际调的是扁平别名**：

- `:39` if8a_60 下单 — 扁平别名 `/app/requestCountingOrder`
- `:53` if8a_61 支付 — 扁平别名 `/app/payment/requestPay`
- `:61` if8a_62 支付结果查询 — 扁平别名 `/app/payment/requestPayResult`
- `:100` 支付回调 — 扁平别名 `/app/payment/receivePayResult`（网关实际回调这条）
- `:68` if8a_64 退款、`:74` if8a_65 取消订单、`:80` if8a_67 激活、`:86` 通知 ACC 已使用 — **仅两条路径，无扁平别名**

daily-ticket-server 侧前缀 `/ci/daily-ticket`，路径见 `rpc/.../DailyTicketClient.java:46,60,74,88,102,116,130,144,158,172,188`。

> ⚠️ 扁平别名的三条支付路径是 2026-09-09 修复缺陷时补的。`/app/payment/requestPay` 此前被 `PaySignController` 占为 IF8A-19，`requestPayResult` 与 `receivePayResult` 此前未注册。详见 `03-阻塞项与缺陷候选.md` D-001。

## 数据表（生产库 4 张，已核实存在）

- `DAILY_TICKET_ORDER` — 订单主表，`ORDER_NO` 前缀 `0E`（`DailyTicketServiceImpl.java:1069` 生成，格式 `0E` + `yyyyMMddHHmmss` + 4 位序号）
- `DAILY_TICKET_PAY_LOG` — 网关报文留证，`BIZ_TYPE` 取 `PAY` / `CALLBACK` / `QUERY` / `REFUND` / `REFUND_QUERY` / `REFUND_RETRY`
- `DAILY_TICKET_INSTANCE` — 票实例，激活时才落库
- `DAILY_TICKET_REFUND` — 退款单

## 状态字典

见 `00-状态与字段字典.md`。

## 用例页

- [01-下单与支付](01-下单与支付.md) — DailyTicket_Test_001~008
- [02-激活与退款](02-激活与退款.md) — DailyTicket_Test_009~014
- [03-阻塞项与缺陷候选](03-阻塞项与缺陷候选.md)
- [04-真实链路时序基线](04-真实链路时序基线.md) — 2026-09-10 实测时序、cardId 来源链、排查速查
