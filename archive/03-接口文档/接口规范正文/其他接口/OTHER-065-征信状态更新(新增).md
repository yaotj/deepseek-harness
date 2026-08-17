# 征信状态更新

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project] /app/ credit
```

## 接口说明

ITP扣费成功后，主动调用支付系统查询征信，征信如果有问题，推送到APP。

## 请求参数

参数详情参见规范表 133。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

## 应答参数

参数详情参见规范表 134。

| 字段 | 类型 | 说明 |
|------|------|------|
| thirdUserId | String | 用户id |
| cardId | String | 卡号 |
| cardType | String | 卡类型 |

