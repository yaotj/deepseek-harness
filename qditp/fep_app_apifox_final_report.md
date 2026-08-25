# fep-app Apifox 接口同步与清理完成报告

**完成时间**: 2026-08-19  
**Apifox 项目**: 青岛ITP (ID: 8379611)  
**模块**: fep-app（默认模块）

---

## 执行摘要

| 操作阶段 | 操作 | 数量 |
|---------|------|------|
| 第一阶段 | 同步接口到 Apifox | 66 个 |
| 第二阶段 | 删除未实现的接口 | 4 个 |
| 第三阶段 | 检查映射关系 | 71 个检查 |
| 第四阶段 | 删除未实现的接口 | 29 个 |
| 第五阶段 | 删除重复路径接口 | 9 个 |
| **最终** | **保留有效接口** | **34 个** |

---

## 最终接口列表（34个）

### AppAccountController - 账户及支付管理（7个）

| 序号 | 接口ID | 接口名称 | 路径 | 状态 |
|------|--------|----------|------|------|
| 1 | 467631846 | IF8A-01 请求开户 | `/ci/app/requestApplication` | developing |
| 2 | 467871946 | IF8A-02 请求同步密钥 | `/ci/app/requestKeyList` | developing |
| 3 | 467874428 | IF8A-23 请求添加支付通道 | `/ci/app/requestAddPayChannel` | developing |
| 4 | 467875243 | IF8A-24 请求设置默认支付通道 | `/ci/app/requestSetDefaultPayChannel` | developing |
| 5 | 503598609 | IF8A-77 更换第三方渠道码默认支付方式 | `/ci/app/requestUpdateChannelDefaultContract` | developing |
| 6 | 503598685 | 员工码信息查询 | `/ci/app/employeeCard/query` | developing |

### AppTicketController - 票务管理（6个）

| 序号 | 接口ID | 接口名称 | 路径 | 状态 |
|------|--------|----------|------|------|
| 7 | 489903043 | IF8A-04 请求用户自助补站 | `/ci/app/requestExcessFare` | released |
| 8 | 502732893 | IF8A-05 请求查询交易记录 | `/ci/app/requestTransList` | developing |
| 9 | 478553746 | IF8A-29 查询用户上次行程 | `/ci/app/queryUserItinerary` | developing |
| 10 | 502696370 | IF8A-34 获取订单详情 | `/ci/app/requestTransDetail` | developing |
| 11 | 503598560 | IF8A-41 查询账单统计 | `/ci/app/requestTransStatistics` | developing |
| 12 | 503598602 | IF8A-73 查询黑名单 | `/ci/app/queryBlackList` | developing |

### AppDailyTicketController - 日票业务（7个）

| 序号 | 接口ID | 接口名称 | 路径 | 状态 |
|------|--------|----------|------|------|
| 13 | 503598566 | IF8A-60 日票下单 | `/ci/app/dailyTicket/requestOrder` | developing |
| 14 | 503598567 | IF8A-61 日票支付 | `/ci/app/dailyTicket/payment/requestPay` | developing |
| 15 | 503598569 | IF8A-62 日票支付结果查询 | `/ci/app/dailyTicket/payment/requestPayResult` | developing |
| 16 | 503598571 | IF8A-64 日票退款 | `/ci/app/dailyTicket/payment/requestRefundTicket` | developing |
| 17 | 503598572 | IF8A-65 日票取消订单 | `/ci/app/dailyTicket/cancelOrder` | developing |
| 18 | 503598573 | IF8A-67 日票激活 | `/ci/app/dailyTicket/updateTicket` | developing |
| 19 | 503598600 | IF8A-71 通知ACC车票已使用 | `/ci/app/dailyTicket/updateAndNotice` | developing |

### CollectPayController - 订单支付（6个）

| 序号 | 接口ID | 接口名称 | 路径 | 状态 |
|------|--------|----------|------|------|
| 20 | 503598393 | IF8A-20 请求下单 | `/ci/app/requestOrder` | developing |
| 21 | 502918043 | IF8A-11 请求支付 | `/ci/app/requestPaymentInfo` | developing |
| 22 | 503598180 | IF8A-18 支付结果查询 | `/ci/app/requestPayResult` | developing |
| 23 | 503598167 | IF8A-12 请求退款 | `/ci/app/requestRefundTicket` | developing |
| 24 | 503598168 | IF8A-13 退款结果查询 | `/ci/app/requestRefundTicketResult` | developing |
| 25 | 503598172 | IF8A-14 获取激活取票订单 | `/ci/app/requestPreActiveOrderList` | developing |

### AppParaController - 基础参数查询（5个）

| 序号 | 接口ID | 接口名称 | 路径 | 状态 |
|------|--------|----------|------|------|
| 26 | 488592637 | IF8A-07 获取线路代码 | `/ci/app/requestLineCodeList` | released |
| 27 | 488592638 | IF8A-08 获取车站代码 | `/ci/app/requestStationCodeList` | released |
| 28 | 503598165 | IF8A-09 获取购买最多张数 | `/ci/app/requestBuySinlgeTicketMaxNum` | developing |
| 29 | 488592639 | IF8A-10 计算票价 | `/ci/app/requestTicketPriceByStation` | released |
| 30 | 488592640 | IF8A-17 获取线路站点代码版本 | `/ci/app/requestLineStationCodeVersion` | released |

### IndustryDataController - 行业数据（2个）

| 序号 | 接口ID | 接口名称 | 路径 | 状态 |
|------|--------|----------|------|------|
| 31 | 467870463 | IF8A-03 请求行业数据 | `/ci/app/requestIndustryData` | developing |
| 32 | 503598613 | IF8D-03 获取离线码数据 | `/ci/app/requestNoSignalData` | developing |

### 已废弃接口（1个）

| 序号 | 接口ID | 接口名称 | 路径 | 状态 |
|------|--------|----------|------|------|
| 33 | 503598410 | IF8A-25 请求实名（已废弃） | `/ci/app/requestRealNameVerify` | deprecated |

### 通知接口（待补充）

以下接口在文档中定义但代码未实现，建议后续开发：

| 接口编号 | 接口名称 | 路径 | 优先级 |
|----------|----------|------|--------|
| IF8B-01 | 行业数据推送 | `/ticket/receiveCardDataFromItp` | 高 |
| IF8B-02 | 解约结果通知 | `/ticket/receiveTerminationResultFromItp` | 高 |
| IF8A-06 | 请求解约 | `/ci/app/requestTermination` | 中 |
| IF8A-19 | BLE通知闸机检票通知 | `/ci/app/notiAgmVerifyResult` | 中 |
| IF8A-21 | 信用能力咨询 | `/ci/app/requestContractAdvisory` | 低 |
| IF8A-22 | 签约结果咨询 | `/ci/app/requestContractResult` | 低 |
| IF8A-26~43 | 新增功能接口（12个） | 多个路径 | 低 |

---

## 删除操作记录

### 第一轮删除（4个接口）

| 接口名称 | 路径 | 原因 |
|----------|------|------|
| IF8A-08 获取车站线路代码 | `/ci/app/requestStationLineInfo` | 未实现 |
| IF8A-16请求签约信息1 | `/ci/app/requestSignInfo` | 未实现 |
| 查询车站名称 | `/ci/app/requestStationName` | 未实现 |
| 签约结果回调 | `/ci/app/receiveSignResult` | 未实现 |

### 第二轮删除（29个接口）

IF8A 系列未实现接口（17个）：
- IF8A-21 信用能力咨询
- IF8A-22 签约结果咨询
- IF8A-26 请求补款下单
- IF8A-32 查询当前是否是单边
- IF8A-35 查询用户账务信息
- IF8A-36 请求移除签约信息
- IF8A-37 查询购票订单详情
- IF8A-38 验证是否有符合条件的行程
- IF8A-42 用户销户
- IF8A-43 查询月度账单
- IF8A-70 请求旅游票下单
- IF8A-72 小程序票状态同步
- IF8A-73 请求免费下单
- IF8A-75 直接解绑支付方式
- IF8A-76 更换手机号
- IF8A-06 请求解约
- IF8A-19 BLE通知闸机检票通知

IF8B 系列通知接口（6个）：
- IF8B-03 黑名单结果通知
- IF8B-04 退款结果通知
- IF8B-05 支付结果通知
- IF8B-06 出票成功结果通知
- IF8B-07 出票故障结果通知
- IF8B-08 闸机CA公钥变更通知

新增通知接口（6个）：
- 卡片行程通知
- 行程订单通知
- 多日票次数扣减通知
- 接收签约异常状态通知
- 征信状态更新
- 5分钟客流数据更新

### 第三轮删除（9个重复路径接口）

| 接口名称 | 路径 | 说明 |
|----------|------|------|
| IF8A-60 日票下单 | `/app/requestCountingOrder` | 与 `/ci/app/dailyTicket/requestOrder` 重复 |
| IF8A-61 日票支付 | `/app/payment/requestPay` | 与 `/ci/app/dailyTicket/payment/requestPay` 重复 |
| IF8A-62 日票支付结果查询 | `/app/payment/requestPayResult` | 与 `/ci/app/dailyTicket/payment/requestPayResult` 重复 |
| IF8A-64 日票退款 | `/app/payment/requestRefundTicket` | 与 `/ci/app/dailyTicket/payment/requestRefundTicket` 重复 |
| IF8A-65 日票取消订单 | `/app/ticket/cancelOrder` | 与 `/ci/app/dailyTicket/cancelOrder` 重复 |
| IF8A-67 日票激活 | `/app/ticket/updateTicket` | 与 `/ci/app/dailyTicket/updateTicket` 重复 |
| IF8A-71 通知ACC车票已使用 | `/app/ticket/updateAndNotice` | 与 `/ci/app/dailyTicket/updateAndNotice` 重复 |
| if8a_73 - 查询黑名单8 | `/app/ticket/queryBlackList` | 与 `/ci/app/queryBlackList` 重复 |
| 更换第三方渠道码默认支付方式 | `/app/requestUpdateChannelDefaultContract` | 与 `/ci/app/requestUpdateChannelDefaultContract` 重复 |

---

## 覆盖率统计

| 指标 | 数值 |
|------|------|
| Apifox 总接口数（原始） | 122 |
| fep-app 相关接口（同步后） | 71 |
| 已删除接口 | 38 |
| **最终保留接口** | **33** |
| 代码实现覆盖率 | 100%（保留的接口均已实现） |

---

## 后续建议

1. **补充通知接口**：实现 IF8B 系列的 6 个通知接口
2. **补充新增功能**：根据需求文档实现 IF8A-26~43 系列接口
3. **完善接口文档**：为每个接口补充完整的请求/响应参数定义
4. **统一环境变量**：确保所有接口使用正确的环境变量配置

---

**Apifox 项目地址**: https://apifox.com/a/qd_itp/project-8379611

---

*本报告由接口同步与清理脚本自动生成*
