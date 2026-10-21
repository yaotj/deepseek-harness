# blacklist-server 定时任务

> 对应 sys_job 4 条。该类任务**刻意不带模块内 `@Scheduled`**，由 web-admin `sys_job` 统一驱动。详见 `BlacklistChannelSyncQuartzTask` 类注释对同模式的说明。

---

## sys_job 230 自动解除黑名单

- **cron**: `0 0 10,16 * * ?`
- **触发目标**: `blacklistAutoReleaseQuartzTask.autoRelease()`
- **执行方**: `POST /internal/blacklist/auto-release`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 落地（ADR 未命名，独立新增）
- **实现要点**:
  - 扫 `BLACK_CAUSE='01'`（欠费类）且生效中的行，按 `CHANNEL_CODE` 查对应欠费源
    - `01` 地铁 APP：查闸机出站扣费
    - `02` 支付宝：查支付宝出行
    - `99` 未知渠道：跳过（打 WARN，这类行不会自愈，MUST 从加黑入口补 `channelCode`）
  - 欠费已结清即走两阶段解除：阶段一 `deleteBlackList` CAS 把 `STATUS` 由 `ACTIVE` 置 `RELEASING`（此状态**仍判黑**）+ 落操作日志；阶段二 `completeRelease` 先 `deleteReleasedById`（CAS 带 `STATUS='RELEASING'`）再插 `BLACKLIST_RELEASED` 历史
  - **与 sys_job 280「黑名单可解除性盘点」分工明确，NEVER 合并** —— 只读盘点要能把全部加黑原因报出来给人看，自动解除只覆盖「原因可被机器证明已失效」的欠费类
  - 响应含 `failed / unknown / skipped`：`failed`（解除动作自身失败）与 `unknown`（欠费查询不通）**只打 ERROR、NEVER 抛异常**（下一轮会重入）；`skipped`（渠道 99）**打 WARN**
- **配置键**:
  - `blacklist.auto-release.batch-size: 200` —— 单轮最多处理 200 条
- **关键约束**:
  - 本项目**第一个会主动解除黑名单的环节**；此前 280 只读、320/325 只推通知不改黑名单本体
  - 两阶段解除**严禁改回「先删本地、通知异步收敛」**：那样会出现「我方已放行、渠道仍拦着」的用户可见窗口
  - **连带 P0**：`BizRejected` 会把 `CHANNEL_SYNC_STATUS` 打成终态 `REJECTED`，而 `REJECTED` 不在补偿白名单 `IN ('PENDING','FAILED')` 里 ⇒ 支付中心一次 502 会让卡永久锁在 `RELEASING`（2026-09-18 实测卡 `TEST2P0000000001` 被锁死）。批量自动解除前 MUST 先处置它。
- **连带 P0**：`BizRejected` 会把 `CHANNEL_SYNC_STATUS` 打成终态 `REJECTED`，而 `REJECTED` 不在补偿白名单 `IN ('PENDING','FAILED')` 里 ⇒ 支付中心一次 502 会让卡永久锁在 `RELEASING`（2026-09-18 实测卡 `TEST2P0000000001` 被锁死）。批量自动解除前 MUST 先处置它。
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=230`

---

## sys_job 280 黑名单可解除性盘点

- **cron**: `0 0 10,16 * * ?`
- **触发目标**: `blacklistReleaseInspectQuartzTask.inspect()`
- **执行方**: `POST /internal/blacklist/inspectReleasable`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 105 改为 280
- **实现要点**:
  - 只读盘点 + 打日志，**NEVER 删记录** —— 「自动解除黑名单」由 job 230 单独承担（2026-09-21 落地），本任务仍只盘点
  - 响应含 `scanned / settled / unsettled / unknown`，`unknown > 0` 打 ERROR（事实不明，需人工看一眼）
  - 把「欠费已结清」的记录逐条打出来，供人工判断是否该解除
- **配置键**:
  - `blacklist.inspect.batch-size: 200` —— 单轮最多盘点 200 条
- **关键约束**:
  - **与 230 分工明确，NEVER 合并** —— 280 只读盘点报全部原因给人看，230 只解除「原因可被机器证明已失效」的欠费类
  - 本任务**只读不写**：响应中 `settled` 是已结清可解除的行，`unsettled` 是欠费未清的，`unknown` 是查询不通的
  - `unknown > 0` MUST 打 ERROR（事实不明），NEVER 降级为 WARN —— 人工 MUST 看一眼
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=280`

---

## sys_job 320 黑名单加黑通知补偿

- **cron**: `0 0/5 * * * ?`
- **触发目标**: `blacklistChannelSyncQuartzTask.compensateAdd()`
- **执行方**: `POST /internal/blacklist/channel-sync/compensate-add`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 125 改为 320
- **实现要点**:
  - 2026-09-20 库内实查已在 `sys_job` 且 `STATUS=0`，**NEVER 再记「迁移脚本待执行」**
  - 补偿加黑通知（落库出网 + afterCommit 快速路径后的兜底重推）
  - 响应含 `scanned / success / failed`：`failed > 0` **只打 ERROR、NEVER 抛异常**（下一轮会重入）
- **配置键**: 无外部可调参数（batchSize 硬编码在 BlacklistChannelSyncService 内部）
- **关键约束**:
  - `BlacklistChannelSyncQuartzTask` Javadoc：**补偿唯一驱动源，黑域刻意无 `@Scheduled`** —— 加了就是「同一份补偿两个驱动源」，多副本时没有分布式锁
  - 加黑通知**乐观**：先本地落库，通知异步收敛。窗口内「我方已拦、渠道未拦」的后果是欠费卡多过一次闸，可后续补加
  - `failed > 0` 只打 ERROR 不抛异常：下一轮会重入，抛异常会让 Quartz 认为本轮失败并中断后续行的处理
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=320`

---

## sys_job 325 黑名单解除通知补偿

- **cron**: `0 2/5 * * * ?`（与 320 错开 2 分钟）
- **触发目标**: `blacklistChannelSyncQuartzTask.compensateRelease()`
- **执行方**: `POST /internal/blacklist/channel-sync/compensate-release`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 126 改为 325
- **实现要点**: 同 320 结构，方向为解除
- **配置键**: 无外部可调参数（与 320 共用 `BlacklistChannelSyncService` 内部逻辑）
- **关键约束**:
  - 解黑是**两阶段的**：阶段一只把行标成 `RELEASING`（仍算黑名单），**只有通知拿到成功应答才进阶段二**搬历史 + 删主表行。这样两侧始终同向，不会出现「我方已放行、渠道仍拦着」——那个窗口对用户可见（被告知已解除却过不了闸），且通知永久失败时会永久不一致
  - **NEVER 把解黑改回「先删本地、通知异步收敛」** —— 那是 2026-08-26 生产事故的同款反模式（参照 §5.2）
  - cron 与 320 错开 2 分钟（`0 2/5 * * * ?` vs `0 0/5 * * * ?`），避免两任务同时刻触发冲击支付中心

---

## 判是否在跑（通用）

同 pay-sign-server：**MUST 查 `SYS_JOB_LOG`**，NEVER 在该模块里找 `@Scheduled`（该模块刻意不带）。
