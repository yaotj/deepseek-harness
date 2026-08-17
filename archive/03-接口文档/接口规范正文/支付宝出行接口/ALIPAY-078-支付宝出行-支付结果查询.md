# 支付宝出行-支付结果查询

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project]/api/payment/payQuery
```

## 接口说明

支付宝出行查询扣费结果。

## 请求参数

参数详情参见规范表 153。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| cardId | String | 卡片ID/逻辑卡号，开卡成功后返回 |

## 应答参数

参数详情参见规范表 154。

| 字段 | 类型 | 说明 |
|------|------|------|
| thirdUserId | String | 第三方用户ID |
| cardId | String | 卡片ID/逻辑卡号 |
| cardType | String | 卡片类型 |

