# web-server / web 详细设计（管理后台）

> 对应 `AGENTS.md` §3.2 中 web-server、web 两行的「详情」。
> 本文档讲**技术结构**；运营页面接口该放哪个模块的约定见 [`../business/admin-web.md`](../business/admin-web.md)。

## 一、定位与关键事实

`web-server` 基于 **RuoYi-Vue3** 改造，前端在 `web/`。

**web-server 只在「定时任务」这一条路径上调用业务服务**（2026-09-07 更新，见 §八）：
- `web-admin/pom.xml:76` 依赖 `rpc`；`ServerApplication` 上有 `@EnableRpcAccount`、`@EnableRpcF2f`
- `web-admin/src/main/resources/application.properties:108-111` 有 `service.account.url`、`service.f2f.url`
- **HTTP 接口层仍不调用任何业务服务**：controller 里无 `XxxClient` 注入，运营查询接口依旧在业务模块的 `/page/**`
- 唯一 HTTP 工具 `web-common/.../utils/http/HttpUtils.java` 只被 IP 归属地查询 `AddressUtils` 使用

它与业务服务的另一个交集是**共用同一个 Oracle 实例**（`web-admin/src/main/resources/application.properties` 的 `jdbc:oracle:thin:@//...:1521/AFCITPDB`），但当前只读写 `sys_*`。

因此**它不是 BFF，也不是聚合层**，而是「权限/菜单后台 + 可配置定时任务调度器」。

## 二、子模块结构

子模块列表见 `web-server/pom.xml`。

| 子模块 | 职责 | 关键内容 |
|---|---|---|
| **web-admin** | 唯一可执行模块 + RuoYi 原生 controller 层 | 启动类 `web-server/web-admin/src/main/java/com/chinasofti/huateng/ServerApplication.java`；端口 **8080**，context-path `/`；**未设置 `spring.application.name`**（仅 `points.name=points`）；controller 分 `system` / `monitor` / `common` / `tool`；集成 Swagger |
| **web-system** | `sys_*` 域的 domain / mapper / service | `SysUserServiceImpl` 等；`src/main/resources/mapper/system/*.xml` |
| **web-quartz** | Quartz 调度与任务 CRUD | `quartz/controller/SysJobController.java` → `/monitor/job` |
| **web-generator** | 代码生成器 | `generator/controller/GenController.java` → `/tool/gen`，Velocity 模板 |
| **web-framework** | Spring Security / JWT、Druid 动态数据源、AOP | 日志 / 数据权限 / 限流切面，全局异常 |
| **web-common** | 基础设施 | 注解、`AjaxResult` / `BaseEntity` / 分页、工具类、XSS 与 Referer 过滤 |

### 依赖方向

```
web-admin → web-quartz / web-generator / web-system → web-framework → web-common
```

新增后台功能 **MUST** 遵循此方向：controller 放 web-admin，domain/service/mapper 放 web-system，通用切面与安全放 web-framework，**NEVER** 让 web-common 反向依赖上层。

## 三、鉴权与数据访问

- 鉴权：Spring Security + JJWT，`token.secret` + `token.expireTime` 配置在 web-admin。
  ⚠️ 现网 `token.secret` 是 26 位连续字母，强度极低；**MUST** 改为 `${WEB_TOKEN_SECRET:}` 由 Secret 注入。更换后所有已签发 JWT 失效，需低峰期执行。
- 数据源：Druid 主从（`spring.datasource.druid.master` / `slave`），slave 当前口令为空、未启用。
  ⚠️ 与业务模块的自研 `other.sql.*` **是两套完全不同的数据源装配**，配置写法不可互相复制。
- Druid 监控台 `statViewServlet` 已启用且口令为弱口令，建议关闭外网暴露。

## 四、数据表

- `sys_*`：用户 / 角色 / 菜单 / 部门 / 字典 / 参数 / 日志
- `gen_table`、`gen_table_column`：代码生成器元数据
- `QRTZ_*`：Quartz 调度（`web-server/sql/quartz.sql`）

⚠️ `web-server/sql/*-menu.sql` 中出现的业务表名（`TBL_TVM_ORDER_PAY`、`ALIPAY_USER_INFO`、`USER_ITP_REG_INFO`、`QRCODE_STATUS`）**只是菜单种子数据的注释**，web-server 后端没有对应实现，勿据此推断。

## 五、前端 web/

```
web/src/
  api/       login.js  menu.js  system/  monitor/  tool/  para/  trans/
  views/     页面
  layout/  components/  router/  store/(Pinia)  directive/  plugins/  utils/
  main.js  permission.js  settings.js  App.vue
```

- `api/system|monitor|tool` 对应 web-admin 的 RuoYi 原生接口；
- `api/para`、`api/trans` 是本项目新增的业务查询页面，其后端在 **para-server / ticket-server 等业务模块的 `/page/**`**，经反向代理转发（`web/vite.config.js`、`web/nginx.conf`）。
- 环境配置：`.env.development` / `.env.staging` / `.env.production`
- 规范：**MUST** 用 Vue 3.5 Composition API（`<script setup>`）+ Element Plus；新增页面 **MUST** 同时补菜单 SQL，写法参考 `web-server/sql/*-menu.sql`。

## 六、常见踩坑点

- web-server 端口 8080 与三个支付宝模块冲突，同机启动必冲突。
- 新增运营查询接口容易误放进 web-server（因为菜单在这里），但**后端约定在业务模块的 `/page/**`**。web-admin 虽已为定时任务引入 `rpc` 依赖，controller 层仍 **NEVER** 调业务服务，否则 web-server 会滑向聚合层、破坏现有边界。
- 代码生成器生成的模板是 RuoYi 风格（标准 MyBatis + `sys_*` 约定），**NEVER** 用它生成业务模块代码 —— 业务模块必须走自研 `mybatis-adaptor`。
- 多个 properties 明文含数据库与 Druid 口令，触碰 **MUST** 提示人工复核，**NEVER** 回显口令值。

## 七、定时任务（Quartz）跨服务调用

**需要运维可见、可改 Cron、可手工触发的定时任务建在 web-server**（前台「监控管理 → 定时任务」增删改查 + 调度日志）；跨服务动作由 `rpc` 模块的 Client 发出：

```text
前台（监控管理 → 定时任务） → web-server Quartz → XxxQuartzTask → XxxClient → 下游服务
```

已落地四条：`AccountQuartzTask → AccountClient → account-server`（`accountQuartzTask.invokeDemo()`，联调用）、`TBNoticeAppTask → F2FClient → collect-pay-server`（扫码取票通知 App 出票 / 故障 / 退款）、`TerminationQuartzTask → PaySignClient → pay-sign-server`（解约申请确认，`terminationQuartzTask.confirmTermination()`，业务口径「申请满 4 天才确认解约」见 [`../business/pay-sign.md`](../business/pay-sign.md) §解约申请满 4 天才确认）、`AlipayTerminationQuartzTask → AlipayPaySignClient → alipay-pay-sign-server`（支付宝出行销卡，`alipayTerminationQuartzTask.cancelCard()`，每天 2:00，链路见 [`../business/alipay-channel.md`](../business/alipay-channel.md) §支付宝出行销卡链路）。

2026-09-11 新增第五条：`ReconQuartzTask → ReconClient → recon-server`（日终对账，`reconQuartzTask.runDailyBatch()`，`sys_job` job_id 109，cron `0 30 2 * * ?` 每日一次，`@EnableRpcRecon` + `service.recon.url`）。**它是目前唯一一条「一次调用要跑几分钟」的链路**：recon-server 端同步建批次、下发抽取、再轮询推进到批次收口才返回（`ReconOrchestrationService.runDailyBatch()`），因此 `ReconClient.getResponseTimeout()` 被覆写成 5 分钟、服务端 `recon.orchestration.run-timeout-millis` 是 240 秒，**改任一处 MUST 保证「服务端超时 < 客户端超时」**，否则会出现「web-admin 记失败、recon-server 还在跑」。recon-server 侧**一个 `@Scheduled` 都没有**，频率完全由这条 cron 决定，详见 [`../business/recon.md`](../business/recon.md)。

2026-09-15 新增第六、七条：`GateTxnPayQuartzTask → GateTxnPayClient → gate-txn-pay-server`，两个方法各对应一条 `sys_job`（`gateTxnPayQuartzTask.recoverOfflineFare()` → `POST /internal/gate-txn-pay/offline-fare/recover`，`gateTxnPayQuartzTask.pushMetroTransfer()` → `POST /internal/gate-txn-pay/metro-transfer/push`；cron 均 `0 0/1 * * * ?`，`@EnableRpcGateTxnPay` + `service.gateTxnPay.url`）。**这两条是「模块内 `@Scheduled` 迁过来」的首例**（gate-txn-pay 2.0.73 起那两个 Processor 上已无 `@Scheduled`），因此有一条**别处没有的约束**：`fixedDelay` 语义在 cron 下无法表达，不重叠**只靠 `sys_job.concurrent='1'`（禁止并发）保证**，那一列改成 `'0'` 就是并发重复扣款；公交换乘推送的周期也因此由 10 秒变成 60 秒（用户 2026-09-15 裁决，属外部可感知变化）。两个端点**当前无鉴权**（同一裁决），上线前 MUST 恢复。建表 SQL 在 `scripts/20260915_sys_job_gate_txn_pay_compensate.sql`，详见 `docs/domain/decisions.md` ADR-D80。

> `AlipayPaySignClient` 的 Bean 由已有的 `@EnableRpcPaySign` 一并扫入（该注解的 `basePackages` 同时含 `com.chinasofti.huateng.rpc.paySign` 与 `com.chinasofti.huateng.rpc.alipay.paysign`），**无需**新增 `@EnableRpcXxx`；但地址键是独立的 `service.alipay-pay-sign.url`（注意是中划线，不是 `alipayPaySign`），漏配会落到默认服务名。

⚠️ **这不等于「业务模块不再有定时任务」**。模块内部的落库补偿仍按 AGENTS.md §5.1 用本模块 `@Scheduled` 扫表，**当前仍在跑的只剩两个模块**（2026-09-16 逐模块实测：行首锚定匹配、已排除 Javadoc 注释里的字样）：`face-pay-server`（**7 个、分布在 6 个类**，含补款 `converge` / `closeTimeout`）与 `collect-pay-server/.../task/SingleTicketRefundTask.java`（**4 个**）。已全部迁走、模块内 `@Scheduled` 现为 **0 个** 的是 **`pay-sign-server`**（其 10 个 `/internal/**` 补偿端点**全部由本处 `sys_job` 触发**，排查 MUST 查 `SYS_JOB_LOG`）与 **`gate-txn-pay-server`**（原 `SupplementOrderCloseProcessor` 的补款任务已连同该类**整体迁到 `face-pay-server`**；`MetroTransferPushTaskProcessor` 与 `OfflineFareRecoveryProcessor` 两个类留在本模块但已迁到本处 `sys_job` 120 / 121）。

⚠️ 本处旧记载「`collect-pay-server/.../task/NoticeAppTask.java`（3 个）」**已作废** —— 该文件仍在但**已无 `@Scheduled`**，collect-pay 的调度实际在 `SingleTicketRefundTask`、是 **4 个**；旧记载把 `pay-sign-server/.../service/AppNotifyService.java` 列为在跑也**已作废**（该模块 0 个 `@Scheduled`）。**NEVER 回退。** 两种方式的分工：**要人工可控（改 Cron / 暂停 / 补跑）的走 web-server Quartz，纯内部重试留在本模块 `@Scheduled`**；同一件事 **NEVER** 两边各建一份。

### 现行 `sys_job` 全量清单（2026-09-15 实测，16 条）

排查「某个补偿有没有跑」**MUST** 查 `SYS_JOB_LOG`。编号与 cron 以 `web-server/web-quartz/src/main/resources/sql/web-quartz-refund-compensate-job-migration.sql` 头部注释的 `SELECT JOB_ID, JOB_NAME, INVOKE_TARGET, CRON_EXPRESSION FROM SYS_JOB ORDER BY JOB_ID` 实测结果为准（**最大 job_id 是 123，不是 109**）：

| job_id | 名称 | invoke_target | cron |
|---|---|---|---|
| 1 / 2 / 3 | 系统默认（无参 / 有参 / 多参） | `ryTask.ryNoParams` / `ryParams` / `ryMultipleParams` | `0/10` `0/15` `0/20` |
| 4 | 解约申请确认 | `terminationQuartzTask.confirmTermination()` | `0 0 4 * * ?` |
| 5 | 支付宝出行销卡 | `alipayTerminationQuartzTask.cancelCard()` | `0 0 2 * * ?` |
| 6 | 签约结果通知补发 | `notifyCompensateQuartzTask.compensateSignNotify()` | `0 0/10 * * * ?` |
| 7 | 解约结果通知补发 | `notifyCompensateQuartzTask.compensateTerminationNotify()` | `0 5/10 * * * ?` |
| 105 | 黑名单可解除性盘点 | `blacklistReleaseInspectQuartzTask.inspect()` | `0 0 10,16 * * ?` |
| 106 | ACC 参数文件同步 | `paraQuartzTask.scanFtpPara()` | `0 2/10 * * * ?` |
| 107 | 卡池维护 | `cardPoolQuartzTask.runMaintenance()` | `0 0/5 * * * ?` |
| 108 | 签约展示账号同步补偿 | `accountQuartzTask.compensatePhoneSignSync()` | `0 0/5 * * * ?` |
| 109 | 日终对账 | `reconQuartzTask.runDailyBatch()` | `0 30 2 * * ?` |
| 120 | 离线码金额补偿 | `gateTxnPayQuartzTask.recoverOfflineFare()` | `0 0/1 * * * ?` |
| 121 | 公交换乘推送 | `gateTxnPayQuartzTask.pushMetroTransfer()` | `0 0/1 * * * ?` |
| 122 | 退款回查补偿 | `refundCompensateQuartzTask.compensateRefundQuery()` | `0 0/10 * * * ?` |
| 123 | 退款汇总跨表对账 | `refundCompensateQuartzTask.compensateRefundSummary()` | `0 15 * * * ?` |

⚠️ Quartz 用**内存 JobStore**：任务只在 web-admin 启动时由 `@PostConstruct` 从 `sys_job` 全量加载，**运行期直接 INSERT 不会生效**，MUST 重启 web-admin、或在后台对任务做一次修改保存才会注册进调度器；`misfire_policy='3'`（不补跑）意味着重启期间错过的调度不追补。其余列照现有行抄：`job_group='DEFAULT'`、`concurrent='1'`（禁止并发）、`status='0'`。

### 7.1 web-server 侧任务类

任务类 **MUST** 放在 `com.chinasofti.huateng.quartz.task` 包下 —— 这是 `web-common/.../constant/Constants.java:166` 的 `JOB_WHITELIST_STR` 唯一白名单，`ScheduleUtils.whiteList()` 据此校验 `invokeTarget`，放在别的包新增任务时前台直接报「违规」。

参考 `web-server/web-admin/src/main/java/com/chinasofti/huateng/quartz/task/AccountQuartzTask.java`：

```java
@Component("accountQuartzTask")
public class AccountQuartzTask {
    private final AccountClient accountClient;

    public AccountQuartzTask(AccountClient accountClient) {
        this.accountClient = accountClient;
    }

    public void invokeDemo() {
        CommonResult response = accountClient.quartzDemo();
        if (response == null) {
            throw new IllegalStateException("account-server Quartz 联调接口未返回响应");
        }
        if (!"0000".equals(response.getRetCode())) {
            throw new IllegalStateException("account-server Quartz 联调失败: retCode=" + response.getRetCode()
                    + ", retMsg=" + response.getRetMsg());
        }
    }
}
```

约束：
- 任务方法 **MUST** 是 `public` 无参（前台 `invokeTarget` 也支持字面量入参，但本项目现有任务都用无参）。
- **MUST 显式校验 `response == null` 与 `retCode != "0000"` 并抛异常**：不抛异常 Quartz 一律记「成功」，`sys_job_log` 里看不出失败。这与 AGENTS.md §5.2「返回值必须显式检查」是同一条要求。
- 任务类里 **NEVER** 写业务逻辑与 SQL，只做「调 Client + 判返回 + 抛异常」；补偿逻辑留在下游服务自己的表和状态机上。
- **下游服务的补偿扫表 NEVER 用 `@Scheduled` 与本处的 `sys_job` 并存**。两套调度源互不知情，改 cron 时只改一处就会出现「以为改了、实际另一套还在按老频率跑」。已落地的第一个真实补偿任务是 `accountQuartzTask.compensatePhoneSignSync()`（2026-09-11，触发 account-server `POST /phoneSignSyncCompensate` 重推 `USER_PHONE_CHANGE_LOG.SIGN_SYNC_STATUS` 为 `PENDING`/`FAILED` 的行），account-server 侧**刻意没有** `@Scheduled`。注意这条只约束**前台可配的**补偿；`collect-pay-server` / `pay-sign-server` / `gate-txn-pay-server` 模块内既有的 `@Scheduled` 保持不动（AGENTS.md §2.2.1）。

### 7.2 前台新增任务

「监控管理 → 定时任务 → 新增」，以 account 联调为例：

- 任务名称：`account Quartz 联调`
- 任务分组：`DEFAULT`
- 调用方法：`accountQuartzTask.invokeDemo()`（Bean 名 + 方法名，Bean 名取 `@Component` 里显式指定的名字）
- Cron：`0 0/5 * * * ?`（每 5 分钟）
- 执行策略：`放弃执行`；是否并发：`禁止`

保存后任务默认「暂停」。**MUST** 先「执行一次」，在「调度日志」确认 `retCode=0000` 后再切「正常」。

### 7.3 下游服务侧接口

下游按普通 Controller 暴露即可，无验签（仅集群内可达）。参考 `account-server/src/main/java/com/chinasofti/huateng/account/controller/task/TaskController.java`：

```java
@PostMapping("/quartzDemo")
public CommonResult quartzDemo() {
    log.info("收到由 web-server Quartz 定时任务发起的调用");
    CommonResult response = new CommonResult();
    response.setRetCode("0000");
    response.setRetMsg("调用成功");
    return response;
}
```

⚠️ 这类接口**没有鉴权**。只放「触发本服务内部扫表补偿」这类动作是可接受的；**NEVER** 让它接收外部可控的业务参数去改单笔用户数据（AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权与归属校验」）。同时下游被触发的逻辑 **MUST** 幂等 —— 前台「执行一次」和 Cron 重叠会重复调用。

### 7.4 rpc 侧与配置

`rpc` 模块对应 Client 补方法，如 `rpc/src/main/java/com/chinasofti/huateng/rpc/account/AccountClient.java:168`：

```java
public CommonResult quartzDemo() {
    return quartzDemo(java.util.Collections.emptyMap());
}

public CommonResult quartzDemo(Map<String, String> headers) {
    String result = postJsonAndGetResponse("/quartzDemo", new java.util.HashMap<>(), headers);
    return JSONUtil.toBean(result, new TypeReference<CommonResult>() {}, true);
}
```

⚠️ **供 Quartz 任务调用的 Client 方法 MUST 提供 `Map<String,String> headers` 重载**（见 §7.6）：`ProxyWebClient` 只自动透传 MDC 里的 `authorization`，**不会**注入任何 trace 头，不传就断链。无 headers 的重载保留给非 Quartz 调用方，内部委托给带 headers 的版本传空 Map，**NEVER** 让两个重载各写一份请求逻辑。

启动类 `web-server/web-admin/src/main/java/com/chinasofti/huateng/ServerApplication.java` **MUST** 加上对应的 `@EnableRpcXxx`（当前已有 `@EnableRpcAccount`、`@EnableRpcF2f`、`@EnableRpcPaySign`），否则 Client 不进容器、任务 Bean 启动即注入失败。注意 `@EnableRpcPaySign` 同时扫 `rpc.paySign` 与 `rpc.alipay.paysign` 两个包，所以 `AlipayPaySignClient` 不需要额外注解。

`web-admin/src/main/resources/application.properties:108-115`：

```properties
service.account.url=${ACCOUNT_SERVICE_URL:http://127.0.0.1:9098}
service.account.openLogger=true
service.f2f.url=${F2F_SERVICE_URL:http://127.0.0.1:58101}
service.f2f.openLogger=true
service.paySign.url=${PAY_SIGN_SERVICE_URL:http://127.0.0.1:9096}
service.paySign.openLogger=true
service.alipay-pay-sign.url=${ALIPAY_PAY_SIGN_SERVICE_URL:http://alipay-pay-sign-server:8080}
service.alipay-pay-sign.openLogger=true
service.recon.url=${RECON_SERVICE_URL:http://recon-server-bjzdy-svc.itp.svc:30034}
service.recon.openLogger=true
```

⚠️ 默认值是本地地址，仅用于开发；上线 **MUST** 由 K8s 注入 `ACCOUNT_SERVICE_URL` / `F2F_SERVICE_URL` / `PAY_SIGN_SERVICE_URL` / `ALIPAY_PAY_SIGN_SERVICE_URL` 指向集群 Service，并把新增的键补进 `docs/ops/生产环境清单.md` 的环境变量清单。缺键会落到 `rpc` 默认服务名 `*-service`、DNS 解析不到（AGENTS.md §8）。`service.alipay-pay-sign.url` 的默认值故意不写 `127.0.0.1:8080`——那是 web-admin 自己的端口，写了会自调。


### 7.5 已知约束

- **调度器实际是内存态 `RAMJobStore`，不是 JDBC JobStore**（2026-09-07 生产日志实测：`RAMJobStore initialized`、`instanceId 'NON_CLUSTERED'`、`which does not support persistence. and is not clustered`）。根因是 `web-quartz/.../config/ScheduleConfig.java` **整个文件 57 行全被注释掉**，没有 `SchedulerFactoryBean` Bean，`LocalDataSourceJobStore` 与 `org.quartz.jobStore.isClustered` 都没生效。由此推出三条：
  - 生产库的 `QRTZ_JOB_DETAILS` / `QRTZ_TRIGGERS` / `QRTZ_SCHEDULER_STATE` **全是 0 行、从未被写过**，纯摆设。判断「任务在不在调度器里」**NEVER** 查 `QRTZ_*`；唯一的持久化执行痕迹是 `sys_job_log`。
  - **web-admin 只能单副本**。内存态调度器每个副本各跑一遍全部任务，`concurrent=1` 只在单 JVM 内生效、跨副本管不住。扩副本前 **MUST** 先启用被注释的集群配置。
  - **直接改 `sys_job` 必须重启才生效**。同步只发生在 `SysJobServiceImpl.init()`（`@PostConstruct`：先 `scheduler.clear()`，再按 `sys_job` 全表 `createScheduleJob`）。
- **暂停态（`status=1`）的任务点「执行一次」不会执行**。`createScheduleJob` 末尾对暂停任务调 `scheduler.pauseJob`（`ScheduleUtils.java:94-97`），此后 `triggerJob` 生成的临时 trigger 在 RAMJobStore 里进不了 `timeTriggers`。现象是：接口返回成功、10 个 `quartzScheduler_Worker` 全部 idle、`sys_job_log` 零记录、下游收不到请求（2026-09-07 连点 6 次全部无效）。验证任务 **MUST** 先置成启动态。
- **页面的状态开关（`PUT /monitor/job/changeStatus`）不可靠，改完 MUST 回查 `sys_job.status`**。2026-09-07 实测：HTTP 200，但 `updateJob` 一行都没落库（`UPDATE_TIME` 仍为 NULL），`resumeJob` 里 `if (rows > 0)` 不成立 ⇒ `scheduler.resumeJob` 根本没被调用，任务仍是暂停态；最后靠直接 UPDATE 库 + 重启才生效。注意 RuoYi 的 `error()` **也是 HTTP 200**（业务码在 body 的 `code`），**NEVER 凭 HTTP 状态码或页面提示判定成功**。根因未定位——MyBatis 的 UPDATE DEBUG 日志被 `log4j2-linux.xml` 的 `level=INFO` 压掉了。
- 新增任务 **MUST** 同时补菜单/任务种子 SQL 或在前台手工建，代码合入不会自动创建 `sys_job` 记录。
- web-admin 现在依赖 `rpc`，**NEVER** 借此在 controller 层调业务服务 —— 运营查询接口的归属仍是业务模块的 `/page/**`（见 §六）。

### 7.6 调度日志的「执行日志」（traceId → VictoriaLogs，2026-09-07 新增）

`AbstractQuartzJob.before()` 每次调度用 `MDC.put("traceId", UUID…)` 生成 32 位 hex traceId，`after()` 把它追加到 `sys_job_log.job_message` 末尾（`，traceId=xxx`）。前台「调度日志」列表页据此提供「执行日志」按钮，反查该次执行的全链路日志。

| 位置 | 内容 |
|---|---|
| `web-quartz/.../controller/SysJobLogController.java` | `GET /monitor/jobLog/trace/{jobLogId}`，鉴权沿用 `@ss.hasPermi('monitor:job:query')` |
| `web-quartz/.../service/IJobTraceLogService.java` + `impl/JobTraceLogServiceImpl.java` | 取 traceId → POST VictoriaLogs `/select/logsql/query`（`query=traceId:"xxx"`）→ 按 `_time` 升序，单次上限 500 行 |
| `web-admin/src/main/resources/application.properties` | `vlogs.query-url=${VLOGS_QUERY_URL:}`、`vlogs.query-timeout-millis`、`vlogs.query-window-before-millis`、`vlogs.query-window-after-millis`、`vlogs.query-limit` |
| `web/src/api/monitor/jobLog.js` | `getJobTraceLog(jobLogId)` |
| `web/src/views/monitor/job/log.vue` | `hasTraceId()` 控制按钮显隐（正则 `traceId=[0-9a-fA-F]{32}`）+ 「执行日志」弹窗 |

**跨服务续接靠任务自己传头，框架不管**（2026-09-08 补齐）。`ProxyWebClient` 只把 MDC 里的 `authorization` 透传给下游，**不会**注入任何 trace 头；micro 的 `FirstFilter` 也只是把请求头 key 小写后塞进 MDC，不解析 `traceparent`。因此任务 **MUST** 显式把头带上，统一走 `web-quartz/.../util/QuartzTraceUtils.java`：

```java
QuartzTraceUtils.runWithTrace(traceId -> {
    RespDTO response = client.someMethod(request, QuartzTraceUtils.traceHeaders(traceId));
    // 失败 MUST 抛异常
});
```

- `runWithTrace(Consumer<String>)` — Quartz 路径直接复用 `AbstractQuartzJob.before()` 放进 MDC 的 traceId（也就是写进 `job_message` 的那个），非 Quartz 路径（本地手工调用 / 单测）自己生成并自己清理。**NEVER** 在任务里另生成 traceId，否则前台按 `job_message` 的值检索不到下游日志。
- `traceHeaders(traceId)` — 生成 W3C `traceparent=00-<traceId>-<spanId>-00` + `X-Vlogs-Capture: 1`。末段采样标记固定 `00`（只为让对端 MDC 里有 traceId，不推 span 给 OTLP collector，该地址可达性未实测）；循环排空型任务 **MUST 每轮重新调一次**以拿到独立 spanId。**NEVER 传自定义 `traceId` 头**——`FirstFilter` 会让它与 Micrometer 写入的键相撞。
- 当前 9 个任务类（`terminationQuartzTask` / `notifyCompensateQuartzTask` / `blacklistReleaseInspectQuartzTask` / `paraQuartzTask` / `alipayTerminationQuartzTask` / `accountQuartzTask` / `tbNoticeAppTask` / `reconQuartzTask` / `gateTxnPayQuartzTask`）**全部已接入**，此前分散在 4 个类里的重复 `traceHeaders()` 私有副本已删除。
- ⚠️ **对端能否把 traceparent 续成自己 MDC 的 `traceId`，取决于该模块 `management.tracing.enabled`**（`resource/micro/web/src/main/resources/web.properties:78` 默认 `false`，目前只有 `pay-sign-server/src/main/resources/application.properties:17` 显式打开）。2026-09-08 实测两侧对照：
  - **pay-sign 续接成功**：web-admin 发 `traceparent=00-0974d055296d4ddfa0600ceb7223440f-…`，pay-sign 日志 MDC traceId **就是** `0974d055296d4ddfa0600ceb7223440f`，`sys_job_log` 里 job 246 的 `job_message` 也是同一个值 —— 前台「执行日志」按钮可直接检索到全链路。
  - **para-server 续接失败**：同一次请求头里 `traceparent=00-aa11bb22cc33dd44ee55ff6677889900-…`，para-server 日志 MDC traceId 却是自己生成的 `5128bde4941342dc31ce43c40216e8e4`，两者无关。因此**按 `job_message` 的 traceId 检索不到 para-server 的日志**。修复方式是给 para-server 补上与 pay-sign 相同的两行（`management.tracing.enabled=true` + `management.tracing.sampling.probability=0`），属配置改动，需单独评审。
  - 结论：新接一个下游 **MUST 用「发出的 traceparent trace-id」与「对端日志 MDC traceId」逐字对照**，**NEVER** 只凭「头发出去了」就认为链路已通。

**检索时间窗怎么定的**（`sys_job_log` 只有 `create_time` 一个时间列，其余全靠反推）：

```
start = create_time − 耗时(从 job_message 的「总共耗时：N毫秒」解析) − query-window-before-millis
end   = create_time + query-window-after-millis
```

`create_time` 由 `after()` 写入、**约等于执行结束点**，所以开始点只能用耗时往前推。两个余量默认值**故意不对称**：前置 5 分钟只为覆盖时钟偏差与 appender 攒批（`flushIntervalMillis=2000`）；后置 30 分钟是因为**批处理型任务自己返回后下游往往还在跑**（异步线程池、`@Scheduled` 补偿、超时重试），那些日志的时间戳晚于 `create_time`，尾部窗口小了直接查不到（2026-09-07 用户提出）。

已知约束：

- **`VLOGS_QUERY_URL` 为空即功能禁用**，接口返回明确文案而非静默空列表。只填 `scheme://host:port` 时代码自动补 `/select/logsql/query`。与各模块**推送**用的 `VLOGS_URL` 是同一个 VictoriaLogs、不同路径（推送 `/insert/jsonline`），**NEVER** 把两个键混用。
- **放宽时间窗 MUST 同步跟上 `vlogs.query-limit`**。VictoriaLogs 的 `limit` **不保证返回顺序**，按 `_time` 排序是本端拿到结果后才做的，因此截断是**随机丢弃**而非「保留最早的 N 条」。前台 `truncated=true` 时的提示语只能说「仅展示部分内容」，**NEVER** 让用户以为看到的是开头那一段。
- **`job_message` 文案改了，耗时就解析不到**（正则 `耗时：(\d+)毫秒`），此时按 0 处理，开始点退化成 `create_time − query-window-before-millis`，长耗时任务会漏掉开头。改 `AbstractQuartzJob.after()` 的拼串 **MUST** 同步改这里的正则。
- **web-admin 自己的日志已进 VictoriaLogs（1.1.14 起，2026-09-08 实测通）**，配置在 `web-admin/src/main/resources/log4j2-web-admin.xml`，由 `application.properties` 的 `logging.config` 指向（**不能只靠 micro 的公共 `log4j2-linux.xml`**，那条路径下自定义 appender 在 fat jar 里解析不出来，见 `docs/architecture/common-components.md` §3.1.1）。两处易踩的坑：
  - **日志系统里 web-admin 的 `app` 字段是 `web-server`**（`other.logging.appName=${spring.application.name}`），检索 **MUST** 用 `app:"web-server"`，用 `web-admin` 查出 0 条。
  - 配置里 **MUST** 有 `<Logger name="com.chinasofti.huateng">` 挂**不带 `level` 的** `<AppenderRef ref="VictoriaLogs" />`。只有 Root 的 `level="WARN"` 引用时，任务的 INFO 会在 AppenderRef 层被 DENY，`x-vlogs-capture` 链路采集完全不生效（1.1.13 的实际故障）。
  实测数据（1.1.14，`NotifyCompensateQuartzTask`）：traceId `b9593382190f4b6fbbb9c75fb63ebda8` 一次调度产出 3 条 web-server 记录（出向请求头、对端响应体、任务完成行），同一 traceId 下 pay-sign 8 条，跨服务串起来了。
- 调 `account` / `f2f` / `para` 的任务仍可能只看到 web-server 侧：下游模块要么没声明 appender，要么缺 `management.tracing.enabled=true`（para-server 已实测会重新生成 trace-id 而不是延用上游的）。这是覆盖面问题，不是 bug。
- **`sys_job_log` 没有 `start_time` / `stop_time` 列**（`SysJobLogMapper.xml` 的 resultMap 与 `selectJobLogVo` 都只有 7 列 + `create_time`），实体上那两个字段是 `AbstractQuartzJob` 内部临时用的，**NEVER** 指望从查询结果里取到。
- 展示的 `_time` 是 **UTC**（`victorialogs-event-template.json` 固定 `timeZone: UTC`），比本地时间早 8 小时。

## 八、相关文档

- 运营页面接口归属约定：[`../business/admin-web.md`](../business/admin-web.md)
- 架构总览与端口：[`overview.md`](overview.md)
- 公共构件：[`common-components.md`](common-components.md)

## 附：web-server / web 源码注释知识抽取（2026-09-16，阶段一）

> **有并发写入者，引用行号前 MUST 先 grep 现查。** 本节所有 `路径:行号` 都是 2026-09-16 抽取时点的位置，
> 任何一次格式化、插行或合并都会让它漂移；据本节定位 MUST 先 `grep -n '<原文片段>' <文件>` 复核。
> 抽取范围：`web-server/**/src/main/java/**/*.java`、`web-server/**/src/main/resources/mapper/*.xml`、
> `web-server/**/src/main/resources/sql/*.sql`、`web-server/web-admin/src/main/resources/application.properties`
> 与 `log4j2-web-admin.xml`；前端只取与后端契约或运维判据相关的注释。
> 条目前的【契约】/【决策】/【陷阱】是归类标记；**注释原文的 MUST / NEVER 一律保留、不合并近似条款**。
> 凡结论只在 AGENTS.md 或迁移脚本头注释里、Java 注释中没有的，条目内已显式标注真实出处。
> 敏感项只写键名与位置，**不回显任何口令 / 密钥真值**。

### 附一、Quartz 调度与 `sys_job`

- 【陷阱/决策】**`ScheduleConfig` 整个文件被逐行注释掉**（web-quartz · `ScheduleConfig`，
  `web-server/web-quartz/src/main/java/com/chinasofti/huateng/quartz/config/ScheduleConfig.java:1~57`
  全部以 `//` 开头）。被注释掉的 Javadoc 原文（`:10`）是「定时任务配置（单机部署建议删除此类和qrtz数据库表，默认走内存会最高效）」，
  被注释的内容里含 `org.quartz.jobStore.class=LocalDataSourceJobStore`（`:32`）、
  `isClustered=true`（`:34`）、`tablePrefix=QRTZ_`（`:42`）、`threadCount=20`（`:29`）——
  即**整套集群化 JobStore 配置连同 `SchedulerFactoryBean` 一起停用**。这是「内存 JobStore」在代码里的唯一物证。
  ⚠️ **出处说明**：由此推出的运维判据「`QRTZ_JOB_DETAILS` / `QRTZ_TRIGGERS` 恒为 0 行、这不等于任务没跑、
  判断任务是否在跑 MUST 查 `SYS_JOB_LOG`」**在 Java 注释里没有**，真实出处是
  `web-server/web-quartz/src/main/resources/sql/web-quartz-refund-compensate-job-migration.sql:11`
  的脚本头注释与 AGENTS.md §8。
- 【契约】**迁移脚本头注释给出的四条生效条件**（web-quartz · `web-quartz-refund-compensate-job-migration.sql:3~12`，
  标题原文「执行方式与生效条件（MUST 先读完再执行）」）：
  1. 「Quartz 用内存 JobStore，任务只在 web-admin 启动时由 @PostConstruct 从 sys_job 全量加载。
     因此「运行中直接 INSERT sys_job 不会生效」，本脚本执行完 MUST 重启 web-admin，
     或在后台「定时任务」界面对这两条任务各做一次修改保存，才会真正注册进调度器。」
  2. 「web-admin MUST 单副本：多副本时每个副本各加载一份，同一时刻会重复触发下游补偿。」
  3. 「misfire_policy=3 表示「不补跑」，web-admin 重启期间错过的调度不会追补。」
  4. 「判断任务有没有跑 MUST 查 SYS_JOB_LOG，QRTZ_* 表在内存 JobStore 下恒为 0 行。」
- 【契约】**`sys_job` 现有 14 行的 job_id / invoke_target / cron 全清单**，抄在同一脚本的
  `web-quartz-refund-compensate-job-migration.sql:14~29`（原文标注「2026-09-15 实测 …… 共 14 行，
  最大 JOB_ID 是 121（不是 109），故新增取 122 / 123」）：
  `1 ryTask.ryNoParams 0/10 * * * * ?`、`2 ryTask.ryParams('ry') 0/15 * * * * ?`、
  `3 ryTask.ryMultipleParams(...) 0/20 * * * * ?`、`4 terminationQuartzTask.confirmTermination(...) 0 0 4 * * ?`、
  `5 alipayTerminationQuartzTask.cancelCard() 0 0 2 * * ?`、
  `6 notifyCompensateQuartzTask.compensateSignNotify() 0 0/10 * * * ?`、
  `7 notifyCompensateQuartzTask.compensateTerminationNotify() 0 5/10 * * * ?`、
  `105 blacklistReleaseInspectQuartzTask.inspect() 0 0 10,16 * * ?`、
  `106 paraQuartzTask.scanFtpPara() 0 2/10 * * * ?`、`107 cardPoolQuartzTask.runMaintenance() 0 0/5 * * * ?`、
  `108 accountQuartzTask.compensatePhoneSignSync() 0 0/5 * * * ?`、`109 reconQuartzTask.runDailyBatch() 0 30 2 * * ?`、
  `120 gateTxnPayQuartzTask.recoverOfflineFare() 0 0/1 * * * ?`、`121 gateTxnPayQuartzTask.pushMetroTransfer() 0 0/1 * * * ?`。
- 【契约】**本脚本新建的两条**（同文件 `:56~66`）：`122 退款回查补偿 refundCompensateQuartzTask.compensateRefundQuery() 0 0/10 * * * ?`、
  `123 退款汇总跨表对账 refundCompensateQuartzTask.compensateRefundSummary() 0 15 * * * ?`；
  两条 remark 原文写明下游端点（`POST /internal/payment/compensateRefundQuery` / `compensateRefundSummary`）、
  「间隔必须大于下游 staleMinutes=5 分钟，禁止单次调度内循环。停用会让退款单永久悬挂。单条结果看 REFUND_STATUS，不要看 submitted」。
- 【契约】**其余列的取值口径**（同文件 `:31~36`）：「其余列取值照现有行抄：job_group='DEFAULT'、misfire_policy='3'、
  concurrent='1'（禁止并发）、status='0'（正常）、create_by='admin'」；幂等做法是「先 DELETE 这两个 JOB_ID 再 INSERT，
  重复执行不会撞主键」；「JOB_ID 是 IDENTITY 列但可显式赋值，与 sys_job 现有 105~121 那批同一做法」。
- 【陷阱】**job 123 remark 里的「不可自愈记录条数」是时点快照**（同文件 `:38~48`）：原文「引用前 MUST 现查」并给出
  `SELECT COUNT(DISTINCT R.ORDER_NO) FROM PAY_REFUND_DETAIL R WHERE R.REFUND_STATUS='SUCCESS' AND NOT EXISTS (...)`；
  「该值只会随「退款回查收口成功」而**增加**」；「本脚本初版写「当前6条」，当天 2.0.88 修掉 refundQuery 字段名缺陷（ADR-D92）、
  收口 RF2026062516090566197559552 之后即变成 7 条」；结论是「**NEVER 把这个数字当成告警阈值或断言基线**」。
  注意同一事实在 `RefundCompensateQuartzTask` 类注释（`web-server/web-admin/src/main/java/com/chinasofti/huateng/quartz/task/RefundCompensateQuartzTask.java:38~39`）
  里写的是「当前库里实测 6 条」，与脚本里的 7 条**不一致**（详见文末矛盾清单）。
- 【契约】**禁止并发与不补跑的理由**（web-admin · `GateTxnPayQuartzTask` 类注释，
  `web-server/web-admin/src/main/java/com/chinasofti/huateng/quartz/task/GateTxnPayQuartzTask.java:22~24`）：
  「**两条任务的 `sys_job` MUST 配「禁止并发」（`concurrent='1'`，注意 '0' 才是允许）**：
  原实现是 `fixedDelay`（上一轮跑完再等 N 秒），cron 表达不了这个语义，不重叠只靠禁并发保证。
  同时 `misfire_policy='3'`（错过不补跑）—— 每分钟一轮，补跑毫无意义只会挤压 worker。」
- 【契约】**任务类必须在白名单包内**：web-common · `Constants.JOB_WHITELIST_STR`
  （`web-server/web-common/src/main/java/com/chinasofti/huateng/common/constant/Constants.java:174~177`，
  注释原文「定时任务白名单配置（仅允许访问的包名，如其他需要可以自行添加）」，值 `com.chinasofti.huateng.quartz.task`）。
  web-admin 侧 11 个任务类的类注释各自复述一遍，措辞最完整的是 `GateTxnPayQuartzTask:13~15`
  「任务类必须位于白名单包 `com.chinasofti.huateng.quartz.task` 下（`web-common/.../constant/Constants.java` 的
  `JOB_WHITELIST_STR`），放别的包前台直接报「违规」」与 `RefundCompensateQuartzTask:29~30`
  「本类 MUST 位于 Quartz 调用白名单包 …… 否则前台配了 invokeTarget 也调不到」。
- 【陷阱】**带数字参数的任务方法 MUST 用包装类型**（web-admin · `TerminationQuartzTask.confirmTermination`，
  `.../task/TerminationQuartzTask.java:62~70`）：「**第二个参数 MUST 是 `Integer` 而不是 `int`**：
  `JobInvokeUtil#getMethodParams` 把不带后缀的数字一律解析成 `Integer.class`（只有 `L` 后缀→Long、`D`→Double、
  引号→String、true/false→Boolean），而它随后用 `getClass().getMethod(名, 类型数组)` 反射查找 ——
  **`getMethod` 按精确类型匹配、不做自动装箱**。因此签名写 `int` 时从后台调用必抛
  `NoSuchMethodException: confirmTermination(java.lang.String, java.lang.Integer)`，
  且 `0` / `0L` / `0D` 全都对不上、无从绕过。2026-09-14 实测到该异常（job 4，job_log_id=5034）。
  **本包内新增带数字参数的任务方法 MUST 一律用包装类型。**」
  配套的解析规则原文在 web-quartz · `JobInvokeUtil.getMethodParams`（`.../quartz/util/JobInvokeUtil.java:118~138`）：
  「String字符串类型，以'或"开头」/「boolean布尔类型，等于true或者false」/「long长整形，以L结尾」/
  「double浮点类型，以D结尾」/「其他类型归类为整形」。
- 【契约】**`sys_job.remark` 的更新语义只判 null、不判空串**（web-quartz · `SysJobMapper.xml:67~69`）：
  「remark 承载「任务说明」（用途与使用方式），前台标签即为任务说明。这里只判 null 不判空串：
  判空串时前台清空说明会被静默忽略、永远删不掉旧文案。null 仍表示「本次不改这一列」，
  pauseJob / resumeJob / changeStatus 依赖这层语义。」
- 【契约】**任务说明长度上限 500 且在 Controller 里显式挡**（web-quartz · `SysJobController.JOB_REMARK_MAX_LENGTH`，
  `.../quartz/controller/SysJobController.java:40~43`）：「任务说明的长度上限，等于 sys_job.remark 的列宽 VARCHAR2(500 CHAR)。
  这里显式挡一次而不是靠 Bean Validation：add / edit 都没有 @Validated，实体上的 @Size 不会被触发，
  超长会一路走到 Oracle 抛 ORA-12899、前台只看到一串异常。」
- 【决策】**启动时全量重载调度器的理由**（web-quartz · `SysJobServiceImpl.init`，
  `.../quartz/service/impl/SysJobServiceImpl.java:45`，RuoYi 原生注释但含判据）：
  「项目启动时，初始化定时器 主要是防止手动修改数据库导致未同步到定时任务处理
  （注：不能手动修改数据库ID和任务组名，否则会导致脏数据）」。
- 【契约】**创建任务时的过期与暂停处理**（web-quartz · `ScheduleUtils.createScheduleJob`，
  `.../quartz/util/ScheduleUtils.java:79~93`）：注释序列为「判断是否存在」→「防止创建时存在数据问题 先移除，
  然后在执行创建操作」→「判断任务是否过期」→「执行调度任务」→「暂停任务」。同类 `:123` 另有「检查包名是否为白名单配置」。

### 附二、`SYS_JOB_LOG` 生命周期（开始即入库 + 收口回写）

- 【契约】**本次执行那一行的主键靠 ThreadLocal 传递**（web-quartz · `AbstractQuartzJob.JOB_LOG_ID`，
  `web-server/web-quartz/src/main/java/com/chinasofti/huateng/quartz/util/AbstractQuartzJob.java:34~37`）：
  「本次执行在 sys_job_log 里那一行的主键。`before` 落「进行中」时回填，`after` 据此 UPDATE。
  取不到（插入失败）时 after 退化成 INSERT，保证结果不丢。」
- 【契约】**MDC 的 traceId 键名两侧共用同一个常量**（同类 `:41~45`）：「MDC 中链路追踪标识的键名，与
  `QuartzTraceUtils#TRACE_ID_KEY` 同源。本类是生产者（放入 MDC 并写库），任务类是消费者（取出往下游传），
  两侧 MUST 用同一个常量，**NEVER** 各写一份字面量。」
- 【契约】**`before()` 每次调度覆盖式生成 traceId**（同类 `AbstractQuartzJob.before`，`:82~84`）：
  「每次调度生成一个 traceId：任务方法可用 MDC.get("traceId") 取出往下游传（W3C traceparent），
  after() 再把它写进 sys_job_log.job_message，前台「调度日志」即可拿到这个值去日志系统检索。
  Quartz 线程是池化复用的，这里直接覆盖上一次的值，不依赖上一次是否清理干净。」
- 【决策/陷阱】**「进行中」那一行的 INSERT NEVER 打断任务**（同类 `AbstractQuartzJob.insertRunningLog`，`:89~94`）：
  「落一行「进行中」，让长任务在执行期间就能在前台被看到并按 traceId 检索。
  日终对账这类任务单次要跑一分钟以上，收口后才入库等于**在跑的时候查不到任何东西**。
  这里的 INSERT 与任务本身无关，因此任何异常只记日志、**NEVER 让它打断任务执行**；
  主键回填失败时 `after` 会退化成 INSERT，结果照样落库。」
- 【契约】**`job_message` 的拼串形态与长度余量**（同类 `AbstractQuartzJob.buildJobMessage`，`:119~122`）：
  「拼接 job_message：带上 traceId，运维在前台「调度日志」看到后可直接去日志系统按该值检索本次执行的全链路日志。
  job_message 是 varchar(500)，原文本很短，追加 32 位 traceId 不会截断。」
- 【决策/陷阱】**`after()` 永不抛异常**（同类 `AbstractQuartzJob.after`，`:134~138`）：
  「本方法**永不抛异常**：`execute` 的 catch 分支会再调一次 after，
  若这里抛出去就会出现「同一次执行被回写两遍、后一遍还带着假的失败状态」。」
  同方法 `:174` 另注「写入数据库当中：before() 已落「进行中」时按主键回写，否则退化成新增」。
- 【陷阱】**Quartz worker 复用，MDC MUST 在 finally 清理**（同类 `AbstractQuartzJob.after` 的 finally 块，`:191`）：
  「Quartz 线程池化复用，MUST 在此清理，否则下一个任务在 before() 覆盖前会短暂带着上一次的 traceId。」
- 【陷阱】**`JOB_LOG_ID` 是 Oracle IDENTITY 列，NEVER 改成 `selectKey` 取 nextval**
  （web-quartz · `SysJobLogMapper.insertJobLog`，`web-server/web-quartz/src/main/resources/mapper/quartz/SysJobLogMapper.xml:72~74`）：
  「job_log_id 是 Oracle IDENTITY 列（DEFAULT ON NULL），库里没有对应序列，
  因此回填主键只能靠 JDBC getGeneratedKeys，NEVER 改成 selectKey 取 nextval。
  回填是 before() 先插「进行中」、after() 再按主键 UPDATE 的前提。」
- 【契约】**收口 UPDATE 保留 `create_time`、并清空上一次的异常残留**（同 XML `updateJobLog`，`:97~98`）：
  「执行收口时按主键回写结果。create_time 保持插入时刻（即任务开始时间）不变，
  exception_info 用 `#{}` 而非 `<if>`，成功时需要把上一次残留清空。」
- 【契约】**重启收口语句**（同 XML `closeRunningJobLog`，`:107`）：
  「web-admin 重启时把上次进程遗留的「进行中」记录收口，避免永远悬挂。」
  Mapper 接口侧的约束更硬（web-quartz · `SysJobLogMapper.closeRunningJobLog`，
  `.../quartz/mapper/SysJobLogMapper.java:56~60`）：「把遗留的「进行中」记录批量收口为指定状态。
  Quartz 是内存 JobStore，web-admin 进程重启后上一轮执行的结果永远不会回写，
  那些行会一直悬在「进行中」。启动时扫一次即可，本方法 NEVER 用于运行期。」
- 【契约】**回写方法的入参前置条件**：`SysJobLogMapper.updateJobLog`（`.../mapper/SysJobLogMapper.java:51`）、
  `ISysJobLogService.updateJobLog`（`.../service/ISysJobLogService.java:39`）、
  `SysJobLogServiceImpl.updateJobLog`（`.../service/impl/SysJobLogServiceImpl.java:59`）三处逐字相同：
  「@param jobLog 调度日志信息，MUST 带 jobLogId」；Mapper 侧另注（`:47~49`）
  「配合 `insertJobLog` 在任务开始时先落「进行中」使用，因此 create_time 保持插入时刻不动，即任务真实开始时间。」
- 【决策】**启动时把遗留「进行中」收口成失败，且失败只记日志**（web-quartz · `SysJobServiceImpl` 的启动收口方法，
  `.../quartz/service/impl/SysJobServiceImpl.java:60~66`）：「收口上一个进程遗留的「进行中」调度日志。
  Quartz 用的是内存 JobStore，进程一停，正在跑的那次执行就再也不会有人回写结果，
  那一行会永远停在「进行中」。这里在启动时统一标失败并写明原因，前台看到的是「结果未知」而不是「还在跑」。
  失败只记日志：这件事不该阻止 web-admin 启动。」
- 【契约/陷阱】**`Constants.RUNNING = "2"` 必须有字典项，否则前台状态列空白**
  （web-common · `Constants.RUNNING`，
  `web-server/web-common/src/main/java/com/chinasofti/huateng/common/constant/Constants.java:55~61`）：
  「目前只用于 sys_job_log：任务开始时先落一行「进行中」，收口时再回写成 `SUCCESS` 或 `FAIL`，
  长任务在跑的过程中即可被观测到。新增该取值时 MUST 同步给字典 sys_common_status 补一条 2=进行中，
  否则前台调度日志的状态列会渲染成空白。」
  配套字典行由 web-quartz · `web-quartz-job-log-running-status-migration.sql`（**该文件没有头注释**，只有 SQL 与
  remark 列文案）落库，第 2 行的 remark 原文是「任务开始时先落该状态，收口后回写为成功或失败」，
  `list_class` 取 `'warning'`、`dict_sort=3`、`is_default='N'`、`status='0'`，并用
  `WHERE NOT EXISTS (...)` 做幂等。⚠️ **「回查 DICT_CODE=100」这一条只在 AGENTS.md §8 里，脚本内没有。**
- 【契约】**`sys_job_log` 只有 `create_time` 一个时间列**（web-quartz · `JobTraceLogServiceImpl` 类注释，
  `.../quartz/service/impl/JobTraceLogServiceImpl.java:38~40`）：「时间窗由 `sys_job_log.create_time` 与
  `job_message` 里的耗时反推。该表**没有** start_time / stop_time 列（见 `SysJobLogMapper.xml` 的 resultMap），
  勿指望从实体上直接取。」实体侧 `SysJobLog`（`.../quartz/domain/SysJobLog.java:46`、`:49`）仍保留
  `/** 开始时间 */` 与 `/** 停止时间 */` 两个字段注释 —— 它们只用于算耗时。
- 【契约】**列表检索按天截断**（web-quartz · `SysJobLogMapper.selectJobLogList` 的两个 `<if>`，
  `SysJobLogMapper.xml:38`「开始时间检索」/`:41`「结束时间检索」，SQL 形态是
  `create_time >= TRUNC(#{params.beginTime})` 与 `create_time < TRUNC(#{params.endTime}) + 1`）。

### 附三、任务 Bean 与 rpc 下游（web-admin `quartz/task` 共 11 个类）

以下路径统一省略前缀 `web-server/web-admin/src/main/java/com/chinasofti/huateng/quartz/task/`。

**通用桥接口径**（三个类用几乎同一句话）：web-admin · `AccountQuartzTask` 类注释（`AccountQuartzTask.java:11~14`）
「account-server 的 Quartz 调用桥接任务。任务必须位于 Quartz 调用白名单包 com.chinasofti.huateng.quartz.task 下，
Quartz 仅调用本 Bean；跨服务调用由 AccountClient 完成。」；`ParaQuartzTask.java:11~14`、`CardPoolQuartzTask.java:11~14`
同形（分别是 ParaClient / CardPoolClient）。

- 【契约】**「失败 MUST 抛异常」是全部 11 个类的统一收口判据**，原文措辞略有差异但语义一致，逐处保留：
  `AccountQuartzTask.java:61~62`「失败 MUST 抛异常 —— sys_job_log 的成功/失败判定就看有没有异常抛出，
  只打日志会让「补偿一直没生效」在调度日志里显示为成功。」；
  `CardPoolQuartzTask.java:47`、`ParaQuartzTask.java:45`「只调一次下游，失败 MUST 抛异常：Quartz 只以异常判定失败，
  静默返回会让调度日志记成成功。」；`GateTxnPayQuartzTask.java:50~51`；`NotifyCompensateQuartzTask.java:69~70`；
  `ReconQuartzTask.java:46~47`；`RefundCompensateQuartzTask.java:45~46`（「响应为 null 或 `resultCode != "0000"` MUST 抛异常」）
  与 `:92`；`TBNoticeAppTask.java:65~66`（「四个入口的结果判定完全一致，统一在此做」）；
  `TerminationQuartzTask.java:83~84`；`AlipayTerminationQuartzTask.java:68`；
  `BlacklistReleaseInspectQuartzTask.java:57~58`。
- 【契约】**traceId 一律复用、MUST NOT 另生成**：`AccountQuartzTask.java:31~33`
  「traceId 由 `QuartzTraceUtils#runWithTrace` 统一处理：Quartz 路径复用 AbstractQuartzJob 放进 MDC 的值
  （同一个值会被写进 sys_job_log.job_message），非 Quartz 路径自行兜底。」；
  `NotifyCompensateQuartzTask.java:60~62` / `RefundCompensateQuartzTask.java:81~83` / `TBNoticeAppTask.java:68~70` /
  `BlacklistReleaseInspectQuartzTask.java:48~49` 均写明「MUST NOT 另生成一个——否则前台调度日志里的 traceId
  与实际发给（pay-sign / collect-pay / 下游）的对不上」。
- 【契约/决策】**`GateTxnPayQuartzTask` 是那两条补偿链路的唯一调度源**（`GateTxnPayQuartzTask.java:17~20`）：
  「**本任务是这两条补偿链路的唯一调度源。**gate-txn-pay-server 2.0.73 起 `OfflineFareRecoveryProcessor` 与
  `MetroTransferPushTaskProcessor` 都没有 `@Scheduled` 了，频率完全由 `sys_job` 的 cron 决定
  （两条都是 `0 0/1 * * * ?`）。**NEVER 在那两个类上加回 `@Scheduled`** —— 两套调度源互不知情，
  离线码那条会并发发起扣款。」
- 【陷阱】**下游恒返 `0000`，排查「补偿为什么不动」MUST 看 `retMsg` 那一列**（同类 `:26~29`）：
  「下游**恒返 `retCode=0000`**（本轮扫表异常属可自愈，下一分钟重入），因此这里的 `retCode` 判定实际只在
  「无响应 / 不可达」时生效。本轮结论在 `retMsg` 里、会进 `sys_job_log`，
  **排查「补偿为什么不动」MUST 先看那一列**（能区分「开关未开启」「扫表异常」「本轮 0 笔」三种情形）。」
- 【契约】**离线码补偿会真的扣款、禁并发那一列 NEVER 改成允许**（同类 `recoverOfflineFare`，`:46~48`）：
  「触发一轮离线码金额重算失败订单的补偿（重算票价并补扣款）。**这条链路会真的扣款**，
  因此下游的抢占依赖条件更新（`applyOfflineFareRecalculated` 返 1 才继续），早跑一轮最坏只是多一次空扫；
  但**禁并发那一列 NEVER 改成允许**。」
- 【契约】**公交换乘推送周期由 10 秒改成 60 秒，且 cron 打密会放大日志表增量**（同类 `pushMetroTransfer`，`:61~63`）：
  「**轮询周期由原来的 10 秒变成 60 秒**（用户 2026-09-15 裁决），公交卡系统那侧收到推送的时延上限随之变化；
  要调回更密只能改这条 cron，但注意 `SYS_JOB_LOG` 是「开始即入库」，cron 每打密一档那张表日增行数就翻一档。」
- 【决策】**两条任务共用一个收口判定，NEVER 各写一份**（同类 `:83`）：
  「两条任务共用的收口判定，**NEVER 让两条各写一份** —— 判据一样，写两遍必然有一天走偏。」
- 【契约/决策】**`ReconQuartzTask` 是日终对账唯一调度源**（`ReconQuartzTask.java:17~20`）：
  「**本任务是日终对账的唯一调度源。**recon-server 已按用户 2026-09-11 的要求删掉 `@EnableScheduling`、
  一个 `@Scheduled` 都没有，账期何时跑、跑几次完全由 `sys_job` 的 cron 决定（默认每天 02:30 一次）。
  **NEVER 在 recon-server 那边再加回 `@Scheduled`**，两套调度源互不知情，改 cron 时只改一处就会重复建批次与重复投递。」
- 【契约】**对账任务不传参、重复触发幂等，且建议禁并发**（同类 `runDailyBatch`，`:37~44`）：
  「触发 recon-server 跑完一整轮日终对账：建批次 → 下发三个源抽取 → 轮询收齐 → 生成四类文件 → FTP 投递。
  账期由下游按 `recon.orchestration.window-offset-days` 自行推算（T-2 日），**本任务不传参**，因此重复触发是幂等的。
  下游同步跑完才返回，单次耗时实测约 60 秒、上限由下游 `recon.orchestration.run-timeout-millis`（默认 4 分钟）控制。
  因此**执行策略建议配「禁止并发」**，避免前台「执行一次」与 cron 重叠时排队占用 Quartz worker
  （下游也有进程内拒绝，会直接返回失败）。」
- 【契约/决策】**`NotifyCompensateQuartzTask` NEVER 在单次调度内循环排空**（`NotifyCompensateQuartzTask.java:23~27`）：
  「**与 `TerminationQuartzTask` 的关键差异：本任务 NEVER 在单次调度内循环排空。**
  下游在提交重发**之前**就同步递增 NOTIFY_RETRY_COUNT 并把状态置为 FAILED，真正的通知是异步发的；
  若在同一次调度里再调一轮，会立刻扫到同一批（计数已 +1、异步结果还没回写），几秒内把重试预算烧光。
  排空只能靠 cron 周期，**调度间隔 MUST 大于下游的 PENDING 滞留阈值（pay-sign 侧 `PENDING_STALE_MINUTES`=10 分钟）**，
  建议 `0 0/10 * * * ?`。」两个入口与表的对应（同类 `:16~21`）：
  `compensateSignNotify()` → `APP_PAY_SIGN_REQUEST`、`compensateTerminationNotify()` → `APP_TERMINATION_REQUEST`，
  「两条链路扫的是不同的表，pay-sign 侧也是两个独立接口，**不可互相替代**，因此这里给出两个入口，前台需要各建一条 sys_job」。
- 【陷阱】**`submitted` 不代表送达**（同类 `:72~73`）：「NEVER 用 submitted 判断通知是否送达——它只代表「提交成功」，
  真实结果由下游异步回写到 NOTIFY_STATUS / NOTIFY_RESULT。本任务只保证「把该补的都提交出去了」。」
  同类 `:86~87` 另注「skipped 是「提交重发时就失败」，这些记录状态未变、下次调度会再取到，不算本次失败，
  但持续非 0 说明通知线程池长期打满或下游异常，需要人工看一眼。」
- 【契约】**`RefundCompensateQuartzTask` 两个入口的性质与间隔**（`RefundCompensateQuartzTask.java:16~36`）：
  `compensateRefundQuery()` → `POST /internal/payment/compensateRefundQuery`，扫 `PAY_REFUND_DETAIL` 停在 `PROCESSING`
  的退款单，「**会出网**调支付中心 §3.2 refundQuery 回查并 CAS 收口。它是 `requestRefund` 摘掉 `@Transactional` 后的
  配套补偿，**停掉等于让那批单子永久悬挂**」；`compensateRefundSummary()` → `POST /internal/payment/compensateRefundSummary`，
  「**不出网**，只重算 `PAY_TXN_DETAIL` 的 `REFUND_AMOUNT` / `REFUND_STATUS`」。
  「**compensateRefundQuery 的调度间隔 MUST 大于下游的 staleMinutes（pay-sign 侧 `REFUND_QUERY_STALE_MINUTES`=5 分钟），
  建议 cron `0 0/10 * * * ?`。** 下游只捞「距上次发起退款已超 5 分钟」的单子，间隔更短时同一批单子会在还没进入
  可回查窗口时被反复扫到，白跑一轮还重复出网。**NEVER 在单次调度内循环排空** —— 理由与 `NotifyCompensateQuartzTask` 相同。」
- 【陷阱/决策】**汇总入口的 `skipped>0` MUST 用 warn、NEVER 抄成 error**（同类 `:38~43` 与 `:111~112`）：
  「**compensateRefundSummary 的 skipped 长期非 0 是预期，不是故障。**其中含「明细已 SUCCESS 但 `PAY_TXN_DETAIL` 里
  没有该 ORDER_NO」这类**不可自愈**的记录（当前库里实测 6 条），每轮都会被重复计入 skipped。
  因此本类的汇总入口 **MUST NOT** 照抄 `NotifyCompensateQuartzTask` 里「`skipped > 0` 就 `log.error`」的写法——
  那会让调度日志每轮报一条 ERROR、把真实故障淹掉；这里改成 `log.warn` 并在消息里点明
  「含不可自愈记录、需人工核对、不代表本轮失败」。**NEVER 抄错这一条。**」
  行内注释复述（`:111~112`）：「MUST 用 warn：写 error 会让每轮调度都报错、把真实故障淹掉。」
- 【陷阱】**单笔退款结果看 `REFUND_STATUS`，不看 `submitted`**（同类 `:94~95`）：
  「NEVER 用 submitted 判断某一笔退款的结果——它只代表本轮提交了几笔，单条结果看 `PAY_REFUND_DETAIL.REFUND_STATUS`。」

- 【契约/决策】**`TerminationQuartzTask` 反过来：在单次调度内循环排空**（`TerminationQuartzTask.java:34~38`）：
  「单次调度内最多调下游多少轮。下游 processTermination 每轮对 PENDING 与 SCANNING 各只取 BATCH_SIZE(200) 条，
  本任务是日级调度，只调一轮的话积压超过 400 条就要拖到第二天，而积压越久 SCANNING 越接近收口超时阈值。
  所以这里循环排空。上限存在的意义是防御：下游若一直返回「有记录但一条都没推进」，不能无限打下去。」
  行内（`:121~122`）「本轮扫到了记录但一条都没推进状态，再调下去只会拿到同一批。停下来告警等下一次调度，
  避免把一批卡住的记录刷满 MAX_ROUNDS。」
- 【陷阱】**循环排空期间 cutoff MUST 固定，NEVER 每轮重新取当前时间**（`TerminationQuartzTask.java:86~87`）：
  「整个循环复用同一个 request，因此 cutoff 在排空过程中固定不变，**NEVER** 每轮重新取当前时间——
  否则边界会随耗时漂移，刚好卡在边界上的申请会被漏掉。」
  `AlipayTerminationQuartzTask.java:70~71` 同款：「**NEVER** 每轮重新取当前时间——否则边界会随耗时漂移。」
- 【契约】**解约延迟天数只由下游配置决定，NEVER 在任务里再写一份**（`TerminationQuartzTask.java:17~19`）：
  「业务口径：乘客 APP 申请解约支付方式后，按规定满 N 天（默认 4 天）才确认解约。
  延迟天数由 pay-sign-server 的 `termination.confirm-delay-days` 控制，本任务只传基准时间，
  **NEVER** 在这里再写一份天数，否则两处配置会漂移。」
  补跑入口示例（`:59~60`）：`terminationQuartzTask.confirmTermination('20260907', 4)`。
- 【契约】**`expired` / `failed` 非 0 只留痕不抛异常**：`TerminationQuartzTask.java:134~135`
  「expired 非 0 说明有申请因收口超时被判失败，需人工到支付中心核对协议真实状态，这不是本次调用失败，
  因此单独用 error 级别留痕而不抛异常。」；`AlipayTerminationQuartzTask.java:113~114` 同形
  （「failed 非 0 说明有登记被判终态失败（当前唯一原因是签约信息不存在），需人工核对」）。
- 【契约】**支付宝出行销卡的业务口径与循环上限**（`AlipayTerminationQuartzTask.java:17~18`、`:33~35`）：
  「扫 `ALIPAY_TERMINATION_REQUEST` 里请求销卡（PENDING）的记录，通知支付中心业务关闭成功后把签约置 TERMINATED、
  登记置 COMPLETED。」「下游每轮只取 BATCH_SIZE(200) 条，日级调度只调一轮的话积压超过 200 条就要拖到第二天，
  所以这里循环排空；上限用于防御下游一直返回「有记录但一条都没推进」的情况。」
  补跑入口示例（`:56`）：`alipayTerminationQuartzTask.cancelCard('20260907')`。
- 【契约/决策】**`BlacklistReleaseInspectQuartzTask` 只读、NEVER 解除任何黑名单**（`BlacklistReleaseInspectQuartzTask.java:19~23`）：
  「**本任务只读，NEVER 解除任何黑名单。**它把每条黑名单记录的欠费事实（闸机出站扣费 + 支付宝出行两个源）查清楚后
  输出到日志，由人工据 REASON 判断该不该解除。之所以不直接删：`BLACKLIST` 只有 5 列、没有拉黑类型字段，
  REASON 是四个来源混写的自由文本，生产实测 35 条 ADD 里 22 条是「用户挂失补卡」——与欠费无关，
  按「欠费结清」删掉等于让挂失旧卡恢复过闸。等拉黑类型落库、且支付宝侧把「幂等拒答误判成扣款失败」的缺陷修掉之后，
  才能在此基础上加自动删除。」建议 cron（`:16~17`）`0 0 10,16 * * ?`。
- 【陷阱】**SETTLED 清单不是可删名单**（同类 `:82~85`）：「把「欠费已结清」的记录逐条打出来，供人工判断是否该解除。
  MUST 带上 reason 原文：SETTLED 只代表钱结清，挂失补卡类同样会是 SETTLED，
  **NEVER** 把这份清单当成「可以删的名单」直接执行。」同类 `:71` 另注
  「unknown 是「至少一个欠费源没查成功」，事实不明，本次盘点结论对这些记录不可用，需人工看一眼。」
  以及 `:25~27`「与 `NotifyCompensateQuartzTask` 一样**不在单次调度内循环**：本任务是只读盘点，不改变任何状态，
  循环调用只会重复输出同一批，没有意义。批量范围由 blacklist-server 侧 `blacklist.inspect.batch-size` 控制。」
- 【契约/陷阱】**卡池维护是「受理式」接口，返回成功 ≠ 已导完**（`CardPoolQuartzTask.java:49~54`）：
  「下游是**受理式**接口：提交给单线程维护池后立刻返回，ACC 申请、FTP 下载、十万行入库都在 card-pool-server 后台跑。
  因此本方法返回成功只代表「已受理」，**NEVER 把它当成「这一轮已导完」**——导入结果 MUST 看 card-pool-server 日志与
  `/card-pools/summary` 的水位。`accepted=false`（上一轮未结束）**不抛异常**：一次十万行导入远超 5 分钟的调度间隔，
  常态会被限流丢弃。这里抛异常会让前台调度日志长期一片红，真故障反而被淹掉。」
  同类 `:16~18` 另记决策理由：「card-pool-server 本模块**不注册 `@Scheduled`**（见 docs/business/card-pool.md「编码约束」），
  卡池的回收 / 补货 / 批次推进全靠本任务按 cron 触发，因此这个 Bean 一旦不跑，卡池就只会被消耗、不会被补充。」
  维护动作范围（`:35~36`）：「回收超时预占、按 `card-pool.threshold` 补货、推进 `CREATED` / `DOWNLOADING` / `IMPORTING`
  批次与可重试的 `FAILED` 批次」。
- 【契约】**`ParaQuartzTask` NEVER 在单次调度内循环重扫**（`ParaQuartzTask.java:47~48`）：
  「NEVER 在单次调度内循环重扫：para-server 的判据是「版本号 + MD5」，同一批文件在第一轮就已收敛，
  再扫一轮只会重复下载 500KB 却拿到全 skipped。要提高时效性 MUST 调 cron。」
  扫描范围（`:33~34`）：「扫描FTP目录中的路网拓扑(0001)、费率(0004)参数文件，按「版本号 + MD5」判断有无变更，
  有变更时由 para-server 下载并解析入库。」
- 【陷阱】**下游要续 traceId 得自己打开 tracing 开关，且部署后 MUST 实测**：`ParaQuartzTask.java:50~54`
  「⚠️ para-server 侧要真正把 traceparent 续成 MDC 的 `traceId`，需要该模块 `management.tracing.enabled=true`
  （默认值在 `resource/micro/web/src/main/resources/web.properties` 里是 false，目前只有 pay-sign-server 显式打开）。
  未打开时头仍会发出去，但对端 `%X{traceId}` 取不到，只能靠 `x-vlogs-capture` 做日志采集——
  **这一点部署后 MUST 用日志实测确认**。」`CardPoolQuartzTask.java:56~58` 同款但已注明 card-pool-server 已打开。
  ⚠️ 这两处「目前只有 pay-sign-server 显式打开」与 AGENTS.md §2.2.1 的「已打开的模块只有 7 个」不一致（见文末矛盾清单）。
- 【契约】**`AccountQuartzTask.compensatePhoneSignSync` 的扫描对象**（`AccountQuartzTask.java:58~59`）：
  「触发 account-server 扫 USER_PHONE_CHANGE_LOG 里 SIGN_SYNC_STATUS 为 PENDING / FAILED 的行，
  逐条向支付域重推显示账号。扫描范围与批量上限由下游决定，本任务不传参。」
- 【契约】**`TBNoticeAppTask` 四个入口**（`TBNoticeAppTask.java:29~57`）：`testtbNoticeAppTask()`（注释仅「测试」）、
  `noticeTakeTicketTask()`「扫码取票接口 通知app出票结果」、`noticeTakeTicketFailureTask()`「通知app出票故障结果」、
  `noticeRefundTask()`「通知app退款结果」；下游是 collect-pay（`:70`）。
- 【契约】**`QuartzTraceUtils` 是 traceId 与出向 trace 头之间的唯一桥梁**
  （web-quartz · `QuartzTraceUtils` 类注释，
  `web-server/web-quartz/src/main/java/com/chinasofti/huateng/quartz/util/QuartzTraceUtils.java:12~19`）：
  「本类是 `AbstractQuartzJob` 生成的 traceId 与「任务方法往下游传 trace 头」之间的唯一桥梁。
  抽出来的原因：`web-admin` 的 7 个任务类此前各自持有一份**逐字节相同**的 `newTraceId()` / `traceHeaders()`，
  改一处采样标记要同步改七处，漏改无法在编译期发现。
  **NEVER 在任务类里再自行生成 traceId。**…… 另生成一个会让前台「调度日志」里的 traceId 与实际发给下游的对不上，
  运维按前台值去日志系统检索将一无所获。」
- 【契约】**两个 MDC 键的大小写规则**（同类 `:24~28` 与 `:33~38`）：
  `TRACE_ID_KEY`「MUST 用驼峰 traceId：这是 Micrometer Tracing correlation 的字段名，也是各服务日志 pattern
  （`%X{traceId}`）与 VictoriaLogs appender 取值用的键，写成 trace_id 会与全链路对不上。」；
  `VLOGS_CAPTURE_KEY`「**MUST 全小写。**入向请求时这个键由 micro 的 `FirstFilter` 把请求头 `X-Vlogs-Capture` 小写后放入；
  本类为「本端主动发起」的链路补同一个键，好让两侧用同一条 log4j2 过滤规则。写成驼峰会匹配不上
  （`log4j2-linux.xml` 的 `ThreadContextMapFilter` 只认小写）。」
- 【契约/陷阱】**`runWithTrace` 的三条不变量**（同类 `QuartzTraceUtils.runWithTrace`，`:50~60`）：
  「Quartz 路径下直接复用 MDC 里已有的值；非 Quartz 路径（本地手工调用 / 单测）自己生成、自己清理，
  **MUST NOT** 顺手清掉别人放进去的值——否则 `AbstractQuartzJob.after()` 就取不到 traceId 写库了。
  同时往 MDC 放 `VLOGS_CAPTURE_KEY`=`1`：Root 只把 WARN 及以上推给 VictoriaLogs，任务正常跑完全是 INFO，
  不加这个标记则**日志系统里查不到本次调度的任何记录**（2026-09-08 实测：按 sys_job_log 的 traceId 检索返回 0 条）。
  加了之后本端 INFO 全量上报，前台「执行日志」才能看到「调了谁、发了什么头、对端回了什么」。
  该标记 **MUST 在 finally 里还原**：Quartz worker 线程是池化复用的，不清理会让后续任何任务的 INFO 日志
  都被误当成「需要全量采集的链路」推上去。」另注（`:63`）「为每一轮下游请求生成独立 spanId」。
- 【契约/决策】**`traceparent` 末段固定 `00`、且 NEVER 传自定义 traceId 头**（同类 `QuartzTraceUtils.traceHeaders`，`:89~103`）：
  「末段采样标记固定为 `00`（不采样）：下游 `management.tracing.sampling.probability=0` 只为在 MDC 里拿到 traceId 供日志用，
  传 `01` 会让对端按「父 span 已采样」把 span 推给 OTLP collector（`management.otlp.tracing.endpoint`），
  该地址可达性尚未实测，不可达时会持续刷导出失败日志。确认要看 trace 拓扑时再改成 `01`。……
  依赖 micro 的 `FirstFilter` 把请求头**小写**后放进 MDC，因此对端匹配的 MDC 键是 `x-vlogs-capture`，
  **NEVER** 在 log4j2 配置里写成驼峰或原样大小写。**NEVER 传自定义 `traceId` 头**：`FirstFilter` 会把所有请求头
  原样塞进 MDC，自定义头会与 Micrometer 写入的 traceId 争抢同一个键，行为取决于两者执行先后，不可控。」
  另注（`:114`）「生成 32 位小写 hex 的 traceId，与 `AbstractQuartzJob#before` 口径一致」。

### 附四、权限与登录

- 【实测结论】**web-framework（43 个 java 文件）里没有任何 MUST / NEVER 级判据注释**，全部是 RuoYi 原生样板
  （方法名复述 + 作者行）。全模块 grep `否则|禁止|实测|坑|风险|NEVER|MUST|漏洞|安全` 只命中一条
  `web-server/web-framework/src/main/java/com/chinasofti/huateng/framework/aspectj/LogAspect.java:226`
  的「@return 如果是需要过滤的对象，则返回true；否则返回false」——属被丢弃的普通 Javadoc。
  **本节没有可归档的注释知识，照实记录，NEVER 代笔补写。**
- 【契约】**JWT 令牌的两个时间阈值写在注释里**（web-framework · `TokenService`，
  `web-server/web-framework/src/main/java/com/chinasofti/huateng/framework/web/service/TokenService.java:40/44/48/132`）：
  `:40`「令牌自定义标识」、`:44`「令牌秘钥」、`:48`「令牌有效期（默认30分钟）」、
  `:132`「验证令牌有效期，相差不足20分钟，自动刷新缓存」。对应配置键是
  `web-server/web-admin/src/main/resources/application.properties:83~85` 的 `token.header` / `token.secret` /
  `token.expireTime`（**只写键名与位置，不回显值**）。
- 【契约】**登录态缓存在 JVM 本地、按单副本设计**（web-common · `LocalCache` 类注释，
  `web-server/web-common/src/main/java/com/chinasofti/huateng/common/core/cache/LocalCache.java:19`）：
  原文只有一行英文「JVM local cache for single-instance deployments.」——这是「web-admin MUST 单副本」在
  非 Quartz 侧的唯一注释级依据。⚠️ 该文件里 grep 命中的 4 处 `NEVER` 全是常量名 `NEVER_EXPIRE`（`:25`/`:31`/`:63`/`:247`），
  **不是判据**，按关键字统计该文件会误判。
- 【契约】**定时任务接口的权限点**（web-quartz · `SysJobController`，
  `web-server/web-quartz/src/main/java/com/chinasofti/huateng/quartz/controller/SysJobController.java`）：
  `monitor:job:list` / `export` / `query` / `add` / `edit` / `changeStatus` / `remove`（`@PreAuthorize("@ss.hasPermi(...)")`）。
  ⚠️ **出处是注解代码，不是注释**；`add` / `edit` 里那六道拒绝（`rmi` / `ldap(s)` / `http(s)` /
  `Constants.JOB_ERROR_STR` / 白名单 / remark 长度，`:98~119`）同样**只有代码、没有注释**，
  唯一带注释的是 remark 长度那条（见附一）。

### 附五、代码生成器

- 【实测结论】**web-generator（13 个 java 文件 + 2 个 mapper XML）里没有任何判据类注释**：
  全模块 grep `否则|禁止|实测|坑|风险|NEVER|MUST` 零命中，`GenTableMapper.xml` / `GenTableColumnMapper.xml`
  内无 `<!-- -->` 注释。**本节没有可归档的注释知识，照实记录，NEVER 代笔补写。**
- 【实测结论】**web-system（52 个 java 文件 + 17 个 mapper XML）同上**，零命中；
  该模块与本次抽取相关的唯一事实是字典表 `sys_dict_data` 由 `SysDictDataMapper.xml` 读写，
  而 `sys_common_status` 的 `2=进行中` 那一行由 web-quartz 的迁移脚本插入（见附二）。

### 附六、前端契约相关（`web/src`）

- 【契约】**调度日志状态列直接吃字典 `sys_common_status`**（`web/src/views/monitor/job/log.vue:112`
  `<dict-tag :options="sys_common_status" :value="scope.row.status" />`，字典在 `:217`
  `proxy.useDict("sys_common_status", "sys_job_group")` 注入；查询条件下拉在 `:36`）。
  ⚠️ 「缺字典项时该列渲染成空白且后端毫无报错」这条判据**前端文件里没有注释**，
  真实出处是 web-common · `Constants.RUNNING` 的 Javadoc（见附二）与 AGENTS.md §8。
- 【契约】**「执行日志」按钮的可用前提是 `job_message` 里有 32 位 hex traceId**
  （`web/src/views/monitor/job/log.vue:286`）：「是否记录了 traceId：AbstractQuartzJob 把它追加在 job_message 末尾，
  没有则无从检索」，判据是正则 `/traceId=[0-9a-fA-F]{32}/`。
- 【陷阱】**日志时间是 UTC，且 NEVER 用全局 `parseTime` 格式化**（同文件 `formatLogTime`，`:291~308`）：
  「UTC 时间串转本地「YYYY-MM-DD HH:mm:ss.SSS」。后端返回的检索区间与 VictoriaLogs 的 _time 都带 Z 后缀
  （event template 固定 timeZone=UTC），直接展示会比本地时间早 8 小时。
  这里不能用全局 parseTime：它会把 ISO 串里的 - 换成 /、并整段删掉毫秒，遇到 Z 后缀解析结果不可靠，
  而日志排序恰恰要看毫秒。」
- 【契约】**截断提示的文案口径**（同文件 `:183~189` 的 `el-alert`）：
  「日志条数已达单次返回上限，仅展示部分内容，完整链路请到日志系统按该 traceId 检索」；空态文案
  「该 traceId 在日志系统中没有记录」。对应后端约束见附七的 `vlogs.query-limit`。
- 【契约】**前端 API 与后端端点的对应**（`web/src/api/monitor/jobLog.js:12`）：
  「查询调度日志的执行日志（按 job_message 里的 traceId 去日志系统反查全链路）」。
- 【契约/陷阱】**TVM 当面付页面的数据源已换服务、NEVER 退回旧前缀**
  （`web/src/api/trans/facePayOrder.js:3~4`）：「数据源自 2026-09-15（ADR-D85）起由 collect-pay-server
  切至 face-pay-server（F2F_ORDER）。URL 后半段 /page/face-pay/orders 两服务一字不差，只换前缀；
  NEVER 退回 /collect-pay-server。」同文件 `:6`「分页查询 TVM 当面付订单」、
  `:15`「对支付成功的 TVM 当面付订单发起全额退款，退款金额由后台从订单计算」。
  ⚠️ 该注释引的 ADR 号与状态与 AGENTS.md 现行记载冲突（见文末矛盾清单）。
- 【契约】**综管台解约的责任边界**（`web/src/api/trans/userSearch.js:30`）：
  「执行支付平台解约。解约状态和 T+4 收口由 pay-sign-server 负责。」
- 【契约】**日票退款三个入口的入参口径与重试语义**（`web/src/api/trans/dailyTicketRefund.js:17/27/37/47`）：
  前三处均为「服务端固定订单类型为日票，页面只提交业务主键。」；`:47`
  「服务端先查询退款结果，再以同一退款单号重试，防止形成重复退款。」
- 【契约】**服务监控探活入口**（`web/src/api/monitor/serviceStatus.js:3`）：「各微服务运行状态探活聚合。」

### 附七、配置与部署（`web-admin/src/main/resources`）

路径统一省略前缀 `web-server/web-admin/src/main/resources/`。

- 【决策】**`logging.config` 指向本模块自带配置的理由**（`application.properties:10~14`）：
  「指向本模块自带的 log4j2 配置。指定后 micro 的 CustomLoggingConfiguration:38 直接 return，
  公共 log4j2-linux.xml 不再参与。这么做是为了让 VictoriaLogs appender 走 Spring Boot 原生加载路径
  ——公共配置由 Configurator.initialize(name, configLocation) 重载，该 API 不传 ClassLoader，
  2026-09-08 实测在 fat jar 下解析不到 micro 嵌套 jar 里的自定义 appender（配置重载成功、
  RollingFile pattern 生效，但 VictoriaLogs 零上报）。详见 log4j2-web-admin.xml 头部注释。」
- 【契约/陷阱】**下游地址默认值与 env 命名风格**（`application.properties:114~117`）：
  「服务间调用地址（Quartz 定时任务的下游）：默认值一律用集群内网 Service 名（`kubectl get svc -n itp` 实测，2026-09-11）。
  NEVER 写 127.0.0.1（在 K8s 里等于打到 web-admin 自己），也 NEVER 写 `http://<模块名>:8080` 裸名
  （Service 名都带随机后缀，解析不到）。本文件的环境变量命名沿用既有的 `<X>_SERVICE_URL` 风格，
  与其他模块的 `SERVICE_<X>_URL` 不同，NEVER 混用。」同一条在 `:141` 再强调一次
  「本模块的 env 命名是 `<X>_SERVICE_URL`（与业务模块的 `SERVICE_<X>_URL` 相反），NEVER 混用。」
- 【契约】**逐任务标注的下游地址用途**：`:130`「卡池维护定时任务（cardPoolQuartzTask.runMaintenance）的下游地址。」；
  `:133~135`「日终对账定时任务（reconQuartzTask.runDailyBatch）的下游地址。recon-server 自 1.0.8 起不再有
  @EnableScheduling / @Scheduled，触发与频率全由本模块的 sys_job 决定。该调用是同步的（下发抽取后轮询推进直到批次收口），
  ReconClient 的响应超时已放宽到 5 分钟。」；`:138~140`「两个补偿定时任务
  （gateTxnPayQuartzTask.recoverOfflineFare / pushMetroTransfer）的下游地址。gate-txn-pay-server 自 2.0.73 起
  把这两条补偿的 @Scheduled 摘掉了，触发与频率全由本模块的 sys_job 决定（两条 cron 都是 0 0/1 * * * ?、
  concurrent='1' 禁止并发）；NEVER 在那边加回 @Scheduled。」
- 【契约】**探活清单与 `service.*.url` 同源**（`application.properties:145~147`）：
  「===== 综管台服务监控补充探活清单（2026-09-15）===== 与上面 service.*.url 同源同风格：默认值一律用集群内网
  Service 名（kubectl get svc -n itp 实测，2026-09-15）。ServiceStatusService 探活这些地址的 /actuator/health；
  env 命名沿用 `<X>_SERVICE_URL` 风格。」分层小节标注：`:148`「公共能力层」、`:155`「接入层（FEP）」、
  `:160`「支付宝域」、`:162`「ACC / 安全层」。
- 【契约/决策】**探活是「web-server 不直连业务服务」的已知例外，且 NEVER 让整体接口 500**
  （web-admin · `ServiceStatusService` 类注释，
  `web-server/web-admin/src/main/java/com/chinasofti/huateng/web/service/ServiceStatusService.java:24~31`）：
  「综管台服务监控：对静态清单中的各微服务逐个探活 `/actuator/health` 并聚合。
  清单与 `service.*.url` 同源（同一批配置项，默认值对齐集群内网 Service 名），无注册中心可自动发现，
  新增服务 MUST 同步这里的清单与 application.properties。探活只读，单个服务失败只降级成该行的 DOWN/UNKNOWN，
  NEVER 让整体接口 500。本接口属「监控探活」而非业务接口，是 web-server 不直连业务服务这一约束的
  已知例外（设计文档 §四.7 已经用户确认）。」同类 `:40`「探活并发用虚拟线程池：任务是纯 IO 等待，
  平台线程池只会徒增调参负担。」；清单分层注释 `:73`「业务核心层」/`:82`「公共能力层」/`:89`「接入层（FEP）」/
  `:94`「支付宝域」/`:97`「ACC / 安全层」。
- 【契约/陷阱】**VictoriaLogs 检索地址与时间窗**（`application.properties:166~176`）：
  「调度日志「执行日志」检索：VictoriaLogs 查询地址（只读）。与各模块推送用的 VLOGS_URL 是同一个 VictoriaLogs，
  但路径不同：推送走 /insert/jsonline，检索走 /select/logsql/query。只填 scheme://host:port 时代码自动补
  /select/logsql/query。留空即禁用该功能，前台会提示未配置。」「检索时间窗：以 sys_job_log.create_time
  （≈执行结束点）为基准，start = create_time - 耗时(从 job_message 解析) - query-window-before-millis，
  end = create_time + query-window-after-millis。后置余量默认 30 分钟且明显大于前置：批处理任务返回后下游常仍在跑
  （异步线程池 / @Scheduled 补偿 / 超时重试），这些日志时间戳晚于 create_time，尾部窗口太小就查不到。
  放宽窗口时 query-limit MUST 同步跟上——VictoriaLogs 的 limit 不保证顺序，截断是随机丢弃。」
  Java 侧同源约束（web-quartz · `JobTraceLogServiceImpl`）：`:35~37`
  「地址由 `vlogs.query-url` 注入（环境变量 `VLOGS_QUERY_URL`），**为空即功能禁用**并给出明确提示，
  NEVER 写死集群地址——测试与生产的 VictoriaLogs Service 名与端口不同。」「只做只读检索，超时按秒级封顶：
  全服务开了虚拟线程，请求线程上 NEVER 长时间阻塞。」；`:48`「`AbstractQuartzJob.after()` 写进 job_message 的形态是
  `，traceId=<32位hex>`。」；`:51`「同一处写入的 `总共耗时：<n>毫秒`，用于把检索时间窗收窄到本次执行区间。」；
  `:58`「复用单例：HttpClient 每 new 一个都会带起 selector 线程，按请求新建会在管理后台被反复点击时堆积。」；
  `:90~91`「单次返回上限。**NEVER 想当然认为截断后留下的是最早的 N 条**：VictoriaLogs 的 `limit` 不保证顺序，
  排序是本端拿到结果后再做的，所以截断是随机丢弃。放宽时间窗时这个值要同步跟上。」；
  `:135~136`「LogsQL 里 traceId 是普通字段过滤（`_stream_fields` 只有 app / host），选择性足够；
  仍带上 start / end 是为了避免全量扫描。」；`:171`「按 `_time` 升序排列：VictoriaLogs 不保证返回顺序，前台要按时间读。」
- 【陷阱】**日志系统里 web-admin 的 `app` 字段是 `web-server`**（`log4j2-web-admin.xml:30~32`）：
  「…… web-server.log 完全一致。**NEVER 改成 web-admin** —— scripts/klog.sh 走远端 ……
  连带结论：**日志系统里 web-admin 的 app 字段是 `web-server`**，检索时 MUST 用 ……」
- 【陷阱】**业务包 MUST 挂不带 `level` 的 AppenderRef**（`log4j2-web-admin.xml:145~155`）：
  「业务包（含 Quartz 任务包）**MUST** 显式声明并挂一个**不带 level 的** …… 原因（2026-09-08 实测踩坑，绕了一轮）：
  log4j2 把 AppenderRef 的 level 属性编译成 …… **NEVER** 只加 Root 的 ref 就以为够了。」
- 【契约】**同文件另三条**：`:7`「…… ClassLoader**，2026-09-08 实测在 Spring Boot fat jar 下 micro 嵌套 jar 里的自定义
  appender ……」；`:19`「**NEVER** 为了少几行启动日志把它改回 off —— 那正是上面那次排查绕远路的原因。」；
  `:22`「webLoggerLevel / logPath / appName 等系统属性，因此全部 lookup **MUST 自带默认值**。」；
  `:98`「**NEVER** 把 key 写成驼峰——FirstFilter 只放小写键，两侧要一致。」；
  `:83`「由 K8s Deployment 注入 VLOGS_URL，测试集群实测可用值：……」
- 【陷阱】**Druid 监控台与令牌密钥在仓库内是明文默认值**：键名与位置为
  `application.properties:71` `spring.datasource.druid.webStatFilter.enabled`、
  `:72` `statViewServlet.enabled`、`:73` `statViewServlet.allow`、`:74` `statViewServlet.url-pattern`、
  `:75` `statViewServlet.login-username`、`:76` `statViewServlet.login-password`、
  `:81` `spring.datasource.druid.filter.wall.config.multi-statement-allow`、`:84` `token.secret`。
  ⚠️ **这些行本身没有任何注释**，「敏感项 MUST 写成 `${ENV_VAR:}` 空默认值并由 K8s Secret 注入、
  NEVER 写默认真值」这条判据的真实出处是 AGENTS.md §5.2，不是源文件注释。
  本节按要求只记键名与行号，**不回显值**。
- 【契约】**主/从数据源与 Druid 连接池的分节位置**（`application.properties:25`「用户配置」、
  `:39`「主库数据源」、`:43`（一行乱码占位注释，原文为连续问号，疑似编码损坏，见文末无把握清单）、
  `:49`「从库数据源」、`:55`「Druid连接池配置」、`:70`「Druid监控配置」、`:95`「Swagger配置」、
  `:99`「防盗链配置」、`:103`「防止XSS攻击」）。

### 附八、注释与代码 / 其它文档的矛盾（照实记录，不裁决）

1. **「不可自愈记录条数」两处不一致**：`RefundCompensateQuartzTask.java:39` 写「当前库里实测 6 条」，
   `web-quartz-refund-compensate-job-migration.sql:44~46` 写「当天 …… 收口 …… 之后即变成 7 条，
   第 7 条是 ORDER_NO 280638294097559552」，且脚本自己声明该数「只增不减、是时点快照不是阈值」。
   **两处都不可引用，MUST 现查那条 SQL。**
2. **tracing 已打开的模块名单不一致**：`ParaQuartzTask.java:52~53` 与 `CardPoolQuartzTask.java:57~58`
   都写「目前只有 pay-sign-server 显式打开」，而 AGENTS.md §2.2.1 记「已打开的模块只有 7 个」
   （含 card-pool-server / recon-server / gate-txn-pay-server / daily-ticket-server / collect-pay-server /
   alipay-pay-sign-server）。任务类注释是较早的时点快照。
3. **TVM 当面付流量归属不一致**：`web/src/api/trans/facePayOrder.js:3` 写「自 2026-09-15（ADR-D85）起
   由 collect-pay-server 切至 face-pay-server」，而 AGENTS.md §2.2 记 2026-09-16 实测 `fep-app-vr` 的
   `/itptvm/` 与 `/itpbom/` 仍指向 collect-pay（ADR-D112 推翻了「已切」的记载）。
   注意这两件事**不一定冲突**：前端页面查的是综管台只读接口，与设备入向路由是两条独立开关。
4. **`ReconQuartzTask.java:42` 写「默认 4 分钟」**，`application.properties:135` 写「ReconClient 的响应超时已放宽到
   5 分钟」——前者指 `recon.orchestration.run-timeout-millis`，后者指客户端响应超时，是两个值，
   但同一段文字里并列出现时易被读成同一个。
5. **`GateTxnPayQuartzTask.java:18` 写「gate-txn-pay-server 2.0.73 起 …… 都没有 `@Scheduled` 了」**，
   `application.properties:139` 同口径；AGENTS.md §2.2.1 记该模块「现在一个 `@Scheduled` 都没有」——一致，
   但两处都点名那两个 Processor 类**仍留在** gate-txn-pay-server，引用时 MUST 区分「类还在」与「注解没了」。

### 附九、没把握归类的条目（留待第二阶段裁决）

1. `application.properties:43` 是一行**全为连续问号的注释**（`# ???????????????????`），位于「主库数据源」与
   「从库数据源」之间，疑似 GBK/UTF-8 编码损坏。无法判断原文内容，因此既不能归类也不能丢弃，**照实记录**。
2. `web-server/web-admin/src/main/java/com/chinasofti/huateng/ServerApplication.java:34` 是一行被注释掉的代码
   `// System.setProperty("spring.devtools.restart.enabled", "false");`——属「注释掉的代码」而非知识，
   但它与 `ScheduleConfig` 整文件注释掉属同一类历史痕迹，是否要归档为「墓碑」不确定。
3. `TBNoticeAppTask.java:29` 的方法注释只有「测试」两字（`testtbNoticeAppTask()`），
   而该方法是可被前台 `invokeTarget` 调用的真实入口。既像样板注释（该丢弃）又像契约（前台可配的入口清单该保留）。
4. web-quartz · `RyTask`（`.../quartz/task/RyTask.java`）对应 `sys_job` 的 job 1/2/3，注释是 RuoYi 原生样板，
   但那三条任务**确实在库里、每 10~20 秒跑一次**。「样板注释 + 真实在跑」这种组合是否该进正文不确定。
5. `SysJob.java:45`「cron计划策略」与 `:49`「是否并发执行（0允许 1禁止）」属字段注释复述，
   但 `0 允许 / 1 禁止` 这个取值方向正是 `GateTxnPayQuartzTask.java:22` 特意强调的易错点，
   按「丢弃复述型注释」会丢掉一个真实判据，故暂列此处。

### 附十、墓碑注释清单（建议转为断言测试）

格式：`文件:行号` — 禁止的事 — 能否断言化。路径省略 `web-server/` 前缀。

1. `web-quartz/src/main/resources/mapper/quartz/SysJobLogMapper.xml:73` — 禁止把 `insertJobLog` 的主键回填改成
   `selectKey` 取 nextval — **可断言**（解析该 XML 断言含 `useGeneratedKeys="true"` 且不含 `selectKey`；
   或集成测试插入后断言 `jobLogId != null`）。
2. `web-quartz/.../util/AbstractQuartzJob.java:45` — 禁止两侧各写一份 `traceId` 字面量 — **可断言**
   （断言 `AbstractQuartzJob` 的 `TRACE_ID_KEY` 与 `QuartzTraceUtils.TRACE_ID_KEY` 同值，反射取字段即可）。
3. `web-quartz/.../util/AbstractQuartzJob.java:93` — 禁止让「进行中」INSERT 的异常打断任务 — **可断言**
   （mock `ISysJobLogService.addJobLog` 抛异常，断言 `doExecute` 仍被调用一次）。
4. `web-quartz/.../util/AbstractQuartzJob.java:137~138` — 禁止 `after()` 抛出异常 — **可断言**
   （mock `updateJobLog` 抛异常，断言 `after` 正常返回、不向外抛）。
5. `web-quartz/.../util/AbstractQuartzJob.java:191` — 禁止不清理 MDC — **可断言**
   （执行一次后断言 `MDC.get("traceId") == null`）。
6. `web-quartz/.../mapper/SysJobLogMapper.java:60` — 禁止在运行期调用 `closeRunningJobLog` — **不可断言**
   （调用时机约束，只能靠人工评审 / 架构测试限制调用方为启动收口方法）。
7. `web-common/.../constant/Constants.java:58~59` — 禁止新增状态取值而不补字典项 — **可断言**
   （DB 断言 `sys_dict_data` 存在 `sys_common_status` + `dict_value='2'`，或对 `Constants` 里的状态常量集合
   与字典表做集合比对）。
8. `web-quartz/.../util/QuartzTraceUtils.java:16~19` — 禁止在任务类里自行生成 traceId — **可断言**
   （静态扫描 `com.chinasofti.huateng.quartz.task` 包字节码，断言不出现 `UUID`/`newTraceId` 之外的 traceId 生成）。
9. `web-quartz/.../util/QuartzTraceUtils.java:26~28` — 禁止把 MDC 键写成 `trace_id` — **可断言**（常量值断言）。
10. `web-quartz/.../util/QuartzTraceUtils.java:35~38` — 禁止把采集标记键写成驼峰 — **可断言**（常量值断言全小写）。
11. `web-quartz/.../util/QuartzTraceUtils.java:51~52` — 禁止清掉别人放进 MDC 的 traceId — **可断言**
    （预置 MDC 后调用 `runWithTrace`，断言调用后原值仍在）。
12. `web-quartz/.../util/QuartzTraceUtils.java:59~60` — 禁止不在 finally 还原采集标记 — **可断言**
    （调用后断言 `MDC.get("x-vlogs-capture")` 恢复原状，含「原本就有值」与「原本没有」两种用例）。
13. `web-quartz/.../util/QuartzTraceUtils.java:91~95` — 禁止把 `traceparent` 末段改成 `01` — **可断言**
    （断言生成的头以 `-00` 结尾）。
14. `web-quartz/.../util/QuartzTraceUtils.java:102~103` — 禁止传自定义 `traceId` 请求头 — **可断言**
    （断言 `traceHeaders` 返回的 key 集合恰为 `traceparent` + `X-Vlogs-Capture`）。
15. `web-quartz/src/main/resources/mapper/quartz/SysJobMapper.xml:68` — 禁止把 remark 的 `<if>` 改成判空串 — **可断言**
    （集成测试：传空串 remark 后回查该列已被清空）。

16. `web-admin/.../quartz/task/*.java`（11 处，措辞见附三第 1 条）— 禁止「失败只打日志、静默返回」— **可断言**
    （对每个任务类：mock 下游返回 `null` 与 `resultCode != "0000"` 两种，断言均抛异常）。
    这是本模块**最值得先落地的一组断言**：11 个类各写一遍，抄漏一处就是「一直失败却一直显示成功」。
17. `web-admin/.../quartz/task/TerminationQuartzTask.java:86~87` 与
    `AlipayTerminationQuartzTask.java:70~71` — 禁止在循环排空时每轮重新取当前时间 — **可断言**
    （让下游 mock 返回「还有记录」三轮，断言三次请求里的 cutoff 字段值完全相同）。
18. `web-admin/.../quartz/task/TerminationQuartzTask.java:62~70` — 禁止用基本类型 `int` 作任务方法参数 — **可断言**
    （反射断言 `getMethod("confirmTermination", String.class, Integer.class)` 存在；
    更强的版本是扫描整个 task 包，断言所有 public 方法的参数类型里不出现基本类型）。
19. `web-admin/.../quartz/task/GateTxnPayQuartzTask.java:20` 与 `ReconQuartzTask.java:19~20` —
    禁止在 gate-txn-pay-server / recon-server 里加回 `@Scheduled` — **可断言但跨模块**
    （对那两个模块的源码或字节码做静态断言；本模块内无法验证，建议放到各自模块的架构测试里）。
20. `web-admin/.../quartz/task/GateTxnPayQuartzTask.java:48` — 禁止把 job 120/121 的 `concurrent` 改成允许 — **可断言**
    （DB 断言 `sys_job` 中 `job_id in (120,121)` 的 `concurrent='1'`、`misfire_policy='3'`）。
21. `web-admin/.../quartz/task/GateTxnPayQuartzTask.java:83` — 禁止两条任务各写一份收口判定 — **不可断言**
    （结构约束，只能靠评审）。
22. `web-admin/.../quartz/task/NotifyCompensateQuartzTask.java:23~27` — 禁止在单次调度内循环排空；
    调度间隔禁止小于下游 10 分钟阈值 — **部分可断言**（前半段可断言：mock 下游返回「还有记录」，
    断言只调用一次下游；后半段是 `sys_job` 数据约束，需 DB 断言 cron 的分钟步长 ≥ 10）。
23. `web-admin/.../quartz/task/NotifyCompensateQuartzTask.java:72~73` 与
    `RefundCompensateQuartzTask.java:94~95` — 禁止用 `submitted` 判断是否送达 / 单笔结果 — **不可断言**
    （解读约束，落不到代码行为上）。
24. `web-admin/.../quartz/task/RefundCompensateQuartzTask.java:35~36` — 禁止在单次调度内循环排空 — **可断言**
    （同第 22 条前半段）。
25. `web-admin/.../quartz/task/RefundCompensateQuartzTask.java:40~43`、`:111~112` — 禁止把汇总入口的
    `skipped > 0` 写成 `log.error` — **可断言**（挂 log4j2 `ListAppender`，mock 返回 `skipped=3`，
    断言只出现 WARN、不出现 ERROR）。
26. `web-admin/.../quartz/task/BlacklistReleaseInspectQuartzTask.java:19` — 禁止在本任务里解除黑名单 — **可断言**
    （mock `BlacklistClient`，断言只调 inspect、不调任何删除 / 解除方法）。
27. `web-admin/.../quartz/task/BlacklistReleaseInspectQuartzTask.java:25~27` — 禁止单次调度内循环 — **可断言**（同上）。
28. `web-admin/.../quartz/task/BlacklistReleaseInspectQuartzTask.java:49` — 禁止另生成 traceId — **可断言**
    （预置 MDC traceId，断言出向头里的 trace-id 段与之一致）。同款判据另见
    `NotifyCompensateQuartzTask.java:60~62`、`RefundCompensateQuartzTask.java:81~83`、`TBNoticeAppTask.java:68~70`。
29. `web-admin/.../quartz/task/BlacklistReleaseInspectQuartzTask.java:84~85` — 禁止把 SETTLED 清单当可删名单 — **不可断言**
    （人工流程约束；可退一步断言「输出行必须带 reason 原文」）。
30. `web-admin/.../quartz/task/CardPoolQuartzTask.java:50~51` — 禁止把「已受理」当「已导完」— **不可断言**；
    同类 `:53~54`「`accepted=false` 不抛异常」— **可断言**（mock `accepted=false`，断言不抛且不记 error）。
31. `web-admin/.../quartz/task/ParaQuartzTask.java:47~48` — 禁止单次调度内循环重扫 — **可断言**（同第 22 条前半段）。
32. `web-admin/.../quartz/task/TerminationQuartzTask.java:18~19` — 禁止在任务里再写一份延迟天数 — **可断言**
    （断言无参入口发出的请求里天数字段为 null / 未设置）。
33. `web-admin/.../web/service/ServiceStatusService.java:28` — 禁止让单个探活失败导致整体接口 500 — **可断言**
    （mock 一个目标抛异常，断言整体返回 200 且该行状态为 DOWN/UNKNOWN）。
34. `web-admin/.../web/service/ServiceStatusService.java:27` — 禁止新增服务只改一处 — **可断言（弱）**
    （断言清单条目数与 `application.properties` 中 `*.url` / `*_SERVICE_URL` 键数量一致）。
35. `web-quartz/.../service/impl/JobTraceLogServiceImpl.java:36` — 禁止写死集群地址 — **可断言**
    （断言该字段来自 `@Value("${vlogs.query-url:}")`，且为空时接口返回「未配置」提示而非空列表）。
36. `web-quartz/.../service/impl/JobTraceLogServiceImpl.java:90~91` — 禁止假定截断后留下最早的 N 条 — **不可断言**
    （对外部系统行为的认知，只能靠文档）。
37. `web-quartz/.../service/impl/JobTraceLogServiceImpl.java:38~40` — 禁止指望从查询结果取
    `start_time` / `stop_time` — **可断言**（解析 `SysJobLogMapper.xml` 的 resultMap，断言不含这两列）。
38. `web/src/views/monitor/job/log.vue:291~308` — 禁止用全局 `parseTime` 格式化带 Z 后缀的时间 — **可断言**
    （前端单测：输入 `2026-09-16T01:02:03.456Z`，断言输出含毫秒且为本地时区）。
39. `web/src/api/trans/facePayOrder.js:4` — 禁止把 URL 前缀退回 `/collect-pay-server` — **可断言**
    （断言导出函数里的 url 常量前缀）。
40. `web-admin/src/main/resources/application.properties:115~117`、`:141` — 禁止把下游地址写成 `127.0.0.1`
    或裸 Service 名；禁止把 env 命名与业务模块的 `SERVICE_<X>_URL` 混用 — **可断言**
    （配置断言测试：加载 properties，断言所有 `service.*.url` 默认值不含 `127.0.0.1`、不匹配 `http://[a-z-]+:\d+$`）。
41. `web-admin/src/main/resources/log4j2-web-admin.xml:19`、`:22`、`:30`、`:98`、`:155` — 分别禁止：
    把 `status` 改回 `off`、写不带默认值的 lookup、把 `appName` 改成 `web-admin`、把采集标记键写成驼峰、
    只在 Root 上挂 VictoriaLogs 的 AppenderRef — **可断言**
    （XML 断言测试：解析该文件逐条检查；其中「不带默认值的 lookup」可用正则断言所有 `${...}` 都含 `:-`）。
42. `web-quartz/.../config/ScheduleConfig.java:1~57`（整文件注释掉）— 禁止启用数据库 JobStore / 集群模式 — **可断言（弱）**
    （断言运行时 `Scheduler.getMetaData().getJobStoreClass()` 是 `RAMJobStore`；比读注释可靠得多，
    也顺带把「`QRTZ_*` 恒 0 行」这条运维判据钉死）。

## 附：web-server 与管理前端 注释知识迁移（2026-09-16，阶段二·完整）

> **与上一节（阶段一）的关系**：阶段一只抽了 624 行、且只覆盖 web-admin 的 Quartz 任务类与部分配置；
> 本节是**闭环迁移**——`web-server/src/main`（313 java / 25 xml / 6 properties / 13 vm）+ 19 个 SQL 脚本
> + 前端 `web/src`（111 vue / 80 js）全量过一遍，把「代码里读不出来的判据」抽到这里，
> 源码侧只保留标准 Javadoc（`@param` / `@return` / 一行式摘要 / 字段一行语义）。
>
> **每条给 `路径:行号` + 可 grep 的原文短语。行号是迁移前基线**（删注释后会前移），
> 定位 **MUST 用 grep 短语，NEVER 直接跳行号**。
>
> **本节不改变任何事实**：与 `AGENTS.md` §8「Quartz 内存 JobStore」「`SYS_JOB_LOG` 四处配套」两条重复的部分，
> 以 AGENTS.md 为准；本节负责的是**代码级细节**（哪个类、哪一行、为什么这么写）。

### 附A、web-quartz：Quartz 装配与内存 JobStore

1. **`ScheduleConfig.java` 整文件是注释掉的 57 行**（原 `web-quartz/.../config/ScheduleConfig.java:1~57`，
   grep 「定时任务配置（单机部署建议删除此类和qrtz数据库表」）。被注释掉的内容是 RuoYi 原生的
   `SchedulerFactoryBean` + `LocalDataSourceJobStore` + `isClustered=true` + `tablePrefix=QRTZ_` +
   `threadCount=20` / `threadPriority=5` / `misfireThreshold=12000` / `startupDelay=1` /
   `overwriteExistingJobs=true` / `autoStartup=true`。
   **连带的三条运行期事实**：①没有 `spring.quartz.job-store-type` 配置 ⇒ Boot 默认 `RAMJobStore`；
   ②`QRTZ_JOB_DETAILS` / `QRTZ_TRIGGERS` **恒 0 行，不等于任务没跑**；
   ③`quartz.sql` / `quartz_oracle.sql`（含 90 条 `COMMENT ON`）建的 11 张 `QRTZ_*` 表**当前无人写**。
   **NEVER 因为「这个类被注释了看着像遗留」就删文件或启用它** —— 启用即切成数据库 JobStore，
   与「web-admin 单副本 + 内存加载」整套现状冲突。
2. **任务注册时机**：`SysJobServiceImpl.init()` 上的 `@PostConstruct` 启动时从 `sys_job` 全量加载
   （`web-quartz/.../service/impl/SysJobServiceImpl.java`，grep 「@PostConstruct」）。
   因此**运行中直接 INSERT / UPDATE `sys_job` 不生效**，MUST 重启 web-admin 或在后台对该任务改一次保存。
3. **任务类必须落在白名单包** `com.chinasofti.huateng.quartz.task`
   （`web-common/.../constant/Constants.java` 的 `JOB_WHITELIST_STR`，校验点
   `web-quartz/.../util/ScheduleUtils.java` grep 「检查包名是否为白名单配置」）。放别的包前台直接报「违规」。
4. **带数字参数的任务方法 MUST 用包装类型 `Integer`，NEVER 用 `int`**
   （原 `TerminationQuartzTask.java:58~74`，grep 「getMethod` 按精确类型匹配」）。
   `JobInvokeUtil.getMethodParams` 把不带后缀的数字一律解析成 `Integer.class`
   （只有 `L`→Long、`D`→Double、引号→String、`true/false`→Boolean），随后用
   `getClass().getMethod(名, 类型数组)` 反射查找，而 **`getMethod` 不做自动装箱**。
   签名写 `int` 时后台调用必抛 `NoSuchMethodException: confirmTermination(java.lang.String, java.lang.Integer)`，
   `0` / `0L` / `0D` 全都绕不过。2026-09-14 实测到（job 4，`job_log_id=5034`）。
5. **`sys_job.remark` 的前台长度上限等于列宽 `VARCHAR2(500 CHAR)`**
   （`web-quartz/.../controller/SysJobController.java`，grep 「等于 sys_job.remark 的列宽」）。

### 附B、`SYS_JOB_LOG`「开始即入库、收口回写」四处配套（web-admin 1.1.18 起）

改动前的实现只有 `AbstractQuartzJob.after()` 写库，于是**任务在跑的整段时间里表里一行都没有**：
日终对账单次 60 秒以上，用户点完「执行一次」查不到任何记录，既不知道有没有真的开始，也拿不到 traceId
去日志系统跟；进程中途重启更是**连失败记录都不留**。现在 `before()` 先 INSERT 一行 `STATUS='2'`，
`after()` 按主键 UPDATE 成 `'0'` / `'1'`。**四处配套，改一处 MUST 看齐其余三处**：

1. **mapper 三条语句**（`web-quartz/src/main/resources/mapper/quartz/SysJobLogMapper.xml`）：
   - `insertJobLog` 带 `useGeneratedKeys="true" keyProperty="jobLogId" keyColumn="job_log_id"`
     （原 `:72~74`，grep 「job_log_id 是 Oracle IDENTITY 列」）。`JOB_LOG_ID` 是 Oracle **IDENTITY 列
     （`DEFAULT ON NULL`）、库里没有对应序列**，回填主键只能靠 JDBC `getGeneratedKeys`，
     **NEVER 改成 `selectKey` 取 nextval**；回填是「先插进行中、后按主键 UPDATE」的前提。
   - `updateJobLog`（原 `:97~98`，grep 「执行收口时按主键回写结果」）：`create_time` 保持插入时刻
     （= **任务开始时间**，此前等于结束时刻）；`exception_info` 用 `#{}` 而不是 `<if>`，
     因为成功时需要把上一次残留清空。
   - `closeRunningJobLog`（原 `:107`，grep 「web-admin 重启时把上次进程遗留的」）。
2. **字典项**：`Constants.RUNNING = "2"`（`web-common/.../constant/Constants.java`，grep 「通用进行中标识」）
   **必须**在 `sys_dict_data` 的 `sys_common_status` 里有对应行。
   迁移脚本 `web-quartz/src/main/resources/sql/web-quartz-job-log-running-status-migration.sql` 是
   `INSERT ... SELECT 3,'进行中','2','sys_common_status',NULL,'warning','N','0' ... WHERE NOT EXISTS`（幂等），
   2026-09-14 已执行、回查 `DICT_CODE=100` / `LIST_CLASS='warning'`。
   **缺这一行时前台 `web/src/views/monitor/job/log.vue:112` 的状态列渲染成空白、后端毫无报错。**
3. **两个写库点都在方法内 catch 全部异常只记日志**
   （`web-quartz/.../util/AbstractQuartzJob.java`，grep 「落一行「进行中」」/「NEVER 让它打断任务执行」）：
   记日志不该打断任务；主键没回填到时 `after()` 自动退化成 INSERT、结果照样落库。
   **`after()` 尤其 NEVER 允许抛出** —— `execute()` 的 catch 分支会再调一次 `after`，
   抛出去就成了「同一次执行被回写两遍、第二遍还带假的失败状态」。
4. **启动收口**：`SysJobServiceImpl.init()` 把遗留 `STATUS='2'` 一律收口为失败 +
   「web-admin 重启，本次执行结果未知」（grep 「收口上一个进程遗留的「进行中」调度日志」，
   服务/mapper 侧对应 `closeRunningJobLog`，grep 「本方法 NEVER 用于运行期」）。
   内存 JobStore 下**进程一停，正在跑那次的结果永远没人回写**，不扫就永久悬挂。

另两条**字段级事实**：`SysJobLog` 的 `startTime` / `stopTime` **始终没有对应列**（见该 mapper 的 resultMap），
只用于算耗时；`sys_job_log.job_message` 里带 `traceId=`，是前台「执行日志」按 traceId 反查的唯一入口。

### 附C、现行 `sys_job` 全量清单（16 条，编号 + cron）

依据 `web-quartz/src/main/resources/sql/web-quartz-refund-compensate-job-migration.sql` 头部注释
（原 `:1~43`，grep 「编号依据：2026-09-15 实测」）里那份 14 行实测清单，加上该脚本自己新增的 122 / 123。
**该脚本已把这份清单写在注释里，本节是它的迁移目的地；`JOB_ID` 是 IDENTITY 列但可显式赋值**
（与现有 105~121 那批同一做法），脚本用「先 DELETE 再 INSERT」保证幂等。

| job_id | 名称 | invokeTarget | cron |
|---|---|---|---|
| 1 | 系统默认（无参） | `ryTask.ryNoParams` | `0/10 * * * * ?` |
| 2 | 系统默认（有参） | `ryTask.ryParams('ry')` | `0/15 * * * * ?` |
| 3 | 系统默认（多参） | `ryTask.ryMultipleParams(...)` | `0/20 * * * * ?` |
| 4 | 解约申请确认 | `terminationQuartzTask.confirmTermination(...)` | `0 0 4 * * ?` |
| 5 | 支付宝出行销卡 | `alipayTerminationQuartzTask.cancelCard()` | `0 0 2 * * ?` |
| 6 | 签约结果通知补发 | `notifyCompensateQuartzTask.compensateSignNotify()` | `0 0/10 * * * ?` |
| 7 | 解约结果通知补发 | `notifyCompensateQuartzTask.compensateTerminationNotify()` | `0 5/10 * * * ?` |
| 105 | 黑名单可解除性盘点 | `blacklistReleaseInspectQuartzTask.inspect()` | `0 0 10,16 * * ?` |
| 106 | ACC 参数文件同步 | `paraQuartzTask.scanFtpPara()` | `0 2/10 * * * ?` |
| 107 | 卡池维护 | `cardPoolQuartzTask.runMaintenance()` | `0 0/5 * * * ?` |
| 108 | 签约展示账号同步补偿 | `accountQuartzTask.compensatePhoneSignSync()` | `0 0/5 * * * ?` |
| 109 | 日终对账 | `reconQuartzTask.runDailyBatch()` | `0 30 2 * * ?` |
| 120 | 离线码金额补偿 | `gateTxnPayQuartzTask.recoverOfflineFare()` | `0 0/1 * * * ?` |
| 121 | 公交换乘推送 | `gateTxnPayQuartzTask.pushMetroTransfer()` | `0 0/1 * * * ?` |
| 122 | 退款回查补偿 | `refundCompensateQuartzTask.compensateRefundQuery()` | `0 0/10 * * * ?` |
| 123 | 退款汇总跨表对账 | `refundCompensateQuartzTask.compensateRefundSummary()` | `0 15 * * * ?` |

**清单外的列取值一律照现有行抄**：`job_group='DEFAULT'`、`misfire_policy='3'`（错过不补跑）、
`concurrent='1'`（**注意 `'0'` 才是允许并发**）、`status='0'`、`create_by='admin'`。

**四条执行前置（原脚本注释 `:5~10`，grep 「执行方式与生效条件（MUST 先读完再执行）」）**：
①内存 JobStore ⇒ 运行中 INSERT `sys_job` 不生效，执行完 MUST 重启 web-admin 或在后台各改存一次；
②**web-admin MUST 单副本**，多副本每份各加载一次、同一时刻重复触发下游补偿；
③`misfire_policy=3` ⇒ 重启期间错过的调度不追补；
④判断任务有没有跑 MUST 查 `SYS_JOB_LOG`，`QRTZ_*` 在内存 JobStore 下恒 0 行。

**cron 与「开始即入库」的连带效应**：`SYS_JOB_LOG` 现在每次执行落两次写（插 + 回写），
**cron 每打密一档，那张表日增行数就翻一档**（原 `GateTxnPayQuartzTask.java:58~64`，
grep 「cron 每打密一档那张表日增行数就翻一档」）。120 / 121 两条是每分钟一轮。

### 附D、web-admin `quartz/task` 任务类：收口判据与逐类差异

**全类共用的三条**（每个类的注释里各写了一份，源码侧删掉后以此处为唯一出处）：
- **失败 MUST 抛异常**。`sys_job_log` 的成功/失败判定**只看有没有异常抛出**，
  「响应为 null 或 `resultCode != "0000"` 只打日志然后正常返回」= 把失败记成成功
  （grep 「Quartz 只以异常判定失败」，11 个类里出现 10 次）。
- **NEVER 在任务类里自行生成 traceId**。Quartz 进来时 traceId 已由 `AbstractQuartzJob.before()` 放进 MDC，
  并被 `after()` 写进 `sys_job_log.job_message`；`QuartzTraceUtils.runWithTrace` 直接复用它。
  另生成一个 ⇒ **前台调度日志里的 traceId 与实际发给下游的对不上**，运维按前台值检索一无所获
  （grep 「MUST NOT 另生成一个」）。
- **任务类 MUST 在白名单包内**（见附A.3）。

逐类差异（**这些是各自独有的、抄错就出事**）：

1. **`TerminationQuartzTask`（job 4）**：解约申请满 N 天才确认，**延迟天数只由 pay-sign-server 的
   `termination.confirm-delay-days` 控制，本任务只传基准时间**，NEVER 在这里再写一份天数（两处会漂移）。
   `confirmTermination` **在单次调度内循环排空**直到 cutoff 前无待处理；
   **整个循环复用同一个 request、cutoff 固定不变，NEVER 每轮重取当前时间**（边界会随耗时漂移、
   刚好卡边界的申请被漏掉）。另有「单次调度内最多调下游多少轮」的上限常量（grep 「单次调度内最多调下游多少轮」）。
2. **`AlipayTerminationQuartzTask`（job 5）**：同样是循环排空 + cutoff 固定；
   补跑入口示例 `alipayTerminationQuartzTask.cancelCard('20260907')`，参数 `yyyyMMdd` 或 `yyyyMMddHHmmss`，
   只处理登记时间早于它的记录。
3. **`NotifyCompensateQuartzTask`（job 6 / 7）**：两个入口扫**不同的表**
   （`compensateSignNotify` → `APP_PAY_SIGN_REQUEST`，`compensateTerminationNotify` → `APP_TERMINATION_REQUEST`），
   pay-sign 侧也是两个独立接口，**不可互相替代**，前台各建一条 `sys_job`。
   **NEVER 在单次调度内循环排空**：下游在提交重发**之前**就同步递增 `NOTIFY_RETRY_COUNT` 并置 FAILED，
   真正通知是异步发的；同一次调度里再来一轮会立刻扫到同一批（计数已 +1、异步结果未回写），
   **几秒内把重试预算烧光**。排空只能靠 cron，**调度间隔 MUST 大于下游 `PENDING_STALE_MINUTES`=10 分钟**。
   **NEVER 用 `submitted` 判断通知是否送达** —— 它只代表「提交成功」，真实结果由下游异步回写
   `NOTIFY_STATUS` / `NOTIFY_RESULT`。
4. **`RefundCompensateQuartzTask`（job 122 / 123）**：两个入口是两件不同的事，也不可互替。
   - `compensateRefundQuery` → `POST /internal/payment/compensateRefundQuery`，扫 `PAY_REFUND_DETAIL`
     停在 `PROCESSING` 的退款单，**会出网**调支付中心 §3.2 `refundQuery` 回查并 CAS 收口；
     它是 `requestRefund` 摘掉 `@Transactional` 后的配套补偿，**停掉等于让那批单子永久悬挂**。
     下游只捞「距上次发起退款已超 `REFUND_QUERY_STALE_MINUTES`=5 分钟」的单子，
     **cron MUST 大于 5 分钟**（建议 `0 0/10 * * * ?`），更短只会白跑一轮还重复出网。
   - `compensateRefundSummary` → `POST /internal/payment/compensateRefundSummary`，**不出网**，
     只重算 `PAY_TXN_DETAIL` 的 `REFUND_AMOUNT` / `REFUND_STATUS`。
     **它的 `skipped` 长期非 0 是预期、不是故障**：其中含「明细已 SUCCESS 但 `PAY_TXN_DETAIL` 里
     没有该 `ORDER_NO`」这类**不可自愈**记录，每轮都会重复计入。因此**汇总入口 MUST 用 `log.warn`、
     NEVER 抄 `NotifyCompensateQuartzTask` 那句「`skipped > 0` 就 `log.error`」** ——
     那会让调度日志每轮报 ERROR、把真实故障淹掉。条数以下面这条 SQL **现查为准**、
     **NEVER 当告警阈值或断言基线**（该值只随「退款回查收口成功」单向增加）：
     `SELECT COUNT(DISTINCT R.ORDER_NO) FROM PAY_REFUND_DETAIL R WHERE R.REFUND_STATUS = 'SUCCESS'
     AND NOT EXISTS (SELECT 1 FROM PAY_TXN_DETAIL P WHERE P.ORDER_NO = R.ORDER_NO)`。
     历史：初版注释写「当前 6 条」，同日 2.0.88 修掉 `refundQuery` 字段名缺陷（ADR-D92）、
     收口 `RF2026062516090566197559552` 后即变 7 条（第 7 条 `ORDER_NO` 280638294097559552）——
     **「改一行 remark 就过期一次」本身就是「不要写死数字」的判据**。

5. **`GateTxnPayQuartzTask`（job 120 / 121）**：**这两条补偿链路的唯一调度源**。
   gate-txn-pay-server 2.0.73 起 `OfflineFareRecoveryProcessor` / `MetroTransferPushTaskProcessor`
   都没有 `@Scheduled`，频率完全由 cron 决定；**NEVER 在那两个类上加回 `@Scheduled`** ——
   两套调度源互不知情，**离线码那条会并发发起扣款**。
   两条的 `sys_job` **MUST 配「禁止并发」`concurrent='1'`**：原实现是 `fixedDelay`（上一轮跑完再等 N 秒），
   cron 表达不了这个语义，不重叠只靠禁并发保证。
   `recoverOfflineFare` **会真的扣款**，下游靠抢占式条件更新（`applyOfflineFareRecalculated` 返 1 才继续），
   早跑一轮最坏只是空扫，**但禁并发那一列 NEVER 改成允许**。
   `pushMetroTransfer` 的轮询周期**由 10 秒改成 60 秒**（用户 2026-09-15 裁决），公交卡侧收到推送的时延上限随之变化。
   下游**恒返 `retCode=0000`**（本轮扫表异常属可自愈、下一分钟重入），所以这里的 `retCode` 判定
   实际只在「无响应 / 不可达」时生效；**本轮结论在 `retMsg` 里、会进 `sys_job_log`，
   排查「补偿为什么不动」MUST 先看那一列**（能区分「开关未开启」「扫表异常」「本轮 0 笔」三种情形）。
   两条任务共用一个收口判定方法，**NEVER 让两条各写一份**。
6. **`ReconQuartzTask`（job 109）**：**日终对账的唯一调度源**，recon-server 已按用户 2026-09-11 要求
   删掉 `@EnableScheduling`、一个 `@Scheduled` 都没有；**NEVER 在 recon-server 那边加回**
   （两套调度源会重复建批次与重复投递）。账期由下游按 `recon.orchestration.window-offset-days` 推算（T-2 日），
   **本任务不传参 ⇒ 重复触发幂等**。下游**同步**跑完才返回，单次实测约 60 秒、上限由
   `recon.orchestration.run-timeout-millis`（默认 4 分钟）控制，因此**执行策略建议「禁止并发」**。
7. **`CardPoolQuartzTask`（job 107）**：card-pool-server **本模块不注册 `@Scheduled`**，
   卡池的回收 / 补货 / 批次推进全靠本任务，**这个 Bean 一旦不跑，卡池只会被消耗、不会被补充**。
   下游是**受理式**接口：提交给单线程维护池后立刻返回，ACC 申请 / FTP 下载 / 十万行入库都在下游后台跑
   ⇒ **本方法返回成功只代表「已受理」，NEVER 当成「这一轮已导完」**（结果看下游日志与
   `/card-pools/summary` 水位）。**`accepted=false`（上一轮未结束）不抛异常**：
   一次十万行导入远超 5 分钟间隔，常态会被限流丢弃，抛异常会让调度日志长期一片红、真故障被淹掉。
8. **`ParaQuartzTask`（job 106）**：**NEVER 在单次调度内循环重扫** —— para-server 的判据是
   「版本号 + MD5」，同一批文件第一轮就收敛，再扫只会重复下载 500KB 拿到全 skipped；提高时效性 MUST 调 cron。
9. **`AccountQuartzTask`（job 108）**：触发 account-server 扫 `USER_PHONE_CHANGE_LOG` 里
   `SIGN_SYNC_STATUS` 为 PENDING / FAILED 的行，逐条向支付域重推显示账号；**扫描范围与批量上限由下游决定，
   本任务不传参**。
10. **`BlacklistReleaseInspectQuartzTask`（job 105）**：**只读盘点，NEVER 解除任何黑名单**。
    它把每条记录的欠费事实（闸机出站扣费 + 支付宝出行两个源）查清后输出日志，由人工据 `REASON` 判断。
    不直接删的理由：`BLACKLIST` 只有 5 列、**没有拉黑类型字段**，`REASON` 是四个来源混写的自由文本，
    生产实测 35 条 ADD 里 22 条是「用户挂失补卡」——与欠费无关，按「欠费结清」删掉等于**让挂失旧卡恢复过闸**。
    等拉黑类型落库、且支付宝侧修掉「幂等拒答误判成扣款失败」后才能加自动删除。
    输出 **MUST 带 `reason` 原文**：`SETTLED` 只代表钱结清，**NEVER 把这份清单当「可以删的名单」执行**。
    与 `NotifyCompensateQuartzTask` 一样**不在单次调度内循环**（只读盘点，循环只会重复输出同一批），
    批量范围由 blacklist-server 的 `blacklist.inspect.batch-size` 控制。
11. **`TBNoticeAppTask`**：四个入口（collect-pay 旧表通知）结果判定完全一致，统一在一个私有方法里做。

### 附E、traceId / 日志采集：`QuartzTraceUtils` 与 `AbstractQuartzJob`

1. **`QuartzTraceUtils` 存在的理由**（`web-quartz/.../util/QuartzTraceUtils.java`）：web-admin 的 7 个任务类
   此前各自持有一份**逐字节相同**的 `newTraceId()` / `traceHeaders()`，改一处采样标记要同步改七处、
   漏改在编译期发现不了。它是「`AbstractQuartzJob` 生成的 traceId」与「任务方法往下游传 trace 头」之间的唯一桥梁。
2. **两个 MDC 键的大小写是硬约束**：
   - `traceId` **MUST 驼峰** —— 这是 Micrometer Tracing correlation 的字段名，也是各服务日志 pattern
     `%X{traceId}` 与 VictoriaLogs appender 取值用的键，写成 `trace_id` 会与全链路对不上。
   - `x-vlogs-capture` **MUST 全小写** —— 入向请求这个键由 micro 的 `FirstFilter` 把请求头
     `X-Vlogs-Capture` **小写后**放入；本端主动发起的链路补同一个键，两侧才能共用一条 log4j2 过滤规则。
     写成驼峰匹配不上（`ThreadContextMapFilter` 只认小写）。
   - `AbstractQuartzJob` 与 `QuartzTraceUtils` 两侧 **MUST 引用同一个常量，NEVER 各写一份字面量**。
3. **`runWithTrace` 的两条不变量**：①Quartz 路径复用 MDC 里已有的值；非 Quartz 路径（本地手工调用 / 单测）
   自己生成自己清理，**MUST NOT 顺手清掉别人放进去的值**，否则 `after()` 取不到 traceId 写库；
   ②`x-vlogs-capture=1` **MUST 在 finally 里还原** —— Quartz worker 线程池化复用，
   不清理会让后续**任何**任务的 INFO 日志都被当成「需全量采集的链路」推上去。
   同理 `AbstractQuartzJob` 在 finally 清 MDC（grep 「Quartz 线程池化复用，MUST 在此清理」），
   否则下一个任务在 `before()` 覆盖前会短暂带着上一次的 traceId。
4. **不加 `x-vlogs-capture` 就查不到本次调度的任何记录**：Root 只把 WARN 及以上推给 VictoriaLogs，
   任务正常跑完全是 INFO。2026-09-08 实测：按 `sys_job_log` 的 traceId 检索返回 0 条。
5. **`traceparent` 末段采样标记固定 `00`（不采样）**：下游 `management.tracing.sampling.probability=0`
   只为在 MDC 里拿到 traceId 供日志用；传 `01` 会让对端按「父 span 已采样」把 span 推给
   `management.otlp.tracing.endpoint`，**该地址可达性尚未实测**，不可达时会持续刷导出失败日志。
   要看 trace 拓扑时再改 `01`。
6. **NEVER 传自定义 `traceId` 请求头**：`FirstFilter` 把所有请求头原样塞进 MDC，
   自定义头会与 Micrometer 写入的 traceId 争抢同一个键，行为取决于执行先后、不可控。
7. **下游要真拿到 traceId，还取决于该模块有没有开 `management.tracing.enabled`**
   （默认值在 `resource/micro/web/src/main/resources/web.properties` 是 `false`）。
   **已打开的名单在 `AGENTS.md` §2.2.1，NEVER 在代码注释或本节再嵌一份会过期的枚举**；
   `para-server` / `card-pool-server` 两个任务类的注释里都提过这件事，其中 para-server **不在**名单内 ——
   未打开时头仍会发出去，但对端 `%X{traceId}` 取不到，只能靠 `x-vlogs-capture` 做采集，
   **这一点部署后 MUST 用日志实测确认**。

### 附F、web-admin 的日志配置：为什么不用公共 `log4j2-linux.xml`

依据 `web-admin/src/main/resources/log4j2-web-admin.xml` 头部与各 appender 注释、
以及 `application.properties:10~14`（grep 「指向本模块自带的 log4j2 配置」）。

1. **根因**：公共配置由 micro 的 `CustomLoggingConfiguration` 在 `ApplicationReadyEvent` 时用
   `Configurator.initialize(name, configLocation)` 重载，**该 API 不传 ClassLoader**；
   2026-09-08 实测在 Spring Boot fat jar 下，micro 嵌套 jar 里的自定义 appender（VictoriaLogs）
   **没有被实例化**：配置确实重载成功（日志里有 `Log4j2 configuration has been reloaded from
   classpath:log4j2-linux.xml`）、RollingFile pattern 也生效，但 **VictoriaLogs 一条都没上报**；
   同一个 appender 在 pay-sign 走 Boot 原生 `logging.config` 加载时正常工作。
   指定 `logging.config` 后 `CustomLoggingConfiguration:38` 直接 return，公共配置不再参与。
2. **`status="warn"` 是故意的，NEVER 改回 `off`** —— appender / plugin 解析失败时能在 stdout 看到原因；
   公共配置写着 `status="off"`，log4j2 自己的 `Unable to locate plugin` 一类自述日志被吞掉，
   那次排查只能靠「上报为 0」反推。
3. **本文件由 Boot 在启动早期加载**，此时 `CustomLoggingConfiguration` 还没设置
   `webLoggerLevel` / `logPath` / `appName` 等系统属性，因此**全部 lookup MUST 自带默认值**。
4. **`appName` 保持 `web-server`（= `spring.application.name`），NEVER 改成 `web-admin`**：
   `scripts/klog.sh` 走远端 `k8s-logview.sh` 按既有文件名 `web-server.log` 取日志。
   连带结论：**日志系统里 web-admin 的 `app` 字段就是 `web-server`**，检索 MUST 用 `app:"web-server"`，
   用 `app:"web-admin"` 查出 0 条（2026-09-08 已踩过一次）。
5. **VictoriaLogs appender**：`url` 为空即自动禁用（不起 `vlogs-sender` 线程、不影响业务），由 K8s
   Deployment 注入 `VLOGS_URL`；测试集群实测可用值 `http://victoria-logs-pbi6a-svc.itp.svc:30032` ——
   **端口是 30032 而不是 VictoriaLogs 默认的 9428**（该 Service 的 port 与 nodePort 都是 30032，
   targetPort 才是 9428，写 9428 连不上）。只给 `scheme://host:port` 时代码补
   `/insert/jsonline?_stream_fields=app,host`；**stream 字段只能放 app/host 这类低基数字段**，
   traceId 走正文（LogsQL 仍可 `traceId:"xxx"` 检索）。
6. **两级过滤，`CompositeFilter` 遇 ACCEPT / DENY 立即短路**：①MDC `x-vlogs-capture=1` ⇒ ACCEPT，
   该链路按 Root 级别（INFO）全量上报；②未命中落到 `ThresholdFilter`，只有 WARN 及以上才上报。
7. **业务包 MUST 显式声明 logger 并挂一个「不带 level 的」`<AppenderRef ref="VictoriaLogs" />`**，
   否则 `x-vlogs-capture` 链路采集根本不生效。原因（2026-09-08 实测绕了一轮）：
   log4j2 把 `AppenderRef` 的 `level` 属性编译成**引用上的过滤器，先于 appender 自己的 `<Filters>` 求值**。
   只靠 Root 的 `<AppenderRef ref="VictoriaLogs" level="WARN" />` 时，任务的 INFO 事件在「引用」这层就被 DENY，
   **永远走不到** appender 上的 `ThreadContextMapFilter`，于是「appender 已启用 + sender 线程在跑 +
   MDC 键确实写入 + Root 的 WARN 能上报」四项全成立却查不到 INFO —— 现象极像过滤器不匹配，实为引用层短路。
   pay-sign 能工作正是因为它有对应的 `com.chinasofti.huateng` logger（`log4j2-paysign.xml:151`）。
   另：RollingFile 侧 `ThresholdFilter="INFO"` 兜底，**配成 DEBUG 会把滚动文件灌满**。

### 附G、`web-admin/src/main/resources/application.properties`：配置项语义

1. **`service.*.url` 一组（Quartz 下游）**（原 `:114~117`，grep 「服务间调用地址（Quartz 定时任务的下游）」）：
   默认值一律用集群内网 Service 名（`kubectl get svc -n itp` 实测，2026-09-11）。
   **NEVER 写 `127.0.0.1`**（在 K8s 里等于打到 web-admin 自己）；
   **NEVER 写 `http://<模块名>:8080` 裸名**（Service 名都带随机后缀，解析不到）。
   **本文件的环境变量命名是 `<X>_SERVICE_URL`，与其他模块的 `SERVICE_<X>_URL` 相反，NEVER 混用** ——
   这条在文件里出现两次（`:117` 与 `:141`），是因为两组配置各写了一遍。
2. **`service.recon.url`**（原 `:133~135`）：recon-server 自 1.0.8 起不再有 `@EnableScheduling` / `@Scheduled`；
   该调用是**同步**的（下发抽取后轮询推进直到批次收口），`ReconClient` 的响应超时已放宽到 **5 分钟**。
3. **`service.gateTxnPay.url`**（原 `:138~141`）：gate-txn-pay-server 自 2.0.73 起摘掉两条补偿的 `@Scheduled`，
   触发与频率全由本模块 `sys_job` 决定（两条 cron 都是 `0 0/1 * * * ?`、`concurrent='1'`）。
4. **综管台服务监控的补充探活清单**（原 `:145~165`，grep 「综管台服务监控补充探活清单」）：与上面
   `service.*.url` 同源同风格，按「公共能力层 / 接入层（FEP）/ 支付宝域 / ACC 安全层」分四组；
   `ServiceStatusService` 探活这些地址的 `/actuator/health`。
5. **`vlogs.query-url` 与检索时间窗**（原 `:166~176`）：与推送用的 `VLOGS_URL` 是**同一个 VictoriaLogs、
   但路径不同**（推送 `/insert/jsonline`，检索 `/select/logsql/query`）；只填 `scheme://host:port` 时代码自动补路径；
   **留空即禁用该功能**，前台会提示未配置。时间窗以 `sys_job_log.create_time`（≈执行结束点）为基准：
   `start = create_time - 耗时(从 job_message 解析) - query-window-before-millis`、
   `end = create_time + query-window-after-millis`。**后置余量默认 30 分钟且明显大于前置**：
   批处理任务返回后下游常仍在跑（异步线程池 / `@Scheduled` 补偿 / 超时重试），这些日志时间戳晚于
   `create_time`，尾部窗口太小就查不到。**放宽窗口时 `query-limit` MUST 同步跟上。**
6. **`JobTraceLogServiceImpl` 的三条约束**（`web-quartz/.../service/impl/JobTraceLogServiceImpl.java`，
   grep 「按 traceId 从 VictoriaLogs 反查」）：①地址靠 `VLOGS_QUERY_URL` 注入，**为空即功能禁用**并给出明确提示，
   **NEVER 写死集群地址**（测试与生产的 Service 名与端口不同）；②只做只读检索、超时按秒级封顶
   （全服务开了虚拟线程，请求线程上 NEVER 长时间阻塞）；③时间窗只能由 `create_time` + `job_message` 反推，
   **该表没有 `start_time` / `stop_time` 列**。另：**`limit` 不保证顺序，截断是随机丢弃**，
   **NEVER 想当然认为截断后留下的是最早的 N 条**。响应是 JSON Lines，每行一条日志事件。
7. **`ServiceStatusService` 是「web-server 不直连业务服务」这条约束的已知例外**（探活性质、非业务接口，
   设计文档 §四.7 已用户确认）。清单与 `service.*.url` 同源，**无注册中心可自动发现，
   新增服务 MUST 同步这里的清单与 `application.properties`**；探活只读，单个服务失败只降级成该行
   DOWN / UNKNOWN，**NEVER 让整体接口 500**。
8. **`:43` 那行 `# ???????????????????` 是编码损坏的注释**（原文是问号占位，不是有效说明），
   属**待裁决**项，见下面「矛盾与待裁决」。

### 附H、`sys_*` 表与 328 条 `COMMENT ON`（DDL，NEVER 当注释删）

1. **`COMMENT ON` 是 DDL 语句、不是注释**：共 **328 条**，全部在两个建库脚本里 ——
   `web-server/sql/ry_20250522_oracle.sql` **238 条**、`web-server/sql/quartz_oracle.sql` **90 条**。
   本轮迁移**一条未动**，删注释的脚本对 `COMMENT ON` 不做任何处理。
2. **`COMMENT ON TABLE` 覆盖 31 张表**：`sys_user` / `sys_dept` / `sys_post` / `sys_role` / `sys_menu` /
   `sys_dict_type` / `sys_dict_data` / `sys_config` / `sys_notice` / `sys_job` / `sys_job_log` /
   `sys_logininfor` / `sys_oper_log` / 四张关联表（`sys_user_post` / `sys_user_role` / `sys_role_dept` /
   `sys_role_menu`）/ `gen_table` / `gen_table_column` + 11 张 `QRTZ_*`。
3. **`COMMENT ON COLUMN` 的列语义分布**（`ry_20250522_oracle.sql`，逐表条数）：
   `gen_table_column` 22 / `gen_table` 21 / `sys_user` 20 / `sys_menu` 20 / `sys_oper_log` 17 /
   `sys_role` 14 / `sys_dict_data` 14 / `sys_dept` 14 / **`sys_job` 13** / `sys_post` 10 / `sys_notice` 10 /
   `sys_config` 10 / `sys_logininfor` 9 / `sys_dict_type` 9 / **`sys_job_log` 8** / 四张关联表各 2。
   **查列语义 MUST 直接 grep 这两个脚本的 `COMMENT ON COLUMN <表>.`，本节不复制那 328 行**
   （复制即产生第二份会漂移的副本，违反本文档的迁移原则）。
4. **`QRTZ_*` 的 90 条注释描述的是当前无人写的表**（内存 JobStore，见附A.1）——
   **NEVER 据这些表注释推断「Quartz 状态在库里」**。
5. **`sys_job_log` 的 8 条列注释里没有 `start_time` / `stop_time`**，与附B 末条互为印证。

### 附I、菜单 / 权限 SQL 脚本（`web-server/sql/*.sql`）

1. **`sys-menu-route-name-migration.sql`：`ROUTE_NAME` 与前端组件 `name` 必须逐条一致**。
   删掉的三条重复菜单的**判据**（原 `:33~42`，grep 「删除判据（buildMenus 只在 menu_type='M' 时递归 children」）：
   `buildMenus` 只在 `menu_type='M'` 时递归 `children`（`SysMenuServiceImpl.java:180`），因此
   - `2005`「日票订单查询」C / 无 perms / `SYS_ROLE_MENU` 0 行 ⇒ 未授权任何角色、前台不可见，
     且与 `2029` component 完全相同（`trans/daily-ticket/refund/index`）；
   - `2030`「日票退款记录」C 且 `parent=2029`（**父级是 C 不是目录**）⇒ `buildMenus` 直接丢弃整棵子树，
     该路由从不下发给前端、页面永远打不开；
   - `2045` F / `parent=2030` ⇒ 随 2030 成孤儿，其 perms `trans:daily-ticket:refund:list`
     在前端**无 `v-hasPermi` 消费点**。
   **保留** `2006`（挂在目录 `2077` 下、已授权，真正在用的「日票退款记录」）与 `2029`（页面本身）。
   **`2029` 的按钮 `2044` 不在保留范围**（落在同脚本「重复按钮权限」第一组里被删；
   注释里此前写「保留 2044」是错的，已在原文更正）。
   回查结论（2026-09-14 实测，原 `:90~97`）：`ROUTE_NAME` 与组件 name 逐条一致、全表无重复 `ROUTE_NAME`；
   `2077` 子树剩 `2006` / `2007(+2025)` / `2029`；重复 perms 只剩 RuoYi 自带的 `monitor:cache:list(113,114)` 未动；
   前端 `v-hasPermi` 用到的 16 个业务 perms 库内均有记录；孤儿菜单 0 条。
2. **`zongguantai-menu.sql`：`menu_id` 已与线上 `QDITP.SYS_MENU` 对齐（2026-09-15 实测），禁止再改动 id**。
   线上占用 `2080`=逻辑卡号重试导入(2078/F)、`2081`=交易明细查询(2071/C)、`2082`=交易明细按钮(2075/F)，
   故本文件菜单**从 2083 起**；`2071~2076` 是用户运营既有菜单（见 `user-query-menu.sql`）。
   **页面由后端动态路由加载，`component` 必须与 `web/src/views` 下路径一致**；
   **`route_name` 必须与前端 `<script setup name="...">` 完全一致**
   （`RegStats` / `OfflineCodeStats` / `ItpUserBatchSearch` / `KeyVersion` / `ServiceStatus` / `OvertimeRefund`），
   否则 **keep-alive `include` 匹配不到 ⇒ 页面不缓存 ⇒ 切 tab 后查询条件丢失**。

### 附J、前端 `web/src`：只读页、路由名与 nginx 前缀

1. **综管台新增页的组件名 ↔ 路径**（`<script setup name>` 即 `sys_menu.ROUTE_NAME`，见附I.2）：
   `RegStats`=`trans/stats/register`、`OfflineCodeStats`=`trans/stats/offline-code`、
   `ItpUserBatchSearch`=`trans/user/batch-search`、`KeyVersion`=`trans/key/version`、
   `ServiceStatus`=`monitor/service-status`、`OvertimeRefund`=`trans/overtime-refund`、
   `FacePayOrder`=`trans/face-pay/order`、`CardPoolManagement`=`trans/card-pool`、
   `BlacklistManagement`=`trans/blacklist`、`DailyTicketRefund` / `DailyTicketRefundRecord`=`trans/daily-ticket/*`、
   `ItpUserSearch` / `AlipayUserSearch` / `UserPayChannel` / `UserRideStatus` / `UserDebitDetail` /
   `UserTransactionDetail`=`trans/user/*`。
2. **前端请求统一带 `/{服务名}` 前缀、转发时删前缀**（`web/vite.config.js:86`，grep 「页面请求统一带」；
   生产侧等价物是 `web/nginx.conf` 的一组 `location /<服务名>` + `proxy_pass .../`）。
   **`web/nginx.conf` 不在本轮删除范围内**（它在 `web/` 根、不在 `web/src/**`），其注释原样保留。
3. **`facePayOrder.js` 是「三个开关别搞混」的唯一提示点**（`web/src/api/trans/facePayOrder.js:3~6`）：
   本页数据源是 face-pay-server 的 `F2F_ORDER`（综管台只读接口，走 web 侧 nginx 前缀）；
   它与 `fep-app-vr` 的**设备入向路由**、`fep-app` 的 **APP 域 env** 是**三个互不相干的开关** ——
   改这里只换综管台读哪个服务，**NEVER 读成「设备入向流量切换」**（那两个开关见 `docs/ops/流量切换.md`，ADR-D117）。
   URL 后半段 `/page/face-pay/orders` 两服务一字不差、只换前缀，**NEVER 退回 `/collect-pay-server`**。
   **这一条源码侧作为一行式护栏保留了首行**，完整版在此。
4. **nginx 侧成对关系（改一处 MUST 改另一处）**：
   `location /face-pay-server` → `172.20.211.23:30025` ↔ `web/src/api/trans/facePayOrder.js`；
   `location /key-server` → `:30015` ↔ `web/src/api/trans/keyVersion.js` 的 `/key-server/page/key/versions`。
   `location /collect-pay-server` → `:30024` **已退居后台、只服务旧单（`TBL_TVM_ORDER_PAY`），
   NEVER 再指向 30025**。`card-pool-server` 那条**唯一用的是 Service DNS**
   （`card-pool-server-86mc1-svc.itp.svc:30033`），其余都是 `172.20.211.23:<NodePort>`。
   另有 `/para1-server` 与 `/para-server` **两条前缀指向同一个 `:30026`**（历史别名，见「矛盾与待裁决」）。
5. **`dailyTicketRefund.js` 的四条一行式语义**（`:17` / `:27` / `:37` / `:47`）：前三处「服务端固定订单类型为日票，
   页面只提交业务主键」，第四处「服务端先查询退款结果，再以同一退款单号重试，**防止形成重复退款**」——
   属**字段/契约级一行语义，按规则保留在源码**。
6. **`web/src/views/monitor/job/log.vue`**：`:112` 状态列绑 `sys_common_status` 字典（缺 `2=进行中` 即渲染空白，
   见附B.2）；`:291~298` 有 UTC 时间串转本地 `YYYY-MM-DD HH:mm:ss.SSS` 的转换（VictoriaLogs 返回 UTC）。
7. **`web/src/router/index.js:5~25` 是 RuoYi 原生的路由配置项说明**（`hidden` / `alwaysShow` / `redirect` /
   `noredirect` / `name` / `meta` 各字段含义）——框架级知识、非本项目判据，**原样保留**。

### 矛盾与待裁决

**照实记录，本节不裁决**。每条给「两边各是什么」+「按哪边动手的后果」。

1. **`ScheduleConfig` 的建议 vs 仍在维护的 `QRTZ_*` 建表脚本**：被注释掉的类头写着
   「单机部署建议删除此类和 qrtz 数据库表，默认走内存会最高效」，而 `web-server/sql/quartz.sql` /
   `quartz_oracle.sql` **仍在仓库里、且带 90 条 `COMMENT ON`**，库里那 11 张表也还在（恒 0 行）。
   删表会让「以后想切数据库 JobStore」需要重新建；不删则每次巡检都要再解释一次「为什么这些表是空的」。
   **待裁决：这 11 张表留还是删。**
2. **web-admin 的环境变量命名与其余模块相反**：本模块是 `<X>_SERVICE_URL`（如 `RECON_SERVICE_URL`），
   业务模块是 `SERVICE_<X>_URL`。两处注释都写着「NEVER 混用」，但**没有任何一处说明以后统一到哪一边**。
   现状下改任一边都会让已有 Deployment env 失效。**待裁决：是否统一，以及统一到哪个风格。**
3. **nginx 里 `/para-server` 与 `/para1-server` 两条前缀指向同一个 `172.20.211.23:30026`**
   （`web/nginx.conf`）。看不出哪条是现役、哪条是历史别名，前端代码里也没有注释说明。
   **待裁决：`/para1-server` 能否删。**
4. **代理目标的写法两套并存**：`card-pool-server` 那条用 Service DNS
   （`card-pool-server-86mc1-svc.itp.svc:30033`），其余十几条用 `172.20.211.23:<NodePort>`。
   前者依赖 CoreDNS（在 Pod 内可解析）、后者依赖宿主节点 IP 固定。**待裁决：统一到哪种。**
5. **`application.properties:43` 是一行编码损坏的注释**（`# ???????????????????`），
   与相邻的「主库数据源 / 从库数据源」结构推断它原本是某段中文说明。**待裁决：补写还是删除**
   （本轮按「多行叙述型」判据未动它，避免删掉一条无法复原的线索）。
6. **代码注释里嵌版本号 / 枚举，全部会过期 —— 已实证两次**：
   ①`ParaQuartzTask` 里曾写「目前只有 pay-sign-server 显式打开 tracing」，实测已有 7 个，
   原文自己已更正并加了「NEVER 在此再嵌一份会过期的枚举」；
   ②`application.properties:134` 写「recon-server 自 1.0.8 起不再有 `@EnableScheduling`」，
   而 `AGENTS.md` 侧当前口径已是 1.0.12。**判据：版本号与名单只放 `AGENTS.md` / 本文档一处，
   源码注释 NEVER 复制。** 本轮迁移即按此执行。
7. **阶段一记录的 `log4j2-web-admin.xml` 行号（`:19` / `:22` / `:30` / `:98` / `:155`）与本次实测的注释块
   起始行（`5` / `18` / `21` / `27` / `81` / `93` / `144` / `148` / `157`）对不齐** ——
   两次都对，指的是同一段里的不同行。**这正是「行号不可引用、MUST 用 grep 短语」的活样例**，
   而本轮删注释会让全部行号再次前移。**待裁决：阶段一那份行号清单是否就地改成 grep 短语。**

### 墓碑清单（记载「已经不存在 / 已被推翻的东西」的注释）

**这类注释的价值在于阻止有人重新推导出同一个错误结论**，因此**迁到文档而不是直接丢弃**；
源码侧一律删。建议逐条转成断言测试（可断言性已在阶段一附十评估过，此处只登记本轮新增/未登记的）。

1. **`ScheduleConfig.java:1~57`** —— 整文件被注释掉的数据库 JobStore + 集群模式装配（含
   `isClustered=true` / `tablePrefix=QRTZ_` / `threadCount=20` / `misfireThreshold=12000`）。
   **可断言**：运行时 `Scheduler.getMetaData().getJobStoreClass()` 是 `RAMJobStore`。
2. **`AbstractQuartzJob` 旧实现（只在 `after()` INSERT）的描述** —— 现已是「开始即入库 + 收口回写」。
   **可断言**：起一次任务，在执行中查 `sys_job_log` 应能命中 `STATUS='2'` 的那行。
3. **`sys-menu-route-name-migration.sql` 里的自我更正**「本行早前写『保留 2044』是错的」——
   `2044` 实际在「重复按钮权限」第一组里被删。**可断言**：库内 `MENU_ID=2044` 不存在。
4. **`RefundCompensateQuartzTask` 里的自我更正**「此前写『当前库里实测 6 条』是错的，当天收口后变 7 条」——
   **不可断言**（该值只增不减、是时点快照），只能保留「NEVER 写死数字」这条判据。
5. **`ParaQuartzTask` 里的自我更正**「此前写『只有 pay-sign-server 打开 tracing』是旧快照」——
   **可断言（弱）**：断言该模块 `management.tracing.enabled` 的实际取值，而不是断言名单。
6. **`web/nginx.conf` 的 `/collect-pay-server` 段**「已退居后台，只服务旧单（`TBL_TVM_ORDER_PAY`）、
   **NEVER 再指向 30025**」—— 记录的是一次已完成的迁移（当面付订单页 2026-09-15 改走 face-pay）。
   **可断言**：该 location 的 `proxy_pass` 端口是 30024。**注意该文件不在本轮删除范围，注释仍在原处。**
7. **`log4j2-web-admin.xml` 的整段「为什么不用公共配置」+「status 别改回 off」** ——
   记录的是 2026-09-08 那次「配置重载成功但 VictoriaLogs 零上报」的排查史。
   **可断言**：启动后 `LoggerContext` 里存在名为 VictoriaLogs 的 appender 且业务 logger 挂了它的 ref。
8. **`quartz.sql` / `quartz_oracle.sql` 整两个脚本** —— 为当前无人写的 11 张表建表 + 注释。
   **可断言**：`SELECT COUNT(*) FROM QRTZ_TRIGGERS` 恒 0（内存 JobStore 下）。
9. **`application.properties:43` 的损坏注释** —— 一条已经读不出内容的墓碑，见「矛盾与待裁决」5。

### 覆盖率自评

**扫描口径**：`web-server/src/main`（313 java / 25 xml / 6 properties / 1 yml / 13 vm）+ `web-server/sql/*.sql` 17 个
+ `web-quartz/src/main/resources/sql/*.sql` 2 个 + `web/src`（111 vue / 80 js / 8 scss）。
按词法剥离统计，这批文件共 **11833 行注释**（含 `web/src` 1266 行；`web-server` 侧与用户口径的 10822 行差额
来自 `web-server/sql/*.sql` 与 `log4j2-*.xml` 这些不在 `src/main/java` 下的文件）。

**本轮产出**：本节新增 **72 条编号条目 + 16 行 `sys_job` 清单 + 14 条子项**，
删除 **734 行注释**（分布在 **31 个文件**：脚本处理 27 个 + 手工处理 4 个），文件总行数减少 752 行。

**已完整覆盖（抽取 + 删除闭环）**：
- web-admin `quartz/task` 全部 11 个任务类；`web-quartz` 的 `AbstractQuartzJob` / `QuartzTraceUtils` /
  `JobInvokeUtil`（`Integer` 那条判据）/ `SysJobServiceImpl` / `SysJobLogMapper`(+XML) / `SysJobController` /
  `JobTraceLogServiceImpl` / `IJobTraceLogService` / `ScheduleConfig`；
- `web-common` 的 `Constants.RUNNING`；`web-admin` 的 `ServiceStatusService`；
- `web-admin/src/main/resources/application.properties` 与 `log4j2-web-admin.xml`（**这两个文件里
  项目自写的说明已全量迁出**）；
- 三个项目自写的 SQL 脚本（`sys-menu-route-name-migration.sql` / `zongguantai-menu.sql` /
  `web-quartz-refund-compensate-job-migration.sql`）；
- 前端 `web/src/api/trans/facePayOrder.js`（只留首行一行式语义）。

**有意未删（并说明理由）**：
1. **RuoYi 框架自带的标准 Javadoc**（`web-common` / `web-system` / `web-framework` / `web-generator`
   合计约 9000 行）——它们本身就是「标准 Javadoc」，不属于叙述型 / MUST-NEVER / 事故史 / 墓碑。
   本轮把处理面收窄成「文件内至少有一处 MARKER 注释」+ 三个显式登记文件，**正是为了不去动框架文档**；
   首版脚本曾把 `UUID.java` / `Convert.java` / `ReflectUtils.java` / `router/index.js` 也纳进来（共多删 522 行），
   已回退该口径。
2. **`web-generator` 的 13 个 `.vm` 模板**：其中的「注释」是模板输出，删掉会改变生成代码，整体跳过。
3. **`ry_20250522.sql` / `ry_20250522_oracle.sql` / `quartz*.sql` 等建库脚本**：注释以
   `-- 分隔线` + `-- N、表名` 的段落标题为主，**328 条 `COMMENT ON` 是 DDL**，一律未动（见附H）。
4. **`web/nginx.conf`**：不在 `web/src/**` 范围内，其 `/face-pay-server` / `/key-server` /
   `/collect-pay-server` 三段注释仍在原处（内容已在附J 记录）。
5. **`application.properties:43` 的损坏注释**：留作线索，见「矛盾与待裁决」5。
6. **mapper XML 的一行式护栏**：`SysJobLogMapper.xml` 的 IDENTITY / 收口两条与 `SysJobMapper.xml` 的
   `remark` 一条，**已从多行压成一行式**保留在源码（完整判据在附B / 附C）；
   **web-server 的 19 个 mapper XML 里没有任何「SQL 正文禁写注释」型告警**（该告警是业务模块的做法），
   因此这一类「MUST 保留项」在本模块为空集。

**不变量自证**：31 个改动文件逐个用 `svn cat -r BASE` 取基线，Java / JS / Vue 走词法剥离
（保护字符串、字符、文本块、模板串与正则字面量）、XML 剥 `<!-- -->`、properties 剥 `#`、SQL 剥 `--` 与 `/* */`，
剥离后**全部逐字一致（31/31）**，无一例需要回退。19 个 mapper XML + `log4j2-web-admin.xml` 逐个
`xmllint --noout` 通过；全仓 XML 注释体内**连续减号 0 处**。

**残余风险**：
- 本节的行号会随本轮删除**整体前移**，**定位 MUST 用 grep 短语**（阶段一那份行号清单同理，见「矛盾与待裁决」7）。
- 附C 那份 `sys_job` 清单是 2026-09-15 的实测快照 + 本次新增两条，**MUST 现查 `SELECT JOB_ID, JOB_NAME,
  INVOKE_TARGET, CRON_EXPRESSION FROM SYS_JOB ORDER BY JOB_ID` 核对**，NEVER 直接引用本表下结论。










