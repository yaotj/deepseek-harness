# key-server 微服务文档

> **模块路径**: `key-server/`
> **端口**: 9102
> **职责**: 密钥服务，处理密钥同步、密钥版本管理、CA 密钥查询
> **源码阅读范围**: `key-server/src/main/java/`、`key-server/src/main/resources/`
> **整理时间**: 2026-07-28

---

## 一、模块概述

### 1.1 核心职责

key-server 是 ITP 平台的密钥管理服务，承担以下核心职责：

1. **密钥同步** - IF8A-02 请求同步密钥（二维码 SM2 密钥/HCE DPK）
2. **密钥版本管理** - 管理密钥版本和有效期
3. **CA 密钥查询** - 查询 CA 公钥
4. **密钥推送** - 推送密钥到 acc-security-server

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| MyBatis | 数据访问层 |
| Oracle | 数据库 |
| 3DES | 对称加密 |

### 1.3 端口配置

```properties
server.port=9102
```

### 1.4 数据表

| 表名 | 说明 |
|------|------|
| `METRO_CA_KEYSTORE` | CA 密钥存储表 |
| `METRO_MEMBER_STATIC_KEY` | 会员静态密钥表 |

---

## 二、接口清单

### 2.1 密钥接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 请求 DTO | 响应 DTO | 说明 |
|---------|---------|---------|---------|---------|---------|------|
| IF8A-02 | 请求同步密钥 | `/requestKeyList` | POST | `RequestKeyListReqDTO` | `RequestKeyListResult` | |

**说明**：
- 卡类型 `03`/`04` 时返回 HCE 消费 DPK
- 其余卡类型按二维码卡生成用户 SM2 密钥
- 具体密钥材料由 acc-security-server 导出后转加密

---

## 三、核心业务流程

### 3.1 IF8A-02 请求同步密钥流程

**调用链路**：
```
APP
  → fep-app-server (/ci/app/requestKeyList)
    → IndustryDataClient.requestKeyList()
      → key-server (/requestKeyList)
        → KeySyncService.requestKeyList()
          → 1. 参数校验
          → 2. 根据 cardType 判断密钥类型
          → 3. 调用 acc-security-server 生成/获取密钥
          → 4. 加密存储
          → 5. 返回密钥材料
```

**业务逻辑**：

1. **参数校验** - 校验 `thirdUserId`、`cardId`、`cardType`
2. **判断密钥类型** - 根据 `cardType` 判断是二维码还是 HCE
3. **生成/获取密钥** - 调用 acc-security-server 生成密钥
4. **加密存储** - 私钥加密后存储到数据库
5. **返回结果** - 返回公钥和证书

---

## 四、数据模型

### 4.1 METRO_CA_KEYSTORE（CA 密钥存储表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ID | NUMBER | 主键 |
| KEY_IDX | VARCHAR2(4) | 密钥索引 |
| KEY_PRIVATE | VARCHAR2(256) | 私钥 |
| KEY_PUBLIC | VARCHAR2(256) | 公钥 |
| KEY_EFFECTIVE_DATE | VARCHAR2(20) | 密钥生效日期 |
| KEY_STATUS | NUMBER | 密钥状态 |
| MANAGER_ID | VARCHAR2(20) | 管理员ID |
| RESERVE | VARCHAR2(100) | 保留字段 |
| REMARK | VARCHAR2(100) | 备注 |
| UPDATE_DATE | DATE | 更新日期 |
| REG_DATE | DATE | 注册日期 |
| KEY_PAIR | VARCHAR2(1024) | 密钥对 |

### 4.2 METRO_MEMBER_STATIC_KEY（会员静态密钥表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| ID | NUMBER | 主键 |
| MEMBER_TYPE | VARCHAR2(32) | 会员类型 |
| PUBLIC_KEY | VARCHAR2(512) | 公钥 |
| PRIVATE_KEY | VARCHAR2(512) | 私钥 |
| STATUS | VARCHAR2(32) | 状态 |
| CREATE_TIME | TIMESTAMP | 创建时间 |
| UPDATE_TIME | TIMESTAMP | 更新时间 |

---

## 五、文件清单

### 5.1 控制器层

| 文件路径 | 说明 |
|---------|------|
| `key-server/src/main/java/com/chinasofti/huateng/key/controller/KeyController.java` | 密钥接口入口 |

### 5.2 服务层

| 文件路径 | 说明 |
|---------|------|
| `key-server/src/main/java/com/chinasofti/huateng/key/service/KeySyncService.java` | 密钥同步服务接口 |
| `key-server/src/main/java/com/chinasofti/huateng/key/service/impl/KeySyncServiceImpl.java` | 密钥同步服务实现 |

### 5.3 工具类

| 文件路径 | 说明 |
|---------|------|
| `key-server/src/main/java/com/chinasofti/huateng/key/util/TripleDesEcbUtils.java` | 3DES 工具类 |

### 5.4 资源配置

| 文件路径 | 说明 |
|---------|------|
| `key-server/src/main/resources/application.properties` | 应用配置 |
| `key-server/src/main/resources/sql/key-server-schema.sql` | 建表脚本 |

---

## 六、与其他服务的交互

### 6.1 服务调用关系

| 调用方 | 被调用服务 | 调用时机 | 说明 |
|--------|-----------|---------|------|
| key-server | acc-security-server | 生成密钥 | 调用 SM2 密钥生成 |
| account-server | key-server | 开户时 | 请求同步密钥 |
| industry-data-server | key-server | 生成行业数据时 | 获取密钥 |

---

## 七、相关文档

- `docs/02-微服务文档/acc-security-server.md` - ACC 安全服务

---

## 八、源码文件索引

### 8.1 Java 源文件（共 9 个）

1. `key-server/src/main/java/com/chinasofti/huateng/KeyServer.java`
2. `key-server/src/main/java/com/chinasofti/huateng/key/controller/KeyController.java`
3. `key-server/src/main/java/com/chinasofti/huateng/key/service/KeySyncService.java`
4. `key-server/src/main/java/com/chinasofti/huateng/key/service/impl/KeySyncServiceImpl.java`
5. `key-server/src/main/java/com/chinasofti/huateng/key/entity/MetroCaKeystore.java`
6. `key-server/src/main/java/com/chinasofti/huateng/key/entity/MetroMemberStaticKey.java`
7. `key-server/src/main/java/com/chinasofti/huateng/key/mapper/MetroCaKeystoreMapper.java`
8. `key-server/src/main/java/com/chinasofti/huateng/key/mapper/MetroMemberStaticKeyMapper.java`
9. `key-server/src/main/java/com/chinasofti/huateng/key/util/TripleDesEcbUtils.java`

### 8.2 资源文件

1. `key-server/src/main/resources/application.properties`
2. `key-server/src/main/resources/sql/key-server-schema.sql`

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/key-server` 模块源码。
