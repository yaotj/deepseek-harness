# fep-alipay-server 微服务文档

> **模块路径**: `fep-alipay-server/`
> **端口**: 8080
> **职责**: 支付宝出行接入网关，提供支付宝出行渠道的专属接口，统一接收支付宝请求并路由到后端微服务
> **源码阅读范围**: `fep-alipay-server/src/main/java/`、`fep-alipay-server/src/main/resources/`
> **关联文档**: `docs/01-项目概述/支付宝乘车业务流.md`
> **整理时间**: 2026-07-20

---

## 一、模块概述

### 1.1 核心职责

fep-alipay-server 是支付宝出行渠道的接入网关，承担以下核心职责：

1. **支付宝出行开户** - `/channel/requestApplication` 支付宝出行开卡申请
2. **支付宝出行签约** - `/channel/addContract` 添加签约信息
3. **支付宝出行解约** - `/channel/terminateContract` 解约登记
4. **支付宝出行行业数据** - `/channel/requestIndustryData` 获取行业数据
5. **支付宝出行行程查询** - `/channel/findTravelList`、`/channel/findTravelDetail`
6. **支付宝出行支付** - `/admin/payment/requestPay` 支付申请
7. **支付宝出行支付查询** - `/admin/payment/payQuery` 支付结果查询
8. **支付宝出行退款** - `/admin/payment/requestRefund` 退款申请
9. **支付宝出行通知** - `/notify/payment/payNotify` 支付回调、`/notify/closeResultForAlipay` 业务关闭结果通知
10. **支付宝出行黑名单** - `/admin/payment/addBlackList` 添加黑名单
11. **支付宝出行执行解约** - `/admin/payment/executeTermination` 执行解约

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| Fastjson2 | JSON 解析 |
| Alipay SDK | 支付宝开放平台 SDK（预留） |

### 1.3 端口配置

```properties
server.port=8080
```

### 1.4 与 fep-app-server 的区别

| 对比项 | fep-app-server | fep-alipay-server |
|--------|----------------|-------------------|
| 端口 | 9101 | 8080 |
| 渠道 | 地铁APP | 支付宝出行 |
| 签约方式 | 支付平台签约 | 同步确认（无需支付平台） |
| 接口前缀 | `/ci/app` | `/channel`、`/admin/payment`、`/notify`、`/memberContract/channel` |

---

## 二、接口清单

### 2.1 请求格式说明

fep-alipay-server 采用两种请求格式：

1. **FormData + bizData**：`@ModelAttribute CommonFormRequest`，业务参数放在 `bizData` 字段（JSON 字符串），适用于 `/channel`、`/admin/payment`、`/memberContract/channel` 路径。
2. **直接 JSON Body**：`@RequestBody`，直接接收 JSON 对象，适用于 `/notify` 路径。

**CommonFormRequest 结构**：

```java
String providerId;    // 提供者ID
String charset;       // 字符集
String format;        // 格式
String timestamp;     // 时间戳
String deviceId;      // 设备ID
String signType;      // 签名类型
String sign;          // 签名
String bizData;       // 业务数据（JSON 字符串）
```

---

### 2.2 Channel 接口（基础路径：`/channel`）

**Controller**：`FepAlipayTripController`

#### 2.2.1 开卡申请

- **路径**：`POST /channel/requestApplication`
- **Service**：`AlipayTripService.requestApplication` → `AlipayApplicationServiceImpl.requestApplication`
- **请求方式**：`@ModelAttribute CommonFormRequest`，业务参数在 `bizData` 中

**请求参数** (`AlipayTripRequestApplicationReqDTO`)：

```java
String thirdUserId;   // 第三方用户ID，格式化后的用户标识
String cardType;      // 卡片类型，如：02
String msisdn;        // 用户手机号码
String extend1;       // 扩展字段1
String extend2;       // 扩展字段2（可选）
String cardIssueCode; // 发卡渠道代码 0007支付宝出行
```

**响应参数** (`AlipayTripRequestApplicationRespDTO` extends CommonResult)：

```java
String cardId;    // 卡片ID/逻辑卡号
String cardType;  // 卡片类型
String status;    // 用户状态，如 ACTIVE
```

**业务逻辑**：

1. 参数校验：`bizData` 必填。
2. 反序列化 `bizData` 为 `AlipayTripRequestApplicationReqDTO`。
3. 调用 `AccountClient.alipayTripRequestApplication()` 转发到 `account-server`。
4. 返回 `account-server` 响应。

---

#### 2.2.2 获取行业数据

- **路径**：`POST /channel/requestIndustryData`
- **Service**：`AlipayTripService.requestIndustryData` → `AlipayApplicationServiceImpl.requestIndustryData`
- **请求方式**：`@ModelAttribute CommonFormRequest`

**请求参数** (`AlipayTripRequestIndustryDataReqDTO`)：

```java
String thirdUserId;  // 第三方用户ID
String cardId;       // 逻辑卡号
String cardType;     // 卡片类型
```

**响应参数** (`AlipayTripRequestIndustryDataRespDTO` extends CommonResult)：

```java
String cardData;     // 卡数据 HexString
String sign;         // 签名
```

**业务逻辑**：

1. 参数校验：`bizData` 必填。
2. 反序列化 `bizData`。
3. 调用 `AlipayAccountService.requestIndustryData()` 处理行业数据请求。
4. 返回结果。

> **注意**：还有另一个入口 `/memberContract/channel/requestIndustryData`（`FepAlipayTripMemberContractController`），功能相同，参数格式相同。

---

#### 2.2.3 添加签约信息

- **路径**：`POST /channel/addContract`
- **Service**：`AlipayTripService.addContract` → `AlipayContractServiceImpl.addContract`

**请求参数** (`AlipayTripAddContractReqDTO`)：

```java
String channel;              // 支付渠道，固定 ALIPAY
String thirdUserId;          // 第三方用户ID
String agreementCode;        // 签约协议号
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

---

#### 2.2.4 解约登记

- **路径**：`POST /channel/terminateContract`
- **Service**：`AlipayTripService.terminateContract` → `AlipayContractServiceImpl.terminateContract`

**请求参数** (`AlipayTripTerminateContractReqDTO`)：

```java
String agreementCode; // 签约协议号
```

**响应参数** (`AlipayTripTerminateContractRespDTO` extends CommonResult)：

```java
String retCode; // 返回码
String retMsg;  // 返回消息
```

---

#### 2.2.5 查询乘车记录列表

- **路径**：`POST /channel/findTravelList`
- **Service**：`AlipayTripService.findTravelList` → `AlipayQueryServiceImpl.findTravelList` → `TicketClient.alipayTripFindTravelList()`

**请求参数** (`AlipayTripFindTravelListReqDTO`)：

```java
String thirdUserId;  // 第三方用户ID
String startDate;    // 开始日期：yyyyMMdd
String endDate;      // 结束日期：yyyyMMdd
String page;         // 页码
String size;         // 每页大小
```

**响应参数** (`AlipayTripFindTravelListRespDTO` extends CommonResult)：

```java
int pageNumber;              // 当前页码
int pageSize;                // 每页大小
int totalPage;               // 总页数
int totalCount;              // 总记录数
List<AlipayTripTravelRecordDTO> ticketTransRecord; // 乘车记录列表
```

**业务逻辑**：

1. 参数校验：`bizData` 必填。
2. 反序列化 `bizData`。
3. 调用 `TicketClient.alipayTripFindTravelList()` 转发到 `ticket-server`。
4. 返回 `ticket-server` 响应。

---

#### 2.2.6 查询乘车记录详情

- **路径**：`POST /channel/findTravelDetail`
- **Service**：`AlipayTripService.findTravelDetail` → `AlipayQueryServiceImpl.findTravelDetail` → `AlipayPaySignClient.findTravelDetail()`

**请求参数** (`AlipayTripFindTravelDetailReqDTO`)：

```java
String thirdUserId;  // 第三方用户ID
String orderNo;      // 订单号/交易流水号
String cardId;       // 逻辑卡号（可选）
```

**响应参数** (`AlipayTripFindTravelDetailRespVO`)：

```java
String retCode;           // 返回码
String retMsg;            // 返回消息
AlipayTripFindTravelDetailRespDTO data; // 详情数据
```

**data 字段** (`AlipayTripFindTravelDetailRespDTO` extends CommonResult)：

```java
String entryStationName;   // 进站站点名称
String entryDate;          // 进站时间
String exitStationName;    // 出站站点名称
String exitDate;           // 出站时间
String payAmount;          // 实付金额（分）
String totalAmount;        // 总金额（分）
String orderExpType;       // 订单扩展类型
String tradeOrderNo;       // 交易订单号
String payTradeOrderNo;    // 支付交易订单号
String payOrderNoDate;     // 支付订单日期（= TRANS_TIME）
String payChannelCode;     // 支付渠道代码
String debitRequestResult; // 扣款请求结果
String discountFee;        // 优惠金额
String discountInfo;       // 优惠信息
String companionFlag;      // 同行票标识
String cardNum;            // 卡号
String ticketCode;         // 日票票号
String countingTimes;      // 计次次数（预留）
String countingFlag;       // 计次标识（预留）
String invoice;            // 发票状态（可选）
```

**业务逻辑**：

1. 参数校验：`bizData` 必填。
2. 反序列化 `bizData`。
3. 调用 `AlipayPaySignClient.findTravelDetail()` 转发到 `alipay-pay-sign-server`。
4. 返回 `alipay-pay-sign-server` 响应（支付流水查询结果，非 ticket-server 闸机交易详情）。

---

### 2.3 MemberContract 接口（基础路径：`/memberContract/channel`）

**Controller**：`FepAlipayTripMemberContractController`

#### 2.3.1 获取行业数据

- **路径**：`POST /memberContract/channel/requestIndustryData`
- **Service**：`AlipayTripService.requestIndustryData` → `AlipayApplicationServiceImpl.requestIndustryData`
- **请求方式**：`@ModelAttribute CommonFormRequest`

参数和响应与 `/channel/requestIndustryData` 相同，为支付宝 MemberContract 场景提供的专用入口。

---

### 2.4 支付接口（基础路径：`/admin/payment`）

**Controller**：`FepAlipayTripPaymentController`

#### 2.4.1 支付结果查询

- **路径**：`POST /admin/payment/payQuery`
- **Service**：`AlipayTripService.payQuery` → `AlipayQueryServiceImpl.payQuery` → `AlipayPaySignClient.payQuery()`

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

---

#### 2.4.2 退款申请

- **路径**：`POST /admin/payment/requestRefund`
- **Service**：`AlipayTripService.requestRefund` → `AlipayPaymentServiceImpl.requestRefund` → `AlipayPaySignClient.requestRefund()`

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

---

#### 2.4.3 添加黑名单

- **路径**：`POST /admin/payment/addBlackList`
- **Service**：`AlipayTripService.addBlackListForAlipay` → `AlipayPaymentServiceImpl.addBlackListForAlipay` → `BlacklistClient.addBlackList()`

**请求参数** (`AddBlackListReqDTO`)：

```java
String thirdUserId;    // 第三方用户ID
String cardId;         // 卡号
String cardType;       // 卡类型
String blackListType;  // 黑名单类型
String reason;         // 原因
String optionDate;     // 操作日期
String expireTime;     // 过期时间（可选）
```

**响应参数** (`BlackListOperateResult`)：

```java
String retCode; // 返回码
String retMsg;  // 返回消息
```

---

#### 2.4.4 执行解约

- **路径**：`POST /admin/payment/executeTermination`
- **Service**：`AlipayTripService.executeTermination` → `AlipayPaymentServiceImpl.executeTermination` → `AlipayPaySignClient.executeTermination()`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `agreementCode` | String | 否 | 签约协议号，为空时执行所有待处理解约 |

**响应参数** (`TerminationExecuteResult`)：

```java
String retCode;      // 返回码
String retMsg;       // 返回消息
String agreementCode;// 签约协议号
```

---

### 2.5 通知接口（基础路径：`/notify`）

**Controller**：`FepAlipayTripNotifyController`

#### 2.5.1 支付结果回调

- **路径**：`POST /notify/payment/payNotify`
- **Service**：`AlipayTripService.handlePaymentNotify` → `AlipayNotifyServiceImpl.handlePaymentNotify` → `AlipayPaySignClient.payNotify()`
- **请求方式**：`@RequestParam Map<String, String>`，业务字段在 `bizData` 参数中（`application/x-www-form-urlencoded` 格式）

**请求参数**：

```java
Map<String, String> params; // 包含 bizData 字段
// bizData 内容 (AlipayTripPayNotifyReqDTO):
String orderNo;           // 订单号
String transStatus;       // 交易状态：1-成功
String channelVoucherId;  // 渠道凭证号
String transTime;         // 交易时间
String transAmount;       // 交易金额
```

**响应参数** (`AlipayTripPayNotifyRespDTO`)：

```java
String retCode; // 返回码
String retMsg;  // 返回消息
```

**业务逻辑**：

1. 从 `params` 中提取 `bizData`。
2. 反序列化 `bizData` 为 `AlipayTripPayNotifyReqDTO`。
3. 调用 `AlipayPaySignClient.payNotify()` 转发到 `alipay-pay-sign-server` 处理支付回调。
4. 返回 `alipay-pay-sign-server` 响应。

---

#### 2.5.2 业务关闭结果通知

- **路径**：`POST /notify/closeResultForAlipay`
- **Service**：`AlipayTripService.closeResult` → `AlipayNotifyServiceImpl.closeResult` → `AlipayPaySignClient.closeResult()`
- **请求方式**：`@RequestBody` 直接 JSON

**请求参数** (`AlipayTripCloseResultReqDTO`)：

```java
String agreementNo; // 签约协议号
Boolean result;     // 处理结果 true-成功 false-失败
```

**响应参数** (`AlipayTripCloseResultRespDTO`)：

```java
String retCode; // 返回码
String retMsg;  // 返回消息
```

**业务逻辑**：

1. 接收支付宝销卡结果通知。
2. 调用 `AlipayPaySignClient.closeResult()` 转发到 `alipay-pay-sign-server`。
3. 更新解约记录状态。
4. 返回结果。

---

## 三、服务交互

### 3.1 上游调用

fep-alipay-server 作为网关，接收支付宝APP/小程序的直接调用。

### 3.2 下游依赖

| 服务 | 调用方式 | 接口/方法 | 说明 |
|------|----------|-----------|------|
| `alipay-account-server` | RPC | `AlipayAccountClient` | 开户、查询用户信息、更新支付通道 |
| `alipay-pay-sign-server` | RPC | `AlipayPaySignClient` | 签约、解约、支付、退款、查询 |
| `ticket-server` | RPC | `TicketClient` | 查询乘车记录列表 |
| `blacklist-server` | RPC | `BlacklistClient` | 黑名单管理 |

### 3.3 交互图

```
┌──────────┐     HTTP      ┌──────────────────┐
│ 支付宝APP │ ──────────────► │ fep-alipay-server│
│ /小程序   │                │     (8080)        │
└──────────┘                └────────┬─────────┘
                                       │
                                       │ RPC
                                       ▼
                               ┌──────────────────┐
                               │ alipay-account   │
                               │     -server      │
                               └──────────────────┘
                                       │
                                       │ RPC
                                       ▼
                               ┌──────────────────┐
                               │ alipay-pay-sign  │
                               │     -server      │
                               └──────────────────┘
                                       │
                                       │ RPC
                                       ▼
                               ┌──────────────────┐
                               │   ticket-server  │
                               └──────────────────┘
```

---

## 四、文件清单

### 4.1 Java 源文件

**控制器层**：
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/controller/FepAlipayTripController.java` - Channel 接口
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/controller/FepAlipayTripMemberContractController.java` - MemberContract 接口
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/controller/FepAlipayTripPaymentController.java` - 支付接口
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/controller/FepAlipayTripNotifyController.java` - 通知接口

**服务层**：
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/AlipayTripService.java` - 门面接口
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/AlipayContractService.java` - 合约服务接口
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/AlipayApplicationService.java` - 开户/行业数据服务接口
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/AlipayQueryService.java` - 查询服务接口
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/AlipayPaymentService.java` - 支付服务接口
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/AlipayNotifyService.java` - 通知服务接口

**服务实现层**：
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/impl/AlipayTripServiceImpl.java` - 门面实现
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/impl/AlipayContractServiceImpl.java` - 合约服务实现
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/impl/AlipayApplicationServiceImpl.java` - 开户/行业数据实现
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/impl/AlipayQueryServiceImpl.java` - 查询服务实现
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/impl/AlipayPaymentServiceImpl.java` - 支付服务实现
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/impl/AlipayNotifyServiceImpl.java` - 通知服务实现

**模型层**：
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/model/CommonFormRequest.java` - FormData 请求包装类

**异常处理**：
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/exception/BusinessException.java`
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/exception/GlobalExceptionHandler.java`

**启动类**：
- `fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/FepAlipayServer.java`

### 4.2 资源文件

1. `fep-alipay-server/src/main/resources/application.properties`
2. `fep-alipay-server/src/main/resources/application.yml`

---

## 五、相关文档

- `docs/01-项目概述/支付宝乘车业务流.md` - 支付宝乘车业务流图
- `docs/03-接口文档/接口改造记录/签约渠道与支付宝出行接口改造记录.md` - 签约渠道改造记录

---

## 六、源码文件索引

### 6.1 所有 Java 源文件

```
fep-alipay-server/pom.xml
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/FepAlipayServer.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/controller/FepAlipayTripController.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/controller/FepAlipayTripMemberContractController.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/controller/FepAlipayTripPaymentController.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/controller/FepAlipayTripNotifyController.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/AlipayTripService.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/AlipayContractService.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/AlipayApplicationService.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/AlipayQueryService.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/AlipayPaymentService.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/AlipayNotifyService.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/impl/AlipayTripServiceImpl.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/impl/AlipayContractServiceImpl.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/impl/AlipayApplicationServiceImpl.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/impl/AlipayQueryServiceImpl.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/impl/AlipayPaymentServiceImpl.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/impl/AlipayNotifyServiceImpl.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/model/CommonFormRequest.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/exception/BusinessException.java
fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/exception/GlobalExceptionHandler.java
```

### 6.2 所有资源文件

```
fep-alipay-server/src/main/resources/application.properties
fep-alipay-server/src/main/resources/application.yml
```

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/fep-alipay-server` 模块源码。
