# 01 · TVM（ITP_Payment_012~014）

模块：`collect-pay-server`（58101，`spring.application.name=itpagm`）。设备侧入口前缀 `/itptvm/ci/tvm`（`TvmOrderController.java:30-31`）。

## TVM 侧真实接口清单

| URL（全路径） | 行号 | 注释编号 |
|---|---|---|
| `/itptvm/ci/tvm/notiDeviceHeard` | :51 | 无（心跳） |
| `/itptvm/ci/tvm/requestGenSjtOrder` | :64 | IF2A-01 |
| `/itptvm/ci/tvm/requestPayment` | :117 | IF2A-11 |
| `/itptvm/ci/tvm/requestPayResult` | :143 | IF2A-03 |
| `/itptvm/ci/tvm/notiTakeTicketResult` | :160 | IF2A-04 |
| `/itptvm/ci/tvm/notiTakeTicketFailResult` | :179 | IF2A-05 |
| `/itptvm/ci/tvm/requestRefund` | :201 | 无（注释写「退款接口。自己用」） |
| `/itptvm/ci/tvm/requestActiveTicket` | :226 | IF8A-15 |
| `/itptvm/ci/tvm/requestTakeTicketAuth` | :242 | IF2A-08 |
| `/itptvm/ci/tvm/requestTopup` | :260 | IF2A-09 |
| `/itptvm/ci/tvm/topupCardResultNoti` | :277 | IF2A-06 |
| `/itptvm/ci/tvm/topupCardFailNoti` | :293 | IF2A-07 |
| `/itptvm/ci/tvm/requestPayOrderDetail` | :308 | 无（支付中心查 ITP 订单详情） |
| `/itptvm/ci/tvm/payNotice` | :320 | 无（支付中心支付结果回调） |

APP 侧入口 `/ci/app`（`TvmAppOrderController.java:24-26`）：`requestOrder`（:41，IF8A-20）、`requestPaymentInfo`（:98，IF8A-11）、`requestPayResult`（:127，IF8A-18）、`requestPreActiveOrderList`（:153）、`requestRefundTicket`（:178）、`requestRefundTicketResult`（:198）、`receiveRefundResult`（:222）。

> ⚠️ `requestPaymentInfo` 的 javadoc（`:90`）把接口地址写成 `/ci/app/requestPayInfo`，与实际 `@PostMapping` 不一致。按注释配 Apifox 会调不通。

二维码生成的两条路径、`scene` 取值、`payType` 影响见 [[00-链路事实与配置字典]]。

## ITP_Payment_012 TVM 扫码购票

链路：`requestGenSjtOrder`（IF2A-01）→ `requestPayment`（IF2A-11，取 `payUrl`）→ `requestPayResult`（IF2A-03）或支付中心回调 `payNotice` → 出票后 `notiTakeTicketResult`（IF2A-04）/ 失败 `notiTakeTicketFailResult`（IF2A-05）。

**出票失败自动退款已实现**：`TvmOrderServiceImpl.notiTakeTicketFailResult`（`:570`）先落 `TvmMainTicket` / `TvmSubTicket` 故障记录，按 `refundNum = buyNum - actualNum`、乘 `ticketPrice` 算退款金额（`:636-637`），扫码取票业务走 `appOrderService.doRefund(...)`（`:653`），其他业务走 `handleRefund(...)`（`:656`、`:687`）。

预期（可验证）：

- 拉码：响应 `URL` 字段非空。若 `payType="0"`，该值是本地拼的收银台链接（不产生支付中心订单）；否则是支付中心返回的 `payUrl`。**两种情况的验证点不同，先确认设备传的 `payType`**
- 支付成功：`requestPayResult` 返回成功，或 `payNotice` 回调落库
- 正常出票：`notiTakeTicketResult` 后订单进终态，出票数 = 购买数
- **部分出票**：`actualNum < buyNum` 时按差额退款——这是本用例最值得测的分支，请专门造一次部分出票，核对 `refundNum` 与退款金额是否等于 `(buyNum - actualNum) × ticketPrice`
- 全部出票失败：`actualNum = 0`，全额退款

结果：☐ 通过 ☐ 失败 ☐ 阻塞

## ITP_Payment_013 TVM 扫码充值

链路：`requestTopup`（IF2A-09）→ 支付 → `topupCardResultNoti`（IF2A-06）成功 / `topupCardFailNoti`（IF2A-07）失败。
二维码生成：`TvmTopupServiceImpl.java:107-112`（本地拼）或 `:130-136`（调支付中心）。

预期（可验证）：

- 拉码与支付同 012
- 充值成功：`topupCardResultNoti` 后订单终态正确
- 充值失败：`topupCardFailNoti` 触发退款

⚠️ **「票卡余额正确增加」ITP 侧验不了**。余额在票卡（一卡通）上，由 TVM 写卡完成，ITP 只收结果通知。ITP 侧能验的是「通知已落库、金额一致、失败已退款」。余额本身要在设备侧或一卡通系统核对。

结果：☐ 通过 ☐ 失败 ☐ 阻塞

## ITP_Payment_014 TVM 扫码取票

⚠️ **执行前必须先定一件事：这条业务在代码里有两套实现，走哪套要现场确认。**

**实现 A：collect-pay-server**（表 `TVM_APP_ORDER`）
APP 下单 `POST /ci/app/requestOrder`（IF8A-20）→ `requestPaymentInfo`（IF8A-11）→ `requestPayResult`（IF8A-18）；TVM 侧 `requestActiveTicket`（IF8A-15）校验 `TvmAppOrder.payStatus` 为成功后把 `activateFlag` 置 `ACTIVATE_ED`（`TvmTakeTicketServiceImpl.java:51`），`requestTakeTicketAuth`（IF2A-08）按 `deviceId + qrcodeGenDate + randomFact` 查订单（`:100`、`:105`），**未激活返回空数据**（`:125`）。出票结果走 `notiTakeTicketResult` / `notiTakeTicketFailResult`，失败退款如 012 所述。

**实现 B：collect-ticket-server**（9098，表 `Ticket_Collect_Info` / `Ticket_Collect_Logs` / `Ticket_Collect_Log_Detail`）
`TicketCollectController.java` 类级 `/ci/app`（`:31`）：`requestBuySinlgeTicketMaxNum`（:41，IF8A-09，方法名拼写就是 `Sinlge`）、`requestTicketPriceByStation`（:50，IF8A-10）、`requestPaymentInfo`（:60，IF8A-11）、`requestOrder`（:69，IF8A-20）、`queryTicketCollectOrder`（:78，IF2A-02）、`ticketCollectNotify`（:87，IF2A-03）、`cancelTicketCollectOrder`（:96，IF2A-05）、`ticketCollectPayNotify`（:105）。

两套实现的 `/ci/app/requestOrder` 与 `/ci/app/requestPaymentInfo` **URL 完全相同**，只是模块不同（58101 vs 9098）。按「模块 + URL」定位，不能只看 URL。

⚠️ **实现 B 没有任何退款代码**：`ticketCollectNotify`（`:288-345`）收到 TVM 的 `collectStatus` / `errorCode` / `faultSlipSeq` 后只更新订单与明细，不发起退款。模块自带的 `CollectPayClient.requestRefund`（`:58`）**全模块无调用点**。所以原用例「出票失败则自动退款」在实现 B 上不成立，只在实现 A 上成立。

预期（按实现 A 写，实现 B 需去掉退款项）：

- APP 下单支付成功：订单 `payStatus` 成功
- TVM 扫码：`requestActiveTicket` 成功后 `activateFlag=ACTIVATE_ED`；**未激活就扫 `requestTakeTicketAuth` 应返回空数据**（这是个值得专门测的负向点）
- 正常出票、出票失败退款同 012
- 若实际走实现 B：二维码 5 分钟过期（`QRCODE_EXPIRE_SECONDS=300`），过期错误码 `2101`；取消订单后 `orderStatus` 回到 `0`（**不是独立的已取消态**，取消与未支付在库里无法区分）

结果：☐ 通过 ☐ 失败 ☐ 阻塞（先确认走哪套实现）

## 矛盾/待验证

- ⚠️ 扫码取票两套实现并存，同 URL 不同模块，需确认生产实际路由到哪一个；若两套都在跑，需确认是否会重复下单
- ⚠️ 实现 B 无退款，与原用例预期冲突
- ⚠️ `requestPaymentInfo` javadoc 地址与实际不一致
- ⚠️ 「票卡余额增加」不在 ITP 可验范围

## 交叉引用

[[00-链路事实与配置字典]] · [[02-BOM]] · [[04-阻塞项与缺陷候选]]

## 参考文献

- `docs/business/tvm-bom-pay.md`
- `docs/business/app-ticket-collect.md`
