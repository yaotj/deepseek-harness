# 支付宝出行-查询乘车记录详情

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project] /channel/findTravelDetail
```

## 接口说明

查询支付宝出行乘车记录详情。根据第三方用户 ID 和订单号查询单条乘车记录的详细信息。

## 请求参数

参数详情参见规范表 147。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| providerId | string | 否 | 服务提供商编码 |
| charset | string | 否 | 字符集，默认 UTF-8 |
| format | string | 否 | 报文格式，默认 json |
| timestamp | string | 否 | 时间戳 |
| deviceId | string | 否 | 设备编码 |
| signType | string | 否 | 签名类型 |
| sign | string | 否 | 签名字段 |
| bizData | string | 是 | 业务参数 JSON 字符串 |

### bizData 参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| thirdUserId | string | 是 | 第三方用户 ID |
| orderNo | string | 是 | 订单号 |

## 应答参数

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| entryStationName | string | 进站名称 |
| entryDate | string | 进站时间 |
| exitStationName | string | 出站名称 |
| exitDate | string | 出站时间 |
| payAmount | string | 支付金额 |
| totalAmount | string | 总金额 |
| orderExpType | string | 订单过期类型 |
| tradeOrderNo | string | 交易订单号 |
| payTradeOrderNo | string | 支付交易订单号 |
| payOrderNoDate | string | 支付订单日期 |
| payChannelCode | string | 支付渠道编码 |
| debitRequestResult | string | 扣费请求结果 |
| discountFee | string | 优惠金额 |
| discountInfo | string | 优惠信息 |
| companionFlag | string | 陪同标志 |
| cardNum | string | 卡号 |
| ticketCode | string | 票码 |
|| countingTimes | string | 计数次数 |
|| countingFlag | string | 计数标志 |
|| invoice | string | 发票状态 |

## 业务流程

1. 支付宝出行调用 `/channel/findTravelDetail`。
2. fep-alipay-server 解析 `bizData` 中的查询参数。
3. 调用 `ticket-server` 的 `alipayTripFindTravelDetail` 接口。
4. ticket-server 根据 `thirdUserId` 和 `orderNo` 查询单条乘车记录详情。
5. 返回乘车记录详细信息。

## 错误码

| 错误码 | 说明 |
|--------|------|
| 0000 | 成功 |
| 1001 | 无效的参数 |
| 9999 | 系统内部错误 |
