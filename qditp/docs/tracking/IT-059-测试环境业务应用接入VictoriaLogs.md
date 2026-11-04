# IT-059 测试环境 25 个业务应用接入 VictoriaLogs

## 1. 背景 / 现象

- 时间：2026-11-04（延续 IT-027 之后的 VL 收口工作）。
- 目标：测试环境 `itp` 命名空间的全部业务 Java 应用都接入 VictoriaLogs，与红岛 / 生产对齐。
- 范围口径：`itp` 共 **33 个 Deployment**，按约定排除 `web`（前端）、`details-v1`/`productpage-v1`/`ratings-v1`/`reviews-v1`/`reviews-v2`/`reviews-v3`（6 个 bookinfo 示例）、`kennethreitz-httpbin`（1 个 httpbin）后，剩 **25 个业务应用**。
- 现状结论：
  - **24 个已接入并验证**（VL 近 7 天均出现对应 `app` 流；route-sentinel 于 2026-11-04 修复 logback 缺陷后接入，见 2.3）；
  - **0 个缺陷**；
  - **1 个（database）经核实为 MCP 数据库服务，非 qditp Java 业务框架，不纳入标准接入范围** → 实际需接入的业务 Java 应用为 24 个，**全部接入完成**。

## 2. 根因分析

### 2.1 23 个已接入（统一框架路径）

这些应用都依赖 `micro/web` 的统一 log4j2（`CustomLoggingConfiguration`）：在 `ApplicationReadyEvent` 后重载 `log4j2-linux.xml`，内含 VictoriaLogs HTTP appender；只要容器内 `VLOGS_URL` 指向 `victoria-logs-pbi6a-svc.itp.svc:30032`（→ 容器 9428）且 `appName` 由 `spring.application.name` 派生，即可上报。

本次需补的 env 缺口（**逐个走 CloudOS advanced-config 改 env，绝不用 kubectl patch/set env**，见 IT-020 平台回滚教训；且 env 是整份覆盖，MUST 保留容器实际 env 的 42 项含 8 个平台注入变量）：

- **card-pool-server**：`VLOGS_URL` 缺 `http://` 前缀 → 补全后 VL 流恢复（`app=card-pool`）。
- **collect-pay**：此前与 `face-pay-server` 共用 `itpagm` 流；注入 `OTHER_LOGGING_APPNAME=collect-pay` 后独立成 `collect-pay` 流（日志文件由 `itpagm.log` 变 `collect-pay.log`）。
- **es-server**：见 2.2。

### 2.2 es-server 未接入根因（代码缺陷，已修）

- `NettyServer` 由 Spring Boot `CommandLineRunner` 调用，原 `start()` 末尾执行
  `channelFuture.channel().closeFuture().sync();` —— 该调用**永久阻塞** `SpringApplication.run()` 的调用线程。
- 后果：`ApplicationReadyEvent` 永不发布 → `CustomLoggingConfiguration` 不加载 → 统一 log4j2（含 VL appender）不生效 → es-server 不进 VL（并放大 IT-027 盲区：启动失败也不落盘）。
- 修复：删除该阻塞行（`NioEventLoopGroup` 为非守护线程，JVM 自然保活；关停由 `@PreDestroy close()` 负责）。`acc-es-server` 版本 `2.0.29 → 2.0.30`，重新 `buildx` 出镜像并切测试环境。commit `0112dee6`。

### 2.3 route-sentinel 未接入根因与修复（自身 logback 缺陷，已闭环）

- route-sentinel 自带 `logback-spring.xml` 的 VL appender 使用 `<url>${env:VLOGS_URL:-}</url>`。经反编译确认：本部署中该 `env:` 查找**不解析**出容器内已注入的 `VLOGS_URL` 值（容器内 `env`、`PID1 environ` 均有值），导致 appender 报 `url is empty`、不进 VL。
- 已验证无效的尝试：注入 `VLOGS_URL`、`JAVA_TOOL_OPTIONS=-DVLOGS_URL`、`-Dvlogs.url` —— 均不能让该 appender 取到 URL（它在 logback 配置加载期从 `env:` 取，而非系统属性）。
- **修复（方案 A 落地，本仓直接改 route-sentinel 源码并重新出镜像）**：
  1. `logback-spring.xml`：`<url>${env:VLOGS_URL:-}</url>` → `<url>${VLOGS_URL:-}</url>`（原生 env 引用可读到注入值）；
  2. `VictoriaLogsAppender.java`：新增 `resolveUrl()` 兜底 —— 当 logback 未解析出值（仍含 `${`）时直接读系统环境变量 `VLOGS_URL`，与 `HOSTNAME` 取法一致；
  3. 版本 `1.0.5 → 1.0.6`，`buildx` 出镜像 + 切测试环境。commit `4ab99051`。
- **附带增强（用户明确要求）**：原 access log 不打印 `bizData`，无法从日志直接看到 `thirdUserId` / 手机号 / 卡号。在分发决策处（`RoutePredicateStrategy.legacyPredicate()`）新增 INFO 日志，对分流路径打印 `分发决策 path={} thirdUserId={} bizData={} -> LEGACY/NEW`，便于核查旧/新 ITP 分发是否正确；非分流路径仅 debug 级（避免淹没）。见 §5 验证 3。

### 2.4 database 不纳入接入范围

- `database` Deployment 实为 **MCP 数据库服务**（镜像 `database-server:0.6.7`；env 全为 `ENTROPY_MCP_DATABASE_*` / `MCP_SECURITY_*`；无 `VLOGS_URL` / `OTHER_LOGGING_APPNAME` / `JAVA_TOOL_OPTIONS`）。
- 非 qditp 业务 Java 框架应用，不在统一 log4j2 体系内，标准接入方式不适用。从 25 个业务应用口径中**排除**。若其团队需日志上 VL，按 MCP 服务日志方案另行处理。

## 3. 方案对比（route-sentinel 修复）

| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| A | 改 route-sentinel 自身 logback：`${env:VLOGS_URL:-}` → 原生 `${VLOGS_URL:-}` | 最小改动、复用已注入 env | 需动 route-sentinel 源码 / 重新出镜像 | 低 |
| B | 在 logback 里写死 VL URL | 不依赖 env | URL 与部署环境耦合，红岛/生产各异 | 低 |
| C | 外部挂载修正版 logback + `logging.config` 覆盖 | 不改源码 | 需挂 configmap + 高级配置改挂载，链路复杂 | 中 |

- 方案 A 已落地（本仓直接改 route-sentinel 源码并重新出镜像，commit `4ab99051`）。

## 4. 决策与结论

- 测试环境 **24/24** 业务 Java 应用已接入 VL（`database` 排除后 24 个全部完成，含 route-sentinel 修复后接入）。
- es-server 修复已 `commit 0112dee6` + 部署 `2.0.30` + 验证 `app=acc-es` 出现于 VL。
- route-sentinel 已本仓直接修复（方案 A）：`commit 4ab99051` + 部署 `1.0.6` + 验证 `app=route-sentinel` 出现于 VL；并新增分发处 bizData 日志（见 §5 验证 3）。
- database 排除，不计入 VL 接入范围。

**Deployment → VL `app` 名映射（23 个已接入）：**

| Deployment | VL app | Deployment | VL app |
|------------|--------|------------|--------|
| account | account | industry-data-server | industry-data-server |
| alipay-account-server | alipay-account | key-server | key |
| alipay-pay-sign-server | alipay-pay-sign | para-server | para-server |
| blacklist-server | blacklist | pay-sign-server | pay-sign |
| card-pool-server | card-pool | recon-server | recon-server |
| collect-pay | collect-pay | security-server | acc-security |
| daily-ticket-server | daily-ticket-server | ticket-server | ticket-server |
| es-server | acc-es | trans-query | trans-query-server |
| face-pay-server | itpagm | web-admin | web-server |
| fep-acc | fep-acc-server | route-sentinel | route-sentinel |
| fep-alipay | fep-alipay | database | （排除，见 2.4） |
| fep-app | fep-app | | |
| fep-dev-server | fep-dev | | |
| gate-txn-pay-server | gate-txn-pay | | |

## 5. 验证与回滚

- **验证 1（es-server 修复）**：VL 查 `app:"acc-es"` 近 1h 出现 2 条，含
  `Log4j2 configuration has been reloaded from classpath:log4j2-linux.xml ... filename=acc-es.log`
  —— 证明 log4j2 已加载、VL appender 生效。
- **验证 2（整体覆盖）**：VL 近 7 天 `stats by (app) count()` 确认上表 24 个 `app` 流均存在（含 `route-sentinel`）。
- **验证命令（跳板机经节点 IP 走 istio 转发）**：
  `curl 'http://10.100.183.227:30032/select/logsql/query' --data-urlencode 'query=app:"acc-es"'`
  （`30032` 为 nodePort → 容器 `9428` 即 VL ingest 端口；查询请求经 envoy 转发到 `victoria-logs-0`，不可直连 Pod IP `:8420`）。
- **回滚 es-server**：切回 `acc-es-server:2.0.29` + 还原 `NettyServer.java`（commit `0112dee6` 的父提交），重出镜像；route-sentinel / database 本次未改动，无需回滚。
- **验证 3（route-sentinel 修复 + 分发日志）**：VL 查 `app:"route-sentinel"` 近 10m 出现 19 条（启动类日志），近 7 天 `app:"route-sentinel" AND _msg:"分发决策"` 计数 2 条，样例：
  `分发决策 path=/itpbom/ci/bom/requestCardDataAnalyse bizData=null thirdUserId=缺失 -> LEGACY(兜底)`
  字段 `logger=org.qd.scg.predicate.RoutePredicateStrategy`、`level=INFO` —— 证明 VL 接入生效、**且分发决策日志已按用户要求在分发处打印 bizData/thirdUserId**（该样例请求未携带 bizData，故 `thirdUserId=缺失`、fail-safe 到 LEGACY；真实携带 bizData 的请求将打印 `thirdUserId=<值> bizData=<json> -> LEGACY/NEW`）。

## 6. 流程符合度自评

- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-059`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 关联文档」
- [x] 索引行未复制本文档叙述
- [x] 关键数字（端口 30032/9428、镜像 tag、commit 号）已标注来源
- 偏差说明：route-sentinel 原"待其团队"已闭环（本仓直接改源码 + 部署 1.0.6，commit `4ab99051`）；database 的重新归类（MCP 服务而非 Java 业务应用）修正了原 25 个业务应用口径的计数，最终 24 个业务 Java 应用全部接入完成。
