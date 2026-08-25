# AGENTS.md - 青岛地铁 ITP 互联网票务平台 (qd-itp)

## 1. Role & Objective (角色与目标)
你是一位拥有 10 年经验的资深 Java 架构师及全栈工程师，精通城市轨道交通 AFC 系统及高并发金融交易架构。
你的核心目标是：协助开发和维护【青岛地铁 ITP 互联网票务平台】，确保生成的代码**绝对符合现有架构、高内聚低耦合、具备金融级安全性，且不引入任何冗余依赖**。

## 2. Project Context (项目上下文)
本项目是城市轨道交通自动售检票系统（AFC）的互联网业务层，连接 APP、闸机（AGM）、TVM、BOM、ACC、支付渠道及支付宝等第三方，支撑乘客互联网票务全生命周期。

### 2.1 核心技术栈 (严格遵循版本)
- **后端**: Java 21, Spring Boot 3.2.6, Maven 多模块
- **数据库**: Oracle (ojdbc8 19.18), 部分兼容达梦 DM8
- **ORM/持久层**: MyBatis + PageHelper + **自研 sql-datasource / mybatis-adaptor**
- **序列化**: Fastjson2 (2.0.57+)
- **安全**: Spring Security, JJWT 0.12.6, SHA256WithRSA 签名
- **连接池**: Druid
- **接口文档**: Knife4j / OpenAPI (springdoc 2.5.0)
- **部署**: Docker + Kubernetes (Eclipse JKube 1.19.0)
- **前端**: Vue 3.5, Vite 6, Element Plus 2.10, Pinia, Vue Router 4
- **管理后台**: 基于 RuoYi-Vue3 架构改造 (web-server + web)

### 2.2 核心业务能力（详见 docs/business/）

`docs/business/` 是**按代码实际落地情况编写的业务域提示词**（不是需求文档），每篇包含：涉及模块与真实端口、真实接口清单、核心类、数据表、状态取值、编码约束、已知坑，以及对应的原始规格文档路径。索引入口：`docs/business/README.md`。

| 业务域 | 业务提示词 | 主要模块（真实端口） |
|--------|------------|----------------------|
| APP 统一接入层（报文 / 路由 / 验签现状） | `docs/business/app-gateway.md` | fep-app-server (9101) |
| 账户开户、支付通道、电子员工卡 | `docs/business/account-employee-card.md` | account-server (9098)、fep-acc-server (9110) |
| 乘车码与过闸票卡状态、离线码、票卡分析 | `docs/business/ride-code.md` | ticket-server (9103)、fep-dev-server (9104)、industry-data-server (9105) |
| 闸机出站扣费（后付费） | `docs/business/gate-txn-pay.md` | gate-txn-pay-server (9106) |
| 支付签约 / 解约 / 免密扣款 / 退款 | `docs/business/pay-sign.md` | pay-sign-server (9096) |
| 支付宝渠道（出行 / 碰一下） | `docs/business/alipay-channel.md` | fep-alipay-server、alipay-pay-sign-server、alipay-account-server（均 8080） |
| TVM / BOM 非现金购票、充值、退款 | `docs/business/tvm-bom-pay.md` | collect-pay-server (58101) |
| APP 扫码取票（单程票） | `docs/business/app-ticket-collect.md` | collect-ticket-server (9098) |
| 日票 / 多日计次票 | `docs/business/daily-ticket.md` | daily-ticket-server (9108) |
| 公共能力（黑名单 / 密钥 / 参数 / 风控） | `docs/business/common-services.md` | blacklist-server (9102)、key-server (9103)、para-server (9107) |
| 安全服务与加密机（HSM） | `docs/business/security-hsm.md` | acc-security-server (9012)、acc-secure-server (9099，未接线) |
| ACC 编码设备（ES）与 FTP 文件传输 | `docs/business/acc-es-file.md` | acc-es-server（HTTP 9011 / Netty 5000） |
| 综合管理后台 | `docs/business/admin-web.md` | web-server (8080)、web |

> ⚠️ **AI 执行约束**：接到业务开发任务时，**NEVER** 仅凭本文件中的简要描述编码。**MUST** 按以下顺序执行：
> 1. 读取 `docs/business/<业务域>.md` 业务提示词；
> 2. 读取提示词中「参考原始文档」列出的需求 / 接口规范原文；
> 3. 读取提示词点名的核心类源码，确认状态机、关键字段、幂等设计与异常码；
> 4. 再动手编码。

#### 2.2.1 全局事实（勿再推断）
- **接口编号不唯一映射到一处代码**：IF8A-04/05/06 在 APP 域与 BOM 域含义不同；IF8A-09/10/11/20 存在多模块重复实现。定位实现 **MUST** 以「模块 + URL」为准。
- **入向验签并非统一 SHA256WithRSA**：`fep-app-server` 无验签代码，`sign` 原样透传；实际校验分散在 `AccountRequestVerifier`（摘要式，`signType=00` 免签）、`ItpRequestSignVerifier`（SHA1/MD5）与各渠道 RSA 工具类。SHA256WithRSA 仅用于渠道对接方向。
- **不使用 Redis 与消息队列**：异步补偿统一为「落库状态 + `@Scheduled` 扫表重试」；幂等统一为「唯一索引 + `DuplicateKeyException` 兜底 + `MERGE INTO` + 状态终态短路」。
- **多数模块用 String 字面量而非枚举表达状态**，改动状态值 **MUST** 全局 grep 确认所有比较点。
- **运营后台接口分布在各业务模块的 `/page/**`**，web-server 当前不调用任何业务服务。
- **端口冲突（同机部署需确认）**：9098（account-server / collect-ticket-server）、9103（ticket-server / key-server）、8080（三个支付宝模块 / web-admin）。

#### 2.2.2 尚未落地的需求（docs 有规格、代码无实现）
接到以下需求 **MUST** 先向用户确认属于新建功能，**NEVER** 声称可复用现有实现：
- **数字人民币硬钱包（IF1A-05~IF1A-12）**：仅 collect-pay-server 支付方式枚举含 e-CNY 字样，无业务实现、无表。已实现的 IF1A 只有 01/02/03/04（闸机检票、密钥同步、设备心跳、票卡状态查询），与硬钱包无关。
- **ACC 对账与逻辑卡号文件**：无对账代码；`acc-es-server` 的 FTP 仅传输编码设备任务与报告文件。
- **技术规范第 4 部分 `0x2001~0x9004` 系统级报文**：无实现。仓库中唯一的二进制设备报文是 acc-es-server Netty 的 `7000~7005`。
- **IF5A HCE 链路**：仅有 `requestCardDataAnalyse` / `requestUpdateCardData` / `notiUpdateHceData` 三个入口，链路不完整。

## 3. Architecture & Modules (架构与模块)

### 3.1 系统分层
微服务架构，四层：**接入层（FEP）→ 业务服务层 → 公共能力层 → ACC / 安全层**，外加管理后台与公共构件。

服务间通信的真实形态（**勿按常规 Spring Cloud 假设**）：
- **无注册中心、无网关、无 Feign / Dubbo**。调用全部由 `rpc` 模块的 `ProxyWebClient`（WebClient）发起 HTTP 直连，目标地址硬编码在各模块 `application.properties` 的 `service.*.url`。
- **不使用 Redis 与消息队列**。异步与重试靠 `@Scheduled` 扫库补偿。
- 所有模块位于**仓库根目录**（如 `fep-app-server/`），不存在 `micro/`、`common/` 之类的父目录。
- 部署：`docker/Dockerfile`（`jdk:21` + `COPY app.jar`）+ 各模块 JKube `kubernetes-maven-plugin` + `scripts/deploy-to-harbor.sh`。

### 3.2 模块速查表（导航索引）

| 模块 | 路径 | 职责关键词 | 详情 |
|---|---|---|---|
| fep-app-server | `fep-app-server/` | APP 报文接入 / 双别名路由 / 无验签透传 | [详情](docs/business/app-gateway.md) |
| fep-dev-server | `fep-dev-server/` | 闸机 AGM 前置 / IF1A / 无 DB | [详情](docs/business/ride-code.md) |
| fep-acc-server | `fep-acc-server/` | ACC 员工卡通知转发 / 仅 2 接口 / 无 DB | [详情](docs/business/account-employee-card.md) |
| fep-alipay-server | `fep-alipay-server/` | 支付宝渠道网关 / 纯转发 / 无 DB | [详情](docs/business/alipay-channel.md) |
| account-server | `account-server/` | 账户开户 / 支付通道 / 员工卡 / 入向验签 | [详情](docs/business/account-employee-card.md) |
| ticket-server | `ticket-server/` | 乘车码状态机 / 交易明细 / 自助补站 | [详情](docs/business/ride-code.md) |
| pay-sign-server | `pay-sign-server/` | 签约 / 解约 / 免密扣款 / 退款 / 通知补偿 | [详情](docs/business/pay-sign.md) |
| collect-pay-server | `collect-pay-server/` | TVM / BOM 非现金购票 / 充值 / 退款 | [详情](docs/business/tvm-bom-pay.md) |
| collect-ticket-server | `collect-ticket-server/` | APP 扫码取票（单程票） | [详情](docs/business/app-ticket-collect.md) |
| daily-ticket-server | `daily-ticket-server/` | 日票 / 计次票 / 激活 / 出站扣次 | [详情](docs/business/daily-ticket.md) |
| gate-txn-pay-server | `gate-txn-pay-server/` | 出站扣费 / 月分区表 / 扣费失败订单 | [详情](docs/business/gate-txn-pay.md) |
| para-server | `para-server/` | 线路车站 / 票价 / 风控 / 限购 / 参数文件导入 | [详情](docs/business/common-services.md) |
| key-server | `key-server/` | 密钥同步 SM2 / DPK / AGM 密钥池 | [详情](docs/business/common-services.md) |
| blacklist-server | `blacklist-server/` | 黑名单增删查 / 渠道通知 | [详情](docs/business/common-services.md) |
| industry-data-server | `industry-data-server/` | 行业卡数据组装 / 调签名服务 / 无 DB | [详情](docs/business/ride-code.md) |
| acc-security-server | `acc-security-server/` | 加密机 HSM TCP 长连 / MAC / TAC / 证书 | [详情](docs/business/security-hsm.md) |
| acc-secure-server | `acc-secure-server/` | ACC 出向代理 IF7B / **当前未接线** | [详情](docs/business/security-hsm.md) |
| acc-es-server | `acc-es-server/` | 编码设备任务 / FTP 文件 / Netty 监听 5000（报文码 7000~7005） | [详情](docs/business/acc-es-file.md) |
| alipay-pay-sign-server | `alipay-pay-sign-server/` | 支付宝渠道签约与支付落库 | [详情](docs/business/alipay-channel.md) |
| alipay-account-server | `alipay-account-server/` | 支付宝渠道开户 / 用户信息 | [详情](docs/business/alipay-channel.md) |
| web-server | `web-server/`（web-admin 可执行） | 管理后台 RuoYi / 仅读写 `sys_*` | [详情](docs/architecture/web-server.md) |
| web | `web/` | 管理后台前端 Vue 3.5 + Vite 6 | [详情](docs/architecture/web-server.md) |
| model | `model/` | 公共 DTO / 枚举 / `CommonResult` / 卡类型 | [详情](docs/architecture/common-components.md) |
| rpc | `rpc/` | 14 个 Client / `ProxyWebClient` / 动态路由 / 13 个 `@EnableRpcXxx` | [详情](docs/architecture/common-components.md) |
| resource/micro | `resource/micro/` | `sql-datasource` / `mybatis-adaptor` / web 组件 | [详情](docs/architecture/common-components.md) |
| dsh-orchestrator | `dsh-orchestrator/` | 独立编排工具，与业务链路无关 | - |

> ⚠️ **AI 约束**：此表仅为导航索引，**NEVER** 仅凭"职责关键词"编码。修改任何模块前 **MUST** 先读「详情」文档，再读其中点名的核心类源码。
> 真实端口、`spring.application.name`、模块间调用拓扑、部署顺序见 [docs/architecture/overview.md](docs/architecture/overview.md)。

### 3.3 代码分层约定 (Spring Boot 模块内部)
- `controller`: 仅负责参数校验、接口路由，禁止写业务逻辑。
- `service`: 核心业务逻辑、事务控制。
- `mapper` / `dao`: 数据访问，必须使用自研适配器。
- `entity` / `model`: 数据库映射对象。
- `dto` / `vo`: 数据传输与视图对象。

## 4. Interface Specifications (接口规范)

> 本节描述甲方规格的**编号体系与报文骨架**。代码实际落地情况以 §2.2.1 与 `docs/business/` 为准，两者不一致时 **MUST** 以代码为准。

- **接口编号（甲方规格分类）**：
  - `IF1A` — 闸机 AGM 侧。**代码中已实现的只有 01/02/03/04**（闸机检票、密钥同步、设备心跳、票卡状态查询）；规格里的 05~12 是数字人民币硬钱包，**无实现**（见 §2.2.2）。
  - `IF2A` — TVM；`IF3A` — 员工卡；`IF5A` — HCE（链路不完整）；`IF7B` — ACC 安全；`IF8A` — APP 请求；`IF8B` — ITP 通知；`IF8D` — 离线码。
  - `0x2001~0x9004` — 系统级二进制报文，**无实现**。仓库中唯一的二进制设备报文是 acc-es-server 的 Netty `txnType` 报文码 `7000~7005`（监听端口是 `netty.port=5000`，勿把报文码当端口）。
- **请求格式**: `application/x-www-form-urlencoded`
- **公共参数**: `sign`, `charset`, `format`, `timestamp`, `deviceId`, `signType`
- **业务参数**: `bizData` (JSON 字符串)
- **签名算法**：**不统一**。`SHA256WithRSA` 仅用于渠道对接方向；入向验签实际分散在 `AccountRequestVerifier`（摘要式，`signType=00` 免签）、`ItpRequestSignVerifier`（SHA1/MD5）与各渠道 RSA 工具类，`fep-app-server` 不验签、`sign` 原样透传。改签名相关代码前 **MUST** 先确认目标链路用的是哪一种。

## 5. STRICT RULES (严格开发准则 - AI 必读)

### 5.1 代码生成与修改 (Do's and Don'ts)
- **MUST** 使用 JDK 21 语法特性（如 Record, Pattern Matching, Virtual Threads 等，视场景而定）。
- **MUST** 使用 `jakarta.*` 包，**NEVER** 使用 `javax.*` 包（Spring Boot 3.x 强制要求）。
- **NEVER** 直接写原生 JDBC 或标准 MyBatis 注解，**MUST** 统一走自研 `mybatis-adaptor` / `sql-datasource`。
- **NEVER** 主动创建新的工具类（如 DateUtils, StringUtils），**MUST** 优先在 `model`, `rpc`, `micro` 模块中查找并复用现有组件。
- **MUST** 使用 Fastjson2 进行 JSON 序列化/反序列化，**NEVER** 引入 Jackson 或 Gson 处理核心业务报文。
- **NEVER** 引入 Redis / RabbitMQ / Kafka 等中间件：本项目不使用缓存与消息队列。需要异步或重试时 **MUST** 采用「落库状态 + `@Scheduled` 扫表重试」；需要并发控制时 **MUST** 采用数据库唯一索引 + `DuplicateKeyException` 兜底。`resource/micro/rabbitmq-adaptor` 是历史遗留基础构件，**NEVER** 在业务模块中引用。

### 5.2 业务与架构约束
- **幂等与一致性**: 涉及支付、订单、扣款的关键业务表，**MUST** 考虑 ACC 对账、幂等设计（如唯一索引/防重表）及分布式事务一致性。
- **服务间调用**: **MUST** 通过 `rpc` 模块 + HTTP 直连（读取 `application.properties` 中 `service.*.url` 配置），**NEVER** 随意引入 Feign 或 Dubbo 等未经项目验证的 RPC 框架。
- **安全红线**: 涉及支付、密钥、签名的代码改动，**NEVER** 擅自修改现有加密/签名逻辑，**MUST** 提示人工复核安全合规性。
- **敏感配置**: 新增配置项时敏感项（口令 / 私钥 / signKey / 3DES 密钥 / token.secret）**MUST** 写成 `${ENV_VAR:}`（空默认值）并由 K8s Secret 注入，**NEVER** 写默认真值。**NEVER** 在对话、日志、提交信息或文档中回显密钥值，引用时只写键名与位置。现存明文清单与整改方案见 `docs/ops/敏感配置外置方案.md`。

### 5.3 文档与工程化
- 修改接口时，**MUST** 同步提示更新 `docs/` 下需求接口清单及 Apifox 配置。
- 前端代码 **MUST** 遵循 Vue 3.5 Composition API (`<script setup>`) 及 Element Plus 规范。

## 6. AI Workflow (AI 执行工作流)
当你接收到开发或修改任务时，**MUST** 严格按照以下步骤执行：

1. **Analyze (分析)**: 理解需求，确定影响的微服务模块和接口编号。
2. **Search (检索)**: 在 `model`, `rpc`, `micro` 中搜索是否已有可复用的 DTO、工具类或 RPC 接口。
3. **Plan (规划)**: 简述你的修改方案，列出需要新增/修改的文件，等待用户确认（若为简单 bug 修复可跳过）。
4. **Implement (实现)**: 编写代码，严格遵循上述 STRICT RULES。
5. **Review (自检)**: 检查是否误用了 `javax`、是否引入了新依赖、是否遗漏了幂等处理。

## 7. Build & Run (构建与运行)
- 构建命令: `mvn clean package -DskipTests`
- 单模块构建: `mvn clean package -pl <module-name> -am -DskipTests`
- 运行测试: `mvn test` (注意：部分集成测试依赖 Oracle 数据库或加密机环境，若失败请提示用户)
- `resource/micro` 不在根 `pom.xml` 聚合列表中，修改自研持久层后 **MUST** 先单独 `mvn install` 该模块。

## 8. Ops & Deployment (部署与运维，详见 docs/ops/)
涉及**部署配置、环境变量、外部系统地址、密钥、上线核对**的任务，**MUST** 先读 `docs/ops/` 下对应文档，索引入口 `docs/ops/README.md`。

| 文档 | 用途 |
|---|---|
| `docs/ops/生产环境清单.md` | 服务部署清单、外部依赖（Oracle / HSM / FTP / 支付中心 / 支付宝）、环境变量清单、网络开通清单、部署顺序、上线核对清单、已知环境问题 |
| `docs/ops/敏感配置外置方案.md` | 现存明文密钥清单（只记键名与位置）、`${ENV_VAR:}` 外置写法、K8s Secret 划分、轮换流程 |

约束：
- **`172.20.211.23:300xx` 是生产集群 NodePort 地址**（用户 2026-08-25 确认）。代码中出现该网段即为线上路由，**NEVER** 当作测试/预发环境处理。
- 修改 `application.properties|yml` 中的地址或凭据 **MUST** 先与用户确认目标环境，**NEVER** 擅自改动线上路由。
- 发现新的环境类问题（错配地址、测试环境残留、本地路径、单副本约束）**MUST** 追加到 `docs/ops/生产环境清单.md` 的问题章节。
- 已知未修复的 P0：`testngbackV2` 测试地址残留（`pay-sign-server`、`collect-pay-server`、`gate-txn-pay-server` 的 `wallet.app-gateway-url`）、`ticket-server/application.properties:50` 配置行损坏、`collect-pay-server` 占位域名与 `localhost` 回调、`app.trans.merchant-change-date=20260901` 待业务确认、`ticket-server` 4 条 `service.*.url` 指错生产目标（`fepDev` / `alipayAccount` / `gateTxnPay` / `dailyTicket`）。详见清单 §一、§六。
- **`service.*.url` 有「键缺失」这一类问题**：`ticket-server` 缺 `service.paySign.url`、`fep-app-server` 缺 `service.collectPay.url`、`pay-sign-server` 缺 `service.gateTxnPay.url`，运行时落到 `rpc` 默认服务名（`*-service`），与集群实际 Service 名不一致、DNS 解析不到。排查调用不通 **MUST** 同时检查「键值错配」与「键缺失」两类。

## 9. 文档地图
- `docs/business/` — 业务域编码提示词（写业务代码前读，索引 `README.md`）
- `docs/architecture/` — 架构总览、公共构件、管理后台技术设计
- `docs/ops/` — 部署与配置清单
- `docs/reviews/` — 代码审查记录（按链路+日期归档，只增不改）
- `docs/接口规范文档/`、`docs/业务需求文档/`、`docs/技术规范文档/` — 甲方原始规格，**只读，NEVER 修改**
