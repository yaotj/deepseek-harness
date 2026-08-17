# acc-secure-server 微服务文档

> **模块路径**: `acc-secure-server/`
> **端口**: 9099
> **职责**: ACC 安全服务，处理 ACC 系统的安全认证、密钥管理、签名验签、卡证书处理
> **源码阅读范围**: `acc-secure-server/src/main/java/`、`acc-secure-server/src/main/resources/`
> **整理时间**: 2026-07-28

---

## 一、模块概述

### 1.1 核心职责

acc-secure-server 是 ACC 安全服务，承担以下核心职责：

1. **CA 密钥管理** - 请求 CA 公钥、更新 CA 密钥版本
2. **DPK 管理** - 请求设备私钥（DPK）
3. **用户密钥管理** - 请求用户 SM2 密钥对、导出用户私钥
4. **卡证书处理** - 卡证书生成、验证
5. **MAC 计算** - 通用 MAC 计算（PBOC 3DES MAC）
6. **TAC 计算** - 通用 TAC 计算
7. **签名验签** - 交易数据签名验签
8. **扫码签名** - 二维码签名数据生成

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| MyBatis | 数据访问层 |
| Oracle | 数据库 |
| SM2/SM3 | 国密算法 |
| 3DES | 对称加密 |
| Socket/Netty | 网络通信（ACC 设备接入） |

### 1.3 端口配置

```properties
server.port=9099
```

---

## 二、接口清单

### 2.1 ACC 安全接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 请求 DTO | 响应 DTO | 说明 |
|---------|---------|---------|---------|---------|---------|------|
| - | 请求 CA 密钥 | `/ci/acc/requestCaKey` | POST | `RequestCaKeyReqDTO` | `RequestCaKeyRespDTO` | 获取 CA 公钥 |
| - | 请求 DPK | `/ci/acc/requestDpk` | POST | `RequestDpkReqDTO` | `RequestDpkRespDTO` | 获取设备私钥 |
| - | 请求用户 SM2 密钥 | `/ci/acc/requestUserSm2Key` | POST | `RequestUserSm2KeyReqDTO` | `RequestUserSm2KeyRespDTO` | 获取用户 SM2 密钥对 |
| - | 导出用户私钥 | `/ci/acc/requestExportUserPriKey` | POST | `RequestExportUserPriKeyReqDTO` | `RequestExportUserPriKeyRespDTO` | 导出用户私钥 |
| - | HEC 卡数据 | `/ci/acc/requestHecCardDate` | POST | `RequestHecCardDateReqDTO` | `RequestHecCardDateRespDTO` | HCE 卡数据请求 |
| - | 二维码逻辑号列表 | `/ci/acc/requestQrLogicNumList` | POST | `RequestQrLogicNumListReqDTO` | `RequestQrLogicNumListRespDTO` | 二维码逻辑号列表 |
| - | 签名初始化数据 | `/ci/acc/requestSignInsData` | POST | `RequestSignInsDataReqDTO` | `RequestSignInsDataRespDTO` | 签名初始化数据 |
| - | 请求签名公钥 | `/ci/acc/requestSignPubkey` | POST | `RequestSignPubkeyReqDTO` | `RequestSignPubkeyRespDTO` | 获取签名公钥 |

### 2.2 通用安全接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| - | 通用 MAC 计算 | `/ci/common/mac` | POST | 计算 PBOC 3DES MAC |
| - | 通用 TAC 计算 | `/ci/common/tac` | POST | 计算 TAC |
| - | 通用销售退款 | `/ci/common/saleAndRefund` | POST | 销售退款 MAC 计算 |
| - | 卡证书处理 | `/ci/certificate` | POST | 卡证书生成/验证 |

---

## 三、核心业务流程

### 3.1 请求 CA 密钥流程

1. **参数校验** - 校验请求参数
2. **查询密钥** - 查询 `METRO_CA_KEYSTORE` 获取最新有效 CA 公钥
3. **返回结果** - 返回 CA 公钥和版本信息

### 3.2 请求用户 SM2 密钥流程

1. **参数校验** - 校验 `thirdUserId`、`cardId` 等参数
2. **生成密钥对** - 使用 SM2 算法生成用户公私钥对
3. **存储私钥** - 私钥加密后存储到数据库
4. **返回公钥** - 返回用户公钥和证书

### 3.3 签名验签流程

1. **签名** - 使用用户私钥对交易数据签名
2. **验签** - 使用用户公钥验证签名

### 3.4 MAC 计算流程

1. **参数校验** - 校验交易数据
2. **MAC 计算** - 使用 PBOC 3DES MAC 算法计算
3. **返回结果** - 返回 MAC 值

---

## 四、数据模型

### 4.1 METRO_CA_KEYSTORE（密钥存储表）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| KEY_VERSION | VARCHAR2(64) | 密钥版本 |
| KEY_TYPE | VARCHAR2(32) | 密钥类型 |
| PUBLIC_KEY | CLOB | 公钥内容 |
| STATUS | VARCHAR2(32) | 密钥状态 |
| EFFECTIVE_TIME | TIMESTAMP | 生效时间 |
| EXPIRED_TIME | TIMESTAMP | 过期时间 |
| CREATE_TIME | TIMESTAMP | 创建时间 |

---

## 五、文件清单

### 5.1 控制器层

| 文件路径 | 说明 |
|---------|------|
| `acc-secure-server/src/main/java/com/chinasofti/huateng/accsecure/controller/AccSecureController.java` | ACC 安全接口入口 |
| `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/CertificateController.java` | 证书接口 |
| `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/CommonMacController.java` | MAC 计算接口 |
| `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/CommonTacController.java` | TAC 计算接口 |
| `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/CommonSaleAndRefundController.java` | 销售退款接口 |
| `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/ItpRemainingController.java` | ITP 余额查询 |
| `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/ItpSignPubkeyController.java` | ITP 签名公钥 |

### 5.2 服务层

| 文件路径 | 说明 |
|---------|------|
| `acc-secure-server/src/main/java/com/chinasofti/huateng/accsecure/service/AccSecureService.java` | ACC 安全服务接口 |
| `acc-secure-server/src/main/java/com/chinasofti/huateng/accsecure/service/impl/AccSecureServiceImpl.java` | ACC 安全服务实现 |
| `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/service/CertificateService.java` | 证书服务接口 |
| `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/service/CommonMacService.java` | MAC 服务接口 |
| `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/service/CommonTacService.java` | TAC 服务接口 |

### 5.3 工具类

| 文件路径 | 说明 |
|---------|------|
| `acc-secure-server/src/main/java/com/chinasofti/huateng/accsecure/util/AccSecureSignUtils.java` | 签名工具类 |
| `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/itp/util/ItpDesUtils.java` | DES 工具类 |
| `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/itp/util/ItpPboc3DesMacUtils.java` | PBOC 3DES MAC 工具类 |
| `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/util/sm2/*.java` | SM2 算法实现 |

### 5.4 资源配置

| 文件路径 | 说明 |
|---------|------|
| `acc-secure-server/src/main/resources/application.properties` | 应用配置 |

---

## 六、与其他服务的交互

### 6.1 服务调用关系

| 调用方 | 被调用服务 | 调用时机 | 说明 |
|--------|-----------|---------|------|
| acc-secure-server | key-server | 密钥查询 | 查询 CA 密钥 |
| ticket-server | acc-secure-server | 安全计算 | MAC/TAC 计算 |
| online-server | acc-secure-server | 安全计算 | MAC/TAC 计算 |

---

## 七、相关文档

- `docs/03-接口文档/接口设计对比/接口规范与项目实现对照表.md` - 接口规范与实现对照

---

## 八、源码文件索引

### 8.1 Java 源文件（共 100+ 个）

**控制器层 (7 个)**:
1. `acc-secure-server/src/main/java/com/chinasofti/huateng/accsecure/controller/AccSecureController.java`
2. `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/CertificateController.java`
3. `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/CommonMacController.java`
4. `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/CommonTacController.java`
5. `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/CommonSaleAndRefundController.java`
6. `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/ItpRemainingController.java`
7. `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/ItpSignPubkeyController.java`

**服务层 (10+ 个)**:
8-17. 见 5.2 文件清单

**模型类 (30+ 个)**:
18-47. `acc-secure-server/src/main/java/com/chinasofti/huateng/accsecure/model/**/*.java`

**工具类 (20+ 个)**:
48-67. `acc-secure-server/src/main/java/com/chinasofti/huateng/acc/security/server/**/*.java`

**启动类 (1 个)**:
68. `acc-secure-server/src/main/java/com/chinasofti/huateng/AccSecureServer.java`

### 8.2 资源文件

1. `acc-secure-server/src/main/resources/application.properties`

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/acc-secure-server` 模块源码。
