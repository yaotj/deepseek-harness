# 5分钟客流数据更新

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project] /receive/passengerFlow
```

## 接口说明

ITP每五分钟向APP推送每个车站五分钟内的进站客流。

## 请求参数

参数详情参见规范表 135。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| exitData | String | 出站行业数据 |
| entryData | String | 进站数据 |
| channel | String | ITP的默认支付渠道 |

## 应答参数

参数详情参见规范表 134。

| 字段 | 类型 | 说明 |
|------|------|------|
| thirdUserId | String | 用户id |
| cardId | String | 卡号 |
| cardType | String | 卡类型 |

