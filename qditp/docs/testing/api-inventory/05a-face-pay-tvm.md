# face-pay-server -- TVM 侧接口契约清单（TvmOrderController）

> 公共响应约定见同目录 [README.md](README.md) §3。
>
> 本文件覆盖范围：**仅** `TvmOrderController`（`face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/ci/tvm/TvmOrderController.java`）。
> 类级 `@RequestMapping({"/itptvm/ci/tvm", "/itpbom/ci/tvm"})`（双前缀，javadoc 第 43 行说明：设备按 `/itptvm/` 或 `/itpbom/` 送同一批 URL 都能命中）。
> 15 个方法 / 17 个路径别名。

## 端点总览

| 序号 | 中文名 | IF 编号 | 对外 URL 主别名 | 响应码族 |
|---|---|---|---|---|
| 1 | 设备心跳 | IF2A-04 | `/notiDeviceHeard` | TVM `2xxx` |
| 2 | 出票成功结果上报 | IF2A-05 | `/notiTakeTicketResult` | TVM `2xxx` |
| 3 | 出票失败结果上报 | IF2A-06 | `/notiTakeTicketFailResult` | TVM `2xxx` |
| 4 | 设备主动退款 | -- | `/requestRefund` | TVM `9999` / `2xxx` |
| 5 | 提交单程票订单 | IF2A-01 | `/requestGenSjtOrder` | TVM `2xxx` |
| 6 | 查询支付结果 | IF2A-03 | `/requestPayResult` | TVM `2xxx` |
| 7 | 付款码支付 | IF2A-11 | `/requestPayment` | BOM `8xxx` |
| 8 | 激活取票订单 | IF8A-15 | `/requestActiveTicket` | TVM `2xxx` |
| 9 | 扫码取票订单查询 | IF2A-08 | `/requestTakeTicketAuth` | TVM `2xxx` |
| 10 | 请求充值下单 | IF2A-09 | `/requestTopup` | TVM `2xxx` |
| 11 | 充值成功通知 | IF2A-06 | `/topupCardResultNoti` | TVM `2xxx` |
| 12 | 充值失败通知 | IF2A-07 | `/topupCardFailNoti` | TVM `2xxx` |
| 13 | 支付中心反查订单详情 | -- | `/requestPayOrderDetail` | TVM `2xxx` |
| 14 | 支付结果回调 (JSON) | -- | `/payNotice` (consumes=json) | 支付中心 `{code,msg}` |
| 15 | 支付结果回调 (form) | -- | `/payNotice` (form fallback) | 支付中心 `{code,msg}` |

---

## §0. 公共骨架：BaseDeviceRequest 与 unwrap 机制

### BaseDeviceRequest 字段表

源文件：`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/face-pay-server/src/main/java/com/chinasofti/huateng/facepay/api/device/BaseDeviceRequest.java`

| 字段 | 类型 | javadoc 说明 |
|---|---|---|
| `providerId` | String | 商户编码：01-APP，02-TVM，03-BOM，04-AGM，05-ACC，06-ITP，07-STT |
| `charset` | String | 入参字符集：UTF-8 |
| `format` | String | 数据格式：json |
| `timestamp` | String | 请求时间，格式 YYYYMMDDHHMMSS |
| `deviceId` | String | 设备编码 |
| `signType` | String | 签名类型：00-不签名，01-sha1withrsa，02-MD5 |
| `sign` | String | 签名值 |
| `bizData` | String | 业务数据（JSON 字符串） |

**与 `ItpCommonFormRequest` 的关键差异**：`BaseDeviceRequest` 多了 `providerId` 字段。这是压测造数最容易错的点 -- APP 域的 form 用 `ItpCommonFormRequest`（无 `providerId`），而 TVM/BOM 域用 `BaseDeviceRequest`（有 `providerId`）。另注 `toString`（:95~104）**完全不输出 `sign`**（连脱敏占位都没有），但 `bizData` 原文进日志。

### DeviceRequests.unwrap 机制

源文件：`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/face-pay-server/src/main/java/com/chinasofti/huateng/facepay/api/device/DeviceRequests.java`

```java
public static <T extends BaseDeviceRequest> T unwrap(BaseDeviceRequest form, Class<T> type)
```

处理流程：
1. `form` 为 null 或 `bizData` 为 null / 空白 --> 返回 `null`。
2. 用 `JSON.parseObject(form.getBizData(), type)` 将 bizData 解析成目标 DTO 类。**不支持 Base64，只接受裸 JSON 字符串**。
3. 解析失败（`RuntimeException`）或解析结果为 null --> 返回 `null`。
4. 成功后，把 form 级的 8 个公共字段**逐个回写**到解析出的 DTO 上（`providerId`、`charset`、`format`、`timestamp`、`deviceId`（仅在非空时覆盖）、`signType`、`sign`、`bizData`）。
5. 返回填充好的 DTO。

调用方对 `null` 返回值统一回 `2002 非法参数`。

---

## §1. TVM `2xxx` 错误码族（DeviceRetCode + TvmResponses）

源文件：
- `/Users/tuanjie/workspace/company/chinasofti/qd/qditp/face-pay-server/src/main/java/com/chinasofti/huateng/facepay/api/device/DeviceRetCode.java`
- `/Users/tuanjie/workspace/company/chinasofti/qd/qditp/face-pay-server/src/main/java/com/chinasofti/huateng/facepay/api/device/tvm/TvmResponses.java`

### DeviceRetCode 枚举完整表

| 码值 | 枚举名 | 含义 | 实际触发条件（本 Controller 范围内） |
|---|---|---|---|
| `0000` | SUCCESS | 成功 | 正常处理完成 |
| `2999` | FAIL | 失败 | 支付中心预下单失败、激活取票订单状态非 PAID、支付失败 |
| `2001` | INVALID_DEVICE | 非法设备 | 本 Controller 未直接使用 |
| `2002` | INVALID_PARAM | 非法参数 | bizData 为空/格式错误、必填字段缺失、actualTakeTicketNum 解析失败 |
| `2003` | NO_ACTIVE_ORDER | 无激活的订单 | 取票鉴权（IF2A-08）未找到匹配订单或订单未激活 |
| `2004` | RECHARGE_AMOUNT_EXCEED | 充值金额超限 | 本 Controller 未直接使用 |
| `2005` | ORDER_NOT_PAID | 订单未支付 | 本 Controller 未直接使用 |
| `2006` | ORDER_NO_ERROR | 订单号错误 | 激活取票（IF8A-15）订单不存在、充值结果通知订单不存在 |
| `2007` | ORDER_REFUNDED | 订单已退款 | 本 Controller 未直接使用 |
| `2008` | ORDER_LOCKED | 订单已锁定 | 激活取票（IF8A-15）订单已被其他设备激活 |

### 退款接口专用码

退款（`requestRefund`）不走 `DeviceRetCode`，而是硬编码 `retCode=9999` 表示失败（`TvmResponses.refundFail`），`retCode=0000` 表示成功。退款订单不存在回 `2002`。

### 出票上报「订单不存在」专用码

- 出票成功上报：`providerId=03`(BOM) 回 `2999`，其余回 `-1`（`TvmResponses.takeTicketResultOrderNotFound`，:214）
- 出票失败上报：一律回 `2999`（`TvmResponses.takeTicketFailResultOrderNotFound`，:222）

### 付款码支付（requestPayment）响应码

该端点使用 `BomResponses`（非 `TvmResponses`），码值：`0000` 成功、`8999` 失败、`8006` 订单号错误。

### 支付中心回调（payNotice）响应码

使用 `PayCenterResponses`（`{code, msg}`）：`0` 受理成功、`-1` 失败、`2001` 订单不存在。

### 响应 JSON 结构

所有方法返回 `com.alibaba.fastjson2.JSONObject`。基本形态是 `{retCode, retMsg, ...业务键}`。不同端点的业务键在下方逐端点列出。

---

## 逐端点详情

### 1. 设备心跳（IF2A-04）

- **对外 URL**：`POST /itptvm/ci/tvm/notiDeviceHeard`、`POST /itpbom/ci/tvm/notiDeviceHeard`、`POST /itptvm/ci/tvm/deviceHeartbeat`、`POST /itpbom/ci/tvm/deviceHeartbeat`
- **容器内路径**：与对外 URL 完全一致
- **Controller**：`TvmOrderController#notiDeviceHeard`（`TvmOrderController.java:95`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` -- **不走 bizData unwrap**，直接取 `form.getDeviceId()`
- **响应码族**：TVM `2xxx`

**bizData 请求字段** -- 无。本端点只使用公共字段中的 `deviceId`，不解析 `bizData`。

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | 恒定 `0000` |
| `retMsg` | String | 恒定「成功」 |

**Controller 层校验与短路分支**
- `form != null` 时才调 `heartbeatService.recordHeartbeat`；`form == null` 时直接返回 `0000`（:97~99）

**压测备注**：写 `F2F_DEVICE_STATUS`（`mergeHeartbeat`，MERGE INTO）/ 不调支付中心 / 幂等键：主键 `PK_F2F_DEVICE_STATUS (CHANNEL, DEVICE_ID)`（`f2f-schema.sql:303`），同一设备反复心跳只更新不新增 / `deviceId` 为空时 service 层直接跳过落库并打 WARN，但**接口仍返 `0000`** / 配套兜底是 `F2fDeviceOfflineJob`（把心跳超时的在线设备置离线）

---

### 2. 出票成功结果上报（IF2A-05）

- **对外 URL**：`POST /itptvm/ci/tvm/notiTakeTicketResult`、`POST /itpbom/ci/tvm/notiTakeTicketResult`
- **容器内路径**：与对外 URL 完全一致
- **Controller**：`TvmOrderController#notiTakeTicketResult`（`TvmOrderController.java:105`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` --> `DeviceRequests.unwrap(form, NotiTakeTicketResultReqDTO.class)`
- **响应码族**：TVM `2xxx`

**bizData 请求字段** -- DTO `com.chinasofti.huateng.facepay.api.device.tvm.NotiTakeTicketResultReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无（代码无 Bean Validation，空值不会 400）。Controller :108 显式判空 --> `2002` | 订单号 |
| `actualTakeTicketNum` | String | 无。service 层 `actualNum()` 解析失败 --> `2002` | 实际出票张数 |
| `takeTickeDate` | String | 无 | 出票时间（注意字段名拼写：ticke 非 ticket） |
| `ticketList` | `List<TicketInfo>` | 无 | 出票明细列表，可能为空 |

**TicketInfo 嵌套字段**（`com.chinasofti.huateng.facepay.api.device.TicketInfo`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `ticketLogicNum` | String | 票逻辑卡号 |
| `transDate` | String | 交易日期 |
| `transAmount` | String | 单票金额，单位分 |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `2002` / `2999`(BOM订单不存在) / `-1`(非BOM订单不存在) |
| `retMsg` | String | 说明文案 |

**Controller 层校验与短路分支**
- `request == null || orderNo 为空` --> `2002 "orderNo不能为空"`（:108~109）

**压测备注**：写 `F2F_RESULT_REPORT`（幂等键 `UK_F2F_REPORT_IDEM (REPORT_TYPE, ORDER_NO)`，`f2f-schema.sql:177`；撞唯一索引即视为重传、直接回 `0000` 不再做后续动作）+ 写 `F2F_TICKET`（逐张，`ticketLogicNum` 经 `F2fLogicCardNo.normalize` 转大写）+ CAS 推进 `F2F_ORDER`（仅 `PAID -> FULFILLED`，不在 PAID 时只留上报并打 WARN）+ 入队 APP 出向通知 / **本端点不调支付中心** / 兜底是 `F2fReportRecoveryJob`（出票上报补偿）与 `F2fNotifyJob`（通知投递）

---

### 3. 出票失败结果上报（IF2A-06）

- **对外 URL**：`POST /itptvm/ci/tvm/notiTakeTicketFailResult`、`POST /itpbom/ci/tvm/notiTakeTicketFailResult`
- **容器内路径**：与对外 URL 完全一致
- **Controller**：`TvmOrderController#notiTakeTicketFailResult`（`TvmOrderController.java:116`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` --> `DeviceRequests.unwrap(form, NotiTakeTicketFailResultReqDTO.class)`
- **响应码族**：TVM `2xxx`

**bizData 请求字段** -- DTO `com.chinasofti.huateng.facepay.api.device.tvm.NotiTakeTicketFailResultReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无。Controller :120 显式判空 --> `2002` | 订单号 |
| `actualTakeTicketNum` | String | 无。service 层解析失败 --> `2002` | 实际出票张数（可能为 0） |
| `faultOccurDate` | String | 无 | 故障发生时间 |
| `faultSlipSeq` | String | 无 | TVM 打印的故障单号 |
| `errorCode` | String | 无 | 设备侧错误码 |
| `errorMessage` | String | 无 | 设备侧错误描述 |
| `ticketList` | `List<TicketInfo>` | 无 | 已成功出票的明细 |

**响应字段**：同端点 2。

**Controller 层校验与短路分支**
- `request == null || orderNo 为空` --> `2002 "orderNo不能为空"`（:120~121）

**压测备注**：写 `F2F_RESULT_REPORT` + `F2F_TICKET` + 更新 `F2F_ORDER` + 差额退款（未出票部分 `ticketPrice * shortfall`）写 `F2F_REFUND` 并调支付中心 / 幂等键同端点 2

---

### 4. 设备主动退款

- **对外 URL**：`POST /itptvm/ci/tvm/requestRefund`、`POST /itpbom/ci/tvm/requestRefund`
- **容器内路径**：与对外 URL 完全一致
- **Controller**：`TvmOrderController#requestRefund`（`TvmOrderController.java:128`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` --> `DeviceRequests.unwrap(form, RequestRefundReqDTO.class)`
- **响应码族**：退款专用 `9999` / `0000` + `2002`

**bizData 请求字段** -- DTO `com.chinasofti.huateng.facepay.api.device.tvm.RequestRefundReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无。Controller :131 显式判空 --> `9999` | 原订单号 |
| `refundReason` | String | 无 | 退款原因，可空 |
| `refundAmt` | String | 无。Controller :131 显式判空 --> `9999` | 退款金额，单位分 |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `9999` / `2002` |
| `retMsg` | String | 说明文案 |
| `refundResult` | String | `SUCCESS` / `PROCESSING` / null（失败时） |
| `refundResultDesc` | String | 结果描述 / null |
| `refundNo` | String | 退款单号 / null |

**Controller 层校验与短路分支**
- `request == null || orderNo 为空 || refundAmt 为空` --> `9999 "订单号和退款金额不能为空"`（:131~132）

**压测备注**：写 `F2F_REFUND` + 调支付中心退款网关 / 幂等键 `UK_F2F_REFUND_NO (REFUND_NO)`（`f2f-schema.sql:244`）与 `UK_F2F_REFUND_IDEM`（`f2f-schema.sql:246`，含 `NVL(TICKET_LOGIC_NUM, '#WHOLE#')` 表达式），命中已有退款单时走 `alreadyExisted` 分支直接回当前状态 / 前置闸门：订单状态必须可退、`countUnsettledRefunds > 0` 拒绝并发退、退款金额不得超过 `ORDER_AMOUNT - REFUND_AMOUNT` / `F2fRefundReconcileJob` 兜底退款回查

---

### 5. 提交单程票订单（IF2A-01）

- **对外 URL**：`POST /itptvm/ci/tvm/requestGenSjtOrder`、`POST /itpbom/ci/tvm/requestGenSjtOrder`
- **容器内路径**：与对外 URL 完全一致
- **Controller**：`TvmOrderController#requestGenSjtOrder`（`TvmOrderController.java:139`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` --> `DeviceRequests.unwrap(form, RequestGenSjtOrderReqDTO.class)`
- **响应码族**：TVM `2xxx`

**bizData 请求字段** -- DTO `com.chinasofti.huateng.facepay.api.device.tvm.RequestGenSjtOrderReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `entryStationCode` | String | 无。`validateGenSjtOrder` :309 当 `singleTicketType=0` 时判空 --> `2002` | 起点站代码 |
| `exitStationCode` | String | 无。`validateGenSjtOrder` :312 当 `singleTicketType=0` 时判空 --> `2002` | 终点站代码 |
| `ticketPrice` | String | 无。`validateGenSjtOrder` :299 判空 --> `2002` | 票价，单位分 |
| `singelTicketNum` | String | 无。`validateGenSjtOrder` :302 判空 --> `2002`（注意拼写：singel 非 single） | 购买数量 |
| `singleTicketType` | String | 无。`validateGenSjtOrder` :305 判空 --> `2002` | 购票类型：0-按站点，1-按固定票价 |
| `payType` | String | 无。`validateGenSjtOrder` :316 判空 --> `2002` | 0-其他支付方式，1-数字人民币APP |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `2002` / `2999` |
| `retMsg` | String | 说明文案 |
| `orderNo` | String | 生成的订单号 / null（失败时） |
| `payUrl` | String | 支付二维码 URL / null（失败时；BOM 下单无此字段值） |

**Controller 层校验与短路分支**
- `request == null` --> `2002 "bizData不能为空或格式错误"`（:142~143）
- `validateGenSjtOrder` 返回非 null --> `2002 + 具体文案`（:146~148）
- `providerId=03`(BOM) 走 `createBomSaleOrder`，其余走 `createSingleTicketOrder`（:149~152）

**压测备注**：写 `F2F_ORDER` + `F2F_PAYMENT` / payType 非 0 时调支付中心预下单 / 幂等键：订单号由 `F2fOrderNoGenerator` 生成（雪花）无重复 / `F2fOrderExpireJob` `@Scheduled` 兜底订单过期

---

### 6. 查询支付结果（IF2A-03）

- **对外 URL**：`POST /itptvm/ci/tvm/requestPayResult`、`POST /itpbom/ci/tvm/requestPayResult`、`POST /itptvm/ci/tvm/requestGetPayResult`、`POST /itpbom/ci/tvm/requestGetPayResult`
- **容器内路径**：与对外 URL 完全一致
- **Controller**：`TvmOrderController#requestPayResult`（`TvmOrderController.java:162`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` --> `DeviceRequests.unwrap(form, RequestPayResultReqDTO.class)`
- **响应码族**：TVM `2xxx`

**bizData 请求字段** -- DTO `com.chinasofti.huateng.facepay.api.device.tvm.RequestPayResultReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无。Controller :165 显式判空（`isBlank`） --> `2002` | 订单号 |
| `userId` | String | 无 | 用户标识（可选） |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `2002` / `2999` |
| `retMsg` | String | 说明文案 |
| `paymentResult` | String | `ORDERED` / `PROCESSING` / `SUCCESS` / `FAILED` / null |
| `paymentResultDesc` | String | 结果描述 / null |
| `paymentChannelCode` | String | 支付渠道码 / null |

**Controller 层校验与短路分支**
- `request == null || orderNo 为空` --> `2002 "orderNo不能为空"`（:165~166）

**压测备注**：读 `F2F_ORDER` + `F2F_PAYMENT` / 待付状态时调支付中心查询接口 / 无写表（查询型） / 不调外部网关（仅待付状态时查支付中心）

---

### 7. 付款码支付（IF2A-11）

- **对外 URL**：`POST /itptvm/ci/tvm/requestPayment`、`POST /itpbom/ci/tvm/requestPayment`
- **容器内路径**：与对外 URL 完全一致
- **Controller**：`TvmOrderController#requestPayment`（`TvmOrderController.java:173`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` --> `DeviceRequests.unwrap(form, RequestPaymentReqDTO.class)`
- **响应码族**：**BOM `8xxx`**（注意：虽然在 TvmOrderController 里，但使用 `BomResponses`）

**bizData 请求字段** -- DTO `com.chinasofti.huateng.facepay.api.device.tvm.RequestPaymentReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无。Controller :176 判空 --> `8999` | 订单号 |
| `paymentCode` | String | 无。Controller :182 判空 --> `8999`。`toString` 对此字段恒定脱敏 | 乘客付款码（条码/二维码内容） |
| `paymentVendor` | String | 无。Controller :179 判空 --> `8999` | 支付渠道（微信/支付宝等） |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `8999` / `8006` |
| `retMsg` | String | 说明文案 |
| `paymentResult` | String | `SUCCESS` / `FAILED` / `ORDERED` / null |
| `paymentResultDesc` | String | 结果描述 / null |
| `msg` | String | 同 paymentResultDesc / null |

**Controller 层校验与短路分支**
- `request == null || orderNo 为空` --> `8999 "orderNo不能为空"`（:176~177）
- `paymentVendor 为空` --> `8999 "paymentVendor不能为空"`（:179~180）
- `paymentCode 为空` --> `8999 "paymentCode不能为空"`（:182~183）

**压测备注**：写 `F2F_PAYMENT`（新增尝试行）/ 调支付中心付款码支付接口 / 同步或异步返结果 / 幂等：订单状态白名单 + 已付终态短路

---

### 8. 激活取票订单（IF8A-15）

- **对外 URL**：`POST /itptvm/ci/tvm/requestActiveTicket`、`POST /itpbom/ci/tvm/requestActiveTicket`
- **容器内路径**：与对外 URL 完全一致
- **Controller**：`TvmOrderController#requestActiveTicket`（`TvmOrderController.java:190`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` --> `DeviceRequests.unwrap(form, RequestActiveTicketReqDTO.class)`
- **响应码族**：TVM `2xxx`

**bizData 请求字段** -- DTO `com.chinasofti.huateng.facepay.api.device.tvm.RequestActiveTicketReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无。Controller :193 判空 --> `2002` | 订单号 |
| `deviceId` | String | 无。Controller :193 判空 --> `2002`（注意：此处取 bizData 内的 deviceId，非公共字段） | 设备编码 |
| `qrcodeGenDate` | String | 无。Controller :194 判空 --> `2002` | 二维码生成日期 |
| `randomFact` | String | 无。Controller :194 判空 --> `2002` | 二维码随机因子 |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `2002` / `2006` / `2999` / `2008` |
| `retMsg` | String | 说明文案 |

**Controller 层校验与短路分支**
- `request == null || orderNo || deviceId || qrcodeGenDate || randomFact 任一为空` --> `2002`（:193~195）

**压测备注**：更新 `F2F_ORDER`（CAS `activateForDevice`：PAID + 未激活 --> 已激活）/ 不调支付中心 / 幂等键：CAS 竞争，已激活返 `2008`

---

### 9. 扫码取票订单查询（IF2A-08）

- **对外 URL**：`POST /itptvm/ci/tvm/requestTakeTicketAuth`、`POST /itpbom/ci/tvm/requestTakeTicketAuth`
- **容器内路径**：与对外 URL 完全一致
- **Controller**：`TvmOrderController#requestTakeTicketAuth`（`TvmOrderController.java:202`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` --> `DeviceRequests.unwrap(form, RequestTakeTicketAuthReqDTO.class)`
- **响应码族**：TVM `2xxx`

**bizData 请求字段** -- DTO `com.chinasofti.huateng.facepay.api.device.tvm.RequestTakeTicketAuthReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `deviceId` | String | 无。Controller :205 判空 --> `2002`（取 bizData 内的 deviceId） | 设备编码 |
| `qrcodeGenDate` | String | 无。Controller :206 判空 --> `2002` | 二维码生成日期 |
| `randomFact` | String | 无。Controller :206 判空 --> `2002` | 二维码随机因子 |

**响应字段（成功时 8 个业务键）**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `2002` / `2003` |
| `retMsg` | String | 说明文案 |
| `orderNo` | String | 订单号 / null |
| `deviceId` | String | 激活设备号 / null |
| `entryStationCode` | String | 起点站 / null |
| `exitStationCode` | String | 终点站 / null |
| `ticketPrice` | Long | 票价（分）/ null |
| `singelTicketNum` | String | 购票张数 / null（注意拼写：singel） |
| `singleTicketType` | String | 购票类型 / null |
| `paymentChannelCode` | String | 支付渠道码 / null |

**Controller 层校验与短路分支**
- `request == null || deviceId || qrcodeGenDate || randomFact 任一为空` --> `2002`（:205~208）

**压测备注**：读 `F2F_ORDER`（按 deviceId + qrcodeGenDate + randomFact 三要素查询）+ `F2F_PAYMENT` / 不写表 / 不调支付中心

---

### 10. 请求充值下单（IF2A-09）

- **对外 URL**：`POST /itptvm/ci/tvm/requestTopup`、`POST /itpbom/ci/tvm/requestTopup`
- **容器内路径**：与对外 URL 完全一致
- **Controller**：`TvmOrderController#requestTopup`（`TvmOrderController.java:215`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` --> `DeviceRequests.unwrap(form, RequestTopupReqDTO.class)`
- **响应码族**：TVM `2xxx`

**bizData 请求字段** -- DTO `com.chinasofti.huateng.facepay.api.device.tvm.RequestTopupReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `ticketLogicNum` | String | 无。Controller :218 判空 --> `2002` | 票卡逻辑卡号 |
| `ticketPhysicsNum` | String | 无。Controller :218 判空 --> `2002` | 票卡物理卡号 |
| `beforeAmount` | String | 无。Controller :219 判空 --> `2002` | 充值前卡内余额，单位分 |
| `transAmount` | String | 无。Controller :219 判空 --> `2002` | 本次充值金额，单位分 |
| `payType` | String | 无。Controller :222 单独判空 --> `2002 "payType不能为空"` | 支付方式：0 聚合码，其余走支付中心 |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `2002` / `2999` |
| `retMsg` | String | 说明文案 |
| `orderNo` | String | 生成的订单号 / null |
| `payUrl` | String | 支付二维码 URL / null |

**Controller 层校验与短路分支**
- `request == null || ticketPhysicsNum || ticketLogicNum || beforeAmount || transAmount 任一为空` --> `2002`（:218~220）
- `payType 为空` --> `2002 "payType不能为空"`（:222~223）

**压测备注**：写 `F2F_ORDER`（bizType=TOPUP）+ `F2F_PAYMENT` / payType 非 0 时调支付中心预下单 / `ticketLogicNum` 入库前经 `F2fLogicCardNo.normalize` 转大写 / `F2fOrderExpireJob` 兜底

---

### 11. 充值成功通知（IF2A-06）

- **对外 URL**：`POST /itptvm/ci/tvm/topupCardResultNoti`、`POST /itpbom/ci/tvm/topupCardResultNoti`
- **容器内路径**：与对外 URL 完全一致
- **Controller**：`TvmOrderController#topupCardResultNoti`（`TvmOrderController.java:230`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` --> `DeviceRequests.unwrap(form, TopupCardResultNotiReqDTO.class)`
- **响应码族**：TVM `2xxx`

**bizData 请求字段** -- DTO `com.chinasofti.huateng.facepay.api.device.tvm.TopupCardResultNotiReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无。Controller :233 判空 --> `2002` | 订单号 |
| `ticketLogicNum` | String | 无。Controller :233 判空 --> `2002` | 票卡逻辑卡号 |
| `ticketPhysicsNum` | String | 无。Controller :234 判空 --> `2002` | 票卡物理卡号 |
| `transDate` | String | 无 | 写卡交易时间 |
| `transAmount` | String | 无 | 本次充值金额，单位分 |
| `afterAmount` | String | 无 | 充值后卡内余额，单位分 |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `2002` / `2006` |
| `retMsg` | String | 说明文案 |

**Controller 层校验与短路分支**
- `request == null || orderNo || ticketLogicNum || ticketPhysicsNum 任一为空` --> `2002`（:233~235）

**压测备注**：写 `F2F_RESULT_REPORT` + 更新 `F2F_ORDER` 状态 --> FULFILLED / 幂等键：`UK_F2F_REPORT_IDEM (REPORT_TYPE, ORDER_NO)` / 不调支付中心

---

### 12. 充值失败通知（IF2A-07）

- **对外 URL**：`POST /itptvm/ci/tvm/topupCardFailNoti`、`POST /itpbom/ci/tvm/topupCardFailNoti`
- **容器内路径**：与对外 URL 完全一致
- **Controller**：`TvmOrderController#topupCardFailNoti`（`TvmOrderController.java:242`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` --> `DeviceRequests.unwrap(form, TopupCardFailNotiReqDTO.class)`
- **响应码族**：TVM `2xxx`

**bizData 请求字段** -- DTO `com.chinasofti.huateng.facepay.api.device.tvm.TopupCardFailNotiReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无。Controller :245 判空 --> `2002` | 订单号 |
| `ticketLogicNum` | String | 无。Controller :245 判空 --> `2002` | 票卡逻辑卡号 |
| `ticketPhysicsNum` | String | 无。Controller :246 判空 --> `2002` | 票卡物理卡号 |
| `topupStatus` | String | 无 | 充值结果：01 失败 / 02 存疑 / 03 取消 |
| `faultOccurDate` | String | 无 | 故障发生时间 |
| `faultSlipSeq` | String | 无 | TVM 打印的故障单号 |
| `errorCode` | String | 无 | 设备侧错误码 |
| `errorMessage` | String | 无 | 设备侧错误描述 |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `2002` / `2006` |
| `retMsg` | String | 说明文案 |

**Controller 层校验与短路分支**
- `request == null || orderNo || ticketLogicNum || ticketPhysicsNum 任一为空` --> `2002`（:245~247）

**压测备注**：写 `F2F_RESULT_REPORT` + 更新 `F2F_ORDER` / `topupStatus=01` 时触发全额退款写 `F2F_REFUND` 并调支付中心 / 幂等同端点 11

---

### 13. 支付中心反查订单详情

- **对外 URL**：`POST /itptvm/ci/tvm/requestPayOrderDetail`、`POST /itpbom/ci/tvm/requestPayOrderDetail`
- **容器内路径**：与对外 URL 完全一致
- **Controller**：`TvmOrderController#requestPayOrderDetail`（`TvmOrderController.java:254`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：**`@ModelAttribute RequestPayResultReqDTO`** -- 直接绑定业务 DTO，**不走 bizData unwrap**
- **响应码族**：TVM `2xxx`

**请求字段** -- 直接作为 form 字段（**非 bizData 内嵌**）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无。Controller :256 判空 --> `2002` | 订单号 |
| `userId` | String | 无 | 用户标识（可选） |
| （继承自 BaseDeviceRequest 的 8 个公共字段也可以作为 form 参数上送，但本端点不使用它们） | | | |

**响应字段（购票单 13 个键 / 充值单 12 个键）**

购票单响应（`TvmResponses.payOrderDetail`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `2002` |
| `retMsg` | String | 说明文案 |
| `orderNo` | String | 订单号 |
| `singlePickupStationName` | String | 起点站代码（当站名用） |
| `singlePickupStationCode` | String | 起点站代码 |
| `singleGetoffStationName` | String | 终点站代码（当站名用） |
| `singleGetoffStationCode` | String | 终点站代码 |
| `singleTicketNum` | Integer | 购票张数 |
| `totalTicketPrice` | String | 总金额（分） |
| `regDate` | String | 下单时间 yyyyMMddHHmmss |
| `payDate` | String | 支付时间（仅已付时有） |
| `orderStatus` | String | `1` 支付中 / `2` 支付成功 / `7` 已退款 / 空串 |
| `subject` | String | 恒定 "一票通_单程票" |
| `body` | String | 恒定 "一票通_单程票" |
| `notifyUrl` | String | 支付回调地址 |

**Controller 层校验与短路分支**
- `form == null || orderNo 为空` --> `2002`（:256~257）

**压测备注**：读 `F2F_ORDER` / 不写表 / 不调支付中心 / 报文形态与其他端点完全不同（见已知坑 2）

---

### 14. 支付结果回调 - JSON（支付中心 --> ITP）

- **对外 URL**：`POST /itptvm/ci/tvm/payNotice`、`POST /itpbom/ci/tvm/payNotice`（`Content-Type: application/json`）
- **容器内路径**：与对外 URL 完全一致
- **Controller**：`TvmOrderController#payNoticeJson`（`TvmOrderController.java:264`）
- **Content-Type**：`application/json`
- **入参形态**：`@RequestBody(required=false) PayCenterCallbackRequest`
- **响应码族**：支付中心 `{code, msg}`

**PayCenterCallbackRequest 信封字段**（`com.chinasofti.huateng.facepay.api.paycenter.PayCenterCallbackRequest`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `merchantNo` | String | 商户号 |
| `apiVersion` | String | API 版本 |
| `signType` | String | 签名类型 |
| `sign` | String | 签名值 |
| `charset` | String | 字符集 |
| `bizData` | String | 业务数据（JSON 或 Base64(JSON)） |

`bizDataJson()` 方法：先尝试 Base64 解码，解不出再当裸 JSON 用。

**bizData 内层字段** -- DTO `com.chinasofti.huateng.facepay.api.paycenter.PayNoticeReqDTO`

| 字段 | 类型 | 说明 |
|---|---|---|
| `orderNo` | String | 支付中心订单号 |
| `merchantOrderNo` | String | 我方订单号（= F2F_ORDER.ORDER_NO） |
| `channelOrderNo` | String | 渠道订单号 |
| `status` | String | SUCCESS / FAILED / ORDERED / UNPAID |
| `payTime` | String | 支付时间 |
| `totalAmount` | String | 订单总金额，单位分 |
| `cashAmount` | String | 现金支付金额，单位分 |
| `couponAmount` | String | 优惠金额，单位分 |
| `payUserId` | String | 付款用户标识 |
| `paymentVendor` | String | 支付方式（渠道码） |
| `options` | String | 扩展字段 |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | String | `0` 受理成功 / `-1` 失败 / `2001` 订单不存在 |
| `msg` | String | 说明文案 |

**Controller 层校验与短路分支**
- `request == null` --> `-1 "bizData不能为空"`（:277~278）
- `bizDataJson() == null` --> `-1 "bizData不能为空"`（:281~282）
- bizData 解析失败 --> `-1 "bizData格式错误"`（:287~289）
- 解析结果为 null --> `-1 "bizData格式错误"`（:291~292）

**压测备注**：更新 `F2F_PAYMENT`（markSuccess / markFinalStatus）+ 更新 `F2F_ORDER` 状态 / 不主动调支付中心 / 幂等：终态短路、重复到达回 `0` / `F2fNotifyJob` `@Scheduled` 兜底通知投递

---

### 15. 支付结果回调 - form 兜底

- **对外 URL**：同端点 14（`POST /itptvm/ci/tvm/payNotice`、`POST /itpbom/ci/tvm/payNotice`），当 `Content-Type` 非 `application/json` 时落到此方法
- **Controller**：`TvmOrderController#payNotice`（`TvmOrderController.java:270`）
- **Content-Type**：`application/x-www-form-urlencoded`（或无 Content-Type）
- **入参形态**：`@ModelAttribute PayCenterCallbackRequest`
- **响应码族**：同端点 14

其余逻辑与端点 14 完全相同，共用私有方法 `handlePayNotice`（:275）。

---

## 已知坑与测试注意点

### 坑 1：`/payNotice` 同一个 URL 按 Content-Type 分裂成两个方法

`TvmOrderController.java:263~272`。`payNoticeJson`（:264，`consumes=APPLICATION_JSON_VALUE`）接收 JSON body；`payNotice`（:270，无 consumes 限定）接收 form 或无 Content-Type 的请求。两者共用私有方法 `handlePayNotice`（:275）。压测**两种 Content-Type 都要出用例**：支付中心实际用哪种取决于其实现版本。

### 坑 2：`requestPayOrderDetail` 不走 bizData unwrap

`TvmOrderController.java:254`。该端点的参数类型是 `@ModelAttribute RequestPayResultReqDTO`（直接绑定业务 DTO），**不走 `BaseDeviceRequest` + `DeviceRequests.unwrap`**。报文形态与其他 14 个端点完全不同：`orderNo` 等字段直接作为 form 的顶层 key（`orderNo=xxx&userId=yyy`），**不需要** `bizData={"orderNo":"xxx"}` 的信封。压测脚本不能套其他端点的模板。

### 坑 3：IF2A-06 编号在本类被用了两次，且 Controller 与 DTO 的 javadoc 编号互相不一致

`notiTakeTicketFailResult`（`TvmOrderController.java:114`，javadoc 标注 IF2A-06，出票失败上报）与 `topupCardResultNoti`（`TvmOrderController.java:228`，javadoc 标注 IF2A-06，充值成功通知）在本类里共用了同一个编号。

更进一步，**Controller javadoc 与对应 DTO javadoc 的编号在 4 处对不上**（以下是逐个源文件实读的结果，未核对甲方规格原文，因此只陈述代码内的不一致事实）：

| 方法 | Controller javadoc | DTO 类 javadoc |
|---|---|---|
| `requestPayment` | IF2A-11 | `RequestPaymentReqDTO`: IF2A-02 |
| `requestActiveTicket` | IF8A-15 | `RequestActiveTicketReqDTO`: IF2A-07 |
| `topupCardResultNoti` | IF2A-06 | `TopupCardResultNotiReqDTO`: IF2A-10 |
| `topupCardFailNoti` | IF2A-07 | `TopupCardFailNotiReqDTO`: IF2A-11 |

**用例编号不要按 IF 号做唯一键**，建议用「方法名」或「序号+方法名」。

### 坑 4：设备送小写 `ticketLogicNum` 时返回 `8999`

已知残留，成因与裁决写在 `com.chinasofti.huateng.facepay.domain.F2fLogicCardNo` 的类注释里（`F2fLogicCardNo.java:5~20`）：`F2fTicketMapper` 的四条语句全是 `WHERE TICKET_LOGIC_NUM = #{ticketLogicNum}`（精确等值，走 `UK_F2F_TICKET_LOGIC` / `IDX_F2F_TICKET_RECENT`，见 `F2fTicketMapper.xml:83/90/99/112`），而旧应用 collect-pay 是 `UPPER()` 比较；`F2F_REFUND` 的幂等又依赖 `NVL(TICKET_LOGIC_NUM, '#WHOLE#')` 函数唯一索引。大小写不一致时查询命中 0 行、幂等键也对不上，表现为业务码 `8999`「没有查找到出票信息」而链路本身完全正常。

已按裁决**只做数据归一、未改查询侧代码**：`F2fLogicCardNo.normalize` 在入参侧统一转大写（本 Controller 范围内的归一点是 `requestTopup` 与两个出票上报的 `saveTickets`，`F2fTicketIssueService.java:224`）。类注释明确 **NEVER 改成在 mapper 里写 `UPPER(TICKET_LOGIC_NUM)`** —— 那会让两个索引在该谓词上失效。

**压测造数 MUST 用大写**（如 `0026070101003C68`）。建议补一条小写负向用例（如 `0026070101003c68`）钉住现状；注意 `8999` 属 BOM 响应码族（`BomResponses.CODE_FAIL`），因此该症状出现在使用 `BomResponses` 的端点上（本文件内是 `requestPayment`，以及本文件范围外的 BOM 交易查询）。类注释记录的实测事实：2026-09-17 同一台设备出票上报送小写、交易查询送大写，设备自己两条报文就不一致。

### 坑 5：本模块 7 个 `@Scheduled` 分布在 6 个类，无分布式锁，MUST 单副本

压测时扩副本会出现重复补偿、重复退款回查、重复通知投递，这是配置约束不是缺陷。

| 序号 | 类 | 行号 | 方法名 | 调度方式 | 默认间隔 |
|---|---|---|---|---|---|
| 1 | `F2fNotifyJob` | :22 | `deliverDueTasks` | fixedDelay | 30000ms（初始延迟 20000ms） |
| 2 | `F2fRefundReconcileJob` | :30 | `reconcileRefunds` | fixedDelay | 60000ms（初始延迟 30000ms） |
| 3 | `F2fOrderExpireJob` | :30 | `reconcileExpiredOrders` | fixedDelay | 30000ms（初始延迟 15000ms） |
| 4 | `F2fDeviceOfflineJob` | :28 | `markTimeoutDevicesOffline` | fixedDelay | 60000ms（初始延迟 60000ms） |
| 5 | `SupplementOrderCloseProcessor` | :21 | `converge` | cron | `0 */5 * * * ?` |
| 6 | `SupplementOrderCloseProcessor` | :33 | `closeTimeout` | cron | `0 */10 * * * ?` |
| 7 | `F2fReportRecoveryJob` | :26 | `resumeDueReports` | fixedDelay | 120000ms（初始延迟 60000ms） |

所有类位于 `/Users/tuanjie/workspace/company/chinasofti/qd/qditp/face-pay-server/src/main/java/com/chinasofti/huateng/facepay/scheduler/` 目录下。

### 坑 6：requestPayment 使用 BomResponses 而非 TvmResponses

虽然 `requestPayment` 位于 `TvmOrderController`，但其失败码是 `8999`（BOM 族），不是 `2999`（TVM 族）。订单不存在返回 `8006`。这是设计意图：付款码支付（主扫）的设备端解析协议按 BOM 规格实现。

### 坑 7：出票上报「订单不存在」的 retCode 按 providerId 分裂

- 出票成功上报（`notiTakeTicketResult`）：`providerId=03`(BOM) 返回 `retCode=2999`，其余（含缺失）返回 `retCode=-1`（`TvmResponses.takeTicketResultOrderNotFound`，TvmResponses.java:214~218）。
- 出票失败上报（`notiTakeTicketFailResult`）：一律返回 `retCode=2999`（`TvmResponses.takeTicketFailResultOrderNotFound`，TvmResponses.java:222~227）。

这两个码值不在 `DeviceRetCode` 枚举内（`-1` 和硬编码 `2999`），是 `TvmResponses` 内的特殊分支。

### 坑 8：validateGenSjtOrder 的 singelTicketNum 拼写

字段名是 `singelTicketNum`（singel 而非 single），这是对外契约不能改。同样 `TvmResponses.takeTicketAuthBody` 里的响应键也是 `singelTicketNum`。压测造数和断言都要按这个拼写。
