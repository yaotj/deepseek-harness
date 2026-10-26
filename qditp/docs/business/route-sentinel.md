---
业务域: 用户分流透明网关（SCG）
模块: route-sentinel（上级目录独立项目）
---

# 提示词：用户分流透明网关（route-sentinel）

## 何时读本文件

需要新增/修改 **设备域（`/itptvm/`、`/itpbom/`）** 或 **APP 域订单支付相关** 的分流路由、名单判定逻辑、网关 Actuator 接口，或排查「新老用户该打旧 ITP 还是新系统」的流量归属问题时。

## 模块定位

`route-sentinel` 是 **Spring Cloud Gateway（SCG）server-mvc** 实现的透明网关，职责只有三件：

1. 接收 `fep-app-vr`（Nginx 虚拟路由）转发的设备域 / APP 域订单支付请求；
2. 按 `bizData.thirdUserId` 查 `USER_ITP_REG_INFO` 判定用户属性；
3. 命中分流路径时，把**旧用户**继续路由到 `face-pay-server`（`legacy-route`），把**新用户**路由到新系统（`face-pay-split-route`，目标地址 D11 占位未填）。

**NEVER** 在本模块写业务规则、签名、事务或状态机；本模块无业务 Service，只有路由策略。

> 项目位置：`../route-sentinel/`（与 `qditp` 同级，独立 git 仓库）。
> qditp 仓只负责：① `deploy/k8s/10-deployments/route-sentinel.yaml`；② `deploy/k8s/20-services.yaml` 中 `route-sentinel-svc` 定义；③ `fep-app-vr` 路由指向 `route-sentinel-svc`。

## 关键事实

| 项 | 现状 |
|---|---|
| artifactId | `org.qd:route-sentinel:1.0.1`（`route-sentinel/pom.xml`）；测试环境跑 `1.0.1`、红岛首发同为 `1.0.1`（**红岛首发不是升级，是首次部署**） |
| Spring Cloud 版本 | `2025.1.2`，网关实现用 `spring-cloud-starter-gateway-server-mvc`（同步 Servlet 栈） |
| 主类 | `org.qd.scg.UserRouteGatewayApplication` |
| JDK 版本 | **21**（2026-10-26 由 Java 25 降级，与 qditp 主体基线一致）。降级已实测：字节码 major 65、22 个测试全过；依赖侧无需改动（spring-boot 4.0.7 / spring-core 7.0.8 / gateway-webmvc 5.0.2 全为 Java 17 字节码） |
| 容器端口 | `8080`（Deployment `SERVER_PORT=8080`，Service `route-sentinel-svc:8080`） |
| 基础镜像 | 测试 `itp/jdk:21`；红岛 `os-harbor-svc.default.svc.cloudos:443/itpb/jdk21:1.0.0`（itpb 项目**唯一**公共 JDK 镜像，2026-10-26 实测 itpb ns 内拉取成功，`java version 21.0.1`） |
| 已部署镜像 | `os-harbor-svc.default.svc.cloudos:443/itp/route-sentinel:1.0.1`（`deploy/k8s/10-deployments/route-sentinel.yaml:42`）；**红岛 `itpb` 尚无 route-sentinel** |
| 上游入口 | `fep-app-vr` 把下面 **7 条精确路径**（`uri.exact`）打到 `route-sentinel-svc:8080`；**其余** `/itptvm/**`、`/itpbom/**` 端点仍由前缀路由直连 `face-pay-server-svc:30025`，不进 SCG |
| 数据库 | 测试直连 `AFCITPDB`（schema `QDITP`）；红岛按域分库后 `USER_ITP_REG_INFO` → `QDITP_USER`（`itp_ro` 只读）、`GATEWAY_ROUTE_CONFIG` → `QDITP_PLAT`（**红岛两表尚未建，见 [IT-021](../tracking/IT-021-红岛网关路由表缺失.md)，是部署前置阻塞**） |

## 数据表

| 表名 | 用途 | 说明 |
|---|---|---|
| `QDITP.USER_ITP_REG_INFO` | 用户注册判定名单 | 有记录 = 新 ITP 用户；无记录 = 旧用户。`UserRouteService` 启动预热 + Caffeine 缓存 |
| `QDITP.GATEWAY_ROUTE_CONFIG` | 路由配置主表 | 路由从 DB 加载；Actuator 变更后 `syncRoute` 落库并触发内存热重载 |
| `QDITP.GATEWAY_ROUTE_CONFIG_AUDIT` | 路由变更审计 | 仅 INSERT，按 `OPERATE_TMS` 月分区 |

建表语句：`route-sentinel/src/main/resources/sql/gateway-route-config-schema.sql`。

## 路由配置契约

### 表结构

```text
ROUTE_ID    VARCHAR2(64)  PK
URI         VARCHAR2(512) 下游 base URL
PREDICATES  CLOB          JSON 数组，如 ["Path=/itptvm/ci/tvm/**"]
FILTERS     CLOB          默认 '[]'，当前未启用
ORDER_NO    NUMBER        路由优先级
```

### 当前两条核心路由（schema-local-h2.sql / 已同步到 DB）

> ⚠️ 下表是**种子基线**（来自 `schema-local-h2.sql` 初始入库值）。运行期路由 URI 以 `GATEWAY_ROUTE_CONFIG` 库表 + Actuator `gatewayroutes` 的**实时值**为准，**NEVER 把这里的 `face-pay-server-svc:30025` 当永久事实**；改路由或排障前 MUST 现查运行态（`curl -X GET <route-sentinel>/actuator/gatewayroutes`）。

| route_id | 说明 | uri | predicates |
|---|---|---|---|
| `legacy-route` | 旧用户全部走 face-pay | `http://face-pay-server-svc:30025` | 7 条设备域 + APP 域订单 URL |
| `face-pay-split-route` | 新用户走新系统（D11 占位） | `http://UNKNOWN-NEWSYSTEM:8080` | 同上的 7 条 URL |

7 条 URL 覆盖（BOM 7 处 / TVM 5 处）：

```text
/itpbom/ci/bom/notiTakeTicketResult
/itpbom/ci/bom/notiTakeTicketFailResult
/itpbom/ci/bom/requestTakeTicketAuth
/itpbom/ci/bom/requestCardDataAnalyse
/itpbom/ci/bom/requestUpdateCardData
/itptvm/ci/tvm/requestTakeTicketAuth
/itptvm/ci/tvm/requestCardDataAnalyse
```

> 旧稿里「5 端点 vs 7 URL」的争议已拍板：**7 条 URL**（D14 已定）。

## 分流判定逻辑

判定入口：`RoutePredicateStrategy.legacyPredicate()`（`route-sentinel/src/main/java/org/qd/scg/predicate/RoutePredicateStrategy.java`）。

```text
1. 取请求 path；
2. 若 path 不在 `UserRouteService.splitPaths`（从 GATEWAY_ROUTE_CONFIG 的 Path= 谓语提取）→ 返回 false（不走 legacy）；
3. 从 query param `bizData` 中 fast-extract `thirdUserId`；
4. thirdUserId 为空 → 默认旧用户（返回 true）；
5. 调 `UserRouteService.isLegacy(thirdUserId)`：
   - 缓存命中 → 直接返回；
   - 缓存未命中 → `SELECT 1 FROM user_itp_reg_info WHERE third_user_id = ? AND ROWNUM = 1`；
   - 查到有记录 → 新用户（返回 false）；
   - 无记录 / 查询失败 → 旧用户（返回 true，fail-safe）。
```

关键实现类：

| 类 | 文件 | 职责 |
|---|---|---|
| `UserRouteService` | `predicate/UserRouteService.java` | Caffeine 缓存（max 10k，TTL 5min）、DB 加载、splitPaths 维护 |
| `BizDataExtractor` | `predicate/BizDataExtractor.java` | 从 `bizData` query param 里字符串扫描 `thirdUserId`，**零 JSON 反序列化** |
| `RoutePredicateStrategy` | `predicate/RoutePredicateStrategy.java` | 组装 legacy predicate |
| `RoutePredicateFactory` | `predicate/RoutePredicateFactory.java` | 把 `GATEWAY_ROUTE_CONFIG.PREDICATES` JSON 解析成 `RequestPredicate` |
| `UserRouteConfig` | `route/UserRouteConfig.java` | 启动从 DB 加载路由；监听 `RouteChangedEvent` 热重载 |

### 缓存与预热

- `@PostConstruct preload()`：启动时全量 `SELECT DISTINCT third_user_id FROM user_itp_reg_info`，把所有新用户写入 Caffeine 作为**否定缓存**（值 = false）。
- 这意味着：旧用户首次请求会触发 `loadLegacy`（DB 查询），后续命中缓存；新用户始终在缓存里命中 false。
- `@Scheduled(fixedRate = 30_000)` 打印 cache stats；Actuator `routecache` 端点可查看命中率。

## 路由标识策略（哪条路由算 legacy）

`LegacyRouteStrategy` 接口两个实现，由 `CompositeLegacyRouteStrategy` 组合（OR 关系）：

| 实现 | 规则 |
|---|---|
| `LegacyRouteIdStrategy` | `routeId == "legacy-route"` |
| `LegacyPredicateStrategy` | `predicates` JSON（转小写后）包含 `"legacy"` 字符串 |

因此新增 legacy 路由时，要么 routeId 叫 `legacy-*`，要么在 predicates 里显式塞 `"legacy"` 字样。

## Actuator 管理端点

| 端点 | 方法 | 用途 | 代码位置 |
|---|---|---|---|
| `/actuator/gatewayroutes` | GET | 列出当前生效路由（routeId / uri / legacy） | `sync/GatewayRoutesAdminEndpoint.java` |
| `/actuator/gatewayroutes/{id}` | POST | 新增/更新路由并落库 + 触发内存热重载 | `sync/GatewayRouteAdminController.java` |
| `/actuator/gatewayroutes/{id}` | DELETE | 标记删除（`uri=''`，`predicates='[]'`） | `sync/GatewayRouteAdminController.java` |
| `/actuator/routecache` | GET | Caffeine 缓存统计 | `sync/RouteCacheAdminEndpoint.java` |

> `management.endpoints.web.exposure.include=gatewayroutes,health,info,metrics,routecache`。

## 配置与部署

### 本地开发配置

`route-sentinel/src/main/resources/application.yml`：

```yaml
server.port: 9999
spring.datasource.url: jdbc:oracle:thin:@//172.20.222.3:1521/AFCITPDB
spring.datasource.username: qditp
spring.datasource.password: ${DB_PASSWORD:qditp}
```

### K8s 部署关键 env

来自 `deploy/k8s/10-deployments/route-sentinel.yaml`：

```yaml
SERVER_PORT: '8080'
spring.datasource.url: jdbc:oracle:thin:@//172.20.222.3:1521/AFCITPDB
spring.datasource.username: qditp
DB_PASSWORD: <secret:itp-secrets/DB_PASSWORD>
```

> 注意：本地配置写 `server.port: 9999`，但 K8s 通过 `SERVER_PORT=8080` 覆盖；Service `targetPort=8080` 必须与之一致。

## 回滚与降级

| 场景 | 操作 |
|---|---|
| route-sentinel 自身故障 | 把 `fep-app-vr` 那 7 条 `uri.exact` 的 destination 改回 `face-pay-server-svc:30025`（或直接删掉这 7 条，让 `/itptvm/`、`/itpbom/` 前缀兜底接住），7 个接口回旧 ITP；**其余端点本来就没走 SCG，不受影响** |
| 名单查询失败 | `UserRouteService.loadLegacy` catch 所有异常，返回 `true`（fail-safe 走旧 ITP） |
| 路由表为空 | `GatewayRouteQueryService.loadRoutes` 返回空列表，`UserRouteConfig` 降级为空路由，请求 404 |
| 需要全量切旧 / 切新 | 通过 Actuator 改 `legacy-route` 或 `face-pay-split-route` 的 URI，同步落库并热重载 |

## 改动红线

1. **不要动 `BizDataExtractor.fastExtract` 的字符串扫描逻辑**，除非 `bizData` 里 `thirdUserId` 的 JSON 形态变了。当前它特意避开 JSON 解析以保性能。
2. **新增分流 URL 必须同时满足**：
   - 在 `GATEWAY_ROUTE_CONFIG` 的 `legacy-route` 与 `face-pay-split-route` 两条记录的 predicates 里补齐；
   - 若 face-pay 端也新增了对应 Controller，需同步核对接口契约。
3. **改 `UserRouteService` 缓存 TTL / 大小时 MUST 评估**：旧用户首次请求会穿透 DB，QPS 高时可能打挂连接池（Hikari max=30）。
4. **D11 新系统地址未填前，face-pay-split-route 的 URI 是占位符**——目前所有命中 split 的流量会打到无效地址，D11 拍板前不能让 `face-pay-split-route` 生效。
5. **route-sentinel 不验签、不改报文**；`sign` 原样透传（见 `docs/business/app-gateway.md` §报文契约）。
6. **TraceId**：`TraceIdFilter` 给无 `X-Request-Id` 头的请求生成 UUID 并放入 MDC，向下游传递。排查链路时以该 header 为准。

## 参考

- 设计决策与选型翻转过程：`docs/design/用户分流渐进迁移设计-定稿.md`
- APP 接入层报文与透传约定：`docs/business/app-gateway.md`
- 设备域 / 订单域流量现状：`docs/business/tvm-bom-pay.md`
- 建表与路由审计 SQL：`route-sentinel/src/main/resources/sql/gateway-route-config-schema.sql`
