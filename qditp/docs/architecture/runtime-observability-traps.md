# 观测切面 / tracing / 虚拟线程（运行时静默失效族）

> **何时读**：改 / 排查「同一份代码在 A 模块好、在 B 模块坏」、tracing 链路断、虚拟线程 pin、`%X{traceId}` 为空时 **MUST 先读**。
> 本文件承载「观测切面换异常类型 / tracing 覆盖面 / 虚拟线程 pin」三类**只在运行时 / 特定模块才暴露**的陷阱，**不再固化进 `AGENTS.md`**（2026-10-15 抽离，遵循「常驻上下文只放高海拔规则、细节按需 JIT 加载」）。
> **本文档「现状」段落均为静态核实时点快照（§1 / §3 为 2026-10-15，§2 已于 2026-10-28 复核）；MUST 现查复核，尤其是「已修 / 已无缺口 / 已清零」这类完成态断言——它们最容易过期。**

## 1. 切面 / 包装层把异常换类型（`throw new RuntimeException(e)`）

本项目幂等主写法是「唯一索引 + 按类型 catch 兜底」。若切面把真实异常包一层（`throw new RuntimeException(e)`），调用方按类型 catch **一律失效**。

**现状（2026-10-15 核实，ADR-D53，2026-09-14 已修）**：`resource/micro/web` 的 `MapperAspectToTrace.around` / `ServiceAspectToTrace.around` 现为手工 `observation.start/openScope/error/stop` + `catch (Throwable e) { …; throw e; }`（**原样抛出不换类型**），两处源码各留一行「`NEVER 回退成 observation.observe(Supplier)`」护栏注释。

**全仓 20 个切面/Filter/Interceptor 已无换类型写法**；残留的 `throw new RuntimeException(e)` 都在 RPC 出向客户端 / 启动期 Bean 注册 / 工具类（`ProxyWebClient.handleResponse`、`DynamicMybatisConfiguration.registerSqlSessionFactoryBean`、`AppOrderServiceImpl` 等），**不包裹 DB 调用**。幂等兜底另有 cause 链判定双保险：`face-pay-server` 的 `F2fDuplicateKey.isConflict(Throwable)`、`pay-sign-server` 的 `PaySignDuplicateKey`（沿 `getCause()` 链认 `DuplicateKeyException` 等）。

## 2. tracing 覆盖面（2026-10-28 起已无缺口）

`SimpleObservationMonitor.shouldSkipAopTraceLogic()` 是**两级短路**：① tracing 未开（`management.tracing.enabled` 默认 `false`）直接 `pjp.proceed()`；② 即使开了，Observation 实例数超上限（`management.tracing.otlp.tracing.batch.max-queue-size` / 2）也短路。因此**同一份代码在 A 模块好、在 B 模块坏**，单测里没有切面、更看不到。

**名单与「MUST 同时查 K8s Deployment env」判据见 [`common-components.md`](common-components.md) §3.1.2**（当前数量 MUST 现查，NEVER 引用文档里的数字）。**2026-10-28 静态核实**：全部 24 个「部署态且依赖 `resource/micro/web`」的服务三行齐全，`industry-data-server` / `key-server` / `collect-ticket-server` / `acc-es-server` / `acc-secure-server` / `acc-security-server` 六个原缺口已补齐（六模块只改了 properties，MUST 重新构建镜像并滚更才生效）。未开 tracing 的只剩不装切面的 `es-server` / `route-sentinel` / `web-server`（后者的 Quartz 链路靠 `QuartzTraceUtils` 显式写 MDC）。线上是否真的开着 MUST 以 Deployment env 为准（`kubectl -n itp get deploy <d> -o yaml`）。

连带后果：**「本模块没开 tracing，所以裸 catch 不会失效」这类论证全仓作废**——切面现在在每个部署态服务里都跑（`throw e` 原样抛出，但仍会补一条 `log.error`），幂等兜底 MUST 一律沿 `getCause()` 链判定。

## 3. 虚拟线程 pin（JDK 21 基线下的现实风险）

**2026-10-20 起全服务已默认禁用虚拟线程**（`spring.threads.virtual.enabled=false`，`resource/micro/web/.../web.properties`；各模块 `application.properties` 亦显式 `false`，Tomcat 请求跑回平台线程，日志线程名为 `http-nio-<port>-exec-N`）。历史背景：本项目曾以 `true` 为全服务默认，JDK 21 未落地 JEP 491，虚拟线程在 `synchronized` 内阻塞会 **pin 住载体线程**；而 ojdbc8 **19.18.0.0** 的 `PhysicalConnection` 有 117 个 `synchronized` 方法（`createStatement` / `prepareStatement` / `commit` / `rollback` / `close` 全覆盖），执行类（`OraclePreparedStatement` 等）字节码里有大量 `monitorenter`（`synchronized` 块）——**一条 60s 慢 SQL 就是 60s 的 pin**；载体线程池（`parallelism` 默认等于容器可见 CPU 数）打满则**全 JVM 虚拟线程停止调度**，连 WebClient 响应续体都唤不醒。禁用后该 pin 机制已消除，但「NEVER 在请求线程上做长时间阻塞的 DB / IO」这条约束仍保留——慢 SQL 会拖垮平台线程池的并发处理。下方取证 / 缓解手段在虚拟线程启用期间仍有效，作为历史机制留存。

**取证（按 JDK 版本分情况）**：
- 一律可用：`jcmd <pid> Thread.dump_to_file -format=json`（`jstack` 看不到虚拟线程）。
- **JDK 21 上**可加 `-Djdk.tracePinnedThreads=full`；**该开关已被 JEP 491 在 JDK 24 删除**（JDK 24+ 只剩 JFR 事件 `jdk.VirtualThreadPinned`，且语义改为「native/VM frame 导致的 pin」）。
- 缓解见 `docker/Dockerfile` 的 `JAVA_TOOL_OPTIONS`（`-Djdk.virtualThreadScheduler.parallelism=8 -Djdk.virtualThreadScheduler.maxPoolSize=256`，与容器 CPU 数解耦）。

**JDK 版本事实**：本项目编译/运行基线是 **JDK 21**（`maven.compiler.source/target=21`、基础镜像 `itp/jdk:21`、各模块 `<java.version>21</java.version>`）；ojdbc 是 `com.oracle.database.jdbc:ojdbc8` **19.18.0.0**（全仓 18 个模块一致，无漂移）。