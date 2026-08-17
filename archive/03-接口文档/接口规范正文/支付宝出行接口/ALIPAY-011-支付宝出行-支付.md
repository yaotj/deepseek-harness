# 支付宝出行-支付

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project]/api/payment/requestPay
```

## 接口说明

支付宝出行请求扣费。

## 请求参数

参数详情参见规范表 151。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

## 应答参数

参数详情参见规范表 152。

| 字段 | 类型 | 说明 |
|------|------|------|
| thirdUserId | String | 第三方用户ID，格式化后的用户标识 |
| cardType | String | 卡片类型，如：02 |
| msisdn | String | 用户手机号码 |
| extend1 | String | 扩展字段1 |
| extend2 | String | 扩展字段2（可选） |
| cardIssueCode | String | 发卡渠道代码 0007支付宝出行 |

