# alipay-pay-sign-server 定时任务

> 对应 sys_job 3 条，处理支付宝出行渠道的销卡 / 通道同步补偿 / 退款回查补偿。与 pay-sign-server 分工：alipay 侧走独立的 `AlipayPaySignClient`，URL / DTO / 接口均独立。

---

## sys_job 260 支付宝出行销卡

- **cron**: `0 30 2 * * ?`
- **触发目标**: `alipayTerminationQuartzTask.cancelCard()` / `cancelCard(String referenceTime)`
- **执行方**: `POST /internal/alipay/termination/process`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 5 改为 260（期间曾暂用 265）
- **实现要点**:
  - 扫 `ALIPAY_TERMINATION_REQUEST` 的 `STATUS=PENDING` + `DELETE_FLAG='0'`，按 `CREATE_TIME` 排序单批 200 条
  - 循环排空：单次最多 20 轮，每轮 200 条
  - **2026-09-20 由 `0 0 2` 挪到 `0 30 2`**，让位给业主要求的对账 2:00；与需求原文的「2 点」有 30 分钟偏移，属刻意错峰，**NEVER 改回 `0 0 2`**
- **配置键**: 无外部可调参数（批次大小 200 硬编码在 AlipayTerminationQuartzTask 的 for 循环内）
- **关键约束**:
  - 本任务由 web-admin `alipayTerminationQuartzTask.cancelCard()` 驱动，alipay-pay-sign-server 侧无 `@Scheduled`
  - 循环排空靠 Quartz 反复触发 + 调用方判断 `scanned=0` 停止，NEVER 在 cancelCard 方法里 for 循环调下游
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=260`

---

## sys_job 315 支付宝支付通道同步补偿

- **cron**: `0 0/5 * * * ?`
- **触发目标**: `alipayChannelSyncQuartzTask.compensate()`
- **执行方**: `POST /internal/alipay/channelSync/compensate`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 124 改为 315
- **实现要点**:
  - ADR-D132 唯一驱动源
  - alipay-pay-sign-server 侧的 `ChannelSyncCompensationService` **刻意没有 `@Scheduled`**
  - 少了本任务 + 对应 sys_job 行，签约首推不可达的行会永久停在 `CHANNEL_SYNC_STATUS != 'SUCCESS'` 上
  - **NEVER 在本方法里循环调下游**：每轮扫多少由下游 `alipay.channel-sync.batch-size` 决定，排空靠 cron 的下一次触发
- **配置键**:
  - `alipay.channel-sync.batch-size: 200` —— 单轮取行数上限（SQL 里 LIMIT，Java 不截断）
  - `alipay.channel-sync.max-retry-count: 5` —— 重试上限，达到后不再自动重推、等人工介入
- **关键约束**:
  - `ChannelSyncCompensationService` 刻意无 `@Scheduled`，**NEVER 在本类上加** —— 加了就是「同一份补偿两个驱动源」，而本模块多副本时没有分布式锁
  - `ChannelSyncCompensationService` 也刻意不带 `@Transactional`：出网 NEVER 被事务包住（参照 §5.2 生产事故），每条回写各自自动提交
  - **代码写了不等于补偿在跑，判据是 SYS_JOB_LOG 有没有记录**
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=315`

---

## sys_job 340 支付宝退款回查补偿

- **cron**: `0 0/10 * * * ?`
- **触发目标**: `alipayRefundQueryQuartzTask.compensate()`
- **执行方**: `POST /internal/alipay/refund/compensateQuery`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-20 新增入库（当时占 137）；2026-09-21 由 137 改为 340，**NEVER 回退成 137 或 135**
- **实现要点**:
  - 扫 `ALIPAY_REFUND_LOG` 的 PROCESSING 明细（7 天窗口 + 5 分钟静默期）回查支付中心并 CAS 收口
  - 端点恒返 0000，条数在 retMsg 里，判是否在跑 MUST 查 `SYS_JOB_LOG`
  - cron 间隔 MUST 大于静默期 5 分钟（现为 10 分钟一轮）
  - **NEVER 在本方法里循环调下游**
- **配置键**:
  - `alipay.channel-sync.batch-size: 200` —— 单轮取行数上限
  - `scan-days: 7` 硬编码 —— 只回查最近 7 天创建的退款，更早的行已过人工核对窗口
  - `stale-minutes: 5` 硬编码 —— 距上次更新至少 5 分钟才回查，避开正常回调时间窗
- **关键约束**:
  - `RefundQueryCompensationService` 刻意无 `@Scheduled`，**NEVER 在本类上加** —— 加了就是「同一份补偿两个驱动源」，而本模块多副本时没有分布式锁
  - `RefundQueryCompensationService` 也刻意不带 `@Transactional`：出网 NEVER 被事务包住（§5.2 事故），每条回写各自自动提交
  - `ALIPAY_REFUND_LOG` 没有任何退避列，重复问一次的代价远小于加 DDL 列；cron 间隔 MUST > 5 分钟，严禁改成 <5 分钟
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=340`

---

## 判是否在跑（通用）

同 pay-sign-server：**MUST 查 `SYS_JOB_LOG`**，NEVER 在模块里找 `@Scheduled`。
