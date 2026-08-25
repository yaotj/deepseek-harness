# 青岛ITP项目接口清单

**项目ID**: 8379611  
**项目名称**: 青岛itp  
**总接口数**: 72个

---

## 一、环境配置

| 环境ID | 环境名称 | 默认BaseURL |
|--------|----------|-------------|
| 46081294 | 开发环境 | http://127.0.0.1:9103 |
| 46081295 | 测试环境 | http://172.20.211.200:48000/fep-app |
| 48216132 | 内网测试环境 | http://172.20.211.23:30010 |
| 46081296 | 正式环境 | http://58.56.166.170:48000/fep-app |
| 46081297 | 本地 Mock | http://127.0.0.1:4523/mock/8379611 |
| 46081298 | 云端 Mock | - |
| 46081299 | 自托管 Mock | - |

---

## 二、接口目录结构

### 模块：IF8A-01 (folderId: 87411363) - 开发中

| 接口ID | 名称 | 方法 | 路径 | 状态 |
|--------|------|------|------|------|
| 467631846 | IF8A-01 请求开户2 | POST | /ci/app/requestApplication | developing |
| 467777970 | IF8A-16请求签约信息1 | POST | /ci/app/requestSignInfo | developing |
| 467868333 | IF8A-06 请求解约7 | POST | /ci/app/requestTermination | developing |
| 467870463 | IF8A-03 请求行业数据-拉码6 | POST | /ci/app/requestIndustryData | developing |
| 467871946 | IF8A-02 请求同步密钥5 | POST | /ci/app/requestKeyList | developing |
| 467874428 | IF8A-23 请求添加支付通道3 | POST | /ci/app/requestAddPayChannel | developing |
| 467875243 | IF8A-24 请求设置默认支付通道4 | POST | /ci/app/requestSetDefaultPayChannel | developing |
| 468003562 | 签约结果回调 | POST | /ci/app/receiveSignResult | developing |
| 468525488 | oldSecrityServer | POST | http://172.20.201.2:12301/... | developing |
| 467875758 | if8a_73 - 查询黑名单8 | POST | /app/ticket/queryBlackList | developing |

### 模块：支付宝出行-开卡申请 (folderId: 89791799)

| 接口ID | 名称 | 方法 | 路径 | 状态 |
|--------|------|------|------|------|
| 480784381 | 支付宝出行-开卡申请 | POST | /channel/requestApplication | released |

### 模块：支付宝出行-签约管理 (folderId: 89817120)

| 接口ID | 名称 | 方法 | 路径 | 状态 |
|--------|------|------|------|------|
| 480696771 | 支付宝出行-添加签约信息 | POST | /channel/addContract | released |
| 480935428 | 添加签约信息 | POST | /channel/addContract | released |
| 480935429 | 解约登记 | POST | /channel/terminateContract | released |

### 模块：TVM-扫码购票 (folderId: 91063100)

| 接口ID | 名称 | 方法 | 路径 | 状态 |
|--------|------|------|------|------|
| 487944739 | IF2A-01 提交单程票订单 | POST | /itptvm/ci/tvm/requestGenSjtOrder | released |
| 487944740 | IF2A-03 查询支付结果 | POST | /itptvm/ci/tvm/requestPayResult | released |
| 487944741 | IF2A-04 出票结果通知 | POST | /itptvm/ci/tvm/notiTakeTicketResult | released |
| 487944742 | IF2A-05 出票故障通知 | POST | /itptvm/ci/tvm/notiTakeTicketFailResult | released |
| 488153630 | 心跳接口 | POST | /itptvm/ci/tvm/notiDeviceHeard | released |

### 模块：TVM-扫码充值 (folderId: 91063101)

| 接口ID | 名称 | 方法 | 路径 | 状态 |
|--------|------|------|------|------|
| 487944743 | IF2A-09 请求充值下单 | POST | /itptvm/ci/tvm/requestTopup | released |
| 487944744 | IF2A-06 充值结果通知 | POST | /itptvm/ci/tvm/topupCardResultNoti | released |
| 487944745 | IF2A-07 充值失败通知 | POST | /itptvm/ci/tvm/topupCardFailNoti | released |

### 模块：BOM-非现金支付 (folderId: 91063102)

| 接口ID | 名称 | 方法 | 路径 | 状态 |
|--------|------|------|------|------|
| 487944746 | IF8A-04 请求非现金收款下单 | POST | /itpbom/ci/bom/requestGenNoCashOrder | released |
| 487944747 | IF8A-05 扫码支付 | POST | /itpbom/ci/bom/requestPayment | released |
| 487944748 | IF8A-06 查询支付结果 | POST | /itpbom/ci/bom/requestGetPayResult | released |
| 487944749 | IF2A-08 业务操作结果通知 | POST | /itpbom/ci/bom/notiBusResult | released |
| 493560041 | IF2A-08 票卡分析 | POST | /itpbom/ci/bom/requestCardDataAnalyse | released |
| 502923237 | IF8A-27 / HCE 数据更新通知 | POST | /itpbom/ci/bom/notiUpdateHceData | released |
| 492978055 | IF2A-09 BOM上报充值结果通知 | POST | /itpbom/ci/bom/notiTopupResult | released |

### 模块：TVM (folderId: 91234338)

| 接口ID | 名称 | 方法 | 路径 | 状态 |
|--------|------|------|------|------|
| 488928688 | IF2A-REFUND-01 退款 | POST | /itptvm/ci/tvm/requestRefund | released |

### 模块：AppPara (folderId: 91174485)

| 接口ID | 名称 | 方法 | 路径 | 状态 |
|--------|------|------|------|------|
| 488592637 | IF8A-07 获取线路代码 | POST | /ci/app/requestLineCodeList | released |
| 488592638 | IF8A-08 获取车站代码 | POST | /ci/app/requestStationCodeList | released |
| 493171224 | IF8A-08 获取车站线路代码 | POST | /ci/app/requestStationLineInfo | released |
| 488592639 | IF8A-10 计算票价 | POST | /ci/app/requestTicketPriceByStation | released |
| 488592640 | IF8A-17 获取线路站点代码版本 | POST | /ci/app/requestLineStationCodeVersion | released |
| 488592641 | 查询车站名称 | POST | /ci/app/requestStationName | released |

### 根目录接口 (folderId: 0)

#### 闸机相关 (IF1A)

| 接口ID | 名称 | 方法 | 路径 | 状态 |
|--------|------|------|------|------|
| 490850107 | IF1A-01 闸机检票通知 | POST | /ci/agm/notiVerifyResult | released |
| 490850108 | IF1A-02 密钥同步 | POST | /ci/agm/requestSynKeyList | released |
| 490850109 | IF1A-04 查询票卡状态 | POST | /ci/agm/requestQrCodeStatus | released |
| 490850110 | IF1A-03 设备心跳 | POST | /ci/agm/deviceHeartbeat | released |

#### 支付宝出行相关

| 接口ID | 名称 | 方法 | 路径 | 状态 |
|--------|------|------|------|------|
| 480696772 | 支付宝出行-解约登记 | POST | /channel/terminateContract | released |
| 480696773 | 支付宝出行-开卡申请 | POST | /channel/requestApplication | released |
| 480696774 | 支付宝出行-查询乘车记录列表 | POST | /channel/findTravelList | released |
| 480696775 | ALIPAY-006 查询乘车记录详情 | POST | /channel/findTravelDetail | released |
| 480696776 | 支付宝出行-获取行业数据 | POST | /channel/requestIndustryData | released |
| 480136427 | 支付宝出行-添加签约信息 | POST | /channel/addContract | released |
| 480136428 | 支付宝出行-解约登记 | POST | /channel/terminateContract | released |
| 480136429 | 支付宝出行-开卡申请 | POST | /channel/requestApplication | released |
| 480136430 | 支付宝出行-获取行业数据 | POST | /channel/requestIndustryData | released |

#### 支付相关

| 接口ID | 名称 | 方法 | 路径 | 状态 |
|--------|------|------|------|------|
| 487478045 | 支付结果回调 | POST | /notify/payment/payNotify | released |
| 480696777 | 支付申请-已废弃 | POST | /api/payment/requestPay | released |
| 480696778 | ALIPAY-012 支付结果查询 | POST | /admin/payment/payQuery | released |
| 480696779 | ALIPAY-013 退款申请 | POST | /admin/payment/requestRefund | released |

#### 日票相关

| 接口ID | 名称 | 方法 | 路径 | 状态 |
|--------|------|------|------|------|
| 489403210 | IF8A-60 日票下单 | POST | /app/requestCountingOrder | released |
| 489403211 | IF8A-61 日票支付 | POST | /app/payment/requestPay | released |
| 489504551 | IF8A-62 日票支付结果查询 | POST | /app/payment/requestPayResult | released |
| 489504552 | IF8A-64 日票退款 | POST | /app/payment/requestRefundTicket | released |
| 489504553 | IF8A-65 日票取消订单 | POST | /app/ticket/cancelOrder | released |
| 489504554 | IF8A-67 日票激活 | POST | /app/ticket/updateTicket | released |
| 489504555 | IF8A-71 通知 ACC 车票已使用 | POST | /app/ticket/updateAndNotice | released |

#### App相关

| 接口ID | 名称 | 方法 | 路径 | 状态 |
|--------|------|------|------|------|
| 489903043 | IF8A-04 请求自助补站 | POST | /ci/app/requestExcessFare | released |
| 478553746 | 查上次行程 | POST | /ci/app/queryUserItinerary | developing |
| 502696370 | IF8A-34 获取订单详情 | POST | /ci/app/requestTransDetail | developing |
| 502732893 | IF8A-05请求查询交易记录 | POST | /ci/app/requestTransList | developing |
| 502918043 | 3.11 IF8A-11 请求支付 | POST | /ci/app/requestPaymentInfo | developing |
| 500996870 | 更换第三方渠道码默认支付方式 | POST | /app/requestUpdateChannelDefaultContract | developing |
| 501131604 | IF8A-22 签约结果咨询 | POST | /ci/app/requestContractResult | developing |

#### 黑名单相关 (IF8B)

| 接口ID | 名称 | 方法 | 路径 | 状态 |
|--------|------|------|------|------|
| 489903993 | IF8B-01 新增黑名单 | POST | /addBlackList | released |
| 489906038 | IF8B-02 执行解约 | POST | /admin/payment/executeTermination | released |

#### 员工码相关

| 接口ID | 名称 | 方法 | 路径 | 状态 |
|--------|------|------|------|------|
| 500570021 | ACC - 员工码状态通知 | POST | {{fep_acc_base_url}}/employee_card/notify | released |
| 500570022 | APP - 员工码信息查询 | POST | {{fep_app_base_url}}/ci/app/employee_card/query | released |
| 501264757 | 员工信息变更通知 | POST | /employee_card/update_notify | released |

---

## 三、接口统计

| 状态 | 数量 |
|------|------|
| released (已发布) | 58 |
| developing (开发中) | 14 |
| **总计** | **72** |

---

## 四、接口分类汇总

### 按业务模块分类

| 模块 | 接口数 | 说明 |
|------|--------|------|
| IF8A-01 | 10 | App参数相关接口（开发中） |
| 支付宝出行 | 11 | 签约、开卡、行业数据等 |
| TVM-扫码购票 | 5 | 单程票订单相关 |
| TVM-扫码充值 | 3 | 充值相关 |
| BOM-非现金支付 | 7 | 非现金支付相关 |
| AppPara | 6 | 线路、车站代码查询 |
| 日票 | 7 | 日票下单、支付、退款 |
| 闸机(IF1A) | 4 | 检票、密钥、心跳 |
| 支付 | 4 | 支付回调、查询、退款 |
| 黑名单(IF8B) | 2 | 黑名单管理 |
| 员工码 | 3 | 员工码通知、查询 |
| 其他App接口 | 10 | 自助补站、交易记录等 |

---

## 五、备注

1. **公共参数**: 所有接口使用 `application/x-www-form-urlencoded` 格式
2. **必填参数**: sign（签名）、charset、format、timestamp、deviceId、signType
3. **业务参数**: bizData（JSON格式的业务数据）
4. **环境变量**: 部分接口使用变量如 `{{fep_acc_base_url}}`、`{{fep_app_base_url}}`、`{{dynamicThirdUserId}}`
