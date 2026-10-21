# gate-txn-pay-server 定时任务

> 对应 sys_job 6 条 + 1 处 @Scheduled（未使用）。该类任务**刻意不带模块内 `@Scheduled`**，由 web-admin `sys_job` 统一驱动。详见 `OfflineFareRecoveryProcessor` 类注释对同模式的说明。

---

## sys_job 220 行程扣费重试（非支付宝）

- **cron**: `0 0 1 * * ?`
- **触发目标**: `gateTxnPayQuartzTask.retryDefaultChannelDebits()`
- **执行方**: `POST /internal/gate-txn-pay/debit/retry/default`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 133 改为 220 并去掉「(非支付宝)」后缀，**NEVER 回退成 133**
- **实现要点**:
  - 甲方需求 5
  - 只捞 `ISSUE_CHANNEL_CODE IS NULL 或 <> '07'` 的非支付宝渠道单（**NEVER 写成 `= '01'`**，该列可空且历史行有空值）
  - 扫 `GATE_TXN_PAY` 的 `DEBIT_STATUS IN (RETRY, FAIL)` + `DEBIT_RETRY_TIMES < maxTimes` + 退避时间已到
  - 三道闸：`maxTimes=5` / `lookbackDays=7` / `backoffMinutes=720`（与 255 共用参数）
  - 出口走 `PaySignInitiator.retryAndConverge(GateTxnPay)` 打支付中心
  - 抢占语句 `prepareBatchRetry` 是**一条 UPDATE 同时做三件事**：CAS 抢占 + 次数记账 + 把终态 `FAIL` 归一成 `RETRY`（第三件必需，否则 `updateOrderStatusFromPending` CAS 白名单只认 `INIT/RETRY`，`FAIL` 那批的结果回写恒 0 行）
  - **NEVER 把归一那段删掉**
- **配置键**:
  - `gate.debitRetry.maxTimes: 5` —— 重试次数上限，达到即不再被捞出
  - `gate.debitRetry.lookbackDays: 7` —— 回溯天数窗口
  - `gate.debitRetry.backoffMinutes: 720` —— 退避时长（默认 12 小时）
  - `gate.debitRetry.batchSize` —— 单轮取行数（未提供默认值，需外部配置）
- **关键约束**:
  - 本模块刻意不带 `@Scheduled`，由 web-admin `sys_job` 统一驱动；NEVER 在本模块内加
  - `DebitRetryProcessor` 无 `@Transactional`：出网 NEVER 被事务包住，每条回写各自自动提交
  - 抢占语句必须 CAS `FAIL → RETRY`：删掉会导致 `updateOrderStatusFromPending` 回写恒 0 行，资损方向
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=220`

---

## sys_job 255 支付宝出行重试扣费

- **cron**: `0 0 1 * * ?`（与 220 同时刻）
- **触发目标**: `gateTxnPayQuartzTask.retryAlipayChannelDebits()`
- **执行方**: `POST /internal/gate-txn-pay/debit/retry/alipay`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 134 改为 255（2026-09-20 新增，原需求 5+18 同日落地）
- **实现要点**:
  - 甲方需求 5 + 需求 18
  - 只捞 `ISSUE_CHANNEL_CODE = '07'` 的支付宝出行单
  - 与 220 **共用三闸参数**（maxTimes / lookbackDays / backoffMinutes），只有 `enabled` 与 `batchSize` 各自一套
  - 两条各有独立 `AtomicBoolean` 互不阻塞
  - **拆分实质理由是故障隔离**：混在一条任务里时一侧渠道整体故障会拖慢另一侧
- **配置键**:
  - `gate.debitRetry.enabled` / `gate.debitRetry.alipay-enabled` —— 各渠道独立开关（未显式列出，见 DebitRetryProcessor 源码）
  - `gate.debitRetry.maxTimes: 5` / `lookbackDays: 7` / `backoffMinutes: 720` —— 与 220 共用
- **关键约束**:
  - 与 sys_job 220 **并行、NEVER 合并** —— 合并不仅丧失故障隔离，还会共用同一 AtomicBoolean，一侧 busy 会阻塞另一侧
  - 本模块刻意不带 `@Scheduled`，由 web-admin `sys_job` 统一驱动；NEVER 在本模块内加
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=255`

---

## sys_job 295 离线码金额补偿

- **cron**: `0 0/1 * * * ?`
- **触发目标**: `gateTxnPayQuartzTask.recoverOfflineFare()`
- **执行方**: `POST /internal/gate-txn-pay/offline-fare/recover`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 120 改为 295
- **实现要点**:
  - 筛「金额待补齐」的单，NEVER 当成通用扣费失败重试
  - 原为模块内 `@Scheduled`，已迁出
  - `OfflineFareRecoveryProcessor` 类注释明确：**NEVER 加回 `@Scheduled`**
- **配置键**:
  - `gate.pay.offline-fare-recovery-enabled: true` —— 总开关，`false` 时本轮直接返回 -1 跳过
  - `gate.pay.offline-fare-recovery-batch-size: 50` —— 单轮取行数
  - `gate.pay.offline-fare-recovery-lookback-days: 7` —— 回溯天数窗口
- **关键约束**:
  - ADR-D80：已从 `@Scheduled` 迁出，**NEVER 加回 `@Scheduled`**
  - 返回值 `-1`（开关关闭）与 `-2`（异常）≠ 业务推进数（≥0 才是真正推进的行数），调用方 MUST 按返回值区分
  - 本类无 `@Transactional`，单轮异常只跳过本轮，不影响库内已推进的行
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=295`

---

## sys_job 300 公交换乘推送

- **cron**: `0 0/1 * * * ?`
- **触发目标**: `gateTxnPayQuartzTask.pushMetroTransfer()`
- **执行方**: `POST /internal/gate-txn-pay/metro-transfer/push`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 121 改为 300
- **实现要点**: 原为模块内 10 秒轮询，已迁出
- **配置键**: 同 `OfflineFareRecoveryProcessor`（均通过 `gateTxnPayQuartzTask` 暴露，共享 gate-txn-pay-server 配置域）
- **关键约束**:
  - ADR-D80：已从模块内 `@Scheduled` 迁出，**NEVER 加回 `@Scheduled`**
  - `MetroTransferPushTaskProcessor` 无 `@Transactional`，出网 NEVER 被事务包住

---

## sys_job 345 补站扣费周期查询更新

- **cron**: `0 0/1 * * * ?`
- **触发目标**: `gateTxnPayQuartzTask.retryRecentUnpaidDebits()`
- **执行方**: `POST /internal/gate-txn-pay/debit/retry/recent`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 新增（ADR-D154）；编号先后为 135→265→235→345，**NEVER 回退成 135 / 265 / 235**
- **实现要点**:
  - 扫近 `windowMinutes=10` 分钟内 `DEBIT_STATUS IN (INIT, RETRY, FAIL)` 且落库超 `minAgeSeconds=60` 秒的单
  - 不分渠道、**不记账**（刻意不写 `DEBIT_RETRY_TIMES` / `DEBIT_NEXT_RETRY_TIME`，否则烧光 220/255 的次数预算）
  - 排除 `DISCOUNT_CALC_STATUS=OFFLINE_FARE_PENDING`
  - 与 220 / 255 **并行、NEVER 合并**
  - **甲方需求 5 衍生任务**，不是需求 13「补站扣费周期查询更新」（那是 ticket-server 侧的缺口）
- **配置键**:
  - `gate.debitRetry.recent.windowMinutes: 10` —— 扫描窗口（左右都闭），出窗交给 220/255 按 720 分钟退避慢跑
  - `gate.debitRetry.recent.minAgeSeconds: 60` —— 下界，躲开正在走 RPC 的单；上界由 windowMinutes 控制
  - `gate.debitRetry.recent.batchSize` —— 单轮取行数（无默认值，需外部配置）
- **关键约束**:
  - 本任务**只读不写** `DEBIT_RETRY_TIMES`：写就烧掉 220/255 的次数预算，两条日跑任务的闸会提前失效
  - 真正的防线是两道：**`minAgeSeconds` 下界（躲正在走 RPC 的单）+ `windowMinutes` 上界（出窗即交给 220/255）**；删任何一道都是资损方向
  - 与 220/255 **并行、NEVER 合并** —— 合并后故障域重叠，一侧 channel 故障会阻塞另一侧
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=345`

---

## sys_job 400 用户主动重试扣费队列消费

- **cron**: `0 0/1 * * * ?`
- **触发目标**: `gateTxnPayQuartzTask.consumeRetryQueue()`
- **执行方**: `POST /internal/gate-txn-pay/retry-queue/consume`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-10 新增（ADR-D169 方案 A），原 CSV 漏项，本次合并补登
- **实现要点**:
  - 消费用户主动重试扣费队列（非批量扫描，是用户触发的队列）
  - 与 220/255 并行、NEVER 合并
- **配置键**:
  - `gate.debitRetry.queue.batchSize: 10` —— 每轮最多消费笔数
  - `gate.debitRetry.queue.maxRetries: 3` —— 失败订单最大重试次数，之后标记 FAILED 等人工介入
  - `gate.debitRetry.queue.timeoutMinutes: 1440` —— PENDING/PROCESSING 超时恢复阈值（默认 24 小时）
- **关键约束**:
  - `RetryQueueConsumer` Javadoc 明确：**本类不带 `@Scheduled`（由 web-admin Quartz 统一管理定时任务）**
  - 消费时用 `SELECT ... FOR UPDATE SKIP LOCKED` 抢占行，防止多实例重复消费
  - 与本模块其他 sys_job（220/255/345）并行不替代：本任务只处理用户主动触发的重试，220/255 处理系统级批量重试，345 处理近 10 分钟快速轮询

---

## 判是否在跑（通用）

同 pay-sign-server：**MUST 查 `SYS_JOB_LOG`**，NEVER 在该模块里找 `@Scheduled`（该模块刻意不带）。
