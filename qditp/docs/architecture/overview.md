# 架构总览（qd-itp）

> 本文档承载 `AGENTS.md` §3 移出的详细信息：真实端口、调用拓扑、配置现状、部署链路。
> 端口与配置值均为从各模块 `src/main/resources/application.properties|yml` 实读，非推测。

## 一、分层与通信形态

```
外部：APP / 闸机AGM / TVM / BOM / ACC清分中心 / 支付中心 / 支付宝
                    │
  ① 接入层(FEP)      fep-app-server  fep-dev-server  fep-acc-server  fep-alipay-server
                    │  （薄网关：解析 ItpCommonFormRequest → RPC 转发，多数无数据库）
  ② 业务服务层       account / ticket / pay-sign / face-pay / daily-ticket
                    │  gate-txn-pay / card-pool / recon / trans-query（已建未接线）
                    │  （事务与落库在这一层；collect-pay 已退居后台、仅留内部端点）
  ③ 公共能力层       para  key  blacklist  industry-data
                    │
  ④ ACC / 安全层     acc-security(HSM)   acc-secure(未接线)   acc-es(FTP+Netty)

  旁路：web-server(管理后台，只读写 sys_*) + web(前端)
  构件：model(DTO/枚举)  rpc(Client)  resource/micro(持久层与web组件)
```

关键约束（**与常规 Spring Cloud 假设完全不同，勿推断**）：

- **无注册中心、无网关、无 Feign / Dubbo、无服务发现**。调用由 `rpc` 模块 `ProxyWebClient`（WebClient）发起 HTTP 直连。
- **不使用 Redis 与消息队列**。异步与重试 = `@Scheduled` 扫库 + 重试状态字段 / 防重表。
- 所有 Maven 模块在**仓库根目录**，无 `micro/`、`common/` 父目录。
- 根 `pom.xml` 共 23 个 `<module>`；`resource/micro`、`dsh-orchestrator`、前端 `web` **不在根聚合构建中**。

## 二、真实端口与服务名

| 模块 | 端口 | `spring.application.name` | 备注 |
|---|---|---|---|
| pay-sign-server | 9096 | pay-sign | |
| account-server | 9098 | account | |
| collect-ticket-server | 9098 | collect-ticket | ⚠️ 与 account-server 冲突 |
| acc-secure-server | 9099 | acc-secure | 未被任何模块调用 |
| fep-app-server | 9101 | fep-app | |
| blacklist-server | 9102 | blacklist | |
| ticket-server | 9103 | ticket-server | |
| key-server | 9103 | key | ⚠️ 与 ticket-server 冲突 |
| fep-dev-server | 9104 | fep-dev | |
| industry-data-server | 9105 | industry-data-server | |
| gate-txn-pay-server | 9106 | gate-txn-pay | |
| para-server | 9107 | para-server | |
| daily-ticket-server | 9108 | daily-ticket-server | |
| fep-acc-server | 9110 | fep-acc-server | |
| card-pool-server | 9111 | card-pool | 无 `@Scheduled`，维护动作走 `POST /card-pools/maintenance` |
| recon-server | 9112 | recon | 无 `@Scheduled`，由 web-admin `sys_job` 225 触发 `POST /internal/recon/daily/run`（2026-09-21 由 109 改号）；**MUST 单副本** |
| trans-query-server | 9113 | trans-query | APP 交易查询独立服务（1.0.4）；**已出镜像但未接线**，三条 URL 与 ticket-server 并存 |
| acc-security-server | 9012 | acc-security | Undertow |
| acc-es-server | 9011 / Netty 5000 | acc-es | |
| face-pay-server | 58101 | face-pay | **TVM / BOM / APP 当面付现行主模块**（2026-09-15 起承接设备与 APP 流量，ADR-D85）；**7 个 `@Scheduled` 分布在 6 个类**，无分布式锁，**MUST 单副本** |
| collect-pay-server | 58101 | **itpagm** | ⚠️ **与 face-pay-server 端口冲突**（同机部署需确认）；**已退居后台**，只留 `/internal/recon/export`、`/internal/app-order/**` 与旧单退款，**NEVER 停掉它**；与 face-pay 同用 `application.yml`；服务名与模块名不一致 |
| fep-alipay-server | 8080 | fep-alipay | ⚠️ 8080 四方冲突 |
| alipay-pay-sign-server | 8080 | alipay-pay-sign | ⚠️ |
| alipay-account-server | 8080 | alipay-account | ⚠️ |
| web-server / web-admin | 8080 | **未设置** | ⚠️ |

## 三、调用拓扑（按 `service.*.url` 实测）

`rpc/src/main/java` 下的构成：一组 Client + `URLDynamicRouter` + 一组 `@EnableRpcXxx` 注解（数量随新增模块变化，**勿引用固定计数**）。Client 与其配置键：

| Client | 配置键 | 目标模块 |
|---|---|---|
| `AccountClient` | `service.account.url` | account-server（支付宝域指向 alipay-account-server） |
| `CardPoolClient` | `service.cardPool.url`（默认 `card-pool-service`） | card-pool-server |
| `TicketClient` | `service.ticket.url` | ticket-server |
| `PaySignClient` | `service.paySign.url` | pay-sign-server |
| `GateTxnPayClient` | `service.gateTxnPay.url` | gate-txn-pay-server |
| `DailyTicketClient` | `service.dailyTicket.url` | daily-ticket-server |
| `CollectPayClient` | `service.collectPay.url`（默认 `collect-pay-service`） | collect-pay-server |
| `ParaClient` | `service.para.url` | para-server |
| `KeyClient` | `service.key.url` | key-server |
| `BlacklistClient` | `service.blacklist.url` | blacklist-server |
| `IndustryDataClient` | `service.industryData.url` | industry-data-server |
| `SecurityClient` | `service.security.url` | acc-security-server |
| `FepDevClient` | `service.fepDev.url` | fep-dev-server |
| `AlipayAccountClient` | `service.account.url` | alipay-account-server |
| `AlipayPaySignClient` | `service.alipay-pay-sign.url`（默认 `http://alipay-pay-sign-server:8080`） | alipay-pay-sign-server |
| `URLDynamicRouter`（非 Client） | `service.route.url.openLogger` | 动态路由辅助类，目标由入参决定 |

> ⚠️ **不存在 `collect.ticket.*` 配置键**（旧版本文档误记）。`pay.center.*` 是支付中心网关地址，与 `CollectPayClient` 无关。

各模块出向依赖（依据启动类 `@EnableRpcXxx` + 实际 `@Autowired` 注入点核对，2026-08-25）：

- **fep-app-server** → account、security、paySign、blacklist、key、ticket、industryData、para、dailyTicket、**collectPay**、**alipayAccount**
- **fep-dev-server** → account、security、ticket、gateTxnPay、para、**key**、**alipayAccount**、**alipay-pay-sign**
- **fep-acc-server** → account
- **fep-alipay-server** → account(alipay)、paySign、blacklist、ticket、**alipay-pay-sign**；`security` / `key` / `para` **只有配置键、代码未注入 Client**
- **ticket-server** → security、industryData、account、alipayAccount、para、fepDev、gateTxnPay、dailyTicket、**paySign**
- **account-server** → ticket、security、paySign
- **pay-sign-server** → account、blacklist、**gateTxnPay**（+ 支付网关 `PayGatewayClient`）
- **gate-txn-pay-server** → paySign、**account**、**para**
- **key-server** → security
- **industry-data-server** → security
- **blacklist-server** → **alipay-pay-sign**（`@EnableRpcPaySign` + `service.alipay-pay-sign.url`）
- **collect-pay-server** → **ticket**、**account**（`application.yml` 中 `service.ticket.url` / `service.account.url`）
- **collect-ticket-server** → **collect-pay**（自有 `client/CollectPayClient`，经 `URLDynamicRouter` 解析）
- **para-server** → **security**、**route**（`@EnableRpcRoute` / `@EnableRpcSecurity`）
- **alipay-pay-sign-server** → account(alipay)、blacklist、para；`security` / `key` / `ticket` / `industryData` **只有配置键、代码未注入 Client**
- **alipay-account-server** → ticket
- **web-server** → 无（不调用任何业务服务，仅共库读写 `sys_*`）

> ⚠️ **三处依赖存在但 URL 键完全没配**，运行时落到 `rpc` 默认服务名，而这些名字与生产集群实际 Service 名不一致，**集群内无法解析**：
> - `ticket-server` 缺 `service.paySign.url` → 默认 `pay-sign-service`
> - `fep-app-server` 缺 `service.collectPay.url` → 默认 `collect-pay-service`
> - `pay-sign-server` 缺 `service.gateTxnPay.url` → 默认 `gate-txn-pay-service`
>
> 详见 `../ops/生产环境清单.md` 问题清单。

### ⚠️ 配置现状问题（排查故障时先看这里）

> **2026-09-16 逐项复核：下面 ①②③ 均已修复，只有 ④ 仍成立。** 修复前的旧值在此仅作历史留痕，**NEVER 当作现状引用** —— 按旧记载排查会得出「线上正在错配」的错误结论。

1. ~~`service.industryData.url` 指向 9104（fep-dev-server）~~ **已修复**：`fep-app-server:32`、`fep-alipay-server:32`、`alipay-pay-sign-server:43` 三处现均为 `http://industry-data-server-6dv5u-svc.itp.svc:30018`（K8s DNS + NodePort）。
2. ~~`fep-dev-server:15` 的 `service.ticket.url=http://127.0.0.1:9097`~~ **已修复**：现为 `http://ticket-server-bsyju-svc.itp.svc:9100`（`fep-dev-server/application.properties:29`，该行上方注释仍留着旧值说明）。
3. ~~地址形态不统一 + 四处生产错配~~ **已全部修复并统一为 K8s DNS**：ticket-server 现为 `alipay-account-server-2n6kc-svc:30021`、`gate-txn-pay-server-jomf4-svc:30019`、`daily-ticket-server-rdbe5-svc:30027`；`service.fepDev.url` 已按 **ADR-D63（2026-09-14）整体删除**，不再是错配项。
   ⚠️ **Service 端口 ≠ 容器 `server.port`，这不是错配**：如 ticket-server 的 Service 端口是 **9100**、容器 `server.port` 是 **9103**；recon-server 的 Service 端口（NodePort 30034）也不等于容器 9112。排障时 **MUST** 先分清说的是哪一层，切勿据此改配置。
4. **仍存在**：`acc-secure-server:11`、`collect-ticket-server:11` 的 `service.token.url` 为空。

修改这些值属**部署配置变更**，会影响线上路由，**MUST** 先与用户确认环境再动手。

## 四、部署链路

- 镜像基底：`docker/Dockerfile` —— `FROM os-harbor-svc.default.svc.cloudos:443/itp/jdk:21` + `COPY app.jar /app.jar`
- 编排：各模块 pom 内 `org.eclipse.jkube:kubernetes-maven-plugin`（部分模块把它注释掉了，如 `ticket-server/pom.xml:115`）
- 脚本：`scripts/deploy-to-harbor.sh`（单服务）、`scripts/batch-deploy.sh`（串行批量）
- 日志辅助：`scripts/klog.sh`、`scripts/klog-watcher.sh`

⚠️ `scripts/batch-deploy.sh` 与 `scripts/deploy-to-harbor.sh` **各有一份 `ALL_SERVICES`，两处 MUST 保持一致**（2026-09-16 已校对统一为 24 项，改动时 MUST 同时改两处）。
历史坑（已修，勿回退）：原清单含仓库内**根本不存在**的 `wallet-server`、`online-server`，而脚本**失败即 `break`**，全量部署会在第二个服务就中断；同时漏了 `face-pay-server`（现行主模块）、`recon-server`、`card-pool-server` 等 9 个模块。
**服务名必须是仓库根目录下的模块目录名** —— `deploy-to-harbor.sh` 按 `${LOCAL_BASE_DIR}/${NAME}/target` 找 `${NAME}-*.jar`，因此 **`web-admin`（位于 `web-server/` 下）不适用本脚本，勿加入**。另：`ticket-server` 的 `kubernetes-maven-plugin` 在 `ticket-server/pom.xml:115` 被注释掉，能否构建镜像需先确认。上述补齐项按 pom 是否声明 JKube 插件判定，**未经实际部署验证**，首次执行前 **MUST** 与运维核对。

## 五、构建

```bash
mvn clean package -DskipTests                          # 全量
mvn clean package -pl <module-name> -am -DskipTests    # 单模块（含依赖）
```

`resource/micro` 不在根聚合中，修改自研持久层后 **MUST** 先单独 `mvn install` 该模块再构建业务模块。

## 六、相关文档

- 业务域提示词索引：[`docs/business/README.md`](../business/README.md)
- 公共构件（model / rpc / resource-micro）：[`common-components.md`](common-components.md)
- 管理后台：[`web-server.md`](web-server.md)
- 生产环境清单（含 Oracle 地址与环境变量）：[`../ops/生产环境清单.md`](../ops/生产环境清单.md)
- 生产环境清单：[`../ops/生产环境清单.md`](../ops/生产环境清单.md)
