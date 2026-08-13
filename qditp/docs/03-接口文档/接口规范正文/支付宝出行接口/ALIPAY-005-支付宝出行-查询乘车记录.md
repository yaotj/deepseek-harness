# 支付宝出行-查询乘车记录

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project] /channel/ findTravelList
```

## 接口说明

查询支付宝出行乘车记录。

## 请求参数

参数详情参见规范表 145。

| 字段 | 类型 | 说明 |
|------|------|------|
| thirdUserId | String | 第三方用户ID，格式化后的用户标识 |
| page | String | 页码，从 0 开始 |
| size | String | 每页大小 |
| debitRequestResult | String | 扣款请求结果筛选（可选） |
| invoice | String | 发票状态筛选（可选） |
| startDate | String | 开始日期（可选） |
| endDate | String | 结束日期（可选） |

## 应答参数

参数详情参见规范表 146。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| pageNumber | Int | 当前页码 |
| pageSize | Int | 每页大小 |
| totalPage | Int | 总页数 |
| totalCount | Int | 总记录数 |
| ticketTransRecord | Array | 乘车记录列表 |

### ticketTransRecord 单条记录

| 字段 | 类型 | 说明 |
|------|------|------|
| entryStationName | String | 进站站点名称 |
| entryDate | String | 进站时间 |
| exitStationName | String | 出站站点名称 |
| exitDate | String | 出站时间 |
| payAmount | String | 实付金额（单位：分） |
| totalAmount | String | 总金额（单位：分） |
| orderExpType | String | 订单扩展类型 |
| tradeOrderNo | String | 交易订单号 |
| payTradeOrderNo | String | 支付交易订单号 |
| payOrderNoDate | String | 支付订单日期 |
| payChannelCode | String | 支付渠道代码 |
| debitRequestResult | String | 扣款请求结果 |
| discountFee | String | 优惠金额 |
| discountInfo | String | 优惠信息 |
| companionFlag | String | 同行票标识 |
| cardNum | String | 卡号 |
| ticketCode | String | 日票票号 |
| countingTimes | String | 计次次数（预留） |
| countingFlag | String | 计次标识（预留） |
