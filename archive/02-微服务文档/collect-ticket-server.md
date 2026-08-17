# collect-ticket-server 微服务文档

> **模块路径**: `collect-ticket-server/`
> **端口**: 9098
> **职责**: 取票代收服务，处理 TVM 取票订单、支付、取票通知、取消订单等业务
> **源码阅读范围**: `collect-ticket-server/src/main/java/`、`collect-ticket-server/src/main/resources/`
> **整理时间**: 2026-07-28

---

## 一、模块概述

### 1.1 核心职责

collect-ticket-server 是 ITP 平台的取票代收服务，承担以下核心职责：

1. **取票订单创建** - IF8A-20 创建取票订单
2. **取票订单查询** - IF2A-02 查询取票订单状态
3. **取票支付** - IF8A-11 请求支付
4. **取票通知** - IF2A-03 TVM 取票结果通知
5. **取消订单** - IF2A-05 取消取票订单
6. **支付结果通知** - 接收支付中心回调
7. **票价查询** - IF8A-10 计算票价
8. **购买数量限制** - IF8A-09 获取购买最多张数

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
server.port=9098
```

### 1.4 依赖下游服务

| 服务 | 调用时机 | 调用方法 |
|------|---------|---------|
| collect-pay-server | 取票支付 | `collectPayClient.requestPayForJson()` |
| pay-sign-server | 签名验证 | `PaySignUtils.verify()` |

---

## 二、接口清单

### 2.1 取票接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 请求 DTO | 响应 DTO | 说明 |
|---------|---------|---------|---------|---------|---------|------|
| IF8A-09 | 获取购买最多张数 | `/ci/app/requestBuySinlgeTicketMaxNum` | POST | - | `RequestBuySingleTicketMaxNumRespDTO` | 注意：方法名拼写为 Sinlge |
| IF8A-10 | 计算票价 | `/ci/app/requestTicketPriceByStation` | POST | `RequestTicketPriceByStationReqDTO` | `RequestTicketPriceByStationRespDTO` | |
| IF8A-11 | 请求支付 | `/ci/app/requestPaymentInfo` | POST | `RequestPaymentInfoReqDTO` | `RequestPaymentInfoRespDTO` | |
| IF8A-20 | 请求下单 | `/ci/app/requestOrder` | POST | `CreateTicketCollectOrderReqDTO` | `CreateTicketCollectOrderRespDTO` | 创建取票订单 |
| IF2A-02 | 查询取票订单状态 | `/ci/app/queryTicketCollectOrder` | POST | `QueryTicketCollectOrderReqDTO` | `QueryTicketCollectOrderRespDTO` | |
| IF2A-03 | 取票订单通知 | `/ci/app/ticketCollectNotify` | POST | `TicketCollectNotifyReqDTO` | `TicketCollectNotifyRespDTO` | TVM 取票结果通知 |
| IF2A-05 | 取消取票订单 | `/ci/app/cancelTicketCollectOrder` | POST | `CancelTicketCollectOrderReqDTO` | `CancelTicketCollectOrderRespDTO` | |
| - | 支付结果通知 | `/ci/app/ticketCollectPayNotify` | POST | `TicketCollectPayNotifyReqDTO` | `TicketCollectPayNotifyRespDTO` | 支付中心回调 |

**请求格式**：JSON Body（`@RequestBody`）

---

## 三、核心业务流程

### 3.1 IF8A-20 创建取票订单流程

**业务流程**：

1. **参数校验** - 校验必填字段
2. **生成订单号** - 生成唯一订单号
3. **构建订单信息** - 构建 `TicketCollectInfo` 和 `TicketCollectLogs`
4. **插入订单** - 插入订单主表和日志表
5. **返回结果** - 返回订单号、订单状态、票价、二维码生成时间等

**关键代码**：
```java
// TicketCollectServiceImpl.java:176-216
@Override
@Transactional(rollbackFor = Exception.class)
public CreateTicketCollectOrderRespDTO createTicketCollectOrder(CreateTicketCollectOrderReqDTO request) {
    // 1. 参数校验
    // 2. 生成订单号
    String orderNo = generateOrderNo();
    // 3. 构建订单信息
    TicketCollectInfo collectInfo = buildCollectInfo(request, orderNo, now, qrcodeGenDate, randomFact);
    // 4. 插入订单
    ticketCollectInfoMapper.insert(collectInfo);
    // 5. 返回结果
    response.setOrderNo(orderNo);
    response.setOrderStatus(collectInfo.getOrderStatus());
}
```

### 3.2 IF8A-11 请求支付流程

**业务流程**：

1. **参数校验** - 校验 `orderNo`、`channelType`、`payChannelCode` 等
2. **查询订单** - 根据 `orderNo` 查询取票订单
3. **订单状态检查** - 订单必须未支付
4. **组装支付请求** - 调用 `collect-pay-server` 的支付接口
5. **更新订单状态** - 更新支付渠道和支付结果
6. **返回结果** - 返回支付渠道、支付信息、签名等

**关键代码**：
```java
// TicketCollectServiceImpl.java:111-173
@Override
@Transactional(rollbackFor = Exception.class)
public RequestPaymentInfoRespDTO requestPaymentInfo(RequestPaymentInfoReqDTO request) {
    // 1. 参数校验
    // 2. 查询订单
    TicketCollectInfo collectInfo = ticketCollectInfoMapper.selectByOrderNo(request.getOrderNo());
    // 3. 订单状态检查
    if (collectInfo.getOrderStatus() == ORDER_STATUS_PAID) {
        return errorResponse(ORDER_STATUS_ERROR);
    }
    // 4. 组装支付请求
    JSONObject payRequest = new JSONObject();
    payRequest.put("orderNo", collectInfo.getOrderNo());
    payRequest.put("scene", request.getChannelType());
    payRequest.put("paymentVendor", request.getPayChannelCode());
    // 5. 调用支付中心
    JSONObject payResponse = collectPayClient.requestPayForJson(payRequest);
    // 6. 更新订单状态
    collectInfo.setPayResult(PAY_RESULT_PROCESSING);
    // 7. 返回结果
}
```

### 3.3 IF2A-03 取票订单通知流程

**业务流程**：

1. **参数校验** - 校验 `orderNo`、`collectStatus` 等
2. **查询订单** - 根据 `orderNo` 查询取票订单
3. **重复通知检查** - 如果已取票成功，返回错误
4. **更新订单状态** - 更新取票状态、设备ID、取票时间
5. **保存取票明细** - 如果取票成功，保存票卡明细
6. **返回结果** - 返回处理结果

### 3.4 支付结果通知流程

**业务流程**：

1. **参数校验** - 校验 `orderNo` 等必填字段
2. **签名验证** - 验证支付中心回调签名
3. **查询订单** - 根据 `orderNo` 查询取票订单
4. **更新订单状态** - 根据支付结果更新订单状态和支付信息
5. **通知支付服务** - 异步通知 collect-pay-server 支付结果
6. **返回结果** - 返回成功

---

## 四、数据模型

### 4.1 TICKET_COLLECT_INFO（取票订单表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ORDER_NO | VARCHAR2(128) | 订单号（主键） |
| USER_ID | VARCHAR2(128) | 用户ID |
| ENTRY_STATION_CODE | VARCHAR2(32) | 进站车站代码 |
| EXIT_STATION_CODE | VARCHAR2(32) | 出站车站代码 |
| TICKET_PRICE | NUMBER | 票价 |
| SINGEL_TICKET_NUM | NUMBER | 购票数量（注意拼写：Singel） |
| CHANNEL_CODE | VARCHAR2(32) | 渠道代码 |
| ORDER_STATUS | NUMBER | 订单状态：0=未支付，100=已支付，99=超时 |
| COLLECT_STATUS | NUMBER | 取票状态：0=未取票，100=已取票 |
| PAY_RESULT | NUMBER | 支付结果：0=未支付，1=处理中，100=成功 |
| PAY_AMOUNT | NUMBER | 支付金额 |
| PAY_DATE | TIMESTAMP | 支付时间 |
| QRCODE_GEN_DATE | TIMESTAMP | 二维码生成时间 |
| RANDOM_FACT | VARCHAR2(64) | 随机因子 |
| DEVICE_ID | VARCHAR2(32) | 设备ID |
| COLLECT_TMS | TIMESTAMP | 取票时间 |
| CREATE_TMS | TIMESTAMP | 创建时间 |
| UPDATE_TMS | TIMESTAMP | 更新时间 |

### 4.2 TICKET_COLLECT_LOGS（取票订单日志表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ORDER_NO | VARCHAR2(128) | 订单号（主键） |
| ORDER_STATUS | NUMBER | 订单状态 |
| COLLECT_STATUS | NUMBER | 取票状态 |
| ACTUAL_TAKE_TICKET_NUM | NUMBER | 实际取票数量 |
| DEVICE_ID | VARCHAR2(32) | 设备ID |
| COLLECT_TMS | TIMESTAMP | 取票时间 |
| ERROR_CODE | VARCHAR2(32) | 错误码 |
| ERROR_MESSAGE | VARCHAR2(255) | 错误信息 |
| FAULT_SLIP_SEQ | VARCHAR2(64) | 故障票流水号 |

### 4.3 TICKET_COLLECT_LOG_DETAIL（取票明细表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ID | NUMBER | 主键 |
| ORDER_NO | VARCHAR2(128) | 订单号 |
| TICKET_TYPE | VARCHAR2(32) | 票类型 |
| TICKET_PRICE | NUMBER | 票价 |
| CREATE_TMS | TIMESTAMP | 创建时间 |

---

## 五、枚举与常量

### 5.1 CollectTicketErrorCodeEnum

| 错误码 | 含义 | 说明 |
|--------|------|------|
| 0000 | SUCCESS | 成功 |
| 9999 | FAIL | 失败 |
| 8001 | INVALID_PARAM | 无效参数 |
| 2101 | QRCODE_EXPIRED | 二维码已超时 |
| ... | ... | ... |

### 5.2 订单状态

| 状态码 | 含义 |
|--------|------|
| 0 | 未支付 |
| 100 | 已支付 |
| 99 | 超时 |

### 5.3 取票状态

| 状态码 | 含义 |
|--------|------|
| 0 | 未取票 |
| 100 | 已取票 |

---

## 六、文件清单

### 6.1 控制器层

| 文件路径 | 说明 |
|---------|------|
| `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/controller/ci/app/TicketCollectController.java` | 取票接口入口 |

### 6.2 服务层

| 文件路径 | 说明 |
|---------|------|
| `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/service/TicketCollectService.java` | 取票服务接口 |
| `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/service/impl/TicketCollectServiceImpl.java` | 取票服务实现 |

### 6.3 客户端层

| 文件路径 | 说明 |
|---------|------|
| `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/client/CollectPayClient.java` | collect-pay-server RPC 客户端 |
| `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/util/PaySignUtils.java` | 签名验证工具 |

### 6.4 数据访问层

| 文件路径 | 说明 |
|---------|------|
| `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/mapper/TicketCollectInfoMapper.java` | 取票订单 Mapper |
| `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/mapper/TicketCollectLogsMapper.java` | 取票日志 Mapper |
| `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/mapper/TicketCollectLogDetailMapper.java` | 取票明细 Mapper |

### 6.5 实体类

| 文件路径 | 说明 |
|---------|------|
| `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/entity/TicketCollectInfo.java` | 取票订单实体 |
| `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/entity/TicketCollectLogs.java` | 取票日志实体 |
| `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/entity/TicketCollectLogDetail.java` | 取票明细实体 |

### 6.6 资源配置

| 文件路径 | 说明 |
|---------|------|
| `collect-ticket-server/src/main/resources/application.properties` | 应用配置 |
| `collect-ticket-server/src/main/resources/mapper/*.xml` | MyBatis Mapper XML |

---

## 七、与其他服务的交互

### 7.1 服务调用关系

| 调用方 | 被调用服务 | 调用时机 | 调用方法 |
|--------|-----------|---------|---------|
| collect-ticket-server | collect-pay-server | 取票支付 | `collectPayClient.requestPayForJson()` |
| collect-ticket-server | pay-sign-server | 签名验证 | `PaySignUtils.verify()` |

### 7.2 与 ticket-server 的关系

- ticket-server 负责 TVM 取票的票卡状态更新
- collect-ticket-server 负责取票订单的创建、支付、通知

---

## 八、相关文档

- `docs/03-接口文档/接口设计对比/接口规范与项目实现对照表.md` - 接口规范与实现对照

---

## 九、源码文件索引

### 9.1 Java 源文件（共 28 个）

**控制器层 (1 个)**:
1. `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/controller/ci/app/TicketCollectController.java`

**服务层 (2 个)**:
2. `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/service/TicketCollectService.java`
3. `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/service/impl/TicketCollectServiceImpl.java`

**客户端层 (2 个)**:
4. `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/client/CollectPayClient.java`
5. `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/util/PaySignUtils.java`

**数据访问层 (3 个)**:
6. `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/mapper/TicketCollectInfoMapper.java`
7. `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/mapper/TicketCollectLogsMapper.java`
8. `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/mapper/TicketCollectLogDetailMapper.java`

**实体类 (3 个)**:
9. `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/entity/TicketCollectInfo.java`
10. `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/entity/TicketCollectLogs.java`
11. `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/entity/TicketCollectLogDetail.java`

**模型类 (多个)**:
12-27. `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/model/request/*.java`
12-27. `collect-ticket-server/src/main/java/com/chinasofti/huateng/collectticket/model/response/*.java`

**启动类 (1 个)**:
28. `collect-ticket-server/src/main/java/com/chinasofti/huateng/CollectTicketServer.java`

### 9.2 资源文件

1. `collect-ticket-server/src/main/resources/application.properties`
2. `collect-ticket-server/src/main/resources/mapper/*.xml`

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/collect-ticket-server` 模块源码。
