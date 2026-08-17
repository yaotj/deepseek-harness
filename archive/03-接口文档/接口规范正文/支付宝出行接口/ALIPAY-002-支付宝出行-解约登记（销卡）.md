# 支付宝出行-解约登记（销卡）

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project]/channel/terminateContract
```

## 接口说明

请求解约，ITP账期结束后执行解约任务。

## 请求参数

参数详情参见规范表 139。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |

## 应答参数

参数详情参见规范表 140。

| 字段 | 类型 | 说明 |
|------|------|------|
| thirdUserId | String | 第三方用户ID |
| cardId | String | 卡号 |
| transSeq | String | 交易序列号 |
| times | String | 扣减次数（最大值为2） |

