# 02 · BOM（ITP_Payment_015~017）

模块：`collect-pay-server`，BOM 侧入口前缀 `/itpbom/ci/bom`（`BomOrderController.java:24-25`）。

## BOM 侧真实接口清单

| URL（全路径） | 行号 | 注释编号 |
|---|---|---|
| `/itpbom/ci/bom/notiDeviceHeard` | :44 | 无（心跳） |
| `/itpbom/ci/bom/requestGenNoCashOrder` | :57 | IF8A-04 |
| `/itpbom/ci/bom/requestPayment` | :114 | IF8A-05 |
| `/itpbom/ci/bom/requestGetPayResult` | :141 | IF8A-06 |
| `/itpbom/ci/bom/notiBusResult` | :165 | IF2A-08 |
| `/itpbom/ci/bom/notiTopupResult` | :192 | IF2A-09 |
| `/itpbom/ci/bom/requestCardDataAnalyse` | :219 | IF5A-01 |
| `/itpbom/ci/bom/requestUpdateCardData` | :246 | IF5A-03 |
| `/itpbom/ci/bom/requestOrderResult` | :276 | 无（单程票交易查询） |
| `/itpbom/ci/bom/requestTicketRefund` | :296 | **无任何 javadoc/编号** |
| `/itpbom/ci/bom/notiUpdateHceData` | :329 | IF5A-09 |
| `/itpbom/ci/bom/notiTakeTicketResult` | face-pay 1.0.42 新增 | IF2A-05（**别名**，转 TVM 那条的同一实现） |
| `/itpbom/ci/bom/notiTakeTicketFailResult` | face-pay 1.0.42 新增 | IF2A-06（**别名**，同上） |

> 最后两条**只在 `face-pay-server` 有、旧 collect-pay 没有**：现场设备把出票上报打到了 BOM 前缀，而该 URL 两侧原本都不存在，落静态资源解析后被全局异常处理器兜成 **HTTP 200 + UUID `retCode`**（**不是 404**），订单永久卡 `PAID`、`F2F_TICKET` / `F2F_RESULT_REPORT` 零行。判据是服务端日志里的 `No static resource <path>.`。别名的响应族是 TVM 的 **2xxx**（不是 BOM 的 8003），渠道兜底 `BOM`，幂等仍靠 `UK_F2F_REPORT_IDEM`。详见 ADR-D97。

> ⚠️ **接口编号冲突（定位实现只能靠「模块 + URL」）**
> `IF2A-08` 同时标在 BOM 的 `notiBusResult`（`:159`）与 TVM 的 `requestTakeTicketAuth`（`TvmOrderController.java:237`）；
> `IF2A-09` 同时标在 BOM 的 `notiTopupResult`（`:186`）与 TVM 的 `requestTopup`（`TvmOrderController.java:255`）。
> 这与 `AGENTS.md` §2.2.1 记录的 IF8A-04/05/06 双义是同一类问题。测试用例里写编号会指向错误实现，**MUST 写 URL**。

BOM 支付走 `scene="scan"`（主动扫用户付款码，`PayCenterCommon.java:95`、`authCode` 在 `:103`），与 TVM 的 `scene="qrcode"` 是相反方向：**BOM 是操作员扫乘客的付款码，不是乘客扫机器**。原用例 015/016 写「用户完成支付」「扫码支付」时要明确是哪一侧扫。

BOM 支付超时参数：`bom.payTimeOut` / `bom.payTimeInterval`（`application.yml:61-62`）——`requestGetPayResult` 的轮询节奏由此决定，造超时场景要看这两个值。

## ITP_Payment_015 BOM 非现金收款

链路：`requestGenNoCashOrder`（IF8A-04）→ `requestPayment`（IF8A-05，传 `authCode`）→ `requestGetPayResult`（IF8A-06）轮询 → 业务办理完成后 `notiBusResult`（IF2A-08）。

预期（可验证）：

- 下单：`BomNoCashOrder` 落库，业务类型字段取值正确
- 支付：`requestPayment` 传乘客付款码，支付中心返回结果；`requestGetPayResult` 能查到终态
- 业务结果回写：`notiBusResult` 后订单进终态

⚠️ **两项 ITP 侧验不了**：
- 「票卡状态正确更新」——写卡在 BOM 侧完成，ITP 只收结果通知
- 「打印业务凭证」——BOM 本地行为，ITP 无相关代码

结果：☐ 通过 ☐ 失败 ☐ 阻塞

## ITP_Payment_016 BOM 扫码充值

链路同 015，结果通知走 `notiTopupResult`（IF2A-09）。

预期：下单、支付、`notiTopupResult` 落库、金额一致。
⚠️ 「票卡余额正确增加」同 013，不在 ITP 可验范围。
⚠️ 「充值失败则自动退款」——BOM 侧未找到与 TVM `notiTakeTicketFailResult` 对应的失败自动退款实现。BOM 只有 `requestTicketRefund`（`:296`，且无 javadoc），需确认是**由 BOM 主动调**还是 ITP 自动触发。按代码现状，**没有找到 ITP 侧自动发起的证据**，请当作「需 BOM 主动发起」来测，并把实际行为回填。

结果：☐ 通过 ☐ 失败 ☐ 阻塞

## ITP_Payment_017 BOM 补票-超程

⚠️ **「补票金额计算正确（超程里程×费率）」在 ITP 侧没有实现。**

全仓库 `ExcessFare` / `excessFare` / `requestExcessFare` 的实现点只有一条链路，且是 **APP 自助补站**，不是 BOM：

- 接入：`fep-app-server/.../AppTicketController.java:52-55`（三别名 `/ci/app/requestExcessFare`、`/app/requestExcessFare`、`/app/ticket/requestExcessFare`）
- 业务：`ticket-server` `POST /ci/app/requestExcessFare`（`TicketRideStatusController.java:83-86`）→ `TicketRideStatusServiceImpl.java:141-154` → `ExcessFareHandler.java:33-242`
- 金额计算：`ExcessFareHandler.calculateTicketPrice`（`:183-208`），**只在 `upgradeAreaType == "02"`（补出站）时计算**（`:119`），其余类型 `trxAmount` 直接置 `"0"`（`:131`），`overtimeAmount` 恒为 `"0"`（`:107`、`:132`）
- 费率来源：调 `paraClient.requestTicketPriceByStation`（`:191`），入参是 `gateInStation`（进站，取自 `QRCodeStatus`）与 `upgradeStationCode`；查不到返回「票价查询失败，请稍后重试或前往车站服务台办理」（`:126`）
- deviceId 拼装 `upgradeStationCode + "36" + "01"`（`:98`），`36` 是「自助补站手机」硬编码

**BOM 侧没有任何 `excessFare` 接口。** BOM 与「超程」的唯一关联是 `requestGenNoCashOrder` 的业务类型取值注释「`03`:超程更新/一卡通余额不足更新」（`entity/BomNoCashOrder.java:22`、`model/request/bom/RequestGenNoCashOrderReqDTO.java:14`、前端 `web/src/views/trans/user/transaction-detail/index.vue:58`）——这是**交易类型字典值**，不是补票计算链路，ITP 侧不算金额。

> ⚠️ **不要把这句读成「BOM 不能补站」**。BOM 的补进站/补出站是**另一条链路**：IF5A-01 `requestCardDataAnalyse` + IF5A-03 `requestUpdateCardData` → `ticket-server/.../CardDataHandler.java`，链路完整可跑。本页说的是 `requestExcessFare` 补票**收费**链路在 BOM 侧不存在。补站链路详见 [BOM 单边处理](../bom-oneside/INDEX.md)。


也就是说：**BOM 补票时金额由 BOM 自己算好后传给 ITP，ITP 只按 `03` 类型收单收款。** 原用例的「金额计算正确」应该在 BOM 侧验，或改为验 ITP 收到的金额与 BOM 显示一致。

重写后的可测形态：

- BOM 发起 `requestGenNoCashOrder`，业务类型 `03`，金额取自 BOM 计算结果
- `requestPayment` + `requestGetPayResult` 支付成功
- `notiBusResult` 回写业务结果
- 断言 ITP 落库金额 == BOM 界面金额（不断言计算过程）

结果：☐ 通过（收单收款段） ☐ 阻塞（金额计算段：ITP 无实现）

## 矛盾/待验证

- ⚠️ IF2A-08 / IF2A-09 编号在 TVM 与 BOM 双义，用例中 **MUST 用 URL 而非编号**
- ⚠️ BOM 侧失败自动退款未找到 ITP 主动发起的实现，`requestTicketRefund` 无 javadoc、职责不明
- ⚠️ 补票金额计算归属 BOM，与原用例预期不符
- ⚠️ 「票卡状态更新」「打印凭证」「余额增加」三项均在设备侧，ITP 不可验

## 交叉引用

[[00-链路事实与配置字典]] · [[01-TVM]] · [[03-STT]] · [[04-阻塞项与缺陷候选]] · [APP 自助补站](../user-card/00-术语与状态字典.md)

## 参考文献

- `docs/business/tvm-bom-pay.md`
- `docs/business/ride-code.md`（`ExcessFareHandler`）
- `docs/business/common-services.md`（para-server 票价）
