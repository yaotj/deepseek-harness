# collect-pay-server 微服务文档

> **模块路径**: `collect-pay-server/`
> **端口**: 9096
> **职责**: 取票支付服务，处理 TVM 取票订单的支付请求、支付结果回调、退款等支付相关业务
> **源码阅读范围**: `collect-pay-server/src/main/java/`、`collect-pay-server/src/main/resources/`
> **关联文档**: `docs/02-微服务文档/collect-ticket-server.md`
> **整理时间**: 2026-07-28

---

## 一、模块概述

### 1.1 核心职责

collect-pay-server 是 ITP 平台的取票支付服务，承担以下核心职责：

1. **支付请求处理** - 接收取票订单支付请求，调用支付中心
2. **支付结果回调** - 接收支付中心异步通知，更新订单状态
3. **退款处理** - 处理取票订单退款请求
4. **支付查询** - 查询取票订单支付状态

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| MyBatis | 数据访问层 |
| Oracle | 数据库 |
| OpenFeign | RPC 服务调用 |

### 1.3 端口配置

```properties
server.port=9096
```

### 1.4 依赖下游服务

| 服务 | 调用时机 | 调用方法 |
|------|---------|---------|
| pay-center | 取票支付 | 调用支付中心接口 |
| pay-sign-server | 签名验证 | 验证回调签名 |

---

## 二、接口清单

### 2.1 支付接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| - | 请求支付 | `/ci/app/requestPayForJson` | POST | 接收取票订单支付请求 |
| - | 支付结果通知 | `/ci/app/notifyPayResult` | POST | 接收支付中心回调 |
| - | 查询支付状态 | `/ci/app/queryPayStatus` | POST | 查询支付状态 |

---

## 三、核心业务流程

### 3.1 请求支付流程

1. **接收请求** - 接收 collect-ticket-server 的支付请求
2. **参数校验** - 校验订单号、金额、渠道等
3. **调用支付中心** - 调用 pay-center 创建支付订单
4. **返回结果** - 返回支付信息、签名等

### 3.2 支付结果通知流程

1. **接收通知** - 接收支付中心异步通知
2. **签名验证** - 验证回调签名
3. **更新订单状态** - 更新订单支付状态
4. **通知业务服务** - 通知 collect-ticket-server
5. **返回结果** - 返回成功

### 3.3 退款流程

1. **接收退款请求** - 接收退款请求
2. **调用支付中心** - 调用支付中心退款接口
3. **更新订单状态** - 更新退款状态
4. **返回结果** - 返回退款结果

---

## 四、文件清单

### 4.1 控制器层

| 文件路径 | 说明 |
|---------|------|
| `collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/controller/*.java` | 支付接口入口 |

### 4.2 服务层

| 文件路径 | 说明 |
|---------|------|
| `collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/*.java` | 支付服务 |

### 4.3 数据访问层

| 文件路径 | 说明 |
|---------|------|
| `collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/mapper/*.java` | 支付 Mapper |

### 4.4 资源配置

| 文件路径 | 说明 |
|---------|------|
| `collect-pay-server/src/main/resources/application.properties` | 应用配置 |
| `collect-pay-server/src/main/resources/mapper/*.xml` | MyBatis Mapper XML |

---

## 五、与其他服务的交互

### 5.1 服务调用关系

| 调用方 | 被调用服务 | 调用时机 | 调用方法 |
|--------|-----------|---------|---------|
| collect-ticket-server | collect-pay-server | 取票支付 | `collectPayClient.requestPayForJson()` |
| collect-pay-server | pay-center | 支付请求 | 调用支付中心 |
| pay-center | collect-pay-server | 支付结果回调 | 异步通知 |

---

## 六、相关文档

- `docs/02-微服务文档/collect-ticket-server.md` - 取票订单服务

---

## 七、源码文件索引

### 7.1 Java 源文件

- `collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/controller/*.java`
- `collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/service/*.java`
- `collect-pay-server/src/main/java/com/chinasofti/huateng/collectpay/mapper/*.java`
- `collect-pay-server/src/main/java/com/chinasofti/huateng/CollectPayServer.java`

### 7.2 资源文件

1. `collect-pay-server/src/main/resources/application.properties`
2. `collect-pay-server/src/main/resources/mapper/*.xml`

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/collect-pay-server` 模块源码。
