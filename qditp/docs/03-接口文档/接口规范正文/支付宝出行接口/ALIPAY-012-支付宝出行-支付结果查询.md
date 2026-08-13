# 支付宝出行-支付结果查询

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project]/api/payment/payQuery
```

## 接口说明

web管理控制台查询支付宝出行扣费结果。

## 请求参数

参数详情参见规范表 153。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| providerId | string | 否 | 服务提供商编码 |
| charset | string | 否 | 字符集，默认 UTF-8 |
| format | string | 否 | 报文格式，默认 json |
| timestamp | string | 否 | 时间戳 |
| deviceId | string | 否 | 设备编码 |
| signType | string | 否 | 签名类型 |
| sign | string | 否 | 签名字段 |
| bizData | string | 是 | 业务参数 JSON 字符串 |

### bizData 参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| orderNo | string | 是 | 订单号 |

## 应答参数

参数详情参见规范表 154。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| outTradeNo | string | 商户订单号 |
| tradeStatus | string | 交易状态 |
| tradeNo | string | 交易号 |
| paymentTime | string | 支付时间 |
| totalAmount | string | 总金额 |
| tradeDesc | string | 交易描述 |

## 业务流程

1. web管理控制台调用 `/api/payment/payQuery`。
2. `FepAlipayTripPaymentController` 接收请求，解析 `bizData`。
3. 调用 `AlipayTripServiceImpl.payQuery`。
4. **RPC 调用 `alipay-pay-sign-server` 的 `AlipayPaySignServiceImpl.payQuery`**。
5. 查询本地 `ALIPAY_PAY_LOG` 支付记录。
6. 调用 `payCenterClient.payQuery` 查询支付中心。
7. 解析支付中心响应，映射交易状态、交易号、支付时间、总金额。
8. 返回支付结果。

## 实现链路

```
web管理控制台
    ↓ POST /api/payment/payQuery
FepAlipayTripPaymentController
    ↓
AlipayTripServiceImpl.payQuery
    ↓
AlipayPaySignServiceImpl.payQuery
    ↓ 查询 ALIPAY_PAY_LOG
    ↓ 调用 payCenterClient.payQuery(bizDataMap)
PayCenterClient.payQuery
    ↓ 最终调用支付宝提供的 payQuery
```

## 错误码

| 错误码 | 说明 |
|--------|------|
| 0000 | 成功 |
| 1001 | 无效的参数 |
| 9999 | 系统内部错误 |
