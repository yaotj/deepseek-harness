# 05b — face-pay-server BOM 域（`BomOrderController`）

> 公共响应约定见同目录 [README.md](README.md) §3，公共报文骨架见 [05a-face-pay-tvm.md](05a-face-pay-tvm.md) §0。
> 本文件**只覆盖一个 Controller**：`face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/ci/bom/BomOrderController.java`（全文 331 行）。

## 0. 报文骨架差异（只写差异，字段表见 05a §0）

设备域用的是**模块内独立**的 `com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest`（`api/device/BaseDeviceRequest.java`，8 个字段），**不是** `model` 模块的 `ItpCommonFormRequest`。与 `ItpCommonFormRequest` 的唯一字段差异是**多一个 `providerId`**（商户编码：`01`-APP / `02`-TVM / `03`-BOM / `04`-AGM / `05`-ACC / `06`-ITP / `07`-STT，见 `BaseDeviceRequest.java:6`）；其余 7 个 `charset` / `format` / `timestamp` / `deviceId` / `signType` / `sign` / `bizData` 与 05a §0 那张表逐字一致，本文件**不再重复**。

三条与压测直接相关的解包行为（`api/device/DeviceRequests.java:12~36`）：

- `bizData` 为 `null` / 空白 / 非法 JSON 时 `unwrap` 返回 `null`，**不抛异常、不返 400**，由 Controller 转成对应族的错误码。
- `unwrap` 成功后会把骨架字段**回灌**进 DTO：`providerId` / `charset` / `format` / `timestamp` / `signType` / `sign` / `bizData` 无条件覆盖；**`deviceId` 只在 form 侧非空时覆盖**（`DeviceRequests.java:29`），因此 `bizData` 里的 `deviceId` 在 form 侧不传时会被保留。
- **本 Controller 全程没有验签代码**，`sign` / `signType` 只被回灌进 DTO 供落库，压测可送任意值。

## 1. 本文件端点总览

类级 `@RequestMapping({"/itpbom/ci/bom", "/itptvm/ci/bom"})`（`BomOrderController.java:45`，**双前缀**）。14 个方法 / 16 个 path 别名 / 展开成 32 条可打 URL。

| # | 中文接口名 | IF 编号 | 对外 URL 主别名 | 响应码族 |
|---|---|---|---|---|
| 1 | 设备心跳 | —（与 AGM 侧 IF1A-03 同族命名） | `POST /itpbom/ci/bom/notiDeviceHeard` | BOM `8xxx` |
| 2 | 非现金收款下单 | IF8A-04（BOM 域） | `POST /itpbom/ci/bom/requestGenNoCashOrder` | BOM `8xxx` |
| 3 | 扫码支付（付款码主扫） | IF8A-05（BOM 域） | `POST /itpbom/ci/bom/requestPayment` | BOM `8xxx` |
| 4 | 查询支付结果 | IF8A-06（BOM 域） | `POST /itpbom/ci/bom/requestGetPayResult` | BOM `8xxx` |
| 5 | 业务操作结果通知 | IF2A-08 | `POST /itpbom/ci/bom/notiBusResult` | BOM `8xxx` |
| 6 | 充值结果通知 | IF2A-09 | `POST /itpbom/ci/bom/notiTopupResult` | BOM `8xxx` |
| 7 | 单程票交易查询 | — | `POST /itpbom/ci/bom/requestOrderResult` | BOM `8xxx` |
| 8 | 单程票退款 | — | `POST /itpbom/ci/bom/requestTicketRefund` | BOM `8xxx` |
| 9 | 票卡分析 | IF5A-01 | `POST /itpbom/ci/bom/requestCardDataAnalyse` | BOM `8xxx` |
| 10 | 票卡更新 | IF5A-03 | `POST /itpbom/ci/bom/requestUpdateCardData` | BOM `8xxx` |
| 11 | HCE 票卡更新结果通知 | IF5A-09 | `POST /itpbom/ci/bom/notiUpdateHceData` | BOM `8xxx` |
| 12 | 出票成功结果上报（BOM 前缀别名） | IF2A-05 | `POST /itpbom/ci/bom/notiTakeTicketResult` | **TVM `2xxx`**（见 §4-1） |
| 13 | 出票失败结果上报（BOM 前缀别名） | IF2A-06 | `POST /itpbom/ci/bom/notiTakeTicketFailResult` | **TVM `2xxx`**（见 §4-1） |
| 14 | 票卡状态查询（前缀别名） | IF1A-04 | `POST /itpbom/ci/bom/requestQrCodeStatus` | BOM `8xxx` |

## 2. BOM `8xxx` 响应码族（`api/device/bom/BomResponses.java`）

`BomResponses` 里**只定义了 3 个常量**，第 4 个 `8003` 定义在 Controller 内部（`BomOrderController.java:51` 的 `CODE_INVALID_PARAM`）。除此之外还有两处**裸字面量 `9999`**。完整表：

| 码值 | 定义位置 | 含义 | 触发条件 |
|---|---|---|---|
| `0000` | `BomResponses.CODE_SUCCESS`（`:10`） | 成功 | 所有成功分支；心跳无论落库成功与否都返它 |
| `8003` | `BomOrderController.CODE_INVALID_PARAM`（`:51`） | 非法参数 | Controller 层显式判空全部命中此码（`unwrap` 返 `null` 也算）。**注意它不在 `BomResponses` 里，`BomResponses` 自身不感知 `8003`** |
| `8006` | `BomResponses.CODE_ORDER_NO_ERROR`（`:16`） | 订单号错误 / 订单不存在 | ① `notiBusResult` / `notiTopupResult` 的 `orderNotFound()`（`:75`）；② `requestPayment` 与 `requestGetPayResult` 查不到订单（`F2fScanPayService.java:75`、`:142`） |
| `8999` | `BomResponses.CODE_FAIL`（`:13`） | 失败（通用） | 金额非数字 / 非正数、`transType=42` 缺 `adminTransType`、订单状态不允许支付、票查不到、票状态不允许退款、退款金额超额、下游（ticket-server / account-server）异常或返非 `0000`、`requestOrderResult` 查不到出票信息 |
| `9999` | **裸字面量**，`F2fBomOrderService.java:204` | 订单号错误,没有找到匹配的订单 | **只在 `requestTicketRefund` 的「订单不存在」分支**。同一语义在别处是 `8006`，这里是 `9999` —— **契约不一致，断言 MUST 按现状写 `9999`** |
| `-1` / `2999` | `TvmResponses.takeTicketResultOrderNotFound`（`:214~219`） | 订单不存在 | 只在 12、13 两条 TVM 码族别名上，见 §4-1 |
| UUID | 全局异常处理器 | 未捕获异常 | 见 README §3；本域已知成因是 Oracle / Druid 侧失败 |

**响应 JSON 的实际结构**：14 个方法**全部返回 `com.alibaba.fastjson2.JSONObject`**（不是 `CommonResult`），键序由 `BomResponses` 的 `body()` + 各专用方法逐个 `put` 决定。设计上**成功与失败两支的键集合完全相同**，失败支把业务键的值置为 JSON `null`（`BomResponses` 里 `orderNoFail` / `paymentResultFail` / `refundFail` / `cardDataAnalyseFail` / `cardDataUpdateFail` / `qrCodeStatusFail` / `orderResultFail` 都是这个形态）。**因此写断言可以固定 key 列表，只判 value**。

`BomResponses` 里两个**照搬旧实现的拼写错误键，NEVER 在用例里改正**（`BomResponses.java:130~131`）：`lastTransAmount` 落成 **`lastTransAmout`**、`lastTicketTransSeq` 落成 **`lastTikcetTransSeq`**。

---

## 3. 端点详情

### 1. 设备心跳

- **对外 URL**：`POST /itpbom/ci/bom/notiDeviceHeard`、`POST /itpbom/ci/bom/deviceHeartbeat`、`POST /itptvm/ci/bom/notiDeviceHeard`、`POST /itptvm/ci/bom/deviceHeartbeat`
- **容器内路径**：与对外 URL **完全一致**（`/itpbom/` 与 `/itptvm/` 这两条 istio rewrite 保留前缀，不像 `/fep-app/` 那样被吃掉）
- **Controller**：`BomOrderController#notiDeviceHeard`（`face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/ci/bom/BomOrderController.java:102~109`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest`，**不 unwrap bizData**（本方法唯一一个只读 form 骨架的端点）
- **响应码族**：BOM `8xxx`（`BomResponses`）

**bizData 请求字段** — 无（不解析 `bizData`，只用骨架的 `deviceId`）

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | 恒 `0000` |
| `retMsg` | String | 恒「成功」 |

**Controller 层校验与短路分支**
- `form == null` → 跳过落库，**仍返 `0000`**（`:105~107`）
- `deviceId` 为空 / 空白 → `F2fDeviceHeartbeatService.recordHeartbeat` 内部 WARN 后跳过落库（`F2fDeviceHeartbeatService.java:31~34`），**仍返 `0000`**
- **本端点没有任何失败分支**：不能靠 `retCode` 判断心跳有没有落库

**压测备注**：写 `F2F_DEVICE_STATUS`（`mergeHeartbeat`，`MERGE INTO` 语义，天然幂等）；不调支付中心；幂等键 = 渠道 + `deviceId`；`stationCode` 本端点恒传 `null`。有 `@Scheduled` 兜底：`F2fDeviceOfflineJob`（`f2f.device.offlineScanIntervalMs:60000`）会把心跳超时的在线设备批量置离线，**压测停打心跳后设备会在 1 分钟量级转离线**。

### 2. 非现金收款下单（IF8A-04，BOM 域）

- **对外 URL**：`POST /itpbom/ci/bom/requestGenNoCashOrder`、`POST /itptvm/ci/bom/requestGenNoCashOrder`
- **容器内路径**：与对外 URL **完全一致**
- **Controller**：`BomOrderController#requestGenNoCashOrder`（`.../BomOrderController.java:112~138`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → `DeviceRequests.unwrap(form, RequestGenNoCashOrderReqDTO.class)`
- **响应码族**：BOM `8xxx`（`BomResponses`）

**bizData 请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.bom.RequestGenNoCashOrderReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `transType` | String | 无（代码无 Bean Validation，空值不会 400） | BOM 交易类型，javadoc 列举 `02`/`03`/`04`/`05`/`06`/`22`/`2A`/`2B`/`42`。**代码不校验取值是否在这 9 个里**，只在 `=42` 时额外要求 `adminTransType` |
| `adminTransType` | String | 无（同上） | `transType=42` 行政处理时必填，javadoc 取值 `01~0A` |
| `operaterId` | String | 无（同上） | 操作员号（**拼写照搬，少一个 `t`**） |
| `shiftId` | String | 无（同上） | 班次号 |
| `cardId` | String | 无（同上） | 卡号，**不必填**、无任何判空 |
| `transAount` | String | 无（同上） | 交易金额，单位分（**字段名拼写错误照搬，NEVER 写成 `transAmount`**）。`amountInFen()` 用 `Long.parseLong` 解析，失败返 `null` |
| `bomOptSeq` | String | 无（同上） | BOM 侧操作流水，**幂等键** |
| `deviceId` | String | 无（同上） | 继承自 `BaseDeviceRequest`；form 侧非空时被覆盖 |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `8003` / `8999` |
| `retMsg` | String | 文案 |
| `orderNo` | String | 成功时为新建（或幂等命中的已有）订单号；失败时**键存在、值为 JSON `null`**（`BomResponses.orderNoFail`） |

**Controller 层校验与短路分支**（真正的必填全部来自这里的显式判空，DTO 无注解）
- `unwrap` 返 `null`（`bizData` 空 / 非法 JSON）→ `8003`「请求报文不能为空」（`:116~118`）
- `transType` 空白 → `8003`「transType不能为空」（`:119~121`）
- `operaterId` 空白 → `8003`「operaterId不能为空」（`:122~124`）
- `shiftId` 空白 → `8003`「shiftId不能为空」（`:125~127`）
- `transAount` 空白 → `8003`「transAount不能为空」（`:128~130`）
- `bomOptSeq` 空白 → `8003`「bomOptSeq不能为空」（`:131~133`）
- `deviceId` 空白 → `8003`「deviceId不能为空」（`:134~136`）
- 判空顺序即上表顺序，**多字段同时缺失时只会报第一个**

**Service 层追加分支**（`F2fBomOrderService.createNoCashOrder`，`F2fBomOrderService.java:95~131`）
- `transAount` 非数字 → `8999`「transAount不是合法数字」
- `transAount <= 0` → `8999`「transAount必须为正数」
- `transType=42` 且 `adminTransType` 空 → `8999`「transType=42时adminTransType不能为空」
- 撞唯一索引（渠道 + `deviceId` + `bomOptSeq`）→ 走 `F2fDuplicateKey.isConflict` 回查 `selectByDeviceSeq`，**返已有订单号 + `0000`**；回查不到 → `8999`「失败」

**压测备注**：写 `F2F_ORDER`（`BIZ_TYPE='04'`、`ORDER_STATUS='CREATED'`、`CHANNEL=BOM`、`ACTIVATE_FLAG='0'`）；**不调支付中心**；幂等键 = `CHANNEL + DEVICE_ID + DEVICE_SEQ`（即 `bomOptSeq`），靠唯一索引 + cause 链兜底，**同一 `bomOptSeq` 重复打必返同一个 `orderNo`，压测造数 MUST 每笔换 `bomOptSeq`**；本服务类**刻意不带 `@Transactional`**（`F2fBomOrderService.java:32`）。`@Scheduled` 兜底：`F2fOrderExpireJob`（`f2f.order.expireScanIntervalMs:30000`）会把超时未支付的单推 `EXPIRED`，**压测残留的 `CREATED` 单会被自动过期**。

### 3. 扫码支付（IF8A-05，BOM 域）

- **对外 URL**：`POST /itpbom/ci/bom/requestPayment`、`POST /itptvm/ci/bom/requestPayment`
- **容器内路径**：与对外 URL **完全一致**
- **Controller**：`BomOrderController#requestPayment`（`.../BomOrderController.java:141~152`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → `DeviceRequests.unwrap(form, RequestPaymentReqDTO.class)`（**复用 TVM 包的 DTO** `api/device/tvm/RequestPaymentReqDTO`）
- **响应码族**：BOM `8xxx`（`BomResponses`）

**bizData 请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.tvm.RequestPaymentReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无（代码无 Bean Validation，空值不会 400） | 订单号，取自 #2 的应答 |
| `paymentCode` | String | 无（同上） | 乘客付款码（条码 / 二维码内容）。**`toString()` 恒打 `***`**（`RequestPaymentReqDTO.java:41~47`），压测排障时日志里看不到真值 |
| `paymentVendor` | String | 无（同上） | 支付渠道（微信 / 支付宝等） |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `8003` / `8006` / `8999` |
| `retMsg` | String | 文案 |
| `paymentResult` | String | `ORDERED` / `PROCESSING` / `SUCCESS` / `FAILED`（`api/device/PaymentResult.java`）；失败支为 JSON `null` |
| `paymentResultDesc` | String | 与 `msg` 同值（`BomResponses.paymentResult` 把同一个 `msg` 同时塞进这两个键，`:42`） |
| `msg` | String | 同上 |

**Controller 层校验与短路分支**
- `unwrap` 返 `null` **或** `orderNo` 空白 → `8003`「orderNo不能为空」（`:145~147`，**两种成因同一个码同一个文案，无法区分**）
- `paymentVendor` 空白 → `8003`「paymentVendor不能为空」（`:148~150`）
- **`paymentCode` 没有判空**：为空时会走到 `PayCenterMessageFactory.buildPayRequest` 抛 `IllegalArgumentException`，被接住后返 `0000` + `paymentResult=FAILED`（`F2fScanPayService.java:109~113`）—— **`retCode` 是 `0000`，判失败 MUST 看 `paymentResult`**

**Service 层分支**（`F2fScanPayService.requestPayment`，`F2fScanPayService.java:70~135`）
- 订单不存在 → `8006`「订单号错误,没有找到匹配的订单」
- 订单已在 `PAID` 类状态 → `0000` + `SUCCESS`（幂等）
- 订单已在 `PAY_FAILED` / `EXPIRED` / `CANCELED` → `0000` + `FAILED`
- 订单不在 `CREATED` / `PAYING`（`F2fOrderStatus.PENDING`）→ `8999`「订单状态不允许支付」
- `ORDER_AMOUNT` 为 `null` 或 `<= 0` → `8999`「订单金额异常，请联系工作人员」
- 支付中心同步返成功 → `0000` + `SUCCESS`；明确拒绝 → `0000` + `FAILED`；无结果 / 已受理 → `0000` + `ORDERED`

**压测备注**：写 `F2F_PAYMENT`（每次 `insert` 一行，`ATTEMPT_NO` 按 `selectMaxAttemptNo` 递增）+ 回写 `F2F_ORDER.ORDER_STATUS`；**调支付中心外部网关**（`F2fPayCenterFlow.submit`，压测这条链路会把真实压力打到支付中心，**MUST 先与支付中心侧约定，NEVER 直接放量**）；幂等键 = `ORDER_NO + ATTEMPT_NO`，撞唯一索引时走 `F2fDuplicateKey.isConflict`（`F2fScanPayService.java:190~192` 有「NEVER 退回 `catch (DuplicateKeyException)`」的告示注释）；该方法**刻意在事务外**（`:98` 注释「必须在事务外：中间那次 `execute` 是网络调用」）。

### 4. 查询支付结果（IF8A-06，BOM 域）

- **对外 URL**：`POST /itpbom/ci/bom/requestGetPayResult`、`POST /itpbom/ci/bom/requestPayResult`、`POST /itptvm/ci/bom/requestGetPayResult`、`POST /itptvm/ci/bom/requestPayResult`
- **容器内路径**：与对外 URL **完全一致**
- **Controller**：`BomOrderController#requestGetPayResult`（`.../BomOrderController.java:162~170`；javadoc `:154~161`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → `DeviceRequests.unwrap(form, RequestPayResultReqDTO.class)`（TVM 包 DTO）
- **响应码族**：BOM `8xxx`（`BomResponses`）。**`requestPayResult` 这条别名与 TVM 侧同名端点撞名，但响应族仍是 `8xxx`**，javadoc `:160` 原文：「**响应族仍是 BOM 的 8xxx，NEVER 因为共用 URL 名就改成 TVM 的 2xxx。**」

**bizData 请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.tvm.RequestPayResultReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无（代码无 Bean Validation，空值不会 400） | 订单号 |
| `userId` | String | 无（同上） | 用户标识。**Controller 只把 `orderNo` 传给 service（`:169`），`userId` 被完全忽略**，压测可省 |

**响应字段**：与 #3 完全相同（`retCode` / `retMsg` / `paymentResult` / `paymentResultDesc` / `msg`）

**Controller 层校验与短路分支**
- `unwrap` 返 `null` **或** `orderNo` 空白 → `8003`「orderNo不能为空」（`:166~168`）

**Service 层分支**（`F2fScanPayService.queryPayResult`，`:138~157`）
- 订单不存在 → `8006`
- `PAID` 类 → `0000` + `SUCCESS`；失败终态 → `0000` + `FAILED`
- 不在 `CREATED` / `PAYING` 的其他状态 → `0000` + `PROCESSING`
- 在 `CREATED` / `PAYING` → 主动向支付中心查（`resolve`）后收口

**压测备注**：命中前 4 个分支时**纯读 `F2F_ORDER`**；落到 `resolve` 时**会调支付中心查询接口**并可能回写 `F2F_PAYMENT` / `F2F_ORDER`。**这是最容易被压测放大的端点**：设备轮询 + 订单停在 `PAYING` ⇒ 每次请求都打一次支付中心。压测脚本 MUST 控制轮询频率或先把订单造成终态。

### 5. 业务操作结果通知（IF2A-08）

- **对外 URL**：`POST /itpbom/ci/bom/notiBusResult`、`POST /itptvm/ci/bom/notiBusResult`
- **容器内路径**：与对外 URL **完全一致**
- **Controller**：`BomOrderController#notiBusResult`（`.../BomOrderController.java:173~184`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → `DeviceRequests.unwrap(form, NotiBusResultReqDTO.class)`
- **响应码族**：BOM `8xxx`（`BomResponses`）

**bizData 请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.bom.NotiBusResultReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `optResult` | String | 无（代码无 Bean Validation，空值不会 400） | 业务操作结果。**只有字面量 `SUCCESS` / `FAILED` 被识别**（`isSuccess()` / `isFailed()`，`:19~25`），其它取值静默忽略（只留上报、不动状态、不退款） |
| `orderNo` | String | 无（同上） | 订单号 |
| `optResultDesc` | String | 无（同上） | 结果描述 |
| `tranDate` | String | 无（同上） | 交易日期（**拼写照搬，不是 `transDate`**），落 `F2F_RESULT_REPORT.REPORT_TMS` |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `8003` / `8006` |
| `retMsg` | String | 文案 |

**Controller 层校验与短路分支**
- `unwrap` 返 `null` **或** `orderNo` 空白 → `8003`「orderNo不能为空」（`:177~179`）
- `optResult` 空白 → `8003`「optResult不能为空」（`:180~182`）

**Service 层分支**（`F2fBomOrderService.receiveBusResult`，`F2fBomOrderService.java:134~166`）
- 订单不存在 → `8006`「订单号错误,没有找到匹配的订单」
- 上报重复到达（撞 `F2F_RESULT_REPORT` 唯一索引）→ `0000`（幂等）
- 订单不在 `PAID` / `FULFILLED` / `FULFILL_FAILED` → **report-only**：只落上报行，不动状态不退款，**仍返 `0000`**
- `optResult=SUCCESS` → CAS 推 `PAID → FULFILLED`，返 `0000`
- `optResult=FAILED` → CAS 推 `PAID → FULFILL_FAILED` + **提交原单全额退款**，返 `0000`
- **本端点除上面三个错误码外恒返 `0000`**：退款成不成功在应答里看不出来，MUST 查库 / 查日志

**压测备注**：写 `F2F_RESULT_REPORT`（`REPORT_TYPE='BOM_BIZ_RESULT'`、`PROCESSED='1'`）+ CAS 更新 `F2F_ORDER.ORDER_STATUS`；`FAILED` 分支经 `F2fRefundService` 写 `F2F_REFUND` 并**可能调支付中心退款接口**；幂等键 = `F2F_RESULT_REPORT` 上的唯一索引（`REPORT_TYPE + ORDER_NO` 族），走 `F2fDuplicateKey.isConflict`。`@Scheduled` 兜底：`F2fRefundReconcileJob`（`f2f.refund.scanIntervalMs:60000`）回查退款终态。

### 6. 充值结果通知（IF2A-09）

- **对外 URL**：`POST /itpbom/ci/bom/notiTopupResult`、`POST /itptvm/ci/bom/notiTopupResult`
- **容器内路径**：与对外 URL **完全一致**
- **Controller**：`BomOrderController#notiTopupResult`（`.../BomOrderController.java:187~199`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → `DeviceRequests.unwrap(form, NotiTopupResultReqDTO.class)`
- **响应码族**：BOM `8xxx`（`BomResponses`）

**bizData 请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.bom.NotiTopupResultReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无（代码无 Bean Validation，空值不会 400） | 订单号 |
| `topupStatus` | String | 无（同上） | `00` 充值成功；`01` 充值失败需退款（`STATUS_OK` / `STATUS_FAILED`，`:9~12`）。**其它取值既不算成功也不退款** |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `8003` / `8006` |
| `retMsg` | String | 文案 |

**Controller 层校验与短路分支**
- `unwrap` 返 `null` **或** `orderNo` 空白 → `8003`「orderNo不能为空」（`:191~193`）
- `topupStatus` 空白 → `8003`「topupStatus不能为空」（`:194~196`）
- Controller 把 `request.needRefund()`（`topupStatus=='01'`）与 `request.toString()`（原始报文）一并传给 service（`:197~198`）

**Service 层分支**（`F2fTopupResultService.receiveBomTopupResult`，`F2fTopupResultService.java:127~`）
- 订单不存在 → `8006`
- 上报重复到达 → `0000`（幂等）
- 成功 → CAS 推 `PAID → FULFILLED`
- 需退款 → CAS 推 `PAID → FULFILL_FAILED` + 提交退款

**压测备注**：写 `F2F_RESULT_REPORT`（`TOPUP_OK` / `TOPUP_FAIL`）+ CAS 更新 `F2F_ORDER`；退款分支经 `F2fRefundService` 写 `F2F_REFUND` 并可能调支付中心；幂等键同 #5。`NotiTopupResultReqDTO.toString()` 只输出 `orderNo` / `topupStatus` / `deviceId`，落库的 `RAW_BODY` 就是这一串（**不是原始 `bizData`**）。

### 7. 单程票交易查询

- **对外 URL**：`POST /itpbom/ci/bom/requestOrderResult`、`POST /itptvm/ci/bom/requestOrderResult`
- **容器内路径**：与对外 URL **完全一致**
- **Controller**：`BomOrderController#requestOrderResult`（`.../BomOrderController.java:202~213`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → `DeviceRequests.unwrap(form, RequestOrderResultReqDTO.class)`
- **响应码族**：BOM `8xxx`（`BomResponses`）

**bizData 请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.bom.RequestOrderResultReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `ticketLogicNum` | String | 无（代码无 Bean Validation，空值不会 400） | 票逻辑卡号。service 侧先过 `F2fLogicCardNo.normalize`，**但下游 mapper 是精确等值比较，小写仍查不到，见 §4-4** |
| `transDate` | String | 无（同上） | 交易日期，与 `ticketLogicNum` 一起做联合查询键 |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `8003` / `8999` |
| `retMsg` | String | 文案 |
| `paymentResult` | String | `SUCCESS` / `FAILED` / `UNPAID`（按订单状态映射，`F2fBomOrderService.java:183~184`）；失败支 JSON `null` |
| `paymentResultDesc` | String | 「支付成功」/「支付失败」/「未支付」 |
| `orderNo` | String | 票所属订单号 |
| `transDate` | String | 取自 `F2F_TICKET.TRANS_DATE`（**不是请求里的值**） |
| `transAmount` | String | 单票金额，`Long` 被 `String.valueOf` 转成字符串（`:181`） |
| `paymentChannelCode` | String | 票上的 `PAY_CHANNEL_CODE`，为空时回落到最后一次支付尝试的渠道码，**仍可能为 `null`** |

**Controller 层校验与短路分支**
- `unwrap` 返 `null` **或** `ticketLogicNum` 空白 → `8003`「ticketLogicNum不能为空」（`:206~208`）
- `transDate` 空白 → `8003`「transDate不能为空」（`:209~211`）

**Service 层分支**（`F2fBomOrderService.requestOrderResult`，`:168~191`）
- 票查不到 → `8999`「没有查找到出票信息」（**小写 `ticketLogicNum` 落在这里**）
- 票在但订单缺失（数据不一致）→ `8999`「无对应的订单信息」+ ERROR 日志

**压测备注**：**纯读**（`F2F_TICKET` + `F2F_ORDER` + 可能一次 `F2F_PAYMENT.selectLastAttempt`）；不写库、不调支付中心；无幂等问题，适合做只读吞吐基线。

### 8. 单程票退款

- **对外 URL**：`POST /itpbom/ci/bom/requestTicketRefund`、`POST /itptvm/ci/bom/requestTicketRefund`
- **容器内路径**：与对外 URL **完全一致**
- **Controller**：`BomOrderController#requestTicketRefund`（`.../BomOrderController.java:216~233`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → `DeviceRequests.unwrap(form, RequestTicketRefundReqDTO.class)`
- **响应码族**：BOM `8xxx`（`BomResponses`），**外加一处裸 `9999`**

**bizData 请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.bom.RequestTicketRefundReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无（代码无 Bean Validation，空值不会 400） | 订单号 |
| `ticketLogicNum` | String | 无（同上） | 票逻辑卡号，service 侧过 `F2fLogicCardNo.normalize` |
| `transAmount` | String | 无（同上） | 退款金额，单位分。`amountInFen()` 解析失败返 `null` |
| `transType` | String | 无（同上） | 交易类型，原样传进 `RefundCommand` |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `8003` / `8999` / **`9999`**（订单不存在这一支） |
| `retMsg` | String | 文案 |
| `refundResult` | String | `SUCCESS`（退款已终态成功）/ `PROCESSING`（已提交待收口）；失败支 JSON `null` |
| `refundResultDesc` | String | 「退款成功」/「退款处理中」 |
| `refundNo` | String | 退款流水号 |

**Controller 层校验与短路分支**
- `unwrap` 返 `null` **或** `orderNo` 空白 → `8003`「orderNo不能为空」（`:220~222`）
- `ticketLogicNum` 空白 → `8003`「ticketLogicNum不能为空」（`:223~225`）
- `transAmount` 空白 → `8003`「transAmount不能为空」（`:226~228`）
- `transType` 空白 → `8003`「transType不能为空」（`:229~231`）

**Service 层分支**（`F2fBomOrderService.requestTicketRefund`，`:194~251`）
- 金额非数字或 `<= 0` → `8999`「transAmount不是合法的正整数金额」
- **订单不存在 → `9999`「订单号错误,没有找到匹配的订单」**（`:204`，**全域唯一一处裸 `9999`，NEVER 在用例里改成 `8006`**）
- 订单不在 `PAID` 类状态 → `8999`「订单未支付，不可退款」
- 票查不到 → `8999`「没有查找到出票信息」
- 票状态不在 `ISSUED` / `FAULT` → `8999`「该票已退款或状态不允许退款」
- 退款金额 > `ORDER_AMOUNT - REFUND_AMOUNT` → `8999`「退款金额不能大于订单金额」
- `F2fRefundService` 拒绝 → `8999` + 拒绝原因原文

**压测备注**：写 `F2F_REFUND`（经 `F2fRefundService`）+ `F2F_TICKET.REFUND_NO` 与 `TICKET_STATUS`（CAS `ISSUED|FAULT → REFUNDING`）+ 间接影响 `F2F_ORDER`；**调支付中心退款接口**；幂等键 = `F2fRefundService` 的退款业务键（`orderNo` + `ticketLogicNum` + `SOURCE_BOM_ORIGINAL`），重复提交返 `alreadyExisted` 且票状态 CAS 会 0 行并打「疑似重复退款」WARN。`@Scheduled` 兜底：`F2fRefundReconcileJob`。**压测这条链路会产生真实退款请求，MUST 在隔离环境做。**

### 9. 票卡分析（IF5A-01）

- **对外 URL**：`POST /itpbom/ci/bom/requestCardDataAnalyse`、`POST /itptvm/ci/bom/requestCardDataAnalyse`
- **容器内路径**：与对外 URL **完全一致**
- **Controller**：`BomOrderController#requestCardDataAnalyse`（`.../BomOrderController.java:236~247`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → `DeviceRequests.unwrap(form, RequestCardDataAnalyseReqDTO.class)`
- **响应码族**：BOM `8xxx`（`BomResponses`），**下游 ticket-server 的错误码会被原样透传到 `retCode`**

**bizData 请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.bom.RequestCardDataAnalyseReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `msisdn` | String | 无（代码无 Bean Validation，空值不会 400） | 手机号 |
| `cardId` | String | 无（同上） | 卡号 |
| `updateType` | String | 无（同上） | 更新类型 |

**响应字段**（`BomResponses.cardDataAnalyse` / `cardDataAnalyseFail`，共 `retCode`+`retMsg`+13 个业务键）

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `8003` / `8999` / **下游原码** |
| `retMsg` | String | 文案 |
| `providerId` | String | 下游回显 |
| `cardIssueDate` | String | 发卡日期 |
| `msisdn` | String | 手机号 |
| `cardId` | String | 卡号 |
| `cardStatus` | String | 卡状态 |
| `lastLineCode` | String | 上次线路码 |
| `lastStationCode` | String | 上次车站码 |
| `lastUpdateDate` | String | 上次更新日期 |
| **`lastTransAmout`** | String | 上次交易金额。**键名少一个 `n`，照搬旧实现，NEVER 改正** |
| **`lastTikcetTransSeq`** | String | 上次票交易流水。**键名 `Ticket` 拼成 `Tikcet`，照搬旧实现，NEVER 改正** |
| `adviceOpt` | Object | 建议操作（**类型是 `Object`，可能是对象 / 数组 / 标量**，断言 MUST 容忍） |
| `managerCode` | String | 管理码 |
| `transAmount` | String | 交易金额（这个键**拼写正确**，与上面 `lastTransAmout` 并存） |

**Controller 层校验与短路分支**
- `unwrap` 返 `null` **或** `cardId` 空白 → `8003`「cardId不能为空」（`:240~242`）
- `updateType` 空白 → `8003`「updateType不能为空」（`:243~245`）
- **`msisdn` 没有判空**

**Service 层分支**（`F2fHceService.requestCardDataAnalyse`，`F2fHceService.java:50~79`）
- 调 `TicketClient.requestCardDataAnalyse` 抛异常 → `8999`「票卡分析失败:下游异常」
- 下游返 `null` → `8999`「票卡分析失败」
- 下游 `retCode != 0000` → **原样透传下游 `retCode` + `retMsg`**（因此 `retCode` 可能既不是 `8xxx` 也不是 `0000`）

**压测备注**：**本端点不落库**，纯透传 ticket-server（`service.ticket.url`）；不调支付中心；无幂等键。**压测它等于压 ticket-server**，MUST 把 ticket-server 一并纳入容量评估。

### 10. 票卡更新（IF5A-03）

- **对外 URL**：`POST /itpbom/ci/bom/requestUpdateCardData`、`POST /itptvm/ci/bom/requestUpdateCardData`
- **容器内路径**：与对外 URL **完全一致**
- **Controller**：`BomOrderController#requestUpdateCardData`（`.../BomOrderController.java:250~267`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → `DeviceRequests.unwrap(form, RequestCardDataUpdateReqDTO.class)`
- **响应码族**：BOM `8xxx`（`BomResponses`），下游码原样透传

**bizData 请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.bom.RequestCardDataUpdateReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `cardId` | String | 无（代码无 Bean Validation，空值不会 400） | 卡号 |
| `updateType` | String | 无（同上） | 更新类型。**Controller 不判空**（与 #9 相反） |
| `adviceOpt` | String | 无（同上） | 建议操作（这里是 `String`，与 #9 应答里的 `Object` 不同） |
| `operaterId` | String | 无（同上） | 操作员号（**拼写照搬，少一个 `t`**，DTO javadoc `:14` 已注明） |
| `updateStationCode` | String | 无（同上） | 更新车站编码 |
| `optDate` | String | 无（同上） | 操作日期 |
| `transAmount` | String | 无（同上） | 交易金额 |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `8003` / `8999` / 下游原码 |
| `retMsg` | String | 文案 |
| `cardData` | String | 更新后的卡数据；失败支**键存在、值为 JSON `null`** |

**Controller 层校验与短路分支**
- `unwrap` 返 `null` **或** `cardId` 空白 → `8003`「cardId不能为空」（`:254~256`）
- `adviceOpt` 空白 → `8003`「adviceOpt不能为空」（`:257~259`）
- `updateStationCode` 空白 → `8003`「updateStationCode不能为空」（`:260~262`）
- `optDate` 空白 → `8003`「optDate不能为空」（`:263~265`）
- **`updateType` / `operaterId` / `transAmount` 均不判空**

**Service 层分支**（`F2fHceService.requestUpdateCardData`，`:82~113`）：与 #9 同形，三档失败分别 `8999`「票卡更新失败:下游异常」/ `8999`「票卡更新失败」/ 下游原码透传。

**压测备注**：不落库，纯透传 ticket-server；不调支付中心；无幂等键。

### 11. HCE 票卡更新结果通知（IF5A-09）

- **对外 URL**：`POST /itpbom/ci/bom/notiUpdateHceData`、`POST /itptvm/ci/bom/notiUpdateHceData`
- **容器内路径**：与对外 URL **完全一致**
- **Controller**：`BomOrderController#notiUpdateHceData`（`.../BomOrderController.java:270~281`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → `DeviceRequests.unwrap(form, NotiUpdateHceDataReqDTO.class)`
- **响应码族**：BOM `8xxx`（`BomResponses`）

**bizData 请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.bom.NotiUpdateHceDataReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `cardId` | String | 无（代码无 Bean Validation，空值不会 400） | 卡号 |
| `hceData` | String | 无（同上） | HCE 卡数据。**`toString()` 只输出长度不输出内容**（`:38~45`），压测排障时日志里拿不到真值 |
| `adviceOpt` | String | 无（同上） | 建议操作 |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `8003` |
| `retMsg` | String | 文案 |

**Controller 层校验与短路分支**
- `unwrap` 返 `null` **或** `cardId` 空白 → `8003`「cardId不能为空」（`:274~276`）
- `hceData` 空白 → `8003`「hceData不能为空」（`:277~279`）
- Controller 的日志只打 `deviceId`、**不打整个 request**（`:272`，与其余端点的 `form={}` 不同）

**Service 层分支**（`F2fHceService.receiveHceUpdateResult`，`F2fHceService.java:116~140`）
- **无论回写成功还是失败，一律返 `0000`**（`:121`、`:140`）：调 `AccountClient.updateHceData` 失败时只把 `F2F_RESULT_REPORT` 的 `OPT_RESULT` 置 `FAILED`（`:132`、`:138`），应答仍是成功。**判成败 MUST 查 `F2F_RESULT_REPORT`，NEVER 看 `retCode`**

**压测备注**：写 `F2F_RESULT_REPORT`（`REPORT_TYPE='BOM_BIZ_RESULT'`，与 #5 共用同一个 `REPORT_TYPE`）+ 调 **account-server** `updateHceData`；不调支付中心；幂等键 = `F2F_RESULT_REPORT` 唯一索引。

### 12. 出票成功结果上报（IF2A-05，BOM 前缀别名）

- **对外 URL**：`POST /itpbom/ci/bom/notiTakeTicketResult`、`POST /itptvm/ci/bom/notiTakeTicketResult`
- **容器内路径**：与对外 URL **完全一致**
- **Controller**：`BomOrderController#notiTakeTicketResult`（`.../BomOrderController.java:284~292`；javadoc `:283`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → `DeviceRequests.unwrap(form, NotiTakeTicketResultReqDTO.class)`（**TVM 包 DTO**）
- **响应码族**：**TVM `2xxx`（`TvmResponses`），不是 BOM 的 `8xxx`** —— 详见 §4-1，断言 MUST 按现状写

**bizData 请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.tvm.NotiTakeTicketResultReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无（代码无 Bean Validation，空值不会 400） | 订单号 |
| `actualTakeTicketNum` | String | 无（同上） | 实际出票张数。`actualNum()` 解析失败返 `null` |
| `takeTickeDate` | String | 无（同上） | 出票时间（**拼写照搬，不是 `takeTicketDate`**） |
| `ticketList` | `List<TicketInfo>` | 无（同上） | 本次出票明细，**可为空列表**（设备只报张数不报明细时） |
| `ticketList[].ticketLogicNum` | String | 无 | 票逻辑卡号 |
| `ticketList[].transDate` | String | 无 | 交易日期，`yyyyMMddHHmmss` 或 `yyyyMMdd`，**原样落库不做归一**（`TicketInfo.java:9`） |
| `ticketList[].transAmount` | String | 无 | 单票金额（分），`priceInFen()` 失败返 `null` |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000`（成功）/ `2002`（非法参数）/ **`2999` 或 `-1`**（订单不存在，取决于 `providerId`，见下） |
| `retMsg` | String | 文案 |

**Controller 层校验与短路分支**
- `unwrap` 返 `null` **或** `orderNo` 空白 → **`TvmResponses.fail(DeviceRetCode.INVALID_PARAM, "orderNo不能为空")` ⇒ `retCode=2002`**（`:288~290`），**不是 `8003`**
- 渠道归属：`channelOf(providerId)`（`:322~325`），`providerId` 非法时**兜底成 BOM**

**Service 层分支**（`F2fTicketIssueService.receiveTakeTicketResult`，`F2fTicketIssueService.java:79~111`）
- `actualNum()` 解析不出 → `TvmResponses.fail(DeviceRetCode.INVALID_PARAM)` ⇒ `2002`
- 订单不存在 → `TvmResponses.takeTicketResultOrderNotFound(providerId)`（`TvmResponses.java:214~219`）：**`providerId` 等于 BOM 渠道码时返 `2999`，否则返 `-1`**。`-1` 不在 `DeviceRetCode` 枚举里，是裸字面量，**用例 MUST 按 `providerId` 分两组断言**
- 上报重复到达 → `TvmResponses.success()` ⇒ `0000`（幂等）
- 正常 → 批量写票 + CAS 推 `PAID → FULFILLED` + 入队 APP 通知，返 `0000`

**压测备注**：写 `F2F_TICKET`（`batchInsert`，撞唯一索引时降级为逐条 `insert`，`:238` / `:246`）+ CAS 更新 `F2F_ORDER` + 写 `F2F_RESULT_REPORT`（`TAKE_TICKET_OK`）+ 经 `F2fNotifyService.enqueue` 落通知任务表；**本端点自身不调支付中心**（出票失败那条会退款）。幂等键 = `F2F_RESULT_REPORT` 唯一索引（首报判定）+ `F2F_TICKET` 的逻辑卡号唯一索引。`@Scheduled` 兜底两个：`F2fNotifyJob`（`f2f.notify.scanIntervalMs:30000`，投递 APP 通知）与 `F2fReportRecoveryJob`（`f2f.report.scanIntervalMs:120000`，出票上报补偿）。

### 13. 出票失败结果上报（IF2A-06，BOM 前缀别名）

- **对外 URL**：`POST /itpbom/ci/bom/notiTakeTicketFailResult`、`POST /itptvm/ci/bom/notiTakeTicketFailResult`
- **容器内路径**：与对外 URL **完全一致**
- **Controller**：`BomOrderController#notiTakeTicketFailResult`（`.../BomOrderController.java:295~304`；javadoc `:294`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → `DeviceRequests.unwrap(form, NotiTakeTicketFailResultReqDTO.class)`（**TVM 包 DTO**）
- **响应码族**：**TVM `2xxx`（`TvmResponses`）**，见 §4-1

**bizData 请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.tvm.NotiTakeTicketFailResultReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无（代码无 Bean Validation，空值不会 400） | 订单号 |
| `actualTakeTicketNum` | String | 无（同上） | 实际出票张数，**可为 `0`**（完全没出票）。`actualNum()` 解析失败返 `null`，DTO javadoc `:32` 明确「**NEVER 把解析失败当成 0，那会退全款**」 |
| `faultOccurDate` | String | 无（同上） | 故障发生时间 |
| `faultSlipSeq` | String | 无（同上） | TVM 打印的故障单号，乘客凭此到 BOM 处理，落 `F2F_RESULT_REPORT.FAULT_SLIP_SEQ` |
| `errorCode` | String | 无（同上） | 设备侧错误码 |
| `errorMessage` | String | 无（同上） | 设备侧错误描述 |
| `ticketList` | `List<TicketInfo>` | 无（同上） | 已成功出票的明细，**可为空列表**（一张都没出）。元素字段同 #12 |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `2002` / **`2999`**（订单不存在，`TvmResponses.takeTicketFailResultOrderNotFound()`，`TvmResponses.java:222~227`，**恒 `2999`、不看 `providerId`**，与 #12 不同） |
| `retMsg` | String | 文案 |

**Controller 层校验与短路分支**
- `unwrap` 返 `null` **或** `orderNo` 空白 → `TvmResponses.fail(DeviceRetCode.INVALID_PARAM, "orderNo不能为空")` ⇒ **`2002`**（`:300~302`）

**Service 层分支**（`F2fTicketIssueService.receiveTakeTicketFailResult`，`:115~151`）
- `actualNum()` 解析不出 → `2002`
- 订单不存在 → `2999`
- 上报重复 → `0000`（幂等）
- 正常 → 写已出票明细 + CAS 推 `PAID → FULFILL_FAILED` + **按未出票张数提交差额/全额退款** + 入队 APP 通知，返 `0000`

**压测备注**：写 `F2F_TICKET` / `F2F_ORDER` / `F2F_RESULT_REPORT`（`TAKE_TICKET_FAIL`）+ `F2F_REFUND`；**会调支付中心退款接口**；幂等键同 #12。`@Scheduled` 兜底：`F2fNotifyJob` / `F2fReportRecoveryJob` / `F2fRefundReconcileJob`。**这是本文件里唯一「报一次就会产生退款」的上报端点，压测 MUST 隔离环境。**

### 14. 票卡状态查询（IF1A-04，前缀别名）

- **对外 URL**：`POST /itpbom/ci/bom/requestQrCodeStatus`、`POST /itptvm/ci/bom/requestQrCodeStatus`
- **容器内路径**：与对外 URL **完全一致**
- **Controller**：`BomOrderController#requestQrCodeStatus`（`.../BomOrderController.java:311~319`；javadoc `:306~310`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → `DeviceRequests.unwrap(form, RequestQrCodeStatusReqDTO.class)`（**BOM 包自己的同名类**，与 `fep-dev-server` 那个**字段同形但不是同一个类**，`RequestQrCodeStatusReqDTO.java:8~9` 明确「NEVER 共享父类或互相复用」）
- **响应码族**：BOM `8xxx`（`BomResponses`），下游 ticket-server 的码会原样透传

**bizData 请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.bom.RequestQrCodeStatusReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `itpUserId` | String | 无（代码无 Bean Validation，空值不会 400） | ITP 用户号。**原样透传给下游、不做十六进制换算**，且下游只按 `cardId` 查库、`itpUserId` 仅被回显（`F2fQrCodeStatusService.java:20~27` 的口径①，**NEVER 改**） |
| `cardId` | String | 无（同上） | 卡号，**下游唯一的实际查询键** |
| `trxType` | String | 无（同上） | 交易类型。**Service 完全没读它**（`F2fQrCodeStatusService.java:45~47` 只映射 `cardId` + `itpUserId`），压测可省 |
| `ticketTransSeq` | String | 无（同上） | 票交易流水。**同上，未被读取** |
| `qrType` | String | 无（同上） | 二维码类型。**同上，未被读取** |

**响应字段**（`BomResponses.qrCodeStatus`，4 个业务键与 `/itpagm/ci/agm/requestQrCodeStatus` 逐字一致）

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | `0000` / `8003` / `8999` / 下游原码 |
| `retMsg` | String | 文案 |
| `itpUserId` | String | **回显请求里的 `itpUserId`**（不是下游返的） |
| `cardId` | String | 下游返的 `cardId` |
| `lastTicketStatus` | String | 下游返的 `status` |
| `lastHandleDateTime` | String | 下游返的 `lastTxnTime` |

**Controller 层校验与短路分支**
- `unwrap` 返 `null` **或** `cardId` 空白 → `8003`「cardId不能为空」（`:315~317`）
- **`itpUserId` 不判空**

**Service 层分支**（`F2fQrCodeStatusService.requestQrCodeStatus`，`:44~67`）
- `TicketClient.queryQrCodeStatus` 抛异常 → `8999`「票卡状态查询失败:下游异常」
- 下游返 `null` → `8999`「票卡状态查询失败」
- 下游 `retCode != 0000` → 原样透传下游 `retCode` + `retMsg`

**压测备注**：**不落库、不带事务**（`:26` 明确「刻意不落库、不带 `@Transactional`」），纯透传 ticket-server；不调支付中心；无幂等键。归属见 §4-3。

---

## 4. 已知坑与测试注意点

### 4-1. 两条出票上报别名故意返 TVM 的 `2xxx`、不返 BOM 的 `8xxx`

`notiTakeTicketResult`（`:284~292`）与 `notiTakeTicketFailResult`（`:295~304`）挂在 BOM 前缀下，**用的却是 `TvmResponses` 与 `DeviceRetCode`**：参数为空返 `2002`、订单不存在返 `2999`（或 `-1`）、成功返 `0000`。这是**有意为之**，因为它们与 `/itptvm/ci/tvm/notiTakeTicketResult` 共用同一个 `F2fTicketIssueService` 实现，设备解析的是同一套码表。

关于「禁止修正」的注释，**MUST 按代码原文引用，NEVER 夸大**：

- **这两条端点自身的 javadoc 只声明了「同一响应族」，没有 `NEVER` 字样**。原文：
  - `BomOrderController.java:283`：`/** IF2A-05 出票成功结果上报的 BOM 前缀别名，与 {@code /itptvm/ci/tvm/notiTakeTicketResult} 同一实现、同一响应族（2xxx）。 */`
  - `BomOrderController.java:294`：`/** IF2A-06 出票失败结果上报的 BOM 前缀别名，成因与上一条相同（ADR-D97）。 */`
- **带显式禁令的那句在同文件另一个方法上**，是**反方向**的同型约束（BOM 端点共用 TVM 的 URL 名、但响应族不许跟着改），原文 `BomOrderController.java:160`：
  `* **响应族仍是 BOM 的 8xxx，NEVER 因为共用 URL 名就改成 TVM 的 2xxx。**`
  该 javadoc 属 `requestGetPayResult`（`:154~161`），**不属于这两条出票上报别名**。

结论：**断言一律按现状写 `2xxx`**（两条别名）与 `8xxx`（其余 12 条）；判据是「哪个 `*Responses` 类」，**NEVER 按 URL 前缀推断码族**。

### 4-2. 这两条上报同时也是 `TvmOrderController` 上同名端点的 BOM 前缀别名

同一业务语义存在**两套 URL、四条以上可打路径**（TVM 前缀那两条在 05a，本文件只覆盖 BOM 前缀这两条）：

- 本文件：`/itpbom/ci/bom/notiTakeTicketResult`、`/itptvm/ci/bom/notiTakeTicketResult`（类级双前缀 × `ci/bom` 段）
- 05a：`TvmOrderController` 的 `.../ci/tvm/notiTakeTicketResult` 族

两侧最终落到同一个 `F2fTicketIssueService.receiveTakeTicketResult`，**落库与退款效果完全相同**。因此：

- **每条用例 MUST 在标题或标签里写明走的是哪条 URL**，否则复现时分不清；
- **NEVER 把两侧用例算成两份覆盖率**（同一实现）；
- 但 **MUST 各自保留至少一条连通性用例**，因为它们是不同的 handler 方法，任何一侧的 `@PostMapping` 被误删都只影响自己那一侧（ADR-D97 的成因正是 BOM 设备把报文打到了 `/itpbom/ci/bom/` 而当时没有 handler，404 被伪装成 HTTP 200 + UUID `retCode`）。

### 4-3. `requestQrCodeStatus` 的真实 owner 是 fep-dev，本条只是前缀别名

`BomOrderController.java:41~42` 的类级 javadoc 原文：「另注意本类的 `requestQrCodeStatus` 是 IF1A-04 的**前缀别名**，真实归属在 `fep-dev-server` 的 `/itpagm/ci/agm/requestQrCodeStatus`；两条落到同一个下游。」

- 两条链路的下游是同一个：`TicketClient.queryQrCodeStatus` → ticket-server `/ci/app/queryQrCodeStatus`；应答 4 个业务键**逐字一致**（`BomResponses.java:152~155` 有说明）。
- **差异在错误码族**：本条走 `BomResponses`（`8999` / 下游原码），AGM 那条走 fep-dev 自己的码族。**用例 MUST 标明走哪条**，断言不能互抄。
- `RequestQrCodeStatusReqDTO` 在两个模块里**是两个类**，字段同形但**不共享**（本文件 §3-14 已注）。给任何一侧加字段**不会自动传导到另一侧**。

### 4-4. 小写 `ticketLogicNum` 返 `8999`（已知残留，只做数据归一未改代码）

设备送**小写** `ticketLogicNum` 时，`F2fTicketMapper` 是**精确等值比较**（旧应用 collect-pay 用的是 `UPPER()`），于是查不到票、落到「没有查找到出票信息」分支返 **`8999`**。影响 #7 `requestOrderResult` 与 #8 `requestTicketRefund` 两条。

- 已按裁决**只做数据归一、未改代码**（`F2fLogicCardNo.normalize` 在 service 侧仍会调用，但它不改变 mapper 侧的精确匹配语义）。
- **压测造数 MUST 用大写 `ticketLogicNum`**，否则整批查询 / 退款用例会集体返 `8999`，被误判成服务故障。
- **建议补一条小写负向用例钉住现状**：断言 `retCode=8999`。一旦哪天 mapper 改回 `UPPER()`，这条用例会红，正好是回归信号。

### 4-5. 本模块 7 个 `@Scheduled` 无分布式锁，MUST 单副本

`face-pay-server/src/main/java/com/chinasofti/huateng/facepay/scheduler/` 下共 **7 个 `@Scheduled`、分布在 6 个类**（2026-09-22 行首锚定实测）：

| 类 | 方法数 | 触发配置 |
|---|---|---|
| `F2fNotifyJob` | 1 | `f2f.notify.scanIntervalMs:30000` |
| `F2fRefundReconcileJob` | 1 | `f2f.refund.scanIntervalMs:60000` |
| `F2fOrderExpireJob` | 1 | `f2f.order.expireScanIntervalMs:30000` |
| `F2fDeviceOfflineJob` | 1 | `f2f.device.offlineScanIntervalMs:60000` |
| `F2fReportRecoveryJob` | 1 | `f2f.report.scanIntervalMs:120000` |
| `SupplementOrderCloseProcessor` | **2** | `supplement.scheduler.converge-cron:0 */5 * * * ?` / `supplement.scheduler.close-cron:0 */10 * * * ?` |

**全部没有分布式锁**（本项目不用 Redis / MQ），因此 **`face-pay-server` MUST 单副本**。压测时**为了提吞吐扩副本会让这 7 个补偿任务每个副本各跑一份**，后果是重复退款回查、重复通知投递、重复置离线。压测要扩容 **MUST 先确认扩副本方案**，或至少把这些 `*.scanIntervalMs` / cron 调到不触发的值。

### 4-6. 重要缺口：数字人民币硬钱包那 5 个端点在代码里全部不存在

规格里归属 `/itpbom/ci/bom/**`（即本 Controller）的 5 个数字人民币硬钱包端点，**代码里一个都没有**。2026-09-22 在 `face-pay-server/src/main/java` 全量 grep `requestEcny|ecnyCashNotice|requestEcnyArrears|requestEcnyNoCashOrder|requestEcnyRetry`，**零命中**；本 Controller 的 `@PostMapping` 只有 §1 那 16 条 path。

| 规格编号 | 规格 URL | 代码现状 |
|---|---|---|
| IF1A-08 | `/itpbom/ci/bom/requestEcnyArrears` | 不存在 |
| IF1A-09 | `/itpbom/ci/bom/requestEcnyNoCashOrder` | 不存在 |
| IF1A-10 | `/itpbom/ci/bom/requestPayment` | **URL 已被 IF8A-05 占用**，见下 |
| IF1A-11 | `/itpbom/ci/bom/ecnyCashNotice` | 不存在 |
| IF1A-12 | `/itpbom/ci/bom/requestEcnyRetry` | 不存在 |

因此：

- **不能给这 5 个写测试用例**。按规格编号排用例时它们必须标「无实现、不可测」，**NEVER 因为编号在 IF1A 族就去 `fep-dev-server` 找**（IF1A-06 / IF1A-07 才在那边）。
- **IF1A-10 的 URL `requestPayment` 与已在跑的 IF8A-05 完全撞名**（同模块、同类级前缀、同 HTTP 方法）。照规格直接实现会**抢占一个正在承接真实流量的端点** —— `requestPayment` 现在是 BOM 扫码支付（本文件 §3-3）。**这一点测试与开发两侧都需要甲方澄清**：是复用同一条 URL 靠字段分流，还是另起一个 URL。澄清前：
  - 测试侧 **NEVER 把 `requestPayment` 的用例名写成「数字人民币支付」**，现状它就是扫码付；
  - 开发侧 **NEVER 在这条 URL 上加 e-CNY 分支**，那等于在没有契约的前提下改一个在跑端点的语义。

### 4-7. 其余零散注意点

- **`8003` 不在 `BomResponses` 里**（在 `BomOrderController.java:51`）。想按常量清单穷举错误码时只看 `BomResponses` 会漏掉它。
- **`requestTicketRefund` 的「订单不存在」是 `9999`、不是 `8006`**（`F2fBomOrderService.java:204`），全域唯一一处。
- **两个拼写错误的响应键 `lastTransAmout` / `lastTikcetTransSeq`**（`BomResponses.java:130~131`）与三个拼写错误的请求字段 `transAount`（#2）/ `operaterId`（#2、#10）/ `takeTickeDate`（#12）/ `tranDate`（#5），**全部照搬旧实现，用例里 NEVER 改正**。
- **多个端点「失败也返 `0000`」**：#1 心跳（恒 `0000`）、#3 付款码缺失（`0000` + `paymentResult=FAILED`）、#5 / #6 的退款提交结果、#11 HCE 回写失败。这四类**MUST 用 DB 或日志做断言，NEVER 只判 `retCode`**。
- **`adviceOpt` 在 #9 应答里是 `Object`**（`BomResponses.java:103`），在 #10 / #11 请求里是 `String`。JSON schema 断言要分开写。
- **`F2F_RESULT_REPORT` 的 `REPORT_TYPE='BOM_BIZ_RESULT'` 被 #5 与 #11 共用**（`F2fBomOrderService.java:61` 与 `F2fHceService.java:34` 是同一个字面量），按 `REPORT_TYPE` 筛数据时两类会混在一起。
- **`F2fBomOrderService` / `F2fScanPayService` / `F2fTicketIssueService` / `F2fQrCodeStatusService` 全部刻意不带 `@Transactional`**（链路里有支付中心调用），因此**中途失败不会回滚已落的行**。压测后清理造数 MUST 按订单号逐表清，**NEVER 假定「失败的那笔什么都没写」**。
