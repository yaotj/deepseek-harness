# 04-服务文档

> **说明**：本目录为旧版服务文档归档目录。
> **新版基于源码的详细文档已迁移到 `docs/02-微服务文档/`**。
>
> 新版文档特点：
> - 基于源码逐文件阅读后编写
> - 覆盖所有微服务模块
> - 包含模块概述、接口清单、核心业务流程、数据模型、枚举常量、文件清单、服务交互、待办事项、源码文件索引
> - 保持中文，技术术语保留英文
>
> 如需查看源码级文档，请参考 [02-微服务文档](../02-微服务文档/README.md)。

---

## 目录结构

| 目录 | 说明 |
|------|------|
| `01-账户与签约/` | 账户服务、支付签约服务、钱包服务 |
| `02-票务核心/` | 票务核心服务 |
| `03-支付代收/` | TVM/BOM 代收付服务、票务代收服务 |
| `04-设备接入/` | 设备在线管理服务、APP 接入网关、开发环境接入网关 |
| `05-参数与密钥/` | 参数管理服务、密钥管理服务 |
| `06-行业数据/` | 行业数据服务、日票服务 |
| `07-安全与合规/` | 黑名单服务、ACC 事件源服务、ACC 安全服务 |
| `08-支付宝服务/` | 支付宝出行接入网关、支付宝通知服务、支付宝账户服务、支付宝支付签约服务 |
| `99-其他/` | 其他服务（闸机交易支付、管理后台服务） |

## 快速导航

- **源码级详细文档** → [02-微服务文档](../02-微服务文档/README.md)
- **接口规范对照** → [03-接口文档/接口设计对比](../03-接口文档/接口设计对比/)
- **业务流程图** → [01-项目概述/支付宝乘车业务流.md](../01-项目概述/支付宝乘车业务流.md)

---

## 旧版文档清单

### 01-账户与签约

| 服务名 | 说明 | 文档链接 |
|--------|------|---------|
| account-server | 通用账户服务 | [README.md](./01-账户与签约/account-server/README.md) |
| pay-sign-server | 通用支付签约服务 | [README.md](./01-账户与签约/pay-sign-server/README.md) |
| wallet-server | 钱包签约服务 | [README.md](./01-账户与签约/wallet-server/README.md) |

### 02-票务核心

| 服务名 | 说明 | 文档链接 |
|--------|------|---------|
| ticket-server | 票务核心服务 | [README.md](./02-票务核心/ticket-server/README.md) |

### 03-支付代收

| 服务名 | 说明 | 文档链接 |
|--------|------|---------|
| collect-pay-server | TVM/BOM 代收付服务 | [README.md](./03-支付代收/collect-pay-server/README.md) |
| collect-ticket-server | 票务代收服务 | [README.md](./03-支付代收/collect-ticket-server/README.md) |

### 04-设备接入

| 服务名 | 说明 | 文档链接 |
|--------|------|---------|
| online-server | 设备在线管理服务 | [README.md](./04-设备接入/online-server/README.md) |
| fep-app-server | APP 接入网关 | [README.md](./04-设备接入/fep-app-server/README.md) |
| fep-dev-server | 开发环境接入网关 | [README.md](./04-设备接入/fep-dev-server/README.md) |

### 05-参数与密钥

| 服务名 | 说明 | 文档链接 |
|--------|------|---------|
| para-server | 参数管理服务 | [README.md](./05-参数与密钥/para-server/README.md) |
| key-server | 密钥管理服务 | [README.md](./05-参数与密钥/key-server/README.md) |

### 06-行业数据

| 服务名 | 说明 | 文档链接 |
|--------|------|---------|
| industry-data-server | 行业数据服务 | [README.md](./06-行业数据/industry-data-server/README.md) |
| daily-ticket-server | 日票服务 | [README.md](./06-行业数据/daily-ticket-server/README.md) |

### 07-安全与合规

| 服务名 | 说明 | 文档链接 |
|--------|------|---------|
| blacklist-server | 黑名单服务 | [README.md](./07-安全与合规/blacklist-server/README.md) |
| acc-es-server | ACC 事件源服务 | [README.md](./07-安全与合规/acc-es-server/README.md) |
| acc-secure-server | ACC 安全服务 | [README.md](./07-安全与合规/acc-secure-server/README.md) |
| acc-security-server | ACC 安全服务 | [README.md](./07-安全与合规/acc-security-server/README.md) |

### 08-支付宝服务

| 服务名 | 说明 | 文档链接 |
|--------|------|---------|
| alipay-account-server | 支付宝账户服务 | [implementation.md](./08-支付宝服务/alipay-account-server/implementation.md) |
| alipay-pay-sign-server | 支付宝支付签约服务 | [README.md](./08-支付宝服务/alipay-pay-sign-server/README.md) |

### 99-其他

| 服务名 | 说明 | 文档链接 |
|--------|------|---------|
| gate-txn-pay-server | 闸机交易支付服务 | [README.md](./99-其他/gate-txn-pay-server/README.md) |
| web-server | 管理后台服务 | [README.md](./99-其他/web-server/README.md) |
