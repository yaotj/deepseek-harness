# 逻辑卡号池（card-pool-server）

> 本文按代码实际落地情况编写，不是需求文档。改动本域代码前先读本文，再读点名的核心类源码。

## 涉及模块与端口

| 模块 | 端口 | 说明 |
|---|---|---|
| card-pool-server | 9111（`CARD_POOL_PORT`） | 逻辑卡号池；批次申请、ACC 文件导入、卡号预占 |
| acc-secure-server | 9099 | IF7B-01 `requestQrLogicNumList` 的上游代理，**当前未接线** |

镜像 `itp/card-pool-server`，`remote` profile `activeByDefault=true` 且 jkube 绑 `package`，
因此 `mvn clean package` 会直接 build + push 到 Harbor；只想拿 jar 加 `-Djkube.skip=true`。

## 真实接口清单

以 Controller 的 `@PostMapping` / `@GetMapping` 为准（`CardPoolController`）。

- `POST /internal/card-pools/reservations` — 预占卡号
- `POST /internal/card-pools/reservations/{reservationId}/confirm` — 确认预占
- `POST /internal/card-pools/reservations/{reservationId}/release` — 释放预占
- `GET  /internal|/page /card-pools/summary` — 各票种水位概览
- `GET  /internal|/page /card-pools/batches` — 批次列表
- `POST /internal|/page /card-pools/batches` — 创建批次（只落 `CREATED` 即返回）
- `POST /internal|/page /card-pools/batches/{batchNo}/retry` — 重试失败批次
- `POST /internal|/page /card-pools/maintenance` — 执行一轮维护（回收超时预占 + 补货 + 推进批次）

调用方走 `rpc` 模块 `CardPoolClient`（`@EnableRpcCardPool`，`service.cardPool.url`）。

## 核心类

- `CardPoolController` — 只做参数校验与路由
- `CardPoolService` / `CardPoolServiceImpl` — 业务与状态机
- `LogicCardPoolMapper` + `mapper/LogicCardPoolMapper.xml`
- `CardPoolProperties` — `card-pool.*` 配置
- `AccSecureProperties` — `acc.secure.*` 配置（ACC 地址、路径、商户号、签名类型）
- `AccLogicNumClient` — IF7B-01 **直连 ACC**（OkHttp，本模块内），不经 `acc-secure-server`
- `AccSignUtils` + `AccCommonRequest` — ACC 公共报文外壳与签名，自 `acc-secure-server` 迁入

## 数据表与状态取值

`LOGIC_CARD_POOL_BATCH`：`CREATED` → `REQUESTING` → `DOWNLOADING` → `IMPORTING` → `SUCCESS`，
任一环节异常置 `FAILED`。

`LOGIC_CARD_POOL_CARD`：`AVAILABLE` → `RESERVED` → `ASSIGNED`；预占超时回收退回 `AVAILABLE`。

`LOGIC_CARD_POOL_TYPE_LOCK`：票种级批次锁，`card-pool.lock-stale-seconds` 秒未续期可被抢占；
建表脚本按 `POOL_ENABLED_TYPES` 预置 7 行（`0441`、`0444`~`0448`、`044A`）。

启用卡池的票种取自 `model` 的 `CardPoolTicketType.POOL_ENABLED_TYPES`；
`0442` / `0443` 由安全服务直接发卡，其余为外部发行，均不进卡池。

## 编码约束

- **申请与导入必须分两段**：接口只落 `CREATED` 批次即返回，ACC 申请 / FTP 下载 / 十万行入库统一由
  `runMaintenance` 推进。**NEVER** 把这条链路放回请求线程（虚拟线程 pin 载体线程）。
- **本模块不注册 `@Scheduled` / `@EnableScheduling`**：维护动作由外部调度经
  `POST /card-pools/maintenance` 触发，多副本不会各跑一份。定时配置建在 web-admin：
  任务 Bean `CardPoolQuartzTask`（`web-server/web-admin/.../quartz/task/CardPoolQuartzTask.java`），
  调用目标 `cardPoolQuartzTask.runMaintenance()`，`sys_job` **job_id=107、cron `0 0/5 * * * ?`**
  （2026-09-09 创建）。跨服务调用走 `rpc` 的 `CardPoolClient.runMaintenance(headers)`，
  地址键 `service.cardPool.url`（集群实测形态 `http://card-pool-server-86mc1-svc.itp.svc:30033`）。
  ⚠️ **`accepted=false` 不是失败**：上一轮未结束时服务端限流丢弃本轮，任务侧只打日志不抛异常，
  否则十万行导入期间前台调度日志会长期一片红。
- **`reserve` 是唯一带 `@Transactional` 的方法，事务内不得出现任何网络调用**。
- 状态机一律白名单：可推进批次仅 `CREATED` / `DOWNLOADING` / `IMPORTING`；
  可复用的已有预占仅 `RESERVED`；可重试仅 `FAILED` 且已有 `FILE_NAME`。
- 导入分片失败后逐条重试，**只有确认唯一键冲突才按重复计数**，其余异常一律上抛让批次落 `FAILED`。
- `updateBatch` 对五个计数列与 `RETRY_COUNT` 用 `<if>` 跳过 null，`insertBatch` 显式写 0，
  两处共同保证 `NUMBER ... DEFAULT 0 NOT NULL` 列不会被写成 NULL（ORA-01407）。

## 已知坑

- **异步维护线程的 trace 交接**（2026-09-09 发现并修复，`card-pool-server:1.0.14`）。`management.tracing.enabled=true`
  已在本模块打开，**请求线程本来就正常**（传入 `traceparent: 00-aaaabbbbccccdddd1111222233334444-...`，
  日志 `%X{traceId}` 原样输出该值）；但 `runMaintenance` 提交到 `card-pool-maintenance` 单线程池后
  **上下文不会自动过去**，该线程里每条 SQL 各自生成一个新 traceId（修复前 15:10:00 那一轮实测
  `76bdfb1a…` / `c6db9831…` / `c8d7d853…` / `8174b8da…` 全不同），
  后果是**按 web-admin 调度的 traceId（`sys_job_log.job_message` 里那个）在本模块日志里零命中**，
  一轮维护被打散成几十条互不关联的 trace。
  现在 `CardPoolServiceImpl.withTraceContext(...)` 在提交前用 `tracer.nextSpan(parent)` 建子 span 再 `withSpan` 执行，
  traceId 与调用方一致、spanId 独立（**不能把已结束父 span 的 scope 搬过来**：`runMaintenance` 受理即返回，父 span 很快 end）。
  `Tracer` 用 `ObjectProvider` 注入，tracing 关闭的环境取不到时降级为「不建 span」，不影响业务。
  实测 2026-09-09 16:05:00 定时那一轮：`[card-pool-maintenance]` 全部 20 条 SQL 与 `卡池维护完成` 同为
  `11f04d1a555242228ee3c8d5d85683dc`；16:05:32 手工 `curl -H 'traceparent: 00-1111222233334444aaaabbbbccccdddd-...'`
  直调 `/internal/card-pools/maintenance`，异步线程整轮日志的 traceId 就是该 `1111222233334444aaaabbbbccccdddd`。
  **NEVER 退化成「只拷 MDC」**（pay-sign `PaySignExecutorConfig#mdcTaskDecorator` 那套）：1.0.13 照该口径改过一版，
  **实测无效** —— 15:55:00 那一轮 20 条 SQL 仍各自一个 traceId（`e898ee88…` / `90abb880…` / `e19032ce…`），
  末行「卡池维护完成」的 traceId 甚至为空。原因是 JDBC observation 每次开始都按当前 span **覆盖** MDC 的 traceId、
  结束时清掉；异步线程无活跃 span ⇒ 每条 SQL 新开 trace 并盖掉拷进去的值。
  **MDC 拷贝只适用于「异步任务内不再产生 observation」的场景**（pay-sign 的通知补偿属此类）；
  本模块异步任务全是 DB 操作，必须建真 span。1.0.14 已回退为建子 span 的实现。

- **光有 span 还查不到日志：`x-vlogs-capture` 也 MUST 透传进维护线程**（2026-09-09 发现并修复，`card-pool-server:1.0.15`）。
  span 只保证「traceId 一致」，不决定「这条 INFO 要不要上报日志系统」。日志全量采集标记由 micro 的
  `FirstFilter` 把请求头 `X-Vlogs-Capture` 小写后放进**请求线程**的 MDC，`log4j2-linux.xml` 的
  VictoriaLogs appender 靠 `ThreadContextMapFilter` 匹配它；不匹配的事件落到 `ThresholdFilter`，
  **只有 WARN 及以上才上报**。而本模块 `other.web.enableLogRequestInFilter=false`
  （`resource/micro/web/src/main/resources/web.properties:25`），请求线程上一条业务 INFO 都不打，
  维护线程又拿不到标记 ⇒ **整轮调度在 VictoriaLogs 里 0 条**，前台「执行日志」按 traceId 检索空手而归。
  1.0.15 起 `withTraceContext` 在建 span 之外把该键拷进维护线程、并在 `finally` 移除
  （维护线程是**单线程池跨轮复用**，不清理会让下一轮非采集链路被误当成需要全量上报）。
  实测：`traceparent` + `X-Vlogs-Capture: 1` 直调维护接口后，`traceId:"<id>" | stats by (app)`
  返回 `{"app":"card-pool","c":"30"}`（修复前恒为 0 条）。
- **card-pool-server 的 Deployment MUST 注入 `VLOGS_URL`，且值 MUST 带 `http://`**。
  该 env 为空 ⇒ appender 自动禁用、一条不推；写成 `victoria-logs-pbi6a-svc.itp.svc:30032`
  （缺 scheme）⇒ `vlogs-sender` 线程照样在跑但**推送全部静默失败**，现象与没注入一模一样
  （`status="off"` 吞掉报错）。2026-09-09 两个坑都踩过。正确值与排查方法见
  `docs/ops/生产环境清单.md` §三 `VLOGS_URL` 行。

- **`REQUESTING` 状态无法自动续做**：表示已向 ACC 发出申请但未拿到文件名，进程中断后无法判断
  对端是否已生成批次。维护动作会把它置 `FAILED` 并写明原因，需人工核对 ACC 侧后重建批次。
- **`LOGIC_CARD_POOL_BATCH_SEQ` 区间 100000~999999 且无 `CYCLE`**，只有 90 万个批次号；
  `REQUEST_SEQ` 为 `VARCHAR2(6)`，位宽受 ACC 报文约束，不能简单加宽。接近上限时代码打 `ERROR` 日志。
- **`acc.secure.base-url` 为空默认值**，未注入 `ACC_BASE_URL` 时 `AccLogicNumClient` 直接抛
  `acc.secure.base-url未配置`，批次落 `FAILED`。IF7B-01 现在是**直连 ACC**，不再经
  `acc-secure-server`，`service.accSecure.url` 与 `@EnableRpcAccSecure` 已删除。
- **`acc.secure.sign-type` 默认 `00`（不签名）**，与迁出前 `acc-secure-server` 的默认一致。
  若 ACC 要求 MD5，改成 `02` 并注入 `ACC_SIGN_KEY`（Secret），签名算法未改动但**需人工复核**。
- **`other.sql.*` 与 FTP 相关键全部为空默认值**，未注入 env 时启动即失败（有意为之，避免测试地址残留）。
- acc-secure-server 侧仍有一份同名的 `RequestQrLogicNumListReqDTO/RespDTO`（在其自身包内，
  依赖 `AccBizBaseResponse`）。跨模块共享的那份在 `model/.../model/accsecure/`。
  两份字段一致，**改字段时两处都要动**；彻底合并需先把 `AccBizBaseResponse` 上移到 model。
- **ACC 完全不校验 `ticketType`，送任何值都返回 `0000` 并正常发号**（2026-09-09 实测：
  对 `41` `42` `43` `44` `45` `46` `47` `48` `49` `4A` `4B` `4F` `99` `ZZ` 各发一次
  `RequestNum=1`，**全部成功**，包括规范里不存在的 `99` 和连十六进制都不是的 `ZZ`）。
  因此 **NEVER 用「ACC 是否接受」来验证票种映射是否正确**——映射错了接口层不报错，
  只会在实际发卡后表现为「卡号票种错位」。`CardPoolTicketType.toAccTicketType()` 目前是
  `substring(2)` 机械截取后两位，**无甲方依据**；已知 `0447` 七日票 ↔ ACC「计期票」名称对不上，
  `0448` 多日计次票 ↔ ACC「计期计次票」名称大体对应但未经甲方确认，`0441` / `044A` 在 ACC 规范里
  没有对应票种，这张映射表仍待甲方确认（确认后应改成显式映射并补单测）。
- **`RequestSeq` 必须是纯数字**，ACC 侧按整数解析。传 `PROBE41130717` 这类带字母的值会返回
  `retCode=1003`、`returnMsg=解析请求数据失败:The input string '...' was not in a correct format.`
  （2026-09-09 实测）。现有代码用 `batchNo` 作 `RequestSeq`，恰好合规，但**规范原文没写这条约束**。
- **卡号与文件名由 ACC 生成，规则是 `0` + `ticketType` 第 1 位 + `yyMMdd` + 2 位当日流水**，
  卡号 = 该 10 位主干 + 6 位序号（实测：`ticketType=41` → `0426090901.txt` /
  `0426090901000001`；`ZZ` → `0Z26090914.txt` / `0Z26090914000003`）。
  **`ticketType` 第 2 位不进卡号**，所以 `0441`(41) 与 `044A`(4A) 的卡号前缀完全相同——
  **卡号本身不携带票种信息**，票种只存在于文件第二列与 `LOGIC_CARD_POOL_CARD.CARD_TYPE`。
  需要「从卡号反推票种」的场景 **MUST** 先与甲方确认，当前规则做不到。

## 参考原始文档

- 《接口规范-IF7B ACC 安全接口》— `docs/接口规范文档/`（`.docx` 原文，只读）
- ACC 逻辑卡号文件格式（2026-09-09 抓真实文件核实，**与规范原文的「每行一个卡号」不符**）：
  每行两列、单空格分隔、**CRLF 换行**，第一列 16 位逻辑卡号、第二列 2 位 ACC 票种，
  例 `0426090935000008 41`。解析实现见 `CardPoolServiceImpl.parseCardNo()`，
  单测样本见 `CardPoolCardNoParseTest`。

## 附：card-pool-server 源码注释知识抽取（2026-09-16，阶段一）

> **行号会漂**：下面每条的 `:行号` 是 2026-09-16 抽取当时的位置，任何一次编辑都会让它失效。
> **引用前 MUST 先 grep 关键字确认**（例如 `grep -n 'NEVER 简化回' card-pool-server/src/main/java/...`），
> NEVER 直接按本节行号跳转或据行号判断注释是否还在。
> 本节只做「注释里的知识」归档，不含评价、不改动任何源文件。
>
> 抽取范围：`card-pool-server/src/main/java/**/*.java`（11 个）、
> `card-pool-server/src/main/resources/mapper/LogicCardPoolMapper.xml`、
> `card-pool-server/src/main/resources/application.properties`，共 13 个文件。
> 已丢弃：复述方法名 / 参数名的普通 Javadoc、`{@inheritDoc}`、空 Javadoc
> （`CardPoolProperties` / `AccSecureProperties` / 两个 entity 的 getter-setter 注释几乎全属此类）。
>
> **路径别名**（下文定位串用别名 + 行号，别名对应的相对路径在此定义，均以仓库根为起点）：
>
> | 别名 | 相对路径 |
> |---|---|
> | `〔impl〕` | `card-pool-server/src/main/java/com/chinasofti/huateng/cardpool/service/impl/CardPoolServiceImpl.java` |
> | `〔svc〕` | `card-pool-server/src/main/java/com/chinasofti/huateng/cardpool/service/CardPoolService.java` |
> | `〔ctl〕` | `card-pool-server/src/main/java/com/chinasofti/huateng/cardpool/controller/CardPoolController.java` |
> | `〔mapper〕` | `card-pool-server/src/main/java/com/chinasofti/huateng/cardpool/mapper/LogicCardPoolMapper.java` |
> | `〔xml〕` | `card-pool-server/src/main/resources/mapper/LogicCardPoolMapper.xml` |
> | `〔prop〕` | `card-pool-server/src/main/resources/application.properties` |
> | `〔client〕` | `card-pool-server/src/main/java/com/chinasofti/huateng/cardpool/client/AccLogicNumClient.java` |
> | `〔sign〕` | `card-pool-server/src/main/java/com/chinasofti/huateng/cardpool/util/AccSignUtils.java` |
> | `〔boot〕` | `card-pool-server/src/main/java/com/chinasofti/huateng/CardPoolServer.java` |
> | `〔cardEntity〕` | `card-pool-server/src/main/java/com/chinasofti/huateng/cardpool/entity/LogicCardPoolCard.java` |
> | `〔batchEntity〕` | `card-pool-server/src/main/java/com/chinasofti/huateng/cardpool/entity/LogicCardPoolBatch.java` |
> | `〔accProp〕` | `card-pool-server/src/main/java/com/chinasofti/huateng/cardpool/config/AccSecureProperties.java` |
> | `〔poolProp〕` | `card-pool-server/src/main/java/com/chinasofti/huateng/cardpool/config/CardPoolProperties.java` |

### 一、契约与判据

#### 1.1 卡池预占与确认

- **明细状态机**：`AVAILABLE` 到 `RESERVED` 到 `ASSIGNED`；预占超时由回收动作退回 `AVAILABLE`。
  （`card-pool-server` `LogicCardPoolCard` 类注释，〔cardEntity〕:8）
- **归属键的构成**：`BUSINESS_TYPE`「与业务流水号组成唯一归属键」（〔cardEntity〕:43）；
  `RESERVATION_ID`「预占标识，全局唯一」（〔cardEntity〕:38）；
  `EXPIRE_TIME`「预占过期时间，确认后置空」（〔cardEntity〕:63）。
- **`reserve` 的幂等键是 `businessType` + `businessId`**：先 `selectByBusiness` 命中即复用，
  mapper 侧注释即「按业务归属查询已占用的卡号，用于预占幂等」。
  （`card-pool-server` `CardPoolServiceImpl.reserve`，〔impl〕:257~306；`LogicCardPoolMapper.selectByBusiness`，〔mapper〕:152~158）
- **可复用状态白名单 `REUSABLE_STATUSES = {"RESERVED", "ASSIGNED"}`**，且「卡号仍受 BUSINESS_TYPE +
  BUSINESS_ID 唯一归属约束，不会跨业务泄漏」。（〔impl〕:66~74）
- **`reserve` 的三分返回契约（对调用方）**，原文含禁令：
  「返回值三分，供调用方区分降级方式，NEVER 再把三者混成一句「无可分配逻辑卡号」：
  `code=200 data!=null` —— 预占成功；`code=200 data=null` —— 该票种卡池已空，属正常业务结果，
  可提示稍后重试或走降级发卡；`code=400` —— 票种不走卡池、票种非法、业务归属缺失或归属冲突，
  属调用方缺陷，重试无用。」（`card-pool-server` `CardPoolController.reserve`，〔ctl〕:44~56）
- **接口层 `data=null` 的两个成因**：候选集为空（日志「逻辑卡号池已空」）与
  `RESERVE_MAX_ATTEMPTS`（5）次重试耗尽（日志「逻辑卡号预占重试耗尽」）。
  （〔impl〕:101、:275~305）
- **`confirm` 幂等语义**：「本次生效或已处于已确认终态返回 true」；影响行数为 0 时回查
  `ASSIGNED` 且 `businessId` 匹配才算成功；SQL 侧「确认预占，仅当状态为 RESERVED 时生效」。
  （`CardPoolService.confirm`，〔svc〕:28~34；`CardPoolServiceImpl.confirm`，〔impl〕:351~362；〔mapper〕:193~199）
- **`release` 幂等语义**：「本次生效或已处于可用终态返回 true」；影响行数为 0 时回查 `AVAILABLE`；
  SQL 侧「释放预占，仅当状态为 RESERVED 时生效」。
  （〔svc〕:37~43；`CardPoolServiceImpl.release`，〔impl〕:364~375；〔mapper〕:202~208）
- **事务边界**：「除 `reserve` 外均不带事务，逐条 SQL 自动提交；ACC 申请与 FTP 下载一律不在事务内发起。」
  （`CardPoolServiceImpl` 类注释，〔impl〕:56~60）
- **复用时的两条硬拒绝**：票种不一致抛「该业务归属已占用其它票种卡号」；
  状态不在白名单抛「该业务归属的卡号状态不允许复用预占」。
  （`CardPoolServiceImpl.reusableReservation`，〔impl〕:1066~1079）
- **候选条数**：`RESERVE_CANDIDATE_SIZE = 20`，「单次预占从库里取回的候选卡号条数，
  用于分散并发请求、避免全部撞在同一行上」。（〔impl〕:103~106）

#### 1.2 IF7B-01 ACC 申请

- **批次状态机**：「CREATED 到 REQUESTING 到 DOWNLOADING 到 IMPORTING 到 SUCCESS，
  任一环节异常置 FAILED；FAILED 且已有文件名的批次可重试回 DOWNLOADING。」
  （`card-pool-server` `LogicCardPoolBatch` 类注释，〔batchEntity〕:6~9）
- **ACC 成功返回码有两种形态**：`ACC_SUCCESS_CODES = {"0000", "200"}`，注释原文
  「ACC 成功返回码，两种形态都在生产上出现过」。（〔impl〕:81~84）
- **入向报文形态以实测为准**：「ACC 只接受 `application/x-www-form-urlencoded`，公共参数平铺为表单字段、
  `bizData` 为 **JSON 字符串**，即 AGENTS.md §4 记载的项目统一形态」；发 `application/json`
  「实测 ACC 一律回 `1003 解析请求数据失败`」。
  （`card-pool-server` `AccLogicNumClient` 类注释，〔client〕:26~30；组装见 `AccLogicNumClient.buildForm`，〔client〕:90~95）
- **响应字段实测**：`fileName` / `retCode` / `returnMsg`——「消息键是 `returnMsg` 而非规格写的 `RetMsg`，
  解析时两者都兼容」，取值顺序 `retMsg` → `returnMsg`。
  （〔client〕:32~33；`AccLogicNumClient.fillCommonFields`，〔client〕:181~186）
- **`bizData` 为 JSON null 时按「未包裹」处理**，避免把字面量当对象再解析一次。
  （`AccLogicNumClient.parseResponse`，〔client〕:147~151）
- **签名算法**：「公共参数与 `bizData` 按 `key=value` 拼接后字典序排序，用 `&` 连接；
  `signType=02` 时追加 `&key=<signKey>` 再取 MD5 十六进制」；`signType=00` 表示不签名、返回空串，
  「当前生产配置即 `00`」；值为空的参数不参与拼接。
  （`card-pool-server` `AccSignUtils` 类注释，〔sign〕:16~28；`AccSignUtils.appendIfPresent`，〔sign〕:91~97）
- **文件名校验**：「校验 ACC 返回的文件名，拒绝路径穿越与非 txt」。
  （`CardPoolServiceImpl.isSafeFileName`，〔impl〕:1098~1108）
- **批次号即 ACC 请求流水号的数值来源**（`BATCH_NO` 注释，〔batchEntity〕:14）；
  `REQUEST_SEQ`「ACC 请求流水号」（〔batchEntity〕:19）；
  序列告警阈值配置项注释「批次号序列接近上限的告警阈值」（〔poolProp〕:23~25）。
- **只保留 IF7B-01 一条路径**：「本模块直连 ACC，不再经 `acc-secure-server` 转发，因此只保留卡号池用到的
  IF7B-01 路径；其余 IF7B-02~08 仍归 `acc-secure-server` 维护。」（〔accProp〕:9~10）
- **签名类型与超时的配置语义**：`sign-type`「00-不签名，02-MD5」、`sign-key`「仅 signType=02 时使用」、
  `base-url`「未配置时申请动作直接失败」。（〔accProp〕:16~17、:46~52）

#### 1.3 FTP 文件导入

- **文件行格式契约**：「ACC 逻辑卡号文件每行两列、以空白分隔、CRLF 换行，形如 `0426090935000008 41`：
  第一列是逻辑卡号，第二列是 ACC 票种。空行跳过、不计入总数。」
  （`card-pool-server` `CardPoolServiceImpl.importText`，〔impl〕:765~769）
- **第二列票种不一致即判非法**：「票种错位意味着 ACC 发错文件，静默入库会让卡号被当成另一种票发出去」；
  「行尾 `\r` 由 trim 兜底」；`expectedAccTicketType` 为空则不校验第二列。
  （`CardPoolServiceImpl.parseCardNo`，〔impl〕:1110~1120）
- **卡号格式**：「仅允许字母数字且不超过列长」，列长常量 `CARD_NO_MAX_LENGTH = 64`。
  （`CardPoolServiceImpl.isValidCardNo`，〔impl〕:1137~1147；常量〔impl〕:100）
- **导入统计口径**：`TOTAL_COUNT`「文件总行数」/ `VALID_COUNT`「成功入库的卡号数」/
  `DUPLICATE_COUNT`「重复卡号数」/ `INVALID_COUNT`「格式非法的行数」。
  （〔batchEntity〕:64~79；`CardPoolServiceImpl.ImportStats`，〔impl〕:1224~1232）
- **「解析出 N 行但零条合法卡号」即判 FAILED**，错误信息带重复数与非法数。
  （`CardPoolServiceImpl.importFromFtp`，〔impl〕:746~751）
- **单条入库的冲突三分判定**：库中无同号记录 ⇒ 抛「逻辑卡号入库失败且库中无同号记录」；
  「同一批次同一票种」⇒ 计 `valid`（注释原文「用于中断后重跑保留原有计数」）；否则计 `duplicates`，
  跨票种额外打 ERROR。（`CardPoolServiceImpl.insertSingleCard`，〔impl〕:857~885；
  `CardPoolServiceImpl.sameBatchAndType`，〔impl〕:1168~1178）
- **临时文件在任何异常路径下都会被清理**（`CardPoolServiceImpl.importFromFtp` 注释，〔impl〕:725~730）；
  下载失败时由 `download()` 的 catch 分支删除临时文件（〔impl〕:1234~1239）。
- **`ERROR_MSG` 的双上限**：`ERROR_MSG_MAX_LENGTH = 1900` 字符 + `ERROR_MSG_MAX_BYTES = 1900` 字节，
  注释原文「列声明是 `VARCHAR2(2000 CHAR)`，但 Oracle 在 `MAX_STRING_SIZE=STANDARD` 下物理上限是
  4000 字节，这里留足余量」；按字节裁剪的回退步长 `TRUNCATE_STEP = 16`，「取小步长以免把多字节字符切得过多」。
  （〔impl〕:86~98；实体侧「失败原因，最长 1900 字符」〔batchEntity〕:89）
- **来源只有两个合法值**：`AUTO` / `MANUAL`，其余抛「不支持的卡池申请来源」。
  （`CardPoolServiceImpl.normalizeSource`，〔impl〕:1208~1222；〔batchEntity〕:39）
- **FTP 配置语义**：`host`「未配置时禁止下载」、`password`「由 K8s Secret 注入」、
  `max-file-size`「允许下载的最大文件字节数」、`data-timeout-seconds`「覆盖 listFiles 与 retrieveFile」。
  （〔poolProp〕:170~212）

#### 1.4 维护与回收

- **可推进批次白名单 `RESUMABLE_STATUSES = {"CREATED", "DOWNLOADING", "IMPORTING"}`**，
  注释即「可由维护动作继续推进的批次状态白名单」。（〔impl〕:76~79）
- **推进规则原文**：「状态白名单：CREATED 走 ACC 申请后下载导入；DOWNLOADING / IMPORTING 直接续做下载导入。
  REQUESTING 表示已向 ACC 发出申请但未拿到文件名，无法判断对端是否已生成批次，
  直接置 FAILED 并留下明确原因交人工核对」。
  （`card-pool-server` `CardPoolServiceImpl.advanceBatch`，〔impl〕:669~679）
- **`runMaintenance` 是受理型接口**：「接口只做受理：提交到单线程维护线程池后立即返回，
  执行结果看服务端日志与 `/card-pools/summary`。上一轮尚未结束时返回 `accepted=false`」，
  返回体两项 `accepted` 与 `message`。
  （`CardPoolController.runMaintenance`，〔ctl〕:176~186；`CardPoolServiceImpl.runMaintenance`，〔impl〕:482~503）
- **维护动作的四个计数键**：`releasedExpired` / `createdBatches` / `executedBatches` / `failedBatches`；
  其中回收「失败只记日志不阻断后续维护动作」「回收数量，失败返回 -1」。
  （〔svc〕:99~106；`CardPoolServiceImpl.executeMaintenance`，〔impl〕:519~523；
  `CardPoolServiceImpl.releaseExpiredQuietly`，〔impl〕:603~607）
- **`retry` 契约**：「仅接受 FAILED 且已有文件名的批次，状态推进用条件更新保证并发安全」，
  且「本实现『受理即返回』…… 因此返回的批次状态是 `DOWNLOADING` 而不是终态，结果需再查 `batches` 获取」。
  （〔svc〕:58~65；`CardPoolServiceImpl.retry`，〔impl〕:396~402）
- **补货触发条件**：「水位低于阈值且无进行中批次时创建一个补货批次」，自动补货的操作人写 `SYSTEM`。
  （`CardPoolServiceImpl.replenish`，〔impl〕:617~622；〔batchEntity〕:99）
- **票种锁契约**：「抢占票种级批次锁；持有超过 staleSeconds 未续期的锁可被抢占」，
  「续期票种级批次锁，长耗时导入期间定期调用以避免被其它副本抢占」，
  「释放票种级批次锁，仅当持有者匹配时生效」；配置侧「需大于单批次最长导入耗时；导入过程中会持续续期」。
  （〔mapper〕:76~103；〔poolProp〕:33~34）
- **续期失败即中止导入**：「续期返回 0 意味着锁已过期并可能被另一执行体抢走，继续写入会与对方并发插入同批卡号。
  此时 MUST 立即失败，由维护接口下一轮重新推进。」
  （`CardPoolServiceImpl.renewLockOrFail`，〔impl〕:815~819）
- **票种级锁贯穿整段执行**：「票种级批次锁贯穿整段执行并在导入过程中续期，多副本并发调用不会重复导入同一批次。」
  （〔svc〕:102~103）
- **发卡方式三分**：`POOL` / `SECURITY_SERVICE` / `EXTERNAL_ISSUED`。
  （`CardPoolServiceImpl.issueModeOf`，〔impl〕:589~595）
- **预占有效期由配置定**：`reservation-minutes`「预占有效分钟数，超时由维护动作回收」。（〔poolProp〕:28~29）
- **容器关闭时给在跑导入留收尾时间**：`EXECUTOR_SHUTDOWN_SECONDS = 30`。（〔impl〕:108~111、:238~240）

#### 1.5 可观测性配置

- **采集标记键名与大小写约束**：`VLOGS_CAPTURE_KEY = "x-vlogs-capture"`，注释原文
  「入向由 micro 的 `FirstFilter` 把请求头 `X-Vlogs-Capture` **小写**后放进 MDC，
  `log4j2-linux.xml` 的 VictoriaLogs appender 靠它把本次链路的 INFO **全量**上报；
  未命中的事件落到 `ThresholdFilter`，只有 WARN 及以上才上报」，以及
  「**MUST 全小写**：`FirstFilter` 只放小写键，appender 的 `ThreadContextMapFilter` 也只认小写，
  写成驼峰等于不上报。口径同 web-admin 的 `QuartzTraceUtils`。」
  （`card-pool-server` `CardPoolServiceImpl.VLOGS_CAPTURE_KEY`，〔impl〕:113~123）
- **tracing 三行成组的每行职责**（本模块是该写法的样板）：
  `management.tracing.enabled=true`「micro/web 默认 `management.tracing.enabled=false`，此处为 card-pool 打开」；
  `management.tracing.sampling.probability=0`「只需要 MDC 里的 traceId/spanId 供 VictoriaLogs 日志检索，
  span 上报一律不要」；`spring.autoconfigure.exclude=...OtlpAutoConfiguration` 带「NEVER 删除下面这行排除」告示。
  （〔prop〕:9~22）
- **打开 tracing 的收益判据**：「打开后 web-admin 卡池维护定时任务发来的 W3C traceparent 才能被续接进 MDC，
  日志 pattern 的 `%X{traceId}` 才有值，否则前台按 `sys_job_log` 的 traceId 检索本模块日志会 0 条。」
  （〔prop〕:10~11）
- **本模块不注册任何调度**：「本模块不注册 `@EnableScheduling`：卡池维护由外部调度经
  `POST /internal/card-pools/maintenance` 触发，避免多副本重复执行」；
  「由 web-admin 的 Quartz 任务或运维按需触发；本模块不注册 `@Scheduled`」。
  （`CardPoolServer` 类注释，〔boot〕:10~11；〔ctl〕:179；〔svc〕:102）
- **IF7B-01 直连即不需要装配注解**：「IF7B-01 由本模块的 `AccLogicNumClient` 直连 ACC，
  不经 `acc-secure-server` 转发，因此不需要任何 `@EnableRpcXxx`。」（〔boot〕:13~14）
- **接口分区语义**：「`/internal/**` 供内部服务调用，`/page/**` 供运营后台调用。
  申请批次只落库即返回，实际的 ACC 申请与文件导入由 `/card-pools/maintenance` 推进。」
  （`CardPoolController` 类注释，〔ctl〕:24~29）

#### 1.6 持久层与 mapper

- **计数列不被写成 NULL 的两处配套**：`insertBatch`「五个计数列与重试次数由 SQL 显式写 0，
  避免后续全字段更新写入 NULL」；`updateBatch`「五个计数列与重试次数为 null 时不参与更新，保留库中原值」。
  （〔mapper〕:24~29、:106~111；SQL 侧〔xml〕:47~54、:105~122）
- **`markBatchFailed` 是降级用的最小化落库**：「只写终态三列的最小化失败落库，供 `updateBatch` 失败后降级调用」；
  XML 注释「最小化失败落库：仅写终态三列，供 updateBatch 失败后降级使用，避免批次卡在中间态」。
  （〔mapper〕:114~124；〔xml〕:124~129）
- **`markBatchRetrying` 的并发安全口径**：「以状态条件推进失败批次进入重试，重试次数在 SQL 内自增，
  保证并发下不丢次数」，命中返 1、状态不满足返 0。（〔mapper〕:127~132；〔xml〕:131~135）
- **`selectAvailableCandidates` 刻意返回多行**：「若固定只取最小 ID，并发预占会全部命中同一行并阻塞在行锁上，
  同票种预占退化为串行。调用方应从候选中随机挑选后走条件 UPDATE 做 CAS。」
  （〔mapper〕:162~171；〔xml〕:157~168）
- **四条写 SQL 都是条件 UPDATE**：`reserve`「仅当状态仍为 AVAILABLE 时生效」、
  `confirm` / `release`「仅当状态为 RESERVED 时生效」、`releaseExpired`「回收所有已过期的预占」。
  （〔mapper〕:175~215；〔xml〕:170~211）
- **两条按键回查的用途**：`selectByCardNo`「用于导入时区分重复与其它约束冲突」；
  `selectByReservation`「按预占标识查询明细」（`confirm` / `release` 影响 0 行时的终态回查）。
  （〔mapper〕:218~231）
- **待推进批次的状态集**：`selectPendingBatches`「CREATED / REQUESTING / DOWNLOADING / IMPORTING，
  按批次号升序」；`countInProgress`「统计某票种进行中的批次数量，用于避免重复申请」。
  （〔mapper〕:60~73；〔xml〕:74~84）

### 二、决策理由

#### 2.1 卡池预占与确认

- **为什么 `REUSABLE_STATUSES` 里含 `ASSIGNED`**（`card-pool-server` `CardPoolServiceImpl.REUSABLE_STATUSES`，〔impl〕:66~74）：
  「含 ASSIGNED 是刻意的：`confirm` 与 `release` 都做了『影响行数为 0 时回查终态』的幂等兜底，
  若 `reserve` 只认 RESERVED，则同一 businessId 在确认成功后重复请求会拿到 null，
  上游只能翻译成『无可分配逻辑卡号』，把重复请求误报成卡池耗尽。复用 ASSIGNED 记录返回同一卡号，
  三个动作的幂等语义才一致；卡号仍受 BUSINESS_TYPE + BUSINESS_ID 唯一归属约束，不会跨业务泄漏。」
- **为什么这里要沿 cause 链判定完整性冲突**（`CardPoolServiceImpl.isIntegrityViolation`，〔impl〕:308~319，ADR-D53）：
  「**NEVER 简化回 `catch (DataIntegrityViolationException)`**（2026-09-14 修，ADR-D53）：
  本模块打开了 tracing，`MapperAspectToTrace` 会切到所有 `@Mapper` 方法上，
  它此前把异常包成 `new RuntimeException(e)`，于是按类型 catch 的幂等兜底**一条都进不去**，
  `ORA-00001` 直接冒到全局处理器返 500。切面已同批修成原样抛出，
  但按链路逐层判定成本极低、且能挡住将来任何新增的包装切面，因此这层防御**保留**。」
- **为什么随机挑候选而不是固定取第一条**（`CardPoolServiceImpl.pickCandidate`，〔impl〕:335~343）：
  「随机而非固定取第一条：并发请求若都挑同一行，条件 UPDATE 不会立刻返回 0 行，
  而是阻塞在前一个事务的行锁上，同票种预占就退化成串行。」
  同一理由在 SQL 侧再写一遍（〔mapper〕:165~166、〔xml〕:157~159）。
- **为什么接口层要把返回值分三档**（`CardPoolController.reserve`，〔ctl〕:47）：
  「返回值三分，供调用方区分降级方式」——`data=null` 属正常业务结果可降级重试，
  `code=400` 属调用方缺陷、重试无用。

#### 2.2 IF7B-01 ACC 申请（含从 acc-secure-server 迁来直连的理由）

- **为什么迁进本模块直连**（`AccLogicNumClient` 类注释，〔client〕:23~24）：
  「由 `acc-secure-server` 迁入本模块直连 ACC，去掉一跳内网转发：报文外壳、签名、
  HTTP 通信都收敛在本类，领域服务只传业务参数、只看 `retCode` 与 `fileName`。」
- **为什么报文形态与迁出侧不同**（〔client〕:26~30）：迁出侧 `AccSecureServiceImpl` 发
  `application/json`（整体 JSON、bizData 为对象），「实测 ACC 一律回 `1003 解析请求数据失败`」；
  「迁出侧因始终未接线，该缺陷从未暴露」。
- **为什么传输层失败改成抛异常**（〔client〕:35~36）：「与迁出前的另一处行为差异：传输层失败**抛异常**
  而不是返回带内部错误码的壳对象，这样批次表 `ERROR_MSG` 能落到真实原因而非统一的 8007/9001。」
- **为什么换成 fastjson2 原生 API 不改变签名结果**（`AccSignUtils` 类注释，〔sign〕:25~28）：
  「迁出侧用 `com.alibaba.fastjson.JSON` + `SerializerFeature.MapSortField`，本类用 `fastjson2` 原生 API。
  二者引擎相同：`acc-secure-server/pom.xml:69` 依赖的 `com.alibaba:fastjson:2.0.57` 是 fastjson2 的兼容壳。
  2026-09-09 已实测同一 bizData 两条 API 序列化结果逐字节一致，待签串与 MD5 均相同，
  断言见 `AccSignUtilsTest`。」（同段另记「算法未改动」）
- **为什么只保留一条 ACC 路径**（〔accProp〕:9~10）：本模块直连、不再转发，
  「其余 IF7B-02~08 仍归 `acc-secure-server` 维护」。
- **为什么 `REQUESTING` 不自动续做**（`CardPoolServiceImpl.advanceBatch`，〔impl〕:673~674）：
  「无法判断对端是否已生成批次，直接置 FAILED 并留下明确原因交人工核对，
  避免重复消耗 ACC 号段，也避免该票种被永久占位。」

#### 2.3 FTP 文件导入

- **为什么分片失败后逐条重试且刻意不吞异常**（`card-pool-server` `CardPoolServiceImpl.flushCards`，〔impl〕:830~834）：
  「分片入库；批量失败后逐条重试，只有确定是唯一键冲突才按重复处理，其余异常一律上抛。
  这里刻意不吞异常：字段超长、外键违反、连接中断等一旦被当成『重复卡号』，
  批次仍会被标成 SUCCESS，卡号静默丢失且日志无痕。」
- **为什么同批次同票种的冲突算 valid**（`CardPoolServiceImpl.sameBatchAndType`，〔impl〕:1168~1173）：
  「判断已有记录是否来自同一批次同一票种，用于中断后重跑保留原有计数。」
- **为什么按 UTF-8 字节而不是字符裁剪**（`CardPoolServiceImpl.truncate`，〔impl〕:1184~1190）：
  「必须按 **UTF-8 字节** 而不是字符裁剪：列声明是 `VARCHAR2(2000 CHAR)`，
  但 Oracle 在 `MAX_STRING_SIZE=STANDARD` 下的物理上限仍是 4000 字节，
  中文按 3 字节计，只按字符数裁剪时长中文原因串会触发 ORA-12899，
  连带让『记录失败』这件事本身失败。」
- **为什么给下载流加字节上限**（`CardPoolServiceImpl.SizeLimitedOutputStream`，〔impl〕:1234~1239）：
  「FTP 的 LIST 报文只是对端自述的大小，实际传输可以远超；没有上限时一个异常大的文件
  会把容器磁盘写满。这里在超限的第一时间抛 `IOException`，由 `download()` 的 catch 分支删除临时文件。」
- **为什么第二列票种不一致要判非法**（`CardPoolServiceImpl.parseCardNo`，〔impl〕:1114~1115）：
  「票种错位意味着 ACC 发错文件，静默入库会让卡号被当成另一种票发出去。」

#### 2.4 维护与回收

- **为什么维护走平台线程单线程池、且「受理即返回」**（`CardPoolServiceImpl.maintenanceExecutor`，〔impl〕:138~147）：
  「本项目 `spring.threads.virtual.enabled=true` 是全局默认，而 ojdbc8 大量方法为 `synchronized`：
  一轮十万行导入若跑在请求线程（虚拟线程）上会长时间 pin 住载体线程，严重时全 JVM 虚拟线程停止调度。
  因此维护与重试一律『受理即返回』，重活挪到这里执行。单线程即可，
  票种间的并发本来就由 `LOGIC_CARD_POOL_TYPE_LOCK` 串行化。」
- **为什么申请与导入拆成两段**（`CardPoolService` 类注释，〔svc〕:15~16）：
  「`requestBatch` 只落 CREATED 批次即返回，ACC 申请、FTP 下载与入库由 `runMaintenance` 推进，
  避免在请求线程上做长耗时阻塞 IO。」
- **为什么续期失败必须立刻中止**（`CardPoolServiceImpl.renewLockOrFail`，〔impl〕:818~819）：
  「续期返回 0 意味着锁已过期并可能被另一执行体抢走，继续写入会与对方并发插入同批卡号。
  此时 MUST 立即失败，由维护接口下一轮重新推进。」
- **为什么「记录失败」本身要有降级路径**（`CardPoolServiceImpl.fail(LogicCardPoolBatch, String)`，〔impl〕:1019~1025）：
  「为什么必须降级而不是只记日志：`REQUESTING` 同时出现在 `selectPendingBatches` 与
  `countInProgress` 的状态集里，一旦失败落库本身失败，批次就永远停在 `REQUESTING`，
  每轮维护都重走『REQUESTING → fail → 再失败』，`replenish` 被 `countInProgress > 0`
  永久挡死，该票种从此不再补货且无法自愈。」
  mapper 侧同源理由：「`updateBatch` 会写 10 余列，任一列越长或类型不匹配都会整条失败，
  批次就会卡在 REQUESTING/IMPORTING 中间态并把该票种的补货永久挡死。
  本方法只碰 STATUS / ERROR_MSG / FINISH_TIME，把『一定要离开中间态』这件事的失败面降到最小。」
  （〔mapper〕:117~119）
- **为什么维护线程内的失败只记日志不上抛**（`CardPoolServiceImpl.releaseExpiredQuietly`，〔impl〕:604；
  `CardPoolServiceImpl.releaseLock`，〔impl〕:995）：「回收超时预占，失败只记日志不阻断后续维护动作」；
  「释放票种锁，失败只记日志，不覆盖业务异常」。
- **为什么用 `AtomicBoolean` 而不是排队**（`CardPoolServiceImpl.maintenanceRunning`，〔impl〕:149~152）：
  「维护任务在跑标记，避免重复提交把任务堆在单线程队列里。」

#### 2.5 可观测性配置

- **为什么维护任务必须包一层子 span**（`card-pool-server` `CardPoolServiceImpl.withTraceContext`，〔impl〕:172~194）：
  「**为什么必须包**：`maintenanceExecutor` 是自建线程池，既不继承 MDC 也不继承 Micrometer
  的 observation 上下文。不包的话该线程里没有活跃 span，每条 SQL 的 observation 各自开一个新 trace，
  一轮维护被打散成几十个互不相关的 traceId，**用 web-admin 调度的 traceId 在本模块日志里一条也搜不到**
  （2026-09-09 实测：`0b9352490160440185b7a9e1abcd37eb` 零命中，异步线程里是
  `76bdfb1a…` / `c6db9831…` / `c8d7d853…` 等每条 SQL 一个）。」
- **为什么用 `nextSpan(parent)` 而不是搬父 span 的 scope**（〔impl〕:181~183）：
  「父 span 属于已经返回的 HTTP 请求，`runMaintenance` 受理即返回后它很快就 end 了，
  **在已结束的 span 上重开 scope 是错的**。建子 span 则 traceId 与父一致、spanId 独立，生命周期归自己管。」
- **为什么 NEVER 退化成只拷 MDC**（〔impl〕:185~191）：
  「**NEVER 退化成『只拷 MDC』**（即 pay-sign `PaySignExecutorConfig#mdcTaskDecorator` 的写法）。
  那套在本模块**实测无效**：1.0.13 照 pay-sign 口径改成 `MDC.setContextMap(parentContext)` 后，
  15:55:00 那一轮的 20 条 SQL 依旧各自一个 traceId（`e898ee88…` / `90abb880…` / `e19032ce…` …），
  末行『卡池维护完成』的 traceId 甚至为空。原因是 JDBC observation 每次开始都会按当前 span
  **覆盖** MDC 的 traceId、结束时清掉；异步线程无活跃 span ⇒ 每条 SQL 新开 trace 并盖掉拷进来的值。
  MDC 拷贝只对『异步任务内不再产生 observation』的场景有效（pay-sign 的通知补偿即属此类）。
  本模块异步任务全是 DB 操作，**必须建真 span**。1.0.14 已回退为本实现。」
- **为什么除 span 之外还要透传采集标记**（〔impl〕:196~205）：
  「**除了 span，还 MUST 把 `VLOGS_CAPTURE_KEY` 透传进维护线程**：span 只解决『traceId 一致』，
  解决不了『这条 INFO 要不要上报』。采集标记由 `FirstFilter` 放在**请求线程**的 MDC 上，
  维护线程拿不到 ⇒ 该线程的 INFO 落到 appender 的 `ThresholdFilter`(WARN) 被丢弃。
  而本模块 `other.web.enableLogRequestInFilter=false`（`web.properties:25`），请求线程上一条业务
  INFO 都不打，于是**日志系统里这次调度整体 0 条**」；
  「维护线程是**单线程池、跨轮复用**，因此标记 MUST 在 finally 里移除，否则下一轮非采集链路
  会被误判成需要全量上报。」
- **为什么 `Tracer` 用 `ObjectProvider` 注入**（`CardPoolServiceImpl.tracerProvider`，〔impl〕:129~136）：
  「用 `ObjectProvider` 而不是直接注入：`management.tracing.enabled=false` 的环境里
  没有 Tracer bean，直接注入会让本服务启动失败。取不到时降级为『不建 span』，
  行为与修复前一致，**不影响业务**。」拿不到 Tracer 或没有父 span 时不建 span、只透传采集标记（〔impl〕:193~194）。
- **为什么三行成组里那条 `exclude` 不能删**（〔prop〕:15~22，口径同 `pay-sign-server/application.properties`）：
  「1) `sampling.probability=0` 只让本服务发起的 trace 不采样，采样器是 `parentBased(traceIdRatioBased(0))`，
  上游带 `sampled=1` 的 traceparent / b3 头进来时 span 仍会被采样并进导出队列；
  2) micro/web 的 `web.properties` 已把 `management.otlp.tracing.endpoint` 整行注释掉，本行是第二道保险——
  K8s Deployment 只要注入 `MANAGEMENT_OTLP_TRACING_ENDPOINT` env 就会重新激活 exporter；
  3) Boot 3.2.6 没有 `management.tracing.export.enabled` 这个开关，把 endpoint 置空也不行
  （`OtlpAutoConfiguration` 只判断键是否存在），只能排掉整个自动配置。」
- **为什么本模块不注册调度**（〔boot〕:10~11）：「避免多副本重复执行」。

#### 2.6 持久层与 mapper

- **为什么候选查询返回多行**（〔xml〕:157~159）：「取多行候选而非固定取最小 ID：并发预占若都命中同一行，
  后到者会阻塞在行锁上而不是立刻返回 0 行，同票种预占会退化为串行。
  这里返回 limit 行候选，由服务侧随机挑选后走条件 UPDATE 做 CAS。」
- **为什么 `insertBatch` 显式写 0**（〔mapper〕:25）：「避免后续全字段更新写入 NULL」；
  `updateBatch` 对应侧「为 null 时不参与更新，保留库中原值」（〔mapper〕:107）。

### 三、陷阱

#### 3.1 卡池预占与确认

- **打开 tracing 后 `MapperAspectToTrace` 把异常包一层，按类型 catch 的兜底一条都进不去**
  （`card-pool-server` `CardPoolServiceImpl.isIntegrityViolation`，〔impl〕:311~315，ADR-D53）：
  「本模块打开了 tracing，`MapperAspectToTrace` 会切到所有 `@Mapper` 方法上，
  它此前把异常包成 `new RuntimeException(e)`，于是按类型 catch 的幂等兜底**一条都进不去**，
  `ORA-00001` 直接冒到全局处理器返 500。」
  ——同一份 catch 写法在没开 tracing 的模块里正常，只在本模块坏；`isIntegrityViolation` 保留是为了
  「挡住将来任何新增的包装切面」（同段〔impl〕:314~315）。
- **并发挑同一行不会立刻返回 0 行，而是阻塞在行锁上**，同票种预占退化成串行。
  （`CardPoolServiceImpl.pickCandidate`，〔impl〕:338~339；〔mapper〕:165~166；〔xml〕:157~159）
- **只认 `RESERVED` 会把重复请求误报成卡池耗尽**（〔impl〕:69~72，见 §2.1 第一条原文）。

#### 3.2 FTP 文件导入

- **把非唯一键冲突当成「重复卡号」会静默丢卡号**（`CardPoolServiceImpl.flushCards`，〔impl〕:833~834）：
  「字段超长、外键违反、连接中断等一旦被当成『重复卡号』，批次仍会被标成 SUCCESS，
  卡号静默丢失且日志无痕。」
- **`ERROR_MSG` 按字符裁剪会触发 ORA-12899，连带让「记录失败」本身失败**
  （`CardPoolServiceImpl.truncate`，〔impl〕:1187~1190；常量注释〔impl〕:89~93）。
- **FTP 的 LIST 报文只是对端自述的大小，实际传输可以远超**，没有上限会把容器磁盘写满
  （`CardPoolServiceImpl.SizeLimitedOutputStream`，〔impl〕:1237~1239）。
- **票种错位不报错、只在发卡后暴露**：第二列与批次票种不一致时若静默入库，
  「会让卡号被当成另一种票发出去」（`CardPoolServiceImpl.parseCardNo`，〔impl〕:1114~1115）。

#### 3.3 维护与回收

- **`updateBatch` 写 10 余列，任一列越长或类型不匹配都会整条失败**，批次卡在
  `REQUESTING` / `IMPORTING` 中间态并「把该票种的补货永久挡死」（〔mapper〕:117~119）；
  服务侧同源描述见 `CardPoolServiceImpl.fail`（〔impl〕:1022~1025），后果是
  「每轮维护都重走『REQUESTING → fail → 再失败』…… 该票种从此不再补货且无法自愈」。
- **锁续期返回 0 时继续写入会与抢锁方并发插入同批卡号**
  （`CardPoolServiceImpl.renewLockOrFail`，〔impl〕:818）。
- **十万行导入跑在请求线程（虚拟线程）上会长时间 pin 住载体线程**，
  「严重时全 JVM 虚拟线程停止调度」（`CardPoolServiceImpl.maintenanceExecutor`，〔impl〕:141~143）。
- **重复提交维护会把任务堆在单线程队列里**（`CardPoolServiceImpl.maintenanceRunning`，〔impl〕:150~151）。

#### 3.4 可观测性配置

- **异步线程里每条 SQL 各自新开一个 trace，按调度 traceId 检索本模块日志零命中**
  （`CardPoolServiceImpl.withTraceContext`，〔impl〕:175~179，含实测 traceId 列举）。
- **照 pay-sign 的「拷 MDC」写法在本模块实测无效**：JDBC observation「每次开始都会按当前 span
  **覆盖** MDC 的 traceId、结束时清掉」，末行日志 traceId 甚至为空（〔impl〕:185~191）。
- **在已结束的父 span 上重开 scope 是错的**（〔impl〕:181~183）。
- **有了 span 仍可能整轮 0 条**：采集标记只在请求线程 MDC 上，维护线程拿不到 ⇒ INFO 落
  `ThresholdFilter`(WARN) 被丢，而本模块 `other.web.enableLogRequestInFilter=false`
  请求线程一条业务 INFO 都不打（〔impl〕:196~202）。
- **单线程池跨轮复用，不在 finally 清标记会让下一轮非采集链路被误判成需要全量上报**（〔impl〕:204~205）。
- **采集标记键写成驼峰等于不上报**（`FirstFilter` 只放小写键、`ThreadContextMapFilter` 只认小写，〔impl〕:120~121）。
- **`sampling.probability=0` 挡不住上游带 `sampled=1` 的头**；**把 OTLP endpoint 置空也不行**
  （Boot 3.2.6 无 `management.tracing.export.enabled`，`OtlpAutoConfiguration` 只判断键是否存在）；
  **Deployment 注入 `MANAGEMENT_OTLP_TRACING_ENDPOINT` 就会重新激活 exporter**（〔prop〕:16~21）。
- **tracing 关闭的环境里没有 Tracer bean，直接注入会让本服务启动失败**（〔impl〕:132~134）。

#### 3.5 持久层与 mapper

- **`ROWNUM = 1 ... FOR UPDATE SKIP LOCKED` 与 `FETCH FIRST ... FOR UPDATE` 两条都不能用**（〔xml〕:160~161）：
  「NEVER 改成 `ROWNUM = 1 ... FOR UPDATE SKIP LOCKED`：ROWNUM 过滤先于加锁生效，
  首行被占时会直接返回 0 行而不是跳到下一行；FETCH FIRST 与 FOR UPDATE 并用则报 ORA-02014。」
- **全字段更新会把计数列写成 NULL**，靠 `insertBatch` 显式 0 与 `updateBatch` 的 `<if>` 两处共同兜住
  （〔mapper〕:25、:107；〔xml〕:47~54、:115~119）。

> **本模块 src/main 注释里没有的四条**（任务点名、但抽取范围内确实不存在，记此一行防止后人反复找）：
> Druid WallFilter 的两条规则、mapper XML 注释内连续减号、MCP 侧函数索引需包一层 PL/SQL、
> 建唯一索引前先按确切谓词统计重复。`LogicCardPoolMapper.xml` 只有两处注释（〔xml〕:124、:157~162），
> 内容分别是「最小化失败落库」与候选查询的取法，均不涉及这四条；本模块也没有
> `src/main/resources/sql/*.sql`。这四条的权威出处是 `AGENTS.md` §5.1 与 §8，
> **NEVER 因为它们没出现在本模块注释里就认为不适用**。

### 四、墓碑注释清单（建议转为断言测试）

不进正文。表内「可否断言化」只描述技术可行性与现有覆盖，不含改动建议。
现有单测：`CardPoolReservationTest`（预占 / 确认 / 释放，25 个用例）、`CardPoolCardNoParseTest`（行解析 7 个）、
`AccSignUtilsTest`（签名等价性）；**没有** controller 层与维护链路的测试。

| # | 文件:行号 | 禁止 / 强制的事 | 可否断言化 |
|---|---|---|---|
| 1 | 〔impl〕:311 | NEVER 简化回 `catch (DataIntegrityViolationException)` | **已断言**：`CardPoolReservationTest.reserveResolvesUniqueKeyRaceWhenExceptionWrapped`（该文件:193）造「包一层 RuntimeException」的异常再断言走通兜底 |
| 2 | 〔impl〕:185 | NEVER 退化成「只拷 MDC」（即 pay-sign `mdcTaskDecorator` 写法） | 可弱断言：mock `Tracer` 后断言 `withTraceContext` 提交的任务调用了 `nextSpan(parent)` + `withSpan`；真实「JDBC observation 覆盖 MDC」的行为单测覆盖不到 |
| 3 | 〔impl〕:196 | MUST 把 `VLOGS_CAPTURE_KEY` 透传进维护线程 | 可断言：请求线程 `MDC.put("x-vlogs-capture","1")` 后，在任务体内断言 `MDC.get` 非空 |
| 4 | 〔impl〕:204 | 标记 MUST 在 finally 里移除 | 可断言：任务执行完毕后断言维护线程 MDC 里该键已被移除（同一线程连跑两轮） |
| 5 | 〔impl〕:120 | 采集标记键 MUST 全小写 | 可断言：常量值等值断言（需放开可见性或反射） |
| 6 | 〔impl〕:819 | 续期失败时 MUST 立即失败 | 可断言：mock `renewBatchLock` 返 0，断言抛 `IllegalStateException` 且消息含「票种卡池锁已失效」 |
| 7 | 〔impl〕:833 | 「这里刻意不吞异常」（`flushCards` 非完整性冲突一律上抛） | 可断言：mock `insertCards` 抛非完整性异常，断言原样上抛且 `stats.valid` 未增；现有 `reserveRethrowsNonIntegrityFailure`（该文件:213）只覆盖 `reserve` 侧 |
| 8 | 〔impl〕:69 | 「含 ASSIGNED 是刻意的」（白名单不得收窄回只认 RESERVED） | **已断言**：`reserveReusesAssignedRecord`（该文件:123）与 `reserveReusesReservedRecord`（:107） |
| 9 | 〔impl〕:1022 | `fail` MUST 降级最小化落库，而不是只记日志 | 可断言：mock `updateBatch` 抛异常，`verify` `markBatchFailed` 被调用一次 |
| 10 | 〔impl〕:1187 | 必须按 UTF-8 字节而不是字符裁剪 | 可断言：长中文串入参，断言返回值 `getBytes(UTF_8).length <= 1900`（`truncate` 为 private，需反射或提可见性，口径同已 `static` 的 `parseCardNo`） |
| 11 | 〔impl〕:132 | `Tracer` 用 `ObjectProvider`、取不到时降级不建 span | 可断言：`ObjectProvider` 返回 null 时任务照常执行、不抛异常 |
| 12 | 〔ctl〕:47 | NEVER 再把三者混成一句「无可分配逻辑卡号」 | 可断言：新增 controller 单测，分别断言 `data!=null` / `data=null` / `code=400` 三档；当前无 controller 测试 |
| 13 | 〔prop〕:15 | NEVER 删除 `spring.autoconfigure.exclude=...OtlpAutoConfiguration` 这行 | 可断言：配置测试读 `application.properties`，断言三行成组同时存在（`enabled=true` + `probability=0` + `exclude` 含 `OtlpAutoConfiguration`） |
| 14 | 〔xml〕:160 | NEVER 改成 `ROWNUM = 1 ... FOR UPDATE SKIP LOCKED` | 可弱断言：读 mapper XML 文本，断言 `selectAvailableCandidates` 段内不含 `FOR UPDATE`、含 `FETCH FIRST`；`ORA-02014` 本身需真实 Oracle |
| 15 | 〔boot〕:10 / 〔svc〕:102 / 〔ctl〕:179 | 本模块不注册 `@EnableScheduling` / `@Scheduled` | 可断言：源码扫描断言 `card-pool-server/src/main` 内无 `@Scheduled` / `@EnableScheduling`（三处注释互为副本，改一处 MUST 看齐其余两处） |

### 五、抽取过程中发现的注释内部不一致（未改代码，供后续裁决）

1. **`ASSIGNED` 能否复用，两处注释口径相反**：
   `REUSABLE_STATUSES`（〔impl〕:66~74）说「含 ASSIGNED 是刻意的 …… 复用 ASSIGNED 记录返回同一卡号」；
   而 `reusableReservation`（〔impl〕:1056~1060）的注释说「只有仍处于 RESERVED 的记录可以复用；
   已 ASSIGNED 表示该业务已完成发卡，再当作新预占返回会让调用方重复走发行流程，且返回的过期时间为空」。
   代码实际按前者执行（`REUSABLE_STATUSES` 含两个值），后者注释与实现及单测
   `reserveReusesAssignedRecord` 都不一致，疑为改动时漏更的旧注释。
2. **`reserve` 返回 null 的条件，接口注释与实现不一致**：
   `CardPoolService.reserve`（〔svc〕:24）写「票种未启用卡池、参数不合法、无可用卡号或该归属已确认发卡时返回 null」，
   而实现对前两类是抛 `IllegalArgumentException`（〔impl〕:259~268，接口层转 `code=400`）、
   「该归属已确认发卡」按第 1 条复用返回非 null。实际返 null 只有「候选为空」与「重试耗尽」两条。

### 六、任务点名但本模块注释未承载的知识（据实记录，NEVER 据此认为规则不适用）

- **ADR-D52 那组结论在本模块 `src/main` 注释里没有任何文字**：`release` / `releaseExpired` 附近
  （〔impl〕:364~375、:603~607；〔mapper〕:202~215；〔xml〕:189~211）都只写「释放预占」「回收所有已过期的预占」，
  **没有**「失败分支 NEVER `releaseReservation`」「两条并发请求拿到同一个 `cardNo` 与 `reservationId`」
  「留给 `sys_job` 107 超时回收」「NEVER 用只有创建者才释放或对 `reservationId` 做 CAS」
  「确认失败 MUST NOT 返成功、MUST 开 `CARD_POOL_CONFIRM_REJECTED` 工单」这些字样。
  本模块注释里与超时回收有关的只有中性描述「预占超时由维护动作回收」（〔poolProp〕:28~29、〔cardEntity〕:8）。
  这些结论的权威出处是 `AGENTS.md` §5.2 与 `docs/domain/decisions.md` ADR-D52，
  以及**调用侧** `account-server`（本次抽取范围外）。
- **`8003 无卡资源` 在本模块 `src/main` 里 grep 不到**：全模块唯一出现是测试注释
  `card-pool-server/src/test/java/com/chinasofti/huateng/cardpool/service/impl/CardPoolReservationTest.java:189`
  「『8003 暂无卡数据资源』—— 池子里明明有卡。切面已改成原样抛出，本用例锁住 ……」。
  本模块只产出 `code=200 data=null`（〔ctl〕:50），`8003` 是**调用侧**的翻译结果。
- **`sys_job` 107 / cron `0 0/5 * * * ?` 在本模块注释里没有**：`src/main` 只说「由外部调度」「由 web-admin 的
  Quartz 任务或运维按需触发」（〔boot〕:10~11、〔ctl〕:179、〔svc〕:102~103），未写 job_id 与 cron。
  这两个值的出处是本文件正文「编码约束」小节与 `docs/architecture/web-server.md` §七。

## 附：card-pool-server 注释知识迁移（2026-09-16，阶段二·完整）

本阶段与阶段一的分工不同：阶段一是**摘录**（477 行，按主题归类）；本阶段是**迁移前的完整清点** ——
逐文件盘一遍注释承载的知识，凡「多行叙述 / MUST-NEVER / 事故史 / 墓碑」一律落进本节，随后从代码删除，
代码只留标准 Javadoc。**删除后代码里保留的一行式护栏见 §覆盖率自评 末尾清单。**

规模口径（2026-09-16 实测，按注释行统计、含 `/** */` 边界行）：`src/main` 1643 行、`src/test` 105 行、
SQL 1 个文件（`card-pool-schema.sql`，只有 3 条 `COMMENT ON TABLE`、**没有列注释**，因此本模块字段取值域
全靠实体类 Javadoc 承载，见 §九）。

文件别名（沿用阶段一）：〔impl〕`card-pool-server/src/main/java/com/chinasofti/huateng/cardpool/service/impl/CardPoolServiceImpl.java`、
〔svc〕`.../service/CardPoolService.java`、〔ctl〕`.../controller/CardPoolController.java`、
〔mapper〕`.../mapper/LogicCardPoolMapper.java`、〔xml〕`card-pool-server/src/main/resources/mapper/LogicCardPoolMapper.xml`、
〔prop〕`card-pool-server/src/main/resources/application.properties`、〔boot〕`.../CardPoolServer.java`、
〔client〕`.../client/AccLogicNumClient.java`、〔sign〕`.../util/AccSignUtils.java`、
〔batchEntity〕`.../entity/LogicCardPoolBatch.java`、〔cardEntity〕`.../entity/LogicCardPoolCard.java`、
〔poolProp〕`.../config/CardPoolProperties.java`、〔accProp〕`.../config/AccSecureProperties.java`、
〔ddl〕`card-pool-server/src/main/resources/sql/card-pool-schema.sql`、
〔t1〕`.../src/test/java/.../CardPoolReservationTest.java`、〔t2〕`.../CardPoolCardNoParseTest.java`、
〔t3〕`.../src/test/java/.../util/AccSignUtilsTest.java`。

### 一、ADR-D52 完整判据（预占是共享资源，失败分支 NEVER 释放）

**这是本模块最贵的一条知识，而它在 `src/main` 注释里原本一个字都没有**（阶段一 §六 已据实记录，
本阶段复核仍然如此：`release`〔impl〕:364、`releaseExpired`〔impl〕:603、〔mapper〕:202 / :211、
〔xml〕:189 附近只有中性描述「释放预占」「回收所有已过期的预占」）。因此本节是从 `AGENTS.md` §5.2 与
`docs/domain/decisions.md` ADR-D52 迁入文档、并在代码侧只留一行式告警的结果：

1. **幂等键是业务键，不是本次请求的唯一键**：`reserveFromPool` 按 `businessId` 幂等，开户场景
   `businessId = thirdUserId + ":" + 票种`；库侧唯一归属键是 `BUSINESS_TYPE + BUSINESS_ID`
   （〔cardEntity〕:42~49「业务类型，与业务流水号组成唯一归属键」，索引 `UK_LOGIC_CARD_POOL_BUSINESS`）。
2. **⇒ 两条并发请求拿到同一个 `cardNo` 与同一个 `reservationId`**，预占是**共享的**、不是本请求私有的
   （实现依据：〔impl〕:1056 `reusableReservation` 复用已有记录、〔impl〕:66 `REUSABLE_STATUSES`）。
3. **⇒ 失败分支 NEVER 调 `releaseReservation`**：兄弟请求可能已 `confirm`、已把卡号写进
   `USER_ITP_REG_INFO` 并对 APP 返 `0000`；此时释放会把卡号抽回池子，形成「账户表已发给 A、
   `LOGIC_CARD_POOL_CARD` 是 `AVAILABLE` 可再发给 B」，只留一行 ERROR 日志。2026-09-14 真实并发实测到。
4. **⇒ 正确形态是留给对方的超时回收**：`sys_job` 107「卡池维护」cron `0 0/5 * * * ?` →
   `POST /internal/card-pools/maintenance`（〔ctl〕:176~186「受理一轮卡池维护」）→ `releaseExpired`
   （〔impl〕:603~607、〔mapper〕:211~215「回收所有已过期的预占」）。
5. **NEVER 用「只有创建者才释放」或对 `reservationId` 做 CAS 来兜** —— 兄弟请求持有的就是同一个 id。
6. **连带**：「先占远端 → 落本地 → 再确认远端」链路里最后那步 `confirm` 失败 **MUST NOT 返成功**，
   MUST 落 `ACCOUNT_EXCEPTION_TICKET` 的 `CARD_POOL_CONFIRM_REJECTED` 工单（实现在调用侧 `account-server`）。

**代码侧保留**：`releaseReservation` 上一行式告警「NEVER 在失败分支调用」。删掉它会直接重现 ADR-D52
缺陷 —— 这是本模块唯一「删注释即等于删防线」的位置。
### 二、预占复用白名单：`RESERVED` 与 `ASSIGNED` **都**可复用

- 常量：〔impl〕:66~73 `REUSABLE_STATUSES`，原文可 grep「含 ASSIGNED 是刻意的」。
- 判据：`confirm`（〔impl〕:351）与 `release`（〔impl〕:364）都做了「影响行数为 0 时回查终态」的幂等兜底；
  若 `reserve` 只认 `RESERVED`，同一 `businessId` 在确认成功后重复请求会拿到 `null`，上游只能翻成
  「无可分配逻辑卡号」，**把重复请求误报成卡池耗尽**（2026-09-09 E2E 实测）。
- 回归用例：〔t1〕:116~121 `reserveReusesAssignedRecord`，原文「此前 ASSIGNED 不在复用白名单内，重复请求拿到 null」。
- **NEVER 把 `ASSIGNED` 从白名单里去掉**（〔impl〕:1059~1064）；卡号仍受 `BUSINESS_TYPE + BUSINESS_ID`
  唯一归属约束，不会跨业务泄漏。仍成立的副作用：已确认记录 `expireTime` 可能为空，
  `toReservation`（〔impl〕:1088）返回的过期时间字段为 `null`。

### 三、`reserve` 返回 `null` **只有两条**：池空 / 重试耗尽

- 权威口径：〔svc〕:20~42（含墓碑段「本注释此前写……是错的」）+ 〔ctl〕:44~56 的三分返回：
  `code=200 data!=null` 预占成功 / `code=200 data=null` 池空或重试耗尽（**正常业务结果**）/
  `code=400` 票种不走卡池、票种非法、归属缺失或归属冲突（**调用方缺陷，重试无用**）。
- 抛异常的两类：`IllegalArgumentException`（票种码非法/为空、该票种不走卡池、`businessType`/`businessId` 缺失）、
  `IllegalStateException`（该归属已占用其它票种卡号、已有记录状态不在复用白名单）。
- **NEVER 把三档混成一句「无可分配逻辑卡号」**（〔ctl〕:47）—— 那正是把「配置错」当「池空」重试的来源。
- 对应用例：〔t1〕:83~86（非卡池票种 0442 直接拒绝、不查库）、:93~95（归属缺失拒绝）、
  :132~134（跨票种归属冲突拒绝）、:142~144（池空返 null）。

### 四、`isIntegrityViolation(Throwable)` 沿 cause 链判定与 ADR-D53 的关系

- 方法：〔impl〕:308~319，可 grep「NEVER 简化回」+「ADR-D53」。
- 因果：本模块**开了 tracing**（见 §八），`resource/micro/web` 的 `MapperAspectToTrace` 曾用
  `observation.observe(Supplier)`，Micrometer 把 mapper 抛出的异常重新包一层 `RuntimeException`，
  于是**按类型 `catch (DataIntegrityViolationException)` 的幂等兜底一条都进不去**，`ORA-00001` 冒到
  全局处理器返 500，`account-server` 收到空体翻成 `8003 暂无卡数据资源` —— 池子里明明有卡（2026-09-14 实测）。
- 修法两层，**缺一不可**：①公共构件侧切面改成 `start()` + `openScope()` +
  `catch (Throwable e) { observation.error(e); throw e; }` + `finally stop()`，**NEVER 回退成 `observe(Supplier)`**；
  ②本模块侧沿 `getCause()` 链判定，**保留这层防御**（成本极低，且能挡住将来任何新增的包装切面）。
- 用例锁定：〔t1〕:183~191（冲突被包成 `RuntimeException` 时同样复用、不上抛）与
  〔t1〕:206~211（**非**完整性冲突 MUST 原样上抛，NEVER 当并发冲突吞掉后回查）。
- 连带：`8003` 在本模块 `src/main` 里 grep 不到，唯一出现是〔t1〕:189 的注释；本模块只产出
  `code=200 data=null`，`8003` 是调用侧的翻译结果。

### 五、IF7B-01 直连 ACC 申请批次（报文形态以实测为准）

- 归属：本模块 `AccLogicNumClient` **直连 ACC**，不经 `acc-secure-server` 转发，因此启动类不需要任何
  `@EnableRpcXxx`（〔boot〕:13~14、〔accProp〕:6~10「只保留卡号池用到的 IF7B-01 路径」）。
- **报文形态实测结论（〔client〕:26~30）**：迁出侧 `AccSecureServiceImpl` 发 `application/json`（整体 JSON、
  `bizData` 为对象），实测 ACC 一律回 **`1003 解析请求数据失败`**；ACC 只接受
  `application/x-www-form-urlencoded` + 公共参数平铺 + `bizData` 为 **JSON 字符串**（即 AGENTS.md §4 的项目统一形态）。
  迁出侧因始终未接线，该缺陷从未暴露。**NEVER 退回整体 JSON。**
- **响应键名实测（〔client〕:32~33、:181~186）**：`fileName` / `retCode` / **`returnMsg`** —— 消息键是
  `returnMsg` 而非规格写的 `RetMsg`，解析按 `retMsg` → `returnMsg` 顺序取，两种都能落进 `ERROR_MSG`。
- **成功码两种形态都在生产出现过**（〔impl〕:81~82 `ACC_SUCCESS_CODES`）。
- **传输层失败抛异常、不返带内部错误码的壳对象**（〔client〕:35~36），这样批次 `ERROR_MSG` 能落真实原因
  而不是统一的 8007 / 9001。
- 签名：〔sign〕:16~29，算法自 `acc-secure-server` 的 `AccSecureSignUtils` **逐字迁入未改**（公共参数与
  `bizData` 按 `key=value` 字典序拼接、`&` 连接，`signType=02` 追加 `&key=<signKey>` 取 MD5 十六进制；
  `signType=00` 不签名返空串，**当前生产配置即 `00`**）。迁出侧用 `com.alibaba.fastjson.JSON` +
  `SerializerFeature.MapSortField`、本类用 fastjson2 原生 API，2026-09-09 已实测两条 API 序列化逐字节一致；
  断言锁在〔t3〕:10~16 与其 5 个用例（`00` 返空串 / 空 signType 同 `00` / `02` 缺 signKey 拒签 /
  `02` 带 signKey 的 MD5 与迁出侧一致 / 其余 signType 一律拒绝）。**改签名或换 JSON 库时这里会先失败。**
### 六、FTP 下载与卡号入库

- **文件行格式（2026-09-09 从 FTP 抓到的真实文件 `0426090935.txt`）**：10 行、每行 20 字符、CRLF 换行、
  两列以单空格分隔，形如 `0426090935000008 41`；第一列逻辑卡号、第二列 ACC 票种（〔impl〕:768~769、:1117~1122）。
  **旧实现把整行当卡号，10 行全判非法、卡号零条入库**（〔t2〕:8~14 锁住解析结果）。
- **第二列票种与批次不一致即视为非法**（〔impl〕:1121~1122）：票种错位意味着 ACC 发错文件，静默入库会让
  卡号被当成另一种票发出去。空行跳过、不计入总数。
- **文件名校验拒绝路径穿越与非 `.txt`**（〔impl〕:1105~1110）。
- **下载有字节上限**（〔impl〕:1241~1247 `SizeLimitedOutputStream`）：FTP 的 `LIST` 报文只是对端自述大小，
  实际传输可远超；超限第一时间抛 `IOException`，由 `download()` 的 catch 删临时文件（〔impl〕:887~893、:967~971）。
  上限键 `card-pool.ftp.max-file-size`（〔poolProp〕:195~196）。
- **分片入库刻意不吞异常**（〔impl〕:830~838）：批量失败后逐条重试，**只有确定是唯一键冲突才按重复处理**，
  字段超长 / 外键违反 / 连接中断一律上抛 —— 否则批次仍会被标成 `SUCCESS`，**卡号静默丢失且日志无痕**。
- **同批次同票种的已有记录按「重复」计数**（〔impl〕:1175~1181），用于中断后重跑保留原有计数。
- 文件 SHA-256 摘要（〔impl〕:1156~1160）落 `FILE_SHA256`，用于重复导入校验（〔batchEntity〕:58~59）。

### 七、维护、批次状态机与失败落库

- **本模块无 `@Scheduled`、无 `@EnableScheduling`**，三处注释互为副本（〔boot〕:10~11、〔ctl〕:176~179、
  〔svc〕:116~120），改一处 MUST 看齐其余两处。维护入口是 `POST /internal/card-pools/maintenance`。
- **受理即返回**：维护与重试都只做同步校验 + 状态推进后立即返回，重活交给**单线程平台线程池**
  （〔impl〕:138~145 `maintenanceExecutor`、:396~402、:432~436、:505~506）。理由：全服务
  `spring.threads.virtual.enabled=true`，ojdbc8 大量方法 `synchronized`，一轮十万行导入跑在请求线程
  （虚拟线程）上会长时间 pin 住载体线程，严重时**全 JVM 虚拟线程停止调度**。
  单线程足够 —— 票种间并发本就由 `LOGIC_CARD_POOL_TYPE_LOCK` 串行化。重试返回的批次状态是
  `DOWNLOADING` **不是终态**，结果需再查 `/page/card-pools/batches`。
- **批次状态流转**（〔batchEntity〕:5~9、:365~377）：`CREATED → REQUESTING → DOWNLOADING → IMPORTING → SUCCESS`，
  任一环节异常置 `FAILED`；`FAILED` 且已有文件名可重试回 `DOWNLOADING`（另有 `RETRYING`）。
  卡号状态流转（〔cardEntity〕:5~8）：`AVAILABLE → RESERVED → ASSIGNED`，预占超时由回收动作退回 `AVAILABLE`。
- **`REQUESTING` 一律置 `FAILED` 交人工**（〔impl〕:669~675）：已向 ACC 发出申请但没拿到文件名时，
  无法判断对端是否已生成批次，重推会重复消耗 ACC 号段，因此直接失败并留明确原因。
- **票种锁在导入过程中续期，续不上立即中止**（〔impl〕:815~823、〔mapper〕:76~103）：续期返回 0 意味着锁已过期、
  可能被另一执行体抢走，继续写入会并发插入同批卡号。锁失效秒数 `card-pool.lock-stale-seconds`
  （〔poolProp〕:33~34「需大于单批次最长导入耗时」）。
- **失败落库必须降级，不能只记日志**（〔impl〕:1019~1029、〔mapper〕:114~124、〔xml〕:124）：`updateBatch` 写 10 余列，
  任一列越长或类型不匹配会整条失败；而 `REQUESTING` 同时在 `selectPendingBatches` 与 `countInProgress` 的状态集里，
  一旦「记录失败」本身失败，批次永停 `REQUESTING`，每轮重走「REQUESTING → fail → 再失败」，
  `replenish` 被 `countInProgress > 0` **永久挡死，该票种从此不再补货且无法自愈**。降级语句只写
  `STATUS` / `ERROR_MSG` / `FINISH_TIME` 三列。
- **`ERROR_MSG` MUST 按 UTF-8 字节裁剪、不是字符**（〔impl〕:89~92、:1191~1201）：列声明 `VARCHAR2(2000 CHAR)`，
  但 Oracle `MAX_STRING_SIZE=STANDARD` 下物理上限 4000 字节，中文按 3 字节计，只按字符裁会触发 `ORA-12899`，
  **连带让「记录失败」这件事本身失败**。
- 其余：`insertBatch` 五个计数列与重试次数由 SQL 显式写 0，避免后续全字段更新写 NULL（〔mapper〕:24~25）；
  `markRetrying` 的重试次数在 SQL 内自增，保证并发下不丢次数（〔mapper〕:127~131）。
- 除 `reserve` 外**均不带事务**，逐条 SQL 自动提交；ACC 申请与 FTP 下载**一律不在事务内发起**（〔impl〕:56~59）。
### 八、可观测性：三行成组（本文件是全项目样板）与异步 span

- **配置块 〔prop〕:9~24 是 AGENTS.md §2.2.1 点名的全项目样板**，三行 MUST 成组：
  `management.tracing.enabled=true` + `management.tracing.sampling.probability=0` +
  `spring.autoconfigure.exclude=...OtlpAutoConfiguration`。
  逐条理由（原文可 grep「NEVER 删除下面这行排除」）：
  ①`sampling.probability=0` 只让**本服务发起**的 trace 不采样（采样器 `parentBased(traceIdRatioBased(0))`），
  上游带 `sampled=1` 的 `traceparent` / `b3` 进来时 span 仍会被采样并进导出队列；
  ②`micro/web` 的 `web.properties` 虽已注释掉 `management.otlp.tracing.endpoint`，但 Deployment 一旦注入
  `MANAGEMENT_OTLP_TRACING_ENDPOINT` env 就会重新激活 exporter，这行是第二道保险；
  ③Boot 3.2.6 **没有** `management.tracing.export.enabled` 这个开关，把 endpoint 置空也不行
  （`OtlpAutoConfiguration` 只判断键是否存在），只能排掉整个自动配置。
  **本条一行式版本保留在 `application.properties` 里**（别的模块照抄这段，删掉会导致抄错）。
- **异步维护线程 MUST 建真子 span，NEVER 退化成「只拷 MDC」**（〔impl〕:172~210 `withTraceContext`）：
  `maintenanceExecutor` 是自建线程池，既不继承 MDC 也不继承 Micrometer observation 上下文；不包时该线程内
  没有活跃 span，每条 SQL 各开一个新 trace，一轮维护被打散成几十个 traceId，
  **用 web-admin 调度的 traceId 在本模块日志里一条也搜不到**（2026-09-09 实测：`0b93524901604401…` 零命中）。
  用 `nextSpan(parent)` 而不是搬父 scope —— 父 span 属于已返回的 HTTP 请求，很快就 end，
  **在已结束的 span 上重开 scope 是错的**。1.0.13 曾照 pay-sign 的 `mdcTaskDecorator` 改成
  `MDC.setContextMap(parentContext)`，**实测无效**（20 条 SQL 依旧各自一个 traceId、末行 traceId 甚至为空），
  1.0.14 已回退为建子 span。**MDC 拷贝只对「异步任务内不再产生 observation」的场景有效。**
- **除 span 外还 MUST 透传采集标记 `x-vlogs-capture`**（〔impl〕:113~122 `VLOGS_CAPTURE_KEY`、:196~205）：
  键名 **MUST 全小写**（`FirstFilter` 只放小写键、appender 的 `ThreadContextMapFilter` 也只认小写，
  写成驼峰等于不上报）；标记只在请求线程 MDC 上，维护线程拿不到就会落到 appender 的
  `ThresholdFilter(WARN)` 被丢弃，而本模块 `other.web.enableLogRequestInFilter=false`、请求线程一条业务 INFO
  都不打，于是**日志系统里这次调度整体 0 条**。维护线程是单线程池跨轮复用，因此标记 MUST 在 `finally` 里移除。
- **Tracer 用 `ObjectProvider` 注入**（〔impl〕:129~135）：`management.tracing.enabled=false` 的环境没有 Tracer bean，
  直接注入会启动失败；取不到时降级为「不建 span」，不影响业务。

### 九、持久层、mapper 与 DDL

- **候选卡号取多行 + 服务侧随机挑选 + 条件 UPDATE 做 CAS**（〔xml〕:157~161、〔mapper〕:162~171、
  〔impl〕:103~104 `CANDIDATE_LIMIT`、:335~340）。理由：固定取最小 ID 时并发预占会全部命中同一行、
  阻塞在行锁上而不是立刻返回 0 行，**同票种预占退化为串行**。
- **NEVER 改成 `ROWNUM = 1 ... FOR UPDATE SKIP LOCKED`**（〔xml〕:160~161）：`ROWNUM` 过滤先于加锁生效，
  首行被占时直接返回 0 行而不是跳到下一行；`FETCH FIRST` 与 `FOR UPDATE` 并用则报 **`ORA-02014`**。
- 三个写动作都是条件更新：`reserveCard` 仅 `AVAILABLE` 生效、`confirmCard` 仅 `RESERVED` 生效、
  `releaseCard` 仅 `RESERVED` 生效（〔mapper〕:175~208）—— 白名单式状态机，返 0 行由服务侧回查终态兜幂等。
- **DDL 只有 3 条表注释、没有列注释**（〔ddl〕:75~77 `LOGIC_CARD_POOL_BATCH` / `LOGIC_CARD_POOL_CARD` /
  `LOGIC_CARD_POOL_TYPE_LOCK`）。字段取值域全在实体 Javadoc：批次 18 个字段（〔batchEntity〕:13~110）、
  卡号明细 12 个字段（〔cardEntity〕:12~69），其中带取值域的有 `SOURCE`（`AUTO` / `MANUAL`）、
  `STATUS`（批次 7 值 / 卡号 3 值）、`ACC_TICKET_TYPE`（票种后两位）、`OPERATOR`（自动补货为 `SYSTEM`）、
  `EXPIRE_TIME`（确认后置空）、`ERROR_MSG`（最长 1900 字符，与 §七的字节裁剪配套）。
  **这批字段级 Javadoc 属「标准 Javadoc」，本次不删。**
- mapper XML 内保留一行式护栏「NEVER 在 SQL 正文里写注释」（Druid WallFilter 会判定为注入并让语句静默失效）。
### 十、逐文件覆盖清单（本阶段口径）

| 文件 | 注释行 | 本阶段处置 | 迁入本节位置 |
|---|---|---|---|
| 〔impl〕 | 401 | 叙述/事故/墓碑段全删，方法留一句式 Javadoc + `@param`/`@return` | §一~§九 |
| 〔svc〕 | 88 | 三分口径与墓碑段删除 | §三 |
| 〔ctl〕 | 80 | 三分返回说明、受理语义删除 | §三、§七 |
| 〔mapper〕 | 161 | 候选多行理由、最小化落库理由删除，`@param` 保留 | §七、§九 |
| 〔xml〕 | 7 | 候选多行那段（含 `ORA-02014`）删除；**保留**「SQL 正文禁写注释」一行 | §九 |
| 〔prop〕 | 11 | **整段保留**（全项目样板，见 §八） | §八 |
| 〔boot〕 | 14 | 无 `@EnableScheduling` / 直连 ACC 两段删除 | §五、§七 |
| 〔client〕 | 75 | 实测报文形态、`returnMsg`、抛异常三段删除 | §五 |
| 〔sign〕 | 39 | 迁入来源与双 API 一致性说明删除 | §五 |
| 〔batchEntity〕 / 〔cardEntity〕 | 266 / 161 | 状态流转类注释精简为一行；字段级 Javadoc **保留** | §七、§九 |
| 〔poolProp〕 / 〔accProp〕 | 209 / 136 | 字段级 Javadoc **保留**（本就是标准形态） | §七 |
| 〔t1〕〔t2〕〔t3〕 | 105 | 事故史与 ADR 引用删除，留用例意图一句 | §二、§四、§五、§六 |

### 矛盾与待裁决

1. **〔impl〕:1059~1064 与 〔impl〕:66~73 曾口径相反**（阶段一 §五 第 1 条）：前者旧版写「只有仍处于
   `RESERVED` 的记录可以复用」，与 `REUSABLE_STATUSES` 含两个值、以及单测 `reserveReusesAssignedRecord`
   都不一致。**本阶段按代码事实收口到 §二（两者都可复用），旧口径已作废、NEVER 回退。**
2. **〔svc〕:24 旧注释对 `reserve` 返 null 的四类描述有三类不成立**（阶段一 §五 第 2 条）：
   真正返 null 只有池空 / 重试耗尽。**已收口到 §三。**
3. **`ERROR_MSG` 列长口径两说**：〔impl〕:89~92 说「列声明 `VARCHAR2(2000 CHAR)`、物理上限 4000 字节、
   这里留足余量」，而〔batchEntity〕:88~90 与 :383~395 写「最长 1900 字符」。两者不冲突（1900 是留余量后的
   业务口径），但**数字不一致、易被当成矛盾**；未去库上核 `USER_TAB_COLS.CHAR_LENGTH`，**待裁决时 MUST 实测**。
4. **`sys_job` 107 与 cron `0 0/5 * * * ?` 在本模块代码里查不到**（阶段一 §六 已记）：`src/main` 只说
   「由外部调度 / web-admin Quartz 或运维触发」。本阶段把 job_id 与 cron 写进 §一，**出处是 AGENTS.md §5.2
   与 `docs/architecture/web-server.md` §七，不是本模块代码** —— 引用时 MUST 现查 `sys_job`。
5. **`AccSecureProperties` 的 `signKey` 有默认真值风险**：〔accProp〕:51~53 只说「MD5 签名 key，仅 signType=02 时使用」，
   未标注 `${ENV:}` 约束。当前生产 `signType=00` 不用它，故未处理；**改成 `02` 前 MUST 先按 AGENTS.md §5.2
   「敏感配置」把它收成空默认值 + K8s Secret 注入。**

### 墓碑清单（本阶段从代码删除、只在文档留证）

| # | 原位置 | 墓碑内容（原文短语可 grep 本文档） | 为什么不能重犯 |
|---|---|---|---|
| 1 | 〔svc〕:35~38 | 「本注释此前写『票种未启用卡池、参数不合法、无可用卡号或该归属已确认发卡时返回 null』是错的」 | 4 类里 3 类不成立，照它写降级逻辑会把 `code=400` 当池空重试 |
| 2 | 〔impl〕:1063~1064 | 「本注释此前写『只有仍处于 RESERVED 的记录可以复用』是错的」 | 去掉 `ASSIGNED` 会让重复开户拿到 null、被上游误报 8003 |
| 3 | 〔impl〕:311~315 | 「NEVER 简化回 `catch (DataIntegrityViolationException)`」（ADR-D53） | tracing 模块里异常曾被包一层，按类型 catch 全部落空 |
| 4 | 〔impl〕:185~191 | 「NEVER 退化成只拷 MDC，1.0.13 实测无效、1.0.14 已回退」 | 照 pay-sign 口径改会让一轮维护散成几十个 traceId |
| 5 | 〔client〕:26~30 | 「迁出侧发 `application/json`，实测 ACC 一律回 `1003`」 | 退回整体 JSON 即全部申请失败，且迁出侧从未接线、缺陷不会自己暴露 |
| 6 | 〔client〕:32~33 | 「消息键是 `returnMsg` 而非规格写的 `RetMsg`」 | 只按规格取键会让 `ERROR_MSG` 恒空、失败原因丢失 |
| 7 | 〔impl〕:1022~1025 | 「失败落库若失败，批次永停 `REQUESTING`、该票种永久不再补货」 | 只记日志不降级即等于埋一个不可自愈的死锁 |
| 8 | 〔impl〕:1194~1197 | 「MUST 按 UTF-8 字节裁剪，只按字符会 `ORA-12899`」 | 让「记录失败」这件事本身失败 |
| 9 | 〔xml〕:160~161 | 「NEVER 改成 `ROWNUM = 1 ... FOR UPDATE SKIP LOCKED`（`ORA-02014`）」 | 首行被占即返 0 行、同票种预占串行化 |
| 10 | 〔t2〕:11~13 | 「旧实现把整行当卡号，10 行全判非法、卡号零条入库」 | 行解析退回整行即整批导入静默为 0 |
| 11 | 〔t1〕:186~190 | 「并发开户返 `8003 无卡资源` —— 池子里明明有卡」（ADR-D53 实测） | 同 #3，是那条修法的现场证据 |
| 12 | 〔impl〕:669~674 | 「`REQUESTING` 直接置 FAILED 交人工，避免重复消耗 ACC 号段」 | 改成重推会重复申请、并让该票种被永久占位 |

### 覆盖率自评

- **注释侧覆盖**：`src/main` 1643 行中，「叙述 / MUST-NEVER / 事故史 / 墓碑」类**已 100% 落进本节或阶段一**
  （逐文件核对见 §十）。剩余 1120 余行是 getter/setter 与字段级一句式 Javadoc（〔poolProp〕209 +
  〔accProp〕136 + 两个实体 427 + 〔mapper〕`@param` 段占大头），**属标准 Javadoc、不在删除范围**。
- **任务点名的 8 项**：ADR-D52 完整判据 §一 ✅；`RESERVED`+`ASSIGNED` 可复用 §二 ✅；`reserve` 返 null 两条 §三 ✅；
  `isIntegrityViolation` 与 ADR-D53 §四 ✅；IF7B-01 直连 ACC + FTP 下载 + 入库 §五/§六 ✅；无 `@Scheduled` §七 ✅；
  tracing 三行成组（`application.properties:9~24` 是样板）§八 ✅。
- **未覆盖 / 降级说明**：①`ERROR_MSG` 真实列长未去库核（矛盾 3）；②`sys_job` 107 / cron 非本模块代码事实（矛盾 4）；
  ③`8003` 只存在于测试注释、正式实现不产出该码；④DDL 无列注释，字段取值域只能以实体 Javadoc 为准。
- **代码侧保留的一行式护栏（删除阶段后逐条复核过）**：
  1. `card-pool-server/src/main/resources/application.properties` tracing「三行成组、NEVER 只加第一行」
     —— 全项目样板，删掉会导致别的模块照抄错；
  2. `card-pool-server/src/main/resources/mapper/LogicCardPoolMapper.xml`「SQL 正文禁写注释」；
  3. 〔impl〕`releaseReservation` 上「NEVER 在失败分支调用」—— 删掉直接重现 ADR-D52。
