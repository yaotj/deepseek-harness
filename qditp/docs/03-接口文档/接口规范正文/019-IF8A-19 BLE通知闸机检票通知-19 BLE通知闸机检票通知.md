# IF8A-19 BLE通知闸机检票通知

> 来源：`10-业务规范与标准/原始归档/青岛地铁-ITP与APP接口规范R6.docx`

## 接口地址

```
http//:[ip]:[port]/[project]/ci/app/notiAgmVerifyResult
```

## 接口说明

闸机验证二维码合法，蓝牙会写到APP，APP通知ITP验签结果。

## 请求参数

参数详情参见规范表 41。

| 字段 | 类型 | 说明 |
|------|------|------|
| retCode | string | 返回码 |
| retMsg | string | 返回消息 |
| requestStartSdkInfo | string | 调用SDK所需的请求参数 |

## 应答参数

参数详情参见规范表 42。

| 字段 | 类型 | 说明 |
|------|------|------|
|  |  |  |

