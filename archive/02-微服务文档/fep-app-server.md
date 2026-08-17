# fep-app-server 微服务文档

> **模块路径**: `fep-app-server/`
> **端口**: 9101
> **职责**: APP 接入前置网关服务，处理移动端 APP 的公共报文接收、参数解析、路由转发和业务编排
> **源码阅读范围**: `fep-app-server/src/main/java/`、`fep-app-server/src/main/resources/`
> **整理时间**: 2026-07-28

---

## 一、模块概述

### 1.1 核心职责

fep-app-server 是 ITP 平台的 APP 接入前置网关，承担以下核心职责：

1. **APP 公共报文接收** - 接收 APP 发送的 FormData 格式公共报文
2. **参数解析与路由转发** - 解析公共字段，将业务参数转发到后端微服务
3. **业务编排** - 协调 account-server、paySign-server、ticket-server 等后端服务
4. **支付回调处理** - 接收支付中心回调并解析业务数据
5. **日票业务代理** - 代理日票下单、支付、激活、退款等接口
6. **支付宝出行代理** - 代理支付宝出行渠道的签约、开卡、生码、查询等接口
7. **黑名单通知** - 接收黑名单状态变更通知并转发到 blacklist-server

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| FastJSON | JSON 序列化 |
| OpenFeign | RPC 服务调用 |
| MyBatis | 数据访问层 |

### 1.3 端口配置

```properties
server.port=9101
```

### 1.4 依赖下游服务

| 服务 | 调用时机 | 调用方法 |
|------|---------|---------|
| account-server | 开户、密钥、支付通道 | `accountClient.*` |
| key-server | 密钥同步 | `keyClient.requestKeyList` |
| industry-data-server | 生成卡数据 | `industryDataClient.buildCardData` |
| ticket-server | 票务查询、补站、行程 | `ticketClient.*` |
| paySign-server | 签约、解约、支付回调 | `paySignClient.*` |
| blacklist-server | 黑名单查询 | `blacklistClient.queryBlackList` |
| para-server | 线路、车站、票价 | `paraClient.*` |
| daily-ticket-server | 日票业务 | `dailyTicketClient.*` |

---

## 二、接口清单

### 2.1 AppAccountController（账户与密钥）

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| IF8A-01 | 请求开户 | `/ci/app/requestApplication` | POST | `CommonFormRequest` + `RequestApplicationReqDTO` |
| IF8A-02 | 请求同步密钥 | `/ci/app/requestKeyList` | POST | `CommonFormRequest` + `RequestKeyListReqDTO` |
| IF8A-23 | 请求添加支付通道 | `/ci/app/requestAddPayChannel` | POST | `CommonFormRequest` + `RequestAddPayChannelReqDTO` |
| IF8A-24 | 请求设置默认支付通道 | `/ci/app/requestSetDefaultPayChannel` | POST | `CommonFormRequest` + `RequestSetDefaultPayChannelReqDTO` |

### 2.2 AppIndustryDataController（行业数据）

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| IF8A-03 | 请求行业数据 | `/ci/app/requestIndustryData` | POST | `CommonFormRequest` + `RequestIndustryDataReqDTO` |
| IF8D_03 | 获取离线码数据 | `/ci/app/requestNoSignalData` | POST | `CommonFormRequest` + `RequestNoSignalDataReqDTO` |

### 2.3 AppParaController（基础参数）

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| IF8A-07 | 获取线路代码 | `/ci/app/requestLineCodeList` | POST | `CommonFormRequest` + `RequestLineCodeListReqDTO` |
| IF8A-08 | 获取车站代码 | `/ci/app/requestStationCodeList` | POST | `CommonFormRequest` + `RequestStationCodeListReqDTO` |
| IF8A-10 | 计算票价 | `/ci/app/requestTicketPriceByStation` | POST | `CommonFormRequest` + `RequestTicketPriceByStationReqDTO` |
| IF8A-17 | 获取线路站点代码版本 | `/ci/app/requestLineStationCodeVersion` | POST | `CommonFormRequest` + `RequestLineStationCodeVersionReqDTO` |

### 2.4 AppPaySignController（签约与支付回调）

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| IF8A-16 | 请求签约信息 | `/ci/app/requestSignInfo` | POST | `CommonFormRequest` + `RequestSignInfoReqDTO` |
| IF8A-06 | 请求解约 | `/ci/app/requestTermination` | POST | `CommonFormRequest` + `RequestTerminationReqDTO` |
| IPD02 | 接收签约结果通知 | `/ci/app/receiveSignResult` | POST | `PayCenterCommonRequest` + `ReceiveSignResultReqDTO`，bizData Base64 |
| 支付API 5.3 | 接收解约结果通知 | `/ci/app/receiveTerminationResult` | POST | JSON Body，兼容根节点/bizData |
| 支付API 5.1 | 接收支付结果通知 | `/ci/app/receivePayResult` | POST | JSON Body，兼容根节点/bizData |

**支付回调说明**：
- `receiveSignResult` 使用 `@RequestBody PayCenterCommonRequest`，bizData 为 Base64 编码
- `receivePayResult` / `receiveTerminationResult` 使用 `@RequestBody String`，解析时兼容：
  - 通用包装：`{bizData: {...}}`
  - 根节点业务：业务字段直接在根节点
  - Base64 编码：`{bizData: "eyJ...=="}`

### 2.5 AppTicketController（票务查询）

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| IF8A-73 | 查询黑名单 | `/ci/app/ticket/queryBlackList` | POST | `CommonFormRequest` + `QueryBlackListReqDTO` |
| IF8A-29 | 查询用户上次行程 | `/ci/app/queryUserItinerary` | POST | `CommonFormRequest` + `QueryUserItineraryReqDTO` |
| IF8A-04 | 请求自助补站 | `/ci/app/requestExcessFare` | POST | `CommonFormRequest` + `RequestExcessFareReqDTO` |
| IF8A-05 | 请求查询交易记录 | `/ci/app/requestTransList` | POST | `CommonFormRequest` + `RequestTransListReqDTO` |

### 2.6 FepAppDailyTicketController（日票业务）

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| IF8A-60 | 日票下单 | `/app/requestCountingOrder` | POST | `CommonFormRequest` + `DailyTicketOrderReqDTO` |
| IF8A-61 | 日票支付 | `/app/payment/requestPay` | POST | `CommonFormRequest` + `DailyTicketPayReqDTO`，channelType 转 scene：1-app，2-wap |
| - | 日票支付结果通知 | `/app/payment/receivePayResult` | POST | `@RequestBody String`，支付网关回调，不进入 pay-sign |
| IF8A-62 | 日票支付结果查询 | `/app/payment/requestPayResult` | POST | `CommonFormRequest` + `DailyTicketOrderNoReqDTO` |
| IF8A-64 | 日票退款 | `/app/payment/requestRefundTicket` | POST | `CommonFormRequest` + `DailyTicketOrderNoReqDTO` |
| IF8A-65 | 日票取消订单 | `/app/ticket/cancelOrder` | POST | `CommonFormRequest` + `DailyTicketOrderNoReqDTO` |
| IF8A-67 | 日票激活 | `/app/ticket/updateTicket` | POST | `CommonFormRequest` + `DailyTicketActivateReqDTO` |
| IF8A-71 | 通知 ACC 车票已使用 | `/app/ticket/updateAndNotice` | POST | `CommonFormRequest` + `DailyTicketUsedNoticeReqDTO` |

**注意**：`FepAppDailyTicketController` 与 `AppNotifyController` 均声明 `@RequestMapping("/app")`，存在路径映射冲突风险。

### 2.7 FepAlipayTripController（支付宝出行）

| 接口编码 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| 1.1 | 添加签约信息 | `/channel/addContract` | POST | `CommonFormRequest` + `AlipayTripAddContractReqDTO` |
| 1.2 | 解约登记 | `/channel/terminateContract` | POST | `CommonFormRequest` + `AlipayTripTerminateContractReqDTO` |
| 1.3 | 开卡申请 | `/channel/requestApplication` | POST | `CommonFormRequest` + `AlipayTripRequestApplicationReqDTO` |
| 1.4 | 获取行业数据 | `/channel/requestIndustryData` | POST | `CommonFormRequest` + `AlipayTripRequestIndustryDataReqDTO` |
| 1.5 | 查询乘车记录 | `/channel/findTravelList` | POST | `CommonFormRequest` + `AlipayTripFindTravelListReqDTO` |
| 1.6 | 查询乘车记录详情 | `/channel/findTravelDetail` | POST | `CommonFormRequest` + `AlipayTripFindTravelDetailReqDTO` |

### 2.8 FepAlipayTripNotifyController（支付宝出行通知）

| 接口编码 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| 1.7 | 业务关闭结果通知 | `/notify/closeResultForAlipay` | POST | `AlipayTripCloseResultReqDTO` |
| 1.8 | 行程数据推送 | `/notify/pushTransData` | POST | `AlipayTripPushTransDataReqDTO` |
| 1.9 | 行业数据推送 | `/notify/receiveCardDataFromItp` | POST | `AlipayTripReceiveCardDataReqDTO` |
| 1.10 | 黑名单状态变更通知 | `/notify/receiveBlackListFromItp` | POST | `AlipayTripReceiveBlackListReqDTO` |

### 2.9 FepAlipayTripMemberContractController（支付宝出行行业数据）

| 接口编码 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| 1.4 | 获取行业数据 | `/memberContract/channel/requestIndustryData` | POST | `CommonFormRequest` + `AlipayTripRequestIndustryDataReqDTO` |

### 2.10 AppNotifyController（接收外部通知）

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| IF8B-03 | 接收黑名单结果通知 | `/app/receiveBlackListFromItp` | POST | `@RequestBody ReceiveBlackListFromItpReqDTO` |

---

## 三、核心业务流程

### 3.1 IF8A-03 请求行业数据

**业务流程**：

1. **参数校验**：校验 `thirdUserId`、`cardId`、`cardType` 是否为空
2. **查询用户信息**：调用 `accountClient.queryUserInfo` 获取用户签约渠道
3. **解析签约渠道**：截取渠道前 2 位作为 `signChannelCode`，若为空则返回错误
4. **查询二维码状态**：调用 `ticketClient.queryQrCodeStatus` 获取用户最新二维码状态
5. **生成卡数据请求**：组装 `IndustryCardDataBuildReqDTO`，其中 `issueChannelCode` 默认为 `01`
6. **调用独立生码服务**：调用 `industryDataClient.buildCardData` 生成卡数据
7. **返回结果**：将卡数据放入响应返回

**依赖服务**：account-server、ticket-server、industry-data-server

### 3.2 IF8D_03 获取离线码数据

**业务流程**：

1. **参数校验**：校验 `thirdUserId`、`cardId`、`cardType` 是否为空
2. **查询用户信息**：调用 `accountClient.queryUserInfo` 获取用户签约渠道
3. **解析签约渠道**：截取渠道前 2 位作为 `signChannelCode`
4. **查询二维码状态**：调用 `ticketClient.queryQrCodeStatus` 获取用户最新二维码状态
5. **判断是否在站内**：
   - **在站内**（`status=04`）：只生成出站码数据，`txnSeq` 使用二维码当前 `txnSeq`，`ticketStatus` 固定为 `04`
   - **不在站内**：同时生成进站码和出站码数据
     - 进站码：`txnSeq` 为当前 `txnSeq + 1`，`ticketStatus` 为二维码状态或默认 `03`
     - 出站码：`txnSeq` 为当前 `txnSeq + 1`，`ticketStatus` 固定为 `04`
6. **调用独立生码服务**：分别调用 `industryDataClient.buildCardData` 生成进站码和/或出站码数据
7. **返回结果**：将 `entryData` 和/或 `exitData` 放入响应返回

**依赖服务**：account-server、ticket-server、industry-data-server

### 3.3 支付宝出行-获取行业数据

**业务流程**：

1. **参数校验**：校验 `thirdUserId`、`cardId`、`cardType` 是否为空
2. **查询用户信息**：调用 `accountClient.queryUserInfo` 获取用户签约渠道
3. **解析签约渠道**：截取渠道前 2 位作为 `signChannelCode`
4. **查询二维码状态**：调用 `ticketClient.queryQrCodeStatus` 获取用户最新二维码状态
5. **生成卡数据请求**：组装 `IndustryCardDataBuildReqDTO`，其中 `issueChannelCode` 固定为 `07`
6. **调用独立生码服务**：调用 `industryDataClient.buildCardData` 生成卡数据
7. **返回结果**：将卡数据放入响应返回

**依赖服务**：account-server、ticket-server、industry-data-server

---

## 四、数据模型

### 4.1 CommonFormRequest

位置：`com.chinasofti.huateng.fep.app.model.CommonFormRequest`

用于接收 APP FormData 格式的公共请求报文。

| 字段 | 类型 | 描述 |
|------|------|------|
| `providerId` | String | 提供者 ID |
| `charset` | String | 字符集 |
| `format` | String | 报文格式 |
| `timestamp` | String | 时间戳 |
| `deviceId` | String | 设备 ID |
| `signType` | String | 签名类型 |
| `sign` | String | 签名 |
| `bizData` | String | 业务参数（JSON 字符串） |

**说明**：`bizData` 为 String 类型，Controller 层通过 FastJSON 反序列化为具体业务 DTO。

### 4.2 CommonRequest

位置：`com.chinasofti.huateng.fep.app.model.CommonRequest`

用于接收 JSON Body 格式的公共请求报文。

| 字段 | 类型 | 描述 |
|------|------|------|
| `providerId` | String | 提供者 ID |
| `charset` | String | 字符集 |
| `format` | String | 报文格式 |
| `timestamp` | String | 时间戳 |
| `deviceId` | String | 设备 ID |
| `signType` | String | 签名类型 |
| `sign` | String | 签名 |
| `bizData` | T | 业务参数（泛型） |

**说明**：`CommonRequest` 是泛型类，`bizData` 为具体业务对象类型，用于 `@RequestBody` JSON Body 场景。

---

## 五、核心类说明

### 5.1 BaseAppController

基础控制器，提供公共方法：
- `parseBizData` - 解析 `bizData` JSON 字符串为业务 DTO
- `parseCallbackBody` - 解析支付中心回调报文，兼容根节点/bizData/Base64
- `decodeBizData` - Base64 解码业务数据

### 5.2 AlipayTripServiceImpl

支付宝出行业务编排服务，负责：
- `addContract` - 调用 paySign-server 添加签约
- `terminateContract` - 调用 paySign-server 解约
- `requestApplication` - 调用 account-server 开卡
- `requestIndustryData` - 调用 industry-data-server 生码
- `findTravelList` / `findTravelDetail` - 调用 ticket-server 查询行程

---

## 六、文件清单

### 6.1 控制器层

| 文件路径 | 说明 |
|---------|------|
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppAccountController.java` | 账户与密钥接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppIndustryDataController.java` | 行业数据接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppParaController.java` | 基础参数接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppPaySignController.java` | 签约与支付回调接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppTicketController.java` | 票务查询接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/FepAppDailyTicketController.java` | 日票业务接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/FepAlipayTripController.java` | 支付宝出行接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/FepAlipayTripNotifyController.java` | 支付宝出行通知 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/FepAlipayTripMemberContractController.java` | 支付宝出行行业数据 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppNotifyController.java` | 接收黑名单通知 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/BaseAppController.java` | 基础控制器 |

### 6.2 服务层

| 文件路径 | 说明 |
|---------|------|
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/AccountAppService.java` | 账户服务接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/IndustryDataService.java` | 行业数据服务接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/ParaAppService.java` | 参数服务接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/PaySignAppService.java` | 签约服务接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/TicketAppService.java` | 票务服务接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/DailyTicketAppService.java` | 日票服务接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/AlipayTripService.java` | 支付宝出行服务接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/AlipayContractService.java` | 支付宝签约服务接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/AlipayApplicationService.java` | 支付宝开户服务接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/AlipayQueryService.java` | 支付宝查询服务接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/AlipayPaymentService.java` | 支付宝支付服务接口 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/AlipayNotifyService.java` | 支付宝通知服务接口 |

### 6.3 模型层

| 文件路径 | 说明 |
|---------|------|
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/model/CommonFormRequest.java` | FormData 公共请求 |
| `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/model/CommonRequest.java` | JSON Body 公共请求 |

### 6.4 资源配置

| 文件路径 | 说明 |
|---------|------|
| `fep-app-server/src/main/resources/application.properties` | 应用配置 |

---

## 七、与其他服务的交互

### 7.1 服务调用关系

| 调用方 | 被调用服务 | 调用时机 | 调用方法 |
|--------|-----------|---------|---------|
| fep-app-server | account-server | 开户、密钥、支付通道 | `accountClient.*` |
| fep-app-server | key-server | 密钥同步 | `keyClient.requestKeyList` |
| fep-app-server | industry-data-server | 生成卡数据 | `industryDataClient.buildCardData` |
| fep-app-server | ticket-server | 票务查询 | `ticketClient.*` |
| fep-app-server | paySign-server | 签约、解约、支付 | `paySignClient.*` |
| fep-app-server | blacklist-server | 黑名单查询 | `blacklistClient.queryBlackList` |
| fep-app-server | para-server | 线路、车站、票价 | `paraClient.*` |
| fep-app-server | daily-ticket-server | 日票业务 | `dailyTicketClient.*` |

---

## 八、待办事项

### 8.1 已知问题

1. **路径映射冲突** - `AppNotifyController` 与 `FepAppDailyTicketController` 均声明 `@RequestMapping("/app")`，存在冲突风险
2. **黑名单入口重复** - IF8A-73 提供了两个入口：`/ci/app/ticket/queryBlackList` 和 `/app/ticket/queryBlackList`

### 8.2 后续优化

1. 统一接口路径前缀规范
2. 消除路径映射冲突
3. 补充更多业务接口

---

## 九、相关文档

- `docs/01-项目概述/支付宝乘车业务流.md` - 支付宝乘车业务流
- `docs/03-接口文档/` - 接口规范文档

---

## 十、源码文件索引

### 10.1 Java 源文件（共 26 个）

**控制器层 (11 个)**:
1. `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppAccountController.java`
2. `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppIndustryDataController.java`
3. `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppParaController.java`
4. `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppPaySignController.java`
5. `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppTicketController.java`
6. `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/FepAppDailyTicketController.java`
7. `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/FepAlipayTripController.java`
8. `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/FepAlipayTripNotifyController.java`
9. `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/FepAlipayTripMemberContractController.java`
10. `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppNotifyController.java`
11. `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/BaseAppController.java`

**服务层 (12 个)**:
12-23. 见 6.2 文件清单

**模型层 (2 个)**:
24. `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/model/CommonFormRequest.java`
25. `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/model/CommonRequest.java`

**启动类 (1 个)**:
26. `fep-app-server/src/main/java/com/chinasofti/huateng/FepAppServer.java`

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/fep-app-server` 模块源码。
