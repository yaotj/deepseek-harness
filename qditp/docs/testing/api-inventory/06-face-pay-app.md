# 06 face-pay-server 非设备域接口契约清单（APP 域 / 支付中心回调 / 内部端点 / 运营端）

适用范围：`face-pay-server` 模块 **除设备域（`TvmOrderController` / `BomOrderController`）之外的全部端点**。设备域两个 Controller 分别由 `05a-face-pay-tvm.md` 与 `05b-face-pay-bom.md` 负责，本文件不重复。

## 0. Controller 枚举与归属（已逐个 grep 核实）

`grep -rn '@RestController\|@Controller' face-pay-server/src/main/java` 实测命中 **11 个**，全部是 `@RestController`、无 `@Controller`：

| # | Controller | 文件与行号 | 归属 |
|---|---|---|---|
| 1 | `TvmOrderController` | `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/ci/tvm/TvmOrderController.java:44` | 设备域，见 `05a-face-pay-tvm.md` |
| 2 | `BomOrderController` | `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/ci/bom/BomOrderController.java:44` | 设备域，见 `05a-face-pay-tvm.md` |
| 3 | `AppOrderController` | `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/ci/app/AppOrderController.java:25` | 本文件（第二块） |
| 4 | `SupplementAppController` | `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/ci/app/SupplementAppController.java:13` | 本文件（第二块） |
| 5 | `RefundNoticeController` | `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/paycenter/RefundNoticeController.java:35` | 本文件（第一块） |
| 6 | `SupplementPayNoticeController` | `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/paycenter/SupplementPayNoticeController.java:13` | 本文件（第一块 + 第三块，双别名跨两类可达性） |
| 7 | `BatchRefundInternalController` | `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/internal/BatchRefundInternalController.java:23` | 本文件（第三块） |
| 8 | `ReconExportController` | `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/internal/ReconExportController.java:12` | 本文件（第三块） |
| 9 | `NoticeAppTaskController` | `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/notice/NoticeAppTaskController.java:14` | 本文件（第三块） |
| 10 | `AppOrderPageController` | `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/page/AppOrderPageController.java:16` | 本文件（第三块） |
| 11 | `FacePayOrderPageController` | `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/page/FacePayOrderPageController.java:26` | 本文件（第三块） |

即 **11 - 2 = 9 个 Controller 归本文件**，共 **20 个 HTTP 端点**（`SupplementPayNoticeController` 与 `RefundNoticeController` 各有 2 条别名，按「端点」计 1 条、两条 URL 都列）。

### 0.1 报文骨架

APP 域四条 `@ModelAttribute` 端点用的骨架是 **模块内的 `com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest`**（`face-pay-server/src/main/java/com/chinasofti/huateng/facepay/api/device/BaseDeviceRequest.java:4`），**不是 `model` 模块的 `ItpCommonFormRequest`**。字段表已在 `05a-face-pay-tvm.md` §0 列出，此处只记差异点：

- 与 `ItpCommonFormRequest`（`charset` / `format` / `timestamp` / `deviceId` / `signType` / `sign` / `bizData`）相比，`BaseDeviceRequest` **多一个 `providerId`**（商户编码：`01`-APP，`02`-TVM，`03`-BOM，`04`-AGM，`05`-ACC，`06`-ITP，`07`-STT，见该文件 `:6`）。
- 解包走 `DeviceRequests.unwrap(form, XxxReqDTO.class)`（`face-pay-server/src/main/java/com/chinasofti/huateng/facepay/api/device/DeviceRequests.java:12`）：`bizData` 为空或非法 JSON 时**返回 `null`**，由 Controller 自行回参数错误码；解包成功后会把 8 个骨架字段回填到 DTO（DTO 均 `extends BaseDeviceRequest`）。
- **本模块无任何入向验签**：9 个 Controller 里没有一处读取 `sign` 做校验，`signType` / `sign` 只是被 `unwrap` 原样回填。压测与自动化用例**不需要构造签名**。

### 0.2 响应码族与响应骨架

| 骨架类 | 使用端点 | 形状 |
|---|---|---|
| `AppResponses`（`face-pay-server/src/main/java/com/chinasofti/huateng/facepay/api/device/app/AppResponses.java:7`） | `AppOrderController` 全部 7 条 + `RefundNoticeController` | `JSONObject`，固定 `retCode` / `retMsg` + 按接口追加业务字段 |
| `SupplementOrderRespDTO`（`model/src/main/java/com/chinasofti/huateng/model/pay/SupplementOrderRespDTO.java:6`） | `SupplementAppController` | `retCode` / `retMsg` / `orderNo` / `payStatus` / `totalAmount` |
| `Map<String,String>` 字面量 | `SupplementPayNoticeController` | **恒 `{"code":"0","msg":"success"}`** |
| `CommonResult`（`model/src/main/java/com/chinasofti/huateng/common/response/CommonResult.java:7`） | `BatchRefundInternalController`、`NoticeAppTaskController` | `retCode` / `retMsg` |
| `ReconExportRespDTO`（`model/src/main/java/com/chinasofti/huateng/model/recon/ReconExportRespDTO.java:9`） | `ReconExportController` | `accepted`（boolean） / `message` |
| `ResultVO<T>`（`model/src/main/java/com/chinasofti/huateng/common/response/ResultVO.java:5`） | 两个 `/page/**` Controller | `code` / `msg` / `data`；`200`=SUCCESS、`400`=非法参数、`500`=内部异常 |

APP 域码族（`AppResponses` 常量，实测值）：`0000` 成功；**`8001`** 下单与请求支付信息的参数校验失败（`CODE_INVALID_ORDER_PARAM`）；**`8003`** 查询类接口参数校验失败（`CODE_INVALID_PARAM`）；**`8999`** 支付中（`requestPayResult` 拿不到明确结果）；**`9999`** service 层通用失败。**APP 域不是 BOM 的 `8xxx` 全族、也不是 TVM 的 `2xxx`**，只有上面这 5 个常量。

---

## 1. 本文件端点总览

| 序号 | 中文名 | 可达性分类 | 对外真实入口 | 响应码族 |
|---|---|---|---|---|
| 1 | 支付中心退款结果回调（直达入口） | 第一块：网关直达 | `POST /itpbom/ci/bom/receiveRefundResult`、`POST /itptvm/ci/tvm/receiveRefundResult` | APP `0000`/`8003` + service `9999` |
| 2 | 补款单支付中心结果回调（BOM 前缀别名） | 第一块：网关直达 | `POST /itpbom/ci/bom/supplementPayNotice` | **恒 `code=0`** |
| 3 | APP 取票下单（IF8A-20） | 第二块：经 fep-app 透传 | `POST /fep-app/ci/app/requestOrder` | `0000`/`8001`/`9999` |
| 4 | APP 请求支付信息（IF8A-11） | 第二块：经 fep-app 透传 | `POST /fep-app/ci/app/requestPaymentInfo` | `0000`/`8001`/`9999` |
| 5 | APP 支付结果查询（IF8A-18） | 第二块：经 fep-app 透传 | `POST /fep-app/ci/app/requestPayResult` | `0000`/`8003`/`8999`/`9999` |
| 6 | 获取已激活取票订单列表 | 第二块：经 fep-app 透传 | `POST /fep-app/ci/app/requestPreActiveOrderList` | `0000`/`8003`/`9999` |
| 7 | APP 请求退款（整单） | 第二块：经 fep-app 透传 | `POST /fep-app/ci/app/requestRefundTicket` | `0000`/`8003`/`9999` |
| 8 | APP 退款结果查询 | 第二块：经 fep-app 透传 | `POST /fep-app/ci/app/requestRefundTicketResult` | `0000`/`8003`/`9999` |
| 9 | 支付中心退款结果回调（`/ci/app` 旧入口） | 第二块：经 fep-app 透传（**负向测试点**） | `POST /fep-app/ci/app/receiveRefundResult` | `0000`/`8003`/`9999` |
| 10 | 请求补款下单（IF8A-26） | 第二块：经 fep-app 透传（**form → JSON 转换**） | `POST /fep-app/ci/app/requestPayOrder`、`POST /fep-app/app/requestPayOrder` | `SupplementOrderRespDTO.retCode` |
| 11 | 补款单支付中心结果回调（内部别名） | 第三块：内部 | `POST /ci/facePay/paycenter/payNotice`（公网不可达） | **恒 `code=0`** |
| 12 | 单程票未取票批量退款 | 第三块：内部（Quartz 200） | `POST /internal/f2f/batch-refund/single-ticket` | `0000`/`9998` |
| 13 | TVM 充值未到账批量退款 | 第三块：内部（Quartz 205） | `POST /internal/f2f/batch-refund/topup` | `0000`/`9998` |
| 14 | BOM 非现金收款未履约批量退款 | 第三块：内部（Quartz 210） | `POST /internal/f2f/batch-refund/no-cash` | `0000`/`9998` |
| 15 | 日终对账抽取受理 | 第三块：内部（recon-server 下发） | `POST /internal/recon/export` | `accepted` true/false |
| 16 | 通知重投连通性探针 | 第三块：内部 | `POST /pay/noticeAppTask/testtbNoticeAppTask` | 恒 `0000` |
| 17 | 重投出票成功通知（IF8B-04） | 第三块：内部 | `POST /pay/noticeAppTask/noticeTakeTicketTask` | 恒 `0000` |
| 18 | 重投出票失败通知（IF8B-06） | 第三块：内部 | `POST /pay/noticeAppTask/noticeTakeTicketFailureTask` | 恒 `0000` |
| 19 | 重投退款结果通知（IF8B-07） | 第三块：内部 | `POST /pay/noticeAppTask/noticeRefundTask` | 恒 `0000` |
| 20 | APP 订单按金额退款（运营端） | 第三块：内部 `/page/**` | `POST /page/app/orders/{orderNo}/refund` | `ResultVO` `200`/`500` |
| 21 | 当面付订单分页查询（运营端） | 第三块：内部 `/page/**` | `GET /page/face-pay/orders` | `ResultVO` `200`/`400` |
| 22 | 当面付订单全额退款（运营端） | 第三块：内部 `/page/**` | `POST /page/face-pay/orders/{orderNo}/refund` | `ResultVO` `200`/`400` |

> 序号 2 与 11 是**同一个 handler 的两条 URL 别名**（`SupplementPayNoticeController:24`），因跨两类可达性而分列两行；总 handler 数仍是 20。

### 1.1 可达性判据

- **能被 `fep-app-vr` 的 8 条网关前缀之一命中**才算公网可达。该 VS 的 8 条前缀是 `/fep-app/`、`/fep-dev/`、`/fep-acc/`、`/fep-alipay/`、`/itptvm/`、`/itpbom/`、`/itpagm/`、`/para-server/`。
- **`/ci/app/**` 与 `/ci/facePay/**` 在 VS 里没有 route，公网不可直达**；`/itptvm/` 与 `/itpbom/` 两条 route 指向 `face-pay-server-svc:30025`、且 `rewrite.uri` 保留前缀，因此带这两个前缀的绝对路径端点**公网直达**。
- `/internal/**`、`/page/**`、`/pay/noticeAppTask/**` 都不在那 8 条前缀内，只能在集群内或从 `k8s-master` 打 `172.20.211.23:30025` 访问。
- 判断流量落点 **MUST 现查** `kubectl get vs fep-app-vr -n itp -o yaml` 与 `fep-app` Deployment 的 `service.collectPay.url` / `service.facePay.url` env，**NEVER 引用本文件的地址**。

---

## 2. 第一块：对外可达的端点

### 1. 支付中心退款结果回调（直达入口）

- **对外真实入口**：`POST /itpbom/ci/bom/receiveRefundResult`（直达）、`POST /itptvm/ci/tvm/receiveRefundResult`（直达，同一个 handler 的第二个别名）
- **face-pay 容器内路径**：同上两条（类上**没有** `@RequestMapping`，`@PostMapping` 写的是绝对路径）
- **Controller**：`RefundNoticeController#receiveRefundResult`（`face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/paycenter/RefundNoticeController.java:46`）
- **Content-Type**：任意能被 `@RequestBody String` 接住的（支付中心实际发 `application/json`）；**入参是原始 body 字符串，不做表单绑定**
- **入参形态**：`@RequestBody String requestBody` → 私有 `parseCallbackBody`（`:70`）→ `AppRefundNotiResultReqDTO`
- **响应码族**：`AppResponses`（`0000` / `8003` / `9999`）
- **配套配置**：`pay.center.refund-notice-url`（env `PAY_CENTER_REFUND_NOTICE_URL`）MUST 指向本端点，它会被 `PayCenterMessageFactory.buildRefundRequest` 作为支付中心 §3.1 必填键 `notifyUrl` 送出；不送就永远收不到退款回调（说明写在类 javadoc `:31`）。

**支持的三种报文形态**（`parseCallbackBody` 逐条实读，**测试必须三种都造**）：

形态 A —— `bizData` 是 JSON 对象（`:77`，走 `((JSONObject) bizData).toJavaObject(...)`）：

```json
{
  "merchantNo": "M0001",
  "apiVersion": "1.0",
  "signType": "RSA",
  "sign": "xxx",
  "charset": "UTF-8",
  "bizData": {
    "orderNo": "F2F20260922000001",
    "refundNo": "RF20260922000001",
    "outRefundNo": "PC-RF-0001",
    "refundResult": "SUCCESS",
    "refundResultDesc": "退款成功",
    "refundDate": "20260922103000",
    "refundAmount": "200"
  }
}
```

形态 B —— `bizData` 是 Base64 字符串（`:80`，`Base64.getDecoder().decode(text)` 后再 `parseObject`）：

```json
{
  "merchantNo": "M0001",
  "bizData": "eyJvcmRlck5vIjoiRjJGMjAyNjA5MjIwMDAwMDEiLCJyZWZ1bmRObyI6IlJGMjAyNjA5MjIwMDAwMDEiLCJvdXRSZWZ1bmRObyI6IlBDLVJGLTAwMDEiLCJyZWZ1bmRSZXN1bHQiOiJTVUNDRVNTIn0="
}
```

形态 C —— 整个 body 就是业务体（`:84`，`root.toJavaObject(...)` 兜底）：

```json
{
  "orderNo": "F2F20260922000001",
  "refundNo": "RF20260922000001",
  "outRefundNo": "PC-RF-0001",
  "refundResult": "SUCCESS",
  "refundResultDesc": "退款成功",
  "refundDate": "20260922103000",
  "refundAmount": "200"
}
```

解析异常或 body 为空 ⇒ `parseCallbackBody` 返 `null` ⇒ 端点回 `8003 非法参数,bizData解析失败`（`:51`）。类 javadoc `:67` 明确写「NEVER 在解析失败时回落成空对象」。

**请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.app.AppRefundNotiResultReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | `String` | 无（代码无 Bean Validation，空值不会 400） | 我方原支付订单号。**本端点不校验它为空**（只有 `/ci/app/receiveRefundResult` 那条才校验） |
| `refundNo` | `String` | 无（同上） | 我方退款单号，幂等键。本端点与 `outRefundNo` **二者至少有一个非空**（`:56`），否则回 `8003 非法参数,退款单号不能为空` |
| `outRefundNo` | `String` | 无（同上） | 支付中心侧退款单号 |
| `refundResult` | `String` | 无（同上） | `SUCCESS` / `FAIL`；**空则回 `8003 非法参数,refundResult不能为空`**（`:53`）。DTO 内 `isSuccess()` 只认字面量 `"SUCCESS"`、`isFailed()` 只认 `"FAIL"`（`:27`/`:31`），**不认 `FAILED`** |
| `refundResultDesc` | `String` | 无（同上） | 结果描述，落库用 |
| `refundDate` | `String` | 无（同上） | 退款时间，字符串原样 |
| `refundAmount` | `String` | 无（同上） | 退款金额，**字符串不是数字类型** |

**响应字段**（`appRefundService.receiveRefundResult(request)` 的返回原样透出，最小骨架）

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | `String` | `0000` / `8003` / `9999` |
| `retMsg` | `String` | 描述 |

**压测备注**：写 `F2F_REFUND`（幂等键 `refundNo`，唯一索引 `UK_F2F_REFUND_IDEM`）与 `F2F_ORDER` 的退款汇总列；**不调支付中心**（这是入向回调）。失败 / 超时由 `F2fRefundReconcileJob`（`face-pay-server/src/main/java/com/chinasofti/huateng/facepay/scheduler/F2fRefundReconcileJob.java:30`，`fixedDelay` 默认 60s）主动回查兜底，因此**丢一次回调不会永久卡住**。幂等重复请求预期仍回 `0000`。

### 2. 补款单支付中心结果回调（BOM 前缀别名）

- **对外真实入口**：`POST /itpbom/ci/bom/supplementPayNotice`（直达）
- **face-pay 容器内路径**：`/itpbom/ci/bom/supplementPayNotice`（第二别名 `/ci/facePay/paycenter/payNotice` 公网不可达，见第三块序号 11）
- **Controller**：`SupplementPayNoticeController#payNotice`（`face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/paycenter/SupplementPayNoticeController.java:24`）
- **Content-Type**：`application/json`
- **入参形态**：`@RequestBody Map<String, Object> body`（**不绑定任何 DTO**，只用两个私有方法取值）
- **响应码族**：**不属于任何业务码族 —— 恒 `{"code":"0","msg":"success"}`**

**请求字段** — 无 DTO，直接读 `Map` 的键（`extractOrderNo` `:53` + `valueOf` `:48`）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | 任意（`String.valueOf` 转换） | 无（无 DTO，无 Bean Validation） | 首选订单号键；`SP` 前缀的补款单号 |
| `merchantOrderNo` | 任意 | 无 | `orderNo` 缺失时的备选键（`:61`） |
| `status` | 任意 | 无 | **只进日志、不参与判断**（类 javadoc `:45` 明确：收口以支付中心查询结果为权威，「NEVER 改成 status 非 SUCCESS 就直接 return」） |
| `channelOrderNo` | 任意 | 无 | 只进日志 |
| `totalAmount` | 任意 | 无 | 只进日志 |
| `payTime` | 任意 | 无 | 只进日志 |

**响应字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | `String` | **恒 `"0"`** |
| `msg` | `String` | **恒 `"success"`** |

**压测备注**：**这个端点恒返成功，不论处理结果** —— 三条分支（`orderNo` 缺失直接 return、`handlePayNotice` 正常、`handlePayNotice` 抛 `RuntimeException` 被 `:36` catch 掉）**返回体完全一致**。因此**从响应完全看不出成败，端到端验证 MUST 查库（`F2F_ORDER` / 补款单的 `PAY_STATUS`）或查日志**（成功路径有「补款支付中心回调」INFO、失败路径有「补款回调处理异常」ERROR、缺号有「补款回调缺少 orderNo / merchantOrderNo」WARN）。链路里 `SupplementPayCenterFlow.handlePayNotice` 会**主动调支付中心查询**（不看回调里的 `status`），因此本端点属于「会出网」的入向回调。补偿兜底是 `SupplementOrderCloseProcessor.converge`（`face-pay-server/src/main/java/com/chinasofti/huateng/facepay/scheduler/SupplementOrderCloseProcessor.java:21`，cron `0 */5 * * * ?`）。

---

## 3. 第二块：经 fep-app 转发才可达的端点

这一块的端点在 face-pay 容器内都挂 `/ci/app/**` 或 `/ci/facePay/**`，**`fep-app-vr` 里没有对应 route、公网打不进来**；真实入口是 `fep-app-server`（前缀 `/fep-app/`），由它经 `rpc` 转发到 face-pay。转发目标由 `fep-app` Deployment 的 env 决定（`service.collectPay.url` 供 `CollectPayClient` 用、`service.facePay.url` 供 `FacePayClient` 用），当前两者都指向 face-pay（ADR-D117）。

**逐条映射（这是测试用例真正要打的地址）**

| 外部真实入口 URL | fep-app 侧 handler | RPC 出向路径 | face-pay 侧 handler |
|---|---|---|---|
| `POST /fep-app/ci/app/requestOrder` | `CollectPayController#requestOrder`（`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/CollectPayController.java:28`，form 原样透传） | `CollectPayClient:33` `/ci/app/requestOrder` | `AppOrderController#requestOrder`（`:47`） |
| `POST /fep-app/ci/app/requestPaymentInfo` | 同类 `:34` | `CollectPayClient:41` `/ci/app/requestPaymentInfo` | `AppOrderController#requestPaymentInfo`（`:76`） |
| `POST /fep-app/ci/app/requestPayResult` | 同类 `:40` | `CollectPayClient:49` `/ci/app/requestPayResult` | `AppOrderController#requestPayResult`（`:93`） |
| `POST /fep-app/ci/app/requestRefundTicket` | 同类 `:46` | `CollectPayClient:62` `/ci/app/requestRefundTicket` | `AppOrderController#requestRefundTicket`（`:122`） |
| `POST /fep-app/ci/app/requestRefundTicketResult` | 同类 `:52` | `CollectPayClient:67` `/ci/app/requestRefundTicketResult` | `AppOrderController#requestRefundTicketResult`（`:133`） |
| `POST /fep-app/ci/app/requestPreActiveOrderList` | 同类 `:58` | `CollectPayClient:72` `/ci/app/requestPreActiveOrderList` | `AppOrderController#requestPreActiveOrderList`（`:107`） |
| `POST /fep-app/ci/app/receiveRefundResult` | 同类 `:70` | `CollectPayClient:82` `/ci/app/receiveRefundResult` | `AppOrderController#receiveRefundResult`（`:147`） |
| `POST /fep-app/ci/app/requestPayOrder`、`POST /fep-app/app/requestPayOrder` | `GateTxnPayController#requestPayOrder`（`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/GateTxnPayController.java:34`，**form 解析成 DTO 再发 JSON**） | `FacePayClient:35` `/ci/facePay/app/requestPayOrder` | `SupplementAppController#requestPayOrder`（`:25`） |

**两条实测差异，写用例前 MUST 知道**：

1. `CollectPayController` 那 7 条是**纯 form 透传**（`toFormDataMap` `:76` 把 8 个骨架字段原样打包，`bizData` 不解析），因此外部报文与 face-pay 收到的报文**逐字一致**。
2. **`requestPayOrder` 那条不是透传**：`fep-app` 用 `parseBizData(request, SupplementOrderReqDTO.class)` 把 form 的 `bizData` 解成 DTO、再以 **JSON body** 发给 face-pay。因此外部仍是 form-urlencoded、face-pay 侧收到的是 JSON。**给 `SupplementOrderReqDTO` 加字段时 fep-app 与 face-pay 两个镜像都要重建**，否则 Fastjson2 会静默丢字段。
3. `fep-app` 的 `CollectPayController` **有 8 条 `/ci/app/**` 映射，比 face-pay 的 `AppOrderController` 多一条** —— 多出来的 `/ci/app/requestActiveTicket`（`:64`）在 `CollectPayClient:77` 转发到 **`/itptvm/ci/tvm/requestActiveTicket`**，落点是设备域 `TvmOrderController:189`，**不在本文件范围内**。NEVER 因为外部前缀是 `/ci/app/` 就去 `AppOrderController` 里找它。

### 3. APP 取票下单（IF8A-20）

- **对外真实入口**：`POST /fep-app/ci/app/requestOrder`（经 fep-app 透传）
- **face-pay 容器内路径**：`/ci/app/requestOrder`
- **Controller**：`AppOrderController#requestOrder`（`face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/ci/app/AppOrderController.java:47`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → `DeviceRequests.unwrap` → `RequestOrderReqDTO`
- **响应码族**：`0000` / `8001`（参数）/ `9999`（service）

**请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.app.RequestOrderReqDTO`（继承 `BaseDeviceRequest` 的 8 个骨架字段，下表只列 `bizData` 内的业务字段）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `userId` | `String` | 无（代码无 Bean Validation，空值不会 400；Controller `:54` 显式判空 → `8001 userId不能为空`） | APP 侧用户号，落 `F2F_ORDER.THIRD_USER_ID` |
| `entryStationCode` | `String` | 无（Controller `:57` 判空 → `8001 entryStationCode不能为空`） | 进站编码 |
| `exitStationCode` | `String` | 无（Controller `:60` 判空 → `8001 exitStationCode不能为空`） | 出站编码 |
| `ticketPrice` | `String` | 无（Controller `:63` 判空 → `8001 ticketPrice不能为空`） | 单张票价，**单位分**，字符串。DTO `priceInFen()`（`:27`）解析失败返 `null` |
| `singelTicketNum` | `String` | 无（Controller `:66` 判空 → `8001 singelTicketNum不能为空`） | 购票张数，**拼写照搬规格的 `singel`、NEVER 写成 `single`**。DTO `ticketCount()`（`:34`）超 `Integer.MAX_VALUE` 或非数字返 `null` |
| `singleTicketType` | `String` | 无（Controller `:69` 判空 → `8001 singleTicketType不能为空`） | `0` 按站点购票，其余按里程/区间，取值口径同 TVM |

`bizData` 整体为空或非法 JSON ⇒ `unwrap` 返 `null` ⇒ `8001 请求报文不能为空`（`:52`）。

**响应字段**（`AppResponses.orderNo(...)`，`AppResponses.java:33`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | `String` | `0000` 成功 |
| `retMsg` | `String` | `成功` |
| `orderNo` | `String` | ITP 订单号 |

**压测备注**：写 `F2F_ORDER`（`CHANNEL='01'` APP / `BIZ_TYPE='01'` 购票）；**本端点不调支付中心**（付钱在 `requestPaymentInfo`）。幂等键是订单号生成侧，重复提交同一份报文会各建一单。过期未付由 `F2fOrderExpireJob`（`scheduler/F2fOrderExpireJob.java:30`，`fixedDelay` 默认 30s）收口。`F2fAppOrderService` **刻意不带 `@Transactional`**（`F2fAppOrderService.java:62` 有注释），因此中途失败**不回滚**、可能留半成品行。

### 4. APP 请求支付信息（IF8A-11）

- **对外真实入口**：`POST /fep-app/ci/app/requestPaymentInfo`（经 fep-app 透传）
- **face-pay 容器内路径**：`/ci/app/requestPaymentInfo`
- **Controller**：`AppOrderController#requestPaymentInfo`（`:76`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → unwrap `RequestPayInfoReqDTO`
- **响应码族**：`0000` / `8001` / `9999`

**按订单号前缀分流（已复核，测试必须两种前缀都造）**

`:86` 的实际代码是 `request.getOrderNo().startsWith(SupplementOrderService.ORDER_NO_PREFIX)`，而 `ORDER_NO_PREFIX` 在 `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/service/supplement/SupplementOrderService.java:11` 定义为 **`"SP"`**：

- `orderNo` 以 **`SP`** 开头 ⇒ 走 `supplementOrderService.requestPayInfo(request)`（补款服务，`SupplementOrderService.java:16`），后续回调落 `/itpbom/ci/bom/supplementPayNotice` 或 `/ci/facePay/paycenter/payNotice`。
- 其余前缀 ⇒ 走 `appOrderService.requestPayInfo(request)`（普通订单服务），后续回调落支付中心 `payNotice`。

两条分支**共用同一个 URL 与同一个 DTO**，只有订单号前缀决定落点，**响应结构相同、无法从响应区分走了哪条**；核对落点 MUST 看日志或库。

**请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.app.RequestPayInfoReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | `String` | 无（代码无 Bean Validation，空值不会 400；Controller `:80` 判空 → `8001 非法参数`） | 订单号。**`SP` 前缀走补款分支** |
| `payChannelCode` | `String` | 无（Controller `:83` 判空 → `8001 非法参数`） | 支付渠道编码 |

**响应字段**（`AppResponses.payInfo(...)`，`AppResponses.java:40`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | `String` | `0000` |
| `retMsg` | `String` | `成功` |
| `payChannelCode` | `String` | 回吐入参 |
| `paymentInfo` | `String` | 支付中心返回的支付要素（拉起收银台用） |
| `signType` | `String` | **恒 `"00"`**（不签名） |
| `sign` | `String` | **恒空串** |

**压测备注**：**会同步调支付中心**（两条分支都会），是本域**最重的一条链路**，压测并发上限受支付中心侧限制。写 `F2F_PAYMENT`（普通单）或补款单的支付前置行。同一订单重复请求由服务层按订单 + 状态短路，**不是靠唯一索引**。

### 5. APP 支付结果查询（IF8A-18）

- **对外真实入口**：`POST /fep-app/ci/app/requestPayResult`（经 fep-app 透传）
- **face-pay 容器内路径**：`/ci/app/requestPayResult`
- **Controller**：`AppOrderController#requestPayResult`（`:93`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → unwrap `RequestAppPayResultReqDTO`
- **响应码族**：`0000` / `8003` / `8999`（支付中）/ `9999`

**请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.app.RequestAppPayResultReqDTO`（**支付结果查询 / 请求退款 / 退款结果查询三个接口共用这一个 DTO**，见类 javadoc `:5`）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | `String` | 无（代码无 Bean Validation，空值不会 400；Controller `:100` 判空 → `8003 非法参数`） | 订单号 |
| `userId` | `String` | 无（Controller `:97` 判空 → `8003 非法参数`） | APP 用户号 |

**响应字段**（`AppResponses.payResult(...)`，`AppResponses.java:55`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | `String` | `0000` / `8999`（支付中，无明确结果）/ `8003` / `9999` |
| `retMsg` | `String` | 描述 |
| `tradeNo` | `String` | 支付中心流水号 |
| `payResult` | `String` | **只有 `SUCCESS` / `FAIL` 两种取值（不是 `FAILED`）**，javadoc `:52` 明确 |
| `payAmount` | `String` | 金额，**Long 转字符串；为 `null` 时原样是 `null` 不是 `"0"`** |
| `payDate` | `String` | 支付时间，**`null` 原样回吐、NEVER 兜成空串**（javadoc `:53`） |

**压测备注**：只读为主，**可能触发对支付中心的主动查询**（拿不到明确结果时返 `8999`）。**已知残留：设备送小写 `ticketLogicNum` 时 face-pay 返 `8999`**（新应用精确等值比较、旧应用是 `UPPER()`），按裁决只做数据归一未改代码 —— 造数据时**票卡逻辑号大小写 MUST 与库内一致**。

### 6. 获取已激活取票订单列表

- **对外真实入口**：`POST /fep-app/ci/app/requestPreActiveOrderList`（经 fep-app 透传）
- **face-pay 容器内路径**：`/ci/app/requestPreActiveOrderList`
- **Controller**：`AppOrderController#requestPreActiveOrderList`（`:107`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → unwrap `RequestQueryActiveOrderReqDTO`
- **响应码族**：`0000` / `8003` / `9999`

**请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.app.RequestQueryActiveOrderReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `userId` | `String` | 无（代码无 Bean Validation，空值不会 400；Controller `:112` 判空 → `8003 非法参数`） | APP 用户号 |
| `appType` | `String` | 无（Controller `:115` 判空 → `8003 非法参数`） | APP 类型。DTO 常量 `APP_TYPE_QD_METRO = "01"`（青岛地铁 APP，`:9`），`isQdMetro()` 只认 `01` |

**响应字段**（`AppResponses.orderList(...)`，`AppResponses.java:65`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | `String` | `0000` |
| `retMsg` | `String` | `成功` |
| `orderList` | `JSONArray` | 已激活取票订单数组；**元素结构由 service 层拼装，不是固定 DTO** —— 用例断言 MUST 先跑一次取实际形状 |

**压测备注**：纯查询、不写库、不调支付中心。**返回是数组，单用户订单多时响应体会明显变大**，压测 P99 对造数据量敏感。

### 7. APP 请求退款（整单）

- **对外真实入口**：`POST /fep-app/ci/app/requestRefundTicket`（经 fep-app 透传）
- **face-pay 容器内路径**：`/ci/app/requestRefundTicket`
- **Controller**：`AppOrderController#requestRefundTicket`（`:122`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → unwrap `RequestAppPayResultReqDTO`（**与序号 5 同一个 DTO**）
- **响应码族**：`0000` / `8003` / `9999`

**请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.app.RequestAppPayResultReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | `String` | 无（代码无 Bean Validation，空值不会 400；Controller `:126` 判空 → `8003 非法参数,orderNo不能为空`） | 待退订单号 |
| `userId` | `String` | 无（**本端点不校验 `userId`**，与序号 5/8 不同） | APP 用户号 |

**响应字段**（`AppResponses.refund(...)`，`AppResponses.java:76`，**与序号 8 同一形态**）

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | `String` | `0000` / `8003` / `9999` |
| `retMsg` | `String` | 描述 |
| `orderNo` | `String` | 回吐订单号 |
| `refundType` | `String` | **恒 `"00"`** |
| `refundDate` | `String` | 退款时间 |
| `refundAmount` | `String` | 退款金额（Long 转字符串，`null` 原样） |
| `refundResult` | `String` | `PROCESSING` / `SUCCESS` / `FAIL`（javadoc `:74`） |
| `refundResultDesc` | `String` | 结果描述 |
| `notifyUrl` | `String` | **仅当非 `null` 才出现在响应里**（`:85` 条件 put），断言时 NEVER 假定它一定存在 |

**压测备注**：**会调支付中心发起退款**，写 `F2F_REFUND`（幂等键靠 `UK_F2F_REFUND_IDEM`）+ 回写 `F2F_ORDER` 退款汇总。首次通常返 `PROCESSING`，终态靠上面序号 1 的回调或 `F2fRefundReconcileJob` 回查补齐。**重复请求同一订单预期命中幂等、不会重复出款** —— 这条是压测里最需要盯的一致性点。

### 8. APP 退款结果查询

- **对外真实入口**：`POST /fep-app/ci/app/requestRefundTicketResult`（经 fep-app 透传）
- **face-pay 容器内路径**：`/ci/app/requestRefundTicketResult`
- **Controller**：`AppOrderController#requestRefundTicketResult`（`:133`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute BaseDeviceRequest` → unwrap `RequestAppPayResultReqDTO`
- **响应码族**：`0000` / `8003` / `9999`

**请求字段** — DTO `com.chinasofti.huateng.facepay.api.device.app.RequestAppPayResultReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `userId` | `String` | 无（代码无 Bean Validation，空值不会 400；Controller `:137` 判空 → `8003 userId不能为空`） | APP 用户号 |
| `orderNo` | `String` | 无（Controller `:140` 判空 → `8003 orderNo不能为空`） | 订单号 |

**响应字段**：同序号 7（`AppResponses.refund(...)`）。

**压测备注**：只读 `F2F_REFUND` / `F2F_ORDER`；**可能触发对支付中心的退款查询**（`F2fAppRefundService.queryRefundResult` 与回查任务同源逻辑）。

### 9. 支付中心退款结果回调（`/ci/app` 旧入口）—— 高价值负向测试点

- **对外真实入口**：`POST /fep-app/ci/app/receiveRefundResult`（经 fep-app 透传）；**支付中心不会打这条**
- **face-pay 容器内路径**：`/ci/app/receiveRefundResult`
- **Controller**：`AppOrderController#receiveRefundResult`（`:147`）
- **Content-Type**：`application/x-www-form-urlencoded`（**这是关键**）
- **入参形态**：`@ModelAttribute PayCenterCallbackRequest`（`face-pay-server/src/main/java/com/chinasofti/huateng/facepay/api/paycenter/PayCenterCallbackRequest.java:7`）→ 手工 `JSON.parseObject(form.getBizData(), AppRefundNotiResultReqDTO.class)`
- **响应码族**：`0000` / `8003` / `9999`

**它与第一块序号 1 是两个不同端点、处理同一件事**，分开的原因写在 `RefundNoticeController` 类 javadoc（`:20~29`），逐条复核如下：

1. 本端点是 `@ModelAttribute` **表单绑定** ⇒ **支付中心发 JSON 时一个字段都取不到**，Spring 不报错、`PayCenterCallbackRequest` 全字段 `null`，随后 `:150` 判到 `bizData` 为空、回 `8003 非法参数,bizData不能为空`。**这是静默绑定失败、不是 400、不是 415**，与日票退款回调 2026-09-20 踩的坑同型。
2. 它同时被 APP 侧 **form 报文**使用，所以**保留原样、NEVER 改它的绑定方式**。
3. `/ci/app/**` 在 `fep-app-vr` 里没有 route ⇒ 支付中心（公网侧）根本打不进来，因此才另起带 `/itpbom/` / `/itptvm/` 前缀的绝对路径端点。

**负向用例（必做）**：用 `Content-Type: application/json` + 序号 1 的形态 A 报文直打本端点，预期 **HTTP 200 + `retCode=8003 非法参数,bizData不能为空`**；同一份报文打 `/itpbom/ci/bom/receiveRefundResult` 则应被正常处理。两条对照即可钉住「回调必须走带前缀的那条」。

**请求字段** — 信封 DTO `com.chinasofti.huateng.facepay.api.paycenter.PayCenterCallbackRequest`（6 个字段对齐支付中心网关「公共请求参数」）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `merchantNo` | `String` | 无（代码无 Bean Validation，空值不会 400） | 商户号 |
| `apiVersion` | `String` | 无 | 接口版本 |
| `signType` | `String` | 无 | 签名类型（**本端点不验签**） |
| `sign` | `String` | 无 | 签名值（**不验**） |
| `charset` | `String` | 无 | 字符集 |
| `bizData` | `String` | 无（Controller `:150` 判空 → `8003 非法参数,bizData不能为空`） | 业务报文 JSON 字符串。类内 `bizDataJson()`（`:74`）支持「裸 JSON」与「Base64」两形态，**但本 Controller 没调它，直接 `JSON.parseObject(form.getBizData(), ...)`** ⇒ **本端点只吃裸 JSON、不吃 Base64**（与第一块序号 1 的三形态不同，NEVER 混为一谈） |

**嵌套 DTO（`bizData` 解析目标）** — `AppRefundNotiResultReqDTO`，字段表同第一块序号 1，但**校验更严**：

| 字段 | 校验 | 失败码与文案 |
|---|---|---|
| `bizData` JSON 解析 | 抛 `RuntimeException` 即失败（`:156`） | `8003 非法参数,bizData格式错误` |
| `orderNo` | **必须非空**（`:160`） | `8003 非法参数,orderNo不能为空` |
| `refundResult` | 必须非空（`:163`） | `8003 非法参数,refundResult不能为空` |
| `refundNo` | **必须非空**（`:166`，第一块那条只要求 `refundNo` 或 `outRefundNo` 二者之一） | `8003 非法参数,refundNo不能为空` |

**响应字段**：同第一块序号 1（`appRefundService.receiveRefundResult` 原样透出）。

**压测备注**：与第一块序号 1 **共用同一个 service 方法**（`F2fAppRefundService.receiveRefundResult`），因此落库副作用与幂等表现完全一致；**NEVER 把两条端点各压一遍当成两条独立链路**。

### 10. 请求补款下单（IF8A-26）

- **对外真实入口**：`POST /fep-app/ci/app/requestPayOrder` 或 `POST /fep-app/app/requestPayOrder`（fep-app 侧双别名，`GateTxnPayController:34`）
- **face-pay 容器内路径**：`/ci/facePay/app/requestPayOrder`（类级 `@RequestMapping("/ci/facePay/app")`，`SupplementAppController:14`）
- **Controller**：`SupplementAppController#requestPayOrder`（`face-pay-server/src/main/java/com/chinasofti/huateng/facepay/controller/ci/app/SupplementAppController.java:25`）
- **Content-Type**：**外部入口是 `application/x-www-form-urlencoded`；face-pay 侧是 `application/json`**（fep-app 做了 form → DTO → JSON 的转换，见 §3 差异点 2）
- **入参形态**：`@RequestBody SupplementOrderReqDTO`（**不是 `BaseDeviceRequest`、不走 `unwrap`**）
- **响应码族**：`SupplementOrderRespDTO.retCode`（`0000` 表示补款单已生成）

**请求字段** — DTO `com.chinasofti.huateng.model.pay.SupplementOrderReqDTO`（在 `model` 模块，`SupplementOrderReqDTO.java:8`）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `goodsCode` | `String` | 无（代码无 Bean Validation，空值不会 400） | 商品编码，规格固定 `001` |
| `price` | `String` | 无 | 补款总金额，**单位分，规格为 string** |
| `quantity` | `String` | 无 | 数量，规格固定 `1` |
| `orderNoList` | `List<String>` | 无 | 待补款的**原过闸订单号数组**（`GATE_TXN_PAY.ORDER_NO`）。**List 展开：元素为字符串订单号，无元素级校验；传空列表 / `null` 不会 400** |
| `thirdUserId` | `String` | 无 | 第三方用户号 |
| `cardId` | `String` | 无 | 卡号 |

**响应字段** — DTO `com.chinasofti.huateng.model.pay.SupplementOrderRespDTO`

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | `String` | `0000` 表示补款单已生成 |
| `retMsg` | `String` | 描述 |
| `orderNo` | `String` | **补款单号，`SP` 前缀**，同时作为后续 `requestPaymentInfo` 的 `orderNo`（因此紧接着会走序号 4 的补款分支） |
| `payStatus` | `String` | `INIT` / `PROCESSING` / `SUCCESS` / `FAIL` / `CLOSED` |
| `totalAmount` | `Long` | 补款单总金额，单位分（**这里是 Long，请求侧 `price` 是 String**） |

**压测备注**：写补款单表 + 关联原过闸订单；**本端点不调支付中心**（付钱在序号 4 的 `SP` 分支）。超时未付由 `SupplementOrderCloseProcessor.closeTimeout`（`scheduler/SupplementOrderCloseProcessor.java:33`，cron `0 */10 * * * ?`）关单、`converge`（`:21`，cron `0 */5 * * * ?`）收敛。**联调链路顺序固定**：本端点拿 `SP` 单号 → 序号 4 拿支付要素 → 支付中心回调打第一块序号 2。

---

## 4. 第三块：内部端点（`/internal/**`、`/page/**`、`/pay/noticeAppTask/**`）

**这一块 NEVER 纳入对外压测。** 三条理由：①不在 `fep-app-vr` 的 8 条前缀内、公网不可达，压它测不出真实入口容量；②**目前一律无鉴权**（本模块没有 spring-security、没有全局拦截器，`X-Recon-Token` 那套也已按裁决整段删除），压测流量等于无认证地改数据；③其中 6 条会**真实发起退款 / 出网通知**，误压即造成资金动作与对上游的重复推送。

但做端到端自动化测试时可能需要**手动触发**它们来构造状态，因此逐条列清单与用途。触发方式：集群内直连 `face-pay-server-svc:30025`，或从 `k8s-master` 打 `http://172.20.211.23:30025<path>`（**NEVER 用 `<svc>.itp.svc:<port>`，那台没有 CoreDNS 解析**）。

### 4.1 由 web-admin Quartz 触发（3 条，`sys_job` 编号已从迁移脚本核实）

迁移脚本 `web-server/web-quartz/src/main/resources/sql/web-quartz-f2f-batch-refund-job-migration.sql`，任务 Bean `web-server/web-admin/src/main/java/com/chinasofti/huateng/quartz/task/F2fBatchRefundQuartzTask.java`，RPC 出向 `rpc/src/main/java/com/chinasofti/huateng/rpc/facepay/FacePayClient.java:48~60`。

| 端点 | Controller 行号 | `sys_job` | cron | 用途 / 候选口径 |
|---|---|---|---|---|
| `POST /internal/f2f/batch-refund/single-ticket` | `BatchRefundInternalController:47` | **200「单程票未取票批量退款」** | `0 0 20 * * ?` | 甲方需求 1。扫 `F2F_ORDER` `ORDER_STATUS=PAID` + `BIZ_TYPE='01'`（`F2fBatchRefundService.BIZ_SINGLE_TICKET`，`:27`）且过静默期的单 |
| `POST /internal/f2f/batch-refund/topup` | `BatchRefundInternalController:53` | **205「TVM充值未到账批量退款」** | `0 0 20 * * ?` | 甲方需求 2。`BIZ_TYPE='02'`（`:30`） |
| `POST /internal/f2f/batch-refund/no-cash` | `BatchRefundInternalController:59` | **210「非现金收款批量退款」** | `0 0 9,15,21 * * ?` | 甲方需求 3。`BIZ_TYPE='04'`（`:33`） |

- **入参形态**：无 body（`FacePayClient` 发空 JSON `{}` + `traceparent` 头）
- **响应字段**：`CommonResult` 的 `retCode` / `retMsg`。`0000` = 本轮跑完，`retMsg` 形如 `单程票退票完成: 候选=N, 已发起=N, 跳过=N, 异常=N`（`:71`，四元组取自 `F2fBatchRefundService.BatchRefundResult` record，`:117`）；**`9998` = 上一轮仍在执行，属限流不是失败**（`:30`/`:67`）
- **三条端点各有自己的 `AtomicBoolean`**（`:36~40`），互不影响；类 javadoc `:17` 明确「NEVER 合成一个端点」——甲方给的执行时间本来就不同
- **压测备注**：会真实调支付中心发起退款、写 `F2F_REFUND`。**手动触发前 MUST 确认候选集**，否则会把测试库里所有已付未取票单全退掉

### 4.2 由 recon-server 下发（1 条）

| 端点 | Controller 行号 | 调用方 | 用途 |
|---|---|---|---|
| `POST /internal/recon/export` | `ReconExportController:28` | `recon-server` 的 `ReconExportClient`（`rpc/src/main/java/com/chinasofti/huateng/rpc/recon/ReconExportClient.java:25` 常量 `EXPORT_PATH`） | 日终对账抽取。face-pay 是第 4 个对账源，产出 PAY、BUS，口径 `F2F_ORDER join F2F_PAYMENT` |

- **Content-Type**：`application/json`
- **入参形态**：`@RequestBody ReconExportReqDTO`（`model/src/main/java/com/chinasofti/huateng/model/recon/ReconExportReqDTO.java:14`，Lombok `@Data`）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `batchId` | `String` | 无 Bean Validation；由 DTO 自带 `validate()`（`:68`）手工校验必填 | 批次标识，形如 `RECON20260910` |
| `businessDate` | `String` | 同上；`validate()` 要求 **8 位纯数字 `yyyyMMdd`** | 账期日期，即最终文件名后缀 |
| `windowStart` | `String` | 同上；`validate()` 要求 **14 位 `yyyyMMddHHmmss`** | 窗口起点（含） |
| `windowEnd` | `String` | 同上；14 位，且 **`windowStart < windowEnd`** 否则抛「时间窗口倒挂」 | 窗口终点（不含） |
| `fileTypes` | `List<String>` | 同上；**非空且每个元素都要能被 `ReconFileTypeEnum.of` 解析**（`:85`） | 本源需产出的文件类型名，**List 展开：元素为 `"PAY"` / `"BUS"` / `"EXP"` / `"DETAIL"` 之一** |

- **响应字段**：`ReconExportRespDTO` 的 `accepted`（boolean）/ `message`。`accepted=false` 表示**同批次上一轮仍在执行、本次被丢弃（限流不是失败）**
- **鉴权**：**已整段删除**（`X-Recon-Token` 不再校验、`ReconExportClient` 也不再发送）。这是**有意为之的临时降级，上线前 MUST 恢复**
- **压测备注**：读重、写 `RECON_*` 相关落地文件；**MUST 单副本**语义（源侧靠进程内标志位挡并发）

### 4.3 通知重投端点（4 条，URL 与旧模块 `collect-pay-server` 的 `NoticeAppTask` 逐字一致）

类级 `@RequestMapping("/pay/noticeAppTask")`（`NoticeAppTaskController:15`）。`rpc` 侧是 `rpc/src/main/java/com/chinasofti/huateng/rpc/f2f/F2FClient.java:55~76`（**注意 `F2FClient` 的路径常量不带前导斜杠**）。

| 端点 | Controller 行号 | 用途 |
|---|---|---|
| `POST /pay/noticeAppTask/testtbNoticeAppTask` | `:33` | 连通性探针：**不碰任何表**，恒返 `0000` |
| `POST /pay/noticeAppTask/noticeTakeTicketTask` | `:40` | 重投出票成功通知（IF8B-04），只投 `NOTIFY_TYPE='TAKE_TICKET_OK'` 的到期任务 |
| `POST /pay/noticeAppTask/noticeTakeTicketFailureTask` | `:46` | 重投出票失败通知（IF8B-06），只投 `NOTIFY_TYPE='TAKE_TICKET_FAIL'` |
| `POST /pay/noticeAppTask/noticeRefundTask` | `:52` | 重投退款结果通知（IF8B-07），只投 `NOTIFY_TYPE='REFUND_RESULT'` |

- **入参形态**：无 body、无参数
- **响应字段**：`CommonResult`，`retCode` **恒 `0000`**（`:66`），`retMsg` 形如 `notifyType=TAKE_TICKET_OK, 到期=N, 已投递=N, 待重试=N, 异常=N`（`:60`，四元组来自 `F2fNotifyDeliverer.DeliverStat`）
- **批量上限**：构造器注入 `${f2f.notify.scanLimit:100}`（`:27`）
- **压测备注**：**会真实向 APP 出网推通知**，误触发即造成对 APP 的重复推送。与模块内 `F2fNotifyJob`（`scheduler/F2fNotifyJob.java:22`，`fixedDelay` 默认 30s）扫的是同一张通知任务表，两者会争抢同一批到期任务 —— 手动触发前建议先停/避开那个 `fixedDelay` 窗口。**注意 `retCode` 恒 `0000`，投递失败也不会体现在返回码上**，判成败 MUST 看 `retMsg` 的四元组或查通知任务表

### 4.4 运营端 `/page/**`（3 条）

| 端点 | Controller 行号 | 用途 |
|---|---|---|
| `POST /page/app/orders/{orderNo}/refund` | `AppOrderPageController:33` | APP 订单**按指定金额**退款（URL 与旧模块逐字一致） |
| `GET /page/face-pay/orders` | `FacePayOrderPageController:46` | 当面付订单分页查询 |
| `POST /page/face-pay/orders/{orderNo}/refund` | `FacePayOrderPageController:82` | 运营端发起**全额**退款 |

#### 4.4.1 `POST /page/app/orders/{orderNo}/refund`

- **Content-Type**：`application/json`
- **入参形态**：`@PathVariable String orderNo` + `@RequestBody(required = false) AppPartialRefundRequest`（**body 可空**）

**请求字段** — DTO `com.chinasofti.huateng.facepay.api.page.AppPartialRefundRequest`（在业务模块内、**不在 `model`**，类 javadoc `:3` 说明它是运营后台与本服务之间的内部契约、不经 `parseBizData`、不属对外契约）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `refundAmount` | `Long` | 无（代码无 Bean Validation，空值不会 400）；body 为空或缺该字段时由 service 按「金额必须大于 0」拒绝（Controller javadoc `:31`） | 本次退款金额，单位分，MUST 大于 0 且不大于剩余可退金额 |
| `refundReason` | `String` | 无 | 退款原因，可空，落 `F2F_REFUND.REFUND_REASON` |
| `operatorId` | `String` | 无 | 操作员标识，可空，落 `F2F_REFUND.OPERATOR_ID` |

- **响应字段**：`ResultVO<?>`（`code` / `msg` / `data`）。`orderNo` 为空或空白 → `ResultMapper.error("订单号不能为空")` ⇒ **`code=500`**（`ResultMapper.error` 用的是 `ERROR_CODE`，**不是 400**，`ResultMapper.java:19`）
- **压测备注**：**会真实调支付中心按金额退款**，写 `F2F_REFUND` + 回写 `F2F_ORDER` 汇总；幂等靠 `UK_F2F_REFUND_IDEM`

#### 4.4.2 `GET /page/face-pay/orders`

- **入参形态**：11 个 `@RequestParam(required = false)`，全部可空

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | `String` | `required = false` | ITP 订单号 |
| `payCenterOrderNo` | `String` | `required = false` | 支付中心订单号 |
| `payCenterChannelOrderNo` | `String` | `required = false` | 渠道订单号 |
| `deviceId` | `String` | `required = false` | 受理设备号 |
| `channel` | `String` | `required = false` | 受理渠道：`01`-APP，`02`-TVM，`03`-BOM |
| `bizType` | `String` | `required = false` | 业务类型：`01`-购票，`02`-充值，`03`-取票，`04`-非现金收款 |
| `orderStatus` | `String` | `required = false` | 订单主状态，取 `F2F_ORDER.ORDER_STATUS` 原值 |
| `beginTime` | `String` | `required = false`；格式 **`yyyy-MM-dd HH:mm:ss`**，解析失败 → `ResultMapper.illegalParams("时间格式必须是 yyyy-MM-dd HH:mm:ss")`（`code=400`，`:64`） | 起始时间 |
| `endTime` | `String` | 同上 | 结束时间 |
| `pageNum` | `Integer` | `required = false` | 页码 |
| `pageSize` | `Integer` | `required = false` | 每页条数 |

- **响应字段**：`ResultVO<Map<String,Object>>`，`data` 内两个键：`list`（元素为 `FacePayOrderPageVO`）、`total`（总数）。`FacePayOrderPageVO`（`face-pay-server/src/main/java/com/chinasofti/huateng/facepay/api/page/FacePayOrderPageVO.java:4`）**24 个字段**：`orderNo` / `orderStatus` / `channel` / `bizType` / `payCenterOrderNo` / `payCenterChannelOrderNo` / `payChannelCode` / `payType` / `inStationCode` / `outStationCode` / `inStationName` / `outStationName` / `ticketPrice`(Long,分) / `ticketNum`(Integer) / `orderAmount`(Long,分) / `singleTicketType` / `deviceId` / `refundNo` / `refundStatus` / `refundAmount`(Long,分) / `lastRefundTime` / `createTime` / `updateTime`
- **`PageOutcome` 是 sealed 二分支**（`:69` 穷尽 `switch`）：`Ok` → `ResultMapper.ok(page)`；`Rejected` → `ResultMapper.illegalParams(reason)`（`code=400`）
- **压测备注**：纯查询。**这是全仓「Druid WallFilter + Oracle 别名」两类陷阱的高风险形态**（多可选谓词 + 分页 + 可能的子查询别名），**MUST 专门覆盖「所有筛选条件都不传」那一支** —— 历史上同型缺陷都只在那一支炸，且被包成 HTTP 200 + UUID `retCode`

#### 4.4.3 `POST /page/face-pay/orders/{orderNo}/refund`

- **Content-Type**：`application/json`
- **入参形态**：`@PathVariable String orderNo` + `@RequestBody(required = false) FacePayRefundRequest`（body 可空）

**请求字段** — DTO `com.chinasofti.huateng.facepay.api.page.FacePayRefundRequest`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `refundReason` | `String` | 无（代码无 Bean Validation，空值不会 400） | 退款原因；**为空 / 空白时落默认值 `运营人工退款`**（`:88`），非空时 `trim()` |
| `operatorId` | `String` | 无 | 操作员，落 `F2F_REFUND.OPERATOR_ID` 供审计追溯；空白归一成 `null`（`trimToNull`，`:101`） |

- **响应字段**：`ResultVO<Map<String,Object>>`。`orderNo` 为空或空白 → `ResultMapper.illegalParams("订单号不能为空")` ⇒ **`code=400`**（**与 4.4.1 那条同语义却用了不同码，NEVER 假定两条一致**）
- **压测备注**：**整单全额退款、会真实调支付中心**；`orderNo` 会 `trim()` 后传下去（`:93`）

---

## 5. 复核记录与不可回退的事实

1. **`requestPaymentInfo` 按订单号前缀分流已复核**：`AppOrderController:86` 的判据是 `SupplementOrderService.ORDER_NO_PREFIX`，实测值 `"SP"`（`SupplementOrderService.java:11`）。`SP` 前缀走 `supplementOrderService.requestPayInfo`，其余走 `appOrderService.requestPayInfo`。**测试必须两种前缀都造**，且两条分支响应结构相同、只能靠日志或库区分落点。
2. **两条退款回调端点是有意分开的**：`RefundNoticeController`（带 `/itpbom/` `/itptvm/` 前缀、`@RequestBody String`、吃三种报文形态）与 `AppOrderController#receiveRefundResult`（`/ci/app/` 前缀、`@ModelAttribute`、只吃 form + 裸 JSON 的 `bizData`）。后者在支付中心发 JSON 时**静默绑定不到任何东西**（不报错、字段全 `null`），且 `/ci/app/**` 在 `fep-app-vr` 里没有 route，所以才另起绝对路径端点。**这是高价值负向测试点，用例见 §3 序号 9。**
3. **`SupplementPayNoticeController` 恒返 `{"code":"0","msg":"success"}`**（`:29` / `:39` 两个 return 完全一致，中间的 `catch (RuntimeException)` 只打 ERROR 日志）。**从响应完全看不出成败，端到端验证 MUST 查库或查日志。**
4. **`catch (DuplicateKeyException` 的 grep 计数不可用作回退判据**（本次已自行复核）：`grep -rn 'catch (DuplicateKeyException' face-pay-server/src/main` 命中 **1 处**，在 `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/service/F2fScanPayService.java:190` 的**注释行**（原文「NEVER 退回 catch (DuplicateKeyException)，MUST 走 F2fDuplicateKey.isConflict(Throwable)」）；另有一处相关命中在 `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/domain/F2fDuplicateKey.java:4` 的 `import` 与 `:22` 的 `instanceof` 判定里。**`src/main` 下真实的 `catch (DuplicateKeyException)` 语句为 0**，12 处原裸 catch 已全部改成沿 `getCause()` 链判定的 `F2fDuplicateKey.isConflict(Throwable)`（模板 `catch (RuntimeException e) { if (!F2fDuplicateKey.isConflict(e)) { throw e; } ... }`）。**NEVER 据 grep 命中数判断已经回退。**
5. **本模块 7 个 `@Scheduled` 无分布式锁，MUST 单副本**（行首锚定实测，已排除 javadoc 里的字样）：
   - `scheduler/F2fNotifyJob.java:22` `deliverDueTasks`（通知投递，`fixedDelay` 默认 30s）
   - `scheduler/F2fRefundReconcileJob.java:30` `reconcileRefunds`（退款回查，默认 60s）
   - `scheduler/F2fOrderExpireJob.java:30` `reconcileExpiredOrders`（订单过期，默认 30s）
   - `scheduler/F2fDeviceOfflineJob.java:28` `markTimeoutDevicesOffline`（设备离线，默认 60s）
   - `scheduler/SupplementOrderCloseProcessor.java:21` `converge`（补款收敛，cron `0 */5 * * * ?`）
   - `scheduler/SupplementOrderCloseProcessor.java:33` `closeTimeout`（补款关单，cron `0 */10 * * * ?`）
   - `scheduler/F2fReportRecoveryJob.java:26` `resumeDueReports`（出票上报补偿，默认 120s）
   
   共 7 个、分布在 **6 个类**（`SupplementOrderCloseProcessor` 一个类里有两个）。压测时这些任务仍在跑，**会与手动触发的内部端点争抢同一批到期数据**，观测结果前 MUST 把它们考虑进去。
6. **本模块服务类整批刻意不带 `@Transactional`**（`F2fTicketIssueService.java:49`、`F2fAppOrderService.java:62` 有注释，因为链路里有支付中心调用）。因此**链路中途失败不回滚、可能留半成品行**，这是设计取舍不是缺陷；连带结论：在这些类上挂 `@TransactionalEventListener(fallbackExecution = false)` 等于静默不执行。
7. **可达性判据不可引用本文件的具体地址**：`fep-app-vr` 的 route 与 `fep-app` Deployment 的 env 都可能变，判断流量落点 MUST 现查 `kubectl get vs fep-app-vr -n itp -o yaml` 与 `fep-app` 的 `service.collectPay.url` / `service.facePay.url`。
