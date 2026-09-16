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
