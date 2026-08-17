# if8a_71 - 通知acc车票已使用

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project] /app/ticket/updateAndNotice
```

## 接口说明

车票使用后通知acc,车票已发售。（日后新ITP如果不想提供接口，可以自己在过闸后判断并通知acc）

## 请求参数

参数详情参见规范表 109。

| 字段 | 类型 | 说明 |
|------|------|------|
| orderNo | String | 订单号 |
| payChannelCode | String | 支付渠道编码 |
| thirdUserId | String | 用户ID |
| channelType | String | 支付渠道 |
| phone | String | 手机号 |
| orderType | String | 订单类型
1日票
2旅游票 |

## 应答参数

参数详情参见规范表 110。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| payResult | String | 支付结果(success/failed) |
| payChannel | String | 支付渠道 |
| payAmount | Int | 支付金额(分) |
| couponDiscount | Int | 优惠券优惠金额(分) |
| channelDiscount | Int | 渠道优惠金额(分) |
| discountInfo | Object | 优惠信息 |
| payDate | String | 支付时间yyyy-MM-dd HH:mm:ss |

