# 架构总览（qd-itp）

> 本文档承载 `AGENTS.md` §3 移出的详细信息：真实端口、调用拓扑、配置现状、部署链路。
> 端口与配置值均为从各模块 `src/main/resources/application.properties|yml` 实读，非推测。

## 一、分层与通信形态

```
外部：APP / 闸机AGM / TVM / BOM / ACC清分中心 / 支付中心 / 支付宝
                    │
  ① 接入层(FEP)      fep-app-server  fep-dev-server  fep-acc-server  fep-alipay-server
                    │  （薄网关：解析 CommonFormRequest → RPC 转发，多数无数据库）
  ② 业务服务层       account / ticket / pay-sign / collect-pay / collect-ticket
                    │  daily-ticket / gate-txn-pay   （事务与落库在这一层）
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
| acc-security-server | 9012 | acc-security | Undertow |
| acc-es-server | 9011 / Netty 5000 | acc-es | |
| collect-pay-server | 58101 | **itpagm** | 唯一使用 `application.yml`；服务名与模块名不一致 |
| fep-alipay-server | 8080 | fep-alipay | ⚠️ 8080 四方冲突 |
| alipay-pay-sign-server | 8080 | alipay-pay-sign | ⚠️ |
| alipay-account-server | 8080 | alipay-account | ⚠️ |
| web-server / web-admin | 8080 | **未设置** | ⚠️ |

## 三、调用拓扑（按 `service.*.url` 实测）

`rpc/src/main/java` 下共 **28 个类**：**14 个 Client** + `URLDynamicRouter` + **13 个 `@EnableRpcXxx` 注解**。Client 与其配置键：

| Client | 配置键 | 目标模块 |
|---|---|---|
| `AccountClient` | `service.account.url` | account-server（支付宝域指向 alipay-account-server） |
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

1. **`service.industryData.url` 指向 9104（fep-dev-server），而 industry-data-server 实际是 9105。**
   出现在 `fep-app-server:27`、`fep-alipay-server:31`、`alipay-pay-sign-server:33`。三处一致，疑为历史端口变更后未同步。
2. **`fep-dev-server:15` 的 `service.ticket.url=http://127.0.0.1:9097`**，而 ticket-server 是 9103，9097 无任何模块占用。
3. **地址形态不统一**：`ticket-server` 全部指向 `172.20.211.23:300xx`（**这是生产集群 NodePort**，2026-08-25 用户确认）；多数模块指向 `127.0.0.1`；`alipay-*` 与 `fep-alipay-server` 用 K8s DNS `http://alipay-account-server:8080`。同一份代码在不同环境需依赖外部覆盖。
   实测的 NodePort 映射表与逐项比对结果见 [`../ops/生产环境清单.md`](../ops/生产环境清单.md) §一「当前集群 NodePort 映射」——其中 `service.fepDev.url` 指向了 ticket-server 自身、`service.alipayAccount.url` 指向 key-server、`service.gateTxnPay.url` 用了容器端口、`service.dailyTicket.url` 仍是 `127.0.0.1`，四处均为**生产在跑的错配**。
4. `acc-secure-server:11`、`collect-ticket-server:11` 的 `service.token.url` 为空。

修改这些值属**部署配置变更**，会影响线上路由，**MUST** 先与用户确认环境再动手。

## 四、部署链路

- 镜像基底：`docker/Dockerfile` —— `FROM os-harbor-svc.default.svc.cloudos:443/itp/jdk:21` + `COPY app.jar /app.jar`
- 编排：各模块 pom 内 `org.eclipse.jkube:kubernetes-maven-plugin`（部分模块把它注释掉了，如 `ticket-server/pom.xml:115`）
- 脚本：`scripts/deploy-to-harbor.sh`（单服务）、`scripts/batch-deploy.sh`（串行批量）
- 日志辅助：`scripts/klog.sh`、`scripts/klog-watcher.sh`

⚠️ `scripts/batch-deploy.sh` 的 `ALL_SERVICES` 清单含 `wallet-server`、`online-server`，**仓库内不存在这两个模块**；同时缺失 daily-ticket-server、alipay-*、fep-acc-server、fep-alipay-server、web-server。清单已过期，全量部署前 **MUST** 提示用户核对。

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
- 敏感配置外置方案：[`../ops/敏感配置外置方案.md`](../ops/敏感配置外置方案.md)
- 生产环境清单：[`../ops/生产环境清单.md`](../ops/生产环境清单.md)
