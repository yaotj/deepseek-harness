# TVM 自动售票机聚合支付联调计划

> 服务：collect-pay-server  
> 对接方：第三方支付中心（BestOnePay）  
> 涉及设备：TVM 自动售票机  
> 整理日期：2026-07-20

---

## 一、联调前准备

### 1.1 环境信息确认

| 项目 | 内容 | 备注 |
|------|------|------|
| collect-pay-server 地址 | http://localhost:8080 | 开发环境 |
| 支付中心网关 | http://dtcustomer.bestonepay.com/ngpayment-gateway | 第三方生产地址 |
| 商户号 | JOPJ490HLK9Z | 需与支付中心确认 |
| 签名方式 | RSA | 商户私钥签名 |
| 数据库 | Oracle AFCITPDB | 130.251.235.198:1521/qditp |
| 回调地址 | http://localhost:9098/ci/app/ticketCollectPayNotify | 支付结果异步通知 |

### 1.2 关键配置确认

```yaml
# collect-pay-server/src/main/resources/application.yml
pay:
  center:
    merchant-no: JOPJ490HLK9Z
    api-version: 1.0
    sign-type: RSA
    charset: UTF-8
    gateway-url: https://pay-gateway.example.com/api
    private-key: <商户私钥>
    paycenter-public-key: <支付中心公钥>
    callback-url: http://localhost:9098/ci/app/ticketCollectPayNotify
    pay-center-pay-url: http://dtcustomer.bestonepay.com/ngpayment-gateway/api/v1/payment/requestPay
    pay-center-query-url: http://dtcustomer.bestonepay.com/ngpayment-gateway/api/v1/payment/payQuery
    pay-center-refund-url: http://dtcustomer.bestonepay.com/ngpayment-gateway/api/v1/refund/requestRefund
    refund-reason: 出票数量不足
```

### 1.3 联调参与方

| 角色 | 负责方 | 内容 |
|------|--------|------|
| 支付平台对接 | 第三方支付中心 | 接口联调、商户号配置、密钥交换 |
| 设备对接 | TVM 厂商 | 设备报文格式、交互流程 |
| 服务端联调 | 开发团队 | collect-pay-server 接口实现、数据库、日志 |
| 测试验证 | 测试团队 | 用例执行、异常场景覆盖 |

---

## 二、TVM 接口清单

### 2.1 核心接口

| 接口编号 | 接口名称 | 路径 | 请求方式 | 说明 |
|---------|---------|------|----------|------|
| IF2A-01 | 提交单程票订单 | `/itptvm/ci/tvm/requestGenSjtOrder` | POST | 生成 TVM 购票订单 |
| IF2A-11 | 扫码支付 | `/itptvm/ci/tvm/requestPayment` | POST | TVM 扫码支付 |
| IF2A-03 | 查询支付结果 | `/itptvm/ci/tvm/requestPayResult` | POST | 轮询查询支付结果 |
| IF2A-04 | 出票结果通知 | `/itptvm/ci/tvm/notiTakeTicketResult` | POST | 接收 TVM 出票通知 |
| IF2A-05 | 出票故障通知 | `/itptvm/ci/tvm/notiTakeTicketFailResult` | POST | 接收 TVM 出票故障 |
| - | 退款 | `/itptvm/ci/tvm/requestRefund` | POST | 主动退款 |
| IF8A-15 | 激活取票订单 | `/itptvm/ci/tvm/requestActiveTicket` | POST | 手机扫码激活取票 |
| IF2A-08 | 扫码取票订单查询 | `/itptvm/ci/tvm/requestTakeTicketAuth` | POST | TVM 轮询查询激活状态 |
| IF2A-09 | 请求充值下单 | `/itptvm/ci/tvm/requestTopup` | POST | 卡片充值 |
| IF2A-06 | 充值结果通知 | `/itptvm/ci/tvm/topupCardResultNoti` | POST | 接收充值结果 |
| IF2A-07 | 充值失败通知 | `/itptvm/ci/tvm/topupCardFailNoti` | POST | 接收充值失败 |

### 2.2 请求格式

使用 `application/x-www-form-urlencoded`（FormData）格式：

```
providerId=01&charset=UTF-8&format=json&timestamp=xxx&deviceId=xxx&signType=00&sign=&bizData={...}
```

控制器使用 `@ModelAttribute BaseRequestDTO` 接收，解析 `bizData` JSON 后转发。

---

## 三、TVM 核心业务流程

### 3.1 扫码购票流程

1. **提交订单**：生成订单号（`00` + yyyyMMddHHmmss + 8位随机字符），保存 TVM_PAY_ORDER 和 TVM_ORDER_PRE
2. **拉码支付**：调用支付中心拉码接口，返回支付 URL
3. **扫码支付**：TVM 扫描用户付款码，调用支付中心支付接口
4. **查询结果**：TVM 轮询查询支付结果
5. **出票通知**：TVM 出票后通知，保存出票记录，计算退款金额
6. **出票故障**：TVM 出票故障通知，保存故障记录，自动退款

### 3.2 扫码充值流程

1. **充值下单**：生成充值订单
2. **充值结果通知**：接收充值结果
3. **充值失败通知**：接收充值失败

### 3.3 扫码取票流程

1. **激活取票**：手机扫码 TVM 二维码后激活取票订单
2. **查询激活状态**：TVM 轮询查询激活状态

---

## 四、TVM 数据模型

### 4.1 TVM 相关表

| 表名 | 说明 |
|------|------|
| TVM_PAY_ORDER | TVM 支付订单表 |
| TVM_ORDER_PRE | TVM 订单前置信息表 |
| TVM_MAIN_TICKET | TVM 出票主记录表 |
| TVM_SUB_TICKET | TVM 出票明细表 |
| TVM_TAKE_TICKET_ORDER | TVM 取票订单表 |
| TVM_TOPUP_ORDER | TVM 充值订单表 |
| REFUND_ORDER | 退款订单表 |

### 4.2 订单号生成规则

| 业务 | 格式 | 示例 |
|------|------|------|
| TVM 购票 | `00` + yyyyMMddHHmmss + 8位随机字符 | `0020260720101234567890abcdef` |
| 退款 | `RF` + yyyyMMddHHmmss + 6位随机字符 | `RF20260720101234567890abcdef` |
| 通知ID | `N` + yyyyMMddHHmmss + 8位随机字符 | `N20260720101234567890abcdef` |

---

## 五、TVM 接口边界联调场景

### 5.1 提交单程票订单（requestGenSjtOrder）

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| TVM-01 | 正常下单 | 合法参数 | 返回 orderNo + payUrl | TVM_PAY_ORDER + TVM_ORDER_PRE 入库 |
| TVM-02 | deviceId 为空 | deviceId="" | 返回 FAILED | 参数校验 |
| TVM-03 | 票价为空 | ticketPrice="" | 返回 FAILED | 参数校验 |
| TVM-04 | 购票数量为空 | singelTicketNum="" | 返回 FAILED | 参数校验 |
| TVM-05 | 票价超大 | ticketPrice=999999999 | 返回 FAILED | 参数校验 |
| TVM-06 | 购票数量为 0 | singelTicketNum=0 | 返回 FAILED | 参数校验 |
| TVM-07 | 购票数量超大 | singelTicketNum=999 | 返回 FAILED | 参数校验 |
| TVM-08 | 进站车站为空 | entryStationCode="" | 返回 FAILED | 参数校验 |
| TVM-09 | 非数币渠道 | payType=0 | 返回测试 URL | 不调用支付中心 |
| TVM-10 | 数据库异常 | 模拟 Oracle 故障 | 返回 FAILED | 异常捕获，记录日志 |

### 5.2 扫码支付（requestPayment）

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| TVM-11 | 正常支付 | 合法参数 | 返回支付结果 | TVM_PAY_ORDER 状态更新 |
| TVM-12 | 订单不存在 | orderNo 不存在 | 返回 FAILED | msg="订单号错误" |
| TVM-13 | 订单已支付 | 重复支付 | 返回 SUCCESS | 不调用支付中心 |
| TVM-14 | 订单已失败 | 已失败订单 | 返回 FAILED | 不调用支付中心 |
| TVM-15 | 订单已未支付 | UNPAID 订单 | 返回 FAILED | 不调用支付中心 |
| TVM-16 | paymentCode 为空 | paymentCode="" | 返回 FAILED | 参数校验 |
| TVM-17 | paymentVendor 为空 | paymentVendor="" | 返回 FAILED | 参数校验 |
| TVM-18 | 支付中心超时 | 模拟超时 | 返回 FAILED | 数据库状态=FAILED |
| TVM-19 | 支付中心返回空 | 模拟空响应 | 返回 FAILED | 数据库状态=FAILED |

### 5.3 查询支付结果（requestPayResult）

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| TVM-20 | 订单已支付 | 数据库状态=SUCCESS | 直接返回 SUCCESS | 不调用支付中心 |
| TVM-21 | 订单已失败 | 数据库状态=FAILED | 直接返回 FAILED | 不调用支付中心 |
| TVM-22 | 订单未支付 | 数据库状态=UNPAID | 直接返回 FAILED | 不调用支付中心，UNPAID 视为终态 |
| TVM-23 | 支付中状态 | 数据库状态=PAYING | 调用支付中心查询 | 根据查询结果更新 |
| TVM-24 | 支付中心返回成功 | status=SUCCESS | 返回 SUCCESS | 数据库状态更新 |
| TVM-25 | 支付中心返回失败 | status=FAILED | 返回 FAILED | 数据库状态更新 |
| TVM-26 | 支付中心返回处理中 | status=PROCESSING | 返回 PROCESSING | 数据库状态保持 PAYING |
| TVM-27 | 订单不存在 | orderNo 不存在 | 返回 FAILED | 不调用支付中心 |
| TVM-28 | 支付中心返回空 | 模拟空响应 | 返回 PROCESSING | 数据库状态保持 PAYING |
| TVM-29 | 支付中心返回非 200 | 模拟 code=500 | 返回 PROCESSING | 数据库状态保持 PAYING |
| TVM-30 | 轮询超时 | 轮询 3 分钟 | 保持 PROCESSING | 设备侧自行处理超时 |

### 5.4 出票结果通知（notiTakeTicketResult）

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| TVM-31 | 正常出票 | actualNum=buyNum | 返回 SUCCESS | 自动退款金额=0 |
| TVM-32 | 出票数量不足 | actualNum < buyNum | 返回 SUCCESS | 自动退款金额=(buyNum-actualNum)×单价 |
| TVM-33 | 出票数量为 0 | actualNum=0 | 返回 SUCCESS | 自动退款金额=buyNum×单价 |
| TVM-34 | 出票数量超额 | actualNum > buyNum | 返回 SUCCESS | 不退款，actualNum 记录 |
| TVM-35 | 订单不存在 | orderNo 不存在 | 返回 FAILED | msg="订单号错误" |
| TVM-36 | actualNum 为空 | actualNum="" | 返回 FAILED | 参数校验 |
| TVM-37 | takeTickeDate 为空 | takeTickeDate="" | 返回 FAILED | 参数校验 |
| TVM-38 | ticketList 为空 | ticketList=null | 返回 SUCCESS | TVM_SUB_TICKET 不插入 |
| TVM-39 | ticketList 部分空 | 部分字段为空 | 返回 SUCCESS | 空值字段处理 |
| TVM-40 | 重复通知 | 同一订单多次通知 | 返回 SUCCESS | 幂等性，TVM_MAIN_TICKET 新增记录 |
| TVM-41 | 退款失败 | 模拟支付中心退款失败 | 返回 SUCCESS | 记录退款失败日志 |

### 5.5 出票故障通知（notiTakeTicketFailResult）

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| TVM-42 | 正常故障 | errorCode≠2101 | 返回 SUCCESS | 状态=REFUND_PENDING，自动退款 |
| TVM-43 | 二维码超时 | errorCode=2101 | 返回 SUCCESS | 状态=CANCELED，不退款 |
| TVM-44 | 错误码为空 | errorCode="" | 返回 SUCCESS | 视为其他故障，退款 |
| TVM-45 | 订单不存在 | orderNo 不存在 | 返回 FAILED | msg="订单号错误" |
| TVM-46 | actualNum 为 0 | actualNum=0 | 返回 SUCCESS | 退款金额=buyNum×单价 |
| TVM-47 | actualNum 部分出票 | actualNum>0 | 返回 SUCCESS | 退款金额=(buyNum-actualNum)×单价 |
| TVM-48 | faultSlipSeq 为空 | faultSlipSeq="" | 返回 SUCCESS | 允许为空 |
| TVM-49 | errorMessage 为空 | errorMessage="" | 返回 SUCCESS | 允许为空 |
| TVM-50 | 重复通知 | 同一订单多次通知 | 返回 SUCCESS | 幂等性，新增故障记录 |

### 5.6 退款（requestRefund）

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| TVM-51 | 正常退款 | 合法参数 | 返回 SUCCESS | REFUND_ORDER 入库，调用支付中心 |
| TVM-52 | 订单不存在 | orderNo 不存在 | 返回 FAILED | msg="订单号错误" |
| TVM-53 | 订单未支付 | 订单状态=PAYING | 返回 FAILED | 不允许退款 |
| TVM-54 | 订单已退款 | 已退款订单 | 返回 FAILED | 幂等性 |
| TVM-55 | 退款金额为 0 | refundAmount=0 | 返回 FAILED | 参数校验 |
| TVM-56 | 退款金额超额 | refundAmount>订单金额 | 返回 FAILED | 参数校验 |
| TVM-57 | 支付中心退款失败 | 模拟退款失败 | 返回 FAILED | REFUND_ORDER 状态=FAILED |
| TVM-58 | 支付中心返回空 | 模拟空响应 | 返回 FAILED | REFUND_ORDER 状态=FAILED |

### 5.7 充值下单（requestTopup）

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| TVM-59 | 正常充值 | 金额 0.1 元 | 返回 orderNo + payUrl | TVM_TOPUP_ORDER 入库 |
| TVM-60 | 充值金额为 0 | transAmount=0 | 返回 FAILED | 参数校验 |
| TVM-61 | 充值金额为负 | transAmount=-1 | 返回 FAILED | 参数校验 |
| TVM-62 | 充值超额 | beforeAmount+transAmount>1000元 | 返回 FAILED | 超过最大充值限额 |
| TVM-63 | 单次超额 | transAmount>1000元 | 返回 FAILED | 超过单次限额 |
| TVM-64 | ticketLogicNum 为空 | ticketLogicNum="" | 返回 FAILED | 参数校验 |
| TVM-65 | 数据库异常 | 模拟 Oracle 故障 | 返回 FAILED | 异常捕获 |

### 5.8 激活取票（requestActiveTicket）

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| TVM-66 | 正常激活 | 合法参数 | 返回 SUCCESS | 取票订单状态更新 |
| TVM-67 | 订单不存在 | orderNo 不存在 | 返回 FAILED | msg="订单号错误" |
| TVM-68 | 订单已激活 | 重复激活 | 返回 FAILED | 幂等性 |
| TVM-69 | deviceId 为空 | deviceId="" | 返回 FAILED | 参数校验 |
| TVM-70 | qrcodeGenDate 为空 | qrcodeGenDate="" | 返回 FAILED | 参数校验 |
| TVM-71 | randomFact 为空 | randomFact="" | 返回 FAILED | 参数校验 |

### 5.9 扫码取票订单查询（requestTakeTicketAuth）

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| TVM-72 | 查询成功 | 合法参数 | 返回订单信息 | TVM_TAKE_TICKET_ORDER 查询 |
| TVM-73 | 订单不存在 | 三元组不匹配 | 返回 FAILED | msg="没有激活订单" |
| TVM-74 | deviceId 为空 | deviceId="" | 返回 FAILED | 参数校验 |
| TVM-75 | qrcodeGenDate 格式错误 | 非 yyyyMMddHHmmss | 返回 FAILED | 时间格式校验 |
| TVM-76 | randomFact 为空 | randomFact="" | 返回 FAILED | 参数校验 |

---

## 六、TVM 设备端边界场景

| 场景ID | 场景描述 | 设备操作 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| TVM-D-01 | 正常扫码购票 | 选择票价→扫码→出票 | 出票成功，状态=SUCCESS | 全流程验证 |
| TVM-D-02 | 扫码后取消支付 | 扫码后关闭支付码 | 轮询超时→状态=PROCESSING→最终 UNPAID | 设备超时处理 |
| TVM-D-03 | 支付后设备断电 | 支付成功后断电重启 | 重启后查询订单状态=SUCCESS | 数据持久化 |
| TVM-D-04 | 网络中断恢复 | 支付时断网→恢复 | 重试后支付成功 | 网络容错 |
| TVM-D-05 | 重复扫码 | 同一订单多次扫码 | 第二次扫码返回已支付 | 幂等性 |
| TVM-D-06 | 二维码过期 | 二维码 5 分钟未支付 | 轮询返回 PROCESSING→UNPAID | 超时处理 |
| TVM-D-07 | 出票卡票 | 支付成功但出票失败 | 触发故障通知→自动退款 | 退款金额正确 |
| TVM-D-08 | 出票部分成功 | 购 5 张出 3 张 | 自动退款 2 张金额 | 退款金额=(5-3)×单价 |
| TVM-D-09 | 出票全部失败 | 购 5 张出 0 张 | 自动退款 5 张金额 | 退款金额=5×单价 |
| TVM-D-10 | 充值金额超大 | 充值 2000 元 | 返回 FAILED | 超过 1000 元限额 |
| TVM-D-11 | 充值余额溢出 | 原有 900 元，充 200 元 | 返回 FAILED | 超过 1000 元限额 |
| TVM-D-12 | 充值后余额刚好 1000 | 原有 800 元，充 200 元 | 返回 SUCCESS | 边界值 1000 元 |
| TVM-D-13 | 取票二维码过期 | 激活二维码过期 | 查询返回 FAILED | 过期处理 |
| TVM-D-14 | 取票订单已使用 | 重复查询已激活订单 | 返回 FAILED | 幂等性 |
| TVM-D-15 | 设备时间错误 | 设备时间回拨 | 返回 FAILED | 时间校验 |

---

## 七、TVM 支付中心接口边界场景

### 7.1 拉码下单边界

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| L-01 | 正常拉码 | 金额 0.01，payType=1 | 返回 payUrl | 数据库 TVM_PAY_ORDER 状态=PAYING |
| L-02 | 非数币渠道 | payType=0 | 返回测试 URL | 不调用支付中心，sign="sign-test" |
| L-03 | 支付中心返回空响应 | 模拟超时 | 返回 FAILED | 数据库状态=FAILED |
| L-04 | 支付中心返回 code≠200 | 模拟 code=500 | 返回 FAILED | 数据库状态=FAILED |
| L-05 | 支付中心返回 data 为空 | code=200，data=null | 返回 FAILED | 数据库状态=FAILED |
| L-06 | 支付中心返回 data 缺少字段 | 缺少 orderNo | 返回 FAILED | 数据库状态=FAILED |
| L-07 | 网络超时 | 支付中心无响应 | 返回 FAILED | 超时时间 15s |
| L-08 | RSA 签名错误 | 私钥不匹配 | 支付中心返回签名错误 | 返回 FAILED |
| L-09 | 商户号错误 | merchant-no 不匹配 | 支付中心返回商户号错误 | 返回 FAILED |
| L-10 | 金额为 0 | 金额 0 | 支付中心拒绝 | 参数校验 |
| L-11 | 金额超大 | 金额 999999999 | 支付中心拒绝 | 参数校验 |
| L-12 | 订单号重复 | 重复提交 | 支付中心处理 | 本地生成策略保证唯一 |
| L-13 | 字符集异常 | charset 乱码 | 支付中心拒绝 | 验证 UTF-8 编码 |

### 7.2 扫码支付边界

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| P-01 | 支付成功 | status=SUCCESS | 返回 SUCCESS | 数据库 TVM_PAY_ORDER 状态=SUCCESS |
| P-02 | 支付失败 | status=FAILED | 返回 FAILED | 数据库状态=FAILED |
| P-03 | 支付中 | status=PROCESSING | 返回 PROCESSING | 数据库状态保持 PAYING |
| P-04 | 支付中心返回未知状态 | status=UNKNOWN | 返回 PROCESSING | 数据库状态保持 PAYING |
| P-05 | 订单不存在 | orderNo 不存在 | 返回 FAILED | msg="订单号错误" |
| P-06 | 订单已支付 | 重复支付 | 直接返回 SUCCESS | 不调用支付中心 |
| P-07 | 订单已失败 | 已失败订单再次支付 | 直接返回 FAILED | 不调用支付中心 |
| P-08 | 支付中心返回空 | 模拟空响应 | 返回 FAILED | 数据库状态=FAILED |
| P-09 | 支付中心返回非 200 | 模拟 code=500 | 返回 FAILED | 数据库状态=FAILED |
| P-10 | paymentCode 为空 | 付款码缺失 | 返回 FAILED | 参数校验 |
| P-11 | paymentVendor 为空 | 支付方式缺失 | 返回 FAILED | 参数校验 |
| P-12 | 网络中断 | 调用超时 | 返回 FAILED | 超时时间 5s |

### 7.3 查询支付结果边界

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| Q-01 | 订单已支付 | 数据库状态=SUCCESS | 直接返回 SUCCESS | 不调用支付中心 |
| Q-02 | 订单已失败 | 数据库状态=FAILED | 直接返回 FAILED | 不调用支付中心 |
| Q-03 | 订单未支付 | 数据库状态=UNPAID | 直接返回 FAILED | 不调用支付中心 |
| Q-04 | 支付中状态 | 数据库状态=PAYING | 调用支付中心查询 | 根据查询结果更新 |
| Q-05 | 支付中心返回成功 | status=SUCCESS | 返回 SUCCESS | 数据库状态更新 |
| Q-06 | 支付中心返回失败 | status=FAILED | 返回 FAILED | 数据库状态更新 |
| Q-07 | 支付中心返回处理中 | status=PROCESSING | 返回 PROCESSING | 数据库状态保持 PAYING |
| Q-08 | 订单不存在 | orderNo 不存在 | 返回 FAILED | 不调用支付中心 |
| Q-09 | 支付中心返回空 | 模拟空响应 | 返回 PROCESSING | 数据库状态保持 PAYING |
| Q-10 | 支付中心返回非 200 | 模拟 code=500 | 返回 PROCESSING | 数据库状态保持 PAYING |
| Q-11 | 轮询超时 | 轮询 3 分钟 | 保持 PROCESSING | 设备侧自行处理超时 |

### 7.4 退款边界

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| R-01 | 退款成功 | 合法退款请求 | 返回 SUCCESS | REFUND_ORDER 状态=SUCCESS |
| R-02 | 退款失败 | 模拟退款失败 | 返回 FAILED | REFUND_ORDER 状态=FAILED |
| R-03 | 原订单不存在 | orderNo 不存在 | 返回 FAILED | 不调用支付中心 |
| R-04 | 退款金额为 0 | refundAmount=0 | 返回 FAILED | 参数校验 |
| R-05 | 退款金额超过订单 | refundAmount > 订单金额 | 返回 FAILED | 参数校验 |
| R-06 | 重复退款 | 同一订单多次退款 | 第二次退款 | 幂等性处理 |
| R-07 | 支付中心返回空 | 模拟空响应 | 返回 FAILED | REFUND_ORDER 状态=FAILED |
| R-08 | 网络超时 | 调用超时 | 返回 FAILED | 超时时间 15s |

---

## 八、TVM 数据一致性验证

### 8.1 订单状态一致性

| 场景ID | 场景描述 | 验证方法 | 预期结果 |
|--------|----------|----------|----------|
| DC-01 | 支付成功状态 | 查询数据库 | TVM_PAY_ORDER.status=SUCCESS |
| DC-02 | 支付失败状态 | 查询数据库 | TVM_PAY_ORDER.status=FAILED |
| DC-03 | 未支付终态 | 轮询结束无结果 | TVM_PAY_ORDER.status=UNPAID |
| DC-04 | 出票成功状态 | 查询数据库 | TVM_MAIN_TICKET.notifyType=0 |
| DC-05 | 出票故障状态 | 查询数据库 | TVM_MAIN_TICKET.notifyType=1 |
| DC-06 | 退款状态 | 查询数据库 | REFUND_ORDER.status=SUCCESS/FAILED |
| DC-07 | 退款订单号关联 | 查询数据库 | TVM_PAY_ORDER.rsv2=退款单号 |

### 8.2 金额一致性

| 场景ID | 场景描述 | 验证方法 | 预期结果 |
|--------|----------|----------|----------|
| DC-08 | 退款金额计算 | 出票数量不足 | 退款金额=(buyNum-actualNum)×单价 |
| DC-09 | 退款金额精度 | 单价 2.5 元，退 1 张 | 退款金额=250（分） |
| DC-10 | 退款金额上限 | 退款金额≤订单金额 | 不超过订单总金额 |

### 8.3 时间一致性

| 场景ID | 场景描述 | 验证方法 | 预期结果 |
|--------|----------|----------|----------|
| DC-11 | 订单创建时间 | 查询数据库 | createTime 正确 |
| DC-12 | 订单更新时间 | 查询数据库 | updateTime 正确 |
| DC-13 | 出票时间 | 查询数据库 | takeTickeDate 正确 |
| DC-14 | 故障时间 | 查询数据库 | faultOccurDate 正确 |
| DC-15 | 退款时间 | 查询数据库 | refundTime 正确 |

---

## 九、TVM 并发与压力边界

| 场景ID | 场景描述 | 测试方法 | 预期结果 |
|--------|----------|----------|----------|
| CP-01 | 同一订单并发支付 | 两个请求同时支付 | 只有一个成功，另一个返回已支付 |
| CP-02 | 同一订单并发查询 | 多个请求同时查询 | 返回结果一致 |
| CP-03 | 同一订单并发通知 | 多个通知同时到达 | 幂等处理，不重复退款 |
| CP-04 | 批量订单并发 | 100 个订单同时支付 | 全部正确处理 |
| CP-05 | 支付中心限流 | 模拟限流 | 返回 FAILED，记录日志 |
| CP-06 | 数据库连接池耗尽 | 模拟连接池满 | 返回 FAILED，记录日志 |

---

## 十、TVM 联调检查清单

### 10.1 环境检查

- [ ] collect-pay-server 可正常启动（端口 8080）
- [ ] Oracle 数据库连接正常（130.251.235.198:1521/qditp）
- [ ] 支付中心网络连通（dtcustomer.bestonepay.com）
- [ ] RSA 密钥配置正确（商户私钥、支付中心公钥）
- [ ] 回调地址在支付中心配置正确（localhost:9098）
- [ ] 日志级别为 DEBUG

### 10.2 接口检查

- [ ] TVM 下单接口路径正确（/itptvm/ci/tvm/requestGenSjtOrder）
- [ ] TVM 支付接口路径正确（/itptvm/ci/tvm/requestPayment）
- [ ] TVM 查询接口路径正确（/itptvm/ci/tvm/requestPayResult）
- [ ] TVM 出票通知接口路径正确（/itptvm/ci/tvm/notiTakeTicketResult）
- [ ] TVM 出票故障接口路径正确（/itptvm/ci/tvm/notiTakeTicketFailResult）
- [ ] TVM 退款接口路径正确（/itptvm/ci/tvm/requestRefund）
- [ ] TVM 充值接口路径正确（/itptvm/ci/tvm/requestTopup）
- [ ] TVM 激活取票接口路径正确（/itptvm/ci/tvm/requestActiveTicket）
- [ ] TVM 取票查询接口路径正确（/itptvm/ci/tvm/requestTakeTicketAuth）
- [ ] 请求方式为 POST
- [ ] Content-Type 为 application/x-www-form-urlencoded
- [ ] bizData 为 JSON 字符串

### 10.3 数据检查

- [ ] 订单号生成规则正确（00+时间+随机）
- [ ] 订单号唯一性（数据库约束）
- [ ] 支付订单状态正确
- [ ] 出票记录正确
- [ ] 退款记录正确
- [ ] 通知记录正确

### 10.4 边界检查

- [ ] 空值参数处理
- [ ] 非法值参数处理
- [ ] 超长值参数处理
- [ ] 重复请求处理
- [ ] 并发请求处理
- [ ] 网络超时处理
- [ ] 支付中心异常处理
- [ ] 数据库异常处理

---

## 十一、TVM 联调日志检查点

### 11.1 关键日志片段

```bash
# 下单
1.开始处理提交单程票订单, deviceId=xxx, request=xxx
2.now is xxx orderNo is xxx
3.tvm订单 order is xxx
4.拉码请求 tvmPayUrl is xxx , payCenterRequest is xxx
5.拉码结束 payResponse is xxx

# 支付
1.开始处理TVM扫码支付, deviceId=xxx, request=xxx
4.扫码支付请求 tvmPayUrl is xxx , payCenterRequest is xxx
5.支付中心响应 payResponse=xxx
6.支付中心返回结果为空
7.支付中心返回成功
8.查询到支付成功的结果

# 出票
1.开始处理出票结果通知, deviceId=xxx, request=xxx
2.没有找到匹配的订单
3.开始保存出票主记录, mainTicket=xxx
4.开始保存出票明细记录, 数量=xxx
5.出票结果通知处理完成, orderNo=xxx, buyNum=xxx, actualNum=xxx, refundAmount=xxx

# 故障
1.开始处理出票故障通知, deviceId=xxx, request=xxx
3.开始保存出票故障主记录, mainTicket=xxx
5.出票故障通知处理完成, orderNo=xxx, buyNum=xxx, actualNum=xxx, refundAmount=xxx

# 退款
开始退款处理
购票数量大于实际出票数量，发起退款, orderNo=xxx, buyNum=xxx, actualNum=xxx, refundAmount=xxx
调用支付中心退款接口, refundUrl=xxx, refundRequest=xxx
支付中心退款响应 refundResponse=xxx
退款成功/退款失败
```

---

## 十二、TVM 问题升级路径

| 问题级别 | 描述 | 响应时间 | 处理人 |
|----------|------|----------|--------|
| P0 | 支付中心不可用、数据丢失 | 30 分钟 | 开发负责人 |
| P1 | 核心流程失败（下单/支付/出票） | 1 小时 | 开发工程师 |
| P2 | 边界场景失败、非核心流程 | 2 小时 | 开发工程师 |
| P3 | UI/日志问题、优化建议 | 下一迭代 | 产品/开发 |

### 12.1 常见问题速查

| 问题 | 可能原因 | 排查命令/方法 |
|------|----------|---------------|
| 支付中心返回签名错误 | RSA 私钥错误 | 检查 application.yml private-key |
| 支付中心返回商户号错误 | merchant-no 不匹配 | 检查 merchant-no 配置 |
| 支付 URL 无法访问 | 网络不通 | curl -I http://dtcustomer.bestonepay.com |
| 扫码支付无响应 | 付款码格式错误 | 检查 paymentCode 格式 |
| 轮询一直返回 PROCESSING | 支付中心查询异常 | 查看支付中心查询日志 |
| 退款失败 | 退款参数错误 | 检查 refundRequest 构造 |
| 设备报文格式错误 | FormData 解析失败 | 检查 bizData JSON 格式 |
| 数据库连接失败 | Oracle 配置错误 | 检查 IP/端口/用户名密码 |
| 订单状态不一致 | 数据库事务问题 | 检查 @Transactional 配置 |
| 退款金额错误 | 金额计算逻辑错误 | 检查 handleRefund 方法 |

---

## 十三、TVM 联调回退方案

### 13.1 支付中心回退

- 若支付中心接口不稳定，可临时切换至测试环境
- 配置项：`pay-center-pay-url`、`pay-center-query-url`、`pay-center-refund-url`

### 13.2 设备回退

- 若设备固件有问题，可回退至上一版本
- 保持接口兼容性，不修改已有接口格式

### 13.3 数据修复

- 若订单状态不一致，可通过 SQL 手动修正
- 修复脚本需记录操作日志

---

## 十四、TVM 联调交付物

1. **联调日志**： collect-pay-server 全量日志（DEBUG 级别）
2. **测试用例执行表**： 所有场景执行结果
3. **问题跟踪表**： 问题描述、重现步骤、解决方案、责任人
4. **接口确认文档**： 最终确认的请求/响应格式
5. **性能测试报告**： 并发、响应时间、吞吐量
6. **上线 checklist**： 生产环境配置、密钥、网络
