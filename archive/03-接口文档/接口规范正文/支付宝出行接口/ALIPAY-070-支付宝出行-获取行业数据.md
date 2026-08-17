# 支付宝出行-获取行业数据

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project] /memberContract/channel/requestIndustryData
```

## 接口说明

获取支付宝出行行业数据。

## 请求参数

参数详情参见规范表 143。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

## 应答参数

参数详情参见规范表 144。

| 字段 | 类型 | 说明 |
|------|------|------|
| thirdUserId | String | 第三方用户ID |
| payChannelCode | String | 支付渠道 |

