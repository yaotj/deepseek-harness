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
