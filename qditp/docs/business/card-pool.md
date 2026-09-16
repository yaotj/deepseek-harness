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
