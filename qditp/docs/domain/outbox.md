# Outbox 模板（落库状态 + 扫表补偿）

本项目不使用消息队列（AGENTS.md §5.1），所有「本地已提交、还要告诉别人」的场景一律是
**同一张业务表上加四列状态 + 外部定时任务扫表重推**。`USER_PHONE_CHANGE_LOG.SIGN_SYNC_*` 是
第一处落地（ADR-D13 / ADR-D45），ADR-D8 第一处要补的 `APP_TERMINATION_REQUEST.CHANNEL_SYNC_*`
是第二处。本文把这套做法固化成模板。

**NEVER 抽一张公共 outbox 表。** 那张表会同时被账户域和支付域写，直接违反 `README.md` 判据 3
（热路径写入决定 owner）：一张表两个写入方，拆库时无处安放。模板复用的是**列的形状、SQL 的写法、
Java 侧的扫描骨架**，不是存储。

## 一、四列形状

给需要投递的那张业务表加四列，前缀按投递目标命名（`SIGN_SYNC_` / `CHANNEL_SYNC_`）：

- `*_STATUS VARCHAR2(32 CHAR)` — `PENDING` / `SUCCESS` / `FAILED`，复用 `model.domain.SyncStatus`。
  **`NULL` 表示该行早于本功能上线**，MUST 扫不到（见下面的 SQL 陷阱）。
- `*_RETRY_COUNT NUMBER DEFAULT 0` — 每重推一次 +1；**达到上限即终态**，这是本模板表达终态的唯一手段。
- `*_TIME TIMESTAMP(6)` — 最近一次尝试的时间，仅供排查。
- `*_RESULT VARCHAR2(1024 CHAR)` — 最近一次的 retCode/retMsg 或异常摘要，**MUST 截断到列长**
  （样板：`PhoneChangeServiceImpl.truncateSyncResult`）。

**NEVER 为「业务拒绝」新增状态值**，也 **NEVER 新增工单类型**：状态取值散落在 Java 常量、
mapper 的两处白名单与运营查询里，多一个值就要全局 grep 一遍；而「业务拒绝」与「重试耗尽」对运维
是同一个动作（人工核对后订正并关单），区别写进 `*_RESULT` 就够。理由全文见 `decisions.md` ADR-D45。

## 二、四条 SQL 与它们的陷阱

以 `UserPhoneChangeLogMapper.xml` 为样板，四条语句一组：

1. **`markXxxSuccess`** / 2. **`markXxxFailed`**（次数 +1）/ 3. **`markXxxRejected`**（次数直接置上限）
   / 4. **`selectPendingXxx`**（扫表）。

前三条都 **MUST 带 CAS 白名单** `AND *_STATUS IN ('PENDING','FAILED')`，并 **MUST 接住影响行数** ——
0 行意味着这行已被别人收口，只能记日志、NEVER 当成功。

扫表语句的四个坑，逐条都踩过或差点踩：

- **`ROWNUM` MUST 在排序子查询之外**。写成 `WHERE ROWNUM <= n ORDER BY ID` 是先截断再排序，
  取到的是随机 n 行，积压时永远重推同一批。
- **`NVL(*_RETRY_COUNT, 0)` 两处都要写**：`SELECT` 列表里要，`WHERE` 的比较里也要。
  Oracle 中 `NULL < 10` 不成立，漏掉 `NVL` 会让 `DEFAULT 0` 之前插入的行永远扫不到。
- **`*_STATUS IS NULL` 的历史行 MUST 扫不到**。白名单写 `IN ('PENDING','FAILED')` 天然满足；
  **NEVER 改成「非 SUCCESS 即扫」** —— 那会把功能上线前的全部历史行一次性推给对端。
- **`FAILED` MUST 仍在扫描白名单里**。`FAILED` 在本模板里不是终态，终态是「次数达上限」；
  把 `FAILED` 移出白名单等于第一次失败即放弃。

**SQL 正文里 NEVER 写注释**（Druid WallFilter 会判定为注入并静默失效），说明写进 XML 的
`<!-- -->`；而 XML 注释里 **NEVER 出现两个连续减号**（MyBatis 解析失败 ⇒ 服务启动即挂）。
改完 MUST 跑 `xmllint --noout <file>`。两条都是 AGENTS.md §5.1 的既有事故。

## 三、Java 侧扫描骨架

扫描循环用 `model/src/main/java/com/chinasofti/huateng/model/domain/OutboxScan.java`，
**NEVER 再手写一遍 for + try/catch**：

```java
OutboxScan.Result scan = OutboxScan.run(pending,
        row -> syncDisplayAccountToPayDomain(row.getId(), row.getThirdUserId(), row.getNewMsisdn()),
        this::openTicketIfRetryExhausted,
        (row, e) -> log.error("单条异常，NEVER 因此中断整批, id={}", row.getId(), e));
```

它固化三条不变量（护栏：`account-server` 的 `OutboxScanTest`）：

- **单条失败 NEVER 中断整批** —— 一行的异常不影响后续行。
- **每行只计一次** —— `onFailure` 自身再抛异常时 `failed` 也只加 1，
  因此扫表日志里 `success + failed` 恒等于 `scanned`。这是设计期修掉的真实缺陷。
- **兜住 `deliver` 与 `onFailure` 两者的异常** —— 「落状态自身失败」只记 ERROR，不上抛。

`OutboxScan` 是纯函数式骨架，**NEVER 往里塞 Spring / MyBatis 依赖**：它住在 `model`，
被 21 个模块引用。

## 四、投递结果的三分支处置

投递方法 MUST 返回 `RpcOutcome`（ADR-D45），三个分支的处置**互不相同、NEVER 合并**：

- `Ok` → `markXxxSuccess`。
- `BizRejected` → `markXxxRejected`（次数置上限）+ **当轮立即开工单**。该行此后扫不到，
  只落状态不开单等于让它彻底失联。
- `Unreachable` → `markXxxFailed`（次数 +1），留给下一轮补偿；达上限时由 `onFailure` 开工单。

工单走 `ACCOUNT_EXCEPTION_TICKET`，`TICKET_TYPE + BIZ_KEY` 是唯一键，重复开单靠
`DuplicateKeyException` 兜底。

## 五、调度

**调度 MUST 在 web-admin 的 `sys_job`，业务模块内 NEVER 加 `@Scheduled`**（用户 2026-09-11 要求；
`pay-sign-server` 与 `recon-server` 现在一个都没有）。新增任务先读
`docs/architecture/web-server.md` §七，并记住：运行中直接 INSERT `sys_job` 不生效，
必须重启 web-admin 或在后台界面对该任务做一次修改保存。

触发端点的路径**目前两套并存**，新增时对齐所在模块的既有那套、**NEVER 混用**：
`pay-sign-server` 的 7 个补偿端点在 `/internal/**`，account-server 的这一个是
`TaskController` 上的 `POST /phoneSignSyncCompensate`（该类没有类级 `@RequestMapping`）。
两套都**尚无鉴权**，与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权」冲突，**上线前 MUST 补**。

排查「补偿到底有没有跑」**MUST 查 `SYS_JOB_LOG`**，`QRTZ_*` 表恒为 0 行（内存 JobStore）。

## 六、当前落地清单

- `USER_PHONE_CHANGE_LOG.SIGN_SYNC_*` — 已落地，四列在 `AFCITPDB` 已在（2026-09-11 实测），
  `sys_job` job 108 触发 `POST /phoneSignSyncCompensate`。
- `APP_TERMINATION_REQUEST.CHANNEL_SYNC_*` — **代码已落地（2026-09-12 / 2.0.80，ADR-D48）**：
  `ChannelSyncDeliverer` 是投递与三分支落状态的唯一实现，快速路径（`CallbackDomainServiceImpl.receiveTerminationResult`
  解约成功收口后，2026-09-15 前宿主是已删除的 `PaySignWorkflow`，见 ADR-D87）
  与补偿扫表（`POST /internal/termination/compensateChannelSync`）共用它。
  **DDL 是否已在 `AFCITPDB` 尚未核实**（列不存在时解约成功收口会整体报 `ORA-00904`），
  `sys_job` 也还没插——**当前补偿端点没有任何触发方**。
  本文此前记载的「DDL 未执行、代码未落地」中的后半句已作废，**NEVER 回退**。
  改动时 MUST 按本文模板，**NEVER 另起一套列名或状态值**。
- `SUPPLEMENT_ORDER.SALE_SYNC_*` — **已落地并已部署（2026-09-14，gate-txn-pay-server 2.0.66，ADR-D64）**：
  投递目标是销售域（collect-pay 的 `POST /internal/app-order/register`），所以前缀是 `SALE_SYNC_`。
  四列 + 索引 `IDX_SUPPLEMENT_ORDER_SALESYNC` **已在 `AFCITPDB` 执行并用
  `USER_TAB_COLS` / `USER_INDEXES` 回查通过**；脚本
  `gate-txn-pay-server/src/main/resources/sql/supplement-order-sale-sync-migration.sql`。
  实现是 `SupplementOrderServiceImpl.deliverSaleSync`（三分支）+ `syncPendingSaleOrders`（走
  `OutboxScan`），`insert` **硬编码** `SALE_SYNC_STATUS='PENDING'`。
  **两处与本文其余条目不同、都是有意为之**：
  ① **调度在本模块的 `@Scheduled`（`SupplementOrderCloseProcessor`）而不是 web-admin**
  —— 该模块已有 3 个在跑的 `@Scheduled`，补款链路的三段（补入口 / 收敛已付 / 关超时）
  不应横跨两个调度器；**要搬 MUST 三段一起搬**，NEVER 只把新这段挪走。
  ② **落本地在调远端之前**，与 AGENTS.md §5.2「先调远端后改本地」通则相反 ——
  反过来写会在 collect-pay 留下可支付的孤儿订单（资损方向），论证见 ADR-D64。
  **补偿未闭环**：支付域没有工单表，达重试上限只落 ERROR + 写 `SALE_SYNC_RESULT`，
  **运维 MUST 配告警规则**；**NEVER 跨域写 account-server 的 `ACCOUNT_EXCEPTION_TICKET`**。


## 七、已设计但搁置的两处（2026-09-14 用户裁决搁置）

行程域（ticket-server）有两条「失败只打日志、无任何补偿」的链路，方案已按本文模板设计完毕，
**用户 2026-09-14 明确要求搁置、本轮不实施，代码一行未改**。重新捡起来时 MUST 先读本节，
**NEVER 从零重新设计**，也 NEVER 直接开工——三个共同待裁决点还没定（见末尾）。

**① 反向推码无失败补偿**（`AppNotifyServiceImpl.doNotifyIndustryData`，`docs/business/ride-code.md` 有对应条目）

- 载体表 `QRCODE_TXN_DETAIL`，四列前缀 `CODE_PUSH_`。该表有代理主键 `ID`（`PK_QRCODE_TXN_DETAIL`），
  回写按 `ID`，**不需要拼 `UK_QRCODE_TXN_DETAIL_BIZ` 那 6 列**。
- `doNotifyIndustryData` 现返回 boolean，但内部已能区分三种失败（HTTP 非 2xx / `retCode != 0000` / IO 异常），
  MUST 改成返回 `RpcOutcome` 后再接本文 §四 的三分支。
- **本链路特有、pay-sign 模板里没有的一条**：补偿重推 MUST 重新调 `industry-data-server` 生码，
  而入参的 `ticketStatus` / `txnSeq` / `gateInStation` 全取自 `QRCODE_STATUS` **当前值**，
  因此推出去的是「补偿执行那一刻的最新码」、不是失败那一刻的快照。由此得出收口规则：
  **`detail.TICKET_TRANS_SEQ < QRCODE_STATUS.TXN_SEQ` 时该行 MUST 直接标 SUCCESS 短路**
  —— 说明乘客后来又过闸并成功推过一次，这行已被自然覆盖，再推等于把同一张最新码重复推一遍。
- 补偿重建入参需要 `companionFlag` 与 account `CHANNEL`，ticket-server 已注入 `AccountClient` /
  `AlipayAccountClient`（`GateTicketHandler:79,83`），有现成通路。

**② 日票扣次失败无补偿**（`GateTicketHandler:217~238` 的 `markUsed` 分支）

- 载体表同为 `QRCODE_TXN_DETAIL`，四列前缀 `MARK_USED_`；只有日票出站行（`TRX_TYPE='02'` +
  `CARD_TYPE` ∈ `0445~0448`）有值，其余行恒 `NULL`、天然扫不到。
- 比 ① 简单的地方：重推入参（`cardId` / `inStation` = `LAST_HANDLE_STATION_CODE` /
  `outStation` = `HANDLE_STATION_CODE`）**在明细行里已全部落库，原样取值即可**，不存在快照过期问题。
- **比 ① 危险的地方，也是这条链路的真正阻塞点**：当前 `markUsed` 传 `orderNo=null`
  （`GateTicketHandler:223`，因为 `GATE_TXN_PAY.ORDER_NO` 由 gate-txn-pay-server 生成、ticket-server 拿不到），
  而 daily-ticket-server 的 `UK_DTUL_ORDER` 在 `null` 上**不拦截**（Oracle 允许多个 null）。
  于是**重试会重复扣次 —— 反向资损，比不补偿更糟**。因此 **MUST 先补确定性 `orderNo`**
  （形如 `"DTMU_" + QRCODE_TXN_DETAIL.ID`）让对端唯一键挡住重复，**NEVER 先上扫表再想幂等**。
  连带收益：快速路径也传同一个值后，AGM 超时重发导致同一笔出站被调两次时也不会重复扣次。
  动手前 MUST 先查清「库内 `orderNo IS NULL` 的历史行有多少、改成非 null 对 daily-ticket-server 有无副作用」。
- **一条必须先闭合的历史冲突**：`GateTicketHandler:215` 的注释原文记载「跨服务补偿的形态还没定
  （**用户已否决扫表/定时任务**）」，而本文 §五 与 AGENTS.md §5.1 要求「异步补偿统一为落库状态 + 扫表重试」。
  2026-09-14 用户提出要做「`markUsed` 落库 + 扫表重试」，**等于推翻那条旧裁决**，但随后又要求整体搁置。
  因此那条注释**原样保留、未改**。重新开工时 MUST 先向用户确认「扫表方案是否已解禁」，
  **NEVER 只看本文 §五 就动手**，也 NEVER 因为看到那条注释就断定扫表被永久否决。

**三个共同待裁决点（两条链路一起定，NEVER 分别定成不一样的）**

1. **工单无处可开**：本文 §四 要求 `BizRejected` 与「重试耗尽」都开工单，但
   `ACCOUNT_EXCEPTION_TICKET` 是 account-server 独占表（`state-machines.md` §四明确「其它域 NEVER 直接写」），
   行程域没有工单表。选项：(a) 耗尽只打 ERROR + 写 `*_RESULT`；(b) 行程域自建同构
   `JOURNEY_EXCEPTION_TICKET`。**(a) 意味着补偿并未真正闭环，选它 MUST 在文档里写明这个缺口。**
2. **调度落点**：照本文 §五 走 web-admin `sys_job` 要同批动 3 个模块（ticket-server 加
   `/internal/**` 端点、`rpc` 的 `TicketClient` 加方法、web-admin 加任务 Bean + `@EnableRpcTicket` +
   插 `sys_job` 并重启）。选项：(a) 严格照模板；(b) 本轮只做端点与扫表逻辑、`sys_job` 留部署时插
   —— 选 (b) 的后果是**代码完备但没有任何触发方**，与 `APP_TERMINATION_REQUEST.CHANNEL_SYNC_*` 现状同型。
3. **DDL 落在热表上**：两组四列都 `ALTER TABLE QRCODE_TXN_DETAIL ADD`，该表是过闸热路径写入表。
   `ADD` 不重写数据、风险低，但 MUST 出独立 `ticket-server-{code-push,mark-used}-migration.sql`
   并当场在 `AFCITPDB` 执行 + `USER_TAB_COLS` 回查（AGENTS.md §8「只改 schema.sql 等于没加」）。

