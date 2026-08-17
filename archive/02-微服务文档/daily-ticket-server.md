# daily-ticket-server 微服务文档

> **模块路径**: `daily-ticket-server/`
> **端口**: 9108
> **职责**: 日票服务，处理日票激活、日票订单、日票状态管理、日票支付、日票退款
> **源码阅读范围**: `daily-ticket-server/src/main/java/`、`daily-ticket-server/src/main/resources/`
> **整理时间**: 2026-07-28

---

## 一、模块概述

### 1.1 核心职责

daily-ticket-server 是 ITP 平台的日票服务，承担以下核心职责：

1. **日票激活** - 日票激活请求处理
2. **日票下单** - 日票订单生成
3. **日票订单号** - 日票订单号下发
4. **日票支付** - 日票支付请求处理
5. **日票支付结果查询** - 查询日票支付结果
6. **日票退款** - 日票退款请求处理
7. **日票取消订单** - 取消日票订单
8. **日票状态管理** - 日票状态查询与更新
9. **通知 ACC** - 通知 ACC 车票已使用

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| MyBatis | 数据访问层 |
| Oracle | 数据库 |

### 1.3 端口配置

```properties
server.port=9108
```

### 1.4 数据表

| 表名 | 说明 |
|------|------|
| `DAILY_TICKET_ORDER` | 日票订单表 |
| `DAILY_TICKET_STATUS` | 日票状态表 |
| `DAILY_TICKET_INSTANCE` | 日票实例表 |
| `DAILY_TICKET_PAY_LOG` | 日票支付日志表 |
| `DAILY_TICKET_REFUND` | 日票退款表 |

---

## 二、接口清单

### 2.1 日票接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 请求 DTO | 响应 DTO | 说明 |
|---------|---------|---------|---------|---------|---------|------|
| IF8A-60 | 日票下单 | `/ci/app/dailyTicket/activate` | POST | `DailyTicketActivateReqDTO` | `DailyTicketBaseResult` | |
| IF8A-61 | 日票支付 | `/ci/app/dailyTicket/order` | POST | `DailyTicketOrderReqDTO` | `DailyTicketBaseResult` | |
| IF8A-62 | 日票支付结果查询 | `/ci/app/dailyTicket/orderNo` | POST | `DailyTicketOrderNoReqDTO` | `DailyTicketBaseResult` | |
| IF8A-64 | 日票退款 | `/ci/app/dailyTicket/refund` | POST | `DailyTicketOrderNoReqDTO` | `DailyTicketRefundResult` | |
| IF8A-65 | 日票取消订单 | `/ci/app/dailyTicket/cancel` | POST | `DailyTicketOrderNoReqDTO` | `DailyTicketBaseResult` | |
| IF8A-67 | 日票激活 | `/ci/app/dailyTicket/activate` | POST | `DailyTicketActivateReqDTO` | `DailyTicketBaseResult` | |
| IF8A-71 | 通知 ACC 车票已使用 | `/ci/app/dailyTicket/updateAndNotice` | POST | `DailyTicketUsedNoticeReqDTO` | `DailyTicketBaseResult` | |

---

## 三、核心业务流程

### 3.1 日票激活流程

**业务流程**：

1. **参数校验** - 校验 `thirdUserId`、`cardId`、`cardType` 是否为空
2. **检查日票状态** - 查询 `DAILY_TICKET_STATUS` 检查是否已激活
3. **激活日票** - 更新日票状态为已激活，记录激活时间
4. **返回结果** - 返回激活结果

### 3.2 日票下单流程

**业务流程**：

1. **参数校验** - 校验 `thirdUserId`、`cardId`、`cardType`、`ticketType` 是否为空
2. **生成订单** - 创建日票订单，生成订单号
3. **调用支付** - 调用支付中心完成支付
4. **返回结果** - 返回订单号和支付二维码

### 3.3 日票订单号流程

**业务流程**：

1. **参数校验** - 校验 `thirdUserId`、`cardId`、`cardType` 是否为空
2. **查询订单** - 查询日票订单
3. **返回订单号** - 返回订单号

---

## 四、数据模型

### 4.1 DAILY_TICKET_ORDER（日票订单表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ORDER_NO | VARCHAR2(128) | 订单号 |
| THIRD_USER_ID | VARCHAR2(128) | 第三方用户ID |
| CARD_ID | VARCHAR2(128) | 卡号 |
| CARD_TYPE | VARCHAR2(64) | 卡类型 |
| TICKET_TYPE | VARCHAR2(32) | 日票类型 |
| STATUS | VARCHAR2(32) | 订单状态 |
| AMOUNT | NUMBER | 订单金额 |
| PAY_TIME | TIMESTAMP | 支付时间 |
| CREATE_TIME | TIMESTAMP | 创建时间 |

### 4.2 DAILY_TICKET_STATUS（日票状态表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| CARD_ID | VARCHAR2(128) | 卡号 |
| THIRD_USER_ID | VARCHAR2(128) | 第三方用户ID |
| STATUS | VARCHAR2(32) | 日票状态 |
| ACTIVATE_TIME | TIMESTAMP | 激活时间 |
| EXPIRE_TIME | TIMESTAMP | 过期时间 |
| CREATE_TIME | TIMESTAMP | 创建时间 |

---

## 五、文件清单

### 5.1 控制器层

| 文件路径 | 说明 |
|---------|------|
| `daily-ticket-server/src/main/java/com/chinasofti/huateng/dailyticket/controller/DailyTicketController.java` | 日票接口入口 |

### 5.2 服务层

| 文件路径 | 说明 |
|---------|------|
| `daily-ticket-server/src/main/java/com/chinasofti/huateng/dailyticket/service/DailyTicketService.java` | 日票服务接口 |
| `daily-ticket-server/src/main/java/com/chinasofti/huateng/dailyticket/service/impl/DailyTicketServiceImpl.java` | 日票服务实现 |

### 5.3 资源配置

| 文件路径 | 说明 |
|---------|------|
| `daily-ticket-server/src/main/resources/application.properties` | 应用配置 |
| `daily-ticket-server/src/main/resources/mapper/*.xml` | MyBatis Mapper XML |

---

## 六、与其他服务的交互

### 6.1 服务调用关系

| 调用方 | 被调用服务 | 调用时机 | 说明 |
|--------|-----------|---------|------|
| daily-ticket-server | pay-sign-server | 日票支付时 | 调用支付接口 |
| daily-ticket-server | ticket-server | 激活成功后 | 更新票卡状态 |

---

## 七、相关文档

- `docs/01-项目概述/支付宝乘车业务流.md` - 支付宝乘车业务流

---

## 八、源码文件索引

### 8.1 Java 源文件（共 4 个）

1. `daily-ticket-server/src/main/java/com/chinasofti/huateng/dailyticket/controller/DailyTicketController.java`
2. `daily-ticket-server/src/main/java/com/chinasofti/huateng/dailyticket/service/DailyTicketService.java`
3. `daily-ticket-server/src/main/java/com/chinasofti/huateng/dailyticket/service/impl/DailyTicketServiceImpl.java`
4. `daily-ticket-server/src/main/java/com/chinasofti/huateng/DailyTicketServer.java`

### 8.2 资源文件

1. `daily-ticket-server/src/main/resources/application.properties`
2. `daily-ticket-server/src/main/resources/mapper/*.xml`

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/daily-ticket-server` 模块源码。
