# qditp 代码架构审查报告

**审查对象**：`qditp` 城市轨道交通票务系统（Java 21 + Spring Boot 微服务，21 个 Maven 模块）
**代码规模**：约 3168 个 Java 文件 / 约 32 万行（不含构建产物；`jkube` 为 eclipse-jkube 开源克隆，未纳入审查；`qdmlc` 为空目录）
**审查日期**：2026-07-21
**审查方法**：全量特征扫描（线程池/静态集合/ThreadLocal/ByteBuf/IO 流/死循环等高危模式）→ 按模块分 4 批对约 30 个高危文件逐一精读复核 → 架构分层、重复代码、配置安全、测试覆盖等工程维度检查

---

## 一、总体结论

| 维度 | 结论 |
|---|---|
| 内存/资源泄漏 | **确认 6 处高危、8 处中危**，集中在 Netty 字节缓冲、HTTP 客户端、IO 流关闭、ThreadLocal 清理四类 |
| 线程管理 | 多数线程池已注册为 Spring Bean，但存在裸线程、调度器未关闭、优雅停机配置缺失 |
| 代码坏味道 | 存在上帝类（最大 1894 行）、Controller 直连 Mapper、工具类重复实现、生产代码 System.out/printStackTrace |
| 架构一致性 | 微服务 + 若依（RuoYi）单体后台混合架构；共享 model 模块被 23 处依赖，耦合面大 |
| 工程质量 | **全项目仅 1 个测试文件**（约 3168 个源文件），几乎没有测试保护网；配置文件存在硬编码密钥 |

整体判断：项目可运行、主干流程的资源管理基本规范（多处 finally 关流、多数 ThreadLocal 有 remove），但**存在若干会在长期运行中累积泄漏的点**，且缺乏测试兜底，重构与修复需谨慎推进。

---

## 二、内存/资源泄漏问题（按严重度）

### 高危（确认存在，建议尽快修复）

**H1. Netty ByteBuf 引用计数泄漏** — `acc-security-server/.../socketServer/SimpleServerHandler.java:32-38`
`Unpooled.buffer()` 创建的响应 ByteBuf 在 `writeAndFlush` 失败路径未释放；`channelRead` 对入参 msg 也未调用 `ReferenceCountUtil.release()`。Netty 池外缓冲虽最终可被 GC，但引用计数语义被破坏，且堆外场景会直接泄漏。
修复：`try-finally` 包裹写操作，失败分支 `release`；读入 msg 统一 `ReferenceCountUtil.release(msg)`。

**H2. 每次请求新建 OkHttpClient** — `collect-pay-server/.../service/impl/TvmTopupServiceImpl.java:476`
`callPayCenter` 每次调用 `new OkHttpClient()`，每个实例自带独立连接池 + Dispatcher 线程池。充值/退款/查询高频调用下，线程与连接持续累积，是典型的慢性内存/线程泄漏。
修复：提取为类级 `static final` 单例复用（同模块 `HttpUtils.FORM_DATA_HTTP_CLIENT` 已是正确写法，可对齐）。

**H3. SSE 长连接线程阻塞 + 全局队列误清** — `resource/micro/web/.../micro/controller/LogController.java:36`
`while(true)` 推送 SSE 日志，仅靠客户端断开触发退出；客户端异常断开未传播时 Tomcat 工作线程可能永久阻塞在 `poll()`。更严重的是 `finally` 中调用全局 `LOG_QUEUE.clear()`，任一连接结束会清空其他消费者的日志数据。
修复：加超时/心跳；移除 `clear()`，改为 per-connection 缓冲或按消费位点读取。

**H4. ThreadLocal 在成功路径不清理** — `resource/micro/sql-datasource/.../datasource/SqlAudit.java:67,119`
`statementExecuteBefore` 写入 ThreadLocal，但 `statementExecuteAfter` 仅在 `result==false` 分支调用 `audit()` 清理；执行成功且无结果集（如 DML 成功）的路径永不移除。在容器线程池复用下，每次 SQL 都残留一组 `String[]`，随运行时间线性累积。
修复：所有出口统一 `sqlAndStartTimeData.remove()`。

**H5. FTP 工具流未可靠关闭（3 处）** — `acc-es-server/.../util/FTP.java:110-116 / 215-216 / 343-348`
- `downLoadFTP`：`FileOutputStream.close()` 写在 try 块内，`retrieveFile` 抛异常时流泄漏；
- `copyFile`：close 在 try 块内，`storeFile` 异常时泄漏；
- `readFileByFolder`：reader 的 close 在循环体内，`readLine` 异常时泄漏，且 `completePendingCommand` 未执行会导致 FTP 连接挂起。
修复：全部改为 try-with-resources；FTP 协议收尾调用放入 finally。

**H6. SSL 请求流仅正常路径关闭** — `web-server/web-common/.../utils/http/HttpUtils.java:234-245`
`sendSSLPost` 的 `BufferedReader.close()` 只在 try 正常路径执行，`readLine` 抛异常时底层 InputStream 泄漏，无 finally。
修复：try-with-resources 或补 finally。

### 中危（特定条件下累积，应排期修复）

**M1. LocalCache 裸线程 + while(true)** — `acc-security-server/.../config/LocalCache.java:33,96`
构造函数直接 `new Thread(new TimeoutTimer()).start()`，非守护线程且循环内捕获 `InterruptedException` 后仅 `printStackTrace` 不恢复中断标志，线程无法退出；被 Spring 多次实例化会重复起线程。（缓存本体有 3 秒 TTL 定时清理，map 本身不会无界增长，泄漏点在线程。）
修复：改 `ScheduledExecutorService`（daemon）或接入 Spring 生命周期。

**M2. ChannelCache 清理路径不完整** — `acc-security-server/.../config/ChannelCache.java:15,60`
`clear()` 仅在 `channelInactive/exceptionCaught` 触发；若 pipeline 移除 handler 而连接未断，Channel 永驻静态 `CopyOnWriteArrayList`。
修复：补 `handlerRemoved` 兜底；`get()` 时检测 inactive Channel 主动剔除。

**M3. commons-httpclient 每次新建** — `collect-pay-server/.../utils/HttpUtils.java:40`
`doPost2` 每次 `new HttpClient()`，未调用 `closeIdleConnections`，连接资源依赖 GC 延迟回收。
修复：单例化或 finally 中 `releaseConnection`。

**M4. SM2 工具文件流未可靠关闭** — `acc-security-server/.../util/sm2/SM2SMUtil.java:27-30`
`FileInputStream.close()` 在 try 块内 read 之后，read 抛异常时不关闭；`ASN1InputStream` 也未关闭。
修复：try-with-resources 同时管理两个流。

**M5. ScheduledExecutorService 无关闭钩子** — `resource/micro/web/.../utils/HostManager.java:28`
调度器随构造启动，但无 `DisposableBean/@PreDestroy` 调用 `shutdown()`，容器热重载场景线程泄漏。
修复：实现 `DisposableBean` 关闭调度器。

**M6. Prometheus 指标 Map 基数风险** — `resource/micro/monitor/prometheus/BaseExport.java:75`
`gaugeValueMap.computeIfAbsent` 以 group 为 key，若业务动态生成 group 名则 Map 与注册的 Meter 同步膨胀；现有清理阈值 10240 偏高且依赖外部触发。
修复：限制 group 基数或加白名单/LRU。

**M7. Netty 服务端关闭不完整** — `acc-es-server/.../netty/service/NettyServer.java:89`
`start()` 中 `closeFuture().sync()` 阻塞调用线程；`@PreDestroy close()` 调 `shutdownGracefully()` 但不等待完成，JVM 退出可能早于优雅关闭结束。另外 `@Sharable` 单例 handler 的 `channelInactive` 未做任何清理，一旦后续加入 per-channel 状态即泄漏。
修复：start 放入独立线程；close 对 shutdown Future `syncUninterruptibly()`；handler 改为每连接新建或显式清理。

**M8. 手动 SqlSession 异常吞没** — `acc-es-server/.../netty/service/BusinessHandler.java:200-213`
手动 `openSession(BATCH)`，catch 中仅打印异常，事务失败时仍 commit 后 close，批量更新可能部分提交（数据一致性风险，属资源误用类）。
修复：改用 Spring 管理的 `SqlSessionTemplate`，或 catch 中 rollback。

### 低危（风格/防御性问题）

| 位置 | 问题 | 建议 |
|---|---|---|
| `acc-security-server/.../reconnect/thread/ContextHolder.java:11` | `setReconnect(true)` 后无 `clear()` 调用，重连线程池线程残留 Boolean | 重连结束处补 `clear()` |
| `resource/micro/web/.../log4j2/ErrorInterceptorFilter.java:33` | `openLogEvent` 非 volatile，多线程可见性问题（队列满时 offer 静默丢弃，不致 OOM） | 改 `AtomicBoolean` |
| `web-server/.../config/ThreadPoolConfig.java`、`acc-security-server/.../config/ExecutorConfig.java` | 线程池未配置 `waitForTasksToCompleteOnShutdown`/awaitTermination，停机时任务丢失 | 补优雅停机参数 |
| `ticket-server/.../config/TicketAsyncConfig.java:16-24` | 队列容量 10000 且无显式拒绝策略（默认 Abort），大积压直接抛异常 | 缩小队列、显式拒绝策略、补优雅停机 |
| `acc-security-server/.../shortconnect/ShortConnectClientHandler.java:31` | 每请求 `AttributeKey.valueOf()` 动态创建 | 缓存 key 或改固定 key+Map |

### 已核查、无泄漏的项（供参考）

- `EsReportServiceImpl`（108/245 行）、`FileController`、`FileUtils`、web-common `HttpUtils` 的 sendGet/sendPost：finally 判空关闭，路径完整
- `SimpleProducer.confirmCallbackMap`：`convertSendAndReceive` 的 finally 中有 `clearSet()`，键集合有界
- `SimpleObservationMonitor`：WeakReference + ReferenceQueue 机制完备，清理线程为 daemon 且响应中断
- `pay-sign-server/PaySignExecutorConfig`、`collect-pay-server/Executor`：有界队列 + 显式拒绝策略 + Bean 注册，规范
- 其余 7 处 ThreadLocal（`LogAspect`、`AuthenticationContextHolder`、`DynamicDataSourceContextHolder`、`AbstractQuartzJob` 等）均有配对 `remove()`

---

## 三、架构问题与代码坏味道

### A1. 上帝类（God Class）

| 文件 | 行数 | 说明 |
|---|---|---|
| `web-common/.../utils/poi/ExcelUtil.java` | 1894 | 若依自带，导出/导入/图片处理全揉一起 |
| `pay-sign-server/.../PaySignWorkflow.java` | 1669 | 签约全流程编排 + 10 个依赖注入，职责过载 |
| `collect-pay-server/.../BomOrderServiceImpl.java` | 1625 | 17 个 public 方法的订单大服务 |
| `daily-ticket-server/.../DailyTicketServiceImpl.java` | 1028 | 18 个 public 方法 |
| `account-server/.../AccountApplicationServiceImpl.java` | 1003 | 账户申请全流程聚合 |

建议：按业务子流程拆分（如 PaySignWorkflow 拆为签约发起/回调处理/解约/查询四个协作者），优先拆支付链路——这是最可能出事故也最难回归的部分。

### A2. 分层违规：Controller 直接注入 Mapper（绕过 Service）

- `collect-pay-server/.../page/FacePayOrderPageController.java`
- `account-server/.../page/ItpUserPageController.java`
- `ticket-server/.../page/QRCodeTxnDetailPageController.java`、`QRCodeRideStatusPageController.java`
- `alipay-account-server/.../AlipayUserPageController.java`

运营端 page 控制器直连 MyBatis Mapper，事务边界与业务校验缺失（如 `QRCodeRideStatusPageController.update` 先查后改无事务、无并发控制）。建议下沉到 Service 层。

### A3. 重复实现 / 代码复制

- 两套 `HttpUtils`：`collect-pay-server/.../utils/HttpUtils.java` 与 `web-common/.../utils/http/HttpUtils.java`，行为与质量不一致（前者含 M3 问题，后者含 H6 问题）。
- `StringUtils` 721 行自研版本与 commons-lang3 并存；`UpdateDbMap` 用 `Map<String,String>` 传递更新字段（弱类型、无编译期检查），应改为实体/UpdateWrapper。

### A4. 架构一致性

- 微服务群（21 个模块，自研 `rpc` 模块用 `EnableRpcXxx` 注解装配 HTTP 客户端）+ 若依改造单体后台（`web-server`，含 quartz/generator/admin）并存，两套技术栈、两套工具类长期共存，维护成本高。
- `model` 共享模块被 23 处 pom 依赖，任何 DTO 变更影响面大；建议区分"对外契约模型"与"内部模型"。

### A5. 配置与安全

- **硬编码密钥**：`acc-security-server/src/main/resources/application.properties:25` 明文 `security.privateKey=2245...`；`collect-pay-server/src/main/resources/application.yml:10` 明文 `password: qditp`。票务/支付系统的私钥入库是高危项，建议迁移到密钥管理服务或至少环境变量注入，并轮换已暴露的密钥。
- `JwtUtils` 使用 `public static` 可变字段持有公私钥（`publicKey/privateKey`），任意代码可改写，应收敛为私有 + 只读访问。

### A6. 测试覆盖

`src/test` 下全项目仅 **1 个测试文件**（对应约 3168 个源文件）。支付、清分、密钥类模块在无测试保护下做任何重构都是高风险动作。建议先为 H1-H6 的修复补最小回归测试（字节缓冲释放、流关闭、ThreadLocal 清理均可用单测覆盖）。

### A7. 日志规范

约 18 个文件存在 `System.out.println`、15 个文件存在 `printStackTrace`（如 `FTP.java` 7 处、`FileUploadException.java` 6 处、`LocalCache.java`），生产代码应统一 SLF4J。

### 值得肯定的地方

- `.gitignore` 配置完整，`target/` 等构建产物未入库；
- 多数 ThreadLocal 有规范 remove、多数 IO 路径有 finally 关流，说明团队有基本规范，问题集中在历史遗留工具类与个别高频服务；
- `SimpleProducer` 回调清理、`SimpleObservationMonitor` 的弱引用监控写法是合格的参考实现。

---

## 四、修复优先级建议

**第一批（止血，约 1 周）**：H2（OkHttpClient 单例化）、H4（ThreadLocal 统一清理）、H1（ByteBuf 释放）、H5/H6（流关闭）——改动小、收益直接，均建议配最小单测。

**第二批（排期，2-4 周）**：H3（SSE 重构）、M1-M8、A5 密钥治理（密钥轮换需与运维协同）。

**第三批（架构演进，按迭代推进）**：A1 上帝类拆分（支付链路优先）、A2 分层收敛、A3 工具类归一、A6 测试基线建设（建议以支付/清分模块为起点）。

---

*说明：行号以审查时仓库状态为准；`jkube` 目录为 eclipse-jkube 开源项目克隆，未纳入本次审查。*
