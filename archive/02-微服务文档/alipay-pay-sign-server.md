# alipay-pay-sign-server 微服务文档

> **模块路径**: `alipay-pay-sign-server/`
> **端口**: 8080
> **职责**: 支付签约服务，处理地铁 APP 渠道的签约/解约/支付/退款/查询/通知
> **渠道标识**: 地铁 APP 渠道（METRO_APP）
> **架构说明**: 与 `alipay-pay-sign-server` 并行存在，分别服务不同支付渠道（本服务面向地铁 APP，alipay-pay-sign-server 面向支付宝出行），非新旧版本关系
> **整理时间**: 2026-07-20

---

## 一、模块概述

### 1.1 核心职责

alipay-pay-sign-server 是支付宝渠道的支付签约服务，承担以下核心职责：

1. **支付宝签约** - 添加签约信息（代扣协议开通）
2. **支付宝解约** - 解约登记 + 执行解约
3. **支付宝支付** - 支付申请，调用支付中心
4. **支付宝退款** - 退款申请，调用支付中心
5. **支付查询** - 支付结果查询 + 支付回调处理
6. **黑名单通知** - 黑名单状态变更通知支付中心
7. **订单管理** - 支付流水查询（列表/详情/按条件查询）

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| Alipay SDK / PayCenterClient | 支付中心 RPC 调用 |
| MyBatis | ORM 框架 |
| Oracle | 数据库 |

### 1.3 端口配置

```properties
server.port=8080
```

---

## 二、接口清单

### 2.1 签约接口（基础路径：`/channel`）

**Controller**：`AlipayPaySignController`

#### 2.1.1 添加签约信息

- **路径**：`POST /channel/addContract`
- **Service**：`AlipayContractServiceImpl.addContract`

**请求参数** (`AlipayTripAddContractReqDTO`)：

```java
String channel;              // 支付渠道，固定 ALIPAY
String thirdUserId;          // 第三方用户ID
String agreementCode;        // 签约协议号（系统生成）
String channelAgreementCode; // 渠道协议号
String channelUserAccount;   // 渠道用户账户
String cardIssueCode;        // 发卡类型代码，如 0007
```

**响应参数** (`AlipayTripAddContractRespDTO` extends CommonResult)：

```java
String retCode;      // 返回码
String retMsg;       // 返回消息
String agreementCode; // 签约协议号
```

**业务逻辑**：

1. 参数校验：`thirdUserId`、`channel`、`agreementCode`、`channelUserAccount` 必填。
2. 重复签约检查：根据 `thirdUserId` + `channel=ALIPAY` 查询 `ALIPAY_SIGN_INFO`，若已存在返回已有 `agreementCode`。
3. 开户检查：调用 `AlipayAccountClient.selectByThirdUserId` 校验用户是否已开户。
4. 插入 `ALIPAY_SIGN_INFO`：`signStatus=SIGNED`，`operationType=SIGN`，`deleteFlag="0"`，`version="1"`。
5. 调用 `AlipayAccountClient.updatePaymentChannel` 更新用户支付通道。
6. 记录签约流水 `ALIPAY_SIGN_LOG`（`SignLogRecorder.recordSignSuccess`）。
7. 返回 `agreementCode`。

---

#### 2.1.2 解约登记

- **路径**：`POST /channel/terminateContract`
- **Service**：`AlipayContractServiceImpl.terminateContract` → `TerminationRegistrationService.terminateContract`

**请求参数** (`AlipayTripTerminateContractReqDTO`)：

```java
String agreementCode; // 签约协议号
```

**响应参数** (`AlipayTripTerminateContractRespDTO` extends CommonResult)：

```java
String retCode;      // 返回码
String retMsg;       // 返回消息
```

**业务逻辑**：

1. 参数校验：`agreementCode` 必填。
2. 查询 `ALIPAY_SIGN_INFO` by `agreementCode`。
3. 插入 `ALIPAY_TERMINATION_REQUEST`：`status=PENDING`，`operationType=TERMINATE`。
4. 记录签约流水 `ALIPAY_SIGN_LOG`（`operationType=TERMINATE`）。
5. 返回成功。

---

#### 2.1.3 查询签约信息

- **路径**：`GET /channel/selectSignInfo`
- **Service**：`AlipayContractServiceImpl.selectSignInfo`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `thirdUserId` | String | 是 | 第三方用户ID |

**响应参数** (`AlipaySignInfoDTO`)：

```java
String thirdUserId;         // 第三方用户ID
String channelAgreementCode;// 渠道协议号
String cardId;              // 逻辑卡号
String cardType;            // 卡类型
```

---

#### 2.1.4 执行解约

- **路径**：`GET /channel/executeTermination`
- **Service**：`AlipayContractServiceImpl.executeTermination`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `agreementCode` | String | 是 | 签约协议号 |

**响应参数** (`AlipayCommonResponse`)：

```java
String retCode; // 返回码
String retMsg;  // 返回消息
```

**业务逻辑**：

1. 参数校验：`agreementCode` 必填。
2. 查询 `ALIPAY_SIGN_INFO` by `agreementCode`，若不存在返回失败。
3. 更新签约状态为 `TERMINATED`，`terminationTime=now`。
4. 调用 `PaymentNotifyAdapter.notifyCloseResult` 通知支付平台解约结果。
5. 返回支付平台响应。

---

#### 2.1.5 查询乘车记录详情

- **路径**：`POST /channel/findTravelDetail`
- **Service**：`AlipayTripPaymentService.findTravelDetail` → `PaymentQueryService.findTravelDetail`

**请求参数** (`AlipayTripFindTravelDetailReqDTO`)：

```java
String orderNo;  // 订单号/交易流水号
```

**响应参数** (`AlipayTripFindTravelDetailRespDTO` extends CommonResult)：

```java
String retCode;           // 返回码
String retMsg;            // 返回消息
String payOrderNoDate;    // 支付订单日期（= TRANS_TIME）
String tradeOrderNo;      // 交易订单号（= TRADE_NO）
String payTradeOrderNo;   // 支付交易订单号（= TRADE_NO）
String payChannelCode;    // 支付渠道代码，固定 ALIPAY
String totalAmount;       // 总金额（分）
String debitRequestResult;// 扣款请求结果（= resultMsg）
```

> **注意**：此接口从 `ALIPAY_PAY_LOG` 查询支付流水，返回支付信息，不是 ticket-server 的闸机交易详情。`payOrderNoDate` 映射自 `TRANS_TIME`，`tradeOrderNo`/`payTradeOrderNo` 均映射自 `TRADE_NO`。

---

#### 2.1.6 黑名单状态变更通知

- **路径**：`POST /channel/notify/blackListChange`
- **Service**：`AlipayTripPaymentService.notifyBlackListChange` → `PaymentNotifyAdapter.notifyBlackListChange`

**请求参数** (`AlipayBlackListNotifyReqDTO`)：

```java
String thirdUserId;     // 第三方用户ID
String cardId;          // 卡号
String cardType;        // 卡类型
String blackListType;   // 黑名单类型：1-加入黑名单
String reason;          // 原因
String optionDate;      // 操作日期
String expireTime;      // 过期时间（blackListType=1 时必填）
```

**响应参数** (`AlipayCommonResponse`)：

```java
String retCode; // 返回码
String retMsg;  // 返回消息
```

**业务逻辑**：

1. 参数校验：`cardId` 必填。
2. 构建黑名单通知报文，调用 `PayCenterClient.blacklistNotify`。
3. 返回支付中心响应结果。

---

### 2.2 支付接口（基础路径：`/api/payment`）

**Controller**：`AlipayTripPaymentController`

#### 2.2.1 支付申请

- **路径**：`POST /api/payment/requestPay`
- **Service**：`AlipayTripPaymentService.requestPay` → `PaymentRequestService.requestPay`

**请求参数** (`AlipayTripRequestPayReqDTO`)：

```java
String orderNo;       // 订单号
String amount;        // 支付金额（分）
String industryType;  // 行业类型：1-地铁
String subject;       // 订单标题
String body;           // 订单描述
String industryDetail;// 行业详情，JSON格式
String requestSignSeq;// 签约流水号（免密场景）
```

**响应参数** (`AlipayTripRequestPayRespDTO`)：

```java
String retCode;   // 返回码
String retMsg;    // 返回消息
String orderNo;   // 订单号
```

**业务逻辑**：

1. 参数校验：`orderNo`、`amount`、`industryType`、`subject`、`body`、`industryDetail` 必填。
2. 签约检查：根据 `thirdUserId` + `channel=ALIPAY` 查询签约信息。
3. 幂等检查：若订单已存在，直接返回已有状态。
4. 行业详情 enrichment：`IndustryDetailEnricher.enrich` 补充用户信息。
5. 构建支付日志 `ALIPAY_PAY_LOG` 并插入。
6. 调用 `PayCenterClient.requestPay` 发起支付。
7. 更新支付日志状态：`SUCCESS`/`FAIL`。
8. 返回结果。

---

#### 2.2.2 支付结果查询

- **路径**：`POST /api/payment/payQuery`
- **Service**：`AlipayTripPaymentService.payQuery` → `PaymentQueryService.payQuery`

**请求参数** (`AlipayTripPayQueryReqDTO`)：

```java
String orderNo;           // 订单号
String channelAgreementNo;// 渠道协议号（可选）
```

**响应参数** (`AlipayTripPayQueryRespDTO`)：

```java
String retCode;      // 返回码
String retMsg;       // 返回消息
String outTradeNo;   // 订单号
String paymentTime;  // 支付时间（TRANS_TIME）
String tradeStatus;  // 支付状态 SUCCESS/FAIL
String totalAmount;  // 支付金额
String tradeNo;      // 支付宝交易号
String tradeDesc;    // 支付结果描述
```

**业务逻辑**：

1. 参数校验：`orderNo` 必填。
2. 查询本地 `ALIPAY_PAY_LOG`。
3. 调用 `PayCenterClient.payQuery` 查询支付中心。
4. **解密数据日志**：通过 `PayCenterClient.getStringFromData` 解密 `data` 后，在取值后、回写前增加解密数据日志。
5. 若支付中心返回成功，更新本地流水：`tradeNo`、`payAmount`、`transTime`、`payStatus`。
6. 若支付中心返回失败，更新本地流水状态为 `FAIL`。
7. 重新查询本地流水，返回最新状态。

---

#### 2.2.3 退款申请

- **路径**：`POST /api/payment/requestRefund`
- **Service**：`AlipayTripPaymentService.requestRefund` → `PaymentRefundService.requestRefund`

**请求参数** (`AlipayTripRequestRefundReqDTO`)：

```java
String orderNo;           // 原订单号
String cardIssueCode;     // 卡机构编号，支付宝 0007
String cardNum;           // 逻辑卡号
String channelAgreementNo;// 渠道协议号
String refundAmount;      // 退款金额，单位分（允许为空，自动计算）
String refundOrderNo;     // 退款订单号（可选）
```

**响应参数** (`AlipayTripRequestRefundRespDTO`)：

```java
String retCode; // 返回码
String retMsg;  // 返回消息
```

**业务逻辑**：

1. 参数校验：`orderNo` 必填。
2. 查询原支付记录，必须状态为 `SUCCESS`。
3. 查询签约信息获取 `channelAgreementNo`。
4. 计算退款金额：`RefundAmountCalculator.resolveRefundAmount`，允许 `refundAmount` 为空。
5. 校验退款金额：`RefundAmountCalculator.validateRefundAmount`。
6. 插入 `ALIPAY_REFUND_LOG`，状态 `FAIL`（待处理）。
7. 调用 `PayCenterClient.requestRefund` 发起退款。
8. **退款成功条件**：`payCenterResponse.getCode() == 200`，其他任何响应都视为退款失败。
9. 更新退款日志状态，若成功则更新 `ALIPAY_PAY_LOG` 退款汇总。
10. 失败时必须将支付中心错误原因回填到响应。

---

#### 2.2.4 支付结果回调

- **路径**：`POST /api/payment/payNotify`
- **Service**：`AlipayTripPaymentService.handlePayNotify` → `PaymentQueryService.handlePayNotify`

**请求参数** (`AlipayTripPayNotifyReqDTO`)：

```java
String orderNo;           // 订单号
String transStatus;       // 交易状态：1-成功
String channelVoucherId;  // 渠道凭证号
String transTime;         // 交易时间
String transAmount;       // 交易金额
```

**响应参数** (`AlipayCommonResponse`)：

```java
String retCode; // 返回码
String retMsg;  // 返回消息
```

**业务逻辑**：

1. 参数校验：`orderNo` 必填。
2. 查询本地支付流水。
3. **幂等性检查**：如果已经是 `SUCCESS`，直接返回成功。
4. 根据 `transStatus` 判断支付状态。
5. 更新本地流水（异常捕获 + 日志）。
6. 返回成功。

---

### 2.3 订单查询接口（基础路径：`/api/payment/payLog`）

**Controller**：`AlipayPayLogController`

#### 2.3.1 查询订单列表

- **路径**：`GET /api/payment/payLog/list`
- **路径**：`POST /api/payment/payLog/list`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `thirdUserId` | String | 否 | 第三方用户ID |
| `startTime` | String | 否 | 开始时间 |
| `endTime` | String | 否 | 结束时间 |
| `pageNum` | int | 否 | 页码，默认 1 |
| `pageSize` | int | 否 | 每页大小，默认 10 |

**响应参数** (`PageResult<AlipayPayLogVO>`)：

```java
long total;           // 总记录数
List<AlipayPayLogVO> list; // 订单列表
```

---

#### 2.3.2 订单详情

- **路径**：`GET /api/payment/payLog/detail`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `orderNo` | String | 是 | 订单号 |

**响应参数**：`AlipayPayLogVO`

---

#### 2.3.3 按进站交易ID查询

- **路径**：`GET /api/payment/payLog/entryId`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `entryId` | String | 是 | 进站交易ID |

**响应参数**：`AlipayPayLogVO`

---

#### 2.3.4 按出站交易ID查询

- **路径**：`GET /api/payment/payLog/exitId`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `exitId` | String | 是 | 出站交易ID |

**响应参数**：`AlipayPayLogVO`

---

#### 2.3.5 按乘车记录查询

- **路径**：`POST /api/payment/payLog/queryByTravelRecord`

**请求参数** (Map)：

```java
String thirdUserId; // 第三方用户ID
String entryDate;   // 进站日期
String cardNum;     // 卡号
```

**响应参数**：`AlipayPayLogVO`

---

#### 2.3.6 查询乘车记录列表（支付宝出行专用）

- **路径**：`GET /api/payment/payLog/travelList`
- **路径**：`POST /api/payment/payLog/travelList`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `thirdUserId` | String | 是 | 第三方用户ID |
| `startDate` | String | 否 | 开始日期 |
| `endDate` | String | 否 | 结束日期 |
| `debitRequestResult` | String | 否 | 扣款结果 |
| `invoice` | String | 否 | 发票状态 |
| `pageNum` | int | 否 | 页码，默认 0 |
| `pageSize` | int | 否 | 每页大小，默认 10 |

**响应参数**：`PageResult<AlipayPayLogVO>`

---

## 三、数据模型

### 3.1 ALIPAY_SIGN_INFO 实体（model 模块）

**路径**：`model/src/main/java/com/chinasofti/huateng/model/alipaytrip/AlipaySignInfo.java`

| 字段 | 类型 | 说明 |
|------|------|------|
| `agreementCode` | String | 签约协议号（主键） |
| `thirdUserId` | String | 支付宝用户ID |
| `cardId` | String | 逻辑卡号 |
| `cardType` | String | 卡类型编码 |
| `channelAgreementCode` | String | 渠道协议号 |
| `channelUserAccount` | String | 渠道用户账户 |
| `channel` | String | 渠道编码，固定 ALIPAY |
| `signStatus` | String | 签约状态：SIGNED/TERMINATED |
| `operationType` | String | 操作类型：SIGN/TERMINATE |
| `signTime` | LocalDateTime | 签约时间 |
| `terminationTime` | LocalDateTime | 解约时间 |
| `deleteFlag` | String | 删除标记（0:正常 1:删除） |
| `version` | String | 乐观锁版本号 |
| `createTime` | LocalDateTime | 创建时间 |
| `updateTime` | LocalDateTime | 更新时间 |

### 3.2 ALIPAY_SIGN_LOG 实体

**路径**：`alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/entity/AlipaySignLog.java`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 主键ID |
| `requestSeq` | String | 请求流水号 |
| `responseSeq` | String | 响应流水号 |
| `thirdUserId` | String | 第三方用户ID |
| `cardId` | String | 逻辑卡号 |
| `cardType` | String | 卡类型 |
| `channel` | String | 渠道编码 |
| `agreementCode` | String | 签约协议号 |
| `operationType` | String | 操作类型：SIGN/TERMINATE |
| `requestBody` | String | 请求报文 |
| `responseBody` | String | 响应报文 |
| `resultCode` | String | 结果码 |
| `resultMsg` | String | 结果消息 |
| `operator` | String | 操作人 |
| `ip` | String | IP地址 |
| `remark` | String | 备注 |
| `version` | String | 版本号 |
| `createTime` | LocalDateTime | 创建时间 |
| `createBy` | String | 创建人 |
| `deleteFlag` | String | 删除标记 |
| `updateTime` | LocalDateTime | 更新时间 |

### 3.3 ALIPAY_PAY_LOG 实体

**路径**：`alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/entity/AlipayPayLog.java`

| 字段 | 类型 | 说明 |
|------|------|------|
| `paySeq` | String | 支付流水号（主键） |
| `thirdUserId` | String | 支付宝用户ID |
| `cardId` | String | 逻辑卡号 |
| `orderNo` | String | 订单号 |
| `tradeNo` | String | 支付宝交易号 |
| `payAmount` | String | 支付金额（分） |
| `payStatus` | String | 支付状态 SUCCESS/FAIL |
| `payType` | String | 支付类型 |
| `scene` | String | 支付场景 |
| `paymentVendor` | String | 支付方式 |
| `industryType` | String | 行业类型：1-地铁 |
| `subject` | String | 订单标题 |
| `body` | String | 订单描述 |
| `requestSignSeq` | String | 签约流水号 |
| `orderTimeOut` | String | 订单超时时间（秒） |
| `authCode` | String | 授权码 |
| `notifyUrl` | String | 回调地址 |
| `returnUrl` | String | 返回页面地址 |
| `ipAddress` | String | 用户IP |
| `industryDetail` | String | 行业详情（JSON） |
| `requestBody` | String | 原始请求报文 |
| `responseBody` | String | 原始响应报文 |
| `resultCode` | String | 响应码 |
| `resultMsg` | String | 响应信息 |
| `remark` | String | 备注 |
| `refundAmount` | String | 已退款金额（分） |
| `refundStatus` | String | 退款状态 SUCCESS/PARTIAL/NONE |
| `entryId` | String | 进站交易ID |
| `exitId` | String | 出站交易ID |
| `invoice` | String | 发票状态 |
| `transTime` | String | 交易时间（yyyy-MM-dd HH:mm:ss） |

### 3.4 ALIPAY_REFUND_LOG 实体

**路径**：`alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/entity/AlipayRefundLog.java`

| 字段 | 类型 | 说明 |
|------|------|------|
| `refundSeq` | String | 退款流水号（主键） |
| `thirdUserId` | String | 支付宝用户ID |
| `cardId` | String | 逻辑卡号 |
| `orderNo` | String | 原订单号 |
| `refundAmount` | String | 退款金额（分） |
| `refundStatus` | String | 退款状态 SUCCESS/FAIL |
| `refundReason` | String | 退款原因 |
| `cardIssueCode` | String | 卡机构编号，支付宝 0007 |
| `channelAgreementNo` | String | 渠道协议号 |
| `refundOrderNo` | String | 退款订单号 |
| `requestBody` | String | 原始请求报文 |
| `responseBody` | String | 原始响应报文 |
| `resultCode` | String | 响应码 |
| `resultMsg` | String | 响应信息 |
| `operator` | String | 操作人 |
| `ip` | String | 请求IP |
| `remark` | String | 备注 |
| `deleteFlag` | String | 删除标记（0:正常 1:删除） |
| `version` | String | 乐观锁版本号 |
| `createTime` | LocalDateTime | 创建时间 |
| `updateTime` | LocalDateTime | 更新时间 |
| `createBy` | String | 创建人 |

### 3.5 ALIPAY_TERMINATION_REQUEST 实体

**路径**：`alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/entity/AlipayTerminationRequest.java`

| 字段 | 类型 | 说明 |
|------|------|------|
| `terminationSeq` | String | 解约流水号（主键） |
| `agreementCode` | String | 签约协议号 |
| `thirdUserId` | String | 第三方用户ID |
| `cardId` | String | 逻辑卡号 |
| `cardType` | String | 卡类型 |
| `channel` | String | 渠道编码 |
| `merchantNo` | String | 商户号 |
| `operationType` | String | 操作类型 |
| `status` | String | 状态：PENDING |
| `terminationResult` | String | 解约结果 |
| `terminationTime` | LocalDateTime | 解约时间 |
| `deleteFlag` | String | 删除标记 |
| `version` | String | 版本号 |
| `createTime` | LocalDateTime | 创建时间 |
| `updateTime` | LocalDateTime | 更新时间 |

---

## 四、核心业务流程

### 4.1 支付申请流程

**入口**：`AlipayTripPaymentController.requestPay`

**业务逻辑**（`PaymentRequestService.requestPay`）：

1. 参数校验：`orderNo`、`amount`、`industryType`、`subject`、`body`、`industryDetail` 必填。
2. 签约检查：查询 `ALIPAY_SIGN_INFO` by `thirdUserId` + `channel=ALIPAY`，未签约返回 `USER_NOT_SIGNED`。
3. 幂等检查：若订单已存在，直接返回已有状态。
4. 行业详情 enrichment：`IndustryDetailEnricher.enrich` 补充用户信息到 `industryDetail`。
5. 构建支付日志：`PayLogBuilder.build` 生成 `ALIPAY_PAY_LOG`，`requestSignSeq=agreementCode`。
6. 调用支付中心：`PayCenterClient.requestPay` 发起支付请求。
7. 更新支付状态：根据支付中心响应更新 `payStatus`、`tradeNo`、`resultCode`、`resultMsg`。
8. 返回结果。

---

### 4.2 支付结果查询流程

**入口**：`AlipayTripPaymentController.payQuery`

**业务逻辑**（`PaymentQueryService.payQuery`）：

1. 参数校验：`orderNo` 必填。
2. 查询本地 `ALIPAY_PAY_LOG`。
3. 调用 `PayCenterClient.payQuery` 查询支付中心。
4. **解密数据日志**：通过 `PayCenterClient.getStringFromData` 解密 `data` 后，记录解密后数据日志。
5. 若支付中心返回成功：
   - 提取 `channelOrderNo`、`transAmount`、`transTime`、`transStatus`。
   - 更新本地流水 `tradeNo`、`payAmount`、`transTime`、`payStatus`、`resultCode`、`resultMsg`。
6. 若支付中心返回失败：
   - 更新本地流水状态为 `FAIL`，`resultMsg=支付中心错误原因`。
7. 重新查询本地流水，返回最新状态。

---

### 4.3 支付回调处理流程

**入口**：`AlipayTripPaymentController.payNotify`

**业务逻辑**（`PaymentQueryService.handlePayNotify`）：

1. 参数校验：`orderNo` 必填。
2. 查询本地支付流水。
3. **幂等性检查**：如果已经是 `SUCCESS`，直接返回成功。
4. 根据 `transStatus` 判断支付状态：`1` → `SUCCESS`，其他 → `FAIL`。
5. 更新本地流水（异常捕获 + 日志）。
6. 返回成功。

---

### 4.4 退款申请流程

**入口**：`AlipayTripPaymentController.requestRefund`

**业务逻辑**（`PaymentRefundService.requestRefund`）：

1. 参数校验：`orderNo` 必填。
2. 查询原支付记录，必须状态为 `SUCCESS`。
3. 查询签约信息获取 `channelAgreementNo`。
4. 计算退款金额：`RefundAmountCalculator.resolveRefundAmount`，允许 `refundAmount` 为空。
5. 校验退款金额：`RefundAmountCalculator.validateRefundAmount`。
6. 插入 `ALIPAY_REFUND_LOG`，状态 `FAIL`（待处理）。
7. 调用 `PayCenterClient.requestRefund` 发起退款。
8. **退款成功条件**：`payCenterResponse.getCode() == 200`。
9. 更新退款日志状态：
   - 成功：`SUCCESS`，更新 `ALIPAY_PAY_LOG` 退款汇总（`refundAmount`、`refundStatus`）。
   - 失败：`FAIL`，错误原因回填到 `resultMsg`。
10. 返回结果（失败时错误原因必须回填到前台响应）。

---

### 4.5 签约流程

**入口**：`AlipayPaySignController.addContract`

**业务逻辑**（`AlipayContractServiceImpl.addContract`）：

1. 参数校验：`thirdUserId`、`channel`、`agreementCode`、`channelUserAccount` 必填。
2. 重复签约检查：查询 `ALIPAY_SIGN_INFO` by `thirdUserId` + `channel=ALIPAY`。
3. 开户检查：调用 `AlipayAccountClient.selectByThirdUserId`。
4. 插入 `ALIPAY_SIGN_INFO`：`signStatus=SIGNED`，`deleteFlag="0"`，`version="1"`。
5. 调用 `AlipayAccountClient.updatePaymentChannel` 更新用户支付通道。
6. 记录签约流水 `ALIPAY_SIGN_LOG`（`SignLogRecorder.recordSignSuccess`）。
7. 返回 `agreementCode`。

> **注意**：`SignLogRecorder` 的 `response` 参数可能为 `AlipayTripAddContractRespDTO`，需通过 `instanceof` 分支取值，避免强转 `AlipayCommonResponse` 的 `ClassCastException`。

---

### 4.6 解约流程

**入口**：`AlipayPaySignController.terminateContract`

**业务逻辑**（`TerminationRegistrationService.terminateContract`）：

1. 参数校验：`agreementCode` 必填。
2. 查询 `ALIPAY_SIGN_INFO` by `agreementCode`。
3. 插入 `ALIPAY_TERMINATION_REQUEST`：`status=PENDING`，`operationType=TERMINATE`。
4. 记录签约流水 `ALIPAY_SIGN_LOG`（`operationType=TERMINATE`）。
5. 返回成功。

---

### 4.7 执行解约流程

**入口**：`AlipayPaySignController.executeTermination`

**业务逻辑**（`AlipayContractServiceImpl.executeTermination`）：

1. 参数校验：`agreementCode` 必填。
2. 查询 `ALIPAY_SIGN_INFO` by `agreementCode`，若不存在返回失败。
3. 更新签约状态为 `TERMINATED`，`terminationTime=now`。
4. 调用 `PaymentNotifyAdapter.notifyCloseResult` 通知支付平台解约结果。
5. 返回支付平台响应。

---

## 五、服务交互

### 5.1 上游调用

| 服务 | 调用方式 | 接口 | 说明 |
|------|----------|------|------|
| `fep-alipay-server` | HTTP | `/channel/addContract` | 添加签约信息 |
| `fep-alipay-server` | HTTP | `/channel/terminateContract` | 解约登记 |
| `fep-alipay-server` | HTTP | `/channel/selectSignInfo` | 查询签约信息 |
| `fep-alipay-server` | HTTP | `/channel/executeTermination` | 执行解约 |
| `fep-alipay-server` | HTTP | `/channel/findTravelDetail` | 查询乘车记录详情 |
| `fep-alipay-server` | HTTP | `/channel/notify/blackListChange` | 黑名单变更通知 |
| `fep-alipay-server` | HTTP | `/api/payment/requestPay` | 支付申请 |
| `fep-alipay-server` | HTTP | `/api/payment/payQuery` | 支付查询 |
| `fep-alipay-server` | HTTP | `/api/payment/requestRefund` | 退款申请 |
| `fep-alipay-server` | HTTP | `/api/payment/payNotify` | 支付回调 |
| `fep-alipay-server` | HTTP | `/api/payment/payLog/*` | 订单查询 |

### 5.2 下游依赖

| 服务 | 调用方式 | 接口/方法 | 说明 |
|------|----------|-----------|------|
| `alipay-account-server` | RPC | `selectByThirdUserId` | 查询用户信息 |
| `alipay-account-server` | RPC | `updatePaymentChannel` | 更新支付通道 |
| `支付中心` | RPC | `requestPay/payQuery/requestRefund/blacklistNotify/closeResultNotify` | 支付中心接口 |

### 5.3 交互图

```
┌──────────┐    签约/支付/退款    ┌──────────────────┐
│ fep-alipay │ ──────────────────► │ alipay-pay-sign │
│   -server  │                     │     -server      │
└──────────┘                     └────────┬─────────┘
                                          │
                                          │ RPC
                                          ▼
                                  ┌──────────────────┐
                                  │ alipay-account   │
                                  │     -server      │
                                  └──────────────────┘

                                          │
                                          │ PayCenterClient
                                          ▼
                                  ┌──────────────────┐
                                  │    支付中心        │
                                  └──────────────────┘
```

---

## 六、文件清单

### 6.1 Java 源文件

**控制器层**：
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/controller/AlipayPaySignController.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/controller/AlipayTripPaymentController.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/controller/AlipayPayLogController.java`

**服务层**：
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/AlipayContractService.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/AlipayTripPaymentService.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/AlipayPayLogQueryService.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/AlipayPaySignService.java`

**服务实现层**：
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/impl/AlipayContractServiceImpl.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/impl/AlipayTripPaymentServiceImpl.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/impl/AlipayPayLogQueryServiceImpl.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/impl/PaymentRequestService.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/impl/PaymentRefundService.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/impl/PaymentQueryService.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/impl/PaymentNotifyAdapter.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/impl/SignLogRecorder.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/impl/TerminationRegistrationService.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/impl/TerminationNotifier.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/impl/BizDataBuilder.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/impl/PayLogBuilder.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/impl/IndustryDetailEnricher.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/impl/RefundAmountCalculator.java`

**实体层**：
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/entity/AlipayPayLog.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/entity/AlipayRefundLog.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/entity/AlipaySignLog.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/entity/AlipayTerminationRequest.java`

**Mapper 层**：
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/mapper/AlipayPayLogMapper.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/mapper/AlipayRefundLogMapper.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/mapper/AlipaySignInfoMapper.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/mapper/AlipaySignLogMapper.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/mapper/AlipayTerminationRequestMapper.java`

**工具类**：
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/util/PayCenterClient.java`
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/config/PayCenterProperties.java`

**启动类**：
- `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/AlipayPaySignServer.java`

### 6.2 资源文件

1. `alipay-pay-sign-server/src/main/resources/application.properties`
2. `alipay-pay-sign-server/src/main/resources/application.yml`

---

## 七、相关文档

- `docs/03-接口文档/接口改造记录/签约渠道与支付宝出行接口改造记录.md` - 签约渠道改造记录
- `docs/01-项目概述/支付宝乘车业务流.md` - 支付宝乘车业务流图

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/alipay-pay-sign-server` 模块源码。
