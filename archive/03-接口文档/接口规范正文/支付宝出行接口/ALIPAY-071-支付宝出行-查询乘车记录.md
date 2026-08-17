# 支付宝出行-查询乘车记录

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
POST http://[ip]:[port]/[project]/channel/findTravelList
```

## 接口说明

查询支付宝出行乘车记录列表。

## 请求参数

参数详情参见规范表 145。

| 字段 | 类型 | 说明 |
|------|------|------|
| providerId | string | 服务提供商编码 |
| charset | string | 字符集 |
| format | string | 报文格式 |
| timestamp | string | 时间戳 |
| deviceId | string | 设备编码 |
| signType | string | 签名类型 |
| sign | string | 签名字段 |
| bizData | string | 业务参数 JSON 字符串 |

### bizData 参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| thirdUserId | string | 是 | 第三方用户 ID |
| page | string | 否 | 页码，从0开始 |
| size | string | 否 | 每页大小 |
| debitRequestResult | string | 否 | 扣款请求结果筛选。空字符串查全部；"0" 查扣款成功；"1" 查扣款失败/未成功 |
| invoice | string | 否 | 发票状态筛选 |
| startDate | string | 否 | 开始日期，格式 yyyy-MM-dd HH:mm:ss |
| endDate | string | 否 | 结束日期，格式 yyyy-MM-dd HH:mm:ss |

## 应答参数

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| pageNumber | int | 当前页码 |
| pageSize | int | 每页大小 |
| totalPage | int | 总页数 |
| totalCount | int | 总记录数 |
| ticketTransRecord | array | 乘车记录列表 |

### ticketTransRecord 字段

| 字段 | 类型 | 说明 | 数据来源 |
|------|------|------|----------|
| entryStationName | string | 进站站点名称 | ticket-server `QRCODE_TXN_DETAIL.HANDLE_STATION_CODE` |
| entryDate | string | 进站时间 | ticket-server `QRCODE_TXN_DETAIL.HANDLE_DATE_TIME` |
| exitStationName | string | 出站站点名称 | ticket-server `QRCODE_TXN_DETAIL.HANDLE_STATION_CODE`（当前实现与进站统一） |
| exitDate | string | 出站时间 | ticket-server `QRCODE_TXN_DETAIL.HANDLE_DATE_TIME`（当前实现与进站统一） |
| payAmount | string | 实付金额（单位：分） | ticket-server `TRX_AMOUNT` |
| totalAmount | string | 总金额（单位：分） | ticket-server `TOTAL_AMOUNT` |
| orderExpType | string | 订单扩展类型，默认 "0" | ticket-server `ORDER_EXP_TYPE` |
| tradeOrderNo | string | 交易订单号 | ticket-server `TICKET_TRANS_SEQ` |
| payTradeOrderNo | string | 支付交易订单号 | alipay-pay-sign-server `CHANNEL_ORDER_NO` |
| payOrderNoDate | string | 支付订单日期 | alipay-pay-sign-server `ORDER_NO` |
| payChannelCode | string | 支付渠道代码，固定 "07" | 固定值 |
| debitRequestResult | string | 扣款请求结果，SUCCESS 返回 "0"，其他返回 "1" | alipay-pay-sign-server `PAY_STATUS` 映射 |
| discountFee | string | 优惠金额 | ticket-server `RESERVE2` |
| discountInfo | string | 优惠信息，固定 null | 固定 null |
| companionFlag | string | 同行票标识 | ticket-server `TRX_TYPE` |
| cardNum | string | 卡号 | alipay-pay-sign-server `CARD_ID` |
| ticketCode | string | 日票票号，固定 null | 固定 null |
| countingTimes | string | 计次次数（预留），固定 null | 固定 null |
| countingFlag | string | 计次标识（预留），固定 "N" | 固定 "N" |
| invoice | string | 发票状态 | alipay-pay-sign-server `INVOICE` |

## 业务流程

1. 支付宝出行调用 `/channel/findTravelList`。
2. fep-alipay-server 解析 `bizData` 中的查询参数。
3. 调用 alipay-pay-sign-server 按 `thirdUserId`、日期范围、`debitRequestResult`、`invoice` 查询支付流水。
4. 组装乘车记录，补充 ticket-server 和 alipay-pay-sign-server 字段。
5. 返回乘车记录列表。

## 错误码

| 错误码 | 说明 |
|--------|------|
| 0000 | 成功 |
| 1001 | 无效的参数 |
| 9999 | 系统内部错误 |
