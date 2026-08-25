# Apifox 接口同步报告

> 生成日期: 2026-08-19
> 项目: 青岛ITP (ID: 8379611)
> 文档来源: docs/接口规范文档/ITP与APP接口规范R6_接口清单.md

---

## 同步结果概览

| 操作类型 | 数量 |
|---------|------|
| 更新接口 | 16 个 |
| 新建接口 | 50 个 |
| **总计** | **66 个** |

---

## 按接口系列分类

### IF8A 系列 (APP请求接口) - 25个

| 序号 | 接口编号 | 接口名称 | 路径 | 状态 |
|------|----------|----------|------|------|
| 1 | IF8A-01 | 请求开户 | `/ci/app/requestApplication` | 已更新 |
| 2 | IF8A-02 | 请求同步密钥 | `/ci/app/requestKeyList` | 已更新 |
| 3 | IF8A-03 | 请求行业数据 | `/ci/app/requestIndustryData` | 已更新 |
| 4 | IF8A-04 | 请求用户自助补站 | `/ci/app/requestExcessFare` | 已更新 |
| 5 | IF8A-05 | 请求查询交易记录 | `/ci/app/requestTransList` | 已更新 |
| 6 | IF8A-06 | 请求解约 | `/ci/app/requestTermination` | 已更新 |
| 7 | IF8A-07 | 获取线路代码 | `/ci/app/requestLineCodeList` | 已更新 |
| 8 | IF8A-08 | 获取车站代码 | `/ci/app/requestStationCodeList` | 已更新 |
| 9 | IF8A-09 | 获取购买最多张数 | `/ci/app/requestBuySinlgeTicketMaxNum` | 新建 |
| 10 | IF8A-10 | 计算票价 | `/ci/app/requestTicketPriceByStation` | 已更新 |
| 11 | IF8A-11 | 请求支付 | `/ci/app/requestPaymentInfo` | 已更新 |
| 12 | IF8A-12 | 请求退款 | `/ci/app/requestRefundTicket` | 新建 |
| 13 | IF8A-13 | 退款结果查询 | `/ci/app/requestRefundTicketResult` | 新建 |
| 14 | IF8A-14 | 获取激活取票订单 | `/ci/app/requestPreActiveOrderList` | 新建 |
| 15 | IF8A-15 | 激活取票订单 | `/ci/tvm/requestActiveTicket` | 新建 |
| 16 | IF8A-16 | 请求签约请求信息 | `/ci/tvm/requestSignInfo` | 新建 |
| 17 | IF8A-17 | 获取线路站点代码版本 | `/ci/app/requestLineStationCodeVersion` | 已更新 |
| 18 | IF8A-18 | 支付结果查询 | `/ci/app/requestPayResult` | 新建 |
| 19 | IF8A-19 | BLE通知闸机检票通知 | `/ci/app/notiAgmVerifyResult` | 新建 |
| 20 | IF8A-20 | 请求下单 | `/ci/app/requestOrder` | 新建 |
| 21 | IF8A-21 | 信用能力咨询 | `/ci/app/requestContractAdvisory` | 新建 |
| 22 | IF8A-22 | 签约结果咨询 | `/ci/app/requestContractResult` | 已更新 |
| 23 | IF8A-23 | 请求添加支付通道 | `/ci/app/requestAddPayChannel` | 已更新 |
| 24 | IF8A-24 | 请求设置默认支付通道 | `/ci/app/requestSetDefaultPayChannel` | 已更新 |
| 25 | IF8A-25 | 请求实名（已废弃） | `/ci/app/requestRealNameVerify` | 新建 |

### IF8B 系列 (通知接口) - 8个

| 序号 | 接口编号 | 接口名称 | 路径 | 状态 |
|------|----------|----------|------|------|
| 26 | IF8B-01 | 行业数据推送 | `/ticket/receiveCardDataFromItp` | 新建 |
| 27 | IF8B-02 | 解约结果通知 | `/ticket/receiveTerminationResultFromItp` | 新建 |
| 28 | IF8B-03 | 黑名单结果通知 | `/app/receiveBlackListFromItp` | 新建 |
| 29 | IF8B-04 | 退款结果通知 | `/app/receiveRefundResult` | 新建 |
| 30 | IF8B-05 | 支付结果通知 | `/app/receivePaymentResult` | 新建 |
| 31 | IF8B-06 | 出票成功结果通知 | `/app/receiveTakeTicketResult` | 新建 |
| 32 | IF8B-07 | 出票故障结果通知 | `/app/receiveTakeTicketFaultResult` | 新建 |
| 33 | IF8B-08 | 闸机CA公钥变更通知 | `/app/receiveChangeAgmCaKey` | 新建 |

### 新增功能接口 - 32个

| 序号 | 接口编号 | 接口名称 | 路径 | 状态 |
|------|----------|----------|------|------|
| 34 | IF8A-26 | 请求补款下单 | `/app/requestPayOrder` | 新建 |
| 35 | IF8A-29 | 查询用户上次行程 | `/ci/app/queryUserItinerary` | 已更新 |
| 36 | IF8A-32 | 查询当前是否是单边 | `/app/requestSingleTrans` | 新建 |
| 37 | IF8A-34 | 获取订单详情 | `/ci/app/requestTransDetail` | 已更新 |
| 38 | IF8A-35 | 查询用户账务信息 | `/app/requestUserAccInfo` | 新建 |
| 39 | IF8A-36 | 请求移除签约信息 | `/app/requestAgreeRelease` | 新建 |
| 40 | IF8A-37 | 查询购票订单详情 | `/app/requestPayOrderDetail` | 新建 |
| 41 | IF8A-38 | 验证是否有符合条件的行程 | `/app/requestTransByChannel` | 新建 |
| 42 | IF8A-41 | 查询账单统计 | `/ci/app/requestTransStatistics` | 新建 |
| 43 | IF8A-42 | 用户销户 | `/app/requestCloseAccount` | 新建 |
| 44 | IF8A-43 | 查询月度账单 | `/app/queryTravelBillStatistics` | 新建 |
| 45 | IF8A-60 | 日票下单 | `/ci/app/dailyTicket/requestOrder` | 新建 |
| 46 | IF8A-61 | 日票支付 | `/ci/app/dailyTicket/payment/requestPay` | 新建 |
| 47 | IF8A-62 | 日票支付结果查询 | `/ci/app/dailyTicket/payment/requestPayResult` | 新建 |
| 48 | IF8A-64 | 日票退款 | `/ci/app/dailyTicket/payment/requestRefundTicket` | 新建 |
| 49 | IF8A-65 | 日票取消订单 | `/ci/app/dailyTicket/cancelOrder` | 新建 |
| 50 | IF8A-67 | 日票激活 | `/ci/app/dailyTicket/updateTicket` | 新建 |
| 51 | IF8A-70 | 请求旅游票下单 | `/app/ticket/requestTravelOrder` | 新建 |
| 52 | IF8A-71 | 通知ACC车票已使用 | `/ci/app/dailyTicket/updateAndNotice` | 新建 |
| 53 | IF8A-72 | 小程序票状态同步 | `/app/ticket/syncOrder` | 新建 |
| 54 | IF8A-73 | 查询黑名单 | `/ci/app/queryBlackList` | 新建 |
| 55 | IF8A-73 | 请求免费下单 | `/app/ticket/requestOrderFree` | 新建 |
| 56 | IF8A-75 | 直接解绑支付方式 | `/app/unbindAgreement` | 新建 |
| 57 | IF8A-76 | 更换手机号 | `/app/changePhone` | 新建 |
| 58 | IF8A-77 | 更换第三方渠道码默认支付方式 | `/ci/app/requestUpdateChannelDefaultContract` | 新建 |
| 59 | IF8D-03 | 获取离线码数据 | `/ci/app/requestNoSignalData` | 新建 |

### 新增通知接口 - 6个

| 序号 | 接口名称 | 路径 | 状态 |
|------|----------|------|------|
| 60 | 卡片行程通知 | `/app/receiveCardTran` | 新建 |
| 61 | 行程订单通知 | `/app/orderPayNotice` | 新建 |
| 62 | 多日票次数扣减通知 | `/app/receiveCountingTicketTimes` | 新建 |
| 63 | 接收签约异常状态通知 | `/app/receiveAgreementException` | 新建 |
| 64 | 征信状态更新 | `/app/credit` | 新建 |
| 65 | 5分钟客流数据更新 | `/receive/passengerFlow` | 新建 |

### 员工码相关 - 1个

| 序号 | 接口名称 | 路径 | 状态 |
|------|----------|------|------|
| 66 | 员工码信息查询 | `/ci/app/employeeCard/query` | 新建 |

---

## Controller 与接口对应关系

| Controller | 接口数量 | 主要接口 |
|-----------|---------|---------|
| AppAccountController | 6 | IF8A-01, IF8A-02, IF8A-23, IF8A-24, IF8A-77, 员工码查询 |
| AppTicketController | 6 | IF8A-04, IF8A-05, IF8A-29, IF8A-34, IF8A-41, IF8A-73 |
| AppDailyTicketController | 8 | IF8A-60, IF8A-61, IF8A-62, IF8A-64, IF8A-65, IF8A-67, IF8A-71 |
| CollectPayController | 8 | IF8A-11, IF8A-12, IF8A-13, IF8A-14, IF8A-15, IF8A-18, IF8A-20 |
| IndustryDataController | 2 | IF8A-03, IF8D-03 |
| AppParaController | 5 | IF8A-07, IF8A-08, IF8A-09, IF8A-10, IF8A-17 |

---

## 公共参数规范

所有接口使用统一的 `CommonFormRequest` 表单参数：

| 字段 | 类型 | 说明 |
|------|------|------|
| providerId | string | 商户编码（01=青岛地铁APP）|
| charset | string | 字符集：UTF-8 |
| format | string | 数据格式：json |
| timestamp | string | 请求时间 YYYYMMDDHHMMSS |
| deviceId | string | 设备编码 |
| signType | string | 签名类型（00/01/02）|
| sign | string | 签名值 |
| bizData | Json | 业务数据（JSON格式）|

---

## 环境变量

| 变量名 | 说明 |
|--------|------|
| fep_app_base_url | fep-app 服务地址 |
| fep_acc_base_url | fep-acc 服务地址 |

---

## Apifox 项目地址

https://apifox.com/a/qd_itp/project-8379611

---

*本报告由 Apifox CLI 自动生成*
