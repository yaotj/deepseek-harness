# micro 基础组件库对比报告

- **对比日期**：2026-10-27
- **对象 A（外部“最佳实践”）**：`/Users/tuanjie/workspace/company/chinasofti/qd/micro`（与 `qditp` 同级目录，另一项目的最新 micro）
- **对象 B（本项目）**：`/Users/tuanjie/workspace/company/chinasofti/qd/qditp/resource/micro`（青岛地铁 ITP 平台自带的 micro 基础库）
- **方法**：逐文件树比对（排除 `target/` 编译产物）+ 源码内容 diff（含空白无关 diff 二次过滤）。
- **结论先行**：两者是**同一条微内核组件线的两个分支**，并非简单的“新旧版本”。外部是 `all:1.0` / `web:2.0` 的通用基线；本项目是 `all:2.0` / `web:2.1` 的**前向分支**，在通用基线上叠加了 ITP 专属加固（VictoriaLogs、虚拟线程 pinning 防护、心跳降噪、MDC 鉴权透传、链路追踪异常保真、retCode/retMsg 响应体）。外部相对“新”的地方主要是**通用韧性能力**（WebClient 网络重试、JVM 对象直方图诊断端点），但**不能用外部整体替换本项目**——那样会丢失 ITP 加固并破坏 retCode API。建议**有选择地 cherry-pick**。

---

## 1. 版本与模块基线

| 维度 | 外部 `qd/micro` | 本项目 `resource/micro` | 说明 |
|---|---|---|---|
| 父工程 `all` | `1.0` | `2.0` | 本项目父工程已升版 |
| `mybatis-adaptor` | `2.0` | `2.0` | 同版本 |
| `rabbitmq-adaptor` | `2.0` | `2.0` | 同版本（且 ITP 已声明 NEVER 在业务模块引用） |
| `sql-datasource` | `2.0` | `2.0` | 同版本，配置与 SqlAudit 实质不同 |
| `web` | `2.0` | **`2.1`** | 本项目已前向升版 |
| 模块数 | 4（mybatis / rabbitmq / sql-datasource / web） | 4（一致） | 结构完全对齐 |

> 关键判断：版本号上本项目 `web` 已经是 2.1（高于外部 2.0），说明本项目是**在 micro 线基础上已演进过**的分支，而不是落后版本。外部所谓“最新最佳实践”更多是**通用能力补充**，不是对本项目的替代。

---

## 2. 文件级差异（新增 / 缺失）

### 2.1 仅外部 `qd/micro` 拥有（本项目缺，共 7 个源/资源文件）

| 文件 | 作用 | 是否建议采纳 |
|---|---|---|
| `web/.../controller/MonitorController.java` | `/monitor/health` + `/monitor/getObjectCountTop`（经 JMX `DiagnosticCommand` 取 JVM 类实例直方图） | 可选（通用运维诊断） |
| `web/.../monitor/jvm/JvmObjectTop.java` | 上面端点的实现，封装 `gcClassHistogram` | 可选（同上） |
| `web/.../web/client/WebClientRetryHelper.java` | **响应式 WebClient 网络重试策略**（退避、按幂等性/请求是否已发出决定可重试） | **建议采纳（高价值，见 §6）** |
| `web/.../META-INF/md/webmvc.md` | SpringMVC / HttpClient / 定时任务用法文档 | 可选 |
| `mybatis-adaptor/.../readme.md`、`rabbitmq-adaptor/.../readme.md`、`sql-datasource/.../readme.md` | 各模块说明文档 | 文档，无代码影响 |

### 2.2 仅本项目 `resource/micro` 拥有（外部缺，共 3 个源/资源文件）

| 文件 | 作用 | 性质 |
|---|---|---|
| `web/.../log4j2/VictoriaLogsAppender.java` | 自定义 log4j2 Appender，把日志以 JSON Lines 推送到 **VictoriaLogs**（ITP 自建日志中心，MDC 仅透传 traceId/spanId） | **ITP 专属，必须保留** |
| `web/.../web/utils/HeartbeatUrlUtil.java` | 心跳 URL 判据（含 `deviceheartbeat`/`deviceheard`），用于抑制设备心跳刷屏日志 | **ITP 专属，必须保留** |
| `web/.../victorialogs-event-template.json` | VictoriaLogs 事件模板 | 配套上面的 Appender |

---

## 3. 关键功能差异（按关注点）

### 3.1 服务间 HTTP 客户端（`ProxyWebClient` / `ProxyOkHttp` / `InternalMicroHttp`）
- **外部**：`ProxyWebClient` 集成了 `WebClientRetryHelper` 做**网络层重试**（`RETRY_ENABLED=true, MAX_RETRIES=1`），默认走 **H2C（HTTP/2 明文）**，并引入 `cn.hutool.core.util.StrUtil`。失败返回 `null`。
- **本项目**：保留 **HTTP/1.1**；**没有重试**；但额外做了两件外部没有的、对 ITP 至关重要的事：
  1. **MDC `authorization` 透传**（`addAuthorizationToHeader`）——服务间调用携带鉴权头，是 ITP 内部 RPC 鉴权的关键链路；
  2. **可配置响应超时**（`DEFAULT_RESPONSE_TIMEOUT=10s`，构造器可覆盖）+ 新增 `postJsonAndGetResponseEntity` 返回 `ResponseEntity` + 失败**抛异常**（而非返回 null）。
- **`ProxyOkHttp`/`InternalMicroHttp`**：差异基本是注释与中文标点（空白级），逻辑一致。
- **判断**：外部补的是“通用韧性（重试 + HTTP/2）”，本项目保的是“ITP 鉴权透传 + 超时控制”。两者应取长补短，而非二选一。

### 3.2 链路追踪切面（`ServiceAspectToTrace` / `MapperAspectToTrace`）
- **外部**：用 `observation.observe(() -> { pjp.proceed(); })` 包裹，异常被再包一层。
- **本项目**：改为显式 `observation.start()` + `try(Observation.Scope)` + `observation.error(e)` + `observation.stop()`，并在注释中明确标注 **“NEVER 回退成 observation.observe(Supplier)：异常会被包一层，幂等兜底 catch 全部落空（ADR-D53）”**。
- **判断**：这是本项目一次**正确性修复**——`observe()` 会吞掉原始异常类型，导致支付/订单等幂等兜底的 `catch` 失效。**绝不能回退到外部写法。**

### 3.3 数据源与 SQL 审计（`sql-datasource`）
- **`SqlAudit.java`（本项目）**：新增 `skipHeartbeat` 开关 + `F2F_DEVICE_HEARTBEAT|F2F_DEVICE_STATUS` 心跳 SQL 抑制（命中后跳过 INFO 审计，慢 SQL/大结果集仍走 error）；并**移除 2048 字符截断**（完整记录非心跳 SQL）。外部则对长 SQL 截断到 2000 字符。
- **`oracle.properties`（本项目）**：新增 `spring.datasource.druid.connection-properties=oracle.jdbc.ReadTimeout=10000`，注释说明是 **虚拟线程 pinning 兜底**（JDK 21 未落地 JEP 491，ojdbc 的 `synchronized` 方法会 pin 载体线程；2026-08-26 pay-sign-server 生产事故根因）。外部无此项。
- **`sql.properties`（本项目）**：移除 `other.sql.trace=false`；新增 `query-timeout=30`（池级查询超时兜底）+ `remove-abandoned=true/timeout=300/log-abandoned=true`，并保留 WallFilter 注释。外部这些项多为注释状态。
- **判断**：本项目针对“虚拟线程 + 长耗时批处理”场景做了**有文档支撑的加固**，外部偏保守（超时/回收多注释掉）。**保留本项目配置。**

### 3.4 日志体系（log4j2 + VictoriaLogs）
- **`log4j2-linux.xml`（本项目）**：
  - 根 `<Configuration>` 增加 `packages="com.chinasofti.huateng.log4j2"`，使嵌套 jar 里的 `VictoriaLogs` `@Plugin` 能被加载；
  - `RollingRandomAccessFile` → `RollingFile`；新增 `Console` Appender；
  - 新增 `VictoriaLogs` Appender（url 为空自动禁用，端口 MUST 30032，app 取 `${appName}`，MDC 白名单仅 traceId/spanId）+ `VLOGS` 专用 Logger（`additivity=false`，DEBUG 全量进 VictoriaLogs）；
  - Pattern 去掉 `%X{traceparent}`。
- **`CustomLoggingConfiguration.java`（本项目）**：`Configurator.initialize` 显式传入 `CustomLoggingConfiguration.class.getClassLoader()`，解决 fat jar 下自定义 Appender “Unable to locate plugin” 被静默吞掉的问题。
- **判断**：这是 ITP 日志上云（VictoriaLogs）的**完整集成**，外部完全不具备。**必须保留。**

### 3.5 统一响应体（`ASimpleResultVo` / `ApiErrorResponse`）
- **外部**：字段/方法为 `code` / `msg`（`getCode()`/`setCode()`/`getMsg()`…）。
- **本项目**：**重命名为 `retCode` / `retMsg`**（`getRetCode()`/`setRetMsg()`），`ApiErrorResponse`、`GlobalControllerExceptionHandler` 同步改为 `setRetCode/setRetMsg`。
- **判断**：这是一次**有意的 API 重构**，贴合 ITP 响应信封（retCode/retMsg 是国内支付系统惯例）。若整体并入外部 `web` 模块会**产生破坏性冲突**，需显式决策。

### 3.6 监控指标（prometheus `BaseExport`/`WebExport`/`LoggerExport`/`ResetMetersJob`）
- 本项目相对外部**精简了部分 gauge**（超时计数、运行任务计数、运行任务峰值、日志耗时等被去掉或简化），`ResetMetersJob` 反而**启用了** `customMeterRegistry.close()/collectorRegistry.clear()`（外部是注释掉的）。
- **判断**：指标取舍差异，影响面小；本项目更克制（减少 Meter 内存膨胀），外部更全。可按实际可观测性需求决定。

### 3.7 MyBatis 事务代理
- 本项目 `DefaultMybatisConfiguration`：`@EnableTransactionManagement(proxyTargetClass = true)`（强制 CGLIB）；外部为默认（JDK 接口代理）。
- **判断**：强制 CGLIB 可避免某些类代理失败，**保留**。

---

## 4. 配置差异（`web.properties` 重点）
| 配置项 | 外部 | 本项目 | 解读 |
|---|---|---|---|
| `spring.threads.virtual.enabled` | `true` | **`false`** | 本项目**刻意关闭虚拟线程**（pinning 风险，见 §3.3） |
| `server.tomcat.max-connections` | `20000` | `8192` | 本项目收紧 |
| `server.http2.enabled` / `multipart` 1GB | 有 | 无 | 外部更激进 |
| `management.tracing.sampling.probability` | `0.0` | **`1.0`** | 本项目全采样，喂给 VictoriaLogs |
| OTLP tracing 批量配置 | 存在（但 SB3.2.6 下键名不生效） | **全部注释 + 详细注释** | 本项目明确不依赖 OTel collector |
| `management.endpoint.loggers.enabled` | `true` | `false` | 本项目关闭 |

> 注意：外部那 6 行 `management.tracing.otlp.tracing.batch.*` 在 Spring Boot 3.2.6 中**根本不存在对应键**（真实前缀是 `management.otlp.tracing.*`），从未生效；本项目已在注释里写明，切勿照抄。

---

## 5. 差异性质统计（重要：别被 diff 行数误导）
30 个“内容不同”的文件里，**相当一部分是纯空白 / 中文标点（句号“。”）差异，逻辑完全等价**：
- `HostManager.java`（raw 282 行 diff，**实质 0**）
- `WebApplicationContextUtils.java`（raw 179 行 diff，**实质 0**）
- `ProxyOkHttp.java`（raw 374，**实质 ~30**，多为 javadoc 标点）
- `AntiInjection.java` / `AntiInjectionValidator.java`（raw 54/173，**实质 4/7**，注释标点）
- `ServiceAspectToTrace` / `MapperAspectToTrace`（raw 126/127，**实质 ~25/26**，空白为主 + 关键 1 处逻辑）

**真正有功能含义的改动**集中在：`ProxyWebClient`、`SqlAudit`/`oracle.properties`/`sql.properties`、`log4j2-linux.xml`/`CustomLoggingConfiguration`、`ASimpleResultVo`/`ApiErrorResponse`、`Service/MapperAspectToTrace`、`DefaultMybatisConfiguration`、`web.properties`、以及心跳降噪（`FirstFilter`/`BodyCacheFilter`/`MoreInterceptor`）。

---

## 6. 评估与行动建议

### 6.1 建议从外部采纳（高价值、低风险）
1. **`WebClientRetryHelper` + 在 `ProxyWebClient` 接入网络重试**（最高优先级）
   - 填补本项目“服务间调用无网络重试”的真实韧性缺口（连接被对端提前关闭、Connection reset 等）。
   - **必须保留本项目已有的 MDC `authorization` 透传与可配置 `responseTimeout`**，并把重试严格限制在幂等/请求未发出路径（`WebClientRetryHelper.isIdempotent` + `isBeforeRequestSent` 已做）。对支付等非幂等写操作**默认不重试**，避免重复扣款。
   - 合并后 `web` 版本升到 `2.2`，重新 `mvn -o clean test-compile` 验证。
2. **`MonitorController` + `JvmObjectTop`**（可选）
   - 通用 JVM 诊断端点，零业务侵入；可纳入但不紧急。需确认 knife4j/swagger 依赖与本项目一致。

### 6.2 必须保留本项目、切勿回退（外部写法更“通用”但对我方是退步）
- 链路追踪切面的 `observation.start/openScope/error/stop` 写法（ADR-D53，保幂等兜底）。
- `retCode`/`retMsg` 响应体重命名（破坏性 API，ITP 全栈依赖）。
- 虚拟线程**关闭** + `ReadTimeout=10000` / `query-timeout=30` / `remove-abandoned` 加固。
- VictoriaLogs 全套集成 + fat-jar classloader 修复。
- `ProxyWebClient` 的 MDC 鉴权透传与响应超时控制。

### 6.3 合并风险与注意事项
- **`web` 版本基线不同**（外部 2.0 / 本项目 2.1）：直接拷贝外部文件会**覆盖本项目 2.1 的 ITP 加固**。应以本项目的 2.1 为基底，仅合并外部新增的 `WebClientRetryHelper` 并改造 `ProxyWebClient` 接入，而不是整目录替换。
- **`ASimpleResultVo` retCode/retMsg 冲突**：若误并入外部版本，全项目 `getCode()/getMsg()` 调用点（含 web-server、各业务模块）会编译失败。
- **H2C/HTTP/2 与 hutool**：外部默认 H2C、引入 hutool；本项目服务器未必支持 h2c，且引入 hutool 会增加依赖面。采纳时**只取重试逻辑，不取 H2C/hutool**。
- **父工程 `all` 版本**：外部 1.0、本项目 2.0，整库替换会拉低父版本，进而影响依赖管理。

---

## 7. 结论
外部 `qd/micro` 与本项目 `resource/micro` 是**同源分支**：结构、模块、绝大多数源码（61 个文件逐字节等价，另有约 30 个仅空白差异）一致；本项目 `web` 已是 2.1 前向分支，叠加了 VictoriaLogs、虚拟线程 pinning 防护、心跳降噪、MDC 鉴权透传、链路追踪异常保真、retCode 响应体等 **ITP 专属加固**；外部相对“新”的仅是 **WebClient 网络重试**与 **JVM 直方图诊断端点**这类通用能力。

**推荐路径**：以本项目 2.1 为基底，**只 cherry-pick `WebClientRetryHelper` 并改造 `ProxyWebClient` 接入重试**（保鉴权透传、保超时、限幂等），其余 ITP 加固一律保留，外部文件**禁止整目录覆盖**。
