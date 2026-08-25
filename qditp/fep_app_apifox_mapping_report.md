# fep-app Apifox 接口映射检查报告

**检查日期**: 2026-08-19  
**Apifox 项目**: 青岛ITP (ID: 8379611)  
**模块**: fep-app（默认模块）  
**总接口数**: 71

---

## 执行摘要

| 类别 | 数量 | 占比 |
|------|------|------|
| ✓ 已匹配（代码已实现） | 34 | 47.9% |
| ○ 已废弃 | 1 | 1.4% |
| ✗ 未匹配（代码未实现） | 36 | 50.7% |

---

## 一、已匹配的接口（代码已实现）- 34个

### 1.1 AppAccountController - 账户及支付管理

| Apifox ID | 接口名称 | 路径 | Controller 方法 | 状态 |
|-----------|----------|------|-----------------|------|
| 467631846 | IF8A-01 请求开户 | `/ci/app/requestApplication` | `requestApplication()` | developing |
| 467871946 | IF8A-02 请求同步密钥 | `/ci/app/requestKeyList` | `requestKeyList()` | developing |
| 467874428 | IF8A-23 请求添加支付通道 | `/ci/app/requestAddPayChannel` | `requestAddPayChannel()` | developing |
| 467875243 | IF8A-24 请求设置默认支付通道 | `/ci/app/requestSetDefaultPayChannel` | `requestSetDefaultPayChannel()` | developing |
| 503598609 | IF8A-77 更换第三方渠道码默认支付方式 | `/ci/app/requestUpdateChannelDefaultContract` | `requestUpdateChannelDefaultContract()` | developing |
| 500996870 | 更换第三方渠道码默认支付方式 | `/app/requestUpdateChannelDefaultContract` | `requestUpdateChannelDefaultContract()` | developing |
| 503598685 | 员工码信息查询 | `/ci/app/employeeCard/query` | `queryEmployeeCard()` | developing |

### 1.2 AppTicketController - 票务管理

| Apifox ID | 接口名称 | 路径 | Controller 方法 | 状态 |
|-----------|----------|------|-----------------|------|
| 489903043 | IF8A-04 请求用户自助补站 | `/ci/app/requestExcessFare` | `requestExcessFare()` | released |
| 502732893 | IF8A-05 请求查询交易记录 | `/ci/app/requestTransList` | `requestTransList()` | developing |
| 478553746 | IF8A-29 查询用户上次行程 | `/ci/app/queryUserItinerary` | `queryUserItinerary()` | developing |
| 502696370 | IF8A-34 获取订单详情 | `/ci/app/requestTransDetail` | `requestTransDetail()` | developing |
| 503598560 | IF8A-41 查询账单统计 | `/ci/app/requestTransStatistics` | `requestTransStatistics()` | developing |
| 503598602 | IF8A-73 查询黑名单 | `/ci/app/queryBlackList` | `queryBlackList()` | developing |
| 467875758 | if8a_73 - 查询黑名单8 | `/app/ticket/queryBlackList` | `queryBlackList()` | developing |

### 1.3 AppDailyTicketController - 日票业务

| Apifox ID | 接口名称 | 路径 | Controller 方法 | 状态 |
|-----------|----------|------|-----------------|------|
| 503598566 | IF8A-60 日票下单 | `/ci/app/dailyTicket/requestOrder` | `requestOrder()` | developing |
| 503598567 | IF8A-61 日票支付 | `/ci/app/dailyTicket/payment/requestPay` | `pay()` | developing |
| 503598569 | IF8A-62 日票支付结果查询 | `/ci/app/dailyTicket/payment/requestPayResult` | `queryPayResult()` | developing |
| 503598571 | IF8A-64 日票退款 | `/ci/app/dailyTicket/payment/requestRefundTicket` | `requestRefund()` | developing |
| 503598572 | IF8A-65 日票取消订单 | `/ci/app/dailyTicket/cancelOrder` | `cancelOrder()` | developing |
| 503598573 | IF8A-67 日票激活 | `/ci/app/dailyTicket/updateTicket` | `activateTicket()` | developing |
| 503598600 | IF8A-71 通知ACC车票已使用 | `/ci/app/dailyTicket/updateAndNotice` | `notifyAccUsed()` | developing |
| 489403210 | IF8A-60 日票下单 | `/app/requestCountingOrder` | `requestOrder()` | released |
| 489403211 | IF8A-61 日票支付 | `/app/payment/requestPay` | `pay()` | released |
| 489504551 | IF8A-62 日票支付结果查询 | `/app/payment/requestPayResult` | `queryPayResult()` | released |
| 489504552 | IF8A-64 日票退款 | `/app/payment/requestRefundTicket` | `requestRefund()` | released |
| 489504553 | IF8A-65 日票取消订单 | `/app/ticket/cancelOrder` | `cancelOrder()` | released |
| 489504554 | IF8A-67 日票激活 | `/app/ticket/updateTicket` | `activateTicket()` | released |
| 489504555 | IF8A-71 通知ACC车票已使用 | `/app/ticket/updateAndNotice` | `notifyAccUsed()` | released |

### 1.4 CollectPayController - 订单支付

| Apifox ID | 接口名称 | 路径 | Controller 方法 | 状态 |
|-----------|----------|------|-----------------|------|
| 503598393 | IF8A-20 请求下单 | `/ci/app/requestOrder` | `requestOrder()` | developing |
| 502918043 | IF8A-11 请求支付 | `/ci/app/requestPaymentInfo` | `requestPaymentInfo()` | developing |
| 503598180 | IF8A-18 支付结果查询 | `/ci/app/requestPayResult` | `requestPayResult()` | developing |
| 503598167 | IF8A-12 请求退款 | `/ci/app/requestRefundTicket` | `requestRefundTicket()` | developing |
| 503598168 | IF8A-13 退款结果查询 | `/ci/app/requestRefundTicketResult` | `requestRefundTicketResult()` | developing |
| 503598172 | IF8A-14 获取激活取票订单 | `/ci/app/requestPreActiveOrderList` | `requestPreActiveOrderList()` | developing |

### 1.5 AppParaController - 基础参数查询

| Apifox ID | 接口名称 | 路径 | Controller 方法 | 状态 |
|-----------|----------|------|-----------------|------|
| 488592637 | IF8A-07 获取线路代码 | `/ci/app/requestLineCodeList` | `requestLineCodeList()` | released |
| 488592638 | IF8A-08 获取车站代码 | `/ci/app/requestStationCodeList` | `requestStationCodeList()` | released |
| 503598165 | IF8A-09 获取购买最多张数 | `/ci/app/requestBuySinlgeTicketMaxNum` | `requestBuySinlgeTicketMaxNum()` | developing |
| 488592639 | IF8A-10 计算票价 | `/ci/app/requestTicketPriceByStation` | `requestTicketPriceByStation()` | released |
| 488592640 | IF8A-17 获取线路站点代码版本 | `/ci/app/requestLineStationCodeVersion` | `requestLineStationCodeVersion()` | released |

### 1.6 IndustryDataController - 行业数据

| Apifox ID | 接口名称 | 路径 | Controller 方法 | 状态 |
|-----------|----------|------|-----------------|------|
| 467870463 | IF8A-03 请求行业数据 | `/ci/app/requestIndustryData` | `requestIndustryData()` | developing |
| 503598613 | IF8D-03 获取离线码数据 | `/ci/app/requestNoSignalData` | `requestNoSignalData()` | developing |

---

## 二、已废弃的接口 - 1个

| Apifox ID | 接口名称 | 路径 | 说明 |
|-----------|----------|------|------|
| 503598410 | IF8A-25 请求实名（已废弃） | `/ci/app/requestRealNameVerify` | 标记为 deprecated |

---

## 三、未匹配的接口（Apifox中有但代码未实现）- 36个

### 3.1 IF8A 系列 - 新增功能接口

| Apifox ID | 接口名称 | 路径 | 说明 |
|-----------|----------|------|------|
| 503598407 | IF8A-21 信用能力咨询 | `/ci/app/requestContractAdvisory` | 代码未实现 |
| 501131604 | IF8A-22 签约结果咨询 | `/ci/app/requestContractResult` | 代码未实现 |
| 503598456 | IF8A-26 请求补款下单 | `/app/requestPayOrder` | 代码未实现 |
| 503598457 | IF8A-32 查询当前是否是单边 | `/app/requestSingleTrans` | 代码未实现 |
| 503598487 | IF8A-35 查询用户账务信息 | `/app/requestUserAccInfo` | 代码未实现 |
| 503598488 | IF8A-36 请求移除签约信息 | `/app/requestAgreeRelease` | 代码未实现 |
| 503598542 | IF8A-37 查询购票订单详情 | `/app/requestPayOrderDetail` | 代码未实现 |
| 503598559 | IF8A-38 验证是否有符合条件的行程 | `/app/requestTransByChannel` | 代码未实现 |
| 503598562 | IF8A-42 用户销户 | `/app/requestCloseAccount` | 代码未实现 |
| 503598564 | IF8A-43 查询月度账单 | `/app/queryTravelBillStatistics` | 代码未实现 |
| 503598598 | IF8A-70 请求旅游票下单 | `/app/ticket/requestTravelOrder` | 代码未实现 |
| 503598601 | IF8A-72 小程序票状态同步 | `/app/ticket/syncOrder` | 代码未实现 |
| 503598605 | IF8A-73 请求免费下单 | `/app/ticket/requestOrderFree` | 代码未实现 |
| 503598610 | IF8A-75 直接解绑支付方式 | `/app/unbindAgreement` | 代码未实现 |
| 503598607 | IF8A-76 更换手机号 | `/app/changePhone` | 代码未实现 |
| 467868333 | IF8A-06 请求解约 | `/ci/app/requestTermination` | 代码未实现 |
| 503598390 | IF8A-19 BLE通知闸机检票通知 | `/ci/app/notiAgmVerifyResult` | 代码未实现 |

### 3.2 IF8B 系列 - 通知接口（全部未实现）

| Apifox ID | 接口名称 | 路径 | 说明 |
|-----------|----------|------|------|
| 503598414 | IF8B-03 黑名单结果通知 | `/app/receiveBlackListFromItp` | 代码未实现 |
| 503598415 | IF8B-04 退款结果通知 | `/app/receiveRefundResult` | 代码未实现 |
| 503598416 | IF8B-05 支付结果通知 | `/app/receivePaymentResult` | 代码未实现 |
| 503598444 | IF8B-06 出票成功结果通知 | `/app/receiveTakeTicketResult` | 代码未实现 |
| 503598446 | IF8B-07 出票故障结果通知 | `/app/receiveTakeTicketFaultResult` | 代码未实现 |
| 503598450 | IF8B-08 闸机CA公钥变更通知 | `/app/receiveChangeAgmCaKey` | 代码未实现 |

### 3.3 新增通知接口（全部未实现）

| Apifox ID | 接口名称 | 路径 | 说明 |
|-----------|----------|------|------|
| 503598663 | 卡片行程通知 | `/app/receiveCardTran` | 代码未实现 |
| 503598665 | 行程订单通知 | `/app/orderPayNotice` | 代码未实现 |
| 503598667 | 多日票次数扣减通知 | `/app/receiveCountingTicketTimes` | 代码未实现 |
| 503598669 | 接收签约异常状态通知 | `/app/receiveAgreementException` | 代码未实现 |
| 503598670 | 征信状态更新 | `/app/credit` | 代码未实现 |
| 503598682 | 5分钟客流数据更新 | `/receive/passengerFlow` | 代码未实现 |

---

## 四、重复路径分析

以下接口存在多条路径记录（同一功能）：

| 功能 | Apifox 路径1 | Apifox 路径2 | Controller 支持路径 |
|------|-------------|-------------|-------------------|
| IF8A-60 日票下单 | `/ci/app/dailyTicket/requestOrder` | `/app/requestCountingOrder` | 两者都支持 |
| IF8A-61 日票支付 | `/ci/app/dailyTicket/payment/requestPay` | `/app/payment/requestPay` | 两者都支持 |
| IF8A-62 日票支付结果 | `/ci/app/dailyTicket/payment/requestPayResult` | `/app/payment/requestPayResult` | 两者都支持 |
| IF8A-64 日票退款 | `/ci/app/dailyTicket/payment/requestRefundTicket` | `/app/payment/requestRefundTicket` | 两者都支持 |
| IF8A-65 日票取消订单 | `/ci/app/dailyTicket/cancelOrder` | `/app/ticket/cancelOrder` | 两者都支持 |
| IF8A-67 日票激活 | `/ci/app/dailyTicket/updateTicket` | `/app/ticket/updateTicket` | 两者都支持 |
| IF8A-71 通知ACC车票已使用 | `/ci/app/dailyTicket/updateAndNotice` | `/app/ticket/updateAndNotice` | 两者都支持 |
| IF8A-73 查询黑名单 | `/ci/app/queryBlackList` | `/app/ticket/queryBlackList` | 两者都支持 |
| IF8A-77 更换支付方式 | `/ci/app/requestUpdateChannelDefaultContract` | `/app/requestUpdateChannelDefaultContract` | 两者都支持 |

---

## 五、建议操作

### 5.1 需要删除的接口（代码未实现且在文档中无明确定义）

以下接口在 Apifox 中存在但代码未实现，建议删除：

| 序号 | 接口名称 | 路径 | Apifox ID |
|------|----------|------|-----------|
| 1 | IF8A-21 信用能力咨询 | `/ci/app/requestContractAdvisory` | 503598407 |
| 2 | IF8A-22 签约结果咨询 | `/ci/app/requestContractResult` | 501131604 |
| 3 | IF8A-26 请求补款下单 | `/app/requestPayOrder` | 503598456 |
| 4 | IF8A-32 查询当前是否是单边 | `/app/requestSingleTrans` | 503598457 |
| 5 | IF8A-35 查询用户账务信息 | `/app/requestUserAccInfo` | 503598487 |
| 6 | IF8A-36 请求移除签约信息 | `/app/requestAgreeRelease` | 503598488 |
| 7 | IF8A-37 查询购票订单详情 | `/app/requestPayOrderDetail` | 503598542 |
| 8 | IF8A-38 验证是否有符合条件的行程 | `/app/requestTransByChannel` | 503598559 |
| 9 | IF8A-42 用户销户 | `/app/requestCloseAccount` | 503598562 |
| 10 | IF8A-43 查询月度账单 | `/app/queryTravelBillStatistics` | 503598564 |
| 11 | IF8A-70 请求旅游票下单 | `/app/ticket/requestTravelOrder` | 503598598 |
| 12 | IF8A-72 小程序票状态同步 | `/app/ticket/syncOrder` | 503598601 |
| 13 | IF8A-73 请求免费下单 | `/app/ticket/requestOrderFree` | 503598605 |
| 14 | IF8A-75 直接解绑支付方式 | `/app/unbindAgreement` | 503598610 |
| 15 | IF8A-76 更换手机号 | `/app/changePhone` | 503598607 |
| 16 | IF8A-06 请求解约 | `/ci/app/requestTermination` | 467868333 |
| 17 | IF8A-19 BLE通知闸机检票通知 | `/ci/app/notiAgmVerifyResult` | 503598390 |
| 18 | IF8B-03 黑名单结果通知 | `/app/receiveBlackListFromItp` | 503598414 |
| 19 | IF8B-04 退款结果通知 | `/app/receiveRefundResult` | 503598415 |
| 20 | IF8B-05 支付结果通知 | `/app/receivePaymentResult` | 503598416 |
| 21 | IF8B-06 出票成功结果通知 | `/app/receiveTakeTicketResult` | 503598444 |
| 22 | IF8B-07 出票故障结果通知 | `/app/receiveTakeTicketFaultResult` | 503598446 |
| 23 | IF8B-08 闸机CA公钥变更通知 | `/app/receiveChangeAgmCaKey` | 503598450 |
| 24 | 卡片行程通知 | `/app/receiveCardTran` | 503598663 |
| 25 | 行程订单通知 | `/app/orderPayNotice` | 503598665 |
| 26 | 多日票次数扣减通知 | `/app/receiveCountingTicketTimes` | 503598667 |
| 27 | 接收签约异常状态通知 | `/app/receiveAgreementException` | 503598669 |
| 28 | 征信状态更新 | `/app/credit` | 503598670 |
| 29 | 5分钟客流数据更新 | `/receive/passengerFlow` | 503598682 |

**共 29 个接口需要删除**

### 5.2 需要保持的接口（代码已实现）- 34个

这些接口在 Apifox 和代码中都存在，建议保持：

- **IF8A-01~05, IF8A-07~08, IF8A-10, IF8A-17**: 基础查询类接口
- **IF8A-09**: 获取购买最多张数
- **IF8A-11~14, IF8A-18, IF8A-20**: 支付订单类接口
- **IF8A-23~24**: 支付通道管理类接口
- **IF8A-25**: 已废弃的实名接口
- **IF8A-29, IF8A-34, IF8A-41**: 交易查询类接口
- **IF8A-60~67, IF8A-71**: 日票业务接口
- **IF8A-73 查询黑名单**: 黑名单查询接口
- **IF8A-77**: 更换支付方式接口
- **IF8D-03**: 离线码数据接口
- **员工码查询**: 员工码信息查询接口

### 5.3 路径重复问题

以下接口存在 Apifox 路径与 Controller 路径不完全一致的问题，建议统一：

| 接口 | Apifox 路径 | Controller 实际路径 | 建议 |
|------|-------------|-------------------|------|
| IF8A-60 | `/app/requestCountingOrder` | `/ci/app/dailyTicket/requestOrder` | 删除 Apifox 中的 `/app/requestCountingOrder` |
| IF8A-61 | `/app/payment/requestPay` | `/ci/app/dailyTicket/payment/requestPay` | 删除 Apifox 中的 `/app/payment/requestPay` |
| IF8A-62 | `/app/payment/requestPayResult` | `/ci/app/dailyTicket/payment/requestPayResult` | 删除 Apifox 中的 `/app/payment/requestPayResult` |
| IF8A-64 | `/app/payment/requestRefundTicket` | `/ci/app/dailyTicket/payment/requestRefundTicket` | 删除 Apifox 中的 `/app/payment/requestRefundTicket` |
| IF8A-65 | `/app/ticket/cancelOrder` | `/ci/app/dailyTicket/cancelOrder` | 删除 Apifox 中的 `/app/ticket/cancelOrder` |
| IF8A-67 | `/app/ticket/updateTicket` | `/ci/app/dailyTicket/updateTicket` | 删除 Apifox 中的 `/app/ticket/updateTicket` |
| IF8A-71 | `/app/ticket/updateAndNotice` | `/ci/app/dailyTicket/updateAndNotice` | 删除 Apifox 中的 `/app/ticket/updateAndNotice` |
| IF8A-73 | `/app/ticket/queryBlackList` | `/ci/app/queryBlackList` | 删除 Apifox 中的 `/app/ticket/queryBlackList` |
| IF8A-77 | `/app/requestUpdateChannelDefaultContract` | `/ci/app/requestUpdateChannelDefaultContract` | 删除 Apifox 中的 `/app/requestUpdateChannelDefaultContract` |

---

## 六、覆盖率统计

| 类别 | 数量 |
|------|------|
| Apifox 总接口数 | 71 |
| 代码已实现 | 34 (47.9%) |
| 代码未实现 | 29 (40.8%) |
| 已废弃 | 1 (1.4%) |
| 路径重复 | 9 (12.7%) |

---

## 七、后续建议

1. **删除未实现的接口**：删除 29 个代码未实现的接口
2. **清理重复路径**：删除 9 组重复路径中多余的记录
3. **补充缺失接口**：根据实际开发进度，补充实现缺失的核心接口
4. **统一路径格式**：确保 Apifox 路径与 Controller 实际路径一致

---

*本报告由接口映射检查脚本自动生成*
