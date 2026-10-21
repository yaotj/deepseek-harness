# pay-sign-server 定时任务

> 对应 sys_job 5 条 + 补偿逻辑。该类任务**刻意不带 `@Scheduled`**（由 web-admin `sys_job` 统一驱动），详见 `BlacklistChannelSyncQuartzTask` 类注释对同模式的说明。

---

## sys_job 215 解约申请确认

- **cron**: `0 0 4 * * ?`
- **触发目标**: `terminationQuartzTask.confirmTermination()` / `confirmTermination(String referenceTime, Integer delayDays)`
- **执行方**: `pay-sign-server POST /internal/termination/process`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 4 改为 215，**NEVER 回退成 4**
- **实现要点**:
  - 扫 APP 解约申请满 4 天的确认解约（`PROCESSING` 状态的申请）
  - 循环排空：单次最多调下游 `MAX_ROUNDS=20` 轮，每轮扫 200 条（BATCH_SIZE 硬编码 200）
  - 基准时间取当前时间，延迟天数用 pay-sign-server 配置默认值（`termination.confirm-delay-days: 4`）
  - 响应码 `0000` 才记成功，非 `0000` 抛异常
- **配置键**:
  - `termination.confirm-delay-days: 4` —— 解约申请提交满此天数才确认解约
  - `app.notify.max-retry-count: 10` —— 通知/通道清理重试上限
  - `app.notify.executor.core-pool-size: 4` / `max-pool-size: 16` / `queue-capacity: 1000` —— 异步通知线程池
  - `itp.providerId: 06` / `charset: UTF-8` / `format: json` / `deviceId: ITP-PAY-SIGN` —— 通知签名参数
- **关键约束**:
  - 全模块刻意无 `@Scheduled`，补偿一律由 web-admin `sys_job` 驱动；NEVER 在本模块里加 `@Scheduled`
  - `PENDING_STALE_MINUTES=10` 硬编码：PENDING 滞留超 10 分钟视为状态回写丢了，纳入补偿范围
  - 循环排空靠 Quartz 每轮触发 + 调用方判断 `scanned=0` 停止，NEVER 在方法体内 for 循环调下游
- **判是否在跑**: `SYS_JOB_LOG` 查 `JOB_NAME` 或 `JOB_ID=215` 的最近记录

---

## sys_job 270 签约结果通知补发

- **cron**: `0 0/10 * * * ?`
- **触发目标**: `notifyCompensateQuartzTask.compensateSignNotify()`
- **执行方**: `pay-sign-server POST /internal/paySign/compensateNotify`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 6 改为 270
- **实现要点**:
  - 扫签约通知里「没通知成功」的记录重新推给 APP
  - 间隔必须大于下游 PENDING 滞留阈值 10 分钟（与下游同口径）
  - 响应含 `scanned / submitted / skipped`，`skipped>0` 打 ERROR（非终态）
  - PENDING 滞留判定硬编码 `PENDING_STALE_MINUTES=10`
- **配置键**:
  - `app.notify.max-retry-count: 10` —— 通知重试上限（与 job 215 共用）
  - `app.notify.sign-result-url` / `app.notify.termination-result-url` —— 通知出网地址（必须配置，否则通知打空 URL）
- **关键约束**:
  - 本类 `SignNotifyServiceImpl` 刻意无 `@Scheduled`（由 sys_job 270 驱动），NEVER 在本类上加
  - PENDING 扫表条件硬编码 10 分钟静默期，cron 必须 > 10 分钟（现为 10 分钟一轮，边界风险需关注）
  - 响应含 `submitted`：真向 APP 发起推送的笔数，0 不代表失败（可能是 `skipped` 重推上限已到）
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=270`

---

## sys_job 275 解约结果通知补发

- **cron**: `0 5/10 * * * ?`（与 270 错开 5 分钟）
- **触发目标**: `notifyCompensateQuartzTask.compensateTerminationNotify()`
- **执行方**: `pay-sign-server POST /internal/termination/compensateNotify`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 7 改为 275
- **实现要点**: 与 270 同结构，方向不同（解约 vs 签约）
- **配置键**:
  - `termination.channel-sync.max-retry-count: 10` —— 通道清理重试上限（与通知上限 `app.notify.max-retry-count` 刻意分开配）
  - `PENDING_STALE_MINUTES=10` —— 解约侧 PENDING 滞留判定（硬编码）
- **关键约束**:
  - `maxChannelSyncRetryCount` 与 `maxNotifyRetryCount` 刻意分开配置：通知失败只是 APP 少收一条，通道清理失败影响业务链路
  - 本类 `TerminationCompensationService` 无 `@Scheduled`，由 sys_job 275 驱动，NEVER 在本类上加

---

## sys_job 305 退款回查补偿

- **cron**: `0 0/10 * * * ?`
- **触发目标**: `refundCompensateQuartzTask.compensateRefundQuery()`
- **执行方**: `pay-sign-server POST /internal/payment/compensateRefundQuery`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 122 改为 305
- **实现要点**:
  - 扫 `ALIPAY_REFUND_LOG` / `PAY_REFUND_DETAIL` 的 PROCESSING 明细（7 天窗口 + 5 分钟静默期）回查支付中心并 CAS 收口
  - 端点恒返 0000，条数在 retMsg 里，判是否在跑 MUST 查 `SYS_JOB_LOG`
  - cron 间隔 MUST 大于静默期 5 分钟（现为 10 分钟一轮）
  - 跳过逻辑：下游对已终态行返回 `skipped`，不算失败
- **配置键**:
  - `REFUND_QUERY_SCAN_DAYS=7` 硬编码 —— 只回查最近 7 天 TXN_DATE，避免对过期行无意义打对端
  - `REFUND_QUERY_STALE_MINUTES=5` 硬编码 —— 距上次发起退款至少 5 分钟才回查，避开正常同步应答时间窗
  - `REFUND_QUERY_RETRY_DELAY_SECONDS=300` 硬编码 —— 退避 CAS 未命中时推迟 5 分钟再查
  - `REFUND_QUERY_GIVE_UP_HOURS=6` 硬编码 —— 超过此时长未收口视为 GiveUp（落终态不再扫）
  - `REFUND_QUERY_BATCH_SIZE=200` 硬编码 —— 单轮取行数上限
- **关键约束**:
  - 本类 `RefundDomainServiceImpl.compensateRefundQuery()` 无 `@Scheduled`（由 sys_job 305 驱动），NEVER 在本类上加
  - 本类也刻意不带 `@Transactional`：出网 NEVER 被事务包住（参照 §5.2 生产事故教训），每条回写各自自动提交
  - cron 间隔 MUST > 静默期 5 分钟（现为 10 分钟），严禁改成 <5 分钟的 cron，否则回查会不断打已经在 RPC 中的单
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=305`

---

## sys_job 310 退款汇总跨表对账

- **cron**: `0 15 * * * ?`
- **触发目标**: `refundCompensateQuartzTask.compensateRefundSummary()`
- **执行方**: `pay-sign-server POST /internal/payment/compensateRefundSummary`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 123 改为 310
- **实现要点**:
  - **不出网**，只重算 `PAY_TXN_DETAIL` 的 `REFUND_AMOUNT` / `REFUND_STATUS`
  - `skipped` 长期非 0 是预期（B 类：明细已 SUCCESS 但原支付订单不存在、不可自愈），**MUST NOT 照抄「skipped>0 就 log.error」**，已改 `log.warn`
  - 依赖端点在 pay-sign 2.0.87 才存在，**MUST 等 pay-sign ≥ 2.0.87 上线后再启用**
- **配置键**:
  - `REFUND_SUMMARY_SCAN_DAYS=7` 硬编码 —— 同回查补偿，只扫 7 天内交易
  - `REFUND_SUMMARY_BATCH_SIZE=200` 硬编码 —— 单轮取行数上限
- **关键约束**:
  - 本任务**不出网**，`skipped>0` 是 B 类明细（原支付订单已消失）的预期，**NEVER 改成 log.error**
  - 本类 `RefundDomainServiceImpl.compensateRefundSummary()` 无 `@Scheduled`（由 sys_job 310 驱动），NEVER 在本类上加
  - `pay-sign-server ≥ 2.0.87` 是硬性依赖：端点 `/internal/payment/compensateRefundSummary` 在此之前不存在，上线前 MUST 核对版本
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=310`

---

## 判是否在跑（通用）

所有 pay-sign-server 侧的补偿任务**都没有模块内 `@Scheduled`**，判是否真正在跑 **MUST 查 `SYS_JOB_LOG`**，NEVER 在该模块里找 `@Scheduled` 或 `QRTZ_*`。
