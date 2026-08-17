# acc-security-server 微服务文档

> **模块路径**: `acc-security-server/`
> **端口**: 9012
> **职责**: ACC 安全服务（新版），处理 ACC 系统的安全认证、密钥管理、签名验签、卡证书处理
> **源码阅读范围**: `acc-security-server/src/main/java/`、`acc-security-server/src/main/resources/`
> **整理时间**: 2026-07-28

---

## 一、模块概述

### 1.1 核心职责

acc-security-server 是 ACC 安全服务的新版实现，承担以下核心职责：

1. **CA 密钥管理** - 请求 CA 公钥、更新 CA 密钥版本
2. **DPK 管理** - 请求设备私钥（DPK）
3. **用户密钥管理** - 请求用户 SM2 密钥对、导出用户私钥
4. **卡证书处理** - 卡证书生成、验证
5. **MAC 计算** - 通用 MAC 计算（PBOC 3DES MAC）
6. **TAC 计算** - 通用 TAC 计算
7. **签名验签** - 交易数据签名验签
8. **扫码签名** - 二维码签名数据生成
9. **ITP 业务接口** - 为 ITP 系统提供安全计算服务

### 1.2 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot | Web 框架 |
| MyBatis | 数据访问层 |
| Oracle | 数据库 |
| SM2/SM3 | 国密算法 |
| 3DES | 对称加密 |
| Netty | 网络通信 |
| Feign | RPC 服务调用 |

### 1.3 端口配置

```properties
server.port=9012
```

---

## 二、接口清单

### 2.1 ACC 安全接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| - | 请求 CA 密钥 | `/ci/acc/security/requestCaKey` | POST | 获取 CA 公钥 |
| - | 请求 DPK | `/ci/acc/security/requestDpk` | POST | 获取设备私钥 |
| - | 请求用户 SM2 密钥 | `/ci/acc/security/requestUserSm2Key` | POST | 获取用户 SM2 密钥对 |
| - | 导出用户私钥 | `/ci/acc/security/requestExportUserPriKey` | POST | 导出用户私钥 |
| - | HEC 卡数据 | `/ci/acc/security/requestHceCardDate` | POST | HCE 卡数据请求 |
| - | 二维码逻辑号列表 | `/ci/acc/security/requestQrLogicNumList` | POST | 二维码逻辑号列表 |
| - | 签名初始化数据 | `/ci/acc/security/requestSignInsData` | POST | 签名初始化数据 |
| - | 请求签名公钥 | `/ci/acc/security/requestSignPubkey` | POST | 获取签名公钥 |

### 2.2 ITP 业务接口

| 接口编号 | 接口名称 | 请求路径 | 请求方式 | 说明 |
|---------|---------|---------|---------|------|
| - | 查询卡证书 | `/ci/itp/certificate` | POST | 查询卡证书信息 |
| - | 计算 MAC | `/ci/itp/mac` | POST | 计算交易 MAC |
| - | 计算 TAC | `/ci/itp/tac` | POST | 计算交易 TAC |
| - | 销售退款 | `/ci/itp/saleAndRefund` | POST | 销售退款处理 |
| - | 查询剩余次数 | `/ci/itp/remaining` | POST | 查询票卡剩余次数 |
| - | 签名公钥 | `/ci/itp/signPubkey` | POST | 获取签名公钥 |

---

## 三、核心业务流程

### 3.1 ITP 卡证书处理流程

1. **参数校验** - 校验卡号、卡类型等参数
2. **查询证书** - 从数据库查询卡证书
3. **证书处理** - 证书生成、更新、删除
4. **返回结果** - 返回证书数据

### 3.2 MAC/TAC 计算流程

1. **参数校验** - 校验交易数据
2. **密钥获取** - 获取会话密钥
3. **MAC/TAC 计算** - 使用 PBOC 算法计算
4. **返回结果** - 返回计算结果

### 3.3 SM2 密钥管理流程

1. **参数校验** - 校验用户标识
2. **密钥生成** - 使用 SM2 算法生成密钥对
3. **密钥存储** - 私钥加密存储
4. **返回公钥** - 返回公钥和证书

---

## 四、数据模型

### 4.1 卡证书模型

| 字段 | 说明 |
|------|------|
| cardId | 卡号 |
| cardType | 卡类型 |
| certificate | 证书数据 |
| publicKey | 公钥 |
| status | 证书状态 |
| createTime | 创建时间 |
| updateTime | 更新时间 |

### 4.2 密钥模型

| 字段 | 说明 |
|------|------|
| keyVersion | 密钥版本 |
| keyType | 密钥类型 |
| publicKey | 公钥 |
| privateKey | 私钥（加密存储） |
| status | 密钥状态 |
| effectiveTime | 生效时间 |
| expiredTime | 过期时间 |

---

## 五、枚举与常量

### 5.1 密钥状态

| 枚举值 | 含义 |
|--------|------|
| ACTIVE | 有效 |
| INACTIVE | 无效 |
| EXPIRED | 过期 |

### 5.2 卡类型

| 类型码 | 含义 |
|--------|------|
| 0441 | 二维码后付费单程票 |
| 0442 | HCE 后付费单程票 |
| 0443 | 新版 HCE 后付费单程票 |

---

## 六、文件清单

### 6.1 控制器层

| 文件路径 | 说明 |
|---------|------|
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/CertificateController.java` | 证书接口 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/CommonMacController.java` | MAC 计算接口 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/CommonTacController.java` | TAC 计算接口 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/CommonSaleAndRefundController.java` | 销售退款接口 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/ItpRemainingController.java` | ITP 余额查询 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/ItpSignPubkeyController.java` | ITP 签名公钥 |

### 6.2 服务层

| 文件路径 | 说明 |
|---------|------|
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/service/CertificateService.java` | 证书服务接口 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/service/impl/CertificateServiceImpl.java` | 证书服务实现 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/service/CommonMacService.java` | MAC 服务接口 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/service/impl/CommonMacServiceImpl.java` | MAC 服务实现 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/service/CommonTacService.java` | TAC 服务接口 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/service/impl/CommonTacServiceImpl.java` | TAC 服务实现 |

### 6.3 ITP 服务层

| 文件路径 | 说明 |
|---------|------|
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/itp/service/ItpService.java` | ITP 服务接口 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/itp/service/impl/ItpServiceImpl.java` | ITP 服务实现 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/itp/service/ItpRequestService.java` | ITP 请求服务接口 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/itp/service/ItpRequestSignVerifier.java` | ITP 签名验证 |

### 6.4 工具类

| 文件路径 | 说明 |
|---------|------|
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/itp/util/ItpDesUtils.java` | DES 工具类 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/itp/util/ItpPboc3DesMacUtils.java` | PBOC 3DES MAC 工具类 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/itp/util/ItpHexUtils.java` | Hex 工具类 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/util/OperationMacUtil.java` | 操作 MAC 工具类 |
| `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/util/sm2/*.java` | SM2 算法实现 |

### 6.5 资源配置

| 文件路径 | 说明 |
|---------|------|
| `acc-security-server/src/main/resources/application.properties` | 应用配置 |

---

## 七、与其他服务的交互

### 7.1 服务调用关系

| 调用方 | 被调用服务 | 调用时机 | 说明 |
|--------|-----------|---------|------|
| ticket-server | acc-security-server | 安全计算 | MAC/TAC 计算 |
| online-server | acc-security-server | 安全计算 | MAC/TAC 计算 |
| collect-pay-server | acc-security-server | 安全计算 | MAC/TAC 计算 |
| fep-dev-server | acc-security-server | 密钥同步 | 密钥版本查询 |

---

## 八、相关文档

- `docs/03-接口文档/接口设计对比/接口规范与项目实现对照表.md` - 接口规范与实现对照

---

## 九、源码文件索引

### 9.1 Java 源文件（共 100+ 个）

**控制器层 (6 个)**:
1. `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/CertificateController.java`
2. `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/CommonMacController.java`
3. `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/CommonTacController.java`
4. `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/CommonSaleAndRefundController.java`
5. `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/ItpRemainingController.java`
6. `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/controller/ItpSignPubkeyController.java`

**服务层 (15+ 个)**:
7-21. 见 6.2/6.3 文件清单

**模型类 (30+ 个)**:
22-51. `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/**/*.java`

**工具类 (20+ 个)**:
52-71. `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/**/*.java`

**启动类 (1 个)**:
72. `acc-security-server/src/main/java/com/chinasofti/huateng/acc/security/server/SecurityServerApplication.java`

### 9.2 资源文件

1. `acc-security-server/src/main/resources/application.properties`

---

> **本文档基于源码实际实现编写**，具体路径基于 `qditp/acc-security-server` 模块源码。
