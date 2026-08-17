# pay-sign-server 微服务文档

> **模块路径**: `pay-sign-server/`
> **端口**: 9096
> **职责**: 支付签约服务，负责签约/解约/支付/退款，对接支付网关，使用 OkHttp 调用支付平台
> **源码阅读范围**: `pay-sign-server/src/main/java/`、`pay-sign-server/src/main/resources/`
> **关联文档**: `docs/03-接口文档/接口设计对比/接口规范与项目实现对照表.md`
> **整理时间**: 2026-07-20

---

## 一、模块概述

### 1.1 核心职责

pay-sign-server 是 ITP 平台的支付签约服务，承担以下核心职责：

1. **签约管理** - IF8A-16 请求签约信息、IF8A-21 信用能力咨询、IF8A-22 签约结果查询
2. **解约管理** - IF8A-06 请求解约、IPD03 解约结果回调
3. **支付管理** - 支付 API 1.1 请求支付、支付 API 5.1 支付结果回调
4. **退款管理** - 支付 API 3.1 请求退款
5. **异步通知** - 签约/解约结果异步通知地铁 APP（IF8B-02）
6. **支付宝出行渠道** - 支付宝出行签约/解约/支付/退款，采用同步确认模式

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| MyBatis | 数据访问层 |
| Oracle | 数据库 |
| FastJSON2 | JSON 序列化 |
| OkHttp | HTTP 客户端，调用支付网关 |
| SLF4J | 日志 |

### 1.3 端口配置

```properties
# application.properties
server.port=9096
spring.application.name=pay-sign
```

### 1.4 数据表

| 表名 | 说明 |
|------|------|
| `APP_PAY_SIGN_INFO` | 签约主表，存储用户签约信息 |
| `APP_PAY_SIGN_REQUEST` | 签约流水表，记录所有签约/解约/回调流水 |
| `PAY_TXN_DETAIL` | 支付订单明细表 |
| `PAY_REFUND_DETAIL` | 退款明细表 |
| `PAY_CALLBACK_LOG` | 支付/退款回调流水表 |

---

## 二、接口清单

### 2.1 地铁 APP 接口（IF8A 系列）

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 请求 DTO | 响应 DTO |
|---------|---------|---------|---------|---------|---------|
| IF8A-16 | 请求签约信息 | `/ci/app/requestSignInfo` | POST | `RequestSignInfoReqDTO` | `RequestSignInfoResult` |
| IF8A-21 | 信用能力咨询 | `/ci/app/requestContractAdvisory` | POST | `RequestContractAdvisoryReqDTO` | `RequestContractAdvisoryRespDTO` |
| IF8A-22 | 签约结果查询 | `/ci/app/requestContractResult` | POST | `RequestContractResultReqDTO` | `RequestContractResultRespDTO` |
| IF8A-06 | 请求解约 | `/ci/app/requestTermination` | POST | `RequestTerminationReqDTO` | `RequestTerminationRespDTO` |
| 支付 API 1.1 | 请求支付 | `/ci/app/requestPay` | POST | `RequestPayReqDTO` | `RequestPayResult` |
| 支付 API 3.1 | 请求退款 | `/ci/app/requestRefund` | POST | `RequestRefundReqDTO` | `RequestRefundResult` |
| 支付 API 5.1 | 支付结果回调 | `/ci/app/receivePayResult` | POST | `ReceivePayResultReqDTO` | `PaySignCallbackResult` |
| IPD02 | 签约结果回调 | `/ci/app/receiveSignResult` | POST | `ReceiveSignResultReqDTO` | `PaySignCallbackResult` |
| IPD03 | 解约结果回调 | `/ci/app/receiveTerminationResult` | POST | `ReceiveTerminationResultReqDTO` | `BaseRespDTO` |

### 2.2 支付宝出行接口

| 接口名称 | 请求路径 | 请求方式 | 请求 DTO | 响应 DTO |
|---------|---------|---------|---------|---------|
| 信用能力咨询 | `/channel/requestContractAdvisory` | POST | `RequestContractAdvisoryReqDTO` | `RequestContractAdvisoryRespDTO` |
| 签约结果查询 | `/channel/requestContractResult` | POST | `RequestContractResultReqDTO` | `RequestContractResultRespDTO` |
| 请求解约 | `/channel/requestTermination` | POST | `RequestTerminationReqDTO` | `RequestTerminationRespDTO` |
| 添加签约信息 | `/channel/addContract` | POST | `AlipayTripAddContractReqDTO` | `RequestSignInfoResult` |

### 2.3 通知回调接口（预留）

| 接口名称 | 请求路径 | 请求方式 | 请求 DTO | 响应 DTO |
|---------|---------|---------|---------|---------|
| 签约结果通知 | `/notify/receiveSignResult` | POST | `ReceiveSignResultReqDTO` | `Object` |
| 解约结果通知 | `/notify/receiveTerminationResult` | POST | `ReceiveTerminationResultReqDTO` | `Object` |

### 2.4 解约结果通知（ITP 侧）

| 接口名称 | 请求路径 | 请求方式 | 请求 DTO | 响应 DTO |
|---------|---------|---------|---------|---------|
| 解约结果通知 | `/ticket/receiveTerminationResultFromItp` | POST | `ReceiveTerminationResultReqDTO` | `BaseRespDTO` |

---

## 三、核心业务流程

### 3.1 IF8A-16 请求签约信息流程

**调用链路**:
```
APP
  → fep-app-server (/ci/app/requestSignInfo)
    → AccountClient RPC（可选，查询用户信息）
      → pay-sign-server (/ci/app/requestSignInfo)
        → PaySignServiceImpl.requestSignInfo()
          → 1. 参数校验
          → 2. 校验是否已签约（查询 APP_PAY_SIGN_INFO）
          → 3. 调用支付平台 contract 接口获取 SDK 参数
          → 4. 返回 SDK 参数
```

**业务逻辑**:

1. **参数校验** - 校验 `thirdUserId`、`displayAccount`、`payChannelCode`、`requestSignSeq` 是否为空
2. **重复签约检查** - 根据 `thirdUserId` 和 `paymentVendor` 查询 `APP_PAY_SIGN_INFO`，如果已签约直接返回错误
3. **调用支付平台** - 组装签约参数，调用支付平台 `/api/v1/contract/contract` 接口，获取 SDK 启动参数
4. **返回结果** - 返回 `requestStartSdkInfo`，包含支付宝/微信 SDK 参数，不写入本地签约表

**关键代码**:
```java
// PaySignServiceImpl.java:145-185
@Override
@Transactional(rollbackFor = Exception.class)
public RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request, String signChannel) {
    // 1. 参数校验
    String validMsg = validateRequestSignInfo(request);
    if (validMsg != null) {
        fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
        return response;
    }
    
    // 2. 校验是否已签约
    PaySignInfo existingSign = paySignInfoMapper.selectByUserAndVendor(request.getThirdUserId(), paymentVendor);
    if (existingSign != null) {
        fillError(response, PaySignErrorCodeEnum.ALREADY_SIGNED, "该用户已签约此支付渠道");
        return response;
    }
    
    // 3. 调用支付平台获取SDK参数
    String sdkInfo = buildRequestStartSdkInfo(request, paymentVendor);
    if (!StringUtils.hasText(sdkInfo)) {
        fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "请求支付签约接口失败");
        return response;
    }
    
    // 4. 返回成功响应
    fillSuccess(response);
    response.setRequestStartSdkInfo(sdkInfo);
    return response;
}
```

### 3.2 支付宝出行-添加签约信息流程

**调用链路**:
```
支付宝APP
  → fep-alipay-server (/channel/addContract)
    → pay-sign-server (/channel/addContract)
      → PaySignServiceImpl.alipayTripRequestSignInfo()
        → 1. 参数校验
        → 2. 校验是否已签约
        → 3. 同步确认：直接写入签约主表
        → 4. 写入流水表
        → 5. 返回成功
```

**业务逻辑**:

1. **参数校验** - 校验 `thirdUserId`、`channel`、`agreementCode`、`channelUserAccount` 是否为空
2. **重复签约检查** - 根据 `thirdUserId` 和 `paymentVendor` 查询 `APP_PAY_SIGN_INFO`
3. **同步确认签约** - 直接写入 `APP_PAY_SIGN_INFO` 表，状态为 `SIGNED`，不调用支付平台
4. **写入流水表** - 记录 `ALIPAY_TRIP_REQUEST_SIGN_INFO` 操作流水
5. **返回结果** - 返回成功，不返回 SDK 参数

**关键代码**:
```java
// PaySignServiceImpl.java:194-253
@Override
@Transactional(rollbackFor = Exception.class)
public RequestSignInfoResult alipayTripRequestSignInfo(AlipayTripAddContractReqDTO request) {
    // 1. 参数校验
    // 2. 校验是否已签约
    // 3. 同步确认：直接写入签约主表，不调用支付平台
    PaySignInfo signInfo = new PaySignInfo();
    signInfo.setRequestSignSeq(request.getAgreementCode());
    signInfo.setThirdUserId(request.getThirdUserId());
    signInfo.setPaymentVendor(paymentVendor);
    signInfo.setSignChannel(SignChannelEnum.ALIPAY.getCode());
    signInfo.setDisplayAccount(request.getChannelUserAccount());
    signInfo.setContractStatus(STATUS_SIGNED);
    signInfo.setSignTime(LocalDateTime.now());
    paySignInfoMapper.insert(signInfo);
    
    // 4. 写入流水表
    PaySignRequest logRecord = new PaySignRequest();
    logRecord.setRequestSignSeq(request.getAgreementCode());
    logRecord.setThirdUserId(request.getThirdUserId());
    logRecord.setPaymentVendor(paymentVendor);
    logRecord.setSignChannel(SignChannelEnum.ALIPAY.getCode());
    logRecord.setOperationType("ALIPAY_TRIP_REQUEST_SIGN_INFO");
    logRecord.setSignStatus(STATUS_SIGNED);
    paySignRequestMapper.insert(logRecord);
    
    // 5. 返回成功响应
    fillSuccess(response);
    response.setRequestStartSdkInfo(null);
    return response;
}
```

### 3.3 IF8A-21 信用能力咨询流程

**调用链路**:
```
APP
  → pay-sign-server (/ci/app/requestContractAdvisory)
    → PaySignServiceImpl.requestContractAdvisory()
      → 1. 参数校验
      → 2. 调用支付平台 creditQuery 接口
      → 3. 返回结果
```

**业务逻辑**:

1. **参数校验** - 校验 `thirdUserId`、`requestSignSeq`、`paymentVendor` 是否为空
2. **调用支付平台** - 调用 `/api/contract/creditQuery` 查询用户信用能力
3. **返回结果** - 返回成功或失败

### 3.4 IF8A-22 签约结果查询流程

**调用链路**:
```
APP
  → pay-sign-server (/ci/app/requestContractResult)
    → PaySignServiceImpl.requestContractResult()
      → 1. 参数校验
      → 2. 查询本地签约记录
      → 3. 调用支付平台 queryResult 接口
      → 4. 回填本地签约状态
      → 5. 返回结果
```

**业务逻辑**:

1. **参数校验** - 校验 `thirdUserId`、`requestSignSeq`、`paymentVendor` 是否为空
2. **查询本地记录** - 根据 `thirdUserId` 和 `paymentVendor` 查询 `APP_PAY_SIGN_INFO`
3. **调用支付平台** - 调用 `/api/contract/queryResult` 查询最新签约状态
4. **回填本地状态** - 以支付平台返回结果为准，刷新本地签约状态和协议号，如果状态为 `SIGNED` 则插入签约记录
5. **返回结果** - 返回签约状态、payUserId、payAccountId、payAgreementNo

### 3.5 IF8A-06 请求解约流程

**调用链路**:
```
APP
  → pay-sign-server (/ci/app/requestTermination)
    → PaySignServiceImpl.requestTermination()
      → 1. 参数校验
      → 2. 查询是否已签约
      → 3. 调用支付平台 dismissal 接口
      → 4. 记录流水
      → 5. 返回结果
```

**业务逻辑**:

1. **参数校验** - 校验 `thirdUserId`、`requestSignSeq` 是否为空
2. **查询签约记录** - 根据 `thirdUserId` 和 `paymentVendor` 查询 `APP_PAY_SIGN_INFO`，如果未签约返回错误
3. **调用支付平台** - 调用 `/api/contract/dismissal` 发起解约请求
4. **记录流水** - 只记录流水，不修改签约主表状态
5. **返回结果** - 返回成功，最终解约状态以后续回调为准

### 3.6 支付 API 1.1 请求支付流程

**调用链路**:
```
内部服务
  → pay-sign-server (/ci/app/requestPay)
    → PaySignServiceImpl.requestPay()
      → 1. 参数校验
      → 2. 查询 account-server 获取用户默认支付通道
      → 3. 创建/更新支付订单（幂等）
      → 4. 调用支付平台 requestPay 接口
      → 5. 更新支付订单状态
      → 6. 返回结果
```

**业务逻辑**:

1. **参数校验** - 校验 `orderNo`、`scene`、`thirdUserId`、`amount`、`industryType`、`subject`、`body`、`cardId`、`cardType` 是否为空
2. **查询用户支付信息** - 调用 `accountClient.queryUserInfo()` 获取用户默认支付通道和签约流水号
3. **创建支付订单** - 幂等创建 `PAY_TXN_DETAIL` 记录，状态为 `INIT`
4. **调用支付平台** - 调用 `/api/payment/requestPay` 发起支付，组装支付网关公共参数和签名
5. **更新订单状态** - 同步请求后更新为 `PROCESSING`，支付回调后更新为最终状态
6. **返回结果** - 返回 `orderNo`、`merchantOrderNo`、`channelOrderNo`、`payData`

**关键代码**:
```java
// PaySignServiceImpl.java:419-475
@Override
@Transactional(rollbackFor = Exception.class)
public RequestPayResult requestPay(RequestPayReqDTO request) {
    // 1. 参数校验
    String validMsg = validateRequestPay(request);
    if (validMsg != null) {
        fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
        return response;
    }
    
    // 2. 查询 account-server 获取用户默认支付通道
    QueryUserInfoResult accountPayInfo = resolveAccountPayInfo(request, response);
    if (accountPayInfo == null) {
        return response;
    }
    
    // 3. 创建支付订单（幂等）
    ensurePayTxn(request, accountPayInfo);
    payTxnDetailMapper.markRequesting(request.getOrderNo());
    
    // 4. 调用支付平台
    Map<String, Object> bizData = buildRequestPayBizData(request);
    PaySignGatewayResponse gatewayResponse = requestGatewaySimple(paySignProperties.getRequestPayPath(), bizData);
    
    // 5. 更新订单状态
    fillSuccess(response);
    fillGatewayFields(response, gatewayResponse);
    updatePayRequestResult(request.getOrderNo(), "PROCESSING", response);
    return response;
}
```

### 3.7 支付 API 3.1 请求退款流程

**调用链路**:
```
内部服务
  → pay-sign-server (/ci/app/requestRefund)
    → PaySignServiceImpl.requestRefund()
      → 1. 参数校验
      → 2. 查询原支付订单
      → 3. 校验退款金额
      → 4. 创建退款明细
      → 5. 调用支付平台 requestRefund 接口
      → 6. 更新退款状态和原支付订单退款汇总
      → 7. 返回结果
```

**业务逻辑**:

1. **参数校验** - 校验 `orderNo`、`refundAmount` 是否为空，`refundAmount` 必须大于 0
2. **查询原支付订单** - 根据 `orderNo` 查询 `PAY_TXN_DETAIL`
3. **校验退款条件** - 原订单必须支付成功，退款金额不能超出可退金额
4. **创建退款明细** - 生成 `refundOrderNo`，插入 `PAY_REFUND_DETAIL`，状态为 `INIT`
5. **调用支付平台** - 调用 `/api/refund/requestRefund` 发起退款，使用原支付商户订单号
6. **更新状态** - 更新退款明细状态，回写原支付订单退款汇总（`REFUND_AMOUNT`、`REFUND_STATUS`）
7. **返回结果** - 返回 `orderNo`、`refundOrderNo`、`merchantRefundNo`、`refundNo`、`channelRefundNo`

### 3.8 支付 API 5.1 支付结果回调流程

**调用链路**:
```
支付平台
  → fep-app-server (/receivePayResult)
    → pay-sign-server (/ci/app/receivePayResult)
      → PaySignServiceImpl.receivePayResult()
        → 1. 参数校验
        → 2. 保存回调流水到 PAY_CALLBACK_LOG
        → 3. 更新 PAY_TXN_DETAIL 支付状态和金额
        → 4. 返回成功
```

**业务逻辑**:

1. **参数校验** - 校验 `orderNo` 是否为空
2. **保存回调流水** - 插入 `PAY_CALLBACK_LOG` 记录，保存原始回调报文
3. **更新支付订单** - 回写 `PAY_TXN_DETAIL` 的支付状态、商户订单号、渠道订单号、金额、支付用户ID、支付时间
4. **返回结果** - 返回成功

### 3.9 IPD02 签约结果回调流程

**调用链路**:
```
支付平台
  → fep-app-server (/receiveSignResult)
    → pay-sign-server (/ci/app/receiveSignResult)
      → PaySignServiceImpl.receiveSignResult()
        → 1. 参数校验
        → 2. 补充 thirdUserId/displayAccount（从流水表）
        → 3. 如果签约成功：
          → 3.1 插入 APP_PAY_SIGN_INFO
          → 3.2 写入流水表
          → 3.3 异步通知地铁 APP
        → 4. 如果签约失败：记录流水
        → 5. 返回成功
```

**业务逻辑**:

1. **参数校验** - 校验 `requestSignSeq`、`paymentVendor`、`status` 是否为空
2. **补充缺失字段** - 从流水表补充 `thirdUserId`、`displayAccount`
3. **签约成功处理**:
   - 插入 `APP_PAY_SIGN_INFO` 签约主表
   - 写入 `APP_PAY_SIGN_REQUEST` 流水表，`notifyStatus=PENDING`
   - 异步调用 `AppNotifyService.asyncNotifySignResult()` 通知地铁 APP
4. **签约失败处理** - 只记录流水，不通知 APP
5. **返回结果** - 返回成功

### 3.10 IPD03 解约结果回调流程

**调用链路**:
```
支付平台
  → fep-app-server (/receiveTerminationResult)
    → pay-sign-server (/ci/app/receiveTerminationResult)
      → PaySignServiceImpl.receiveTerminationResult()
        → 1. 参数校验
        → 2. 补充 thirdUserId/cardId/cardType
        → 3. 如果解约成功：
          → 3.1 查询签约记录
          → 3.2 删除 APP_PAY_SIGN_INFO
          → 3.3 清理 account-server 支付通道
          → 3.4 写入流水表
          → 3.5 异步通知地铁 APP
        → 4. 如果解约失败：记录流水
        → 5. 返回成功
```

**业务逻辑**:

1. **参数校验** - 校验 `requestSignSeq`、`paymentVendor`、`status` 是否为空
2. **补充缺失字段** - 从流水表补充 `thirdUserId`，从签约主表补充 `cardId`、`cardType`
3. **解约成功处理**:
   - 查询签约记录（删除前先查询，用于通知）
   - 删除 `APP_PAY_SIGN_INFO` 签约记录
   - 调用 `accountClient.requestRemovePayChannel()` 清理 account-server 支付通道
   - 写入 `APP_PAY_SIGN_REQUEST` 流水表，`signStatus=UNSIGNED`，`notifyStatus=PENDING`
   - 异步调用 `AppNotifyService.asyncNotifyTerminationResult()` 通知地铁 APP
4. **解约失败处理** - 只记录流水，不通知 APP
5. **返回结果** - 返回成功

### 3.11 支付网关调用流程

**统一网关调用**:
```java
// PaySignServiceImpl.requestGatewaySimple()
private PaySignGatewayResponse requestGatewaySimple(String path, Map<String, Object> bizData) {
    // 1. 组装公共参数
    PaySignGatewayRequest request = new PaySignGatewayRequest();
    request.setMerchantNo(merchantNo);
    request.setApiVersion(apiVersion);
    request.setSignType(signType);
    request.setCharset(charset);
    request.setBizData(Base64.encode(bizDataJson));
    
    // 2. 签名
    signRequest(request, bizDataJson);
    
    // 3. 调用支付网关
    String url = gatewayUrl + path;
    Response response = httpClient.newCall(httpRequest).execute();
    
    // 4. 解析响应
    return JSON.parseObject(responseBody, PaySignGatewayResponse.class);
}
```

**签名机制**:
- 除 `sign` 外，其余字段按字母顺序排序拼接
- 使用商户私钥进行 RSA2 (SHA256WithRSA) 签名
- 签名结果 Base64 编码

**注意**: 当前代码中存在硬编码的 RSA 私钥（`signRequest` 方法），生产环境应通过 `pay.sign.merchant-private-key` 配置注入。

### 3.12 APP 异步通知流程

**通知机制**:
1. 签约/解约成功后，将通知任务提交到 `notifyExecutor` 线程池
2. 组装 ITP 通用请求格式，包含 `providerId`、`charset`、`format`、`timestamp`、`deviceId`、`signType`、`sign`、`bizData`
3. 使用 Multipart 表单格式 POST 到配置的通知 URL
4. 根据响应判断通知是否成功，更新 `notifyStatus` 和 `notifyRetryCount`

**补偿机制**:
- 每 5 分钟执行一次 `compensateNotify()` 定时任务
- 查询 `notifyStatus=FAILED` 且 `notifyRetryCount < 3` 的记录
- 重新提交通知任务，最多重试 3 次

---

## 四、数据模型

### 4.1 APP_PAY_SIGN_INFO（签约主表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| REQUEST_SIGN_SEQ | VARCHAR | 签约流水号（主键） |
| THIRD_USER_ID | VARCHAR | 第三方用户 ID |
| SIGN_STATUS | VARCHAR | 签约状态：NOT_SIGNED/SIGNING/SIGNED/UNSIGNED/FAILED |
| SIGN_CHANNEL | VARCHAR | 签约渠道：METRO_APP/ALIPAY/WECHAT/UNION_PAY/LONG_PAY/WALLET |
| PAYMENT_VENDOR | VARCHAR | 支付渠道：03支付宝/04微信/05支付宝出行/06龙支付等 |
| CARD_ID | VARCHAR | 卡 ID |
| CARD_TYPE | VARCHAR | 卡类型 |
| PAY_ACCOUNT_ID | VARCHAR | 支付账号 ID |
| PAY_AGREEMENT_NO | VARCHAR | 支付协议号 |
| DISPLAY_ACCOUNT | VARCHAR | 显示账号 |
| SIGN_TIME | TIMESTAMP | 签约时间 |
| TERMINATION_TIME | TIMESTAMP | 解约时间 |

### 4.2 APP_PAY_SIGN_REQUEST（签约流水表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ID | BIGINT | 主键 |
| REQUEST_SIGN_SEQ | VARCHAR | 签约流水号 |
| THIRD_USER_ID | VARCHAR | 第三方用户 ID |
| PAYMENT_VENDOR | VARCHAR | 支付渠道 |
| DISPLAY_ACCOUNT | VARCHAR | 显示账号 |
| OPERATION_TYPE | VARCHAR | 操作类型：SIGN/UNSIGN |
| REQUEST_BODY | VARCHAR | 请求报文 |
| RESPONSE_BODY | VARCHAR | 响应报文 |
| RESULT_CODE | VARCHAR | 结果码 |
| RESULT_MSG | VARCHAR | 结果消息 |
| SIGN_STATUS | VARCHAR | 签约状态 |
| SIGN_CHANNEL | VARCHAR | 签约渠道 |
| PAY_ACCOUNT_ID | VARCHAR | 支付账号 ID |
| PAY_AGREEMENT_NO | VARCHAR | 支付协议号 |
| CARD_ID | VARCHAR | 卡 ID |
| CARD_TYPE | VARCHAR | 卡类型 |
| TERMINATION_TIME | VARCHAR | 解约时间 |
| CREATE_TMS | TIMESTAMP | 创建时间 |
| NOTIFY_STATUS | VARCHAR | 通知状态：PENDING/SUCCESS/FAILED |
| NOTIFY_RETRY_COUNT | INTEGER | 通知重试次数 |
| NOTIFY_TIME | TIMESTAMP | 最后通知时间 |
| NOTIFY_RESULT | VARCHAR | 通知结果描述 |

### 4.3 PAY_TXN_DETAIL（支付订单明细表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ID | NUMBER | 主键 |
| ORDER_NO | VARCHAR | 订单号 |
| PAY_TYPE | VARCHAR | 支付类型：PAY |
| PAY_STATUS | VARCHAR | 支付状态：INIT/PROCESSING/SUCCESS/FAIL |
| THIRD_USER_ID | VARCHAR | 第三方用户 ID |
| CARD_ID | VARCHAR | 卡 ID |
| CARD_TYPE | VARCHAR | 卡类型 |
| PAYMENT_VENDOR | VARCHAR | 支付渠道 |
| PAY_CHANNEL_CODE | VARCHAR | 支付渠道编码 |
| REQUEST_SIGN_SEQ | VARCHAR | 签约流水号 |
| AMOUNT | NUMERIC | 订单金额（分） |
| TOTAL_AMOUNT | NUMERIC | 总金额（分） |
| CASH_AMOUNT | NUMERIC | 现金金额（分） |
| COUPON_AMOUNT | NUMERIC | 优惠券金额（分） |
| REFUND_STATUS | VARCHAR | 退款状态：NONE/PARTIAL/SUCCESS |
| REFUND_AMOUNT | NUMERIC | 已退款金额（分） |
| LAST_REFUND_TIME | TIMESTAMP | 最后退款时间 |
| MERCHANT_ORDER_NO | VARCHAR | 商户订单号 |
| CHANNEL_ORDER_NO | VARCHAR | 渠道订单号 |
| PAY_USER_ID | VARCHAR | 支付用户 ID |
| REQUEST_COUNT | NUMERIC | 请求次数 |
| NEXT_REQUEST_TIME | TIMESTAMP | 下次请求时间 |
| LAST_REQUEST_TIME | TIMESTAMP | 最后请求时间 |
| FIRST_REQUEST_TIME | TIMESTAMP | 首次请求时间 |
| RESPONSE_TIME | TIMESTAMP | 响应时间 |
| PAY_TIME | VARCHAR | 支付时间 |
| TXN_DATE | VARCHAR | 交易日期（yyyyMMdd） |
| CREATE_TIME | TIMESTAMP | 创建时间 |
| UPDATE_TIME | TIMESTAMP | 更新时间 |

### 4.4 PAY_REFUND_DETAIL（退款明细表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ID | NUMBER | 主键 |
| REFUND_ORDER_NO | VARCHAR | 退款订单号 |
| ORDER_NO | VARCHAR | 原支付订单号 |
| REFUND_STATUS | VARCHAR | 退款状态：INIT/PROCESSING/SUCCESS/FAIL |
| REFUND_AMOUNT | NUMERIC | 退款金额（分） |
| REFUND_REASON | VARCHAR | 退款原因 |
| MERCHANT_REFUND_NO | VARCHAR | 商户退款单号 |
| REFUND_NO | VARCHAR | 退款单号 |
| CHANNEL_REFUND_NO | VARCHAR | 渠道退款单号 |
| REQUEST_COUNT | NUMERIC | 请求次数 |
| NEXT_REQUEST_TIME | TIMESTAMP | 下次请求时间 |
| LAST_REQUEST_TIME | TIMESTAMP | 最后请求时间 |
| REFUND_TIME | VARCHAR | 退款时间 |
| TXN_DATE | VARCHAR | 交易日期（yyyyMMdd） |
| RET_CODE | VARCHAR | 返回码 |
| RET_MSG | VARCHAR | 返回消息 |
| PAY_CENTER_CODE | VARCHAR | 支付中心码 |
| PAY_CENTER_MSG | VARCHAR | 支付中心消息 |
| REQUEST_BODY | CLOB | 请求报文 |
| RESPONSE_BODY | CLOB | 响应报文 |
| CREATE_TIME | TIMESTAMP | 创建时间 |
| UPDATE_TIME | TIMESTAMP | 更新时间 |

### 4.5 PAY_CALLBACK_LOG（支付回调流水表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ID | NUMBER | 主键 |
| ORDER_NO | VARCHAR | 订单号 |
| CALLBACK_TYPE | VARCHAR | 回调类型：PAY/REFUND |
| CALLBACK_STATUS | VARCHAR | 回调状态 |
| REFUND_ORDER_NO | VARCHAR | 退款订单号 |
| MERCHANT_ORDER_NO | VARCHAR | 商户订单号 |
| CHANNEL_ORDER_NO | VARCHAR | 渠道订单号 |
| MERCHANT_REFUND_NO | VARCHAR | 商户退款单号 |
| REFUND_NO | VARCHAR | 退款单号 |
| CHANNEL_REFUND_NO | VARCHAR | 渠道退款单号 |
| PAY_TIME | VARCHAR | 支付时间 |
| REFUND_TIME | VARCHAR | 退款时间 |
| TOTAL_AMOUNT | NUMERIC | 总金额 |
| CASH_AMOUNT | NUMERIC | 现金金额 |
| COUPON_AMOUNT | NUMERIC | 优惠券金额 |
| REFUND_AMOUNT | NUMERIC | 退款金额 |
| PAY_USER_ID | VARCHAR | 支付用户 ID |
| PAYMENT_VENDOR | VARCHAR | 支付渠道 |
| TXN_DATE | VARCHAR | 交易日期 |
| RAW_BODY | CLOB | 原始回调报文 |
| HANDLE_STATUS | VARCHAR | 处理状态 |
| HANDLE_MSG | VARCHAR | 处理消息 |
| CREATE_TIME | TIMESTAMP | 创建时间 |

---

## 五、枚举与常量

### 5.1 SignChannelEnum（签约渠道枚举）

| 编码 | 名称 | 说明 |
|------|------|------|
| METRO_APP | 地铁APP | 地铁 APP 签约渠道 |
| ALIPAY | 支付宝 | 支付宝出行渠道 |
| WECHAT | 微信 | 微信支付渠道 |
| UNION_PAY | 云闪付 | 云闪付渠道 |
| LONG_PAY | 龙支付 | 龙支付渠道 |
| WALLET | 钱包 | 钱包渠道 |

### 5.2 PaymentVendorEnum（支付渠道枚举）

| 编码 | 名称 | 说明 |
|------|------|------|
| 03 | 支付宝 | 支付宝支付 |
| 04 | 微信 | 微信支付 |
| 05 | 支付宝出行 | 支付宝出行支付 |
| 06 | 龙支付 | 龙支付 |
| 0601 | 招商银行 | 招商银行 |
| 0602 | 中国银行 | 中国银行 |
| 08 | 建行数币 | 建行数字人民币 |
| 0801 | 中行数币 | 中行数字人民币 |
| 0802 | 邮储数币 | 邮储数字人民币 |
| 0803 | 交行数币 | 交行数字人民币 |
| 0B | 钱包 | 钱包 |
| 0C | 数币APP | 数字人民币 APP |

### 5.3 PaySignErrorCodeEnum（错误码枚举）

| 错误码 | 含义 | 说明 |
|--------|------|------|
| 0000 | SUCCESS | 成功 |
| 9999 | FAIL | 失败 |
| 9001 | SYSTEM_ERROR | 系统内部错误 |
| 8001 | INVALID_PARAM | 无效的参数 |
| 8007 | SERVICE_PROVIDER_UNAVAILABLE | 支付系统不可用 |
| 8011 | USER_NOT_SIGNED | 用户未签约 |
| 8012 | RECORD_NOT_EXIST | 签约记录不存在 |
| 8013 | ALREADY_SIGNED | 该用户已签约此支付渠道 |
| 8014 | INVALID_SIGN_DATA | 无效的签约数据 |
| 8180 | ORDER_CANNOT_REFUND | 该订单不能退款 |
| 8181 | ORDER_ALREADY_PAID | 订单已支付成功，请勿重复支付 |
| 8182 | ORDER_CLOSED | 订单已关闭，请重新创建订单 |
| 8183 | ORDER_CANNOT_ACTIVATE | 订单无法激活 |
| 8184 | ORDER_ALREADY_ACTIVATED | 订单已激活 |

---

## 六、核心配置说明

### 6.1 支付网关配置

```properties
# 支付网关基础配置
pay.sign.merchant-no=JOPJ490HLK9Z
pay.sign.api-version=1.0
pay.sign.sign-type=RSA2
pay.sign.charset=UTF-8
pay.sign.gateway-url=http://dtcustomer.bestonepay.com/ngpayment-gateway
pay.sign.merchant-private-key=${PAY_SIGN_MERCHANT_PRIVATE_KEY:}

# 支付平台接口路径
pay.sign.contract-config-path=/api/v1/contract/requestContractConfig
pay.sign.contract-path=/api/v1/contract/contract
pay.sign.contract-advisory-path=/api/contract/creditQuery
pay.sign.contract-result-path=/api/contract/queryResult
pay.sign.termination-path=/api/contract/dismissal
pay.sign.request-pay-path=/api/payment/requestPay
pay.sign.request-refund-path=/api/refund/requestRefund
```

### 6.2 通知 URL 配置

```properties
# APP 签约结果通知
app.notify.sign-result-url=https://dtcustomer.bestonepay.com/testngbackV2/ci/app/v2/receiveSignResult

# APP 解约结果通知
app.notify.termination-result-url=https://dtcustomer.bestonepay.com/testngbackV2/ci/app/v2/ticket/receiveTerminationResultFromItp

# 默认签约通知地址
pay.sign.default-notify-url=http://58.56.166.170:48000/ci/app/receiveSignResult

# 支付结果通知地址
pay.sign.request-pay-notify-url=http://58.56.166.170:48000/ci/app/receivePayResult
```

### 6.3 支付宝/微信配置

```properties
# 支付宝配置
pay.sign.alipay.app-id=60000157
pay.sign.alipay.merchant-app-id=2015101000413186

# 微信配置
pay.sign.wechat.app-id=wx426a3015555a46be
pay.sign.wechat.entrust-url=https://api.mch.weixin.qq.com/papay/entrustweb
```

### 6.4 ITP 通知配置

```properties
itp.providerId=06
itp.charset=UTF-8
itp.format=json
itp.deviceId=ITP-PAY-SIGN
itp.signType=00
itp.signKey=
```

### 6.5 异步线程池配置

```properties
app.notify.executor.core-pool-size=4
app.notify.executor.max-pool-size=16
app.notify.executor.queue-capacity=1000
app.notify.executor.thread-name-prefix=app-notify-
```

---

## 七、与其他服务的交互

### 7.1 服务调用关系

| 被调用服务 | 调用时机 | 调用方法 | 说明 |
|-----------|---------|---------|------|
| account-server | 请求支付时 | `accountClient.queryUserInfo()` | 查询用户默认支付通道和签约流水号 |
| account-server | 解约成功后 | `accountClient.requestRemovePayChannel()` | 清理用户支付通道 |
| 支付网关 | 签约/解约/支付/退款 | OkHttp POST | 调用支付平台接口 |
| 地铁 APP | 签约/解约成功 | OkHttp POST | 异步通知签约/解约结果 |

### 7.2 RPC 客户端

| Client | 说明 |
|--------|------|
| `com.chinasofti.huateng.rpc.account.AccountClient` | 账户服务 RPC 客户端，用于查询用户支付信息和清理支付通道 |

---

## 八、待办事项

### 8.1 已知问题

1. **硬编码私钥** - `PaySignServiceImpl.signRequest()` 方法中硬编码了 RSA 私钥，生产环境应通过配置注入
2. **支付宝出行同步确认** - 当前支付宝出行渠道采用同步确认模式，不调用支付平台，直接写入本地签约表
3. **通知重试限制** - 当前补偿通知最多重试 3 次，超过后不再自动重试

### 8.2 后续优化

1. 移除硬编码私钥，统一使用配置注入
2. 完善支付回调的幂等性校验
3. 增加支付订单超时自动关闭机制
4. 完善退款回调处理
5. 增加支付渠道故障自动切换

---

## 九、相关文档

- `docs/03-接口文档/接口设计对比/接口规范与项目实现对照表.md` - 接口规范与实现对照
- `docs/01-项目概述/支付宝乘车业务流.md` - 支付宝乘车业务流
- `docs/05-数据库/tables.md` - 数据库表结构说明

---

## 十、源码文件索引

### 10.1 Java 源文件（共 42 个）

**控制器层 (6 个)**:
1. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/controller/ci/app/PaySignController.java` - 地铁 APP 签约信息接口
2. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/controller/ci/app/PaySignAppController.java` - 地铁 APP 签约/解约/支付/退款/回调接口
3. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/controller/channel/PaySignAlipayTripController.java` - 支付宝出行渠道接口
4. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/controller/notify/PaySignAlipayTripNotifyController.java` - 支付宝出行通知回调（预留）
5. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/controller/app/PaySignNotifyController.java` - 地铁 APP 签约结果通知
6. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/controller/ticket/TerminationNotifyController.java` - 解约结果通知（ITP 侧）

**服务层 (4 个)**:
7. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/PaySignService.java` - 支付签约服务接口
8. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/PaySignServiceImpl.java` - 支付签约服务实现，核心业务逻辑
9. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/AppNotifyService.java` - APP 异步通知服务接口
10. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/AppNotifyServiceImpl.java` - APP 异步通知服务实现

**数据访问层 (6 个)**:
11. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PaySignInfoMapper.java` - 签约主表 Mapper
12. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PaySignRequestMapper.java` - 签约流水表 Mapper
13. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PayTxnDetailMapper.java` - 支付订单明细 Mapper
14. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PayRefundDetailMapper.java` - 退款明细 Mapper
15. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PayCallbackLogMapper.java` - 回调流水 Mapper
16. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PaySignLogMapper.java` - 签约日志 Mapper（废弃）

**实体类 (5 个)**:
17. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/entity/PaySignInfo.java` - 签约主表实体
18. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/entity/PaySignRequest.java` - 签约流水表实体
19. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/entity/PayTxnDetail.java` - 支付订单明细实体
20. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/entity/PayRefundDetail.java` - 退款明细实体
21. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/entity/PayCallbackLog.java` - 回调流水实体

**模型类 (9 个)**:
22. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/model/request/PaySignGatewayRequest.java` - 支付网关请求模型
23. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/model/response/PaySignGatewayResponse.java` - 支付网关响应模型
24. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/model/response/BaseRespDTO.java` - 基础响应 DTO
25. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/model/response/RequestSignInfoRespDTO.java` - 签约信息响应 DTO
26. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/model/response/RequestContractAdvisoryRespDTO.java` - 信用能力咨询响应 DTO
27. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/model/response/RequestContractResultRespDTO.java` - 签约结果查询响应 DTO
28. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/model/response/RequestTerminationRespDTO.java` - 解约响应 DTO
29. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/model/request/RequestSignInfoReqDTO.java` - 签约信息请求 DTO
30. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/model/request/RequestContractAdvisoryReqDTO.java` - 信用能力咨询请求 DTO

**模型类（续）(6 个)**:
31. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/model/request/RequestContractResultReqDTO.java` - 签约结果查询请求 DTO
32. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/model/request/RequestTerminationReqDTO.java` - 解约请求 DTO
33. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/model/request/ReceiveTerminationResultReqDTO.java` - 解约结果回调请求 DTO
34. `model/src/main/java/com/chinasofti/huateng/model/app/RequestPayReqDTO.java` - 支付请求 DTO（model 模块）
35. `model/src/main/java/com/chinasofti/huateng/model/app/RequestRefundReqDTO.java` - 退款请求 DTO（model 模块）
36. `model/src/main/java/com/chinasofti/huateng/model/app/ReceiveSignResultReqDTO.java` - 签约结果回调请求 DTO（model 模块）

**模型类（续）(6 个)**:
37. `model/src/main/java/com/chinasofti/huateng/model/app/ReceivePayResultReqDTO.java` - 支付结果回调请求 DTO（model 模块）
38. `model/src/main/java/com/chinasofti/huateng/model/app/RequestSignInfoResult.java` - 签约信息响应（model 模块）
39. `model/src/main/java/com/chinasofti/huateng/model/app/PaySignCallbackResult.java` - 回调响应结果（model 模块）
40. `model/src/main/java/com/chinasofti/huateng/model/app/RequestPayResult.java` - 支付响应结果（model 模块）
41. `model/src/main/java/com/chinasofti/huateng/model/app/RequestRefundResult.java` - 退款响应结果（model 模块）
42. `model/src/main/java/com/chinasofti/huateng/model/app/AppSignResultNotifyReqDTO.java` - APP 签约结果通知 DTO（model 模块）

**模型类（续）(3 个)**:
43. `model/src/main/java/com/chinasofti/huateng/model/app/AppTerminationResultNotifyReqDTO.java` - APP 解约结果通知 DTO（model 模块）
44. `model/src/main/java/com/chinasofti/huateng/model/alipaytrip/AlipayTripAddContractReqDTO.java` - 支付宝出行添加签约信息 DTO（model 模块）
45. `pay-sign-server/src/main/java/com/chinasofti/huateng/PaySignServer.java` - 启动类

**常量类 (3 个)**:
46. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/constant/SignChannelEnum.java` - 签约渠道枚举
47. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/constant/PaymentVendorEnum.java` - 支付渠道枚举
48. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/constant/PaySignErrorCodeEnum.java` - 错误码枚举

**工具类 (1 个)**:
49. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/util/RSASignUtils.java` - RSA 签名工具类

**配置类 (3 个)**:
50. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/config/PaySignProperties.java` - 支付签约配置属性
51. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/config/PaySignExecutorConfig.java` - 异步线程池配置
52. `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/config/VisibleThreadPoolTaskExecutor.java` - 可见线程池执行器

### 10.2 资源文件（共 8 个）

1. `pay-sign-server/src/main/resources/application.properties` - 应用配置
2. `pay-sign-server/src/main/resources/mapper/PaySignInfoMapper.xml` - 签约主表 SQL
3. `pay-sign-server/src/main/resources/mapper/PaySignRequestMapper.xml` - 签约流水表 SQL
4. `pay-sign-server/src/main/resources/mapper/PayTxnDetailMapper.xml` - 支付订单明细 SQL
5. `pay-sign-server/src/main/resources/mapper/PayRefundDetailMapper.xml` - 退款明细 SQL
6. `pay-sign-server/src/main/resources/mapper/PayCallbackLogMapper.xml` - 回调流水 SQL
7. `pay-sign-server/src/main/resources/mapper/PaySignLogMapper.xml` - 签约日志 SQL（废弃）
8. `pay-sign-server/pom.xml` - Maven 依赖配置

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/pay-sign-server` 模块源码。
> 文档中涉及的文件路径均为相对项目根目录的路径。
