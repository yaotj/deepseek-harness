# 支付宝出行-退款

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project]/api/payment/requestRefund
```

## 接口说明

支付宝出行退款申请。

## 请求参数

参数详情参见规范表 155。

|| 字段 | 类型 | 必填 | 说明 |
||------|------|------|------|
|| orderNo | String | 是 | 原订单号 |
|| cardIssueCode | String | 是 | 卡机构编号，支付宝固定为 0007 |
|| cardNum | String | 是 | 逻辑卡号 |
|| channelAgreementNo | String | 是 | 渠道协议号 |
|| refundAmount | String | 是 | 退款金额，单位分 |
|| refundOrderNo | String | 是 | 退款订单号 |

## 应答参数

参数详情参见规范表 156。

|| 字段 | 类型 | 说明 |
||------|------|------|
|| retCode | string | 返回码 |
|| retMsg | string | 返回消息 |
