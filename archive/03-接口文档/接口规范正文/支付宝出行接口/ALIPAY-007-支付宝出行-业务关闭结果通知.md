# 支付宝出行-业务关闭结果通知

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project]/notify/closeResultForAlipay
```

## 接口说明

支付宝出行销卡结果通知。

## 请求参数

参数详情参见规范表 149。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

## 应答参数

参数详情参见规范表 150。

| 字段 | 类型 | 说明 |
|------|------|------|
| agreementCode | String | 协议号，签约时生成的协议编号 |
| merchantNo | String | 合作方机构编号/商户号 |

