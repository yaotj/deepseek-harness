# 接收签约异常状态通知

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project] /app/receiveAgreementException
```

## 接口说明

ITP扣费失败后，判断扣费失败原因，如果是因为签约异常，将异常信息通知到APP。

## 请求参数

参数详情参见规范表 131。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

## 应答参数

参数详情参见规范表 132。

| 字段 | 类型 | 说明 |
|------|------|------|
| thirdUserId | String | 用户id |
| paymentVendor | String | 支付渠道 |
| requestSignSeq | String | 签约流水号 |

