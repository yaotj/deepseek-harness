# 卡片行程通知

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project] /app/ receiveCardTran
```

## 接口说明

用户过闸、补站后，ITP给app推送该笔行程。

## 请求参数

参数详情参见规范表 125。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| inBlack |  | 是否在黑名单
0否
1是 |
| failedCount | Int | 失败次数 |

## 应答参数

参数详情参见规范表 126。

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | String | 用户ID |
| cardType | String | 票卡类型 |
| ticketPrice | String | 票价 |
| orderSource | String | 订单来源 |
| showType | String | 展示类型 |
| orderType | String | 订单类型
1日票
2旅游票 |
| payChannelCode | String | 支付方式
01手动发放
02兑换码 |

