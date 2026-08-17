# 行程订单通知

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project] /app/ orderPayNotice
```

## 接口说明

用户行程订单产生支付结果后（无论失败还是成功），ITP将行程订单数据推送到APP。

## 请求参数

参数详情参见规范表 127。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| orderNo | String | 单号 |
| subOrders | String | 每一张票的子单号 |

## 应答参数

参数详情参见规范表 128。

| 字段 | 类型 | 说明 |
|------|------|------|
| newPhone | String | 新手机号 |
| thirdUserId | String | 用户id |

