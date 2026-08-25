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
| 运营端返回封装 | `ResultVO` + `ResultMapper`（ok / error / illegalParams / signError） | `common/response/` |
| 卡类型判定（含日票判定） | `CardTypeCodeEnum`（含 `isDailyTicket`）、`CardTypeMapping` | `model/enums/`、`model/app/` |
| 乘车码状态机 | `QRCodeStatusEnum`（含 `isClosedLoop` / `isOpenLoop`） | `model/ticket/enums/` |
| 交易类型 / 设备类型 / 发行渠道 | `TrxTypeCodeEnum`、`DeviceTypeEnum`、`IssueChannelCodeEnum` | `model/enums/` |
| 字节与十六进制转换（设备报文用） | `ByteConvert`、`ByteConvertUtil` | `common/util/` |
| 签约渠道判定 | `SignChannelUtils` | `model/utils/` |

### 1.3 约束

- 跨模块传输对象 **MUST** 定义在 `model`，**NEVER** 在业务模块内复制一份。
  已知反例：`acc-secure-server` 的 `accsecure.model.*` 与 `model/model/security/*` 存在 8 组重复 DTO，属技术债，勿模仿。
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
| `web` | `ProxyWebClient`、`GlobalControllerExceptionHandler`、`EnableExporterAutoConfig` | RPC 客户端底座、全局异常兜底、监控指标导出 |
| `rabbitmq-adaptor` | `EnableDefaultRabbitmqAutoConfig` | **历史遗留，项目不使用 MQ**。`src/main/resources/mq.properties` 含主机与 guest 口令，建议整体移除，**NEVER** 在业务模块引用 |

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
