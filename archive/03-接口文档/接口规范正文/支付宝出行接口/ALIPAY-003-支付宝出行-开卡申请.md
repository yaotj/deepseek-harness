# 支付宝出行-开卡申请

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project] /channel/requestApplication
```

## 接口说明

支付宝出行申请开卡

## 请求参数

参数详情参见规范表 141。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

## 应答参数

参数详情参见规范表 142。

| 字段 | 类型 | 说明 |
|------|------|------|
| thirdUserId | String | 第三方用户ID |
| cardId | String | 卡号 |
| transSeq | String | 交易序列号 |
| times | String | 扣减次数（最大值为2） |

