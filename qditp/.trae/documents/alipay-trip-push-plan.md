# Plan: 在推送行业数据后查询行程数据并推送给支付宝

## Summary

在 `TicketRideStatusServiceImpl` 的 `notifyVerifyResult` 方法中，`appNotifyService.notifyVerifyResult` 调用完成后，针对 `issueChannelCode=07`（支付宝）的场景，构建行程数据并直接推送给支付宝外部地址（3.74 接口）。

## Current State Analysis

- `AppNotifyServiceImpl.notifyVerifyResult` 已支持按 `issueChannelCode` 区分推送地址（地铁 APP vs 支付宝行业数据）
- 新建 `AlipayPushTransDataReqDTO`，按支付宝 3.74 接口规范定义行程数据推送字段
- `TicketRideStatusServiceImpl.notifyVerifyResult` 在调用 `appNotifyService.notifyVerifyResult` 后直接返回，没有后续行程推送逻辑
- `notifyVerifyResult` 方法已接收完整的闸机交易参数 `NotifyVerifyResultReqDTO` 和更新后的 `QRCodeStatus`，包含构建行程数据所需的全部字段
- `QRCodeTxnDetail` 在 `notifyVerifyResult` 中已入库，可获取自增主键作为交易记录 ID（后调整为 `ticketTransSeq`）

## Proposed Changes

### 1. `ticket-server/src/main/java/com/chinasofti/huateng/ticket/service/impl/TicketRideStatusServiceImpl.java`

**修改 `notifyVerifyResult` 方法：**
在 `appNotifyService.notifyVerifyResult(request, nextStatus)` 调用后，增加行程数据推送调用：
```java
String issueChannelCode = request.getIssueChannelCode();
appNotifyService.notifyVerifyResult(request, nextStatus);

// 支付宝渠道：行业数据推送完成后，推进行程数据
if ("07".equals(issueChannelCode)) {
    appNotifyService.pushAlipayTripData(request, nextStatus, detail);
}
```

### 2. `ticket-server/src/main/java/com/chinasofti/huateng/ticket/service/AppNotifyService.java`

**新增接口方法：**
```java
void pushAlipayTripData(NotifyVerifyResultReqDTO request, QRCodeStatus qrCodeStatus, QRCodeTxnDetail detail);
```

### 3. `ticket-server/src/main/java/com/chinasofti/huateng/ticket/service/impl/AppNotifyServiceImpl.java`

**新增配置注入：**
```java
@Value("${app.notify.alipay-push-trans-data-url:https://dtcustomer.bestonepay.com/ngopenplatform/notify/pushTransData}")
private String alipayPushTransDataUrl;
```

**注入 `StationInfoMapper`：**
用于根据车站代码查询线路代码（`transLine`）。

**新增公开方法 `pushAlipayTripData`：**
- 构建 `AlipayPushTransDataReqDTO`，字段来源：
  - `logicCard`：`request.getCardId()`
  - `transType`：`request.getTrxType()`
  - `transTime`：`request.getHandleDateTime()`（`yyyyMMddHHmmss` → `yyyy-MM-dd HH:mm:ss`）
  - `transSeq`：`request.getTicketTransSeq()`
  - `transStation`：`request.getHandleStationCode()`
  - `transLine`：`StationInfoMapper.selectByStationCode` 查询，查不到时用 `transStation` 兜底
  - `tirpNo`：`request.getTicketTransSeq()`（交易序列号，真实业务数据）
  - `thirdUserId`：`request.getItpUserId()`（16进制转10进制）
  - `cardId`：`request.getCardId()`
  - `cardType`：`request.getCardType()`
  - `signType`：固定 `"00"`
  - `sign`：固定 `""`
- 使用 OkHttp 直接 POST `multipart/form-data`（`bizData` 字段），和行业数据推送格式一致
- 推送失败仅记录日志，不影响主流程

### 4. `model/src/main/java/com/chinasofti/huateng/model/alipaytrip/AlipayPushTransDataReqDTO.java`

**新建 DTO，字段：**
- `logicCard` - 逻辑卡号
- `transType` - 交易类型
- `transTime` - 交易时间（`yyyy-MM-dd HH:mm:ss`）
- `transSeq` - 交易序列号
- `transStation` - 交易车站代码
- `transLine` - 交易线路代码
- `tirpNo` - 交易记录id
- `thirdUserId` - 第三方用户ID
- `cardId` - 逻辑卡号
- `cardType` - 卡类型
- `signType` - 签名类型
- `sign` - 签名

### 5. `ticket-server/src/main/resources/application.properties`

```properties
app.notify.alipay-push-trans-data-url=https://dtcustomer.bestonepay.com/ngopenplatform/notify/pushTransData
```

## Assumptions & Decisions

- **推送目标**：直接推送给支付宝外部地址，不经过 `fep-alipay-server` 内部接口（与行业数据推送保持一致）
- **触发条件**：仅 `issueChannelCode="07"` 时推进行程数据
- **执行时机**：在 `TicketRideStatusServiceImpl.notifyVerifyResult` 中，`appNotifyService.notifyVerifyResult` 调用后执行
- **数据来源**：使用 `notifyVerifyResult` 方法已有的参数构建，`transLine` 通过 `StationInfoMapper` 查询 `STATION_INFO` 表
- **失败处理**：推送异常仅记录日志，不回滚业务事务，不影响闸机检票通知的返回结果
- **请求格式**：`multipart/form-data`，通过 `bizData` 字段传递 JSON，与行业数据推送格式一致
- **公共参数**：包含 `thirdUserId`、`cardId`、`cardType`、`signType="00"`、`sign=""`

## Known Issues / Risks

1. **`transLine` 数据依赖 `STATION_INFO` 表**：如果 `STATION_INFO` 表没有对应车站的线路映射，`transLine` 将用 `transStation`（车站代码）兜底。需确认支付宝是否接受此兜底方案，或是否需要补充 `STATION_INFO` 表数据。
2. **`tirpNo` 与业务订单号的关系**：当前使用 `ticketTransSeq`（票卡交易序列号）作为 `tirpNo`。如果支付宝期望的是支付订单号或其他业务订单号，需进一步调整。
3. **`transTime` 格式转换**：系统内时间格式为 `yyyyMMddHHmmss`，需转换为 `yyyy-MM-dd HH:mm:ss`。需确认转换逻辑是否覆盖所有场景（如 `null` 或长度不足 14 位的情况）。
4. **异步推送顺序**：行业数据推送和行程数据推送均通过 `appNotifyExecutor` 线程池异步执行，但当前代码中行程推送是在行业数据推送完成后同步调用的。如果行业数据推送耗时较长，可能影响行程数据推送的及时性。

## Verification Steps

1. 单测验证：`issueChannelCode="07"` 时，`TicketRideStatusServiceImpl.notifyVerifyResult` 调用 `appNotifyService.pushAlipayTripData`
2. 单测验证：其他 `issueChannelCode` 不触发行程数据推送
3. 单测验证：`AlipayPushTransDataReqDTO` 字段从 `NotifyVerifyResultReqDTO` 和 `QRCodeStatus` 正确映射
4. 单测验证：推送地址读取配置 `app.notify.alipay-push-trans-data-url` 生效
5. 单测验证：OkHttp 请求为 `multipart/form-data` 格式，`bizData` 包含完整 JSON
6. 单测验证：`transLine` 查询不到时用 `transStation` 兜底
7. 单测验证：`tirpNo` 使用 `ticketTransSeq`
8. 单测验证：推送异常时仅记录日志，不抛出异常影响主流程
