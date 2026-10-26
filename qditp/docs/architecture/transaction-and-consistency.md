# 分布式一致性 / 事务 / RPC 边界不变量

> **何时读**：改支付 / 订单 / 扣款 / 签约 / 开户 / 卡池 / 对账链路，或写 `@Transactional` 方法、Spring 事件、RPC 包装方法时 **MUST 先读**。
> 这些规则**不再固化进 `AGENTS.md`**（2026-10-15 抽离，遵循「常驻上下文只放高海拔规则、细节按需 JIT 加载」），改由本文件承载，并经 AGENTS.md §5.1「任务 → 必读路由」按需加载。
> **本文档的「现状」段落均为 2026-10-15 全仓静态扫描结论；MUST 现查源码复核，尤其是带「已清零 / 已补齐 / 只剩 N 个」字样的完成态断言——这类断言最容易过期。**

## 共同特征（先建立心智模型）

这几条约束共享同一个失效模式：**变更在编译期、单测、`xmllint` 里都是绿的，缺陷只在生产 Oracle / 并发 / 重推下才暴露**，且多数被全局异常处理器包成 **HTTP 200 + UUID `retCode`** 或 **Spring 原生 500 报文**，前台只显示「查询失败」。因此**排查判据必须落在服务端日志原文**，NEVER 只看 HTTP 状态码。

---

## 1. 「按业务键幂等」的远端资源 NEVER 在失败分支回滚

`reserveFromPool` 按 `businessId` 幂等，**两条并发请求拿到的是同一个 `cardNo` 和同一个 `reservationId`** —— 预占是**共享的**，不是本请求私有的。于是「我失败了就 `releaseReservation`」会把兄弟请求正要 `confirm` 的卡号抽回池子，而那条兄弟已经把卡号写进 `USER_ITP_REG_INFO` 并对 APP 返了 `0000`，结果**同一个卡号在账户表是「已发给 A」、在 `LOGIC_CARD_POOL_CARD` 是 `AVAILABLE`（可再发给 B）**，只留一行 ERROR 日志。2026-09-14 真实并发实测到（ADR-D52；属潜伏缺陷首次触发）。

**判据**：远端资源的键是业务键、不是本次请求的唯一键时，本请求就**不是该资源的唯一持有者**，MUST 放弃「失败即回滚」，改为**留给对方的超时回收**。**NEVER 试图用「只有创建者才释放」或对 `reservationId` 做 CAS 来兜** —— 兄弟请求持有的就是同一个 id，任何以它为键的判断都分不清彼此。

连带一条：这类「先占远端 → 落本地 → 再确认远端」的链路，**最后那步确认失败时 MUST NOT 返成功**，MUST 落异常工单转人工（`ACCOUNT_EXCEPTION_TICKET` 的 `CARD_POOL_CONFIRM_REJECTED`，常量 `AccountExceptionTicket.TYPE_CARD_POOL_CONFIRM_REJECTED`），否则不一致对上游完全不可见。

**现状（2026-10-15 核实）**
- `AccountRegistrationServiceImpl.requestApplication` 的三处失败分支均只置 `allocation = null`、**不释放**；全仓 `releaseReservation` **零生产调用点**（只剩接口声明 + 实现 + 注释）。**该禁令已被遵守。**
- 回收任务是 `sys_job` **240「卡池数据导入」**，cron `0 0/5 * * * ?`，触发目标 `cardPoolQuartzTask.runMaintenance()`；2026-09-21 由 107「卡池维护」改名改号，**NEVER 回退成 107**（改名改号留痕：`web-server/web-quartz/src/main/resources/sql/web-quartz-card-pool-job-rename-migration.sql`）。
- ⚠️ `businessId` 当前格式 MUST 现查 `AccountRegistrationServiceImpl.buildAccountOpenBusinessId`：`ticketLimit=2` 走 `MULTI:<UUID>`；否则走**长度前缀键** `UNIQUE:<用户长度>:<用户><所属方长度>:<所属方>:<票种>`（支付宝侧前缀 `ALIPAY_ACCOUNT_OPEN`，ADR-D143）。旧格式 `thirdUserId + ":" + 票种` 已下线——ADR-D52 里出现的 `BUSINESS_ID='99900916:0441'` 是当时缺陷现场的旧格式留样，**不是当前态**。
- ⚠️ 已知文档缺口：`account-server-schema.sql` 的 `TICKET_TYPE` 列注释漏了 `CARD_POOL_CONFIRM_REJECTED`（代码已在两条开户链路真实开单）。

## 2. 跨模块写操作的顺序

一个事务内既要调远端（RPC）又要改本地时，**MUST 先调远端、后改本地**，远端失败即抛异常回滚，把状态留在中间态等对方重试。顺序颠倒会留下「本地已完成、远端未清理」的不一致，且无法自愈。

**宿主现状（2026-10-15 核实，ADR-D87 / D48 / D122 后）**
- 解约申请在 `ContractDomainServiceImpl.requestTermination` —— 但它**已摘掉 `@Transactional`、体内无 RPC**，不再是「远端在事务内」的活例子。
- 解约成功收口的**真实实现宿主是 `TerminationResultCallbackHandler.receiveTerminationResult`**；`CallbackDomainServiceImpl.receiveTerminationResult` 自 ADR-D122 起只是保接口不变的**纯委派门面**，NEVER 在它里面找业务逻辑。
- `PaySignWorkflow` 类定义已随 ADR-D87（2026-09-15）删除，全仓仅剩注释/文档提及，**NEVER 再引用该类名**。
- pay-sign 模块当前带 `@Transactional` 的方法只有 3 个（`PaySignTransactionBoundaryArchTest.EXPECTED_TRANSACTIONAL_METHODS` 冻结集合），**MUST 现查，NEVER 假设解约链路仍有事务**。
- 该收口分支已按 ADR-D48 改为「本地短事务 + `CHANNEL_SYNC_*` outbox + 提交后出网」——它的远端调用**不允许**被事务包住（见第 6 条）。

## 3. RPC 包装方法 MUST 返回 `RpcOutcome`，NEVER 返回 boolean

多个 RPC 包装方法内部 `catch` 全部异常后 `return false`、**从不抛异常**，调用方一旦丢弃返回值就会把「远端失败」写成「本地成功」，既不重试也不开工单（ADR-D13 即此类）。**排查这类静默不一致 MUST 先看调用点有没有接住返回值。**

`rpc` 模块现役 sealed `RpcOutcome`（`rpc/.../outcome/RpcOutcome.java`）：`Ok` / `BizRejected(retCode, retMsg)` / `Unreachable(cause)`。调用点用穷尽 `switch` 模式匹配，**少写一个分支直接编译失败**。收益不在类型优雅，而在于「业务拒绝」与「网络不可达」终于可区分 —— 前者重推一万次也不会成功、MUST 一次即终态 + 开工单，后者才进补偿队列。

**现状（2026-10-15 核实）**
- `PaySignClient.updatePaySignDisplayAccount` 确已 `@Deprecated`，实现体只有 `updateDisplayAccountOutcome(...).isOk()` 一行、**零解析逻辑**；唯一真实调用方已迁到 `updateDisplayAccountOutcome`。它**不是**违规活例子。
- ⚠️ **仍有 2 个未 `RpcOutcome` 化的 boolean 包装**：`AlipayAccountClient.updatePaymentChannel`（已由 alipay-pay-sign-server 的 `AccountChannelRpcAdapter` 包成三态，ADR-D131）与 `AlipayAccountClient.updatePhone`（**仍被直接消费 boolean**）。`ProxyWebClient.getAndGetResponse` 抛的 `RuntimeException` 会穿透。扫现状 MUST 用 `rg "public boolean [a-zA-Z]" --glob '*Client.java'`，**NEVER 认定只剩一个**。
- ⚠️ **同名类陷阱**：通道清理的实现类叫 `ChannelSyncDeliverer`，但 `pay-sign-server`（写/清账户侧通道）与 `alipay-pay-sign-server`（方向相反）**各有一个同名类**，`rg` 会同时命中。看到同名类 **NEVER 假设是同一个类**，必须连模块路径一起看。

## 4. `@Transactional` 方法内 NEVER 提交异步通知任务

事务未提交时发出的通知，若随后回滚会造成「对方已收到成功通知、本地状态已回退」。**MUST** 用 `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = false)`（首个落地样例：`pay-sign-server` 的 `SignResultCommittedEvent` + `SignResultCommittedListener`）、`TransactionSynchronizationManager.registerSynchronization(...)` 的 `afterCommit`，或把通知移出事务方法。

**Spring Event 在本项目只准用于这一件事**：一个事件类只挂一个监听器、两者同包成对命名、事件只带主键 + 入向 DTO（**NEVER 带实体**，事务内的实体可能是脏快照，监听器 MUST 自己按键回查）；**NEVER** 拿它做解耦或跨模块通信（服务间只有 `rpc` 的 HTTP 直连，没有事件总线），**NEVER** 拿它替代「落库状态 + 扫表补偿」——事件是**进程内非持久**的，JVM 崩溃即丢，它只能当已落库任务的**快速路径**。监听器 **NEVER 加 `@Async`**（异常会彻底无声，且默认执行器在虚拟线程下还有 pin 风险），内部 **MUST** `catch` 全部异常只记日志：`AFTER_COMMIT` 里抛异常回滚不了已提交的事务，只会让本已成功的回调对上游报错、引来重推。

**现状（2026-10-15 核实）**：全仓 `@TransactionalEventListener` **仅 1 处**、事件类型仅 1 个（`SignResultCommittedEvent`），发布点唯一（`SignResultCallbackHandler.receiveSignResult`），监听器无 `@Async`。可靠性仍靠 `NOTIFY_STATUS='PENDING'` + 扫表补偿兜底。**该约束已被严守。**（小瑕疵：事件带的 `ReceiveSignResultReqDTO` 是可变 POJO，约束的实质「不带实体、按键回查」成立，但别把「不可变」当代码事实。）

## 5. `fallbackExecution = false` 在不带事务的类上等于「静默不执行」

编译与单测都发现不了。因此**据文档挑「事务内通知」的改造点前 MUST 先 grep 目标类有没有 `@Transactional`**。

- `face-pay-server` 的 `F2fTicketIssueService` / `F2fAppOrderService` **有意不带事务**（链路里有支付中心调用），在那里挂监听器会把通知直接丢掉；改成 `fallbackExecution = true` 更糟，等于「无事务时立刻同步发 HTTP」，退回该模块重写前的旧行为。详见 `docs/domain/decisions.md` 撤回 7。
- 现状：`face-pay-server` **整批服务类**（`F2fAppOrderService` / `F2fAppRefundService` / `F2fTakeTicketService` / `F2fPayCenterFlow` 等约 20 个）都是有意不带事务，类注释自带「NEVER 加」。唯一带真实 `@Transactional` 的是 `SupplementOrderLocalWriter`（纯本地写入，无远程调用，合规）。MUST 现查，NEVER 引用清单里的具体数字。

## 6. `@Transactional` 方法内 NEVER 发起任何 RPC / 网络调用

与第 4 条不是同一件事：第 4 条约束通知的**时机**，本条约束网络调用**不能被事务包住**。事务内调远端 ⇒ 行级排他锁的持有时长等于对端响应时长，上游对同一笔的重推会全部堆在同一行上串行等待；等待超过 Druid `remove-abandoned-timeout` 后连接被强杀，`commit` 抛 `connection closed`，**整个事务连同「留证据」的 INSERT 一起丢弃**，响应退化成 UUID `retCode`，上游继续重推——自我放大且没有出口。**MUST** 用 `afterCommit` 触发，或让该方法不带事务（每条 SQL 自动提交）。

已发生事故：2026-08-26 生产，`PaymentDomainServiceImpl.receivePayResult`（原宿主 `PaySignWorkflow.receivePayResult`，2026-09-15 随 ADR-D87 迁入）事务内调 `syncDebitStatus`，订单循环重推 8 分钟、单次请求 287 秒、`enq: TX - row lock contention` 持续，**循环期间 `PAY_CALLBACK_LOG` 零条落库**（每轮都被回滚），说明「回滚不丢证据」的前提在事务内并不成立。**该方法事务注解自 ADR-D48 摘掉后一直没有、且是刻意的，NEVER 加回。**

**现状（2026-10-15 全仓扫描 104 个 `@Transactional` 方法）：仍有 5 处在线违例**，均带活跃端点、非死代码——
- `collect-pay-server`：`AppOrderServiceImpl.requestPayInfo` / `TvmOrderServiceImpl.requestTvmPayOrder` / `BomOrderServiceImpl.requestPayment`（经 `payCenterService.callPayCenter` 调外部支付中心 HTTP）、`BomOrderServiceImpl.notiUpdateHceData`（经 `accountClient.updateHceData`）。
- `collect-ticket-server`：`TicketCollectServiceImpl.requestPaymentInfo`（经 `collectPayClient`，事务持锁跨越支付中心下单全程，风险最高）。

`pay-sign-server` / `face-pay-server` / `ticket-server` 已清零。这 5 处 MUST 按上述规则改造（`afterCommit` 或摘掉事务）。