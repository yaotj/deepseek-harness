# blacklist-server 微服务文档

> **模块路径**: `blacklist-server/`
> **端口**: 9095
> **职责**: 黑名单服务，管理支付宝乘车黑名单，处理用户解约和黑名单同步
> **源码阅读范围**: `blacklist-server/src/main/java/`、`blacklist-server/src/main/resources/`
> **关联文档**: `docs/01-项目概述/支付宝乘车业务流.md`
> **整理时间**: 2026-07-28

---

## 一、模块概述

### 1.1 核心职责

blacklist-server 是 ITP 平台的黑名单管理服务，承担以下核心职责：

1. **黑名单管理** - 管理支付宝乘车用户黑名单
2. **解约处理** - 处理用户解约请求
3. **黑名单同步** - 同步黑名单到支付宝/行业服务
4. **黑名单查询** - 查询用户黑名单状态

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| MyBatis | 数据访问层 |
| Oracle | 数据库 |

### 1.3 端口配置

```properties
server.port=9095
```

### 1.4 数据表

| 表名 | 说明 |
|------|------|
| `BLACKLIST` | 黑名单表 |
| `BLACKLIST_OPERATE_LOG` | 黑名单操作日志表 |

---

## 二、接口清单

### 2.1 黑名单接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| IF2B-04 | 解约通知 | `/ci/app/receiveTerminationNotify` | POST | 接收解约通知 |
| IF2B-05 | 黑名单通知 | `/ci/app/notifyBlacklist` | POST | 通知黑名单 |
| IF2A-08 | 查询黑名单 | `/ci/app/queryBlacklist` | POST | 查询用户黑名单状态 |

---

## 三、核心业务流程

### 3.1 解约通知流程

1. **接收通知** - 接收解约通知
2. **参数校验** - 校验通知参数
3. **更新黑名单** - 更新用户黑名单状态
4. **同步通知** - 同步到支付宝/行业服务
5. **返回结果** - 返回成功

### 3.2 黑名单通知流程

1. **接收通知** - 接收黑名单通知
2. **参数校验** - 校验通知参数
3. **保存黑名单** - 保存到 `BLACKLIST` 表
4. **记录操作日志** - 记录到 `BLACKLIST_OPERATE_LOG`
5. **返回结果** - 返回成功

---

## 四、数据模型

### 4.1 BLACKLIST（黑名单表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ID | NUMBER | 主键 |
| CARD_ID | VARCHAR2(16) | 卡号 |
| THIRD_USER_ID | VARCHAR2(8) | 第三方用户ID |
| REASON | VARCHAR2(500) | 黑名单原因 |
| CREATE_TIME | TIMESTAMP | 创建时间 |

### 4.2 BLACKLIST_OPERATE_LOG（黑名单操作日志表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ID | NUMBER | 主键 |
| CARD_ID | VARCHAR2(16) | 卡号 |
| THIRD_USER_ID | VARCHAR2(8) | 第三方用户ID |
| OPERATE_TYPE | VARCHAR2(16) | 操作类型 |
| REASON | VARCHAR2(500) | 操作原因 |
| OPERATE_TIME | TIMESTAMP | 操作时间 |

---

## 五、文件清单

### 5.1 控制器层

| 文件路径 | 说明 |
|---------|------|
| `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/controller/*.java` | 黑名单接口入口 |

### 5.2 服务层

| 文件路径 | 说明 |
|---------|------|
| `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/service/*.java` | 黑名单服务 |

### 5.3 数据访问层

| 文件路径 | 说明 |
|---------|------|
| `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/mapper/*.java` | 黑名单 Mapper |

### 5.4 资源配置

| 文件路径 | 说明 |
|---------|------|
| `blacklist-server/src/main/resources/application.properties` | 应用配置 |

---

## 六、与其他服务的交互

### 6.1 服务调用关系

| 调用方 | 被调用服务 | 调用时机 | 说明 |
|--------|-----------|---------|------|
| blacklist-server | alipay-pay-sign-server | 解约时 | 更新签约状态 |
| blacklist-server | ticket-server | 黑名单同步时 | 同步票卡状态 |

---

## 七、相关文档

- `docs/01-项目概述/支付宝乘车业务流.md` - 支付宝乘车业务流

---

## 八、源码文件索引

### 8.1 Java 源文件

- `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/controller/*.java`
- `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/service/*.java`
- `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/mapper/*.java`
- `blacklist-server/src/main/java/com/chinasofti/huateng/BlacklistServer.java`

### 8.2 资源文件

1. `blacklist-server/src/main/resources/application.properties`
2. `blacklist-server/src/main/resources/mapper/*.xml`

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/blacklist-server` 模块源码。
