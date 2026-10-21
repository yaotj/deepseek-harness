# daily-ticket-server 定时任务

> 对应 sys_job 2 条，处理多日票/旅游票批量退款。该类任务**刻意不带模块内 `@Scheduled`**，由 web-admin `sys_job` 统一驱动。

---

## sys_job 245 多日票批量退款（当日）

- **cron**: `0 0 20 * * ?`
- **触发目标**: `dailyTicketBatchRefundQuartzTask.refundDaily()`
- **执行方**: `POST /internal/daily-ticket/batch-refund/daily`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 135 改为 245，**NEVER 回退成 135**
- **实现要点**:
  - 甲方需求 16
  - 候选判据：`DAILY_TICKET_ORDER` 的 `ORDER_STATUS='PAID'` + `PAY_STATUS='PAID'` + **`DAILY_TICKET_INSTANCE` 里没有同 `ORDER_NO` 的行**（未激活）+ `PAY_DATE` 早于 `waitDays`（默认 3 天）+ 回溯 `daily.lookbackDays`（默认 7 天）
  - **独立日票谓词 MUST 带 `PARENT_ORDER_NO IS NULL`，NEVER 去掉** —— 不带会把旅游票子单当独立日票单独退掉
  - 旅游票按 `TRAVEL_TICKET_ORDER` 主单整单退（要求全部子单都无票实例）
  - 单批 200（`daily.batchRefund.limit`），两类各取一次
  - **首跑候选恒为 0，NEVER 当成已验证**（库内 `DAILY_TICKET_ORDER` 里根本没有 `ORDER_STATUS='PAID'` 的行）
  - **未做**: `POST /internal/daily-ticket/refund/notify`（退款结果通知补投）仍无调度源
- **配置键**:
  - `daily.batchRefund.limit: 200` —— 单批最大退款笔数（日票 + 旅游票各取一次，上限 200）
  - `daily.batchRefund.daily.lookbackDays: 7` —— 当日批次回溯天数
  - 注：文档中 `waitDays`（默认 3 天）与 `daily.lookbackDays`（默认 7 天）为业务术语，源码中对应 `daily.batchRefund.daily.lookbackDays`
- **关键约束**:
  - `DAILY_TICKET_BATCH_REFUND` 表有 `UK_DAILY_TICKET_REFUND_ORDER` 唯一索引，幂等由数据库保证；NEVER 在 Java 侧再加去重逻辑
  - 首跑候选恒为 0 是正常现象（库内无 PAID 行），**NEVER 当作「任务未跑」或「SQL 写错」**
  - 本模块刻意无 `@Scheduled`，由 web-admin `sys_job` 驱动；NEVER 在 daily-ticket-server 内加
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=245`

---

## sys_job 250 多日票批量退款（月度）

- **cron**: `0 0 20 L * ?`（月末最后一天，**NEVER 改回 30 日**）
- **触发目标**: `dailyTicketBatchRefundQuartzTask.refundMonthly()`
- **执行方**: `POST /internal/daily-ticket/batch-refund/monthly`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 136 改为 250，**NEVER 回退成 136**
- **实现要点**:
  - 甲方需求 17
  - 口径与 245 完全一致，只把回溯换成 `monthly.lookbackDays`（默认 60 天）兜漏网单
  - 与 245 谓词重叠靠 `UK_DAILY_TICKET_REFUND_ORDER` 幂等
  - cron 用 `L`（月末最后一天）而非甲方原文的 30 日——`0 0 20 30 * ?` 在 2 月不触发，那个月整月不跑，**NEVER 改回 30 日**；`L` 已确认被 `org.quartz.CronExpression` 接受
- **配置键**:
  - `daily.batchRefund.monthly.lookbackDays: 60` —— 月度批次回溯 60 天，兜住月度批次遗漏的单
- **关键约束**:
  - 与 245 谓词重叠是**刻意的**：245 负责每日处理新单，250 每月兜底处理漏网单；幂等由 `UK_DAILY_TICKET_REFUND_ORDER` 保证，NEVER 改为互斥谓词
  - cron 必须用 `L`（月末最后一天），**NEVER 改回 `30`** —— 2 月没有 30 日，改用 30 日会导致 2 月整月不跑
  - 本模块刻意无 `@Scheduled`，由 web-admin `sys_job` 驱动；NEVER 在 daily-ticket-server 内加
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=250`

---

## 新增索引

`daily-ticket-batch-refund-migration.sql` 已执行并回查（`USER_INDEXES` 确认 `VALID`）：
- `IDX_DAILY_TICKET_ORDER_BATCH_REFUND` `(ORDER_STATUS, PAY_STATUS, PAY_DATE)`
- `IDX_TRAVEL_TICKET_ORDER_BATCH_REFUND` `(ORDER_STATUS, PAY_STATUS, PAY_DATE)`

---

## 判是否在跑（通用）

**MUST 查 `SYS_JOB_LOG`**，NEVER 在该模块里找 `@Scheduled`（该模块刻意不带）。
