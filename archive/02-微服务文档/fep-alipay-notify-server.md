# fep-alipay-notify-server 微服务文档

> **模块路径**: `fep-alipay-notify-server/`
> **端口**: 8080
> **职责**: 支付宝出行通知服务，接收支付宝异步通知并处理
> **源码阅读范围**: `fep-alipay-notify-server/src/main/java/`、`fep-alipay-notify-server/src/main/resources/`
> **关联文档**: `docs/01-项目概述/支付宝乘车业务流.md`
> **整理时间**: 2026-07-28

---

## 一、模块概述

### 1.1 核心职责

fep-alipay-notify-server 是支付宝出行渠道的通知服务，承担以下核心职责：

1. **签约结果通知** - 接收支付宝签约结果异步通知
2. **支付结果通知** - 接收支付宝支付结果异步通知
3. **退款结果通知** - 接收支付宝退款结果异步通知
4. **行业数据通知** - 接收支付宝行业数据异步通知

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |

### 1.3 端口配置

```properties
server.port=8080
```

### 1.4 依赖下游服务

| 服务 | 调用时机 | 调用方法 |
|------|---------|---------|
| alipay-pay-sign-server | 签约/支付/退款通知时 | 更新签约/支付状态 |
| pay-sign-server | 签约/支付/退款通知时 | 更新签约/支付状态 |
| account-server | 开户通知时 | 更新账户状态 |
| ticket-server | 行程数据通知时 | 更新票卡状态 |

---

## 二、接口清单

### 2.1 通知接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| - | 签约结果通知 | `/notify/receiveSignResultFromAlipay` | POST | 接收支付宝签约结果通知 |
| - | 支付结果通知 | `/notify/receivePayResultFromAlipay` | POST | 接收支付宝支付结果通知 |
| - | 退款结果通知 | `/notify/receiveRefundResultFromAlipay` | POST | 接收支付宝退款结果通知 |
| - | 行业数据通知 | `/notify/receiveIndustryDataFromAlipay` | POST | 接收支付宝行业数据通知 |

---

## 三、核心业务流程

### 3.1 签约结果通知流程

1. **接收通知** - 接收支付宝签约结果异步通知
2. **参数校验** - 校验通知参数
3. **转发处理** - 转发到 pay-sign-server 处理
4. **返回结果** - 返回成功确认

### 3.2 支付结果通知流程

1. **接收通知** - 接收支付宝支付结果异步通知
2. **参数校验** - 校验通知参数
3. **转发处理** - 转发到 pay-sign-server 处理
4. **返回结果** - 返回成功确认

### 3.3 退款结果通知流程

1. **接收通知** - 接收支付宝退款结果异步通知
2. **参数校验** - 校验通知参数
3. **转发处理** - 转发到 pay-sign-server 处理
4. **返回结果** - 返回成功确认

---

## 四、文件清单

### 4.1 控制器层

| 文件路径 | 说明 |
|---------|------|
| `fep-alipay-notify-server/src/main/java/com/chinasofti/huateng/fep/alipay/notify/controller/*.java` | 支付宝通知接口入口 |

### 4.2 服务层

| 文件路径 | 说明 |
|---------|------|
| `fep-alipay-notify-server/src/main/java/com/chinasofti/huateng/fep/alipay/notify/service/*.java` | 支付宝通知服务 |

### 4.3 资源配置

| 文件路径 | 说明 |
|---------|------|
| `fep-alipay-notify-server/src/main/resources/application.properties` | 应用配置 |
| `fep-alipay-notify-server/src/main/resources/application.yml` | 应用配置（YAML 格式） |

---

## 五、与其他服务的交互

### 5.1 服务调用关系

| 调用方 | 被调用服务 | 调用时机 | 调用方法 |
|--------|-----------|---------|---------|
| fep-alipay-notify-server | alipay-pay-sign-server | 签约/支付/退款通知 | 更新签约/支付状态 |
| fep-alipay-notify-server | pay-sign-server | 签约/支付/退款通知 | 更新签约/支付状态 |
| fep-alipay-notify-server | account-server | 开户通知 | 更新账户状态 |
| fep-alipay-notify-server | ticket-server | 行程数据通知 | 更新票卡状态 |

---

## 六、相关文档

- `docs/01-项目概述/支付宝乘车业务流.md` - 支付宝乘车业务流

---

## 七、源码文件索引

### 7.1 Java 源文件

- `fep-alipay-notify-server/src/main/java/com/chinasofti/huateng/fep/alipay/notify/controller/*.java`
- `fep-alipay-notify-server/src/main/java/com/chinasofti/huateng/fep/alipay/notify/service/*.java`
- `fep-alipay-notify-server/src/main/java/com/chinasofti/huateng/FepAlipayNotifyServer.java`

### 7.2 资源文件

1. `fep-alipay-notify-server/src/main/resources/application.properties`
2. `fep-alipay-notify-server/src/main/resources/application.yml`

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/fep-alipay-notify-server` 模块源码。
