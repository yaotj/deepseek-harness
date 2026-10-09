# 02 fep-app-server：签约支付解约 / 日票计次票旅游票 / TVM-BOM 订单透传

> **生成时间**：2026-09-22
> **代码基线**：`main` 工作副本，仓库根 `/Users/tuanjie/workspace/company/chinasofti/qd/qditp`
> **本文件覆盖的 Controller**：`PaySignController`（10 端点）、`AppDailyTicketController`（12 端点）、`CollectPayController`（8 端点）
> **模块**：`fep-app-server`（容器端口 9101，NodePort 由 `fep-app-hr32k-svc` 对外）
> **公共报文骨架**：不在本文件逐端点重复，见 [README.md](README.md) 2 `ItpCommonFormRequest` 七字段表（sign / charset / format / timestamp / deviceId / signType / bizData）
> **响应骨架与错误码**：见 [README.md](README.md) 3

---

## 本文件端点总览

| # | 中文名 | IF 编号 | 对外 URL（主别名） | 下游服务 |
|---|--------|---------|---------------------|----------|
| 1 | 请求支付（通用） | IF8A-19 | `/fep-app/ci/app/requestPay` | pay-sign-server |
| 2 | 请求签约信息 | IF8A-16 | `/fep-app/ci/app/requestSignInfo` | pay-sign-server |
| 3 | 支付成功回调 | IF8A-05 | `/fep-app/ci/app/receivePayResult` | pay-sign-server |
| 4 | 退款结果回调 | -- | `/fep-app/ci/app/paySign/payment/receiveRefundResult` | pay-sign-server |
| 5 | 请求解约 | IF8A-06 | `/fep-app/ci/app/requestTermination` | pay-sign-server |
| 6 | 直接解绑支付方式 | IF8A-75 | `/fep-app/userData/unbindAgreement` | pay-sign-server |
| 7 | 签约结果查询 | IF8A-22 | `/fep-app/ci/app/requestContractResult` | pay-sign-server |
| 8 | 内部签约结果通知 | IF8A-07 | `/fep-app/ci/app/receiveSignResult` | pay-sign-server |
| 9 | 内部解约结果通知 | IF8A-10 | `/fep-app/ci/app/receiveTerminationResult` | pay-sign-server |
| 10 | 批量查询支付明细 | IF8A-05 | `/fep-app/ci/app/queryPayTxnBatch` | pay-sign-server |
| 11 | 日票下单 | IF8A-60 | `/fep-app/ci/app/dailyTicket/requestOrder` | daily-ticket-server |
| 12 | 旅游票下单 | IF8A-70 | `/fep-app/ci/app/dailyTicket/requestTravelOrder` | daily-ticket-server |
| 13 | 免费票下单 | IF8A-73 | `/fep-app/app/ticket/requestOrderFree` | daily-ticket-server |
| 14 | 小程序票状态同步 | IF8A-72 | `/fep-app/app/ticket/syncOrder` | daily-ticket-server |
| 15 | 日票支付 | IF8A-61 | `/fep-app/ci/app/dailyTicket/payment/requestPay` | daily-ticket-server |
| 16 | 日票支付结果查询 | IF8A-62 | `/fep-app/ci/app/dailyTicket/payment/requestPayResult` | daily-ticket-server |
| 17 | 日票退款 | IF8A-64 | `/fep-app/ci/app/dailyTicket/payment/requestRefundTicket` | daily-ticket-server |
| 18 | 日票取消订单 | IF8A-65 | `/fep-app/ci/app/dailyTicket/cancelOrder` | daily-ticket-server |
| 19 | 日票激活 | IF8A-67 | `/fep-app/ci/app/dailyTicket/updateTicket` | daily-ticket-server |
| 20 | 通知 ACC 车票已使用 | IF8A-71 | `/fep-app/ci/app/dailyTicket/updateAndNotice` | daily-ticket-server |
| 21 | 日票支付回调 | -- | `/fep-app/ci/app/dailyTicket/payment/receivePayResult` | daily-ticket-server |
| 22 | 日票退款结果回调 | -- | `/fep-app/ci/app/dailyTicket/payment/receiveRefundResult` | daily-ticket-server |
| 23 | 请求下单（TVM/BOM） | IF8A-20 | `/fep-app/ci/app/requestOrder` | face-pay-server |
| 24 | 请求支付信息 | IF8A-11 | `/fep-app/ci/app/requestPaymentInfo` | face-pay-server |
| 25 | 支付结果查询（TVM/BOM） | IF8A-18 | `/fep-app/ci/app/requestPayResult` | face-pay-server |
| 26 | 请求退款 | -- | `/fep-app/ci/app/requestRefundTicket` | face-pay-server |
| 27 | 退款结果查询 | -- | `/fep-app/ci/app/requestRefundTicketResult` | face-pay-server |
| 28 | 查询激活订单列表 | -- | `/fep-app/ci/app/requestPreActiveOrderList` | face-pay-server |
| 29 | TVM 激活请求 | -- | `/fep-app/ci/app/requestActiveTicket` | face-pay-server (TVM 域) |
| 30 | 接收退款结果通知 | -- | `/fep-app/ci/app/receiveRefundResult` | face-pay-server |

---

## A. PaySignController（10 端点）

> 源文件：`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/PaySignController.java`（172 行）
> 类级无 `@RequestMapping`；全部方法通过 `paySignAppService` 转发到 pay-sign-server（`service.paySign.url`，下游路径见 `rpc/.../rpc/paySign/PaySignClient.java`）
> `PaySignAppServiceImpl`（115 行）10 个方法全是纯转发 `paySignClient.xxx`，无业务逻辑。

### 1. 请求支付（通用）（IF8A-19）

- **对外 URL**：`POST /fep-app/ci/app/requestPay`
- **容器内路径**：`/ci/app/requestPay`（`/fep-app/` 被 istio rewrite 吃掉）
- **Controller**：`PaySignController:47`
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest` -> `parseBizData(request, RequestPayReqDTO.class)`
- **下游**：`PaySignClient.requestPay` -> pay-sign-server `POST /ci/app/requestPay`
- **异常现象**：**本端点是全模块唯一带入参补全逻辑的端点**：
  ```java
  if (dto.getScene() == null) {
      JSONObject bizData = JSON.parseObject(request.getBizData());
      if (bizData.containsKey("channelType")) {
          dto.setScene(bizData.getString("channelType"));
      }
  }
  if (dto.getIndustryType() == null) {
      dto.setIndustryType("1");
  }
  ```
  即 `scene` 为空时从原始 `bizData` 的 `channelType` 回填；`industryType` 为空时默认 `"1"`。

**bizData 请求字段（`RequestPayReqDTO`，24 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| orderNo | String | 无（代码无 Bean Validation，空值不会 400） | 商户订单号 |
| scene | String | 无 | 支付场景；为空时由 Controller 从 bizData 的 `channelType` 回填 |
| paymentVendor | String | 无 | 支付渠道 |
| amount | Integer | 无 | 金额（分） |
| industryType | String | 无 | 行业类型；为空时由 Controller 默认填 `"1"` |
| subject | String | 无 | 订单标题 |
| body | String | 无 | 订单描述 |
| requestSignSeq | String | 无 | 签约请求序列号 |
| payUserId | String | 无 | 支付用户 ID |
| thirdUserId | String | 无 | 第三方用户 ID |
| cardId | String | 无 | 卡号 |
| cardType | String | 无 | 卡类型 |
| industryDetail | String | 无 | 行业详情 |
| orderTimeOut | Long | 无 | 订单超时时间 |
| authCode | String | 无 | 授权码 |
| notifyUrl | String | 无 | 回调地址 |
| returnUrl | String | 无 | 前端回跳地址 |
| ipAddress | String | 无 | IP 地址 |
| remark | String | 无 | 备注 |
| payChannelCode | String | 无 | 支付渠道编码 |
| discountFee | Integer | 无 | 优惠金额（分） |
| discountInfo | String | 无 | 优惠信息 |
| txnDate | String | 无 | 交易日期 |
| channelType | String | 无 | 渠道类型（Fastjson2 解析到 DTO 会被丢弃，但 Controller 会从原始 bizData 读取回填到 scene） |

**响应字段（`RequestPayResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码，0000 成功 |
| retMsg | String | 返回信息 |
| code | Integer | 下游补充码 |
| msg | String | 下游补充信息 |
| success | Boolean | 是否成功 |
| data | Map\<String,Object\> | 扩展数据 |
| orderNo | String | 订单号 |
| merchantOrderNo | String | 商户订单号 |
| channelOrderNo | String | 渠道订单号 |
| payData | String | 支付凭证 |

**压测备注**：写库（pay-sign-server 的 `PAY_TXN_DETAIL` / `PAY_REQUEST_LOG`）；会调支付中心外部网关；幂等键 = `orderNo + txnDate`（唯一索引）；非回调端点。

---

### 2. 请求签约信息（IF8A-16）

- **对外 URL**：`POST /fep-app/ci/app/requestSignInfo`
- **容器内路径**：`/ci/app/requestSignInfo`
- **Controller**：`PaySignController:63`
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest` -> `parseBizData(request, RequestSignInfoReqDTO.class)`
- **下游**：`PaySignClient.requestSignInfo` -> pay-sign-server `POST /requestSignInfo`（注意：下游路径无 `/ci/app` 前缀）

**bizData 请求字段（`RequestSignInfoReqDTO`，15 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | 第三方用户 ID |
| certNo | String | 无 | 证件号 |
| notifyUrl | String | 无 | 签约结果回调地址 |
| returnUrl | String | 无 | 前端回跳地址 |
| options | String | 无 | 扩展参数；`getOptions()` 有兜底 `return options != null ? options : other` |
| authCode | String | 无 | 授权码 |
| mobilePhone | String | 无 | 手机号 |
| payChannelCode | String | 无 | 支付渠道编码 |
| requestSignSeq | String | 无 | 签约请求序列号 |
| displayAccount | String | 无 | 展示账号 |
| token | String | 无 | 令牌 |
| payUserId | String | 无 | 支付用户 ID |
| bankCardNo | String | 无 | 银行卡号 |
| custName | String | 无 | 客户名 |
| other | String | 无 | 兜底扩展参数（`options` 为空时 `getOptions()` 返回此字段） |

**响应字段（`RequestSignInfoResult extends CommonResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |
| requestStartSdkInfo | String | SDK 拉起签约所需数据 |

**压测备注**：写库（`APP_PAY_SIGN_INFO` / `APP_SIGN_REQUEST_LOG`）；会调支付中心外部网关；非回调端点。

---

### 3. 支付成功回调（IF8A-05）

- **对外 URL**：`POST /fep-app/ci/app/receivePayResult`
- **容器内路径**：`/ci/app/receivePayResult`
- **Controller**：`PaySignController:74`
- **Content-Type**：`application/json`（`@RequestBody String`）
- **入参形态**：`@RequestBody String` -> 双形态解析（见下方）
- **下游**：`PaySignClient.receivePayResult` -> pay-sign-server `POST /ci/app/receivePayResult`

**双形态解析逻辑**：先 `parseCallbackBody(requestBody, ReceivePayResultReqDTO.class)`；为 null 时再 `parseBizData(JSON.parseObject(requestBody, ItpCommonFormRequest.class), ReceivePayResultReqDTO.class)`。

**形态 A（`parseCallbackBody` 三分支）**：
```json
// 分支 1：bizData 是 JSON 对象
{"bizData": {"orderNo":"xxx", "status":"SUCCESS", ...}}

// 分支 2：bizData 是字符串（可能 Base64）
{"bizData": "{\"orderNo\":\"xxx\", \"status\":\"SUCCESS\", ...}"}

// 分支 3：扁平业务 JSON（无 bizData 外壳）
{"orderNo":"xxx", "merchantOrderNo":"yyy", "status":"SUCCESS", ...}
```

**形态 B（兜底 ItpCommonFormRequest）**：
```json
{
  "providerId":"01", "charset":"UTF-8", "format":"JSON",
  "timestamp":"20260922120000", "deviceId":"xxx",
  "signType":"00", "sign":"",
  "bizData": "{\"orderNo\":\"xxx\", \"status\":\"SUCCESS\", ...}"
}
```

**bizData 请求字段（`ReceivePayResultReqDTO`，12 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| orderNo | String | 无（代码无 Bean Validation，空值不会 400） | 支付中心订单号 |
| merchantOrderNo | String | 无 | 商户订单号 |
| channelOrderNo | String | 无 | 渠道订单号 |
| status | String | 无 | 支付状态 |
| payTime | String | 无 | 支付时间 |
| totalAmount | Integer | 无 | 总金额（分） |
| cashAmount | Integer | 无 | 现金金额（分） |
| couponAmount | Integer | 无 | 优惠金额（分） |
| payUserId | String | 无 | 支付用户 ID |
| paymentVendor | String | 无 | 支付渠道 |
| options | String | 无 | 扩展参数 |
| discountInfo | String | 无 | 优惠信息（网关 V1.2 新增，JSON 数组原文，只原样接收落库） |

**响应字段（`PaySignCallbackResult extends CommonResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |

**压测备注**：回调端点；写库（`PAY_TXN_DETAIL` UPDATE + `PAY_CALLBACK_LOG` INSERT）；不调外部网关；幂等 = 终态短路（已 SUCCESS/FAIL 的直接返回）。

---

### 4. 退款结果回调（支付中心 5.2）

- **对外 URL**：
  - `POST /fep-app/ci/app/paySign/payment/receiveRefundResult`
  - `POST /fep-app/app/paySign/payment/receiveRefundResult`
- **容器内路径**：`/ci/app/paySign/payment/receiveRefundResult` 或 `/app/paySign/payment/receiveRefundResult`
- **Controller**：`PaySignController:97`
- **Content-Type**：`application/json`（`@RequestBody String`）
- **入参形态**：`@RequestBody String` -> 双形态解析（同 #3 的逻辑）
- **下游**：`PaySignClient.receiveRefundResult` -> pay-sign-server `POST /ci/app/receiveRefundResult`
- **异常现象**：**URL 特意带 `paySign/payment` 中缀**，因为裸的 `/ci/app/receiveRefundResult` 已被 `CollectPayController`（#30）占用。javadoc 原文：「裸的 `/ci/app/receiveRefundResult` 已被 `CollectPayController` 占用（转发给 collect-pay / face-pay 的 TVM/BOM 退款通知），两者报文与下游完全不同、NEVER 复用同一条 URL」。**本端点没有验签**（2026-09-22 用户裁决「不补验签」），属已知待补的安全缺口，**NEVER 当成新增端点可以免签的先例**。

**bizData 请求字段（`ReceiveRefundResultReqDTO`，7 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| orderNo | String | 无（代码无 Bean Validation，空值不会 400） | 支付中心侧原支付订单号（NEVER 拿它查我方 ORDER_NO） |
| refundResult | String | 无 | 退款结果：SUCCESS / FAIL / PROCESSING |
| refundResultDesc | String | 无 | 退款结果描述 |
| refundDate | String | 无 | 退款时间 |
| refundAmount | Integer | 无 | 退款金额（分） |
| refundNo | String | 无 | 支付中心侧退款单号 |
| outRefundNo | String | 无 | 我方 REFUND_ORDER_NO，定位本地行的唯一键 |

> javadoc 注明：回调报文**没有 txnDate**，而 `PAY_REFUND_DETAIL` 主键是 `REFUND_ORDER_NO + TXN_DATE`。

**响应字段（`PaySignCallbackResult extends CommonResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |

**压测备注**：回调端点；写库（`PAY_REFUND_DETAIL` UPDATE）；不调外部网关；幂等 = 终态短路 + `outRefundNo` 定位。

---

### 5. 请求解约（IF8A-06）

- **对外 URL**：`POST /fep-app/ci/app/requestTermination`
- **容器内路径**：`/ci/app/requestTermination`
- **Controller**：`PaySignController:110`
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest` -> `parseBizData(request, RequestTerminationReqDTO.class)`
- **下游**：`PaySignClient.requestTermination` -> pay-sign-server `POST /ci/app/requestTermination`

**bizData 请求字段（`RequestTerminationReqDTO`，5 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | 第三方用户 ID |
| cardId | String | 无 | 卡号 |
| cardType | String | 无 | 卡类型 |
| requestSignSeq | String | 无 | 签约请求序列号 |
| paymentVendor | String | 无 | 支付渠道 |

**响应字段（`RequestTerminationResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |
| code | Integer | 下游补充码 |
| msg | String | 下游补充信息 |
| success | Boolean | 是否成功 |
| data | Object | 扩展数据 |

**压测备注**：写库（`APP_TERMINATION_REQUEST`）；会调支付中心外部网关（请求解约 2.3）；非回调端点。

---

### 6. 直接解绑支付方式（IF8A-75）

- **对外 URL**：
  - `POST /fep-app/userData/unbindAgreement`（**全模块唯一非 `/app` 和 `/ci/app` 前缀**）
  - `POST /fep-app/ci/app/unbindAgreement`
  - `POST /fep-app/app/unbindAgreement`
- **容器内路径**：`/userData/unbindAgreement` 或 `/ci/app/unbindAgreement` 或 `/app/unbindAgreement`
- **Controller**：`PaySignController:119`
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest` -> `parseBizData(request, UnbindAgreementReqDTO.class)`
- **下游**：`PaySignClient.unbindAgreement` -> pay-sign-server `POST /ci/app/unbindAgreement`
- **异常现象**：首别名 `/userData/unbindAgreement` 是**全模块唯一非 `/app` 或 `/ci/app` 前缀**的端点。

**bizData 请求字段（`UnbindAgreementReqDTO`，3 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | 第三方用户 ID |
| paymentVendor | String | 无 | 支付渠道（与 `USER_ITP_REG_INFO.CHANNEL` 同编码，如 03 支付宝） |
| requestSignSeq | String | 无 | 签约请求序列号（对应 `APP_TERMINATION_REQUEST.REQUEST_SIGN_SEQ`） |

**响应字段（`UnbindAgreementResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |

**压测备注**：写库（`APP_PAY_SIGN_INFO` UPDATE / `APP_TERMINATION_REQUEST`）；可能调支付中心网关；非回调端点。

---

### 7. 签约结果查询（IF8A-22）

- **对外 URL**：`POST /fep-app/ci/app/requestContractResult`
- **容器内路径**：`/ci/app/requestContractResult`
- **Controller**：`PaySignController:128`
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest` -> `parseBizData(request, RequestContractResultReqDTO.class)`
- **下游**：`PaySignClient.requestContractResult` -> pay-sign-server `POST /ci/app/requestContractResult`

**bizData 请求字段（`RequestContractResultReqDTO`，5 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | 第三方用户 ID |
| requestSignSeq | String | 无 | 签约请求序列号 |
| paymentVendor | String | 无 | 支付渠道 |
| cardId | String | 无 | 钱包绑定状态查询所需；传统签约查询可不传 |
| cardType | String | 无 | 同上 |

**响应字段（`RequestContractResultResult extends CommonResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |
| status | String | 签约状态 |
| payUserId | String | 支付用户 ID |
| payAccountId | String | 支付账号 ID |
| payAgreementNo | String | 支付协议号 |

**压测备注**：只读（查 `APP_PAY_SIGN_INFO` + 调支付中心网关查询 2.4）；非回调端点。

---

### 8. 内部签约结果通知（IF8A-07）

- **对外 URL**：`POST /fep-app/ci/app/receiveSignResult`
- **容器内路径**：`/ci/app/receiveSignResult`
- **Controller**：`PaySignController:138`
- **Content-Type**：`application/json`（`@RequestBody String`）
- **入参形态**：`@RequestBody String` -> 双形态解析（同 #3 的逻辑）
- **下游**：`PaySignClient.receiveSignResult` -> pay-sign-server `POST /ci/app/receiveSignResult`

**双形态解析**：与 #3 一致，先 `parseCallbackBody` 后 `parseBizData` 兜底。

**bizData 请求字段（`ReceiveSignResultReqDTO`，10 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | 第三方用户 ID |
| requestSignSeq | String | 无 | 签约请求序列号 |
| agreementNo | String | 无 | 协议号 |
| paymentVendor | String | 无 | 支付渠道 |
| payUserId | String | 无 | 支付用户 ID |
| payAgreementNo | String | 无 | 支付协议号 |
| status | String | 无 | 签约状态 |
| displayAccount | String | 无 | 展示账号 |
| options | String | 无 | 扩展参数 |
| signTime | String | 无 | 签约时间 |

**响应字段（`PaySignCallbackResult extends CommonResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |

**压测备注**：回调端点；写库（`APP_PAY_SIGN_INFO` UPDATE）；不调外部网关；幂等 = 终态短路。

---

### 9. 内部解约结果通知（IF8A-10）

- **对外 URL**：
  - `POST /fep-app/ci/app/receiveTerminationResult`
  - `POST /fep-app/ci/app/receiveUnsignResult`（第二别名）
- **容器内路径**：`/ci/app/receiveTerminationResult` 或 `/ci/app/receiveUnsignResult`
- **Controller**：`PaySignController:152`
- **Content-Type**：`application/json`（`@RequestBody String`）
- **入参形态**：`@RequestBody String` -> 双形态解析（同 #3 的逻辑）
- **下游**：`PaySignClient.receiveTerminationResult` -> pay-sign-server `POST /ci/app/receiveTerminationResult`
- **异常现象**：有 `receiveUnsignResult` 第二别名。

**bizData 请求字段（`ReceiveTerminationResultReqDTO`，9 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | 第三方用户 ID |
| cardId | String | 无 | 卡号 |
| cardType | String | 无 | 卡类型 |
| requestSignSeq | String | 无 | 签约请求序列号 |
| agreementNo | String | 无 | 协议号 |
| payAgreementNo | String | 无 | 支付协议号 |
| paymentVendor | String | 无 | 支付渠道 |
| status | String | 无 | SUCCESS 表示解约成功 |
| dismissalTime | String | 无 | 解约时间（yyyyMMddHHmmss） |

**响应字段（`PaySignCallbackResult extends CommonResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |

**压测备注**：回调端点；写库（`APP_PAY_SIGN_INFO` UPDATE + `APP_TERMINATION_REQUEST` UPDATE + CHANNEL_SYNC outbox）；不调外部网关；幂等 = 终态短路。

---

### 10. 批量查询支付明细（IF8A-05）

- **对外 URL**：`POST /fep-app/ci/app/queryPayTxnBatch`
- **容器内路径**：`/ci/app/queryPayTxnBatch`
- **Controller**：`PaySignController:165`
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest` -> `parseBizData(request, QueryPayTxnBatchReqDTO.class)`
- **下游**：`PaySignClient.queryPayTxnBatch` -> pay-sign-server `POST /ci/app/queryPayTxnBatch`

**bizData 请求字段（`QueryPayTxnBatchReqDTO`，1 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| orderNos | List\<String\> | 无（代码无 Bean Validation，空值不会 400） | 订单号列表 |

**响应字段（`RequestPayTxnBatchResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |
| payTxnDetailList | List\<PayTxnDetailDTO\> | 支付明细列表（新字段名） |
| data | List\<PayTxnDetailDTO\> | 支付明细列表（旧字段名，getter 优先新字段） |

**`PayTxnDetailDTO` 内层类（31 字段）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| orderNo | String | 订单号 |
| payType | String | 支付类型 |
| payStatus | String | 支付状态 |
| thirdUserId | String | 第三方用户 ID |
| cardId | String | 卡号 |
| cardType | String | 卡类型 |
| paymentVendor | String | 支付渠道 |
| requestSignSeq | String | 签约请求序列号 |
| amount | String | 金额 |
| totalAmount | String | 总金额 |
| cashAmount | String | 现金金额 |
| couponAmount | Integer | 优惠金额 |
| refundStatus | String | 退款状态 |
| refundAmount | Integer | 退款金额 |
| lastRefundTime | LocalDateTime | 最后退款时间 |
| merchantOrderNo | String | 商户订单号 |
| channelOrderNo | String | 渠道订单号 |
| payUserId | String | 支付用户 ID |
| requestCount | Integer | 请求次数 |
| nextRequestTime | LocalDateTime | 下次请求时间 |
| lastRequestTime | LocalDateTime | 最后请求时间 |
| firstRequestTime | LocalDateTime | 首次请求时间 |
| responseTime | LocalDateTime | 响应时间 |
| payTime | String | 支付时间 |
| txnDate | String | 交易日期 |
| createTime | LocalDateTime | 创建时间 |
| updateTime | LocalDateTime | 更新时间 |
| discountInfo | String | 优惠信息 |
| debitRequestResult | String | 扣款请求结果 |
| discountFee | Integer | 优惠费 |

**压测备注**：只读（查 `PAY_TXN_DETAIL`）；不调外部网关；非回调端点。

---

## B. AppDailyTicketController（12 端点）

> 源文件：`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppDailyTicketController.java`（155 行）
> 类级无 `@RequestMapping`；全部方法通过 `dailyTicketAppService` 转发到 daily-ticket-server（`service.dailyTicket.url`，下游路径前缀统一 `/ci/daily-ticket/`）

### 11. 日票下单（IF8A-60）

- **对外 URL**（4 别名）：
  - `POST /fep-app/ci/app/dailyTicket/requestOrder`
  - `POST /fep-app/app/dailyTicket/requestOrder`
  - `POST /fep-app/app/requestCountingOrder`
  - `POST /fep-app/app/ticket/requestOrder`
- **容器内路径**：去掉 `/fep-app` 前缀后的 4 条
- **Controller**：`AppDailyTicketController:42`
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest` -> `parseBizData(request, DailyTicketOrderReqDTO.class)`
- **下游**：`DailyTicketClient.requestCountingOrder` -> daily-ticket-server `POST /ci/daily-ticket/requestCountingOrder`

**bizData 请求字段（`DailyTicketOrderReqDTO`，5 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| orderSource | String | 无（代码无 Bean Validation，空值不会 400） | 订单来源 |
| ticketPrice | Integer | 无 | 票价（分） |
| cardType | String | 无 | APP 侧卡类型（生码码体票种固定 0441） |
| showType | String | 无 | 展示类型 |
| userId | String | 无 | 用户 ID |

**响应字段（`DailyTicketOrderResult extends DailyTicketBaseResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |
| data | Object | 扩展数据 |
| orderNo | String | 订单号 |

**压测备注**：写库（`DAILY_TICKET_ORDER` INSERT）；不调外部网关；非回调端点。

---

### 12. 旅游票下单（IF8A-70）

- **对外 URL**（3 别名）：
  - `POST /fep-app/ci/app/dailyTicket/requestTravelOrder`
  - `POST /fep-app/app/dailyTicket/requestTravelOrder`
  - `POST /fep-app/app/ticket/requestTravelOrder`
- **Controller**：`AppDailyTicketController:52`
- **入参形态**：`parseBizData(request, TravelTicketOrderReqDTO.class)`
- **下游**：`DailyTicketClient.requestTravelOrder` -> daily-ticket-server `POST /ci/daily-ticket/requestTravelOrder`

**bizData 请求字段（`TravelTicketOrderReqDTO`，7 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| cardType | String | 无（代码无 Bean Validation，空值不会 400） | 卡类型 |
| userId | String | 无 | 用户 ID |
| ticketPrice | Integer | 无 | 单张票价（分） |
| ticketCount | Integer | 无 | 购票数量 |
| totalAmount | Integer | 无 | 总金额（服务端以 ticketPrice*ticketCount 重算校验、不采信落库） |
| orderSource | String | 无 | 订单来源 |
| showType | String | 无 | 展示类型 |

**响应字段（`TravelTicketOrderResult extends DailyTicketBaseResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |
| data | Object | 扩展数据 |
| orderNo | String | 聚合主单号 |
| subOrders | String | 子单号 JSON 字符串数组 |

**压测备注**：写库（`TRAVEL_TICKET_ORDER` + N 条 `DAILY_TICKET_ORDER` 子单）；不调外部网关；非回调端点。

---

### 13. 免费票下单（IF8A-73）

- **对外 URL**（3 别名）：
  - `POST /fep-app/app/ticket/requestOrderFree`
  - `POST /fep-app/ci/app/dailyTicket/requestOrderFree`
  - `POST /fep-app/app/dailyTicket/requestOrderFree`
- **Controller**：`AppDailyTicketController:62`
- **入参形态**：`parseBizData(request, DailyTicketFreeOrderReqDTO.class)`
- **下游**：`DailyTicketClient.requestOrderFree` -> daily-ticket-server `POST /ci/daily-ticket/requestOrderFree`

**bizData 请求字段（`DailyTicketFreeOrderReqDTO`，7 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| userId | String | 无（代码无 Bean Validation，空值不会 400） | 用户 ID |
| cardType | String | 无 | 卡类型 |
| ticketPrice | Integer | 无 | 票价（分） |
| orderSource | String | 无 | 订单来源 |
| showType | String | 无 | 展示类型 |
| orderType | String | 无 | 1 普通日票 / 2 旅游票 |
| payChannelCode | String | 无 | 支付渠道编码 |

**响应字段（`DailyTicketFreeOrderResult extends DailyTicketBaseResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |
| data | Object | 扩展数据 |
| orderNo | String | 订单号 |
| subOrders | String | 子单号（旅游票时有值） |

**压测备注**：写库；不调外部网关；非回调端点。

---

### 14. 小程序票状态同步（IF8A-72）

- **对外 URL**（3 别名）：
  - `POST /fep-app/app/ticket/syncOrder`
  - `POST /fep-app/ci/app/dailyTicket/syncOrder`
  - `POST /fep-app/app/dailyTicket/syncOrder`
- **Controller**：`AppDailyTicketController:72`
- **入参形态**：`parseBizData(request, DailyTicketSyncOrderReqDTO.class)`
- **下游**：`DailyTicketClient.syncOrder` -> daily-ticket-server `POST /ci/daily-ticket/syncOrder`

**bizData 请求字段（`DailyTicketSyncOrderReqDTO`，8 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| orderNo | String | 无（代码无 Bean Validation，空值不会 400） | 订单号 |
| orderType | String | 无 | 1 日票 / 2 旅游票 |
| event | String | 无 | 1 支付成功 / 2 退款成功 |
| eventTime | Date | 无 | 事件时间 |
| payChannel | String | 无 | 支付渠道 |
| discountInfo | String | 无 | 优惠信息 |
| payAmount | Integer | 无 | 支付金额（分） |
| discountAmount | Integer | 无 | 优惠金额（分） |

**响应字段（`DailyTicketBaseResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |
| data | Object | 扩展数据 |

**压测备注**：写库（UPDATE 订单状态）；不调外部网关；非回调端点。

---

### 15. 日票支付（IF8A-61）

- **对外 URL**（**4 别名**）：
  - `POST /fep-app/ci/app/dailyTicket/payment/requestPay`
  - `POST /fep-app/app/dailyTicket/payment/requestPay`
  - `POST /fep-app/app/payment/requestPay`
  - `POST /fep-app/app/ticket/payment/requestPay`
- **Controller**：`AppDailyTicketController:81`，方法名 `pay`
- **入参形态**：`parseBizData(request, DailyTicketPayReqDTO.class)`
- **下游**：`DailyTicketClient.requestPay` -> daily-ticket-server `POST /ci/daily-ticket/requestPay`
- **异常现象**：日票支付类端点是 **4 别名**。

**bizData 请求字段（`DailyTicketPayReqDTO`，7 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | 第三方用户 ID |
| orderType | String | 无 | 日票固定 1 |
| orderNo | String | 无 | 订单号 |
| payChannelCode | String | 无 | 支付渠道编码（如 03 支付宝） |
| phone | String | 无 | 手机号 |
| channelType | String | 无 | 1 app / 2 wap |
| cardId | String | 无 | pay-sign 支付校验需要 |

**响应字段（`DailyTicketPayResult extends DailyTicketBaseResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |
| data | Object | 扩展数据 |
| signType | String | 签名类型 |
| sign | String | 签名 |
| payChannelCode | String | 支付渠道编码 |
| paymentInfo | String | 支付凭证 |
| discountInfo | String | 优惠信息 |

**压测备注**：写库；会调支付中心外部网关（发起支付）；非回调端点。

---

### 16. 日票支付结果查询（IF8A-62）

- **对外 URL**（4 别名）：
  - `POST /fep-app/ci/app/dailyTicket/payment/requestPayResult`
  - `POST /fep-app/app/dailyTicket/payment/requestPayResult`
  - `POST /fep-app/app/payment/requestPayResult`
  - `POST /fep-app/app/ticket/payment/requestPayResult`
- **Controller**：`AppDailyTicketController:89`
- **入参形态**：`parseBizData(request, DailyTicketOrderNoReqDTO.class)`
- **下游**：`DailyTicketClient.requestPayResult` -> daily-ticket-server `POST /ci/daily-ticket/requestPayResult`

**bizData 请求字段（`DailyTicketOrderNoReqDTO`，2 字段，#16/#17/#18 三端点共用）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| orderType | String | 无（代码无 Bean Validation，空值不会 400） | 订单类型 |
| orderNo | String | 无 | 订单号 |

**响应字段（`DailyTicketPayQueryResult extends DailyTicketBaseResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |
| data | Object | 扩展数据 |
| tradeNo | String | 交易号 |
| payResult | String | success 表示成功 |
| payAmount | Integer | 支付金额（分） |
| payDate | Date | 支付日期 |
| discountInfo | String | 优惠信息 |
| payChannel | String | 支付渠道 |
| channelDiscount | Integer | 渠道优惠 |
| couponDiscount | Integer | 券优惠 |

**压测备注**：只读；非回调端点。

---

### 17. 日票退款（IF8A-64）

- **对外 URL**（4 别名）：
  - `POST /fep-app/ci/app/dailyTicket/payment/requestRefundTicket`
  - `POST /fep-app/app/dailyTicket/payment/requestRefundTicket`
  - `POST /fep-app/app/payment/requestRefundTicket`
  - `POST /fep-app/app/ticket/payment/requestRefundTicket`
- **Controller**：`AppDailyTicketController:99`
- **入参形态**：`parseBizData(request, DailyTicketOrderNoReqDTO.class)`（同 #16）
- **下游**：`DailyTicketClient.requestRefundTicket` -> daily-ticket-server `POST /ci/daily-ticket/requestRefundTicket`

**bizData 请求字段**：同 #16（`DailyTicketOrderNoReqDTO`）。

**响应字段（`DailyTicketRefundResult extends DailyTicketBaseResult`）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 返回码 |
| retMsg | String | 返回信息 |
| data | Object | 扩展数据 |
| refundType | String | 00 直接退款 / 01 激活后核验退款 |
| orderNo | String | 订单号 |
| refundResultDesc | String | 退款结果描述 |
| refundResult | String | SUCCESS |
| refundDate | String | 退款日期 |
| refundAmount | **String** | 退款金额（注意是 String 不是 Integer） |

**压测备注**：写库（`DAILY_TICKET_REFUND` INSERT）；会调支付中心外部网关（发起退款）；非回调端点。

---

### 18. 日票取消订单（IF8A-65）

- **对外 URL**（3 别名）：
  - `POST /fep-app/ci/app/dailyTicket/cancelOrder`
  - `POST /fep-app/app/dailyTicket/cancelOrder`
  - `POST /fep-app/app/ticket/cancelOrder`
- **Controller**：`AppDailyTicketController:106`
- **入参形态**：`parseBizData(request, DailyTicketOrderNoReqDTO.class)`（同 #16）
- **下游**：`DailyTicketClient.cancelOrder` -> daily-ticket-server `POST /ci/daily-ticket/cancelOrder`

**bizData 请求字段**：同 #16（`DailyTicketOrderNoReqDTO`）。

**响应字段（`DailyTicketBaseResult`）**：retCode / retMsg / data。

**压测备注**：写库（UPDATE 订单状态）；不调外部网关；非回调端点。

---

### 19. 日票激活（IF8A-67）

- **对外 URL**（3 别名）：
  - `POST /fep-app/ci/app/dailyTicket/updateTicket`
  - `POST /fep-app/app/dailyTicket/updateTicket`
  - `POST /fep-app/app/ticket/updateTicket`
- **Controller**：`AppDailyTicketController:112`
- **入参形态**：`parseBizData(request, DailyTicketActivateReqDTO.class)`
- **下游**：`DailyTicketClient.updateTicket` -> daily-ticket-server `POST /ci/daily-ticket/updateTicket`
- **异常现象**：**方法名 `activateTicket` 与 URL `updateTicket` 不同名**，service 方法也叫 `updateTicket`。

**bizData 请求字段（`DailyTicketActivateReqDTO`，17 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | 第三方用户 ID |
| operationDate | String | 无 | 操作日期（yyyyMMdd） |
| period | Integer | 无 | 有效期 |
| orderNo | String | 无 | 订单号 |
| cardIssue | String | 无 | 发卡机构 |
| discountAmount | Integer | 无 | 优惠金额（分） |
| ticketType | String | 无 | 票种 |
| actualTimes | Integer | 无 | 实际次数（-99 不限次） |
| transSeq | Integer | 无 | 交易序列号 |
| transAmount | Integer | 无 | 交易金额 |
| cardNum | String | 无 | 卡号 |
| countingStart | Long | 无 | 计次开始时间（毫秒） |
| transDate | Long | 无 | 交易日期（毫秒） |
| showType | String | 无 | 展示类型 |
| payChannel | String | 无 | 支付渠道 |
| ticketCode | String | 无 | 票码 |
| ticketName | String | 无 | 票名 |

**响应字段（`DailyTicketBaseResult`）**：retCode / retMsg / data。

**压测备注**：写库（UPDATE 票状态）；不调外部网关；非回调端点。

---

### 20. 通知 ACC 车票已使用（IF8A-71）

- **对外 URL**（3 别名）：
  - `POST /fep-app/ci/app/dailyTicket/updateAndNotice`
  - `POST /fep-app/app/dailyTicket/updateAndNotice`
  - `POST /fep-app/app/ticket/updateAndNotice`
- **Controller**：`AppDailyTicketController:118`
- **入参形态**：`parseBizData(request, DailyTicketUsedNoticeReqDTO.class)`
- **下游**：`DailyTicketClient.updateAndNotice` -> daily-ticket-server `POST /ci/daily-ticket/updateAndNotice`

**bizData 请求字段（`DailyTicketUsedNoticeReqDTO`，5 字段）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| cardNum | String | 无（代码无 Bean Validation，空值不会 400） | 卡号 |
| period | Integer | 无 | 有效期 |
| countingEnd | Long | 无 | 计次结束时间（毫秒） |
| discountAmount | Integer | 无 | 优惠金额（分） |
| ticketName | String | 无 | 票名 |

**响应字段（`DailyTicketBaseResult`）**：retCode / retMsg / data。

**压测备注**：写库（UPDATE 票状态）；不调外部网关；非回调端点。

---

### 21. 日票支付回调

- **对外 URL**（4 别名）：
  - `POST /fep-app/ci/app/dailyTicket/payment/receivePayResult`
  - `POST /fep-app/app/dailyTicket/payment/receivePayResult`
  - `POST /fep-app/app/payment/receivePayResult`
  - `POST /fep-app/app/ticket/payment/receivePayResult`
- **Controller**：`AppDailyTicketController:128`
- **Content-Type**：`application/json`（`@RequestBody String`）
- **入参形态**：`@RequestBody String` -> **整个 raw body 直接丢给 service**
- **下游**：`DailyTicketClient.receivePayResult` -> daily-ticket-server `POST /ci/daily-ticket/receivePayResult`
- **异常现象**：**`receivePayNotify` 是唯一把整个 raw body 直接丢给 service 的端点**（不在 Controller 层做任何解析）：
  ```java
  public DailyTicketBaseResult receivePayNotify(@RequestBody String requestBody) {
      log.info("日票支付回调原始报文={}", requestBody);
      DailyTicketBaseResult result = dailyTicketAppService.handlePayResultCallback(requestBody);
  ```

**service 层解析逻辑（`DailyTicketAppServiceImpl.parsePayResultCallback`）**：

回调报文由 `handlePayResultCallback` 内部用 `parsePayResultCallback` 解析，字段映射如下：
```java
orderNo = firstText(callbackData.getString("merchantOrderNo"), callbackData.getString("orderNo"));
// orderNo 为 null -> retCode=9999 "无效的支付回调参数"
tradeNo = firstText(callbackData.getString("orderNo"), callbackData.getString("channelOrderNo"));
paymentOrderNo = callbackData.getString("orderNo");
payResult = callbackData.getString("status");
payAmount = firstInteger(callbackData.getInteger("cashAmount"), callbackData.getInteger("totalAmount"));
cashAmount = callbackData.getInteger("cashAmount");
couponAmount = callbackData.getInteger("couponAmount");
payDate = parsePayDate(callbackData.getString("payTime"));
// payTime 支持三种格式：yyyyMMddHHmmss / yyyy-MM-dd HH:mm:ss / yyyy-MM-dd'T'HH:mm:ss
payChannel = callbackData.getString("paymentVendor");
rawBody = requestBody;  // 原始报文整体保存
```

**回调报文字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| merchantOrderNo | String | 商户订单号（优先用来定位我方 orderNo） |
| orderNo | String | 支付中心订单号（兜底 + 作为 tradeNo / paymentOrderNo） |
| channelOrderNo | String | 渠道订单号（兜底 tradeNo） |
| status | String | 支付状态 |
| payTime | String | 支付时间（三种格式） |
| totalAmount | Integer | 总金额（分） |
| cashAmount | Integer | 现金金额（分，优先） |
| couponAmount | Integer | 优惠金额（分） |
| paymentVendor | String | 支付渠道 |

> `merchantOrderNo` 和 `orderNo` 全空时返 `retCode=9999, retMsg="无效的支付回调参数"`。

**响应字段（`DailyTicketBaseResult`）**：retCode / retMsg / data。

**压测备注**：回调端点；写库（`DAILY_TICKET_ORDER` UPDATE）；不调外部网关；幂等 = 终态短路。

---

### 22. 日票退款结果回调

- **对外 URL**（4 别名）：
  - `POST /fep-app/ci/app/dailyTicket/payment/receiveRefundResult`
  - `POST /fep-app/app/dailyTicket/payment/receiveRefundResult`
  - `POST /fep-app/app/payment/receiveRefundResult`
  - `POST /fep-app/app/ticket/payment/receiveRefundResult`
- **Controller**：`AppDailyTicketController:140`
- **Content-Type**：`application/json`（`@RequestBody String`）
- **入参形态**：`@RequestBody String` -> 双形态解析：先 `parseCallbackBody(requestBody, DailyTicketRefundCallbackReqDTO.class)`；为 null 时 `parseBizData(JSON.parseObject(requestBody, ItpCommonFormRequest.class), DailyTicketRefundCallbackReqDTO.class)`
- **下游**：`DailyTicketClient.receiveRefundResult` -> daily-ticket-server `POST /ci/daily-ticket/receiveRefundResult`

**bizData 请求字段（`DailyTicketRefundCallbackReqDTO`，8 字段，支付中心网关 3.3）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| orderNo | String | 无（代码无 Bean Validation，空值不会 400） | 支付中心平台单号（2026-09-20 实测回显的是我方送出的 `PAYMENT_ORDER_NO`，拿它查订单表与退款单表一律 0 行） |
| merchantOrderNo | String | 无 | 2026-09-20 连续三笔实测均未实际下发，只能当可选校验 |
| refundResult | String | 无 | 退款结果 |
| refundResultDesc | String | 无 | 退款结果描述 |
| refundDate | String | 无 | 退款日期（yyyyMMddHHmmss） |
| refundAmount | String | 无 | 退款金额（注意是 String） |
| refundNo | String | 无 | 平台退款单号 -> `PLATFORM_REFUND_NO` |
| outRefundNo | String | 无 | 商户退款单号 -> `DAILY_TICKET_REFUND.REFUND_ORDER_NO` |

**响应字段（`DailyTicketBaseResult`）**：retCode / retMsg / data。

**压测备注**：回调端点；写库（`DAILY_TICKET_REFUND` UPDATE）；不调外部网关；幂等 = 终态短路 + `outRefundNo` 定位。

---

## C. CollectPayController（8 端点）

> 源文件：`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/CollectPayController.java`（89 行）
> 类级无 `@RequestMapping`
> **本 Controller 不注 service，直接注 `CollectPayClient`**，用 `toFormDataMap` 把公共字段 + bizData 原样透传，返回 `JSONObject`。Controller 侧不解析 DTO。
> **下游现为 face-pay-server**（`service.collectPay.url`，线上 env 已指向 `http://172.20.211.23:30025`）。
> **契约来源是下游 face-pay，fep-app 只透传。** 以下 bizData 字段表来自 face-pay 的 `AppOrderController` + `DeviceRequests.unwrap(form, XxxReqDTO.class)` 目标 DTO。
>
> 下游 face-pay APP 域错误码族（`AppResponses`）：`0000` 成功 / `8001` 下单与支付信息参数校验失败 / `8003` 查询类参数校验失败 / `8999` 支付中 / `9999` service 层通用失败。

### 23. 请求下单（IF8A-20）

- **对外 URL**：`POST /fep-app/ci/app/requestOrder`
- **容器内路径**：`/ci/app/requestOrder`
- **Controller**：`CollectPayController:28`
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest` -> `toFormDataMap` 原样透传
- **下游**：`CollectPayClient.requestOrder` -> face-pay-server `POST /ci/app/requestOrder` -> `AppOrderController:47`

**bizData 请求字段（契约来源：face-pay `RequestOrderReqDTO extends BaseDeviceRequest`）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| userId | String | 无（Controller 显式判空 -> `8001`） | 用户 ID（落 `F2F_ORDER.THIRD_USER_ID`） |
| entryStationCode | String | 无（Controller 显式判空 -> `8001`） | 进站站点 |
| exitStationCode | String | 无（Controller 显式判空 -> `8001`） | 出站站点 |
| ticketPrice | String | 无（Controller 显式判空 -> `8001`） | 票价（分）；带 `priceInFen()` 解析方法 |
| singelTicketNum | String | 无（Controller 显式判空 -> `8001`） | 购票数量（**拼写照搬**）；带 `ticketCount()` 解析方法 |
| singleTicketType | String | 无（Controller 显式判空 -> `8001`） | 0 按站点购票，其余按里程/区间 |

> face-pay `AppOrderController:47-70` 显式校验：`userId / entryStationCode / exitStationCode / ticketPrice / singelTicketNum / singleTicketType` 任一为空 -> `8001`。

**响应字段（`AppResponses.orderNo` 形态）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 0000 成功 / 8001 参数校验失败 |
| retMsg | String | 返回信息 |
| orderNo | String | 订单号 |

**压测备注**：写库（face-pay 的 `F2F_ORDER` INSERT）；不调外部网关；非回调端点。

---

### 24. 请求支付信息（IF8A-11）

- **对外 URL**：`POST /fep-app/ci/app/requestPaymentInfo`
- **Controller**：`CollectPayController:34`
- **下游**：face-pay `POST /ci/app/requestPaymentInfo` -> `AppOrderController:76`

**bizData 请求字段（契约来源：face-pay `RequestPayInfoReqDTO`）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| orderNo | String | 无（Controller 显式判空 -> `8001`） | 订单号 |
| payChannelCode | String | 无（Controller 显式判空 -> `8001`） | 支付渠道编码 |

> `orderNo` 以 `SupplementOrderService.ORDER_NO_PREFIX` 开头时走补款链路。

**响应字段（`AppResponses.payInfo` 形态）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 0000 / 8001 |
| retMsg | String | 返回信息 |
| payChannelCode | String | 支付渠道编码 |
| paymentInfo | String | 支付凭证 |
| signType | String | "00" |
| sign | String | "" |

**压测备注**：写库（`F2F_PAYMENT` INSERT 或 UPDATE）；会调支付中心外部网关；非回调端点。

---

### 25. 支付结果查询（IF8A-18）

- **对外 URL**：`POST /fep-app/ci/app/requestPayResult`
- **Controller**：`CollectPayController:40`
- **下游**：face-pay `POST /ci/app/requestPayResult` -> `AppOrderController:93`

**bizData 请求字段（契约来源：face-pay `RequestAppPayResultReqDTO`，支付结果查询/请求退款/退款结果查询三端点共用）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| userId | String | 无（Controller 显式判空 -> `8003`） | 用户 ID |
| orderNo | String | 无（Controller 显式判空 -> `8003`） | 订单号 |

**响应字段（`AppResponses.payResult` 形态）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 0000 / 8003 |
| retMsg | String | 返回信息 |
| tradeNo | String | 交易号 |
| payResult | String | SUCCESS / FAIL |
| payAmount | String | 支付金额 |
| payDate | String | 支付日期（原样回吐） |

**压测备注**：只读；非回调端点。

---

### 26. 请求退款

- **对外 URL**：`POST /fep-app/ci/app/requestRefundTicket`
- **Controller**：`CollectPayController:46`
- **下游**：face-pay `POST /ci/app/requestRefundTicket` -> `AppOrderController:122`

**bizData 请求字段（契约来源：face-pay，Controller 只校验 `orderNo`）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| orderNo | String | 无（Controller 显式判空 -> `8003`） | 订单号 |

**响应字段（`AppResponses.refund` 形态）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 0000 / 8003 |
| retMsg | String | 返回信息 |
| orderNo | String | 订单号 |
| refundType | String | "00" |
| refundDate | String | 退款日期 |
| refundAmount | String | 退款金额 |
| refundResult | String | PROCESSING / SUCCESS / FAIL |
| refundResultDesc | String | 退款结果描述 |
| notifyUrl | String | 非 null 才放 |

**压测备注**：写库（`F2F_REFUND` INSERT）；会调支付中心外部网关（发起退款）；非回调端点。

---

### 27. 退款结果查询

- **对外 URL**：`POST /fep-app/ci/app/requestRefundTicketResult`
- **Controller**：`CollectPayController:52`
- **下游**：face-pay `POST /ci/app/requestRefundTicketResult` -> `AppOrderController:133`

**bizData 请求字段（契约来源：face-pay `RequestAppPayResultReqDTO`）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| userId | String | 无（Controller 显式判空 -> `8003`） | 用户 ID |
| orderNo | String | 无（Controller 显式判空 -> `8003`） | 订单号 |

**响应字段**：同 #26 的 `AppResponses.refund` 形态。

**压测备注**：只读；非回调端点。

---

### 28. 查询激活订单列表

- **对外 URL**：`POST /fep-app/ci/app/requestPreActiveOrderList`
- **Controller**：`CollectPayController:58`
- **下游**：face-pay `POST /ci/app/requestPreActiveOrderList` -> `AppOrderController:107`

**bizData 请求字段（契约来源：face-pay `RequestQueryActiveOrderReqDTO`）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| userId | String | 无（Controller 显式判空 -> `8003`） | 用户 ID |
| appType | String | 无（Controller 显式判空 -> `8003`） | 应用类型（常量 `APP_TYPE_QD_METRO = "01"`） |

**响应字段（`AppResponses.orderList` 形态）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | 0000 / 8003 |
| retMsg | String | 返回信息 |
| orderList | JSONArray | 订单列表 |

**压测备注**：只读；非回调端点。

---

### 29. TVM 激活请求

- **对外 URL**：`POST /fep-app/ci/app/requestActiveTicket`
- **Controller**：`CollectPayController:64`
- **下游**：`CollectPayClient.requestActiveTicket` -> face-pay-server `POST /itptvm/ci/tvm/requestActiveTicket`（**注意：下游路径是 `/itptvm/ci/tvm/` 前缀**，落在 face-pay 的 `TvmOrderController:189`，不是 `AppOrderController`）
- **异常现象**：这是 CollectPayController 8 个端点中**唯一一个下游路径不是 `/ci/app/` 的**，落在 face-pay 的 TVM 域 `TvmOrderController`，错误码族是 TVM `2xxx`（`TvmResponses`），不是 APP `8xxx`。

**bizData 请求字段（契约来源：face-pay `RequestActiveTicketReqDTO extends BaseDeviceRequest`）**：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| orderNo | String | 无（Controller 显式判空 -> TVM `INVALID_PARAM`） | 订单号 |
| qrcodeGenDate | String | 无（Controller 显式判空 -> TVM `INVALID_PARAM`） | 二维码生成日期 |
| randomFact | String | 无（Controller 显式判空 -> TVM `INVALID_PARAM`） | 随机因子 |
| deviceId | String | 无（从 `BaseDeviceRequest` 继承，Controller 显式判空） | 设备号 |

> face-pay `TvmOrderController:189-198` 显式校验：`orderNo / deviceId / qrcodeGenDate / randomFact` 任一为空 -> `TvmResponses.fail(DeviceRetCode.INVALID_PARAM)`（TVM 域 `2xxx` 码族）。

**响应字段（TVM 域 `TvmResponses` 形态）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | String | TVM 码族 2xxx |
| retMsg | String | 返回信息 |

**压测备注**：写库（`F2F_TICKET` UPDATE）；不调外部网关；非回调端点。

---

### 30. 接收退款结果通知

- **对外 URL**：`POST /fep-app/ci/app/receiveRefundResult`
- **Controller**：`CollectPayController:70`
- **下游**：`CollectPayClient.receiveRefundResult` -> face-pay-server `POST /ci/app/receiveRefundResult` -> `AppOrderController:147`
- **注意**：此 URL 与 PaySignController #4 的退款回调路径冲突，因此 #4 特意加了 `paySign/payment` 中缀避让。

**bizData 请求字段（契约来源：face-pay `AppRefundNotiResultReqDTO`，不继承 BaseDeviceRequest）**：

face-pay 侧用 `@ModelAttribute PayCenterCallbackRequest form` + `JSON.parseObject(form.getBizData(), AppRefundNotiResultReqDTO.class)`。

`PayCenterCallbackRequest` 信封（6 字段）：merchantNo / apiVersion / signType / sign / charset / bizData。

`AppRefundNotiResultReqDTO` 内层（7 字段）：

| 字段 | 类型 | 校验注解 | 说明 |
|------|------|----------|------|
| orderNo | String | 无（Controller 显式判空 -> `8003`） | 我方订单号 |
| refundNo | String | 无（Controller 显式判空 -> `8003`） | 我方退款单号（幂等键） |
| outRefundNo | String | 无 | 支付中心侧退款单号 |
| refundResult | String | 无（Controller 显式判空 -> `8003`） | SUCCESS / FAIL |
| refundResultDesc | String | 无 | 退款结果描述 |
| refundDate | String | 无 | 退款日期 |
| refundAmount | String | 无 | 退款金额 |

> face-pay `AppOrderController:147-165` 显式校验：`bizData / orderNo / refundResult / refundNo` 任一为空 -> `8003`。

**响应字段**：同 APP 域 `AppResponses.success()` 形态（retCode / retMsg）。

**压测备注**：回调端点；写库（`F2F_REFUND` UPDATE）；不调外部网关；幂等 = 终态短路 + `refundNo` 定位。
