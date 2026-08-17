# 青岛地铁ITP与APP接口规范R6 - 接口清单

> 文档来源：青岛地铁-ITP与APP接口规范R6.docx  
> 生成日期：2026-08-16  
> 总计接口数：80个

---

## 一、接口总览

### 1.1 IF8A系列接口（APP请求接口）- 25个

| 序号 | 接口编号 | 接口名称 | 接口路径 |
|------|----------|----------|----------|
| 1 | IF8A-01 | 请求开户 | `/ci/app/requestApplication` |
| 2 | IF8A-02 | 请求同步密钥 | `/ci/app/requestKeyList` |
| 3 | IF8A-03 | 请求行业数据 | `/ci/app/requestIndustryData` |
| 4 | IF8A-04 | 请求用户自助补站 | `/ci/app/requestExcessFare` |
| 5 | IF8A-05 | 请求查询交易记录 | `/ci/app/requestTransList` |
| 6 | IF8A-06 | 请求解约 | `/ci/app/requestTermination` |
| 7 | IF8A-07 | 获取线路代码 | `/ci/app/requestLineCodeList` |
| 8 | IF8A-08 | 获取车站代码 | `/ci/app/requestStationCodeList` |
| 9 | IF8A-09 | 获取购买最多张数 | `/ci/app/requestBuySinlgeTicketMaxNum` |
| 10 | IF8A-10 | 计算票价 | `/ci/app/requestTicketPriceByStation` |
| 11 | IF8A-11 | 请求支付 | `/ci/app/requestPaymentInfo` |
| 12 | IF8A-12 | 请求退款 | `/ci/app/requestRefundTicket` |
| 13 | IF8A-13 | 退款结果查询 | `/ci/app/requestRefundTicketResult` |
| 14 | IF8A-14 | 获取激活取票订单 | `/ci/app/requestPreActiveOrderList` |
| 15 | IF8A-15 | 激活取票订单 | `/ci/tvm/requestActiveTicket` |
| 16 | IF8A-16 | 请求签约请求信息 | `/ci/tvm/requestSignInfo` |
| 17 | IF8A-17 | 获取线路站点代码版本 | `/ci/app/requestLineStationCodeVersion` |
| 18 | IF8A-18 | 支付结果查询 | `/ci/app/requestPayResult` |
| 19 | IF8A-19 | BLE通知闸机检票通知 | `/ci/app/notiAgmVerifyResult` |
| 20 | IF8A-20 | 请求下单 | `/ci/app/requestOrder` |
| 21 | IF8A-21 | 信用能力咨询 | `/ci/app/requestContractAdvisory` |
| 22 | IF8A-22 | 签约结果咨询 | `/ci/app/requestContractResult` |
| 23 | IF8A-23 | 请求添加支付通道 | `/ci/app/requestAddPayChannel` |
| 24 | IF8A-24 | 请求设置默认支付通道 | `/ci/app/requestSetDefaultPayChannel` |
| 25 | IF8A-25 | 请求实名（已废弃） | `/ci/app/requestRealNameVerify` |

### 1.2 IF8B系列接口（通知接口）- 8个

| 序号 | 接口编号 | 接口名称 | 接口路径 |
|------|----------|----------|----------|
| 26 | IF8B-01 | 行业数据推送 | `/ticket/receiveCardDataFromItp` |
| 27 | IF8B-02 | 解约结果通知 | `/ticket/receiveTerminationResultFromItp` |
| 28 | IF8B-03 | 黑名单结果通知 | `/app/receiveBlackListFromItp` |
| 29 | IF8B-04 | 退款结果通知 | `/app/receiveRefundResult` |
| 30 | IF8B-05 | 支付结果通知 | `/app/receivePaymentResult` |
| 31 | IF8B-06 | 出票成功结果通知 | `/app/receiveTakeTicketResult` |
| 32 | IF8B-07 | 出票故障结果通知 | `/app/receiveTakeTicketFaultResult` |
| 33 | IF8B-08 | 闸机CA公钥变更通知 | `/app/receiveChangeAgmCaKey` |

### 1.3 新增接口（IF8A-26 ~ IF8A-77）- 47个

| 序号 | 接口编号 | 接口名称 | 接口路径 |
|------|----------|----------|----------|
| 34 | IF8A-26 | 请求补款下单 | `/app/requestPayOrder` |
| 35 | IF8A-29 | 查询用户上次行程 | `/app/queryUserItinerary` |
| 36 | IF8A-32 | 查询当前是否是单边 | `/app/requestSingleTrans` |
| 37 | IF8A-34 | 获取订单详情 | `/app/requestTransDetail` |
| 38 | IF8A-35 | 查询用户账务信息 | `/app/requestUserAccInfo` |
| 39 | IF8A-36 | 请求移除签约信息 | `/app/requestAgreeRelease` |
| 40 | IF8A-37 | 查询购票订单详情 | `/app/requestPayOrderDetail` |
| 41 | IF8A-38 | 验证是否有符合条件的行程 | `/app/requestTransByChannel` |
| 42 | IF8A-41 | 查询账单统计 | `/app/requestTransStatistics` |
| 43 | IF8A-42 | 用户销户 | `/app/requestCloseAccount` |
| 44 | IF8A-43 | 查询月度账单 | `/app/queryTravelBillStatistics` |
| 45 | IF8A-60 | 日票下单 | `/app/requestCountingOrder` |
| 46 | IF8A-61 | 日票支付 | `/app/payment/requestPay` |
| 47 | IF8A-62 | 日票支付结果查询 | `/app/payment/requestPayResult` |
| 48 | IF8A-64 | 日票退款 | `/app/payment/requestRefundTicket` |
| 49 | IF8A-65 | 日票取消订单 | `/app/ticket/cancelOrder` |
| 50 | IF8A-67 | 日票激活 | `/app/ticket/updateTicket` |
| 51 | IF8A-70 | 请求旅游票下单 | `/app/ticket/requestTravelOrder` |
| 52 | IF8A-71 | 通知ACC车票已使用 | `/app/ticket/updateAndNotice` |
| 53 | IF8A-72 | 小程序票状态同步 | `/app/ticket/syncOrder` |
| 54 | IF8A-73 | 查询黑名单 | `/app/ticket/queryBlackList` |
| 55 | IF8A-73 | 请求免费下单 | `/app/ticket/requestOrderFree` |
| 56 | IF8A-76 | 更换手机号 | `/app/changePhone` |
| 57 | IF8A-77 | 更换第三方渠道码默认支付方式 | `/app/requestUpdateChannelDefaultContract` |
| 58 | IF8A-75 | 直接解绑支付方式 | `/app/unbindAgreement` |
| 59 | IF8D-03 | 获取离线码数据 | `/app/requestNoSignalData` |

### 1.4 新增通知接口 - 6个

| 序号 | 接口编号 | 接口名称 | 接口路径 |
|------|----------|----------|----------|
| 60 | - | 卡片行程通知 | `/app/receiveCardTran` |
| 61 | - | 行程订单通知 | `/app/orderPayNotice` |
| 62 | - | 多日票次数扣减通知 | `/app/receiveCountingTicketTimes` |
| 63 | - | 接收签约异常状态通知 | `/app/receiveAgreementException` |
| 64 | - | 征信状态更新 | `/app/credit` |
| 65 | - | 5分钟客流数据更新 | `/receive/passengerFlow` |

### 1.5 支付宝出行接口 - 15个

| 序号 | 接口编号 | 接口名称 | 接口路径 |
|------|----------|----------|----------|
| 66 | - | 添加签约信息 | `/channel/addContract` |
| 67 | - | 解约登记（销卡） | `/channel/terminateContract` |
| 68 | - | 开卡申请 | `/channel/requestApplication` |
| 69 | - | 获取行业数据 | `/memberContract/channel/requestIndustryData` |
| 70 | - | 查询乘车记录 | `/channel/findTravelList` |
| 71 | - | 查询乘车记录详情 | `/channel/findTravelDetail` |
| 72 | - | 业务关闭结果通知 | `/notify/closeResultForAlipay` |
| 73 | - | 行程数据推送 | `/notify/pushTransData` |
| 74 | - | 行业数据推送 | `/notify/receiveCardDataFromItp` |
| 75 | - | 黑名单状态变更通知 | `/notify/receiveBlackListFromItp` |
| 76 | - | 支付 | `/api/payment/requestPay` |
| 77 | - | 支付结果查询 | `/api/payment/payQuery` |
| 78 | - | 退款 | `/api/payment/requestRefund` |
| 79 | - | 支付回调 | （自定义回调地址） |

---

## 二、接口分类统计

| 分类 | 接口数 | 说明 |
|------|--------|------|
| IF8A系列（APP请求） | 25 | 用户主动请求的接口 |
| IF8B系列（通知） | 8 | ITP推送给APP的通知接口 |
| 新增功能接口 | 47 | 日票、旅游票、离线码等新功能 |
| 新增通知接口 | 6 | 行程、征信等新通知 |
| 支付宝出行接口 | 15 | 支付宝出行专用接口 |
| **总计** | **80** | - |

---

## 三、核心业务流程

### 3.1 开户流程
1. IF8A-01 请求开户
2. IF8A-02 请求同步密钥
3. IF8A-03 请求行业数据

### 3.2 支付流程
1. IF8A-20 请求下单
2. IF8A-11 请求支付
3. IF8B-05 支付结果通知
4. IF8A-18 支付结果查询（主动查询）

### 3.3 退款流程
1. IF8A-12 请求退款
2. IF8B-04 退款结果通知
3. IF8A-13 退款结果查询（主动查询）

### 3.4 日票业务流程
1. IF8A-60 日票下单
2. IF8A-61 日票支付
3. IF8A-67 日票激活
4. IF8A-64 日票退款
5. IF8A-65 日票取消订单

### 3.5 黑名单流程
1. IF8B-03 黑名单结果通知（ITP推送）
2. IF8A-73 查询黑名单（APP主动查询）

---

## 四、公共参数

### 4.1 请求公共参数

| 字段 | 类型 | 说明 |
|------|------|------|
| providerId | string | 商户编码（01=青岛地铁APP）|
| charset | string | 字符集：UTF-8 |
| format | string | 数据格式：json |
| timestamp | string | 请求时间 YYYYMMDDHHMMSS |
| deviceId | string | 设备编码 |
| signType | string | 签名类型（00/01/02）|
| sign | string | 签名值 |
| bizData | Json | 业务数据 |

### 4.2 错误代码

| 错误码 | 含义 |
|--------|------|
| 0000 | 成功 |
| 9999 | 失败 |
| 8001 | 无效的参数 |
| 8002 | 已发卡 |
| 8003 | 暂无卡数据资源 |
| 8004 | 没有账号卡片数据 |
| 8005 | 已进入黑名单 |
| 8006 | 账号和卡号不匹配 |
| 8007 | 服务提供商不可用 |
| 8008 | 解约审核中 |
| 8009 | ITP无可用CA证书 |
| 8010 | 请勿重复签约 |
| 8011 | 用户未签约 |
| 8012 | 解除签约 |
| 8013 | 不允许解约默认支付渠道 |
| 8014 | 无效的签约数据 |
| 8021 | 请勿重复添加支付渠道 |
| 8180 | 该订单不能退款 |
| 8181 | 订单已支付成功，请勿重复支付 |
| 8182 | 订单已关闭，请重新创建订单 |
| 8183 | 订单无法激活 |
| 8184 | 订单已激活 |

---

*本文档从Word需求文档提取，共80个接口*
