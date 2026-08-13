# 多日票次数扣减通知

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project] /app/receiveCountingTicketTimes
```

## 接口说明

多日票次数扣减通知。

## 请求参数

参数详情参见规范表 129。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

## 应答参数

参数详情参见规范表 130。

| 字段 | 类型 | 说明 |
|------|------|------|
| thirdUserId | String | 用户id |
| channel | String | 支付渠道 |
| cardIssueCode | String | 第三方渠道 |
| regSignSeq | String | 签约流水号 |

