# alipay-account-server 微服务文档

> **模块路径**: `alipay-account-server/`
> **端口**: 8080
> **职责**: 支付宝账户服务，处理支付宝渠道的用户开户、用户信息查询、支付通道更新
> **源码阅读范围**: `alipay-account-server/src/main/java/`、`alipay-account-server/src/main/resources/`
> **整理时间**: 2026-07-20

---

## 一、模块概述

### 1.1 核心职责

alipay-account-server 是支付宝渠道的账户服务，承担以下核心职责：

1. **支付宝开户** - 支付宝渠道用户开户，分配逻辑卡号
2. **用户信息查询** - 按 thirdUserId 查询支付宝用户信息
3. **支付通道更新** - 更新用户第三方支付ID和签约请求号

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| MyBatis | ORM 框架 |
| Oracle | 数据库 |

### 1.3 端口配置

```properties
server.port=8080
```

---

## 二、接口清单

### 2.1 支付宝出行接口（供 fep-alipay-server 调用）

**基础路径**：`/channel`

#### 2.1.1 开卡申请

- **路径**：`POST /channel/requestApplication`
- **Controller**：`FepAlipayTripRequestApplicationController.requestApplication`
- **Service**：`AlipayAccountServiceImpl.requestApplication`

**请求参数** (`AlipayTripRequestApplicationReqDTO`)：

```java
String thirdUserId;      // 第三方用户ID，格式化后的用户标识
String cardType;         // 卡片类型，如：02
String msisdn;           // 用户手机号码
String extend1;          // 扩展字段1
String extend2;          // 扩展字段2（可选）
String cardIssueCode;    // 发卡渠道代码 0007支付宝出行
```

**响应参数** (`AlipayTripRequestApplicationRespDTO` extends CommonResult)：

```java
String cardId;           // 卡片ID/逻辑卡号，开卡成功后返回
String cardType;         // 卡片类型
String status;           // 用户状态，如 ACTIVE
```

**业务逻辑**：

1. 参数校验：`thirdUserId`、`cardType`、`cardIssueCode` 必填。
2. 重复开户检查：根据 `thirdUserId` 查询 `ALIPAY_USER_INFO`，若已开户且 `cardIssueCode` 相同，返回已有卡号。
3. 卡号分配：调用 `CardPoolService.allocateNextCard()` 分配逻辑卡号。
4. 生成 `requestSeq` UUID。
5. 写入 `ALIPAY_USER_INFO`：状态=`ACTIVE`，`deleteFlag="0"`，`version="1"`，`createTime`/`updateTime` 自动填充。
6. 写入 `ALIPAY_REG_LOG` 开卡流水。
7. 调用 `ticket-server` 注册乘车状态 `registerRideStatus`。
8. 返回 `cardId`、`cardType`、`status`。

---

#### 2.1.2 查询用户信息

- **路径**：`GET /channel/queryUserInfo`
- **Controller**：`FepAlipayTripRequestApplicationController.queryUserInfo`
- **Service**：`AlipayAccountServiceImpl.selectByThirdUserId`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `thirdUserId` | String | 是 | 第三方用户ID |

**响应参数** (`AlipayUserInfoDTO`)：

```java
String thirdUserId;      // 支付宝用户ID（主键）
String cardId;           // 逻辑卡号
String cardType;         // 卡类型编码
String msisdn;           // 手机号
String extend1;          // 扩展字段1
String extend2;          // 扩展字段2
String channel;          // 开户渠道编码
String thirdPayId;       // 第三方支付ID
String reqContractNo;    // 签约请求号
String status;           // 用户状态
String cardIssueCode;    // 发卡渠道代码
```

> **注意**：`AlipayUserInfoDTO.phone` 对应 entity 字段 `msisdn`，Service 层 `selectByThirdUserId` 必须显式赋值，否则返回 null。

---

#### 2.1.3 更新支付通道

- **路径**：`GET /channel/updatePaymentChannel`
- **Controller**：`FepAlipayTripRequestApplicationController.updatePaymentChannel`
- **Service**：`AlipayAccountServiceImpl.updatePaymentChannel`

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `thirdUserId` | String | 是 | 第三方用户ID |
| `thirdPayId` | String | 是 | 第三方支付ID |
| `reqContractNo` | String | 是 | 签约请求号 |

**响应参数**：`Boolean` — 更新是否成功

---

## 三、数据模型

### 3.1 ALIPAY_USER_INFO 实体

**路径**：`alipay-account-server/src/main/java/com/chinasofti/huateng/alipay/account/entity/AlipayUserInfo.java`

| 字段 | 类型 | 说明 |
|------|------|------|
| `thirdUserId` | String | 支付宝用户ID（主键） |
| `cardId` | String | 逻辑卡号 |
| `cardType` | String | 卡类型编码 |
| `cardIssueCode` | String | 发卡渠道代码，固定 0007 |
| `msisdn` | String | 手机号 |
| `extend1` | String | 扩展字段1 |
| `extend2` | String | 扩展字段2 |
| `channel` | String | 开户渠道编码 |
| `thirdPayId` | String | 第三方支付ID |
| `reqContractNo` | String | 签约请求号 |
| `status` | String | 用户状态 |
| `deleteFlag` | String | 删除标记（0:正常 1:删除） |
| `version` | String | 乐观锁版本号 |
| `createTime` | LocalDateTime | 创建时间 |
| `updateTime` | LocalDateTime | 更新时间 |
| `createBy` | String | 创建人 |

### 3.2 ALIPAY_REG_LOG 实体

**路径**：`alipay-account-server/src/main/java/com/chinasofti/huateng/alipay/account/entity/AlipayRegLog.java`

| 字段 | 类型 | 说明 |
|------|------|------|
| `requestSeq` | String | 请求流水号（主键） |
| `thirdUserId` | String | 第三方用户ID |
| `cardId` | String | 逻辑卡号 |
| `cardType` | String | 卡类型 |
| `cardIssueCode` | String | 发卡渠道代码 |
| `channel` | String | 渠道编码 |
| `status` | String | 状态 |
| `requestBody` | String | 请求报文 |
| `responseBody` | String | 响应报文 |
| `resultCode` | String | 结果码 |
| `resultMsg` | String | 结果消息 |
| `createTime` | LocalDateTime | 创建时间 |

### 3.3 ALIPAY_CARD_POOL 实体

**路径**：`alipay-account-server/src/main/java/com/chinasofti/huateng/alipay/account/entity/AlipayCardPool.java`

逻辑卡号池，用于分配可用卡号。

---

## 四、服务交互

### 4.1 服务调用关系

| 被调用服务 | 调用时机 | 调用方法 |
|-----------|---------|---------|
| ticket-server | 开卡申请成功后 | `ticketClient.registerRideStatus()` |
| alipay-pay-sign-server | 签约成功后更新支付通道 | 通过 fep-alipay-server 间接调用 |

### 4.2 RPC 客户端

- `rpc/src/main/java/com/chinasofti/huateng/rpc/alipay/account/AlipayAccountClient.java`

---

## 五、文件清单

### 5.1 Java 源文件

**控制器层**：
- `alipay-account-server/src/main/java/com/chinasofti/huateng/alipay/account/controller/FepAlipayTripRequestApplicationController.java`

**服务层**：
- `alipay-account-server/src/main/java/com/chinasofti/huateng/alipay/account/service/AlipayAccountService.java`
- `alipay-account-server/src/main/java/com/chinasofti/huateng/alipay/account/service/impl/AlipayAccountServiceImpl.java`

**实体层**：
- `alipay-account-server/src/main/java/com/chinasofti/huateng/alipay/account/entity/AlipayUserInfo.java`
- `alipay-account-server/src/main/java/com/chinasofti/huateng/alipay/account/entity/AlipayRegLog.java`
- `alipay-account-server/src/main/java/com/chinasofti/huateng/alipay/account/entity/AlipayCardPool.java`

**Mapper 层**：
- `alipay-account-server/src/main/java/com/chinasofti/huateng/alipay/account/mapper/AlipayUserInfoMapper.java`
- `alipay-account-server/src/main/java/com/chinasofti/huateng/alipay/account/mapper/AlipayRegLogMapper.java`
- `alipay-account-server/src/main/java/com/chinasofti/huateng/alipay/account/mapper/AlipayCardPoolMapper.java`

**启动类**：
- `alipay-account-server/src/main/java/com/chinasofti/huateng/alipay/account/AlipayAccountServer.java`

### 5.2 资源文件

1. `alipay-account-server/src/main/resources/application.properties`
2. `alipay-account-server/src/main/resources/application.yml`

---

## 六、相关文档

- `docs/03-接口文档/接口改造记录/签约渠道与支付宝出行接口改造记录.md` - 签约渠道改造记录
- `docs/01-项目概述/支付宝乘车业务流.md` - 支付宝乘车业务流图

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/alipay-account-server` 模块源码。
