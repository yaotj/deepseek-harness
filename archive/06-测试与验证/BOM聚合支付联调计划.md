# BOM 半自动售票机聚合支付联调计划

> 服务：collect-pay-server  
> 对接方：第三方支付中心（BestOnePay）  
> 涉及设备：BOM 半自动售票机  
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
| 设备对接 | BOM 厂商 | 设备报文格式、交互流程 |
| 服务端联调 | 开发团队 | collect-pay-server 接口实现、数据库、日志 |
| 测试验证 | 测试团队 | 用例执行、异常场景覆盖 |

---

## 二、BOM 接口清单

### 2.1 核心接口

| 接口编号 | 接口名称 | 路径 | 请求方式 | 说明 |
|---------|---------|------|----------|------|
| IF8A-04 | 请求非现金收款下单 | `/itpbom/ci/bom/requestGenNoCashOrder` | POST | BOM 无现金下单 |
| IF8A-05 | 扫码支付 | `/itpbom/ci/bom/requestPayment` | POST | BOM 扫码支付 |
| IF8A-06 | 查询支付结果 | `/itpbom/ci/bom/requestGetPayResult` | POST | 轮询查询支付结果 |
| IF2A-08 | 业务操作结果通知 | `/itpbom/ci/bom/notiBusResult` | POST | 接收 BOM 业务结果 |

### 2.2 请求格式

使用 `application/x-www-form-urlencoded`（FormData）格式：

```
providerId=01&charset=UTF-8&format=json&timestamp=xxx&deviceId=xxx&signType=00&sign=&bizData={...}
```

控制器使用 `@ModelAttribute BaseRequestDTO` 接收，解析 `bizData` JSON 后转发。

---

## 三、BOM 核心业务流程

### 3.1 非现金业务支付流程

1. **下单**：生成订单号（`03` + yyyyMMddHHmmss + 8位随机字符），保存 BOM_NO_CASH_ORDER
2. **扫码支付**：BOM 扫描用户付款码，调用支付中心支付接口
3. **查询结果**：BOM 轮询查询支付结果
4. **业务结果通知**：BOM 业务完成后回告平台
   - SUCCESS：更新通知状态
   - FAILED：发起退款，更新原订单状态

### 3.2 退款流程

1. **自动退款**：业务操作失败时自动触发退款
2. **退款单号**：`R` + yyyyMMddHHmmss + 8位随机字符
3. **退款记录**：保存到 BOM_REFUND_ORDER 表，更新原订单 rsv2 字段

---

## 四、BOM 数据模型

### 4.1 BOM 相关表

| 表名 | 说明 |
|------|------|
| BOM_NO_CASH_ORDER | BOM 无现金订单表 |
| BOM_BUS_RESULT | BOM 业务操作结果通知表 |
| BOM_REFUND_ORDER | BOM 退款订单表 |

### 4.2 订单号生成规则

| 业务 | 格式 | 示例 |
|------|------|------|
| BOM 无现金 | `03` + yyyyMMddHHmmss + 8位随机字符 | `0320260720101234567890abcdef` |
| 退款 | `R` + yyyyMMddHHmmss + 8位随机字符 | `R20260720101234567890abcdef` |
| 通知ID | `N` + yyyyMMddHHmmss + 8位随机字符 | `N20260720101234567890abcdef` |

---

## 五、BOM 接口边界联调场景

### 5.1 请求非现金收款下单（requestGenNoCashOrder）

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| BOM-01 | 正常下单 | 合法参数 | 返回 orderNo | BOM_NO_CASH_ORDER 入库 |
| BOM-02 | deviceId 为空 | deviceId="" | 返回 FAILED | 参数校验 |
| BOM-03 | transType 为空 | transType="" | 返回 FAILED | 参数校验 |
| BOM-04 | transAount 为空 | transAount="" | 返回 FAILED | 参数校验 |
| BOM-05 | transAount 为 0 | transAount=0 | 返回 FAILED | 参数校验 |
| BOM-06 | transAount 为负 | transAount=-1 | 返回 FAILED | 参数校验 |
| BOM-07 | operaterId 为空 | operaterId="" | 返回 FAILED | 参数校验 |
| BOM-08 | shiftId 为空 | shiftId="" | 返回 FAILED | 参数校验 |
| BOM-09 | 数据库异常 | 模拟 Oracle 故障 | 返回 FAILED | 异常捕获 |

### 5.2 扫码支付（requestPayment）

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| BOM-10 | 正常支付 | 合法参数 | 返回 SUCCESS | BOM_NO_CASH_ORDER 状态更新 |
| BOM-11 | 订单不存在 | orderNo 不存在 | 返回 FAILED | msg="订单号错误" |
| BOM-12 | 订单已支付 | 重复支付 | 返回 SUCCESS | 不调用支付中心 |
| BOM-13 | 订单已失败 | 已失败订单 | 返回 FAILED | 不调用支付中心 |
| BOM-14 | paymentCode 为空 | paymentCode="" | 返回 FAILED | 参数校验 |
| BOM-15 | paymentVendor 为空 | paymentVendor="" | 返回 FAILED | 参数校验 |
| BOM-16 | 支付中心超时 | 模拟超时 | 返回 FAILED | 数据库状态=FAILED |
| BOM-17 | 支付中心返回空 | 模拟空响应 | 返回 FAILED | 数据库状态=FAILED |
| BOM-18 | 支付中心返回失败 | 模拟 code≠200 | 返回 PROCESSING | 状态保持 PAYING |

### 5.3 查询支付结果（requestGetPayResult）

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| BOM-19 | 查询成功 | 订单已支付 | 返回 SUCCESS | 不调用支付中心 |
| BOM-20 | 查询失败 | 订单已失败 | 返回 FAILED | 不调用支付中心 |
| BOM-21 | 查询处理中 | 订单 PAYING | 调用支付中心 | 根据查询结果更新 |
| BOM-22 | 订单不存在 | orderNo 不存在 | 返回 FAILED | msg="订单号错误" |

### 5.4 业务操作结果通知（notiBusResult）

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| BOM-23 | 业务成功 | optResult=SUCCESS | 返回 SUCCESS | 通知状态=处理成功 |
| BOM-24 | 业务失败-退款成功 | optResult=FAILED，退款成功 | 返回 SUCCESS | BOM_REFUND_ORDER 状态=SUCCESS |
| BOM-25 | 业务失败-退款失败 | optResult=FAILED，退款失败 | 返回 SUCCESS | BOM_REFUND_ORDER 状态=FAILED |
| BOM-26 | 业务失败-退款返回空 | optResult=FAILED，退款返回空 | 返回 SUCCESS | BOM_REFUND_ORDER 状态=FAILED |
| BOM-27 | 订单不存在 | orderNo 不存在 | 返回 FAILED | msg="订单号错误" |
| BOM-28 | optResult 为空 | optResult="" | 返回 FAILED | 参数校验 |
| BOM-29 | deviceId 为空 | deviceId="" | 返回 FAILED | 参数校验 |
| BOM-30 | 重复通知 | 同一订单多次通知 | 返回 SUCCESS | 幂等性，新增通知记录 |

---

## 六、BOM 设备端边界场景

| 场景ID | 场景描述 | 设备操作 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| BOM-D-01 | 正常扫码支付 | 选择业务→扫码→成功 | 业务成功 | 全流程验证 |
| BOM-D-02 | 支付后业务失败 | 支付成功→业务处理失败 | 触发退款 | 退款金额=订单金额 |
| BOM-D-03 | 支付后设备重启 | 支付成功后重启 | 重启后查询状态=SUCCESS | 数据持久化 |
| BOM-D-04 | 网络中断恢复 | 支付时断网→恢复 | 重试后支付成功 | 网络容错 |
| BOM-D-05 | 重复支付 | 同一订单多次扫码 | 第二次返回已支付 | 幂等性 |
| BOM-D-06 | 支付超时 | 轮询超时 | 返回 PROCESSING | 设备自行处理 |
| BOM-D-07 | 业务取消 | 支付后取消业务 | 触发退款 | 退款金额=订单金额 |
| BOM-D-08 | 业务部分成功 | 部分处理成功 | 按实际结果通知 | 状态正确 |
| BOM-D-09 | 通知丢失 | 业务结果通知丢失 | 订单状态保持 PAYING | 对账机制 |
| BOM-D-10 | 通知重复 | 同一通知多次发送 | 幂等处理 | 不重复退款 |
| BOM-D-11 | 金额为 0 | 业务金额 0 | 返回 FAILED | 参数校验 |
| BOM-D-12 | 金额超大 | 业务金额 999999 | 返回 FAILED | 参数校验 |
| BOM-D-13 | 操作员 ID 为空 | operaterId="" | 返回 FAILED | 参数校验 |
| BOM-D-14 | 班次 ID 为空 | shiftId="" | 返回 FAILED | 参数校验 |
| BOM-D-15 | 业务类型切换 | 不同业务类型混用 | 各业务独立处理 | 业务隔离 |

---

## 七、BOM 支付中心接口边界场景

### 7.1 扫码支付边界

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| P-01 | 支付成功 | status=SUCCESS | 返回 SUCCESS | BOM_NO_CASH_ORDER 状态=SUCCESS |
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

### 7.2 查询支付结果边界

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| Q-01 | 订单已支付 | 数据库状态=SUCCESS | 直接返回 SUCCESS | 不调用支付中心 |
| Q-02 | 订单已失败 | 数据库状态=FAILED | 直接返回 FAILED | 不调用支付中心 |
| Q-03 | 支付中状态 | 数据库状态=PAYING | 调用支付中心查询 | 根据查询结果更新 |
| Q-04 | 支付中心返回成功 | status=SUCCESS | 返回 SUCCESS | 数据库状态更新 |
| Q-05 | 支付中心返回失败 | status=FAILED | 返回 FAILED | 数据库状态更新 |
| Q-06 | 支付中心返回处理中 | status=PROCESSING | 返回 PROCESSING | 数据库状态保持 PAYING |
| Q-07 | 订单不存在 | orderNo 不存在 | 返回 FAILED | 不调用支付中心 |
| Q-08 | 支付中心返回空 | 模拟空响应 | 返回 PROCESSING | 数据库状态保持 PAYING |
| Q-09 | 支付中心返回非 200 | 模拟 code=500 | 返回 PROCESSING | 数据库状态保持 PAYING |
| Q-10 | 轮询超时 | 轮询 3 分钟 | 保持 PROCESSING | 设备侧自行处理超时 |

### 7.3 退款边界

| 场景ID | 场景描述 | 请求参数 | 预期结果 | 验证点 |
|--------|----------|----------|----------|--------|
| R-01 | 退款成功 | 合法退款请求 | 返回 SUCCESS | BOM_REFUND_ORDER 状态=SUCCESS |
| R-02 | 退款失败 | 模拟退款失败 | 返回 FAILED | BOM_REFUND_ORDER 状态=FAILED |
| R-03 | 原订单不存在 | orderNo 不存在 | 返回 FAILED | 不调用支付中心 |
| R-04 | 退款金额为 0 | refundAmount=0 | 返回 FAILED | 参数校验 |
| R-05 | 退款金额超过订单 | refundAmount > 订单金额 | 返回 FAILED | 参数校验 |
| R-06 | 重复退款 | 同一订单多次退款 | 第二次退款 | 幂等性处理 |
| R-07 | 支付中心返回空 | 模拟空响应 | 返回 FAILED | BOM_REFUND_ORDER 状态=FAILED |
| R-08 | 网络超时 | 调用超时 | 返回 FAILED | 超时时间 15s |

---

## 八、BOM 数据一致性验证

### 8.1 订单状态一致性

| 场景ID | 场景描述 | 验证方法 | 预期结果 |
|--------|----------|----------|----------|
| DC-01 | 支付成功状态 | 查询数据库 | BOM_NO_CASH_ORDER.status=SUCCESS |
| DC-02 | 支付失败状态 | 查询数据库 | BOM_NO_CASH_ORDER.status=FAILED |
| DC-03 | 处理中状态 | 轮询中 | BOM_NO_CASH_ORDER.status=PAYING |
| DC-04 | 通知状态 | 查询数据库 | BOM_BUS_RESULT.status=处理成功/失败 |
| DC-05 | 退款状态 | 查询数据库 | BOM_REFUND_ORDER.status=SUCCESS/FAILED |
| DC-06 | 退款订单号关联 | 查询数据库 | BOM_NO_CASH_ORDER.rsv2=退款订单号 |

### 8.2 金额一致性

| 场景ID | 场景描述 | 验证方法 | 预期结果 |
|--------|----------|----------|----------|
| DC-07 | 退款金额计算 | 业务失败退款 | 退款金额=订单金额 |
| DC-08 | 退款金额精度 | 金额 2.5 元 | 退款金额=250（分） |
| DC-09 | 退款金额上限 | 退款金额≤订单金额 | 不超过订单总金额 |

### 8.3 时间一致性

| 场景ID | 场景描述 | 验证方法 | 预期结果 |
|--------|----------|----------|----------|
| DC-10 | 订单创建时间 | 查询数据库 | createTime 正确 |
| DC-11 | 订单更新时间 | 查询数据库 | updateTime 正确 |
| DC-12 | 通知时间 | 查询数据库 | createTime 正确 |
| DC-13 | 退款时间 | 查询数据库 | refundTime 正确 |

---

## 九、BOM 并发与压力边界

| 场景ID | 场景描述 | 测试方法 | 预期结果 |
|--------|----------|----------|----------|
| CP-01 | 同一订单并发支付 | 两个请求同时支付 | 只有一个成功，另一个返回已支付 |
| CP-02 | 同一订单并发查询 | 多个请求同时查询 | 返回结果一致 |
| CP-03 | 同一订单并发通知 | 多个通知同时到达 | 幂等处理，不重复退款 |
| CP-04 | 批量订单并发 | 100 个订单同时支付 | 全部正确处理 |
| CP-05 | 支付中心限流 | 模拟限流 | 返回 FAILED，记录日志 |
| CP-06 | 数据库连接池耗尽 | 模拟连接池满 | 返回 FAILED，记录日志 |

---

## 十、BOM 联调检查清单

### 10.1 环境检查

- [ ] collect-pay-server 可正常启动（端口 8080）
- [ ] Oracle 数据库连接正常（130.251.235.198:1521/qditp）
- [ ] 支付中心网络连通（dtcustomer.bestonepay.com）
- [ ] RSA 密钥配置正确（商户私钥、支付中心公钥）
- [ ] 回调地址在支付中心配置正确（localhost:9098）
- [ ] 日志级别为 DEBUG

### 10.2 接口检查

- [ ] BOM 下单接口路径正确（/itpbom/ci/bom/requestGenNoCashOrder）
- [ ] BOM 支付接口路径正确（/itpbom/ci/bom/requestPayment）
- [ ] BOM 查询接口路径正确（/itpbom/ci/bom/requestGetPayResult）
- [ ] BOM 业务通知接口路径正确（/itpbom/ci/bom/notiBusResult）
- [ ] 请求方式为 POST
- [ ] Content-Type 为 application/x-www-form-urlencoded
- [ ] bizData 为 JSON 字符串

### 10.3 数据检查

- [ ] 订单号生成规则正确（03+时间+随机）
- [ ] 订单号唯一性（数据库约束）
- [ ] 支付订单状态正确
- [ ] 通知记录正确
- [ ] 退款记录正确

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

## 十一、BOM 联调日志检查点

### 11.1 关键日志片段

```bash
# 下单
1.开始处理BOM非现金收款下单, deviceId=xxx, request=xxx
2.当前时间=xxx, 生成订单号=xxx
3.构建BOM订单信息=xxx

# 支付
1.开始处理BOM扫码支付, deviceId=xxx, request=xxx
4.拉码请求 bomPayUrl is xxx , payCenterRequest is xxx
5.支付中心响应 payResponse=xxx
6.支付中心返回结果为空
7.支付中心返回成功
8.查询到支付成功的结果

# 查询
1.开始处理BOM查询支付结果, deviceId=xxx, request=xxx
3.tvmQueryUrl is xxx , queryPayRequest is xxx
queryPayResponse is xxx

# 业务通知
1.开始处理BOM业务操作结果通知, deviceId=xxx, request=xxx
2.业务操作结果通知记录已入库, notifyId=xxx
3.业务操作成功/业务操作失败，发起退款
4.调用支付中心退款接口, refundUrl=xxx, refundRequest=xxx
5.支付中心退款响应 refundResponse=xxx
6.退款成功/退款失败
7.BOM业务操作结果通知处理完成
```

---

## 十二、BOM 问题升级路径

| 问题级别 | 描述 | 响应时间 | 处理人 |
|----------|------|----------|--------|
| P0 | 支付中心不可用、数据丢失 | 30 分钟 | 开发负责人 |
| P1 | 核心流程失败（下单/支付/通知） | 1 小时 | 开发工程师 |
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
| 通知丢失 | 网络问题 | 检查 BOM_BUS_RESULT 表 |

---

## 十三、BOM 联调回退方案

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

## 十四、BOM 联调交付物

1. **联调日志**： collect-pay-server 全量日志（DEBUG 级别）
2. **测试用例执行表**： 所有场景执行结果
3. **问题跟踪表**： 问题描述、重现步骤、解决方案、责任人
4. **接口确认文档**： 最终确认的请求/响应格式
5. **性能测试报告**： 并发、响应时间、吞吐量
6. **上线 checklist**： 生产环境配置、密钥、网络
