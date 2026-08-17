# gate-txn-pay-server 微服务文档

> **模块路径**: `gate-txn-pay-server/`
> **端口**: 9106
> **职责**: 闸机过闸扣费服务，处理闸机出站扣费交易，调用 pay-sign-server 完成免密扣款
> **源码阅读范围**: `gate-txn-pay-server/src/main/java/`、`gate-txn-pay-server/src/main/resources/`
> **整理时间**: 2026-07-28

---

## 一、模块概述

### 1.1 核心职责

gate-txn-pay-server 是 ITP 平台的闸机过闸扣费服务，承担以下核心职责：

1. **过闸扣费交易** - 接收闸机出站交易，生成扣费订单
2. **免密扣款** - 调用 pay-sign-server 完成免密支付扣款
3. **日票特殊处理** - 日票卡类型直接标记支付成功，不调用 pay-sign
4. **幂等保护** - 重复交易自动去重，避免重复扣款

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| MyBatis | 数据访问层 |
| Oracle | 数据库 |
| FastJSON | JSON 序列化 |
| OpenFeign | RPC 服务调用 |

### 1.3 端口配置

```properties
server.port=9106
```

### 1.4 依赖下游服务

| 服务 | 调用时机 | 调用方法 |
|------|---------|---------|
| pay-sign-server | 非日票出站扣费 | `paySignClient.requestPay()` |

---

## 二、接口清单

### 2.1 闸机扣费接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 请求 DTO | 响应 DTO | 说明 |
|---------|---------|---------|---------|---------|---------|------|
| - | 过闸扣费交易 | `/ci/gateTxnPay/requestPay` | POST | `GateTxnPayReqDTO` | `GateTxnPayRespDTO` | 接收闸机出站交易 |

**请求格式**：JSON Body（`@RequestBody`）

---

## 三、核心业务流程

### 3.1 过闸扣费交易流程

**调用链路**：
```
闸机
  → gate-txn-pay-server (/ci/gateTxnPay/requestPay)
    → GateTxnPayServiceImpl.requestPay()
      → 1. 参数校验（只处理出站交易 trxType=02/03）
      → 2. 构建本地订单
      → 3. 幂等检查（按 bizKey 查询）
      → 4. 插入订单
      → 5. 日票特殊处理：直接标记 SUCCESS
      → 6. 非日票：调用 pay-sign 免密扣款
      → 7. 返回结果
```

**业务逻辑**：

1. **参数校验**：
   - 请求报文不能为空
   - `trxType` 必须为 `02` 或 `03`（只处理出站扣费，进站交易只更新状态不扣款）
   - `cardId` 不能为空
   - `handleDateTime` 不能为空且长度不小于 8

2. **构建本地订单**：
   - `orderNo`：`GT` + 时间戳(17位) + 卡号后6位
   - `thirdUserId`：`itpUserId`（16进制转十进制）
   - `trxAmount`：实际交易金额（分）
   - `overtimeAmount`：超时金额（分）
   - `totalAmount`：`trxAmount + overtimeAmount`
   - `txnDate`：`handleDateTime` 前 8 位

3. **幂等检查**：
   - 按 `cardId + trxType + outTime + ticketTransSeq + deviceId + txnDate` 查询
   - 已存在则复用已有订单，不重复插入
   - 插入时捕获 `DuplicateKeyException` 再查询一次

4. **日票特殊处理**：
   - 卡类型为 `0445/0446/0447/0448` 时，直接标记支付状态为 `SUCCESS`
   - 不调用 pay-sign-server
   - 备注："日票交易默认支付成功"

5. **非日票扣款**：
   - 调用 `paySignClient.requestPay()` 发起免密扣款
   - 组装 `GatePayRequestDTO`，包含 `scene`、`industryType`、`subject`、`body`、`orderTimeOut`
   - `industryDetail` 存储完整订单 JSON 快照
   - 成功则状态为 `PROCESSING`，失败则为 `RETRY`

6. **返回结果**：
   - `retCode`：`0000`
   - `retMsg`：`成功`
   - `orderNo`：订单号
   - `payStatus`：`SUCCESS/PROCESSING/RETRY`

**关键代码**：
```java
// GateTxnPayServiceImpl.java:49-93
@Override
@Transactional(rollbackFor = Exception.class)
public GateTxnPayRespDTO requestPay(GateTxnPayReqDTO request) {
    // 1. 参数校验
    // 2. 构建订单
    GateTxnPay order = buildOrder(request);
    // 3. 幂等检查
    GateTxnPay existing = gateTxnPayMapper.selectByBizKey(...);
    // 4. 插入订单
    gateTxnPayMapper.insert(order);
    // 5. 日票/非日票处理
    if (isDailyTicket(order)) {
        nextStatus = "SUCCESS";
    } else {
        RequestPayResult payResult = requestPaySign(order);
        nextStatus = isSuccess(payResult) ? "PROCESSING" : "RETRY";
    }
    // 6. 返回结果
    response.setOrderNo(order.getOrderNo());
    response.setPayStatus(nextStatus);
}
```

---

## 四、数据模型

### 4.1 GateTxnPay（过闸扣费订单表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ORDER_NO | VARCHAR2(128) | 订单号（主键），格式：GT + 时间戳 + 卡号后6位 |
| DEBIT_STATUS | VARCHAR2(32) | 扣款状态：INIT/PROCESSING/SUCCESS/FAILED/RETRY |
| THIRD_USER_ID | VARCHAR2(128) | 第三方用户ID（itpUserId 十进制） |
| CARD_ID | VARCHAR2(64) | 卡号 |
| CARD_TYPE | VARCHAR2(16) | 卡类型 |
| DEVICE_ID | VARCHAR2(32) | 设备ID |
| TRX_TYPE | VARCHAR2(8) | 交易类型：02出站、03异常出站 |
| TICKET_TRANS_SEQ | VARCHAR2(128) | 票卡交易流水号 |
| IN_STATION | VARCHAR2(32) | 进站车站（lastHandleStationCode） |
| IN_TIME | VARCHAR2(28) | 进站时间（lastHandleDateTime） |
| OUT_STATION | VARCHAR2(32) | 出站车站（handleStationCode） |
| OUT_TIME | VARCHAR2(28) | 出站时间（handleDateTime） |
| TXN_DATE | VARCHAR2(16) | 交易日期（handleDateTime 前8位） |
| TRX_AMOUNT | NUMBER | 实际交易金额（分） |
| OVERTIME_AMOUNT | NUMBER | 超时金额（分） |
| TOTAL_AMOUNT | NUMBER | 总金额 = trxAmount + overtimeAmount |
| ISSUE_CHANNEL_CODE | VARCHAR2(16) | 发卡渠道编码 |
| SIGN_CHANNEL_CODE | VARCHAR2(16) | 签约渠道编码 |
| CREATE_TIME | TIMESTAMP | 创建时间 |
| UPDATE_TIME | TIMESTAMP | 更新时间 |
| REMARK | VARCHAR2(255) | 备注（支付状态描述） |

---

## 五、配置说明

### 5.1 业务配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `gate.pay.scene` | `AGM_GATE` | 支付场景 |
| `gate.pay.industry-type` | `1` | 行业类型 |
| `gate.pay.subject` | `地铁乘车扣费` | 支付标题 |
| `gate.pay.body` | `地铁乘车费用` | 支付描述 |
| `gate.pay.order-timeout-seconds` | `60` | 订单超时时间（秒） |

### 5.2 日票卡类型

| 卡类型 | 说明 |
|--------|------|
| `0445` | 一日票 |
| `0446` | 三日票 |
| `0447` | 七日票 |
| `0448` | 月票 |

---

## 六、文件清单

### 6.1 控制器层

| 文件路径 | 说明 |
|---------|------|
| `gate-txn-pay-server/src/main/java/com/chinasofti/huateng/gatetxnpay/controller/GateTxnPayController.java` | 过闸扣费接口入口 |

### 6.2 服务层

| 文件路径 | 说明 |
|---------|------|
| `gate-txn-pay-server/src/main/java/com/chinasofti/huateng/gatetxnpay/service/GateTxnPayService.java` | 过闸扣费服务接口 |
| `gate-txn-pay-server/src/main/java/com/chinasofti/huateng/gatetxnpay/service/impl/GateTxnPayServiceImpl.java` | 过闸扣费服务实现 |

### 6.3 数据访问层

| 文件路径 | 说明 |
|---------|------|
| `gate-txn-pay-server/src/main/java/com/chinasofti/huateng/gatetxnpay/mapper/GateTxnPayMapper.java` | 过闸扣费订单 Mapper |

### 6.4 实体类

| 文件路径 | 说明 |
|---------|------|
| `gate-txn-pay-server/src/main/java/com/chinasofti/huateng/gatetxnpay/entity/GateTxnPay.java` | 过闸扣费订单实体 |

### 6.5 资源配置

| 文件路径 | 说明 |
|---------|------|
| `gate-txn-pay-server/src/main/resources/application.properties` | 应用配置 |
| `gate-txn-pay-server/src/main/resources/mapper/GateTxnPayMapper.xml` | 过闸扣费订单 SQL |

---

## 七、与其他服务的交互

### 7.1 服务调用关系

| 调用方 | 被调用服务 | 调用时机 | 调用方法 |
|--------|-----------|---------|---------|
| gate-txn-pay-server | pay-sign-server | 非日票出站扣费 | `paySignClient.requestPay()` |

### 7.2 与 ticket-server 的关系

- ticket-server 负责闸机检票通知的业务逻辑（`TicketRideStatusServiceImpl`）
- gate-txn-pay-server 只处理出站扣费，进站交易不进入扣款链路

---

## 八、相关文档

- `docs/03-接口文档/接口设计对比/接口规范与项目实现对照表.md` - 接口规范与实现对照

---

## 九、源码文件索引

### 9.1 Java 源文件（共 5 个）

1. `gate-txn-pay-server/src/main/java/com/chinasofti/huateng/GateTxnPayServer.java`
2. `gate-txn-pay-server/src/main/java/com/chinasofti/huateng/gatetxnpay/controller/GateTxnPayController.java`
3. `gate-txn-pay-server/src/main/java/com/chinasofti/huateng/gatetxnpay/service/GateTxnPayService.java`
4. `gate-txn-pay-server/src/main/java/com/chinasofti/huateng/gatetxnpay/service/impl/GateTxnPayServiceImpl.java`
5. `gate-txn-pay-server/src/main/java/com/chinasofti/huateng/gatetxnpay/mapper/GateTxnPayMapper.java`
6. `gate-txn-pay-server/src/main/java/com/chinasofti/huateng/gatetxnpay/entity/GateTxnPay.java`

### 9.2 资源文件

1. `gate-txn-pay-server/src/main/resources/application.properties`
2. `gate-txn-pay-server/src/main/resources/mapper/GateTxnPayMapper.xml`

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/gate-txn-pay-server` 模块源码。
