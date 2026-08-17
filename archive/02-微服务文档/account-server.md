# account-server 微服务文档

> **模块路径**: `account-server/`
> **端口**: 9097
> **职责**: ITP 账户服务，处理用户开户、支付通道管理、HCE 数据更新、用户信息查询
> **源码阅读范围**: `account-server/src/main/java/`、`account-server/src/main/resources/`
> **整理时间**: 2026-07-28

---

## 一、模块概述

### 1.1 核心职责

account-server 是 ITP 平台的账户核心服务，承担以下核心职责：

1. **用户开户** - IF8A-01 请求开户（支持二维码/HCE）
2. **支付通道管理** - 添加/删除/设置默认支付通道
3. **用户信息查询** - 查询用户信息、卡类型
4. **HCE 数据更新** - 回写 IF1A-01 闸机交易后产生的 HCE 卡数据
5. **卡号管理** - 逻辑卡号与真实卡号映射管理

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| MyBatis | 数据访问层 |
| Oracle | 数据库 |

### 1.3 端口配置

```properties
server.port=9097
```

### 1.4 数据表

| 表名 | 说明 |
|------|------|
| `USER_ITP_REG_INFO` | ITP 用户注册信息表 |
| `USER_ITP_REG_LOG` | ITP 用户注册日志表 |
| `USER_PAY_CHANNEL` | 用户支付渠道表 |
| `USER_ACC_TICKETNO` | 用户账户票卡号表 |

---

## 二、接口清单

### 2.1 APP 接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 请求 DTO | 响应 DTO | 说明 |
|---------|---------|---------|---------|---------|---------|------|
| IF8A-01 | 请求开户 | `/ci/app/requestApplication` | POST | `RequestApplicationReqDTO` | `RequestApplicationResult` | |
| IF8A-19 | 请求添加支付通道 | `/ci/app/requestAddPayChannel` | POST | `RequestAddPayChannelReqDTO` | `RequestAddPayChannelResult` | |
| IF8A-21 | 请求设置默认支付通道 | `/ci/app/requestSetDefaultPayChannel` | POST | `RequestSetDefaultPayChannelReqDTO` | `RequestSetDefaultPayChannelResult` | |
| IF8A-22 | 请求删除支付通道 | `/ci/app/requestRemovePayChannel` | POST | `RequestRemovePayChannelReqDTO` | `RequestRemovePayChannelResult` | |
| IF8A-23 | 查询用户信息 | `/ci/app/queryUserInfo` | POST | `QueryUserInfoReqDTO` | `QueryUserInfoResult` | |
| IF8A-28 | 按逻辑卡号查询真实ITP卡类型 | `/ci/app/queryCardTypeByCardId` | GET | - | `QueryUserInfoResult` | 注意：GET 请求，参数通过 `@RequestParam` 传递 |
| IF8A-32 | 更新HCE卡数据 | `/ci/app/updateHceData` | POST | `UpdateHceDataReqDTO` | `UpdateHceDataResult` | 回写 IF1A-01 闸机交易后产生的 HCE 卡数据 |

**请求格式**：JSON Body（`@RequestBody`），IF8A-28 除外（GET + `@RequestParam`）

---

## 三、核心业务流程

### 3.1 IF8A-01 请求开户流程

**业务流程**：

1. **参数校验** - 校验 `thirdUserId`、`cardId`、`cardType` 等必填字段
2. **检查是否已开户** - 查询 `USER_ITP_REG_INFO` 检查用户是否已注册
3. **生成票卡号** - 调用卡号池服务生成逻辑卡号
4. **保存注册信息** - 插入 `USER_ITP_REG_INFO` 和 `USER_ITP_REG_LOG`
5. **返回结果** - 返回开户结果、票卡号等

### 3.2 IF8A-19 添加支付通道流程

**业务流程**：

1. **参数校验** - 校验 `thirdUserId`、支付渠道等
2. **检查用户是否存在** - 查询 `USER_ITP_REG_INFO`
3. **添加支付渠道** - 插入 `USER_PAY_CHANNEL`
4. **返回结果** - 返回添加结果

### 3.3 IF8A-23 查询用户信息流程

**业务流程**：

1. **参数校验** - 校验 `thirdUserId` 或 `cardId`
2. **查询注册信息** - 查询 `USER_ITP_REG_INFO`
3. **查询支付渠道** - 查询 `USER_PAY_CHANNEL`
4. **返回结果** - 返回用户信息和支付渠道列表

### 3.4 IF8A-28 按逻辑卡号查询卡类型

**业务流程**：

1. **参数校验** - 校验 `cardId`
2. **查询票卡号** - 查询 `USER_ACC_TICKETNO` 获取真实卡号
3. **查询注册信息** - 查询 `USER_ITP_REG_INFO` 获取卡类型
4. **返回结果** - 返回卡类型

### 3.5 IF8A-32 更新HCE卡数据流程

**业务流程**：

1. **参数校验** - 校验 `cardId`、`reserve1`
2. **查询票卡** - 查询 `USER_ACC_TICKETNO`
3. **更新 HCE 数据** - 更新 HCE 卡数据
4. **返回结果** - 返回更新结果

---

## 四、数据模型

### 4.1 USER_ITP_REG_INFO（ITP 用户注册信息表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ID | NUMBER | 主键 |
| CARD_ID | VARCHAR2(128) | 卡号 |
| CARD_TYPE | VARCHAR2(64) | 卡类型 |
| THIRD_USER_ID | VARCHAR2(128) | 第三方用户ID |
| THIRD_USER_ID_SUFFIX | VARCHAR2(4) | 第三方用户ID后缀 |
| MSISDN | VARCHAR2(64) | 手机号 |
| REG_TMS | TIMESTAMP | 注册时间 |
| DEL_YN | NUMBER | 删除标记 |
| DEL_THIRD_USER_ID | VARCHAR2(128) | 删除第三方用户ID |
| UN_REG_TMS | TIMESTAMP | 注销时间 |
| USER_NAME | VARCHAR2(256) | 用户名称 |
| USER_ID | VARCHAR2(128) | 用户ID |
| CARD_ISSUE_CODE | VARCHAR2(64) | 发卡机构代码 |
| THIRD_PAY_ID | VARCHAR2(128) | 第三方支付ID |
| CHANNEL | VARCHAR2(64) | 渠道 |
| REQ_CONTRACT_NO | VARCHAR2(128) | 签约请求号 |

### 4.2 USER_PAY_CHANNEL（用户支付渠道表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| THIRD_USER_ID | VARCHAR2(128) | 第三方用户ID（主键之一） |
| CARD_ID | VARCHAR2(128) | 卡号（主键之一） |
| CARD_TYPE | VARCHAR2(64) | 卡类型 |
| CHANNEL | VARCHAR2(32) | 支付渠道 |
| THIRD_PAY_ID | VARCHAR2(128) | 第三方支付ID |
| REQ_CONTRACT_NO | VARCHAR2(128) | 签约请求号 |
| STATUS | VARCHAR2(64) | 状态 |
| CREATE_TMS | TIMESTAMP | 创建时间 |
| UPDATE_TMS | TIMESTAMP | 更新时间 |

### 4.3 USER_ACC_TICKETNO（用户账户票卡号表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ID | NUMBER | 主键 |
| CARD_ID | VARCHAR2(128) | 卡号 |
| THIRD_USER_ID | VARCHAR2(128) | 第三方用户ID |
| INSERT_TMS | TIMESTAMP | 插入时间 |
| REG_TMS | TIMESTAMP | 注册时间 |

### 4.4 USER_ITP_REG_LOG（ITP 用户注册日志表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ID | NUMBER | 主键 |
| CARD_ID | VARCHAR2(128) | 卡号 |
| CARD_TYPE | VARCHAR2(64) | 卡类型 |
| THIRD_USER_ID | VARCHAR2(128) | 第三方用户ID |
| MSISDN | VARCHAR2(64) | 手机号 |
| OPER_DATE_TIME | TIMESTAMP | 操作时间 |
| OPER_TYPE | NUMBER | 操作类型 |

---

## 五、枚举与常量

### 5.1 AccountErrorCodeEnum

| 错误码 | 含义 | 说明 |
|--------|------|------|
| 0000 | SUCCESS | 成功 |
| 9999 | FAIL | 失败 |
| 8001 | INVALID_PARAM | 无效参数 |
| ... | ... | ... |

---

## 六、文件清单

### 6.1 控制器层

| 文件路径 | 说明 |
|---------|------|
| `account-server/src/main/java/com/chinasofti/huateng/account/controller/ci/app/RequestApplicationController.java` | APP 接口入口 |

### 6.2 服务层

| 文件路径 | 说明 |
|---------|------|
| `account-server/src/main/java/com/chinasofti/huateng/account/service/AccountApplicationService.java` | 账户应用服务接口 |
| `account-server/src/main/java/com/chinasofti/huateng/account/service/impl/AccountApplicationServiceImpl.java` | 账户应用服务实现 |
| `account-server/src/main/java/com/chinasofti/huateng/account/service/AccountRequestVerifier.java` | 请求验证服务 |
| `account-server/src/main/java/com/chinasofti/huateng/account/service/CardPoolService.java` | 卡号池服务接口 |
| `account-server/src/main/java/com/chinasofti/huateng/account/service/impl/CardPoolServiceImpl.java` | 卡号池服务实现 |

### 6.3 模型类

| 文件路径 | 说明 |
|---------|------|
| `account-server/src/main/java/com/chinasofti/huateng/account/model/application/*.java` | 开户相关 DTO |
| `account-server/src/main/java/com/chinasofti/huateng/account/model/card/*.java` | 卡管理相关 DTO |
| `account-server/src/main/java/com/chinasofti/huateng/account/model/common/*.java` | 公共请求 DTO |

### 6.4 数据访问层

| 文件路径 | 说明 |
|---------|------|
| `account-server/src/main/java/com/chinasofti/huateng/account/mapper/UserAccTicketNoMapper.java` | 票卡号 Mapper |
| `account-server/src/main/java/com/chinasofti/huateng/account/mapper/UserItpRegInfoMapper.java` | 注册信息 Mapper |
| `account-server/src/main/java/com/chinasofti/huateng/account/mapper/UserItpRegLogMapper.java` | 注册日志 Mapper |
| `account-server/src/main/java/com/chinasofti/huateng/account/mapper/UserPayChannelMapper.java` | 支付渠道 Mapper |

### 6.5 资源配置

| 文件路径 | 说明 |
|---------|------|
| `account-server/src/main/resources/application.properties` | 应用配置 |
| `account-server/src/main/resources/mapper/*.xml` | MyBatis Mapper XML |

---

## 七、与其他服务的交互

### 7.1 服务调用关系

| 调用方 | 被调用服务 | 调用时机 | 说明 |
|--------|-----------|---------|------|
| account-server | key-server | 开户时 | 请求同步密钥 |
| account-server | para-server | 开户时 | 查询参数信息 |
| account-server | card-pool-service | 开户时 | 获取逻辑卡号 |

---

## 八、相关文档

- `docs/03-接口文档/接口设计对比/接口规范与项目实现对照表.md` - 接口规范与实现对照

---

## 九、源码文件索引

### 9.1 Java 源文件（共 20 个）

1. `account-server/src/main/java/com/chinasofti/huateng/AccountServer.java`
2. `account-server/src/main/java/com/chinasofti/huateng/account/controller/ci/app/RequestApplicationController.java`
3. `account-server/src/main/java/com/chinasofti/huateng/account/controller/ci/channel/FepAlipayTripRequestApplicationController.java`
4. `account-server/src/main/java/com/chinasofti/huateng/account/service/AccountApplicationService.java`
5. `account-server/src/main/java/com/chinasofti/huateng/account/service/impl/AccountApplicationServiceImpl.java`
6. `account-server/src/main/java/com/chinasofti/huateng/account/service/CardPoolService.java`
7. `account-server/src/main/java/com/chinasofti/huateng/account/service/impl/CardPoolServiceImpl.java`
8. `account-server/src/main/java/com/chinasofti/huateng/account/entity/UserItpRegInfo.java`
9. `account-server/src/main/java/com/chinasofti/huateng/account/entity/UserPayChannel.java`
10. `account-server/src/main/java/com/chinasofti/huateng/account/entity/UserAccTicketNo.java`
11. `account-server/src/main/java/com/chinasofti/huateng/account/entity/UserItpRegLog.java`
12-20. `account-server/src/main/java/com/chinasofti/huateng/account/mapper/*.java`

### 9.2 资源文件

1. `account-server/src/main/resources/application.properties`
2. `account-server/src/main/resources/mapper/*.xml`

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/account-server` 模块源码。
