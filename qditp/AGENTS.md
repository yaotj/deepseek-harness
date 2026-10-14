# 青岛地铁 ITP 票务平台（qd-itp）

## 1. Core Objective (核心目标)
协助本平台的**开发、联调、文档编写与运维**。一切产出（代码 / 文档 / 配置 / SQL / 联调改动）MUST 绝对符合现有架构与既有约定、高内聚低耦合、具备金融级安全性，且 NEVER 引入任何冗余（依赖 / 文档 / 流程）——分域纪律见 §5~§9。

## 2. Project Context (项目上下文)
本项目是城市轨道交通自动售检票系统（AFC）的互联网业务层，连接 APP、闸机（AGM）、TVM、BOM、ACC、支付渠道及支付宝等第三方，支撑乘客互联网票务全生命周期。

### 2.1 核心技术栈（本节只列不可从代码推断的硬约定；具体版本以各模块 pom / package.json 为准，不自此处查）
- **后端**: Java + Spring Boot + Maven 多模块；**数据库**: Oracle（ojdbc），部分兼容达梦 DM8
- **ORM/持久层**: MyBatis + PageHelper + **自研 sql-datasource / mybatis-adaptor**（硬约束见 §5.1）
- **序列化**: Fastjson2，**NEVER** Jackson / Gson（§5.1）；**安全**: Spring Security（仅管理后台 web-server 启用）、JJWT、SHA256WithRSA
- **连接池**: Druid（WallFilter 两条陷阱见 §5.1）；**接口文档**: Knife4j / OpenAPI（springdoc）
- **部署**: Docker + K8s（Eclipse JKube）；**前端**: Vue + Vite + Element Plus + Pinia + Vue Router；管理后台基于 RuoYi-Vue3 改造（web-server + web）

### 2.2 核心业务能力（详见 docs/business/）

`docs/business/` 是**按代码实际落地情况编写的业务域提示词**（不是需求文档），每篇包含：涉及模块与真实端口、真实接口清单、核心类、数据表、状态取值、编码约束、已知坑，以及对应的原始规格文档路径。索引入口：`docs/business/README.md`。

| 业务域 | 业务提示词 | 主要模块 |
|--------|------------|----------|
| APP 统一接入层（报文 / 路由 / 验签现状） | `docs/business/app-gateway.md` | fep-app-server |
| 账户开户、支付通道、电子员工卡 | `docs/business/account-employee-card.md` | account-server、fep-acc-server |
| 乘车码与过闸票卡状态、离线码、票卡分析 | `docs/business/ride-code.md` | ticket-server、fep-dev-server、industry-data-server |
| 闸机出站扣费（后付费） | `docs/business/gate-txn-pay.md` | gate-txn-pay-server |
| 支付签约 / 解约 / 免密扣款 / 退款 | `docs/business/pay-sign.md` | pay-sign-server |
| 支付宝渠道（出行 / 碰一下） | `docs/business/alipay-channel.md` | fep-alipay-server、alipay-pay-sign-server、alipay-account-server |
| TVM / BOM 非现金购票、充值、退款 | `docs/business/tvm-bom-pay.md` | face-pay-server、collect-pay-server |
| APP 扫码取票（单程票） | `docs/business/app-ticket-collect.md` | collect-ticket-server |
| 日票 / 多日计次票 | `docs/business/daily-ticket.md` | daily-ticket-server |
| 逻辑卡号池（批次申请 / ACC 文件导入 / 卡号预占） | `docs/business/card-pool.md` | card-pool-server |
| 日终对账（分片抽取 / 流式聚合 / FTP 投递） | `docs/business/recon.md` | recon-server（源服务详见该文档） |
| 公共能力（黑名单 / 密钥 / 参数 / 风控） | `docs/business/common-services.md` | blacklist-server、key-server、para-server |
| 安全服务与加密机（HSM） | `docs/business/security-hsm.md` | acc-security-server、acc-secure-server |
| ACC 编码设备（ES）与 FTP 文件传输 | `docs/business/acc-es-file.md` | acc-es-server |
| 综合管理后台 | `docs/business/admin-web.md` | web-server、web |

> ⚠️ **AI 执行约束**：接到业务开发任务时，**NEVER** 仅凭本文件中的简要描述编码。**MUST** 按以下顺序执行：
> 1. 读取 `docs/business/<业务域>.md` 业务提示词；
> 2. 读取提示词中「参考原始文档」列出的需求 / 接口规范原文；
> 3. 读取提示词点名的核心类源码，确认状态机、关键字段、幂等设计与异常码；
> 4. 再动手编码。

#### 2.2.1 全局事实（勿再推断）

> 本节只留**跨域元规则**与**排障入口判据**。凡「哪个域的事实」一律去承接文档读，NEVER 在本文件复述：

| 事实簇 | 唯一权威文档 |
|---|---|
| `sys_job` 清单 / cron / 任务编号、`gate-txn-pay` 7 个内部端点、模块内 `@Scheduled` 现状 | [`docs/architecture/web-server.md`](docs/architecture/web-server.md) §七（含 §7.0 端点表、§7.0.1 `@Scheduled` 实测表） |
| tracing 开关「三行成组」、已打开的 9 个模块名单、「MUST 同时查 Deployment env」判据 | [`docs/architecture/common-components.md`](docs/architecture/common-components.md) §附.5.1 |
| 开户数量由 `ticketLimit` 控制、唯一卡查重条件、`ISSUE_ORG_CODE` vs `CARD_ISSUE_CODE` | [`docs/business/account-employee-card.md`](docs/business/account-employee-card.md) §IF8A-01 当前开户规则（ADR-D122） |
| 黑名单自动解除（`sys_job` 230）三条判据与 `skipped` 覆盖缺口 | [`docs/business/common-services.md`](docs/business/common-services.md) |
| 用户主动重试扣费、`GATE_RETRY_QUEUE`、`sys_job` 400 | `docs/domain/decisions.md` ADR-D169；队列消费细节见 web-server.md §7.0 |

**版本号 / 端口 / 任务编号 / 流量指向 MUST 现查**（§5.3），NEVER 引用上表文档里的数字当现状。

- **接口编号不唯一映射到一处代码**：IF8A-04/05/06 在 APP 域与 BOM 域含义不同；IF8A-09/10/11/20 存在多模块重复实现。定位实现 **MUST** 以「模块 + URL」为准。
- **多数模块用 String 字面量而非枚举表达状态**，改动状态值 **MUST** 全局 grep 确认所有比较点。
- **端口冲突（同机部署需确认）**：9098（account-server / collect-ticket-server）、9103（ticket-server / key-server）、8080（三个支付宝模块 / web-admin）。完整部署清单见 [`docs/ops/生产环境清单.md`](docs/ops/生产环境清单.md)。

#### 2.2.2 尚未落地的需求（**MUST 先读 `docs/business/未落地需求.md`**）

> 完整清单与逐条分组在 [`docs/business/未落地需求.md`](docs/business/未落地需求.md)，本节只留入口判据。

接到以下需求 **MUST** 先向用户确认属于新建功能，**NEVER 声称可复用现有实现**：
- **数字人民币硬钱包**（IF1A-06~IF1A-12，共 7 个，**规格原文里没有 IF1A-05**）：其中 5 个 URL 前缀是 `/itpbom/ci/bom/**`、归属 `face-pay-server`，**NEVER 因编号是 IF1A 就认为它们进闸机前置**。
- **ACC 对账文件**（`ITP.EXP` / `ITP.PAY` / `ITP.BUS` / `ITP.DETAIL`）：生成与 FTP 投递**已落地**。
- **技术规范第 4 部分 `0x2001~0x9004` 系统级报文**：无实现；该规范在仓库内**没有源文件**，涉及该编号体系的任务 MUST 先告知用户文档缺失。
- **IF5A HCE 链路**：仅有 3 个入口，链路不完整。
- **R6 两条出向通知（IF8B 族）**：§3.63 已实现；`/app/receiveAgreementException` 未实现，且其表 131 的请求参数与接口语义明显不符，**实现前 MUST 先向甲方澄清真实字段，NEVER 照抄那张表**。
- **超时处理**：契约已成文、代码未实现，且带一个 P0 现存缺陷；唯一入口 `docs/business/overtime-handling.md`，**接这类需求 MUST 先读它，NEVER 只凭本节动手**。

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
| trans-query-server | `trans-query-server/` | APP 交易查询独立服务：三条 URL 与 `ticket-server` 并存未替换，**已在承接真实流量** | [详情](docs/business/ride-code.md) |
| pay-sign-server | `pay-sign-server/` | 签约 / 解约 / 免密扣款 / 退款 / 通知补偿 | [详情](docs/business/pay-sign.md) |
| face-pay-server | `face-pay-server/` | TVM / BOM / APP 当面付主实现（`F2F_*` 表），2026-09-16 起承接设备 + APP 域入向流量，**NEVER 回退**；7 个 `@Scheduled` 无分布式锁，**MUST 单副本** | [详情](docs/business/tvm-bom-pay.md) |
| collect-pay-server | `collect-pay-server/` | **2026-10-13 已下线（0 pod）**：入向流量 2026-09-16 已切 face-pay，对账旧表源已移除、通知端点由 face-pay 复刻承接，`fep-app` / web-admin / recon 均已切并验证；残留 collect-ticket 未部署、gate-txn-pay 死配置待清理。**判断 MUST 现查 Deployment** | [详情](docs/business/tvm-bom-pay.md) |
| collect-ticket-server | `collect-ticket-server/` | APP 扫码取票（单程票） | [详情](docs/business/app-ticket-collect.md) |
| daily-ticket-server | `daily-ticket-server/` | 日票 / 计次票 / 激活 / 出站扣次 | [详情](docs/business/daily-ticket.md) |
| card-pool-server | `card-pool-server/` | 逻辑卡号池 / IF7B-01 直连 ACC 申请 / FTP 导入 / 卡号预占 / 无 `@Scheduled`（维护走接口） | [详情](docs/business/card-pool.md) |
| recon-server | `recon-server/` | 日终对账批次编排 / 分片接收 / 流式聚合 / FTP 投递；主链路无模块内 `@Scheduled`（由 web-admin `sys_job` 触发，**MUST 单副本**），另有 1 个第三方对账 `@Scheduled` **默认关闭** | [详情](docs/business/recon.md) |
| gate-txn-pay-server | `gate-txn-pay-server/` | 出站扣费 / 月分区表 / 扣费失败订单 | [详情](docs/business/gate-txn-pay.md) |
| para-server | `para-server/` | 线路车站 / 票价 / 风控 / 限购 / 参数文件导入 | [详情](docs/business/common-services.md) |
| key-server | `key-server/` | 密钥同步 SM2 / DPK / AGM 密钥池 | [详情](docs/business/common-services.md) |
| blacklist-server | `blacklist-server/` | 黑名单增删查 / 渠道通知 | [详情](docs/business/common-services.md) |
| industry-data-server | `industry-data-server/` | 行业卡数据组装 / 调签名服务 / 无 DB | [详情](docs/business/ride-code.md) |
| acc-security-server | `acc-security-server/` | 加密机 HSM TCP 长连 / MAC / TAC / 证书 | [详情](docs/business/security-hsm.md) |
| acc-secure-server | `acc-secure-server/` | ACC 出向代理 IF7B-02~08 / **当前未接线**；IF7B-01 已迁至 card-pool-server 直连 | [详情](docs/business/security-hsm.md) |
| acc-es-server | `acc-es-server/` | 编码设备任务 / FTP 文件 / Netty 监听 5000（报文码 7000~7005） | [详情](docs/business/acc-es-file.md) |
| alipay-pay-sign-server | `alipay-pay-sign-server/` | 支付宝渠道签约与支付落库 | [详情](docs/business/alipay-channel.md) |
| alipay-account-server | `alipay-account-server/` | 支付宝渠道开户 / 用户信息 | [详情](docs/business/alipay-channel.md) |
| web-server | `web-server/`（web-admin 可执行） | 管理后台 RuoYi / 读写 `sys_*` / 前台可配的 Quartz 定时任务 | [详情](docs/architecture/web-server.md) |
| web | `web/` | 管理后台前端 Vue 3.5 + Vite 6 | [详情](docs/architecture/web-server.md) |
| rpc | `rpc/` | 服务间调用**唯一通道**：`ProxyWebClient` + 各业务 Client + 一组 `@EnableRpcXxx` 装配注解；无注册中心、无 Feign / Dubbo | [详情](docs/architecture/common-components.md) |
| model | `model/` | 公共 DTO / 枚举 / `CommonResult` / 卡类型 | [详情](docs/architecture/common-components.md) |
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
  - `IF1A` — 闸机 AGM 侧。**代码中已实现的只有 01/02/03/04**（闸机检票、密钥同步、设备心跳、票卡状态查询）；规格里的 **06~12**（共 7 个，**没有 05**）是数字人民币硬钱包，**无实现**，且**其中 5 个的 URL 前缀是 `/itpbom/ci/bom/**`、归属 `face-pay-server`，不在闸机前置**（见 §2.2.2 与 ADR-D75）。
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
- **NEVER 在 SQL 正文中写注释**（`--` 行注释或 `/* */` 块注释）。全部服务共用 `resource/micro/sql-datasource/src/main/resources/sql.properties` 的 `spring.datasource.druid.filter.wall.enabled=true`，Druid WallFilter 默认 `commentAllow=false`，SQL 带注释会被判定为注入并抛 `SQLException: sql injection violation ... comment not allow`，该语句**静默失效**（编译与单测都发现不了，只在运行时炸）。注释 **MUST** 写在 mapper XML 的 `<!-- -->` 或 Java 注释里。**NEVER** 靠放宽 `wall.config.comment-allow` 绕过——那是全局降低注入防护等级。注意 `dm.properties` 在达梦环境关闭了 WallFilter，因此**该类缺陷只在 Oracle 生产环境暴露**。已发生事故：`PayTxnDetailMapper.updatePayCallback` 的 WHERE 内两行 `--` 注释导致支付成功回调的 UPDATE 全部失败，`PAY_STATUS` 长期卡在 `PROCESSING`、`PAY_TIME` 恒为空（2026-08-25 修复）。
- **同一个 Druid WallFilter 还有第二条规则：NEVER 写 `where 1 = 1` 打头 + 全部谓词都是可选 `<if>`**（2026-09-16 实测，ADR-D97 续）。所有可选条件都不传时，恒真条件成为唯一谓词，Druid 报 `sql injection violation ... select alway true condition not allow`，该查询整条失败、退化成 UUID `retCode`。**MUST 用 MyBatis `<where>` 标签**（无条件时不产出 `WHERE`）。与上面那条注释规则同源、同样只在运行时炸，而且**只在「筛选条件全空」那一支上踩** —— 而那一支往往正是前台首次进页面、不选任何条件的默认加载。已发生：`account-server` 的 `UserItpRegInfoMapper.countGroupByCardType`（综管台注册量统计，不选日期即失败，2.0.73 修复）。
- **mapper XML 的 `<!-- -->` 注释里 NEVER 出现两个连续减号**。XML 规范禁止注释文本内含连续减号，MyBatis 解析该 mapper 直接失败 ⇒ `sqlSessionFactory` 建不起来 ⇒ **整个服务启动即挂**（`Failed to parse mapping resource: '.../XxxMapper.xml'`；异常链最外层报的是某个无关 mapper 的 `sqlSessionTemplate` 注入失败，极易误判成依赖问题，**MUST 一直往下翻到 `Failed to parse mapping resource` 那一行看文件名**）。这与上一条是**孪生陷阱**：上一条要求把「禁止在 SQL 正文写行注释」的说明写进 XML 注释，而说明里若原样引用那两个符号就会踩本条。已发生（2026-09-11）：`account-server` 2.0.47 因 `AccountExceptionTicketMapper.xml` 的注释里原样写了行注释符号，部署后启动失败、Pod 虽 `2/2 Running` 但端口不监听（表现为 envoy 返 `Connection refused`）。新增或改动 mapper XML 后 **MUST** 跑 `xmllint --noout <file>` 逐个校验。
- **子查询起了驼峰别名时，外层 `<where>` / `order by` MUST 引用别名，NEVER 引用物理列名**（2026-09-20 实测，`daily-ticket-server` 1.0.35 修复）。Oracle 把未加引号的列别名统一折叠成**大写无下划线**（`O.CREATE_TIME as createTime` ⇒ 结果集列名是 `CREATETIME`），于是外层写 `O.CREATE_TIME` 一律 `ORA-00904: "O"."CREATE_TIME": 标识符无效`；`ORDER_NO as orderNo` / `PAYMENT_ORDER_NO as paymentOrderNo` 同理。这是本节前两条 Druid WallFilter 陷阱的**同族第三条**：编译、单测、`xmllint` 全都发现不了，只在 Oracle 运行时炸，且被全局异常处理器包成 **HTTP 200 + UUID `retCode`**，前台只显示「查询失败」。**NEVER 以为「只有带筛选条件那一支才踩」** —— `order by` 不在 `<if>` 里、每次都拼上，因此该查询**任何筛选组合都失败**、功能从上线起从未可用过。已发生：综管台「日票退款」页（`172.20.211.23:30029/trans-operation/daily-ticket-refund` → `GET /page/daily-ticket/refund/orders`）的 `DailyTicketOrderMapper.selectRefundOrders`，UNION ALL 子查询外层 4 个谓词加 `order by` 全按物理列名写，页面 100% 报错。**排查判据**：日志里 `ORA-00904` 指向的表别名若是**子查询自己的别名**（本例是 `) O`），就是本条，MUST 回头看那段 `as` 列表；修法是把外层引用换成别名（`O.createTime`），**NEVER 去动子查询里的别名** —— `resultType` 的字段映射依赖它。
- **mapper 写语句的参数 MUST 显式写 `jdbcType`，NEVER 依赖 MyBatis 推断**（2026-09-23 实测，上述三条的同族第四条）。Java 侧参数为 `null` 且未指定 `jdbcType` 时，MyBatis 按 `JdbcType.OTHER`（type code 1111）下发，Oracle/ojdbc 直接报 `java.sql.SQLException: 无效的列类型: 1111`，被包成 `MyBatisSystemException: null`（**注意 message 是 `null`**，见下面第二条连带结论）。编译、单测、`xmllint` 全都发现不了，**只在 Oracle 运行时炸、且只在「该列前端传了字面 null」那一支上炸** —— 而前端普遍把空白说明/备注 trim 成 `null` 再送。已发生：综管台「风险组管理 / 风险规则管理」（`172.20.211.23:30029/risk/risk-group` → `POST|PUT /page/risk/groups`、`/page/risk/rules`）在描述留空时新增/修改**必现 HTTP 500**，日志原文 `Could not set parameters for mapping: ParameterMapping{property='groupDesc', ... jdbcType=null ...} Cause: ... Error setting null for parameter #2 with JdbcType OTHER`；同一支 XPath 上 `groupId` / `managerCode` / `remark` 三个可空列同样中招，**已于 para-server `2.0.26` 重建镜像并生产复测通过（描述 `null` 的新增/修改 → `200`）**。修法是在 XML 里给每个参数补正确类型（字符串列 `VARCHAR`、数值列 `NUMERIC`），**NEVER 用全局 `mybatis.configuration.jdbc-type-for-null` 兜底或把 null 归一成空串**——前者改的是所有模块的公共行为，后者会改变列语义（NULL 变成空串）。**本项目已有既成约定**：`recon-server/src/main/resources/mapper/ReconPartMapper.xml` 写明「所有参数一律显式写 jdbcType（Oracle 下 null + OTHER(1111) = 无效列类型）」；`para-server` 里此前只有 `RiskGroupMapper` / `RiskRuleMapper` 两个写 mapper 漏写，其余（含 `OrderRefundCycleMapper`、`SingleTicketPurchaseLimitMapper`）均合规。**判据**：新增或改动含可空列的写语句后，MUST 逐个参数确认有 `jdbcType=`。
- **连带，排查「明明有全局异常处理器却返 Spring 原生 500 报文」MUST 先看异常自身的 `getMessage()` 是否为 `null`**：`resource/micro/web/.../global/ApiErrorResponse.java` 无条件执行 `ex.getMessage().length() > 20`，而 `MyBatisSystemException`（由 MyBatis 只带 cause 构造）的 message 恒为 `null` ⇒ `@ExceptionHandler(Exception)` 自己抛 `NullPointerException` ⇒ `@RestControllerAdvice` 失效 ⇒ 请求回落到 `BasicErrorController`，最终返 **HTTP 500 + `{"timestamp","status","error","path"}`**（不是项目的 `retCode` 形态、也没有 `msg`，前台只能显示 500）。因此**判定「XX 接口报 500」时判据 MUST 落在服务端日志原文**，NEVER 只看有无 500：**Spring 原生 500 报文 = 全局异常处理器自身失败**（HTTP 状态 500 且 body 无 `retCode`），而 **`code:"500"` 的业务报文是正常的业务拒绝**（如「风险组名称已存在」），两者形态不同、成因不同。该 NPE 是 `resource/micro` 公共构件的既有缺陷，修复需走「`mvn install` 公共构件 + 逐个重建受影响模块镜像」的完整序列（§5.2 末条），**未做那套重建前 NEVER 认为它已生效**。
- **NEVER** 主动创建新的工具类（如 DateUtils, StringUtils），**MUST** 优先在 `model`, `rpc`, `micro` 模块中查找并复用现有组件。
- **公共请求报文骨架全项目只有两个类**：`model/app/ItpCommonRequest<T>` 与 `ItpCommonFormRequest`（`extends ItpCommonRequest<String>`，form-data 入向用）。2026-09-11 已把 7 份字段完全相同的副本删除并收口（`fep-app-server` / `fep-acc-server` 的 `CommonRequest` + `CommonFormRequest`、`fep-dev-server` / `fep-alipay-server` 的 `CommonFormRequest`、`account-server` 的 `CommonRequest`、`collect-pay-server` 的 `AppCommonRequest`、`acc-secure-server` 的 `AccCommonRequest`），涉 23 个引用文件、8 个模块（同批 +0.0.1 重建镜像）。**NEVER 再在业务模块新建同形副本**。这两个类**只承载报文骨架、不承载签名语义**——四条链路的加签验签互不相同（见 §4「签名算法」），**NEVER 因为共用同一个类就顺手统一签名逻辑**（属本节末「安全红线」）。`toString` 已对 `sign` 恒定脱敏、**NEVER 改成打印真值**；但 `bizData` 会完整进日志，`fep-alipay-server` 的 8 处 `log.info("...{}", request)` 因此由「只打对象 hash」变为「打全部字段」。
- **MUST** 使用 Fastjson2 进行 JSON 序列化/反序列化，**NEVER** 引入 Jackson 或 Gson 处理核心业务报文。
- **NEVER** 引入 Redis / RabbitMQ / Kafka 等中间件：本项目不使用缓存与消息队列。需要异步或重试时 **MUST** 采用「落库状态 + `@Scheduled` 扫表重试」；需要并发控制时 **MUST** 采用数据库唯一索引 + `DuplicateKeyException` 兜底。`resource/micro/rabbitmq-adaptor` 是历史遗留基础构件，**NEVER** 在业务模块中引用。

### 5.2 业务与架构约束
- **幂等与一致性**: 涉及支付、订单、扣款的关键业务表，**MUST** 考虑 ACC 对账、幂等设计（如唯一索引/防重表）及分布式事务一致性。
- **「按业务键幂等」的远端资源 NEVER 在失败分支回滚**。卡池预占是活样例：`reserveFromPool` 按 `businessId`（开户唯一卡现为用户+所属方+票种的长度前缀键，见 ADR-D122；旧版为 `thirdUserId + ":" + 票种`）幂等，**两条并发请求拿到的是同一个 `cardNo` 和同一个 `reservationId`** —— 预占是**共享的**，不是本请求私有的。于是「我失败了就 `releaseReservation`」会把兄弟请求正要 `confirm` 的卡号抽回池子，而那条兄弟已经把卡号写进 `USER_ITP_REG_INFO` 并对 APP 返了 `0000`，结果**同一个卡号在账户表是「已发给 A」、在 `LOGIC_CARD_POOL_CARD` 是 `AVAILABLE`（可再发给 B）**，只留一行 ERROR 日志。2026-09-14 真实并发实测到（ADR-D52；全库扫出 1 条即当次造的，属潜伏缺陷首次触发）。**判据**：远端资源的键是业务键、不是本次请求的唯一键时，本请求就**不是该资源的唯一持有者**，MUST 放弃「失败即回滚」，改为**留给对方的超时回收**（卡池是 `sys_job` **240「卡池数据导入」**，cron `0 0/5 * * * ?`；2026-09-21 由 107「卡池维护」改名改号，触发目标与 cron 未变，**NEVER 回退成 107**）。**NEVER 试图用「只有创建者才释放」或对 `reservationId` 做 CAS 来兜** —— 兄弟请求持有的就是同一个 id，任何以它为键的判断都分不清彼此。连带一条：这类「先占远端 → 落本地 → 再确认远端」的链路，**最后那步确认失败时 MUST NOT 返成功**，MUST 落异常工单转人工（`ACCOUNT_EXCEPTION_TICKET` 的 `CARD_POOL_CONFIRM_REJECTED`），否则不一致对上游完全不可见。
- **跨模块写操作的顺序**：一个事务内既要调远端（RPC）又要改本地时，**MUST 先调远端、后改本地**，远端失败即抛异常回滚，把状态留在中间态等对方重试。顺序颠倒会留下「本地已完成、远端未清理」的不一致，且无法自愈。链路见 `docs/business/pay-sign.md` §解约链路 —— **宿主已变**：解约申请在 `ContractDomainServiceImpl.requestTermination`、解约成功收口在 `CallbackDomainServiceImpl.receiveTerminationResult`，`PaySignWorkflow` 已于 2026-09-15 删除（ADR-D87）。**注意该收口分支本身已按 ADR-D48 改成「本地短事务 + `CHANNEL_SYNC_*` outbox + 提交后出网」**，因为它的远端调用**不允许**被事务包住（见下面「`@Transactional` 方法内 NEVER 发起任何 RPC」）；本条只约束「远端调用确实在事务内」的场景。
- **返回 boolean 的 RPC 包装方法**：本项目多个 RPC 包装方法内部 `catch` 全部异常后 `return false`，**从不抛异常**（活例子只剩 `PaySignClient.updatePaySignDisplayAccount`；**原先并列的 `PaySignWorkflow.removeAccountPayChannel` 已随 god class 于 2026-09-15 一并删除**——拆分时确认它零调用方，通道清理的唯一实现改为 `ChannelSyncDeliverer`，见 ADR-D87 / ADR-D48，**NEVER 再引用那个类名**）。调用这类方法 **MUST** 显式检查返回值并自行抛异常，**NEVER** 假定「没抛异常就是成功」——已发生两起：①远端清理失败仍写成功状态并通知 APP；②2026-09-11 `AccountApplicationServiceImpl.syncDisplayAccountToPayDomain` 丢弃 `updatePaySignDisplayAccount` 的返回值，而支付域在 `APP_PAY_SIGN_INFO` UPDATE 影响 0 行时就返回 FAIL（`PaySignAppController`），于是「该用户在支付域没有签约记录」被写成 `SIGN_SYNC_STATUS='SUCCESS'`，既不重试也不开工单（合成数据端到端实测到，已修，见 `docs/domain/decisions.md` ADR-D13）。**排查这类静默不一致 MUST 先看调用点有没有接住 boolean**。**2026-09-12（ADR-D45）起，这两处已换成 `rpc` 模块的 sealed `RpcOutcome`**（`Ok` / `BizRejected` / `Unreachable`，`rpc/src/main/java/com/chinasofti/huateng/rpc/outcome/RpcOutcome.java`）：调用点改成穷尽 `switch` 模式匹配，**少写一个分支直接编译失败**，不再依赖人记规则。收益不在类型优雅，而在于「业务拒绝」与「网络不可达」终于可区分 —— 前者重推一万次也不会成功、MUST 一次即终态 + 开工单，后者才进补偿队列。**新写 RPC 包装方法 MUST 返回 `RpcOutcome`，NEVER 再返回 boolean**；`PaySignClient.updatePaySignDisplayAccount` 仅作为 `@Deprecated` 兼容壳保留（`rpc` 版本号锁死、被 21 个模块引用，只能增方法不能改签名），**NEVER 在它内部重复一份解析逻辑**。
- **NEVER 在切面 / 包装层把异常换类型**（`throw new RuntimeException(e)`）。本项目的幂等主写法是
  「唯一索引 + `catch (DuplicateKeyException / DataIntegrityViolationException)` 兜底」（§5.1 末条），
  而 `resource/micro/web` 的 `MapperAspectToTrace` / `ServiceAspectToTrace` 曾用 `observation.observe(Supplier)`
  那种写法，lambda 内 `throw new RuntimeException(e)` 把真实异常包了一层，后果是**调用方按类型 catch 一律失效**。
- **上面这条只在打开 tracing 的模块上出现**（`shouldSkipAopTraceLogic()` 会短路其余模块），
  因此**同一份代码在 A 模块好、在 B 模块坏**，单测里没有切面、更看不到。
  **名单与「MUST 同时查 K8s Deployment env」判据见 [`docs/architecture/common-components.md`](docs/architecture/common-components.md) §附.5.1**（2026-10-13 起为 9 个）。
  给 face-pay 开 tracing 补齐后，已无模块缺这一环。
- **`@Transactional` 方法内 NEVER 提交异步通知任务**。事务未提交时发出的通知，若随后回滚会造成「对方已收到成功通知、本地状态已回退」。**MUST** 用 `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = false)`（首个落地样例：`pay-sign-server` 的 `paysign/event/SignResultCommittedEvent` + `SignResultCommittedListener`，2026-09-11 / 2.0.75）、`TransactionSynchronizationManager.registerSynchronization(...)` 的 `afterCommit`，或把通知移出事务方法。**Spring Event 在本项目只准用于这一件事**：一个事件类只挂一个监听器、两者同包成对命名、事件只带主键 + 不可变入向 DTO（**NEVER 带实体**，事务内的实体可能是脏快照，监听器 MUST 自己按键回查）；**NEVER** 拿它做解耦或跨模块通信（服务间只有 `rpc` 的 HTTP 直连，没有事件总线），**NEVER** 拿它替代「落库状态 + 扫表补偿」——事件是**进程内非持久**的，JVM 崩溃即丢，它只能当已落库任务的**快速路径**。监听器 **NEVER 加 `@Async`**（异常会彻底无声，默认执行器在虚拟线程下还有 pin 风险），内部 **MUST** `catch` 全部异常只记日志：`AFTER_COMMIT` 里抛异常回滚不了已提交的事务，只会让本已成功的回调对上游报错、引来重推。
- **`fallbackExecution = false` 在不带事务的类上等于「静默不执行」**，编译与单测都发现不了。因此**据文档挑「事务内通知」的改造点前 MUST 先 grep 目标类有没有 `@Transactional`** —— `face-pay-server` 整批服务类是**有意不带事务**的（`F2fTicketIssueService.java`、`F2fAppOrderService.java`，因为链路里有支付中心调用），在那里挂监听器会把通知直接丢掉；改成 `fallbackExecution = true` 更糟，等于「无事务时立刻同步发 HTTP」，退回该模块重写前的旧行为。详见 `docs/domain/decisions.md` 撤回 7。
- **`@Transactional` 方法内 NEVER 发起任何 RPC / 网络调用**。与上一条不是同一件事：上一条约束通知的**时机**，本条约束网络调用**不能被事务包住**。事务内调远端 ⇒ 行级排他锁的持有时长等于对端响应时长，上游对同一笔的重推会全部堆在同一行上串行等待；等待超过 Druid `remove-abandoned-timeout`（`resource/micro/sql-datasource/src/main/resources/sql.properties`）后连接被强杀，`commit` 抛 `connection closed`，**整个事务连同「留证据」的 INSERT 一起丢弃**，响应退化成全局异常处理器的 UUID `retCode`，上游继续重推——自我放大且没有出口。**MUST** 用 `afterCommit` 触发，或让该方法不带事务（每条 SQL 自动提交）。已发生事故：2026-08-26 21:07~21:14 生产，`PaySignWorkflow.receivePayResult` 事务内调 `syncDebitStatus`，订单 `GT20260826210647653586419` 循环重推 8 分钟，单次请求 287233ms、`UPDATE` 等锁 44997ms、`enq: TX - row lock contention` 持续；**循环期间 `PAY_CALLBACK_LOG` 零条落库**（每轮都被回滚），说明「回滚不丢证据」的前提在事务内并不成立。**该方法现宿主是 `PaymentDomainServiceImpl.receivePayResult`（2026-09-15 随拆分迁入，ADR-D87），事务注解自 ADR-D48 摘掉后一直没有、且是刻意的，NEVER 加回**（上面的事故事实不变）。
- **`spring.threads.virtual.enabled=true` 是全服务默认**（`resource/micro/web/src/main/resources/web.properties`；日志里的 `tomcat-handler-N` 就是虚拟线程命名，平台线程会是 `http-nio-<port>-exec-N`）。JDK 21 未落地 JEP 491，虚拟线程在 `synchronized` 内阻塞会 **pin 住载体线程**，而 ojdbc8 的 `PhysicalConnection` / `OracleStatement` 大量方法是 `synchronized`——一条 60s 慢 SQL 就是 60s 的 pin。载体线程池 `parallelism` 默认等于容器可见 CPU 数，CPU limit 偏小时一两条慢 SQL 即可 pin 满，**全 JVM 虚拟线程停止调度**，连 WebClient 响应的续体也唤不醒（同一事故的表现：对端 17ms 就答完，本端 287 秒后才处理响应；多个线程在同一毫秒集体苏醒）。因此 **NEVER 在请求线程上做长时间阻塞的 DB / IO**。取证 **MUST** 用 `jcmd <pid> Thread.dump_to_file -format=json`（`jstack` 看不到虚拟线程）、`-Djdk.tracePinnedThreads=full` 或 JFR 事件 `jdk.VirtualThreadPinned`；缓解手段见 `docker/Dockerfile` 的 `JAVA_TOOL_OPTIONS`。
- **状态机校验用白名单，不用黑名单**：只允许明确的前置状态继续处理，其余状态一律拒绝。**NEVER** 写成「非终态即可处理」——中间态往往尚未通过前置校验，放行等于绕过校验。
- **服务间调用**: **MUST** 通过 `rpc` 模块 + HTTP 直连（读取 `application.properties` 中 `service.*.url` 配置），**NEVER** 随意引入 Feign 或 Dubbo 等未经项目验证的 RPC 框架。新增内部接口 **MUST** 把 DTO 放 `model` 模块并在对应 Client 补方法，**NEVER** 只在本模块定义。
- **新增状态变更型接口 MUST 有鉴权与归属校验**。本项目多数业务模块**没有** spring-security、没有全局拦截器兜底，裸暴露的 POST 接口等于允许任何网络可达方按流水号操作任意用户数据。鉴权 **MUST** 对齐现有验签实现（`AccountRequestVerifier` / `ItpRequestSignVerifier`），**NEVER** 自造签名逻辑。
- **安全红线**: 涉及支付、密钥、签名的代码改动，**NEVER** 擅自修改现有加密/签名逻辑，**MUST** 提示人工复核安全合规性。
- **敏感配置**: 新增配置项时敏感项（口令 / 私钥 / signKey / 3DES 密钥 / token.secret）**MUST** 写成 `${ENV_VAR:}`（空默认值）并由 K8s Secret 注入，**NEVER** 写默认真值。**NEVER** 在对话、日志、提交信息或文档中回显密钥值，引用时只写键名与位置。生产 Oracle 地址、`DB_*` 环境变量与数据源已知坑见 `docs/ops/生产环境清单.md` §二、§三。

### 5.3 文档与工程化
- **事实快照 MUST 让位于实况**：本文档含少量会过期的事实（数字 / 版本 / 名单 / 日期，**含各章节内嵌的此类陈述**）；与现实冲突时 **MUST 以代码 / 集群实况 > `docs/business/<域>.md`、`docs/architecture/*.md`、`docs/ops/*.md`** 裁决，两份文档互相矛盾时以上游实况为准。
- **事实过期 MUST 直接改旧值，NEVER 追加补丁**（「此前写 X 已过期」式句子由 lint 机查）；变更脉络写 `docs/domain/decisions.md` 的 ADR；同一事实散落多处的按 §6 第 6 步一起改。
- 版本号 / 端口 / 任务编号 / 流量指向 / 名单口径 **MUST 现查**，NEVER 引用本文档里的数字当现状；**NEVER 新增 `Xxx.java:NNN` 形式的源码行号引用**（写文档 / 改文档时都算），行号会漂，MUST 按类名 / 方法名定位。
- 修改接口时，**MUST** 同步提示更新对应 `docs/business/<业务域>.md` 的接口清单及 Apifox 配置。
- **提交前 MUST 跑 `python3 docs/提示词/lint-prompts.py`，ERROR 必须清零**。它把以上纪律变成机械检查（历史补丁句 / 旧任务编号被当现状引用 / `.md` 间断链 / 常驻预算 / 单行长度 / **新增源码行号引用**）。**NEVER 用「我记得改过了」代替它** —— 2026-10-13 首次运行即在 39 篇约束层文档里查出 **68 处 ERROR**（55 处积压补丁句、9 处旧编号当现状引用、2 处断链、2 处误报判据）。`--fix` 自动删纯历史补丁句，`-v` 看全部 WARN，仍报 ERROR 的 MUST 手工处理。源码行号引用走**基线机制**（`docs/提示词/lint-baseline.json`）：存量按文件汇总成 WARN，**基线外的新增报 ERROR**（2026-10-13 已清扫 9962 处、基线为空 ⇒ 现存任何引用即新增、直接 ERROR）；日后再有存量，清一批后 MUST `--update-baseline`；`--deep` 把每条引用与源码树核对（约 13 秒，诊断用、不进提交门禁）。**第三层（技能 `~/.workbuddy/skills/qditp-*/SKILL.md`）默认同一套检查**（补丁句 / 旧编号 / 断链 / 行号引用 / 单行长度），且技能**不设存量基线**——任何行号引用都算新增（负向验证过）；`--no-skills` 可跳过。
- **仓库内不再维护「需求接口清单 / 实现进度报告 / 接口提取结果」这类汇总文件**（2026-08-25 全部删除：4 份进度报告在 23 分钟内给出 27.3%/50%/79.6%/78% 四个互相矛盾的实现率，「实现位置」列 50 行里 46 行误指 `daily-ticket-server`，且其中一份的数据源 `docs/API.md` 早已不存在）。判断「某接口是否存在 / 是否已实现」**MUST** 直接看 Controller 的 `@RequestMapping` / `@PostMapping` 注解，**NEVER** 重新生成这类汇总报告。
- 前端代码 **MUST** 遵循 Vue 3.5 Composition API (`<script setup>`) 及 Element Plus 规范。
- **模型升级后 MUST 重新评估常驻规则**：规则多是为当年模型短板写的，新模型已能自行处理的条目留着只是上下文成本与枷锁，该删就删。判据：这条规则改变的是模型行为，还是只是提醒一个真实陷阱——只有后者才留。

### 5.4 本机 `grep` / `sed` 是 toybox shim：`\|` / `\s` / `\b` **静默返回 0 结果**
多关键词 MUST `grep -rEn "A|B"` 或 `rg`（POSIX 类用 `[[:space:]]`）；**任何「grep 0 条」的结论，只要用了这三种写法，MUST 复验后才敢采信**（2026-10-13 曾因此把「文档里有」误判成「文档里没有」）。完整陷阱清单（`sed -i` 备份后缀 / `--include` 引号 / `awk length` 数字节 / 仓库根 `grep -rn` 扫进 `web/` → SIGTERM /「先怀疑 shim 再怀疑环境」推论）见 `.workbuddy/memory/MEMORY.md` §八 工具坑；comate / trae 用户见 `docs/提示词/引导词.md` 对应条目。

## 6. AI Workflow (AI 执行工作流)
当你接收到开发或修改任务时，**MUST** 严格按照以下步骤执行：

> **与 §2.2 的衔接**：两套「MUST 按顺序执行」是**前后关系、不是二选一**。若任务落在 §2.2 表格中的任一业务域，**MUST 先完成 §2.2 那套「业务提示词 → 原始规格 → 核心类源码」三层阅读**，再进入下面的步骤。

1. **Analyze (分析)**: 理解需求，确定影响的微服务模块和接口编号。
2. **Search (检索)**: 在 `model`, `rpc`, `micro` 中搜索是否已有可复用的 DTO、工具类或 RPC 接口。
3. **Plan (规划)**: 简述你的修改方案，列出需要新增/修改的文件，等待用户确认（若为简单 bug 修复可跳过）。
4. **Implement (实现)**: 编写代码，严格遵循上述 STRICT RULES。
5. **Review (自检)**: 检查是否误用了 `javax`、是否引入了新依赖、是否遗漏了幂等处理。**完成判据 MUST 可观察**：改了构建相关 ⇒ `mvn -o clean test-compile` 通过（§7）；改了提示词 / 文档 ⇒ `lint-prompts.py` ERROR 0（§5.3）。
6. **Document (回写提示词)**: 改动涉及**类名 / 方法名 / URL / 端口 / 状态取值 / 数据表 owner** 时 **MUST** 同步回写提示词，**NEVER 只改代码不回写**。四条要点：
   - **回写到哪**：业务域事实 → `docs/business/<业务域>.md`；跨域归属 / 边界变化 → `docs/domain/README.md`。**同一事实常散落在三处**（典型是 `AGENTS.md` §2.2.1、`docs/business/<域>.md`、`docs/architecture/*.md`），改 **MUST** 三处一起改，否则必然分叉。
   - **每条 MUST 标注改动日期与 ADR 号**，便于后人对齐；改动 `AGENTS.md` 的全局事实时 **MUST 同步检查 `docs/business/README.md`**（它是 AI 先读的索引，历史上长期滞后于本文）。
   - **改名 MUST 保留旧名做交叉引用**（例：`archiveIfLastChannelRemoved`（**旧名 `archiveUserInfoIfLastChannelRemoved`**）），否则后续按旧名检索会误判成文档自相矛盾。**ADR 正文与撤回记录 NEVER 删改**，只在原文旁补注现名。
   - **行号会漂、NEVER 依赖行号**：文档增长后旧行号引用必然失效，MUST 按类名 / 方法名定位（§7 与 `docs/business/account-employee-card.md` 均有此要求）。

   **这一步为什么不可省**：本仓库提示词（`AGENTS.md` + `docs/` + 技能）是全项目唯一的架构事实源；过期表述仍以 `MUST` / `NEVER` 的语气继续指挥后续改动，而**编译与单测都发现不了**。实测实例与检查方法见 `docs/reviews/提示词过期检查记录-2026-09-23.md`。

### 6.1 任务 → 必读路由（不用读完本文档就知道去哪）

**这是渐进式披露的入口**：本文档只放「日常 90% 场景都要遵守的硬约束」，其余按本表按需加载。
**任务命中本表任一行时，MUST 先读「先读」列，再动手。**

| 任务类型 | 先读 | 再读 |
|---|---|---|
| 改业务代码 | `docs/business/<域>.md`（索引 `docs/business/README.md`） | 该篇点名的核心类源码 |
| 部署 / 发版 / 排查线上版本 | 技能 `qditp-deploy-release` | `docs/ops/构建与部署规范.md` |
| 清测试数据 / 重跑场景 | 技能 `qditp-test-data-reset` | `docs/ops/测试数据退款操作手册.md` |
| 新增 / 改定时任务 | 技能 `qditp-scheduled-task` | `docs/architecture/web-server.md` §七 |
| 提交代码 | 技能 `qditp-svn-commit` | — |
| 退款清退 | 技能 `qditp-refund-execution` | `docs/ops/测试数据退款操作手册.md` |
| 支付宝链路排障 | 技能 `qditp-alipay-ride-diagnosis` | `docs/business/alipay-channel.md` |
| 现象有了但代码看着没错 | `docs/ops/排障手册.md` | 本文档 §5 |
| 切流量 / 排查请求落到哪 | `docs/ops/流量切换.md` | `docs/ops/K8S迁移清单.md` |
| 判「这个需求做过没有」 | `docs/business/未落地需求.md` | `docs/business/README.md` |
| 改表归属 / 拆库 / 跨服务调用 | `docs/domain/README.md` | `docs/domain/decisions.md` |
| 按域分库 / schema 拆分 / 迁移 | `docs/ops/数据库按域分库迁移手册.md`（v1.0 执行入口，待 DBA 审批） | `数据库按域分库计划.md` + `迁移DDL草案.md`（**NEVER 直接在生产库执行**）+ `scripts/migration/`；`docs/domain/README.md` |
| 启用 / 评估链路跟踪 | `docs/ops/链路跟踪全量接入评估.md` | `docs/ops/排障手册.md`（traceId 断链类症状） |
| 接甲方新需求 | `docs/business/未落地需求.md` | 该需求的规格原文 |

**技能不在当前上下文时，读 `~/.workbuddy/skills/qditp-<名>/SKILL.md`。**
**本表漏了某类任务时，MUST 补进本表，NEVER 让下一个 AI 重新摸索。**

## 7. Build & Run (构建与运行)

**本机三条硬约束**：SVN 服务器不可达 ⇒ 源码提交换有公司网络的环境；
JDK/Maven 由 mise 管 ⇒ **MUST** `mise exec -- mvn ...`（裸 `mvn` 落到系统 3.9.9）；
本机连不上集群 docker ⇒ 镜像 MUST 在 `k8s-master` 构建推送
（`scripts/deploy-to-harbor.sh` / `batch-deploy.sh` 路径问题跑不通，MUST 手动 scp+ssh）。

**构建顺序 NEVER 颠倒**：①`resource/micro`（改了自研持久层才需要，不在根 pom 聚合列表）
→ ②`model` + `rpc` → ③目标模块。
- `model` / `rpc` **版本号恒定（2.0.0 / 2.0.1）、NEVER 升级**，只 `mvn install` ——
  没先 install 会拿到 `~/.m2` 里的旧 jar，**后果是编译通过但打进镜像的是旧 class**，
  新增字段被 Fastjson2 静默丢弃。
- 验证改动 **MUST** `mvn -o clean test-compile`（增量被 target 缓存骗过）；
  **NEVER 靠全量 `mvn compile` 判断**（`employee-card-acc-simulator` 必然编译失败），
  跑 `ticket-server` 测试还 MUST 带 `-am`（`model` 新字段在旧 jar 里不可见）。
- 给 `model` 的 DTO 加字段后，**MUST 重建链路上每一个经手该 DTO 的模块镜像**，NEVER 只重建终点服务。

**出 jar**：MUST `-Djkube.skip=true` + **`-Dmaven.test.skip=true`**（`-DskipTests` 仍会跑 test-compile）；
`face-pay-server` **MUST 加 `-Premote`**（jkube 在 remote profile 且无 `activeByDefault`）。

**推镜像**：**NEVER 凭「有 execution + 有 phase」推断会推镜像** ——
`fep-acc-server` 的 goals 被整段注释、症状同样是「只出 jar 不报错」，MUST 看 `<goals>` 有没有未注释的 goal。
判成功 **MUST 在输出里找 `digest:` / `Pushed`**（两种措辞，按 `Pushed itp/` 过滤会漏掉带仓库全名的那种），
**NEVER 看退出码**（管道会吃掉 Maven 的退出码）。镜像名取自 `<image.prefix>`，
**NEVER 用模块目录名拼**（`fep-app-server` 的镜像是 `itp/fep-app`）。

**版本与回滚**：镜像 tag = pom `<version>`，**NEVER 同 tag 覆盖** ——
`rollout restart` 不会重新拉取、Harbor 上也没有旧镜像可回滚 ⇒ 每次部署 MUST 升末位 +0.0.1。
已发生覆盖时唯一补救是 **digest 强制**（`kubectl set image ... <repo>@sha256:<digest>`）。
**pom 版本 ≠ 线上版本**，判断 MUST 查 Deployment 的 image tag。

> 完整条文（推镜像的两套绑定、jkube profile 逐模块差异、容器内 `javap` 反查字节码、
> Harbor 凭据失效的误判、`web-admin` 版本号位置、`mvn clean` 失败、build-push.sh 删其他 jar 等）见
> [`docs/ops/构建与部署规范.md`](docs/ops/构建与部署规范.md)。
> 发布六步流程见技能 `qditp-deploy-release`。

## 8. Ops & Deployment (部署与运维，详见 docs/ops/)

涉及部署配置、环境变量、外部系统地址、密钥、上线核对的任务，
**MUST** 先读 [`docs/ops/README.md`](docs/ops/README.md) 的文档清单（含「何时读」列），再动手。

| 文档 | 用途 |
|---|---|
| `docs/ops/生产环境清单.md` | 服务部署清单、外部依赖、环境变量、网络开通、部署顺序、上线核对、已知环境问题 |
| `docs/ops/排障手册.md` | 「现象有了但代码看着没错」的排查入口；只做索引与排查顺序，规则条文以 §5 / §7 为准 |
| `docs/ops/流量切换.md` | 入向路由互切的**唯一 Runbook**（切流量前 MUST 先读，NEVER 只凭下面那条摘要） |
| `docs/ops/构建与部署规范.md` | §7 / §8 的完整条文 |

**环境判据（数字与地址 MUST 现查，NEVER 引用本行）**：
- **线上真实地址 / 版本 / 流量落点 MUST 查 K8s Deployment env 与 image tag**，
  NEVER 只看仓库 properties 或 pom（集群 env 会覆盖 jar 内配置；`deploy/k8s/` 的 manifest 只作模板）。
- **入向路由唯一开关是 `fep-app-vr` 这条 VirtualService**（8 条前缀）：
  MUST 用 `kubectl patch --type=json` 且带 `test` op 校验下标与原值；**NEVER 改 Service selector**
  （`targetPort` 陷阱：改端口只改 `targetPort`，NEVER 改 `port` / 调用方）。
  APP 域不走这条 VS，要切得改 `fep-app` Deployment 的 `service.*.url` env（键名带点）。
  **NEVER 用 `kubectl get vs -A` 摘要表判断前缀归属**（那张表不显示 path），MUST 读 envoy 或 `-o yaml`。
- **Quartz 用内存 JobStore**：`QRTZ_*` 恒 0 行 ≠ 任务没跑，MUST 查 `SYS_JOB_LOG`；
  运行期 INSERT/UPDATE `sys_job` 不生效，MUST 重启 web-admin；`misfire_policy=3` 表示重启期间不补跑。
- **双跑硬约束**：recon-server / web-admin / face-pay / collect-pay 的定时任务无分布式锁，MUST 单副本。
- 从 `k8s-master` 压测 MUST 用 `172.20.211.23:<NodePort>`；看日志 MUST 走 `scripts/klog.sh`
  （本机 kubeconfig 无 ITP 集群凭据）；**klog 只看尾部窗口且 Pod 一重启日志就丢**，
  全量检索须 `ssh k8s-master` 后逐 Pod grep 容器内日志文件。
- **`172.20.211.23:300xx` 网段裁决为测试环境**；即便如此，破坏性写库仍 MUST 先存原值并给还原 SQL。

> 完整条文（`service.*.url` 三类问题、404 伪装成 HTTP 200 + UUID retCode 的巡检手法、
> MCP 数据库工具的参数名坑、`*-schema.sql` 与 `*-migration.sql` 的分界、
> `testngbackV2` 7 处残留须连集群 env 一起查、外部网关地址 MUST 实测等）见
> [`docs/ops/构建与部署规范.md`](docs/ops/构建与部署规范.md)。

## 9. 文档地图
`docs/` 下有三类内容：**AI 提示词文档**（可改）、**甲方原始规格**（只读）、**外部系统接口规格**（按供方版本整体替换）。**没有第二份索引文件**——本节即唯一索引，`docs/README.md` 已于 2026-08-25 删除，**NEVER** 重建。

- `docs/提示词/` — **跨工具引导词**（`引导词.md`）：comate / trae 等无规则机制的工具开工前 **MUST 粘贴其正文**，并承担同样回写义务（§6 第 6 步）。只放跨工具通用的硬约束，业务细节一律靠 `docs/business/` 按需读，**NEVER 把业务事实抄进引导词**。它不是第二份索引
- `.workbuddy/memory/` — **项目记忆层**（仅 workbuddy 自动加载）：`MEMORY.md` 每次会话注入、`YYYY-MM-DD.md` 当日日记按需读；其 P0 事实同样按 §5.3「事实快照让位于实况」裁决
- `docs/history/trae-plans/` — **历史计划归档**（9 份，由 Trae 生成）：均为**方案草稿、非当前现状**，判断现状 **MUST** 以本文档与 `docs/business/` 为准。保留用于追溯「当初为什么这么设计」，**NEVER** 当作现状引用
- `docs/design/` — **设计方案稿**（两份：`用户分流渐进迁移设计.md` + `用户分流渐进迁移设计-定稿.md`，fep-app 接入层新老用户分流，均已按 2026-10-14 定案对齐）。设计稿 ≠ 现状，**NEVER 当作现状引用**；落地后 MUST 把结论同步进 `docs/business/app-gateway.md` 并更新文首状态行
- `docs/business/` — 业务域编码提示词（写业务代码前读，索引 `README.md`）
- `docs/domain/` — **领域边界与聚合归属**（索引 `README.md`）。比 `docs/business/` 高一层：`business` 回答「这个业务怎么实现」，`domain` 回答「这个东西归谁管、边界在哪」。**调整表归属、拆库、评审外部架构方案、判断跨服务调用是否合理时 MUST 先读**。含六域划分与目标 Schema、四条边界自查判据（双向 RPC 即边界画错 / 1:N 子实体不能当父状态机的状态 / 热路径写入定 owner / 域内应能本地事务强一致）、对外契约判据（**能被 `parseBizData` 解析的 DTO 就是对外契约，NEVER 加字段**）；`state-machines.md` 记四台在跑的状态机与「枚举 + 白名单 + CAS」三件套（**已决定不引 Spring Statemachine**）；`outbox.md` 记「落库状态 + 扫表补偿」模板（**NEVER 抽一张公共 outbox 表**——跨域、违反边界判据 3，见 ADR-D46）；`decisions.md` 记 ADR 与**撤回记录**。**撤回记录 NEVER 删** —— 已记 9 条被实证推翻的架构判断（拆库 / 签约表归属 / 列长判据 / 事务内通知候选等，逐条见该文件），保留是为防止重新推导出同一个错误结论。**追加新 ADR 前 MUST 用 `grep -n '^#\{2,3\} ADR-D'` 查已用编号，NEVER 只匹配 `^## `** —— 文件里有 `###` 级 ADR，只查二级标题会把已占用的号判成空号（2026-09-15 因此把一条错写成 D86、返工改 D88）。
- `docs/architecture/` — 架构总览、公共构件、管理后台技术设计
- `docs/ops/` — 部署与配置清单。**排障手册.md** 是「现象有了、但代码看着没错」时的排查入口（六类高频症状 + 部署后五个不可靠信号 + 两条「不是 bug」的误报）；它**只做索引与排查顺序，规则条文以本文 §5 / §7 为准，NEVER 在其中复述规则**。**数据库按域分库计划.md**（草案 v2）是按域拆库规划总纲：方案 A 逻辑分库 + 4 用户库（`QDITP_USER`/`QDITP_TXN`/`QDITP_RIDE`/`QDITP_PLAT`）、M0~M4 路径、CTAS 快照兜底，**归属清单实测覆盖 127/162 业务表、34 张漏统待补**，尚待甲方 DBA 校准；**数据库按域分库迁移手册.md**（+ `迁移DDL草案.md`，**NEVER 直接在生产库执行**，脚本在 `scripts/migration/`）是分库执行入口，`链路跟踪全量接入评估.md` 记 tracing 分三档接入结论
- `docs/external/` — **外部系统接口规格原文**（非甲方规格、非本项目设计）。当前只有 `支付中心网关接口文档.md`（bestonepay，用户 2026-08-26 提供）。改动 `pay.sign.*` 配置、`PaySignGatewayMessages` 的出向 bizData 组装（原在 `PaySignWorkflow`，2026-09-15 已收口到该 support 类，ADR-D87）、`PayGatewayClient` 或支付/签约/解约/退款回调解析前 **MUST** 先核对该文档。供方发新版本时整体替换，**NEVER** 在其中混入本项目的实现说明或差异分析（差异写 `docs/business/pay-sign.md` 与 `docs/ops/生产环境清单.md`）。
- `docs/reviews/` — 代码审查记录（按链路+日期归档，只增不改）；**提示词过期检查记录也归档在此**（当前 `提示词过期检查记录-2026-09-23.md`，含类名 / 方法名 / URL / 端口四类比对方法，以及重做检查时 MUST 避开的六个脚本坑）。**重做一次全面提示词核对前 MUST 先读它**，避免重复踩坑与重复报同一条。
- `docs/接口规范文档/`、`docs/业务需求文档/`、`docs/技术规范文档/` — 甲方原始规格，**只有 `.docx` / `.doc` 原文，只读，NEVER 修改**。这三个目录下原有的 8 个 `*接口清单.md` / `.json` 是 2026-08-25 之前脚本抽取的派生产物（接口数与源文档不符、部分源文件已不存在），已全部删除；**NEVER** 在这三个目录内新增派生文件。
- **缺失的甲方文档**：《技术规范-第4部分-系统接口规范》（`0x2001~0x9004` 报文码定义）在仓库内**没有源文件**，需向甲方索取。涉及该编号体系的任务 **MUST** 先告知用户文档缺失。