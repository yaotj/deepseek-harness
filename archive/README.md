# 青岛地铁 ITP 平台文档目录

> 本文档目录覆盖项目业务说明、接口规范、服务实现、数据库设计、测试验证及部署运维等核心内容。
> 整理时间：2026-07-20

---

## 目录结构

| 序号 | 目录 | 说明 |
|------|------|------|
| 01 | [项目概述](./01-项目概述/) | 项目整体介绍、业务流图、架构说明 |
| 02 | [微服务文档](./02-微服务文档/) | 各微服务基于源码的详细实现文档 |
| 03 | [接口文档](./03-接口文档/) | 接口规范与实现对照、开发记录、改造记录 |
| 04 | [数据库](./05-数据库/) | 数据库表结构说明、SQL 脚本 |
| 05 | [测试与验证](./06-测试与验证/) | 测试用例、请求与代码匹配度检查 |
| 06 | [运维与部署](./07-运维与部署/) | 部署配置、Maven 升级、环境说明 |
| 07 | [历史记录](./08-历史记录/) | 旧版文档归档（迁移自旧版目录结构） |
| 08 | [OpenAPI 规范](./09-OpenAPI规范/) | 接口 OpenAPI/Swagger 定义文件、导出脚本 |
| 09 | [业务规范与标准](./10-业务规范与标准/) | 上级原始业务规范与标准文档（已迁移） |
| 10 | [设计文档](./11-设计文档/) | 上级原始设计文档（已迁移） |
| 11 | [测试与功能梳理](./12-测试与功能梳理/) | 上级原始测试与功能梳理资料（已迁移） |

---

## 快速导航

### 新手上路
1. 先阅读 [01-项目概述/支付宝乘车业务流.md](./01-项目概述/支付宝乘车业务流.md) 了解核心业务流程
2. 再查看 [03-接口文档/接口设计对比/接口规范与项目实现对照表.md](./03-接口文档/接口设计对比/接口规范与项目实现对照表.md) 了解接口实现现状
3. 最后参考 [02-微服务文档](./02-微服务文档/) 深入各服务实现细节

### 按业务查找
- **支付宝出行扫码** → [01-项目概述/支付宝乘车业务流.md](./01-项目概述/支付宝乘车业务流.md) + [02-微服务文档/fep-alipay-server.md](./02-微服务文档/fep-alipay-server.md)
- **TVM/BOM 购票** → [02-微服务文档/collect-pay-server.md](./02-微服务文档/collect-pay-server.md)
- **日票业务** → [02-微服务文档/daily-ticket-server.md](./02-微服务文档/daily-ticket-server.md)
- **闸机过闸扣费** → [02-微服务文档/ticket-server.md](./02-微服务文档/ticket-server.md) + [02-微服务文档/online-server.md](./02-微服务文档/online-server.md)

### 按接口查找
- **接口规范对照** → [03-接口文档/接口设计对比](./03-接口文档/接口设计对比/)
- **接口开发记录** → [03-接口文档/接口开发记录](./03-接口文档/接口开发记录/)
- **接口改造记录** → [03-接口文档/接口改造记录](./03-接口文档/接口改造记录/)
- **OpenAPI 定义** → [09-OpenAPI规范](./09-OpenAPI规范/)

---

## 文档维护说明

1. **新增文档**：请按上述目录结构放置到对应分类下
2. **更新文档**：修改后请同步更新本文档 README 中的相关链接
3. **归档旧文档**：废弃文档请移动到 `08-历史记录/旧版文档归档/` 目录
4. **OpenAPI 文件**：统一存放在 `09-OpenAPI规范/`，命名规则：`{服务名}-openapi.{json|yaml}`

---

## 文档清单

### 01-项目概述
- [支付宝乘车业务流](./01-项目概述/支付宝乘车业务流.md) — 完整业务流程泳道图与时序说明，含闸机支付宝免密扣费链路详图

### 03-接口文档
> **说明**：本目录包含接口规范正文拆分、设计对比、开发记录、改造记录及时序图。
> 详细索引见 [03-接口文档/README.md](./03-接口文档/README.md)。

#### 接口规范正文
- [接口规范正文目录](./03-接口文档/接口规范正文/README.md) — 原始接口规范按接口拆分为 84 个独立文件

#### 接口设计对比
- [接口规范与项目实现对照表](./03-接口文档/接口设计对比/接口规范与项目实现对照表.md) — 规范 vs 实现详细对照，含支付宝 vs 地铁APP对比、接口依赖矩阵

#### 接口开发记录
- [IF8A-04-请求自助补站](./03-接口文档/接口开发记录/IF8A-04-请求自助补站.md) — 自助补站功能开发记录
- [IF8A-01-请求开户时序图](./03-接口文档/IF8A-01-请求开户时序图.md) — 请求开户接口调用时序文档

#### 接口改造记录
- [签约渠道与支付宝出行接口改造记录](./03-接口文档/接口改造记录/签约渠道与支付宝出行接口改造记录.md) — 多渠道签约改造，含签约信息字段映射详表

### 02-微服务文档
> **说明**：基于源码逐文件阅读后整理的详细文档，覆盖所有 20 个微服务模块。
> 每个文档包含：模块概述、接口清单、核心业务流程、数据模型、枚举常量、文件清单、服务交互、待办事项、源码文件索引。

| 服务名 | 说明 | 文档链接 |
|--------|------|---------|
| account-server | 通用账户服务 | [account-server.md](./02-微服务文档/account-server.md) |
| pay-sign-server | 通用支付签约服务 | [pay-sign-server.md](./02-微服务文档/pay-sign-server.md) |
| ticket-server | 票务核心服务 | [ticket-server.md](./02-微服务文档/ticket-server.md) |
| fep-app-server | APP 接入网关 | [fep-app-server.md](./02-微服务文档/fep-app-server.md) |
| online-server | 设备在线管理服务 | [online-server.md](./02-微服务文档/online-server.md) |
| collect-pay-server | TVM/BOM 代收付服务 | [collect-pay-server.md](./02-微服务文档/collect-pay-server.md) |
| wallet-server | 钱包签约服务 | [wallet-server.md](./02-微服务文档/wallet-server.md) |
| blacklist-server | 黑名单管理服务 | [blacklist-server.md](./02-微服务文档/blacklist-server.md) |
| industry-data-server | 行业数据服务 | [industry-data-server.md](./02-微服务文档/industry-data-server.md) |
| key-server | 密钥管理服务 | [key-server.md](./02-微服务文档/key-server.md) |
| para-server | 参数管理服务 | [para-server.md](./02-微服务文档/para-server.md) |
| daily-ticket-server | 日票服务 | [daily-ticket-server.md](./02-微服务文档/daily-ticket-server.md) |
| collect-ticket-server | 票务代收服务 | [collect-ticket-server.md](./02-微服务文档/collect-ticket-server.md) |
| fep-dev-server | 开发环境接入网关 | [fep-dev-server.md](./02-微服务文档/fep-dev-server.md) |
| gate-txn-pay-server | 闸机交易支付服务 | [gate-txn-pay-server.md](./02-微服务文档/gate-txn-pay-server.md) |
| fep-alipay-server | 支付宝出行接入网关 | [fep-alipay-server.md](./02-微服务文档/fep-alipay-server.md) |
| fep-alipay-notify-server | 支付宝出行通知服务 | [fep-alipay-notify-server.md](./02-微服务文档/fep-alipay-notify-server.md) |
| alipay-account-server | 支付宝账户服务 | [alipay-account-server.md](./02-微服务文档/alipay-account-server.md) |
| alipay-pay-sign-server | 支付宝支付签约服务 | [alipay-pay-sign-server.md](./02-微服务文档/alipay-pay-sign-server.md) |
| 接口改造记录 |
|:-|
| - [签约渠道与支付宝出行接口改造记录](./03-接口文档/接口改造记录/签约渠道与支付宝出行接口改造记录.md) — 多渠道签约改造，含签约信息字段映射详表 |

### 05-数据库
- [tables.md](./05-数据库/tables.md) — 数据库表结构说明
- [支付宝接口建表.sql](./05-数据库/支付宝接口建表.sql) — 支付宝相关建表脚本

### 06-测试与验证
- [TVM聚合支付联调计划](./06-测试与验证/TVM聚合支付联调计划.md) — TVM自动售票机聚合支付联调计划，含边界条件、设备联调、数据一致性验证
- [BOM聚合支付联调计划](./06-测试与验证/BOM聚合支付联调计划.md) — BOM半自动售票机聚合支付联调计划，含边界条件、设备联调、数据一致性验证
- [测试请求与代码实现匹配度检查](./06-测试与验证/测试请求与代码实现匹配度检查.md) — collect-pay-server TVM/BOM 接口测试验证，含路径前缀、请求方式、字段匹配度检查

### 07-运维与部署
- [maven-version-upgrade](./07-运维与部署/maven-version-upgrade.md) — Maven 版本升级记录

### 09-OpenAPI 规范
- [支付宝出行接口规范](./09-OpenAPI规范/支付宝出行接口规范.yaml)
- [支付宝出行接口](./09-OpenAPI规范/支付宝出行接口.yaml)
- [当面付接口](./09-OpenAPI规范/当面付接口-openapi.yaml)
- [IF2A-REFUND-requestRefund](./09-OpenAPI规范/IF2A-REFUND-requestRefund.yaml)
- [IF8A-04-requestExcessFare](./09-OpenAPI规范/IF8A-04-requestExcessFare.yaml)
- [IF8A-05-requestTransList](./09-OpenAPI规范/IF8A-05-requestTransList.yaml)
- [generate_openapi.py](./09-OpenAPI规范/generate_openapi.py) — OpenAPI 生成脚本
|- [export-openapi.sh](./09-OpenAPI规范/export-openapi.sh) — OpenAPI 导出脚本

### 09-业务规范与标准
> **说明**：本目录包含从上级 `../qd/docs` 迁移过来的原始业务规范与标准文档。

| 文档名 | 说明 | 链接 |
|--------|------|------|
| 青岛地铁-ITP与APP接口规范 | ITP 与 APP 接口规范 | [青岛地铁-ITP与APP接口规范.md](./10-业务规范与标准/青岛地铁-ITP与APP接口规范.md) |
| 城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范 | 互联网业务规范标准 | [城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.md](./10-业务规范与标准/城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.md) |

### 10-设计文档
> **说明**：本目录包含从上级 `../qd/docs` 迁移过来的原始设计文档。

| 文档名 | 说明 | 链接 |
|--------|------|------|
| 设计文档_中软华腾轨道交通电子票应用接入平台软件_V4 | 系统设计文档 | [设计文档_中软华腾轨道交通电子票应用接入平台软件_V4.doc](./11-设计文档/设计文档_中软华腾轨道交通电子票应用接入平台软件_V4.doc) |

### 11-测试与功能梳理
> **说明**：本目录包含从上级 `../qd/docs` 迁移过来的测试与功能梳理资料。

| 文档名 | 说明 | 链接 |
|--------|------|------|
| 基本功能测试 | 基本功能测试用例 | [基本功能测试.xlsx](./12-测试与功能梳理/基本功能测试.xlsx) |
| ITP功能梳理20251120 | ITP 功能梳理 2025-11-20 | [ITP功能梳理20251120.xlsx](./12-测试与功能梳理/ITP功能梳理20251120.xlsx) |
