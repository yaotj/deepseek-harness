# 架构总览（qd-itp）

> 本文档承载 AGENTS.md §3「Architecture & Modules (架构与模块)」移出的架构事实：分层、模块归属、端口约定、调用拓扑。
>
> - 端口默认值来自各模块 `src/main/resources/application.properties|yml`；**实际容器端口以 K8s Deployment 注入的 `SERVER_PORT` 为准**（未注入则使用默认值）。Service `targetPort` / NodePort 见具体环境文档，MUST 现查。
> - 所有服务间调用由 `rpc` 模块 `ProxyWebClient`（WebClient）通过 `service.*.url` 发起 HTTP 直连，**无注册中心、无网关、无 Feign / Dubbo、无服务发现**。

## 一、分层与通信形态

```
外部：APP / 闸机 AGM / TVM / BOM / ACC 清分中心 / 支付中心 / 支付宝
                    │
  ① 接入层(FEP)      fep-app-server  fep-dev-server  fep-acc-server  fep-alipay-server
                    │  （薄网关：解析 ItpCommonFormRequest → RPC 转发，多数无数据库）
  ①-1 分流网关       route-sentinel（SCG 透明网关，旁路分流 APP/设备部分流量）
                    │
  ② 业务服务层       account / ticket / pay-sign / face-pay / daily-ticket
                    │  gate-txn-pay / card-pool / recon / trans-query
                    │  （事务与落库在这一层）
  ③ 公共能力层       para  key  blacklist  industry-data
                    │
  ④ ACC / 安全层     acc-security(HSM)   acc-secure(未部署)   acc-es(FTP+Netty)

  旁路：web-server(web-admin，只读写 sys_*) + web（前端）
  构件：model(DTO/枚举)  rpc(Client)  resource/micro(持久层与 web 组件)
```

关键约束（**与常规 Spring Cloud 假设完全不同，勿推断**）：

- **无注册中心、无网关、无 Feign / Dubbo、无服务发现**。调用由 `rpc` 模块 `ProxyWebClient`（WebClient）发起 HTTP 直连。
- **不使用 Redis 与消息队列**。异步与重试 = `@Scheduled` 扫库 + 重试状态字段 / 防重表。
- 所有 Maven 模块在**仓库根目录**，无 `micro/`、`common/` 父目录。
- 根 `pom.xml` 共 27 个 `<module>`；`resource/micro`、前端 `web` **不在根聚合构建中**。
- `route-sentinel` 是独立 Spring Cloud Gateway 项目，目录 `../route-sentinel/`，部署见 `deploy/k8s/10-deployments/route-sentinel.yaml`，业务契约见 [`../business/route-sentinel.md`](../business/route-sentinel.md)。

## 二、模块清单与端口约定

> **容器端口 ≠ Service `targetPort` ≠ NodePort**。例：`ticket-server` 容器默认 `8080`，Deployment 注入 `SERVER_PORT=9100`，Service `targetPort=9100`，NodePort `30014`。排障 MUST 先分清说的是哪一层，切勿据此改配置。

### 在线服务

| 模块 | `spring.application.name` | 默认端口 | 容器有效端口 | 备注 |
|---|---|---|---|---|
| account-server | account | 8080 | 9098（env） | DB |
| acc-security-server | acc-security | 9012 | 9012 | Undertow，HSM；无 DB |
| acc-es-server | acc-es | 8080 / Netty 5000 | 8080 | FTP + Netty |
| alipay-account-server | alipay-account | 8080 | 8080 | DB |
| alipay-pay-sign-server | alipay-pay-sign | 8080 | 8080 | DB |
| blacklist-server | blacklist | 8080 | 9102（env） | DB |
| card-pool-server | card-pool | `${CARD_POOL_PORT:8080}` | 8080 | DB；无 `@Scheduled` |
| daily-ticket-server | daily-ticket-server | 8080 | 9108（env） | DB |
| face-pay-server | itpagm | 58101 | 58101 | DB；TVM/BOM/APP 当面付主模块；**MUST 单副本** |
| fep-acc-server | fep-acc-server | 8080 | 9110（env） | 无 DB |
| fep-alipay-server | fep-alipay | 8080 | 8080 | 无 DB |
| fep-app-server | fep-app | 8080 | 9101（env） | 无 DB |
| fep-dev-server | fep-dev | 8080 | 9104（env） | 无 DB |
| gate-txn-pay-server | gate-txn-pay | 8080 | 8080 | DB |
| industry-data-server | industry-data-server | 8080 | 8080 | 无 DB |
| key-server | key | 8080 | 8080 | DB |
| para-server | para-server | 8080 | 9107（env） | DB |
| pay-sign-server | pay-sign | 8080 | 8080 | DB |
| recon-server | recon-server | `${SERVER_PORT:8080}` | 9112（env） | DB；第三方对账默认关闭；主链由 web-admin `sys_job` 触发；**MUST 单副本** |
| ticket-server | ticket-server | 8080 | 9100（env） | DB |
| trans-query-server | `${SPRING_APPLICATION_NAME:trans-query-server}` | `${SERVER_PORT:8080}` | 9113（env） | 无 DB；2026-09-20 起 `fep-alipay` 的 `/channel/findTravelList` 已切到本服务；APP 域三条 URL 仍在 ticket-server |
| web-server(web-admin) | web-server | 8080 | 8080 | 无 DB；只读写 `sys_*` |
| web（前端） | — | — | — | nginx；承载综管台 UI |
| route-sentinel | route-sentinel | 8080 | 8080 | SCG 透明网关；项目目录 `../route-sentinel/` |

### 已下线 / 未部署

| 模块 | 状态 | 说明 |
|---|---|---|
| collect-pay-server | 2026-10-13 已下线（0 pod） | 入向流量、对账旧表源、`NoticeAppTask` 4 端点、web-admin 菜单均已切 face-pay；残留代码勿部署 |
| collect-ticket-server | 未部署 | 无流量 |
| acc-secure-server | 未部署 | 死模块；未被任何模块调用 |

## 三、调用拓扑（按 `service.*.url`）

`rpc/src/main/java` 下为一组 Client + `URLDynamicRouter` + 一组 `@EnableRpcXxx` 注解。Client 与配置键、目标模块对应关系：

| Client | 配置键 | 目标模块 / 说明 |
|---|---|---|
| `AccountClient` | `service.account.url` | account-server |
| `AlipayAccountClient` | `service.alipayAccount.url`（回退 `service.account.url`） | alipay-account-server |
| `AlipayPaySignClient` | `service.alipay-pay-sign.url` | alipay-pay-sign-server |
| `BlacklistClient` | `service.blacklist.url` | blacklist-server |
| `CardPoolClient` | `service.cardPool.url` | card-pool-server |
| `CollectPayClient` | `service.collectPay.url` | collect-pay-server（已下线） |
| `DailyTicketClient` | `service.dailyTicket.url` | daily-ticket-server |
| `F2FClient` | `service.f2f.url` | face-pay-server |
| `FacePayClient` | `service.facePay.url` | face-pay-server |
| `FepDevClient` | `service.fepDev.url` | fep-dev-server |
| `GateTxnPayClient` | `service.gateTxnPay.url` | gate-txn-pay-server |
| `IndustryDataClient` | `service.industryData.url` | industry-data-server |
| `KeyClient` | `service.key.url` | key-server |
| `ParaClient` | `service.para.url` | para-server |
| `PaySignClient` | `service.paySign.url` | pay-sign-server |
| `ReconClient` / `ReconExportClient` | `service.recon.url` | recon-server |
| `SecurityClient` | `service.security.url` | acc-security-server |
| `TicketClient` | `service.ticket.url` | ticket-server |
| `TransQueryClient` | `service.transQuery.url` | trans-query-server |

> `URLDynamicRouter` 不是 Client，配置键为 `service.route.url.openLogger`，目标由入参动态决定。

模块出向依赖（按启动类 `@EnableRpcXxx` + 实际注入点，2026-10-21 核查）：

- **fep-app-server** → account、alipayAccount、blacklist、collectPay（死配置）、dailyTicket、facePay、gateTxnPay、industryData、key、para、paySign、ticket、transQuery
- **fep-dev-server** → key、ticket（另启用 `@EnableRpcRoute`，可经 `URLDynamicRouter` 动态转发）
- **fep-acc-server** → account
- **fep-alipay-server** → account、alipayAccount、alipayPaySign、blacklist、industryData、paySign、ticket、transQuery
- **ticket-server** → account、alipayAccount、dailyTicket、gateTxnPay、industryData、para、paySign、recon、ticket（内部复用）
- **account-server** → cardPool、gateTxnPay、paySign、security、ticket
- **pay-sign-server** → account、blacklist、gateTxnPay
- **gate-txn-pay-server** → account、alipayPaySign、para、paySign、ticket
- **blacklist-server** → alipayPaySign、gateTxnPay、paySign
- **alipay-account-server** → cardPool、ticket
- **alipay-pay-sign-server** → alipayAccount、blacklist、gateTxnPay、para、ticket
- **key-server** → security
- **industry-data-server** → security
- **para-server** → （启用 `@EnableRpcRoute` 与 `@EnableRpcSecurity`；无直接注入的 Client）
- **daily-ticket-server** → recon
- **recon-server** → recon（内部导出 Client）
- **face-pay-server** → account、gateTxnPay、ticket
- **trans-query-server** → alipayPaySign、dailyTicket、gateTxnPay、para、paySign、ticket
- **acc-security-server** → `SocketClient`（HSM 专用，非 rpc Client）
- **acc-es-server** → 无 rpc Client
- **card-pool-server** → 无 rpc Client
- **web-server** → account、blacklist、cardPool、f2f、facePay、gateTxnPay、para、paySign、recon（共 9 个 `@EnableRpcXxx`）

> `collectPay` 已下线，相关配置键属死配置；新增模块 / 切流后 MUST 回写本文。

### 配置现状红线

1. **容器端口 ≠ Service 端口**。Deployment `SERVER_PORT` 决定容器监听端口；K8s Service `targetPort` 必须与之相同。例：`ticket-server` 容器 9100、`recon-server` 容器 9112。
2. **`service.*.url` 缺配会落到 rpc 默认服务名**。部分模块代码中使用了某 Client，但 `application.properties` 未显式配置对应 URL，运行期可能解析到不存在的默认 Service 名。**排障时 MUST 现查**具体模块的 `application.properties` 与 Deployment env。
3. **线上真实地址 MUST 现查 K8s Deployment env**，NEVER 只凭仓库 properties 推断；`deploy/k8s/` 的 manifest 是模板，集群 env 会覆盖 jar 内配置。

## 四、构建与部署（指针）

- 构建：见 `AGENTS.md §6「Build (构建)」`；`resource/micro` 不在根聚合，修改后须先 `mvn install`。
- 部署 / 滚更 / 镜像：见 `AGENTS.md §7「Ops & Deployment」` 与 [`../ops/README.md`](../ops/README.md)。
- 流量切换 / VirtualService：见 [`../ops/流量切换Runbook.md`](../ops/流量切换Runbook.md)。

## 五、相关文档

- 业务域索引：[`docs/business/README.md`](../business/README.md)
- 公共构件（model / rpc / resource-micro）：[`common-components.md`](common-components.md)
- 综管台开发工作流：[`web-admin-development-workflow.md`](web-admin-development-workflow.md)
- 管理后台结构事实：[`web-server.md`](web-server.md)
- 用户分流透明网关：[`../business/route-sentinel.md`](../business/route-sentinel.md)
- 环境与部署清单：[`docs/ops/README.md`](../ops/README.md) → `environments/`
