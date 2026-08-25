# fep-app-server 接口与 Apifox 青岛ITP 映射关系

**项目ID**: 8379611 (青岛itp)  
**默认模块ID**: 8242120 (fep-app)  
**服务路径前缀**: /ci/app 或 /app

---

## 一、Controller 概览

| Controller | 职责 | 接口数 |
|------------|------|--------|
| [AppAccountController.java](file:///Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppAccountController.java) | 账户及支付管理 | 6 |
| [AppParaController.java](file:///Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppParaController.java) | 基础参数查询 | 5 |
| [AppTicketController.java](file:///Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppTicketController.java) | 票务管理 | 6 |
| [AppDailyTicketController.java](file:///Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppDailyTicketController.java) | 日票业务 | 8 |
| [IndustryDataController.java](file:///Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/IndustryDataController.java) | 行业数据 | 2 |
| [CollectPayController.java](file:///Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/CollectPayController.java) | 订单支付 | 9 |
| [BaseAppController.java](file:///Users/tuanjie/workspace/company/chinasofti/qd/qditp/fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/BaseAppController.java) | 公共基类 | - |

---

## 二、接口详细映射

### 2.1 AppAccountController - 账户及支付管理

| Apifox 接口ID | Apifox 名称 | Apifox 路径 | 方法 | Controller 方法 | IF编号 | 状态 |
|---------------|-------------|-------------|------|-----------------|--------|------|
| 467631846 | IF8A-01 请求开户2 | /ci/app/requestApplication | POST | `requestApplication()` | IF8A-01 | developing |
| 467871946 | IF8A-02 请求同步密钥5 | /ci/app/requestKeyList | POST | `requestKeyList()` | IF8A-02 | developing |
| 467874428 | IF8A-23 请求添加支付通道3 | /ci/app/requestAddPayChannel | POST | `requestAddPayChannel()` | IF8A-23 | developing |
| 467875243 | IF8A-24 请求设置默认支付通道4 | /ci/app/requestSetDefaultPayChannel | POST | `requestSetDefaultPayChannel()` | IF8A-24 | developing |
| 500996870 | 更换第三方渠道码默认支付方式 | /app/requestUpdateChannelDefaultContract | POST | `requestUpdateChannelDefaultContract()` | IF8A-77 | developing |
| 500570022 | APP - 员工码信息查询 | {{fep_app_base_url}}/ci/app/employee_card/query | POST | `queryEmployeeCard()` | - | released |

---

### 2.2 AppParaController - 基础参数查询

| Apifox 接口ID | Apifox 名称 | Apifox 路径 | 方法 | Controller 方法 | IF编号 | 状态 |
|---------------|-------------|-------------|------|-----------------|--------|------|
| 488592637 | IF8A-07 获取线路代码 | /ci/app/requestLineCodeList | POST | `requestLineCodeList()` | IF8A-07 | released |
| 488592638 | IF8A-08 获取车站代码 | /ci/app/requestStationCodeList | POST | `requestStationCodeList()` | IF8A-08 | released |
| 493171224 | IF8A-08 获取车站线路代码 | /ci/app/requestStationLineInfo | POST | - | IF8A-08 | released |
| 488592639 | IF8A-10 计算票价 | /ci/app/requestTicketPriceByStation | POST | `requestTicketPriceByStation()` | IF8A-10 | released |
| 488592640 | IF8A-17 获取线路站点代码版本 | /ci/app/requestLineStationCodeVersion | POST | `requestLineStationCodeVersion()` | IF8A-17 | released |

---

### 2.3 AppTicketController - 票务管理

| Apifox 接口ID | Apifox 名称 | Apifox 路径 | 方法 | Controller 方法 | IF编号 | 状态 |
|---------------|-------------|-------------|------|-----------------|--------|------|
| 489903043 | IF8A-04 请求自助补站 | /ci/app/requestExcessFare | POST | `requestExcessFare()` | IF8A-04 | released |
| 478553746 | 查上次行程 | /ci/app/queryUserItinerary | POST | `queryUserItinerary()` | IF8A-29 | developing |
| 467875758 | if8a_73 - 查询黑名单8 | /app/ticket/queryBlackList | POST | `queryBlackList()` | IF8A-73 | developing |
| 502732893 | IF8A-05请求查询交易记录 | /ci/app/requestTransList | POST | `requestTransList()` | IF8A-05 | developing |
| 502696370 | IF8A-34 获取订单详情 | /ci/app/requestTransDetail | POST | `requestTransDetail()` | IF8A-34 | developing |
| - | IF8A-41 查询账单统计 | /ci/app/requestTransStatistics | POST | `requestTransStatistics()` | IF8A-41 | - |

---

### 2.4 AppDailyTicketController - 日票业务

| Apifox 接口ID | Apifox 名称 | Apifox 路径 | 方法 | Controller 方法 | IF编号 | 状态 |
|---------------|-------------|-------------|------|-----------------|--------|------|
| 489403210 | IF8A-60 日票下单 | /app/requestCountingOrder | POST | `requestOrder()` | IF8A-60 | released |
| 489403211 | IF8A-61 日票支付 | /app/payment/requestPay | POST | `pay()` | IF8A-61 | released |
| 489504551 | IF8A-62 日票支付结果查询 | /app/payment/requestPayResult | POST | `queryPayResult()` | IF8A-62 | released |
| 489504552 | IF8A-64 日票退款 | /app/payment/requestRefundTicket | POST | `requestRefund()` | IF8A-64 | released |
| 489504553 | IF8A-65 日票取消订单 | /app/ticket/cancelOrder | POST | `cancelOrder()` | IF8A-65 | released |
| 489504554 | IF8A-67 日票激活 | /app/ticket/updateTicket | POST | `activateTicket()` | IF8A-67 | released |
| 489504555 | IF8A-71 通知 ACC 车票已使用 | /app/ticket/updateAndNotice | POST | `notifyAccUsed()` | IF8A-71 | released |

---

### 2.5 IndustryDataController - 行业数据

| Apifox 接口ID | Apifox 名称 | Apifox 路径 | 方法 | Controller 方法 | IF编号 | 状态 |
|---------------|-------------|-------------|------|-----------------|--------|------|
| 467870463 | IF8A-03 请求行业数据-拉码6 | /ci/app/requestIndustryData | POST | `requestIndustryData()` | IF8A-03 | developing |
| 480696776 | 支付宝出行-获取行业数据 | /memberContract/channel/requestIndustryData | POST | - | IF8A-03 | released |
| 480696774 | 支付宝出行-查询乘车记录列表 | /channel/findTravelList | POST | - | - | released |
| 480696775 | ALIPAY-006 查询乘车记录详情 | /channel/findTravelDetail | POST | - | - | released |

---

### 2.6 CollectPayController - 订单支付（透传到 collect-pay-server）

| Apifox 接口ID | Apifox 名称 | Apifox 路径 | 方法 | Controller 方法 | IF编号 | 状态 |
|---------------|-------------|-------------|------|-----------------|--------|------|
| 502918043 | 3.11 IF8A-11 请求支付 | /ci/app/requestPaymentInfo | POST | `requestPaymentInfo()` | IF8A-11 | developing |
| - | IF8A-20 请求下单 | /ci/app/requestOrder | POST | `requestOrder()` | IF8A-20 | - |
| - | IF8A-18 支付结果查询 | /ci/app/requestPayResult | POST | `requestPayResult()` | IF8A-18 | - |
| - | 请求退款 | /ci/app/requestRefundTicket | POST | `requestRefundTicket()` | - | - |
| - | 退款结果查询 | /ci/app/requestRefundTicketResult | POST | `requestRefundTicketResult()` | - | - |
| - | 查询激活订单列表 | /ci/app/requestPreActiveOrderList | POST | `requestPreActiveOrderList()` | - | - |
| - | TVM激活请求 | /ci/app/requestActiveTicket | POST | `requestActiveTicket()` | - | - |
| - | 接收退款结果通知 | /ci/app/receiveRefundResult | POST | `receiveRefundResult()` | - | - |

---

## 三、路径映射说明

所有接口均支持两条路径：
- `/ci/app/...` - CI/集成路径
- `/app/...` - 通用路径

例如：
```java
@PostMapping({"/ci/app/requestApplication", "/app/requestApplication"})
```

---

## 四、公共请求模型

### CommonFormRequest 字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| providerId | String | 否 | 服务商ID，示例: "01" |
| charset | String | 否 | 字符集，示例: "UTF-8" |
| format | String | 否 | 数据格式，示例: "json" |
| timestamp | String | 否 | 时间戳，示例: "202605251452" |
| deviceId | String | 否 | 设备ID，示例: 10位数字 |
| signType | String | 否 | 签名类型，示例: "00" |
| sign | String | 否 | 签名值 |
| bizData | String | 否 | 业务数据（JSON字符串） |

---

## 五、环境变量

| 变量名 | 环境 | 值 |
|--------|------|-----|
| fep_app_base_url | 开发环境 | http://127.0.0.1:9103 |
| fep_app_base_url | 测试环境 | http://172.20.211.200:48000/fep-app |
| fep_app_base_url | 正式环境 | http://58.56.166.170:48000/fep-app |
| fep_acc_base_url | 正式环境 | http://58.56.166.170:48000 |

---

## 六、状态统计

| 状态 | Apifox | 代码实现 |
|------|--------|----------|
| released | 58个 | 约40个 |
| developing | 14个 | 约14个 |
| 有代码无Apifox | - | 约7个（CollectPay部分接口） |

---

## 七、注意事项

1. **验签**: 所有接口使用 `application/x-www-form-urlencoded` 格式，需传递 sign 参数
2. **bizData**: 业务参数统一封装在 bizData JSON 字符串中
3. **日票回调**: `receivePayNotify` 使用 `@RequestBody String` 接收原始报文，不走 CommonFormRequest
4. **员工码**: 路径使用环境变量 `{{fep_app_base_url}}` 和 `{{fep_acc_base_url}}`
5. **路径差异**: Apifox 中日票相关接口路径为 `/app/...`，代码中同时支持 `/ci/app/...`
