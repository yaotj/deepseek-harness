# 公共构件详细设计（model / rpc / resource-micro）

> 对应 `AGENTS.md` §3.2 中 model、rpc、resource/micro 三行的「详情」。
> AGENTS.md 5.1 规定 **NEVER 新建工具类，MUST 先在这三个模块中找**。本文档就是那份查找清单。

---

## 一、model —— 公共 DTO / 枚举 / 响应封装

路径 `model/`，参与根聚合构建。包根 `com.chinasofti.huateng`。

### 1.1 包结构

```
common/
  constant/   FepAppErrorCodeEnum
  message/model/  MessageHead
  response/   CommonResult  ResultVO  ResultMapper  AlipayCommonResponse
  util/       ByteConvert  ByteConvertUtil
model/
  app/        IF8A 域全部 ReqDTO / Result（约 80 个）+ app/dailyticket/ 日票 DTO
  ticket/     乘车码 DTO + ticket/enums/QRCodeStatusEnum
  pay/        GateTxnPay 系列 DTO
  paysign/    PaySignInfoDTO  PayTxnDetailDTO
  security/   RequestDpk / UserSm2Key / SignPubkey / HceCardData 等 HSM DTO
  employee/   员工卡 DTO
  agm/        AGM 密钥同步 DTO
  alipaytrip/ 支付宝出行域全部 DTO（约 40 个）
  acc/        ACC 任务与同步 DTO
  para/ frs/  参数、票价与费率 DTO / VO
  enums/      CardTypeCodeEnum  DeviceTypeEnum  IssueChannelCodeEnum  TrxTypeCodeEnum
  front/      HttpHeader / RequestHead / ResponseHead / ResponseBuilder
  utils/      SignChannelUtils
  autoclear/ devinfo/ devserver/ fephandle/ fileProxy/ iam/ manage/ token/
```

### 1.2 高频复用点（改动前必查）

| 需求 | 已有实现 | 位置 |
|---|---|---|
| 统一返回码封装 | `CommonResult`（retCode/retMsg） | `common/response/CommonResult.java` |
| 公共请求报文骨架（providerId / charset / format / timestamp / deviceId / signType / sign / bizData） | `ItpCommonRequest<T>`；form-data 入向用 `ItpCommonFormRequest`（`extends ItpCommonRequest<String>`） | `model/app/` |
| 运营端返回封装 | `ResultVO` + `ResultMapper`（ok / error / illegalParams / signError） | `common/response/` |
| 卡类型判定（含日票判定） | `CardTypeCodeEnum`（含 `isDailyTicket`）、`CardTypeMapping` | `model/enums/`、`model/app/` |
| 乘车码状态机 | `QRCodeStatusEnum`（含 `isClosedLoop` / `isOpenLoop`） | `model/ticket/enums/` |
| 交易类型 / 设备类型 / 发行渠道 | `TrxTypeCodeEnum`、`DeviceTypeEnum`、`IssueChannelCodeEnum` | `model/enums/` |
| 字节与十六进制转换（设备报文用） | `ByteConvert`、`ByteConvertUtil` | `common/util/` |
| 签约渠道判定 | `SignChannelUtils` | `model/utils/` |

### 1.3 约束

- 跨模块传输对象 **MUST** 定义在 `model`，**NEVER** 在业务模块内复制一份。
  已知反例：`acc-secure-server` 的 `accsecure.model.*` 与 `model/model/security/*` 存在 8 组重复 DTO，属技术债，勿模仿。
- **公共请求报文骨架只有 `model/app/ItpCommonRequest` 与 `ItpCommonFormRequest` 两个类，NEVER 再在业务模块新建副本**。
  2026-09-11 已把 7 份字段完全相同的副本（`fep-app-server` / `fep-acc-server` 的 `CommonRequest` + `CommonFormRequest`、`fep-dev-server` / `fep-alipay-server` 的 `CommonFormRequest`、`account-server` 的 `CommonRequest`、`collect-pay-server` 的 `AppCommonRequest`、`acc-secure-server` 的 `AccCommonRequest`）全部删除并收口，涉及 23 个引用文件、8 个模块。
  **这两个类只承载报文骨架，不承载签名语义**：四条链路的加签 / 验签算法互不相同（`AccountRequestVerifier` 摘要式 SHA1/MD5 + signKey、`AccSecureSignUtils`、`AppNotifyServiceImpl.buildItpSign` 的 fastjson2 摘要、`collect-pay-server` 的 `signType=00` 不签），**NEVER 因为共用同一个类就顺手统一签名逻辑**，那会同时改动多个已与对端约定好的链路，属 AGENTS.md §5.2 安全红线。
  `toString` 已在公共类内对 `sign` 恒定脱敏，**NEVER 改成打印真实签名值**；注意 `bizData` 会完整进日志，`fep-alipay-server` 的 controller 有 8 处 `log.info("...{}", request)` 因此从「只打对象 hash」变成「打全部字段」。
- 枚举取值一旦落库即为数据契约，**NEVER** 修改已有取值，只能新增。
- 序列化统一 Fastjson2。

---

## 二、rpc —— 服务间调用客户端

路径 `rpc/`，参与根聚合构建。包根 `com.chinasofti.huateng.rpc`。

### 2.1 组成

`rpc/src/main/java` 下共 **28 个类** = **14 个 Client** + `URLDynamicRouter` + **13 个 `@EnableRpcXxx` 注解**。

14 个 Client：

```
account/AccountClient            ticket/TicketClient
paySign/PaySignClient            pay/GateTxnPayClient
dailyticket/DailyTicketClient    collectpay/CollectPayClient
para/ParaClient                  key/KeyClient
blacklist/BlacklistClient        industry/IndustryDataClient
security/SecurityClient          fepDev/FepDevClient
alipay/account/AlipayAccountClient
alipay/paysign/AlipayPaySignClient
route/URLDynamicRouter           （动态路由辅助类，不是 Client）
```

### 2.2 工作机制

- 底层是 `resource/micro/web` 的 `ProxyWebClient`（Spring WebClient 封装），主方法 `postJsonAndGetResponse`。
  - **它住在 `resource/micro/web` 的 `micro.web.client` 包、不在 `rpc` 模块**，因此「走 rpc 模块」与「继承 `ProxyWebClient`」是两件事。**对接外部系统的 client 照样该继承它，但 NEVER 搬进 `rpc`**（`rpc` 只放 ITP 内部服务 Client；外部系统 client 的位置见 pay-sign-server 的 `PayGatewayClient` 与 gate-txn-pay-server 那三个，ADR-D68）。
  - 提供：连接池（maxConnections 500 / pendingAcquire 1000 / maxIdleTime 20s / maxLifeTime 5min）、连接超时 3s、响应超时默认 10s、`openLogger` 控制的出入报文日志、`authorization` 的 MDC 透传、统一失败日志。
  - **要按配置定响应超时 MUST 用带 `responseTimeout` 参数的构造器**（2026-09-14 新增）。`getResponseTimeout()` 在**父类构造期**就被调用，**NEVER 覆写它去读子类 `@Value` 字段** —— 那时字段还是 0 / null，编译、单测、启动全都不报错，只会静默把超时改错。`rpc` 里既有 7 处覆写（`ReconClient` / `ReconExportClient` 5min、`PaySignClient` / `FepDevClient` 30s、`CardPoolClient` 15s、`TicketClient` / `TransQueryClient` 10s）**全部返回常量**，不受影响。
- 目标地址来自各调用方 `application.properties` 的 `service.*.url`，**无服务发现、无负载均衡、无熔断**。
- 各模块通过 `@EnableRpcXxx` 注解按需装配（如 `@EnableRpcAccount`、`@EnableRpcRoute`、`@EnableRpcPara`），启动类上声明。

> ⚠️ **装配机制有两个例外，勿凭 Client 名推断注解名**：
> - **不存在 `@EnableRpcAlipayPaySign`**。`AlipayPaySignClient` 由 `@EnableRpcPaySign` 一并扫描（`EnableRpcPaySign.java:14` 同时扫 `rpc.paySign` 与 `rpc.alipay.paysign`）。
> - **不存在 `@EnableRpcFepDev`**。`FepDevClient` 没有任何 Enable 注解，只靠「启动类位于根包 `com.chinasofti.huateng`」时的默认组件扫描生效（`ticket-server` / `fep-app-server` / `fep-dev-server` 即如此）。
>
> 这一默认扫描同时掩盖了若干「注入了 Client 但启动类没加对应注解」的情况（如 `fep-dev-server` 的 `GateTransactionHandler` 注入 `AlipayPaySignClient`）。目前不会启动失败，但**一旦把启动类下移包路径就会断**，属隐性耦合。新增调用 **MUST** 仍显式加注解，**NEVER** 依赖根包扫描。

### 2.3 约束

- 新增跨服务调用 **MUST**：① 在 `rpc` 对应 Client 加方法；② 在调用方启动类加 `@EnableRpcXxx`（无对应注解的按上述例外处理）；③ 在调用方配置补 `service.xxx.url`。
- **NEVER** 引入 Feign / Dubbo / RestTemplate 新实例，**NEVER** 在业务模块内直接 `new WebClient()`。
- 无熔断与重试机制：调用失败的兜底 **MUST** 由业务层落库 + `@Scheduled` 补偿，参考 `pay-sign-server` 的 `AppNotifyServiceImpl.compensateNotify()`。
- 配置现状有多处端口错配（如 `service.industryData.url` 指向 9104），还有三处依赖存在但 URL 键完全没配（落到无法解析的默认服务名），排查前先读 [`overview.md` §三](overview.md)。

---

## 三、resource/micro —— 自研基础构件

路径 `resource/micro/`，**不在根 `pom.xml` 聚合列表中**。修改后 **MUST** 先单独 `mvn install` 再构建业务模块。

### 3.1 四个子构件

| 子构件 | 启用注解 / 核心类 | 说明 |
|---|---|---|
| `sql-datasource` | `EnableMoreDruidDataSourceConfigAutoConfig` | Druid 多数据源装配，配置前缀 `other.sql.*`（`host` / `database` / `username` / `password` / `type` / `page.type` / `double-datasource`） |
| `mybatis-adaptor` | `EnableDefaultMybatisAutoConfig` | MyBatis + PageHelper 自动装配，业务模块启动类必加 |
| `web` | `ProxyWebClient`、`GlobalControllerExceptionHandler`、`EnableExporterAutoConfig`、`VictoriaLogsAppender` | RPC 客户端底座、全局异常兜底、监控指标导出、日志推送 VictoriaLogs |
| `rabbitmq-adaptor` | `EnableDefaultRabbitmqAutoConfig` | **历史遗留，项目不使用 MQ**。`src/main/resources/mq.properties` 含主机与 guest 口令，建议整体移除，**NEVER** 在业务模块引用 |

### 3.1.1 web 的日志构件（含 VictoriaLogs 推送）

日志栈是 log4j2（`starter-log4j2` + `log4j-layout-template-json`，logback 已被排除；只有 `web-server/web-admin` 例外用 logback）。相关类与资源全在 `web` 子构件内：

| 类 / 资源 | 作用 |
|---|---|
| `log4j2-linux.xml` | 全服务默认日志配置：`RollingFile`（带 `%X{traceId}`）+ `Console`（**不带** traceId）+ `ErrorInterceptorFilter` + **`VictoriaLogs`（2026-09-08 新增，`VLOGS_URL` 未注入时自动禁用）** + `VLOGS` 专用 logger |
| `CustomLoggingConfiguration` | Linux 且 `logging.config` 为空时，在 `ApplicationReadyEvent` 用上面那份配置重载。**模块自带 `logging.config` 时本类直接 return**。⚠️ 它原来用的是 `Configurator.initialize(name, configLocation)`，**该重载不传 ClassLoader**，2026-09-08 实测在 Spring Boot fat jar 下**解析不到 micro 嵌套 jar 里的自定义 appender**（para-server 已注入 `VLOGS_URL` 却 `vlogs-sender` 线程数 0、零上报，而配置重载成功、`RollingFile` pattern 已生效）。**已于 2026-09-08 改为 `Configurator.initialize(ctx.getName(), CustomLoggingConfiguration.class.getClassLoader(), linuxFile)`**，同日实测 para-server 2.0.19 起 `vlogs-sender` 线程数 1、上报正常。**NEVER 退回不带 ClassLoader 的重载**——本文件 `status="off"`，log4j2 自己的 `Unable to locate plugin` 会被吞掉，只能靠「上报为 0」反推。 |
| `ErrorInterceptorFilter` | log4j2 插件，统计 ERROR 供 `LoggerExport` 上报 |
| `VictoriaLogsAppender` | 把日志以 JSON Lines 推送到 VictoriaLogs `/insert/jsonline` |
| `victorialogs-event-template.json` | `JsonTemplateLayout` 的 event template：定义 `_time`（UTC ISO8601）/ `_msg` / `level` / `logger` / `thread` / `traceId` / `spanId` |

`VictoriaLogsAppender` 的关键约束（改动前必读）：

- **类存在 ≠ 启用**。log4j2 只在配置里出现 `<VictoriaLogs>` 元素时才实例化插件，未声明的模块不起线程、不发请求，零影响。**2026-09-08 起公共 `log4j2-linux.xml` 已声明它**，因此全部走公共配置的服务都具备上报能力，但**是否真的上报取决于有没有注入 `VLOGS_URL`**（未注入 = appender 自动禁用）。`pay-sign-server` 仍用自己的 `log4j2-paysign.xml`（`logging.config` 指向它 ⇒ `CustomLoggingConfiguration` 直接 return），两份配置互不影响、**不会重复上报**。
- **上报量由两级过滤控制，MUST 理解清楚再改**：Root 只把 **WARN 及以上**推给 VictoriaLogs；要拿到某条链路的 INFO 全量，靠 MDC 里的 `x-vlogs-capture=1` 命中 appender 上的 `ThreadContextMapFilter`。该键的两个来源是「入向请求头 `X-Vlogs-Capture` 经 `FirstFilter` 小写后放入」和「本端主动发起时由 `QuartzTraceUtils.runWithTrace` 放入」。**NEVER 把 Root 的级别放到 INFO** —— 那会把各服务的报文日志整体搬进日志后端，量与敏感面都不可控。
- **想让 `x-vlogs-capture` 真正生效，该模块配置里 MUST 有一个业务包 logger 挂「不带 `level` 的」`<AppenderRef ref="VictoriaLogs" />`**（`<Logger name="com.chinasofti.huateng">`）。原因：log4j2 把 `AppenderRef` 的 `level` 属性编译成**引用层过滤器，先于 appender 自己的 `<Filters>` 求值**。若只有 Root 的 `<AppenderRef ref="VictoriaLogs" level="WARN"/>`，INFO 事件在引用层就被 DENY，**根本走不到** `ThreadContextMapFilter`，链路采集全程失效。2026-09-08 在 web-admin 实测踩过：「appender 已实例化（`vlogs-sender` 线程在跑）+ MDC 键确实写入 + Root 的 WARN 能正常上报 + 新代码已部署」四项全部成立，却查不到任何 INFO，现象极像「过滤器不匹配」，实际是引用层短路。**NEVER 只加 Root 的 ref 就认为链路采集已具备。** 三份配置（公共 `log4j2-linux.xml`、`log4j2-paysign.xml`、`log4j2-web-admin.xml`）**均已补齐**该 logger，新增模块专属配置时也 MUST 带上。
- `url` 由环境变量 `VLOGS_URL` 注入，**为空则 appender 自动禁用**，文件与控制台日志不受影响。只给 `scheme://host:port` 时代码自动补 `/insert/jsonline?_stream_fields=app,host&_msg_field=_msg&_time_field=_time`；**NEVER** 让 `_stream_fields` 缺失，否则每种字段组合都变成一条 stream，直接打爆索引。
- 序列化由内嵌 `JsonTemplateLayout` 承担，appender 只做「批量攒 + HTTP 发送」。**加字段改 template 即可，不必改 Java、不必重新构建本模块**。
- `app` / `host` 用 `<EventTemplateAdditionalField>` 在各模块自己的 log4j2 配置里给出，template 保持通用。
- **MDC 只输出 `traceId` / `spanId` 两个白名单键**。`FirstFilter` 会把全部请求头（含 `authorization`）塞进 MDC，**NEVER** 把 template 改成全量输出 MDC，那等于把令牌送进日志后端。
- 业务线程只做「序列化 + 入有界队列」，发送在**平台线程** `vlogs-sender` 上进行；队列满即丢弃并计数，绝不阻塞业务线程。全服务开着虚拟线程，在虚拟线程上做阻塞 HTTP 会 pin 载体线程。
- 内部异常只走 log4j2 StatusLogger 且按分钟限流。各模块配置多为 `status="off"`，因此**这些自述日志默认看不到**；判断是否启用要看 `vlogs-sender` 线程是否存在（`jcmd <pid> Thread.print | grep vlogs-sender`）。
- template 里 `_msg` 的 pattern resolver **必须带 `"stackTraceEnabled": false`**。它默认 `alwaysWriteExceptions=true`，会在显式 `%ex` 之外再追加一份 `%xEx`，导致异常栈在 `_msg` 里出现两遍，且追加的那份不受 `%maxLen` 长度限制（2026-09-07 实测并修复）。

哪些日志会被推送由各模块的 logger 配置决定，与 appender 无关。`pay-sign-server` 的口径是：Root 用 `<AppenderRef ref="VictoriaLogs" level="WARN"/>` 只推 WARN 及以上；另有 `VLOGS` logger（`additivity="false"`）全量推送，业务侧用 `LoggerFactory.getLogger("VLOGS")` 显式埋点。

写模块专属 log4j2 配置时另有两条实测坑（2026-09-11 在 `collect-pay-server` 踩过，两次构建才闭合）：

- **`<Properties>` MUST 是 `<Configuration>` 的第一个子元素**。把配置级 `<Filters>` 写在它前面时，整份配置的 `${logPath}` / `${appName}` / `${webLoggerLevel}` **全部不做替换**：日志文件被建成字面量路径 `/home/javaapp/soft/${logPath}/${appName}.log`，Root 级别失效、INFO 全丢，只剩 WARN 进 console。`status="off"` 会吞掉 log4j2 自己的报错，**唯一可见线索是文件名里带着 `${}`**，判据 MUST 是 `find / -name '*.log'` 看真实落盘路径，**NEVER 只看 console 有没有输出**。
- **容器内真实日志目录带 Pod 名一级子目录**：`/home/javaapp/app/logs/<POD_NAME>/<appName>.log`。模块自带 `logging.config` 时 `CustomLoggingConfiguration` 直接 return、`${sys:logPath}` 不会被赋值，因此默认值 MUST 写成 `${sys:logPath:-/home/javaapp/app/logs/${env:HOSTNAME:-local}}`（与 `log4j2-paysign.xml` 同口径）。少写 `${env:HOSTNAME}` 会让 `scripts/klog.sh` 什么都看不到——现象与「服务没日志」完全一样。
- 按 URL 静音高频接口用**配置级 `<Filters>`**：`<ThresholdFilter level="WARN" onMatch="ACCEPT" onMismatch="NEUTRAL"/>` + `<RegexFilter regex="(?s).*<关键字>.*" onMatch="DENY" onMismatch="NEUTRAL"/>`。它先于所有 logger 求值，能一并干掉 `FirstFilter` / `MoreInterceptor` / `BodyCacheFilter` 三行框架日志；WARN 兜在前面保证该接口真出错时仍可见。**NEVER 改用 `logging.level` 压这三个类**——那是按类生效，会把所有接口的报文日志一起干掉，报文日志是重放与排障的唯一依据。已落地：`collect-pay-server` 用它静音 `notiDeviceHeard`（设备心跳原先占日志量 79%，实测 3000 行里 1992 行）。


### 3.2 约束

- 数据访问 **MUST** 走 `mybatis-adaptor` + `resources/mapper/*.xml`，**NEVER** 写原生 JDBC 或 MyBatis 注解 SQL。
- 无数据库的模块（fep-app-server、fep-dev-server、fep-alipay-server、industry-data-server、acc-security-server、acc-secure-server、fep-acc-server）**NEVER** 加 `other.sql.*` 配置或 mapper。配了 `other.sql.*` 的模块共 12 个。
- 全局异常处理已由 `GlobalControllerExceptionHandler` 兜底，**NEVER** 在每个 controller 里重复 try-catch 包装。

---

## 四、常见踩坑点

- `resource/micro` 不参与根构建 → 改了自研持久层却直接跑 `mvn clean package` 时用的仍是旧 jar。
- `collect-pay-server` 用 `application.yml`，数据源键路径与其他模块的 `other.sql.*` 写法不同，复制配置会失效。
- `collect-ticket-server` **未配置 `other.sql.*`** 但有 mapper XML，数据源依赖外部注入，本地起不来属预期。
- `model` 中 `RequestBuySinlgeTicketMaxNumResult` 等类名保留了规范原文的拼写错误，**NEVER** 顺手改名（会破坏调用方）。

## 五、相关文档

- 架构总览与端口 / 调用拓扑：[`overview.md`](overview.md)
- 管理后台：[`web-server.md`](web-server.md)
- 业务域提示词：[`../business/README.md`](../business/README.md)

---

## 附：model / rpc / resource-micro 源码注释知识抽取（2026-09-16，阶段一）

> **有并发写入者，引用行号前 MUST 先 grep 现查。** 本节所有 `路径:行号` 都是 2026-09-16 抽取当时的快照，同一时段可能有人在改这三个模块。
>
> **本节约束影响 21 个模块，改动前 MUST 先评估受影响范围。** `model` / `rpc` / `resource/micro` 被 21 个模块的 pom 引用且版本号锁死（`model` 2.0.0 / `rpc` 2.0.1），一处改动的爆炸半径等于「依赖它且真正走到那段代码的全部模块」。
>
> 抽取范围：`model/src/main/java/**`、`rpc/src/main/java/**`、`resource/micro/*/src/main/java/**` 与 `resource/micro/*/src/main/resources/*.properties`。已丢弃复述字段名的 DTO Javadoc、`{@inheritDoc}`、空 Javadoc、getter/setter 段与分节横线；DTO 字段说明只在它构成对外契约约束时收录。归类为三类：**契约与判据 / 决策理由 / 陷阱**。墓碑注释（「NEVER 回退 / NEVER 改回 / 那版口径已作废」）不进正文，单列文末。

### 附.1 报文骨架与对外契约

- **契约**｜`model` · `ItpCommonRequest`（`model/src/main/java/com/chinasofti/huateng/model/app/ItpCommonRequest.java:4~19`）：ITP / APP / ACC 公共请求报文骨架，**全项目唯一实现**。字段集来自甲方规范公共参数部分，注释逐字列的是 `providerId、charset、format、timestamp、deviceId、signType、sign、bizData` 八个。原先 `fep-app-server`、`fep-acc-server`、`fep-dev-server`、`fep-alipay-server`、`account-server`、`collect-pay-server`、`acc-secure-server` 各有一份字段完全相同的副本，已于 2026-09-11 全部收口到本类。
- **契约**｜`model` · `ItpCommonRequest`（`.../app/ItpCommonRequest.java:11~15`）：「**本类只承载报文骨架，不承载签名语义。**各链路的加签与验签算法互不相同（account-server 的摘要式 SHA1/MD5 + signKey、acc-secure-server 的 AccSecureSignUtils、pay-sign-server 的 fastjson2 摘要、collect-pay-server 的 signType=00 不签、渠道方向的 RSA），**NEVER** 因为共用本类就把签名逻辑也统一，那会同时改动多个已与对端约定好的链路，属安全红线。」
- **契约**｜`model` · `ItpCommonRequest.toString`（`.../app/ItpCommonRequest.java:96`）：「日志友好输出。sign 恒定脱敏，**NEVER** 改成打印真实签名值。」实现里 `sign='***'`（`:107`），`bizData` 原样进日志（`:108`）。
- **契约**｜`model` · `ItpCommonFormRequest`（`model/src/main/java/com/chinasofti/huateng/model/app/ItpCommonFormRequest.java:4~12`）：form-data 入向专用，全项目唯一实现；与 `ItpCommonRequest` 的唯一区别是 `bizData` 固定为 `String`（公共参数平铺在 form-data、业务参数以 JSON 字符串提交，由各模块入口自行反序列化）。原先 `fep-app-server` / `fep-acc-server`（继承式）与 `fep-dev-server` / `fep-alipay-server`（平铺式）各有一份 `CommonFormRequest`，2026-09-11 收口。「与父类一致：**本类不承载签名语义**，验签由各链路自行负责。」`toString` 同样对 `sign` 脱敏（`:25`）。
- **判据**｜`model` · `SyncPayAccountIdReqDTO`（`model/src/main/java/com/chinasofti/huateng/model/app/SyncPayAccountIdReqDTO.java:11~12`）：「**本 DTO 不经过 `parseBizData`**（调用方是 pay-sign-server 而非 APP），按 `docs/domain/README.md` 的分类判据属**对内接口**，加字段不受「对外契约 NEVER 加字段」约束。」——这是仓库里把那条分类判据写进代码的样例。同类：`AppPayOrderRegisterReqDTO`（`model/src/main/java/com/chinasofti/huateng/model/collectpay/AppPayOrderRegisterReqDTO.java:7`）「因此加字段是安全的（对比 `parseBizData` ……）」；`QueryDailyTicketPayInfoReqDTO:4` / `QueryDailyTicketPayInfoResult:4` 标「服务间内部 DTO，非 APP 对外契约」。
- **契约**｜`model` · `DailyTicketRefundCallbackReqDTO`（`model/src/main/java/com/chinasofti/huateng/model/app/dailyticket/DailyTicketRefundCallbackReqDTO.java:6~7`）：「字段名逐字对齐支付中心回调报文，**NEVER 改名或增删**：本 DTO 既是 `parseBizData` 的目标类型、又是转发给 daily-ticket-server 的入参，改名等于改对外契约。」
- **契约**｜`model` · `UpdatePhoneReqDTO`（`model/src/main/java/com/chinasofti/huateng/model/app/UpdatePhoneReqDTO.java:7、17`）：规范字段是 `newPhone` + `thirdUserId`，「**NEVER** 改回 `newMsisdn` 作为规范字段」；`newPhone` 一条「**字段名本身是对外契约**：改名 MUST 连带重建 `fep-app-server`」。旧名只在 `PhoneChangeController` 的入向别名列表里兜底、不属于对外契约（`:9`）。
- **契约**｜`model` · `AdviceOptEnum`（`model/src/main/java/com/chinasofti/huateng/model/ticket/enums/AdviceOptEnum.java:19~21`）：「**码值 NEVER 改动**：000 / 005 / 006 / 018 / 020 是与 BOM、闸机的对外契约，改一个字符就是协议不兼容。新增取值 MUST 同时确认 `isSupplementExit()` 的归属 —— 漏归类会让 `GateFarePaymentOrchestrator` 的 `shouldPay` 走「未知即扣费」分支。」
- **契约**｜`model` · `ReconFileTypeEnum`（`model/src/main/java/com/chinasofti/huateng/model/recon/ReconFileTypeEnum.java:11~13`）：「**字段顺序来自甲方 `docs/接口规范文档/ACC与ITP之间的文件.docx` §一「对账文件」，改动 MUST 同步四个源服务与 recon-server**——分片是纯文本、无 schema，错位不会报错，只会静默出错账。」同类注释还写明四类文件段数（EXP 13 明细 / PAY 21 = 5 键 + 16 度量 / BUS / DETAIL），以及「PAY / BUS 是汇总文件，源服务 MUST 在数据库内 GROUP BY 后再上送」（`:8`）、「**EXP 是「单边账 / 异常交易」明细，NEVER 当成全量过闸明细**」（`:34`）。
- **契约**｜`model` · `GateTxnPayReqDTO.copyCommonFields`（`model/src/main/java/com/chinasofti/huateng/model/pay/GateTxnPayReqDTO.java:91~124`）：出站扣费请求「**MUST 由 fep-dev-server 在出站当次组装后整块透传**」，其中 `entryLineCode` / `entryLineName` 等 9 项「供首次扣费与后续重试复用，**NEVER 在下游重算**（重算必然缺那 9 项）」；「**本方法只搬运，NEVER 在此处加任何默认值、trim、大小写归一或空值兜底**」；「**NEVER 把本类独有字段（ticketStatus / orderExpType / offlineFlag / 站名 / ……）**」纳入搬运；调用方（当前只有 fep-dev-server）「**MUST 用重新 install 过的 `model` 重建镜像**」；`@param request` 处标「NEVER 传 null」。另记「行业明细用订单快照 `JSON.toJSONString(order)`，两套语义 **NEVER 混用**」（`:99`）。

### 附.2 CommonResult 与枚举

- **缺口**｜`model` · `CommonResult`（`model/src/main/java/com/chinasofti/huateng/common/response/CommonResult.java:3~17`）：**注释里只有 `@author zzm` / `@date` 与两个字段的「返回码 / 返回消息」**，没有取值集合、没有「`0000` 即成功」的约定。成功码 `0000` 只在 `rpc` 侧写着（`rpc/src/main/java/com/chinasofti/huateng/rpc/outcome/RpcOutcome.java:34~35` 的 `SUCCESS_CODE`「本项目服务间调用统一的成功码」）。
- **契约**｜`model` · `CardTypeMapping`（`model/src/main/java/com/chinasofti/huateng/model/app/CardTypeMapping.java:15~57`）：两个「聚合桶」是本类的核心语义。`DAY_TICKET_BUCKET_CARD_TYPE = "05"` 是 APP「电子日票」页签上送的聚合票种码（用户 2026-09-10 确认语义为「日票聚合桶」），「05 必须展开成多个发卡卡类型，用 `toIssueCardTypes(String)` 取列表，**NEVER** 走单值映射」；`NFC_BUCKET_CARD_TYPES = List.of("03","04")`（用户 2026-09-10 裁决）「一律按「NFC 聚合桶」展开为 `0442`+`0443`，**NEVER 退回单值映射**：APP 传哪个都要能查到两类 NFC 卡的记录」，且明确只作用于查询链路（`toIssueCardTypes` 只被 IF8A-05 / IF8A-41 调用），开户侧走 `toIssueCardType` 的单值语义。
- **陷阱**｜`model` · `CardTypeMapping.toIssueCardType`（`.../app/CardTypeMapping.java:74~76`）：「**本方法对未识别值原样返回、不抛异常**，脏值会静默流到下游（SQL 命中 0 行、卡池预占匹配不到分区）。因此接口入口 **MUST** 先用 `isSupportedAppCardType(String)` 校验，**NEVER** 依赖本方法拦截非法票种。」`toIssueCardTypes` 同款告示（`:106`），`isSupportedAppCardType` 反向再写一次（`:126~127`）。
- **判据**｜`model` · `CardTypeMapping.toIssueCardType`（`.../app/CardTypeMapping.java:65~72`）：识别顺序是「爱山东 `044A` → APP 口径映射表 → **ACC 2 位票种码**（补 `04` 前缀）→ 未识别原样返回」；接受 ACC 口径的理由是「上游存在两套票种编码且**取值空间不重叠**」（APP 口径全是 `0x`/`1x`、ACC 口径全是 `4x`，交集为空）。
- **陷阱**｜`model` · `CardPoolTicketType.fromAccTicketType`（`model/src/main/java/com/chinasofti/huateng/model/cardpool/CardPoolTicketType.java:63~71`）：同一判据的另一半，并附事故记录 ——「2026-09-10 日票下单 `cardType=45`（ACC 口径），`CardTypeMapping.toIssueCardType` 因映射表无此键而原样返回 `45`，一路穿到 card-pool-server 预占，与任何 `CARD_TYPE` 分区都不匹配，卡池回 `code=null / msg=null`，激活报「发卡服务暂不可用」，订单 `0E202609101131510006` 支付后 1.5 小时才在激活环节暴露。」
- **契约**｜`model` · 三个状态机枚举（`SignStatus` / `SyncStatus` / `TerminationStatus`，`model/src/main/java/com/chinasofti/huateng/model/domain/`）：三者都写着同一条 —— 「本枚举**只做快速失败与错误提示，NEVER 当作并发保证**。并发保证唯一来自 mapper 的 CAS UPDATE，前置状态写在 SQL 的 WHERE 里」（`SignStatus.java:20~23、66`；`SyncStatus.java:24~25、64`；`TerminationStatus.java:28~30、73`）。`QRCodeStatusEnum` 也照同一口径（`model/src/main/java/com/chinasofti/huateng/model/ticket/enums/QRCodeStatusEnum.java:11~13、142~143`，并追加「也 NEVER 用它在 CAS 之前拦请求 —— 调用方手里的「当前状态」来自更早一次 select、随时可能过期」）。
- **契约**｜`model` · `SignStatus`（`.../domain/SignStatus.java:11~27`）：白名单 `NOT_SIGNED -> SIGNED|FAILED`、`FAILED -> SIGNED|NOT_SIGNED`、`SIGNED -> UNSIGNED`、`UNSIGNED -> NOT_SIGNED`（复位重签）；「**NEVER 允许 `UNSIGNED -> SIGNED`**：已解约通道被迟到的签约回调覆盖，会让 APP 显示通道有效而渠道侧协议已注销」；「**NEVER 改动这些常量的字面量**：库内已有存量数据按这些字符串存储，且 pay-sign-server 侧仍有 `private static final String` 常量与裸字面量在比较同一批值，改名等于制造两套口径。新增取值 MUST 同步 `ALLOWED` 与 mapper XML 的 CAS。」`isTerminal()` 注释点明「当前没有真终态：UNSIGNED / FAILED 都可复位重签」（`:71`）。
- **契约**｜`model` · `SyncStatus`（`.../domain/SyncStatus.java:13~25、59`）：白名单 `PENDING -> SUCCESS|FAILED`、`FAILED -> SUCCESS|FAILED`（重推再失败仅 `RETRY_COUNT+1`）；「**`SUCCESS` 是唯一终态，NEVER 允许从它迁出**：补偿扫表按 `SIGN_SYNC_STATUS IN ('PENDING','FAILED')` 捞取，一旦回退就会对已送达的记录重复推送」；「**NULL 不属于本枚举**…… **NEVER 把 NULL 兜底成 `PENDING`** —— 那等于对改造前的存量记录发起一轮真实 RPC 重推」；`isCompensable()` 标「与扫表 SQL 的 `IN ('PENDING','FAILED')` 必须保持一致」。
- **契约**｜`model` · `TerminationStatus`（`.../domain/TerminationStatus.java:11~34、86~92`）：白名单与 `AppTerminationRequestMapper.xml` 的 6 条 CAS 一一对应；「**NEVER 允许 `FAILED -> SUCCESS`**（用户 2026-09-12 裁决）」，理由是日级调度下 `SCANNING_TIMEOUT_MINUTES=1440` 已把滞留申请打成 `FAILED` 并给 APP 发过「解约失败」通知，迟到的成功回调若能改成 `SUCCESS`，APP 会收到两条相反通知，「而「已告知失败之后怎么补通知」是业务口径问题、不该由代码默认决定」，该场景 MUST 走人工；「**NEVER 允许 `SUCCESS` 迁出**」；`isNotifiable()` 处「**NEVER 把 `PENDING` / `SCANNING` 纳入**：那两个态还没有结果可通知，滞留后会被补偿当成「成功通知丢了」，给 APP 发一条 `status=SUCCESS` 且 `dismissalTime` 为空的假解约成功通知（2026-08-26 已修复过一次）」。另注意 `isTerminal()` 与「通知补偿扫表白名单」是两个概念（`:78~81`）。
- **决策**｜`model` · `AdviceOptEnum`（`.../ticket/enums/AdviceOptEnum.java:16~17`）：三处消费点中「第三处是**防资金损失的白名单**（BOM 已补出站的交易不再重复扣费），它与前两处对不上即漏扣或重扣，因此字典 MUST 只有这一份、放在 `model` 里由两个模块共用。」
- **陷阱**｜`model` · `AdviceOptEnum`（`.../ticket/enums/AdviceOptEnum.java:32~38`）：「**厂家字典与本枚举的 Java 常量名互相错位，读码值、NEVER 读名字。**BOM 侧原厂枚举 `com.bestone.itp.bom.enumtype.CardAdviceOpt` 是 `NO_UPDATE(000) / FREE_IN_20(005) / ADD_OUT(006) / ADD_IN(018) / FREE_UPDATE(020)` —— 厂家的 `FREE_UPDATE` 是 **020**，而本枚举的 `FREE_UPDATE` 是 **005**。……**NEVER 把 005 改名成 FREE_IN_20 再让 FREE_UPDATE 指向 020** —— 现有 5 处 `AdviceOptEnum.FREE_UPDATE` 调用点会照旧编译通过，但含义从 005 静默变成 020，编译器与单测都拦不住。」
- **契约**｜`model` · `AdviceOptEnum.isSupplementExit` / `SUPPLEMENT_EXIT_CODES`（`:140~161`）：「**这是资金安全判据**：命中即表示该乘客的出站费用已在 BOM 侧结清，出站扣费 MUST 跳过；不命中（含解析不出的未知取值）MUST 照常扣费。」两者「语义完全一致，两者 MUST 同步改动」。方向分组由 `isSupplementEntry()`（018 / 020）与 `isSupplementExit()`（005 / 006）承担、互斥不重叠（`:27~29`），区域判据留在 `SupplementStateRules.UPDATE_RULES`、「NEVER 混到一处」（`:133`）。
- **陷阱**｜`model` · `QRCodeStatusEnum.ALLOWED`（`.../ticket/enums/QRCodeStatusEnum.java:111~117`）：「改 `ExcessFareHandler.resolveAllowedTypes` 的允许集合时 MUST 同步看齐这里，否则每笔补站都会打一条「迁移不在白名单内」WARN」；「**10（入站码更新）没有任何写入路径**（2026-09-09 实测生产库 0 行），它的出边是理论值、当前不可达，NEVER 把新功能的前置条件挂在 10 上」；运营端 `updateCodeStatus` 是人工改值、**刻意不受白名单约束**。`ALWAYS_ALLOWED_TARGETS` 只收 70 / 03，「FF（进站失败）只在 03 / 05 / 06 / 80 / FF 之后合法，因此 **NEVER 加进本集合**」（`:137`）。
- **契约**｜四个枚举的 `parseOrNull` / `fromCode` 统一口径：宽松解析、解析不出返回 `null` 而不抛异常，「**NEVER 在这里兜底成某个具体状态** —— 猜错方向会把脏数据推进状态机」（`SignStatus.java:50~53`、`SyncStatus.java:43~45`、`TerminationStatus.java:56~59`），「上游（BOM / 闸机）可能上送未登记的取值，处置方式 MUST 由调用方决定，NEVER 在这里抛」（`AdviceOptEnum.java:103`、`QRCodeStatusEnum.java:157`）。
- **决策**｜`model` · `IssueChannelCodeEnum.thirdUserIdLength`（`model/src/main/java/com/chinasofti/huateng/model/enums/IssueChannelCodeEnum.java:60~85`）：支付宝 10 位、其余 8 位。上移理由（2026-09-14 / ADR-D70）是「长度散落在 `fep-dev-server` 的 `DeviceUserIdCodec.normalize` 与 `ticket-server` 的 `SupplementCodec.normalizeDeviceThirdUserId` 两份实现里，而「谁算支付宝」的判断一直在本类 —— **同一条渠道规则的两半分居两地**，这是上移的唯一理由」；「**两处产出必须永远相等**」，都写 `NotifyVerifyResultReqDTO.itpUserId` → `QRCODE_TXN_DETAIL.ITP_USER_ID`，「长度一旦分叉，同一个用户在同一张表里会出现两种位数，按它查历史必然漏行、且不报错」。边界：「**本方法只管长度，NEVER 把补位或进制转换搬进来**」（fep-dev 收十六进制、ticket-server 拿到的是十进制，「把整个函数上移就得带一个 `boolean hexInput` 或两个重载，等于让 `model` 承担「AGM 用十六进制」这个只属于接入层的事实」）；「未知 / null 渠道码返回 8……**NEVER 改成先 `fromCode` 再取实例属性**，那会给未知渠道引入一条 null 路径」。
- **契约**｜`model` · `ReconRecord`（`model/src/main/java/com/chinasofti/huateng/model/recon/ReconRecord.java:3~7`）：四类对账文件的唯一格式出口，分隔符 `|`、行分隔符 `\n`。「**NEVER 在业务代码里自行用 `String.join("|", ...)` 拼行**：字段里一旦带上分隔符或换行，整个文件从该行起全部错位，而纯文本没有 schema、下游读不出异常。本类统一做净化。」
- **契约**｜`model` · `ReconExportReqDTO`（`model/src/main/java/com/chinasofti/huateng/model/recon/ReconExportReqDTO.java:15~16`）：各源时间列类型不同（一处 `yyyy-MM-dd HH:mm:ss`、daily-ticket 是 TIMESTAMP），「各源 MUST 用本类提供的转换方法落到自己的列类型上，NEVER 在 SQL 里对时间列做函数转换 —— 那会让分区裁剪与索引全部失效」。
- **契约**｜`model` · `CardPoolOutcome`（`model/src/main/java/com/chinasofti/huateng/model/cardpool/CardPoolOutcome.java:22~31`）：`REJECTED`（票种不走卡池 / 票种非法 / 业务归属缺失或冲突）「属调用方参数或配置问题，重试无用，MUST 告警」；`CALL_FAILED`（连接失败 / 超时 / 响应无法解析 / 服务端内部错误）「可重试」。`CardPoolReserveResult`（`.../cardpool/CardPoolReserveResult.java:7`）补一条「`getMessage()` 是服务端原文，只用于日志与告警，NEVER 直接透给终端用户」。

### 附.3 RpcOutcome 与各 Client

- **决策**｜`rpc` · `RpcOutcome`（`rpc/src/main/java/com/chinasofti/huateng/rpc/outcome/RpcOutcome.java:6~9`）：「**为什么不是 boolean**：AGENTS.md §5.2 为「内部 catch 全部异常后 return false、从不抛异常」的包装方法专门写了一条规则，而它被违反过两次并双双造成生产不一致（见 `docs/domain/decisions.md` ADR-D13）。规则靠人记、boolean 靠人查；sealed + record 让穷尽性由**编译器**检查 —— 调用点少写一个分支就编译失败。」
- **判据**｜`rpc` · `RpcOutcome`（`.../outcome/RpcOutcome.java:11~18`）：三分支处置 ——「**真正的收益是「业务拒绝」与「网络不可达」终于可区分**，两者在 boolean 下都是 false」。`BizRejected`「对端答复了、但业务上拒绝（如「该用户在支付域没有签约记录」，表现为 UPDATE 影响 0 行后返 FAIL）。**重推一万次也不会成功**，MUST 直接转终态 / 工单，NEVER 放进补偿队列反复重试」；`Unreachable`「请求没能拿到业务答复（连不上、超时、HTTP 4xx/5xx）。这才是补偿队列该收的那一类」。
- **判据**｜`rpc` · `RpcOutcome`（`.../outcome/RpcOutcome.java:20~24`）：「**HTTP 4xx 归到 `Unreachable` 是有意的保守选择**：`ProxyWebClient.handleResponse`（`resource/micro/web/.../client/ProxyWebClient.java:138`）把 `createError()` 产生的 `WebClientResponseException` 与连接失败一起包成 `RuntimeException`，包装方法拿不到可靠的区分依据。误判方向 MUST 是「把永久失败当成可重试」（代价是白重试几轮），**NEVER 反过来** —— 把网络抖动当成业务拒绝会让一笔真实待投递的事实被直接判死。」（注：注释里引用的 `ProxyWebClient.java:138` 与现文件行号不一致，见文末缺口）
- **决策**｜`rpc` · `RpcOutcome`（`.../outcome/RpcOutcome.java:26~30`）：「**本类型 NEVER 参与序列化**：它是进程内的调用结果，不是报文 DTO，因此放在 `rpc` 而不是 `model`。跨服务的报文契约仍是各 `*Result` / `*RespDTO`。」；「**落地方式是「新增方法、老方法保留」**：`rpc` 版本号锁死在 2.0.1、被 21 个模块引用（AGENTS.md §7），因此 MUST 只增不改签名，逐调用点迁移。」
- **契约**｜`rpc` · `RpcOutcome`（`.../outcome/RpcOutcome.java:44~74`）：`BizRejected.retMsg`「仅用于日志与工单，NEVER 用它做分支判断」；`Unreachable.cause`「MUST 保留以便日志带栈；调用方 NEVER 只打 `getMessage()`」；`isOk()`「**只用于日志与计数，分支判断 MUST 用 switch 模式匹配**，否则又退回 boolean」；`ofRetCode`「响应体为空、或 `retCode` 缺失，都算 `BizRejected` 而不是 `Unreachable` —— 那种情况下 HTTP 已经 2xx，是对端契约问题，重推同一报文不会变好」。
- **决策**｜`rpc` · `PaySignClient.updatePaySignDisplayAccount`（`rpc/src/main/java/com/chinasofti/huateng/rpc/paySign/PaySignClient.java:345~353`）：`@Deprecated` 兼容壳。「boolean 把「支付域业务拒绝」与「网络不可达」压成同一个 false……这条方法本身也是 AGENTS.md §5.2 点名的两处之一。**本方法保留只为不改签名**（`rpc` 版本号锁死、被 21 个模块引用），实现已委托给新方法，**NEVER 在此重复一份解析逻辑**。」实现体是 `updateDisplayAccountOutcome(...).isOk()`。
- **契约**｜`rpc` · `PaySignClient.updateDisplayAccountOutcome`（`.../paySign/PaySignClient.java:356~387`）：支付域在 `APP_PAY_SIGN_INFO` UPDATE 影响 0 行时就返 FAIL（`PaySignAppController:159`），即「该用户没有签约记录」也走这条 ——「那属 `BizRejected`，**MUST 直接转终态 / 工单，NEVER 反复重推**」；「本方法**NEVER 向外抛异常**：异常统一收成 `Unreachable` 并带上原始 cause……调用方仍 MUST 自行决定抛不抛」。行内注释还写明「解析 MUST 与上面的调用一样收在本方法内：HTTP 已 2xx 但响应体不是 JSON（如网关返回 HTML 错误页）时 `JSONUtil.parseObj` 会抛 `JSONException`，逃出去就打破了本方法「NEVER 向外抛异常」的契约 —— 同步链路会退化成全局异常处理器的 UUID retCode，补偿链路则因状态与重试次数都不落库而永远没有出口」（`:377~379`）。
- **陷阱**｜`rpc` · `PaySignClient.compensateSignNotify`（`.../paySign/PaySignClient.java:262~265`）：「**调用方 NEVER 在同一次调度内循环调用本接口。**下游在提交重发**之前**就同步递增 `NOTIFY_RETRY_COUNT` 并把状态置为 FAILED，而真正的通知是异步发出的；紧接着再调一轮会立刻扫到同一批（计数已 +1、结果还没回写），几秒内烧完全部重试预算。排空要靠 cron 周期，调度间隔 MUST 大于下游的 PENDING 滞留阈值。」返回的 `submitted`「只代表「提交成功」，NEVER 用它判断通知是否送达，真实结果看 `APP_PAY_SIGN_REQUEST` 的 `NOTIFY_STATUS` / `NOTIFY_RESULT`」（`:267~268`）。
- **陷阱**｜`rpc` · `PaySignClient`（`.../paySign/PaySignClient.java:271~273`、`288`、`313`、`336`）：「下游是无参 POST，但 body **MUST NOT 传 null**：`ProxyWebClient.postJsonAndGetResponse` 无条件调 `bodyValue(requestBody)`，Spring 的 `BodyInserters.fromValue` 断言非 null，传 null 会抛 `IllegalArgumentException: 'body' must not be null`，请求根本发不出去。传空 Map 序列化成 `{}`。」四处方法各有一份同源注释。同一坑的**反向**样例在 `F2FClient.post`（`rpc/src/main/java/com/chinasofti/huateng/rpc/f2f/F2FClient.java:93~95`）：「请求体保持原有的 `null`，**NEVER** 改成空 Map —— 下游 `NoticeAppTask` 的入参形态未经验证，改动等于改契约。」两条并列保留、不合并。
- **判据**｜`rpc` · `PaySignClient.compensateRefundQuery` / `compensateRefundSummary`（`.../paySign/PaySignClient.java:301~332`）：「**它与 `compensateRefundSummary` 是两件不同的事，NEVER 合并调度**：本方法**会出网**、要贴着退款时效跑；那个**不出网**、只重算本地两表汇总，属日终级别。合并后既没法分别调频，出网这半边一挂还会把纯本地的那半边一起拖停。」调度间隔「MUST 大于下游的 staleMinutes（5 分钟）」、建议 `0 0/10 * * * ?`。汇总那条另记「**返回的 skipped 长期非 0 是预期，不代表本轮失败**」，混着「重算影响 0 行（下一轮自愈）」与「明细已 SUCCESS 但 `PAY_TXN_DETAIL` 里没有该 ORDER_NO」两类，后者「**永远不会自愈**、只打 WARN 等人工核对。因此调用方 MUST NOT 把 skipped > 0 记成 ERROR，区分两类只能看 pay-sign 侧日志措辞」。另：`compensateSignNotify` 与 `compensateTerminationNotify` 扫的表不同、「两者**不可互相替代**，MUST 各自调度」（`:283`）。
- **判据**｜`rpc` · `CollectPayClient.registerAppPayOrder`（`rpc/src/main/java/com/chinasofti/huateng/rpc/collectpay/CollectPayClient.java:91~97`）：「**幂等键是 `orderNo`**：对端按订单号回查，已登记过就直接返成功，因此本方法可被补偿任务无限次重放。这一点是「先落本地 PENDING、提交后再同步」那套 outbox 能成立的前提，**NEVER** 让对端改成「重复即报错」。」返回 `RpcOutcome`：「`BizRejected` 重推一万次也不会成功、MUST 一次即终态 + 落 ERROR；只有 `Unreachable` 才该进补偿队列。」
- **判据**｜`rpc` · `CollectPayClient.queryAppPayOrderResult`（`.../collectpay/CollectPayClient.java:128~132`）：「**本方法在网络失败时 MUST 抛异常、NEVER 返回「查不到」**：`found=false` 是对端答复的业务事实（这张单没登记进 APP 订单表，乘客根本付不了），调用方会据此落 ERROR 或补登记；而连不上时我方对支付状态一无所知，若也返回 `found=false`，一次抖动就会被误判成「单据丢失」。两者的处置方向相反，因此 **NEVER** 在这里 catch 成空对象。」对应 DTO 侧同款判据在 `model/src/main/java/com/chinasofti/huateng/model/collectpay/AppPayOrderResultRespDTO.java:6~13`（并补「判成功 MUST 严格等于 `1`，**NEVER** 写成「非 2 即成功」」、「`0` 的措辞在 collect-pay 侧是矛盾的，NEVER 按字面理解」）。
- **陷阱**｜`rpc` · `GateTxnPayClient.countTransList` / `parseCount`（`rpc/src/main/java/com/chinasofti/huateng/rpc/pay/GateTxnPayClient.java:99~143`）：「**这是本 Client 里唯一一个「对端返裸标量、既没有 DTO 也没有 retCode」的方法**：gate-txn-pay 侧 `/ci/gateTxnPay/app/countTransList` 直接把 int 写进响应体。这个隐式契约没有编译期保护 —— 对端哪天改成返 JSON、或套一层 `CommonResult`，本端只会在运行时炸。**因此这里 MUST 自己把「对端到底返了什么」带进异常消息**」；「**NEVER 改成 catch 住返 0**：那会把「对端契约变了」伪装成「该用户没有交易记录」，分页总数恒为 0、APP 列表永远只剩第一页，且日志里一条错都没有。抛出去、由调用方兜成 9001 才是对的。」解析只容忍首尾空白与被引号包起来的数字，其余抛异常并带响应体前 200 字符；异常文案本身带 `MUST 先确认 service.gateTxnPay.url 与对端存活`（`:128`）与 `MUST 同批改本方法与 ticket-server、trans-query-server 两个调用点`（`:140`）。
- **判据**｜`rpc` · `GateTxnPayClient.recoverOfflineFare` / `pushMetroTransfer`（`.../pay/GateTxnPayClient.java:196~223`）：两个 web-admin Quartz 入口 ——「**同步跑完一轮才返回**，因此 `sys_job` 的「禁止并发」才有意义 —— 对端改成异步受理即返回时，禁并发会失效」；「对端**恒返 `retCode=0000`**：本轮扫表异常属可自愈（下一分钟重入），结论只在 `retMsg` 里……**NEVER 为了让 `sys_job_log` 更「灵敏」而要求对端把可自愈错误返成非 0**」；两者语义一致、「**改一个 MUST 看齐另一个**」。`headers` 参数注释点明「不传就在调度日志里断链（`ProxyWebClient` 不自动注入 trace 头）」。
- **判据**｜`rpc` · `GateTxnPayClient.convergeDebitStatusForSupplement`（`.../pay/GateTxnPayClient.java:226~248`）：「**与上面两个 `/internal/**` 方法语义不同：这条不是补偿批处理、对端也不恒返 `0000`。**调用方 MUST 按 `retCode` + `converged` + `debitStatus` 三者分支（已结清 / 重复支付待退款 / 需人工核对），判据写在 `GateTxnPayDebitConvergeRespDTO` 的类注释里，**NEVER 只看 `retCode`**。」；「**本方法不吞异常**：网络不可达时让底层异常原样抛给调用方，等价于 `RpcOutcome.Unreachable` —— 调用方接住后 MUST 不改本地状态、留补偿任务重入。**NEVER 在这里 catch 后返 null 或造一个假的业务码**，那会让「该重试」变成「已终态」」；「将来若这条也要加内部令牌，MUST 新增重载、**NEVER 改本方法签名**（`rpc` 版本号锁死、被 21 个模块引用）」。
- **判据**｜`model` · `GateTxnPayDebitConvergeRespDTO`（`model/src/main/java/com/chinasofti/huateng/model/pay/GateTxnPayDebitConvergeRespDTO.java:11~57`）：上一条点名的那份判据 ——「**NEVER 只看 `retCode` 就判成功**」；`converged=false` + 已 SUCCESS 时「本单属**重复支付、待退款**，调用方 MUST 标失败并留「重复支付待退款」，**NEVER 当成幂等成功悄悄跳过**」；业务拒绝分支「MUST 一次即终态并留人工核对线索」；「**NEVER 把它和上面第三支（业务拒绝）合并处理** —— 前者该重试、后者重试一万次也不会变」；`retMsg`「仅用于日志与工单，**NEVER 用它做分支判断**」；`converged` 「调用方 MUST 显式判 `Boolean.TRUE.equals(converged)`」；`debitStatus`「对端 MUST 始终回填它，**NEVER 因为「本次没改行」就省略**」。请求侧 `GateTxnPayDebitConvergeReqDTO:10~47` 记「**NEVER 在 face-pay 侧加回任何对 `GATE_TXN_PAY` 的写语句**」、`origTxnDate`「**MUST 传**（月分区表要分区裁剪）……**NEVER 用当天日期替代**」、以及「要统一成不含 `FAIL` 属业务规则变更，MUST 先与业务确认「FAIL 单能否补款」，NEVER 自行收窄」。
- **契约**｜`rpc` · 「先判 retCode / resultCode 再用字段」这一族（同形不合并）：`GateTxnPayClient.hasUnsettledOrderByCard`（`.../pay/GateTxnPayClient.java:69~70`）「下游在查询未真正执行时会把 hasUnsettled 置为 true，**NEVER** 把它当成「已结清」」；`GateTxnPayClient.syncDebitStatus`（`:81~82`）「非 0000 表示 `GATE_TXN_PAY` 没收敛，NEVER 因为「没抛异常」就认为对齐了」；`GateTxnPayClient.requestUserAccInfo`（`:183~184`）「查询未执行时两数为 0，**NEVER** 当成「无欠费」」；`AlipayPaySignClient`（`rpc/src/main/java/com/chinasofti/huateng/rpc/alipay/paysign/AlipayPaySignClient.java:291~295`）「判定「该卡欠费是否结清」MUST 同时问本接口与 `GateTxnPayClient.hasUnsettledOrderByCard`」；`AccountClient.queryByThirdUserId`（`rpc/src/main/java/com/chinasofti/huateng/rpc/account/AccountClient.java:126`）「未找到时返回 `retCode=8004`，调用方 MUST 判 retCode 而不是只判字段空」；`PaySignClient.processTermination`（`.../paySign/PaySignClient.java:234~235`）「本方法不抛业务异常，失败体现在 resultCode 上；响应为 null 说明 HTTP 层就没通」；`BlacklistClient`（`rpc/src/main/java/com/chinasofti/huateng/rpc/blacklist/BlacklistClient.java:80~89`）「**只读，下游 NEVER 删除任何黑名单记录**」+ 「body **MUST NOT 传 null**」+ 「调用方 MUST 检查返回的 resultCode」；`FacePayClient.requestPayOrder`（`rpc/src/main/java/com/chinasofti/huateng/rpc/facepay/FacePayClient.java:33`）「只生成补款单并返回补款单号，不发起支付。调用方 MUST 检查 retCode」。
- **契约**｜`rpc` · `AlipayPaySignClient`（`.../alipay/paysign/AlipayPaySignClient.java:154~155`）：「服务端单次只处理一批（上限 200 条），调用方 **MUST** 反复调用直到 `scanned` 为 0 才算排空；**MUST** 先检查 `resultCode`（本方法不抛业务异常），返回 null 说明 HTTP 层就没通。」
- **判据**｜`rpc` · `CardPoolClient`（`rpc/src/main/java/com/chinasofti/huateng/rpc/cardpool/CardPoolClient.java:22~27`）：「三个方法都返回结果对象而不是 `null` / `false`：调用方 MUST 按 `CardPoolOutcome` 分流，把「池空」「参数或票种被拒」「远端不可达」区别对待，NEVER 再统一报成「无可分配逻辑卡号」—— 那会把配置错误和网络故障都误报成卡池耗尽。」
- **判据**｜`rpc` · `CardPoolClient.runMaintenance`（`.../cardpool/CardPoolClient.java:123~145`）：「服务端只做**受理**……因此本方法返回成功**不代表这一轮已经导完**，执行结果 MUST 看 card-pool-server 日志与 `/card-pools/summary`」；「`data.accepted=false` 表示上一轮尚未结束，本轮被丢弃。这是**正常的限流行为**，不是失败……调用方 NEVER 因此抛异常告警，否则前台调度日志会长期一片红。」`release` 的 `businessId` 标「MUST 与创建预占时使用的值一致，否则无法命中预占记录」（`model/.../cardpool/CardPoolReservationActionReqDTO.java:17`）。
- **判据**｜`rpc` · recon 三件套：`ReconExportClient`（`rpc/src/main/java/com/chinasofti/huateng/rpc/recon/ReconExportClient.java:37~38`）「返回 `accepted=true` 只代表源服务**受理**，抽取仍在对端后台跑。收齐判定 MUST 以 `RECON_BATCH_SOURCE.STATUS` 为准，NEVER 把受理当成完成」（DTO 侧同一条在 `model/.../recon/ReconExportRespDTO.java:8~9`）；`ReconClient.sourceComplete`（`rpc/.../recon/ReconClient.java:88`）「`retCode=0000` 表示对账完成；其它值表示失败，调用方 MUST 据此抛异常」；`ReconClient.uploadPart`（`:123`）「哈希不同拒收。因此重试 MUST 用同一 partNo 与同一文件内容」（DTO 侧 `model/.../recon/ReconPartReceiptDTO.java:9~10`：「源服务重试 MUST 复用相同分片号，NEVER 换号重传 —— 换号会让同一批数据在最终文件里出现两次」）；`ReconSourceCompleteReqDTO:10`「并告警，NEVER 放行 —— 分片少一片，最终对账文件就少几十万条，且文件本身看不出缺失」。
- **契约**｜`rpc` · `ReconPartSink` / `ReconPartUploader`（`rpc/src/main/java/com/chinasofti/huateng/rpc/recon/ReconPartSink.java:26~37`、`ReconPartUploader.java:49`）：「用法固定为 try-with-resources，且成功路径 MUST 显式调用 `commit()`」，「没走到 `commit()` 就 `close()`（抛异常、提前 return）时，本类会向 recon-server **声明失败**而不是静默退出 —— 否则该源永远停在 EXPORTING，整批对账挂死等不到收齐」；「NEVER 把全部记录缓存到 `List` 再一次性写：400 万条明细即使每条 100 字节也是 400MB 堆」；写行「MUST 由 `ReconRecord.line(Object...)` 生成」（`ReconPartSink.java:92`）。
- **决策**｜`rpc` · `ReconClient`（`.../recon/ReconClient.java:27~36`）：分片上传走 `application/octet-stream` **流式**发送，「NEVER 改成把文件读成 `byte[]` 再发：单片上限 128MB，读成数组等于每片一次 128MB 的堆分配，并发几片就 OOM。也 NEVER 改成 JSON —— Base64 会把体积放大 33%，且 400 万条明细无法整体入内存」；「本客户端的方法**失败即抛异常**，不返回 `false`……NEVER 吞异常继续写下一片，那会产生分片号空洞，最终在生成阶段报「分片序号不连续」而整批失败」。分片粒度默认 20 万条 / 64MB，「**NEVER 把上限调到百万级**：单片越大，重传成本越高，且服务端 `recon.max-part-bytes`（默认 128MB）会直接拒收」（`ReconPartUploader.java:13`）。
- **契约**｜`rpc` · `ReconClient` / `ReconExportClient` 的鉴权降级（`.../recon/ReconClient.java:37~40`、`ReconExportClient.java:40~42`）：「**【开发测试阶段：不再发送 `X-Recon-Token`】**用户 2026-09-11 要求「删除令牌要求，不用令牌了，当前处于开发测试阶段」，recon-server 侧已不校验该头，故这里连带去掉发送，顺带消除 `InternalMicroHttp` / `FirstFilter` 把请求头 Map 打进日志时的令牌明文泄漏。**恢复鉴权时两端 MUST 同时改回。**」
- **契约**｜`rpc` · `TransQueryClient`（`rpc/src/main/java/com/chinasofti/huateng/rpc/transquery/TransQueryClient.java:22~36`）：三个方法与 `TicketClient` 的同名方法「**路径、请求 DTO、应答 DTO 全部逐字相同**，唯一差别是 baseUrl 指向新服务」；另起 Client 而非改 `TicketClient` 的 baseUrl 是因为「ticket-server 上还有乘车码状态机、自助补站等一批接口没迁走，改 baseUrl 会把它们一起指错」；「**ticket-server 上那三个同路径端点仍在、未删**（过渡期双活）……两边同时在跑期间 MUST 保证 `app.trans.*` 五个键取值一致，否则同一笔订单在新旧链路会返回不同商户号」；支付宝行程与日票乘车记录「**故意不在这里**……仍 MUST 走 TicketClient」。
- **契约**｜`rpc` · `AccountClient.syncSignDisplayAccount`（`rpc/.../account/AccountClient.java:147~153`）：「**调用方 MUST 把它当「允许失败」处理**：本方法只回写一列展示值……因此调用点 MUST catch 全部异常只记日志，**NEVER 让它把已提交的签约结果翻成失败**。」同时「按 AGENTS.md §5.2，调用方 **MUST 判 retCode，NEVER 假定「没抛异常就是写成功」**」——两条并存、方向不同，别当矛盾。
- **契约**｜`model` · 补偿类应答 DTO：`CompensateNotifyRespDTO`（`model/src/main/java/com/chinasofti/huateng/model/paysign/CompensateNotifyRespDTO.java:8~15`）「**重发是异步的，submitted 只代表「提交成功」，NEVER 用它判断通知是否真的送达**」+「此前两份并存、靠「改动 MUST 两侧同步」的口头约定维持，而 rpc 反序列化对缺字段是静默的 —— **NEVER 再在业务模块新建同形副本**」；`ResendSignNotifyReqDTO:8`「NEVER 为了重发一条而去打批量补偿接口 —— 那会把所有符合扫描条件的历史流水一起发给 APP」；`ResendSignNotifyRespDTO:11`「`resultCode=0000` 只代表接口本身处理完成，通知是否送达 MUST 看 `notified`」。

### 附.4 ProxyWebClient 与路由

- **陷阱**｜`resource/micro/web` · `ProxyWebClient`（第二个构造器，`resource/micro/web/src/main/java/com/chinasofti/huateng/micro/web/client/ProxyWebClient.java:96~106`）：「存在的理由：`getResponseTimeout()` 是在**父类构造期**被 `getInitOkHttpClient` 调用的，此时子类的实例字段还没赋值。于是「子类覆写 `getResponseTimeout()` 返回自己 `@Value` 注入的超时字段」这个看起来最自然的写法，**实际读到的恒为 0 / null**，且编译与单测都发现不了 —— 超时值必须在 `super(...)` 的实参里传进来才来得及。需要非默认超时的子类 MUST 用本构造器，**NEVER 靠覆写 `getResponseTimeout()` 去读实例字段**。」`responseTimeout` 传 null 时退回默认 10 秒（`:105`）。
- **陷阱**｜`resource/micro/web` · `ProxyWebClient.getResponseTimeout`（`.../client/ProxyWebClient.java:114~119`）：「重写实现里 **NEVER 引用子类的实例字段**（含 `@Value` 注入的字段）：本方法在父类构造期就被调用，那时子类字段尚未赋值。需要按配置定超时的走带 `responseTimeout` 参数的构造器。」
- **契约**｜`resource/micro/web` · `ProxyWebClient` 连接与超时常量（`.../client/ProxyWebClient.java:33~38`，**无注释、按代码读**）：`MAX_CONNECTIONS=500`、`PENDING_ACQUIRE_MAX_COUNT=1000`、`MAX_IDLE_TIME=20s`、`MAX_LIFE_TIME=5min`、`EVICT_IN_BACKGROUND=30s`、`DEFAULT_RESPONSE_TIMEOUT=10s`，连接超时 `CONNECT_TIMEOUT_MILLIS=3000`（`:51`），编解码 `maxInMemorySize=500MB`（`:77`）。这些值**注释里没有任何说明**，见文末缺口。
- **契约**｜`resource/micro/web` · `ProxyWebClient.addAuthorizationToHeader`（`.../client/ProxyWebClient.java:147~159`，**无注释、按代码读**）：只从 MDC 取 `authorization` 一个键并放进请求头。多个 rpc 侧注释都以此为前提，例如 `ParaClient.quartzScanFtpPara`（`rpc/src/main/java/com/chinasofti/huateng/rpc/para/ParaClient.java:63~64`）：「⚠️ `ProxyWebClient` 不会自动注入任何 trace 头（它只从 MDC 取 authorization），所以这个重载是必需的，不能指望框架兜底」；`ReconExportClient`（`rpc/.../recon/ReconExportClient.java:44~52`）「`ProxyWebClient.addAuthorizationToHeader` **只转发 `authorization` 一个头**，web-admin 的 `QuartzTraceUtils.traceHeaders` 发出的 `X-Vlogs-Capture` 到 recon-server 这一跳就断了」。
- **判据**｜`rpc` · 默认服务名规则：各 Client 构造器的 `@Value` 默认值就是「没配 `service.*.url` 时的兜底服务名」，且集群里解析不到。样例 `GateTxnPayClient`（`rpc/.../pay/GateTxnPayClient.java:33` → `gate-txn-pay-service`）、`ReconClient`（`rpc/.../recon/ReconClient.java:57` → `recon-server-service`）、`TransQueryClient`（`rpc/.../transquery/TransQueryClient.java:44` → `trans-query-service`）、`CardPoolClient`（`rpc/.../cardpool/CardPoolClient.java:45` → `card-pool-service`）。把这条写成显式约束的只有 `EnableRpcTransQuery`（`rpc/src/main/java/com/chinasofti/huateng/rpc/EnableRpcTransQuery.java:12~13`）：「加了本注解 MUST 同批在 `application.properties` 补 `service.transQuery.url`，否则 baseUrl 退化成默认服务名 `trans-query-service`、集群里 DNS 解析不到。」同注解还写明与 `@EnableRpcTicket`「**不互斥、通常成对出现**」。
- **陷阱**｜`rpc` · `AlipayAccountClient`（`rpc/src/main/java/com/chinasofti/huateng/rpc/alipay/account/AlipayAccountClient.java:22~29`）：「**配置键用嵌套默认值 `service.alipayAccount.url` → `service.account.url`，NEVER 退回只读后者。**历史实现只读 `service.account.url`，而该键在不同模块含义不同：`fep-alipay-server` 把它配成 alipay-account 地址，但 `ticket-server` 的同名键指向**真 account-server**。于是 ticket-server 里 `CardDataHandler` 按 thirdUserId 查支付宝用户时一直打错目标，且 ticket-server 早已配好的 `service.alipayAccount.url` 没有任何读取方（2026-09-11 定位）。」现构造器是 `${service.alipayAccount.url:${service.account.url:http://127.0.0.1:9106}}`（`:36`）。
- **决策**｜`rpc` · `ReconExportClient` 的动态路由（`rpc/.../recon/ReconExportClient.java:17~35`）：「与其他 Client 不同，本类**不绑定单一目标地址**：三个源服务地址各不相同，调用时传入完整 URL。实现方式复用 `rpc` 模块既有的「动态路由」机制 —— 即 `URLDynamicRouter` 的做法：**把 baseUrl 传空串**，每次调用自己给出绝对 URL。」；「**baseUrl MUST 保持空串，NEVER 填 `service.recon.self-url` 之类的占位地址。**」两层理由：①`InternalMicroHttp.logRequest()` 打的 URL 是无条件的 `joinUrl(getBaseUrl(), url)`，占位 baseUrl 会让 INFO 日志出现 `url=http://127.0.0.1:9112/http://gate-txn-pay-server-...:30019/internal/recon/export` 这种双份地址（2026-09-11 端到端实跑实录），把人往「打到自己身上」的方向误导；②「真正发请求时能否忽略 baseUrl，取决于 `DefaultUriBuilderFactory` 的实现细节（绝对 URL 带 host 时才丢弃 baseUri）。依赖这个细节属于**隐式契约**，baseUrl 留空则不论框架实现如何都只有绝对 URL 一个来源。」配套：`sourceBaseUrl`「MUST 是绝对地址」（`:95`），非绝对地址「NEVER 放过去让 WebClient 把它当相对路径拼到空 baseUrl 上 —— 那会得到一个没有 host 的请求」（`:135`）。
- **决策**｜`rpc` · `ReconExportClient` 固定带 `X-Vlogs-Capture: 1`（`rpc/.../recon/ReconExportClient.java:44~54`）：「**下发时固定带 `X-Vlogs-Capture: 1`（2026-09-14 新增，NEVER 删）。**三个源服务的 INFO 日志能不能进 VictoriaLogs，取决于它们 MDC 里有没有 `x-vlogs-capture=1`……少了本行，前台调度日志按 traceId 反查只能看到 web-admin + recon-server 两段，三个源的 INFO **一条都查不到**（2026-09-14 实测）。」写死 `1` 而非「有则透传」的理由是「本端点**只被日终对账下发调用、每天每源一次**，量级可忽略」；「**NEVER 把这个写法照搬到高频业务端点** —— 那等于把该链路的全部 INFO 灌进日志库。」
- **陷阱**｜`rpc` · `ParaClient.requestLineCodeList` / `requestLineStationCodeVersion`（`rpc/.../para/ParaClient.java:76~84`、`115~118`）：「`RequestLineCodeListReqDTO` 是**零字段类**，直接 `bodyValue(request)` 会被 WebClient 判定为无可用编码器并抛 `UnsupportedMediaTypeException: Content type 'application/json' not supported for bodyType=...`，请求根本发不出去、响应退化成全局异常处理器的 UUID retCode。因此……改送空 JSON 对象。入参保留在签名上只为兼容调用方，**NEVER 改回 `postJsonAndGetResponse(url, request)`**。」`RequestLineStationCodeVersionReqDTO` 同因、「也是零字段类，**MUST** 送空 JSON 对象」。

### 附.5 观测切面与 tracing

- **决策 + 陷阱**｜`resource/micro/web` · `MapperAspectToTrace.around`（`resource/micro/web/src/main/java/com/chinasofti/huateng/micro/monitor/trace/MapperAspectToTrace.java:32~48`）：「观测 mapper 调用耗时，并**原样抛出**业务异常。**NEVER 改回 `observation.observe(Supplier)` 那种写法**（2026-09-14 修，ADR-D53）：`observe` 收的是 `Supplier`、不能抛受检异常，于是原实现在 lambda 里 `throw new RuntimeException(e)` 把真实异常包了一层。后果是**调用方按类型 catch 一律失效** —— 本项目的幂等兜底全靠 `catch (DataIntegrityViolationException / DuplicateKeyException)`（AGENTS.md §5.1「唯一索引 + DuplicateKeyException 兜底」），包一层后那些 catch 永远进不去，异常直接冒到全局处理器变成 500。而且**只在打开 tracing 的模块上出现**（`shouldSkipAopTraceLogic()` 为 false 才走这段），因此同一份业务代码在 account-server 上正常、在 card-pool-server 上就崩 —— 2026-09-14 并发开户实测：卡池 `reserve` 明明写了 `catch (DataIntegrityViolationException)` 去回查兄弟请求的预占，却因本条被跳过，并发同 `businessId` 的第二条请求直接 500、APP 收到「暂无卡数据资源」。」修法：「现在改成手工 `start / openScope / error / stop`：观测语义与 `observe` 等价（scope 内 MDC 与 traceId 照旧），但异常路径不再新建包装异常。」代码形态见 `:61~73`（`start()` → try-with-resources `openScope()` → `catch (Throwable e) { observation.error(e); log.error(...); throw e; }` → `finally observation.stop()`）。
- **决策 + 陷阱**｜`resource/micro/web` · `ServiceAspectToTrace.around`（`resource/micro/web/src/main/java/com/chinasofti/huateng/micro/monitor/trace/ServiceAspectToTrace.java:32~40`）：与上一条「同一处修复（2026-09-14，ADR-D53）」，原实现同样 `observe(Supplier)` + lambda 内 `throw new RuntimeException(e)`，「**调用方按类型 catch 一律失效**……且只在打开 tracing 的模块上出现。**NEVER 改回 observe 那种写法。**」切点是 `within(@org.springframework.stereotype.Service *)`（`:28`），mapper 那条是 `within(@org.apache.ibatis.annotations.Mapper *)`（`MapperAspectToTrace.java:24`）。
- **契约**｜`resource/micro/web` · tracing 开关默认值（`resource/micro/web/src/main/resources/web.properties:78~80`）：`management.tracing.enabled=false`、`management.tracing.propagation.type=W3C,B3,B3_MULTI`、`management.tracing.sampling.probability=1.0`。**「三行成组」这条要求在 properties 注释里没有**（只有 OTLP 那段），见文末缺口。
- **决策**｜`resource/micro/web` · OTLP 六行为何注释掉（`resource/micro/web/src/main/resources/web.properties:81~89`）：「以下 OTLP 上报配置全部注释掉，**勿删除**（2026-09-08）：1) endpoint 指向的 `collector-istio-traces-service.opentelemetry` 在集群里不存在，任何打开 `management.tracing.enabled=true` 的服务都会被 `OtlpAutoConfiguration` 建出 exporter，导出线程每批抛 `UnknownHostException` 刷日志（pay-sign 已实测）。日志采集本项目自建，不依赖 OTel collector。2) 注释掉 endpoint 后 `OtlpTracingConnectionDetails` 的 `@ConditionalOnProperty` 不成立，全局不再创建 SpanExporter，Tracer 与 MDC 的 traceId/spanId 不受影响。要恢复上报：填真实 collector 地址即可。3) 下面 batch/compression/connect-timeout 六行的键名在 Spring Boot 3.2.6 里根本不存在（真实前缀是 `management.otlp.tracing.*`，且只有 endpoint / timeout / compression / headers 四个键），**从来没生效过，恢复上报时勿照抄**。」
- **陷阱**｜`rpc` · 「NEVER 自造 `traceId` 请求头」（5 处同源注释，不合并）：`PaySignClient.processTermination(request, headers)`（`rpc/.../paySign/PaySignClient.java:244~248`）「headers 传 `traceparent`（W3C 格式 `00-<32位hex>-<16位hex>-00`）即可，pay-sign 侧由 Spring Boot tracing 自动解析并写入 MDC 的 traceId / spanId。**NEVER 传自定义的 traceId 头**：pay-sign 的 `FirstFilter` 会把请求头全部转小写后放入 MDC，落进去的 key 是 `traceid`，与日志 pattern 取的 `traceId` 不匹配，输出恒为空」；`ParaClient.quartzScanFtpPara(headers)`（`rpc/.../para/ParaClient.java:56~61`）同款并点明「`traceId` 会变成 `traceid`，与 log4j2 的 `%X{traceId}` 大小写不匹配，等于白传」；`F2FClient.post`（`rpc/.../f2f/F2FClient.java:88~92`）「与 Micrometer 写入的 traceId 抢同一个键」；`AlipayPaySignClient`（`rpc/.../alipay/paysign/AlipayPaySignClient.java:166~170`）；`CardPoolClient.runMaintenance`（`rpc/.../cardpool/CardPoolClient.java:135~138`）「与 log4j2 的 `%X{traceId}` 大小写对不上，等于白传」。`AccountClient`（`rpc/.../account/AccountClient.java:229~233`、`261`）另记「不传……否则 traceId 断链、前台「执行日志」查不到下游」。
- **缺口**｜`resource/micro/web` · `FirstFilter.doFilter`（`resource/micro/web/src/main/java/com/chinasofti/huateng/micro/web/filter/FirstFilter.java:88~92`）：把请求头名 `toLowerCase()` 后同时放进 `headers` Map 与 MDC 的那三行**一条注释都没有**。上面那 5 处 rpc 注释全部以它为前提，但它自己不解释、也不警示。
- **决策 + 陷阱**｜`resource/micro/web` · `CustomLoggingConfiguration.onApplicationEvent`（`resource/micro/web/src/main/java/com/chinasofti/huateng/log4j2/CustomLoggingConfiguration.java:54~62`）：「**MUST 传 ClassLoader。**`Configurator.initialize(name, configLocation)` 那个重载不传 ClassLoader，在 Spring Boot fat jar 下**解析不到 micro 嵌套 jar 里的自定义 appender**（`@Plugin(name="VictoriaLogs")` 等）：配置本身能重载成功、RollingFile 也生效，但自定义 appender 静默缺失，表现为「已注入 `VLOGS_URL` 却零上报、连 `vlogs-sender` 线程都没有」。2026-09-08 在 para-server 实测复现（env 已注入，vlogs 线程数 0）。本类的 ClassLoader 即 `LaunchedURLClassLoader`，能同时看到 micro 嵌套 jar 与业务类。**NEVER** 退回不带 ClassLoader 的重载 —— 本文件 `status="off"`，log4j2 自己的 "Unable to locate plugin" 会被吞掉，退回后只能靠「上报为 0」反推，极难定位。」
- **决策**｜`resource/micro/web` · `VictoriaLogsAppender`（`resource/micro/web/src/main/java/com/chinasofti/huateng/log4j2/VictoriaLogsAppender.java:29~50`）：四条「设计约束（与本项目已发生的生产事故直接相关，改动前务必先读）」——①「业务线程只做「序列化 + 入有界队列」，绝不在业务线程上发 HTTP。全服务开了 `spring.threads.virtual.enabled=true`，在虚拟线程上做阻塞 IO 会 pin 载体线程」；②「发送线程是**平台线程**（`Thread.ofPlatform()`），阻塞它不影响虚拟线程调度」；③「队列满时丢弃并计数，**绝不阻塞业务线程** —— 日志管道不能反噬业务」；④「内部异常只走 log4j2 StatusLogger（`LOGGER`），绝不用 slf4j，避免日志递归。注意各模块 log4j2 配置多为 `status="off"`，这些自述日志默认看不到；判断是否启用请看 `vlogs-sender` 线程是否存在」。另「本类放在 micro/web 只是「能力下放」：log4j2 仅在配置里出现 `<VictoriaLogs>` 时才实例化插件，光有类不会起线程、不会发请求，因此对未配置的模块零影响」。默认插入路径注释点明「stream 字段必须低基数，traceId 只能进正文」（`:56`）。

### 附.6 自研持久层与 Druid

- **契约**｜`resource/micro/sql-datasource` · Druid 池级超时兜底（`resource/micro/sql-datasource/src/main/resources/sql.properties:43~46`）：「池级查询超时兜底：未显式 `setQueryTimeout` 的语句默认 30s 超时，与 `oracle.jdbc.ReadTimeout`（10s socket 读）形成双层保护。业务代码显式 `setQueryTimeout` 的（如对账抽取）会覆盖此值。」对应键 `spring.datasource.druid.query-timeout=30`。
- **决策**｜`resource/micro/sql-datasource` · `remove-abandoned-timeout` 从 30 分钟降到 5 分钟（`sql.properties:47~51`）：「虚拟线程环境下 30 分钟的 abandoned 超时太长，pin 满载体线程前连接不会被回收。降至 5 分钟，配合 `ReadTimeout`/`query-timeout` 在更早层就切断。长耗时批处理（对账抽取等）显式跑在平台线程池上，不受此限制。」
- **契约**｜`resource/micro/sql-datasource` · WallFilter 现状（`sql.properties:40~42`，**无注释、按配置读**）：`spring.datasource.druid.filter.wall.enabled=true`，只显式放开 `delete-allow` 与 `drop-table-allow` 两项。**「SQL 正文里 NEVER 写注释」与「NEVER 写 `where 1 = 1` + 全可选 `<if>`」这两条规则在 properties 里一个字都没有**（只在 AGENTS.md §5.1），见文末缺口。
- **契约**｜`resource/micro/sql-datasource` · 达梦环境关掉 WallFilter（`resource/micro/sql-datasource/src/main/resources/dm.properties:5`，**无注释、按配置读**）：`spring.datasource.druid.filter.wall.enabled=false`。这一行是「WallFilter 类缺陷只在 Oracle 环境暴露」的成因，但**该文件里没有任何说明**。
- **契约**｜`resource/micro/sql-datasource` · Oracle 数据源 URL 模板（`resource/micro/sql-datasource/src/main/resources/oracle.properties:1`）：`jdbc:oracle:thin:@${other.sql.host}/${other.sql.database}`。数据源三要素统一走 `other.sql.*` 占位（`:2~3`），驱动固定 `oracle.jdbc.OracleDriver`（`:4`）。
- **契约**｜`resource/micro/mybatis-adaptor` · mapper 扫描路径（`resource/micro/mybatis-adaptor/src/main/resources/persistent.properties:1`）：`mybatis.mapper-locations=classpath*:/mappers/*.xml`（第二行是注释掉的 `logging.level` 样例）。**注意它是构件默认值、不是实际生效值**：抽样 `pay-sign-server` / `account-server` / `recon-server` 的 `application.properties:44 / :41 / :43` 都用 `classpath*:mapper/*.xml` 覆盖了它，磁盘上的目录也叫 `mapper`（单数）。因此本文档 §3.2 写的 `resources/mapper/*.xml` 描述的是业务模块现状，与构件默认值 `mappers`（复数）**不一致但不冲突**；**排查「mapper 没被加载」MUST 先看业务模块自己有没有覆盖这个键**。
- **缺口**｜`resource/micro/sql-datasource` / `mybatis-adaptor` 的 Java 侧注释：9 + 3 个类里**只有 7 行注释**，且全是流程性行注释（`GenericDoubleDatasourceCondition.java:16~44` 的「1. 先判断全局开关 …… 4. 所有条件满足则返回 true」六行，`DynamicMybatisConfiguration.java:64` 的 `// PageInterceptor` 一行）。`SqlAudit`、`AutoSelectDbType`、`DbPropertyFactory`、`MoreDruidDataSourceConfig`、`DruidDataSourceWrapper1`、`SqlConfiguration`、`DefaultMybatisConfiguration`、`EnableDefaultMybatisAutoConfig` 等 **没有任何类级 Javadoc**。「自研持久层的使用约定」在这两个子模块的源码注释里**不存在**，现有约定只在本文档 §3.2 与 AGENTS.md §5.1。

### 附.7 虚拟线程

- **契约 + 陷阱**｜`resource/micro/sql-datasource` · `oracle.jdbc.ReadTimeout=10000` 的存在理由（`resource/micro/sql-datasource/src/main/resources/oracle.properties:6~14`）：「虚拟线程 pinning 兜底：socket 读超时直接限制单次慢 SQL 的 pin 时长上限。ojdbc8 的 `PhysicalConnection` / `OracleStatement` 大量方法是 `synchronized`，JDK 21 未落地 JEP 491，虚拟线程在 `synchronized` 内阻塞会 pin 载体线程；一条慢 SQL 就等于把一个载体线程 pin 住同样长的时间。这里 10s 的 `ReadTimeout` 让 DB 响应超过 10s 的语句直接抛 `SocketTimeoutException`，pin 的载体线程立刻释放，不再拖到 `remove-abandoned-timeout`。**已在 2026-08-26 pay-sign-server 生产事故中观察到「对端 17ms 答完、本端 287s 才处理」的形态，根因就是载体线程被 pin 满后虚拟线程停止调度。**长耗时批量任务（对账抽取等）已显式切到 `newFixedThreadPool` 平台线程上跑，不受此限制。」
- **契约**｜`resource/micro/web` · 虚拟线程全局默认（`resource/micro/web/src/main/resources/web.properties:6`，**无注释、按配置读**）：`spring.threads.virtual.enabled=true`。这一行本身没有任何说明，它的后果只在 `oracle.properties`（上一条）与 `VictoriaLogsAppender` 类注释（下一条）里被解释。
- **决策**｜`resource/micro/web` · `VictoriaLogsAppender` 为什么用平台线程发送（`resource/micro/web/src/main/java/com/chinasofti/huateng/log4j2/VictoriaLogsAppender.java:38~41`）：「业务线程只做「序列化 + 入有界队列」，绝不在业务线程上发 HTTP。全服务开了 `spring.threads.virtual.enabled=true`，在虚拟线程上做阻塞 IO 会 pin 载体线程。」「发送线程是**平台线程**（`Thread.ofPlatform()`），阻塞它不影响虚拟线程调度。」
- **缺口**｜「取证 MUST 用 `jcmd Thread.dump_to_file -format=json` / `-Djdk.tracePinnedThreads=full` / JFR `jdk.VirtualThreadPinned`」与「载体线程池 `parallelism` 等于容器可见 CPU 数」这两条，在这三块的源码注释里**都没有**，只在 AGENTS.md §5.2。

### 附.8 历史遗留构件

- **缺口 / 决策**｜`resource/micro/rabbitmq-adaptor`：4 个 Java 类（`DefaultRabbitmqConfiguration`、`EnableDefaultRabbitmqAutoConfig`、`MqConsumersCheckFilter`、`SimpleProducer`）与 `mq.properties` 里**一条注释都没有**，因此「它是历史遗留、业务模块 NEVER 引用、为什么留着不删」这套结论在源码里**完全没有落笔**，只存在于 AGENTS.md §5.1 与本文档正文。
- **陷阱**｜`resource/micro/rabbitmq-adaptor` · `mq.properties:1~4`（**无注释、按配置读**）：默认值是一个**具体的内网地址** `spring.rabbitmq.host=130.251.235.235` 加 `guest/guest` 明文口令。按 AGENTS.md §5.2「敏感项 MUST 写成 `${ENV_VAR:}`」的口径这属违例；因为无人引用而未暴露，但**只要有一个模块加了 `spring-boot-starter-amqp` 与本 properties，它就会去连那个地址**。
- **契约**｜`rpc` · `TransQueryClient` 与 `TicketClient` 的双活过渡（`rpc/src/main/java/com/chinasofti/huateng/rpc/transquery/TransQueryClient.java:29~36`）：见 §附.3 该条。这是本次抽取里唯一一处「新旧实现并存」在注释中被显式登记的地方。
- **契约**｜`rpc` · `GateTxnPayClient` 中已迁走的接口（`rpc/.../pay/GateTxnPayClient.java` 的 `// ==================== IF8A-26 APP 在线补款下单 RPC ====================` 段）：「IF8A-26 补款下单已随补款功能迁入 face-pay-server（2026-09-15），调用方改用 `FacePayClient#requestPayOrder`。」——方法体已删、只留这条指路注释。

### 附.9 缺口清单（源码注释里没有、只在 AGENTS.md / 其它文档）

以下结论**在本次抽取范围的注释里查无实据**，引用时 MUST 回到 AGENTS.md 或对应文档，NEVER 说「代码注释里写了」：

1. 「`model` / `rpc` 版本号锁死为 2.0.0 / 2.0.1、被 21 个模块 pom 的 `<model.version>` / `<rpc.version>` 引用、升一次要改 21 个文件」——注释里只有 `RpcOutcome.java:29~30` 与 `PaySignClient.java:348`、`GateTxnPayClient.java:245` 三处提到「`rpc` 版本号锁死、被 21 个模块引用」，**`model` 侧一处都没有**，具体版本号与「21 个 pom 属性」的机制只在 AGENTS.md §7。
2. 「`mvn install` 只更新本机 `~/.m2`、对已在跑的 Pod 零影响，改公共构件 MUST 逐个重建镜像」——注释里唯一沾边的是 `GateTxnPayReqDTO.java:120`「调用方 MUST 用重新 install 过的 `model` 重建镜像」，只覆盖一个 DTO 的一个调用方；整条规则与 ADR-D53 那个「只重建了 card-pool-server、其余 6 个 tracing 模块线上仍是旧切面」的实例，注释里都没有。
3. 「tracing 开一个模块 MUST 三行成组（`enabled=true` + `sampling.probability=0` + 排除 `OtlpAutoConfiguration`）」「已打开的只有 7 个模块」——`web.properties` 只解释了 OTLP 六行为何注释掉，**三行成组与那份 7 模块名单都不在注释里**。
4. 「Druid WallFilter 的两条规则（SQL 正文禁注释、禁 `where 1 = 1` + 全可选 `<if>`）」「mapper XML 注释里禁两个连续减号」——`sql.properties` / `dm.properties` 只有开关本身，**三条规则与各自的事故记录全在 AGENTS.md §5.1**。
5. 「`FirstFilter` 把请求头小写后塞 MDC」——这条**在 `FirstFilter` 自己身上没有注释**，只由 5 处 rpc Client 注释间接记载（§附.5）。
6. 「`ProxyWebClient` 的连接池 / 超时常量为什么取这些值」「`addAuthorizationToHeader` 为什么只转发 `authorization`」——两处代码都**无注释**。
7. 「`CommonResult` 的 `retCode` 取值集合与 `0000` 语义」——`CommonResult` 自身注释里没有，只有 `RpcOutcome.SUCCESS_CODE` 一处。
8. 「自研 `sql-datasource` / `mybatis-adaptor` 的使用约定」——两个子模块共 12 个类、**7 行流程性注释**，无任何约定性说明。
9. 「`rabbitmq-adaptor` 为什么留着不删」——源码与 properties **零注释**。
10. 「虚拟线程 pin 的取证手段与载体线程池 parallelism」——只在 AGENTS.md §5.2。

### 附.10 墓碑注释清单（建议转为断言测试）

「墓碑」= 注释形态为「NEVER 回退 / NEVER 改回 / 那版口径已作废 / NEVER 加回」的条目，作用是拦住一次已被实证推翻的改动。这类条目最适合改成断言测试或静态检查 —— 现在它们只靠人读注释生效。**本清单不进正文，正文里对应条目已按三类归档。**

| 文件:行号 | 禁止的事 | 能否断言化 |
|---|---|---|
| `resource/micro/web/.../monitor/trace/MapperAspectToTrace.java:35` | NEVER 改回 `observation.observe(Supplier)` | **能**：单测让被观测方法抛 `DuplicateKeyException`，断言外层 catch 到的**就是原类型**（不是 `RuntimeException` 包装） |
| `resource/micro/web/.../monitor/trace/ServiceAspectToTrace.java:39` | 同上（service 切面） | **能**，同上 |
| `resource/micro/web/.../client/ProxyWebClient.java:103` | NEVER 靠覆写 `getResponseTimeout()` 去读实例字段 | 部分：可写单测「子类覆写返回 `@Value` 字段 ⇒ 构造期读到 0/null」，但断言的是反面行为，需要一个刻意写错的样例类 |
| `resource/micro/web/.../client/ProxyWebClient.java:117` | 重写实现里 NEVER 引用子类实例字段 | 同上 |
| `resource/micro/web/.../log4j2/CustomLoggingConfiguration.java:60` | NEVER 退回不带 ClassLoader 的 `Configurator.initialize` 重载 | **能**（静态检查）：grep 断言不出现两参数重载；行为断言需 fat jar 环境，成本高 |
| `model/.../app/ItpCommonRequest.java:14` | NEVER 因为共用本类就统一签名逻辑 | 否：属人工评审判据，无可断言的可观测量 |
| `model/.../app/ItpCommonRequest.java:96` | NEVER 把 `toString` 改成打印真实 `sign` | **能**：`assertThat(req.toString()).contains("sign='***'").doesNotContain(真值)`，`ItpCommonFormRequest:25` 同 |
| `model/.../ticket/enums/AdviceOptEnum.java:30` | 020 的方向反转 + 退出资金白名单，NEVER 回退 | **能**：断言 `FREE_UPDATE_020.isSupplementEntry()==true` 且 `isSupplementExit()==false` |
| `model/.../ticket/enums/AdviceOptEnum.java:36` | NEVER 把 005 改名成 `FREE_IN_20` 再让 `FREE_UPDATE` 指向 020 | **能**：断言 `FREE_UPDATE.getCode().equals("005")` 与 `FREE_UPDATE_020.getCode().equals("020")` |
| `model/.../ticket/enums/AdviceOptEnum.java:69` | 「020 = 更宽松版 005」那版口径已作废，NEVER 回退 | **能**：与上一条同一组断言 |
| `model/.../ticket/enums/AdviceOptEnum.java:148` | NEVER 因为「020 也叫免费更新」把它加回 `isSupplementExit` | **能**：断言 `SUPPLEMENT_EXIT_CODES == Set.of("005","006")` |
| `model/.../app/CardTypeMapping.java:20` | 日票聚合码 05，NEVER 走单值映射 | **能**：断言 `toIssueCardTypes("05")` 等于 `[0445,0446,0447,0448]` |
| `model/.../app/CardTypeMapping.java:36` | NFC 03/04，NEVER 退回单值映射 | **能**：断言 `toIssueCardTypes("03")` 与 `("04")` 都等于 `[0442,0443]` |
| `model/.../domain/SignStatus.java:25` | NEVER 改动状态常量字面量 | **能**：断言 `values()` 的 `name()` 集合恒等于 `{NOT_SIGNED,SIGNED,UNSIGNED,FAILED}` |
| `model/.../domain/TerminationStatus.java:32` | 同上（解约状态） | **能**，同上 |
| `model/.../domain/SignStatus.java:17` | NEVER 允许 `UNSIGNED -> SIGNED` | **能**：断言 `UNSIGNED.canTransitTo(SIGNED)==false` |
| `model/.../domain/TerminationStatus.java:18` | NEVER 允许 `FAILED -> SUCCESS` | **能**：断言 `FAILED.canTransitTo(SUCCESS)==false` |
| `model/.../domain/TerminationStatus.java:25` | NEVER 允许 `SUCCESS` 迁出 | **能**：断言 `SUCCESS.isTerminal()==true` |
| `model/.../domain/TerminationStatus.java:90` | NEVER 把 `PENDING`/`SCANNING` 纳入 `isNotifiable()` | **能**：逐值断言 `isNotifiable()` |
| `model/.../domain/SyncStatus.java:17` | `SUCCESS` 唯一终态，NEVER 允许迁出 | **能**：断言 `SUCCESS.isTerminal()` 且 `canTransitTo(*)` 全 false |
| `model/.../domain/SyncStatus.java:21` | NEVER 把 NULL 兜底成 `PENDING` | **能**：断言 `parseOrNull(null)` / `("")` / 未知值一律 `null`（三个枚举同型） |
| `model/.../domain/OutboxScan.java:23` | NEVER 建公共 outbox 表 | 否：架构判据，无代码可断言 |
| `model/.../domain/OutboxScan.java:28` | NEVER 往本类塞 Spring / MyBatis 依赖 | **能**（静态检查）：断言该类 import 集合不含 `org.springframework` / `org.apache.ibatis` |
| `model/.../domain/OutboxScan.java:16` | 单条失败 NEVER 中断整批 | **能**：让 `deliver` 对某一行抛异常，断言 `Result.scanned == success + failed` 且后续行仍被处理 |
| `model/.../ticket/enums/QRCodeStatusEnum.java:137` | FF NEVER 加进 `ALWAYS_ALLOWED_TARGETS` | **能**：断言任意状态 `canTransitTo(ENTRY_FAIL)` 只在 03/05/06/80/FF 之后为 true |
| `model/.../ticket/enums/QRCodeStatusEnum.java:116` | NEVER 把新功能的前置条件挂在状态 10 上 | 否：属设计约束 |
| `model/.../enums/IssueChannelCodeEnum.java:81` | NEVER 改成先 `fromCode` 再取实例属性 | **能**：断言 `thirdUserIdLength(null)==8` 且未知码 `==8`（不抛 NPE） |
| `model/.../app/TripDataDTO.java:14` | NEVER 把 `OVERTIME_AMOUNT` 映射到 `totalDiscount` | 部分：需在 ticket/gate-txn-pay 侧对装配逻辑断言，`model` 内无可断言点 |
| `model/.../recon/ReconRecord.java:6` | NEVER 自行 `String.join("|", ...)` 拼行 | **能**（静态检查）：全仓 grep 断言业务代码无 `String.join("|"`；另可断言 `line()` 对含 `|`/`\n` 的字段做净化 |
| `model/.../recon/ReconPartReceiptDTO.java:10` | 重试 NEVER 换分片号重传 | 否：跨服务行为，需集成测试 |
| `model/.../pay/GateTxnPayReqDTO.java:96` | 9 项进站快照 NEVER 在下游重算 | 部分：可断言 `copyCommonFields` 不写这 9 项之外的字段、也不做默认值兜底 |
| `model/.../pay/GateTxnPayDebitConvergeReqDTO.java:10` | NEVER 在 face-pay 侧加回对 `GATE_TXN_PAY` 的写语句 | **能**（静态检查）：grep 断言 `face-pay-server` 下无 `GATE_TXN_PAY` 的 UPDATE/INSERT |
| `model/.../pay/GateTxnPayListDTO.java:58` | NEVER 在查询侧按订单字段重算 | 否 |
| `model/.../app/UpdatePhoneReqDTO.java:7` | NEVER 改回 `newMsisdn` 作为规范字段 | **能**（静态检查）：断言字段名集合含 `newPhone`、不含 `newMsisdn` |
| `model/.../app/dailyticket/DailyTicketRefundCallbackReqDTO.java:6` | NEVER 改名或增删字段 | **能**：字段名快照断言（golden set） |
| `model/.../app/SyncPayAccountIdReqDTO.java:14` | NEVER 用它去改 `USER_ITP_REG_INFO.THIRD_PAY_ID` | 否：需在 account-server 侧断言 |
| `model/.../app/UserCancelReqDTO.java:8` | 注销时 NEVER 顺手删 `USER_PAY_CHANNEL` | 否：需在 account-server 侧断言 |
| `model/.../paysign/CompensateNotifyRespDTO.java:15` | NEVER 再在业务模块新建同形副本 | **能**（静态检查）：grep 断言业务模块下无同名类 |
| `model/.../collectpay/AppPayOrderRegisterReqDTO.java:15` | NEVER 只写 `TOTALPRICE`（算钱不读那一列） | 否：需在 collect-pay 侧断言落库列 |
| `model/.../collectpay/AppPayOrderResultRespDTO.java:13` | 判成功 NEVER 写成「非 2 即成功」 | 部分：可对解析工具方法断言「只有 `1` 为成功」 |
| `rpc/.../paySign/PaySignClient.java:348` | 兼容壳内 NEVER 重复一份解析逻辑 | **能**（静态检查）：断言该方法体只有一行委托 |
| `rpc/.../pay/GateTxnPayClient.java:110` | `countTransList` NEVER 改成 catch 住返 0 | **能**：喂非数字响应体，断言抛异常且消息含响应体片段 |
| `rpc/.../pay/GateTxnPayClient.java:150` | NEVER 退回 ticket-server 用 `QRCODE_TXN_DETAIL` 自算 IF8A-41 | 否：跨模块设计判据 |
| `rpc/.../pay/GateTxnPayClient.java:203` | NEVER 为让 `sys_job_log` 灵敏而要求对端把可自愈错误返非 0 | 否 |
| `rpc/.../pay/GateTxnPayClient.java:240` | `convergeDebitStatusForSupplement` NEVER catch 后返 null 或造假业务码 | **能**：mock 底层抛异常，断言异常原样透出 |
| `rpc/.../pay/GateTxnPayClient.java:245` | NEVER 改本方法签名（要加令牌就新增重载） | **能**（静态检查）：方法签名快照断言 |
| `rpc/.../para/ParaClient.java:83` | 零字段 DTO 场景 NEVER 改回 `postJsonAndGetResponse(url, request)` | **能**（静态检查）：断言这两个方法体传的是 `Collections.emptyMap()` |
| `rpc/.../recon/ReconClient.java:30` | 分片上传 NEVER 改成 `byte[]` 或 JSON | **能**（静态检查）：断言用 `BodyInserters.fromResource` 且 contentType 为 `application/octet-stream` |
| `rpc/.../recon/ReconExportClient.java:25` | baseUrl MUST 空串、NEVER 填占位地址 | **能**：断言 `getBaseUrl()` 为空串 |
| `rpc/.../recon/ReconExportClient.java:44` | 下发固定带 `X-Vlogs-Capture: 1`，NEVER 删 | **能**：断言出向 header 含该键值 |
| `rpc/.../recon/ReconExportClient.java:54` | NEVER 把「写死 capture=1」照搬到高频业务端点 | 否 |
| `rpc/.../recon/ReconPartUploader.java:13` | NEVER 把单片上限调到百万级 | **能**：断言默认 `maxPartRecords=200000` / `maxPartBytes=67108864` 且不超过服务端 128MB |
| `rpc/.../alipay/account/AlipayAccountClient.java:22` | NEVER 退回只读 `service.account.url` | **能**（静态检查）：断言 `@Value` 表达式为嵌套默认值形态 |
| `rpc/.../f2f/F2FClient.java:93` | 请求体 NEVER 改成空 Map（保持 `null`） | **能**（静态检查）：断言 `post(...)` 传 `null` |
| `rpc/.../paySign/PaySignClient.java:271`（及 `288`/`313`/`336`） | body MUST NOT 传 null（与上一条方向相反，NEVER 合并理解） | **能**：断言这四处传的是空 Map |
| `rpc/.../collectpay/CollectPayClient.java:93` | NEVER 让对端把 `orderNo` 幂等改成「重复即报错」 | 否：对端契约 |
| `rpc/.../collectpay/CollectPayClient.java:132` | 网络失败 NEVER catch 成空对象 / 返「查不到」 | **能**：mock 空响应，断言抛 `IllegalStateException` |
| `rpc/.../outcome/RpcOutcome.java:24` | 误判方向 NEVER 反过来（网络抖动当业务拒绝） | **能**：断言 `ofRetCode(null, ...)` 归 `BizRejected`、异常路径归 `Unreachable`（在各 Client 上逐个断言） |
| `rpc/.../outcome/RpcOutcome.java:26` | 本类型 NEVER 参与序列化 | **能**（静态检查）：断言 `model` 与各 `*RespDTO` 不引用它 |
| `rpc/.../recon/ReconClient.java:40`、`ReconExportClient.java:42` | 鉴权已降级，恢复时两端 MUST 同时改回 | 否：属待办提醒，不是断言对象（但 MUST 进上线核对清单） |

---

## 附：model / rpc / resource-micro 注释知识迁移（2026-09-16，阶段二·完整）

> **本节是「抽取 + 删除」闭环的终点。** 阶段一（上一节）只抽了 202 行；本次把 `model/**`（注释 4364 行）、
> `rpc/**`（1225 行）、`resource/micro/**`（230 行）里的**多行叙述型 / MUST-NEVER / 事故史 / 墓碑注释整批删除**，
> 代码只留标准 Javadoc；**代码里只保留两处一行式护栏**（见 §附二.14）。
> 因此**本节是这些知识的唯一副本，NEVER 期望回到源码注释里再找**。
> 引用格式 `路径:行号` 用的是**删除前**的行号，删除后会前移，故每条都附可 grep 的符号名或原文短语，行号只作粗定位。

### 附二.1 model —— 报文骨架 `ItpCommonRequest` / `ItpCommonFormRequest`

- **全项目只有这两个报文骨架类**：`model/.../app/ItpCommonRequest.java:3`（泛型 `<T>` 承载 `bizData`）与
  `model/.../app/ItpCommonFormRequest.java:3`（`extends ItpCommonRequest<String>`，form-data 入向专用，
  原文「全项目唯一实现」）。7 份业务模块副本已于 2026-09-11 删除收口，**NEVER 再在业务模块新建同形副本**。
- **只承载报文骨架、不承载签名语义**（`ItpCommonRequest.java:3`）。四条链路加签验签各不相同：`account-server` 摘要式
  SHA1/MD5 + `signKey`、`acc-secure-server` 的 `AccSecureSignUtils`、`pay-sign-server` 的 fastjson2 摘要、
  `collect-pay-server` 的 `signType=00` 不签、渠道方向 RSA。**NEVER 因为共用本类就顺手统一签名逻辑**（安全红线）。
- **`toString` 对 `sign` 恒定脱敏**（`ItpCommonRequest.java:95`，原文「sign 恒定脱敏」）：**NEVER 改成打印真实签名值**；
  但 **`bizData` 会完整进日志** —— `fep-alipay-server` 的 8 处 `log.info("...{}", request)` 因此从「只打对象 hash」
  变成「打全部字段」。要收紧日志脱敏面 MUST 改 `toString` 这一处，NEVER 逐个改调用点。

### 附二.2 model —— `CommonResult` 与响应封装

- `common/response/` 只有四个类：`CommonResult`（`retCode` / `retMsg`）、`ResultVO`、`ResultMapper`、
  `AlipayCommonResponse`。**`CommonResult` 自己的注释里没有 `retCode` 取值集合** —— 全仓唯一成文处是
  `rpc/.../outcome/RpcOutcome.java` 的 `SUCCESS_CODE = "0000"`；判断某个码的含义 MUST 看对应模块的
  `*ErrorCodeEnum`（`model` 侧是 `common/constant/FepAppErrorCodeEnum`），**NEVER 假定全项目共用一套码表**。
- 各 `*RespDTO` / `*Result` 的通用约定（逐条落点见 §附二.5）：**调用方 MUST 先判 `retCode` / `resultCode` 再取业务字段**；
  查询未真正执行时计数类字段恒为 0、布尔字段被置成保守值（如 `hasUnsettled=true`），
  **NEVER 把「没查到」读成「无欠费 / 已结清 / 无待处理」**。

### 附二.3 model —— 卡类型 `CardTypeMapping` 与发卡 / 渠道枚举

| 位置（删除前） | 迁出的知识 |
|---|---|
| `model/.../app/CardTypeMapping.java:15` | **日票聚合码 `05` MUST 展开成多值**（`toIssueCardTypes`）：APP 只有一个「电子日票」入口、不分一日/三日/七日/月票，而发卡卡类型按天数细分 `12~15` → `0445~0448`。**NEVER 走单值映射** |
| `CardTypeMapping.java:24` | **NFC 的 `03` / `04` 在查询侧同义、MUST 展开成 `0442+0443`**。实测：页签发 `03`（`/ci/app/requestTransList` 抓包 `cardType=03 + cardId=0426091000000013`），而同一张卡开户时 APP 送 `04`（`USER_ITP_REG_INFO.ITP_CARD_TYPE=04` / `CARD_TYPE=0443`），闸机上报与订单落库也是 `0443` —— 单值映射成 `0442` 后**恒命中 0 行且 `retCode=0000` 不报错**（2026-09-10 定位，订单 `GT20260910164625246000013` 查不到）。**NEVER 退回单值映射** |
| `CardTypeMapping.java:62` / `:94` | `toIssueCardType` / `toIssueCardTypes` 对**未识别值原样返回、不抛异常**，脏值会静默流到下游（SQL 命中 0 行、卡池预占匹配不到 `CARD_TYPE` 分区）。因此**接口入口 MUST 先 `isSupportedAppCardType` 校验**，NEVER 依赖映射方法拦非法票种 |
| `CardTypeMapping.java:122` | 同上反向表述：`isSupportedAppCardType` 是唯一的拦截点 |
| `model/.../cardpool/CardPoolTicketType.java:31` / `:60` | 规范化票种码 = 去空白 + 转大写 + 必须落在 `ALL_TYPES` 内。**已发生事故**：2026-09-10 日票下单送 ACC 口径 `cardType=45`，映射表无此键被原样返回、一路穿到 card-pool 预占，与任何分区都不匹配，卡池回 `code=null/msg=null`、激活报「发卡服务暂不可用」，订单 `0E202609101131510006` 支付后 1.5 小时才在激活环节暴露 |
| `model/.../enums/IssueChannelCodeEnum.java:60` | `thirdUserIdLength` 于 2026-09-14（ADR-D70）从两个模块上移到此：此前长度散落在 `fep-dev-server` 的 `DeviceUserIdCodec.normalize` 与 `ticket-server` 的 `SupplementCodec.normalizeDeviceThirdUserId`，而「谁算支付宝」（`isAlipay`）一直在本类。**本方法只管长度，NEVER 把补位 / 进制转换搬进来**（fep-dev 侧收的是十六进制、要先 `new BigInteger(x,16)`；ticket 侧已是十进制）。未知 / null 渠道码返回 **8**，与上移前 `isAlipay ? 10 : 8` 逐位一致，**NEVER 改成先 `fromCode` 再取实例属性**（会给未知渠道引入 null 路径） |
| `model/.../enums/CardIssueOrgEnum.java:3` | 发卡机构编码枚举 = APP 开户 IF8A-01 上送的 `cardIssueCode` 原值，属对外契约取值 |

### 附二.4 model —— 状态机枚举与 `domain.OutboxScan`

四台状态机枚举（`model/domain/`）共有的三条口径，**改任一处 MUST 看齐其余**：
① **只做解析 / 快速失败 / 文档化，NEVER 当作并发保证** —— 并发保证是 mapper XML 里的 CAS；
② **NEVER 改动常量字面量** —— 库内存量数据按这些字符串存储，且 `pay-sign-server` 侧仍有 `private static final String`
常量与裸字面量在比较同一批值，改名等于制造两套口径；③ **新增取值 MUST 同步 `ALLOWED` 与 mapper XML 的 CAS**。

| 位置（删除前） | 迁出的知识 |
|---|---|
| `domain/SignStatus.java:7` / `:17` | **NEVER 允许 `UNSIGNED -> SIGNED`**：已解约通道被迟到的签约回调覆盖，会让 APP 显示通道有效而渠道侧协议已注销；`NOT_SIGNED` 是复位重签用的 |
| `domain/SignStatus.java:49` / `:66` | `parseOrNull` **NEVER 兜底成具体状态** —— 猜错方向会把脏数据推进状态机 |
| `domain/TerminationStatus.java:7` / `:47` | **NEVER 允许 `FAILED -> SUCCESS`**（用户 2026-09-12 裁决）：该场景 MUST 走人工（`expired > 0` 的 `log.error` 是告警出口），运维到支付中心核对协议真实状态后由用户从 APP 重新申请（走 `FAILED -> PENDING` 复活） |
| `domain/TerminationStatus.java:25` / `:78` | **NEVER 允许 `SUCCESS` 迁出**：解约成功后签约记录已 DELETE、账户域支付通道已清理，回退状态只会让补偿队列重新捞取、对 APP 重复投递 |
| `domain/TerminationStatus.java:86` | **NEVER 把 `PENDING` / `SCANNING` 纳入 `isNotifiable()`**：那两个态没有结果可通知，滞留后会被补偿当成「成功通知丢了」，给 APP 发一条 `status=SUCCESS` 且 `dismissalTime` 为空的**假解约成功通知**（2026-08-26 已修复过一次） |
| `domain/SyncStatus.java:7` / `:21` | `SUCCESS` 是唯一终态、**NEVER 允许迁出**（补偿扫表按 `SIGN_SYNC_STATUS IN ('PENDING','FAILED')` 捞取，回退即对已送达记录重复推送）；**NEVER 把 NULL 兜底成 `PENDING`** —— 那等于对改造前的存量记录发起一轮真实 RPC 重推 |
| `model/.../ticket/enums/QRCodeStatusEnum.java:7` / `:141` | 状态迁移白名单**只做解析与文档化，NEVER 当作并发保证**（并发保证是 `QRCodeStatusMapper.upsertWithCas` 的 `TXN_SEQ` CAS），也 **NEVER 用它在 CAS 之前拦请求** —— 调用方手里的「当前状态」来自更早一次 select、随时可能过期 |
| `QRCodeStatusEnum.java:101` | `80` / `81` 的入边已按 `ExcessFareHandler.resolveAllowedTypes` 的前置状态集合补齐，**改那个方法的允许集合时 MUST 同步看齐这里**，否则每笔补站都打一条「迁移不在白名单内」WARN；`10`（入站码更新）**没有任何写入路径**（2026-09-09 实测生产库 0 行），其出边是理论值、当前不可达，**NEVER 把新功能的前置条件挂在 `10` 上** |
| `QRCodeStatusEnum.java:134` / `:155` | `FF`（进站失败）只在 `03/05/06/80/FF` 之后合法，**NEVER 加进 `ALWAYS_ALLOWED_TARGETS`**；上游可能上送未登记取值，**解析失败 MUST 由调用方决定处置、NEVER 在枚举里抛** |
| `model/.../domain/OutboxScan.java:8` | 只固化 Java 侧三条不变量（SQL 侧的坑见 `docs/domain/outbox.md`）：**单条失败 NEVER 中断整批**（一条恒失败的记录会永久挡住它后面所有行）；**NEVER 建公共 outbox 表** —— 待投递的事实 MUST 留在产生它的那张业务表上（如 `USER_PHONE_CHANGE_LOG.SIGN_SYNC_*`）；**NEVER 往本类塞 Spring / MyBatis 依赖**（需要注入就说明抽错了层）。刻意不管的四件事 MUST 留在调用方：扫表 SQL 与白名单、重试上限判据、达上限后开工单还是转人工、状态列取值集合 |
| `OutboxScan.java:39` / `:48` | `scanned` 恒等于 `success + failed`；各域补偿端点自己的返回记录（如 `SignSyncCompensateResult`）是**对外契约**，**NEVER 用本记录替换它**，转换一下即可。`deliver` MUST 自己把成败落库（本类不碰数据库），`onUnexpected` **MUST 只记日志、NEVER 再抛** —— 从那里抛出去会中断整批 |

### 附二.5 model —— 各业务 DTO 逐条（`app` / `pay` / `paysign` / `collectpay` / `cardpool` / `recon` / `ticket` / `alipaytrip` / `accsecure`）

| 位置（删除前） | 迁出的知识 |
|---|---|
| `app/AppIndustryDataNotifyReqDTO.java:16` | 一个 `thirdUserId` 下可同时存在主码与同行码，APP 侧 MUST 按 `cardId` 落地；票种标识只区分语义，**NEVER 当成卡的唯一键** |
| `app/BlacklistReleaseCandidateDTO.java:3` / `:32` | 只用于**只读盘点报表，NEVER 被任何删除逻辑消费**。`BLACKLIST` 没有拉黑类型字段、`REASON` 是四个来源混写的自由文本（支付中心应答原文 / 代码拼接 / 外部接口传入 / 运营手工输入），生产实测 35 条 ADD 里 22 条是「用户挂失补卡」——与欠费无关，误删等于让挂失旧卡恢复过闸。`SETTLED` 只代表钱结清、**NEVER 等同于「可以解除」**，判定 MUST 由人看 `reason` |
| `app/BlacklistReleaseInspectRespDTO.java:5` | 字段与 blacklist-server 侧同名类一一对应，**改动 MUST 两侧同步**；本接口 **NEVER 删除任何黑名单记录** |
| `app/CardUnsettledQueryReqDTO.java:3` | 故意不带模块前缀命名，新增第三个欠费源时**直接复用，NEVER 每模块复制一份同结构 DTO** |
| `app/QueryPayChannelByContractReqDTO.java:3` | `APP_PAY_SIGN_INFO` 的 `CARD_ID` / `CARD_TYPE` **全库为 NULL**，真实来源是 account 侧 `APP_USER_PAY_CHANNEL`（`REQ_CONTRACT_NO` 即签约流水号）。IF8A-75 走 `createTerminationRequest` 补建申请时从签约记录回填**必然拿到 NULL 并抛 `ORA-01400`**（2026-09-08 实测） |
| `app/QueryPayChannelByContractResult.java:3` | 未找到返 `8004`，**MUST 判 retCode 而不是只判字段空** |
| `app/QueryTransListReqDTO.java:22` | 日票聚合码 `05` 展开成 `0445~0448`，因此**入参 MUST 用列表而非单值** |
| `app/RequestPayReqDTO.java:45` | `TXN_DATE` 是 `PAY_TXN_DETAIL` 的**月分区键**，MUST 与行程侧同一 `ORDER_NO` 的 `TXN_DATE` 一致；支付域自取 `LocalDateTime.now()` 会跨零点分叉（实测出站到落库最大滞后 94 分钟，22:26 后出站即可能跨日），两表按 `(ORDER_NO, TXN_DATE)` 关联随即落空 |
| `app/RequestTransStatisticsReqDTO.java:5` | IF8A-41 实测 APP 只上送 6 字段（2026-09-10 抓 fep-app 日志）：`thirdUserId/cardType/transType/cardId/startDate/endDate`；**`cardType` 与 `transType` 恒同值**（三个页签 05/05、02/02、03/03），一个语义，**NEVER 把 `transType` 当另一个查询维度**。**NEVER 复用 IF8A-05 的 `TransQueryHandler.normalizeDate`**（它按 `yyyy-MM-dd` 解析，喂 `yyyyMMdd` 抛 `IllegalArgumentException`）。未开通某票种时该页签**不带 `cardId`**，因此 `cardType` 是唯一票种过滤依据，缺它会退化成「按用户查全部票种」（实测 4 票种时把 47 条全算进来） |
| `app/RequestUserAccInfoResult.java:3` | IF8A-35 分档口径：`CLOSED` 与脏数据 `NULL` 两档都不计入，故 `unpaidCount + failureCount` **不等于「全部非 SUCCESS 订单数」**，与解约校验 `countFailedOrder` / 黑名单 `countUnsettledOrderByCardId` 的「非 SUCCESS 即未结清」**不是同一个口径**，比对两边数字前 MUST 先看清定义；查询未执行时两数为 0，**NEVER 当成「无欠费」**；**放行类判定（过闸、解约）各有自己的校验，NEVER 改用本接口结果** |
| `app/SyncPayAccountIdReqDTO.java:3` / `:26` | 2026-09-11（ADR-D32）新增，补 ADR-D30 的覆盖率缺口（`PAY_ACCOUNT_ID` 原先只有 IF8A-77 会写、签约后恒空、运营页显示 `-`）。本 DTO **不经过 `parseBizData`**（调用方是 pay-sign-server），按 `docs/domain/README.md` 判据属**对内接口**，加字段不受「对外契约 NEVER 加字段」约束。**NEVER 用它去改 `USER_ITP_REG_INFO.THIRD_PAY_ID`** —— 那列是 IF8A-77「更换默认支付方式」这个业务动作的产物，越过动作去写等于替用户决定默认支付方式；三个同名概念 NEVER 混用 |
| `app/TransRecordDTO.java:34` | 「原价」字段 **NEVER 当成 `GATE_TXN_PAY.TOTAL_AMOUNT`**（那是实付 = 车费 + 超时费，对应 `payAmount`）；赋值处 MUST 与 `originalFare` 同源、两者不允许出现不同值 |
| `app/TripDataDTO.java:3` / `:25` | IF8A-41 四个金额口径（2026-09-10 校准，统计源表已从 `QRCODE_TXN_DETAIL` 换到 `GATE_TXN_PAY`）：`totalPrice=SUM(NVL(ORIGINAL_FARE,TRX_AMOUNT))`、`totalDebit=SUM(TOTAL_AMOUNT)`、`totalDiscount=SUM(MAX(原价-TRX_AMOUNT,0))`、`totalOvertime=SUM(OVERTIME_AMOUNT)`。**NEVER 把 `OVERTIME_AMOUNT` 映射到 `totalDiscount`**（超时加收与优惠语义相反）—— 旧实现 `QRCodeTxnDetailMapper.selectTransStatistics` 三个标签全部错位（把 12 元超时费显示成「已优惠 12 元」）。`totalDiscount` 的减数 **MUST 是 `TRX_AMOUNT` 而非 `TOTAL_AMOUNT`**，后者含超时费会算出负数 |
| `app/UnbindAgreementReqDTO.java:3` / `UnbindAgreementResult.java:3` | 钱包（`paymentVendor=0B`）**不适用本接口**（开户即绑通道、无签约流水），解绑 MUST 走 IF8A-25 `requestRemovePayChannel`；**APP NEVER 把本接口的 `0000` 当成解绑成功直接刷新为「未绑定」** |
| `app/UpdatePhoneReqDTO.java:3` / `:16` | 规范字段是 `newPhone`，**NEVER 改回早期自造的 `newMsisdn`**（2026-09-09 与规范核对后纠正）；字段名本身是对外契约，**改名 MUST 连带重建 `fep-app-server` 镜像并与 APP 同步切换**，否则 Fastjson2 静默丢弃该键、下游只收到 null |
| `app/UpdatePhoneResult.java:5` | 原先多出的 `thirdUserId` 从未赋值、恒 null，属契约冗余，2026-09-09 已删除 |
| `app/UserCancelReqDTO.java:3` / `UserCancelResult.java:3` | 销户只把 `USER_ITP_REG_INFO.DEL_YN` 置 0，**NEVER 顺手删 `USER_PAY_CHANNEL`**（那是 IF8A-75 解绑才做的事，提前删会让后续解绑找不到渠道信息）；**NEVER 把「查不到有效开户记录」当成错误** |
| `app/dailyticket/DailyTicketRefundCallbackReqDTO.java:3` | 字段名逐字对齐支付中心回调报文，**NEVER 改名或增删**：本 DTO 既是 fep-app 侧 `parseBizData` 的目标类型、又是转发给 daily-ticket-server 的入参 |
| `app/dailyticket/QueryDailyTicketPayInfoReqDTO.java:3` | 入参**只用 `ticketCode`，NEVER 改成 `cardNum`**（一张卡先后买过多张日票，按卡号查会命中历史多单）；**NEVER 与 `QueryDailyTicketInfoReqDTO` 合并**（后者服务 IF1A-01 闸机热路径，入参 `orderNo/cardId`，语义与时机都不同） |
| `app/dailyticket/QueryDailyTicketPayInfoResult.java:3` / `:27` | 三个字段名 MUST 与 IF8A-05 / IF8A-34 出参 `ticketTransRecord` 的同名字段一致（甲方《青岛地铁-ITP与APP接口规范R6.docx》if8a_34）。数据来源（2026-09-15 用票号 `2099439054066438144` 实测打通）：`DAILY_TICKET_INSTANCE`（按 `TICKET_CODE`）→ `ORDER_NO` → `DAILY_TICKET_ORDER` 的 `TRADE_NO/PAY_DATE/PAY_CHANNEL_CODE`。**NEVER 因为「时间看着不对」改成行程时间或清空**；查不到时三个字段**一律留 null、NEVER 编造默认值**；`PAY_DATE` 与 `entryDate/exitDate` 同为 14 位定长、**NEVER 返回带分隔符的形式** |
| `paysign/CompensateNotifyRespDTO.java:3` | 签约 / 解约两个补偿端点**共用同一结构**；`submitted` 只代表「提交成功」，**NEVER 用它判断通知是否送达**（真实结果看 `APP_PAY_SIGN_REQUEST` / `APP_TERMINATION_REQUEST` 的 `NOTIFY_STATUS` / `NOTIFY_RESULT`）。2026-09-15 起本类是**唯一定义点**（pay-sign 侧同名副本已删），**NEVER 再在业务模块新建同形副本** |
| `paysign/ProcessTerminationReqDTO.java:3` | 两个字段都可省略：都不传即不按申请时间过滤（扫全部 `PENDING + SCANNING`）；传了则 `cutoff = referenceTime - delayDays`，业务口径「解约申请 4 天后才确认」由 `delayDays` 表达，过滤同时作用于两个状态 |
| `paysign/ResendSignNotifyReqDTO.java:3` / `RespDTO:3` | 联调期人工重放走这个单条接口，**NEVER 为了重发一条而去打批量补偿接口**（会把所有符合扫描条件的历史流水一起发给 APP）；`resultCode=0000` 只代表接口处理完成，**是否送达 MUST 看 `notified`** |
| `pay/GateTxnPayReqDTO.java:88` | 支付宝行业明细 9 项（`entryLineCode/entryLineName/exitLineCode/exitLineName/entryDeviceCode/entryId/exitId/cardNum/cardIssueCode`）**MUST 由 fep-dev-server 在出站当次组装后整块透传**：`GATE_TXN_PAY` 没有对应列，需 para 单查站线（批量接口不返回线路字段）、ticket 查进站设备号、按 `itpUserId + 时间戳`现算进出站 ID —— **出站是唯一能拿全这些值的时点**，gate-txn-pay 原样落 `INDUSTRY_DETAIL`，**NEVER 在下游重算**。非支付宝渠道恒 null，此时 `PaySignInitiator` 走老路、行业明细用订单快照 `JSON.toJSONString(order)`，**两套语义 NEVER 混用** |
| `pay/GateTxnPayReqDTO.java:103` | `copyCommonFields` 只搬运设备上送字段，**NEVER 在此加默认值 / trim / 大小写归一 / 空值兜底** —— 一旦加了「设备上送什么就落什么」这条前提就断了，而 `GATE_TXN_PAY` 是对账依据；也 **NEVER 把本类独有字段（`ticketStatus`/`orderExpType`/`offlineFlag`/站名/`industryDetail`）搬进来**（来源是 ticket-server 响应与 para 查询，不是设备报文）。⚠️ 这是**新增静态方法**、不受「Fastjson2 静默丢字段」约束，但调用方 MUST 用重新 install 过的 `model` 重建镜像，否则旧镜像启动即 `NoSuchMethodError` |
| `pay/GateTxnPayListDTO.java:53` / `:58` | `entryId` / `exitId` **只有出站那一刻拿得到**，由 fep-dev-server 整块透传存下，fep-alipay 的行程详情靠它们去 ticket-server 关联进出站交易；**NEVER 在查询侧按订单字段重算**（重算出来的键名与值都与支付宝要的不一致） |
| `pay/GateTxnPaySyncStatusReqDTO.java:3` | 存在的原因：`GATE_TXN_PAY` 只在「日票 / 零元交易」分支直接写 `SUCCESS`，真实免密扣款订单调 pay-sign 后只停在 `PROCESSING` / `RETRY`，**没有任何代码能把它推进到 `SUCCESS`** —— 扣款结果只有支付中心回调知道，而回调只发到 pay-sign-server（2026-08-26 生产实测：全部真实扣款订单卡在中间态） |
| `pay/GateTxnPayDebitConvergeReqDTO.java:3` / `:33` / `:43` | 补款收敛是**独立端点 + 独立响应契约**（响应 MUST 带 `converged` 与当前 `debitStatus`）；**NEVER 在 face-pay 侧加回任何对 `GATE_TXN_PAY` 的写语句**（复用会让重复扣款静默漏判）。`origTxnDate` **MUST 传**（月分区裁剪），取补款明细的 `ORIG_TXN_DATE`、**NEVER 用当天日期替代**（补款可能发生在行程之后任意一天）；备注按 `"补款单号:" + 单号` 组织，**NEVER 让任何代码分支去解析它**。要把收敛白名单收窄成不含 `FAIL` 属业务规则变更，MUST 先与业务确认 |
| `pay/GateTxnPayDebitConvergeRespDTO.java:3` / `:36` / `:42` / `:52` | **NEVER 只看 `retCode` 判成功**：`0000 + converged=true` = 本次把欠费单推进 `SUCCESS`（调用方标已结清）；`0000 + converged=false + debitStatus=SUCCESS` = 钱已实收但行程早已结清 ⇒ **重复支付、待退款**，MUST 标失败并留「重复支付待退款」，**NEVER 当成幂等成功悄悄跳过**；其余 = 业务拒绝、**一次即终态** + 留人工核对线索。`converged` 用 `Boolean` 而非 `boolean` 是**故意的**（Fastjson2 缺字段得 null，基本类型会静默 false，把「成功收敛」误判成「重复支付」），调用方 MUST 用 `Boolean.TRUE.equals(...)`；`debitStatus` 是 `converged=false` 时唯一的区分依据，对端 **MUST 始终回填、NEVER 因为「本次没改行」就省略**；`retMsg` 仅用于日志与工单、**NEVER 做分支判断** |
| `pay/GateTxnPayFailedOrderRespDTO.java:3` | 解约流程 **MUST 先判 `resultCode == "0000"` 再用 `hasFailedOrder`** |
| `collectpay/AppPayOrderRegisterReqDTO.java:3` / `:45` | 三条资损防线：`totalAmount` 单位是**分**、会同时写 `TICKET_PRICE` / `TOTALPRICE` / `PAY_AMOUNT`，`TICKET_NUM` 固定 1；collect-pay 的 `requestPayInfo` 按 `TICKET_PRICE × TICKET_NUM` 算送去支付中心的金额，**NEVER 只写 `TOTALPRICE`**（算钱时根本不读那列）；`supplementFlag` 落 `RSV2`、**MUST 非空** |
| `collectpay/AppPayOrderCloseReqDTO.java:3` | 不关单的后果：乘客对一张已作废 / 已关闭的补款单再点支付时，collect-pay 仍看到 `PAY_STATUS='0'` 而正常发起预下单 —— **钱收进来了、上游那张单却已关闭，无人收敛** |
| `collectpay/AppPayOrderRespDTO.java:3` / `AppPayOrderResultRespDTO.java:3` | 调用方 MUST 用 `RpcOutcome` 三分支处置，**NEVER 只判「没抛异常就算成功」**。`found=false` 是对端明确答复的**业务事实**（这张单没登记进 APP 订单表、乘客根本付不了 ⇒ 落 ERROR 转人工 / 重新登记），而连不上 / 超时 / 5xx 会让查询方法**抛异常**，**NEVER 当成 `found=false`**（一次抖动会被误判成「单据丢失」）。**判成功 MUST 严格等于 `1`、NEVER 写成「非 2 即成功」**；`ItpStatusEnum.PAYING` 的 desc 写「支付中」但 `TBL_TVM_APP_ORDER.PAY_STATUS='0'` 实际是**下单后未支付**的初始态（关单白名单 `TvmAppOrderMapper.closeUnpaidByOrderNo` 的 `and PAY_STATUS='0'` 正是靠它选行），该枚举 0/1/2 各有两个常量（支付组与退款组共用 code），**NEVER 按字面理解** |
| `cardpool/CardPoolOutcome.java:3` / `:22` | 三分支存在的理由：此前 `CardPoolClient` 用 `null` / `false` 表达全部失败，调用方分不清「池里真没号」「票种压根不走卡池（程序缺陷）」「card-pool-server 不可达（可重试）」，只能一律翻成「无可分配逻辑卡号」，把配置错误与网络故障都报成卡池耗尽。`REJECTED` 属参数 / 配置问题、**重试无用、MUST 告警** |
| `cardpool/CardPoolActionResult.java:3` / `CardPoolReserveResult.java:3` | 服务端对释放 / 确认两个动作**已做幂等兜底**（影响 0 行时回查终态），故 `SUCCESS` 同时覆盖「本次改状态成功」与「此前已到终态」；`getMessage()` 是服务端原文，只用于日志与告警、**NEVER 直接透给终端用户** |
| `cardpool/CardPoolReservationActionReqDTO.java:14` | `businessId` **MUST 与创建预占时一致**，否则命不中预占记录 |
| `ticket/NotifyVerifyResultRespDTO.java:35` / `:76` / `:93` | 本次行程消耗日票次数**恒为 1**（仅 `SIGN_CHANNEL_CODE=12/13/14/15` 写入），**NEVER 回填 `DAILY_TICKET_INSTANCE.ACTUAL_TIMES`** —— 那列用 `-99` 表示不限次，会一路透到 APP 与 `GATE_TXN_PAY.COUNTING_TIMES`（2026-09-10 订单 `GT20260910143159899000084` 已发生）。`debitRequestResult` **NEVER 依赖**：全仓无写入方、恒 null（2026-09-14 grep `setDebitRequestResult` 只命中本类 setter），看扣款结果 MUST 走 IF8A 交易记录（权威列 `GATE_TXN_PAY.DEBIT_STATUS`，`PAY_TXN_DETAIL.DEBIT_REQUEST_RESULT` 仅兜底）。实际卡类型 **MUST 走响应通道回传**：fep-dev → ticket 的请求对象是跨进程值拷贝，ticket 在 `applyActualCardType` 改的是自己那份副本；支付渠道编码已由 `payChannelCode` 承载 `USER_ITP_REG_INFO.CHANNEL`，**NEVER 再加 `paymentVendor` 表达同一个值** |
| `ticket/enums/AdviceOptEnum.java:7` / `:54` / `:100` / `:127` / `:139` / `:155` | 这套取值此前**在三处各写一份且互不知晓**（`GateTicketHandler` 三个常量 / `CardDataHandler` 约 20 处裸字面量 / `fep-dev` 的 `Set.of("005","006")`），第三处是**防资损白名单**（BOM 已补出站的交易不再重复扣费），对不上即漏扣或重扣，2026-09-14 收口到本枚举。**码值 NEVER 改动**（`000/005/006/018/020` 是与 BOM、闸机的对外契约）；新增取值 **MUST 同时确认 `isSupplementExit()` 归属**（漏归类会让 `GateFarePaymentOrchestrator.shouldPay` 走「未知即扣费」）。**`020` NEVER 放进 `isSupplementExit()` / `SUPPLEMENT_EXIT_CODES`**：2026-09-14~15 之间曾实现成「更宽松版 005」（补出站 + 落 08 + 进资金白名单），**那版口径已作废、NEVER 回退** —— `005/006` 的前提是「卡已进站未出站」，而 `020` 要放行**从未进站的新卡 03**（用户第三次裁决），补进站口径才自洽；登记 `020` 的直接原因是 BOM 实际上送的就是它（2026-09-14 19:58 实测），枚举里没有导致 IF5A-03 一路返 `8001`、零数据落库。`isSupplementExit()` 是资金安全判据：命中即出站费用已在 BOM 侧结清、出站扣费 MUST 跳过；不命中（含未知取值）MUST 照常扣费，**NEVER 因为「020 也叫免费更新」就加回来**；上游可能上送未登记取值，**处置 MUST 由调用方决定、NEVER 在枚举里抛** |
| `recon/ReconFileTypeEnum.java:3` | `PAY` / `BUS` 是**汇总文件**，源服务 MUST 在数据库内 `GROUP BY` 后再上送，recon-server 按 `getKeyFieldCount()` 段键聚合、对随后 `getMetricFieldCount()` 个度量列累加；字段顺序来自甲方《ACC与ITP之间的文件.docx》§一，**改动 MUST 同步四个源服务与 recon-server** —— 分片是纯文本无 schema，**错位不报错、只静默出错账**；`EXP` 是「单边账 / 异常交易」明细，**NEVER 当成全量过闸明细** |
| `recon/ReconRecord.java:3` | **NEVER 在业务代码里自行 `String.join("|", ...)` 拼行**：字段里一旦带分隔符或换行，整个文件从该行起全部错位，而纯文本没有 schema、下游读不出异常 |
| `recon/ReconExportReqDTO.java:10` / `ReconExportRespDTO.java:5` | 各源 MUST 用本类提供的转换方法落到自己的列类型，**NEVER 在 SQL 里对时间列做函数转换**（分区裁剪与索引全失效）；`accepted=true` 只表示已排入抽取队列，**收齐判定 MUST 以 `RECON_BATCH_SOURCE.STATUS=COMPLETED` 为准** |
| `recon/ReconPartReceiptDTO.java:5` | 重试 **MUST 复用相同分片号、NEVER 换号重传** —— 换号会让同一批数据在最终文件里出现两次 |
| `recon/ReconSourceCompleteReqDTO.java:5` | 分片数 / 记录数 / 金额三项任一不符即置 `MISMATCH` 并告警、**NEVER 放行** —— 少一片就少几十万条，而文件本身看不出缺失 |
| `alipaytrip/AlipayProcessTerminationRespDTO.java:3` | **MUST 先判 `resultCode=="0000"` 再看计数**，否则把「查询未执行」误读成「无待处理记录」 |
| `accsecure/RequestQrLogicNumListReqDTO.java:43` / `:52` | 申请流水号与批次号一一对应，**ACC 侧按整数解析、MUST 为纯数字** |

### 附二.6 rpc —— `ProxyWebClient`（实体在 `resource/micro/web`）与动态路由

- **`ProxyWebClient` 不在 `rpc` 模块里**，实体是 `resource/micro/web/.../web/client/ProxyWebClient.java`；`rpc` 的各 Client
  只是它的子类。排查连接池 / 超时 / 请求头行为 MUST 去 micro 侧看，**NEVER 在 `rpc` 里找它的实现**。
- **超时只能从构造器传，NEVER 靠覆写 `getResponseTimeout()` 读实例字段**（`ProxyWebClient.java:96` / `:114`）：
  `getResponseTimeout()` 是在**父类构造期**被 `getInitOkHttpClient` 调用的，那时子类的 `@Value` 字段还没赋值，
  「覆写后返回自己的超时字段」**实际读到恒为 0 / null，且编译与单测都发现不了**。需要非默认超时的子类 MUST 用带
  `responseTimeout` 形参的构造器（传 null 退回默认 10 秒）。
- **只自动透传 MDC 里的 `authorization` 一个头**（`addAuthorizationToHeader`），**不注入任何 trace 头**。因此
  Quartz 侧要带链路上下文 MUST 显式传 `QuartzTraceUtils.traceHeaders`（W3C `traceparent` + `X-Vlogs-Capture`）；
  服务间调用的 traceId 靠 Boot 托管的 `WebClient.Builder` 由观测自动带出。**NEVER 自造 `traceId` 请求头** ——
  下游 `FirstFilter` 把请求头名全部小写后塞 MDC，`traceId` 会变成 `traceid`，与 log4j2 的 `%X{traceId}` 对不上、等于白传。
- **HTTP 4xx 与连接失败在 `handleResponse` 里被包成同一个 `RuntimeException`**（`ProxyWebClient.java:161~173`；
  **注释里此前写 `:138` 是错的、行号已随改动漂走，引用前 MUST 先 grep 方法名、NEVER 照抄行号**）——
  这就是 `RpcOutcome` 把 4xx 归到 `Unreachable` 的原因（见 §附二.7）。
- **动态路由靠「baseUrl 传空串 + 每次调用给绝对 URL」**（`rpc/.../route/URLDynamicRouter.java`，装配注解 `@EnableRpcRoute`）；
  `ReconExportClient` 是现行唯一使用者，理由与坑见 §附二.8。

### 附二.7 rpc —— `RpcOutcome` 三分支（`Ok` / `BizRejected` / `Unreachable`）

- **为什么不是 boolean**（`outcome/RpcOutcome.java:3`）：AGENTS.md §5.2 为「内部 catch 全部异常后 `return false`」的包装方法
  专门写过规则，而它被违反过两次并双双造成生产不一致（`docs/domain/decisions.md` ADR-D13）。规则靠人记、boolean 靠人查；
  `sealed` + `record` 让穷尽性由**编译器**检查 —— 调用点少写一个分支直接编译失败。
- **真正的收益是可区分「业务拒绝」与「网络不可达」**：`BizRejected`（对端答复但业务拒绝，如 UPDATE 影响 0 行后返 FAIL）
  **重推一万次也不会成功、MUST 直接转终态 / 工单，NEVER 进补偿队列**；`Unreachable`（连不上 / 超时 / 4xx / 5xx）
  才是补偿队列该收的那类。**误判方向 MUST 是「把永久失败当成可重试」**（代价只是白重试几轮），
  **NEVER 反过来** —— 把网络抖动当成业务拒绝会让一笔真实待投递的事实被直接判死。
- `retMsg` 仅用于日志与工单、**NEVER 做分支判断**（`:43`）；`Unreachable.cause` **MUST 保留原始异常**，
  调用方 **NEVER 只打 `getMessage()`**（`:52`）；`isSuccess()` **只用于日志与计数，分支判断 MUST 用 switch 模式匹配**（`:60`），
  否则又退回 boolean。**本类型 NEVER 参与序列化**（`:26`）：它是进程内调用结果、不是报文 DTO，因此放 `rpc` 而不是 `model`。
- **落地方式是「新增方法、老方法保留」**：`rpc` 版本号锁死在 2.0.1、被 21 个模块引用，**MUST 只增不改签名**、逐调用点迁移；
  `PaySignClient.updatePaySignDisplayAccount` 只作为 `@Deprecated` 兼容壳（`:342`），实现已委托给
  `updateDisplayAccountOutcome`（`:355`），**NEVER 在兼容壳里重复一份解析逻辑**。新版方法 **NEVER 向外抛异常**：
  异常统一收成 `Unreachable` 并带 cause；解析 MUST 收在方法内（`:377`）—— HTTP 2xx 但响应体不是 JSON（如网关返 HTML 错误页）时
  `JSONUtil.parseObj` 会抛 `JSONException`，逃出去就破坏了「NEVER 向外抛异常」的契约。

### 附二.8 rpc —— 各 `*Client` 逐条（20 个 Client）

`rpc` 下现有 20 个 Client：`account` / `alipay.account` / `alipay.paysign` / `blacklist` / `cardpool` / `collectpay` /
`dailyticket` / `f2f` / `facepay` / `fepDev` / `industry` / `key` / `para` / `pay(GateTxnPay)` / `paySign` / `recon(ReconClient
+ ReconExportClient)` / `security` / `ticket` / `transquery`。下表是它们注释里迁出的知识（`dailyticket` / `fepDev` /
`industry` / `key` / `security` / `ticket` 六个当时无 MUST-NEVER 类注释）。

| 位置（删除前） | 迁出的知识 |
|---|---|
| `paySign/PaySignClient.java:227` / `:241` | 解约申请批处理 `/internal/termination/process` 供 web-admin Quartz 调；**不抛业务异常，失败体现在 `resultCode` 上，响应为 null 说明 HTTP 层没通**。带 headers 的重载只需传 `traceparent`（W3C `00-<32hex>-<16hex>-00`），**NEVER 传自定义 `traceId` 头** |
| `PaySignClient.java:256` / `:279` | 签约通知补偿扫 `APP_PAY_SIGN_REQUEST`、解约通知补偿扫 `APP_TERMINATION_REQUEST`，**覆盖的表不同、不可互相替代、MUST 各自调度**。**调用方 NEVER 在同一次调度内循环调用**：下游在提交重发**之前**就同步 `NOTIFY_RETRY_COUNT +1` 并置 FAILED，而通知是异步发的，紧接着再调一轮会立刻扫到同一批、**几秒内烧完全部重试预算**；排空靠 cron 周期，**调度间隔 MUST 大于下游 PENDING 滞留阈值**。`submitted` 只代表「提交成功」 |
| `PaySignClient.java:294` | 退款回查补偿 `/internal/payment/compensateRefundQuery`：扫 `PAY_REFUND_DETAIL` 里停在 `PROCESSING` 且距上次发起超 `REFUND_QUERY_STALE_MINUTES`（现 5 分钟）的单，逐条**出网**调支付中心 §3.2 `refundQuery` 回查并 CAS 收口。**与 `compensateRefundSummary` 是两件事、NEVER 合并调度**（本条出网、要贴退款时效；那条不出网、属日终级别；合并后既没法分别调频，出网这半边一挂还把纯本地那半边拖停）。**调度间隔 MUST 大于 staleMinutes**（建议 `0 0/10 * * * ?`）；单条是否收口 MUST 看 `PAY_REFUND_DETAIL.REFUND_STATUS` |
| `PaySignClient.java:319` | 退款汇总跨表对账 `/internal/payment/compensateRefundSummary`：**不出网**，只比对 `PAY_REFUND_DETAIL`（唯一账本）与 `PAY_TXN_DETAIL` 的 `REFUND_AMOUNT` / `REFUND_STATUS` 汇总并逐单重算。**`skipped` 长期非 0 是预期**，它混着两类：重算影响 0 行（下轮自愈）与「明细已 SUCCESS 但 `PAY_TXN_DETAIL` 里没有该 `ORDER_NO`」（**永远不会自愈**、只打 WARN 等人工）。**调用方 MUST NOT 把 `skipped > 0` 记成 ERROR**，区分两类只能看 pay-sign 侧日志措辞 |
| `PaySignClient.java:271` / `:288` / `:313` / `:336` | 四个无参 POST 的 **body MUST NOT 传 null**：`ProxyWebClient.postJsonAndGetResponse` 无条件调 `bodyValue(...)`，而 Spring 的 `BodyInserters.fromValue` 断言非 null，传 null 抛 `IllegalArgumentException: 'body' must not be null`、请求根本发不出去；传空 Map 序列化成 `{}`（`BlacklistClient.java:77` 同因，那里写了完整解释） |
| `pay/GateTxnPayClient.java:63` / `:78` / `:180` | `hasUnsettledOrderByCard` 按 `CARD_ID`、不带渠道、不带时间下限（`BLACKLIST` 无渠道字段，判定必须覆盖该卡全部历史欠费）；`syncDebitStatus` 的 `retCode` 非 `0000` 即 `GATE_TXN_PAY` 没收敛，**NEVER 因为「没抛异常」就认为对齐了**；IF8A-35 两个计数在查询未执行时为 0、**NEVER 当成「无欠费」** |
| `GateTxnPayClient.java:100` | `countTransList` 是本 Client 里**唯一「对端返裸标量、既无 DTO 也无 retCode」**的方法（gate-txn-pay 侧 `/ci/gateTxnPay/app/countTransList` 直接把 int 写进响应体）。这个隐式契约没有编译期保护，**因此 MUST 把「对端到底返了什么」带进异常消息**（裸 `Integer.parseInt` 的 `NumberFormatException` 只有 `For input string: "..."`，看不出是包装变了还是 envoy 返了错误页）。**NEVER 改成 catch 住返 0** —— 那会把「对端契约变了」伪装成「该用户没有交易记录」，分页总数恒 0、APP 列表永远只剩第一页且日志里一条错都没有 |
| `GateTxnPayClient.java:146` | IF8A-41 统计源表是 `GATE_TXN_PAY`（同时有原价 / 票价 / 超时费 / 实付四个量，单表可算全）。**NEVER 退回 ticket-server 用 `QRCODE_TXN_DETAIL` 自算** —— 那张表没有 `ORIGINAL_FARE`，只能拿超时费冒充优惠（2026-09-10 修正的语义错位） |
| `GateTxnPayClient.java:195` / `:215` | `recoverOfflineFare`（离线码金额补偿）/ `pushMetroTransfer`（公交换乘推送）供 web-admin Quartz 调，对端 2.0.73 起才有这两个端点（此前是模块内 `@Scheduled`）。两者**同步跑完一轮才返回**，`sys_job` 的「禁止并发」才有意义 —— 对端改成异步受理即返回时禁并发会失效；**对端恒返 `retCode=0000`**（本轮扫表异常属可自愈），结论只在 `retMsg` 里，**NEVER 为了让 `sys_job_log` 更「灵敏」而要求对端把可自愈错误返成非 0**。两个方法语义完全一致、**改一个 MUST 看齐另一个** |
| `GateTxnPayClient.java:230` / `:245` | `convergeDebitStatusForSupplement` 与上面两个 `/internal/**` 不同：**不是补偿批处理、对端也不恒返 `0000`**，调用方 MUST 按 `retCode` + `converged` + `debitStatus` 三者分支（判据在 `GateTxnPayDebitConvergeRespDTO` 类注释，见 §附二.5），**NEVER 只看 `retCode`**。**本方法不吞异常**（网络不可达时原样抛给调用方 = `Unreachable`，调用方 MUST 不改本地状态、留补偿重入），**NEVER catch 后返 null 或造假业务码**；本方法**不带 headers 形参**（调用方是业务链路、traceId 由观测自动带出），将来要加内部令牌 **MUST 新增重载、NEVER 改签名** |
| `GateTxnPayClient.java:175` | IF8A-26 补款下单**已随补款功能迁入 face-pay-server**（2026-09-15），本 Client 里同名方法已删除，改走 `facepay/FacePayClient.java:13` / `:30`（`retCode` 8001 参数问题 / 8003 订单状态或金额校验不通过，**两者都不应重试**） |
| `account/AccountClient.java:119` | IF8A-75 补建解约申请前用它取 `cardId` / `cardType`（权威来源是 `APP_USER_PAY_CHANNEL`，见 §附二.5 那条）；未找到返 `8004`，**MUST 判 retCode 而不是只判字段空** |
| `AccountClient.java:141` | `syncPayAccountId`（ADR-D32）**MUST 当「允许失败」处理**：只回写一列展示值，权威值始终在支付域 `APP_PAY_SIGN_INFO`，丢一次不影响任何业务动作（IF8A-77 仍会自行取值）。调用点 **MUST catch 全部异常只记日志、NEVER 让它把已提交的签约结果翻成失败**；命中 0 行时账户域返 `8004` 而不是抛错（签约先于开卡是合法时序），仍 **MUST 判 retCode、NEVER 假定「没抛异常就是写成功」** |
| `AccountClient.java:225` / `:256` | 两个带 trace 头的重载专供 web-admin Quartz；`ProxyWebClient` 只透传 `authorization`、不注入 trace 头，**不传就 traceId 断链、前台「执行日志」查不到下游** |
| `alipay/account/AlipayAccountClient.java:19` | 配置键用**嵌套默认值** `service.alipayAccount.url` → `service.account.url`，**NEVER 退回只读后者**：`service.account.url` 在不同模块含义不同（`fep-alipay-server` 把它配成 alipay-account 地址，而 `ticket-server` 的同名键指向**真 account-server**），于是 ticket-server 的 `CardDataHandler` 按 `thirdUserId` 查支付宝用户时一直打错目标，且它早已配好的 `service.alipayAccount.url` 没有任何读取方（2026-09-11 定位）。`fep-alipay-server` 没有该键会自动回落、**行为不变、不需要改那边配置** |
| `alipay/paysign/AlipayPaySignClient.java:151` / `:161` | 销卡批处理**服务端单次只处理一批（上限 200 条）**，调用方 **MUST 反复调用直到 `scanned` 为 0 才算排空**；不抛业务异常，MUST 先判 `resultCode`，返 null 说明 HTTP 层没通；带 trace 头的重载口径同 `PaySignClient.processTermination` |
| `AlipayPaySignClient.java:287` | 支付宝出行欠费**只落 `ALIPAY_PAY_LOG`、`GATE_TXN_PAY` 里没有对应行**，因此判定「该卡欠费是否结清」**MUST 同时问本接口与 `GateTxnPayClient.hasUnsettledOrderByCard`，缺一个就漏判**；下游在查询未真正执行时把 `hasUnsettled` 置 true，**NEVER 当成「已结清」** |
| `collectpay/CollectPayClient.java:88` / `:125` | `/internal/app-order` 登记的**幂等键是 `orderNo`**（对端按订单号回查、已登记就返成功），因此可被补偿任务无限重放 —— 这是「先落本地 PENDING、提交后再同步」那套 outbox 成立的前提，**NEVER 让对端改成「重复即报错」**。查询方法在**网络失败时 MUST 抛异常、NEVER 返回「查不到」**（两者处置方向相反，见 §附二.5 `AppPayOrderResultRespDTO`） |
| `f2f/F2FClient.java:85` | 四个通知接口请求体都是空、只有路径不同，统一在此发出；请求体**保持 `null`、NEVER 改成空 Map** —— 下游 `NoticeAppTask` 的入参形态未经验证，改动等于改契约（**与 `PaySignClient` 那组「body MUST NOT 传 null」方向相反、NEVER 合并理解**：那组走 `postJsonAndGetResponse`，这条走的是另一条发送路径） |
| `para/ParaClient.java:75` / `:114` | **零字段 DTO 场景 MUST 改送空 JSON 对象**：`RequestLineCodeListReqDTO` / `RequestLineStationCodeVersionReqDTO` 都是零字段类，直接 `bodyValue(request)` 会被 WebClient 判定为无可用编码器并抛 `UnsupportedMediaTypeException: Content type 'application/json' not supported for bodyType=...`，**请求根本发不出去、响应退化成 UUID retCode**；入参保留在签名上只为兼容调用方，**NEVER 改回 `postJsonAndGetResponse(url, request)`** |
| `ParaClient.java:54` | 带 trace 头的重载供 web-admin Quartz，口径同 `PaySignClient`；⚠️ `ProxyWebClient` 不会自动注入任何 trace 头（只从 MDC 取 `authorization`），**这个重载是必需的、不能指望框架兜底** |
| `cardpool/CardPoolClient.java:22` / `:110` / `:124` | 三个方法都返回结果对象而不是 `null` / `false`，调用方 **MUST 按 `CardPoolOutcome` 分流**（池空 / 参数或票种被拒 / 远端不可达），**NEVER 再统一报成「无可分配逻辑卡号」**。释放已幂等（重复释放同一 `reservationId` 仍成功；已 `ASSIGNED` 的不允许释放、返 REJECTED），`CALL_FAILED` 时**不必强行重试到成功**，预占超时回收会兜底。卡池维护端点**只做受理**（提交给单线程维护池后立即返回，ACC 申请 / FTP 下载 / 十万行入库都在服务端后台跑），**返回成功不代表这一轮已导完**；`data.accepted=false` 表示上一轮未结束、本轮被丢弃，这是**正常限流、NEVER 因此抛异常告警**（否则前台调度日志长期一片红） |
| `recon/ReconClient.java:26` | 分片上传走 `application/octet-stream` **流式**发送（`BodyInserters.fromResource`）：**NEVER 改成读成 `byte[]` 再发**（单片上限 128MB，读成数组等于每片一次 128MB 堆分配、并发几片就 OOM），也 **NEVER 改成 JSON**（Base64 放大 33%，且 400 万条明细无法整体入内存）。本客户端**失败即抛异常、不返回 false**，调用方 `ReconPartSink` 据此中断本次抽取并声明失败，**NEVER 吞异常继续写下一片** —— 那会产生分片号空洞、最终在生成阶段报「分片序号不连续」而整批失败 |
| `ReconClient.java:81` / `:94` / `:119` | `runDailyBatch` 会**阻塞到批次收口或超时**（服务端 `recon.orchestration.run-timeout-millis` 默认 4 分钟 < 本客户端 `getResponseTimeout()` 的 5 分钟）；无参重载 MUST 委托到带 headers 的那个、**NEVER 让两个重载各写一份请求逻辑**。分片按 `(batchId, source, fileType, partNo)` 幂等：哈希相同返首次回执、不同则拒收，**重试 MUST 用同一 partNo 与同一文件内容** |
| `recon/ReconExportClient.java:17` | 本类**不绑定单一目标地址**（三个源地址各不相同），复用 `URLDynamicRouter` 的做法：**baseUrl MUST 保持空串、NEVER 填 `service.recon.self-url` 之类占位地址**。两层理由：① `InternalMicroHttp.logRequest()` 打的是无条件 `joinUrl(getBaseUrl(), url)`，占位 baseUrl 会让 INFO 日志出现 `url=http://127.0.0.1:9112/http://gate-txn-pay-server-...:30019/internal/recon/export` 这种双份地址（2026-09-11 端到端实录），把人往「打到自己身上」误导；② 能否忽略 baseUrl 取决于 `DefaultUriBuilderFactory` 的实现细节（绝对 URL 带 host 时才丢弃 baseUri），依赖它属**隐式契约** |
| `ReconExportClient.java:44` / `:54` | **下发时固定带 `X-Vlogs-Capture: 1`（2026-09-14 新增，NEVER 删）**：三个源的 INFO 能不能进 VictoriaLogs 取决于其 MDC 有没有该键，而 `ProxyWebClient.addAuthorizationToHeader` **只转发 `authorization`**，web-admin 发出的该头到 recon-server 这一跳就断了；少这一行时按 traceId 反查只能看到 web-admin + recon-server 两段、三个源的 INFO **一条都查不到**（2026-09-14 实测）。写死 `1` 是因为本端点**每天每源一次**、量级可忽略，**NEVER 把这个写法照搬到高频业务端点**（等于把该链路全部 INFO 灌进日志库） |
| `ReconExportClient.java:92` / `:131` | `dispatchExport` **从不抛异常**（地址非法 / 不可达 / 响应无法解析都返 `accepted=false` 带原因）—— `ReconOrchestrationService.dispatchSource` 依赖这个语义把来源逐个标 FAILED 后继续下发其余源，**抛异常会让同一轮里剩下的源全部漏发**；`sourceBaseUrl` **缺 scheme 即当配置错误直接拒绝**，NEVER 放过去让 WebClient 当相对路径拼到空 baseUrl 上 |
| `recon/ReconPartSink.java:23` / `:91` | 用法固定 try-with-resources 且成功路径 **MUST 显式 `commit()`**；没走到 `commit()` 就 `close()`（抛异常、提前 return）时本类会向 recon-server **声明失败而不是静默退出** —— 否则该源永远停在 `EXPORTING`、整批对账挂死等不到收齐。**NEVER 把全部记录缓存到 `List` 再一次性写**（400 万条 × 100 字节 = 400MB 堆），本类只持有 1MB 级 `BufferedOutputStream`、内存与数据量无关；行内容 MUST 由 `ReconRecord.line(...)` 生成 |
| `recon/ReconPartUploader.java:9` / `:48` | 分片粒度「记录数或未压缩字节先到先滚」，默认 **20 万条 / 64MB**（400 万条明细约落 20~40 片）。**NEVER 把上限调到百万级**：单片越大重传成本越高，且服务端 `recon.max-part-bytes`（默认 128MB）会直接拒收。临时目录默认 `/home/javaapp/app/recon-export`，分片一经上送即删，磁盘峰值 = 并发文件类型数 × 单片上限 |
| `transquery/TransQueryClient.java:21` | 三个方法与 `TicketClient` 同名方法**路径、请求 DTO、应答 DTO 全部逐字相同**，唯一差别是 baseUrl；另起 Client 而不是改 `TicketClient` 的 baseUrl，是因为 ticket-server 上还有乘车码状态机、自助补站等一批接口没迁走。**ticket-server 那三个同路径端点仍在、未删**（过渡期双活），回滚只需把调用点换回 `ticketClient`；两边同时在跑期间 **MUST 保证 `app.trans.*` 五个键取值一致**，否则同一笔订单在新旧链路会返回不同商户号。支付宝行程与日票乘车记录**故意不在这里**（还没迁进 trans-query-server、阻塞在支付宝 pay-sign 新表），仍 MUST 走 `TicketClient` |

### 附二.9 rpc —— `@EnableRpcXxx` 装配注解（18 个）

`EnableRpcAccount` / `AlipayAccount` / `Blacklist` / `CardPool` / `CollectPay` / `DailyTicket` / `F2f` / `FacePay` /
`GateTxnPay` / `IndustryData` / `Key` / `Para` / `PaySign` / `Recon` / `Route` / `Security` / `Ticket` / `TransQuery`。

- 新增跨服务调用的三件套（缺一不可）：① 在对应 Client 加方法；② 调用方启动类加 `@EnableRpcXxx`；③ 调用方配置补 `service.xxx.url`。
- **`@EnableRpcTransQuery` 与 `@EnableRpcTicket` 不互斥、通常成对出现**（`EnableRpcTransQuery.java:7`）：交易列表 / 统计 / 详情
  走前者，支付宝行程与乘车码状态机等仍留 ticket-server。加了它 **MUST 同批补 `service.transQuery.url`**，
  否则 baseUrl 退化成默认服务名 `trans-query-service`、**集群里 DNS 解析不到**（这是 AGENTS.md §8「键缺失」那类问题的成因）。

### 附二.10 resource/micro —— `sql-datasource`（Druid 配置与两条孪生陷阱）

- **两条孪生陷阱同源于 `sql.properties:40` 的 `spring.datasource.druid.filter.wall.enabled=true`**（全部服务共用），
  Druid WallFilter 默认 `commentAllow=false`：① **NEVER 在 SQL 正文写注释**（`--` 或 `/* */`）——
  抛 `SQLException: sql injection violation ... comment not allow`，语句**静默失效**（已发生：`PayTxnDetailMapper.updatePayCallback`
  的 WHERE 内两行行注释让支付成功回调的 UPDATE 全部失败、`PAY_STATUS` 长期卡 `PROCESSING`，2026-08-25 修复）；
  ② **NEVER 写 `where 1 = 1` 打头 + 全部谓词都是可选 `<if>`** —— 条件全空时恒真条件成为唯一谓词，报
  `sql injection violation ... select alway true condition not allow`，MUST 用 MyBatis `<where>` 标签
  （已发生：`account-server` 的 `UserItpRegInfoMapper.countGroupByCardType`，2.0.73 修复）。
  **NEVER 靠放宽 `wall.config.comment-allow` 绕过**（全局降低注入防护等级）；注意 **`dm.properties:5` 在达梦环境关闭了
  WallFilter**，因此这两类缺陷**只在 Oracle 环境暴露**。
- **两个超时是「双层保护」，改一个 MUST 想清另一个**（原注释已迁出、配置行保留）：`sql.properties:46`
  `druid.query-timeout=30`（池级兜底，未显式 `setQueryTimeout` 的语句 30s 超时；业务显式设置的如对账抽取会覆盖）+
  `oracle.properties:14` 的 `oracle.jdbc.ReadTimeout=10000`（socket 读 10s）。后者的真实目的是**限制单次慢 SQL 的 pin 时长上限**：
  ojdbc8 的 `PhysicalConnection` / `OracleStatement` 大量方法是 `synchronized`，JDK 21 未落地 JEP 491，虚拟线程在
  `synchronized` 内阻塞会 pin 载体线程；10s 读超时让 DB 响应超时的语句直接抛 `SocketTimeoutException`、pin 的载体线程立刻释放。
  已在 2026-08-26 pay-sign-server 生产事故中观察到「对端 17ms 答完、本端 287s 才处理」的形态，根因即载体线程被 pin 满。
- **`sql.properties:51` `remove-abandoned-timeout=300`（5 分钟）是刻意降下来的**：默认 30 分钟在虚拟线程环境下太长，
  pin 满载体线程前连接不会被回收。长耗时批处理（对账抽取等）**已显式切到 `newFixedThreadPool` 平台线程上跑、不受此限制**。
- 其余现状：`stat-view-servlet` 默认开启且 `login-username/password` 都是 `admin`（`sql.properties:36~37`）——
  属**上线前 MUST 处置**的暴露面，见 §矛盾与待裁决。多数据源按 `other.sql.double-datasource` + `ConditionalOnDoubleDatasource`
  条件装配；`SqlAudit` 由 `other.sql.audit` / `other.sql.rows.warn=1000` 控制慢查询与大结果集告警。
  **`SqlConfiguration.java:41` 里有一个共享 `@Scheduled`**（`other.monitor.resetAllMetersCron`，见 §附二.12）。

### 附二.11 resource/micro —— `mybatis-adaptor`

- 只有三个类：`EnableDefaultMybatisAutoConfig`（业务模块启动类必加）、`DefaultMybatisConfiguration`、
  `DynamicMybatisConfiguration`；**当时全模块只有 7 行流程性注释、无任何约定性说明**，因此这里记的是**实测形态**而非迁出文本。
- `persistent.properties:1` 的 `mybatis.mapper-locations=classpath*:/mappers/*.xml` 是**默认值**，
  而多数业务模块把 mapper 放在 `resources/mapper/`（单数）并在自己的 `application.properties` 里覆盖该键 ——
  **新建模块时 MUST 确认这两者对得上**，否则表现为「mapper XML 明明在、方法却报 `Invalid bound statement`」。
- 数据访问 **MUST 走 `mybatis-adaptor` + mapper XML，NEVER 写原生 JDBC 或 MyBatis 注解 SQL**（AGENTS.md §5.1）。

### 附二.12 resource/micro —— web 组件（`WebAutoConfig` / `FirstFilter` / 两个观测切面 / `web.properties`）

- **`WebAutoConfig.java:18` 的 `@EnableScheduling` 是全局生效的**（该类还带 `@EnableAspectJAutoProxy` / `@EnableAsync` /
  `@ComponentScan` 三个包 / `@PropertySource("classpath:web.properties")` / `@AutoConfigureBefore` Knife4j）。
  **只要模块依赖了 micro-web（实测 31 个 pom 引用），调度容器就在跑**，与该模块自己有没有 `@EnableScheduling` 无关 ——
  排查「我没开调度为什么有定时任务」MUST 先看这一行，**NEVER 断言「本模块没开 `@EnableScheduling` 所以不会有 `@Scheduled`」**。
- **公共构件自带 3 个共享 `@Scheduled`**（2026-09-16 逐个实测，全仓 micro 侧只有这三处）：
  `micro/monitor/prometheus/ResetMetersJob.java:35`（`other.monitor.resetAllMetersCron`，默认 `0 0 2 * * ?`）、
  `ResetMetersJob.java:55`（`other.monitor.logAllMetersCron`，默认 `0/30 * * * * ?`）、
  `micro/datasource/SqlConfiguration.java:41`（同 `resetAllMetersCron`）。它们**在每个依赖 micro 的服务里都跑**，
  统计「某模块有几个 `@Scheduled`」时 **MUST 声明是否含这三个**（AGENTS.md §2.2.1 那份清单是**业务侧口径、不含它们**）。
- **`FirstFilter`（`micro/web/filter/FirstFilter.java`，`doFilter` 内约 :88~:93）把全部请求头名 `toLowerCase()` 后
  逐个 `MDC.put(key, value)`**。三条连带结论：① **NEVER 自造 `traceId` 请求头** —— 会落到 `traceid` 键、与 log4j2 的
  `%X{traceId}` 大小写对不上、**永远不显示**（这条在 5 个 rpc Client 的注释里各写了一份，源头就是这里）；
  ② `X-Vlogs-Capture` 正是靠这里变成 MDC 的 `x-vlogs-capture` 才能命中 appender 的 `ThreadContextMapFilter`；
  ③ **`authorization` 也进了 MDC**，因此 VictoriaLogs 的 event template **MUST 只输出 `traceId` / `spanId` 两个白名单键**，
  **NEVER 改成全量输出 MDC**（等于把令牌送进日志后端）。
- **两个观测切面 MUST 用 `observation.start()` + `openScope()` + `catch (Throwable e) { observation.error(e); throw e; }` +
  `finally observation.stop()`，NEVER 回退成 `observation.observe(Supplier)`**（`micro/monitor/trace/MapperAspectToTrace.java:32`
  与 `ServiceAspectToTrace.java:32`，2026-09-14 修，ADR-D53）。`observe` 收的是 `Supplier`、不能抛受检异常，原实现在 lambda 里
  `throw new RuntimeException(e)` 把真实异常包了一层，后果是**调用方按类型 catch 一律失效** ——
  本项目幂等兜底全靠 `catch (DataIntegrityViolationException / DuplicateKeyException)`，包一层后那些 catch 永远进不去、
  异常直冒到全局处理器变成 500。**只在打开 tracing 的模块中招**（`shouldSkipAopTraceLogic()` 短路其余模块），
  因此**同一份业务代码在 account-server 上正常、在 card-pool-server 上就崩**：2026-09-14 并发开户实测，卡池 `reserve`
  明明写了 `catch (DataIntegrityViolationException)` 去回查兄弟请求的预占，却因本条被跳过，并发同 `businessId` 的第二条请求
  直接 500、APP 收到「暂无卡数据资源」。**这两处各保留了一行式护栏注释，见 §附二.14。**
- **`web.properties` 里六个「一改就全局生效」的键**：`:1` `server.port=8080`（业务模块各自覆盖）、
  `:6` `spring.threads.virtual.enabled=true`（**全服务虚拟线程默认，日志里 `tomcat-handler-N` 即虚拟线程命名**）、
  `:3` `spring.mvc.throw-exception-if-no-handler-found=true` + `:4` `spring.resources.add-mappings=false`
  （**这两条正是「404 变成 HTTP 200 + UUID retCode」的机理**：无 handler 抛异常 → 全局处理器兜成 UUID 码）、
  `:78` `management.tracing.enabled=false`（**默认关闭**，因此 `%X{traceId}` 那一列默认恒空；打开 MUST「三行成组」）、
  `:48/:49` 上面那两个 cron。`:81~:96` 的 OTLP 上报配置**整段注释掉且 NEVER 删除**（2026-09-08）：endpoint 指向的
  collector 在集群里不存在，任何打开 tracing 的服务都会被 `OtlpAutoConfiguration` 建出 exporter、导出线程每批抛
  `UnknownHostException` 刷日志（pay-sign 已实测）；且其中 6 行键名在 Spring Boot 3.2.6 里**根本不存在**
  （真实前缀是 `management.otlp.tracing.*`、只有 endpoint/timeout/compression/headers 四个键），**从来没生效过、恢复上报时勿照抄**。
- `log4j2` 相关知识（`CustomLoggingConfiguration` 的 ClassLoader 坑、`VictoriaLogsAppender` 的四条设计约束、
  两级过滤与 `AppenderRef` 引用层短路）**已在上一节 §3.1.1 成文**，本次不重复；`CustomLoggingConfiguration.java:54~60`
  与 `VictoriaLogsAppender.java:29` 的注释按同一口径删除，**NEVER 退回不带 ClassLoader 的 `Configurator.initialize` 重载**
  这条判据以 §3.1.1 那段为准。

### 附二.13 公共构件改动的连带影响（版本号锁死 + 逐个重建镜像）

- **`model` / `rpc` 的 `<version>` 锁死在 2.0.0 / 2.0.1**（用户 2026-09-07 明确要求，AGENTS.md §7）：被 21 个模块 pom 的
  `<model.version>` / `<rpc.version>` 引用，升一次要连带改 21 个文件、漏一个即构建期报「找不到依赖」。
  连带三条硬约束：① 公共构件里 **MUST 只增方法、NEVER 改已有签名**（`RpcOutcome` 的落地方式、
  `GateTxnPayClient.convergeDebitStatusForSupplement` 的「要加令牌就新增重载」都是这条的产物）；
  ② 兼容壳（`updatePaySignDisplayAccount`）只能留、不能删；③ 版本号相同意味着**镜像里的 class 与本机 `~/.m2` 可能不一致**。
- **改完 MUST 逐个重建受影响模块镜像**：`mvn install` 只更新本机 `~/.m2`、对已在跑的 Pod 零影响。两类后果不同 ——
  给 DTO **加字段**时 Fastjson2 宽松模式**静默丢弃**目标类里不存在的字段（接入层用旧 class 一反序列化就没了，下游收到 `null`；
  已发生：IF8A-41 加 `cardType` 只重建 ticket-server，日志里 `cardType='null'`，重建 `itp/fep-app:2.0.77` 后立即正常）；
  改**行为语义**时更严重 —— ADR-D53 修两个观测切面后只重建了 `card-pool-server:1.0.17`，**其余 6 个 tracing 模块线上仍是旧切面、
  幂等兜底 catch 依然失效**。判据：「谁受影响」= 依赖该构件且真正走到那段代码的全部模块；
  **NEVER 认为「`mvn install` 过了」就等于生效**，只有重建镜像 + `kubectl set image` 才算。
- `resource/micro` **不在根 pom 聚合列表**，改后 MUST 先单独 `mvn install` 该子模块再构建业务模块。

### 附二.14 代码里保留的两处一行式护栏（NEVER 删）

删这两行会**直接诱发回退到已知事故写法**，因此本次刻意留在代码里，与本节形成双写：

| 文件 | 保留内容 | 删掉的后果 |
|---|---|---|
| `resource/micro/sql-datasource/src/main/resources/sql.properties`（`wall.enabled=true` 上方一行） | `# WallFilter 拦 SQL 注释与恒真条件；NEVER 放宽 wall.config.comment-allow，详见 docs/architecture/common-components.md` | 下一个人为了让带注释的 SQL 跑通，最省事的动作就是加一行 `comment-allow=true`，**全局降低注入防护等级** |
| `resource/micro/web/.../monitor/trace/MapperAspectToTrace.java` 与 `ServiceAspectToTrace.java`（`around` 方法上方各一行） | `// NEVER 回退成 observation.observe(Supplier)：会把异常包一层、幂等兜底 catch 全部落空（ADR-D53）` | `observe(Supplier)` 写法更短、看着更「干净」，回退后**只在打开 tracing 的模块上炸**，单测与编译都发现不了 |

### 矛盾与待裁决

1. **`stat-view-servlet` 默认开启 + 弱口令**（`sql.properties:32~37`：`enabled=true`、`login-username/password=admin`、
   `reset-enable=true`、`allow=` 为空即不限 IP）。与 AGENTS.md §5.2「敏感配置」冲突，**上线前 MUST 处置**（关掉或按 IP 白名单 + Secret 注入）。
   本次只迁移注释、**未改任何配置值**。
2. **`ReconClient.java:40` / `ReconExportClient.java:42` 的 `X-Recon-Token` 已整段删除**（用户 2026-09-11 要求，开发测试阶段），
   与 §5.2「新增状态变更型接口 MUST 有鉴权」冲突；恢复时**两端 MUST 同时改回**。属待办、不是断言对象。
3. **`web.properties:6` 的虚拟线程默认 `true` 与 ojdbc8 的 `synchronized` 天然冲突**：现有缓解是三层
   （`ReadTimeout=10s` / `query-timeout=30s` / `remove-abandoned-timeout=300s` + `docker/Dockerfile` 的 `JAVA_TOOL_OPTIONS`），
   但**根因（JDK 21 无 JEP 491）无法在本项目内消除**。是否给重 DB 模块单独关掉虚拟线程，**尚未裁决**。
4. **`rabbitmq-adaptor` 留着不删**：`src/main/resources/mq.properties` 含主机与 guest 口令，源码与 properties **零注释**、
   没有任何说明它为什么还在。建议整体移除（AGENTS.md 已写「NEVER 在业务模块引用」），**删不删待裁决**。
5. **`persistent.properties` 的 `mapper-locations` 默认值（`/mappers/`，复数）与多数模块实际目录（`/mapper/`，单数）不一致**，
   靠各模块覆盖生效。是「统一成一个」还是「保持各自覆盖」**待裁决**；在裁决前 **NEVER 只改公共默认值**（会打挂所有没覆盖的模块）。
6. **`countTransList` 的裸标量契约**（`GateTxnPayClient.java:100`）没有编译期保护，只靠「异常消息里带上对端原文」兜。
   要不要给它套 `CommonResult` 属**跨模块契约变更**，需与 gate-txn-pay / trans-query 两侧一起定，**待裁决**。

### 墓碑清单（迁出后仍需盯的「NEVER 回退」条目）

「墓碑」= 注释形态为「NEVER 回退 / NEVER 改回 / 那版口径已作废 / NEVER 加回」的条目，作用是拦住一次**已被实证推翻**的改动。
本次从三个模块迁出 **12 条**（阶段一那张表已列 43 条断言候选，两者互补、**都 NEVER 删**）：

| 条目 | 曾经的错写法 | 判据来源 |
|---|---|---|
| 观测切面 `observe(Supplier)` | 异常包一层 ⇒ 幂等兜底失效 | ADR-D53（已留一行式护栏） |
| `ProxyWebClient` 覆写 `getResponseTimeout()` 读实例字段 | 父类构造期读到恒 0 / null | `ProxyWebClient.java:96/:114` |
| `CardTypeMapping` 日票 `05` / NFC `03`+`04` 单值映射 | 恒命中 0 行且 `retCode=0000` | 2026-09-10 订单 `GT20260910164625246000013` |
| `TripDataDTO` 把 `OVERTIME_AMOUNT` 映射到 `totalDiscount` | 12 元超时费显示成「已优惠 12 元」 | 2026-09-10 校准 |
| `AdviceOptEnum` 把 `020` 当「更宽松版 005」进资金白名单 | 给没进过站的卡补出站、自相矛盾 | 用户 2026-09-14 第三次裁决 |
| `NotifyVerifyResultRespDTO` 回填 `ACTUAL_TIMES` | `-99`（不限次）透到 APP 与 `COUNTING_TIMES` | 2026-09-10 订单 `GT20260910143159899000084` |
| `UpdatePhoneReqDTO` 用 `newMsisdn` | 自造命名、与规范不符 | 2026-09-09 与规范核对 |
| `ParaClient` 零字段 DTO 直接 `bodyValue(request)` | `UnsupportedMediaTypeException`、请求发不出去 | `ParaClient.java:75/:114` |
| `AlipayAccountClient` 只读 `service.account.url` | ticket-server 一直打错目标 | 2026-09-11 定位 |
| `ReconExportClient` 给 baseUrl 填占位地址 | 日志出现双份 URL、误导排查 | 2026-09-11 端到端实录 |
| `ReconExportClient` 删掉写死的 `X-Vlogs-Capture: 1` | 三个源的 INFO 一条都查不到 | 2026-09-14 实测 |
| `GateTxnPayClient.countTransList` catch 住返 0 | 分页总数恒 0、APP 只剩第一页、无一条错误日志 | `GateTxnPayClient.java:100` |

### 覆盖率自评

**口径先说清**：分母是「**承载知识的注释**」（含 MUST / NEVER / 实测 / 事故 / 墓碑 / 判据字样的注释块），
不是全部注释行 —— 字段级单行 Javadoc（`/** 拉黑原因。 */` 这类）属结构性说明，本来就该留在代码里。

| 指标 | 数值 |
|---|---|
| 本节新增文档行数 | **334 行**（`common-components.md` 由 379 行 → **715 行**） |
| 抽取条数 | **144 条**（表格行 106 + 要点 32 + 待裁决 6），分 16 个小节 |
| 删除注释行数 | **1709 行**：`model` 4366→3233（−1133）、`rpc` 1225→717（−508）、`resource/micro` 181→113（−68） |
| 受影响文件 | **208 个 `.java` 被改写**（扫过 450 个，含 316 个仍有注释的文件） |
| 知识注释残留 | **1888 行 → 27 行**：其中 **2 行是刻意保留的护栏**（两个切面），余下 25 行是关键词误命中（如「本类继承自」命中「本条/本类」、「原因」命中判据词），逐条肉眼确认**无知识丢失风险** |
| 不变量自证 | **450 个文件 × 2 轮比对，0 处不一致**（详见回报） |
| 矛盾 / 待裁决 | 6 条 | 
| 墓碑 | 12 条（阶段一另有 43 条断言候选表，两份互补） |

**明确未覆盖的部分（不是漏做，是本次划定的边界）**：

1. **`.properties` 里的注释未删**（`sql.properties` 两段超时说明、`oracle.properties` 的 8 行 pin 说明、
   `web.properties` 的 OTLP 那 9 行）。知识已在 §附二.10 / §附二.12 **双写**，但原注释保留 ——
   它们紧贴着「被注释掉的配置行」，删掉会让下一个人直接取消注释踩回旧坑；这是**有意保留**，
   与「两处一行式护栏」是同一类判断。要清就得连配置一起返工，**属独立任务**。
2. **`rabbitmq-adaptor` 无可迁**：源码与 `mq.properties` **零注释**，为什么留着没人写过（已进 §矛盾 4）。
3. **字段级单行 Javadoc 全部保留**（约 3100 行，占剩余注释的大头）：它们是「这个字段是什么」，
   不是「这里为什么这么写」，迁进文档只会让文档变字段字典。
4. **`model` 的 `alipaytrip` / `para` / `frs` / `autoclear` 等包**里那批**纯字段注释 DTO**（如
   `AlipayPayLogDTO` 96 行注释、`AlipayTripTravelRecordDTO` 63 行）本次只做了格式收敛、没有条目产出 ——
   因为它们的注释里**一条 MUST / NEVER 都没有**（实测知识行为 0）。
5. **阶段一那份「墓碑注释→断言测试」的转化建议未落地**（43 条候选一条都还没写成测试）。
   本次是文档迁移、不含写测试，**NEVER 把本节的存在当成「已有回归防线」** —— 目前唯一的防线仍是人读文档。
