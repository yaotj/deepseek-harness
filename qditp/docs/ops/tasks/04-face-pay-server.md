# face-pay-server 定时任务

> 对应 sys_job 3 条 + 模块内 @Scheduled 7 处。face-pay-server 的 @Scheduled 任务**无分布式锁，MUST 单副本**。

---

## sys_job 200 单程票未取票批量退款（甲方需求 1）

- **cron**: `0 0 20 * * ?`
- **触发目标**: `f2fBatchRefundQuartzTask.refundSingleTicket()`
- **执行方**: `POST /internal/f2f/batch-refund/single-ticket`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 130 改为 200
- **实现要点**:
  - 扫 `F2F_ORDER` 的 `BIZ_TYPE=01` + `ORDER_STATUS=PAID` + `PAID_TMS` 窗口（silenceMinutes 默认 60 / lookbackDays 默认 7）
  - 逐笔调 `F2fRefundService` 落 `REFUND_SOURCE=DAILY_BATCH`
  - **三条口径 NEVER 改**：①三类共用 `REFUND_SOURCE='DAILY_BATCH'` 是安全的（幂等由 `UK_F2F_REFUND_IDEM` 保证，且一笔订单只属一个 `BIZ_TYPE`）；②`refundBatch` **刻意不带 `@Transactional`**（逐笔独立、一笔失败不拖累整批）；③端点各有一个 `AtomicBoolean`，busy 返 `9998`，Quartz 侧只打 WARN 不抛
- **配置键**:
  - `f2f.batchRefund.limit: 200` —— 单批取行数上限
  - `f2f.batchRefund.silenceMinutes: 60` —— 已支付订单静默期（毫秒内不再扫描），避开正常退款申请时间窗
  - `f2f.batchRefund.lookbackDays: 1` —— 回溯天数（默认 1 天，可按需调整）
- **关键约束**:
  - `F2fBatchRefundService.refundBatch()` **刻意不带 `@Transactional`**：逐笔独立，一笔失败不拖累整批
  - 三个端点（200/205/210）各有一个独立 `AtomicBoolean`，busy 返 `CODE_BUSY=9998`，Quartz 侧只打 WARN 不抛异常
  - 三类 BIZ_TYPE（01/02/04）共用 `REFUND_SOURCE='DAILY_BATCH'` 是安全的，幂等由 `UK_F2F_REFUND_IDEM` 保证，NEVER 为区分而拆不同 REFUND_SOURCE
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=200`

---

## sys_job 205 充值未取票批量退款（甲方需求 2）

- **cron**: `0 0 20 * * ?`（与 200 同时刻）
- **触发目标**: `f2fBatchRefundQuartzTask.refundTopup()`
- **执行方**: `POST /internal/f2f/batch-refund/topup`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 131 改为 205
- **实现要点**: 同 job 200 但 `BIZ_TYPE=02`；甲方指定执行时间与 200 相同，两者由各自 `AtomicBoolean` 独立防重入
- **配置键**: 与 job 200 共用 `f2f.batchRefund.*` 三键（limit/silenceMinutes/lookbackDays）
- **关键约束**:
  - 与 job 200 **并行同刻触发**，靠各自 `AtomicBoolean` 独立防重入（`singleTicketRunning` vs `topupRunning`），NEVER 共用同一 guard
  - `f2f.batchRefund.lookbackDays` 默认 1 天：只回溯最近 1 天的 PAID 单，更早的已由月度批次覆盖或已过人工核对窗口

---

## sys_job 210 非现金购票未履约批量退款（甲方需求 3）

- **cron**: `0 0 9,15,21 * * ?`
- **触发目标**: `f2fBatchRefundQuartzTask.refundNoCash()`
- **执行方**: `POST /internal/f2f/batch-refund/no-cash`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 132 改为 210
- **实现要点**: 同 job 200 但 `BIZ_TYPE=04`；一天三次为甲方指定
- **配置键**: 与 job 200 共用 `f2f.batchRefund.*` 三键
- **关键约束**:
  - cron `0 0 9,15,21 * * ?` 为甲方指定，NEVER 改成更多/更少次数（影响对账节奏）
  - 与 job 200/205 的 `AtomicBoolean` 各自独立，NEVER 共用

---

## @Scheduled 当面付通知投递

- **触发目标**: `F2fNotifyJob.deliverDueTasks()`
- **调度表达式**: fixedDelay `${f2f.notify.scanIntervalMs:30000}` / initialDelay 20000
- **状态**: 随模块启动生效 / 无分布式锁 / **MUST 单副本**
- **实现要点**: 出向通知投递，处理待投递的 F2F 通知
- **配置键**:
  - `f2f.notify.scanIntervalMs: 30000` —— fixedDelay，两轮间隔 30 秒
  - `f2f.notify.scanInitialDelayMs: 20000` —— 模块启动后首次延迟 20 秒
  - `f2f.notify.scanLimit: 100` —— 单轮最多处理 100 条
  - `f2f.notify.app.*-url` —— APP 通知出网地址（必须配置，否则通知打空 URL）
- **关键约束**:
  - 本类 `F2fNotifyJob` 是 face-pay-server 内 7 个 `@Scheduled` 之一，**MUST 单副本**（无分布式锁）
  - fixedDelay 语义：上轮结束到本轮开始的间隔；若单轮耗时 > intervalMs，则连续串行执行不叠加
  - 出向通知投递，**NEVER 当成「收到通知后回复」**——发起方是本模块，接收方是 APP

---

## @Scheduled 当面付退款回查

- **触发目标**: `F2fRefundReconcileJob.reconcileRefunds()`
- **调度表达式**: fixedDelay `${f2f.refund.scanIntervalMs:60000}` / initialDelay 30000
- **状态**: 随模块启动生效 / 无分布式锁 / **MUST 单副本**
- **实现要点**: 只回查已存在的 `F2F_REFUND` 行，**NEVER 新建退款单**
- **配置键**:
  - `f2f.refund.scanIntervalMs: 60000` —— fixedDelay，60 秒一轮
  - `f2f.refund.scanInitialDelayMs: 30000` —— 启动后首次延迟 30 秒
  - `f2f.refund.scanLimit: 100` —— 单轮最多回查 100 条
- **关键约束**:
  - 本类 `F2fRefundReconcileJob` **MUST 单副本**，无分布式锁
  - **只回查、绝不新建**：如果扫到 `PROCESSING` 但支付中心查不到记录，视为正常（等待回调），NEVER 因此新建一笔
  - 本任务是退款回调快速路径的兜底，不可被取消

---

## @Scheduled 当面付订单过期收口

- **触发目标**: `F2fOrderExpireJob.reconcileExpiredOrders()`
- **调度表达式**: fixedDelay `${f2f.order.expireScanIntervalMs:30000}` / initialDelay 15000
- **状态**: 随模块启动生效 / 无分布式锁 / **MUST 单副本**
- **实现要点**: 只置 EXPIRED 或补 markPaid，不发起退款
- **配置键**:
  - `f2f.order.expireScanIntervalMs: 30000` —— fixedDelay，30 秒一轮
  - `f2f.order.expireScanInitialDelayMs: 15000` —— 启动后首次延迟 15 秒
  - `f2f.order.expireScanLimit: 200` —— 单轮最多扫描 200 条
  - `f2f.order.expireGiveUpHours: 24` —— 超过此时长仍未支付，直接置 EXPIRED（不再等）
  - `f2f.order.qrcodeExpireSeconds: 180` —— 二维码有效期 3 分钟（用于判定是否可 markPaid）
  - `f2f.order.bomSaleExpireSeconds: 1800` —— BOM 售票有效期 30 分钟
- **关键约束**:
  - 本类 `F2fOrderExpireJob` **MUST 单副本**，无分布式锁
  - 只处置「出站侧过期」的订单（置 EXPIRED 或补 markPaid），**绝不发起退款** —— 退款由 `F2fRefundReconcileJob` 处理
  - `expireGiveUpHours=24`：超过 24 小时未支付视为用户放弃，不再等待；调大只会让过期单堆积，调小会误杀仍在支付中的单

---

## @Scheduled 设备离线标记

- **触发目标**: `F2fDeviceOfflineJob.markTimeoutDevicesOffline()`
- **调度表达式**: fixedDelay `${f2f.device.offlineScanIntervalMs:60000}` / initialDelay 60000
- **状态**: 随模块启动生效 / 无分布式锁 / **MUST 单副本**
- **实现要点**: 在线态一律以落库 `ONLINE_FLAG` 为准
- **配置键**:
  - `f2f.device.offlineScanIntervalMs: 60000` —— fixedDelay，60 秒一轮
  - `f2f.device.offlineScanInitialDelayMs: 60000` —— 启动后首次延迟 60 秒
  - `f2f.device.offlineTimeoutSeconds: 300` —— 设备离线判定阈值（默认 5 分钟无心跳即算离线）
- **关键约束**:
  - 本类 `F2fDeviceOfflineJob` **MUST 单副本**，无分布式锁
  - 在线态判断**只读 `ONLINE_FLAG`**，NEVER 用心跳时间戳自己算 —— `ONLINE_FLAG` 是设备主动上报的最新状态，本地计算会因时钟漂移产生误判
  - `offlineTimeoutSeconds=300` 是经验值：短于 5 分钟会因网络抖动误报离线，长于 5 分钟会延迟发现真离线

---

## @Scheduled 出票上报补偿

- **触发目标**: `F2fReportRecoveryJob.resumeDueReports()`
- **调度表达式**: fixedDelay `${f2f.report.scanIntervalMs:120000}` / initialDelay 60000
- **状态**: 随模块启动生效 / 无分布式锁 / **MUST 单副本**
- **实现要点**: 恢复出票后续动作（超时未上报的补偿重推）
- **配置键**:
  - `f2f.report.scanIntervalMs: 120000` —— fixedDelay，120 秒一轮
  - `f2f.report.scanInitialDelayMs: 60000` —— 启动后首次延迟 60 秒
  - `f2f.report.scanLimit: 100` —— 单轮最多重推 100 条
  - `f2f.report.staleSeconds: 300` —— 超时阈值 5 分钟，超时就视为 stale 需要重推
- **关键约束**:
  - 本类 `F2fReportRecoveryJob` **MUST 单副本**，无分布式锁
  - 只重推「超时未上报」的出票记录，**NEVER 新建** —— 新建是出票流程的职责，不是本任务的
  - `staleSeconds=300`：短于 5 分钟可能误判正常 RPC 延迟，长于 5 分钟会延迟发现真丢失

---

## @Scheduled 补款单收敛

- **触发目标**: `SupplementOrderCloseProcessor.converge()`
- **调度表达式**: cron `${supplement.scheduler.converge-cron:0 */5 * * * ?}`
- **状态**: 随模块启动生效 / 无分布式锁 / **MUST 单副本**
- **实现要点**:
  - 类整体自 gate-txn-pay-server 迁入，**NEVER 在 gate-txn-pay-server 加回**
  - 处理「出站欠费在线**补款**」的 F2F 订单，与 ticket-server 的「补**站**」零交集
- **配置键**:
  - `supplement.scheduler.converge-cron: 0 */5 * * * ?` —— 收敛周期 5 分钟一轮
- **关键约束**:
  - 本类 `SupplementOrderCloseProcessor` 的 `converge()` 方法处理「出站欠费在线**补款**」，与 ticket-server 的「补**站**」（金额计算缺口）零交集，NEVER 合并理解
  - **NEVER 在 gate-txn-pay-server 加回本类** —— 已在 ADR 中决议迁到 face-pay-server
  - 本类 **MUST 单副本**，无分布式锁

---

## @Scheduled 补款单超时关闭

- **触发目标**: `SupplementOrderCloseProcessor.closeTimeout()`
- **调度表达式**: cron `${supplement.scheduler.close-cron:0 */10 * * * ?}`
- **状态**: 随模块启动生效 / 无分布式锁 / **MUST 单副本**
- **实现要点**: 语义是关单，**NEVER 当成「bom 补款退款」**
- **配置键**:
  - `supplement.scheduler.close-cron: 0 */10 * * * ?` —— 关单周期 10 分钟一轮
- **关键约束**:
  - 本类 `SupplementOrderCloseProcessor.closeTimeout()` 只关单（置终态），**NEVER 发起退款** —— 退款由专门的退款任务处理
  - 「补款单超时关闭」与「bom 补款退款」是两件事：前者是状态机流转，后者是资金回退，NEVER 合并
  - 本类 **MUST 单副本**，无分布式锁

---

## 与 collect-pay 旧表任务的覆盖关系

collect-pay-server 那 4 个旧表 `@Scheduled`（APP/TVM/BOM 售票 + BOM 充值退款）**原样保留、NEVER 停**，覆盖旧表存量；与新表 `F2F_*` 的覆盖面互不重叠。新表由 sys_job 200/205/210 覆盖。旧表存量按裁决人工清退。

---

## 判是否在跑（sys_job）

查 `SYS_JOB_LOG`，`JOB_ID ∈ {200, 205, 210}`。

## 判是否在跑（@Scheduled）

查模块日志，看 fixedDelay / cron 对应的执行记录。无 `SYS_JOB_LOG` 记录。
