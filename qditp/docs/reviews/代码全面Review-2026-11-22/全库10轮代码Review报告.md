# qd-itp 全库 10 轮代码 Review 报告

| 项目 | 内容 |
|---|---|
| 审查日期 | 2026-11-22 |
| 审查范围 | qd-itp 全仓 **27 个模块 / 2402 个 Java 源文件**，含 Mapper XML、`pom.xml`、`application*.properties`、前端 `web/` |
| 审查方式 | 10 个维度逐轮独立审查（并行执行，**只读，未修改任何代码**） |
| 判据来源 | `AGENTS.md` §4、`docs/architecture/`、`docs/business/`、`docs/domain/decisions.md`、`docs/提示词/`、`docs/接口规范文档/`（只读事实源） |
| 报告性质 | 审查发现汇总；所有结论均给出文件与行号证据，可直接定位复核 |

---

## 一、执行摘要

### 1.1 问题统计

共记录 **95 条**问题，按严重度：**P0 致命 4 / P1 严重 28 / P2 一般 47 / P3 建议 16**。跨轮重复约 5 条（详见 §五），去重后约 **90 项独立问题**。

| 轮次 | 维度 | P0 | P1 | P2 | P3 | 合计 |
|---|---|---:|---:|---:|---:|---:|
| 1 | 并发与线程安全 | 0 | 2 | 7 | 2 | 11 |
| 2 | 事务与数据一致性 | 1 | 4 | 2 | 0 | 7 |
| 3 | 安全与密钥合规 | 2 | 3 | 3 | 1 | 9 |
| 4 | 异常处理与容错 | 0 | 3 | 5 | 2 | 10 |
| 5 | 性能与资源 | 0 | 2 | 6 | 2 | 10 |
| 6 | SQL 与 MyBatis/Oracle | 0 | 5 | 4 | 2 | 11 |
| 7 | 架构规范与依赖 | 0 | 2 | 4 | 2 | 8 |
| 8 | 接口契约与 DTO | 0 | 1 | 6 | 3 | 10 |
| 9 | 业务正确性与边界 | 1 | 3 | 5 | 0 | 9 |
| 10 | 健壮性与工程实践 | 0 | 3 | 5 | 2 | 10 |
| **合计** | | **4** | **28** | **47** | **16** | **95** |

### 1.2 P0 致命问题（4 项，建议立即处置）

1. **[P0-1] 支付宝退款并发双提交，唯一索引建错键** — `alipay-pay-sign-server/.../PaymentRefundService.java:88`
   去重靠无锁 `count`，退款单号随机生成，唯一索引建在随机键上 → 并发双退款、**直接资损**。
2. **[P0-2] HSM 密钥导出/签名接口验签器注入但从未调用** — `acc-security-server/.../ItpRemainingController.java:22-80`
   网络可达即可导出用户 SM2 私钥、CA 密钥、DPK，击穿 HSM 管控。**需人工复核安全合规性**。
3. **[P0-3] 账户核心接口（开户/绑卡/销户/改手机号）验签器注入未调用** — `account-server/.../RequestApplicationController.java:53,70-133`
   资金账户写接口无签名/无鉴权，存在水平越权与账户接管风险。**需人工复核安全合规性**。
4. **[P0-4] 支付宝渠道所有出向请求硬编码测试签名 `sign="test"`** — `alipay-pay-sign-server/.../PayCenterClient.java:188-191`
   签约/扣款/退款/解约签名全部无效，`docs/business/alipay-channel.md:467` 已标注为「【P0 上线阻塞】」。

### 1.3 关键结论

- **资金链路重复发起**是最高频风险：扣款重试队列（第1/2/9轮三次命中）、退款并发（P0-1）、计次票扣次（第9轮 P2）均存在并发兜底不足。
- **验签器"注入但不用"**是安全问题共性模式（`acc-security-server`、`account-server`），非单点疏漏。
- **事务包裹 RPC**（`RetryQueueConsumer`）违反 `docs/architecture/transaction-and-consistency.md` §6，长事务持锁 + 回滚引发状态不一致。
- **已确认合规**：`debitRequestResult` 四处出口映射逐字一致（SUCCESS→0 / INIT,PROCESSING,RETRY→null / 其余→2），全仓无旧值残留，`model.version` 全仓 26 处均为 `2.0.0`，无真实 Feign/Redis/MQ 业务引用。

---

## 二、第1轮：并发与线程安全（11 项）

> 全库确认不存在：静态 `SimpleDateFormat`、虚拟线程 pin、`parallelStream` 共享状态、缺 `volatile` 的双重检查、ReentrantLock 死锁环。问题集中在"扫表 + 外部调用"的重复投递和线程池无界。

### P1

- **[P1] 通知扫表任务无领取标记，多实例重复推送 APP 通知** — `face-pay-server/src/main/java/com/chinasofti/huateng/facepay/service/F2fNotifyDeliverer.java:36,64-66`
  `loadDueTasks()` 只按 `NOTIFY_STATUS='PENDING'` 捞取后直接 HTTP 投递，成功才回写；`F2fNotifyTaskMapper.xml:66-72` 无 CAS 领取字段。多实例/上轮未跑完即重复投递，APP 可能重复入账/出票。
  **修复**：仿 `GateRetryQueueMapper.markProcessing` 增加 `PENDING→PROCESSING` 原子领取（带租约），领取独立事务提交后再投递；加超时回归 `PENDING` 补偿。

- **[P1] 扣款重试队列领取在事务内、外部扣款在提交前，可重复发起扣款** — `gate-txn-pay-server/src/main/java/com/chinasofti/huateng/gatetxnpay/service/impl/RetryQueueConsumer.java:46,73,87`
  `consumeBatch()` 标 `@Transactional`，`markProcessing` 与外部支付 RPC 同事务；Oracle READ_COMMITTED 下另一实例看不到未提交的 `PROCESSING`，重复捞取并再次 `requestPaySign`。**修复**：领取用 `REQUIRES_NEW` 立即提交，外部调用移出事务边界。

### P2

- **[P2] 两个无界 `newCachedThreadPool` 暴露在 HTTP 请求路径** — `web-server/web-admin/.../service/ServiceStatusService.java:30,33`：`probeExecutor` 与 HttpClient executor 均无上限，并发探活可耗尽线程资源。**修复**：有界池 `(4,8,60s,ArrayBlockingQueue(64),CallerRunsPolicy)` + `join` 总超时。
- **[P2] 网关双发使用按请求创建的无界线程池** — `resource/route-sentinel/.../dual/DualQueryService.java:62-66,159-163`：下游变慢时线程随并发线性膨胀。**修复**：固定池 + 有界队列，或 JDK21 虚拟线程池 + 阻塞 HTTP 超时。
- **[P2] `@Async` 未指定执行器，落到 Spring Boot 默认无界队列** — `acc-es-server/.../EsReportServiceImpl.java:81`（同类 `EsTaskServiceImpl.java:323`）：全库无 `TaskExecutor` Bean，队列 `Integer.MAX_VALUE`。**修复**：显式声明有界 `ThreadPoolTaskExecutor` 并在 `@Async` 指定。
- **[P2] face-pay-server 8 个 `@Scheduled` 共用默认单线程调度器** — `face-pay-server/.../FacePayServer.java:13`：无 `ThreadPoolTaskScheduler`，慢任务串行阻塞其余全部、cron 触发被跳过。**修复**：`setPoolSize(>=8)` + 优雅关闭。
- **[P2] 第三方对账定时任务无分布式领取** — `recon-server/.../ThirdPartyReconScheduler.java:46-56,84-105`：多实例并发 MERGE（易 ORA-30926）并覆盖同一账期差异文件。**修复**：`BILL_DATE+渠道` 粒度数据库领取行 + `FOR UPDATE SKIP LOCKED`。
- **[P2] Controller 静态 `LinkedHashMap` 被并发请求读写** — `web-server/web-admin/.../controller/tool/TestController.java:29,65,81-82,92`：丢更新/`ConcurrentModificationException`。**修复**：`ConcurrentHashMap`；生产 profile 禁用该示例 Controller。
- **[P2] 日票订单号依赖进程内 `AtomicInteger`，多实例碰撞且无冲突重试** — `daily-ticket-server/.../order/DailyTicketOrderCreationService.java:49,340-347`：各实例从 1 开始，同秒大概率重号，无 `DuplicateKeyException` 重取号。**修复**：Oracle SEQUENCE/号段表 + 唯一键冲突有限次重试。

### P3

- **[P3] `synchronized(key.intern())` 把用户可控字符串变成全局互斥锁** — `web-server/web-framework/.../aspectj/RateLimiterAspect.java:65`：限流器自身成热点瓶颈。**修复**：分段锁或 `ConcurrentHashMap.compute`。
- **[P3] SqlAudit 的 ThreadLocal 只在 `audit()` 中清理** — `resource/micro/sql-datasource/.../SqlAudit.java:69-70,127`：异常路径残留导致慢 SQL 日志串台。**修复**：`set`/`remove` 严格 try/finally，用栈支持嵌套。

---

## 三、第2轮：事务与数据一致性（7 项）

### P0

- **[P0] 支付宝退款并发双提交，唯一索引建错键** — `alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/service/impl/payment/PaymentRefundService.java:88`
  去重仅靠无锁 `count`，`refundOrderNo = "R"+currentTimeMillis+UUID` 每次随机，迁移脚本 `.../resources/sql/alipay-refund-order-no-unique-migration.sql:2-3,52` 自认挡不住并发但把索引建在 `REFUND_ORDER_NO` 而非 `ORDER_NO`。异常分支的 `DuplicateKeyException` 兜底永不命中 → **重复退款/资损**。
  **修复**：建 `UK(ORDER_NO)` 或 `(ORDER_NO,DELETE_FLAG)` 防重，先 insert 占位再调支付中心；并发第二笔捕获唯一键冲突返回「已受理」；`refundOrderNo` 由 `orderNo` 确定性派生。

### P1

- **[P1] 补偿消费在事务内发起远程 RPC，行锁跨越支付链路** — `gate-txn-pay-server/.../RetryQueueConsumer.java:46,87`：`consumeBatch()` 整批（默认10笔）一个事务，循环内调 `paySignInitiator.retryAndConverge`（HTTP）。上游重推堆在同一行串行等待，对端超时会连 `markFailed` 证据一起回滚。**修复**：去 `@Transactional`，每笔短事务 + CAS 收口，或 `afterCommit` 触发。
- **[P1] 签约回调 catch 全异常后返回错误，`@Transactional` 形同虚设** — `pay-sign-server/.../SignResultCallbackHandler.java:52,113-118`：外层 catch 只记日志 + `fillError` 后 return，从不重抛/`setRollbackOnly`。若 `insert(SIGNED)` 之后通知记录/事件失败，事务仍提交 → APP 永收不到签约成功，重推走 `replaySignResult` 只返成功不补通知，**不一致永久固化**。**修复**：DB/事件异常必须重抛或 `setRollbackOnly`，通知记录同事务落库。
- **[P1] 支付宝出行签约 check-then-insert 无并发兜底，异常被吞** — `pay-sign-server/.../ContractDomainServiceImpl.java:148,163-189`：库有 `UK_APP_PAY_SIGN_INFO_USER_VENDOR`，并发第二笔 `DuplicateKeyException` 落进通用 catch → 返回「系统错误」引发反复重试。**修复**：直接 insert + 捕获唯一键冲突回查按「已签约」幂等返回。
- **[P1] 重复落单后无条件改状态，可回退他人已终态订单** — `gate-txn-pay-server/.../writer/GateTxnPayWriter.java:68`：命中唯一索引返回已存在订单后仍无条件 `updateStatus`（`GateTxnPayMapper.xml:221-228` 无前置状态条件），免扣费入口重推可把 `FAIL` 终态改回 `SUCCESS`。**修复**：改带前置状态白名单的 CAS，显式判重短路。

### P2

- **[P2] CAS 失败回查为空时降级无条件 MERGE，覆盖并发新状态** — `ticket-server/.../gate/GateTicketWriter.java:57`：`QRCodeStatusMapper.xml:31-69` 的 MERGE 无 WHERE 全字段覆盖。**修复**：删无条件 upsert 降级，或改纯 insert 语义。
- **[P2] `USE_COUNT` 用 Java 读-改-写回写，非数据库原子自增** — `ticket-server/.../gate/GateTxnAssembler.java:88`：遇无条件 MERGE 降级或 `TXN_SEQ` 重置并发时丢更新。**修复**：改 `SET USE_COUNT=NVL(USE_COUNT,0)+1 WHERE CARD_ID=? AND TXN_SEQ=?`。

---

## 四、第3轮：安全与密钥合规（9 项）

> 配置/文档中的明文口令按既定口径（`docs/ops/environments/跨环境共性.md` §三）**不作为问题上报**；SQL 注入、SSRF、fastjson autoType 未发现真实可利用点。

### P0（需人工复核安全合规性）

- **[P0] HSM 密钥导出/签名接口验签器注入未调用** — `acc-security-server/.../controller/ItpRemainingController.java:22-80`、`ItpSignPubkeyController.java:22-54`：所有 `@PostMapping`（`/ci/itp/requestExportUserPriKey`、`requestCaKey`、`requestUserSm2Key`、`requestDPK`、`requestSignPubkey`）从未调用 `ItpRequestSignVerifier`，全库无调用点。**修复**：AOP 或逐接口统一强制验签，失败立即返回。
- **[P0] 账户核心接口验签器注入未调用** — `account-server/.../controller/ci/app/RequestApplicationController.java:53,70-133`：9 个接口未调 `validateCommonRequest`/`checkSign`；`updatePhone` 用 GET + `@RequestParam` 改手机号且明文打印。**修复**：写接口强制签名 + 时间窗，`updatePhone` 改 POST。

### P1（需人工复核安全合规性）

- **[P1] 验签器内置硬编码默认密钥，且 `signType=00` 直接放行** — `acc-security-server/.../ItpRequestSignVerifier.java:18,26-28`、`account-server/.../AccountRequestVerifier.java:77-79`：`@Value("${itp.signKey:bc4f7c96259acf9946094fa}")` + `NO_SIGN=00 return true` + MD5/SHA1 摘要。**修复**：fail-closed 启动、禁止请求参数指定「不签名」、升级 SHA256WithRSA/HMAC-SHA256。
- **[P1] JWT 签发者/受众校验值取自未验签 token 自身 payload** — `resource/micro/web/.../utils/JwtUtils.java:151-164`：`getExtendData` 仅 Base64 解码无验签，用其中 `iss`/`aud` 作期望值；`getAuthorizationDataByMdc` 直接暴露未验签 payload 给业务取用户身份。**修复**：期望值取服务端配置常量，验签后再解析 claims。
- **[P1] 密钥同步链路使用 DES/3DES-ECB 与固定全零 IV** — `key-server/.../TripleDesEcbUtils.java:13-14,44-48`、`acc-security-server/.../itp/util/ItpDesUtils.java:13,22-33`：弱算法 + 确定性加密危及用户私钥/DPK。**修复**：AES-GCM/带认证 CBC + 随机 IV，在 HSM 内完成（需评估协议兼容）。

### P2

- **[P2] 支付与账户接口明文打印完整请求报文** — `fep-app-server/.../PaySignController.java:45,69,80,112`、`fep-alipay-server/.../FepAlipayTripController.java:43-53`、`account-server/.../RequestApplicationController.java:72,78,104,122`：控制器层日志绕过 `LogContentMasker`。**修复**：统一脱敏日志，禁止 `{}` 打印整个 DTO/body。
- **[P2] fastjson 1.x 与 Fastjson2 混用** — `account-server/.../AccountRequestVerifier.java:3-4`、`acc-secure-server/.../AccSecureSignUtils.java:3-4`、`ticket-server/.../notify/CardTranNotifier.java:3`、`model/.../HttpResponseBody.java:3` 等：`AccountRequestVerifier` 的 1.x 序列化用于签名串构造，差异可能致签名不一致。**修复**：全库迁 Fastjson2，核对 `MapSortField` 等价性（属防御纵深+一致性）。
- **[P2] 签名摘要使用 SHA-1/MD5，且无时间戳防重放窗口** — `pay-sign-server/.../AppNotifySigner.java:32-33`、`face-pay-server/.../PayCenterSigner.java:69,85-88`、`account-server/.../AccountRequestVerifier.java:20,52-57`：时间戳仅校验 `\d{14}` 格式。**修复**：±5 分钟窗口 + nonce/请求号幂等。**需人工复核安全合规性**。

### P3

- **[P3] 内部接口异常信息直接回传前端** — `web-server/web-framework/.../GlobalExceptionHandler.java:44,85,94`、`web-admin/.../CommonController.java:80,113`：`AjaxResult.error(e.getMessage())` 泄漏实现细节。**修复**：统一通用错误码，堆栈仅服务端记录。

---

## 五、第4轮：异常处理与容错（10 项）

> 整体质量较高：多数 RPC 用 `RpcOutcome` sealed 接口区分 Ok/BizRejected/Unreachable。问题集中在 `@Async/@Transactional` 边界、魔法错误码、异步通知缺持久化补偿。

### P1

- **[P1] Netty 任务下发：批量状态更新异常被吞后仍继续下发** — `acc-es-server/.../netty/service/BusinessHandler.java:180-187`：`catch` 仅 `log.info` + `printStackTrace`，`sqlSession.close()` 后继续执行 `mack=NORMAL` 下发。**修复**：回滚并抛异常，或置 `OPERATOR_ERROR` 直接返回；改 `log.error` 带任务号。
- **[P1] ACC 文件解析：`@Async+@Transactional` 中异常被吞，任务状态永久滞留** — `acc-es-server/.../EsReportServiceImpl.java:82,190-203`：只 catch `FileNotFoundException|IOException`，`NumberFormatException` 等未捕获；无 `@AsyncUncaughtExceptionHandler`。**修复**：捕获 `Exception` 显式置 `FAILURE` 并写 procLog，或配置全局 Async 异常处理器。
- **[P1] 资金查询 catch-all 吞异常后返回成功码** — `trans-query-server/.../AlipayTravelQueryHandler.java:113-117`：与 `findTravelDetail`（用 `SYSTEM_ERROR`）口径不一致；`assembleRecords` 中明细异常被吞致乘车记录不完整却返回成功。**修复**：区分 `BusinessException`/`RpcException`/未知异常，明细失败返回 `PARTIAL` 标识。

### P2

- **[P2] 错误码成功判断魔法字符串全库泛滥且口径不一** — `alipay-pay-sign-server/.../PaymentRequestService.java:154`、`key-server/.../KeySyncServiceImpl.java:450` 等 30+ 文件：`"SUCCESS"`/`"200"||"0000"`/`"0000"?"COMPLETED":"FAIL"` 至少 3 种写法。**修复**：统一 `RetCode` 枚举含 `isRetryable()`。
- **[P2] 黑名单通知 fire-and-forget，无超时/重试/落库补偿** — `blacklist-server/.../BlacklistServiceImpl.java:305-367`：`WebClient` 无 timeout，`subscribe()` 后即返回，加黑/解黑渠道侧通知失败即永久丢失。**修复**：配置超时 + 有界退避重试 + 「落库待通知 + `@Scheduled` 扫表补偿」。
- **[P2] 退款回调：异常数值直接跳过金额比对** — `daily-ticket-server/.../DailyTicketRefundCallbackService.java`：`catch(NumberFormatException)` 后跳过金额一致性校验继续推进。**修复**：解析失败拒绝回调 + 记 MANUAL 工单，不得跳过关键资金校验。
- **[P2] 全局异常处理器覆盖不全，部分泄漏类型信息** — 27 模块仅约 8 个有 `@RestControllerAdvice`（缺 face-pay/daily-ticket/account/cardpool/blacklist/gate-txn-pay 等）；`recon-server/.../ReconInternalExceptionHandler.java:29-34` 把 `ex.getClass().getSimpleName()` 作响应体。**修复**：抽取公共 advice，未知异常仅返回通用码 + traceId。
- **[P2] Netty Server 启动 `InterruptedException` 仅 printStackTrace 不恢复中断** — `acc-es-server/.../NettyServer.java:74-77`：主流程认为启动完成但端口未监听。**修复**：`Thread.currentThread().interrupt()` + 上抛失败。

### P3

- **[P3] 关闭文件流 finally 中异常可能覆盖业务异常** — `acc-es-server/.../EsReportServiceImpl.java:194-201,319-336`：改 try-with-resources。
- **[P3] 支付中心签名使用测试桩 `"test"`** — `alipay-pay-sign-server/.../PayCenterClient.java:188-191`：**与第9轮 P0 同一问题**，真实 `signWithRsa` 存在但从未调用；按 profile 注入真实实现，生产强制 RSA。

---

## 六、第5轮：性能与资源（10 项）

### P1

- **[P1] APP 通知线程池满时退回调用方线程同步执行 HTTP，拖慢过闸应答** — `ticket-server/.../config/TicketAsyncConfig.java:22-35`：核心4/最大16，但 `queueCapacity=2000` 使 maxPoolSize 永不生效；拒绝处理器 `runnable.run()` 同步执行（注释自认拖长过闸应答），`AppNotifyServiceImpl.java:37-76` 把 4 类推送（含过闸校验）全提交到该池。**修复**：队列降至 100~200，关键/非关键通知拆池，过闸链路不回退调用方。
- **[P1] ES 票卡文件解析 O(N×M) 嵌套循环 + 循环内重复创建 DateTimeFormatter** — `acc-es-server/.../EsReportServiceImpl.java:277-288`：逐行读文件后线性查找 `tblTktPrePeople` 并 `ArrayList.remove(i)`，每行新建 2 次 formatter。**修复**：`Map<phyCode,Person>` 预建索引 O(1) 删除；formatter 提 `static final`；流式批处理写库。

### P2

- **[P2] 风险组绑定规则 N+1 查询 + 逐条 insert** — `para-server/.../RiskManagementServiceImpl.java:112-116`：2N 次数据库往返。**修复**：`selectExistingIds(List)` 批量查 + `insertBatch`。
- **[P2] 风控命中派生逐条 insert** — `para-server/.../RiskHitDeriveServiceImpl.java:111-123`：命中数千条时单条 insert 放大。**修复**：构造 List 后分批 500~1000 批量写。
- **[P2] SXSSFWorkbook 未 dispose 导致临时文件泄漏 + 强制 `System.gc()`** — `web-server/web-admin/.../export/executor/AbstractExcelExportExecutor.java:76-89`：显式 `dispose()` 并删除 `System.gc()`。
- **[P2] ES 信息全表查询直接暴露为接口** — `acc-es-server/.../EsInfoServerImpl.java:91-93`、`EsInfoController.java:18-21`：`/all` 无分页无过滤。**修复**：PageHelper 分页 + 结果上限。
- **[P2] 无界 `newCachedThreadPool` 缺少背压** — `web-server/web-admin/.../ServiceStatusService.java:30-34`、`resource/route-sentinel/.../DualQueryService.java:62-66`（与第1轮重复）。**修复**：有界池 + 拒绝策略。
- **[P2] 日票通知线程池 maxPoolSize 形同虚设且关闭时丢弃任务** — `daily-ticket-server/.../DailyTicketAccNotifyExecutorConfig.java:15-19`：队列 1000 使 max=4 永不生效，`setWaitForTasksToCompleteOnShutdown(false)`。**修复**：队列降至 100~200 + 优雅关闭 + 明确拒绝策略。

### P3

- **[P3] 报表导出通过 RPC 一次性全量拉取数据到内存** — `web-server/web-admin/.../export/executor/FacePayOrderExportExecutor.java:54-59`、`CardPoolBatchExportExecutor.java:51-55`：与 SXSSF 流式写初衷相悖。**修复**：RPC 加分页/游标，边查边写。
- **[P3] 批量通知/补款收敛循环内串行 HTTP/RPC** — `face-pay-server/.../F2fNotifyDeliverer.java:62-64`、`supplement/SupplementPayCenterFlow.java:172-185`：批耗时=单条RT×N。**修复**：有界小线程池并行 + 批量回写。

> 对账文件处理（`ThirdPartyBillCsvParser`、`TxnDiffReconService`）使用 try-with-resources + HashMap 比对 + 批量 insert，属合理实现，未误报。

---

## 七、第6轮：SQL 与 MyBatis/Oracle（11 项）

> 判据真源：`docs/architecture/mybatis-oracle-runtime-traps.md`。`${params.dataScope}`、`${sql}` 代码生成器均属合法场景；ROWNUM 分页写法正确；`<foreach>` 空集合调用方均已 guard——均未误报。

### P1

- **[P1] `WHERE 1=1` + 全部谓词可选，空筛选时查询被 Druid 静默失效** — `ticket-server/.../mapper/QRCodeTxnDetailMapper.xml:234,260,308,345` 四条语句：触发 traps §2 `select alway true condition not allow`，HTTP 200 + UUID retCode。**修复**：改 `<where>` 或保留必选谓词（如默认近 N 天）。
- **[P1] 同类 `WHERE 1=1` + 全可选** — `alipay-pay-sign-server/.../AlipayPayLogMapper.xml:158,188`（且 `debitRequestResult='2'` 分支不产出谓词）；`face-pay-server/.../SupplementOrderMapper.xml:240`（片段被列表+count共用）。**修复**：同步改 `<where>`。
- **[P1] 对 VARCHAR2 混合格式时间列做 `TO_DATE`，触发 ORA-01861 且索引失效** — `alipay-pay-sign-server/.../AlipayPayLogMapper.xml:176,179,206,209`：schema 记载 `ALIPAY_PAY_LOG.TRANS_TIME VARCHAR2(20)` 存量既有 `yyyy-MM-dd HH:mm:ss` 也有毫秒时间戳。**修复**：存量归一为定宽字符串后字典序比较，或加镜像列，不在 SQL 里 `TO_DATE(TRANS_TIME,...)`。
- **[P1] 写语句参数未写 `jdbcType`，首次状态推进即抛「无效的列类型: 1111」** — `card-pool-server/.../mapper/LogicCardPoolMapper.xml:110-116`：`updateBatch` 无条件写 6 个可空字段无 `jdbcType`，调用方 `CardPoolServiceImpl.java:513-515` 在字段全 null 时调用。**修复**：逐列补 `jdbcType`。

### P2

- **[P2] `TRUNC(列)` 写在相关子查询谓词里索引失效** — `para-server/.../RiskHitDeriveMapper.xml:63`：改半开区间 `>= TO_DATE(...) AND < +1`。
- **[P2] 按小时分组取错 PAY_TIME 字符位置** — `recon-server/.../ReconReportMapper.xml:136,145`：`SUBSTR(PAY_TIME,12,2)` 但列定义 `VARCHAR2(14) YYYYMMDDHH24MISS` 小时在第9-10位。**修复**：改 `SUBSTR(PAY_TIME,9,2)`；执行前现查存量格式确认。
- **[P2] 前置通配符模糊查询打在主键列上** — `key-server/.../ComDeviceSynKeyMapper.xml:70,73,91,94`：`DEVICE_ID like '%'||...||'%'`，count 无上限。**修复**：精确匹配改 `=` 或后缀匹配 + count 加时间窗。
- **[P2] OR 连接两条前置模糊匹配** — `para-server/.../DeviceInfoMapper.xml:77`：双列索引均失效。**修复**：拆分支 + 加必选 `LINE_ID`/`STATION_ID`。

### P3

- **[P3] MERGE source 参数缺 `jdbcType`** — `key-server/.../ComDeviceSynKeyMapper.xml:20,22`：补 `jdbcType=VARCHAR`。
- **[P3] 等值查询列上套 `NVL` 使本地唯一索引不可用** — `gate-txn-pay-server/.../GateTxnPayMapper.xml:452,454-455`：`NVL(TRX_TYPE,' ')=...`。**修复**：列 NOT NULL 时直接等号比较。

> 说明：题设背景「分区表只到 P202612 导致 2027 无落点」与仓库证据不符——所有 RANGE 分区表末尾均有 `PARTITION P_MAX VALUES LESS THAN (MAXVALUE)`；`DAILY_TICKET_INSTANCE.COUNTING_START/END` 实际为 `NUMBER(20,0)`，resultMap `BIGINT` 正确。本轮按仓库证据处理。

---

## 八、第7轮：架构规范与依赖（8 项）

> 合规项：`model.version` 全仓 26 处均 `2.0.0`；无 `@FeignClient`/Redis 业务引用；`javax.*` 仅出现在测试禁用清单字符串；rabbitmq-adaptor 仅基础构件自引用；无循环/反向依赖。

### P1

- **[P1] para-server 用标准 MyBatis 注解写完整 CRUD，且为绕过注入检测而改** — `para-server/.../mapper/risk/GraylistRiskMapper.java:4`：`@Select/@Insert/@Delete` 全注解（11-39行），同模块其余 29 个 mapper 均走 XML；`git log edd0001075` 显示是「修复 SQL 注入检测（移除 WHERE 1=1）」时改的，注解绕过了 Druid WallFilter。**违反**：AGENTS.md §4.1「唯一遗留是 collect-pay OrderSeqMapper（该模块已删除）」，属**新增同类写法**。**修复**：删注解 SQL，建 `GraylistRiskMapper.xml`，`<where>` + `#{}`。
- **[P1] blacklist-server 操作记录 mapper 使用 `@Select` 注解 SQL** — `blacklist-server/.../mapper/BlacklistOperateLogMapper.java:18`：`git log 1069287523` 新增，非遗留，审计数据绕过统一治理。**修复**：改走同名 XML + 显式列。

### P2

- **[P2] model 公共报文类引入 Jackson `@JsonInclude`** — `model/.../front/response/HttpResponseBody.java:2`、`ResponseHead.java:3`：核心响应骨架混入 Jackson 与 fastjson 1.x 注解，`@JsonInclude` 在 Fastjson2 路径下不生效、NON_NULL 语义静默丢失。**修复**：换 fastjson2 `@JSONField` 或 ResponseBuilder 统一处理。
- **[P2] acc-es-server 实体用 Jackson `@JsonFormat` 承载出参格式化** — `acc-es-server/.../model/TblTktTaskPlan.java:35`：时间格式依赖 Jackson 转换器而非 Fastjson2，时区 `GMT+8` 硬编码与「部署设 TZ」口径冲突。**修复**：改 `@JSONField(format=...)`。
- **[P2] 多个核心模块仍用 fastjson 1.x 坐标** — `account-server/pom.xml:79` 等：全库约 168 个文件 `import com.alibaba.fastjson.*`，含 ticket-server 的 controller/notify。**修复**：统一坐标与 import，分批回归。
- **[P2] acc-es-server 自建 `DateUtil` 工具类** — `acc-es-server/.../util/DateUtil.java:7`：`new SimpleDateFormat("yyyyMMdd")`，被 10+ 处引用。**违反**：AGENTS.md §4.1「NEVER 主动创建新工具类」；编码规范 §3.5.1。**修复**：改 `java.time` 或复用公共构件。

### P3

- **[P3] web-server 导入 controller 承载 120 行业务逻辑** — `web-server/web-admin/.../importer/CommonImporterController.java:92`：文件落盘、BizImportRecord 落库、Excel 解析、统计、状态回写全在 controller。**修复**：迁入 service带 `@Transactional`（RuoYi 存量风格，故定 P3）。
- **[P3] 对账文件上传 controller 内含落盘/改名/DB 落库全流程（114 行）** — `web-server/web-admin/.../recon/ReconUploadFileController.java:76`：抽取上传 service。

---

## 九、第8轮：接口契约与 DTO（10 项）

> **高危点核查结论**：`debitRequestResult` 四处出口映射（ticket-server `toAppDebitResult`、trans-query-server `toAppDebitResult`、`AlipayTravelQueryHandler.mapDebitStatusToResult`、`PaymentQueryService.mapPayStatusToDebitResult`）**逐行语义完全一致**：`SUCCESS→"0"`、`INIT/PROCESSING/RETRY→null`、`CLOSED/未知/空→"2"`；全仓无 `? "0":"1"` 残留。两份 `TransRecordAssembler` 与 `/ci/app` 三条 Controller 路径逐字一致。

### P1

- **[P1] 支付/退款回调接口无验签、无幂等键** — `alipay-pay-sign-server/.../controller/paycenter/PayCenterCallbackController.java:27-44`：`/payNotify` 直接 `@RequestBody` 交 service，DTO 仅 6 字段无 `sign`/`requestId`/`notifyId`（`model/.../AlipayTripPayNotifyReqDTO.java:4-22`），`AlipayPayCallbackServiceImpl.java:44` 过滤状态白名单后即同步终态；`/refundNotify` 同样裸接。内网可达即可伪造成功回调。**修复**：DTO 加签名字段 + `notifyId` 唯一索引幂等，验签失败固定错误码留证。

### P2

- **[P2] 出口响应码 `2` 与请求侧筛选 `2` 值域不同源** — `model/.../TransRecordAssembler.java:96-103` ↔ `gate-txn-pay-server/.../GateTxnPayMapper.xml:479-480`：出口把 `CLOSED`/未知/两侧皆空映射 `"2"`，请求侧 `1 or 2` 只筛 `('RETRY','FAIL')` → `CLOSED`/脏数据永久漏单，且响应 `1`（线下补款成功）被当失败筛选。**修复**：显式补 `CLOSED`/NULL，或文档与前端固定「响应 2 不可回传筛选」。
- **[P2] 同一 Handler 对"系统内部错误"返回两个不同 retCode** — `trans-query-server/.../AlipayTravelQueryHandler.java:115`（FAIL=9999）vs `:303`（SYSTEM_ERROR=9001），retMsg 同为"系统内部错误"。**修复**：统一 9001 + 单测锁定。
- **[P2] 同一 Controller 两个分页接口基准不一致** — `alipay-pay-sign-server/.../legacy/AlipayPayLogController.java:34-37`（1-based）vs `:57-60`（0-based）：相同 `pageNum=1` 一个取首页、一个跳首页。**修复**：统一 1-based。
- **[P2] "乘车记录 payAmount"三处取值不同源** — `AlipayTravelQueryHandler.java:169,275` 仅取 `gate.getTrxAmount()`；`ticket-server/.../TransRecordAssembler.java:55-60` 优先 `pay.getCashAmount()`。**修复**：抽公共金额解析口径统一三处。
- **[P2] 同一乘车记录折扣字段一处算真实值、一处恒空串** — `AlipayTravelQueryHandler.java:179-180,288-289`：`EMPTY_DISCOUNT=""` 写死；APP 侧由 `discountFee/couponAmount` 计算。**修复**：确认是否刻意；非刻意则复用解析逻辑。
- **[P2] 同一支付回调契约存在两套行为不一致的实现** — `alipay-pay-sign-server/.../callback/AlipayPayCallbackServiceImpl.java:72`（回写支付流水再同步扣费）vs `.../payment/PaymentQueryService.java:178-222`（仅 recordCallback + 同步，不回写支付流水）。**修复**：择一实现，单一接口/入口收口。

### P3

- **[P3] 分页参数命名与类型三套并存** — `AlipayTripFindTravelListReqDTO.java:14-17`（String page/size）、`RequestTransListReqDTO.java:8-9`（Integer pageNumber/pageSize）、`QueryTransListReqDTO.java:22-23`（pageNumber/pageSize + offset/limit）。**修复**：对外网关统一并文档化，内部单用 offset/limit。
- **[P3] 同类查询接口日期参数命名不一致** — `AlipayPayLogController.java:32-33`（startTime/endTime）vs `:53-54`（startDate/endDate）：复用参数对象时日期条件静默忽略返全量。**修复**：统一 startDate/endDate + 旧名兼容一版。
- **[P3] 请求 DTO 混入响应字段且与内部 DTO 重复** — `RequestTransListReqDTO.java:10` 含响应字段 `totalPage` 但服务端实际收 `QueryTransListReqDTO`。**修复**：删除请求 DTO 的 totalPage。

---

## 十、第9轮：业务正确性与边界（9 项）

> 全部结论结合 `docs/business/`、`docs/domain/decisions.md` 口径核对。

### P0

- **[P0] 支付宝渠道所有出向请求硬编码测试签名 `sign="test"`** — `alipay-pay-sign-server/.../util/PayCenterClient.java:188-191`：`signRequest()` 中 `request.setSign("test")`，`buildRequest` 第113行必调；`buildSignData`/`signWithRsa` 已实现但无调用方。`docs/business/alipay-channel.md:467` 标注「【P0 上线阻塞】」。**修复**：改用已实现的真实签名，私钥缺失时 fail-fast，禁止回落 test。

### P1

- **[P1] 用户重试队列消费把跨服务 RPC 包进 `@Transactional`** — `gate-txn-pay-server/.../RetryQueueConsumer.java:46,87`（与第1/2轮同一问题）：`docs/business/gate-txn-pay.md:61` 明确「**NEVER** 带 `@Transactional`（链路含 RPC）」。后续单笔异常回滚已成功 RPC 的状态写入 →「钱已发起、订单仍 RETRY」→ 下轮重复扣款。**修复**：去方法级事务，改逐笔短事务 + 状态 CAS + 扫表补偿。
- **[P1] 离线码首次算价 0 元走「免扣费 SUCCESS」收口** — `gate-txn-pay-server/.../GateTxnPayServiceImpl.java:88,103-107`、`fare/FareCalculator.java:138-143`：`calculateOfflineFare` 只判 null 不判 `<=0`；第107行 `totalAmount<=0` 直接 SUCCESS，补偿链路 `OfflineFareRecoveryServiceImpl:81-87` 反有 0 元守卫，两路不对称。违反 `docs/business/gate-txn-pay.md:128-129` 三条红线 → **票价参数异常时免费过闸且无补偿入口**。**修复**：免扣费分支排除离线码 0 元场景转 PENDING 留痕；`FareCalculator` 校验 `>0`。
- **[P1] 第三方对账差异重跑 delete+insert 非原子，零差异时不清旧数据** — `recon-server/.../TxnDiffReconService.java:83-86`：`reconcile` 无 `@Transactional`，`if(!diffs.isEmpty())` 才删旧插新。`docs/business/recon-thirdparty.md:178` 已列此债。**修复**：无条件删旧插新并纳入事务，或幂等 MERGE/带版本整批替换。

### P2

- **[P2] 出站金额未校验负数与非整数分** — `gate-txn-pay-server/.../GateTxnPayServiceImpl.java:107,241-243,420-424`：`validate()` 只校验三字段，`parseAmount` 无异常兜底，负数金额落免扣费 SUCCESS。**修复**：validate 要求非负整数分，解析失败返 8001。
- **[P2] 计次票并发扣次 CAS 失败仍返回成功并置 `USED`** — `daily-ticket-server/.../DailyTicketInstanceLifecycleService.java:265-275,285`：`decreaseActualTimes` 影响行数 0 时 `remainTimes` 仍取旧值，`markUsed` 照写。**修复**：`updated==0` 直接返回失败，不写 USED。
- **[P2] 站名展示路径直查 `STATION_INFO`，与写入侧版本化表不一致** — `gate-txn-pay-server/.../GateTxnPayMapper.xml:283-290` 无 `PARA_VER_NO` 过滤，写入侧经 para-server 读 `TBL_STATION_INFO` 按 MAX 版本。`docs/business/gate-txn-pay.md:195` 明示「未裁决，NEVER 当成已收口」。**修复**：收口唯一权威源并补一致性断言。
- **[P2] 渠道账单脏字段静默转 0/跳行，对账差异失真** — `recon-server/.../ThirdPartyBillCsvParser.java:36-39,110-116`、`TxnDiffReconService.java:188-191`：字段数≠22 跳过，`yuanToCents(null)=0L` 参与比对产生误报 `AMOUNT_MISMATCH`。**修复**：非法行进入异常清单并告警，禁止静默转 0。
- **[P2] 日票有效期终点 `COUNTING_END` 完全采信 APP 上送** — `daily-ticket-server/.../DailyTicketInstanceLifecycleService.java:158`：无判空、不与当前时间与 `PERIOD` 交叉校验。**修复**：校验落在 `[now, now+合理上限]` 且与激活 PERIOD 一致。

---

## 十一、第10轮：健壮性与工程实践（10 项）

### P1

- **[P1] 对账核心 recon-server 零单测，四个状态机缺白名单测试** — `recon-server/.../service/ReconBatchService.java:50`：全模块 54 主类 0 测试文件；`ReconBatchStatus`/`ReconPartStatus`/`ReconSourceStatus`/`ReconFileStatus` 无 `*WhitelistTest`，`pom.xml:91` 已引入 starter-test 却无用例。违反《单元测试规范》§2.4/§6.1。**修复**：`@ParameterizedTest` 遍历全部枚举值断言白名单外拒绝 + 终态不可迁移 + 重复事件幂等。
- **[P1] 黑名单文件构建测试是无断言伪测试且用 JUnit4** — `blacklist-server/.../HceBlacklistFileBuilderTest.java:4`：`import org.junit.Test`，`generateSampleFile()` 仅落盘 + `System.out.println`，零断言，卡号 `0178606904586419` 疑似真实 PII。**修复**：改 JUnit5，`assertArrayEquals` 断言文件头/版本/记录数，卡号换虚构值。
- **[P1] rpc 依赖版本全仓不一致（2.0.1 / 2.0.3 并存）** — `rpc/pom.xml:8` 为 2.0.3，但 `recon-server:27`、`pay-sign-server:37`、`ticket-server:36` 等 20+ 模块仍 2.0.1。网络重试/`responseTimeout` 修复只在 2.0.3 生效，跨模块行为分裂。**修复**：统一版本并逐服务重打包，加 lint 断言。

### P2

- **[P2] daily-ticket-server 日期格式化 15 处重复且仍用 `SimpleDateFormat`** — `daily-ticket-server/.../refund/DailyTicketRefundMessages.java:23` 等 10 类：`DateTimeFormatter static final` 集中到现有 support 类，单号时间注入 `Clock`。
- **[P2] FareCalculator 折扣金额计算重复 + 状态字面量散落** — `gate-txn-pay-server/.../fare/FareCalculator.java:106,180` 两处相同 `BigDecimal` 计算；`:64-65,100,111,153` 散落 `"01"/"02"`。**修复**：抽私有方法 `calcDiscountedFare`，魔法值补进 `GateTxnPayFieldCode`。
- **[P2] 调试 main 类与 `System.out` 遗留生产源码树** — `acc-security-server/.../service/Main.java:11`、`util/sm2/SM2.java:137-160`、`Cipher.java:39-53`、`web-server/.../RyTask.java:12-22`：改 slf4j 或删除。
- **[P2] fastjson 两套坐标与版本并存** — `recon-server/pom.xml:61`、`pay-sign-server:87-88` 等用 `com.alibaba:fastjson`，`gate-txn-pay-server:69-70` 等 12+ 模块用 `com.alibaba.fastjson2:fastjson2`（与第3/7轮重复）。**修复**：统一坐标与版本，签名链路改动须人工复核。
- **[P2] `@Deprecated` 遗留 API 无替代说明与清理期限** — `rpc/.../PaySignClient.java:282`、`ticket-server/.../QRCodeTxnDetailMapper.java:30/34/38` 等 7 处。**修复**：零引用则删除，否则补 `@deprecated` Javadoc。

### P3

- **[P3] 前端错误分支残留 `console.log`** — `web/src/utils/request.js:70,111`：改统一错误提示/上报或删除。
- **[P3] 根 pom 聚合模拟器与验证门约定冲突** — `pom.xml:53` 含 `employee-card-acc-simulator`，而 AGENTS.md §6 明确「NEVER 从根跑全量（会带进该模拟器假失败）」。**修复**：移出默认聚合，改独立 profile。

---

## 十二、跨轮重复与一致性核查

### 12.1 跨轮重复（去重时合并计数）

| 问题 | 命中轮次 | 统一位置 |
|---|---|---|
| 重试队列事务内发 RPC / 重复扣款 | 第1、2、9 轮 | `RetryQueueConsumer.java:46,87` |
| 支付宝签名 `sign="test"` | 第4轮 P3、第9轮 P0 | `PayCenterClient.java:188-191` |
| fastjson 1.x/2.x 双栈 | 第3、7、10 轮 | 多模块 pom + import |
| 无界 `newCachedThreadPool` | 第1、5 轮 | `ServiceStatusService.java:30,33`、`DualQueryService.java:62-66` |

### 12.2 已核查为合规/既定场景（避免误报）

- `debitRequestResult` 四态映射四处逐字一致；两份 `TransRecordAssembler` 与 `/ci/app` 三条 Controller 路径一致。
- `${params.dataScope}`（RuoYi 数据权限）、`${sql}`（代码生成器 DDL）属合法 `${}` 场景；全库 `<foreach>` 空集合调用方均有 guard。
- ROWNUM 分页写法正确；MERGE source 均为 `SELECT ... FROM DUAL` 单行，无 ORA-30926 风险。
- 分区表均有 `P_MAX` 兜底；`COUNTING_START/END` 为 NUMBER 类型；`model.version=2.0.0`；无 Feign/Redis/MQ 业务引用。
- 状态机字符串字面量按编码规范 §1.3.3 属存量已知形态，按既有风格续写，未重复上报。

---

## 十三、修复优先级路线图

| 优先级 | 建议动作 | 关联问题 |
|---|---|---|
| **立即（P0）** | ① 支付宝退款幂等改 `UK(ORDER_NO)` + 先占位再调支付；② HSM/账户接口补强制验签（人工复核）；③ `PayCenterClient` 启用真实 RSA 签名 | P0-1 / P0-2 / P0-3 / P0-4 |
| **本迭代（P1 资金与并发）** | ④ `RetryQueueConsumer` 去事务 + 领取/投递分离；⑤ 离线码 0 元资损修理；⑥ 计次票 CAS 失败返失败；⑦ 签约回调异常重抛/`setRollbackOnly`；⑧ 重复落单改白名单 CAS；⑨ 回调接口补验签+`notifyId` 幂等 | 第1/2/8/9 轮 P1 |
| **本迭代（P1 安全）** | ⑩ 删除硬编码签名密钥默认值、`signType=00` 放行；⑪ JWT iss/aud 改服务端常量；⑫ 支付报文明文日志脱敏；⑬ 检视 DES/ECB（需评估协议兼容） | 第3轮 P1/P2 |
| **下一迭代** | 线程池有界化、通知扫表领取标记、对账批次分布式领取、Mapper `WHERE 1=1`/`jdbcType`/`TO_DATE` 整改、标准 MyBatis 注解迁 XML、recon 状态机白名单测试、rpc 版本统一 | 第1/5/6/7/10 轮 |
| **持续治理** | fastjson 统一 2.x、错误码枚举化、分页/金额/折扣口径统一、日期格式化收敛、调试代码清理 | 第3/4/8/10 轮 |

---

### 12.3 与既有审查记录的关系

本次为**全库 10 维度独立审查**，与同日的 `docs/reviews/2026-11-22-盲区10轮代码Review.md`（范围：web/ 前端、web-server、resource、mapper；已产出 IT-097）范围互补。以下发现与既有跟踪重叠，属**跨次复核确认仍未修复**，非本次重复劳动：

- `WHERE 1=1` 全可选谓词（本次第6轮 3 项 P1）在盲区 review `:138` 已列为「仅报告」，本次独立复核确认仍未整改。
- 验签 fail-open / 密钥相关红线：IT-097 已跟踪 web-server token.secret 弱口令 + Druid 外网暴露、resource TrustAll TLS、route-sentinel 路由无鉴权 + 断言 fail-open；本次第3轮新增命中 acc-security-server 与 account-server 的「验签器注入未调用」，属**不同模块的新问题**，建议与 IT-097 合并入同一安全专项。
- 分区表 `P_MAX` 兜底、`COUNTING_END` 列类型辟谣结论与盲区 review 一致。

---

## 十四、审查覆盖、排除项与局限

- **覆盖**：`src/main/java` 全量关键词扫描 + 关键文件逐行阅读；`src/main/resources/mapper/**` 全量 XML；各模块 `pom.xml`、`application*.properties`；前端 `web/src`；`src/test/java` 测试分布。
- **未覆盖**：运行时动态行为（需部署后 traceId / VL 日志验证）；数据库实际数据与执行计划（如 `PAY_TIME` 存量格式、`TRX_TYPE` 是否 NOT NULL 需现查确认）；外部渠道协议对端实现；红岛生产环境配置。
- **局限**：静态审查无法证明并发时序问题的必然触发，所报并发问题均给出代码层证据，需结合部署副本数与压测复核。涉及支付/密钥/签名的整改**必须人工复核安全合规性**，且不得在本次审查中直接改动加密/签名逻辑。
- **纪律**：本次审查全程只读，未修改任何代码/配置/文档（本报告除外）；所有报告文件位于 `docs/reviews/`（lint 门禁明确排除的记录目录）。
