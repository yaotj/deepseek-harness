# 支付宝出行-添加签约信息

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project] /channel/addContract
```

## 接口说明

添加支付宝出行签约信息。

## 请求参数

参数详情参见规范表 137。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

## 应答参数

参数详情参见规范表 138。

| 字段 | 类型 | 说明 |
|------|------|------|
| thirdUserId | String | 第三方用户ID |
| entryStationName | String | 进站站点名称 |
| exitStationName | String | 出站站点名称 |
| entryStationDate | String | 进站日期 |
| exitStationDate | String | 出站日期 |
| payAmount | String | 支付金额 |
| tradeOrderNo | String | 交易订单号 |
| payTradeOrderNo | String | 支付交易订单号 |
| orderExpType | String | 订单类型 |
| payChannelCode | String | 支付渠道代码 |
| orderStatus | String | 订单状态 |
| requestDebitNo | String | 请求扣款编号 |
| cardNo | String | 卡号 |
| ticketType | String | 票种类型 |
| transSeq | String | 交易序列号 |
| debitAmount | String | 扣款金额 |

